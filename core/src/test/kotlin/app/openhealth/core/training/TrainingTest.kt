package app.openhealth.core.training

import app.openhealth.core.body.Muscle
import app.openhealth.core.formula.FormulaOutcome
import app.openhealth.core.formula.SessionVolumeCalculator
import app.openhealth.core.formula.VolumeSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class RoutinePlannerTest {
    private val monday = LocalDate(2026, 11, 23)

    @Test
    fun aWeekdayRoutineOwnsItsDay() {
        val routines = listOf(
            RoutinePlacement("push", "Push", weekday = 1),
            RoutinePlacement("pull", "Pull", weekday = 3),
        )
        val plan = RoutinePlanner.today(routines, lastRoutineId = null, date = monday)
        assertEquals("push", plan?.routineId)
        assertEquals(PlanReason.WEEKDAY, plan?.reason)
    }

    @Test
    fun aFavouriteWinsTwoRoutinesOnTheSameDay() {
        val routines = listOf(
            RoutinePlacement("a", "A", weekday = 1),
            RoutinePlacement("b", "B", weekday = 1, favourite = true),
        )
        assertEquals("b", RoutinePlanner.today(routines, null, monday)?.routineId)
    }

    @Test
    fun rotationFollowsTheLastRoutineAndWraps() {
        val routines = listOf(
            RoutinePlacement("a", "A", rotationIndex = 1),
            RoutinePlacement("b", "B", rotationIndex = 2),
            RoutinePlacement("c", "C", rotationIndex = 3),
        )
        assertEquals("b", RoutinePlanner.today(routines, "a", monday)?.routineId)
        assertEquals("a", RoutinePlanner.today(routines, "c", monday)?.routineId)
        assertEquals(
            PlanReason.ROTATION_FIRST,
            RoutinePlanner.today(routines, null, monday)?.reason,
        )
    }

    @Test
    fun aWeekdayRoutineBeatsTheRotationOnItsDay() {
        val routines = listOf(
            RoutinePlacement("mon", "Monday", weekday = 1),
            RoutinePlacement("a", "A", rotationIndex = 1),
        )
        assertEquals("mon", RoutinePlanner.today(routines, null, monday)?.routineId)
    }

    @Test
    fun noRoutineMeansNoPlan() {
        assertNull(RoutinePlanner.today(emptyList(), null, monday))
        assertNull(
            RoutinePlanner.today(
                listOf(RoutinePlacement("tue", "Tuesday", weekday = 2)),
                null,
                monday,
            ),
        )
    }

    @Test
    fun theWeekStripRunsMondayToSunday() {
        val week = RoutinePlanner.week(LocalDate(2026, 11, 26))
        assertEquals(LocalDate(2026, 11, 23), week.first())
        assertEquals(LocalDate(2026, 11, 29), week.last())
        assertEquals(7, week.size)
    }

    @Test
    fun rotationLettersAreCappedAlphabetically() {
        assertEquals("A", rotationLetter(1))
        assertEquals("C", rotationLetter(3))
        assertEquals("Z", rotationLetter(26))
        assertEquals("A2", rotationLetter(27))
        assertNull(rotationLetter(null))
        assertNull(rotationLetter(0))
    }
}

class ProgressionTest {
    private fun input(
        weight: Double? = 80.0,
        reps: Int? = 5,
        missed: Boolean = false,
        sets: Int = 3,
        target: Int = 3,
        stall: Int = 0,
        low: Int = 5,
        high: Int = 8,
    ) = ProgressionInput(
        lastWeightKg = weight,
        lastReps = reps,
        targetRepsLow = low,
        targetRepsHigh = high,
        targetSets = target,
        completedSets = sets,
        missedReps = missed,
        sessionsAtLoad = stall,
    )

    @Test
    fun aFirstSessionHasNoNumberYet() {
        val decision = LinearProgression.next(input(weight = null, reps = null, sets = 0))
        assertEquals(ProgressionAction.START, decision.action)
        assertNull(decision.weightKg)
    }

    @Test
    fun linearAddsLoadAfterAFullSession() {
        val decision = LinearProgression.next(input())
        assertEquals(ProgressionAction.ADD_LOAD, decision.action)
        assertEquals(82.5, decision.weightKg)
    }

    @Test
    fun noPolicyAdvancesAfterAMissedRep() {
        ProgressionPolicy.all.forEach { policy ->
            val decision = policy.next(input(missed = true))
            assertTrue(
                decision.action != ProgressionAction.ADD_LOAD &&
                    decision.action != ProgressionAction.ADD_REPS,
                "${policy.key} advanced after a missed rep",
            )
            assertEquals(80.0, decision.weightKg, "${policy.key} changed the load on a miss")
        }
    }

    @Test
    fun anIncompleteSessionHoldsTheLoad() {
        val decision = LinearProgression.next(input(sets = 2, target = 3))
        assertEquals(ProgressionAction.HOLD, decision.action)
        assertEquals(ProgressionReason.INCOMPLETE_SESSION, decision.reason)
    }

    @Test
    fun aStalledExerciseDeloadsTenPercent() {
        val decision = LinearProgression.next(input(stall = 3))
        assertEquals(ProgressionAction.DELOAD, decision.action)
        assertEquals(70.0, decision.weightKg)
    }

    @Test
    fun doubleProgressionClimbsRepsThenLoads() {
        val nextRep = DoubleProgression.next(input(reps = 6))
        assertEquals(ProgressionAction.ADD_REPS, nextRep.action)
        assertEquals(80.0, nextRep.weightKg)
        assertEquals(7, nextRep.repsLow)

        val nextLoad = DoubleProgression.next(input(reps = 8))
        assertEquals(ProgressionAction.ADD_LOAD, nextLoad.action)
        assertEquals(82.5, nextLoad.weightKg)
        assertEquals(5, nextLoad.repsLow)
        assertEquals(8, nextLoad.repsHigh)
    }

    @Test
    fun aStallOfFailedSessionsStillDeloads() {
        val decision = LinearProgression.next(input(stall = 3, missed = true))
        assertEquals(ProgressionAction.DELOAD, decision.action)
        assertTrue(decision.weightKg!! < 80.0)
    }

    @Test
    fun offNeverChangesTheTarget() {
        val decision = OffProgression.next(input())
        assertEquals(ProgressionAction.OFF, decision.action)
        assertEquals(80.0, decision.weightKg)
    }

    @Test
    fun policySelectionFallsBackToLinear() {
        assertEquals("double", ProgressionPolicy.fromKey("double").key)
        assertEquals("linear", ProgressionPolicy.fromKey("invented").key)
        assertEquals("linear", ProgressionPolicy.fromKey(null).key)
    }
}

class PersonalRecordsTest {
    private val bench = TrainingSetRecord(80.0, 5)

    @Test
    fun theFirstSetIsABaselineNotARecord() {
        assertTrue(PersonalRecords.detect(emptyList(), bench).isEmpty())
    }

    @Test
    fun heavierBeatsLighterEvenAtFewerReps() {
        val records = PersonalRecords.detect(listOf(bench), TrainingSetRecord(85.0, 3))
        assertTrue(records.any { it.kind == PersonalRecordKind.HEAVIEST_WEIGHT })
    }

    @Test
    fun aBetterEstimatedOneRepMaxIsARecord() {
        // 80 x 5 estimates 93.3 kg; 85 x 4 estimates 96.3 kg.
        val records = PersonalRecords.detect(listOf(bench), TrainingSetRecord(85.0, 4))
        assertTrue(records.any { it.kind == PersonalRecordKind.ESTIMATED_ONE_REP_MAX })
    }

    @Test
    fun moreRepsAtTheSameWeightIsARecord() {
        val records = PersonalRecords.detect(listOf(bench), TrainingSetRecord(80.0, 6))
        assertTrue(records.any { it.kind == PersonalRecordKind.REPS_AT_WEIGHT })
    }

    @Test
    fun warmupsNeverEarnRecords() {
        assertTrue(
            PersonalRecords.detect(
                listOf(bench),
                TrainingSetRecord(100.0, 1, isWarmup = true),
            ).isEmpty(),
        )
    }
}

class PlateMathTest {
    private val plates = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)

    @Test
    fun aHundredKilosOnATwentyKiloBarIsExact() {
        val solution = assertNotNull(PlateMath.solve(100.0, 20.0, plates))
        assertEquals(listOf(25.0, 15.0), solution.perSideKg)
        assertEquals(100.0, solution.achievedKg, 0.001)
        assertTrue(solution.exact)
    }

    @Test
    fun anImpossibleTargetReportsTheGap() {
        val solution = assertNotNull(PlateMath.solve(101.0, 20.0, listOf(25.0, 10.0)))
        assertEquals(90.0, solution.achievedKg, 0.001)
        assertEquals(5.5, solution.perSideGapKg, 0.001)
        assertTrue(!solution.exact)
    }

    @Test
    fun belowTheBarIsNotASolution() {
        assertNull(PlateMath.solve(15.0, 20.0, plates))
        assertNull(PlateMath.solve(0.0, 20.0, plates))
    }

    @Test
    fun theEquipmentProfileRoundTrips() {
        val profile = EquipmentProfile()
        assertEquals(profile, EquipmentProfile.decode(EquipmentProfile.encode(profile)))
        assertEquals(EquipmentProfile(), EquipmentProfile.decode("not json"))
        assertEquals("barbell", EquipmentProfile().bar().key)
        assertEquals(10.0, EquipmentProfile().bar("ez").weightKg)
    }
}

class RestTimerTest {
    @Test
    fun aRestRunsDownAndStopsAtZero() {
        val running = RestTimer.tick(RestTimer.start(90), 20)
        assertEquals(70, running.remainingSeconds)
        assertEquals(0, RestTimer.tick(running, 120).remainingSeconds)
        assertTrue(!RestTimer.tick(running, 120).running)
    }

    @Test
    fun pauseHoldsTheClock() {
        val paused = RestTimer.togglePause(RestTimer.start(90))
        assertEquals(90, RestTimer.tick(paused, 30).remainingSeconds)
        assertEquals(60, RestTimer.tick(RestTimer.togglePause(paused), 30).remainingSeconds)
    }

    @Test
    fun fifteenSecondsEitherWayAndACeiling() {
        assertEquals(105, RestTimer.extend(RestTimer.start(90)).remainingSeconds)
        assertEquals(75, RestTimer.shorten(RestTimer.start(90)).remainingSeconds)
        assertEquals(600, RestTimer.extend(RestTimer.start(590), 60).remainingSeconds)
        assertEquals(0, RestTimer.shorten(RestTimer.start(10), 30).remainingSeconds)
    }

    @Test
    fun skipEndsTheRest() {
        assertEquals(0, RestTimer.skip().remainingSeconds)
        assertEquals(0f, RestTimer.skip().progress)
    }
}

class SessionSummaryTest {
    @Test
    fun primaryMusclesEarnAFullSetAndSecondaryHalf() {
        val loads = SessionLoads.byMuscle(
            listOf(
                PerformedSet(listOf(Muscle.QUADS), listOf(Muscle.GLUTES), 100.0, 5),
                PerformedSet(listOf(Muscle.QUADS), listOf(Muscle.GLUTES), 100.0, 5),
            ),
        )
        assertEquals(2.0, loads.getValue(Muscle.QUADS).weeklySets)
        assertEquals(1.0, loads.getValue(Muscle.GLUTES).weeklySets)
        assertEquals(1.0, loads.getValue(Muscle.QUADS).share.toDouble(), 0.001)
        assertEquals(0.5, loads.getValue(Muscle.GLUTES).share.toDouble(), 0.001)
    }

    @Test
    fun warmupsDoNotLoadTheMap() {
        assertTrue(
            SessionLoads.byMuscle(
                listOf(PerformedSet(listOf(Muscle.QUADS), emptyList(), 40.0, 10, isWarmup = true)),
            ).isEmpty(),
        )
    }

    @Test
    fun theClockTextRollsIntoHours() {
        assertEquals("0:05", clockText(5))
        assertEquals("1:05", clockText(65))
        assertEquals("1:01:05", clockText(3_665))
        assertEquals("0:00", clockText(-4))
    }

    @Test
    fun bodyweightLoadUsesThePersonsWeightAndAssistance() {
        val result = SessionVolumeCalculator.compute(
            listOf(
                VolumeSet(
                    weightKg = 10.0,
                    reps = 5,
                    isBodyweight = true,
                    assistanceKg = 20.0,
                    bodyweightKg = 80.0,
                ),
                VolumeSet(0.0, 5, isBodyweight = true),
            ),
        ).value()
        assertEquals((70.0 * 5) + (0.0 * 5), result.externalLoadKg, 0.001)
    }

    @Test
    fun bandResistanceCountsTowardVolume() {
        val result = SessionVolumeCalculator.compute(
            listOf(VolumeSet(60.0, 5, bandKg = 10.0)),
        ).value()
        assertEquals(350.0, result.externalLoadKg, 0.001)
    }
}

private fun <T> FormulaOutcome<T>.value(): T {
    assertTrue(this is FormulaOutcome.Computed, "expected a computed value")
    @Suppress("UNCHECKED_CAST")
    return (this as FormulaOutcome.Computed<T>).value
}

class ProgressionPlannerTest {
    private fun set(
        session: String,
        weight: Double,
        reps: Int,
        at: Long,
        warmup: Boolean = false,
        backoff: Boolean = false,
        drop: Boolean = false,
    ) = ProgressionSet(session, weight, reps, warmup, backoff, drop, at)

    @Test
    fun aBackoffSetNeverLowersTheNextTarget() {
        val input = ProgressionPlanner.input(
            ProgressionRequest(
                sets = listOf(
                    set("s1", 100.0, 5, 3, backoff = false),
                    set("s1", 100.0, 5, 2),
                    set("s1", 100.0, 5, 1),
                    set("s1", 80.0, 8, 4, backoff = true),
                    set("s1", 60.0, 5, 5, drop = true),
                ),
                currentSessionId = "s2",
                targetSets = 3,
                targetRepsLow = 5,
                targetRepsHigh = 8,
            ),
        )
        assertEquals(100.0, input.lastWeightKg)
        assertEquals(5, input.lastReps)
        assertEquals(3, input.completedSets)
        assertEquals(false, input.missedReps)
        assertEquals(102.5, LinearProgression.next(input).weightKg)
    }

    @Test
    fun aHeavyWorkSetIsTheLastPerformanceEvenWhenItWasNotLast() {
        val input = ProgressionPlanner.input(
            ProgressionRequest(
                sets = listOf(
                    set("s1", 100.0, 5, 1),
                    set("s1", 95.0, 5, 2),
                    set("s1", 90.0, 6, 3),
                ),
                currentSessionId = "s2",
                targetSets = 3,
                targetRepsLow = 5,
                targetRepsHigh = 8,
            ),
        )
        assertEquals(100.0, input.lastWeightKg)
        assertEquals(100.0, LinearProgression.next(input).weightKg?.minus(2.5))
    }

    @Test
    fun aMissedWorkSetHoldsEvenWhenABackoffRepIsHigh() {
        val input = ProgressionPlanner.input(
            ProgressionRequest(
                sets = listOf(
                    set("s1", 100.0, 4, 1),
                    set("s1", 80.0, 12, 2, backoff = true),
                ),
                currentSessionId = "s2",
                targetSets = 3,
                targetRepsLow = 5,
                targetRepsHigh = 8,
            ),
        )
        assertEquals(true, input.missedReps)
        assertEquals(100.0, LinearProgression.next(input).weightKg)
    }

    @Test
    fun threeSessionsAtOneLoadStallAndDeload() {
        val input = ProgressionPlanner.input(
            ProgressionRequest(
                sets = listOf(
                    set("s3", 100.0, 5, 3),
                    set("s3", 100.0, 5, 3),
                    set("s3", 100.0, 5, 3),
                    set("s2", 100.0, 5, 2),
                    set("s2", 100.0, 5, 2),
                    set("s2", 100.0, 5, 2),
                    set("s1", 100.0, 5, 1),
                    set("s1", 100.0, 5, 1),
                    set("s1", 100.0, 5, 1),
                ),
                currentSessionId = "s4",
                targetSets = 3,
                targetRepsLow = 5,
                targetRepsHigh = 8,
            ),
        )
        assertEquals(3, input.sessionsAtLoad)
        assertEquals(ProgressionAction.DELOAD, LinearProgression.next(input).action)
    }

    @Test
    fun aFirstSessionUsesItsOwnEarlierSetOnlyForThePrefill() {
        val input = ProgressionPlanner.input(
            ProgressionRequest(
                sets = listOf(set("s1", 80.0, 5, 1)),
                currentSessionId = "s1",
                targetSets = 3,
                targetRepsLow = 5,
                targetRepsHigh = 8,
            ),
        )
        assertEquals(80.0, input.lastWeightKg)
        assertEquals(1, input.completedSets)
        assertEquals(ProgressionAction.HOLD, LinearProgression.next(input).action)
    }
}

class TrainingUnitsTest {
    @Test
    fun poundsRoundTripAndMetricIsUnchanged() {
        assertEquals(100.0, TrainingUnits.toDisplay(100.0, false))
        assertEquals(220.462, TrainingUnits.toDisplay(100.0, true), 0.001)
        assertEquals(100.0, TrainingUnits.toKilograms(220.462, true), 0.001)
        assertEquals("kg", TrainingUnits.label(false))
        assertEquals("lb", TrainingUnits.label(true))
    }
}
