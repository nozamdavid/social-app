package am.noz.caelum.ui.search

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import am.noz.caelum.core.i18n.tr
import am.noz.caelum.core.model.GeneratorView
import am.noz.caelum.core.model.PostView
import am.noz.caelum.core.model.ProfileView
import am.noz.caelum.core.model.ProfileViewBasic
import am.noz.caelum.core.moderation.ModerationContext
import am.noz.caelum.core.moderation.ModerationEngine
import am.noz.caelum.core.storage.AppSettingsSnapshot
import am.noz.caelum.core.storage.ModerationUserSettings
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

internal data class PostSearchPageState(
    val items: List<PostView> = emptyList(),
    val cursor: String? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val hasLoaded: Boolean = false,
)

internal fun ProfileView.toBasicProfile(): ProfileViewBasic = ProfileViewBasic(
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

internal fun parseFeedRouteFromAtUri(atUri: String): AppRoute {
    val clean = atUri.removePrefix("at://")
    val repo = clean.substringBefore('/')
    val rkey = clean.substringAfterLast('/')
    return AppRoute.CustomFeed(repo, rkey)
}

@Composable
internal fun ProfileResultRow(
    profile: ProfileView,
    followUriOverride: String?,
    hasFollowOverride: Boolean,
    isFollowBusy: Boolean,
    canFollow: Boolean,
    onToggleFollow: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalAlfPalette.current
    val effectiveFollowUri = if (hasFollowOverride) followUriOverride else profile.viewer?.following
    val isFollowing = !effectiveFollowUri.isNullOrBlank()
    val borderColor = palette.contrast100.toComposeColor()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.contrast0.toComposeColor())
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CaelumAvatar(
                url = profile.avatar,
                contentDescription = profile.displayName ?: profile.handle,
                size = 44.dp,
                isLive = profile.status?.isActive == true,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        DisplayNameRow(
                            profile = profile.toBasicProfile(),
                            showHandle = false,
                        )
                        Text(
                            text = "@${profile.handle}",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.contrast600.toComposeColor(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (canFollow) {
                        if (isFollowing) {
                            OutlinedButton(
                                onClick = onToggleFollow,
                                enabled = !isFollowBusy,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = tr("Following"),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        } else {
                            Button(
                                onClick = onToggleFollow,
                                enabled = !isFollowBusy,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = palette.primary500.toComposeColor(),
                                    contentColor = Color.White,
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PersonAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = tr("Follow"),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }

                if (!profile.description.isNullOrBlank()) {
                    Text(
                        text = profile.description.trim(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.contrast900.toComposeColor(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        HorizontalDivider(color = borderColor, thickness = 0.5.dp)
    }
}

@Composable
internal fun FeedGeneratorResultCard(
    feed: GeneratorView,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalAlfPalette.current
    val borderColor = palette.contrast100.toComposeColor()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.contrast0.toComposeColor())
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CaelumAvatar(
                url = feed.avatar,
                contentDescription = feed.displayName,
                size = 44.dp,
                shapeType = AvatarShape.RoundedSquare,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = feed.displayName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = palette.contrast1000.toComposeColor(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    val likes = feed.likeCount ?: 0
                    if (likes > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(palette.contrast50.toComposeColor())
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Favorite,
                                contentDescription = null,
                                tint = palette.contrast600.toComposeColor(),
                                modifier = Modifier.size(11.dp),
                            )
                            Text(
                                text = formatCompactCount(likes),
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.contrast700.toComposeColor(),
                            )
                        }
                    }
                }

                Text(
                    text = tr("Feed by @${feed.creator.handle}"),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.contrast600.toComposeColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (!feed.description.isNullOrBlank()) {
                    Text(
                        text = feed.description.trim(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.contrast900.toComposeColor(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        HorizontalDivider(color = borderColor, thickness = 0.5.dp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchPostsFeedTab(
    pageState: PostSearchPageState,
    emptyMessage: String,
    listState: LazyListState = rememberLazyListState(),
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
    onOpenComposer: (replyTo: PostView?, quote: PostView?) -> Unit,
    onOpenEditPost: (PostView) -> Unit,
    onOpenIn: (OpenInSubject) -> Unit,
    onOpenReport: (PostView) -> Unit,
    onOpenLightbox: (List<EmbedImageItem>, Int) -> Unit,
    onOpenVideoLightbox: (String, String?, String?) -> Unit,
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

    val shouldLoadMore by remember(listState, pageState.items.size, pageState.cursor) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            pageState.items.isNotEmpty() &&
                !pageState.cursor.isNullOrBlank() &&
                lastVisible >= pageState.items.size - 5
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            onLoadMore()
        }
    }

    PullToRefreshBox(
        isRefreshing = pageState.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        when {
            pageState.isLoading && pageState.items.isEmpty() -> {
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
                    Text(
                        text = pageState.error,
                        style = MaterialTheme.typography.bodyLarge,
                        color = palette.contrast800.toComposeColor(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onRefresh) {
                        Text(tr("Try again"))
                    }
                }
            }
            else -> {
                val mutedThreads = modSettings?.mutedThreads.orEmpty()
                val filteredPosts = remember(pageState.items, shadows, session.moderationOpts, mutedThreads) {
                    pageState.items.mapNotNull { post ->
                        val applied = container.postShadowStore.applyToPost(post) ?: return@mapNotNull null
                        if (applied.uri in mutedThreads) return@mapNotNull null
                        val modUi = ModerationEngine.moderatePost(applied, session.moderationOpts)
                            .ui(ModerationContext.contentList)
                        if (modUi.filter) return@mapNotNull null
                        applied
                    }
                }

                if (filteredPosts.isEmpty() && pageState.hasLoaded) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        item {
                            Text(
                                text = emptyMessage,
                                style = MaterialTheme.typography.bodyLarge,
                                color = palette.contrast600.toComposeColor(),
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 84.dp),
                    ) {
                        itemsIndexed(
                            items = filteredPosts,
                            key = { index, post -> "${post.uri}_$index" },
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

                        if (pageState.isLoadingMore) {
                            item(key = "search_footer_loader") {
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
