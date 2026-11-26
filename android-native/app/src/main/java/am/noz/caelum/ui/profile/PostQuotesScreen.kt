package am.noz.caelum.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import am.noz.caelum.core.i18n.LocalI18n
import am.noz.caelum.core.i18n.tr
import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.model.PostView
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostQuotesScreen(
    actorHandleOrDid: String,
    rkey: String,
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
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val modSettingsFlow = remember(session.currentDid) {
        session.currentDid?.let { container.preferences.observeModerationSettings(it) }
    }
    val modSettings = modSettingsFlow?.collectAsState(initial = ModerationUserSettings())?.value

    var posts by remember(actorHandleOrDid, rkey) { mutableStateOf<List<PostView>>(emptyList()) }
    var cursor by remember(actorHandleOrDid, rkey) { mutableStateOf<String?>(null) }
    var isLoading by remember(actorHandleOrDid, rkey) { mutableStateOf(true) }
    var isRefreshing by remember(actorHandleOrDid, rkey) { mutableStateOf(false) }
    var isLoadingMore by remember(actorHandleOrDid, rkey) { mutableStateOf(false) }
    var errorMsg by remember(actorHandleOrDid, rkey) { mutableStateOf<String?>(null) }

    suspend fun loadQuotes(refresh: Boolean = false, loadMore: Boolean = false) {
        if (loadMore && (isLoadingMore || cursor.isNullOrBlank())) return
        if (!refresh && !loadMore && posts.isNotEmpty()) return

        if (refresh) isRefreshing = true
        else if (loadMore) isLoadingMore = true
        else isLoading = true
        errorMsg = null

        val nextCursor = if (loadMore) cursor else null

        runCatching {
            val did = if (actorHandleOrDid.startsWith("did:")) {
                actorHandleOrDid
            } else {
                container.identityResolver.resolveHandle(actorHandleOrDid)
                    ?: error("Could not resolve @$actorHandleOrDid")
            }
            val uri = "at://$did/app.bsky.feed.post/$rkey"
            val res = container.api.getQuotes(uri, nextCursor)
            val decoded = (res["posts"] as? JsonArray)?.mapNotNull { el ->
                runCatching { AtProtoJson.decodeFromJsonElement<PostView>(el) }.getOrNull()
            }.orEmpty()
            val cur = (res["cursor"] as? JsonPrimitive)?.content
            decoded to cur
        }.onSuccess { (items, newCursor) ->
            posts = if (loadMore) {
                (posts + items).distinctBy { it.uri }
            } else {
                items
            }
            cursor = newCursor
        }.onFailure { ex ->
            errorMsg = ex.message ?: i18n.translate("Unable to load quotes")
        }

        isLoading = false
        isRefreshing = false
        isLoadingMore = false
    }

    LaunchedEffect(actorHandleOrDid, rkey) {
        loadQuotes()
    }

    val shouldLoadMore by remember(listState, posts.size, cursor) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            posts.isNotEmpty() && !cursor.isNullOrBlank() && lastVisible >= posts.size - 6
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) loadQuotes(loadMore = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.contrast0.toComposeColor()),
                title = { Text(tr("Quotes")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back"))
                    }
                },
            )
        },
        containerColor = palette.contrast0.toComposeColor(),
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { scope.launch { loadQuotes(refresh = true) } },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
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
                        Button(onClick = { scope.launch { loadQuotes(refresh = true) } }) {
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
                                text = tr("No quotes yet."),
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
                                item(key = "quotes_footer") {
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
