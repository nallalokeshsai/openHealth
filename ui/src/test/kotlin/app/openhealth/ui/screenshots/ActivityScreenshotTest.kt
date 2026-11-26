package app.openhealth.ui.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import app.openhealth.core.activity.ActivityType
import app.openhealth.core.activity.BreathPattern
import app.openhealth.core.activity.YogaLibrary
import app.openhealth.core.activity.YogaPlayer
import app.openhealth.ui.components.ChartTableModel
import app.openhealth.ui.screens.activity.ActivityDetailModel
import app.openhealth.ui.screens.activity.ActivityDetailScreen
import app.openhealth.ui.screens.activity.ActivityHistoryModel
import app.openhealth.ui.screens.activity.ActivityListScreen
import app.openhealth.ui.screens.activity.ActivityRowModel
import app.openhealth.ui.screens.activity.ActivitySplitRow
import app.openhealth.ui.screens.activity.ActivityZoneRow
import app.openhealth.ui.screens.activity.CardioLogScreen
import app.openhealth.ui.screens.activity.GpsRecordScreen
import app.openhealth.ui.screens.activity.GpsRecordingModel
import app.openhealth.ui.screens.activity.MapPackModel
import app.openhealth.ui.screens.activity.MapPacksModel
import app.openhealth.ui.screens.activity.MapPacksScreen
import app.openhealth.ui.screens.activity.YogaFinishScreen
import app.openhealth.ui.screens.activity.YogaFlowRow
import app.openhealth.ui.screens.activity.YogaLibraryModel
import app.openhealth.ui.screens.activity.YogaLibraryScreen
import app.openhealth.ui.screens.activity.YogaPlayerScreen
import app.openhealth.ui.screens.activity.YogaPoseRow
import app.openhealth.ui.theme.OhTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The B11 movement surfaces in dark, light, 200 % text and RTL. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-mdpi")
class ActivityScreenshotTest {
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

    private val today = LocalDate(2026, 11, 24)

    private val history = ActivityHistoryModel(
        rows = listOf(
            ActivityRowModel(
                id = "a1",
                title = "Morning loop",
                subtitle = "24 Nov 2026 · Recorded · Recorded",
                distanceText = "5.20 km",
                durationText = "31:24",
                sourceLabel = "Recorded",
                hasRoute = true,
                type = ActivityType.RUN,
            ),
            ActivityRowModel(
                id = "a2",
                title = "Evening ride",
                subtitle = "22 Nov 2026 · Imported · Imported",
                distanceText = "18.40 km",
                durationText = "52:10",
                sourceLabel = "Imported",
                hasRoute = true,
                type = ActivityType.RIDE,
            ),
            ActivityRowModel(
                id = "a3",
                title = "Hip opener",
                subtitle = "21 Nov 2026 · Manual · Manual",
                distanceText = null,
                durationText = "14:02",
                sourceLabel = "Manual",
                hasRoute = false,
                type = ActivityType.YOGA,
            ),
        ),
        weekCount = 3,
        weekMinutes = 97,
        weekDistanceText = "23.60 km",
        note = "Recorded, imported and manually entered sessions stay separate.",
    )

    private val detail = ActivityDetailModel(
        id = "a1",
        title = "Morning loop",
        typeLabel = "Run",
        sourceLabel = "Recorded",
        dateText = "24 Nov 2026",
        startedText = "08:02",
        durationText = "31:24",
        distanceText = "5.20 km",
        paceText = "6:02 /km",
        elevationText = "48 m up",
        heartRateText = "148 bpm",
        energyText = "402 kcal",
        splits = listOf(
            ActivitySplitRow(1, "1.00 km", "6:01 /km", "12 m up", "142 bpm"),
            ActivitySplitRow(2, "1.00 km", "6:04 /km", "10 m up", "146 bpm"),
            ActivitySplitRow(3, "1.00 km", "5:58 /km", "9 m up", "151 bpm"),
            ActivitySplitRow(4, "1.00 km", "6:00 /km", "8 m up", "153 bpm"),
            ActivitySplitRow(5, "1.00 km", "6:07 /km", "9 m up", "152 bpm"),
            ActivitySplitRow(6, "0.20 km", "1:14 /km", "0 m up", "149 bpm"),
        ),
        zones = listOf(
            ActivityZoneRow("Zone 2", "114–132 bpm", "12:40", 0.40f),
            ActivityZoneRow("Zone 3", "133–152 bpm", "15:10", 0.48f),
            ActivityZoneRow("Zone 4", "153–171 bpm", "3:34", 0.12f),
        ),
        zoneNote = "Zones use the maximum of 188 bpm seen in this activity.",
        table = ChartTableModel(
            title = "Heart-rate zones",
            columns = listOf("Zone", "Range", "Time", "Share"),
            rows = listOf(
                listOf("Zone 2", "114–132 bpm", "12:40", "40 %"),
                listOf("Zone 3", "133–152 bpm", "15:10", "48 %"),
                listOf("Zone 4", "153–171 bpm", "3:34", "12 %"),
            ),
            citation = "Edwards 1993",
        ),
        route = emptyList(),
        notes = "Easy effort, cool morning.",
        provenance = emptyList(),
    )

    private val yogaLibrary = YogaLibraryModel(
        flows = YogaLibrary.flows.map { flow ->
            YogaFlowRow(
                id = flow.id,
                name = flow.name,
                subtitle = flow.subtitle,
                levelLabel = "Beginner",
                focus = flow.focus,
                minutes = flow.minutes,
                poseCount = flow.poseCount,
                favourite = flow.id == "post-run",
            )
        },
        poses = YogaLibrary.poses.take(8).map { pose ->
            YogaPoseRow(
                id = pose.id,
                name = pose.name,
                sanskrit = pose.sanskrit,
                levelLabel = "Beginner",
                muscleLabels = pose.primaryMuscles.map { it.key.replace('-', ' ') },
                why = pose.why,
                modification = pose.modification,
                contraindications = pose.contraindications,
                instructions = pose.setup,
                breathCue = pose.breathCue,
            )
        },
        note = "Eight flows and their poses, written for openHealth.",
    )

    private val yogaFlow = YogaLibrary.flow("sun-salutation-a")!!

    private val yogaState = YogaPlayer.tick(yogaFlow, YogaPlayer.start(yogaFlow), 70)

    @Test
    fun activityHistory() = shot("activity-list") {
        ActivityListScreen(history, onBack = {}, onOpen = {}, onImport = {})
    }

    @Test
    fun activityDetail() = shot("activity-detail") {
        ActivityDetailScreen(
            model = detail,
            onBack = {},
            onExport = {},
            onDelete = {},
            onOpenMapPacks = {},
        )
    }

    @Test
    fun cardioForm() = shot("activity-cardio") {
        CardioLogScreen(date = today, onBack = {}, onSave = {})
    }

    @Test
    fun yogaLibraryScreen() = shot("yoga-library") {
        YogaLibraryScreen(yogaLibrary, onBack = {}, onStart = {}, onFavourite = { _, _ -> })
    }

    @Test
    fun yogaPlayer() = shot("yoga-player") {
        YogaPlayerScreen(
            flow = yogaFlow,
            state = yogaState,
            breath = BreathPattern.CALM,
            voiceOn = true,
            onPause = {},
            onSkip = {},
            onPrevious = {},
            onRepeat = {},
            onFinish = {},
            onVoice = {},
            synchronousLayers = true,
        )
    }

    @Test
    fun yogaFinish() = shot("yoga-finish") {
        YogaFinishScreen(
            flowName = "Post-run",
            minutes = 14,
            posesHeld = 9,
            muscleLabels = listOf("Hamstrings", "Glutes", "Lower back"),
            onDone = {},
        )
    }

    @Test
    fun gpsIdle() = shot("activity-gps-idle") {
        GpsRecordScreen(
            recording = null,
            onBack = {},
            onStart = {},
            onPause = {},
            onResume = {},
            onFinish = {},
        )
    }

    @Test
    fun gpsRecording() = shot("activity-gps-recording") {
        GpsRecordScreen(
            recording = GpsRecordingModel(
                type = ActivityType.RUN,
                elapsedSeconds = 734,
                distanceM = 2_180.0,
                paused = false,
                hasFix = true,
            ),
            onBack = {},
            onStart = {},
            onPause = {},
            onResume = {},
            onFinish = {},
        )
    }

    @Test
    fun mapPacks() = shot("map-packs") {
        MapPacksScreen(
            model = MapPacksModel(
                packs = listOf(
                    MapPackModel(
                        name = "Monaco",
                        sizeText = "462 KB",
                        installedText = "24 Nov 2026",
                    ),
                ),
                note = "A map pack is a single PMTiles file built from OpenStreetMap data.",
            ),
            onBack = {},
            onDownload = { _, _ -> },
            onDelete = {},
        )
    }

    private companion object {
        const val SHOT = "shot"
    }
}
