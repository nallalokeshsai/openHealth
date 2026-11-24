package app.openhealth.core.body

/**
 * The whole muscles the body map draws (`DESIGN.md` §9.1: whole muscles, not sub-groups)
 * and the exercise library names (`pr.md` §6.5). A `key` is both the geometry key in
 * `ui/src/main/res/raw/body_paths.tsv` and the value stored in
 * `exercise.primary_muscles` / `exercise.secondary_muscles`, so a muscle has exactly one
 * name across storage, data and drawing.
 */
enum class Muscle(
    val key: String,
    val bodyPart: BodyPart,
    val region: MuscleRegion,
    val labelKey: String,
) {
    CHEST("chest", BodyPart.CHEST, MuscleRegion.CHEST, "muscle_chest"),
    UPPER_BACK("upper-back", BodyPart.BACK, MuscleRegion.MIDSECTION, "muscle_upper_back"),
    LOWER_BACK("lower-back", BodyPart.BACK, MuscleRegion.MIDSECTION, "muscle_lower_back"),
    TRAPEZIUS("trapezius", BodyPart.BACK, MuscleRegion.CHEST, "muscle_trapezius"),
    DELTOIDS("deltoids", BodyPart.SHOULDERS, MuscleRegion.CHEST, "muscle_deltoids"),
    BICEPS("biceps", BodyPart.ARMS, MuscleRegion.ARMS, "muscle_biceps"),
    TRICEPS("triceps", BodyPart.ARMS, MuscleRegion.ARMS, "muscle_triceps"),
    FOREARMS("forearm", BodyPart.ARMS, MuscleRegion.ARMS, "muscle_forearms"),
    ABS("abs", BodyPart.CORE, MuscleRegion.MIDSECTION, "muscle_abs"),
    OBLIQUES("obliques", BodyPart.CORE, MuscleRegion.MIDSECTION, "muscle_obliques"),
    SERRATUS("serratus", BodyPart.CORE, MuscleRegion.CHEST, "muscle_serratus"),
    HIP_FLEXORS("hip-flexors", BodyPart.CORE, MuscleRegion.MIDSECTION, "muscle_hip_flexors"),
    GLUTES("gluteal", BodyPart.GLUTES, MuscleRegion.HIPS, "muscle_glutes"),
    ADDUCTORS("adductors", BodyPart.LEGS, MuscleRegion.HIPS, "muscle_adductors"),
    QUADS("quadriceps", BodyPart.LEGS, MuscleRegion.THIGHS, "muscle_quads"),
    HAMSTRINGS("hamstring", BodyPart.LEGS, MuscleRegion.THIGHS, "muscle_hamstrings"),
    CALVES("calves", BodyPart.LEGS, MuscleRegion.CALVES, "muscle_calves"),
    TIBIALIS("tibialis", BodyPart.LEGS, MuscleRegion.CALVES, "muscle_tibialis"),
    NECK("neck", BodyPart.SHOULDERS, MuscleRegion.NECK, "muscle_neck"),
    ;

    companion object {
        private val byKey: Map<String, Muscle> = entries.associateBy { it.key }

        /** A stored or geometry key, or null when it names no whole muscle. */
        fun fromKey(key: String): Muscle? = byKey[key.trim().lowercase()]

        /** Weighted-set share of a secondary muscle (`pr.md` §6.5: primary full, secondary half). */
        const val SECONDARY_WEIGHT: Double = 0.5

        /** The muscles in anatomy order, for a table or a TalkBack walk. */
        val anatomyOrder: List<Muscle> = entries.toList()
    }
}

/** The body-part tags an exercise row and its filter chips carry (`pr.md` §6.5). */
enum class BodyPart(val key: String, val labelKey: String) {
    CHEST("chest", "body_part_chest"),
    BACK("back", "body_part_back"),
    SHOULDERS("shoulders", "body_part_shoulders"),
    ARMS("arms", "body_part_arms"),
    CORE("core", "body_part_core"),
    LEGS("legs", "body_part_legs"),
    GLUTES("glutes", "body_part_glutes"),
    ;

    companion object {
        private val byKey: Map<String, BodyPart> = entries.associateBy { it.key }

        fun fromKey(key: String): BodyPart? = byKey[key.trim().lowercase()]

        /**
         * The tag a list of primary muscles implies: the part with the most primary
         * muscles, and on a tie the first in [BodyPart.entries] order, so the answer is
         * stable. A custom exercise with no recognised muscle has no tag.
         */
        fun of(primary: List<Muscle>): BodyPart? = primary
            .groupingBy { it.bodyPart }
            .eachCount()
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<BodyPart, Int>> { it.value }
                    .thenBy { it.key.ordinal },
            )
            .firstOrNull()
            ?.key
    }
}

/**
 * The seven adipose sites of `DESIGN.md` §9.1.1. A region is a group of drawn shapes that
 * share one tape measurement, so the model can thicken one part of the figure without
 * changing the rest.
 */
enum class MuscleRegion(val measurementType: String) {
    NECK("neck"),
    CHEST("chest"),
    MIDSECTION("waist"),
    HIPS("hips"),
    THIGHS("thigh"),
    ARMS("upper_arm"),
    CALVES("calf"),
}

/** The four views of the body map (`DESIGN.md` §9.1), never mirrored in RTL. */
enum class BodySex { MALE, FEMALE }

enum class BodySide { FRONT, BACK }

data class BodyView(val sex: BodySex, val side: BodySide)

/**
 * The parts MuscleMap draws that are not muscles. They are the silhouette (head, hair,
 * hands, feet, knees, ankles) and never take a heat colour (`DESIGN.md` §9.1).
 */
val BODY_INERT_PARTS: Set<String> = setOf(
    "head",
    "hair",
    "hands",
    "feet",
    "knees",
    "ankles",
)
