package am.noz.caelum.core.session

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import am.noz.caelum.core.model.LabelerPrefItem
import am.noz.caelum.core.model.PostShadow
import am.noz.caelum.core.model.PostShadowStore
import am.noz.caelum.core.model.SavedFeedPref
import am.noz.caelum.core.model.SessionTokens
import am.noz.caelum.core.model.StoredAccount
import am.noz.caelum.core.network.AtprotoApi
import am.noz.caelum.core.network.IdentityResolver
import am.noz.caelum.core.network.XrpcClient
import am.noz.caelum.core.storage.InMemoryTokenVault
import am.noz.caelum.core.storage.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
class SessionManagerTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `cached subscriptions are requested before labeler services finish loading`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(tempFolder.newFolder(), "labelers.preferences_pb")
        val prefs = PreferencesRepository(context, PreferenceDataStoreFactory.create(produceFile = { file }))
        val vault = InMemoryTokenVault()
        val account = StoredAccount("did:plc:alice", "alice.example", "https://alice.example")
        val subscribed = "did:plc:communitylabeler"
        prefs.updateAccounts(listOf(account), account.did)
        prefs.updateModerationSettings(account.did) { it.copy(labelers = listOf(LabelerPrefItem(subscribed))) }
        vault.saveTokens(account.did, SessionTokens("access", "refresh"))

        val manager = SessionManager(prefs, vault, PostShadowStore(), scope = backgroundScope)
        manager.initialize()

        assertTrue(manager.state.value.subscribedLabelers.isEmpty())
        assertEquals(listOf(SessionManager.BLUESKY_LABELER_DID, subscribed), manager.currentAuth()?.labelers)
    }

    @Test
    fun `preferences received after an account switch cannot reach either account cache`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(tempFolder.newFolder(), "preferences.preferences_pb")
        val prefs = PreferencesRepository(context, PreferenceDataStoreFactory.create(produceFile = { file }))
        val manager = SessionManager(prefs, InMemoryTokenVault(), PostShadowStore(), scope = backgroundScope)
        val started = CountDownLatch(1)
        val respond = CountDownLatch(1)
        val authorization = AtomicReference<String?>()

        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.requestUrl?.encodedPath != "/xrpc/app.bsky.actor.getPreferences") {
                        return MockResponse().setResponseCode(404)
                    }
                    authorization.set(request.getHeader("Authorization"))
                    started.countDown()
                    respond.await(10, TimeUnit.SECONDS)
                    return MockResponse().setBody(
                        """{"preferences":[
                            {"${'$'}type":"app.bsky.actor.defs#interestsPref","tags":["stale"]},
                            {"${'$'}type":"app.bsky.actor.defs#adultContentPref","enabled":true}
                        ]}""",
                    )
                }
            }
            val origin = server.url("/").toString().trimEnd('/')
            val alice = StoredAccount(did = "did:plc:alice", handle = "alice.example", serviceUrl = origin)
            val bob = StoredAccount(did = "did:plc:bob", handle = "bob.example", serviceUrl = origin)
            manager.initialize()
            manager.registerNewAccount(alice, SessionTokens("access-a", "refresh-a"))
            prefs.setInterests(alice.did, listOf("Alice"))
            prefs.setInterests(bob.did, listOf("Bob"))
            manager.attachNetwork(IdentityResolver(OkHttpClient()), AtprotoApi(XrpcClient(sessionDelegate = manager)))

            val fetch = async(Dispatchers.IO) { runCatching { manager.syncPreferencesFromServer(alice.did) } }
            try {
                assertTrue(started.await(10, TimeUnit.SECONDS))
                // Adding an account switches the active identity without launching another fetch.
                manager.registerNewAccount(bob, SessionTokens("access-b", "refresh-b"))
            } finally {
                respond.countDown()
            }
            fetch.await()

            assertEquals("Bearer access-a", authorization.get())
            assertEquals(bob.did, manager.state.value.currentDid)
            assertEquals(listOf("Bob"), prefs.currentSnapshot().interests)
            assertTrue(prefs.getSavedFeeds(alice.did).isEmpty())
            assertTrue(prefs.getSavedFeeds(bob.did).isEmpty())
            assertFalse(prefs.getModerationSettings(alice.did).adultContentEnabled)
            assertFalse(prefs.getModerationSettings(bob.did).adultContentEnabled)

            manager.syncPreferencesFromServer(alice.did)
            assertEquals(1, server.requestCount)
            prefs.updateAccounts(listOf(alice, bob), alice.did)
            assertEquals(listOf("Alice"), prefs.currentSnapshot().interests)
        }
    }

    @Test
    fun `late refresh can rotate inactive credentials but cannot replace a new login or resurrect removed accounts`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(tempFolder.newFolder(), "credentials.preferences_pb")
        val prefs = PreferencesRepository(context, PreferenceDataStoreFactory.create(produceFile = { file }))
        val vault = InMemoryTokenVault()
        val manager = SessionManager(prefs, vault, PostShadowStore(), scope = backgroundScope)
        val alice = StoredAccount(did = "did:plc:alice", handle = "alice.example", serviceUrl = "https://alice.example")
        val bob = StoredAccount(did = "did:plc:bob", handle = "bob.example", serviceUrl = "https://bob.example")
        val first = SessionTokens("first", "first-refresh")
        val rotated = SessionTokens("rotated", "rotated-refresh")
        val replacement = SessionTokens("replacement", "replacement-refresh")
        manager.initialize()
        manager.registerNewAccount(alice, first)
        val failed = checkNotNull(manager.currentAuth())
        manager.registerNewAccount(bob, first)

        manager.expireFailedSession(failed)
        assertNull(manager.state.value.expiredDid)
        assertTrue(manager.updateRefreshedTokens(failed, rotated))
        assertEquals(rotated, vault.getTokens(alice.did))
        assertEquals(first.accessJwt, manager.currentAuth()?.accessJwt)
        assertTrue(manager.switchAccount(alice.did))
        val oldLogin = checkNotNull(manager.currentAuth())

        manager.registerNewAccount(alice, replacement)
        manager.expireFailedSession(oldLogin)
        assertNull(manager.state.value.expiredDid)
        assertFalse(manager.updateRefreshedTokens(oldLogin, first))
        assertEquals(replacement, vault.getTokens(alice.did))
        val removed = checkNotNull(manager.currentAuth())

        manager.removeAccount(alice.did)
        assertFalse(manager.updateRefreshedTokens(removed, rotated))
        assertNull(vault.getTokens(alice.did))
        assertEquals(bob.did, manager.state.value.currentDid)
    }

    @Test
    fun `multi-account sign in switch and logout isolate tokens and clear shadow store`() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val ds = PreferenceDataStoreFactory.create(produceFile = { File(tempFolder.newFolder(), "session_prefs.preferences_pb") })
        val prefs = PreferencesRepository(ctx, ds)
        val vault = InMemoryTokenVault()
        val shadows = PostShadowStore()

        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: ""
                return when {
                    path.startsWith("/xrpc/com.atproto.identity.resolveHandle?handle=alice.example") ->
                        MockResponse().setResponseCode(200).setBody("""{"did":"did:plc:alice"}""")
                    path.startsWith("/xrpc/com.atproto.identity.resolveHandle?handle=bob.example") ->
                        MockResponse().setResponseCode(200).setBody("""{"did":"did:plc:bob"}""")
                    path.startsWith("/xrpc/com.atproto.server.createSession") -> {
                        val body = request.body.readUtf8()
                        if ("alice.example" in body) {
                            MockResponse().setResponseCode(200).setBody(
                                """{"did":"did:plc:alice","handle":"alice.example","accessJwt":"acc-a","refreshJwt":"ref-a"}"""
                            )
                        } else {
                            MockResponse().setResponseCode(200).setBody(
                                """{"did":"did:plc:bob","handle":"bob.example","accessJwt":"acc-b","refreshJwt":"ref-b"}"""
                            )
                        }
                    }
                    path.startsWith("/xrpc/app.bsky.actor.getProfile?actor=did:plc:alice") ->
                        MockResponse().setResponseCode(200).setBody(
                            """{"did":"did:plc:alice","handle":"alice.example","displayName":"Alice"}"""
                        )
                    path.startsWith("/xrpc/app.bsky.actor.getProfile?actor=did:plc:bob") ->
                        MockResponse().setResponseCode(200).setBody(
                            """{"did":"did:plc:bob","handle":"bob.example","displayName":"Bob"}"""
                        )
                    path.startsWith("/xrpc/app.bsky.actor.getPreferences") ->
                        MockResponse().setResponseCode(200).setBody("""{"preferences":[]}""")
                    path.startsWith("/xrpc/app.bsky.labeler.getServices") ->
                        MockResponse().setResponseCode(200).setBody("""{"views":[]}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        try {
            val baseUrl = server.url("").toString().trimEnd('/')
            val manager = SessionManager(
                preferences = prefs,
                tokenVault = vault,
                postShadowStore = shadows,
                scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob()),
            )
            val xrpc = XrpcClient(sessionDelegate = manager)
            val api = AtprotoApi(xrpc)
            val resolver = IdentityResolver(OkHttpClient())
            manager.attachNetwork(resolver, api, null)
            manager.initialize()

            assertFalse(manager.state.value.hasSession)

            val alice = manager.signIn(baseUrl, "alice.example", "pw1")
            assertEquals("did:plc:alice", alice.did)
            assertEquals("acc-a", manager.currentAuth()?.accessJwt)

            shadows.update("at://post/1") { PostShadow(likeCount = 99) }
            assertNotNull(shadows.get("at://post/1"))

            val bob = manager.signIn(baseUrl, "bob.example", "pw2")
            assertEquals("did:plc:bob", bob.did)
            assertEquals("acc-b", manager.currentAuth()?.accessJwt)
            // Account boundary clears shadows
            assertNull(shadows.get("at://post/1"))
            assertEquals(2, manager.state.value.accounts.size)

            assertTrue(manager.switchAccount("did:plc:alice"))
            assertEquals("did:plc:alice", manager.state.value.currentDid)
            assertEquals("acc-a", manager.currentAuth()?.accessJwt)

            manager.removeAccount("did:plc:alice")
            assertNull(vault.getTokens("did:plc:alice"))
            assertEquals("did:plc:bob", manager.state.value.currentDid)
            assertEquals("acc-b", manager.currentAuth()?.accessJwt)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `normalizeSavedFeeds deduplicates by value, normalizes type, and guarantees unique non-blank ids`() {
        val listUri = "at://did:plc:abc/app.bsky.graph.list/my-list"
        val feedUri = "at://did:plc:def/app.bsky.feed.generator/whats-hot"
        val input = listOf(
            SavedFeedPref(id = "", type = "feed", value = "following", pinned = true),
            SavedFeedPref(id = "dup-id", type = "feed", value = listUri, pinned = true),
            SavedFeedPref(id = "dup-id", type = "feed", value = feedUri, pinned = true),
            SavedFeedPref(id = "another-id", type = "feed", value = "  $feedUri  ", pinned = false),
            SavedFeedPref(id = "blank-val", type = "feed", value = "   ", pinned = true),
        )

        val normalized = SessionManager.normalizeSavedFeeds(input)

        assertEquals(3, normalized.size)
        assertEquals(listOf("following", listUri, feedUri), normalized.map { it.value })
        assertEquals(listOf("timeline", "list", "feed"), normalized.map { it.type })
        assertTrue(normalized.all { it.id.isNotBlank() })
        assertEquals(normalized.size, normalized.map { it.id }.toSet().size)
        assertEquals("dup-id", normalized[1].id)
        assertTrue(normalized[2].id != "dup-id")

        // When no timeline entry is present, a pinned following timeline entry is prepended
        val withoutTimeline = SessionManager.normalizeSavedFeeds(
            listOf(SavedFeedPref(id = "f1", type = "feed", value = feedUri, pinned = true))
        )
        assertEquals(2, withoutTimeline.size)
        assertEquals("timeline", withoutTimeline[0].type)
        assertEquals("following", withoutTimeline[0].value)
        assertTrue(withoutTimeline[0].pinned)
        assertTrue(withoutTimeline[0].id.isNotBlank())

        // Prefer a valid non-URI id over an earlier corrupted at:// id for the same feed value
        val corruptedFirst = SessionManager.normalizeSavedFeeds(
            listOf(
                SavedFeedPref(id = "timeline-1", type = "timeline", value = "following", pinned = true),
                SavedFeedPref(id = feedUri, type = "feed", value = feedUri, pinned = false),
                SavedFeedPref(id = "3ktidvalid", type = "feed", value = feedUri, pinned = true),
            )
        )
        val mergedFeed = corruptedFirst.first { it.value == feedUri }
        assertEquals("3ktidvalid", mergedFeed.id)
        assertTrue(mergedFeed.pinned)
    }

    @Test
    fun `syncPreferencesFromServer excludes dead feed generators omitted by getFeedGenerators and 404 lists`() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val ds = PreferenceDataStoreFactory.create(produceFile = { File(tempFolder.newFolder(), "dead_feeds.preferences_pb") })
        val prefs = PreferencesRepository(ctx, ds)
        val vault = InMemoryTokenVault()
        val shadows = PostShadowStore()

        val liveFeedUri = "at://did:plc:z72i7hdynmk6r22z27h6tvur/app.bsky.feed.generator/whats-hot"
        val deadFeedUri = "at://did:plc:q6gjnaw2blty4crticxkmujt/app.bsky.feed.generator/my-pins"
        val liveListUri = "at://did:plc:alice/app.bsky.graph.list/live-list"
        val deadListUri = "at://did:plc:alice/app.bsky.graph.list/dead-list"

        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: ""
                return when {
                    path.startsWith("/xrpc/com.atproto.identity.resolveHandle") ->
                        MockResponse().setResponseCode(200).setBody("""{"did":"did:plc:alice"}""")
                    path.startsWith("/xrpc/com.atproto.server.createSession") ->
                        MockResponse().setResponseCode(200).setBody(
                            """{"did":"did:plc:alice","handle":"alice.example","accessJwt":"acc-a","refreshJwt":"ref-a"}"""
                        )
                    path.startsWith("/xrpc/app.bsky.actor.getProfile") ->
                        MockResponse().setResponseCode(200).setBody(
                            """{"did":"did:plc:alice","handle":"alice.example","displayName":"Alice"}"""
                        )
                    path.startsWith("/xrpc/app.bsky.actor.getPreferences") ->
                        MockResponse().setResponseCode(200).setBody(
                            """
                            {
                              "preferences": [
                                {
                                  "${"$"}type": "app.bsky.actor.defs#savedFeedsPrefV2",
                                  "items": [
                                    {"id": "1", "type": "timeline", "value": "following", "pinned": true},
                                    {"id": "2", "type": "feed", "value": "$liveFeedUri", "pinned": true},
                                    {"id": "3", "type": "feed", "value": "$deadFeedUri", "pinned": true},
                                    {"id": "4", "type": "list", "value": "$liveListUri", "pinned": true},
                                    {"id": "5", "type": "list", "value": "$deadListUri", "pinned": true}
                                  ]
                                }
                              ]
                            }
                            """.trimIndent()
                        )
                    path.startsWith("/xrpc/app.bsky.feed.getFeedGenerators") ->
                        MockResponse().setResponseCode(200).setBody(
                            """
                            {
                              "feeds": [
                                {
                                  "uri": "$liveFeedUri",
                                  "cid": "cid-1",
                                  "did": "did:plc:z72i7hdynmk6r22z27h6tvur",
                                  "creator": {"did": "did:plc:z72i7hdynmk6r22z27h6tvur", "handle": "bsky.app"},
                                  "displayName": "Discover",
                                  "indexedAt": "2026-01-01T00:00:00Z"
                                }
                              ]
                            }
                            """.trimIndent()
                        )
                    path.contains("live-list") ->
                        MockResponse().setResponseCode(200).setBody(
                            """
                            {
                              "list": {
                                "uri": "$liveListUri",
                                "cid": "cid-l1",
                                "name": "Live List",
                                "purpose": "app.bsky.graph.defs#curatelist",
                                "creator": {"did": "did:plc:alice", "handle": "alice.example"},
                                "indexedAt": "2026-01-01T00:00:00Z"
                              },
                              "items": []
                            }
                            """.trimIndent()
                        )
                    path.contains("dead-list") ->
                        MockResponse().setResponseCode(400).setBody("""{"error":"InvalidRequest","message":"List not found"}""")
                    path.startsWith("/xrpc/app.bsky.labeler.getServices") ->
                        MockResponse().setResponseCode(200).setBody("""{"views":[]}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        try {
            val baseUrl = server.url("").toString().trimEnd('/')
            val manager = SessionManager(
                preferences = prefs,
                tokenVault = vault,
                postShadowStore = shadows,
                scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob()),
            )
            val xrpc = XrpcClient(sessionDelegate = manager)
            val api = AtprotoApi(xrpc)
            val resolver = IdentityResolver(OkHttpClient())
            manager.attachNetwork(resolver, api, null)
            manager.initialize()

            manager.signIn(baseUrl, "alice.example", "pw1")
            manager.syncPreferencesFromServer("did:plc:alice")

            val st = manager.state.value
            assertTrue(st.feedsHydrated)
            assertEquals(listOf("following", liveFeedUri, liveListUri), st.pinnedFeeds.map { it.value })
            assertTrue(liveFeedUri in st.feedGenerators)
            assertFalse(deadFeedUri in st.feedGenerators)
            assertTrue(liveListUri in st.listViews)
            assertFalse(deadListUri in st.listViews)
        } finally {
            server.shutdown()
        }
    }
}
