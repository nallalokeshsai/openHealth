package app.openhealth.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import app.openhealth.ui.theme.OhTheme

/**
 * Press feedback per DESIGN.md §6.1: it starts on touch-down, never on release, it is a transform
 * only, and it is removed under reduced motion. There is no ripple anywhere (§5.1–5.3).
 *
 * A press scales the control; rows fill instead, so they do not use this.
 */
@Composable
fun Modifier.pressable(
    interactionSource: MutableInteractionSource,
    scale: Float,
    enabled: Boolean = true,
    role: Role? = null,
    onClick: () -> Unit
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val motion = OhTheme.motion
    val animatedScale by animateFloatAsState(
        targetValue = if (pressed && enabled) scale else 1f,
        animationSpec = tween(durationMillis = motion.fast, easing = motion.ease),
        label = "press-scale"
    )
    return this
        .graphicsLayer {
            scaleX = animatedScale
            scaleY = animatedScale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = role,
            onClick = onClick
        )
}

/** §2.4: a 0.5 dp hairline drawn as a sub-pixel line, never a 1 dp divider. */
fun Modifier.topHairline(color: Color, widthPx: Float, insetStartPx: Float = 0f): Modifier = drawBehind {
    drawLine(
        color = color,
        start = Offset(insetStartPx, 0f),
        end = Offset(size.width, 0f),
        strokeWidth = widthPx
    )
}

/** The hairline under a row, inset past the row's icon rail when it has one (§5.3). */
fun Modifier.bottomHairline(color: Color, widthPx: Float, insetStartPx: Float = 0f): Modifier = drawBehind {
    drawLine(
        color = color,
        start = Offset(insetStartPx, size.height),
        end = Offset(size.width, size.height),
        strokeWidth = widthPx
    )
}
