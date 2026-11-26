package am.noz.caelum.ui.profile

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import am.noz.caelum.core.i18n.LocalI18n
import am.noz.caelum.core.i18n.tr
import am.noz.caelum.core.model.PostView
import am.noz.caelum.core.model.ProfileViewDetailed
import am.noz.caelum.core.moderation.ModerationContext
import am.noz.caelum.core.moderation.ModerationEngine
import am.noz.caelum.core.storage.AppSettingsSnapshot
import am.noz.caelum.core.storage.ModerationUserSettings
import am.noz.caelum.ui.components.LocalAppContainer
import am.noz.caelum.ui.navigation.AppRoute
import am.noz.caelum.ui.openin.OpenInSubject
import am.noz.caelum.ui.post.EmbedImageItem
import am.noz.caelum.ui.post.PostCard
import am.noz.caelum.ui.theme.LocalAlfPalette
import am.noz.caelum.ui.theme.toComposeColor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSearchScreen(
    actorHandleOrDid: String,
    initialQuery: String? = null,
    onBack: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
    onOpenComposer: (replyTo: PostView?, quote: PostView?) -> Unit,
    onOpenEditPost: (PostView) -> Unit,
    onOpenIn: (OpenInSubject) -> Unit,
    onOpenReport: (PostView) -> Unit,
    onOpenLightbox: (List<EmbedImageItem>, Int) -> Unit,
    onOpenVideoLightbox: (String, String?, String?) -> Unit,
) {
    val container = LocalAppContainer.current
    val palette = LocalAlfPalette.current
    val i18n = LocalI18n.current
    val session by container.sessionManager.state.collectAsState()
    val settings by container.preferences.settingsFlow.collectAsState(initial = AppSettingsSnapshot())
    val shadows by container.postShadowStore.shadows.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val modSettingsFlow = remember(session.currentDid) {
        session.currentDid?.let { container.preferences.observeModerationSettings(it) }
    }
    val modSettings = modSettingsFlow?.collectAsState(initial = ModerationUserSettings())?.value

    var profile by remember(actorHandleOrDid) { mutableStateOf<ProfileViewDetailed?>(null) }
    var query by remember(actorHandleOrDid) { mutableStateOf(initialQuery.orEmpty()) }
    var submittedQuery by remember(actorHandleOrDid) { mutableStateOf(initialQuery?.trim().orEmpty()) }
    var searchNonce by remember(actorHandleOrDid) { mutableIntStateOf(0) }
    var selectedSortIdx by remember(actorHandleOrDid) { mutableIntStateOf(0) } // 0 = Top, 1 = Latest

    var posts by remember(actorHandleOrDid) { mutableStateOf<List<PostView>>(emptyList()) }
    var cursor by remember(actorHandleOrDid) { mutableStateOf<String?>(null) }
    var isLoading by remember(actorHandleOrDid) { mutableStateOf(false) }
    var isRefreshing by remember(actorHandleOrDid) { mutableStateOf(false) }
    var isLoadingMore by remember(actorHandleOrDid) { mutableStateOf(false) }
    var errorMsg by remember(actorHandleOrDid) { mutableStateOf<String?>(null) }
    var hasSearched by remember(actorHandleOrDid) { mutableStateOf(false) }

    LaunchedEffect(actorHandleOrDid) {
        runCatching { container.api.getProfile(actorHandleOrDid) }
            .onSuccess { profile = it }
    }

    suspend fun runSearch(
        searchTerm: String,
        sort: String,
        refresh: Boolean = false,
        loadMore: Boolean = false,
    ) {
        val clean = searchTerm.trim()
        if (clean.isEmpty()) {
            posts = emptyList()
            cursor = null
            hasSearched = false
            return
        }
        if (loadMore && (isLoadingMore || cursor.isNullOrBlank())) return

        if (refresh) isRefreshing = true
        else if (loadMore) isLoadingMore = true
        else isLoading = true
        errorMsg = null
        hasSearched = true

        val nextCursor = if (loadMore) cursor else null
        runCatching {
            val authorDid = profile?.did ?: if (actorHandleOrDid.startsWith("did:")) {
                actorHandleOrDid
            } else {
                container.identityResolver.resolveHandle(actorHandleOrDid) ?: actorHandleOrDid
            }
            container.api.searchPosts(
                query = clean,
                sort = sort,
                author = authorDid,
                cursor = nextCursor,
                limit = 25,
            )
        }.onSuccess { res ->
            posts = if (loadMore) {
                (posts + res.posts).distinctBy { it.uri }
            } else {
                res.posts
            }
            cursor = res.cursor
        }.onFailure { ex ->
            errorMsg = ex.message ?: i18n.translate("Search failed")
        }

        isLoading = false
        isRefreshing = false
        isLoadingMore = false
    }

    val activeSort = if (selectedSortIdx == 0) "top" else "latest"

    LaunchedEffect(actorHandleOrDid, submittedQuery, activeSort, searchNonce) {
        if (submittedQuery.isNotBlank()) {
            runSearch(submittedQuery, activeSort)
        }
    }

    val shouldLoadMore by remember(listState, posts.size, cursor) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            posts.isNotEmpty() && !cursor.isNullOrBlank() && lastVisible >= posts.size - 6
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && submittedQuery.isNotBlank()) {
            runSearch(submittedQuery, activeSort, loadMore = true)
        }
    }

    val displayHandle = profile?.handle ?: actorHandleOrDid.removePrefix("@")

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.contrast0.toComposeColor())
                    .statusBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back"))
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = {
                            Text(
                                text = "Search @$displayHandle",
                                maxLines = 1,
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Filled.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = {
                                    query = ""
                                    submittedQuery = ""
                                    posts = emptyList()
                                    cursor = null
                                    hasSearched = false
                                }) {
                                    Icon(Icons.Filled.Clear, contentDescription = tr("Clear"))
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                keyboardController?.hide()
                                submittedQuery = query.trim()
                                searchNonce++
                            }
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }

                SecondaryTabRow(
                    selectedTabIndex = selectedSortIdx,
                    containerColor = palette.contrast0.toComposeColor(),
                    contentColor = palette.contrast1000.toComposeColor(),
                ) {
                    listOf(tr("Top"), tr("Latest")).forEachIndexed { idx, label ->
                        Tab(
                            selected = selectedSortIdx == idx,
                            onClick = { selectedSortIdx = idx },
                            text = {
                                Text(
                                    text = label,
                                    fontWeight = if (selectedSortIdx == idx) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            },
                        )
                    }
                }
                HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)
            }
        },
        containerColor = palette.contrast0.toComposeColor(),
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                if (submittedQuery.isNotBlank()) {
                    scope.launch { runSearch(submittedQuery, activeSort, refresh = true) }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                !hasSearched && submittedQuery.isBlank() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Search posts by @$displayHandle",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.contrast600.toComposeColor(),
                        )
                    }
                }
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                errorMsg != null && posts.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(errorMsg.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = {
                            scope.launch { runSearch(submittedQuery, activeSort, refresh = true) }
                        }) {
                            Text(tr("Try again"))
                        }
                    }
                }
                else -> {
                    val mutedThreads = modSettings?.mutedThreads.orEmpty()
                    val filteredPosts = remember(posts, shadows, session.moderationOpts, mutedThreads) {
                        posts.mapNotNull { post ->
                            val applied = container.postShadowStore.applyToPost(post) ?: return@mapNotNull null
                            if (applied.uri in mutedThreads) return@mapNotNull null
                            val modUi = ModerationEngine.moderatePost(applied, session.moderationOpts)
                                .ui(ModerationContext.contentList)
                            if (modUi.filter) return@mapNotNull null
                            applied
                        }
                    }

                    if (filteredPosts.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = tr("No posts found."),
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.contrast600.toComposeColor(),
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 32.dp),
                        ) {
                            itemsIndexed(
                                items = filteredPosts,
                                key = { _, item -> item.uri },
                            ) { _, post ->
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
                            }

                            if (isLoadingMore) {
                                item(key = "profile_search_footer") {
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
