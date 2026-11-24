package app.openhealth.core.training

import app.openhealth.core.formula.FormulaOutcome
import app.openhealth.core.formula.OneRepMax

/**
 * One logged set as personal-record detection sees it. Warm-ups and bodyweight sets are
 * excluded by the caller or by [isWarmup]; a bodyweight set has no external load to rank.
 */
data class TrainingSetRecord(
    val weightKg: Double,
    val reps: Int,
    val isWarmup: Boolean = false,
    val isBodyweight: Boolean = false,
)

enum class PersonalRecordKind { HEAVIEST_WEIGHT, ESTIMATED_ONE_REP_MAX, REPS_AT_WEIGHT }

/** A record the just-finished set earned, with the value it beat. */
data class PersonalRecord(
    val kind: PersonalRecordKind,
    val weightKg: Double,
    val reps: Int,
    val previousValue: Double,
)

/**
 * PR detection (`pr.md` §6.5): the first set of an exercise is a baseline, not a record.
 * An estimated 1RM uses the same cited Epley equation as the rest of the app and is only
 * ranked inside its documented rep range.
 */
object PersonalRecords {
    fun detect(
        history: List<TrainingSetRecord>,
        candidate: TrainingSetRecord,
    ): List<PersonalRecord> {
        val records = mutableListOf<PersonalRecord>()
        val eligible = !candidate.isWarmup && !candidate.isBodyweight && candidate.weightKg > 0.0
        val prior = history.filterNot { it.isWarmup || it.isBodyweight || it.weightKg <= 0.0 }
        if (eligible && prior.isNotEmpty()) {
            val bestWeight = prior.maxOf { it.weightKg }
            if (candidate.weightKg > bestWeight) {
                records += PersonalRecord(
                    PersonalRecordKind.HEAVIEST_WEIGHT,
                    candidate.weightKg,
                    candidate.reps,
                    bestWeight,
                )
            }
            val bestOneRepMax = prior.mapNotNull { oneRepMax(it) }.maxOrNull()
            val candidateOneRepMax = oneRepMax(candidate)
            if (bestOneRepMax != null &&
                candidateOneRepMax != null &&
                candidateOneRepMax > bestOneRepMax
            ) {
                records += PersonalRecord(
                    PersonalRecordKind.ESTIMATED_ONE_REP_MAX,
                    candidate.weightKg,
                    candidate.reps,
                    bestOneRepMax,
                )
            }
            val bestReps = prior.filter { it.weightKg == candidate.weightKg }
                .maxOfOrNull { it.reps }
            if (bestReps != null && candidate.reps > bestReps) {
                records += PersonalRecord(
                    PersonalRecordKind.REPS_AT_WEIGHT,
                    candidate.weightKg,
                    candidate.reps,
                    bestReps.toDouble(),
                )
            }
        }
        return records
    }

    /** The best estimated 1RM in a history, or null when no set is in the formula's range. */
    fun bestOneRepMax(history: List<TrainingSetRecord>): Double? =
        history.filterNot { it.isWarmup || it.isBodyweight || it.weightKg <= 0.0 }
            .mapNotNull(::oneRepMax)
            .maxOrNull()

    private fun oneRepMax(set: TrainingSetRecord): Double? =
        (OneRepMax.estimate(set.weightKg, set.reps) as? FormulaOutcome.Computed)?.value
}
