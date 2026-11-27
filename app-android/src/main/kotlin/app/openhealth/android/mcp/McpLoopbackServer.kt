@file:Suppress("MagicNumber") // HTTP status codes are protocol constants, not values.

package app.openhealth.android.mcp

import app.openhealth.core.ai.McpHttpResponse
import app.openhealth.core.ai.McpProtocol
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * The local MCP endpoint (B15, `pr.md` §6.30). It binds the loopback interface only, so
 * nothing on the network can reach it; the bearer-token check and the scopes are the
 * authorization, and the server stops whenever the person turns it off or the app dies.
 *
 * The handler receives the Authorization header and the body and returns the response;
 * this class performs no logging, so a token or a note body cannot reach logcat.
 */
class McpLoopbackServer(
    private val handler: (authorization: String?, body: String) -> McpHttpResponse,
) {
    private var socket: ServerSocket? = null
    private var thread: Thread? = null

    @Volatile
    var running: Boolean = false
        private set

    /** True when the port was free and the accept loop started. */
    fun start(port: Int): Boolean {
        if (running) return true
        return runCatching {
            val server = ServerSocket(port, BACKLOG, InetAddress.getByName(LOOPBACK))
            socket = server
            running = true
            thread = Thread({ acceptLoop(server) }, "oh-mcp").apply {
                isDaemon = true
                start()
            }
            true
        }.getOrDefault(false)
    }

    fun stop() {
        running = false
        runCatching { socket?.close() }
        socket = null
        thread = null
    }

    private fun acceptLoop(server: ServerSocket) {
        while (running) {
            val client = runCatching { server.accept() }.getOrNull() ?: break
            runCatching { serve(client) }
            runCatching { client.close() }
        }
    }

    @Suppress("ReturnCount")
    private fun serve(client: Socket) {
        client.soTimeout = TIMEOUT_MILLIS
        val input = BufferedInputStream(client.getInputStream())
        val output = client.getOutputStream()
        val requestLine = readLine(input) ?: return
        val parts = requestLine.split(' ')
        if (parts.size < 2) return fail(output, HTTP_BAD_REQUEST)
        val headers = readHeaders(input)
        if (parts[0] != "POST" || parts[1].substringBefore('?') != McpProtocol.PATH) {
            return fail(output, HTTP_NOT_FOUND)
        }
        val length = headers["content-length"]?.toIntOrNull()
            ?: return fail(output, HTTP_LENGTH_REQUIRED)
        if (length <= 0 || length > McpProtocol.MAX_BODY_BYTES) {
            return fail(output, HTTP_PAYLOAD_TOO_LARGE)
        }
        val body = readBody(input, length) ?: return fail(output, HTTP_BAD_REQUEST)
        val response = runCatching { handler(headers["authorization"], body) }
            .getOrElse { McpHttpResponse(HTTP_INTERNAL_ERROR) }
        write(output, response)
    }

    private fun readHeaders(input: InputStream): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        var line = readLine(input)
        while (line != null && line.isNotEmpty()) {
            val colon = line.indexOf(':')
            if (colon > 0) {
                headers[line.substring(0, colon).trim().lowercase()] =
                    line.substring(colon + 1).trim()
            }
            line = readLine(input)
        }
        return headers
    }

    private fun readBody(input: InputStream, length: Int): String? {
        val bytes = ByteArray(length)
        var read = 0
        while (read < length) {
            val count = input.read(bytes, read, length - read)
            if (count < 0) return null
            read += count
        }
        return bytes.toString(Charsets.UTF_8)
    }

    /** Reads one ISO-8859-1 header line without buffering past the body. */
    @Suppress("ReturnCount")
    private fun readLine(input: InputStream): String? {
        val buffer = StringBuilder()
        while (true) {
            val value = input.read()
            if (value < 0) return if (buffer.isEmpty()) null else buffer.toString()
            if (value == '\n'.code) return buffer.toString().trimEnd('\r')
            if (buffer.length > MAX_LINE) return null
            buffer.append(value.toChar())
        }
    }

    private fun fail(output: OutputStream, status: Int) {
        write(output, McpHttpResponse(status, """{"error":"refused"}"""))
    }

    private fun write(output: OutputStream, response: McpHttpResponse) {
        val body = response.body
        val head = buildString {
            append("HTTP/1.1 ").append(response.status).append(' ')
            append(reason(response.status)).append("\r\n")
            append("Content-Type: application/json\r\n")
            append("Content-Length: ")
                .append(body?.toByteArray(Charsets.UTF_8)?.size ?: 0)
                .append("\r\n")
            append("Connection: close\r\n\r\n")
        }
        output.write(head.toByteArray(Charsets.ISO_8859_1))
        body?.let { output.write(it.toByteArray(Charsets.UTF_8)) }
        output.flush()
    }

    private fun reason(status: Int): String = when (status) {
        HTTP_BAD_REQUEST -> "Bad Request"
        HTTP_UNAUTHORIZED -> "Unauthorized"
        HTTP_NOT_FOUND -> "Not Found"
        HTTP_LENGTH_REQUIRED -> "Length Required"
        HTTP_PAYLOAD_TOO_LARGE -> "Payload Too Large"
        HTTP_INTERNAL_ERROR -> "Internal Server Error"
        else -> "OK"
    }

    private companion object {
        const val LOOPBACK = "127.0.0.1"
        const val BACKLOG = 4
        const val TIMEOUT_MILLIS = 10_000
        const val MAX_LINE = 8_192
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_NOT_FOUND = 404
        const val HTTP_LENGTH_REQUIRED = 411
        const val HTTP_PAYLOAD_TOO_LARGE = 413
        const val HTTP_INTERNAL_ERROR = 500
    }
}
