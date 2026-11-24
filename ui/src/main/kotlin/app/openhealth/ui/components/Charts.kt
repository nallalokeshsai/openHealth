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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.openhealth.ui.theme.OhTheme
import app.openhealth.ui.theme.OhWeight
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
) {
    val colors = OhTheme.colors
    Box(
        modifier = modifier
            .size(OhTheme.dims.nutritionRing)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = OhTheme.dims.nutritionRingStroke.toPx()
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
) {
    val colors = OhTheme.colors
    val stack = androidx.compose.ui.platform.LocalDensity.current.fontScale >=
        OhTheme.dims.largeFontScale
    Column(modifier = modifier.fillMaxWidth()) {
        if (stack) {
            Text(text = label, style = OhTheme.type.footnote, color = colors.label2)
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
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = label, style = OhTheme.type.footnote, color = colors.label2)
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
        Spacer(Modifier.height(OhTheme.space.s1))
        OhBar(
            progress = progress ?: 0f,
            ink = if (progress == null) colors.label4 else ink,
        )
    }
}

private const val STROKE_HALF = 2f
private const val FULL_CIRCLE = 360f
