package app.openhealth.android.mcp

import android.content.Context
import app.openhealth.android.preferences.PreferencesStore
import app.openhealth.core.ai.McpScope
import app.openhealth.core.ai.McpTool
import app.openhealth.core.security.Sha256
import app.openhealth.data.repo.McpRepository
import app.openhealth.ui.R as UiR
import app.openhealth.ui.screens.mcp.McpAuditModel
import app.openhealth.ui.screens.mcp.McpModel
import app.openhealth.ui.screens.mcp.McpTokenModel
import java.security.SecureRandom
import java.text.DateFormat
import java.util.Base64
import java.util.Date

/**
 * The external-access screen's holder (B15). It issues a token once, shows it once, and
 * stores only its digest. The endpoint is loopback and the server runs only while the
 * person has it on and the app is alive.
 */
class McpPresenter(
    private val context: Context,
    private val mcp: McpRepository,
    private val preferences: PreferencesStore,
    private val runtime: McpRuntime,
) {
    private val random = SecureRandom()

    fun model(newToken: String? = null, error: String? = null): McpModel {
        val port = preferences.snapshot().mcpPort
        val enabled = preferences.snapshot().mcpEnabled
        return McpModel(
            enabled = enabled,
            endpoint = context.getString(UiR.string.mcp_endpoint_value, port),
            running = runtime.running,
            tokens = mcp.tokens().map(::tokenModel),
            audit = mcp.access().map(::auditModel),
            newToken = newToken,
            error = error,
        )
    }

    fun summary(): String {
        val enabled = preferences.snapshot().mcpEnabled
        if (!enabled) return context.getString(UiR.string.settings_mcp_off)
        val active = mcp.tokens().count { !it.revoked }
        return context.resources.getQuantityString(
            UiR.plurals.settings_mcp_tokens,
            active,
            active,
        )
    }

    fun setEnabled(enabled: Boolean): String? {
        if (enabled) {
            if (!startOnFreePort()) return ERROR_START
            preferences.setMcpEnabled(true)
        } else {
            runtime.stop()
            preferences.setMcpEnabled(false)
        }
        return null
    }

    /** Starts the endpoint at app launch when it was left on. */
    fun startIfEnabled() {
        if (preferences.snapshot().mcpEnabled && !runtime.running) {
            startOnFreePort()
        }
    }

    /**
     * The preferred port first, then the rest of the declared range. A port held by
     * another app is not a dead end: the chosen one is stored so the endpoint line stays
     * truthful across a relaunch.
     */
    private fun startOnFreePort(): Boolean {
        val preferred = preferences.snapshot().mcpPort
        val ports = listOf(preferred) + (PORT_RANGE.first..PORT_RANGE.last)
            .filter { it != preferred }
        return ports.any { port ->
            runtime.start(port).also { started ->
                if (started) preferences.setMcpPort(port)
            }
        }
    }

    /** Returns the one-time token, or an error resource name. */
    @Suppress("ReturnCount")
    fun create(label: String, scopes: Set<McpScope>): Pair<String?, String?> {
        if (label.isBlank()) return null to ERROR_LABEL
        if (scopes.isEmpty()) return null to ERROR_SCOPE
        val plain = "ohm_" + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(ByteArray(TOKEN_BYTES).also(random::nextBytes))
        mcp.issue(label.trim().take(MAX_LABEL), scopes, Sha256.hexDigest(plain))
        return plain to null
    }

    fun revoke(id: String): Boolean = mcp.revoke(id)

    private fun tokenModel(record: app.openhealth.data.repo.McpTokenRecord): McpTokenModel =
        McpTokenModel(
            id = record.id,
            label = record.label,
            scopes = record.scopes.sortedBy { it.key },
            createdLabel = date(record.createdAt),
            lastUsedLabel = record.lastUsedAt?.let(::date),
            revoked = record.revoked,
        )

    private fun auditModel(record: app.openhealth.data.repo.McpAccessRecord): McpAuditModel =
        McpAuditModel(
            id = record.id,
            tool = McpTool.fromWire(record.tool)?.wireName ?: record.tool,
            atLabel = date(record.at),
            ok = record.ok,
        )

    private fun date(millis: Long): String {
        val formatted = DateFormat.getDateTimeInstance(
            DateFormat.MEDIUM,
            DateFormat.SHORT,
        ).format(Date(millis))
        // A date and a time are not mirrored in RTL; isolate them so the surrounding
        // paragraph cannot reorder the two groups (§11.5).
        return "\u2066$formatted\u2069"
    }

    companion object {
        const val ERROR_LABEL = "mcp_error_label"
        const val ERROR_SCOPE = "mcp_error_scope"
        const val ERROR_START = "mcp_error_start"
        private const val TOKEN_BYTES = 32
        private const val MAX_LABEL = 60
        private val PORT_RANGE = 8_765..8_775
    }
}
