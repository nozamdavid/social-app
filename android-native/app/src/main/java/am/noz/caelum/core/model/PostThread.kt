package am.noz.caelum.core.model

import am.noz.caelum.core.network.AtprotoApi
import am.noz.caelum.core.network.XrpcException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlin.random.Random

enum class ThreadSort(val preference: String, val apiValue: String) {
    Top("hotness", "top"),
    Oldest("oldest", "oldest"),
    Newest("newest", "newest"),
    MostLiked("most-likes", "top"),
    RandomOrder("random", "newest");

    companion object {
        fun fromPreference(value: String) =
            entries.firstOrNull { it.preference == value }
                ?: Top
    }
}

data class ThreadViewOptions(
    val sort: ThreadSort,
    val threaded: Boolean,
    val randomSeed: Int,
)

enum class UnavailableThreadPost {
    NotFound, Blocked, SignInRequired, Hidden
}

data class ThreadNode(
    val uri: String,
    val post: PostView? = null,
    val unavailable: UnavailableThreadPost = UnavailableThreadPost.NotFound,
    val moreParents: Boolean = false,
    val moreReplies: Int = 0,
    val children: List<ThreadNode> = emptyList(),
) {
    fun find(uri: String): ThreadNode? =
        if (this.uri == uri) this else children.firstNotNullOfOrNull { it.find(uri) }
}

data class PostThread(
    val parents: List<ThreadNode>,
    val anchor: ThreadNode,
    val hasOtherReplies: Boolean = false,
    val threadgate: ThreadgateView? = null,
)

suspend fun AtprotoApi.loadPostThread(uri: String, options: ThreadViewOptions, above: Boolean = true): PostThread {
    try {
        return getPostThreadV2(
            anchorUri = uri,
            above = above,
            below = if (options.threaded) 4 else 10,
            branchingFactor = if (options.threaded) null else 1,
            sort = options.sort.apiValue,
        ).toPostThread(uri, options)
    } catch (error: XrpcException) {
        if (error.statusCode !in setOf(404, 501) && error.error !in setOf("MethodNotFound", "NotImplemented")) throw error
    }
    return try {
        getPostThread(uri, depth = if (options.threaded) 4 else 10, parentHeight = if (above) 80 else 0)
            .toLegacyPostThread(uri, options)
    } catch (error: XrpcException) {
        if (error.error != "NotFound") throw error
        PostThread(emptyList(), ThreadNode(uri))
    }
}

fun ThreadV2Response.toPostThread(uri: String, options: ThreadViewOptions): PostThread {
    val parents = thread.filter { it.depth < 0 }.distinctBy { it.uri }.sortedBy { it.depth }.map { it.toNode() }
    val anchor = thread.firstOrNull { it.uri == uri && it.depth == 0 }?.toNode() ?: ThreadNode(uri)
    return PostThread(
        parents = parents,
        anchor = anchor.copy(children = replies(options)),
        hasOtherReplies = hasOtherReplies,
        threadgate = threadgate,
    )
}

fun ThreadV2Response.replies(options: ThreadViewOptions, revealHidden: Boolean = false): List<ThreadNode> {
    val values = thread.filter { it.depth > 0 }.distinctBy { it.uri }
    var index = 0
    fun branch(parentDepth: Int): List<ThreadNode> = buildList {
        while (index < values.size && values[index].depth > parentDepth) {
            val item = values[index++]
            val node = item.toNode(revealHidden)
            val children = branch(item.depth)
            add(if (node.unavailable == UnavailableThreadPost.Hidden) node else node.copy(children = children))
        }
    }.sortedReplies(options, serverSorted = true)

    return branch(0)
}

private fun ThreadV2Item.toNode(revealHidden: Boolean = false): ThreadNode {
    if (value.boolean("hiddenByThreadgate") && !revealHidden) {
        return ThreadNode(uri, unavailable = UnavailableThreadPost.Hidden)
    }
    return ThreadNode(
        uri = uri,
        post = value.postView(),
        unavailable = when (value.string("\$type")?.substringAfterLast('#')) {
            "threadItemBlocked" -> UnavailableThreadPost.Blocked
            "threadItemNoUnauthenticated" -> UnavailableThreadPost.SignInRequired
            else -> UnavailableThreadPost.NotFound
        },
        moreParents = value.boolean("moreParents"),
        moreReplies = ((value["moreReplies"] as? JsonPrimitive)?.intOrNull ?: 0).coerceAtLeast(0),
    )
}

private fun JsonObject.toLegacyPostThread(uri: String, options: ThreadViewOptions): PostThread {
    val thread = this["thread"] as? JsonObject ?: return PostThread(emptyList(), ThreadNode(uri))
    val gate = this["threadgate"]?.let { runCatching { AtProtoJson.decodeFromJsonElement<ThreadgateView>(it) }.getOrNull() }
        ?: thread.postView()?.threadgate
    val hidden = (gate?.record?.get("hiddenReplies") as? JsonArray)
        .orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet()
    fun JsonObject.toNode(path: String, depth: Int): ThreadNode {
        val post = postView()
        val nodeUri = post?.uri ?: string("uri") ?: path
        if (depth > 0 && nodeUri in hidden) return ThreadNode(nodeUri, unavailable = UnavailableThreadPost.Hidden)
        val decoded = (this["replies"] as? JsonArray).orEmpty().mapIndexedNotNull { index, child ->
            (child as? JsonObject)?.toNode("$path/$index", depth + 1)
        }.distinctBy { it.uri }.sortedReplies(options, serverSorted = false)
        val children = if (!options.threaded && depth > 0) decoded.take(1) else decoded
        return ThreadNode(
            uri = nodeUri,
            post = post,
            unavailable = when (string("\$type")?.substringAfterLast('#')) {
                "blockedPost" -> UnavailableThreadPost.Blocked
                "noUnauthenticated" -> UnavailableThreadPost.SignInRequired
                else -> UnavailableThreadPost.NotFound
            },
            moreParents = depth < 0 && this["parent"] == null && post?.replyParentUri() != null,
            moreReplies = ((post?.replyCount ?: decoded.size) - children.size).coerceAtLeast(0),
            children = children,
        )
    }

    val parents = buildList {
        var parent = thread["parent"] as? JsonObject
        var depth = -1
        while (parent != null) {
            add(parent.toNode("$uri/parent/$depth", depth))
            parent = parent["parent"] as? JsonObject
            depth--
        }
    }.asReversed()
    val anchor = thread.toNode(uri, 0)
    return PostThread(parents.distinctBy { it.uri }, anchor, threadgate = gate)
}

fun PostView.replyParentUri(): String? =
    ((record["reply"] as? JsonObject)?.get("parent") as? JsonObject)?.string("uri")

/** Prefer newly fetched metadata while retaining descendants loaded by an earlier expansion. */
fun ThreadNode.mergeExpansion(fetched: ThreadNode): ThreadNode {
    if (fetched.post == null) return fetched
    val previous = children.associateBy { it.uri }
    val seen = fetched.children.mapTo(HashSet()) { it.uri }
    val older = if (fetched.moreReplies > 0) children.filterNot { it.uri in seen } else emptyList()
    return fetched.copy(
        children = fetched.children.map { child -> previous[child.uri]?.mergeExpansion(child) ?: child } + older,
        moreReplies = (fetched.moreReplies - older.size).coerceAtLeast(0),
    )
}

fun ThreadNode.replaceBranch(uri: String, replacement: ThreadNode): ThreadNode =
    if (this.uri == uri) mergeExpansion(replacement)
    else copy(children = children.map { it.replaceBranch(uri, replacement) })

fun ThreadNode.withPublishedReplies(replies: List<PostView>, options: ThreadViewOptions): ThreadNode {
    if (replies.isEmpty()) return this
    val existing = children.mapTo(HashSet()) { it.uri }
    val published = replies.filter { it.replyParentUri() == uri && it.uri !in existing }
    val newNodes = published.map { ThreadNode(uri = it.uri, post = it) }
    val next = (if (options.sort == ThreadSort.Newest) newNodes.asReversed() + children else children + newNodes)
        .sortedReplies(options, serverSorted = true)
    return copy(
        post = post?.let {
            if (newNodes.isEmpty()) it else it.copy(replyCount = maxOf(it.replyCount ?: 0, next.size))
        },
        children = next.map { it.withPublishedReplies(replies, options) },
    )
}

fun JsonArray.publishedReplyViews(account: StoredAccount): List<PostView> = mapNotNull { value ->
    val write = value as? JsonObject ?: return@mapNotNull null
    if (write.string("collection") != "app.bsky.feed.post") return@mapNotNull null
    val key = write.string("rkey") ?: return@mapNotNull null
    val record = write["value"] as? JsonObject ?: return@mapNotNull null
    if (record["reply"] !is JsonObject) return@mapNotNull null
    val at = record.string("createdAt") ?: return@mapNotNull null
    PostView(
        uri = "at://${account.did}/app.bsky.feed.post/$key",
        cid = runCatching { recordCid(record) }.getOrNull() ?: return@mapNotNull null,
        author = ProfileViewBasic(
            did = account.did,
            handle = account.handle,
            displayName = account.displayName,
            avatar = account.avatar,
        ),
        record = record,
        indexedAt = at,
    )
}

private fun List<ThreadNode>.sortedReplies(
    options: ThreadViewOptions,
    serverSorted: Boolean,
): List<ThreadNode> = when (options.sort) {
    ThreadSort.MostLiked -> sortedByDescending { it.post?.likeCount ?: 0 }
    ThreadSort.RandomOrder -> sortedBy { Random(it.uri.hashCode() xor options.randomSeed).nextInt() }
    ThreadSort.Oldest -> if (serverSorted) this else sortedBy { it.post?.indexedAt.orEmpty() }
    ThreadSort.Newest -> if (serverSorted) this else sortedByDescending { it.post?.indexedAt.orEmpty() }
    ThreadSort.Top -> if (serverSorted) this else sortedByDescending { (it.post?.likeCount ?: 0) + (it.post?.replyCount ?: 0) }
}

private fun JsonObject.postView(): PostView? =
    this["post"]?.let { runCatching { AtProtoJson.decodeFromJsonElement<PostView>(it) }.getOrNull() }

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.boolean(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull == true
