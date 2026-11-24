package app.openhealth.core.exercise

import app.openhealth.core.body.BodyPart
import app.openhealth.core.body.Muscle

/** Equipment an exercise needs (`pr.md` §6.5). The key is the stored value. */
enum class Equipment(val key: String, val labelKey: String) {
    BARBELL("barbell", "equipment_barbell"),
    EZ_BAR("ez-bar", "equipment_ez_bar"),
    DUMBBELL("dumbbell", "equipment_dumbbell"),
    MACHINE("machine", "equipment_machine"),
    CABLE("cable", "equipment_cable"),
    KETTLEBELL("kettlebell", "equipment_kettlebell"),
    BANDS("bands", "equipment_bands"),
    BODYWEIGHT("bodyweight", "equipment_bodyweight"),
    BALL("ball", "equipment_ball"),
    OTHER("other", "equipment_other"),
    ;

    companion object {
        private val byKey: Map<String, Equipment> = entries.associateBy { it.key }

        fun fromKey(key: String?): Equipment = key?.let { byKey[it.trim().lowercase()] } ?: OTHER
    }
}

enum class ExerciseLevel(val key: String, val labelKey: String) {
    BEGINNER("beginner", "level_beginner"),
    INTERMEDIATE("intermediate", "level_intermediate"),
    EXPERT("expert", "level_expert"),
    ;

    companion object {
        private val byKey: Map<String, ExerciseLevel> = entries.associateBy { it.key }

        fun fromKey(key: String?): ExerciseLevel =
            key?.let { byKey[it.trim().lowercase()] } ?: BEGINNER
    }
}

enum class ExerciseMechanic(val key: String, val labelKey: String) {
    COMPOUND("compound", "mechanic_compound"),
    ISOLATION("isolation", "mechanic_isolation"),
    ;

    companion object {
        private val byKey: Map<String, ExerciseMechanic> = entries.associateBy { it.key }

        fun fromKey(key: String?): ExerciseMechanic? = key?.let { byKey[it.trim().lowercase()] }
    }
}

enum class ExerciseForce(val key: String, val labelKey: String) {
    PUSH("push", "force_push"),
    PULL("pull", "force_pull"),
    STATIC("static", "force_static"),
    ;

    companion object {
        private val byKey: Map<String, ExerciseForce> = entries.associateBy { it.key }

        fun fromKey(key: String?): ExerciseForce? = key?.let { byKey[it.trim().lowercase()] }
    }
}

/**
 * One exercise of the library (`pr.md` §6.5). [id] is stable for a bundled exercise and a
 * generated id for a custom one; [muscles] is the map the recovery engine reads, so the
 * stored comma-separated columns and this typed list never disagree.
 */
data class Exercise(
    val id: String,
    val name: String,
    val primary: List<Muscle>,
    val secondary: List<Muscle>,
    val equipment: Equipment,
    val level: ExerciseLevel,
    val mechanic: ExerciseMechanic?,
    val force: ExerciseForce?,
    val bodyPart: BodyPart?,
    val instructions: List<String>,
    val isCustom: Boolean = false,
) {
    init {
        require(id.isNotBlank()) { "Exercise id must not be blank" }
        require(name.isNotBlank()) { "Exercise name must not be blank" }
    }

    /** Primary full and secondary half, as a per-muscle weight (`pr.md` §6.5). */
    fun muscleWeights(): Map<Muscle, Double> = buildMap {
        primary.forEach { put(it, 1.0) }
        secondary.forEach { put(it, Muscle.SECONDARY_WEIGHT) }
    }
}

/**
 * Alternatives for a substitution sheet (`pr.md` §6.5, V1 lists substitution explicitly;
 * the field itself is MVP). Ranked by shared primary muscle, then body part, equipment and
 * mechanic, so a barbell bench suggests a dumbbell bench before a cable fly.
 */
object ExerciseAlternatives {
    private const val PRIMARY_OVERLAP = 2.0
    private const val SAME_BODY_PART = 1.0
    private const val SAME_EQUIPMENT = 1.0
    private const val SAME_MECHANIC = 0.5
    private const val DEFAULT_LIMIT = 3

    fun forExercise(
        target: Exercise,
        catalogue: List<Exercise>,
        limit: Int = DEFAULT_LIMIT,
    ): List<Exercise> {
        if (limit <= 0) return emptyList()
        return catalogue
            .asSequence()
            .filter { it.id != target.id }
            .map { candidate -> candidate to score(target, candidate) }
            .filter { (_, score) -> score > 0.0 }
            .sortedWith(
                compareByDescending<Pair<Exercise, Double>> { it.second }
                    .thenBy { it.first.name },
            )
            .take(limit)
            .map { it.first }
            .toList()
    }

    private fun score(target: Exercise, candidate: Exercise): Double {
        var score = PRIMARY_OVERLAP * target.primary.count { it in candidate.primary }
        if (target.bodyPart != null && target.bodyPart == candidate.bodyPart) {
            score += SAME_BODY_PART
        }
        if (target.equipment == candidate.equipment) score += SAME_EQUIPMENT
        if (target.mechanic != null && target.mechanic == candidate.mechanic) {
            score += SAME_MECHANIC
        }
        return score
    }
}
