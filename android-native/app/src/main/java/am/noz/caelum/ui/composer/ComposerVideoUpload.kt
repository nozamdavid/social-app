package am.noz.caelum.ui.composer

import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.model.BlobRef
import am.noz.caelum.core.network.AtprotoApi
import am.noz.caelum.core.network.VideoJobStatus
import am.noz.caelum.core.network.XrpcErrorBody
import am.noz.caelum.core.network.XrpcException
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.UUID
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink

internal val composerVideoExtensions = mapOf(
    "video/mp4" to "mp4",
    "video/mpeg" to "mpeg",
    "video/webm" to "webm",
    "video/quicktime" to "mov",
)

internal class ComposerVideoUpload(
    private val client: OkHttpClient,
    private val serviceAuth: suspend (audience: String, method: String, expiresInSeconds: Long?) -> String?,
    private val failedMessage: String,
    private val limitMessage: String,
    private val timeoutMessage: String,
    private val origin: HttpUrl = AtprotoApi.VIDEO_SERVICE_ORIGIN.toHttpUrl(),
) {
    suspend fun upload(file: File, mimeType: String, pdsOrigin: HttpUrl, did: String): BlobRef {
        val limitsToken = token("did:web:video.bsky.app", "app.bsky.video.getUploadLimits", null)
        val limits = request<VideoUploadLimits>("getUploadLimits", token = limitsToken)
        if (!limits.canUpload) {
            throw IOException(limits.message?.takeIf { it.isNotBlank() } ?: limitMessage)
        }

        val pdsAudience = "did:web:${pdsOrigin.host}"
        val scope = "com.atproto.repo.uploadBlob"
        var uploadToken = token(pdsAudience, scope, 30 * 60L)
        val extension = composerVideoExtensions.getValue(mimeType)
        val size = file.length()
        val upload = request<VideoUploadSession>(
            "startUpload",
            token = uploadToken,
            body = jsonBody {
                put("name", "${UUID.randomUUID()}.$extension")
                put("mimeType", mimeType)
                put("sizeBytes", size)
            },
        )
        val jobBody = jsonBody { put("jobId", upload.jobId) }
        var finalized = false
        val job = try {
            val partSize = upload.partSizeBytes
            if (partSize <= 0 || upload.partCount <= 0 || (size - 1) / partSize + 1 != upload.partCount.toLong()) {
                throw IOException(failedMessage)
            }

            suspend fun sendPart(part: Int) {
                val offset = (part - 1) * partSize
                val count = minOf(partSize, size - offset)
                val receipt = retryVideoRequest {
                    request<VideoUploadReceipt>(
                        "uploadPart",
                        token = uploadToken,
                        body = VideoFilePart(file, offset, count),
                        params = listOf("jobId" to upload.jobId, "partNumber" to part.toString()),
                    )
                }
                if ((receipt.partNumber != null && receipt.partNumber != part) ||
                    (receipt.sizeBytes != null && receipt.sizeBytes != count)
                ) throw IOException(failedMessage)
            }

            for (part in 1..upload.partCount) sendPart(part)
            // The token supplied to finishUpload is used for the later write to the PDS.
            uploadToken = token(pdsAudience, scope, 30 * 60L)
            val result = withTimeoutOrNull(60_000L) {
                finish(upload, jobBody, uploadToken, ::sendPart)
            } ?: throw IOException(timeoutMessage)
            finalized = true
            result
        } finally {
            if (!finalized) withContext(NonCancellable) {
                withTimeoutOrNull(3_000L) {
                    runCatching { request<VideoUploadState>("abortUpload", token = uploadToken, body = jobBody) }
                }
            }
        }

        return withTimeoutOrNull(5 * 60_000L) {
            waitForBlob(job, did)
        } ?: throw IOException(timeoutMessage)
    }

    private suspend fun finish(
        upload: VideoUploadSession,
        body: RequestBody,
        token: String,
        sendPart: suspend (Int) -> Unit,
    ): VideoJobStatus {
        repeat(3) { attempt ->
            try {
                return request<VideoJobResponse>("finishUpload", token = token, body = body).jobStatus
            } catch (error: IOException) {
                var state = uploadStatus(upload.jobId, token)
                while (state.state == "finishing") {
                    delay(1_000L)
                    state = uploadStatus(upload.jobId, token)
                }
                when (state.state) {
                    "completed" -> return state.jobStatus ?: throw IOException(failedMessage)
                    "created" -> {
                        if (attempt == 2) throw error
                        val receivedParts = state.receivedParts.toSet()
                        for (part in 1..upload.partCount) {
                            if (part !in receivedParts) sendPart(part)
                        }
                        delay(500L shl attempt)
                    }
                    else -> throw IOException(state.failureReason ?: failedMessage, error)
                }
            }
        }
        error("Unreachable video finish state")
    }

    private suspend fun uploadStatus(jobId: String, token: String): VideoUploadState = retryVideoRequest {
        request("getUploadStatus", token = token, params = listOf("jobId" to jobId))
    }

    private suspend fun waitForBlob(first: VideoJobStatus, did: String): BlobRef {
        val jobId = first.jobId
        var job = first
        while (true) {
            if (job.did != did || job.jobId != jobId || jobId.isBlank()) throw IOException(failedMessage)
            // The service can return a usable blob with an already_exists error.
            job.blob?.let { blob ->
                if (blob.mimeType != "video/mp4" || blob.ref.link.isBlank() || blob.size <= 0) {
                    throw IOException(failedMessage)
                }
                return blob
            }
            if (job.state == "JOB_STATE_FAILED" || job.state == "JOB_STATE_COMPLETED" || !job.error.isNullOrBlank()) {
                throw IOException(job.message?.takeIf { it.isNotBlank() } ?: failedMessage)
            }
            delay(1_000L)
            job = retryVideoRequest {
                request<VideoJobResponse>("getJobStatus", params = listOf("jobId" to jobId)).jobStatus
            }
        }
    }

    private suspend fun token(audience: String, method: String, expiresInSeconds: Long?): String =
        serviceAuth(audience, method, expiresInSeconds)?.takeIf { it.isNotBlank() } ?: throw IOException(failedMessage)

    private suspend inline fun <reified T> request(
        method: String,
        token: String? = null,
        body: RequestBody? = null,
        params: List<Pair<String, String>> = emptyList(),
    ): T {
        val url = origin.newBuilder()
            .addPathSegments("xrpc/app.bsky.video.$method")
            .apply { params.forEach { (key, value) -> addQueryParameter(key, value) } }
            .build()
        val request = Request.Builder().url(url)
            .header("Accept", "application/json")
            .apply {
                if (body != null) post(body)
                token?.let { header("Authorization", "Bearer $it") }
            }
            .build()
        return client.readComposerResponse(request) { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val error = runCatching { AtProtoJson.decodeFromString<XrpcErrorBody>(raw) }.getOrNull()
                throw XrpcException(response.code, error?.error, error?.message?.takeIf { it.isNotBlank() } ?: failedMessage)
            }
            try {
                AtProtoJson.decodeFromString<T>(raw.ifBlank { "{}" })
            } catch (error: SerializationException) {
                throw IOException(failedMessage, error)
            }
        }
    }

    private fun jsonBody(build: JsonObjectBuilder.() -> Unit) =
        buildJsonObject(build).toString().toRequestBody("application/json".toMediaType())
}

private suspend fun <T> retryVideoRequest(block: suspend () -> T): T {
    for (attempt in 0..1) {
        try {
            return block()
        } catch (error: IOException) {
            if (error is XrpcException &&
                error.error != "ServiceOverloaded" &&
                error.statusCode !in 500..599 &&
                error.statusCode != 408 &&
                error.statusCode != 429
            ) throw error
            delay(500L shl attempt)
        }
    }
    return block()
}

private class VideoFilePart(
    private val file: File,
    private val offset: Long,
    private val count: Long,
) : RequestBody() {
    override fun contentType() = "application/octet-stream".toMediaType()

    override fun contentLength() = count

    override fun writeTo(sink: BufferedSink) {
        RandomAccessFile(file, "r").use { input ->
            input.seek(offset)
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var remaining = count
            while (remaining > 0) {
                val size = minOf(remaining, buffer.size.toLong()).toInt()
                input.readFully(buffer, 0, size)
                sink.write(buffer, 0, size)
                remaining -= size
            }
        }
    }
}

internal suspend fun <T> OkHttpClient.readComposerResponse(
    request: Request,
    read: (Response) -> T,
): T = suspendCancellableCoroutine { continuation ->
    val call = newCall(request)
    continuation.invokeOnCancellation { call.cancel() }
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            continuation.resumeWith(runCatching { response.use(read) })
        }
    })
}

@Serializable
private data class VideoUploadLimits(val canUpload: Boolean, val message: String? = null)

@Serializable
private data class VideoUploadSession(val jobId: String, val partSizeBytes: Long, val partCount: Int)

@Serializable
private data class VideoUploadReceipt(val partNumber: Int? = null, val sizeBytes: Long? = null)

@Serializable
private data class VideoJobResponse(val jobStatus: VideoJobStatus)

@Serializable
private data class VideoUploadState(
    val state: String,
    val receivedParts: List<Int> = emptyList(),
    val jobStatus: VideoJobStatus? = null,
    val failureReason: String? = null,
)
