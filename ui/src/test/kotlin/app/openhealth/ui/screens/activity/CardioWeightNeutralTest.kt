package app.openhealth.ui.screens.activity

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.openhealth.ui.theme.OhTheme
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Weight-neutral mode hides calories everywhere, not only on Today and in Nutrition
 * (`pr.md` §6.25, `DESIGN.md` §7.6). The cardio form is one of the places a calorie can
 * be typed, so the field goes with the switch and a stale value never reaches the record.
 * Nothing else about the form changes: duration, distance and heart rate stay.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardioWeightNeutralTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate(2026, 11, 26)

    @Test
    fun theEnergyFieldIsThereInStandardMode() {
        compose.setContent {
            OhTheme(dark = true) { CardioLogScreen(date = today, onBack = {}, onSave = {}) }
        }
        compose.onNodeWithText("Energy (kcal)").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun weightNeutralModeHidesTheEnergyFieldAndKeepsTheRest() {
        compose.setContent {
            OhTheme(dark = true) {
                CardioLogScreen(date = today, onBack = {}, onSave = {}, showEnergy = false)
            }
        }
        compose.onNodeWithText("Energy (kcal)").assertDoesNotExist()
        compose.onNodeWithText("Distance (km)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Average heart rate (bpm)").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aSavedEntryCarriesNoEnergyWhenTheModeHidesIt() {
        var saved: CardioEntry? = null
        compose.setContent {
            OhTheme(dark = true) {
                CardioLogScreen(
                    date = today,
                    onBack = {},
                    onSave = { saved = it },
                    showEnergy = false,
                )
            }
        }
        compose.onNodeWithText("Save session").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(today, saved?.date)
        assertNull(saved?.energyKcal, "weight-neutral mode must not record a calorie")
    }
}
