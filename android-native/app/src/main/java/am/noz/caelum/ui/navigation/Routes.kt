package am.noz.caelum.ui.navigation

import android.net.Uri
import am.noz.caelum.core.model.StrongRef
import kotlinx.serialization.Serializable

/**
 * Type-safe native routes covering every destination in `src/routes.ts` & `src/Navigation.tsx`.
 */
@Serializable
sealed interface AppRoute {
    // Primary 5 tabs
    @Serializable data object Home : AppRoute
    @Serializable data class Search(
        val query: String? = null,
        val filters: SearchFilters = SearchFilters(),
    ) : AppRoute
    @Serializable data object Messages : AppRoute
    @Serializable data object Notifications : AppRoute
    @Serializable data object MyProfile : AppRoute

    // Secondary 5 drawer tabs
    @Serializable data object Atmosphere : AppRoute
    @Serializable data object Feeds : AppRoute
    @Serializable data object Lists : AppRoute
    @Serializable data object Bookmarks : AppRoute
    @Serializable data object Settings : AppRoute

    // Profiles, posts, threads, feeds, lists
    @Serializable data class Profile(val name: String) : AppRoute
    @Serializable data class ProfileFollowers(val name: String) : AppRoute
    @Serializable data class ProfileFollows(val name: String) : AppRoute
    @Serializable data class ProfileKnownFollowers(val name: String) : AppRoute
    @Serializable data class ProfileSearch(val name: String, val query: String? = null) : AppRoute
    @Serializable data class ProfileList(val name: String, val rkey: String) : AppRoute
    @Serializable data class ProfileLabelerLikedBy(val name: String) : AppRoute
    @Serializable data class PostThread(val name: String, val rkey: String) : AppRoute
    @Serializable data class PostLikedBy(val name: String, val rkey: String) : AppRoute
    @Serializable data class PostRepostedBy(val name: String, val rkey: String) : AppRoute
    @Serializable data class PostQuotes(val name: String, val rkey: String) : AppRoute
    @Serializable data class CustomFeed(val name: String, val rkey: String) : AppRoute
    @Serializable data class CustomFeedLikedBy(val name: String, val rkey: String) : AppRoute

    // Explore / tags / video
    @Serializable data class Hashtag(val tag: String) : AppRoute
    @Serializable data class Topic(val topic: String) : AppRoute
    @Serializable data class VideoFeed(val sourceUri: String = "", val title: String = "") : AppRoute

    // Notifications
    @Serializable data class NotificationsActivityList(val urisCsv: String) : AppRoute

    // Direct Messages
    @Serializable data object MessagesSettings : AppRoute
    @Serializable data object MessagesInbox : AppRoute
    @Serializable data class MessagesConversation(val conversation: String, val sharedPost: StrongRef? = null) : AppRoute
    @Serializable data class SendPostViaChat(val postUri: String, val postCid: String) : AppRoute
    @Serializable data class MessagesConversationSettings(val conversation: String) : AppRoute
    @Serializable data class MessagesJoinRequests(val conversation: String) : AppRoute

    // Starter Packs & Invites
    @Serializable data class StarterPack(val name: String, val rkey: String) : AppRoute
    @Serializable data class StarterPackShort(val code: String) : AppRoute
    @Serializable data object StarterPackWizard : AppRoute
    @Serializable data class StarterPackEdit(val rkey: String) : AppRoute
    @Serializable data object InviteScanner : AppRoute

    // Moderation & Moderation Inbox
    @Serializable data object Moderation : AppRoute
    @Serializable data object ModerationInbox : AppRoute
    @Serializable data object ModerationInboxSettings : AppRoute
    @Serializable data class ModerationInboxReportDetails(val moderationService: String, val id: String) : AppRoute
    @Serializable data class ModerationInboxNoticeDetails(val subjectJson: String = "") : AppRoute
    @Serializable data object ModerationModlists : AppRoute
    @Serializable data object ModerationMutedAccounts : AppRoute
    @Serializable data object ModerationBlockedAccounts : AppRoute
    @Serializable data object ModerationInteractionSettings : AppRoute
    @Serializable data object ModerationVerificationSettings : AppRoute

    // Settings (all 22+ settings screens)
    @Serializable data object LanguageSettings : AppRoute
    @Serializable data object AppPasswords : AppRoute
    @Serializable data object PreferencesFollowingFeed : AppRoute
    @Serializable data object PreferencesThreads : AppRoute
    @Serializable data object PreferencesExternalEmbeds : AppRoute
    @Serializable data object AccessibilitySettings : AppRoute
    @Serializable data object AppearanceSettings : AppRoute
    @Serializable data object BetaFeaturesSettings : AppRoute
    @Serializable data object SavedFeeds : AppRoute
    @Serializable data object AccountSettings : AppRoute
    @Serializable data object AutomationLabelSettings : AppRoute
    @Serializable data object PrivacyAndSecuritySettings : AppRoute
    @Serializable data object ActivityPrivacySettings : AppRoute
    @Serializable data object ContentAndMediaSettings : AppRoute
    @Serializable data object InterestsSettings : AppRoute
    @Serializable data object AboutSettings : AppRoute
    @Serializable data object InfrastructureSettings : AppRoute
    @Serializable data object CustomizationSettings : AppRoute
    @Serializable data object EmojiCredits : AppRoute
    @Serializable data object AppIconSettings : AppRoute
    @Serializable data object NotificationSettings : AppRoute
    @Serializable data object ActivityNotificationSettings : AppRoute
    @Serializable data object FindContactsSettings : AppRoute
    @Serializable data object FindContactsFlow : AppRoute

    // Debug & Support
    @Serializable data object Log : AppRoute
    @Serializable data object DebugMod : AppRoute
    @Serializable data object Support : AppRoute
    @Serializable data object PrivacyPolicy : AppRoute
    @Serializable data object TermsOfService : AppRoute
    @Serializable data object CommunityGuidelines : AppRoute
    @Serializable data object CopyrightPolicy : AppRoute
    @Serializable data class NotFound(val path: String = "") : AppRoute
}

enum class RootTab(val route: AppRoute) {
    HomeTab(AppRoute.Home),
    SearchTab(AppRoute.Search()),
    MessagesTab(AppRoute.Messages),
    NotificationsTab(AppRoute.Notifications),
    MyProfileTab(AppRoute.MyProfile),
    AtmosphereTab(AppRoute.Atmosphere),
    FeedsTab(AppRoute.Feeds),
    ListsTab(AppRoute.Lists),
    BookmarksTab(AppRoute.Bookmarks),
    SettingsTab(AppRoute.Settings),
}

sealed interface DeepLinkTarget {
    data class RouteTarget(val route: AppRoute, val targetTab: RootTab? = null) : DeepLinkTarget
    data class ComposeIntentTarget(
        val text: String? = null,
        val imageUris: List<String> = emptyList(),
        val videoUri: String? = null,
    ) : DeepLinkTarget
}

object DeepLinkParser {
    private val WIDGET_TRACKING_PARAMS = setOf("ref_src", "ref_rank", "ref_rec_id")
    private val INTERNAL_HOSTS = setOf("bsky.app", "www.bsky.app", "main.bsky.dev")

    fun parseUri(raw: String): DeepLinkTarget? {
        if (raw.isBlank()) return null
        val normalized = normalizeUrl(raw) ?: return null
        val uri = Uri.parse(normalized)
        val path = uri.path.orEmpty().trimEnd('/').ifEmpty { "/" }
        val segments = path.split('/').filter { it.isNotEmpty() }

        if (segments.firstOrNull() == "intent" && segments.getOrNull(1) == "compose") {
            val text = uri.getQueryParameter("text")
            val imageUris = uri.getQueryParameter("imageUris")
                ?.split(',')
                ?.map { it.substringBefore('|') }
                ?.filter { it.isNotBlank() }
                .orEmpty()
            val videoUri = uri.getQueryParameter("videoUri")?.substringBefore('|')
            return DeepLinkTarget.ComposeIntentTarget(text = text, imageUris = imageUris, videoUri = videoUri)
        }

        val target: Pair<AppRoute, RootTab?> = when {
            path == "/" || path == "/download" -> AppRoute.Home to RootTab.HomeTab
            path == "/search" -> AppRoute.Search(
                query = uri.getQueryParameter("q"),
                filters = SearchFilters.fromUri(uri),
            ) to RootTab.SearchTab
            path == "/atmosphere" -> AppRoute.Atmosphere to RootTab.AtmosphereTab
            path == "/feeds" -> AppRoute.Feeds to RootTab.FeedsTab
            path == "/notifications" -> AppRoute.Notifications to RootTab.NotificationsTab
            path == "/notifications/activity" -> AppRoute.NotificationsActivityList(uri.getQueryParameter("posts").orEmpty()) to RootTab.NotificationsTab
            path == "/notifications/settings" -> AppRoute.NotificationSettings to RootTab.SettingsTab
            path == "/lists" -> AppRoute.Lists to RootTab.ListsTab
            path == "/saved" -> AppRoute.Bookmarks to RootTab.BookmarksTab
            path == "/video-feed" -> AppRoute.VideoFeed() to null
            path == "/invite/scan" -> AppRoute.InviteScanner to null
            path == "/find-contacts" -> AppRoute.FindContactsFlow to null

            // Moderation
            path == "/moderation" -> AppRoute.Moderation to null
            path == "/moderation/inbox" -> AppRoute.ModerationInbox to null
            path == "/moderation/inbox/settings" -> AppRoute.ModerationInboxSettings to null
            path == "/moderation/inbox/notice/details" -> AppRoute.ModerationInboxNoticeDetails() to null
            segments.size == 5 && segments[0] == "moderation" && segments[1] == "inbox" && segments[2] == "report" ->
                AppRoute.ModerationInboxReportDetails(segments[3], segments[4]) to null
            path == "/moderation/modlists" -> AppRoute.ModerationModlists to null
            path == "/moderation/muted-accounts" -> AppRoute.ModerationMutedAccounts to null
            path == "/moderation/blocked-accounts" -> AppRoute.ModerationBlockedAccounts to null
            path == "/moderation/interaction-settings" -> AppRoute.ModerationInteractionSettings to null
            path == "/moderation/verification-settings" -> AppRoute.ModerationVerificationSettings to null

            // Settings
            path == "/settings" -> AppRoute.Settings to RootTab.SettingsTab
            path == "/settings/language" -> AppRoute.LanguageSettings to RootTab.SettingsTab
            path == "/settings/app-passwords" -> AppRoute.AppPasswords to RootTab.SettingsTab
            path == "/settings/following-feed" -> AppRoute.PreferencesFollowingFeed to RootTab.SettingsTab
            path == "/settings/threads" -> AppRoute.PreferencesThreads to RootTab.SettingsTab
            path == "/settings/external-embeds" -> AppRoute.PreferencesExternalEmbeds to RootTab.SettingsTab
            path == "/settings/accessibility" -> AppRoute.AccessibilitySettings to RootTab.SettingsTab
            path == "/settings/appearance" -> AppRoute.AppearanceSettings to RootTab.SettingsTab
            path == "/settings/beta-features" -> AppRoute.BetaFeaturesSettings to RootTab.SettingsTab
            path == "/settings/saved-feeds" -> AppRoute.SavedFeeds to RootTab.SettingsTab
            path == "/settings/account" -> AppRoute.AccountSettings to RootTab.SettingsTab
            path == "/settings/automation-label" -> AppRoute.AutomationLabelSettings to RootTab.SettingsTab
            path == "/settings/privacy-and-security" -> AppRoute.PrivacyAndSecuritySettings to RootTab.SettingsTab
            path == "/settings/privacy-and-security/activity" -> AppRoute.ActivityPrivacySettings to RootTab.SettingsTab
            path == "/settings/content-and-media" -> AppRoute.ContentAndMediaSettings to RootTab.SettingsTab
            path == "/settings/interests" -> AppRoute.InterestsSettings to RootTab.SettingsTab
            path == "/settings/about" -> AppRoute.AboutSettings to RootTab.SettingsTab
            path == "/settings/infrastructure" -> AppRoute.InfrastructureSettings to RootTab.SettingsTab
            path == "/settings/customization" -> AppRoute.CustomizationSettings to RootTab.SettingsTab
            path == "/settings/about/emoji-credits" -> AppRoute.EmojiCredits to RootTab.SettingsTab
            path == "/settings/app-icon" -> AppRoute.AppIconSettings to RootTab.SettingsTab
            path == "/settings/notifications" -> AppRoute.NotificationSettings to RootTab.SettingsTab
            path == "/settings/notifications/activity" -> AppRoute.ActivityNotificationSettings to RootTab.SettingsTab
            path == "/settings/find-contacts" -> AppRoute.FindContactsSettings to RootTab.SettingsTab

            // Debug & Support
            path == "/sys/log" -> AppRoute.Log to null
            path == "/sys/debug-mod" -> AppRoute.DebugMod to null
            path == "/support" -> AppRoute.Support to null
            path == "/support/privacy" -> AppRoute.PrivacyPolicy to null
            path == "/support/tos" -> AppRoute.TermsOfService to null
            path == "/support/community-guidelines" -> AppRoute.CommunityGuidelines to null
            path == "/support/copyright" -> AppRoute.CopyrightPolicy to null

            // Hashtag / Topic
            segments.size == 2 && segments[0] == "hashtag" -> AppRoute.Hashtag(Uri.decode(segments[1])) to null
            segments.size == 2 && segments[0] == "topic" -> AppRoute.Topic(Uri.decode(segments[1])) to null

            // Messages
            path == "/messages" -> AppRoute.Messages to RootTab.MessagesTab
            path == "/messages/settings" -> AppRoute.MessagesSettings to RootTab.MessagesTab
            path == "/messages/inbox" -> AppRoute.MessagesInbox to RootTab.MessagesTab
            segments.size == 2 && segments[0] == "messages" -> AppRoute.MessagesConversation(segments[1]) to RootTab.MessagesTab
            segments.size == 3 && segments[0] == "messages" && segments[2] == "settings" ->
                AppRoute.MessagesConversationSettings(segments[1]) to RootTab.MessagesTab
            segments.size == 3 && segments[0] == "messages" && segments[2] == "requests" ->
                AppRoute.MessagesJoinRequests(segments[1]) to RootTab.MessagesTab
            segments.size == 2 && segments[0] == "chat" -> AppRoute.Home to RootTab.HomeTab

            // Starter Packs
            segments.size == 3 && (segments[0] == "start" || segments[0] == "starter-pack") ->
                AppRoute.StarterPack(segments[1], segments[2]) to null
            segments.size == 2 && segments[0] == "starter-pack-short" ->
                AppRoute.StarterPackShort(segments[1]) to null
            path == "/starter-pack/create" -> AppRoute.StarterPackWizard to null
            segments.size == 3 && segments[0] == "starter-pack" && segments[1] == "edit" ->
                AppRoute.StarterPackEdit(segments[2]) to null

            // Profile & Post & Feed & List subroutes
            segments.size >= 2 && segments[0] == "profile" -> {
                val name = Uri.decode(segments[1])
                when {
                    segments.size == 2 || (segments.size == 3 && segments[2] == "rss") ->
                        AppRoute.Profile(name) to null
                    segments.size == 3 && segments[2] == "followers" ->
                        AppRoute.ProfileFollowers(name) to null
                    segments.size == 3 && segments[2] == "follows" ->
                        AppRoute.ProfileFollows(name) to null
                    segments.size == 3 && segments[2] == "known-followers" ->
                        AppRoute.ProfileKnownFollowers(name) to null
                    segments.size == 3 && segments[2] == "search" ->
                        AppRoute.ProfileSearch(name) to null
                    segments.size == 4 && segments[2] == "lists" ->
                        AppRoute.ProfileList(name, segments[3]) to null
                    segments.size == 4 && segments[2] == "post" ->
                        AppRoute.PostThread(name, segments[3]) to null
                    segments.size == 5 && segments[2] == "post" && segments[4] == "liked-by" ->
                        AppRoute.PostLikedBy(name, segments[3]) to null
                    segments.size == 5 && segments[2] == "post" && segments[4] == "reposted-by" ->
                        AppRoute.PostRepostedBy(name, segments[3]) to null
                    segments.size == 5 && segments[2] == "post" && segments[4] == "quotes" ->
                        AppRoute.PostQuotes(name, segments[3]) to null
                    segments.size == 4 && segments[2] == "feed" ->
                        AppRoute.CustomFeed(name, segments[3]) to null
                    segments.size == 5 && segments[2] == "feed" && segments[4] == "liked-by" ->
                        AppRoute.CustomFeedLikedBy(name, segments[3]) to null
                    segments.size == 4 && segments[2] == "labeler" && segments[3] == "liked-by" ->
                        AppRoute.ProfileLabelerLikedBy(name) to null
                    else -> AppRoute.NotFound(path) to null
                }
            }

            else -> AppRoute.NotFound(path) to null
        }

        return DeepLinkTarget.RouteTarget(target.first, target.second)
    }

    fun atUriToRoute(atUri: String): AppRoute? {
        if (!atUri.startsWith("at://")) return null
        val parts = atUri.removePrefix("at://").split('/')
        if (parts.size < 3) return null
        val repo = parts[0]
        val collection = parts[1]
        val rkey = parts[2]
        return when (collection) {
            "app.bsky.feed.post" -> AppRoute.PostThread(repo, rkey)
            "app.bsky.feed.generator" -> AppRoute.CustomFeed(repo, rkey)
            "app.bsky.graph.list" -> AppRoute.ProfileList(repo, rkey)
            "app.bsky.graph.starterpack" -> AppRoute.StarterPack(repo, rkey)
            else -> AppRoute.Profile(repo)
        }
    }

    private fun normalizeUrl(raw: String): String? {
        val replaced = when {
            raw.startsWith("blueskyplus://", ignoreCase = true) ->
                "https://bsky.app/" + raw.substring("blueskyplus://".length).trimStart('/')
            raw.startsWith("bluesky://", ignoreCase = true) ->
                "https://bsky.app/" + raw.substring("bluesky://".length).trimStart('/')
            raw.startsWith("bsky://", ignoreCase = true) ->
                "https://bsky.app/" + raw.substring("bsky://".length).trimStart('/')
            raw.startsWith("/") -> "https://bsky.app$raw"
            else -> {
                val parsed = Uri.parse(raw)
                val scheme = parsed.scheme?.lowercase()
                val host = parsed.host?.lowercase()
                if ((scheme != "https" && scheme != "http") || host !in INTERNAL_HOSTS) {
                    return null
                }
                raw
            }
        }
        val uri = Uri.parse(replaced)
        if (uri.queryParameterNames.any { it in WIDGET_TRACKING_PARAMS }) {
            val builder = uri.buildUpon().clearQuery()
            for (name in uri.queryParameterNames) {
                if (name !in WIDGET_TRACKING_PARAMS) {
                    uri.getQueryParameters(name).forEach { builder.appendQueryParameter(name, it) }
                }
            }
            return builder.build().toString()
        }
        return replaced
    }
}
