package app.openhealth.sync

import app.openhealth.core.sync.ChangeRecord
import app.openhealth.core.sync.PullRequest
import app.openhealth.core.sync.PullResponse
import app.openhealth.core.sync.PushRequest
import app.openhealth.core.sync.PushResponse
import app.openhealth.core.sync.SyncConflict
import app.openhealth.core.sync.SyncStatus

/** What applying one pulled page produced. */
data class ApplyOutcome(
    val applied: Int,
    val conflicts: List<SyncConflict>,
    /** Changes the local store refused because its schema is older than the payload. */
    val refused: List<ChangeRecord> = emptyList(),
)

/**
 * The client half of sync: pending change log entries, the pull cursor and the merge
 * result. Implemented by `:data` on the device and in memory in tests. The engine never
 * touches SQL or Android.
 */
@Suppress("TooManyFunctions")
interface SyncLocalStore {
    fun deviceId(): String

    /** The schema this client writes and can read (`pr.md` §7.9). */
    fun schemaVersion(): Int

    /** Advance the local hybrid clock past a change just received. */
    fun observeRemote(clock: app.openhealth.core.sync.HybridLogicalClock) = Unit

    fun pendingChanges(limit: Int): List<ChangeRecord>

    fun pendingCount(): Int

    /** Marks a key as acknowledged at [serverSeq]; safe to call twice. */
    fun markAcknowledged(keys: Set<String>, serverSeq: Long)

    fun applyRemote(records: List<ChangeRecord>): ApplyOutcome

    fun cursor(): Long

    fun setCursor(cursor: Long)

    fun recordStatus(status: SyncStatus, atMillis: Long, error: String? = null)

    fun conflicts(): List<SyncConflict>

    fun openConflictCount(): Int

    /**
     * Resolve a conflict by keeping one side. Returns the change to push (the winning
     * side with a fresh key and clock), or null when the conflict is already gone.
     */
    fun resolveConflict(id: String, keepLocal: Boolean, atMillis: Long): ChangeRecord?

    fun lastSyncedAt(): Long?
}

/** The transport boundary. A fake implements it in tests; Ktor implements it on device. */
interface SyncTransport {
    suspend fun push(request: PushRequest): PushResponse

    suspend fun pull(request: PullRequest): PullResponse
}

/** Retry policy for a push or pull that the network dropped. */
data class RetryPolicy(
    val attempts: Int = 4,
    val baseDelayMillis: Long = 250,
    val maxDelayMillis: Long = 8_000,
) {
    /** Exponential backoff with a deterministic cap; no jitter in tests. */
    fun delayFor(attempt: Int): Long {
        require(attempt >= 1) { "attempt is 1-based" }
        var delay = baseDelayMillis
        repeat(attempt - 1) {
            delay = (delay * 2).coerceAtMost(maxDelayMillis)
        }
        return delay
    }
}
