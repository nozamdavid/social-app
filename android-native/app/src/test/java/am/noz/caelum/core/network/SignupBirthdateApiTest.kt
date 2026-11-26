package am.noz.caelum.core.network

import am.noz.caelum.core.model.AtProtoJson
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class SignupBirthdateApiTest {
    @Test
    fun `birthdate writes target the new account PDS and preserve other preferences`() = runTest {
        val delegate = object : XrpcSessionDelegate {
            override fun currentAuth() = ActiveXrpcAuth(
                "did:plc:existing", "https://example.invalid", "existing-access", "existing-refresh",
            )
        }
        MockWebServer().use { pds ->
            val existingPrefs = """{"preferences":[{"${'$'}type":"app.bsky.actor.defs#unknownPref","value":true}]}"""
            val existingDetails = """{"preferences":[{"${'$'}type":"app.bsky.actor.defs#personalDetailsPref","birthDate":"2000-01-01T00:00:00Z","extra":"preserve"}]}"""
            val api = AtprotoApi(XrpcClient(sessionDelegate = delegate))
            for (preferences in listOf(existingPrefs, existingDetails)) {
                pds.enqueue(MockResponse().setBody(preferences))
                pds.enqueue(MockResponse().setBody("{}"))

                api.setSignupBirthdate(pds.url("").toString(), "new-access", LocalDate.of(2000, 2, 29))

                val get = pds.takeRequest()
                assertEquals("/xrpc/app.bsky.actor.getPreferences", get.path)
                assertEquals("Bearer new-access", get.getHeader("Authorization"))
                val put = pds.takeRequest()
                assertEquals("/xrpc/app.bsky.actor.putPreferences", put.path)
                assertEquals("Bearer new-access", put.getHeader("Authorization"))
                assertNull(put.getHeader("atproto-proxy"))
                val expected = if (preferences == existingPrefs) {
                    """{"preferences":[{"${'$'}type":"app.bsky.actor.defs#unknownPref","value":true},{"${'$'}type":"app.bsky.actor.defs#personalDetailsPref","birthDate":"2000-02-29T00:00:00Z"}]}"""
                } else {
                    """{"preferences":[{"${'$'}type":"app.bsky.actor.defs#personalDetailsPref","birthDate":"2000-02-29T00:00:00Z","extra":"preserve"}]}"""
                }
                assertEquals(
                    AtProtoJson.parseToJsonElement(expected),
                    AtProtoJson.parseToJsonElement(put.body.readUtf8()),
                )
            }
        }
    }
}
