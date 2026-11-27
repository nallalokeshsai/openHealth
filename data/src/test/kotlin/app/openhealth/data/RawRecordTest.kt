package app.openhealth.data

import app.openhealth.core.ids.RawRecordId
import app.openhealth.core.ids.SourceId
import app.openhealth.core.ingestion.RawRecord
import app.openhealth.core.observation.SourceKind
import app.openhealth.core.seam.FixedClock
import app.openhealth.data.repo.RawRecordRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class RawRecordTest {
    @Test
    fun payloadsExpireAfterNinetyDaysButTheAuditTrailStays() {
        val store = inMemoryStore()
        val now = Instant.parse("2026-03-01T00:00:00Z")
        val old = RawRecord(
            id = RawRecordId("raw-old"),
            sourceId = SourceId("garmin"),
            sourceKind = SourceKind.GARMIN,
            receivedAt = now - 100.days,
            recordedAt = now - 100.days,
            happenedAt = null,
            payloadHash = "abc123",
            idempotencyKey = "raw-old",
            schemaVersion = 2,
            payload = "{\"steps\":1}",
        )
        val fresh = old.copy(
            id = RawRecordId("raw-fresh"),
            idempotencyKey = "raw-fresh",
            receivedAt = now - 10.days,
            recordedAt = now - 10.days,
        )
        val repository = RawRecordRepository(store.db)
        repository.insert(old)
        repository.insert(fresh)
        assertEquals(1, repository.compactExpired(now))
        val compacted = repository.byId(old.id)!!
        assertNull(compacted.payload)
        assertEquals("abc123", compacted.payloadHash)
        assertEquals("{\"steps\":1}", repository.byId(fresh.id)!!.payload)
        store.close()
    }

    /**
     * The 90-day window is a promise, not a button: nothing in the app asked for it, so
     * the payloads grew without limit. Opening the store is what enforces it
     * (`pr.md` §7.1), beside the Trash purge that already ran there.
     */
    @Test
    fun openingTheStoreCompactsExpiredPayloads() {
        val opened = inMemoryStore()
        val now = Instant.parse("2026-03-01T00:00:00Z")
        val store = LocalStore(opened.driver, opened.db, FixedClock(now))
        val old = RawRecord(
            id = RawRecordId("raw-old"),
            sourceId = SourceId("garmin"),
            sourceKind = SourceKind.GARMIN,
            receivedAt = now - 100.days,
            recordedAt = now - 100.days,
            happenedAt = null,
            payloadHash = "abc123",
            idempotencyKey = "raw-old",
            schemaVersion = 2,
            payload = "{\"steps\":1}",
        )
        store.raw.insert(old)
        store.raw.insert(
            old.copy(
                id = RawRecordId("raw-fresh"),
                idempotencyKey = "raw-fresh",
                receivedAt = now - 10.days,
                recordedAt = now - 10.days,
            ),
        )

        val report = store.runStartupMaintenance()

        assertEquals(1L, report.payloadsCompacted)
        assertNull(store.raw.byId(RawRecordId("raw-old"))!!.payload)
        assertEquals("abc123", store.raw.byId(RawRecordId("raw-old"))!!.payloadHash)
        assertEquals("{\"steps\":1}", store.raw.byId(RawRecordId("raw-fresh"))!!.payload)
        // A second open is a no-op: a compacted record is not compacted twice.
        assertEquals(0L, store.runStartupMaintenance().payloadsCompacted)
        store.close()
    }
}
