package am.noz.caelum.core.network

import android.net.Uri
import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.model.NotificationPreferencesResponse
import am.noz.caelum.core.storage.PreferencesRepository
import am.noz.caelum.ui.navigation.AppRoute
import am.noz.caelum.ui.navigation.DeepLinkParser
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap

data class ParsedPushNotification(
    val reason: String,
    val title: String,
    val message: String,
    val channelId: String,
    val recipientDid: String?,
    val uri: String?,
    val subject: String?,
    val convoId: String?,
    val messageId: String?,
    val authorDid: String?,
    val authorHandle: String?,
    val targetRoute: AppRoute,
)

/**
 * Caelum push gateway registration, preference sync (`did:web:push.noz.am#bsky_notif`),
 * client-side suppression, and FCM data-only payload parsing/routing.
 */
class PushGatewayManager(
    private val api: AtprotoApi,
    private val preferences: PreferencesRepository,
    val pushGatewayDid: String = DEFAULT_PUSH_GATEWAY_DID,
) {
    private val lastSyncedSignatures = ConcurrentHashMap<String, String>()

    private val pushServiceDid: String = pushGatewayDid.substringBefore('#')

    suspend fun registerToken(
        fcmToken: String,
        ageRestricted: Boolean = false,
    ) {
        api.registerPush(
            serviceDid = pushServiceDid,
            token = fcmToken,
            appId = CAELUM_APP_ID,
            ageRestricted = ageRestricted,
            proxyServiceDid = pushGatewayDid,
        )
    }

    suspend fun unregisterToken(fcmToken: String) {
        api.unregisterPush(
            serviceDid = pushServiceDid,
            token = fcmToken,
            appId = CAELUM_APP_ID,
            proxyServiceDid = pushGatewayDid,
        )
    }

    suspend fun syncPreferences(
        did: String,
        prefs: NotificationPreferencesResponse,
        force: Boolean = false,
        persistDisabledReasons: suspend (Set<String>) -> Unit = { preferences.setDisabledPushReasons(did, it) },
    ) {
        persistDisabledReasons(getDisabledPushReasons(prefs))

        val signature = computeGatewaySignature(did, pushGatewayDid, prefs)
        if (!force && lastSyncedSignatures[did] == signature) return

        val body = buildGatewayPreferenceBody(prefs)
        if (body.isEmpty()) return

        runCatching {
            api.putNotificationPreferencesV2(
                body = body,
                proxyServiceDid = pushGatewayDid,
            )
            lastSyncedSignatures[did] = signature
        }.onFailure {
            lastSyncedSignatures.remove(did)
        }
    }

    fun shouldDisplayNotification(recipientDid: String?, reason: String): Boolean {
        if (recipientDid.isNullOrBlank()) return true
        val disabled = preferences.getDisabledPushReasons(recipientDid)
        return !disabled.contains(reason)
    }

    fun clearSyncCache() {
        lastSyncedSignatures.clear()
    }

    companion object {
        const val DEFAULT_PUSH_GATEWAY_DID = "did:web:push.noz.am#bsky_notif"
        const val CAELUM_APP_ID = "am.noz.caelum"

        fun getDisabledPushReasons(prefs: NotificationPreferencesResponse): Set<String> {
            val disabled = linkedSetOf<String>()
            if (!prefs.follow.push) disabled.add("follow")
            if (!prefs.like.push) disabled.add("like")
            if (!prefs.likeViaRepost.push) disabled.add("like-via-repost")
            if (!prefs.mention.push) disabled.add("mention")
            if (!prefs.quote.push) disabled.add("quote")
            if (!prefs.reply.push) disabled.add("reply")
            if (!prefs.repost.push) disabled.add("repost")
            if (!prefs.repostViaRepost.push) disabled.add("repost-via-repost")
            if (!prefs.starterpackJoined.push) disabled.add("starterpack-joined")
            if (!prefs.subscribedPost.push) disabled.add("subscribed-post")
            if (!prefs.unverified.push) disabled.add("unverified")
            if (!prefs.verified.push) disabled.add("verified")
            if (prefs.chat?.push == false) disabled.add("chat-message")
            return disabled
        }

        fun buildGatewayPreferenceBody(prefs: NotificationPreferencesResponse): JsonObject {
            return buildJsonObject {
                fun entry(key: String, include: String?, list: Boolean, push: Boolean) {
                    put(
                        key,
                        buildJsonObject {
                            include?.let { put("include", JsonPrimitive(it)) }
                            put("list", JsonPrimitive(list))
                            put("push", JsonPrimitive(push))
                        }
                    )
                }
                entry("follow", prefs.follow.include, prefs.follow.list, prefs.follow.push)
                entry("like", prefs.like.include, prefs.like.list, prefs.like.push)
                entry("likeViaRepost", prefs.likeViaRepost.include, prefs.likeViaRepost.list, prefs.likeViaRepost.push)
                entry("mention", prefs.mention.include, prefs.mention.list, prefs.mention.push)
                entry("quote", prefs.quote.include, prefs.quote.list, prefs.quote.push)
                entry("reply", prefs.reply.include, prefs.reply.list, prefs.reply.push)
                entry("repost", prefs.repost.include, prefs.repost.list, prefs.repost.push)
                entry("repostViaRepost", prefs.repostViaRepost.include, prefs.repostViaRepost.list, prefs.repostViaRepost.push)
                entry("starterpackJoined", prefs.starterpackJoined.include, prefs.starterpackJoined.list, prefs.starterpackJoined.push)
                entry("subscribedPost", prefs.subscribedPost.include, prefs.subscribedPost.list, prefs.subscribedPost.push)
                entry("unverified", prefs.unverified.include, prefs.unverified.list, prefs.unverified.push)
                entry("verified", prefs.verified.include, prefs.verified.list, prefs.verified.push)
            }
        }

        fun computeGatewaySignature(
            did: String,
            gatewayDid: String,
            prefs: NotificationPreferencesResponse,
        ): String {
            val states = listOf(
                "follow:${if (prefs.follow.push) 1 else 0}",
                "like:${if (prefs.like.push) 1 else 0}",
                "likeViaRepost:${if (prefs.likeViaRepost.push) 1 else 0}",
                "mention:${if (prefs.mention.push) 1 else 0}",
                "quote:${if (prefs.quote.push) 1 else 0}",
                "reply:${if (prefs.reply.push) 1 else 0}",
                "repost:${if (prefs.repost.push) 1 else 0}",
                "repostViaRepost:${if (prefs.repostViaRepost.push) 1 else 0}",
                "starterpackJoined:${if (prefs.starterpackJoined.push) 1 else 0}",
                "subscribedPost:${if (prefs.subscribedPost.push) 1 else 0}",
                "unverified:${if (prefs.unverified.push) 1 else 0}",
                "verified:${if (prefs.verified.push) 1 else 0}",
            )
            return "$did|$gatewayDid|${states.joinToString(",")}"
        }

        /**
         * Parses both Expo-compatible JSON `data["body"]` and flat Caelum gateway key/values.
         */
        fun parseFcmData(data: Map<String, String>): ParsedPushNotification? {
            if (data.isEmpty()) return null
            val bodyJson = data["body"]?.let { raw ->
                runCatching { AtProtoJson.parseToJsonElement(raw).jsonObject }.getOrNull()
            }

            fun field(key: String): String? =
                data[key]?.takeIf { it.isNotBlank() }
                    ?: (bodyJson?.get(key) as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

            val reason = field("reason") ?: return null
            val uri = field("uri")
            val subject = field("subject")
            val convoId = field("convoId")
            val messageId = field("messageId")
            val recipientDid = field("recipientDid")
            val authorDid = field("authorDid")
            val authorHandle = field("authorHandle")
            val title = field("title") ?: "Caelum"
            val message = field("message") ?: title
            val channelId = field("channelId") ?: when (reason) {
                "chat-message" -> "chat-messages"
                "like", "like-via-repost" -> "like"
                "repost", "repost-via-repost" -> "repost"
                "follow" -> "follow"
                "mention" -> "mention"
                "reply" -> "reply"
                "quote" -> "quote"
                else -> "default"
            }

            val route: AppRoute = when {
                reason == "chat-message" && !convoId.isNullOrBlank() ->
                    AppRoute.MessagesConversation(convoId)
                reason == "follow" && !authorDid.isNullOrBlank() ->
                    AppRoute.Profile(authorHandle ?: authorDid)
                !subject.isNullOrBlank() && subject.startsWith("at://") ->
                    DeepLinkParser.atUriToRoute(subject) ?: AppRoute.Notifications
                !uri.isNullOrBlank() && uri.startsWith("at://") ->
                    DeepLinkParser.atUriToRoute(uri) ?: AppRoute.Notifications
                !authorDid.isNullOrBlank() ->
                    AppRoute.Profile(authorHandle ?: authorDid)
                else -> AppRoute.Notifications
            }

            return ParsedPushNotification(
                reason = reason,
                title = title,
                message = message,
                channelId = channelId,
                recipientDid = recipientDid,
                uri = uri,
                subject = subject,
                convoId = convoId,
                messageId = messageId,
                authorDid = authorDid,
                authorHandle = authorHandle,
                targetRoute = route,
            )
        }
    }
}
