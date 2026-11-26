package am.noz.caelum.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import am.noz.caelum.core.i18n.LocalI18n
import am.noz.caelum.core.i18n.tr
import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.model.GeneratorView
import am.noz.caelum.core.model.PostView
import am.noz.caelum.core.model.ProfileView
import am.noz.caelum.core.model.ProfileViewBasic
import am.noz.caelum.core.model.TrendTopicView
import am.noz.caelum.core.moderation.ModerationEngine
import am.noz.caelum.core.session.ScreenStateCache
import am.noz.caelum.core.storage.AppSettingsSnapshot
import am.noz.caelum.core.storage.SearchHistoryEntity
import am.noz.caelum.ui.components.CaelumAvatar
import am.noz.caelum.ui.components.DisplayNameRow
import am.noz.caelum.ui.components.LinkNavigationHelper
import am.noz.caelum.ui.components.LocalAppContainer
import am.noz.caelum.ui.components.LocalScreenStateCache
import am.noz.caelum.ui.components.ShellIcons
import am.noz.caelum.ui.navigation.AppRoute
import am.noz.caelum.ui.navigation.DeepLinkParser
import am.noz.caelum.ui.navigation.DeepLinkTarget
import am.noz.caelum.ui.navigation.SearchFilters
import am.noz.caelum.ui.openin.OpenInSubject
import am.noz.caelum.ui.post.EmbedImageItem
import am.noz.caelum.ui.post.PostActionIcons
import am.noz.caelum.ui.post.formatCompactCount
import am.noz.caelum.ui.theme.LocalAlfPalette
import am.noz.caelum.ui.theme.toComposeColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.launch

private data class ExploreCategoryOption(
    val key: String,
    val label: String,
)

private val SUGGESTED_CATEGORIES = listOf(
    ExploreCategoryOption("all", "All"),
    ExploreCategoryOption("art", "Art"),
    ExploreCategoryOption("tech", "Tech"),
    ExploreCategoryOption("science", "Science"),
    ExploreCategoryOption("gaming", "Video Games"),
    ExploreCategoryOption("news", "News"),
    ExploreCategoryOption("music", "Music"),
    ExploreCategoryOption("photography", "Photography"),
    ExploreCategoryOption("books", "Books"),
    ExploreCategoryOption("nature", "Nature"),
)

private data class PeopleSearchState(
    val items: List<ProfileView> = emptyList(),
    val cursor: String? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val hasLoaded: Boolean = false,
)

private data class FeedsSearchState(
    val items: List<GeneratorView> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val hasLoaded: Boolean = false,
)

private data class ParsedSearchQuery(
    val cleanQuery: String,
    val inlineFromMe: Boolean,
    val inlineLang: String,
    val inlineSince: String,
)

private val SINCE_DATE_REGEX = Regex("^\\d{4}-\\d{2}-\\d{2}$")
private val SEARCH_TOKEN_REGEX = Regex("""(?:[^\s"]+|"[^"]*"?)+""")

private fun parseSearchOperators(raw: String): ParsedSearchQuery {
    val tokens = SEARCH_TOKEN_REGEX.findAll(raw).map { it.value }
    val remaining = mutableListOf<String>()
    var inlineFromMe = false
    var inlineLang = ""
    var inlineSince = ""

    for (tok in tokens) {
        when {
            tok.equals("from:me", ignoreCase = true) -> inlineFromMe = true
            tok.startsWith("lang:", ignoreCase = true) && tok.length > 5 -> {
                inlineLang = tok.substring(5).lowercase()
            }
            tok.startsWith("since:", ignoreCase = true) -> {
                val datePart = tok.substring(6)
                if (SINCE_DATE_REGEX.matches(datePart)) {
                    inlineSince = datePart
                } else {
                    remaining.add(tok)
                }
            }
            else -> remaining.add(tok)
        }
    }
    return ParsedSearchQuery(
        cleanQuery = remaining.joinToString(" "),
        inlineFromMe = inlineFromMe,
        inlineLang = inlineLang,
        inlineSince = inlineSince,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchExploreScreen(
    initialQuery: String? = null,
    initialFilters: SearchFilters = SearchFilters(),
    onOpenDrawer: () -> Unit,
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
    val session by container.sessionManager.state.collectAsState()
    val settings by container.preferences.settingsFlow.collectAsState(initial = AppSettingsSnapshot())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val accountKey = session.currentDid ?: "guest"
    val searchHistoryDao = remember { container.database.searchHistoryDao() }
    val historyItems by remember(accountKey) {
        searchHistoryDao.observe(accountKey)
    }.collectAsState(initial = emptyList())

    var draftQuery by rememberSaveable(initialQuery) { mutableStateOf(initialQuery.orEmpty()) }
    var submittedQuery by rememberSaveable(initialQuery) { mutableStateOf(initialQuery?.trim().orEmpty()) }
    var isSearchFocused by remember { mutableStateOf(false) }

    var filters by rememberSaveable(initialQuery, initialFilters, stateSaver = searchFiltersSaver) {
        mutableStateOf(initialFilters)
    }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var showTrendingOptions by remember { mutableStateOf(false) }

    // Typeahead state
    var typeaheadActors by remember { mutableStateOf<List<ProfileViewBasic>>(emptyList()) }
    var isTypeaheadLoading by remember { mutableStateOf(false) }

    LaunchedEffect(draftQuery, isSearchFocused) {
        val q = draftQuery.trim()
        if (!isSearchFocused || q.isEmpty()) {
            typeaheadActors = emptyList()
            isTypeaheadLoading = false
            return@LaunchedEffect
        }
        isTypeaheadLoading = true
        delay(250L)
        runCatching {
            container.api.searchActorsTypeahead(q.removePrefix("@"), limit = 8)
        }.onSuccess { actors ->
            typeaheadActors = actors
        }.onFailure {
            typeaheadActors = emptyList()
        }
        isTypeaheadLoading = false
    }

    // Explore state
    val cachedExplore = remember(session.currentDid) { screenCache.searchExplore }
    var trends by remember(session.currentDid) { mutableStateOf(cachedExplore?.trends.orEmpty()) }
    var trendLanguages by remember(session.currentDid) { mutableStateOf(cachedExplore?.trendLanguages) }
    var trendsFailed by remember(session.currentDid) { mutableStateOf(false) }
    val moderatedTrends = remember(trends, session.moderationOpts) {
        trends.asSequence()
            .filter { trend ->
                ModerationEngine.matchMuteWords(
                    mutedWords = session.moderationOpts.prefs.mutedWords,
                    text = listOfNotNull(trend.displayName, trend.description, trend.category).joinToString(" "),
                    facets = null,
                    outlineTags = emptyList(),
                    languages = emptyList(),
                    authorIsFollowing = false,
                ).isNullOrEmpty()
            }
            .take(10)
            .map { trend ->
                trend.copy(actors = trend.actors.filter { actor ->
                    val moderation = ModerationEngine.moderateProfile(actor, session.moderationOpts)
                    !moderation.blocked && !moderation.muted
                })
            }
            .toList()
    }
    var popularFeeds by remember(session.currentDid) { mutableStateOf(cachedExplore?.popularFeeds.orEmpty()) }
    var selectedCategory by rememberSaveable { mutableStateOf(cachedExplore?.selectedCategory ?: "all") }
    val categoryAccountsCache = remember(session.currentDid) {
        mutableStateMapOf<String, List<ProfileView>>().apply {
            cachedExplore?.categoryAccounts?.let(::putAll)
        }
    }
    var isExploreLoading by remember { mutableStateOf(false) }
    var isExploreRefreshing by remember { mutableStateOf(false) }
    var isCategoryLoading by remember { mutableStateOf(false) }
    var exploreError by remember { mutableStateOf<String?>(null) }

    fun persistExploreCache() {
        val prev = screenCache.searchExplore ?: ScreenStateCache.CachedSearchExploreScreen()
        screenCache.searchExplore = prev.copy(
            trends = trends,
            trendLanguages = trendLanguages,
            popularFeeds = popularFeeds,
            categoryAccounts = categoryAccountsCache.toMap(),
            selectedCategory = selectedCategory,
        )
    }

    // Optimistic follow overrides for Suggested Accounts & People tab
    val followOverrides = remember { mutableStateMapOf<String, String?>() }
    val followBusyMap = remember { mutableStateMapOf<String, Boolean>() }

    fun toggleFollow(profile: ProfileView) {
        val myDid = session.currentDid ?: return
        if (followBusyMap[profile.did] == true) return
        val hasOverride = followOverrides.containsKey(profile.did)
        val currentFollowUri = if (hasOverride) followOverrides[profile.did] else profile.viewer?.following
        val wasFollowing = !currentFollowUri.isNullOrBlank()

        followBusyMap[profile.did] = true
        followOverrides[profile.did] = if (wasFollowing) null else "pending"

        scope.launch {
            runCatching {
                if (wasFollowing) {
                    val rkey = currentFollowUri!!.substringAfterLast('/')
                    if (currentFollowUri != "pending" && rkey.isNotBlank()) {
                        container.api.deleteRecord(myDid, "app.bsky.graph.follow", rkey)
                    }
                    null
                } else {
                    container.api.followActor(myDid, profile.did).uri
                }
            }.onSuccess { newUri ->
                followOverrides[profile.did] = newUri
            }.onFailure {
                // Rollback optimistic state
                followOverrides[profile.did] = currentFollowUri
            }
            followBusyMap[profile.did] = false
        }
    }

    suspend fun loadSuggestedCategory(category: String, force: Boolean = false) {
        if (!force && categoryAccountsCache.containsKey(category)) return
        isCategoryLoading = true
        runCatching {
            if (category == "all") {
                container.api.getSuggestions(limit = 25).actors
            } else {
                container.api.searchActors(query = category, limit = 20).actors
            }
        }.onSuccess { actors ->
            categoryAccountsCache[category] = actors
            persistExploreCache()
        }
        isCategoryLoading = false
    }

    suspend fun loadExplore(refresh: Boolean = false) {
        if (isExploreLoading || isExploreRefreshing) return
        val shouldLoadTrends = !settings.hideTrendingTopics &&
            (refresh || trendLanguages != settings.contentLanguages || trendsFailed)
        if (!refresh && !shouldLoadTrends && popularFeeds.isNotEmpty() && categoryAccountsCache.containsKey(selectedCategory)) {
            return
        }
        if (refresh) {
            isExploreRefreshing = true
        } else if (trends.isEmpty() && popularFeeds.isEmpty()) {
            isExploreLoading = true
        }
        exploreError = null

        try {
            coroutineScope {
                if (shouldLoadTrends) {
                    launch {
                        try {
                            trends = container.api.getTrends(limit = 20, contentLanguages = settings.contentLanguages)
                            trendLanguages = settings.contentLanguages
                            trendsFailed = false
                            persistExploreCache()
                        } catch (ex: CancellationException) {
                            throw ex
                        } catch (_: Exception) {
                            trendsFailed = true
                        }
                    }
                }
                launch {
                    try {
                        popularFeeds = container.api.getPopularFeedGenerators()
                        persistExploreCache()
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (_: Exception) {
                        exploreError = "Unable to load explore content"
                    }
                }
                launch { loadSuggestedCategory(selectedCategory, force = refresh) }
            }
        } finally {
            isExploreLoading = false
            isExploreRefreshing = false
        }
    }

    LaunchedEffect(submittedQuery, session.currentDid, settings.hideTrendingTopics, settings.contentLanguages) {
        if (submittedQuery.isBlank()) {
            loadExplore(refresh = false)
        }
    }

    LaunchedEffect(selectedCategory) {
        persistExploreCache()
        if (submittedQuery.isBlank()) {
            loadSuggestedCategory(selectedCategory, force = false)
        }
    }

    val parsedQuery = remember(submittedQuery) { parseSearchOperators(submittedQuery) }
    val effectiveFromMe = filters.from == "me" || (filters.author.isBlank() && parsedQuery.inlineFromMe)
    val effectiveLang = filters.lang.ifBlank { parsedQuery.inlineLang }
    val effectiveSince = filters.since.ifBlank { parsedQuery.inlineSince }
    val searchTabCount = if (filters.postOnly || parsedQuery.inlineFromMe || parsedQuery.inlineSince.isNotBlank()) 2 else 4
    val effectiveSearchKey = "${filters.shareUrl(submittedQuery)}|${session.currentDid}"

    // Search results state (restored from ScreenStateCache when available)
    val initialQueryCache = remember(effectiveSearchKey) {
        screenCache.searchExplore?.queryResults?.get(effectiveSearchKey)
    }
    val searchPagerState = rememberPagerState(
        initialPage = initialQueryCache?.selectedPage?.coerceIn(0, searchTabCount - 1) ?: 0,
        pageCount = { searchTabCount },
    )
    var topPostsState by remember(effectiveSearchKey) {
        mutableStateOf(
            initialQueryCache?.topPosts?.let {
                PostSearchPageState(items = it.items, cursor = it.cursor, hasLoaded = it.hasLoaded)
            } ?: PostSearchPageState()
        )
    }
    var latestPostsState by remember(effectiveSearchKey) {
        mutableStateOf(
            initialQueryCache?.latestPosts?.let {
                PostSearchPageState(items = it.items, cursor = it.cursor, hasLoaded = it.hasLoaded)
            } ?: PostSearchPageState()
        )
    }
    var peopleState by remember(effectiveSearchKey) {
        mutableStateOf(
            initialQueryCache?.people?.let {
                PeopleSearchState(items = it.items, cursor = it.cursor, hasLoaded = it.hasLoaded)
            } ?: PeopleSearchState()
        )
    }
    var feedsState by remember(effectiveSearchKey) {
        mutableStateOf(
            initialQueryCache?.feeds?.let {
                FeedsSearchState(items = it.items, hasLoaded = it.hasLoaded)
            } ?: FeedsSearchState()
        )
    }

    fun persistQueryResultsCache() {
        if (submittedQuery.isBlank()) return
        val prev = screenCache.searchExplore ?: ScreenStateCache.CachedSearchExploreScreen()
        val entry = ScreenStateCache.CachedSearchQueryResults(
            searchKey = effectiveSearchKey,
            topPosts = ScreenStateCache.CachedSearchPostResults(topPostsState.items, topPostsState.cursor, topPostsState.hasLoaded),
            latestPosts = ScreenStateCache.CachedSearchPostResults(latestPostsState.items, latestPostsState.cursor, latestPostsState.hasLoaded),
            people = ScreenStateCache.CachedSearchPeopleResults(peopleState.items, peopleState.cursor, peopleState.hasLoaded),
            feeds = ScreenStateCache.CachedSearchFeedsResults(feedsState.items, feedsState.hasLoaded),
            selectedPage = searchPagerState.currentPage,
        )
        screenCache.searchExplore = prev.copy(
            queryResults = prev.queryResults + (effectiveSearchKey to entry),
        )
    }

    LaunchedEffect(effectiveSearchKey, searchPagerState.currentPage) {
        persistQueryResultsCache()
    }

    suspend fun loadSearchPosts(sort: String, refresh: Boolean = false, loadMore: Boolean = false) {
        if (submittedQuery.isBlank()) return
        val requestKey = effectiveSearchKey
        val current = if (sort == "top") topPostsState else latestPostsState
        if (current.isLoading || current.isRefreshing || current.isLoadingMore) return
        if (loadMore && current.cursor.isNullOrBlank()) return
        if (!refresh && !loadMore && current.hasLoaded) return

        val nextLoading = current.copy(
            isLoading = !refresh && !loadMore && current.items.isEmpty(),
            isRefreshing = refresh,
            isLoadingMore = loadMore,
            error = null,
        )
        if (sort == "top") topPostsState = nextLoading else latestPostsState = nextLoading

        val q = parsedQuery.cleanQuery.ifBlank { submittedQuery }
        val authorParam = if (effectiveFromMe) {
            session.currentDid
        } else {
            filters.author
        }

        runCatching {
            container.api.searchPosts(
                query = q,
                sort = sort,
                author = authorParam,
                since = formatSearchDate(effectiveSince),
                until = formatSearchDate(filters.until),
                lang = effectiveLang,
                cursor = if (loadMore) current.cursor else null,
                limit = 25,
                mentions = filters.mentions,
                domain = filters.domain,
                url = filters.url,
                tags = filters.tag.split(' ').filter { it.isNotBlank() },
            )
        }.onSuccess { resp ->
            if (effectiveSearchKey != requestKey) return
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
            if (sort == "top") topPostsState = done else latestPostsState = done
            persistQueryResultsCache()
        }.onFailure { ex ->
            if (ex is kotlinx.coroutines.CancellationException) throw ex
            if (effectiveSearchKey != requestKey) return
            val err = current.copy(
                isLoading = false,
                isRefreshing = false,
                isLoadingMore = false,
                error = ex.message ?: "Unable to search posts",
                hasLoaded = true,
            )
            if (sort == "top") topPostsState = err else latestPostsState = err
        }
    }

    suspend fun loadPeopleSearch(refresh: Boolean = false, loadMore: Boolean = false) {
        if (submittedQuery.isBlank()) return
        val requestKey = effectiveSearchKey
        if (loadMore && (peopleState.isLoadingMore || peopleState.cursor.isNullOrBlank())) return
        if (!refresh && !loadMore && peopleState.hasLoaded) return

        peopleState = peopleState.copy(
            isLoading = !refresh && !loadMore && peopleState.items.isEmpty(),
            isRefreshing = refresh,
            isLoadingMore = loadMore,
            error = null,
        )
        val q = parsedQuery.cleanQuery.ifBlank { submittedQuery }.removePrefix("@")
        runCatching {
            container.api.searchActors(
                query = q,
                cursor = if (loadMore) peopleState.cursor else null,
                limit = 25,
            )
        }.onSuccess { resp ->
            if (effectiveSearchKey != requestKey) return
            val merged = if (loadMore) {
                (peopleState.items + resp.actors).distinctBy { it.did }
            } else {
                resp.actors
            }
            peopleState = PeopleSearchState(
                items = merged,
                cursor = resp.cursor,
                hasLoaded = true,
            )
            persistQueryResultsCache()
        }.onFailure { ex ->
            if (ex is kotlinx.coroutines.CancellationException) throw ex
            if (effectiveSearchKey != requestKey) return
            peopleState = peopleState.copy(
                isLoading = false,
                isRefreshing = false,
                isLoadingMore = false,
                error = ex.message ?: "Unable to search people",
                hasLoaded = true,
            )
        }
    }

    suspend fun loadFeedsSearch(refresh: Boolean = false) {
        if (submittedQuery.isBlank()) return
        val requestKey = effectiveSearchKey
        if (!refresh && feedsState.hasLoaded) return

        feedsState = feedsState.copy(
            isLoading = !refresh && feedsState.items.isEmpty(),
            isRefreshing = refresh,
            error = null,
        )
        val q = parsedQuery.cleanQuery.ifBlank { submittedQuery }
        runCatching {
            container.api.getPopularFeedGenerators(query = q)
        }.onSuccess { list ->
            if (effectiveSearchKey != requestKey) return
            feedsState = FeedsSearchState(
                items = list,
                hasLoaded = true,
            )
            persistQueryResultsCache()
        }.onFailure { ex ->
            if (ex is kotlinx.coroutines.CancellationException) throw ex
            if (effectiveSearchKey != requestKey) return
            feedsState = feedsState.copy(
                isLoading = false,
                isRefreshing = false,
                error = ex.message ?: "Unable to search feeds",
                hasLoaded = true,
            )
        }
    }

    LaunchedEffect(effectiveSearchKey, searchPagerState.currentPage) {
        if (submittedQuery.isNotBlank()) {
            scope.launch {
                when (searchPagerState.currentPage) {
                    0 -> loadSearchPosts("top")
                    1 -> loadSearchPosts("latest")
                    2 -> if (searchTabCount > 2) loadPeopleSearch()
                    3 -> if (searchTabCount > 3) loadFeedsSearch()
                }
            }
        }
    }

    fun submitSearch(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return
        draftQuery = trimmed
        submittedQuery = trimmed
        isSearchFocused = false
        focusManager.clearFocus()
        keyboardController?.hide()
        scope.launch {
            searchHistoryDao.upsert(
                SearchHistoryEntity(
                    accountDid = accountKey,
                    kind = "query",
                    query = trimmed,
                    updatedAtMs = System.currentTimeMillis(),
                )
            )
        }
    }

    fun clearSearch() {
        draftQuery = ""
        submittedQuery = ""
        filters = SearchFilters()
        typeaheadActors = emptyList()
    }

    fun cancelSearch() {
        clearSearch()
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    BackHandler(enabled = isSearchFocused, onBack = ::cancelSearch)

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.contrast0.toComposeColor())
                    .statusBarsPadding(),
            ) {
                val hasResults = submittedQuery.isNotBlank() && !isSearchFocused
                val shareLabel = tr("Share this search")
                val copiedMessage = tr("Copied link to clipboard")
                if (!isSearchFocused) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = tr("Open drawer menu"),
                                tint = palette.contrast600.toComposeColor(),
                            )
                        }
                        Text(
                            text = tr("Explore"),
                            modifier = Modifier.weight(1f).padding(start = 4.dp).semantics { heading() },
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.contrast1000.toComposeColor(),
                        )
                        if (hasResults) {
                            val filterLabel = tr("Open advanced search options")
                            val activeFilters = if (filters.count > 0) {
                                tr(
                                    "{filterCount, plural, one {+# filter} other {+# filters}}",
                                    mapOf("filterCount" to filters.count),
                                )
                            } else null
                            IconButton(
                                onClick = { showFilters = true },
                                enabled = searchPagerState.currentPage < 2,
                                modifier = Modifier.testTag("search_filter_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Tune,
                                    contentDescription = activeFilters?.let { "$filterLabel, $it" } ?: filterLabel,
                                    tint = when {
                                        searchPagerState.currentPage >= 2 -> palette.contrast400.toComposeColor()
                                        filters.count > 0 -> palette.primary500.toComposeColor()
                                        else -> palette.contrast700.toComposeColor()
                                    },
                                )
                            }
                            IconButton(
                                modifier = Modifier.testTag("search_share_button"),
                                onClick = {
                                    LinkNavigationHelper.copyToClipboard(
                                        context,
                                        shareLabel,
                                        filters.shareUrl(submittedQuery),
                                        copiedMessage,
                                    )
                                },
                            ) {
                                Icon(
                                    imageVector = PostActionIcons.Share,
                                    contentDescription = shareLabel,
                                    modifier = Modifier.size(20.dp),
                                    tint = palette.contrast700.toComposeColor(),
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isSearchFocused) {
                        IconButton(onClick = ::cancelSearch) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = tr("Cancel search"),
                            )
                        }
                    }
                    ExploreSearchField(
                        query = draftQuery,
                        onChange = { draftQuery = it },
                        onClear = { clearSearch() },
                        onSubmit = { submitSearch(draftQuery) },
                        onFocusChange = { isSearchFocused = it },
                        modifier = Modifier.weight(1f),
                    )
                }

                if (hasResults) {
                    val tabs = listOf(tr("Top"), tr("Latest"), tr("People"), tr("Feeds")).take(searchTabCount)
                    SecondaryTabRow(
                        selectedTabIndex = searchPagerState.currentPage.coerceIn(tabs.indices),
                        containerColor = palette.contrast0.toComposeColor(),
                        contentColor = palette.contrast1000.toComposeColor(),
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = searchPagerState.currentPage == index,
                                onClick = { scope.launch { searchPagerState.animateScrollToPage(index) } },
                                text = { Text(title, fontWeight = FontWeight.Medium) },
                            )
                        }
                    }
                }
                HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)
            }
        },
        containerColor = palette.contrast0.toComposeColor(),
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                isSearchFocused || (draftQuery.isNotBlank() && submittedQuery != draftQuery.trim()) -> {
                    SearchTypeaheadAndHistoryPanel(
                        draftQuery = draftQuery,
                        historyItems = historyItems,
                        typeaheadActors = typeaheadActors,
                        isTypeaheadLoading = isTypeaheadLoading,
                        onSubmitQuery = { submitSearch(it) },
                        onSelectActor = { actor ->
                            isSearchFocused = false
                            focusManager.clearFocus()
                            scope.launch {
                                searchHistoryDao.upsert(
                                    SearchHistoryEntity(
                                        accountDid = accountKey,
                                        kind = "query",
                                        query = "@${actor.handle}",
                                        updatedAtMs = System.currentTimeMillis(),
                                    )
                                )
                            }
                            onNavigate(AppRoute.Profile(actor.handle.ifBlank { actor.did }))
                        },
                        onDeleteHistoryItem = { item ->
                            scope.launch { searchHistoryDao.delete(accountKey, item.query) }
                        },
                        onClearAllHistory = {
                            scope.launch { searchHistoryDao.clear(accountKey) }
                        },
                    )
                }

                submittedQuery.isNotBlank() -> {
                    HorizontalPager(
                        state = searchPagerState,
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        when (page) {
                            0 -> SearchPostsFeedTab(
                                pageState = topPostsState,
                                emptyMessage = tr("No results found for “<0>{query}</0>”.", mapOf("query" to submittedQuery)),
                                listState = screenCache.rememberCachedLazyListState("search_top_$effectiveSearchKey"),
                                onRefresh = { scope.launch { loadSearchPosts("top", refresh = true) } },
                                onLoadMore = { scope.launch { loadSearchPosts("top", loadMore = true) } },
                                onNavigate = onNavigate,
                                onOpenComposer = onOpenComposer,
                                onOpenEditPost = onOpenEditPost,
                                onOpenIn = onOpenIn,
                                onOpenReport = onOpenReport,
                                onOpenLightbox = onOpenLightbox,
                                onOpenVideoLightbox = onOpenVideoLightbox,
                            )

                            1 -> SearchPostsFeedTab(
                                pageState = latestPostsState,
                                emptyMessage = tr("No results found for “<0>{query}</0>”.", mapOf("query" to submittedQuery)),
                                listState = screenCache.rememberCachedLazyListState("search_latest_$effectiveSearchKey"),
                                onRefresh = { scope.launch { loadSearchPosts("latest", refresh = true) } },
                                onLoadMore = { scope.launch { loadSearchPosts("latest", loadMore = true) } },
                                onNavigate = onNavigate,
                                onOpenComposer = onOpenComposer,
                                onOpenEditPost = onOpenEditPost,
                                onOpenIn = onOpenIn,
                                onOpenReport = onOpenReport,
                                onOpenLightbox = onOpenLightbox,
                                onOpenVideoLightbox = onOpenVideoLightbox,
                            )

                            2 -> PeopleResultsTab(
                                state = peopleState,
                                query = submittedQuery,
                                listState = screenCache.rememberCachedLazyListState("search_people_$effectiveSearchKey"),
                                canFollow = session.hasSession,
                                followOverrides = followOverrides,
                                followBusyMap = followBusyMap,
                                onToggleFollow = { toggleFollow(it) },
                                onRefresh = { scope.launch { loadPeopleSearch(refresh = true) } },
                                onLoadMore = { scope.launch { loadPeopleSearch(loadMore = true) } },
                                onNavigate = onNavigate,
                            )

                            3 -> FeedsResultsTab(
                                state = feedsState,
                                query = submittedQuery,
                                listState = screenCache.rememberCachedLazyListState("search_feeds_$effectiveSearchKey"),
                                onRefresh = { scope.launch { loadFeedsSearch(refresh = true) } },
                                onNavigate = onNavigate,
                            )
                        }
                    }
                }

                else -> {
                    ExploreHomeContent(
                        trends = moderatedTrends,
                        hideTrendingTopics = settings.hideTrendingTopics,
                        isTrendingLoading = trendLanguages == null && !trendsFailed,
                        trendingError = trendsFailed,
                        onTrendingOptions = { showTrendingOptions = true },
                        popularFeeds = popularFeeds,
                        selectedCategory = selectedCategory,
                        suggestedActors = categoryAccountsCache[selectedCategory].orEmpty(),
                        isExploreLoading = isExploreLoading,
                        isRefreshing = isExploreRefreshing,
                        isCategoryLoading = isCategoryLoading,
                        errorMessage = exploreError,
                        canFollow = session.hasSession,
                        followOverrides = followOverrides,
                        followBusyMap = followBusyMap,
                        listState = screenCache.rememberCachedLazyListState("search_explore"),
                        onSelectCategory = { selectedCategory = it },
                        onToggleFollow = { toggleFollow(it) },
                        onRefresh = { scope.launch { loadExplore(refresh = true) } },
                        onNavigate = onNavigate,
                    )
                }
            }
        }
    }

    if (showFilters) {
        AdvancedSearchDialog(
            initialQuery = parsedQuery.cleanQuery,
            initialFilters = filters.copy(
                from = if (effectiveFromMe) "me" else "",
                lang = effectiveLang,
                since = effectiveSince,
            ),
            canSearchOwnPosts = session.hasSession,
            onDismiss = { showFilters = false },
            onApply = { query, newFilters ->
                filters = newFilters
                showFilters = false
                submitSearch(query)
            },
        )
    }
    if (showTrendingOptions) {
        AlertDialog(
            onDismissRequest = { showTrendingOptions = false },
            title = { Text(tr("Trending topics")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(tr("Trending topics are based on what people are talking about on the network. Topic titles and descriptions are generated with AI."))
                    Text(tr("You can update this later from your settings."))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTrendingOptions = false }) { Text(tr("Close")) }
            },
            confirmButton = {
                TextButton(onClick = {
                    showTrendingOptions = false
                    scope.launch { container.preferences.setHideTrendingTopics(true) }
                }) {
                    Text(tr("Hide trending topics"))
                }
            },
        )
    }
}

private val searchFiltersSaver = Saver<SearchFilters, String>(
    save = { AtProtoJson.encodeToString(it) },
    restore = { AtProtoJson.decodeFromString<SearchFilters>(it) },
)

private fun formatSearchDate(value: String): String = if (SINCE_DATE_REGEX.matches(value)) {
    "${value}T00:00:00Z"
} else {
    value
}

@Composable
private fun ExploreSearchField(
    query: String,
    onChange: (String) -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalAlfPalette.current
    val label = tr("Search")
    BasicTextField(
        value = query,
        onValueChange = onChange,
        modifier = modifier
            .testTag("search_input")
            .semantics { contentDescription = label }
            .onFocusChanged { onFocusChange(it.isFocused) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = palette.contrast1000.toComposeColor()),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        cursorBrush = SolidColor(palette.primary500.toComposeColor()),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier.fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.contrast50.toComposeColor())
                    .padding(start = 12.dp, end = if (query.isEmpty()) 12.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = ShellIcons.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = palette.contrast600.toComposeColor(),
                )
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = palette.contrast600.toComposeColor(),
                        )
                    }
                    innerTextField()
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = tr("Clear search query"),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun SearchTypeaheadAndHistoryPanel(
    draftQuery: String,
    historyItems: List<SearchHistoryEntity>,
    typeaheadActors: List<ProfileViewBasic>,
    isTypeaheadLoading: Boolean,
    onSubmitQuery: (String) -> Unit,
    onSelectActor: (ProfileViewBasic) -> Unit,
    onDeleteHistoryItem: (SearchHistoryEntity) -> Unit,
    onClearAllHistory: () -> Unit,
) {
    val palette = LocalAlfPalette.current
    val trimmed = draftQuery.trim()
    val filteredHistory = remember(historyItems, trimmed) {
        if (trimmed.isEmpty()) historyItems
        else historyItems.filter { it.query.contains(trimmed, ignoreCase = true) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.contrast0.toComposeColor()),
        contentPadding = PaddingValues(bottom = 84.dp),
    ) {
        if (trimmed.isNotEmpty()) {
            item(key = "submit_search_row") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSubmitQuery(trimmed) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = palette.primary500.toComposeColor(),
                    )
                    Text(
                        text = tr("Search for “{searchText}”", mapOf("searchText" to trimmed)),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = palette.primary500.toComposeColor(),
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = palette.primary500.toComposeColor(),
                        modifier = Modifier.size(18.dp),
                    )
                }
                HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)
            }
        }

        if (isTypeaheadLoading && typeaheadActors.isEmpty()) {
            item(key = "typeahead_loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }
        }

        if (typeaheadActors.isNotEmpty()) {
            item(key = "typeahead_header") {
                Text(
                    text = tr("People"),
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.contrast600.toComposeColor(),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            items(
                items = typeaheadActors,
                key = { "ta_${it.did}" },
            ) { actor ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectActor(actor) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CaelumAvatar(
                        url = actor.avatar,
                        contentDescription = actor.displayName ?: actor.handle,
                        size = 38.dp,
                        isLive = actor.status?.isActive == true,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        DisplayNameRow(
                            profile = actor,
                            showHandle = false,
                        )
                        Text(
                            text = "@${actor.handle}",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.contrast600.toComposeColor(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            item(key = "typeahead_divider") {
                HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)
            }
        }

        if (filteredHistory.isNotEmpty()) {
            item(key = "history_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = tr("Recent searches"),
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.contrast600.toComposeColor(),
                    )
                    TextButton(onClick = onClearAllHistory) {
                        Text(
                            text = tr("Clear all", context = "action"),
                            color = palette.primary500.toComposeColor(),
                        )
                    }
                }
            }
            items(
                items = filteredHistory,
                key = { "hist_${it.kind}_${it.query}" },
            ) { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSubmitQuery(entry.query) }
                        .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = null,
                        tint = palette.contrast500.toComposeColor(),
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = entry.query,
                        style = MaterialTheme.typography.bodyLarge,
                        color = palette.contrast1000.toComposeColor(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { onDeleteHistoryItem(entry) },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = tr("Remove"),
                            tint = palette.contrast500.toComposeColor(),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExploreHomeContent(
    trends: List<TrendTopicView>,
    hideTrendingTopics: Boolean,
    isTrendingLoading: Boolean,
    trendingError: Boolean,
    onTrendingOptions: () -> Unit,
    popularFeeds: List<GeneratorView>,
    selectedCategory: String,
    suggestedActors: List<ProfileView>,
    isExploreLoading: Boolean,
    isRefreshing: Boolean,
    isCategoryLoading: Boolean,
    errorMessage: String?,
    canFollow: Boolean,
    followOverrides: Map<String, String?>,
    followBusyMap: Map<String, Boolean>,
    listState: LazyListState,
    onSelectCategory: (String) -> Unit,
    onToggleFollow: (ProfileView) -> Unit,
    onRefresh: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
) {
    val palette = LocalAlfPalette.current

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        if (isExploreLoading && trends.isEmpty() && popularFeeds.isEmpty() && suggestedActors.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (errorMessage != null && trends.isEmpty() && popularFeeds.isEmpty() && suggestedActors.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(errorMessage, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRefresh) {
                    Text(tr("Try again"))
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 84.dp),
            ) {
                if (!hideTrendingTopics && (trends.isNotEmpty() || isTrendingLoading || trendingError)) {
                    item(key = "explore_trends_header") {
                        Column(Modifier.padding(top = 12.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.TrendingUp,
                                    contentDescription = null,
                                    tint = palette.contrast1000.toComposeColor(),
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = tr("Trending"),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = palette.contrast1000.toComposeColor(),
                                    modifier = Modifier.weight(1f).semantics { heading() },
                                )
                                IconButton(onClick = onTrendingOptions) {
                                    Icon(
                                        imageVector = ShellIcons.More,
                                        modifier = Modifier.size(18.dp),
                                        contentDescription = tr("Trending options"),
                                        tint = palette.contrast600.toComposeColor(),
                                    )
                                }
                            }
                            HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)
                        }
                    }

                    if (trends.isEmpty()) {
                        item(key = "explore_trends_status") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isTrendingLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                } else {
                                    TextButton(onClick = onRefresh) { Text(tr("Try again")) }
                                }
                            }
                        }
                    }

                    itemsIndexed(trends, key = { _, trend -> "trend_${trend.link}" }) { index, trend ->
                        TrendTopicRow(
                            rank = index + 1,
                            trend = trend,
                            onClick = {
                                val parsed = DeepLinkParser.parseUri(trend.link)
                                if (parsed is DeepLinkTarget.RouteTarget && parsed.route !is AppRoute.NotFound) {
                                    onNavigate(parsed.route)
                                } else {
                                    onNavigate(AppRoute.Search(trend.displayName))
                                }
                            },
                        )
                    }
                }

                // Video Reels shortcut banner
                item(key = "explore_video_feed_banner") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.primary50.toComposeColor())
                            .border(1.dp, palette.primary100.toComposeColor(), RoundedCornerShape(14.dp))
                            .clickable { onNavigate(AppRoute.VideoFeed()) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayCircleOutline,
                            contentDescription = null,
                            tint = palette.primary500.toComposeColor(),
                            modifier = Modifier.size(28.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tr("Trending videos"),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = palette.contrast1000.toComposeColor(),
                            )
                            Text(
                                text = tr("View more trending videos"),
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.contrast700.toComposeColor(),
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = palette.primary500.toComposeColor(),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                // Suggested Accounts section with category chips
                item(key = "explore_suggested_header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    ) {
                        Text(
                            text = tr("Suggested accounts"),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = palette.contrast1000.toComposeColor(),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SUGGESTED_CATEGORIES.forEach { cat ->
                                FilterChip(
                                    selected = selectedCategory == cat.key,
                                    onClick = { onSelectCategory(cat.key) },
                                    label = { Text(tr(cat.label)) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = palette.primary500.toComposeColor(),
                                        selectedLabelColor = androidx.compose.ui.graphics.Color.White,
                                    ),
                                )
                            }
                        }
                    }
                }

                if (isCategoryLoading && suggestedActors.isEmpty()) {
                    item(key = "suggested_loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                } else {
                    items(
                        items = suggestedActors.take(10),
                        key = { "sug_${selectedCategory}_${it.did}" },
                    ) { profile ->
                        ProfileResultRow(
                            profile = profile,
                            followUriOverride = followOverrides[profile.did],
                            hasFollowOverride = followOverrides.containsKey(profile.did),
                            isFollowBusy = followBusyMap[profile.did] == true,
                            canFollow = canFollow,
                            onToggleFollow = { onToggleFollow(profile) },
                            onClick = { onNavigate(AppRoute.Profile(profile.handle.ifBlank { profile.did })) },
                        )
                    }
                }

                // Popular Feeds section
                if (popularFeeds.isNotEmpty()) {
                    item(key = "explore_feeds_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.RssFeed,
                                    contentDescription = null,
                                    tint = palette.primary500.toComposeColor(),
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = tr("Discover feeds"),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = palette.contrast1000.toComposeColor(),
                                )
                            }
                            TextButton(onClick = { onNavigate(AppRoute.Feeds) }) {
                                Text(tr("View all"))
                            }
                        }
                    }

                    items(
                        items = popularFeeds.take(12),
                        key = { "pop_feed_${it.uri}" },
                    ) { feed ->
                        FeedGeneratorResultCard(
                            feed = feed,
                            onClick = { onNavigate(parseFeedRouteFromAtUri(feed.uri)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendTopicRow(
    rank: Int,
    trend: TrendTopicView,
    onClick: () -> Unit,
) {
    val palette = LocalAlfPalette.current

    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = tr("{rank}.", mapOf("rank" to rank)),
                style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                color = palette.contrast600.toComposeColor(),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = trend.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = palette.contrast1000.toComposeColor(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                trend.description?.takeIf { it.isNotBlank() }?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.contrast700.toComposeColor(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (trend.actors.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                            trend.actors.take(3).forEach { actor ->
                                CaelumAvatar(url = actor.avatar, contentDescription = null, size = 22.dp)
                            }
                        }
                    }
                    trend.postCount?.let { count ->
                        Text(
                            text = tr(
                                "{formattedPostCount} {postCount, plural, one {post} other {posts}}",
                                mapOf(
                                    "formattedPostCount" to formatCompactCount(count, LocalI18n.current.locale),
                                    "postCount" to count,
                                ),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.contrast700.toComposeColor(),
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = palette.contrast100.toComposeColor(), thickness = 0.5.dp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeopleResultsTab(
    state: PeopleSearchState,
    query: String,
    listState: LazyListState,
    canFollow: Boolean,
    followOverrides: Map<String, String?>,
    followBusyMap: Map<String, Boolean>,
    onToggleFollow: (ProfileView) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
) {
    val palette = LocalAlfPalette.current

    val shouldLoadMore by remember(listState, state.items.size, state.cursor) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            state.items.isNotEmpty() && !state.cursor.isNullOrBlank() && lastVisible >= state.items.size - 5
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            state.isLoading && state.items.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.error != null && state.items.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(state.error, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onRefresh) { Text(tr("Try again")) }
                }
            }
            state.items.isEmpty() && state.hasLoaded -> {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = tr("No results found for “<0>{query}</0>”.", mapOf("query" to query)),
                        style = MaterialTheme.typography.bodyLarge,
                        color = palette.contrast600.toComposeColor(),
                    )
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 84.dp),
                ) {
                    itemsIndexed(
                        items = state.items,
                        key = { idx, actor -> "ppl_${actor.did}_$idx" },
                    ) { _, actor ->
                        ProfileResultRow(
                            profile = actor,
                            followUriOverride = followOverrides[actor.did],
                            hasFollowOverride = followOverrides.containsKey(actor.did),
                            isFollowBusy = followBusyMap[actor.did] == true,
                            canFollow = canFollow,
                            onToggleFollow = { onToggleFollow(actor) },
                            onClick = { onNavigate(AppRoute.Profile(actor.handle.ifBlank { actor.did })) },
                        )
                    }
                    if (state.isLoadingMore) {
                        item(key = "people_footer_loader") {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedsResultsTab(
    state: FeedsSearchState,
    query: String,
    listState: LazyListState,
    onRefresh: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
) {
    val palette = LocalAlfPalette.current
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            state.isLoading && state.items.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.error != null && state.items.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(state.error, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onRefresh) { Text(tr("Try again")) }
                }
            }
            state.items.isEmpty() && state.hasLoaded -> {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = tr("No results found for “<0>{query}</0>”.", mapOf("query" to query)),
                        style = MaterialTheme.typography.bodyLarge,
                        color = palette.contrast600.toComposeColor(),
                    )
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 84.dp),
                ) {
                    items(
                        items = state.items,
                        key = { "feed_${it.uri}" },
                    ) { feed ->
                        FeedGeneratorResultCard(
                            feed = feed,
                            onClick = { onNavigate(parseFeedRouteFromAtUri(feed.uri)) },
                        )
                    }
                }
            }
        }
    }
}
