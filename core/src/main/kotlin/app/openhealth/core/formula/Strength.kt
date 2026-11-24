package app.openhealth.core.formula

import app.openhealth.core.citation.CitationKey

enum class OneRepMaxEstimator { EPLEY, BRZYCKI }

/**
 * Estimated one-rep max (`pr.md` §6.5). Both published equations are offered; the
 * sheet names the one used and shows the inputs. Above [MAX_REPS] the estimate is not
 * shown: the formulas are fitted to sets near failure, and a 30-rep extrapolation is
 * fiction.
 */
object OneRepMax {
    private const val EPLEY_DIVISOR = 30.0
    private const val BRZYCKI_CONSTANT = 36.0
    private const val BRZYCKI_OFFSET = 37.0
    private const val MIN_REPS = 1
    private const val MAX_REPS = 15
    private const val MIN_KG = 1.0
    private const val MAX_KG = 700.0

    fun estimate(
        weightKg: Double,
        reps: Int,
        estimator: OneRepMaxEstimator = OneRepMaxEstimator.EPLEY,
    ): FormulaOutcome<Double> {
        val inputs = listOf(input("weight", weightKg, "kg"), input("reps", reps.toDouble(), "1"))
        val citation = when (estimator) {
            OneRepMaxEstimator.EPLEY -> CitationKey.EPLEY_LESUER_1997
            OneRepMaxEstimator.BRZYCKI -> CitationKey.BRZYCKI_1993
        }
        return when {
            weightKg !in MIN_KG..MAX_KG || reps !in MIN_REPS..MAX_REPS ->
                notAvailable(NotAvailableReason.OUT_OF_RANGE, citation, inputs)

            reps == 1 -> computed(weightKg, citation, inputs)
            estimator == OneRepMaxEstimator.EPLEY -> computed(
                weightKg * (1.0 + reps / EPLEY_DIVISOR),
                citation,
                inputs,
            )

            else -> computed(
                weightKg * BRZYCKI_CONSTANT / (BRZYCKI_OFFSET - reps),
                citation,
                inputs,
            )
        }
    }
}

/** A completed set, as the volume calculation sees it. */
data class VolumeSet(
    val weightKg: Double,
    val reps: Int,
    val isWarmup: Boolean = false,
    val isBodyweight: Boolean = false,
    val completed: Boolean = true,
    /** Band or chain resistance added to the bar (`pr.md` §6.5). */
    val bandKg: Double = 0.0,
    /** Assistance subtracted from a bodyweight movement; never below zero. */
    val assistanceKg: Double = 0.0,
    /** The person's bodyweight when a set is a bodyweight movement; null means unknown. */
    val bodyweightKg: Double? = null,
) {
    /** External load this set moved, as the session summary counts it. */
    val externalLoadKg: Double
        get() = when {
            isBodyweight -> {
                val base = ((bodyweightKg ?: 0.0) - assistanceKg).coerceAtLeast(0.0)
                base + bandKg + weightKg.coerceAtLeast(0.0)
            }
            else -> weightKg.coerceAtLeast(0.0) + bandKg
        }
}

data class SessionVolume(
    val externalLoadKg: Double,
    val workSets: Int,
    val warmupSets: Int,
    val totalReps: Int,
    val heaviestKg: Double?,
)

/**
 * Session volume: the sum of weight times reps over completed work sets. Warm-up sets
 * are counted but not added to the external load, so a long ramp does not inflate the
 * session total; bodyweight work contributes reps and sets, not kilograms.
 */
object SessionVolumeCalculator {
    private const val MAX_WEIGHT_KG = 700.0
    private const val MAX_REPS = 200

    fun compute(sets: List<VolumeSet>): FormulaOutcome<SessionVolume> {
        val completed = sets.filter { it.completed }
        val inputs = listOf(
            input("sets", completed.size.toDouble(), "{sets}"),
            input("reps", completed.sumOf { it.reps }.toDouble(), "1"),
        )
        return when {
            completed.isEmpty() -> notAvailable(
                NotAvailableReason.MISSING_INPUT,
                CitationKey.APP_RULE,
            )
            completed.any {
                it.weightKg < 0.0 || it.weightKg > MAX_WEIGHT_KG || it.reps !in 0..MAX_REPS
            } -> notAvailable(NotAvailableReason.OUT_OF_RANGE, CitationKey.APP_RULE, inputs)

            else -> {
                val work = completed.filterNot { it.isWarmup }
                computed(
                    SessionVolume(
                        externalLoadKg = work.sumOf { it.externalLoadKg * it.reps },
                        workSets = work.size,
                        warmupSets = completed.size - work.size,
                        totalReps = completed.sumOf { it.reps },
                        heaviestKg = work.filterNot { it.isBodyweight }
                            .maxOfOrNull { it.weightKg },
                    ),
                    CitationKey.APP_RULE,
                    inputs,
                )
            }
        }
    }
}
