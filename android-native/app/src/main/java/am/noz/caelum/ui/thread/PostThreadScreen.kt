package am.noz.caelum.ui.thread

import android.icu.text.MessageFormat
import android.widget.Toast
import am.noz.caelum.R
import am.noz.caelum.core.i18n.LocalI18n
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import am.noz.caelum.core.i18n.tr
import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.model.PostView
import am.noz.caelum.core.model.ThreadNode
import am.noz.caelum.core.model.ThreadSort
import am.noz.caelum.core.model.ThreadViewOptions
import am.noz.caelum.core.model.TimeFormatter
import am.noz.caelum.core.model.UnavailableThreadPost
import am.noz.caelum.core.model.loadPostThread
import am.noz.caelum.core.model.mergeExpansion
import am.noz.caelum.core.model.replaceBranch
import am.noz.caelum.core.model.replies
import am.noz.caelum.core.model.withPublishedReplies
import am.noz.caelum.core.moderation.ModerationContext
import am.noz.caelum.core.moderation.ModerationEngine
import am.noz.caelum.core.moderation.ModerationOpts
import am.noz.caelum.core.richtext.RichTextFacet
import am.noz.caelum.core.session.ScreenStateCache
import am.noz.caelum.core.storage.AppSettingsSnapshot
import am.noz.caelum.core.storage.ModerationUserSettings
import am.noz.caelum.ui.components.AvatarShape
import am.noz.caelum.ui.components.CaelumAvatar
import am.noz.caelum.ui.components.LinkMismatchWarningDialog
import am.noz.caelum.ui.components.LinkNavigationHelper
import am.noz.caelum.ui.components.LocalAppContainer
import am.noz.caelum.ui.components.LocalScreenStateCache
import am.noz.caelum.ui.components.RichTextView
import am.noz.caelum.ui.components.VerificationBadge
import am.noz.caelum.ui.components.rememberAccountCoroutineScope
import am.noz.caelum.ui.navigation.AppRoute
import am.noz.caelum.ui.openin.OpenInSubject
import am.noz.caelum.ui.post.EmbedImageItem
import am.noz.caelum.ui.post.PostActionRow
import am.noz.caelum.ui.post.PostAlertPills
import am.noz.caelum.ui.post.PostAuthorFollowButton
import am.noz.caelum.ui.post.PostCard
import am.noz.caelum.ui.post.PostMoreSheet
import am.noz.caelum.ui.post.PostEmbeds
import am.noz.caelum.ui.post.formatCompactCount
import am.noz.caelum.ui.post.isLiveAvatarStatus
import am.noz.caelum.ui.post.isAuthorBlocked
import am.noz.caelum.ui.post.postModerationCauseTitle
import am.noz.caelum.ui.settings.upsertActorPreference
import am.noz.caelum.ui.theme.LocalAlfPalette
import am.noz.caelum.ui.theme.toComposeColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.random.Random
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private enum class ThreadLoadState { Pending, Loaded, Failed }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostThreadScreen(
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
    val screenCache = LocalScreenStateCache.current
    val palette = LocalAlfPalette.current
    val context = LocalContext.current
    val session by container.sessionManager.state.collectAsState()
    val settings by container.preferences.settingsFlow.collectAsState(initial = AppSettingsSnapshot())
    val shadows by container.postShadowStore.shadows.collectAsState()
    val publishedReplies by screenCache.recentlyPublishedReplies.collectAsState()
    val scope = rememberAccountCoroutineScope()

    val cacheKey = "$actorHandleOrDid/$rkey"
    var cached by remember(cacheKey) { mutableStateOf(screenCache.threads[cacheKey]) }
    var retry by remember(cacheKey) { mutableIntStateOf(0) }
    val workInProgress = remember(cacheKey) { mutableStateListOf<String>() }
    val preferencesMutex = remember { Mutex() }
    val preferencesFlow = remember(session.currentDid) {
        session.currentDid?.let(container.preferences::observeModerationSettings) ?: flowOf(ModerationUserSettings())
    }
    val preferences by preferencesFlow.collectAsState(initial = null)
    var guestSort by rememberSaveable { mutableStateOf(ThreadSort.Top.name) }
    var guestThreaded by rememberSaveable { mutableStateOf(false) }
    val randomSeed = rememberSaveable { Random.nextInt() }
    val sort = if (session.hasSession) {
        ThreadSort.fromPreference(preferences?.threadReplyPrioritization ?: ThreadSort.Top.preference)
    } else {
        ThreadSort.valueOf(guestSort)
    }
    val options = ThreadViewOptions(
        sort = sort,
        threaded = if (session.hasSession) preferences?.threadedReplies == true else guestThreaded,
        randomSeed = if (sort == ThreadSort.RandomOrder) randomSeed else 0,
    )
    val loadError = tr("Couldn’t load more posts. Try again")
    val preferenceError = tr("Failed to save settings. Please try again.")
    val deleteError = tr("Failed to delete post, please try again")

    fun saveThread(state: ScreenStateCache.CachedPostThread) {
        cached = state
        screenCache.threads[cacheKey] = state
    }

    fun setOptions(sort: ThreadSort? = null, threaded: Boolean? = null) {
        val did = session.currentDid
        if (did == null) {
            sort?.let { guestSort = it.name }
            threaded?.let { guestThreaded = it }
            return
        }
        scope.launch {
            preferencesMutex.withLock {
                container.preferences.updateModerationSettings(did) {
                    it.copy(
                        threadReplyPrioritization = sort?.preference ?: it.threadReplyPrioritization,
                        threadedReplies = threaded ?: it.threadedReplies,
                    )
                }
                try {
                    upsertActorPreference(container.api, "app.bsky.actor.defs#threadViewPref") { existing ->
                        buildJsonObject {
                            existing?.forEach { (key, value) -> put(key, value) }
                            put("\$type", JsonPrimitive("app.bsky.actor.defs#threadViewPref"))
                            sort?.let { put("sort", JsonPrimitive(it.preference)) }
                            threaded?.let { put("lab_treeViewEnabled", JsonPrimitive(it)) }
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    Toast.makeText(context, preferenceError, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val labelerDids = session.moderationOpts.prefs.labelers.map { it.did }
    val preferencesReady = preferences != null
    var loadState by remember(cacheKey, options, retry, preferencesReady, labelerDids) {
        mutableStateOf(ThreadLoadState.Pending)
    }
    val loading = loadState == ThreadLoadState.Pending
    val failed = loadState == ThreadLoadState.Failed
    LaunchedEffect(cacheKey, options, retry, preferencesReady, labelerDids) {
        if (!preferencesReady) return@LaunchedEffect
        try {
            val did = if (actorHandleOrDid.startsWith("did:")) actorHandleOrDid
                else container.identityResolver.resolveHandle(actorHandleOrDid) ?: error(loadError)
            val uri = "at://$did/app.bsky.feed.post/$rkey"
            val fresh = container.api.loadPostThread(uri, options)
            val previous = cached?.takeIf { it.options == options }
            val ancestors = if (fresh.parents.firstOrNull()?.moreParents == true) {
                val first = previous?.thread?.parents?.takeWhile { it.uri != fresh.parents.first().uri }.orEmpty()
                first + fresh.parents
            } else {
                fresh.parents
            }
            val thread = fresh.copy(
                parents = ancestors,
                anchor = previous?.thread?.anchor?.mergeExpansion(fresh.anchor) ?: fresh.anchor,
            )
            saveThread(ScreenStateCache.CachedPostThread(options, thread, previous?.otherReplies))
            loadState = ThreadLoadState.Loaded
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            loadState = ThreadLoadState.Failed
        }
    }

    fun continueThread(key: String, request: suspend (ScreenStateCache.CachedPostThread) -> Unit) {
        val current = cached ?: return
        if (key in workInProgress || loading) return
        workInProgress.add(key)
        scope.launch {
            try {
                request(current)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                Toast.makeText(context, loadError, Toast.LENGTH_SHORT).show()
            } finally {
                workInProgress.remove(key)
            }
        }
    }

    val thread = cached?.thread
    val displayedAnchor = remember(thread, publishedReplies, cached?.options) {
        thread?.anchor?.withPublishedReplies(publishedReplies, checkNotNull(cached).options)
    }
    val anchorPost = remember(displayedAnchor, shadows) {
        displayedAnchor?.post?.let(container.postShadowStore::applyToPost)?.let { post ->
            post.copy(threadgate = post.threadgate ?: thread?.threadgate)
        }
    }

    @Composable
    fun ThreadPostCard(node: ThreadNode, connectsAbove: Boolean, connectsBelow: Boolean) {
        val post = remember(node.post, shadows) {
            node.post?.let(container.postShadowStore::applyToPost)
        }
        if (post == null) {
            ThreadTombstone(node.unavailable)
            return
        }
        PostCard(
            post = post,
            moderationOpts = session.moderationOpts,
            currentUserDid = session.currentDid,
            inThread = true,
            showTopThreadLine = connectsAbove,
            showBottomThreadLine = connectsBelow,
            largeAltBadge = settings.largeAltBadgeEnabled,
            disableAutoplay = settings.disableAutoplay,
            disableHaptics = settings.disableHaptics,
            useLinkRedirects = settings.infrastructure.useLinkRedirects,
            openBskyLinksInCaelum = settings.openBskyLinksInCaelum,
            onPostClick = { p ->
                onNavigate(AppRoute.PostThread(p.author.handle.ifBlank { p.author.did }, p.uri.substringAfterLast('/')))
            },
            onReplyClick = { p -> onOpenComposer(p, null) },
            onQuoteClick = { p -> onOpenComposer(null, p) },
            onRepostClick = { p -> scope.launch { container.postController.toggleRepost(p) } },
            onLikeClick = { p -> scope.launch { container.postController.toggleLike(p) } },
            onBookmarkClick = { p -> scope.launch { container.postController.toggleBookmark(p) } },
            onEditPostClick = onOpenEditPost,
            onDeletePostClick = { p ->
                scope.launch {
                    container.postController.deletePost(p).onFailure {
                        Toast.makeText(context, deleteError, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onToggleThreadMuteClick = { p -> scope.launch { container.postController.toggleThreadMute(p) } },
            onHidePostClick = { p -> scope.launch { container.postController.hidePost(p.uri) } },
            onReportPostClick = onOpenReport,
            onOpenInClick = { p -> onOpenIn(OpenInSubject.Post(p.uri, p.author.did, p.author.handle)) },
            onOpenLightbox = onOpenLightbox,
            onOpenVideoLightbox = onOpenVideoLightbox,
            onNavigate = onNavigate,
        )
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(tr("Post"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
                    expandedHeight = 56.dp,
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.contrast0.toComposeColor()),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back"))
                        }
                    },
                    actions = {
                        ThreadOptionsMenu(
                            options = options,
                            onSort = { setOptions(sort = it) },
                            onView = { setOptions(threaded = it) },
                        )
                    },
                )
                HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)
            }
        },
        bottomBar = {
            if (session.hasSession && anchorPost != null && !anchorPost.isAuthorBlocked &&
                anchorPost.viewer?.replyDisabled != true
            ) {
                Surface(
                    color = palette.contrast0.toComposeColor(),
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding(),
                ) {
                    Surface(
                        onClick = { onOpenComposer(anchorPost, null) },
                        shape = CircleShape,
                        color = palette.contrast50.toComposeColor(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CaelumAvatar(url = session.currentAccount?.avatar, contentDescription = null, size = 24.dp)
                            Text(
                                text = tr("Write your reply"),
                                color = palette.contrast700.toComposeColor(),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        },
        containerColor = palette.contrast0.toComposeColor(),
    ) { padding ->
        if (thread == null || displayedAnchor == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (failed) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(loadError)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { retry++ }) { Text(tr("Retry")) }
                    }
                } else {
                    CircularProgressIndicator()
                }
            }
            return@Scaffold
        }

        val earliest = thread.parents.firstOrNull()?.takeIf { it.moreParents }
        val anchorIndex = thread.parents.size + if (earliest != null) 1 else 0
        val anchorKey = "anchor_${thread.anchor.uri}"
        val rows = remember(displayedAnchor) { displayedAnchor.replyRows() }
        val other = cached?.otherReplies
        val otherRows = remember(displayedAnchor, other) {
            other.orEmpty().filter { displayedAnchor.find(it.uri)?.post == null }
        }
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = anchorIndex)
        val appliedOptions = checkNotNull(cached).options
        val positionOptions = "${appliedOptions.sort}:${appliedOptions.threaded}"
        var previousPositionOptions by rememberSaveable { mutableStateOf(positionOptions) }
        LaunchedEffect(positionOptions) {
            if (previousPositionOptions != positionOptions) {
                listState.requestScrollToItem(anchorIndex)
                previousPositionOptions = positionOptions
            }
        }

        val lastKey = when {
            failed -> "retry"
            otherRows.isNotEmpty() -> "other_${otherRows.last().uri}"
            thread.hasOtherReplies -> "other"
            rows.isNotEmpty() -> rows.last().key
            else -> anchorKey
        }
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val density = LocalDensity.current
            val viewportPx = with(density) { maxHeight.roundToPx() }
            val defaultPadding = if (anchorIndex > 0) maxHeight else 0.dp
            var trailingPaddingPx by rememberSaveable { mutableIntStateOf(-1) }
            /*
             * A nested anchor can have less than one screen of content below it. Give the list
             * enough space to align it at the top, then keep only the measured shortfall.
             */
            LaunchedEffect(listState, anchorKey, lastKey, viewportPx) {
                snapshotFlow { listState.layoutInfo.visibleItemsInfo }.collect { visible ->
                    val anchorItem = visible.firstOrNull { it.key == anchorKey } ?: return@collect
                    val lastItem = visible.firstOrNull { it.key == lastKey }
                    val contentHeight = lastItem?.let { it.offset + it.size - anchorItem.offset } ?: viewportPx
                    trailingPaddingPx = if (anchorIndex == 0) 0 else (viewportPx - contentHeight).coerceAtLeast(0)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    bottom = if (trailingPaddingPx < 0) defaultPadding else with(density) { trailingPaddingPx.toDp() },
                ),
            ) {
                if (earliest != null) {
                    item(key = "ancestors") {
                        TextButton(
                            enabled = !loading && earliest.uri !in workInProgress,
                            onClick = {
                                continueThread(earliest.uri) { state ->
                                    val response = container.api.loadPostThread(earliest.uri, state.options)
                                    val latest = cached?.takeIf { it.options == state.options } ?: return@continueThread
                                    val parents = (response.parents + latest.thread.parents).distinctBy { it.uri }
                                    val updated = parents.map { if (it.uri == earliest.uri) it.copy(moreParents = false) else it }
                                    saveThread(latest.copy(thread = latest.thread.copy(parents = updated)))
                                    if (response.parents.isEmpty()) {
                                        Toast.makeText(context, loadError, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.padding(start = 16.dp),
                        ) { Text(tr("View full thread")) }
                    }
                }
                itemsIndexed(thread.parents, key = { _, node -> "parent_${node.uri}" }) { index, parent ->
                    ThreadPostCard(parent, connectsAbove = index > 0 || earliest != null, connectsBelow = true)
                }

                item(key = anchorKey) {
                    if (anchorPost == null) {
                        ThreadTombstone(thread.anchor.unavailable)
                    } else {
                        val anchorHandle = anchorPost.author.handle.ifBlank { anchorPost.author.did }
                        val anchorRkey = anchorPost.uri.substringAfterLast('/')
                        AnchorThreadPost(
                            post = anchorPost,
                            hasParentAbove = thread.parents.isNotEmpty(),
                            moderationOpts = session.moderationOpts,
                            currentUserDid = session.currentDid,
                            largeAltBadge = settings.largeAltBadgeEnabled,
                            disableAutoplay = settings.disableAutoplay,
                            disableHaptics = settings.disableHaptics,
                            useLinkRedirects = settings.infrastructure.useLinkRedirects,
                            openBskyLinksInCaelum = settings.openBskyLinksInCaelum,
                            onNavigate = onNavigate,
                            onReplyClick = { p -> onOpenComposer(p, null) },
                            onQuoteClick = { p -> onOpenComposer(null, p) },
                            onRepostClick = { p -> scope.launch { container.postController.toggleRepost(p) } },
                            onLikeClick = { p -> scope.launch { container.postController.toggleLike(p) } },
                            onBookmarkClick = { p -> scope.launch { container.postController.toggleBookmark(p) } },
                            onEditPostClick = onOpenEditPost,
                            onDeletePostClick = { p ->
                                scope.launch {
                                    container.postController.deletePost(p).onSuccess {
                                        screenCache.threads.remove(cacheKey)
                                        onBack()
                                    }.onFailure {
                                        Toast.makeText(context, deleteError, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onToggleThreadMuteClick = { p -> scope.launch { container.postController.toggleThreadMute(p) } },
                            onHidePostClick = { p ->
                                scope.launch {
                                    container.postController.hidePost(p.uri)
                                    screenCache.threads.remove(cacheKey)
                                    onBack()
                                }
                            },
                            onReportPostClick = onOpenReport,
                            onOpenInClick = { p ->
                                onOpenIn(OpenInSubject.Post(p.uri, p.author.did, p.author.handle.ifBlank { p.author.did }))
                            },
                            onOpenLikedBy = { onNavigate(AppRoute.PostLikedBy(anchorHandle, anchorRkey)) },
                            onOpenRepostedBy = { onNavigate(AppRoute.PostRepostedBy(anchorHandle, anchorRkey)) },
                            onOpenQuotes = { onNavigate(AppRoute.PostQuotes(anchorHandle, anchorRkey)) },
                            onOpenLightbox = onOpenLightbox,
                            onOpenVideoLightbox = onOpenVideoLightbox,
                        )
                    }
                }

                itemsIndexed(rows, key = { _, row -> row.key }) { _, row ->
                    val indent = if (appliedOptions.threaded) 14.dp * row.depth.coerceAtMost(4) else 0.dp
                    Box(Modifier.padding(start = indent)) {
                        if (row.continuation) {
                            ReplyContinuation(
                                branches = row.branches,
                                enabled = !loading && row.branches.none { it.uri in workInProgress },
                                onExpand = { branch ->
                                    continueThread(branch.uri) { state ->
                                        val response = container.api.loadPostThread(branch.uri, state.options, above = false)
                                        val latest = cached?.takeIf { it.options == state.options } ?: return@continueThread
                                        val previous = latest.thread.anchor.find(branch.uri) ?: return@continueThread
                                        val expanded = previous.mergeExpansion(response.anchor)
                                        val unchanged = expanded.moreReplies > 0 &&
                                            expanded.replyRows().map { it.key } == previous.replyRows().map { it.key }
                                        val anchorUri = latest.thread.anchor.uri
                                        if (unchanged && branch.uri != anchorUri && previous.post != null) {
                                            val post = previous.post
                                            onNavigate(
                                                AppRoute.PostThread(
                                                    post.author.handle.ifBlank { post.author.did },
                                                    post.uri.substringAfterLast('/'),
                                                )
                                            )
                                            return@continueThread
                                        }
                                        val next = if (unchanged) expanded.copy(moreReplies = 0) else expanded
                                        val updated = latest.thread.anchor.replaceBranch(branch.uri, next)
                                        saveThread(latest.copy(thread = latest.thread.copy(
                                            anchor = updated,
                                            hasOtherReplies = latest.thread.hasOtherReplies ||
                                                (branch.uri == anchorUri && response.hasOtherReplies),
                                        )))
                                        if (unchanged && !response.hasOtherReplies) {
                                            Toast.makeText(context, loadError, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                            )
                        } else {
                            ThreadPostCard(
                                row.node,
                                connectsAbove = !appliedOptions.threaded && row.connectsAbove,
                                connectsBelow = !appliedOptions.threaded && row.node.children.isNotEmpty(),
                            )
                        }
                    }
                }

                if (thread.hasOtherReplies) {
                    item(key = "other") {
                        if (other == null) {
                            TextButton(
                                enabled = !loading && "other" !in workInProgress,
                                onClick = {
                                    continueThread("other") { state ->
                                        val response = container.api.getPostThreadOtherV2(state.thread.anchor.uri)
                                        val latest = cached?.takeIf { it.options == state.options } ?: return@continueThread
                                        saveThread(latest.copy(otherReplies = response.replies(state.options, revealHidden = true)))
                                    }
                                },
                                modifier = Modifier.padding(start = 16.dp),
                            ) { Text(tr("Show other replies")) }
                        } else {
                            Text(
                                text = tr(if (otherRows.isEmpty()) "No other replies" else "Other replies"),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                        }
                    }
                    itemsIndexed(otherRows, key = { _, node -> "other_${node.uri}" }) { _, node ->
                        ThreadPostCard(node, connectsAbove = false, connectsBelow = false)
                    }
                }
                if (failed) {
                    item(key = "retry") {
                        TextButton(onClick = { retry++ }, modifier = Modifier.padding(start = 16.dp)) { Text(tr("Retry")) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplyContinuation(
    branches: List<ThreadNode>,
    enabled: Boolean,
    onExpand: (ThreadNode) -> Unit,
) {
    var chooseBranch by remember(branches) { mutableStateOf(false) }
    val i18n = LocalI18n.current
    Box(Modifier.padding(start = 16.dp)) {
        TextButton(
            enabled = enabled,
            onClick = {
                if (branches.size == 1) onExpand(branches.single()) else chooseBranch = true
            },
        ) { Text(tr("Show more replies")) }
        DropdownMenu(expanded = chooseBranch, onDismissRequest = { chooseBranch = false }) {
            branches.forEach { branch ->
                DropdownMenuItem(
                    text = {
                        Column {
                            branch.post?.let { post ->
                                Text("@${post.author.handle} · ${i18n.formatRelativeShort(post.indexedAt)}")
                            }
                            Text(tr("Read {0, plural, one {# more reply} other {# more replies}}",
                                mapOf("0" to branch.moreReplies)))
                        }
                    },
                    onClick = {
                        chooseBranch = false
                        onExpand(branch)
                    },
                )
            }
        }
    }
}

@Composable
private fun ThreadOptionsMenu(
    options: ThreadViewOptions,
    onSort: (ThreadSort) -> Unit,
    onView: (Boolean) -> Unit,
) {
    val palette = LocalAlfPalette.current
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                painter = painterResource(R.drawable.ic_thread_options),
                contentDescription = tr("Thread options"),
                tint = palette.contrast700.toComposeColor(),
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text(tr("Show replies as"), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(12.dp))
            for (threaded in listOf(false, true)) {
                DropdownMenuItem(
                    text = { Text(tr(if (threaded) "Threaded" else "Linear")) },
                    onClick = { open = false; onView(threaded) },
                    trailingIcon = { RadioButton(selected = options.threaded == threaded, onClick = null) },
                )
            }
            HorizontalDivider()
            Text(tr("Reply sorting"), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(12.dp))
            for (sort in ThreadSort.entries) {
                DropdownMenuItem(
                    text = {
                        Text(when (sort) {
                            ThreadSort.Top -> tr("Top replies first")
                            ThreadSort.Oldest -> tr("Oldest replies first")
                            ThreadSort.Newest -> tr("Newest replies first")
                            ThreadSort.MostLiked -> tr("Most liked")
                            ThreadSort.RandomOrder -> tr("Random")
                        })
                    },
                    onClick = { open = false; onSort(sort) },
                    trailingIcon = { RadioButton(selected = options.sort == sort, onClick = null) },
                )
            }
        }
    }
}

@Composable
private fun ThreadTombstone(reason: UnavailableThreadPost) {
    val palette = LocalAlfPalette.current
    Text(
        text = when (reason) {
            UnavailableThreadPost.NotFound -> tr("Post not found")
            UnavailableThreadPost.Blocked -> tr("Post blocked")
            UnavailableThreadPost.SignInRequired -> tr("Sign in to view this post")
            UnavailableThreadPost.Hidden -> tr("Post Hidden by You")
        },
        style = MaterialTheme.typography.bodyMedium,
        color = palette.contrast700.toComposeColor(),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
    )
    HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)
}

@Composable
private fun AnchorThreadPost(
    post: PostView,
    hasParentAbove: Boolean,
    moderationOpts: ModerationOpts,
    currentUserDid: String?,
    largeAltBadge: Boolean,
    disableAutoplay: Boolean,
    disableHaptics: Boolean,
    useLinkRedirects: Boolean,
    openBskyLinksInCaelum: Boolean,
    onNavigate: (AppRoute) -> Unit,
    onReplyClick: (PostView) -> Unit,
    onQuoteClick: (PostView) -> Unit,
    onRepostClick: (PostView) -> Unit,
    onLikeClick: (PostView) -> Unit,
    onBookmarkClick: (PostView) -> Unit,
    onEditPostClick: (PostView) -> Unit,
    onDeletePostClick: (PostView) -> Unit,
    onToggleThreadMuteClick: (PostView) -> Unit,
    onHidePostClick: (PostView) -> Unit,
    onReportPostClick: (PostView) -> Unit,
    onOpenInClick: (PostView) -> Unit,
    onOpenLikedBy: () -> Unit,
    onOpenRepostedBy: () -> Unit,
    onOpenQuotes: () -> Unit,
    onOpenLightbox: (List<EmbedImageItem>, Int) -> Unit,
    onOpenVideoLightbox: (String, String?, String?) -> Unit,
) {
    val context = LocalContext.current
    val palette = LocalAlfPalette.current

    val text = (post.record["text"] as? JsonPrimitive)?.content.orEmpty()
    val updatedAt = (post.record["updatedAt"] as? JsonPrimitive)?.content
    val facets: List<RichTextFacet> = remember(post.record["facets"]) {
        (post.record["facets"] as? JsonArray)?.mapNotNull {
            runCatching { AtProtoJson.decodeFromJsonElement<RichTextFacet>(it) }.getOrNull()
        }.orEmpty()
    }

    val handleForNav = post.author.handle.ifBlank { post.author.did }
    val mutedColor = palette.contrast600.toComposeColor()
    val dividerColor = palette.contrast100.toComposeColor()

    val modDecision = remember(post, moderationOpts) {
        ModerationEngine.moderatePost(post, moderationOpts)
    }
    val viewUi = remember(modDecision) { modDecision.ui(ModerationContext.contentView) }
    val mediaUi = remember(modDecision) { modDecision.ui(ModerationContext.contentMedia) }
    val avatarBlurred = remember(post.author, moderationOpts) {
        ModerationEngine.moderateProfile(post.author, moderationOpts).ui(ModerationContext.avatar).blur
    }

    var shieldExpanded by remember(post.uri) { mutableStateOf(false) }
    var showMoreMenu by remember(post.uri, currentUserDid) { mutableStateOf(false) }
    var pendingExternalMismatch by remember(post.uri) { mutableStateOf<Pair<String, String>?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.contrast0.toComposeColor()),
    ) {
        if (hasParentAbove) {
            Box(
                modifier = Modifier
                    .padding(start = 36.dp)
                    .width(2.dp)
                    .height(10.dp)
                    .background(palette.contrast200.toComposeColor()),
            )
        } else {
            Spacer(Modifier.height(10.dp))
        }

        // Author Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CaelumAvatar(
                url = post.author.avatar.takeUnless { avatarBlurred },
                contentDescription = post.author.displayName ?: post.author.handle,
                size = 42.dp,
                shapeType = if (post.author.associated?.labeler == true) AvatarShape.RoundedSquare else AvatarShape.Circle,
                isLive = !avatarBlurred && post.author.status.isLiveAvatarStatus(),
                onClick = { onNavigate(AppRoute.Profile(handleForNav)) },
            )
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onNavigate(AppRoute.Profile(handleForNav)) },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = post.author.displayName?.takeIf { it.isNotBlank() } ?: "@${post.author.handle}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = palette.contrast1000.toComposeColor(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    VerificationBadge(post.author.verification)
                }
                Text(
                    text = "@${post.author.handle}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = mutedColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            PostAuthorFollowButton(post, currentUserDid, disableHaptics)
        }

        Spacer(Modifier.height(10.dp))

        // Moderation Warning / Blur Shield + Body + Embeds
        if (viewUi.blur && (!shieldExpanded || viewUi.noOverride)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.contrast25.toComposeColor())
                    .border(1.dp, dividerColor, RoundedCornerShape(10.dp))
                    .clickable(enabled = !viewUi.noOverride) { shieldExpanded = true }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.VisibilityOff, contentDescription = null, tint = mutedColor, modifier = Modifier.size(18.dp))
                    Text(
                        text = postModerationCauseTitle(viewUi.blurs.firstOrNull()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.contrast800.toComposeColor(),
                    )
                }
                if (!viewUi.noOverride) {
                    Text(
                        text = tr("Show"),
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.primary500.toComposeColor(),
                    )
                }
            }
        } else {
            PostAlertPills(
                moderation = viewUi,
                moderationOpts = moderationOpts,
                expanded = true,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )

            if (text.isNotBlank()) {
                SelectionContainer(Modifier.padding(horizontal = 16.dp)) {
                    RichTextView(
                        text = text,
                        facets = facets,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 17.sp,
                            lineHeight = 22.sp,
                            color = palette.contrast1000.toComposeColor(),
                        ),
                        onMentionClick = { didOrHandle -> onNavigate(AppRoute.Profile(didOrHandle)) },
                        onTagClick = { tag -> onNavigate(AppRoute.Hashtag(tag)) },
                        onLinkClick = { displayText, uri ->
                            if (LinkNavigationHelper.isPotentiallyMisleadingLink(displayText, uri)) {
                                pendingExternalMismatch = displayText to uri
                            } else {
                                LinkNavigationHelper.openUrl(
                                    context = context,
                                    rawUrl = uri,
                                    useLinkRedirects = useLinkRedirects,
                                    openBskyLinksInCaelum = openBskyLinksInCaelum,
                                    onInternalRoute = onNavigate,
                                )
                            }
                        },
                    )
                }
            }

            if (post.embed != null) {
                Spacer(Modifier.height(10.dp))
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    PostEmbeds(
                        embed = post.embed,
                        mediaModeration = mediaUi,
                        moderationOpts = moderationOpts,
                        expanded = true,
                        blurReason = postModerationCauseTitle(mediaUi.blurs.firstOrNull()),
                        largeAltBadge = largeAltBadge,
                        disableAutoplay = disableAutoplay,
                        onOpenLightbox = onOpenLightbox,
                        onOpenVideoLightbox = onOpenVideoLightbox,
                        onNavigate = onNavigate,
                        onOpenExternalLink = { uri ->
                            LinkNavigationHelper.openUrl(
                                context = context,
                                rawUrl = uri,
                                useLinkRedirects = useLinkRedirects,
                                openBskyLinksInCaelum = openBskyLinksInCaelum,
                                onInternalRoute = onNavigate,
                            )
                        },
                    )
                }
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val i18n = LocalI18n.current
            val localTime = TimeFormatter.parseInstantOrNull(post.indexedAt)?.let {
                DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                    .withLocale(i18n.locale).withZone(ZoneId.systemDefault()).format(it)
            }
            Text(
                text = buildString {
                    if (localTime != null) append("$localTime · ")
                    append(i18n.formatDate(post.indexedAt, timeStyle = null))
                    if (updatedAt != null) append(" · ${tr("Edited")}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = mutedColor,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            if (post.record["reply"] == null || post.viewer?.replyDisabled == true) {
                AnchorReplyPermissions(post, Modifier.align(Alignment.CenterVertically))
            }
        }

        val reposts = post.repostCount ?: 0
        val quotes = (post.quoteCount ?: 0).takeUnless { post.viewer?.embeddingDisabled == true } ?: 0
        val likes = post.likeCount ?: 0
        val saves = post.bookmarkCount ?: 0
        if (reposts > 0 || quotes > 0 || likes > 0 || saves > 0) {
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = dividerColor, thickness = 0.5.dp)
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (reposts > 0) {
                    AnchorStatCount(reposts, "{0, plural, one {# repost} other {# reposts}}", onOpenRepostedBy)
                }
                if (quotes > 0) {
                    AnchorStatCount(quotes, "{0, plural, one {# quote} other {# quotes}}", onOpenQuotes)
                }
                if (likes > 0) {
                    AnchorStatCount(likes, "{0, plural, one {# like} other {# likes}}", onOpenLikedBy)
                }
                if (saves > 0) {
                    AnchorStatCount(saves, "{0, plural, one {# save} other {# saves}}")
                }
            }
        }

        PostActionRow(
            post = post,
            signedIn = currentUserDid != null,
            disableHaptics = disableHaptics,
            onReply = { onReplyClick(post) },
            onQuote = { onQuoteClick(post) },
            onRepost = { onRepostClick(post) },
            onLike = { onLikeClick(post) },
            onSave = { onBookmarkClick(post) },
            onMore = { showMoreMenu = true },
            onOpenIn = { onOpenInClick(post) },
            onNavigate = onNavigate,
            expanded = true,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
        )
        HorizontalDivider(color = dividerColor, thickness = 0.5.dp)
    }

    PostMoreSheet(
        post = post,
        currentUserDid = currentUserDid,
        showMenu = showMoreMenu,
        onDismiss = { showMoreMenu = false },
        onEditPostClick = onEditPostClick,
        onDeletePostClick = onDeletePostClick,
        onToggleThreadMuteClick = onToggleThreadMuteClick,
        onHidePostClick = onHidePostClick,
        onReportPostClick = onReportPostClick,
        onOpenInClick = onOpenInClick,
        onNavigate = onNavigate,
        contentVisible = !viewUi.blur || shieldExpanded && !viewUi.noOverride,
    )

    pendingExternalMismatch?.let { (disp, uri) ->
        LinkMismatchWarningDialog(
            displayText = disp,
            targetUri = uri,
            onConfirm = {
                pendingExternalMismatch = null
                LinkNavigationHelper.openUrl(context, uri, useLinkRedirects, openBskyLinksInCaelum, onNavigate)
            },
            onDismiss = { pendingExternalMismatch = null },
        )
    }
}

@Composable
private fun AnchorReplyPermissions(post: PostView, modifier: Modifier = Modifier) {
    val palette = LocalAlfPalette.current
    var showPermissions by remember(post.uri) { mutableStateOf(false) }
    val rules = post.threadgate?.record?.get("allow") as? JsonArray
    val blocked = rules?.isEmpty() == true
    val restricted = rules?.isNotEmpty() == true || post.viewer?.replyDisabled == true
    val label = when {
        blocked -> tr("Replies disabled")
        restricted -> tr("Some can reply")
        else -> tr("Everybody can reply")
    }
    val mutedColor = palette.contrast700.toComposeColor()
    Row(
        modifier = modifier.clip(RoundedCornerShape(4.dp)).clickable { showPermissions = true },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (blocked || restricted) Icons.Filled.Lock else Icons.Filled.Language,
            contentDescription = null,
            tint = mutedColor,
            modifier = Modifier.size(14.dp),
        )
        Text(label, style = MaterialTheme.typography.bodySmall, color = mutedColor)
    }
    if (showPermissions) {
        AlertDialog(
            onDismissRequest = { showPermissions = false },
            title = { Text(tr("Who can reply")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        blocked -> Text(tr("Replies to this post are disabled."))
                        !restricted -> Text(tr("Everybody can reply to this post."))
                        rules.isNullOrEmpty() -> Text(tr("Reply settings are chosen by the author of the thread"))
                        else -> {
                            Text(tr("Some people can reply"))
                            rules.forEach { element ->
                                val rule = element as? JsonObject ?: return@forEach
                                val kind = (rule["\$type"] as? JsonPrimitive)?.contentOrNull
                                val description = when (kind?.substringAfterLast('#')) {
                                    "followerRule" -> tr("People who follow the author")
                                    "followingRule" -> tr("People followed by the author")
                                    "mentionRule" -> tr("People mentioned in the post")
                                    "listRule" -> {
                                        val uri = (rule["list"] as? JsonPrimitive)?.contentOrNull
                                        post.threadgate?.lists?.firstOrNull { it.uri == uri }?.name
                                    }
                                    else -> null
                                }
                                if (description != null) Text(description)
                            }
                        }
                    }
                    if (post.viewer?.embeddingDisabled == true) {
                        Text(tr("No one but the author can quote this post."))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPermissions = false }) { Text(tr("Done")) }
            },
        )
    }
}

@Composable
private fun AnchorStatCount(count: Int, message: String, onClick: (() -> Unit)? = null) {
    val palette = LocalAlfPalette.current
    val locale = LocalI18n.current.locale
    val translation = tr(message, mapOf("0" to count))
    val formatted = if (translation == message) MessageFormat(message, locale).format(arrayOf(count)) else translation
    val fullCount = NumberFormat.getIntegerInstance(locale).format(count)
    val numberStart = formatted.indexOf(fullCount)
    Text(
        text = buildAnnotatedString {
            if (numberStart < 0) {
                append(formatted)
            } else {
                append(formatted.substring(0, numberStart))
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = palette.contrast1000.toComposeColor())) {
                    append(formatCompactCount(count, locale))
                }
                append(formatted.substring(numberStart + fullCount.length))
            }
        },
        style = MaterialTheme.typography.bodyMedium,
        color = palette.contrast700.toComposeColor(),
        modifier = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick),
    )
}
