package app.openhealth.core.validate

import app.openhealth.core.citation.CitationKey
import app.openhealth.core.citation.Citations
import app.openhealth.core.observation.Observation
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.units.UcumUnit
import kotlin.time.Instant

/**
 * A plausibility range: a screen for entry mistakes, not a clinical judgement. The copy
 * that shows it is neutral ("That is outside the plausible range") and `DESIGN.md` §11.7
 * governs the words. Values outside it are flagged for the review inbox (`pr.md` §6.28)
 * and never diagnosed.
 */
data class PlausibleRange(
    val type: ObservationType,
    val min: Double,
    val max: Double,
    val unit: UcumUnit = type.canonicalUnit,
    val citation: CitationKey = CitationKey.APP_RULE,
) {
    init {
        require(min < max) { "min $min must be below max $max" }
    }

    fun contains(value: Double): Boolean = value in min..max

    fun citationText(): String = Citations.of(citation).title
}

object PlausibilityRanges {
    private const val DAY_SECONDS = 86_400.0

    private val ranges: Map<ObservationType, PlausibleRange> = listOf(
        PlausibleRange(type = ObservationType.STEPS, min = 0.0, max = 100_000.0),
        PlausibleRange(type = ObservationType.DISTANCE, min = 0.0, max = 500_000.0),
        PlausibleRange(type = ObservationType.ACTIVE_CALORIES, min = 0.0, max = 20_000.0),
        PlausibleRange(type = ObservationType.ACTIVE_MINUTES, min = 0.0, max = 1_440.0),
        PlausibleRange(type = ObservationType.FLIGHTS_CLIMBED, min = 0.0, max = 1_000.0),
        PlausibleRange(type = ObservationType.HEART_RATE, min = 20.0, max = 300.0),
        PlausibleRange(type = ObservationType.RESTING_HEART_RATE, min = 25.0, max = 150.0),
        PlausibleRange(type = ObservationType.HEART_RATE_VARIABILITY, min = 0.002, max = 0.5),
        PlausibleRange(type = ObservationType.BLOOD_PRESSURE_SYSTOLIC, min = 40.0, max = 300.0),
        PlausibleRange(type = ObservationType.BLOOD_PRESSURE_DIASTOLIC, min = 20.0, max = 200.0),
        PlausibleRange(type = ObservationType.BLOOD_GLUCOSE, min = 0.5, max = 50.0),
        PlausibleRange(type = ObservationType.BODY_TEMPERATURE, min = 30.0, max = 45.0),
        PlausibleRange(type = ObservationType.OXYGEN_SATURATION, min = 50.0, max = 100.0),
        PlausibleRange(type = ObservationType.RESPIRATORY_RATE, min = 3.0, max = 80.0),
        PlausibleRange(type = ObservationType.WEIGHT, min = 1.0, max = 500.0),
        PlausibleRange(type = ObservationType.BODY_FAT_PERCENT, min = 1.0, max = 70.0),
        PlausibleRange(type = ObservationType.LEAN_MASS, min = 1.0, max = 200.0),
        PlausibleRange(ObservationType.SLEEP_DURATION, 0.0, DAY_SECONDS),
        PlausibleRange(ObservationType.SLEEP_DEEP, 0.0, DAY_SECONDS),
        PlausibleRange(ObservationType.SLEEP_LIGHT, 0.0, DAY_SECONDS),
        PlausibleRange(ObservationType.SLEEP_REM, 0.0, DAY_SECONDS),
        PlausibleRange(ObservationType.SLEEP_AWAKE, 0.0, DAY_SECONDS),
        PlausibleRange(ObservationType.SLEEP_IN_BED, 0.0, DAY_SECONDS),
        PlausibleRange(type = ObservationType.NUTRITION_ENERGY, min = 0.0, max = 20_000.0),
        PlausibleRange(type = ObservationType.NUTRITION_PROTEIN, min = 0.0, max = 5_000.0),
        PlausibleRange(type = ObservationType.NUTRITION_CARBS, min = 0.0, max = 5_000.0),
        PlausibleRange(type = ObservationType.NUTRITION_FAT, min = 0.0, max = 5_000.0),
        PlausibleRange(type = ObservationType.NUTRITION_FIBRE, min = 0.0, max = 500.0),
        PlausibleRange(type = ObservationType.WATER, min = 0.0, max = 20_000.0),
        PlausibleRange(type = ObservationType.CAFFEINE, min = 0.0, max = 5_000.0),
        PlausibleRange(type = ObservationType.TRAINING_VOLUME, min = 0.0, max = 200_000.0),
        PlausibleRange(type = ObservationType.TRAINING_SETS, min = 0.0, max = 500.0),
        PlausibleRange(ObservationType.TRAINING_DURATION, 0.0, DAY_SECONDS),
        PlausibleRange(type = ObservationType.MOOD, min = 1.0, max = 5.0),
        PlausibleRange(type = ObservationType.STRESS, min = 1.0, max = 5.0),
    ).associateBy(PlausibleRange::type)

    fun of(type: ObservationType): PlausibleRange =
        ranges[type] ?: error("No plausibility range declared for ${type.name}")

    fun all(): List<PlausibleRange> = ranges.values.sortedBy { it.type.name }
}

enum class ValidationIssue {
    OUTSIDE_PLAUSIBLE_RANGE,
    WRONG_UNIT,
    END_BEFORE_START,
    IN_THE_FUTURE,
    VALUE_NOT_FINITE,
}

data class ValidationResult(val issues: List<ValidationIssue>, val range: PlausibleRange? = null) {
    val isValid: Boolean get() = issues.isEmpty()

    companion object {
        val OK = ValidationResult(emptyList())
    }
}

/**
 * Validates a value the same way for the UI, an importer and the AI proposal parser
 * (`Tech-Stack.md` §9). It never edits a value: a person sees the flag and decides.
 */
object ObservationValidator {
    /** Clock skew allowance for a phone whose time is a little ahead. */
    private const val FUTURE_TOLERANCE_SECONDS = 300L

    fun validate(observation: Observation, now: Instant): ValidationResult {
        val range = PlausibilityRanges.of(observation.type)
        val issues = buildList {
            if (observation.value.unit !=
                observation.type.canonicalUnit
            ) {
                add(ValidationIssue.WRONG_UNIT)
            }
            if (!observation.value.value.isFinite()) add(ValidationIssue.VALUE_NOT_FINITE)
            if (observation.value.value.isFinite() && !range.contains(observation.value.value)) {
                add(ValidationIssue.OUTSIDE_PLAUSIBLE_RANGE)
            }
            if (observation.end != null && observation.end < observation.start) {
                add(ValidationIssue.END_BEFORE_START)
            }
            if (observation.provenance.happenedAt.epochSeconds >
                now.epochSeconds + FUTURE_TOLERANCE_SECONDS
            ) {
                add(ValidationIssue.IN_THE_FUTURE)
            }
        }
        return ValidationResult(issues, range)
    }

    fun validateNumber(type: ObservationType, canonicalValue: Double): ValidationResult {
        val range = PlausibilityRanges.of(type)
        val issues = buildList {
            if (!canonicalValue.isFinite()) add(ValidationIssue.VALUE_NOT_FINITE)
            if (canonicalValue.isFinite() && !range.contains(canonicalValue)) {
                add(ValidationIssue.OUTSIDE_PLAUSIBLE_RANGE)
            }
        }
        return ValidationResult(issues, range)
    }
}
