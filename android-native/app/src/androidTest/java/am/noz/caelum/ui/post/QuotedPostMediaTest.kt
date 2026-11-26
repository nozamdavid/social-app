package am.noz.caelum.ui.post

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import am.noz.caelum.core.moderation.ModerationOpts
import am.noz.caelum.core.moderation.ModerationUI
import am.noz.caelum.ui.theme.CaelumTheme
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuotedPostMediaTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun mediaOnlyQuoteDisplaysItsExternalCardAndOpensTheLink() {
        val articleUrl = "https://example.invalid/article"
        val articleTitle = "Article in the quoted post"
        val embed = buildJsonObject {
            put("\$type", "app.bsky.embed.record#view")
            put("record", buildJsonObject {
                put("\$type", "app.bsky.embed.record#viewRecord")
                put("uri", "at://did:plc:quoted/app.bsky.feed.post/article")
                put("cid", "quoted-post-cid")
                put("indexedAt", "2026-11-18T12:00:00Z")
                put("author", buildJsonObject {
                    put("did", "did:plc:quoted")
                    put("handle", "quoted.example")
                })
                put("value", buildJsonObject {
                    put("\$type", "app.bsky.feed.post")
                    put("text", "")
                    put("createdAt", "2026-11-18T12:00:00Z")
                })
                put("embeds", buildJsonArray {
                    add(buildJsonObject {
                        put("\$type", "app.bsky.embed.external#view")
                        put("external", buildJsonObject {
                            put("uri", articleUrl)
                            put("title", articleTitle)
                        })
                    })
                })
            })
        }

        val openedUrls = mutableListOf<String>()
        compose.setContent {
            CaelumTheme {
                PostEmbeds(
                    embed = embed,
                    mediaModeration = ModerationUI(),
                    moderationOpts = ModerationOpts(),
                    blurReason = null,
                    largeAltBadge = false,
                    disableAutoplay = true,
                    onOpenLightbox = { _, _ -> },
                    onOpenVideoLightbox = { _, _, _ -> },
                    onNavigate = { error("A tap on the article should open its own link") },
                    onOpenExternalLink = { openedUrls.add(it) },
                )
            }
        }

        compose.onNodeWithText(articleTitle).assertIsDisplayed().performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf(articleUrl), openedUrls) }
    }
}
