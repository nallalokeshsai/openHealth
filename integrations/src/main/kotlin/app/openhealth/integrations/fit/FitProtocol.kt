package app.openhealth.integrations.fit

import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * The FIT binary format, decoded from the published protocol documentation
 * (`FitProtocol.md`): a 12- or 14-byte header, a sequence of definition and data
 * messages, and a CRC. This is an independent implementation — no Garmin SDK code,
 * headers or sample files are used or bundled, because the FIT SDK licence is
 * source-available and its terms conflict with redistributing it in an open-source
 * client (`docs/adr/0013-activity-sources.md`, `NOTICE.md`).
 *
 * The decoder is deliberately complete about the container and narrow about meaning: it
 * decodes every field of every message into a typed value, and the importer decides what
 * the app stores. An unknown profile field never breaks an import.
 */

/** A field value as the file stores it, before any scale or offset is applied. */
sealed interface FitValue {
    data class Whole(val value: Long) : FitValue

    data class Decimal(val value: Double) : FitValue

    data class Text(val value: String) : FitValue

    /** A byte field, kept with its element count so an unknown array is not misread. */
    data class Blob(val bytes: ByteArray, val count: Int) : FitValue

    /** The base type's "invalid" sentinel: the field is absent, not zero. */
    data object Absent : FitValue

    fun whole(): Long? = (this as? Whole)?.value

    fun decimal(): Double? = when (this) {
        is Whole -> value.toDouble()
        is Decimal -> value
        else -> null
    }

    fun text(): String? = (this as? Text)?.value
}

data class FitMessage(
    val globalNumber: Int,
    val localNumber: Int,
    /** Seconds since the FIT epoch (1989-12-31T00:00:00Z), when the message has one. */
    val timestampSeconds: Long?,
    val fields: Map<Int, FitValue>,
) {
    fun whole(field: Int): Long? = fields[field]?.whole()

    fun decimal(field: Int): Double? = fields[field]?.decimal()

    fun text(field: Int): String? = fields[field]?.text()

    fun scaled(field: Int, scale: Double, offset: Double = 0.0): Double? {
        val raw = decimal(field) ?: return null
        return raw / scale + offset
    }
}

data class FitFile(
    val messages: List<FitMessage>,
    val profileVersion: Int,
    val protocolVersion: Int,
    val headerCrcValid: Boolean,
    val fileCrcValid: Boolean,
) {
    fun messagesOf(globalNumber: Int): List<FitMessage> =
        messages.filter { it.globalNumber == globalNumber }

    /** The file_id message: manufacturer, product and the creation time. */
    val fileId: FitMessage? get() = messagesOf(FitGlobal.FILE_ID).firstOrNull()
}

/** Thrown for a file that is not FIT or is truncated; never for an unknown field. */
class FitFormatException(message: String) : IllegalArgumentException(message)

/**
 * Global message numbers and field numbers used by the importer, from the published FIT
 * profile. Only the messages the app stores are named; everything else is decoded and
 * ignored.
 */
object FitGlobal {
    const val FILE_ID = 0
    const val SESSION = 18
    const val RECORD = 20
    const val ACTIVITY = 34
    const val MONITORING = 55
    const val MONITORING_INFO = 103

    const val TIMESTAMP_FIELD = 253

    object FileId {
        const val TYPE = 0
        const val MANUFACTURER = 1
        const val PRODUCT = 2
        const val TIME_CREATED = 4
        const val PRODUCT_NAME = 8
    }

    object Session {
        const val START_TIME = 2
        const val SPORT = 5
        const val SUB_SPORT = 6
        const val TOTAL_ELAPSED_TIME = 7
        const val TOTAL_TIMER_TIME = 8
        const val TOTAL_DISTANCE = 9
        const val TOTAL_CYCLES = 10
        const val TOTAL_CALORIES = 11
        const val AVG_HEART_RATE = 16
        const val MAX_HEART_RATE = 17
        const val AVG_CADENCE = 18
        const val SPORT_PROFILE_NAME = 110
    }

    object Record {
        const val ALTITUDE = 2
        const val HEART_RATE = 3
        const val CADENCE = 4
        const val DISTANCE = 5
        const val TOTAL_CYCLES = 19
        const val ENHANCED_ALTITUDE = 78
        const val WRIST_HEART_RATE = 136
        const val EXTERNAL_HEART_RATE = 144
        const val TIMESTAMP = 253
    }

    object Monitoring {
        const val CALORIES = 1
        const val DISTANCE = 2
        const val CYCLES = 3
        const val ACTIVE_TIME = 4
        const val ACTIVITY_TYPE = 5
        const val LOCAL_TIMESTAMP = 11
        const val CYCLES_16 = 9
        const val TIMESTAMP_16 = 26
        const val HEART_RATE = 27
        const val MODERATE_ACTIVITY_MINUTES = 33
        const val VIGOROUS_ACTIVITY_MINUTES = 34
        const val TIMESTAMP = 253
    }

    object Activity {
        const val TIMESTAMP = 253
        const val LOCAL_TIMESTAMP = 5
        const val NUM_SESSIONS = 1
    }
}

/** Activity type values in the `monitoring` message that count steps (published profile). */
object FitActivityTypes {
    const val RUNNING = 1
    const val WALKING = 6
    const val WHEELCHAIR_PUSHING = 13
    const val HIKING = 17

    /**
     * One cycle is one stride, and a stride is **two** steps, in both messages that
     * report cycles. The two messages scale their raw field differently, so each
     * conversion is spelled out where it is applied:
     *
     * - `monitoring.cycles` (55/3) is `uint32` with **scale 2**, units cycles
     *   ([MONITORING_CYCLES_SCALE]), so `steps = raw / 2 * 2 = raw`. The profile says
     *   the same thing about `cycles_16` (55/9), whose documented unit is literally
     *   "2 * cycles (steps)".
     * - `session.total_cycles` (18/10) has **no scale**, and for a walking or running
     *   sport its subfield is `total_strides`, so `steps = raw * 2`.
     *
     * Halving the raw value in either place loses half the day, which is why the two
     * scales are named rather than folded into one constant.
     */
    const val STEPS_PER_CYCLE = 2.0

    /** `monitoring.cycles` is stored at twice its value in cycles (profile scale 2). */
    const val MONITORING_CYCLES_SCALE = 2.0

    /**
     * A monitoring message's cycles are steps unless the activity it names is clearly not
     * ambulatory. An unlabelled message counts: a day file that leaves the type out is
     * still a day of walking, and refusing it silently loses the day. Cycling, swimming,
     * sleep, sedentary, driving and gym equipment are the explicit exclusions.
     */
    fun countsSteps(activityType: Long?): Boolean = when (activityType) {
        null -> true
        CYCLING, SWIMMING, SLEEP, SEDENTARY, UNMONITORED, DRIVING, TRANSITION,
        FITNESS_EQUIPMENT,
        -> false

        else -> true
    }

    private const val CYCLING = 2L
    private const val TRANSITION = 3L
    private const val FITNESS_EQUIPMENT = 4L
    private const val SWIMMING = 5L
    private const val SLEEP = 7L
    private const val SEDENTARY = 8L
    private const val UNMONITORED = 9L
    private const val DRIVING = 10L

    /** `session.sport` values that walk or run; a ride's cycles are not steps. */
    fun sportCountsSteps(sport: Long?): Boolean = sport != null &&
        sport in setOf(SPORT_RUNNING, SPORT_WALKING, SPORT_HIKING)

    private const val SPORT_RUNNING = 1L
    private const val SPORT_WALKING = 11L
    private const val SPORT_HIKING = 17L
}

object FitTime {
    /** The FIT epoch is 1989-12-31T00:00:00Z, 631065600 seconds after the Unix epoch. */
    const val EPOCH_OFFSET_SECONDS = 631_065_600L

    private val EPOCH = LocalDateTime(EPOCH_YEAR, EPOCH_MONTH, EPOCH_DAY, 0, 0, 0)

    private const val EPOCH_YEAR = 1989
    private const val EPOCH_MONTH = 12
    private const val EPOCH_DAY = 31

    fun unixSeconds(fitSeconds: Long): Long = EPOCH_OFFSET_SECONDS + fitSeconds

    /**
     * A `local_timestamp` counts seconds from the same epoch in the device's local time,
     * so it needs the zone to become an instant. Treating it as UTC would silently move a
     * day, which the rollup is built on.
     */
    fun localInstant(fitSeconds: Long, zone: TimeZone): kotlin.time.Instant =
        EPOCH.toInstant(zone) + fitSeconds.seconds

    /** The 5-bit field of a compressed-timestamp header; it rolls over every 32 s. */
    const val COMPRESSED_OFFSET_MASK = 0x1FL

    private const val COMPRESSED_ROLLOVER = 0x20L

    /**
     * Expands a compressed-timestamp header's 5-bit offset against the running
     * timestamp, as the protocol defines it: the offset replaces the low five bits, and
     * an offset that has wrapped below the current ones adds a 32-second rollover.
     *
     * Adding the offset to the base instead only agrees with this when the base happens
     * to sit on a 32-second boundary, so a file whose first timestamp is not a multiple
     * of 32 would drift by up to 31 seconds on every compressed record.
     */
    fun expandCompressed(baseFitSeconds: Long, offset: Int): Long {
        val masked = offset.toLong() and COMPRESSED_OFFSET_MASK
        val low = baseFitSeconds and COMPRESSED_OFFSET_MASK
        val rolled = if (masked >= low) 0L else COMPRESSED_ROLLOVER
        return (baseFitSeconds and COMPRESSED_OFFSET_MASK.inv()) + masked + rolled
    }

    /** `timestamp_16` keeps the low sixteen bits; it rolls over every 65,536 s. */
    const val TRUNCATED_16_MASK = 0xFFFFL

    private const val TRUNCATED_16_ROLLOVER = 0x1_0000L
    private const val TRUNCATED_16_HALF = 0x8000L

    /**
     * Expands a `timestamp_16` against the most recent full timestamp. The sixteen bits
     * name a time within half a rollover of that reference, so the answer is the nearest
     * one: the reference has to be the previous *resolved* time, not the first full
     * timestamp in the file. A monitoring file that carries one full timestamp and then
     * sixteen-bit stamps for a day would otherwise be misdated by 18 hours from the
     * nine-hour mark onwards.
     */
    fun expandTruncated16(referenceFitSeconds: Long, truncated: Long): Long {
        var candidate = (referenceFitSeconds and TRUNCATED_16_MASK.inv()) or
            (truncated and TRUNCATED_16_MASK)
        if (candidate < referenceFitSeconds - TRUNCATED_16_HALF) {
            candidate += TRUNCATED_16_ROLLOVER
        }
        if (candidate > referenceFitSeconds + TRUNCATED_16_HALF) {
            candidate -= TRUNCATED_16_ROLLOVER
        }
        return candidate
    }
}

/**
 * The streaming decoder. Definition messages are remembered per local message type, so a
 * data message is always read with the field layout that preceded it, including the
 * little- and big-endian forms and any developer fields.
 */
// A binary decoder is a table of readers, like the format it implements; the
// structural rules are waived here for the same reason the corpus generator waives them.
@Suppress("TooManyFunctions")
class FitDecoder(private val data: ByteArray) {
    private val definitions = HashMap<Int, FieldLayout>()
    private var lastTimestampSeconds: Long? = null

    fun decode(): FitFile {
        val headerSize = headerSize()
        val dataStart = headerSize
        val dataSize = readUInt32(DATA_SIZE_OFFSET, false)
        if (dataSize < 0 || dataStart + dataSize > data.size) {
            throw FitFormatException("declared data size $dataSize does not fit the file")
        }
        val dataEnd = dataStart + dataSize.toInt()
        val profileVersion = readUInt16(2, false)
        val protocolVersion = readUInt8(1)
        val headerCrcValid = headerSize == MIN_HEADER_SIZE ||
            crc16(data, 0, MIN_HEADER_SIZE) == readUInt16(MIN_HEADER_SIZE, false)
        val messages = mutableListOf<FitMessage>()
        var cursor = dataStart
        while (cursor < dataEnd) {
            cursor = readRecord(cursor, dataEnd, messages)
        }
        val fileCrcValid = if (dataEnd + CRC_BYTES <= data.size) {
            crc16(data, 0, dataEnd) == readUInt16(dataEnd, false)
        } else {
            true
        }
        return FitFile(
            messages = messages,
            profileVersion = profileVersion,
            protocolVersion = protocolVersion,
            headerCrcValid = headerCrcValid,
            fileCrcValid = fileCrcValid,
        )
    }

    @Suppress("ThrowsCount", "ReturnCount")
    private fun headerSize(): Int {
        if (data.size < MIN_HEADER_SIZE) {
            throw FitFormatException("a FIT file is at least $MIN_HEADER_SIZE bytes")
        }
        val size = readUInt8(0)
        if (size != MIN_HEADER_SIZE && size != MAX_HEADER_SIZE) {
            throw FitFormatException("unsupported FIT header size $size")
        }
        val magic = String(data, MAGIC_OFFSET, MAGIC_LENGTH, Charsets.US_ASCII)
        if (magic != MAGIC) throw FitFormatException("missing .FIT signature")
        return size
    }

    @Suppress("ReturnCount")
    private fun readRecord(start: Int, dataEnd: Int, out: MutableList<FitMessage>): Int {
        var cursor = start
        val header = readUInt8(cursor)
        cursor += 1
        val compressed = header and COMPRESSED_FLAG != 0
        if (compressed) {
            val localNumber = (header and COMPRESSED_LOCAL_MASK) ushr COMPRESSED_LOCAL_SHIFT
            val offset = header and COMPRESSED_OFFSET_MASK
            val layout = definitions[localNumber]
                ?: throw FitFormatException("compressed message without a definition")
            out += readData(localNumber, layout, cursor, dataEnd, compressedOffset = offset)
            return cursor + layout.byteSize
        }
        if (header and DEFINITION_FLAG != 0) {
            return readDefinition(header, cursor, dataEnd)
        }
        val localNumber = header and LOCAL_NUMBER_MASK
        val layout = definitions[localNumber]
            ?: throw FitFormatException("data message without a definition")
        out += readData(localNumber, layout, cursor, dataEnd)
        return cursor + layout.byteSize
    }

    @Suppress("ThrowsCount")
    private fun readDefinition(header: Int, start: Int, dataEnd: Int): Int {
        var cursor = start
        val localNumber = header and LOCAL_NUMBER_MASK
        if (cursor + DEFINITION_HEADER_BYTES > dataEnd) {
            throw FitFormatException("definition message truncated")
        }
        cursor += 1 // reserved
        val bigEndian = readUInt8(cursor) == BIG_ENDIAN_ARCHITECTURE
        cursor += 1
        val globalNumber = readUInt16(cursor, bigEndian)
        cursor += 2
        val fieldCount = readUInt8(cursor)
        cursor += 1
        val fields = mutableListOf<FieldDef>()
        repeat(fieldCount) {
            if (cursor + FIELD_DEFINITION_BYTES > dataEnd) {
                throw FitFormatException("field definition truncated")
            }
            fields += FieldDef(
                number = readUInt8(cursor),
                size = readUInt8(cursor + 1),
                baseType = readUInt8(cursor + 2),
            )
            cursor += FIELD_DEFINITION_BYTES
        }
        val developerFieldSizes = mutableListOf<Int>()
        if (header and DEVELOPER_FLAG != 0) {
            if (cursor + 1 > dataEnd) throw FitFormatException("developer count truncated")
            val developerFields = readUInt8(cursor)
            cursor += 1
            repeat(developerFields) {
                if (cursor + FIELD_DEFINITION_BYTES > dataEnd) {
                    throw FitFormatException("developer field definition truncated")
                }
                // field number, size, developer-data index. The size is how many bytes
                // the value occupies in every data message; a fixed one-byte skip
                // desynchronises the stream after the first developer field.
                developerFieldSizes += readUInt8(cursor + 1)
                cursor += FIELD_DEFINITION_BYTES
            }
        }
        definitions[localNumber] = FieldLayout(
            globalNumber = globalNumber,
            fields = fields,
            bigEndian = bigEndian,
            developerFieldSizes = developerFieldSizes,
        )
        return cursor
    }

    private fun readData(
        localNumber: Int,
        layout: FieldLayout,
        start: Int,
        dataEnd: Int,
        compressedOffset: Int? = null,
    ): FitMessage {
        var cursor = start
        val values = LinkedHashMap<Int, FitValue>()
        var timestamp: Long? = null
        // Every field the definition declares is present in the data, including field
        // 253 on the rare definition that keeps it under a compressed header. The
        // record's length is therefore always the definition's byte size, which is what
        // `readRecord` advances by: skipping a field here but not there would misalign
        // every record that follows.
        layout.fields.forEach { field ->
            if (cursor + field.size > dataEnd) throw FitFormatException("field value truncated")
            val value = decodeValue(field, cursor, layout.bigEndian)
            values[field.number] = value
            if (field.number == FitGlobal.TIMESTAMP_FIELD) timestamp = value.whole()
            cursor += field.size
        }
        // Developer field values follow the normal fields and are skipped, not guessed.
        cursor += layout.developerFieldSizes.sum()
        if (compressedOffset != null) {
            val base = lastTimestampSeconds
                ?: throw FitFormatException("compressed timestamp without a base time")
            timestamp = FitTime.expandCompressed(base, compressedOffset)
            values[FitGlobal.TIMESTAMP_FIELD] = FitValue.Whole(timestamp)
        }
        val stamped = timestamp
        if (stamped != null && stamped > 0) lastTimestampSeconds = stamped
        return FitMessage(
            globalNumber = layout.globalNumber,
            localNumber = localNumber,
            timestampSeconds = stamped,
            fields = values,
        )
    }

    @Suppress("CyclomaticComplexMethod")
    private fun decodeValue(field: FieldDef, at: Int, bigEndian: Boolean): FitValue {
        val type = field.baseType and BASE_TYPE_MASK
        val count = field.size / baseSize(type).coerceAtLeast(1)
        return when (type) {
            BASE_ENUM, BASE_UINT8 -> unsigned(readUInt8(at).toLong(), UINT8_INVALID)
            BASE_SINT8 -> signed(readUInt8(at).toByte().toLong(), SINT8_INVALID)
            BASE_UINT8Z -> readUInt8(at).takeIf { it != 0 }?.let { FitValue.Whole(it.toLong()) }
                ?: FitValue.Absent

            BASE_UINT16 -> unsigned(readUInt16(at, bigEndian).toLong(), UINT16_INVALID)
            BASE_SINT16 -> signed(readUInt16(at, bigEndian).toShort().toLong(), SINT16_INVALID)
            BASE_UINT16Z -> readUInt16(at, bigEndian).takeIf { it != 0 }
                ?.let { FitValue.Whole(it.toLong()) }
                ?: FitValue.Absent

            BASE_UINT32 -> unsigned(readUInt32(at, bigEndian), UINT32_INVALID)
            BASE_SINT32 -> signed(readUInt32(at, bigEndian).toInt().toLong(), SINT32_INVALID)
            BASE_UINT32Z -> readUInt32(at, bigEndian).takeIf { it != 0L }
                ?.let(FitValue::Whole)
                ?: FitValue.Absent

            BASE_UINT64 -> readUInt64(at, bigEndian).takeIf { it != UINT64_INVALID }
                ?.let(FitValue::Whole)
                ?: FitValue.Absent

            BASE_UINT64Z -> readUInt64(at, bigEndian).takeIf { it != 0L }
                ?.let(FitValue::Whole)
                ?: FitValue.Absent

            BASE_SINT64 -> readUInt64(at, bigEndian).takeIf { it != SINT64_INVALID }
                ?.let(FitValue::Whole)
                ?: FitValue.Absent

            BASE_FLOAT32 -> {
                val bits = readUInt32(at, bigEndian)
                if (bits == FLOAT32_INVALID) {
                    FitValue.Absent
                } else {
                    FitValue.Decimal(Float.fromBits(bits.toInt()).toDouble())
                }
            }

            BASE_FLOAT64 -> {
                val bits = readUInt64(at, bigEndian)
                if (bits == FLOAT64_INVALID) {
                    FitValue.Absent
                } else {
                    FitValue.Decimal(Double.fromBits(bits))
                }
            }

            BASE_STRING -> FitValue.Text(readString(at, field.size))
            BASE_BYTE -> FitValue.Blob(data.copyOfRange(at, at + field.size), count)
            else -> FitValue.Absent
        }
    }

    private fun unsigned(raw: Long, invalid: Long): FitValue =
        if (raw == invalid) FitValue.Absent else FitValue.Whole(raw)

    private fun signed(raw: Long, invalid: Long): FitValue =
        if (raw == invalid) FitValue.Absent else FitValue.Whole(raw)

    private fun baseSize(type: Int): Int = when (type) {
        BASE_ENUM, BASE_UINT8, BASE_SINT8, BASE_UINT8Z, BASE_BYTE -> SIZE_8
        BASE_UINT16, BASE_SINT16, BASE_UINT16Z -> SIZE_16
        BASE_UINT32, BASE_SINT32, BASE_UINT32Z, BASE_FLOAT32 -> SIZE_32
        BASE_UINT64, BASE_UINT64Z, BASE_SINT64, BASE_FLOAT64 -> SIZE_64
        else -> SIZE_8
    }

    private fun readString(at: Int, size: Int): String {
        val end = (at until at + size).firstOrNull { data[it] == ZERO_BYTE } ?: (at + size)
        return String(data, at, end - at, Charsets.UTF_8)
    }

    private fun readUInt8(at: Int): Int {
        if (at !in data.indices) throw FitFormatException("read past the end of the file")
        return data[at].toInt() and UINT8_MASK
    }

    private fun readUInt16(at: Int, bigEndian: Boolean): Int {
        val a = readUInt8(at)
        val b = readUInt8(at + 1)
        return if (bigEndian) (a shl BYTE_SHIFT) or b else a or (b shl BYTE_SHIFT)
    }

    private fun readUInt32(at: Int, bigEndian: Boolean): Long {
        val bytes = (0 until SIZE_32).map { readUInt8(at + it).toLong() }
        val ordered = if (bigEndian) bytes else bytes.reversed()
        return ordered.fold(0L) { acc, byte -> (acc shl BYTE_SHIFT) or byte } and UINT32_MASK
    }

    private fun readUInt64(at: Int, bigEndian: Boolean): Long {
        val bytes = (0 until SIZE_64).map { readUInt8(at + it).toLong() }
        val ordered = if (bigEndian) bytes else bytes.reversed()
        return ordered.fold(0L) { acc, byte -> (acc shl BYTE_SHIFT) or byte }
    }

    private companion object {
        const val UINT8_MASK = 0xFF
        const val COMPRESSED_FLAG = 0x80
        const val DEFINITION_FLAG = 0x40
        const val DEVELOPER_FLAG = 0x20
        const val COMPRESSED_LOCAL_MASK = 0x60
        const val COMPRESSED_LOCAL_SHIFT = 5
        const val COMPRESSED_OFFSET_MASK = 0x1F
        const val LOCAL_NUMBER_MASK = 0x0F
        const val BASE_TYPE_MASK = 0x1F
        const val BIG_ENDIAN_ARCHITECTURE = 1
        const val MIN_HEADER_SIZE = 12
        const val MAX_HEADER_SIZE = 14
        const val CRC_BYTES = 2
        const val DATA_SIZE_OFFSET = 4
        const val MAGIC_OFFSET = 8
        const val MAGIC_LENGTH = 4
        const val MAGIC = ".FIT"
        const val DEFINITION_HEADER_BYTES = 5
        const val FIELD_DEFINITION_BYTES = 3
        const val ZERO_BYTE = 0.toByte()

        const val BASE_ENUM = 0x00
        const val BASE_SINT8 = 0x01
        const val BASE_UINT8 = 0x02
        const val BASE_SINT16 = 0x03
        const val BASE_UINT16 = 0x04
        const val BASE_SINT32 = 0x05
        const val BASE_UINT32 = 0x06
        const val BASE_STRING = 0x07
        const val BASE_FLOAT32 = 0x08
        const val BASE_FLOAT64 = 0x09
        const val BASE_UINT8Z = 0x0A
        const val BASE_UINT16Z = 0x0B
        const val BASE_UINT32Z = 0x0C
        const val BASE_BYTE = 0x0D
        const val BASE_SINT64 = 0x0E
        const val BASE_UINT64 = 0x0F
        const val BASE_UINT64Z = 0x10

        const val BYTE_SHIFT = 8
        const val SIZE_8 = 1
        const val SIZE_16 = 2
        const val SIZE_32 = 4
        const val SIZE_64 = 8

        const val UINT8_INVALID = 0xFFL
        const val SINT8_INVALID = 0x7FL
        const val UINT16_INVALID = 0xFFFFL
        const val SINT16_INVALID = 0x7FFFL
        const val UINT32_INVALID = 0xFFFFFFFFL
        const val SINT32_INVALID = 0x7FFFFFFFL
        const val UINT64_INVALID = -1L
        const val SINT64_INVALID = Long.MAX_VALUE
        const val FLOAT32_INVALID = 0xFFFFFFFFL
        const val FLOAT64_INVALID = -1L
        const val UINT32_MASK = 0xFFFFFFFFL

        val CRC_TABLE = intArrayOf(
            0x0000, 0xCC01, 0xD801, 0x1400, 0xF001, 0x3C00, 0x2800, 0xE401,
            0xA001, 0x6C00, 0x7800, 0xB401, 0x5000, 0x9C01, 0x8801, 0x4400,
        )

        /** The FIT CRC-16, as the protocol documents it (nibble table, low byte first). */
        fun crc16(bytes: ByteArray, from: Int, to: Int): Int {
            var crc = 0
            for (index in from until to) {
                val byte = bytes[index].toInt() and UINT8_MASK
                var tmp = CRC_TABLE[crc and NIBBLE_MASK]
                crc = (crc shr NIBBLE_BITS) and CRC_HIGH_MASK
                crc = crc xor tmp xor CRC_TABLE[byte and NIBBLE_MASK]
                tmp = CRC_TABLE[crc and NIBBLE_MASK]
                crc = (crc shr NIBBLE_BITS) and CRC_HIGH_MASK
                crc = crc xor tmp xor CRC_TABLE[(byte shr NIBBLE_BITS) and NIBBLE_MASK]
            }
            return crc and UINT16_INVALID.toInt()
        }

        const val NIBBLE_MASK = 0x0F
        const val NIBBLE_BITS = 4
        const val CRC_HIGH_MASK = 0x0FFF
    }
}

private data class FieldDef(val number: Int, val size: Int, val baseType: Int)

private data class FieldLayout(
    val globalNumber: Int,
    val fields: List<FieldDef>,
    val bigEndian: Boolean,
    val developerFieldSizes: List<Int>,
) {
    val byteSize: Int = fields.sumOf(FieldDef::size) + developerFieldSizes.sum()
}
