package app.openhealth.core.stats

import app.openhealth.core.citation.CitationKey
import app.openhealth.core.formula.DailyValue
import app.openhealth.core.formula.EnergyBalance
import app.openhealth.core.formula.EnergyBalanceCalculator
import app.openhealth.core.formula.EnergyRange
import app.openhealth.core.formula.FormulaOutcome
import app.openhealth.core.formula.Readiness
import app.openhealth.core.formula.ReadinessInputs
import app.openhealth.core.formula.ReadinessScore
import app.openhealth.core.formula.ReadinessSeries
import app.openhealth.core.formula.StepEnergy
import app.openhealth.core.formula.WorkoutEnergy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

private fun <T> FormulaOutcome<T>.value(): T = (this as FormulaOutcome.Computed).value

class StepEnergyTest {
    @Test
    fun aWalkAtSeventyKilosIsAboutFourHundredKcalPerTenThousandSteps() {
        val range = StepEnergy.compute(10_000.0, 70.0).value()
        // 0.762 m stride at 4 km/h, 3 MET: about 420 kcal per 10 000 steps at 70 kg.
        assertTrue(range.central in 380.0..460.0, "got ${range.central}")
        assertEquals(range.central * 0.75, range.low, 1e-9)
        assertEquals(range.central * 1.25, range.high, 1e-9)
    }

    @Test
    fun noStepsIsZeroNotUnavailable() {
        assertEquals(0.0, StepEnergy.compute(0.0, 70.0).value().central, 1e-9)
    }

    @Test
    fun implausibleInputsSayWhy() {
        val negative = StepEnergy.compute(-1.0, 70.0)
        assertIs<FormulaOutcome.NotAvailable>(negative)
        val light = StepEnergy.compute(1_000.0, 10.0)
        assertIs<FormulaOutcome.NotAvailable>(light)
    }
}

class WorkoutEnergyTest {
    @Test
    fun aMetWorkoutCarriesItsOwnRange() {
        val range = WorkoutEnergy.compute(6.0, 80.0, 30.0).value()
        assertEquals(range.central * 0.8, range.low, 1e-9)
        assertEquals(range.central * 1.2, range.high, 1e-9)
    }

    @Test
    fun importedKilocaloriesUseTheSameUncertainty() {
        val range = WorkoutEnergy.fromKcal(300.0).value()
        assertEquals(240.0, range.low, 1e-9)
        assertEquals(360.0, range.high, 1e-9)
    }
}

class EnergyBalanceTest {
    private fun balance(
        intake: Double = 2_000.0,
        bmr: Double = 1_700.0,
        steps: Int = 8_000,
    ): EnergyBalance {
        val stepRange = StepEnergy.compute(steps.toDouble(), 70.0).value()
        return EnergyBalanceCalculator.compute(intake, bmr, stepRange).value()
    }

    @Test
    fun netIsInMinusOutAndTheRangeIsReversed() {
        val result = balance()
        val out = result.outKcal
        assertEquals(result.intakeKcal - out.central, result.netKcal.central, 1e-9)
        assertEquals(result.intakeKcal - out.high, result.netKcal.low, 1e-9)
        assertEquals(result.intakeKcal - out.low, result.netKcal.high, 1e-9)
        assertTrue(result.outKcal.low < result.outKcal.central)
        assertTrue(result.outKcal.high > result.outKcal.central)
    }

    @Test
    fun aWorkoutAddsToOut() {
        val without = balance(workout = null)
        val with = balance(workout = WorkoutEnergy.fromKcal(400.0).value())
        assertTrue(with.outKcal.central > without.outKcal.central)
        assertEquals(400.0, with.outKcal.central - without.outKcal.central, 1e-9)
    }

    @Test
    fun theRangeSaysWhetherItCrossesZero() {
        val deficit = balance(intake = 1_500.0)
        assertTrue(EnergyBalanceCalculator.isDeficit(deficit.netKcal))
        val surplus = balance(intake = 4_000.0)
        assertTrue(EnergyBalanceCalculator.isSurplus(surplus.netKcal))
        val uncertain = EnergyRange(-10.0, 0.0, 10.0)
        assertTrue(EnergyBalanceCalculator.crossesZero(uncertain))
    }

    @Test
    fun targetLeavesARemainingFigure() {
        val result = EnergyBalanceCalculator
            .compute(1_200.0, 1_700.0, targetKcal = 2_000.0).value()
        assertEquals(800.0, result.remainingKcal!!, 1e-9)
    }

    @Test
    fun theWeeklyRateUsesTheSevenThousandSevenHundredRule() {
        assertEquals(-0.5, EnergyBalanceCalculator.weeklyRateKg(-550.0), 1e-9)
        assertFalse(EnergyBalanceCalculator.isFlat(-550.0))
        assertTrue(EnergyBalanceCalculator.isFlat(-10.0))
    }

    @Test
    fun theBalanceCitesTheMetCompendium() {
        val result = EnergyBalanceCalculator.compute(2_000.0, 1_700.0)
            as FormulaOutcome.Computed
        assertEquals(CitationKey.COMPENDIUM_OF_PHYSICAL_ACTIVITIES_2024, result.citation)
        assertEquals(listOf("intake", "bmr", "steps", "workouts"), result.inputs.map { it.key })
    }

    private fun balance(workout: EnergyRange?): EnergyBalance {
        val stepRange = StepEnergy.compute(8_000.0, 70.0).value()
        return EnergyBalanceCalculator.compute(2_000.0, 1_700.0, stepRange, workout).value()
    }
}

class ReadinessSeriesTest {
    private val today = LocalDate(2026, 11, 25)

    private fun days(count: Int, value: Double = 60.0): List<DailyValue> =
        (0 until count).map { index ->
            DailyValue(today.minusDays(index + 1), value + index % 2)
        }

    @Test
    fun sixDaysIsNotABaseline() {
        assertNull(ReadinessSeries.baseline(days(6), today))
    }

    @Test
    fun sevenDaysIsABaselineAndTodayIsExcluded() {
        val series = days(7) + DailyValue(today, 200.0)
        val baseline = ReadinessSeries.baseline(series, today)
        assertNotNull(baseline)
        assertEquals(7, baseline.days)
        assertTrue(baseline.mean < 100.0, "today's outlier must not be in the baseline")
    }

    @Test
    fun oldDaysOutsideTheWindowAreIgnored() {
        val old = DailyValue(today.minusDays(40), 60.0)
        val baseline = ReadinessSeries.baseline(days(7) + old, today)
        assertNotNull(baseline)
        assertEquals(7, baseline.days)
    }

    @Test
    fun twoInputsProduceAScoreAndOneDoesNot() {
        val enough = ReadinessSeries.inputs(
            today = today,
            sleepMinutes = 7.5 * 60,
            restingHeartRate = 55.0,
            restingHeartRateSeries = days(10, 56.0),
        )
        assertIs<FormulaOutcome.Computed<ReadinessScore>>(Readiness.compute(enough))

        val one = ReadinessSeries.inputs(today = today, sleepMinutes = 7.5 * 60)
        assertIs<FormulaOutcome.NotAvailable>(Readiness.compute(one))
    }

    @Test
    fun theLoadAverageIgnoresTodayAndFillsTheGap() {
        val series = (0 until 5).map { DailyValue(today.minusDays(it + 1), 100.0) }
        assertEquals(100.0, ReadinessSeries.loadAverage(series, today)!!, 1e-9)
        assertNull(ReadinessSeries.loadAverage(emptyList(), today))
    }

    @Test
    fun latestTakesTheMostRecentDayOnOrBeforeToday() {
        val series = listOf(
            DailyValue(today.minusDays(2), 50.0),
            DailyValue(today, 54.0),
            DailyValue(today.plusDays(1), 90.0),
        )
        assertEquals(54.0, ReadinessSeries.latest(series, today)!!, 1e-9)
    }

    @Test
    fun theDefaultSleepNeedIsEightHours() {
        assertEquals(480.0, ReadinessInputs.DEFAULT_SLEEP_NEED_MINUTES, 1e-9)
    }
}

class StatsMathTest {
    private val today = LocalDate(2026, 11, 25)

    @Test
    fun theWindowEndsTodayAndCountsInclusively() {
        val range = StatsMath.window(today, 7)
        assertEquals(LocalDate(2026, 11, 19), range.start)
        assertEquals(today, range.endInclusive)
    }

    @Test
    fun aMeanSkipsMissingDaysRatherThanFillingZero() {
        val values = listOf(
            DailyValue(today, 100.0),
            DailyValue(today.minusDays(2), 50.0),
        )
        val mean = StatsMath.mean(values, StatsMath.window(today, 7))
        assertEquals(75.0, mean!!, 1e-9)
        assertEquals(2, StatsMath.daysWithData(values, StatsMath.window(today, 7)))
    }

    @Test
    fun theLastValueWrittenForADayWins() {
        val values = listOf(DailyValue(today, 10.0), DailyValue(today, 12.0))
        assertEquals(12.0, StatsMath.byDay(values).single().value, 1e-9)
    }

    @Test
    fun theRampHasFiveStepsAndZeroIsTheEmptyStep() {
        assertEquals(RampLevel.L0, StatsMath.level(0.0, 100.0))
        assertEquals(RampLevel.L1, StatsMath.level(10.0, 100.0))
        assertEquals(RampLevel.L2, StatsMath.level(50.0, 100.0))
        assertEquals(RampLevel.L3, StatsMath.level(75.0, 100.0))
        assertEquals(RampLevel.L4, StatsMath.level(100.0, 100.0))
    }

    @Test
    fun weeklyBucketsStartOnMondayAndKeepTheirDays() {
        val values = (0 until 28).map { DailyValue(today.minusDays(it), 10.0) }
        val weeks = StatsMath.weekly(values, today, 2)
        assertEquals(2, weeks.size)
        assertEquals(0, weeks.first().start.dayOfWeek.ordinal)
        assertEquals(7, weeks.first().daysWithData)
        assertEquals(70.0, weeks.first().value, 1e-9)
    }

    @Test
    fun theHeatGridAlwaysHasWholeWeeksEndingInTodaysWeek() {
        val grid = StatsMath.heatGrid(listOf(DailyValue(today, 5.0)), today, 12)
        assertEquals(12, grid.size)
        assertTrue(grid.all { it.size == 7 })
        assertEquals(today, grid.last().first { it.date == today }.date)
        assertEquals(RampLevel.L4, grid.last().first { it.date == today }.level)
        assertEquals(RampLevel.L0, grid.last().first { it.date != today }.level)
    }

    @Test
    fun aChangeNamesItsTwoEnds() {
        val values = listOf(
            DailyValue(today.minusDays(10), 80.0),
            DailyValue(today, 78.0),
        )
        val change = StatsMath.change(values, StatsMath.window(today, 30))!!
        assertEquals(-2.0, change.change, 1e-9)
        assertEquals(11, change.days)
    }

    @Test
    fun rankedTotalsAreDescendingAndDropEmptyMuscles() {
        val ranked = StatsMath.ranked(mapOf("chest" to 12.0, "back" to 18.0, "arms" to 0.0))
        assertEquals(listOf("back", "chest"), ranked.map { it.name })
    }
}

private fun LocalDate.minusDays(days: Int): LocalDate =
    this.minus(kotlinx.datetime.DatePeriod(days = days))

private fun LocalDate.plusDays(days: Int): LocalDate =
    this.plus(kotlinx.datetime.DatePeriod(days = days))
