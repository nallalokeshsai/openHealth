package app.openhealth.server

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Where an account's media blobs (the end-to-end encrypted body photos) are kept. The
 * server relays opaque bytes: it never decrypts, resizes or inspects them, and a blob is
 * only ever readable by the account that wrote it.
 */
interface MediaStore {
    fun put(accountId: String, id: String, bytes: ByteArray)

    fun get(accountId: String, id: String): ByteArray?
}

class InMemoryMediaStore : MediaStore {
    private val blobs = linkedMapOf<String, ByteArray>()

    @Synchronized
    override fun put(accountId: String, id: String, bytes: ByteArray) {
        blobs["$accountId/$id"] = bytes
    }

    @Synchronized
    override fun get(accountId: String, id: String): ByteArray? = blobs["$accountId/$id"]
}

/**
 * S3 and MinIO through plain HTTPS with an AWS Signature V4 header, path-style. It carries
 * no SDK, so the server image gains no dependency (and no telemetry) for two verbs.
 */
@Suppress("TooManyFunctions", "MagicNumber", "LongParameterList")
class S3MediaStore(
    endpoint: String,
    private val bucket: String,
    private val accessKey: String,
    private val secretKey: String,
    private val region: String = "us-east-1",
    private val client: HttpClient = HttpClient.newHttpClient(),
    private val clock: () -> ZonedDateTime = { ZonedDateTime.now(ZoneOffset.UTC) },
) : MediaStore {
    private val base: URI = URI.create(endpoint.trimEnd('/'))

    override fun put(accountId: String, id: String, bytes: ByteArray) {
        val response = send("PUT", accountId, id, bytes)
        check(response.statusCode() in 200..299) { "media store refused the write" }
    }

    override fun get(accountId: String, id: String): ByteArray? {
        val response = send("GET", accountId, id, ByteArray(0))
        return when (response.statusCode()) {
            404 -> null
            in 200..299 -> response.body()
            else -> error("media store could not read the blob")
        }
    }

    private fun send(
        method: String,
        accountId: String,
        id: String,
        body: ByteArray,
    ): HttpResponse<ByteArray> {
        val path = "/$bucket/$accountId/$id"
        val uri = URI.create("${base.scheme}://${base.authority}$path")
        val now = clock()
        val stamp = now.format(AMZ_DATE)
        val payloadHash = hex(sha256(body))
        val host = base.authority
        val signed = "host;x-amz-content-sha256;x-amz-date"
        val canonical = listOf(
            method,
            path,
            "",
            "host:$host\nx-amz-content-sha256:$payloadHash\nx-amz-date:$stamp\n",
            signed,
            payloadHash,
        ).joinToString("\n")
        val day = now.format(DAY)
        val scope = "$day/$region/s3/aws4_request"
        val toSign = "AWS4-HMAC-SHA256\n$stamp\n$scope\n${hex(sha256(canonical.toByteArray()))}"
        val signature = hex(hmac(signingKey(day), toSign))
        val authorization = "AWS4-HMAC-SHA256 Credential=$accessKey/$scope, " +
            "SignedHeaders=$signed, Signature=$signature"
        val request = HttpRequest.newBuilder(uri)
            .method(method, HttpRequest.BodyPublishers.ofByteArray(body))
            .header("x-amz-date", stamp)
            .header("x-amz-content-sha256", payloadHash)
            .header("Authorization", authorization)
            .build()
        return client.send(request, HttpResponse.BodyHandlers.ofByteArray())
    }

    private fun signingKey(day: String): ByteArray {
        val date = hmac("AWS4$secretKey".toByteArray(), day)
        val regionKey = hmac(date, region)
        val service = hmac(regionKey, "s3")
        return hmac(service, "aws4_request")
    }

    private fun hmac(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray())
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private companion object {
        val AMZ_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
        val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    }
}
