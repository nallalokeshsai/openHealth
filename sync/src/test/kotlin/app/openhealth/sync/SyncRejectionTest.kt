package app.openhealth.sync

import app.openhealth.core.sync.HybridClock
import app.openhealth.core.sync.PullRequest
import app.openhealth.core.sync.PullResponse
import app.openhealth.core.sync.PushRequest
import app.openhealth.core.sync.PushResponse
import app.openhealth.core.sync.RejectedChange
import app.openhealth.core.sync.SyncStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** One change the server refuses must not hold every later change back. */
class SyncRejectionTest {
    private class RefusingServer(private val inner: InMemorySyncServer) : SyncTransport {
        override suspend fun push(request: PushRequest): PushResponse {
            val (bad, good) = request.changes.partition { it.entityId == "bad" }
            val response = inner.push(request.copy(changes = good))
            return response.copy(
                rejected = bad.map { RejectedChange(it.idempotencyKey, "out of range") },
            )
        }

        override suspend fun pull(request: PullRequest): PullResponse = inner.pull(request)
    }

    @Test
    fun aRefusedChangeIsSetAsideAndTheRestStillSync() {
        val server = InMemorySyncServer()
        val store = InMemorySyncStore("device-a", HybridClock({ WALL }, seed = 3))
        store.edit("note", "bad", mapOf("body" to "refused"))
        store.edit("note", "good-1", mapOf("body" to "one"))
        store.edit("note", "good-2", mapOf("body" to "two"))
        val engine = SyncEngine(
            store = store,
            transport = RefusingServer(server),
            now = { WALL },
            retry = RetryPolicy(attempts = 1),
            sleep = {},
        )
        var result: SyncResult? = null
        kotlinx.coroutines.test.runTest { result = engine.syncOnce() }
        assertEquals(SyncStatus.SYNCED, result!!.status)
        assertEquals(2, result!!.pushed)
        assertTrue(result!!.error!!.contains("1 change(s)"), result!!.error)
        assertEquals(0, store.pendingCount(), "the queue must drain past the refused change")
    }

    private companion object {
        const val WALL = 1_700_000_000_000L
    }
}
