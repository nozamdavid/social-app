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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.model.FeedViewPost
import am.noz.caelum.core.model.ListView
import am.noz.caelum.core.model.ListViewerState
import am.noz.caelum.core.model.PostView
import am.noz.caelum.core.model.ProfileView
import am.noz.caelum.core.model.ProfileViewBasic
import am.noz.caelum.ui.components.AvatarShape
import am.noz.caelum.ui.components.CaelumAvatar
import am.noz.caelum.ui.components.LinkNavigationHelper
import am.noz.caelum.ui.components.LocalAppContainer
import am.noz.caelum.ui.components.rememberHaptics
import am.noz.caelum.ui.navigation.AppRoute
import am.noz.caelum.ui.openin.OpenInSubject
import am.noz.caelum.ui.post.EmbedImageItem
import am.noz.caelum.ui.theme.LocalAlfPalette
import am.noz.caelum.ui.theme.toComposeColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.time.Instant

private const val PURPOSE_CURATE = "app.bsky.graph.defs#curatelist"
private const val PURPOSE_MOD = "app.bsky.graph.defs#modlist"

private data class ListMemberEntry(
    val uri: String,
    val subject: ProfileView,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsScreen(
    onOpenDrawer: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
) {
    val container = LocalAppContainer.current
    val palette = LocalAlfPalette.current
    val session by container.sessionManager.state.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var lists by remember { mutableStateOf<List<ListView>>(emptyList()) }
    var cursor by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var purposeFilter by remember { mutableStateOf<String?>(null) }

    var showCreateDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    suspend fun loadLists(refresh: Boolean = false, loadMore: Boolean = false) {
        val did = session.currentDid
        if (did.isNullOrBlank()) {
            isLoading = false
            return
        }
        if (loadMore && (isLoadingMore || cursor.isNullOrBlank())) return
        if (refresh) isRefreshing = true else if (loadMore) isLoadingMore = true else isLoading = true
        errorMsg = null

        runCatching {
            val params = buildList {
                add("actor" to did)
                add("limit" to "50")
                if (loadMore && !cursor.isNullOrBlank()) add("cursor" to cursor!!)
            }
            val res = container.xrpcClient.getJson("app.bsky.graph.getLists", params).jsonObject
            val parsed = res["lists"]?.jsonArray?.mapNotNull {
                runCatching { AtProtoJson.decodeFromJsonElement<ListView>(it) }.getOrNull()
            }.orEmpty()
            val nextCursor = (res["cursor"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
            parsed to nextCursor
        }.onSuccess { (items, nextCursor) ->
            lists = if (loadMore) (lists + items).distinctBy { it.uri } else items
            cursor = if (items.isEmpty()) null else nextCursor
        }.onFailure { ex ->
            errorMsg = ex.message ?: "Unable to load lists"
        }

        isLoading = false
        isRefreshing = false
        isLoadingMore = false
    }

    LaunchedEffect(session.currentDid) {
        loadLists()
    }

    val displayedLists = remember(lists, purposeFilter) {
        if (purposeFilter == null) lists else lists.filter { it.purpose == purposeFilter }
    }

    val shouldLoadMore by remember(listState, displayedLists.size, cursor, isLoadingMore) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            !cursor.isNullOrBlank() && !isLoadingMore && displayedLists.isNotEmpty() && lastVisible >= displayedLists.size - 4
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) loadLists(loadMore = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.contrast0.toComposeColor()),
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer, modifier = Modifier.testTag("lists_drawer_button")) {
                        Icon(Icons.Filled.Menu, contentDescription = "Open drawer menu")
                    }
                },
                title = { Text("Lists", fontWeight = FontWeight.SemiBold) },
                actions = {
                    if (session.hasSession) {
                        IconButton(onClick = { showCreateDialog = true }) {
                            Icon(Icons.Filled.Add, contentDescription = "Create new list")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (session.hasSession) {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = palette.primary500.toComposeColor(),
                    contentColor = Color.White,
                    modifier = Modifier.testTag("lists_create_fab"),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "New list")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = palette.contrast0.toComposeColor(),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.contrast25.toComposeColor())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = purposeFilter == null,
                    onClick = { purposeFilter = null },
                    label = { Text("All") },
                )
                FilterChip(
                    selected = purposeFilter == PURPOSE_CURATE,
                    onClick = { purposeFilter = if (purposeFilter == PURPOSE_CURATE) null else PURPOSE_CURATE },
                    label = { Text("Curation") },
                )
                FilterChip(
                    selected = purposeFilter == PURPOSE_MOD,
                    onClick = { purposeFilter = if (purposeFilter == PURPOSE_MOD) null else PURPOSE_MOD },
                    label = { Text("Moderation") },
                )
            }
            HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { scope.launch { loadLists(refresh = true) } },
                modifier = Modifier.weight(1f),
            ) {
                when {
                    !session.hasSession -> {
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("Sign in to view and manage your lists.", color = palette.contrast600.toComposeColor())
                        }
                    }
                    isLoading && lists.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    errorMsg != null && lists.isEmpty() -> {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(errorMsg.orEmpty(), color = palette.contrast700.toComposeColor())
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { scope.launch { loadLists(refresh = true) } }) {
                                Text("Try again")
                            }
                        }
                    }
                    displayedLists.isEmpty() -> {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = "You don’t have any lists yet.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = palette.contrast600.toComposeColor(),
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { showCreateDialog = true }) {
                                Text("Create a list")
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 84.dp),
                        ) {
                            items(items = displayedLists, key = { it.uri }) { list ->
                                val creatorHandle = list.creator.handle.ifBlank { list.creator.did }
                                val rkey = list.uri.substringAfterLast('/')
                                ListSummaryRow(
                                    list = list,
                                    onClick = { onNavigate(AppRoute.ProfileList(creatorHandle, rkey)) },
                                )
                            }
                            if (isLoadingMore) {
                                item(key = "lists_footer_loader") {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(20.dp),
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

    if (showCreateDialog) {
        CreateOrEditListDialog(
            title = "New List",
            initialName = "",
            initialDescription = "",
            initialPurpose = PURPOSE_CURATE,
            allowPurposeEdit = true,
            onDismiss = { showCreateDialog = false },
            onSave = { name, description, purpose ->
                val did = session.currentDid ?: return@CreateOrEditListDialog
                scope.launch {
                    runCatching {
                        val record = buildJsonObject {
                            put("\$type", JsonPrimitive("app.bsky.graph.list"))
                            put("purpose", JsonPrimitive(purpose))
                            put("name", JsonPrimitive(name.trim()))
                            if (description.isNotBlank()) {
                                put("description", JsonPrimitive(description.trim()))
                            }
                            put("createdAt", JsonPrimitive(Instant.now().toString()))
                        }
                        container.api.createRecord(did, "app.bsky.graph.list", record)
                    }.onSuccess { created ->
                        showCreateDialog = false
                        loadLists(refresh = true)
                        val rkey = created.uri.substringAfterLast('/')
                        val handleOrDid = session.currentAccount?.handle?.ifBlank { did } ?: did
                        onNavigate(AppRoute.ProfileList(handleOrDid, rkey))
                    }.onFailure { ex ->
                        snackbarHostState.showSnackbar(ex.message ?: "Failed to create list")
                    }
                }
            },
        )
    }
}

@Composable
private fun ListSummaryRow(
    list: ListView,
    onClick: () -> Unit,
) {
    val palette = LocalAlfPalette.current
    val muted = palette.contrast600.toComposeColor()
    val creatorHandle = list.creator.handle.ifBlank { list.creator.did }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(palette.contrast0.toComposeColor()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CaelumAvatar(
                url = list.avatar,
                contentDescription = list.name,
                size = 44.dp,
                shapeType = AvatarShape.RoundedSquare,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = list.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = palette.contrast1000.toComposeColor(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    PurposeBadge(list.purpose)
                }
                Text(
                    text = "by @$creatorHandle" + (list.listItemCount?.let { " · $it members" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!list.description.isNullOrBlank()) {
                    Text(
                        text = list.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.contrast800.toComposeColor(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileListScreen(
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
    val context = LocalContext.current
    val session by container.sessionManager.state.collectAsState()
    val settings by container.preferences.settingsFlow.collectAsState(initial = am.noz.caelum.core.storage.AppSettingsSnapshot())
    val haptics = rememberHaptics(settings.disableHaptics)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var resolvedListUri by remember(actorHandleOrDid, rkey) { mutableStateOf<String?>(null) }
    var listView by remember(actorHandleOrDid, rkey) { mutableStateOf<ListView?>(null) }
    var members by remember(actorHandleOrDid, rkey) { mutableStateOf<List<ListMemberEntry>>(emptyList()) }
    var membersCursor by remember(actorHandleOrDid, rkey) { mutableStateOf<String?>(null) }
    var isLoadingMembersMore by remember(actorHandleOrDid, rkey) { mutableStateOf(false) }

    var posts by remember(actorHandleOrDid, rkey) { mutableStateOf<List<FeedViewPost>>(emptyList()) }
    var postsCursor by remember(actorHandleOrDid, rkey) { mutableStateOf<String?>(null) }
    var isLoading by remember(actorHandleOrDid, rkey) { mutableStateOf(true) }
    var isRefreshing by remember(actorHandleOrDid, rkey) { mutableStateOf(false) }
    var isLoadingPostsMore by remember(actorHandleOrDid, rkey) { mutableStateOf(false) }
    var errorMsg by remember(actorHandleOrDid, rkey) { mutableStateOf<String?>(null) }

    var selectedTab by remember { mutableIntStateOf(0) }
    var optimisticSavedFeeds by remember(session.pinnedFeeds) { mutableStateOf(session.pinnedFeeds) }
    var showOwnerMenu by remember { mutableStateOf(false) }
    var showAddMemberSheet by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val peopleListState = rememberLazyListState()

    fun parseMembers(json: JsonObject): List<ListMemberEntry> {
        return json["items"]?.jsonArray?.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val itemUri = (obj["uri"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            val subjObj = obj["subject"] as? JsonObject ?: return@mapNotNull null
            val subject = runCatching { AtProtoJson.decodeFromJsonElement<ProfileView>(subjObj) }.getOrNull()
                ?: return@mapNotNull null
            ListMemberEntry(uri = itemUri, subject = subject)
        }.orEmpty()
    }

    suspend fun loadAll(refresh: Boolean = false) {
        if (refresh) isRefreshing = true else isLoading = listView == null
        errorMsg = null

        runCatching {
            val did = resolveActorDid(container, actorHandleOrDid)
            val uri = "at://$did/app.bsky.graph.list/$rkey"
            resolvedListUri = uri

            val listJson = container.api.getList(uri, cursor = null)
            val lv = listJson["list"]?.let { AtProtoJson.decodeFromJsonElement<ListView>(it) }
            listView = lv
            members = parseMembers(listJson)
            membersCursor = (listJson["cursor"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

            if (lv?.purpose != PURPOSE_MOD) {
                val feedResp = container.api.getListFeed(uri, cursor = null, limit = 30)
                posts = feedResp.feed
                postsCursor = feedResp.cursor
            } else {
                posts = emptyList()
                postsCursor = null
            }
        }.onFailure { ex ->
            errorMsg = ex.message ?: "Unable to load list"
        }

        isLoading = false
        isRefreshing = false
    }

    suspend fun loadMoreMembers() {
        val uri = resolvedListUri ?: return
        val cur = membersCursor?.takeIf { it.isNotBlank() } ?: return
        if (isLoadingMembersMore) return
        isLoadingMembersMore = true
        runCatching {
            val listJson = container.api.getList(uri, cursor = cur)
            val parsed = parseMembers(listJson)
            val next = (listJson["cursor"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
            members = (members + parsed).distinctBy { it.subject.did }
            membersCursor = if (parsed.isEmpty()) null else next
        }
        isLoadingMembersMore = false
    }

    suspend fun loadMorePosts() {
        val uri = resolvedListUri ?: return
        val cur = postsCursor?.takeIf { it.isNotBlank() } ?: return
        if (isLoadingPostsMore) return
        isLoadingPostsMore = true
        runCatching {
            container.api.getListFeed(uri, cursor = cur, limit = 30)
        }.onSuccess { resp ->
            posts = (posts + resp.feed).distinctBy { it.post.uri + ":" + (it.reason?.toString() ?: "") }
            postsCursor = resp.cursor
        }
        isLoadingPostsMore = false
    }

    LaunchedEffect(actorHandleOrDid, rkey) {
        loadAll()
    }

    val shouldLoadMoreMembers by remember(peopleListState, members.size, membersCursor, isLoadingMembersMore) {
        derivedStateOf {
            val lastVisible = peopleListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            !membersCursor.isNullOrBlank() && !isLoadingMembersMore && members.isNotEmpty() && lastVisible >= members.size - 5
        }
    }
    LaunchedEffect(shouldLoadMoreMembers) {
        if (shouldLoadMoreMembers) loadMoreMembers()
    }

    val lv = listView
    val listUri = resolvedListUri ?: lv?.uri
    val isOwner = session.currentDid != null && session.currentDid == lv?.creator?.did
    val isModList = lv?.purpose == PURPOSE_MOD
    val creatorHandle = lv?.creator?.handle?.ifBlank { lv.creator.did } ?: actorHandleOrDid

    val savedPref = listUri?.let { uri -> optimisticSavedFeeds.firstOrNull { it.value == uri } }
    val isPinned = savedPref?.pinned == true

    fun togglePinList() {
        val did = session.currentDid ?: return
        val uri = listUri ?: return
        haptics.tap()
        val prev = optimisticSavedFeeds
        scope.launch {
            runCatching {
                optimisticSavedFeeds = SavedFeedsHelper.togglePin(container, did, optimisticSavedFeeds, "list", uri)
            }.onFailure { ex ->
                optimisticSavedFeeds = prev
                snackbarHostState.showSnackbar(ex.message ?: "Failed to update pinned list")
            }
        }
    }

    fun toggleMuteList() {
        val current = lv ?: return
        val uri = listUri ?: return
        val currentlyMuted = current.viewer?.muted == true
        haptics.confirm()
        listView = current.copy(viewer = (current.viewer ?: ListViewerState()).copy(muted = !currentlyMuted))
        scope.launch {
            runCatching {
                val nsid = if (currentlyMuted) "app.bsky.graph.unmuteActorList" else "app.bsky.graph.muteActorList"
                container.xrpcClient.postJson(nsid, buildJsonObject { put("list", JsonPrimitive(uri)) })
            }.onFailure { ex ->
                listView = current
                snackbarHostState.showSnackbar(ex.message ?: "Failed to update list mute")
            }
        }
    }

    fun toggleBlockList() {
        val did = session.currentDid ?: return
        val current = lv ?: return
        val uri = listUri ?: return
        val blockUri = current.viewer?.blocked
        val currentlyBlocked = !blockUri.isNullOrBlank()
        haptics.confirm()
        listView = current.copy(
            viewer = (current.viewer ?: ListViewerState()).copy(
                blocked = if (currentlyBlocked) null else "pending://listblock",
            ),
        )
        scope.launch {
            runCatching {
                if (currentlyBlocked) {
                    if (!blockUri.isNullOrBlank() && !blockUri.startsWith("pending://")) {
                        container.api.deleteRecord(did, "app.bsky.graph.listblock", blockUri.substringAfterLast('/'))
                    }
                } else {
                    val created = container.api.createRecord(
                        repo = did,
                        collection = "app.bsky.graph.listblock",
                        record = buildJsonObject {
                            put("\$type", JsonPrimitive("app.bsky.graph.listblock"))
                            put("subject", JsonPrimitive(uri))
                            put("createdAt", JsonPrimitive(Instant.now().toString()))
                        },
                    )
                    listView = listView?.copy(
                        viewer = (listView?.viewer ?: ListViewerState()).copy(blocked = created.uri),
                    )
                }
            }.onFailure { ex ->
                listView = current
                snackbarHostState.showSnackbar(ex.message ?: "Failed to update list block")
            }
        }
    }

    fun removeMember(entry: ListMemberEntry) {
        val did = session.currentDid ?: return
        val prev = members
        members = members.filterNot { it.uri == entry.uri }
        haptics.tap()
        scope.launch {
            runCatching {
                container.api.deleteRecord(did, "app.bsky.graph.listitem", entry.uri.substringAfterLast('/'))
            }.onFailure { ex ->
                members = prev
                snackbarHostState.showSnackbar(ex.message ?: "Failed to remove member")
            }
        }
    }

    fun addMember(profile: ProfileViewBasic) {
        val did = session.currentDid ?: return
        val uri = listUri ?: return
        if (members.any { it.subject.did == profile.did }) return
        haptics.confirm()
        scope.launch {
            runCatching {
                val created = container.api.createRecord(
                    repo = did,
                    collection = "app.bsky.graph.listitem",
                    record = buildJsonObject {
                        put("\$type", JsonPrimitive("app.bsky.graph.listitem"))
                        put("subject", JsonPrimitive(profile.did))
                        put("list", JsonPrimitive(uri))
                        put("createdAt", JsonPrimitive(Instant.now().toString()))
                    },
                )
                val full = ProfileView(
                    did = profile.did,
                    handle = profile.handle,
                    displayName = profile.displayName,
                    pronouns = profile.pronouns,
                    avatar = profile.avatar,
                    associated = profile.associated,
                    viewer = profile.viewer,
                    labels = profile.labels,
                    verification = profile.verification,
                    status = profile.status,
                )
                members = listOf(ListMemberEntry(uri = created.uri, subject = full)) + members
            }.onFailure { ex ->
                snackbarHostState.showSnackbar(ex.message ?: "Failed to add member")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.contrast0.toComposeColor()),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text(
                        text = lv?.name ?: "List",
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    if (listUri != null) {
                        IconButton(onClick = { onOpenIn(OpenInSubject.ListRecord(listUri)) }) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open in ATProto app")
                        }
                        IconButton(onClick = {
                            LinkNavigationHelper.shareText(
                                context,
                                "https://bsky.app/profile/$creatorHandle/lists/$rkey",
                                lv?.name,
                            )
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = "Share list")
                        }
                    }
                    if (isOwner) {
                        Box {
                            IconButton(onClick = { showOwnerMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "List options")
                            }
                            DropdownMenu(
                                expanded = showOwnerMenu,
                                onDismissRequest = { showOwnerMenu = false },
                            ) {
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                                    text = { Text("Add member") },
                                    onClick = {
                                        showOwnerMenu = false
                                        showAddMemberSheet = true
                                    },
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                    text = { Text("Edit list details") },
                                    onClick = {
                                        showOwnerMenu = false
                                        showEditDialog = true
                                    },
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = palette.negative500.toComposeColor()) },
                                    text = { Text("Delete list", color = palette.negative500.toComposeColor()) },
                                    onClick = {
                                        showOwnerMenu = false
                                        showDeleteConfirm = true
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = palette.contrast0.toComposeColor(),
    ) { padding ->
        if (isLoading && lv == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (errorMsg != null && lv == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(errorMsg.orEmpty(), color = palette.contrast800.toComposeColor())
                Spacer(Modifier.height(12.dp))
                Button(onClick = { scope.launch { loadAll(refresh = true) } }) {
                    Text("Try again")
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (lv != null) {
                    ProfileListHeader(
                        list = lv,
                        isPinned = isPinned,
                        isOwner = isOwner,
                        hasSession = session.hasSession,
                        onCreatorClick = { onNavigate(AppRoute.Profile(creatorHandle)) },
                        onTogglePin = ::togglePinList,
                        onToggleMute = ::toggleMuteList,
                        onToggleBlock = ::toggleBlockList,
                        onAddMember = { showAddMemberSheet = true },
                        onOpenIn = { onOpenIn(OpenInSubject.ListRecord(lv.uri)) },
                    )
                }

                val tabs = if (isModList) listOf("People") else listOf("Posts", "People")
                val effectiveTab = selectedTab.coerceIn(0, tabs.lastIndex)

                SecondaryTabRow(
                    selectedTabIndex = effectiveTab,
                    containerColor = palette.contrast0.toComposeColor(),
                    contentColor = palette.contrast1000.toComposeColor(),
                ) {
                    tabs.forEachIndexed { idx, label ->
                        Tab(
                            selected = effectiveTab == idx,
                            onClick = { selectedTab = idx },
                            text = { Text(label) },
                        )
                    }
                }

                if (!isModList && effectiveTab == 0) {
                    FeedPostListContent(
                        items = posts,
                        isLoading = isLoading,
                        isRefreshing = isRefreshing,
                        isLoadingMore = isLoadingPostsMore,
                        error = errorMsg,
                        hasMore = !postsCursor.isNullOrBlank(),
                        onRefresh = { scope.launch { loadAll(refresh = true) } },
                        onLoadMore = { scope.launch { loadMorePosts() } },
                        onNavigate = onNavigate,
                        onOpenComposer = onOpenComposer,
                        onOpenEditPost = onOpenEditPost,
                        onOpenIn = onOpenIn,
                        onOpenReport = onOpenReport,
                        onOpenLightbox = onOpenLightbox,
                        onOpenVideoLightbox = onOpenVideoLightbox,
                        emptyMessage = "No posts from list members yet.",
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = { scope.launch { loadAll(refresh = true) } },
                        modifier = Modifier.weight(1f),
                    ) {
                        if (members.isEmpty() && !isLoading) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text("No members in this list yet.", color = palette.contrast600.toComposeColor())
                                if (isOwner) {
                                    Spacer(Modifier.height(12.dp))
                                    Button(onClick = { showAddMemberSheet = true }) {
                                        Text("Add people")
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                state = peopleListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 84.dp),
                            ) {
                                items(items = members, key = { it.uri }) { entry ->
                                    val p = entry.subject
                                    val basic = ProfileViewBasic(
                                        did = p.did,
                                        handle = p.handle,
                                        displayName = p.displayName,
                                        pronouns = p.pronouns,
                                        avatar = p.avatar,
                                        associated = p.associated,
                                        viewer = p.viewer,
                                        labels = p.labels,
                                        verification = p.verification,
                                        status = p.status,
                                    )
                                    ActorListRow(
                                        profile = basic,
                                        description = p.description,
                                        onClick = { onNavigate(AppRoute.Profile(p.handle.ifBlank { p.did })) },
                                        trailingContent = if (isOwner) {
                                            {
                                                IconButton(onClick = { removeMember(entry) }) {
                                                    Icon(
                                                        imageVector = Icons.Filled.PersonRemove,
                                                        contentDescription = "Remove from list",
                                                        tint = palette.negative500.toComposeColor(),
                                                    )
                                                }
                                            }
                                        } else {
                                            null
                                        },
                                    )
                                }
                                if (isLoadingMembersMore) {
                                    item(key = "list_members_loader") {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().padding(20.dp),
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

    if (showAddMemberSheet) {
        AddListMemberBottomSheet(
            existingDids = members.map { it.subject.did }.toSet(),
            onDismiss = { showAddMemberSheet = false },
            onAdd = ::addMember,
        )
    }

    if (showEditDialog && lv != null) {
        CreateOrEditListDialog(
            title = "Edit List",
            initialName = lv.name,
            initialDescription = lv.description.orEmpty(),
            initialPurpose = lv.purpose,
            allowPurposeEdit = false,
            onDismiss = { showEditDialog = false },
            onSave = { name, description, _ ->
                val did = session.currentDid ?: return@CreateOrEditListDialog
                scope.launch {
                    runCatching {
                        val existingRecord = container.xrpcClient.getJson(
                            nsid = "com.atproto.repo.getRecord",
                            params = listOf(
                                "repo" to did,
                                "collection" to "app.bsky.graph.list",
                                "rkey" to rkey,
                            ),
                        ).jsonObject["value"]?.jsonObject ?: JsonObject(emptyMap())
                        val record = buildJsonObject {
                            for ((k, v) in existingRecord) {
                                if (k != "name" && k != "description") {
                                    put(k, v)
                                }
                            }
                            put("\$type", JsonPrimitive("app.bsky.graph.list"))
                            if (!existingRecord.containsKey("purpose")) {
                                put("purpose", JsonPrimitive(lv.purpose))
                            }
                            put("name", JsonPrimitive(name.trim()))
                            if (description.isNotBlank()) {
                                put("description", JsonPrimitive(description.trim()))
                            }
                            if (!existingRecord.containsKey("createdAt")) {
                                put("createdAt", JsonPrimitive(lv.indexedAt ?: Instant.now().toString()))
                            }
                        }
                        container.api.putRecord(did, "app.bsky.graph.list", rkey, record)
                    }.onSuccess {
                        showEditDialog = false
                        loadAll(refresh = true)
                    }.onFailure { ex ->
                        snackbarHostState.showSnackbar(ex.message ?: "Failed to update list")
                    }
                }
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this list?") },
            text = { Text("This permanently deletes the list and its items.") },
            confirmButton = {
                TextButton(onClick = {
                    val did = session.currentDid ?: return@TextButton
                    showDeleteConfirm = false
                    scope.launch {
                        runCatching {
                            container.api.deleteRecord(did, "app.bsky.graph.list", rkey)
                        }.onSuccess {
                            onBack()
                        }.onFailure { ex ->
                            snackbarHostState.showSnackbar(ex.message ?: "Failed to delete list")
                        }
                    }
                }) {
                    Text("Delete", color = palette.negative500.toComposeColor())
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ProfileListHeader(
    list: ListView,
    isPinned: Boolean,
    isOwner: Boolean,
    hasSession: Boolean,
    onCreatorClick: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleBlock: () -> Unit,
    onAddMember: () -> Unit,
    onOpenIn: () -> Unit,
) {
    val palette = LocalAlfPalette.current
    val creatorHandle = list.creator.handle.ifBlank { list.creator.did }
    val isModList = list.purpose == PURPOSE_MOD
    val isMuted = list.viewer?.muted == true
    val isBlocked = !list.viewer?.blocked.isNullOrBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.contrast0.toComposeColor())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CaelumAvatar(
                url = list.avatar,
                contentDescription = list.name,
                size = 54.dp,
                shapeType = AvatarShape.RoundedSquare,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = list.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = palette.contrast1000.toComposeColor(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    PurposeBadge(list.purpose)
                }
                Text(
                    text = "by @$creatorHandle" + (list.listItemCount?.let { " · $it members" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.primary500.toComposeColor(),
                    modifier = Modifier.clickable(onClick = onCreatorClick),
                )
            }
        }

        if (!list.description.isNullOrBlank()) {
            Text(
                text = list.description,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.contrast800.toComposeColor(),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hasSession) {
                if (isModList) {
                    OutlinedButton(onClick = onToggleMute) {
                        Icon(Icons.Filled.VolumeOff, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isMuted) "Muted" else "Mute")
                    }
                    OutlinedButton(onClick = onToggleBlock) {
                        Icon(Icons.Filled.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isBlocked) "Blocked" else "Block")
                    }
                } else {
                    OutlinedButton(onClick = onTogglePin) {
                        Icon(
                            imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (isPinned) "Pinned" else "Pin to Home")
                    }
                }
                if (isOwner) {
                    OutlinedButton(onClick = onAddMember) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add")
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onOpenIn) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Open in…")
            }
        }
    }
    HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddListMemberBottomSheet(
    existingDids: Set<String>,
    onDismiss: () -> Unit,
    onAdd: (ProfileViewBasic) -> Unit,
) {
    val container = LocalAppContainer.current
    val palette = LocalAlfPalette.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ProfileViewBasic>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.isEmpty()) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(250L)
        isSearching = true
        results = runCatching { container.api.searchActorsTypeahead(q, limit = 12) }.getOrDefault(emptyList())
        isSearching = false
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Add people to list",
                style = MaterialTheme.typography.titleMedium,
                color = palette.contrast1000.toComposeColor(),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search by handle or name…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (isSearching) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp))
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                ) {
                    items(items = results, key = { it.did }) { profile ->
                        val alreadyAdded = profile.did in existingDids
                        ActorListRow(
                            profile = profile,
                            onClick = { if (!alreadyAdded) onAdd(profile) },
                            trailingContent = {
                                if (alreadyAdded) {
                                    Icon(Icons.Filled.Check, contentDescription = "Already in list", tint = palette.positive500.toComposeColor())
                                } else {
                                    TextButton(onClick = { onAdd(profile) }) {
                                        Text("Add")
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateOrEditListDialog(
    title: String,
    initialName: String,
    initialDescription: String,
    initialPurpose: String,
    allowPurposeEdit: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, description: String, purpose: String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }
    var purpose by remember { mutableStateOf(initialPurpose) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 64) name = it },
                    label = { Text("List name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (allowPurposeEdit) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = purpose == PURPOSE_CURATE,
                            onClick = { purpose = PURPOSE_CURATE },
                            label = { Text("Curation list") },
                        )
                        FilterChip(
                            selected = purpose == PURPOSE_MOD,
                            onClick = { purpose = PURPOSE_MOD },
                            label = { Text("Moderation list") },
                        )
                    }
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { if (it.length <= 300) description = it },
                    label = { Text("Description (optional)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, description, purpose) },
                enabled = name.trim().isNotEmpty(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
