@file:Suppress(
    "LargeClass",
    "TooManyFunctions",
    "LongParameterList",
    "LongMethod",
    "CyclomaticComplexMethod",
    "ReturnCount",
    "MagicNumber",
)

package app.openhealth.android.today

import android.content.Context
import app.openhealth.android.preferences.Preferences
import app.openhealth.core.body.BodySex
import app.openhealth.core.exercise.PlanCoverage
import app.openhealth.core.exercise.PlannedExercise
import app.openhealth.core.formula.ActivityLevel
import app.openhealth.core.formula.Bmr
import app.openhealth.core.formula.DailyValue
import app.openhealth.core.formula.EnergyBalanceCalculator
import app.openhealth.core.formula.EnergySafety
import app.openhealth.core.formula.FormulaOutcome
import app.openhealth.core.formula.GoalEta
import app.openhealth.core.formula.GoalMode
import app.openhealth.core.formula.GoalRatePolicy
import app.openhealth.core.formula.MetActivity
import app.openhealth.core.formula.Readiness
import app.openhealth.core.formula.ReadinessSeries
import app.openhealth.core.formula.SexAtBirth
import app.openhealth.core.formula.SleepSample
import app.openhealth.core.formula.SleepSession
import app.openhealth.core.formula.SleepSessions
import app.openhealth.core.formula.StepEnergy
import app.openhealth.core.formula.Tdee
import app.openhealth.core.formula.WeightEntry
import app.openhealth.core.formula.WeightTrend
import app.openhealth.core.formula.WorkoutEnergy
import app.openhealth.core.ids.SourceId
import app.openhealth.core.nutrition.MacroTargets
import app.openhealth.core.nutrition.Nutrient
import app.openhealth.core.nutrition.TargetSuggestions
import app.openhealth.core.observation.MeasurementMethod
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.preferences.HealthModule
import app.openhealth.core.seam.ClockSeam
import app.openhealth.core.training.RoutinePlacement
import app.openhealth.core.training.RoutinePlanner
import app.openhealth.data.LocalStore
import app.openhealth.data.repo.SettingKeys
import app.openhealth.ui.R as UiR
import app.openhealth.ui.body.BodyMapModel
import app.openhealth.ui.components.ChartPoint
import app.openhealth.ui.components.ChartTableModel
import app.openhealth.ui.screens.stats.ChartId
import app.openhealth.ui.screens.stats.ChartRegistry
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
import app.openhealth.ui.screens.today.TodayProvenance
import app.openhealth.ui.screens.today.TodayStatus
import app.openhealth.ui.screens.today.TodayStepsModel
import app.openhealth.ui.screens.today.TodayValue
import app.openhealth.ui.screens.today.TodayWeekDay
import app.openhealth.ui.screens.today.TodayWeightModel
import app.openhealth.ui.screens.vitals.ValueProvenance
import java.text.NumberFormat
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.days
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Builds Today (`DESIGN.md` §14.2) from the rollups and the day's own rows. Every figure
 * is either measured, calculated by a cited formula, or absent; nothing is invented and
 * nothing hidden by weight-neutral mode is built at all.
 */
class TodayPresenter(
    private val context: Context,
    private val store: LocalStore,
    private val clock: ClockSeam,
    private val zone: TimeZone,
) {
    private val numberFormat: NumberFormat = NumberFormat.getNumberInstance()

    fun model(preferences: Preferences, profile: app.openhealth.data.db.Profile): TodayModel {
        val date = today()
        val rollups = store.rollups.forDay(date)
        fun value(type: ObservationType): Double? =
            rollups.firstOrNull { it.type == type }?.value?.value

        val notes = store.notes.forDay(date)
        val water = value(ObservationType.WATER)
        val steps = value(ObservationType.STEPS)?.roundToInt()
        val energyKcal = value(ObservationType.NUTRITION_ENERGY)
        val weight = latestWeight()
        val weightTrend = weightTrend()
        val readiness = if (preferences.modules.any {
                it == HealthModule.SLEEP ||
                    it == HealthModule.VITALS ||
                    it == HealthModule.TRAINING
            }
        ) {
            readiness(date)
        } else {
            null
        }
        val plan = plan(date, profile)
        val sessionCount = sessionsThisWeek(date)
        val showsCalories = preferences.displayPolicy.shows(
            app.openhealth.core.preferences.SensitiveMetric.CALORIES,
        )
        val showsWeight = preferences.displayPolicy.shows(
            app.openhealth.core.preferences.SensitiveMetric.WEIGHT,
        )
        val energy = if (showsCalories) energy(date, profile, weight?.kg, energyKcal) else null
        val stepsCard = stepsCard(date, steps)
        val weightCard = if (showsWeight) weightCard(weight, weightTrend) else null
        val anything = notes.isNotEmpty() ||
            water != null ||
            weight != null ||
            steps != null ||
            energyKcal != null
        val moduleOn = { module: HealthModule -> module in preferences.modules }
        return TodayModel(
            today = date,
            greeting = greeting(),
            status = when {
                preferences.weightNeutral -> TodayStatus.WEIGHT_NEUTRAL
                anything -> TodayStatus.LOGGED_TODAY
                else -> TodayStatus.NOTHING_LOGGED
            },
            noteCount = notes.size,
            waterMl = water,
            steps = steps,
            energyKcal = energyKcal,
            weightKg = if (showsWeight) weight?.kg else null,
            unitSystem = app.openhealth.core.preferences.UnitSystem.fromKey(profile.units)
                ?: app.openhealth.core.preferences.UnitSystem.METRIC,
            readinessScore = readiness?.score,
            modules = preferences.modules,
            demo = preferences.demo,
            bodyMap = null,
            readiness = readiness,
            week = week(date),
            sessionCount = sessionCount,
            plan = plan,
            energy = energy,
            stepsCard = stepsCard,
            weightCard = weightCard,
            due = due(date, moduleOn, preferences.weightNeutral),
            charts = charts(date, energy, steps, weight, weightTrend),
            provenances = provenances(date, energy, steps, weight),
        )
    }

    // ------------------------------------------------------------------ day values

    private fun today(): LocalDate = clock.now().toLocalDateTime(zone).date

    private fun greeting(): Greeting = when (clock.now().toLocalDateTime(zone).hour) {
        in 0..4 -> Greeting.NIGHT
        in 5..11 -> Greeting.MORNING
        in 12..16 -> Greeting.AFTERNOON
        else -> Greeting.EVENING
    }

    private data class WeightReading(val kg: Double, val date: LocalDate)

    private fun latestWeight(): WeightReading? {
        val date = today()
        val rollups = store.rollups.forTypeRange(
            ObservationType.WEIGHT,
            date.minus(DatePeriod(days = TREND_DAYS - 1)),
            date,
        )
        val latest = rollups.maxByOrNull { it.localDate } ?: return null
        return WeightReading(latest.value.value, latest.localDate)
    }

    private fun weightTrend(): app.openhealth.core.formula.WeightTrendSeries? {
        val date = today()
        val rollups = store.rollups.forTypeRange(
            ObservationType.WEIGHT,
            date.minus(DatePeriod(days = TREND_DAYS - 1)),
            date,
        )
        if (rollups.isEmpty()) return null
        val entries = rollups.map { WeightEntry(it.localDate, it.value.value) }
        return (WeightTrend.ewma(entries) as? FormulaOutcome.Computed)?.value
    }

    private fun readiness(date: LocalDate): ReadinessCardModel? {
        val yesterday = date.minus(DatePeriod(days = 1))
        val sleep = sleepSessions(date)
            .lastOrNull { it.localDate >= yesterday.minus(DatePeriod(days = 1)) }
            ?.totals
        val rhrSeries = dailySeries(
            ObservationType.RESTING_HEART_RATE,
            date,
        )
        val hrvSeries = dailySeries(ObservationType.HEART_RATE_VARIABILITY, date)
            .map { it.copy(value = it.value * MILLIS_PER_SECOND) }
        val rawLoad = dailySeries(ObservationType.TRAINING_VOLUME, date)
        val loadByDay = rawLoad.associateBy { it.date }
        val loadSeries = (0 until ReadinessSeries.WINDOW_DAYS).map { offset ->
            val day = date.minus(DatePeriod(days = offset))
            DailyValue(day, loadByDay[day]?.value ?: 0.0)
        }
        val loadHistoryDays = rawLoad.minOfOrNull { it.date }?.let { first ->
            (first.daysUntil(date.minus(DatePeriod(days = 1))) + 1)
                .coerceIn(0, ReadinessSeries.WINDOW_DAYS)
        } ?: 0
        val rhr = ReadinessSeries.latest(rhrSeries, date)
        val hrvSeconds = ReadinessSeries.latest(hrvSeries, date)
        val recentLoad = loadSeries.filter { it.date >= date.minus(DatePeriod(days = 1)) }
        val load48 = recentLoad.takeIf { it.isNotEmpty() }?.sumOf { it.value }
        val sleepNeed = store.settings.get(SettingKeys.SLEEP_NEED_MINUTES)
            ?.toDoubleOrNull()
            ?.takeIf { it >= 60.0 }
            ?: DEFAULT_SLEEP_NEED_MINUTES
        val inputs = ReadinessSeries.inputs(
            today = date,
            sleepMinutes = sleep?.asleepMinutes,
            restingHeartRate = rhr,
            restingHeartRateSeries = rhrSeries,
            hrvMillis = hrvSeconds,
            hrvSeries = hrvSeries,
            trainingLoad48h = load48,
            trainingLoadSeries = loadSeries,
            trainingLoadBaselineDays = loadHistoryDays,
            sleepNeedMinutes = sleepNeed,
        )
        val outcome = Readiness.compute(inputs)
        if (outcome !is FormulaOutcome.Computed) {
            // The card still names what is missing: a sleep-only day has one chip, no score.
            val chips = buildList {
                sleep?.let {
                    add(
                        ReadinessChip(
                            context.getString(UiR.string.today_readiness_part_sleep),
                            durationText(it.asleepMinutes),
                            true,
                        ),
                    )
                }
                rhr?.let {
                    add(
                        ReadinessChip(
                            context.getString(UiR.string.today_chip_rhr),
                            "${decimal(it)} ${context.getString(UiR.string.unit_bpm)}",
                            true,
                        ),
                    )
                }
                hrvSeconds?.let {
                    add(
                        ReadinessChip(
                            context.getString(UiR.string.today_chip_hrv),
                            "${decimal(it)} ${context.getString(UiR.string.unit_ms)}",
                            true,
                        ),
                    )
                }
            }
            if (chips.isEmpty()) return null
            return ReadinessCardModel(
                score = null,
                chips = chips,
                explanation = context.getString(UiR.string.today_readiness_empty_body),
            )
        }
        val score = outcome.value
        val chips = buildList {
            sleep?.let {
                add(
                    ReadinessChip(
                        context.getString(UiR.string.today_readiness_part_sleep),
                        durationText(it.asleepMinutes),
                        true,
                    ),
                )
            }
            hrvSeconds?.let {
                add(
                    ReadinessChip(
                        context.getString(UiR.string.today_chip_hrv),
                        "${decimal(it)} ${context.getString(UiR.string.unit_ms)}",
                        true,
                    ),
                )
            }
            rhr?.let {
                add(
                    ReadinessChip(
                        context.getString(UiR.string.today_chip_rhr),
                        "${decimal(it)} ${context.getString(UiR.string.unit_bpm)}",
                        true,
                    ),
                )
            }
        }
        val names = mapOf(
            "sleep" to UiR.string.today_readiness_part_sleep,
            "restingHeartRate" to UiR.string.today_readiness_part_rhr,
            "hrv" to UiR.string.today_readiness_part_hrv,
            "load" to UiR.string.today_readiness_part_load,
        )
        return ReadinessCardModel(
            score = score.score,
            chips = chips,
            explanation = context.getString(UiR.string.today_readiness_explanation),
            parts = score.parts.map { part ->
                ReadinessPartRow(
                    label = context.getString(names[part.name] ?: UiR.string.today_readiness_title),
                    score = part.score.roundToInt(),
                    weightText = context.getString(
                        UiR.string.today_readiness_part_weight,
                        decimal(part.weight * 100.0),
                    ),
                )
            },
            version = score.version,
        )
    }

    /** Every reading of the last week, assembled by session so midnight cannot split a night. */
    private fun sleepSessions(date: LocalDate): List<SleepSession> {
        val from = date.minus(DatePeriod(days = SLEEP_QUERY_DAYS)).atStartOfDayIn(zone)
        val to = date.plus(DatePeriod(days = 2)).atStartOfDayIn(zone)
        val samples = SLEEP_TYPES.flatMap { type ->
            store.observations.forTypeBetween(type, from, to).map { observation ->
                SleepSample(
                    groupKey = observation.groupKey,
                    type = observation.type,
                    seconds = observation.value.value,
                    localDate = observation.localDate,
                    sourceId = observation.provenance.sourceId.value,
                )
            }
        }
        // One source wins a night, by the same priority list the rollups use (`pr.md` §7.6).
        val registry = store.sources.registry()
        return SleepSessions.collect(samples) { sourceId ->
            registry.priorityOf(SourceId(sourceId), ObservationType.SLEEP_DURATION)
        }
    }

    private fun dailySeries(type: ObservationType, today: LocalDate): List<DailyValue> =
        store.rollups.forTypeRange(type, today.minus(DatePeriod(days = TREND_DAYS - 1)), today)
            .map { DailyValue(it.localDate, it.value.value) }

    private fun week(date: LocalDate): List<TodayWeekDay> {
        val days = RoutinePlanner.week(date)
        val sessions = store.training.sessionsBetween(
            days.first(),
            days.last().plus(DatePeriod(days = 1)),
        ).map { LocalDate.parse(it.local_date) }.toSet()
        return days.map { TodayWeekDay(it, it == date, it in sessions) }
    }

    private fun sessionsThisWeek(date: LocalDate): Int {
        val days = RoutinePlanner.week(date)
        return store.training.sessionsBetween(
            days.first(),
            days.last().plus(DatePeriod(days = 1)),
        ).size
    }

    private fun plan(date: LocalDate, profile: app.openhealth.data.db.Profile): TodayPlanModel? {
        val routines = store.training.routines().filter { it.archived_at == null }
        val last = store.training.lastFinishedSession()
        val chosen = RoutinePlanner.today(
            routines.map {
                RoutinePlacement(
                    id = it.id,
                    name = it.name,
                    weekday = it.weekday?.toInt(),
                    rotationIndex = it.rotation_index?.toInt(),
                    favourite = it.favourite,
                    folder = it.folder,
                )
            },
            last?.routine_id,
            date,
        ) ?: return null
        val entries = store.training.routineEntries(chosen.routineId)
        val catalogue = store.training.catalogue().associateBy { it.id }
        val coverage = PlanCoverage.compute(
            entries.mapNotNull { entry ->
                catalogue[entry.exercise_id]?.let {
                    PlannedExercise(it, entry.target_sets?.toInt() ?: PlanCoverage.DEFAULT_SETS)
                }
            },
        )
        val setCount = entries.sumOf { it.target_sets ?: PlanCoverage.DEFAULT_SETS.toLong() }
            .toInt()
        val map = if (coverage.isEmpty()) {
            null
        } else {
            BodyMapModel.plan(
                sex = if (profile.sex_at_birth == "FEMALE") BodySex.FEMALE else BodySex.MALE,
                coverage = coverage,
            )
        }
        return TodayPlanModel(
            routineId = chosen.routineId,
            name = chosen.name,
            detail = context.getString(UiR.string.today_week_plan_sets, setCount),
            setCount = setCount,
            map = map,
        )
    }

    // --------------------------------------------------------------------- energy

    private fun energy(
        date: LocalDate,
        profile: app.openhealth.data.db.Profile,
        weightKg: Double?,
        intakeKcal: Double?,
    ): TodayEnergyModel? {
        val intake = intakeKcal ?: 0.0
        val weight = weightKg ?: profile.weight_kg ?: return null
        val sex = profile.sex_at_birth?.let { runCatching { SexAtBirth.valueOf(it) }.getOrNull() }
            ?: return null
        val height = profile.height_cm ?: return null
        val dob = profile.date_of_birth?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return null
        val age = ageOn(dob, date)
        val bmr = (Bmr.mifflinStJeor(sex, weight, height, age) as? FormulaOutcome.Computed)
            ?.value ?: return null
        val stepKcal = store.rollups.forDayAndType(date, ObservationType.STEPS)
            ?.value?.value
            ?.let { StepEnergy.compute(it, weight) }
            ?.let { (it as? FormulaOutcome.Computed)?.value }
        val sessions = store.training.sessionsForDay(date)
        val workoutRanges = sessions.mapNotNull { session ->
            val end = session.ended_at ?: return@mapNotNull null
            val minutes = (end - session.started_at) / MILLIS_PER_MINUTE
            (
                WorkoutEnergy.compute(
                    met = MetActivity.RESISTANCE_TRAINING.met,
                    weightKg = weight,
                    minutes = minutes.toDouble(),
                ) as? FormulaOutcome.Computed
                )?.value
        }
        val workoutKcal = workoutRanges.takeIf { it.isNotEmpty() }?.let { ranges ->
            app.openhealth.core.formula.EnergyRange(
                low = ranges.sumOf { it.low },
                central = ranges.sumOf { it.central },
                high = ranges.sumOf { it.high },
            )
        }
        val targets = targets(date, profile, sex, weight, bmr)
        val balance = (
            EnergyBalanceCalculator.compute(
                intakeKcal = intake,
                bmrKcal = bmr,
                stepsKcal = stepKcal,
                workoutKcal = workoutKcal,
                targetKcal = targets.kcal,
            ) as? FormulaOutcome.Computed
            )?.value ?: return null
        val goalWeight = store.settings.get(SettingKeys.GOAL_WEIGHT_KG)?.toDoubleOrNull()
        val trend = weightTrend()
        val eta = etaState(goalWeight, trend, date)
        val floorKcal = (EnergySafety.floors(sex, bmr) as? FormulaOutcome.Computed)
            ?.value?.minimumIntakeKcal ?: MIN_SAFE_KCAL
        val whatIf = whatIf(goalWeight, trend, profile, floorKcal)
        return TodayEnergyModel(
            intakeKcal = balance.intakeKcal,
            outLowKcal = balance.outKcal.low,
            outHighKcal = balance.outKcal.high,
            netLowKcal = balance.netKcal.low,
            netHighKcal = balance.netKcal.high,
            targetKcal = targets.kcal,
            remainingKcal = balance.remainingKcal,
            macros = listOf(
                macro(Nutrient.PROTEIN, targets.proteinG),
                macro(Nutrient.CARBS, targets.carbsG),
                macro(Nutrient.FAT, targets.fatG),
                macro(Nutrient.FIBRE, targets.fibreG),
            ),
            waterMl = store.rollups.forDayAndType(date, ObservationType.WATER)?.value?.value
                ?: if (targets.waterMl != null) 0.0 else null,
            waterTargetMl = targets.waterMl,
            goalEta = eta,
            whatIf = whatIf,
            hasIntake = intakeKcal != null,
            notes = buildList {
                if (stepKcal == null) {
                    add(context.getString(UiR.string.today_energy_no_steps))
                }
                if (sessions.isEmpty()) {
                    add(context.getString(UiR.string.today_energy_no_workout))
                }
                if (weightKg == null) {
                    add(context.getString(UiR.string.today_energy_profile_weight))
                }
            },
            floorApplied = targets.kcal != null &&
                targets.kcal == (
                    EnergySafety.floors(sex, bmr)
                        as? FormulaOutcome.Computed
                    )?.value?.minimumIntakeKcal,
        )
    }

    private fun macro(nutrient: Nutrient, target: Double?): TodayMacro {
        val type = when (nutrient) {
            Nutrient.PROTEIN -> ObservationType.NUTRITION_PROTEIN
            Nutrient.CARBS -> ObservationType.NUTRITION_CARBS
            Nutrient.FAT -> ObservationType.NUTRITION_FAT
            Nutrient.FIBRE -> ObservationType.NUTRITION_FIBRE
            else -> ObservationType.NUTRITION_ENERGY
        }
        val grams = store.rollups.forDayAndType(today(), type)?.value?.value ?: 0.0
        return TodayMacro(
            nutrient = nutrient,
            grams = grams,
            targetGrams = target,
            fraction = target?.takeIf { it > 0.0 }?.let {
                (grams / it).coerceIn(0.0, 1.0).toFloat()
            },
        )
    }

    private fun targets(
        date: LocalDate,
        profile: app.openhealth.data.db.Profile,
        sex: SexAtBirth,
        weight: Double,
        bmr: Double,
    ): MacroTargets {
        store.nutrition.targetFor(date)?.let { row -> return row.targets }
        val level = profile.activity_level
            ?.let { runCatching { ActivityLevel.valueOf(it) }.getOrNull() }
            ?: ActivityLevel.SEDENTARY
        val tdee =
            (Tdee.fromBmr(bmr, level) as? FormulaOutcome.Computed)?.value
                ?: return MacroTargets.EMPTY
        val mode = when (profile.goal) {
            "lose_fat" -> GoalMode.LOSE
            "gain_muscle" -> GoalMode.GAIN
            else -> GoalMode.MAINTAIN
        }
        val requested = store.settings.get(SettingKeys.GOAL_RATE_KG_PER_WEEK)?.toDoubleOrNull()
            ?: DEFAULT_GOAL_RATE
        val signed = when (mode) {
            GoalMode.LOSE -> -abs(requested)
            GoalMode.GAIN -> abs(requested)
            GoalMode.MAINTAIN -> 0.0
        }
        val rate = (GoalRatePolicy.clamp(mode, signed, weight) as? FormulaOutcome.Computed)?.value
        val energy = rate?.let {
            (TargetSuggestions.energyForRate(tdee, it.appliedKgPerWeek) as? FormulaOutcome.Computed)
                ?.value
        } ?: tdee
        val floor = (EnergySafety.floors(sex, bmr) as? FormulaOutcome.Computed)
            ?.value?.minimumIntakeKcal
        val safeEnergy = if (floor != null) maxOf(energy, floor) else energy
        return (TargetSuggestions.suggest(sex, weight, safeEnergy) as? FormulaOutcome.Computed)
            ?.value ?: MacroTargets.EMPTY
    }

    private fun etaState(
        goalWeight: Double?,
        trend: app.openhealth.core.formula.WeightTrendSeries?,
        date: LocalDate,
    ): GoalEtaState {
        if (goalWeight == null || goalWeight <= 0.0) return GoalEtaState.NoGoalWeight
        if (trend == null || trend.spanDays < GoalEta.MINIMUM_DAYS) {
            return GoalEtaState.NeedsData
        }
        val rate = trend.weeklyRateKg ?: return GoalEtaState.Steady
        if (abs(rate) < MIN_RATE_KG_PER_WEEK) return GoalEtaState.Steady
        val current = trend.points.last().trendKg
        if ((current - goalWeight) * rate > 0.0) return GoalEtaState.Away
        val outcome = GoalEta.fromTrend(goalWeight, trend, date)
        return (outcome as? FormulaOutcome.Computed)?.value?.let {
            GoalEtaState.Window(it.start, it.end)
        } ?: GoalEtaState.Steady
    }

    private fun whatIf(
        goalWeight: Double?,
        trend: app.openhealth.core.formula.WeightTrendSeries?,
        profile: app.openhealth.data.db.Profile,
        minimumTargetKcal: Double,
    ): GoalWhatIfModel? {
        if (goalWeight == null || goalWeight <= 0.0 || trend == null) return null
        if (trend.spanDays < GoalEta.MINIMUM_DAYS) return null
        val current = trend.points.last().trendKg
        val mode = when (profile.goal) {
            "lose_fat" -> GoalMode.LOSE
            "gain_muscle" -> GoalMode.GAIN
            else -> GoalMode.MAINTAIN
        }
        val applied = (
            GoalRatePolicy.clamp(mode, signedRate(mode), current)
                as? FormulaOutcome.Computed
            )?.value
        val allowed = applied?.allowedRangeKgPerWeek
        val dailyMin = allowed?.start?.times(KCAL_PER_KG)?.div(DAYS_PER_WEEK)
            ?: -DEFAULT_WHAT_IF_KCAL
        val dailyMax = allowed?.endInclusive?.times(KCAL_PER_KG)?.div(DAYS_PER_WEEK)
            ?: DEFAULT_WHAT_IF_KCAL
        return GoalWhatIfModel(
            currentWeightKg = current,
            goalWeightKg = goalWeight,
            initialDailyDeltaKcal = (applied?.appliedKgPerWeek ?: 0.0) *
                KCAL_PER_KG / DAYS_PER_WEEK,
            minimumDailyDeltaKcal = minOf(dailyMin, dailyMax, -MIN_WHAT_IF_KCAL),
            maximumDailyDeltaKcal = maxOf(dailyMin, dailyMax, MIN_WHAT_IF_KCAL),
            minimumTargetKcal = minimumTargetKcal,
        )
    }

    private fun signedRate(mode: GoalMode): Double {
        val requested = store.settings.get(SettingKeys.GOAL_RATE_KG_PER_WEEK)?.toDoubleOrNull()
            ?: DEFAULT_GOAL_RATE
        return when (mode) {
            GoalMode.LOSE -> -abs(requested)
            GoalMode.GAIN -> abs(requested)
            GoalMode.MAINTAIN -> 0.0
        }
    }

    // ---------------------------------------------------------------------- cards

    private fun stepsCard(date: LocalDate, steps: Int?): TodayStepsModel? {
        if (steps == null) return null
        val rollup = store.rollups.forDayAndType(date, ObservationType.STEPS)
        val sourceLabel = rollup?.sourceId?.let { store.sources.byId(it)?.label }
        fun sameSource(type: ObservationType): Double? = store.rollups.forDayAndType(date, type)
            ?.takeIf { it.sourceId == rollup?.sourceId }
            ?.value?.value
        return TodayStepsModel(
            steps = steps,
            goal = stepGoal(),
            activeMinutes = sameSource(ObservationType.ACTIVE_MINUTES),
            distanceKm = sameSource(ObservationType.DISTANCE)?.div(METRES_PER_KM),
            sourceLabel = sourceLabel,
        )
    }

    private fun stepGoal(): Int = store.settings.get(SettingKeys.STEP_GOAL)
        ?.toIntOrNull()
        ?.takeIf { it in MIN_STEP_GOAL..MAX_STEP_GOAL }
        ?: DEFAULT_STEP_GOAL

    private fun weightCard(
        weight: WeightReading?,
        trend: app.openhealth.core.formula.WeightTrendSeries?,
    ): TodayWeightModel? {
        if (weight == null) return null
        val date = today()
        val rollups = store.rollups.forTypeRange(
            ObservationType.WEIGHT,
            date.minus(DatePeriod(days = SPARK_DAYS - 1)),
            date,
        )
        val latestRollup = rollups.maxByOrNull { it.localDate }
        return TodayWeightModel(
            weightKg = weight.kg,
            trendKg = trend?.points?.lastOrNull()?.trendKg,
            weeklyChangeKg = trend?.weeklyRateKg,
            goalKg = store.settings.get(SettingKeys.GOAL_WEIGHT_KG)?.toDoubleOrNull(),
            spark = trend?.points?.map { ChartPoint(it.date, it.trendKg) }
                ?: rollups.map { ChartPoint(it.localDate, it.value.value) },
            sparkDescription = weightDescription(weight, trend),
            sourceLabel = latestRollup?.sourceId?.let { store.sources.byId(it)?.label },
        )
    }

    private fun due(
        date: LocalDate,
        moduleOn: (HealthModule) -> Boolean,
        weightNeutral: Boolean,
    ): List<TodayDueItem> {
        val due = mutableListOf<TodayDueItem>()
        if (moduleOn(HealthModule.VITALS)) {
            val now = clock.now()
            val bp = store.body.vitalsForTypeBetween(
                ObservationType.BLOOD_PRESSURE_SYSTOLIC.name,
                (now - 7.days).toEpochMilliseconds(),
                now.toEpochMilliseconds(),
            )
            if (bp.isEmpty()) {
                due += TodayDueItem(
                    context.getString(UiR.string.today_due_vitals_title),
                    context.getString(UiR.string.today_due_vitals_body),
                    TodayDueAction.VITALS,
                )
            }
        }
        if (store.notes.forDay(date).isEmpty()) {
            due += TodayDueItem(
                context.getString(UiR.string.today_due_journal_title),
                context.getString(UiR.string.today_due_journal_body),
                TodayDueAction.NOTE,
            )
        }
        if (moduleOn(HealthModule.SLEEP) &&
            store.rollups.forDayAndType(
                date.minus(DatePeriod(days = 1)),
                ObservationType.SLEEP_DURATION,
            ) ==
            null
        ) {
            due += TodayDueItem(
                context.getString(UiR.string.today_due_sleep_title),
                context.getString(UiR.string.today_due_sleep_body),
                TodayDueAction.SLEEP,
            )
        }
        if (!weightNeutral &&
            moduleOn(HealthModule.BODY) &&
            store.rollups.forTypeRange(
                ObservationType.WEIGHT,
                date.minus(DatePeriod(days = 6)),
                date,
            ).isEmpty()
        ) {
            due += TodayDueItem(
                context.getString(UiR.string.today_due_weight_title),
                context.getString(UiR.string.today_due_weight_body),
                TodayDueAction.WEIGHT,
            )
        }
        if (moduleOn(HealthModule.NUTRITION) &&
            store.rollups.forDayAndType(date, ObservationType.NUTRITION_ENERGY) == null
        ) {
            due += TodayDueItem(
                context.getString(UiR.string.today_due_food_title),
                context.getString(UiR.string.today_due_food_body),
                TodayDueAction.FOOD,
            )
        }
        return due.take(MAX_DUE)
    }

    // --------------------------------------------------------------------- charts

    private fun charts(
        date: LocalDate,
        energy: TodayEnergyModel?,
        steps: Int?,
        weight: WeightReading?,
        trend: app.openhealth.core.formula.WeightTrendSeries?,
    ): List<TodayChartModel> = buildList {
        if (energy != null) {
            add(
                TodayChartModel(
                    chart = ChartId.ENERGY_RING,
                    description = context.getString(
                        UiR.string.today_chart_energy_desc,
                        decimal(energy.intakeKcal),
                        decimal(energy.outLowKcal),
                        decimal(energy.outHighKcal),
                    ),
                    table = ChartTableModel(
                        title = context.getString(UiR.string.today_energy_title),
                        columns = listOf(
                            context.getString(UiR.string.chart_table_value),
                            context.getString(UiR.string.chart_table_reading),
                        ),
                        rows = energyRows(energy),
                        citation = citationFor(ChartId.ENERGY_RING),
                    ),
                ),
            )
        }
        if (steps != null) {
            add(
                TodayChartModel(
                    chart = ChartId.STEPS_RING,
                    description = context.getString(
                        UiR.string.today_chart_steps_desc,
                        numberFormat.format(steps),
                        numberFormat.format(stepGoal()),
                    ),
                    table = ChartTableModel(
                        title = context.getString(UiR.string.today_steps_title),
                        columns = listOf(
                            context.getString(UiR.string.chart_table_value),
                            context.getString(UiR.string.chart_table_reading),
                        ),
                        rows = listOf(
                            listOf(
                                context.getString(UiR.string.today_steps_title),
                                numberFormat.format(steps),
                            ),
                            listOf(
                                context.getString(UiR.string.today_steps_goal_label),
                                numberFormat.format(stepGoal()),
                            ),
                        ),
                        citation = citationFor(ChartId.STEPS_RING),
                    ),
                ),
            )
        }
        if (weight != null) {
            val rollups = store.rollups.forTypeRange(
                ObservationType.WEIGHT,
                date.minus(DatePeriod(days = SPARK_DAYS - 1)),
                date,
            )
            add(
                TodayChartModel(
                    chart = ChartId.WEIGHT_SPARKLINE,
                    description = weightDescription(weight, trend),
                    table = ChartTableModel(
                        title = context.getString(UiR.string.today_weight_title),
                        columns = listOf(
                            context.getString(UiR.string.chart_table_date),
                            context.getString(UiR.string.stats_table_weight),
                        ),
                        rows = trend?.points?.map {
                            listOf(it.date.toString(), decimal(it.trendKg, 1))
                        } ?: rollups.map {
                            listOf(it.localDate.toString(), decimal(it.value.value, 1))
                        },
                        citation = citationFor(ChartId.WEIGHT_SPARKLINE),
                    ),
                ),
            )
        }
    }

    private fun energyRows(energy: TodayEnergyModel): List<List<String>> = buildList {
        add(listOf(context.getString(UiR.string.today_energy_in), decimal(energy.intakeKcal)))
        add(
            listOf(
                context.getString(UiR.string.today_energy_out),
                "${decimal(energy.outLowKcal)}–${decimal(energy.outHighKcal)}",
            ),
        )
        add(
            listOf(
                context.getString(UiR.string.today_energy_net),
                "${decimal(energy.netLowKcal)}–${decimal(energy.netHighKcal)}",
            ),
        )
        energy.targetKcal?.let {
            add(listOf(context.getString(UiR.string.today_energy_target), decimal(it)))
        }
        energy.macros.forEach { macro ->
            add(
                listOf(
                    context.getString(macroLabelRes(macro.nutrient)),
                    "${decimal(macro.grams)} g",
                ),
            )
        }
    }

    private fun weightDescription(
        weight: WeightReading,
        trend: app.openhealth.core.formula.WeightTrendSeries?,
    ): String {
        val change = trend?.weeklyRateKg
        return if (change == null) {
            context.getString(UiR.string.today_chart_weight_desc_flat, decimal(weight.kg, 1))
        } else {
            context.getString(
                UiR.string.today_chart_weight_desc,
                decimal(weight.kg, 1),
                decimal(change, 1),
            )
        }
    }

    // ----------------------------------------------------------------- provenance

    private fun provenances(
        date: LocalDate,
        energy: TodayEnergyModel?,
        steps: Int?,
        weight: WeightReading?,
    ): List<TodayProvenance> = buildList {
        val energyKcal = energy?.intakeKcal
        if (energyKcal != null) {
            val entries = store.nutrition.entriesForDay(date)
            if (entries.isNotEmpty()) {
                val latest = entries.maxByOrNull { it.recordedAt }!!
                add(
                    TodayProvenance(
                        TodayValue.ENERGY_IN,
                        ValueProvenance(
                            sourceLabel = context.getString(
                                UiR.string.today_provenance_food_entries,
                            ),
                            method = context.getString(UiR.string.today_provenance_sum),
                            happenedAt = latest.happenedAt,
                            recordedAt = latest.recordedAt,
                            localDate = date,
                            zoneId = zone.id,
                            quality = context.getString(
                                UiR.string.today_provenance_from_entries,
                                entries.size,
                            ),
                            edited = entries.any { it.edited },
                            valueText = "${decimal(
                                energyKcal,
                            )} ${context.getString(UiR.string.unit_kcal)}",
                        ),
                    ),
                )
            }
        }
        if (energy != null && energy.hasIntake) {
            val calculated = ValueProvenance(
                sourceLabel = context.getString(UiR.string.today_provenance_calculated),
                method = context.getString(UiR.string.today_provenance_energy_method),
                happenedAt = clock.now(),
                recordedAt = clock.now(),
                localDate = date,
                zoneId = zone.id,
                valueText = "${decimal(energy.outLowKcal)}–${decimal(energy.outHighKcal)} " +
                    context.getString(UiR.string.unit_kcal),
            )
            add(TodayProvenance(TodayValue.ENERGY_OUT, calculated))
            add(
                TodayProvenance(
                    TodayValue.ENERGY_NET,
                    calculated.copy(
                        valueText = "${decimal(energy.netLowKcal)}–" +
                            "${decimal(energy.netHighKcal)} " +
                            context.getString(UiR.string.unit_kcal),
                    ),
                ),
            )
        }
        val water = store.rollups.forDayAndType(date, ObservationType.WATER)
        if (water != null) {
            add(
                TodayProvenance(
                    TodayValue.WATER,
                    ValueProvenance(
                        sourceLabel = water.sourceId?.let { store.sources.byId(it)?.label }
                            ?: context.getString(UiR.string.vitals_provenance_manual),
                        happenedAt = water.watermark,
                        recordedAt = water.watermark,
                        localDate = date,
                        zoneId = zone.id,
                        quality = qualityOf(water.dedupReason),
                        valueText = "${decimal(water.value.value)} " +
                            context.getString(UiR.string.unit_ml),
                    ),
                ),
            )
        }
        if (steps != null) {
            val rollup = store.rollups.forDayAndType(date, ObservationType.STEPS)
            // Steps are the type two sources most often both report (`pr.md` §7.6), so
            // the sheet reads the winner's own observation, never the loser's device.
            val observation = winningObservation(ObservationType.STEPS, date, rollup)
            add(
                TodayProvenance(
                    TodayValue.STEPS,
                    ValueProvenance(
                        sourceLabel = rollup?.sourceId?.let { store.sources.byId(it)?.label }
                            ?: context.getString(UiR.string.vitals_provenance_manual),
                        method = observation?.provenance?.method?.let(::methodLabel),
                        happenedAt = observation?.provenance?.happenedAt
                            ?: rollup?.watermark ?: clock.now(),
                        recordedAt = observation?.provenance?.recordedAt
                            ?: rollup?.watermark ?: clock.now(),
                        localDate = date,
                        zoneId = zone.id,
                        device = observation?.provenance?.device,
                        quality = qualityOf(rollup?.dedupReason),
                        edited = observation?.userEdited ?: false,
                        rawRecord = observation?.provenance?.rawRecordId != null,
                        valueText = numberFormat.format(steps),
                    ),
                ),
            )
        }
        if (weight != null) {
            val rollup = store.rollups.forDayAndType(weight.date, ObservationType.WEIGHT)
            val observation = winningObservation(
                ObservationType.WEIGHT,
                weight.date,
                rollup,
            )
            add(
                TodayProvenance(
                    TodayValue.WEIGHT,
                    ValueProvenance(
                        sourceLabel = rollup?.sourceId?.let { store.sources.byId(it)?.label }
                            ?: context.getString(UiR.string.vitals_provenance_manual),
                        method = observation?.provenance?.method?.let(::methodLabel),
                        happenedAt = observation?.provenance?.happenedAt
                            ?: rollup?.watermark ?: clock.now(),
                        recordedAt = observation?.provenance?.recordedAt
                            ?: rollup?.watermark ?: clock.now(),
                        localDate = weight.date,
                        zoneId = zone.id,
                        device = observation?.provenance?.device,
                        quality = qualityOf(rollup?.dedupReason),
                        edited = observation?.userEdited ?: false,
                        rawRecord = observation?.provenance?.rawRecordId != null,
                        valueText = "${decimal(
                            weight.kg,
                            1,
                        )} ${context.getString(UiR.string.unit_kg)}",
                    ),
                ),
            )
        }
    }

    /** The observation behind the rollup's winning source, never another source's time. */
    private fun winningObservation(
        type: ObservationType,
        date: LocalDate,
        rollup: app.openhealth.core.rollup.DailyRollup?,
    ): app.openhealth.core.observation.Observation? = store.observations.forDayAndType(date, type)
        .filter { rollup?.sourceId == null || it.provenance.sourceId == rollup.sourceId }
        .maxByOrNull { it.provenance.happenedAt }

    private fun qualityOf(reason: String?): String? = when (reason) {
        null -> null
        "ONE_SOURCE_WINS" -> context.getString(UiR.string.today_quality_one_source)
        "GAP_FALLBACK" -> context.getString(UiR.string.today_quality_gap)
        "MANUAL_OVERRIDE" -> context.getString(UiR.string.today_quality_manual)
        "INCOMPLETE_DAY" -> context.getString(UiR.string.today_quality_incomplete)
        "LATER_WINS" -> context.getString(UiR.string.today_quality_later)
        "DUPLICATE_MERGED" -> context.getString(UiR.string.today_quality_merged)
        "MOST_DETAILED" -> context.getString(UiR.string.today_quality_detailed)
        "NO_DEDUP" -> context.getString(UiR.string.today_quality_no_dedup)
        else -> reason
    }

    private fun methodLabel(method: MeasurementMethod): String = context.getString(
        when (method) {
            MeasurementMethod.SCALE -> UiR.string.vitals_method_scale
            MeasurementMethod.BIA -> UiR.string.vitals_method_bia
            MeasurementMethod.DEXA -> UiR.string.vitals_method_dexa
            MeasurementMethod.MANUAL_ENTRY -> UiR.string.vitals_method_manual
            MeasurementMethod.CUFF -> UiR.string.vitals_method_cuff
            MeasurementMethod.DEVICE_SENSOR -> UiR.string.vitals_method_device
            MeasurementMethod.FILE_IMPORT -> UiR.string.vitals_method_file
            MeasurementMethod.CGM_SENSOR -> UiR.string.vitals_method_device
            MeasurementMethod.LAB_ASSAY -> UiR.string.vitals_method_other
            MeasurementMethod.WATCH_OPTICAL -> UiR.string.vitals_method_device
            MeasurementMethod.OTHER -> UiR.string.vitals_method_other
        },
    )

    private fun citationFor(id: ChartId): String = context.getString(
        UiR.string.chart_citation,
        app.openhealth.core.citation.Citations.of(ChartRegistry.of(id).citation).title,
    )

    private fun macroLabelRes(nutrient: Nutrient): Int = when (nutrient) {
        Nutrient.PROTEIN -> UiR.string.nutrient_protein
        Nutrient.CARBS -> UiR.string.nutrient_carbs
        Nutrient.FAT -> UiR.string.nutrient_fat
        Nutrient.FIBRE -> UiR.string.nutrient_fibre
        else -> UiR.string.nutrient_energy
    }

    // ------------------------------------------------------------------- formatting

    private fun decimal(value: Double, decimals: Int = 0): String =
        numberFormat.apply { maximumFractionDigits = decimals }.format(value)

    private fun durationText(minutes: Double): String {
        val total = minutes.roundToInt()
        val hours = total / 60
        val remainder = total % 60
        return if (hours > 0) {
            if (remainder == 0) {
                context.getString(UiR.string.today_hours, hours)
            } else {
                context.getString(UiR.string.today_hours_minutes, hours, remainder)
            }
        } else {
            context.getString(UiR.string.today_minutes, remainder)
        }
    }

    private fun ageOn(dateOfBirth: LocalDate, today: LocalDate): Int {
        var age = today.year - dateOfBirth.year
        val hadBirthday = today.month.ordinal > dateOfBirth.month.ordinal ||
            (today.month == dateOfBirth.month && today.day >= dateOfBirth.day)
        if (!hadBirthday) age -= 1
        return age.coerceAtLeast(0)
    }
}

private const val MILLIS_PER_SECOND = 1_000.0
private const val MILLIS_PER_MINUTE = 60_000L
private const val METRES_PER_KM = 1_000.0
private const val TREND_DAYS = 90
private const val SPARK_DAYS = 30
private const val DEFAULT_SLEEP_NEED_MINUTES = 480.0
private const val SLEEP_QUERY_DAYS = 8
private val SLEEP_TYPES = listOf(
    ObservationType.SLEEP_DURATION,
    ObservationType.SLEEP_IN_BED,
    ObservationType.SLEEP_DEEP,
    ObservationType.SLEEP_LIGHT,
    ObservationType.SLEEP_REM,
    ObservationType.SLEEP_AWAKE,
)
private const val DEFAULT_GOAL_RATE = 0.25
private const val DEFAULT_STEP_GOAL = 8_000
private const val MIN_STEP_GOAL = 1_000
private const val MAX_STEP_GOAL = 50_000
private const val MAX_DUE = 5
private const val KCAL_PER_KG = 7_700.0
private const val DAYS_PER_WEEK = 7.0
private const val MIN_RATE_KG_PER_WEEK = 0.05
private const val DEFAULT_WHAT_IF_KCAL = 500.0
private const val MIN_WHAT_IF_KCAL = 100.0
private const val MIN_SAFE_KCAL = 1_200.0
