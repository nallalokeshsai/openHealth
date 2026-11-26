package app.openhealth.tools.fixtures

/**
 * A minimal FIT writer, used only to produce the golden fixtures that `:integrations`
 * parses (`BUILD-ROADMAP.md` §B8, `Tech-Stack.md` §10). It is written from the published
 * protocol, independently of the decoder, so a fixture that round-trips proves the
 * parser against a second implementation rather than against itself.
 *
 * No Garmin SDK code or sample files are used: the FIT SDK licence is source-available
 * and cannot be redistributed in this Apache-2.0 repository (`NOTICE.md`).
 */
@Suppress("TooManyFunctions")
class FitWriter(
    private val profileVersion: Int = PROFILE_VERSION,
    private val protocolVersion: Int = PROTOCOL_VERSION,
    private val bigEndian: Boolean = false,
) {
    data class FieldSpec(val number: Int, val size: Int, val baseType: Int)

    private data class Definition(val globalNumber: Int, val fields: List<FieldSpec>)

    private val definitions = LinkedHashMap<Int, Definition>()
    private val body = java.io.ByteArrayOutputStream()

    fun define(localType: Int, globalNumber: Int, fields: List<FieldSpec>) {
        definitions[localType] = Definition(globalNumber, fields)
        val header = DEFINITION_FLAG or localType
        body.write(header)
        body.write(0) // reserved
        body.write(if (bigEndian) 1 else 0)
        writeUInt16(globalNumber, bigEndian)
        body.write(fields.size)
        fields.forEach { field ->
            body.write(field.number)
            body.write(field.size)
            body.write(field.baseType)
        }
    }

    fun data(localType: Int, values: Map<Int, Any>) {
        val definition = definitions[localType]
            ?: error("local type $localType has no definition")
        body.write(localType)
        definition.fields.forEach { field ->
            writeValue(field, values[field.number])
        }
    }

    /**
     * A data message with the compressed-timestamp header: the 5-bit offset in the
     * header carries the time, which is why such a message is defined **without** field
     * 253. The body is every field the definition declares, exactly as a normal data
     * message is, so the record's length on disk is the definition's byte size.
     */
    fun compressedData(localType: Int, timeOffset: Int, values: Map<Int, Any>) {
        val definition = definitions[localType]
            ?: error("local type $localType has no definition")
        require(definition.fields.none { it.number == TIMESTAMP_FIELD }) {
            "a compressed-timestamp message takes its time from the header, so local " +
                "type $localType must be defined without field $TIMESTAMP_FIELD"
        }
        body.write(
            COMPRESSED_FLAG or (localType shl COMPRESSED_LOCAL_SHIFT) or
                (timeOffset and COMPRESSED_OFFSET_MASK),
        )
        definition.fields.forEach { field ->
            writeValue(field, values[field.number])
        }
    }

    fun toByteArray(): ByteArray {
        val data = body.toByteArray()
        val header = java.io.ByteArrayOutputStream()
        header.write(MAX_HEADER_SIZE)
        header.write(protocolVersion)
        writeUInt16(profileVersion, false, header)
        writeUInt32(data.size.toLong(), false, header)
        header.write(".FIT".toByteArray(Charsets.US_ASCII))
        val headerBytes = header.toByteArray()
        val crc = crc16(headerBytes, 0, headerBytes.size)
        val output = java.io.ByteArrayOutputStream()
        output.write(headerBytes)
        writeUInt16(crc, false, output)
        output.write(data)
        // The file CRC covers the header (including its own CRC field) and the data.
        val fileCrc = crc16(output.toByteArray(), 0, headerBytes.size + CRC_BYTES + data.size)
        writeUInt16(fileCrc, false, output)
        return output.toByteArray()
    }

    private fun writeValue(field: FieldSpec, value: Any?) {
        val type = field.baseType
        when (type) {
            BASE_ENUM, BASE_UINT8 -> body.write(intValue(value, UINT8_INVALID))
            BASE_UINT8Z -> body.write(intValue(value, 0))
            BASE_SINT8 -> body.write(intValue(value, SINT8_INVALID))
            BASE_UINT16, BASE_UINT16Z ->
                writeUInt16(intValue(value, UINT16_INVALID), bigEndian)

            BASE_SINT16 -> writeUInt16(intValue(value, SINT16_INVALID), bigEndian)
            BASE_UINT32, BASE_UINT32Z ->
                writeUInt32(longValue(value, UINT32_INVALID), bigEndian)

            BASE_SINT32 -> writeUInt32(longValue(value, SINT32_INVALID), bigEndian)
            BASE_UINT64, BASE_UINT64Z -> writeUInt64(longValue(value, UINT64_INVALID))
            BASE_SINT64 -> writeUInt64(longValue(value, SINT64_INVALID))
            BASE_STRING -> {
                val text = (value as? String).orEmpty().toByteArray(Charsets.UTF_8)
                val padded = ByteArray(field.size) { 0 }
                text.copyInto(padded, endIndex = minOf(text.size, field.size))
                body.write(padded)
            }

            BASE_FLOAT32 -> writeUInt32(floatBits(value), bigEndian)
            else -> error("unsupported fixture base type $type")
        }
    }

    private fun intValue(value: Any?, invalid: Int): Int = when (value) {
        null -> invalid
        is Int -> value
        is Long -> value.toInt()
        is Boolean -> if (value) 1 else 0
        else -> error("expected an integer field value, was $value")
    }

    private fun longValue(value: Any?, invalid: Long): Long = when (value) {
        null -> invalid
        is Int -> value.toLong()
        is Long -> value
        is kotlin.time.Instant -> value.epochSeconds - FIT_EPOCH_OFFSET
        else -> error("expected a long field value, was $value")
    }

    private fun floatBits(value: Any?): Long = when (value) {
        null -> UINT32_INVALID
        is Float -> value.toRawBits().toLong() and UINT32_MASK
        is Double -> value.toFloat().toRawBits().toLong() and UINT32_MASK
        else -> error("expected a float field value, was $value")
    }

    private fun writeUInt16(
        value: Int,
        bigEndian: Boolean,
        out: java.io.ByteArrayOutputStream = body,
    ) {
        if (bigEndian) {
            out.write((value shr 8) and 0xFF)
            out.write(value and 0xFF)
        } else {
            out.write(value and 0xFF)
            out.write((value shr 8) and 0xFF)
        }
    }

    private fun writeUInt32(
        value: Long,
        bigEndian: Boolean,
        out: java.io.ByteArrayOutputStream = body,
    ) {
        if (bigEndian) {
            for (shift in intArrayOf(24, 16, 8, 0)) out.write(((value shr shift) and 0xFF).toInt())
        } else {
            for (shift in intArrayOf(0, 8, 16, 24)) out.write(((value shr shift) and 0xFF).toInt())
        }
    }

    private fun writeUInt64(value: Long) {
        for (shift in intArrayOf(0, 8, 16, 24, 32, 40, 48, 56)) {
            body.write(((value shr shift) and 0xFF).toInt())
        }
    }

    companion object {
        const val PROFILE_VERSION = 2132
        const val PROTOCOL_VERSION = 0x20
        const val FIT_EPOCH_OFFSET = 631_065_600L

        const val BASE_ENUM = 0x00
        const val BASE_SINT8 = 0x01
        const val BASE_UINT8 = 0x02
        const val BASE_SINT16 = 0x03
        const val BASE_UINT16 = 0x04
        const val BASE_SINT32 = 0x05
        const val BASE_UINT32 = 0x06
        const val BASE_STRING = 0x07
        const val BASE_FLOAT32 = 0x08
        const val BASE_UINT8Z = 0x0A
        const val BASE_UINT16Z = 0x0B
        const val BASE_UINT32Z = 0x0C
        const val BASE_SINT64 = 0x0E
        const val BASE_UINT64 = 0x0F
        const val BASE_UINT64Z = 0x10

        private const val DEFINITION_FLAG = 0x40
        private const val COMPRESSED_FLAG = 0x80
        private const val COMPRESSED_LOCAL_SHIFT = 5
        private const val COMPRESSED_OFFSET_MASK = 0x1F
        private const val TIMESTAMP_FIELD = 253
        private const val MAX_HEADER_SIZE = 14
        private const val CRC_BYTES = 2
        private const val UINT8_INVALID = 0xFF
        private const val SINT8_INVALID = 0x7F
        private const val UINT16_INVALID = 0xFFFF
        private const val SINT16_INVALID = 0x7FFF
        private const val UINT32_INVALID = 0xFFFFFFFFL
        private const val UINT32_MASK = 0xFFFFFFFFL
        private const val SINT32_INVALID = 0x7FFFFFFFL
        private const val UINT64_INVALID = -1L
        private const val SINT64_INVALID = Long.MAX_VALUE

        private val CRC_TABLE = intArrayOf(
            0x0000, 0xCC01, 0xD801, 0x1400, 0xF001, 0x3C00, 0x2800, 0xE401,
            0xA001, 0x6C00, 0x7800, 0xB401, 0x5000, 0x9C01, 0x8801, 0x4400,
        )

        /** The same published CRC-16; written independently of the decoder's copy. */
        fun crc16(bytes: ByteArray, from: Int, to: Int): Int {
            var crc = 0
            for (index in from until to) {
                val byte = bytes[index].toInt() and 0xFF
                var tmp = CRC_TABLE[crc and 0x0F]
                crc = (crc shr 4) and 0x0FFF
                crc = crc xor tmp xor CRC_TABLE[byte and 0x0F]
                tmp = CRC_TABLE[crc and 0x0F]
                crc = (crc shr 4) and 0x0FFF
                crc = crc xor tmp xor CRC_TABLE[(byte shr 4) and 0x0F]
            }
            return crc and 0xFFFF
        }
    }

    /** Field definitions for the messages the fixtures use. */
    object Fields {
        val FILE_ID = listOf(
            FieldSpecNumbers.FILE_ID_TYPE to BASE_ENUM,
            FieldSpecNumbers.FILE_ID_MANUFACTURER to BASE_UINT16,
            FieldSpecNumbers.FILE_ID_PRODUCT to BASE_UINT16,
            FieldSpecNumbers.FILE_ID_TIME_CREATED to BASE_UINT32,
            FieldSpecNumbers.FILE_ID_PRODUCT_NAME to BASE_STRING,
        )

        val SESSION = listOf(
            FieldSpecNumbers.SESSION_START_TIME to BASE_UINT32,
            FieldSpecNumbers.SESSION_SPORT to BASE_ENUM,
            FieldSpecNumbers.SESSION_SUB_SPORT to BASE_ENUM,
            FieldSpecNumbers.SESSION_TOTAL_ELAPSED to BASE_UINT32,
            FieldSpecNumbers.SESSION_TOTAL_DISTANCE to BASE_UINT32,
            FieldSpecNumbers.SESSION_TOTAL_CYCLES to BASE_UINT32,
            FieldSpecNumbers.SESSION_TOTAL_CALORIES to BASE_UINT16,
            FieldSpecNumbers.SESSION_AVG_HR to BASE_UINT8,
            FieldSpecNumbers.SESSION_MAX_HR to BASE_UINT8,
        )

        val RECORD = listOf(
            FieldSpecNumbers.RECORD_TIMESTAMP to BASE_UINT32,
            FieldSpecNumbers.RECORD_HEART_RATE to BASE_UINT8,
            FieldSpecNumbers.RECORD_DISTANCE to BASE_UINT32,
        )

        /** A record with position and the sensors an outdoor activity carries (B11). */
        val RECORD_ROUTE = listOf(
            FieldSpecNumbers.RECORD_TIMESTAMP to BASE_UINT32,
            FieldSpecNumbers.RECORD_LATITUDE to BASE_SINT32,
            FieldSpecNumbers.RECORD_LONGITUDE to BASE_SINT32,
            FieldSpecNumbers.RECORD_ALTITUDE to BASE_UINT16,
            FieldSpecNumbers.RECORD_HEART_RATE to BASE_UINT8,
            FieldSpecNumbers.RECORD_CADENCE to BASE_UINT8,
            FieldSpecNumbers.RECORD_DISTANCE to BASE_UINT32,
            FieldSpecNumbers.RECORD_SPEED to BASE_UINT16,
            FieldSpecNumbers.RECORD_POWER to BASE_UINT16,
        )

        /** The same record without field 253, for compressed-timestamp messages. */
        val RECORD_TIMED_BY_HEADER = RECORD.filterNot {
            it.first == FieldSpecNumbers.RECORD_TIMESTAMP
        }

        val MONITORING = listOf(
            FieldSpecNumbers.MONITORING_TIMESTAMP to BASE_UINT32,
            FieldSpecNumbers.MONITORING_LOCAL_TIMESTAMP to BASE_UINT32,
            FieldSpecNumbers.MONITORING_ACTIVITY_TYPE to BASE_ENUM,
            FieldSpecNumbers.MONITORING_CYCLES to BASE_UINT32,
            FieldSpecNumbers.MONITORING_CALORIES to BASE_UINT16,
        )

        /** `monitoring_info`: the full timestamp a day file states once, up front. */
        val MONITORING_INFO = listOf(
            FieldSpecNumbers.MONITORING_TIMESTAMP to BASE_UINT32,
        )

        /**
         * The abbreviated monitoring message a watch actually writes for a day: no full
         * timestamp, a 16-bit one that is resolved against the running time, and the
         * 16-bit cycle counter beside it.
         */
        val MONITORING_TIMED_16 = listOf(
            FieldSpecNumbers.MONITORING_TIMESTAMP_16 to BASE_UINT16,
            FieldSpecNumbers.MONITORING_ACTIVITY_TYPE to BASE_ENUM,
            FieldSpecNumbers.MONITORING_CYCLES_16 to BASE_UINT16,
        )

        fun specs(pairs: List<Pair<Int, Int>>): List<FieldSpec> = pairs.map { (number, type) ->
            val size = when (type) {
                BASE_ENUM, BASE_UINT8, BASE_SINT8, BASE_UINT8Z -> 1
                BASE_UINT16, BASE_SINT16, BASE_UINT16Z -> 2
                BASE_STRING -> 16
                else -> 4
            }
            FieldSpec(number, size, type)
        }
    }

    object FieldSpecNumbers {
        const val FILE_ID_TYPE = 0
        const val FILE_ID_MANUFACTURER = 1
        const val FILE_ID_PRODUCT = 2
        const val FILE_ID_TIME_CREATED = 4
        const val FILE_ID_PRODUCT_NAME = 8

        const val SESSION_START_TIME = 2
        const val SESSION_SPORT = 5
        const val SESSION_SUB_SPORT = 6
        const val SESSION_TOTAL_ELAPSED = 7
        const val SESSION_TOTAL_DISTANCE = 9
        const val SESSION_TOTAL_CYCLES = 10
        const val SESSION_TOTAL_CALORIES = 11
        const val SESSION_AVG_HR = 16
        const val SESSION_MAX_HR = 17

        const val RECORD_TIMESTAMP = 253
        const val RECORD_HEART_RATE = 3
        const val RECORD_DISTANCE = 5
        const val RECORD_LATITUDE = 0
        const val RECORD_LONGITUDE = 1
        const val RECORD_ALTITUDE = 2
        const val RECORD_CADENCE = 4
        const val RECORD_SPEED = 6
        const val RECORD_POWER = 7

        const val MONITORING_TIMESTAMP = 253
        const val MONITORING_LOCAL_TIMESTAMP = 11
        const val MONITORING_ACTIVITY_TYPE = 5
        const val MONITORING_CYCLES = 3
        const val MONITORING_CALORIES = 1
        const val MONITORING_CYCLES_16 = 9
        const val MONITORING_TIMESTAMP_16 = 26
    }

    /** Global message numbers, named so a fixture reads like the message it writes. */
    object Numbers {
        const val GLOBAL_FILE_ID = 0
        const val GLOBAL_SESSION = 18
        const val GLOBAL_RECORD = 20
        const val GLOBAL_MONITORING = 55
        const val GLOBAL_MONITORING_INFO = 103
    }
}

/** Convenience wrappers so a fixture reads like the data it encodes. */
object FitFixtures {
    const val GARMIN_MANUFACTURER = 1
    const val VIVOACTIVE_5_PRODUCT = 4340
    const val SPORT_WALKING = 11
    const val SPORT_RUNNING = 1
    const val ACTIVITY_TYPE_WALKING = 6
    const val ACTIVITY_TYPE_RUNNING = 1
    const val ACTIVITY_TYPE_SLEEP = 7

    /** 08:00 on 2026-11-23 in Unix seconds, the morning the fixtures describe. */
    const val DAY_START_UNIX_SECONDS = 1_795_420_800L

    /** The same moment in FIT seconds (the FIT epoch is 631,065,600 s after Unix). */
    const val DAY_START_SECONDS = DAY_START_UNIX_SECONDS - FitWriter.FIT_EPOCH_OFFSET

    /** A 45-minute walk with a heart-rate series and 3,000 steps. */
    fun activityWalk(): ByteArray {
        val writer = FitWriter()
        writer.define(
            localType = 0,
            globalNumber = FitWriter.Numbers.GLOBAL_FILE_ID,
            fields = FitWriter.Fields.specs(FitWriter.Fields.FILE_ID),
        )
        writer.data(
            0,
            mapOf(
                FitWriter.FieldSpecNumbers.FILE_ID_TYPE to 4,
                FitWriter.FieldSpecNumbers.FILE_ID_MANUFACTURER to GARMIN_MANUFACTURER,
                FitWriter.FieldSpecNumbers.FILE_ID_PRODUCT to VIVOACTIVE_5_PRODUCT,
                FitWriter.FieldSpecNumbers.FILE_ID_TIME_CREATED to DAY_START_SECONDS,
                FitWriter.FieldSpecNumbers.FILE_ID_PRODUCT_NAME to "vivoactive 5",
            ),
        )
        writer.define(
            localType = 1,
            globalNumber = FitWriter.Numbers.GLOBAL_RECORD,
            fields = FitWriter.Fields.specs(FitWriter.Fields.RECORD),
        )
        val start = DAY_START_SECONDS + WALK_START_OFFSET_SECONDS
        repeat(WALK_MINUTES) { minute ->
            writer.data(
                1,
                mapOf(
                    FitWriter.FieldSpecNumbers.RECORD_TIMESTAMP to
                        start + minute * SECONDS_PER_MINUTE,
                    FitWriter.FieldSpecNumbers.RECORD_HEART_RATE to
                        WALK_START_HEART_RATE + minute / HEART_RATE_STEP_MINUTES,
                    FitWriter.FieldSpecNumbers.RECORD_DISTANCE to
                        minute * DISTANCE_PER_MINUTE_CENTIMETRES,
                ),
            )
        }
        writer.define(
            localType = 2,
            globalNumber = FitWriter.Numbers.GLOBAL_SESSION,
            fields = FitWriter.Fields.specs(FitWriter.Fields.SESSION),
        )
        writer.data(
            2,
            mapOf(
                FitWriter.FieldSpecNumbers.SESSION_START_TIME to start,
                FitWriter.FieldSpecNumbers.SESSION_SPORT to SPORT_WALKING,
                FitWriter.FieldSpecNumbers.SESSION_SUB_SPORT to 0,
                FitWriter.FieldSpecNumbers.SESSION_TOTAL_ELAPSED to
                    WALK_MINUTES * SECONDS_PER_MINUTE * MILLIS_PER_SECOND,
                FitWriter.FieldSpecNumbers.SESSION_TOTAL_DISTANCE to
                    WALK_MINUTES * DISTANCE_PER_MINUTE_CENTIMETRES,
                // `session.total_cycles` is `total_strides` for a walk, and a stride
                // is two steps, so the file stores half the step count.
                FitWriter.FieldSpecNumbers.SESSION_TOTAL_CYCLES to WALK_STEPS / STEPS_PER_CYCLE,
                FitWriter.FieldSpecNumbers.SESSION_TOTAL_CALORIES to WALK_CALORIES,
                FitWriter.FieldSpecNumbers.SESSION_AVG_HR to WALK_AVG_HEART_RATE,
                FitWriter.FieldSpecNumbers.SESSION_MAX_HR to WALK_MAX_HEART_RATE,
            ),
        )
        return writer.toByteArray()
    }

    /**
     * A 10-minute outdoor run with a route, altitude, cadence, speed and power (B11).
     * Twenty record messages at 30 s intervals; the position moves north by 0.0005
     * degrees each step, about 55 m, for a little over a kilometre.
     */
    fun activityRoute(): ByteArray {
        val writer = FitWriter()
        writer.define(
            localType = 0,
            globalNumber = FitWriter.Numbers.GLOBAL_FILE_ID,
            fields = FitWriter.Fields.specs(FitWriter.Fields.FILE_ID),
        )
        writer.data(
            0,
            mapOf(
                FitWriter.FieldSpecNumbers.FILE_ID_TYPE to 4,
                FitWriter.FieldSpecNumbers.FILE_ID_MANUFACTURER to GARMIN_MANUFACTURER,
                FitWriter.FieldSpecNumbers.FILE_ID_PRODUCT to VIVOACTIVE_5_PRODUCT,
                FitWriter.FieldSpecNumbers.FILE_ID_TIME_CREATED to DAY_START_SECONDS,
                FitWriter.FieldSpecNumbers.FILE_ID_PRODUCT_NAME to "vivoactive 5",
            ),
        )
        writer.define(
            localType = 1,
            globalNumber = FitWriter.Numbers.GLOBAL_RECORD,
            fields = FitWriter.Fields.specs(FitWriter.Fields.RECORD_ROUTE),
        )
        val start = DAY_START_SECONDS + ROUTE_START_OFFSET_SECONDS
        repeat(ROUTE_POINTS) { index ->
            val metres = index * ROUTE_METRES_PER_STEP
            writer.data(
                1,
                mapOf(
                    FitWriter.FieldSpecNumbers.RECORD_TIMESTAMP to
                        start + index * ROUTE_SECONDS_PER_STEP,
                    FitWriter.FieldSpecNumbers.RECORD_LATITUDE to
                        semicircles(52.5200 + index * 0.0005),
                    FitWriter.FieldSpecNumbers.RECORD_LONGITUDE to
                        semicircles(13.4050 + index * 0.0001),
                    FitWriter.FieldSpecNumbers.RECORD_ALTITUDE to (34.0 * 5).toInt() + index,
                    FitWriter.FieldSpecNumbers.RECORD_HEART_RATE to 150 + index / 5,
                    FitWriter.FieldSpecNumbers.RECORD_CADENCE to 84,
                    FitWriter.FieldSpecNumbers.RECORD_DISTANCE to (metres * 100).toLong(),
                    FitWriter.FieldSpecNumbers.RECORD_SPEED to ROUTE_SPEED_MPS * 1000,
                    FitWriter.FieldSpecNumbers.RECORD_POWER to 240 + index,
                ),
            )
        }
        writer.define(
            localType = 2,
            globalNumber = FitWriter.Numbers.GLOBAL_SESSION,
            fields = FitWriter.Fields.specs(FitWriter.Fields.SESSION),
        )
        writer.data(
            2,
            mapOf(
                FitWriter.FieldSpecNumbers.SESSION_START_TIME to start,
                FitWriter.FieldSpecNumbers.SESSION_SPORT to SPORT_RUNNING,
                FitWriter.FieldSpecNumbers.SESSION_SUB_SPORT to 0,
                FitWriter.FieldSpecNumbers.SESSION_TOTAL_ELAPSED to
                    ROUTE_DURATION_SECONDS * MILLIS_PER_SECOND,
                FitWriter.FieldSpecNumbers.SESSION_TOTAL_DISTANCE to
                    (ROUTE_DISTANCE_METRES * 100).toLong(),
                FitWriter.FieldSpecNumbers.SESSION_TOTAL_CALORIES to 92,
                FitWriter.FieldSpecNumbers.SESSION_AVG_HR to 152,
                FitWriter.FieldSpecNumbers.SESSION_MAX_HR to 168,
            ),
        )
        return writer.toByteArray()
    }

    private const val ROUTE_START_OFFSET_SECONDS = 18L * 60 * 60
    private const val ROUTE_POINTS = 20
    private const val ROUTE_SECONDS_PER_STEP = 30L
    private const val ROUTE_METRES_PER_STEP = 55.0
    private const val ROUTE_DISTANCE_METRES = ROUTE_METRES_PER_STEP * (ROUTE_POINTS - 1)
    private const val ROUTE_DURATION_SECONDS = ROUTE_SECONDS_PER_STEP * (ROUTE_POINTS - 1)
    private const val ROUTE_SPEED_MPS = 1

    private const val SEMICIRCLES_PER_DEGREE = 11_930_464.711111111

    private fun semicircles(degrees: Double): Long = (degrees * SEMICIRCLES_PER_DEGREE).toLong()

    /**
     * A daily monitoring file: quarter-hour buckets from 08:00, each carrying one
     * bucket's worth of steps while walking, plus a sleeping stretch with no steps.
     *
     * `monitoring.cycles` is written as the published profile stores it: scale 2 takes
     * the raw field to cycles and a walking cycle is two steps, so the raw value is the
     * step count. The expected total is derived from these constants by the test, not
     * copy-pasted.
     */
    fun dailyMonitoring(): ByteArray {
        val writer = FitWriter()
        writer.define(
            localType = 0,
            globalNumber = FitWriter.Numbers.GLOBAL_FILE_ID,
            fields = FitWriter.Fields.specs(FitWriter.Fields.FILE_ID),
        )
        writer.data(
            0,
            mapOf(
                FitWriter.FieldSpecNumbers.FILE_ID_TYPE to 4,
                FitWriter.FieldSpecNumbers.FILE_ID_MANUFACTURER to GARMIN_MANUFACTURER,
                FitWriter.FieldSpecNumbers.FILE_ID_PRODUCT to VIVOACTIVE_5_PRODUCT,
                FitWriter.FieldSpecNumbers.FILE_ID_TIME_CREATED to DAY_START_SECONDS,
                FitWriter.FieldSpecNumbers.FILE_ID_PRODUCT_NAME to "vivoactive 5",
            ),
        )
        writer.define(
            localType = 1,
            globalNumber = FitWriter.Numbers.GLOBAL_MONITORING,
            fields = FitWriter.Fields.specs(FitWriter.Fields.MONITORING),
        )
        repeat(MONITORING_BUCKETS) { index ->
            val timestamp = DAY_START_SECONDS + index * MONITORING_BUCKET_SECONDS
            val walking = index >= AWAKE_FROM_BUCKET && index < ASLEEP_FROM_BUCKET
            writer.data(
                1,
                mapOf(
                    FitWriter.FieldSpecNumbers.MONITORING_TIMESTAMP to timestamp,
                    FitWriter.FieldSpecNumbers.MONITORING_LOCAL_TIMESTAMP to timestamp,
                    FitWriter.FieldSpecNumbers.MONITORING_ACTIVITY_TYPE to if (walking) {
                        ACTIVITY_TYPE_WALKING
                    } else {
                        ACTIVITY_TYPE_SLEEP
                    },
                    FitWriter.FieldSpecNumbers.MONITORING_CYCLES to if (walking) {
                        MONITORING_RAW_CYCLES_PER_BUCKET
                    } else {
                        0
                    },
                    FitWriter.FieldSpecNumbers.MONITORING_CALORIES to if (walking) {
                        CALORIES_PER_BUCKET
                    } else {
                        0
                    },
                ),
            )
        }
        return writer.toByteArray()
    }

    /**
     * A day file in the shape a watch actually writes one: `monitoring_info` states the
     * full timestamp once, and every monitoring message after it carries only a 16-bit
     * timestamp and a 16-bit cycle counter.
     *
     * The file spans a full 24 hours, which is longer than the 65,536 seconds a 16-bit
     * stamp can hold, so the stamps roll over once part way through — and the walking
     * stretch deliberately straddles that rollover. A decoder that resolves the stamps
     * against the file's first full timestamp instead of against the running one
     * misdates the buckets after it; a decoder that only looks at monitoring messages
     * never finds a reference at all and loses the day.
     */
    fun monitoringTimestamp16(): ByteArray {
        val writer = FitWriter()
        writer.define(
            localType = 0,
            globalNumber = FitWriter.Numbers.GLOBAL_MONITORING_INFO,
            fields = FitWriter.Fields.specs(FitWriter.Fields.MONITORING_INFO),
        )
        writer.data(
            0,
            mapOf(FitWriter.FieldSpecNumbers.MONITORING_TIMESTAMP to DAY_START_SECONDS),
        )
        writer.define(
            localType = 1,
            globalNumber = FitWriter.Numbers.GLOBAL_MONITORING,
            fields = FitWriter.Fields.specs(FitWriter.Fields.MONITORING_TIMED_16),
        )
        repeat(MONITORING_BUCKETS) { index ->
            val timestamp = DAY_START_SECONDS + (index + 1) * MONITORING_BUCKET_SECONDS
            val walking = index >= AWAKE_FROM_BUCKET_16
            writer.data(
                1,
                mapOf(
                    FitWriter.FieldSpecNumbers.MONITORING_TIMESTAMP_16 to
                        (timestamp and TRUNCATED_16_MASK).toInt(),
                    FitWriter.FieldSpecNumbers.MONITORING_ACTIVITY_TYPE to if (walking) {
                        ACTIVITY_TYPE_WALKING
                    } else {
                        ACTIVITY_TYPE_SLEEP
                    },
                    FitWriter.FieldSpecNumbers.MONITORING_CYCLES_16 to if (walking) {
                        MONITORING_RAW_CYCLES_PER_BUCKET.toInt()
                    } else {
                        0
                    },
                ),
            )
        }
        return writer.toByteArray()
    }

    /** A one-record file with a big-endian definition, to prove endianness is honoured. */
    fun bigEndian(): ByteArray {
        val writer = FitWriter(bigEndian = true)
        writer.define(
            localType = 0,
            globalNumber = FitWriter.Numbers.GLOBAL_RECORD,
            fields = FitWriter.Fields.specs(FitWriter.Fields.RECORD),
        )
        writer.data(
            0,
            mapOf(
                FitWriter.FieldSpecNumbers.RECORD_TIMESTAMP to DAY_START_SECONDS,
                FitWriter.FieldSpecNumbers.RECORD_HEART_RATE to BIG_ENDIAN_HEART_RATE,
                FitWriter.FieldSpecNumbers.RECORD_DISTANCE to 0,
            ),
        )
        return writer.toByteArray()
    }

    /**
     * Three heart-rate records: a full one, one with a compressed-timestamp header, and
     * a full one after it.
     *
     * The base timestamp deliberately does **not** sit on a 32-second boundary, so the
     * 5-bit offset has to be expanded against the running timestamp rather than added
     * to it, and the trailing record only lands in the right place if the compressed
     * record's length was read correctly.
     */
    fun compressedTimestamp(): ByteArray {
        val writer = FitWriter()
        val base = DAY_START_SECONDS + COMPRESSED_BASE_OFFSET_SECONDS
        writer.define(
            localType = 0,
            globalNumber = FitWriter.Numbers.GLOBAL_RECORD,
            fields = FitWriter.Fields.specs(FitWriter.Fields.RECORD),
        )
        // A compressed-timestamp message takes its time from the header, so its
        // definition carries no field 253 (that is the point of compressing it).
        writer.define(
            localType = 1,
            globalNumber = FitWriter.Numbers.GLOBAL_RECORD,
            fields = FitWriter.Fields.specs(FitWriter.Fields.RECORD_TIMED_BY_HEADER),
        )
        writer.data(
            0,
            mapOf(
                FitWriter.FieldSpecNumbers.RECORD_TIMESTAMP to base,
                FitWriter.FieldSpecNumbers.RECORD_HEART_RATE to COMPRESSED_FIRST_HEART_RATE,
                FitWriter.FieldSpecNumbers.RECORD_DISTANCE to 0,
            ),
        )
        writer.compressedData(
            localType = 1,
            timeOffset = COMPRESSED_OFFSET_SECONDS,
            values = mapOf(
                FitWriter.FieldSpecNumbers.RECORD_HEART_RATE to COMPRESSED_SECOND_HEART_RATE,
                FitWriter.FieldSpecNumbers.RECORD_DISTANCE to 0,
            ),
        )
        writer.data(
            0,
            mapOf(
                FitWriter.FieldSpecNumbers.RECORD_TIMESTAMP to
                    base + COMPRESSED_TRAILING_OFFSET_SECONDS,
                FitWriter.FieldSpecNumbers.RECORD_HEART_RATE to COMPRESSED_THIRD_HEART_RATE,
                FitWriter.FieldSpecNumbers.RECORD_DISTANCE to 0,
            ),
        )
        return writer.toByteArray()
    }

    /** A copy of [bytes] with one payload byte flipped, so the CRC no longer matches. */
    fun corrupt(bytes: ByteArray): ByteArray {
        val copy = bytes.copyOf()
        val index = copy.size / 2
        copy[index] = (copy[index].toInt() xor 0xFF).toByte()
        return copy
    }

    const val WALK_MINUTES = 45
    const val WALK_STEPS = 3000
    const val WALK_CALORIES = 142
    const val WALK_START_HEART_RATE = 96
    const val WALK_AVG_HEART_RATE = 108
    const val WALK_MAX_HEART_RATE = 124
    const val HEART_RATE_STEP_MINUTES = 5
    const val WALK_START_OFFSET_SECONDS = 9 * 60 * 60L
    const val DISTANCE_PER_MINUTE_CENTIMETRES = 5300L
    const val SECONDS_PER_MINUTE = 60L
    const val MILLIS_PER_SECOND = 1000L

    /** One cycle (one stride) is two steps, in both messages that report cycles. */
    const val STEPS_PER_CYCLE = 2L

    const val MONITORING_BUCKETS = 96
    const val MONITORING_BUCKET_SECONDS = 15 * 60L
    const val AWAKE_FROM_BUCKET = 0
    const val ASLEEP_FROM_BUCKET = 72
    const val CALORIES_PER_BUCKET = 21L

    /** Steps in one waking bucket. */
    const val STEPS_PER_BUCKET = 250L

    /**
     * What `monitoring.cycles` holds on disk for [STEPS_PER_BUCKET]: scale 2 divides the
     * raw field into cycles and a walking cycle is two steps, so the raw field is the
     * step count itself (the profile's unit for `cycles_16` says as much).
     */
    const val MONITORING_RAW_CYCLES_PER_BUCKET = STEPS_PER_BUCKET

    /** 72 waking buckets × 250 steps = 18,000 steps in the fixture. */
    const val EXPECTED_MONITORING_STEPS = ASLEEP_FROM_BUCKET * STEPS_PER_BUCKET

    const val BIG_ENDIAN_HEART_RATE = 77

    const val COMPRESSED_FIRST_HEART_RATE = 88
    const val COMPRESSED_SECOND_HEART_RATE = 91
    const val COMPRESSED_THIRD_HEART_RATE = 94
    const val COMPRESSED_OFFSET_SECONDS = 3

    /**
     * 30 s past a 32-second boundary, so the header's 5-bit offset of 3 wraps: the
     * expanded time is 5 s after the base, not 3 s. A decoder that simply adds the
     * offset gets a different answer, which is the point of the fixture.
     */
    const val COMPRESSED_BASE_OFFSET_SECONDS = 30L

    /** Where the rollover lands the compressed record, relative to the base. */
    const val COMPRESSED_EXPANDED_SECONDS = 5L

    /** The trailing full record, which only parses if the compressed one was sized right. */
    const val COMPRESSED_TRAILING_OFFSET_SECONDS = 60L

    /** `timestamp_16` keeps the low sixteen bits of the FIT seconds. */
    const val TRUNCATED_16_MASK = 0xFFFFL

    /**
     * The 16-bit day file sleeps first and walks the rest of the day, so its walking
     * stretch straddles the 65,536-second rollover rather than ending before it.
     */
    const val AWAKE_FROM_BUCKET_16 = 24
}
