package app.openhealth.data.repo

import app.openhealth.core.ai.CoachAction
import app.openhealth.core.ai.CoachActionValidator
import app.openhealth.core.ai.CoachFinding
import app.openhealth.core.ai.CoachFindingCode
import app.openhealth.core.ai.CoachPlan
import app.openhealth.core.ai.CoachPlanEntry
import app.openhealth.core.ai.CoachPlanRoutine
import app.openhealth.core.ids.IdGenerator
import app.openhealth.core.ids.RandomIdGenerator
import app.openhealth.core.seam.ClockSeam
import app.openhealth.core.seam.SystemClock
import app.openhealth.data.db.OpenHealthDb
import app.openhealth.data.db.Routine
import app.openhealth.data.db.Routine_exercise
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The rows an approved proposal is allowed to change, captured before it runs. */
@Serializable
private data class PlanSnapshot(
    val routines: List<RoutineSnapshot>,
    val entries: List<EntrySnapshot>,
    /** The rows this proposal created; Undo removes exactly these and nothing else. */
    val addedEntryIds: List<String> = emptyList(),
)

@Serializable
private data class RoutineSnapshot(
    val id: String,
    val name: String,
    val weekday: Long?,
    val progression: String?,
    val targetRestSeconds: Long?,
)

@Serializable
private data class EntrySnapshot(
    val id: String,
    val routineId: String,
    val exerciseId: String,
    val position: Long,
    val targetSets: Long?,
    val targetReps: Long?,
    val targetRepsHigh: Long?,
    val targetWeightKg: Double?,
    val notes: String?,
    val targetRir: Double?,
    val restSeconds: Long?,
    val progression: String?,
    val groupIndex: Long?,
)

sealed interface CoachApplyResult {
    data class Applied(val proposalId: String) : CoachApplyResult

    data class Rejected(val findings: List<CoachFinding>) : CoachApplyResult
}

/**
 * Applies an approved proposal through the normal training repository and restores the
 * before-snapshot on Undo (`pr.md` §6.15: "Every proposal is diffable and reversible").
 *
 * No row is deleted to make room for a change: only the entries the proposal itself
 * added are removed by Undo, and the snapshot proves which those were.
 */
class CoachPlanApplier(
    private val db: OpenHealthDb,
    private val training: TrainingRepository,
    private val coach: CoachRepository,
    private val clock: ClockSeam = SystemClock,
    private val ids: IdGenerator = RandomIdGenerator(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun plan(): CoachPlan {
        val catalogue = training.exercises().associate { it.id to it.name }
        return CoachPlan(
            routines = training.routines()
                .filter { it.archived_at == null }
                .map { routine ->
                    CoachPlanRoutine(
                        id = routine.id,
                        name = routine.name,
                        weekday = routine.weekday?.toInt(),
                        entries = training.routineEntries(routine.id).map { entry ->
                            CoachPlanEntry(
                                id = entry.id,
                                exerciseId = entry.exercise_id,
                                exerciseName = catalogue[entry.exercise_id]
                                    ?: entry.exercise_id,
                                sets = entry.target_sets?.toInt(),
                                reps = entry.target_reps?.toInt(),
                                repsHigh = entry.target_reps_high?.toInt(),
                                weightKg = entry.target_weight_kg,
                                rir = entry.target_rir,
                                position = entry.position.toInt(),
                            )
                        },
                    )
                },
            catalogue = catalogue,
        )
    }

    /**
     * The plan a diff must be drawn against: the live plan for a proposal that has not
     * been applied, and the before-snapshot for one that has. The snapshot now holds only
     * the rows the proposal touched, so the added rows are removed from the live plan and
     * the touched values are replaced.
     */
    @Suppress("ReturnCount")
    fun planFor(proposalId: String): CoachPlan {
        val payload = coach.snapshot(proposalId) ?: return plan()
        val snapshot = runCatching {
            json.decodeFromString(PlanSnapshot.serializer(), payload)
        }.getOrNull() ?: return plan()
        val live = plan()
        val added = snapshot.addedEntryIds.toSet()
        return live.copy(
            routines = live.routines.map { routine ->
                val oldRoutine = snapshot.routines.firstOrNull { it.id == routine.id }
                val entries = routine.entries
                    .filterNot { it.id in added }
                    .map { entry ->
                        val old = snapshot.entries.firstOrNull { it.id == entry.id }
                        if (old == null) {
                            entry
                        } else {
                            entry.copy(
                                sets = old.targetSets?.toInt(),
                                reps = old.targetReps?.toInt(),
                                repsHigh = old.targetRepsHigh?.toInt(),
                                weightKg = old.targetWeightKg,
                                rir = old.targetRir,
                            )
                        }
                    }
                routine.copy(
                    weekday = oldRoutine?.weekday?.toInt() ?: routine.weekday,
                    entries = entries,
                )
            },
        )
    }

    /**
     * Validates, snapshots and writes in one transaction. The proposal's status is read
     * again inside the transaction, so two taps cannot both apply the same card.
     */
    private class Abort(val findings: List<CoachFinding>) : RuntimeException()

    @Suppress("ReturnCount")
    fun apply(proposalId: String): CoachApplyResult = try {
        db.transactionWithResult {
            val proposal = coach.proposal(proposalId)
                ?: return@transactionWithResult CoachApplyResult.Rejected(
                    listOf(CoachFinding(CoachFindingCode.NO_ACTIONS)),
                )
            if (proposal.status != CoachProposalStatus.PROPOSED) {
                return@transactionWithResult CoachApplyResult.Rejected(
                    listOf(CoachFinding(CoachFindingCode.NO_CHANGE)),
                )
            }
            val actions = proposal.decodedActions
            if (actions.size != proposal.actions.size) {
                return@transactionWithResult CoachApplyResult.Rejected(
                    listOf(CoachFinding(CoachFindingCode.WRONG_TYPE)),
                )
            }
            val findings = CoachActionValidator.validate(actions, plan())
            if (findings.isNotEmpty()) {
                return@transactionWithResult CoachApplyResult.Rejected(findings)
            }
            val newIds = actions.filterIsInstance<CoachAction.AddExercise>()
                .map { it to ids.newId() }
                .toMutableList()
            val snapshot = snapshot(actions, newIds.map { it.second })
            coach.saveSnapshot(proposalId, json.encodeToString(PlanSnapshot.serializer(), snapshot))
            actions.forEach { action ->
                val index = newIds.indexOfFirst { it.first == action }
                val newId = if (index >= 0) newIds.removeAt(index).second else null
                if (!write(action, newId)) {
                    throw Abort(listOf(CoachFinding(CoachFindingCode.UNKNOWN_ENTRY)))
                }
            }
            if (!coach.decide(proposalId, CoachProposalStatus.ACCEPTED)) {
                throw Abort(listOf(CoachFinding(CoachFindingCode.NO_CHANGE)))
            }
            CoachApplyResult.Applied(proposalId)
        }
    } catch (abort: Abort) {
        CoachApplyResult.Rejected(abort.findings)
    }

    /**
     * Restores only the rows the proposal changed: its added entries are removed and the
     * named entries and weekday are put back. A later manual edit to another entry in the
     * same routine is untouched.
     */
    @Suppress("ReturnCount")
    fun undo(proposalId: String): Boolean = db.transactionWithResult {
        val proposal = coach.proposal(proposalId) ?: return@transactionWithResult false
        if (proposal.status != CoachProposalStatus.ACCEPTED) return@transactionWithResult false
        if (coach.latestAcceptedId() != proposalId) return@transactionWithResult false
        val payload = coach.snapshot(proposalId) ?: return@transactionWithResult false
        val snapshot = runCatching {
            json.decodeFromString(PlanSnapshot.serializer(), payload)
        }.getOrNull() ?: return@transactionWithResult false
        snapshot.addedEntryIds.forEach { id ->
            if (training.routineEntryById(id) != null) training.deleteRoutineEntry(id)
        }
        snapshot.routines.forEach { routine ->
            val current = training.routineById(routine.id) ?: return@forEach
            training.upsertRoutine(
                current.copy(
                    weekday = routine.weekday,
                    updated_at = clock.now().toEpochMilliseconds(),
                    revision = current.revision + 1,
                ),
            )
        }
        snapshot.entries.forEach { entry ->
            training.upsertRoutineExercise(
                Routine_exercise(
                    id = entry.id,
                    routine_id = entry.routineId,
                    exercise_id = entry.exerciseId,
                    position = entry.position,
                    target_sets = entry.targetSets,
                    target_reps = entry.targetReps,
                    target_reps_high = entry.targetRepsHigh,
                    target_weight_kg = entry.targetWeightKg,
                    notes = entry.notes,
                    target_rir = entry.targetRir,
                    rest_seconds = entry.restSeconds,
                    progression = entry.progression,
                    group_index = entry.groupIndex,
                ),
            )
        }
        coach.markReverted(proposalId)
        true
    }

    @Suppress("ReturnCount", "CyclomaticComplexMethod")
    private fun write(action: CoachAction, newEntryId: String?): Boolean {
        when (action) {
            is CoachAction.AddExercise -> {
                if (newEntryId == null) return false
                if (training.routineById(action.routineId) == null) return false
                if (training.exerciseById(action.exerciseId) == null) return false
                val entries = training.routineEntries(action.routineId)
                val position = (entries.maxOfOrNull { it.position } ?: -1L) + 1
                training.upsertRoutineExercise(
                    Routine_exercise(
                        id = newEntryId,
                        routine_id = action.routineId,
                        exercise_id = action.exerciseId,
                        position = position,
                        target_sets = action.sets.toLong(),
                        target_reps = action.reps.toLong(),
                        target_reps_high = action.reps.toLong(),
                        target_weight_kg = action.weightKg,
                        notes = null,
                        target_rir = action.rir,
                        rest_seconds = null,
                        progression = null,
                        group_index = null,
                    ),
                )
            }

            is CoachAction.UpdateTarget -> {
                val entry = training.routineEntryById(action.entryId)
                    ?: return false
                val actionReps = action.reps
                val reps = actionReps?.toLong() ?: entry.target_reps
                val high = if (actionReps != null) {
                    maxOf(actionReps.toLong(), entry.target_reps_high ?: actionReps.toLong())
                } else {
                    entry.target_reps_high
                }
                training.upsertRoutineExercise(
                    entry.copy(
                        target_sets = action.sets?.toLong() ?: entry.target_sets,
                        target_reps = reps,
                        target_reps_high = high,
                        target_weight_kg = action.weightKg ?: entry.target_weight_kg,
                        target_rir = action.rir ?: entry.target_rir,
                    ),
                )
            }

            is CoachAction.MoveDay -> {
                val routine = training.routineById(action.routineId) ?: return false
                training.upsertRoutine(
                    routine.copy(
                        weekday = action.weekday.toLong(),
                        updated_at = clock.now().toEpochMilliseconds(),
                        revision = routine.revision + 1,
                    ),
                )
            }
        }
        return true
    }

    private fun snapshot(actions: List<CoachAction>, addedEntryIds: List<String>): PlanSnapshot {
        val routineIds = actions.filterIsInstance<CoachAction.MoveDay>()
            .map(CoachAction::routineId)
            .distinct()
        val entryIds = actions.filterIsInstance<CoachAction.UpdateTarget>()
            .map(CoachAction.UpdateTarget::entryId)
            .toSet()
        val entries = entryIds.mapNotNull(training::routineEntryById)
        return PlanSnapshot(
            routines = routineIds.mapNotNull(training::routineById).map { row: Routine ->
                RoutineSnapshot(
                    id = row.id,
                    name = row.name,
                    weekday = row.weekday,
                    progression = row.progression,
                    targetRestSeconds = row.target_rest_seconds,
                )
            },
            addedEntryIds = addedEntryIds,
            entries = entries.map { row: Routine_exercise ->
                EntrySnapshot(
                    id = row.id,
                    routineId = row.routine_id,
                    exerciseId = row.exercise_id,
                    position = row.position,
                    targetSets = row.target_sets,
                    targetReps = row.target_reps,
                    targetRepsHigh = row.target_reps_high,
                    targetWeightKg = row.target_weight_kg,
                    notes = row.notes,
                    targetRir = row.target_rir,
                    restSeconds = row.rest_seconds,
                    progression = row.progression,
                    groupIndex = row.group_index,
                )
            },
        )
    }
}
