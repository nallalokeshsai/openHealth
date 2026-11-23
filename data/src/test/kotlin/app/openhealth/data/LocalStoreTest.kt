package app.openhealth.data

import app.openhealth.core.observation.ObservationType
import app.openhealth.core.units.UcumUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.hours
import kotlinx.datetime.LocalDate

class LocalStoreTest {
    @Test
    fun oneWritePathRebuildsTheDayWithoutManualWiring() {
        val raw = inMemoryStore()
        val store = LocalStore(raw.driver, raw.db)
        val date = LocalDate(2026, 3, 1)
        store.observations.record(
            testObservation(
                "local-store-weight",
                ObservationType.WEIGHT,
                80.0,
                UcumUnit.KILOGRAM,
                dayStart(date).plus(7.hours),
            ),
        )
        val rollup = store.rollups.forDayAndType(date, ObservationType.WEIGHT)
        assertNotNull(rollup)
        assertEquals(80.0, rollup.value.value)
        store.close()
    }

    @Test
    fun aSourcePriorityChangeRefreshesTheRegistryAndRebuilds() {
        val raw = inMemoryStore()
        val store = LocalStore(raw.driver, raw.db)
        val date = LocalDate(2026, 3, 1)
        store.sources.upsert(
            app.openhealth.core.source.Source(
                app.openhealth.core.ids.SourceId("garmin"),
                app.openhealth.core.observation.SourceKind.GARMIN,
                "Garmin",
            ),
        )
        store.observations.recordAll(hourlySteps("g1", date, "garmin", 100.0))
        assertEquals(
            2_400.0,
            store.rollups.forDayAndType(date, ObservationType.STEPS)!!.value.value,
        )
        store.sources.upsert(
            app.openhealth.core.source.Source(
                app.openhealth.core.ids.SourceId("garmin"),
                app.openhealth.core.observation.SourceKind.GARMIN,
                "Garmin",
                priorities = mapOf(ObservationType.STEPS to 99),
            ),
        )
        assertEquals(
            2_400.0,
            store.rollups.forDayAndType(date, ObservationType.STEPS)!!.value.value,
        )
        store.close()
    }
}
