package app.openhealth.data

import app.openhealth.data.db.Measurement
import app.openhealth.data.repo.BodyRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate

/** The tape read the adipose model depends on: newest of its type, and never an excluded one. */
class BodyRepositoryTest {
    private val store = inMemoryStore()
    private val body = BodyRepository(store.db)
    private val day = dayStart(LocalDate(2026, 11, 24)).toEpochMilliseconds()

    private fun measurement(id: String, value: Double, at: Long, excluded: Boolean = false) =
        Measurement(
            id = id,
            type = "waist",
            value_ = value,
            unit = "cm",
            local_date = "2026-11-24",
            zone_id = TEST_ZONE.id,
            happened_at = at,
            recorded_at = at,
            source_id = "manual",
            method = "tape",
            notes = null,
            excluded_from_trends = excluded,
            observation_id = null,
            raw_record_id = null,
            idempotency_key = id,
            device = null,
            app_version = "test",
            revision = 1L,
        )

    @Test
    fun theNewestReadingOfATapeSiteWins() {
        body.upsertMeasurement(measurement("old", 88.0, day + 1_000L))
        body.upsertMeasurement(measurement("new", 86.0, day + 2_000L))
        assertEquals(86.0, body.latestMeasurementOfType("waist")?.value_)
    }

    @Test
    fun anExcludedReadingNeverDrivesTheBodyModel() {
        body.upsertMeasurement(measurement("excluded", 120.0, day + 2_000L, excluded = true))
        assertNull(body.latestMeasurementOfType("waist"))
        body.upsertMeasurement(measurement("kept", 88.0, day + 1_000L))
        assertEquals(88.0, body.latestMeasurementOfType("waist")?.value_)
    }
}
