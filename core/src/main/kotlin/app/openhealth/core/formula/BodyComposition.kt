package app.openhealth.core.formula

import app.openhealth.core.citation.CitationKey
import kotlin.math.log10
import kotlin.math.pow

/** Body mass index and its WHO bands. */
enum class BmiCategory {
    BELOW_RANGE,
    IN_RANGE,
    ABOVE_RANGE,
    HIGH,
    VERY_HIGH,
    EXTREMELY_HIGH,
}

object Bmi {
    private const val MIN_KG = 1.0
    private const val MAX_KG = 500.0
    private const val MIN_CM = 50.0
    private const val MAX_CM = 260.0
    private const val BELOW = 18.5
    private const val ABOVE = 25.0
    private const val HIGH = 30.0
    private const val VERY_HIGH = 35.0
    private const val EXTREMELY_HIGH = 40.0
    private const val CM_PER_METRE = 100.0

    fun compute(weightKg: Double, heightCm: Double): FormulaOutcome<Double> {
        val inputs = listOf(input("weight", weightKg, "kg"), input("height", heightCm, "cm"))
        return if (weightKg !in MIN_KG..MAX_KG || heightCm !in MIN_CM..MAX_CM) {
            notAvailable(NotAvailableReason.OUT_OF_RANGE, CitationKey.WHO_BMI_2000, inputs)
        } else {
            val heightMetres = heightCm / CM_PER_METRE
            computed(weightKg / heightMetres.pow(2), CitationKey.WHO_BMI_2000, inputs)
        }
    }

    fun category(bmi: Double): BmiCategory = when {
        bmi < BELOW -> BmiCategory.BELOW_RANGE
        bmi < ABOVE -> BmiCategory.IN_RANGE
        bmi < HIGH -> BmiCategory.ABOVE_RANGE
        bmi < VERY_HIGH -> BmiCategory.HIGH
        bmi < EXTREMELY_HIGH -> BmiCategory.VERY_HIGH
        else -> BmiCategory.EXTREMELY_HIGH
    }
}

/**
 * US Navy body-fat estimate from tape circumferences (`pr.md` §6.8). The published
 * regression is fitted in inches, so each circumference is converted once, before the
 * logarithm:
 *
 * `men:   86.010 x log10(waist - neck) - 70.041 x log10(height) + 36.76`
 * `women: 163.205 x log10(waist + hip - neck) - 97.684 x log10(height) - 78.387`
 *
 * Both come from Hodgdon and Beckett (1984); the citation table records the reports.
 */
object NavyBodyFat {
    private const val CM_PER_INCH = 2.54
    private const val MIN_CM = 20.0
    private const val MAX_CM = 300.0
    private const val MIN_PERCENT = 1.0
    private const val MAX_PERCENT = 70.0
    private const val MALE_WAIST_TERM = 86.010
    private const val MALE_HEIGHT_TERM = 70.041
    private const val MALE_CONSTANT = 36.76
    private const val FEMALE_WAIST_TERM = 163.205
    private const val FEMALE_HEIGHT_TERM = 97.684
    private const val FEMALE_CONSTANT = 78.387

    fun compute(
        sex: SexAtBirth,
        heightCm: Double,
        neckCm: Double,
        waistCm: Double,
        hipCm: Double? = null,
    ): FormulaOutcome<Double> {
        val inputs = buildList {
            add(input("height", heightCm, "cm"))
            add(input("neck", neckCm, "cm"))
            add(input("waist", waistCm, "cm"))
            if (hipCm != null) add(input("hip", hipCm, "cm"))
        }
        val heightIn = heightCm / CM_PER_INCH
        val outcome: FormulaOutcome<Double> = when {
            heightCm !in MIN_CM..MAX_CM ||
                neckCm !in MIN_CM..MAX_CM ||
                waistCm !in MIN_CM..MAX_CM ->
                notAvailable(
                    NotAvailableReason.OUT_OF_RANGE,
                    CitationKey.US_NAVY_BODY_FAT_1984,
                    inputs,
                )

            sex == SexAtBirth.MALE -> percentOutcome(malePercent(heightIn, waistCm, neckCm), inputs)
            hipCm == null -> notAvailable(
                NotAvailableReason.MISSING_INPUT,
                CitationKey.US_NAVY_BODY_FAT_1984,
                inputs,
            )

            hipCm !in MIN_CM..MAX_CM -> notAvailable(
                NotAvailableReason.OUT_OF_RANGE,
                CitationKey.US_NAVY_BODY_FAT_1984,
                inputs,
            )

            else -> percentOutcome(femalePercent(heightIn, waistCm, hipCm, neckCm), inputs)
        }
        return outcome
    }

    private fun percentOutcome(
        percent: Double?,
        inputs: List<FormulaInput>,
    ): FormulaOutcome<Double> = if (percent == null || percent !in MIN_PERCENT..MAX_PERCENT) {
        notAvailable(NotAvailableReason.OUT_OF_RANGE, CitationKey.US_NAVY_BODY_FAT_1984, inputs)
    } else {
        computed(percent, CitationKey.US_NAVY_BODY_FAT_1984, inputs)
    }

    private fun malePercent(heightIn: Double, waistCm: Double, neckCm: Double): Double? {
        val waistIn = waistCm / CM_PER_INCH
        val neckIn = neckCm / CM_PER_INCH
        if (waistIn - neckIn <= 0.0) return null
        return MALE_WAIST_TERM * log10(waistIn - neckIn) -
            MALE_HEIGHT_TERM * log10(heightIn) +
            MALE_CONSTANT
    }

    private fun femalePercent(
        heightIn: Double,
        waistCm: Double,
        hipCm: Double,
        neckCm: Double,
    ): Double? {
        val waistIn = waistCm / CM_PER_INCH
        val hipIn = hipCm / CM_PER_INCH
        val neckIn = neckCm / CM_PER_INCH
        if (waistIn + hipIn - neckIn <= 0.0) return null
        return FEMALE_WAIST_TERM * log10(waistIn + hipIn - neckIn) -
            FEMALE_HEIGHT_TERM * log10(heightIn) -
            FEMALE_CONSTANT
    }

    /** A single tape measurement that implies an impossible percentage is a typo. */
    fun plausible(percent: Double): Boolean = percent in MIN_PERCENT..MAX_PERCENT
}

/**
 * Fat-free mass index: fat-free mass divided by height squared, and the height-
 * normalised form published by Schutz et al. (`pr.md` §6.8).
 */
object Ffmi {
    private const val REFERENCE_HEIGHT_M = 1.8
    private const val NORMALISATION = 6.1
    private const val MIN_KG = 1.0
    private const val MAX_KG = 500.0
    private const val MIN_CM = 50.0
    private const val MAX_CM = 260.0
    private const val CM_PER_METRE = 100.0
    private const val MIN_BODY_FAT = 0.0
    private const val MAX_BODY_FAT = 100.0

    data class Result(val fatFreeMassKg: Double, val ffmi: Double, val normalisedFfmi: Double)

    fun fromBodyFat(
        weightKg: Double,
        heightCm: Double,
        bodyFatPercent: Double,
    ): FormulaOutcome<Result> {
        val inputs = listOf(
            input("weight", weightKg, "kg"),
            input("height", heightCm, "cm"),
            input("bodyFat", bodyFatPercent, "%"),
        )
        return if (weightKg !in MIN_KG..MAX_KG ||
            heightCm !in MIN_CM..MAX_CM ||
            bodyFatPercent !in MIN_BODY_FAT..MAX_BODY_FAT
        ) {
            notAvailable(NotAvailableReason.OUT_OF_RANGE, CitationKey.FFMI_SCHUTZ_2002, inputs)
        } else {
            val fatFree = weightKg * (1.0 - bodyFatPercent / MAX_BODY_FAT)
            fromFatFreeMass(fatFree, heightCm, inputs)
        }
    }

    fun fromFatFreeMass(
        fatFreeMassKg: Double,
        heightCm: Double,
        extraInputs: List<FormulaInput> = emptyList(),
    ): FormulaOutcome<Result> {
        val inputs = listOf(
            input("fatFreeMass", fatFreeMassKg, "kg"),
            input("height", heightCm, "cm"),
        ) + extraInputs
        return if (fatFreeMassKg !in MIN_KG..MAX_KG || heightCm !in MIN_CM..MAX_CM) {
            notAvailable(NotAvailableReason.OUT_OF_RANGE, CitationKey.FFMI_SCHUTZ_2002, inputs)
        } else {
            val heightMetres = heightCm / CM_PER_METRE
            val ffmi = fatFreeMassKg / heightMetres.pow(2)
            val normalised = ffmi + NORMALISATION * (REFERENCE_HEIGHT_M - heightMetres)
            computed(
                Result(fatFreeMassKg = fatFreeMassKg, ffmi = ffmi, normalisedFfmi = normalised),
                CitationKey.FFMI_SCHUTZ_2002,
                inputs,
            )
        }
    }
}

/**
 * Lean mass from a weight and a body-fat figure (`pr.md` §6.8). It is a derivation, not a
 * measurement: the card labels it and keeps the body fat's own method, because a lean
 * mass from BIA and one from DEXA are not the same number.
 */
object LeanMass {
    private const val MIN_KG = 1.0
    private const val MAX_KG = 500.0
    private const val MIN_BODY_FAT = 1.0
    private const val MAX_BODY_FAT = 70.0
    private const val PERCENT = 100.0

    fun compute(weightKg: Double, bodyFatPercent: Double): FormulaOutcome<Double> {
        val inputs = listOf(
            input("weight", weightKg, "kg"),
            input("bodyFat", bodyFatPercent, "%"),
        )
        return if (weightKg !in MIN_KG..MAX_KG || bodyFatPercent !in MIN_BODY_FAT..MAX_BODY_FAT) {
            notAvailable(NotAvailableReason.OUT_OF_RANGE, CitationKey.APP_RULE, inputs)
        } else {
            computed(weightKg * (1.0 - bodyFatPercent / PERCENT), CitationKey.APP_RULE, inputs)
        }
    }
}
