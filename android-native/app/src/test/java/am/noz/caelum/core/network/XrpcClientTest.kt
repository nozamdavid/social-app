package am.noz.caelum.core.network

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.model.NotificationPrefEntry
import am.noz.caelum.core.model.NotificationPreferencesResponse
import am.noz.caelum.core.model.SessionTokens
import am.noz.caelum.core.storage.PreferencesRepository
import am.noz.caelum.ui.composer.composerPostWrites
import am.noz.caelum.ui.navigation.AppRoute
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
class XrpcClientTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `single-flight refresh retries expired tokens and attaches custom AppView proxy header`() = runTest {
        val server = MockWebServer()
        val refreshCalls = AtomicInteger(0)
        val timelineCalls = AtomicInteger(0)

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: ""
                return when {
                    path.startsWith("/xrpc/com.atproto.server.refreshSession") -> {
                        refreshCalls.incrementAndGet()
                        assertEquals("Bearer refresh-1", request.getHeader("Authorization"))
                        MockResponse()
                            .setResponseCode(200)
                            .setBody("""{"accessJwt":"access-2","refreshJwt":"refresh-2","did":"did:plc:me","handle":"me.bsky.social"}""")
                    }
                    path.startsWith("/xrpc/app.bsky.feed.getTimeline") -> {
                        timelineCalls.incrementAndGet()
                        assertEquals("did:web:api.eurosky.network#bsky_appview", request.getHeader("atproto-proxy"))
                        if (request.getHeader("Authorization") == "Bearer access-1") {
                            MockResponse()
                                .setResponseCode(400)
                                .setBody("""{"error":"ExpiredToken","message":"Token has expired"}""")
                        } else {
                            assertEquals("Bearer access-2", request.getHeader("Authorization"))
                            MockResponse()
                                .setResponseCode(200)
                                .setBody("""{"cursor":"c1","feed":[]}""")
                        }
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        try {
            val baseUrl = server.url("").toString().trimEnd('/')
            val currentTokens = java.util.concurrent.atomic.AtomicReference(SessionTokens("access-1", "refresh-1"))
            val delegate = object : XrpcSessionDelegate {
                override fun currentAuth(): ActiveXrpcAuth {
                    val tok = currentTokens.get()
                    return ActiveXrpcAuth(
                        did = "did:plc:me",
                        pdsUrl = baseUrl,
                        accessJwt = tok.accessJwt,
                        refreshJwt = tok.refreshJwt,
                    )
                }
                override suspend fun updateRefreshedTokens(failed: ActiveXrpcAuth, tokens: SessionTokens): Boolean {
                    currentTokens.set(tokens)
                    return true
                }
            }

            val client = XrpcClient(
                sessionDelegate = delegate,
                infraProvider = { InfrastructureSettings(appviewHostname = "api.eurosky.network") },
            )
            val api = AtprotoApi(client)

            // Fire 4 concurrent requests against an expired token; refreshSession must run ONCE.
            val results = (1..4).map {
                async { api.getTimeline() }
            }.awaitAll()

            assertEquals(1, refreshCalls.get())
            assertEquals("access-2", currentTokens.get().accessJwt)
            assertEquals("c1", results.first().cursor)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `thread mute and unmute send the root URI to AppView`() = runTest {
        MockWebServer().use { server ->
            repeat(2) { server.enqueue(MockResponse().setResponseCode(204)) }
            val api = AtprotoApi(XrpcClient(publicOriginOverride = server.url("").toString()))
            val root = "at://did:plc:alice/app.bsky.feed.post/root"

            api.setThreadMuted(root, true)
            api.setThreadMuted(root, false)

            for (method in listOf("muteThread", "unmuteThread")) {
                val request = server.takeRequest()
                assertEquals("POST", request.method)
                assertEquals("/xrpc/app.bsky.graph.$method", request.path)
                assertEquals(root, AtProtoJson.parseToJsonElement(request.body.readUtf8()).jsonObject["root"]?.jsonPrimitive?.content)
            }
        }
    }

    @Test
    fun `publishing a thread submits one authenticated validated batch`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{}"))
            server.start()
            val did = "did:plc:me"
            val baseUrl = server.url("").toString().trimEnd('/')
            val delegate = object : XrpcSessionDelegate {
                override fun currentAuth() = ActiveXrpcAuth(did, baseUrl, "acc", "ref")
            }
            val api = AtprotoApi(XrpcClient(sessionDelegate = delegate))
            val writes = composerPostWrites(
                did = did,
                posts = List(2) { index -> buildJsonObject { put("text", JsonPrimitive("Post $index")) } },
                parent = null,
                root = null,
                threadgateMode = "following",
                allowQuotes = false,
            )

            api.applyWrites(did, writes)

            assertEquals(1, server.requestCount)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/xrpc/com.atproto.repo.applyWrites", request.path)
            assertEquals("Bearer acc", request.getHeader("Authorization"))
            val body = AtProtoJson.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertEquals(JsonPrimitive(did), body["repo"])
            assertEquals(JsonPrimitive(true), body["validate"])
            assertEquals(writes, body["writes"])
        }
    }

    @Test
    fun `edit post within window sends applyWrites delete and create preserving rkey and originalText`() = runTest {
        val server = MockWebServer()
        var capturedBody = ""
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path?.startsWith("/xrpc/com.atproto.repo.applyWrites") == true) {
                    capturedBody = request.body.readUtf8()
                    return MockResponse().setResponseCode(200).setBody("""{"results":[]}""")
                }
                return MockResponse().setResponseCode(404)
            }
        }
        server.start()
        try {
            val baseUrl = server.url("").toString().trimEnd('/')
            val delegate = object : XrpcSessionDelegate {
                override fun currentAuth() = ActiveXrpcAuth("did:plc:me", baseUrl, "acc", "ref")
            }
            val api = AtprotoApi(XrpcClient(sessionDelegate = delegate))

            val existing = buildJsonObject {
                put("\$type", JsonPrimitive("app.bsky.feed.post"))
                put("text", JsonPrimitive("First draft"))
                put("createdAt", JsonPrimitive("2026-04-01T12:00:00Z"))
            }

            val updated = api.editPostWithinWindow(
                repo = "did:plc:me",
                postUri = "at://did:plc:me/app.bsky.feed.post/3kabc999",
                existingRecord = existing,
                newText = "Edited text!",
                newFacets = emptyList(),
            )

            assertEquals("Edited text!", updated["text"]?.jsonPrimitive?.content)
            assertEquals("First draft", updated["originalText"]?.jsonPrimitive?.content)
            assertEquals("2026-04-01T12:00:00Z", updated["createdAt"]?.jsonPrimitive?.content)

            val parsedReq = AtProtoJson.parseToJsonElement(capturedBody).jsonObject
            val writes = parsedReq["writes"]!!.jsonArray
            assertEquals(2, writes.size)
            assertEquals("com.atproto.repo.applyWrites#delete", writes[0].jsonObject["\$type"]?.jsonPrimitive?.content)
            assertEquals("3kabc999", writes[0].jsonObject["rkey"]?.jsonPrimitive?.content)
            assertEquals("com.atproto.repo.applyWrites#create", writes[1].jsonObject["\$type"]?.jsonPrimitive?.content)
            assertEquals("3kabc999", writes[1].jsonObject["rkey"]?.jsonPrimitive?.content)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `push gateway parses FCM fixtures syncs preferencesV2 and enforces suppression`() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val ds = PreferenceDataStoreFactory.create(produceFile = { File(tempFolder.newFolder(), "push_prefs.preferences_pb") })
        val prefsRepo = PreferencesRepository(ctx, ds)

        val server = MockWebServer()
        var recordedProxyHeader: String? = null
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path?.startsWith("/xrpc/app.bsky.notification.putPreferencesV2") == true) {
                    recordedProxyHeader = request.getHeader("atproto-proxy")
                    return MockResponse().setResponseCode(200).setBody("""{"preferences":{}}""")
                }
                return MockResponse().setResponseCode(404)
            }
        }
        server.start()
        try {
            val baseUrl = server.url("").toString().trimEnd('/')
            val delegate = object : XrpcSessionDelegate {
                override fun currentAuth() = ActiveXrpcAuth("did:plc:recipient", baseUrl, "acc", "ref")
            }
            val api = AtprotoApi(XrpcClient(sessionDelegate = delegate))
            val pushMgr = PushGatewayManager(api, prefsRepo)

            val notifPrefs = NotificationPreferencesResponse(
                follow = NotificationPrefEntry(push = true),
                like = NotificationPrefEntry(push = false),
            )
            pushMgr.syncPreferences("did:plc:recipient", notifPrefs)
            assertEquals(PushGatewayManager.DEFAULT_PUSH_GATEWAY_DID, recordedProxyHeader)
            assertFalse(pushMgr.shouldDisplayNotification("did:plc:recipient", "like"))
            assertTrue(pushMgr.shouldDisplayNotification("did:plc:recipient", "follow"))

            val fixtureRaw = XrpcClientTest::class.java.classLoader!!
                .getResource("fixtures/notifications_and_migration.json")!!
                .readText()
            val payloads = AtProtoJson.parseToJsonElement(fixtureRaw).jsonObject["fcmPayloads"]!!.jsonArray
            for (el in payloads) {
                val obj = el.jsonObject
                val dataMap = obj["data"]!!.jsonObject.mapValues { it.value.jsonPrimitive.content }
                val parsed = PushGatewayManager.parseFcmData(dataMap)
                assertNotNull(parsed)
                when (obj["name"]!!.jsonPrimitive.content) {
                    "like_gateway_expo_data_only" -> {
                        assertEquals("like", parsed!!.reason)
                        assertEquals(
                            AppRoute.PostThread("did:plc:recipient", "3abc"),
                            parsed.targetRoute,
                        )
                    }
                    "follow_gateway_flat" -> {
                        assertEquals(AppRoute.Profile("bob.bsky.social"), parsed!!.targetRoute)
                    }
                    "chat_message" -> {
                        assertEquals(AppRoute.MessagesConversation("3convo1"), parsed!!.targetRoute)
                    }
                }
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `listConvos decodes object kind for directConvo and groupConvo`() = runTest {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path?.startsWith("/xrpc/chat.bsky.convo.listConvos") == true) {
                    assertEquals(XrpcClient.BSKY_CHAT_PROXY_DID, request.getHeader("atproto-proxy"))
                    return MockResponse().setResponseCode(200).setBody(
                        """
                        {
                          "cursor": "c2",
                          "convos": [
                            {
                              "id": "convo-direct",
                              "rev": "1",
                              "members": [{"did": "did:plc:alice", "handle": "alice.bsky.social"}],
                              "muted": false,
                              "unreadCount": 2,
                              "status": "accepted",
                              "kind": {"${"$"}type": "chat.bsky.convo.defs#directConvo"},
                              "details": {"${"$"}type": "chat.bsky.convo.defs#directConvoDetails"}
                            },
                            {
                              "id": "convo-group",
                              "rev": "2",
                              "members": [{"did": "did:plc:bob", "handle": "bob.bsky.social"}],
                              "muted": true,
                              "unreadCount": 0,
                              "status": "accepted",
                              "kind": {"${"$"}type": "chat.bsky.convo.defs#groupConvo", "lockStatus": "unlocked"}
                            }
                          ]
                        }
                        """.trimIndent()
                    )
                }
                return MockResponse().setResponseCode(404)
            }
        }
        server.start()
        try {
            val baseUrl = server.url("").toString().trimEnd('/')
            val delegate = object : XrpcSessionDelegate {
                override fun currentAuth() = ActiveXrpcAuth("did:plc:me", baseUrl, "acc", "ref")
            }
            val api = AtprotoApi(XrpcClient(sessionDelegate = delegate))

            val resp = api.listConvos(status = "accepted")
            assertEquals("c2", resp.cursor)
            assertEquals(2, resp.convos.size)
            assertEquals("convo-direct", resp.convos[0].id)
            assertEquals(2, resp.convos[0].unreadCount)
            assertEquals("convo-group", resp.convos[1].id)
            assertTrue(resp.convos[1].muted)
            assertEquals(
                "chat.bsky.convo.defs#groupConvo",
                resp.convos[1].kind?.jsonObject?.get("\$type")?.jsonPrimitive?.content,
            )
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `accept-labelers redacts only Bluesky labeler and is omitted on PDS endpoints`() = runTest {
        MockWebServer().use { server ->
            repeat(3) { server.enqueue(MockResponse().setResponseCode(200).setBody("{}")) }
            val baseUrl = server.url("").toString().trimEnd('/')
            val delegate = object : XrpcSessionDelegate {
                override fun currentAuth() = ActiveXrpcAuth(
                    did = "did:plc:me",
                    pdsUrl = baseUrl,
                    accessJwt = "acc",
                    refreshJwt = "ref",
                    labelers = listOf(
                        am.noz.caelum.core.session.SessionManager.BLUESKY_LABELER_DID,
                        "did:plc:thirdpartylabeler",
                    ),
                )
            }
            val client = XrpcClient(sessionDelegate = delegate)

            client.getJson("app.bsky.feed.getTimeline")
            client.getJson("app.bsky.actor.getPreferences")
            client.postJson("com.atproto.server.deleteSession", body = null)

            val timelineReq = server.takeRequest()
            assertEquals(
                "${am.noz.caelum.core.session.SessionManager.BLUESKY_LABELER_DID};redact, did:plc:thirdpartylabeler",
                timelineReq.getHeader("atproto-accept-labelers"),
            )

            val prefsReq = server.takeRequest()
            assertNull(prefsReq.getHeader("atproto-accept-labelers"))

            val deleteSessionReq = server.takeRequest()
            assertNull(deleteSessionReq.getHeader("atproto-accept-labelers"))
            assertNull(deleteSessionReq.getHeader("Content-Type"))
        }
    }

    @Test
    fun `push gateway strips service fragment from serviceDid while keeping fragment in atproto-proxy`() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val ds = PreferenceDataStoreFactory.create(produceFile = { File(tempFolder.newFolder(), "push_reg.preferences_pb") })
        val prefsRepo = PreferencesRepository(ctx, ds)

        MockWebServer().use { server ->
            repeat(2) { server.enqueue(MockResponse().setResponseCode(200).setBody("{}")) }
            val baseUrl = server.url("").toString().trimEnd('/')
            val delegate = object : XrpcSessionDelegate {
                override fun currentAuth() = ActiveXrpcAuth("did:plc:recipient", baseUrl, "acc", "ref")
            }
            val api = AtprotoApi(XrpcClient(sessionDelegate = delegate))
            val pushMgr = PushGatewayManager(api, prefsRepo)

            pushMgr.registerToken("fcm-token-123")
            pushMgr.unregisterToken("fcm-token-123")

            for (nsid in listOf("registerPush", "unregisterPush")) {
                val req = server.takeRequest()
                assertEquals("/xrpc/app.bsky.notification.$nsid", req.path)
                assertEquals(PushGatewayManager.DEFAULT_PUSH_GATEWAY_DID, req.getHeader("atproto-proxy"))
                val body = AtProtoJson.parseToJsonElement(req.body.readUtf8()).jsonObject
                assertEquals("did:web:push.noz.am", body["serviceDid"]?.jsonPrimitive?.content)
                assertEquals("android", body["platform"]?.jsonPrimitive?.content)
                assertEquals(PushGatewayManager.CAELUM_APP_ID, body["appId"]?.jsonPrimitive?.content)
                assertEquals("fcm-token-123", body["token"]?.jsonPrimitive?.content)
            }
        }
    }
}
