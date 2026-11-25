package app.openhealth.core.seam

/**
 * Platform capability seams. Each is declared here and implemented in `app-android` (or
 * `integrations/android/`), so the domain and storage modules stay free of `android.*`
 * (`Tech-Stack.md` §3). Even the seams Android implements first exist as interfaces.
 */

/** Keys and passphrases. The Android implementation is backed by the Keystore. */
interface SecretVault {
    fun has(name: String): Boolean

    fun get(name: String): ByteArray?

    fun put(name: String, value: ByteArray)

    fun remove(name: String)
}

/** App-private files: exports, database backups, photos. */
interface FileStore {
    fun exists(name: String): Boolean

    fun read(name: String): ByteArray?

    fun write(name: String, bytes: ByteArray)

    fun delete(name: String)

    fun list(prefix: String): List<String>

    fun size(name: String): Long
}

data class NotificationAction(val id: String, val label: String)

data class NotificationSpec(
    val id: String,
    val title: String,
    val body: String,
    val ongoing: Boolean = false,
    val actions: List<NotificationAction> = emptyList(),
    val silent: Boolean = false,
)

/** Local notifications only; there is no push server (`Tech-Stack.md` §8). */
interface NotificationSink {
    fun canPost(): Boolean

    fun post(spec: NotificationSpec)

    fun cancel(id: String)
}

/** One reading offered by the platform health bus, already in a canonical unit. */
data class HealthReading(
    val type: String,
    val value: Double,
    val unit: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long?,
    val metadata: Map<String, String> = emptyMap(),
)

interface HealthBus {
    fun isAvailable(): Boolean

    suspend fun grantedPermissions(): Set<String>

    suspend fun read(type: String, fromEpochMillis: Long, toEpochMillis: Long): List<HealthReading>

    suspend fun write(readings: List<HealthReading>)
}

/** The hardware step counter, independent of the health bus. */
interface StepSensor {
    fun isAvailable(): Boolean

    /** Reboot-safe cumulative count since boot, or null before the first event. */
    fun countSinceBoot(): Long?

    fun start(onCount: (Long) -> Unit): AutoCloseable
}

data class BleDevice(val address: String, val name: String?, val serviceUuids: List<String>)

data class BleFilter(val serviceUuid: String? = null, val namePrefix: String? = null)

interface BleScanner {
    fun isAvailable(): Boolean

    suspend fun scan(filter: BleFilter, timeoutMillis: Long): List<BleDevice>
}

data class CaptureRequest(val purpose: String, val frontFacing: Boolean = false)

data class CapturedImage(
    val bytes: ByteArray,
    val mimeType: String,
    val width: Int,
    val height: Int,
)

interface CameraSeam {
    fun isAvailable(): Boolean

    suspend fun capture(request: CaptureRequest): CapturedImage?
}

/** A neutral, light PDF document; layout is data so the seam stays platform-free. */
data class PdfText(val text: String, val bold: Boolean = false)

data class PdfSection(val heading: String?, val lines: List<PdfText>)

data class PdfDocumentSpec(val title: String, val sections: List<PdfSection>, val footer: String)

interface PdfSeam {
    fun render(spec: PdfDocumentSpec): ByteArray
}
