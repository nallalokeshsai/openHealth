package app.openhealth.data.repo

import app.openhealth.core.ids.RawRecordId
import app.openhealth.core.ingestion.RawRecord
import app.openhealth.core.ingestion.RawRetention
import app.openhealth.data.db.OpenHealthDb
import app.openhealth.data.mapping.toDomain
import kotlin.time.Instant

/** Layer 1 reads and the one-way compaction (`pr.md` §7.1). */
class RawRecordRepository(private val db: OpenHealthDb) {
    fun insert(raw: RawRecord): Boolean {
        val before = db.rawRecordQueries.countRawRecords().executeAsOne()
        db.rawRecordQueries.insertRawRecord(
            id = raw.id.value,
            sourceId = raw.sourceId.value,
            sourceKind = raw.sourceKind.name,
            device = raw.device,
            appVersion = raw.appVersion,
            receivedAt = raw.receivedAt.toEpochMilliseconds(),
            recordedAt = raw.recordedAt.toEpochMilliseconds(),
            happenedAt = raw.happenedAt?.toEpochMilliseconds(),
            payloadHash = raw.payloadHash,
            payloadRef = raw.payloadRef,
            payload = raw.payload,
            idempotencyKey = raw.idempotencyKey,
            schemaVersion = raw.schemaVersion.toLong(),
        )
        return db.rawRecordQueries.countRawRecords().executeAsOne() > before
    }

    /**
     * Layer 1 for a hand-entered or app-originated row (`pr.md` §7.1, §2.2): every
     * manual entry gets an immutable ingestion record so the value it produces has a
     * raw-record ref. Returns the record id to store on the layer-4 row.
     */
    @Suppress("LongParameterList")
    fun recordManual(
        rawId: String,
        sourceId: app.openhealth.core.ids.SourceId,
        sourceKind: app.openhealth.core.observation.SourceKind,
        kind: String,
        payload: String,
        happenedAt: Instant,
        recordedAt: Instant,
        appVersion: String?,
    ): String {
        insert(
            RawRecord(
                id = RawRecordId(rawId),
                sourceId = sourceId,
                sourceKind = sourceKind,
                receivedAt = recordedAt,
                recordedAt = recordedAt,
                happenedAt = happenedAt,
                payloadHash = app.openhealth.data.ingestion.PayloadHash.sha256(payload),
                idempotencyKey = "manual-raw:$kind:$rawId",
                schemaVersion = MANUAL_RAW_SCHEMA,
                device = "android",
                appVersion = appVersion,
                payload = payload,
            ),
        )
        return rawId
    }

    fun byIdempotencyKey(key: String): RawRecord? =
        db.rawRecordQueries.rawRecordByIdempotencyKey(key).executeAsOneOrNull()?.toDomain()

    fun byHash(hash: String): RawRecord? =
        db.rawRecordQueries.rawRecordByHash(hash).executeAsOneOrNull()?.toDomain()

    fun byId(id: RawRecordId): RawRecord? =
        db.rawRecordQueries.rawRecordById(id.value).executeAsOneOrNull()?.toDomain()

    /** Drops payloads past the retention window, keeping every audit field. */
    fun compactExpired(
        now: Instant,
        retention: kotlin.time.Duration = RawRetention.DEVICE_RETENTION,
    ): Long {
        val before = db.rawRecordQueries.countCompactedRecords().executeAsOne()
        db.rawRecordQueries.compactPayloadsBefore(
            compactedAt = now.toEpochMilliseconds(),
            before = (now - retention).toEpochMilliseconds(),
        )
        return db.rawRecordQueries.countCompactedRecords().executeAsOne() - before
    }

    fun count(): Long = db.rawRecordQueries.countRawRecords().executeAsOne()

    /** Payload bytes still held, for the Settings storage row (`pr.md` §7.1). */
    fun payloadBytes(): Long = db.rawRecordQueries.payloadBytes().executeAsOne().toLong()

    private companion object {
        const val MANUAL_RAW_SCHEMA = 1
    }
}
