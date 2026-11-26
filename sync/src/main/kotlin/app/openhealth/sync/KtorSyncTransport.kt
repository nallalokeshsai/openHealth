package app.openhealth.sync

import app.openhealth.core.sync.PairingRequest
import app.openhealth.core.sync.PairingResponse
import app.openhealth.core.sync.PullRequest
import app.openhealth.core.sync.PullResponse
import app.openhealth.core.sync.PushRequest
import app.openhealth.core.sync.PushResponse
import app.openhealth.core.sync.ServerHealth
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * The Ktor client transport (`Tech-Stack.md` §6): JSON, gzip, HTTPS.
 *
 * The base URL is validated before a client is built. plain http is refused except for
 * loopback and the Android emulator's host alias, which is what a self-hoster running
 * `docker compose up` on the same machine uses before a certificate exists. Everything
 * else must be HTTPS, because a health record is not sent in clear text.
 */
class KtorSyncTransport(
    baseUrl: String,
    private val token: String,
    private val client: HttpClient = defaultClient(),
    allowInsecureLocal: Boolean = true,
) : SyncTransport {
    private val base = requireSecureBase(baseUrl, allowInsecureLocal)

    override suspend fun push(request: PushRequest): PushResponse = client.post("$base/sync/push") {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(request)
    }.body()

    override suspend fun pull(request: PullRequest): PullResponse = client.post("$base/sync/pull") {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(request)
    }.body()

    /** Release the CIO pool when the pairing goes away. */
    fun close() {
        client.close()
    }

    suspend fun health(): ServerHealth = client.get("$base/health").body()

    suspend fun pair(request: PairingRequest): PairingResponse =
        client.post("$base/pair/complete") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    companion object {
        private const val CONNECT_TIMEOUT_MILLIS = 10_000L
        private const val REQUEST_TIMEOUT_MILLIS = 30_000L

        /** The JSON contract: unknown keys are ignored so a newer server can add fields. */
        val json: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = true
        }

        fun defaultClient(): HttpClient = HttpClient(CIO) {
            expectSuccess = true
            install(ContentNegotiation) { json(json) }
            install(ContentEncoding) { gzip() }
            install(HttpTimeout) {
                connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
                requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
                socketTimeoutMillis = REQUEST_TIMEOUT_MILLIS
            }
        }

        fun requireSecureBase(baseUrl: String, allowInsecureLocal: Boolean): String {
            val trimmed = baseUrl.trim().trimEnd('/')
            require(trimmed.isNotEmpty()) { "server URL is empty" }
            val scheme = trimmed.substringBefore("://", "").lowercase()
            val insecure = scheme == "http"
            require(insecure || scheme == "https") {
                "server URL must start with https"
            }
            require(!insecure || allowInsecureLocal) {
                "the sync server must use https (plain http is only allowed for a local server)"
            }
            if (insecure) {
                val host = trimmed.substringAfter("://")
                    .substringBefore('/')
                    .substringBefore(':')
                    .lowercase()
                require(host == "localhost" || host == "127.0.0.1" || host == "10.0.2.2") {
                    "plain http is only allowed for loopback or the emulator host, not $host"
                }
            }
            return trimmed
        }
    }
}
