package app.openhealth.sync

import app.openhealth.core.merge.SyncMergePolicyRegistry
import app.openhealth.core.sync.ChangeOperation
import app.openhealth.core.sync.ChangeRecord
import app.openhealth.core.sync.HybridClock
import app.openhealth.core.sync.HybridLogicalClock
import app.openhealth.core.sync.PullRequest
import app.openhealth.core.sync.PullResponse
import app.openhealth.core.sync.PushRequest
import app.openhealth.core.sync.PushResponse
import app.openhealth.core.sync.SyncConflict
import app.openhealth.core.sync.SyncSnapshot
import app.openhealth.core.sync.SyncStatus
import java.io.IOException
import java.util.UUID

private data class RowKey(val entity: String, val id: String)

private data class RowState(
    val fields: MutableMap<String, String?>,
    val clocks: MutableMap<String, String>,
    var hlc: String,
    var deleted: Boolean = false,
)

/**
 * A client store used by the property tests: the same merge registry the app uses, but
 * in memory. It is deliberately not the production implementation, so a test cannot pass
 * by accident through SQL behaviour.
 */
class InMemorySyncStore(
    private val device: String,
    private val clock: HybridClock,
    private val registry: SyncMergePolicyRegistry = SyncMergePolicyRegistry.default(),
    private val schema: Int = 11,
) : SyncLocalStore {
    private val rows = linkedMapOf<RowKey, RowState>()
    private val pending = linkedMapOf<String, ChangeRecord>()
    private val seen = mutableSetOf<String>()
    private val open = linkedMapOf<String, SyncConflict>()
    private var pullCursor = 0L
    private var lastSynced: Long? = null
    private var status = SyncStatus.LOCAL_ONLY
    private var lastError: String? = null

    override fun deviceId(): String = device

    override fun schemaVersion(): Int = schema

    override fun observeRemote(clock: HybridLogicalClock) {
        this.clock.observe(clock)
    }

    override fun pendingChanges(limit: Int): List<ChangeRecord> = pending.values.take(limit)

    override fun pendingCount(): Int = pending.size

    override fun markAcknowledged(keys: Set<String>, serverSeq: Long) {
        keys.forEach(pending::remove)
    }

    override fun cursor(): Long = pullCursor

    override fun setCursor(cursor: Long) {
        pullCursor = cursor
    }

    override fun recordStatus(status: SyncStatus, atMillis: Long, error: String?) {
        this.status = status
        this.lastError = error
        if (status == SyncStatus.SYNCED) lastSynced = atMillis
    }

    override fun conflicts(): List<SyncConflict> = open.values.toList()

    override fun openConflictCount(): Int = open.size

    override fun lastSyncedAt(): Long? = lastSynced

    fun status(): SyncStatus = status

    fun lastError(): String? = lastError

    /** A local edit. [revision] is set for revisioned documents by [editDocument]. */
    fun edit(
        entity: String,
        id: String,
        values: Map<String, String?>,
        localDate: String? = null,
        operation: ChangeOperation = ChangeOperation.UPSERT,
    ): ChangeRecord {
        val key = RowKey(entity, id)
        val existing = rows[key]
        val now = clock.next()
        val fields = existing?.fields?.toMutableMap() ?: mutableMapOf()
        val clocks = existing?.clocks?.toMutableMap() ?: mutableMapOf()
        values.forEach { (field, value) ->
            fields[field] = value
            clocks[field] = now.encode()
        }
        val record = ChangeRecord(
            entity = entity,
            entityId = id,
            operation = operation,
            fields = fields.toMap(),
            fieldClocks = clocks.toMap(),
            idempotencyKey = "$device:${UUID.randomUUID()}",
            hlc = now.encode(),
            deviceId = device,
            schemaVersion = schema,
            localDate = localDate,
            recordedAt = now.wallMillis,
        )
        rows[key] = RowState(fields, clocks, now.encode())
        seen += record.idempotencyKey
        pending[record.idempotencyKey] = record
        return record
    }

    fun editDocument(entity: String, id: String, values: Map<String, String?>): ChangeRecord {
        val existing = rows[RowKey(entity, id)]
        val nextRevision = (
            (
                existing?.fields?.get(
                    "revision",
                )?.toLongOrNull() ?: 0L
                ) + 1
            ).toString()
        return edit(entity, id, values + ("revision" to nextRevision))
    }

    fun delete(entity: String, id: String): ChangeRecord {
        val existing = rows[RowKey(entity, id)] ?: error("no row $entity/$id")
        val now = clock.next()
        val record = ChangeRecord(
            entity = entity,
            entityId = id,
            operation = ChangeOperation.DELETE,
            fields = existing.fields.toMap(),
            fieldClocks = existing.clocks.toMap(),
            idempotencyKey = "$device:${UUID.randomUUID()}",
            hlc = now.encode(),
            deviceId = device,
            schemaVersion = schema,
            recordedAt = now.wallMillis,
        )
        existing.deleted = true
        pending[record.idempotencyKey] = record
        seen += record.idempotencyKey
        return record
    }

    @Suppress("CyclomaticComplexMethod", "LoopWithTooManyJumpStatements")
    override fun applyRemote(records: List<ChangeRecord>): ApplyOutcome {
        var applied = 0
        val found = mutableListOf<SyncConflict>()
        val refused = mutableListOf<ChangeRecord>()
        for (record in records) {
            if (!seen.add(record.idempotencyKey)) continue
            val policy = registry.policyFor(record.entity)
            if (policy == null || record.schemaVersion > schema) {
                refused += record
                continue
            }
            val key = RowKey(record.entity, record.entityId)
            val local = rows[key]
            val localHlc = local?.hlc ?: HybridLogicalClock.ZERO.encode()
            if (local?.deleted == true && record.operation == ChangeOperation.UPSERT) {
                val conflict = conflict(record, localHlc, "sync.conflict.deleted_locally")
                open[conflict.id] = conflict
                found += conflict
                continue
            }
            if (local == null) {
                rows[key] = RowState(
                    record.fields.toMutableMap(),
                    record.fieldClocks.toMutableMap(),
                    record.hlc,
                    deleted = record.operation == ChangeOperation.DELETE,
                )
                applied += 1
                continue
            }
            if (record.operation == ChangeOperation.DELETE) {
                if (record.clock() > HybridLogicalClock.parse(localHlc)) {
                    local.deleted = true
                    local.hlc = record.hlc
                    clearConflicts(record.entity, record.entityId)
                    applied += 1
                } else {
                    val conflict = conflict(record, localHlc, "sync.conflict.delete_newer_local")
                    open[conflict.id] = conflict
                    found += conflict
                }
                continue
            }
            val snapshot = SyncSnapshot(local.fields.toMap(), local.clocks.toMap())
            val remote = SyncSnapshot(record.fields.toMap(), record.fieldClocks.toMap())
            val outcome = policy.merge(
                snapshot,
                remote,
                HybridLogicalClock.parse(localHlc).wallMillis * 1_000_000 +
                    HybridLogicalClock.parse(localHlc).counter,
                record.clock().wallMillis * 1_000_000 + record.clock().counter,
            )
            val conflict = outcome.conflict
            val merged = outcome.value
            when {
                conflict != null -> {
                    val foundConflict = conflict(record, localHlc, conflict.reasonKey)
                    open[foundConflict.id] = foundConflict
                    found += foundConflict
                }
                merged != null -> {
                    local.fields.clear()
                    local.fields.putAll(merged.fields)
                    local.clocks.clear()
                    local.clocks.putAll(merged.fieldClocks)
                    local.hlc = maxOf(localHlc, record.hlc)
                    clearConflicts(record.entity, record.entityId)
                    applied += 1
                }
            }
        }
        return ApplyOutcome(applied, found, refused)
    }

    private fun clearConflicts(entity: String, entityId: String) {
        open.entries.removeAll { (_, conflict) ->
            conflict.entity == entity && conflict.entityId == entityId
        }
    }

    private fun conflict(record: ChangeRecord, localHlc: String, reason: String): SyncConflict {
        val localRecord = ChangeRecord(
            entity = record.entity,
            entityId = record.entityId,
            operation = ChangeOperation.UPSERT,
            fields = rows[RowKey(record.entity, record.entityId)]?.fields?.toMap().orEmpty(),
            idempotencyKey = "local:${UUID.randomUUID()}",
            hlc = localHlc,
            deviceId = device,
            schemaVersion = schema,
            recordedAt = 0,
        )
        return SyncConflict(
            id = "${record.entity}:${record.entityId}:${record.idempotencyKey}",
            entity = record.entity,
            entityId = record.entityId,
            local = localRecord,
            remote = record,
            reason = reason,
            createdAt = clock.current().wallMillis,
        )
    }

    override fun resolveConflict(id: String, keepLocal: Boolean, atMillis: Long): ChangeRecord? {
        val conflict = open.remove(id) ?: return null
        val chosen = if (keepLocal) conflict.local else conflict.remote
        val now = clock.next()
        val fields = chosen.fields.toMutableMap()
        val revision = fields["revision"]?.toLongOrNull()
        if (revision != null) fields["revision"] = (revision + 1).toString()
        val record = ChangeRecord(
            entity = chosen.entity,
            entityId = chosen.entityId,
            operation = ChangeOperation.UPSERT,
            fields = fields,
            fieldClocks = fields.keys.associateWith { now.encode() },
            idempotencyKey = "$device:${UUID.randomUUID()}",
            hlc = now.encode(),
            deviceId = device,
            schemaVersion = schema,
            recordedAt = atMillis,
        )
        val key = RowKey(record.entity, record.entityId)
        rows[key] = RowState(fields, record.fieldClocks.toMutableMap(), now.encode())
        pending[record.idempotencyKey] = record
        seen += record.idempotencyKey
        return record
    }

    /** The current visible fields per entity/id, deleted rows included as a marker. */
    fun snapshot(): Map<String, Map<String, String?>> = rows.entries
        .sortedBy { (key, _) -> "${key.entity}|${key.id}" }
        .associate { (key, row) ->
            "${key.entity}/${key.id}" to
                if (row.deleted) mapOf("__deleted" to "true") else row.fields.toMap()
        }

    fun row(entity: String, id: String): Map<String, String?>? =
        rows[RowKey(entity, id)]?.fields?.toMap()

    fun isDeleted(entity: String, id: String): Boolean = rows[RowKey(entity, id)]?.deleted == true

    fun hasPending(): Boolean = pending.isNotEmpty()
}

/**
 * A server for tests: append-only log, first write wins per idempotency key, pull by
 * sequence cursor. [dropConnectionAfter] applies that many changes and then throws,
 * which is how a killed connection is simulated.
 */
class InMemorySyncServer : SyncTransport {
    private val log = mutableListOf<ChangeRecord>()
    private val keys = mutableSetOf<String>()

    var dropConnectionAfter: Int? = null
    var minSchemaVersion: Int = 1

    override suspend fun push(request: PushRequest): PushResponse {
        var applied = 0
        for (change in request.changes) {
            if (keys.add(change.idempotencyKey)) {
                log += change
                applied += 1
            }
            val drop = dropConnectionAfter
            if (drop != null && applied >= drop) {
                dropConnectionAfter = null
                throw IOException("connection lost after $applied change(s)")
            }
        }
        return PushResponse(
            acknowledged = request.changes.map { it.idempotencyKey },
            serverSeq = log.size.toLong(),
        )
    }

    override suspend fun pull(request: PullRequest): PullResponse {
        val from = request.cursor.coerceAtLeast(0).toInt()
        val to = (from + request.limit).coerceAtMost(log.size)
        val page = if (from >= log.size) emptyList() else log.subList(from, to).toList()
        return PullResponse(
            changes = page,
            nextCursor = to.toLong(),
            hasMore = to < log.size,
            minSchemaVersion = minSchemaVersion,
        )
    }

    fun size(): Int = log.size

    fun all(): List<ChangeRecord> = log.toList()
}
