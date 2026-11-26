package app.openhealth.ui.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import app.openhealth.core.activity.DeviceKindName
import app.openhealth.ui.screens.devices.BluetoothModel
import app.openhealth.ui.screens.devices.DevicesModel
import app.openhealth.ui.screens.devices.DevicesScreen
import app.openhealth.ui.screens.devices.FoundDeviceModel
import app.openhealth.ui.screens.devices.GarminModel
import app.openhealth.ui.screens.devices.HealthConnectModel
import app.openhealth.ui.screens.devices.HealthProvideModel
import app.openhealth.ui.screens.devices.PairedDeviceModel
import app.openhealth.ui.screens.devices.PriorityModel
import app.openhealth.ui.screens.devices.PriorityRowModel
import app.openhealth.ui.screens.devices.StepsSourceModel
import app.openhealth.ui.screens.devices.WatchLinkModel
import app.openhealth.ui.theme.OhTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The B8 Devices page at full size in dark, light, 200 % text and RTL
 * (`DESIGN.md` §14.5). A grouped settings surface, so the goldens are the review of its
 * hairlines, inks and wrapping rather than of a chart.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-mdpi")
class DevicesScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private enum class Variant(
        val dir: String,
        val dark: Boolean,
        val scale: Float,
        val rtl: Boolean,
    ) {
        Dark("dark", true, 1f, false),
        Light("light", false, 1f, false),
        Text200("text200", true, 2f, false),
        Rtl("rtl", true, 1f, true),
    }

    private fun shot(name: String, content: @Composable () -> Unit) {
        val variant = mutableStateOf(Variant.Dark)
        compose.setContent {
            val current = variant.value
            val density = LocalDensity.current
            OhTheme(dark = current.dark) {
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale = current.scale),
                    LocalLayoutDirection provides if (current.rtl) {
                        LayoutDirection.Rtl
                    } else {
                        LayoutDirection.Ltr
                    },
                ) {
                    Box(
                        modifier = Modifier
                            .testTag(SHOT)
                            .fillMaxSize()
                            .background(OhTheme.colors.bg),
                    ) {
                        content()
                    }
                }
            }
        }
        Variant.entries.forEach { entry ->
            compose.runOnIdle { variant.value = entry }
            compose.waitForIdle()
            compose.onNodeWithTag(SHOT)
                .captureRoboImage("src/test/screenshots/${entry.dir}/$name.png")
        }
    }

    private fun model(empty: Boolean = false): DevicesModel = DevicesModel(
        steps = if (empty) {
            StepsSourceModel(
                available = false,
                tracking = false,
                moduleOff = false,
                reasonNote = null,
                todayText = "No step source yet",
                sourceLine = null,
                otherSources = emptyList(),
                lastReadingText = null,
                canCorrect = false,
                permissionNote = "This phone has no step counter.",
            )
        } else {
            StepsSourceModel(
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
            )
        },
        healthConnect = HealthConnectModel(
            available = true,
            enabled = true,
            grantedCount = 3,
            totalCount = 10,
            statusText = "3 of 10 data types shared with openHealth.",
            lastSyncText = "Last sync 09:12",
            provides = listOf(
                HealthProvideModel("Steps", true),
                HealthProvideModel("Heart rate", true),
                HealthProvideModel("Sleep", true),
                HealthProvideModel("Weight", false),
                HealthProvideModel("Body fat", false),
            ),
            historyImported = false,
            note = "Health Connect is read only here.",
            vendorNote = "Garmin Connect writes to Health Connect one way on Android 14 and later.",
        ),
        garmin = GarminModel(
            lastImportText = "Last import 08:40",
            lastImportDetail = "3,000 steps, one walk of 45 minutes, 45 heart-rate readings",
            stepsFrom = "Steps come from the workbook's own cycles.",
            note = "A FIT file is a file on your phone.",
            readOnlyNote = "Your Garmin data is read only.",
        ),
        bluetooth = BluetoothModel(
            available = true,
            needsPermission = false,
            scanning = false,
            devices = listOf(
                PairedDeviceModel(
                    id = "1",
                    name = "Polar H10",
                    kind = DeviceKindName.HEART_RATE,
                    kindLabel = "Heart-rate strap",
                    statusText = "Heart rate · Connected",
                    lastSeenText = "Seen 09:20",
                    providesText = "Heart rate",
                    autoConnect = true,
                ),
                PairedDeviceModel(
                    id = "2",
                    name = "Scale",
                    kind = DeviceKindName.SCALE,
                    kindLabel = "Scale",
                    statusText = "Weight and body composition · Not connected",
                    lastSeenText = null,
                    providesText = "Weight and body composition",
                    autoConnect = true,
                ),
            ),
            found = listOf(
                FoundDeviceModel(
                    "AA:BB",
                    "Cuff",
                    "Blood-pressure cuff",
                    DeviceKindName.BLOOD_PRESSURE,
                ),
            ),
            liveHeartRate = 62,
            note = "openHealth reads the standard profiles.",
        ),
        watchLink = WatchLinkModel(enabled = false, available = true, note = "A local link."),
        priority = PriorityModel(
            rows = listOf(
                PriorityRowModel("phone-steps", "This phone", "phone", true, false, false),
                PriorityRowModel("garmin-fit", "Garmin files", "Garmin", false, false, false),
                PriorityRowModel(
                    "health-connect",
                    "Health Connect",
                    "Health Connect",
                    false,
                    false,
                    false,
                ),
                PriorityRowModel("manual", "Manual", "You", false, true, true),
            ),
            typeLabel = "Steps priority",
            note = "One source wins a day and the others stay in your history.",
        ),
        note = "A source that is turned off hides its surfaces and keeps its data.",
        message = null,
    )

    @Test
    fun devices() {
        shot("screen-devices") {
            DevicesScreen(
                model = model(),
                onBack = {},
                onToggleSteps = {},
                onCorrectSteps = {},
                onExcludeToday = {},
                onRequestHealthConnect = {},
                onSyncHealthConnect = {},
                onImportGarmin = {},
                onScan = {},
                onStopScan = {},
                onPair = {},
                onUnpair = {},
                onConnect = {},
                onMovePriority = { _, _ -> },
                onToggleWatchLink = {},
            )
        }
    }

    @Test
    fun devicesEmpty() {
        shot("screen-devices-empty") {
            DevicesScreen(
                model = model(empty = true).copy(
                    healthConnect = model().healthConnect.copy(
                        available = false,
                        enabled = false,
                        grantedCount = 0,
                        statusText = "Health Connect is not on this phone.",
                        lastSyncText = null,
                        provides = emptyList(),
                    ),
                    bluetooth = model().bluetooth.copy(devices = emptyList(), found = emptyList()),
                ),
                onBack = {},
                onToggleSteps = {},
                onCorrectSteps = {},
                onExcludeToday = {},
                onRequestHealthConnect = {},
                onSyncHealthConnect = {},
                onImportGarmin = {},
                onScan = {},
                onStopScan = {},
                onPair = {},
                onUnpair = {},
                onConnect = {},
                onMovePriority = { _, _ -> },
                onToggleWatchLink = {},
            )
        }
    }

    private companion object {
        const val SHOT = "device-shot"
    }
}
