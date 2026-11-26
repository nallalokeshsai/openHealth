@file:Suppress(
    "ComplexCondition",
    "ReturnCount",
    "MagicNumber",
)

package app.openhealth.core.activity

import app.openhealth.core.citation.CitationKey
import app.openhealth.core.formula.FormulaOutcome
import app.openhealth.core.formula.computed
import app.openhealth.core.formula.input
import kotlin.math.exp
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * One day's training stress in the app's relative load unit. Cardio is weighted by the
 * Banister heart-rate ratio when heart rate exists, otherwise by duration and MET;
 * strength is session RPE times minutes (`pr.md` §6.11 V1: TRIMP, CTL/ATL/TSB).
 *
 * Load is a relative number for comparing a person with themself. It is not a calorie,
 * not a medical measure and never a target.
 */
object TrainingLoadMath {
    private const val SECONDS_PER_MINUTE = 60.0
    private const val TRIMP_MALE_A = 0.64
    private const val TRIMP_MALE_B = 1.92
    private const val TRIMP_FEMALE_A = 0.86
    private const val TRIMP_FEMALE_B = 1.67
    private const val MIN_HR_RATIO = 0.0
    private const val MAX_HR_RATIO = 1.2

    /** Banister TRIMP for a steady effort with an average heart rate. */
    fun cardioLoad(
        minutes: Double,
        averageHeartRate: Double?,
        restingHeartRate: Double?,
        maxHeartRate: Double?,
        female: Boolean = false,
    ): Double {
        if (minutes <= 0.0) return 0.0
        val average = averageHeartRate
        val resting = restingHeartRate
        val maximum = maxHeartRate
        if (average == null || resting == null || maximum == null || maximum <= resting) {
            return 0.0
        }
        val ratio = ((average - resting) / (maximum - resting)).coerceIn(MIN_HR_RATIO, MAX_HR_RATIO)
        val a = if (female) TRIMP_FEMALE_A else TRIMP_MALE_A
        val b = if (female) TRIMP_FEMALE_B else TRIMP_MALE_B
        return minutes * ratio * a * exp(b * ratio)
    }

    /** The fallback when no heart-rate series exists: minutes times MET. */
    fun metLoad(minutes: Double, met: Double): Double =
        if (minutes <= 0.0 || met <= 0.0) 0.0 else minutes * met

    /** Session RPE: minutes times a 0-10 effort, the strength-training convention. */
    fun srpeLoad(minutes: Double, effort: Double?): Double {
        if (minutes <= 0.0) return 0.0
        val rpe = effort?.coerceIn(0.0, 10.0) ?: DEFAULT_STRENGTH_RPE
        return minutes * rpe
    }

    /** Load for a finished activity, preferring its measured heart rate. */
    fun activityLoad(
        activity: ActivityRecord,
        restingHeartRate: Double?,
        maxHeartRate: Double?,
        female: Boolean = false,
    ): Double {
        val minutes = activity.durationSeconds / SECONDS_PER_MINUTE
        val trimp = cardioLoad(
            minutes = minutes,
            averageHeartRate = activity.averageHeartRate,
            restingHeartRate = restingHeartRate,
            maxHeartRate = maxHeartRate,
            female = female,
        )
        return if (trimp > 0.0) trimp else metLoad(minutes, activity.type.met.met)
    }

    const val DEFAULT_STRENGTH_RPE = 6.0
}

/** One day of load, with the sessions that produced it for the explanation sheet. */
data class LoadDay(
    val date: LocalDate,
    val load: Double,
    val activities: Int = 0,
    val strengthSessions: Int = 0,
)

data class TrainingLoadPoint(
    val date: LocalDate,
    val load: Double,
    val fitness: Double,
    val fatigue: Double,
    val form: Double,
)

/**
 * Fitness, fatigue and form as exponentially weighted averages of daily load
 * (`CitationKey.ALLEN_COGGAN_2019`): fitness uses a 42-day time constant, fatigue 7 days,
 * and form is fitness minus fatigue. A missing day is a zero-load day, which is the
 * correct input to a decay, not a gap in the chart.
 */
data class TrainingLoadModel(
    val points: List<TrainingLoadPoint>,
    val fitness: Double,
    val fatigue: Double,
    val form: Double,
    val hasData: Boolean,
) {
    val today: TrainingLoadPoint? get() = points.lastOrNull()
}

object TrainingLoad {
    const val FITNESS_DAYS = 42.0
    const val FATIGUE_DAYS = 7.0
    const val DEFAULT_WINDOW_DAYS = 90

    fun compute(
        endDate: LocalDate,
        days: List<LoadDay>,
        windowDays: Int = DEFAULT_WINDOW_DAYS,
        fitnessDays: Double = FITNESS_DAYS,
        fatigueDays: Double = FATIGUE_DAYS,
    ): FormulaOutcome<TrainingLoadModel> {
        if (windowDays <= 0 || fitnessDays <= 0.0 || fatigueDays <= 0.0) {
            return FormulaOutcome.NotAvailable(
                reason = app.openhealth.core.formula.NotAvailableReason.OUT_OF_RANGE,
                citation = CitationKey.ALLEN_COGGAN_2019,
            )
        }
        val byDate = days.groupBy { it.date }.mapValues { (_, rows) ->
            rows.sumOf { it.load }
        }
        val start = endDate.minus(DatePeriod(days = windowDays - 1))
        var fitness = 0.0
        var fatigue = 0.0
        // Warm the averages from the earliest available load so a 90-day window is not
        // dominated by the artificial zero before the first session.
        val first = byDate.keys.minOrNull()
        if (first != null) {
            var cursor: LocalDate = first
            while (cursor < start) {
                val load = byDate[cursor] ?: 0.0
                fitness += (load - fitness) / fitnessDays
                fatigue += (load - fatigue) / fatigueDays
                cursor = cursor.plus(DatePeriod(days = 1))
            }
        }
        val points = mutableListOf<TrainingLoadPoint>()
        var cursor = start
        while (cursor <= endDate) {
            val load = byDate[cursor] ?: 0.0
            fitness += (load - fitness) / fitnessDays
            fatigue += (load - fatigue) / fatigueDays
            points += TrainingLoadPoint(
                date = cursor,
                load = load,
                fitness = fitness,
                fatigue = fatigue,
                form = fitness - fatigue,
            )
            cursor = cursor.plus(DatePeriod(days = 1))
        }
        val last = points.last()
        return computed(
            TrainingLoadModel(
                points = points,
                fitness = last.fitness,
                fatigue = last.fatigue,
                form = last.form,
                hasData = byDate.values.any { it > 0.0 },
            ),
            CitationKey.ALLEN_COGGAN_2019,
            listOf(
                input("fitness_days", fitnessDays, "d"),
                input("fatigue_days", fatigueDays, "d"),
                input("window_days", windowDays.toDouble(), "d"),
            ),
        )
    }

    /** Load days from activities and finished strength sessions. */
    fun loadDays(
        activities: List<ActivityRecord>,
        strengthSessions: List<StrengthLoad>,
        restingHeartRate: Double?,
        maxHeartRate: Double?,
        female: Boolean = false,
    ): List<LoadDay> {
        val byDate = mutableMapOf<LocalDate, LoadDay>()
        activities.forEach { activity ->
            val load = TrainingLoadMath.activityLoad(
                activity,
                restingHeartRate,
                maxHeartRate,
                female,
            )
            val existing = byDate[activity.localDate]
            byDate[activity.localDate] = LoadDay(
                date = activity.localDate,
                load = (existing?.load ?: 0.0) + load,
                activities = (existing?.activities ?: 0) + 1,
                strengthSessions = existing?.strengthSessions ?: 0,
            )
        }
        strengthSessions.forEach { session ->
            val minutes = session.durationSeconds / 60.0
            val load = TrainingLoadMath.srpeLoad(minutes, session.perceivedEffort)
            val existing = byDate[session.date]
            byDate[session.date] = LoadDay(
                date = session.date,
                load = (existing?.load ?: 0.0) + load,
                activities = existing?.activities ?: 0,
                strengthSessions = (existing?.strengthSessions ?: 0) + 1,
            )
        }
        return byDate.values.sortedBy { it.date }
    }

    /** A finished strength session, reduced to what a load needs. */
    data class StrengthLoad(
        val date: LocalDate,
        val durationSeconds: Long,
        val perceivedEffort: Double?,
    )

    /** A short neutral word for the form value, never a diagnosis or a warning. */
    fun formBand(form: Double): String = when {
        form > 5.0 -> "form.fresh"
        form < -10.0 -> "form.loaded"
        else -> "form.neutral"
    }
}
