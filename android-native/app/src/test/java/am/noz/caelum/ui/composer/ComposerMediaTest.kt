package am.noz.caelum.ui.composer

import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.model.BlobLink
import am.noz.caelum.core.model.BlobRef
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ComposerMediaTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `video uploads file ranges with scoped auth and polls for a previously processed blob`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(json("""{"canUpload":true}"""))
            server.enqueue(json("""{"jobId":"upload","partSizeBytes":3,"partCount":2}"""))
            server.enqueue(json("""{"partNumber":1,"sizeBytes":3}"""))
            server.enqueue(json("""{"partNumber":2,"sizeBytes":2}"""))
            server.enqueue(json("""{"jobStatus":{"jobId":"process","did":"$did","state":"JOB_STATE_ENCODING"}}"""))
            server.enqueue(json("""{"jobStatus":{
                "jobId":"process","did":"$did","state":"JOB_STATE_FAILED","error":"already_exists","blob":$blobJson
            }}"""))
            val auth = mutableListOf<Triple<String, String, Long?>>()
            val upload = uploader(server, auth)

            assertEquals(BlobRef(ref = BlobLink("blob-cid"), mimeType = "video/mp4", size = 8), upload.upload(video(), "video/quicktime", pds, did))
            assertEquals(
                listOf(
                    Triple("did:web:video.bsky.app", "app.bsky.video.getUploadLimits", null),
                    Triple("did:web:account.example", "com.atproto.repo.uploadBlob", 1800L),
                    Triple("did:web:account.example", "com.atproto.repo.uploadBlob", 1800L),
                ),
                auth,
            )
            val limits = server.takeRequest()
            assertEquals("/xrpc/app.bsky.video.getUploadLimits", limits.path)
            assertEquals("Bearer scoped-1", limits.getHeader("Authorization"))
            val start = server.takeRequest()
            assertEquals("/xrpc/app.bsky.video.startUpload", start.path)
            assertEquals("Bearer scoped-2", start.getHeader("Authorization"))
            val uploadMetadata = AtProtoJson.parseToJsonElement(start.body.readUtf8()).jsonObject
            assertEquals("video/quicktime", uploadMetadata["mimeType"]?.jsonPrimitive?.content)
            assertEquals("5", uploadMetadata["sizeBytes"]?.jsonPrimitive?.content)
            assertEquals("mov", uploadMetadata["name"]?.jsonPrimitive?.content?.substringAfterLast('.'))
            val first = server.takeRequest()
            assertEquals("/xrpc/app.bsky.video.uploadPart?jobId=upload&partNumber=1", first.path)
            assertEquals("application/octet-stream", first.getHeader("Content-Type"))
            assertEquals("012", first.body.readUtf8())
            assertEquals("34", server.takeRequest().body.readUtf8())
            val finish = server.takeRequest()
            assertEquals("/xrpc/app.bsky.video.finishUpload", finish.path)
            assertEquals("Bearer scoped-3", finish.getHeader("Authorization"))
            val job = server.takeRequest()
            assertEquals("/xrpc/app.bsky.video.getJobStatus?jobId=process", job.path)
            assertNull(job.getHeader("Authorization"))
        }
    }

    @Test
    fun `video surfaces upload limits before sending file data`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(json("""{"canUpload":false,"message":"Daily limit"}"""))
            val auth = mutableListOf<Triple<String, String, Long?>>()
            val result = runCatching { uploader(server, auth).upload(video(), "video/mp4", pds, did) }
            assertEquals("Daily limit", result.exceptionOrNull()?.message)
            assertEquals(1, auth.size)
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun `video aborts an incomplete upload after a permanent part failure`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(json("""{"canUpload":true}"""))
            server.enqueue(json("""{"jobId":"upload","partSizeBytes":5,"partCount":1}"""))
            server.enqueue(json("""{"error":"PartRejected","message":"Part rejected"}""").setResponseCode(400))
            server.enqueue(json("""{"state":"aborted"}"""))
            val result = runCatching { uploader(server).upload(video(), "video/mp4", pds, did) }
            assertEquals("Part rejected", result.exceptionOrNull()?.message)
            repeat(3) { server.takeRequest() }
            assertEquals("/xrpc/app.bsky.video.abortUpload", server.takeRequest().path)
        }
    }

    @Test
    fun `video recovers when finish returns an error after completing the multipart upload`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(json("""{"canUpload":true}"""))
            server.enqueue(json("""{"jobId":"upload","partSizeBytes":5,"partCount":1}"""))
            server.enqueue(json("""{"partNumber":1,"sizeBytes":5}"""))
            server.enqueue(json("""{"message":"Failed to return finish response"}""").setResponseCode(503))
            server.enqueue(json("""{"state":"completed","jobStatus":{
                "jobId":"process","did":"$did","state":"JOB_STATE_COMPLETED","blob":$blobJson
            }}"""))
            val blob = uploader(server).upload(video(), "video/mp4", pds, did)
            assertEquals("blob-cid", blob.ref.link)
            repeat(4) { server.takeRequest() }
            assertEquals("/xrpc/app.bsky.video.getUploadStatus?jobId=upload", server.takeRequest().path)
            assertEquals(5, server.requestCount)
        }
    }

    @Test
    fun `video rejects a processing failure without a blob`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(json("""{"canUpload":true}"""))
            server.enqueue(json("""{"jobId":"upload","partSizeBytes":5,"partCount":1}"""))
            server.enqueue(json("{}"))
            server.enqueue(json("""{"jobStatus":{
                "jobId":"process","did":"$did","state":"JOB_STATE_FAILED","message":"Encoding failed"
            }}"""))
            val result = runCatching { uploader(server).upload(video(), "video/mp4", pds, did) }
            assertEquals(IOException::class, result.exceptionOrNull()!!::class)
            assertEquals("Encoding failed", result.exceptionOrNull()?.message)
            assertEquals(4, server.requestCount)
        }
    }

    @Test
    fun `gif proxy uses the Android client key and encodes search and pagination`() {
        val featured = composerGifSearchUrl("", "ES", null)
        assertEquals("https", featured.scheme)
        assertEquals("gifs.bsky.app", featured.host)
        assertEquals("/klipy/v2/featured", featured.encodedPath)
        assertEquals("bluesky-android", featured.queryParameter("client_key"))
        assertEquals("low", featured.queryParameter("contentfilter"))
        assertEquals("es", featured.queryParameter("locale"))
        assertNull(featured.queryParameter("q"))
        val search = composerGifSearchUrl("cats & dogs", "", "Mg==")
        assertEquals("/klipy/v2/search", search.encodedPath)
        assertEquals("cats & dogs", search.queryParameter("q"))
        assertEquals("Mg==", search.queryParameter("pos"))
    }

    @Test
    fun `gif record persists its canonical media link and keeps its preview and alt distinct`() {
        val page = decodeComposerGifPage(
            """
            {
                "next": "Mg==",
                "results": [
                    {
                        "title": "cat",
                        "content_description": "Cat waves",
                        "media_formats": {
                            "gif": {"url": "https://static.klipy.com/ii/cat/ani.gif", "dims": [200, 120]},
                            "tinygif": {"url": "https://static.klipy.com/ii/cat/tiny.gif"},
                            "preview": {"url": "https://static.klipy.com/ii/cat/still.jpg"},
                            "mp4": {"url": "https://static.klipy.com/ii/cat/motion-mp4.mp4"},
                            "webm": {"url": "https://static.klipy.com/ii/cat/motion-webm.webm"}
                        }
                    },
                    {
                        "media_formats": {
                            "gif": {"url": "https://other.example/bad.gif", "dims": [200, 120]}
                        }
                    }
                ]
            }
            """.trimIndent()
        )
        assertEquals("Mg==", page.next)
        val gif = page.results.single()
        assertEquals("https://k.gifs.bsky.app/ii/cat/tiny.gif", gif.previewUri)
        assertEquals("https://k.gifs.bsky.app/ii/cat/still.jpg", gif.previewThumbnailUri)
        val thumb = BlobRef(ref = BlobLink("thumb-cid"), mimeType = "image/jpeg", size = 42)
        val embed = composerGifEmbed(gif.copy(altText = " A tabby waving. "), thumb)
        assertEquals("app.bsky.embed.external", embed["\$type"]?.jsonPrimitive?.content)
        val external = embed.getValue("external").jsonObject
        assertEquals(
            "https://static.klipy.com/ii/cat/ani.gif?hh=120&ww=200&mp4=motion-mp4&webm=motion-webm",
            external["uri"]?.jsonPrimitive?.content,
        )
        assertEquals("Cat waves", external["title"]?.jsonPrimitive?.content)
        assertEquals("Alt: A tabby waving.", external["description"]?.jsonPrimitive?.content)
        assertEquals("thumb-cid", external["thumb"]?.jsonObject?.get("ref")?.jsonObject?.get("\$link")?.jsonPrimitive?.content)
        assertEquals("ALT: Cat waves", composerGifEmbed(gif)["external"]?.jsonObject?.get("description")?.jsonPrimitive?.content)
    }

    private fun uploader(server: MockWebServer, auth: MutableList<Triple<String, String, Long?>> = mutableListOf()) =
        ComposerVideoUpload(
            client = OkHttpClient(),
            serviceAuth = { audience, method, ttl ->
                auth.add(Triple(audience, method, ttl))
                "scoped-${auth.size}"
            },
            failedMessage = "Could not process video",
            limitMessage = "Upload limit reached",
            timeoutMessage = "Video timed out",
            origin = server.url("/"),
        )

    private fun video(): File = temp.newFile().apply { writeText("01234") }

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    private val did = "did:plc:account"
    private val pds = "https://account.example:8443".toHttpUrl()
    private val blobJson = """{"${'$'}type":"blob","ref":{"${'$'}link":"blob-cid"},"mimeType":"video/mp4","size":8}"""
}
