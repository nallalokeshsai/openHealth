package app.openhealth.core.exercise

import app.openhealth.core.body.Muscle

/** One exercise as a routine or programme plans it (`pr.md` §6.5). */
data class PlannedExercise(val exercise: Exercise, val targetSets: Int) {
    init {
        require(targetSets > 0) { "A planned exercise needs at least one set" }
    }
}

/** A muscle's share of a plan, as the map shades it (`DESIGN.md` §9.1, §8.3). */
data class MusclePlan(
    val muscle: Muscle,
    val weeklySets: Double,
    /** 1.0 for the muscle with the most sets; the accent ramp shades by this. */
    val share: Double,
)

/**
 * The routine and programme coverage behind the plan map (`pr.md` §6.5, `DESIGN.md`
 * §14.2 Train): a muscle earns its primary share from every planned set, a secondary half,
 * and the whole thing repeats once per training day the routine is run.
 */
object PlanCoverage {
    const val DEFAULT_SETS: Int = 3

    fun compute(exercises: List<PlannedExercise>, timesPerWeek: Int = 1): Map<Muscle, MusclePlan> {
        val weekly = mutableMapOf<Muscle, Double>()
        exercises.forEach { planned ->
            val sets = planned.targetSets.toDouble() * timesPerWeek
            planned.exercise.muscleWeights().forEach { (muscle, weight) ->
                weekly[muscle] = (weekly[muscle] ?: 0.0) + sets * weight
            }
        }
        val top = weekly.values.maxOrNull()?.takeIf { it > 0.0 }
        return top?.let {
            weekly.mapValues { (muscle, sets) ->
                MusclePlan(muscle = muscle, weeklySets = sets, share = sets / it)
            }
        }.orEmpty()
    }
}
