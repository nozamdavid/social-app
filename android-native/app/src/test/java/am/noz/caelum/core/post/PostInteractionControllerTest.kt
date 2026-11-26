package am.noz.caelum.core.post

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import am.noz.caelum.core.model.AtProtoJson
import am.noz.caelum.core.model.PostShadowStore
import am.noz.caelum.core.model.PostView
import am.noz.caelum.core.model.ProfileViewBasic
import am.noz.caelum.core.model.SessionTokens
import am.noz.caelum.core.model.StoredAccount
import am.noz.caelum.core.model.ToggleMutationQueue
import am.noz.caelum.core.network.AtprotoApi
import am.noz.caelum.core.network.IdentityResolver
import am.noz.caelum.core.network.XrpcClient
import am.noz.caelum.core.session.SessionManager
import am.noz.caelum.core.storage.CaelumDatabase
import am.noz.caelum.core.storage.InMemoryTokenVault
import am.noz.caelum.core.storage.PreferencesRepository
import am.noz.caelum.core.storage.SearchableBookmarkEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
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
class PostInteractionControllerTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `rapid like then unlike deletes the newly created server record URI`() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val ds = PreferenceDataStoreFactory.create(
            produceFile = { File(tempFolder.newFolder(), "post_ctrl.preferences_pb") },
        )
        val prefs = PreferencesRepository(ctx, ds)
        val vault = InMemoryTokenVault()
        val shadows = PostShadowStore()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(ctx, CaelumDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val createStarted = CountDownLatch(1)
        val allowCreateToFinish = CountDownLatch(1)
        val deletedRkey = AtomicReference<String?>()

        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path ?: ""
                    return when {
                        path.startsWith("/xrpc/com.atproto.repo.createRecord") -> {
                            createStarted.countDown()
                            allowCreateToFinish.await(10, TimeUnit.SECONDS)
                            MockResponse().setResponseCode(200).setBody(
                                """{"uri":"at://did:plc:alice/app.bsky.feed.like/3knewlike","cid":"bafynew"}"""
                            )
                        }
                        path.startsWith("/xrpc/com.atproto.repo.deleteRecord") -> {
                            val body = AtProtoJson.parseToJsonElement(request.body.readUtf8()).jsonObject
                            deletedRkey.set(body["rkey"]?.jsonPrimitive?.content)
                            MockResponse().setResponseCode(200).setBody("{}")
                        }
                        else -> MockResponse().setResponseCode(200).setBody("{}")
                    }
                }
            }
            server.start()
            try {
                val origin = server.url("").toString().trimEnd('/')
                val manager = SessionManager(prefs, vault, shadows, scope = backgroundScope)
                val xrpc = XrpcClient(sessionDelegate = manager)
                val api = AtprotoApi(xrpc)
                manager.attachNetwork(IdentityResolver(OkHttpClient()), api)
                manager.initialize()
                manager.registerNewAccount(
                    StoredAccount("did:plc:alice", "alice.example", origin),
                    SessionTokens("acc-a", "ref-a"),
                )

                val controller = PostInteractionController(
                    api = api,
                    sessionManager = manager,
                    preferences = prefs,
                    shadowStore = shadows,
                    mutationQueue = ToggleMutationQueue(),
                    bookmarkEngine = SearchableBookmarkEngine(db.bookmarkIndexDao()),
                    tldsProvider = { emptySet() },
                )

                val post = PostView(
                    uri = "at://did:plc:bob/app.bsky.feed.post/3kpost1",
                    cid = "bafypost1",
                    author = ProfileViewBasic(did = "did:plc:bob", handle = "bob.example"),
                    record = buildJsonObject {},
                    likeCount = 5,
                    indexedAt = "2026-01-01T00:00:00Z",
                )

                val firstTap = async(Dispatchers.IO) { controller.toggleLike(post) }
                assertTrue(createStarted.await(10, TimeUnit.SECONDS))
                // Shadow now holds pending://like; second tap toggles back to unliked immediately
                val secondTap = async(Dispatchers.IO) { controller.toggleLike(post) }
                allowCreateToFinish.countDown()

                firstTap.await()
                secondTap.await()

                assertEquals("3knewlike", deletedRkey.get())
                val finalShadow = shadows.get(post.uri)
                assertNull(finalShadow?.likeUri)
                assertEquals(5, finalShadow?.likeCount)
            } finally {
                db.close()
            }
        }
    }
}
