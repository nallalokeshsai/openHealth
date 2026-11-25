// Chart geometry is quoted from DESIGN.md §8.1-8.3; percentages are drawing
// arithmetic, not theme values.
@file:Suppress("MagicNumber")

package app.openhealth.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import app.openhealth.core.stats.RampLevel
import app.openhealth.ui.screens.common.observedLocale
import app.openhealth.ui.theme.OhRamp
import app.openhealth.ui.theme.OhTheme
import app.openhealth.ui.theme.tabular
import kotlinx.datetime.LocalDate

/** One point of a chart. A missing day is absent from the list, never drawn as zero. */
data class ChartPoint(val date: LocalDate, val value: Double)

/** One bar of a bar chart, named so the chart survives colour blindness. */
data class ChartBar(val label: String, val value: Double)

/** One cell of a heatmap with its ramp step (`DESIGN.md` §8.3). */
data class HeatCellUi(val date: LocalDate, val level: RampLevel)

/** One row of the horizontal bar list (`DESIGN.md` §8.2). */
data class StatBarRow(val label: String, val value: Double, val valueText: String)

/**
 * A sparkline inside a card (`DESIGN.md` §8.2): 1.5 dp accent, a 3 dp last point, no
 * axes. It is decorative, so its meaning is carried by the card's sentence and its
 * table alternative, not by a content description of dots.
 */
@Composable
fun OhSparkline(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
    goal: Double? = null,
    description: String? = null,
) {
    if (points.size < 2) return
    val colors = OhTheme.colors
    val valueLow = points.minOf { it.value }
    val valueHigh = points.maxOf { it.value }
    val valueSpan = (valueHigh - valueLow).takeIf { it > 0.0 } ?: 1.0
    val goalVisible = goal != null &&
        goal >= valueLow - valueSpan &&
        goal <= valueHigh + valueSpan
    val low = if (goalVisible) minOf(valueLow, goal!!) else valueLow
    val high = if (goalVisible) maxOf(valueHigh, goal!!) else valueHigh
    val span = (high - low).takeIf { it > 0.0 } ?: 1.0
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(OhTheme.dims.chartHeight / 3)
            .then(
                if (description == null) {
                    Modifier
                } else {
                    Modifier.semantics { contentDescription = description }
                },
            ),
    ) {
        val stepX = size.width / (points.size - 1)
        val path = Path()
        points.forEachIndexed { index, point ->
            val x = stepX * index
            val y = (size.height - (point.value - low) / span * size.height).toFloat()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = colors.accInk,
            style = Stroke(
                width = OhTheme.dims.chartSparkLine.toPx(),
                cap = StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            ),
        )
        if (goalVisible) {
            val goalY = (size.height - (goal - low) / span * size.height).toFloat()
            drawLine(
                color = colors.goldInk,
                start = Offset(0f, goalY),
                end = Offset(size.width, goalY),
                strokeWidth = OhTheme.dims.chartGoal.toPx(),
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                    floatArrayOf(
                        OhTheme.dims.chartDashOn.toPx(),
                        OhTheme.dims.chartDashOff.toPx(),
                    ),
                ),
            )
        }
        val last = points.last()
        val lastY = (size.height - (last.value - low) / span * size.height).toFloat()
        drawCircle(
            color = colors.accInk,
            radius = OhTheme.dims.chartSparkPoint.toPx(),
            center = Offset(size.width, lastY),
        )
    }
}

/**
 * A bar chart (`DESIGN.md` §8.2): 2 dp gaps, an 8 dp radius cap, gridlines only, and a
 * zero baseline because a count or a volume means something from zero. [labels] are shown
 * beneath their bars; when there are more than seven only every other one is printed.
 */
@Composable
fun OhBarChart(bars: List<ChartBar>, description: String, modifier: Modifier = Modifier) {
    if (bars.isEmpty()) return
    val colors = OhTheme.colors
    val type = OhTheme.type
    val maximum = bars.maxOf { it.value }.takeIf { it > 0.0 } ?: 1.0
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(modifier = modifier.fillMaxWidth()) {
            Row {
                Column(
                    modifier = Modifier
                        .width(OhTheme.dims.chartAxisWidth)
                        .height(OhTheme.dims.chartHeight),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = axisNumber(maximum),
                        style = type.caption.tabular(),
                        color = colors.label2,
                    )
                    Text(text = "0", style = type.caption.tabular(), color = colors.label2)
                }
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(OhTheme.dims.chartHeight)
                        .semantics { contentDescription = description },
                ) {
                    val gridColor = colors.sepOp
                    repeat(GRIDLINES) { index ->
                        val y = size.height * index / (GRIDLINES - 1)
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = OhTheme.dims.chartGrid.toPx(),
                        )
                    }
                    val gap = OhTheme.dims.macroBarGap.toPx()
                    val slot = (size.width - gap * (bars.size - 1)) / bars.size
                    bars.forEachIndexed { index, bar ->
                        val height = (bar.value / maximum * size.height).toFloat()
                            .coerceIn(0f, size.height)
                        val left = index * (slot + gap)
                        if (height > 0f) {
                            drawRoundRect(
                                color = colors.acc,
                                topLeft = Offset(left, size.height - height),
                                size = Size(slot, height),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                                    OhTheme.dims.macroBarHeight.toPx(),
                                ),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(OhTheme.space.s1))
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(OhTheme.dims.chartAxisWidth))
                val everyOther = bars.size > SEVEN
                bars.forEachIndexed { index, bar ->
                    val show = !everyOther || index % 2 == 0 || index == bars.lastIndex
                    Text(
                        text = if (show) bar.label else "",
                        style = type.caption2.tabular(),
                        color = colors.label2,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * The 12-week heatmap (`DESIGN.md` §8.2): 11 dp cells, a 2 dp gap, a 2 dp radius and the
 * five-step ramp, so "more" always looks the same. Month labels sit over the first week
 * of their month; the table alternative names every day.
 */
@Composable
fun OhHeatmap(weeks: List<List<HeatCellUi>>, description: String, modifier: Modifier = Modifier) {
    if (weeks.isEmpty()) return
    val colors = OhTheme.colors
    val type = OhTheme.type
    val cell = OhTheme.dims.heatCell
    val gap = OhTheme.dims.heatGap
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(modifier = modifier.fillMaxWidth()) {
            val months = mutableListOf<Pair<Int, Int>>()
            weeks.forEach { week ->
                val month = week.firstOrNull()?.date?.month?.ordinal ?: return@forEach
                val last = months.lastOrNull()
                if (last == null || last.first != month) {
                    months += month to 1
                } else {
                    months[months.lastIndex] = month to last.second + 1
                }
            }
            Row {
                months.forEach { (month, span) ->
                    Text(
                        text = monthShort(month),
                        style = type.caption2,
                        color = colors.label3,
                        maxLines = 1,
                        modifier = Modifier.width((cell + gap) * span),
                    )
                }
            }
            Spacer(Modifier.height(OhTheme.space.s0))
            Row(
                modifier = Modifier.semantics { contentDescription = description },
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                weeks.forEach { week ->
                    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        week.forEach { cellData ->
                            val step = when (cellData.level) {
                                RampLevel.L0 -> OhRamp.l0(colors)
                                RampLevel.L1 -> OhRamp.l1(colors)
                                RampLevel.L2 -> OhRamp.l2(colors)
                                RampLevel.L3 -> OhRamp.l3(colors)
                                RampLevel.L4 -> OhRamp.l4(colors)
                            }
                            Box(
                                modifier = Modifier
                                    .size(cell)
                                    .clip(RoundedCornerShape(OhTheme.dims.heatRadius))
                                    .background(step.track)
                                    .then(
                                        step.overlay?.let { overlay ->
                                            Modifier.background(overlay)
                                        } ?: Modifier,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The horizontal bar list of `DESIGN.md` §8.2: name, an 8 dp track, the fill and the
 * value. At 400 dp and below the bar runs full width under the name, so 200 % text never
 * squeezes the value out.
 */
@Composable
fun OhStatBarList(rows: List<StatBarRow>, description: String, modifier: Modifier = Modifier) {
    if (rows.isEmpty()) return
    val colors = OhTheme.colors
    val type = OhTheme.type
    val maximum = rows.maxOf { it.value }.takeIf { it > 0.0 } ?: 1.0
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
    ) {
        val stack = maxWidth <= OhTheme.dims.statBarStackWidth
        val nameWidth = maxWidth * NAME_SHARE
        Column(verticalArrangement = Arrangement.spacedBy(OhTheme.space.s3)) {
            rows.forEach { row ->
                if (stack) {
                    Text(text = row.label, style = type.footnote, color = colors.label2)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.weight(1f)) {
                            OhBar(progress = (row.value / maximum).toFloat(), ink = colors.accInk)
                        }
                        Spacer(Modifier.width(OhTheme.space.s3))
                        Text(
                            text = row.valueText,
                            style = type.footnote.tabular(),
                            color = colors.label,
                        )
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = row.label,
                            style = type.footnote,
                            color = colors.label2,
                            modifier = Modifier.width(nameWidth),
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            OhBar(progress = (row.value / maximum).toFloat(), ink = colors.accInk)
                        }
                        Spacer(Modifier.width(OhTheme.space.s3))
                        Text(
                            text = row.valueText,
                            style = type.footnote.tabular(),
                            color = colors.label,
                        )
                    }
                }
            }
        }
    }
}

/** A labelled legend swatch; the macro and sleep hues always arrive with a name. */
@Composable
fun OhLegend(items: List<Pair<String, Color>>, modifier: Modifier = Modifier) {
    val colors = OhTheme.colors
    val type = OhTheme.type
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(OhTheme.space.s3),
    ) {
        items.forEach { (label, ink) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(OhTheme.dims.chartLegendDot)
                        .clip(RoundedCornerShape(OhTheme.radius.full))
                        .background(ink),
                )
                Spacer(Modifier.width(OhTheme.space.s1))
                Text(text = label, style = type.caption2, color = colors.label2)
            }
        }
    }
}

/** The 7 dp dot and the label of a chart's own key; exposed for a stacked bar's legend. */
@Composable
fun OhChartKey(label: String, ink: Color, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(OhTheme.dims.chartLegendDot)
                .clip(RoundedCornerShape(OhTheme.radius.full))
                .background(ink),
        )
        Spacer(Modifier.width(OhTheme.space.s1))
        Text(
            text = label,
            style = OhTheme.type.caption2,
            color = OhTheme.colors.label2,
        )
    }
}

/** A stacked bar row, 14 dp tall with 2 dp seams (`DESIGN.md` §8.2). */
@Composable
fun OhStackedBar(segments: List<Pair<Double, Color>>, modifier: Modifier = Modifier) {
    val height = OhTheme.dims.stackedBarHeight
    val total = segments.sumOf { it.first }.takeIf { it > 0.0 } ?: 1.0
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(OhTheme.radius.sm)),
        horizontalArrangement = Arrangement.spacedBy(OhTheme.dims.macroBarGap),
    ) {
        segments.forEach { (value, ink) ->
            if (value > 0.0) {
                Box(
                    modifier = Modifier
                        .weight((value / total).toFloat())
                        .height(height)
                        .background(ink),
                )
            }
        }
    }
}

/** Axis figures never take more than four characters: 1,240 stays, 12,480 becomes 12.5k. */
private fun axisNumber(value: Double): String = when {
    value >= 10_000 -> "${kotlin.math.round(value / 100.0) / 10.0}k"
    value >= 1_000 -> "${kotlin.math.round(value / 1_000.0).toInt()}k"
    else -> value.toInt().toString()
}

private const val GRIDLINES = 4
private const val SEVEN = 7
private const val NAME_SHARE = 0.30f

/** A month's short name for the heatmap label row, in the chart's own language. */
@Composable
private fun monthShort(monthOrdinal: Int): String =
    java.time.Month.of(monthOrdinal + 1).getDisplayName(
        java.time.format.TextStyle.SHORT,
        observedLocale(),
    )
