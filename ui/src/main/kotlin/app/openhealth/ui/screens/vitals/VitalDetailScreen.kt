package app.openhealth.ui.screens.vitals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.openhealth.core.observation.ObservationContext
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.vitals.HomeSlot
import app.openhealth.core.vitals.RangePosition
import app.openhealth.ui.R
import app.openhealth.ui.components.OhButton
import app.openhealth.ui.components.OhButtonVariant
import app.openhealth.ui.components.OhCard
import app.openhealth.ui.components.ohClickable
import app.openhealth.ui.components.rememberInteraction
import app.openhealth.ui.components.OhCardHeader
import app.openhealth.ui.components.OhChip
import app.openhealth.ui.components.OhChipStrip
import app.openhealth.ui.components.OhInsetGroup
import app.openhealth.ui.components.OhKpiBand
import app.openhealth.ui.components.OhKpiFigure
import app.openhealth.ui.components.OhModalSheet
import app.openhealth.ui.components.OhRow
import app.openhealth.ui.screens.common.OhEmptyState
import app.openhealth.ui.screens.common.OhPageHeader
import app.openhealth.ui.screens.common.OhScreen
import app.openhealth.ui.screens.common.OhSectionTitle
import app.openhealth.ui.screens.common.decimalText
import app.openhealth.ui.screens.common.number
import app.openhealth.ui.screens.common.shortDate
import app.openhealth.ui.screens.common.timeOf
import app.openhealth.ui.theme.OhTheme
import app.openhealth.ui.theme.OhWeight
import app.openhealth.ui.theme.tabular
import kotlinx.datetime.TimeZone

/**
 * The Vitals detail page (`DESIGN.md` §14.2 Vitals detail): a KPI band, the cited range
 * strip, a 30-day line, context chips, the reading list with provenance, and Add reading
 * as the single primary. The home-BP protocol is a sheet that walks two readings in the
 * morning and two in the evening for seven days (`pr.md` §6.9).
 */
@Composable
fun VitalDetailScreen(
    model: VitalDetailModel,
    imperial: Boolean,
    onBack: () -> Unit,
    onSaveReading: (VitalDraft) -> Unit,
    onDeleteReading: (String) -> Unit,
    onSelectContext: (ObservationContext?) -> Unit,
    onStartProtocol: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showTable by rememberSaveable { mutableStateOf(false) }
    var showProtocol by rememberSaveable { mutableStateOf(false) }
    var provenance by rememberSaveable { mutableStateOf<String?>(null) }
    var addSlot by rememberSaveable { mutableStateOf<HomeSlot?>(null) }
    var addReading by rememberSaveable { mutableStateOf(false) }
    val provenanceReading = provenance?.let { id -> model.readings.firstOrNull { it.id == id } }
    OhScreen(modifier = modifier) {
        OhPageHeader(title = model.title, onBack = onBack)
        if (model.readings.isEmpty()) {
            OhCard {
                OhEmptyState(
                    title = stringResource(R.string.vitals_readings_empty_title),
                    body = stringResource(R.string.vitals_detail_no_readings),
                    actionLabel = stringResource(R.string.vitals_readings_add),
                    onAction = { addReading = true },
                )
            }
            return@OhScreen
        }
        val kpiInteraction = rememberInteraction()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .ohClickable(
                    interactionSource = kpiInteraction,
                    onClick = { provenance = model.readings.firstOrNull()?.id },
                ),
        ) {
        OhKpiBand(
            figures = listOf(
                OhKpiFigure(
                    value = model.latestText,
                    label = stringResource(R.string.vitals_detail_latest),
                ),
                OhKpiFigure(
                    value = model.averageText,
                    label = stringResource(R.string.vitals_detail_average),
                ),
                OhKpiFigure(
                    value = model.count.toString(),
                    label = stringResource(R.string.vitals_detail_count),
                ),
            ),
            footer = model.unitText,
        )
        }
        model.range?.let { range ->
            Spacer(Modifier.height(OhTheme.space.s5))
            OhCard {
                OhCardHeader(
                    eyebrow = stringResource(R.string.vitals_detail_range_title),
                    title = stringResource(R.string.vitals_detail_range_title),
                    glyph = app.openhealth.ui.icons.OhIcons.info,
                )
                Spacer(Modifier.height(OhTheme.space.s3))
                Text(
                    text = stringResource(
                        R.string.vitals_range_citation,
                        range.population,
                        range.statement,
                    ) + " " + model.unitText,
                    style = OhTheme.type.footnote,
                    color = OhTheme.colors.label,
                )
                Spacer(Modifier.height(OhTheme.space.s1))
                Text(
                    text = range.citationLabel,
                    style = OhTheme.type.footnote,
                    color = OhTheme.colors.label2,
                )
                Spacer(Modifier.height(OhTheme.space.s1))
                Text(
                    text = range.boundsText,
                    style = OhTheme.type.caption.tabular(),
                    color = OhTheme.colors.label2,
                )
                model.range.position?.let { position ->
                    Spacer(Modifier.height(OhTheme.space.s2))
                    Text(
                        text = if (position == RangePosition.WITHIN) {
                            stringResource(R.string.vitals_reading_inside)
                        } else {
                            stringResource(R.string.vitals_reading_outside)
                        },
                        style = OhTheme.type.footnote,
                        color = if (position == RangePosition.WITHIN) {
                            OhTheme.colors.label2
                        } else {
                            OhTheme.colors.orangeInk
                        },
                    )
                }
                model.scaleValues.forEach { reading ->
                    Spacer(Modifier.height(OhTheme.space.s4))
                    Text(
                        text = reading.label,
                        style = OhTheme.type.caption,
                        color = OhTheme.colors.label2,
                    )
                    OhClinicalRangeStrip(scale = reading.scale, value = reading.value)
                }
            }
        }
        if (model.chart != null) {
            Spacer(Modifier.height(OhTheme.space.s5))
            OhCard {
                OhCardHeader(
                    eyebrow = stringResource(R.string.vitals_detail_30_day),
                    title = model.title,
                    glyph = app.openhealth.ui.icons.OhModuleIcons.heart,
                )
                Spacer(Modifier.height(OhTheme.space.s3))
                OhTrendChart(model = model.chart, onShowTable = { showTable = true })
            }
        }
        if (model.contexts.isNotEmpty()) {
            Spacer(Modifier.height(OhTheme.space.s4))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(OhTheme.space.s2),
            ) {
                model.contexts.forEach { context ->
                    OhChip(
                        text = context.label,
                        selected = context.selected,
                        onClick = { onSelectContext(context.context) },
                    )
                }
            }
        }
        Spacer(Modifier.height(OhTheme.space.s5))
        OhSectionTitle(stringResource(R.string.vitals_readings_title))
        OhInsetGroup {
            model.readings.forEach { reading ->
                OhRow(
                    title = reading.values.joinToString(" / ") {
                        decimalText(
                            vitalDisplayValue(model.type, it, imperial),
                            decimals(model.type),
                        )
                    },
                    subtitle = listOfNotNull(
                        reading.contextText,
                        shortDate(reading.provenance.localDate),
                        timeOf(reading.happenedAt, TimeZone.of(reading.provenance.zoneId)),
                    ).joinToString(" · "),
                    value = reading.unitText,
                    onClick = { provenance = reading.id },
                    showChevron = true,
                )
            }
        }
        Spacer(Modifier.height(OhTheme.space.s5))
        OhButton(
            text = stringResource(R.string.vitals_readings_add),
            onClick = { addReading = true },
        )
        if (model.type == ObservationType.BLOOD_PRESSURE_SYSTOLIC) {
            Spacer(Modifier.height(OhTheme.space.s3))
            OhButton(
                text = stringResource(R.string.vitals_detail_home_protocol),
                onClick = { showProtocol = true },
                variant = OhButtonVariant.Secondary,
            )
        }
        Spacer(Modifier.height(OhTheme.space.s7))
    }
    if (showTable) {
        OhModalSheet(
            visible = true,
            onDismiss = { showTable = false },
            title = stringResource(R.string.vitals_detail_table_title),
            cancelLabel = stringResource(R.string.vitals_table_close),
            onCancel = { showTable = false },
        ) {
            OhInsetGroup {
                model.readings.forEach { reading ->
                    OhRow(
                        title = shortDate(reading.provenance.localDate),
                        subtitle = reading.contextText,
                        value = reading.values.joinToString(" / ") {
                            decimalText(
                                vitalDisplayValue(model.type, it, imperial),
                                decimals(model.type),
                            )
                        } + " " + reading.unitText,
                    )
                }
            }
        }
    }
    if (addReading) {
        AddReadingSheet(
            visible = true,
            initialType = model.type,
            imperial = imperial,
            homeSlot = addSlot,
            onDismiss = { addReading = false; addSlot = null },
            onSave = { draft ->
                addReading = false
                addSlot = null
                onSaveReading(draft)
            },
        )
    }
    provenanceReading?.let { reading ->
        VitalsProvenanceSheet(
            provenance = reading.provenance,
            onDismiss = { provenance = null },
            onDelete = {
                provenance = null
                onDeleteReading(reading.id)
            },
        )
    }
    if (showProtocol) {
        model.protocol?.let { protocol ->
            BpProtocolSheet(
                model = protocol,
                onDismiss = { showProtocol = false },
                onStart = onStartProtocol,
                onAddReading = { slot ->
                    showProtocol = false
                    addSlot = slot
                    addReading = true
                },
            )
        }
    }
}

@Composable
private fun BpProtocolSheet(
    model: BpProtocolModel,
    onDismiss: () -> Unit,
    onStart: () -> Unit,
    onAddReading: (HomeSlot) -> Unit,
) {
    OhModalSheet(
        visible = true,
        onDismiss = onDismiss,
        title = stringResource(R.string.vitals_detail_home_protocol),
        cancelLabel = stringResource(R.string.vitals_table_close),
        onCancel = onDismiss,
    ) {
        Text(
            text = stringResource(R.string.vitals_detail_home_protocol_body),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
        Spacer(Modifier.height(OhTheme.space.s4))
        if (!model.active) {
            OhButton(
                text = stringResource(R.string.vitals_detail_home_protocol_start),
                onClick = onStart,
            )
            return@OhModalSheet
        }
        Text(
            text = stringResource(
                R.string.vitals_detail_home_protocol_progress,
                model.state.recorded,
                model.state.target,
            ),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label,
        )
        Spacer(Modifier.height(OhTheme.space.s3))
        OhInsetGroup {
            model.state.days.forEach { day ->
                OhRow(
                    title = shortDate(day.date),
                    subtitle = stringResource(
                        R.string.vitals_detail_home_protocol_morning,
                    ) + " ${day.morning}/${day.targetPerSlot} · " +
                        stringResource(R.string.vitals_detail_home_protocol_evening) +
                        " ${day.evening}/${day.targetPerSlot}",
                    value = if (day.complete) "✓" else "",
                )
            }
        }
        Spacer(Modifier.height(OhTheme.space.s4))
        model.averageSystolic?.let { systolic ->
            Text(
                text = stringResource(
                    R.string.vitals_detail_home_protocol_average_value,
                    number(systolic, 0),
                    number(model.averageDiastolic ?: 0.0, 0),
                ),
                style = OhTheme.type.title3.copy(fontWeight = OhWeight.semibold).tabular(),
                color = OhTheme.colors.label,
            )
            Text(
                text = stringResource(
                    R.string.vitals_detail_home_protocol_average_body,
                    model.averageReadings,
                ),
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
            )
            Spacer(Modifier.height(OhTheme.space.s3))
        }
        model.state.nextSlot(model.state.startDate)?.let { next ->
            OhButton(
                text = stringResource(
                    R.string.vitals_detail_home_protocol_next,
                    stringResource(
                        if (next.slot == HomeSlot.MORNING) {
                            R.string.vitals_detail_home_protocol_slot_morning
                        } else {
                            R.string.vitals_detail_home_protocol_slot_evening
                        },
                    ),
                ),
                onClick = { onAddReading(next.slot) },
            )
        }
    }
}

private fun decimals(type: ObservationType): Int = when (type) {
    ObservationType.BODY_TEMPERATURE -> 1
    ObservationType.BLOOD_GLUCOSE -> 1
    ObservationType.OXYGEN_SATURATION -> 0
    else -> 0
}

/** The table alternative of a range or a chart is a list of the same numbers. */
@Composable
fun VitalTable(model: VitalDetailModel, imperial: Boolean) {
    Column(Modifier.fillMaxWidth()) {
        model.tableHeader.forEach { header ->
            Text(
                text = header,
                style = OhTheme.type.caption,
                color = OhTheme.colors.label2,
                modifier = Modifier.padding(vertical = OhTheme.space.s0),
            )
        }
        model.tableRows.forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { cell ->
                    Text(
                        text = cell,
                        style = OhTheme.type.footnote,
                        color = OhTheme.colors.label,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
