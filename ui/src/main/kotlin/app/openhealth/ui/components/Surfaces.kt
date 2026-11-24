package app.openhealth.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.openhealth.ui.icons.OhIcon
import app.openhealth.ui.theme.OhAlpha
import app.openhealth.ui.theme.OhTheme
import app.openhealth.ui.theme.OhWeight
import app.openhealth.ui.theme.ltrFigures
import app.openhealth.ui.theme.tabular

/**
 * §5.1 Card: `surface`, radius 14, padding 16. No border, no shadow, and cards never
 * nest. With [onClick] the whole card is one button and scales to .985 on press;
 * a card holding another control is not a button (use [OhCardHeader]'s title).
 */
@Composable
fun OhCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = OhTheme.colors
    val radius = OhTheme.radius.card
    val shape = RoundedCornerShape(radius)
    val interaction = rememberInteraction()
    var surface = modifier.fillMaxWidth()
    if (onClick != null) {
        surface = surface.ohPressScale(interaction, OhTheme.motion.pressScaleCard)
    }
    surface = surface
        .clip(shape)
        .background(colors.surface)
    if (onClick != null) {
        surface = surface.ohClickable(interaction, onClick = onClick)
    }
    Column(modifier = surface.padding(OhTheme.dims.cardPad), content = content)
}

/**
 * §5.2 Card header: the one header used everywhere. Glyph (29 dp), eyebrow (13,
 * label-2), title (17/600, label, wraps), subtitle (13, label-2), at most one
 * trailing control.
 */
@Composable
fun OhCardHeader(
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    glyph: ImageVector? = null,
    glyphTint: Color = OhTheme.colors.accInk,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = OhTheme.colors
    val type = OhTheme.type
    val interaction = rememberInteraction()
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        if (glyph != null) {
            OhIcon(
                icon = glyph,
                contentDescription = null,
                size = OhTheme.dims.glyph,
                tint = glyphTint,
                modifier = Modifier.padding(top = OhTheme.space.s0),
            )
            Spacer(Modifier.width(OhTheme.space.s5))
        }
        var textColumn = Modifier.weight(1f)
        if (onClick != null) {
            textColumn = textColumn.ohClickable(interaction, onClick = onClick)
        }
        Column(modifier = textColumn) {
            Text(
                text = eyebrow,
                style = type.footnote,
                color = colors.label2,
            )
            Spacer(Modifier.height(OhTheme.space.s0))
            Text(
                text = title,
                style = type.body.copy(fontWeight = OhWeight.semibold),
                color = colors.label,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(OhTheme.space.s0))
                Text(
                    text = subtitle,
                    style = type.footnote,
                    color = colors.label2,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(OhTheme.space.s3))
            Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
        }
    }
}

/**
 * §5.3 Inset group list: `surface`, radius 14, clips its children, no padding.
 * Hairlines belong between rows only, so each [OhRow] decides with `showDivider`.
 */
@Composable
fun OhInsetGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(OhTheme.radius.card))
            .background(OhTheme.colors.surface),
        content = content,
    )
}

/**
 * §5.3 The settings/list row. One accessibility node: title, subtitle, value and
 * role are read as one item.
 */
@Composable
fun OhRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    unit: String? = null,
    leadingIcon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    leadingTint: Color = OhTheme.colors.acc,
    leadingInk: Color = OhTheme.colors.onAcc,
    destructive: Boolean = false,
    showChevron: Boolean = false,
    showDivider: Boolean = false,
    enabled: Boolean = true,
    mergeSemantics: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = OhTheme.colors
    val type = OhTheme.type
    val dims = OhTheme.dims
    val interaction = rememberInteraction()
    val narrow = LocalConfiguration.current.screenWidthDp <= OhTheme.dims.narrowValueRow
    // A value in words moves under the title; a number with a short unit stays trailing (§5.3).
    val wordsValue = value != null && narrow && value.count { it.isLetter() } > SHORT_UNIT_LETTERS
    var row = modifier
        .fillMaxWidth()
        .semantics(mergeDescendants = mergeSemantics) {}
    if (!enabled) {
        row = row.alpha(OhAlpha.disabledRow)
    }
    if (onClick != null) {
        row = row.ohSurfacePressable(interaction, RectangleShape, enabled, onClick)
    }
    // The hairline sits between rows: above this one, never above the first, and it is
    // drawn over the pressed fill rather than under it.
    if (showDivider) {
        row = row.ohHairline(
            color = colors.sep,
            insetStart = if (leadingIcon != null) dims.rowIconInset else dims.rowHairInset,
            atBottom = false,
        )
    }
    Row(
        modifier = row
            .heightIn(min = dims.rowMin)
            .padding(horizontal = dims.rowPadH, vertical = dims.rowPadV),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(dims.rowGap))
        } else if (leadingIcon != null) {
            Box(
                modifier = Modifier
                    .size(dims.iconTile)
                    .clip(RoundedCornerShape(OhTheme.radius.sm))
                    .background(if (destructive) Color.Transparent else leadingTint),
                contentAlignment = Alignment.Center,
            ) {
                OhIcon(
                    icon = leadingIcon,
                    contentDescription = null,
                    size = dims.iconButtonGlyph,
                    tint = if (destructive) colors.redInk else leadingInk,
                )
            }
            Spacer(Modifier.width(dims.rowGap))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = type.body,
                color = if (destructive) colors.redInk else colors.label,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(text = subtitle, style = type.footnote, color = colors.label2)
            }
            if (wordsValue) {
                Text(text = value.orEmpty(), style = type.footnote, color = colors.label2)
            }
        }
        if (value != null && !wordsValue) {
            Spacer(Modifier.width(OhTheme.space.s3))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = value,
                    style = type.body.tabular().ltrFigures(),
                    color = colors.label2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (unit != null) {
                    Text(
                        text = unit,
                        style = type.caption.ltrFigures(),
                        color = colors.label2,
                        maxLines = 1,
                    )
                }
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(OhTheme.space.s3))
            trailing()
        }
        if (showChevron) {
            Spacer(Modifier.width(OhTheme.space.s3))
            OhIcon(
                icon = app.openhealth.ui.icons.OhIcons.chevronRight,
                contentDescription = null,
                size = dims.chevron,
                tint = colors.label,
            )
        }
    }
}

/**
 * §5.4 Media row: 60 dp minimum, a 50 dp thumbnail, title 16/400, subtitle 13
 * `label-2`, one trailing control or a chevron, never both.
 */
@Composable
fun OhMediaRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    thumbnail: (@Composable () -> Unit)? = null,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = OhTheme.colors
    val type = OhTheme.type
    val dims = OhTheme.dims
    val interaction = rememberInteraction()
    val shape = RoundedCornerShape(OhTheme.radius.card)
    var row = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(colors.surface)
    if (onClick != null) {
        row = row.ohClickable(interaction, onClick = onClick).ohPressFill(interaction, shape)
    }
    Row(
        modifier = row
            .heightIn(min = dims.mediaRowMin)
            .padding(dims.mediaPad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (thumbnail != null) {
            Box(
                modifier = Modifier
                    .size(dims.mediaThumb)
                    .clip(RoundedCornerShape(OhTheme.radius.sm)),
            ) {
                thumbnail()
            }
            Spacer(Modifier.width(dims.rowGap))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = type.callout, color = colors.label, maxLines = 2)
            if (subtitle != null) {
                Text(text = subtitle, style = type.footnote, color = colors.label2, maxLines = 2)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(OhTheme.space.s2))
            trailing()
        }
        if (showChevron) {
            Spacer(Modifier.width(OhTheme.space.s3))
            OhIcon(
                icon = app.openhealth.ui.icons.OhIcons.chevronRight,
                contentDescription = null,
                size = dims.chevron,
                tint = colors.label,
            )
        }
    }
}

/** §5.5 Stat tile: label 13, value `display` 28/600, optional delta in a status ink. */
@Composable
fun OhStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    delta: String? = null,
    deltaInk: Color = OhTheme.colors.greenInk,
) {
    val colors = OhTheme.colors
    val type = OhTheme.type
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(OhTheme.radius.card))
            .background(colors.surface)
            .padding(OhTheme.space.s5),
    ) {
        Text(text = label, style = type.footnote, color = colors.label2)
        Spacer(Modifier.height(OhTheme.space.s0))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = type.display.copy(fontWeight = OhWeight.semibold).tabular().ltrFigures(),
                color = colors.label,
                maxLines = 1,
            )
            if (unit != null) {
                Spacer(Modifier.width(OhTheme.space.s0))
                Text(
                    text = unit,
                    style = type.subhead.ltrFigures(),
                    color = colors.label2,
                    maxLines = 1,
                )
            }
        }
        if (delta != null) {
            Spacer(Modifier.height(OhTheme.space.s0))
            Text(text = delta, style = type.footnote.tabular(), color = deltaInk)
        }
    }
}

/** One figure in an [OhKpiBand]. */
data class OhKpiFigure(
    val value: String,
    val label: String,
    val unit: String? = null,
    val ink: Color? = null,
)

/**
 * §5.6 KPI band: one card divided by vertical hairlines, three figures across.
 * Figures are type, not tiles; the optional footer sits underneath at full width.
 */
@Composable
fun OhKpiBand(figures: List<OhKpiFigure>, modifier: Modifier = Modifier, footer: String? = null) {
    val colors = OhTheme.colors
    val type = OhTheme.type
    require(figures.size == KPI_FIGURES) { "A KPI band shows three figures (DESIGN.md §5.6)." }
    val stack = androidx.compose.ui.platform.LocalDensity.current.fontScale >=
        OhTheme.dims.largeFontScale
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(OhTheme.radius.card))
            .background(colors.surface)
            .padding(horizontal = OhTheme.dims.cardPad, vertical = OhTheme.space.s5),
    ) {
        if (stack) {
            figures.forEachIndexed { index, figure ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(OhTheme.dims.hairline)
                            .background(colors.sep),
                    )
                    Spacer(Modifier.height(OhTheme.space.s3))
                }
                Text(text = figure.label, style = type.footnote, color = colors.label2)
                Text(
                    text = figure.value,
                    style = type.display.copy(fontWeight = OhWeight.semibold)
                        .tabular()
                        .ltrFigures(),
                    color = figure.ink ?: colors.label,
                )
                figure.unit?.let {
                    Text(text = it, style = type.caption, color = colors.label2)
                }
                Spacer(Modifier.height(OhTheme.space.s3))
            }
        } else {
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                figures.forEachIndexed { index, figure ->
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(OhTheme.dims.hairline)
                                .background(colors.sep),
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = figure.value,
                                style = type.display.copy(fontWeight = OhWeight.semibold)
                                    .tabular()
                                    .ltrFigures(),
                                color = figure.ink ?: colors.label,
                                maxLines = 1,
                            )
                            if (figure.unit != null) {
                                Spacer(Modifier.width(OhTheme.space.s0))
                                Text(
                                    text = figure.unit,
                                    style = type.caption.ltrFigures(),
                                    color = colors.label2,
                                    maxLines = 1,
                                )
                            }
                        }
                        Text(text = figure.label, style = type.footnote, color = colors.label2)
                    }
                }
            }
        }
        if (footer != null) {
            Spacer(Modifier.height(OhTheme.space.s3))
            Text(text = footer, style = type.footnote.tabular(), color = colors.label2)
        }
    }
}

/** A value with more letters than a short unit is a sentence and moves under its title. */
private const val SHORT_UNIT_LETTERS = 3

/** A KPI band shows three figures (DESIGN.md §5.6). */
private const val KPI_FIGURES = 3
