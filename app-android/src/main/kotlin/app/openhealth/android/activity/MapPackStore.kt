@file:Suppress(
    "LongMethod",
    "CyclomaticComplexMethod",
    "ReturnCount",
    "MagicNumber",
)

package app.openhealth.android.activity

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One PMTiles map pack in app-private storage (`Tech-Stack.md` §8, B11). A pack is
 * downloaded only on an explicit action, over HTTPS, capped at 50 MB, never in the
 * background. The archive's first seven bytes must be `PMTiles`, so an HTML error page is
 * refused rather than installed. The pack can be deleted at any time.
 */
class MapPackStore(private val context: Context) {
    private val directory: File get() = File(context.filesDir, DIRECTORY).apply { mkdirs() }

    fun list(): List<MapPack> = directory.listFiles()
        .orEmpty()
        .filter { it.isFile && it.name.endsWith(EXTENSION) }
        .sortedBy { it.name }
        .map { file ->
            MapPack(
                name = file.name.removeSuffix(EXTENSION),
                path = file.absolutePath,
                bytes = file.length(),
                modifiedAtMillis = file.lastModified(),
                bounds = readBounds(file),
            )
        }

    fun path(name: String): String? {
        val file = fileFor(name)
        return if (file.isFile && file.length() > 0) file.absolutePath else null
    }

    fun delete(name: String): Boolean = fileFor(name).delete()

    suspend fun download(url: String, name: String, onProgress: (Float) -> Unit): Result<MapPack> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!url.startsWith("https://", ignoreCase = true)) {
                    throw MapPackException("Only an HTTPS address can be downloaded")
                }
                val safeName = sanitise(name)
                val target = fileFor(safeName)
                val temporary = File(directory, "$safeName.part")
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = CONNECT_TIMEOUT_MILLIS
                    readTimeout = READ_TIMEOUT_MILLIS
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Accept", "application/octet-stream")
                    instanceFollowRedirects = true
                }
                try {
                    val status = connection.responseCode
                    if (status !in 200..299) {
                        throw MapPackException("The pack server answered $status")
                    }
                    val length = connection.contentLengthLong
                    if (length > MAX_BYTES) throw MapPackException("The pack is larger than 50 MB")
                    var total = 0L
                    connection.inputStream.use { input ->
                        FileOutputStream(temporary).use { output ->
                            val buffer = ByteArray(BUFFER_BYTES)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                total += read
                                if (total > MAX_BYTES) {
                                    throw MapPackException("The pack is larger than 50 MB")
                                }
                                output.write(buffer, 0, read)
                                if (length >
                                    0
                                ) {
                                    onProgress((total.toFloat() / length).coerceIn(0f, 1f))
                                }
                            }
                        }
                    }
                    if (!isPmtiles(temporary)) {
                        temporary.delete()
                        throw MapPackException("That file is not a PMTiles archive")
                    }
                    if (target.exists()) target.delete()
                    if (!temporary.renameTo(target)) {
                        throw MapPackException("The pack could not be stored")
                    }
                    onProgress(1f)
                    MapPack(
                        name = safeName,
                        path = target.absolutePath,
                        bytes = target.length(),
                        modifiedAtMillis = target.lastModified(),
                    )
                } finally {
                    connection.disconnect()
                    if (temporary.exists() && !temporary.name.endsWith(".pmtiles")) {
                        temporary.delete()
                    }
                }
            }
        }

    private fun fileFor(name: String): File = File(directory, sanitise(name) + EXTENSION)

    private fun sanitise(name: String): String {
        val clean = name.trim().replace(Regex("[^A-Za-z0-9._-]"), "-")
        return clean.takeIf { it.isNotBlank() } ?: "map"
    }

    /**
     * The archive header's bounding box. The map uses it to choose the local pack only
     * when it actually covers the route; a pack for another region would render black.
     */
    private fun readBounds(file: File): MapPackBounds? = runCatching {
        if (file.length() < HEADER_BYTES) return null
        val header = ByteArray(HEADER_BYTES)
        file.inputStream().use { input ->
            if (input.read(header) != HEADER_BYTES) return null
        }
        if (!header.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) return null
        fun int32(offset: Int): Int = (header[offset].toInt() and 0xFF) or
            ((header[offset + 1].toInt() and 0xFF) shl 8) or
            ((header[offset + 2].toInt() and 0xFF) shl 16) or
            ((header[offset + 3].toInt() and 0xFF) shl 24)
        MapPackBounds(
            minLatitude = int32(106) / PMTILES_EPOCH,
            minLongitude = int32(102) / PMTILES_EPOCH,
            maxLatitude = int32(114) / PMTILES_EPOCH,
            maxLongitude = int32(110) / PMTILES_EPOCH,
        )
    }.getOrNull()

    private fun isPmtiles(file: File): Boolean {
        if (!file.isFile || file.length() < MAGIC.size) return false
        file.inputStream().use { input ->
            val bytes = ByteArray(MAGIC.size)
            if (input.read(bytes) != bytes.size) return false
            return bytes.contentEquals(MAGIC)
        }
    }

    private companion object {
        const val DIRECTORY = "map-packs"
        const val EXTENSION = ".pmtiles"
        const val MAX_BYTES = 50L * 1024 * 1024
        const val HEADER_BYTES = 127
        const val PMTILES_EPOCH = 10_000_000.0
        const val BUFFER_BYTES = 8_192
        const val CONNECT_TIMEOUT_MILLIS = 15_000
        const val READ_TIMEOUT_MILLIS = 30_000
        const val USER_AGENT = "openHealth/1.0 (Android; PMTiles map pack)"
        val MAGIC = "PMTiles".toByteArray(Charsets.US_ASCII)
    }
}

data class MapPack(
    val name: String,
    val path: String,
    val bytes: Long,
    val modifiedAtMillis: Long,
    val bounds: MapPackBounds? = null,
)

/** The PMTiles header's bounding box, in degrees. */
data class MapPackBounds(
    val minLatitude: Double,
    val minLongitude: Double,
    val maxLatitude: Double,
    val maxLongitude: Double,
) {
    fun covers(latitude: Double, longitude: Double): Boolean =
        latitude in minLatitude..maxLatitude && longitude in minLongitude..maxLongitude
}

class MapPackException(message: String) : IllegalArgumentException(message)
