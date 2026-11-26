package am.noz.caelum.ui.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.model.FeedViewPost
import am.noz.caelum.core.model.GeneratorView
import am.noz.caelum.core.model.ListView
import am.noz.caelum.core.model.PostView
import am.noz.caelum.core.model.ProfileViewBasic
import am.noz.caelum.core.model.SavedFeedPref
import am.noz.caelum.core.moderation.ModerationContext
import am.noz.caelum.core.moderation.ModerationEngine
import am.noz.caelum.core.session.SessionManager
import am.noz.caelum.core.storage.AppSettingsSnapshot
import am.noz.caelum.core.storage.ModerationUserSettings
import am.noz.caelum.di.AppContainer
import am.noz.caelum.ui.components.AvatarShape
import am.noz.caelum.ui.components.CaelumAvatar
import am.noz.caelum.ui.components.DisplayNameRow
import am.noz.caelum.ui.components.LocalAppContainer
import am.noz.caelum.ui.navigation.AppRoute
import am.noz.caelum.ui.openin.OpenInSubject
import am.noz.caelum.ui.post.EmbedImageItem
import am.noz.caelum.ui.post.PostCard
import am.noz.caelum.ui.post.formatCompactCount
import am.noz.caelum.ui.theme.LocalAlfPalette
import am.noz.caelum.ui.theme.toComposeColor
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Resolves a handle or DID string into a canonical DID.
 */
internal suspend fun resolveActorDid(container: AppContainer, actorHandleOrDid: String): String {
    val cleaned = actorHandleOrDid.trim().removePrefix("@")
    if (cleaned.startsWith("did:")) return cleaned
    val resolved = container.identityResolver.resolveHandle(cleaned)
    if (!resolved.isNullOrBlank()) return resolved
    return container.api.getProfile(cleaned).did
}

/**
 * Persists changes to the signed-in account's `savedFeedsPrefV2` locally and on the PDS,
 * then synchronizes `SessionManager` state.
 */
internal object SavedFeedsHelper {
    private val persistMutex = Mutex()

    fun isSaved(feeds: List<SavedFeedPref>, uri: String): Boolean =
        feeds.any { it.value == uri }

    fun isPinned(feeds: List<SavedFeedPref>, uri: String): Boolean =
        feeds.any { it.value == uri && it.pinned }

    suspend fun persistSavedFeeds(
        container: AppContainer,
        did: String,
        updatedFeeds: List<SavedFeedPref>,
        extraGenerator: GeneratorView? = null,
        extraList: ListView? = null,
    ) = container.sessionManager.runForCurrentAccount(did) {
        val normalized = SessionManager.normalizeSavedFeeds(updatedFeeds)
        container.sessionManager.updateSavedFeedsOptimistically(did, normalized, extraGenerator, extraList)

        persistMutex.withLock {
            container.preferences.setSavedFeeds(did, normalized)
            val rawPrefs = container.api.getActorPreferences()
            val existingV1 = rawPrefs.firstOrNull { el ->
                ((el as? JsonObject)?.get("\$type") as? JsonPrimitive)?.contentOrNull == "app.bsky.actor.defs#savedFeedsPref"
            } as? JsonObject

            val preserved = rawPrefs.filterNot { el ->
                val type = ((el as? JsonObject)?.get("\$type") as? JsonPrimitive)?.contentOrNull
                type == "app.bsky.actor.defs#savedFeedsPrefV2" || type == "app.bsky.actor.defs#savedFeedsPref"
            }.toMutableList()

            val encodedItems = JsonArray(
                normalized.map { pref ->
                    AtProtoJson.encodeToJsonElement(SavedFeedPref.serializer(), pref)
                },
            )
            preserved.add(
                buildJsonObject {
                    put("\$type", JsonPrimitive("app.bsky.actor.defs#savedFeedsPrefV2"))
                    put("items", encodedItems)
                },
            )

            // Keep legacy v1 savedFeedsPref in sync if it already existed on the PDS
            if (existingV1 != null) {
                val v1Pinned = JsonArray(
                    normalized.filter { it.pinned && it.type != "timeline" }.map { JsonPrimitive(it.value) },
                )
                val v1Saved = JsonArray(
                    normalized.filter { it.type != "timeline" }.map { JsonPrimitive(it.value) },
                )
                preserved.add(
                    buildJsonObject {
                        put("\$type", JsonPrimitive("app.bsky.actor.defs#savedFeedsPref"))
                        put("pinned", v1Pinned)
                        put("saved", v1Saved)
                        existingV1["timelineIndex"]?.let { put("timelineIndex", it) }
                    },
                )
            }

            container.api.putActorPreferences(JsonArray(preserved))
            // Only overwrite state from server if no newer local mutation arrived while saving
            if (container.sessionManager.state.value.pinnedFeeds == normalized) {
                container.sessionManager.syncPreferencesFromServer(did)
            }
        }
    }

    suspend fun toggleSave(
        container: AppContainer,
        did: String,
        currentFeeds: List<SavedFeedPref>,
        type: String,
        value: String,
        generator: GeneratorView? = null,
        listView: ListView? = null,
    ): List<SavedFeedPref> {
        val normalizedCurrent = SessionManager.normalizeSavedFeeds(currentFeeds)
        val existing = normalizedCurrent.any { it.value == value }
        val updated = if (existing) {
            normalizedCurrent.filterNot { it.value == value }
        } else {
            normalizedCurrent + SavedFeedPref(
                id = SessionManager.generateFeedPrefId(),
                type = type,
                value = value,
                pinned = false,
            )
        }
        val normalizedUpdated = SessionManager.normalizeSavedFeeds(updated)
        persistSavedFeeds(container, did, normalizedUpdated, generator, listView)
        return normalizedUpdated
    }

    suspend fun togglePin(
        container: AppContainer,
        did: String,
        currentFeeds: List<SavedFeedPref>,
        type: String,
        value: String,
        generator: GeneratorView? = null,
        listView: ListView? = null,
    ): List<SavedFeedPref> {
        val normalizedCurrent = SessionManager.normalizeSavedFeeds(currentFeeds)
        val currentlyPinned = isPinned(normalizedCurrent, value)
        val exists = normalizedCurrent.any { it.value == value }
        val updated = if (exists) {
            normalizedCurrent.map { pref ->
                if (pref.value == value) pref.copy(pinned = !currentlyPinned) else pref
            }
        } else {
            normalizedCurrent + SavedFeedPref(
                id = SessionManager.generateFeedPrefId(),
                type = type,
                value = value,
                pinned = true,
            )
        }
        val normalizedUpdated = SessionManager.normalizeSavedFeeds(updated)
        persistSavedFeeds(container, did, normalizedUpdated, generator, listView)
        return normalizedUpdated
    }

    suspend fun togglePinByPref(
        container: AppContainer,
        did: String,
        currentFeeds: List<SavedFeedPref>,
        pref: SavedFeedPref,
    ): List<SavedFeedPref> {
        val normalizedCurrent = SessionManager.normalizeSavedFeeds(currentFeeds)
        val targetPinned = !pref.pinned
        val updated = normalizedCurrent.map { item ->
            if (item.value == pref.value) item.copy(pinned = targetPinned) else item
        }
        val reordered = updated.filter { it.pinned } + updated.filterNot { it.pinned }
        val normalizedUpdated = SessionManager.normalizeSavedFeeds(reordered)
        persistSavedFeeds(container, did, normalizedUpdated)
        return normalizedUpdated
    }
}

/**
 * Shared paginated feed list used by `CustomFeedScreen`, `ProfileListScreen`, and `StarterPackScreen`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeedPostListContent(
    items: List<FeedViewPost>,
    isLoading: Boolean,
    isRefreshing: Boolean,
    isLoadingMore: Boolean,
    error: String?,
    hasMore: Boolean,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
    onOpenComposer: (replyTo: PostView?, quote: PostView?) -> Unit,
    onOpenEditPost: (PostView) -> Unit,
    onOpenIn: (OpenInSubject) -> Unit,
    onOpenReport: (PostView) -> Unit,
    onOpenLightbox: (List<EmbedImageItem>, Int) -> Unit,
    onOpenVideoLightbox: (String, String?, String?) -> Unit,
    emptyMessage: String = "No posts yet.",
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val palette = LocalAlfPalette.current
    val session by container.sessionManager.state.collectAsState()
    val settings by container.preferences.settingsFlow.collectAsState(initial = AppSettingsSnapshot())
    val shadows by container.postShadowStore.shadows.collectAsState()
    val scope = rememberCoroutineScope()

    val modSettingsFlow = remember(session.currentDid) {
        session.currentDid?.let { container.preferences.observeModerationSettings(it) }
    }
    val modSettings = modSettingsFlow?.collectAsState(initial = ModerationUserSettings())?.value

    val shouldLoadMore by remember(listState, items.size, hasMore, isLoadingMore) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            hasMore && !isLoadingMore && items.isNotEmpty() && lastVisible >= items.size - 6
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        when {
            isLoading && items.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            error != null && items.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(error, style = MaterialTheme.typography.bodyLarge, color = palette.contrast800.toComposeColor())
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onRefresh) {
                        Text("Try again")
                    }
                }
            }
            items.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(emptyMessage, style = MaterialTheme.typography.bodyLarge, color = palette.contrast600.toComposeColor())
                }
            }
            else -> {
                val mutedThreads = modSettings?.mutedThreads.orEmpty()
                val filteredItems = remember(items, shadows, session.moderationOpts, mutedThreads) {
                    items.mapNotNull { item ->
                        val applied = container.postShadowStore.applyToPost(item.post) ?: return@mapNotNull null
                        if (applied.uri in mutedThreads) return@mapNotNull null
                        val modUi = ModerationEngine.moderatePost(applied, session.moderationOpts)
                            .ui(ModerationContext.contentList)
                        if (modUi.filter) return@mapNotNull null
                        item.copy(post = applied)
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 84.dp),
                ) {
                    itemsIndexed(
                        items = filteredItems,
                        key = { idx, item -> "${item.post.uri}_${idx}" },
                    ) { _, item ->
                        val post = item.post
                        val parentHandle = ((item.reply?.parent?.get("author") as? JsonObject)
                            ?.get("handle") as? JsonPrimitive)?.content

                        PostCard(
                            post = post,
                            moderationOpts = session.moderationOpts,
                            currentUserDid = session.currentDid,
                            reason = item.reason,
                            replyParentAuthorHandle = parentHandle,
                            largeAltBadge = settings.largeAltBadgeEnabled,
                            disableAutoplay = settings.disableAutoplay,
                            disableHaptics = settings.disableHaptics,
                            useLinkRedirects = settings.infrastructure.useLinkRedirects,
                            openBskyLinksInCaelum = settings.openBskyLinksInCaelum,
                            onPostClick = { p ->
                                onNavigate(
                                    AppRoute.PostThread(
                                        p.author.handle.ifBlank { p.author.did },
                                        p.uri.substringAfterLast('/'),
                                    ),
                                )
                            },
                            onReplyClick = { p -> onOpenComposer(p, null) },
                            onQuoteClick = { p -> onOpenComposer(null, p) },
                            onRepostClick = { p -> scope.launch { container.postController.toggleRepost(p) } },
                            onLikeClick = { p -> scope.launch { container.postController.toggleLike(p) } },
                            onBookmarkClick = { p -> scope.launch { container.postController.toggleBookmark(p) } },
                            onEditPostClick = onOpenEditPost,
                            onDeletePostClick = { p -> scope.launch { container.postController.deletePost(p) } },
                            onToggleThreadMuteClick = { p -> scope.launch { container.postController.toggleThreadMute(p) } },
                            onHidePostClick = { p -> scope.launch { container.postController.hidePost(p.uri) } },
                            onReportPostClick = onOpenReport,
                            onOpenInClick = { p ->
                                onOpenIn(
                                    OpenInSubject.Post(
                                        p.uri,
                                        p.author.did,
                                        p.author.handle.ifBlank { p.author.did },
                                    ),
                                )
                            },
                            onOpenLightbox = onOpenLightbox,
                            onOpenVideoLightbox = onOpenVideoLightbox,
                            onNavigate = onNavigate,
                        )
                    }

                    if (isLoadingMore) {
                        item(key = "feed_footer_loader") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun GeneratorListRow(
    generator: GeneratorView,
    isSaved: Boolean,
    isPinned: Boolean,
    showControls: Boolean = true,
    onOpen: () -> Unit,
    onToggleSave: (() -> Unit)? = null,
    onTogglePin: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val palette = LocalAlfPalette.current
    val mutedColor = palette.contrast600.toComposeColor()
    val creatorHandle = generator.creator.handle.ifBlank { generator.creator.did }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .background(palette.contrast0.toComposeColor()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CaelumAvatar(
                url = generator.avatar,
                contentDescription = generator.displayName,
                size = 44.dp,
                shapeType = AvatarShape.RoundedSquare,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = generator.displayName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = palette.contrast1000.toComposeColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Feed by @$creatorHandle",
                    style = MaterialTheme.typography.bodySmall,
                    color = mutedColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!generator.description.isNullOrBlank()) {
                    Text(
                        text = generator.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.contrast800.toComposeColor(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val likeCount = generator.likeCount ?: 0
                if (likeCount > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = null,
                            tint = mutedColor,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = "Liked by ${formatCompactCount(likeCount)} users",
                            style = MaterialTheme.typography.labelSmall,
                            color = mutedColor,
                        )
                    }
                }
            }
            if (showControls) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onToggleSave != null) {
                        IconButton(onClick = onToggleSave, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = if (isSaved) "Remove from saved feeds" else "Save feed",
                                tint = if (isSaved) palette.primary500.toComposeColor() else mutedColor,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    if (onTogglePin != null) {
                        IconButton(onClick = onTogglePin, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                contentDescription = if (isPinned) "Unpin from home" else "Pin to home",
                                tint = if (isPinned) palette.primary500.toComposeColor() else mutedColor,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)
    }
}

@Composable
internal fun ActorListRow(
    profile: ProfileViewBasic,
    description: String? = null,
    onClick: () -> Unit,
    trailingContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val palette = LocalAlfPalette.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(palette.contrast0.toComposeColor()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CaelumAvatar(
                url = profile.avatar,
                contentDescription = profile.displayName ?: profile.handle,
                size = 42.dp,
                isLive = profile.status?.isActive == true,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                DisplayNameRow(profile = profile, showHandle = true)
                if (!description.isNullOrBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.contrast700.toComposeColor(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailingContent != null) {
                Spacer(Modifier.width(4.dp))
                trailingContent()
            }
        }
        HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)
    }
}

@Composable
internal fun PurposeBadge(purpose: String, modifier: Modifier = Modifier) {
    val palette = LocalAlfPalette.current
    val (label, bgColor, textColor) = when (purpose) {
        "app.bsky.graph.defs#modlist" -> Triple(
            "Moderation list",
            palette.negative50.toComposeColor(),
            palette.negative600.toComposeColor(),
        )
        "app.bsky.graph.defs#referencelist" -> Triple(
            "Starter pack list",
            palette.primary50.toComposeColor(),
            palette.primary600.toComposeColor(),
        )
        else -> Triple(
            "Curation list",
            palette.contrast50.toComposeColor(),
            palette.contrast700.toComposeColor(),
        )
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(0.5.dp, palette.contrast100.toComposeColor(), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.Medium,
        )
    }
}
