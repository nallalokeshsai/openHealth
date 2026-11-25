package app.openhealth.core.vitals

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * The home blood-pressure protocol (`pr.md` §6.9, `DESIGN.md` §14.2 Vitals detail):
 * two readings in the morning and two in the evening for seven days, from the ESC/ESH
 * 2023 guideline recorded in [app.openhealth.core.citation.Citations].
 *
 * The protocol is a reading pattern, not a diagnosis or a target: the average it shows is
 * the average of the person's own readings over the window, drawn against the cited home
 * range. Nothing here decides that a reading is a condition.
 */
data class BpProtocolReading(val date: LocalDate, val slot: HomeSlot)

data class BpProtocolDay(
    val date: LocalDate,
    val morning: Int,
    val evening: Int,
    val targetPerSlot: Int,
) {
    val complete: Boolean get() = morning >= targetPerSlot && evening >= targetPerSlot
}

data class BpProtocolState(
    val startDate: LocalDate,
    val days: List<BpProtocolDay>,
    val targetPerSlot: Int,
    /** Readings recorded inside this protocol window. */
    val recorded: Int,
    val target: Int,
    val complete: Boolean,
) {
    val fraction: Float get() = if (target == 0) 0f else recorded.toFloat() / target

    /** The next slot that is short of its target, for the sheet's one action. */
    fun nextSlot(today: LocalDate): BpSlotTarget? {
        val day = days.firstOrNull { it.date == today } ?: days.lastOrNull() ?: return null
        if (day.morning < targetPerSlot) return BpSlotTarget(day.date, HomeSlot.MORNING)
        if (day.evening < targetPerSlot) return BpSlotTarget(day.date, HomeSlot.EVENING)
        val later = days.firstOrNull { it.date > today && !it.complete } ?: return null
        if (later.morning < targetPerSlot) return BpSlotTarget(later.date, HomeSlot.MORNING)
        if (later.evening < targetPerSlot) return BpSlotTarget(later.date, HomeSlot.EVENING)
        return null
    }
}

data class BpSlotTarget(val date: LocalDate, val slot: HomeSlot)

object BloodPressureProtocol {
    const val DAYS = 7
    const val READINGS_PER_SLOT = 2

    fun state(
        startDate: LocalDate,
        today: LocalDate,
        readings: List<BpProtocolReading>,
    ): BpProtocolState {
        val window = (0 until DAYS).map { startDate.plus(DatePeriod(days = it)) }
        val counts = readings.groupBy { it.date to it.slot }
            .mapValues { (_, same) -> same.size }
        val days = window.map { date ->
            BpProtocolDay(
                date = date,
                morning = counts[date to HomeSlot.MORNING] ?: 0,
                evening = counts[date to HomeSlot.EVENING] ?: 0,
                targetPerSlot = READINGS_PER_SLOT,
            )
        }
        val recorded = days.sumOf { it.morning + it.evening }
            .coerceAtMost(DAYS * READINGS_PER_SLOT * 2)
        return BpProtocolState(
            startDate = startDate,
            days = days,
            targetPerSlot = READINGS_PER_SLOT,
            recorded = recorded,
            target = DAYS * READINGS_PER_SLOT * 2,
            complete = days.all { it.complete },
        )
    }

    /** The common home-monitoring average rule: every reading in the window counts once. */
    data class Average(val systolic: Double, val diastolic: Double, val readings: Int)

    fun average(
        readings: List<Pair<Double, Double>>,
    ): Average? = if (readings.isEmpty()) {
        null
    } else {
        Average(
            systolic = readings.map { it.first }.average(),
            diastolic = readings.map { it.second }.average(),
            readings = readings.size,
        )
    }

    /** True when a protocol started today is still being collected. */
    fun withinWindow(start: LocalDate, today: LocalDate): Boolean =
        today >= start && today < start.plus(DatePeriod(days = DAYS))
}
