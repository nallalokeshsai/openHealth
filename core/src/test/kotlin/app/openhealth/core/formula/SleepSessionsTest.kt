package app.openhealth.core.formula

import app.openhealth.core.observation.ObservationType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

class SleepSessionsTest {
    private val day = LocalDate(2026, 11, 24)

    @Test
    fun aNightThatCrossesMidnightIsOneSession() {
        val session = SleepSessions.collect(
            listOf(
                SleepSample("hc-1", ObservationType.SLEEP_DURATION, 8.0 * 3_600, day),
                SleepSample("hc-1", ObservationType.SLEEP_DEEP, 1.0 * 3_600, day),
                SleepSample("hc-1", ObservationType.SLEEP_LIGHT, 2.0 * 3_600, day.plusDays(1)),
                SleepSample("hc-1", ObservationType.SLEEP_REM, 2.0 * 3_600, day.plusDays(1)),
                SleepSample("hc-1", ObservationType.SLEEP_AWAKE, 1.0 * 3_600, day.plusDays(1)),
            ),
        )
        assertEquals(1, session.size)
        assertEquals(day, session.single().localDate)
        assertEquals(5.0 * 3_600, session.single().totals.asleepSeconds, 0.001)
    }

    @Test
    fun stagesWithoutASpanStillMakeANight() {
        val session = SleepSessions.collect(
            listOf(
                SleepSample("hc-2", ObservationType.SLEEP_DEEP, 90.0 * 60, day),
                SleepSample("hc-2", ObservationType.SLEEP_LIGHT, 4.0 * 3_600, day.plusDays(1)),
                SleepSample("hc-2", ObservationType.SLEEP_REM, 90.0 * 60, day.plusDays(1)),
            ),
        )
        assertEquals(1, session.size)
        assertEquals(7.0 * 3_600, session.single().totals.asleepSeconds, 0.001)
        assertEquals(day, session.single().localDate)
    }

    @Test
    fun aSummaryOnlyNightKeepsItsSpan() {
        val session = SleepSessions.collect(
            listOf(SleepSample(null, ObservationType.SLEEP_DURATION, 7.5 * 3_600, day)),
        )
        assertEquals(1, session.size)
        assertTrue(!session.single().totals.hasStages)
        assertEquals(7.5 * 3_600, session.single().totals.asleepSeconds, 0.001)
    }

    @Test
    fun twoSessionsOnOneDayAreNotMerged() {
        val sessions = SleepSessions.collect(
            listOf(
                SleepSample("a", ObservationType.SLEEP_DURATION, 4.0 * 3_600, day),
                SleepSample("b", ObservationType.SLEEP_DURATION, 3.0 * 3_600, day),
            ),
        )
        assertEquals(2, sessions.size)
    }

    private fun LocalDate.plusDays(days: Int): LocalDate =
        plus(kotlinx.datetime.DatePeriod(days = days))
}
