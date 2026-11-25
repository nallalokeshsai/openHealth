package app.openhealth.core.body

import app.openhealth.core.units.UcumUnit

/**
 * Every circumference the body screens measure (`pr.md` §6.8, §6.1 tier 3).
 *
 * [type] is the stable value stored in `measurement.type`, and the same value onboarding
 * and the CSV import use, so a tape reading has one name everywhere. The first nine are
 * the measurement anchors of `DESIGN.md` §9.3; [WRIST] and [ANKLE] are frame sizes that
 * live in the list only, because they do not move the adipose layer.
 */
enum class MeasurementSite(
    val type: String,
    val unit: UcumUnit,
    val decimals: Int,
    /** The drawn shape an anchor sits on, or null when the site has no anchor. */
    val anchorMuscle: Muscle?,
    val anchorSide: BodySide = BodySide.FRONT,
) {
    NECK("neck", UcumUnit.CENTIMETRE, 1, Muscle.NECK),
    SHOULDERS("shoulders", UcumUnit.CENTIMETRE, 1, Muscle.DELTOIDS),
    CHEST("chest", UcumUnit.CENTIMETRE, 1, Muscle.CHEST),
    WAIST("waist", UcumUnit.CENTIMETRE, 1, Muscle.ABS),
    HIPS("hips", UcumUnit.CENTIMETRE, 1, Muscle.ADDUCTORS),
    UPPER_ARM("upper_arm", UcumUnit.CENTIMETRE, 1, Muscle.BICEPS),
    FOREARM("forearm", UcumUnit.CENTIMETRE, 1, Muscle.FOREARMS),
    THIGH("thigh", UcumUnit.CENTIMETRE, 1, Muscle.QUADS),
    CALF("calf", UcumUnit.CENTIMETRE, 1, Muscle.CALVES),
    WRIST("wrist", UcumUnit.CENTIMETRE, 1, null),
    ANKLE("ankle", UcumUnit.CENTIMETRE, 1, null),
    ;

    /** True for the nine sites the measurement silhouette draws an anchor for. */
    val hasAnchor: Boolean get() = anchorMuscle != null

    companion object {
        private val byType: Map<String, MeasurementSite> = entries.associateBy(
            MeasurementSite::type,
        )

        fun fromType(type: String): MeasurementSite? = byType[type.trim().lowercase()]

        /** The tape sites in anatomy order; wrist and ankle last, as the frame sizes. */
        val anchors: List<MeasurementSite> = entries.filter(MeasurementSite::hasAnchor)

        val frameSizes: List<MeasurementSite> = listOf(WRIST, ANKLE)
    }
}

/**
 * A body-composition value that is not a tape circumference (`pr.md` §6.8). It is stored
 * in `measurement.type` too, so the same provenance rules apply.
 */
enum class CompositionMeasure(val type: String, val unit: UcumUnit, val decimals: Int) {
    BODY_FAT("body_fat", UcumUnit.PERCENT, 1),
    BODY_WATER("body_water", UcumUnit.PERCENT, 1),
    ;

    companion object {
        fun fromType(type: String): CompositionMeasure? =
            entries.firstOrNull { it.type == type.trim().lowercase() }
    }
}
