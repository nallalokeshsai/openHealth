package app.openhealth.core.preferences

/**
 * The thirteen switchable modules of `pr.md` §6.21. The key is the stable storage value;
 * a label is UI copy and lives with the screens.
 *
 * Turning a module off hides its surfaces and keeps its data (`pr.md` §2.4, §6.21), so
 * this enum is the only place the set is defined. A new module is added here and in the
 * same migration that gives it its tables.
 */
enum class HealthModule(val key: String) {
    TRAINING("training"),
    NUTRITION("nutrition"),
    STEPS("steps"),
    YOGA("yoga"),
    BODY("body"),
    VITALS("vitals"),
    SLEEP("sleep"),
    ACTIVITIES("activities"),
    MENTAL_HEALTH("mental_health"),
    CYCLE("cycle"),
    FAMILY("family"),
    COACH("coach"),
    INTEGRATIONS("integrations"),
    ;

    companion object {
        fun fromKey(key: String?): HealthModule? = entries.firstOrNull { it.key == key }
    }
}

/**
 * The three presets of `pr.md` §6.1 and §6.21. `EVERYTHING` is the default for a new
 * profile; the other two hide what the person said they do not use.
 */
enum class ModulePreset(val key: String, val enabled: Set<HealthModule>) {
    JUST_TRAINING(
        "just_training",
        setOf(HealthModule.TRAINING, HealthModule.STEPS, HealthModule.BODY, HealthModule.SLEEP),
    ),
    TRAINING_AND_BODY(
        "training_and_body",
        setOf(
            HealthModule.TRAINING,
            HealthModule.NUTRITION,
            HealthModule.STEPS,
            HealthModule.BODY,
            HealthModule.VITALS,
            HealthModule.SLEEP,
            HealthModule.ACTIVITIES,
        ),
    ),
    EVERYTHING("everything", HealthModule.entries.toSet()),

    /** Not a preset the person picks: the state after they change one switch. */
    CUSTOM("custom", emptySet()),
    ;

    companion object {
        fun fromKey(key: String?): ModulePreset? = entries.firstOrNull { it.key == key }
    }
}
