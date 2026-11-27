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
        val exercises = training.exercises().associate { it.id to it.name }
        return CoachPlan(
            training.routines()
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
                                exerciseName = exercises[entry.exercise_id]
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
        )
    }

    /**
     * The plan a diff must be drawn against: the live plan for a proposal that has not
     * been applied, and the before-snapshot for one that has, so an accepted card keeps
     * showing the change it made rather than two equal sides.
     */
    @Suppress("ReturnCount")
    fun planFor(proposalId: String): CoachPlan {
        val payload = coach.snapshot(proposalId) ?: return plan()
        val snapshot = runCatching {
            json.decodeFromString(PlanSnapshot.serializer(), payload)
        }.getOrNull() ?: return plan()
        val names = training.exercises().associate { it.id to it.name }
        return CoachPlan(
            snapshot.routines.map { routine ->
                CoachPlanRoutine(
                    id = routine.id,
                    name = routine.name,
                    weekday = routine.weekday?.toInt(),
                    entries = snapshot.entries
                        .filter { it.routineId == routine.id }
                        .map { entry ->
                            CoachPlanEntry(
                                id = entry.id,
                                exerciseId = entry.exerciseId,
                                exerciseName = names[entry.exerciseId] ?: entry.exerciseId,
                                sets = entry.targetSets?.toInt(),
                                reps = entry.targetReps?.toInt(),
                                repsHigh = entry.targetRepsHigh?.toInt(),
                                weightKg = entry.targetWeightKg,
                                rir = entry.targetRir,
                                position = entry.position.toInt(),
                            )
                        },
                )
            },
        )
    }

    @Suppress("ReturnCount")
    fun apply(proposalId: String): CoachApplyResult {
        val proposal = coach.proposal(proposalId)
            ?: return CoachApplyResult.Rejected(
                listOf(CoachFinding(CoachFindingCode.NO_ACTIONS)),
            )
        if (proposal.status != CoachProposalStatus.PROPOSED) {
            return CoachApplyResult.Rejected(listOf(CoachFinding(CoachFindingCode.NO_CHANGE)))
        }
        val actions = proposal.decodedActions
        if (actions.size != proposal.actions.size) {
            return CoachApplyResult.Rejected(listOf(CoachFinding(CoachFindingCode.WRONG_TYPE)))
        }
        val live = plan()
        val findings = CoachActionValidator.validate(actions, live)
        if (findings.isNotEmpty()) return CoachApplyResult.Rejected(findings)
        val newIds = actions.filterIsInstance<CoachAction.AddExercise>()
            .associateWith { ids.newId() }
        val snapshot = snapshot(actions, newIds.values.toList())
        db.transaction {
            coach.saveSnapshot(proposalId, json.encodeToString(PlanSnapshot.serializer(), snapshot))
            actions.forEach { write(it, newIds[it]) }
            coach.decide(proposalId, CoachProposalStatus.ACCEPTED)
        }
        return CoachApplyResult.Applied(proposalId)
    }

    /**
     * Restores the exact rows the proposal replaced. Only the newest accepted proposal
     * can be undone, so an older snapshot can never overwrite a later edit.
     */
    @Suppress("ReturnCount")
    fun undo(proposalId: String): Boolean {
        val proposal = coach.proposal(proposalId) ?: return false
        if (proposal.status != CoachProposalStatus.ACCEPTED) return false
        val newest = coach.proposals().firstOrNull { it.status == CoachProposalStatus.ACCEPTED }
        if (newest?.id != proposalId) return false
        val payload = coach.snapshot(proposalId) ?: return false
        val snapshot = runCatching {
            json.decodeFromString(PlanSnapshot.serializer(), payload)
        }.getOrNull() ?: return false
        db.transaction {
            snapshot.addedEntryIds.forEach { id ->
                if (training.routineEntryById(id) != null) training.deleteRoutineEntry(id)
            }
            snapshot.routines.forEach { routine ->
                val current = training.routineById(routine.id) ?: return@forEach
                training.upsertRoutine(
                    current.copy(
                        weekday = routine.weekday,
                        progression = routine.progression,
                        target_rest_seconds = routine.targetRestSeconds,
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
        }
        return true
    }

    @Suppress("ReturnCount")
    private fun write(action: CoachAction, newEntryId: String?) {
        when (action) {
            is CoachAction.AddExercise -> {
                val entries = training.routineEntries(action.routineId)
                val position = (entries.maxOfOrNull { it.position } ?: -1L) + 1
                training.upsertRoutineExercise(
                    Routine_exercise(
                        id = newEntryId ?: return,
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
                val entry = training.routineEntryById(action.entryId) ?: return
                training.upsertRoutineExercise(
                    entry.copy(
                        target_sets = action.sets?.toLong() ?: entry.target_sets,
                        target_reps = action.reps?.toLong() ?: entry.target_reps,
                        target_weight_kg = action.weightKg ?: entry.target_weight_kg,
                        target_rir = action.rir ?: entry.target_rir,
                    ),
                )
            }

            is CoachAction.MoveDay -> {
                val routine = training.routineById(action.routineId) ?: return
                training.upsertRoutine(
                    routine.copy(
                        weekday = action.weekday.toLong(),
                        updated_at = clock.now().toEpochMilliseconds(),
                        revision = routine.revision + 1,
                    ),
                )
            }
        }
    }

    private fun snapshot(actions: List<CoachAction>, addedEntryIds: List<String>): PlanSnapshot {
        val routineIds = actions.map(CoachAction::routineId).distinct()
        val routines = routineIds.mapNotNull(training::routineById)
        val entries = routineIds.flatMap(training::routineEntries)
        return PlanSnapshot(
            routines = routines.map { row: Routine ->
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
