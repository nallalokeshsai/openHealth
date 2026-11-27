package app.openhealth.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import app.openhealth.ui.theme.OhTheme
import app.openhealth.ui.theme.OhWeight
import app.openhealth.ui.theme.ltrFigures
import app.openhealth.ui.theme.tabular

/**
 * A ring chart (`DESIGN.md` §8.2): an 8 dp `surface-3` track with a round-capped accent
 * arc and a 28/600 figure in the centre. Calories and steps are its two uses.
 */
@Composable
fun OhRing(
    progress: Float,
    centre: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    /** §14.2: 120 dp for energy, 96 dp for steps; the stroke is 8 dp in both. */
    diameter: Dp? = null,
    strokeWidth: Dp? = null,
) {
    val colors = OhTheme.colors
    val ringSize = diameter ?: OhTheme.dims.nutritionRing
    val strokeDp = strokeWidth ?: OhTheme.dims.nutritionRingStroke
    Box(
        modifier = modifier
            .size(ringSize)
            .semantics(mergeDescendants = true) { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeDp.toPx()
            val inset = stroke / STROKE_HALF
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = colors.surface3,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke),
            )
            val sweep = (progress.coerceIn(0f, 1f)) * FULL_CIRCLE
            if (sweep > 0f) {
                drawArc(
                    color = colors.acc,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(
                        width = stroke,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    ),
                )
            }
        }
        Text(
            text = centre,
            style = OhTheme.type.display.copy(fontWeight = OhWeight.semibold).tabular(),
            color = colors.label,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/**
 * A ring split into the shares of its [segments] (`BUILD-ROADMAP.md` §B9 "macro ring"). The
 * shares are drawn in order from the top with a small gap, each in its own hue; the
 * [centre] text and the table alternative carry the numbers, so colour is never alone
 * (`DESIGN.md` §8.4).
 */
@Composable
fun OhMacroRing(
    segments: List<Pair<Float, Color>>,
    centre: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val colors = OhTheme.colors
    val ringSize = OhTheme.dims.nutritionRing
    val strokeDp = OhTheme.dims.nutritionRingStroke
    Box(
        modifier = modifier
            .size(ringSize)
            .semantics(mergeDescendants = true) { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeDp.toPx()
            val inset = stroke / STROKE_HALF
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = colors.surface3,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke),
            )
            val total = segments.sumOf { it.first.toDouble() }.toFloat()
            if (total > 0f) {
                var start = RING_START_DEGREES
                segments.forEach { (share, ink) ->
                    val sweep = share / total * FULL_CIRCLE
                    val drawn = (sweep - MACRO_GAP_DEGREES).coerceAtLeast(0f)
                    if (drawn > 0f) {
                        drawArc(
                            color = ink,
                            startAngle = start + MACRO_GAP_DEGREES / STROKE_HALF,
                            sweepAngle = drawn,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(width = stroke),
                        )
                    }
                    start += sweep
                }
            }
        }
        Text(
            text = centre,
            style = OhTheme.type.title2.copy(fontWeight = OhWeight.semibold).tabular(),
            color = colors.label,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/**
 * One horizontal bar with its value (`DESIGN.md` §8.2): an 8 dp track and a rounded fill,
 * named in words so the chart survives colour blindness and grayscale (§8.4).
 */
@Composable
fun OhBar(progress: Float, ink: Color, modifier: Modifier = Modifier) {
    val colors = OhTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(OhTheme.dims.macroBarHeight)
            .clip(RoundedCornerShape(OhTheme.radius.full))
            .background(colors.surface3),
    ) {
        val fraction = progress.coerceIn(0f, 1f)
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(OhTheme.radius.full))
                    .background(ink),
            )
        }
    }
}

/** A label, a value, an optional target and a bar; the macro bar's whole row. */
@Composable
fun OhBarRow(
    label: String,
    value: String,
    target: String?,
    progress: Float?,
    ink: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colors = OhTheme.colors
    val stack = androidx.compose.ui.platform.LocalDensity.current.fontScale >=
        OhTheme.dims.largeFontScale
    val interaction = rememberInteraction()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier.ohClickable(interactionSource = interaction, onClick = onClick)
                },
            ),
    ) {
        if (stack) {
            Text(text = label, style = OhTheme.type.footnote, color = colors.label2)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = value,
                        style = OhTheme.type.footnote.tabular(),
                        color = colors.label,
                    )
                    if (target != null) {
                        Spacer(Modifier.width(OhTheme.space.s1))
                        Text(
                            text = target,
                            style = OhTheme.type.footnote.tabular().ltrFigures(),
                            color = colors.label2,
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = label, style = OhTheme.type.footnote, color = colors.label2)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = value,
                            style = OhTheme.type.footnote.tabular(),
                            color = colors.label,
                        )
                        if (target != null) {
                            Spacer(Modifier.width(OhTheme.space.s1))
                            Text(
                                text = target,
                                style = OhTheme.type.footnote.tabular(),
                                color = colors.label3,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(OhTheme.space.s1))
        OhBar(
            progress = progress ?: 0f,
            ink = if (progress == null) colors.label4 else ink,
        )
    }
}

private const val STROKE_HALF = 2f
private const val MACRO_GAP_DEGREES = 4f
private const val RING_START_DEGREES = -90f
private const val FULL_CIRCLE = 360f
