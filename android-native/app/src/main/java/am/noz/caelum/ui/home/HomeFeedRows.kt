package am.noz.caelum.ui.home

import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.model.FeedViewPost
import am.noz.caelum.core.model.PostView
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal sealed interface HomeFeedRow {
    val key: String

    data class Post(
        override val key: String,
        val post: PostView,
        val reason: JsonObject?,
        val parentHandle: String?,
        val showTopRail: Boolean,
        val showBottomRail: Boolean,
    ) : HomeFeedRow

    data class FullThread(override val key: String, val root: PostView) : HomeFeedRow
}

internal fun buildHomeFeedRows(
    feed: List<FeedViewPost>,
    visiblePost: (PostView) -> PostView?,
): List<HomeFeedRow> = buildList {
    val seenEntries = mutableSetOf<String>()
    val seenPosts = mutableSetOf<String>()
    for (item in feed) {
        val post = visiblePost(item.post) ?: continue
        val sliceKey = "${post.uri}:${item.reason.orEmpty()}"
        if (!seenEntries.add(sliceKey) || (item.reason == null && post.uri in seenPosts)) continue

        val parent = item.reply?.parent?.asPostView()?.takeIf { it.uri != post.uri }?.let(visiblePost)
        val root = item.reply?.root?.asPostView()
            ?.takeIf { it.uri != parent?.uri && it.uri != post.uri }
            ?.let(visiblePost)

        // Reposted and pinned replies are elevated to their own cards.
        val context = if (item.reason == null && parent != null) {
            listOfNotNull(root, parent).dropWhile { it.uri in seenPosts }
        } else {
            emptyList()
        }
        val slice = context + post
        val hasGap = context.size == 2 && context[1].parentUri() != context[0].uri
        for ((index, current) in slice.withIndex()) {
            if (hasGap && index == 1) {
                add(HomeFeedRow.FullThread("$sliceKey:full-thread", context[0]))
            }
            val parentHandle = when {
                current.uri == post.uri && index == 0 -> parent?.author?.handle
                else -> null
            }
            add(
                HomeFeedRow.Post(
                    key = if (current.uri == post.uri) sliceKey else "$sliceKey:${current.uri}",
                    post = current,
                    reason = item.reason,
                    parentHandle = parentHandle,
                    showTopRail = index > 0 && !(hasGap && index == 1),
                    showBottomRail = index < slice.lastIndex,
                ),
            )
        }
        if (item.reason == null) seenPosts.addAll(slice.map { it.uri })
    }
}

private fun JsonObject.asPostView(): PostView? {
    val type = (get("\$type") as? JsonPrimitive)?.content
    if (type != null && type != "app.bsky.feed.defs#postView") return null
    return runCatching { AtProtoJson.decodeFromJsonElement(PostView.serializer(), this) }.getOrNull()
}

private fun PostView.parentUri(): String? =
    (((record["reply"] as? JsonObject)?.get("parent") as? JsonObject)?.get("uri") as? JsonPrimitive)?.content
