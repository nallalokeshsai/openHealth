package app.openhealth.android.mcp

import app.openhealth.core.ai.McpHttpResponse
import app.openhealth.core.ai.McpProtocol
import app.openhealth.core.security.Sha256
import app.openhealth.data.LocalStore
import app.openhealth.data.repo.McpGateway

/**
 * The lifetime and the authorization of the local MCP endpoint. A request must carry a
 * token whose digest is stored and whose record is not revoked; every call is then
 * dispatched through [McpGateway] with that token, so a write names the token that made
 * it. The server binds loopback only and stops when the person turns it off.
 */
class McpRuntime(private val store: LocalStore, private val gateway: McpGateway) {
    private val server = McpLoopbackServer { authorization, body -> handle(authorization, body) }

    val running: Boolean get() = server.running

    fun start(port: Int): Boolean = server.start(port)

    fun stop() {
        server.stop()
    }

    @Suppress("ReturnCount")
    private fun handle(authorization: String?, body: String): McpHttpResponse {
        val presented = authorization
            ?.trim()
            ?.removePrefix("Bearer ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return McpHttpResponse(HTTP_UNAUTHORIZED)
        val record =
            store.mcp.byHash(Sha256.hexDigest(presented))
                ?: return McpHttpResponse(HTTP_UNAUTHORIZED)
        if (record.revoked) return McpHttpResponse(HTTP_UNAUTHORIZED)
        store.mcp.touch(record.id)
        val token = record.toProtocol()
        return McpProtocol.handle(body, token, gateway.host(token)) { refusing, tool, scope ->
            store.mcp.log(
                tokenId = refusing.id,
                tool = tool.wireName,
                scope = scope.key,
                ok = false,
                detail = "insufficient_scope",
            )
        }
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
    }
}
