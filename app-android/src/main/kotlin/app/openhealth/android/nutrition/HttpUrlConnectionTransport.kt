package app.openhealth.android.nutrition

import app.openhealth.core.seam.HttpRequest
import app.openhealth.core.seam.HttpResponse
import app.openhealth.core.seam.HttpTransport
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The app's one outbound transport. It is used by nothing but the explicit Open Food
 * Facts barcode lookup (`pr.md` §6.23): no background task, no retry, no cookie jar.
 */
class HttpUrlConnectionTransport : HttpTransport {
    override suspend fun get(request: HttpRequest): HttpResponse = withContext(Dispatchers.IO) {
        val connection = URL(request.url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", request.accept)
            request.headers.forEach { (name, value) ->
                connection.setRequestProperty(name, value)
            }
            val status = connection.responseCode
            val stream = if (status in HTTP_OK..HTTP_LAST_OK) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            HttpResponse(status, body)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 8_000
        const val HTTP_OK = 200
        const val HTTP_LAST_OK = 299
    }
}
