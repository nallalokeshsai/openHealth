package app.openhealth.data.repo

import app.openhealth.core.ai.McpScope
import app.openhealth.core.ai.McpToken
import app.openhealth.core.ids.IdGenerator
import app.openhealth.core.ids.RandomIdGenerator
import app.openhealth.core.seam.ClockSeam
import app.openhealth.core.seam.SystemClock
import app.openhealth.data.db.Mcp_access
import app.openhealth.data.db.Mcp_token
import app.openhealth.data.db.OpenHealthDb

data class McpTokenRecord(
    val id: String,
    val label: String,
    val scopes: Set<McpScope>,
    val createdAt: Long,
    val revokedAt: Long?,
    val lastUsedAt: Long?,
) {
    val revoked: Boolean get() = revokedAt != null

    fun toProtocol(): McpToken = McpToken(
        id = id,
        label = label,
        scopes = scopes,
        revoked = revoked,
    )
}

data class McpAccessRecord(
    val id: String,
    val tokenId: String?,
    val tool: String,
    val scope: String?,
    val at: Long,
    val ok: Boolean,
    val detail: String?,
)

/**
 * The local MCP token store (B15, `pr.md` §6.30). A token is never stored in the clear:
 * the caller keeps the plain value for the one reveal and stores only its SHA-256 digest
 * here. The audit records the tool and the decision, never an argument, so a weight or a
 * note body cannot leak into a log line.
 *
 * Neither table syncs (`SyncTables.neverSynced`): a bearer token belongs to one device.
 */
class McpRepository(
    private val db: OpenHealthDb,
    private val clock: ClockSeam = SystemClock,
    private val ids: IdGenerator = RandomIdGenerator(),
) {
    fun issue(label: String, scopes: Set<McpScope>, tokenHash: String): McpTokenRecord {
        val id = ids.newId()
        db.mcpQueries.insertToken(
            id = id,
            label = label,
            scopes = scopes.joinToString(",") { it.key },
            tokenHash = tokenHash,
            createdAt = clock.now().toEpochMilliseconds(),
        )
        return McpTokenRecord(
            id = id,
            label = label,
            scopes = scopes,
            createdAt = clock.now().toEpochMilliseconds(),
            revokedAt = null,
            lastUsedAt = null,
        )
    }

    fun tokens(): List<McpTokenRecord> =
        db.mcpQueries.allTokens().executeAsList().map { it.toRecord() }

    fun byHash(tokenHash: String): McpTokenRecord? =
        db.mcpQueries.tokenByHash(tokenHash).executeAsOneOrNull()?.toRecord()

    fun revoke(id: String): Boolean {
        val token = tokens().firstOrNull { it.id == id } ?: return false
        if (token.revoked) return false
        db.mcpQueries.revokeToken(
            revokedAt = clock.now().toEpochMilliseconds(),
            id = id,
        )
        return true
    }

    fun touch(id: String) {
        db.mcpQueries.touchToken(clock.now().toEpochMilliseconds(), id)
    }

    fun log(tokenId: String?, tool: String, scope: String?, ok: Boolean, detail: String? = null) {
        db.mcpQueries.insertAccess(
            id = ids.newId(),
            tokenId = tokenId,
            tool = tool,
            scope = scope,
            at = clock.now().toEpochMilliseconds(),
            ok = ok,
            detail = detail,
        )
        // Keep the audit readable rather than unbounded: the newest 500 calls.
        db.mcpQueries.deleteAccessBefore(
            cutoff = clock.now().toEpochMilliseconds() - RETENTION_MILLIS,
        )
    }

    fun access(limit: Long = 50): List<McpAccessRecord> =
        db.mcpQueries.recentAccess(limit).executeAsList().map {
            McpAccessRecord(
                id = it.id,
                tokenId = it.token_id,
                tool = it.tool,
                scope = it.scope,
                at = it.at,
                ok = it.ok,
                detail = it.detail,
            )
        }

    fun countAccess(): Long = db.mcpQueries.countAccess().executeAsOne()

    private fun Mcp_token.toRecord(): McpTokenRecord = McpTokenRecord(
        id = id,
        label = label,
        scopes = scopes.split(',')
            .mapNotNull(McpScope::fromKey)
            .toSet(),
        createdAt = created_at,
        revokedAt = revoked_at,
        lastUsedAt = last_used_at,
    )

    private fun Mcp_access.toRecord(): McpAccessRecord = McpAccessRecord(
        id = id,
        tokenId = token_id,
        tool = tool,
        scope = scope,
        at = at,
        ok = ok,
        detail = detail,
    )

    private companion object {
        const val RETENTION_DAYS = 30L
        const val MILLIS_PER_DAY = 86_400_000L
        const val RETENTION_MILLIS = RETENTION_DAYS * MILLIS_PER_DAY
    }
}
