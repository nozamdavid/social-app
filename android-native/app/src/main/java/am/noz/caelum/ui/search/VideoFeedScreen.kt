package am.noz.caelum.ui.search

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import am.noz.caelum.core.i18n.tr
import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.model.PostView
import am.noz.caelum.core.moderation.ModerationContext
import am.noz.caelum.core.moderation.ModerationEngine
import am.noz.caelum.core.moderation.ModerationOpts
import am.noz.caelum.core.richtext.RichTextFacet
import am.noz.caelum.core.storage.AppSettingsSnapshot
import am.noz.caelum.ui.components.CaelumAvatar
import am.noz.caelum.ui.components.LinkNavigationHelper
import am.noz.caelum.ui.components.LocalAppContainer
import am.noz.caelum.ui.components.RichTextView
import am.noz.caelum.ui.components.rememberHaptics
import am.noz.caelum.ui.navigation.AppRoute
import am.noz.caelum.ui.post.formatCompactCount
import am.noz.caelum.ui.theme.LocalAlfPalette
import am.noz.caelum.ui.theme.toComposeColor
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

private const val DEFAULT_VIDEO_FEED_URI =
    "at://did:plc:z72i7hdynmk6r22z27h6tvur/app.bsky.feed.generator/thevids"

private data class VideoReelItem(
    val post: PostView,
    val playlist: String,
    val thumbnail: String?,
    val alt: String?,
)

private fun canPlayInVideoFeed(post: PostView, opts: ModerationOpts): Boolean {
    val decision = ModerationEngine.moderatePost(post, opts)
    val media = decision.ui(ModerationContext.contentMedia)
    return !decision.ui(ModerationContext.contentList).filter && !(media.blur && media.noOverride)
}

private fun extractVideoFromEmbed(embed: JsonObject?): Triple<String, String?, String?>? {
    if (embed == null) return null
    val type = (embed["\$type"] as? JsonPrimitive)?.content.orEmpty()
    val videoObj = when (type) {
        "app.bsky.embed.video#view" -> embed
        "app.bsky.embed.recordWithMedia#view" -> {
            val media = embed["media"] as? JsonObject
            val mediaType = (media?.get("\$type") as? JsonPrimitive)?.content
            if (mediaType == "app.bsky.embed.video#view") media else null
        }
        else -> null
    } ?: return null

    val playlist = (videoObj["playlist"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
        ?: return null
    val thumbnail = (videoObj["thumbnail"] as? JsonPrimitive)?.content
    val alt = (videoObj["alt"] as? JsonPrimitive)?.content
    return Triple(playlist, thumbnail, alt)
}

@OptIn(UnstableApi::class)
@Composable
fun VideoFeedScreen(
    sourceUri: String,
    title: String,
    onBack: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
    onOpenComposer: (replyTo: PostView?, quote: PostView?) -> Unit,
) {
    val container = LocalAppContainer.current
    val palette = LocalAlfPalette.current
    val context = LocalContext.current
    val session by container.sessionManager.state.collectAsState()
    val settings by container.preferences.settingsFlow.collectAsState(initial = AppSettingsSnapshot())
    val shadows by container.postShadowStore.shadows.collectAsState()
    val muted by container.videoPlayerPool.muted.collectAsState()
    val haptics = rememberHaptics(settings.disableHaptics)
    val scope = rememberCoroutineScope()

    var fetchedVideos by remember(sourceUri) { mutableStateOf<List<VideoReelItem>>(emptyList()) }
    val videos = remember(fetchedVideos, session.moderationOpts) {
        fetchedVideos.filter { canPlayInVideoFeed(it.post, session.moderationOpts) }
    }
    var nextCursor by remember(sourceUri) { mutableStateOf<String?>(null) }
    var isLoading by remember(sourceUri) { mutableStateOf(true) }
    var isLoadingMore by remember(sourceUri) { mutableStateOf(false) }
    var errorMsg by remember(sourceUri) { mutableStateOf<String?>(null) }
    var useSearchFallback by remember(sourceUri) { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            container.videoPlayerPool.pauseActive()
        }
    }

    suspend fun fetchBatch(reset: Boolean) {
        if (!reset && (isLoadingMore || nextCursor.isNullOrBlank())) return
        if (reset) {
            isLoading = videos.isEmpty()
            errorMsg = null
        } else {
            isLoadingMore = true
        }

        val collected = if (reset) mutableListOf() else videos.toMutableList()
        var cursor: String? = if (reset) null else nextCursor
        var pagesFetched = 0

        runCatching {
            val cleanSource = sourceUri.trim()
            do {
                val (posts, fetchedCursor) = when {
                    useSearchFallback -> {
                        val resp = container.api.searchPosts(
                            query = cleanSource.ifBlank { "video" },
                            sort = "top",
                            cursor = cursor,
                            limit = 30,
                        )
                        resp.posts to resp.cursor
                    }
                    cleanSource.isEmpty() -> {
                        val resp = runCatching {
                            container.api.getFeed(DEFAULT_VIDEO_FEED_URI, cursor = cursor, limit = 30)
                        }.getOrElse {
                            useSearchFallback = true
                            val fallback = container.api.searchPosts(
                                query = "video",
                                sort = "top",
                                cursor = cursor,
                                limit = 30,
                            )
                            return@getOrElse am.noz.caelum.core.model.FeedResponse(
                                cursor = fallback.cursor,
                                feed = fallback.posts.map { am.noz.caelum.core.model.FeedViewPost(post = it) },
                            )
                        }
                        resp.feed.map { it.post } to resp.cursor
                    }
                    cleanSource.contains("/app.bsky.feed.generator/") -> {
                        val resp = container.api.getFeed(cleanSource, cursor = cursor, limit = 30)
                        resp.feed.map { it.post } to resp.cursor
                    }
                    cleanSource.contains("/app.bsky.graph.list/") -> {
                        val resp = container.api.getListFeed(cleanSource, cursor = cursor, limit = 30)
                        resp.feed.map { it.post } to resp.cursor
                    }
                    cleanSource.startsWith("did:") ||
                        (!cleanSource.startsWith("at://") && cleanSource.contains('.') && !cleanSource.contains(' ')) -> {
                        val resp = container.api.getAuthorFeed(
                            actor = cleanSource,
                            filter = "posts_with_media",
                            cursor = cursor,
                            limit = 30,
                        )
                        resp.feed.map { it.post } to resp.cursor
                    }
                    else -> {
                        val resp = container.api.searchPosts(
                            query = cleanSource,
                            sort = "top",
                            cursor = cursor,
                            limit = 30,
                        )
                        resp.posts to resp.cursor
                    }
                }

                for (post in posts) {
                    val extracted = extractVideoFromEmbed(post.embed) ?: continue
                    if (collected.none { it.post.uri == post.uri }) {
                        collected.add(
                            VideoReelItem(
                                post = post,
                                playlist = extracted.first,
                                thumbnail = extracted.second,
                                alt = extracted.third,
                            )
                        )
                    }
                }
                cursor = fetchedCursor
                pagesFetched++
            } while (
                collected.count { canPlayInVideoFeed(it.post, session.moderationOpts) } < (if (reset) 3 else videos.size + 3) &&
                    !cursor.isNullOrBlank() &&
                    pagesFetched < 3
            )
        }.onSuccess {
            fetchedVideos = collected
            nextCursor = cursor
        }.onFailure { ex ->
            if (ex is kotlinx.coroutines.CancellationException) throw ex
            if (collected.isEmpty()) {
                errorMsg = ex.message ?: "Unable to load videos"
            }
        }

        isLoading = false
        isLoadingMore = false
    }

    LaunchedEffect(sourceUri, session.currentDid) {
        fetchBatch(reset = true)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when {
            isLoading && videos.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
            errorMsg != null && videos.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = errorMsg.orEmpty(),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { scope.launch { fetchBatch(reset = true) } }) {
                        Text(tr("Try again"))
                    }
                }
            }
            videos.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = tr("No videos found"),
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            else -> {
                val pagerState = rememberPagerState(pageCount = { videos.size })

                LaunchedEffect(pagerState.currentPage, videos.size, nextCursor) {
                    if (pagerState.currentPage >= videos.size - 3 && !nextCursor.isNullOrBlank()) {
                        fetchBatch(reset = false)
                    }
                }

                VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    key = { page -> videos[page].post.uri },
                ) { page ->
                    val item = videos[page]
                    val isCurrentPage = pagerState.currentPage == page
                    val effectivePost = remember(item.post, shadows) {
                        container.postShadowStore.applyToPost(item.post) ?: item.post
                    }
                    var isPaused by remember(item.playlist, isCurrentPage) { mutableStateOf(false) }
                    var captionExpanded by remember(item.post.uri) { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                if (isCurrentPage) {
                                    isPaused = !isPaused
                                }
                            },
                    ) {
                        // Thumbnail layer underneath video
                        if (!item.thumbnail.isNullOrBlank()) {
                            AsyncImage(
                                model = item.thumbnail,
                                contentDescription = item.alt ?: tr("Video thumbnail"),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        if (isCurrentPage) {
                            val player = remember(item.playlist) {
                                container.videoPlayerPool.acquirePlayer(
                                    url = item.playlist,
                                    loop = true,
                                    autoPlay = !isPaused,
                                )
                            }
                            LaunchedEffect(item.playlist, isPaused) {
                                player.playWhenReady = !isPaused
                            }
                            DisposableEffect(item.playlist) {
                                onDispose {
                                    container.videoPlayerPool.releasePlayerIfUrl(item.playlist)
                                }
                            }
                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        useController = false
                                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                        layoutParams = FrameLayout.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                        )
                                        this.player = player
                                    }
                                },
                                update = { pv -> pv.player = player },
                                onRelease = { pv -> pv.player = null },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        if (isPaused) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.55f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = tr("Play"),
                                    tint = Color.White,
                                    modifier = Modifier.size(38.dp),
                                )
                            }
                        }

                        // Bottom gradient scrim + author/caption/actions overlay
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.72f),
                                            Color.Black.copy(alpha = 0.9f),
                                        )
                                    )
                                )
                                .navigationBarsPadding()
                                .padding(start = 16.dp, end = 12.dp, top = 48.dp, bottom = 20.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                // Author info + caption
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    val handleForNav = effectivePost.author.handle.ifBlank { effectivePost.author.did }
                                    Row(
                                        modifier = Modifier.clickable {
                                            onNavigate(AppRoute.Profile(handleForNav))
                                        },
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        CaelumAvatar(
                                            url = effectivePost.author.avatar,
                                            contentDescription = effectivePost.author.displayName ?: handleForNav,
                                            size = 38.dp,
                                        )
                                        Column {
                                            Text(
                                                text = effectivePost.author.displayName?.takeIf { it.isNotBlank() }
                                                    ?: "@$handleForNav",
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = "@$handleForNav",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White.copy(alpha = 0.78f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }

                                    val text = (effectivePost.record["text"] as? JsonPrimitive)?.content.orEmpty()
                                    val facets: List<RichTextFacet> = remember(effectivePost.record["facets"]) {
                                        (effectivePost.record["facets"] as? JsonArray)?.mapNotNull {
                                            runCatching { AtProtoJson.decodeFromJsonElement<RichTextFacet>(it) }.getOrNull()
                                        }.orEmpty()
                                    }
                                    if (text.isNotBlank()) {
                                        RichTextView(
                                            text = text,
                                            facets = facets,
                                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                                            maxLines = if (captionExpanded) 10 else 3,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.clickable { captionExpanded = !captionExpanded },
                                            onMentionClick = { didOrHandle -> onNavigate(AppRoute.Profile(didOrHandle)) },
                                            onTagClick = { tag -> onNavigate(AppRoute.Hashtag(tag)) },
                                            onLinkClick = { _, uri ->
                                                LinkNavigationHelper.openUrl(
                                                    context = context,
                                                    rawUrl = uri,
                                                    useLinkRedirects = settings.infrastructure.useLinkRedirects,
                                                    openBskyLinksInCaelum = settings.openBskyLinksInCaelum,
                                                    onInternalRoute = onNavigate,
                                                )
                                            },
                                        )
                                    }

                                    if (!item.alt.isNullOrBlank()) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color.White.copy(alpha = 0.16f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                        ) {
                                            Text(
                                                text = "ALT: ${item.alt}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White.copy(alpha = 0.9f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }

                                // Right-side vertical interaction rail
                                val isLiked = !effectivePost.viewer?.like.isNullOrBlank()
                                val isReposted = !effectivePost.viewer?.repost.isNullOrBlank()
                                val isBookmarked = effectivePost.viewer?.bookmarked == true
                                val handleForShare = effectivePost.author.handle.ifBlank { effectivePost.author.did }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    VideoReelActionButton(
                                        icon = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                        label = effectivePost.likeCount?.takeIf { it > 0 }?.let { formatCompactCount(it) },
                                        tint = if (isLiked) palette.like.toComposeColor() else Color.White,
                                        contentDescription = tr("Like"),
                                        onClick = {
                                            haptics.toggle(!isLiked)
                                            scope.launch { container.postController.toggleLike(effectivePost) }
                                        },
                                    )

                                    VideoReelActionButton(
                                        icon = Icons.Filled.ChatBubbleOutline,
                                        label = effectivePost.replyCount?.takeIf { it > 0 }?.let { formatCompactCount(it) },
                                        tint = Color.White,
                                        contentDescription = tr("Reply"),
                                        onClick = {
                                            haptics.tap()
                                            onOpenComposer(effectivePost, null)
                                        },
                                    )

                                    VideoReelActionButton(
                                        icon = Icons.Filled.Repeat,
                                        label = effectivePost.repostCount?.takeIf { it > 0 }?.let { formatCompactCount(it) },
                                        tint = if (isReposted) palette.positive500.toComposeColor() else Color.White,
                                        contentDescription = tr("Repost"),
                                        onClick = {
                                            haptics.confirm()
                                            scope.launch { container.postController.toggleRepost(effectivePost) }
                                        },
                                    )

                                    VideoReelActionButton(
                                        icon = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                        label = null,
                                        tint = if (isBookmarked) palette.primary400.toComposeColor() else Color.White,
                                        contentDescription = tr("Bookmark"),
                                        onClick = {
                                            haptics.toggle(!isBookmarked)
                                            scope.launch { container.postController.toggleBookmark(effectivePost) }
                                        },
                                    )

                                    VideoReelActionButton(
                                        icon = Icons.Filled.Share,
                                        label = null,
                                        tint = Color.White,
                                        contentDescription = tr("Share"),
                                        onClick = {
                                            haptics.tap()
                                            LinkNavigationHelper.shareText(
                                                context,
                                                LinkNavigationHelper.postCanonicalWebUrl(handleForShare, effectivePost.uri),
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Top overlay bar (always visible)
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.65f),
                            Color.Transparent,
                        )
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = tr("Back"),
                        tint = Color.White,
                    )
                }
                Text(
                    text = title.ifBlank { tr("Videos") },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            IconButton(
                onClick = {
                    haptics.tap()
                    container.videoPlayerPool.toggleMuted()
                },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
            ) {
                Icon(
                    imageVector = if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = if (muted) tr("Unmute") else tr("Mute"),
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun VideoReelActionButton(
    icon: ImageVector,
    label: String?,
    tint: Color,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
        }
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
            )
        }
    }
}
