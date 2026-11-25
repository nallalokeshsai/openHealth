package app.openhealth.ui.screens.stats

import app.openhealth.ui.components.ChartBar
import app.openhealth.ui.components.ChartPoint
import app.openhealth.ui.components.ChartTableModel
import app.openhealth.ui.components.HeatCellUi
import app.openhealth.ui.components.StatBarRow
import kotlinx.datetime.LocalDate

/** The five segments of `DESIGN.md` §14.2 Stats. */
enum class StatsSegment { OVERVIEW, BODY, TRAINING, SLEEP, NUTRITION }

/** One figure of the Stats KPI band. */
data class StatsKpi(val value: String, val label: String, val unit: String? = null)

/** A named segment of a stacked bar; the hue always arrives with its name. */
data class StatsStackSegment(val name: String, val value: Double, val hue: StatsHue)

/** The four domain hues of `DESIGN.md` §8.4, named so a caller cannot pass a raw colour. */
enum class StatsHue { ORANGE, FROST, BLUE, PURPLE, GREEN, TEAL }

/** The drawing a card asks for. */
sealed interface StatsPlot {
    data class Line(val points: List<ChartPoint>) : StatsPlot

    data class Bars(val bars: List<ChartBar>) : StatsPlot

    data class Heat(val weeks: List<List<HeatCellUi>>) : StatsPlot

    data class BarsList(val rows: List<StatBarRow>) : StatsPlot

    data class Stack(val segments: List<StatsStackSegment>) : StatsPlot

    /** The macro bars, with a name and a value for every row. */
    data class Macros(val rows: List<MacroAverage>) : StatsPlot
}

/** One macro's average grams a day and its target, for the Nutrition segment. */
data class MacroAverage(
    val name: String,
    val grams: Double,
    val targetGrams: Double?,
    val fraction: Float?,
    val hue: StatsHue,
)

/**
 * One Stats card: a chart with its table alternative, its one-sentence description and
 * the registry entry that explains its arithmetic (`pr.md` §6.16, `DESIGN.md` §8.6).
 */
data class StatsCard(
    val chart: ChartId,
    val title: String,
    val description: String,
    val plot: StatsPlot,
    val table: ChartTableModel,
    val footer: String? = null,
    val goal: Double? = null,
    val unitText: String = "",
    val decimals: Int = 0,
) {
    init {
        require(description.isNotBlank()) {
            "a chart without its sentence is not drawn (DESIGN.md §8.6)"
        }
    }
}

/** A plain row under the charts, for records that are lists rather than trends. */
data class StatsRecords(val title: String, val rows: List<String>)

data class StatsModel(
    val today: LocalDate,
    val segment: StatsSegment,
    val kpis: List<StatsKpi>,
    val cards: List<StatsCard>,
    val records: StatsRecords? = null,
    val demo: Boolean = false,
    val weightNeutral: Boolean = false,
    val modules: Set<app.openhealth.core.preferences.HealthModule> = emptySet(),
)
