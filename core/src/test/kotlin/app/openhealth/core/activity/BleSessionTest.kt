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

    /**
     * The tick is started once and left alone. Restarting it on every notification
     * postpones it, and a strap notifying once a second postpones a ten-second tick for
     * ever: the batch would land only on a disconnect, and a session longer than the
     * buffer holds would drop its oldest samples without saying so.
     */
    @Test
    fun theFlushTickIsStartedOnceAndNotRestartedByEveryReading() {
        val schedule = FlushSchedule()

        assertTrue(schedule.start(), "the first reading posts the tick")
        repeat(STREAMING_READINGS) {
            assertFalse(
                schedule.start(),
                "a reading arriving before the tick fires must not postpone it",
            )
        }
        assertTrue(schedule.isScheduled)
    }

    /** The tick keeps running while a device is linked or anything is still waiting. */
    @Test
    fun theFlushTickRunsOnWhileThereIsWorkAndStopsWhenThereIsNone() {
        val schedule = FlushSchedule()
        schedule.start()

        assertTrue(schedule.ranAndContinues(linked = true, waiting = 0))
        assertTrue(schedule.ranAndContinues(linked = false, waiting = 3))
        assertFalse(
            schedule.ranAndContinues(linked = false, waiting = 0),
            "an idle tick stops rather than waking the phone every ten seconds",
        )
        assertFalse(schedule.isScheduled)
        assertTrue(schedule.start(), "the next reading starts it again")
    }

    /**
     * "Nothing was written" and "the write failed" are not the same answer. A batch that
     * mapped to no observation — a composition packet whose fat is the unknown sentinel
     * and that states no weight — has nothing to store, and re-buffering it would retry
     * it on every tick for the rest of the session while never making progress.
     */
    @Test
    fun onlyAFailedWriteKeepsItsBatchBuffered() {
        assertTrue(BleWriteOutcome.FAILED.keepBuffered)
        assertFalse(BleWriteOutcome.WRITTEN.keepBuffered)
        assertFalse(BleWriteOutcome.NOTHING_TO_WRITE.keepBuffered)
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

    private companion object {
        /** More readings than a ten-second tick would ever see from a 1 Hz strap. */
        const val STREAMING_READINGS = 30
    }
}
