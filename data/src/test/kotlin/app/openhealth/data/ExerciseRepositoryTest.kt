package app.openhealth.data

import app.openhealth.core.body.Muscle
import app.openhealth.core.body.RecoveryMap
import app.openhealth.core.exercise.Equipment
import app.openhealth.core.exercise.Exercise as CatalogueExercise
import app.openhealth.core.exercise.ExerciseCatalogue
import app.openhealth.core.exercise.ExerciseLevel
import app.openhealth.core.formula.RecoveryStep
import app.openhealth.data.db.Work_set
import app.openhealth.data.repo.TrainingRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

class ExerciseRepositoryTest {
    private val store = inMemoryStore()
    private val training = TrainingRepository(store.db)
    private val now = Instant.parse("2026-11-24T18:00:00Z")

    @Test
    fun theBundledLibraryIsWrittenOnceAndIsIdempotent() {
        val first = training.seedCatalogue(
            exercises = ExerciseCatalogue.bundled,
            sourceId = "manual",
            nowMillis = now.toEpochMilliseconds(),
        )
        assertEquals(ExerciseCatalogue.bundled.size, first)
        assertEquals(first.toLong(), training.countExercises())

        // A second run (a bumped library version) is an upsert, never a duplicate.
        training.seedCatalogue(ExerciseCatalogue.bundled, "manual", now.toEpochMilliseconds())
        assertEquals(first.toLong(), training.countExercises())
        assertTrue(first >= 150)
    }

    @Test
    fun aSeededExerciseComesBackWithItsTypedFacts() {
        training.seedCatalogue(ExerciseCatalogue.bundled, "manual", now.toEpochMilliseconds())
        val catalogue = training.catalogue()
        assertEquals(ExerciseCatalogue.bundled.size, catalogue.size)
        val withMuscles = catalogue.first { it.primary.isNotEmpty() }
        assertTrue(withMuscles.instructions.isNotEmpty())
        assertTrue(withMuscles.level in ExerciseLevel.entries)
        val stored = training.exerciseById(withMuscles.id)
        assertEquals(withMuscles.primary.joinToString(",") { it.key }, stored?.primary_muscles)
        assertEquals(withMuscles.equipment.key, stored?.equipment)
        assertEquals(withMuscles.level.key, stored?.level)
    }

    @Test
    fun aCustomExerciseIsMarkedCustom() {
        val custom = CatalogueExercise(
            id = "custom-press",
            name = "Garage press",
            primary = listOf(Muscle.CHEST),
            secondary = listOf(Muscle.TRICEPS),
            equipment = Equipment.BARBELL,
            level = ExerciseLevel.INTERMEDIATE,
            mechanic = null,
            force = null,
            bodyPart = app.openhealth.core.body.BodyPart.CHEST,
            instructions = listOf("Press the bar."),
            isCustom = true,
        )
        training.insertCatalogueExercise(custom, sourceId = "manual", nowMillis = 0L)
        val row = training.exerciseById("custom-press")
        assertEquals(true, row?.is_custom)
        assertEquals("chest", row?.primary_muscles)
        assertEquals(custom.id, training.catalogue().single().id)
    }

    @Test
    fun loadedSetsCarryTheExercisesMusclesAndSession() {
        val date = LocalDate(2026, 11, 24)
        seedSessionAndExercise(store, "s-1", "e-1", now - 2.hours, date)
        training.upsertSet(
            Work_set(
                id = "set-1",
                session_id = "s-1",
                session_exercise_id = null,
                exercise_id = "e-1",
                position = 0,
                weight_kg = 100.0,
                reps = 5,
                rir = null,
                rpe = null,
                is_warmup = false,
                is_backoff = false,
                is_failure = false,
                is_bodyweight = false,
                tempo = null,
                rest_seconds = null,
                notes = null,
                completed_at = (now - 2.hours).toEpochMilliseconds(),
                happened_at = (now - 2.hours).toEpochMilliseconds(),
                recorded_at = (now - 2.hours).toEpochMilliseconds(),
                source_id = "manual",
                idempotency_key = "k-set-1",
                observation_id = null,
                raw_record_id = null,
                device = null,
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
        val loads = training.loadedSets(now.minus(4.hours).toEpochMilliseconds())
        assertEquals(1, loads.size)
        assertEquals("s-1", loads.single().sessionId)
        assertEquals(listOf(Muscle.QUADS), loads.single().primary)
        assertEquals(listOf(Muscle.GLUTES), loads.single().secondary)
        assertEquals(100.0, loads.single().weightKg)
    }

    @Test
    fun theRecoveryMapIsComputedFromTheStoredSets() {
        val date = LocalDate(2026, 11, 24)
        seedSessionAndExercise(store, "s-1", "e-1", now - 2.hours, date)
        training.upsertSet(
            Work_set(
                id = "set-1",
                session_id = "s-1",
                session_exercise_id = null,
                exercise_id = "e-1",
                position = 0,
                weight_kg = 100.0,
                reps = 5,
                rir = null,
                rpe = null,
                is_warmup = false,
                is_backoff = false,
                is_failure = false,
                is_bodyweight = false,
                tempo = null,
                rest_seconds = null,
                notes = null,
                completed_at = (now - 2.hours).toEpochMilliseconds(),
                happened_at = (now - 2.hours).toEpochMilliseconds(),
                recorded_at = (now - 2.hours).toEpochMilliseconds(),
                source_id = "manual",
                idempotency_key = "k-set-1",
                observation_id = null,
                raw_record_id = null,
                device = null,
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
        val states = RecoveryMap.compute(now, training.loadedSets(0L))
        assertEquals(RecoveryStep.RECOVERING, states.getValue(Muscle.QUADS).step)
        assertEquals(RecoveryStep.RECOVERING, states.getValue(Muscle.GLUTES).step)
        assertEquals(RecoveryStep.UNTRAINED, states.getValue(Muscle.CHEST).step)
        assertNull(states.getValue(Muscle.CHEST).hoursSinceLoad)
    }
}
