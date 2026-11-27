package app.openhealth.server

import app.openhealth.core.sync.PairingRequest
import app.openhealth.core.sync.PullRequest
import app.openhealth.core.sync.PushRequest
import app.openhealth.core.sync.ServerHealth
import app.openhealth.core.sync.SyncStatus
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.auth.principal
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ErrorResponse(val error: String)

@Serializable
data class RevokeRequest(val tokenId: String)

/**
 * The HTTP surface (`Tech-Stack.md` §7): health, the passkey pairing page, passkey and
 * optional password sign-in, scoped token management and the two sync verbs. There is no
 * route that reads a person's health data except through the account's own token.
 */
@Suppress("LongMethod", "ThrowsCount", "CyclomaticComplexMethod")
fun Application.openHealthModule(
    config: ServerConfig,
    auth: AuthService,
    changes: ChangeStore,
    media: MediaStore? = null,
) {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                explicitNulls = true
                encodeDefaults = true
            },
        )
    }
    install(CallLogging) { filter { call -> call.request.path().startsWith("/sync") } }
    install(StatusPages) {
        exception<AuthFailure> { call, cause ->
            call.respond(
                HttpStatusCode.Unauthorized,
                ErrorResponse(cause.message ?: "not authorised"),
            )
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: "bad request"))
        }
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("unhandled server error", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("server error"))
        }
    }
    install(Authentication) {
        bearer("session") {
            authenticate { credential ->
                auth.validate(credential.token, SCOPE_SESSION)
            }
        }
        bearer("sync") {
            authenticate { credential ->
                auth.validate(credential.token, SCOPE_SYNC_READ)
            }
        }
    }

    routing {
        get("/health") {
            call.respond(
                ServerHealth(
                    service = "openhealth-sync",
                    schemaVersion = config.maxSchemaVersion,
                    minSupportedSchemaVersion = config.minSchemaVersion,
                ),
            )
        }

        get("/pair") {
            call.respondText(pairingPage(config), ContentType.Text.Html)
        }

        post("/auth/register/options") {
            val body = call.receive<RegisterOptionsBody>()
            val options = auth.registrationOptions(body.username, body.displayName)
                ?: throw AuthFailure("this username cannot register here")
            call.respond(options)
        }
        post("/auth/register/verify") {
            call.respond(auth.verifyRegistration(call.receive<VerifyRegistrationBody>()))
        }
        post("/auth/login/options") {
            val body = call.receive<LoginOptionsBody>()
            val options = auth.loginOptions(body.username) ?: throw AuthFailure("unknown user")
            call.respond(options)
        }
        post("/auth/login/verify") {
            call.respond(auth.verifyLogin(call.receive<VerifyLoginBody>()))
        }
        post("/auth/password/register") {
            call.respond(auth.passwordRegister(call.receive<PasswordBody>()))
        }
        post("/auth/password/login") {
            call.respond(auth.passwordLogin(call.receive<PasswordBody>()))
        }

        post("/pair/complete") {
            val body = call.receive<PairingRequest>()
            call.respond(auth.completePairing(body.code, body.deviceName))
        }

        authenticate("session") {
            post("/pair/code") {
                val account = call.principal<Account>() ?: throw AuthFailure("not signed in")
                call.respond(auth.createPairingCode(account))
            }
            post("/tokens/revoke") {
                val account = call.principal<Account>() ?: throw AuthFailure("not signed in")
                val body = call.receive<RevokeRequest>()
                if (!auth.revoke(account, body.tokenId)) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("no such token"))
                } else {
                    call.respond(mapOf("revoked" to body.tokenId))
                }
            }
        }

        authenticate("sync") {
            post("/sync/push") {
                val account = call.principal<Account>() ?: throw AuthFailure("not signed in")
                val token = call.request.headers[HttpHeaders.Authorization]
                    ?.removePrefix("Bearer ")
                    ?.trim()
                val scopes = token?.let(auth::tokenFor)?.scopes.orEmpty()
                if (SCOPE_SYNC_WRITE !in scopes) {
                    throw AuthFailure("this token may read but not write")
                }
                val body = call.receive<PushRequest>()
                val deviceId = body.deviceId.ifBlank { "unknown" }
                call.respond(changes.push(account.id, deviceId, body.changes, config))
            }
            put("/media/{id}") {
                val account = call.principal<Account>() ?: throw AuthFailure("not signed in")
                val token = call.request.headers[HttpHeaders.Authorization]
                    ?.removePrefix("Bearer ")
                    ?.trim()
                val scopes = token?.let(auth::tokenFor)?.scopes.orEmpty()
                if (SCOPE_SYNC_WRITE !in scopes) {
                    throw AuthFailure("this token may read but not write")
                }
                val store = media
                    ?: return@put call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        ErrorResponse("media storage is not configured"),
                    )
                val id = mediaId(call.parameters["id"])
                val bytes = call.receive<ByteArray>()
                require(bytes.size <= MAX_MEDIA_BYTES) { "media is larger than the limit" }
                store.put(account.id, id, bytes)
                call.respond(HttpStatusCode.NoContent)
            }
            get("/media/{id}") {
                val account = call.principal<Account>() ?: throw AuthFailure("not signed in")
                val store = media
                    ?: return@get call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        ErrorResponse("media storage is not configured"),
                    )
                val bytes = store.get(account.id, mediaId(call.parameters["id"]))
                if (bytes == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("no such media"))
                } else {
                    call.respondBytes(bytes, ContentType.Application.OctetStream)
                }
            }
            post("/sync/pull") {
                val account = call.principal<Account>() ?: throw AuthFailure("not signed in")
                val body = call.receive<PullRequest>()
                call.respond(
                    changes.pull(
                        accountId = account.id,
                        cursor = body.cursor.coerceAtLeast(0),
                        limit = body.limit.coerceIn(1, MAX_PAGE),
                    ),
                )
            }
        }
    }
}

@Serializable
data class RegisterOptionsBody(val username: String, val displayName: String = "")

private const val MAX_PAGE = 500
private const val MAX_MEDIA_BYTES = 25 * 1024 * 1024
private val MEDIA_ID = Regex("[A-Za-z0-9._-]{1,128}")

/** A blob id is a plain token: it becomes part of a storage key and never a path. */
private fun mediaId(raw: String?): String {
    require(raw != null && MEDIA_ID.matches(raw) && raw != "." && raw != "..") {
        "bad media id"
    }
    return raw
}

/** Kept out of the route body so the log never sees a token or a health value. */
fun SyncStatus.label(): String = when (this) {
    SyncStatus.LOCAL_ONLY -> "local only"
    SyncStatus.SYNCING -> "syncing"
    SyncStatus.SYNCED -> "synced"
    SyncStatus.OFFLINE -> "offline"
    SyncStatus.ERROR -> "error"
}
