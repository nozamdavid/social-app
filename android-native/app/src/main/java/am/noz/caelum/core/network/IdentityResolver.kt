package am.noz.caelum.core.network

import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.model.DidDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves handles -> DIDs and DIDs -> PDS origins, honoring Caelum's custom PLC Directory
 * setting (`plc.directory` default or user-configured hostname) and `did:web:*` documents.
 */
class IdentityResolver(
    private val okHttpClient: OkHttpClient,
    private val infraProvider: () -> InfrastructureSettings = { InfrastructureConfig.DEFAULT },
) {
    private val handleCache = ConcurrentHashMap<String, String>()
    private val pdsCache = ConcurrentHashMap<String, String>()
    private val didDocCache = ConcurrentHashMap<String, DidDocument>()

    suspend fun resolveHandle(handleOrDid: String, serviceOrigin: String = "https://bsky.social"): String? {
        val cleaned = handleOrDid.trim().removePrefix("@")
        if (cleaned.isEmpty()) return null
        if (cleaned.startsWith("did:")) return cleaned
        handleCache[cleaned.lowercase()]?.let { return it }

        // 1. Try PDS/AppView XRPC resolveHandle
        val encoded = withContext(Dispatchers.IO) { URLEncoder.encode(cleaned, "UTF-8") }
        val xrpcUrl = "${serviceOrigin.trimEnd('/')}/xrpc/com.atproto.identity.resolveHandle?handle=$encoded"
        val xrpcDid = fetchJsonStringField(xrpcUrl, "did")
        if (!xrpcDid.isNullOrBlank()) {
            handleCache[cleaned.lowercase()] = xrpcDid
            return xrpcDid
        }

        // 2. Fallback: HTTP well-known on custom handle domain
        val wellKnownUrl = "https://$cleaned/.well-known/atproto-did"
        val wellKnownDid = fetchPlainText(wellKnownUrl)?.lineSequence()?.firstOrNull()?.trim()
        if (!wellKnownDid.isNullOrBlank() && wellKnownDid.startsWith("did:")) {
            handleCache[cleaned.lowercase()] = wellKnownDid
            return wellKnownDid
        }

        return null
    }

    suspend fun resolveDidDocument(did: String): DidDocument? {
        didDocCache[did]?.let { return it }
        val docUrl = when {
            did.startsWith("did:plc:") -> {
                val origin = InfrastructureConfig.plcDirectoryOrigin(infraProvider().plcDirectoryHostname)
                "$origin/$did"
            }
            did.startsWith("did:web:") -> {
                val domain = did.removePrefix("did:web:").substringBefore(':').replace("%3A", ":")
                "https://$domain/.well-known/did.json"
            }
            else -> return null
        }

        return withContext(Dispatchers.IO) {
            val req = Request.Builder().url(docUrl).get().header("Accept", "application/json").build()
            runCatching {
                okHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val raw = resp.body?.string() ?: return@use null
                    val doc = AtProtoJson.decodeFromString<DidDocument>(raw)
                    didDocCache[did] = doc
                    doc.service.firstOrNull { it.id == "#atproto_pds" || it.type == "AtprotoPersonalDataServer" }
                        ?.serviceEndpoint
                        ?.let { pdsCache[did] = it }
                    doc
                }
            }.getOrNull()
        }
    }

    suspend fun resolvePdsUrl(did: String): String? {
        pdsCache[did]?.let { return it }
        val doc = resolveDidDocument(did) ?: return null
        return doc.service
            .firstOrNull { it.id == "#atproto_pds" || it.type == "AtprotoPersonalDataServer" }
            ?.serviceEndpoint
    }

    suspend fun discoverPdsForLogin(identifier: String, defaultServiceOrigin: String): String {
        val cleaned = identifier.trim().removePrefix("@")
        if (cleaned.isEmpty() || '@' in cleaned) {
            // Email sign-ins fall back to selected hosting service origin
            return defaultServiceOrigin
        }
        val did = if (cleaned.startsWith("did:")) cleaned else resolveHandle(cleaned, defaultServiceOrigin)
        if (did.isNullOrBlank()) return defaultServiceOrigin
        return resolvePdsUrl(did) ?: defaultServiceOrigin
    }

    fun clearCaches() {
        handleCache.clear()
        pdsCache.clear()
        didDocCache.clear()
    }

    private suspend fun fetchJsonStringField(url: String, field: String): String? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).get().header("Accept", "application/json").build()
        runCatching {
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val raw = resp.body?.string() ?: return@use null
                val obj = AtProtoJson.parseToJsonElement(raw) as? kotlinx.serialization.json.JsonObject
                obj?.get(field)?.jsonPrimitive?.content
            }
        }.getOrNull()
    }

    private suspend fun fetchPlainText(url: String): String? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).get().build()
        runCatching {
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.string()
            }
        }.getOrNull()
    }
}
