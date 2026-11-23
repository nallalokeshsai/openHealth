package app.openhealth.core.formula

import app.openhealth.core.citation.CitationKey
import kotlin.math.roundToInt

/** A 28-day baseline for one input, with the number of days it was computed from. */
data class Baseline(val mean: Double, val days: Int) {
    init {
        require(mean > 0.0) { "a baseline mean must be positive" }
        require(days >= 0) { "baseline days must not be negative" }
    }
}

data class ReadinessInputs(
    val sleepMinutes: Double? = null,
    val sleepNeedMinutes: Double = DEFAULT_SLEEP_NEED_MINUTES,
    val restingHeartRate: Double? = null,
    val restingHeartRateBaseline: Baseline? = null,
    val hrvMillis: Double? = null,
    val hrvBaseline: Baseline? = null,
    val trainingLoad48h: Double? = null,
    val trainingLoadDailyAverage28d: Double? = null,
) {
    companion object {
        const val DEFAULT_SLEEP_NEED_MINUTES = 8.0 * 60.0
        const val MIN_BASELINE_DAYS = 7
    }
}

data class ReadinessPart(val name: String, val score: Double, val weight: Double)

data class ReadinessScore(val score: Int, val parts: List<ReadinessPart>, val version: Int = 1)

/**
 * Readiness v1 (`pr.md` §6.10). Each available input is scored 0-100 against the
 * person's own 28-day baseline, the weights are 0.35 sleep, 0.25 resting heart rate,
 * 0.20 HRV and 0.20 training load, renormalised over the inputs present. Fewer than two
 * inputs or fewer than seven days of baseline returns NotAvailable instead of a number.
 *
 * The mappings are this app's rule, versioned and shown in the explanation sheet:
 * - sleep: 100 at the sleep need, straight-line below it;
 * - resting heart rate: 100 at or below baseline, 0 at 25 % above;
 * - HRV: 0 at 75 % of baseline, 100 at baseline or above;
 * - load: 100 when the last 48 h match the usual 48 h, 0 at twice the usual load.
 */
object Readiness {
    private const val WEIGHT_SLEEP = 0.35
    private const val WEIGHT_RESTING_HEART_RATE = 0.25
    private const val WEIGHT_HRV = 0.20
    private const val WEIGHT_LOAD = 0.20
    private const val MIN_INPUTS = 2
    private const val FULL_SCORE = 100.0
    private const val MIN_SCORE = 0
    private const val MAX_SCORE = 100
    private const val RHR_ZERO_AT = 1.25
    private const val HRV_ZERO_AT = 0.75
    private const val LOAD_NORMAL_DAYS = 2.0

    fun compute(inputs: ReadinessInputs): FormulaOutcome<ReadinessScore> {
        val parts = readinessParts(inputs)
        val formulaInputs = parts.map { input(it.name, it.score, "1") }
        val baselineDays = listOfNotNull(
            inputs.restingHeartRateBaseline?.takeIf(::usable),
            inputs.hrvBaseline?.takeIf(::usable),
        ).minOfOrNull { it.days }
        val enoughData = parts.size >= MIN_INPUTS &&
            (baselineDays == null || baselineDays >= ReadinessInputs.MIN_BASELINE_DAYS)
        return if (!enoughData) {
            notAvailable(NotAvailableReason.NOT_ENOUGH_DATA, CitationKey.APP_RULE, formulaInputs)
        } else {
            val weightSum = parts.sumOf { it.weight }
            val score = parts.sumOf { it.score * it.weight } / weightSum
            val renormalised = parts.map { it.copy(weight = it.weight / weightSum) }
            computed(
                ReadinessScore(score.roundToInt().coerceIn(MIN_SCORE, MAX_SCORE), renormalised),
                CitationKey.APP_RULE,
                formulaInputs,
            )
        }
    }

    private fun readinessParts(inputs: ReadinessInputs): List<ReadinessPart> = buildList {
        inputs.sleepMinutes?.let { sleep ->
            add(ReadinessPart("sleep", sleepScore(sleep, inputs.sleepNeedMinutes), WEIGHT_SLEEP))
        }
        if (inputs.restingHeartRate != null && usable(inputs.restingHeartRateBaseline)) {
            add(
                ReadinessPart(
                    "restingHeartRate",
                    rhrScore(inputs.restingHeartRate, inputs.restingHeartRateBaseline!!.mean),
                    WEIGHT_RESTING_HEART_RATE,
                ),
            )
        }
        if (inputs.hrvMillis != null && usable(inputs.hrvBaseline)) {
            add(
                ReadinessPart(
                    "hrv",
                    hrvScore(inputs.hrvMillis, inputs.hrvBaseline!!.mean),
                    WEIGHT_HRV,
                ),
            )
        }
        if (inputs.trainingLoad48h != null && positive(inputs.trainingLoadDailyAverage28d)) {
            add(
                ReadinessPart(
                    "load",
                    loadScore(
                        inputs.trainingLoad48h,
                        inputs.trainingLoadDailyAverage28d!! * LOAD_NORMAL_DAYS,
                    ),
                    WEIGHT_LOAD,
                ),
            )
        }
    }

    private fun usable(baseline: Baseline?): Boolean =
        baseline != null && baseline.days >= ReadinessInputs.MIN_BASELINE_DAYS

    private fun positive(value: Double?): Boolean = value != null && value > 0.0

    private fun sleepScore(minutes: Double, needMinutes: Double): Double =
        (FULL_SCORE * (minutes / needMinutes).coerceIn(0.0, 1.0)).coerceIn(0.0, FULL_SCORE)

    private fun rhrScore(value: Double, baseline: Double): Double {
        val ratio = value / baseline
        val score = FULL_SCORE - (ratio - 1.0) / (RHR_ZERO_AT - 1.0) * FULL_SCORE
        return score.coerceIn(0.0, FULL_SCORE)
    }

    private fun hrvScore(value: Double, baseline: Double): Double {
        val ratio = value / baseline
        val score = (ratio - HRV_ZERO_AT) / (1.0 - HRV_ZERO_AT) * FULL_SCORE
        return score.coerceIn(0.0, FULL_SCORE)
    }

    private fun loadScore(load48h: Double, usual48h: Double): Double {
        val ratio = load48h / usual48h
        val score = FULL_SCORE - (ratio - 1.0) * FULL_SCORE
        return score.coerceIn(0.0, FULL_SCORE)
    }
}
