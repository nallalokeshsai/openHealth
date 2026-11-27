package app.openhealth.core.watch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Connect IQ packet fits one 20-byte notification and survives a round trip. */
class WatchBlePacketTest {
    private val now = 1_700_000_000_000L

    @Test
    fun aSetPacketRoundTripsInSixteenBytes() {
        val packet = WatchBlePacket.encodeCommand(
            WatchBleCommand(
                id = 42,
                type = WatchBlePacket.Type.LOG_SET,
                weightKg = 82.5,
                reps = 6,
                bpm = null,
                isWarmup = true,
                happenedAtMillis = now,
            ),
        )
        assertEquals(WatchBlePacket.COMMAND_SIZE, packet.size)
        val decoded = assertNotNull(WatchBlePacket.decodeCommand(packet))
        assertEquals(42, decoded.id)
        assertEquals(82.5, decoded.weightKg)
        assertEquals(6, decoded.reps)
        assertTrue(decoded.isWarmup)
        assertEquals(now, decoded.happenedAtMillis)
    }

    @Test
    fun aHeartRatePacketNeedsAHumanValue() {
        val heart = WatchBleCommand(
            id = 7,
            type = WatchBlePacket.Type.HEART_RATE,
            weightKg = 0.0,
            reps = 0,
            bpm = 72,
            isWarmup = false,
            happenedAtMillis = now,
        )
        assertNotNull(WatchBlePacket.decodeCommand(WatchBlePacket.encodeCommand(heart)))
        assertNull(
            WatchBlePacket.decodeCommand(WatchBlePacket.encodeCommand(heart.copy(bpm = 5))),
        )
    }

    @Test
    fun aSnapshotFitsOneNotificationAndKeepsItsFlags() {
        val packet = WatchBlePacket.encodeSnapshot(
            WatchBleSnapshot(
                sessionOpen = true,
                restRunning = true,
                connected = true,
                weightUnit = "lb",
                weightKg = 82.3,
                reps = 8,
                doneSets = 2,
                targetSets = 3,
                restRemainingSeconds = 45,
                restTotalSeconds = 120,
                weightIncrementKg = 2.27,
                ackCommandId = 42,
            ),
        )
        assertEquals(WatchBlePacket.SNAPSHOT_SIZE, packet.size)
        val decoded = assertNotNull(WatchBlePacket.decodeSnapshot(packet))
        assertTrue(decoded.sessionOpen)
        assertTrue(decoded.restRunning)
        assertTrue(decoded.connected)
        assertEquals("lb", decoded.weightUnit)
        assertEquals(82.3, decoded.weightKg)
        assertEquals(45, decoded.restRemainingSeconds)
        assertEquals(120, decoded.restTotalSeconds)
        assertEquals(2.27, decoded.weightIncrementKg)
        assertEquals(42, decoded.ackCommandId)
    }

    @Test
    fun aPacketFromAnotherVersionOrAForeignServiceIsIgnored() {
        val good = WatchBlePacket.encodeCommand(
            WatchBleCommand(1, WatchBlePacket.Type.FINISH, 0.0, 0, null, false, now),
        )
        assertNull(WatchBlePacket.decodeCommand(ByteArray(4)))
        assertNull(WatchBlePacket.decodeCommand(good.copyOf().also { it[0] = 0 }))
        assertNull(WatchBlePacket.decodeCommand(good.copyOf().also { it[1] = 9 }))
        assertNull(WatchBlePacket.decodeSnapshot(good))
    }
}
