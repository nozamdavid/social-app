package am.noz.caelum.core.model

import am.noz.caelum.core.richtext.RichTextFacet
import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

val AtProtoJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    coerceInputValues = true
}

@Immutable
@Serializable
data class StoredAccount(
    val did: String,
    val handle: String,
    val serviceUrl: String,
    val pdsUrl: String? = null,
    val email: String? = null,
    val emailConfirmed: Boolean = true,
    val emailAuthFactor: Boolean = false,
    val signupQueued: Boolean = false,
    val active: Boolean = true,
    val status: String? = null,
    val isSelfHosted: Boolean = false,
    val displayName: String? = null,
    val avatar: String? = null,
)

data class SessionTokens(
    val accessJwt: String,
    val refreshJwt: String,
)

@Serializable
data class CreateSessionResponse(
    val did: String,
    val handle: String,
    val email: String? = null,
    val emailConfirmed: Boolean? = null,
    val emailAuthFactor: Boolean? = null,
    val accessJwt: String,
    val refreshJwt: String,
    val active: Boolean? = null,
    val status: String? = null,
    val didDoc: JsonObject? = null,
)

@Serializable
data class DidDocument(
    val id: String,
    val alsoKnownAs: List<String> = emptyList(),
    val service: List<DidService> = emptyList(),
)

@Serializable
data class DidService(
    val id: String,
    val type: String,
    val serviceEndpoint: String,
)

@Serializable
data class Label(
    val src: String,
    val uri: String,
    val cid: String? = null,
    val `val`: String,
    val neg: Boolean? = null,
    val cts: String? = null,
    val exp: String? = null,
)

@Serializable
data class ActorViewerState(
    val muted: Boolean? = null,
    val mutedByList: ListViewBasic? = null,
    val blockedBy: Boolean? = null,
    val blocking: String? = null,
    val blockingByList: ListViewBasic? = null,
    val following: String? = null,
    val followedBy: String? = null,
    val knownFollowers: KnownFollowers? = null,
    val activitySubscription: ActivitySubscription? = null,
)

@Serializable
data class KnownFollowers(
    val count: Int = 0,
    val followers: List<ProfileViewBasic> = emptyList(),
)

@Serializable
data class ActivitySubscription(
    val post: Boolean = false,
    val reply: Boolean = false,
)

@Serializable
data class VerificationView(
    val issuer: String = "",
    val uri: String = "",
    val isValid: Boolean = false,
    val createdAt: String = "",
)

@Serializable
data class VerificationState(
    val verifications: List<VerificationView> = emptyList(),
    val verifiedStatus: String? = null,
    val trustedVerifierStatus: String? = null,
)

@Serializable
data class AssociatedProfile(
    val lists: Int? = null,
    val feedgens: Int? = null,
    val starterPacks: Int? = null,
    val labeler: Boolean? = null,
    val chat: AssociatedChat? = null,
    val activitySubscription: AssociatedActivitySubscription? = null,
)

@Serializable
data class AssociatedChat(
    val allowIncoming: String? = null,
)

@Serializable
data class AssociatedActivitySubscription(
    val allowSubscriptions: String? = null,
)

@Serializable
data class ActorStatusView(
    val status: String = "",
    val record: JsonObject? = null,
    val embed: JsonObject? = null,
    val expiresAt: String? = null,
    val isActive: Boolean? = null,
)

@Immutable
@Serializable
data class ProfileViewBasic(
    val did: String = "",
    val handle: String = "",
    val displayName: String? = null,
    val pronouns: String? = null,
    val avatar: String? = null,
    val associated: AssociatedProfile? = null,
    val viewer: ActorViewerState? = null,
    val labels: List<Label> = emptyList(),
    val verification: VerificationState? = null,
    val status: ActorStatusView? = null,
)

@Immutable
@Serializable
data class ProfileView(
    val did: String = "",
    val handle: String = "",
    val displayName: String? = null,
    val pronouns: String? = null,
    val description: String? = null,
    val avatar: String? = null,
    val associated: AssociatedProfile? = null,
    val indexedAt: String? = null,
    val createdAt: String? = null,
    val viewer: ActorViewerState? = null,
    val labels: List<Label> = emptyList(),
    val verification: VerificationState? = null,
    val status: ActorStatusView? = null,
)

@Immutable
@Serializable
data class ProfileViewDetailed(
    val did: String,
    val handle: String,
    val displayName: String? = null,
    val pronouns: String? = null,
    val description: String? = null,
    val avatar: String? = null,
    val banner: String? = null,
    val website: String? = null,
    val followersCount: Int? = null,
    val followsCount: Int? = null,
    val postsCount: Int? = null,
    val associated: AssociatedProfile? = null,
    val pinnedPost: StrongRef? = null,
    val viewer: ActorViewerState? = null,
    val labels: List<Label> = emptyList(),
    val verification: VerificationState? = null,
    val status: ActorStatusView? = null,
    val joinedViaStarterPack: StarterPackViewBasic? = null,
    val createdAt: String? = null,
)

@Serializable
data class StrongRef(
    val uri: String,
    val cid: String,
)

@Serializable
data class PostViewerState(
    val repost: String? = null,
    val like: String? = null,
    val bookmarked: Boolean? = null,
    val threadMuted: Boolean? = null,
    val replyDisabled: Boolean? = null,
    val embeddingDisabled: Boolean? = null,
    val pinned: Boolean? = null,
)

@Immutable
@Serializable
data class PostView(
    val uri: String,
    val cid: String,
    val author: ProfileViewBasic,
    val record: JsonObject,
    val embed: JsonObject? = null,
    val bookmarkCount: Int? = null,
    val replyCount: Int? = null,
    val repostCount: Int? = null,
    val likeCount: Int? = null,
    val quoteCount: Int? = null,
    val indexedAt: String,
    val viewer: PostViewerState? = null,
    val labels: List<Label> = emptyList(),
    val threadgate: ThreadgateView? = null,
)

@Serializable
data class ThreadgateView(
    val uri: String? = null,
    val cid: String? = null,
    val record: JsonObject? = null,
    val lists: List<ListViewBasic> = emptyList(),
)

@Immutable
@Serializable
data class FeedViewPost(
    val post: PostView,
    val reply: ReplyRef? = null,
    val reason: JsonObject? = null,
    val feedContext: String? = null,
    val reqId: String? = null,
)

@Serializable
data class ReplyRef(
    val root: JsonObject,
    val parent: JsonObject,
    val grandparentAuthor: ProfileViewBasic? = null,
)

@Serializable
data class FeedResponse(
    val cursor: String? = null,
    val feed: List<FeedViewPost> = emptyList(),
)

@Serializable
data class ListViewBasic(
    val uri: String,
    val cid: String,
    val name: String,
    val purpose: String,
    val avatar: String? = null,
    val listItemCount: Int? = null,
    val labels: List<Label> = emptyList(),
    val viewer: ListViewerState? = null,
)

@Serializable
data class ListViewerState(
    val muted: Boolean? = null,
    val blocked: String? = null,
)

@Serializable
data class ListView(
    val uri: String = "",
    val cid: String = "",
    val creator: ProfileView = ProfileView(),
    val name: String = "",
    val purpose: String = "",
    val description: String? = null,
    val descriptionFacets: List<JsonObject> = emptyList(),
    val avatar: String? = null,
    val listItemCount: Int? = null,
    val labels: List<Label> = emptyList(),
    val viewer: ListViewerState? = null,
    val indexedAt: String? = null,
)

@Serializable
data class GeneratorView(
    val uri: String = "",
    val cid: String = "",
    val did: String = "",
    val creator: ProfileView = ProfileView(),
    val displayName: String = "",
    val description: String? = null,
    val descriptionFacets: List<JsonObject> = emptyList(),
    val avatar: String? = null,
    val likeCount: Int? = null,
    val acceptsInteractions: Boolean? = null,
    val labels: List<Label> = emptyList(),
    val viewer: GeneratorViewerState? = null,
    val indexedAt: String? = null,
)

@Serializable
data class GeneratorViewerState(
    val like: String? = null,
)

@Serializable
data class StarterPackViewBasic(
    val uri: String,
    val cid: String,
    val record: JsonObject,
    val creator: ProfileViewBasic,
    val listItemCount: Int? = null,
    val joinedWeekCount: Int? = null,
    val joinedAllTimeCount: Int? = null,
    val labels: List<Label> = emptyList(),
    val indexedAt: String? = null,
)

@Serializable
data class SavedFeedPref(
    val id: String = "",
    val type: String = "feed",
    val value: String = "",
    val pinned: Boolean = false,
)

@Serializable
data class MutedWord(
    val id: String? = null,
    val value: String,
    val targets: List<String> = listOf("content"),
    val actorTarget: String? = "all",
    val expiresAt: String? = null,
)

@Serializable
data class LabelerPrefItem(
    val did: String,
)

@Serializable
data class ContentLabelPref(
    val labelerDid: String? = null,
    val label: String,
    val visibility: String,
)

@Serializable
data class LabelerViewDetailed(
    val uri: String,
    val cid: String,
    val creator: ProfileView,
    val policies: LabelerPolicies,
    val likeCount: Int? = null,
    val viewer: LabelerViewerState? = null,
    val indexedAt: String? = null,
    val labels: List<Label> = emptyList(),
    val reasonTypes: List<String>? = null,
    val subjectTypes: List<String>? = null,
    val subjectCollections: List<String>? = null,
)

@Serializable
data class LabelerPolicies(
    val labelValues: List<String> = emptyList(),
    val labelValueDefinitions: List<LabelValueDefinition> = emptyList(),
)

@Serializable
data class LabelValueDefinition(
    val identifier: String,
    val severity: String = "inform",
    val blurs: String = "none",
    val defaultSetting: String? = "warn",
    val adultOnly: Boolean? = null,
    val locales: List<LabelValueDefinitionStrings> = emptyList(),
)

@Serializable
data class LabelValueDefinitionStrings(
    val lang: String,
    val name: String,
    val description: String,
)

@Serializable
data class LabelerViewerState(
    val like: String? = null,
)

@Serializable
data class NotificationItem(
    val uri: String,
    val cid: String,
    val author: ProfileView,
    val reason: String,
    val reasonSubject: String? = null,
    val record: JsonObject,
    val isRead: Boolean,
    val indexedAt: String,
    val labels: List<Label> = emptyList(),
)

@Serializable
data class NotificationPrefEntry(
    val include: String? = null,
    val list: Boolean = true,
    val push: Boolean = true,
)

@Serializable
data class NotificationPreferencesResponse(
    val chat: ChatNotificationPref? = null,
    val follow: NotificationPrefEntry = NotificationPrefEntry(),
    val like: NotificationPrefEntry = NotificationPrefEntry(),
    val likeViaRepost: NotificationPrefEntry = NotificationPrefEntry(),
    val mention: NotificationPrefEntry = NotificationPrefEntry(),
    val quote: NotificationPrefEntry = NotificationPrefEntry(),
    val reply: NotificationPrefEntry = NotificationPrefEntry(),
    val repost: NotificationPrefEntry = NotificationPrefEntry(),
    val repostViaRepost: NotificationPrefEntry = NotificationPrefEntry(),
    val starterpackJoined: NotificationPrefEntry = NotificationPrefEntry(),
    val subscribedPost: NotificationPrefEntry = NotificationPrefEntry(),
    val unverified: NotificationPrefEntry = NotificationPrefEntry(),
    val verified: NotificationPrefEntry = NotificationPrefEntry(),
)

@Serializable
data class ChatNotificationPref(
    val include: String = "all",
    val push: Boolean = true,
)

@Serializable
data class BookmarkView(
    val subject: StrongRef,
    val createdAt: String? = null,
    val item: JsonObject,
)

@Serializable
data class ConvoView(
    val id: String = "",
    val rev: String = "",
    val members: List<ProfileViewBasic> = emptyList(),
    val muted: Boolean = false,
    val opened: Boolean? = null,
    val unreadCount: Int = 0,
    val status: String? = null,
    val lastMessage: JsonObject? = null,
    val lastReaction: JsonObject? = null,
    val kind: JsonElement? = null,
    val details: JsonElement? = null,
)

@Serializable
data class ChatMessageSender(
    val did: String = "",
)

@Serializable
data class ChatMessageReaction(
    val value: String = "",
    val sender: ChatMessageSender = ChatMessageSender(),
    val createdAt: String? = null,
)

@Serializable
data class ChatMessageView(
    val id: String = "",
    val rev: String = "",
    val text: String = "",
    val facets: List<RichTextFacet> = emptyList(),
    val embed: JsonObject? = null,
    val reactions: List<ChatMessageReaction> = emptyList(),
    val sender: ChatMessageSender = ChatMessageSender(),
    val sentAt: String = "",
)

@Serializable
data class DraftView(
    val id: String,
    val draft: JsonObject,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class SuggestedAccount(
    val did: String,
    val handle: String,
    val avatar: String? = null,
    val displayName: String? = null,
)

@Serializable
data class BlobRef(
    @SerialName("\$type") val type: String = "blob",
    val ref: BlobLink,
    val mimeType: String,
    val size: Long,
)

@Serializable
data class BlobLink(
    @SerialName("\$link") val link: String,
)

@Serializable
data class ThreadV2Item(
    val uri: String,
    val depth: Int,
    val value: JsonObject,
)

@Serializable
data class ThreadV2Response(
    val thread: List<ThreadV2Item> = emptyList(),
    val hasOtherReplies: Boolean = false,
    val threadgate: ThreadgateView? = null,
)

@Serializable
data class TrendTopicView(
    val topic: String,
    val displayName: String,
    val link: String,
    val description: String? = null,
    val startedAt: String? = null,
    val postCount: Int? = null,
    val status: String? = null,
    val category: String? = null,
    val actors: List<ProfileViewBasic> = emptyList(),
)
