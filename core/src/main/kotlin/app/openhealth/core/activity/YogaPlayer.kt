@file:Suppress(
    "TooManyFunctions",
    "ReturnCount",
    "UnusedParameter",
)

package app.openhealth.core.activity

/**
 * The player's phase for one step: the settle/transition into the shape, the hold, and a
 * finish. A paused player keeps its state; a process death is handled by persisting this
 * value, not by rebuilding it from the clock.
 */
enum class YogaPhase { TRANSITION, HOLD, DONE }

data class YogaPlayerState(
    val flowId: String,
    val stepIndex: Int,
    val phase: YogaPhase,
    val secondsInPhase: Int,
    val elapsedSeconds: Int,
    val paused: Boolean = false,
    val totalSeconds: Int,
    val stepCount: Int,
) {
    val isDone: Boolean get() = phase == YogaPhase.DONE
}

/**
 * A deterministic session player (`pr.md` §6.7): tick, pause, resume, skip, go back and
 * repeat. It owns no clock and no platform: the screen or service calls [tick] once a
 * second, so a pause or a process death cannot drift the count.
 */
object YogaPlayer {
    fun start(flow: YogaFlow): YogaPlayerState = YogaPlayerState(
        flowId = flow.id,
        stepIndex = 0,
        phase = firstPhase(flow.steps.first()),
        secondsInPhase = 0,
        elapsedSeconds = 0,
        totalSeconds = flow.totalSeconds,
        stepCount = flow.steps.size,
    )

    /** Advances the player by [seconds]; a whole flow can be advanced in one call in a test. */
    fun tick(flow: YogaFlow, state: YogaPlayerState, seconds: Int = 1): YogaPlayerState {
        if (state.isDone || state.paused || seconds <= 0) return state
        var current = state
        var remaining = seconds
        while (remaining > 0 && !current.isDone) {
            val step = flow.steps[current.stepIndex]
            val phaseLength = lengthOf(step, current.phase)
            val left = phaseLength - current.secondsInPhase
            if (remaining < left) {
                current = current.copy(
                    secondsInPhase = current.secondsInPhase + remaining,
                    elapsedSeconds = current.elapsedSeconds + remaining,
                )
                remaining = 0
            } else {
                remaining -= left
                current = current.copy(
                    elapsedSeconds = current.elapsedSeconds + left,
                    secondsInPhase = 0,
                )
                current = advancePhase(flow, current)
            }
        }
        return current
    }

    fun togglePause(state: YogaPlayerState): YogaPlayerState =
        if (state.isDone) state else state.copy(paused = !state.paused)

    /** Skip the current pose or transition and move to the next step. */
    fun skip(flow: YogaFlow, state: YogaPlayerState): YogaPlayerState {
        if (state.isDone) return state
        return if (state.stepIndex >= flow.steps.lastIndex) {
            state.copy(phase = YogaPhase.DONE, secondsInPhase = 0)
        } else {
            val next = state.stepIndex + 1
            state.copy(
                stepIndex = next,
                phase = firstPhase(flow.steps[next]),
                secondsInPhase = 0,
            )
        }
    }

    /** Go back one step; the first step restarts itself. */
    fun previous(flow: YogaFlow, state: YogaPlayerState): YogaPlayerState {
        if (state.stepIndex == 0 || state.isDone) {
            return if (state.isDone) {
                val last = flow.steps.lastIndex
                state.copy(
                    stepIndex = last,
                    phase = firstPhase(flow.steps[last]),
                    secondsInPhase = 0,
                    paused = false,
                )
            } else {
                state.copy(phase = firstPhase(flow.steps.first()), secondsInPhase = 0)
            }
        }
        val previous = state.stepIndex - 1
        return state.copy(
            stepIndex = previous,
            phase = firstPhase(flow.steps[previous]),
            secondsInPhase = 0,
            paused = false,
        )
    }

    /** Repeat the current pose from its hold, keeping the elapsed total. */
    fun repeatStep(flow: YogaFlow, state: YogaPlayerState): YogaPlayerState {
        if (state.isDone) return state
        return state.copy(phase = YogaPhase.HOLD, secondsInPhase = 0, paused = false)
    }

    /** The pose at the current step, for the screen. */
    fun currentPose(flow: YogaFlow, state: YogaPlayerState): YogaPose? {
        if (state.isDone) return null
        val step = flow.steps.getOrNull(state.stepIndex) ?: return null
        return YogaLibrary.pose(step.poseId)
    }

    fun currentStep(flow: YogaFlow, state: YogaPlayerState): YogaStep? =
        if (state.isDone) null else flow.steps.getOrNull(state.stepIndex)

    fun phaseRemaining(flow: YogaFlow, state: YogaPlayerState): Int {
        if (state.isDone) return 0
        val step = flow.steps.getOrNull(state.stepIndex) ?: return 0
        return (lengthOf(step, state.phase) - state.secondsInPhase).coerceAtLeast(0)
    }

    /** 0..1 through the whole flow, for the progress bar. */
    fun progress(state: YogaPlayerState): Float = if (state.totalSeconds <= 0) {
        0f
    } else {
        (state.elapsedSeconds.toFloat() / state.totalSeconds).coerceIn(0f, 1f)
    }

    private fun firstPhase(step: YogaStep): YogaPhase =
        if (step.transitionSeconds > 0) YogaPhase.TRANSITION else YogaPhase.HOLD

    private fun lengthOf(step: YogaStep, phase: YogaPhase): Int = when (phase) {
        YogaPhase.TRANSITION -> step.transitionSeconds
        YogaPhase.HOLD -> step.holdSeconds
        YogaPhase.DONE -> 0
    }

    private fun advancePhase(flow: YogaFlow, state: YogaPlayerState): YogaPlayerState {
        if (state.phase == YogaPhase.TRANSITION) {
            return state.copy(phase = YogaPhase.HOLD, secondsInPhase = 0)
        }
        if (state.stepIndex >= flow.steps.lastIndex) {
            return state.copy(phase = YogaPhase.DONE, secondsInPhase = 0)
        }
        val next = state.stepIndex + 1
        return state.copy(
            stepIndex = next,
            phase = firstPhase(flow.steps[next]),
            secondsInPhase = 0,
        )
    }
}

/** The result of one completed flow, ready to be stored as an activity. */
data class YogaSessionResult(
    val flow: YogaFlow,
    val durationSeconds: Long,
    val posesHeld: Int,
    val muscles: YogaMuscleLoad,
    val completed: Boolean,
)
