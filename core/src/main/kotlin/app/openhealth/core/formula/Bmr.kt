package app.openhealth.core.formula

import app.openhealth.core.citation.CitationKey

/**
 * Basal metabolic rate, Mifflin-St Jeor by default (`pr.md` §6.4):
 *
 * `BMR = 10 x kg + 6.25 x cm - 5 x age + s`, where `s` is +5 for male and -161 for
 * female. The age term needs a sex at birth; the equation has no neutral variant, and
 * this app does not invent an average (`docs/adr/0005-core-and-data.md`).
 */
enum class SexAtBirth { FEMALE, MALE }

object Bmr {
    private const val KG_TERM = 10.0
    private const val CM_TERM = 6.25
    private const val AGE_TERM = 5.0
    private const val MALE_CONSTANT = 5.0
    private const val FEMALE_CONSTANT = -161.0
    private const val MIN_AGE = 16
    private const val MAX_AGE = 120
    private const val MIN_KG = 20.0
    private const val MAX_KG = 400.0
    private const val MIN_CM = 100.0
    private const val MAX_CM = 250.0

    fun mifflinStJeor(
        sex: SexAtBirth,
        weightKg: Double,
        heightCm: Double,
        ageYears: Int,
    ): FormulaOutcome<Double> {
        val inputs = listOf(
            input("weight", weightKg, "kg"),
            input("height", heightCm, "cm"),
            input("age", ageYears.toDouble(), "a"),
        )
        val sexConstant = if (sex == SexAtBirth.MALE) MALE_CONSTANT else FEMALE_CONSTANT
        val bmr = KG_TERM * weightKg + CM_TERM * heightCm - AGE_TERM * ageYears + sexConstant
        return when {
            ageYears < MIN_AGE ->
                notAvailable(
                    NotAvailableReason.NOT_SUPPORTED,
                    CitationKey.MIFFLIN_ST_JEOR_1990,
                    inputs,
                )

            ageYears > MAX_AGE || weightKg !in MIN_KG..MAX_KG || heightCm !in MIN_CM..MAX_CM ->
                notAvailable(
                    NotAvailableReason.OUT_OF_RANGE,
                    CitationKey.MIFFLIN_ST_JEOR_1990,
                    inputs,
                )

            bmr <= 0.0 ->
                notAvailable(
                    NotAvailableReason.OUT_OF_RANGE,
                    CitationKey.MIFFLIN_ST_JEOR_1990,
                    inputs,
                )

            else -> computed(bmr, CitationKey.MIFFLIN_ST_JEOR_1990, inputs)
        }
    }
}
