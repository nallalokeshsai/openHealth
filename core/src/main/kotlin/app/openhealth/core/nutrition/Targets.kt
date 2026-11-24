package app.openhealth.core.nutrition

import app.openhealth.core.citation.CitationKey
import app.openhealth.core.formula.FormulaOutcome
import app.openhealth.core.formula.NotAvailableReason
import app.openhealth.core.formula.SexAtBirth
import app.openhealth.core.formula.computed
import app.openhealth.core.formula.input
import app.openhealth.core.formula.notAvailable

/**
 * The editable daily targets of `pr.md` §6.3. Every field is optional so a person can set
 * one number and leave the rest blank; a blank target is missing data, not zero.
 */
data class MacroTargets(
    val kcal: Double? = null,
    val proteinG: Double? = null,
    val carbsG: Double? = null,
    val fatG: Double? = null,
    val fibreG: Double? = null,
    val waterMl: Double? = null,
) {
    init {
        listOf(kcal, proteinG, carbsG, fatG, fibreG, waterMl).forEach {
            require(it == null || it >= 0.0) { "A target cannot be negative." }
        }
    }

    val isEmpty: Boolean
        get() = kcal == null && proteinG == null && carbsG == null && fatG == null &&
            fibreG == null && waterMl == null

    companion object {
        val EMPTY = MacroTargets()
    }
}

/**
 * A starting point for the targets sheet, not a prescription.
 *
 * Protein is the 0.8 g per kg body mass RDA, fat is the midpoint of the 20-35 % energy
 * acceptable range, carbohydrate is the remaining energy, and fibre is 14 g per 1,000
 * kcal — all from the Institute of Medicine's macronutrient report
 * (`IOM_MACRONUTRIENTS_2005`). Water is an app starting point, editable and labelled as
 * one, because a drinking target is not the same quantity as total water intake.
 */
object TargetSuggestions {
    private const val PROTEIN_G_PER_KG = 0.8
    private const val FAT_ENERGY_SHARE = 0.30
    private const val KCAL_PER_G_PROTEIN = 4.0
    private const val KCAL_PER_G_FAT = 9.0
    private const val KCAL_PER_G_CARB = 4.0
    private const val FIBRE_G_PER_1000_KCAL = 14.0
    private const val WATER_ML_FEMALE = 2_000.0
    private const val WATER_ML_MALE = 2_500.0
    private const val MIN_WEIGHT_KG = 20.0
    private const val MAX_WEIGHT_KG = 400.0
    private const val MIN_KCAL = 500.0
    private const val MAX_KCAL = 10_000.0

    fun suggest(sex: SexAtBirth, weightKg: Double, kcal: Double): FormulaOutcome<MacroTargets> {
        val inputs = listOf(
            input("weight", weightKg, "kg"),
            input("energy", kcal, "kcal/d"),
        )
        if (weightKg !in MIN_WEIGHT_KG..MAX_WEIGHT_KG || kcal !in MIN_KCAL..MAX_KCAL) {
            return notAvailable(
                NotAvailableReason.OUT_OF_RANGE,
                CitationKey.IOM_MACRONUTRIENTS_2005,
                inputs,
            )
        }
        val protein = PROTEIN_G_PER_KG * weightKg
        val fat = kcal * FAT_ENERGY_SHARE / KCAL_PER_G_FAT
        val carbs = (kcal - protein * KCAL_PER_G_PROTEIN - fat * KCAL_PER_G_FAT) /
            KCAL_PER_G_CARB
        if (carbs < 0.0) {
            return notAvailable(
                NotAvailableReason.OUT_OF_RANGE,
                CitationKey.IOM_MACRONUTRIENTS_2005,
                inputs,
            )
        }
        val targets = MacroTargets(
            kcal = kcal,
            proteinG = protein,
            carbsG = carbs,
            fatG = fat,
            fibreG = kcal / 1_000.0 * FIBRE_G_PER_1000_KCAL,
            waterMl = when (sex) {
                SexAtBirth.FEMALE -> WATER_ML_FEMALE
                SexAtBirth.MALE -> WATER_ML_MALE
            },
        )
        return computed(targets, CitationKey.IOM_MACRONUTRIENTS_2005, inputs)
    }

    /** The daily energy a requested rate implies, by the 7,700 kcal per kg rule. */
    fun energyForRate(tdeeKcal: Double, appliedKgPerWeek: Double): FormulaOutcome<Double> {
        val inputs = listOf(
            input("tdee", tdeeKcal, "kcal/d"),
            input("rate", appliedKgPerWeek, "kg/wk"),
        )
        if (!tdeeKcal.isFinite() || tdeeKcal <= 0.0 || !appliedKgPerWeek.isFinite()) {
            return notAvailable(
                NotAvailableReason.OUT_OF_RANGE,
                CitationKey.WISHNOFSKY_1958,
                inputs,
            )
        }
        val dailyDelta = appliedKgPerWeek * KCAL_PER_KG / DAYS_PER_WEEK
        return computed(tdeeKcal + dailyDelta, CitationKey.WISHNOFSKY_1958, inputs)
    }

    private const val KCAL_PER_KG = 7_700.0
    private const val DAYS_PER_WEEK = 7.0
}
