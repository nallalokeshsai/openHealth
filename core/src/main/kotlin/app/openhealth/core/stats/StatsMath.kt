package app.openhealth.core.stats

import app.openhealth.core.formula.DailyValue
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** One of the five steps of the one ramp (`DESIGN.md` §8.3). */
enum class RampLevel { L0, L1, L2, L3, L4 }

/** A week of one figure, starting on a Monday. */
data class WeeklyBucket(val start: LocalDate, val value: Double, val daysWithData: Int)

/** One cell of a consistency heatmap. */
data class HeatCell(val date: LocalDate, val value: Double, val level: RampLevel)

/** One row of a horizontal bar list, such as weekly sets per muscle. */
data class NamedTotal(val name: String, val value: Double)

/** The change between two ends of a series of days. */
data class SeriesChange(
    val start: LocalDate,
    val end: LocalDate,
    val startValue: Double,
    val endValue: Double,
    val change: Double,
) {
    val days: Int get() = start.daysUntil(end) + 1
}

/**
 * The small amount of arithmetic every Stats card shares (`pr.md` §6.16). It lives in
 * `:core` so the chart and its table are built from the same numbers, and so a missing
 * day stays missing: a mean is over the days that have a value, never over zero-filled
 * days.
 */
@Suppress("TooManyFunctions")
object StatsMath {
    private const val DAYS_PER_WEEK = 7
    private const val MONDAY = 1
    private const val STEP_ONE = 0.25
    private const val STEP_TWO = 0.50
    private const val STEP_THREE = 0.75

    fun startOfWeek(date: LocalDate): LocalDate =
        date.minus(DatePeriod(days = date.dayOfWeek.isoDayNumber - MONDAY))

    /** The inclusive window of [days] ending on [end]. */
    fun window(end: LocalDate, days: Int): ClosedRange<LocalDate> {
        require(days > 0) { "a window needs at least one day" }
        return end.minus(DatePeriod(days = days - 1))..end
    }

    fun inWindow(values: List<DailyValue>, range: ClosedRange<LocalDate>): List<DailyValue> =
        values.filter { it.date in range }

    /** One value per day, the last written for that date winning. */
    fun byDay(values: List<DailyValue>): List<DailyValue> =
        values.groupBy { it.date }.map { (_, day) -> day.last() }.sortedBy { it.date }

    fun mean(values: List<DailyValue>, range: ClosedRange<LocalDate>): Double? {
        val counted = inWindow(byDay(values), range).filter { it.value.isFinite() }
        return if (counted.isEmpty()) null else counted.map { it.value }.average()
    }

    fun total(values: List<DailyValue>, range: ClosedRange<LocalDate>): Double =
        inWindow(byDay(values), range).sumOf { it.value }

    /** Days inside the window that carry a value, which is what a "days logged" line says. */
    fun daysWithData(values: List<DailyValue>, range: ClosedRange<LocalDate>): Int =
        inWindow(byDay(values), range).size

    /** The five-step ramp, relative to the largest value in the chart (`DESIGN.md` §8.3). */
    fun level(value: Double, maximum: Double): RampLevel {
        if (value <= 0.0 || maximum <= 0.0) return RampLevel.L0
        val ratio = value / maximum
        return when {
            ratio <= STEP_ONE -> RampLevel.L1
            ratio <= STEP_TWO -> RampLevel.L2
            ratio <= STEP_THREE -> RampLevel.L3
            else -> RampLevel.L4
        }
    }

    /** Weekly totals ending on [endDate]'s week, oldest week first. */
    fun weekly(values: List<DailyValue>, endDate: LocalDate, weeks: Int): List<WeeklyBucket> {
        require(weeks > 0) { "a weekly chart needs at least one week" }
        val lastStart = startOfWeek(endDate)
        val firstStart = lastStart.minus(DatePeriod(days = DAYS_PER_WEEK * (weeks - 1)))
        val days = byDay(values)
        return (0 until weeks).map { index ->
            val start = firstStart.plus(DatePeriod(days = DAYS_PER_WEEK * index))
            val range = start..start.plus(DatePeriod(days = DAYS_PER_WEEK - 1))
            val counted = inWindow(days, range)
            WeeklyBucket(start, counted.sumOf { it.value }, counted.size)
        }
    }

    /**
     * A 12-week heatmap ending in [endDate]'s week: weeks oldest first, each Monday to
     * Sunday. Missing days are `L0` cells with their real date, so the grid keeps its
     * shape and the table can name every day.
     */
    fun heatGrid(
        values: List<DailyValue>,
        endDate: LocalDate,
        weeks: Int = 12,
    ): List<List<HeatCell>> {
        val lastStart = startOfWeek(endDate)
        val firstStart = lastStart.minus(DatePeriod(days = DAYS_PER_WEEK * (weeks - 1)))
        val days = byDay(values).associateBy { it.date }
        val maximum = days.values.maxOfOrNull { it.value } ?: 0.0
        return (0 until weeks).map { index ->
            val start = firstStart.plus(DatePeriod(days = DAYS_PER_WEEK * index))
            (0 until DAYS_PER_WEEK).map { offset ->
                val date = start.plus(DatePeriod(days = offset))
                val value = days[date]?.value ?: 0.0
                HeatCell(date, value, level(value, maximum))
            }
        }
    }

    /** The first and last day with data inside [range], and the change between them. */
    @Suppress("ReturnCount")
    fun change(values: List<DailyValue>, range: ClosedRange<LocalDate>): SeriesChange? {
        val counted = inWindow(byDay(values), range)
        if (counted.size < 2) return null
        val first = counted.firstOrNull() ?: return null
        val last = counted.last()
        return SeriesChange(
            start = first.date,
            end = last.date,
            startValue = first.value,
            endValue = last.value,
            change = last.value - first.value,
        )
    }

    /** Descending totals, so a bar list reads like a ranked list (`DESIGN.md` §8.2). */
    fun ranked(totals: Map<String, Double>): List<NamedTotal> = totals.entries
        .filter { it.value > 0.0 }
        .map { NamedTotal(it.key, it.value) }
        .sortedWith(compareByDescending<NamedTotal> { it.value }.thenBy { it.name })
}
