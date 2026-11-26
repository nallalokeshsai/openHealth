@file:Suppress(
    "MagicNumber",
    "CyclomaticComplexMethod",
    "ReturnCount",
    "LoopWithTooManyJumpStatements",
)

package app.openhealth.core.activity

import app.openhealth.core.body.Muscle
import app.openhealth.core.formula.MetActivity
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/**
 * The sports an activity can be (`pr.md` §6.11 MVP). A type names the muscles a session
 * loads so a run or a swim can reach the recovery map without pretending to know the
 * exact fibres; yoga names its own muscles per pose in `Yoga.kt`.
 */
enum class ActivityType(
    val labelKey: String,
    val met: MetActivity,
    val primaryMuscles: List<Muscle>,
    val secondaryMuscles: List<Muscle>,
    val distanceBased: Boolean,
) {
    RUN(
        labelKey = "activity.type.run",
        met = MetActivity.RUNNING_6_MPH,
        primaryMuscles = listOf(Muscle.QUADS, Muscle.CALVES),
        secondaryMuscles = listOf(Muscle.HAMSTRINGS, Muscle.GLUTES, Muscle.ABS),
        distanceBased = true,
    ),
    WALK(
        labelKey = "activity.type.walk",
        met = MetActivity.WALKING_3_MPH,
        primaryMuscles = listOf(Muscle.CALVES),
        secondaryMuscles = listOf(Muscle.GLUTES, Muscle.QUADS),
        distanceBased = true,
    ),
    HIKE(
        labelKey = "activity.type.hike",
        met = MetActivity.HIKING_HILLY,
        primaryMuscles = listOf(Muscle.QUADS, Muscle.GLUTES),
        secondaryMuscles = listOf(Muscle.CALVES, Muscle.HAMSTRINGS),
        distanceBased = true,
    ),
    RIDE(
        labelKey = "activity.type.ride",
        met = MetActivity.BICYCLING_MODERATE,
        primaryMuscles = listOf(Muscle.QUADS),
        secondaryMuscles = listOf(Muscle.GLUTES, Muscle.CALVES, Muscle.HAMSTRINGS),
        distanceBased = true,
    ),
    SWIM(
        labelKey = "activity.type.swim",
        met = MetActivity.SWIMMING_LAPS_MODERATE,
        primaryMuscles = listOf(Muscle.UPPER_BACK, Muscle.DELTOIDS),
        secondaryMuscles = listOf(Muscle.CHEST, Muscle.ABS, Muscle.TRICEPS),
        distanceBased = true,
    ),
    ROW(
        labelKey = "activity.type.row",
        met = MetActivity.ROWING_STATIONARY_MODERATE,
        primaryMuscles = listOf(Muscle.UPPER_BACK, Muscle.QUADS),
        secondaryMuscles = listOf(Muscle.LOWER_BACK, Muscle.GLUTES, Muscle.BICEPS),
        distanceBased = true,
    ),
    SKI(
        labelKey = "activity.type.ski",
        met = MetActivity.SKIING_DOWNHILL_MODERATE,
        primaryMuscles = listOf(Muscle.QUADS, Muscle.GLUTES),
        secondaryMuscles = listOf(Muscle.CALVES, Muscle.ABS),
        distanceBased = true,
    ),
    YOGA(
        labelKey = "activity.type.yoga",
        met = MetActivity.YOGA_GENERAL,
        primaryMuscles = emptyList(),
        secondaryMuscles = emptyList(),
        distanceBased = false,
    ),
    MOBILITY(
        labelKey = "activity.type.mobility",
        met = MetActivity.STRETCHING,
        primaryMuscles = emptyList(),
        secondaryMuscles = emptyList(),
        distanceBased = false,
    ),
    OTHER(
        labelKey = "activity.type.other",
        met = MetActivity.CALISTHENICS_MODERATE,
        primaryMuscles = emptyList(),
        secondaryMuscles = emptyList(),
        distanceBased = false,
    ),
    ;

    val hasDistance: Boolean get() = distanceBased

    companion object {
        fun fromKey(key: String): ActivityType? = entries.firstOrNull { it.name == key }

        /** Maps a file's free-text sport (GPX `<type>`, TCX `Sport`, Strava CSV) to a type. */
        fun fromSportName(name: String?): ActivityType {
            val clean = name?.trim()?.lowercase().orEmpty()
            return when {
                clean.contains("run") || clean.contains("jog") -> RUN
                clean.contains("walk") -> WALK
                clean.contains("hike") || clean.contains("hiking") -> HIKE
                clean.contains("ride") || clean.contains("cycl") || clean.contains("bik") -> RIDE
                clean.contains("swim") -> SWIM
                clean.contains("row") -> ROW
                clean.contains("ski") -> SKI
                clean.contains("yoga") -> YOGA
                clean.contains("mobil") || clean.contains("stretch") -> MOBILITY
                else -> OTHER
            }
        }
    }
}

/**
 * How the record reached the app (`pr.md` §6.11 acceptance: recorded, imported and
 * manually entered data are clearly separated). The value is stored, never inferred.
 */
enum class ActivitySource(val labelKey: String) {
    RECORDED("activity.source.recorded"),
    IMPORTED("activity.source.imported"),
    MANUAL("activity.source.manual"),
    ;

    companion object {
        fun fromKey(key: String): ActivitySource = entries.firstOrNull { it.name == key }
            ?: error("Unknown activity source $key")
    }
}

/** One position sample. A sample may also carry heart rate, cadence or power. */
data class ActivityPoint(
    val latitude: Double,
    val longitude: Double,
    val elapsedSeconds: Long,
    val elevationM: Double? = null,
    val heartRate: Double? = null,
    val cadence: Double? = null,
    val powerWatts: Double? = null,
    val speedMps: Double? = null,
) {
    init {
        require(latitude in -90.0..90.0) { "latitude $latitude is not on Earth" }
        require(longitude in -180.0..180.0) { "longitude $longitude is not on Earth" }
        require(elapsedSeconds >= 0) { "elapsedSeconds must not be negative" }
    }
}

/**
 * A finished activity, layer 4 of `pr.md` §7. The route is the ordered point list; the
 * summary values are what the recording, the file or the person supplied, and a missing
 * value stays null rather than becoming a zero.
 */
data class ActivityRecord(
    val id: String,
    val type: ActivityType,
    val source: ActivitySource,
    val startedAt: Instant,
    val endedAt: Instant,
    val localDate: LocalDate,
    val zoneId: String,
    val distanceM: Double = 0.0,
    val elevationGainM: Double? = null,
    val averageHeartRate: Double? = null,
    val maxHeartRate: Double? = null,
    val averageCadence: Double? = null,
    val averagePowerWatts: Double? = null,
    val energyKcal: Double? = null,
    val name: String? = null,
    val notes: String? = null,
    val flowId: String? = null,
    val muscles: List<Muscle> = emptyList(),
    val points: List<ActivityPoint> = emptyList(),
) {
    init {
        require(endedAt >= startedAt) { "endedAt $endedAt is before startedAt $startedAt" }
        require(distanceM >= 0.0) { "distanceM must not be negative" }
        require(energyKcal == null || energyKcal >= 0.0) { "energyKcal must not be negative" }
    }

    val duration: Duration get() = endedAt - startedAt
    val durationSeconds: Long get() = duration.inWholeSeconds
    val hasRoute: Boolean get() = points.size >= 2

    /** A stable identity for a cross-source duplicate check, never a user-visible id. */
    val fingerprint: String
        get() = buildString {
            append(type.name)
            append(':')
            append(startedAt.toEpochMilliseconds() / FINGERPRINT_START_BUCKET_MS)
            append(':')
            append((distanceM / FINGERPRINT_DISTANCE_BUCKET_M).toLong())
        }

    companion object {
        private const val FINGERPRINT_START_BUCKET_MS = 60_000L
        private const val FINGERPRINT_DISTANCE_BUCKET_M = 100.0
    }
}

/** Great-circle helpers for a route, kept here so GPS math has no platform dependency. */
object Geo {
    private const val EARTH_RADIUS_M = 6_371_008.8
    private const val DEGREES = 180.0

    /** Haversine distance in metres. */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = lat1 * PI / DEGREES
        val phi2 = lat2 * PI / DEGREES
        val dPhi = (lat2 - lat1) * PI / DEGREES
        val dLambda = (lon2 - lon1) * PI / DEGREES
        val a = sin(dPhi / 2) * sin(dPhi / 2) +
            cos(phi1) * cos(phi2) * sin(dLambda / 2) * sin(dLambda / 2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    /**
     * Route distance, dropping impossible jumps that GPS produces when it re-acquires.
     * A jump is kept only when the elapsed time makes the implied speed believable.
     */
    fun routeDistanceMeters(points: List<ActivityPoint>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (index in 1 until points.size) {
            val previous = points[index - 1]
            val current = points[index]
            val metres = distanceMeters(
                previous.latitude,
                previous.longitude,
                current.latitude,
                current.longitude,
            )
            val seconds = (current.elapsedSeconds - previous.elapsedSeconds).coerceAtLeast(1)
            val impliedSpeed = metres / seconds
            if (metres <= MAX_JUMP_M || impliedSpeed <= MAX_PLAUSIBLE_SPEED_MPS) {
                total += metres
            }
        }
        return total
    }

    /** Positive elevation change above the noise floor. */
    fun elevationGainMeters(points: List<ActivityPoint>): Double {
        var gain = 0.0
        var previous: Double? = null
        points.forEach { point ->
            val elevation = point.elevationM ?: return@forEach
            val last = previous
            if (last != null) {
                val delta = elevation - last
                if (delta >= ELEVATION_NOISE_M) {
                    gain += delta
                    previous = elevation
                } else if (delta < -ELEVATION_NOISE_M) {
                    previous = elevation
                }
            } else {
                previous = elevation
            }
        }
        return gain
    }

    private const val MAX_JUMP_M = 1_000.0
    private const val MAX_PLAUSIBLE_SPEED_MPS = 30.0
    private const val ELEVATION_NOISE_M = 0.5
}

/** One kilometre (or the closing part of one) of a route (`pr.md` §6.11: splits). */
data class ActivitySplit(
    val index: Int,
    val distanceM: Double,
    val durationSeconds: Long,
    val elevationGainM: Double,
    val averageHeartRate: Double?,
) {
    val paceSecondsPerKm: Double
        get() = if (distanceM <= 0.0) 0.0 else durationSeconds * 1_000.0 / distanceM
}

enum class HeartRateZone(val labelKey: String, val lowerFraction: Double) {
    Z1("activity.zone.z1", 0.50),
    Z2("activity.zone.z2", 0.60),
    Z3("activity.zone.z3", 0.70),
    Z4("activity.zone.z4", 0.80),
    Z5("activity.zone.z5", 0.90),
    ;

    companion object {
        /** The zone a heart rate falls in for a given maximum; 0 when below Z1. */
        fun of(heartRate: Double, maxHeartRate: Double): HeartRateZone? {
            if (maxHeartRate <= 0.0) return null
            val fraction = heartRate / maxHeartRate
            return entries.lastOrNull { fraction >= it.lowerFraction }
        }
    }
}

data class HeartRateZoneSlice(val zone: HeartRateZone, val seconds: Long, val share: Double)

/**
 * Time in each zone from the samples (`DESIGN.md` §8.2). The gaps between samples are
 * attributed to the zone of the earlier sample, and the last sample gets no time. The
 * zone model is the five-band Edwards model; the max is labelled an estimate when it
 * comes from age rather than a measured value.
 */
data class HeartRateZoneModel(
    val maxHeartRate: Double,
    val maxIsEstimate: Boolean,
    val slices: List<HeartRateZoneSlice>,
    val totalSeconds: Long,
) {
    val hasData: Boolean get() = totalSeconds > 0

    companion object {
        fun from(
            points: List<ActivityPoint>,
            maxHeartRate: Double,
            estimate: Boolean,
        ): HeartRateZoneModel {
            val samples = points.filter { it.heartRate != null }
            val durations = mutableMapOf<HeartRateZone, Long>()
            var total = 0L
            for (index in 0 until samples.size - 1) {
                val sample = samples[index]
                val next = samples[index + 1]
                val seconds = (next.elapsedSeconds - sample.elapsedSeconds).coerceAtLeast(0)
                if (seconds == 0L) continue
                val zone = HeartRateZone.of(sample.heartRate ?: continue, maxHeartRate)
                    ?: continue
                durations[zone] = (durations[zone] ?: 0L) + seconds
                total += seconds
            }
            val slices = HeartRateZone.entries.map { zone ->
                val seconds = durations[zone] ?: 0L
                HeartRateZoneSlice(
                    zone = zone,
                    seconds = seconds,
                    share = if (total == 0L) 0.0 else seconds.toDouble() / total,
                )
            }
            return HeartRateZoneModel(maxHeartRate, estimate, slices, total)
        }

        /** 220 minus age is the app's fallback only when no measured maximum exists. */
        fun estimateMaxHeartRate(age: Int?): Double? {
            if (age == null || age <= 0) return null
            return 220.0 - age
        }
    }
}

/** Fastest contiguous effort over a target distance, when the route is long enough. */
data class BestEffort(val distanceM: Double, val durationSeconds: Double, val achieved: Boolean)

/** Everything an activity detail shows, computed from the stored record. */
data class ActivityMetrics(
    val distanceM: Double,
    val durationSeconds: Long,
    val movingSeconds: Long,
    val elevationGainM: Double,
    val averageSpeedMps: Double,
    val averagePaceSecondsPerKm: Double?,
    val averageHeartRate: Double?,
    val maxHeartRate: Double?,
    val averageCadence: Double?,
    val averagePowerWatts: Double?,
    val energyKcal: Double?,
    val splits: List<ActivitySplit>,
    val bestEfforts: List<BestEffort>,
)

object ActivityCalculator {
    private const val MOVING_SPEED_MPS = 0.5
    private const val BEST_EFFORT_TOLERANCE = 0.98

    fun metrics(activity: ActivityRecord): ActivityMetrics {
        val points = activity.points
        val routeDistance = Geo.routeDistanceMeters(points)
        val distance = when {
            activity.distanceM > 0.0 -> activity.distanceM
            else -> routeDistance
        }
        val elevation = activity.elevationGainM ?: Geo.elevationGainMeters(points)
        val averageHeartRate = activity.averageHeartRate ?: meanHr(points)
        val maxHeartRate = activity.maxHeartRate ?: points.mapNotNull { it.heartRate }.maxOrNull()
        val cadence = activity.averageCadence ?: mean(points.mapNotNull { it.cadence })
        val power = activity.averagePowerWatts ?: mean(points.mapNotNull { it.powerWatts })
        val moving = movingSeconds(points, activity.durationSeconds)
        val speed = if (activity.durationSeconds == 0L) {
            0.0
        } else {
            distance / activity.durationSeconds
        }
        return ActivityMetrics(
            distanceM = distance,
            durationSeconds = activity.durationSeconds,
            movingSeconds = moving,
            elevationGainM = elevation,
            averageSpeedMps = speed,
            averagePaceSecondsPerKm = if (distance > 0.0) {
                activity.durationSeconds * 1_000.0 / distance
            } else {
                null
            },
            averageHeartRate = averageHeartRate,
            maxHeartRate = maxHeartRate,
            averageCadence = cadence,
            averagePowerWatts = power,
            energyKcal = activity.energyKcal,
            splits = splits(points),
            bestEfforts = bestEfforts(points),
        )
    }

    fun splits(points: List<ActivityPoint>, splitMeters: Double = 1_000.0): List<ActivitySplit> {
        if (points.size < 2 || splitMeters <= 0.0) return emptyList()
        val splits = mutableListOf<ActivitySplit>()
        var splitStart = 0
        var splitDistance = 0.0
        var splitElevation = 0.0
        var splitHrSum = 0.0
        var splitHrCount = 0
        var index = 1
        while (index < points.size) {
            val previous = points[index - 1]
            val current = points[index]
            val metres = Geo.distanceMeters(
                previous.latitude,
                previous.longitude,
                current.latitude,
                current.longitude,
            )
            splitDistance += metres
            val rise = (current.elevationM ?: 0.0) - (previous.elevationM ?: 0.0)
            if (rise > 0.0) splitElevation += rise
            current.heartRate?.let {
                splitHrSum += it
                splitHrCount += 1
            }
            if (splitDistance >= splitMeters) {
                val start = points[splitStart]
                val seconds = (current.elapsedSeconds - start.elapsedSeconds).coerceAtLeast(0)
                splits += ActivitySplit(
                    index = splits.size + 1,
                    distanceM = splitDistance,
                    durationSeconds = seconds,
                    elevationGainM = splitElevation,
                    averageHeartRate = if (splitHrCount == 0) null else splitHrSum / splitHrCount,
                )
                splitStart = index
                splitDistance = 0.0
                splitElevation = 0.0
                splitHrSum = 0.0
                splitHrCount = 0
            }
            index += 1
        }
        if (splitDistance > MIN_PARTIAL_SPLIT_M) {
            val start = points[splitStart]
            val end = points.last()
            val seconds = (end.elapsedSeconds - start.elapsedSeconds).coerceAtLeast(0)
            splits += ActivitySplit(
                index = splits.size + 1,
                distanceM = splitDistance,
                durationSeconds = seconds,
                elevationGainM = splitElevation,
                averageHeartRate = if (splitHrCount == 0) null else splitHrSum / splitHrCount,
            )
        }
        return splits
    }

    /**
     * Fastest window over 1 km and 5 km. The window is found on the cumulative-distance
     * axis; the time is interpolated between the two enclosing samples.
     */
    fun bestEfforts(points: List<ActivityPoint>): List<BestEffort> =
        listOf(1_000.0, 5_000.0).map { target ->
            fastestWindow(points, target)
        }

    private fun fastestWindow(points: List<ActivityPoint>, targetM: Double): BestEffort {
        if (points.size < 2) return BestEffort(targetM, 0.0, false)
        val cumulative = DoubleArray(points.size)
        for (index in 1 until points.size) {
            cumulative[index] = cumulative[index - 1] + Geo.distanceMeters(
                points[index - 1].latitude,
                points[index - 1].longitude,
                points[index].latitude,
                points[index].longitude,
            )
        }
        val total = cumulative.last()
        if (total < targetM) return BestEffort(targetM, 0.0, false)
        var best = Double.MAX_VALUE
        var start = 0
        var end = 0
        while (end < points.size) {
            while (end < points.size - 1 && cumulative[end] - cumulative[start] < targetM) {
                end += 1
            }
            val span = cumulative[end] - cumulative[start]
            if (span >= targetM * BEST_EFFORT_TOLERANCE) {
                val seconds = (points[end].elapsedSeconds - points[start].elapsedSeconds)
                    .toDouble()
                val scaled = seconds * targetM / span
                if (scaled < best) best = scaled
            }
            start += 1
            if (start > end) end = start
            if (start >= points.size - 1) break
        }
        return BestEffort(
            targetM,
            if (best ==
                Double.MAX_VALUE
            ) {
                0.0
            } else {
                best
            },
            best != Double.MAX_VALUE,
        )
    }

    private fun movingSeconds(points: List<ActivityPoint>, fallback: Long): Long {
        if (points.size < 2) return fallback
        var moving = 0L
        for (index in 1 until points.size) {
            val previous = points[index - 1]
            val current = points[index]
            val metres = Geo.distanceMeters(
                previous.latitude,
                previous.longitude,
                current.latitude,
                current.longitude,
            )
            val seconds = current.elapsedSeconds - previous.elapsedSeconds
            val speed = if (seconds <= 0) 0.0 else metres / seconds
            if (speed >= MOVING_SPEED_MPS) moving += seconds.coerceAtLeast(0)
        }
        return moving
    }

    private fun meanHr(points: List<ActivityPoint>): Double? = mean(
        points.mapNotNull { it.heartRate }.filter { it > 0.0 },
    )

    private fun mean(values: List<Double>): Double? =
        if (values.isEmpty()) null else values.average()

    private const val MIN_PARTIAL_SPLIT_M = 100.0
}

/** Statistics over a list of activities, for the history header and the stats card. */
data class ActivityTotals(
    val count: Int,
    val distanceM: Double,
    val durationSeconds: Long,
    val elevationGainM: Double,
) {
    companion object {
        fun of(activities: List<ActivityRecord>): ActivityTotals = ActivityTotals(
            count = activities.size,
            distanceM = activities.sumOf { ActivityCalculator.metrics(it).distanceM },
            durationSeconds = activities.sumOf { it.durationSeconds },
            elevationGainM = activities.sumOf { ActivityCalculator.metrics(it).elevationGainM },
        )

        fun weekly(activities: List<ActivityRecord>, weeks: Int = 4): List<ActivityTotals> {
            val sorted = activities.sortedByDescending { it.localDate }
            if (sorted.isEmpty()) return List(weeks) { ActivityTotals(0, 0.0, 0, 0.0) }
            val latest = sorted.first().localDate
            return (0 until weeks).map { back ->
                val end = latest.minus(DatePeriod(days = back * 7))
                val start = end.minus(DatePeriod(days = 6))
                val inWeek = activities.filter { it.localDate in start..end }
                of(inWeek)
            }.reversed()
        }
    }
}

/** Estimates a session's energy from its MET when the file or the device did not provide one. */
object ActivityEnergy {
    private const val SECONDS_PER_MINUTE = 60.0

    fun estimate(activity: ActivityRecord, weightKg: Double?): Double? {
        if (activity.energyKcal != null) return activity.energyKcal
        if (weightKg == null || weightKg <= 0.0) return null
        val minutes = activity.durationSeconds / SECONDS_PER_MINUTE
        if (minutes <= 0.0) return null
        val outcome = app.openhealth.core.formula.MetEnergy.fromActivity(
            activity.type.met,
            weightKg,
            minutes,
        )
        return (
            outcome as? app.openhealth.core.formula.FormulaOutcome.Computed
            )?.value?.kilocalories
    }

    fun withEstimate(activity: ActivityRecord, weightKg: Double?): ActivityRecord =
        activity.copy(energyKcal = estimate(activity, weightKg))
}

/** True when two records describe the same session from two sources. */
object ActivityDuplicates {
    private const val START_TOLERANCE_SECONDS = 120L
    private const val DISTANCE_RATIO = 0.05
    private const val DISTANCE_ABSOLUTE_M = 250.0

    fun sameSession(first: ActivityRecord, second: ActivityRecord): Boolean {
        if (first.type != second.type) return false
        val gap = abs(
            first.startedAt.toEpochMilliseconds() -
                second.startedAt.toEpochMilliseconds(),
        ) / 1_000.0
        if (gap > START_TOLERANCE_SECONDS) return false
        val distanceGap = abs(first.distanceM - second.distanceM)
        if (distanceGap <= DISTANCE_ABSOLUTE_M) return true
        val largest = max(first.distanceM, second.distanceM)
        val smallest = min(first.distanceM, second.distanceM)
        if (largest <= 0.0) return true
        return (largest - smallest) / largest <= DISTANCE_RATIO
    }
}
