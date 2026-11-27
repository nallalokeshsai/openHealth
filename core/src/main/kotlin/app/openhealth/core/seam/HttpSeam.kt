package app.openhealth.core.seam

/**
 * The one way a feature may leave the process (`Tech-Stack.md` §3, `pr.md` §6.23). A
 * feature that needs a network call declares its request here and asks the person before
 * the first call; nothing calls this from a background task.
 *
 * A POST is used only by the AI coach's provider adapter (B15). The body is supplied by
 * the caller and the transport never logs a header or a body.
 */
data class HttpRequest(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val accept: String = "application/json",
    val method: String = "GET",
    val body: String? = null,
    val contentType: String? = null,
    /** Per-call timeout. The default is the barcode lookup's; a model call asks longer. */
    val timeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
) {
    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 8_000
    }
}

data class HttpResponse(val status: Int, val body: String)

/** A transport timeout, distinct from a host that could not be reached. */
class HttpTimeoutException(cause: Throwable? = null) : Exception(cause)

interface HttpTransport {
    /** Performs a GET. A transport failure throws; a protocol answer is returned. */
    suspend fun get(request: HttpRequest): HttpResponse

    /**
     * Performs a POST with [HttpRequest.body]. Also throws on a transport failure, so a
     * caller can tell "could not reach" from "reached and refused".
     */
    suspend fun post(request: HttpRequest): HttpResponse
}
