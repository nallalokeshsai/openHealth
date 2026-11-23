package app.openhealth.core.time

import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * A local calendar day in a specific zone. Rollups use the local day, never UTC
 * (`pr.md` §7.8), and a day can be 23, 24 or 25 hours long around a DST change.
 */
data class LocalDay(val date: LocalDate, val zone: TimeZone) {
    /** First instant of the day. During a spring-forward gap this is the transition. */
    fun startInclusive(): Instant = date.atStartOfDayIn(zone)

    /** First instant of the following local day; exclusive. */
    fun endExclusive(): Instant = date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)

    fun contains(instant: Instant): Boolean {
        val at = instant.toLocalDateTime(zone).date
        return at == date
    }

    fun length(): kotlin.time.Duration = endExclusive() - startInclusive()

    fun next(): LocalDay = LocalDay(date.plus(1, DateTimeUnit.DAY), zone)

    fun previous(): LocalDay = LocalDay(date.minus(1, DateTimeUnit.DAY), zone)

    /** Monday of this day's ISO week; downstream aggregates are scoped by it. */
    fun weekStart(): LocalDate = date.minus(date.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)

    override fun toString(): String = "$date@${zone.id}"
}

/** `pr.md` §7.8: store the UTC instant plus the local date and the zone. */
data class ZonedInstant(val instant: Instant, val localDate: LocalDate, val zone: TimeZone) {
    companion object {
        fun of(instant: Instant, zone: TimeZone): ZonedInstant =
            ZonedInstant(instant, instant.toLocalDateTime(zone).date, zone)
    }
}

/** The two timestamps every value carries (`pr.md` §2.2, §7.1). */
data class TimeProvenance(
    val happenedAt: Instant,
    val recordedAt: Instant,
    val localDate: LocalDate,
    val zone: TimeZone,
) {
    init {
        require(localDate == happenedAt.toLocalDateTime(zone).date) {
            "localDate $localDate does not match happenedAt in ${zone.id}"
        }
    }

    /** Backfill: a value can be entered late, but `recordedAt` never moves back. */
    fun isBackfilled(atRecording: Instant): Boolean = recordedAt > atRecording

    /** Hours between when something happened and when it was recorded. */
    fun recordingLagHours(): Double = (recordedAt - happenedAt).inWholeSeconds / SECONDS_PER_HOUR

    companion object {
        const val SECONDS_PER_HOUR = 3600.0

        fun of(happenedAt: Instant, recordedAt: Instant, zone: TimeZone): TimeProvenance =
            TimeProvenance(
                happenedAt = happenedAt,
                recordedAt = recordedAt,
                localDate = happenedAt.toLocalDateTime(zone).date,
                zone = zone,
            )
    }
}

/** Calendar arithmetic that the rollup and trend code shares. */
object Days {
    fun of(instant: Instant, zone: TimeZone): LocalDay =
        LocalDay(instant.toLocalDateTime(zone).date, zone)

    fun today(now: Instant, zone: TimeZone): LocalDay = of(now, zone)

    /** Inclusive range of local days. */
    fun range(from: LocalDate, toInclusive: LocalDate): List<LocalDate> {
        require(from <= toInclusive) { "range start $from is after $toInclusive" }
        val days = mutableListOf<LocalDate>()
        var cursor = from
        while (cursor <= toInclusive) {
            days += cursor
            cursor = cursor.plus(1, DateTimeUnit.DAY)
        }
        return days
    }

    fun daysBetween(from: LocalDate, toInclusive: LocalDate): Long =
        from.daysUntil(toInclusive).toLong()

    fun addDays(date: LocalDate, days: Int): LocalDate = date.plus(DatePeriod(days = days))

    fun hoursBetween(from: Instant, to: Instant): Double =
        (to - from).inWholeSeconds / TimeProvenance.SECONDS_PER_HOUR
}

/** Default working zone for fixtures and tests. */
val UTC: TimeZone = TimeZone.UTC

val ONE_HOUR: kotlin.time.Duration = 1.hours
