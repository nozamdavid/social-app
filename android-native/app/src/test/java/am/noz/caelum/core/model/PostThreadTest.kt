package am.noz.caelum.core.model

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PostThreadTest {
    private val options = ThreadViewOptions(ThreadSort.Newest, threaded = true, randomSeed = 0)
    private val actor = ProfileViewBasic(did = "did:plc:example", handle = "example.test")

    private fun post(key: String, parent: PostView? = null) = PostView(
        uri = "at://${actor.did}/app.bsky.feed.post/$key",
        cid = "cid-$key",
        author = actor,
        record = buildJsonObject {
            parent?.let {
                put("reply", buildJsonObject {
                    put("parent", AtProtoJson.encodeToJsonElement(StrongRef(it.uri, it.cid)))
                })
            }
        },
        indexedAt = "2026-11-18T12:00:00Z",
    )

    private fun item(post: PostView, depth: Int, hidden: Boolean = false) =
        ThreadV2Item(post.uri, depth, buildJsonObject {
            put("\$type", "app.bsky.unspecced.defs#threadItemPost")
            put("post", AtProtoJson.encodeToJsonElement(post))
            put("hiddenByThreadgate", hidden)
        })

    @Test
    fun `converts V2 depth into ancestors and branches while redacting hidden content`() {
        val parent = post("ancestor")
        val anchor = post("anchor", parent)
        val unavailable = post("blocked", anchor)
        val hidden = post("hidden", anchor)
        val hiddenChild = post("private-branch", hidden)
        val sibling = post("sibling", anchor)
        val response = ThreadV2Response(
            listOf(
                item(parent, -1),
                item(anchor, 0),
                ThreadV2Item(unavailable.uri, 1, buildJsonObject {
                    put("\$type", "app.bsky.unspecced.defs#threadItemBlocked")
                }),
                item(hidden, 1, hidden = true),
                item(hiddenChild, 2),
                item(sibling, 1),
            ),
            hasOtherReplies = true,
        )

        val thread = response.toPostThread(anchor.uri, options)

        assertEquals(listOf(parent.uri), thread.parents.map { it.uri })
        assertEquals(listOf(unavailable.uri, hidden.uri, sibling.uri), thread.anchor.children.map { it.uri })
        assertEquals(UnavailableThreadPost.Blocked, thread.anchor.find(unavailable.uri)?.unavailable)
        assertEquals(UnavailableThreadPost.Hidden, thread.anchor.find(hidden.uri)?.unavailable)
        assertNull(thread.anchor.find(hidden.uri)?.post)
        assertNull(thread.anchor.find(hiddenChild.uri))
        assertEquals(hidden, response.replies(options, revealHidden = true)[1].post)
    }

    @Test
    fun `expansion keeps previously loaded descendants and publication inserts a chain once`() {
        val anchor = post("anchor")
        val older = post("older", anchor)
        val alreadyExpanded = post("existing", older)
        val fresh = post("published", anchor)
        val followUp = post("followup", fresh)
        val earlier = ThreadNode(anchor.uri, anchor, children = listOf(
            ThreadNode(older.uri, older, children = listOf(ThreadNode(alreadyExpanded.uri, alreadyExpanded))),
        ))
        val refreshed = ThreadNode(anchor.uri, anchor, children = listOf(
            ThreadNode(older.uri, older, moreReplies = 1),
        ))
        val expanded = earlier.mergeExpansion(refreshed)
        val inserted = expanded.withPublishedReplies(listOf(fresh, followUp), options)

        assertEquals(listOf(fresh.uri, older.uri), inserted.children.map { it.uri })
        assertEquals(2, inserted.post?.replyCount)
        assertEquals(followUp.uri, inserted.find(fresh.uri)?.children?.single()?.uri)
        assertEquals(alreadyExpanded, inserted.find(alreadyExpanded.uri)?.post)
        assertEquals(inserted, inserted.withPublishedReplies(listOf(fresh, followUp), options))
    }
}
