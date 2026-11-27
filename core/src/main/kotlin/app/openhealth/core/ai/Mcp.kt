@file:Suppress("MagicNumber", "TooManyFunctions") // JSON-RPC error codes are protocol constants.

package app.openhealth.core.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** The scopes an MCP token can carry. A tool names exactly one (`pr.md` §6.30). */
enum class McpScope(val key: String) {
    READ_PROFILE("read:profile"),
    READ_TRAINING("read:training"),
    READ_DAY("read:day"),
    WRITE_BODY("write:body"),
    WRITE_NUTRITION("write:nutrition"),
    WRITE_NOTES("write:notes"),
    ;

    companion object {
        fun fromKey(key: String?): McpScope? = entries.firstOrNull { it.key == key }
    }
}

/** A token as the protocol sees it. The digest never leaves the data layer. */
data class McpToken(
    val id: String,
    val label: String,
    val scopes: Set<McpScope>,
    val revoked: Boolean = false,
) {
    fun allows(scope: McpScope): Boolean = !revoked && scope in scopes
}

/** The seven tools of the documented surface. Names are prefixed for a shared client. */
enum class McpTool(val wireName: String, val scope: McpScope, val description: String) {
    GET_PROFILE(
        "openhealth_get_profile",
        McpScope.READ_PROFILE,
        "The profile settings the person chose: sex, birth year, height, units, goal mode " +
            "and training goal. No observations and no notes.",
    ),
    GET_TRAINING_PLAN(
        "openhealth_get_training_plan",
        McpScope.READ_TRAINING,
        "Every routine with its weekday and its exercise targets (sets, reps, load, RIR).",
    ),
    LIST_RECENT_SESSIONS(
        "openhealth_list_recent_sessions",
        McpScope.READ_TRAINING,
        "Finished sessions, newest first, reduced to date, routine, set count, volume and " +
            "perceived effort.",
    ),
    GET_DAY_SUMMARY(
        "openhealth_get_day_summary",
        McpScope.READ_DAY,
        "One day's logged summary: steps, active energy, water, sleep and the day's " +
            "training volume. Defaults to today.",
    ),
    LOG_WEIGHT(
        "openhealth_log_weight",
        McpScope.WRITE_BODY,
        "Logs one body-weight reading in kilograms. The same plausibility check as the " +
            "manual entry runs; an out-of-range value is rejected.",
    ),
    LOG_WATER(
        "openhealth_log_water",
        McpScope.WRITE_NUTRITION,
        "Logs water in millilitres. The same plausibility check as the manual entry runs.",
    ),
    ADD_NOTE(
        "openhealth_add_note",
        McpScope.WRITE_NOTES,
        "Adds one note to the person's timeline. The text is stored verbatim, up to 2000 " +
            "characters.",
    ),
    ;

    companion object {
        fun fromWire(name: String?): McpTool? = entries.firstOrNull { it.wireName == name }
    }
}

sealed interface McpToolResult {
    data class Ok(val json: String) : McpToolResult

    /** A stable code the assistant can act on; never a raw exception or a stack trace. */
    data class Error(val code: String) : McpToolResult
}

/**
 * The data layer implements this. Every implementation must route writes through the
 * same repositories and validators as the UI (`pr.md` §6.30 acceptance).
 */
interface McpToolHost {
    fun call(tool: McpTool, arguments: JsonObject): McpToolResult
}

data class McpHttpResponse(val status: Int, val body: String? = null)

/**
 * A JSON-RPC 2.0 MCP server over the Streamable HTTP transport, stateless variant: every
 * POST carries one request and gets one JSON answer (the spec permits JSON instead of
 * SSE). The HTTP layer owns the loopback binding and the bearer-token lookup.
 */
object McpProtocol {
    const val VERSION = "2025-06-18"
    const val PATH = "/mcp"
    const val SERVER_NAME = "openhealth"
    const val SERVER_VERSION = "1.0"
    const val MAX_BODY_BYTES = 64 * 1024

    private const val PARSE_ERROR = -32700
    private const val INVALID_REQUEST = -32600
    private const val METHOD_NOT_FOUND = -32601
    private const val INVALID_PARAMS = -32602
    private const val INSUFFICIENT_SCOPE = -32001

    private val json = Json { ignoreUnknownKeys = true }

    @Suppress("ReturnCount", "CyclomaticComplexMethod")
    fun handle(
        body: String,
        token: McpToken,
        host: McpToolHost,
        onRefused: (McpToken, McpTool, McpScope) -> Unit = { _, _, _ -> },
    ): McpHttpResponse {
        if (token.revoked) return McpHttpResponse(401)
        val parsed = runCatching { json.parseToJsonElement(body) }.getOrNull()
            ?: return McpHttpResponse(400, error(JsonNull, PARSE_ERROR, "Parse error"))
        if (parsed is JsonArray) {
            // JSON-RPC batches were removed from the 2025-06-18 transport.
            return McpHttpResponse(400, error(JsonNull, INVALID_REQUEST, "Invalid Request"))
        }
        val root = parsed as? JsonObject
            ?: return McpHttpResponse(400, error(JsonNull, INVALID_REQUEST, "Invalid Request"))
        val id = root["id"] ?: JsonNull
        if (root["jsonrpc"]?.jsonPrimitive?.contentOrNull != "2.0") {
            return McpHttpResponse(400, error(id, INVALID_REQUEST, "Invalid Request"))
        }
        val method = root["method"]?.jsonPrimitive?.contentOrNull
            ?: return McpHttpResponse(400, error(id, INVALID_REQUEST, "Invalid Request"))
        val params = root["params"] as? JsonObject ?: JsonObject(emptyMap())
        val notification = root["id"] == null
        val response = when (method) {
            "notifications/initialized" -> if (notification) {
                null
            } else {
                result(id, buildJsonObject {})
            }
            "initialize" -> result(id, initialize())
            "ping" -> result(id, buildJsonObject {})
            "tools/list" -> result(id, toolsList(token))
            "tools/call" -> callTool(id, params, token, host, onRefused)
            else -> error(id, METHOD_NOT_FOUND, "Method not found")
        }
        return when {
            notification -> McpHttpResponse(202)
            response == null -> McpHttpResponse(202)
            else -> McpHttpResponse(200, response)
        }
    }

    /** The tool names a token may call; used by tests and the token UI. */
    fun toolNamesFor(token: McpToken): List<String> =
        McpTool.entries.filter { token.allows(it.scope) }.map { it.wireName }

    private fun initialize(): JsonObject = buildJsonObject {
        put("protocolVersion", VERSION)
        put(
            "capabilities",
            buildJsonObject {
                put("tools", buildJsonObject { put("listChanged", false) })
            },
        )
        put(
            "serverInfo",
            buildJsonObject {
                put("name", SERVER_NAME)
                put("version", SERVER_VERSION)
            },
        )
    }

    private fun toolsList(token: McpToken): JsonObject = buildJsonObject {
        put(
            "tools",
            buildJsonArray {
                McpTool.entries.filter { token.allows(it.scope) }.forEach { tool ->
                    add(
                        buildJsonObject {
                            put("name", tool.wireName)
                            put("description", tool.description)
                            put("inputSchema", inputSchema(tool))
                        },
                    )
                }
            },
        )
    }

    @Suppress("LongMethod")
    private fun inputSchema(tool: McpTool): JsonObject = when (tool) {
        McpTool.LIST_RECENT_SESSIONS -> schema(
            buildJsonObject {
                put(
                    "limit",
                    buildJsonObject {
                        put("type", "integer")
                        put("minimum", 1)
                        put("maximum", 30)
                    },
                )
            },
        )

        McpTool.GET_DAY_SUMMARY -> schema(
            buildJsonObject {
                put(
                    "date",
                    buildJsonObject {
                        put("type", "string")
                        put("description", "ISO local date, YYYY-MM-DD. Defaults to today.")
                    },
                )
            },
        )

        McpTool.LOG_WEIGHT, McpTool.LOG_WATER -> {
            val field = if (tool == McpTool.LOG_WEIGHT) "weight_kg" else "millilitres"
            schema(
                buildJsonObject {
                    put(
                        field,
                        buildJsonObject {
                            put("type", "number")
                            put("minimum", if (tool == McpTool.LOG_WEIGHT) 1 else 0)
                            put("maximum", if (tool == McpTool.LOG_WEIGHT) 500 else 20_000)
                        },
                    )
                    put(
                        "date",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "ISO local date, YYYY-MM-DD. Defaults to today.")
                        },
                    )
                },
                required = listOf(field),
            )
        }

        McpTool.ADD_NOTE -> schema(
            buildJsonObject {
                put(
                    "text",
                    buildJsonObject {
                        put("type", "string")
                        put("minLength", 1)
                        put("maxLength", 2_000)
                    },
                )
            },
            required = listOf("text"),
        )

        McpTool.GET_PROFILE, McpTool.GET_TRAINING_PLAN -> schema(buildJsonObject {})
    }

    private fun schema(properties: JsonObject, required: List<String> = emptyList()): JsonObject =
        buildJsonObject {
            put("type", "object")
            put("properties", properties)
            put("additionalProperties", false)
            if (required.isNotEmpty()) {
                put(
                    "required",
                    buildJsonArray { required.forEach { add(JsonPrimitive(it)) } },
                )
            }
        }

    @Suppress("ReturnCount")
    private fun callTool(
        id: JsonElement,
        params: JsonObject,
        token: McpToken,
        host: McpToolHost,
        onRefused: (McpToken, McpTool, McpScope) -> Unit,
    ): String {
        val tool = McpTool.fromWire(params["name"]?.jsonPrimitive?.contentOrNull)
            ?: return error(id, INVALID_PARAMS, "Unknown tool")
        if (!token.allows(tool.scope)) {
            onRefused(token, tool, tool.scope)
            return error(id, INSUFFICIENT_SCOPE, "Insufficient scope")
        }
        val arguments = params["arguments"] as? JsonObject ?: JsonObject(emptyMap())
        val outcome = runCatching { host.call(tool, arguments) }
            .getOrElse { McpToolResult.Error("internal_error") }
        return when (outcome) {
            is McpToolResult.Ok -> result(id, toolResult(outcome.json, isError = false))
            is McpToolResult.Error -> result(id, toolResult(outcome.code, isError = true))
        }
    }

    private fun toolResult(text: String, isError: Boolean): JsonObject = buildJsonObject {
        put(
            "content",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("type", "text")
                        put("text", text)
                    },
                )
            },
        )
        put("isError", isError)
    }

    private fun result(id: JsonElement, result: JsonObject): String = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        put("result", result)
    }.toString()

    private fun error(id: JsonElement, code: Int, message: String): String = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        put(
            "error",
            buildJsonObject {
                put("code", code)
                put("message", message)
            },
        )
    }.toString()

    /** A complete error envelope for the HTTP layer's refusal path. */
    fun unauthorizedBody(): String = buildJsonObject { put("error", "unauthorized") }.toString()

    fun notFoundBody(): String = buildJsonObject { put("error", "not_found") }.toString()
}
