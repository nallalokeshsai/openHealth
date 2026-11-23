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
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

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
    fun shuffledIncrementalRebuildsMatchAFullRebuild() {
        val (store, engine) = storeAndEngine()
        val repository = ObservationRepository(store.db)
        val random = kotlin.random.Random(99)
        val types = listOf(
            ObservationType.WEIGHT,
            ObservationType.HEART_RATE,
            ObservationType.STEPS,
            ObservationType.NUTRITION_ENERGY,
            ObservationType.BLOOD_GLUCOSE,
        )
        val observations = (0 until 60).map { index ->
            val date = D1.plus(kotlinx.datetime.DatePeriod(days = random.nextInt(0, 10)))
            val type = types[random.nextInt(types.size)]
            val start = dayStart(date).plus(random.nextInt(0, 22).hours)
            testObservation(
                id = "random-$index",
                type = type,
                value = when (type) {
                    ObservationType.STEPS -> random.nextInt(0, 12_000).toDouble()
                    ObservationType.HEART_RATE -> random.nextInt(45, 160).toDouble()
                    ObservationType.BLOOD_GLUCOSE -> 3.9 + random.nextDouble(0.0, 6.0)
                    ObservationType.NUTRITION_ENERGY -> random.nextInt(100, 1_200).toDouble()
                    else -> 70.0 + random.nextDouble(0.0, 20.0)
                },
                unit = type.canonicalUnit,
                start = start,
                sourceId = if (random.nextBoolean()) "garmin" else "fitbit",
            )
        }
        repository.recordAll(observations)
        val days = observations.map { it.localDate }.distinct().shuffled(random)
        days.forEach { engine.rebuildDay(it) }
        val lastDay = D1.plus(DatePeriod(days = 9))
        val incremental = normalize(RollupRepository(store.db).forRange(D1, lastDay))
        engine.rebuildAll()
        val full = normalize(RollupRepository(store.db).forRange(D1, lastDay))
        assertEquals(full, incremental)
        assertTrue(full.isNotEmpty())
        store.close()
    }

    private fun normalize(
        rollups: List<app.openhealth.core.rollup.DailyRollup>,
    ): List<app.openhealth.core.rollup.DailyRollup> =
        rollups.map { it.copy(computedAt = Instant.fromEpochMilliseconds(0)) }

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
    fun aCorrectionKeepsTheOriginalAndDoesNotMoveRecordedAt() {
        val (store, engine) = storeAndEngine()
        val repository = ObservationRepository(store.db)
        val original = testObservation(
            "correct-me",
            ObservationType.WEIGHT,
            80.0,
            UcumUnit.KILOGRAM,
            dayStart(D1).plus(7.hours),
        )
        repository.record(original)
        engine.rebuildDay(D1)
        assertTrue(repository.correct(original.id, 79.4, "scale was on a rug"))
        val corrected = repository.forDay(D1).single()
        assertEquals(79.4, corrected.value.value)
        assertEquals(80.0, corrected.editTrail.originalValue)
        assertEquals("scale was on a rug", corrected.editTrail.reason)
        assertEquals(original.provenance.recordedAt, corrected.provenance.recordedAt)
        engine.rebuildDay(D1)
        assertEquals(
            79.4,
            RollupRepository(store.db).forDayAndType(D1, ObservationType.WEIGHT)!!.value.value,
        )
        store.close()
    }

    @Test
    fun heartRateTakesThePrimarySourceSeries() {
        val (store, engine) = storeAndEngine()
        ObservationRepository(store.db).recordAll(
            listOf(
                testObservation(
                    "hr-a",
                    ObservationType.HEART_RATE,
                    60.0,
                    UcumUnit.PER_MINUTE,
                    dayStart(D1).plus(8.hours),
                    sourceId = "garmin",
                ),
                testObservation(
                    "hr-b",
                    ObservationType.HEART_RATE,
                    70.0,
                    UcumUnit.PER_MINUTE,
                    dayStart(D1).plus(9.hours),
                    sourceId = "fitbit",
                ),
            ),
        )
        engine.rebuildDay(D1)
        val rollup = RollupRepository(store.db).forDayAndType(D1, ObservationType.HEART_RATE)!!
        assertEquals(60.0, rollup.value.value, 0.001)
        assertEquals(1, rollup.contributingSources)
        assertEquals("garmin", rollup.sourceId?.value)
        assertTrue(rollup.value.value != 65.0, "heart rate must not be averaged across devices")
        store.close()
    }

    @Test
    fun recordingAnObservationRebuildsItsDayThroughTheInvalidator() {
        val (store, engine) = storeAndEngine()
        val repository = ObservationRepository(store.db)
        repository.invalidateRollupsWith { engine.rebuildDay(it) }
        repository.record(
            testObservation(
                "auto-rebuild",
                ObservationType.WEIGHT,
                80.2,
                UcumUnit.KILOGRAM,
                dayStart(D1).plus(7.hours),
            ),
        )
        assertNotNull(RollupRepository(store.db).forDayAndType(D1, ObservationType.WEIGHT))
        store.close()
    }

    @Test
    fun nutritionTotalsAreTheExactSumOfEntries() {
        val (store, engine) = storeAndEngine()
        val nutrition = app.openhealth.data.repo.NutritionRepository(store.db)
        nutrition.upsertEntry(entry("e-1", 520.0, 30.0, 60.0, 20.0, 8.0, 1))
        nutrition.upsertEntry(entry("e-2", 180.0, 12.0, 20.0, 5.0, 2.0, 2))
        engine.rebuildDay(D1)
        val rollups = RollupRepository(store.db)
        assertEquals(
            700.0,
            rollups.forDayAndType(D1, ObservationType.NUTRITION_ENERGY)!!.value.value,
            1e-9,
        )
        assertEquals(
            42.0,
            rollups.forDayAndType(D1, ObservationType.NUTRITION_PROTEIN)!!.value.value,
            1e-9,
        )
        assertEquals(
            80.0,
            rollups.forDayAndType(D1, ObservationType.NUTRITION_CARBS)!!.value.value,
            1e-9,
        )
        assertEquals(
            25.0,
            rollups.forDayAndType(D1, ObservationType.NUTRITION_FAT)!!.value.value,
            1e-9,
        )
        assertEquals(
            10.0,
            rollups.forDayAndType(D1, ObservationType.NUTRITION_FIBRE)!!.value.value,
            1e-9,
        )
        store.close()
    }

    @Test
    fun aDayWithOnlyADiaryEntryIsStillRebuilt() {
        val (store, engine) = storeAndEngine()
        val row = entry("only-entry", 100.0, 5.0, 10.0, 2.0, 1.0, 1)
            .copy(
                local_date = D2.toString(),
                happened_at = dayStart(D2).plus(12.hours).toEpochMilliseconds() + 1,
                recorded_at = dayStart(D2).plus(12.hours).toEpochMilliseconds() + 1,
            )
        app.openhealth.data.repo.NutritionRepository(store.db).upsertEntry(row)
        engine.rebuildAll()
        assertNotNull(
            RollupRepository(store.db).forDayAndType(D2, ObservationType.NUTRITION_ENERGY),
        )
        store.close()
    }

    @Test
    fun aDailyTotalIsNotSummedWithItsOwnIntervals() {
        val (store, engine) = storeAndEngine()
        val buckets = hourlySteps("daily", D1, "garmin", 100.0)
        val total = testObservation(
            "daily-total",
            ObservationType.STEPS,
            2_400.0,
            UcumUnit.STEP,
            dayStart(D1),
            sourceId = "garmin",
        )
        ObservationRepository(store.db).recordAll(buckets + total)
        engine.rebuildDay(D1)
        val rollup = RollupRepository(store.db).forDayAndType(D1, ObservationType.STEPS)!!
        assertEquals(2_400.0, rollup.value.value, 1e-9)
        store.close()
    }

    @Test
    fun aMeasurementOfAnObservationTypeReachesTheRollup() {
        val (store, engine) = storeAndEngine()
        app.openhealth.data.repo.BodyRepository(store.db).upsertMeasurement(
            app.openhealth.data.db.Measurement(
                id = "scale-weight",
                type = "WEIGHT",
                value_ = 80.4,
                unit = "kg",
                local_date = D1.toString(),
                zone_id = TEST_ZONE.id,
                happened_at = dayStart(D1).plus(7.hours).toEpochMilliseconds(),
                recorded_at = dayStart(D1).plus(7.hours).toEpochMilliseconds(),
                source_id = "manual",
                method = "SCALE",
                notes = null,
                excluded_from_trends = false,
                observation_id = null,
                raw_record_id = null,
                idempotency_key = "scale-weight",
                device = "scale",
                app_version = "test",
                revision = 1L,
            ),
        )
        engine.rebuildDay(D1)
        assertEquals(
            80.4,
            RollupRepository(store.db).forDayAndType(D1, ObservationType.WEIGHT)!!.value.value,
        )
        store.close()
    }

    @Test
    fun vitalsFromTwoSourcesRecordNoSingleWinner() {
        val (store, engine) = storeAndEngine()
        val body = app.openhealth.data.repo.BodyRepository(store.db)
        body.upsertVital(vital("v-g", "BLOOD_PRESSURE_SYSTOLIC", 120.0, "mm[Hg]", 1, "garmin"))
        body.upsertVital(vital("v-f", "BLOOD_PRESSURE_SYSTOLIC", 130.0, "mm[Hg]", 2, "fitbit"))
        engine.rebuildDay(D1)
        val rollup = RollupRepository(store.db)
            .forDayAndType(D1, ObservationType.BLOOD_PRESSURE_SYSTOLIC)!!
        assertEquals(125.0, rollup.value.value, 1e-9)
        assertEquals(2, rollup.contributingSources)
        assertNull(rollup.sourceId)
        store.close()
    }

    @Test
    fun aLastValueVitalKeepsTheLastReadingNotTheMean() {
        val (store, engine) = storeAndEngine()
        val body = app.openhealth.data.repo.BodyRepository(store.db)
        body.upsertVital(
            vital("rhr-1", "RESTING_HEART_RATE", 60.0, "/min", 1),
        )
        body.upsertVital(
            vital("rhr-2", "RESTING_HEART_RATE", 70.0, "/min", 2),
        )
        engine.rebuildDay(D1)
        assertEquals(
            70.0,
            RollupRepository(store.db)
                .forDayAndType(D1, ObservationType.RESTING_HEART_RATE)!!.value.value,
        )
        store.close()
    }

    @Test
    fun aDayWithOnlyAVitalIsStillRebuilt() {
        val (store, engine) = storeAndEngine()
        app.openhealth.data.repo.BodyRepository(store.db).upsertVital(
            vital("only-vital", "BLOOD_PRESSURE_SYSTOLIC", 122.0, "mm[Hg]", 1),
        )
        engine.rebuildAll()
        assertNotNull(
            RollupRepository(store.db)
                .forDayAndType(D1, ObservationType.BLOOD_PRESSURE_SYSTOLIC),
        )
        store.close()
    }

    @Test
    fun alayerTwoObservationWinsOverAVitalOfTheSameType() {
        val (store, engine) = storeAndEngine()
        ObservationRepository(store.db).record(
            testObservation(
                "bp-observation",
                ObservationType.BLOOD_PRESSURE_SYSTOLIC,
                118.0,
                UcumUnit.MILLIMETRE_MERCURY,
                dayStart(D1).plus(8.hours),
            ),
        )
        app.openhealth.data.repo.BodyRepository(store.db).upsertVital(
            vital("bp-vital", "BLOOD_PRESSURE_SYSTOLIC", 150.0, "mm[Hg]", 1),
        )
        engine.rebuildDay(D1)
        assertEquals(
            118.0,
            RollupRepository(store.db)
                .forDayAndType(D1, ObservationType.BLOOD_PRESSURE_SYSTOLIC)!!.value.value,
        )
        store.close()
    }

    @Test
    fun aLayerFourReplayCannotMoveRecordedAt() {
        val (store, _) = storeAndEngine()
        val body = app.openhealth.data.repo.BodyRepository(store.db)
        val first = app.openhealth.data.db.Measurement(
            id = "replay-measurement",
            type = "waist",
            value_ = 84.0,
            unit = "cm",
            local_date = D1.toString(),
            zone_id = TEST_ZONE.id,
            happened_at = dayStart(D1).plus(9.hours).toEpochMilliseconds(),
            recorded_at = dayStart(D1).plus(9.hours).toEpochMilliseconds(),
            source_id = "manual",
            method = "MANUAL_ENTRY",
            notes = null,
            excluded_from_trends = false,
            observation_id = null,
            raw_record_id = null,
            idempotency_key = "replay-measurement",
            device = "manual",
            app_version = "test",
            revision = 1L,
        )
        body.upsertMeasurement(first)
        body.upsertMeasurement(
            first.copy(
                value_ = 90.0,
                recorded_at = dayStart(D1).plus(11.hours).toEpochMilliseconds(),
            ),
        )
        val stored = store.db.bodyQueries.measurementById("replay-measurement").executeAsOne()
        assertEquals(84.0, stored.value_)
        assertEquals(first.recorded_at, stored.recorded_at)
        store.close()
    }

    @Test
    fun vitalReadingsAveragePerType() {
        val (store, engine) = storeAndEngine()
        val body = app.openhealth.data.repo.BodyRepository(store.db)
        body.upsertVital(vital("v-1", "BLOOD_PRESSURE_SYSTOLIC", 120.0, "mm[Hg]", 1))
        body.upsertVital(vital("v-2", "BLOOD_PRESSURE_SYSTOLIC", 130.0, "mm[Hg]", 2))
        engine.rebuildDay(D1)
        val rollup = RollupRepository(store.db)
            .forDayAndType(D1, ObservationType.BLOOD_PRESSURE_SYSTOLIC)!!
        assertEquals(125.0, rollup.value.value, 1e-9)
        assertEquals(2, rollup.secondaryValue?.toInt())
        store.close()
    }

    private fun entry(
        id: String,
        energy: Double,
        protein: Double,
        carbs: Double,
        fat: Double,
        fibre: Double,
        second: Long,
    ) = app.openhealth.data.db.Entry(
        id = id,
        meal_id = null,
        food_id = null,
        local_date = D1.toString(),
        zone_id = TEST_ZONE.id,
        happened_at = dayStart(D1).plus(12.hours).toEpochMilliseconds() + second,
        recorded_at = dayStart(D1).plus(12.hours).toEpochMilliseconds() + second,
        quantity_g = 100.0,
        energy_kcal = energy,
        protein_g = protein,
        carbs_g = carbs,
        fat_g = fat,
        fibre_g = fibre,
        source_kind = "manual",
        source_id = "manual",
        user_verified = true,
        user_edited = false,
        observation_id = null,
        raw_record_id = null,
        idempotency_key = "entry-$id",
        device = "manual",
        app_version = "test",
        revision = 1L,
    )

    private fun vital(
        id: String,
        type: String,
        value: Double,
        unit: String,
        second: Long,
        sourceId: String = "manual",
    ) = app.openhealth.data.db.Vital(
        id = id,
        type = type,
        value_ = value,
        unit = unit,
        local_date = D1.toString(),
        zone_id = TEST_ZONE.id,
        happened_at = dayStart(D1).plus(10.hours).toEpochMilliseconds() + second,
        recorded_at = dayStart(D1).plus(10.hours).toEpochMilliseconds() + second,
        source_id = sourceId,
        method = "CUFF",
        context = "RESTING",
        note = null,
        excluded_from_trends = false,
        observation_id = null,
        raw_record_id = null,
        idempotency_key = "vital-$id",
        device = "manual",
        app_version = "test",
        revision = 1L,
    )

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
