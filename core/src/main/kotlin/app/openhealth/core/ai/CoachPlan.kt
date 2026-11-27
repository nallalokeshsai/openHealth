package app.openhealth.core.ai

/**
 * The training plan as the coach may read and propose against (`pr.md` §6.15: validate
 * exercise ids, sets, reps, loads and dates against the data model). A date in a routine
 * is its weekday; there is no free-floating date the app cannot keep.
 */
data class CoachPlanEntry(
    val id: String,
    val exerciseId: String,
    val exerciseName: String,
    val sets: Int?,
    val reps: Int?,
    val repsHigh: Int?,
    val weightKg: Double?,
    val rir: Double?,
    val position: Int,
)

data class CoachPlanRoutine(
    val id: String,
    val name: String,
    /** ISO weekday, Monday = 1. Null when the routine is not pinned to a day. */
    val weekday: Int?,
    val entries: List<CoachPlanEntry>,
)

data class CoachPlan(
    val routines: List<CoachPlanRoutine>,
    /** The exercise catalogue, so a proposal may add an exercise not yet in a routine. */
    val catalogue: Map<String, String> = emptyMap(),
) {
    fun routine(id: String): CoachPlanRoutine? = routines.firstOrNull { it.id == id }

    fun entry(routineId: String, entryId: String): CoachPlanEntry? =
        routine(routineId)?.entries?.firstOrNull { it.id == entryId }

    /** Every exercise id a proposal may name, with the catalogue name for display. */
    val exercises: Map<String, String>
        get() = catalogue + routines.flatMap { it.entries }
            .associate { it.exerciseId to it.exerciseName }

    @Suppress("CyclomaticComplexMethod")
    fun with(action: CoachAction): CoachPlan = when (action) {
        is CoachAction.AddExercise -> {
            val routine = routine(action.routineId) ?: return this
            val entry = CoachPlanEntry(
                // A diff row only needs a stable handle inside the working copy; the
                // storage layer mints the real id when the proposal is approved.
                id = "pending-${routine.entries.size}",
                exerciseId = action.exerciseId,
                exerciseName = exercises[action.exerciseId] ?: action.exerciseId,
                sets = action.sets,
                reps = action.reps,
                repsHigh = null,
                weightKg = action.weightKg,
                rir = action.rir,
                position = routine.entries.size,
            )
            copy(
                routines = routines.map {
                    if (it.id == routine.id) it.copy(entries = it.entries + entry) else it
                },
            )
        }

        is CoachAction.UpdateTarget -> copy(
            routines = routines.map { routine ->
                if (routine.id != action.routineId) {
                    routine
                } else {
                    routine.copy(
                        entries = routine.entries.map { entry ->
                            if (entry.id != action.entryId) {
                                entry
                            } else {
                                val reps = action.reps
                                entry.copy(
                                    sets = action.sets ?: entry.sets,
                                    reps = reps ?: entry.reps,
                                    repsHigh = if (reps != null) {
                                        maxOf(reps, entry.repsHigh ?: reps)
                                    } else {
                                        entry.repsHigh
                                    },
                                    weightKg = action.weightKg ?: entry.weightKg,
                                    rir = action.rir ?: entry.rir,
                                )
                            }
                        },
                    )
                }
            },
        )

        is CoachAction.MoveDay -> copy(
            routines = routines.map {
                if (it.id == action.routineId) it.copy(weekday = action.weekday) else it
            },
        )
    }
}

/** A typed plan change. The payload is what a proposal stores and what approval applies. */
sealed interface CoachAction {
    val routineId: String

    data class AddExercise(
        override val routineId: String,
        val exerciseId: String,
        val sets: Int,
        val reps: Int,
        val weightKg: Double? = null,
        val rir: Double? = null,
    ) : CoachAction

    data class UpdateTarget(
        override val routineId: String,
        val entryId: String,
        val sets: Int? = null,
        val reps: Int? = null,
        val weightKg: Double? = null,
        val rir: Double? = null,
    ) : CoachAction

    data class MoveDay(override val routineId: String, val weekday: Int) : CoachAction
}

enum class CoachFindingCode {
    NOT_JSON,
    MISSING_FIELD,
    WRONG_TYPE,
    UNKNOWN_FIELD,
    NO_PROPOSALS,
    TOO_MANY_PROPOSALS,
    TOO_MANY_ACTIONS,
    NO_ACTIONS,
    UNKNOWN_ROUTINE,
    UNKNOWN_ENTRY,
    UNKNOWN_EXERCISE,
    BAD_SETS,
    BAD_REPS,
    BAD_WEIGHT,
    BAD_RIR,
    BAD_WEEKDAY,
    NO_CHANGE,
    EMPTY_TEXT,
    UNSAFE_CLAIM,
}

data class CoachFinding(val code: CoachFindingCode, val actionIndex: Int? = null) {
    /** Stable text included in the one repair round; never shown to the person as-is. */
    fun repairText(): String = buildString {
        append(code.name.lowercase().replace('_', ' '))
        actionIndex?.let { append(" at action ").append(it + 1) }
    }
}

/**
 * Validates a proposal before it can be shown, and again before it can be applied.
 * Bounds are deliberately wide screens for mistakes, not training advice.
 */
object CoachActionValidator {
    const val MIN_SETS = 1
    const val MAX_SETS = 20
    const val MIN_REPS = 1
    const val MAX_REPS = 100
    const val MIN_WEIGHT_KG = 0.0
    const val MAX_WEIGHT_KG = 500.0
    const val MIN_RIR = 0.0
    const val MAX_RIR = 10.0
    const val MIN_WEEKDAY = 1
    const val MAX_WEEKDAY = 7
    const val MAX_ACTIONS = 10
    const val MAX_PROPOSALS = 5

    fun validate(actions: List<CoachAction>, plan: CoachPlan): List<CoachFinding> {
        if (actions.isEmpty()) return listOf(CoachFinding(CoachFindingCode.NO_ACTIONS))
        val findings = mutableListOf<CoachFinding>()
        if (actions.size > MAX_ACTIONS) {
            findings += CoachFinding(CoachFindingCode.TOO_MANY_ACTIONS)
        }
        actions.forEachIndexed { index, action ->
            findings += validateOne(action, plan, index)
        }
        return findings
    }

    private fun validateOne(action: CoachAction, plan: CoachPlan, index: Int): List<CoachFinding> {
        val routine = plan.routine(action.routineId)
            ?: return listOf(CoachFinding(CoachFindingCode.UNKNOWN_ROUTINE, index))
        return when (action) {
            is CoachAction.AddExercise -> validateAdd(action, plan, index)
            is CoachAction.UpdateTarget -> validateUpdate(action, plan, index)
            is CoachAction.MoveDay -> validateMove(action, routine, index)
        }
    }

    private fun validateAdd(
        action: CoachAction.AddExercise,
        plan: CoachPlan,
        index: Int,
    ): List<CoachFinding> = buildList {
        if (action.exerciseId !in plan.exercises) {
            add(CoachFinding(CoachFindingCode.UNKNOWN_EXERCISE, index))
        }
        if (action.sets !in MIN_SETS..MAX_SETS) add(CoachFinding(CoachFindingCode.BAD_SETS, index))
        if (action.reps !in MIN_REPS..MAX_REPS) add(CoachFinding(CoachFindingCode.BAD_REPS, index))
        if (action.weightKg != null && action.weightKg !in MIN_WEIGHT_KG..MAX_WEIGHT_KG) {
            add(CoachFinding(CoachFindingCode.BAD_WEIGHT, index))
        }
        if (action.rir != null && action.rir !in MIN_RIR..MAX_RIR) {
            add(CoachFinding(CoachFindingCode.BAD_RIR, index))
        }
    }

    private fun validateUpdate(
        action: CoachAction.UpdateTarget,
        plan: CoachPlan,
        index: Int,
    ): List<CoachFinding> {
        val entry = plan.entry(action.routineId, action.entryId)
            ?: return listOf(CoachFinding(CoachFindingCode.UNKNOWN_ENTRY, index))
        val findings = fieldFindings(action, index).toMutableList()
        if (hasNoField(action)) findings += CoachFinding(CoachFindingCode.NO_CHANGE, index)
        if (findings.isEmpty() && unchanged(action, entry)) {
            findings += CoachFinding(CoachFindingCode.NO_CHANGE, index)
        }
        return findings
    }

    private fun hasNoField(action: CoachAction.UpdateTarget): Boolean = action.sets == null &&
        action.reps == null &&
        action.weightKg == null &&
        action.rir == null

    private fun fieldFindings(action: CoachAction.UpdateTarget, index: Int): List<CoachFinding> =
        buildList {
            if (action.sets != null && action.sets !in MIN_SETS..MAX_SETS) {
                add(CoachFinding(CoachFindingCode.BAD_SETS, index))
            }
            if (action.reps != null && action.reps !in MIN_REPS..MAX_REPS) {
                add(CoachFinding(CoachFindingCode.BAD_REPS, index))
            }
            if (action.weightKg != null && action.weightKg !in MIN_WEIGHT_KG..MAX_WEIGHT_KG) {
                add(CoachFinding(CoachFindingCode.BAD_WEIGHT, index))
            }
            if (action.rir != null && action.rir !in MIN_RIR..MAX_RIR) {
                add(CoachFinding(CoachFindingCode.BAD_RIR, index))
            }
        }

    private fun unchanged(action: CoachAction.UpdateTarget, entry: CoachPlanEntry): Boolean =
        (action.sets == null || action.sets == entry.sets) &&
            (action.reps == null || action.reps == entry.reps) &&
            (action.weightKg == null || action.weightKg == entry.weightKg) &&
            (action.rir == null || action.rir == entry.rir)

    private fun validateMove(
        action: CoachAction.MoveDay,
        routine: CoachPlanRoutine,
        index: Int,
    ): List<CoachFinding> = when {
        action.weekday !in MIN_WEEKDAY..MAX_WEEKDAY ->
            listOf(CoachFinding(CoachFindingCode.BAD_WEEKDAY, index))

        routine.weekday == action.weekday ->
            listOf(CoachFinding(CoachFindingCode.NO_CHANGE, index))

        else -> emptyList()
    }
}

/** A before-and-after row the diff card renders (`DESIGN.md` §14 Coach). */
sealed interface CoachDiffRow {
    data class Added(
        val exerciseId: String,
        val exerciseName: String,
        val sets: Int,
        val reps: Int,
        val weightKg: Double?,
        val rir: Double?,
    ) : CoachDiffRow

    data class Target(
        val entryId: String,
        val exerciseName: String,
        val before: CoachTarget,
        val after: CoachTarget,
    ) : CoachDiffRow

    data class Day(val routineName: String, val before: Int?, val after: Int) : CoachDiffRow
}

data class CoachTarget(val sets: Int?, val reps: Int?, val weightKg: Double?, val rir: Double?)

object CoachDiff {
    /**
     * Rows are computed against the live plan so two actions on one entry show the real
     * sequence, not two copies of the original.
     */
    fun rows(actions: List<CoachAction>, plan: CoachPlan): List<CoachDiffRow> {
        val rows = mutableListOf<CoachDiffRow>()
        var working = plan
        actions.forEach { action ->
            when (action) {
                is CoachAction.AddExercise -> {
                    rows += CoachDiffRow.Added(
                        exerciseId = action.exerciseId,
                        exerciseName = working.exercises[action.exerciseId] ?: action.exerciseId,
                        sets = action.sets,
                        reps = action.reps,
                        weightKg = action.weightKg,
                        rir = action.rir,
                    )
                    working = working.with(action)
                }

                is CoachAction.UpdateTarget -> {
                    val before = working.entry(action.routineId, action.entryId) ?: return@forEach
                    val after = working.with(action).entry(action.routineId, action.entryId)
                        ?: return@forEach
                    rows += CoachDiffRow.Target(
                        entryId = before.id,
                        exerciseName = before.exerciseName,
                        before = targetOf(before),
                        after = targetOf(after),
                    )
                    working = working.with(action)
                }

                is CoachAction.MoveDay -> {
                    val routine = working.routine(action.routineId) ?: return@forEach
                    rows += CoachDiffRow.Day(
                        routineName = routine.name,
                        before = routine.weekday,
                        after = action.weekday,
                    )
                    working = working.with(action)
                }
            }
        }
        return rows
    }

    fun targetOf(entry: CoachPlanEntry): CoachTarget = CoachTarget(
        sets = entry.sets,
        reps = entry.reps,
        weightKg = entry.weightKg,
        rir = entry.rir,
    )
}

/** A parsed proposal before it is stored: one card with one or more plan changes. */
data class CoachProposalDraft(
    val summary: String,
    val rationale: String,
    val actions: List<CoachAction>,
)

data class CoachAnswer(val note: String, val proposals: List<CoachProposalDraft>)
