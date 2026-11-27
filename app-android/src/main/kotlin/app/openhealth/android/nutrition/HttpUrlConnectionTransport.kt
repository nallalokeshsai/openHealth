package app.openhealth.android.nutrition

import app.openhealth.core.seam.HttpRequest
import app.openhealth.core.seam.HttpResponse
import app.openhealth.core.seam.HttpTimeoutException
import app.openhealth.core.seam.HttpTransport
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The app's outbound transport. It is used by the explicit Open Food Facts lookup
 * (`pr.md` §6.23) and by the coach's provider adapter (B15): no background task, no retry,
 * no cookie jar, and nothing logged.
 */
class HttpUrlConnectionTransport : HttpTransport {
    override suspend fun get(request: HttpRequest): HttpResponse = send(request)

    override suspend fun post(request: HttpRequest): HttpResponse = send(request)

    private suspend fun send(request: HttpRequest): HttpResponse = withContext(Dispatchers.IO) {
        val connection = URL(request.url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = request.method
            connection.connectTimeout = request.timeoutMillis
            connection.readTimeout = request.timeoutMillis
            // A redirect on a POST could carry an API key to another host; follow only
            // the barcode GET, and the provider adapters treat a 3xx as a failure.
            connection.instanceFollowRedirects = request.method == "GET"
            connection.setRequestProperty("Accept", request.accept)
            request.headers.forEach { (name, value) ->
                connection.setRequestProperty(name, value)
            }
            request.contentType?.let { connection.setRequestProperty("Content-Type", it) }
            request.body?.let { body ->
                connection.doOutput = true
                connection.outputStream.use { it.write(body.encodeToByteArray()) }
            }
            val status = connection.responseCode
            val stream = if (status in HTTP_OK..HTTP_LAST_OK) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            HttpResponse(status, body)
        } catch (timeout: SocketTimeoutException) {
            throw HttpTimeoutException(timeout)
        } catch (timeout: InterruptedIOException) {
            throw HttpTimeoutException(timeout)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_LAST_OK = 299
    }
}
