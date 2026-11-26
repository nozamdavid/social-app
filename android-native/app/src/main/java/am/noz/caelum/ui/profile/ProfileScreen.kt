package am.noz.caelum.ui.profile

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import am.noz.caelum.core.i18n.LocalI18n
import am.noz.caelum.core.i18n.tr
import am.noz.caelum.core.model.ActivitySubscription
import am.noz.caelum.core.model.ActorViewerState
import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.model.FeedViewPost
import am.noz.caelum.core.model.GeneratorView
import am.noz.caelum.core.model.LabelerPrefItem
import am.noz.caelum.core.model.LabelerViewDetailed
import am.noz.caelum.core.model.ListView
import am.noz.caelum.core.model.PostView
import am.noz.caelum.core.model.ProfileViewDetailed
import am.noz.caelum.core.model.StarterPackViewBasic
import am.noz.caelum.core.model.StrongRef
import am.noz.caelum.core.moderation.LabelPreference
import am.noz.caelum.core.moderation.ModerationContext
import am.noz.caelum.core.moderation.ModerationEngine
import am.noz.caelum.core.network.ConvoAvailability
import am.noz.caelum.core.richtext.RichTextEngine
import am.noz.caelum.core.session.ScreenStateCache
import am.noz.caelum.core.storage.AppSettingsSnapshot
import am.noz.caelum.core.storage.ModerationUserSettings
import am.noz.caelum.ui.components.AvatarShape
import am.noz.caelum.ui.components.CaelumAvatar
import am.noz.caelum.ui.components.DisplayNameRow
import am.noz.caelum.ui.components.LinkMismatchWarningDialog
import am.noz.caelum.ui.components.LinkNavigationHelper
import am.noz.caelum.ui.components.rememberAccountCoroutineScope
import am.noz.caelum.ui.components.LocalAppContainer
import am.noz.caelum.ui.components.LocalScreenStateCache
import am.noz.caelum.ui.components.RichTextView
import am.noz.caelum.ui.components.ShellIcons
import am.noz.caelum.ui.components.rememberHaptics
import am.noz.caelum.ui.navigation.AppRoute
import am.noz.caelum.ui.openin.OpenInSubject
import am.noz.caelum.ui.post.EmbedImageItem
import am.noz.caelum.ui.post.PostCard
import am.noz.caelum.ui.post.formatCompactCount
import am.noz.caelum.ui.theme.LocalAlfPalette
import am.noz.caelum.ui.theme.toComposeColor
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.math.roundToInt

private const val MAX_SUBSCRIBED_LABELERS = 20
private const val PIN_REASON = "app.bsky.feed.defs#reasonPin"
private val PinnedPostReason = buildJsonObject { put("\$type", JsonPrimitive(PIN_REASON)) }

private fun FeedViewPost.isProfilePin(): Boolean =
    (reason?.get("\$type") as? JsonPrimitive)?.content == PIN_REASON

private enum class ProfileTab(val title: String) {
    Labels("Labels"),
    Posts("Posts"),
    Replies("Replies"),
    Media("Media"),
    Videos("Videos"),
    Likes("Likes"),
    Feeds("Feeds"),
    Lists("Lists"),
    StarterPacks("Starter Packs"),
}

private data class ProfileTabState(
    val feedPosts: List<FeedViewPost> = emptyList(),
    val feeds: List<GeneratorView> = emptyList(),
    val lists: List<ListView> = emptyList(),
    val starterPacks: List<StarterPackViewBasic> = emptyList(),
    val cursor: String? = null,
    val loadedOnce: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    actorHandleOrDid: String,
    isOwnProfileTab: Boolean,
    onBack: (() -> Unit)?,
    onOpenDrawer: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
    onOpenComposer: (replyTo: PostView?, quote: PostView?) -> Unit,
    onOpenEditPost: (PostView) -> Unit,
    onOpenIn: (OpenInSubject) -> Unit,
    onOpenReport: (PostView) -> Unit,
    onOpenAccountSwitcher: () -> Unit,
    onOpenLightbox: (List<EmbedImageItem>, Int) -> Unit,
    onOpenVideoLightbox: (String, String?, String?) -> Unit,
) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val screenCache = LocalScreenStateCache.current
    val palette = LocalAlfPalette.current
    val i18n = LocalI18n.current
    val density = LocalDensity.current
    val mirrorBackArrow = LocalLayoutDirection.current == LayoutDirection.Rtl
    val session by container.sessionManager.state.collectAsState()
    val settings by container.preferences.settingsFlow.collectAsState(initial = AppSettingsSnapshot())
    val shadows by container.postShadowStore.shadows.collectAsState()
    val haptics = rememberHaptics(settings.disableHaptics)
    val scope = rememberAccountCoroutineScope()

    val modSettingsFlow = remember(session.currentDid) {
        session.currentDid?.let { container.preferences.observeModerationSettings(it) }
    }
    val modSettings = modSettingsFlow?.collectAsState(initial = ModerationUserSettings())?.value
        ?: ModerationUserSettings()

    val targetActor = remember(actorHandleOrDid, isOwnProfileTab, session.currentDid, session.currentAccount) {
        actorHandleOrDid.ifBlank {
            session.currentDid ?: session.currentAccount?.handle.orEmpty()
        }
    }

    val cachedProfile = remember(targetActor, session.currentDid) {
        if (targetActor.isNotBlank()) screenCache.profiles[targetActor] else null
    }

    var profile by remember(targetActor, session.currentDid) { mutableStateOf(cachedProfile?.profile) }
    var isLoadingProfile by remember(targetActor, session.currentDid) { mutableStateOf(cachedProfile == null) }
    var profileError by remember(targetActor, session.currentDid) { mutableStateOf<String?>(null) }

    var labelerView by remember(targetActor, session.currentDid) { mutableStateOf(cachedProfile?.labelerView) }
    var isLoadingLabeler by remember(targetActor, session.currentDid) { mutableStateOf(false) }
    var labelerLikeUri by remember(targetActor, session.currentDid) { mutableStateOf(cachedProfile?.labelerLikeUri) }
    var labelerLikeCount by remember(targetActor, session.currentDid) { mutableIntStateOf(cachedProfile?.labelerLikeCount ?: 0) }

    var showEditDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showBlockConfirm by remember { mutableStateOf(false) }
    var showReportAccountDialog by remember { mutableStateOf(false) }
    var isSubmittingAccountReport by remember { mutableStateOf(false) }
    var isStartingConvo by remember { mutableStateOf(false) }
    var showActivitySubscription by remember(targetActor, session.currentDid) { mutableStateOf(false) }
    var isFollowPending by remember { mutableStateOf(false) }
    var isSubscribePending by remember { mutableStateOf(false) }
    var pendingExternalMismatch by remember { mutableStateOf<Pair<String, String>?>(null) }

    val tabStates = remember(targetActor, session.currentDid) {
        mutableStateMapOf<ProfileTab, ProfileTabState>().apply {
            cachedProfile?.tabs?.forEach { (tabName, cachedTab) ->
                ProfileTab.entries.firstOrNull { it.name == tabName }?.let { tab ->
                    put(
                        tab,
                        ProfileTabState(
                            feedPosts = cachedTab.feedPosts,
                            feeds = cachedTab.feeds,
                            lists = cachedTab.lists,
                            starterPacks = cachedTab.starterPacks,
                            cursor = cachedTab.cursor,
                            loadedOnce = cachedTab.loadedOnce,
                        ),
                    )
                }
            }
        }
    }
    val listStates = remember(targetActor, session.currentDid) { mutableStateMapOf<ProfileTab, LazyListState>() }
    val tabRequests = remember(targetActor, session.currentDid) { mutableMapOf<ProfileTab, Int>() }

    fun syncProfileToCache(
        selectedTabIndex: Int = screenCache.profiles[targetActor]?.selectedTabIndex ?: 0,
        headerOffset: Float = screenCache.profiles[targetActor]?.headerOffsetPx ?: 0f,
    ) {
        val loaded = profile ?: return
        if (targetActor.isBlank()) return
        val cachedTabs = tabStates.mapKeys { it.key.name }.mapValues { (_, st) ->
            ScreenStateCache.CachedProfileTab(
                feedPosts = st.feedPosts,
                feeds = st.feeds,
                lists = st.lists,
                starterPacks = st.starterPacks,
                cursor = st.cursor,
                loadedOnce = st.loadedOnce,
            )
        }
        screenCache.profiles[targetActor] = ScreenStateCache.CachedProfileScreen(
            profile = loaded,
            labelerView = labelerView,
            labelerLikeUri = labelerLikeUri,
            labelerLikeCount = labelerLikeCount,
            selectedTabIndex = selectedTabIndex,
            tabs = cachedTabs,
            headerOffsetPx = headerOffset,
        )
    }

    suspend fun loadProfile(force: Boolean = false) {
        if (targetActor.isBlank()) {
            isLoadingProfile = false
            profile = null
            return
        }
        if (force || profile == null) {
            isLoadingProfile = profile == null
            profileError = null
        }
        runCatching {
            container.api.getProfile(targetActor)
        }.onSuccess { loaded ->
            profile = loaded
            if (loaded.associated?.labeler == true) {
                isLoadingLabeler = labelerView == null
                runCatching {
                    container.api.getLabelerServices(listOf(loaded.did)).firstOrNull()
                }.onSuccess { svc ->
                    labelerView = svc
                    labelerLikeUri = svc?.viewer?.like
                    labelerLikeCount = svc?.likeCount ?: 0
                }
                isLoadingLabeler = false
            } else {
                labelerView = null
            }
            syncProfileToCache()
        }.onFailure { ex ->
            if (profile == null) {
                profileError = ex.message ?: i18n.translate("Unable to load profile")
            }
        }
        isLoadingProfile = false
    }

    LaunchedEffect(targetActor, session.currentDid) {
        loadProfile(force = profile == null)
    }

    val currentProfile = profile
    val isMe = session.currentDid != null && currentProfile?.did == session.currentDid
    val isLabeler = currentProfile?.associated?.labeler == true
    val associated = currentProfile?.associated
    val starterPackCount = associated?.starterPacks ?: 0
    val showLists = session.hasSession && (isMe || (associated?.lists ?: 0) > starterPackCount)
    val visibleTabs = buildList {
        if (isLabeler) {
            add(ProfileTab.Labels)
            if (showLists) add(ProfileTab.Lists)
        }
        add(ProfileTab.Posts)
        if (session.hasSession) add(ProfileTab.Replies)
        if (!isLabeler) {
            add(ProfileTab.Media)
            add(ProfileTab.Videos)
        }
        if (isMe) add(ProfileTab.Likes)
        if (isMe || (associated?.feedgens ?: 0) > 0) add(ProfileTab.Feeds)
        if (isMe || starterPackCount > 0) add(ProfileTab.StarterPacks)
        if (showLists && !isLabeler) add(ProfileTab.Lists)
    }
    val pagerState = rememberPagerState(
        initialPage = (cachedProfile?.selectedTabIndex ?: 0).coerceIn(0, (visibleTabs.size - 1).coerceAtLeast(0)),
        pageCount = { visibleTabs.size },
    )

    LaunchedEffect(targetActor) {
        val targetPage = (screenCache.profiles[targetActor]?.selectedTabIndex ?: 0)
            .coerceIn(0, (visibleTabs.size - 1).coerceAtLeast(0))
        if (pagerState.currentPage != targetPage) {
            pagerState.scrollToPage(targetPage)
        }
    }

    LaunchedEffect(targetActor, pagerState.currentPage) {
        syncProfileToCache(selectedTabIndex = pagerState.currentPage)
    }

    suspend fun findPinnedPost(
        loadedProfile: ProfileViewDetailed,
        feed: List<FeedViewPost>,
    ): FeedViewPost? {
        feed.firstOrNull { it.isProfilePin() && it.post.author.did == loadedProfile.did }?.let { return it }
        val pinUri = loadedProfile.pinnedPost?.uri ?: return null
        feed.firstOrNull { it.post.uri == pinUri && it.post.author.did == loadedProfile.did }?.let {
            return it.copy(reason = PinnedPostReason)
        }
        // Some AppViews leave the pin out of the author feed. Fetch it independently.
        val post = runCatching { container.api.getPosts(listOf(pinUri)) }
            .onFailure { if (it is CancellationException) throw it }
            .getOrNull()
            ?.firstOrNull { it.uri == pinUri && it.author.did == loadedProfile.did }
        return post?.let { FeedViewPost(post = it, reason = PinnedPostReason) }
    }

    suspend fun loadTab(tab: ProfileTab, refresh: Boolean = false, loadMore: Boolean = false) {
        val loadedProfile = profile ?: return
        if (tab == ProfileTab.Labels) return
        val current = tabStates[tab] ?: ProfileTabState()
        if (current.isRefreshing || current.isLoading) return
        if (loadMore && (current.isLoadingMore || current.cursor.isNullOrBlank())) return
        if (!refresh && !loadMore && current.loadedOnce) return

        val request = (tabRequests[tab] ?: 0) + 1
        tabRequests[tab] = request
        tabStates[tab] = current.copy(
            isLoading = !refresh && !loadMore && !current.loadedOnce,
            isRefreshing = refresh,
            isLoadingMore = loadMore,
            error = null,
        )

        val nextCursor = if (loadMore) current.cursor else null

        runCatching {
            when (tab) {
                ProfileTab.Posts -> {
                    val res = container.api.getAuthorFeed(
                        actor = loadedProfile.did,
                        filter = "posts_and_author_threads",
                        cursor = nextCursor,
                        includePins = !loadMore,
                    )
                    val pinned = if (loadMore) current.feedPosts.firstOrNull { it.isProfilePin() }
                        else findPinnedPost(loadedProfile, res.feed)
                    val regularPosts = res.feed.filterNot { it.isProfilePin() || it.post.uri == pinned?.post?.uri }
                    val merged = if (loadMore) {
                        (current.feedPosts + regularPosts).distinctBy { it.post.uri to it.reason }
                    } else {
                        listOfNotNull(pinned) + regularPosts
                    }
                    current.copy(feedPosts = merged, cursor = res.cursor, loadedOnce = true, isLoading = false, isRefreshing = false, isLoadingMore = false)
                }
                ProfileTab.Replies -> {
                    val res = container.api.getAuthorFeed(loadedProfile.did, "posts_with_replies", nextCursor, 30)
                    val merged = if (loadMore) {
                        (current.feedPosts + res.feed).distinctBy { it.post.uri + ":" + (it.reason?.toString() ?: "") }
                    } else res.feed
                    current.copy(feedPosts = merged, cursor = res.cursor, loadedOnce = true, isLoading = false, isRefreshing = false, isLoadingMore = false)
                }
                ProfileTab.Media -> {
                    val res = container.api.getAuthorFeed(loadedProfile.did, "posts_with_media", nextCursor, 30)
                    val merged = if (loadMore) {
                        (current.feedPosts + res.feed).distinctBy { it.post.uri + ":" + (it.reason?.toString() ?: "") }
                    } else res.feed
                    current.copy(feedPosts = merged, cursor = res.cursor, loadedOnce = true, isLoading = false, isRefreshing = false, isLoadingMore = false)
                }
                ProfileTab.Videos -> {
                    val res = container.api.getAuthorFeed(loadedProfile.did, "posts_with_video", nextCursor, 30)
                    val merged = if (loadMore) {
                        (current.feedPosts + res.feed).distinctBy { it.post.uri + ":" + (it.reason?.toString() ?: "") }
                    } else res.feed
                    current.copy(feedPosts = merged, cursor = res.cursor, loadedOnce = true, isLoading = false, isRefreshing = false, isLoadingMore = false)
                }
                ProfileTab.Likes -> {
                    val res = container.api.getActorLikes(loadedProfile.did, nextCursor, 30)
                    val merged = if (loadMore) {
                        (current.feedPosts + res.feed).distinctBy { it.post.uri }
                    } else res.feed
                    current.copy(feedPosts = merged, cursor = res.cursor, loadedOnce = true, isLoading = false, isRefreshing = false, isLoadingMore = false)
                }
                ProfileTab.Feeds -> {
                    val res = container.api.getActorFeeds(loadedProfile.did, nextCursor)
                    val items = (res["feeds"] as? JsonArray)?.mapNotNull {
                        runCatching { AtProtoJson.decodeFromJsonElement<GeneratorView>(it) }.getOrNull()
                    }.orEmpty()
                    val cur = (res["cursor"] as? JsonPrimitive)?.content
                    val merged = if (loadMore) (current.feeds + items).distinctBy { it.uri } else items
                    current.copy(feeds = merged, cursor = cur, loadedOnce = true, isLoading = false, isRefreshing = false, isLoadingMore = false)
                }
                ProfileTab.Lists -> {
                    val items = container.api.getLists(loadedProfile.did, null)
                    current.copy(lists = items, cursor = null, loadedOnce = true, isLoading = false, isRefreshing = false, isLoadingMore = false)
                }
                ProfileTab.StarterPacks -> {
                    val items = container.api.getActorStarterPacks(loadedProfile.did)
                    current.copy(starterPacks = items, cursor = null, loadedOnce = true, isLoading = false, isRefreshing = false, isLoadingMore = false)
                }
                ProfileTab.Labels -> current
            }
        }.onSuccess { updated ->
            if (tabRequests[tab] != request) return
            tabStates[tab] = updated
            syncProfileToCache(selectedTabIndex = pagerState.currentPage)
        }.onFailure { ex ->
            if (tabRequests[tab] == request) {
                tabStates[tab] = current.copy(
                    isLoading = false,
                    isRefreshing = false,
                    isLoadingMore = false,
                    error = if (ex is CancellationException) current.error
                        else ex.message ?: i18n.translate("Unable to load tab"),
                )
            }
            if (ex is CancellationException) throw ex
        }
    }

    // Signed-out own profile empty state
    if (targetActor.isBlank() && !isLoadingProfile) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.contrast0.toComposeColor())
                .statusBarsPadding()
                .padding(24.dp),
        ) {
            IconButton(
                onClick = { onBack?.invoke() ?: onOpenDrawer() },
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                Icon(
                    imageVector = if (onBack != null) Icons.AutoMirrored.Filled.ArrowBack else Icons.Filled.Menu,
                    contentDescription = if (onBack != null) tr("Back") else tr("Menu"),
                )
            }
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(tr("Sign in to view your profile"), style = MaterialTheme.typography.titleMedium)
                Button(onClick = onOpenAccountSwitcher) {
                    Text(tr("Sign in"))
                }
            }
        }
        return
    }

    if (isLoadingProfile && currentProfile == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.contrast0.toComposeColor())
                .statusBarsPadding(),
        ) {
            IconButton(
                onClick = { onBack?.invoke() ?: onOpenDrawer() },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
            ) {
                Icon(
                    imageVector = if (onBack != null) Icons.AutoMirrored.Filled.ArrowBack else Icons.Filled.Menu,
                    contentDescription = if (onBack != null) tr("Back") else tr("Menu"),
                )
            }
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        return
    }

    if (currentProfile == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.contrast0.toComposeColor())
                .statusBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(profileError ?: tr("Profile not found"), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onBack != null) {
                    OutlinedButton(onClick = onBack) { Text(tr("Back")) }
                }
                Button(onClick = { scope.launch { loadProfile(force = true) } }) {
                    Text(tr("Retry"))
                }
            }
        }
        return
    }

    val statusBarTopPx = with(density) {
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding().toPx()
    }
    val pinnedTopBarHeightPx = with(density) { 56.dp.toPx() + statusBarTopPx }.roundToInt()
    var collapsibleHeightPx by remember(currentProfile.did) { mutableIntStateOf(0) }
    var headerOffsetPx by remember(currentProfile.did) {
        mutableFloatStateOf(screenCache.profiles[targetActor]?.headerOffsetPx ?: 0f)
    }
    DisposableEffect(targetActor, currentProfile.did) {
        onDispose {
            syncProfileToCache(
                selectedTabIndex = pagerState.currentPage,
                headerOffset = headerOffsetPx,
            )
        }
    }

    val collapseProgress = if (collapsibleHeightPx > 0) {
        (-headerOffsetPx / collapsibleHeightPx.toFloat()).coerceIn(0f, 1f)
    } else 0f

    fun activeTabCanScrollBackward(): Boolean {
        val activeTab = visibleTabs.getOrNull(pagerState.currentPage) ?: return false
        return listStates[activeTab]?.canScrollBackward == true
    }

    fun consumeHeaderDelta(deltaY: Float): Float {
        if (collapsibleHeightPx <= 0) return 0f
        val minOffset = -collapsibleHeightPx.toFloat()
        val prev = headerOffsetPx
        val next = (prev + deltaY).coerceIn(minOffset, 0f)
        headerOffsetPx = next
        return next - prev
    }

    val nestedScrollConnection = remember(currentProfile.did) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0f) {
                    val consumedY = consumeHeaderDelta(available.y)
                    return Offset(0f, consumedY)
                }
                if (available.y > 0f && !activeTabCanScrollBackward()) {
                    val consumedY = consumeHeaderDelta(available.y)
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f) {
                    val consumedY = consumeHeaderDelta(available.y)
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }
        }
    }

    val headerDragState = rememberDraggableState { delta ->
        consumeHeaderDelta(delta)
    }

    val profileMod = remember(currentProfile, session.moderationOpts) {
        ModerationEngine.moderateProfile(currentProfile, session.moderationOpts)
    }
    val avatarBlur = profileMod.ui(ModerationContext.avatar).blur
    val bannerBlur = profileMod.ui(ModerationContext.banner).blur
    val profileViewBlur = profileMod.ui(ModerationContext.profileView).blur
    val isBannerVisible = collapseProgress < 0.6f
    val windowView = LocalView.current
    val window = (windowView.context as? Activity)?.window
    if (window != null && !windowView.isInEditMode) {
        val lightStatusBar = !palette.isDark
        val lightStatusBarOverBanner = lightStatusBar && (
            !isBannerVisible || currentProfile.banner.isNullOrBlank() || bannerBlur
        )
        SideEffect {
            WindowCompat.getInsetsController(window, windowView).isAppearanceLightStatusBars = lightStatusBarOverBanner
        }
        DisposableEffect(window, windowView, lightStatusBar) {
            onDispose {
                WindowCompat.getInsetsController(window, windowView).isAppearanceLightStatusBars = lightStatusBar
            }
        }
    }
    val headerNameStyle = MaterialTheme.typography.headlineLarge
    val headerBodyStyle = MaterialTheme.typography.bodyLarge

    val handleForNav = currentProfile.handle.ifBlank { currentProfile.did }
    val isBlocking = !currentProfile.viewer?.blocking.isNullOrBlank()
    val isBlockedBy = currentProfile.viewer?.blockedBy == true
    val isBlocked = isBlocking || isBlockedBy || currentProfile.viewer?.blockingByList != null
    val isMuted = currentProfile.viewer?.muted == true
    val isFollowing = !currentProfile.viewer?.following.isNullOrBlank()
    val isFollowedBy = !currentProfile.viewer?.followedBy.isNullOrBlank()
    val declaresIncomingMessages = when (currentProfile.associated?.chat?.allowIncoming) {
        "all" -> true
        null, "following" -> isFollowedBy
        else -> false
    }
    var convoAvailability by remember(session.activation, currentProfile.did, isBlocked) {
        mutableStateOf<ConvoAvailability?>(null)
    }
    LaunchedEffect(session.activation, currentProfile.did, isBlocked) {
        val did = session.currentDid ?: return@LaunchedEffect
        if (isMe || isBlocked) return@LaunchedEffect
        container.sessionManager.runForCurrentAccount(did) {
            convoAvailability = runCatching { container.api.getConvoAvailability(currentProfile.did) }
                .onFailure { if (it is CancellationException) throw it }
                .getOrNull()
        }
    }
    val canMessage = convoAvailability?.canChat ?: declaresIncomingMessages
    val canSubscribeToActivity = isFollowing && !isLabeler &&
        when (currentProfile.associated?.activitySubscription?.allowSubscriptions) {
            null, "followers" -> true
            "mutuals" -> isFollowedBy
            else -> false
        }
    val activitySubscription = currentProfile.viewer?.activitySubscription ?: ActivitySubscription()
    val isSubscribedToActivity = activitySubscription.post || activitySubscription.reply
    val isSubscribedLabeler = modSettings.labelers.any { it.did == currentProfile.did }

    val bioFacets = remember(currentProfile.description, container.tlds) {
        currentProfile.description?.let {
            RichTextEngine.detectFacetsWithoutResolution(it, container.tlds)
        }.orEmpty()
    }
    val sponsorUrls = remember(currentProfile.description, bioFacets, currentProfile.website) {
        extractGitHubSponsorUrls(currentProfile.description, bioFacets, currentProfile.website)
    }

    fun startConversation() {
        if (!session.hasSession || !canMessage || isStartingConvo) return
        convoAvailability?.convo?.let {
            onNavigate(AppRoute.MessagesConversation(it.id))
            return
        }
        isStartingConvo = true
        scope.launch {
            runCatching {
                container.api.getConvoForMembers(listOf(currentProfile.did))
            }.onSuccess { convo ->
                onNavigate(AppRoute.MessagesConversation(convo.id))
            }.onFailure { error ->
                if (error is CancellationException) throw error
                Toast.makeText(
                    context,
                    error.message ?: i18n.translate("Unable to start conversation"),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            isStartingConvo = false
        }
    }

    fun toggleFollow() {
        val myDid = session.currentDid ?: return
        if (isFollowPending) return
        haptics.confirm()
        val prevFollowingUri = currentProfile.viewer?.following
        val prevCount = currentProfile.followersCount ?: 0
        isFollowPending = true
        scope.launch {
            runCatching {
                if (!prevFollowingUri.isNullOrBlank()) {
                    profile = currentProfile.copy(
                        viewer = currentProfile.viewer?.copy(following = null),
                        followersCount = (prevCount - 1).coerceAtLeast(0),
                    )
                    container.api.deleteRecord(myDid, "app.bsky.graph.follow", prevFollowingUri.substringAfterLast('/'))
                } else {
                    profile = currentProfile.copy(
                        viewer = (currentProfile.viewer ?: am.noz.caelum.core.model.ActorViewerState()).copy(following = "pending"),
                        followersCount = prevCount + 1,
                    )
                    val created = container.api.followActor(myDid, currentProfile.did)
                    profile = profile?.copy(
                        viewer = profile?.viewer?.copy(following = created.uri),
                    )
                }
            }.onFailure { ex ->
                profile = currentProfile
                Toast.makeText(context, ex.message ?: i18n.translate("Action failed"), Toast.LENGTH_SHORT).show()
            }
            isFollowPending = false
        }
    }

    fun toggleMute() {
        if (!session.hasSession) return
        haptics.confirm()
        val targetMuted = !isMuted
        scope.launch {
            runCatching {
                if (targetMuted) {
                    container.api.muteActor(currentProfile.did)
                } else {
                    container.api.unmuteActor(currentProfile.did)
                }
                profile = currentProfile.copy(
                    viewer = (currentProfile.viewer ?: am.noz.caelum.core.model.ActorViewerState()).copy(muted = targetMuted),
                )
                Toast.makeText(
                    context,
                    if (targetMuted) i18n.translate("Account muted") else i18n.translate("Account unmuted"),
                    Toast.LENGTH_SHORT,
                ).show()
            }.onFailure { ex ->
                Toast.makeText(context, ex.message ?: i18n.translate("Action failed"), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun toggleBlock() {
        val myDid = session.currentDid ?: return
        haptics.confirm()
        val prevBlockUri = currentProfile.viewer?.blocking
        scope.launch {
            runCatching {
                if (!prevBlockUri.isNullOrBlank()) {
                    container.api.deleteRecord(myDid, "app.bsky.graph.block", prevBlockUri.substringAfterLast('/'))
                    profile = currentProfile.copy(
                        viewer = currentProfile.viewer?.copy(blocking = null),
                    )
                    Toast.makeText(context, i18n.translate("Account unblocked"), Toast.LENGTH_SHORT).show()
                } else {
                    val created = container.api.blockActor(myDid, currentProfile.did)
                    profile = currentProfile.copy(
                        viewer = (currentProfile.viewer ?: am.noz.caelum.core.model.ActorViewerState()).copy(
                            blocking = created.uri,
                            following = null,
                        ),
                    )
                    Toast.makeText(context, i18n.translate("Account blocked"), Toast.LENGTH_SHORT).show()
                }
            }.onFailure { ex ->
                Toast.makeText(context, ex.message ?: i18n.translate("Action failed"), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun toggleLabelerSubscription() {
        val myDid = session.currentDid ?: return
        if (isSubscribePending) return
        val wantSubscribe = !isSubscribedLabeler
        if (wantSubscribe && modSettings.labelers.size >= MAX_SUBSCRIBED_LABELERS) {
            Toast.makeText(
                context,
                i18n.translate("You can only subscribe to up to 20 labelers."),
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        haptics.confirm()
        isSubscribePending = true
        scope.launch {
            runCatching {
                var updatedLabelers: List<LabelerPrefItem> = emptyList()
                container.preferences.updateModerationSettings(myDid) { prev ->
                    updatedLabelers = if (wantSubscribe) {
                        (prev.labelers + LabelerPrefItem(did = currentProfile.did)).distinctBy { it.did }
                    } else {
                        prev.labelers.filterNot { it.did == currentProfile.did }
                    }
                    prev.copy(labelers = updatedLabelers)
                }
                val raw = container.api.getActorPreferences().toMutableList()
                val filtered = raw.filterNot {
                    (it as? JsonObject)?.get("\$type")?.let { t ->
                        (t as? JsonPrimitive)?.content == "app.bsky.actor.defs#labelersPref"
                    } == true
                }.toMutableList()
                filtered.add(
                    buildJsonObject {
                        put("\$type", JsonPrimitive("app.bsky.actor.defs#labelersPref"))
                        put(
                            "labelers",
                            buildJsonArray {
                                updatedLabelers.forEach { item ->
                                    add(buildJsonObject { put("did", JsonPrimitive(item.did)) })
                                }
                            },
                        )
                    }
                )
                container.api.putActorPreferences(JsonArray(filtered))
                container.sessionManager.syncPreferencesFromServer(myDid)
            }.onFailure { ex ->
                Toast.makeText(context, ex.message ?: i18n.translate("Unable to update labeler subscription"), Toast.LENGTH_SHORT).show()
            }
            isSubscribePending = false
        }
    }

    fun toggleLabelerLike() {
        val myDid = session.currentDid ?: return
        val svc = labelerView ?: return
        haptics.tap()
        val prevLike = labelerLikeUri
        val prevCount = labelerLikeCount
        scope.launch {
            runCatching {
                if (!prevLike.isNullOrBlank()) {
                    labelerLikeUri = null
                    labelerLikeCount = (prevCount - 1).coerceAtLeast(0)
                    container.api.deleteRecord(myDid, "app.bsky.feed.like", prevLike.substringAfterLast('/'))
                } else {
                    labelerLikeUri = "pending"
                    labelerLikeCount = prevCount + 1
                    val created = container.api.likePost(myDid, StrongRef(svc.uri, svc.cid))
                    labelerLikeUri = created.uri
                }
            }.onFailure { ex ->
                labelerLikeUri = prevLike
                labelerLikeCount = prevCount
                Toast.makeText(context, ex.message ?: i18n.translate("Unable to update like"), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun updateLabelPreference(labelId: String, visibility: LabelPreference) {
        val myDid = session.currentDid ?: return
        haptics.selection()
        scope.launch {
            runCatching {
                container.preferences.updateModerationSettings(myDid) { prev ->
                    val filtered = prev.labelerContentLabels.filterNot {
                        it.labelerDid == currentProfile.did && it.label == labelId
                    }
                    prev.copy(
                        labelerContentLabels = filtered + am.noz.caelum.core.model.ContentLabelPref(
                            labelerDid = currentProfile.did,
                            label = labelId,
                            visibility = visibility.wire,
                        )
                    )
                }
                val raw = container.api.getActorPreferences().toMutableList()
                val filtered = raw.filterNot { el ->
                    val obj = el as? JsonObject ?: return@filterNot false
                    val type = (obj["\$type"] as? JsonPrimitive)?.content
                    val lDid = (obj["labelerDid"] as? JsonPrimitive)?.content
                    val lbl = (obj["label"] as? JsonPrimitive)?.content
                    type == "app.bsky.actor.defs#contentLabelPref" &&
                        lDid == currentProfile.did &&
                        lbl == labelId
                }.toMutableList()
                filtered.add(
                    buildJsonObject {
                        put("\$type", JsonPrimitive("app.bsky.actor.defs#contentLabelPref"))
                        put("labelerDid", JsonPrimitive(currentProfile.did))
                        put("label", JsonPrimitive(labelId))
                        put("visibility", JsonPrimitive(visibility.wire))
                    }
                )
                container.api.putActorPreferences(JsonArray(filtered))
                container.sessionManager.syncPreferencesFromServer(myDid)
            }.onFailure { ex ->
                Toast.makeText(context, ex.message ?: i18n.translate("Failed to save label preference"), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val profileOverflow: @Composable () -> Unit = {
        Box {
            ProfileActionIcon(
                icon = ShellIcons.More,
                label = tr("More options"),
                onClick = { showOverflowMenu = true },
            )

            DropdownMenu(
                expanded = showOverflowMenu,
                onDismissRequest = { showOverflowMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text(tr("Search posts")) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    onClick = {
                        showOverflowMenu = false
                        onNavigate(AppRoute.ProfileSearch(handleForNav))
                    },
                )
                if (!isMe && session.hasSession) {
                    DropdownMenuItem(
                        text = { Text(if (isMuted) tr("Unmute account") else tr("Mute account")) },
                        leadingIcon = { Icon(Icons.Filled.VisibilityOff, contentDescription = null) },
                        onClick = {
                            showOverflowMenu = false
                            toggleMute()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (isBlocking) tr("Unblock account") else tr("Block account")) },
                        leadingIcon = { Icon(Icons.Filled.Block, contentDescription = null) },
                        onClick = {
                            showOverflowMenu = false
                            showBlockConfirm = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(tr("Report account")) },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Report,
                                contentDescription = null,
                                tint = palette.negative500.toComposeColor(),
                            )
                        },
                        onClick = {
                            showOverflowMenu = false
                            showReportAccountDialog = true
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(tr("Share")) },
                    leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                    onClick = {
                        showOverflowMenu = false
                        LinkNavigationHelper.shareText(
                            context,
                            "https://bsky.app/profile/$handleForNav",
                        )
                    },
                )
                DropdownMenuItem(
                    text = { Text(tr("Open in…")) },
                    leadingIcon = { Icon(Icons.Filled.OpenInNew, contentDescription = null) },
                    onClick = {
                        showOverflowMenu = false
                        onOpenIn(OpenInSubject.Profile(currentProfile.did, currentProfile.handle))
                    },
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.contrast0.toComposeColor())
            .nestedScroll(nestedScrollConnection),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Collapsing Header Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = headerDragState,
                    )
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val newCollapsible = (placeable.height - pinnedTopBarHeightPx).coerceAtLeast(0)
                        if (collapsibleHeightPx != newCollapsible) {
                            collapsibleHeightPx = newCollapsible
                        }
                        val clampedOffset = headerOffsetPx.coerceIn(-newCollapsible.toFloat(), 0f)
                        val visibleHeight = (placeable.height + clampedOffset.roundToInt())
                            .coerceAtLeast(pinnedTopBarHeightPx.coerceAtMost(placeable.height))
                        layout(placeable.width, visibleHeight) {
                            placeable.placeRelative(0, clampedOffset.roundToInt())
                        }
                    },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.contrast0.toComposeColor()),
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp)
                                    .background(palette.contrast100.toComposeColor())
                                    .clickable(
                                        enabled = !currentProfile.banner.isNullOrBlank() && !bannerBlur,
                                        role = Role.Image,
                                    ) {
                                        val url = currentProfile.banner ?: return@clickable
                                        onOpenLightbox(listOf(EmbedImageItem(thumb = url, fullsize = url)), 0)
                                    },
                            ) {
                                if (!currentProfile.banner.isNullOrBlank() && !bannerBlur) {
                                    AsyncImage(
                                        model = currentProfile.banner,
                                        contentDescription = tr("View profile banner"),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }

                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent),
                                            ),
                                        ),
                                )
                            }

                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 106.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                itemVerticalAlignment = Alignment.CenterVertically,
                            ) {
                                when {
                                    isMe -> {
                                        ProfileActionButton(
                                            label = tr("Edit Profile"),
                                            onClick = { showEditDialog = true },
                                        )
                                        ProfileActionIcon(
                                            icon = ShellIcons.Share,
                                            label = tr("Share"),
                                            onClick = {
                                                LinkNavigationHelper.shareText(context, "https://bsky.app/profile/$handleForNav")
                                            },
                                        )
                                    }
                                    isBlocking && currentProfile.viewer?.blockingByList == null -> {
                                        ProfileActionButton(
                                            label = tr("Unblock"),
                                            onClick = { showBlockConfirm = true },
                                        )
                                    }
                                    !isBlocked && session.hasSession -> {
                                        if (canSubscribeToActivity) {
                                            ProfileActionIcon(
                                                icon = if (isSubscribedToActivity) ProfileHeaderIcons.BellRinging
                                                    else ProfileHeaderIcons.BellPlus,
                                                label = tr("Get notified about new posts"),
                                                onClick = { showActivitySubscription = true },
                                            )
                                        }
                                        if (canMessage) {
                                            ProfileActionIcon(
                                                icon = ProfileHeaderIcons.Message,
                                                label = tr("Message"),
                                                pending = isStartingConvo,
                                                onClick = { startConversation() },
                                            )
                                        }
                                        if (isLabeler) {
                                            ProfileActionButton(
                                                label = if (isSubscribedLabeler) tr("Unsubscribe") else tr("Subscribe"),
                                                primary = !isSubscribedLabeler,
                                                enabled = !isSubscribePending,
                                                onClick = { toggleLabelerSubscription() },
                                            )
                                        } else {
                                            ProfileActionButton(
                                                label = when {
                                                    isFollowing -> tr("Following")
                                                    currentProfile.viewer?.followedBy != null -> tr("Follow back")
                                                    else -> tr("Follow")
                                                },
                                                primary = !isFollowing,
                                                icon = if (isFollowing) null else ProfileHeaderIcons.Plus,
                                                enabled = !isFollowPending,
                                                onClick = { toggleFollow() },
                                            )
                                        }
                                    }
                                }
                                if (collapseProgress <= 0.6f) {
                                    profileOverflow()
                                }
                            }
                        }

                        val isLive = currentProfile.status?.isActive == true
                        val avatarShape = if (isLabeler) RoundedCornerShape(16.dp) else CircleShape
                        Box(
                            modifier = Modifier
                                .padding(start = 10.dp, top = 104.dp)
                                .size(94.dp)
                                .background(
                                    if (isLive) palette.negative500.toComposeColor()
                                    else palette.contrast0.toComposeColor(),
                                    avatarShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            CaelumAvatar(
                                url = if (avatarBlur) null else currentProfile.avatar,
                                contentDescription = currentProfile.displayName ?: currentProfile.handle,
                                size = if (isLive) 88.dp else 90.dp,
                                shapeType = if (isLabeler) AvatarShape.RoundedSquare else AvatarShape.Circle,
                                isLive = isLive,
                                showBorder = false,
                                onClick = currentProfile.avatar?.takeIf { it.isNotBlank() && !avatarBlur }?.let { url ->
                                    { onOpenLightbox(listOf(EmbedImageItem(thumb = url, fullsize = url)), 0) }
                                },
                            )
                        }
                    }

                    // Profile Info Block
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 3.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        DisplayNameRow(
                            profile = currentProfile.toBasicProfile(),
                            showHandle = false,
                            showPronouns = false,
                            nameStyle = headerNameStyle,
                            nameMaxLines = 2,
                            badgeSize = 18.dp,
                        )

                        // Handle + Follows you + Caelum Pronouns Badge
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            itemVerticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (currentProfile.viewer?.followedBy != null && !isBlocked) {
                                Text(
                                    text = tr("Follows you"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.contrast700.toComposeColor(),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(palette.contrast50.toComposeColor())
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                            Text(
                                text = if (currentProfile.handle == "handle.invalid") {
                                    tr("⚠Invalid Handle")
                                } else {
                                    "@${currentProfile.handle}"
                                },
                                style = headerBodyStyle,
                                color = palette.contrast600.toComposeColor(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val pronounsText = currentProfile.pronouns?.trim()
                            if (!pronounsText.isNullOrEmpty() && !isBlocked && !profileViewBlur) {
                                Text(
                                    text = pronounsText,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    color = palette.primary700.toComposeColor(),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(palette.primary50.toComposeColor())
                                        .border(0.5.dp, palette.primary200.toComposeColor(), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }

                        if (isMuted && !isBlocked) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(palette.contrast50.toComposeColor())
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.VisibilityOff,
                                        contentDescription = null,
                                        tint = palette.contrast600.toComposeColor(),
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = tr("You muted this account"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = palette.contrast700.toComposeColor(),
                                    )
                                }
                                Text(
                                    text = tr("Unmute"),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = palette.primary500.toComposeColor(),
                                    modifier = Modifier.clickable { toggleMute() },
                                )
                            }
                        }

                        if (!isBlocked) {
                            // Metrics Row: followers / following / posts
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                itemVerticalAlignment = Alignment.CenterVertically,
                            ) {
                                val followersCount = currentProfile.followersCount ?: 0
                                val followsCount = currentProfile.followsCount ?: 0
                                val postsCount = currentProfile.postsCount ?: 0

                                Text(
                                    text = buildAnnotatedString {
                                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = palette.contrast1000.toComposeColor())) {
                                            append(formatCompactCount(followersCount))
                                        }
                                        append(" ")
                                        withStyle(SpanStyle(color = palette.contrast600.toComposeColor())) {
                                            append(tr("followers"))
                                        }
                                    },
                                    style = headerBodyStyle,
                                    modifier = Modifier.clickable {
                                        onNavigate(AppRoute.ProfileFollowers(handleForNav))
                                    },
                                )

                                Text(
                                    text = buildAnnotatedString {
                                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = palette.contrast1000.toComposeColor())) {
                                            append(formatCompactCount(followsCount))
                                        }
                                        append(" ")
                                        withStyle(SpanStyle(color = palette.contrast600.toComposeColor())) {
                                            append(tr("following"))
                                        }
                                    },
                                    style = headerBodyStyle,
                                    modifier = Modifier.clickable {
                                        onNavigate(AppRoute.ProfileFollows(handleForNav))
                                    },
                                )

                                Text(
                                    text = buildAnnotatedString {
                                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = palette.contrast1000.toComposeColor())) {
                                            append(formatCompactCount(postsCount))
                                        }
                                        append(" ")
                                        withStyle(SpanStyle(color = palette.contrast600.toComposeColor())) {
                                            append(tr("posts"))
                                        }
                                    },
                                    style = headerBodyStyle,
                                    modifier = Modifier.clickable {
                                        val postsIdx = visibleTabs.indexOf(ProfileTab.Posts)
                                        if (postsIdx >= 0) {
                                            scope.launch { pagerState.animateScrollToPage(postsIdx) }
                                        }
                                    },
                                )
                            }

                            // RichText Bio
                            if (!currentProfile.description.isNullOrBlank() && !profileViewBlur) {
                                RichTextView(
                                    text = currentProfile.description,
                                    facets = bioFacets,
                                    modifier = Modifier.padding(top = 2.dp),
                                    maxLines = 15,
                                    overflow = TextOverflow.Ellipsis,
                                    style = headerBodyStyle.copy(
                                        color = palette.contrast950.toComposeColor(),
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
                                                useLinkRedirects = settings.infrastructure.useLinkRedirects,
                                                openBskyLinksInCaelum = settings.openBskyLinksInCaelum,
                                                onInternalRoute = onNavigate,
                                            )
                                        }
                                    },
                                )
                            }

                            // Caelum GitHub Sponsors Green Pill(s)
                            if (sponsorUrls.isNotEmpty() && !profileViewBlur) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    for (sponsorUrl in sponsorUrls) {
                                        GitHubSponsorPill(
                                            url = sponsorUrl,
                                            onClick = {
                                                LinkNavigationHelper.openUrl(
                                                    context = context,
                                                    rawUrl = sponsorUrl,
                                                    useLinkRedirects = settings.infrastructure.useLinkRedirects,
                                                    openBskyLinksInCaelum = false,
                                                    onInternalRoute = onNavigate,
                                                )
                                            },
                                        )
                                    }
                                }
                            }

                            // Website link
                            val websiteUrl = currentProfile.website?.trim()
                            if (!websiteUrl.isNullOrBlank() && !profileViewBlur) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            LinkNavigationHelper.openUrl(
                                                context = context,
                                                rawUrl = websiteUrl,
                                                useLinkRedirects = settings.infrastructure.useLinkRedirects,
                                                openBskyLinksInCaelum = settings.openBskyLinksInCaelum,
                                                onInternalRoute = onNavigate,
                                            )
                                        }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Link,
                                        contentDescription = null,
                                        tint = palette.primary500.toComposeColor(),
                                        modifier = Modifier.size(15.dp),
                                    )
                                    Text(
                                        text = websiteUrl.removePrefix("https://").removePrefix("http://").trimEnd('/'),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = palette.primary500.toComposeColor(),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }

                            // Labeler Like Row
                            if (isLabeler && labelerView != null) {
                                val isLikedLabeler = !labelerLikeUri.isNullOrBlank()
                                Row(
                                    modifier = Modifier.padding(top = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    IconButton(
                                        onClick = { toggleLabelerLike() },
                                        enabled = session.hasSession,
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(palette.contrast50.toComposeColor()),
                                    ) {
                                        Icon(
                                            imageVector = if (isLikedLabeler) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                            contentDescription = tr("Like this labeler"),
                                            tint = if (isLikedLabeler) palette.like.toComposeColor() else palette.contrast700.toComposeColor(),
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    Text(
                                        text = tr("Liked by {count} users", mapOf("count" to formatCompactCount(labelerLikeCount))),
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = palette.contrast700.toComposeColor(),
                                        modifier = Modifier.clickable {
                                            onNavigate(AppRoute.ProfileLabelerLikedBy(handleForNav))
                                        },
                                    )
                                }
                            }

                            // Known Followers Row
                            val knownFollowers = currentProfile.viewer?.knownFollowers
                            if (!isMe && knownFollowers != null && knownFollowers.count > 0 && knownFollowers.followers.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            onNavigate(AppRoute.ProfileKnownFollowers(handleForNav))
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    val preview = knownFollowers.followers.take(3)
                                    Box(modifier = Modifier.width((30 + (preview.size - 1) * 23).dp).height(30.dp)) {
                                        preview.forEachIndexed { idx, f ->
                                            Box(
                                                modifier = Modifier
                                                    .offset(x = (idx * 23).dp)
                                                    .size(30.dp)
                                                    .clip(CircleShape)
                                                    .border(1.5.dp, palette.contrast0.toComposeColor(), CircleShape),
                                            ) {
                                                CaelumAvatar(
                                                    url = f.avatar,
                                                    contentDescription = f.displayName ?: f.handle,
                                                    size = 30.dp,
                                                )
                                            }
                                        }
                                    }
                                    val names = preview.map { it.displayName?.takeIf { d -> d.isNotBlank() } ?: "@${it.handle}" }
                                    val remaining = (knownFollowers.count - names.size).coerceAtLeast(0)
                                    val summary = when {
                                        remaining > 0 -> "Followed by ${names.joinToString(", ")}, and $remaining others"
                                        names.size == 1 -> "Followed by ${names[0]}"
                                        else -> "Followed by ${names.dropLast(1).joinToString(", ")} and ${names.last()}"
                                    }
                                    Text(
                                        text = summary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = palette.contrast600.toComposeColor(),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (isBlocked) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Block,
                            contentDescription = null,
                            tint = palette.contrast500.toComposeColor(),
                            modifier = Modifier.size(36.dp),
                        )
                        Text(
                            text = when {
                                isBlocking -> tr("Account blocked")
                                isBlockedBy -> tr("This account has blocked you")
                                else -> tr("Blocked by moderation list")
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.contrast900.toComposeColor(),
                        )
                    }
                }
            } else {
                // Sticky Tab Row
                SecondaryScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage.coerceIn(0, visibleTabs.lastIndex),
                    containerColor = palette.contrast0.toComposeColor(),
                    contentColor = palette.contrast1000.toComposeColor(),
                    edgePadding = 8.dp,
                    minTabWidth = 0.dp,
                ) {
                    visibleTabs.forEachIndexed { idx, tab ->
                        val selected = pagerState.currentPage == idx
                        Tab(
                            selected = selected,
                            onClick = { scope.launch { pagerState.animateScrollToPage(idx) } },
                            modifier = Modifier.height(44.dp),
                            content = {
                                Text(
                                    text = tr(tab.title),
                                    style = headerBodyStyle,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selected) palette.contrast1000.toComposeColor()
                                        else palette.contrast700.toComposeColor(),
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                )
                            },
                        )
                    }
                }

                // Tab Pages
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) { pageIdx ->
                    val tab = visibleTabs.getOrNull(pageIdx) ?: return@HorizontalPager
                    val listState = screenCache.rememberCachedLazyListState("profile_${targetActor}_${tab.name}")
                    listStates[tab] = listState

                    if (tab == ProfileTab.Labels) {
                        PullToRefreshBox(
                            isRefreshing = isLoadingLabeler,
                            onRefresh = { scope.launch { loadProfile(force = true) } },
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 32.dp),
                            ) {
                                item(key = "labeler_controls") {
                                    LabelerLabelsList(
                                        labeler = labelerView,
                                        isLoading = isLoadingLabeler,
                                        isSubscribed = isSubscribedLabeler,
                                        isBlocked = isBlocking,
                                        modSettings = modSettings,
                                        hasSession = session.hasSession,
                                        onSelectVisibility = { labelId, pref ->
                                            updateLabelPreference(labelId, pref)
                                        },
                                    )
                                }
                            }
                        }
                        return@HorizontalPager
                    }

                    val tabState = tabStates[tab] ?: ProfileTabState()

                    LaunchedEffect(tab, currentProfile.did) {
                        loadTab(tab)
                    }

                    val activeItemCount = when (tab) {
                        ProfileTab.Feeds -> tabState.feeds.size
                        ProfileTab.Lists -> tabState.lists.size
                        ProfileTab.StarterPacks -> tabState.starterPacks.size
                        else -> tabState.feedPosts.size
                    }

                    val shouldLoadMore by remember(listState, activeItemCount, tabState.cursor) {
                        derivedStateOf {
                            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            activeItemCount > 0 && !tabState.cursor.isNullOrBlank() && lastVisible >= activeItemCount - 6
                        }
                    }
                    LaunchedEffect(shouldLoadMore, tabState.cursor) {
                        if (shouldLoadMore) loadTab(tab, loadMore = true)
                    }

                    PullToRefreshBox(
                        isRefreshing = tabState.isRefreshing,
                        onRefresh = {
                            scope.launch {
                                loadProfile(force = true)
                                loadTab(tab, refresh = true)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        when {
                            tabState.isLoading -> {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                            tabState.error != null && activeItemCount == 0 -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(tabState.error, style = MaterialTheme.typography.bodyMedium)
                                    Spacer(Modifier.height(12.dp))
                                    Button(onClick = { scope.launch { loadTab(tab, refresh = true) } }) {
                                        Text(tr("Try again"))
                                    }
                                }
                            }
                            tab == ProfileTab.Feeds -> {
                                if (tabState.feeds.isEmpty()) {
                                    EmptyTabPlaceholder(tr("No custom feeds yet."))
                                } else {
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(bottom = 48.dp),
                                    ) {
                                        itemsIndexed(tabState.feeds, key = { _, f -> f.uri }) { _, feed ->
                                            FeedGeneratorRow(
                                                feed = feed,
                                                onClick = {
                                                    onNavigate(
                                                        AppRoute.CustomFeed(
                                                            feed.creator.did,
                                                            feed.uri.substringAfterLast('/'),
                                                        )
                                                    )
                                                },
                                            )
                                        }
                                        if (tabState.isLoadingMore) {
                                            item(key = "feeds_loader") { FooterLoader() }
                                        }
                                    }
                                }
                            }
                            tab == ProfileTab.Lists -> {
                                if (tabState.lists.isEmpty()) {
                                    EmptyTabPlaceholder(tr("No lists yet."))
                                } else {
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(bottom = 48.dp),
                                    ) {
                                        itemsIndexed(tabState.lists, key = { _, l -> l.uri }) { _, list ->
                                            ProfileListRow(
                                                list = list,
                                                onClick = {
                                                    onNavigate(
                                                        AppRoute.ProfileList(
                                                            list.creator.did,
                                                            list.uri.substringAfterLast('/'),
                                                        )
                                                    )
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                            tab == ProfileTab.StarterPacks -> {
                                if (tabState.starterPacks.isEmpty()) {
                                    EmptyTabPlaceholder(tr("No starter packs yet."))
                                } else {
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(bottom = 48.dp),
                                    ) {
                                        itemsIndexed(tabState.starterPacks, key = { _, p -> p.uri }) { _, pack ->
                                            StarterPackRow(
                                                pack = pack,
                                                onClick = {
                                                    onNavigate(
                                                        AppRoute.StarterPack(
                                                            pack.creator.did,
                                                            pack.uri.substringAfterLast('/'),
                                                        )
                                                    )
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                            else -> {
                                val mutedThreads = modSettings.mutedThreads
                                val filteredFeed = remember(tabState.feedPosts, shadows, session.moderationOpts, mutedThreads) {
                                    tabState.feedPosts.mapNotNull { item ->
                                        val applied = container.postShadowStore.applyToPost(item.post) ?: return@mapNotNull null
                                        if (applied.uri in mutedThreads) return@mapNotNull null
                                        val modUi = ModerationEngine.moderatePost(applied, session.moderationOpts)
                                            .ui(ModerationContext.contentList)
                                        if (modUi.filter) return@mapNotNull null
                                        item.copy(post = applied)
                                    }
                                }
                                if (filteredFeed.isEmpty()) {
                                    EmptyTabPlaceholder(tr("No posts yet."))
                                } else {
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(bottom = 48.dp),
                                    ) {
                                        itemsIndexed(
                                            items = filteredFeed,
                                            key = { idx, item -> "${tab.name}_${item.post.uri}_$idx" },
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
                                        if (tabState.isLoadingMore) {
                                            item(key = "feed_loader_${tab.name}") { FooterLoader() }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Pinned Top App Bar Overlay (transparent over banner -> solid when collapsed)
        val topBarBgAlpha = collapseProgress
        val pillBgAlpha = (1f - collapseProgress) * 0.48f
        val iconTint = if (collapseProgress > 0.6f) palette.contrast1000.toComposeColor() else Color.White

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.contrast0.toComposeColor().copy(alpha = topBarBgAlpha))
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                IconButton(
                    onClick = { onBack?.invoke() ?: onOpenDrawer() },
                    modifier = Modifier
                        .offset(y = (-12.5f * (1f - collapseProgress)).dp)
                        .size(31.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = pillBgAlpha)),
                ) {
                    Icon(
                        imageVector = if (onBack != null) ShellIcons.Back else Icons.Filled.Menu,
                        contentDescription = if (onBack != null) tr("Back") else tr("Menu"),
                        tint = iconTint,
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer { scaleX = if (onBack != null && mirrorBackArrow) -1f else 1f },
                    )
                }

                if (collapseProgress > 0.35f) {
                    Spacer(Modifier.width(10.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .alpha(((collapseProgress - 0.35f) / 0.65f).coerceIn(0f, 1f)),
                    ) {
                        Text(
                            text = currentProfile.displayName?.takeIf { it.isNotBlank() } ?: "@${currentProfile.handle}",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = palette.contrast1000.toComposeColor(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "@${currentProfile.handle}",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.contrast600.toComposeColor(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (isOwnProfileTab) {
                    IconButton(
                        onClick = onOpenAccountSwitcher,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = pillBgAlpha)),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SwitchAccount,
                            contentDescription = tr("Switch account"),
                            tint = iconTint,
                        )
                    }
                }

                if (collapseProgress > 0.6f) {
                    profileOverflow()
                }
            }
        }
    }

    if (showActivitySubscription) {
        ProfileActivitySubscriptionDialog(
            profileDid = currentProfile.did,
            currentSubscription = activitySubscription,
            onDismiss = { showActivitySubscription = false },
            onSaved = { subscription ->
                profile = profile?.let { loaded ->
                    loaded.copy(viewer = (loaded.viewer ?: ActorViewerState()).copy(activitySubscription = subscription))
                }
                syncProfileToCache()
                showActivitySubscription = false
            },
        )
    }

    if (showEditDialog) {
        EditProfileDialog(
            profile = currentProfile,
            onDismiss = { showEditDialog = false },
            onSaved = {
                showEditDialog = false
                scope.launch { loadProfile(force = true) }
            },
        )
    }

    if (showBlockConfirm) {
        AlertDialog(
            onDismissRequest = { showBlockConfirm = false },
            title = {
                Text(if (isBlocking) tr("Unblock Account?") else tr("Block Account?"))
            },
            text = {
                Text(
                    if (isBlocking) {
                        tr("The account will be able to interact with you after unblocking.")
                    } else {
                        tr("Blocked accounts cannot reply in your threads, mention you, or otherwise interact with you.")
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showBlockConfirm = false
                    toggleBlock()
                }) {
                    Text(
                        text = if (isBlocking) tr("Unblock") else tr("Block"),
                        color = palette.negative500.toComposeColor(),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirm = false }) {
                    Text(tr("Cancel"))
                }
            },
        )
    }

    if (showReportAccountDialog) {
        ReportAccountDialog(
            handle = currentProfile.handle,
            isSubmitting = isSubmittingAccountReport,
            onSubmit = { reasonType, details ->
                isSubmittingAccountReport = true
                scope.launch {
                    runCatching {
                        container.api.createModerationReport(
                            reasonType = reasonType,
                            subject = buildJsonObject {
                                put("\$type", JsonPrimitive("com.atproto.admin.defs#repoRef"))
                                put("did", JsonPrimitive(currentProfile.did))
                            },
                            reason = details.takeIf { it.isNotBlank() },
                        )
                    }.onSuccess {
                        showReportAccountDialog = false
                        Toast.makeText(context, i18n.translate("Report sent"), Toast.LENGTH_SHORT).show()
                    }.onFailure { ex ->
                        Toast.makeText(
                            context,
                            ex.message ?: i18n.translate("Failed to submit report"),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    isSubmittingAccountReport = false
                }
            },
            onDismiss = { showReportAccountDialog = false },
        )
    }

    pendingExternalMismatch?.let { (disp, uri) ->
        LinkMismatchWarningDialog(
            displayText = disp,
            targetUri = uri,
            onConfirm = {
                pendingExternalMismatch = null
                LinkNavigationHelper.openUrl(
                    context = context,
                    rawUrl = uri,
                    useLinkRedirects = settings.infrastructure.useLinkRedirects,
                    openBskyLinksInCaelum = settings.openBskyLinksInCaelum,
                    onInternalRoute = onNavigate,
                )
            },
            onDismiss = { pendingExternalMismatch = null },
        )
    }
}

@Composable
private fun ProfileActivitySubscriptionDialog(
    profileDid: String,
    currentSubscription: ActivitySubscription,
    onDismiss: () -> Unit,
    onSaved: (ActivitySubscription) -> Unit,
) {
    val container = LocalAppContainer.current
    val scope = rememberAccountCoroutineScope()
    val i18n = LocalI18n.current
    var subscription by remember(profileDid, currentSubscription) { mutableStateOf(currentSubscription) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(tr("Keep me posted")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Get notified of this account’s activity"))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = subscription.post, enabled = !saving, role = Role.Checkbox) { enabled ->
                            subscription = subscription.copy(post = enabled, reply = enabled && subscription.reply)
                        }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(tr("Posts"))
                    Checkbox(checked = subscription.post, onCheckedChange = null, enabled = !saving)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = subscription.reply, enabled = !saving, role = Role.Checkbox) { enabled ->
                            subscription = subscription.copy(post = enabled || subscription.post, reply = enabled)
                        }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(tr("Replies"))
                    Checkbox(checked = subscription.reply, onCheckedChange = null, enabled = !saving)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving && subscription != currentSubscription,
                onClick = {
                    val selected = subscription
                    saving = true
                    error = null
                    scope.launch {
                        runCatching { container.api.putActivitySubscription(profileDid, selected) }
                            .onSuccess { onSaved(selected) }
                            .onFailure {
                                if (it is CancellationException) throw it
                                error = it.message ?: i18n.translate("Could not save changes")
                            }
                        saving = false
                    }
                },
            ) {
                Text(tr("Save changes"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text(tr("Cancel")) }
        },
    )
}

@Composable
private fun EmptyTabPlaceholder(message: String) {
    val palette = LocalAlfPalette.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.contrast600.toComposeColor(),
        )
    }
}

@Composable
private fun FooterLoader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
    }
}
