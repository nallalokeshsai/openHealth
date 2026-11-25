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
import app.openhealth.core.body.AdiposeInputs
import app.openhealth.core.body.AdiposeModel
import app.openhealth.core.body.BodySex
import app.openhealth.core.body.Muscle
import app.openhealth.core.body.MuscleRecoveryState
import app.openhealth.core.formula.ActivityLevel
import app.openhealth.core.formula.RecoveryStep
import app.openhealth.core.preferences.DisplayPolicy
import app.openhealth.core.preferences.HealthModule
import app.openhealth.core.preferences.ModulePreset
import app.openhealth.core.preferences.ProfileGoal
import app.openhealth.core.preferences.UnitSystem
import app.openhealth.ui.body.BodyMapModel
import app.openhealth.ui.body.OhBodyMapTable
import app.openhealth.ui.screens.common.OhScreen
import app.openhealth.ui.screens.lock.LockMessage
import app.openhealth.ui.screens.lock.LockScreen
import app.openhealth.ui.screens.onboarding.OnboardingAnswers
import app.openhealth.ui.screens.onboarding.OnboardingScreen
import app.openhealth.ui.screens.onboarding.OnboardingStep
import app.openhealth.ui.screens.settings.SettingsCounts
import app.openhealth.ui.screens.settings.SettingsScreen
import app.openhealth.ui.screens.settings.SettingsState
import app.openhealth.ui.screens.settings.ThemeChoice
import app.openhealth.ui.screens.settings.TrashItemModel
import app.openhealth.ui.screens.settings.TrashKind
import app.openhealth.ui.screens.settings.TrashScreen
import app.openhealth.ui.screens.timeline.TimelineFilter
import app.openhealth.ui.screens.timeline.TimelineModel
import app.openhealth.ui.screens.timeline.TimelineScreen
import app.openhealth.ui.screens.today.Greeting
import app.openhealth.ui.screens.today.TodayModel
import app.openhealth.ui.screens.today.TodayScreen
import app.openhealth.ui.screens.today.TodayStatus
import app.openhealth.ui.theme.OhAccent
import app.openhealth.ui.theme.OhTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The B3 screens at full size in the four variants the design contract requires: dark,
 * light, 200 % text and RTL. These are screen goldens, not component goldens; the B1
 * suite still covers each component in isolation.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-mdpi")
class ScreenScreenshotTest {
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

    private val today = TodayModel(
        today = LocalDate(2026, 11, 23),
        greeting = Greeting.MORNING,
        status = TodayStatus.NOTHING_LOGGED,
        noteCount = 0,
        waterMl = null,
        steps = null,
        energyKcal = null,
        weightKg = null,
        readinessScore = null,
        modules = HealthModule.entries.toSet(),
        demo = false,
    )

    private val settings = SettingsState(
        profileName = "Sam",
        profileSubtitle = "Just track · metric",
        profileBirthDate = "1990-04-12",
        profileGoal = ProfileGoal.JUST_TRACK,
        profileHeight = "170",
        profileWeight = "80",
        profileUnits = UnitSystem.METRIC,
        profileMayLoseWeight = true,
        hasRestoreSnapshot = false,
        modules = HealthModule.entries.toSet(),
        preset = ModulePreset.EVERYTHING,
        weightNeutral = false,
        displayPolicy = app.openhealth.core.preferences.DisplayPolicy.of(false),
        theme = ThemeChoice.SYSTEM,
        accent = OhAccent.Brand,
        appLock = false,
        appLockAvailable = true,
        demo = false,
        versionName = "0.1.0",
        counts = SettingsCounts(notes = 2, observations = 14, measurements = 1, trash = 0),
        busy = false,
        message = null,
    )

    @Composable
    private fun noopSettings() = SettingsScreen(
        state = settings,
        onBack = {},
        onSaveProfile = {},
        onToggleModule = { _, _ -> },
        onPreset = {},
        onWeightNeutral = {},
        onTheme = {},
        onAccent = {},
        onAppLock = {},
        onExport = { _, _ -> },
        onRestore = { _, _ -> },
        onOpenDevices = {},
        onOpenTimeline = {},
        onOpenTrash = {},
        onExternalActivity = {},
        onDeleteAllNotes = {},
        onEmptyTrash = {},
        onUndoRestore = {},
    )

    @Test
    fun todayEmpty() {
        shot("screen-today-empty") {
            TodayScreen(
                model = today,
                displayPolicy = DisplayPolicy.Standard,
                onQuickAdd = {},
                onOpenTimeline = {},
                onOpenTrain = {},
                onOpenNutrition = {},
            )
        }
    }

    private val bodyMap = BodyMapModel.recovery(
        sex = BodySex.MALE,
        states = mapOf(
            Muscle.CHEST to MuscleRecoveryState(
                Muscle.CHEST,
                RecoveryStep.RECOVERING,
                hoursSinceLoad = 6.0,
                sets = 4.0,
            ),
            Muscle.QUADS to MuscleRecoveryState(
                Muscle.QUADS,
                RecoveryStep.EASING,
                hoursSinceLoad = 30.0,
                sets = 5.0,
            ),
            Muscle.GLUTES to MuscleRecoveryState(
                Muscle.GLUTES,
                RecoveryStep.READY,
                hoursSinceLoad = 60.0,
                sets = 3.0,
            ),
        ),
        adipose = AdiposeModel.compute(AdiposeInputs(BodySex.MALE, bodyFatPercent = 22.0)),
        fatProvenance = app.openhealth.ui.body.FatProvenance(
            bodyFatPercent = 22.4,
            method = app.openhealth.core.observation.MeasurementMethod.BIA,
            sourceLabel = "Your scale",
            tapeSites = listOf("waist", "hips"),
        ),
    )

    @Test
    fun todayWithABodyMap() {
        shot("screen-today-body") {
            TodayScreen(
                model = today.copy(status = TodayStatus.LOGGED_TODAY, bodyMap = bodyMap),
                displayPolicy = DisplayPolicy.Standard,
                onQuickAdd = {},
                onOpenTimeline = {},
                onOpenTrain = {},
                onOpenNutrition = {},
                synchronousLayers = true,
            )
        }
    }

    /**
     * The map's table alternative (`DESIGN.md` §8.6). It is goldened in its own right:
     * the card renders only the figure, so a hairline or a 200 % wrap in the table is
     * invisible to every other shot.
     */
    @Test
    fun bodyMapTableAlternative() {
        shot("screen-body-table") {
            OhScreen {
                OhBodyMapTable(model = bodyMap)
            }
        }
    }

    @Test
    fun todayWeightNeutral() {
        shot("screen-today-weight-neutral") {
            TodayScreen(
                model = today.copy(status = TodayStatus.WEIGHT_NEUTRAL),
                displayPolicy = DisplayPolicy.WeightNeutral,
                onQuickAdd = {},
                onOpenTimeline = {},
                onOpenTrain = {},
                onOpenNutrition = {},
            )
        }
    }

    @Test
    fun onboardingName() {
        shot("screen-onboarding-name") {
            OnboardingScreen(
                step = OnboardingStep.Name,
                answers = OnboardingAnswers(
                    units = UnitSystem.METRIC,
                    goal = ProfileGoal.JUST_TRACK,
                    activity = ActivityLevel.MODERATELY_ACTIVE,
                ),
                issue = null,
                ageBlocked = false,
                weightLossAllowed = true,
                onChange = {},
                onBack = {},
                onNext = {},
                onSkip = {},
                onTryDemo = {},
            )
        }
    }

    @Test
    fun onboardingWeightNeutral() {
        shot("screen-onboarding-weight-neutral") {
            OnboardingScreen(
                step = OnboardingStep.WeightNeutral,
                answers = OnboardingAnswers(
                    name = "Sam",
                    birthDate = "1990-04-12",
                    units = UnitSystem.METRIC,
                    goal = ProfileGoal.JUST_TRACK,
                ),
                issue = null,
                ageBlocked = false,
                weightLossAllowed = true,
                onChange = {},
                onBack = {},
                onNext = {},
                onSkip = {},
                onTryDemo = {},
            )
        }
    }

    @Test
    fun settings() {
        shot("screen-settings") { noopSettings() }
    }

    @Test
    fun timelineEmpty() {
        shot("screen-timeline-empty") {
            TimelineScreen(
                model = TimelineModel(days = emptyList(), filter = TimelineFilter.ALL, total = 0),
                onBack = {},
                onFilter = {},
                onDelete = {},
                onExclude = {},
                onLogNote = {},
                onLoadMore = {},
            )
        }
    }

    @Test
    fun trashOneNote() {
        shot("screen-trash") {
            TrashScreen(
                items = listOf(
                    TrashItemModel(
                        id = "t-1",
                        kind = TrashKind.NOTE,
                        detail = "Knee felt fine",
                        deletedLabel = "23 Nov 2026",
                    ),
                ),
                onBack = {},
                onRestore = {},
                onPurge = {},
            )
        }
    }

    @Test
    fun lock() {
        shot("screen-lock") {
            LockScreen(message = LockMessage.NONE, onUnlock = {})
        }
    }

    private companion object {
        const val SHOT = "screen"
    }
}
