package app.openhealth.core.formula

import app.openhealth.core.citation.CitationKey
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus

data class WeightEntry(val date: LocalDate, val kg: Double)

data class TrendPoint(val date: LocalDate, val rawKg: Double, val trendKg: Double)

data class WeightTrendSeries(
    val points: List<TrendPoint>,
    val firstDate: LocalDate,
    val lastDate: LocalDate,
    val weeklyRateKg: Double?,
    val spanDays: Int,
)

/**
 * Exponentially weighted weight trend (`pr.md` §6.4). Scale noise is large from day to
 * day, so the trend is what the goal ETA divides into, never a single weigh-in. The
 * half-life is the number of days for the trend to close half of the gap to a new value;
 * seven days is the default.
 */
object WeightTrend {
    private const val DEFAULT_HALF_LIFE_DAYS = 7.0
    private const val MIN_HALF_LIFE_DAYS = 1.0
    private const val MAX_HALF_LIFE_DAYS = 30.0
    private const val MIN_KG = 1.0
    private const val MAX_KG = 500.0
    private const val RATE_WINDOW_DAYS = 7
    private const val DAYS_PER_WEEK = 7.0
    private const val HALF = 0.5

    fun ewma(
        entries: List<WeightEntry>,
        halfLifeDays: Double = DEFAULT_HALF_LIFE_DAYS,
    ): FormulaOutcome<WeightTrendSeries> {
        val inputs = listOf(
            input("entries", entries.size.toDouble(), "{count}"),
            input("halfLife", halfLifeDays, "d"),
        )
        val ordered = entries.distinctBy { it.date }.sortedBy { it.date }
        return when {
            halfLifeDays !in MIN_HALF_LIFE_DAYS..MAX_HALF_LIFE_DAYS ->
                notAvailable(NotAvailableReason.OUT_OF_RANGE, CitationKey.APP_RULE, inputs)

            entries.any { it.kg !in MIN_KG..MAX_KG } ->
                notAvailable(NotAvailableReason.OUT_OF_RANGE, CitationKey.APP_RULE, inputs)

            ordered.isEmpty() ->
                notAvailable(NotAvailableReason.NOT_ENOUGH_DATA, CitationKey.APP_RULE, inputs)

            else -> computed(buildSeries(ordered, halfLifeDays), CitationKey.APP_RULE, inputs)
        }
    }

    private fun buildSeries(ordered: List<WeightEntry>, halfLifeDays: Double): WeightTrendSeries {
        var trend = ordered.first().kg
        var previousDate = ordered.first().date
        val points = ordered.mapIndexed { index, entry ->
            if (index == 0) {
                trend = entry.kg
            } else {
                val elapsedDays = previousDate.daysUntil(entry.date).toDouble().coerceAtLeast(1.0)
                val alpha = 1.0 - HALF.pow(elapsedDays / halfLifeDays)
                trend += alpha * (entry.kg - trend)
            }
            previousDate = entry.date
            TrendPoint(entry.date, entry.kg, trend)
        }
        val last = points.last()
        val rateTarget = last.date.minus(DatePeriod(days = RATE_WINDOW_DAYS))
        val earlier = points.lastOrNull { it.date <= rateTarget }
        val weeklyRate = earlier?.let {
            val weeks = it.date.daysUntil(last.date) / DAYS_PER_WEEK
            if (weeks <= 0.0) null else (last.trendKg - it.trendKg) / weeks
        }
        return WeightTrendSeries(
            points = points,
            firstDate = points.first().date,
            lastDate = last.date,
            weeklyRateKg = weeklyRate,
            spanDays = points.first().date.daysUntil(last.date) + 1,
        )
    }
}

data class EtaWindow(
    val start: LocalDate,
    val end: LocalDate,
    val centralDays: Int,
    val weeklyRateKg: Double,
    val basisKey: String,
)

/**
 * The goal ETA is a window, not a date (`pr.md` §6.4), and it needs at least fourteen
 * days of weight data. The 7,700 kcal per kg rule is the cross-check for the what-if
 * projection; the window is the central estimate widened for the uncertainty of a
 * trend fitted to a noisy scale.
 */
object GoalEta {
    const val MINIMUM_DAYS = 14
    private const val KCAL_PER_KG = 7_700.0
    private const val DAYS_PER_WEEK = 7.0
    private const val WINDOW_FRACTION = 0.2
    private const val MIN_WINDOW_DAYS = 7.0
    private const val MIN_RATE_KG_PER_WEEK = 0.05
    private const val MIN_KG = 1.0
    private const val MAX_KG = 500.0

    /**
     * The current weight is the last smoothed trend point, not the latest scale reading:
     * dividing by the trend is the whole point of smoothing (`pr.md` §6.4).
     */
    fun fromTrend(
        goalWeightKg: Double,
        trend: WeightTrendSeries,
        today: LocalDate,
    ): FormulaOutcome<EtaWindow> {
        val currentWeightKg = trend.points.last().trendKg
        val inputs = listOf(
            input("current", currentWeightKg, "kg"),
            input("goal", goalWeightKg, "kg"),
            input("rate", trend.weeklyRateKg ?: 0.0, "kg/wk"),
            input("days", trend.spanDays.toDouble(), "d"),
        )
        val rate = trend.weeklyRateKg
        return when {
            currentWeightKg !in MIN_KG..MAX_KG || goalWeightKg !in MIN_KG..MAX_KG ->
                notAvailable(NotAvailableReason.OUT_OF_RANGE, CitationKey.WISHNOFSKY_1958, inputs)

            trend.spanDays < MINIMUM_DAYS || rate == null || abs(rate) < MIN_RATE_KG_PER_WEEK ->
                notAvailable(
                    NotAvailableReason.NOT_ENOUGH_DATA,
                    CitationKey.WISHNOFSKY_1958,
                    inputs,
                )

            else -> windowFor(
                remainingKg = currentWeightKg - goalWeightKg,
                weeklyRateKg = rate,
                today = today,
                basisKey = "goal.basis.trend",
                inputs = inputs,
            )
        }
    }

    /** The what-if control: a different daily energy target, nothing saved. */
    fun whatIf(
        currentWeightKg: Double,
        goalWeightKg: Double,
        dailyDeltaKcal: Double,
        today: LocalDate,
    ): FormulaOutcome<EtaWindow> {
        val inputs = listOf(
            input("current", currentWeightKg, "kg"),
            input("goal", goalWeightKg, "kg"),
            input("dailyDelta", dailyDeltaKcal, "kcal/d"),
        )
        val weeklyRate = dailyDeltaKcal * DAYS_PER_WEEK / KCAL_PER_KG
        return when {
            currentWeightKg !in MIN_KG..MAX_KG || goalWeightKg !in MIN_KG..MAX_KG ->
                notAvailable(NotAvailableReason.OUT_OF_RANGE, CitationKey.WISHNOFSKY_1958, inputs)

            abs(weeklyRate) < MIN_RATE_KG_PER_WEEK ->
                notAvailable(
                    NotAvailableReason.NOT_ENOUGH_DATA,
                    CitationKey.WISHNOFSKY_1958,
                    inputs,
                )

            else -> windowFor(
                remainingKg = currentWeightKg - goalWeightKg,
                weeklyRateKg = weeklyRate,
                today = today,
                basisKey = "goal.basis.what_if",
                inputs = inputs,
            )
        }
    }

    private fun windowFor(
        remainingKg: Double,
        weeklyRateKg: Double,
        today: LocalDate,
        basisKey: String,
        inputs: List<FormulaInput>,
    ): FormulaOutcome<EtaWindow> =
        // A loss needs a negative rate and a gain a positive one; a trend moving the
        // other way has no honest date.
        if (remainingKg != 0.0 && remainingKg * weeklyRateKg > 0.0) {
            notAvailable(NotAvailableReason.OUT_OF_RANGE, CitationKey.WISHNOFSKY_1958, inputs)
        } else {
            val centralDays = abs(remainingKg / weeklyRateKg * DAYS_PER_WEEK)
            val width = maxOf(MIN_WINDOW_DAYS, centralDays * WINDOW_FRACTION)
            val startOffset = kotlin.math.round(centralDays - width).toInt().coerceAtLeast(0)
            val endOffset = kotlin.math.round(centralDays + width).toInt().coerceAtLeast(0)
            computed(
                EtaWindow(
                    start = today.plus(DatePeriod(days = startOffset)),
                    end = today.plus(DatePeriod(days = endOffset)),
                    centralDays = kotlin.math.round(centralDays).toInt(),
                    weeklyRateKg = weeklyRateKg,
                    basisKey = basisKey,
                ),
                CitationKey.WISHNOFSKY_1958,
                inputs,
            )
        }
}

/** Sleep debt and consistency over a window of nights (`pr.md` §6.10). */
data class SleepNight(val date: LocalDate, val minutes: Double)

data class SleepDebtResult(
    val debtMinutes: Double,
    val nightsCounted: Int,
    val consistencyMinutes: Double,
    val shortestMinutes: Double,
)

object SleepDebt {
    private const val DEFAULT_WINDOW_DAYS = 14
    private const val MAX_MINUTES = 24.0 * 60.0
    private const val MIN_NEED_MINUTES = 60.0

    fun compute(
        nights: List<SleepNight>,
        needMinutes: Double,
        windowDays: Int = DEFAULT_WINDOW_DAYS,
    ): FormulaOutcome<SleepDebtResult> {
        val inputs = listOf(
            input("nights", nights.size.toDouble(), "{count}"),
            input("need", needMinutes, "min"),
            input("window", windowDays.toDouble(), "d"),
        )
        val latest = nights.maxOfOrNull { it.date }
        val earliest = latest?.minus(DatePeriod(days = windowDays - 1))
        val counted = if (earliest == null) {
            emptyList()
        } else {
            nights.filter { it.date >= earliest }.distinctBy { it.date }
        }
        return when {
            needMinutes !in MIN_NEED_MINUTES..MAX_MINUTES || windowDays <= 0 ->
                notAvailable(
                    NotAvailableReason.OUT_OF_RANGE,
                    CitationKey.SLEEP_CONSENSUS_2015,
                    inputs,
                )

            counted.isEmpty() ->
                notAvailable(
                    NotAvailableReason.NOT_ENOUGH_DATA,
                    CitationKey.SLEEP_CONSENSUS_2015,
                    inputs,
                )

            counted.any { it.minutes !in 0.0..MAX_MINUTES } ->
                notAvailable(
                    NotAvailableReason.OUT_OF_RANGE,
                    CitationKey.SLEEP_CONSENSUS_2015,
                    inputs,
                )

            else -> computed(debtOf(counted, needMinutes), CitationKey.SLEEP_CONSENSUS_2015, inputs)
        }
    }

    private fun debtOf(counted: List<SleepNight>, needMinutes: Double): SleepDebtResult {
        val debt = counted.sumOf { maxOf(0.0, needMinutes - it.minutes) }
        val mean = counted.sumOf { it.minutes } / counted.size
        val variance = counted.sumOf { (it.minutes - mean) * (it.minutes - mean) } / counted.size
        val shortest = counted.minOf { it.minutes }
        return SleepDebtResult(
            debtMinutes = debt,
            nightsCounted = counted.size,
            consistencyMinutes = sqrt(variance),
            shortestMinutes = shortest,
        )
    }
}
