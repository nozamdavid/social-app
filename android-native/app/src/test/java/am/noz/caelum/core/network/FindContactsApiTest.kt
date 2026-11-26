package am.noz.caelum.core.network

import am.noz.caelum.core.model.AtProtoJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class FindContactsApiTest {
    @Test
    fun `verification and import send only their declared fields to the Bluesky AppView`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{}"))
            server.enqueue(MockResponse().setBody("""{"token":"single-use"}"""))
            server.enqueue(MockResponse().setBody(
                """{"matchesAndContactIndexes":[{"match":{"did":"did:plc:friend","handle":"friend.bsky.social"},"contactIndex":0}]}""",
            ))
            val session = TestSession(ActiveXrpcAuth(
                did = "did:plc:me",
                pdsUrl = server.url("/").toString(),
                accessJwt = "access",
                refreshJwt = "refresh",
            ))
            val api = FindContactsApi(XrpcClient(sessionDelegate = session), BoundXrpcSession {
                session.currentAuth().takeIf { it.did == "did:plc:me" }
            })
            api.startPhoneVerification("+12025550100")
            val token = api.verifyPhone("+12025550100", "123456")
            val matches = api.importContacts(token, listOf("+12025550101"))

            assertEquals(listOf("did:plc:friend"), matches.map { it.did })
            fun assertRequest(method: String, body: String) {
                val request = server.takeRequest()
                assertEquals("POST", request.method)
                assertEquals("/xrpc/app.bsky.contact.$method", request.path)
                assertEquals("Bearer access", request.getHeader("Authorization"))
                assertEquals("did:web:api.bsky.app#bsky_appview", request.getHeader("atproto-proxy"))
                assertEquals(
                    AtProtoJson.parseToJsonElement(body).jsonObject,
                    AtProtoJson.parseToJsonElement(request.body.readUtf8()).jsonObject,
                )
            }
            assertRequest("startPhoneVerification", """{"phone":"+12025550100"}""")
            assertRequest("verifyPhone", """{"phone":"+12025550100","code":"123456"}""")
            assertRequest("importContacts", """{"token":"single-use","contacts":["+12025550101"]}""")
        }
    }

    @Test
    fun `stale contacts flow does not send requests with another account`() = runTest {
        MockWebServer().use { server ->
            val session = TestSession(ActiveXrpcAuth(
                did = "did:plc:other",
                pdsUrl = server.url("/").toString(),
                accessJwt = "other-access",
                refreshJwt = "other-refresh",
            ))
            val api = FindContactsApi(XrpcClient(sessionDelegate = session), BoundXrpcSession {
                session.currentAuth().takeIf { it.did == "did:plc:me" }
            })

            val error = runCatching { api.importContacts("single-use", listOf("+12025550101")) }.exceptionOrNull()

            assertTrue(error is IOException)
            assertEquals(0, server.requestCount)
        }
    }

    private class TestSession(private val auth: ActiveXrpcAuth) : XrpcSessionDelegate {
        override fun currentAuth(): ActiveXrpcAuth = auth
    }
}
