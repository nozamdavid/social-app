package am.noz.caelum.core.alttext

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.imagedescription.ImageDescriberOptions
import com.google.mlkit.genai.imagedescription.ImageDescription
import com.google.mlkit.genai.imagedescription.ImageDescriptionRequest
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

enum class DescriptionSupportStatus {
    AVAILABLE,
    DOWNLOADABLE,
    DOWNLOADING,
    UNAVAILABLE,
}

data class AltTextProgress(
    val requestId: String,
    val phase: String,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
)

class AltTextException(val code: String) : Exception(code)

class OnDeviceAltTextEngine(context: Context) {

    private val appContext = context.applicationContext
    private val currentRequest = AtomicReference<ActiveRequest?>(null)

    private class ActiveRequest(val id: String) {
        val cancelled = AtomicBoolean(false)
    }

    suspend fun getDescriptionStatus(): DescriptionSupportStatus = withContext(Dispatchers.IO) {
        runCatching {
            val client = ImageDescription.getClient(ImageDescriberOptions.builder(appContext).build())
            try {
                val future = client.checkFeatureStatus()
                val status = try {
                    future.get(20, TimeUnit.SECONDS)
                } finally {
                    future.cancel(false)
                }
                when (status) {
                    FeatureStatus.AVAILABLE -> DescriptionSupportStatus.AVAILABLE
                    FeatureStatus.DOWNLOADABLE -> DescriptionSupportStatus.DOWNLOADABLE
                    FeatureStatus.DOWNLOADING -> DescriptionSupportStatus.DOWNLOADING
                    else -> DescriptionSupportStatus.UNAVAILABLE
                }
            } finally {
                client.close()
            }
        }.getOrDefault(DescriptionSupportStatus.UNAVAILABLE)
    }

    fun cancel(requestId: String) {
        currentRequest.get()?.takeIf { it.id == requestId }?.cancelled?.set(true)
    }

    suspend fun extractText(
        requestId: String,
        uri: Uri,
        onProgress: (AltTextProgress) -> Unit = {},
    ): String = runSingleFlight(requestId) { request ->
        val bitmap = loadBoundedBitmap(uri)
        try {
            ensureActive(request)
            val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                onProgress(AltTextProgress(requestId, "recognizing"))
                val text = Tasks.await(client.process(InputImage.fromBitmap(bitmap, 0))).text.trim()
                ensureActive(request)
                if (text.isEmpty()) throw AltTextException("ERR_NO_TEXT")
                text
            } finally {
                client.close()
            }
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun suggestDescription(
        requestId: String,
        uri: Uri,
        allowDownload: Boolean,
        onProgress: (AltTextProgress) -> Unit = {},
    ): String = runSingleFlight(requestId) { request ->
        val client = ImageDescription.getClient(ImageDescriberOptions.builder(appContext).build())
        try {
            onProgress(AltTextProgress(requestId, "checking"))
            val status = client.checkFeatureStatus().get(20, TimeUnit.SECONDS)
            if (status == FeatureStatus.UNAVAILABLE) throw AltTextException("ERR_UNAVAILABLE")
            if (status != FeatureStatus.AVAILABLE) {
                if (!allowDownload) throw AltTextException("ERR_MODEL_REQUIRED")
                var total = 0L
                onProgress(AltTextProgress(requestId, "downloading"))
                client.downloadFeature(object : DownloadCallback {
                    override fun onDownloadStarted(bytesToDownload: Long) {
                        total = bytesToDownload
                        if (!request.cancelled.get()) {
                            onProgress(AltTextProgress(requestId, "downloading", 0L, total))
                        }
                    }

                    override fun onDownloadProgress(totalBytesDownloaded: Long) {
                        if (!request.cancelled.get()) {
                            onProgress(AltTextProgress(requestId, "downloading", totalBytesDownloaded, total))
                        }
                    }

                    override fun onDownloadCompleted() {}
                    override fun onDownloadFailed(e: GenAiException) {}
                }).get()
            }
            ensureActive(request)
            val bitmap = loadBoundedBitmap(uri)
            try {
                ensureActive(request)
                onProgress(AltTextProgress(requestId, "describing"))
                val description = client.runInference(ImageDescriptionRequest.builder(bitmap).build())
                    .get()
                    .description
                    .trim()
                ensureActive(request)
                if (description.isEmpty()) throw AltTextException("ERR_EMPTY_DESCRIPTION")
                description
            } finally {
                bitmap.recycle()
            }
        } finally {
            client.close()
        }
    }

    private suspend fun runSingleFlight(
        requestId: String,
        block: (ActiveRequest) -> String,
    ): String = withContext(Dispatchers.IO) {
        val request = ActiveRequest(requestId)
        if (!currentRequest.compareAndSet(null, request)) {
            throw AltTextException("ERR_BUSY")
        }
        try {
            ensureActive(request)
            block(request)
        } catch (err: Exception) {
            throw mapException(err)
        } finally {
            currentRequest.compareAndSet(request, null)
        }
    }

    private fun ensureActive(request: ActiveRequest) {
        if (request.cancelled.get()) throw CancellationException()
    }

    private fun loadBoundedBitmap(uri: Uri): Bitmap {
        val normalized = if (uri.scheme == null && uri.toString().startsWith("/")) {
            Uri.fromFile(File(uri.toString()))
        } else {
            uri
        }
        if (normalized.scheme != "file" && normalized.scheme != "content") {
            throw AltTextException("ERR_IMAGE")
        }
        val resolver = appContext.contentResolver
        fun open() = resolver.openInputStream(normalized) ?: throw AltTextException("ERR_IMAGE")
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            open().use { BitmapFactory.decodeStream(it, null, opts) }
            if (opts.outWidth <= 0 || opts.outHeight <= 0) throw AltTextException("ERR_IMAGE")
            opts.inSampleSize = 1
            while (maxOf(opts.outWidth, opts.outHeight) / opts.inSampleSize > MAX_IMAGE_EDGE) {
                opts.inSampleSize *= 2
            }
            opts.inJustDecodeBounds = false
            val orientation = open().use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
            val bitmap = open().use { BitmapFactory.decodeStream(it, null, opts) }
                ?: throw AltTextException("ERR_IMAGE")
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> {
                        setRotate(90f)
                        postScale(-1f, 1f)
                    }
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> {
                        setRotate(-90f)
                        postScale(-1f, 1f)
                    }
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
                }
            }
            if (matrix.isIdentity) {
                bitmap
            } else {
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
                    if (it !== bitmap) bitmap.recycle()
                }
            }
        } catch (e: AltTextException) {
            throw e
        } catch (_: Exception) {
            throw AltTextException("ERR_IMAGE")
        }
    }

    private fun mapException(error: Exception): AltTextException {
        var cause: Throwable = error
        while (cause is ExecutionException && cause.cause != null) {
            cause = cause.cause!!
        }
        return when (cause) {
            is AltTextException -> cause
            is CancellationException -> AltTextException("ERR_CANCELLED")
            is TimeoutException -> AltTextException("ERR_TIMEOUT")
            is GenAiException -> when (cause.errorCode) {
                GenAiException.ErrorCode.BUSY -> AltTextException("ERR_BUSY")
                GenAiException.ErrorCode.NOT_AVAILABLE -> AltTextException("ERR_UNAVAILABLE")
                GenAiException.ErrorCode.NOT_ENOUGH_DISK_SPACE -> AltTextException("ERR_STORAGE")
                GenAiException.ErrorCode.NEEDS_SYSTEM_UPDATE,
                GenAiException.ErrorCode.AICORE_INCOMPATIBLE -> AltTextException("ERR_SYSTEM_UPDATE")
                else -> AltTextException("ERR_GENERATION")
            }
            else -> AltTextException("ERR_GENERATION")
        }
    }

    private companion object {
        const val MAX_IMAGE_EDGE = 2048
    }
}
