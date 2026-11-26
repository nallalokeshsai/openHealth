package app.openhealth.server

import app.openhealth.core.sync.ChangeRecord
import app.openhealth.core.sync.PullResponse
import app.openhealth.core.sync.PushResponse
import app.openhealth.core.sync.RejectedChange
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

/** One account. [passwordHash] is null unless the optional fallback is enabled. */
data class Account(
    val id: String,
    val username: String,
    val displayName: String?,
    val passwordHash: String? = null,
    val createdAt: Long,
)

/** A registered passkey. The attestation is kept so an assertion can be verified later. */
data class StoredCredential(
    val credentialId: String,
    val accountId: String,
    val attestationObject: ByteArray,
    val clientData: ByteArray,
    val transports: Set<String>,
    val signCount: Long,
    val createdAt: Long,
) {
    override fun equals(other: Any?): Boolean =
        other is StoredCredential && other.credentialId == credentialId

    override fun hashCode(): Int = credentialId.hashCode()
}

/** An opaque bearer token, stored only as a SHA-256 hash. */
data class StoredToken(
    val id: String,
    val accountId: String,
    val tokenHash: String,
    val scopes: Set<String>,
    val label: String?,
    val deviceId: String?,
    val createdAt: Long,
    val expiresAt: Long?,
    val revokedAt: Long? = null,
)

data class PairingCode(
    val codeHash: String,
    val accountId: String,
    val createdAt: Long,
    val expiresAt: Long,
    val usedAt: Long? = null,
)

data class StoredDevice(
    val id: String,
    val accountId: String,
    val name: String,
    val createdAt: Long,
    val lastSeenAt: Long? = null,
)

@Suppress("TooManyFunctions")
interface AuthStore {
    fun accountByUsername(username: String): Account?

    fun accountById(id: String): Account?

    fun createAccount(account: Account): Account

    fun setPassword(accountId: String, passwordHash: String)

    fun credentialsFor(accountId: String): List<StoredCredential>

    fun credentialById(credentialId: String): StoredCredential?

    fun saveCredential(credential: StoredCredential)

    fun updateSignCount(credentialId: String, signCount: Long, at: Long)

    fun createToken(token: StoredToken): StoredToken

    fun tokenByHash(hash: String): StoredToken?

    fun revokeToken(id: String, at: Long): Boolean

    fun createPairingCode(code: PairingCode)

    fun pairingCodeByHash(hash: String): PairingCode?

    fun usePairingCode(codeHash: String, at: Long): Boolean

    fun createDevice(device: StoredDevice)

    fun tokensFor(accountId: String): List<StoredToken>
}

/** The append-only relay: first copy of a key wins, pull by sequence. */
interface ChangeStore {
    fun push(
        accountId: String,
        deviceId: String,
        changes: List<ChangeRecord>,
        config: ServerConfig,
    ): PushResponse

    fun pull(accountId: String, cursor: Long, limit: Int): PullResponse
}

@Suppress("MagicNumber")
object Tokens {
    private val random = SecureRandom()

    fun newToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return base64Url(bytes)
    }

    fun hash(token: String): String = MessageDigest.getInstance("SHA-256")
        .digest(token.toByteArray())
        .joinToString("") { "%02x".format(it) }

    fun newId(): String = UUID.randomUUID().toString()

    fun base64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    fun randomCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..10).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
    }
}

const val SCOPE_SESSION = "session"
const val SCOPE_SYNC_READ = "sync:read"
const val SCOPE_SYNC_WRITE = "sync:write"
val SCOPE_SYNC: Set<String> = setOf(SCOPE_SYNC_READ, SCOPE_SYNC_WRITE)

/** In-memory stores: the server runs without Postgres for a trial, and tests use them. */
@Suppress("TooManyFunctions", "MagicNumber")
class InMemoryAuthStore : AuthStore {
    private val accounts = linkedMapOf<String, Account>()
    private val byUsername = linkedMapOf<String, String>()
    private val credentials = linkedMapOf<String, StoredCredential>()
    private val tokens = linkedMapOf<String, StoredToken>()
    private val pairing = linkedMapOf<String, PairingCode>()
    private val devices = linkedMapOf<String, StoredDevice>()

    @Synchronized
    override fun accountByUsername(username: String): Account? =
        byUsername[username.lowercase()]?.let(accounts::get)

    @Synchronized
    override fun accountById(id: String): Account? = accounts[id]

    @Synchronized
    override fun createAccount(account: Account): Account {
        require(!byUsername.containsKey(account.username.lowercase())) { "username taken" }
        accounts[account.id] = account
        byUsername[account.username.lowercase()] = account.id
        return account
    }

    @Synchronized
    override fun setPassword(accountId: String, passwordHash: String) {
        accounts[accountId]?.let { accounts[accountId] = it.copy(passwordHash = passwordHash) }
    }

    @Synchronized
    override fun credentialsFor(accountId: String): List<StoredCredential> =
        credentials.values.filter { it.accountId == accountId }

    @Synchronized
    override fun credentialById(credentialId: String): StoredCredential? = credentials[credentialId]

    @Synchronized
    override fun saveCredential(credential: StoredCredential) {
        credentials[credential.credentialId] = credential
    }

    @Synchronized
    override fun updateSignCount(credentialId: String, signCount: Long, at: Long) {
        credentials[credentialId]?.let {
            credentials[credentialId] = it.copy(signCount = signCount)
        }
    }

    @Synchronized
    override fun createToken(token: StoredToken): StoredToken {
        tokens[token.id] = token
        return token
    }

    @Synchronized
    override fun tokenByHash(hash: String): StoredToken? =
        tokens.values.firstOrNull { it.tokenHash == hash }

    @Synchronized
    override fun revokeToken(id: String, at: Long): Boolean {
        val token = tokens[id] ?: return false
        tokens[id] = token.copy(revokedAt = at)
        return true
    }

    @Synchronized
    override fun createPairingCode(code: PairingCode) {
        pairing[code.codeHash] = code
    }

    @Synchronized
    override fun pairingCodeByHash(codeHash: String): PairingCode? = pairing[codeHash]

    @Synchronized
    @Suppress("ReturnCount")
    override fun usePairingCode(codeHash: String, at: Long): Boolean {
        val code = pairing[codeHash] ?: return false
        if (code.usedAt != null || code.expiresAt < at) return false
        pairing[codeHash] = code.copy(usedAt = at)
        return true
    }

    @Synchronized
    override fun createDevice(device: StoredDevice) {
        devices[device.id] = device
    }

    @Synchronized
    override fun tokensFor(accountId: String): List<StoredToken> =
        tokens.values.filter { it.accountId == accountId }
}

@Suppress("LoopWithTooManyJumpStatements")
class InMemoryChangeStore(private val minSchemaVersion: Int = 1) : ChangeStore {
    private data class Entry(val accountId: String, val seq: Long, val change: ChangeRecord)

    private val log = mutableListOf<Entry>()
    private val keys = mutableSetOf<String>()

    @Synchronized
    override fun push(
        accountId: String,
        deviceId: String,
        changes: List<ChangeRecord>,
        config: ServerConfig,
    ): PushResponse {
        val rejected = mutableListOf<RejectedChange>()
        val acknowledged = mutableListOf<String>()
        for (change in changes) {
            if (change.schemaVersion > config.maxSchemaVersion) {
                rejected += RejectedChange(change.idempotencyKey, "schema version too new")
                continue
            }
            if (change.schemaVersion < config.minSchemaVersion) {
                rejected += RejectedChange(change.idempotencyKey, "update required")
                continue
            }
            val key = "$accountId:${change.idempotencyKey}"
            if (keys.add(key)) {
                log += Entry(accountId, log.size.toLong() + 1, change.copy(deviceId = deviceId))
            }
            acknowledged += change.idempotencyKey
        }
        return PushResponse(
            acknowledged = acknowledged,
            serverSeq = log.size.toLong(),
            rejected = rejected,
        )
    }

    @Synchronized
    override fun pull(accountId: String, cursor: Long, limit: Int): PullResponse {
        val mine = log.filter { it.accountId == accountId && it.seq > cursor }
        val page = mine.take(limit)
        return PullResponse(
            changes = page.map { it.change },
            nextCursor = page.lastOrNull()?.seq ?: cursor,
            hasMore = mine.size > page.size,
            minSchemaVersion = minSchemaVersion,
        )
    }
}
