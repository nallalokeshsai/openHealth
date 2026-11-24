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
import app.openhealth.core.body.BodySex
import app.openhealth.core.body.Muscle
import app.openhealth.core.body.MuscleRecoveryState
import app.openhealth.core.exercise.MusclePlan
import app.openhealth.core.formula.RecoveryStep
import app.openhealth.core.training.PlanReason
import app.openhealth.core.training.ProgressionAction
import app.openhealth.core.training.ProgressionReason
import app.openhealth.core.training.RoutineSchedule
import app.openhealth.ui.body.BodyMapModel
import app.openhealth.ui.screens.train.EditorExerciseRow
import app.openhealth.ui.screens.train.ExerciseHistoryModel
import app.openhealth.ui.screens.train.ExerciseHistoryRow
import app.openhealth.ui.screens.train.ExerciseHistoryScreen
import app.openhealth.ui.screens.train.LiveExerciseModel
import app.openhealth.ui.screens.train.LiveSessionModel
import app.openhealth.ui.screens.train.LiveSessionScreen
import app.openhealth.ui.screens.train.LiveSetKind
import app.openhealth.ui.screens.train.LiveSetRow
import app.openhealth.ui.screens.train.OpenSessionCard
import app.openhealth.ui.screens.train.RestDisplay
import app.openhealth.ui.screens.train.RoutineDetailModel
import app.openhealth.ui.screens.train.RoutineDetailScreen
import app.openhealth.ui.screens.train.RoutineEditorModel
import app.openhealth.ui.screens.train.RoutineEditorScreen
import app.openhealth.ui.screens.train.RoutineExerciseRow
import app.openhealth.ui.screens.train.SessionSummaryModel
import app.openhealth.ui.screens.train.SessionSummaryScreen
import app.openhealth.ui.screens.train.TrainModel
import app.openhealth.ui.screens.train.TrainRootScreen
import app.openhealth.ui.screens.train.TrainingDay
import app.openhealth.ui.screens.train.TrainingHistoryRow
import app.openhealth.ui.screens.train.TrainingPlanCard
import app.openhealth.ui.screens.train.TrainingRoutineRow
import app.openhealth.ui.theme.OhTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The B5 Train surfaces in dark, light, 200 % text and RTL (`DESIGN.md` §14.5). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-mdpi")
class TrainScreenshotTest {
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

    private val recovery = BodyMapModel.recovery(
        sex = BodySex.MALE,
        states = Muscle.entries.associateWith { muscle ->
            MuscleRecoveryState(
                muscle = muscle,
                step = if (muscle ==
                    Muscle.QUADS
                ) {
                    RecoveryStep.RECOVERING
                } else {
                    RecoveryStep.UNTRAINED
                },
                hoursSinceLoad = if (muscle == Muscle.QUADS) 6.0 else null,
            )
        },
    )

    private val plan = BodyMapModel.plan(
        sex = BodySex.MALE,
        coverage = mapOf(
            Muscle.QUADS to MusclePlan(Muscle.QUADS, 9.0, 1.0),
            Muscle.GLUTES to MusclePlan(Muscle.GLUTES, 6.0, 0.66),
            Muscle.HAMSTRINGS to MusclePlan(Muscle.HAMSTRINGS, 4.5, 0.5),
        ),
    )

    private val train = TrainModel(
        today = today,
        week = (0..6).map { index ->
            TrainingDay(
                date = today.plusDays(index - 1),
                isToday = index == 1,
                hasSession = index == 1 || index == 3,
            )
        },
        plan = TrainingPlanCard(
            routineId = "legs",
            name = "Leg day",
            reason = PlanReason.WEEKDAY,
            setCount = 12,
            map = plan,
        ),
        recovery = recovery,
        planMap = plan,
        planUsesCycle = false,
        routines = listOf(
            TrainingRoutineRow(
                id = "legs",
                name = "Leg day",
                schedule = RoutineSchedule.WEEKDAY,
                weekday = 2,
                rotationIndex = null,
                folder = "Strength",
                setCount = 12,
                favourite = true,
                map = plan,
            ),
        ),
        history = listOf(
            TrainingHistoryRow(
                sessionId = "s1",
                title = "Leg day",
                date = today.minusDays(3),
                sets = 12,
                volumeKg = 5_400.0,
                map = plan,
            ),
        ),
        openSession = OpenSessionCard("s2", "Leg day", 0L, 4),
        demo = false,
    )

    private val live = LiveSessionModel(
        sessionId = "s2",
        title = "Leg day",
        startedAt = 0L,
        localDate = today,
        backdated = false,
        exercises = listOf(
            LiveExerciseModel(
                id = "e1",
                exerciseId = "squat",
                name = "Barbell Squat",
                equipmentKey = "barbell",
                muscles = "Quadriceps, Glutes",
                target = "3 × 8-12",
                lastTime = "80 kg × 8",
                progressionKey = "linear",
                incrementKg = 2.5,
                action = ProgressionAction.ADD_LOAD,
                progressionReason = ProgressionReason.COMPLETED_ALL_REPS,
                nextWeight = "82.5",
                nextReps = "8",
                notes = "Belt on for the last set.",
                groupIndex = null,
                sets = listOf(
                    LiveSetRow(
                        id = "set1",
                        label = "W1",
                        weight = "40",
                        reps = "8",
                        rir = "3",
                        kind = LiveSetKind.WARMUP,
                        done = true,
                    ),
                    LiveSetRow(
                        id = null,
                        label = "1",
                        weight = "82.5",
                        reps = "8",
                        rir = null,
                        kind = LiveSetKind.WORK,
                        done = false,
                    ),
                ),
            ),
        ),
        rest = RestDisplay(remainingSeconds = 72, totalSeconds = 120, paused = false),
        editing = false,
        note = "",
        feeling = "",
    )

    private val summary = SessionSummaryModel(
        sessionId = "s1",
        title = "Leg day",
        date = today,
        duration = "58:00",
        volume = "5,400 kg",
        sets = "12",
        prs = listOf(
            app.openhealth.core.training.PersonalRecordKind.HEAVIEST_WEIGHT,
            app.openhealth.core.training.PersonalRecordKind.ESTIMATED_ONE_REP_MAX,
        ),
        map = plan,
        topMuscles = listOf(Muscle.QUADS to 9.0, Muscle.GLUTES to 6.0),
        feeling = "Steady",
        note = "Knees felt fine.",
        sourceLabel = "Manual",
        happenedAt = kotlin.time.Instant.fromEpochMilliseconds(0),
        recordedAt = kotlin.time.Instant.fromEpochMilliseconds(0),
        localDay = today,
        zoneId = "Europe/Berlin",
        device = "manual",
    )

    private val routine = RoutineDetailModel(
        id = "legs",
        name = "Leg day",
        folder = "Strength",
        schedule = RoutineSchedule.WEEKDAY,
        weekday = 2,
        rotationIndex = null,
        progressionKey = "linear",
        notes = "Two warm-up sets, then three work sets.",
        favourite = true,
        map = plan,
        weeklySets = 12.0,
        exercises = listOf(
            RoutineExerciseRow(
                entryId = "r1",
                exerciseId = "squat",
                name = "Barbell Squat",
                muscles = "Quadriceps, Glutes",
                target = "3 × 8-12 @ 80 kg",
                rest = "120 s",
                groupIndex = null,
                notes = null,
            ),
        ),
        lastTrained = today.minusDays(3),
    )

    private val editor = RoutineEditorModel(
        id = null,
        name = "Leg day",
        folder = "Strength",
        schedule = RoutineSchedule.WEEKDAY,
        weekday = 2,
        rotationIndex = 1,
        favourite = true,
        progressionKey = "linear",
        notes = "",
        exercises = listOf(
            EditorExerciseRow(
                id = "new-1",
                exerciseId = "squat",
                name = "Barbell Squat",
                muscles = "Quadriceps, Glutes",
                sets = "3",
                reps = "8-12",
                weight = "80",
                rest = "120",
            ),
        ),
        library = emptyList(),
    )

    private val exerciseHistory = ExerciseHistoryModel(
        exerciseId = "squat",
        name = "Barbell Squat",
        muscles = "Quadriceps, Glutes",
        bestOneRepMax = "112.5 kg",
        progressionKey = "linear",
        incrementKg = 2.5,
        nextWeight = 82.5,
        nextReps = 8,
        rows = listOf(
            ExerciseHistoryRow(
                date = today.minusDays(3),
                sets = "3",
                topSet = "80 kg × 8",
                volume = "1,920 kg",
                estimatedOneRepMax = "101.3 kg",
            ),
        ),
    )

    @Test
    fun trainRoot() = shot("train-root") {
        TrainRootScreen(
            model = train,
            onStartPlan = {},
            onStartFreestyle = {},
            onStartPast = {},
            onResume = {},
            onOpenRoutine = {},
            onNewRoutine = {},
            onOpenSession = {},
            onOpenSettings = {},
        )
    }

    @Test
    fun liveSession() = shot("train-live") {
        LiveSessionScreen(
            model = live,
            now = 0L,
            onBack = {},
            onFinish = {},
            onLogSet = {},
            onUpdateSet = { _, _ -> },
            onDeleteSet = {},
            onAddExercise = {},
            onExerciseNotes = { _, _ -> },
            onSessionNote = {},
            onSuperset = { _, _ -> },
            onSaveEquipment = {},
            onRestMinus = {},
            onRestPlus = {},
            onRestPause = {},
            onRestSkip = {},
        )
    }

    @Test
    fun sessionSummary() = shot("train-summary") {
        SessionSummaryScreen(
            model = summary,
            onBack = {},
            onEdit = {},
            onMove = { _, _ -> },
            onDelete = {},
            onSaveNote = { _, _ -> },
        )
    }

    @Test
    fun routineDetail() = shot("train-routine") {
        RoutineDetailScreen(
            model = routine,
            onBack = {},
            onStart = {},
            onEdit = {},
            onFavourite = { _, _ -> },
            onExercise = {},
        )
    }

    @Test
    fun routineEditor() = shot("train-editor") {
        RoutineEditorScreen(model = editor, onBack = {}, onSave = {})
    }

    @Test
    fun exerciseHistory() = shot("train-exercise-history") {
        ExerciseHistoryScreen(model = exerciseHistory, onBack = {})
    }

    private fun LocalDate.plusDays(days: Int): LocalDate =
        plus(kotlinx.datetime.DatePeriod(days = days))

    private fun LocalDate.minusDays(days: Int): LocalDate =
        plus(kotlinx.datetime.DatePeriod(days = -days))

    private companion object {
        const val SHOT = "shot"
    }
}
