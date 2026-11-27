package app.openhealth.core.ai

import app.openhealth.core.seam.HttpRequest
import app.openhealth.core.seam.HttpResponse
import app.openhealth.core.seam.HttpTransport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Both adapters, with a recording transport. The key is asserted on the wire and
 * asserted absent from anything the app would show.
 */
class CoachProviderTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val request = CoachProviderRequest("system prompt", "user data")

    private class RecordingTransport(
        private val response: () -> HttpResponse,
    ) : HttpTransport {
        val requests = mutableListOf<HttpRequest>()

        override suspend fun get(request: HttpRequest): HttpResponse = response()

        override suspend fun post(request: HttpRequest): HttpResponse {
            requests += request
            return response()
        }
    }

    @Test
    fun anthropicUsesTheMessagesShapeAndTheKeyOnlyInAHeader() = runTest {
        val transport = RecordingTransport {
            HttpResponse(
                200,
                """{"content":[{"type":"text","text":"{\"note\":\"ok\",\"proposals\":[]}"}],
                    "usage":{"input_tokens":11,"output_tokens":3}}""",
            )
        }
        val provider = AnthropicCoachProvider(transport, "sk-test-key", "claude-opus-5-5")
        val result = provider.complete(request)
        assertTrue(result is CoachProviderResult.Text)
        assertEquals(11, (result as CoachProviderResult.Text).inputTokens)
        val sent = transport.requests.single()
        assertEquals("POST", sent.method)
        assertEquals("https://api.anthropic.com/v1/messages", sent.url)
        assertEquals("sk-test-key", sent.headers["x-api-key"])
        assertEquals("2023-06-01", sent.headers["anthropic-version"])
        assertEquals(CoachProviderConfig.TIMEOUT_MILLIS, sent.timeoutMillis)
        val body = json.parseToJsonElement(sent.body!!).jsonObject
        assertEquals("claude-opus-5-5", body.getValue("model").jsonPrimitive.content)
        assertEquals("system prompt", body.getValue("system").jsonPrimitive.content)
        assertFalse(sent.body!!.contains("sk-test-key"))
    }

    @Test
    fun anUnauthorizedAnswerIsTypedAndDoesNotEchoTheKey() = runTest {
        val transport = RecordingTransport { HttpResponse(401, "bad key sk-test-key") }
        val provider = AnthropicCoachProvider(transport, "sk-test-key", "claude-opus-5-5")
        val result = provider.complete(request)
        assertTrue(result is CoachProviderResult.Failed)
        val failure = (result as CoachProviderResult.Failed).failure
        assertEquals(CoachFailureKind.UNAUTHORIZED, failure.kind)
        assertFalse(failure.detail.orEmpty().contains("sk-test-key"))
    }

    @Test
    fun aRateLimitAndAServerErrorAreDistinct() = runTest {
        val limited = AnthropicCoachProvider(
            RecordingTransport { HttpResponse(429, "") },
            "k",
            "m",
        ).complete(request)
        assertEquals(
            CoachFailureKind.RATE_LIMITED,
            (limited as CoachProviderResult.Failed).failure.kind,
        )
        val server = AnthropicCoachProvider(
            RecordingTransport { HttpResponse(503, "") },
            "k",
            "m",
        ).complete(request)
        assertEquals(
            CoachFailureKind.SERVER,
            (server as CoachProviderResult.Failed).failure.kind,
        )
    }

    @Test
    fun aTransportFailureIsOfflineNotACrash() = runTest {
        val transport = RecordingTransport { throw IllegalStateException("no route") }
        val result = AnthropicCoachProvider(transport, "k", "m").complete(request)
        assertTrue(result is CoachProviderResult.Failed)
        assertEquals(CoachFailureKind.OFFLINE, (result as CoachProviderResult.Failed).failure.kind)
    }

    @Test
    fun aSelfHostedBaseUrlGainsTheChatPath() = runTest {
        val transport = RecordingTransport {
            HttpResponse(200, """{"choices":[{"message":{"content":"{}"}}]}""")
        }
        val provider = SelfHostedCoachProvider(transport, "http://127.0.0.1:1234/v1", "local", "")
        provider.complete(request)
        assertEquals(
            "http://127.0.0.1:1234/v1/chat/completions",
            transport.requests.single().url,
        )
        assertFalse(transport.requests.single().headers.containsKey("Authorization"))

        val plain = RecordingTransport {
            HttpResponse(200, """{"choices":[{"message":{"content":"{}"}}]}""")
        }
        SelfHostedCoachProvider(plain, "http://box:8080", "local", "").complete(request)
        assertEquals("http://box:8080/v1/chat/completions", plain.requests.single().url)
    }

    @Test
    fun aSelfHostedKeyBecomesABearerHeader() = runTest {
        val transport = RecordingTransport {
            HttpResponse(200, """{"choices":[{"message":{"content":"{}"}}]}""")
        }
        SelfHostedCoachProvider(transport, "https://box/v1", "local", "secret")
            .complete(request)
        assertEquals("Bearer secret", transport.requests.single().headers["Authorization"])
    }

    @Test
    fun aBaseUrlThatIsNotHttpIsRefusedBeforeAnyCall() = runTest {
        val transport = RecordingTransport { HttpResponse(200, "{}") }
        val result = SelfHostedCoachProvider(transport, "ftp://box/v1", "local", "")
            .complete(request)
        assertTrue(result is CoachProviderResult.Failed)
        assertTrue(transport.requests.isEmpty())
    }
}
