package app.openhealth.core.preferences

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.LocalTime

class NotificationPrefsTest {
    private val quiet = NotificationPrefs(
        quietHours = true,
        quietStart = "22:00",
        quietEnd = "07:00",
    )

    @Test
    fun quietHoursCrossMidnight() {
        assertTrue(quiet.silences(LocalTime(23, 30)))
        assertTrue(quiet.silences(LocalTime(0, 15)))
        assertTrue(quiet.silences(LocalTime(6, 59)))
        assertFalse(quiet.silences(LocalTime(7, 0)))
        assertFalse(quiet.silences(LocalTime(12, 0)))
        assertFalse(quiet.silences(LocalTime(21, 59)))
    }

    @Test
    fun quietHoursCanSitInsideOneDay() {
        val afternoon = NotificationPrefs(
            quietHours = true,
            quietStart = "13:00",
            quietEnd = "15:00",
        )
        assertTrue(afternoon.silences(LocalTime(14, 0)))
        assertFalse(afternoon.silences(LocalTime(16, 0)))
    }

    @Test
    fun quietHoursOffNeverSilence() {
        assertFalse(NotificationPrefs(quietHours = false).silences(LocalTime(23, 0)))
    }

    @Test
    fun aBrokenWindowDoesNotSilence() {
        assertFalse(
            NotificationPrefs(quietHours = true, quietStart = "nope")
                .silences(LocalTime(23, 0)),
        )
    }
}
