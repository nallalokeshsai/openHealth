package app.openhealth.data.sync

import app.cash.sqldelight.db.SqlDriver
import app.openhealth.core.merge.MergeAction
import app.openhealth.core.merge.SyncMergePolicyRegistry
import app.openhealth.core.seam.ClockSeam
import app.openhealth.core.seam.SystemClock
import app.openhealth.core.sync.ChangeOperation
import app.openhealth.core.sync.ChangeRecord
import app.openhealth.core.sync.HybridClock
import app.openhealth.core.sync.HybridLogicalClock
import app.openhealth.core.sync.SyncConflict
import app.openhealth.core.sync.SyncSnapshot
import app.openhealth.core.sync.SyncStatus
import app.openhealth.data.db.OpenHealthDb
import app.openhealth.data.repo.SettingKeys
import app.openhealth.sync.ApplyOutcome
import app.openhealth.sync.SyncLocalStore
import java.util.UUID
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * The production change log: captures every write the repositories make, applies a
 * pulled page through the same merge registry the server names, and keeps conflicts for
 * the user (`Tech-Stack.md` §6, `pr.md` §7.7).
 *
 * Capture runs inside the repository's own transaction, so a write and its log entry
 * commit together or not at all. A remote apply sets [applying] so the domain writes it
 * makes are not captured again and pushed back as new changes.
 */
@Suppress("TooManyFunctions")
class SyncRepository(
    private val db: OpenHealthDb,
    private val driver: SqlDriver,
    private val clock: ClockSeam = SystemClock,
    private val device: String,
    private val schemaVersion: Int,
    private val registry: SyncMergePolicyRegistry = SyncMergePolicyRegistry.default(),
) : SyncLocalStore {
    private val codec = SyncRowCodec(driver)
    private val hlc = HybridClock { clock.now().toEpochMilliseconds() }
    private var rollupInvalidator: ((kotlinx.datetime.LocalDate) -> Unit)? = null

    /**
     * A remote apply bypasses the repositories, so the composition root wires the same
     * rollup engine here: a Tuesday that arrives on Wednesday rewrites Tuesday
     * (`pr.md` §7.3). Rollups are never synced; they are rebuilt from the applied rows.
     */
    fun invalidateRollupsWith(invalidator: ((kotlinx.datetime.LocalDate) -> Unit)?) {
        rollupInvalidator = invalidator
    }

    @Volatile
    private var applying = false

    /** Sample data is generated through the real repositories; it must never sync. */
    @Volatile
    private var captureEnabled = true

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = true
        encodeDefaults = true
    }

    // ---------------------------------------------------------------- capture

    /**
     * Record the row as it stands now. Called by every repository write after the write,
     * inside the same transaction. An entity with no sync table is ignored, so a local
     * cache cannot leak into the log.
     */
    @Suppress("ReturnCount")
    fun capture(entity: String, entityId: String, localDate: String? = null) {
        if (applying || !captureEnabled) return
        val table = SyncTables.of(entity) ?: return
        if (entity in SyncTables.neverSynced) return
        if (entity == "settings" &&
            !app.openhealth.data.repo.SyncSettingsPolicy.shouldSync(entityId)
        ) {
            return
        }
        val keyValues = SyncRowCodec.keyValues(entityId, table.keys.size)
        val fields = codec.read(table.table, table.keys, keyValues) ?: return
        val now = hlc.next()
        val clocks = fields.keys.associateWith { now.encode() }
        val record = ChangeRecord(
            entity = entity,
            entityId = entityId,
            operation = ChangeOperation.UPSERT,
            fields = fields,
            fieldClocks = clocks,
            idempotencyKey = "$device:${UUID.randomUUID()}",
            hlc = now.encode(),
            deviceId = device,
            schemaVersion = schemaVersion,
            localDate = localDate ?: fields["local_date"],
            recordedAt = now.wallMillis,
        )
        db.syncQueries.insertChange(
            record.entity,
            record.entityId,
            record.operation.name,
            encode(record.fields),
            encodeClocks(record.fieldClocks),
            record.idempotencyKey,
            record.hlc,
            record.deviceId,
            record.schemaVersion.toLong(),
            record.localDate,
            record.recordedAt,
        )
        db.syncQueries.upsertRowState(
            entity = record.entity,
            entity_id = record.entityId,
            hlc = record.hlc,
            field_clocks = encodeClocks(record.fieldClocks),
            deleted = 0L,
        )
    }

    /** A delete is a tombstone in the log, never a silent resurrection (`pr.md` §7.7). */
    @Suppress("ReturnCount")
    fun captureDelete(entity: String, entityId: String, localDate: String? = null) {
        if (applying || !captureEnabled) return
        val table = SyncTables.of(entity) ?: return
        if (entity in SyncTables.neverSynced) return
        if (entity == "settings" &&
            !app.openhealth.data.repo.SyncSettingsPolicy.shouldSync(entityId)
        ) {
            return
        }
        val now = hlc.next()
        val record = ChangeRecord(
            entity = entity,
            entityId = entityId,
            operation = ChangeOperation.DELETE,
            idempotencyKey = "$device:${UUID.randomUUID()}",
            hlc = now.encode(),
            deviceId = device,
            schemaVersion = schemaVersion,
            localDate = localDate,
            recordedAt = now.wallMillis,
        )
        db.syncQueries.insertChange(
            record.entity,
            record.entityId,
            record.operation.name,
            encode(record.fields),
            encodeClocks(record.fieldClocks),
            record.idempotencyKey,
            record.hlc,
            record.deviceId,
            record.schemaVersion.toLong(),
            record.localDate,
            record.recordedAt,
        )
        db.syncQueries.upsertRowState(
            entity = record.entity,
            entity_id = record.entityId,
            hlc = record.hlc,
            field_clocks = encodeClocks(record.fieldClocks),
            deleted = 1L,
        )
    }

    /** Demo/sample writes stay local: they are not the person's record. */
    fun pauseCapture() {
        captureEnabled = false
    }

    fun resumeCapture() {
        captureEnabled = true
    }

    // ----------------------------------------------------------------- client

    override fun deviceId(): String = device

    override fun schemaVersion(): Int = schemaVersion

    override fun observeRemote(clock: HybridLogicalClock) {
        hlc.observe(clock)
    }

    override fun pendingChanges(limit: Int): List<ChangeRecord> =
        db.syncQueries.pendingChanges(limit.toLong()).executeAsList().map(::toRecord)

    override fun pendingCount(): Int = db.syncQueries.pendingChangeCount().executeAsOne().toInt()

    override fun markAcknowledged(keys: Set<String>, serverSeq: Long) {
        val at = clock.now().toEpochMilliseconds()
        db.transaction {
            keys.forEach { key ->
                db.syncQueries.markChangeAcknowledged(at, serverSeq, key)
            }
        }
    }

    override fun cursor(): Long = db.settingsQueries.setting(SettingKeys.SYNC_PULL_CURSOR)
        .executeAsOneOrNull()?.value_?.toLongOrNull() ?: 0L

    override fun setCursor(cursor: Long) {
        db.settingsQueries.putSetting(
            key = SettingKeys.SYNC_PULL_CURSOR,
            value = cursor.toString(),
            updatedAt = clock.now().toEpochMilliseconds(),
            schemaVersion = schemaVersion.toLong(),
        )
    }

    override fun recordStatus(status: SyncStatus, atMillis: Long, error: String?) {
        // Written through the generated query, not SettingsRepository: a status write
        // must never become a change of its own on its way to the server.
        fun put(key: String, value: String) {
            db.settingsQueries.putSetting(key, value, atMillis, schemaVersion.toLong())
        }
        put(SettingKeys.SYNC_STATUS, status.name)
        put(SettingKeys.SYNC_LAST_ERROR, error.orEmpty())
        if (status == SyncStatus.SYNCED) put(SettingKeys.SYNC_LAST_SYNCED, atMillis.toString())
    }

    override fun lastSyncedAt(): Long? = db.settingsQueries.setting(SettingKeys.SYNC_LAST_SYNCED)
        .executeAsOneOrNull()?.value_?.toLongOrNull()

    override fun conflicts(): List<SyncConflict> =
        db.syncQueries.conflicts().executeAsList().mapNotNull(::toConflict)

    override fun openConflictCount(): Int =
        db.syncQueries.openConflictCount().executeAsOne().toInt()

    @Suppress("CyclomaticComplexMethod", "LoopWithTooManyJumpStatements")
    override fun applyRemote(records: List<ChangeRecord>): ApplyOutcome {
        val dates = mutableSetOf<String>()
        val outcome = db.transactionWithResult {
            applying = true
            try {
                var applied = 0
                val conflicts = mutableListOf<SyncConflict>()
                val refused = mutableListOf<ChangeRecord>()
                // Parents first for writes; children first for deletes. A stable sort
                // keeps the server's sequence inside each group.
                val ordered = records.sortedWith(
                    compareBy(
                        { record ->
                            val rank = SyncTables.of(record.entity)?.rank ?: UNKNOWN_RANK
                            if (record.operation == ChangeOperation.DELETE) -rank else rank
                        },
                    ),
                )
                for (record in ordered) {
                    // Our own pushed change comes back on the next pull; it is already in
                    // the log and re-applying it would clear a real conflict.
                    if (db.syncQueries.changeByKey(record.idempotencyKey)
                            .executeAsOneOrNull() != null
                    ) {
                        continue
                    }
                    val table = SyncTables.of(record.entity)
                    val policy = registry.policyFor(record.entity)
                    if (table == null || policy == null || record.schemaVersion > schemaVersion) {
                        refused += record
                        continue
                    }
                    if (record.entity in SyncTables.neverSynced) {
                        refused += record
                        continue
                    }
                    val appliedChange = applyOne(record, table, policy)
                    when (appliedChange.kind) {
                        ApplyKind.APPLIED -> {
                            applied += 1
                            record.localDate?.let(dates::add)
                        }
                        ApplyKind.CONFLICT -> appliedChange.conflict?.let {
                            conflicts += it
                            insertConflict(it)
                        }
                        ApplyKind.REFUSED -> refused += record
                    }
                    // Keep the wire payload, unknown fields included (`pr.md` §7.9).
                    insertApplied(record)
                }
                ApplyOutcome(applied, conflicts, refused)
            } finally {
                applying = false
            }
        }
        dates.mapNotNull { runCatching { kotlinx.datetime.LocalDate.parse(it) }.getOrNull() }
            .forEach { rollupInvalidator?.invoke(it) }
        return outcome
    }

    private data class OneApply(val kind: ApplyKind, val conflict: SyncConflict? = null)

    private enum class ApplyKind { APPLIED, CONFLICT, REFUSED }

    @Suppress("LongMethod", "ReturnCount")
    private fun applyOne(
        record: ChangeRecord,
        table: SyncTable,
        policy: app.openhealth.core.merge.MergePolicy<SyncSnapshot>,
    ): OneApply {
        val keyValues = SyncRowCodec.keyValues(record.entityId, table.keys.size)
        val localFields = codec.read(table.table, table.keys, keyValues)
        val state = db.syncQueries.rowState(record.entity, record.entityId).executeAsOneOrNull()
        val localHlc = state?.hlc ?: HybridLogicalClock.ZERO.encode()
        val localClocks = state?.field_clocks?.let(::decodeClocks) ?: emptyMap()

        if (record.operation == ChangeOperation.DELETE) {
            if (localFields == null) {
                db.syncQueries.upsertRowState(
                    record.entity,
                    record.entityId,
                    record.hlc,
                    encodeClocks(emptyMap()),
                    1L,
                )
                clearConflicts(record.entity, record.entityId)
                return OneApply(ApplyKind.APPLIED)
            }
            if (record.clock() >= HybridLogicalClock.parse(localHlc)) {
                if (!codec.delete(table.table, table.keys, keyValues)) {
                    return OneApply(ApplyKind.REFUSED)
                }
                db.syncQueries.upsertRowState(
                    record.entity,
                    record.entityId,
                    record.hlc,
                    state?.field_clocks ?: encodeClocks(emptyMap()),
                    1L,
                )
                clearConflicts(record.entity, record.entityId)
                return OneApply(ApplyKind.APPLIED)
            }
            return OneApply(
                ApplyKind.CONFLICT,
                conflict(record, localFields, localClocks, localHlc),
            )
        }

        if (localFields == null) {
            if (!codec.write(table.table, table.keys, record.fields)) {
                return OneApply(ApplyKind.REFUSED)
            }
            db.syncQueries.upsertRowState(
                record.entity,
                record.entityId,
                record.hlc,
                encodeClocks(
                    record.fieldClocks.ifEmpty {
                        record.fields.keys.associateWith { record.hlc }
                    },
                ),
                0L,
            )
            clearConflicts(record.entity, record.entityId)
            return OneApply(ApplyKind.APPLIED)
        }

        val local = SyncSnapshot(localFields, localClocks)
        val remote = SyncSnapshot(record.fields, record.fieldClocks)
        val merged = policy.merge(
            local,
            remote,
            orderKey(HybridLogicalClock.parse(localHlc)),
            orderKey(record.clock()),
        )
        val conflict = merged.conflict
        if (merged.action == MergeAction.CONFLICT || conflict != null) {
            return OneApply(
                ApplyKind.CONFLICT,
                conflict(record, localFields, localClocks, localHlc, conflict?.reasonKey),
            )
        }
        val value = merged.value ?: return OneApply(ApplyKind.APPLIED)
        if (!codec.write(table.table, table.keys, value.fields)) {
            return OneApply(ApplyKind.REFUSED)
        }
        db.syncQueries.upsertRowState(
            record.entity,
            record.entityId,
            maxOf(localHlc, record.hlc),
            encodeClocks(value.fieldClocks.ifEmpty { record.fieldClocks }),
            0L,
        )
        clearConflicts(record.entity, record.entityId)
        return OneApply(ApplyKind.APPLIED)
    }

    @Suppress("ReturnCount")
    override fun resolveConflict(id: String, keepLocal: Boolean, atMillis: Long): ChangeRecord? {
        val row = db.syncQueries.conflictById(id).executeAsOneOrNull() ?: return null
        val chosen = runCatching {
            json.decodeFromString(
                ChangeRecord.serializer(),
                if (keepLocal) row.local_change else row.remote_change,
            )
        }.getOrNull() ?: return null
        val table = SyncTables.of(row.entity) ?: return null
        val now = hlc.next()
        val fields = chosen.fields.toMutableMap()
        val revision = fields["revision"]?.toLongOrNull()
        if (revision != null) fields["revision"] = (revision + 1).toString()
        val clocks = fields.keys.associateWith { now.encode() }
        val resolved = ChangeRecord(
            entity = row.entity,
            entityId = row.entity_id,
            operation = ChangeOperation.UPSERT,
            fields = fields,
            fieldClocks = clocks,
            idempotencyKey = "$device:${UUID.randomUUID()}",
            hlc = now.encode(),
            deviceId = device,
            schemaVersion = schemaVersion,
            recordedAt = atMillis,
        )
        db.transaction {
            codec.write(table.table, table.keys, fields)
            db.syncQueries.insertChange(
                resolved.entity,
                resolved.entityId,
                resolved.operation.name,
                encode(resolved.fields),
                encodeClocks(resolved.fieldClocks),
                resolved.idempotencyKey,
                resolved.hlc,
                resolved.deviceId,
                resolved.schemaVersion.toLong(),
                resolved.localDate,
                resolved.recordedAt,
            )
            db.syncQueries.upsertRowState(
                resolved.entity,
                resolved.entityId,
                resolved.hlc,
                encodeClocks(clocks),
                0L,
            )
            db.syncQueries.deleteConflict(id)
        }
        fields["local_date"]?.let { date ->
            runCatching { kotlinx.datetime.LocalDate.parse(date) }.getOrNull()
                ?.let { rollupInvalidator?.invoke(it) }
        }
        return resolved
    }

    // ---------------------------------------------------------------- helpers

    private fun insertApplied(record: ChangeRecord) {
        db.syncQueries.insertAppliedChange(
            record.entity,
            record.entityId,
            record.operation.name,
            encode(record.fields),
            encodeClocks(record.fieldClocks),
            record.idempotencyKey,
            record.hlc,
            record.deviceId,
            record.schemaVersion.toLong(),
            record.localDate,
            record.recordedAt,
            clock.now().toEpochMilliseconds(),
        )
    }

    private fun insertConflict(conflict: SyncConflict) {
        db.syncQueries.insertConflict(
            conflict.id,
            conflict.entity,
            conflict.entityId,
            json.encodeToString(ChangeRecord.serializer(), conflict.local),
            json.encodeToString(ChangeRecord.serializer(), conflict.remote),
            conflict.reason,
            conflict.createdAt,
        )
    }

    private fun clearConflicts(entity: String, entityId: String) {
        db.syncQueries.deleteConflictsForEntity(entity, entityId)
    }

    private fun conflict(
        record: ChangeRecord,
        localFields: Map<String, String?>,
        localClocks: Map<String, String>,
        localHlc: String,
        reasonKey: String? = null,
    ): SyncConflict {
        val now = clock.now().toEpochMilliseconds()
        val local = ChangeRecord(
            entity = record.entity,
            entityId = record.entityId,
            operation = ChangeOperation.UPSERT,
            fields = localFields,
            fieldClocks = localClocks,
            idempotencyKey = "local:$device:${record.entityId}",
            hlc = localHlc,
            deviceId = device,
            schemaVersion = schemaVersion,
            recordedAt = now,
        )
        return SyncConflict(
            id = "${record.entity}:${record.entityId}:${record.idempotencyKey}",
            entity = record.entity,
            entityId = record.entityId,
            local = local,
            remote = record,
            reason = reasonKey ?: "sync.conflict.manual",
            createdAt = now,
        )
    }

    private fun toConflict(row: app.openhealth.data.db.Sync_conflict): SyncConflict? = runCatching {
        SyncConflict(
            id = row.id,
            entity = row.entity,
            entityId = row.entity_id,
            local = json.decodeFromString(ChangeRecord.serializer(), row.local_change),
            remote = json.decodeFromString(ChangeRecord.serializer(), row.remote_change),
            reason = row.reason,
            createdAt = row.created_at,
        )
    }.getOrNull()

    private fun toRecord(row: app.openhealth.data.db.Change_log): ChangeRecord = ChangeRecord(
        entity = row.entity,
        entityId = row.entity_id,
        operation = ChangeOperation.valueOf(row.operation),
        fields = decodeFields(row.fields),
        fieldClocks = decodeClocks(row.field_clocks),
        idempotencyKey = row.idempotency_key,
        hlc = row.hlc,
        deviceId = row.device_id,
        schemaVersion = row.schema_version.toInt(),
        localDate = row.local_date,
        recordedAt = row.recorded_at,
    )

    private fun encode(fields: Map<String, String?>): String =
        json.encodeToString(mapSerializer, fields)

    private fun decodeFields(text: String): Map<String, String?> =
        runCatching { json.decodeFromString(mapSerializer, text) }.getOrDefault(emptyMap())

    private fun encodeClocks(clocks: Map<String, String>): String =
        json.encodeToString(stringMapSerializer, clocks)

    private fun decodeClocks(text: String?): Map<String, String> =
        text?.let { runCatching { json.decodeFromString(stringMapSerializer, it) }.getOrNull() }
            .orEmpty()

    private fun orderKey(clock: HybridLogicalClock): Long =
        clock.wallMillis * ORDER_SCALE + clock.counter

    private companion object {
        const val UNKNOWN_RANK = 500
        const val ORDER_SCALE = 1_000_000L
        val mapSerializer = MapSerializer(String.serializer(), String.serializer().nullable)
        val stringMapSerializer = MapSerializer(String.serializer(), String.serializer())
    }
}
