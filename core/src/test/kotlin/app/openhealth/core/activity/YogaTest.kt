package app.openhealth.core.activity

import app.openhealth.core.body.Muscle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class YogaTest {
    @Test
    fun libraryIsValidAndCarriesTheBuiltInFlows() {
        assertEquals(emptyList(), YogaLibrary.validate())
        val ids = YogaLibrary.flows.map { it.id }
        assertTrue(
            ids.containsAll(
                listOf(
                    "sun-salutation-a",
                    "sun-salutation-b",
                    "beginner-full-body",
                    "hip-opener",
                    "spine",
                    "post-run",
                    "desk-reset",
                    "bedtime",
                ),
            ),
        )
        assertTrue(YogaLibrary.poses.size >= 25)
    }

    @Test
    fun everyPoseHasAWhyAndAModification() {
        YogaLibrary.poses.forEach { pose ->
            assertTrue(pose.why.isNotBlank(), "${pose.id} has no why")
            assertTrue(pose.modification.isNotBlank(), "${pose.id} has no modification")
            assertTrue(pose.setup.size >= 2, "${pose.id} needs setup steps")
            assertTrue(pose.breathCue.isNotBlank(), "${pose.id} needs a breath cue")
            assertTrue(pose.defaultHoldSeconds >= 3)
        }
    }

    @Test
    fun everyFlowStepNamesARealPoseAndKeepsTheSessionLengthSane() {
        YogaLibrary.flows.forEach { flow ->
            assertTrue(flow.totalSeconds >= 60, "${flow.id} is shorter than a minute")
            flow.steps.forEach { step ->
                assertNotNull(YogaLibrary.pose(step.poseId), "${flow.id}/${step.poseId}")
            }
        }
    }

    @Test
    fun muscleLoadWeightspRimaryAndSecondaryPoses() {
        val flow = YogaLibrary.flow("beginner-full-body")!!
        val load = YogaLoad.of(flow)
        assertTrue(load.weights.isNotEmpty())
        assertTrue(load.weights.getValue(Muscle.QUADS) > 0.0)
        assertTrue(load.primary.isNotEmpty())
    }

    @Test
    fun playerWalksTheWholeFlowAndFinishes() {
        val flow = YogaLibrary.flow("desk-reset")!!
        var state = YogaPlayer.start(flow)
        assertTrue(state.phase == YogaPhase.TRANSITION || state.phase == YogaPhase.HOLD)
        state = YogaPlayer.tick(flow, state, flow.totalSeconds + 10)
        assertTrue(state.isDone)
        assertEquals(flow.totalSeconds, state.elapsedSeconds)
        assertNull(YogaPlayer.currentPose(flow, state))
    }

    @Test
    fun pauseStopsTheClockAndResumeContinuesIt() {
        val flow = YogaLibrary.flow("post-run")!!
        var state = YogaPlayer.start(flow)
        state = YogaPlayer.tick(flow, state, 10)
        state = YogaPlayer.togglePause(state)
        val pausedAt = state.elapsedSeconds
        state = YogaPlayer.tick(flow, state, 60)
        assertEquals(pausedAt, state.elapsedSeconds)
        state = YogaPlayer.togglePause(state)
        state = YogaPlayer.tick(flow, state, 1)
        assertEquals(pausedAt + 1, state.elapsedSeconds)
    }

    @Test
    fun skipMovesOneStepAndPreviousGoesBack() {
        val flow = YogaLibrary.flow("bedtime")!!
        var state = YogaPlayer.start(flow)
        val firstIndex = state.stepIndex
        state = YogaPlayer.skip(flow, state)
        assertEquals(firstIndex + 1, state.stepIndex)
        state = YogaPlayer.previous(flow, state)
        assertEquals(firstIndex, state.stepIndex)
    }

    @Test
    fun breathPatternCyclesAndReportsThePhase() {
        val pattern = BreathPattern.BOX
        assertEquals(16, pattern.cycleSeconds)
        assertEquals(BreathPhase.INHALE, pattern.phaseAt(0))
        assertEquals(BreathPhase.HOLD_IN, pattern.phaseAt(4))
        assertEquals(BreathPhase.EXHALE, pattern.phaseAt(8))
        assertEquals(BreathPhase.HOLD_OUT, pattern.phaseAt(12))
        assertEquals(BreathPhase.INHALE, pattern.phaseAt(16))
        assertEquals(4, pattern.remainingInPhase(0))
        assertEquals(2, pattern.remainingInPhase(2))
    }

    @Test
    fun yogaActivityLoadCarriesTheSessionIdAndMuscles() {
        val flow = YogaLibrary.flow("hip-opener")!!
        val at = Instant.parse("2026-11-24T18:00:00Z")
        val load = YogaLoad.loadOf(flow, at, "activity-yoga")
        assertEquals("activity-yoga", load.activityId)
        assertEquals(at, load.at)
        assertTrue(load.primary.contains(Muscle.GLUTES) || load.primary.contains(Muscle.ADDUCTORS))
    }

    @Test
    fun aSessionResultKeepsTheFlowAndItsLength() {
        val flow = YogaLibrary.flow("spine")!!
        val result = YogaSessionResult(
            flow = flow,
            durationSeconds = flow.totalSeconds.toLong(),
            posesHeld = flow.poseCount,
            muscles = YogaLoad.of(flow),
            completed = true,
        )
        assertEquals(flow.totalSeconds.toLong(), result.durationSeconds)
        assertEquals(flow.poseCount, result.posesHeld)
        assertTrue(result.completed)
    }
}
