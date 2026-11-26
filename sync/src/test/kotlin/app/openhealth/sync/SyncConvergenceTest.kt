package app.openhealth.sync

import app.openhealth.core.sync.HybridClock
import app.openhealth.core.sync.SyncStatus
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncConvergenceTest {
    private val wall = 1_700_000_000_000L

    private fun harness(): Triple<InMemorySyncServer, InMemorySyncStore, InMemorySyncStore> {
        // One wall clock for both devices in the test: the merge algorithm, not a clock
        // collision, is what the property is about. Production clocks are per device.
        val clock = HybridClock { wall }
        return Triple(
            InMemorySyncServer(),
            InMemorySyncStore("device-a", clock),
            InMemorySyncStore("device-b", clock),
        )
    }

    @Test
    fun randomInterleavingsOfOfflineEditsConverge() {
        repeat(200) { iteration ->
            val seed = iteration.toLong()
            val random = Random(seed)
            val (server, a, b) = harness()
            val devices = listOf(a, b)

            fun localEdit(store: InMemorySyncStore, op: Int) {
                when (random.nextInt(3)) {
                    0 -> store.edit(
                        entity = "note",
                        id = "note-${random.nextInt(4)}",
                        values = mapOf("body" to "v$op-${store.deviceId()}"),
                    )
                    1 -> store.edit(
                        entity = "settings",
                        id = random.nextBoolean().toString(),
                        values = mapOf(
                            listOf("units", "theme", "accent").random(random) to "v$op",
                        ),
                    )
                    else -> store.edit(
                        entity = "observation",
                        id = "${store.deviceId()}-$op",
                        values = mapOf("value" to "$op", "type" to "steps"),
                    )
                }
            }

            repeat(20) { op ->
                if (random.nextInt(4) != 0) {
                    localEdit(devices[random.nextInt(2)], op)
                }
            }
            // Sync in a random order, several rounds, so changes meet in both directions.
            repeat(6) {
                devices.shuffled(random).forEach { device ->
                    val engine = SyncEngine(
                        store = device,
                        transport = server,
                        now = { wall },
                        retry = RetryPolicy(attempts = 3, baseDelayMillis = 0),
                        sleep = {},
                    )
                    kotlinx.coroutines.test.runTest { engine.syncOnce() }
                }
            }

            assertEquals(a.snapshot(), b.snapshot(), "seed $seed diverged")
            assertTrue(!a.hasPending() && !b.hasPending(), "seed $seed left changes queued")
            assertEquals(server.size(), server.all().map { it.idempotencyKey }.toSet().size)
        }
    }

    @Test
    fun aDroppedConnectionMidPushLosesNothingAndRetriesForFree() {
        val server = InMemorySyncServer()
        val clock = HybridClock { wall }
        val a = InMemorySyncStore("device-a", clock)
        val b = InMemorySyncStore("device-b", clock)
        repeat(5) { i ->
            a.edit("note", "note-$i", mapOf("body" to "offline $i"))
        }
        val expected = server.size() + 5

        // The first attempt is allowed exactly one change, then the link dies.
        server.dropConnectionAfter = 1
        val first = SyncEngine(
            store = a,
            transport = server,
            now = { wall },
            retry = RetryPolicy(attempts = 1),
            sleep = {},
        )
        var failed: SyncResult? = null
        kotlinx.coroutines.test.runTest { failed = first.syncOnce() }
        assertEquals(SyncStatus.OFFLINE, failed!!.status)
        assertTrue(a.hasPending(), "an interrupted push must leave its changes queued")

        // The phone reconnects and pushes again; the server keeps the first copy of each
        // key, so the retry is free and nothing is duplicated.
        val second = SyncEngine(
            store = a,
            transport = server,
            now = { wall },
            retry = RetryPolicy(attempts = 3, baseDelayMillis = 0),
            sleep = {},
        )
        var ok: SyncResult? = null
        kotlinx.coroutines.test.runTest { ok = second.syncOnce() }
        assertEquals(SyncStatus.SYNCED, ok!!.status)
        assertTrue(!a.hasPending())
        assertEquals(expected, server.size())
        assertEquals(server.size(), server.all().map { it.idempotencyKey }.toSet().size)

        // The other device pulls everything the first one wrote.
        val engineB = SyncEngine(
            store = b,
            transport = server,
            now = { wall },
            retry = RetryPolicy(attempts = 3, baseDelayMillis = 0),
            sleep = {},
        )
        kotlinx.coroutines.test.runTest { engineB.syncOnce() }
        assertEquals(a.snapshot(), b.snapshot())
    }

    @Test
    fun twoEditsFromTheSameRevisionAreSurfacedNotSilentlyMerged() {
        val server = InMemorySyncServer()
        val clock = HybridClock { wall }
        val a = InMemorySyncStore("device-a", clock)
        val b = InMemorySyncStore("device-b", clock)
        a.editDocument("profile", "me", mapOf("displayName" to "Alex"))
        b.editDocument("profile", "me", mapOf("displayName" to "Sam"))

        val engineA =
            SyncEngine(a, server, {
                wall
            }, retry = RetryPolicy(attempts = 2, baseDelayMillis = 0), sleep = {})
        val engineB =
            SyncEngine(b, server, {
                wall
            }, retry = RetryPolicy(attempts = 2, baseDelayMillis = 0), sleep = {})
        kotlinx.coroutines.test.runTest { engineA.syncOnce() }
        kotlinx.coroutines.test.runTest { engineB.syncOnce() }
        kotlinx.coroutines.test.runTest { engineA.syncOnce() }

        assertEquals(1, a.openConflictCount(), "device A must see the conflict")
        assertEquals(1, b.openConflictCount(), "device B must see the conflict")
        assertTrue(a.conflicts().single().local.fields["displayName"] == "Alex")
        assertTrue(a.conflicts().single().remote.fields["displayName"] == "Sam")
    }

    @Test
    fun resolvingAConflictConvergesBothDevices() {
        val server = InMemorySyncServer()
        val clock = HybridClock { wall }
        val a = InMemorySyncStore("device-a", clock)
        val b = InMemorySyncStore("device-b", clock)
        a.editDocument("profile", "me", mapOf("displayName" to "Alex"))
        b.editDocument("profile", "me", mapOf("displayName" to "Sam"))
        val engineA =
            SyncEngine(a, server, {
                wall
            }, retry = RetryPolicy(attempts = 2, baseDelayMillis = 0), sleep = {})
        val engineB =
            SyncEngine(b, server, {
                wall
            }, retry = RetryPolicy(attempts = 2, baseDelayMillis = 0), sleep = {})
        kotlinx.coroutines.test.runTest { engineA.syncOnce() }
        kotlinx.coroutines.test.runTest { engineB.syncOnce() }
        kotlinx.coroutines.test.runTest { engineA.syncOnce() }

        val conflict = a.conflicts().single()
        a.resolveConflict(conflict.id, keepLocal = false, atMillis = wall)
        kotlinx.coroutines.test.runTest { engineA.syncOnce() }
        kotlinx.coroutines.test.runTest { engineB.syncOnce() }
        kotlinx.coroutines.test.runTest { engineA.syncOnce() }

        assertEquals(0, a.openConflictCount())
        assertEquals(0, b.openConflictCount())
        assertEquals("Sam", a.row("profile", "me")?.get("displayName"))
        assertEquals("Sam", b.row("profile", "me")?.get("displayName"))
        assertEquals(a.snapshot(), b.snapshot())
    }

    @Test
    fun aClientOlderThanTheServersWindowIsToldToUpdateAndKeepsLogging() {
        val server = InMemorySyncServer()
        server.minSchemaVersion = 99
        val store = InMemorySyncStore("device-a", HybridClock { wall }, schema = 11)
        val engine =
            SyncEngine(store, server, {
                wall
            }, retry = RetryPolicy(attempts = 2, baseDelayMillis = 0), sleep = {})
        var result: SyncResult? = null
        kotlinx.coroutines.test.runTest { result = engine.syncOnce() }
        assertEquals(SyncStatus.ERROR, result!!.status)
        assertTrue(result!!.error!!.contains("update"))
    }
}
