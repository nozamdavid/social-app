package am.noz.caelum.ui.search

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import am.noz.caelum.core.i18n.tr
import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.model.MutedWord
import am.noz.caelum.core.model.PostView
import am.noz.caelum.core.storage.ModerationUserSettings
import am.noz.caelum.ui.components.LinkNavigationHelper
import am.noz.caelum.ui.components.LocalAppContainer
import am.noz.caelum.ui.components.rememberAccountCoroutineScope
import am.noz.caelum.ui.navigation.AppRoute
import am.noz.caelum.ui.openin.OpenInSubject
import am.noz.caelum.ui.post.EmbedImageItem
import am.noz.caelum.ui.theme.LocalAlfPalette
import am.noz.caelum.ui.theme.toComposeColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HashtagScreen(
    tag: String,
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
    val scope = rememberAccountCoroutineScope()

    val decodedTag = remember(tag) { Uri.decode(tag).trim() }
    val isCashtag = decodedTag.startsWith("$")
    val cleanTag = remember(decodedTag) { decodedTag.removePrefix("#") }
    val fullTag = remember(decodedTag, isCashtag) {
        if (isCashtag) decodedTag else "#$cleanTag"
    }

    val modSettingsFlow = remember(session.currentDid) {
        session.currentDid?.let { container.preferences.observeModerationSettings(it) }
    }
    val modSettings = modSettingsFlow?.collectAsState(initial = ModerationUserSettings())?.value
    val isMuted = remember(modSettings, cleanTag, fullTag) {
        modSettings?.mutedWords?.any {
            it.value.equals(cleanTag, ignoreCase = true) ||
                it.value.equals(fullTag, ignoreCase = true)
        } == true
    }

    fun toggleMuteTag() {
        val did = session.currentDid ?: return
        val wasMuted = isMuted
        val previousWords = modSettings?.mutedWords
        val newEntry = MutedWord(
            id = UUID.randomUUID().toString(),
            value = cleanTag,
            targets = listOf("tag"),
        )
        scope.launch {
            runCatching {
                container.preferences.updateModerationSettings(did) { current ->
                    if (wasMuted) {
                        current.copy(
                            mutedWords = current.mutedWords.filterNot {
                                it.value.equals(cleanTag, ignoreCase = true) ||
                                    it.value.equals(fullTag, ignoreCase = true)
                            }
                        )
                    } else {
                        current.copy(mutedWords = current.mutedWords + newEntry)
                    }
                }
                val prefs = container.api.getActorPreferences().mapNotNull { it as? JsonObject }.toMutableList()
                val existing = prefs.firstOrNull {
                    (it["\$type"] as? JsonPrimitive)?.content == "app.bsky.actor.defs#mutedWordsPref"
                }
                val existingItems = (existing?.get("items") as? JsonArray)?.toMutableList() ?: mutableListOf()
                val updatedItems = if (wasMuted) {
                    existingItems.filterNot { el ->
                        val decoded = runCatching { AtProtoJson.decodeFromJsonElement(MutedWord.serializer(), el) }.getOrNull()
                        decoded != null && (
                            decoded.value.equals(cleanTag, ignoreCase = true) ||
                                decoded.value.equals(fullTag, ignoreCase = true)
                            )
                    }
                } else {
                    existingItems + AtProtoJson.encodeToJsonElement(MutedWord.serializer(), newEntry)
                }
                prefs.removeAll { (it["\$type"] as? JsonPrimitive)?.content == "app.bsky.actor.defs#mutedWordsPref" }
                prefs.add(
                    buildJsonObject {
                        put("\$type", JsonPrimitive("app.bsky.actor.defs#mutedWordsPref"))
                        put("items", JsonArray(updatedItems))
                    }
                )
                container.api.putActorPreferences(JsonArray(prefs))
                container.sessionManager.syncPreferencesFromServer(did)
            }.onFailure { ex ->
                if (ex is CancellationException) throw ex
                if (previousWords != null) {
                    runCatching {
                        container.preferences.updateModerationSettings(did) { current ->
                            current.copy(mutedWords = previousWords)
                        }
                    }
                }
                Toast.makeText(context, ex.message ?: "Failed to update muted tag", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val pagerState = rememberPagerState(pageCount = { 2 })
    var topState by remember(fullTag) { mutableStateOf(PostSearchPageState()) }
    var latestState by remember(fullTag) { mutableStateOf(PostSearchPageState()) }

    suspend fun loadTab(sort: String, refresh: Boolean = false, loadMore: Boolean = false) {
        val current = if (sort == "top") topState else latestState
        if (loadMore && (current.isLoadingMore || current.cursor.isNullOrBlank())) return
        if (!refresh && !loadMore && current.hasLoaded) return

        val next = current.copy(
            isLoading = !refresh && !loadMore && current.items.isEmpty(),
            isRefreshing = refresh,
            isLoadingMore = loadMore,
            error = null,
        )
        if (sort == "top") topState = next else latestState = next

        runCatching {
            container.api.searchPosts(
                query = fullTag,
                sort = sort,
                cursor = if (loadMore) current.cursor else null,
                limit = 25,
            )
        }.onSuccess { resp ->
            val merged = if (loadMore) {
                (current.items + resp.posts).distinctBy { it.uri }
            } else {
                resp.posts
            }
            val done = PostSearchPageState(
                items = merged,
                cursor = resp.cursor,
                hasLoaded = true,
            )
            if (sort == "top") topState = done else latestState = done
        }.onFailure { ex ->
            if (ex is CancellationException) throw ex
            val err = current.copy(
                isLoading = false,
                isRefreshing = false,
                isLoadingMore = false,
                error = ex.message ?: "Unable to load posts for $fullTag",
                hasLoaded = true,
            )
            if (sort == "top") topState = err else latestState = err
        }
    }

    LaunchedEffect(pagerState.currentPage, fullTag) {
        if (pagerState.currentPage == 0) {
            loadTab("top")
        } else {
            loadTab("latest")
        }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(palette.contrast0.toComposeColor())) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = palette.contrast0.toComposeColor(),
                    ),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = tr("Back"),
                            )
                        }
                    },
                    title = {
                        Text(
                            text = fullTag,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    actions = {
                        if (session.hasSession) {
                            FilterChip(
                                selected = isMuted,
                                onClick = { toggleMuteTag() },
                                label = {
                                    Text(
                                        text = if (isMuted) tr("Muted") else tr("Mute {tag}", mapOf("tag" to fullTag)),
                                        maxLines = 1,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isMuted) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = palette.negative100.toComposeColor(),
                                    selectedLabelColor = palette.negative600.toComposeColor(),
                                    selectedLeadingIconColor = palette.negative600.toComposeColor(),
                                ),
                                modifier = Modifier.padding(end = 4.dp),
                            )
                        }
                        IconButton(
                            onClick = {
                                LinkNavigationHelper.shareText(
                                    context,
                                    "https://bsky.app/hashtag/${Uri.encode(cleanTag)}",
                                )
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = tr("Share"),
                            )
                        }
                    },
                )

                val tabs = listOf(tr("Top"), tr("Latest"))
                SecondaryTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = palette.contrast0.toComposeColor(),
                    contentColor = palette.contrast1000.toComposeColor(),
                ) {
                    tabs.forEachIndexed { idx, label ->
                        val selected = pagerState.currentPage == idx
                        Tab(
                            selected = selected,
                            onClick = { scope.launch { pagerState.animateScrollToPage(idx) } },
                            text = {
                                Text(
                                    text = label,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            },
                        )
                    }
                }
                HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)
            }
        },
        containerColor = palette.contrast0.toComposeColor(),
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) { page ->
            val sort = if (page == 0) "top" else "latest"
            val state = if (page == 0) topState else latestState
            SearchPostsFeedTab(
                pageState = state,
                emptyMessage = tr("No posts found for {tag}", mapOf("tag" to fullTag)),
                onRefresh = { scope.launch { loadTab(sort, refresh = true) } },
                onLoadMore = { scope.launch { loadTab(sort, loadMore = true) } },
                onNavigate = onNavigate,
                onOpenComposer = onOpenComposer,
                onOpenEditPost = onOpenEditPost,
                onOpenIn = onOpenIn,
                onOpenReport = onOpenReport,
                onOpenLightbox = onOpenLightbox,
                onOpenVideoLightbox = onOpenVideoLightbox,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicScreen(
    topic: String,
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
    val scope = rememberCoroutineScope()

    val decodedTopic = remember(topic) { Uri.decode(topic).trim() }
    val pagerState = rememberPagerState(pageCount = { 2 })
    var topState by remember(decodedTopic) { mutableStateOf(PostSearchPageState()) }
    var latestState by remember(decodedTopic) { mutableStateOf(PostSearchPageState()) }

    suspend fun loadTab(sort: String, refresh: Boolean = false, loadMore: Boolean = false) {
        val current = if (sort == "top") topState else latestState
        if (loadMore && (current.isLoadingMore || current.cursor.isNullOrBlank())) return
        if (!refresh && !loadMore && current.hasLoaded) return

        val next = current.copy(
            isLoading = !refresh && !loadMore && current.items.isEmpty(),
            isRefreshing = refresh,
            isLoadingMore = loadMore,
            error = null,
        )
        if (sort == "top") topState = next else latestState = next

        runCatching {
            container.api.searchPosts(
                query = decodedTopic,
                sort = sort,
                cursor = if (loadMore) current.cursor else null,
                limit = 25,
            )
        }.onSuccess { resp ->
            val merged = if (loadMore) {
                (current.items + resp.posts).distinctBy { it.uri }
            } else {
                resp.posts
            }
            val done = PostSearchPageState(
                items = merged,
                cursor = resp.cursor,
                hasLoaded = true,
            )
            if (sort == "top") topState = done else latestState = done
        }.onFailure { ex ->
            if (ex is CancellationException) throw ex
            val err = current.copy(
                isLoading = false,
                isRefreshing = false,
                isLoadingMore = false,
                error = ex.message ?: "Unable to load topic posts",
                hasLoaded = true,
            )
            if (sort == "top") topState = err else latestState = err
        }
    }

    LaunchedEffect(pagerState.currentPage, decodedTopic) {
        if (pagerState.currentPage == 0) {
            loadTab("top")
        } else {
            loadTab("latest")
        }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(palette.contrast0.toComposeColor())) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = palette.contrast0.toComposeColor(),
                    ),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = tr("Back"),
                            )
                        }
                    },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = decodedTopic,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                LinkNavigationHelper.shareText(
                                    context,
                                    "https://bsky.app/topic/${Uri.encode(decodedTopic)}",
                                )
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = tr("Share"),
                            )
                        }
                    },
                )

                val tabs = listOf(tr("Top"), tr("Latest"))
                SecondaryTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = palette.contrast0.toComposeColor(),
                    contentColor = palette.contrast1000.toComposeColor(),
                ) {
                    tabs.forEachIndexed { idx, label ->
                        val selected = pagerState.currentPage == idx
                        Tab(
                            selected = selected,
                            onClick = { scope.launch { pagerState.animateScrollToPage(idx) } },
                            text = {
                                Text(
                                    text = label,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            },
                        )
                    }
                }
                HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)
            }
        },
        containerColor = palette.contrast0.toComposeColor(),
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) { page ->
            val sort = if (page == 0) "top" else "latest"
            val state = if (page == 0) topState else latestState
            SearchPostsFeedTab(
                pageState = state,
                emptyMessage = tr("No posts found for “{topic}”", mapOf("topic" to decodedTopic)),
                onRefresh = { scope.launch { loadTab(sort, refresh = true) } },
                onLoadMore = { scope.launch { loadTab(sort, loadMore = true) } },
                onNavigate = onNavigate,
                onOpenComposer = onOpenComposer,
                onOpenEditPost = onOpenEditPost,
                onOpenIn = onOpenIn,
                onOpenReport = onOpenReport,
                onOpenLightbox = onOpenLightbox,
                onOpenVideoLightbox = onOpenVideoLightbox,
            )
        }
    }
}
