package am.noz.caelum.ui.lists

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.outlined.PushPin
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.model.GeneratorView
import am.noz.caelum.core.model.ListView
import am.noz.caelum.core.model.SavedFeedPref
import am.noz.caelum.ui.components.AvatarShape
import am.noz.caelum.ui.components.CaelumAvatar
import am.noz.caelum.ui.components.LocalAppContainer
import am.noz.caelum.ui.components.rememberHaptics
import am.noz.caelum.ui.navigation.AppRoute
import am.noz.caelum.ui.theme.LocalAlfPalette
import am.noz.caelum.ui.theme.toComposeColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

private data class DiscoverFeedsResponse(
    val feeds: List<GeneratorView>,
    val cursor: String?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedsDiscoveryScreen(
    onOpenDrawer: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
) {
    val container = LocalAppContainer.current
    val palette = LocalAlfPalette.current
    val session by container.sessionManager.state.collectAsState()
    val settings by container.preferences.settingsFlow.collectAsState(initial = am.noz.caelum.core.storage.AppSettingsSnapshot())
    val haptics = rememberHaptics(settings.disableHaptics)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var searchQuery by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }

    var discoverFeeds by remember { mutableStateOf<List<GeneratorView>>(emptyList()) }
    var discoverCursor by remember { mutableStateOf<String?>(null) }
    var isLoadingDiscover by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var discoverError by remember { mutableStateOf<String?>(null) }

    val extraGenerators = remember { mutableStateMapOf<String, GeneratorView>() }
    val extraLists = remember { mutableStateMapOf<String, ListView>() }
    val savedFeeds = session.pinnedFeeds
    var myFeedsExpanded by remember { mutableStateOf(true) }

    val listState = rememberLazyListState()

    suspend fun fetchPopularFeeds(query: String, cursor: String?): DiscoverFeedsResponse {
        val params = buildList {
            add("limit" to "30")
            if (query.isNotBlank()) add("query" to query)
            if (!cursor.isNullOrBlank()) add("cursor" to cursor)
        }
        val res = container.xrpcClient.getJson("app.bsky.unspecced.getPopularFeedGenerators", params).jsonObject
        val feeds = res["feeds"]?.jsonArray?.mapNotNull {
            runCatching { AtProtoJson.decodeFromJsonElement<GeneratorView>(it) }.getOrNull()
        }.orEmpty()
        val nextCursor = (res["cursor"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
        return DiscoverFeedsResponse(feeds = feeds, cursor = nextCursor)
    }

    suspend fun loadDiscover(refresh: Boolean = false, loadMore: Boolean = false) {
        if (loadMore && (isLoadingMore || discoverCursor.isNullOrBlank())) return
        if (refresh) {
            isRefreshing = true
        } else if (loadMore) {
            isLoadingMore = true
        } else {
            isLoadingDiscover = true
        }
        discoverError = null

        runCatching {
            val cursor = if (loadMore) discoverCursor else null
            fetchPopularFeeds(debouncedQuery.trim(), cursor)
        }.onSuccess { resp ->
            val merged = if (loadMore) {
                (discoverFeeds + resp.feeds).distinctBy { it.uri }
            } else {
                resp.feeds
            }
            discoverFeeds = merged
            discoverCursor = if (resp.feeds.isEmpty()) null else resp.cursor
        }.onFailure { ex ->
            discoverError = ex.message ?: "Unable to load feeds"
        }

        isLoadingDiscover = false
        isLoadingMore = false
        isRefreshing = false
    }

    // Hydrate metadata for saved feeds/lists not yet in session.feedGenerators or session.listViews
    LaunchedEffect(savedFeeds, session.feedGenerators, session.listViews) {
        val missingFeedUris = savedFeeds
            .filter { it.type == "feed" || it.value.contains("/app.bsky.feed.generator/") }
            .map { it.value }
            .filter { it !in session.feedGenerators && it !in extraGenerators }
            .distinct()
        if (missingFeedUris.isNotEmpty()) {
            missingFeedUris.chunked(25).forEach { chunk ->
                runCatching { container.api.getFeedGenerators(chunk) }.getOrNull()?.forEach { gen ->
                    extraGenerators[gen.uri] = gen
                }
            }
        }
        val missingListUris = savedFeeds
            .filter { it.type == "list" || it.value.contains("/app.bsky.graph.list/") }
            .map { it.value }
            .filter { it !in session.listViews && it !in extraLists }
            .distinct()
        missingListUris.forEach { uri ->
            runCatching {
                val res = container.api.getList(uri)
                res["list"]?.let { AtProtoJson.decodeFromJsonElement<ListView>(it) }
            }.getOrNull()?.let { list ->
                extraLists[uri] = list
            }
        }
    }

    LaunchedEffect(searchQuery) {
        delay(300L)
        debouncedQuery = searchQuery
    }

    LaunchedEffect(debouncedQuery) {
        loadDiscover()
    }

    val totalItemCount = savedFeeds.size + discoverFeeds.size + 4
    val shouldLoadMore by remember(listState, discoverFeeds.size, discoverCursor, isLoadingMore) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            !discoverCursor.isNullOrBlank() && !isLoadingMore && discoverFeeds.isNotEmpty() && lastVisible >= totalItemCount - 6
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) loadDiscover(loadMore = true)
    }

    fun mutateSavedFeed(
        type: String,
        value: String,
        isPinAction: Boolean,
        generator: GeneratorView? = null,
        listView: ListView? = null,
    ) {
        val did = session.currentDid ?: return
        haptics.tap()
        val prev = container.sessionManager.state.value.pinnedFeeds
        scope.launch {
            runCatching {
                if (isPinAction) {
                    SavedFeedsHelper.togglePin(
                        container = container,
                        did = did,
                        currentFeeds = prev,
                        type = type,
                        value = value,
                        generator = generator,
                        listView = listView,
                    )
                } else {
                    SavedFeedsHelper.toggleSave(
                        container = container,
                        did = did,
                        currentFeeds = prev,
                        type = type,
                        value = value,
                        generator = generator,
                        listView = listView,
                    )
                }
            }.onFailure { ex ->
                container.sessionManager.updateSavedFeedsOptimistically(did, prev)
                snackbarHostState.showSnackbar(ex.message ?: "Failed to update feeds")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.contrast0.toComposeColor()),
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer, modifier = Modifier.testTag("feeds_drawer_button")) {
                        Icon(Icons.Filled.Menu, contentDescription = "Open drawer menu")
                    }
                },
                title = { Text("Feeds", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(
                        onClick = { onNavigate(AppRoute.SavedFeeds) },
                        modifier = Modifier.testTag("feeds_manage_button"),
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = "Manage saved feeds")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = palette.contrast0.toComposeColor(),
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    session.currentDid?.let { did ->
                        runCatching { container.sessionManager.syncPreferencesFromServer(did) }
                    }
                    loadDiscover(refresh = true)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 84.dp),
            ) {
                // My Feeds section
                if (session.hasSession) {
                    item(key = "my_feeds_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(palette.contrast25.toComposeColor())
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(
                                    text = "My Feeds",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = palette.contrast1000.toComposeColor(),
                                )
                                Text(
                                    text = "Pinned feeds appear as tabs on your Home screen",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.contrast600.toComposeColor(),
                                )
                            }
                            TextButton(onClick = { onNavigate(AppRoute.SavedFeeds) }) {
                                Text("Edit")
                            }
                        }
                        HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)
                    }

                    if (savedFeeds.isEmpty()) {
                        item(key = "my_feeds_empty") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigate(AppRoute.Home) }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(palette.primary50.toComposeColor()),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Filled.Home, contentDescription = null, tint = palette.primary500.toComposeColor())
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Following", style = MaterialTheme.typography.titleSmall, color = palette.contrast1000.toComposeColor())
                                    Text("Posts from people you follow", style = MaterialTheme.typography.bodySmall, color = palette.contrast600.toComposeColor())
                                }
                            }
                            HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)
                        }
                    } else if (myFeedsExpanded) {
                        val sortedMyFeeds = savedFeeds.sortedByDescending { it.pinned }
                        items(items = sortedMyFeeds, key = { "my_${it.value}" }) { pref ->
                            when (pref.type) {
                                "timeline" -> {
                                    MyTimelineFeedRow(
                                        isPinned = pref.pinned,
                                        onOpen = { onNavigate(AppRoute.Home) },
                                        onTogglePin = { mutateSavedFeed("timeline", pref.value, isPinAction = true) },
                                    )
                                }
                                "list" -> {
                                    val listView = session.listViews[pref.value] ?: extraLists[pref.value]
                                    val repoDid = pref.value.removePrefix("at://").substringBefore('/')
                                    val rkey = pref.value.substringAfterLast('/')
                                    MySavedListRow(
                                        title = listView?.name?.takeIf { it.isNotBlank() } ?: rkey,
                                        avatarUrl = listView?.avatar,
                                        creatorHandle = listView?.creator?.handle,
                                        isPinned = pref.pinned,
                                        onOpen = { onNavigate(AppRoute.ProfileList(repoDid, rkey)) },
                                        onTogglePin = { mutateSavedFeed("list", pref.value, isPinAction = true, listView = listView) },
                                        onRemove = { mutateSavedFeed("list", pref.value, isPinAction = false, listView = listView) },
                                    )
                                }
                                else -> {
                                    val gen = session.feedGenerators[pref.value] ?: extraGenerators[pref.value]
                                    val repoDid = pref.value.removePrefix("at://").substringBefore('/')
                                    val rkey = pref.value.substringAfterLast('/')
                                    if (gen != null) {
                                        GeneratorListRow(
                                            generator = gen,
                                            isSaved = true,
                                            isPinned = pref.pinned,
                                            onOpen = { onNavigate(AppRoute.CustomFeed(repoDid, rkey)) },
                                            onToggleSave = { mutateSavedFeed("feed", pref.value, isPinAction = false, generator = gen) },
                                            onTogglePin = { mutateSavedFeed("feed", pref.value, isPinAction = true, generator = gen) },
                                        )
                                    } else {
                                        MyFallbackFeedRow(
                                            title = rkey,
                                            subtitle = pref.value,
                                            isPinned = pref.pinned,
                                            onOpen = { onNavigate(AppRoute.CustomFeed(repoDid, rkey)) },
                                            onTogglePin = { mutateSavedFeed("feed", pref.value, isPinAction = true) },
                                            onRemove = { mutateSavedFeed("feed", pref.value, isPinAction = false) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Discover New Feeds section + search bar
                item(key = "discover_header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(palette.contrast25.toComposeColor())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "Discover New Feeds",
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.contrast1000.toComposeColor(),
                        )
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search feeds…") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("feeds_search_input"),
                        )
                    }
                    HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)
                }

                when {
                    isLoadingDiscover && discoverFeeds.isEmpty() -> {
                        item(key = "discover_loading") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    discoverError != null && discoverFeeds.isEmpty() -> {
                        item(key = "discover_error") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(discoverError.orEmpty(), color = palette.contrast700.toComposeColor())
                                Button(onClick = { scope.launch { loadDiscover(refresh = true) } }) {
                                    Text("Try again")
                                }
                            }
                        }
                    }
                    discoverFeeds.isEmpty() -> {
                        item(key = "discover_empty") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (debouncedQuery.isBlank()) "No popular feeds found." else "No feeds matching “$debouncedQuery”.",
                                    color = palette.contrast600.toComposeColor(),
                                )
                            }
                        }
                    }
                    else -> {
                        items(items = discoverFeeds, key = { "disc_${it.uri}" }) { gen ->
                            val isSaved = SavedFeedsHelper.isSaved(savedFeeds, gen.uri)
                            val isPinned = SavedFeedsHelper.isPinned(savedFeeds, gen.uri)
                            val repoDid = gen.uri.removePrefix("at://").substringBefore('/')
                            val rkey = gen.uri.substringAfterLast('/')

                            GeneratorListRow(
                                generator = gen,
                                isSaved = isSaved,
                                isPinned = isPinned,
                                showControls = session.hasSession,
                                onOpen = { onNavigate(AppRoute.CustomFeed(repoDid, rkey)) },
                                onToggleSave = { mutateSavedFeed("feed", gen.uri, isPinAction = false, generator = gen) },
                                onTogglePin = { mutateSavedFeed("feed", gen.uri, isPinAction = true, generator = gen) },
                            )
                        }
                    }
                }

                if (isLoadingMore) {
                    item(key = "discover_footer_loader") {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedFeedsScreen(
    onBack: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
) {
    val container = LocalAppContainer.current
    val palette = LocalAlfPalette.current
    val session by container.sessionManager.state.collectAsState()
    val settings by container.preferences.settingsFlow.collectAsState(initial = am.noz.caelum.core.storage.AppSettingsSnapshot())
    val haptics = rememberHaptics(settings.disableHaptics)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val feeds = session.pinnedFeeds
    var isSaving by remember { mutableStateOf(false) }
    val extraGenerators = remember { mutableStateMapOf<String, GeneratorView>() }
    val extraLists = remember { mutableStateMapOf<String, ListView>() }

    LaunchedEffect(feeds, session.feedGenerators, session.listViews) {
        val missingFeedUris = feeds
            .filter { it.type == "feed" || it.value.contains("/app.bsky.feed.generator/") }
            .map { it.value }
            .filter { it !in session.feedGenerators && it !in extraGenerators }
            .distinct()
        if (missingFeedUris.isNotEmpty()) {
            missingFeedUris.chunked(25).forEach { chunk ->
                runCatching { container.api.getFeedGenerators(chunk) }.getOrNull()?.forEach { gen ->
                    extraGenerators[gen.uri] = gen
                }
            }
        }
        val missingListUris = feeds
            .filter { it.type == "list" || it.value.contains("/app.bsky.graph.list/") }
            .map { it.value }
            .filter { it !in session.listViews && it !in extraLists }
            .distinct()
        missingListUris.forEach { uri ->
            runCatching {
                val res = container.api.getList(uri)
                res["list"]?.let { AtProtoJson.decodeFromJsonElement<ListView>(it) }
            }.getOrNull()?.let { list ->
                extraLists[uri] = list
            }
        }
    }

    fun applyFeedsUpdate(updated: List<SavedFeedPref>) {
        val did = session.currentDid ?: return
        val prev = container.sessionManager.state.value.pinnedFeeds
        isSaving = true
        scope.launch {
            runCatching {
                SavedFeedsHelper.persistSavedFeeds(container, did, updated)
            }.onFailure { ex ->
                container.sessionManager.updateSavedFeedsOptimistically(did, prev)
                snackbarHostState.showSnackbar(ex.message ?: "Failed to save feed order")
            }
            isSaving = false
        }
    }

    fun movePinned(pinnedIndex: Int, delta: Int) {
        val current = container.sessionManager.state.value.pinnedFeeds
        val pinnedList = current.filter { it.pinned }.toMutableList()
        val targetIndex = pinnedIndex + delta
        if (pinnedIndex !in pinnedList.indices || targetIndex !in pinnedList.indices) return
        haptics.selection()
        val item = pinnedList.removeAt(pinnedIndex)
        pinnedList.add(targetIndex, item)
        val unpinnedList = current.filterNot { it.pinned }
        applyFeedsUpdate(pinnedList + unpinnedList)
    }

    fun togglePin(pref: SavedFeedPref) {
        val did = session.currentDid ?: return
        haptics.toggle(!pref.pinned)
        val prev = container.sessionManager.state.value.pinnedFeeds
        isSaving = true
        scope.launch {
            runCatching {
                SavedFeedsHelper.togglePinByPref(container, did, prev, pref)
            }.onFailure { ex ->
                container.sessionManager.updateSavedFeedsOptimistically(did, prev)
                snackbarHostState.showSnackbar(ex.message ?: "Failed to update pin")
            }
            isSaving = false
        }
    }

    fun removeFeed(pref: SavedFeedPref) {
        haptics.tap()
        val current = container.sessionManager.state.value.pinnedFeeds
        val updated = current.filterNot { it.value == pref.value }
        applyFeedsUpdate(updated)
    }

    val pinnedFeeds = feeds.filter { it.pinned }
    val unpinnedFeeds = feeds.filterNot { it.pinned }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.contrast0.toComposeColor()),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Saved Feeds", fontWeight = FontWeight.SemiBold) },
                actions = {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    TextButton(onClick = { onNavigate(AppRoute.Feeds) }) {
                        Text("Discover")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = palette.contrast0.toComposeColor(),
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 48.dp),
        ) {
            item(key = "pinned_section_header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.contrast25.toComposeColor())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = "Pinned Feeds",
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.contrast1000.toComposeColor(),
                    )
                    Text(
                        text = "Reorder how feeds appear in your Home tab bar",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.contrast600.toComposeColor(),
                    )
                }
                HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)
            }

            if (pinnedFeeds.isEmpty()) {
                item(key = "pinned_empty") {
                    Text(
                        text = "No pinned feeds yet. Pin any saved feed below or discover new feeds.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.contrast600.toComposeColor(),
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                itemsIndexed(
                    items = pinnedFeeds,
                    key = { _, pref -> "pinned_${pref.value}" },
                ) { index, pref ->
                    val gen = if (pref.type == "feed") session.feedGenerators[pref.value] ?: extraGenerators[pref.value] else null
                    val listView = if (pref.type == "list") session.listViews[pref.value] ?: extraLists[pref.value] else null
                    SavedFeedManageRow(
                        pref = pref,
                        generator = gen,
                        listView = listView,
                        isPinned = true,
                        canMoveUp = index > 0,
                        canMoveDown = index < pinnedFeeds.lastIndex,
                        onOpen = {
                            when (pref.type) {
                                "timeline" -> onNavigate(AppRoute.Home)
                                "list" -> {
                                    val repoDid = pref.value.removePrefix("at://").substringBefore('/')
                                    val rkey = pref.value.substringAfterLast('/')
                                    onNavigate(AppRoute.ProfileList(repoDid, rkey))
                                }
                                else -> {
                                    val repoDid = pref.value.removePrefix("at://").substringBefore('/')
                                    val rkey = pref.value.substringAfterLast('/')
                                    onNavigate(AppRoute.CustomFeed(repoDid, rkey))
                                }
                            }
                        },
                        onMoveUp = { movePinned(index, -1) },
                        onMoveDown = { movePinned(index, +1) },
                        onTogglePin = { togglePin(pref) },
                        onRemove = if (pref.type == "timeline") null else ({ removeFeed(pref) }),
                    )
                }
            }

            item(key = "saved_section_header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.contrast25.toComposeColor())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = "Saved Feeds",
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.contrast1000.toComposeColor(),
                    )
                    Text(
                        text = "Pin a feed to add it to your Home tabs, or remove it from your list",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.contrast600.toComposeColor(),
                    )
                }
                HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)
            }

            if (unpinnedFeeds.isEmpty()) {
                item(key = "saved_empty") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "No unpinned saved feeds.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.contrast600.toComposeColor(),
                        )
                        OutlinedButton(onClick = { onNavigate(AppRoute.Feeds) }) {
                            Text("Browse feeds")
                        }
                    }
                }
            } else {
                items(
                    items = unpinnedFeeds,
                    key = { pref -> "unpinned_${pref.value}" },
                ) { pref ->
                    val gen = if (pref.type == "feed") session.feedGenerators[pref.value] ?: extraGenerators[pref.value] else null
                    val listView = if (pref.type == "list") session.listViews[pref.value] ?: extraLists[pref.value] else null
                    SavedFeedManageRow(
                        pref = pref,
                        generator = gen,
                        listView = listView,
                        isPinned = false,
                        canMoveUp = false,
                        canMoveDown = false,
                        onOpen = {
                            when (pref.type) {
                                "timeline" -> onNavigate(AppRoute.Home)
                                "list" -> {
                                    val repoDid = pref.value.removePrefix("at://").substringBefore('/')
                                    val rkey = pref.value.substringAfterLast('/')
                                    onNavigate(AppRoute.ProfileList(repoDid, rkey))
                                }
                                else -> {
                                    val repoDid = pref.value.removePrefix("at://").substringBefore('/')
                                    val rkey = pref.value.substringAfterLast('/')
                                    onNavigate(AppRoute.CustomFeed(repoDid, rkey))
                                }
                            }
                        },
                        onMoveUp = {},
                        onMoveDown = {},
                        onTogglePin = { togglePin(pref) },
                        onRemove = if (pref.type == "timeline") null else ({ removeFeed(pref) }),
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedFeedManageRow(
    pref: SavedFeedPref,
    generator: GeneratorView?,
    listView: ListView?,
    isPinned: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onOpen: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onTogglePin: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    val palette = LocalAlfPalette.current
    val muted = palette.contrast600.toComposeColor()

    val title = when (pref.type) {
        "timeline" -> "Following"
        "list" -> listView?.name ?: pref.value.substringAfterLast('/')
        else -> generator?.displayName ?: pref.value.substringAfterLast('/')
    }
    val subtitle = when (pref.type) {
        "timeline" -> "Home timeline"
        "list" -> listView?.creator?.handle?.let { "List by @$it" } ?: "Curation list"
        else -> generator?.creator?.handle?.let { "Feed by @$it" } ?: "Custom feed"
    }
    val avatarUrl = when (pref.type) {
        "list" -> listView?.avatar
        "feed" -> generator?.avatar
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .background(palette.contrast0.toComposeColor()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (pref.type == "timeline") {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(palette.primary50.toComposeColor()),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Home, contentDescription = null, tint = palette.primary500.toComposeColor(), modifier = Modifier.size(20.dp))
                }
            } else {
                CaelumAvatar(
                    url = avatarUrl,
                    contentDescription = title,
                    size = 38.dp,
                    shapeType = AvatarShape.RoundedSquare,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = palette.contrast1000.toComposeColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (isPinned) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = canMoveUp,
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowUp,
                        contentDescription = "Move up",
                        tint = if (canMoveUp) palette.contrast800.toComposeColor() else palette.contrast200.toComposeColor(),
                    )
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = canMoveDown,
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Move down",
                        tint = if (canMoveDown) palette.contrast800.toComposeColor() else palette.contrast200.toComposeColor(),
                    )
                }
            }

            IconButton(onClick = onTogglePin, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = if (isPinned) "Unpin" else "Pin",
                    tint = if (isPinned) palette.primary500.toComposeColor() else muted,
                    modifier = Modifier.size(19.dp),
                )
            }

            if (onRemove != null) {
                IconButton(onClick = onRemove, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Remove feed",
                        tint = palette.negative500.toComposeColor(),
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        }
        HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)
    }
}

@Composable
private fun MyTimelineFeedRow(
    isPinned: Boolean,
    onOpen: () -> Unit,
    onTogglePin: () -> Unit,
) {
    val palette = LocalAlfPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.primary50.toComposeColor()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Home, contentDescription = null, tint = palette.primary500.toComposeColor())
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Following", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = palette.contrast1000.toComposeColor())
                Text("Posts from people you follow", style = MaterialTheme.typography.bodySmall, color = palette.contrast600.toComposeColor())
            }
            IconButton(onClick = onTogglePin, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = if (isPinned) "Unpin Following" else "Pin Following",
                    tint = if (isPinned) palette.primary500.toComposeColor() else palette.contrast600.toComposeColor(),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)
    }
}

@Composable
private fun MySavedListRow(
    title: String,
    avatarUrl: String?,
    creatorHandle: String?,
    isPinned: Boolean,
    onOpen: () -> Unit,
    onTogglePin: () -> Unit,
    onRemove: () -> Unit,
) {
    val palette = LocalAlfPalette.current
    val muted = palette.contrast600.toComposeColor()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CaelumAvatar(url = avatarUrl, contentDescription = title, size = 44.dp, shapeType = AvatarShape.RoundedSquare)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = palette.contrast1000.toComposeColor(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = creatorHandle?.let { "List by @$it" } ?: "Pinned list",
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Bookmark, contentDescription = "Remove saved list", tint = palette.primary500.toComposeColor(), modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onTogglePin, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = if (isPinned) "Unpin list" else "Pin list",
                    tint = if (isPinned) palette.primary500.toComposeColor() else muted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)
    }
}

@Composable
private fun MyFallbackFeedRow(
    title: String,
    subtitle: String,
    isPinned: Boolean,
    onOpen: () -> Unit,
    onTogglePin: () -> Unit,
    onRemove: () -> Unit,
) {
    val palette = LocalAlfPalette.current
    val muted = palette.contrast600.toComposeColor()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.contrast50.toComposeColor()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Tag, contentDescription = null, tint = muted)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = palette.contrast1000.toComposeColor(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Bookmark, contentDescription = "Remove saved feed", tint = palette.primary500.toComposeColor(), modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onTogglePin, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = if (isPinned) "Unpin feed" else "Pin feed",
                    tint = if (isPinned) palette.primary500.toComposeColor() else muted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)
    }
}
