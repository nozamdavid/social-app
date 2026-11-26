package am.noz.caelum.core.model

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.concurrent.ConcurrentHashMap

@Immutable
data class PostShadow(
    val likeUri: String? = null,
    val likeUriSet: Boolean = false,
    val likeCount: Int? = null,
    val repostUri: String? = null,
    val repostUriSet: Boolean = false,
    val repostCount: Int? = null,
    val bookmarked: Boolean? = null,
    val bookmarkCount: Int? = null,
    val replyCount: Int? = null,
    val pinned: Boolean? = null,
    val threadMuted: Boolean? = null,
    val replyDisabled: Boolean? = null,
    val embeddingDisabled: Boolean? = null,
    val isDeleted: Boolean = false,
    val editedRecord: JsonObject? = null,
)

/** The AppView mute applies to the whole conversation, including replies. */
internal fun PostView.threadRootUri(): String {
    val reply = record["reply"] as? JsonObject
    val root = reply?.get("root") as? JsonObject
    return (root?.get("uri") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: uri
}

class PostShadowStore {
    private val _shadows = MutableStateFlow<Map<String, PostShadow>>(emptyMap())
    val shadows: StateFlow<Map<String, PostShadow>> = _shadows.asStateFlow()

    fun get(uri: String): PostShadow? = _shadows.value[uri]

    fun update(uri: String, transform: (PostShadow) -> PostShadow) {
        _shadows.update { current ->
            val prev = current[uri] ?: PostShadow()
            val next = transform(prev)
            current + (uri to next)
        }
    }

    fun applyToPost(post: PostView): PostView? {
        val shadows = _shadows.value
        val shadow = shadows[post.uri]
        val threadMuted = shadows[post.threadRootUri()]?.threadMuted
        if (shadow == null && threadMuted == null) return post
        if (shadow?.isDeleted == true) return null
        val v = post.viewer ?: PostViewerState()
        val updatedViewer = v.copy(
            like = if (shadow?.likeUriSet == true) shadow.likeUri else v.like,
            repost = if (shadow?.repostUriSet == true) shadow.repostUri else v.repost,
            bookmarked = shadow?.bookmarked ?: v.bookmarked,
            pinned = shadow?.pinned ?: v.pinned,
            threadMuted = threadMuted ?: v.threadMuted,
            replyDisabled = shadow?.replyDisabled ?: v.replyDisabled,
            embeddingDisabled = shadow?.embeddingDisabled ?: v.embeddingDisabled,
        )
        return post.copy(
            record = shadow?.editedRecord ?: post.record,
            likeCount = shadow?.likeCount ?: post.likeCount,
            repostCount = shadow?.repostCount ?: post.repostCount,
            bookmarkCount = shadow?.bookmarkCount ?: post.bookmarkCount,
            replyCount = shadow?.replyCount ?: post.replyCount,
            viewer = updatedViewer,
        )
    }

    fun clear() {
        _shadows.value = emptyMap()
    }
}

/**
 * Per-key serialized mutation queue matching `useToggleMutationQueue.ts` & `postLikeQueue.ts`.
 * Ensures concurrent like/unlike/repost/auto-like operations serialize cleanly per post URI.
 */
class ToggleMutationQueue {
    private val mutexes = ConcurrentHashMap<String, Mutex>()

    suspend fun <T> enqueue(key: String, block: suspend () -> T): T {
        val mutex = mutexes.getOrPut(key) { Mutex() }
        return mutex.withLock {
            block()
        }
    }
}
