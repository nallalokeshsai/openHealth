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
        val timeline = TimelineRepository(store.db, store.driver)
        val page = timeline.page(10)
        assertEquals(2, page.size)
        assertEquals("late", page.first().entityId)
        assertTrue(page.first().happenedAt > page.last().happenedAt)
        assertEquals("observation", page.first().kind)
        assertEquals(date, page.first().localDate)
        assertEquals(2, timeline.forDay(date).size)
        assertEquals(2, timeline.count())
        assertEquals(1, timeline.page(1).size)
        store.close()
    }
}
