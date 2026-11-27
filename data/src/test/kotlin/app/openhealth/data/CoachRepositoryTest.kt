package app.openhealth.data

import app.openhealth.core.ai.CoachAction
import app.openhealth.core.ai.CoachAnswer
import app.openhealth.core.ai.CoachFailure
import app.openhealth.core.ai.CoachFailureKind
import app.openhealth.core.ai.CoachFinding
import app.openhealth.core.ai.CoachFindingCode
import app.openhealth.core.ai.CoachProposalDraft
import app.openhealth.core.ids.SeededIdGenerator
import app.openhealth.core.seam.FixedClock
import app.openhealth.data.LocalStore
import app.openhealth.data.repo.CoachApplyResult
import app.openhealth.data.repo.CoachPlanApplier
import app.openhealth.data.repo.CoachProposalStatus
import app.openhealth.data.repo.CoachRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** The coach's stored conversation and the apply/undo of one approved plan change. */
class CoachRepositoryTest {
    private val now = Instant.parse("2026-11-27T09:00:00Z")
    private val clock = FixedClock(now)

    private fun store(): LocalStore {
        val opened = inMemoryStore()
        val local = LocalStore(opened.driver, opened.db)
        local.training.upsertExercise(
            app.openhealth.data.db.Exercise(
                id = "bench",
                name = "Bench press",
                primary_muscles = "chest",
                secondary_muscles = "triceps",
                equipment = "barbell",
                instructions = null,
                level = null,
                mechanic = null,
                force = null,
                met_activity = null,
                is_custom = false,
                source_id = null,
                created_at = 0,
                updated_at = 0,
                revision = 1,
            ),
        )
        local.training.upsertRoutine(
            app.openhealth.data.db.Routine(
                id = "push",
                name = "Push",
                folder = null,
                notes = null,
                archived_at = null,
                created_at = 0,
                updated_at = 0,
                source_id = "manual",
                revision = 1,
                weekday = 1,
                rotation_index = null,
                favourite = false,
                progression = null,
                target_rest_seconds = null,
            ),
        )
        local.training.upsertRoutineExercise(
            app.openhealth.data.db.Routine_exercise(
                id = "entry",
                routine_id = "push",
                exercise_id = "bench",
                position = 0,
                target_sets = 3,
                target_reps = 8,
                target_reps_high = 8,
                target_weight_kg = 80.0,
                notes = null,
                target_rir = 2.0,
                rest_seconds = null,
                progression = null,
                group_index = null,
            ),
        )
        return local
    }

    private fun answer(actions: List<CoachAction>) = CoachAnswer(
        note = "One change.",
        proposals = listOf(
            CoachProposalDraft(
                summary = "Change the target",
                rationale = "The data supports it.",
                actions = actions,
            ),
        ),
    )

    @Test
    fun aConversationKeepsEveryMessageAndProposalInOrder() {
        val store = store()
        val coach = CoachRepository(store.db, clock, SeededIdGenerator(1))
        coach.appendPerson("Review my week.")
        val (messageId, proposalIds) = coach.appendCoach(
            answer(listOf(CoachAction.UpdateTarget("push", "entry", sets = 4))),
            meta = app.openhealth.data.repo.CoachTurnMeta(
                provider = "anthropic",
                model = "claude-sonnet-5-5",
            ),
        )
        val messages = coach.messages()
        assertEquals(2, messages.size)
        assertEquals("Review my week.", messages[0].body)
        assertEquals(messageId, messages[1].id)
        val proposals = coach.proposals()
        assertEquals(proposalIds, proposals.map { it.id })
        assertEquals(1, proposals.single().decodedActions.size)
        assertEquals("push", proposals.single().decodedActions.single().routineId)
        store.close()
    }

    @Test
    fun aFailureIsVisibleAsItsOwnRow() {
        val store = store()
        val coach = CoachRepository(store.db, clock, SeededIdGenerator(2))
        val id = coach.appendError(
            CoachFailure(
                CoachFailureKind.INVALID_OUTPUT,
                findings = listOf(CoachFinding(CoachFindingCode.BAD_SETS)),
            ),
            meta = app.openhealth.data.repo.CoachTurnMeta(provider = "anthropic", model = "m"),
        )
        val message = coach.messages().single()
        assertEquals(id, message.id)
        assertEquals(app.openhealth.data.repo.CoachRole.ERROR, message.role)
        assertTrue(message.errorKey!!.startsWith("invalid_output"))
        store.close()
    }

    @Test
    fun approvingAppliesTheChangeAndUndoRestoresTheExactRow() {
        val store = store()
        val coach = CoachRepository(store.db, clock, SeededIdGenerator(3))
        val applier = CoachPlanApplier(store.db, store.training, coach, clock, SeededIdGenerator(4))
        val (_, ids) = coach.appendCoach(
            answer(listOf(CoachAction.UpdateTarget("push", "entry", sets = 4, reps = 10))),
            meta = app.openhealth.data.repo.CoachTurnMeta(provider = "anthropic", model = "m"),
        )
        val proposalId = ids.single()
        val applied = applier.apply(proposalId)
        assertTrue(applied is CoachApplyResult.Applied)
        val changed = assertNotNull(store.training.routineEntryById("entry"))
        assertEquals(4L, changed.target_sets)
        assertEquals(10L, changed.target_reps)
        assertEquals(CoachProposalStatus.ACCEPTED, coach.proposal(proposalId)!!.status)

        assertTrue(applier.undo(proposalId))
        val restored = assertNotNull(store.training.routineEntryById("entry"))
        assertEquals(3L, restored.target_sets)
        assertEquals(8L, restored.target_reps)
        assertEquals(CoachProposalStatus.REVERTED, coach.proposal(proposalId)!!.status)
        store.close()
    }

    @Test
    fun undoRemovesOnlyTheEntryTheProposalAdded() {
        val store = store()
        val coach = CoachRepository(store.db, clock, SeededIdGenerator(5))
        val applier = CoachPlanApplier(store.db, store.training, coach, clock, SeededIdGenerator(6))
        val (_, ids) = coach.appendCoach(
            answer(
                listOf(
                    CoachAction.AddExercise(
                        routineId = "push",
                        exerciseId = "bench",
                        sets = 3,
                        reps = 5,
                        weightKg = 60.0,
                    ),
                ),
            ),
            meta = app.openhealth.data.repo.CoachTurnMeta(provider = "anthropic", model = "m"),
        )
        applier.apply(ids.single())
        assertEquals(2, store.training.routineEntries("push").size)
        // A later manual entry is not the proposal's and must survive Undo.
        store.training.upsertRoutineExercise(
            store.training.routineEntries("push").first().copy(
                id = "manual-entry",
                position = 2,
            ),
        )
        assertTrue(applier.undo(ids.single()))
        val remaining = store.training.routineEntries("push")
        assertEquals(listOf("entry", "manual-entry"), remaining.map { it.id }.sorted())
        store.close()
    }

    @Test
    fun anInvalidActionIsRejectedAndWritesNothing() {
        val store = store()
        val coach = CoachRepository(store.db, clock, SeededIdGenerator(7))
        val applier = CoachPlanApplier(store.db, store.training, coach, clock, SeededIdGenerator(8))
        val (_, ids) = coach.appendCoach(
            answer(listOf(CoachAction.UpdateTarget("push", "missing", sets = 4))),
            meta = app.openhealth.data.repo.CoachTurnMeta(provider = "anthropic", model = "m"),
        )
        val result = applier.apply(ids.single())
        assertTrue(result is CoachApplyResult.Rejected)
        assertEquals(3L, store.training.routineEntryById("entry")!!.target_sets)
        assertEquals(CoachProposalStatus.PROPOSED, coach.proposal(ids.single())!!.status)
        store.close()
    }

    @Test
    fun onlyTheNewestAcceptedProposalCanBeUndone() {
        val store = store()
        val coach = CoachRepository(store.db, clock, SeededIdGenerator(9))
        val applier =
            CoachPlanApplier(store.db, store.training, coach, clock, SeededIdGenerator(10))
        val first = coach.appendCoach(
            answer(listOf(CoachAction.UpdateTarget("push", "entry", sets = 4))),
            meta = app.openhealth.data.repo.CoachTurnMeta(provider = "p", model = "m"),
        ).second.single()
        val second = coach.appendCoach(
            answer(listOf(CoachAction.UpdateTarget("push", "entry", reps = 10))),
            meta = app.openhealth.data.repo.CoachTurnMeta(provider = "p", model = "m"),
        ).second.single()
        assertTrue(applier.apply(first) is CoachApplyResult.Applied)
        assertTrue(applier.apply(second) is CoachApplyResult.Applied)
        assertEquals(false, applier.undo(first))
        assertTrue(applier.undo(second))
        store.close()
    }

    @Test
    fun aSecondApplyOfTheSameProposalIsRejectedAndAddsNothing() {
        val store = store()
        val coach = CoachRepository(store.db, clock, SeededIdGenerator(20))
        val applier =
            CoachPlanApplier(store.db, store.training, coach, clock, SeededIdGenerator(21))
        val proposalId = coach.appendCoach(
            answer(
                listOf(
                    CoachAction.AddExercise(
                        routineId = "push",
                        exerciseId = "bench",
                        sets = 3,
                        reps = 5,
                        weightKg = 60.0,
                    ),
                ),
            ),
            meta = app.openhealth.data.repo.CoachTurnMeta(provider = "p", model = "m"),
        ).second.single()
        assertTrue(applier.apply(proposalId) is CoachApplyResult.Applied)
        assertEquals(2, store.training.routineEntries("push").size)
        assertTrue(applier.apply(proposalId) is CoachApplyResult.Rejected)
        assertEquals(2, store.training.routineEntries("push").size)
        store.close()
    }

    @Test
    fun undoLeavesAnUnrelatedEditInTheSameRoutineAlone() {
        val store = store()
        store.training.upsertRoutineExercise(
            app.openhealth.data.db.Routine_exercise(
                id = "entry-2", routine_id = "push", exercise_id = "bench", position = 1,
                target_sets = 5, target_reps = 5, target_reps_high = 5,
                target_weight_kg = 40.0, notes = null, target_rir = null,
                rest_seconds = null, progression = null, group_index = null,
            ),
        )
        val coach = CoachRepository(store.db, clock, SeededIdGenerator(22))
        val applier =
            CoachPlanApplier(store.db, store.training, coach, clock, SeededIdGenerator(23))
        val proposalId = coach.appendCoach(
            answer(listOf(CoachAction.UpdateTarget("push", "entry", sets = 4))),
            meta = app.openhealth.data.repo.CoachTurnMeta(provider = "p", model = "m"),
        ).second.single()
        assertTrue(applier.apply(proposalId) is CoachApplyResult.Applied)
        // A later manual edit that the proposal never named.
        store.training.upsertRoutineExercise(
            store.training.routineEntryById("entry-2")!!.copy(target_sets = 9),
        )
        assertTrue(applier.undo(proposalId))
        assertEquals(9L, store.training.routineEntryById("entry-2")!!.target_sets)
        assertEquals(3L, store.training.routineEntryById("entry")!!.target_sets)
        store.close()
    }

    @Test
    fun raisingRepsAlsoRaisesTheRangeHighSoTheProgressionEngineCanReadIt() {
        val store = store()
        val coach = CoachRepository(store.db, clock, SeededIdGenerator(24))
        val applier =
            CoachPlanApplier(store.db, store.training, coach, clock, SeededIdGenerator(25))
        val proposalId = coach.appendCoach(
            answer(listOf(CoachAction.UpdateTarget("push", "entry", reps = 12))),
            meta = app.openhealth.data.repo.CoachTurnMeta(provider = "p", model = "m"),
        ).second.single()
        assertTrue(applier.apply(proposalId) is CoachApplyResult.Applied)
        val entry = store.training.routineEntryById("entry")!!
        assertEquals(12L, entry.target_reps)
        assertEquals(12L, entry.target_reps_high)
        app.openhealth.core.training.ProgressionPlanner.input(
            app.openhealth.core.training.ProgressionRequest(
                sets = emptyList(),
                currentSessionId = null,
                targetRepsLow = entry.target_reps!!.toInt(),
                targetRepsHigh = entry.target_reps_high!!.toInt(),
                targetSets = entry.target_sets!!.toInt(),
            ),
        )
        store.close()
    }

    @Test
    fun anExerciseOutsideThePlannedRoutinesCanStillBeAdded() {
        val store = store()
        store.training.upsertExercise(
            app.openhealth.data.db.Exercise(
                id = "row", name = "Barbell row", primary_muscles = "back",
                secondary_muscles = "biceps", equipment = "barbell", instructions = null,
                level = null, mechanic = null, force = null, met_activity = null,
                is_custom = false, source_id = null, created_at = 0, updated_at = 0,
                revision = 1,
            ),
        )
        val coach = CoachRepository(store.db, clock, SeededIdGenerator(26))
        val applier =
            CoachPlanApplier(store.db, store.training, coach, clock, SeededIdGenerator(27))
        val proposalId = coach.appendCoach(
            answer(
                listOf(
                    CoachAction.AddExercise("push", "row", sets = 3, reps = 8, weightKg = 40.0),
                ),
            ),
            meta = app.openhealth.data.repo.CoachTurnMeta(provider = "p", model = "m"),
        ).second.single()
        assertTrue(applier.apply(proposalId) is CoachApplyResult.Applied)
        val added = store.training.routineEntries("push")
        assertEquals(2, added.size)
        assertTrue(added.any { it.exercise_id == "row" })
        store.close()
    }

    @Test
    fun theNewestAcceptedByDecisionTimeIsTheOneUndoCanRestore() {
        val store = store()
        val coach = CoachRepository(store.db, clock, SeededIdGenerator(50))
        val applier =
            CoachPlanApplier(store.db, store.training, coach, clock, SeededIdGenerator(51))
        val older = coach.appendCoach(
            answer(listOf(CoachAction.UpdateTarget("push", "entry", sets = 4))),
            meta = app.openhealth.data.repo.CoachTurnMeta(provider = "p", model = "m"),
        ).second.single()
        val newer = coach.appendCoach(
            answer(listOf(CoachAction.UpdateTarget("push", "entry", reps = 10))),
            meta = app.openhealth.data.repo.CoachTurnMeta(provider = "p", model = "m"),
        ).second.single()
        // The newer card is accepted first, then the older one.
        assertTrue(applier.apply(newer) is CoachApplyResult.Applied)
        clock.advance(kotlin.time.Duration.parse("1s"))
        assertTrue(applier.apply(older) is CoachApplyResult.Applied)
        assertEquals(false, applier.undo(newer))
        assertTrue(applier.undo(older))
        store.close()
    }

    @Test
    fun twoIdenticalAddActionsCreateTwoRows() {
        val store = store()
        val coach = CoachRepository(store.db, clock, SeededIdGenerator(52))
        val applier =
            CoachPlanApplier(store.db, store.training, coach, clock, SeededIdGenerator(53))
        val action = CoachAction.AddExercise("push", "bench", sets = 3, reps = 5, weightKg = 60.0)
        val proposalId = coach.appendCoach(
            answer(listOf(action, action)),
            meta = app.openhealth.data.repo.CoachTurnMeta(provider = "p", model = "m"),
        ).second.single()
        assertTrue(applier.apply(proposalId) is CoachApplyResult.Applied)
        assertEquals(3, store.training.routineEntries("push").size)
        store.close()
    }

    @Test
    fun aDiscardedProposalNeverApplies() {
        val store = store()
        val coach = CoachRepository(store.db, clock, SeededIdGenerator(11))
        val applier =
            CoachPlanApplier(store.db, store.training, coach, clock, SeededIdGenerator(12))
        val proposalId = coach.appendCoach(
            answer(listOf(CoachAction.UpdateTarget("push", "entry", sets = 5))),
            meta = app.openhealth.data.repo.CoachTurnMeta(provider = "p", model = "m"),
        ).second.single()
        assertTrue(coach.decide(proposalId, CoachProposalStatus.DISCARDED))
        assertTrue(applier.apply(proposalId) is CoachApplyResult.Rejected)
        assertEquals(3L, store.training.routineEntryById("entry")!!.target_sets)
        store.close()
    }

    @Test
    fun theSnapshotRoundTripsThroughTheDatabase() {
        val store = store()
        val coach = CoachRepository(store.db, clock, SeededIdGenerator(13))
        val proposalId = coach.appendCoach(
            answer(listOf(CoachAction.MoveDay("push", 3))),
            meta = app.openhealth.data.repo.CoachTurnMeta(provider = "p", model = "m"),
        ).second.single()
        val snapshotId = coach.saveSnapshot(proposalId, """{"routines":[],"entries":[]}""")
        assertNotNull(snapshotId)
        assertEquals(
            """{"routines":[],"entries":[]}""",
            coach.snapshot(proposalId),
        )
        assertNull(coach.snapshot("missing"))
        store.close()
    }
}
