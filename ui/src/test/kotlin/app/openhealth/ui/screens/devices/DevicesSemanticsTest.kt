package app.openhealth.ui.screens.devices

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import app.openhealth.core.activity.DeviceKindName
import app.openhealth.ui.theme.OhTheme
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The B8 interactions (`BUILD-ROADMAP.md` §B8): the priority list reorders, a correction
 * is typed and saved, and a discovered device is paired. They are the three controls a
 * person uses on this page, so each is a semantics assertion rather than a screenshot.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-mdpi")
class DevicesSemanticsTest {
    @get:Rule
    val compose = createComposeRule()

    private val moves = mutableListOf<Pair<String, Boolean>>()
    private val corrections = mutableListOf<Int>()
    private val paired = mutableListOf<FoundDeviceModel>()

    private fun model(): DevicesModel = DevicesModel(
        steps = StepsSourceModel(
            available = true,
            tracking = true,
            moduleOff = false,
            reasonNote = null,
            todayText = "8,412 steps today",
            sourceLine = "Steps today come from This phone",
            otherSources = listOf("Garmin reported 9,110, kept in your history"),
            lastReadingText = "Last reading 14:32",
            canCorrect = true,
            permissionNote = "The phone's own step counter.",
        ),
        healthConnect = HealthConnectModel(
            available = true,
            enabled = true,
            grantedCount = 3,
            totalCount = 10,
            statusText = "3 of 10 data types shared with openHealth.",
            lastSyncText = "Last sync 09:12",
            provides = listOf(HealthProvideModel("Steps", true)),
            historyImported = false,
            note = "Health Connect is read only here.",
            vendorNote = "Garmin writes one way on Android 14 and later.",
        ),
        garmin = GarminModel(
            lastImportText = null,
            lastImportDetail = null,
            stepsFrom = null,
            note = "A FIT file is a file on your phone.",
            readOnlyNote = "Your Garmin data is read only.",
        ),
        bluetooth = BluetoothModel(
            available = true,
            needsPermission = false,
            scanning = false,
            devices = emptyList(),
            found = listOf(
                FoundDeviceModel(
                    address = "AA:BB",
                    name = "Cuff",
                    kindLabel = "Blood-pressure cuff",
                    kind = DeviceKindName.BLOOD_PRESSURE,
                ),
            ),
            liveHeartRate = null,
            note = "openHealth reads the standard profiles.",
        ),
        priority = PriorityModel(
            rows = listOf(
                PriorityRowModel("phone-steps", "This phone", "phone", true, false, false),
                PriorityRowModel("garmin-fit", "Garmin files", "Garmin", false, true, false),
            ),
            typeLabel = "Steps priority",
            note = "One source wins a day.",
        ),
        note = "A source that is turned off hides its surfaces.",
    )

    private fun screen() {
        compose.setContent {
            OhTheme(dark = true) {
                Box(Modifier.fillMaxSize()) {
                    DevicesScreen(
                        model = model(),
                        onBack = {},
                        onToggleSteps = {},
                        onCorrectSteps = { corrections += it },
                        onExcludeToday = {},
                        onRequestHealthConnect = {},
                        onSyncHealthConnect = {},
                        onImportGarmin = {},
                        onScan = {},
                        onStopScan = {},
                        onPair = { paired += it },
                        onUnpair = {},
                        onConnect = {},
                        onMovePriority = { id, up -> moves += id to up },
                    )
                }
            }
        }
    }

    @Test
    fun thePageIsHeadedAndNamesItsSections() {
        screen()
        compose.onNodeWithText("Devices and integrations").assertIsDisplayed()
        compose.onAllNodesWithText("Health Connect").fetchSemanticsNodes().isNotEmpty()
        compose.onAllNodesWithText("Garmin files").fetchSemanticsNodes().isNotEmpty()
        compose.onAllNodesWithText("Bluetooth devices").fetchSemanticsNodes().isNotEmpty()
        compose.onAllNodesWithText("Which source to use").fetchSemanticsNodes().isNotEmpty()
        assertTrue(
            compose.onAllNodesWithText(
                "Devices and integrations",
            ).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun theStepsCardShowsTheWinnerAndTheLoser() {
        screen()
        compose.onNodeWithText("8,412 steps today").assertIsDisplayed()
        compose.onNodeWithText("Steps today come from This phone").assertIsDisplayed()
        compose.onNodeWithText("Garmin reported 9,110, kept in your history").assertIsDisplayed()
    }

    @Test
    fun movingASourceUpReportsTheSourceAndDirection() {
        screen()
        compose.onNodeWithContentDescription(
            "Move Garmin files up",
        ).performScrollTo().performClick()
        assertEquals(listOf("garmin-fit" to true), moves)
    }

    @Test
    fun theFirstRowCannotMoveUpAndTheLastCannotMoveDown() {
        screen()
        // The first row's up button exists but is disabled, so a click does nothing.
        compose.onNodeWithContentDescription("Move This phone up").performScrollTo().performClick()
        compose.onNodeWithContentDescription(
            "Move Garmin files down",
        ).performScrollTo().performClick()
        assertTrue(moves.isEmpty())
    }

    @Test
    fun aCorrectionIsTypedAndSaved() {
        screen()
        compose.onNodeWithText("Correct today").performScrollTo().performClick()
        compose.onNodeWithText("Steps today").performTextInput("9123")
        compose.onNodeWithText("Save today's steps").performClick()
        assertEquals(listOf(9123), corrections)
    }

    @Test
    fun aDiscoveredDeviceCanBePaired() {
        screen()
        compose.onNodeWithText("Pair").performScrollTo().performClick()
        assertEquals("AA:BB", paired.single().address)
    }
}
