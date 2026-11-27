package app.openhealth.core.watch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchLiveControllerTest {
    private val now = 1_700_000_000_000L

    private fun snapshot(
        weightUnit: String = "kg",
        step: Double = 2.5,
        sets: List<WatchSet> = emptyList(),
    ) = WatchSnapshot(
        generatedAtMillis = now,
        session = WatchSessionSummary(
            id = "s1",
            title = "Push",
            startedAtMillis = now - 600_000,
            exercises = listOf(
                WatchExercise(
                    id = "e1",
                    name = "Bench press",
                    targetSets = 3,
                    targetReps = 8,
                    targetWeightKg = 80.0,
                    restSeconds = 120,
                    sets = sets,
                ),
            ),
            currentExerciseIndex = 0,
            completedSetCount = sets.size,
        ),
        weightUnit = weightUnit,
        weightIncrementKg = step,
    )

    @Test
    fun aSnapshotSeedsTheDraftFromTheLastSetThenTheTarget() {
        val empty = WatchLiveController.onSnapshot(WatchLiveState(), snapshot(), now)
        assertEquals(80.0, empty.weightKg)
        assertEquals(8, empty.reps)
        assertTrue(empty.canLog)

        val logged = WatchLiveController.onSnapshot(
            WatchLiveState(),
            snapshot(sets = listOf(WatchSet(85.0, 6, now - 60_000))),
            now,
        )
        assertEquals(85.0, logged.weightKg)
        assertEquals(6, logged.reps)
    }

    @Test
    fun aPollDoesNotOverwriteTheDraftThePersonJustDialledIn() {
        // The wrist polls the phone every ten seconds. Reseeding on every snapshot threw
        // away the figure on the screen, so Log set wrote the previous set's weight.
        val seeded = WatchLiveController.onSnapshot(
            WatchLiveState(),
            snapshot(sets = listOf(WatchSet(80.0, 8, now - 60_000))),
            now,
        )
        val dialled = WatchLiveController.adjustReps(
            WatchLiveController.adjustWeight(seeded, 1),
            2,
        )
        assertEquals(82.5, dialled.weightKg)
        assertEquals(10, dialled.reps)

        val polled = WatchLiveController.onSnapshot(
            dialled,
            snapshot(sets = listOf(WatchSet(80.0, 8, now - 60_000))),
            now + 10_000,
        )
        assertEquals(82.5, polled.weightKg)
        assertEquals(10, polled.reps)
        assertEquals(82.5, WatchLiveController.logSet(polled, "cmd-1", now).command?.weightKg)
    }

    @Test
    fun movingToTheNextExerciseSeedsTheDraftAgain() {
        val first = snapshot(sets = listOf(WatchSet(80.0, 8, now - 60_000)))
        val dialled = WatchLiveController.adjustWeight(
            WatchLiveController.onSnapshot(WatchLiveState(), first, now),
            1,
        )
        val session = assertNotNull(first.session)
        val second = first.copy(
            session = session.copy(
                exercises = session.exercises + WatchExercise(
                    id = "e2",
                    name = "Row",
                    targetSets = 3,
                    targetReps = 10,
                    targetWeightKg = 60.0,
                ),
                currentExerciseIndex = 1,
            ),
        )
        val moved = WatchLiveController.onSnapshot(dialled, second, now + 10_000)
        assertEquals(60.0, moved.weightKg)
        assertEquals(10, moved.reps)
    }

    @Test
    fun aPhoneThatHasNoRestLeftClearsTheWristsRestOnceTheSetIsApplied() {
        val resting = WatchLiveController.logSet(
            WatchLiveController.onSnapshot(WatchLiveState(), snapshot(), now),
            "cmd-1",
            now,
        ).state
        assertEquals(120, resting.restRemainingSeconds(now))

        // The set is still queued, so a snapshot built before the phone saw it must not
        // pull the rest out from under the wrist.
        val racing = WatchLiveController.onSnapshot(
            resting,
            snapshot(sets = listOf(WatchSet(80.0, 8, now))),
            now + 1_000,
            pendingSets = true,
        )
        assertEquals(119, racing.restRemainingSeconds(now + 1_000))

        // Applied and the phone's rest skipped there: the wrist follows the phone.
        val cleared = WatchLiveController.onSnapshot(
            racing,
            snapshot(sets = listOf(WatchSet(80.0, 8, now))),
            now + 2_000,
        )
        assertNull(cleared.restEndsAtMillis)
        assertEquals(0, cleared.restTotalSeconds)
    }

    @Test
    fun theWeightStepperUsesTheProfilesStepAndNeverGoesNegative() {
        var state = WatchLiveController.onSnapshot(WatchLiveState(), snapshot(), now)
        state = WatchLiveController.adjustWeight(state, 1)
        assertEquals(82.5, state.weightKg)
        state = WatchLiveController.adjustWeight(WatchLiveController.adjustWeight(state, -1), -100)
        assertEquals(0.0, state.weightKg)

        val pounds = WatchLiveController.onSnapshot(
            WatchLiveState(),
            snapshot(weightUnit = "lb", step = 2.26796),
            now,
        )
        val stepped = WatchLiveController.adjustWeight(pounds, 1)
        assertEquals(82.26796, stepped.weightKg, 0.00001)
        assertEquals("lb", stepped.weightUnit)
    }

    @Test
    fun theRepStepperClampsToAUsableRange() {
        var state = WatchLiveController.onSnapshot(WatchLiveState(), snapshot(), now)
        state = WatchLiveController.adjustReps(state, 1)
        assertEquals(9, state.reps)
        state = WatchLiveController.adjustReps(state, -100)
        assertEquals(0, state.reps)
        state = WatchLiveController.adjustReps(state, 1000)
        assertEquals(WatchLiveController.MAX_REPS, state.reps)
    }

    @Test
    fun loggingASetEmitsOneCommandAndStartsTheExercisesRest() {
        val state = WatchLiveController.onSnapshot(WatchLiveState(), snapshot(), now)
        val result = WatchLiveController.logSet(state, "cmd-1", now)
        val command = assertNotNull(result.command)
        assertEquals(WatchCommandType.LOG_SET, command.type)
        assertEquals("cmd-1", command.id)
        assertEquals("s1", command.sessionId)
        assertEquals("e1", command.exerciseId)
        assertEquals(80.0, command.weightKg)
        assertEquals(8, command.reps)
        assertEquals(120, result.state.restRemainingSeconds(now))
        assertTrue(result.vibrate)
        assertEquals(1, result.state.pendingCount)
    }

    @Test
    fun aTickInTheMiddleKeepsTheDeadlineAndTheEndClearsIt() {
        val resting = WatchLiveController.logSet(
            WatchLiveController.onSnapshot(WatchLiveState(), snapshot(), now),
            "cmd-1",
            now,
        ).state
        val middle = WatchLiveController.tick(resting, now + 60_000)
        assertNotNull(middle.restEndsAtMillis)
        assertEquals(60, middle.restRemainingSeconds(now + 60_000))
        val finished = WatchLiveController.tick(resting, now + 130_000)
        assertNull(finished.restEndsAtMillis)
        assertEquals(0, finished.restTotalSeconds)
    }

    @Test
    fun restCanBeExtendedFifteenSecondsAndSkipped() {
        val resting = WatchLiveController.logSet(
            WatchLiveController.onSnapshot(WatchLiveState(), snapshot(), now),
            "cmd-1",
            now,
        ).state
        val extended = WatchLiveController.extendRest(resting, now)
        assertEquals(135, extended.restRemainingSeconds(now))
        val skipped = WatchLiveController.skipRest(extended, now)
        assertNull(skipped.restEndsAtMillis)
        assertEquals(0, skipped.restRemainingSeconds(now))
    }

    @Test
    fun aPhoneRestSnapshotRefreshesTheWristDeadline() {
        val withRest = snapshot().copy(
            rest = WatchRest(remainingSeconds = 45, totalSeconds = 90, paused = false),
        )
        val state = WatchLiveController.onSnapshot(WatchLiveState(), withRest, now)
        assertEquals(45, state.restRemainingSeconds(now))
        assertEquals(90, state.restTotalSeconds)
    }

    @Test
    fun noSessionMeansNoCommandRatherThanAnEmptyWrite() {
        val result = WatchLiveController.logSet(WatchLiveState(), "cmd-1", now)
        assertNull(result.command)
        assertEquals(WatchLiveState(), result.state)
    }

    @Test
    fun startAndFinishCarryTheSessionAndRoutine() {
        val start = WatchLiveController.startSession(WatchLiveState(), "c-start", now, "r1")
        assertEquals(WatchCommandType.START_SESSION, start.command?.type)
        assertEquals("r1", start.command?.routineId)
        assertEquals(1, start.state.pendingCount)

        val state = WatchLiveController.onSnapshot(WatchLiveState(), snapshot(), now)
        val finish = WatchLiveController.finishSession(state, "c-finish", now)
        assertEquals(WatchCommandType.FINISH_SESSION, finish.command?.type)
        assertEquals("s1", finish.command?.sessionId)
    }

    @Test
    fun anImpossibleHeartRateIsNotShown() {
        val state = WatchLiveController.heartRate(WatchLiveState(), 72)
        assertEquals(72, state.heartRateBpm)
        assertNull(WatchLiveController.heartRate(state, 5).heartRateBpm)
        assertNull(WatchLiveController.heartRate(state, null).heartRateBpm)
    }
}
