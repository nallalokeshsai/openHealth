@file:Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")

package app.openhealth.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import app.openhealth.ui.icon.OhIcon
import app.openhealth.ui.icon.OhIconName
import app.openhealth.ui.theme.OhTheme
import app.openhealth.ui.theme.semiboldWeight

/**
 * DESIGN.md §5.1. The card is a fill and 14 dp radius, separated by space rather than an outline:
 * no border, no shadow in dark, never nested.
 */
@Composable
fun OhCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = OhTheme.colors
    val geometry = OhTheme.geometry
    val motion = OhTheme.motion
    val source = remember { MutableInteractionSource() }
    Box(modifier.padding(bottom = geometry.cardGap)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(OhTheme.radius.card))
                .background(colors.surface)
                .then(
                    if (onClick != null) {
                        Modifier.pressable(source, motion.pressCard, role = Role.Button, onClick = onClick)
                    } else {
                        Modifier
                    }
                )
                .padding(geometry.cardPadding),
            content = content
        )
    }
}

/**
 * DESIGN.md §5.2. One header everywhere: a 29 dp line glyph, an eyebrow, a wrapping title, an
 * optional subtitle and at most one trailing control with its own 44 dp target.
 */
@Composable
fun OhCardHeader(
    glyph: OhIconName,
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    glyphTint: Color = OhTheme.colors.accInk,
    trailing: (@Composable () -> Unit)? = null
) {
    val colors = OhTheme.colors
    val type = OhTheme.type
    val geometry = OhTheme.geometry
    Row(modifier = modifier.fillMaxWidth()) {
        OhIcon(
            name = glyph,
            contentDescription = null,
            size = geometry.cardHeaderGlyph,
            tint = glyphTint
        )
        Spacer(Modifier.width(OhTheme.space.space3))
        Column(modifier = Modifier.weight(1f)) {
            Text(eyebrow, style = type.footnote, color = colors.label2)
            Text(
                title,
                style = type.body.semiboldWeight(),
                color = colors.label
            )
            if (subtitle != null) {
                Text(subtitle, style = type.footnote, color = colors.label2)
            }
        }
        if (trailing != null) {
            Box(
                modifier = Modifier.size(geometry.cardHeaderTrailing),
                contentAlignment = Alignment.Center
            ) {
                trailing()
            }
        }
    }
}

/** DESIGN.md §5.5. Two per row, 12 dp padding, a display value and an optional signed delta. */
@Composable
fun OhStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    delta: String? = null,
    deltaColor: Color = OhTheme.colors.greenInk
) {
    val colors = OhTheme.colors
    val type = OhTheme.type
    val geometry = OhTheme.geometry
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(OhTheme.radius.card))
            .background(colors.surface)
            .padding(geometry.statTilePadding)
    ) {
        Text(label, style = type.footnote, color = colors.label2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(geometry.statDeltaGap))
        Text(value, style = type.display, color = colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (delta != null) {
            Spacer(Modifier.height(geometry.statDeltaGap))
            Text(delta, style = type.footnote, color = deltaColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** One figure in a [OhKpiBand]. */
data class OhKpiFigure(val label: String, val value: String)

/**
 * DESIGN.md §5.6. One surface card divided by vertical hairlines, three figures across. Figures
 * are type, not tiles: there are no boxes inside the card.
 */
@Composable
fun OhKpiBand(figures: List<OhKpiFigure>, modifier: Modifier = Modifier, columnCount: Int = 3) {
    val colors = OhTheme.colors
    val type = OhTheme.type
    val geometry = OhTheme.geometry
    val rows = figures.chunked(columnCount)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(OhTheme.radius.card))
            .background(colors.surface)
    ) {
        rows.forEachIndexed { rowIndex, rowFigures ->
            if (rowIndex > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(OhTheme.space.hairline)
                        .background(colors.sep)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = geometry.kpiHorizontalPadding,
                        vertical = geometry.kpiVerticalPadding
                    ),
                horizontalArrangement = Arrangement.spacedBy(OhTheme.space.space3)
            ) {
                rowFigures.forEachIndexed { index, figure ->
                    if (index > 0) {
                        Box(
                            Modifier
                                .width(OhTheme.space.hairline)
                                .fillMaxHeight()
                                .background(colors.sep)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(figure.label, style = type.footnote, color = colors.label2)
                        Text(figure.value, style = type.display, color = colors.label)
                    }
                }
            }
        }
    }
}
