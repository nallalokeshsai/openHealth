package app.openhealth.data

import app.openhealth.core.observation.ObservationType
import app.openhealth.core.units.UcumUnit
import app.openhealth.data.db.Note
import app.openhealth.data.repo.NoteRepository
import app.openhealth.data.repo.ObservationRepository
import app.openhealth.data.repo.TimelineRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlinx.datetime.LocalDate

class TimelineRepositoryTest {
    @Test
    fun thePageIsNewestFirstAcrossEveryTable() {
        val store = inMemoryStore()
        val date = LocalDate(2026, 3, 1)
        val repository = ObservationRepository(store.db)
        repository.record(
            testObservation(
                "early",
                ObservationType.WEIGHT,
                80.0,
                UcumUnit.KILOGRAM,
                dayStart(date).plus(7.hours),
            ),
        )
        repository.record(
            testObservation(
                "late",
                ObservationType.HEART_RATE,
                60.0,
                UcumUnit.PER_MINUTE,
                dayStart(date).plus(20.hours),
            ),
        )
        app.openhealth.data.repo.NutritionRepository(store.db).upsertMeal(
            app.openhealth.data.repo.MealDraft(
                id = "meal-1",
                name = "Lunch",
                kind = app.openhealth.data.repo.MealKind.LUNCH,
                localDate = date,
                zoneId = TEST_ZONE,
                happenedAt = dayStart(date).plus(22.hours),
                recordedAt = dayStart(date).plus(22.hours),
                sourceId = "manual",
                idempotencyKey = "meal-1",
            ),
        )
        NoteRepository(store.db).insert(
            Note(
                id = "note-1",
                body = "Knee felt fine today",
                local_date = date.toString(),
                zone_id = TEST_ZONE.id,
                happened_at = dayStart(date).plus(23.hours).toEpochMilliseconds(),
                recorded_at = dayStart(date).plus(23.hours).toEpochMilliseconds(),
                edited_at = null,
                source_id = "manual",
                raw_record_id = null,
                idempotency_key = "timeline-note-1",
                device = "manual",
                app_version = "test",
                revision = 1L,
            ),
        )
        val timeline = TimelineRepository(store.db, store.driver)
        val page = timeline.page(10)
        assertEquals(4, page.size)
        assertEquals("note-1", page.first().entityId)
        assertEquals("note", page.first().kind)
        assertTrue(page.first().happenedAt > page.last().happenedAt)
        assertEquals("meal", page[1].kind)
        assertEquals("observation", page[2].kind)
        assertEquals(date, page.first().localDate)
        assertEquals(4, timeline.forDay(date).size)
        assertTrue(timeline.forDay(date).any { it.kind == "meal" })
        assertTrue(timeline.forDay(date).any { it.kind == "note" })
        assertEquals(4, timeline.count())
        assertEquals(1, timeline.page(1).size)
        store.close()
    }
}
