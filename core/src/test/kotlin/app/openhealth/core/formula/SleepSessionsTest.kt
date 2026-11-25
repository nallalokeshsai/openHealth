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

    @Test
    fun twoSourcesOnOneNightAreNeverSummed() {
        // The phone's own log and Health Connect both describe the same night. The day
        // keeps one source's night, never the total of the two (`pr.md` §7.6).
        val sessions = SleepSessions.collect(
            listOf(
                SleepSample("hc-9", ObservationType.SLEEP_DURATION, 7.0 * 3_600, day, "garmin"),
                SleepSample("hc-9", ObservationType.SLEEP_DEEP, 1.5 * 3_600, day, "garmin"),
                SleepSample("hc-9", ObservationType.SLEEP_LIGHT, 4.0 * 3_600, day, "garmin"),
                SleepSample("hc-9", ObservationType.SLEEP_REM, 1.5 * 3_600, day, "garmin"),
                SleepSample("man-1", ObservationType.SLEEP_DURATION, 6.0 * 3_600, day, "manual"),
            ),
            priority = { if (it == "garmin") 1 else 2 },
        )
        assertEquals(1, sessions.size)
        assertEquals("garmin", sessions.single().sourceId)
        assertEquals(7.0 * 3_600, sessions.single().totals.asleepSeconds, 0.001)
    }

    @Test
    fun theHigherPrioritySourceWinsAnEquallyDetailedNight() {
        val sessions = SleepSessions.collect(
            listOf(
                SleepSample(null, ObservationType.SLEEP_DURATION, 6.0 * 3_600, day, "watch"),
                SleepSample(null, ObservationType.SLEEP_DURATION, 8.0 * 3_600, day, "phone"),
            ),
            priority = { if (it == "watch") 0 else 5 },
        )
        assertEquals(1, sessions.size)
        assertEquals("watch", sessions.single().sourceId)
        // Never the largest value: the losing source's longer night is not what shows.
        assertEquals(6.0 * 3_600, sessions.single().totals.asleepSeconds, 0.001)
    }

    @Test
    fun twoSourcesWithoutASessionIdAreNotAddedTogether() {
        val sessions = SleepSessions.collect(
            listOf(
                SleepSample(null, ObservationType.SLEEP_DEEP, 1.0 * 3_600, day, "a"),
                SleepSample(null, ObservationType.SLEEP_LIGHT, 3.0 * 3_600, day, "a"),
                SleepSample(null, ObservationType.SLEEP_DEEP, 1.0 * 3_600, day, "b"),
                SleepSample(null, ObservationType.SLEEP_LIGHT, 3.0 * 3_600, day, "b"),
            ),
            priority = { if (it == "a") 1 else 2 },
        )
        assertEquals(1, sessions.size)
        assertEquals(4.0 * 3_600, sessions.single().totals.asleepSeconds, 0.001)
    }

    @Test
    fun oneSourceStillKeepsEveryNightItReports() {
        val sessions = SleepSessions.collect(
            listOf(
                SleepSample("n-1", ObservationType.SLEEP_DURATION, 7.0 * 3_600, day, "garmin"),
                SleepSample(
                    "n-2",
                    ObservationType.SLEEP_DURATION,
                    7.5 * 3_600,
                    day.plusDays(1),
                    "garmin",
                ),
            ),
        )
        assertEquals(2, sessions.size)
    }

    private fun LocalDate.plusDays(days: Int): LocalDate =
        plus(kotlinx.datetime.DatePeriod(days = days))
}
