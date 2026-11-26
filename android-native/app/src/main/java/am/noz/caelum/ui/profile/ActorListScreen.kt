package am.noz.caelum.ui.profile

import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import am.noz.caelum.core.i18n.LocalI18n
import am.noz.caelum.core.i18n.tr
import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.model.ProfileView
import am.noz.caelum.core.model.ProfileViewBasic
import am.noz.caelum.ui.components.CaelumAvatar
import am.noz.caelum.ui.components.DisplayNameRow
import am.noz.caelum.ui.components.LocalAppContainer
import am.noz.caelum.ui.components.rememberAccountCoroutineScope
import am.noz.caelum.ui.components.rememberHaptics
import am.noz.caelum.ui.navigation.AppRoute
import am.noz.caelum.ui.theme.LocalAlfPalette
import am.noz.caelum.ui.theme.toComposeColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

sealed interface ActorListMode {
    data class Followers(val actor: String) : ActorListMode
    data class Follows(val actor: String) : ActorListMode
    data class KnownFollowers(val actor: String) : ActorListMode
    data class PostLikedBy(val actor: String, val rkey: String) : ActorListMode
    data class PostRepostedBy(val actor: String, val rkey: String) : ActorListMode
    data class FeedLikedBy(val actor: String, val rkey: String) : ActorListMode
    data class LabelerLikedBy(val actor: String) : ActorListMode
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActorListScreen(
    mode: ActorListMode,
    onBack: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val palette = LocalAlfPalette.current
    val i18n = LocalI18n.current
    val session by container.sessionManager.state.collectAsState()
    val settings by container.preferences.settingsFlow.collectAsState(initial = am.noz.caelum.core.storage.AppSettingsSnapshot())
    val haptics = rememberHaptics(settings.disableHaptics)
    val scope = rememberAccountCoroutineScope()
    val listState = rememberLazyListState()

    var actors by remember(mode, session.currentDid) { mutableStateOf<List<ProfileView>>(emptyList()) }
    var cursor by remember(mode, session.currentDid) { mutableStateOf<String?>(null) }
    var isLoading by remember(mode, session.currentDid) { mutableStateOf(true) }
    var isRefreshing by remember(mode, session.currentDid) { mutableStateOf(false) }
    var isLoadingMore by remember(mode, session.currentDid) { mutableStateOf(false) }
    var errorMsg by remember(mode, session.currentDid) { mutableStateOf<String?>(null) }

    // Optimistic follow URI overrides keyed by DID (`""` means explicitly unfollowed)
    val followOverrides = remember(mode, session.currentDid) { mutableStateMapOf<String, String>() }
    val pendingFollowDids = remember(mode, session.currentDid) { mutableStateMapOf<String, Boolean>() }

    suspend fun resolveDid(handleOrDid: String): String {
        return if (handleOrDid.startsWith("did:")) {
            handleOrDid
        } else {
            container.identityResolver.resolveHandle(handleOrDid)
                ?: error("Could not resolve @$handleOrDid")
        }
    }

    suspend fun loadPage(refresh: Boolean = false, loadMore: Boolean = false) {
        if (loadMore && (isLoadingMore || cursor.isNullOrBlank())) return
        if (!refresh && !loadMore && actors.isNotEmpty()) return

        if (refresh) isRefreshing = true
        else if (loadMore) isLoadingMore = true
        else isLoading = true
        errorMsg = null

        val nextCursor = if (loadMore) cursor else null

        runCatching {
            when (mode) {
                is ActorListMode.Followers -> {
                    val res = container.api.getFollowers(mode.actor, nextCursor)
                    val list = decodeProfileArray(res["followers"] as? JsonArray)
                    val cur = (res["cursor"] as? JsonPrimitive)?.content
                    list to cur
                }
                is ActorListMode.Follows -> {
                    val res = container.api.getFollows(mode.actor, nextCursor)
                    val list = decodeProfileArray(res["follows"] as? JsonArray)
                    val cur = (res["cursor"] as? JsonPrimitive)?.content
                    list to cur
                }
                is ActorListMode.KnownFollowers -> {
                    val res = container.api.getKnownFollowers(mode.actor, nextCursor)
                    val list = decodeProfileArray(res["followers"] as? JsonArray)
                    val cur = (res["cursor"] as? JsonPrimitive)?.content
                    list to cur
                }
                is ActorListMode.PostLikedBy -> {
                    val did = resolveDid(mode.actor)
                    val res = container.api.getLikedBy("at://$did/app.bsky.feed.post/${mode.rkey}", nextCursor)
                    val list = decodeLikesArray(res["likes"] as? JsonArray)
                    val cur = (res["cursor"] as? JsonPrimitive)?.content
                    list to cur
                }
                is ActorListMode.PostRepostedBy -> {
                    val did = resolveDid(mode.actor)
                    val res = container.api.getRepostedBy("at://$did/app.bsky.feed.post/${mode.rkey}", nextCursor)
                    val list = decodeProfileArray(res["repostedBy"] as? JsonArray)
                    val cur = (res["cursor"] as? JsonPrimitive)?.content
                    list to cur
                }
                is ActorListMode.FeedLikedBy -> {
                    val did = resolveDid(mode.actor)
                    val res = container.api.getLikedBy("at://$did/app.bsky.feed.generator/${mode.rkey}", nextCursor)
                    val list = decodeLikesArray(res["likes"] as? JsonArray)
                    val cur = (res["cursor"] as? JsonPrimitive)?.content
                    list to cur
                }
                is ActorListMode.LabelerLikedBy -> {
                    val did = resolveDid(mode.actor)
                    val res = container.api.getLikedBy("at://$did/app.bsky.labeler.service/self", nextCursor)
                    val list = decodeLikesArray(res["likes"] as? JsonArray)
                    val cur = (res["cursor"] as? JsonPrimitive)?.content
                    list to cur
                }
            }
        }.onSuccess { (items, newCursor) ->
            actors = if (loadMore) {
                (actors + items).distinctBy { it.did }
            } else {
                items.distinctBy { it.did }
            }
            cursor = newCursor
        }.onFailure { ex ->
            if (ex is CancellationException) throw ex
            errorMsg = ex.message ?: i18n.translate("Unable to load list")
        }

        isLoading = false
        isRefreshing = false
        isLoadingMore = false
    }

    LaunchedEffect(mode, session.currentDid) {
        loadPage()
    }

    val shouldLoadMore by remember(listState, actors.size, cursor) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            actors.isNotEmpty() && !cursor.isNullOrBlank() && lastVisible >= actors.size - 6
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) loadPage(loadMore = true)
    }

    val titleText = when (mode) {
        is ActorListMode.Followers -> tr("Followers")
        is ActorListMode.Follows -> tr("Following")
        is ActorListMode.KnownFollowers -> tr("Followers you know")
        is ActorListMode.PostLikedBy,
        is ActorListMode.FeedLikedBy,
        is ActorListMode.LabelerLikedBy,
        -> tr("Liked by")
        is ActorListMode.PostRepostedBy -> tr("Reposted by")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.contrast0.toComposeColor()),
                title = { Text(titleText) },
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
            onRefresh = { scope.launch { loadPage(refresh = true) } },
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
                errorMsg != null && actors.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(errorMsg.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { scope.launch { loadPage(refresh = true) } }) {
                            Text(tr("Try again"))
                        }
                    }
                }
                actors.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = tr("No users to display."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.contrast600.toComposeColor(),
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 32.dp),
                    ) {
                        itemsIndexed(
                            items = actors,
                            key = { _, item -> item.did },
                        ) { _, actor ->
                            val followUri = if (followOverrides.containsKey(actor.did)) {
                                followOverrides[actor.did]?.takeIf { it.isNotEmpty() }
                            } else {
                                actor.viewer?.following
                            }
                            val isFollowing = !followUri.isNullOrBlank()
                            val isPending = pendingFollowDids[actor.did] == true

                            ActorListRow(
                                actor = actor,
                                isMe = actor.did == session.currentDid,
                                canFollow = session.hasSession &&
                                    actor.did != session.currentDid &&
                                    actor.viewer?.blockedBy != true &&
                                    actor.viewer?.blocking == null,
                                isFollowing = isFollowing,
                                isPending = isPending,
                                onToggleFollow = {
                                    val myDid = session.currentDid ?: return@ActorListRow
                                    if (isPending) return@ActorListRow
                                    haptics.confirm()
                                    pendingFollowDids[actor.did] = true
                                    scope.launch {
                                        runCatching {
                                            if (isFollowing) {
                                                val rkey = followUri!!.substringAfterLast('/')
                                                followOverrides[actor.did] = ""
                                                container.api.deleteRecord(myDid, "app.bsky.graph.follow", rkey)
                                            } else {
                                                followOverrides[actor.did] = "pending"
                                                val created = container.api.followActor(myDid, actor.did)
                                                followOverrides[actor.did] = created.uri
                                            }
                                        }.onFailure { ex ->
                                            if (followUri != null) {
                                                followOverrides[actor.did] = followUri
                                            } else {
                                                followOverrides.remove(actor.did)
                                            }
                                            Toast.makeText(
                                                context,
                                                ex.message ?: i18n.translate("Unable to update follow"),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                        pendingFollowDids.remove(actor.did)
                                    }
                                },
                                onClick = {
                                    onNavigate(AppRoute.Profile(actor.handle.ifBlank { actor.did }))
                                },
                            )
                        }

                        if (isLoadingMore) {
                            item(key = "actor_list_footer") {
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

@Composable
private fun ActorListRow(
    actor: ProfileView,
    isMe: Boolean,
    canFollow: Boolean,
    isFollowing: Boolean,
    isPending: Boolean,
    onToggleFollow: () -> Unit,
    onClick: () -> Unit,
) {
    val palette = LocalAlfPalette.current
    val muted = palette.contrast600.toComposeColor()
    val basic = remember(actor) {
        ProfileViewBasic(
            did = actor.did,
            handle = actor.handle,
            displayName = actor.displayName,
            pronouns = actor.pronouns,
            avatar = actor.avatar,
            associated = actor.associated,
            viewer = actor.viewer,
            labels = actor.labels,
            verification = actor.verification,
            status = actor.status,
        )
    }

    Column(
        modifier = Modifier
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
                url = actor.avatar,
                contentDescription = actor.displayName ?: actor.handle,
                size = 44.dp,
                isLive = actor.status?.isActive == true,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        DisplayNameRow(
                            profile = basic,
                            showHandle = false,
                            showPronouns = true,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = if (actor.handle == "handle.invalid") {
                                    tr("⚠Invalid Handle")
                                } else {
                                    "@${actor.handle}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (actor.viewer?.followedBy != null && !isMe) {
                                Text(
                                    text = tr("Follows you"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.contrast700.toComposeColor(),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(palette.contrast50.toComposeColor())
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                            if (actor.viewer?.muted == true) {
                                Text(
                                    text = tr("Muted"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.contrast700.toComposeColor(),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(palette.contrast50.toComposeColor())
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }

                    if (canFollow) {
                        Spacer(Modifier.size(8.dp))
                        if (isFollowing) {
                            OutlinedButton(
                                onClick = onToggleFollow,
                                enabled = !isPending,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp),
                            ) {
                                Text(
                                    text = tr("Following"),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        } else {
                            Button(
                                onClick = onToggleFollow,
                                enabled = !isPending,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = palette.primary500.toComposeColor(),
                                    contentColor = Color.White,
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp),
                            ) {
                                Text(
                                    text = if (actor.viewer?.followedBy != null) tr("Follow back") else tr("Follow"),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }

                if (!actor.description.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = actor.description.trim(),
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

private fun decodeProfileArray(arr: JsonArray?): List<ProfileView> {
    if (arr == null) return emptyList()
    return arr.mapNotNull { el ->
        runCatching { AtProtoJson.decodeFromJsonElement<ProfileView>(el) }.getOrNull()
    }
}

private fun decodeLikesArray(arr: JsonArray?): List<ProfileView> {
    if (arr == null) return emptyList()
    return arr.mapNotNull { el ->
        val obj = el as? JsonObject ?: return@mapNotNull null
        val actorObj = obj["actor"] ?: return@mapNotNull null
        runCatching { AtProtoJson.decodeFromJsonElement<ProfileView>(actorObj) }.getOrNull()
    }
}
