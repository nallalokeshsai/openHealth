// Text helpers for the map and the measurement silhouette keep their small label
// functions together; that is the file's whole job.
@file:Suppress("TooManyFunctions")

package app.openhealth.ui.body

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.openhealth.core.body.Muscle
import app.openhealth.core.formula.RecoveryStep
import app.openhealth.ui.R
import app.openhealth.ui.components.OhInsetGroup
import app.openhealth.ui.components.OhRow
import app.openhealth.ui.screens.common.number
import app.openhealth.ui.theme.OhRamp
import app.openhealth.ui.theme.OhSpace
import app.openhealth.ui.theme.OhTheme

/**
 * The sentence and the table every map owes its reader (`DESIGN.md` §8.6, §11.3, §14.2):
 * a chart's content description is one sentence, not a dump, and the table carries the
 * same numbers the colours do.
 */
@Composable
fun bodyMapDescription(model: BodyMapModel): String = when (model.type) {
    BodyMapType.RECOVERY -> {
        val steps = Muscle.anatomyOrder.mapNotNull { muscle ->
            (model.shade(muscle) as? MuscleShade.Recovery)?.let { muscle to it }
        }
        val recovering = steps.count { it.second.step == RecoveryStep.RECOVERING }
        val easing = steps.count { it.second.step == RecoveryStep.EASING }
        val ready = steps.count {
            it.second.step == RecoveryStep.READY ||
                (it.second.step == RecoveryStep.UNTRAINED && it.second.hoursSinceLoad != null)
        }
        val recent = steps
            .filter { it.second.hoursSinceLoad != null }
            .minByOrNull { it.second.hoursSinceLoad ?: Double.MAX_VALUE }
        if (recent == null) {
            stringResource(R.string.body_map_desc_recovery_empty)
        } else {
            stringResource(
                R.string.body_map_desc_recovery,
                recovering,
                easing,
                ready,
                muscleLabel(recent.first),
            )
        }
    }

    BodyMapType.PLAN -> {
        val plans = Muscle.anatomyOrder.mapNotNull { muscle ->
            (model.shade(muscle) as? MuscleShade.Plan)?.let { muscle to it }
        }
        val top = plans.maxByOrNull { it.second.weeklySets }
        val weekly = model.planWindow == PlanWindow.WEEK
        if (top == null) {
            stringResource(
                if (weekly) {
                    R.string.body_map_desc_plan_empty
                } else {
                    R.string.body_map_desc_session_empty
                },
            )
        } else {
            stringResource(
                if (weekly) R.string.body_map_desc_plan else R.string.body_map_desc_session,
                pluralStringResource(
                    R.plurals.body_map_plan_muscles,
                    plans.size,
                    plans.size,
                ),
                muscleLabel(top.first),
                pluralStringResource(
                    if (weekly) {
                        R.plurals.body_map_plan_sets
                    } else {
                        R.plurals.body_map_session_sets
                    },
                    top.second.weeklySets.toInt(),
                    number(top.second.weeklySets),
                ),
            )
        }
    }
}

/** The muscle's localised name, shared with the map's accessibility nodes. */
@Composable
fun muscleLabel(muscle: Muscle): String = stringResource(
    when (muscle) {
        Muscle.CHEST -> R.string.muscle_chest
        Muscle.UPPER_BACK -> R.string.muscle_upper_back
        Muscle.LOWER_BACK -> R.string.muscle_lower_back
        Muscle.TRAPEZIUS -> R.string.muscle_trapezius
        Muscle.DELTOIDS -> R.string.muscle_deltoids
        Muscle.BICEPS -> R.string.muscle_biceps
        Muscle.TRICEPS -> R.string.muscle_triceps
        Muscle.FOREARMS -> R.string.muscle_forearms
        Muscle.ABS -> R.string.muscle_abs
        Muscle.OBLIQUES -> R.string.muscle_obliques
        Muscle.SERRATUS -> R.string.muscle_serratus
        Muscle.HIP_FLEXORS -> R.string.muscle_hip_flexors
        Muscle.GLUTES -> R.string.muscle_glutes
        Muscle.ADDUCTORS -> R.string.muscle_adductors
        Muscle.QUADS -> R.string.muscle_quads
        Muscle.HAMSTRINGS -> R.string.muscle_hamstrings
        Muscle.CALVES -> R.string.muscle_calves
        Muscle.TIBIALIS -> R.string.muscle_tibialis
        Muscle.NECK -> R.string.muscle_neck
    },
)

/** The fat layer's provenance in one line, with its method, source and tape sites. */
@Composable
fun fatProvenanceLine(provenance: FatProvenance): String {
    val sites = provenance.tapeSites.mapNotNull { type -> tapeSiteLabel(type) }
    val siteList = sites.joinToString(", ")
    val source = provenance.sourceLabel ?: stringResource(R.string.body_map_fat_unknown_source)
    val percent = provenance.bodyFatPercent
    return when {
        percent != null && sites.isNotEmpty() -> stringResource(
            R.string.body_map_fat_measured_tape,
            number(percent, decimals = 1),
            methodLabel(provenance.method),
            source,
            siteList,
        )

        percent != null -> stringResource(
            R.string.body_map_fat_measured,
            number(percent, decimals = 1),
            methodLabel(provenance.method),
            source,
        )

        sites.isNotEmpty() -> stringResource(R.string.body_map_fat_estimated_tape, siteList)
        else -> stringResource(R.string.body_map_fat_measured_from_tape)
    }
}

@Composable
fun methodLabel(method: app.openhealth.core.observation.MeasurementMethod?): String =
    stringResource(
        when (method) {
            app.openhealth.core.observation.MeasurementMethod.BIA -> R.string.method_bia
            app.openhealth.core.observation.MeasurementMethod.DEXA -> R.string.method_dexa
            app.openhealth.core.observation.MeasurementMethod.SCALE -> R.string.method_scale
            app.openhealth.core.observation.MeasurementMethod.MANUAL_ENTRY -> R.string.method_manual
            else -> R.string.method_other
        },
    )

@Composable
fun tapeSiteLabel(type: String): String? = when (type) {
    "neck" -> stringResource(R.string.site_neck)
    "chest" -> stringResource(R.string.site_chest)
    "waist" -> stringResource(R.string.site_waist)
    "hips" -> stringResource(R.string.site_hips)
    "upper_arm" -> stringResource(R.string.site_upper_arm)
    "forearm" -> stringResource(R.string.site_forearm)
    "thigh" -> stringResource(R.string.site_thigh)
    "calf" -> stringResource(R.string.site_calf)
    else -> null
}

/** The rows of the map's table alternative, one per muscle, in anatomy order. */
@Composable
fun bodyMapTableRows(model: BodyMapModel): List<BodyMapTableRow> =
    Muscle.anatomyOrder.map { muscle ->
        val shade = model.shade(muscle)
        when (shade) {
            is MuscleShade.Recovery -> BodyMapTableRow(
                muscleLabel = muscleLabel(muscle),
                valueLabel = if (shade.hoursSinceLoad == null) {
                    stringResource(R.string.body_map_table_no_load)
                } else {
                    stringResource(
                        R.string.body_map_table_sets_hours,
                        number(shade.sets),
                        number(shade.hoursSinceLoad),
                    )
                },
                stateLabel = recoveryStateLabel(shade.step, shade.hoursSinceLoad),
            )

            is MuscleShade.Plan -> BodyMapTableRow(
                muscleLabel = muscleLabel(muscle),
                valueLabel = stringResource(
                    if (model.planWindow == PlanWindow.WEEK) {
                        R.string.body_map_table_weekly_sets
                    } else {
                        R.string.body_map_table_session_sets
                    },
                    number(shade.weeklySets),
                ),
                stateLabel = stringResource(R.string.body_map_legend_plan),
            )

            MuscleShade.None -> BodyMapTableRow(
                muscleLabel = muscleLabel(muscle),
                valueLabel = if (model.type == BodyMapType.PLAN) {
                    stringResource(
                        if (model.planWindow == PlanWindow.WEEK) {
                            R.string.body_map_table_weekly_sets
                        } else {
                            R.string.body_map_table_session_sets
                        },
                        number(0.0),
                    )
                } else {
                    stringResource(R.string.body_map_table_no_load)
                },
                stateLabel = if (model.type == BodyMapType.PLAN) {
                    stringResource(R.string.body_map_plan_none)
                } else {
                    stringResource(R.string.recovery_untrained)
                },
            )
        }
    }

/**
 * The map's table alternative (`DESIGN.md` §8.6, §14.2: muscle, sets, hours since load,
 * step). It is the same data the colours carry, so a reader who cannot use the figure
 * loses nothing.
 *
 * The hairline sits **between** rows (`DESIGN.md` §5.3): every row but the first draws
 * one, so the group never opens or closes on a rule.
 */
@Composable
fun OhBodyMapTable(model: BodyMapModel, modifier: Modifier = Modifier) {
    val rows = bodyMapTableRows(model)
    OhInsetGroup(modifier = modifier) {
        rows.forEachIndexed { index, row ->
            OhRow(
                title = row.muscleLabel,
                subtitle = row.stateLabel,
                value = row.valueLabel,
                showDivider = index > 0,
            )
        }
    }
}

/** The word a recovery step carries; a trained muscle past Ready says "Recovered". */
@Composable
fun recoveryStateLabel(step: RecoveryStep, hoursSinceLoad: Double?): String = when {
    step == RecoveryStep.UNTRAINED && hoursSinceLoad != null ->
        stringResource(R.string.recovery_recovered)

    step == RecoveryStep.UNTRAINED -> stringResource(R.string.recovery_untrained)
    step == RecoveryStep.RECOVERING -> stringResource(R.string.recovery_recovering)
    step == RecoveryStep.EASING -> stringResource(R.string.recovery_easing)
    else -> stringResource(R.string.recovery_ready)
}

/** The legend every map carries, with the words that carry the meaning (`DESIGN.md` §11.3). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun OhBodyMapLegend(model: BodyMapModel, modifier: Modifier = Modifier) {
    val colors = OhTheme.colors
    when (model.type) {
        BodyMapType.RECOVERY -> FlowRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OhSpace.s6),
            verticalArrangement = Arrangement.spacedBy(OhSpace.s0),
        ) {
            LegendDot(stringResource(R.string.recovery_recovering), colors.recActive)
            LegendDot(stringResource(R.string.recovery_easing), colors.recEasing)
            LegendDot(stringResource(R.string.recovery_ready), colors.recReady)
            if (model.adipose != null) {
                LegendDot(
                    stringResource(R.string.body_map_fat_layer),
                    colors.fat.copy(alpha = colors.fatLayerAlpha),
                )
            }
        }

        BodyMapType.PLAN -> Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OhSpace.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.body_map_legend_less),
                style = OhTheme.type.caption,
                color = colors.label2,
            )
            listOf(OhRamp.l1(colors), OhRamp.l2(colors), OhRamp.l3(colors), OhRamp.l4(colors))
                .forEach { step ->
                    Box(
                        modifier = Modifier
                            .size(OhTheme.dims.glyphInline)
                            .clip(RoundedCornerShape(OhTheme.radius.sm))
                            .background(step.track),
                    ) {
                        step.overlay?.let { overlay ->
                            Box(Modifier.matchParentSize().background(overlay))
                        }
                    }
                }
            Text(
                text = stringResource(R.string.body_map_legend_more),
                style = OhTheme.type.caption,
                color = colors.label2,
            )
        }
    }
}

@Composable
private fun LegendDot(label: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(OhTheme.space.s3)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(OhTheme.space.s2))
        Text(text = label, style = OhTheme.type.caption, color = OhTheme.colors.label2)
    }
}

/** The site's name on the silhouette and in the list (`DESIGN.md` §9.3). */
@Composable
fun measurementSiteLabel(site: app.openhealth.core.body.MeasurementSite): String = stringResource(
    when (site) {
        app.openhealth.core.body.MeasurementSite.NECK -> R.string.measure_site_neck
        app.openhealth.core.body.MeasurementSite.SHOULDERS -> R.string.measure_site_shoulders
        app.openhealth.core.body.MeasurementSite.CHEST -> R.string.measure_site_chest
        app.openhealth.core.body.MeasurementSite.WAIST -> R.string.measure_site_waist
        app.openhealth.core.body.MeasurementSite.HIPS -> R.string.measure_site_hips
        app.openhealth.core.body.MeasurementSite.UPPER_ARM -> R.string.measure_site_upper_arm
        app.openhealth.core.body.MeasurementSite.FOREARM -> R.string.measure_site_forearm
        app.openhealth.core.body.MeasurementSite.THIGH -> R.string.measure_site_thigh
        app.openhealth.core.body.MeasurementSite.CALF -> R.string.measure_site_calf
        app.openhealth.core.body.MeasurementSite.WRIST -> R.string.measure_site_wrist
        app.openhealth.core.body.MeasurementSite.ANKLE -> R.string.measure_site_ankle
    },
)
