// Chart geometry is quoted from DESIGN.md §8.1-8.2.1; percentages are drawing
// arithmetic, not theme values.
@file:Suppress("MagicNumber")

package app.openhealth.ui.screens.vitals

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import app.openhealth.ui.R
import app.openhealth.ui.components.ohClickable
import app.openhealth.ui.components.rememberInteraction
import app.openhealth.ui.screens.common.decimalText
import app.openhealth.ui.screens.common.number
import app.openhealth.ui.screens.common.shortDate
import app.openhealth.ui.theme.OhTheme
import app.openhealth.ui.theme.tabular
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.datetime.LocalDate

private const val MAX_GAP_DAYS = 14.0

/**
 * A line chart with the chrome of `DESIGN.md` §8.1: horizontal gridlines only, no border,
 * a 2.5 dp round line (2 dp for a second series), a dashed goal line, a reference band at
 * 18 % and a touch scrub that shows one date's values. Missing days are not interpolated:
 * a segment longer than two weeks is left out rather than drawn through nothing.
 *
 * Every chart carries a one-sentence content description and the "Show as table" action
 * its card repeats as a row (`DESIGN.md` §8.6).
 */
@Composable
fun OhTrendChart(
    model: TrendChartModel,
    modifier: Modifier = Modifier,
    onShowTable: (() -> Unit)? = null,
) {
    val colors = OhTheme.colors
    val type = OhTheme.type
    val dims = OhTheme.dims
    val density = LocalDensity.current
    var selected by remember(model.series) { mutableStateOf<LocalDate?>(null) }
    val allDates = model.series.flatMap { series -> series.points.map { it.date } }
    val allValues = model.series.flatMap { series -> series.points.map { it.value } }
    if (allDates.isEmpty() || allValues.isEmpty()) return
    val minDate = allDates.min()
    val maxDate = allDates.max()
    val dateSpan = (maxDate.toEpochDays() - minDate.toEpochDays()).toDouble()
        .coerceAtLeast(1.0)
    val valueLow = allValues.min()
    val valueHigh = allValues.max()
    val valueSpan = (valueHigh - valueLow).takeIf { it > 0.0 } ?: 1.0
    val goalVisible = model.goal != null &&
        model.goal >= valueLow - valueSpan &&
        model.goal <= valueHigh + valueSpan
    val low = if (model.startAtZero) {
        0.0
    } else {
        listOfNotNull(valueLow, model.referenceLow, if (goalVisible) model.goal else null).min()
    }
    val high = listOfNotNull(
        valueHigh,
        model.referenceHigh,
        if (goalVisible) model.goal else null,
    ).max()
    val padding = ((high - low) * 0.12).takeIf { it > 0.0 } ?: 1.0
    val yLow = if (model.startAtZero) 0.0 else low - padding
    val yHigh = high + padding
    val ySpan = (yHigh - yLow).coerceAtLeast(0.001)
    val tableLabel = stringResource(R.string.vitals_show_table)
    val lineColors = listOf(colors.accInk, colors.blueInk)
    val dash = remember(density) {
        PathEffect.dashPathEffect(
            floatArrayOf(
                with(density) { dims.chartDashOn.toPx() },
                with(density) { dims.chartDashOff.toPx() },
            ),
        )
    }

    fun xFraction(date: LocalDate): Float =
        ((date.toEpochDays() - minDate.toEpochDays()) / dateSpan).toFloat().coerceIn(0f, 1f)

    fun yFraction(value: Double): Float = (1.0 - (value - yLow) / ySpan).toFloat().coerceIn(0f, 1f)

    androidx.compose.runtime.CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Ltr,
    ) {
        Column(modifier = modifier.fillMaxWidth()) {
            // The scrub result is a sentence above the plot, so it is reachable without a
            // floating tooltip and survives 200 % text.
            selected?.let { date ->
                val values = model.series.mapNotNull { series ->
                    series.points.firstOrNull { it.date == date }?.let { point ->
                        (series.label ?: "") to point.value
                    }
                }
                Text(
                    text = if (values.size == 1) {
                        stringResource(
                            R.string.vitals_chart_scrub_one,
                            shortDate(date),
                            number(values.single().second, model.decimals),
                            model.unitText,
                        )
                    } else {
                        stringResource(
                            R.string.vitals_chart_scrub_two,
                            shortDate(date),
                            values.joinToString(" · ") {
                                "${it.first} ${decimalText(it.second, model.decimals)}"
                            },
                        )
                    },
                    style = type.caption,
                    color = colors.label2,
                )
                Spacer(Modifier.height(OhTheme.space.s2))
            }
            Row {
                Column(
                    modifier = Modifier.width(dims.chartAxisWidth).height(dims.chartHeight),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = number(yHigh, model.decimals),
                        style = type.caption.tabular(),
                        color = colors.label2,
                    )
                    Text(
                        text = number(yLow, model.decimals),
                        style = type.caption.tabular(),
                        color = colors.label2,
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(dims.chartHeight)
                        .semantics {
                            contentDescription = model.description
                            if (onShowTable != null) {
                                customActions = listOf(
                                    CustomAccessibilityAction(tableLabel) {
                                        onShowTable()
                                        true
                                    },
                                )
                            }
                        }
                        .pointerInput(model.series) {
                            detectTapGestures { offset ->
                                selected = nearestDate(
                                    offset.x / size.width,
                                    allDates,
                                    minDate,
                                    dateSpan,
                                )
                            }
                        }
                        .pointerInput(model.series) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    selected = nearestDate(
                                        offset.x / size.width,
                                        allDates,
                                        minDate,
                                        dateSpan,
                                    )
                                },
                            ) { change, _ ->
                                selected = nearestDate(
                                    change.position.x / size.width,
                                    allDates,
                                    minDate,
                                    dateSpan,
                                )
                            }
                        },
                ) {
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        // Horizontal gridlines only (`DESIGN.md` §8.1).
                        repeat(3) { index ->
                            val y = h * (index + 1) / 4f
                            drawLine(
                                color = colors.sepOp,
                                start = Offset(0f, y),
                                end = Offset(w, y),
                                strokeWidth = dims.chartGrid.toPx(),
                            )
                        }
                        // The reference band, green at 18 %, never a hard coloured edge.
                        if (model.referenceLow != null && model.referenceHigh != null) {
                            val top = h * yFraction(model.referenceHigh)
                            val bottom = h * yFraction(model.referenceLow)
                            drawRect(
                                color = colors.greenInk.copy(alpha = 0.18f),
                                topLeft = Offset(0f, top),
                                size = Size(w, (bottom - top).coerceAtLeast(1f)),
                            )
                        }
                        model.goal?.takeIf { goalVisible }?.let { goal ->
                            val y = h * yFraction(goal)
                            drawLine(
                                color = colors.goldInk,
                                start = Offset(0f, y),
                                end = Offset(w, y),
                                strokeWidth = dims.chartGoal.toPx(),
                                pathEffect = dash,
                            )
                        }
                        model.series.forEachIndexed { index, series ->
                            val ink = lineColors[index % lineColors.size]
                            val points = series.points.sortedBy { it.date }
                            points.zipWithNext().forEach { (from, to) ->
                                val gap = (to.date.toEpochDays() - from.date.toEpochDays())
                                    .toDouble()
                                if (gap > MAX_GAP_DAYS) return@forEach
                                drawLine(
                                    color = ink,
                                    start = Offset(
                                        w * xFraction(from.date),
                                        h * yFraction(from.value),
                                    ),
                                    end = Offset(w * xFraction(to.date), h * yFraction(to.value)),
                                    strokeWidth = if (model.series.size == 1) {
                                        dims.chartLine.toPx()
                                    } else {
                                        dims.chartSecondLine.toPx()
                                    },
                                    cap = StrokeCap.Round,
                                )
                            }
                            points.forEach { point ->
                                drawCircle(
                                    color = ink,
                                    radius = if (point == points.last()) {
                                        dims.chartLastPoint.toPx()
                                    } else {
                                        dims.chartPoint.toPx()
                                    },
                                    center = Offset(
                                        w * xFraction(point.date),
                                        h * yFraction(point.value),
                                    ),
                                )
                            }
                        }
                        selected?.let { date ->
                            val x = w * xFraction(date)
                            drawLine(
                                color = colors.label3,
                                start = Offset(x, 0f),
                                end = Offset(x, h),
                                strokeWidth = dims.chartGrid.toPx(),
                                pathEffect = dash,
                            )
                            model.series.forEachIndexed { index, series ->
                                val point = series.points.firstOrNull { it.date == date }
                                    ?: return@forEachIndexed
                                drawCircle(
                                    color = colors.surface,
                                    radius = dims.chartScrubRing.toPx(),
                                    center = Offset(x, h * yFraction(point.value)),
                                )
                                drawCircle(
                                    color = lineColors[index % lineColors.size],
                                    radius = dims.chartScrubDot.toPx(),
                                    center = Offset(x, h * yFraction(point.value)),
                                )
                            }
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(start = dims.chartAxisWidth)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = shortDate(minDate),
                        style = type.caption.tabular(),
                        color = colors.label2,
                    )
                    Text(
                        text = shortDate(maxDate),
                        style = type.caption.tabular(),
                        color = colors.label2,
                    )
                }
            }
            if (goalVisible && model.goal != null) {
                Spacer(Modifier.height(OhTheme.space.s1))
                Text(
                    text = stringResource(
                        R.string.chart_goal_label,
                        number(model.goal, model.decimals),
                        model.unitText,
                    ),
                    style = type.caption.tabular(),
                    color = colors.label2,
                )
            }
            if (model.series.size > 1) {
                Spacer(Modifier.height(OhTheme.space.s1))
                Row(horizontalArrangement = Arrangement.spacedBy(OhTheme.space.s3)) {
                    model.series.forEachIndexed { index, series ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(dims.chartLegendDot)
                                    .clip(CircleShape)
                                    .background(lineColors[index % lineColors.size]),
                            )
                            Spacer(Modifier.width(OhTheme.space.s1))
                            Text(
                                text = series.label.orEmpty(),
                                style = type.caption,
                                color = colors.label2,
                            )
                        }
                    }
                }
            }
            if (onShowTable != null) {
                Spacer(Modifier.height(OhTheme.space.s1))
                ChartLinkRow(text = tableLabel, onClick = onShowTable)
            }
        }
    }
}

@Composable
private fun ChartLinkRow(text: String, onClick: () -> Unit) {
    val interaction = rememberInteraction()
    val colors = OhTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(OhTheme.radius.sm))
            .ohClickable(interactionSource = interaction, onClick = onClick)
            .padding(vertical = OhTheme.space.s1),
    ) {
        Text(
            text = text,
            style = OhTheme.type.footnote,
            color = colors.accInk,
        )
    }
}

private fun nearestDate(
    xFraction: Float,
    dates: List<LocalDate>,
    minDate: LocalDate,
    span: Double,
): LocalDate? {
    if (dates.isEmpty()) return null
    val target = minDate.toEpochDays() + xFraction * span
    return dates.minByOrNull { abs(it.toEpochDays() - target) }
}

/**
 * The clinical range strip (`DESIGN.md` §8.2.1): four 10 dp stops, never red, the person's
 * value as a `label` tick with "you" above it. The strip is a ruler, not an alarm; the
 * guideline line above it carries the citation.
 */
@Composable
fun OhClinicalRangeStrip(
    scale: ScaleModel,
    value: Double,
    description: String,
    modifier: Modifier = Modifier,
) {
    val colors = OhTheme.colors
    val type = OhTheme.type
    val dims = OhTheme.dims
    val inks = listOf(
        colors.blue.copy(alpha = 0.35f),
        colors.green.copy(alpha = 0.35f),
        colors.yellow.copy(alpha = 0.45f),
        colors.orange.copy(alpha = 0.55f),
    )
    val labelInks = listOf(colors.blueInk, colors.greenInk, colors.goldInk, colors.orangeInk)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(scale.fractionOf(value).coerceAtLeast(0.001f)),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = stringResource(R.string.vitals_range_you),
                    style = type.caption2,
                    color = colors.label2,
                )
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(dims.rangeStripBox)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dims.rangeStripTrack)
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(OhTheme.radius.full)),
            ) {
                scale.bands.forEachIndexed { index, band ->
                    val from = band.from ?: scale.domainLow
                    val to = band.to ?: scale.domainHigh
                    val weight = ((to - from) / (scale.domainHigh - scale.domainLow))
                        .toFloat()
                        .coerceAtLeast(0.001f)
                    Box(
                        Modifier
                            .weight(weight)
                            .fillMaxSize()
                            .background(inks[index % inks.size]),
                    )
                }
            }
            // The value tick: a 3 x 16 dp label mark, never a coloured dot.
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth(scale.fractionOf(value))
                    .height(dims.rangeStripBox),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    Modifier
                        .width(dims.rangeStripTickWidth)
                        .height(dims.rangeStripBox)
                        .background(colors.label),
                )
            }
        }
        Spacer(Modifier.height(OhTheme.space.s0))
        // The four names sit under the ruler with equal columns, so a 200 % label wraps
        // rather than losing a letter; the numbers are the band's own edges.
        Row(modifier = Modifier.fillMaxWidth()) {
            scale.bands.forEachIndexed { index, band ->
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = scaleLabel(band.label),
                        style = type.caption2,
                        color = labelInks[index % labelInks.size],
                        maxLines = 2,
                    )
                    Text(
                        text = bandEdges(index, band, scale),
                        style = type.caption2.tabular(),
                        color = colors.label2,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

@Composable
fun scaleDescription(label: String, value: Double, scale: ScaleModel): String =
    label + " " + number(value, 0) + ". " + scale.bands.joinToString("; ") { band ->
        scaleLabelText(band.label) + " " + bandEdges(
            scale.bands.indexOf(band),
            band,
            scale,
        )
    }

private fun bandEdges(index: Int, band: ScaleBandModel, scale: ScaleModel): String = when {
    index == 0 -> "below ${band.to ?: scale.domainHigh}"
    index == scale.bands.lastIndex -> "above ${band.from ?: scale.domainLow}"
    else -> "${band.from ?: scale.domainLow}-${band.to ?: scale.domainHigh}"
}

private fun scaleLabelText(label: ScaleLabel): String = when (label) {
    ScaleLabel.LOW -> "Low"
    ScaleLabel.NORMAL -> "Normal"
    ScaleLabel.RAISED -> "Raised"
    ScaleLabel.HIGH -> "High"
}

@Composable
private fun scaleLabel(label: ScaleLabel): String = stringResource(
    when (label) {
        ScaleLabel.LOW -> R.string.vitals_scale_low
        ScaleLabel.NORMAL -> R.string.vitals_scale_normal
        ScaleLabel.RAISED -> R.string.vitals_scale_raised
        ScaleLabel.HIGH -> R.string.vitals_scale_high
    },
)

/**
 * The before/after photo slider (`DESIGN.md` §9.4): a 2 dp white handle with a 24 dp grip,
 * date pills on each corner, a 44 dp drag area and slider semantics with arrow support.
 * The first photo is always the older one.
 */
@Composable
fun OhBeforeAfterSlider(model: PhotoCompareModel, modifier: Modifier = Modifier) {
    var fraction by remember(model.beforeId, model.afterId) { mutableStateOf(0.5f) }
    val colors = OhTheme.colors
    val type = OhTheme.type
    val dims = OhTheme.dims
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val label = stringResource(R.string.vitals_photo_compare_label)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(dims.photoCompareMax)
            .semantics {
                contentDescription = label
                // A slider, so TalkBack can move the divider with the arrow actions.
                progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(
                    fraction,
                    0f..1f,
                )
                setProgress { increment ->
                    fraction = (fraction + increment).coerceIn(0f, 1f)
                    true
                }
            }
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        fraction = (fraction - 0.02f).coerceIn(0f, 1f)
                        true
                    }

                    Key.DirectionRight -> {
                        fraction = (fraction + 0.02f).coerceIn(0f, 1f)
                        true
                    }

                    else -> false
                }
            }
            .pointerInput(model.beforeId, model.afterId) {
                detectTapGestures { offset ->
                    fraction = (offset.x / size.width).coerceIn(0f, 1f)
                }
            }
            .pointerInput(model.beforeId, model.afterId) {
                detectDragGestures { change, _ ->
                    fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                }
            },
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            // The newer image is the base; the older is clipped to the left of the handle.
            val afterScale = fillScale(model.after.width, model.after.height, size)
            drawImage(
                image = model.after,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(model.after.width, model.after.height),
                dstOffset = IntOffset(
                    afterScale.offset.x.roundToInt(),
                    afterScale.offset.y.roundToInt(),
                ),
                dstSize = IntSize(
                    afterScale.size.width.roundToInt(),
                    afterScale.size.height.roundToInt(),
                ),
            )
            val beforeScale = fillScale(model.before.width, model.before.height, size)
            val clipWidth = size.width * fraction
            if (clipWidth > 0f) {
                clipRect(right = clipWidth) {
                    drawImage(
                        image = model.before,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(model.before.width, model.before.height),
                        dstOffset = IntOffset(
                            beforeScale.offset.x.roundToInt(),
                            beforeScale.offset.y.roundToInt(),
                        ),
                        dstSize = IntSize(
                            beforeScale.size.width.roundToInt(),
                            beforeScale.size.height.roundToInt(),
                        ),
                    )
                }
            }
            val x = size.width * fraction
            drawLine(
                color = colors.knob,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = dims.photoHandle.toPx(),
            )
            drawCircle(
                color = colors.knob,
                radius = dims.photoGrip.toPx(),
                center = Offset(x, size.height / 2f),
            )
            drawCircle(
                color = colors.scrim,
                radius = dims.photoGripDot.toPx(),
                center = Offset(x - dims.photoGripDotOffset.toPx(), size.height / 2f),
                style = Stroke(width = dims.photoGripStroke.toPx()),
            )
        }
        DatePill(
            text = shortDate(model.beforeDate),
            modifier = Modifier.align(if (rtl) Alignment.TopEnd else Alignment.TopStart)
                .padding(OhTheme.space.s2),
        )
        DatePill(
            text = shortDate(model.afterDate),
            modifier = Modifier.align(if (rtl) Alignment.TopStart else Alignment.TopEnd)
                .padding(OhTheme.space.s2),
        )
        Text(
            text = stringResource(R.string.vitals_photo_range, model.daysApart),
            style = type.caption,
            color = colors.knob,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(OhTheme.radius.full))
                .background(colors.scrim)
                .padding(horizontal = OhTheme.space.s3, vertical = OhTheme.space.s1),
        )
    }
}

@Composable
private fun DatePill(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = OhTheme.type.caption,
        color = OhTheme.colors.knob,
        modifier = modifier
            .clip(RoundedCornerShape(OhTheme.radius.full))
            .background(OhTheme.colors.scrim)
            .padding(horizontal = OhTheme.space.s3, vertical = OhTheme.space.s1),
    )
}

private data class ImageFit(val offset: Offset, val size: Size)

/** Draw an image as large as it can be without distortion, centred in the box. */
private fun fillScale(width: Int, height: Int, box: Size): ImageFit {
    if (width <= 0 || height <= 0) return ImageFit(Offset.Zero, box)
    val scale = minOf(box.width / width, box.height / height)
    val drawnWidth = width * scale
    val drawnHeight = height * scale
    return ImageFit(
        offset = Offset((box.width - drawnWidth) / 2f, (box.height - drawnHeight) / 2f),
        size = Size(drawnWidth, drawnHeight),
    )
}
