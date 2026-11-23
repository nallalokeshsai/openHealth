package app.openhealth.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.openhealth.ui.theme.OhTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The screenshot proof for B1 (BUILD-ROADMAP.md B1 item 6): every gallery entry in dark, light,
 * 200 % font and RTL, on a phone-sized surface. Golden images live in `src/test/screenshots`.
 *
 * Run with `./gradlew :ui:recordRoborazziDebug` to record and `:ui:verifyRoborazziDebug` to
 * verify. Under a plain `check` the test still renders every entry, but does not touch the goldens.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class ComponentGalleryScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private data class Variant(val name: String, val dark: Boolean, val fontScale: Float, val rtl: Boolean)

    private val variants = listOf(
        Variant("dark", dark = true, fontScale = 1f, rtl = false),
        Variant("light", dark = false, fontScale = 1f, rtl = false),
        Variant("dark-200", dark = true, fontScale = 2f, rtl = false),
        Variant("dark-rtl", dark = true, fontScale = 1f, rtl = true)
    )

    private val recording: Boolean =
        System.getProperty("roborazzi.test.record") == "true" ||
            System.getProperty("roborazzi.test.verify") == "true" ||
            System.getProperty("roborazzi.test.compare") == "true"

    @Test
    fun card() = capture(GalleryEntry.Card)

    @Test
    fun insetGroup() = capture(GalleryEntry.InsetGroup)

    @Test
    fun mediaRow() = capture(GalleryEntry.MediaRow)

    @Test
    fun statTile() = capture(GalleryEntry.StatTile)

    @Test
    fun kpiBand() = capture(GalleryEntry.KpiBand)

    @Test
    fun buttons() = capture(GalleryEntry.Buttons)

    @Test
    fun buttonsPressed() = capturePressed(GalleryEntry.Buttons, "Start session", "Buttons-pressed")

    @Test
    fun iconButton() = capture(GalleryEntry.IconButton)

    @Test
    fun chips() = capture(GalleryEntry.Chips)

    @Test
    fun chipsPressed() = capturePressed(GalleryEntry.Chips, "Fat layer", "Chips-pressed")

    @Test
    fun segments() = capture(GalleryEntry.Segments)

    @Test
    fun switch() = capture(GalleryEntry.Switch)

    @Test
    fun stepper() = capture(GalleryEntry.Stepper)

    @Test
    fun slider() = capture(GalleryEntry.Slider)

    @Test
    fun fields() = capture(GalleryEntry.Fields)

    @Test
    fun topBar() = capture(GalleryEntry.TopBar)

    @Test
    fun tabBar() = capture(GalleryEntry.TabBar)

    @Test
    fun sheets() = capture(GalleryEntry.Sheets)

    @Test
    fun dialogs() = capture(GalleryEntry.Dialogs)

    @Test
    fun toasts() = capture(GalleryEntry.Toasts)

    @Test
    fun timers() = capture(GalleryEntry.Timers)

    @Test
    fun icons() = capture(GalleryEntry.Icons)

    @Test
    fun states() = capture(GalleryEntry.States)

    private fun capture(entry: GalleryEntry) {
        var variant by mutableStateOf(variants.first())
        compose.setContent {
            OhTheme(darkTheme = variant.dark) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, variant.fontScale),
                    LocalLayoutDirection provides
                        if (variant.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(OhTheme.colors.bg)
                            .padding(OhTheme.space.space6)
                    ) {
                        GalleryEntryContent(entry)
                    }
                }
            }
        }
        for (each in variants) {
            variant = each
            compose.waitForIdle()
            if (recording) {
                compose.onNodeWithTag("gallery-entry")
                    .captureRoboImage("src/test/screenshots/${entry.name}-${each.name}.png")
            }
        }
    }

    private fun capturePressed(entry: GalleryEntry, label: String, file: String) {
        var variant by mutableStateOf(variants.first())
        compose.setContent {
            OhTheme(darkTheme = variant.dark) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(OhTheme.colors.bg)
                        .padding(OhTheme.space.space6)
                        .testTag("pressed-root")
                ) {
                    GalleryEntryContent(entry)
                }
            }
        }
        compose.waitForIdle()
        variant = variants.first()
        compose.waitForIdle()
        compose.onNodeWithText(label).performTouchInput { down(center) }
        compose.waitForIdle()
        if (recording) {
            compose.onNodeWithTag("gallery-entry")
                .captureRoboImage("src/test/screenshots/$file.png")
        }
    }
}
