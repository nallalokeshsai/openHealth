@file:Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")

package app.openhealth.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import app.openhealth.ui.icon.OhIcon
import app.openhealth.ui.icon.OhIconName
import app.openhealth.ui.theme.OhTheme
import app.openhealth.ui.theme.semiboldWeight

/**
 * DESIGN.md §5.3: the settings/list grammar. A `surface` container with 14 dp corners that clips
 * its rows, a hairline between rows only, and pressing a row fills it with `surface-2` — no scale
 * and no ripple.
 *
 * The scope exists so the group can inset each hairline correctly without the screen counting rows.
 */
@Composable
fun OhInsetGroup(modifier: Modifier = Modifier, content: @Composable OhInsetGroupScope.() -> Unit) {
    val colors = OhTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(OhTheme.radius.card))
            .background(colors.surface)
    ) {
        OhInsetGroupScope().content()
    }
}

class OhInsetGroupScope internal constructor() {
    private var drawn = 0

    @Composable
    fun row(
        title: String,
        subtitle: String? = null,
        value: String? = null,
        modifier: Modifier = Modifier,
        icon: OhIconName? = null,
        iconTint: Color = OhTheme.colors.acc,
        trailingChevron: Boolean = false,
        destructive: Boolean = false,
        enabled: Boolean = true,
        onClick: (() -> Unit)? = null,
        trailing: (@Composable () -> Unit)? = null
    ) {
        OhRow(
            title = title,
            subtitle = subtitle,
            value = value,
            modifier = modifier,
            icon = icon,
            iconTint = iconTint,
            trailingChevron = trailingChevron,
            destructive = destructive,
            enabled = enabled,
            showDivider = drawn > 0,
            onClick = onClick,
            trailing = trailing
        )
        drawn++
    }
}

/**
 * One row of a group (§5.3): 46 dp minimum, 12/16 dp padding, a 29 dp icon tile, title, subtitle,
 * value and either a chevron or one real secondary control.
 */
@Composable
fun OhRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    icon: OhIconName? = null,
    iconTint: Color = OhTheme.colors.acc,
    trailingChevron: Boolean = false,
    destructive: Boolean = false,
    enabled: Boolean = true,
    showDivider: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val colors = OhTheme.colors
    val type = OhTheme.type
    val geometry = OhTheme.geometry
    val density = LocalDensity.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val titleColor = when {
        !enabled -> colors.label3
        destructive -> colors.redInk
        else -> colors.label
    }
    val fill = if (pressed) colors.surface2 else Color.Transparent
    val dividerInset: Dp = if (icon != null) {
        geometry.rowHorizontalPadding + geometry.rowIconTile + geometry.rowGap
    } else {
        geometry.rowHorizontalPadding
    }
    val hairlinePx = with(density) { OhTheme.space.hairline.toPx() }
    val dividerInsetPx = with(density) { dividerInset.toPx() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = geometry.rowMinHeight)
            .then(
                if (showDivider) {
                    Modifier.topHairline(colors.sep, hairlinePx, dividerInsetPx)
                } else {
                    Modifier
                }
            )
            .background(fill)
            .then(
                if (onClick != null) {
                    Modifier
                        .semantics(mergeDescendants = true) {}
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            enabled = enabled,
                            role = Role.Button,
                            onClick = onClick
                        )
                } else {
                    Modifier
                }
            )
            .padding(
                horizontal = geometry.rowHorizontalPadding,
                vertical = geometry.rowVerticalPadding
            )
            .alpha(if (enabled) 1f else geometry.rowDisabledAlpha),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(geometry.rowIconTile)
                    .clip(RoundedCornerShape(geometry.rowIconRadius))
                    .background(if (destructive) colors.red else iconTint),
                contentAlignment = Alignment.Center
            ) {
                OhIcon(
                    name = icon,
                    contentDescription = null,
                    size = geometry.rowIconTile / 2,
                    tint = colors.onAcc
                )
            }
            Spacer(Modifier.width(geometry.rowGap))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = type.body,
                color = titleColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(subtitle, style = type.footnote, color = colors.label2, maxLines = 2)
            }
        }
        if (value != null) {
            Spacer(Modifier.width(geometry.rowGap))
            Text(
                text = value,
                style = type.body,
                color = colors.label2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        when {
            trailing != null -> {
                Spacer(Modifier.width(geometry.rowGap))
                trailing()
            }
            trailingChevron -> {
                Spacer(Modifier.width(geometry.rowGap))
                OhIcon(
                    name = OhIconName.ChevronRight,
                    contentDescription = null,
                    size = geometry.rowChevron,
                    tint = colors.label,
                    strokeWidth = geometry.iconStrokeChevron
                )
            }
        }
    }
}

/**
 * DESIGN.md §5.4. Exercises, foods, routines, activities and articles: a 50 dp thumbnail, a 16 sp
 * title, a 13 sp subtitle with the source, and one quiet action or a chevron, never both.
 */
@Composable
fun OhMediaRow(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    thumbnail: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val colors = OhTheme.colors
    val type = OhTheme.type
    val geometry = OhTheme.geometry
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = geometry.mediaMinHeight)
            .clip(RoundedCornerShape(OhTheme.radius.card))
            .background(if (pressed) colors.surface2 else colors.surface)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Button,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
            .padding(geometry.mediaPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(geometry.mediaThumbnail)
                .clip(RoundedCornerShape(geometry.mediaThumbnailRadius))
                .background(colors.surface2),
            contentAlignment = Alignment.Center
        ) {
            if (thumbnail != null) {
                thumbnail()
            } else {
                OhIcon(
                    name = OhIconName.File,
                    contentDescription = null,
                    size = geometry.mediaThumbnail / 2,
                    tint = colors.label3
                )
            }
        }
        Spacer(Modifier.width(geometry.mediaPadding))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = type.callout, color = colors.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = type.footnote, color = colors.label2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) {
            Spacer(Modifier.width(geometry.mediaPadding))
            trailing()
        }
    }
}

/**
 * DESIGN.md §5.18. The tombstone is the promise, the toast is the shortcut: a deleted entry leaves
 * this row where it was for the session, with its Undo.
 */
@Composable
fun OhTombstoneRow(text: String, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val colors = OhTheme.colors
    val type = OhTheme.type
    val geometry = OhTheme.geometry
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = geometry.rowMinHeight)
            .background(colors.surface2)
            .padding(
                horizontal = geometry.rowHorizontalPadding,
                vertical = geometry.rowVerticalPadding
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OhIcon(
            name = OhIconName.Delete,
            contentDescription = null,
            size = geometry.iconInText,
            tint = colors.label2
        )
        Spacer(Modifier.width(geometry.rowGap))
        Text(
            text = text,
            style = type.footnote,
            color = colors.label2,
            textDecoration = TextDecoration.LineThrough,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "Undo",
            style = type.subhead.semiboldWeight(),
            color = colors.accInk,
            modifier = Modifier
                .clickable(role = Role.Button, onClick = onUndo)
                .padding(horizontal = OhTheme.space.space3, vertical = OhTheme.space.space2)
                .semantics { contentDescription = "Undo delete" }
        )
    }
}
