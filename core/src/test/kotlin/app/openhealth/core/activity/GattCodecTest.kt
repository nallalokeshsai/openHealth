package app.openhealth.core.activity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

/**
 * The Bluetooth SIG decoders (`Tech-Stack.md` §8). Packets are built from the published
 * layouts, so the test is the specification: 8- and 16-bit heart rate, RR intervals,
 * metric and imperial weight, body composition, and both pressure units of a cuff.
 */
class GattCodecTest {
    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) {
        values[it].toByte()
    }

    @Test
    fun heartRateDecodesTheEightBitForm() {
        val decoded = GattCodec.decodeHeartRate(bytes(0x00, 72))
        assertEquals(72, decoded.bpm)
        assertEquals(ContactStatus.NOT_SUPPORTED, decoded.contact)
        assertEquals(emptyList(), decoded.rrIntervalsMillis)
        assertNull(decoded.energyExpendedKiloJoules)
    }

    @Test
    fun heartRateDecodesTheSixteenBitFormWithContact() {
        val decoded = GattCodec.decodeHeartRate(bytes(0x0F, 0x2C, 0x01, 0x40, 0x00))
        assertEquals(300, decoded.bpm)
        assertEquals(ContactStatus.SUPPORTED, decoded.contact)
        assertEquals(64, decoded.energyExpendedKiloJoules)
    }

    @Test
    fun heartRateDecodesRrIntervalsInMilliseconds() {
        // Flags: RR present. HR 60, then two RR values of 1024 (1 s) and 512 (0.5 s).
        val decoded = GattCodec.decodeHeartRate(bytes(0x10, 60, 0x00, 0x04, 0x00, 0x02))
        assertEquals(60, decoded.bpm)
        assertEquals(listOf(1000, 500), decoded.rrIntervalsMillis)
    }

    @Test
    fun heartRateReportsAContactSensorThatSaysNoContact() {
        val decoded = GattCodec.decodeHeartRate(bytes(0x04, 80))
        assertEquals(ContactStatus.NOT_SUPPORTED_NO_CONTACT, decoded.contact)
    }

    @Test
    fun aTruncatedHeartRatePacketIsRejected() {
        assertFailsWith<GattDecodeException> { GattCodec.decodeHeartRate(ByteArray(0)) }
        assertFailsWith<GattDecodeException> { GattCodec.decodeHeartRate(bytes(0x00)) }
    }

    @Test
    fun weightDecodesKilogramsAtTheProfilesResolution() {
        // 14,000 × 0.005 kg = 70.000 kg.
        val decoded = GattCodec.decodeWeight(bytes(0x00, 0xB0, 0x36))
        assertEquals(70.0, decoded.kilograms, 1e-9)
        assertNull(decoded.bmi)
        assertNull(decoded.at)
    }

    @Test
    fun weightDecodesPoundsIntoKilograms() {
        // 154.32 lb at 0.01 lb resolution.
        val decoded = GattCodec.decodeWeight(bytes(0x01, 0x48, 0x3C))
        assertEquals(70.0, decoded.kilograms, 0.01)
    }

    @Test
    fun weightDecodesTheTimestampAndBmi() {
        val packet = bytes(
            0x0A,
            0xB0, 0x36,
            0xEA, 0x07, 0x0B, 0x17, 0x08, 0x1E, 0x00,
            0xEB, 0x00,
            0xAF, 0x00,
        )
        val decoded = GattCodec.decodeWeight(packet, TimeZone.UTC)
        assertEquals(70.0, decoded.kilograms, 1e-9)
        assertEquals(23.5, decoded.bmi ?: 0.0, 1e-9)
        assertEquals(1.75, decoded.heightMetres ?: 0.0, 1e-9)
        assertEquals(Instant.parse("2026-11-23T08:30:00Z"), decoded.at)
    }

    @Test
    fun bodyCompositionDecodesFatWeightAndImpedance() {
        // Flags: timestamp, basal metabolism, impedance, weight and the trailing fat %.
        val packet = bytes(
            0x0A, 0x06,
            0xEA, 0x07, 0x0B, 0x17, 0x08, 0x1E, 0x00,
            0x10, 0x27,
            0x2C, 0x01,
            0xB0, 0x36,
            0xB4, 0x00,
        )
        val decoded = GattCodec.decodeBodyComposition(packet, TimeZone.UTC)
        assertEquals(70.0, decoded.kilograms ?: 0.0, 1e-9)
        assertEquals(18.0, decoded.bodyFatPercent ?: 0.0, 1e-9)
        assertEquals(10_000.0, decoded.basalMetabolismKiloJoules?.toDouble() ?: 0.0, 1e-9)
        assertEquals(30.0, decoded.impedanceOhms ?: 0.0, 1e-9)
        assertEquals(Instant.parse("2026-11-23T08:30:00Z"), decoded.at)
    }

    @Test
    fun aTruncatedBodyCompositionPacketIsRejected() {
        assertFailsWith<GattDecodeException> { GattCodec.decodeBodyComposition(bytes(0x00)) }
    }

    @Test
    fun bloodPressureDecodesMillimetresOfMercury() {
        val packet = bytes(0x04, 0x80, 0x00, 0x4E, 0x00, 0x5A, 0x00, 0x44, 0x00)
        val decoded = GattCodec.decodeBloodPressure(packet, TimeZone.UTC)
        assertEquals(128.0, decoded.systolicMmHg, 1e-9)
        assertEquals(78.0, decoded.diastolicMmHg, 1e-9)
        assertEquals(90.0, decoded.meanArterialMmHg, 1e-9)
        assertEquals(68, decoded.pulseBpm)
    }

    @Test
    fun bloodPressureConvertsKilopascalsIntoTheStorageUnit() {
        // 17.0 kPa systolic, 10.0 kPa diastolic, 12.0 kPa mean; 7.500615 mmHg per kPa.
        val packet = bytes(0x01, 0xAA, 0x00, 0x64, 0x00, 0x78, 0x00)
        val decoded = GattCodec.decodeBloodPressure(packet, TimeZone.UTC)
        assertEquals(17.0 * 7.500615, decoded.systolicMmHg, 1e-6)
        assertEquals(10.0 * 7.500615, decoded.diastolicMmHg, 1e-6)
        assertEquals(12.0 * 7.500615, decoded.meanArterialMmHg, 1e-6)
    }

    @Test
    fun aTruncatedBloodPressurePacketIsRejected() {
        assertFailsWith<GattDecodeException> { GattCodec.decodeBloodPressure(bytes(0x00, 0x80)) }
    }

    @Test
    fun aDeviceIsIdentifiedByTheServiceItAdvertises() {
        assertEquals(
            DeviceKind.HEART_RATE,
            DeviceKind.fromServices(listOf(GattServices.HEART_RATE)),
        )
        assertEquals(
            DeviceKind.SCALE,
            DeviceKind.fromServices(
                listOf(GattServices.BATTERY.uppercase(), GattServices.WEIGHT_SCALE),
            ),
        )
        assertEquals(
            DeviceKind.BLOOD_PRESSURE,
            DeviceKind.fromServices(listOf(GattServices.BLOOD_PRESSURE)),
        )
        assertNull(DeviceKind.fromServices(listOf(GattServices.BATTERY)))
        assertNotNull(DeviceKind.fromServices(listOf(GattServices.HEART_RATE)))
    }
}
