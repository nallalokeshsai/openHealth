package app.openhealth.core.sync

import kotlinx.serialization.Serializable

/** What a change does to one row. A delete is a tombstone, never a resurrection. */
@Serializable
enum class ChangeOperation {
    UPSERT,
    DELETE,
}

/**
 * One immutable entry in the change log (`Tech-Stack.md` §6).
 *
 * [fields] is the whole row as text values keyed by column name; [fieldClocks] records
 * the hybrid logical clock of the last write to each field, so two devices that edited
 * different fields can merge without either losing an edit. [idempotencyKey] is unique
 * per logical write and is what makes a retried push free.
 *
 * Unknown entities and unknown fields travel verbatim: the server stores them and hands
 * them back (`pr.md` §7.9).
 */
@Serializable
data class ChangeRecord(
    val entity: String,
    val entityId: String,
    val operation: ChangeOperation,
    val fields: Map<String, String?> = emptyMap(),
    val fieldClocks: Map<String, String> = emptyMap(),
    val idempotencyKey: String,
    val hlc: String,
    val deviceId: String,
    val schemaVersion: Int,
    val localDate: String? = null,
    val recordedAt: Long,
) {
    fun clock(): HybridLogicalClock = HybridLogicalClock.parse(hlc)

    /** The fields a merge may look at; a delete carries none. */
    fun revision(): Long? = fields["revision"]?.toLongOrNull()
}

/** The state of one syncable row as the merge engine sees it. */
data class SyncSnapshot(val fields: Map<String, String?>, val fieldClocks: Map<String, String>)

/** The four states the Settings screen shows (`pr.md` §6.24). */
@Serializable
enum class SyncStatus {
    LOCAL_ONLY,
    SYNCING,
    SYNCED,
    OFFLINE,
    ERROR,
}

/**
 * A conflict a policy could not settle. Both sides are kept with their full provenance;
 * the user resolves it and the resolution is itself a change (`pr.md` §7.7).
 */
@Serializable
data class SyncConflict(
    val id: String,
    val entity: String,
    val entityId: String,
    val local: ChangeRecord,
    val remote: ChangeRecord,
    val reason: String,
    val createdAt: Long,
)

/** The snapshot the Settings screen renders; no device identity is in it. */
data class SyncStateInfo(
    val status: SyncStatus,
    val serverUrl: String?,
    val deviceId: String,
    val lastSyncedAt: Long?,
    val lastError: String?,
    val pendingChanges: Int,
    val openConflicts: Int,
)

/** One push unit. The server acknowledges keys, so an interrupted push is retried whole. */
@Serializable
data class PushRequest(val deviceId: String, val changes: List<ChangeRecord>)

@Serializable
data class PushResponse(
    val acknowledged: List<String>,
    val serverSeq: Long,
    val rejected: List<RejectedChange> = emptyList(),
)

@Serializable
data class RejectedChange(val idempotencyKey: String, val reason: String)

@Serializable
data class PullRequest(val cursor: Long = 0, val limit: Int = 200)

@Serializable
data class PullResponse(
    val changes: List<ChangeRecord>,
    val nextCursor: Long,
    val hasMore: Boolean,
    /** The oldest schema version this server still accepts (`pr.md` §7.9). */
    val minSchemaVersion: Int = 1,
)

@Serializable
data class PairingRequest(val code: String, val deviceName: String)

@Serializable
data class PairingResponse(val token: String, val deviceId: String, val scopes: List<String>)

@Serializable
data class ServerHealth(
    val service: String,
    val schemaVersion: Int,
    val minSupportedSchemaVersion: Int,
)
