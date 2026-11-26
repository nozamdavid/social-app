package am.noz.caelum.ui.home

import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.model.FeedViewPost
import am.noz.caelum.core.model.PostView
import am.noz.caelum.core.model.ProfileViewBasic
import am.noz.caelum.core.model.ReplyRef
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFeedRowsTest {
    @Test
    fun directlyRelatedPostsHaveContinuousRails() {
        val root = post("root")
        val parent = post("parent", parent = root)
        val reply = post("reply", parent = parent)

        val rows = buildHomeFeedRows(listOf(feedReply(reply, root, parent))) { it }
        val cards = rows.filterIsInstance<HomeFeedRow.Post>()

        assertEquals(3, rows.size)
        assertEquals(listOf(root, parent, reply), cards.map { it.post })
        assertEquals(listOf(false, true, true), cards.map { it.showTopRail })
        assertEquals(listOf(true, true, false), cards.map { it.showBottomRail })
        assertTrue(cards.all { it.parentHandle == null })
    }

    @Test
    fun missingContextAddsFullThreadLinkAfterTheRoot() {
        val root = post("root")
        val skipped = post("skipped", parent = root)
        val parent = post("parent", parent = skipped)
        val reply = post("reply", parent = parent)

        val rows = buildHomeFeedRows(listOf(feedReply(reply, root, parent))) { it }
        val link = rows[1] as HomeFeedRow.FullThread
        val parentCard = rows[2] as HomeFeedRow.Post

        assertEquals(root, link.root)
        assertFalse(parentCard.showTopRail)
        assertTrue(parentCard.showBottomRail)
    }

    @Test
    fun filteredAncestorsAreNeitherDisplayedNorUsedForThreadLinks() {
        val root = post("root")
        val parent = post("parent", parent = post("skipped", parent = root))
        val reply = post("reply", parent = parent)

        val rows = buildHomeFeedRows(listOf(feedReply(reply, root, parent))) {
            it.takeUnless { candidate -> candidate.uri == root.uri }
        }
        assertEquals(listOf(parent, reply), rows.filterIsInstance<HomeFeedRow.Post>().map { it.post })
        assertEquals(2, rows.size)
    }

    @Test
    fun previouslyShownContextIsSkippedButExplicitRepostsRemain() {
        val root = post("root")
        val reply = post("reply", parent = root)
        val hydratedReply = feedReply(reply, root, root)
        val repost = hydratedReply.copy(reason = buildJsonObject { put("\$type", "app.bsky.feed.defs#reasonRepost") })

        val rows = buildHomeFeedRows(listOf(FeedViewPost(root), hydratedReply, repost)) { it }
            .filterIsInstance<HomeFeedRow.Post>()

        assertEquals(listOf(root, reply, reply), rows.map { it.post })
        assertEquals(listOf(null, root.author.handle, root.author.handle), rows.map { it.parentHandle })
        assertEquals(repost.reason, rows[2].reason)
        assertEquals(3, rows.map { it.key }.distinct().size)
        assertTrue(rows.none { it.showTopRail || it.showBottomRail })
    }

    private fun post(rkey: String, parent: PostView? = null): PostView = PostView(
        uri = "at://did:plc:author/app.bsky.feed.post/$rkey",
        cid = "cid",
        author = ProfileViewBasic(did = "did:plc:author", handle = "author.test"),
        record = buildJsonObject {
            put("\$type", "app.bsky.feed.post")
            put("text", rkey)
            if (parent != null) {
                put("reply", buildJsonObject {
                    put("parent", buildJsonObject { put("uri", parent.uri) })
                })
            }
        },
        indexedAt = "2026-11-18T12:00:00Z",
    )

    private fun feedReply(post: PostView, root: PostView, parent: PostView) =
        FeedViewPost(post = post, reply = ReplyRef(root = root.toJson(), parent = parent.toJson()))

    private fun PostView.toJson(): JsonObject =
        AtProtoJson.encodeToJsonElement(PostView.serializer(), this) as JsonObject
}
