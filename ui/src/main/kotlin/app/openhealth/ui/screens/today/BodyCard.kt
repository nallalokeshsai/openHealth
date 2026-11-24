package app.openhealth.ui.screens.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.openhealth.core.body.Muscle
import app.openhealth.core.formula.RecoveryStep
import app.openhealth.ui.R
import app.openhealth.ui.body.BodyMapModel
import app.openhealth.ui.body.MuscleShade
import app.openhealth.ui.body.OhBodyMap
import app.openhealth.ui.body.OhBodyMapLegend
import app.openhealth.ui.body.bodyMapTableRows
import app.openhealth.ui.body.fatProvenanceLine
import app.openhealth.ui.body.muscleLabel
import app.openhealth.ui.components.OhButton
import app.openhealth.ui.components.OhButtonVariant
import app.openhealth.ui.components.OhCard
import app.openhealth.ui.components.OhCardHeader
import app.openhealth.ui.components.OhChip
import app.openhealth.ui.components.OhChipStrip
import app.openhealth.ui.components.OhInsetGroup
import app.openhealth.ui.components.OhModalSheet
import app.openhealth.ui.components.OhRow
import app.openhealth.ui.icons.OhModuleIcons
import app.openhealth.ui.screens.common.OhEmptyState
import app.openhealth.ui.screens.common.number
import app.openhealth.ui.theme.OhTheme

/**
 * Today's hero body card (`DESIGN.md` §14.2 item 3): the recovery map, the chip row and
 * one 13 sp line naming the most recently trained muscle. A tap opens that muscle's sheet;
 * the map's table alternative is one row away (`DESIGN.md` §8.6, §9.1).
 */
@Composable
fun BodyCard(model: BodyMapModel?, onOpenTrain: () -> Unit, modifier: Modifier = Modifier) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var showFat by rememberSaveable { mutableStateOf(true) }
    var showTable by rememberSaveable { mutableStateOf(false) }
    OhCard(modifier = modifier) {
        OhCardHeader(
            eyebrow = stringResource(R.string.today_body_eyebrow),
            title = stringResource(R.string.today_body_title),
            glyph = OhModuleIcons.workout,
        )
        val map = model
        if (map == null) {
            Spacer(Modifier.height(OhTheme.space.s2))
            OhEmptyState(
                title = stringResource(R.string.today_body_empty_title),
                body = stringResource(R.string.today_body_empty_body),
                actionLabel = stringResource(R.string.today_body_empty_action),
                onAction = onOpenTrain,
            )
            return@OhCard
        }
        val trained = map.shades.values
            .filterIsInstance<MuscleShade.Recovery>()
            .any { it.step != RecoveryStep.UNTRAINED }
        Spacer(Modifier.height(OhTheme.space.s5))
        OhBodyMap(
            model = map.copy(
                selected = selected?.let { Muscle.fromKey(it) },
                adipose = map.adipose.takeIf { showFat },
            ),
            onMuscle = { muscle -> selected = muscle.key },
            onShowTable = { showTable = true },
        )
        Spacer(Modifier.height(OhTheme.space.s5))
        OhChipStrip {
            OhChip(
                text = stringResource(R.string.body_map_recovery),
                selected = true,
                onClick = {},
            )
            if (map.adipose != null) {
                OhChip(
                    text = stringResource(R.string.body_map_fat_layer),
                    selected = showFat,
                    neutralSelected = true,
                    onClick = { showFat = !showFat },
                )
            }
        }
        Spacer(Modifier.height(OhTheme.space.s2))
        Text(
            text = if (trained) {
                lastTrainedLine(map)
            } else {
                stringResource(R.string.body_map_nothing_trained)
            },
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
        if (map.adipose != null && showFat) {
            Spacer(Modifier.height(OhTheme.space.s0))
            map.fatProvenance?.let { provenance ->
                Text(
                    text = fatProvenanceLine(provenance),
                    style = OhTheme.type.footnote,
                    color = OhTheme.colors.label2,
                )
            }
            Text(
                text = stringResource(R.string.body_map_fat_estimate),
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
            )
        }
        Spacer(Modifier.height(OhTheme.space.s3))
        OhBodyMapLegend(model = map)
        if (!trained) {
            Spacer(Modifier.height(OhTheme.space.s5))
            OhButton(
                text = stringResource(R.string.today_body_empty_action),
                onClick = onOpenTrain,
                variant = OhButtonVariant.Secondary,
            )
        }
        Spacer(Modifier.height(OhTheme.space.s3))
        OhRow(
            title = stringResource(R.string.body_map_show_table),
            showChevron = true,
            onClick = { showTable = true },
        )
    }
    val open = selected?.let { Muscle.fromKey(it) }
    val map = model
    if (open != null && map != null) {
        MuscleSheet(model = map, muscle = open, onClose = { selected = null })
    }
    if (map != null) {
        OhModalSheet(
            visible = showTable,
            onDismiss = { showTable = false },
            title = stringResource(R.string.body_map_table_title),
        ) {
            BodyMapTable(model = map)
        }
    }
}

@Composable
private fun lastTrainedLine(model: BodyMapModel): String {
    val recent = Muscle.anatomyOrder
        .mapNotNull { muscle ->
            (model.shade(muscle) as? MuscleShade.Recovery)
                ?.takeIf { it.hoursSinceLoad != null }
                ?.let { muscle to it }
        }
        .minByOrNull { it.second.hoursSinceLoad ?: Double.MAX_VALUE } ?: return ""
    return stringResource(
        R.string.body_map_last_trained,
        muscleLabel(recent.first),
        number(recent.second.hoursSinceLoad ?: 0.0),
        stepWord(recent.second.step),
    )
}

@Composable
private fun MuscleSheet(model: BodyMapModel, muscle: Muscle, onClose: () -> Unit) {
    val shade = model.shade(muscle) as? MuscleShade.Recovery
    OhModalSheet(
        visible = true,
        onDismiss = onClose,
        title = muscleLabel(muscle),
    ) {
        if (shade == null) {
            Text(
                text = stringResource(R.string.recovery_untrained),
                style = OhTheme.type.body,
                color = OhTheme.colors.label,
            )
            return@OhModalSheet
        }
        Column(verticalArrangement = Arrangement.spacedBy(OhTheme.space.s4)) {
            Text(
                text = stepWord(shade.step),
                style = OhTheme.type.body,
                color = OhTheme.colors.label,
            )
            OhInsetGroup {
                OhRow(
                    title = stringResource(R.string.body_map_muscle_sheet_hours),
                    value = shade.hoursSinceLoad?.let { number(it) },
                )
                OhRow(
                    title = stringResource(R.string.body_map_muscle_sheet_sets),
                    value = number(shade.sets),
                    showDivider = true,
                )
            }
            if (shade.hardSession) {
                Text(
                    text = stringResource(R.string.body_map_muscle_sheet_hard),
                    style = OhTheme.type.footnote,
                    color = OhTheme.colors.label2,
                )
            }
        }
    }
}

@Composable
private fun BodyMapTable(model: BodyMapModel) {
    val rows = bodyMapTableRows(model)
    OhInsetGroup {
        rows.forEachIndexed { index, row ->
            OhRow(
                title = row.muscleLabel,
                subtitle = row.stateLabel,
                value = row.valueLabel,
                showDivider = index < rows.lastIndex,
            )
        }
    }
}

@Composable
private fun stepWord(step: RecoveryStep): String = stringResource(
    when (step) {
        RecoveryStep.UNTRAINED -> R.string.recovery_untrained
        RecoveryStep.RECOVERING -> R.string.recovery_recovering
        RecoveryStep.EASING -> R.string.recovery_easing
        RecoveryStep.READY -> R.string.recovery_ready
    },
)
