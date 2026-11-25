package app.openhealth.core.body

import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/**
 * One accepted weight check-in. A day may hold several; [WeightTrend.daily] keeps the
 * latest and reports every one (`pr.md` §6.8 "one or more per day, with a single daily
 * value rule").
 */
data class WeighIn(
    val date: LocalDate,
    val kg: Double,
    val happenedAtMillis: Long? = null,
    /** Used only to break a same-millisecond tie; the day's value is still one value. */
    val recordedAtMillis: Long? = null,
)

/** The figures the weight card shows (`DESIGN.md` §14.2 Vitals). */
data class WeightTrend(
    val latestKg: Double?,
    val latestDate: LocalDate?,
    val previousDayKg: Double?,
    val changeKg: Double?,
    val changeDays: Int,
    val weeklyRateKg: Double?,
    val goalKg: Double?,
    val checkIns: Int,
    val points: List<WeighIn>,
) {
    val hasTrend: Boolean get() = weeklyRateKg != null

    /** The signed change in words, or null when there is nothing to compare. */
    val changeSentence: String?
        get() = when {
            changeKg == null || changeDays <= 0 -> null
            changeKg == 0.0 -> "No change in ${dayWord(changeDays)}"
            changeKg > 0 -> "Up ${format(Math.abs(changeKg))} kg in ${dayWord(changeDays)}"
            else -> "Down ${format(Math.abs(changeKg))} kg in ${dayWord(changeDays)}"
        }

    private fun dayWord(days: Int): String = if (days == 1) "1 day" else "$days days"

    private fun format(value: Double): String {
        val rounded = kotlin.math.round(value * 10.0) / 10.0
        return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
    }
}

/**
 * Weight trend and weekly rate from the person's own check-ins (`pr.md` §6.8).
 *
 * The daily value is the latest check-in of that local day; the rate is the least-squares
 * slope of the daily values over the last [RATE_WINDOW_DAYS] days, multiplied to a week.
 * It needs at least [MIN_RATE_POINTS] distinct days, because two weigh-ins a week apart
 * are a pair, not a trend. No projection is invented here; B9 owns the goal ETA.
 */
object WeightTrendCalculator {
    const val RATE_WINDOW_DAYS = 28
    const val MIN_RATE_POINTS = 3
    const val SPARK_WINDOW_DAYS = 90
    const val TREND_WINDOW_DAYS = 30

    fun compute(
        checkIns: List<WeighIn>,
        today: LocalDate,
        goalKg: Double? = null,
        windowDays: Int = TREND_WINDOW_DAYS,
    ): WeightTrend {
        val sorted = checkIns.filter { it.kg.isFinite() && it.kg > 0.0 }
            .sortedWith(
                compareBy(
                    { it.date },
                    { it.happenedAtMillis ?: 0L },
                    { it.recordedAtMillis ?: 0L },
                ),
            )
        val daily = daily(sorted)
        val inWindow = daily.filter { it.date <= today }
        val latest = inWindow.lastOrNull()
        val windowStart = today.minusDays(windowDays)
        val previous = latest?.let { last ->
            inWindow.lastOrNull { it.date < last.date && it.date >= windowStart }
        }
        val changeDays = if (latest != null && previous != null) {
            (latest.date.toEpochDays() - previous.date.toEpochDays()).toInt()
        } else {
            0
        }
        val rate = weeklyRate(sorted, today)
        return WeightTrend(
            latestKg = latest?.kg,
            latestDate = latest?.date,
            previousDayKg = previous?.kg,
            changeKg = if (latest != null && previous != null) latest.kg - previous.kg else null,
            changeDays = changeDays,
            weeklyRateKg = rate,
            goalKg = goalKg,
            checkIns = sorted.size,
            points = daily,
        )
    }

    /** The latest check-in per local day, in date order (`pr.md` §6.8). */
    fun daily(checkIns: List<WeighIn>): List<WeighIn> = checkIns
        .sortedWith(
            compareBy(
                { it.date },
                { it.happenedAtMillis ?: 0L },
                { it.recordedAtMillis ?: 0L },
            ),
        )
        .groupBy { it.date }
        .map { (_, sameDay) -> sameDay.last() }
        .sortedBy { it.date }

    /**
     * The least-squares slope, in kilograms per week, over the last [RATE_WINDOW_DAYS]
     * days. Null when there are fewer than [MIN_RATE_POINTS] distinct days or the span is
     * shorter than a week: a weekly rate from three consecutive days would be noise.
     */
    fun weeklyRate(checkIns: List<WeighIn>, today: LocalDate): Double? {
        val daily = daily(checkIns)
        val start = today.minusDays(RATE_WINDOW_DAYS)
        val window = daily.filter { it.date >= start && it.date <= today }
        if (window.size < MIN_RATE_POINTS) return null
        val first = window.first().date.toEpochDays().toDouble()
        val xs = window.map { it.date.toEpochDays().toDouble() - first }
        if (xs.max() - xs.min() < MIN_RATE_SPAN_DAYS) return null
        val ys = window.map { it.kg }
        val meanX = xs.average()
        val meanY = ys.average()
        var covariance = 0.0
        var variance = 0.0
        xs.indices.forEach { index ->
            val dx = xs[index] - meanX
            covariance += dx * (ys[index] - meanY)
            variance += dx * dx
        }
        if (variance == 0.0) return null
        return covariance / variance * DAYS_PER_WEEK
    }

    private const val MIN_RATE_SPAN_DAYS = 6.0
    private const val DAYS_PER_WEEK = 7.0
}

private fun LocalDate.minusDays(days: Int): LocalDate =
    this - kotlinx.datetime.DatePeriod(days = days)
