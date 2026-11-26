@file:Suppress("ReturnCount")

package app.openhealth.core.body

import app.openhealth.core.formula.FormulaOutcome
import app.openhealth.core.formula.HardSession
import app.openhealth.core.formula.MuscleLoadHistory
import app.openhealth.core.formula.MuscleRecovery
import app.openhealth.core.formula.RecoveryStep
import app.openhealth.core.formula.RecoveryThresholds
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * One completed set, reduced to what the recovery map needs. The exercise's muscles are
 * resolved by the caller, because the map must not depend on a storage shape.
 */
data class LoadedSet(
    val sessionId: String,
    val exerciseId: String,
    val primary: List<Muscle>,
    val secondary: List<Muscle>,
    val completedAt: Instant,
    val weightKg: Double,
    val reps: Int,
    val isWarmup: Boolean = false,
    /** The source named on the set, and when the set was recorded (`pr.md` §7.2). */
    val sourceId: String? = null,
    val recordedAt: Instant? = null,
)

/**
 * A muscle's state on the map (`pr.md` §6.5). [step] is the classification, [hoursSinceLoad]
 * is what the sheet and the table say in words, and [hardSession] is why a muscle can hold
 * a step longer.
 */
data class MuscleRecoveryState(
    val muscle: Muscle,
    val step: RecoveryStep,
    val lastLoadedAt: Instant? = null,
    val hoursSinceLoad: Double? = null,
    val hardSession: Boolean = false,
    val sets: Double = 0.0,
    val tonnageKg: Double = 0.0,
    val lastSessionId: String? = null,
    val lastSourceId: String? = null,
    val lastRecordedAt: Instant? = null,
)

/**
 * A load that is not a set, such as a yoga pose or a run (`pr.md` §6.11: activities feed
 * the recovery map). [intensity] is 1.0 for a session that loads a muscle directly and
 * 0.5 for a supporting one; it contributes no tonnage, so it can never make a strength
 * session look hard.
 */
data class ActivityMuscleLoad(
    val activityId: String,
    val at: Instant,
    val primary: List<Muscle>,
    val secondary: List<Muscle> = emptyList(),
    val sourceId: String? = null,
    val recordedAt: Instant? = null,
    val intensity: Double = 1.0,
)

/**
 * The recovery engine (`pr.md` §6.5, `DESIGN.md` §9.1): every logged set loads the muscles
 * its exercise names, primary full and secondary half; a muscle's step comes from the hours
 * since its last load through [MuscleRecovery]; a session above that muscle's own four-week
 * median holds it one step longer through [HardSession]. A finished activity is the same
 * kind of load with no tonnage.
 *
 * Nothing is stored: the map is computed from the sets every time and a backfilled set
 * therefore changes it (pr.md §7.3).
 */
object RecoveryMap {
    /** pr.md §6.5: the median is taken over the muscle's own last four weeks. */
    const val HISTORY_DAYS: Int = 28

    private const val SECONDS_PER_HOUR = 3_600.0
    private const val WHOLE = 2.0

    fun compute(
        now: Instant,
        sets: List<LoadedSet>,
        thresholds: RecoveryThresholds = RecoveryThresholds(),
        historyDays: Int = HISTORY_DAYS,
        activityLoads: List<ActivityMuscleLoad> = emptyList(),
    ): Map<Muscle, MuscleRecoveryState> {
        val windowStart = now - historyDays.days
        val usable = sets.filter {
            !it.isWarmup && it.completedAt <= now && it.completedAt >= windowStart
        }
        val usableActivities = activityLoads.filter {
            it.at <= now && it.at >= windowStart
        }
        val perMuscle = Muscle.entries.associateWith { muscle ->
            val sessions = usable
                .filter { muscle in it.primary || muscle in it.secondary }
                .groupBy { it.sessionId }
                .map { (_, sessionSets) -> sessionLoad(sessionSets, muscle) }
                .sortedBy { it.completedAt }
            val activity = usableActivities
                .filter { muscle in it.primary || muscle in it.secondary }
                .maxByOrNull { it.at }
            stateOf(muscle, now, sessions, thresholds, activity)
        }
        return perMuscle
    }

    private data class SessionLoad(
        val sessionId: String,
        val completedAt: Instant,
        val recordedAt: Instant?,
        val sourceId: String?,
        val sets: Double,
        val tonnageKg: Double,
    )

    private fun sessionLoad(sessionSets: List<LoadedSet>, muscle: Muscle): SessionLoad {
        var sets = 0.0
        var tonnage = 0.0
        sessionSets.forEach { set ->
            val weight = if (muscle in set.primary) 1.0 else Muscle.SECONDARY_WEIGHT
            sets += weight
            tonnage += set.weightKg * set.reps * weight
        }
        val latest = sessionSets.maxBy { it.completedAt }
        return SessionLoad(
            sessionId = latest.sessionId,
            completedAt = latest.completedAt,
            recordedAt = sessionSets.mapNotNull { it.recordedAt }.maxOrNull(),
            sourceId = latest.sourceId,
            sets = sets,
            tonnageKg = tonnage,
        )
    }

    private fun stateOf(
        muscle: Muscle,
        now: Instant,
        sessions: List<SessionLoad>,
        thresholds: RecoveryThresholds,
        activity: ActivityMuscleLoad?,
    ): MuscleRecoveryState {
        val last = sessions.lastOrNull()
        if (last == null && activity == null) {
            return MuscleRecoveryState(muscle = muscle, step = RecoveryStep.UNTRAINED)
        }
        if (last == null || (activity != null && activity.at > last.completedAt)) {
            val loadedAt = activity!!.at
            val hours = (now - loadedAt).inWholeSeconds / SECONDS_PER_HOUR
            val step = (
                MuscleRecovery.stepAtHours(hours, hardSession = false, thresholds)
                    as? FormulaOutcome.Computed
                )?.value ?: RecoveryStep.UNTRAINED
            return MuscleRecoveryState(
                muscle = muscle,
                step = step,
                lastLoadedAt = loadedAt,
                hoursSinceLoad = hours,
                hardSession = false,
                sets = if (muscle in activity.primary) {
                    activity.intensity
                } else {
                    activity.intensity * Muscle.SECONDARY_WEIGHT
                },
                tonnageKg = 0.0,
                lastSessionId = activity.activityId,
                lastSourceId = activity.sourceId,
                lastRecordedAt = activity.recordedAt,
            )
        }
        val history = historyOf(sessions)
        val hard = history != null &&
            HardSession.isHard(
                sets = last.sets,
                tonnageKg = last.tonnageKg,
                history = history,
            )
        val hours = (now - last.completedAt).inWholeSeconds / SECONDS_PER_HOUR
        val step = (
            MuscleRecovery.stepAtHours(hours, hard, thresholds)
                as? FormulaOutcome.Computed
            )?.value
            ?: RecoveryStep.UNTRAINED
        return MuscleRecoveryState(
            muscle = muscle,
            step = step,
            lastLoadedAt = last.completedAt,
            hoursSinceLoad = hours,
            hardSession = hard,
            sets = last.sets,
            tonnageKg = last.tonnageKg,
            lastSessionId = last.sessionId,
            lastSourceId = last.sourceId,
            lastRecordedAt = last.recordedAt,
        )
    }

    /**
     * The median of the muscle's earlier sessions. One session is not a baseline: a
     * single workout would otherwise always equal its own median and never be hard.
     */
    private fun historyOf(sessions: List<SessionLoad>): MuscleLoadHistory? {
        val earlier = sessions.dropLast(1)
        if (earlier.isEmpty()) return null
        return MuscleLoadHistory(
            sessionMedianSets = median(earlier.map { it.sets }),
            sessionMedianTonnage = median(earlier.map { it.tonnageKg }),
        )
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[middle - 1] + sorted[middle]) / WHOLE
        } else {
            sorted[middle]
        }
    }

    /** The muscles the map can colour, in anatomy order, for a table or a legend. */
    fun orderedStates(states: Map<Muscle, MuscleRecoveryState>): List<MuscleRecoveryState> =
        Muscle.anatomyOrder.map { muscle ->
            states[muscle] ?: MuscleRecoveryState(muscle, RecoveryStep.UNTRAINED)
        }
}
