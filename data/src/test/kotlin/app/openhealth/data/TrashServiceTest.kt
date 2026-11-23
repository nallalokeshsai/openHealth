package app.openhealth.data

import app.openhealth.core.ids.EntryId
import app.openhealth.core.ids.MeasurementId
import app.openhealth.core.ids.SetId
import app.openhealth.core.seam.FixedClock
import app.openhealth.data.db.Entry
import app.openhealth.data.db.Measurement
import app.openhealth.data.db.Work_set
import app.openhealth.data.repo.NutritionRepository
import app.openhealth.data.repo.TrashService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

class TrashServiceTest {
    private val now = Instant.parse("2026-03-01T12:00:00Z")
    private val date = LocalDate(2026, 3, 1)

    private fun measurement(db: app.openhealth.data.db.OpenHealthDb, id: String = "m-1") {
        app.openhealth.data.repo.BodyRepository(db).upsertMeasurement(
            Measurement(
                id = id,
                type = "waist",
                value_ = 84.2,
                unit = "cm",
                local_date = date.toString(),
                zone_id = TEST_ZONE.id,
                happened_at = now.toEpochMilliseconds(),
                recorded_at = now.toEpochMilliseconds(),
                source_id = "manual",
                method = "MANUAL_ENTRY",
                notes = null,
                excluded_from_trends = false,
                observation_id = null,
                raw_record_id = null,
                idempotency_key = "trash-measurement-$id",
                device = "manual",
                app_version = "test",
                revision = 1L,
            ),
        )
    }

    @Test
    fun deletingWritesATombstoneAndUndoRestoresTheRow() {
        val store = inMemoryStore()
        val clock = FixedClock(now)
        val trash = TrashService(store.db, clock)
        measurement(store.db)
        val tombstone = trash.deleteMeasurement(MeasurementId("m-1"))!!
        assertNull(store.db.bodyQueries.measurementById("m-1").executeAsOneOrNull())
        assertEquals(1, trash.active().size)
        assertTrue(trash.undo(tombstone))
        assertEquals(
            84.2,
            store.db.bodyQueries.measurementById("m-1").executeAsOneOrNull()!!.value_,
        )
        assertTrue(trash.active().isEmpty())
        store.close()
    }

    @Test
    fun trashKeepsARowForThirtyDaysThenPurgeRemovesTheTombstone() {
        val store = inMemoryStore()
        val clock = FixedClock(now)
        val trash = TrashService(store.db, clock)
        measurement(store.db)
        val tombstone = trash.deleteMeasurement(MeasurementId("m-1"))!!
        clock.set(now + 29.days)
        assertEquals(0, trash.purgeExpired())
        assertTrue(trash.active().isNotEmpty())
        assertTrue(trash.undo(tombstone))
        store.close()
    }

    @Test
    fun afterTheWindowTheRowIsGoneForGood() {
        val store = inMemoryStore()
        val clock = FixedClock(now)
        val trash = TrashService(store.db, clock)
        measurement(store.db)
        val tombstone = trash.deleteMeasurement(MeasurementId("m-1"))!!
        clock.set(now + 31.days)
        assertEquals(1, trash.purgeExpired())
        assertTrue(trash.active().isEmpty())
        assertTrue(!trash.undo(tombstone))
        val marker = trash.byId(tombstone)!!
        assertNull(marker.payload)
        assertEquals("measurement", marker.entity_table)
        store.close()
    }

    @Test
    fun aDeletedEntryComesBackExactly() {
        val store = inMemoryStore()
        val trash = TrashService(store.db, FixedClock(now))
        seedFood(store, "food-oats")
        NutritionRepository(store.db).upsertEntry(
            Entry(
                id = "e-1",
                meal_id = null,
                food_id = "food-oats",
                local_date = date.toString(),
                zone_id = TEST_ZONE.id,
                happened_at = now.toEpochMilliseconds(),
                recorded_at = now.toEpochMilliseconds(),
                quantity_g = 80.0,
                energy_kcal = 303.2,
                protein_g = 10.4,
                carbs_g = 55.0,
                fat_g = 5.2,
                fibre_g = 8.1,
                source_kind = "manual",
                source_id = "manual",
                user_verified = true,
                user_edited = false,
                observation_id = null,
                raw_record_id = null,
                idempotency_key = "trash-entry-e-1",
                device = "manual",
                app_version = "test",
                revision = 1L,
            ),
        )
        val tombstone = trash.deleteEntry(EntryId("e-1"))!!
        assertNull(NutritionRepository(store.db).entryById("e-1"))
        assertTrue(trash.undo(tombstone))
        val restored = NutritionRepository(store.db).entryById("e-1")!!
        assertEquals(303.2, restored.energy_kcal)
        assertEquals(80.0, restored.quantity_g)
        assertEquals(8.1, restored.fibre_g)
        store.close()
    }

    @Test
    fun aDeletedSetComesBackWithItsFlagsAndPosition() {
        val store = inMemoryStore()
        val trash = TrashService(store.db, FixedClock(now))
        seedSessionAndExercise(store, "session-1", "exercise-1", now, date)
        val set = Work_set(
            id = "s-1",
            session_id = "session-1",
            session_exercise_id = null,
            exercise_id = "exercise-1",
            position = 4L,
            weight_kg = 102.5,
            reps = 5L,
            rir = 2.0,
            rpe = null,
            is_warmup = false,
            is_backoff = true,
            is_failure = false,
            is_bodyweight = false,
            tempo = "3-1-1",
            rest_seconds = 180L,
            completed_at = now.toEpochMilliseconds(),
            happened_at = now.toEpochMilliseconds(),
            recorded_at = now.toEpochMilliseconds(),
            source_id = "manual",
            idempotency_key = "trash-set-s-1",
            observation_id = null,
            raw_record_id = null,
            device = "manual",
            app_version = "test",
            revision = 2L,
        )
        app.openhealth.data.repo.TrainingRepository(store.db).upsertSet(set)
        val tombstone = trash.deleteWorkSet(SetId("s-1"))!!
        assertNull(store.db.trainingQueries.workSetById("s-1").executeAsOneOrNull())
        assertTrue(trash.undo(tombstone))
        val restored = store.db.trainingQueries.workSetById("s-1").executeAsOneOrNull()!!
        assertEquals(102.5, restored.weight_kg)
        assertTrue(restored.is_backoff)
        assertEquals(180L, restored.rest_seconds)
        assertEquals(4L, restored.position)
        assertEquals(2L, restored.revision)
        store.close()
    }
}
