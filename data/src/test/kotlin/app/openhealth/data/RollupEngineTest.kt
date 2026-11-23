package app.openhealth.data

import app.openhealth.core.ids.SourceId
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.source.Source
import app.openhealth.core.units.UcumUnit
import app.openhealth.data.repo.ObservationRepository
import app.openhealth.data.repo.RecordOutcome
import app.openhealth.data.repo.RollupRepository
import app.openhealth.data.repo.SourceRepository
import app.openhealth.data.rollup.RollupEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

private val D1 = LocalDate(2026, 3, 1)
private val D2 = LocalDate(2026, 3, 2)

class RollupEngineTest {
    private fun storeAndEngine(): Pair<app.openhealth.data.db.OpenHealthStore, RollupEngine> {
        val store = inMemoryStore()
        val sources = SourceRepository(store.db)
        sources.upsertAll(
            listOf(
                Source(
                    SourceId("garmin"),
                    app.openhealth.core.observation.SourceKind.GARMIN,
                    "Garmin",
                ),
                Source(
                    SourceId("fitbit"),
                    app.openhealth.core.observation.SourceKind.FITBIT,
                    "Fitbit",
                ),
                Source(
                    SourceId("manual"),
                    app.openhealth.core.observation.SourceKind.MANUAL,
                    "Manual",
                ),
            ),
        )
        val observations = ObservationRepository(store.db)
        val rollups = RollupRepository(store.db)
        val engine = RollupEngine(observations, rollups, store.db, sources.registry())
        return store to engine
    }

    @Test
    fun aDayIsBuiltFromItsObservations() {
        val (store, engine) = storeAndEngine()
        ObservationRepository(store.db).recordAll(hourlySteps("a", D1, "garmin", 100.0))
        assertEquals(1, engine.rebuildDay(D1))
        val rollup = RollupRepository(store.db).forDayAndType(D1, ObservationType.STEPS)
        assertNotNull(rollup)
        assertEquals(2_400.0, rollup.value.value, 1e-9)
        assertNull(rollup.secondaryValue)
        assertEquals("garmin", rollup.sourceId?.value)
        store.close()
    }

    @Test
    fun dedupTakesOneSourceAndNeverTheSum() {
        val (store, engine) = storeAndEngine()
        val observations = hourlySteps("g", D1, "garmin", 100.0) +
            hourlySteps("f", D1, "fitbit", 300.0)
        ObservationRepository(store.db).recordAll(observations)
        engine.rebuildDay(D1)
        val rollup = RollupRepository(store.db).forDayAndType(D1, ObservationType.STEPS)!!
        assertEquals(2_400.0, rollup.value.value, 1e-9)
        assertTrue(rollup.value.value != 9_600.0)
        assertEquals(1, rollup.contributingSources)
        store.close()
    }

    @Test
    fun incrementalAndFullRebuildsAgree() {
        val (store, engine) = storeAndEngine()
        val repository = ObservationRepository(store.db)
        repository.recordAll(
            hourlySteps("g1", D1, "garmin", 80.0) + hourlySteps("f1", D1, "fitbit", 120.0) +
                hourlySteps("g2", D2, "garmin", 90.0) +
                listOf(
                    testObservation(
                        "w1",
                        ObservationType.WEIGHT,
                        82.5,
                        UcumUnit.KILOGRAM,
                        dayStart(D1).plus(7.hours),
                    ),
                    testObservation(
                        "w2",
                        ObservationType.WEIGHT,
                        82.1,
                        UcumUnit.KILOGRAM,
                        dayStart(D2).plus(7.hours),
                        sourceId = "manual",
                    ),
                ),
        )
        engine.rebuildDay(D1)
        engine.rebuildDay(D2)
        val incremental = RollupRepository(store.db).forRange(D1, D2)
            .map { it.copy(computedAt = Instant.fromEpochMilliseconds(0)) }

        engine.rebuildAll()
        val full = RollupRepository(store.db).forRange(D1, D2)
            .map { it.copy(computedAt = Instant.fromEpochMilliseconds(0)) }
        assertEquals(full, incremental)
        assertEquals(4, full.size)
        store.close()
    }

    @Test
    fun aBackfilledObservationKeepsItsRecordedAtAndRebuildsTheDay() {
        val (store, engine) = storeAndEngine()
        val repository = ObservationRepository(store.db)
        val now = Instant.parse("2026-03-05T12:00:00Z")
        val late = testObservation(
            id = "backfill",
            type = ObservationType.WEIGHT,
            value = 81.0,
            unit = UcumUnit.KILOGRAM,
            start = dayStart(D1).plus(7.hours),
            recordedAt = now,
        )
        val outcome = repository.record(late)
        assertTrue(outcome is RecordOutcome.Inserted)
        engine.rebuildDay(D1)
        val stored = repository.forDay(D1).single()
        assertEquals(late.provenance.recordedAt, stored.provenance.recordedAt)
        assertEquals(
            81.0,
            RollupRepository(store.db).forDayAndType(D1, ObservationType.WEIGHT)!!.value.value,
        )
        store.close()
    }

    @Test
    fun aRetriedIdempotencyKeyInsertsNothing() {
        val (store, _) = storeAndEngine()
        val repository = ObservationRepository(store.db)
        val observation = testObservation(
            "same-key",
            ObservationType.WEIGHT,
            80.0,
            UcumUnit.KILOGRAM,
            dayStart(D1).plus(7.hours),
        )
        assertTrue(repository.record(observation) is RecordOutcome.Inserted)
        val second = repository.record(observation)
        assertTrue(second is RecordOutcome.Duplicate)
        assertEquals(1, repository.forDay(D1).size)
        store.close()
    }

    @Test
    fun aWeightDayKeepsOneValueAndPrefersManual() {
        val (store, engine) = storeAndEngine()
        ObservationRepository(store.db).recordAll(
            listOf(
                testObservation(
                    "d1",
                    ObservationType.WEIGHT,
                    83.0,
                    UcumUnit.KILOGRAM,
                    dayStart(D1).plus(6.hours),
                ),
                testObservation(
                    "m1",
                    ObservationType.WEIGHT,
                    82.4,
                    UcumUnit.KILOGRAM,
                    dayStart(D1).plus(8.hours),
                    sourceId = "manual",
                ),
            ),
        )
        engine.rebuildDay(D1)
        val rollup = RollupRepository(store.db).forDayAndType(D1, ObservationType.WEIGHT)!!
        assertEquals(82.4, rollup.value.value)
        assertEquals("manual", rollup.sourceId?.value)
        assertEquals("MANUAL_OVERRIDE", rollup.dedupReason)
        store.close()
    }

    @Test
    fun excludedReadingsDoNotReachTheRollup() {
        val (store, engine) = storeAndEngine()
        val repository = ObservationRepository(store.db)
        val observation = testObservation(
            "mood",
            ObservationType.MOOD,
            3.0,
            UcumUnit.SCORE,
            dayStart(D1).plus(9.hours),
            sourceId = "manual",
        )
        repository.record(observation)
        repository.excludeFromTrends(observation.id, true)
        assertEquals(0, engine.rebuildDay(D1))
        store.close()
    }

    @Test
    fun theWatermarkIsTheNewestObservationUsed() {
        val (store, engine) = storeAndEngine()
        val first = testObservation(
            "w-a",
            ObservationType.WEIGHT,
            80.0,
            UcumUnit.KILOGRAM,
            dayStart(D1).plus(7.hours),
            recordedAt = dayStart(D1).plus(7.hours),
        )
        val second = testObservation(
            "w-b",
            ObservationType.WEIGHT,
            79.8,
            UcumUnit.KILOGRAM,
            dayStart(D1).plus(9.hours),
            recordedAt = dayStart(D1).plus(9.hours),
        )
        ObservationRepository(store.db).recordAll(listOf(first, second))
        engine.rebuildDay(D1)
        val rollup = RollupRepository(store.db).forDayAndType(D1, ObservationType.WEIGHT)!!
        assertEquals(second.provenance.recordedAt, rollup.watermark)
        store.close()
    }
}
