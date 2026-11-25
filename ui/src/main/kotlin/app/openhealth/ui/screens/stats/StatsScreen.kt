package app.openhealth.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.openhealth.ui.R
import app.openhealth.ui.components.ChartTableModel
import app.openhealth.ui.components.ChartTableSheet
import app.openhealth.ui.components.OhBarChart
import app.openhealth.ui.components.OhBarRow
import app.openhealth.ui.components.OhChartCard
import app.openhealth.ui.components.OhHeatmap
import app.openhealth.ui.components.OhInsetGroup
import app.openhealth.ui.components.OhKpiBand
import app.openhealth.ui.components.OhKpiFigure
import app.openhealth.ui.components.OhRow
import app.openhealth.ui.components.OhSegmentedControl
import app.openhealth.ui.components.OhStackedBar
import app.openhealth.ui.components.OhStatBarList
import app.openhealth.ui.components.OhStatTile
import app.openhealth.ui.screens.common.OhScreen
import app.openhealth.ui.screens.common.number
import app.openhealth.ui.screens.vitals.FormulaSheet
import app.openhealth.ui.screens.vitals.FormulaSheetModel
import app.openhealth.ui.screens.vitals.OhTrendChart
import app.openhealth.ui.screens.vitals.SeriesRole
import app.openhealth.ui.screens.vitals.TrendChartModel
import app.openhealth.ui.screens.vitals.TrendPoint
import app.openhealth.ui.screens.vitals.TrendSeriesModel
import app.openhealth.ui.theme.OhTheme
import app.openhealth.ui.theme.OhWeight

/**
 * Stats (`DESIGN.md` §14.2): a segmented control over Overview, Body, Training, Sleep and
 * Nutrition, a KPI band, then one card per chart. Every card has a table alternative and
 * the "How this is calculated" row that opens the formula and its citation. A chart never
 * invents a number the table does not hold.
 */
@Composable
fun StatsScreen(
    model: StatsModel,
    onSelectSegment: (StatsSegment) -> Unit,
    modifier: Modifier = Modifier,
) {
    var table by remember { mutableStateOf<ChartTableModel?>(null) }
    var formula by remember { mutableStateOf<FormulaSheetModel?>(null) }
    val formulas = model.cards.distinctBy { it.chart }
        .associate { it.chart to formulaFor(it.chart) }
    OhScreen(modifier = modifier) {
        Text(
            text = stringResource(R.string.root_stats),
            style = OhTheme.type.large.copy(fontWeight = OhWeight.bold),
            color = OhTheme.colors.label,
            modifier = Modifier
                .padding(top = OhTheme.space.s6)
                .semantics { heading() },
        )
        Spacer(Modifier.height(OhTheme.space.s1))
        Text(
            text = stringResource(R.string.stats_subtitle),
            style = OhTheme.type.subhead,
            color = OhTheme.colors.label2,
        )
        if (model.demo) {
            Spacer(Modifier.height(OhTheme.space.s2))
            Text(
                text = stringResource(R.string.today_sample_data),
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
            )
        }
        Spacer(Modifier.height(OhTheme.space.s5))
        OhSegmentedControl(
            options = StatsSegment.entries.map { segmentLabel(it) },
            selectedIndex = model.segment.ordinal,
            onSelect = { index -> onSelectSegment(StatsSegment.entries[index]) },
        )
        Spacer(Modifier.height(OhTheme.space.s5))
        if (model.kpis.size >= KPI_BAND) {
            OhKpiBand(
                figures = model.kpis.take(KPI_BAND).map {
                    OhKpiFigure(value = it.value, label = it.label, unit = it.unit)
                },
            )
        } else if (model.kpis.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(OhTheme.space.s3)) {
                model.kpis.forEach {
                    OhStatTile(label = it.label, value = it.value, unit = it.unit)
                }
            }
        }
        if (model.cards.isEmpty() && model.records == null) {
            Spacer(Modifier.height(OhTheme.space.s7))
            val moduleOff = when (model.segment) {
                StatsSegment.TRAINING ->
                    app.openhealth.core.preferences.HealthModule.TRAINING !in model.modules
                StatsSegment.SLEEP ->
                    app.openhealth.core.preferences.HealthModule.SLEEP !in model.modules
                else -> false
            }
            val neutral = model.weightNeutral &&
                (model.segment == StatsSegment.BODY || model.segment == StatsSegment.NUTRITION)
            Text(
                text = when {
                    moduleOff -> stringResource(R.string.stats_module_off_title)
                    neutral -> stringResource(R.string.today_status_weight_neutral)
                    else -> stringResource(R.string.stats_empty_title)
                },
                style = OhTheme.type.title3,
                color = OhTheme.colors.label,
            )
            if (!moduleOff && !neutral) {
                Spacer(Modifier.height(OhTheme.space.s1))
                Text(
                    text = stringResource(R.string.stats_empty_body),
                    style = OhTheme.type.footnote,
                    color = OhTheme.colors.label2,
                )
            }
        }
        model.cards.forEach { card ->
            Spacer(Modifier.height(OhTheme.space.s5))
            StatsChartCard(
                card = card,
                onShowTable = { table = card.table },
                onShowFormula = { formula = formulas[card.chart] },
            )
        }
        model.records?.let { records ->
            Spacer(Modifier.height(OhTheme.space.s5))
            Text(
                text = records.title,
                style = OhTheme.type.body.copy(fontWeight = OhWeight.semibold),
                color = OhTheme.colors.label,
            )
            Spacer(Modifier.height(OhTheme.space.s3))
            if (records.rows.isEmpty()) {
                Text(
                    text = stringResource(R.string.stats_pr_none),
                    style = OhTheme.type.footnote,
                    color = OhTheme.colors.label2,
                )
            } else {
                OhInsetGroup {
                    records.rows.forEachIndexed { index, row ->
                        OhRow(title = row, showDivider = index > 0)
                    }
                }
            }
        }
        Spacer(Modifier.height(OhTheme.space.s7))
    }
    table?.let { ChartTableSheet(model = it, onDismiss = { table = null }) }
    formula?.let { FormulaSheet(sheet = it, onDismiss = { formula = null }) }
}

@Composable
private fun StatsChartCard(card: StatsCard, onShowTable: () -> Unit, onShowFormula: () -> Unit) {
    OhChartCard(
        title = card.title,
        eyebrow = stringResource(R.string.root_stats),
        onShowTable = onShowTable,
        onShowFormula = onShowFormula,
        footer = card.footer,
    ) {
        when (val plot = card.plot) {
            is StatsPlot.Line -> OhTrendChart(
                model = TrendChartModel(
                    series = listOf(
                        TrendSeriesModel(
                            label = null,
                            points = plot.points.map { TrendPoint(it.date, it.value) },
                            role = SeriesRole.SINGLE,
                        ),
                    ),
                    unitText = card.unitText,
                    goal = card.goal,
                    description = card.description,
                    decimals = card.decimals,
                ),
                onShowTable = null,
            )

            is StatsPlot.Bars -> OhBarChart(
                bars = plot.bars,
                description = card.description,
            )

            is StatsPlot.Heat -> OhHeatmap(
                weeks = plot.weeks,
                description = card.description,
            )

            is StatsPlot.BarsList -> OhStatBarList(
                rows = plot.rows,
                description = card.description,
            )

            is StatsPlot.Stack -> StackedChart(plot = plot, description = card.description)

            is StatsPlot.Macros -> Column(
                modifier = Modifier.semantics { contentDescription = card.description },
            ) {
                plot.rows.forEach { macro ->
                    OhBarRow(
                        label = macro.name,
                        value = number(macro.grams),
                        target = macro.targetGrams?.let { stringResource(R.string.today_macro_target, number(it)) },
                        progress = macro.fraction,
                        ink = hueInk(macro.hue),
                    )
                    Spacer(Modifier.height(OhTheme.space.s2))
                }
            }
        }
    }
}

@Composable
private fun StackedChart(plot: StatsPlot.Stack, description: String) {
    val colors = OhTheme.colors
    val inks = plot.segments.map { hueInk(it.hue) }
    OhStackedBar(
        segments = plot.segments.mapIndexed { index, segment ->
            segment.value to
                inks[index]
        },
    )
    Spacer(Modifier.height(OhTheme.space.s3))
    plot.segments.forEachIndexed { index, segment ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(OhTheme.dims.chartLegendDot)
                    .clip(RoundedCornerShape(OhTheme.radius.full))
                    .background(inks[index]),
            )
            Spacer(Modifier.width(OhTheme.space.s2))
            Text(
                text = segment.name,
                style = OhTheme.type.footnote,
                color = colors.label2,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = number(segment.value, 1),
                style = OhTheme.type.footnote,
                color = colors.label,
            )
        }
        Spacer(Modifier.height(OhTheme.space.s1))
    }
}

@Composable
private fun hueInk(hue: StatsHue) = when (hue) {
    StatsHue.ORANGE -> OhTheme.colors.orange
    StatsHue.FROST -> OhTheme.colors.frost
    StatsHue.BLUE -> OhTheme.colors.blue
    StatsHue.PURPLE -> OhTheme.colors.purple
    StatsHue.GREEN -> OhTheme.colors.green
    StatsHue.TEAL -> OhTheme.colors.teal
}

@Composable
private fun segmentLabel(segment: StatsSegment): String = stringResource(
    when (segment) {
        StatsSegment.OVERVIEW -> R.string.stats_segment_overview
        StatsSegment.BODY -> R.string.stats_segment_body
        StatsSegment.TRAINING -> R.string.stats_segment_training
        StatsSegment.SLEEP -> R.string.stats_segment_sleep
        StatsSegment.NUTRITION -> R.string.stats_segment_nutrition
    },
)

@Composable
private fun formulaFor(id: ChartId): FormulaSheetModel {
    val meta = ChartRegistry.of(id)
    return FormulaSheetModel(
        title = stringResource(meta.formulaTitleRes),
        body = stringResource(meta.formulaBodyRes),
        citation = meta.citation.name,
    )
}

private const val KPI_BAND = 3
