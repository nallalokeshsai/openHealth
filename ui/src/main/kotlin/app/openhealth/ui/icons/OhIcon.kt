package app.openhealth.ui.icons

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.Dp
import app.openhealth.ui.theme.OhTheme
import app.openhealth.ui.theme.ohDims

/**
 * One custom line set, drawn for openHealth (`DESIGN.md` §10).
 *
 * 24 × 24 dp box, stroke 1.7, round caps and joins, no fill. Not Material Icons, not
 * SF Symbols, nothing copied from openGym (`§10.1`, `§13.6`). Every icon is built
 * once and cached; it inherits the text role's colour through [OhIcon]'s tint.
 */
internal fun ohIcon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = ohDims.iconBox,
        defaultHeight = ohDims.iconBox,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply(block).build()

/** One stroked path in the 24 dp box. No icon in the set is filled. */
internal fun ImageVector.Builder.stroke(
    strokeWidth: Float = ohDims.iconStroke,
    block: PathBuilder.() -> Unit,
) {
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = strokeWidth,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = block,
    )
}

/**
 * The same icon at a different stroke weight: a tab's glyph rests at 1.65 and goes to
 * 2.0 when active (`DESIGN.md` §5.15, §10.1). The path data is shared, so the set still
 * has one geometry.
 */
fun ImageVector.withStrokeWidth(width: Float): ImageVector {
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = defaultWidth,
        defaultHeight = defaultHeight,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
    )

    fun addNode(node: VectorNode) {
        when (node) {
            is VectorPath -> builder.addPath(
                pathData = node.pathData,
                pathFillType = node.pathFillType,
                name = node.name,
                fill = node.fill,
                fillAlpha = node.fillAlpha,
                stroke = node.stroke,
                strokeAlpha = node.strokeAlpha,
                strokeLineWidth = if (node.stroke == null) 0f else width,
                strokeLineCap = node.strokeLineCap,
                strokeLineJoin = node.strokeLineJoin,
                strokeLineMiter = node.strokeLineMiter,
                trimPathStart = node.trimPathStart,
                trimPathEnd = node.trimPathEnd,
                trimPathOffset = node.trimPathOffset,
            )

            is VectorGroup -> {
                // addGroup opens a group; the children added next belong to it.
                builder.addGroup(
                    name = node.name,
                    rotate = node.rotation,
                    pivotX = node.pivotX,
                    pivotY = node.pivotY,
                    scaleX = node.scaleX,
                    scaleY = node.scaleY,
                    translationX = node.translationX,
                    translationY = node.translationY,
                    clipPathData = node.clipPathData,
                )
                node.forEach { child -> addNode(child) }
            }
        }
    }

    root.forEach { node -> addNode(node) }
    return builder.build()
}

/** Draws one icon at the optical size the caller asks for (`DESIGN.md` §10.1). */
@Composable
fun OhIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = ohDims.iconButtonGlyph,
    tint: Color = LocalContentColor.current,
) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint,
    )
}

/** The optical box a card glyph uses (29 dp, §5.2). */
@Composable
fun OhCardGlyph(icon: ImageVector, contentDescription: String?, tint: Color) {
    OhIcon(
        icon = icon,
        contentDescription = contentDescription,
        size = OhTheme.dims.glyph,
        tint = tint,
    )
}
