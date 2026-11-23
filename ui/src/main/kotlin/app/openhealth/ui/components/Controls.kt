@file:Suppress("MagicNumber", "LongParameterList")

package app.openhealth.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import app.openhealth.ui.icons.OhIcon
import app.openhealth.ui.icons.OhIcons
import app.openhealth.ui.theme.OhAlpha
import app.openhealth.ui.theme.OhTheme
import app.openhealth.ui.theme.OhWeight
import app.openhealth.ui.theme.ltrFigures
import app.openhealth.ui.theme.tabular
import kotlin.math.roundToInt
import kotlinx.coroutines.withTimeoutOrNull

/**
 * §5.10 Segmented control: `surface-3` track, 2 dp padding, a `#636366` thumb in
 * dark (white with a hairline in light), 32 dp segments, up to five.
 */
@Composable
fun OhSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OhTheme.colors
    val type = OhTheme.type
    val dims = OhTheme.dims
    val shape = RoundedCornerShape(OhTheme.radius.md)
    require(options.size <= 5) {
        "A segmented control holds up to five segments (DESIGN.md §5.10)."
    }
    // At the largest text sizes the segments scroll instead of clipping a label.
    val scrollable = LocalDensity.current.fontScale >= dims.largeFontScale
    Row(
        modifier = modifier
            .clip(shape)
            .background(colors.surface3)
            .then(if (scrollable) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
            .padding(dims.segmentPad)
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            val thumbShape = RoundedCornerShape(OhTheme.radius.sm)
            Box(
                modifier = Modifier
                    .then(
                        if (scrollable) Modifier.width(IntrinsicSize.Max) else Modifier.weight(1f),
                    )
                    .heightIn(min = dims.segmentHeight)
                    .clip(thumbShape)
                    .then(
                        if (selected) {
                            Modifier
                                .background(colors.segmentThumb)
                                .border(dims.hairline, colors.sep, thumbShape)
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = OhTheme.space.s2)
                    .selectable(
                        selected = selected,
                        interactionSource = rememberInteraction(),
                        indication = null,
                        role = Role.Button,
                        onClick = { onSelect(index) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option,
                    style = type.subhead.copy(
                        fontWeight = if (selected) OhWeight.semibold else OhWeight.regular,
                    ),
                    color = if (selected) colors.label else colors.label2,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * §5.11 Switch: 51 × 31 dp, `r-full`, on `acc`, off `surface-3`, a white 27 dp knob.
 * A switch takes effect immediately: there is no Save in a row.
 */
@Composable
fun OhSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    val colors = OhTheme.colors
    val dims = OhTheme.dims
    val motion = OhTheme.motion
    val interaction = rememberInteraction()
    val pressed by interaction.collectIsPressedAsState()
    val trackColor by animateColorAsState(
        targetValue = if (checked) colors.acc else colors.surface3,
        animationSpec = tween(motion.fastDuration(), easing = motion.ease),
        label = "switchTrack",
    )
    val travel = dims.switchWidth - dims.switchKnob - OhTheme.space.s0 * 2f
    val knobOffset by animateDpAsState(
        targetValue = if (checked) travel else OhTheme.space.s0,
        animationSpec = tween(motion.fastDuration(), easing = motion.ease),
        label = "switchKnob",
    )
    val knobWidth by animateDpAsState(
        targetValue = if (pressed) dims.switchKnob + OhTheme.space.s1 else dims.switchKnob,
        animationSpec = tween(motion.fastDuration(), easing = motion.ease),
        label = "switchStretch",
    )
    // The knob's hairline ring so it never vanishes on a very light accent (§2.6).
    val ring = if (colors.dark) colors.knobRing else Color.Transparent
    Box(
        modifier = modifier
            .width(dims.switchWidth)
            .height(dims.touchTarget)
            .alpha(if (enabled) OhAlpha.full else OhAlpha.disabled)
            .toggleable(
                value = checked,
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .semantics {
                if (contentDescription != null) this.contentDescription = contentDescription
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(dims.switchWidth)
                .height(dims.switchHeight)
                .clip(CircleShape)
                .background(trackColor),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = OhTheme.space.s0, start = knobOffset)
                    .width(knobWidth)
                    .height(dims.switchKnob)
                    .clip(CircleShape)
                    .background(colors.knob)
                    .border(dims.hairline, ring, CircleShape),
            )
        }
    }
}

/**
 * §5.12 Stepper: a number field between − and +, 44 dp tall, `surface-2` cells,
 * tabular numerals, the unit as a 13 sp suffix. Press and hold accelerates.
 */
@Composable
fun OhStepper(
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    unit: String? = null,
    enabled: Boolean = true,
) {
    val colors = OhTheme.colors
    val type = OhTheme.type
    val dims = OhTheme.dims
    val shape = RoundedCornerShape(OhTheme.radius.base)
    Row(
        modifier = modifier
            .clip(shape)
            .background(colors.surface2)
            .alpha(if (enabled) OhAlpha.full else OhAlpha.disabled),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperCell(
            icon = OhIcons.remove,
            description = "Decrease",
            enabled = enabled,
            onStep = onDecrement,
        )
        Row(
            modifier = Modifier
                .heightIn(min = dims.stepperHeight)
                .padding(horizontal = OhTheme.space.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = value,
                style = type.body.copy(fontWeight = OhWeight.semibold).tabular().ltrFigures(),
                color = colors.label,
                maxLines = 1,
            )
            if (unit != null) {
                Spacer(Modifier.width(OhTheme.space.s0))
                Text(text = unit, style = type.footnote, color = colors.label2, maxLines = 1)
            }
        }
        StepperCell(
            icon = OhIcons.add,
            description = "Increase",
            enabled = enabled,
            onStep = onIncrement,
        )
    }
}

@Composable
private fun StepperCell(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onStep: () -> Unit,
) {
    val colors = OhTheme.colors
    val dims = OhTheme.dims
    val motion = OhTheme.motion
    val currentStep by rememberUpdatedState(onStep)
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) motion.pressScaleSmall else OhAlpha.full,
        animationSpec = tween(motion.fastDuration(), easing = motion.ease),
        label = "stepperPress",
    )
    Box(
        modifier = Modifier
            .width(dims.stepperHeight)
            .heightIn(min = dims.stepperHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .semantics {
                contentDescription = description
                role = Role.Button
                onClick {
                    currentStep()
                    true
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    currentStep()
                    var interval = 400L
                    while (true) {
                        val released = withTimeoutOrNull(interval) {
                            var event = awaitPointerEvent()
                            while (event.changes.all { !it.pressed }) {
                                return@withTimeoutOrNull true
                            }
                            false
                        }
                        if (released == true) break
                        currentStep()
                        interval = (interval * 3 / 4).coerceAtLeast(60L)
                    }
                    pressed = false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        OhIcon(
            icon = icon,
            contentDescription = null,
            size = dims.iconButtonGlyph,
            tint = colors.label,
        )
    }
}

/**
 * §5.13 Slider: a 4 dp `surface-3` track, accent fill, a 24 dp white knob with an
 * accent ring, and a value bubble while dragging. Never used for a clinical value.
 */
@Composable
fun OhSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    val colors = OhTheme.colors
    val type = OhTheme.type
    val dims = OhTheme.dims
    var dragging by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val knobPx = with(density) { dims.sliderKnob.toPx() }
    val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start))
        .coerceIn(0f, 1f)
    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.height(dims.touchTarget).fillMaxWidth()) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dims.sliderTrack)
                    .align(Alignment.Center)
                    .alpha(if (enabled) OhAlpha.full else OhAlpha.disabled)
                    .pointerInput(enabled, valueRange) {
                        if (!enabled) return@pointerInput
                        val usable = (size.width - knobPx).coerceAtLeast(1f)
                        val emit: (Float) -> Unit = { x ->
                            val f = ((x - knobPx / 2f) / usable).coerceIn(0f, 1f)
                            val raw =
                                valueRange.start + f * (valueRange.endInclusive - valueRange.start)
                            onValueChange(
                                if (valueRange.endInclusive <= 1f) {
                                    (raw * 100f).roundToInt() / 100f
                                } else {
                                    raw
                                },
                            )
                        }
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            dragging = true
                            emit(down.position.x)
                            var pressed = true
                            while (pressed) {
                                val change = awaitPointerEvent().changes.firstOrNull()
                                pressed = change != null && change.pressed
                                if (pressed) emit(change!!.position.x)
                            }
                            dragging = false
                        }
                    }
                    .semantics {
                        progressBarRangeInfo = ProgressBarRangeInfo(value, valueRange)
                        if (contentDescription != null) this.contentDescription = contentDescription
                        setProgress { target ->
                            onValueChange(
                                target.coerceIn(valueRange.start, valueRange.endInclusive),
                            )
                            true
                        }
                    },
            ) {
                val trackY = size.height / 2f
                val knobX = knobPx / 2f + (size.width - knobPx) * fraction
                drawLine(
                    color = colors.surface3,
                    start = Offset(0f, trackY),
                    end = Offset(size.width, trackY),
                    strokeWidth = size.height,
                )
                drawLine(
                    color = colors.acc,
                    start = Offset(0f, trackY),
                    end = Offset(size.width * fraction, trackY),
                    strokeWidth = size.height,
                )
                drawCircle(
                    color = colors.knob,
                    radius = knobPx / 2f,
                    center = Offset(knobX, trackY),
                )
                drawCircle(
                    color = colors.acc,
                    radius = knobPx / 2f - dims.hairline.toPx(),
                    center = Offset(knobX, trackY),
                    style = Stroke(width = dims.hairline.toPx() * 4),
                )
            }
            if (dragging) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .clip(RoundedCornerShape(OhTheme.radius.sm))
                        .background(colors.surfaceRaised)
                        .padding(horizontal = OhTheme.space.s2, vertical = OhTheme.space.s0),
                ) {
                    Text(
                        text = formatSlider(value),
                        style = type.caption.copy(
                            fontWeight = OhWeight.semibold,
                        ).tabular().ltrFigures(),
                        color = colors.label,
                    )
                }
            }
        }
    }
}

private fun formatSlider(value: Float): String {
    val rounded = (value * 100f).roundToInt() / 100f
    return if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString()
}

/**
 * §5.14 Text and search field: `surface-2`, radius 10, 46 dp, 17 sp; focus is a
 * 2.5 dp `acc-ink` ring outside the control, never a glow.
 */
@Composable
fun OhTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    singleLine: Boolean = true,
    error: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    contentDescription: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = OhTheme.colors
    val type = OhTheme.type
    val dims = OhTheme.dims
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val radius = OhTheme.radius.md
    val shape = RoundedCornerShape(radius)
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .ohFocusRing(focused, radius)
                .heightIn(min = dims.fieldHeight)
                .clip(shape)
                .background(colors.surface2)
                .border(
                    width = if (error != null) dims.hairline * 2 else dims.hairline,
                    color = if (error != null) colors.red else Color.Transparent,
                    shape = shape,
                )
                .padding(horizontal = dims.fieldPadH)
                .alpha(if (enabled) OhAlpha.full else OhAlpha.disabled),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = singleLine,
                textStyle = type.body.copy(color = colors.label),
                cursorBrush = SolidColor(colors.accInk),
                keyboardOptions = keyboardOptions,
                visualTransformation = visualTransformation,
                interactionSource = interaction,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        if (contentDescription != null) this.contentDescription = contentDescription
                    },
                decorationBox = { inner ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (leading != null) {
                            leading()
                            Spacer(Modifier.width(OhTheme.space.s2))
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            if (value.isEmpty()) {
                                Text(
                                    text = placeholder,
                                    style = type.body,
                                    color = colors.label3,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            inner()
                        }
                        if (trailing != null) {
                            Spacer(Modifier.width(OhTheme.space.s2))
                            trailing()
                        }
                    }
                },
            )
        }
        if (error != null) {
            Spacer(Modifier.height(OhTheme.space.s1))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OhIcon(
                    icon = OhIcons.warning,
                    contentDescription = null,
                    size = OhTheme.space.s6,
                    tint = colors.redInk,
                )
                Spacer(Modifier.width(OhTheme.space.s1))
                Text(
                    text = error,
                    style = type.footnote,
                    color = colors.redInk,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

/**
 * §5.14 Search field: an 18 dp magnifier, a 22 dp clear button and an optional
 * trailing count badge. It never grows a border.
 */
@Composable
fun OhSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    count: Int? = null,
    enabled: Boolean = true,
) {
    val colors = OhTheme.colors
    val type = OhTheme.type
    val dims = OhTheme.dims
    OhTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = placeholder,
        enabled = enabled,
        singleLine = true,
        contentDescription = placeholder,
        leading = {
            OhIcon(
                icon = OhIcons.search,
                contentDescription = null,
                size = dims.searchGlyph,
                tint = colors.label2,
            )
        },
        trailing = {
            if (value.isNotEmpty()) {
                val clearInteraction = rememberInteraction()
                Box(
                    modifier = Modifier
                        .size(dims.touchTarget)
                        .ohClickable(clearInteraction, role = Role.Button) { onValueChange("") }
                        .semantics { contentDescription = "Clear search" },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(dims.clearGlyph)
                            .clip(CircleShape)
                            .background(colors.surface3),
                        contentAlignment = Alignment.Center,
                    ) {
                        OhIcon(
                            icon = OhIcons.close,
                            contentDescription = null,
                            size = dims.iconButtonGlyph - OhTheme.space.s0,
                            tint = colors.label2,
                        )
                    }
                }
            } else if (count != null) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.surface3)
                        .padding(horizontal = OhTheme.space.s2, vertical = OhTheme.space.s0),
                ) {
                    Text(text = count.toString(), style = type.caption, color = colors.label2)
                }
            }
        },
    )
}
