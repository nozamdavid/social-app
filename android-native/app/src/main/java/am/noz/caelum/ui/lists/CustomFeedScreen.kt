package am.noz.caelum.ui.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import am.noz.caelum.core.i18n.LocalI18n
import am.noz.caelum.core.i18n.tr
import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.model.GeneratorView
import am.noz.caelum.core.model.GeneratorViewerState
import am.noz.caelum.core.model.PostView
import am.noz.caelum.core.model.StrongRef
import am.noz.caelum.core.richtext.RichTextFacet
import am.noz.caelum.core.session.ScreenStateCache
import am.noz.caelum.ui.components.AvatarShape
import am.noz.caelum.ui.components.CaelumAvatar
import am.noz.caelum.ui.components.ComposePostButton
import am.noz.caelum.ui.components.LinkNavigationHelper
import am.noz.caelum.ui.components.LocalAppContainer
import am.noz.caelum.ui.components.LocalScreenStateCache
import am.noz.caelum.ui.components.RichTextView
import am.noz.caelum.ui.components.ShellIcons
import am.noz.caelum.ui.components.rememberHaptics
import am.noz.caelum.ui.navigation.AppRoute
import am.noz.caelum.ui.openin.OpenInSubject
import am.noz.caelum.ui.post.EmbedImageItem
import am.noz.caelum.ui.post.formatCompactCount
import am.noz.caelum.ui.theme.LocalAlfPalette
import am.noz.caelum.ui.theme.toComposeColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomFeedScreen(
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
    val i18n = LocalI18n.current
    val context = LocalContext.current
    val session by container.sessionManager.state.collectAsState()
    val settings by container.preferences.settingsFlow.collectAsState(initial = am.noz.caelum.core.storage.AppSettingsSnapshot())
    val haptics = rememberHaptics(settings.disableHaptics)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val cacheKey = "$actorHandleOrDid/$rkey"
    val cachedFeed = remember(cacheKey) { screenCache.customFeeds[cacheKey] }

    var resolvedFeedUri by remember(cacheKey) { mutableStateOf(cachedFeed?.resolvedFeedUri) }
    var generator by remember(cacheKey) { mutableStateOf(cachedFeed?.generator) }
    var contentMode by remember(cacheKey) { mutableStateOf(cachedFeed?.contentMode) }
    var isOnline by remember(cacheKey) { mutableStateOf(cachedFeed?.isOnline ?: true) }
    var isValid by remember(cacheKey) { mutableStateOf(cachedFeed?.isValid ?: true) }

    var posts by remember(cacheKey) { mutableStateOf(cachedFeed?.posts.orEmpty()) }
    var cursor by remember(cacheKey) { mutableStateOf(cachedFeed?.cursor) }
    var isLoadingHeader by remember(cacheKey) { mutableStateOf(cachedFeed?.generator == null) }
    var isLoadingPosts by remember(cacheKey) { mutableStateOf(cachedFeed == null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var loadingFirstPage by remember { mutableStateOf(false) }
    var feedGeneration by remember { mutableIntStateOf(0) }
    var errorMsg by remember(cacheKey) { mutableStateOf<String?>(null) }

    var showFeedInfo by remember(cacheKey) { mutableStateOf(false) }
    var optimisticSavedFeeds by remember(session.pinnedFeeds) { mutableStateOf(session.pinnedFeeds) }
    var likeInFlight by remember { mutableStateOf(false) }
    var savedFeedsInFlight by remember { mutableStateOf(false) }

    val listState = screenCache.rememberCachedLazyListState("custom_feed_${actorHandleOrDid}_$rkey")

    fun persistCustomFeedCache() {
        screenCache.customFeeds[cacheKey] = ScreenStateCache.CachedCustomFeedScreen(
            resolvedFeedUri = resolvedFeedUri,
            generator = generator,
            contentMode = contentMode,
            isOnline = isOnline,
            isValid = isValid,
            posts = posts,
            cursor = cursor,
        )
    }

    suspend fun loadAll(refresh: Boolean = false) {
        if (loadingFirstPage) return
        if (!refresh && generator != null && posts.isNotEmpty()) return
        loadingFirstPage = true
        feedGeneration++
        isLoadingMore = false
        if (refresh) isRefreshing = true else {
            isLoadingHeader = generator == null
            isLoadingPosts = posts.isEmpty()
        }
        errorMsg = null

        runCatching {
            val did = resolveActorDid(container, actorHandleOrDid)
            val uri = "at://$did/app.bsky.feed.generator/$rkey"
            resolvedFeedUri = uri

            val genJson = container.api.getFeedGenerator(uri)
            val viewObj = genJson["view"] as? JsonObject
            if (viewObj != null) {
                generator = AtProtoJson.decodeFromJsonElement<GeneratorView>(viewObj)
                contentMode = viewObj["contentMode"]?.jsonPrimitive?.contentOrNull
            }
            isOnline = (genJson["isOnline"] as? JsonPrimitive)?.booleanOrNull ?: true
            isValid = (genJson["isValid"] as? JsonPrimitive)?.booleanOrNull ?: true

            val feedResp = container.api.getFeed(uri, cursor = null, limit = 30)
            posts = feedResp.feed
            cursor = feedResp.cursor
        }.onSuccess {
            persistCustomFeedCache()
        }.also { result ->
            loadingFirstPage = false
            isLoadingHeader = false
            isLoadingPosts = false
            isRefreshing = false
            val failure = result.exceptionOrNull()
            if (failure is CancellationException) throw failure
            if (failure != null && posts.isEmpty()) errorMsg = i18n.translate("Failed to load feeds")
        }
    }

    suspend fun loadMorePosts() {
        val uri = resolvedFeedUri ?: return
        val cur = cursor?.takeIf { it.isNotBlank() } ?: return
        if (isLoadingMore || loadingFirstPage) return
        val generation = feedGeneration
        isLoadingMore = true
        runCatching {
            container.api.getFeed(uri, cursor = cur, limit = 30)
        }.onSuccess { resp ->
            if (generation != feedGeneration) return
            posts = (posts + resp.feed).distinctBy { it.post.uri + ":" + (it.reason?.toString() ?: "") }
            cursor = resp.cursor
            persistCustomFeedCache()
        }.also { result ->
            if (generation == feedGeneration) isLoadingMore = false
            if (result.exceptionOrNull() is CancellationException) result.getOrThrow()
        }
    }

    LaunchedEffect(actorHandleOrDid, rkey) {
        loadAll()
    }

    fun toggleLike() {
        val did = session.currentDid ?: return
        val activation = session.activation
        val gen = generator ?: return
        if (likeInFlight) return
        likeInFlight = true
        val prevGen = gen
        val existingLike = gen.viewer?.like
        val currentlyLiked = !existingLike.isNullOrBlank()
        haptics.toggle(!currentlyLiked)

        val nextCount = ((gen.likeCount ?: 0) + if (currentlyLiked) -1 else 1).coerceAtLeast(0)
        generator = gen.copy(
            likeCount = nextCount,
            viewer = (gen.viewer ?: GeneratorViewerState()).copy(
                like = if (currentlyLiked) null else "pending://like",
            ),
        )

        scope.launch {
            val result = runCatching {
                withContext(container.sessionManager.bindAccount(did, activation)) {
                    if (currentlyLiked) {
                        container.api.deleteRecord(did, "app.bsky.feed.like", existingLike.orEmpty().substringAfterLast('/'))
                    } else {
                        val created = container.api.likePost(did, StrongRef(gen.uri, gen.cid))
                        generator = generator?.copy(
                            viewer = (generator?.viewer ?: GeneratorViewerState()).copy(like = created.uri),
                        )
                    }
                }
            }
            likeInFlight = false
            result.onSuccess { persistCustomFeedCache() }.onFailure { ex ->
                generator = prevGen
                if (ex is CancellationException) throw ex
                snackbarHostState.showSnackbar(
                    i18n.translate("There was an issue contacting the server, please check your internet connection and try again."),
                )
            }
        }
    }

    fun toggleSavedOrPinned(isPinAction: Boolean) {
        val did = session.currentDid ?: return
        val activation = session.activation
        val uri = resolvedFeedUri ?: generator?.uri ?: return
        if (savedFeedsInFlight) return
        savedFeedsInFlight = true
        haptics.confirm()
        scope.launch {
            val result = runCatching {
                withContext(container.sessionManager.bindAccount(did, activation)) {
                    optimisticSavedFeeds = if (isPinAction) {
                        SavedFeedsHelper.togglePin(container, did, optimisticSavedFeeds, "feed", uri, generator)
                    } else {
                        SavedFeedsHelper.toggleSave(container, did, optimisticSavedFeeds, "feed", uri, generator)
                    }
                }
            }
            savedFeedsInFlight = false
            result.onFailure { ex ->
                if (ex is CancellationException) throw ex
                snackbarHostState.showSnackbar(i18n.translate("Failed to update feeds"))
            }
        }
    }

    val gen = generator
    val feedUri = resolvedFeedUri ?: gen?.uri
    val savedPref = feedUri?.let { uri -> optimisticSavedFeeds.firstOrNull { it.value == uri } }
    val isSaved = savedPref != null
    val isPinned = savedPref?.pinned == true
    val creatorHandle = gen?.creator?.handle?.ifBlank { gen.creator.did } ?: actorHandleOrDid

    fun shareFeed() {
        LinkNavigationHelper.shareText(
            context,
            "https://bsky.app/profile/$creatorHandle/feed/$rkey",
            gen?.displayName,
        )
    }

    Scaffold(
        topBar = {
            CustomFeedHeader(
                generator = gen,
                hasSession = session.hasSession,
                isSaved = isSaved,
                isPinned = isPinned,
                pinEnabled = !savedFeedsInFlight,
                onBack = onBack,
                onInfo = {
                    haptics.tap()
                    showFeedInfo = true
                },
                onTogglePin = { toggleSavedOrPinned(isPinAction = true) },
                onToggleSave = { toggleSavedOrPinned(isPinAction = false) },
            )
        },
        floatingActionButton = {
            if (session.hasSession && gen != null) {
                ComposePostButton(
                    disableHaptics = settings.disableHaptics,
                    onClick = { onOpenComposer(null, null) },
                    modifier = Modifier.testTag("custom_feed_compose_fab"),
                )
            }
        },
        snackbarHost = {
            if (!showFeedInfo) SnackbarHost(snackbarHostState)
        },
        containerColor = palette.contrast0.toComposeColor(),
    ) { padding ->
        val contentModifier = Modifier.padding(padding).consumeWindowInsets(padding)
        if (isLoadingHeader && gen == null) {
            Box(
                modifier = contentModifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            FeedPostListContent(
                items = posts,
                isLoading = isLoadingPosts,
                isRefreshing = isRefreshing,
                isLoadingMore = isLoadingMore,
                error = errorMsg,
                hasMore = !loadingFirstPage && !cursor.isNullOrBlank(),
                onRefresh = { scope.launch { loadAll(refresh = true) } },
                onLoadMore = { scope.launch { loadMorePosts() } },
                onNavigate = onNavigate,
                onOpenComposer = onOpenComposer,
                onOpenEditPost = onOpenEditPost,
                onOpenIn = onOpenIn,
                onOpenReport = onOpenReport,
                onOpenLightbox = onOpenLightbox,
                onOpenVideoLightbox = onOpenVideoLightbox,
                emptyMessage = tr("This feed is empty."),
                listState = listState,
                modifier = contentModifier,
            )
        }
    }

    if (showFeedInfo && gen != null) {
        CustomFeedInfoSheet(
            generator = gen,
            contentMode = contentMode,
            isOnline = isOnline,
            isValid = isValid,
            hasSession = session.hasSession,
            isPinned = isPinned,
            isSaved = isSaved,
            canUpdateLike = !likeInFlight,
            canUpdateSavedFeeds = !savedFeedsInFlight,
            useLinkRedirects = settings.infrastructure.useLinkRedirects,
            openBskyLinksInCaelum = settings.openBskyLinksInCaelum,
            snackbarHostState = snackbarHostState,
            onDismiss = { showFeedInfo = false },
            onNavigate = onNavigate,
            onLikesCountClick = { onNavigate(AppRoute.CustomFeedLikedBy(creatorHandle, rkey)) },
            onToggleLike = ::toggleLike,
            onTogglePin = { toggleSavedOrPinned(isPinAction = true) },
            onToggleSave = { toggleSavedOrPinned(isPinAction = false) },
            onShare = ::shareFeed,
            onOpenIn = { onOpenIn(OpenInSubject.Feed(gen.uri)) },
        )
    }
}

@Composable
private fun CustomFeedHeader(
    generator: GeneratorView?,
    hasSession: Boolean,
    isSaved: Boolean,
    isPinned: Boolean,
    pinEnabled: Boolean,
    onBack: () -> Unit,
    onInfo: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleSave: () -> Unit,
) {
    val palette = LocalAlfPalette.current
    val textColor = palette.contrast1000.toComposeColor()
    val secondaryTextColor = palette.contrast800.toComposeColor()
    val muted = palette.contrast700.toComposeColor()
    val locale = LocalI18n.current.locale
    val mirrorArrow = LocalLayoutDirection.current == LayoutDirection.Rtl
    var pinMenuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.contrast0.toComposeColor())
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(start = 5.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = ShellIcons.Back,
                    contentDescription = tr("Go back"),
                    tint = muted,
                    modifier = Modifier.graphicsLayer { scaleX = if (mirrorArrow) -1f else 1f },
                )
            }
            if (generator == null) {
                Text(
                    text = tr("Feed"),
                    style = MaterialTheme.typography.titleMedium,
                    color = textColor,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(role = Role.Button, onClickLabel = tr("Open feed info screen"), onClick = onInfo)
                        .padding(end = 10.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!generator.avatar.isNullOrBlank()) {
                        CaelumAvatar(
                            url = generator.avatar,
                            contentDescription = null,
                            size = 36.dp,
                            shapeType = AvatarShape.RoundedSquare,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = generator.displayName,
                            color = textColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            lineHeight = 18.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "@${generator.creator.handle.ifBlank { generator.creator.did }}",
                                color = secondaryTextColor,
                                fontSize = 13.sp,
                                lineHeight = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            val likeCount = generator.likeCount ?: 0
                            if (likeCount > 0) {
                                Icon(
                                    imageVector = ShellIcons.Like,
                                    contentDescription = null,
                                    tint = if (generator.viewer?.like.isNullOrBlank()) muted else palette.like.toComposeColor(),
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = formatCompactCount(likeCount, locale),
                                    color = secondaryTextColor,
                                    fontSize = 13.sp,
                                    lineHeight = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    Icon(
                        imageVector = ShellIcons.More,
                        contentDescription = null,
                        tint = secondaryTextColor,
                        modifier = Modifier.size(18.dp),
                    )
                }

                if (hasSession) {
                    Box {
                        IconButton(
                            enabled = pinEnabled,
                            onClick = { if (isPinned) pinMenuOpen = true else onTogglePin() },
                            modifier = Modifier.testTag("custom_feed_pin"),
                        ) {
                            Icon(
                                imageVector = if (isPinned) ShellIcons.PinSelected else ShellIcons.Pin,
                                contentDescription = if (isPinned) tr("Open feed options menu") else tr("Pin to Home"),
                                tint = if (isPinned) palette.primary500.toComposeColor() else muted,
                            )
                        }
                        DropdownMenu(
                            expanded = pinMenuOpen,
                            onDismissRequest = { pinMenuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(tr("Unpin from home")) },
                                onClick = {
                                    pinMenuOpen = false
                                    onTogglePin()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(if (isSaved) tr("Remove from my feeds") else tr("Save to my feeds")) },
                                onClick = {
                                    pinMenuOpen = false
                                    onToggleSave()
                                },
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = Dp.Hairline)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomFeedInfoSheet(
    generator: GeneratorView,
    contentMode: String?,
    isOnline: Boolean,
    isValid: Boolean,
    hasSession: Boolean,
    isPinned: Boolean,
    isSaved: Boolean,
    canUpdateLike: Boolean,
    canUpdateSavedFeeds: Boolean,
    useLinkRedirects: Boolean,
    openBskyLinksInCaelum: Boolean,
    snackbarHostState: SnackbarHostState,
    onDismiss: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
    onLikesCountClick: () -> Unit,
    onToggleLike: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleSave: () -> Unit,
    onShare: () -> Unit,
    onOpenIn: () -> Unit,
) {
    val palette = LocalAlfPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var closing by remember { mutableStateOf(false) }
    val sheetLabel = tr("Feed menu")
    val creatorHandle = generator.creator.handle.ifBlank { generator.creator.did }
    val isLiked = !generator.viewer?.like.isNullOrBlank()
    val likeCount = generator.likeCount ?: 0
    val facets = remember(generator.descriptionFacets) {
        generator.descriptionFacets.mapNotNull {
            runCatching { AtProtoJson.decodeFromJsonElement<RichTextFacet>(it) }.getOrNull()
        }
    }

    fun closeThen(action: () -> Unit) {
        if (closing) return
        closing = true
        scope.launch {
            sheetState.hide()
            onDismiss()
            action()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.contrast0.toComposeColor(),
        modifier = Modifier.semantics { paneTitle = sheetLabel },
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CaelumAvatar(
                        url = generator.avatar,
                        contentDescription = null,
                        size = 48.dp,
                        shapeType = AvatarShape.RoundedSquare,
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clickable(role = Role.Button, onClickLabel = tr("View profile")) {
                                closeThen { onNavigate(AppRoute.Profile(creatorHandle)) }
                            },
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = generator.displayName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "@$creatorHandle",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.contrast700.toComposeColor(),
                            textDecoration = TextDecoration.Underline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = onShare) {
                        Icon(ShellIcons.Share, contentDescription = tr("Share this feed"))
                    }
                }

                if (!generator.description.isNullOrBlank()) {
                    RichTextView(
                        text = generator.description,
                        facets = facets,
                        style = MaterialTheme.typography.bodyLarge.copy(color = palette.contrast1000.toComposeColor()),
                        onMentionClick = { closeThen { onNavigate(AppRoute.Profile(it)) } },
                        onTagClick = { closeThen { onNavigate(AppRoute.Hashtag(it)) } },
                        onLinkClick = { _, uri ->
                            closeThen {
                                LinkNavigationHelper.openUrl(context, uri, useLinkRedirects, openBskyLinksInCaelum, onNavigate)
                            }
                        },
                    )
                }

                if (likeCount > 0) {
                    TextButton(onClick = { closeThen(onLikesCountClick) }) {
                        Text(
                            tr(
                                "Liked by {likeCount, plural, one {# user} other {# users}}",
                                mapOf("likeCount" to likeCount),
                            ),
                        )
                    }
                }

                if (hasSession) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onToggleLike,
                            enabled = canUpdateLike,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (isLiked) tr("Unlike") else tr("Like"))
                        }
                        Button(
                            onClick = onTogglePin,
                            enabled = canUpdateSavedFeeds,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (isPinned) tr("Unpin feed") else tr("Pin feed"))
                        }
                    }
                    OutlinedButton(
                        onClick = onToggleSave,
                        enabled = canUpdateSavedFeeds,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (isSaved) tr("Remove from my feeds") else tr("Save to my feeds"))
                    }
                }

                HorizontalDivider(color = palette.contrast100.toComposeColor())

                val modeLabel = when (contentMode) {
                    "app.bsky.feed.defs#contentModeVideo" -> tr("Video feed")
                    "app.bsky.feed.defs#contentModeUnspecified", null -> tr("Standard posts")
                    else -> contentMode.substringAfterLast('#')
                }
                val status = when {
                    !isValid -> tr("Invalid record")
                    !isOnline -> tr("Offline")
                    else -> tr("Online")
                }
                DetailMetaRow(tr("Content mode"), modeLabel)
                DetailMetaRow(tr("Status"), status)
                DetailMetaRow(tr("Interactions"), if (generator.acceptsInteractions == true) tr("Supported") else tr("Standard"))
                DetailMetaRow(tr("AT URI"), generator.uri)

                OutlinedButton(
                    onClick = { closeThen(onOpenIn) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Open in ATProto app"))
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun DetailMetaRow(label: String, value: String) {
    val palette = LocalAlfPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.4f),
            style = MaterialTheme.typography.bodySmall,
            color = palette.contrast700.toComposeColor(),
        )
        Text(
            text = value,
            modifier = Modifier.weight(0.6f),
            style = MaterialTheme.typography.bodySmall,
            color = palette.contrast950.toComposeColor(),
        )
    }
}
