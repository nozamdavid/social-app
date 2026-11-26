package am.noz.caelum.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.model.BookmarkView
import am.noz.caelum.core.model.PostView
import am.noz.caelum.core.session.ScreenStateCache
import am.noz.caelum.core.storage.AppSettingsSnapshot
import am.noz.caelum.core.storage.SearchableBookmarkEngine
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import java.io.File

private fun decodeBookmarkPost(bookmark: BookmarkView): PostView? {
    val type = (bookmark.item["\$type"] as? JsonPrimitive)?.content
    if (type == "app.bsky.feed.defs#blockedPost" || type == "app.bsky.feed.defs#notFoundPost") {
        return null
    }
    return runCatching { AtProtoJson.decodeFromJsonElement<PostView>(bookmark.item) }.getOrNull()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    onOpenDrawer: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
    onOpenComposer: (replyTo: PostView?, quote: PostView?) -> Unit,
    onOpenEditPost: (PostView) -> Unit,
    onOpenIn: (OpenInSubject) -> Unit,
    onOpenReport: (PostView) -> Unit,
    onOpenLightbox: (List<EmbedImageItem>, Int) -> Unit,
    onOpenVideoLightbox: (String, String?, String?) -> Unit,
) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val screenCache = LocalScreenStateCache.current
    val palette = LocalAlfPalette.current
    val session by container.sessionManager.state.collectAsState()
    val settings by container.preferences.settingsFlow.collectAsState(initial = AppSettingsSnapshot())
    val shadows by container.postShadowStore.shadows.collectAsState()
    val scope = rememberAccountCoroutineScope()
    val listState = screenCache.rememberCachedLazyListState("bookmarks")
    val cachedBookmarks = remember(session.currentDid) { screenCache.bookmarks }

    var bookmarks by remember(session.currentDid) {
        mutableStateOf(cachedBookmarks?.bookmarks.orEmpty())
    }
    var cursor by remember(session.currentDid) { mutableStateOf(cachedBookmarks?.cursor) }
    var loadedOnce by remember(session.currentDid) { mutableStateOf(cachedBookmarks?.loadedOnce == true) }
    var isLoading by remember(session.currentDid) {
        mutableStateOf(cachedBookmarks == null || (!cachedBookmarks.loadedOnce && cachedBookmarks.bookmarks.isEmpty()))
    }
    var isRefreshing by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var localSearchResults by remember { mutableStateOf<List<BookmarkView>>(emptyList()) }
    var indexedCount by remember { mutableIntStateOf(0) }

    val scopeKey = remember(session.currentDid, settings.infrastructure.appviewHostname) {
        session.currentDid?.let { did ->
            SearchableBookmarkEngine.scopeKeyFor(did, settings.infrastructure.appviewHostname)
        }
    }

    suspend fun syncBookmarksToLocalIndex(loaded: List<BookmarkView>) {
        val key = scopeKey ?: return
        if (!settings.searchableBookmarks || loaded.isEmpty()) return
        runCatching {
            val valid = loaded.filter { decodeBookmarkPost(it) != null }
            if (valid.isNotEmpty()) {
                container.bookmarkEngine.upsertBookmarks(key, valid)
                val gzipBytes = container.bookmarkEngine.exportGzipSnapshot(key)
                withContext(Dispatchers.IO) {
                    SearchableBookmarkEngine.snapshotFile(context.filesDir, key).writeBytes(gzipBytes)
                }
            }
            indexedCount = container.database.bookmarkIndexDao().countForScope(key)
        }
    }

    LaunchedEffect(scopeKey, settings.searchableBookmarks) {
        val key = scopeKey ?: return@LaunchedEffect
        if (!settings.searchableBookmarks) return@LaunchedEffect
        val daoCount = runCatching { container.database.bookmarkIndexDao().countForScope(key) }.getOrDefault(0)
        if (daoCount == 0) {
            val snapshotFile = SearchableBookmarkEngine.snapshotFile(context.filesDir, key)
            if (snapshotFile.exists()) {
                runCatching {
                    val bytes = withContext(Dispatchers.IO) { snapshotFile.readBytes() }
                    container.bookmarkEngine.importGzipSnapshot(bytes)
                }
            }
        }
        indexedCount = runCatching { container.database.bookmarkIndexDao().countForScope(key) }.getOrDefault(0)
    }

    suspend fun loadBookmarks(refresh: Boolean = false, loadMore: Boolean = false) {
        if (!session.hasSession) return
        if (loadMore && (isLoadingMore || cursor.isNullOrBlank())) return
        if (!refresh && !loadMore && (loadedOnce || bookmarks.isNotEmpty())) return
        isLoading = !refresh && !loadMore && bookmarks.isEmpty()
        isRefreshing = refresh
        isLoadingMore = loadMore
        errorMsg = null

        runCatching {
            container.api.getBookmarks(
                cursor = if (loadMore) cursor else null,
                limit = 50,
            )
        }.onSuccess { resp ->
            val merged = if (loadMore) {
                (bookmarks + resp.bookmarks).distinctBy { it.subject.uri }
            } else {
                resp.bookmarks.distinctBy { it.subject.uri }
            }
            bookmarks = merged
            cursor = resp.cursor
            loadedOnce = true
            isLoading = false
            isRefreshing = false
            isLoadingMore = false
            screenCache.bookmarks = ScreenStateCache.CachedBookmarksScreen(
                bookmarks = merged,
                cursor = resp.cursor,
                loadedOnce = true,
            )
            syncBookmarksToLocalIndex(resp.bookmarks)
        }.onFailure { ex ->
            if (ex is CancellationException) throw ex
            errorMsg = ex.message ?: "Unable to load saved posts"
            isLoading = false
            isRefreshing = false
            isLoadingMore = false
        }
    }

    LaunchedEffect(session.currentDid) {
        if (session.hasSession) {
            loadBookmarks()
        }
    }

    LaunchedEffect(settings.searchableBookmarks, searchQuery, bookmarks, scopeKey) {
        val key = scopeKey
        if (!settings.searchableBookmarks || key == null) {
            localSearchResults = emptyList()
            return@LaunchedEffect
        }
        if (bookmarks.isNotEmpty() && indexedCount == 0) {
            syncBookmarksToLocalIndex(bookmarks)
        }
        if (searchQuery.isBlank()) {
            indexedCount = runCatching { container.database.bookmarkIndexDao().countForScope(key) }.getOrDefault(0)
            localSearchResults = emptyList()
        } else {
            localSearchResults = runCatching {
                container.bookmarkEngine.search(key, searchQuery)
            }.getOrDefault(emptyList())
        }
    }

    val shouldLoadMore by remember(listState, bookmarks.size, cursor, searchQuery) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            searchQuery.isBlank() &&
                bookmarks.isNotEmpty() &&
                !cursor.isNullOrBlank() &&
                lastVisible >= bookmarks.size - 6
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            loadBookmarks(loadMore = true)
        }
    }

    val activeSource = if (settings.searchableBookmarks && searchQuery.isNotBlank()) {
        localSearchResults
    } else {
        bookmarks
    }

    val visibleItems = remember(activeSource, shadows) {
        activeSource.filter { bm ->
            val shadow = container.postShadowStore.get(bm.subject.uri)
            shadow == null || (!shadow.isDeleted && shadow.bookmarked != false)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.contrast0.toComposeColor()),
                navigationIcon = {
                    IconButton(
                        onClick = onOpenDrawer,
                        modifier = Modifier.testTag("bookmarks_drawer_button"),
                    ) {
                        Icon(Icons.Filled.Menu, contentDescription = "Open drawer menu")
                    }
                },
                title = {
                    Text(
                        text = "Saved",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    IconButton(onClick = { onNavigate(AppRoute.CustomizationSettings) }) {
                        Icon(Icons.Filled.Tune, contentDescription = "Bookmark indexing settings")
                    }
                },
            )
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
                    text = "Sign in to view your saved posts.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.contrast600.toComposeColor(),
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (settings.searchableBookmarks) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.contrast0.toComposeColor())
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search saved posts (instant local index)…") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("bookmarks_local_search_input"),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) {
                                "${visibleItems.size} match${if (visibleItems.size == 1) "" else "es"}"
                            } else {
                                "$indexedCount indexed locally"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.contrast600.toComposeColor(),
                        )
                        TextButton(onClick = { onNavigate(AppRoute.CustomizationSettings) }) {
                            Text("Manage index")
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = palette.contrast25.toComposeColor(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .border(0.5.dp, palette.contrast100.toComposeColor(), RoundedCornerShape(12.dp)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Searchable Bookmarks",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.contrast1000.toComposeColor(),
                            )
                            Text(
                                text = "Index saved posts locally for instant offline search.",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.contrast600.toComposeColor(),
                            )
                        }
                        OutlinedButton(onClick = { onNavigate(AppRoute.CustomizationSettings) }) {
                            Text("Enable")
                        }
                    }
                }
            }

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { scope.launch { loadBookmarks(refresh = true) } },
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    isLoading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    errorMsg != null && bookmarks.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(errorMsg.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { scope.launch { loadBookmarks(refresh = true) } }) {
                                Text("Try again")
                            }
                        }
                    }
                    visibleItems.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.BookmarkBorder,
                                contentDescription = null,
                                tint = palette.contrast400.toComposeColor(),
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = if (settings.searchableBookmarks && searchQuery.isNotBlank()) {
                                    "No saved posts match “$searchQuery”"
                                } else {
                                    "No saved posts yet"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.contrast700.toComposeColor(),
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("bookmarks_list"),
                            contentPadding = PaddingValues(bottom = 84.dp),
                        ) {
                            itemsIndexed(
                                items = visibleItems,
                                key = { _, bm -> bm.subject.uri },
                            ) { _, bm ->
                                val rawPost = decodeBookmarkPost(bm)
                                val post = rawPost?.let { container.postShadowStore.applyToPost(it) }
                                if (post != null) {
                                    PostCard(
                                        post = post,
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
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = "Post unavailable or deleted",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = palette.contrast600.toComposeColor(),
                                        )
                                        TextButton(
                                            onClick = {
                                                scope.launch {
                                                    runCatching {
                                                        container.api.deleteBookmark(bm.subject.uri)
                                                        scopeKey?.let { container.bookmarkEngine.removeBookmark(it, bm.subject.uri) }
                                                    }.onSuccess {
                                                        val updated = bookmarks.filterNot { it.subject.uri == bm.subject.uri }
                                                        bookmarks = updated
                                                        screenCache.bookmarks = ScreenStateCache.CachedBookmarksScreen(
                                                            bookmarks = updated,
                                                            cursor = cursor,
                                                            loadedOnce = loadedOnce,
                                                        )
                                                    }
                                                }
                                            },
                                        ) {
                                            Text("Remove")
                                        }
                                    }
                                    HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)
                                }
                            }

                            if (isLoadingMore) {
                                item(key = "bookmarks_footer_loader") {
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
    }
}
