@file:Suppress("MagicNumber")

package app.openhealth.core.formula

import app.openhealth.core.citation.CitationKey
import kotlin.math.abs

/** A figure that is an estimate: a central value and the interval it is shown in. */
data class EnergyRange(val low: Double, val central: Double, val high: Double) {
    init {
        require(low <= central && central <= high) { "an energy range must contain its centre" }
    }

    /** The half-width in words, for the sheet: "plus or minus 140 kcal". */
    val uncertainty: Double get() = (high - low) / 2.0
}

/**
 * Energy burned by walking, from the steps the day's rollup kept (`pr.md` §6.4).
 *
 * The estimate is built only from published or documented constants: an average adult
 * walking stride of 0.762 m and a 4 km/h walk at 3.0 MET, then the Compendium's MET
 * equation `kcal = MET x 3.5 x kg / 200 x minutes`. The stride and the cadence are this
 * app's stated defaults, and the result is always a range because a step count is not a
 * measurement of effort.
 */
object StepEnergy {
    const val STRIDE_METRES = 0.762
    const val WALKING_SPEED_METRES_PER_MINUTE = 66.6667
    const val WALKING_MET = 3.0
    private const val RANGE_LOW = 0.75
    private const val RANGE_HIGH = 1.25
    private const val MIN_KG = 20.0
    private const val MAX_KG = 400.0
    private const val MAX_STEPS = 200_000

    @Suppress("ReturnCount")
    fun compute(steps: Double, weightKg: Double): FormulaOutcome<EnergyRange> {
        val inputs = listOf(
            input("steps", steps, "{count}"),
            input("weight", weightKg, "kg"),
            input("stride", STRIDE_METRES, "m"),
            input("met", WALKING_MET, "MET"),
        )
        if (steps < 0.0 || steps > MAX_STEPS || weightKg !in MIN_KG..MAX_KG) {
            return notAvailable(
                NotAvailableReason.OUT_OF_RANGE,
                CitationKey.COMPENDIUM_OF_PHYSICAL_ACTIVITIES_2024,
                inputs,
            )
        }
        val distanceMetres = steps * STRIDE_METRES
        val minutes = distanceMetres / WALKING_SPEED_METRES_PER_MINUTE
        val central = MetEnergy.compute(WALKING_MET, weightKg, minutes)
            .let { (it as? FormulaOutcome.Computed)?.value?.kilocalories }
            ?: return notAvailable(
                NotAvailableReason.OUT_OF_RANGE,
                CitationKey.COMPENDIUM_OF_PHYSICAL_ACTIVITIES_2024,
                inputs,
            )
        return computed(
            EnergyRange(central * RANGE_LOW, central, central * RANGE_HIGH),
            CitationKey.COMPENDIUM_OF_PHYSICAL_ACTIVITIES_2024,
            inputs,
        )
    }
}

/** The metabolic cost of a logged workout, with the MET range's own uncertainty. */
object WorkoutEnergy {
    private const val RANGE_LOW = 0.8
    private const val RANGE_HIGH = 1.2

    fun compute(met: Double, weightKg: Double, minutes: Double): FormulaOutcome<EnergyRange> =
        when (val outcome = MetEnergy.compute(met, weightKg, minutes)) {
            is FormulaOutcome.Computed -> computed(
                EnergyRange(
                    outcome.value.kilocalories * RANGE_LOW,
                    outcome.value.kilocalories,
                    outcome.value.kilocalories * RANGE_HIGH,
                ),
                outcome.citation,
                outcome.inputs,
            )

            is FormulaOutcome.NotAvailable -> outcome
        }

    /** From a workout whose energy is already known (an imported activity). */
    fun fromKcal(kcal: Double): FormulaOutcome<EnergyRange> {
        val inputs = listOf(input("kcal", kcal, "kcal"))
        if (kcal < 0.0 || kcal > 20_000.0) {
            return notAvailable(
                NotAvailableReason.OUT_OF_RANGE,
                CitationKey.COMPENDIUM_OF_PHYSICAL_ACTIVITIES_2024,
                inputs,
            )
        }
        return computed(
            EnergyRange(kcal * RANGE_LOW, kcal, kcal * RANGE_HIGH),
            CitationKey.COMPENDIUM_OF_PHYSICAL_ACTIVITIES_2024,
            inputs,
        )
    }
}

/**
 * The day's energy balance (`pr.md` §6.4): in is the food log, out is BMR plus
 * non-exercise activity from steps plus logged and imported workouts, and net is
 * `in - out`, shown as a range from the uncertainty of the burn estimate. A missing part
 * is left out and named, never guessed.
 */
data class EnergyBalance(
    val intakeKcal: Double,
    val bmrKcal: Double,
    val stepsKcal: EnergyRange?,
    val workoutKcal: EnergyRange?,
    val outKcal: EnergyRange,
    val netKcal: EnergyRange,
    val targetKcal: Double?,
    val remainingKcal: Double?,
)

object EnergyBalanceCalculator {
    private const val BMR_RANGE = 0.05
    private const val MIN_INTAKE = 0.0
    private const val MAX_INTAKE = 20_000.0
    private const val MIN_BMR = 500.0
    private const val MAX_BMR = 6_000.0

    fun compute(
        intakeKcal: Double,
        bmrKcal: Double,
        stepsKcal: EnergyRange? = null,
        workoutKcal: EnergyRange? = null,
        targetKcal: Double? = null,
    ): FormulaOutcome<EnergyBalance> {
        val inputs = listOf(
            input("intake", intakeKcal, "kcal"),
            input("bmr", bmrKcal, "kcal"),
            input("steps", stepsKcal?.central ?: 0.0, "kcal"),
            input("workouts", workoutKcal?.central ?: 0.0, "kcal"),
        )
        if (intakeKcal !in MIN_INTAKE..MAX_INTAKE || bmrKcal !in MIN_BMR..MAX_BMR) {
            return notAvailable(
                NotAvailableReason.OUT_OF_RANGE,
                CitationKey.MIFFLIN_ST_JEOR_1990,
                inputs,
            )
        }
        val outLow = bmrKcal * (1.0 - BMR_RANGE) +
            (stepsKcal?.low ?: 0.0) + (workoutKcal?.low ?: 0.0)
        val outCentral = bmrKcal + (stepsKcal?.central ?: 0.0) + (workoutKcal?.central ?: 0.0)
        val outHigh = bmrKcal * (1.0 + BMR_RANGE) +
            (stepsKcal?.high ?: 0.0) + (workoutKcal?.high ?: 0.0)
        // A range is reversed when subtracted: the low net comes from the high burn.
        val net = EnergyRange(
            low = intakeKcal - outHigh,
            central = intakeKcal - outCentral,
            high = intakeKcal - outLow,
        )
        return computed(
            EnergyBalance(
                intakeKcal = intakeKcal,
                bmrKcal = bmrKcal,
                stepsKcal = stepsKcal,
                workoutKcal = workoutKcal,
                outKcal = EnergyRange(outLow, outCentral, outHigh),
                netKcal = net,
                targetKcal = targetKcal,
                remainingKcal = targetKcal?.let { it - intakeKcal },
            ),
            CitationKey.COMPENDIUM_OF_PHYSICAL_ACTIVITIES_2024,
            inputs,
        )
    }

    /** `true` when the two ends of the range sit on the same side of zero. */
    fun isDeficit(net: EnergyRange): Boolean = net.high < 0.0

    fun isSurplus(net: EnergyRange): Boolean = net.low > 0.0

    fun crossesZero(net: EnergyRange): Boolean = !isDeficit(net) && !isSurplus(net)

    /** The weekly rate a daily energy change implies, via the 7,700 kcal/kg rule. */
    fun weeklyRateKg(dailyDeltaKcal: Double): Double = dailyDeltaKcal * 7.0 / 7_700.0

    fun isFlat(dailyDeltaKcal: Double, toleranceKcal: Double = 25.0): Boolean =
        abs(dailyDeltaKcal) <= toleranceKcal
}
