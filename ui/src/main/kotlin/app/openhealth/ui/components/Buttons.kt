package app.openhealth.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.openhealth.ui.icons.OhIcon
import app.openhealth.ui.theme.OhAlpha
import app.openhealth.ui.theme.OhTheme
import app.openhealth.ui.theme.OhWeight

/** The six button variants of `DESIGN.md` §5.7. */
enum class OhButtonVariant { Primary, Secondary, Tinted, Danger, Ghost, Quiet }

/** Default 50 dp, `.sm` 35 dp, `.xs` 28 dp (`DESIGN.md` §5.7). */
enum class OhButtonSize { Default, Small, ExtraSmall }

/**
 * §5.7 Button. One primary per sheet; a button's label is a verb and an object.
 * It presses by scale and opacity, never by darkening the fill behind its text.
 */
@Composable
fun OhButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: OhButtonVariant = OhButtonVariant.Primary,
    size: OhButtonSize = OhButtonSize.Default,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val colors = OhTheme.colors
    val type = OhTheme.type
    val dims = OhTheme.dims
    val interaction = rememberInteraction()
    val pressed by interaction.collectIsPressedAsState()
    val motion = OhTheme.motion
    val pressedOpacity by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) OhAlpha.pressed else OhAlpha.full,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = motion.fastDuration(),
            easing = motion.ease,
        ),
        label = "buttonOpacity",
    )
    val fill = when (variant) {
        OhButtonVariant.Primary -> colors.acc
        OhButtonVariant.Secondary -> colors.surface2
        OhButtonVariant.Tinted -> colors.accSoft
        OhButtonVariant.Danger -> colors.dangerFill
        OhButtonVariant.Ghost, OhButtonVariant.Quiet -> Color.Transparent
    }
    val ink = when (variant) {
        OhButtonVariant.Primary -> colors.onAcc
        OhButtonVariant.Secondary -> colors.label
        // acc-ink on the 16 % tint measured 4.19:1 for the default accent over `bg`;
        // `label` passes, and the tint itself carries the accent (§5.7).
        OhButtonVariant.Tinted -> colors.label
        // red-ink on the 15 % fill measures 4.23:1 (dark) and 4.40:1 (light), below the
        // 4.5 of §11.1; `label` passes and the fill carries the danger signal (§5.7).
        OhButtonVariant.Danger -> colors.label
        OhButtonVariant.Ghost -> colors.accInk
        OhButtonVariant.Quiet -> colors.label2
    }
    val height = when (size) {
        OhButtonSize.Default -> dims.buttonHeight
        OhButtonSize.Small -> dims.buttonSmallHeight
        OhButtonSize.ExtraSmall -> dims.buttonXsHeight
    }
    val padH = when (size) {
        OhButtonSize.Default -> dims.buttonPadH
        OhButtonSize.Small -> dims.buttonSmallPadH
        OhButtonSize.ExtraSmall -> dims.buttonSmallPadH
    }
    // The small sizes take less vertical padding so they keep their stated heights
    // (35 dp and 28 dp) while still growing at 200 % text.
    val padV = when (size) {
        OhButtonSize.Default -> dims.buttonPadV
        OhButtonSize.Small -> OhTheme.space.s2
        OhButtonSize.ExtraSmall -> OhTheme.space.s0
    }
    val radius = when (size) {
        OhButtonSize.Default -> OhTheme.radius.base
        OhButtonSize.Small, OhButtonSize.ExtraSmall -> OhTheme.radius.sm
    }
    val textStyle = when (size) {
        OhButtonSize.Default -> type.body.copy(fontWeight = OhWeight.semibold)
        OhButtonSize.Small -> type.subhead.copy(fontWeight = OhWeight.semibold)
        OhButtonSize.ExtraSmall -> type.footnote.copy(fontWeight = OhWeight.semibold)
    }
    val shape = RoundedCornerShape(radius)
    val painted = modifier
        .heightIn(min = height)
        .alpha(if (!enabled) OhAlpha.disabled else pressedOpacity)
        .ohPressScale(interaction, OhTheme.motion.pressScale)
        .clip(shape)
        .background(fill)
        .ohClickable(interaction, enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = padH, vertical = padV)
    val label: @Composable () -> Unit = {
        if (leadingIcon != null) {
            OhIcon(
                icon = leadingIcon,
                contentDescription = null,
                size = dims.iconButtonGlyph,
                tint = ink,
            )
            Spacer(Modifier.size(OhTheme.space.s2))
        }
        Text(text = text, style = textStyle, color = ink, maxLines = 1)
    }
    if (size == OhButtonSize.Default) {
        Row(
            modifier = painted,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            label()
        }
    } else {
        // A `.sm` or `.xs` button paints below 44 dp, so its target grows to the
        // minimum while the painted height stays as specified (§4.5, §11.1).
        Box(
            modifier = modifier.defaultMinSize(minHeight = dims.touchTarget),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = painted,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                label()
            }
        }
    }
}

/**
 * §5.8 Icon button: a 36 dp circle with a 44 dp target, glyph 18 `label`, `.on`
 * uses `acc-soft` and `acc-ink`. One 8 dp orange dot may sit on it (§5.15).
 */
@Composable
fun OhIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    on: Boolean = false,
    enabled: Boolean = true,
    showDot: Boolean = false,
    tint: Color? = null,
) {
    val colors = OhTheme.colors
    val dims = OhTheme.dims
    val interaction = rememberInteraction()
    Box(
        modifier = modifier
            .size(dims.touchTarget)
            .ohClickable(interaction, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(dims.iconButton)
                .alpha(if (enabled) OhAlpha.full else OhAlpha.disabled)
                .ohPressScale(interaction, OhTheme.motion.pressScaleSmall)
                .clip(CircleShape)
                .background(if (on) colors.accSoft else colors.surface),
            contentAlignment = Alignment.Center,
        ) {
            OhIcon(
                icon = icon,
                contentDescription = null,
                size = dims.iconButtonGlyph,
                tint = tint ?: if (on) colors.accInk else colors.label,
            )
        }
        if (showDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = OhTheme.space.s1, end = OhTheme.space.s3)
                    .size(dims.dot + dims.dotRing * 2f)
                    .clip(CircleShape)
                    .background(colors.bgEl),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(dims.dot)
                        .clip(CircleShape)
                        .background(colors.orange),
                )
            }
        }
    }
}

/**
 * §5.9 Chip: 32 dp tall, `r-full`, text 15/400 (`subhead`; the section's "14" has no
 * role in the closed 11-step scale, see the build log). Selected is `acc` + `on-acc`
 * at weight 500; a filter strip may select neutrally with `surface-3` + `label`.
 */
@Composable
fun OhChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    neutralSelected: Boolean = false,
    enabled: Boolean = true,
    swatch: Color? = null,
) {
    val colors = OhTheme.colors
    val type = OhTheme.type
    val dims = OhTheme.dims
    val interaction = rememberInteraction()
    val fill = when {
        selected && neutralSelected -> colors.surface3
        selected -> colors.acc
        else -> colors.surface
    }
    val ink = when {
        selected && neutralSelected -> colors.label
        selected -> colors.onAcc
        else -> colors.label
    }
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = dims.touchTarget)
            .alpha(if (enabled) OhAlpha.full else OhAlpha.disabled)
            .ohPressScale(interaction, OhTheme.motion.pressScale)
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = dims.chipHeight)
                .clip(RoundedCornerShape(OhTheme.radius.full))
                .background(fill)
                .padding(horizontal = dims.chipPadH, vertical = dims.chipPadV),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (swatch != null) {
                Box(
                    modifier = Modifier
                        .size(OhTheme.space.s5)
                        .clip(CircleShape)
                        .background(swatch),
                )
                Spacer(Modifier.width(OhTheme.space.s2))
            }
            Text(
                text = text,
                style = type.subhead.copy(
                    fontWeight = if (selected) OhWeight.medium else OhWeight.regular,
                ),
                color = ink,
                maxLines = 1,
            )
        }
    }
}

/** A scrolling strip of chips; its slop is clipped by the strip (`DESIGN.md` §5.9). */
@Composable
fun OhChipStrip(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(end = OhTheme.space.s6),
        horizontalArrangement = Arrangement.spacedBy(OhTheme.space.s2),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** A neutral 29 dp tile for a row that has no asset yet (`DESIGN.md` §9.5). */
@Composable
fun OhGlyphTile(icon: ImageVector, contentDescription: String? = null) {
    val dims = OhTheme.dims
    Box(
        modifier = Modifier
            .size(dims.iconTile)
            .clip(RoundedCornerShape(OhTheme.radius.sm))
            .background(OhTheme.colors.surface3),
        contentAlignment = Alignment.Center,
    ) {
        OhIcon(
            icon = icon,
            contentDescription = contentDescription,
            size = dims.iconButtonGlyph,
            tint = OhTheme.colors.label2,
        )
    }
}
