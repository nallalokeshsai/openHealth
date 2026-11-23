@file:Suppress("MagicNumber", "LongMethod", "LargeClass", "TooManyFunctions")

package app.openhealth.ui.icon

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.unit.Dp
import app.openhealth.ui.theme.OhIconGrid
import app.openhealth.ui.theme.OhTheme

/**
 * The one icon set of DESIGN.md §10. Every glyph is drawn for this product on the 24 × 24 dp grid
 * with a 1.5 dp padding, round caps and joins, stroke 1.7 (2.0 when active). Nothing is copied from
 * openGym, Material Icons or SF Symbols, and no emoji is ever an icon (§10.1, §10.3).
 *
 * A glyph is stored as path nodes rather than a font or a bitmap, so it inherits the text colour
 * through `tint` and scales with the type it sits in.
 */
@Suppress("LongParameterList", "FunctionNaming")
@Composable
fun OhIcon(
    name: OhIconName,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp? = null,
    tint: Color = OhTheme.colors.label,
    strokeWidth: Dp? = null
) {
    val geometry = OhTheme.geometry
    val stroke = strokeWidth ?: geometry.iconStroke
    val vector = remember(name, stroke) { iconVector(name, stroke.value) }
    Icon(
        imageVector = vector,
        contentDescription = contentDescription,
        modifier = modifier.size(size ?: geometry.iconBox),
        tint = tint
    )
}

/** The core set (§10.2), the routine glyph keys (§10.3) and the status glyphs (§10.4). */
enum class OhIconName {
    // Navigation (§10.2)
    Today,
    Train,
    Food,
    Body,
    Insights,
    More,
    Settings,

    // Modules (§10.2)
    Workout,
    Yoga,
    Steps,
    Weight,
    Heart,
    Sleep,
    Activity,
    Mood,
    Cycle,
    Family,
    Coach,
    Integrations,

    // Actions (§10.2)
    Add,
    Minus,
    Check,
    Close,
    Error,
    ChevronLeft,
    ChevronRight,
    ChevronDown,
    Search,
    Filter,
    Scan,
    Camera,
    Play,
    Pause,
    Skip,
    Timer,
    Edit,
    Delete,
    Undo,
    Share,
    Export,
    Lock,
    CloudOff,
    Refresh,
    Info,
    Warning,
    ExternalLink,

    // Objects (§10.2)
    Barbell,
    Dumbbell,
    Kettlebell,
    Plate,
    Machine,
    Band,
    Mat,
    Shoe,
    Bike,
    Route,
    Scale,
    Cuff,
    Glucose,
    Pill,
    Lab,
    File,
    Watch,
    Phone,

    // Status glyphs (§10.4)
    Trophy,
    Snowflake,
    Flame,

    // Routine glyph keys (§10.3), grouped Strength / Equipment / Cardio / Recovery
    Strength,
    Arms,
    Abs,
    Legs,
    PullUp,
    Run,
    Swim,
    Boxing,
    Stretch,
    Moon,
    Bolt
}

internal fun iconVector(name: OhIconName, strokeWidth: Float): ImageVector = ImageVector.Builder(
    name = "OhIcon.${name.name}",
    defaultWidth = OhIconGrid,
    defaultHeight = OhIconGrid,
    viewportWidth = 24f,
    viewportHeight = 24f
).addPath(
    pathData = glyphOf(name),
    fill = null,
    stroke = SolidColor(Color.White),
    strokeLineWidth = strokeWidth,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round
).build()

private class Glyph {
    private val nodes = mutableListOf<PathNode>()

    fun moveTo(x: Float, y: Float) = apply { nodes += PathNode.MoveTo(x, y) }
    fun lineTo(x: Float, y: Float) = apply { nodes += PathNode.LineTo(x, y) }
    fun quadTo(cx: Float, cy: Float, x: Float, y: Float) = apply { nodes += PathNode.QuadTo(cx, cy, x, y) }

    @Suppress("LongParameterList")
    fun curveTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) = apply {
        nodes += PathNode.CurveTo(x1, y1, x2, y2, x3, y3)
    }
    fun close() = apply { nodes += PathNode.Close }

    fun line(x1: Float, y1: Float, x2: Float, y2: Float) = moveTo(x1, y1).lineTo(x2, y2)
    fun polyline(vararg points: Float) = apply {
        moveTo(points[0], points[1])
        var index = 2
        while (index < points.size) {
            lineTo(points[index], points[index + 1])
            index += 2
        }
    }
    fun polygon(vararg points: Float) = apply { polyline(*points).close() }

    fun circle(cx: Float, cy: Float, r: Float) = apply {
        val k = r * 0.5523f
        moveTo(cx + r, cy)
        curveTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r)
        curveTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy)
        curveTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r)
        curveTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy)
        close()
    }

    fun roundRect(x: Float, y: Float, w: Float, h: Float, r: Float) = apply {
        moveTo(x + r, y)
        lineTo(x + w - r, y)
        quadTo(x + w, y, x + w, y + r)
        lineTo(x + w, y + h - r)
        quadTo(x + w, y + h, x + w - r, y + h)
        lineTo(x + r, y + h)
        quadTo(x, y + h, x, y + h - r)
        lineTo(x, y + r)
        quadTo(x, y, x + r, y)
        close()
    }

    fun build(): List<PathNode> = nodes.toList()
}

@Suppress("CyclomaticComplexMethod", "LongMethod")
private fun glyphOf(name: OhIconName): List<PathNode> = when (name) {
    OhIconName.Today -> Glyph()
        .polyline(3.5f, 10.5f, 12f, 3.5f, 20.5f, 10.5f)
        .line(6f, 10.5f, 6f, 20.5f).line(18f, 10.5f, 18f, 20.5f)
        .line(6f, 20.5f, 18f, 20.5f)
        .line(10f, 20.5f, 10f, 14.5f).line(14f, 20.5f, 14f, 14.5f)
        .line(10f, 14.5f, 14f, 14.5f).build()

    OhIconName.Train, OhIconName.Dumbbell, OhIconName.Strength -> Glyph()
        .line(3f, 9f, 3f, 15f).line(21f, 9f, 21f, 15f)
        .line(6f, 6.5f, 6f, 17.5f).line(18f, 6.5f, 18f, 17.5f)
        .line(6f, 12f, 18f, 12f).build()

    OhIconName.Food -> Glyph()
        .line(7f, 3.5f, 7f, 20.5f)
        .line(4.5f, 3.5f, 4.5f, 9f).line(9.5f, 3.5f, 9.5f, 9f).line(4.5f, 9f, 9.5f, 9f)
        .line(16.5f, 3.5f, 16.5f, 20.5f)
        .moveTo(16.5f, 3.5f).curveTo(19.5f, 6f, 19.5f, 10.5f, 16.5f, 11.5f).build()

    OhIconName.Body -> Glyph().circle(12f, 5.2f, 2.4f)
        .moveTo(8.6f, 21f).lineTo(8.6f, 12.5f).curveTo(8.6f, 10.8f, 9.9f, 9.6f, 12f, 9.6f)
        .curveTo(14.1f, 9.6f, 15.4f, 10.8f, 15.4f, 12.5f).lineTo(15.4f, 21f)
        .moveTo(6.4f, 12f).lineTo(8.6f, 11.4f).moveTo(17.6f, 12f).lineTo(15.4f, 11.4f).build()

    OhIconName.Insights -> Glyph().polyline(3f, 18f, 8f, 11.5f, 12.5f, 15f, 21f, 5.5f)
        .line(3f, 21f, 21f, 21f).build()

    OhIconName.More -> Glyph().line(3f, 7f, 21f, 7f).circle(8.5f, 7f, 1.6f)
        .line(3f, 12f, 21f, 12f).circle(15f, 12f, 1.6f)
        .line(3f, 17f, 21f, 17f).circle(10.5f, 17f, 1.6f).build()

    OhIconName.Settings -> Glyph().circle(12f, 12f, 3.2f)
        .polygon(
            21.2f, 12f, 18.28f, 14.6f, 18.51f, 18.51f, 14.6f, 18.28f,
            12f, 21.2f, 9.4f, 18.28f, 5.49f, 18.51f, 5.72f, 14.6f,
            2.8f, 12f, 5.72f, 9.4f, 5.49f, 5.49f, 9.4f, 5.72f,
            12f, 2.8f, 14.6f, 5.72f, 18.51f, 5.49f, 18.28f, 9.4f
        ).build()

    OhIconName.Workout -> Glyph().line(2.5f, 12f, 21.5f, 12f)
        .roundRect(4f, 7.5f, 4f, 9f, 1f).roundRect(16f, 7.5f, 4f, 9f, 1f)
        .roundRect(8.5f, 9f, 2.5f, 6f, 0.8f).roundRect(13f, 9f, 2.5f, 6f, 0.8f).build()

    OhIconName.Yoga -> Glyph().circle(12f, 4.8f, 2.2f)
        .moveTo(12f, 7.4f).lineTo(12f, 14f).lineTo(6.5f, 20.5f)
        .moveTo(12f, 14f).lineTo(17.5f, 20.5f)
        .moveTo(6.5f, 10.5f).lineTo(12f, 12f).lineTo(17.5f, 10.5f).build()

    OhIconName.Steps -> Glyph().roundRect(5f, 3.5f, 5.5f, 11f, 2.7f)
        .roundRect(13.5f, 9.5f, 5.5f, 11f, 2.7f).build()

    OhIconName.Weight -> Glyph().roundRect(3.5f, 3.5f, 17f, 17f, 3f)
        .moveTo(7.5f, 15f).curveTo(7.5f, 11.5f, 9.5f, 9.5f, 12f, 9.5f)
        .curveTo(14.5f, 9.5f, 16.5f, 11.5f, 16.5f, 15f)
        .line(12f, 15f, 13.6f, 11.8f).build()

    OhIconName.Heart -> Glyph()
        .moveTo(12f, 20.5f)
        .curveTo(4.2f, 14.8f, 3f, 10.2f, 5.2f, 7.2f)
        .curveTo(7.2f, 4.4f, 10.6f, 4.8f, 12f, 7.6f)
        .curveTo(13.4f, 4.8f, 16.8f, 4.4f, 18.8f, 7.2f)
        .curveTo(21f, 10.2f, 19.8f, 14.8f, 12f, 20.5f).close().build()

    OhIconName.Sleep, OhIconName.Moon -> Glyph()
        .moveTo(15.5f, 3.5f)
        .curveTo(10.5f, 5f, 8f, 8.4f, 8f, 12f)
        .curveTo(8f, 16.2f, 11f, 19.5f, 15.5f, 20.5f)
        .curveTo(11.5f, 17.5f, 10.5f, 14.8f, 10.5f, 12f)
        .curveTo(10.5f, 9f, 11.5f, 6.4f, 15.5f, 3.5f)
        .close()
        .circle(18.5f, 6f, 0.8f).build()

    OhIconName.Activity -> Glyph().roundRect(2.5f, 5f, 19f, 14f, 2.5f)
        .polyline(2.5f, 13f, 7f, 13f, 9f, 9f, 12f, 16f, 14.5f, 12.5f, 21.5f, 12.5f).build()

    OhIconName.Mood -> Glyph().circle(12f, 12f, 9f)
        .circle(9f, 10f, 0.8f).circle(15f, 10f, 0.8f)
        .moveTo(8f, 14f).curveTo(9.5f, 16.5f, 14.5f, 16.5f, 16f, 14f).build()

    OhIconName.Cycle -> Glyph()
        .moveTo(19.5f, 12f).curveTo(19.5f, 7.9f, 16.1f, 4.5f, 12f, 4.5f)
        .curveTo(9.2f, 4.5f, 6.8f, 6.1f, 5.6f, 8.4f)
        .moveTo(3f, 5.5f).lineTo(5.6f, 8.4f).lineTo(8.5f, 5.8f)
        .moveTo(4.5f, 12f).curveTo(4.5f, 16.1f, 7.9f, 19.5f, 12f, 19.5f)
        .curveTo(14.8f, 19.5f, 17.2f, 17.9f, 18.4f, 15.6f)
        .moveTo(21f, 18.5f).lineTo(18.4f, 15.6f).lineTo(15.5f, 18.2f).build()

    OhIconName.Family -> Glyph().circle(8.5f, 7.5f, 2.5f).circle(16.5f, 9f, 2f)
        .moveTo(3.5f, 20f).curveTo(3.5f, 15.8f, 5.8f, 13.5f, 8.5f, 13.5f)
        .curveTo(11.2f, 13.5f, 13.5f, 15.8f, 13.5f, 20f)
        .moveTo(14.5f, 13.8f).curveTo(17.5f, 13.4f, 20.5f, 15f, 20.5f, 19f).build()

    OhIconName.Coach -> Glyph().roundRect(3f, 4.5f, 18f, 13f, 3f)
        .polyline(8f, 17.5f, 8f, 21f, 12f, 17.5f)
        .line(7.5f, 11f, 16.5f, 11f).line(7.5f, 8f, 13f, 8f).build()

    OhIconName.Integrations -> Glyph()
        .roundRect(2.5f, 8.5f, 7f, 7f, 2f)
        .roundRect(14.5f, 8.5f, 7f, 7f, 2f)
        .line(9.5f, 12f, 14.5f, 12f)
        .line(6f, 5.5f, 6f, 8.5f).line(18f, 15.5f, 18f, 18.5f).build()

    OhIconName.Add -> Glyph().line(12f, 5f, 12f, 19f).line(5f, 12f, 19f, 12f).build()

    OhIconName.Minus -> Glyph().line(5f, 12f, 19f, 12f).build()

    OhIconName.Check -> Glyph().polyline(4.5f, 12.5f, 9.5f, 17.5f, 19.5f, 6.5f).build()

    OhIconName.Close -> Glyph().line(6f, 6f, 18f, 18f).line(18f, 6f, 6f, 18f).build()

    OhIconName.ChevronLeft -> Glyph().polyline(15f, 4.5f, 7.5f, 12f, 15f, 19.5f).build()

    OhIconName.ChevronRight -> Glyph().polyline(9f, 4.5f, 16.5f, 12f, 9f, 19.5f).build()

    OhIconName.ChevronDown -> Glyph().polyline(4.5f, 9f, 12f, 16.5f, 19.5f, 9f).build()

    OhIconName.Search -> Glyph().circle(10.5f, 10.5f, 6.5f).line(15.5f, 15.5f, 20.5f, 20.5f).build()

    OhIconName.Filter -> Glyph().line(3f, 6f, 21f, 6f).line(6f, 12f, 18f, 12f).line(9.5f, 18f, 14.5f, 18f).build()

    OhIconName.Scan -> Glyph()
        .polyline(3.5f, 8f, 3.5f, 3.5f, 8f, 3.5f)
        .polyline(16f, 3.5f, 20.5f, 3.5f, 20.5f, 8f)
        .polyline(20.5f, 16f, 20.5f, 20.5f, 16f, 20.5f)
        .polyline(8f, 20.5f, 3.5f, 20.5f, 3.5f, 16f)
        .line(7f, 8f, 7f, 16f).line(10f, 8f, 10f, 16f)
        .line(13.5f, 8f, 13.5f, 16f).line(17f, 8f, 17f, 16f).build()

    OhIconName.Camera -> Glyph().roundRect(2.5f, 6.5f, 19f, 13.5f, 3f)
        .circle(12f, 13.2f, 4f).line(8f, 3.5f, 10f, 6.5f).line(16f, 6.5f, 18f, 6.5f).build()

    OhIconName.Play -> Glyph().polygon(7f, 4.5f, 19f, 12f, 7f, 19.5f).build()

    OhIconName.Pause -> Glyph().line(8f, 4.5f, 8f, 19.5f).line(16f, 4.5f, 16f, 19.5f).build()

    OhIconName.Skip -> Glyph().polygon(5f, 4.5f, 16f, 12f, 5f, 19.5f).line(19f, 4.5f, 19f, 19.5f).build()

    OhIconName.Timer -> Glyph().circle(12f, 13f, 8f)
        .line(12f, 13f, 15.5f, 10.5f).line(12f, 13f, 12f, 8.5f)
        .line(9f, 2.5f, 15f, 2.5f).line(12f, 2.5f, 12f, 5f)
        .line(18.5f, 6f, 20f, 4.5f).build()

    OhIconName.Edit -> Glyph()
        .moveTo(4f, 20f).lineTo(5f, 15.5f).lineTo(16.5f, 4f)
        .curveTo(17.4f, 3.1f, 18.9f, 3.1f, 19.8f, 4f)
        .curveTo(20.7f, 4.9f, 20.7f, 6.4f, 19.8f, 7.3f)
        .lineTo(8.5f, 19f).close().build()

    OhIconName.Delete -> Glyph().line(4f, 6.5f, 20f, 6.5f)
        .polyline(9.5f, 3f, 14.5f, 3f, 15f, 6.5f).line(9f, 6.5f, 9.5f, 21f).line(15f, 6.5f, 14.5f, 21f)
        .line(9.5f, 21f, 14.5f, 21f).line(10.5f, 10f, 10.8f, 17.5f).line(13.5f, 10f, 13.2f, 17.5f).build()

    OhIconName.Undo -> Glyph()
        .moveTo(4f, 11f).lineTo(9f, 6f)
        .moveTo(4f, 11f).lineTo(9f, 16f)
        .moveTo(4.5f, 11f).curveTo(4.5f, 6.5f, 8.5f, 3.5f, 13f, 3.5f)
        .curveTo(17.5f, 3.5f, 21f, 7f, 21f, 11.5f)
        .curveTo(21f, 16f, 17.5f, 19.5f, 13f, 19.5f).build()

    OhIconName.Share -> Glyph().circle(6f, 12f, 2.5f).circle(18f, 6f, 2.5f).circle(18f, 18f, 2.5f)
        .line(8.2f, 10.8f, 15.8f, 7.2f).line(8.2f, 13.2f, 15.8f, 16.8f).build()

    OhIconName.Export -> Glyph().roundRect(3.5f, 10f, 13f, 10.5f, 2f)
        .line(18f, 3.5f, 18f, 10f).line(14.5f, 7f, 18f, 3.5f).line(21.5f, 7f, 18f, 3.5f).build()

    OhIconName.Lock -> Glyph().roundRect(4.5f, 10f, 15f, 10.5f, 2.5f)
        .moveTo(8f, 10f).lineTo(8f, 7.5f)
        .curveTo(8f, 4.2f, 9.8f, 2.5f, 12f, 2.5f)
        .curveTo(14.2f, 2.5f, 16f, 4.2f, 16f, 7.5f).lineTo(16f, 10f).build()

    OhIconName.CloudOff -> Glyph()
        .moveTo(6f, 17f).curveTo(3.6f, 17f, 2.5f, 15f, 3.2f, 13f)
        .curveTo(3.8f, 11.4f, 5.3f, 10.8f, 6.5f, 11f)
        .curveTo(7f, 8f, 9.3f, 6f, 12.3f, 6f)
        .curveTo(15.6f, 6f, 18f, 8.4f, 18.4f, 11.4f)
        .curveTo(20.2f, 11.8f, 21.3f, 13.3f, 20.8f, 15f)
        .line(4f, 4f, 20f, 20f).build()

    OhIconName.Refresh -> Glyph()
        .moveTo(20f, 12f).curveTo(20f, 7.6f, 16.4f, 4f, 12f, 4f)
        .curveTo(8.8f, 4f, 6f, 5.9f, 4.8f, 8.6f)
        .moveTo(4f, 4.5f).lineTo(4.8f, 8.6f).lineTo(8.8f, 7.6f)
        .moveTo(4f, 12f).curveTo(4f, 16.4f, 7.6f, 20f, 12f, 20f)
        .curveTo(15.2f, 20f, 18f, 18.1f, 19.2f, 15.4f)
        .moveTo(20f, 19.5f).lineTo(19.2f, 15.4f).lineTo(15.2f, 16.4f).build()

    OhIconName.Info -> Glyph().circle(12f, 12f, 9f)
        .line(12f, 10.5f, 12f, 17f).circle(12f, 7f, 0.9f).build()

    OhIconName.Warning -> Glyph().polygon(12f, 3f, 21.5f, 20.5f, 2.5f, 20.5f)
        .line(12f, 8f, 12f, 14.5f).circle(12f, 17.5f, 0.9f).build()

    OhIconName.Error -> Glyph()
        .polygon(
            12f, 2.5f, 18.7f, 5.3f, 21.5f, 12f, 18.7f, 18.7f,
            12f, 21.5f, 5.3f, 18.7f, 2.5f, 12f, 5.3f, 5.3f
        )
        .line(9f, 9f, 15f, 15f).line(15f, 9f, 9f, 15f).build()

    OhIconName.ExternalLink -> Glyph().roundRect(3f, 7f, 14f, 14f, 2.5f)
        .line(12f, 3.5f, 20.5f, 3.5f).line(20.5f, 3.5f, 20.5f, 12f)
        .line(20.5f, 3.5f, 12.5f, 11.5f).build()

    OhIconName.Barbell -> Glyph().line(2.5f, 12f, 21.5f, 12f)
        .line(5f, 7f, 5f, 17f).line(8f, 5.5f, 8f, 18.5f)
        .line(16f, 5.5f, 16f, 18.5f).line(19f, 7f, 19f, 17f).build()

    OhIconName.Kettlebell -> Glyph().circle(12f, 15f, 6f)
        .moveTo(8f, 9.5f).curveTo(8f, 5.5f, 9.5f, 3.5f, 12f, 3.5f)
        .curveTo(14.5f, 3.5f, 16f, 5.5f, 16f, 9.5f).build()

    OhIconName.Plate -> Glyph().circle(12f, 12f, 8.5f).circle(12f, 12f, 4.5f)
        .line(12f, 3.5f, 12f, 5f).line(12f, 19f, 12f, 20.5f).build()

    OhIconName.Machine -> Glyph().line(4f, 3f, 4f, 20.5f)
        .circle(4f, 3.5f, 1.5f)
        .line(4f, 5f, 4f, 13f).line(4f, 13f, 9f, 13f)
        .line(9f, 13f, 15f, 13f).line(12f, 13f, 12f, 20.5f)
        .line(6f, 20.5f, 12f, 20.5f).line(15f, 13f, 15f, 20.5f)
        .circle(9f, 16f, 2f).build()

    OhIconName.Band -> Glyph()
        .moveTo(7f, 5f).curveTo(3.5f, 7f, 3.5f, 17f, 7f, 19f)
        .lineTo(17f, 19f).curveTo(20.5f, 17f, 20.5f, 7f, 17f, 5f).close().build()

    OhIconName.Mat -> Glyph().roundRect(3f, 8f, 17f, 8f, 2.5f).circle(19f, 12f, 2.5f).build()

    OhIconName.Shoe -> Glyph()
        .moveTo(3f, 16f).lineTo(3f, 12f).curveTo(5.5f, 12f, 7f, 10.5f, 8f, 8f)
        .lineTo(12f, 8f).curveTo(13.5f, 11f, 16.5f, 12.5f, 21f, 13.5f)
        .lineTo(21f, 16f).close().line(3f, 16f, 21f, 16f).build()

    OhIconName.Bike -> Glyph().circle(6f, 16.5f, 3.5f).circle(18f, 16.5f, 3.5f)
        .line(6f, 16.5f, 10f, 9f).line(10f, 9f, 14f, 16.5f).line(10f, 9f, 8.5f, 7f)
        .line(14f, 16.5f, 18f, 16.5f).line(13f, 8f, 17f, 8f).build()

    OhIconName.Route -> Glyph().circle(6f, 18f, 2.5f).circle(18f, 6f, 2.5f)
        .moveTo(8.5f, 18f).curveTo(14f, 18f, 10f, 8f, 15.5f, 6f).build()

    OhIconName.Scale -> Glyph().roundRect(3f, 3f, 18f, 18f, 4f)
        .moveTo(7f, 15f).curveTo(7f, 11f, 9f, 8.5f, 12f, 8.5f)
        .curveTo(15f, 8.5f, 17f, 11f, 17f, 15f).line(12f, 15f, 13.5f, 11.5f).build()

    OhIconName.Cuff -> Glyph().roundRect(8f, 8f, 8f, 8f, 2.5f)
        .circle(12f, 12f, 2f)
        .moveTo(8f, 11f).quadTo(4f, 13f, 8f, 15f)
        .moveTo(16f, 11f).quadTo(20f, 13f, 16f, 15f).build()

    OhIconName.Glucose -> Glyph()
        .moveTo(12f, 3f).curveTo(8f, 8f, 6f, 10.5f, 6f, 13.5f)
        .curveTo(6f, 17f, 8.7f, 19.5f, 12f, 19.5f)
        .curveTo(15.3f, 19.5f, 18f, 17f, 18f, 13.5f)
        .curveTo(18f, 10.5f, 16f, 8f, 12f, 3f).close()
        .line(9.5f, 14f, 14.5f, 14f).build()

    OhIconName.Pill -> Glyph()
        .moveTo(6f, 17.5f).lineTo(16.5f, 7f)
        .curveTo(18f, 5.5f, 20f, 5.5f, 21f, 6.5f)
        .curveTo(22f, 7.5f, 22f, 9.5f, 20.5f, 11f)
        .lineTo(10f, 21f).curveTo(8.5f, 22.5f, 6.5f, 22f, 5f, 20.5f)
        .curveTo(3.5f, 19f, 4.5f, 18.5f, 6f, 17.5f).close()
        .line(11f, 12f, 15f, 16f).build()

    OhIconName.Lab -> Glyph()
        .line(9f, 3f, 15f, 3f).line(10.5f, 3f, 10.5f, 9f).line(13.5f, 3f, 13.5f, 9f)
        .moveTo(10.5f, 9f).lineTo(5.5f, 19f).curveTo(4.8f, 20.6f, 6f, 21f, 7f, 21f)
        .lineTo(17f, 21f).curveTo(18f, 21f, 19.2f, 20.6f, 18.5f, 19f)
        .lineTo(13.5f, 9f).close().line(8f, 16f, 16f, 16f).build()

    OhIconName.File -> Glyph()
        .moveTo(5f, 3f).lineTo(14f, 3f).lineTo(19f, 8f).lineTo(19f, 21f).lineTo(5f, 21f).close()
        .line(14f, 3f, 14f, 8f).line(14f, 8f, 19f, 8f)
        .line(8f, 12f, 16f, 12f).line(8f, 16f, 13f, 16f).build()

    OhIconName.Watch -> Glyph().roundRect(7f, 6.5f, 10f, 11f, 2.5f)
        .line(9.5f, 3f, 14.5f, 3f).line(9.5f, 3f, 9.5f, 6.5f).line(14.5f, 3f, 14.5f, 6.5f)
        .line(9.5f, 17.5f, 9.5f, 21f).line(14.5f, 17.5f, 14.5f, 21f)
        .line(9.5f, 21f, 14.5f, 21f).line(12f, 9f, 12f, 12.5f).line(12f, 12.5f, 14f, 13.5f).build()

    OhIconName.Phone -> Glyph().roundRect(6.5f, 2.5f, 11f, 19f, 2.5f)
        .line(10.5f, 5f, 13.5f, 5f).circle(12f, 18.5f, 0.9f).build()

    OhIconName.Trophy -> Glyph()
        .moveTo(7f, 3.5f).lineTo(17f, 3.5f).lineTo(17f, 8f)
        .curveTo(17f, 12f, 15f, 14f, 12f, 14f).curveTo(9f, 14f, 7f, 12f, 7f, 8f).close()
        .moveTo(7f, 5.5f).curveTo(4f, 5.5f, 3.5f, 9f, 7f, 9.5f)
        .moveTo(17f, 5.5f).curveTo(20f, 5.5f, 20.5f, 9f, 17f, 9.5f)
        .line(12f, 14f, 12f, 17.5f).line(8.5f, 20.5f, 15.5f, 20.5f).line(9.5f, 17.5f, 14.5f, 17.5f).build()

    OhIconName.Snowflake -> Glyph()
        .line(12f, 2.5f, 12f, 21.5f).line(3.8f, 7.2f, 20.2f, 16.8f).line(3.8f, 16.8f, 20.2f, 7.2f)
        .line(9.5f, 4.5f, 12f, 7f).line(14.5f, 4.5f, 12f, 7f)
        .line(9.5f, 19.5f, 12f, 17f).line(14.5f, 19.5f, 12f, 17f).build()

    OhIconName.Flame -> Glyph()
        .moveTo(12f, 2.5f).curveTo(13f, 6.5f, 19f, 9f, 19f, 14.5f)
        .curveTo(19f, 18.5f, 15.8f, 21.5f, 12f, 21.5f)
        .curveTo(8.2f, 21.5f, 5f, 18.5f, 5f, 14.5f)
        .curveTo(5f, 11f, 8f, 9.5f, 8f, 6.5f)
        .curveTo(9.5f, 8f, 10.5f, 9.5f, 11f, 11f)
        .curveTo(11.5f, 8f, 11.5f, 5f, 12f, 2.5f).close().build()

    OhIconName.Arms -> Glyph()
        .moveTo(4f, 6f).lineTo(8f, 10f).curveTo(10f, 12f, 10f, 14f, 8f, 16f)
        .lineTo(5f, 19f).moveTo(20f, 6f).lineTo(16f, 10f)
        .curveTo(14f, 12f, 14f, 14f, 16f, 16f).lineTo(19f, 19f)
        .line(8f, 10f, 11f, 9f).line(16f, 10f, 13f, 9f).build()

    OhIconName.Abs -> Glyph().roundRect(8f, 3f, 8f, 18f, 3f)
        .line(9.5f, 9f, 14.5f, 9f).line(9.5f, 12f, 14.5f, 12f)
        .line(9.5f, 15f, 14.5f, 15f).line(12f, 6.5f, 12f, 17.5f).build()

    OhIconName.Legs -> Glyph()
        .moveTo(8f, 3f).lineTo(9f, 11f).lineTo(6.5f, 21f)
        .moveTo(16f, 3f).lineTo(15f, 11f).lineTo(17.5f, 21f)
        .line(9f, 11f, 12f, 13f).line(15f, 11f, 12f, 13f).build()

    OhIconName.PullUp -> Glyph().line(3f, 3f, 21f, 3f)
        .line(7f, 3f, 7f, 7.5f).line(17f, 3f, 17f, 7.5f)
        .line(7f, 7.5f, 10f, 10.5f).line(17f, 7.5f, 14f, 10.5f)
        .circle(12f, 13f, 2.2f)
        .line(12f, 15.2f, 12f, 20f).line(9f, 21f, 12f, 20f).line(15f, 21f, 12f, 20f).build()

    OhIconName.Run -> Glyph().circle(15f, 4.5f, 2.2f)
        .line(15f, 7f, 12f, 12f).line(12f, 12f, 15f, 16f).line(15f, 16f, 13f, 21f)
        .line(12f, 12f, 7f, 14.5f).line(7f, 14.5f, 4f, 12f)
        .line(15f, 16f, 19f, 14f).build()

    OhIconName.Swim -> Glyph().circle(17f, 7f, 2.2f)
        .moveTo(3f, 13f).curveTo(5f, 11f, 7f, 11f, 9f, 13f)
        .curveTo(11f, 15f, 13f, 15f, 15f, 13f)
        .moveTo(3f, 18f).curveTo(5f, 16f, 7f, 16f, 9f, 18f)
        .curveTo(11f, 20f, 13f, 20f, 15f, 18f)
        .moveTo(14f, 11f).lineTo(10f, 8f).lineTo(6f, 9f).build()

    OhIconName.Boxing -> Glyph().circle(11f, 12f, 7f)
        .moveTo(5f, 8f).curveTo(5f, 5f, 8f, 4f, 11f, 4f)
        .lineTo(16f, 4f).curveTo(18f, 4f, 19f, 6f, 19f, 8f).lineTo(19f, 12f)
        .lineTo(15f, 12f).curveTo(14f, 12f, 14f, 14f, 15f, 14f).lineTo(17f, 14f)
        .lineTo(17f, 18f).curveTo(17f, 20f, 15f, 21f, 13f, 21f).lineTo(9f, 21f)
        .curveTo(7f, 21f, 5f, 19f, 5f, 17f).close().build()

    OhIconName.Stretch -> Glyph().circle(12f, 4.5f, 2.2f)
        .line(12f, 7f, 12f, 13f).line(12f, 13f, 7f, 21f).line(12f, 13f, 17f, 21f)
        .line(5f, 8f, 12f, 9f).line(19f, 8f, 12f, 9f).build()

    OhIconName.Bolt -> Glyph()
        .polygon(13.5f, 2.5f, 5f, 13.5f, 11.5f, 13.5f, 10.5f, 21.5f, 19f, 10.5f, 12.5f, 10.5f)
        .build()
}
