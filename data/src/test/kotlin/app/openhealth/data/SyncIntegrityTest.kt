package app.openhealth.data

import app.openhealth.core.ids.MeasurementId
import app.openhealth.core.ids.RawRecordId
import app.openhealth.core.ids.SourceId
import app.openhealth.core.ingestion.RawRecord
import app.openhealth.core.merge.SyncMergePolicyRegistry
import app.openhealth.core.observation.SourceKind
import app.openhealth.data.db.Measurement
import app.openhealth.data.sync.SyncTables
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** The B13 apply-edge cases that a well-formed happy path does not reach. */
class SyncIntegrityTest {
    private fun store(): LocalStore = inMemoryStore().let { LocalStore(it.driver, it.db) }

    private fun measurement(id: String, value: Double, revision: Long = 1) = Measurement(
        id = id,
        type = "weight",
        value_ = value,
        unit = "kg",
        local_date = "2026-11-26",
        zone_id = TEST_ZONE.id,
        happened_at = dayStart(kotlinx.datetime.LocalDate(2026, 11, 26)).toEpochMilliseconds(),
        recorded_at = dayStart(kotlinx.datetime.LocalDate(2026, 11, 26)).toEpochMilliseconds(),
        source_id = "manual",
        method = null,
        notes = null,
        excluded_from_trends = false,
        observation_id = null,
        raw_record_id = null,
        idempotency_key = id,
        device = null,
        app_version = null,
        revision = revision,
    )

    @Test
    fun everySyncableTableHasAMergePolicy() {
        val registry = SyncMergePolicyRegistry.default()
        val tables = SyncTables.all.map { it.entity }.toSet()
        val policies = registry.entities().toSet() - registry.neverSynced().toSet()
        assertEquals(tables, policies, "SyncTables and SyncMergePolicyRegistry drifted")
    }

    @Test
    fun aMeasurementAndItsProvenanceCrossIntact() {
        val a = store()
        val b = store()
        a.body.upsertMeasurement(measurement("m1", 80.0))
        val outcome = b.sync.applyRemote(a.sync.pendingChanges(10))
        assertTrue(outcome.refused.isEmpty())
        val restored = b.body.measurementById("m1")
        assertNotNull(restored)
        assertEquals(80.0, restored.value_)
        assertEquals("manual", restored.source_id)
    }

    @Test
    fun aRawRecordReferenceNeverBreaksTheReceiver() {
        val a = store()
        val b = store()
        val raw = RawRecord(
            id = RawRecordId("raw-1"),
            sourceId = SourceId("garmin"),
            sourceKind = SourceKind.GARMIN,
            receivedAt = Instant.parse("2026-11-26T12:00:00Z"),
            recordedAt = Instant.parse("2026-11-26T12:00:00Z"),
            happenedAt = null,
            payloadHash = "hash-1",
            idempotencyKey = "raw-1",
            schemaVersion = 12,
        )
        a.raw.insert(raw)
        a.body.upsertMeasurement(measurement("m-raw", 79.0).copy(raw_record_id = "raw-1"))
        val outcome = b.sync.applyRemote(a.sync.pendingChanges(10))
        assertTrue(outcome.refused.isEmpty())
        val restored = b.body.measurementById("m-raw")
        assertNotNull(restored)
        assertNull(restored.raw_record_id, "the receiver has no raw payload to reference")
    }

    @Test
    fun aTombstoneIsNotResurrectedByAnotherDevicesEdit() {
        val a = store()
        val b = store()
        a.body.upsertMeasurement(measurement("m-tomb", 80.0))
        b.sync.applyRemote(a.sync.pendingChanges(10))
        assertNotNull(b.body.measurementById("m-tomb"))

        b.body.upsertMeasurement(measurement("m-tomb", 81.0, revision = 2L))
        val edit = b.sync.pendingChanges(10)
        a.trash.deleteMeasurement(MeasurementId("m-tomb"))
        val outcome = a.sync.applyRemote(edit)

        assertEquals(1, outcome.conflicts.size)
        assertNull(a.body.measurementById("m-tomb"), "a tombstone must not be resurrected")
        assertEquals(1, a.sync.openConflictCount())
    }

    @Test
    fun aRecordMissingProvenanceIsRefusedAndNotMarkedSeen() {
        val b = store()
        val change = app.openhealth.core.sync.ChangeRecord(
            entity = "measurement",
            entityId = "m-bad",
            operation = app.openhealth.core.sync.ChangeOperation.UPSERT,
            fields = mapOf("id" to "m-bad", "value" to "80"),
            idempotencyKey = "bad-key",
            hlc = "0000000000000001-00000000",
            deviceId = "device-a",
            schemaVersion = 12,
            recordedAt = 1,
        )
        val outcome = b.sync.applyRemote(listOf(change))
        assertEquals(1, outcome.refused.size)
        assertNull(b.db.syncQueries.changeByKey("bad-key").executeAsOneOrNull())
    }

    @Test
    fun backfillUploadsTheRecordThatPredatesSchemaV11() {
        val store = store()
        store.sync.pauseCapture()
        store.profile.upsert(
            app.openhealth.data.db.Profile(
                id = "profile",
                name = "Sam",
                date_of_birth = null,
                sex_at_birth = null,
                gender = null,
                height_cm = null,
                weight_kg = null,
                units = "metric",
                zone_id = TEST_ZONE.id,
                language = "en",
                goal = null,
                activity_level = null,
                training_experience = null,
                weight_neutral = false,
                intake_tier = 1L,
                created_at = 0,
                updated_at = 0,
                source_id = "manual",
                revision = 1L,
            ),
        )
        store.sync.resumeCapture()
        assertEquals(0, store.sync.pendingCount())
        store.sync.backfillIfNeeded()
        assertTrue(store.sync.pendingCount() > 0)
        val afterFirst = store.sync.pendingCount()
        store.sync.backfillIfNeeded()
        assertEquals(afterFirst, store.sync.pendingCount(), "the marker makes the sweep once")
    }
}
