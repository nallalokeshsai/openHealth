package app.openhealth.core.formula

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * B8 left one semantic point for this phase: the session span is not the sleep when the
 * night carries stages. These tests pin the distinction `pr.md` §6.10 needs.
 */
class SleepTotalsTest {
    @Test
    fun aStagedNightCountsItsStagesAndNotItsAwakeTime() {
        val night = SleepTotalsCalculator.of(
            spanSeconds = 8.0 * 3_600,
            deepSeconds = 2.0 * 3_600,
            lightSeconds = 4.0 * 3_600,
            remSeconds = 1.5 * 3_600,
            awakeSeconds = 0.5 * 3_600,
        )
        assertNotNull(night)
        assertTrue(night.hasStages)
        // 2 + 4 + 1.5 h, not the 8 h span and not the span minus awake twice.
        assertEquals(7.5 * 3_600, night.asleepSeconds, 0.001)
        assertEquals(8.0 * 3_600, night.inBedSeconds!!, 0.001)
        assertEquals(450.0, night.asleepMinutes, 0.001)
    }

    @Test
    fun aSummaryOnlyNightUsesItsDuration() {
        val night = SleepTotalsCalculator.of(spanSeconds = 7.25 * 3_600)
        assertNotNull(night)
        assertFalse(night.hasStages)
        assertEquals(7.25 * 3_600, night.asleepSeconds, 0.001)
        assertEquals(7.25 * 3_600, night.inBedSeconds!!, 0.001)
    }

    @Test
    fun timeInBedIsNeverAddedToSleep() {
        val night = SleepTotalsCalculator.of(
            spanSeconds = 8.0 * 3_600,
            inBedSeconds = 9.0 * 3_600,
        )
        assertEquals(8.0 * 3_600, night!!.asleepSeconds, 0.001)
        assertEquals(9.0 * 3_600, night.inBedSeconds!!, 0.001)
    }

    @Test
    fun noNightAtAllIsNull() {
        assertNull(SleepTotalsCalculator.of())
    }

    @Test
    fun anAbsurdStageIsIgnoredRatherThanScored() {
        val night = SleepTotalsCalculator.of(
            spanSeconds = 7.0 * 3_600,
            deepSeconds = -1.0,
            lightSeconds = 40.0 * 3_600,
        )
        assertFalse(night!!.hasStages)
        assertEquals(7.0 * 3_600, night.asleepSeconds, 0.001)
    }
}
