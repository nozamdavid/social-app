package am.noz.caelum.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import am.noz.caelum.core.i18n.LocalI18n
import am.noz.caelum.core.i18n.tr
import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.model.PostView
import am.noz.caelum.core.storage.AppSettingsSnapshot
import am.noz.caelum.di.AppContainer
import am.noz.caelum.ui.auth.AccountSwitcherSheet
import am.noz.caelum.ui.auth.SignInScreen
import am.noz.caelum.ui.auth.SignupOnboardingScreen
import am.noz.caelum.ui.auth.WelcomeScreen
import am.noz.caelum.ui.components.CaelumAvatar
import am.noz.caelum.ui.components.CaelumLogoMark
import am.noz.caelum.ui.components.LocalAppContainer
import am.noz.caelum.ui.components.LocalScreenStateCache
import am.noz.caelum.ui.components.LocalHideVerificationBadges
import am.noz.caelum.ui.components.LocalNavigate
import am.noz.caelum.ui.components.LocalOpenDrawer
import am.noz.caelum.ui.components.ShellIcons
import am.noz.caelum.ui.components.rememberHaptics
import am.noz.caelum.ui.composer.ComposerSheet
import am.noz.caelum.ui.composer.EditPostDialog
import am.noz.caelum.ui.home.HomeFeedScreen
import am.noz.caelum.ui.lists.CustomFeedScreen
import am.noz.caelum.ui.lists.FeedsDiscoveryScreen
import am.noz.caelum.ui.lists.InviteScannerScreen
import am.noz.caelum.ui.lists.ListsScreen
import am.noz.caelum.ui.lists.ProfileListScreen
import am.noz.caelum.ui.lists.SavedFeedsScreen
import am.noz.caelum.ui.lists.StarterPackScreen
import am.noz.caelum.ui.lists.StarterPackWizardScreen
import am.noz.caelum.ui.navigation.AppRoute
import am.noz.caelum.ui.navigation.DeepLinkTarget
import am.noz.caelum.ui.navigation.PrimaryTabButton
import am.noz.caelum.ui.navigation.RootTab
import am.noz.caelum.ui.openin.OpenInBottomSheet
import am.noz.caelum.ui.openin.OpenInSubject
import am.noz.caelum.ui.post.EmbedImageItem
import am.noz.caelum.ui.post.LightboxMedia
import am.noz.caelum.ui.post.LightboxOverlay
import am.noz.caelum.ui.profile.ActorListMode
import am.noz.caelum.ui.profile.ActorListScreen
import am.noz.caelum.ui.profile.PostQuotesScreen
import am.noz.caelum.ui.profile.ProfileScreen
import am.noz.caelum.ui.profile.ProfileSearchScreen
import am.noz.caelum.ui.search.AtmosphereScreen
import am.noz.caelum.ui.search.HashtagScreen
import am.noz.caelum.ui.search.SearchExploreScreen
import am.noz.caelum.ui.search.TopicScreen
import am.noz.caelum.ui.search.VideoFeedScreen
import am.noz.caelum.ui.settings.SettingsRouterScreen
import am.noz.caelum.ui.social.BookmarksScreen
import am.noz.caelum.ui.social.MessagesConversationScreen
import am.noz.caelum.ui.social.MessagesConversationSettingsScreen
import am.noz.caelum.ui.social.MessagesInboxScreen
import am.noz.caelum.ui.social.MessagesJoinRequestsScreen
import am.noz.caelum.ui.social.MessagesListScreen
import am.noz.caelum.ui.social.MessagesSettingsScreen
import am.noz.caelum.ui.social.SendPostViaChatScreen
import am.noz.caelum.ui.social.ModerationHubScreen
import am.noz.caelum.ui.social.NotificationsActivityListScreen
import am.noz.caelum.ui.social.NotificationsScreen
import am.noz.caelum.ui.social.ReportPostOrActorDialog
import am.noz.caelum.ui.theme.CaelumTheme
import am.noz.caelum.ui.theme.LocalAlfPalette
import am.noz.caelum.ui.theme.ThemeEngine
import am.noz.caelum.ui.theme.toComposeColor
import am.noz.caelum.ui.thread.PostThreadScreen
import am.noz.caelum.core.model.publishedReplyViews
import coil3.compose.setSingletonImageLoaderFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

private enum class AuthOverlayMode {
    None,
    Welcome,
    SignIn,
    Signup,
}

private data class ComposerRequest(
    val initialText: String = "",
    val initialSharedUris: List<Uri> = emptyList(),
    val replyTo: PostView? = null,
    val quotePost: PostView? = null,
)

@Serializable
private data class SavedNavState(
    val activeTabName: String = RootTab.HomeTab.name,
    val stacks: Map<String, List<AppRoute>> = emptyMap(),
)

private val PRIMARY_TABS = listOf(
    RootTab.HomeTab,
    RootTab.SearchTab,
    RootTab.MessagesTab,
    RootTab.NotificationsTab,
    RootTab.MyProfileTab,
)

private fun defaultNavState(): SavedNavState = SavedNavState(
    activeTabName = RootTab.HomeTab.name,
    stacks = PRIMARY_TABS.associate { it.name to listOf(it.route) },
)

private fun SavedNavState.normalized(): SavedNavState {
    val validTab = RootTab.entries.firstOrNull { it.name == activeTabName }
        ?.takeIf { it in PRIMARY_TABS }
        ?: RootTab.HomeTab
    val fixedStacks = PRIMARY_TABS.associate { tab ->
        val existing = stacks[tab.name].orEmpty()
        tab.name to (existing.ifEmpty { listOf(tab.route) })
    }
    return SavedNavState(activeTabName = validTab.name, stacks = fixedStacks)
}

private val NavStateSaver: Saver<SavedNavState, String> = Saver(
    save = { state -> AtProtoJson.encodeToString(SavedNavState.serializer(), state) },
    restore = { json ->
        runCatching { AtProtoJson.decodeFromString(SavedNavState.serializer(), json).normalized() }
            .getOrElse { defaultNavState() }
    },
)

private data class PrimaryTabVisual(
    val tab: RootTab,
    val label: String,
    val testTag: String,
    val selectedIcon: ImageVector? = null,
    val unselectedIcon: ImageVector? = null,
    val iconSize: Dp = 26.dp,
    val badgeCount: Int = 0,
    val onLongClick: (() -> Unit)? = null,
    val onLongClickLabel: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaelumRootApp(
    container: AppContainer,
    initialDeepLink: DeepLinkTarget? = null,
    initialSharedText: String? = null,
    initialSharedMedia: List<Uri> = emptyList(),
    liveComposeEvents: Flow<DeepLinkTarget.ComposeIntentTarget>? = null,
) {
    setSingletonImageLoaderFactory { container.imageLoader }

    val settings by container.preferences.settingsFlow.collectAsState(initial = AppSettingsSnapshot())
    val session by container.sessionManager.state.collectAsState()
    val hideVerificationBadges = key(session.currentDid) {
        val did = session.currentDid
        if (did == null) {
            false
        } else {
            val preference = remember(did, container) {
                container.preferences.observeModerationSettings(did)
                    .map { it.hideVerificationBadges }
                    .distinctUntilChanged()
            }
            preference.collectAsState(initial = false).value
        }
    }
    val i18nCatalog by container.i18nCatalog.collectAsState()
    val scope = rememberCoroutineScope()

    var guestBrowsing by rememberSaveable { mutableStateOf(false) }
    var authOverlayMode by rememberSaveable { mutableStateOf(AuthOverlayMode.None) }
    var navState by rememberSaveable(stateSaver = NavStateSaver) { mutableStateOf(defaultNavState()) }
    var initialIntentConsumed by rememberSaveable { mutableStateOf(false) }

    // Global modal/sheet/overlay state
    var pendingComposeRequest by remember { mutableStateOf<ComposerRequest?>(null) }
    var composerRequest by remember { mutableStateOf<ComposerRequest?>(null) }
    var editingPost by remember { mutableStateOf<PostView?>(null) }
    var openInSubject by remember { mutableStateOf<OpenInSubject?>(null) }
    var reportingPost by remember { mutableStateOf<PostView?>(null) }
    var showAccountSwitcher by remember { mutableStateOf(false) }
    var lightboxMedia by remember { mutableStateOf<LightboxMedia?>(null) }

    var showChatTabActions by remember(session.activation) { mutableStateOf(false) }
    var updatingChatReadState by remember(session.activation) { mutableStateOf(false) }
    val homeReselectEvents = remember(session.activation) { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    val markAllReadEvents = remember(session.activation) { MutableSharedFlow<String>(extraBufferCapacity = 1) }
    val screenCache = remember(session.activation) { container.screenCacheFor(session.activation) }
    val saveableStateHolder = key(session.currentDid, session.activation) { rememberSaveableStateHolder() }

    fun routeStateKey(tab: RootTab, index: Int, route: AppRoute): String =
        "${tab.name}:$index:$route"

    val activeTab = remember(navState.activeTabName) {
        RootTab.entries.firstOrNull { it.name == navState.activeTabName }
            ?.takeIf { it in PRIMARY_TABS }
            ?: RootTab.HomeTab
    }
    val currentStack = navState.stacks[activeTab.name].orEmpty().ifEmpty { listOf(activeTab.route) }
    val currentRoute = currentStack.last()
    val currentRouteKey = routeStateKey(activeTab, currentStack.lastIndex, currentRoute)

    fun clearChildStatesAbove(tab: RootTab, stack: List<AppRoute>, keepCount: Int = 1) {
        for (idx in keepCount until stack.size) {
            saveableStateHolder.removeState(routeStateKey(tab, idx, stack[idx]))
        }
    }

    fun selectTab(tab: RootTab) {
        val targetTab = if (tab in PRIMARY_TABS) tab else RootTab.HomeTab
        val currentMap = navState.stacks.toMutableMap()
        if (activeTab == targetTab) {
            val existing = currentMap[targetTab.name].orEmpty()
            if (existing.size <= 1 && targetTab == RootTab.HomeTab) {
                homeReselectEvents.tryEmit(Unit)
                return
            }
            clearChildStatesAbove(targetTab, existing, keepCount = 1)
            currentMap[targetTab.name] = listOf(targetTab.route)
            navState = navState.copy(activeTabName = targetTab.name, stacks = currentMap)
        } else {
            if (currentMap[targetTab.name].isNullOrEmpty()) {
                currentMap[targetTab.name] = listOf(targetTab.route)
            }
            navState = navState.copy(activeTabName = targetTab.name, stacks = currentMap)
        }
    }

    fun navigateTo(route: AppRoute, explicitTargetTab: RootTab? = null) {
        val currentMap = navState.stacks.toMutableMap()
        val resolvedPrimaryTab: RootTab? = when {
            explicitTargetTab in PRIMARY_TABS -> explicitTargetTab
            route == AppRoute.Home -> RootTab.HomeTab
            route is AppRoute.Search && route.query == null -> RootTab.SearchTab
            route == AppRoute.Messages -> RootTab.MessagesTab
            route == AppRoute.Notifications -> RootTab.NotificationsTab
            route == AppRoute.MyProfile -> RootTab.MyProfileTab
            route is AppRoute.Search -> RootTab.SearchTab
            else -> null
        }

        if (resolvedPrimaryTab != null) {
            val baseStack = currentMap[resolvedPrimaryTab.name].orEmpty().ifEmpty { listOf(resolvedPrimaryTab.route) }
            val updatedStack = when {
                route == resolvedPrimaryTab.route -> {
                    clearChildStatesAbove(resolvedPrimaryTab, baseStack, keepCount = 1)
                    listOf(resolvedPrimaryTab.route)
                }
                baseStack.lastOrNull() == route -> baseStack
                else -> baseStack + route
            }
            currentMap[resolvedPrimaryTab.name] = updatedStack
            navState = navState.copy(activeTabName = resolvedPrimaryTab.name, stacks = currentMap)
        } else {
            val stack = currentMap[activeTab.name].orEmpty().ifEmpty { listOf(activeTab.route) }
            if (stack.lastOrNull() != route) {
                currentMap[activeTab.name] = stack + route
                navState = navState.copy(stacks = currentMap)
            }
        }
    }

    fun replaceTopRoute(route: AppRoute) {
        val currentMap = navState.stacks.toMutableMap()
        val stack = currentMap[activeTab.name].orEmpty().ifEmpty { listOf(activeTab.route) }
        if (stack.isNotEmpty()) {
            saveableStateHolder.removeState(routeStateKey(activeTab, stack.lastIndex, stack.last()))
        }
        currentMap[activeTab.name] = stack.dropLast(1).ifEmpty { listOf(activeTab.route) } + route
        navState = navState.copy(stacks = currentMap)
    }

    fun popBack() {
        val currentMap = navState.stacks.toMutableMap()
        val stack = currentMap[activeTab.name].orEmpty().ifEmpty { listOf(activeTab.route) }
        if (stack.size > 1) {
            saveableStateHolder.removeState(routeStateKey(activeTab, stack.lastIndex, stack.last()))
            currentMap[activeTab.name] = stack.dropLast(1)
            navState = navState.copy(stacks = currentMap)
        } else if (activeTab != RootTab.HomeTab) {
            navState = navState.copy(activeTabName = RootTab.HomeTab.name)
        }
    }

    fun handleComposeTarget(target: DeepLinkTarget.ComposeIntentTarget) {
        val uris = buildList {
            target.imageUris.forEach { raw ->
                runCatching { Uri.parse(raw) }.getOrNull()?.let(::add)
            }
            target.videoUri?.let { raw ->
                runCatching { Uri.parse(raw) }.getOrNull()?.let(::add)
            }
        }
        val req = ComposerRequest(
            initialText = target.text.orEmpty(),
            initialSharedUris = uris,
        )
        if (session.hasSession) {
            composerRequest = req
        } else {
            pendingComposeRequest = req
            authOverlayMode = AuthOverlayMode.SignIn
        }
    }

    fun handleDeepLinkTarget(target: DeepLinkTarget) {
        when (target) {
            is DeepLinkTarget.RouteTarget -> {
                if (!session.hasSession && !guestBrowsing && target.route != AppRoute.Home) {
                    guestBrowsing = true
                }
                navigateTo(target.route, target.targetTab)
            }
            is DeepLinkTarget.ComposeIntentTarget -> {
                handleComposeTarget(target)
            }
        }
    }

    // Consume cold-start deep link or share intent once session initialization completes
    LaunchedEffect(session.isInitialized) {
        if (!session.isInitialized || initialIntentConsumed) return@LaunchedEffect
        initialIntentConsumed = true
        when {
            initialDeepLink != null -> handleDeepLinkTarget(initialDeepLink)
            !initialSharedText.isNullOrBlank() || initialSharedMedia.isNotEmpty() -> {
                val req = ComposerRequest(
                    initialText = initialSharedText.orEmpty(),
                    initialSharedUris = initialSharedMedia,
                )
                if (session.hasSession) {
                    composerRequest = req
                } else {
                    pendingComposeRequest = req
                    authOverlayMode = AuthOverlayMode.SignIn
                }
            }
        }
    }

    // Reset navigation stack and global overlays when switching accounts or signing out
    var previousAccountDid by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(session.isInitialized, session.currentDid, session.activation) {
        if (!session.isInitialized) return@LaunchedEffect
        val prev = previousAccountDid
        previousAccountDid = session.currentDid
        if (prev != null && prev != session.currentDid) {
            navState = defaultNavState()
            composerRequest = null
            editingPost = null
            openInSubject = null
            reportingPost = null
            showAccountSwitcher = false
            lightboxMedia = null
        }
    }

    // Flush any compose intent queued while waiting for sign-in
    LaunchedEffect(session.hasSession, pendingComposeRequest, authOverlayMode) {
        val pending = pendingComposeRequest
        if (session.hasSession && pending != null && authOverlayMode != AuthOverlayMode.Signup) {
            composerRequest = pending
            pendingComposeRequest = null
            authOverlayMode = AuthOverlayMode.None
        }
    }

    // Listen for live deep link routes emitted from MainActivity.onNewIntent
    LaunchedEffect(container) {
        container.deepLinkEvents.collect { route ->
            if (!session.hasSession && !guestBrowsing && route != AppRoute.Home) {
                guestBrowsing = true
            }
            val inferredTab = when (route) {
                AppRoute.Home -> RootTab.HomeTab
                is AppRoute.Search -> RootTab.SearchTab
                AppRoute.Messages,
                AppRoute.MessagesInbox,
                AppRoute.MessagesSettings,
                is AppRoute.MessagesConversation,
                is AppRoute.MessagesConversationSettings,
                is AppRoute.MessagesJoinRequests -> RootTab.MessagesTab
                AppRoute.Notifications,
                is AppRoute.NotificationsActivityList -> RootTab.NotificationsTab
                AppRoute.MyProfile -> RootTab.MyProfileTab
                else -> null
            }
            navigateTo(route, inferredTab)
        }
    }

    // Listen for live compose / share intents from MainActivity.onNewIntent
    LaunchedEffect(liveComposeEvents) {
        liveComposeEvents?.collect { target ->
            handleComposeTarget(target)
        }
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    LaunchedEffect(session.activation) {
        if (drawerState.isOpen) drawerState.close()
    }
    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }

    CaelumTheme(
        colorMode = settings.colorMode,
        darkTheme = settings.darkTheme,
        witchskyTheme = settings.witchskySelectedTheme,
        dynamicColorEnabled = settings.dynamicColorEnabled,
        fontFamilySetting = settings.fontFamily,
        fontScaleSetting = settings.fontScale,
        reducedMotion = settings.reducedMotion,
    ) {
        CompositionLocalProvider(
            LocalAppContainer provides container,
            LocalScreenStateCache provides screenCache,
            LocalHideVerificationBadges provides hideVerificationBadges,
            LocalI18n provides i18nCatalog,
            LocalNavigate provides { route -> navigateTo(route) },
            LocalOpenDrawer provides openDrawer,
        ) {
            val palette = LocalAlfPalette.current

            if (!session.isInitialized) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(palette.contrast0.toComposeColor()),
                    contentAlignment = Alignment.Center,
                ) {
                    CaelumLogoMark(size = 56.dp, useDarkVariant = palette.isDark)
                }
                return@CompositionLocalProvider
            }

            if (authOverlayMode == AuthOverlayMode.Signup) {
                SignupOnboardingScreen(
                    onBack = {
                        authOverlayMode = if (session.hasSession || guestBrowsing) {
                            AuthOverlayMode.None
                        } else {
                            AuthOverlayMode.Welcome
                        }
                    },
                    onCompleted = { authOverlayMode = AuthOverlayMode.None },
                )
                return@CompositionLocalProvider
            }

            if (!session.hasSession && !guestBrowsing) {
                when (authOverlayMode) {
                    AuthOverlayMode.SignIn -> {
                        BackHandler { authOverlayMode = AuthOverlayMode.Welcome }
                        SignInScreen(
                            onBack = { authOverlayMode = AuthOverlayMode.Welcome },
                            onSignedIn = { authOverlayMode = AuthOverlayMode.None },
                        )
                    }
                    AuthOverlayMode.None, AuthOverlayMode.Welcome -> {
                        WelcomeScreen(
                            onCreateAccount = { authOverlayMode = AuthOverlayMode.Signup },
                            onSignIn = { authOverlayMode = AuthOverlayMode.SignIn },
                            onBrowseGuest = {
                                guestBrowsing = true
                                authOverlayMode = AuthOverlayMode.None
                            },
                        )
                    }
                    AuthOverlayMode.Signup -> Unit
                }
                return@CompositionLocalProvider
            }

            // Adding another account (or signing in from guest mode) overlays cleanly over the shell
            if (authOverlayMode == AuthOverlayMode.SignIn) {
                BackHandler { authOverlayMode = AuthOverlayMode.None }
                SignInScreen(
                    onBack = { authOverlayMode = AuthOverlayMode.None },
                    onSignedIn = { authOverlayMode = AuthOverlayMode.None },
                )
                return@CompositionLocalProvider
            }

            // Navigation back handling: close drawer first, otherwise pop stack / return to HomeTab
            BackHandler(enabled = drawerState.isOpen || currentStack.size > 1 || activeTab != RootTab.HomeTab) {
                if (drawerState.isOpen) {
                    scope.launch { drawerState.close() }
                } else {
                    popBack()
                }
            }

            val openComposer: (PostView?, PostView?) -> Unit = { replyTo, quote ->
                if (!session.hasSession) {
                    authOverlayMode = AuthOverlayMode.SignIn
                } else {
                    composerRequest = ComposerRequest(replyTo = replyTo, quotePost = quote)
                }
            }
            val openEditPost: (PostView) -> Unit = { post -> editingPost = post }
            val openIn: (OpenInSubject) -> Unit = { subject -> openInSubject = subject }
            val openReport: (PostView) -> Unit = { post ->
                if (!session.hasSession) {
                    authOverlayMode = AuthOverlayMode.SignIn
                } else {
                    reportingPost = post
                }
            }
            val openLightbox: (List<EmbedImageItem>, Int) -> Unit = { items, index ->
                lightboxMedia = LightboxMedia.Images(items, index)
            }
            val openVideoLightbox: (String, String?, String?) -> Unit = { playlistUrl, thumbUrl, alt ->
                lightboxMedia = LightboxMedia.Video(playlistUrl, thumbUrl, alt)
            }

            val isRootChromeVisible = when (currentRoute) {
                is AppRoute.VideoFeed,
                is AppRoute.MessagesConversation,
                is AppRoute.SendPostViaChat,
                AppRoute.InviteScanner,
                AppRoute.StarterPackWizard,
                is AppRoute.StarterPackEdit,
                AppRoute.FindContactsFlow -> false
                AppRoute.Home,
                is AppRoute.Search,
                AppRoute.Messages,
                AppRoute.Notifications,
                AppRoute.MyProfile,
                AppRoute.Atmosphere,
                AppRoute.Feeds,
                AppRoute.Lists,
                AppRoute.Bookmarks,
                AppRoute.Settings -> true
                else -> session.hasSession
            }
            val isInsideDrawerDestination = currentStack.any {
                when (it) {
                    AppRoute.Atmosphere,
                    AppRoute.Feeds,
                    AppRoute.Lists,
                    AppRoute.Bookmarks,
                    AppRoute.Settings -> true
                    else -> false
                }
            }

            val haptics = rememberHaptics(settings.disableHaptics)
            val snackbarHostState = remember(session.activation) { SnackbarHostState() }
            val tabItems = listOf(
                PrimaryTabVisual(
                    tab = RootTab.HomeTab,
                    label = tr("Home"),
                    selectedIcon = ShellIcons.HomeSelected,
                    unselectedIcon = ShellIcons.Home,
                    iconSize = 29.dp,
                    testTag = "bottom_nav_home",
                ),
                PrimaryTabVisual(
                    tab = RootTab.SearchTab,
                    label = tr("Search"),
                    selectedIcon = ShellIcons.SearchSelected,
                    unselectedIcon = ShellIcons.Search,
                    iconSize = 30.dp,
                    testTag = "bottom_nav_search",
                ),
                PrimaryTabVisual(
                    tab = RootTab.MessagesTab,
                    label = tr("Chat"),
                    selectedIcon = ShellIcons.ChatSelected,
                    unselectedIcon = ShellIcons.Chat,
                    iconSize = 27.dp,
                    testTag = "bottom_nav_messages",
                    badgeCount = session.unreadChatCount,
                    onLongClick = if (session.hasSession && !updatingChatReadState) {
                        {
                            haptics.longPress()
                            showChatTabActions = true
                        }
                    } else null,
                    onLongClickLabel = tr("Chat options"),
                ),
                PrimaryTabVisual(
                    tab = RootTab.NotificationsTab,
                    label = tr("Notifications"),
                    selectedIcon = ShellIcons.NotificationsSelected,
                    unselectedIcon = ShellIcons.Notifications,
                    iconSize = 28.dp,
                    testTag = "bottom_nav_notifications",
                    badgeCount = session.unreadNotificationsCount,
                ),
                PrimaryTabVisual(
                    tab = RootTab.MyProfileTab,
                    label = tr("Profile"),
                    testTag = "bottom_nav_profile",
                    onLongClick = if (session.hasSession) {
                        {
                            haptics.longPress()
                            showAccountSwitcher = true
                        }
                    } else null,
                    onLongClickLabel = tr("Switch account"),
                ),
            )

            val isTabletWidth = LocalConfiguration.current.screenWidthDp >= 600
            val density = LocalDensity.current
            val showBottomBar = !isTabletWidth && isRootChromeVisible &&
                WindowInsets.ime.getBottom(density) <= WindowInsets.navigationBars.getBottom(density)
            val drawerScrimColor = Color(ThemeEngine.getDrawerOverlayArgb(palette))

            @Composable
            fun TabButton(item: PrimaryTabVisual, modifier: Modifier = Modifier) {
                val selected = activeTab == item.tab && !isInsideDrawerDestination
                PrimaryTabButton(
                    label = item.label,
                    icon = if (selected) item.selectedIcon else item.unselectedIcon,
                    iconSize = item.iconSize,
                    avatarUrl = session.currentAccount?.avatar,
                    selected = selected,
                    unreadCount = item.badgeCount,
                    onClick = { selectTab(item.tab) },
                    onLongClick = item.onLongClick,
                    onLongClickLabel = item.onLongClickLabel,
                    modifier = modifier.testTag(item.testTag),
                )
            }

            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = currentStack.size == 1,
                scrimColor = drawerScrimColor,
                drawerContent = {
                    CaelumDrawerContent(
                        currentRoute = currentRoute,
                        unreadNotificationsCount = session.unreadNotificationsCount,
                        unreadChatCount = session.unreadChatCount,
                        onSelectPrimaryTab = { tab ->
                            scope.launch { drawerState.close() }
                            selectTab(tab)
                        },
                        onOpenDrawerRoute = { route ->
                            scope.launch { drawerState.close() }
                            navigateTo(route)
                        },
                        onOpenAccountSwitcher = {
                            scope.launch { drawerState.close() }
                            showAccountSwitcher = true
                        },
                        onSignIn = {
                            scope.launch { drawerState.close() }
                            authOverlayMode = AuthOverlayMode.SignIn
                        },
                        onCreateAccount = {
                            scope.launch { drawerState.close() }
                            authOverlayMode = AuthOverlayMode.Signup
                        },
                    )
                },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(palette.contrast0.toComposeColor()),
                ) {
                    if (isTabletWidth && isRootChromeVisible) {
                        NavigationRail(
                            containerColor = palette.contrast25.toComposeColor(),
                            contentColor = palette.contrast1000.toComposeColor(),
                            modifier = Modifier.fillMaxHeight(),
                        ) {
                            Column(
                                modifier = Modifier.verticalScroll(rememberScrollState()).selectableGroup(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Spacer(Modifier.height(12.dp))
                                IconButton(onClick = openDrawer) {
                                    Icon(Icons.Filled.Menu, contentDescription = tr("Menu"))
                                }
                                Spacer(Modifier.height(8.dp))
                                for (item in tabItems) {
                                    TabButton(item, Modifier.width(80.dp))
                                }
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        val contentModifier = if (showBottomBar) {
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .consumeWindowInsets(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                        } else {
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        }

                        Box(modifier = contentModifier) {
                            key(session.activation, currentRouteKey) {
                                saveableStateHolder.SaveableStateProvider(currentRouteKey) {
                                    CaelumRouteHost(
                                        route = currentRoute,
                                        hasSession = session.hasSession,
                                        currentDid = session.currentDid,
                                        currentHandle = session.currentAccount?.handle,
                                        homeReselectEvents = homeReselectEvents,
                                        markAllReadEvents = markAllReadEvents,
                                        onBack = ::popBack,
                                        onGoHome = {
                                            clearChildStatesAbove(
                                                RootTab.HomeTab,
                                                navState.stacks[RootTab.HomeTab.name].orEmpty(),
                                                keepCount = 1,
                                            )
                                            navState = navState.copy(
                                                activeTabName = RootTab.HomeTab.name,
                                                stacks = navState.stacks + (RootTab.HomeTab.name to listOf(AppRoute.Home)),
                                            )
                                        },
                                        onOpenDrawer = openDrawer,
                                        onNavigate = { dest -> navigateTo(dest) },
                                        onReplaceTop = ::replaceTopRoute,
                                        onOpenComposer = openComposer,
                                        onOpenEditPost = openEditPost,
                                        onOpenIn = openIn,
                                        onOpenReport = openReport,
                                        onOpenAccountSwitcher = { showAccountSwitcher = true },
                                        onAddAccount = { authOverlayMode = AuthOverlayMode.SignIn },
                                        onCreateAccount = { authOverlayMode = AuthOverlayMode.Signup },
                                        onOpenLightbox = openLightbox,
                                        onOpenVideoLightbox = openVideoLightbox,
                                    )
                                }
                            }
                            SnackbarHost(
                                hostState = snackbarHostState,
                                modifier = Modifier.align(Alignment.BottomCenter),
                            )
                        }

                        if (showBottomBar) {
                            val bottomPadding = WindowInsets.navigationBars.asPaddingValues()
                                .calculateBottomPadding().coerceAtLeast(15.dp)
                            HorizontalDivider(thickness = Dp.Hairline, color = palette.contrast100.toComposeColor())
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(palette.contrast0.toComposeColor())
                                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                                    .padding(start = 8.dp, end = 8.dp, bottom = bottomPadding)
                                    .selectableGroup(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                for (item in tabItems) {
                                    TabButton(item, Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // Global overlays
            if (showChatTabActions) {
                val chatsRead = tr("Marked all chats as read")
                val requestsRead = tr("Marked all requests as read")
                val chatsFailed = tr("Failed to mark all chats as read")
                val requestsFailed = tr("Failed to mark all requests as read")
                ChatTabActions(
                    onDismiss = { showChatTabActions = false },
                    onMarkRead = { status ->
                        val did = session.currentDid ?: return@ChatTabActions
                        val activation = session.activation
                        updatingChatReadState = true
                        scope.launch {
                            val result = try {
                                runCatching {
                                    withContext(container.sessionManager.bindAccount(did, activation)) {
                                        container.api.updateAllReadConvos(status)
                                    }
                                }.also { if (it.exceptionOrNull() is CancellationException) it.getOrThrow() }
                            } finally {
                                updatingChatReadState = false
                            }
                            if (container.sessionManager.state.value.activation != activation) return@launch

                            val accepted = status == "accepted"
                            if (result.isSuccess) {
                                if (accepted) {
                                    screenCache.messagesConvos = screenCache.messagesConvos?.map { it.copy(unreadCount = 0) }
                                } else {
                                    screenCache.messagesRequestConvos =
                                        screenCache.messagesRequestConvos?.map { it.copy(unreadCount = 0) }
                                }
                                markAllReadEvents.tryEmit(status)
                                withContext(container.sessionManager.bindAccount(did, activation)) {
                                    container.sessionManager.refreshUnreadCounts()
                                }
                                snackbarHostState.showSnackbar(if (accepted) chatsRead else requestsRead)
                            } else {
                                snackbarHostState.showSnackbar(if (accepted) chatsFailed else requestsFailed)
                            }
                        }
                    },
                )
            }
            composerRequest?.let { req ->
                ComposerSheet(
                    initialText = req.initialText,
                    initialSharedUris = req.initialSharedUris,
                    replyTo = req.replyTo,
                    quotePost = req.quotePost,
                    onDismiss = { composerRequest = null },
                    onPostPublished = { writes ->
                        session.currentAccount?.let { account ->
                            val replies = writes.publishedReplyViews(account)
                            if (replies.isNotEmpty()) {
                                screenCache.recordPublishedReplies(replies)
                                scope.launch {
                                    container.sessionManager.runForCurrentAccount(account.did) {
                                        var pending = replies.map { it.uri }
                                        for (wait in listOf(0L, 400L, 1200L, 2400L)) {
                                            delay(wait)
                                            val indexed = try {
                                                container.api.getPosts(pending)
                                            } catch (error: CancellationException) {
                                                throw error
                                            } catch (_: Exception) {
                                                emptyList()
                                            }
                                            screenCache.recordPublishedReplies(indexed)
                                            pending = pending - indexed.map { it.uri }.toSet()
                                            if (pending.isEmpty()) break
                                        }
                                    }
                                }
                            }
                        }
                        composerRequest = null
                    },
                )
            }

            editingPost?.let { post ->
                EditPostDialog(
                    post = post,
                    onDismiss = { editingPost = null },
                    onSaved = { editingPost = null },
                )
            }

            openInSubject?.let { subject ->
                OpenInBottomSheet(
                    subject = subject,
                    onDismiss = { openInSubject = null },
                    onNavigate = { route -> navigateTo(route) },
                )
            }

            reportingPost?.let { post ->
                ReportPostOrActorDialog(
                    post = post,
                    actorDid = null,
                    onDismiss = { reportingPost = null },
                )
            }

            if (showAccountSwitcher) {
                AccountSwitcherSheet(
                    onDismiss = { showAccountSwitcher = false },
                    onAddAccount = {
                        showAccountSwitcher = false
                        authOverlayMode = AuthOverlayMode.SignIn
                    },
                    onSwitchAccount = { did ->
                        container.applicationScope.launch { container.sessionManager.switchAccount(did) }
                    },
                    onRemoveAccount = { did ->
                        container.applicationScope.launch { container.sessionManager.removeAccount(did) }
                    },
                )
            }

            lightboxMedia?.let { media ->
                LightboxOverlay(
                    media = media,
                    onDismiss = { lightboxMedia = null },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTabActions(onDismiss: () -> Unit, onMarkRead: (String) -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }

    fun markRead(status: String) {
        if (closing) return
        closing = true
        scope.launch {
            sheet.hide()
            onDismiss()
            onMarkRead(status)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        ListItem(
            headlineContent = { Text(tr("Mark all chats as read")) },
            modifier = Modifier.clickable(enabled = !closing) { markRead("accepted") },
        )
        ListItem(
            headlineContent = { Text(tr("Mark all requests as read")) },
            modifier = Modifier.clickable(enabled = !closing) { markRead("request") },
        )
    }
}

@Composable
private fun CaelumDrawerContent(
    currentRoute: AppRoute,
    unreadNotificationsCount: Int,
    unreadChatCount: Int,
    onSelectPrimaryTab: (RootTab) -> Unit,
    onOpenDrawerRoute: (AppRoute) -> Unit,
    onOpenAccountSwitcher: () -> Unit,
    onSignIn: () -> Unit,
    onCreateAccount: () -> Unit,
) {
    val container = LocalAppContainer.current
    val palette = LocalAlfPalette.current
    val session by container.sessionManager.state.collectAsState()
    val account = session.currentAccount

    ModalDrawerSheet(
        drawerContainerColor = palette.contrast0.toComposeColor(),
        drawerContentColor = palette.contrast1000.toComposeColor(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
        ) {
            if (session.hasSession && account != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CaelumAvatar(
                            url = account.avatar,
                            contentDescription = account.handle,
                            size = 52.dp,
                            onClick = { onSelectPrimaryTab(RootTab.MyProfileTab) },
                        )
                        IconButton(
                            onClick = onOpenAccountSwitcher,
                            modifier = Modifier.testTag("drawer_account_switcher"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SwapHoriz,
                                contentDescription = tr("Switch account"),
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectPrimaryTab(RootTab.MyProfileTab) },
                    ) {
                        Text(
                            text = account.displayName?.takeIf { it.isNotBlank() } ?: account.handle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "@${account.handle}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.contrast600.toComposeColor(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CaelumLogoMark(size = 34.dp, useDarkVariant = palette.isDark)
                        Text(
                            text = "Caelum",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        text = tr("Join the conversation"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.contrast700.toComposeColor(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onCreateAccount, modifier = Modifier.weight(1f)) {
                            Text(tr("Create account"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(onClick = onSignIn, modifier = Modifier.weight(1f)) {
                            Text(tr("Sign in"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            HorizontalDivider(
                color = palette.contrast100.toComposeColor(),
                modifier = Modifier.padding(vertical = 8.dp),
            )

            val itemColors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = palette.primary50.toComposeColor(),
                selectedIconColor = palette.primary500.toComposeColor(),
                selectedTextColor = palette.contrast1000.toComposeColor(),
                unselectedIconColor = palette.contrast700.toComposeColor(),
                unselectedTextColor = palette.contrast900.toComposeColor(),
            )

            @Composable
            fun DrawerNavRow(
                label: String,
                icon: ImageVector,
                selected: Boolean,
                badgeCount: Int = 0,
                onClick: () -> Unit,
            ) {
                NavigationDrawerItem(
                    label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    icon = { Icon(icon, contentDescription = null) },
                    badge = if (badgeCount > 0) {
                        {
                            Badge {
                                Text(if (badgeCount > 99) "99+" else badgeCount.toString())
                            }
                        }
                    } else null,
                    selected = selected,
                    onClick = onClick,
                    colors = itemColors,
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }

            DrawerNavRow(
                label = tr("Search"),
                icon = Icons.Filled.Search,
                selected = currentRoute is AppRoute.Search,
                onClick = { onSelectPrimaryTab(RootTab.SearchTab) },
            )
            DrawerNavRow(
                label = tr("Home"),
                icon = Icons.Filled.Home,
                selected = currentRoute == AppRoute.Home,
                onClick = { onSelectPrimaryTab(RootTab.HomeTab) },
            )
            DrawerNavRow(
                label = tr("Notifications"),
                icon = Icons.Filled.Notifications,
                selected = currentRoute == AppRoute.Notifications,
                badgeCount = unreadNotificationsCount,
                onClick = { onSelectPrimaryTab(RootTab.NotificationsTab) },
            )
            DrawerNavRow(
                label = tr("Chat"),
                icon = Icons.AutoMirrored.Filled.Chat,
                selected = currentRoute == AppRoute.Messages,
                badgeCount = unreadChatCount,
                onClick = { onSelectPrimaryTab(RootTab.MessagesTab) },
            )
            DrawerNavRow(
                label = tr("Feeds"),
                icon = Icons.Filled.Tag,
                selected = currentRoute == AppRoute.Feeds,
                onClick = { onOpenDrawerRoute(AppRoute.Feeds) },
            )
            DrawerNavRow(
                label = tr("Lists"),
                icon = Icons.AutoMirrored.Filled.List,
                selected = currentRoute == AppRoute.Lists,
                onClick = { onOpenDrawerRoute(AppRoute.Lists) },
            )
            DrawerNavRow(
                label = tr("Saved"),
                icon = Icons.Filled.Bookmark,
                selected = currentRoute == AppRoute.Bookmarks,
                onClick = { onOpenDrawerRoute(AppRoute.Bookmarks) },
            )
            DrawerNavRow(
                label = "Atmosphere",
                icon = Icons.Filled.Public,
                selected = currentRoute == AppRoute.Atmosphere,
                onClick = { onOpenDrawerRoute(AppRoute.Atmosphere) },
            )
            DrawerNavRow(
                label = tr("Profile"),
                icon = Icons.Filled.Person,
                selected = currentRoute == AppRoute.MyProfile,
                onClick = { onSelectPrimaryTab(RootTab.MyProfileTab) },
            )
            DrawerNavRow(
                label = tr("Settings"),
                icon = Icons.Filled.Settings,
                selected = currentRoute == AppRoute.Settings,
                onClick = { onOpenDrawerRoute(AppRoute.Settings) },
            )
        }
    }
}

@Composable
private fun CaelumRouteHost(
    route: AppRoute,
    hasSession: Boolean,
    currentDid: String?,
    currentHandle: String?,
    homeReselectEvents: Flow<Unit>,
    markAllReadEvents: Flow<String>,
    onBack: () -> Unit,
    onGoHome: () -> Unit,
    onOpenDrawer: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
    onReplaceTop: (AppRoute) -> Unit,
    onOpenComposer: (replyTo: PostView?, quote: PostView?) -> Unit,
    onOpenEditPost: (PostView) -> Unit,
    onOpenIn: (OpenInSubject) -> Unit,
    onOpenReport: (PostView) -> Unit,
    onOpenAccountSwitcher: () -> Unit,
    onAddAccount: () -> Unit,
    onCreateAccount: () -> Unit,
    onOpenLightbox: (List<EmbedImageItem>, Int) -> Unit,
    onOpenVideoLightbox: (String, String?, String?) -> Unit,
) {
    when (route) {
        // Primary 5 tabs
        AppRoute.Home -> HomeFeedScreen(
            reselectEvents = homeReselectEvents,
            onOpenDrawer = onOpenDrawer,
            onNavigate = onNavigate,
            onOpenComposer = onOpenComposer,
            onOpenEditPost = onOpenEditPost,
            onOpenIn = onOpenIn,
            onOpenReport = onOpenReport,
            onOpenLightbox = onOpenLightbox,
            onOpenVideoLightbox = onOpenVideoLightbox,
        )

        is AppRoute.Search -> SearchExploreScreen(
            initialQuery = route.query,
            initialFilters = route.filters,
            onOpenDrawer = onOpenDrawer,
            onNavigate = onNavigate,
            onOpenComposer = onOpenComposer,
            onOpenEditPost = onOpenEditPost,
            onOpenIn = onOpenIn,
            onOpenReport = onOpenReport,
            onOpenLightbox = onOpenLightbox,
            onOpenVideoLightbox = onOpenVideoLightbox,
        )

        AppRoute.Messages -> if (hasSession) {
            MessagesListScreen(
                onOpenDrawer = onOpenDrawer,
                onNavigate = onNavigate,
                markAllReadEvents = markAllReadEvents,
            )
        } else {
            GuestAuthRequiredScreen(
                title = tr("Chat"),
                message = "Sign in to read and send direct messages.",
                onOpenDrawer = onOpenDrawer,
                onSignIn = onAddAccount,
                onCreateAccount = onCreateAccount,
            )
        }

        AppRoute.Notifications -> if (hasSession) {
            NotificationsScreen(
                onOpenDrawer = onOpenDrawer,
                onNavigate = onNavigate,
                onOpenComposer = onOpenComposer,
                onOpenEditPost = onOpenEditPost,
                onOpenIn = onOpenIn,
                onOpenReport = onOpenReport,
                onOpenLightbox = onOpenLightbox,
                onOpenVideoLightbox = onOpenVideoLightbox,
            )
        } else {
            GuestAuthRequiredScreen(
                title = tr("Notifications"),
                message = "Sign in to view your notifications and activity.",
                onOpenDrawer = onOpenDrawer,
                onSignIn = onAddAccount,
                onCreateAccount = onCreateAccount,
            )
        }

        AppRoute.MyProfile -> {
            val ownActor = currentDid ?: currentHandle
            if (hasSession && !ownActor.isNullOrBlank()) {
                ProfileScreen(
                    actorHandleOrDid = ownActor,
                    isOwnProfileTab = true,
                    onBack = null,
                    onOpenDrawer = onOpenDrawer,
                    onNavigate = onNavigate,
                    onOpenComposer = onOpenComposer,
                    onOpenEditPost = onOpenEditPost,
                    onOpenIn = onOpenIn,
                    onOpenReport = onOpenReport,
                    onOpenAccountSwitcher = onOpenAccountSwitcher,
                    onOpenLightbox = onOpenLightbox,
                    onOpenVideoLightbox = onOpenVideoLightbox,
                )
            } else {
                GuestAuthRequiredScreen(
                    title = tr("Profile"),
                    message = "Sign in to view and manage your profile.",
                    onOpenDrawer = onOpenDrawer,
                    onSignIn = onAddAccount,
                    onCreateAccount = onCreateAccount,
                )
            }
        }

        // Secondary 5 drawer tabs
        AppRoute.Atmosphere -> AtmosphereScreen(
            onOpenDrawer = onOpenDrawer,
            onNavigate = onNavigate,
        )

        AppRoute.Feeds -> FeedsDiscoveryScreen(
            onOpenDrawer = onOpenDrawer,
            onNavigate = onNavigate,
        )

        AppRoute.Lists -> ListsScreen(
            onOpenDrawer = onOpenDrawer,
            onNavigate = onNavigate,
        )

        AppRoute.Bookmarks -> if (hasSession) {
            BookmarksScreen(
                onOpenDrawer = onOpenDrawer,
                onNavigate = onNavigate,
                onOpenComposer = onOpenComposer,
                onOpenEditPost = onOpenEditPost,
                onOpenIn = onOpenIn,
                onOpenReport = onOpenReport,
                onOpenLightbox = onOpenLightbox,
                onOpenVideoLightbox = onOpenVideoLightbox,
            )
        } else {
            GuestAuthRequiredScreen(
                title = tr("Saved"),
                message = "Sign in to view your bookmarked posts.",
                onOpenDrawer = onOpenDrawer,
                onSignIn = onAddAccount,
                onCreateAccount = onCreateAccount,
            )
        }

        // Profiles, posts, threads, feeds, lists
        is AppRoute.Profile -> ProfileScreen(
            actorHandleOrDid = route.name,
            isOwnProfileTab = false,
            onBack = onBack,
            onOpenDrawer = onOpenDrawer,
            onNavigate = onNavigate,
            onOpenComposer = onOpenComposer,
            onOpenEditPost = onOpenEditPost,
            onOpenIn = onOpenIn,
            onOpenReport = onOpenReport,
            onOpenAccountSwitcher = onOpenAccountSwitcher,
            onOpenLightbox = onOpenLightbox,
            onOpenVideoLightbox = onOpenVideoLightbox,
        )

        is AppRoute.ProfileFollowers -> ActorListScreen(
            mode = ActorListMode.Followers(route.name),
            onBack = onBack,
            onNavigate = onNavigate,
        )

        is AppRoute.ProfileFollows -> ActorListScreen(
            mode = ActorListMode.Follows(route.name),
            onBack = onBack,
            onNavigate = onNavigate,
        )

        is AppRoute.ProfileKnownFollowers -> ActorListScreen(
            mode = ActorListMode.KnownFollowers(route.name),
            onBack = onBack,
            onNavigate = onNavigate,
        )

        is AppRoute.ProfileSearch -> ProfileSearchScreen(
            actorHandleOrDid = route.name,
            initialQuery = route.query,
            onBack = onBack,
            onNavigate = onNavigate,
            onOpenComposer = onOpenComposer,
            onOpenEditPost = onOpenEditPost,
            onOpenIn = onOpenIn,
            onOpenReport = onOpenReport,
            onOpenLightbox = onOpenLightbox,
            onOpenVideoLightbox = onOpenVideoLightbox,
        )

        is AppRoute.ProfileList -> ProfileListScreen(
            actorHandleOrDid = route.name,
            rkey = route.rkey,
            onBack = onBack,
            onNavigate = onNavigate,
            onOpenComposer = onOpenComposer,
            onOpenEditPost = onOpenEditPost,
            onOpenIn = onOpenIn,
            onOpenReport = onOpenReport,
            onOpenLightbox = onOpenLightbox,
            onOpenVideoLightbox = onOpenVideoLightbox,
        )

        is AppRoute.ProfileLabelerLikedBy -> ActorListScreen(
            mode = ActorListMode.LabelerLikedBy(route.name),
            onBack = onBack,
            onNavigate = onNavigate,
        )

        is AppRoute.PostThread -> PostThreadScreen(
            actorHandleOrDid = route.name,
            rkey = route.rkey,
            onBack = onBack,
            onNavigate = onNavigate,
            onOpenComposer = onOpenComposer,
            onOpenEditPost = onOpenEditPost,
            onOpenIn = onOpenIn,
            onOpenReport = onOpenReport,
            onOpenLightbox = onOpenLightbox,
            onOpenVideoLightbox = onOpenVideoLightbox,
        )

        is AppRoute.PostLikedBy -> ActorListScreen(
            mode = ActorListMode.PostLikedBy(route.name, route.rkey),
            onBack = onBack,
            onNavigate = onNavigate,
        )

        is AppRoute.PostRepostedBy -> ActorListScreen(
            mode = ActorListMode.PostRepostedBy(route.name, route.rkey),
            onBack = onBack,
            onNavigate = onNavigate,
        )

        is AppRoute.PostQuotes -> PostQuotesScreen(
            actorHandleOrDid = route.name,
            rkey = route.rkey,
            onBack = onBack,
            onNavigate = onNavigate,
            onOpenComposer = onOpenComposer,
            onOpenEditPost = onOpenEditPost,
            onOpenIn = onOpenIn,
            onOpenReport = onOpenReport,
            onOpenLightbox = onOpenLightbox,
            onOpenVideoLightbox = onOpenVideoLightbox,
        )

        is AppRoute.CustomFeed -> CustomFeedScreen(
            actorHandleOrDid = route.name,
            rkey = route.rkey,
            onBack = onBack,
            onNavigate = onNavigate,
            onOpenComposer = onOpenComposer,
            onOpenEditPost = onOpenEditPost,
            onOpenIn = onOpenIn,
            onOpenReport = onOpenReport,
            onOpenLightbox = onOpenLightbox,
            onOpenVideoLightbox = onOpenVideoLightbox,
        )

        is AppRoute.CustomFeedLikedBy -> ActorListScreen(
            mode = ActorListMode.FeedLikedBy(route.name, route.rkey),
            onBack = onBack,
            onNavigate = onNavigate,
        )

        // Explore / tags / video
        is AppRoute.Hashtag -> HashtagScreen(
            tag = route.tag,
            onBack = onBack,
            onNavigate = onNavigate,
            onOpenComposer = onOpenComposer,
            onOpenEditPost = onOpenEditPost,
            onOpenIn = onOpenIn,
            onOpenReport = onOpenReport,
            onOpenLightbox = onOpenLightbox,
            onOpenVideoLightbox = onOpenVideoLightbox,
        )

        is AppRoute.Topic -> TopicScreen(
            topic = route.topic,
            onBack = onBack,
            onNavigate = onNavigate,
            onOpenComposer = onOpenComposer,
            onOpenEditPost = onOpenEditPost,
            onOpenIn = onOpenIn,
            onOpenReport = onOpenReport,
            onOpenLightbox = onOpenLightbox,
            onOpenVideoLightbox = onOpenVideoLightbox,
        )

        is AppRoute.VideoFeed -> VideoFeedScreen(
            sourceUri = route.sourceUri,
            title = route.title,
            onBack = onBack,
            onNavigate = onNavigate,
            onOpenComposer = onOpenComposer,
        )

        // Notifications
        is AppRoute.NotificationsActivityList -> NotificationsActivityListScreen(
            urisCsv = route.urisCsv,
            onBack = onBack,
            onNavigate = onNavigate,
        )

        // Direct Messages
        AppRoute.MessagesSettings -> MessagesSettingsScreen(
            onBack = onBack,
        )

        AppRoute.MessagesInbox -> MessagesInboxScreen(
            onBack = onBack,
            onNavigate = onNavigate,
        )

        is AppRoute.MessagesConversation -> MessagesConversationScreen(
            convoId = route.conversation,
            sharedPost = route.sharedPost,
            onBack = onBack,
            onNavigate = onNavigate,
        )

        is AppRoute.SendPostViaChat -> SendPostViaChatScreen(
            postUri = route.postUri,
            postCid = route.postCid,
            onBack = onBack,
            onSelectConversation = onReplaceTop,
        )

        is AppRoute.MessagesConversationSettings -> MessagesConversationSettingsScreen(
            convoId = route.conversation,
            onBack = onBack,
            onNavigate = onNavigate,
        )

        is AppRoute.MessagesJoinRequests -> MessagesJoinRequestsScreen(
            convoId = route.conversation,
            onBack = onBack,
        )

        // Starter Packs & Invites
        is AppRoute.StarterPack -> StarterPackScreen(
            actorHandleOrDid = route.name,
            rkeyOrShortCode = route.rkey,
            isShortCode = false,
            onBack = onBack,
            onNavigate = onNavigate,
            onOpenComposer = onOpenComposer,
            onOpenEditPost = onOpenEditPost,
            onOpenIn = onOpenIn,
            onOpenReport = onOpenReport,
            onOpenLightbox = onOpenLightbox,
            onOpenVideoLightbox = onOpenVideoLightbox,
        )

        is AppRoute.StarterPackShort -> StarterPackScreen(
            actorHandleOrDid = "",
            rkeyOrShortCode = route.code,
            isShortCode = true,
            onBack = onBack,
            onNavigate = onNavigate,
            onOpenComposer = onOpenComposer,
            onOpenEditPost = onOpenEditPost,
            onOpenIn = onOpenIn,
            onOpenReport = onOpenReport,
            onOpenLightbox = onOpenLightbox,
            onOpenVideoLightbox = onOpenVideoLightbox,
        )

        AppRoute.StarterPackWizard -> StarterPackWizardScreen(
            editRkey = null,
            onBack = onBack,
            onCompleted = onReplaceTop,
        )

        is AppRoute.StarterPackEdit -> StarterPackWizardScreen(
            editRkey = route.rkey,
            onBack = onBack,
            onCompleted = onReplaceTop,
        )

        AppRoute.InviteScanner -> InviteScannerScreen(
            onBack = onBack,
            onNavigate = onNavigate,
        )

        // Moderation & Moderation Inbox
        AppRoute.Moderation,
        AppRoute.ModerationInbox,
        AppRoute.ModerationInboxSettings,
        is AppRoute.ModerationInboxReportDetails,
        is AppRoute.ModerationInboxNoticeDetails,
        AppRoute.ModerationModlists,
        AppRoute.ModerationMutedAccounts,
        AppRoute.ModerationBlockedAccounts,
        AppRoute.ModerationInteractionSettings,
        AppRoute.ModerationVerificationSettings -> ModerationHubScreen(
            route = route,
            onBack = onBack,
            onNavigate = onNavigate,
        )

        // Saved Feeds settings screen
        AppRoute.SavedFeeds -> SavedFeedsScreen(
            onBack = onBack,
            onNavigate = onNavigate,
        )

        // All remaining Settings, Debug & Support routes
        AppRoute.Settings,
        AppRoute.LanguageSettings,
        AppRoute.AppPasswords,
        AppRoute.PreferencesFollowingFeed,
        AppRoute.PreferencesThreads,
        AppRoute.PreferencesExternalEmbeds,
        AppRoute.AccessibilitySettings,
        AppRoute.AppearanceSettings,
        AppRoute.BetaFeaturesSettings,
        AppRoute.AccountSettings,
        AppRoute.AutomationLabelSettings,
        AppRoute.PrivacyAndSecuritySettings,
        AppRoute.ActivityPrivacySettings,
        AppRoute.ContentAndMediaSettings,
        AppRoute.InterestsSettings,
        AppRoute.AboutSettings,
        AppRoute.InfrastructureSettings,
        AppRoute.CustomizationSettings,
        AppRoute.EmojiCredits,
        AppRoute.AppIconSettings,
        AppRoute.NotificationSettings,
        AppRoute.ActivityNotificationSettings,
        AppRoute.FindContactsSettings,
        AppRoute.FindContactsFlow,
        AppRoute.Log,
        AppRoute.DebugMod,
        AppRoute.Support,
        AppRoute.PrivacyPolicy,
        AppRoute.TermsOfService,
        AppRoute.CommunityGuidelines,
        AppRoute.CopyrightPolicy -> SettingsRouterScreen(
            route = route,
            onBack = onBack,
            onOpenDrawer = onOpenDrawer,
            onNavigate = onNavigate,
            onOpenAccountSwitcher = onOpenAccountSwitcher,
            onAddAccount = onAddAccount,
        )

        // 404 Not Found fallback
        is AppRoute.NotFound -> NotFoundScreen(
            path = route.path,
            onBack = onBack,
            onGoHome = onGoHome,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GuestAuthRequiredScreen(
    title: String,
    message: String,
    onOpenDrawer: () -> Unit,
    onSignIn: () -> Unit,
    onCreateAccount: () -> Unit,
) {
    val palette = LocalAlfPalette.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = tr("Menu"))
                    }
                },
            )
        },
        containerColor = palette.contrast0.toComposeColor(),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CaelumLogoMark(size = 44.dp, useDarkVariant = palette.isDark)
            Spacer(Modifier.height(14.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = palette.contrast800.toComposeColor(),
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onCreateAccount) {
                    Text(tr("Create account"))
                }
                OutlinedButton(onClick = onSignIn) {
                    Text(tr("Sign in"))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotFoundScreen(
    path: String,
    onBack: () -> Unit,
    onGoHome: () -> Unit,
) {
    val palette = LocalAlfPalette.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("Page not found")) },
            )
        },
        containerColor = palette.contrast0.toComposeColor(),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = tr("Page not found"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            if (path.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = path,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.contrast600.toComposeColor(),
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onBack) {
                    Text(tr("Go back"))
                }
                Button(onClick = onGoHome) {
                    Text(tr("Home"))
                }
            }
        }
    }
}
