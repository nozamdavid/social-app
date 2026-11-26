package am.noz.caelum.core.network

import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.model.ProfileView
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.coroutines.withContext

@Serializable
data class ContactSyncStatus(val syncedAt: String, val matchesCount: Int)

@Serializable
data class ContactMatchesPage(val matches: List<ProfileView>, val cursor: String? = null)

internal class FindContactsApi(
    private val client: XrpcClient,
    private val sessionContext: BoundXrpcSession,
) {
    suspend fun startPhoneVerification(phone: String) {
        post("startPhoneVerification", buildJsonObject { put("phone", JsonPrimitive(phone)) })
    }

    suspend fun verifyPhone(phone: String, code: String): String =
        AtProtoJson.decodeFromJsonElement<Verification>(
            post("verifyPhone", buildJsonObject {
                put("phone", JsonPrimitive(phone))
                put("code", JsonPrimitive(code))
            }),
        ).token

    suspend fun importContacts(token: String, phones: List<String>): List<ProfileView> =
        AtProtoJson.decodeFromJsonElement<ImportResult>(
            post("importContacts", buildJsonObject {
                put("token", JsonPrimitive(token))
                put("contacts", JsonArray(phones.map { JsonPrimitive(it) }))
            }),
        ).matchesAndContactIndexes.map { it.match }

    suspend fun syncStatus(): ContactSyncStatus? =
        AtProtoJson.decodeFromJsonElement<StatusResult>(get("getSyncStatus")).syncStatus

    suspend fun getMatches(cursor: String? = null): ContactMatchesPage =
        AtProtoJson.decodeFromJsonElement(
            get("getMatches", cursor?.let { listOf("cursor" to it) }.orEmpty()),
        )

    suspend fun removeData() {
        post("removeData", buildJsonObject {})
    }

    private suspend fun get(method: String, params: List<Pair<String, String>> = emptyList()) =
        withContext(sessionContext) { client.getJson("app.bsky.contact.$method", params = params, proxyServiceDid = CONTACTS_SERVICE) }

    private suspend fun post(method: String, body: JsonObject) =
        withContext(sessionContext) { client.postJson("app.bsky.contact.$method", body = body, proxyServiceDid = CONTACTS_SERVICE) }

    @Serializable
    private data class Verification(val token: String)

    @Serializable
    private data class ImportResult(val matchesAndContactIndexes: List<IndexedMatch>)

    @Serializable
    private data class IndexedMatch(val match: ProfileView)

    @Serializable
    private data class StatusResult(val syncStatus: ContactSyncStatus? = null)

    companion object {
        const val MAX_CONTACTS = 1000
        private const val CONTACTS_SERVICE = "did:web:api.bsky.app#bsky_appview"
    }
}
