@file:Suppress(
    "MagicNumber",
    "ReturnCount",
    "LongMethod",
    "LongParameterList",
    "CyclomaticComplexMethod",
)

package app.openhealth.core.clinical

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus

/**
 * Adherence is what the person logged against what the schedule expected (`pr.md` §6.9).
 * It never counts a dose that was not logged as missed: the app cannot know whether a
 * medicine was taken and not recorded. The report therefore prints expected, taken and
 * skipped, and a percentage only when at least one dose was expected.
 */
object Adherence {
    private const val MAX_WINDOW_DAYS = 3660

    fun expectedDoses(medication: Medication, from: LocalDate, to: LocalDate): Int {
        if (to < from) return 0
        if (from.daysUntil(to) > MAX_WINDOW_DAYS) return 0
        if (medication.schedule.asNeeded || medication.schedule.times.isEmpty()) return 0
        val start = maxOf(from, medication.startedOn ?: from)
        val end = minOf(to, medication.endedOn ?: to)
        if (end < start) return 0
        var date = start
        var expected = 0
        while (date <= end) {
            val isoDay = date.dayOfWeek.isoDayNumber
            if (medication.schedule.daysOfWeek.isEmpty() ||
                isoDay in medication.schedule.daysOfWeek
            ) {
                expected += medication.schedule.times.size
            }
            date = date.plus(DatePeriod(days = 1))
        }
        return expected
    }

    fun compute(
        medication: Medication,
        doses: List<MedicationDose>,
        from: LocalDate,
        to: LocalDate,
    ): MedicationAdherence {
        val own = doses.filter {
            it.medicationId == medication.id && it.localDate >= from && it.localDate <= to
        }
        return MedicationAdherence(
            medicationId = medication.id,
            expected = expectedDoses(medication, from, to),
            taken = own.count { !it.skipped },
            skipped = own.count { it.skipped },
        )
    }
}
