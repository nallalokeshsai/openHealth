package app.openhealth.wear.heart

import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.SampleDataPoint
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `HEART_RATE_BPM` carries a `Double`, so the reader has to use the typed accessor. The
 * first B14 cut asked for a `Float` and therefore never produced a reading on a real
 * watch; the emulator has no sensor, so nothing caught it (`docs/adr/0019`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WearHeartRateTest {
    private fun sample(bpm: Double, seconds: Long): SampleDataPoint<Double> = SampleDataPoint(
        dataType = DataType.HEART_RATE_BPM,
        value = bpm,
        timeDurationFromBoot = Duration.ofSeconds(seconds),
    )

    @Test
    fun aDoubleSampleIsReadAsBeatsPerMinute() {
        val container = DataPointContainer(listOf(sample(72.0, 1)))
        assertEquals(72, WearHeartRate.bpmOf(container))
    }

    @Test
    fun theNewestPlausibleSampleWins() {
        val container = DataPointContainer(
            listOf(sample(61.0, 1), sample(0.0, 2), sample(134.0, 3)),
        )
        assertEquals(134, WearHeartRate.bpmOf(container))
    }

    @Test
    fun aSensorWithoutContactReportsNothingRatherThanZero() {
        val container = DataPointContainer(listOf(sample(0.0, 1)))
        assertNull(WearHeartRate.bpmOf(container))
    }

    @Test
    fun anImplausiblyHighFigureIsDropped() {
        val container = DataPointContainer(listOf(sample(400.0, 1)))
        assertNull(WearHeartRate.bpmOf(container))
    }

    @Test
    fun anEmptyBatchIsNotAReading() {
        assertNull(WearHeartRate.bpmOf(DataPointContainer(emptyList())))
    }
}
