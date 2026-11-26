package am.noz.caelum.ui.composer

import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.model.StrongRef
import am.noz.caelum.core.model.recordCid
import java.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComposerPostWritesTest {
    private val did = "did:plc:abc123"
    private val now = Instant.parse("2024-01-01T00:00:00Z")

    @Test
    fun `thread batches the posts and gates with matching keys and chained CIDs`() {
        val keys = listOf("rkey0", "rkey1", "rkey2").iterator()
        val writes = composerPostWrites(
            did = did,
            posts = List(3) { index -> buildJsonObject { put("text", "post $index") } },
            parent = null,
            root = null,
            threadgateMode = "nobody",
            allowQuotes = false,
            now = now,
            nextRkey = keys::next,
        ).map { it.jsonObject }
        val collections = writes.map { it.getValue("collection").jsonPrimitive.content }
        assertEquals(
            listOf("post", "threadgate", "postgate", "post", "postgate", "post", "postgate")
                .map { "app.bsky.feed.$it" },
            collections,
        )
        val posts = writes.filter { it.getValue("collection").jsonPrimitive.content == "app.bsky.feed.post" }
            .map { it.getValue("value").jsonObject }
        assertNull(posts[0]["reply"])
        val root = StrongRef(
            "at://$did/app.bsky.feed.post/rkey0",
            "bafyreig62rxs34h5rvznfrracwkjlfgad5b25qxglp2hcziqdfas2nw2ee",
        )
        assertEquals(root.cid, recordCid(posts[0]))
        assertEquals(root, replyRef(posts[1], "root"))
        assertEquals(root, replyRef(posts[1], "parent"))
        assertEquals(root, replyRef(posts[2], "root"))
        assertEquals(
            StrongRef(
                "at://$did/app.bsky.feed.post/rkey1",
                "bafyreicxcj2tq5jrh5jcaczg3eli5cvxitgzu7kpu3fm5v3njq2byjxirq",
            ),
            replyRef(posts[2], "parent"),
        )
        for (write in writes.filter { it.getValue("collection").jsonPrimitive.content != "app.bsky.feed.post" }) {
            val key = write.getValue("rkey").jsonPrimitive.content
            assertEquals("at://$did/app.bsky.feed.post/$key", write.getValue("value").jsonObject["post"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun `thread extending a reply keeps the original root`() {
        val root = StrongRef("at://did:plc:root/app.bsky.feed.post/root", "root-cid")
        val parent = StrongRef("at://did:plc:parent/app.bsky.feed.post/parent", "parent-cid")
        val keys = listOf("first", "second").iterator()
        val posts = composerPostWrites(
            did = did,
            posts = listOf(
                buildJsonObject { put("text", "first") },
                buildJsonObject { put("text", "second") },
            ),
            parent = parent,
            root = root,
            threadgateMode = "anyone",
            allowQuotes = true,
            now = now,
            nextRkey = keys::next,
        ).map { it.jsonObject.getValue("value").jsonObject }
        assertEquals(root, replyRef(posts[0], "root"))
        assertEquals(parent, replyRef(posts[0], "parent"))
        assertEquals(root, replyRef(posts[1], "root"))
        assertEquals(StrongRef("at://$did/app.bsky.feed.post/first", recordCid(posts[0])), replyRef(posts[1], "parent"))
    }

    @Test
    fun `replies never emit threadgate writes even if threadgateMode is restricted`() {
        val root = StrongRef("at://did:plc:root/app.bsky.feed.post/root", "root-cid")
        val parent = StrongRef("at://did:plc:parent/app.bsky.feed.post/parent", "parent-cid")
        val writes = composerPostWrites(
            did = did,
            posts = listOf(buildJsonObject { put("text", "reply text") }),
            parent = parent,
            root = root,
            threadgateMode = "nobody",
            allowQuotes = false,
            now = now,
            nextRkey = { "reply1" },
        ).map { it.jsonObject }
        val collections = writes.map { it.getValue("collection").jsonPrimitive.content }
        assertEquals(listOf("app.bsky.feed.post", "app.bsky.feed.postgate"), collections)
    }

    @Test
    fun `blob ref hashes as a CID link`() {
        val record = AtProtoJson.parseToJsonElement(
            """
            {
                "${'$'}type": "app.bsky.feed.post",
                "createdAt": "2024-01-01T00:00:00.001Z",
                "text": "post with image",
                "embed": {
                    "${'$'}type": "app.bsky.embed.images",
                    "images": [{
                        "image": {
                            "${'$'}type": "blob",
                            "ref": {"${'$'}link": "bafkreieq5jui4j25lacwomsqgjeswwl3y5zcdrresptwgmfylxo2depppq"},
                            "mimeType": "image/jpeg",
                            "size": 12345
                        },
                        "alt": "alt text",
                        "aspectRatio": {"width": 100, "height": 200}
                    }]
                }
            }
            """.trimIndent(),
        ).jsonObject
        assertEquals("bafyreiem7g6vja66nebr7he4fshfnlyndyldbvle2n265oixscmepjcbii", recordCid(record))
    }

    private fun replyRef(post: JsonObject, key: String): StrongRef =
        AtProtoJson.decodeFromJsonElement(StrongRef.serializer(), post.getValue("reply").jsonObject.getValue(key))
}
