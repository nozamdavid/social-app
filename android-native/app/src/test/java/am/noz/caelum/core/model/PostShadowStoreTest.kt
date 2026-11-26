package am.noz.caelum.core.model

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class PostShadowStoreTest {
    private fun samplePost(uri: String = "at://did:plc:abc/app.bsky.feed.post/1"): PostView {
        return PostView(
            uri = uri,
            cid = "bafycid1",
            author = ProfileViewBasic(did = "did:plc:abc", handle = "alice.bsky.social"),
            record = buildJsonObject { put("text", JsonPrimitive("Original text")) },
            likeCount = 5,
            repostCount = 2,
            bookmarkCount = 1,
            replyCount = 0,
            indexedAt = "2026-04-01T10:00:00Z",
            viewer = PostViewerState(),
        )
    }

    @Test
    fun `applies like repost bookmark pin and edit shadows accurately`() {
        val store = PostShadowStore()
        val post = samplePost()

        store.update(post.uri) {
            it.copy(
                likeUri = "at://did:plc:me/app.bsky.feed.like/99",
                likeUriSet = true,
                likeCount = 6,
                bookmarked = true,
                bookmarkCount = 2,
                pinned = true,
                editedRecord = JsonObject(mapOf("text" to JsonPrimitive("Edited text"))),
            )
        }

        val updated = store.applyToPost(post)
        assertNotNull(updated)
        assertEquals("at://did:plc:me/app.bsky.feed.like/99", updated!!.viewer?.like)
        assertEquals(6, updated.likeCount)
        assertEquals(true, updated.viewer?.bookmarked)
        assertEquals(2, updated.bookmarkCount)
        assertEquals(true, updated.viewer?.pinned)
        assertEquals("Edited text", (updated.record["text"] as JsonPrimitive).content)

        store.update(post.uri) {
            it.copy(likeUri = null, likeUriSet = true, likeCount = 5)
        }
        val unliked = store.applyToPost(post.copy(viewer = PostViewerState(like = "old-like")))
        assertNull(unliked!!.viewer?.like)
        assertEquals(5, unliked.likeCount)
    }

    @Test
    fun `thread mute shadow applies to the root and replies without changing other threads`() {
        val store = PostShadowStore()
        val root = samplePost()
        val reply = samplePost("${root.uri}-reply").copy(
            record = buildJsonObject {
                put("reply", buildJsonObject {
                    put("root", buildJsonObject { put("uri", JsonPrimitive(root.uri)) })
                })
            },
        )
        val otherPost = samplePost("${root.uri}-other")

        store.update(root.uri) { it.copy(threadMuted = true) }
        assertEquals(true, store.applyToPost(root)?.viewer?.threadMuted)
        assertEquals(true, store.applyToPost(reply)?.viewer?.threadMuted)
        assertNull(store.applyToPost(otherPost)?.viewer?.threadMuted)

        val cachedMutedReply = reply.copy(viewer = PostViewerState(threadMuted = true))
        store.update(root.uri) { it.copy(threadMuted = false) }
        assertEquals(false, store.applyToPost(root)?.viewer?.threadMuted)
        assertEquals(false, store.applyToPost(cachedMutedReply)?.viewer?.threadMuted)

        store.clear()
        assertEquals(true, store.applyToPost(cachedMutedReply)?.viewer?.threadMuted)
    }

    @Test
    fun `deleted shadow hides post`() {
        val store = PostShadowStore()
        val post = samplePost()
        store.update(post.uri) { it.copy(isDeleted = true) }
        assertNull(store.applyToPost(post))
    }

    @Test
    fun `toggle mutation queue serializes operations per key`() = runTest {
        val queue = ToggleMutationQueue()
        val events = mutableListOf<Int>()

        val jobs = (1..5).map { idx ->
            async {
                queue.enqueue("post-1") {
                    val snapshot = events.size
                    delay(10)
                    events.add(snapshot + 1)
                    idx
                }
            }
        }
        jobs.awaitAll()
        assertEquals(listOf(1, 2, 3, 4, 5), events)
    }

    @Test
    fun `time formatter edit window enforces ten minutes`() {
        val base = Instant.parse("2026-04-01T10:00:00Z")
        assertEquals(true, TimeFormatter.isWithinEditWindow("2026-04-01T09:50:01Z", base))
        assertEquals(false, TimeFormatter.isWithinEditWindow("2026-04-01T09:50:00Z", base))
        assertEquals("9:59", TimeFormatter.formatEditCountdown("2026-04-01T09:59:59Z", base))
    }
}
