package app.openhealth.android.wear

import app.openhealth.core.seam.ClockSeam
import app.openhealth.core.seam.RestTimerSeam
import app.openhealth.core.watch.WatchExercise
import app.openhealth.core.watch.WatchLiveController
import app.openhealth.core.watch.WatchRest
import app.openhealth.core.watch.WatchSessionSummary
import app.openhealth.core.watch.WatchSet
import app.openhealth.core.watch.WatchSnapshot
import app.openhealth.data.LocalStore

/**
 * Builds what a wrist is allowed to see (`pr.md` §6.5, §6.18; `docs/adr/0019`).
 *
 * It reads the open session and the routine list from the same repositories the phone
 * screens use. It never computes a second progression or a second rest timer; the
 * current exercise is simply the first one still short of its target sets. A watch that
 * arrives after a process kill gets the same answer as one that never left.
 */
class WatchSnapshotBuilder(
    private val store: LocalStore,
    private val restTimer: RestTimerSeam,
    private val clock: ClockSeam,
    private val applied: WatchAppliedStore,
    private val weightUnit: () -> String = { "kg" },
    private val weightIncrementKg: () -> Double = { WatchLiveController.WEIGHT_STEP_KG },
) {
    fun build(nowMillis: Long = clock.now().toEpochMilliseconds()): WatchSnapshot {
        val open = store.training.openSession()
        val generated = nowMillis.coerceAtLeast(1L)
        val rest = restTimer.snapshot(nowMillis)?.takeIf { it.remainingSeconds > 0 || it.paused }
        val restModel = rest?.let {
            WatchRest(
                remainingSeconds = it.remainingSeconds.toInt(),
                totalSeconds = it.totalSeconds.toInt(),
                paused = it.paused,
            )
        }
        return WatchSnapshot(
            generatedAtMillis = generated,
            session = open?.let { session -> summary(session.id) },
            routines = emptyList(),
            rest = restModel,
            appliedCommandIds = applied.recent(),
            weightUnit = weightUnit(),
            weightIncrementKg = weightIncrementKg(),
        )
    }

    private fun summary(sessionId: String): WatchSessionSummary? {
        val session = store.training.sessionById(sessionId) ?: return null
        val sets = store.training.setsForSession(sessionId)
        val entries = store.training.sessionExercises(sessionId).sortedBy { it.position }
        val exercises = entries.mapNotNull { entry ->
            val exercise = store.training.exerciseById(entry.exercise_id) ?: return@mapNotNull null
            val own = sets.filter { it.session_exercise_id == entry.id }
            WatchExercise(
                id = exercise.id,
                entryId = entry.id,
                name = exercise.name,
                targetSets = (entry.target_sets ?: DEFAULT_SETS).toInt(),
                targetReps = (entry.target_reps ?: DEFAULT_REPS).toInt(),
                targetRepsHigh = entry.target_reps_high?.toInt(),
                targetWeightKg = entry.target_weight_kg,
                restSeconds = (
                    entry.rest_seconds
                        ?: session.routine_id?.let { id ->
                            store.training.routineById(id)?.target_rest_seconds
                        }
                        ?: DEFAULT_REST_SECONDS
                    ).toInt(),
                sets = own.map { set ->
                    WatchSet(
                        weightKg = set.weight_kg,
                        reps = set.reps.toInt(),
                        completedAtMillis = set.completed_at,
                        isWarmup = set.is_warmup,
                    )
                },
            )
        }
        val current = exercises.indexOfFirst { exercise ->
            exercise.sets.count { set -> !set.isWarmup } < exercise.targetSets
        }.let { if (it >= 0) it else exercises.lastIndex.coerceAtLeast(0) }
        return WatchSessionSummary(
            id = session.id,
            title = session.title ?: store.training.routineById(session.routine_id.orEmpty())?.name
                ?: "Workout",
            startedAtMillis = session.started_at,
            exercises = exercises,
            currentExerciseIndex = current,
            completedSetCount = exercises.sumOf { exercise ->
                exercise.sets.count { set -> !set.isWarmup }
            },
            sourceId = session.source_id,
        )
    }

    companion object {
        const val MAX_ROUTINES: Int = 5
        const val DEFAULT_SETS: Long = 3
        const val DEFAULT_REPS: Long = 8
        const val DEFAULT_REST_SECONDS: Long = 90
    }
}
