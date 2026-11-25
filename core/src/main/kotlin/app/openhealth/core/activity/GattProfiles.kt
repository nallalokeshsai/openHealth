package app.openhealth.core.activity

import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * The three standard GATT services this app reads (`Tech-Stack.md` §8): heart rate
 * 0x180D, weight scale 0x181D and blood pressure 0x1810. They are open Bluetooth SIG
 * profiles, so any strap, scale or cuff that implements the profile works — no vendor
 * SDK and no vendor cloud.
 *
 * The decoders are pure functions over the characteristic bytes, which is what makes the
 * "mid-workout disconnect loses nothing" claim testable without hardware: the same bytes
 * are decoded in a unit test and over a socket in the instrumented test.
 */
object GattServices {
    const val HEART_RATE = "0000180d-0000-1000-8000-00805f9b34fb"
    const val WEIGHT_SCALE = "0000181d-0000-1000-8000-00805f9b34fb"

    /**
     * Body composition is its own service. A scale that measures impedance exposes both
     * 0x181D and 0x181B, and the composition characteristic lives in this one.
     */
    const val BODY_COMPOSITION = "0000181b-0000-1000-8000-00805f9b34fb"
    const val BLOOD_PRESSURE = "00001810-0000-1000-8000-00805f9b34fb"
    const val BATTERY = "0000180f-0000-1000-8000-00805f9b34fb"
    const val DEVICE_INFORMATION = "0000180a-0000-1000-8000-00805f9b34fb"

    /** Heart Rate Measurement: a notify characteristic. */
    const val HEART_RATE_MEASUREMENT = "00002a37-0000-1000-8000-00805f9b34fb"

    /** Weight Scale Measurement and Body Composition Measurement: indications. */
    const val WEIGHT_MEASUREMENT = "00002a9d-0000-1000-8000-00805f9b34fb"
    const val BODY_COMPOSITION_MEASUREMENT = "00002a9c-0000-1000-8000-00805f9b34fb"

    /** Blood Pressure Measurement (indicate) and the live cuff pressure (notify). */
    const val BLOOD_PRESSURE_MEASUREMENT = "00002a35-0000-1000-8000-00805f9b34fb"

    /** Device Name, so a paired device is recognisable without the vendor app. */
    const val DEVICE_NAME = "00002a00-0000-1000-8000-00805f9b34fb"
}

/**
 * What a device is, decided from the services it advertises.
 *
 * A kind names every service that identifies it and every measurement characteristic it
 * is read through. The two lists are not parallel and must not be treated as such: the
 * Bluetooth SIG puts Weight Measurement (0x2A9D) in the Weight Scale service (0x181D)
 * and Body Composition Measurement (0x2A9C) in the Body Composition service (0x181B),
 * so a scale that measures impedance carries one characteristic in each. A characteristic
 * is therefore looked up across the services the device actually discovered, never inside
 * the one service the device happened to be recognised by.
 */
enum class DeviceKind(val serviceUuids: List<String>, val measurementUuids: List<String>) {
    HEART_RATE(listOf(GattServices.HEART_RATE), listOf(GattServices.HEART_RATE_MEASUREMENT)),

    /**
     * A scale indicates its weight on 0x2A9D and, when it measures impedance, its
     * composition on 0x2A9C. Both are subscribed: a scale that only ever reported the
     * first would leave body fat behind on a device that sends it. Either service is
     * enough to recognise one, because a composition-only scale advertises 0x181B alone.
     */
    SCALE(
        listOf(GattServices.WEIGHT_SCALE, GattServices.BODY_COMPOSITION),
        listOf(GattServices.WEIGHT_MEASUREMENT, GattServices.BODY_COMPOSITION_MEASUREMENT),
    ),
    BLOOD_PRESSURE(
        listOf(GattServices.BLOOD_PRESSURE),
        listOf(GattServices.BLOOD_PRESSURE_MEASUREMENT),
    ),
    ;

    /** The service a device of this kind always advertises. */
    val serviceUuid: String get() = serviceUuids.first()

    /** The characteristic a device of this kind always has. */
    val measurementUuid: String get() = measurementUuids.first()

    companion object {
        /** The first known service wins; a strap that also exposes battery is a strap. */
        fun fromServices(serviceUuids: List<String>): DeviceKind? {
            val normalised = serviceUuids.map(::normaliseUuid).toSet()
            return entries.firstOrNull { kind ->
                kind.serviceUuids.any { it in normalised }
            }
        }
    }
}

/** Android and most devices report 16-bit UUIDs in their 128-bit form, in either case. */
fun normaliseUuid(uuid: String): String = uuid.trim().lowercase()

/** The contact sensor of a heart-rate strap, when the strap reports it. */
enum class ContactStatus { SUPPORTED, NOT_SUPPORTED, NOT_SUPPORTED_NO_CONTACT }

/**
 * A decoded measurement. [at] is nil when the device did not send its own timestamp; the
 * caller then uses the moment the packet arrived, which is the honest fallback.
 */
sealed interface GattMeasurement {
    data class HeartRate(
        val bpm: Int,
        val contact: ContactStatus,
        val rrIntervalsMillis: List<Int>,
        val energyExpendedKiloJoules: Int?,
    ) : GattMeasurement

    data class Weight(
        val kilograms: Double,
        val bmi: Double?,
        val heightMetres: Double?,
        val at: Instant?,
    ) : GattMeasurement

    data class BodyComposition(
        val bodyFatPercent: Double?,
        val kilograms: Double?,
        val basalMetabolismKiloJoules: Int?,
        val musclePercent: Double?,
        val bodyWaterPercent: Double?,
        val impedanceOhms: Double?,
        val at: Instant?,
    ) : GattMeasurement

    data class BloodPressure(
        val systolicMmHg: Double,
        val diastolicMmHg: Double,
        val meanArterialMmHg: Double,
        val pulseBpm: Int?,
        val at: Instant?,
    ) : GattMeasurement
}

/** Returned for a packet this profile does not define; never a guessed value. */
class GattDecodeException(message: String) : IllegalArgumentException(message)

/**
 * Decoders for the Bluetooth SIG characteristics. Field layouts come from the published
 * GATT specifications (Heart Rate 0x2A37, Weight 0x2A9D, Body Composition 0x2A9C, Blood
 * Pressure 0x2A35); no vendor code is used.
 */
object GattCodec {
    private const val UINT8_MAX = 0xFF
    private const val UINT16_UNKNOWN = 0xFFFF
    private const val HEART_RATE_FLAG_UINT16 = 0x01
    private const val HEART_RATE_FLAG_CONTACT_SUPPORTED = 0x04
    private const val HEART_RATE_FLAG_CONTACT = 0x02
    private const val HEART_RATE_FLAG_ENERGY = 0x08
    private const val HEART_RATE_FLAG_RR = 0x10
    private const val WEIGHT_FLAG_IMPERIAL = 0x01
    private const val WEIGHT_FLAG_TIMESTAMP = 0x02
    private const val WEIGHT_FLAG_BMI = 0x08
    private const val WEIGHT_SI_RESOLUTION_KG = 0.005
    private const val WEIGHT_IMPERIAL_RESOLUTION_LB = 0.01
    private const val POUNDS_PER_KILOGRAM = 2.2046226218
    private const val BMI_RESOLUTION = 0.1
    private const val HEIGHT_RESOLUTION_METRES = 0.01
    private const val KILOJOULES_PER_KILOCALORIE = 4.184
    private const val KILOPASCAL_TO_MMHG = 7.500615

    /**
     * Decodes whichever characteristic the packet arrived on. The Android side only
     * moves bytes, so the choice of decoder is made here, by characteristic, rather
     * than by what kind of device is attached: a scale sends its weight on 0x2A9D and
     * its composition on 0x2A9C, and the two layouts are not interchangeable.
     *
     * An unknown characteristic returns null and is ignored, never guessed at.
     */
    fun decode(
        characteristicUuid: String,
        bytes: ByteArray,
        zone: TimeZone = TimeZone.UTC,
    ): GattMeasurement? = when (normaliseUuid(characteristicUuid)) {
        GattServices.HEART_RATE_MEASUREMENT -> decodeHeartRate(bytes)
        GattServices.WEIGHT_MEASUREMENT -> decodeWeight(bytes, zone)
        GattServices.BODY_COMPOSITION_MEASUREMENT -> decodeBodyComposition(bytes, zone)
        GattServices.BLOOD_PRESSURE_MEASUREMENT -> decodeBloodPressure(bytes, zone)
        else -> null
    }

    fun decodeHeartRate(bytes: ByteArray): GattMeasurement.HeartRate {
        if (bytes.isEmpty()) throw GattDecodeException("empty heart-rate packet")
        val flags = bytes[0].toInt() and UINT8_MAX
        var index = 1
        val wide = flags and HEART_RATE_FLAG_UINT16 != 0
        val bpm = if (wide) {
            readUInt16(bytes, index).also { index += 2 }
        } else {
            readUInt8(bytes, index).also { index += 1 }
        }
        val contact = when {
            flags and HEART_RATE_FLAG_CONTACT_SUPPORTED == 0 ->
                ContactStatus.NOT_SUPPORTED

            flags and HEART_RATE_FLAG_CONTACT != 0 -> ContactStatus.SUPPORTED
            else -> ContactStatus.NOT_SUPPORTED_NO_CONTACT
        }
        var energy: Int? = null
        if (flags and HEART_RATE_FLAG_ENERGY != 0) {
            energy = readUInt16(bytes, index)
            index += 2
        }
        val rr = mutableListOf<Int>()
        if (flags and HEART_RATE_FLAG_RR != 0) {
            while (index + 1 < bytes.size) {
                // The RR unit is 1/1024 s; milliseconds are the app's storage unit.
                rr += (readUInt16(bytes, index) * MILLIS_PER_SECOND) / RR_UNITS_PER_SECOND
                index += 2
            }
        }
        return GattMeasurement.HeartRate(bpm, contact, rr, energy)
    }

    fun decodeWeight(bytes: ByteArray, zone: TimeZone = TimeZone.UTC): GattMeasurement.Weight {
        if (bytes.size < WEIGHT_MIN_PACKET_BYTES) {
            throw GattDecodeException("weight packet too short")
        }
        val flags = readUInt8(bytes, 0)
        val imperial = flags and WEIGHT_FLAG_IMPERIAL != 0
        var index = 1
        val raw = readUInt16(bytes, index)
        index += 2
        val kilograms = if (imperial) {
            raw * WEIGHT_IMPERIAL_RESOLUTION_LB / POUNDS_PER_KILOGRAM
        } else {
            raw * WEIGHT_SI_RESOLUTION_KG
        }
        var at: Instant? = null
        if (flags and WEIGHT_FLAG_TIMESTAMP != 0) {
            at = readDateTime(bytes, index, zone)
            index += DATE_TIME_BYTES
        }
        // A user id byte may follow the timestamp; it is a scale-side slot, not a person.
        if (flags and WEIGHT_FLAG_USER_ID != 0) index += 1
        var bmi: Double? = null
        var height: Double? = null
        if (flags and WEIGHT_FLAG_BMI != 0 && index + 1 < bytes.size) {
            val rawBmi = readUInt16(bytes, index)
            index += 2
            if (rawBmi != UINT16_UNKNOWN) bmi = rawBmi * BMI_RESOLUTION
            if (index + 1 < bytes.size) {
                val rawHeight = readUInt16(bytes, index)
                if (rawHeight != UINT16_UNKNOWN) height = rawHeight * HEIGHT_RESOLUTION_METRES
            }
        }
        return GattMeasurement.Weight(kilograms, bmi, height, at)
    }

    /**
     * Body Composition Measurement (0x2A9C). The profile's field order is **flags, then
     * the mandatory body fat percentage**, and only then the optional fields in flag-bit
     * order: time stamp, user id, basal metabolism, muscle percentage, muscle mass, fat
     * free mass, soft lean mass, body water mass, impedance, weight, height.
     *
     * Body fat is not a trailing field. Reading it from the end of the packet puts every
     * optional field two bytes early, so a BIA scale's timestamp, impedance and weight
     * all come out of the wrong bytes and the fat percentage is read from the height
     * field or past the end.
     */
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun decodeBodyComposition(
        bytes: ByteArray,
        zone: TimeZone = TimeZone.UTC,
    ): GattMeasurement.BodyComposition {
        if (bytes.size < BODY_COMPOSITION_MIN_PACKET_BYTES) {
            throw GattDecodeException("body-composition packet too short")
        }
        val flags = readUInt16(bytes, 0)
        val imperial = flags and BODY_FLAG_UNITS_IMPERIAL != 0
        fun mass(raw: Int): Double = if (imperial) {
            raw * WEIGHT_IMPERIAL_RESOLUTION_LB / POUNDS_PER_KILOGRAM
        } else {
            raw * WEIGHT_SI_RESOLUTION_KG
        }
        var index = 2
        val rawFat = readUInt16(bytes, index)
        index += 2
        val fatPercent = rawFat.takeIf { it != UINT16_UNKNOWN }?.let { it * BODY_FAT_RESOLUTION }
        var at: Instant? = null
        if (flags and BODY_FLAG_TIMESTAMP != 0) {
            at = readDateTime(bytes, index, zone)
            index += DATE_TIME_BYTES
        }
        if (flags and BODY_FLAG_USER_ID != 0) index += 1
        var basal: Int? = null
        if (flags and BODY_FLAG_BASAL_METABOLISM != 0) {
            basal = readUInt16(bytes, index).takeIf { it != UINT16_UNKNOWN }
            index += 2
        }
        var musclePercent: Double? = null
        if (flags and BODY_FLAG_MUSCLE_PERCENT != 0) {
            val raw = readUInt16(bytes, index)
            if (raw != UINT16_UNKNOWN) musclePercent = raw * BODY_FAT_RESOLUTION
            index += 2
        }
        if (flags and BODY_FLAG_MUSCLE_MASS != 0) index += 2
        if (flags and BODY_FLAG_FAT_FREE_MASS != 0) index += 2
        if (flags and BODY_FLAG_SOFT_LEAN_MASS != 0) index += 2
        var waterKilograms: Double? = null
        if (flags and BODY_FLAG_WATER_MASS != 0) {
            val raw = readUInt16(bytes, index)
            if (raw != UINT16_UNKNOWN) waterKilograms = mass(raw)
            index += 2
        }
        var impedance: Double? = null
        if (flags and BODY_FLAG_IMPEDANCE != 0) {
            val raw = readUInt16(bytes, index)
            if (raw != UINT16_UNKNOWN) impedance = raw * IMPEDANCE_RESOLUTION
            index += 2
        }
        var weightKg: Double? = null
        if (flags and BODY_FLAG_WEIGHT != 0) {
            val raw = readUInt16(bytes, index)
            if (raw != UINT16_UNKNOWN) weightKg = mass(raw)
            index += 2
        }
        // The profile reports water as a mass, so a percentage needs the weight from the
        // same packet. Without one there is no honest percentage to report.
        val waterPercent = if (waterKilograms != null && weightKg != null && weightKg > 0) {
            waterKilograms / weightKg * PERCENT
        } else {
            null
        }
        return GattMeasurement.BodyComposition(
            bodyFatPercent = fatPercent,
            kilograms = weightKg,
            basalMetabolismKiloJoules = basal,
            musclePercent = musclePercent,
            bodyWaterPercent = waterPercent,
            impedanceOhms = impedance,
            at = at,
        )
    }

    fun decodeBloodPressure(
        bytes: ByteArray,
        zone: TimeZone = TimeZone.UTC,
    ): GattMeasurement.BloodPressure {
        if (bytes.size < BP_MIN_PACKET_BYTES) {
            throw GattDecodeException("blood-pressure packet too short")
        }
        val flags = readUInt8(bytes, 0)
        val inKilopascals = flags and BP_FLAG_KILOPASCAL != 0

        // Systolic, diastolic and the mean arterial pressure are IEEE-11073 16-bit
        // SFLOATs, not plain integers: the top nibble is a base-10 exponent. A cuff
        // that reports 120.5 mmHg, or any value in kPa, sets that exponent, and reading
        // the word as a uint16 then turns 120.5 into 62,645.
        fun pressure(at: Int, name: String): Double {
            val value = readSFloat(bytes, at)
            if (!value.isFinite()) {
                throw GattDecodeException("the cuff reported no $name pressure")
            }
            return if (inKilopascals) value * KILOPASCAL_TO_MMHG else value
        }
        var index = 1
        val systolic = pressure(index, "systolic")
        index += 2
        val diastolic = pressure(index, "diastolic")
        index += 2
        val mean = pressure(index, "mean arterial")
        index += 2
        var at: Instant? = null
        if (flags and BP_FLAG_TIMESTAMP != 0) {
            at = readDateTime(bytes, index, zone)
            index += DATE_TIME_BYTES
        }
        var pulse: Int? = null
        if (flags and BP_FLAG_PULSE != 0 && index + 1 < bytes.size) {
            // The pulse rate is an SFLOAT too, and it is optional, so a special value
            // means "the cuff did not measure it" rather than a reading of zero.
            pulse = readSFloat(bytes, index)
                .takeIf { it.isFinite() && it > 0 }
                ?.roundToInt()
            index += 2
        }
        return GattMeasurement.BloodPressure(systolic, diastolic, mean, pulse, at)
    }

    /** The 7-byte GATT date-time: year u16, then month, day, hour, minute, second. */
    private fun readDateTime(bytes: ByteArray, at: Int, zone: TimeZone): Instant? {
        if (at + DATE_TIME_BYTES > bytes.size) return null
        var index = at
        val year = readUInt16(bytes, index)
        index += 2
        val month = readUInt8(bytes, index)
        index += 1
        val day = readUInt8(bytes, index)
        index += 1
        val hour = readUInt8(bytes, index)
        index += 1
        val minute = readUInt8(bytes, index)
        index += 1
        val second = readUInt8(bytes, index)
        return runCatching {
            LocalDateTime(
                year,
                month.coerceIn(MIN_MONTH, MAX_MONTH),
                day.coerceIn(MIN_DAY, MAX_DAY),
                hour.coerceIn(MIN_HOUR, MAX_HOUR),
                minute.coerceIn(MIN_MINUTE, MAX_MINUTE),
                second.coerceIn(MIN_SECOND, MAX_SECOND),
            ).toInstant(zone)
        }.getOrNull()
    }

    /** A kilojoule figure from a scale, converted to the app's kilocalorie vocabulary. */
    fun kilojoulesToKilocalories(kilojoules: Int): Double = kilojoules / KILOJOULES_PER_KILOCALORIE

    private fun readUInt8(bytes: ByteArray, index: Int): Int {
        if (index >= bytes.size) throw GattDecodeException("packet ended at byte $index")
        return bytes[index].toInt() and UINT8_MAX
    }

    private fun readUInt16(bytes: ByteArray, index: Int): Int {
        if (index + 1 >= bytes.size) throw GattDecodeException("packet ended at byte $index")
        val low = bytes[index].toInt() and UINT8_MAX
        val high = bytes[index + 1].toInt() and UINT8_MAX
        return low or (high shl BYTE_SHIFT)
    }

    /**
     * The IEEE-11073 16-bit SFLOAT the medical GATT profiles use for a measured value:
     * a 4-bit signed base-10 exponent over a 12-bit signed mantissa, so the value is
     * `mantissa x 10^exponent`. The reserved mantissas (NaN, NRes, the two infinities
     * and the reserved pattern) say the device has no value, and come back as a
     * non-finite double so the caller decides what that means for a mandatory field and
     * for an optional one.
     *
     * Reading the word as a plain `uint16` only agrees with this when the exponent is
     * zero, which is why a cuff reporting tenths, or reporting in kPa, would otherwise
     * come out thousands of units wrong.
     */
    fun readSFloat(bytes: ByteArray, index: Int): Double {
        val raw = readUInt16(bytes, index)
        val rawMantissa = raw and SFLOAT_MANTISSA_MASK
        val reserved = SFLOAT_RESERVED_MANTISSAS[rawMantissa]
        if (reserved != null) return reserved
        val mantissa = if (rawMantissa >= SFLOAT_MANTISSA_SIGN) {
            rawMantissa - SFLOAT_MANTISSA_RANGE
        } else {
            rawMantissa
        }
        val rawExponent = (raw shr SFLOAT_EXPONENT_SHIFT) and SFLOAT_EXPONENT_MASK
        val exponent = if (rawExponent >= SFLOAT_EXPONENT_SIGN) {
            rawExponent - SFLOAT_EXPONENT_RANGE
        } else {
            rawExponent
        }
        return mantissa * TEN.pow(exponent)
    }

    /** The mantissas the profile reserves; none of them is a measured value. */
    private val SFLOAT_RESERVED_MANTISSAS: Map<Int, Double> = mapOf(
        SFLOAT_NAN to Double.NaN,
        SFLOAT_NRES to Double.NaN,
        SFLOAT_RESERVED to Double.NaN,
        SFLOAT_POSITIVE_INFINITY to Double.POSITIVE_INFINITY,
        SFLOAT_NEGATIVE_INFINITY to Double.NEGATIVE_INFINITY,
    )

    private const val DATE_TIME_BYTES = 7
    private const val BYTE_SHIFT = 8
    private const val TEN = 10.0
    private const val SFLOAT_MANTISSA_MASK = 0x0FFF
    private const val SFLOAT_MANTISSA_SIGN = 0x0800
    private const val SFLOAT_MANTISSA_RANGE = 0x1000
    private const val SFLOAT_EXPONENT_SHIFT = 12
    private const val SFLOAT_EXPONENT_MASK = 0x0F
    private const val SFLOAT_EXPONENT_SIGN = 0x08
    private const val SFLOAT_EXPONENT_RANGE = 0x10
    private const val SFLOAT_NAN = 0x07FF
    private const val SFLOAT_NRES = 0x0800
    private const val SFLOAT_POSITIVE_INFINITY = 0x07FE
    private const val SFLOAT_NEGATIVE_INFINITY = 0x0802
    private const val SFLOAT_RESERVED = 0x0801
    private const val MIN_MONTH = 1
    private const val MAX_MONTH = 12
    private const val MIN_DAY = 1
    private const val MAX_DAY = 31
    private const val MIN_HOUR = 0
    private const val MAX_HOUR = 23
    private const val MIN_MINUTE = 0
    private const val MAX_MINUTE = 59
    private const val MIN_SECOND = 0
    private const val MAX_SECOND = 59
    private const val WEIGHT_MIN_PACKET_BYTES = 3
    private const val BODY_COMPOSITION_MIN_PACKET_BYTES = 4
    private const val BP_MIN_PACKET_BYTES = 7
    private const val MILLIS_PER_SECOND = 1000
    private const val RR_UNITS_PER_SECOND = 1024
    private const val WEIGHT_FLAG_USER_ID = 0x04
    private const val IMPEDANCE_RESOLUTION = 0.1
    private const val BODY_FAT_RESOLUTION = 0.1
    private const val PERCENT = 100.0
    private const val BP_FLAG_KILOPASCAL = 0x01
    private const val BP_FLAG_TIMESTAMP = 0x02
    private const val BP_FLAG_PULSE = 0x04
    private const val BODY_FLAG_UNITS_IMPERIAL = 0x0001
    private const val BODY_FLAG_TIMESTAMP = 0x0002
    private const val BODY_FLAG_USER_ID = 0x0004
    private const val BODY_FLAG_BASAL_METABOLISM = 0x0008
    private const val BODY_FLAG_MUSCLE_PERCENT = 0x0010
    private const val BODY_FLAG_MUSCLE_MASS = 0x0020
    private const val BODY_FLAG_FAT_FREE_MASS = 0x0040
    private const val BODY_FLAG_SOFT_LEAN_MASS = 0x0080
    private const val BODY_FLAG_WATER_MASS = 0x0100
    private const val BODY_FLAG_IMPEDANCE = 0x0200
    private const val BODY_FLAG_WEIGHT = 0x0400
}
