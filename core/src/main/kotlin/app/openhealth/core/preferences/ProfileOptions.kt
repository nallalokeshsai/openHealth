package app.openhealth.core.preferences

/**
 * The profile choices of `pr.md` §6.1. Each enum's key is the value stored in the
 * profile or settings row; the label is UI copy.
 *
 * Sex at birth lives in `core.formula.SexAtBirth` because the BMR and Navy formulas take
 * it; "prefer not to say" is simply the absence of a value, never a third enum constant
 * that the formulas would have to reject.
 */
enum class UnitSystem(val key: String) {
    METRIC("metric"),
    IMPERIAL("imperial"),
    ;

    companion object {
        fun fromKey(key: String?): UnitSystem? = entries.firstOrNull { it.key == key }
    }
}

/** The six goals of `pr.md` §6.1. [wantsWeightChange] gates the 16-17 rule. */
enum class ProfileGoal(val key: String, val wantsWeightLoss: Boolean = false) {
    LOSE_FAT("lose_fat", wantsWeightLoss = true),
    MAINTAIN("maintain"),
    GAIN_MUSCLE("gain_muscle"),
    IMPROVE_HEALTH("improve_health"),
    MANAGE_CONDITION("manage_condition"),
    JUST_TRACK("just_track"),
    ;

    companion object {
        fun fromKey(key: String?): ProfileGoal? = entries.firstOrNull { it.key == key }
    }
}

enum class TrainingExperience(val key: String) {
    NONE("none"),
    BEGINNER("beginner"),
    INTERMEDIATE("intermediate"),
    ADVANCED("advanced"),
    ;

    companion object {
        fun fromKey(key: String?): TrainingExperience? = entries.firstOrNull { it.key == key }
    }
}

enum class Equipment(val key: String) {
    BODYWEIGHT("bodyweight"),
    DUMBBELLS("dumbbells"),
    BARBELL("barbell"),
    MACHINES("machines"),
    BANDS("bands"),
    CARDIO("cardio"),
    ;

    companion object {
        fun fromKey(key: String?): Equipment? = entries.firstOrNull { it.key == key }
    }
}

enum class DietaryPattern(val key: String) {
    OMNIVORE("omnivore"),
    VEGETARIAN("vegetarian"),
    VEGAN("vegan"),
    PESCATARIAN("pescatarian"),
    HALAL("halal"),
    KOSHER("kosher"),
    OTHER("other"),
    ;

    companion object {
        fun fromKey(key: String?): DietaryPattern? = entries.firstOrNull { it.key == key }
    }
}
