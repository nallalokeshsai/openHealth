package app.openhealth.core.training

import app.openhealth.core.body.Muscle
import app.openhealth.core.exercise.MusclePlan

/** One completed set with the muscles its exercise names, for the summary's mini map. */
data class PerformedSet(
    val primary: List<Muscle>,
    val secondary: List<Muscle>,
    val weightKg: Double,
    val reps: Int,
    val isWarmup: Boolean = false,
)

/**
 * What a session loaded, as the summary's mini map draws it (`DESIGN.md` §14.2): primary
 * muscles earn the full set, secondary half, warm-ups nothing. Shares are relative to the
 * most-loaded muscle, exactly like the plan map, so a session and a plan read the same
 * way without sharing colours.
 */
object SessionLoads {
    fun byMuscle(sets: List<PerformedSet>): Map<Muscle, MusclePlan> {
        val counts = mutableMapOf<Muscle, Double>()
        sets.filterNot { it.isWarmup }.forEach { set ->
            set.primary.forEach { counts[it] = (counts[it] ?: 0.0) + 1.0 }
            set.secondary.filterNot { it in set.primary }.forEach {
                counts[it] = (counts[it] ?: 0.0) + Muscle.SECONDARY_WEIGHT
            }
        }
        val top = counts.values.maxOrNull()?.takeIf { it > 0.0 } ?: return emptyMap()
        return counts.mapValues { (muscle, value) ->
            MusclePlan(muscle = muscle, weeklySets = value, share = value / top)
        }
    }

    /** The muscles a session trained most, in descending order, for the summary line. */
    fun topMuscles(sets: List<PerformedSet>, limit: Int = 3): List<Pair<Muscle, Double>> =
        byMuscle(sets).entries
            .sortedByDescending { it.value.weeklySets }
            .take(limit)
            .map { it.key to it.value.weeklySets }
}

/** Duration in whole seconds; a session still open is measured to [nowSeconds]. */
fun sessionDurationSeconds(startedAt: Long, endedAt: Long?, nowSeconds: Long): Long =
    ((endedAt ?: nowSeconds) - startedAt).coerceAtLeast(0L) / MILLIS_PER_SECOND

const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L

/** mm:ss for the session header and the rest bar; hours appear only past 60 minutes. */
fun clockText(totalSeconds: Long): String {
    val safe = totalSeconds.coerceAtLeast(0L)
    val hours = safe / SECONDS_PER_HOUR
    val minutes = (safe % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val seconds = safe % SECONDS_PER_MINUTE
    return if (hours > 0) {
        "$hours:${two(minutes)}:${two(seconds)}"
    } else {
        "${safe / SECONDS_PER_MINUTE}:${two(seconds)}"
    }
}

private fun two(value: Long): String = value.toString().padStart(2, '0')
