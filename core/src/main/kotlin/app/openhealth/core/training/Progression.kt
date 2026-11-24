package app.openhealth.core.training

/**
 * How the next target was chosen (`pr.md` §6.5). A decision always says why, because the
 * live session shows the policy, the last performance and the reason in one line.
 */
enum class ProgressionAction { START, HOLD, ADD_REPS, ADD_LOAD, DELOAD, OFF }

enum class ProgressionReason {
    FIRST_TIME,
    COMPLETED_ALL_REPS,
    COMPLETED_ALL_SETS,
    MISSED_REP,
    INCOMPLETE_SESSION,
    STALLED,
    POLICY_OFF,
}

/** What the last session at this exercise did, and what the plan asks for. */
data class ProgressionInput(
    val lastWeightKg: Double? = null,
    val lastReps: Int? = null,
    val targetRepsLow: Int,
    val targetRepsHigh: Int,
    val targetSets: Int,
    val completedSets: Int = 0,
    val missedReps: Boolean = false,
    /** Consecutive sessions at the same load with no increase; the policy's stall rule. */
    val sessionsAtLoad: Int = 0,
    val incrementKg: Double = DEFAULT_INCREMENT_KG,
) {
    init {
        require(targetRepsLow in MIN_REPS..MAX_REPS) { "targetRepsLow out of range" }
        require(targetRepsHigh in targetRepsLow..MAX_REPS) { "targetRepsHigh below the low end" }
        require(targetSets in 1..MAX_SETS) { "targetSets out of range" }
        require(incrementKg > 0.0) { "increment must be positive" }
    }

    companion object {
        const val MIN_REPS = 1
        const val MAX_REPS = 100
        const val MAX_SETS = 20
        const val DEFAULT_INCREMENT_KG = 2.5
    }
}

/**
 * The next set target. [weightKg] null means "no number yet" (a first session or a policy
 * that is off); [reason] is what the session screen explains.
 */
data class ProgressionDecision(
    val weightKg: Double?,
    val repsLow: Int,
    val repsHigh: Int,
    val action: ProgressionAction,
    val reason: ProgressionReason,
)

/**
 * The policy interface the MVP ships (`pr.md` §6.5). The other seven policies are
 * additive: each implements this and is selected by [key].
 */
interface ProgressionPolicy {
    val key: String
    fun next(input: ProgressionInput): ProgressionDecision

    companion object {
        val all: List<ProgressionPolicy> =
            listOf(LinearProgression, DoubleProgression, OffProgression)
        fun fromKey(key: String?): ProgressionPolicy =
            all.firstOrNull { it.key == key } ?: LinearProgression
    }
}

private const val DELOAD_FRACTION = 0.90
private const val DELOAD_ROUND = 100.0

/**
 * Linear: add load after every completed session, hold after a missed or incomplete one,
 * and deload after [STALL_SESSIONS] sessions without progress. A missed rep can never
 * advance the load (`pr.md` §6.5 acceptance criterion).
 */
object LinearProgression : ProgressionPolicy {
    override val key = "linear"

    internal const val STALL_SESSIONS = 3

    override fun next(input: ProgressionInput): ProgressionDecision {
        val last = input.lastWeightKg
        return when {
            last == null || input.completedSets == 0 ->
                decision(null, input, ProgressionAction.START, ProgressionReason.FIRST_TIME)
            input.sessionsAtLoad >= STALL_SESSIONS -> decision(
                deload(last, input.incrementKg),
                input,
                ProgressionAction.DELOAD,
                ProgressionReason.STALLED,
            )
            input.missedReps ->
                decision(last, input, ProgressionAction.HOLD, ProgressionReason.MISSED_REP)
            input.completedSets < input.targetSets ->
                decision(last, input, ProgressionAction.HOLD, ProgressionReason.INCOMPLETE_SESSION)
            else -> decision(
                last + input.incrementKg,
                input,
                ProgressionAction.ADD_LOAD,
                ProgressionReason.COMPLETED_ALL_REPS,
            )
        }
    }
}

/**
 * Double progression: climb the rep range at one load, then add load and return to the
 * bottom of the range. The range is the plan's, so a smaller increment is a plan choice,
 * not a policy invention.
 */
object DoubleProgression : ProgressionPolicy {
    override val key = "double"

    override fun next(input: ProgressionInput): ProgressionDecision {
        val last = input.lastWeightKg
        val reps = input.lastReps
        return when {
            last == null || reps == null || input.completedSets == 0 ->
                decision(null, input, ProgressionAction.START, ProgressionReason.FIRST_TIME)
            input.sessionsAtLoad >= LinearProgression.STALL_SESSIONS -> decision(
                deload(last, input.incrementKg),
                input,
                ProgressionAction.DELOAD,
                ProgressionReason.STALLED,
            )
            input.missedReps ->
                decision(last, input, ProgressionAction.HOLD, ProgressionReason.MISSED_REP)
            input.completedSets < input.targetSets ->
                decision(last, input, ProgressionAction.HOLD, ProgressionReason.INCOMPLETE_SESSION)
            reps < input.targetRepsHigh -> decision(
                last,
                input,
                ProgressionAction.ADD_REPS,
                ProgressionReason.COMPLETED_ALL_SETS,
                reps = Reps(reps + 1, input.targetRepsHigh),
            )
            else -> decision(
                last + input.incrementKg,
                input,
                ProgressionAction.ADD_LOAD,
                ProgressionReason.COMPLETED_ALL_REPS,
            )
        }
    }
}

/** Off: the plan stays exactly where it was trained. Nothing advances silently. */
object OffProgression : ProgressionPolicy {
    override val key = "off"

    override fun next(input: ProgressionInput): ProgressionDecision = decision(
        input.lastWeightKg,
        input,
        ProgressionAction.OFF,
        ProgressionReason.POLICY_OFF,
    )
}

private data class Reps(val low: Int, val high: Int)

private fun decision(
    weightKg: Double?,
    input: ProgressionInput,
    action: ProgressionAction,
    reason: ProgressionReason,
    reps: Reps = Reps(input.targetRepsLow, input.targetRepsHigh),
): ProgressionDecision = ProgressionDecision(
    weightKg = weightKg?.let { roundTo(it, DISPLAY_STEP_KG) },
    repsLow = reps.low,
    repsHigh = reps.high,
    action = action,
    reason = reason,
)

/** Ten percent off, rounded down to the increment and never below one increment. */
private fun deload(weightKg: Double, incrementKg: Double): Double {
    val target = weightKg * DELOAD_FRACTION
    val steps = ((target / incrementKg) * DELOAD_ROUND).toInt() / DELOAD_ROUND.toInt()
    return (steps * incrementKg).coerceAtLeast(incrementKg)
}

private const val DISPLAY_STEP_KG = 0.5

private fun roundTo(value: Double, step: Double): Double = kotlin.math.round(value / step) * step
