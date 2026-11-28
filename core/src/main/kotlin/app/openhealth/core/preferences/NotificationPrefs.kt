package app.openhealth.core.preferences

import kotlinx.datetime.LocalTime

/**
 * Which local reminders may notify, and the quiet window that silences the check-ins
 * (`pr.md` §6.19). Medicine reminders are deliberately outside quiet hours.
 */
data class NotificationPrefs(
    val enabled: Boolean = true,
    val medication: Boolean = true,
    val daily: Boolean = true,
    val weekly: Boolean = true,
    val quietHours: Boolean = false,
    val quietStart: String = "22:00",
    val quietEnd: String = "07:00",
) {
    /** True inside the quiet window, including one that crosses midnight. */
    fun silences(time: LocalTime): Boolean {
        val start = runCatching { LocalTime.parse(quietStart) }.getOrNull()
        val end = runCatching { LocalTime.parse(quietEnd) }.getOrNull()
        return quietHours &&
            start != null &&
            end != null &&
            if (start <= end) {
                time >= start && time < end
            } else {
                time >= start || time < end
            }
    }
}
