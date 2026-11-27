package app.openhealth.data

import app.openhealth.core.body.Muscle
import app.openhealth.core.ids.SetId
import app.openhealth.core.observation.ObservationType
import app.openhealth.data.LocalStore
import app.openhealth.data.db.Exercise
import app.openhealth.data.db.Session
import app.openhealth.data.db.Session_exercise
import app.openhealth.data.db.Work_set
import app.openhealth.data.repo.RollupRepository
import app.openhealth.data.repo.TrainingRepository
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

class TrainingRepositoryTest {
    private val day = LocalDate(2026, 11, 23)
    private val ids = AtomicLong(0)

    private fun localStore(): LocalStore = inMemoryStore().let { LocalStore(it.driver, it.db) }

    private fun id(prefix: String): String = "$prefix-${ids.incrementAndGet()}"

    private fun seedExercise(training: TrainingRepository, id: String = "squat") {
        training.upsertExercise(
            Exercise(
                id = id,
                name = "Back squat",
                primary_muscles = Muscle.QUADS.key,
                secondary_muscles = Muscle.GLUTES.key,
                equipment = "barbell",
                instructions = null,
                level = "beginner",
                mechanic = "compound",
                force = "push",
                met_activity = null,
                is_custom = false,
                source_id = "manual",
                created_at = 0L,
                updated_at = 0L,
                revision = 1L,
            ),
        )
    }

    private fun session(
        store: LocalStore,
        exerciseId: String,
        start: Instant,
        ended: Instant? = start + 1.hours,
    ): String {
        val training = store.training
        val sessionId = id("session")
        val date = start.localDate()
        training.upsertSession(
            Session(
                id = sessionId,
                started_at = start.toEpochMilliseconds(),
                ended_at = ended?.toEpochMilliseconds(),
                local_date = date.toString(),
                zone_id = TEST_ZONE.id,
                happened_at = start.toEpochMilliseconds(),
                recorded_at = (start + 1.hours).toEpochMilliseconds(),
                routine_id = null,
                programme_id = null,
                notes = null,
                source_id = "manual",
                idempotency_key = "session-$sessionId",
                observation_id = null,
                raw_record_id = null,
                device = "manual",
                app_version = "test",
                revision = 1L,
                updated_at = start.toEpochMilliseconds(),
                title = "Leg day",
                feeling = null,
                perceived_effort = null,
            ),
        )
        val entryId = id("entry")
        training.upsertSessionExercise(
            Session_exercise(
                id = entryId,
                session_id = sessionId,
                exercise_id = exerciseId,
                position = 0L,
                notes = null,
                group_index = null,
                target_sets = 3L,
                target_reps = 5L,
                target_reps_high = 8L,
                target_weight_kg = 100.0,
                progression = "linear",
                rest_seconds = 120L,
            ),
        )
        listOf(true, false, false).forEachIndexed { index, warmup ->
            val at = start + (index * 5).minutes
            training.upsertSet(
                Work_set(
                    id = id("set"),
                    session_id = sessionId,
                    session_exercise_id = entryId,
                    exercise_id = exerciseId,
                    position = index.toLong(),
                    weight_kg = if (warmup) 40.0 else 100.0,
                    reps = 5L,
                    rir = 2.0,
                    rpe = null,
                    is_warmup = warmup,
                    is_backoff = false,
                    is_failure = false,
                    is_bodyweight = false,
                    tempo = null,
                    rest_seconds = 180L,
                    notes = null,
                    completed_at = at.toEpochMilliseconds(),
                    happened_at = at.toEpochMilliseconds(),
                    recorded_at = at.toEpochMilliseconds(),
                    source_id = "manual",
                    idempotency_key = id("set-key"),
                    observation_id = null,
                    raw_record_id = null,
                    device = "manual",
                    app_version = "test",
                    revision = 1L,
                    is_drop = false,
                    is_assisted = false,
                    assistance_kg = null,
                    band_kg = null,
                    original_weight_kg = null,
                    original_reps = null,
                    edit_reason = null,
                    edited_at = null,
                    user_edited = false,
                ),
            )
        }
        return sessionId
    }

    private fun Instant.localDate(): LocalDate = toLocalDateTime(TEST_ZONE).date

    @Test
    fun aFinishedSessionRollsUpItsVolumeSetsAndDuration() {
        val store = localStore()
        seedExercise(store.training)
        session(store, "squat", midday(day))
        val rollups = RollupRepository(store.db)
        val volume = rollups.forDayAndType(day, ObservationType.TRAINING_VOLUME)
        assertNotNull(volume)
        assertEquals(1000.0, volume.value.value, 1e-9)
        assertEquals(2.0, rollups.forDayAndType(day, ObservationType.TRAINING_SETS)?.value?.value)
        assertEquals(
            3_600.0,
            rollups.forDayAndType(day, ObservationType.TRAINING_DURATION)?.value?.value,
        )
        assertEquals("manual", volume.sourceId?.value)
        store.close()
    }

    @Test
    fun anOpenSessionIsNotRolledUpUntilItFinishes() {
        val store = localStore()
        seedExercise(store.training)
        val id = session(
            store,
            "squat",
            midday(day),
            ended = null,
        )
        assertNull(
            RollupRepository(store.db).forDayAndType(day, ObservationType.TRAINING_VOLUME),
        )
        val ended = midday(day) + 1.hours
        store.training.finishSession(id, ended.toEpochMilliseconds(), ended.toEpochMilliseconds())
        assertNotNull(
            RollupRepository(store.db).forDayAndType(day, ObservationType.TRAINING_VOLUME),
        )
        store.close()
    }

    @Test
    fun movingASessionRebuildsBothDaysAndKeepsRecordedAt() {
        val store = localStore()
        seedExercise(store.training)
        val start = midday(day)
        val id = session(store, "squat", start)
        val rollups = RollupRepository(store.db)
        assertNotNull(rollups.forDayAndType(day, ObservationType.TRAINING_VOLUME))
        val recorded = store.training.sessionById(id)!!.recorded_at
        val setRecorded = store.training.setsForSession(id).map { it.recorded_at }

        val moved = day.plusDays(1)
        val newStart = start + 24.hours
        assertTrue(
            store.training.moveSession(
                id,
                newStart.toEpochMilliseconds(),
                TEST_ZONE,
                newStart.toEpochMilliseconds(),
            ),
        )
        assertEquals(moved.toString(), store.training.sessionById(id)!!.local_date)
        assertEquals(recorded, store.training.sessionById(id)!!.recorded_at)
        assertEquals(setRecorded, store.training.setsForSession(id).map { it.recorded_at })
        assertNull(rollups.forDayAndType(day, ObservationType.TRAINING_VOLUME))
        assertEquals(
            1000.0,
            rollups.forDayAndType(moved, ObservationType.TRAINING_VOLUME)?.value?.value,
        )
        store.close()
    }

    @Test
    fun deletingASessionKeepsItWholeInTrashAndRestoresEverySet() {
        val store = localStore()
        seedExercise(store.training)
        val id = session(store, "squat", midday(day))
        val tombstone = assertNotNull(store.trash.deleteSession(id))
        assertNull(store.training.sessionById(id))
        assertTrue(store.training.setsForSession(id).isEmpty())
        assertNull(RollupRepository(store.db).forDayAndType(day, ObservationType.TRAINING_VOLUME))
        assertTrue(store.trash.undo(tombstone))
        assertNotNull(store.training.sessionById(id))
        assertEquals(3, store.training.setsForSession(id).size)
        assertNotNull(
            RollupRepository(store.db).forDayAndType(day, ObservationType.TRAINING_VOLUME),
        )
        store.close()
    }

    @Test
    fun editingASetKeepsTheOriginalValueAndReason() {
        val store = localStore()
        seedExercise(store.training)
        val id = session(store, "squat", midday(day))
        val set = store.training.setsForSession(id).first { !it.is_warmup }
        store.training.updateSet(
            set.id,
            app.openhealth.data.repo.SetValues(
                weightKg = 110.0,
                reps = 6,
                rir = 1.0,
                rpe = null,
                isWarmup = false,
                isBackoff = false,
                isFailure = false,
                isBodyweight = false,
                isDrop = false,
                isAssisted = false,
                assistanceKg = null,
                bandKg = null,
                tempo = null,
                restSeconds = null,
                notes = null,
                reason = "misload",
                editedAt = 1L,
            ),
        )
        val edited = store.training.setById(set.id)!!
        assertEquals(110.0, edited.weight_kg)
        assertEquals(100.0, edited.original_weight_kg)
        assertEquals(5L, edited.original_reps)
        assertEquals("misload", edited.edit_reason)
        assertTrue(edited.user_edited)
        store.close()
    }

    @Test
    fun deletingOneSetRebuildsTheSessionsDay() {
        val store = localStore()
        seedExercise(store.training)
        val id = session(store, "squat", midday(day))
        val set = store.training.setsForSession(id).first { !it.is_warmup }
        val tombstone = assertNotNull(store.trash.deleteWorkSet(SetId(set.id)))
        assertEquals(
            500.0,
            RollupRepository(store.db).forDayAndType(day, ObservationType.TRAINING_VOLUME)
                ?.value?.value,
        )
        assertTrue(store.trash.undo(tombstone))
        assertEquals(
            1000.0,
            RollupRepository(store.db).forDayAndType(day, ObservationType.TRAINING_VOLUME)
                ?.value?.value,
        )
        store.close()
    }

    @Test
    fun theTimelineCarriesSetsAsWellAsSessions() {
        val store = localStore()
        seedExercise(store.training)
        session(store, "squat", midday(day))
        val kinds = store.timeline.page(50).map { it.kind }.toSet()
        assertTrue("session" in kinds)
        assertTrue("work_set" in kinds)
        // The fresh schema's view must carry a set too, not only the migrated one.
        assertTrue(store.timeline.forDay(day).any { it.kind == "work_set" })
        store.close()
    }

    @Test
    fun theExerciseHistoryNamesEachSessionAndItsBestSet() {
        val store = localStore()
        seedExercise(store.training)
        val id = session(store, "squat", midday(day))
        val history = store.training.exerciseSessionSummary("squat")
        assertEquals(1, history.size)
        assertEquals(id, history.single().sessionId)
        assertEquals(2, history.single().setCount)
        assertEquals(100.0, history.single().topWeightKg)
    }

    @Test
    fun editingAFinishedSessionKeepsTheMomentItEnded() {
        val store = localStore()
        seedExercise(store.training)
        val start = midday(day)
        val id = session(store, "squat", start)
        val ended = store.training.sessionById(id)!!.ended_at
        val recorded = store.training.sessionById(id)!!.recorded_at
        store.training.finishSession(id, start.toEpochMilliseconds() + 999_000L, 1L)
        assertEquals(ended, store.training.sessionById(id)!!.ended_at)
        assertEquals(recorded, store.training.sessionById(id)!!.recorded_at)
        store.close()
    }

    private fun seedRoutine(store: LocalStore, id: String, name: String) {
        store.training.upsertRoutine(
            app.openhealth.data.db.Routine(
                id = id,
                name = name,
                folder = null,
                notes = null,
                archived_at = null,
                created_at = 0L,
                updated_at = 0L,
                source_id = "manual",
                revision = 1L,
                weekday = null,
                rotation_index = null,
                favourite = false,
                progression = "linear",
                target_rest_seconds = null,
            ),
        )
    }

    /** A programme keeps its routines week by week, and editing replaces them cleanly. */
    @Test
    fun aProgrammesWeeksAreReplacedNotAppended() {
        val store = localStore()
        seedRoutine(store, "r-push", "Push")
        seedRoutine(store, "r-pull", "Pull")
        store.training.upsertProgramme(
            app.openhealth.data.db.Programme(
                id = "p-1",
                name = "Eight weeks",
                weeks = 2L,
                notes = null,
                archived_at = null,
                created_at = 0L,
                updated_at = 0L,
                source_id = null,
                revision = 1L,
            ),
        )
        store.training.replaceProgrammeRoutines(
            "p-1",
            listOf(listOf("r-push", "r-pull"), listOf("r-pull")),
        ) { id("pr") }
        assertEquals(3, store.training.programmeRoutines("p-1").size)
        store.training.replaceProgrammeRoutines("p-1", listOf(listOf("r-push"))) { id("pr") }
        val rows = store.training.programmeRoutines("p-1")
        assertEquals(1, rows.size)
        assertEquals("r-push", rows.single().routine_id)
        assertEquals(1L, rows.single().week)
    }

    @Test
    fun deletingARoutineExerciseIsUndoableWithItsTarget() {
        val store = localStore()
        seedExercise(store.training)
        val routineId = id("routine")
        store.training.upsertRoutine(
            app.openhealth.data.db.Routine(
                id = routineId,
                name = "Legs",
                folder = null,
                notes = null,
                archived_at = null,
                created_at = 0L,
                updated_at = 0L,
                source_id = "manual",
                revision = 1L,
                weekday = 1L,
                rotation_index = null,
                favourite = false,
                progression = "linear",
                target_rest_seconds = 120L,
            ),
        )
        val entryId = id("entry")
        store.training.upsertRoutineExercise(
            app.openhealth.data.db.Routine_exercise(
                id = entryId,
                routine_id = routineId,
                exercise_id = "squat",
                position = 0L,
                target_sets = 4L,
                target_reps = 5L,
                target_reps_high = 8L,
                target_weight_kg = 100.0,
                notes = "pause",
                target_rir = 2.0,
                rest_seconds = 120L,
                progression = "linear",
                group_index = null,
            ),
        )
        val tombstone = assertNotNull(store.trash.deleteRoutineExercise(entryId))
        assertNull(store.training.routineEntryById(entryId))
        assertTrue(store.trash.undo(tombstone))
        val restored = assertNotNull(store.training.routineEntryById(entryId))
        assertEquals(4L, restored.target_sets)
        assertEquals(8L, restored.target_reps_high)
        assertEquals("pause", restored.notes)
        store.close()
    }

    @Test
    fun aLegSessionTurnsQuadsRedThenOrangeThenGreen() {
        val store = localStore()
        seedExercise(store.training)
        val start = midday(day)
        session(store, "squat", start)
        val sets = store.training.loadedSets(
            (start - 28.days).toEpochMilliseconds(),
        )
        val atLoad = app.openhealth.core.body.RecoveryMap.compute(start + 1.hours, sets)
        assertEquals(
            app.openhealth.core.formula.RecoveryStep.RECOVERING,
            atLoad.getValue(Muscle.QUADS).step,
        )
        val nextDay = start + 26.hours
        val dayTwo = app.openhealth.core.body.RecoveryMap.compute(
            nextDay,
            store.training.loadedSets((nextDay - 28.days).toEpochMilliseconds()),
        )
        assertEquals(
            app.openhealth.core.formula.RecoveryStep.EASING,
            dayTwo.getValue(Muscle.QUADS).step,
        )
        val thirdDay = start + 50.hours
        val dayThree = app.openhealth.core.body.RecoveryMap.compute(
            thirdDay,
            store.training.loadedSets((thirdDay - 28.days).toEpochMilliseconds()),
        )
        assertEquals(
            app.openhealth.core.formula.RecoveryStep.READY,
            dayThree.getValue(Muscle.QUADS).step,
        )
        store.close()
    }

    @Test
    fun anIncrementalAndAFullRebuildAgreeWithSessions() {
        val store = localStore()
        seedExercise(store.training)
        session(store, "squat", midday(day))
        session(
            store,
            "squat",
            midday(day.plusDays(1)),
        )
        val before = store.rollups.forDay(day)
        store.engine.rebuildAll()
        val after = store.rollups.forDay(day)
        assertEquals(before.map { it.type }, after.map { it.type })
        assertEquals(before.map { it.value.value }, after.map { it.value.value })
        store.close()
    }
}

private fun LocalDate.plusDays(days: Int): LocalDate = plus(DatePeriod(days = days))

private fun midday(date: LocalDate): Instant =
    LocalDateTime(date, LocalTime(12, 0)).toInstant(TEST_ZONE)
