package am.noz.caelum.core.network

import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.model.SessionTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

data class ActiveXrpcAuth(
    val did: String,
    val pdsUrl: String,
    val accessJwt: String,
    val refreshJwt: String,
    val labelers: List<String>? = null,
    val accountGeneration: Long = 0,
)

interface XrpcSessionDelegate {
    fun currentAuth(): ActiveXrpcAuth?
    fun authForRefresh(failed: ActiveXrpcAuth): ActiveXrpcAuth? =
        currentAuth()?.takeIf { it.did == failed.did && it.accountGeneration == failed.accountGeneration }

    suspend fun updateRefreshedTokens(failed: ActiveXrpcAuth, tokens: SessionTokens): Boolean = false
    suspend fun expireFailedSession(failed: ActiveXrpcAuth) = Unit
}

internal class BoundXrpcSession(
    val auth: () -> ActiveXrpcAuth?,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<BoundXrpcSession>
}

@Serializable
data class XrpcErrorBody(
    val error: String? = null,
    val message: String? = null,
)

class XrpcException(
    val statusCode: Int,
    val error: String?,
    override val message: String,
) : IOException(message) {
    val isExpiredToken: Boolean
        get() = error == "ExpiredToken" || (statusCode == 401 && error == null)

    val isInvalidToken: Boolean
        get() = error == "InvalidToken" || error == "AccountTakedown" || error == "AccountSuspended"

    val isChatScopeOrPermissionError: Boolean
        get() = error == "ScopeMissingError" ||
            statusCode == 403 ||
            message.contains("scope", ignoreCase = true) ||
            message.contains("app password", ignoreCase = true) ||
            message.contains("permission", ignoreCase = true)
}

enum class XrpcMethod {
    QUERY,
    PROCEDURE,
}

/**
 * Native OkHttp-backed ATProto XRPC client supporting dynamic AppView proxy routing,
 * chat & push-gateway service proxies, single-flight token refresh, and blob uploads.
 */
class XrpcClient(
    val okHttpClient: OkHttpClient = defaultOkHttpClient(),
    private val sessionDelegate: XrpcSessionDelegate? = null,
    private val infraProvider: () -> InfrastructureSettings = { InfrastructureConfig.DEFAULT },
    private val labelersProvider: () -> List<String> = { emptyList() },
    private val publicOriginOverride: String? = null,
) {
    private val refreshMutexes = ConcurrentHashMap<String, Mutex>()

    suspend fun getJson(
        nsid: String,
        params: List<Pair<String, String>> = emptyList(),
        baseUrlOverride: String? = null,
        proxyServiceDid: String? = null,
        authenticated: Boolean = true,
        bearerOverride: String? = null,
        extraHeaders: Map<String, String> = emptyMap(),
    ): JsonElement = callRawJson(
        method = XrpcMethod.QUERY,
        nsid = nsid,
        params = params,
        bodyBytes = null,
        contentType = null,
        baseUrlOverride = baseUrlOverride,
        proxyServiceDid = proxyServiceDid,
        authenticated = authenticated,
        bearerOverride = bearerOverride,
        extraHeaders = extraHeaders,
    )

    suspend fun postJson(
        nsid: String,
        body: JsonElement? = null,
        params: List<Pair<String, String>> = emptyList(),
        baseUrlOverride: String? = null,
        proxyServiceDid: String? = null,
        authenticated: Boolean = true,
        bearerOverride: String? = null,
        extraHeaders: Map<String, String> = emptyMap(),
    ): JsonElement = callRawJson(
        method = XrpcMethod.PROCEDURE,
        nsid = nsid,
        params = params,
        bodyBytes = body?.toString()?.toByteArray(Charsets.UTF_8),
        contentType = if (body != null) JSON_MEDIA_TYPE_STR else null,
        baseUrlOverride = baseUrlOverride,
        proxyServiceDid = proxyServiceDid,
        authenticated = authenticated,
        bearerOverride = bearerOverride,
        extraHeaders = extraHeaders,
    )

    suspend fun uploadBinary(
        nsid: String,
        bytes: ByteArray,
        mimeType: String,
        params: List<Pair<String, String>> = emptyList(),
        baseUrlOverride: String? = null,
        bearerOverride: String? = null,
    ): JsonElement = callRawJson(
        method = XrpcMethod.PROCEDURE,
        nsid = nsid,
        params = params,
        bodyBytes = bytes,
        contentType = mimeType,
        baseUrlOverride = baseUrlOverride,
        proxyServiceDid = null,
        authenticated = true,
        bearerOverride = bearerOverride,
        extraHeaders = emptyMap(),
    )

    private suspend fun callRawJson(
        method: XrpcMethod,
        nsid: String,
        params: List<Pair<String, String>>,
        bodyBytes: ByteArray?,
        contentType: String?,
        baseUrlOverride: String?,
        proxyServiceDid: String?,
        authenticated: Boolean,
        bearerOverride: String?,
        extraHeaders: Map<String, String>,
    ): JsonElement {
        val boundSession = coroutineContext[BoundXrpcSession]
        val authSnapshot = if (authenticated && bearerOverride == null) {
            if (boundSession != null) boundSession.auth() ?: throw IOException("Session changed")
            else sessionDelegate?.currentAuth()
        } else null
        val result = withContext(Dispatchers.IO) {
            if (authSnapshot != null && boundSession != null && boundSession.auth() == null) {
                throw IOException("Session changed")
            }
            try {
                executeOnce(
                    method = method,
                    nsid = nsid,
                    params = params,
                    bodyBytes = bodyBytes,
                    contentType = contentType,
                    baseUrlOverride = baseUrlOverride,
                    proxyServiceDid = proxyServiceDid,
                    auth = authSnapshot,
                    bearerOverride = bearerOverride,
                    extraHeaders = extraHeaders,
                )
            } catch (ex: XrpcException) {
                if (authSnapshot == null || !ex.isExpiredToken) throw ex
                val refreshed = refreshSingleFlight(authSnapshot) ?: throw ex
                if (boundSession != null && boundSession.auth() == null) throw ex
                executeOnce(
                    method = method,
                    nsid = nsid,
                    params = params,
                    bodyBytes = bodyBytes,
                    contentType = contentType,
                    baseUrlOverride = baseUrlOverride,
                    proxyServiceDid = proxyServiceDid,
                    auth = refreshed,
                    bearerOverride = null,
                    extraHeaders = extraHeaders,
                )
            }
        }
        if (authSnapshot != null && boundSession != null && boundSession.auth() == null) {
            throw IOException("Session changed")
        }
        return result
    }

    private suspend fun refreshSingleFlight(failedAuth: ActiveXrpcAuth): ActiveXrpcAuth? {
        val mutex = refreshMutexes.getOrPut(failedAuth.did) { Mutex() }
        return mutex.withLock {
            val delegate = sessionDelegate ?: return@withLock null
            val latest = delegate.authForRefresh(failedAuth) ?: return@withLock null
            if (latest.pdsUrl != failedAuth.pdsUrl) return@withLock null
            if (latest.accessJwt != failedAuth.accessJwt || latest.refreshJwt != failedAuth.refreshJwt) {
                return@withLock latest.copy(labelers = failedAuth.labelers)
            }

            val refreshUrl = "${failedAuth.pdsUrl.trimEnd('/')}/xrpc/com.atproto.server.refreshSession"
            val req = Request.Builder()
                .url(refreshUrl)
                .post(ByteArray(0).toRequestBody(null))
                .header("Authorization", "Bearer ${failedAuth.refreshJwt}")
                .header("Accept", "application/json")
                .build()

            val resp = try {
                okHttpClient.newCall(req).execute()
            } catch (_: IOException) {
                return@withLock null
            }
            resp.use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    if (response.code == 400 || response.code == 401) {
                        delegate.expireFailedSession(latest)
                    }
                    return@withLock null
                }
                val parsed = runCatching { AtProtoJson.parseToJsonElement(raw).jsonObject }.getOrNull()
                    ?: return@withLock null
                val newAccess = (parsed["accessJwt"] as? JsonPrimitive)?.content ?: return@withLock null
                val newRefresh = (parsed["refreshJwt"] as? JsonPrimitive)?.content ?: return@withLock null
                val nextTokens = SessionTokens(accessJwt = newAccess, refreshJwt = newRefresh)
                val stored = withContext(NonCancellable) { delegate.updateRefreshedTokens(latest, nextTokens) }
                if (!stored) return@withLock null
                failedAuth.copy(accessJwt = newAccess, refreshJwt = newRefresh)
            }
        }
    }

    private fun executeOnce(
        method: XrpcMethod,
        nsid: String,
        params: List<Pair<String, String>>,
        bodyBytes: ByteArray?,
        contentType: String?,
        baseUrlOverride: String?,
        proxyServiceDid: String?,
        auth: ActiveXrpcAuth?,
        bearerOverride: String?,
        extraHeaders: Map<String, String>,
    ): JsonElement {
        val infra = infraProvider()
        val path = "/xrpc/$nsid"
        val useAuth = bearerOverride != null || (auth != null)
        val origin = when {
            !baseUrlOverride.isNullOrBlank() -> baseUrlOverride.trimEnd('/')
            useAuth && auth != null -> auth.pdsUrl.trimEnd('/')
            !publicOriginOverride.isNullOrBlank() -> publicOriginOverride.trimEnd('/')
            infra.appviewHostname.isNotEmpty() -> "https://${infra.appviewHostname}"
            else -> PUBLIC_APPVIEW_ORIGIN
        }

        val httpUrlBuilder = ("$origin$path").toHttpUrlOrNull()?.newBuilder()
            ?: throw IOException("Invalid XRPC URL origin=$origin path=$path")
        for ((k, v) in params) {
            httpUrlBuilder.addQueryParameter(k, v)
        }

        val builder = Request.Builder().url(httpUrlBuilder.build())
        when (method) {
            XrpcMethod.QUERY -> builder.get()
            XrpcMethod.PROCEDURE -> {
                val bytes = bodyBytes ?: ByteArray(0)
                val media = contentType?.toMediaType()
                builder.post(bytes.toRequestBody(media))
            }
        }

        builder.header("Accept", "application/json")
        val token = bearerOverride ?: auth?.accessJwt
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }

        val resolvedProxy = when {
            !proxyServiceDid.isNullOrBlank() -> proxyServiceDid
            useAuth && nsid.startsWith("chat.bsky.") -> BSKY_CHAT_PROXY_DID
            useAuth && baseUrlOverride == null && infra.appviewHostname.isNotEmpty() &&
                InfrastructureConfig.isAppviewRequest(path) ->
                "did:web:${infra.appviewHostname}#bsky_appview"
            else -> null
        }
        if (resolvedProxy != null) {
            builder.header("atproto-proxy", resolvedProxy)
        }

        val labelers = auth?.labelers ?: labelersProvider()
        val supportsLabelers = baseUrlOverride == null &&
            (InfrastructureConfig.isAppviewRequest(path) || nsid.startsWith("chat.bsky."))
        if (labelers.isNotEmpty() && supportsLabelers) {
            val formatted = labelers.distinct().joinToString(", ") { did ->
                when {
                    did.contains(';') -> did
                    did == BLUESKY_LABELER_DID -> "$did;redact"
                    else -> did
                }
            }
            builder.header("atproto-accept-labelers", formatted)
        }

        for ((k, v) in extraHeaders) {
            builder.header(k, v)
        }

        okHttpClient.newCall(builder.build()).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val errBody = runCatching { AtProtoJson.decodeFromString<XrpcErrorBody>(raw) }.getOrNull()
                val resolvedMessage = errBody?.message?.takeIf { it.isNotBlank() }
                    ?: errBody?.error?.takeIf { it.isNotBlank() }
                    ?: "HTTP ${response.code} on $nsid"
                throw XrpcException(
                    statusCode = response.code,
                    error = errBody?.error,
                    message = resolvedMessage,
                )
            }
            if (raw.isBlank()) return JsonObject(emptyMap())
            return AtProtoJson.parseToJsonElement(raw)
        }
    }

    companion object {
        const val PUBLIC_APPVIEW_ORIGIN = "https://public.api.bsky.app"
        const val BSKY_CHAT_PROXY_DID = "did:web:api.bsky.chat#bsky_chat"
        private const val BLUESKY_LABELER_DID = "did:plc:ar7c4by46qjdydhdevvrndac"
        private const val JSON_MEDIA_TYPE_STR = "application/json; charset=utf-8"

        fun defaultOkHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        }
    }
}
