@file:Suppress(
    "ReturnCount",
    "MagicNumber",
)

package app.openhealth.core.activity

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One location fix as the platform seam reports it. [accuracyM] is used to reject a
 * useless fix; [paused] is never a property of a fix.
 */
data class GpsFix(
    val latitude: Double,
    val longitude: Double,
    val timeEpochMillis: Long,
    val accuracyM: Double? = null,
    val elevationM: Double? = null,
    val speedMps: Double? = null,
    val heartRate: Double? = null,
    val cadence: Double? = null,
    val powerWatts: Double? = null,
)

/** The persisted shape of a recording in progress. It is JSON so a process death is cheap. */
@Serializable
data class GpsSnapshot(
    val id: String,
    val typeKey: String,
    val startedAtEpochMillis: Long,
    val zoneId: String,
    val localDate: String,
    val paused: Boolean = false,
    val pausedSinceEpochMillis: Long? = null,
    val pausedTotalMillis: Long = 0,
    val lastFixEpochMillis: Long? = null,
    val distanceM: Double = 0.0,
    val points: List<GpsTrackPoint> = emptyList(),
)

@Serializable
data class GpsTrackPoint(
    val latitude: Double,
    val longitude: Double,
    val elapsedSeconds: Long,
    val elevationM: Double? = null,
    val heartRate: Double? = null,
    val cadence: Double? = null,
    val powerWatts: Double? = null,
    val speedMps: Double? = null,
)

/**
 * The recording state machine (`pr.md` §6.11: a recorded activity survives a crash and
 * can be recovered). It is pure and deterministic: the service gives it a fix and the
 * wall clock, and it returns the next persisted snapshot. Paused time is excluded from
 * the elapsed total; the points already taken are never dropped.
 */
object GpsRecorder {
    private const val MAX_ACCURACY_M = 60.0
    private const val MAX_PLAUSIBLE_SPEED_MPS = 30.0
    private const val MIN_POINT_GAP_MS = 1_000L
    private const val MILLIS_PER_SECOND = 1_000.0

    fun start(
        id: String,
        type: ActivityType,
        startedAt: Instant,
        zoneId: String,
        localDate: LocalDate,
    ): GpsSnapshot = GpsSnapshot(
        id = id,
        typeKey = type.name,
        startedAtEpochMillis = startedAt.toEpochMilliseconds(),
        zoneId = zoneId,
        localDate = localDate.toString(),
    )

    fun addFix(snapshot: GpsSnapshot, fix: GpsFix): GpsSnapshot {
        if (snapshot.paused) return snapshot
        if (fix.accuracyM != null && fix.accuracyM > MAX_ACCURACY_M) return snapshot
        val lastPoint = snapshot.points.lastOrNull()
        if (lastPoint != null) {
            val gap = snapshot.lastFixEpochMillis?.let {
                fix.timeEpochMillis - it
            } ?: Long.MAX_VALUE
            if (gap < MIN_POINT_GAP_MS) return snapshot
            val metres = Geo.distanceMeters(
                lastPoint.latitude,
                lastPoint.longitude,
                fix.latitude,
                fix.longitude,
            )
            val seconds = (gap / MILLIS_PER_SECOND).coerceAtLeast(1.0)
            if (metres / seconds > MAX_PLAUSIBLE_SPEED_MPS) return snapshot
        }
        val elapsed = elapsedSeconds(snapshot, fix.timeEpochMillis)
        val point = GpsTrackPoint(
            latitude = fix.latitude,
            longitude = fix.longitude,
            elapsedSeconds = elapsed,
            elevationM = fix.elevationM,
            heartRate = fix.heartRate,
            cadence = fix.cadence,
            powerWatts = fix.powerWatts,
            speedMps = fix.speedMps,
        )
        val addedDistance = lastPoint?.let {
            Geo.distanceMeters(it.latitude, it.longitude, fix.latitude, fix.longitude)
        } ?: 0.0
        return snapshot.copy(
            points = snapshot.points + point,
            distanceM = snapshot.distanceM + addedDistance,
            lastFixEpochMillis = fix.timeEpochMillis,
        )
    }

    fun pause(snapshot: GpsSnapshot, nowEpochMillis: Long): GpsSnapshot = if (snapshot.paused) {
        snapshot
    } else {
        snapshot.copy(
            paused = true,
            pausedSinceEpochMillis = nowEpochMillis,
            lastFixEpochMillis = null,
        )
    }

    fun resume(snapshot: GpsSnapshot, nowEpochMillis: Long): GpsSnapshot {
        if (!snapshot.paused) return snapshot
        val pausedFor = (nowEpochMillis - (snapshot.pausedSinceEpochMillis ?: nowEpochMillis))
            .coerceAtLeast(0)
        return snapshot.copy(
            paused = false,
            pausedSinceEpochMillis = null,
            pausedTotalMillis = snapshot.pausedTotalMillis + pausedFor,
        )
    }

    /**
     * Closes the recording. [nowEpochMillis] is the finish time; a paused recording ends
     * at the moment it was paused, so the pause never becomes activity time.
     */
    fun finish(snapshot: GpsSnapshot, nowEpochMillis: Long): ActivityRecord {
        val ended = if (snapshot.paused) {
            snapshot.pausedSinceEpochMillis ?: nowEpochMillis
        } else {
            nowEpochMillis
        }
        val started = Instant.fromEpochMilliseconds(snapshot.startedAtEpochMillis)
        val type = ActivityType.fromKey(snapshot.typeKey) ?: ActivityType.OTHER
        val points = snapshot.points.map { point ->
            ActivityPoint(
                latitude = point.latitude,
                longitude = point.longitude,
                elapsedSeconds = point.elapsedSeconds,
                elevationM = point.elevationM,
                heartRate = point.heartRate,
                cadence = point.cadence,
                powerWatts = point.powerWatts,
                speedMps = point.speedMps,
            )
        }
        val heartRates = points.mapNotNull { it.heartRate }.filter { it > 0.0 }
        val cadences = points.mapNotNull { it.cadence }.filter { it > 0.0 }
        val powers = points.mapNotNull { it.powerWatts }.filter { it > 0.0 }
        return ActivityRecord(
            id = snapshot.id,
            type = type,
            source = ActivitySource.RECORDED,
            startedAt = started,
            endedAt = Instant.fromEpochMilliseconds(ended),
            localDate = LocalDate.parse(snapshot.localDate),
            zoneId = snapshot.zoneId,
            distanceM = snapshot.distanceM,
            elevationGainM = Geo.elevationGainMeters(points),
            averageHeartRate = if (heartRates.isEmpty()) null else heartRates.average(),
            maxHeartRate = heartRates.maxOrNull(),
            averageCadence = if (cadences.isEmpty()) null else cadences.average(),
            averagePowerWatts = if (powers.isEmpty()) null else powers.average(),
            points = points,
        )
    }

    /** Live elapsed seconds, excluding pauses, for the notification and the screen. */
    fun elapsedSeconds(snapshot: GpsSnapshot, nowEpochMillis: Long): Long {
        val pausedNow = if (snapshot.paused) {
            (nowEpochMillis - (snapshot.pausedSinceEpochMillis ?: nowEpochMillis)).coerceAtLeast(0)
        } else {
            0L
        }
        val total = nowEpochMillis - snapshot.startedAtEpochMillis -
            snapshot.pausedTotalMillis - pausedNow
        return (total / 1_000L).coerceAtLeast(0L)
    }

    fun toGpsSnapshot(json: String): GpsSnapshot? = runCatching {
        GpsSnapshotCodec.decode(json)
    }.getOrNull()
}

/** JSON for the recording snapshot; unknown keys from a newer build are ignored. */
object GpsSnapshotCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(snapshot: GpsSnapshot): String =
        json.encodeToString(GpsSnapshot.serializer(), snapshot)

    fun decode(text: String): GpsSnapshot = json.decodeFromString(GpsSnapshot.serializer(), text)
}

/** Storage for the in-progress snapshot, implemented by the platform service. */
interface GpsRecordingStore {
    fun load(): String?
    fun save(json: String)
    fun clear()
}
