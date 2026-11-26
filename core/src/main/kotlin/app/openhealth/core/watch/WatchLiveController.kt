package app.openhealth.core.watch

import app.openhealth.core.training.RestTimer

/**
 * The wrist's set-and-rest state machine, shared by the Wear and Connect IQ surfaces and
 * tested without a watch. It never writes a record: [logSet] produces the command the
 * transport sends, and the phone decides what the write means (`docs/adr/0019`).
 *
 * The rest timer is a deadline, not a decrementing counter, so a process kill or a missed
 * tick cannot make it drift. [snapshot] refreshes the deadline from the phone when the
 * phone's rest alert is the one that is running.
 */
data class WatchLiveState(
    val snapshot: WatchSnapshot? = null,
    val weightKg: Double = 0.0,
    val reps: Int = 8,
    val restEndsAtMillis: Long? = null,
    val restTotalSeconds: Int = 0,
    val pendingCount: Int = 0,
    val heartRateBpm: Int? = null,
    val connected: Boolean = false,
    val nowMillis: Long = 0L,
) {
    val session: WatchSessionSummary? get() = snapshot?.session
    val currentExercise: WatchExercise?
        get() = session?.let { it.exercises.getOrNull(it.currentExerciseIndex) }
    val hasSession: Boolean get() = session != null
    val canLog: Boolean get() = session != null && currentExercise != null
    val weightUnit: String get() = snapshot?.weightUnit ?: "kg"
    val weightIncrementKg: Double get() = snapshot?.weightIncrementKg
        ?: WatchLiveController.WEIGHT_STEP_KG

    /** Whole seconds left; zero when no rest is running. */
    fun restRemainingSeconds(now: Long = nowMillis): Int {
        val ends = restEndsAtMillis ?: return 0
        return ((ends - now).coerceAtLeast(0L) / WatchLiveController.MILLIS_PER_SECOND).toInt()
    }

    fun restProgress(now: Long = nowMillis): Float {
        if (restTotalSeconds <= 0) return 0f
        return (restRemainingSeconds(now).toFloat() / restTotalSeconds).coerceIn(0f, 1f)
    }
}

/** The one side effect a reducer may ask for; the transport and vibration live outside. */
data class WatchActionResult(
    val state: WatchLiveState,
    val command: WatchCommand? = null,
    val vibrate: Boolean = false,
)

@Suppress("TooManyFunctions")
object WatchLiveController {
    const val DEFAULT_REPS: Int = 8
    const val MIN_WEIGHT_KG: Double = 0.0
    const val MAX_WEIGHT_KG: Double = 500.0
    const val MAX_REPS: Int = 100
    const val WEIGHT_STEP_KG: Double = 2.5
    const val REST_STEP_SECONDS: Int = 15
    const val DEFAULT_REST_SECONDS: Int = RestTimer.DEFAULT_SECONDS
    const val MILLIS_PER_SECOND: Long = 1_000L

    fun onSnapshot(
        state: WatchLiveState,
        snapshot: WatchSnapshot,
        nowMillis: Long,
        connected: Boolean = true,
    ): WatchLiveState {
        val exercise = snapshot.session?.exercises
            ?.getOrNull(snapshot.session.currentExerciseIndex)
        val last = exercise?.sets?.lastOrNull()
        val weight = last?.weightKg
            ?: exercise?.targetWeightKg
            ?: state.weightKg
        val reps = last?.reps
            ?: exercise?.targetReps
            ?: state.reps
        val rest = snapshot.rest
        val restEndsAt = if (rest != null && !rest.paused && rest.remainingSeconds > 0) {
            nowMillis + rest.remainingSeconds * WatchLiveController.MILLIS_PER_SECOND
        } else if (rest == null) {
            state.restEndsAtMillis?.takeIf { it > nowMillis }
        } else {
            null
        }
        return state.copy(
            snapshot = snapshot,
            weightKg = weight.coerceIn(MIN_WEIGHT_KG, MAX_WEIGHT_KG),
            reps = reps.coerceIn(0, MAX_REPS),
            restEndsAtMillis = restEndsAt,
            restTotalSeconds = rest?.totalSeconds ?: state.restTotalSeconds,
            nowMillis = nowMillis,
            connected = connected,
        )
    }

    fun onConnection(state: WatchLiveState, connected: Boolean): WatchLiveState =
        state.copy(connected = connected)

    fun adjustWeight(state: WatchLiveState, steps: Int): WatchLiveState {
        val step = if (state.weightIncrementKg > 0.0) state.weightIncrementKg else WEIGHT_STEP_KG
        val next = (state.weightKg + steps * step).coerceIn(MIN_WEIGHT_KG, MAX_WEIGHT_KG)
        return state.copy(weightKg = next)
    }

    fun adjustReps(state: WatchLiveState, steps: Int): WatchLiveState =
        state.copy(reps = (state.reps + steps).coerceIn(0, MAX_REPS))

    /**
     * Produces the set command and starts a local rest. The command's id is supplied by
     * the caller so it can be persisted before it is sent; a replayed queue then writes
     * nothing twice.
     */
    @Suppress("ReturnCount")
    fun logSet(
        state: WatchLiveState,
        commandId: String,
        nowMillis: Long,
        locale: String? = null,
    ): WatchActionResult {
        val session = state.session ?: return WatchActionResult(state)
        val exercise = state.currentExercise ?: return WatchActionResult(state)
        val command = WatchCommand(
            id = commandId,
            type = WatchCommandType.LOG_SET,
            happenedAtMillis = nowMillis,
            sessionId = session.id,
            exerciseId = exercise.id,
            weightKg = state.weightKg,
            reps = state.reps,
            locale = locale,
        )
        val restSeconds = exercise.restSeconds.coerceIn(0, RestTimer.MAX_SECONDS)
        return WatchActionResult(
            state = state.copy(
                restEndsAtMillis = if (restSeconds > 0) nowMillis + restSeconds * 1_000L else null,
                restTotalSeconds = restSeconds,
                pendingCount = state.pendingCount + 1,
                nowMillis = nowMillis,
            ),
            command = command,
            vibrate = true,
        )
    }

    fun startSession(
        state: WatchLiveState,
        commandId: String,
        nowMillis: Long,
        routineId: String? = null,
        locale: String? = null,
    ): WatchActionResult = WatchActionResult(
        state = state.copy(nowMillis = nowMillis, pendingCount = state.pendingCount + 1),
        command = WatchCommand(
            id = commandId,
            type = WatchCommandType.START_SESSION,
            happenedAtMillis = nowMillis,
            routineId = routineId,
            locale = locale,
        ),
    )

    fun finishSession(
        state: WatchLiveState,
        commandId: String,
        nowMillis: Long,
    ): WatchActionResult {
        val session = state.session ?: return WatchActionResult(state)
        return WatchActionResult(
            state = state.copy(pendingCount = state.pendingCount + 1, nowMillis = nowMillis),
            command = WatchCommand(
                id = commandId,
                type = WatchCommandType.FINISH_SESSION,
                happenedAtMillis = nowMillis,
                sessionId = session.id,
            ),
        )
    }

    fun heartRate(state: WatchLiveState, bpm: Int?): WatchLiveState = state.copy(
        heartRateBpm = bpm?.takeIf {
            it in
                WatchProtocol.MIN_HEART_RATE..WatchProtocol.MAX_HEART_RATE
        },
    )

    fun extendRest(state: WatchLiveState, nowMillis: Long): WatchLiveState {
        val ends = state.restEndsAtMillis ?: return state
        return state.copy(
            restEndsAtMillis = ends + REST_STEP_SECONDS * 1_000L,
            restTotalSeconds = (state.restTotalSeconds + REST_STEP_SECONDS)
                .coerceAtMost(RestTimer.MAX_SECONDS),
            nowMillis = nowMillis,
        )
    }

    fun skipRest(state: WatchLiveState, nowMillis: Long): WatchLiveState =
        state.copy(restEndsAtMillis = null, restTotalSeconds = 0, nowMillis = nowMillis)

    /** Clears an expired rest so the screen returns to the set without a stale deadline. */
    fun tick(state: WatchLiveState, nowMillis: Long): WatchLiveState {
        val ends = state.restEndsAtMillis ?: return state.copy(nowMillis = nowMillis)
        return if (nowMillis >= ends) {
            state.copy(restEndsAtMillis = null, restTotalSeconds = 0, nowMillis = nowMillis)
        } else {
            state.copy(nowMillis = nowMillis)
        }
    }
}
