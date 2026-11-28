package app.openhealth.ui.screens.activity

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.openhealth.ui.R
import app.openhealth.ui.components.ChartTableModel
import app.openhealth.ui.components.ChartTableSheet
import app.openhealth.ui.components.OhBarRow
import app.openhealth.ui.components.OhButton
import app.openhealth.ui.components.OhButtonVariant
import app.openhealth.ui.components.OhCard
import app.openhealth.ui.components.OhCardHeader
import app.openhealth.ui.components.OhDialog
import app.openhealth.ui.components.OhInsetGroup
import app.openhealth.ui.components.OhKpiBand
import app.openhealth.ui.components.OhKpiFigure
import app.openhealth.ui.components.OhModalSheet
import app.openhealth.ui.components.OhRow
import app.openhealth.ui.components.OhSwitch
import app.openhealth.ui.icons.OhModuleIcons
import app.openhealth.ui.screens.common.OhPageHeader
import app.openhealth.ui.screens.common.OhScreen
import app.openhealth.ui.screens.common.OhSectionTitle
import app.openhealth.ui.theme.OhTheme

/**
 * One activity (`pr.md` §6.11): the route, its KPI band, splits, heart-rate zones, the
 * values' provenance, GPX export and delete. The map is a slot so the platform view can
 * live in the app module and `:ui` stays free of a map dependency.
 */
@Composable
fun ActivityDetailScreen(
    model: ActivityDetailModel,
    onBack: () -> Unit,
    onExport: (String) -> Unit,
    onExportTcx: (String) -> Unit = {},
    onDelete: (String) -> Unit,
    onOpenMapPacks: () -> Unit,
    modifier: Modifier = Modifier,
    mapContent: (@Composable () -> Unit)? = null,
) {
    var showTable by rememberSaveable { mutableStateOf(false) }
    var showProvenance by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var hideRoute by rememberSaveable { mutableStateOf(false) }
    OhScreen(modifier = modifier) {
        OhPageHeader(title = model.title, onBack = onBack)
        Text(
            text = listOfNotNull(model.typeLabel, model.sourceLabel).joinToString(" · "),
            style = OhTheme.type.subhead,
            color = OhTheme.colors.label2,
        )
        Text(
            text = "${model.dateText} · ${model.startedText}",
            style = OhTheme.type.caption,
            color = OhTheme.colors.label3,
        )
        Spacer(Modifier.height(OhTheme.space.s5))
        if (model.route.isNotEmpty() && mapContent != null) {
            if (!hideRoute) {
                OhCard {
                    Box(modifier = Modifier.fillMaxWidth()) { mapContent() }
                    Spacer(Modifier.height(OhTheme.space.s2))
                    Text(
                        text = stringResource(R.string.map_packs_attribution),
                        style = OhTheme.type.caption2,
                        color = OhTheme.colors.label3,
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.activity_detail_route_hidden),
                    style = OhTheme.type.footnote,
                    color = OhTheme.colors.label2,
                )
            }
            OhRow(
                title = stringResource(R.string.activity_detail_hide_route),
                trailing = {
                    OhSwitch(checked = hideRoute, onCheckedChange = { hideRoute = it })
                },
            )
            Spacer(Modifier.height(OhTheme.space.s2))
            Text(
                text = stringResource(R.string.map_packs_note),
                style = OhTheme.type.caption,
                color = OhTheme.colors.label3,
            )
            OhRow(
                title = stringResource(R.string.map_packs_title),
                showChevron = true,
                onClick = onOpenMapPacks,
            )
            Spacer(Modifier.height(OhTheme.space.s5))
        } else if (model.route.isEmpty()) {
            Text(
                text = stringResource(R.string.activity_detail_no_route),
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
            )
            Spacer(Modifier.height(OhTheme.space.s5))
        }
        OhKpiBand(
            figures = listOf(
                OhKpiFigure(
                    model.distanceText ?: stringResource(R.string.train_not_available),
                    stringResource(R.string.activity_detail_distance),
                ),
                OhKpiFigure(model.durationText, stringResource(R.string.activity_detail_duration)),
                OhKpiFigure(
                    model.paceText ?: stringResource(R.string.train_not_available),
                    stringResource(R.string.activity_detail_pace),
                ),
            ),
        )
        Spacer(Modifier.height(OhTheme.space.s3))
        OhInsetGroup {
            model.elevationText?.let {
                OhRow(
                    title = stringResource(R.string.activity_detail_elevation),
                    value = it,
                )
            }
            model.heartRateText?.let {
                OhRow(
                    title = stringResource(R.string.activity_detail_heart_rate),
                    value = it,
                    showDivider = true,
                )
            }
            model.energyText?.let {
                OhRow(
                    title = stringResource(R.string.activity_detail_energy),
                    value = it,
                    showDivider = true,
                )
            }
            OhRow(
                title = stringResource(R.string.activity_detail_provenance),
                value = model.sourceLabel,
                showDivider = true,
                showChevron = true,
                onClick = { showProvenance = true },
            )
        }
        if (model.isYoga && model.muscleLabels.isNotEmpty()) {
            OhSectionTitle(stringResource(R.string.activity_detail_yoga_muscles))
            Text(
                text = model.muscleLabels.joinToString(" · "),
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
            )
        }
        if (model.bestEfforts.isNotEmpty()) {
            OhSectionTitle(stringResource(R.string.activity_detail_best_efforts))
            OhInsetGroup {
                model.bestEfforts.forEachIndexed { index, (label, time) ->
                    OhRow(title = label, value = time, showDivider = index > 0)
                }
            }
        }
        if (model.splits.isNotEmpty()) {
            OhSectionTitle(stringResource(R.string.activity_detail_splits))
            OhInsetGroup {
                model.splits.forEachIndexed { index, split ->
                    OhRow(
                        title = stringResource(
                            R.string.activity_split_row,
                            split.index.toString(),
                            split.distanceText,
                        ),
                        subtitle = split.elevationText,
                        value = split.paceText,
                        showDivider = index > 0,
                    )
                }
            }
        }
        if (model.zones.isNotEmpty()) {
            OhSectionTitle(stringResource(R.string.activity_detail_zones))
            OhCard {
                OhCardHeader(
                    eyebrow = stringResource(R.string.stats_training_load),
                    title = stringResource(R.string.activity_detail_zones),
                    subtitle = model.zoneNote,
                    glyph = OhModuleIcons.heart,
                )
                Spacer(Modifier.height(OhTheme.space.s3))
                model.zones.forEach { zone ->
                    OhBarRow(
                        label = zone.label,
                        value = zone.timeText,
                        target = zone.rangeText,
                        progress = zone.share,
                        ink = OhTheme.colors.orangeInk,
                    )
                }
                Spacer(Modifier.height(OhTheme.space.s2))
                OhButton(
                    text = stringResource(R.string.activity_detail_table),
                    onClick = { showTable = true },
                    variant = OhButtonVariant.Ghost,
                )
            }
        }
        model.notes?.takeIf { it.isNotBlank() }?.let { notes ->
            OhSectionTitle(stringResource(R.string.activity_detail_notes))
            Text(text = notes, style = OhTheme.type.body, color = OhTheme.colors.label)
        }
        if (model.route.isNotEmpty()) {
            Spacer(Modifier.height(OhTheme.space.s6))
            OhButton(
                text = stringResource(R.string.activity_detail_export_gpx),
                onClick = { onExport(model.id) },
                variant = OhButtonVariant.Secondary,
            )
            Spacer(Modifier.height(OhTheme.space.s2))
            OhButton(
                text = stringResource(R.string.activity_detail_export_tcx),
                onClick = { onExportTcx(model.id) },
                variant = OhButtonVariant.Secondary,
            )
            Spacer(Modifier.height(OhTheme.space.s2))
        } else {
            Spacer(Modifier.height(OhTheme.space.s6))
        }
        OhButton(
            text = stringResource(R.string.activity_detail_delete),
            onClick = { deleting = true },
            variant = OhButtonVariant.Danger,
        )
    }
    if (showTable && model.table != null) {
        ChartTableSheet(model = model.table, onDismiss = { showTable = false })
    }
    if (showProvenance) {
        OhModalSheet(
            visible = showProvenance,
            onDismiss = { showProvenance = false },
            title = stringResource(R.string.activity_detail_provenance),
        ) {
            model.provenance.forEach { line ->
                OhInsetGroup {
                    OhRow(title = line.label, subtitle = line.detail, value = line.value)
                }
                Spacer(Modifier.height(OhTheme.space.s2))
            }
        }
    }
    OhDialog(
        visible = deleting,
        onDismiss = { deleting = false },
        title = stringResource(R.string.activity_detail_delete),
        message = stringResource(R.string.activity_detail_delete_body),
        primaryLabel = stringResource(R.string.activity_detail_delete),
        cancelLabel = stringResource(R.string.activity_detail_keep),
        destructive = true,
        onPrimary = {
            deleting = false
            onDelete(model.id)
        },
        onCancel = { deleting = false },
    )
}

/** The table behind the zone chart, built once by the presenter. */
fun zoneTable(model: ActivityDetailModel): ChartTableModel? = model.table
