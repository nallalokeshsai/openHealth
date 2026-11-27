package app.openhealth.core.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The scoped MCP surface (`pr.md` §6.15 V1, §6.30 V1). */
class McpProtocolTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val token = McpToken(
        id = "tok",
        label = "Laptop",
        scopes = setOf(McpScope.READ_TRAINING, McpScope.WRITE_NUTRITION),
    )

    private class Host : McpToolHost {
        val calls = mutableListOf<McpTool>()

        override fun call(tool: McpTool, arguments: JsonObject): McpToolResult {
            calls += tool
            return McpToolResult.Ok("""{"tool":"${tool.wireName}"}""")
        }
    }

    private fun call(body: String, token: McpToken = this.token): Pair<Int, JsonObject?> {
        val response = McpProtocol.handle(body, token, Host())
        return response.status to response.body?.let { json.parseToJsonElement(it).jsonObject }
    }

    @Test
    fun initializeAnnouncesTheProtocolAndTools() {
        val (status, body) = call(
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""",
        )
        assertEquals(200, status)
        val result = body!!.getValue("result").jsonObject
        assertEquals(McpProtocol.VERSION, result.getValue("protocolVersion").jsonPrimitive.content)
        assertEquals(
            "openhealth",
            result.getValue("serverInfo").jsonObject
                .getValue("name").jsonPrimitive.content,
        )
        assertEquals(1, body.getValue("id").jsonPrimitive.content.toInt())
    }

    @Test
    fun toolsListIsFilteredByTheTokensScopes() {
        val (_, body) = call("""{"jsonrpc":"2.0","id":"a","method":"tools/list"}""")
        val tools = body!!.getValue("result").jsonObject.getValue("tools").jsonArray
        val names = tools.map { it.jsonObject.getValue("name").jsonPrimitive.content }
        assertEquals(
            listOf(
                McpTool.GET_TRAINING_PLAN.wireName,
                McpTool.LIST_RECENT_SESSIONS.wireName,
                McpTool.LOG_WATER.wireName,
            ),
            names,
        )
        tools.forEach { tool ->
            val schema = tool.jsonObject.getValue("inputSchema").jsonObject
            assertEquals("object", schema.getValue("type").jsonPrimitive.content)
        }
    }

    @Test
    fun anAuthorizedCallReachesTheHost() {
        val host = Host()
        val response = McpProtocol.handle(
            """{"jsonrpc":"2.0","id":2,"method":"tools/call","params":
               {"name":"openhealth_get_training_plan","arguments":{}}}""",
            token,
            host,
        )
        assertEquals(200, response.status)
        assertEquals(listOf(McpTool.GET_TRAINING_PLAN), host.calls)
        val result = json.parseToJsonElement(response.body!!).jsonObject
            .getValue("result").jsonObject
        assertEquals(false, result.getValue("isError").jsonPrimitive.content.toBoolean())
        assertTrue(
            result.getValue("content").jsonArray.single().jsonObject
                .getValue("text").jsonPrimitive.content.contains("get_training_plan"),
        )
    }

    @Test
    fun aReadOnlyTokenCannotWrite() {
        val host = Host()
        var refused = false
        val response = McpProtocol.handle(
            """{"jsonrpc":"2.0","id":3,"method":"tools/call","params":
               {"name":"openhealth_log_weight","arguments":{"weight_kg":80}}}""",
            token,
            host,
        ) { _, _, _ -> refused = true }
        val error = json.parseToJsonElement(response.body!!).jsonObject
            .getValue("error").jsonObject
        assertEquals(-32001, error.getValue("code").jsonPrimitive.content.toInt())
        assertTrue(host.calls.isEmpty())
        assertTrue(refused, "a refused call must be visible to the audit")
    }

    @Test
    fun anUnknownToolAndMethodAreProtocolErrors() {
        val unknownTool = McpProtocol.handle(
            """{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"nope"}}""",
            token,
            Host(),
        )
        assertEquals(
            -32602,
            json.parseToJsonElement(unknownTool.body!!).jsonObject
                .getValue("error").jsonObject.getValue("code").jsonPrimitive.content.toInt(),
        )
        val unknownMethod = McpProtocol.handle(
            """{"jsonrpc":"2.0","id":5,"method":"resources/list"}""",
            token,
            Host(),
        )
        assertEquals(
            -32601,
            json.parseToJsonElement(unknownMethod.body!!).jsonObject
                .getValue("error").jsonObject.getValue("code").jsonPrimitive.content.toInt(),
        )
    }

    @Test
    fun aRevokedTokenIsRefusedAtTheDoor() {
        val response = McpProtocol.handle(
            """{"jsonrpc":"2.0","id":6,"method":"tools/list"}""",
            token.copy(revoked = true),
            Host(),
        )
        assertEquals(401, response.status)
    }

    @Test
    fun aNotificationGetsNoBody() {
        val response = McpProtocol.handle(
            """{"jsonrpc":"2.0","method":"notifications/initialized"}""",
            token,
            Host(),
        )
        assertEquals(202, response.status)
        assertNull(response.body)
    }

    @Test
    fun anAcknowledgedNotificationWithAnIdGetsAResult() {
        val (status, body) = call(
            """{"jsonrpc":"2.0","id":9,"method":"notifications/initialized"}""",
        )
        assertEquals(200, status)
        assertTrue(body!!.contains("result"))
    }

    @Test
    fun aBatchBodyIsAnInvalidRequest() {
        val response = McpProtocol.handle("[{}]", token, Host())
        assertEquals(400, response.status)
        assertTrue(response.body!!.contains("-32600"))
    }

    @Test
    fun malformedJsonIsAParseError() {
        val response = McpProtocol.handle("{", token, Host())
        assertEquals(400, response.status)
        assertTrue(response.body!!.contains("-32700"))
    }

    @Test
    fun aHostErrorBecomesAnIsErrorResultNotAStackTrace() {
        val failing = object : McpToolHost {
            override fun call(tool: McpTool, arguments: JsonObject): McpToolResult =
                McpToolResult.Error("out_of_range")
        }
        val response = McpProtocol.handle(
            """{"jsonrpc":"2.0","id":7,"method":"tools/call","params":
               {"name":"openhealth_log_water","arguments":{"millilitres":1}}}""",
            token,
            failing,
        )
        val result = json.parseToJsonElement(response.body!!).jsonObject
            .getValue("result").jsonObject
        assertEquals(true, result.getValue("isError").jsonPrimitive.content.toBoolean())
        assertEquals(
            "out_of_range",
            result.getValue("content").jsonArray.single().jsonObject
                .getValue("text").jsonPrimitive.content,
        )
    }
}
