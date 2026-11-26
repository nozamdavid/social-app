package am.noz.caelum.ui.post

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import am.noz.caelum.core.moderation.ModerationCause
import am.noz.caelum.core.moderation.ModerationUI
import am.noz.caelum.ui.theme.CaelumTheme
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PostEmbedsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun lockedMediaShieldsEveryEmbedWithoutMountingTheMedia() {
        val embed = mutableStateOf(imageEmbed())
        var mediaOpens = 0
        compose.setContent {
            CaelumTheme {
                PostEmbeds(
                    embed = embed.value,
                    mediaModeration = blurredMedia(noOverride = true),
                    blurReason = "Sensitive media",
                    largeAltBadge = false,
                    disableAutoplay = false,
                    onOpenLightbox = { _, _ -> mediaOpens++ },
                    onOpenVideoLightbox = { _, _, _ -> mediaOpens++ },
                    onNavigate = {},
                    onOpenExternalLink = { mediaOpens++ },
                )
            }
        }

        val video = buildJsonObject {
            put("\$type", "app.bsky.embed.video#view")
            put("playlist", "https://example.invalid/play.m3u8")
            put("thumbnail", "https://example.invalid/frame.jpg")
            put("alt", "Hidden video")
        }
        val gif = externalEmbed("https://example.invalid/image.gif")
        val wrappedGif = buildJsonObject {
            put("\$type", "app.bsky.embed.recordWithMedia#view")
            put("media", gif)
            put("record", buildJsonObject {
                put("record", JsonObject(mapOf("\$type" to JsonPrimitive("app.bsky.embed.record#viewNotFound"))))
            })
        }

        for (hiddenEmbed in listOf(imageEmbed(), video, gif, externalEmbed("https://example.invalid"), wrappedGif)) {
            compose.runOnIdle { embed.value = hiddenEmbed }
            compose.onNodeWithText("Sensitive media").assertIsDisplayed()
            compose.onNodeWithText("Show").assertDoesNotExist()
            compose.onNodeWithText("Hide").assertDoesNotExist()
            compose.onNodeWithText("External card").assertDoesNotExist()
            compose.onNodeWithContentDescription("External card").assertDoesNotExist()
            compose.onNodeWithContentDescription("Hidden image").assertDoesNotExist()
            compose.onNodeWithContentDescription("Hidden video").assertDoesNotExist()
            compose.onNodeWithContentDescription("Enter fullscreen").assertDoesNotExist()
        }
        compose.onNodeWithText("Post has been deleted").assertIsDisplayed()
        assertEquals(0, mediaOpens)
    }

    @Test
    fun lockingPreviouslyRevealedMediaRemovesItAndResetsRevealState() {
        val moderation = mutableStateOf(blurredMedia(noOverride = false))
        val embed = externalEmbed("https://example.invalid")
        val openedUrls = mutableListOf<String>()
        compose.setContent {
            CaelumTheme {
                PostEmbeds(
                    embed = embed,
                    mediaModeration = moderation.value,
                    blurReason = "Sensitive media",
                    largeAltBadge = false,
                    disableAutoplay = true,
                    onOpenLightbox = { _, _ -> },
                    onOpenVideoLightbox = { _, _, _ -> },
                    onNavigate = {},
                    onOpenExternalLink = { openedUrls.add(it) },
                )
            }
        }

        compose.onNodeWithText("External card").assertDoesNotExist()
        compose.onNodeWithText("Show").performClick()
        compose.onNodeWithText("External card").performClick()
        compose.runOnIdle {
            assertEquals(listOf("https://example.invalid"), openedUrls)
            moderation.value = blurredMedia(noOverride = true)
        }
        compose.onNodeWithText("External card").assertDoesNotExist()
        compose.onNodeWithText("Show").assertDoesNotExist()

        compose.runOnIdle { moderation.value = blurredMedia(noOverride = false) }
        compose.onNodeWithText("External card").assertDoesNotExist()
        compose.onNodeWithText("Show").assertIsDisplayed()
    }

    private fun blurredMedia(noOverride: Boolean) = ModerationUI().apply {
        this.noOverride = noOverride
        blurs.add(ModerationCause.Hidden())
    }

    private fun imageEmbed() = buildJsonObject {
        put("\$type", "app.bsky.embed.images#view")
        put("images", buildJsonArray {
            add(buildJsonObject {
                put("thumb", "https://example.invalid/thumb.jpg")
                put("fullsize", "https://example.invalid/image.jpg")
                put("alt", "Hidden image")
            })
        })
    }

    private fun externalEmbed(uri: String) = buildJsonObject {
        put("\$type", "app.bsky.embed.external#view")
        put("external", buildJsonObject {
            put("uri", uri)
            put("title", "External card")
            put("thumb", "https://example.invalid/thumb.jpg")
        })
    }
}
