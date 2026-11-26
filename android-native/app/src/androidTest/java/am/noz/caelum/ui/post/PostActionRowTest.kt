package am.noz.caelum.ui.post

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import am.noz.caelum.core.i18n.I18nCatalog
import am.noz.caelum.core.i18n.LocalI18n
import am.noz.caelum.core.model.PostView
import am.noz.caelum.core.model.PostViewerState
import am.noz.caelum.core.model.ProfileViewBasic
import am.noz.caelum.ui.navigation.AppRoute
import am.noz.caelum.ui.theme.CaelumTheme
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PostActionRowTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun longPressRepostQuotesWhileTapOpensRepostMenu() {
        var quotes = 0
        var reposts = 0
        showPostActions(onQuote = { quotes++ }, onRepost = { reposts++ })

        val repostButton = compose.onNodeWithTag("repostBtn")
            .assertContentDescriptionEquals("Repost (8 reposts)")
        repostButton.performTouchInput { longClick() }
        compose.onNodeWithText("Quote post").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, quotes)
            assertEquals(0, reposts)
        }

        repostButton.performTouchInput { click() }
        compose.onNodeWithText("Quote post").assertIsDisplayed()
        compose.onNodeWithText("Repost").performTouchInput { click() }
        compose.onNodeWithText("Quote post").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, quotes)
            assertEquals(1, reposts)
        }
    }

    @Test
    fun longPressShareUsesSystemSharingWhileTapOffersChatAndCopyLink() {
        val post = samplePost()
        val sharedUrls = mutableListOf<String>()
        val routes = mutableListOf<AppRoute>()
        showPostActions(
            post = post,
            onSystemShare = { _, url -> sharedUrls.add(url) },
            onNavigate = { routes.add(it) },
        )

        val shareButton = compose.onNodeWithTag("postShareBtn")
        shareButton.performTouchInput { longClick() }
        compose.onNodeWithText("Share via...").assertDoesNotExist()
        val expectedUrl = "https://bsky.app/profile/author.example/post/example"
        compose.runOnIdle { assertEquals(listOf(expectedUrl), sharedUrls) }

        shareButton.performTouchInput { click() }
        compose.onNodeWithText("Share via...").assertIsDisplayed()
        compose.onNodeWithText("Copy link to post").assertIsDisplayed()
        compose.onNodeWithText("Send via chat").performTouchInput { click() }
        compose.onNodeWithText("Share via...").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf(expectedUrl), sharedUrls)
            assertEquals(listOf(AppRoute.SendPostViaChat(post.uri, post.cid)), routes)
        }
    }

    @Test
    fun restrictedReplyAndQuoteStayDisabledWhileRepostRemainsAvailable() {
        var replies = 0
        var quotes = 0
        var reposts = 0
        showPostActions(
            post = samplePost().copy(viewer = PostViewerState(replyDisabled = true, embeddingDisabled = true)),
            onReply = { replies++ },
            onQuote = { quotes++ },
            onRepost = { reposts++ },
        )

        compose.onNodeWithTag("replyBtn").assertIsNotEnabled().performTouchInput { click() }
        compose.onNodeWithTag("repostBtn").performTouchInput { longClick() }
        compose.onNodeWithText("Quote posts disabled").assertIsNotEnabled().performTouchInput { click() }
        compose.onNodeWithText("Repost").performTouchInput { click() }
        compose.onNodeWithText("Quote posts disabled").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(0, replies)
            assertEquals(0, quotes)
            assertEquals(1, reposts)
        }
    }

    private fun showPostActions(
        post: PostView = samplePost(),
        onReply: () -> Unit = {},
        onQuote: () -> Unit = {},
        onRepost: () -> Unit = {},
        onSystemShare: (Context, String) -> Unit = { _, _ -> error("Unexpected system sharing") },
        onNavigate: (AppRoute) -> Unit = {},
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val catalog = I18nCatalog.loadFromAssets(context, "en")
        compose.setContent {
            CompositionLocalProvider(LocalI18n provides catalog) {
                CaelumTheme {
                    PostActionRow(
                        post = post,
                        signedIn = true,
                        disableHaptics = true,
                        onReply = onReply,
                        onQuote = onQuote,
                        onRepost = onRepost,
                        onLike = {},
                        onSave = {},
                        onMore = {},
                        onOpenIn = {},
                        onSystemShare = onSystemShare,
                        onNavigate = onNavigate,
                    )
                }
            }
        }
    }

    private fun samplePost() = PostView(
        uri = "at://did:plc:author/app.bsky.feed.post/example",
        cid = "example-post-cid",
        author = ProfileViewBasic(did = "did:plc:author", handle = "author.example"),
        record = buildJsonObject {},
        replyCount = 2,
        repostCount = 3,
        likeCount = 4,
        quoteCount = 5,
        indexedAt = "2026-11-18T12:00:00Z",
    )
}
