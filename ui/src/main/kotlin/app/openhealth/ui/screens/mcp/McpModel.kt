package app.openhealth.ui.screens.mcp

import app.openhealth.core.ai.McpScope

data class McpTokenModel(
    val id: String,
    val label: String,
    val scopes: List<McpScope>,
    val createdLabel: String,
    val lastUsedLabel: String?,
    val revoked: Boolean,
)

data class McpAuditModel(val id: String, val tool: String, val atLabel: String, val ok: Boolean)

/**
 * The local MCP surface (`pr.md` §6.30). [newToken] is the one-time reveal and is held in
 * memory only; [endpoint] is loopback and is stated as available while the app is open.
 */
data class McpModel(
    val enabled: Boolean,
    val endpoint: String,
    val running: Boolean,
    val tokens: List<McpTokenModel>,
    val audit: List<McpAuditModel>,
    val newToken: String?,
    val error: String?,
)
