@file:Suppress("MagicNumber", "ReturnCount")

package app.openhealth.core.formula

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** One night's clock times, as the observation layer recorded them. */
data class SleepTiming(val localDate: LocalDate, val bedtime: Instant?, val wake: Instant?)

/**
 * How regular a window of nights was (`pr.md` §6.10, "sleep debt and consistency").
 * Bedtime and wake time are circular quantities: 23:50 and 00:10 are twenty minutes
 * apart, not twenty-three hours. The standard deviation is therefore computed on the
 * circle, in minutes, and a single night has no deviation to report.
 */
data class SleepConsistencyResult(
    val nights: Int,
    val bedtimeSdMinutes: Double?,
    val wakeSdMinutes: Double?,
    val averageBedtimeMinutes: Double?,
    val averageWakeMinutes: Double?,
)

object SleepConsistency {
    private const val MINUTES_PER_DAY = 24.0 * 60.0

    fun compute(nights: List<SleepTiming>, zone: TimeZone): SleepConsistencyResult {
        val bedtimes = nights.mapNotNull { it.bedtime?.minuteOfDay(zone) }
        val wakes = nights.mapNotNull { it.wake?.minuteOfDay(zone) }
        return SleepConsistencyResult(
            nights = nights.size,
            bedtimeSdMinutes = circularSd(bedtimes),
            wakeSdMinutes = circularSd(wakes),
            averageBedtimeMinutes = circularMean(bedtimes),
            averageWakeMinutes = circularMean(wakes),
        )
    }

    /** A circular standard deviation in minutes; null for fewer than two readings. */
    fun circularSd(minutes: List<Double>): Double? {
        if (minutes.size < 2) return null
        val mean = circularMean(minutes) ?: return null
        val deltas = minutes.map { circularDistance(it, mean) }
        val variance = deltas.sumOf { it * it } / deltas.size
        return sqrt(variance)
    }

    /** The circular mean, in minutes from local midnight (0..1440). */
    fun circularMean(minutes: List<Double>): Double? {
        if (minutes.isEmpty()) return null
        var x = 0.0
        var y = 0.0
        minutes.forEach { value ->
            val angle = 2.0 * PI * value / MINUTES_PER_DAY
            x += cos(angle)
            y += sin(angle)
        }
        if (x == 0.0 && y == 0.0) return null
        var angle = atan2(y, x)
        if (angle < 0.0) angle += 2.0 * PI
        return angle / (2.0 * PI) * MINUTES_PER_DAY
    }

    /** The shortest distance between two clock minutes, in minutes. */
    fun circularDistance(a: Double, b: Double): Double {
        val raw = kotlin.math.abs(a - b) % MINUTES_PER_DAY
        return kotlin.math.min(raw, MINUTES_PER_DAY - raw)
    }

    /** `23:40` from a circular mean; the caller formats it, the formula stays numeric. */
    fun clockText(minutes: Double?): String? {
        if (minutes == null) return null
        val rounded = (minutes.toLong() + MINUTES_PER_DAY.toLong()) % MINUTES_PER_DAY.toLong()
        val hour = rounded / 60
        val minute = rounded % 60
        return "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
    }

    private fun Instant.minuteOfDay(zone: TimeZone): Double {
        val local = toLocalDateTime(zone).time
        return local.hour * 60.0 + local.minute + local.second / 60.0
    }
}

/** A night's stage mix, in minutes, with the shares the stage card prints. */
data class SleepStageBreakdown(
    val deepMinutes: Double?,
    val lightMinutes: Double?,
    val remMinutes: Double?,
    val awakeMinutes: Double?,
    val asleepMinutes: Double,
) {
    fun share(minutes: Double?): Double? {
        if (minutes == null || asleepMinutes <= 0.0) return null
        return minutes / asleepMinutes * 100.0
    }

    companion object {
        fun of(totals: SleepTotals): SleepStageBreakdown = SleepStageBreakdown(
            deepMinutes = totals.deepSeconds?.div(60.0),
            lightMinutes = totals.lightSeconds?.div(60.0),
            remMinutes = totals.remSeconds?.div(60.0),
            awakeMinutes = totals.awakeSeconds?.div(60.0),
            asleepMinutes = totals.asleepMinutes,
        )
    }
}
