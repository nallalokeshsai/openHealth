package app.openhealth.integrations.fit

import app.openhealth.core.ids.IdGenerator
import app.openhealth.core.ids.ObservationId
import app.openhealth.core.ids.RawRecordId
import app.openhealth.core.ids.SourceId
import app.openhealth.core.observation.MeasurementMethod
import app.openhealth.core.observation.Observation
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.observation.Provenance
import app.openhealth.core.time.TimeProvenance
import app.openhealth.core.units.Measure
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

/** One training session in a FIT file, in the app's vocabulary. */
private const val SECONDS_PER_MINUTE = 60.0

data class FitActivity(
    val start: Instant,
    val end: Instant,
    val sport: Long?,
    val subSport: Long?,
    val name: String?,
    val distanceMetres: Double?,
    val calories: Double?,
    val averageHeartRate: Int?,
    val maximumHeartRate: Int?,
    val averageCadence: Int?,
    val steps: Long?,
) {
    val durationMinutes: Double get() = (end - start).inWholeSeconds / SECONDS_PER_MINUTE
}

data class FitHeartRateSample(val at: Instant, val bpm: Int)

data class FitStepBucket(val start: Instant, val end: Instant, val steps: Long)

/** Where a FIT file's steps came from; a file is a day file or an activity file. */
enum class FitStepSource { MONITORING, ACTIVITY, NONE }

data class FitImport(
    val messageCount: Int,
    val activities: List<FitActivity>,
    val heartRates: List<FitHeartRateSample>,
    val steps: List<FitStepBucket>,
    val stepSource: FitStepSource,
    val device: String?,
    val manufacturer: Long?,
    val profileVersion: Int,
) {
    val stepTotal: Long get() = steps.sumOf { it.steps }
    val isEmpty: Boolean
        get() = activities.isEmpty() && heartRates.isEmpty() && steps.isEmpty()
}

/**
 * Reads a FIT file into activities, heart-rate samples and steps. It never sums a
 * monitoring total with an activity's cycles: a daily file's `monitoring` messages are
 * the day's steps, and an activity file's session cycles are used only when the file has
 * no monitoring messages at all. That is the one place a Garmin file could double-count
 * its own day, so the rule is explicit and tested.
 *
 * `pr.md` §6.18: the import is model-independent — any Garmin, or any device that writes
 * FIT. Garmin's manufacturer id is 1; the label is a device hint, never a source change.
 */
object FitImporter {
    private const val GARMIN_MANUFACTURER = 1L
    private const val DISTANCE_SCALE = 100.0
    private const val DURATION_SCALE = 1000.0
    private val DEFAULT_BUCKET_MINUTES = 1.minutes
    private const val MILLIS_PER_SECOND = 1000.0
    private const val MIN_PLAUSIBLE_HEART_RATE = 25
    private const val MAX_PLAUSIBLE_HEART_RATE = 250

    fun import(bytes: ByteArray, zone: TimeZone = TimeZone.UTC): FitImport {
        val file = FitDecoder(bytes).decode()
        if (!file.headerCrcValid || !file.fileCrcValid) {
            throw FitFormatException("the FIT checksum does not match; the file is corrupt")
        }
        val activities = file.messagesOf(FitGlobal.SESSION).mapNotNull { session(it) }
        val records = heartRatesFromRecords(file)
        val monitoring = monitoringBuckets(file, zone)
        val monitoringHeartRate = file.messagesOf(FitGlobal.MONITORING).mapNotNull { message ->
            val bpm = message.whole(FitGlobal.Monitoring.HEART_RATE)?.toInt()
                ?: return@mapNotNull null
            val at = message.timestampSeconds ?: return@mapNotNull null
            if (bpm !in MIN_PLAUSIBLE_HEART_RATE..MAX_PLAUSIBLE_HEART_RATE) {
                return@mapNotNull null
            }
            FitHeartRateSample(Instant.fromEpochSeconds(FitTime.unixSeconds(at)), bpm)
        }
        val recordSteps = if (monitoring.isEmpty()) {
            activities.mapNotNull { activity ->
                activity.steps?.takeIf { it > 0 }?.let {
                    FitStepBucket(activity.start, activity.end, it)
                }
            }
        } else {
            emptyList()
        }
        val steps = monitoring.map { FitStepBucket(it.start, it.end, it.steps) } + recordSteps
        val stepSource = when {
            monitoring.isNotEmpty() -> FitStepSource.MONITORING
            recordSteps.isNotEmpty() -> FitStepSource.ACTIVITY
            else -> FitStepSource.NONE
        }
        return FitImport(
            messageCount = file.messages.size,
            activities = activities,
            heartRates = (records + monitoringHeartRate)
                .distinctBy { it.at to it.bpm }
                .sortedBy { it.at },
            steps = steps.sortedBy { it.start },
            stepSource = stepSource,
            device = deviceLabel(file),
            manufacturer = file.fileId?.whole(FitGlobal.FileId.MANUFACTURER),
            profileVersion = file.profileVersion,
        )
    }

    private fun deviceLabel(file: FitFile): String? {
        val id = file.fileId ?: return null
        val manufacturer = id.whole(FitGlobal.FileId.MANUFACTURER)
        val name = id.text(FitGlobal.FileId.PRODUCT_NAME)?.takeIf { it.isNotBlank() }
        return when {
            name != null -> name
            manufacturer == GARMIN_MANUFACTURER -> "Garmin"
            manufacturer != null -> "FIT device $manufacturer"
            else -> null
        }
    }

    private fun session(message: FitMessage): FitActivity? {
        val startSeconds = message.whole(FitGlobal.Session.START_TIME)
            ?: message.timestampSeconds
            ?: return null
        val elapsed = message.scaled(FitGlobal.Session.TOTAL_ELAPSED_TIME, DURATION_SCALE)
            ?: message.scaled(FitGlobal.Session.TOTAL_TIMER_TIME, DURATION_SCALE)
            ?: 0.0
        val start = Instant.fromEpochSeconds(FitTime.unixSeconds(startSeconds))
        val end = start + (elapsed * MILLIS_PER_SECOND).roundToLong().milliseconds
        val sport = message.whole(FitGlobal.Session.SPORT)
        val cycles = message.whole(FitGlobal.Session.TOTAL_CYCLES)
        val steps = if (cycles != null && FitActivityTypes.sportCountsSteps(sport)) {
            // `session.total_cycles` carries no scale and is `total_strides` for a
            // walking or running sport, so a stride is two steps.
            (cycles * FitActivityTypes.STEPS_PER_CYCLE).roundToLong()
        } else {
            null
        }
        val name = message.text(FitGlobal.Session.SPORT_PROFILE_NAME)?.takeIf { it.isNotBlank() }
        return FitActivity(
            start = start,
            end = end,
            sport = sport,
            subSport = message.whole(FitGlobal.Session.SUB_SPORT),
            name = name,
            distanceMetres = message.scaled(FitGlobal.Session.TOTAL_DISTANCE, DISTANCE_SCALE),
            calories = message.whole(FitGlobal.Session.TOTAL_CALORIES)?.toDouble(),
            averageHeartRate = message.whole(FitGlobal.Session.AVG_HEART_RATE)?.toInt(),
            maximumHeartRate = message.whole(FitGlobal.Session.MAX_HEART_RATE)?.toInt(),
            averageCadence = message.whole(FitGlobal.Session.AVG_CADENCE)?.toInt(),
            steps = steps,
        )
    }

    private fun heartRatesFromRecords(file: FitFile): List<FitHeartRateSample> =
        file.messagesOf(FitGlobal.RECORD).mapNotNull { record ->
            val timestamp = record.timestampSeconds ?: return@mapNotNull null
            val bpm = (
                record.whole(FitGlobal.Record.HEART_RATE)
                    ?: record.whole(FitGlobal.Record.WRIST_HEART_RATE)
                    ?: record.whole(FitGlobal.Record.EXTERNAL_HEART_RATE)
                )
                ?.toInt()
                ?: return@mapNotNull null
            if (bpm !in MIN_PLAUSIBLE_HEART_RATE..MAX_PLAUSIBLE_HEART_RATE) return@mapNotNull null
            FitHeartRateSample(Instant.fromEpochSeconds(FitTime.unixSeconds(timestamp)), bpm)
        }

    /**
     * A monitoring file reports a delta per message: `cycles` (or the compressed
     * `cycles_16`) belongs to the interval since the previous monitoring message, and
     * its raw value is the interval's step count ([FitActivityTypes.STEPS_PER_CYCLE]).
     * Sampling `active_time` is not needed for steps, so its absence does not lose a
     * bucket.
     */
    private fun monitoringBuckets(file: FitFile, zone: TimeZone): List<FitStepBucket> {
        var previous: Instant? = null
        var previousFitSeconds: Long? = null
        val buckets = mutableListOf<FitStepBucket>()
        file.messagesOf(FitGlobal.MONITORING).forEach { message ->
            val end = monitoringTimestamp(message, zone, previousFitSeconds) ?: return@forEach
            val activityType = message.whole(FitGlobal.Monitoring.ACTIVITY_TYPE)
            val cycles = message.whole(FitGlobal.Monitoring.CYCLES)
                ?: message.whole(FitGlobal.Monitoring.CYCLES_16)
            val start = previous ?: (end - DEFAULT_BUCKET_MINUTES)
            previous = end
            previousFitSeconds = message.timestampSeconds ?: previousFitSeconds
            if (cycles == null || !FitActivityTypes.countsSteps(activityType)) return@forEach
            // Scale 2 takes the raw field to cycles; two steps to the cycle takes it
            // back, so the raw value is the step count. Dividing here would report
            // half the day.
            val steps = (
                cycles / FitActivityTypes.MONITORING_CYCLES_SCALE *
                    FitActivityTypes.STEPS_PER_CYCLE
                ).roundToLong()
            if (steps <= 0) return@forEach
            buckets += FitStepBucket(start, end, steps)
        }
        return buckets
    }

    /**
     * `timestamp`, else the local timestamp, else the 16-bit compressed one. The 16-bit
     * field is a truncated timestamp, not an absolute one: it is resolved against the
     * previous full timestamp the same way a compressed header is (32-second rollover),
     * so it can never be read as a date in 1989.
     */
    @Suppress("ReturnCount")
    private fun monitoringTimestamp(
        message: FitMessage,
        zone: TimeZone,
        previousFitSeconds: Long?,
    ): Instant? {
        message.timestampSeconds?.let { return Instant.fromEpochSeconds(FitTime.unixSeconds(it)) }
        message.whole(FitGlobal.Monitoring.LOCAL_TIMESTAMP)?.let {
            return FitTime.localInstant(it, zone)
        }
        val truncated = message.whole(FitGlobal.Monitoring.TIMESTAMP_16) ?: return null
        val base = previousFitSeconds ?: return null
        var candidate = (base and TIMESTAMP_16_MASK.inv()) or
            (truncated and TIMESTAMP_16_MASK)
        if (candidate < base - TIMESTAMP_16_HALF) candidate += TIMESTAMP_16_ROLLOVER
        return Instant.fromEpochSeconds(FitTime.unixSeconds(candidate))
    }

    private const val TIMESTAMP_16_MASK = 0xFFFFL
    private const val TIMESTAMP_16_ROLLOVER = 0x10000L
    private const val TIMESTAMP_16_HALF = 0x8000L
}

/** Where an import's values came from, so provenance is built the same way as elsewhere. */
data class FitImportContext(
    val sourceId: SourceId,
    val zone: TimeZone,
    val recordedAt: Instant,
    val device: String? = null,
    val appVersion: String? = null,
    val rawRecordId: RawRecordId? = null,
    val ids: IdGenerator,
)

/** One observation and the type it belongs to; the caller stores it unchanged. */
typealias FitObservations = List<Observation>

/**
 * Maps an import to layer-2 observations (`pr.md` §7.2). `happened_at` is the instant in
 * the file and `recorded_at` is when this app imported it, so a backfilled month is
 * visibly backfilled and the file's own timestamps are never rewritten (`pr.md` §6.27).
 * Steps and heart rate keep their real intervals; an activity becomes active minutes,
 * distance and calories over its own window, with one group key so the parts stay
 * recognisable as one session.
 */
object FitObservationMapper {
    fun toObservations(import: FitImport, context: FitImportContext): FitObservations =
        steps(import, context) + heartRates(import, context) + activities(import, context)

    private fun steps(import: FitImport, context: FitImportContext): List<Observation> =
        import.steps.map { bucket ->
            observation(
                context = context,
                type = ObservationType.STEPS,
                value = bucket.steps.toDouble(),
                start = bucket.start,
                end = bucket.end.takeIf { it > bucket.start },
                key = "step:${bucket.start.epochSeconds}:${bucket.end.epochSeconds}",
                method = MeasurementMethod.FILE_IMPORT,
            )
        }

    private fun heartRates(import: FitImport, context: FitImportContext): List<Observation> =
        import.heartRates.map { sample ->
            observation(
                context = context,
                type = ObservationType.HEART_RATE,
                value = sample.bpm.toDouble(),
                start = sample.at,
                end = null,
                key = "hr:${sample.at.epochSeconds}:${sample.bpm}",
                method = MeasurementMethod.WATCH_OPTICAL,
            )
        }

    private fun activities(import: FitImport, context: FitImportContext): List<Observation> {
        val observations = mutableListOf<Observation>()
        import.activities.forEach { activity ->
            val group = "fit:${activity.start.epochSeconds}"
            observations += observation(
                context = context,
                type = ObservationType.ACTIVE_MINUTES,
                value = activity.durationMinutes,
                start = activity.start,
                end = activity.end,
                key = "active:$group",
                method = MeasurementMethod.FILE_IMPORT,
                groupKey = group,
            )
            activity.distanceMetres?.takeIf { it > 0 }?.let { metres ->
                observations += observation(
                    context = context,
                    type = ObservationType.DISTANCE,
                    value = metres,
                    start = activity.start,
                    end = activity.end,
                    key = "distance:$group",
                    method = MeasurementMethod.FILE_IMPORT,
                    groupKey = group,
                )
            }
            activity.calories?.takeIf { it > 0 }?.let { kcal ->
                observations += observation(
                    context = context,
                    type = ObservationType.ACTIVE_CALORIES,
                    value = kcal,
                    start = activity.start,
                    end = activity.end,
                    key = "calories:$group",
                    method = MeasurementMethod.FILE_IMPORT,
                    groupKey = group,
                )
            }
        }
        return observations
    }

    @Suppress("LongParameterList")
    private fun observation(
        context: FitImportContext,
        type: ObservationType,
        value: Double,
        start: Instant,
        end: Instant?,
        key: String,
        method: MeasurementMethod,
        groupKey: String? = null,
    ): Observation = Observation(
        id = ObservationId(context.ids.newId()),
        type = type,
        value = Measure(value, type.canonicalUnit).convertTo(type.canonicalUnit),
        start = start,
        end = end,
        provenance = Provenance(
            sourceId = context.sourceId,
            time = TimeProvenance.of(start, context.recordedAt, context.zone),
            idempotencyKey = "fit:${context.sourceId.value}:$key",
            device = context.device,
            appVersion = context.appVersion,
            method = method,
            rawRecordId = context.rawRecordId,
        ),
        groupKey = groupKey,
    )
}
