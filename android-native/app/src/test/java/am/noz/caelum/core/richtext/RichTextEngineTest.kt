package am.noz.caelum.core.richtext

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RichTextEngineTest {

    private lateinit var tlds: Set<String>
    private lateinit var fixtureCases: JsonArray

    @Before
    fun setup() {
        val tldsStream = requireNotNull(
            javaClass.classLoader?.getResourceAsStream("tlds.json")
                ?: javaClass.classLoader?.getResourceAsStream("assets/tlds.json")
        ) { "Missing tlds.json" }
        val tldArr = Json.parseToJsonElement(tldsStream.bufferedReader().readText()).jsonArray
        tlds = tldArr.map { it.jsonPrimitive.content.lowercase() }.toSet()

        val fixtureStream = requireNotNull(
            javaClass.classLoader?.getResourceAsStream("fixtures/richtext_facets.json")
        ) { "Missing fixtures/richtext_facets.json" }
        fixtureCases = Json.parseToJsonElement(fixtureStream.bufferedReader().readText())
            .jsonObject["cases"]!!.jsonArray
    }

    @Test
    fun matchesAllSharedTypeScriptFixtures() {
        for (elem in fixtureCases) {
            val c = elem.jsonObject
            val id = c["id"]!!.jsonPrimitive.content
            val input = c["input"]!!.jsonPrimitive.content
            val expectedGraphemes = c["graphemeLength"]!!.jsonPrimitive.int
            val expectedUtf8 = c["utf8Length"]!!.jsonPrimitive.int
            val expectedFacets = c["facets"]!!.jsonArray
            val expectedShortText = c["shortenedText"]!!.jsonPrimitive.content
            val expectedShortGraphemes = c["shortenedGraphemeLength"]!!.jsonPrimitive.int
            val expectedShortUtf8 = c["shortenedUtf8Length"]!!.jsonPrimitive.int
            val expectedShortFacets = c["shortenedFacets"]!!.jsonArray

            val mapper = RichTextEngine.Utf8Mapper(input)
            assertEquals("$id utf8Length", expectedUtf8, mapper.utf8Length)
            assertEquals("$id graphemeLength", expectedGraphemes, RichTextEngine.graphemeLength(input))

            val detected = RichTextEngine.detectFacetsWithoutResolution(input, tlds)
            assertFacetListEquals("$id detected facets", expectedFacets, detected)

            val (shortText, shortFacets) = RichTextEngine.shortenLinks(input, detected)
            assertEquals("$id shortenedText", expectedShortText, shortText)
            assertEquals(
                "$id shortenedGraphemeLength",
                expectedShortGraphemes,
                RichTextEngine.graphemeLength(shortText),
            )
            assertEquals(
                "$id shortenedUtf8Length",
                expectedShortUtf8,
                shortText.toByteArray(Charsets.UTF_8).size,
            )
            assertFacetListEquals("$id shortenedFacets", expectedShortFacets, shortFacets)
        }
    }

    @Test
    fun utf8MapperRoundTripsMultibyteEmojiAndCombiningChars() {
        val text = "Hi 👨‍👩‍👧‍👦 café e\u0301 日本語"
        val mapper = RichTextEngine.Utf8Mapper(text)
        val cafeUtf16 = text.indexOf("café")
        val cafeByteStart = mapper.utf16ToUtf8(cafeUtf16)
        val cafeByteEnd = mapper.utf16ToUtf8(cafeUtf16 + "café".length)
        assertEquals("café", mapper.sliceBytes(cafeByteStart, cafeByteEnd))
        assertEquals(cafeUtf16, mapper.utf8ToUtf16(cafeByteStart))
    }

    @Test
    fun segmentsSplitAtUtf8ByteBoundariesWithoutSplittingEmoji() {
        val text = "🏳️‍🌈 @alice.bsky.social cool"
        val facets = RichTextEngine.detectFacetsWithoutResolution(text, tlds)
        val segments = RichTextEngine.segments(text, facets)
        assertEquals(3, segments.size)
        assertEquals("🏳️‍🌈 ", segments[0].text)
        assertEquals("@alice.bsky.social", segments[1].text)
        assertEquals("alice.bsky.social", segments[1].mention?.did)
        assertEquals(" cool", segments[2].text)
    }

    private fun assertFacetListEquals(
        label: String,
        expected: JsonArray,
        actual: List<RichTextFacet>,
    ) {
        assertEquals("$label size", expected.size, actual.size)
        for (i in expected.indices) {
            val exp = expected[i].jsonObject
            val expIdx = exp["index"]!!.jsonObject
            val act = actual[i]
            assertEquals("$label[$i] byteStart", expIdx["byteStart"]!!.jsonPrimitive.int, act.index.byteStart)
            assertEquals("$label[$i] byteEnd", expIdx["byteEnd"]!!.jsonPrimitive.int, act.index.byteEnd)
            val expFeat = exp["features"]!!.jsonArray[0].jsonObject
            val expType = expFeat["\$type"]!!.jsonPrimitive.content
            when (val f = act.features.first()) {
                is FacetFeature.Mention -> {
                    assertEquals("$label[$i] type", "app.bsky.richtext.facet#mention", expType)
                    assertEquals("$label[$i] did", expFeat["did"]!!.jsonPrimitive.content, f.did)
                }
                is FacetFeature.Link -> {
                    assertEquals("$label[$i] type", "app.bsky.richtext.facet#link", expType)
                    assertEquals("$label[$i] uri", expFeat["uri"]!!.jsonPrimitive.content, f.uri)
                }
                is FacetFeature.Tag -> {
                    assertEquals("$label[$i] type", "app.bsky.richtext.facet#tag", expType)
                    assertEquals("$label[$i] tag", expFeat["tag"]!!.jsonPrimitive.content, f.tag)
                }
            }
        }
    }
}
