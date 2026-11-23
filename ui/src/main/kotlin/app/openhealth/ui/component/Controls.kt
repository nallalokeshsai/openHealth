@file:Suppress("MagicNumber", "LongParameterList", "LongMethod", "CyclomaticComplexMethod")

package app.openhealth.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import app.openhealth.ui.R
import app.openhealth.ui.icon.OhIcon
import app.openhealth.ui.icon.OhIconName
import app.openhealth.ui.theme.OhTheme
import app.openhealth.ui.theme.mediumWeight
import app.openhealth.ui.theme.semiboldWeight
import kotlinx.coroutines.delay

/** The six button variants of DESIGN.md §5.7. There is one primary action per sheet. */
enum class OhButtonVariant { Primary, Secondary, Tinted, Danger, Ghost, Quiet }

/** `Medium` is 50 dp and full width; `.sm` is 35 dp and `.xs` 28 dp, both content-sized (§5.7). */
enum class OhButtonSize { Medium, Small, ExtraSmall }

@Composable
fun OhButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: OhButtonVariant = OhButtonVariant.Primary,
    size: OhButtonSize = OhButtonSize.Medium,
    enabled: Boolean = true
) {
    val colors = OhTheme.colors
    val type = OhTheme.type
    val geometry = OhTheme.geometry
    val motion = OhTheme.motion
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) motion.pressControl else 1f,
        animationSpec = tween(motion.fast, easing = motion.ease),
        label = "button-scale"
    )
    val alpha by animateFloatAsState(
        targetValue = when {
            !enabled -> geometry.buttonDisabledAlpha
            pressed -> geometry.buttonPressedAlpha
            else -> 1f
        },
        animationSpec = tween(motion.fast, easing = motion.ease),
        label = "button-alpha"
    )
    val fill = when (variant) {
        OhButtonVariant.Primary -> colors.acc
        OhButtonVariant.Secondary -> colors.surface2
        OhButtonVariant.Tinted -> colors.accSoft
        OhButtonVariant.Danger -> colors.red.copy(alpha = 0.15f)
        OhButtonVariant.Ghost, OhButtonVariant.Quiet -> Color.Transparent
    }
    val textColor = when (variant) {
        OhButtonVariant.Primary -> colors.onAcc
        OhButtonVariant.Secondary -> colors.label
        OhButtonVariant.Tinted, OhButtonVariant.Ghost -> colors.accInk
        OhButtonVariant.Danger -> colors.redInk
        OhButtonVariant.Quiet -> colors.label2
    }
    val height = when (size) {
        OhButtonSize.Medium -> geometry.buttonHeight
        OhButtonSize.Small -> geometry.buttonHeightSmall
        OhButtonSize.ExtraSmall -> geometry.buttonHeightExtraSmall
    }
    val paddingVertical = when (size) {
        OhButtonSize.Medium -> geometry.buttonPaddingVertical
        OhButtonSize.Small -> geometry.buttonSmallPaddingVertical
        OhButtonSize.ExtraSmall -> OhTheme.space.space1
    }
    val paddingHorizontal = when (size) {
        OhButtonSize.Medium -> geometry.buttonPaddingHorizontal
        OhButtonSize.Small -> geometry.buttonSmallPaddingHorizontal
        OhButtonSize.ExtraSmall -> OhTheme.space.space3
    }
    val shape = RoundedCornerShape(
        if (size == OhButtonSize.Medium) OhTheme.radius.base else OhTheme.radius.sm
    )
    val textStyle = when (size) {
        OhButtonSize.Medium -> type.body.semiboldWeight()
        OhButtonSize.Small -> type.subhead.semiboldWeight()
        OhButtonSize.ExtraSmall -> type.footnote.semiboldWeight()
    }

    Box(
        modifier = modifier
            .then(if (size == OhButtonSize.Medium) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = height)
            .minimumInteractiveComponentSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .clip(shape)
            .background(fill)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = paddingHorizontal, vertical = paddingVertical),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = textStyle,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** DESIGN.md §5.8: a 36 dp circle inside a 44 dp target, pressed to `.92`, never a ripple. */
@Composable
fun OhIconButton(
    icon: OhIconName,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    on: Boolean = false,
    enabled: Boolean = true,
    tint: Color? = null
) {
    val colors = OhTheme.colors
    val geometry = OhTheme.geometry
    val motion = OhTheme.motion
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) motion.pressSmall else 1f,
        animationSpec = tween(motion.fast, easing = motion.ease),
        label = "icon-button-scale"
    )
    Box(
        modifier = modifier
            .size(OhTheme.space.touchTarget)
            .clip(RoundedCornerShape(OhTheme.radius.full))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(geometry.iconButton)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(OhTheme.radius.full))
                .background(if (on) colors.accSoft else colors.surface),
            contentAlignment = Alignment.Center
        ) {
            OhIcon(
                name = icon,
                contentDescription = contentDescription,
                size = geometry.iconButtonGlyph,
                tint = tint ?: if (on) colors.accInk else colors.label
            )
        }
    }
}

/** DESIGN.md §5.9: a filter or a choice, never a status. */
@Composable
fun OhChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    neutralSelected: Boolean = false,
    enabled: Boolean = true
) {
    val colors = OhTheme.colors
    val type = OhTheme.type
    val geometry = OhTheme.geometry
    val motion = OhTheme.motion
    val interaction = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(
        targetValue = if (interaction.collectIsPressedAsState().value && enabled) motion.pressControl else 1f,
        animationSpec = tween(motion.fast, easing = motion.ease),
        label = "chip-scale"
    )
    val fill = when {
        !selected -> colors.surface
        neutralSelected -> colors.surface3
        else -> colors.acc
    }
    val textColor = when {
        !selected -> colors.label
        neutralSelected -> colors.label
        else -> colors.onAcc
    }
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .heightIn(min = geometry.chipHeight)
            .minimumInteractiveComponentSize()
            .alpha(if (enabled) 1f else geometry.buttonDisabledAlpha)
            .clip(RoundedCornerShape(OhTheme.radius.full))
            .background(fill)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Checkbox,
                onClick = onClick
            )
            .semantics { this.selected = selected }
            .padding(
                horizontal = geometry.chipPaddingHorizontal,
                vertical = geometry.chipPaddingVertical
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = if (selected) type.chip.mediumWeight() else type.chip,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** DESIGN.md §5.10: up to five segments; more becomes a chip strip or a dropdown sheet. */
@Composable
fun OhSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    require(options.size in 1..OhTheme.geometry.segmentMaxCount) {
        "A segmented control holds 1 to ${OhTheme.geometry.segmentMaxCount} segments (DESIGN.md §5.10)"
    }
    val colors = OhTheme.colors
    val type = OhTheme.type
    val geometry = OhTheme.geometry
    require(options.size in 1..geometry.segmentMaxCount) {
        "A segmented control holds 1 to ${geometry.segmentMaxCount} segments (DESIGN.md §5.10)"
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(OhTheme.radius.md))
            .background(colors.surface3)
            .padding(geometry.segmentPadding)
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = geometry.segmentHeight)
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(OhTheme.radius.md))
                    .background(if (selected) colors.segmentThumb else Color.Transparent)
                    .then(
                        if (selected) {
                            Modifier.border(
                                width = OhTheme.space.hairline,
                                color = colors.raisedLine,
                                shape = RoundedCornerShape(OhTheme.radius.md)
                            )
                        } else {
                            Modifier
                        }
                    )
                    .clickable(
                        interactionSource = remember {
                            MutableInteractionSource()
                        },
                        indication = null,
                        role = Role.Tab
                    ) { onSelect(index) }
                    .semantics { this.selected = selected },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option,
                    style = if (selected) type.subhead.semiboldWeight() else type.subhead,
                    color = if (selected) colors.label else colors.label2,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** DESIGN.md §5.11: 51 × 31 dp, immediate effect, knob stretches before it snaps. */
@Composable
fun OhSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = OhTheme.colors
    val geometry = OhTheme.geometry
    val motion = OhTheme.motion
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val onLabel = stringResource(R.string.oh_on)
    val offLabel = stringResource(R.string.oh_off)
    val track by animateColorAsState(
        targetValue = if (checked) colors.acc else colors.surface3,
        animationSpec = tween(motion.fast),
        label = "switch-track"
    )
    val knobWidth by animateDpAsState(
        targetValue = if (pressed) geometry.switchKnob + OhTheme.space.space2 else geometry.switchKnob,
        animationSpec = tween(motion.fast, easing = motion.ease),
        label = "switch-knob-width"
    )
    Box(
        modifier = modifier
            .width(geometry.switchWidth)
            .height(OhTheme.space.touchTarget)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onClick = { onCheckedChange(!checked) }
            )
            .semantics {
                stateDescription = if (checked) onLabel else offLabel
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(geometry.switchWidth)
                .height(geometry.switchHeight)
                .clip(RoundedCornerShape(OhTheme.radius.full))
                .background(track)
        ) {
            // Alignment, not a physical offset, keeps the knob on the inline end in RTL.
            Box(
                modifier = Modifier
                    .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = OhTheme.space.space1)
                    .width(knobWidth)
                    .height(geometry.switchKnob)
                    .clip(RoundedCornerShape(OhTheme.radius.full))
                    .background(colors.switchKnob)
                    .border(
                        width = OhTheme.space.hairline,
                        color = colors.switchKnobRing,
                        shape = RoundedCornerShape(OhTheme.radius.full)
                    )
            )
        }
    }
}

/** DESIGN.md §5.12: a number between − and +, 44 dp, tabular, long-press accelerates. */
@Composable
fun OhStepper(
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    unit: String? = null,
    enabled: Boolean = true,
    onValueClick: (() -> Unit)? = null,
    contentDescription: String? = null
) {
    val colors = OhTheme.colors
    val type = OhTheme.type
    val geometry = OhTheme.geometry
    Row(
        modifier = modifier
            .heightIn(min = geometry.stepperHeight)
            .clip(RoundedCornerShape(OhTheme.radius.base))
            .background(colors.surface2)
            .alpha(if (enabled) 1f else geometry.buttonDisabledAlpha),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepButton(
            icon = OhIconName.Minus,
            description = stringResource(R.string.oh_decrease),
            enabled = enabled,
            onStep = onDecrement
        )
        Row(
            modifier = Modifier
                .heightIn(min = geometry.stepperHeight)
                .then(
                    if (onValueClick != null) {
                        Modifier.clickable(
                            interactionSource = remember {
                                MutableInteractionSource()
                            },
                            indication = null,
                            role = Role.Button,
                            onClick = onValueClick
                        )
                    } else {
                        Modifier
                    }
                )
                .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = value,
                style = type.body,
                color = colors.label,
                maxLines = 1
            )
            if (unit != null) {
                Spacer(Modifier.width(OhTheme.space.space0))
                Text(text = unit, style = type.footnote, color = colors.label2, maxLines = 1)
            }
        }
        StepButton(
            icon = OhIconName.Add,
            description = stringResource(R.string.oh_increase),
            enabled = enabled,
            onStep = onIncrement
        )
    }
}

@Composable
private fun StepButton(icon: OhIconName, description: String, enabled: Boolean, onStep: () -> Unit) {
    val colors = OhTheme.colors
    val geometry = OhTheme.geometry
    var pressed by remember { mutableStateOf(false) }
    var repeated by remember { mutableStateOf(false) }
    LaunchedEffect(pressed) {
        if (pressed) {
            delay(400)
            repeated = true
            while (pressed) {
                onStep()
                delay(90)
            }
        }
    }
    Box(
        modifier = Modifier
            .width(geometry.stepperCellWidth)
            .heightIn(min = geometry.stepperHeight)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                        if (!repeated) onStep()
                        repeated = false
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        OhIcon(
            name = icon,
            contentDescription = description,
            size = geometry.iconButtonGlyph,
            tint = if (enabled) colors.label else colors.label3
        )
    }
}

/** DESIGN.md §5.13: 4 dp track, 24 dp white knob with an accent ring, bubble while dragging. */
@Composable
fun OhSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true,
    contentDescription: String? = null,
    valueLabel: String? = null
) {
    val colors = OhTheme.colors
    val type = OhTheme.type
    val geometry = OhTheme.geometry
    var dragging by remember { mutableStateOf(false) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    val visualFraction = if (rtl) 1f - fraction else fraction

    fun update(trackFraction: Float) {
        val clamped = trackFraction.coerceIn(0f, 1f)
        val logical = if (rtl) 1f - clamped else clamped
        onValueChange(valueRange.start + logical * span)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(OhTheme.space.touchTarget)
            .semantics {
                if (contentDescription != null) this.contentDescription = contentDescription
                progressBarRangeInfo = ProgressBarRangeInfo(value, valueRange)
                setProgress { target ->
                    onValueChange(target.coerceIn(valueRange.start, valueRange.endInclusive))
                    true
                }
            }
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset -> update(offset.x / size.width.toFloat()) }
            }
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragging = true
                        update(it.x / size.width.toFloat())
                    },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false }
                ) { change, _ ->
                    update(change.position.x / size.width.toFloat())
                }
            }
    ) {
        val usable = (maxWidth - geometry.sliderKnob).coerceAtLeast(androidx.compose.ui.unit.Dp.Hairline)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(geometry.sliderTrack)
                .align(Alignment.CenterStart)
        ) {
            drawRoundRect(
                color = colors.surface3,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
            )
            drawRoundRect(
                color = if (enabled) colors.acc else colors.acc.copy(alpha = geometry.buttonDisabledAlpha),
                topLeft = androidx.compose.ui.geometry.Offset(size.width * (1f - visualFraction), 0f),
                size = androidx.compose.ui.geometry.Size(size.width * visualFraction, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
            )
        }
        Box(
            modifier = Modifier
                .offset(x = usable * visualFraction)
                .size(geometry.sliderKnob)
                .align(Alignment.CenterStart)
                .clip(RoundedCornerShape(OhTheme.radius.full))
                .background(colors.switchKnob)
                .border(
                    width = geometry.sliderRing,
                    color = if (enabled) colors.acc else colors.label3,
                    shape = RoundedCornerShape(OhTheme.radius.full)
                )
        )
        if (dragging && valueLabel != null) {
            Text(
                text = valueLabel,
                style = type.caption.semiboldWeight(),
                color = colors.label,
                modifier = Modifier
                    .offset(x = (usable * visualFraction).coerceAtMost(maxWidth - geometry.sliderKnob))
                    .clip(RoundedCornerShape(OhTheme.radius.sm))
                    .background(colors.surfaceRaised)
                    .padding(horizontal = OhTheme.space.space1, vertical = OhTheme.space.space0),
                textAlign = TextAlign.Center
            )
        }
    }
}
