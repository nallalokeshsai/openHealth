package app.openhealth.ui.screens.today

import app.openhealth.core.nutrition.Nutrient
import app.openhealth.core.preferences.HealthModule
import app.openhealth.ui.body.BodyMapModel
import app.openhealth.ui.components.ChartPoint
import app.openhealth.ui.components.ChartTableModel
import app.openhealth.ui.screens.stats.ChartId
import kotlinx.datetime.LocalDate

/** The part of the day the greeting names (`DESIGN.md` §14.2, Today item 1). */
enum class Greeting { NIGHT, MORNING, AFTERNOON, EVENING }

/** The one-line status under the greeting. Never a score and never a guess. */
enum class TodayStatus { NOTHING_LOGGED, LOGGED_TODAY, WEIGHT_NEUTRAL }

/** The six quick-add tiles of `pr.md` §6.2 and `DESIGN.md` §14.2. */
enum class QuickAction { NOTE, WATER, WEIGHT, FOOD, SET, VITALS }

/** One of the three chips under the readiness score (`DESIGN.md` §14.2 item 4). */
data class ReadinessChip(val label: String, val value: String?, val available: Boolean)

/** One input row of the readiness explanation sheet. */
data class ReadinessPartRow(val label: String, val score: Int, val weightText: String)

/** The readiness card. A null score is an honest "not enough data yet". */
data class ReadinessCardModel(
    val score: Int?,
    val chips: List<ReadinessChip>,
    val explanation: String,
    val parts: List<ReadinessPartRow> = emptyList(),
    val version: Int = 1,
)

/** One day of the week strip. */
data class TodayWeekDay(val date: LocalDate, val isToday: Boolean, val hasSession: Boolean)

/** Today's plan, when a routine or a rotation has one for today. */
data class TodayPlanModel(
    val routineId: String?,
    val name: String,
    val detail: String,
    val setCount: Int,
    val map: BodyMapModel?,
)

/** One row of the due list. [action] is what the row opens. */
enum class TodayDueAction { VITALS, NOTE, SLEEP, WEIGHT, FOOD, WATER }

data class TodayDueItem(val title: String, val body: String, val action: TodayDueAction)

/** One macro row of the energy card. */
data class TodayMacro(
    val nutrient: Nutrient,
    val grams: Double,
    val targetGrams: Double?,
    val fraction: Float?,
)

/** The goal estimate: a window, or the honest reason there is none yet. */
sealed interface GoalEtaState {
    data class Window(val start: LocalDate, val end: LocalDate) : GoalEtaState

    data object NeedsData : GoalEtaState

    /** Fourteen days exist but the trend is too flat to project a window. */
    data object Steady : GoalEtaState

    /** The recent trend moves away from the goal, so there is nothing honest to project. */
    data object Away : GoalEtaState

    data object NoGoalWeight : GoalEtaState
}

/** The what-if control's starting point and the range the safety floors allow. */
data class GoalWhatIfModel(
    val currentWeightKg: Double,
    val goalWeightKg: Double,
    val initialDailyDeltaKcal: Double,
    val minimumDailyDeltaKcal: Double,
    val maximumDailyDeltaKcal: Double,
)

/** In, out and net as ranges, with the ring's remaining figure and the goal line. */
data class TodayEnergyModel(
    val intakeKcal: Double,
    val outLowKcal: Double,
    val outHighKcal: Double,
    val netLowKcal: Double,
    val netHighKcal: Double,
    val targetKcal: Double?,
    val remainingKcal: Double?,
    val macros: List<TodayMacro>,
    val waterMl: Double?,
    val waterTargetMl: Double?,
    val goalEta: GoalEtaState,
    val whatIf: GoalWhatIfModel?,
    val hasIntake: Boolean,
)

/** The steps card: one ring, active minutes and distance, and the winning source. */
data class TodayStepsModel(
    val steps: Int,
    val goal: Int,
    val activeMinutes: Double?,
    val distanceKm: Double?,
    val sourceLabel: String?,
)

/** The weight card: the latest value, the weekly change and a 30-day sparkline. */
data class TodayWeightModel(
    val weightKg: Double,
    val trendKg: Double?,
    val weeklyChangeKg: Double?,
    val goalKg: Double?,
    val spark: List<ChartPoint>,
    val sourceLabel: String?,
)

/** A chart on Today, with its one-sentence description and its table alternative. */
data class TodayChartModel(
    val chart: ChartId,
    val description: String,
    val table: ChartTableModel,
) {
    init {
        require(description.isNotBlank()) {
            "a chart without its sentence is not drawn (DESIGN.md §8.6)"
        }
    }
}

/**
 * Everything Today shows. A field that is null is honestly absent and the screen renders
 * its empty state; nothing here is a placeholder number (`DESIGN.md` §14.2).
 */
data class TodayModel(
    val today: LocalDate,
    val greeting: Greeting,
    val status: TodayStatus,
    val noteCount: Int,
    val waterMl: Double?,
    val steps: Int?,
    val energyKcal: Double?,
    val weightKg: Double?,
    val unitSystem: app.openhealth.core.preferences.UnitSystem =
        app.openhealth.core.preferences.UnitSystem.METRIC,
    val readinessScore: Int?,
    val modules: Set<HealthModule>,
    val demo: Boolean,
    /** The recovery map, or null when training is off or no store is open. */
    val bodyMap: BodyMapModel? = null,
    val bodyChips: List<String> = emptyList(),
    val lastMuscle: String? = null,
    val readiness: ReadinessCardModel? = null,
    val week: List<TodayWeekDay> = emptyList(),
    val sessionCount: Int = 0,
    val plan: TodayPlanModel? = null,
    val energy: TodayEnergyModel? = null,
    val stepsCard: TodayStepsModel? = null,
    val weightCard: TodayWeightModel? = null,
    val due: List<TodayDueItem> = emptyList(),
    val charts: List<TodayChartModel> = emptyList(),
    val provenances: List<TodayProvenance> = emptyList(),
)
