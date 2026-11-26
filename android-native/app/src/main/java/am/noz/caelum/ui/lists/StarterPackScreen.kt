package am.noz.caelum.ui.lists

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.model.FeedViewPost
import am.noz.caelum.core.model.GeneratorView
import am.noz.caelum.core.model.ListViewBasic
import am.noz.caelum.core.model.PostView
import am.noz.caelum.core.model.ProfileView
import am.noz.caelum.core.model.ProfileViewBasic
import am.noz.caelum.core.model.StrongRef
import am.noz.caelum.core.richtext.RichTextFacet
import am.noz.caelum.ui.components.CaelumAvatar
import am.noz.caelum.ui.components.LinkNavigationHelper
import am.noz.caelum.ui.components.LocalAppContainer
import am.noz.caelum.ui.components.RichTextView
import am.noz.caelum.ui.components.rememberHaptics
import am.noz.caelum.ui.navigation.AppRoute
import am.noz.caelum.ui.navigation.DeepLinkParser
import am.noz.caelum.ui.navigation.DeepLinkTarget
import am.noz.caelum.ui.openin.OpenInSubject
import am.noz.caelum.ui.post.EmbedImageItem
import am.noz.caelum.ui.theme.LocalAlfPalette
import am.noz.caelum.ui.theme.toComposeColor
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import java.time.Instant

private data class StarterPackModel(
    val uri: String,
    val cid: String,
    val name: String,
    val description: String?,
    val descriptionFacets: List<RichTextFacet>,
    val creator: ProfileViewBasic,
    val list: ListViewBasic?,
    val listItemsSample: List<ProfileView>,
    val feeds: List<GeneratorView>,
    val joinedWeekCount: Int?,
    val joinedAllTimeCount: Int?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarterPackScreen(
    actorHandleOrDid: String,
    rkeyOrShortCode: String,
    isShortCode: Boolean = false,
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

    var resolvedRkey by remember(actorHandleOrDid, rkeyOrShortCode, isShortCode) {
        mutableStateOf(rkeyOrShortCode)
    }
    var starterPack by remember(actorHandleOrDid, rkeyOrShortCode, isShortCode) {
        mutableStateOf<StarterPackModel?>(null)
    }
    var members by remember(actorHandleOrDid, rkeyOrShortCode, isShortCode) {
        mutableStateOf<List<ProfileView>>(emptyList())
    }
    var membersCursor by remember(actorHandleOrDid, rkeyOrShortCode, isShortCode) {
        mutableStateOf<String?>(null)
    }
    var isLoadingMembersMore by remember { mutableStateOf(false) }

    var posts by remember(actorHandleOrDid, rkeyOrShortCode, isShortCode) {
        mutableStateOf<List<FeedViewPost>>(emptyList())
    }
    var postsCursor by remember(actorHandleOrDid, rkeyOrShortCode, isShortCode) {
        mutableStateOf<String?>(null)
    }
    var isLoadingPostsMore by remember { mutableStateOf(false) }

    var isLoading by remember(actorHandleOrDid, rkeyOrShortCode, isShortCode) { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isFollowingAll by remember { mutableStateOf(false) }
    var errorMsg by remember(actorHandleOrDid, rkeyOrShortCode, isShortCode) { mutableStateOf<String?>(null) }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showQrModal by remember { mutableStateOf(false) }
    var optimisticSavedFeeds by remember(session.pinnedFeeds) { mutableStateOf(session.pinnedFeeds) }
    val followOverrides = remember { mutableStateMapOf<String, String?>() }
    val peopleListState = rememberLazyListState()

    fun parseStarterPackJson(root: JsonObject): StarterPackModel? {
        val sp = (root["starterPack"] as? JsonObject) ?: root
        val uri = (sp["uri"] as? JsonPrimitive)?.content ?: return null
        val cid = (sp["cid"] as? JsonPrimitive)?.content.orEmpty()
        val record = sp["record"] as? JsonObject ?: JsonObject(emptyMap())
        val name = (record["name"] as? JsonPrimitive)?.content ?: "Starter Pack"
        val description = (record["description"] as? JsonPrimitive)?.content
        val facets = (record["descriptionFacets"] as? JsonArray)?.mapNotNull {
            runCatching { AtProtoJson.decodeFromJsonElement<RichTextFacet>(it) }.getOrNull()
        }.orEmpty()
        val creator = sp["creator"]?.let {
            runCatching { AtProtoJson.decodeFromJsonElement<ProfileViewBasic>(it) }.getOrNull()
        } ?: return null
        val list = sp["list"]?.let {
            runCatching { AtProtoJson.decodeFromJsonElement<ListViewBasic>(it) }.getOrNull()
        }
        val sample = (sp["listItemsSample"] as? JsonArray)?.mapNotNull { itemEl ->
            val subj = (itemEl as? JsonObject)?.get("subject") ?: return@mapNotNull null
            runCatching { AtProtoJson.decodeFromJsonElement<ProfileView>(subj) }.getOrNull()
        }.orEmpty()
        val feeds = (sp["feeds"] as? JsonArray)?.mapNotNull { feedEl ->
            runCatching { AtProtoJson.decodeFromJsonElement<GeneratorView>(feedEl) }.getOrNull()
        }.orEmpty()
        val joinedWeek = sp["joinedWeekCount"]?.jsonPrimitive?.intOrNull
        val joinedAll = sp["joinedAllTimeCount"]?.jsonPrimitive?.intOrNull
        return StarterPackModel(
            uri = uri,
            cid = cid,
            name = name,
            description = description,
            descriptionFacets = facets,
            creator = creator,
            list = list,
            listItemsSample = sample,
            feeds = feeds,
            joinedWeekCount = joinedWeek,
            joinedAllTimeCount = joinedAll,
        )
    }

    suspend fun resolveShortCodeToNameAndRkey(code: String): Pair<String, String> {
        val shortUrl = "https://go.bsky.app/$code"
        val resolvedUrl = withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url(shortUrl)
                .get()
                .header("Accept", "application/json")
                .build()
            container.okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    resp.request.url.toString()
                } else {
                    val body = resp.body?.string().orEmpty()
                    val jsonUrl = runCatching {
                        (AtProtoJson.parseToJsonElement(body) as? JsonObject)
                            ?.get("url")?.jsonPrimitive?.content
                    }.getOrNull()
                    jsonUrl ?: resp.request.url.toString()
                }
            }
        }
        val parsed = DeepLinkParser.parseUri(resolvedUrl)
        if (parsed is DeepLinkTarget.RouteTarget && parsed.route is AppRoute.StarterPack) {
            return parsed.route.name to parsed.route.rkey
        }
        error("Starter pack short link could not be resolved")
    }

    suspend fun loadAll(refresh: Boolean = false) {
        if (refresh) isRefreshing = true else isLoading = starterPack == null
        errorMsg = null

        runCatching {
            val (targetName, targetRkey) = if (isShortCode) {
                resolveShortCodeToNameAndRkey(rkeyOrShortCode)
            } else {
                actorHandleOrDid to rkeyOrShortCode
            }
            resolvedRkey = targetRkey
            val did = resolveActorDid(container, targetName)
            val spUri = "at://$did/app.bsky.graph.starterpack/$targetRkey"
            val spJson = container.api.getStarterPack(spUri)
            val parsed = parseStarterPackJson(spJson) ?: error("Starter pack not found")
            starterPack = parsed

            val listUri = parsed.list?.uri
            if (!listUri.isNullOrBlank()) {
                val listJson = container.api.getList(listUri, cursor = null)
                val fullMembers = listJson["items"]?.jsonArray?.mapNotNull { el ->
                    val subj = (el as? JsonObject)?.get("subject") ?: return@mapNotNull null
                    runCatching { AtProtoJson.decodeFromJsonElement<ProfileView>(subj) }.getOrNull()
                }.orEmpty()
                members = fullMembers.ifEmpty { parsed.listItemsSample }
                membersCursor = (listJson["cursor"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

                val feedResp = container.api.getListFeed(listUri, cursor = null, limit = 30)
                posts = feedResp.feed
                postsCursor = feedResp.cursor
            } else {
                members = parsed.listItemsSample
                membersCursor = null
            }
        }.onFailure { ex ->
            errorMsg = ex.message ?: "Unable to load Starter Pack"
        }

        isLoading = false
        isRefreshing = false
    }

    suspend fun loadMoreMembers() {
        val listUri = starterPack?.list?.uri ?: return
        val cur = membersCursor?.takeIf { it.isNotBlank() } ?: return
        if (isLoadingMembersMore) return
        isLoadingMembersMore = true
        runCatching {
            val listJson = container.api.getList(listUri, cursor = cur)
            val more = listJson["items"]?.jsonArray?.mapNotNull { el ->
                val subj = (el as? JsonObject)?.get("subject") ?: return@mapNotNull null
                runCatching { AtProtoJson.decodeFromJsonElement<ProfileView>(subj) }.getOrNull()
            }.orEmpty()
            val next = (listJson["cursor"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
            members = (members + more).distinctBy { it.did }
            membersCursor = if (more.isEmpty()) null else next
        }
        isLoadingMembersMore = false
    }

    suspend fun loadMorePosts() {
        val listUri = starterPack?.list?.uri ?: return
        val cur = postsCursor?.takeIf { it.isNotBlank() } ?: return
        if (isLoadingPostsMore) return
        isLoadingPostsMore = true
        runCatching {
            container.api.getListFeed(listUri, cursor = cur, limit = 30)
        }.onSuccess { resp ->
            posts = (posts + resp.feed).distinctBy { it.post.uri + ":" + (it.reason?.toString() ?: "") }
            postsCursor = resp.cursor
        }
        isLoadingPostsMore = false
    }

    LaunchedEffect(actorHandleOrDid, rkeyOrShortCode, isShortCode) {
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

    fun isFollowingMember(profile: ProfileView): Boolean {
        return if (followOverrides.containsKey(profile.did)) {
            !followOverrides[profile.did].isNullOrBlank()
        } else {
            !profile.viewer?.following.isNullOrBlank()
        }
    }

    fun followAllMembers() {
        val did = session.currentDid ?: return
        val sp = starterPack ?: return
        if (isFollowingAll) return
        isFollowingAll = true
        haptics.confirm()

        scope.launch {
            runCatching {
                // Pull any remaining pages of the starter pack list so Follow All covers the whole pack
                var cur = membersCursor
                var pagesFetched = 0
                val listUri = sp.list?.uri
                while (!listUri.isNullOrBlank() && !cur.isNullOrBlank() && pagesFetched < 5) {
                    val listJson = container.api.getList(listUri, cursor = cur)
                    val more = listJson["items"]?.jsonArray?.mapNotNull { el ->
                        val subj = (el as? JsonObject)?.get("subject") ?: return@mapNotNull null
                        runCatching { AtProtoJson.decodeFromJsonElement<ProfileView>(subj) }.getOrNull()
                    }.orEmpty()
                    members = (members + more).distinctBy { it.did }
                    cur = (listJson["cursor"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
                    membersCursor = if (more.isEmpty()) null else cur
                    pagesFetched++
                    if (more.isEmpty()) break
                }

                val targets = members.filter { p ->
                    p.did != did && !isFollowingMember(p)
                }
                if (targets.isEmpty()) {
                    snackbarHostState.showSnackbar("You already follow everyone in this pack")
                    return@runCatching
                }

                val viaRef = if (sp.cid.isNotBlank()) StrongRef(sp.uri, sp.cid) else null
                val previousOverrides = targets.associate { it.did to followOverrides[it.did] }
                val succeededDids = mutableSetOf<String>()
                targets.forEach { followOverrides[it.did] = "pending://follow" }

                try {
                    targets.chunked(50).forEach { chunk ->
                        val writes = buildJsonArray {
                            val now = Instant.now().toString()
                            for (p in chunk) {
                                add(
                                    buildJsonObject {
                                        put("\$type", JsonPrimitive("com.atproto.repo.applyWrites#create"))
                                        put("collection", JsonPrimitive("app.bsky.graph.follow"))
                                        put(
                                            "value",
                                            buildJsonObject {
                                                put("\$type", JsonPrimitive("app.bsky.graph.follow"))
                                                put("subject", JsonPrimitive(p.did))
                                                viaRef?.let {
                                                    put("via", AtProtoJson.encodeToJsonElement(StrongRef.serializer(), it))
                                                }
                                                put("createdAt", JsonPrimitive(now))
                                            },
                                        )
                                    },
                                )
                            }
                        }
                        val response = container.xrpcClient.postJson(
                            nsid = "com.atproto.repo.applyWrites",
                            body = buildJsonObject {
                                put("repo", JsonPrimitive(did))
                                put("writes", writes)
                            },
                        ).jsonObject
                        val results = response["results"] as? JsonArray
                        chunk.forEachIndexed { idx, p ->
                            val createdUri = (results?.getOrNull(idx) as? JsonObject)
                                ?.get("uri")
                                ?.jsonPrimitive
                                ?.content
                            if (!createdUri.isNullOrBlank()) {
                                followOverrides[p.did] = createdUri
                            }
                            succeededDids.add(p.did)
                        }
                    }
                    snackbarHostState.showSnackbar("Followed ${targets.size} people")
                } catch (ex: Throwable) {
                    for (p in targets) {
                        if (p.did !in succeededDids) {
                            val prev = previousOverrides[p.did]
                            if (prev == null) followOverrides.remove(p.did) else followOverrides[p.did] = prev
                        }
                    }
                    throw ex
                }
            }.onFailure { ex ->
                if (ex is kotlinx.coroutines.CancellationException) throw ex
                snackbarHostState.showSnackbar(ex.message ?: "Failed to follow all")
            }
            isFollowingAll = false
        }
    }

    fun toggleSingleFollow(profile: ProfileView) {
        val did = session.currentDid ?: return
        val currentFollowUri = if (followOverrides.containsKey(profile.did)) {
            followOverrides[profile.did]
        } else {
            profile.viewer?.following
        }
        val currentlyFollowing = !currentFollowUri.isNullOrBlank()
        haptics.toggle(!currentlyFollowing)

        followOverrides[profile.did] = if (currentlyFollowing) null else "pending://follow"
        scope.launch {
            runCatching {
                if (currentlyFollowing) {
                    if (!currentFollowUri.isNullOrBlank() && !currentFollowUri.startsWith("pending://")) {
                        container.api.deleteRecord(did, "app.bsky.graph.follow", currentFollowUri.substringAfterLast('/'))
                    }
                } else {
                    val created = container.api.followActor(did, profile.did)
                    followOverrides[profile.did] = created.uri
                }
            }.onFailure { ex ->
                followOverrides[profile.did] = currentFollowUri
                snackbarHostState.showSnackbar(ex.message ?: "Failed to update follow")
            }
        }
    }

    fun mutateSavedFeed(uri: String, isPinAction: Boolean) {
        val did = session.currentDid ?: return
        haptics.tap()
        val prev = optimisticSavedFeeds
        scope.launch {
            runCatching {
                optimisticSavedFeeds = if (isPinAction) {
                    SavedFeedsHelper.togglePin(container, did, optimisticSavedFeeds, "feed", uri)
                } else {
                    SavedFeedsHelper.toggleSave(container, did, optimisticSavedFeeds, "feed", uri)
                }
            }.onFailure { ex ->
                optimisticSavedFeeds = prev
                snackbarHostState.showSnackbar(ex.message ?: "Failed to update feeds")
            }
        }
    }

    val sp = starterPack
    val creatorHandle = sp?.creator?.handle?.ifBlank { sp.creator.did } ?: actorHandleOrDid
    val shareUrl = "https://bsky.app/starter-pack/$creatorHandle/$resolvedRkey"
    val isOwner = session.currentDid != null && session.currentDid == sp?.creator?.did

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
                        text = sp?.name ?: "Starter Pack",
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    if (sp != null) {
                        if (isOwner) {
                            IconButton(onClick = { onNavigate(AppRoute.StarterPackEdit(resolvedRkey)) }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit starter pack")
                            }
                        }
                        IconButton(onClick = { showQrModal = true }) {
                            Icon(Icons.Filled.QrCode2, contentDescription = "Show QR code")
                        }
                        sp.list?.uri?.let { listUri ->
                            IconButton(onClick = { onOpenIn(OpenInSubject.ListRecord(listUri)) }) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open in ATProto app")
                            }
                        }
                        IconButton(onClick = {
                            LinkNavigationHelper.shareText(context, shareUrl, sp.name)
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = "Share starter pack")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = palette.contrast0.toComposeColor(),
    ) { padding ->
        if (isLoading && sp == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (errorMsg != null && sp == null) {
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
        } else if (sp != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                // Header
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
                            url = sp.creator.avatar,
                            contentDescription = sp.creator.displayName ?: sp.creator.handle,
                            size = 48.dp,
                            onClick = { onNavigate(AppRoute.Profile(creatorHandle)) },
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = sp.name,
                                style = MaterialTheme.typography.titleLarge,
                                color = palette.contrast1000.toComposeColor(),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "Starter pack by @$creatorHandle",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.primary500.toComposeColor(),
                                modifier = Modifier.clickable { onNavigate(AppRoute.Profile(creatorHandle)) },
                            )
                            val joined = sp.joinedAllTimeCount
                            if (joined != null && joined > 0) {
                                Text(
                                    text = "$joined ${if (joined == 1) "person has" else "people have"} joined using this pack",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.contrast600.toComposeColor(),
                                )
                            }
                        }
                    }

                    if (!sp.description.isNullOrBlank()) {
                        RichTextView(
                            text = sp.description,
                            facets = sp.descriptionFacets,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = palette.contrast800.toComposeColor(),
                            ),
                            onMentionClick = { onNavigate(AppRoute.Profile(it)) },
                            onTagClick = { onNavigate(AppRoute.Hashtag(it)) },
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (session.hasSession) {
                            val allFollowed = members.isNotEmpty() && membersCursor == null && members.all {
                                it.did == session.currentDid || isFollowingMember(it)
                            }
                            Button(
                                onClick = ::followAllMembers,
                                enabled = !isFollowingAll && !allFollowed && members.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                            ) {
                                if (isFollowingAll) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Following…")
                                } else if (allFollowed) {
                                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Following all")
                                } else {
                                    Icon(Icons.Filled.GroupAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Follow all")
                                }
                            }
                        }
                        OutlinedButton(onClick = { showQrModal = true }) {
                            Icon(Icons.Filled.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("QR Code")
                        }
                    }
                }
                HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)

                val tabLabels = listOf("People", "Feeds", "Posts")
                SecondaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = palette.contrast0.toComposeColor(),
                    contentColor = palette.contrast1000.toComposeColor(),
                ) {
                    tabLabels.forEachIndexed { idx, label ->
                        Tab(
                            selected = selectedTab == idx,
                            onClick = { selectedTab = idx },
                            text = { Text(label) },
                        )
                    }
                }

                when (selectedTab) {
                    0 -> {
                        PullToRefreshBox(
                            isRefreshing = isRefreshing,
                            onRefresh = { scope.launch { loadAll(refresh = true) } },
                            modifier = Modifier.weight(1f),
                        ) {
                            if (members.isEmpty()) {
                                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                                    Text("No people in this starter pack.", color = palette.contrast600.toComposeColor())
                                }
                            } else {
                                LazyColumn(
                                    state = peopleListState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(bottom = 84.dp),
                                ) {
                                    items(items = members, key = { it.did }) { p ->
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
                                        val isMe = p.did == session.currentDid
                                        val following = isFollowingMember(p)
                                        ActorListRow(
                                            profile = basic,
                                            description = p.description,
                                            onClick = { onNavigate(AppRoute.Profile(p.handle.ifBlank { p.did })) },
                                            trailingContent = if (session.hasSession && !isMe) {
                                                {
                                                    if (following) {
                                                        OutlinedButton(onClick = { toggleSingleFollow(p) }) {
                                                            Text("Following")
                                                        }
                                                    } else {
                                                        Button(onClick = { toggleSingleFollow(p) }) {
                                                            Text("Follow")
                                                        }
                                                    }
                                                }
                                            } else {
                                                null
                                            },
                                        )
                                    }
                                    if (isLoadingMembersMore) {
                                        item(key = "sp_members_loader") {
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
                    1 -> {
                        if (sp.feeds.isEmpty()) {
                            Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                Text("No feeds included in this starter pack.", color = palette.contrast600.toComposeColor())
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentPadding = PaddingValues(bottom = 84.dp),
                            ) {
                                items(items = sp.feeds, key = { it.uri }) { gen ->
                                    val savedPref = optimisticSavedFeeds.firstOrNull { it.value == gen.uri }
                                    val repoDid = gen.uri.removePrefix("at://").substringBefore('/')
                                    val feedRkey = gen.uri.substringAfterLast('/')
                                    GeneratorListRow(
                                        generator = gen,
                                        isSaved = savedPref != null,
                                        isPinned = savedPref?.pinned == true,
                                        showControls = session.hasSession,
                                        onOpen = { onNavigate(AppRoute.CustomFeed(repoDid, feedRkey)) },
                                        onToggleSave = { mutateSavedFeed(gen.uri, isPinAction = false) },
                                        onTogglePin = { mutateSavedFeed(gen.uri, isPinAction = true) },
                                    )
                                }
                            }
                        }
                    }
                    else -> {
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
                            emptyMessage = "No posts from starter pack members yet.",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }

    if (showQrModal && sp != null) {
        StarterPackQrCodeModal(
            packName = sp.name,
            creatorHandle = creatorHandle,
            shareUrl = shareUrl,
            onDismiss = { showQrModal = false },
        )
    }
}

@Composable
private fun StarterPackQrCodeModal(
    packName: String,
    creatorHandle: String,
    shareUrl: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val palette = LocalAlfPalette.current
    val qrBitmap = remember(shareUrl) { generateQrBitmap(shareUrl, 512) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(packName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = "by @$creatorHandle",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.contrast600.toComposeColor(),
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (qrBitmap != null) {
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "Starter pack QR code",
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Text(
                    text = "Scan with Caelum Invite Scanner or any camera to open this starter pack.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.contrast700.toComposeColor(),
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                LinkNavigationHelper.shareText(context, shareUrl, packName)
            }) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Share")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    LinkNavigationHelper.copyToClipboard(context, "Starter Pack URL", shareUrl)
                }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copy link")
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        },
    )
}

private fun generateQrBitmap(content: String, sizePx: Int): Bitmap? {
    return runCatching {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (matrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE
            }
        }
        Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }.getOrNull()
}
