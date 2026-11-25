@file:Suppress(
    "LargeClass",
    "TooManyFunctions",
    "LongParameterList",
    "LongMethod",
    "CyclomaticComplexMethod",
    "ReturnCount",
    "MagicNumber",
)

package app.openhealth.android.stats

import android.content.Context
import app.openhealth.android.preferences.Preferences
import app.openhealth.core.body.Muscle
import app.openhealth.core.formula.DailyValue
import app.openhealth.core.formula.FormulaOutcome
import app.openhealth.core.formula.SleepDebt
import app.openhealth.core.formula.SleepNight
import app.openhealth.core.formula.SleepSample
import app.openhealth.core.formula.SleepSession
import app.openhealth.core.formula.SleepSessions
import app.openhealth.core.formula.WeightEntry
import app.openhealth.core.formula.WeightTrend
import app.openhealth.core.ids.SourceId
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.preferences.HealthModule
import app.openhealth.core.preferences.SensitiveMetric
import app.openhealth.core.seam.ClockSeam
import app.openhealth.core.stats.StatsMath
import app.openhealth.core.training.PersonalRecords
import app.openhealth.core.training.TrainingSetRecord
import app.openhealth.data.LocalStore
import app.openhealth.data.repo.SettingKeys
import app.openhealth.ui.R as UiR
import app.openhealth.ui.components.ChartBar
import app.openhealth.ui.components.ChartPoint
import app.openhealth.ui.components.ChartTableModel
import app.openhealth.ui.components.HeatCellUi
import app.openhealth.ui.components.StatBarRow
import app.openhealth.ui.screens.stats.ChartId
import app.openhealth.ui.screens.stats.ChartRegistry
import app.openhealth.ui.screens.stats.MacroAverage
import app.openhealth.ui.screens.stats.StatsCard
import app.openhealth.ui.screens.stats.StatsHue
import app.openhealth.ui.screens.stats.StatsKpi
import app.openhealth.ui.screens.stats.StatsModel
import app.openhealth.ui.screens.stats.StatsPlot
import app.openhealth.ui.screens.stats.StatsRecords
import app.openhealth.ui.screens.stats.StatsSegment
import app.openhealth.ui.screens.stats.StatsStackSegment
import java.text.NumberFormat
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Builds each Stats segment from the rollups (`pr.md` §6.16). A missing day is a gap in
 * the line and a named row in its table; a card only exists when it has data, and every
 * card carries the table and the registry formula the screen needs.
 */
class StatsPresenter(
    private val context: Context,
    private val store: LocalStore,
    private val clock: ClockSeam,
    private val zone: TimeZone,
) {
    private val numberFormat: NumberFormat = NumberFormat.getNumberInstance()

    fun model(segment: StatsSegment, preferences: Preferences): StatsModel {
        val date = today()
        val hidden = preferences.displayPolicy
        val modules = preferences.modules
        val cards = when (segment) {
            StatsSegment.OVERVIEW -> overview(date, hidden, modules)
            StatsSegment.BODY -> body(date, hidden, modules)
            StatsSegment.TRAINING -> training(date, preferences)
            StatsSegment.SLEEP -> sleep(date, preferences)
            StatsSegment.NUTRITION -> nutrition(date, hidden, modules)
        }
        return StatsModel(
            today = date,
            segment = segment,
            kpis = kpis(segment, date, hidden, modules),
            cards = cards.first,
            records = cards.second,
            demo = preferences.demo,
            weightNeutral = preferences.weightNeutral,
            modules = preferences.modules,
        )
    }

    // -------------------------------------------------------------------- overview

    private fun overview(
        date: LocalDate,
        hidden: app.openhealth.core.preferences.DisplayPolicy,
        modules: Set<HealthModule>,
    ): Pair<List<StatsCard>, StatsRecords?> {
        val cards = mutableListOf<StatsCard>()
        if (HealthModule.BODY in modules && hidden.shows(SensitiveMetric.WEIGHT)) {
            weightCard(date)?.let(cards::add)
        }
        if (HealthModule.STEPS in modules) {
            stepsCard(date)?.let(cards::add)
        }
        if (HealthModule.NUTRITION in modules && hidden.shows(SensitiveMetric.CALORIES)) {
            energyCard(date)?.let(cards::add)
        }
        return cards to null
    }

    // ------------------------------------------------------------------------ body

    private fun body(
        date: LocalDate,
        hidden: app.openhealth.core.preferences.DisplayPolicy,
        modules: Set<HealthModule>,
    ): Pair<List<StatsCard>, StatsRecords?> {
        if (HealthModule.BODY !in modules) return emptyList<StatsCard>() to null
        val cards = mutableListOf<StatsCard>()
        if (hidden.shows(SensitiveMetric.WEIGHT)) {
            weightCard(date)?.let(cards::add)
        }
        if (hidden.shows(SensitiveMetric.BODY_FAT)) {
            bodyFatCard(date)?.let(cards::add)
        }
        return cards to null
    }

    // -------------------------------------------------------------------- training

    private fun training(
        date: LocalDate,
        preferences: Preferences,
    ): Pair<List<StatsCard>, StatsRecords?> {
        if (HealthModule.TRAINING !in preferences.modules) {
            return emptyList<StatsCard>() to null
        }
        val cards = mutableListOf<StatsCard>()
        volumeCard(date)?.let(cards::add)
        consistencyCard(date)?.let(cards::add)
        muscleCard(date)?.let(cards::add)
        return cards.toList() to records()
    }

    // ----------------------------------------------------------------------- sleep

    private fun sleep(
        date: LocalDate,
        preferences: Preferences,
    ): Pair<List<StatsCard>, StatsRecords?> {
        if (HealthModule.SLEEP !in preferences.modules) {
            return emptyList<StatsCard>() to null
        }
        val cards = mutableListOf<StatsCard>()
        sleepCard(date)?.let(cards::add)
        stagesCard(date)?.let(cards::add)
        return cards.toList() to null
    }

    // ------------------------------------------------------------------- nutrition

    private fun nutrition(
        date: LocalDate,
        hidden: app.openhealth.core.preferences.DisplayPolicy,
        modules: Set<HealthModule>,
    ): Pair<List<StatsCard>, StatsRecords?> {
        if (HealthModule.NUTRITION !in modules ||
            !hidden.shows(SensitiveMetric.CALORIES)
        ) {
            return emptyList<StatsCard>() to null
        }
        val cards = mutableListOf<StatsCard>()
        energyCard(date, last = SEASON_DAYS, id = ChartId.ENERGY_TREND)?.let(cards::add)
        proteinCard(date)?.let(cards::add)
        macroCard(date)?.let(cards::add)
        return cards.toList() to null
    }

    // ------------------------------------------------------------------------- KPIs

    private fun kpis(
        segment: StatsSegment,
        date: LocalDate,
        hidden: app.openhealth.core.preferences.DisplayPolicy,
        modules: Set<HealthModule>,
    ): List<StatsKpi> = when (segment) {
        StatsSegment.OVERVIEW -> buildList {
            add(
                StatsKpi(
                    value = numberFormat.format(sessionsThisWeek(date)),
                    label = context.getString(UiR.string.stats_kpi_sessions),
                ),
            )
            if (HealthModule.STEPS in modules) {
                add(
                    stepsAverage(date, 30)?.let {
                        StatsKpi(
                            value = numberFormat.format(it.roundToInt()),
                            label = context.getString(UiR.string.stats_kpi_steps),
                            chart = ChartId.STEPS_TREND,
                        )
                    } ?: StatsKpi(
                        value = DASH,
                        label = context.getString(UiR.string.stats_kpi_steps),
                    ),
                )
            }
            if (HealthModule.BODY in modules && hidden.shows(SensitiveMetric.WEIGHT)) {
                add(
                    weightChange(date, 30)?.let {
                        StatsKpi(
                            value = signed(it, 1),
                            label = context.getString(UiR.string.stats_kpi_weight),
                            unit = context.getString(UiR.string.unit_kg),
                            chart = ChartId.WEIGHT_TREND,
                        )
                    } ?: StatsKpi(
                        value = DASH,
                        label = context.getString(UiR.string.stats_kpi_weight),
                        unit = context.getString(UiR.string.unit_kg),
                    ),
                )
            }
        }

        StatsSegment.BODY -> if (HealthModule.BODY !in modules ||
            (
                !hidden.shows(SensitiveMetric.WEIGHT) &&
                    !hidden.shows(SensitiveMetric.BODY_FAT)
                )
        ) {
            emptyList()
        } else {
            listOf(
                latest(ObservationType.WEIGHT, date)?.let {
                    StatsKpi(
                        value = decimal(it, 1),
                        label = context.getString(UiR.string.stats_card_weight_title),
                        unit = context.getString(UiR.string.unit_kg),
                        chart = ChartId.WEIGHT_TREND,
                    )
                } ?: StatsKpi(DASH, context.getString(UiR.string.stats_card_weight_title)),
                weeklyRate(date)?.let {
                    StatsKpi(
                        value = signed(it, 2),
                        label = context.getString(UiR.string.stats_weekly_rate_label),
                        unit = context.getString(UiR.string.stats_unit_kg_week),
                        chart = ChartId.WEIGHT_TREND,
                    )
                } ?: StatsKpi(
                    DASH,
                    context.getString(UiR.string.stats_weekly_rate_label),
                ),
                latest(ObservationType.BODY_FAT_PERCENT, date)?.let {
                    StatsKpi(
                        value = decimal(it, 1),
                        label = context.getString(UiR.string.stats_card_body_fat_title),
                        unit = context.getString(UiR.string.unit_percent),
                        chart = ChartId.BODY_FAT_TREND,
                    )
                } ?: StatsKpi(DASH, context.getString(UiR.string.stats_card_body_fat_title)),
            )
        }

        StatsSegment.TRAINING -> if (HealthModule.TRAINING !in modules) {
            emptyList()
        } else {
            listOf(
                StatsKpi(
                    value = numberFormat.format(weeklyTotal(date, ObservationType.TRAINING_VOLUME)),
                    label = context.getString(UiR.string.stats_kpi_volume),
                    unit = context.getString(UiR.string.unit_kg),
                    chart = ChartId.TRAINING_VOLUME,
                ),
                StatsKpi(
                    value = numberFormat.format(weeklyTotal(date, ObservationType.TRAINING_SETS)),
                    label = context.getString(UiR.string.stats_kpi_sets),
                    chart = ChartId.TRAINING_CONSISTENCY,
                ),
                StatsKpi(
                    value = numberFormat.format(sessionsThisWeek(date)),
                    label = context.getString(UiR.string.stats_kpi_sessions),
                ),
            )
        }

        StatsSegment.SLEEP -> if (HealthModule.SLEEP !in modules) {
            emptyList()
        } else {
            val nights = sleepNights(date, 14)
            listOf(
                StatsKpi(
                    value = nights.map { it.totals.asleepMinutes }.average().takeIf { !it.isNaN() }
                        ?.let { duration(it) } ?: DASH,
                    label = context.getString(UiR.string.stats_kpi_sleep),
                    chart = ChartId.SLEEP_DURATION,
                ),
                StatsKpi(
                    value = sleepDebt(date)?.let { duration(it.debtMinutes) } ?: DASH,
                    label = context.getString(UiR.string.stats_kpi_debt),
                    chart = ChartId.SLEEP_DURATION,
                ),
                StatsKpi(
                    value = sleepDebt(date)?.let { duration(it.consistencyMinutes) } ?: DASH,
                    label = context.getString(UiR.string.stats_kpi_consistency),
                    chart = ChartId.SLEEP_DURATION,
                ),
            )
        }

        StatsSegment.NUTRITION -> if (HealthModule.NUTRITION !in modules ||
            !hidden.shows(SensitiveMetric.CALORIES)
        ) {
            emptyList()
        } else {
            listOf(
                average(date, ObservationType.NUTRITION_ENERGY, SEASON_DAYS)?.let {
                    StatsKpi(
                        value = numberFormat.format(it.roundToInt()),
                        label = context.getString(UiR.string.stats_kpi_energy),
                        unit = context.getString(UiR.string.unit_kcal),
                        chart = ChartId.ENERGY_TREND,
                    )
                } ?: StatsKpi(DASH, context.getString(UiR.string.stats_kpi_energy)),
                average(date, ObservationType.NUTRITION_PROTEIN, SEASON_DAYS)?.let {
                    StatsKpi(
                        value = numberFormat.format(it.roundToInt()),
                        label = context.getString(UiR.string.stats_kpi_protein),
                        unit = context.getString(UiR.string.unit_g),
                        chart = ChartId.PROTEIN_TREND,
                    )
                } ?: StatsKpi(DASH, context.getString(UiR.string.stats_kpi_protein)),
                StatsKpi(
                    value = context.getString(
                        UiR.string.stats_days_logged,
                        loggedDays(date, ObservationType.NUTRITION_ENERGY, SEASON_DAYS),
                        SEASON_DAYS,
                    ),
                    label = context.getString(UiR.string.stats_kpi_days),
                ),
            )
        }
    }

    // ----------------------------------------------------------------------- cards

    private fun weightCard(date: LocalDate): StatsCard? {
        val rollups = rollups(date, ObservationType.WEIGHT, SEASON_DAYS)
        if (rollups.isEmpty()) return null
        val trend = (
            WeightTrend.ewma(rollups.map { WeightEntry(it.first, it.second) })
                as? FormulaOutcome.Computed
            )?.value
        val change = trend?.points?.let { points ->
            if (points.size < 2) null else points.last().trendKg - points.first().trendKg
        }
        val description = when {
            change == null -> context.getString(UiR.string.stats_desc_weight_flat)
            abs(change) < 0.05 -> context.getString(
                UiR.string.stats_desc_weight_same,
                decimal(trend!!.points.first().trendKg, 1),
            )

            else -> context.getString(
                if (change < 0) {
                    UiR.string.stats_desc_weight_down
                } else {
                    UiR.string.stats_desc_weight_up
                },
                decimal(abs(change), 1),
                decimal(trend!!.points.first().trendKg, 1),
                decimal(trend.points.last().trendKg, 1),
            )
        }
        return StatsCard(
            chart = ChartId.WEIGHT_TREND,
            title = context.getString(UiR.string.stats_card_weight_title),
            description = description,
            plot = StatsPlot.Line(
                trend?.points?.map { ChartPoint(it.date, it.trendKg) }
                    ?: rollups.map { ChartPoint(it.first, it.second) },
            ),
            table = table(
                title = context.getString(UiR.string.stats_card_weight_title),
                columns = listOf(
                    context.getString(UiR.string.chart_table_date),
                    context.getString(UiR.string.stats_table_weight),
                ),
                rows = trend?.points?.map {
                    shortDate(it.date) to decimal(it.trendKg, 1)
                } ?: rollups.map { shortDate(it.first) to decimal(it.second, 1) },
                chart = ChartId.WEIGHT_TREND,
            ),
            unitText = context.getString(UiR.string.unit_kg),
            decimals = 1,
            goal = store.settings.get(SettingKeys.GOAL_WEIGHT_KG)?.toDoubleOrNull(),
        )
    }

    private fun stepsCard(date: LocalDate): StatsCard? {
        val rollups = rollups(date, ObservationType.STEPS, SEASON_DAYS)
        if (rollups.isEmpty()) return null
        val average = rollups.map { it.second }.average().roundToInt()
        return StatsCard(
            chart = ChartId.STEPS_TREND,
            title = context.getString(UiR.string.stats_card_steps_title),
            description = context.getString(
                UiR.string.stats_desc_steps,
                numberFormat.format(average),
            ),
            plot = StatsPlot.Line(rollups.map { ChartPoint(it.first, it.second) }),
            table = table(
                title = context.getString(UiR.string.stats_card_steps_title),
                columns = listOf(
                    context.getString(UiR.string.chart_table_date),
                    context.getString(UiR.string.stats_table_steps),
                ),
                rows = rollups.map { shortDate(it.first) to numberFormat.format(it.second) },
                chart = ChartId.STEPS_TREND,
            ),
            unitText = context.getString(UiR.string.stats_unit_steps),
            decimals = 0,
            footer = context.getString(
                UiR.string.stats_days_logged,
                rollups.size,
                SEASON_DAYS,
            ),
        )
    }

    private fun energyCard(
        date: LocalDate,
        last: Int = SEASON_DAYS,
        id: ChartId = ChartId.ENERGY_TREND,
    ): StatsCard? {
        val rollups = rollups(date, ObservationType.NUTRITION_ENERGY, last)
        if (rollups.isEmpty()) return null
        val average = rollups.map { it.second }.average()
        return StatsCard(
            chart = id,
            title = context.getString(UiR.string.stats_card_energy_title),
            description = context.getString(
                UiR.string.stats_desc_energy,
                numberFormat.format(average.roundToInt()),
            ),
            plot = StatsPlot.Line(rollups.map { ChartPoint(it.first, it.second) }),
            table = table(
                title = context.getString(UiR.string.stats_card_energy_title),
                columns = listOf(
                    context.getString(UiR.string.chart_table_date),
                    context.getString(UiR.string.stats_table_kcal),
                ),
                rows = rollups.map { shortDate(it.first) to numberFormat.format(it.second) },
                chart = id,
            ),
            unitText = context.getString(UiR.string.unit_kcal),
            footer = context.getString(
                UiR.string.stats_days_logged,
                rollups.size,
                last,
            ),
        )
    }

    private fun proteinCard(date: LocalDate): StatsCard? {
        val rollups = rollups(date, ObservationType.NUTRITION_PROTEIN, SEASON_DAYS)
        if (rollups.isEmpty()) return null
        val average = rollups.map { it.second }.average().roundToInt()
        return StatsCard(
            chart = ChartId.PROTEIN_TREND,
            title = context.getString(UiR.string.stats_card_protein_title),
            description = context.getString(
                UiR.string.stats_desc_protein,
                numberFormat.format(average),
            ),
            plot = StatsPlot.Line(rollups.map { ChartPoint(it.first, it.second) }),
            table = table(
                title = context.getString(UiR.string.stats_card_protein_title),
                columns = listOf(
                    context.getString(UiR.string.chart_table_date),
                    context.getString(UiR.string.stats_table_grams),
                ),
                rows = rollups.map { shortDate(it.first) to numberFormat.format(it.second) },
                chart = ChartId.PROTEIN_TREND,
            ),
            unitText = context.getString(UiR.string.unit_g),
            footer = context.getString(
                UiR.string.stats_days_logged,
                rollups.size,
                SEASON_DAYS,
            ),
        )
    }

    private fun bodyFatCard(date: LocalDate): StatsCard? {
        val rollups = rollups(date, ObservationType.BODY_FAT_PERCENT, BODY_DAYS)
        if (rollups.isEmpty()) return null
        return StatsCard(
            chart = ChartId.BODY_FAT_TREND,
            title = context.getString(UiR.string.stats_card_body_fat_title),
            description = if (rollups.size < 2) {
                context.getString(
                    UiR.string.stats_desc_body_fat_flat,
                    decimal(rollups.single().second, 1),
                )
            } else {
                context.getString(
                    UiR.string.stats_desc_body_fat,
                    decimal(rollups.last().second, 1),
                    decimal(rollups.first().second, 1),
                )
            },
            plot = StatsPlot.Line(rollups.map { ChartPoint(it.first, it.second) }),
            table = table(
                title = context.getString(UiR.string.stats_card_body_fat_title),
                columns = listOf(
                    context.getString(UiR.string.chart_table_date),
                    context.getString(UiR.string.stats_table_fat),
                ),
                rows = rollups.map { shortDate(it.first) to decimal(it.second, 1) },
                chart = ChartId.BODY_FAT_TREND,
            ),
            unitText = context.getString(UiR.string.unit_percent),
            decimals = 1,
        )
    }

    private fun volumeCard(date: LocalDate): StatsCard? {
        val values = weeklyValues(date, ObservationType.TRAINING_VOLUME, WEEKS)
        if (values.all { it.value == 0.0 }) return null
        val bars = values.map {
            ChartBar(it.start.day.toString(), it.value)
        }
        return StatsCard(
            chart = ChartId.TRAINING_VOLUME,
            title = context.getString(UiR.string.stats_card_volume_title),
            description = context.getString(
                UiR.string.stats_desc_volume,
                numberFormat.format(values.last().value.roundToInt()),
            ),
            plot = StatsPlot.Bars(bars),
            table = table(
                title = context.getString(UiR.string.stats_card_volume_title),
                columns = listOf(
                    context.getString(UiR.string.chart_table_week),
                    context.getString(UiR.string.stats_table_volume),
                ),
                rows = values.map {
                    shortDate(it.start) to numberFormat.format(it.value.roundToInt())
                },
                chart = ChartId.TRAINING_VOLUME,
            ),
            unitText = context.getString(UiR.string.unit_kg),
        )
    }

    private fun consistencyCard(date: LocalDate): StatsCard? {
        val values = rollups(date, ObservationType.TRAINING_SETS, CONSISTENCY_DAYS)
        if (values.isEmpty()) return null
        val grid = StatsMath.heatGrid(
            values.map { DailyValue(it.first, it.second) },
            date,
            WEEKS,
        )
        val cells = grid.map { week ->
            week.map { HeatCellUi(it.date, it.level) }
        }
        val top = grid.flatten().maxByOrNull { it.value }?.value ?: 0.0
        return StatsCard(
            chart = ChartId.TRAINING_CONSISTENCY,
            title = context.getString(UiR.string.stats_card_consistency_title),
            description = context.getString(
                UiR.string.stats_desc_consistency,
                numberFormat.format(top.roundToInt()),
            ),
            plot = StatsPlot.Heat(cells),
            table = table(
                title = context.getString(UiR.string.stats_card_consistency_title),
                columns = listOf(
                    context.getString(UiR.string.chart_table_date),
                    context.getString(UiR.string.stats_table_sets),
                ),
                rows = grid.flatten().map {
                    shortDate(it.date) to numberFormat.format(it.value)
                },
                chart = ChartId.TRAINING_CONSISTENCY,
            ),
        )
    }

    private fun muscleCard(date: LocalDate): StatsCard? {
        val from = date.minus(DatePeriod(days = 6))
        val sets = store.training.loadedSets(
            from.atStartOfDayIn(zone).toEpochMilliseconds(),
        )
        val totals = mutableMapOf<String, Double>()
        sets.filterNot { it.isWarmup }.forEach { set ->
            set.primary.forEach { muscle ->
                totals[muscle.key] = (totals[muscle.key] ?: 0.0) + 1.0
            }
        }
        if (totals.isEmpty()) return null
        val ranked = StatsMath.ranked(totals)
        val rows = ranked.map { StatBarRow(muscleLabel(it.name), it.value, decimal(it.value, 0)) }
        return StatsCard(
            chart = ChartId.MUSCLE_SETS,
            title = context.getString(UiR.string.stats_card_muscles_title),
            description = context.getString(
                UiR.string.stats_desc_muscles,
                rows.firstOrNull()?.label ?: DASH,
            ),
            plot = StatsPlot.BarsList(rows),
            table = table(
                title = context.getString(UiR.string.stats_card_muscles_title),
                columns = listOf(
                    context.getString(UiR.string.chart_table_muscle),
                    context.getString(UiR.string.chart_table_sets),
                ),
                rows = ranked.map { muscleLabel(it.name) to decimal(it.value, 0) },
                chart = ChartId.MUSCLE_SETS,
            ),
        )
    }

    private fun sleepCard(date: LocalDate): StatsCard? {
        val nights = sleepNights(date, NIGHTS)
        if (nights.isEmpty()) return null
        val bars = nights.map {
            ChartBar(shortDate(it.localDate), it.totals.asleepMinutes / MINUTES_PER_HOUR)
        }
        return StatsCard(
            chart = ChartId.SLEEP_DURATION,
            title = context.getString(UiR.string.stats_card_sleep_title),
            description = context.getString(
                UiR.string.stats_desc_sleep,
                nights.size,
                duration(nights.map { it.totals.asleepMinutes }.average()),
            ),
            plot = StatsPlot.Bars(bars),
            table = table(
                title = context.getString(UiR.string.stats_card_sleep_title),
                columns = listOf(
                    context.getString(UiR.string.stats_table_night),
                    context.getString(UiR.string.stats_table_hours),
                ),
                rows = nights.map {
                    shortDate(it.localDate) to decimal(
                        it.totals.asleepMinutes / MINUTES_PER_HOUR,
                        1,
                    )
                },
                chart = ChartId.SLEEP_DURATION,
            ),
            unitText = context.getString(UiR.string.stats_unit_hours),
            decimals = 1,
        )
    }

    private fun stagesCard(date: LocalDate): StatsCard? {
        val night = sleepSessions(date).lastOrNull()?.totals ?: return null
        if (!night.hasStages) return null
        val segments = listOfNotNull(
            night.awakeSeconds?.let {
                StatsStackSegment(
                    context.getString(UiR.string.stats_stage_awake),
                    it / 60.0,
                    StatsHue.ORANGE,
                )
            },
            night.remSeconds?.let {
                StatsStackSegment(
                    context.getString(UiR.string.stats_stage_rem),
                    it / 60.0,
                    StatsHue.FROST,
                )
            },
            night.lightSeconds?.let {
                StatsStackSegment(
                    context.getString(UiR.string.stats_stage_light),
                    it / 60.0,
                    StatsHue.BLUE,
                )
            },
            night.deepSeconds?.let {
                StatsStackSegment(
                    context.getString(UiR.string.stats_stage_deep),
                    it / 60.0,
                    StatsHue.PURPLE,
                )
            },
        )
        if (segments.isEmpty()) return null
        return StatsCard(
            chart = ChartId.SLEEP_STAGES,
            title = context.getString(UiR.string.stats_card_sleep_stages_title),
            description = context.getString(
                UiR.string.stats_desc_stages,
                duration(night.asleepMinutes),
            ),
            plot = StatsPlot.Stack(segments),
            table = table(
                title = context.getString(UiR.string.stats_card_sleep_stages_title),
                columns = listOf(
                    context.getString(UiR.string.stats_table_stage),
                    context.getString(UiR.string.stats_table_minutes),
                ),
                rows = segments.map { it.name to decimal(it.value, 0) },
                chart = ChartId.SLEEP_STAGES,
            ),
            unitText = context.getString(UiR.string.stats_unit_minutes),
        )
    }

    private fun macroCard(date: LocalDate): StatsCard? {
        if (loggedDays(date, ObservationType.NUTRITION_ENERGY, SEASON_DAYS) == 0) return null
        val target = store.nutrition.targetFor(date)?.targets
        val rows = listOf(
            NutrientRow(
                context.getString(UiR.string.nutrient_protein),
                ObservationType.NUTRITION_PROTEIN,
                target?.proteinG,
                StatsHue.GREEN,
            ),
            NutrientRow(
                context.getString(UiR.string.nutrient_carbs),
                ObservationType.NUTRITION_CARBS,
                target?.carbsG,
                StatsHue.BLUE,
            ),
            NutrientRow(
                context.getString(UiR.string.nutrient_fat),
                ObservationType.NUTRITION_FAT,
                target?.fatG,
                StatsHue.PURPLE,
            ),
            NutrientRow(
                context.getString(UiR.string.nutrient_fibre),
                ObservationType.NUTRITION_FIBRE,
                target?.fibreG,
                StatsHue.TEAL,
            ),
        ).map { row ->
            MacroAverage(
                name = row.label,
                grams = average(date, row.type, MACRO_DAYS) ?: 0.0,
                targetGrams = row.target,
                fraction = row.target?.takeIf { it > 0.0 }?.let { target ->
                    ((average(date, row.type, MACRO_DAYS) ?: 0.0) / target)
                        .coerceIn(0.0, 1.0).toFloat()
                },
                hue = row.hue,
            )
        }
        if (rows.all { it.grams == 0.0 }) return null
        return StatsCard(
            chart = ChartId.MACRO_BARS,
            title = context.getString(UiR.string.stats_card_macros_title),
            description = context.getString(UiR.string.stats_desc_macros),
            plot = StatsPlot.Macros(rows),
            table = table(
                title = context.getString(UiR.string.stats_card_macros_title),
                columns = listOf(
                    context.getString(UiR.string.stats_table_macro),
                    context.getString(UiR.string.stats_table_grams),
                ),
                rows = rows.map { it.name to decimal(it.grams, 0) },
                chart = ChartId.MACRO_BARS,
            ),
            unitText = context.getString(UiR.string.unit_g),
            footer = context.getString(
                UiR.string.stats_days_logged,
                loggedDays(date, ObservationType.NUTRITION_ENERGY, MACRO_DAYS),
                MACRO_DAYS,
            ),
        )
    }

    // --------------------------------------------------------------------- records

    private fun records(): StatsRecords? {
        val from = today().minus(DatePeriod(days = RECORD_DAYS))
        val sets = store.training.loadedSets(
            from.atStartOfDayIn(zone).toEpochMilliseconds(),
        ).filterNot { it.isWarmup || it.weightKg <= 0.0 || it.reps <= 0 }
        val best = sets.groupBy { it.exerciseId }.mapNotNull { (exerciseId, rows) ->
            val bestOneRep = PersonalRecords.bestOneRepMax(
                rows.map { TrainingSetRecord(it.weightKg, it.reps) },
            ) ?: return@mapNotNull null
            val bestWeight = rows.maxOf { it.weightKg }
            val name = store.training.exerciseById(exerciseId)?.name ?: return@mapNotNull null
            Triple(name, bestWeight, bestOneRep)
        }.sortedByDescending { it.third }.take(RECORD_ROWS)
        if (best.isEmpty()) {
            return StatsRecords(
                context.getString(UiR.string.stats_card_prs_title),
                emptyList(),
            )
        }
        return StatsRecords(
            context.getString(UiR.string.stats_card_prs_title),
            best.map { (name, weight, oneRep) ->
                context.getString(
                    UiR.string.stats_record_row,
                    name,
                    decimal(weight, 1),
                    decimal(oneRep, 1),
                )
            },
        )
    }

    // --------------------------------------------------------------------- helpers

    private data class NutrientRow(
        val label: String,
        val type: ObservationType,
        val target: Double?,
        val hue: StatsHue,
    )

    private fun today(): LocalDate = clock.now().toLocalDateTime(zone).date

    private fun rollups(
        date: LocalDate,
        type: ObservationType,
        days: Int,
    ): List<Pair<LocalDate, Double>> = store.rollups.forTypeRange(
        type,
        date.minus(DatePeriod(days = days - 1)),
        date,
    ).map { it.localDate to it.value.value }

    private fun latest(type: ObservationType, date: LocalDate): Double? =
        rollups(date, type, SEASON_DAYS).lastOrNull()?.second

    private fun average(date: LocalDate, type: ObservationType, days: Int): Double? {
        val values = rollups(date, type, days)
        return if (values.isEmpty()) null else values.map { it.second }.average()
    }

    private fun loggedDays(date: LocalDate, type: ObservationType, days: Int): Int =
        rollups(date, type, days).size

    private fun weeklyTotal(date: LocalDate, type: ObservationType): Double {
        val weekStart = StatsMath.startOfWeek(date)
        return rollups(date, type, 7).filter { it.first >= weekStart }.sumOf { it.second }
    }

    private fun weeklyValues(
        date: LocalDate,
        type: ObservationType,
        weeks: Int,
    ): List<app.openhealth.core.stats.WeeklyBucket> = StatsMath.weekly(
        rollups(date, type, weeks * 7).map { DailyValue(it.first, it.second) },
        date,
        weeks,
    )

    private fun sessionsThisWeek(date: LocalDate): Int {
        val start = StatsMath.startOfWeek(date)
        return store.training.sessionsBetween(start, start.plus(DatePeriod(days = 7))).size
    }

    private fun weightChange(date: LocalDate, days: Int): Double? {
        val values = rollups(date, ObservationType.WEIGHT, days)
        if (values.isEmpty()) return null
        val outcome = WeightTrend.ewma(values.map { WeightEntry(it.first, it.second) })
        val trend = (outcome as? FormulaOutcome.Computed)?.value ?: return null
        return trend.points.lastOrNull()?.trendKg?.minus(trend.points.first().trendKg)
    }

    private fun weeklyRate(date: LocalDate): Double? {
        val values = rollups(date, ObservationType.WEIGHT, BODY_DAYS)
        if (values.isEmpty()) return null
        val trend = (
            WeightTrend.ewma(values.map { WeightEntry(it.first, it.second) })
                as? FormulaOutcome.Computed
            )?.value ?: return null
        return trend.weeklyRateKg
    }

    private fun stepsAverage(date: LocalDate, days: Int): Double? =
        average(date, ObservationType.STEPS, days)

    private fun sleepNights(date: LocalDate, count: Int): List<SleepSession> {
        val earliest = date.minus(DatePeriod(days = count - 1))
        return sleepSessions(date).filter { it.localDate >= earliest && it.localDate <= date }
            .takeLast(count)
    }

    private fun sleepSessions(date: LocalDate): List<SleepSession> {
        val from = date.minus(DatePeriod(days = NIGHTS + 3)).atStartOfDayIn(zone)
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

    private fun sleepDebt(date: LocalDate): app.openhealth.core.formula.SleepDebtResult? {
        val need = store.settings.get(SettingKeys.SLEEP_NEED_MINUTES)?.toDoubleOrNull()
            ?.takeIf { it >= 60.0 } ?: DEFAULT_SLEEP_NEED_MINUTES
        val nights = sleepNights(date, NIGHTS).map {
            SleepNight(it.localDate, it.totals.asleepMinutes)
        }
        return (SleepDebt.compute(nights, need) as? FormulaOutcome.Computed)?.value
    }

    private fun table(
        title: String,
        columns: List<String>,
        rows: List<Pair<String, String>>,
        chart: ChartId,
    ): ChartTableModel = ChartTableModel(
        title = title,
        columns = columns,
        rows = rows.map { listOf(it.first, it.second) },
        citation = context.getString(
            UiR.string.chart_citation,
            app.openhealth.core.citation.Citations.of(ChartRegistry.of(chart).citation).title,
        ),
    )

    private fun muscleLabel(key: String): String {
        val muscle = Muscle.fromKey(key) ?: return key
        return context.getString(muscleRes(muscle))
    }

    private fun muscleRes(muscle: Muscle): Int = when (muscle) {
        Muscle.CHEST -> UiR.string.muscle_chest
        Muscle.UPPER_BACK -> UiR.string.muscle_upper_back
        Muscle.LOWER_BACK -> UiR.string.muscle_lower_back
        Muscle.TRAPEZIUS -> UiR.string.muscle_trapezius
        Muscle.DELTOIDS -> UiR.string.muscle_deltoids
        Muscle.BICEPS -> UiR.string.muscle_biceps
        Muscle.TRICEPS -> UiR.string.muscle_triceps
        Muscle.FOREARMS -> UiR.string.muscle_forearms
        Muscle.ABS -> UiR.string.muscle_abs
        Muscle.OBLIQUES -> UiR.string.muscle_obliques
        Muscle.SERRATUS -> UiR.string.muscle_serratus
        Muscle.HIP_FLEXORS -> UiR.string.muscle_hip_flexors
        Muscle.GLUTES -> UiR.string.muscle_glutes
        Muscle.ADDUCTORS -> UiR.string.muscle_adductors
        Muscle.QUADS -> UiR.string.muscle_quads
        Muscle.HAMSTRINGS -> UiR.string.muscle_hamstrings
        Muscle.CALVES -> UiR.string.muscle_calves
        Muscle.TIBIALIS -> UiR.string.muscle_tibialis
        Muscle.NECK -> UiR.string.muscle_neck
    }

    private fun decimal(value: Double, decimals: Int = 0): String =
        numberFormat.apply { maximumFractionDigits = decimals }.format(value)

    private fun signed(value: Double, decimals: Int): String {
        val rounded = if (decimals == 0) value.roundToInt().toDouble() else value
        val sign = when {
            rounded > 0.0 -> "+"
            else -> ""
        }
        return "$sign${decimal(rounded, decimals)}"
    }

    private fun duration(minutes: Double): String {
        val total = minutes.roundToInt()
        val hours = total / 60
        val remainder = total % 60
        return if (hours > 0) {
            context.getString(UiR.string.today_hours_minutes, hours, remainder)
        } else {
            context.getString(UiR.string.today_minutes, remainder)
        }
    }

    private fun shortDate(date: LocalDate): String =
        java.time.LocalDate.of(date.year, date.month.ordinal + 1, date.day)
            .format(java.time.format.DateTimeFormatter.ofPattern("d MMM"))
}

private const val SEASON_DAYS = 30
private const val MACRO_DAYS = 7
private const val BODY_DAYS = 90
private const val WEEKS = 12
private const val NIGHTS = 14
private const val CONSISTENCY_DAYS = 84
private const val RECORD_ROWS = 5
private const val RECORD_DAYS = 90
private const val MINUTES_PER_HOUR = 60.0
private const val DEFAULT_SLEEP_NEED_MINUTES = 480.0
private val SLEEP_TYPES = listOf(
    ObservationType.SLEEP_DURATION,
    ObservationType.SLEEP_IN_BED,
    ObservationType.SLEEP_DEEP,
    ObservationType.SLEEP_LIGHT,
    ObservationType.SLEEP_REM,
    ObservationType.SLEEP_AWAKE,
)
private const val DASH = "—"
