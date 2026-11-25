package app.openhealth.ui.screens.stats

import app.openhealth.ui.components.ChartTableModel
import app.openhealth.ui.screens.today.TodayChartModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `pr.md` §6.16 and `DESIGN.md` §8.6: every chart has a table alternative, a one-sentence
 * description and a "how this is calculated" sheet with a citation. A chart that skips
 * one of the three fails here, before it can reach a screen.
 */
class ChartRegistryTest {
    @Test
    fun everyChartIdHasAnEntry() {
        assertEquals(ChartId.entries.size, ChartRegistry.all.size)
        assertEquals(
            ChartId.entries.toSet(),
            ChartRegistry.all.map { it.id }.toSet(),
        )
    }

    @Test
    fun everyEntryHasTitleFormulaAndCitation() {
        ChartRegistry.all.forEach { meta ->
            assertTrue(meta.titleRes != 0, "${meta.id} has no title")
            assertTrue(meta.formulaTitleRes != 0, "${meta.id} has no formula title")
            assertTrue(meta.formulaBodyRes != 0, "${meta.id} has no formula body")
            assertNotNull(meta.citation, "${meta.id} has no citation")
        }
    }

    @Test
    fun ofFindsEveryId() {
        ChartId.entries.forEach { id ->
            assertEquals(id, ChartRegistry.of(id).id)
        }
    }

    @Test
    fun aTableWithNoRowsIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            ChartTableModel(title = "Weight", columns = listOf("Day", "kg"), rows = emptyList())
        }
    }

    @Test
    fun aTableRowMustMatchItsColumns() {
        assertFailsWith<IllegalArgumentException> {
            ChartTableModel(
                title = "Weight",
                columns = listOf("Day", "kg"),
                rows = listOf(listOf("2026-11-25")),
            )
        }
    }

    @Test
    fun aChartWithoutASentenceIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            StatsCard(
                chart = ChartId.WEIGHT_TREND,
                title = "Weight",
                description = " ",
                plot = StatsPlot.Line(emptyList()),
                table = table(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            TodayChartModel(
                chart = ChartId.STEPS_RING,
                description = "",
                table = table(),
            )
        }
    }

    @Test
    fun aKpiOpensItsOwnTableOrNone() {
        val weight = StatsCard(
            chart = ChartId.WEIGHT_TREND,
            title = "Weight",
            description = "Down 1.2 kg over 30 days.",
            plot = StatsPlot.Line(emptyList()),
            table = table(),
        )
        val cards = listOf(weight)
        // A figure that names no chart opens nothing, rather than the first card's table.
        assertNull(tableForKpi(StatsKpi(value = "4", label = "Sessions"), cards))
        // A figure whose chart has no card on this segment also opens nothing.
        assertNull(
            tableForKpi(
                StatsKpi(value = "7 h", label = "Sleep", chart = ChartId.SLEEP_DURATION),
                cards,
            ),
        )
        assertEquals(
            weight.table,
            tableForKpi(
                StatsKpi(value = "82.4", label = "Weight", chart = ChartId.WEIGHT_TREND),
                cards,
            ),
        )
    }

    @Test
    fun everySegmentIsNamed() {
        assertEquals(5, StatsSegment.entries.size)
    }

    private fun table() = ChartTableModel(
        title = "Weight",
        columns = listOf("Day", "kg"),
        rows = listOf(listOf("2026-11-25", "82.4")),
    )
}
