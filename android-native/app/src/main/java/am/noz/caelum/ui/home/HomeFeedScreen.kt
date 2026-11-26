package am.noz.caelum.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import am.noz.caelum.core.i18n.tr
import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.model.FeedViewPost
import am.noz.caelum.core.model.ListView
import am.noz.caelum.core.model.PostView
import am.noz.caelum.core.model.threadRootUri
import am.noz.caelum.core.moderation.ModerationContext
import am.noz.caelum.core.moderation.ModerationEngine
import am.noz.caelum.core.session.ScreenStateCache
import am.noz.caelum.ui.components.CaelumLogoMark
import am.noz.caelum.ui.components.ComposePostButton
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.decodeFromJsonElement

data class HomeFeedTabDescriptor(
    val id: String,
    val title: String,
    val type: String, // "timeline" | "feed" | "list"
    val value: String,
)

private data class FeedPageState(
    val items: List<FeedViewPost> = emptyList(),
    val cursor: String? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
)

private const val DISCOVER_FEED_URI = "at://did:plc:z72i7hdynmk6r22z27h6tvur/app.bsky.feed.generator/whats-hot"
private const val WHATS_HOT_FEED_URI = "at://did:plc:z72i7hdynmk6r22z27h6tvur/app.bsky.feed.generator/hot-classic"

private fun wellKnownFeedTitle(uri: String): String? = when {
    uri == DISCOVER_FEED_URI || uri.endsWith("/app.bsky.feed.generator/whats-hot") -> "Discover"
    uri == WHATS_HOT_FEED_URI || uri.endsWith("/app.bsky.feed.generator/hot-classic") -> "What's Hot"
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeFeedScreen(
    reselectEvents: Flow<Unit>,
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
    val settings by container.preferences.settingsFlow.collectAsState(initial = am.noz.caelum.core.storage.AppSettingsSnapshot())
    val shadows by container.postShadowStore.shadows.collectAsState()
    val scope = rememberAccountCoroutineScope()

    val modSettingsFlow = remember(session.currentDid) {
        session.currentDid?.let { container.preferences.observeModerationSettings(it) }
    }
    val modSettings = modSettingsFlow?.collectAsState(initial = am.noz.caelum.core.storage.ModerationUserSettings())?.value

    val resolvedTitles = remember(session.currentDid) {
        mutableStateMapOf<String, String>().apply {
            putAll(screenCache.resolvedFeedTitles)
        }
    }

    // Resolve any missing feed generator or list titles not yet in SessionUiState
    val pinnedFeeds = remember(session.pinnedFeeds) {
        session.pinnedFeeds
            .filter { it.pinned }
            .distinctBy { if (it.type == "timeline") "timeline:${it.value}" else it.value }
    }
    var localFeedsHydrated by remember(session.currentDid, pinnedFeeds) {
        mutableStateOf(session.feedsHydrated)
    }

    LaunchedEffect(pinnedFeeds, session.feedGenerators, session.listViews, session.feedsHydrated) {
        session.feedGenerators.forEach { (uri, gen) ->
            if (gen.displayName.isNotBlank()) {
                screenCache.resolvedFeedTitles[uri] = gen.displayName
                resolvedTitles[uri] = gen.displayName
            }
        }
        session.listViews.forEach { (uri, list) ->
            if (list.name.isNotBlank()) {
                screenCache.resolvedFeedTitles[uri] = list.name
                resolvedTitles[uri] = list.name
            }
        }

        val missingFeeds = pinnedFeeds
            .filter { it.type != "timeline" && it.type != "list" && !it.value.contains("/app.bsky.graph.list/") }
            .map { it.value }
            .filter { it.startsWith("at://") && it !in session.feedGenerators && it !in resolvedTitles }
            .distinct()

        val missingLists = pinnedFeeds
            .filter { it.type == "list" || it.value.contains("/app.bsky.graph.list/") }
            .map { it.value }
            .filter { it.startsWith("at://") && it !in session.listViews && it !in resolvedTitles }
            .distinct()

        if (missingFeeds.isEmpty() && missingLists.isEmpty()) {
            localFeedsHydrated = true
            return@LaunchedEffect
        }

        coroutineScope {
            val feedResults = missingFeeds.chunked(25).map { chunk ->
                async {
                    runCatching { container.api.getFeedGenerators(chunk) }
                }
            }.awaitAll()
            val listResults = missingLists.map { listUri ->
                async {
                    runCatching {
                        val res = container.api.getList(listUri)
                        res["list"]?.let { AtProtoJson.decodeFromJsonElement<ListView>(it) }
                    }.getOrNull()?.let { listUri to it.name }
                }
            }.awaitAll()

            feedResults.forEach { res ->
                res.getOrNull()?.forEach { gen ->
                    if (gen.displayName.isNotBlank()) {
                        screenCache.resolvedFeedTitles[gen.uri] = gen.displayName
                        resolvedTitles[gen.uri] = gen.displayName
                    }
                }
            }
            listResults.filterNotNull().forEach { (uri, name) ->
                if (name.isNotBlank()) {
                    screenCache.resolvedFeedTitles[uri] = name
                    resolvedTitles[uri] = name
                }
            }

            val allFeedsOk = missingFeeds.isEmpty() || feedResults.all { it.isSuccess }
            if (allFeedsOk || session.feedsHydrated) {
                localFeedsHydrated = true
            }
        }
    }

    val followingLabel = tr("Following")
    val discoverLabel = tr("Discover")
    val whatsHotLabel = tr("What's Hot")
    val defaultFeedLabel = tr("Feed")
    val defaultListLabel = tr("List")

    val isHydrated = session.feedsHydrated || localFeedsHydrated
    val defaultTabs = remember(session.hasSession, followingLabel, discoverLabel, whatsHotLabel) {
        buildList {
            if (session.hasSession) {
                add(HomeFeedTabDescriptor("following", followingLabel, "timeline", "following"))
            }
            add(HomeFeedTabDescriptor("discover", discoverLabel, "feed", DISCOVER_FEED_URI))
            add(HomeFeedTabDescriptor("whats-hot", whatsHotLabel, "feed", WHATS_HOT_FEED_URI))
        }
    }
    val tabs = remember(session.hasSession, pinnedFeeds, session.feedGenerators, session.listViews, resolvedTitles.toMap(), isHydrated, defaultTabs) {
        if (!session.hasSession || pinnedFeeds.isEmpty()) {
            defaultTabs
        } else {
            val filteredPinned = if (!isHydrated) {
                pinnedFeeds
            } else {
                pinnedFeeds.filter { pref ->
                    val isList = pref.type == "list" || pref.value.contains("/app.bsky.graph.list/")
                    when {
                        pref.type == "timeline" -> true
                        isList -> pref.value in session.listViews || pref.value in resolvedTitles
                        else -> pref.value in session.feedGenerators || pref.value in resolvedTitles
                    }
                }
            }
            if (filteredPinned.isEmpty()) {
                defaultTabs
            } else {
                filteredPinned.map { pref ->
                    val isList = pref.type == "list" || pref.value.contains("/app.bsky.graph.list/")
                    val title = when {
                        pref.type == "timeline" -> followingLabel
                        isList -> session.listViews[pref.value]?.name
                            ?: resolvedTitles[pref.value]
                            ?: screenCache.resolvedFeedTitles[pref.value]
                            ?: defaultListLabel
                        else -> session.feedGenerators[pref.value]?.displayName
                            ?: resolvedTitles[pref.value]
                            ?: screenCache.resolvedFeedTitles[pref.value]
                            ?: wellKnownFeedTitle(pref.value)
                            ?: defaultFeedLabel
                    }
                    val effectiveType = if (isList) "list" else pref.type
                    HomeFeedTabDescriptor(
                        id = pref.value.ifBlank { pref.id },
                        title = title,
                        type = effectiveType,
                        value = pref.value,
                    )
                }
            }
        }
    }

    val feedStates = remember(session.currentDid) {
        mutableStateMapOf<String, FeedPageState>().apply {
            screenCache.homeFeeds.forEach { (key, cached) ->
                put(key, FeedPageState(items = cached.items, cursor = cached.cursor))
            }
        }
    }
    val pagerState = rememberPagerState(
        initialPage = screenCache.homeSelectedTabIndex.coerceIn(0, (tabs.size - 1).coerceAtLeast(0)),
        pageCount = { tabs.size.coerceAtLeast(1) },
    )
    val listStates = remember { HashMap<String, LazyListState>() }

    LaunchedEffect(pagerState.currentPage) {
        screenCache.homeSelectedTabIndex = pagerState.currentPage
    }

    val unableToLoadFeedText = tr("Unable to load feed")

    suspend fun loadFeed(tab: HomeFeedTabDescriptor, refresh: Boolean = false, loadMore: Boolean = false) {
        val key = tab.value
        val current = feedStates[key]
            ?: screenCache.homeFeeds[key]?.let { FeedPageState(items = it.items, cursor = it.cursor) }
            ?: FeedPageState()
        if (current.isLoading || current.isRefreshing) return
        if (loadMore && (current.isLoadingMore || current.cursor.isNullOrBlank())) return
        if (!refresh && !loadMore && current.items.isNotEmpty()) {
            if (key !in feedStates) feedStates[key] = current
            return
        }

        val request = current.copy(
            isLoading = !refresh && !loadMore && current.items.isEmpty(),
            isRefreshing = refresh,
            isLoadingMore = loadMore,
            error = null,
        )
        feedStates[key] = request

        runCatching {
            val cursor = if (loadMore) current.cursor else null
            when (tab.type) {
                "timeline" -> container.api.getTimeline(cursor = cursor, limit = 30)
                "list" -> container.api.getListFeed(tab.value, cursor = cursor, limit = 30)
                else -> container.api.getFeed(tab.value, cursor = cursor, limit = 30)
            }
        }.onSuccess { resp ->
            if (feedStates[key] !== request) return
            val merged = if (loadMore) {
                (current.items + resp.feed).distinctBy { it.post.uri + ":" + (it.reason?.toString() ?: "") }
            } else {
                resp.feed
            }
            feedStates[key] = FeedPageState(
                items = merged,
                cursor = resp.cursor,
            )
            screenCache.homeFeeds[key] = ScreenStateCache.CachedFeedPage(
                items = merged,
                cursor = resp.cursor,
            )
        }.onFailure { ex ->
            if (ex is CancellationException) {
                if (feedStates[key] === request) {
                    feedStates[key] = request.copy(isLoading = false, isRefreshing = false, isLoadingMore = false)
                }
                throw ex
            }
            if (feedStates[key] !== request) return
            feedStates[key] = current.copy(
                isLoading = false,
                isRefreshing = false,
                isLoadingMore = false,
                error = ex.message ?: unableToLoadFeedText,
            )
        }
    }

    fun resetCurrentFeed() {
        val tab = tabs.getOrNull(pagerState.currentPage) ?: return
        listStates[tab.value]?.requestScrollToItem(0)
        scope.launch { loadFeed(tab, refresh = true) }
    }

    LaunchedEffect(reselectEvents, tabs) {
        reselectEvents.collect { resetCurrentFeed() }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(palette.contrast0.toComposeColor())) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.contrast0.toComposeColor()),
                    navigationIcon = {
                        IconButton(
                            onClick = onOpenDrawer,
                            modifier = Modifier.testTag("home_drawer_button"),
                        ) {
                            Icon(Icons.Filled.Menu, contentDescription = tr("Open drawer menu"))
                        }
                    },
                    title = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClickLabel = tr("Load new posts"), onClick = ::resetCurrentFeed),
                            contentAlignment = Alignment.Center,
                        ) {
                            CaelumLogoMark(size = 28.dp)
                        }
                    },
                    actions = {
                        IconButton(onClick = { onNavigate(AppRoute.Feeds) }) {
                            Icon(Icons.Filled.Tag, contentDescription = tr("Browse Feeds"))
                        }
                    },
                )

                if (tabs.isNotEmpty()) {
                    SecondaryScrollableTabRow(
                        selectedTabIndex = pagerState.currentPage.coerceIn(0, tabs.lastIndex),
                        containerColor = palette.contrast0.toComposeColor(),
                        contentColor = palette.contrast1000.toComposeColor(),
                        edgePadding = 12.dp,
                    ) {
                        tabs.forEachIndexed { idx, tab ->
                            val selected = pagerState.currentPage == idx
                            Tab(
                                selected = selected,
                                onClick = { scope.launch { pagerState.animateScrollToPage(idx) } },
                                text = {
                                    Text(
                                        text = tab.title,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (session.hasSession) {
                ComposePostButton(
                    disableHaptics = settings.disableHaptics,
                    onClick = { onOpenComposer(null, null) },
                    modifier = Modifier.testTag("home_compose_fab"),
                )
            }
        },
        containerColor = palette.contrast0.toComposeColor(),
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) { pageIdx ->
            val tab = tabs.getOrNull(pageIdx) ?: return@HorizontalPager
            val cached = screenCache.homeFeeds[tab.value]
            val pageState = feedStates[tab.value]
                ?: cached?.let { FeedPageState(items = it.items, cursor = it.cursor) }
                ?: FeedPageState()
            val listState = screenCache.rememberCachedLazyListState("home_feed_${tab.value}")
            listStates[tab.value] = listState

            LaunchedEffect(tab.value, session.currentDid) {
                loadFeed(tab)
            }

            val mutedThreads = modSettings?.mutedThreads.orEmpty()
            val rows = remember(pageState.items, shadows, session.moderationOpts, mutedThreads) {
                buildHomeFeedRows(pageState.items) { candidate ->
                    val post = container.postShadowStore.applyToPost(candidate) ?: return@buildHomeFeedRows null
                    if (post.viewer?.threadMuted == true || post.threadRootUri() in mutedThreads) {
                        return@buildHomeFeedRows null
                    }
                    val moderation = ModerationEngine.moderatePost(post, session.moderationOpts)
                    post.takeUnless { moderation.ui(ModerationContext.contentList).filter }
                }
            }

            // Prefetch next page smoothly when within 6 items of end
            val shouldLoadMore by remember(listState, rows.size, pageState.cursor) {
                derivedStateOf {
                    val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                    pageState.cursor != null && lastVisible >= rows.size - 6
                }
            }
            LaunchedEffect(shouldLoadMore, pageState.cursor) {
                if (shouldLoadMore) loadFeed(tab, loadMore = true)
            }

            PullToRefreshBox(
                isRefreshing = pageState.isRefreshing,
                onRefresh = { scope.launch { loadFeed(tab, refresh = true) } },
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    pageState.isLoading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    pageState.error != null && pageState.items.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(pageState.error, style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { scope.launch { loadFeed(tab, refresh = true) } }) {
                                Text(tr("Try again"))
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("home_feed_list"),
                            contentPadding = PaddingValues(bottom = 84.dp),
                        ) {
                            items(rows, key = { it.key }) { row ->
                                if (row is HomeFeedRow.FullThread) {
                                    ViewFullThread(onClick = { onNavigate(row.root.threadRoute()) })
                                    return@items
                                }
                                row as HomeFeedRow.Post
                                PostCard(
                                    post = row.post,
                                    moderationOpts = session.moderationOpts,
                                    currentUserDid = session.currentDid,
                                    reason = row.reason,
                                    replyParentAuthorHandle = row.parentHandle,
                                    showTopThreadLine = row.showTopRail,
                                    showBottomThreadLine = row.showBottomRail,
                                    largeAltBadge = settings.largeAltBadgeEnabled,
                                    disableAutoplay = settings.disableAutoplay,
                                    disableHaptics = settings.disableHaptics,
                                    useLinkRedirects = settings.infrastructure.useLinkRedirects,
                                    openBskyLinksInCaelum = settings.openBskyLinksInCaelum,
                                    onPostClick = { p -> onNavigate(p.threadRoute()) },
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
                                        onOpenIn(OpenInSubject.Post(p.uri, p.author.did, p.author.handle.ifBlank { p.author.did }))
                                    },
                                    onOpenLightbox = onOpenLightbox,
                                    onOpenVideoLightbox = onOpenVideoLightbox,
                                    onNavigate = onNavigate,
                                )
                            }

                            if (pageState.isLoadingMore) {
                                item(key = "footer_loader") {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(20.dp),
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
    }
}

private fun PostView.threadRoute() =
    AppRoute.PostThread(author.handle.ifBlank { author.did }, uri.substringAfterLast('/'))

@Composable
private fun ViewFullThread(onClick: () -> Unit) {
    val palette = LocalAlfPalette.current
    val railColor = palette.contrast200.toComposeColor()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 18.dp, end = 15.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.width(42.dp).height(48.dp)) {
            val x = center.x
            drawLine(railColor, Offset(x, 0f), Offset(x, 15.dp.toPx()), strokeWidth = 2.dp.toPx())
            for (y in listOf(22.dp, 28.dp, 34.dp)) {
                drawCircle(railColor, radius = 1.5.dp.toPx(), center = Offset(x, y.toPx()))
            }
        }
        Text(
            tr("View full thread"),
            style = MaterialTheme.typography.bodyMedium,
            color = palette.primary500.toComposeColor(),
        )
    }
}
