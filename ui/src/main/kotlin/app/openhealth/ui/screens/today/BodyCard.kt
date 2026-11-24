package app.openhealth.ui.screens.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import app.openhealth.core.body.Muscle
import app.openhealth.ui.R
import app.openhealth.ui.body.BodyMapModel
import app.openhealth.ui.body.FatProvenance
import app.openhealth.ui.body.MuscleShade
import app.openhealth.ui.body.OhBodyMap
import app.openhealth.ui.body.OhBodyMapLegend
import app.openhealth.ui.body.bodyMapTableRows
import app.openhealth.ui.body.methodLabel
import app.openhealth.ui.body.muscleLabel
import app.openhealth.ui.body.recoveryStateLabel
import app.openhealth.ui.body.tapeSiteLabel
import app.openhealth.ui.components.OhButton
import app.openhealth.ui.components.OhButtonVariant
import app.openhealth.ui.components.OhCard
import app.openhealth.ui.components.OhCardHeader
import app.openhealth.ui.components.OhChip
import app.openhealth.ui.components.OhChipStrip
import app.openhealth.ui.components.OhInsetGroup
import app.openhealth.ui.components.OhModalSheet
import app.openhealth.ui.components.OhRow
import app.openhealth.ui.components.ohClickable
import app.openhealth.ui.components.ohPressScale
import app.openhealth.ui.components.rememberInteraction
import app.openhealth.ui.icons.OhIcon
import app.openhealth.ui.icons.OhIcons
import app.openhealth.ui.icons.OhModuleIcons
import app.openhealth.ui.screens.common.OhEmptyState
import app.openhealth.ui.screens.common.number
import app.openhealth.ui.screens.common.shortDate
import app.openhealth.ui.screens.common.timeOf
import app.openhealth.ui.theme.OhTheme
import app.openhealth.ui.theme.OhWeight
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private const val ONE_HOUR = 1.0

/**
 * Today's hero body card (`DESIGN.md` §14.2 item 3): the recovery map, the chip row and
 * one 13 sp line naming the most recently trained muscle. A tap opens that muscle's sheet;
 * the map's table alternative and the fat layer's provenance are one row away
 * (`DESIGN.md` §7.1, §8.6, §9.1).
 */
@Composable
fun BodyCard(
    model: BodyMapModel?,
    onOpenTrain: () -> Unit,
    modifier: Modifier = Modifier,
    synchronousLayers: Boolean = false,
) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var showFat by rememberSaveable { mutableStateOf(true) }
    var showTable by rememberSaveable { mutableStateOf(false) }
    var showFatSource by rememberSaveable { mutableStateOf(false) }
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
        // "Loaded" is a set inside the four-week window, not a recovery step: a muscle
        // trained last week is past Ready and still a session that happened.
        val loaded = map.shades.values
            .filterIsInstance<MuscleShade.Recovery>()
            .any { it.hoursSinceLoad != null }
        Spacer(Modifier.height(OhTheme.space.s5))
        OhBodyMap(
            model = map.copy(
                selected = selected?.let { Muscle.fromKey(it) },
                adipose = map.adipose.takeIf { showFat },
            ),
            onMuscle = { muscle -> selected = muscle.key },
            onShowTable = { showTable = true },
            synchronousLayers = synchronousLayers,
        )
        Spacer(Modifier.height(OhTheme.space.s5))
        OhChipStrip {
            // A status, not a control: until the Train root adds the Plan switch there is
            // one map type, and a chip that does nothing is announced as a button.
            Text(
                text = stringResource(R.string.body_map_recovery),
                style = OhTheme.type.subhead.copy(fontWeight = OhWeight.semibold),
                color = OhTheme.colors.label2,
                modifier = Modifier.padding(
                    start = OhTheme.space.s1,
                    end = OhTheme.space.s3,
                    top = OhTheme.space.s3,
                    bottom = OhTheme.space.s3,
                ),
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
            text = if (loaded) {
                lastTrainedLine(map)
            } else {
                stringResource(R.string.body_map_nothing_trained)
            },
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
        if (!map.sexStated) {
            Spacer(Modifier.height(OhTheme.space.s0))
            Text(
                text = stringResource(R.string.body_map_sex_unknown),
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
            )
        }
        if (map.adipose != null && showFat) {
            Spacer(Modifier.height(OhTheme.space.s0))
            Text(
                text = stringResource(R.string.body_map_fat_estimate),
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
            )
            if (map.fatProvenance != null) {
                CardLinkRow(
                    title = stringResource(R.string.body_map_fat_source_title),
                    onClick = { showFatSource = true },
                )
            }
        }
        Spacer(Modifier.height(OhTheme.space.s3))
        OhBodyMapLegend(model = map)
        if (!loaded) {
            Spacer(Modifier.height(OhTheme.space.s5))
            OhButton(
                text = stringResource(R.string.today_body_empty_action),
                onClick = onOpenTrain,
                variant = OhButtonVariant.Secondary,
            )
        }
        Spacer(Modifier.height(OhTheme.space.s3))
        CardLinkRow(
            title = stringResource(R.string.body_map_show_table),
            onClick = { showTable = true },
        )
    }
    val open = selected?.let { Muscle.fromKey(it) }
    val map = model
    if (open != null && map != null) {
        MuscleSheet(
            model = map,
            muscle = open,
            onClose = { selected = null },
            onOpenTrain = onOpenTrain,
        )
    }
    if (map != null) {
        OhModalSheet(
            visible = showTable,
            onDismiss = { showTable = false },
            title = stringResource(R.string.body_map_table_title),
        ) {
            BodyMapTable(model = map)
        }
        OhModalSheet(
            visible = showFatSource && map.fatProvenance != null,
            onDismiss = { showFatSource = false },
            title = stringResource(R.string.body_map_fat_source_title),
        ) {
            map.fatProvenance?.let { FatProvenanceSheet(it) }
        }
    }
}

/** A row that belongs to the card's own column: no extra horizontal inset. */
@Composable
private fun CardLinkRow(title: String, onClick: () -> Unit) {
    val interaction = rememberInteraction()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .ohPressScale(interaction, OhTheme.motion.pressScaleCard)
            .ohClickable(interaction, onClick = onClick)
            .padding(vertical = OhTheme.space.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = OhTheme.type.subhead.copy(fontWeight = OhWeight.semibold),
            color = OhTheme.colors.label,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(OhTheme.space.s3))
        OhIcon(
            icon = OhIcons.chevronRight,
            contentDescription = null,
            size = OhTheme.dims.chevron,
            tint = OhTheme.colors.label3,
        )
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
    val hours = recent.second.hoursSinceLoad ?: 0.0
    val ago = if (hours < ONE_HOUR) {
        stringResource(R.string.body_map_duration_under_hour)
    } else {
        stringResource(R.string.body_map_hours_value, number(hours))
    }
    return stringResource(
        R.string.body_map_last_trained,
        muscleLabel(recent.first),
        ago,
        recoveryStateLabel(recent.second.step, recent.second.hoursSinceLoad),
    )
}

@Composable
private fun MuscleSheet(
    model: BodyMapModel,
    muscle: Muscle,
    onClose: () -> Unit,
    onOpenTrain: () -> Unit,
) {
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
                text = recoveryStateLabel(shade.step, shade.hoursSinceLoad),
                style = OhTheme.type.body,
                color = OhTheme.colors.label,
            )
            if (shade.hoursSinceLoad == null) {
                Text(
                    text = stringResource(R.string.body_map_no_load_body),
                    style = OhTheme.type.footnote,
                    color = OhTheme.colors.label2,
                )
                OhButton(
                    text = stringResource(R.string.today_body_empty_action),
                    onClick = {
                        onClose()
                        onOpenTrain()
                    },
                    variant = OhButtonVariant.Secondary,
                )
                return@Column
            }
            OhInsetGroup {
                OhRow(
                    title = stringResource(R.string.body_map_muscle_sheet_hours),
                    value = stringResource(
                        R.string.body_map_hours_value,
                        number(shade.hoursSinceLoad),
                    ),
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
            shade.lastRecordedAt?.let { recorded ->
                Text(
                    text = stringResource(
                        R.string.body_map_muscle_sheet_recorded,
                        recordedLabel(recorded),
                        shade.lastSourceLabel
                            ?: stringResource(R.string.body_map_fat_unknown_source),
                    ),
                    style = OhTheme.type.footnote,
                    color = OhTheme.colors.label2,
                )
            }
        }
    }
}

@Composable
private fun FatProvenanceSheet(provenance: FatProvenance) {
    val percent = provenance.bodyFatPercent
    val sites = provenance.tapeSites.mapNotNull { type -> tapeSiteLabel(type) }
    OhInsetGroup {
        OhRow(
            title = stringResource(R.string.body_map_fat_value_label),
            value = if (percent == null) {
                stringResource(R.string.body_map_fat_estimated)
            } else {
                stringResource(R.string.body_map_fat_percent_value, number(percent, decimals = 1))
            },
        )
        if (percent != null) {
            OhRow(
                title = stringResource(R.string.body_map_fat_method_label),
                value = methodLabel(provenance.method),
                showDivider = true,
            )
            OhRow(
                title = stringResource(R.string.body_map_fat_source_label),
                value = provenance.sourceLabel
                    ?: stringResource(R.string.body_map_fat_unknown_source),
                showDivider = true,
            )
        }
        provenance.recordedAt?.let { recorded ->
            OhRow(
                title = stringResource(R.string.body_map_fat_recorded_label),
                value = recordedLabel(recorded),
                showDivider = true,
            )
        }
        if (sites.isNotEmpty()) {
            OhRow(
                title = stringResource(R.string.body_map_fat_tape_label),
                value = sites.joinToString(", "),
                showDivider = true,
            )
        }
    }
    Spacer(Modifier.height(OhTheme.space.s4))
    Text(
        text = stringResource(R.string.body_map_fat_estimate),
        style = OhTheme.type.footnote,
        color = OhTheme.colors.label2,
    )
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
private fun recordedLabel(at: kotlin.time.Instant): String {
    val zone = TimeZone.currentSystemDefault()
    val date = at.toLocalDateTime(zone).date
    return stringResource(R.string.body_map_recorded_at, shortDate(date), timeOf(at, zone))
}
