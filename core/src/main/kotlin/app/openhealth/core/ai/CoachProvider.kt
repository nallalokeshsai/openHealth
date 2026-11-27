package app.openhealth.core.ai

import app.openhealth.core.seam.HttpRequest
import app.openhealth.core.seam.HttpResponse
import app.openhealth.core.seam.HttpTransport
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Which provider a person configured (`pr.md` §6.15: bring-your-own-key or self-hosted). */
enum class CoachProviderKind(val key: String) {
    ANTHROPIC("anthropic"),
    SELF_HOSTED("self_hosted"),
    ;

    companion object {
        fun fromKey(key: String?): CoachProviderKind? = entries.firstOrNull { it.key == key }
    }
}

/**
 * A provider setting. The key is deliberately absent: it lives in the platform vault and
 * is passed to the adapter at send time (`AGENTS.md`, B15 key handling).
 */
data class CoachProviderConfig(
    val kind: CoachProviderKind = CoachProviderKind.ANTHROPIC,
    val model: String = ANTHROPIC_DEEP_MODEL,
    /** Only for [CoachProviderKind.SELF_HOSTED]; an OpenAI-compatible base URL. */
    val baseUrl: String = "",
) {
    fun isUsable(): Boolean = when (kind) {
        CoachProviderKind.ANTHROPIC -> model.isNotBlank()
        CoachProviderKind.SELF_HOSTED ->
            baseUrl.startsWith("http://") || baseUrl.startsWith("https://")
    }

    companion object {
        /** `Tech-Stack.md` §9 names these two models. */
        const val ANTHROPIC_DEEP_MODEL = "claude-opus-5-5"
        const val ANTHROPIC_QUICK_MODEL = "claude-sonnet-5-5"
        const val ANTHROPIC_VERSION = "2023-06-01"
        const val ANTHROPIC_BASE_URL = "https://api.anthropic.com"
        const val SELF_HOSTED_EXAMPLE = "http://127.0.0.1:1234/v1"
        const val MAX_TOKENS = 2_048

        /** A model call is slower than a barcode lookup; the transport honours this. */
        const val TIMEOUT_MILLIS = 60_000
    }
}

/**
 * Anthropic's Messages API. The request carries the system prompt, the consented data
 * and the note; the key travels in a header only and is never logged or stored by this
 * class. A non-2xx answer becomes a typed failure with no body echo.
 */
class AnthropicCoachProvider(
    private val transport: HttpTransport,
    private val apiKey: String,
    private val model: String,
    private val baseUrl: String = CoachProviderConfig.ANTHROPIC_BASE_URL,
) : CoachProvider {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun complete(request: CoachProviderRequest): CoachProviderResult {
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", CoachProviderConfig.MAX_TOKENS)
            put("system", request.system)
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("content", request.user)
                        },
                    )
                },
            )
        }
        val response = call(
            HttpRequest(
                url = "${baseUrl.trimEnd('/')}/v1/messages",
                method = "POST",
                headers = mapOf(
                    "x-api-key" to apiKey,
                    "anthropic-version" to CoachProviderConfig.ANTHROPIC_VERSION,
                ),
                contentType = "application/json",
                body = json.encodeToString(JsonObject.serializer(), body),
                timeoutMillis = CoachProviderConfig.TIMEOUT_MILLIS,
            ),
        ) ?: return CoachProviderResult.Failed(CoachFailure(CoachFailureKind.OFFLINE))
        val failure = failureOf(response)
        if (failure != null) return CoachProviderResult.Failed(failure)
        val root = runCatching { json.parseToJsonElement(response.body).jsonObject }.getOrNull()
            ?: return CoachProviderResult.Failed(
                CoachFailure(CoachFailureKind.INVALID_RESPONSE, "body"),
            )
        val text = root["content"]?.jsonArray?.mapNotNull { block ->
            val objectValue = block as? JsonObject ?: return@mapNotNull null
            if (objectValue["type"]?.jsonPrimitive?.contentOrNull != "text") return@mapNotNull null
            objectValue["text"]?.jsonPrimitive?.contentOrNull
        }?.joinToString("").orEmpty()
        if (text.isBlank()) {
            return CoachProviderResult.Failed(
                CoachFailure(CoachFailureKind.INVALID_RESPONSE, "empty"),
            )
        }
        val usage = root["usage"] as? JsonObject
        return CoachProviderResult.Text(
            text = text,
            inputTokens = (usage?.get("input_tokens") as? JsonPrimitive)?.intOrNull,
            outputTokens = (usage?.get("output_tokens") as? JsonPrimitive)?.intOrNull,
        )
    }

    private suspend fun call(request: HttpRequest): HttpResponse? = runCatching {
        transport.post(request)
    }.getOrNull()
}

/**
 * Any OpenAI-compatible endpoint: LM Studio, Ollama, vLLM, llama.cpp, a self-hosted
 * gateway. A local host usually needs no key, so the Authorization header is omitted
 * when the key is blank.
 */
class SelfHostedCoachProvider(
    private val transport: HttpTransport,
    private val baseUrl: String,
    private val model: String,
    private val apiKey: String,
) : CoachProvider {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun complete(request: CoachProviderRequest): CoachProviderResult {
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            return CoachProviderResult.Failed(
                CoachFailure(CoachFailureKind.DISABLED, "url"),
            )
        }
        val body = buildJsonObject {
            put("model", model)
            put("temperature", 0)
            put(
                "messages",
                buildJsonArray {
                    add(buildJsonObject { put("role", "system"); put("content", request.system) })
                    add(buildJsonObject { put("role", "user"); put("content", request.user) })
                },
            )
        }
        val headers = buildMap {
            put("Accept", "application/json")
            if (apiKey.isNotBlank()) put("Authorization", "Bearer $apiKey")
        }
        val response = runCatching {
            transport.post(
                HttpRequest(
                    url = chatUrl(baseUrl),
                    method = "POST",
                    headers = headers,
                    contentType = "application/json",
                    body = json.encodeToString(JsonObject.serializer(), body),
                    timeoutMillis = CoachProviderConfig.TIMEOUT_MILLIS,
                ),
            )
        }.getOrNull() ?: return CoachProviderResult.Failed(CoachFailure(CoachFailureKind.OFFLINE))
        val failure = failureOf(response)
        if (failure != null) return CoachProviderResult.Failed(failure)
        val root = runCatching { json.parseToJsonElement(response.body).jsonObject }.getOrNull()
            ?: return CoachProviderResult.Failed(
                CoachFailure(CoachFailureKind.INVALID_RESPONSE, "body"),
            )
        val text = root["choices"]?.jsonArray?.firstOrNull()
            ?.let { it as? JsonObject }
            ?.get("message")?.let { it as? JsonObject }
            ?.get("content")?.jsonPrimitive?.contentOrNull
            .orEmpty()
        if (text.isBlank()) {
            return CoachProviderResult.Failed(
                CoachFailure(CoachFailureKind.INVALID_RESPONSE, "empty"),
            )
        }
        return CoachProviderResult.Text(text)
    }

    private fun chatUrl(base: String): String {
        val trimmed = base.trimEnd('/')
        return when {
            trimmed.endsWith("/chat/completions") -> trimmed
            trimmed.endsWith("/v1") -> "$trimmed/chat/completions"
            else -> "$trimmed/v1/chat/completions"
        }
    }
}

/** One place that turns an HTTP status into a typed failure, shared by both adapters. */
internal fun failureOf(response: HttpResponse): CoachFailure? = when {
    response.status in 200..299 -> null
    response.status == 401 || response.status == 403 ->
        CoachFailure(CoachFailureKind.UNAUTHORIZED, "HTTP ${response.status}")

    response.status == 429 ->
        CoachFailure(CoachFailureKind.RATE_LIMITED, "HTTP 429")

    response.status in 500..599 ->
        CoachFailure(CoachFailureKind.SERVER, "HTTP ${response.status}")

    else -> CoachFailure(CoachFailureKind.SERVER, "HTTP ${response.status}")
}
