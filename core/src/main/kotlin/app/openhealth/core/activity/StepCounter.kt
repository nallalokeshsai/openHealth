package app.openhealth.core.activity

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * One reading of the platform step counter (`Sensor.TYPE_STEP_COUNTER`, `pr.md` §6.6).
 * The counter is cumulative since boot and resets on a reboot, so the boot identity is
 * part of the reading: two counts from different boots cannot be subtracted.
 */
data class StepCounterSample(val count: Long, val bootEpochMillis: Long, val at: Instant) {
    init {
        require(count >= 0) { "a step counter cannot count backwards, was $count" }
    }
}

/**
 * What the tracker must persist between readings. It is runtime state, not a health
 * value: it never leaves the device and it is not part of an export.
 */
data class StepCounterState(
    val bootEpochMillis: Long? = null,
    val lastCount: Long? = null,
    val lastAt: Instant? = null,
)

/** Steps attributable to one interval; the sum of a day's intervals is its total. */
data class StepInterval(val start: Instant, val end: Instant, val steps: Long) {
    init {
        require(end >= start) { "interval end $end is before its start $start" }
        require(steps >= 0) { "a step interval cannot be negative, was $steps" }
    }
}

data class StepCounterUpdate(
    val intervals: List<StepInterval>,
    val state: StepCounterState,
    /**
     * True when the delta had to be clamped or a reboot was inferred, so the caller can
     * tell the person that one interval is an estimate rather than a measurement.
     */
    val repaired: Boolean = false,
)

/**
 * Turns the platform's cumulative counter into per-interval step deltas that survive a
 * reboot, a process death, a clock that moved and a sensor that reported nonsense
 * (`pr.md` §6.6: a reboot or app kill does not lose the day's count; `< 2 %` battery).
 *
 * The rules, in order:
 * 1. A new boot (the identity changed, or nothing was stored yet) cannot be subtracted
 *    from the old count. The whole count is the steps since boot; it is attributed to
 *    the local day it is read on, clipped to the plausible maximum for the elapsed time.
 * 2. A count that went backwards on the same boot is a reset the platform did not
 *    announce. It is treated as a new boot.
 * 3. Otherwise the delta is the difference, clamped to what a human can walk in the
 *    elapsed time so one corrupt reading cannot invent a day of steps.
 * 4. A delta that crosses local midnight is split at the boundary, because the rollup
 *    is per local day (`pr.md` §7.8). Rounding never loses or invents a step.
 */
object StepCounter {
    /** Above this a counter is not a person walking; it is a corrupt reading. */
    const val MAX_STEPS_PER_MINUTE = 240.0

    private val MAX_MEASURED_GAP = 7.minutes
    private const val MILLIS_PER_MINUTE = 60_000.0

    @Suppress("ReturnCount")
    fun apply(
        state: StepCounterState,
        sample: StepCounterSample,
        zone: TimeZone,
    ): StepCounterUpdate {
        val previousCount = state.lastCount
        val previousAt = state.lastAt
        val sameBoot = previousCount != null &&
            previousAt != null &&
            state.bootEpochMillis == sample.bootEpochMillis
        val restart = !sameBoot || sample.count < previousCount
        if (restart) {
            return firstOfBoot(state, sample, zone)
        }
        if (sample.at <= previousAt) {
            // A clock that went backwards, or two readings in the same millisecond: keep
            // the counter and drop the interval rather than invent a negative duration.
            val kept = maxOf(previousCount, sample.count)
            return StepCounterUpdate(
                intervals = emptyList(),
                state = state.copy(lastCount = kept),
            )
        }
        val raw = sample.count - previousCount
        val elapsed = sample.at - previousAt
        val clamped = clamp(raw, elapsed)
        return StepCounterUpdate(
            intervals = split(previousAt, sample.at, clamped, zone),
            state = StepCounterState(
                bootEpochMillis = sample.bootEpochMillis,
                lastCount = sample.count,
                lastAt = sample.at,
            ),
            repaired = clamped != raw,
        )
    }

    /**
     * The first reading of a boot. The counter restarted at zero, so its whole value is
     * steps since boot — but only if the boot happened today. A phone that has been on
     * for three days reports three days of steps, and attributing that to the day the app
     * was first opened would invent a number the person never walked. So the rule is:
     *
     * - boot on the same local day as the reading → the count is today's steps since
     *   boot, so it is attributed (clipped to the day, reset at the last reading);
     * - boot on an earlier day → the counter is only a baseline. Nothing is attributed,
     *   and steps start accruing from this reading.
     *
     * This is what makes a restart of the app, or a reboot, safe without ever inventing
     * the past (`pr.md` §6.6).
     */
    @Suppress("ReturnCount")
    private fun firstOfBoot(
        state: StepCounterState,
        sample: StepCounterSample,
        zone: TimeZone,
    ): StepCounterUpdate {
        val boot = Instant.fromEpochMilliseconds(sample.bootEpochMillis)
        val dayStart = dayStart(sample.at, zone)
        // The high-water mark never moves back: a sample that arrives with an earlier
        // clock must not let the next one re-cover an interval already counted.
        val lastAt = maxOf(state.lastAt ?: sample.at, sample.at)
        val started = StepCounterState(sample.bootEpochMillis, sample.count, lastAt)
        if (boot < dayStart) {
            return StepCounterUpdate(intervals = emptyList(), state = started, repaired = true)
        }
        // A reset after earlier readings has already counted the part before them; only
        // the part since the last reading may be attributed again.
        val from = minOf(maxOf(boot, dayStart, state.lastAt ?: boot), sample.at)
        if (from >= sample.at) {
            return StepCounterUpdate(intervals = emptyList(), state = started)
        }
        val kept = clamp(sample.count, sample.at - from)
        return StepCounterUpdate(
            intervals = split(from, sample.at, kept, zone),
            state = started,
            repaired = kept != sample.count,
        )
    }

    @Suppress("ReturnCount")
    private fun clamp(steps: Long, elapsed: Duration): Long {
        val elapsedMinutes = elapsed.inWholeMilliseconds / MILLIS_PER_MINUTE
        if (steps <= 0 || elapsedMinutes <= 0) return 0
        val allowed = elapsedMinutes * MAX_STEPS_PER_MINUTE
        return minOf(steps.toDouble(), allowed).toLong()
    }

    /** Splits [steps] across local midnights in proportion to each part's duration. */
    @Suppress("ReturnCount")
    private fun split(
        start: Instant,
        end: Instant,
        steps: Long,
        zone: TimeZone,
    ): List<StepInterval> {
        if (steps <= 0L || end <= start) return emptyList()
        val boundaries = mutableListOf(start)
        var cursor = start
        while (true) {
            val day = cursor.toLocalDateTime(zone).date
            val nextMidnight = day.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
            if (nextMidnight <= cursor || nextMidnight >= end) break
            boundaries += nextMidnight
            cursor = nextMidnight
        }
        boundaries += end
        val totalMillis = (end - start).inWholeMilliseconds
        if (totalMillis <= 0L) return listOf(StepInterval(start, end, steps))
        val parts = boundaries.zipWithNext { from, to -> Pair(from, to) }
        var assigned = 0L
        return parts.mapIndexed { index, (from, to) ->
            val share = if (index == parts.lastIndex) {
                steps - assigned
            } else {
                val millis = (to - from).inWholeMilliseconds
                (steps.toDouble() * millis / totalMillis).toLong()
            }
            assigned += share
            StepInterval(from, to, share)
        }
    }

    private fun dayStart(at: Instant, zone: TimeZone): Instant =
        at.toLocalDateTime(zone).date.atStartOfDayIn(zone)

    /** A gap this short is a normal sampling cadence, not a lost window. */
    fun isNormalGap(elapsed: Duration): Boolean = elapsed <= MAX_MEASURED_GAP
}

/**
 * How often the counter is read (`pr.md` §6.6, the `< 2 %` budget). The hardware counter
 * batches its own edges, so the cost is the wake-up, not the step: a longer interval
 * when nothing is happening is the whole optimisation. A running workout and the
 * foreground app need a fresh number; a locked phone in a pocket does not.
 */
object StepSampling {
    val FOREGROUND: Duration = 30.seconds
    val WORKOUT: Duration = 60.seconds
    val BACKGROUND: Duration = 5.minutes
    val LOW_BATTERY: Duration = 15.minutes

    /** Below this the phone is treated as low on power, an Android convention. */
    const val LOW_BATTERY_PERCENT = 15

    fun interval(screenOn: Boolean, workoutActive: Boolean, batteryPercent: Int?): Duration = when {
        workoutActive -> WORKOUT
        screenOn -> FOREGROUND
        batteryPercent != null && batteryPercent <= LOW_BATTERY_PERCENT -> LOW_BATTERY
        else -> BACKGROUND
    }
}
