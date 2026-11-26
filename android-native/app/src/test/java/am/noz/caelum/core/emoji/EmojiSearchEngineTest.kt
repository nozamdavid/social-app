package am.noz.caelum.core.emoji

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EmojiSearchEngineTest {
    private fun loadEngine(): EmojiSearchEngine {
        val candidates = listOf(
            File("src/main/assets/emoji/emoji_mart_native.json"),
            File("app/src/main/assets/emoji/emoji_mart_native.json"),
        )
        val file = candidates.first { it.exists() }
        return file.inputStream().use { EmojiSearchEngine.fromJsonStream(it) }
    }

    private fun loadFixtures() = Json.parseToJsonElement(
        EmojiSearchEngineTest::class.java.classLoader!!
            .getResource("fixtures/emoji_suggestions.json")!!
            .readText()
    ).jsonObject

    @Test
    fun `emoji token detection and replacement match TS reference fixture`() {
        val fixtures = loadFixtures()
        val cases = fixtures["tokenCases"]!!.jsonArray
        for (el in cases) {
            val obj = el.jsonObject
            val text = obj["text"]!!.jsonPrimitive.content
            val start = obj["start"]!!.jsonPrimitive.int
            val end = obj["end"]!!.jsonPrimitive.int
            val expectedTokenEl = obj["token"]!!
            val actualToken = EmojiSearchEngine.getEmojiToken(text, start, end)

            if (expectedTokenEl.toString() == "null") {
                assertNull("Expected null token for '$text'", actualToken)
            } else {
                val exp = expectedTokenEl.jsonObject
                assertNotNull("Expected non-null token for '$text'", actualToken)
                assertEquals(exp["start"]!!.jsonPrimitive.int, actualToken!!.start)
                assertEquals(exp["end"]!!.jsonPrimitive.int, actualToken.end)
                assertEquals(exp["query"]!!.jsonPrimitive.content, actualToken.query)
                assertEquals(exp["context"]!!.jsonPrimitive.content, actualToken.context)

                val replaced = EmojiSearchEngine.replaceEmojiToken(text, actualToken, "😄")
                val expRep = obj["replacedWithSmile"]!!.jsonObject
                assertEquals(expRep["text"]!!.jsonPrimitive.content, replaced.text)
                assertEquals(
                    expRep["selection"]!!.jsonObject["start"]!!.jsonPrimitive.int,
                    replaced.selectionStart,
                )
            }
        }
    }

    @Test
    fun `emoji search results and context tie-break match TS reference fixture`() {
        val engine = loadEngine()
        val fixtures = loadFixtures()
        val cases = fixtures["searchCases"]!!.jsonArray

        for (el in cases) {
            val obj = el.jsonObject
            val query = obj["query"]!!.jsonPrimitive.content
            val limit = obj["limit"]!!.jsonPrimitive.int
            val context = obj["context"]!!.jsonPrimitive.content
            val expectedIds = obj["results"]!!.jsonArray.map {
                it.jsonObject["id"]!!.jsonPrimitive.content
            }

            val actual = engine.search(query = query, limit = limit, context = context)
            val actualIds = actual.map { it.id }
            assertEquals("Mismatch for query='$query', context='$context'", expectedIds, actualIds)
        }
    }

    @Test
    fun `categories and notices assets load cleanly`() {
        val engine = loadEngine()
        assertTrue(engine.categories.isNotEmpty())
        val noticesCandidates = listOf(
            File("src/main/assets/emoji/notices.json"),
            File("app/src/main/assets/emoji/notices.json"),
        )
        val notices: List<EmojiThirdPartyNotice> = Json.decodeFromString(
            noticesCandidates.first { it.exists() }.readText()
        )
        assertTrue(notices.any { it.name == "Emoji Mart" })
        assertTrue(notices.any { it.name == "Fuse.js" })
    }
}
