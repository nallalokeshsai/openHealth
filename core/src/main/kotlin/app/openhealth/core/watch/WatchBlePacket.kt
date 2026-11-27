@file:Suppress("MagicNumber")

package app.openhealth.core.watch

/**
 * The Connect IQ BLE packet (`pr.md` §6.18; `docs/adr/0019`).
 *
 * A Connect IQ characteristic write is limited to 20 bytes and has no long write, so the
 * phone and the watch exchange fixed-size binary packets instead of JSON. The same
 * snapshot fields are used by the Wear app through [WatchSnapshot]; this is the compact
 * projection the watch can read in one notification.
 *
 * Commands are deliberately small: the phone already owns the session and the current
 * exercise. A packet says "log this set", "finish" or "heart rate", and the phone fills
 * the session and exercise from the snapshot it just built.
 */
object WatchBlePacket {
    const val MAGIC: Int = 0xA7
    const val VERSION: Int = 1
    const val COMMAND_SIZE: Int = 16
    const val SNAPSHOT_SIZE: Int = 20

    /**
     * The GATT contract, here rather than in the Android server, because it is part of the
     * same wire model the Connect IQ app reads (`docs/adr/0019` decision 3). The watch
     * finds the phone by this service UUID and by nothing else: the phone's Bluetooth name
     * is the person's and never goes on the air.
     */
    const val SERVICE_UUID: String = "6f1e2a10-4b7a-4f1e-9d2c-6f0e2b7a1000"
    const val COMMAND_UUID: String = "6f1e2a10-4b7a-4f1e-9d2c-6f0e2b7a1001"
    const val SNAPSHOT_UUID: String = "6f1e2a10-4b7a-4f1e-9d2c-6f0e2b7a1002"

    enum class Type(val code: Int) {
        LOG_SET(1),
        FINISH(2),
        HEART_RATE(3),
    }

    fun encodeCommand(command: WatchBleCommand): ByteArray {
        val out = ByteArray(COMMAND_SIZE)
        out[0] = MAGIC.toByte()
        out[1] = VERSION.toByte()
        out[2] = command.type.code.toByte()
        out[3] = if (command.isWarmup) 1 else 0
        putU16(out, 4, (command.weightKg.coerceIn(0.0, 500.0) * 10.0).toInt())
        out[6] = command.reps.coerceIn(0, 255).toByte()
        out[7] = (command.bpm ?: 0).coerceIn(0, 255).toByte()
        putU32(out, 8, command.id)
        putU32(out, 12, (command.happenedAtMillis / 1_000L).toInt())
        return out
    }

    @Suppress("ReturnCount")
    fun decodeCommand(bytes: ByteArray): WatchBleCommand? {
        if (bytes.size < COMMAND_SIZE) return null
        if (u8(bytes, 0) != MAGIC || u8(bytes, 1) != VERSION) return null
        val type = Type.entries.firstOrNull { it.code == u8(bytes, 2) } ?: return null
        val weight = u16(bytes, 4) / 10.0
        val reps = u8(bytes, 6)
        val bpm = u8(bytes, 7).takeIf { it > 0 }
        val id = u32(bytes, 8)
        val happenedSeconds = u32(bytes, 12).toLong()
        if (!weight.isFinite() || weight < 0.0 || weight > 500.0) return null
        if (reps > 255) return null
        if (type == Type.HEART_RATE &&
            (bpm == null || bpm !in WatchProtocol.MIN_HEART_RATE..WatchProtocol.MAX_HEART_RATE)
        ) {
            return null
        }
        if (happenedSeconds <= 0L) return null
        return WatchBleCommand(
            id = id,
            type = type,
            weightKg = weight,
            reps = reps,
            bpm = bpm,
            isWarmup = u8(bytes, 3) == 1,
            happenedAtMillis = happenedSeconds * 1_000L,
        )
    }

    fun encodeSnapshot(snapshot: WatchBleSnapshot): ByteArray {
        val out = ByteArray(SNAPSHOT_SIZE)
        out[0] = MAGIC.toByte()
        out[1] = VERSION.toByte()
        var flags = 0
        if (snapshot.sessionOpen) flags = flags or 0x01
        if (snapshot.restRunning) flags = flags or 0x02
        if (snapshot.connected) flags = flags or 0x04
        if (snapshot.weightUnit == "lb") flags = flags or 0x08
        out[2] = flags.toByte()
        putU16(out, 3, (snapshot.weightKg.coerceIn(0.0, 500.0) * 10.0).toInt())
        out[5] = snapshot.reps.coerceIn(0, 255).toByte()
        out[6] = snapshot.doneSets.coerceIn(0, 255).toByte()
        out[7] = snapshot.targetSets.coerceIn(0, 255).toByte()
        putU16(out, 8, snapshot.restRemainingSeconds.coerceIn(0, 65_535))
        putU16(out, 10, snapshot.restTotalSeconds.coerceIn(0, 65_535))
        putU16(out, 12, (snapshot.weightIncrementKg.coerceIn(0.0, 6.55) * 100.0).toInt())
        putU32(out, 14, snapshot.ackCommandId)
        out[18] = 0
        out[19] = 0
        return out
    }

    @Suppress("ReturnCount")
    fun decodeSnapshot(bytes: ByteArray): WatchBleSnapshot? {
        if (bytes.size < SNAPSHOT_SIZE) return null
        if (u8(bytes, 0) != MAGIC || u8(bytes, 1) != VERSION) return null
        val flags = u8(bytes, 2)
        val weight = u16(bytes, 3) / 10.0
        if (!weight.isFinite() || weight < 0.0 || weight > 500.0) return null
        return WatchBleSnapshot(
            sessionOpen = flags and 0x01 != 0,
            restRunning = flags and 0x02 != 0,
            connected = flags and 0x04 != 0,
            weightUnit = if (flags and 0x08 != 0) "lb" else "kg",
            weightKg = weight,
            reps = u8(bytes, 5),
            doneSets = u8(bytes, 6),
            targetSets = u8(bytes, 7),
            restRemainingSeconds = u16(bytes, 8),
            restTotalSeconds = u16(bytes, 10),
            weightIncrementKg = u16(bytes, 12) / 100.0,
            ackCommandId = u32(bytes, 14),
        )
    }

    private fun u8(bytes: ByteArray, index: Int): Int = bytes[index].toInt() and 0xFF

    private fun u16(bytes: ByteArray, index: Int): Int =
        (u8(bytes, index) shl 8) or u8(bytes, index + 1)

    private fun u32(bytes: ByteArray, index: Int): Int = (u8(bytes, index) shl 24) or
        (u8(bytes, index + 1) shl 16) or
        (u8(bytes, index + 2) shl 8) or
        u8(bytes, index + 3)

    private fun putU16(bytes: ByteArray, index: Int, value: Int) {
        val safe = value.coerceIn(0, 65_535)
        bytes[index] = (safe ushr 8).toByte()
        bytes[index + 1] = safe.toByte()
    }

    private fun putU32(bytes: ByteArray, index: Int, value: Int) {
        bytes[index] = (value ushr 24).toByte()
        bytes[index + 1] = (value ushr 16).toByte()
        bytes[index + 2] = (value ushr 8).toByte()
        bytes[index + 3] = value.toByte()
    }
}

data class WatchBleCommand(
    val id: Int,
    val type: WatchBlePacket.Type,
    val weightKg: Double,
    val reps: Int,
    val bpm: Int?,
    val isWarmup: Boolean,
    val happenedAtMillis: Long,
)

data class WatchBleSnapshot(
    val sessionOpen: Boolean,
    val restRunning: Boolean,
    val connected: Boolean,
    val weightUnit: String,
    val weightKg: Double,
    val reps: Int,
    val doneSets: Int,
    val targetSets: Int,
    val restRemainingSeconds: Int,
    val restTotalSeconds: Int,
    val weightIncrementKg: Double,
    val ackCommandId: Int,
)
