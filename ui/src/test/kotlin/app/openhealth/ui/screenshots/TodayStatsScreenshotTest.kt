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
import app.openhealth.core.nutrition.Nutrient
import app.openhealth.core.preferences.HealthModule
import app.openhealth.ui.components.ChartBar
import app.openhealth.ui.components.ChartPoint
import app.openhealth.ui.components.ChartTableModel
import app.openhealth.ui.components.HeatCellUi
import app.openhealth.ui.components.StatBarRow
import app.openhealth.ui.screens.stats.ChartId
import app.openhealth.ui.screens.stats.MacroAverage
import app.openhealth.ui.screens.stats.StatsCard
import app.openhealth.ui.screens.stats.StatsHue
import app.openhealth.ui.screens.stats.StatsKpi
import app.openhealth.ui.screens.stats.StatsModel
import app.openhealth.ui.screens.stats.StatsPlot
import app.openhealth.ui.screens.stats.StatsRecords
import app.openhealth.ui.screens.stats.StatsScreen
import app.openhealth.ui.screens.stats.StatsSegment
import app.openhealth.ui.screens.stats.StatsStackSegment
import app.openhealth.ui.screens.today.GoalEtaState
import app.openhealth.ui.screens.today.GoalWhatIfModel
import app.openhealth.ui.screens.today.Greeting
import app.openhealth.ui.screens.today.ReadinessCardModel
import app.openhealth.ui.screens.today.ReadinessChip
import app.openhealth.ui.screens.today.ReadinessPartRow
import app.openhealth.ui.screens.today.TodayChartModel
import app.openhealth.ui.screens.today.TodayDueAction
import app.openhealth.ui.screens.today.TodayDueItem
import app.openhealth.ui.screens.today.TodayEnergyModel
import app.openhealth.ui.screens.today.TodayMacro
import app.openhealth.ui.screens.today.TodayModel
import app.openhealth.ui.screens.today.TodayPlanModel
import app.openhealth.ui.screens.today.TodayScreen
import app.openhealth.ui.screens.today.TodayStatus
import app.openhealth.ui.screens.today.TodayStepsModel
import app.openhealth.ui.screens.today.TodayWeekDay
import app.openhealth.ui.screens.today.TodayWeightModel
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

/**
 * B9's filled Today and Stats in the four variants of `DESIGN.md` §14.5: dark, light,
 * 200 % text and RTL. The empty Today stays in the B3 suite, so both states are reviewed.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-mdpi")
class TodayStatsScreenshotTest {
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

    private val date = LocalDate(2026, 11, 25)

    private fun table(title: String, name: String, value: String) = ChartTableModel(
        title = title,
        columns = listOf("Day", "Value"),
        rows = listOf(listOf(name, value)),
    )

    private fun chart(id: ChartId, title: String, description: String) = TodayChartModel(
        chart = id,
        description = description,
        table = table(title, "2026-11-25", "82.4"),
    )

    private fun filledToday() = TodayModel(
        today = date,
        greeting = Greeting.MORNING,
        status = TodayStatus.LOGGED_TODAY,
        noteCount = 1,
        waterMl = 900.0,
        steps = 8_412,
        energyKcal = 2_120.0,
        weightKg = 82.4,
        readinessScore = 74,
        modules = HealthModule.entries.toSet(),
        demo = false,
        readiness = ReadinessCardModel(
            score = 74,
            chips = listOf(
                ReadinessChip("Sleep", "7 h 30", true),
                ReadinessChip("HRV", "58 ms", true),
                ReadinessChip("Resting HR", "54 bpm", true),
            ),
            explanation = "Readiness compares today with your own 28-day baseline.",
            parts = listOf(
                ReadinessPartRow("Sleep", 90, "35 %"),
                ReadinessPartRow("Resting heart rate", 70, "25 %"),
                ReadinessPartRow("Heart rate variability", 60, "20 %"),
                ReadinessPartRow("Training load", 65, "20 %"),
            ),
        ),
        week = (0 until 7).map { index ->
            TodayWeekDay(
                date.minusDays(2).plusDays(index),
                isToday = index == 2,
                hasSession = index == 2 || index == 4 || index == 6,
            )
        },
        sessionCount = 3,
        plan = TodayPlanModel(
            routineId = "routine-a",
            name = "Lower body",
            detail = "18 sets",
            setCount = 18,
            map = null,
        ),
        energy = TodayEnergyModel(
            intakeKcal = 2_120.0,
            outLowKcal = 1_930.0,
            outHighKcal = 2_180.0,
            netLowKcal = -60.0,
            netHighKcal = 190.0,
            targetKcal = 2_300.0,
            remainingKcal = 180.0,
            macros = listOf(
                TodayMacro(Nutrient.PROTEIN, 132.0, 140.0, 0.94f),
                TodayMacro(Nutrient.CARBS, 220.0, 260.0, 0.85f),
                TodayMacro(Nutrient.FAT, 68.0, 76.0, 0.89f),
                TodayMacro(Nutrient.FIBRE, 24.0, 32.0, 0.75f),
            ),
            waterMl = 900.0,
            waterTargetMl = 2_500.0,
            goalEta = GoalEtaState.Window(
                date.plusDays(81),
                date.plusDays(104),
            ),
            whatIf = GoalWhatIfModel(
                currentWeightKg = 82.4,
                goalWeightKg = 78.0,
                initialDailyDeltaKcal = -275.0,
                minimumDailyDeltaKcal = -825.0,
                maximumDailyDeltaKcal = -206.0,
                minimumTargetKcal = 1_500.0,
            ),
            hasIntake = true,
        ),
        stepsCard = TodayStepsModel(
            steps = 8_412,
            goal = 8_000,
            activeMinutes = 42.0,
            distanceKm = 6.1,
            sourceLabel = "Garmin",
        ),
        weightCard = TodayWeightModel(
            weightKg = 82.4,
            trendKg = 82.6,
            weeklyChangeKg = -0.3,
            goalKg = 78.0,
            spark = (0 until 10).map {
                ChartPoint(date.minusDays(9 - it), 83.2 - it * 0.08)
            },
            sparkDescription = "Weight 82.4 kilograms, 0.3 kilograms a week.",
            sourceLabel = "Withings",
        ),
        due = listOf(
            TodayDueItem(
                "Check your blood pressure",
                "No reading in the last seven days.",
                TodayDueAction.VITALS,
            ),
            TodayDueItem(
                "Write a note",
                "Nothing has been written today.",
                TodayDueAction.NOTE,
            ),
        ),
        charts = listOf(
            chart(
                ChartId.ENERGY_RING,
                "Energy",
                "Energy in 2120 kcal, out between 1930 and 2180 kcal.",
            ),
            chart(ChartId.STEPS_RING, "Steps", "8412 of 8000 steps."),
            chart(
                ChartId.WEIGHT_SPARKLINE,
                "Weight",
                "Weight 82.4 kilograms, 0.3 kilograms a week.",
            ),
        ),
    )

    @Suppress("ReturnCount")
    private fun stats(segment: StatsSegment): StatsModel {
        val line = StatsPlot.Line(
            (0 until 14).map { ChartPoint(date.minusDays(13 - it), 82.0 + it * 0.03) },
        )
        val weight = StatsCard(
            chart = ChartId.WEIGHT_TREND,
            title = "Weight, 30 days",
            description = "Weight, 30 days, down 0.8 kilograms, from 83.2 to 82.4.",
            plot = line,
            table = table("Weight, 30 days", "2026-11-25", "82.4"),
            unitText = "kg",
            decimals = 1,
            goal = 78.0,
        )
        val steps = StatsCard(
            chart = ChartId.STEPS_TREND,
            title = "Steps, 30 days",
            description = "Steps, 30 days, an average of 7412 a day.",
            plot = line,
            table = table("Steps, 30 days", "2026-11-25", "8412"),
            unitText = "steps",
        )
        val volume = StatsCard(
            chart = ChartId.TRAINING_VOLUME,
            title = "Training volume",
            description = "Training volume, 12 weeks, 12400 kilograms this week.",
            plot = StatsPlot.Bars(
                listOf(
                    ChartBar("4 Nov", 11_200.0),
                    ChartBar("11 Nov", 12_800.0),
                    ChartBar("18 Nov", 10_400.0),
                    ChartBar("25 Nov", 12_400.0),
                ),
            ),
            table = table("Training volume", "2026-11-25", "12400"),
            unitText = "kg",
        )
        val heat = StatsCard(
            chart = ChartId.TRAINING_CONSISTENCY,
            title = "Training consistency, 12 weeks",
            description = "Training consistency, 12 weeks, the busiest day holds 22 sets.",
            plot = StatsPlot.Heat(
                (0 until 8).map { week ->
                    (0 until 7).map { day ->
                        HeatCellUi(
                            date.minusDays((7 - week) * 7 + day),
                            app.openhealth.core.stats.RampLevel.entries[(week + day) % 5],
                        )
                    }
                },
            ),
            table = table("Training consistency", "2026-11-25", "18"),
        )
        val muscles = StatsCard(
            chart = ChartId.MUSCLE_SETS,
            title = "Sets per muscle, last seven days",
            description = "Sets per muscle, last seven days, most for Quads.",
            plot = StatsPlot.BarsList(
                listOf(
                    StatBarRow("Quads", 18.0, "18"),
                    StatBarRow("Chest", 12.0, "12"),
                    StatBarRow("Upper back", 11.0, "11"),
                ),
            ),
            table = table("Sets per muscle", "Quads", "18"),
        )
        val overview = segment == StatsSegment.OVERVIEW
        if (segment == StatsSegment.SLEEP) {
            return StatsModel(
                today = date,
                segment = segment,
                kpis = listOf(
                    StatsKpi("7 h 18", "Sleep a night"),
                    StatsKpi("9 h 19", "Sleep debt, 14 nights"),
                    StatsKpi("31 min", "Night-to-night variation"),
                ),
                cards = listOf(
                    StatsCard(
                        chart = ChartId.SLEEP_DURATION,
                        title = "Sleep, 14 nights",
                        description = "Sleep, 14 nights, an average of 7 h 18 a night.",
                        plot = StatsPlot.Bars(
                            (0 until 7).map { ChartBar("${12 + it}", 7.0 + it / 10.0) },
                        ),
                        table = table("Sleep, 14 nights", "2026-11-24", "7.3"),
                        unitText = "hours",
                    ),
                    StatsCard(
                        chart = ChartId.SLEEP_STAGES,
                        title = "Last night's stages",
                        description = "Last night, 7 h 18 asleep, by stage.",
                        plot = StatsPlot.Stack(
                            listOf(
                                StatsStackSegment("Awake", 24.0, StatsHue.ORANGE),
                                StatsStackSegment("REM", 96.0, StatsHue.FROST),
                                StatsStackSegment("Light", 240.0, StatsHue.BLUE),
                                StatsStackSegment("Deep", 78.0, StatsHue.PURPLE),
                            ),
                        ),
                        table = table("Last night's stages", "Deep", "78"),
                        unitText = "minutes",
                    ),
                ),
            )
        }
        if (segment == StatsSegment.NUTRITION) {
            return StatsModel(
                today = date,
                segment = segment,
                kpis = listOf(
                    StatsKpi("2,200", "Energy in a day", "kcal"),
                    StatsKpi("148", "Protein a day", "g"),
                    StatsKpi("30 of 30", "Days logged"),
                ),
                cards = listOf(
                    StatsCard(
                        chart = ChartId.MACRO_BARS,
                        title = "Macro averages, 7 days",
                        description = "Macro averages, seven days, beside their targets.",
                        plot = StatsPlot.Macros(
                            listOf(
                                MacroAverage("Protein", 148.0, 140.0, 1f, StatsHue.GREEN),
                                MacroAverage("Carbohydrates", 220.0, 260.0, 0.84f, StatsHue.BLUE),
                                MacroAverage("Fat", 68.0, 76.0, 0.89f, StatsHue.PURPLE),
                                MacroAverage("Fibre", 24.0, 32.0, 0.75f, StatsHue.TEAL),
                            ),
                        ),
                        table = table("Macro averages", "Protein", "148"),
                        unitText = "g",
                    ),
                ),
            )
        }
        return StatsModel(
            today = date,
            segment = segment,
            kpis = if (overview) {
                listOf(
                    StatsKpi("3", "Sessions"),
                    StatsKpi("7,412", "Steps a day"),
                    StatsKpi("−0.8", "Weight change", "kg"),
                )
            } else {
                listOf(
                    StatsKpi("12,400", "Volume this week", "kg"),
                    StatsKpi("54", "Sets this week"),
                    StatsKpi("3", "Sessions"),
                )
            },
            cards = if (overview) listOf(weight, steps) else listOf(volume, heat, muscles),
            records = if (overview) {
                null
            } else {
                StatsRecords(
                    "Personal records",
                    listOf("Back squat · 100.0 kg for 121.3", "Bench press · 80.0 kg for 96.0"),
                )
            },
        )
    }

    @Test
    fun todayFilled() {
        shot("screen-today-filled") {
            TodayScreen(
                model = filledToday(),
                displayPolicy = app.openhealth.core.preferences.DisplayPolicy.Standard,
                onQuickAdd = {},
                onOpenTimeline = {},
                onOpenTrain = {},
                onOpenNutrition = {},
                synchronousLayers = true,
            )
        }
    }

    @Test
    fun statsOverview() {
        shot("screen-stats-overview") {
            StatsScreen(model = stats(StatsSegment.OVERVIEW), onSelectSegment = {})
        }
    }

    @Test
    fun statsTraining() {
        shot("screen-stats-training") {
            StatsScreen(model = stats(StatsSegment.TRAINING), onSelectSegment = {})
        }
    }

    @Test
    fun statsSleep() {
        shot("screen-stats-sleep") {
            StatsScreen(model = stats(StatsSegment.SLEEP), onSelectSegment = {})
        }
    }

    @Test
    fun statsBody() {
        shot("screen-stats-body") {
            StatsScreen(model = stats(StatsSegment.BODY), onSelectSegment = {})
        }
    }

    @Test
    fun statsNutrition() {
        shot("screen-stats-nutrition") {
            StatsScreen(model = stats(StatsSegment.NUTRITION), onSelectSegment = {})
        }
    }

    private fun LocalDate.plusDays(days: Int): LocalDate =
        plus(kotlinx.datetime.DatePeriod(days = days))

    private fun LocalDate.minusDays(days: Int): LocalDate =
        plus(kotlinx.datetime.DatePeriod(days = -days))

    private companion object {
        const val SHOT = "shot"
    }
}
