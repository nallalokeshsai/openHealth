package app.openhealth.sync

import app.openhealth.core.sync.ChangeRecord
import app.openhealth.core.sync.PullRequest
import app.openhealth.core.sync.PushRequest
import app.openhealth.core.sync.SyncStatus
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** What one sync pass did. The Settings screen reads the same facts the engine returns. */
data class SyncResult(
    val pushed: Int,
    val pulled: Int,
    val conflicts: Int,
    val status: SyncStatus,
    val error: String? = null,
)

/** A protocol refusal: retrying will not help until the app or the server changes. */
private class SyncFailure(val reason: String) : Exception(reason)

/**
 * Push then pull, idempotently (`Tech-Stack.md` §6, `pr.md` §7.7).
 *
 * A push is retried as a whole: every change carries its own idempotency key and the
 * server keeps the first copy, so a connection killed halfway through acknowledges what
 * landed and the retry inserts only the rest. Nothing is marked synced before the
 * server acknowledges it, and a pull only advances the cursor after its page applies.
 */
class SyncEngine(
    private val store: SyncLocalStore,
    private val transport: SyncTransport,
    private val now: () -> Long,
    private val pageSize: Int = 200,
    private val retry: RetryPolicy = RetryPolicy(),
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    private class Progress {
        var pushed = 0
        var pulled = 0
        var conflicts = 0
        var rejected = 0
    }

    init {
        require(pageSize > 0) { "pageSize must be positive" }
    }

    suspend fun syncOnce(): SyncResult {
        store.recordStatus(SyncStatus.SYNCING, now())
        val progress = Progress()
        val outcome = runCatching {
            pushPending(progress)
            pullRemote(progress)
        }
        return outcome.fold(
            onSuccess = {
                val note = progress.rejected.takeIf { it > 0 }
                    ?.let { "$it change(s) were refused by the server and set aside" }
                store.recordStatus(SyncStatus.SYNCED, now(), note)
                SyncResult(
                    progress.pushed,
                    progress.pulled,
                    progress.conflicts,
                    SyncStatus.SYNCED,
                    note,
                )
            },
            onFailure = { failure ->
                if (failure is CancellationException) throw failure
                val status = if (failure is SyncFailure) SyncStatus.ERROR else SyncStatus.OFFLINE
                val reason = failure.message ?: failure::class.simpleName ?: "sync failed"
                store.recordStatus(status, now(), reason)
                SyncResult(progress.pushed, progress.pulled, progress.conflicts, status, reason)
            },
        )
    }

    private suspend fun pushPending(progress: Progress) {
        while (true) {
            val pending = store.pendingChanges(pageSize)
            if (pending.isEmpty()) return
            val response = withRetry { transport.push(PushRequest(store.deviceId(), pending)) }
            // Only a key from this page can clear a pending change; anything else the
            // server echoes would leave the queue the same size and spin this loop.
            val sent = pending.mapTo(mutableSetOf(), ChangeRecord::idempotencyKey)
            // A refused change is set aside, not retried forever: one bad change must not
            // hold every later change back (the queue still strictly shrinks).
            val refused = response.rejected.map { it.idempotencyKey }
                .filterTo(mutableSetOf(), sent::contains)
            if (refused.isNotEmpty()) {
                store.quarantine(refused)
                progress.rejected += refused.size
            }
            val acknowledged = response.acknowledged
                .filterTo(mutableSetOf()) { it in sent && it !in refused }
            if (acknowledged.isEmpty() && refused.isEmpty()) {
                throw SyncFailure("server acknowledged no changes")
            }
            if (acknowledged.isNotEmpty()) store.markAcknowledged(acknowledged, response.serverSeq)
            progress.pushed += acknowledged.size
            // Every pass acknowledges at least one change, so the queue strictly shrinks
            // and the loop ends when it is empty. A backlog larger than one page — the
            // first pairing after the pre-v11 backfill — drains in this one sync pass
            // instead of being left queued under a Synced status.
        }
    }

    private suspend fun pullRemote(progress: Progress) {
        var cursor = store.cursor()
        while (true) {
            val response = withRetry { transport.pull(PullRequest(cursor, pageSize)) }
            val tooNew = response.changes.filter { it.schemaVersion > store.schemaVersion() }
            val refusal = when {
                store.schemaVersion() < response.minSchemaVersion ->
                    "this app is too old to sync; update to continue"
                tooNew.isNotEmpty() ->
                    "${tooNew.size} change(s) need a newer app; update to continue"
                else -> null
            }
            if (refusal != null) throw SyncFailure(refusal)
            if (response.changes.isNotEmpty()) {
                response.changes.forEach { store.observeRemote(it.clock()) }
                val outcome = store.applyRemote(response.changes)
                progress.conflicts += outcome.conflicts.size
                progress.pulled += outcome.applied
                if (outcome.refused.isNotEmpty()) {
                    throw SyncFailure("schema too old for ${outcome.refused.size} change(s)")
                }
            }
            cursor = response.nextCursor
            store.setCursor(cursor)
            if (!response.hasMore) return
        }
    }

    @Suppress("TooGenericExceptionCaught", "ThrowsCount") // transport failures are varied
    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_CLIENT_ERROR_END = 499
    }

    @Suppress("TooGenericExceptionCaught", "ThrowsCount") // transport failures are varied
    private suspend fun <T> withRetry(block: suspend () -> T): T {
        var attempt = 1
        while (true) {
            try {
                return block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: ResponseException) {
                val status = failure.response.status.value
                if (status == HTTP_UNAUTHORIZED || status == HTTP_FORBIDDEN) {
                    throw SyncFailure("this device is no longer paired; pair again")
                }
                if (status in HTTP_BAD_REQUEST..HTTP_CLIENT_ERROR_END) {
                    throw SyncFailure("server refused the request ($status)")
                }
                if (attempt >= retry.attempts) throw failure
                sleep(retry.delayFor(attempt))
                attempt += 1
            } catch (failure: Exception) {
                if (attempt >= retry.attempts) throw failure
                sleep(retry.delayFor(attempt))
                attempt += 1
            }
        }
    }
}
