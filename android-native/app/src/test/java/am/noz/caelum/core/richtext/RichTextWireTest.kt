package am.noz.caelum.core.richtext

import am.noz.caelum.core.model.AtProtoJson
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RichTextWireTest {
    @Test
    fun `wire facets render as typed segments and encode with the AT Protocol discriminator`() {
        val facets = AtProtoJson.decodeFromString<List<RichTextFacet>>(
            """
            [
              {"index":{"byteStart":5,"byteEnd":10},"features":[{"${'$'}type":"app.bsky.richtext.facet#mention","did":"did:plc:example"}]},
              {"index":{"byteStart":11,"byteEnd":30},"features":[{"${'$'}type":"app.bsky.richtext.facet#link","uri":"https://example.org"}]},
              {"index":{"byteStart":31,"byteEnd":35},"features":[{"${'$'}type":"app.bsky.richtext.facet#tag","tag":"sky"}]}
            ]
            """.trimIndent(),
        )
        val segments = RichTextEngine.segments("🌱 @name https://example.org #sky", facets).filter { it.facet != null }
        assertEquals(listOf("@name", "https://example.org", "#sky"), segments.map { it.text })
        assertEquals("did:plc:example", segments[0].mention?.did)
        assertEquals("https://example.org", segments[1].link?.uri)
        assertEquals("sky", segments[2].tag?.tag)
        val encoded = AtProtoJson.parseToJsonElement(AtProtoJson.encodeToString(facets)).jsonArray
        val feature = encoded.first().jsonObject.getValue("features").jsonArray.first().jsonObject
        assertEquals("app.bsky.richtext.facet#mention", feature.getValue("\$type").jsonPrimitive.content)
        assertFalse("type" in feature)
    }

    @Test
    fun `decodes facets written by older Caelum builds`() {
        val facet = AtProtoJson.decodeFromString<RichTextFacet>(
            """{"index":{"byteStart":0,"byteEnd":4},"features":[{"type":"app.bsky.richtext.facet#tag","tag":"sky"}]}""",
        )
        assertEquals(listOf(FacetFeature.Tag("sky")), facet.features)
    }
}
