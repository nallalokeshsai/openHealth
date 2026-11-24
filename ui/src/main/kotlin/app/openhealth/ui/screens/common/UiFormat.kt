package app.openhealth.ui.screens.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import app.openhealth.ui.R
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Dates and times in the person's locale (§11.4). Compose observes the locale through
 * [observedLocale]; these helpers never use a fixed English month or a fixed 24-hour
 * clock.
 */
@Composable
fun shortDate(date: LocalDate): String {
    val locale = observedLocale()
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "MMMd")
    return javaDate(date).format(DateTimeFormatter.ofPattern(pattern, locale))
}

@Composable
fun mediumDate(date: LocalDate): String = javaDate(date)
    .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(observedLocale()))

@Composable
fun timeOf(instant: Instant, zone: TimeZone): String {
    val local = instant.toLocalDateTime(zone)
    return javaTime(local).format(
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(observedLocale()),
    )
}

@Composable
fun hourOf(hour: Int): String {
    val local = java.time.LocalTime.of(hour, 0)
    return local.format(
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(observedLocale()),
    )
}

/**
 * A number in the person's locale: its separators, at most [decimals] decimal places, no
 * trailing zero, and a true minus sign rather than a hyphen (`DESIGN.md` §8.1, §11.7).
 */
@Composable
fun number(value: Double, decimals: Int = 0): String = java.text.NumberFormat
    .getNumberInstance(observedLocale())
    .apply { maximumFractionDigits = decimals }
    .format(value)
    .replace('-', MINUS)

private const val MINUS = '\u2212'

/** "1 h 28 m later", or null when the recording happened at the same moment. */
@Composable
fun recordingDelay(happenedAt: Instant, recordedAt: Instant): String? {
    val delay = recordedAt - happenedAt
    if (delay < 1.minutes) return null
    return when {
        delay < 1.hours -> {
            val minutes = delay.inWholeMinutes.toInt()
            pluralStringResource(R.plurals.provenance_minutes_later, minutes, minutes)
        }
        delay < 1.days -> {
            val hours = delay.inWholeHours.toInt()
            pluralStringResource(R.plurals.provenance_hours_later, hours, hours)
        }
        else -> {
            val days = delay.inWholeDays.toInt()
            pluralStringResource(R.plurals.provenance_days_later, days, days)
        }
    }
}

private fun javaDate(date: LocalDate): java.time.LocalDate =
    java.time.LocalDate.of(date.year, date.month.ordinal + 1, date.dayOfMonth)

private fun javaTime(local: LocalDateTime): java.time.LocalDateTime = java.time.LocalDateTime.of(
    local.year,
    local.month.ordinal + 1,
    local.dayOfMonth,
    local.hour,
    local.minute,
)
