package am.noz.caelum.core.session

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import am.noz.caelum.core.model.BookmarkView
import am.noz.caelum.core.model.ConvoView
import am.noz.caelum.core.model.FeedViewPost
import am.noz.caelum.core.model.GeneratorView
import am.noz.caelum.core.model.LabelerViewDetailed
import am.noz.caelum.core.model.ListView
import am.noz.caelum.core.model.NotificationItem
import am.noz.caelum.core.model.PostThread
import am.noz.caelum.core.model.PostView
import am.noz.caelum.core.model.ProfileView
import am.noz.caelum.core.model.ProfileViewDetailed
import am.noz.caelum.core.model.StarterPackViewBasic
import am.noz.caelum.core.model.ThreadNode
import am.noz.caelum.core.model.ThreadViewOptions
import am.noz.caelum.core.model.TrendTopicView
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Application-scoped in-memory cache for loaded screen data and LazyColumn scroll positions.
 * Preserves feed/profile/thread/search/notifications/bookmarks state across tab switches and
 * back navigation, within one account activation.
 */
class ScreenStateCache {
    data class ScrollPosition(
        val index: Int = 0,
        val offset: Int = 0,
    )

    data class CachedFeedPage(
        val items: List<FeedViewPost> = emptyList(),
        val cursor: String? = null,
        val updatedAtMs: Long = System.currentTimeMillis(),
    )

    data class CachedProfileTab(
        val feedPosts: List<FeedViewPost> = emptyList(),
        val feeds: List<GeneratorView> = emptyList(),
        val lists: List<ListView> = emptyList(),
        val starterPacks: List<StarterPackViewBasic> = emptyList(),
        val cursor: String? = null,
        val loadedOnce: Boolean = false,
    )

    data class CachedProfileScreen(
        val profile: ProfileViewDetailed,
        val labelerView: LabelerViewDetailed? = null,
        val labelerLikeUri: String? = null,
        val labelerLikeCount: Int = 0,
        val selectedTabIndex: Int = 0,
        val tabs: Map<String, CachedProfileTab> = emptyMap(),
        val headerOffsetPx: Float = 0f,
    )

    data class CachedPostThread(
        val options: ThreadViewOptions,
        val thread: PostThread,
        val otherReplies: List<ThreadNode>? = null,
    )

    data class CachedNotificationsTab(
        val items: List<NotificationItem> = emptyList(),
        val postsByUri: Map<String, PostView> = emptyMap(),
        val cursor: String? = null,
        val loadedOnce: Boolean = false,
    )

    data class CachedNotificationsScreen(
        val tabs: Map<String, CachedNotificationsTab> = emptyMap(),
        val selectedTabKey: String = "all",
        val seenAt: String? = null,
    )

    data class CachedSearchPostResults(
        val items: List<PostView> = emptyList(),
        val cursor: String? = null,
        val hasLoaded: Boolean = false,
    )

    data class CachedSearchPeopleResults(
        val items: List<ProfileView> = emptyList(),
        val cursor: String? = null,
        val hasLoaded: Boolean = false,
    )

    data class CachedSearchFeedsResults(
        val items: List<GeneratorView> = emptyList(),
        val hasLoaded: Boolean = false,
    )

    data class CachedSearchQueryResults(
        val searchKey: String,
        val topPosts: CachedSearchPostResults = CachedSearchPostResults(),
        val latestPosts: CachedSearchPostResults = CachedSearchPostResults(),
        val people: CachedSearchPeopleResults = CachedSearchPeopleResults(),
        val feeds: CachedSearchFeedsResults = CachedSearchFeedsResults(),
        val selectedPage: Int = 0,
    )

    data class CachedSearchExploreScreen(
        val trends: List<TrendTopicView> = emptyList(),
        val trendLanguages: List<String>? = null,
        val popularFeeds: List<GeneratorView> = emptyList(),
        val categoryAccounts: Map<String, List<ProfileView>> = emptyMap(),
        val selectedCategory: String = "all",
        val queryResults: Map<String, CachedSearchQueryResults> = emptyMap(),
    )

    data class CachedCustomFeedScreen(
        val resolvedFeedUri: String? = null,
        val generator: GeneratorView? = null,
        val contentMode: String? = null,
        val isOnline: Boolean = true,
        val isValid: Boolean = true,
        val posts: List<FeedViewPost> = emptyList(),
        val cursor: String? = null,
    )

    data class CachedBookmarksScreen(
        val bookmarks: List<BookmarkView> = emptyList(),
        val cursor: String? = null,
        val loadedOnce: Boolean = false,
    )

    @Volatile
    var homeSelectedTabIndex: Int = 0

    val homeFeeds = ConcurrentHashMap<String, CachedFeedPage>()
    val resolvedFeedTitles = ConcurrentHashMap<String, String>()
    val customFeeds = ConcurrentHashMap<String, CachedCustomFeedScreen>()
    val profiles = ConcurrentHashMap<String, CachedProfileScreen>()
    val threads = ConcurrentHashMap<String, CachedPostThread>()
    private val _recentlyPublishedReplies = MutableStateFlow<List<PostView>>(emptyList())
    val recentlyPublishedReplies = _recentlyPublishedReplies.asStateFlow()

    fun recordPublishedReplies(posts: List<PostView>) {
        _recentlyPublishedReplies.update { current ->
            (current + posts).associateBy { it.uri }.values.toList().takeLast(100)
        }
    }

    val scrollPositions = ConcurrentHashMap<String, ScrollPosition>()

    @Volatile
    var notifications: CachedNotificationsScreen? = null

    @Volatile
    var searchExplore: CachedSearchExploreScreen? = null

    @Volatile
    var bookmarks: CachedBookmarksScreen? = null

    @Volatile
    var messagesConvos: List<ConvoView>? = null

    @Volatile
    var messagesRequestConvos: List<ConvoView>? = null

    @Volatile
    var messagesCursor: String? = null

    /**
     * Creates a [LazyListState] initialized with any previously saved scroll index/offset for
     * [cacheKey] and continuously persists scroll changes while the list is active and on dispose.
     */
    @Composable
    fun rememberCachedLazyListState(cacheKey: String): LazyListState {
        val listState = remember(cacheKey) {
            val saved = scrollPositions[cacheKey] ?: ScrollPosition()
            LazyListState(
                firstVisibleItemIndex = saved.index,
                firstVisibleItemScrollOffset = saved.offset,
            )
        }
        LaunchedEffect(cacheKey, listState) {
            snapshotFlow {
                listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
            }.collect { (index, offset) ->
                scrollPositions[cacheKey] = ScrollPosition(index = index, offset = offset)
            }
        }
        DisposableEffect(cacheKey, listState) {
            onDispose {
                scrollPositions[cacheKey] = ScrollPosition(
                    index = listState.firstVisibleItemIndex,
                    offset = listState.firstVisibleItemScrollOffset,
                )
            }
        }
        return listState
    }
}


/** Replaces the cache at activation boundaries so outgoing screens can only write their old cache. */
internal class ScreenStateCacheStore {
    private var activation = 0L
    private var cache = ScreenStateCache()

    @Synchronized
    fun forActivation(requested: Long): ScreenStateCache {
        if (requested < activation) return ScreenStateCache()
        if (requested > activation) {
            cache = ScreenStateCache()
            activation = requested
        }
        return cache
    }
}
