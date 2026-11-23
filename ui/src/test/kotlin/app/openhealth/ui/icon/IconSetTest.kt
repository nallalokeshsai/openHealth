package app.openhealth.ui.icon

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The icon set is drawn, not imported (§10.1). This proves every named glyph exists as a path on
 * the 24 dp grid, so a missing `when` branch or an empty path cannot ship as a blank tab.
 */
class IconSetTest {

    @Test
    fun everyGlyphBuildsOnTheTwentyFourDpGrid() {
        for (name in OhIconName.entries) {
            val vector = iconVector(name, strokeWidth = 1.7f)
            assertEquals(24.dp, vector.defaultWidth, "$name: width")
            assertEquals(24.dp, vector.defaultHeight, "$name: height")
            assertTrue(vector.viewportWidth == 24f, "$name: viewport width")
            assertTrue(vector.viewportHeight == 24f, "$name: viewport height")
            assertTrue(vector.root.size > 0, "$name: no path")
        }
    }

    @Test
    fun theSetCoversEveryNavigationAndModuleIcon() {
        val required = listOf(
            OhIconName.Today, OhIconName.Train, OhIconName.Food, OhIconName.Body,
            OhIconName.Insights, OhIconName.More, OhIconName.Settings,
            OhIconName.Workout, OhIconName.Yoga, OhIconName.Steps, OhIconName.Weight,
            OhIconName.Heart, OhIconName.Sleep, OhIconName.Activity, OhIconName.Mood,
            OhIconName.Cycle, OhIconName.Family, OhIconName.Coach, OhIconName.Integrations,
            OhIconName.Add, OhIconName.Check, OhIconName.Close, OhIconName.Search,
            OhIconName.Scan, OhIconName.Timer, OhIconName.Delete, OhIconName.Undo,
            OhIconName.Trophy, OhIconName.Snowflake, OhIconName.Flame,
            OhIconName.Barbell, OhIconName.Dumbbell, OhIconName.Kettlebell, OhIconName.Bike
        )
        val drawn = OhIconName.entries.map { iconVector(it, 1.7f).name }.toSet()
        for (name in required) {
            assertTrue("OhIcon.${name.name}" in drawn, "$name is missing from the set")
        }
    }
}
