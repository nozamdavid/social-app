package am.noz.caelum.ui.composer

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import am.noz.caelum.core.model.PostView
import am.noz.caelum.core.model.ProfileViewBasic
import am.noz.caelum.core.storage.DraftDao
import am.noz.caelum.core.storage.DraftEntity
import java.io.File
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ComposerDraftsTest {
    @Test
    fun `legacy drafts decode without images or interaction settings`() {
        val legacy = DraftEntity(
            id = "legacy",
            accountDid = ACCOUNT,
            payloadJson = """{"posts":["First","Second"],"language":"es","threadgateMode":"followers"}""",
            createdAtMs = 1L,
            updatedAtMs = 1L,
        )
        val payload = checkNotNull(legacy.localPayload())

        assertEquals(listOf("First", "Second"), payload.posts)
        assertEquals("es", payload.language)
        assertEquals("followers", payload.threadgateMode)
        assertTrue(payload.mediaByPost.isEmpty())
        assertTrue(payload.allowQuotes)
        assertNull(payload.replyTo)
        assertNull(payload.quotePost)
    }

    @Test
    fun `save copies media and preserves alt text and subjects when edited`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dao = MemoryDraftDao()
        val store = ComposerDrafts(context, dao)
        val source = File.createTempFile("composer_test_", ".jpg", context.cacheDir)
        source.writeText("original attachment")
        val reply = post("older")
        val quote = post("example")
        val payload = LocalDraftPayload(
            posts = listOf("Reply", "Follow-up"),
            language = "es",
            threadgateMode = "nobody",
            allowQuotes = false,
            replyTo = reply,
            quotePost = quote,
            mediaByPost = listOf(
                listOf(ComposerAttachment.Image(Uri.fromFile(source).toString(), "An image description")),
                listOf(ComposerAttachment.Gif("https://example.com/gif", "https://example.com/preview", "GIF", "GIF description")),
            ),
        )

        val first = store.save(ACCOUNT, previous = null, payload)
        source.delete()
        val restored = checkNotNull(first.localPayload())
        val firstImage = restored.mediaByPost.first().single() as ComposerAttachment.Image
        val firstFile = File(checkNotNull(Uri.parse(firstImage.uri).path))
        assertEquals("original attachment", firstFile.readText())
        assertEquals("An image description", firstImage.altText)
        assertEquals(reply, restored.replyTo)
        assertEquals(quote, restored.quotePost)
        assertEquals(payload.mediaByPost[1], restored.mediaByPost[1])
        assertFalse(restored.allowQuotes)

        val edited = store.save(ACCOUNT, first, restored.copy(posts = listOf("Edited reply", "Follow-up")))
        val editedPayload = checkNotNull(edited.localPayload())
        val currentImage = editedPayload.mediaByPost.first().single() as ComposerAttachment.Image
        val currentFile = File(checkNotNull(Uri.parse(currentImage.uri).path))
        assertEquals(first.id, edited.id)
        assertEquals(first.createdAtMs, edited.createdAtMs)
        assertEquals(listOf(edited), dao.getForAccount(ACCOUNT))
        assertNotEquals(firstFile, currentFile)
        assertFalse(firstFile.exists())
        assertEquals("original attachment", currentFile.readText())

        store.delete(edited)
        assertTrue(dao.getForAccount(ACCOUNT).isEmpty())
        assertFalse(currentFile.exists())
    }

    private fun post(key: String) = PostView(
        uri = "at://did:plc:subject/app.bsky.feed.post/$key",
        cid = "cid-$key",
        author = ProfileViewBasic(did = "did:plc:subject", handle = "subject.test"),
        record = buildJsonObject { put("text", "Subject post $key") },
        indexedAt = "2026-11-18T09:00:00.000Z",
    )

    private class MemoryDraftDao : DraftDao {
        private val items = mutableMapOf<String, DraftEntity>()

        override fun observeForAccount(did: String) = flowOf(items.values.filter { it.accountDid == did })

        override suspend fun getForAccount(did: String) = items.values.filter { it.accountDid == did }

        override suspend fun upsert(draft: DraftEntity) {
            items[draft.id] = draft
        }

        override suspend fun deleteById(id: String) {
            items.remove(id)
        }

        override suspend fun clearForAccount(did: String) {
            items.values.removeAll { it.accountDid == did }
        }
    }

    private companion object {
        const val ACCOUNT = "did:plc:composer"
    }
}
