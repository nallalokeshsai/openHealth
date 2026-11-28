package app.openhealth.data

import app.openhealth.core.observation.ObservationType
import app.openhealth.core.units.UcumUnit
import app.openhealth.data.LocalStore
import app.openhealth.data.corpus.CorpusGenerator
import app.openhealth.data.corpus.CorpusRequest
import app.openhealth.data.repo.DemoDataPurger
import app.openhealth.data.repo.ObservationRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlinx.datetime.LocalDate

class DemoDataPurgerTest {
    @Test
    fun theCorpusLeavesAndThePersonsOwnRowsStay() {
        val store = inMemoryStore()
        val local = LocalStore(store.driver, store.db)
        val date = LocalDate(2026, 5, 10)
        val generator = CorpusGenerator(store.db)
        val stats = generator.generate(
            CorpusRequest(days = 7, seed = 11L, endDate = date, zone = TEST_ZONE),
        )
        assertTrue(stats.observations > 0)

        // A row the person logs after the demo is not tagged and must survive.
        ObservationRepository(store.db).record(
            testObservation(
                "mine",
                ObservationType.WEIGHT,
                70.0,
                UcumUnit.KILOGRAM,
                dayStart(date).plus(9.hours),
            ),
        )

        DemoDataPurger(store.driver).purge()

        assertEquals(1L, local.observations.count())
        assertTrue(
            local.observations.byId(
                app.openhealth.core.ids.ObservationId("mine"),
            ) != null,
        )
        assertTrue(
            local.training.sessionsBetween(
                LocalDate(2026, 1, 1),
                LocalDate(2027, 1, 1),
            ).isEmpty(),
        )
        assertEquals(0, local.profile.all().size)
        assertNull(local.nutrition.foodById("food-indexed-0001"))
        store.close()
    }
}
