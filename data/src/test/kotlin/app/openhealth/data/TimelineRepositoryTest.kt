package app.openhealth.data

import app.openhealth.core.observation.ObservationType
import app.openhealth.core.units.UcumUnit
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
            app.openhealth.data.db.Meal(
                id = "meal-1",
                name = "Lunch",
                kind = "lunch",
                local_date = date.toString(),
                notes = null,
                created_at = dayStart(date).plus(22.hours).toEpochMilliseconds(),
                updated_at = dayStart(date).plus(22.hours).toEpochMilliseconds(),
                revision = 1L,
            ),
        )
        val timeline = TimelineRepository(store.db, store.driver)
        val page = timeline.page(10)
        assertEquals(3, page.size)
        assertEquals("meal-1", page.first().entityId)
        assertEquals("meal", page.first().kind)
        assertTrue(page.first().happenedAt > page.last().happenedAt)
        assertEquals("observation", page[1].kind)
        assertEquals(date, page.first().localDate)
        assertEquals(3, timeline.forDay(date).size)
        assertTrue(timeline.forDay(date).any { it.kind == "meal" })
        assertEquals(3, timeline.count())
        assertEquals(1, timeline.page(1).size)
        store.close()
    }
}
