package am.noz.caelum.core.network

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthorFeedApiTest {
    @Test
    fun `author feed requests pins when the caller opts in`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"feed":[],"cursor":"next"}"""))
            val api = AtprotoApi(XrpcClient(publicOriginOverride = server.url("").toString()))

            val response = api.getAuthorFeed(
                actor = "did:plc:alice",
                filter = "posts_and_author_threads",
                includePins = true,
            )

            val url = checkNotNull(server.takeRequest().requestUrl)
            assertEquals("/xrpc/app.bsky.feed.getAuthorFeed", url.encodedPath)
            assertEquals("true", url.queryParameter("includePins"))
            assertEquals("posts_and_author_threads", url.queryParameter("filter"))
            assertEquals("next", response.cursor)
        }
    }

    @Test
    fun `author feed retries without pins when an AppView rejects the parameter`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setResponseCode(400)
                    .setBody("""{"error":"InvalidRequest","message":"Unknown parameter: includePins"}"""),
            )
            server.enqueue(MockResponse().setBody("""{"feed":[],"cursor":"next"}"""))
            val api = AtprotoApi(XrpcClient(publicOriginOverride = server.url("").toString()))

            val response = api.getAuthorFeed(
                actor = "did:plc:alice",
                filter = "posts_and_author_threads",
                includePins = true,
            )

            val first = checkNotNull(server.takeRequest().requestUrl)
            val retry = checkNotNull(server.takeRequest().requestUrl)
            assertEquals(first.newBuilder().removeAllQueryParameters("includePins").build(), retry)
            assertNull(retry.queryParameter("includePins"))
            assertEquals("next", response.cursor)
        }
    }
}
