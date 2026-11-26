package am.noz.caelum.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InfrastructureConfigTest {

    @Test
    fun matchesSharedInfrastructureFixtures() {
        val stream = requireNotNull(
            javaClass.classLoader?.getResourceAsStream("fixtures/infrastructure_routing.json")
        )
        val root = Json.parseToJsonElement(stream.bufferedReader().readText()).jsonObject

        for (elem in root["hostTests"]!!.jsonArray) {
            val item = elem.jsonObject
            val input = item["input"]!!.jsonPrimitive.content
            verifyCase(input, item["appview"]!!.jsonObject) { InfrastructureConfig.normalizeAppview(it) }
            verifyCase(input, item["imageCdn"]!!.jsonObject) { InfrastructureConfig.normalizeImageCdn(it) }
            verifyCase(input, item["plcDirectory"]!!.jsonObject) { InfrastructureConfig.normalizePlcDirectory(it) }
        }

        for (elem in root["imageRewriteTests"]!!.jsonArray) {
            val item = elem.jsonObject
            val uri = item["uri"]!!.jsonPrimitive.content
            val host = item["host"]!!.jsonPrimitive.content
            val expected = item["rewritten"]!!.jsonPrimitive.content
            assertEquals(expected, InfrastructureConfig.rewriteImageUrl(uri, host))
        }
    }

    @Test
    fun appviewRequestScopeMatchesTsImplementation() {
        assertTrue(InfrastructureConfig.isAppviewRequest("/xrpc/app.bsky.feed.getTimeline?limit=30"))
        assertTrue(InfrastructureConfig.isAppviewRequest("/xrpc/app.bsky.actor.getProfile?actor=alice"))
        assertFalse(InfrastructureConfig.isAppviewRequest("/xrpc/app.bsky.actor.getPreferences"))
        assertFalse(InfrastructureConfig.isAppviewRequest("/xrpc/app.bsky.actor.putPreferences"))
        assertFalse(InfrastructureConfig.isAppviewRequest("/xrpc/app.bsky.video.getJobStatus?jobId=1"))
        assertFalse(InfrastructureConfig.isAppviewRequest("/xrpc/com.atproto.repo.createRecord"))
        assertFalse(InfrastructureConfig.isAppviewRequest("/xrpc/chat.bsky.convo.listConvos"))
    }

    private fun verifyCase(
        input: String,
        expectedObj: kotlinx.serialization.json.JsonObject,
        fn: (String) -> String,
    ) {
        val ok = expectedObj["ok"]!!.jsonPrimitive.boolean
        if (ok) {
            assertEquals(expectedObj["value"]!!.jsonPrimitive.content, fn(input))
        } else {
            var threw = false
            try {
                fn(input)
            } catch (_: IllegalArgumentException) {
                threw = true
            }
            assertTrue("Expected '$input' to throw", threw)
        }
    }
}
