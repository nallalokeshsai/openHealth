package app.openhealth.core.activity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The pure half of the Bluetooth link (`BUILD-ROADMAP.md` §B8, ADR 0013 §4): which
 * measurements are batched, how a bounded buffer behaves when it fills, and the retry
 * ladder. The radio needs hardware; none of this does.
 */
class BleSessionTest {
    private fun heartRate(bpm: Int) = GattMeasurement.HeartRate(
        bpm = bpm,
        contact = ContactStatus.SUPPORTED,
        rrIntervalsMillis = emptyList(),
        energyExpendedKiloJoules = null,
    )

    /**
     * A strap notifies about once a second. Writing each sample on its own is one
     * transaction and one day rebuild a second, which is the work `pr.md` §6.26 budgets
     * against during a session, so a strap's samples are batched.
     */
    @Test
    fun aStrapIsBatchedAndASingleMeasurementIsNot() {
        assertTrue(BleWritePolicy.isStreaming(heartRate(bpm = 128)))

        assertFalse(
            BleWritePolicy.isStreaming(
                GattMeasurement.Weight(
                    kilograms = 70.0,
                    bmi = null,
                    heightMetres = null,
                    at = null,
                ),
            ),
        )
        assertFalse(
            BleWritePolicy.isStreaming(
                GattMeasurement.BloodPressure(
                    systolicMmHg = 118.0,
                    diastolicMmHg = 76.0,
                    meanArterialMmHg = 90.0,
                    pulseBpm = 61,
                    at = null,
                ),
            ),
        )
        assertFalse(
            BleWritePolicy.isStreaming(
                GattMeasurement.BodyComposition(
                    bodyFatPercent = 18.0,
                    kilograms = 70.0,
                    basalMetabolismKiloJoules = null,
                    musclePercent = null,
                    bodyWaterPercent = null,
                    impedanceOhms = null,
                    at = null,
                ),
            ),
        )
    }

    @Test
    fun theBufferDrainsInOrderAndEmptiesItself() {
        val buffer = MeasurementBuffer<Int>()
        (1..3).forEach(buffer::add)
        assertEquals(listOf(1, 2, 3), buffer.drain())
        assertEquals(0, buffer.size)
        assertEquals(emptyList(), buffer.drain())
    }

    /** A full buffer drops the oldest reading rather than growing without limit. */
    @Test
    fun aFullBufferDropsTheOldestReading() {
        val buffer = MeasurementBuffer<Int>(capacity = 3)
        (1..5).forEach(buffer::add)
        assertEquals(listOf(3, 4, 5), buffer.drain())
    }

    @Test
    fun theRetryLadderDoublesToItsCeilingAndStops() {
        assertEquals(BleRetry.FIRST_DELAY, BleRetry.delayFor(1))
        assertEquals(BleRetry.FIRST_DELAY * 2, BleRetry.delayFor(2))
        assertEquals(BleRetry.MAX_DELAY, BleRetry.delayFor(BleRetry.MAX_ATTEMPTS))
        assertTrue(BleRetry.shouldRetry(attempt = 1, autoConnect = true))
        assertFalse(BleRetry.shouldRetry(attempt = BleRetry.MAX_ATTEMPTS, autoConnect = true))
        assertFalse(
            BleRetry.shouldRetry(attempt = 1, autoConnect = false),
            "a device the person turned off is not chased",
        )
    }
}
