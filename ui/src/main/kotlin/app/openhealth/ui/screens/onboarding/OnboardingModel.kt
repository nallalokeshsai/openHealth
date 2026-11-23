package app.openhealth.ui.screens.onboarding

import app.openhealth.core.formula.ActivityLevel
import app.openhealth.core.formula.SexAtBirth
import app.openhealth.core.preferences.DietaryPattern
import app.openhealth.core.preferences.Equipment
import app.openhealth.core.preferences.ModulePreset
import app.openhealth.core.preferences.ProfileGoal
import app.openhealth.core.preferences.TrainingExperience
import app.openhealth.core.preferences.UnitSystem

/**
 * The onboarding flow of `pr.md` §6.1 and `DESIGN.md` §14.2: one question per screen,
 * three tiers, and nothing asked twice.
 *
 * Tier 1 is required, tier 2 is recommended and tier 3 is optional. A step can be
 * skipped unless [OnboardingStep.required].
 */
enum class OnboardingStep(val required: Boolean = false, val tier: Int = 1) {
    Welcome(tier = 0),
    Name(required = true),
    BirthDate(required = true),
    Sex(required = true),
    Height(required = true),
    Weight(required = true),
    Units(required = true),
    Goal(required = true),
    WeightNeutral,
    TierTwoIntro(tier = 2),
    Activity(tier = 2),
    Experience(tier = 2),
    Equipment(tier = 2),
    Diet(tier = 2),
    Allergies(tier = 2),
    Sleep(tier = 2),
    Modules(tier = 2),
    TierThreeIntro(tier = 3),
    BodyFat(tier = 3),
    RestingHeartRate(tier = 3),
    BloodPressure(tier = 3),
    Measurements(tier = 3),
    Injuries(tier = 3),
    Medications(tier = 3),
    Cycle(tier = 3),
    GoalWeight(tier = 3),
    Finish(tier = 3),
}

/** How a body-fat percentage was obtained (`pr.md` §6.1 tier 3). */
enum class CompositionSource(val key: String) {
    SCALE("scale"),
    CALIPERS("calipers"),
    BIA("bia"),
    DEXA("dexa"),
    OTHER("other"),
    ;

    companion object {
        fun fromKey(key: String?): CompositionSource? = entries.firstOrNull { it.key == key }
    }
}

/** A validation message the screen renders under the field, never a toast. */
enum class OnboardingIssue {
    NAME_REQUIRED,
    DATE_INVALID,
    DATE_UNDER_SIXTEEN,
    VALUE_REQUIRED,
    VALUE_OUT_OF_RANGE,
    GOAL_REQUIRED,
    REQUIRED,
}

/**
 * The answers as typed. Numbers stay strings until submit so a half-typed decimal is
 * never coerced to 0; the state holder parses and validates them.
 */
data class OnboardingAnswers(
    val name: String = "",
    val birthDate: String = "",
    val sexAtBirth: SexAtBirth? = null,
    val preferNotToSay: Boolean = false,
    val height: String = "",
    val weight: String = "",
    val units: UnitSystem = UnitSystem.METRIC,
    val goal: ProfileGoal? = null,
    val weightNeutral: Boolean = false,
    val activity: ActivityLevel? = null,
    val experience: TrainingExperience? = null,
    val equipment: Set<Equipment> = emptySet(),
    val diet: DietaryPattern? = null,
    val allergies: String = "",
    val sleepStartHour: Int = 23,
    val sleepEndHour: Int = 7,
    val preset: ModulePreset = ModulePreset.EVERYTHING,
    val bodyFat: String = "",
    val restingHeartRate: String = "",
    val systolic: String = "",
    val diastolic: String = "",
    val neck: String = "",
    val shoulders: String = "",
    val chest: String = "",
    val waist: String = "",
    val hips: String = "",
    val upperArm: String = "",
    val forearm: String = "",
    val thigh: String = "",
    val calf: String = "",
    val wrist: String = "",
    val ankle: String = "",
    val compositionSource: CompositionSource? = null,
    val injuries: String = "",
    val medications: String = "",
    val cycleTracking: Boolean = false,
    val goalWeight: String = "",
    val goalDate: String = "",
)
