package app.openhealth.core.formula

import app.openhealth.core.citation.CitationKey
import app.openhealth.core.citation.Citations
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

private fun <T> FormulaOutcome<T>.value(): T = (this as FormulaOutcome.Computed).value

class BmrTest {
    @Test
    fun maleAndFemaleMifflinStJeor() {
        assertEquals(1780.0, Bmr.mifflinStJeor(SexAtBirth.MALE, 80.0, 180.0, 30).value(), 0.001)
        assertEquals(1614.0, Bmr.mifflinStJeor(SexAtBirth.FEMALE, 80.0, 180.0, 30).value(), 0.001)
    }

    @Test
    fun inputsAndCitationComeBackWithTheValue() {
        val result = Bmr.mifflinStJeor(SexAtBirth.MALE, 80.0, 180.0, 30) as FormulaOutcome.Computed
        assertEquals(CitationKey.MIFFLIN_ST_JEOR_1990, result.citation)
        assertEquals(listOf("weight", "height", "age"), result.inputs.map { it.key })
    }

    @Test
    fun underSixteenIsNotSupported() {
        val result = Bmr.mifflinStJeor(SexAtBirth.MALE, 60.0, 170.0, 15)
        assertIs<FormulaOutcome.NotAvailable>(result)
        assertEquals(NotAvailableReason.NOT_SUPPORTED, result.reason)
    }

    @Test
    fun implausibleHeightIsOutOfRange() {
        val result = Bmr.mifflinStJeor(SexAtBirth.MALE, 80.0, 10.0, 30)
        assertIs<FormulaOutcome.NotAvailable>(result)
        assertEquals(NotAvailableReason.OUT_OF_RANGE, result.reason)
    }
}

class BodyCompositionTest {
    @Test
    fun bmiAndItsBands() {
        assertEquals(24.691358, Bmi.compute(80.0, 180.0).value(), 0.0001)
        assertEquals(BmiCategory.IN_RANGE, Bmi.category(24.9))
        assertEquals(BmiCategory.BELOW_RANGE, Bmi.category(18.4))
        assertEquals(BmiCategory.ABOVE_RANGE, Bmi.category(25.0))
        assertEquals(BmiCategory.HIGH, Bmi.category(31.0))
        assertEquals(BmiCategory.VERY_HIGH, Bmi.category(36.0))
        assertEquals(BmiCategory.EXTREMELY_HIGH, Bmi.category(41.0))
    }

    @Test
    fun navyMaleBodyFat() {
        val result = NavyBodyFat.compute(
            sex = SexAtBirth.MALE,
            heightCm = 180.0,
            neckCm = 40.0,
            waistCm = 90.0,
        ).value()
        assertEquals(18.462, result, 0.01)
    }

    @Test
    fun navyFemaleNeedsAHipMeasurement() {
        val missing = NavyBodyFat.compute(SexAtBirth.FEMALE, 170.0, 32.0, 80.0)
        assertIs<FormulaOutcome.NotAvailable>(missing)
        assertEquals(NotAvailableReason.MISSING_INPUT, missing.reason)
        val result = NavyBodyFat.compute(SexAtBirth.FEMALE, 170.0, 32.0, 80.0, 100.0).value()
        assertTrue(result in 15.0..50.0, "got $result")
    }

    @Test
    fun ffmiNormalisesHeight() {
        val result = Ffmi.fromBodyFat(80.0, 180.0, 12.0).value()
        assertEquals(70.4, result.fatFreeMassKg, 0.001)
        assertEquals(21.728, result.ffmi, 0.001)
        assertEquals(21.728, result.normalisedFfmi, 0.001)
    }
}

class EnergyTest {
    @Test
    fun tdeeMultipliesByTheActivityLevel() {
        val result = Tdee.fromBmr(1780.0, ActivityLevel.MODERATELY_ACTIVE).value()
        assertEquals(2759.0, result, 0.001)
    }

    @Test
    fun metEnergyUsesThePublishedEquation() {
        val result = MetEnergy.compute(6.0, 80.0, 60.0).value()
        assertEquals(504.0, result.kilocalories, 0.001)
        val range = MetEnergy.range(result)
        assertEquals(403.2, range.start, 0.001)
        assertEquals(604.8, range.endInclusive, 0.001)
    }

    @Test
    fun metTableIsCitedAndPlausible() {
        MetActivity.entries.forEach { activity ->
            assertTrue(activity.met in 1.0..20.0, "${activity.name} has MET ${activity.met}")
            assertEquals(activity.labelKey, activity.labelKey.lowercase())
        }
        assertEquals(42, MetActivity.entries.size)
        assertEquals(
            CitationKey.COMPENDIUM_OF_PHYSICAL_ACTIVITIES_2024,
            Citations.of(CitationKey.COMPENDIUM_OF_PHYSICAL_ACTIVITIES_2024).key,
        )
    }

    @Test
    fun goalRateIsClampedNotRefused() {
        val lose = GoalRatePolicy.clamp(GoalMode.LOSE, -2.0, 80.0).value()
        assertTrue(lose.clamped)
        assertEquals(-0.8, lose.appliedKgPerWeek, 0.0001)
        assertEquals(-0.8, lose.allowedRangeKgPerWeek.start, 0.0001)
        assertEquals(0.5, lose.citedLossRangeKgPerWeek.start, 0.0)
        assertEquals(1.0, lose.citedLossRangeKgPerWeek.endInclusive, 0.0)
        val maintain = GoalRatePolicy.clamp(GoalMode.MAINTAIN, -1.0, 80.0).value()
        assertTrue(maintain.clamped)
        assertEquals(0.0, maintain.appliedKgPerWeek)
    }

    @Test
    fun safetyFloorNeverGoesBelowBmrOrTheAbsoluteFloor() {
        assertEquals(1780.0, EnergySafety.floors(SexAtBirth.MALE, 1780.0).value().minimumIntakeKcal)
        assertEquals(1500.0, EnergySafety.floors(SexAtBirth.MALE, 1200.0).value().minimumIntakeKcal)
        assertEquals(
            1200.0,
            EnergySafety.floors(SexAtBirth.FEMALE, 1100.0).value().minimumIntakeKcal,
        )
    }
}

class StrengthTest {
    @Test
    fun epleyAndBrzycki() {
        assertEquals(116.6667, OneRepMax.estimate(100.0, 5).value(), 0.001)
        assertEquals(
            112.5,
            OneRepMax.estimate(100.0, 5, OneRepMaxEstimator.BRZYCKI).value(),
            0.001,
        )
        assertEquals(100.0, OneRepMax.estimate(100.0, 1).value(), 0.0)
    }

    @Test
    fun highRepsAreNotExtrapolated() {
        val result = OneRepMax.estimate(100.0, 30)
        assertIs<FormulaOutcome.NotAvailable>(result)
        assertEquals(NotAvailableReason.OUT_OF_RANGE, result.reason)
    }

    @Test
    fun volumeExcludesWarmupsAndBodyweightFromLoad() {
        val result = SessionVolumeCalculator.compute(
            listOf(
                VolumeSet(40.0, 10, isWarmup = true),
                VolumeSet(100.0, 5),
                VolumeSet(100.0, 5),
                VolumeSet(0.0, 12, isBodyweight = true),
                VolumeSet(120.0, 3, completed = false),
            ),
        ).value()
        assertEquals(1000.0, result.externalLoadKg, 0.001)
        assertEquals(3, result.workSets)
        assertEquals(1, result.warmupSets)
        assertEquals(32, result.totalReps)
        assertEquals(100.0, result.heaviestKg)
    }

    @Test
    fun emptySessionHasNoVolume() {
        assertIs<FormulaOutcome.NotAvailable>(SessionVolumeCalculator.compute(emptyList()))
    }
}

class TrendTest {
    private fun day(index: Int) = LocalDate(2026, 1, 1).plusDays(index)

    private fun LocalDate.plusDays(days: Int): LocalDate = plus(days, DateTimeUnit.DAY)

    @Test
    fun ewmaSmoothsAFlatSeries() {
        val series = WeightTrend.ewma((0 until 21).map { WeightEntry(day(it), 80.0) }).value()
        assertEquals(21, series.points.size)
        assertEquals(80.0, series.points.last().trendKg, 1e-9)
        assertEquals(0.0, series.weeklyRateKg!!, 1e-9)
        assertEquals(21, series.spanDays)
    }

    @Test
    fun ewmaLagsADrop() {
        val entries = (0 until 14).map { WeightEntry(day(it), 80.0) } +
            WeightEntry(day(14), 78.0)
        val series = WeightTrend.ewma(entries, halfLifeDays = 7.0).value()
        val last = series.points.last().trendKg
        assertTrue(last > 79.5 && last < 80.0, "trend lagged to $last")
    }

    @Test
    fun etaNeedsFourteenDays() {
        val short = WeightTrend.ewma((0 until 5).map { WeightEntry(day(it), 90.0) }).value()
        val result = GoalEta.fromTrend(85.0, short, day(4))
        assertIs<FormulaOutcome.NotAvailable>(result)
        assertEquals(NotAvailableReason.NOT_ENOUGH_DATA, result.reason)
    }

    @Test
    fun etaIsAWindowAroundTheCentralDay() {
        val entries = (0 until 28).map { WeightEntry(day(it), 90.0 - it * 0.07) }
        val series = WeightTrend.ewma(entries).value()
        val window = GoalEta.fromTrend(85.0, series, day(27)).value()
        assertTrue(window.start < window.end)
        assertTrue(window.centralDays in 30..60, "central ${window.centralDays}")
        assertEquals("goal.basis.trend", window.basisKey)
        assertTrue(window.end > day(27))
    }

    @Test
    fun whatIfUsesTheSeventySevenHundredRule() {
        val window = GoalEta.whatIf(90.0, 85.0, dailyDeltaKcal = -500.0, today = day(0)).value()
        // 5 kg x 7700 / 500 = 77 days.
        assertEquals(77, window.centralDays)
    }

    @Test
    fun aTrendMovingAwayHasNoDate() {
        val entries = (0 until 20).map { WeightEntry(day(it), 80.0 + it * 0.05) }
        val series = WeightTrend.ewma(entries).value()
        val result = GoalEta.fromTrend(75.0, series, day(19))
        assertIs<FormulaOutcome.NotAvailable>(result)
    }

    @Test
    fun sleepDebtAndConsistency() {
        val nights = listOf(
            SleepNight(day(0), 400.0),
            SleepNight(day(1), 500.0),
            SleepNight(day(2), 480.0),
        )
        val result = SleepDebt.compute(nights, needMinutes = 480.0).value()
        assertEquals(80.0, result.debtMinutes, 0.001)
        assertEquals(3, result.nightsCounted)
        assertEquals(400.0, result.shortestMinutes, 0.0)
        assertTrue(result.consistencyMinutes > 0.0)
    }

    @Test
    fun emptySleepHasNoDebt() {
        assertIs<FormulaOutcome.NotAvailable>(SleepDebt.compute(emptyList(), 480.0))
    }
}

class RecoveryTest {
    @Test
    fun stepsAtTheDocumentedHours() {
        assertEquals(RecoveryStep.RECOVERING, MuscleRecovery.stepAtHours(10.0, false).value())
        assertEquals(RecoveryStep.EASING, MuscleRecovery.stepAtHours(30.0, false).value())
        assertEquals(RecoveryStep.READY, MuscleRecovery.stepAtHours(60.0, false).value())
        assertEquals(RecoveryStep.UNTRAINED, MuscleRecovery.stepAtHours(80.0, false).value())
    }

    @Test
    fun aHardSessionIsComparedWithTheMedianSession() {
        val history = MuscleLoadHistory(sessionMedianSets = 12.0, sessionMedianTonnage = 1_500.0)
        assertTrue(HardSession.isHard(sets = 16, tonnageKg = 1_800.0, history = history))
        assertTrue(!HardSession.isHard(sets = 12, tonnageKg = 1_500.0, history = history))
    }

    @Test
    fun aHardSessionHoldsTheMuscleOneStepLonger() {
        assertEquals(RecoveryStep.RECOVERING, MuscleRecovery.stepAtHours(30.0, true).value())
        assertEquals(RecoveryStep.EASING, MuscleRecovery.stepAtHours(60.0, true).value())
        assertEquals(RecoveryStep.READY, MuscleRecovery.stepAtHours(90.0, true).value())
        assertEquals(RecoveryStep.UNTRAINED, MuscleRecovery.stepAtHours(100.0, true).value())
    }
}

class ReadinessTest {
    @Test
    fun fullSleepAndBaselineRestingHeartRateScoreOneHundred() {
        val result = Readiness.compute(
            ReadinessInputs(
                sleepMinutes = 480.0,
                restingHeartRate = 60.0,
                restingHeartRateBaseline = Baseline(60.0, 28),
            ),
        ).value()
        assertEquals(100, result.score)
        assertEquals(2, result.parts.size)
        assertEquals(1.0, result.parts.sumOf { it.weight }, 1e-9)
    }

    @Test
    fun fewerThanTwoInputsIsNotANumber() {
        val result = Readiness.compute(ReadinessInputs(sleepMinutes = 420.0))
        assertIs<FormulaOutcome.NotAvailable>(result)
        assertEquals(NotAvailableReason.NOT_ENOUGH_DATA, result.reason)
    }

    @Test
    fun fewerThanSevenBaselineDaysIsNotANumber() {
        val result = Readiness.compute(
            ReadinessInputs(
                sleepMinutes = 480.0,
                restingHeartRate = 60.0,
                restingHeartRateBaseline = Baseline(60.0, 3),
            ),
        )
        assertIs<FormulaOutcome.NotAvailable>(result)
    }

    @Test
    fun aZeroSleepNeedIsNotANumber() {
        val result = Readiness.compute(
            ReadinessInputs(
                sleepMinutes = 420.0,
                sleepNeedMinutes = 0.0,
                trainingLoad48h = 100.0,
                trainingLoadDailyAverage28d = 100.0,
            ),
        )
        assertIs<FormulaOutcome.NotAvailable>(result)
    }

    @Test
    fun anUnusedShortHrvBaselineDoesNotHideAScoreItCanCompute() {
        val result = Readiness.compute(
            ReadinessInputs(
                sleepMinutes = 480.0,
                restingHeartRate = 60.0,
                restingHeartRateBaseline = Baseline(60.0, 30),
                hrvMillis = 50.0,
                hrvBaseline = Baseline(50.0, 3),
            ),
        ).value()
        assertEquals(2, result.parts.size)
    }

    @Test
    fun aOneMinuteSleepNeedIsOutOfRangeRatherThanEnoughData() {
        val result = Readiness.compute(
            ReadinessInputs(
                sleepMinutes = 420.0,
                sleepNeedMinutes = 1.0,
                trainingLoad48h = 100.0,
                trainingLoadDailyAverage28d = 100.0,
            ),
        )
        assertIs<FormulaOutcome.NotAvailable>(result)
        assertEquals(NotAvailableReason.OUT_OF_RANGE, result.reason)
    }

    @Test
    fun aThreeDayLoadBaselineIsNotANumber() {
        val result = Readiness.compute(
            ReadinessInputs(
                sleepMinutes = 480.0,
                trainingLoad48h = 100.0,
                trainingLoadDailyAverage28d = 100.0,
                trainingLoadBaselineDays = 3,
            ),
        )
        assertIs<FormulaOutcome.NotAvailable>(result)
    }

    @Test
    fun aHighRestingHeartRateScoresLower() {
        val baseline = Baseline(60.0, 28)
        val normal = Readiness.compute(
            ReadinessInputs(
                sleepMinutes = 480.0,
                restingHeartRate = 60.0,
                restingHeartRateBaseline = baseline,
            ),
        ).value()
        val elevated = Readiness.compute(
            ReadinessInputs(
                sleepMinutes = 480.0,
                restingHeartRate = 70.0,
                restingHeartRateBaseline = baseline,
            ),
        ).value()
        assertTrue(elevated.score < normal.score)
    }

    @Test
    fun aHeavyLoadLowersTheScore() {
        val rested = Readiness.compute(
            ReadinessInputs(
                sleepMinutes = 480.0,
                trainingLoad48h = 100.0,
                trainingLoadDailyAverage28d = 100.0,
            ),
        ).value()
        val loaded = Readiness.compute(
            ReadinessInputs(
                sleepMinutes = 480.0,
                trainingLoad48h = 400.0,
                trainingLoadDailyAverage28d = 100.0,
            ),
        ).value()
        assertTrue(loaded.score < rested.score)
    }
}

class CitationTableTest {
    @Test
    fun everyKeyHasARow() {
        CitationKey.entries.forEach { key ->
            val citation = Citations.of(key)
            assertEquals(key, citation.key)
            assertTrue(citation.url.startsWith("http"))
            assertTrue(citation.title.isNotBlank())
        }
    }

    @Test
    fun theSourcesThatNeedAttributionCarryALicenceNote() {
        assertTrue(Citations.of(CitationKey.UCUM).licenceNote != null)
        assertTrue(
            Citations.of(CitationKey.COMPENDIUM_OF_PHYSICAL_ACTIVITIES_2024).licenceNote != null,
        )
    }
}
