package app.openhealth.ui.body

import app.openhealth.core.body.AdiposeLayer
import app.openhealth.core.body.BodySex
import app.openhealth.core.body.Muscle
import app.openhealth.core.body.MuscleRecoveryState
import app.openhealth.core.exercise.MusclePlan
import app.openhealth.core.formula.RecoveryStep

/** Which colouring a body map carries (`DESIGN.md` §9.1, `pr.md` §6.5). */
enum class BodyMapType { RECOVERY, PLAN }

/**
 * What the plan map counts: a routine's weekly target sets, or the sets actually done in
 * one session, flow or pose. The two never share wording: a summary that says "1 set a
 * week" about a single session is wrong.
 */
enum class PlanWindow { WEEK, SESSION }

/**
 * One muscle's value on the map. A recovery step carries the hours and sets its sheet
 * states in words; a plan share carries the weekly sets behind the accent ramp.
 */
sealed interface MuscleShade {
    data object None : MuscleShade

    data class Recovery(
        val step: RecoveryStep,
        val hoursSinceLoad: Double?,
        val sets: Double,
        val hardSession: Boolean,
        val lastRecordedAt: kotlin.time.Instant? = null,
        val lastSourceLabel: String? = null,
    ) : MuscleShade

    data class Plan(val share: Float, val weeklySets: Double) : MuscleShade
}

/**
 * Where the adipose layer's numbers came from (`DESIGN.md` §9.1.1, §9.2): a recorded body
 * fat figure with its method and source, or the cited tape estimate, plus the tape sites
 * that scaled the regions. The card states it; the layer is always labelled an estimate.
 */
data class FatProvenance(
    val bodyFatPercent: Double? = null,
    val method: app.openhealth.core.observation.MeasurementMethod? = null,
    val sourceLabel: String? = null,
    val observedAt: kotlin.time.Instant? = null,
    val recordedAt: kotlin.time.Instant? = null,
    val tapeSites: List<String> = emptyList(),
)

/**
 * Everything one map pair draws. Recovery and plan never share colours: the recovery map
 * is red, orange and green; the plan map is the one accent ramp (`DESIGN.md` §9.1,
 * `pr.md` §6.5).
 */
data class BodyMapModel(
    val sex: BodySex,
    val type: BodyMapType,
    val shades: Map<Muscle, MuscleShade> = emptyMap(),
    val adipose: AdiposeLayer? = null,
    val fatProvenance: FatProvenance? = null,
    val selected: Muscle? = null,
    /** False when the profile did not state a sex at birth; the figure is then generic. */
    val sexStated: Boolean = true,
    /** Only meaningful for a plan map: weekly targets or one session's work. */
    val planWindow: PlanWindow = PlanWindow.WEEK,
) {
    fun shade(muscle: Muscle): MuscleShade = shades[muscle] ?: MuscleShade.None

    companion object {
        fun recovery(
            sex: BodySex,
            states: Map<Muscle, MuscleRecoveryState>,
            adipose: AdiposeLayer? = null,
            fatProvenance: FatProvenance? = null,
            extras: BodyMapExtras = BodyMapExtras(),
        ): BodyMapModel = BodyMapModel(
            sex = sex,
            type = BodyMapType.RECOVERY,
            shades = states.mapValues { (_, state) ->
                MuscleShade.Recovery(
                    step = state.step,
                    hoursSinceLoad = state.hoursSinceLoad,
                    sets = state.sets,
                    hardSession = state.hardSession,
                    lastRecordedAt = state.lastRecordedAt,
                    lastSourceLabel = state.lastSourceId?.let(extras.sourceLabels::get),
                )
            },
            adipose = adipose,
            fatProvenance = fatProvenance,
            selected = extras.selected,
            sexStated = extras.sexStated,
        )

        fun plan(
            sex: BodySex,
            coverage: Map<Muscle, MusclePlan>,
            selected: Muscle? = null,
            window: PlanWindow = PlanWindow.WEEK,
        ): BodyMapModel = BodyMapModel(
            sex = sex,
            type = BodyMapType.PLAN,
            shades = coverage.mapValues { (_, plan) ->
                MuscleShade.Plan(share = plan.share.toFloat(), weeklySets = plan.weeklySets)
            },
            selected = selected,
            planWindow = window,
        )
    }
}

/** What a map carries besides its data: selection, the stated sex and source names. */
data class BodyMapExtras(
    val selected: Muscle? = null,
    val sexStated: Boolean = true,
    val sourceLabels: Map<String, String> = emptyMap(),
)

/** The two sizes of the map (`DESIGN.md` §9.1): the pair, and the mini map. */
enum class BodyMapVariant { FULL, MINI, EVENT_MINI }

/** A legend entry, with the word that carries the meaning when colour cannot. */
data class BodyMapLegendEntry(val label: String, val color: androidx.compose.ui.graphics.Color)

/** One row of the map's table alternative (`DESIGN.md` §8.6). */
data class BodyMapTableRow(val muscleLabel: String, val valueLabel: String, val stateLabel: String)
