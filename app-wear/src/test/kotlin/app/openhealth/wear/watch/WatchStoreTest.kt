package app.openhealth.wear.watch

import app.openhealth.core.watch.WatchCommand
import app.openhealth.core.watch.WatchCommandType
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The wrist's queue survives a process restart and acknowledges only what the phone applied. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WatchStoreTest {
    private fun store() = WatchStore(RuntimeEnvironment.getApplication())

    private fun command(id: String) = WatchCommand(
        id = id,
        type = WatchCommandType.LOG_SET,
        happenedAtMillis = 1_700_000_000_000L,
        sessionId = "s1",
        exerciseId = "e1",
        weightKg = 80.0,
        reps = 5,
    )

    @Test
    fun theQueueIsOrderedAndDeduplicatedById() {
        val store = store()
        store.clear()
        store.addPending(command("a"))
        store.addPending(command("b"))
        store.addPending(command("a"))
        assertEquals(listOf("a", "b"), store.pending().map { it.id })
    }

    @Test
    fun onlyTheAppliedCommandsLeaveTheQueue() {
        val store = store()
        store.clear()
        store.addPending(command("a"))
        store.addPending(command("b"))
        assertEquals(1, store.acknowledge(listOf("a")))
        assertEquals(listOf("b"), store.pending().map { it.id })
        assertEquals(0, store.acknowledge(listOf("missing")))
        assertNull(store.snapshot())
    }

    @Test
    fun aSnapshotSurvivesANewStoreInstance() {
        val store = store()
        store.clear()
        val snapshot = app.openhealth.core.watch.WatchSnapshot(
            generatedAtMillis = 1_700_000_000_000L,
            session = null,
        )
        store.saveSnapshot(snapshot)
        assertEquals(1_700_000_000_000L, store().snapshot()?.generatedAtMillis)
    }
}
