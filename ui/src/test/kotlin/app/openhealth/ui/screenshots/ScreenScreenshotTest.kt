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
import app.openhealth.core.ai.CoachCategory
import app.openhealth.core.ai.CoachProviderKind
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
import app.openhealth.ui.screens.clinical.ClinicalEditorModel
import app.openhealth.ui.screens.clinical.ClinicalEditorScreen
import app.openhealth.ui.screens.clinical.ClinicalFieldModel
import app.openhealth.ui.screens.clinical.ClinicalFieldType
import app.openhealth.ui.screens.clinical.ClinicalKind
import app.openhealth.ui.screens.clinical.ClinicalModel
import app.openhealth.ui.screens.clinical.ClinicalRowModel
import app.openhealth.ui.screens.clinical.ClinicalScreen
import app.openhealth.ui.screens.clinical.ClinicalSectionKind
import app.openhealth.ui.screens.clinical.ClinicalSectionModel
import app.openhealth.ui.screens.clinical.ReportRangeModel
import app.openhealth.ui.screens.clinical.ReportsModel
import app.openhealth.ui.screens.clinical.ReportsScreen
import app.openhealth.ui.screens.clinical.SleepModel
import app.openhealth.ui.screens.clinical.SleepNightRow
import app.openhealth.ui.screens.clinical.SleepScreen
import app.openhealth.ui.screens.clinical.SleepStageRow
import app.openhealth.ui.screens.clinical.WeeklyReviewItemModel
import app.openhealth.ui.screens.clinical.WeeklyReviewModel
import app.openhealth.ui.screens.clinical.WeeklyReviewScreen
import app.openhealth.ui.screens.coach.CoachDiffRowModel
import app.openhealth.ui.screens.coach.CoachMessageModel
import app.openhealth.ui.screens.coach.CoachModel
import app.openhealth.ui.screens.coach.CoachProposalModel
import app.openhealth.ui.screens.coach.CoachProposalStatusUi
import app.openhealth.ui.screens.coach.CoachRoleUi
import app.openhealth.ui.screens.coach.CoachScreen
import app.openhealth.ui.screens.common.OhScreen
import app.openhealth.ui.screens.lock.LockMessage
import app.openhealth.ui.screens.lock.LockScreen
import app.openhealth.ui.screens.mcp.McpAuditModel
import app.openhealth.ui.screens.mcp.McpModel
import app.openhealth.ui.screens.mcp.McpScreen
import app.openhealth.ui.screens.mcp.McpTokenModel
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
import app.openhealth.ui.screens.sync.SyncConflictModel
import app.openhealth.ui.screens.sync.SyncFieldModel
import app.openhealth.ui.screens.sync.SyncModel
import app.openhealth.ui.screens.sync.SyncScreen
import app.openhealth.ui.screens.sync.SyncSideModel
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
        devicesDetail = "None connected",
        syncDetail = "Local only",
        coachDetail = "Off",
        mcpDetail = "Off",
        profileSubtitle = "Just track · metric",
        profileBirthDate = "1990-04-12",
        profileGoal = ProfileGoal.JUST_TRACK,
        profileHeight = "170",
        profileWeight = "80",
        profileUnits = UnitSystem.METRIC,
        profileMayLoseWeight = true,
        hasRestoreSnapshot = false,
        recoveryNote = null,
        reviewCount = 0,
        modules = HealthModule.entries.toSet(),
        preset = ModulePreset.EVERYTHING,
        weightNeutral = false,
        displayPolicy = app.openhealth.core.preferences.DisplayPolicy.of(false),
        theme = ThemeChoice.SYSTEM,
        accent = OhAccent.Brand,
        highContrast = false,
        reduceTransparency = false,
        reducedMotion = false,
        appLock = false,
        appLockAvailable = true,
        demo = false,
        versionName = "1.0.0",
        counts = SettingsCounts(notes = 2, observations = 14, measurements = 1, trash = 0),
        busy = false,
        message = null,
    )

    private val coach = CoachModel(
        enabled = true,
        configured = true,
        providerKind = CoachProviderKind.ANTHROPIC,
        model = "claude-opus-5-5",
        baseUrl = "",
        keyPresent = true,
        categories = setOf(CoachCategory.TRAINING, CoachCategory.RECOVERY),
        healthConnect = false,
        healthConnectAvailable = true,
        disclosureAccepted = true,
        messages = listOf(
            CoachMessageModel(
                id = "m1",
                role = CoachRoleUi.PERSON,
                body = "Review my week.",
            ),
            CoachMessageModel(
                id = "m2",
                role = CoachRoleUi.COACH,
                body = "You completed every set last week. One change would keep the load moving.",
                proposals = listOf(
                    CoachProposalModel(
                        id = "p1",
                        summary = "Add a fourth bench set",
                        rationale = "All planned sets were completed at the same load.",
                        rows = listOf(
                            CoachDiffRowModel(
                                label = "Bench press",
                                before = "3 × 8 · 80 kg · RIR 2",
                                after = "4 × 8 · 80 kg · RIR 2",
                            ),
                        ),
                        status = CoachProposalStatusUi.PROPOSED,
                        undoable = false,
                    ),
                ),
            ),
        ),
        busy = false,
        error = null,
        status = "Ready",
    )

    private val coachDisclosure = coach.copy(
        disclosureAccepted = false,
        messages = emptyList(),
    )

    private val coachStatuses = coach.copy(
        messages = listOf(
            CoachMessageModel(
                id = "m3",
                role = CoachRoleUi.COACH,
                body = "Two changes were considered.",
                proposals = listOf(
                    CoachProposalModel(
                        id = "p2",
                        summary = "Move Push to Wednesday",
                        rationale = "Two sessions sat back to back.",
                        rows = listOf(
                            CoachDiffRowModel(
                                label = "Push",
                                before = "Mon",
                                after = "Wed",
                            ),
                        ),
                        status = CoachProposalStatusUi.ACCEPTED,
                        undoable = true,
                    ),
                    CoachProposalModel(
                        id = "p3",
                        summary = "Add a fourth bench set",
                        rationale = "Kept for next week.",
                        rows = listOf(
                            CoachDiffRowModel(
                                label = "Bench press",
                                before = "3 × 8 · 80 kg",
                                after = "4 × 8 · 80 kg",
                            ),
                        ),
                        status = CoachProposalStatusUi.DISCARDED,
                        undoable = false,
                    ),
                ),
            ),
        ),
    )

    private val mcp = McpModel(
        enabled = true,
        endpoint = "http://127.0.0.1:8765/mcp",
        running = true,
        tokens = listOf(
            McpTokenModel(
                id = "t1",
                label = "Laptop",
                scopes = listOf(
                    app.openhealth.core.ai.McpScope.READ_TRAINING,
                    app.openhealth.core.ai.McpScope.WRITE_NOTES,
                ),
                createdLabel = "2026-11-27 09:10",
                lastUsedLabel = "2026-11-27 09:12",
                revoked = false,
            ),
        ),
        audit = listOf(
            McpAuditModel(
                id = "a1",
                tool = "openhealth_get_training_plan",
                atLabel = "2026-11-27 09:12",
                ok = true,
            ),
        ),
        newToken = null,
        error = null,
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
        onHighContrast = {},
        onReduceTransparency = {},
        onReducedMotion = {},
        onAppLock = {},
        onExport = { _, _ -> },
        onRestore = { _, _ -> },
        onOpenDevices = {},
        onOpenSync = {},
        onOpenCoach = {},
        onOpenMcp = {},
        onOpenTimeline = {},
        onOpenTrash = {},
        onOpenReview = {},
        onOpenSleep = {},
        onOpenClinical = {},
        onOpenReports = {},
        onOpenWeeklyReview = {},
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
    fun coach() {
        shot("screen-coach") {
            CoachScreen(
                model = coach,
                onBack = {},
                onToggle = {},
                onCategory = { _, _ -> },
                onHealthConnect = {},
                onProvider = { _, _, _, _ -> },

                onClearKey = {},
                onAcceptDisclosure = {},
                onSend = {},
                onAccept = {},
                onDiscard = {},
                onUndo = {},
            )
        }
    }

    @Test
    fun coachDisclosure() {
        shot("screen-coach-disclosure") {
            CoachScreen(
                model = coachDisclosure,
                onBack = {},
                onToggle = {},
                onCategory = { _, _ -> },
                onHealthConnect = {},
                onProvider = { _, _, _, _ -> },
                onClearKey = {},
                onAcceptDisclosure = {},
                onSend = {},
                onAccept = {},
                onDiscard = {},
                onUndo = {},
            )
        }
    }

    @Test
    fun coachStatuses() {
        shot("screen-coach-statuses") {
            CoachScreen(
                model = coachStatuses,
                onBack = {},
                onToggle = {},
                onCategory = { _, _ -> },
                onHealthConnect = {},
                onProvider = { _, _, _, _ -> },

                onClearKey = {},
                onAcceptDisclosure = {},
                onSend = {},
                onAccept = {},
                onDiscard = {},
                onUndo = {},
            )
        }
    }

    @Test
    fun mcp() {
        shot("screen-mcp") {
            McpScreen(
                model = mcp,
                onBack = {},
                onToggle = {},
                onCreate = { _, _ -> },
                onRevoke = {},
            )
        }
    }

    @Test
    fun settings() {
        shot("screen-settings") { noopSettings() }
    }

    @Test
    fun syncConflict() {
        shot("screen-sync") {
            SyncScreen(
                state = SyncModel(
                    paired = true,
                    serverUrl = "https://health.example.com",
                    status = app.openhealth.core.sync.SyncStatus.SYNCED,
                    lastSyncedLabel = "2026-11-26 08:12",
                    pending = 0,
                    error = null,
                    conflicts = listOf(
                        SyncConflictModel(
                            id = "profile:me:key",
                            entity = "profile",
                            entityId = "me",
                            local = SyncSideModel(
                                device = "This device · 4f2a1c9e",
                                whenLabel = "2026-11-25 19:04",
                                fields = listOf(SyncFieldModel("name", "Sam")),
                            ),
                            remote = SyncSideModel(
                                device = "Other device · 91bb0d22",
                                whenLabel = "2026-11-26 07:40",
                                fields = listOf(SyncFieldModel("name", "Alex")),
                            ),
                        ),
                    ),
                    busy = false,
                ),
                onBack = {},
                onPair = { _, _ -> },
                onSyncNow = {},
                onDisconnect = {},
                onKeep = { _, _ -> },
            )
        }
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

    private val clinical = ClinicalModel(
        sections = listOf(
            ClinicalSectionModel(
                kind = ClinicalSectionKind.MEDICATIONS,
                title = "Medicines and supplements",
                addLabel = "Add",
                emptyBody = "",
                rows = listOf(
                    ClinicalRowModel(
                        id = "med-1",
                        kind = ClinicalKind.MEDICATION,
                        title = "Levothyroxine",
                        subtitle = "08:00 · 50 ug",
                        value = "1/1 today",
                        deletable = false,
                    ),
                    ClinicalRowModel(
                        id = "med-2",
                        kind = ClinicalKind.MEDICATION,
                        title = "Vitamin D",
                        subtitle = "20:00 · 25 ug",
                        value = "0/1 today",
                        deletable = false,
                    ),
                ),
            ),
            ClinicalSectionModel(
                kind = ClinicalSectionKind.LABS,
                title = "Laboratory results",
                addLabel = "Add",
                emptyBody = "",
                rows = listOf(
                    ClinicalRowModel(
                        id = "lab-1",
                        kind = ClinicalKind.LAB,
                        title = "HbA1c",
                        subtitle = "Labor Berlin · 2026-11-20",
                        value = "5.4 %",
                        deletable = true,
                    ),
                ),
            ),
        ),
        countLabel = "2 medicines · 1 lab result · 0 symptom episodes",
        note = "Everything here is what you entered or imported.",
    )

    private val sleep = SleepModel(
        title = "Sleep",
        averageLabel = "Average asleep",
        averageValue = "7h 10m",
        debtLabel = "Sleep debt",
        debtValue = "3h 20m",
        debtNote = "Against your own need of 8h a night.",
        consistencyBedLabel = "Bedtime varied by",
        consistencyBedValue = "38m",
        consistencyWakeLabel = "Wake time varied by",
        consistencyWakeValue = "21m",
        consistencyNote = "Measured on the clock.",
        nights = listOf(
            SleepNightRow("2026-11-25", "7h 20m", "22:40", "06:10", "4"),
            SleepNightRow("2026-11-24", "6h 55m", "23:10", "06:05", "3"),
        ),
        stages = listOf(
            SleepStageRow("Deep", "1h 10m", "16 %"),
            SleepStageRow("REM", "1h 25m", "19 %"),
        ),
        stagesNote = "Stages come from the most detailed source.",
        accessible = "2 nights, average 7h 5m asleep.",
        emptyTitle = "No nights yet",
        emptyBody = "Sleep arrives from Health Connect.",
        qualityLabel = "Sleep quality is your own rating.",
        addQualityLabel = "Rate last night",
        reminder = "A night without data is left out.",
    )

    private val reports = ReportsModel(
        title = "Doctor report",
        ranges = listOf(
            ReportRangeModel(30, "30 days"),
            ReportRangeModel(90, "90 days"),
            ReportRangeModel(180, "180 days"),
            ReportRangeModel(365, "1 year"),
        ),
        selectedRange = 1,
        rangeNote = "The report uses the window you choose.",
        generatedLabel = "Generated 2026-11-26 on this device",
        previewTitle = "openHealth clinical summary",
        previewLines = listOf(
            "openHealth clinical summary",
            "Alex · 2026-08-29 to 2026-11-26",
            "Vitals",
            "Systolic blood pressure | 122 | mmHg | 120-129 (ESH 2023)",
        ),
        previewTruncated = false,
        disclaimer = "This report is a record of information the person entered.",
        sourceNote = "Every figure names its source.",
        exports = app.openhealth.ui.screens.clinical.ReportFormat.entries,
        empty = false,
        emptyBody = "There is nothing in this window yet.",
    )

    private val weekly = WeeklyReviewModel(
        title = "Weekly review",
        weekLabel = "2026-11-20 to 2026-11-26",
        items = listOf(
            WeeklyReviewItemModel(
                title = "Sleep",
                value = "7h 10m",
                note = "6 nights recorded, sleep debt 2h.",
                citation = "Joint Consensus Statement 2015",
            ),
            WeeklyReviewItemModel(
                title = "Training",
                value = "3 activities",
                note = "2h 40m",
            ),
        ),
        accessible = "2 measures. First: Sleep, 7h 10m.",
        emptyTitle = "Nothing to review yet",
        emptyBody = "A week with sleep or activity is described here.",
    )

    private val clinicalEditor = ClinicalEditorModel(
        kind = ClinicalKind.LAB,
        title = "Lab result",
        fields = listOf(
            ClinicalFieldModel(
                key = "analyte",
                label = "Test",
                value = "HbA1c",
                type = ClinicalFieldType.CHOICE,
                options = listOf("HbA1c", "Fasting glucose", "TSH"),
            ),
            ClinicalFieldModel(key = "value", label = "Result", value = "5.4"),
        ),
        saveLabel = "Save",
        deleteLabel = "Move to Trash",
        note = "The interval belongs to the laboratory that printed it.",
    )

    @Test
    fun clinicalHub() {
        shot("screen-clinical") {
            ClinicalScreen(
                model = clinical,
                onBack = {},
                onAdd = {},
                onOpen = {},
            )
        }
    }

    @Test
    fun clinicalEditor() {
        shot("screen-clinical-editor") {
            ClinicalEditorScreen(
                model = clinicalEditor,
                onBack = {},
                onSave = {},
                onDelete = {},
            )
        }
    }

    @Test
    fun sleep() {
        shot("screen-sleep") {
            SleepScreen(model = sleep, onBack = {}, onRateQuality = {})
        }
    }

    @Test
    fun reports() {
        shot("screen-reports") {
            ReportsScreen(
                model = reports,
                onBack = {},
                onSelectRange = {},
                onExport = {},
            )
        }
    }

    @Test
    fun weeklyReview() {
        shot("screen-weekly-review") {
            WeeklyReviewScreen(model = weekly, onBack = {})
        }
    }

    private companion object {
        const val SHOT = "screen"
    }
}
