package app.openhealth.core.ingestion

import app.openhealth.core.ids.RawRecordId
import app.openhealth.core.ids.SourceId
import app.openhealth.core.observation.SourceKind
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Layer 1 (`pr.md` §7.1): one immutable record per import, sync, manual entry or API
 * call. The payload can be compacted after the retention window, but the hash, source,
 * timestamps and idempotency key stay forever so every derived value remains auditable.
 */
data class RawRecord(
    val id: RawRecordId,
    val sourceId: SourceId,
    val sourceKind: SourceKind,
    val receivedAt: Instant,
    val recordedAt: Instant,
    val happenedAt: Instant?,
    val payloadHash: String,
    val idempotencyKey: String,
    val schemaVersion: Int,
    val device: String? = null,
    val appVersion: String? = null,
    val payloadRef: String? = null,
    val payload: String? = null,
) {
    init {
        require(payloadHash.isNotBlank()) { "payloadHash must not be blank" }
        require(idempotencyKey.isNotBlank()) { "idempotencyKey must not be blank" }
    }

    val isCompacted: Boolean get() = payload == null && payloadRef == null
}

/**
 * Raw storage policy (`pr.md` §7.1): keep the full payload for 90 days on device, keep
 * the audit fields forever. The setting is user-editable; the default lives here.
 */
object RawRetention {
    val DEVICE_RETENTION = 90.days

    fun isDueForCompaction(
        record: RawRecord,
        now: Instant,
        retention: kotlin.time.Duration = DEVICE_RETENTION,
    ): Boolean = !record.isCompacted && record.recordedAt + retention <= now
}
