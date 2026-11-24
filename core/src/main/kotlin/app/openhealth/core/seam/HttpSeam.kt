package app.openhealth.core.seam

/**
 * The one way a feature may leave the process (`Tech-Stack.md` §3, `pr.md` §6.23). A
 * feature that needs a network call declares its request here and asks the person before
 * the first call; nothing calls this from a background task.
 */
data class HttpRequest(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val accept: String = "application/json",
)

data class HttpResponse(val status: Int, val body: String)

interface HttpTransport {
    /** Performs a GET. A transport failure throws; a protocol answer is returned. */
    suspend fun get(request: HttpRequest): HttpResponse
}
