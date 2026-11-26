@file:Suppress("MagicNumber")

package app.openhealth.server

import app.openhealth.core.sync.ChangeRecord
import app.openhealth.core.sync.PullResponse
import app.openhealth.core.sync.PushResponse
import app.openhealth.core.sync.RejectedChange
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.flywaydb.core.Flyway

/** PostgreSQL 16 behind Hikari, with Flyway forward-only migrations (`Tech-Stack.md` §7). */
class PostgresDatabase(config: ServerConfig) : AutoCloseable {
    val dataSource: HikariDataSource = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = config.dbUrl ?: error("OPENHEALTH_DB_URL is required for PostgreSQL")
            username = config.dbUser
            password = config.dbPassword
            maximumPoolSize = 10
            minimumIdle = 1
            poolName = "openhealth-server"
            connectionTimeout = 10_000
        },
    )

    init {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }

    override fun close() {
        dataSource.close()
    }

    fun <T> read(block: (Connection) -> T): T =
        dataSource.connection.use { connection -> block(connection) }
}

@Suppress("TooGenericExceptionCaught")
private fun Connection.transaction(block: (Connection) -> Unit) {
    autoCommit = false
    try {
        block(this)
        commit()
    } catch (failure: Exception) {
        rollback()
        throw failure
    } finally {
        autoCommit = true
    }
}

private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = true
}
private val mapSerializer = MapSerializer(String.serializer(), String.serializer().nullable)
private val stringMapSerializer = MapSerializer(String.serializer(), String.serializer())

@Suppress("TooManyFunctions")
class PostgresAuthStore(private val db: PostgresDatabase) : AuthStore {
    override fun accountByUsername(username: String): Account? = db.read { connection ->
        connection.prepareStatement(
            "SELECT id, username, display_name, password_hash, created_at FROM account " +
                "WHERE lower(username) = lower(?)",
        ).use { statement ->
            statement.setString(1, username)
            statement.executeQuery().use { rows -> if (rows.next()) rows.toAccount() else null }
        }
    }

    override fun accountById(id: String): Account? = db.read { connection ->
        connection.prepareStatement(
            "SELECT id, username, display_name, password_hash, created_at FROM account " +
                "WHERE id = ?::uuid",
        ).use { statement ->
            statement.setString(1, id)
            statement.executeQuery().use { rows -> if (rows.next()) rows.toAccount() else null }
        }
    }

    override fun createAccount(account: Account): Account = db.read { connection ->
        connection.prepareStatement(
            "INSERT INTO account(id, username, display_name, password_hash, created_at) " +
                "VALUES (?::uuid, ?, ?, ?, ?)",
        ).use { statement ->
            statement.setString(1, account.id)
            statement.setString(2, account.username)
            statement.setString(3, account.displayName)
            statement.setString(4, account.passwordHash)
            statement.setLong(5, account.createdAt)
            statement.executeUpdate()
        }
        account
    }

    override fun setPassword(accountId: String, passwordHash: String) {
        db.read { connection ->
            connection.prepareStatement("UPDATE account SET password_hash = ? WHERE id = ?::uuid")
                .use { statement ->
                    statement.setString(1, passwordHash)
                    statement.setString(2, accountId)
                    statement.executeUpdate()
                }
        }
    }

    override fun credentialsFor(accountId: String): List<StoredCredential> = db.read { connection ->
        connection.prepareStatement(
            "SELECT credential_id, account_id, attestation_object, client_data, transports, " +
                "sign_count, created_at FROM webauthn_credential WHERE account_id = ?::uuid",
        ).use { statement ->
            statement.setString(1, accountId)
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) add(rows.toCredential())
                }
            }
        }
    }

    override fun credentialById(credentialId: String): StoredCredential? = db.read { connection ->
        connection.prepareStatement(
            "SELECT credential_id, account_id, attestation_object, client_data, transports, " +
                "sign_count, created_at FROM webauthn_credential WHERE credential_id = ?",
        ).use { statement ->
            statement.setString(1, credentialId)
            statement.executeQuery().use { rows ->
                if (rows.next()) rows.toCredential() else null
            }
        }
    }

    override fun saveCredential(credential: StoredCredential) {
        db.read { connection ->
            connection.prepareStatement(
                "INSERT INTO webauthn_credential(credential_id, account_id, " +
                    "attestation_object, client_data, transports, sign_count, created_at) " +
                    "VALUES (?, ?::uuid, ?, ?, ?, ?, ?) " +
                    "ON CONFLICT (credential_id) DO UPDATE SET sign_count = " +
                    "excluded.sign_count",
            ).use { statement ->
                statement.setString(1, credential.credentialId)
                statement.setString(2, credential.accountId)
                statement.setBytes(3, credential.attestationObject)
                statement.setBytes(4, credential.clientData)
                statement.setString(5, credential.transports.joinToString(","))
                statement.setLong(6, credential.signCount)
                statement.setLong(7, credential.createdAt)
                statement.executeUpdate()
            }
        }
    }

    override fun updateSignCount(credentialId: String, signCount: Long, at: Long) {
        db.read { connection ->
            connection.prepareStatement(
                "UPDATE webauthn_credential SET sign_count = ?, last_used_at = ? " +
                    "WHERE credential_id = ?",
            ).use { statement ->
                statement.setLong(1, signCount)
                statement.setLong(2, at)
                statement.setString(3, credentialId)
                statement.executeUpdate()
            }
        }
    }

    override fun createToken(token: StoredToken): StoredToken {
        db.read { connection ->
            connection.prepareStatement(
                "INSERT INTO auth_token(id, account_id, token_hash, scopes, label, device_id, " +
                    "created_at, expires_at) VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?)",
            ).use { statement ->
                statement.setString(1, token.id)
                statement.setString(2, token.accountId)
                statement.setString(3, token.tokenHash)
                statement.setString(4, token.scopes.joinToString(" "))
                statement.setString(5, token.label)
                statement.setString(6, token.deviceId)
                statement.setLong(7, token.createdAt)
                if (token.expiresAt == null) {
                    statement.setNull(8, java.sql.Types.BIGINT)
                } else {
                    statement.setLong(8, token.expiresAt)
                }
                statement.executeUpdate()
            }
        }
        return token
    }

    override fun tokenByHash(hash: String): StoredToken? = db.read { connection ->
        connection.prepareStatement(
            "SELECT id, account_id, token_hash, scopes, label, device_id, created_at, " +
                "expires_at, revoked_at FROM auth_token WHERE token_hash = ?",
        ).use { statement ->
            statement.setString(1, hash)
            statement.executeQuery().use { rows -> if (rows.next()) rows.toToken() else null }
        }
    }

    override fun revokeToken(id: String, at: Long): Boolean = db.read { connection ->
        connection.prepareStatement(
            "UPDATE auth_token SET revoked_at = ? WHERE id = ?::uuid AND revoked_at IS NULL",
        ).use { statement ->
            statement.setLong(1, at)
            statement.setString(2, id)
            statement.executeUpdate() > 0
        }
    }

    override fun createPairingCode(code: PairingCode) {
        db.read { connection ->
            connection.prepareStatement(
                "INSERT INTO pairing_code(code_hash, account_id, created_at, expires_at) " +
                    "VALUES (?, ?::uuid, ?, ?)",
            ).use { statement ->
                statement.setString(1, code.codeHash)
                statement.setString(2, code.accountId)
                statement.setLong(3, code.createdAt)
                statement.setLong(4, code.expiresAt)
                statement.executeUpdate()
            }
        }
    }

    override fun pairingCodeByHash(codeHash: String): PairingCode? = db.read { connection ->
        connection.prepareStatement(
            "SELECT code_hash, account_id, created_at, expires_at, used_at FROM pairing_code " +
                "WHERE code_hash = ?",
        ).use { statement ->
            statement.setString(1, codeHash)
            statement.executeQuery().use { rows ->
                if (rows.next()) {
                    PairingCode(
                        codeHash = rows.getString("code_hash"),
                        accountId = rows.getString("account_id"),
                        createdAt = rows.getLong("created_at"),
                        expiresAt = rows.getLong("expires_at"),
                        usedAt = rows.getLongOrNull("used_at"),
                    )
                } else {
                    null
                }
            }
        }
    }

    override fun usePairingCode(codeHash: String, at: Long): Boolean = db.read { connection ->
        connection.prepareStatement(
            "UPDATE pairing_code SET used_at = ? WHERE code_hash = ? AND used_at IS NULL " +
                "AND expires_at >= ?",
        ).use { statement ->
            statement.setLong(1, at)
            statement.setString(2, codeHash)
            statement.setLong(3, at)
            statement.executeUpdate() > 0
        }
    }

    override fun createDevice(device: StoredDevice) {
        db.read { connection ->
            connection.prepareStatement(
                "INSERT INTO device(id, account_id, name, created_at) VALUES (?::uuid, ?::uuid, ?, ?)",
            ).use { statement ->
                statement.setString(1, device.id)
                statement.setString(2, device.accountId)
                statement.setString(3, device.name)
                statement.setLong(4, device.createdAt)
                statement.executeUpdate()
            }
        }
    }

    override fun tokensFor(accountId: String): List<StoredToken> = db.read { connection ->
        connection.prepareStatement(
            "SELECT id, account_id, token_hash, scopes, label, device_id, created_at, " +
                "expires_at, revoked_at FROM auth_token WHERE account_id = ?::uuid",
        ).use { statement ->
            statement.setString(1, accountId)
            statement.executeQuery().use { rows ->
                buildList { while (rows.next()) add(rows.toToken()) }
            }
        }
    }

    private fun ResultSet.toAccount() = Account(
        id = getString("id"),
        username = getString("username"),
        displayName = getString("display_name"),
        passwordHash = getString("password_hash"),
        createdAt = getLong("created_at"),
    )

    private fun ResultSet.toCredential() = StoredCredential(
        credentialId = getString("credential_id"),
        accountId = getString("account_id"),
        attestationObject = getBytes("attestation_object"),
        clientData = getBytes("client_data"),
        transports = getString(
            "transports",
        ).orEmpty().split(',').filter(String::isNotBlank).toSet(),
        signCount = getLong("sign_count"),
        createdAt = getLong("created_at"),
    )

    private fun ResultSet.toToken() = StoredToken(
        id = getString("id"),
        accountId = getString("account_id"),
        tokenHash = getString("token_hash"),
        scopes = getString("scopes").orEmpty().split(' ').filter(String::isNotBlank).toSet(),
        label = getString("label"),
        deviceId = getString("device_id"),
        createdAt = getLong("created_at"),
        expiresAt = getLongOrNull("expires_at"),
        revokedAt = getLongOrNull("revoked_at"),
    )

    private fun ResultSet.getLongOrNull(column: String): Long? {
        val value = getLong(column)
        return if (wasNull()) null else value
    }
}

@Suppress("LoopWithTooManyJumpStatements")
class PostgresChangeStore(
    private val db: PostgresDatabase,
    private val minSchemaVersion: Int = 1,
) : ChangeStore {
    override fun push(
        accountId: String,
        deviceId: String,
        changes: List<ChangeRecord>,
        config: ServerConfig,
    ): PushResponse {
        val acknowledged = mutableListOf<String>()
        val rejected = mutableListOf<RejectedChange>()
        var serverSeq = 0L
        db.read { connection ->
            connection.transaction { tx ->
                val insert = tx.prepareStatement(
                    "INSERT INTO change(account_id, entity, entity_id, operation, fields, " +
                        "field_clocks, idempotency_key, hlc, device_id, schema_version, " +
                        "local_date, recorded_at) VALUES (?::uuid, ?, ?, ?, ?::jsonb, " +
                        "?::jsonb, ?, ?, ?, ?, ?, ?) " +
                        "ON CONFLICT (account_id, idempotency_key) DO NOTHING",
                )
                try {
                    for (change in changes) {
                        if (change.schemaVersion > config.maxSchemaVersion) {
                            rejected += RejectedChange(
                                change.idempotencyKey,
                                "schema version too new",
                            )
                            continue
                        }
                        if (change.schemaVersion < config.minSchemaVersion) {
                            rejected += RejectedChange(change.idempotencyKey, "update required")
                            continue
                        }
                        insert.setString(1, accountId)
                        insert.setString(2, change.entity)
                        insert.setString(3, change.entityId)
                        insert.setString(4, change.operation.name)
                        insert.setString(5, json.encodeToString(mapSerializer, change.fields))
                        insert.setString(
                            6,
                            json.encodeToString(stringMapSerializer, change.fieldClocks),
                        )
                        insert.setString(7, change.idempotencyKey)
                        insert.setString(8, change.hlc)
                        insert.setString(9, deviceId)
                        insert.setInt(10, change.schemaVersion)
                        insert.setString(11, change.localDate)
                        insert.setLong(12, change.recordedAt)
                        insert.executeUpdate()
                        acknowledged += change.idempotencyKey
                    }
                } finally {
                    insert.close()
                }
                tx.prepareStatement(
                    "SELECT COALESCE(MAX(seq), 0) FROM change WHERE account_id = ?::uuid",
                ).use { statement ->
                    statement.setString(1, accountId)
                    statement.executeQuery().use { rows ->
                        if (rows.next()) serverSeq = rows.getLong(1)
                    }
                }
            }
        }
        return PushResponse(acknowledged, serverSeq, rejected)
    }

    override fun pull(accountId: String, cursor: Long, limit: Int): PullResponse {
        val page = db.read { connection ->
            connection.prepareStatement(
                "SELECT seq, entity, entity_id, operation, fields::text, field_clocks::text, " +
                    "idempotency_key, hlc, device_id, schema_version, local_date, recorded_at " +
                    "FROM change WHERE account_id = ?::uuid AND seq > ? ORDER BY seq LIMIT ?",
            ).use { statement ->
                statement.setString(1, accountId)
                statement.setLong(2, cursor)
                statement.setInt(3, limit + 1)
                statement.executeQuery().use { rows ->
                    buildList {
                        while (rows.next()) add(rows.toChangeRecord())
                    }
                }
            }
        }
        val hasMore = page.size > limit
        val visible = page.take(limit)
        return PullResponse(
            changes = visible.map { it.second },
            nextCursor = visible.lastOrNull()?.first ?: cursor,
            hasMore = hasMore,
            minSchemaVersion = minSchemaVersion,
        )
    }

    private fun ResultSet.toChangeRecord(): Pair<Long, ChangeRecord> =
        getLong("seq") to ChangeRecord(
            entity = getString("entity"),
            entityId = getString("entity_id"),
            operation = app.openhealth.core.sync.ChangeOperation.valueOf(getString("operation")),
            fields = json.decodeFromString(mapSerializer, getString("fields")),
            fieldClocks = json.decodeFromString(stringMapSerializer, getString("field_clocks")),
            idempotencyKey = getString("idempotency_key"),
            hlc = getString("hlc"),
            deviceId = getString("device_id"),
            schemaVersion = getInt("schema_version"),
            localDate = getString("local_date"),
            recordedAt = getLong("recorded_at"),
        )
}

/** Used by docs/tests: a UUID string is the only id shape the schema accepts. */
fun newUuid(): String = UUID.randomUUID().toString()
