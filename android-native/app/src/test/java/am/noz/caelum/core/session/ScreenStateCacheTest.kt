package am.noz.caelum.core.session

import am.noz.caelum.core.model.ConvoView
import am.noz.caelum.core.model.FeedViewPost
import am.noz.caelum.core.model.PostThread
import am.noz.caelum.core.model.PostView
import am.noz.caelum.core.model.ProfileViewBasic
import am.noz.caelum.core.model.ProfileViewDetailed
import am.noz.caelum.core.model.ThreadNode
import am.noz.caelum.core.model.ThreadSort
import am.noz.caelum.core.model.ThreadViewOptions
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenStateCacheTest {

    private fun samplePost(uri: String = "at://did:plc:alice/app.bsky.feed.post/1"): PostView {
        return PostView(
            uri = uri,
            cid = "bafyreicid1",
            author = ProfileViewBasic(did = "did:plc:alice", handle = "alice.bsky.social"),
            record = JsonObject(emptyMap()),
            indexedAt = "2026-01-01T00:00:00Z",
        )
    }

    @Test
    fun `new activations isolate screen caches from previous reads and writes`() {
        val store = ScreenStateCacheStore()
        val cache = store.forActivation(1)
        val aliceDid = "did:plc:alice"

        val post = samplePost()
        cache.homeSelectedTabIndex = 2
        cache.homeFeeds["following"] = ScreenStateCache.CachedFeedPage(
            items = listOf(FeedViewPost(post = post)),
            cursor = "cursor-1",
        )
        cache.resolvedFeedTitles["at://did:plc:alice/app.bsky.graph.list/1"] = "My List"
        cache.profiles[aliceDid] = ScreenStateCache.CachedProfileScreen(
            profile = ProfileViewDetailed(did = aliceDid, handle = "alice.bsky.social"),
            selectedTabIndex = 1,
        )
        cache.threads["alice.bsky.social:1"] = ScreenStateCache.CachedPostThread(
            options = ThreadViewOptions(ThreadSort.Top, threaded = false, randomSeed = 0),
            thread = PostThread(emptyList(), ThreadNode(post.uri, post)),
        )
        cache.scrollPositions["home_feed_following"] = ScreenStateCache.ScrollPosition(index = 7, offset = 42)
        cache.messagesConvos = listOf(ConvoView(id = "convo-1"))
        cache.messagesRequestConvos = listOf(ConvoView(id = "req-1"))
        cache.messagesCursor = "msg-cursor-1"

        assertSame(cache, store.forActivation(1))
        assertEquals(2, cache.homeSelectedTabIndex)
        assertEquals(1, cache.homeFeeds["following"]?.items?.size)
        assertEquals("My List", cache.resolvedFeedTitles["at://did:plc:alice/app.bsky.graph.list/1"])
        assertNotNull(cache.profiles[aliceDid])
        assertNotNull(cache.threads["alice.bsky.social:1"])
        assertEquals(7, cache.scrollPositions["home_feed_following"]?.index)
        assertEquals(1, cache.messagesConvos?.size)
        assertEquals(1, cache.messagesRequestConvos?.size)
        assertEquals("msg-cursor-1", cache.messagesCursor)

        val next = store.forActivation(2)
        cache.messagesCursor = "delayed-write"
        store.forActivation(1).homeSelectedTabIndex = 7
        assertSame(next, store.forActivation(2))
        assertEquals(0, next.homeSelectedTabIndex)
        assertTrue(next.homeFeeds.isEmpty())
        assertTrue(next.resolvedFeedTitles.isEmpty())
        assertTrue(next.profiles.isEmpty())
        assertTrue(next.threads.isEmpty())
        assertTrue(next.scrollPositions.isEmpty())
        assertNull(next.messagesConvos)
        assertNull(next.messagesRequestConvos)
        assertNull(next.messagesCursor)
    }
}
