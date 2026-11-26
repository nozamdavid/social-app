package am.noz.caelum.core.moderation

import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.model.LabelValueDefinition
import am.noz.caelum.core.model.MutedWord
import am.noz.caelum.core.model.PostView
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class ModerationEngineTest {

    @Test
    fun matchesAllSharedTypeScriptModerationFixtures() {
        val stream = requireNotNull(
            javaClass.classLoader?.getResourceAsStream("fixtures/moderation_cases.json")
        )
        val root = Json.parseToJsonElement(stream.bufferedReader().readText()).jsonObject
        val rawDefs = root["labelDefs"]!!.jsonObject
        val labelDefs = rawDefs.mapValues { (did, arr) ->
            arr.jsonArray.map { elem ->
                val def = AtProtoJson.decodeFromJsonElement(LabelValueDefinition.serializer(), elem)
                ModerationEngine.interpretLabelValueDefinition(def, did)
            }
        }

        for (elem in root["cases"]!!.jsonArray) {
            val c = elem.jsonObject
            val id = c["id"]!!.jsonPrimitive.content
            val userDid = c["userDid"]?.jsonPrimitive?.contentOrNull
            val prefsObj = c["prefs"]!!.jsonObject

            val labelsPref = prefsObj["labels"]!!.jsonObject.mapValues { (_, v) ->
                LabelPreference.fromWire(v.jsonPrimitive.content)!!
            }
            val labelersPref = prefsObj["labelers"]!!.jsonArray.map { l ->
                val lo = l.jsonObject
                val lLabels = lo["labels"]!!.jsonObject.mapValues { (_, v) ->
                    LabelPreference.fromWire(v.jsonPrimitive.content)!!
                }
                LabelerPref(did = lo["did"]!!.jsonPrimitive.content, labels = lLabels)
            }
            val mutedWords = prefsObj["mutedWords"]!!.jsonArray.map {
                AtProtoJson.decodeFromJsonElement(MutedWord.serializer(), it)
            }
            val hiddenPosts = prefsObj["hiddenPosts"]!!.jsonArray.map { it.jsonPrimitive.content }
            val prefs = ModerationPrefs(
                adultContentEnabled = prefsObj["adultContentEnabled"]!!.jsonPrimitive.boolean,
                labels = labelsPref,
                labelers = labelersPref,
                mutedWords = mutedWords,
                hiddenPosts = hiddenPosts,
            )
            val post = AtProtoJson.decodeFromJsonElement(PostView.serializer(), c["post"]!!)
            val decision = ModerationEngine.moderatePost(
                post,
                ModerationOpts(userDid = userDid, prefs = prefs, labelDefs = labelDefs),
            )
            val exp = c["expected"]!!.jsonObject
            assertEquals("$id blocked", exp["blocked"]!!.jsonPrimitive.boolean, decision.blocked)
            assertEquals("$id muted", exp["muted"]!!.jsonPrimitive.boolean, decision.muted)

            assertUiEquals("$id contentList", exp["contentList"]!!.jsonObject, decision.ui(ModerationContext.contentList))
            assertUiEquals("$id contentView", exp["contentView"]!!.jsonObject, decision.ui(ModerationContext.contentView))
            assertUiEquals("$id contentMedia", exp["contentMedia"]!!.jsonObject, decision.ui(ModerationContext.contentMedia))
            assertUiEquals("$id avatar", exp["avatar"]!!.jsonObject, decision.ui(ModerationContext.avatar))
            assertUiEquals("$id profileView", exp["profileView"]!!.jsonObject, decision.ui(ModerationContext.profileView))
        }
    }

    private fun assertUiEquals(label: String, exp: JsonObject, act: ModerationUI) {
        assertEquals("$label noOverride", exp["noOverride"]!!.jsonPrimitive.boolean, act.noOverride)
        assertEquals("$label filter", exp["filter"]!!.jsonPrimitive.boolean, act.filter)
        assertEquals("$label blur", exp["blur"]!!.jsonPrimitive.boolean, act.blur)
        assertEquals("$label alert", exp["alert"]!!.jsonPrimitive.boolean, act.alert)
        assertEquals("$label inform", exp["inform"]!!.jsonPrimitive.boolean, act.inform)
        assertEquals("$label filters", exp["filters"]!!.jsonArray.map { it.jsonPrimitive.content }, act.filters.map { it.type })
        assertEquals("$label blurs", exp["blurs"]!!.jsonArray.map { it.jsonPrimitive.content }, act.blurs.map { it.type })
        assertEquals("$label alerts", exp["alerts"]!!.jsonArray.map { it.jsonPrimitive.content }, act.alerts.map { it.type })
        assertEquals("$label informs", exp["informs"]!!.jsonArray.map { it.jsonPrimitive.content }, act.informs.map { it.type })
    }

    @Test
    fun mutedWordStillMatchesAfterUrlOrSlashTokenInPost() {
        val post = AtProtoJson.decodeFromJsonElement(
            PostView.serializer(),
            Json.parseToJsonElement(
                """
                {
                  "uri": "at://did:plc:author/app.bsky.feed.post/3k1",
                  "cid": "bafyreicid1",
                  "author": {"did": "did:plc:author", "handle": "author.bsky.social"},
                  "record": {
                    "${'$'}type": "app.bsky.feed.post",
                    "text": "Read https://example.com/path/to/article about spoiler alert",
                    "createdAt": "2026-01-01T00:00:00Z"
                  },
                  "indexedAt": "2026-01-01T00:00:00Z"
                }
                """.trimIndent(),
            ),
        )
        val decision = ModerationEngine.moderatePost(
            post,
            ModerationOpts(
                userDid = "did:plc:viewer",
                prefs = ModerationPrefs(
                    mutedWords = listOf(MutedWord(value = "spoiler", targets = listOf("content"))),
                ),
            ),
        )
        val contentList = decision.ui(ModerationContext.contentList)
        assertEquals(true, contentList.filter)
        assertEquals(true, contentList.blur)
        assertEquals(listOf("mute-word"), contentList.filters.map { it.type })
    }
}
