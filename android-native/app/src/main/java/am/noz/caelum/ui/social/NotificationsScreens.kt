package am.noz.caelum.ui.social

import am.noz.caelum.core.i18n.LocalI18n
import am.noz.caelum.core.i18n.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.model.NotificationItem
import am.noz.caelum.core.model.PostView
import am.noz.caelum.core.model.ProfileView
import am.noz.caelum.core.model.ProfileViewBasic
import am.noz.caelum.core.model.ProfileViewDetailed
import am.noz.caelum.core.model.TimeFormatter
import am.noz.caelum.core.moderation.ModerationEngine
import am.noz.caelum.core.session.ScreenStateCache
import am.noz.caelum.core.storage.AppSettingsSnapshot
import am.noz.caelum.ui.components.CaelumAvatar
import am.noz.caelum.ui.components.DisplayNameRow
import am.noz.caelum.ui.components.LocalAppContainer
import am.noz.caelum.ui.components.LocalScreenStateCache
import am.noz.caelum.ui.components.rememberAccountCoroutineScope
import am.noz.caelum.ui.navigation.AppRoute
import am.noz.caelum.ui.openin.OpenInSubject
import am.noz.caelum.ui.post.EmbedImageItem
import am.noz.caelum.ui.post.PostCard
import am.noz.caelum.ui.theme.LocalAlfPalette
import am.noz.caelum.ui.theme.toComposeColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant

private enum class NotificationTab(
    val key: String,
    val title: String,
    val emptyMessage: String,
    val reasons: List<String> = emptyList(),
    val fallbackTitle: String = title,
) {
    All("all", "All", "No notifications yet"),
    Following("people-i-follow", "People I follow", "No notifications from people you follow yet"),
    Replies("conversations", "Replies", "No replies to show yet", listOf("reply", "quote", "mention")),
    Follows("followers", "Follows", "No followers to show yet", listOf("follow"), fallbackTitle = "New followers"),
    PostAlerts(
        "activity",
        "Post alerts",
        "No post alerts yet",
        listOf("subscribed-post"),
        fallbackTitle = "Activity notifications",
    );

    @Composable
    fun localizedTitle(): String {
        val titleTranslation = tr(title, context = "notifications tab")
        if (titleTranslation != title) return titleTranslation
        // Contextual keys can still contain English in an otherwise translated catalog.
        val alternateTranslation = tr(fallbackTitle)
        return alternateTranslation.takeUnless { it == fallbackTitle } ?: titleTranslation
    }

    fun includes(item: NotificationItem): Boolean = when (this) {
        All -> true
        Following -> !item.author.viewer?.following.isNullOrBlank()
        else -> item.reason in reasons
    }
}

private data class NotificationsFeedState(
    val items: List<NotificationItem> = emptyList(),
    val postsByUri: Map<String, PostView> = emptyMap(),
    val cursor: String? = null,
    val loadedOnce: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: Boolean = false,
    val retryPagination: Boolean = false,
)

private sealed interface NotificationRowModel {
    val key: String

    data class PostNotificationRow(
        override val key: String,
        val notification: NotificationItem,
        val post: PostView?,
    ) : NotificationRowModel

    data class GroupedNotificationRow(
        override val key: String,
        val reason: String,
        val reasonSubject: String?,
        val notifications: List<NotificationItem>,
        val subjectPost: PostView?,
        val hasUnread: Boolean,
        val latestIndexedAt: String,
    ) : NotificationRowModel
}

private val INLINE_POST_REASONS = setOf("reply", "mention", "quote", "subscribed-post")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onOpenDrawer: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
    onOpenComposer: (replyTo: PostView?, quote: PostView?) -> Unit,
    onOpenEditPost: (PostView) -> Unit,
    onOpenIn: (OpenInSubject) -> Unit,
    onOpenReport: (PostView) -> Unit,
    onOpenLightbox: (List<EmbedImageItem>, Int) -> Unit,
    onOpenVideoLightbox: (String, String?, String?) -> Unit,
) {
    val container = LocalAppContainer.current
    val screenCache = LocalScreenStateCache.current
    val palette = LocalAlfPalette.current
    val session by container.sessionManager.state.collectAsState()
    val settings by container.preferences.settingsFlow.collectAsState(initial = AppSettingsSnapshot())
    val shadows by container.postShadowStore.shadows.collectAsState()
    val scope = rememberAccountCoroutineScope()

    val cachedNotifs = remember(session.currentDid) { screenCache.notifications }
    var selectedTab by remember(session.currentDid) {
        mutableStateOf(NotificationTab.entries.firstOrNull { it.key == cachedNotifs?.selectedTabKey } ?: NotificationTab.All)
    }
    var seenAt by remember(session.currentDid) { mutableStateOf(cachedNotifs?.seenAt) }
    val states = remember(session.currentDid) {
        mutableStateMapOf<NotificationTab, NotificationsFeedState>().apply {
            NotificationTab.entries.forEach { tab ->
                cachedNotifs?.tabs?.get(tab.key)?.let { cached ->
                    put(
                        tab,
                        NotificationsFeedState(
                            items = cached.items,
                            postsByUri = cached.postsByUri,
                            cursor = cached.cursor,
                            loadedOnce = cached.loadedOnce,
                        ),
                    )
                }
            }
        }
    }
    val activeListState = screenCache.rememberCachedLazyListState("notifications_${selectedTab.key}")

    fun persistNotificationsCache() {
        screenCache.notifications = ScreenStateCache.CachedNotificationsScreen(
            tabs = states.filterValues { it.loadedOnce }.mapKeys { it.key.key }.mapValues { (_, state) ->
                ScreenStateCache.CachedNotificationsTab(
                    items = state.items,
                    postsByUri = state.postsByUri,
                    cursor = state.cursor,
                    loadedOnce = state.loadedOnce,
                )
            },
            selectedTabKey = selectedTab.key,
            seenAt = seenAt,
        )
    }

    suspend fun fetchMissingPosts(items: List<NotificationItem>, existing: Map<String, PostView>): Map<String, PostView> {
        val uris = buildSet {
            for (item in items) {
                if (item.reason in INLINE_POST_REASONS && item.uri.contains("/app.bsky.feed.post/")) {
                    add(item.uri)
                }
                val subj = item.reasonSubject
                if (!subj.isNullOrBlank() && subj.contains("/app.bsky.feed.post/")) {
                    add(subj)
                }
            }
        }.filterNot { it in existing }

        if (uris.isEmpty()) return existing
        val fetched = coroutineScope {
            uris.chunked(25).map { chunk ->
                async { runCatching { container.api.getPosts(chunk) }.getOrDefault(emptyList()) }
            }.awaitAll().flatten()
        }
        return existing + fetched.associateBy { it.uri }
    }

    suspend fun loadTab(tab: NotificationTab, refresh: Boolean = false, loadMore: Boolean = false) {
        if (!session.hasSession) return
        val current = states[tab] ?: NotificationsFeedState()
        if (current.isLoading || current.isRefreshing || current.isLoadingMore) return
        if (loadMore && current.cursor.isNullOrBlank()) return
        if (!refresh && !loadMore && current.loadedOnce) return

        states[tab] = current.copy(
            isLoading = !refresh && !loadMore,
            isRefreshing = refresh,
            isLoadingMore = loadMore,
            error = false,
            retryPagination = false,
        )

        try {
            val requestedAt = Instant.now()
            var cursor = if (loadMore) current.cursor else null
            val existingItems = if (loadMore) current.items else emptyList()
            val existingIds = existingItems.mapTo(mutableSetOf(), NotificationItem::identity)
            val newItems = mutableListOf<NotificationItem>()
            var responseSeenAt: String? = null

            /*
             * "People I follow" has no reason parameter. Continue past sparse
             * pages, and give the user a continuation button if the scan is empty.
             */
            for (page in 0 until 4) {
                val response = container.api.listNotifications(reasons = tab.reasons, cursor = cursor, limit = 40)
                if (page == 0) responseSeenAt = response.seenAt
                newItems += response.notifications.filter { tab.includes(it) && existingIds.add(it.identity()) }
                cursor = response.cursor?.takeIf { next -> next.isNotBlank() && next != cursor }
                if (cursor == null || newItems.isNotEmpty()) break
            }
            val items = existingItems + newItems
            val posts = fetchMissingPosts(newItems, if (loadMore) current.postsByUri else emptyMap())
            if (!loadMore && (seenAt == null || refresh)) {
                seenAt = responseSeenAt
            }
            states[tab] = NotificationsFeedState(items = items, postsByUri = posts, cursor = cursor, loadedOnce = true)
            persistNotificationsCache()

            if (tab == NotificationTab.All && !loadMore && selectedTab == tab) {
                val newest = items.mapNotNull { TimeFormatter.parseInstantOrNull(it.indexedAt) }.maxOrNull()
                val safeCutoff = requestedAt.minusSeconds(10)
                val shownThrough = newest?.let { maxOf(it, safeCutoff) } ?: safeCutoff
                runCatching {
                    container.api.updateNotificationsSeen(shownThrough.toString())
                    container.sessionManager.refreshUnreadCounts()
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (_: Exception) {
            states[tab] = current.copy(error = true, retryPagination = loadMore)
        }
    }

    LaunchedEffect(selectedTab, session.currentDid) {
        persistNotificationsCache()
        scope.launch { loadTab(selectedTab) }
    }

    val pageState = states[selectedTab] ?: NotificationsFeedState()

    val rows = remember(pageState.items, pageState.postsByUri, shadows, session.moderationOpts, seenAt) {
        val snapshot = TimeFormatter.parseInstantOrNull(seenAt)
        buildNotificationRows(
            items = pageState.items.map { item ->
                val indexedAt = TimeFormatter.parseInstantOrNull(item.indexedAt)
                if (snapshot != null && indexedAt != null) item.copy(isRead = indexedAt <= snapshot) else item
            },
            postsByUri = pageState.postsByUri,
            applyShadow = { container.postShadowStore.applyToPost(it) },
            isActorBlocked = { profile ->
                !profile.viewer?.blocking.isNullOrBlank() ||
                    profile.viewer?.blockedBy == true ||
                    ModerationEngine.moderateProfile(profile, session.moderationOpts).blocked
            },
        )
    }

    val shouldLoadMore by remember(activeListState, rows.size) {
        derivedStateOf {
            val lastVisible = activeListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            rows.isNotEmpty() && lastVisible >= 0 && lastVisible >= rows.size - 5
        }
    }
    LaunchedEffect(shouldLoadMore, selectedTab, rows.size) {
        if (shouldLoadMore && !pageState.error) {
            scope.launch { loadTab(selectedTab, loadMore = true) }
        }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(palette.contrast0.toComposeColor())) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.contrast0.toComposeColor()),
                    navigationIcon = {
                        IconButton(
                            onClick = onOpenDrawer,
                            modifier = Modifier.testTag("notifications_drawer_button"),
                        ) {
                            Icon(Icons.Filled.Menu, contentDescription = tr("Open drawer menu"))
                        }
                    },
                    title = {
                        Text(
                            text = tr("Notifications"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    actions = {
                        IconButton(onClick = { onNavigate(AppRoute.NotificationSettings) }) {
                            Icon(Icons.Filled.Settings, contentDescription = tr("Notification settings"))
                        }
                    },
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    NotificationTab.entries.forEach { tab ->
                        val selected = selectedTab == tab
                        val requester = remember { BringIntoViewRequester() }
                        LaunchedEffect(selected) {
                            if (selected) requester.bringIntoView()
                        }
                        Box(
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("notifications_tab_${tab.key}")
                                .bringIntoViewRequester(requester)
                                .selectable(
                                    selected = selected,
                                    role = Role.Tab,
                                    onClick = {
                                        if (selected) {
                                            scope.launch {
                                                if (activeListState.firstVisibleItemIndex > 0 ||
                                                    activeListState.firstVisibleItemScrollOffset > 0
                                                ) {
                                                    activeListState.animateScrollToItem(0)
                                                } else {
                                                    loadTab(tab, refresh = true)
                                                }
                                            }
                                        } else {
                                            selectedTab = tab
                                        }
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = tab.localizedTitle(),
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(
                                        if (selected) palette.contrast100.toComposeColor() else Color.Transparent
                                    )
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                color = if (selected) palette.contrast1000.toComposeColor() else palette.contrast700.toComposeColor(),
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                            )
                        }
                    }
                }
                HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)
            }
        },
        containerColor = palette.contrast0.toComposeColor(),
    ) { padding ->
        if (!session.hasSession) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tr("Sign in to view your notifications."),
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.contrast600.toComposeColor(),
                )
            }
            return@Scaffold
        }

        PullToRefreshBox(
            isRefreshing = pageState.isRefreshing,
            onRefresh = { scope.launch { loadTab(selectedTab, refresh = true) } },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                pageState.isLoading || !pageState.loadedOnce && !pageState.error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                pageState.error && !pageState.loadedOnce -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(tr("Something went wrong"), style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { scope.launch { loadTab(selectedTab, refresh = true) } }) {
                            Text(tr("Try again"))
                        }
                    }
                }
                rows.isEmpty() && pageState.cursor == null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsNone,
                            contentDescription = null,
                            tint = palette.contrast400.toComposeColor(),
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = if (!pageState.error) tr(selectedTab.emptyMessage) else tr("Something went wrong"),
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.contrast700.toComposeColor(),
                        )
                        if (pageState.error) {
                            Button(onClick = { scope.launch { loadTab(selectedTab, refresh = true) } }) {
                                Text(tr("Try again"))
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        state = activeListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("notifications_list"),
                        contentPadding = PaddingValues(bottom = 84.dp),
                    ) {
                        items(items = rows, key = { it.key }) { row ->
                            when (row) {
                                is NotificationRowModel.PostNotificationRow -> {
                                    val notif = row.notification
                                    val unreadBg = if (!notif.isRead) {
                                        palette.primary50.toComposeColor().copy(alpha = 0.35f)
                                    } else {
                                        palette.contrast0.toComposeColor()
                                    }
                                    val reasonLabel = when (notif.reason) {
                                        "reply" -> "Replied to your post"
                                        "quote" -> "Quoted your post"
                                        "subscribed-post" -> "New post notification"
                                        else -> "Mentioned you"
                                    }

                                    Column(modifier = Modifier.background(unreadBg)) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 16.dp, end = 16.dp, top = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Icon(
                                                imageVector = if (notif.reason == "quote") Icons.Filled.FormatQuote else Icons.Filled.AlternateEmail,
                                                contentDescription = null,
                                                tint = palette.primary500.toComposeColor(),
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Text(
                                                text = reasonLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = palette.contrast600.toComposeColor(),
                                            )
                                        }

                                        if (row.post != null) {
                                            PostCard(
                                                post = row.post,
                                                moderationOpts = session.moderationOpts,
                                                currentUserDid = session.currentDid,
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
                                                        )
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
                                                        )
                                                    )
                                                },
                                                onOpenLightbox = onOpenLightbox,
                                                onOpenVideoLightbox = onOpenVideoLightbox,
                                                onNavigate = onNavigate,
                                            )
                                        } else {
                                            FallbackPostNotificationRow(
                                                notification = notif,
                                                onNavigate = onNavigate,
                                            )
                                        }
                                    }
                                }
                                is NotificationRowModel.GroupedNotificationRow -> {
                                    GroupedNotificationItemRow(
                                        row = row,
                                        onNavigate = onNavigate,
                                    )
                                }
                            }
                        }

                        if (pageState.isLoadingMore) {
                            item(key = "notif_footer_loader") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        } else if (pageState.cursor != null || pageState.error) {
                            item(key = "notif_footer_continue") {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Button(onClick = {
                                        scope.launch {
                                            loadTab(
                                                selectedTab,
                                                loadMore = if (pageState.error) pageState.retryPagination else true,
                                                refresh = pageState.error && !pageState.retryPagination,
                                            )
                                        }
                                    }) {
                                        Text(if (pageState.error) tr("Try again") else tr("Load more"))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun NotificationItem.identity() = Triple(uri, reason, author.did)

@Composable
private fun FallbackPostNotificationRow(
    notification: NotificationItem,
    onNavigate: (AppRoute) -> Unit,
) {
    val palette = LocalAlfPalette.current
    val text = (notification.record["text"] as? JsonPrimitive)?.content.orEmpty()
    val handleOrDid = notification.author.handle.ifBlank { notification.author.did }
    val rkey = notification.uri.substringAfterLast('/')

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigate(AppRoute.PostThread(handleOrDid, rkey)) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CaelumAvatar(
                url = notification.author.avatar,
                contentDescription = notification.author.displayName ?: notification.author.handle,
                size = 40.dp,
                onClick = { onNavigate(AppRoute.Profile(handleOrDid)) },
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DisplayNameRow(
                    profile = notification.author.toBasic(),
                    timestamp = LocalI18n.current.formatRelativeShort(notification.indexedAt),
                )
                if (text.isNotBlank()) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.contrast950.toComposeColor(),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)
    }
}

@Composable
private fun GroupedNotificationItemRow(
    row: NotificationRowModel.GroupedNotificationRow,
    onNavigate: (AppRoute) -> Unit,
) {
    val palette = LocalAlfPalette.current
    val distinctActors = remember(row.notifications) {
        row.notifications.map { it.author }.distinctBy { it.did }
    }
    val unreadBg = if (row.hasUnread) {
        palette.primary50.toComposeColor().copy(alpha = 0.35f)
    } else {
        palette.contrast0.toComposeColor()
    }

    val (icon, iconTint, verb) = when (row.reason) {
        "like" -> Triple(Icons.Filled.Favorite, palette.like.toComposeColor(), "liked your post")
        "like-via-repost" -> Triple(Icons.Filled.Favorite, palette.like.toComposeColor(), "liked your repost")
        "repost" -> Triple(Icons.Filled.Repeat, palette.positive500.toComposeColor(), "reposted your post")
        "repost-via-repost" -> Triple(Icons.Filled.Repeat, palette.positive500.toComposeColor(), "reposted your repost")
        "follow" -> Triple(Icons.Filled.PersonAdd, palette.primary500.toComposeColor(), "followed you")
        "starterpack-joined" -> Triple(Icons.Filled.RocketLaunch, palette.primary500.toComposeColor(), "joined via your starter pack")
        "verified" -> Triple(Icons.Filled.Verified, Color(0xFF0085FF), "verified your account")
        "unverified" -> Triple(Icons.Filled.Verified, palette.contrast500.toComposeColor(), "updated verification")
        else -> Triple(Icons.Filled.NotificationsNone, palette.primary500.toComposeColor(), "interacted with you")
    }

    val summary = buildAnnotatedString {
        val first = distinctActors.firstOrNull()
        val firstName = first?.displayName?.takeIf { it.isNotBlank() } ?: "@${first?.handle.orEmpty()}"
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = palette.contrast1000.toComposeColor())) {
            append(firstName)
        }
        when {
            distinctActors.size == 2 -> {
                val second = distinctActors[1]
                val secondName = second.displayName?.takeIf { it.isNotBlank() } ?: "@${second.handle}"
                append(" and ")
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = palette.contrast1000.toComposeColor())) {
                    append(secondName)
                }
            }
            distinctActors.size > 2 -> {
                append(" and ")
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = palette.contrast1000.toComposeColor())) {
                    append("${distinctActors.size - 1} others")
                }
            }
        }
        append(" $verb")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(unreadBg)
            .clickable {
                when {
                    row.reason == "follow" && distinctActors.size == 1 -> {
                        val actor = distinctActors.first()
                        onNavigate(AppRoute.Profile(actor.handle.ifBlank { actor.did }))
                    }
                    row.reason == "follow" && distinctActors.size > 1 -> {
                        onNavigate(AppRoute.NotificationsActivityList(distinctActors.joinToString(",") { it.did }))
                    }
                    !row.reasonSubject.isNullOrBlank() && distinctActors.size > 1 -> {
                        onNavigate(AppRoute.NotificationsActivityList(row.reasonSubject))
                    }
                    row.subjectPost != null -> {
                        val p = row.subjectPost
                        onNavigate(
                            AppRoute.PostThread(
                                p.author.handle.ifBlank { p.author.did },
                                p.uri.substringAfterLast('/'),
                            )
                        )
                    }
                    !row.reasonSubject.isNullOrBlank() -> {
                        onNavigate(AppRoute.NotificationsActivityList(row.reasonSubject))
                    }
                    distinctActors.isNotEmpty() -> {
                        val actor = distinctActors.first()
                        onNavigate(AppRoute.Profile(actor.handle.ifBlank { actor.did }))
                    }
                }
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .padding(top = 4.dp),
                contentAlignment = Alignment.TopEnd,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    distinctActors.take(6).forEach { actor ->
                        CaelumAvatar(
                            url = actor.avatar,
                            contentDescription = actor.displayName ?: actor.handle,
                            size = 32.dp,
                            onClick = { onNavigate(AppRoute.Profile(actor.handle.ifBlank { actor.did })) },
                        )
                    }
                    if (distinctActors.size > 6) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(palette.contrast100.toComposeColor()),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "+${distinctActors.size - 6}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.contrast800.toComposeColor(),
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.contrast800.toComposeColor(),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = LocalI18n.current.formatRelativeShort(row.latestIndexedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.contrast500.toComposeColor(),
                    )
                }

                row.subjectPost?.let { post ->
                    val postText = (post.record["text"] as? JsonPrimitive)?.content.orEmpty()
                    if (postText.isNotBlank()) {
                        Text(
                            text = postText,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.contrast600.toComposeColor(),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(palette.contrast25.toComposeColor())
                                .border(0.5.dp, palette.contrast100.toComposeColor(), RoundedCornerShape(8.dp))
                                .clickable {
                                    onNavigate(
                                        AppRoute.PostThread(
                                            post.author.handle.ifBlank { post.author.did },
                                            post.uri.substringAfterLast('/'),
                                        )
                                    )
                                }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)
    }
}

private fun buildNotificationRows(
    items: List<NotificationItem>,
    postsByUri: Map<String, PostView>,
    applyShadow: (PostView) -> PostView?,
    isActorBlocked: (ProfileView) -> Boolean,
): List<NotificationRowModel> {
    val result = mutableListOf<NotificationRowModel>()
    val groupMap = linkedMapOf<String, MutableList<NotificationItem>>()
    val orderedTokens = mutableListOf<Pair<Boolean, Any>>() // true = post row, false = group key

    for (item in items) {
        if (isActorBlocked(item.author)) continue
        if (item.reason in INLINE_POST_REASONS) {
            val rawPost = postsByUri[item.uri]
            val shadowed = if (rawPost != null) applyShadow(rawPost) else null
            if (rawPost != null && shadowed == null) continue // deleted via shadow store
            orderedTokens.add(
                true to NotificationRowModel.PostNotificationRow(
                    key = "post_${item.uri}_${item.cid}",
                    notification = item,
                    post = shadowed,
                )
            )
        } else {
            val groupKey = when (item.reason) {
                "follow" -> "follow:${item.isRead}"
                else -> "${item.reason}:${item.reasonSubject.orEmpty()}"
            }
            val list = groupMap[groupKey]
            if (list == null) {
                groupMap[groupKey] = mutableListOf(item)
                orderedTokens.add(false to groupKey)
            } else {
                list.add(item)
            }
        }
    }

    for ((isPost, payload) in orderedTokens) {
        if (isPost) {
            result.add(payload as NotificationRowModel.PostNotificationRow)
        } else {
            val groupKey = payload as String
            val groupItems = groupMap[groupKey].orEmpty()
            if (groupItems.isEmpty()) continue
            val first = groupItems.first()
            val subjectPost = first.reasonSubject?.let { postsByUri[it] }?.let(applyShadow)
            result.add(
                NotificationRowModel.GroupedNotificationRow(
                    key = "grp_${groupKey}_${first.cid}",
                    reason = first.reason,
                    reasonSubject = first.reasonSubject,
                    notifications = groupItems,
                    subjectPost = subjectPost,
                    hasUnread = groupItems.any { !it.isRead },
                    latestIndexedAt = first.indexedAt,
                )
            )
        }
    }
    return result
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsActivityListScreen(
    urisCsv: String,
    onBack: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
) {
    val container = LocalAppContainer.current
    val palette = LocalAlfPalette.current

    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var posts by remember { mutableStateOf<List<PostView>>(emptyList()) }
    var actors by remember { mutableStateOf<List<ProfileViewBasic>>(emptyList()) }

    val tokens = remember(urisCsv) {
        urisCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    suspend fun loadActivity() {
        isLoading = true
        errorMsg = null
        runCatching {
            val postUris = tokens.filter { it.startsWith("at://") && it.contains("/app.bsky.feed.post/") }
            val didTokens = tokens.filter { it.startsWith("did:") }
            val otherAtUris = tokens.filter { it.startsWith("at://") && it !in postUris }

            val loadedPosts = if (postUris.isNotEmpty()) {
                coroutineScope {
                    postUris.chunked(25).map { chunk ->
                        async { runCatching { container.api.getPosts(chunk) }.getOrDefault(emptyList()) }
                    }.awaitAll().flatten()
                }
            } else {
                emptyList()
            }

            val profileActors = if (didTokens.isNotEmpty()) {
                coroutineScope {
                    didTokens.chunked(25).map { chunk ->
                        async { runCatching { container.api.getProfiles(chunk) }.getOrDefault(emptyList()) }
                    }.awaitAll().flatten().map { it.toBasic() }
                }
            } else {
                emptyList()
            }

            val notifActors = if (postUris.isNotEmpty() || otherAtUris.isNotEmpty()) {
                val targetSet = (postUris + otherAtUris).toSet()
                val recent = runCatching { container.api.listNotifications(limit = 100).notifications }.getOrDefault(emptyList())
                recent.filter { it.reasonSubject in targetSet || it.uri in targetSet }
                    .map { it.author.toBasic() }
            } else {
                emptyList()
            }

            loadedPosts to (profileActors + notifActors).distinctBy { it.did }
        }.onSuccess { (loadedPosts, loadedActors) ->
            posts = loadedPosts
            actors = loadedActors
            isLoading = false
        }.onFailure { ex ->
            errorMsg = ex.message ?: "Unable to load activity"
            isLoading = false
        }
    }

    LaunchedEffect(urisCsv) {
        loadActivity()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.contrast0.toComposeColor()),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text(
                        text = "Activity",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
            )
        },
        containerColor = palette.contrast0.toComposeColor(),
    ) { padding ->
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            errorMsg != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(errorMsg.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                }
            }
            posts.isEmpty() && actors.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No matching activity found.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = palette.contrast600.toComposeColor(),
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(bottom = 40.dp),
                ) {
                    if (posts.isNotEmpty()) {
                        item(key = "activity_posts_header") {
                            Text(
                                text = "Posts",
                                style = MaterialTheme.typography.labelLarge,
                                color = palette.contrast600.toComposeColor(),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                        }
                        items(items = posts, key = { "act_post_${it.uri}" }) { post ->
                            val text = (post.record["text"] as? JsonPrimitive)?.content.orEmpty()
                            val handleOrDid = post.author.handle.ifBlank { post.author.did }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onNavigate(AppRoute.PostThread(handleOrDid, post.uri.substringAfterLast('/')))
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    CaelumAvatar(
                                        url = post.author.avatar,
                                        contentDescription = post.author.displayName ?: post.author.handle,
                                        size = 32.dp,
                                    )
                                    DisplayNameRow(
                                        profile = post.author,
                                        timestamp = LocalI18n.current.formatRelativeShort(post.indexedAt),
                                    )
                                }
                                if (text.isNotBlank()) {
                                    Text(
                                        text = text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = palette.contrast950.toComposeColor(),
                                        maxLines = 4,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)
                        }
                    }

                    if (actors.isNotEmpty()) {
                        item(key = "activity_actors_header") {
                            Text(
                                text = "People",
                                style = MaterialTheme.typography.labelLarge,
                                color = palette.contrast600.toComposeColor(),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                        }
                        items(items = actors, key = { "act_actor_${it.did}" }) { actor ->
                            val handleOrDid = actor.handle.ifBlank { actor.did }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigate(AppRoute.Profile(handleOrDid)) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                CaelumAvatar(
                                    url = actor.avatar,
                                    contentDescription = actor.displayName ?: actor.handle,
                                    size = 42.dp,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    DisplayNameRow(profile = actor, showHandle = true, showPronouns = true)
                                }
                            }
                            HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}

private fun ProfileView.toBasic(): ProfileViewBasic = ProfileViewBasic(
    did = did,
    handle = handle,
    displayName = displayName,
    pronouns = pronouns,
    avatar = avatar,
    associated = associated,
    viewer = viewer,
    labels = labels,
    verification = verification,
    status = status,
)

private fun ProfileViewDetailed.toBasic(): ProfileViewBasic = ProfileViewBasic(
    did = did,
    handle = handle,
    displayName = displayName,
    pronouns = pronouns,
    avatar = avatar,
    associated = associated,
    viewer = viewer,
    labels = labels,
    verification = verification,
    status = status,
)
