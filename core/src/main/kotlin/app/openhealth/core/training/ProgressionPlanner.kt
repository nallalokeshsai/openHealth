package app.openhealth.core.training

/** One stored work set, as progression reads it; warm-ups, back-offs and drops are not load. */
data class ProgressionSet(
    val sessionId: String,
    val weightKg: Double,
    val reps: Int,
    val isWarmup: Boolean = false,
    val isBackoff: Boolean = false,
    val isDrop: Boolean = false,
    val completedAt: Long = 0L,
) {
    val countsAsLoad: Boolean get() = !isWarmup && !isBackoff && !isDrop
}

/**
 * Builds a [ProgressionInput] from a history (`pr.md` §6.5). "Last time" is the previous
 * session's **heaviest work set**, not whatever was logged last: a back-off or drop set
 * must never lower the next target, and a light back-off must not count as a missed rep.
 * A stall is a run of sessions whose heaviest work set is the same load.
 */
data class ProgressionRequest(
    val sets: List<ProgressionSet>,
    val currentSessionId: String?,
    val targetSets: Int,
    val targetRepsLow: Int,
    val targetRepsHigh: Int,
    val incrementKg: Double = ProgressionInput.DEFAULT_INCREMENT_KG,
)

object ProgressionPlanner {
    fun input(request: ProgressionRequest): ProgressionInput {
        val sets = request.sets
        val currentSessionId = request.currentSessionId
        val targetSets = request.targetSets
        val targetRepsLow = request.targetRepsLow
        val targetRepsHigh = request.targetRepsHigh
        val incrementKg = request.incrementKg
        val work = sets.filter { it.countsAsLoad }
        val prior = work.filter { it.sessionId != currentSessionId }
        val sessions = prior.groupBy { it.sessionId }
            .values
            .sortedByDescending { group -> group.maxOf { it.completedAt } }
        if (sessions.isNotEmpty()) {
            val last = sessions.first()
            val lastWeight = last.maxOf { it.weightKg }
            val lastReps = last.filter { it.weightKg == lastWeight }.maxOf { it.reps }
            val atLoad = sessions.takeWhile { group -> group.maxOf { it.weightKg } == lastWeight }
            return ProgressionInput(
                lastWeightKg = lastWeight,
                lastReps = lastReps,
                targetRepsLow = targetRepsLow,
                targetRepsHigh = targetRepsHigh,
                targetSets = targetSets,
                completedSets = last.size,
                missedReps = last.any { it.reps < targetRepsLow },
                sessionsAtLoad = atLoad.size,
                incrementKg = incrementKg,
            )
        }
        // A first session has no prior baseline; its own earlier sets only tell the
        // prefill what was just lifted.
        val current = work.filter { it.sessionId == currentSessionId }
        val fallback = (current.ifEmpty { work }).maxByOrNull { it.completedAt }
        return ProgressionInput(
            lastWeightKg = fallback?.weightKg,
            lastReps = fallback?.reps,
            targetRepsLow = targetRepsLow,
            targetRepsHigh = targetRepsHigh,
            targetSets = targetSets,
            completedSets = current.size,
            missedReps = current.any { it.reps < targetRepsLow },
            sessionsAtLoad = 0,
            incrementKg = incrementKg,
        )
    }
}
