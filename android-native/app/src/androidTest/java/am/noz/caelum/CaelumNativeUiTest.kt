package am.noz.caelum

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import am.noz.caelum.di.AppContainer
import am.noz.caelum.ui.navigation.AppRoute
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaelumNativeUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val verticalScrollableMatcher = SemanticsMatcher("LazyVerticalList") { node ->
        SemanticsActions.ScrollToIndex in node.config
    }

    private fun enterGuestBrowsing() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("welcome_browse_guest", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("welcome_browse_guest", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("bottom_nav_home", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun welcomeSignInAndGuestShell() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("welcome_browse_guest", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("welcome_create_account").assertIsDisplayed()
        composeRule.onNodeWithTag("welcome_sign_in").assertIsDisplayed()
        composeRule.onNodeWithTag("welcome_browse_guest").assertIsDisplayed()

        composeRule.onNodeWithTag("welcome_sign_in").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("signin_identifier")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("signin_identifier").performTextInput("bsky.app")

        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("welcome_browse_guest")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("welcome_browse_guest").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("bottom_nav_home")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("bottom_nav_home").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom_nav_search").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom_nav_messages").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom_nav_notifications").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom_nav_profile").assertIsDisplayed()
    }

    @Test
    fun tabSwitchingAndActivityRecreationPreservesSearchState() {
        enterGuestBrowsing()

        composeRule.onNodeWithTag("bottom_nav_search").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("search_input")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("search_input").assertIsDisplayed()

        composeRule.activityRule.scenario.recreate()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("search_input")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("search_input").assertIsDisplayed()
    }

    @Test
    fun settingsAndCaelumCustomizationsViaDeepLinks() {
        enterGuestBrowsing()

        var containerRef: AppContainer? = null
        composeRule.runOnUiThread {
            containerRef = (composeRule.activity.application as CaelumApplication).container
        }
        val container = requireNotNull(containerRef)

        // 1. Settings
        composeRule.runOnUiThread {
            container.emitDeepLinkRoute(AppRoute.Settings)
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("settings_list")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("settings_list").assertIsDisplayed()

        // 2. Appearance Settings
        composeRule.runOnUiThread {
            container.emitDeepLinkRoute(AppRoute.AppearanceSettings)
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("appearance_settings_screen")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("appearance_settings_screen").assertIsDisplayed()
        composeRule.onNodeWithText("Color Mode").assertIsDisplayed()

        composeRule.onNode(verticalScrollableMatcher)
            .performScrollToNode(hasTestTag("add_themes_button"))
        composeRule.onNodeWithText("Add themes").assertIsDisplayed()

        composeRule.onNode(verticalScrollableMatcher)
            .performScrollToNode(hasText("Caelum Light"))
        composeRule.onNodeWithText("Caelum Light").assertIsDisplayed()

        // 3. Infrastructure Settings
        composeRule.runOnUiThread {
            container.emitDeepLinkRoute(AppRoute.InfrastructureSettings)
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("infrastructure_settings_screen")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("infrastructure_settings_screen").assertIsDisplayed()
        composeRule.onNodeWithText("Use Bluesky link redirects (go.bsky.app)").assertIsDisplayed()

        composeRule.onNode(verticalScrollableMatcher)
            .performScrollToNode(hasTestTag("appview_hostname_input"))
        composeRule.onNodeWithTag("appview_hostname_input").assertIsDisplayed()

        // 4. Customization Settings
        composeRule.runOnUiThread {
            container.emitDeepLinkRoute(AppRoute.CustomizationSettings)
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("customization_settings_screen")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("customization_settings_screen").assertIsDisplayed()
        composeRule.onNode(verticalScrollableMatcher)
            .performScrollToNode(hasText("Open Bluesky links in Caelum"))
        composeRule.onNodeWithText("Open Bluesky links in Caelum").assertIsDisplayed()

        composeRule.onNode(verticalScrollableMatcher)
            .performScrollToNode(hasText("Automatically like reposts"))
        composeRule.onNodeWithText("Automatically like reposts").assertIsDisplayed()

        composeRule.onNode(verticalScrollableMatcher)
            .performScrollToNode(hasText("Searchable saved posts"))
        composeRule.onNodeWithText("Searchable saved posts").assertIsDisplayed()

        // 5. Emoji Credits
        composeRule.runOnUiThread {
            container.emitDeepLinkRoute(AppRoute.EmojiCredits)
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("emoji_credits_screen")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("emoji_credits_screen").assertIsDisplayed()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Emoji Mart")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Emoji Mart").assertIsDisplayed()
        composeRule.onNode(verticalScrollableMatcher)
            .performScrollToNode(hasText("Fuse.js"))
        composeRule.onNodeWithText("Fuse.js").assertIsDisplayed()
    }
}
