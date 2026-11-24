package app.openhealth.core.training

/**
 * The rest timer's arithmetic (`DESIGN.md` §5.19, `pr.md` §6.5). The Android service owns
 * the clock and the notification; this object owns the rules so they are testable without
 * a device: a rest can be shortened to zero, extended, paused and resumed, and never runs
 * past [MAX_SECONDS].
 */
data class RestState(
    val totalSeconds: Int = RestTimer.DEFAULT_SECONDS,
    val remainingSeconds: Int = totalSeconds,
    val paused: Boolean = false,
) {
    val progress: Float
        get() = if (totalSeconds <= 0) {
            0f
        } else {
            (remainingSeconds.toFloat() / totalSeconds).coerceIn(0f, 1f)
        }

    val running: Boolean get() = remainingSeconds > 0 && !paused
}

object RestTimer {
    const val STEP_SECONDS = 15
    const val DEFAULT_SECONDS = 90
    const val MIN_SECONDS = 0
    const val MAX_SECONDS = 600

    fun start(seconds: Int = DEFAULT_SECONDS): RestState {
        val total = seconds.coerceIn(MIN_SECONDS, MAX_SECONDS)
        return RestState(totalSeconds = total, remainingSeconds = total)
    }

    fun tick(state: RestState, elapsedSeconds: Int): RestState {
        if (state.paused || elapsedSeconds <= 0) return state
        return state.copy(
            remainingSeconds = (state.remainingSeconds - elapsedSeconds).coerceAtLeast(0),
        )
    }

    fun extend(state: RestState, seconds: Int = STEP_SECONDS): RestState {
        val remaining = (state.remainingSeconds + seconds).coerceIn(MIN_SECONDS, MAX_SECONDS)
        val total = state.totalSeconds.coerceAtLeast(remaining)
        return state.copy(totalSeconds = total, remainingSeconds = remaining)
    }

    fun shorten(state: RestState, seconds: Int = STEP_SECONDS): RestState = extend(state, -seconds)

    fun togglePause(state: RestState): RestState = state.copy(paused = !state.paused)

    fun skip(): RestState = RestState(totalSeconds = 0, remainingSeconds = 0, paused = false)
}
