package app.openhealth.core.formula

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/** One day's value for an input the readiness score uses. */
data class DailyValue(val date: LocalDate, val value: Double)

/**
 * Assembles the readiness inputs of `pr.md` §6.10 from the person's own recent days.
 *
 * The baseline is the mean of the 28 days before today (today excluded), and it needs at
 * least seven of them. Nothing is inferred from a source that is absent: an input with no
 * series is left out and [Readiness.compute] renormalises over what remains. A value
 * arriving from a wearable for the same day replaces a manual one only because the caller
 * passes one series per input.
 */
object ReadinessSeries {
    const val WINDOW_DAYS = 28
    const val MIN_BASELINE_DAYS = ReadinessInputs.MIN_BASELINE_DAYS
    private const val MIN_USEFUL = 0.0

    /**
     * The baseline for [today] from a series that may hold gaps. Only days inside the
     * window count, one value per date, and a non-positive value is not a baseline.
     */
    fun baseline(
        values: List<DailyValue>,
        today: LocalDate,
        windowDays: Int = WINDOW_DAYS,
    ): Baseline? {
        val earliest = today.minus(DatePeriod(days = windowDays))
        val counted = values
            .filter { it.date in earliest..today }
            .filter { it.date < today }
            .groupBy { it.date }
            .map { (_, day) -> day.last().value }
            .filter { it > MIN_USEFUL }
        if (counted.size < MIN_BASELINE_DAYS) return null
        return Baseline(mean = counted.average(), days = counted.size)
    }

    /**
     * Builds the inputs for [Readiness.compute]. [sleepMinutes] is last night's duration;
     * the resting heart rate and HRV are today's reading (or the most recent one the
     * caller chose) and their series supply the baseline.
     */
    fun inputs(
        today: LocalDate,
        sleepMinutes: Double? = null,
        restingHeartRate: Double? = null,
        restingHeartRateSeries: List<DailyValue> = emptyList(),
        hrvMillis: Double? = null,
        hrvSeries: List<DailyValue> = emptyList(),
        trainingLoad48h: Double? = null,
        trainingLoadSeries: List<DailyValue> = emptyList(),
        sleepNeedMinutes: Double = ReadinessInputs.DEFAULT_SLEEP_NEED_MINUTES,
    ): ReadinessInputs {
        val windowStart = today.minus(DatePeriod(days = WINDOW_DAYS))
        val loadDays = trainingLoadSeries.count { it.date in windowStart..today && it.date < today }
        return ReadinessInputs(
            sleepMinutes = sleepMinutes,
            sleepNeedMinutes = sleepNeedMinutes,
            restingHeartRate = restingHeartRate,
            restingHeartRateBaseline = baseline(restingHeartRateSeries, today),
            hrvMillis = hrvMillis,
            hrvBaseline = baseline(hrvSeries, today),
            trainingLoad48h = trainingLoad48h,
            trainingLoadDailyAverage28d = loadAverage(trainingLoadSeries, today),
            trainingLoadBaselineDays = loadDays,
        )
    }

    /** The mean daily training load over the 28 days before today, or null with no data. */
    fun loadAverage(values: List<DailyValue>, today: LocalDate): Double? {
        val earliest = today.minus(DatePeriod(days = WINDOW_DAYS))
        val counted = values
            .filter { it.date in earliest..today && it.date < today }
            .groupBy { it.date }
            .map { (_, day) -> day.last().value }
            .filter { it >= 0.0 }
        return if (counted.isEmpty()) null else counted.average()
    }

    /** The most recent value on or before [today], for the input that is shown as today's. */
    fun latest(values: List<DailyValue>, today: LocalDate): Double? =
        values.filter { it.date <= today }.maxByOrNull { it.date }?.value
}
