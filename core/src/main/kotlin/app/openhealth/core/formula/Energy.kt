package app.openhealth.core.formula

import app.openhealth.core.citation.CitationKey

/** Physical activity levels used to turn a BMR into a TDEE (`pr.md` §6.4). */
enum class ActivityLevel(val pal: Double, val labelKey: String) {
    SEDENTARY(pal = 1.2, labelKey = "activity.sedentary"),
    LIGHTLY_ACTIVE(pal = 1.375, labelKey = "activity.light"),
    MODERATELY_ACTIVE(pal = 1.55, labelKey = "activity.moderate"),
    VERY_ACTIVE(pal = 1.725, labelKey = "activity.very_active"),
    EXTREMELY_ACTIVE(pal = 1.9, labelKey = "activity.extremely_active"),
}

object Tdee {
    private const val MIN_BMR = 500.0
    private const val MAX_BMR = 6_000.0

    fun fromBmr(bmrKcal: Double, level: ActivityLevel): FormulaOutcome<Double> {
        val inputs = listOf(input("bmr", bmrKcal, "kcal/d"), input("pal", level.pal, "1"))
        if (bmrKcal !in MIN_BMR..MAX_BMR) {
            return notAvailable(
                NotAvailableReason.OUT_OF_RANGE,
                CitationKey.FAO_WHO_UNU_2004_PAL,
                inputs,
            )
        }
        return computed(bmrKcal * level.pal, CitationKey.FAO_WHO_UNU_2004_PAL, inputs)
    }
}

/**
 * Exercise energy from a MET value: `kcal = MET x 3.5 x kg / 200 x minutes`
 * (`pr.md` §6.4). MET values come from the bundled table, each row citing the
 * Compendium; the app never guesses a MET for an unknown activity.
 */
object MetEnergy {
    private const val ML_O2_PER_KG_PER_MIN = 3.5
    private const val KCAL_PER_LITRE_O2 = 5.0
    private const val ML_PER_LITRE = 1_000.0
    private const val MIN_KG = 20.0
    private const val MAX_KG = 400.0
    private const val MAX_MINUTES = 24.0 * 60.0

    data class Result(val kilocalories: Double, val met: Double, val minutes: Double)

    fun compute(met: Double, weightKg: Double, minutes: Double): FormulaOutcome<Result> {
        val inputs = listOf(
            input("met", met, "MET"),
            input("weight", weightKg, "kg"),
            input("minutes", minutes, "min"),
        )
        if (met <= 0.0 || weightKg !in MIN_KG..MAX_KG || minutes !in 0.0..MAX_MINUTES) {
            return notAvailable(
                NotAvailableReason.OUT_OF_RANGE,
                CitationKey.COMPENDIUM_OF_PHYSICAL_ACTIVITIES_2024,
                inputs,
            )
        }
        val litresO2 = met * ML_O2_PER_KG_PER_MIN * weightKg / ML_PER_LITRE * minutes
        val kcal = litresO2 * KCAL_PER_LITRE_O2
        return computed(
            Result(kilocalories = kcal, met = met, minutes = minutes),
            CitationKey.COMPENDIUM_OF_PHYSICAL_ACTIVITIES_2024,
            inputs,
        )
    }

    fun fromActivity(
        activity: MetActivity,
        weightKg: Double,
        minutes: Double,
    ): FormulaOutcome<Result> = compute(activity.met, weightKg, minutes)

    /** The broom: the range the estimate sits in, never a single false number. */
    fun range(result: Result): ClosedFloatingPointRange<Double> {
        val low = result.kilocalories * RANGE_LOW
        val high = result.kilocalories * RANGE_HIGH
        return low..high
    }

    private const val RANGE_LOW = 0.8
    private const val RANGE_HIGH = 1.2
}

/**
 * Goal rate and the safety floors around it (`pr.md` §6.4). The request is clamped, not
 * refused: the sheet shows the cited range and the reason.
 */
data class GoalRate(
    val requestedKgPerWeek: Double,
    val appliedKgPerWeek: Double,
    val clamped: Boolean,
    /** The range this app allows for the mode; its source is the app rule. */
    val allowedRangeKgPerWeek: ClosedFloatingPointRange<Double>,
    /** The published loss range the neutral note cites (CDC, 0.5-1 kg per week). */
    val citedLossRangeKgPerWeek: ClosedFloatingPointRange<Double> = CITED_LOSS_RANGE,
) {
    val isLoss: Boolean get() = appliedKgPerWeek < 0.0

    companion object {
        val CITED_LOSS_RANGE = 0.5..1.0
    }
}

object GoalRatePolicy {
    private const val MIN_LOSS_FRACTION = 0.0025
    private const val MAX_LOSS_FRACTION = 0.01
    private const val MAX_GAIN_FRACTION = 0.005
    private const val MIN_WEIGHT_KG = 20.0
    private const val MAX_WEIGHT_KG = 400.0

    fun clamp(
        mode: GoalMode,
        requestedKgPerWeek: Double,
        weightKg: Double,
    ): FormulaOutcome<GoalRate> {
        val inputs = listOf(
            input("requested", requestedKgPerWeek, "kg/wk"),
            input("weight", weightKg, "kg"),
        )
        if (weightKg !in MIN_WEIGHT_KG..MAX_WEIGHT_KG || !requestedKgPerWeek.isFinite()) {
            return notAvailable(
                NotAvailableReason.OUT_OF_RANGE,
                CitationKey.CDC_WEIGHT_LOSS,
                inputs,
            )
        }
        return when (mode) {
            GoalMode.MAINTAIN -> computed(
                GoalRate(
                    requestedKgPerWeek = requestedKgPerWeek,
                    appliedKgPerWeek = 0.0,
                    clamped = requestedKgPerWeek != 0.0,
                    allowedRangeKgPerWeek = 0.0..0.0,
                ),
                CitationKey.CDC_WEIGHT_LOSS,
                inputs,
            )

            GoalMode.LOSE -> {
                val min = -MAX_LOSS_FRACTION * weightKg
                val max = -MIN_LOSS_FRACTION * weightKg
                val applied = requestedKgPerWeek.coerceIn(min, max)
                computed(
                    GoalRate(
                        requestedKgPerWeek = requestedKgPerWeek,
                        appliedKgPerWeek = applied,
                        clamped = applied != requestedKgPerWeek,
                        allowedRangeKgPerWeek = min..max,
                    ),
                    CitationKey.CDC_WEIGHT_LOSS,
                    inputs,
                )
            }

            GoalMode.GAIN -> {
                val min = MIN_LOSS_FRACTION * weightKg
                val max = MAX_GAIN_FRACTION * weightKg
                val applied = requestedKgPerWeek.coerceIn(min, max)
                computed(
                    GoalRate(
                        requestedKgPerWeek = requestedKgPerWeek,
                        appliedKgPerWeek = applied,
                        clamped = applied != requestedKgPerWeek,
                        allowedRangeKgPerWeek = min..max,
                    ),
                    CitationKey.CDC_WEIGHT_LOSS,
                    inputs,
                )
            }
        }
    }
}

enum class GoalMode { LOSE, MAINTAIN, GAIN }

/**
 * Energy safety floors: the app never proposes an intake below the higher of a cited
 * absolute floor and the person's own BMR, and shows a neutral note when a request is
 * clamped (`pr.md` §6.4, §12.1).
 */
object EnergySafety {
    private const val ABSOLUTE_FLOOR_FEMALE = 1_200.0
    private const val ABSOLUTE_FLOOR_MALE = 1_500.0

    data class Floors(val minimumIntakeKcal: Double, val appliedRuleKey: String)

    fun floors(sex: SexAtBirth, bmrKcal: Double): FormulaOutcome<Floors> {
        if (!bmrKcal.isFinite() || bmrKcal <= 0.0) {
            return notAvailable(
                NotAvailableReason.OUT_OF_RANGE,
                CitationKey.APP_RULE,
                listOf(input("bmr", bmrKcal, "kcal/d")),
            )
        }
        val absolute = when (sex) {
            SexAtBirth.FEMALE -> ABSOLUTE_FLOOR_FEMALE
            SexAtBirth.MALE -> ABSOLUTE_FLOOR_MALE
        }
        val floor = maxOf(absolute, bmrKcal)
        return computed(
            Floors(floor, if (floor > absolute) "floor.above_bmr" else "floor.absolute"),
            CitationKey.APP_RULE,
            listOf(
                input("bmr", bmrKcal, "kcal/d"),
                input("absoluteFloor", absolute, "kcal/d"),
            ),
        )
    }
}
