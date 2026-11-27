package app.openhealth.core.activity

import app.openhealth.core.ids.IdGenerator
import app.openhealth.core.ids.ObservationId
import app.openhealth.core.ids.RawRecordId
import app.openhealth.core.ids.SourceId
import app.openhealth.core.observation.MeasurementMethod
import app.openhealth.core.observation.Observation
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.observation.Provenance
import app.openhealth.core.seam.HealthReading
import app.openhealth.core.time.TimeProvenance
import app.openhealth.core.units.Measure
import app.openhealth.core.units.UcumUnit
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

/**
 * The canonical vocabulary of the health bus (`Tech-Stack.md` §8). It is a closed set of
 * keys: the platform implementation maps its records to these strings and the domain
 * maps them back to an [ObservationType], so `:core` never sees `androidx.health`.
 */
object HealthBusTypes {
    const val STEPS = "steps"
    const val DISTANCE = "distance"
    const val ACTIVE_CALORIES = "active_calories"
    const val ACTIVE_MINUTES = "active_minutes"
    const val FLOORS = "floors"
    const val HEART_RATE = "heart_rate"
    const val RESTING_HEART_RATE = "resting_heart_rate"
    const val SLEEP = "sleep"
    const val SLEEP_DEEP = "sleep.deep"
    const val SLEEP_LIGHT = "sleep.light"
    const val SLEEP_REM = "sleep.rem"
    const val SLEEP_AWAKE = "sleep.awake"
    const val SLEEP_IN_BED = "sleep.in_bed"
    const val EXERCISE = "exercise"
    const val WEIGHT = "weight"
    const val BODY_FAT = "body_fat"

    /** Every type this app can read. A key outside this list is ignored, never guessed. */
    val all: List<String> = listOf(
        STEPS,
        DISTANCE,
        ACTIVE_CALORIES,
        ACTIVE_MINUTES,
        FLOORS,
        HEART_RATE,
        RESTING_HEART_RATE,
        SLEEP,
        SLEEP_DEEP,
        SLEEP_LIGHT,
        SLEEP_REM,
        SLEEP_AWAKE,
        SLEEP_IN_BED,
        EXERCISE,
        WEIGHT,
        BODY_FAT,
    )

    /**
     * The observation a bus key becomes, or null when the key is not one we read.
     *
     * Every sleep key keeps a type of its own. Collapsing them into `SLEEP_DURATION`
     * would land one night's stages and that same night's total in one SUM rollup, so
     * a staged night would be counted about twice (`pr.md` §7.6).
     */
    fun observationType(key: String): ObservationType? = OBSERVATION_TYPES[key]

    private val OBSERVATION_TYPES: Map<String, ObservationType> = mapOf(
        STEPS to ObservationType.STEPS,
        DISTANCE to ObservationType.DISTANCE,
        ACTIVE_CALORIES to ObservationType.ACTIVE_CALORIES,
        ACTIVE_MINUTES to ObservationType.ACTIVE_MINUTES,
        FLOORS to ObservationType.FLIGHTS_CLIMBED,
        HEART_RATE to ObservationType.HEART_RATE,
        RESTING_HEART_RATE to ObservationType.RESTING_HEART_RATE,
        SLEEP to ObservationType.SLEEP_DURATION,
        SLEEP_DEEP to ObservationType.SLEEP_DEEP,
        SLEEP_LIGHT to ObservationType.SLEEP_LIGHT,
        SLEEP_REM to ObservationType.SLEEP_REM,
        SLEEP_AWAKE to ObservationType.SLEEP_AWAKE,
        SLEEP_IN_BED to ObservationType.SLEEP_IN_BED,
        EXERCISE to ObservationType.ACTIVE_MINUTES,
        WEIGHT to ObservationType.WEIGHT,
        BODY_FAT to ObservationType.BODY_FAT_PERCENT,
    )

    /** The unit a bus reading of this key arrives in, as UCUM. */
    fun unit(key: String): UcumUnit? = when (key) {
        STEPS -> UcumUnit.STEP
        DISTANCE -> UcumUnit.METRE
        ACTIVE_CALORIES -> UcumUnit.KILOCALORIE
        ACTIVE_MINUTES, EXERCISE -> UcumUnit.MINUTE
        FLOORS -> UcumUnit.COUNT
        HEART_RATE, RESTING_HEART_RATE -> UcumUnit.PER_MINUTE
        SLEEP, SLEEP_DEEP, SLEEP_LIGHT, SLEEP_REM, SLEEP_AWAKE, SLEEP_IN_BED -> UcumUnit.SECOND
        WEIGHT -> UcumUnit.KILOGRAM
        BODY_FAT -> UcumUnit.PERCENT
        else -> null
    }

    /** True for the keys whose value is a start and an end, not a moment. */
    fun isInterval(key: String): Boolean = key in setOf(
        STEPS,
        DISTANCE,
        ACTIVE_CALORIES,
        ACTIVE_MINUTES,
        FLOORS,
        SLEEP,
        SLEEP_DEEP,
        SLEEP_LIGHT,
        SLEEP_REM,
        SLEEP_AWAKE,
        SLEEP_IN_BED,
        EXERCISE,
    )
}

/** How the bus reader should be asked for its data. */
data class HealthBusRequest(
    val fromEpochMillis: Long,
    val toEpochMillis: Long,
    val types: List<String> = HealthBusTypes.all,
)

/**
 * The capabilities the permission screen lists, in the order a person reads them. The
 * platform's permission strings live in the Android implementation, because they are
 * platform vocabulary; the domain only names the capability.
 */
data class HealthBusCapability(val key: String, val history: Boolean = false)

object HealthBusCapabilities {
    /** The read capabilities the permission screen asks for, in a stable order. */
    val all: List<HealthBusCapability> = listOf(
        HealthBusCapability(HealthBusTypes.STEPS),
        HealthBusCapability(HealthBusTypes.DISTANCE),
        HealthBusCapability(HealthBusTypes.ACTIVE_CALORIES),
        HealthBusCapability(HealthBusTypes.FLOORS),
        HealthBusCapability(HealthBusTypes.HEART_RATE),
        HealthBusCapability(HealthBusTypes.RESTING_HEART_RATE),
        HealthBusCapability(HealthBusTypes.SLEEP),
        HealthBusCapability(HealthBusTypes.EXERCISE),
        HealthBusCapability(HealthBusTypes.WEIGHT),
        HealthBusCapability(HealthBusTypes.BODY_FAT),
    )

    val readKeys: List<String> = all.map { it.key }
}

/** Where a batch of bus readings came from, and when this app learned about them. */
data class HealthImportContext(
    val sourceId: SourceId,
    val zone: TimeZone,
    val recordedAt: Instant,
    val device: String? = null,
    val appVersion: String? = null,
    val rawRecordId: RawRecordId? = null,
    val ids: IdGenerator,
)

data class HealthImportResult(
    val observations: List<Observation>,
    val skipped: List<String> = emptyList(),
)

/** The zone a reading was taken in when the platform says so, else the device's own. */
private fun zoneOf(reading: HealthReading, fallback: TimeZone): TimeZone =
    reading.metadata["zoneOffsetSeconds"]?.toIntOrNull()
        ?.let {
            runCatching {
                kotlinx.datetime.FixedOffsetTimeZone(kotlinx.datetime.UtcOffset(seconds = it))
            }.getOrNull()
        }
        ?: fallback

/**
 * Turns health-bus readings into layer-2 observations (`pr.md` §7.2). A reading keeps
 * its own start and end, so an hour of steps stays an hour of steps; `happened_at` is
 * the reading's start and `recorded_at` is when this app imported it, which is never
 * moved back (`pr.md` §6.27). Nothing here decides which source wins a day: that is the
 * rollup's job and it needs both sources to exist (`pr.md` §7.6).
 */
object HealthBusImporter {
    fun toObservations(
        readings: List<HealthReading>,
        context: HealthImportContext,
    ): HealthImportResult {
        val observations = mutableListOf<Observation>()
        val skipped = mutableListOf<String>()
        readings.forEach { reading ->
            val mapped = toObservation(reading, context)
            if (mapped == null) {
                skipped += reading.type
            } else {
                observations += mapped
            }
        }
        return HealthImportResult(observations = observations, skipped = skipped.distinct())
    }

    @Suppress("ReturnCount")
    private fun toObservation(reading: HealthReading, context: HealthImportContext): Observation? {
        val type = HealthBusTypes.observationType(reading.type) ?: return null
        val unit = unitOf(reading) ?: return null
        if (!reading.value.isFinite() || reading.value < 0) return null
        val start = Instant.fromEpochMilliseconds(reading.startEpochMillis)
        val end = reading.endEpochMillis
            ?.takeIf { it > reading.startEpochMillis }
            ?.let(Instant::fromEpochMilliseconds)
        val method = methodOf(reading.type)
        val provenance = Provenance(
            sourceId = context.sourceId,
            time = TimeProvenance.of(start, context.recordedAt, zoneOf(reading, context.zone)),
            idempotencyKey = idempotencyKey(reading),
            device = reading.metadata["device"] ?: context.device,
            appVersion = context.appVersion,
            method = method,
            rawRecordId = context.rawRecordId,
        )
        val id = ObservationId(context.ids.newId())
        return Observation(
            id = id,
            type = type,
            value = Measure(reading.value, unit).convertTo(type.canonicalUnit),
            start = start,
            end = end,
            provenance = provenance,
            groupKey = reading.metadata["group"],
            excludedFromTrends = false,
        )
    }

    /**
     * A reading's identity is its source, its type, its start and the value; the platform
     * may hand the same record back on two syncs, and one of those must not count twice.
     */
    private fun idempotencyKey(reading: HealthReading): String {
        val value = reading.value.toString()
        return "hc:${reading.type}:${reading.startEpochMillis}:${reading.endEpochMillis}:$value:" +
            (reading.metadata["uid"] ?: "")
    }

    private fun unitOf(reading: HealthReading): UcumUnit? {
        val declared = HealthBusTypes.unit(reading.type) ?: return null
        val parsed = runCatching { UcumUnit.byUcum(reading.unit, declared.dimension) }
            .getOrNull()
        return parsed ?: declared
    }

    private fun methodOf(key: String): MeasurementMethod = when (key) {
        HealthBusTypes.WEIGHT, HealthBusTypes.BODY_FAT -> MeasurementMethod.SCALE
        HealthBusTypes.HEART_RATE, HealthBusTypes.RESTING_HEART_RATE,
        HealthBusTypes.SLEEP,
        HealthBusTypes.SLEEP_DEEP,
        HealthBusTypes.SLEEP_LIGHT,
        HealthBusTypes.SLEEP_REM,
        HealthBusTypes.SLEEP_AWAKE,
        HealthBusTypes.SLEEP_IN_BED,
        -> MeasurementMethod.WATCH_OPTICAL

        HealthBusTypes.EXERCISE -> MeasurementMethod.DEVICE_SENSOR
        else -> MeasurementMethod.DEVICE_SENSOR
    }

    /** A single consent sentence the rationale screen prints, with no platform detail. */
    fun describe(key: String): String = when (key) {
        HealthBusTypes.STEPS -> "step counts"
        HealthBusTypes.DISTANCE -> "distance walked"
        HealthBusTypes.ACTIVE_CALORIES -> "calories burned"
        HealthBusTypes.ACTIVE_MINUTES -> "active minutes"
        HealthBusTypes.FLOORS -> "floors climbed"
        HealthBusTypes.HEART_RATE -> "heart rate"
        HealthBusTypes.RESTING_HEART_RATE -> "resting heart rate"
        HealthBusTypes.SLEEP -> "sleep"
        HealthBusTypes.EXERCISE -> "workouts"
        HealthBusTypes.WEIGHT -> "weight"
        HealthBusTypes.BODY_FAT -> "body fat"
        else -> "no data"
    }
}
