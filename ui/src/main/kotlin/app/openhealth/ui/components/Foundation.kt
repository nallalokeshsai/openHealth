package app.openhealth.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import app.openhealth.ui.theme.OhTheme

/**
 * Press feedback of `DESIGN.md` §6.1: it starts on touch-down, never on release, and
 * is a transform only. Rows fill instead and never scale, so they use [ohPressFill].
 */
@Composable
fun Modifier.ohPressScale(interactionSource: MutableInteractionSource, scale: Float): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val motion = OhTheme.motion
    val scaleState by animateFloatAsState(
        targetValue = if (pressed) scale else 1f,
        animationSpec = tween(durationMillis = motion.fastDuration(), easing = motion.ease),
        label = "pressScale",
    )
    return graphicsLayer {
        scaleX = scaleState
        scaleY = scaleState
    }
}

/**
 * A ripple-less pressable control. No ripple, no long-press highlight, no grey
 * overlay (`DESIGN.md` §6.1).
 */
@Composable
fun Modifier.ohClickable(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    role: Role? = null,
    onClick: () -> Unit,
): Modifier = clickable(
    interactionSource = interactionSource,
    indication = null,
    enabled = enabled,
    role = role,
    onClick = onClick,
)

/** A row's press response: its fill becomes `surface-2`, it does not move (§6.1). */
@Composable
fun Modifier.ohPressFill(interactionSource: MutableInteractionSource, shape: Shape): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val fill = if (pressed) OhTheme.colors.surface2 else Color.Transparent
    return background(fill, shape)
}

/**
 * The 0.5 dp separator of `DESIGN.md` §2.4, §4.3, drawn as a sub-pixel line, inset
 * from the start of the row. It exists only **between** rows.
 */
@Composable
fun Modifier.ohHairline(
    color: Color,
    insetStart: Dp = OhTheme.dims.rowHairInset,
    insetEnd: Dp = OhTheme.space.none,
    atBottom: Boolean = true,
): Modifier {
    val hairlinePx = with(LocalDensity.current) { OhTheme.dims.hairline.toPx() }
    val startPx = with(LocalDensity.current) { insetStart.toPx() }
    val endPx = with(LocalDensity.current) { insetEnd.toPx() }
    return drawBehind {
        val y = if (atBottom) size.height - hairlinePx / 2f else hairlinePx / 2f
        drawLine(
            color = color,
            start = Offset(startPx, y),
            end = Offset(size.width - endPx, y),
            strokeWidth = hairlinePx,
        )
    }
}

/**
 * The 2.5 dp focus ring of `DESIGN.md` §5.14, §11.6: `acc-ink`, 2 dp outside the
 * control. It is never removed without a replacement.
 */
@Composable
fun Modifier.ohFocusRing(focused: Boolean, radius: Dp): Modifier {
    if (!focused) return this
    val dims = OhTheme.dims
    val ring = dims.focusRing
    val offset = dims.focusOffset
    val ringPx = with(LocalDensity.current) { ring.toPx() }
    val offsetPx = with(LocalDensity.current) { offset.toPx() }
    val radiusPx = with(LocalDensity.current) { radius.toPx() }
    val ink = OhTheme.colors.accInk
    return drawBehind {
        drawRoundRect(
            color = ink,
            topLeft = Offset(-offsetPx, -offsetPx),
            size = Size(size.width + offsetPx * 2, size.height + offsetPx * 2),
            cornerRadius = CornerRadius(radiusPx + offsetPx),
            style = Stroke(width = ringPx),
        )
    }
}

/** Clips to [shape] and adds the row-style pressed fill. */
@Composable
fun Modifier.ohSurfacePressable(
    interactionSource: MutableInteractionSource,
    shape: Shape,
    enabled: Boolean,
    onClick: () -> Unit,
): Modifier = ohClickable(interactionSource, enabled = enabled, onClick = onClick)
    .ohPressFill(interactionSource, shape)
    .clip(shape)

/** Remembers an interaction source for a control that only needs press feedback. */
@Composable
fun rememberInteraction(): MutableInteractionSource = remember { MutableInteractionSource() }
