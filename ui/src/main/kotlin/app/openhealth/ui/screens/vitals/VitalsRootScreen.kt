package app.openhealth.ui.screens.vitals

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.openhealth.core.body.AdiposeModel
import app.openhealth.core.body.MeasurementSite
import app.openhealth.core.observation.MeasurementMethod
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.vitals.RangePosition
import app.openhealth.ui.R
import app.openhealth.ui.body.OhBodyMap
import app.openhealth.ui.body.measurementSiteLabel
import app.openhealth.ui.components.OhButton
import app.openhealth.ui.components.OhButtonVariant
import app.openhealth.ui.components.OhCard
import app.openhealth.ui.components.OhCardHeader
import app.openhealth.ui.components.OhChip
import app.openhealth.ui.components.OhChipStrip
import app.openhealth.ui.components.OhInsetGroup
import app.openhealth.ui.components.OhModalSheet
import app.openhealth.ui.components.OhRow
import app.openhealth.ui.components.OhSlider
import app.openhealth.ui.components.OhSwitch
import app.openhealth.ui.components.ohClickable
import app.openhealth.ui.components.rememberInteraction
import app.openhealth.ui.icons.OhObjectIcons
import app.openhealth.ui.screens.common.OhEmptyState
import app.openhealth.ui.screens.common.OhScreen
import app.openhealth.ui.screens.common.OhSectionTitle
import app.openhealth.ui.screens.common.number
import app.openhealth.ui.screens.common.shortDate
import app.openhealth.ui.theme.OhTheme
import app.openhealth.ui.theme.OhWeight
import app.openhealth.ui.theme.ltrFigures
import app.openhealth.ui.theme.tabular

/**
 * The Vitals root (`DESIGN.md` §14.2 Vitals): the weight lead card, the dynamic body model
 * with measurement anchors, the composition grid, photos, measurements and the grouped
 * readings. Every number opens a provenance sheet, every hidden value is removed rather
 * than blurred, and the body model keeps the muscle figure while weight-neutral mode
 * removes only its adipose layer (`pr.md` §6.25, `DESIGN.md` §7.6, §9.1.1).
 */
@Composable
fun VitalsRootScreen(
    model: VitalsModel,
    onLogWeight: () -> Unit,
    onOpenDetail: (ObservationType) -> Unit,
    onSaveReading: (VitalDraft) -> Unit,
    onSaveMeasurement: (String, String, MeasurementMethod) -> Unit,
    onAddPhoto: () -> Unit,
    onOpenPhoto: (String) -> Unit,
    onDeletePhoto: (String) -> Unit,
    onPhotosHidden: (Boolean) -> Unit,
    onPhotosInExport: (Boolean) -> Unit,
    onExportCsv: () -> Unit,
    onImportCsv: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    synchronousLayers: Boolean = false,
    geometry: app.openhealth.ui.body.BodyGeometry? = null,
) {
    if (!model.bodyModuleOn && !model.vitalsModuleOn) {
        app.openhealth.ui.screens.roots.ModuleOffRoot(
            name = stringResource(R.string.root_vitals),
            onOpenSettings = onOpenSettings,
            modifier = modifier,
        )
        return
    }
    var table by rememberSaveable { mutableStateOf<String?>(null) }
    var photoSheet by rememberSaveable { mutableStateOf<String?>(null) }
    var provenance by remember { mutableStateOf<ValueProvenance?>(null) }
    var formula by remember { mutableStateOf<FormulaSheetModel?>(null) }
    var addReading by remember { mutableStateOf(false) }
    var addMeasurement by remember { mutableStateOf<MeasurementSite?>(null) }
    OhScreen(modifier = modifier) {
        Text(
            text = stringResource(R.string.root_vitals),
            style = OhTheme.type.large.copy(fontWeight = OhWeight.bold),
            color = OhTheme.colors.label,
            modifier = Modifier
                .padding(top = OhTheme.space.s6)
                .semantics { heading() },
        )
        Spacer(Modifier.height(OhTheme.space.s1))
        Text(
            text = stringResource(R.string.vitals_subtitle),
            style = OhTheme.type.subhead,
            color = OhTheme.colors.label2,
        )
        if (model.demo) {
            Spacer(Modifier.height(OhTheme.space.s2))
            Text(
                text = stringResource(R.string.vitals_demo),
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
            )
        }
        if (model.weightNeutral) {
            Spacer(Modifier.height(OhTheme.space.s3))
            Text(
                text = stringResource(R.string.vitals_weight_neutral_note),
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
            )
        }
        if (!model.bodyModuleOn) {
            Spacer(Modifier.height(OhTheme.space.s3))
            Text(
                text = stringResource(R.string.vitals_body_module_off),
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
            )
        }
        if (!model.vitalsModuleOn) {
            Spacer(Modifier.height(OhTheme.space.s3))
            Text(
                text = stringResource(R.string.vitals_vitals_module_off),
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
            )
        }
        if (model.bodyModuleOn) {
            if (!model.weightNeutral) {
                Spacer(Modifier.height(OhTheme.space.s5))
                WeightCard(
                    model = model.weight,
                    weightNeutral = false,
                    imperial = model.imperial,
                    onLogWeight = onLogWeight,
                    onProvenance = { provenance = it },
                    onFormula = { title, body, key ->
                        formula = FormulaSheetModel(title, body, key)
                    },
                    onShowTable = { table = TABLE_WEIGHT },
                )
            }
            Spacer(Modifier.height(OhTheme.space.s5))
            BodyModelCard(
                model = model.body,
                onAddMeasurement = { addMeasurement = it },
                onProvenance = { provenance = it },
                synchronousLayers = synchronousLayers,
                geometry = geometry,
            )
            if (model.composition.isNotEmpty()) {
                Spacer(Modifier.height(OhTheme.space.s5))
                CompositionCard(tiles = model.composition, onProvenance = { provenance = it })
            }
            Spacer(Modifier.height(OhTheme.space.s5))
            MeasurementsCard(
                measurements = model.measurements,
                frameSizes = model.frameSizes,
                onAddMeasurement = { addMeasurement = it },
                onProvenance = { provenance = it },
            )
            if (!model.weightNeutral) {
                Spacer(Modifier.height(OhTheme.space.s5))
                PhotosCard(
                    photos = model.photos,
                    hidden = model.photosHidden,
                    photosInExport = model.photosInExport,
                    onAddPhoto = onAddPhoto,
                    onOpenPhoto = { photoSheet = it },
                    onPhotosHidden = onPhotosHidden,
                    onPhotosInExport = onPhotosInExport,
                )
            }
        }
        if (model.vitalsModuleOn) {
            Spacer(Modifier.height(OhTheme.space.s5))
            ReadingsCard(
                groups = model.vitals,
                onAddReading = { addReading = true },
                onOpenDetail = onOpenDetail,
            )
        }
        if (model.bodyModuleOn && !model.weightNeutral) {
            Spacer(Modifier.height(OhTheme.space.s7))
            OhSectionTitle(stringResource(R.string.vitals_csv_title))
            OhInsetGroup {
                OhRow(
                    title = stringResource(R.string.vitals_csv_export),
                    subtitle = stringResource(R.string.vitals_csv_export_body),
                    leadingIcon = OhObjectIcons.file,
                    onClick = onExportCsv,
                )
                OhRow(
                    title = stringResource(R.string.vitals_csv_import),
                    subtitle = stringResource(R.string.vitals_csv_import_body),
                    leadingIcon = OhObjectIcons.file,
                    onClick = onImportCsv,
                )
            }
        }
        Spacer(Modifier.height(OhTheme.space.s7))
    }
    if (table == TABLE_WEIGHT && model.weight != null) {
        WeightTableSheet(
            model = model.weight,
            imperial = model.imperial,
            onDismiss = { table = null },
        )
    }
    if (addReading) {
        AddReadingSheet(
            visible = true,
            initialType = null,
            imperial = model.imperial,
            homeSlot = null,
            onDismiss = { addReading = false },
            onSave = { draft ->
                addReading = false
                onSaveReading(draft)
            },
        )
    }
    addMeasurement?.let { site ->
        AddMeasurementSheet(
            visible = true,
            initialSite = site,
            imperial = model.imperial,
            onDismiss = { addMeasurement = null },
            onSave = { chosen, value, method ->
                addMeasurement = null
                onSaveMeasurement(chosen, value, method)
            },
        )
    }
    provenance?.let { value ->
        VitalsProvenanceSheet(
            provenance = value,
            onDismiss = { provenance = null },
            onDelete = null,
        )
    }
    formula?.let { sheet ->
        FormulaSheet(sheet = sheet, onDismiss = { formula = null })
    }
    photoSheet?.let { id ->
        val photo = model.photos.firstOrNull { it.id == id }
        if (photo != null) {
            OhModalSheet(
                visible = true,
                onDismiss = { photoSheet = null },
                title = photo.caption ?: shortDate(photo.date),
                cancelLabel = stringResource(R.string.vitals_table_close),
                onCancel = { photoSheet = null },
            ) {
                Text(
                    text = stringResource(R.string.vitals_photo_private),
                    style = OhTheme.type.footnote,
                    color = OhTheme.colors.label2,
                )
                photo.provenance?.let { source ->
                    Spacer(Modifier.height(OhTheme.space.s2))
                    OhRow(
                        title = stringResource(R.string.vitals_provenance_title),
                        onClick = {
                            photoSheet = null
                            provenance = source
                        },
                        showChevron = true,
                    )
                }
                Spacer(Modifier.height(OhTheme.space.s3))
                if (model.photos.size >= 2) {
                    OhButton(
                        text = stringResource(R.string.vitals_photos_compare),
                        onClick = {
                            photoSheet = null
                            onOpenPhoto(photo.id)
                        },
                    )
                }
                Spacer(Modifier.height(OhTheme.space.s2))
                OhButton(
                    text = stringResource(R.string.vitals_provenance_delete),
                    onClick = {
                        photoSheet = null
                        onDeletePhoto(photo.id)
                    },
                    variant = OhButtonVariant.Danger,
                )
            }
        }
    }
}

private const val TABLE_WEIGHT = "weight"

@Composable
private fun WeightCard(
    model: WeightCardModel?,
    weightNeutral: Boolean,
    imperial: Boolean,
    onLogWeight: () -> Unit,
    onProvenance: (ValueProvenance) -> Unit,
    onFormula: (String, String, String) -> Unit,
    onShowTable: () -> Unit,
) {
    OhCard {
        OhCardHeader(
            eyebrow = stringResource(R.string.vitals_body_eyebrow),
            title = stringResource(R.string.vitals_weight_title),
            glyph = OhObjectIcons.scale,
        )
        if (model == null || weightNeutral) {
            Spacer(Modifier.height(OhTheme.space.s2))
            OhEmptyState(
                title = stringResource(R.string.vitals_weight_empty_title),
                body = if (weightNeutral) {
                    stringResource(R.string.vitals_weight_neutral_note)
                } else {
                    stringResource(R.string.vitals_weight_empty_body)
                },
                actionLabel = if (weightNeutral) {
                    null
                } else {
                    stringResource(R.string.vitals_weight_empty_action)
                },
                onAction = if (weightNeutral) null else onLogWeight,
            )
            return@OhCard
        }
        Spacer(Modifier.height(OhTheme.space.s4))
        val unit = stringResource(if (imperial) R.string.unit_pounds else R.string.unit_kg)
        val factor = if (imperial) KG_TO_LB else 1.0
        val valueInteraction = rememberInteraction()
        Row(
            modifier = Modifier
                .ohClickable(
                    interactionSource = valueInteraction,
                    onClick = { onProvenance(model.provenance) },
                ),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = number(model.latestKg * factor, 1),
                style = OhTheme.type.display.copy(fontWeight = OhWeight.semibold)
                    .tabular()
                    .ltrFigures(),
                color = OhTheme.colors.label,
            )
            Spacer(Modifier.width(OhTheme.space.s1))
            Text(
                text = unit,
                style = OhTheme.type.subhead,
                color = OhTheme.colors.label2,
            )
        }
        Spacer(Modifier.height(OhTheme.space.s1))
        Text(
            text = changeSentence(model, factor, unit),
            style = OhTheme.type.footnote,
            color = if (model.changeKg != null && model.changeKg < 0) {
                OhTheme.colors.greenInk
            } else {
                OhTheme.colors.label2
            },
        )
        if (model.points.size >= 2) {
            Spacer(Modifier.height(OhTheme.space.s4))
            OhTrendChart(
                model = TrendChartModel(
                    series = listOf(
                        TrendSeriesModel(
                            label = null,
                            points = model.points.map { it.copy(value = it.value * factor) },
                            role = SeriesRole.SINGLE,
                        ),
                    ),
                    unitText = unit,
                    goal = model.goalKg?.times(factor),
                    description = stringResource(
                        R.string.vitals_weight_chart_desc,
                        changeSentence(model, factor, unit),
                        number(
                            (model.points.firstOrNull()?.value ?: model.latestKg) * factor,
                            1,
                        ),
                        number(model.latestKg * factor, 1),
                        unit,
                    ),
                    decimals = 1,
                ),
                onShowTable = onShowTable,
            )
        } else {
            Spacer(Modifier.height(OhTheme.space.s3))
            OhRow(
                title = stringResource(R.string.vitals_weight_show_table),
                onClick = onShowTable,
                showChevron = true,
            )
        }
        Spacer(Modifier.height(OhTheme.space.s3))
        val weeklyTitle = stringResource(R.string.vitals_weight_weekly_label)
        val weeklyNone = stringResource(R.string.vitals_weight_weekly_none)
        val weeklyBody = stringResource(R.string.vitals_weight_weekly_formula)
        val goalTitle = stringResource(R.string.vitals_weight_goal)
        val goalBody = stringResource(R.string.vitals_weight_goal_formula)
        OhRow(
            title = weeklyTitle,
            subtitle = model.weeklyRateKg?.let {
                stringResource(R.string.vitals_weight_weekly, number(it * factor, 2), unit)
            } ?: weeklyNone,
            onClick = { onFormula(weeklyTitle, weeklyBody, "app") },
            showChevron = true,
        )
        OhRow(
            title = stringResource(R.string.vitals_weight_goal),
            subtitle = model.goalKg?.let { "${number(it * factor, 1)} $unit" }
                ?: stringResource(R.string.vitals_weight_goal_none),
            onClick = { onFormula(goalTitle, goalBody, "app") },
            showChevron = true,
        )
        Spacer(Modifier.height(OhTheme.space.s3))
        OhButton(
            text = stringResource(R.string.vitals_weight_empty_action),
            onClick = onLogWeight,
            variant = OhButtonVariant.Secondary,
        )
    }
}

@Composable
private fun changeSentence(model: WeightCardModel, factor: Double, unit: String): String {
    val change = model.changeKg ?: return stringResource(R.string.vitals_weight_none)
    val days = model.changeDays
    return when {
        change > 0 -> stringResource(
            R.string.vitals_weight_up,
            number(kotlin.math.abs(change) * factor, 1),
            unit,
            days,
        )

        change < 0 -> stringResource(
            R.string.vitals_weight_down,
            number(kotlin.math.abs(change) * factor, 1),
            unit,
            days,
        )

        else -> stringResource(R.string.vitals_weight_same, days)
    }
}

@Composable
private fun BodyModelCard(
    model: BodyFigureModel?,
    onAddMeasurement: (MeasurementSite) -> Unit,
    onProvenance: (ValueProvenance) -> Unit,
    synchronousLayers: Boolean,
    geometry: app.openhealth.ui.body.BodyGeometry? = null,
) {
    var showFat by rememberSaveable { mutableStateOf(true) }
    var scrub by rememberSaveable { mutableStateOf(1f) }
    OhCard {
        OhCardHeader(
            eyebrow = stringResource(R.string.vitals_body_eyebrow),
            title = stringResource(R.string.vitals_body_title),
            glyph = OhObjectIcons.watch,
        )
        if (model == null) {
            Spacer(Modifier.height(OhTheme.space.s2))
            OhEmptyState(
                title = stringResource(R.string.vitals_body_empty_title),
                body = stringResource(R.string.vitals_body_empty_body),
                actionLabel = stringResource(R.string.vitals_measurements_add),
                onAction = { onAddMeasurement(MeasurementSite.WAIST) },
            )
            return@OhCard
        }
        Spacer(Modifier.height(OhTheme.space.s4))
        val before = model.before
        val shown = if (
            before?.adipose != null && model.current.adipose != null && scrub < 1f
        ) {
            model.current.copy(
                adipose = AdiposeModel.blend(
                    before.adipose,
                    model.current.adipose,
                    scrub.toDouble(),
                ),
            )
        } else {
            model.current
        }
        OhBodyMap(
            model = shown.copy(
                adipose = shown.adipose?.takeIf { showFat },
                selected = null,
            ),
            anchors = model.anchors.associate { it.site to it.measured },
            onAnchor = onAddMeasurement,
            descriptionOverride = stringResource(R.string.vitals_body_model_desc),
            geometry = geometry,
            synchronousLayers = synchronousLayers,
        )
        Spacer(Modifier.height(OhTheme.space.s4))
        OhChipStrip {
            if (model.current.adipose != null) {
                OhChip(
                    text = stringResource(R.string.vitals_body_fat_layer),
                    selected = showFat,
                    neutralSelected = true,
                    onClick = { showFat = !showFat },
                )
            }
        }
        if (model.current.adipose != null && showFat) {
            Spacer(Modifier.height(OhTheme.space.s1))
            Text(
                text = stringResource(R.string.vitals_body_estimate),
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
            )
            model.fatMethod?.let { method ->
                Spacer(Modifier.height(OhTheme.space.s0))
                Text(
                    text = stringResource(R.string.vitals_body_fat_legend, method),
                    style = OhTheme.type.footnote,
                    color = OhTheme.colors.label2,
                )
            }
            model.fatSourceLabel?.let { source ->
                Spacer(Modifier.height(OhTheme.space.s1))
                OhRow(
                    title = stringResource(R.string.vitals_body_fat_source),
                    subtitle = listOfNotNull(model.fatMethod, source).joinToString(" · "),
                    onClick = { model.provenance?.let(onProvenance) },
                    showChevron = true,
                )
            }
        }
        if (before != null && before.adipose != null && model.current.adipose != null) {
            Spacer(Modifier.height(OhTheme.space.s3))
            Text(
                text = stringResource(
                    R.string.vitals_body_scrub_label,
                    model.beforeDate?.let { shortDate(it) }.orEmpty(),
                    model.afterDate?.let { shortDate(it) }.orEmpty(),
                ),
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
            )
            OhSlider(
                value = scrub,
                onValueChange = { scrub = it },
                contentDescription = stringResource(R.string.vitals_body_before_after),
            )
        }
    }
}

@Composable
private fun CompositionCard(
    tiles: List<CompositionTileModel>,
    onProvenance: (ValueProvenance) -> Unit,
) {
    val named = tiles.filter {
        it.key == CompositionKey.BODY_FAT ||
            it.key == CompositionKey.LEAN_MASS ||
            it.key == CompositionKey.WAIST ||
            it.key == CompositionKey.RESTING_HEART_RATE
    }
    val extra = tiles.filterNot { it in named }
    OhCard {
        OhCardHeader(
            eyebrow = stringResource(R.string.vitals_body_eyebrow),
            title = stringResource(R.string.vitals_composition_title),
            glyph = OhObjectIcons.scale,
        )
        if (named.isEmpty() && extra.isEmpty()) {
            OhEmptyState(
                title = stringResource(R.string.vitals_composition_empty_title),
                body = stringResource(R.string.vitals_composition_empty_body),
            )
            return@OhCard
        }
        Spacer(Modifier.height(OhTheme.space.s3))
        named.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(OhTheme.space.s3),
            ) {
                row.forEach { tile ->
                    CompositionTile(
                        tile = tile,
                        onClick = { tile.provenance?.let(onProvenance) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(OhTheme.space.s3))
        }
        extra.forEach { tile ->
            OhRow(
                title = compositionLabel(tile.key),
                subtitle = tile.sourceLine,
                value = listOfNotNull(tile.valueText, tile.unitText).joinToString(" "),
                onClick = { tile.provenance?.let(onProvenance) },
                showChevron = true,
            )
        }
    }
}

@Composable
private fun CompositionTile(
    tile: CompositionTileModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = rememberInteraction()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(OhTheme.radius.card))
            .background(OhTheme.colors.surface2)
            .ohClickable(interactionSource = interaction, onClick = onClick)
            .padding(OhTheme.space.s4),
    ) {
        Text(
            text = compositionLabel(tile.key),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
        Spacer(Modifier.height(OhTheme.space.s0))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = tile.valueText,
                style = OhTheme.type.title3.copy(fontWeight = OhWeight.semibold)
                    .tabular()
                    .ltrFigures(),
                color = OhTheme.colors.label,
                maxLines = 1,
            )
            tile.unitText?.let {
                Spacer(Modifier.width(OhTheme.space.s0))
                Text(
                    text = it,
                    style = OhTheme.type.caption,
                    color = OhTheme.colors.label2,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(OhTheme.space.s0))
        Text(
            text = if (tile.estimate) {
                stringResource(R.string.vitals_composition_estimate) + " · " + tile.sourceLine
            } else {
                tile.sourceLine
            },
            style = OhTheme.type.caption,
            color = OhTheme.colors.label2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun compositionLabel(key: CompositionKey): String = stringResource(
    when (key) {
        CompositionKey.BODY_FAT -> R.string.vitals_composition_body_fat
        CompositionKey.LEAN_MASS -> R.string.vitals_composition_lean
        CompositionKey.WAIST -> R.string.vitals_composition_waist
        CompositionKey.RESTING_HEART_RATE -> R.string.vitals_type_heart_rate
        CompositionKey.BODY_WATER -> R.string.vitals_composition_water
        CompositionKey.BMI -> R.string.vitals_composition_bmi
        CompositionKey.FFMI -> R.string.vitals_composition_ffmi
    },
)

@Composable
private fun MeasurementsCard(
    measurements: List<MeasurementRowModel>,
    frameSizes: List<MeasurementRowModel>,
    onAddMeasurement: (MeasurementSite) -> Unit,
    onProvenance: (ValueProvenance) -> Unit,
) {
    OhCard {
        OhCardHeader(
            eyebrow = stringResource(R.string.vitals_body_eyebrow),
            title = stringResource(R.string.vitals_measurements_title),
            glyph = OhObjectIcons.scale,
        )
        if (measurements.isEmpty() && frameSizes.isEmpty()) {
            OhEmptyState(
                title = stringResource(R.string.vitals_measurements_empty_title),
                body = stringResource(R.string.vitals_measurements_empty_body),
                actionLabel = stringResource(R.string.vitals_measurements_add),
                onAction = { onAddMeasurement(MeasurementSite.WAIST) },
            )
            return@OhCard
        }
        measurements.forEach { row ->
            MeasurementRow(row, onProvenance)
        }
        if (frameSizes.isNotEmpty()) {
            Spacer(Modifier.height(OhTheme.space.s2))
            Text(
                text = stringResource(R.string.vitals_frame_sizes),
                style = OhTheme.type.footnote.copy(fontWeight = OhWeight.semibold),
                color = OhTheme.colors.label2,
            )
            frameSizes.forEach { row -> MeasurementRow(row, onProvenance) }
        }
        Spacer(Modifier.height(OhTheme.space.s3))
        OhButton(
            text = stringResource(R.string.vitals_measurements_add),
            onClick = { onAddMeasurement(MeasurementSite.WAIST) },
            variant = OhButtonVariant.Secondary,
        )
    }
}

@Composable
private fun MeasurementRow(row: MeasurementRowModel, onProvenance: (ValueProvenance) -> Unit) {
    OhRow(
        title = row.customLabel ?: row.site?.let { measurementSiteLabel(it) }.orEmpty(),
        subtitle = "${shortDate(row.date)} · ${row.sourceLine}",
        value = stringResource(R.string.vitals_measurements_value, row.valueText, row.unitText),
        onClick = { onProvenance(row.provenance) },
        showChevron = true,
    )
}

@Composable
private fun PhotosCard(
    photos: List<PhotoTileModel>,
    hidden: Boolean,
    photosInExport: Boolean,
    onAddPhoto: () -> Unit,
    onOpenPhoto: (String) -> Unit,
    onPhotosHidden: (Boolean) -> Unit,
    onPhotosInExport: (Boolean) -> Unit,
) {
    OhCard {
        OhCardHeader(
            eyebrow = stringResource(R.string.vitals_body_eyebrow),
            title = stringResource(R.string.vitals_photos_title),
            glyph = OhObjectIcons.file,
        )
        Spacer(Modifier.height(OhTheme.space.s2))
        OhRow(
            title = stringResource(R.string.vitals_photos_hide),
            trailing = {
                OhSwitch(checked = hidden, onCheckedChange = onPhotosHidden)
            },
        )
        OhRow(
            title = stringResource(R.string.vitals_photos_export),
            subtitle = stringResource(R.string.vitals_photos_export_body),
            trailing = {
                OhSwitch(checked = photosInExport, onCheckedChange = onPhotosInExport)
            },
        )
        if (photos.isEmpty()) {
            OhEmptyState(
                title = stringResource(R.string.vitals_photos_empty_title),
                body = stringResource(R.string.vitals_photos_empty_body),
                actionLabel = stringResource(R.string.vitals_photos_add),
                onAction = onAddPhoto,
            )
            return@OhCard
        }
        Spacer(Modifier.height(OhTheme.space.s3))
        photos.chunked(PHOTO_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(OhTheme.dims.photoGridGap)) {
                row.forEach { photo ->
                    PhotoTile(
                        photo = photo,
                        hidden = hidden,
                        onClick = { onOpenPhoto(photo.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(PHOTO_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(OhTheme.dims.photoGridGap))
        }
        Spacer(Modifier.height(OhTheme.space.s1))
        Text(
            text = stringResource(R.string.vitals_photo_private),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
        Spacer(Modifier.height(OhTheme.space.s3))
        OhButton(
            text = stringResource(R.string.vitals_photos_add),
            onClick = onAddPhoto,
            variant = OhButtonVariant.Secondary,
        )
    }
}

@Composable
private fun PhotoTile(
    photo: PhotoTileModel,
    hidden: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var revealed by rememberSaveable(photo.id) { mutableStateOf(false) }
    val interaction = rememberInteraction()
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(OhTheme.radius.lg))
            .background(OhTheme.colors.surface2)
            .ohClickable(
                interactionSource = interaction,
                onClick = {
                    if (hidden && !revealed) revealed = true else onClick()
                },
            ),
    ) {
        val thumbnail = photo.thumbnail
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail,
                contentDescription = photo.caption ?: shortDate(photo.date),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (hidden &&
                            !revealed
                        ) {
                            Modifier.blur(OhTheme.dims.photoHiddenBlur)
                        } else {
                            Modifier
                        },
                    ),
            )
        }
        if (hidden && !revealed) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(OhTheme.colors.scrim),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.vitals_photos_hidden),
                    style = OhTheme.type.caption,
                    color = OhTheme.colors.knob,
                )
            }
        }
        Text(
            text = shortDate(photo.date),
            style = OhTheme.type.caption,
            color = OhTheme.colors.knob,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(OhTheme.space.s2)
                .clip(RoundedCornerShape(OhTheme.radius.full))
                .background(OhTheme.colors.scrim)
                .padding(horizontal = OhTheme.space.s2, vertical = OhTheme.space.s0),
        )
    }
}

@Composable
private fun ReadingsCard(
    groups: List<VitalGroupModel>,
    onAddReading: () -> Unit,
    onOpenDetail: (ObservationType) -> Unit,
) {
    OhCard {
        OhCardHeader(
            eyebrow = stringResource(R.string.vitals_vitals_eyebrow),
            title = stringResource(R.string.vitals_readings_title),
            glyph = OhObjectIcons.cuff,
        )
        if (groups.isEmpty()) {
            OhEmptyState(
                title = stringResource(R.string.vitals_readings_empty_title),
                body = stringResource(R.string.vitals_readings_empty_body),
                actionLabel = stringResource(R.string.vitals_readings_add),
                onAction = onAddReading,
            )
            return@OhCard
        }
        groups.forEach { group ->
            OhRow(
                title = vitalTitle(group.type),
                subtitle = listOfNotNull(
                    group.contextText,
                    group.sourceLabel,
                    group.date?.let { shortDate(it) },
                ).joinToString(" · "),
                value = "${group.latestText} ${group.unitText}",
                onClick = { onOpenDetail(group.type) },
                showChevron = true,
            )
            group.citationLine?.let { line ->
                Text(
                    text = if (group.position == RangePosition.BELOW ||
                        group.position == RangePosition.ABOVE
                    ) {
                        "${stringResource(R.string.vitals_reading_outside)} · $line"
                    } else {
                        "$line · ${stringResource(R.string.vitals_reading_inside)}"
                    },
                    style = OhTheme.type.caption,
                    color = if (group.position == RangePosition.BELOW ||
                        group.position == RangePosition.ABOVE
                    ) {
                        OhTheme.colors.orangeInk
                    } else {
                        OhTheme.colors.label2
                    },
                    modifier = Modifier.padding(
                        start = OhTheme.dims.rowPadH,
                        bottom = OhTheme.space.s2,
                    ),
                )
            }
        }
        Spacer(Modifier.height(OhTheme.space.s3))
        OhButton(text = stringResource(R.string.vitals_readings_add), onClick = onAddReading)
    }
}

@Composable
private fun WeightTableSheet(model: WeightCardModel, imperial: Boolean, onDismiss: () -> Unit) {
    OhModalSheet(
        visible = true,
        onDismiss = onDismiss,
        title = stringResource(R.string.vitals_weight_show_table),
        cancelLabel = stringResource(R.string.vitals_table_close),
        onCancel = onDismiss,
    ) {
        val unit = if (imperial) "lb" else "kg"
        val factor = if (imperial) KG_TO_LB else 1.0
        Text(
            text = stringResource(
                R.string.vitals_weight_latest_line,
                number(model.latestKg * factor, 1),
                unit,
            ),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
        Spacer(Modifier.height(OhTheme.space.s3))
        OhInsetGroup {
            model.points.forEach { point ->
                OhRow(
                    title = shortDate(point.date),
                    value = "${number(point.value * factor, 1)} $unit",
                )
            }
        }
    }
}

private const val PHOTO_COLUMNS = 3
private const val KG_TO_LB = 2.2046226218
