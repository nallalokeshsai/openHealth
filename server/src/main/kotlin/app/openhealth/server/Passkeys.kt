package app.openhealth.server

import app.openhealth.core.sync.PairingResponse
import at.favre.lib.crypto.bcrypt.BCrypt
import com.webauthn4j.WebAuthnManager
import com.webauthn4j.converter.AttestationObjectConverter
import com.webauthn4j.converter.util.ObjectConverter
import com.webauthn4j.credential.CredentialRecordImpl
import com.webauthn4j.data.AuthenticationParameters
import com.webauthn4j.data.AuthenticatorTransport
import com.webauthn4j.data.RegistrationParameters
import com.webauthn4j.data.client.CollectedClientData
import com.webauthn4j.data.client.Origin
import com.webauthn4j.data.client.challenge.DefaultChallenge
import com.webauthn4j.server.ServerProperty
import java.security.SecureRandom
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

@Serializable
data class RegistrationOptions(val challengeId: String, val options: JsonObject)

@Serializable
data class AuthenticationOptions(val challengeId: String, val options: JsonObject)

@Serializable
data class AuthResult(
    val token: String,
    val accountId: String,
    val scopes: List<String>,
    val expiresAt: Long?,
)

@Serializable
data class PairingCodeResponse(val code: String, val expiresAt: Long, val domain: String)

@Serializable
data class VerifyRegistrationBody(
    val username: String,
    val challengeId: String,
    val response: String,
)

@Serializable
data class LoginOptionsBody(val username: String)

@Serializable
data class VerifyLoginBody(val username: String, val challengeId: String, val response: String)

@Serializable
data class PasswordBody(val username: String, val password: String)

private data class ChallengeEntry(
    val challenge: ByteArray,
    val kind: Kind,
    val username: String,
    val expiresAt: Long,
) {
    enum class Kind { REGISTER, LOGIN }
}

/**
 * Passkey sign-in and the pairing exchange (`Tech-Stack.md` §7; owner's answer: browser
 * pairing page and a one-time code). Passkeys are the primary factor; a password is a
 * fallback only when the operator enables it. Tokens are opaque, hashed at rest, scoped
 * and revocable.
 */
/**
 * A security adapter: guard clauses and a third-party verifier's exceptions are the
 * shape of the job, so the generic copies of those rules are scoped off here.
 */
@Suppress(
    "TooManyFunctions",
    "MagicNumber",
    "ReturnCount",
    "ThrowsCount",
    "TooGenericExceptionCaught",
    "SwallowedException",
)
class AuthService(
    private val config: ServerConfig,
    private val store: AuthStore,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private val manager = WebAuthnManager.createNonStrictWebAuthnManager()
    private val random = SecureRandom()
    private val challenges = linkedMapOf<String, ChallengeEntry>()

    // ------------------------------------------------------------- registration

    fun registrationOptions(username: String, displayName: String): RegistrationOptions? {
        if (!mayRegister(username)) return null
        if (store.accountByUsername(username) != null) return null
        val challengeId = Tokens.newId()
        val challenge = ByteArray(32).also(random::nextBytes)
        challenges[challengeId] = ChallengeEntry(
            challenge = challenge,
            kind = ChallengeEntry.Kind.REGISTER,
            username = username,
            expiresAt = now() + CHALLENGE_TTL_MILLIS,
        )
        val userHandle = Tokens.base64Url(username.toByteArray())
        val options = buildJsonObject {
            putJsonObject("rp") {
                put("id", config.rpId)
                put("name", "openHealth")
            }
            putJsonObject("user") {
                put("id", userHandle)
                put("name", username)
                put("displayName", displayName)
            }
            put("challenge", Tokens.base64Url(challenge))
            putJsonArray("pubKeyCredParams") {
                add(credentialParameter(-7))
                add(credentialParameter(-257))
            }
            put("timeout", 120_000)
            put("attestation", "none")
            putJsonObject("authenticatorSelection") {
                put("residentKey", "preferred")
                put("userVerification", "required")
            }
        }
        return RegistrationOptions(challengeId, options)
    }

    fun verifyRegistration(body: VerifyRegistrationBody): AuthResult {
        val entry = takeChallenge(body.challengeId, ChallengeEntry.Kind.REGISTER, body.username)
            ?: throw AuthFailure("challenge expired or unknown")
        val serverProperty = serverProperty(entry.challenge)
        val parameters = RegistrationParameters(serverProperty, true, true)
        val data = try {
            manager.verifyRegistrationResponseJSON(body.response, parameters)
        } catch (failure: Exception) {
            throw AuthFailure("passkey registration did not verify: ${failure.message}")
        }
        val attestation = data.attestationObject
            ?: throw AuthFailure("registration carried no attestation")
        val attested = attestation.authenticatorData.attestedCredentialData
            ?: throw AuthFailure("registration carried no credential")
        val credentialId = Tokens.base64Url(attested.credentialId)
        val account = store.accountByUsername(body.username) ?: store.createAccount(
            Account(
                id = UUID.randomUUID().toString(),
                username = body.username,
                displayName = body.username,
                createdAt = now(),
            ),
        )
        store.saveCredential(
            StoredCredential(
                credentialId = credentialId,
                accountId = account.id,
                attestationObject = data.attestationObjectBytes,
                clientData = data.collectedClientDataBytes,
                transports = data.transports.orEmpty().mapNotNull { it?.value }.toSet(),
                signCount = attestation.authenticatorData.signCount,
                createdAt = now(),
            ),
        )
        return issueSession(account)
    }

    // ------------------------------------------------------------------- login

    fun loginOptions(username: String): AuthenticationOptions? {
        val account = store.accountByUsername(username) ?: return null
        val credentials = store.credentialsFor(account.id)
        if (credentials.isEmpty()) return null
        val challengeId = Tokens.newId()
        val challenge = ByteArray(32).also(random::nextBytes)
        challenges[challengeId] = ChallengeEntry(
            challenge = challenge,
            kind = ChallengeEntry.Kind.LOGIN,
            username = username,
            expiresAt = now() + CHALLENGE_TTL_MILLIS,
        )
        val options = buildJsonObject {
            put("challenge", Tokens.base64Url(challenge))
            put("rpId", config.rpId)
            put("timeout", 120_000)
            put("userVerification", "required")
            putJsonArray("allowCredentials") {
                credentials.forEach { credential ->
                    add(
                        buildJsonObject {
                            put("type", "public-key")
                            put("id", credential.credentialId)
                        },
                    )
                }
            }
        }
        return AuthenticationOptions(challengeId, options)
    }

    fun verifyLogin(body: VerifyLoginBody): AuthResult {
        val entry = takeChallenge(body.challengeId, ChallengeEntry.Kind.LOGIN, body.username)
            ?: throw AuthFailure("challenge expired or unknown")
        val account = store.accountByUsername(body.username)
            ?: throw AuthFailure("unknown account")
        val parsed = try {
            manager.parseAuthenticationResponseJSON(body.response)
        } catch (failure: Exception) {
            throw AuthFailure("passkey assertion could not be read: ${failure.message}")
        }
        val credentialId = Tokens.base64Url(parsed.credentialId)
        val stored = store.credentialById(credentialId)
            ?: throw AuthFailure("credential is not registered")
        if (stored.accountId != account.id) throw AuthFailure("credential belongs elsewhere")
        val record = credentialRecord(stored)
        val parameters = AuthenticationParameters(
            serverProperty(entry.challenge),
            record,
            null,
            true,
            true,
        )
        val verified = try {
            manager.verify(parsed, parameters)
        } catch (failure: Exception) {
            throw AuthFailure("passkey assertion did not verify: ${failure.message}")
        }
        store.updateSignCount(
            credentialId,
            verified.authenticatorData?.signCount ?: 0L,
            now(),
        )
        return issueSession(account)
    }

    // -------------------------------------------------------------- password fallback

    fun passwordRegister(body: PasswordBody): AuthResult {
        if (!config.allowPassword) throw AuthFailure("password sign-in is disabled")
        if (!mayRegister(body.username)) throw AuthFailure("this server does not allow that user")
        if (body.password.length < 12) throw AuthFailure("password must be at least 12 characters")
        if (store.accountByUsername(body.username) != null) {
            throw AuthFailure("username taken")
        }
        val account = store.createAccount(
            Account(
                id = UUID.randomUUID().toString(),
                username = body.username,
                displayName = body.username,
                passwordHash = BCrypt.withDefaults().hashToString(12, body.password.toCharArray()),
                createdAt = now(),
            ),
        )
        return issueSession(account)
    }

    fun passwordLogin(body: PasswordBody): AuthResult {
        if (!config.allowPassword) throw AuthFailure("password sign-in is disabled")
        val account =
            store.accountByUsername(body.username) ?: throw AuthFailure("wrong user or password")
        val hash = account.passwordHash ?: throw AuthFailure("this account has no password")
        if (!BCrypt.verifyer().verify(body.password.toCharArray(), hash).verified) {
            throw AuthFailure("wrong user or password")
        }
        return issueSession(account)
    }

    // ------------------------------------------------------------------ pairing

    fun createPairingCode(account: Account): PairingCodeResponse {
        val code = Tokens.randomCode()
        val expiresAt = now() + PAIRING_TTL_MILLIS
        store.createPairingCode(
            PairingCode(
                codeHash = Tokens.hash(code),
                accountId = account.id,
                createdAt = now(),
                expiresAt = expiresAt,
            ),
        )
        return PairingCodeResponse(code = code, expiresAt = expiresAt, domain = config.domain)
    }

    fun completePairing(code: String, deviceName: String): PairingResponse {
        val hash = Tokens.hash(code.trim().uppercase())
        val pairing =
            store.pairingCodeByHash(hash) ?: throw AuthFailure("pairing code is not valid")
        if (!store.usePairingCode(hash, now())) {
            throw AuthFailure("pairing code is used or expired")
        }
        val account = store.accountById(pairing.accountId) ?: throw AuthFailure("account is gone")
        val deviceId = UUID.randomUUID().toString()
        store.createDevice(
            StoredDevice(
                id = deviceId,
                accountId = account.id,
                name = deviceName.ifBlank { "Android device" },
                createdAt = now(),
            ),
        )
        val token = Tokens.newToken()
        store.createToken(
            StoredToken(
                id = Tokens.newId(),
                accountId = account.id,
                tokenHash = Tokens.hash(token),
                scopes = SCOPE_SYNC,
                label = deviceName.ifBlank { "Android device" },
                deviceId = deviceId,
                createdAt = now(),
                expiresAt = null,
            ),
        )
        return PairingResponse(token = token, deviceId = deviceId, scopes = SCOPE_SYNC.toList())
    }

    // ------------------------------------------------------------------ tokens

    fun validate(token: String, scope: String): Account? {
        val row = store.tokenByHash(Tokens.hash(token)) ?: return null
        if (row.revokedAt != null) return null
        val expiry = row.expiresAt
        if (expiry != null && expiry < now()) return null
        if (scope !in row.scopes) return null
        return store.accountById(row.accountId)
    }

    fun tokenFor(token: String): StoredToken? = store.tokenByHash(Tokens.hash(token))

    fun revoke(account: Account, tokenId: String): Boolean {
        val token = store.tokensFor(account.id).firstOrNull { it.id == tokenId } ?: return false
        return store.revokeToken(token.id, now())
    }

    fun tokens(account: Account): List<StoredToken> = store.tokensFor(account.id)

    // ------------------------------------------------------------------ helpers

    private fun mayRegister(username: String): Boolean =
        username.isNotBlank() && config.allowedUsers.any { it.equals(username, ignoreCase = true) }

    private fun issueSession(account: Account): AuthResult {
        val token = Tokens.newToken()
        val expiresAt = now() + SESSION_TTL_MILLIS
        store.createToken(
            StoredToken(
                id = Tokens.newId(),
                accountId = account.id,
                tokenHash = Tokens.hash(token),
                scopes = setOf(SCOPE_SESSION),
                label = "browser session",
                deviceId = null,
                createdAt = now(),
                expiresAt = expiresAt,
            ),
        )
        return AuthResult(
            token = token,
            accountId = account.id,
            scopes = listOf(SCOPE_SESSION),
            expiresAt = expiresAt,
        )
    }

    private fun takeChallenge(
        id: String,
        kind: ChallengeEntry.Kind,
        username: String,
    ): ChallengeEntry? {
        val entry = challenges.remove(id) ?: return null
        if (entry.kind != kind) return null
        if (entry.expiresAt < now()) return null
        if (!entry.username.equals(username, ignoreCase = true)) return null
        return entry
    }

    private fun serverProperty(challenge: ByteArray): ServerProperty = ServerProperty.builder()
        .origin(Origin.create(config.origin))
        .rpId(config.rpId)
        .challenge(DefaultChallenge(challenge))
        .build()

    private fun credentialRecord(stored: StoredCredential): CredentialRecordImpl {
        val converter = ObjectConverter()
        val attestation = AttestationObjectConverter(converter).convert(stored.attestationObject)
            ?: throw AuthFailure("stored attestation could not be read")
        val clientData: CollectedClientData = converter.jsonConverter.readValue(
            stored.clientData.toString(Charsets.UTF_8),
            CollectedClientData::class.java,
        ) ?: throw AuthFailure("stored client data could not be read")
        val authData = attestation.authenticatorData
            ?: throw AuthFailure("stored attestation has no authenticator data")
        return CredentialRecordImpl(
            attestation.attestationStatement,
            authData.isFlagUP,
            authData.isFlagUV,
            authData.isFlagBE,
            stored.signCount,
            authData.attestedCredentialData
                ?: throw AuthFailure("stored attestation has no credential data"),
            authData.extensions,
            clientData,
            null,
            stored.transports.mapNotNull { transport ->
                AuthenticatorTransport.create(transport)
            }.toSet(),
        )
    }

    private fun credentialParameter(algorithm: Int): JsonObject = buildJsonObject {
        put("type", "public-key")
        put("alg", algorithm)
    }

    companion object {
        const val CHALLENGE_TTL_MILLIS = 5 * 60 * 1000L
        const val PAIRING_TTL_MILLIS = 10 * 60 * 1000L
        const val SESSION_TTL_MILLIS = 60 * 60 * 1000L
    }
}

/** A request the server refuses with a 4xx and a message the browser can show. */
class AuthFailure(message: String) : RuntimeException(message)
