package am.noz.caelum.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoutesTest {

    @Test
    fun parsesHttpsCustomSchemesAndWidgetParams() {
        val profile = DeepLinkParser.parseUri("https://bsky.app/profile/alice.bsky.social")
        assertEquals(DeepLinkTarget.RouteTarget(AppRoute.Profile("alice.bsky.social"), null), profile)

        val post = DeepLinkParser.parseUri("blueskyplus://profile/did:plc:abc/post/3xyz")
        assertEquals(DeepLinkTarget.RouteTarget(AppRoute.PostThread("did:plc:abc", "3xyz"), null), post)

        val legacyScheme = DeepLinkParser.parseUri("bluesky://settings/infrastructure")
        assertEquals(
            DeepLinkTarget.RouteTarget(AppRoute.InfrastructureSettings, RootTab.SettingsTab),
            legacyScheme,
        )

        val topic = DeepLinkParser.parseUri("https://bsky.app/topic/atproto?ref_src=widget&ref_rank=1")
        assertEquals(DeepLinkTarget.RouteTarget(AppRoute.Topic("atproto"), null), topic)

        val intent = DeepLinkParser.parseUri("blueskyplus://intent/compose?text=Hello&imageUris=file%3A%2F%2F%2Fa.jpg%7C100%7C100")
        assertTrue(intent is DeepLinkTarget.ComposeIntentTarget)
        val compose = intent as DeepLinkTarget.ComposeIntentTarget
        assertEquals("Hello", compose.text)
        assertEquals(listOf("file:///a.jpg"), compose.imageUris)
    }

    @Test
    fun convertsAtUrisToNativeRoutes() {
        assertEquals(
            AppRoute.PostThread("did:plc:abc", "3k1"),
            DeepLinkParser.atUriToRoute("at://did:plc:abc/app.bsky.feed.post/3k1"),
        )
        assertEquals(
            AppRoute.CustomFeed("did:plc:abc", "whats-hot"),
            DeepLinkParser.atUriToRoute("at://did:plc:abc/app.bsky.feed.generator/whats-hot"),
        )
    }

    @Test
    fun rejectsExternalHttpUrlsAsInternalDeepLinks() {
        assertNull(DeepLinkParser.parseUri("https://example.com/"))
        assertNull(DeepLinkParser.parseUri("https://status.bsky.app/"))
        assertNull(DeepLinkParser.parseUri("https://github.com/settings"))
        assertNull(DeepLinkParser.parseUri("https://example.com/search?q=bluesky"))
        assertNull(DeepLinkParser.parseUri("https://evilbsky.app/profile/alice.bsky.social"))
        assertEquals(
            DeepLinkTarget.RouteTarget(AppRoute.Profile("alice.bsky.social"), null),
            DeepLinkParser.parseUri("/profile/alice.bsky.social"),
        )
    }
}
