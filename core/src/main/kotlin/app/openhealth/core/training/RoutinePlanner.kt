package app.openhealth.core.training

import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus

/**
 * Routines by weekday or by rotation, A/B/C (`pr.md` §6.5). A routine has either one ISO
 * weekday (1 = Monday) or a rotation position; a favourite breaks a tie, and a folder is
 * only a label.
 */
data class RoutinePlacement(
    val id: String,
    val name: String,
    val weekday: Int? = null,
    val rotationIndex: Int? = null,
    val favourite: Boolean = false,
    val folder: String? = null,
) {
    /** A routine with no weekday runs in the rotation, even if its index is unset. */
    val rotates: Boolean get() = weekday == null
}

/** Why today's plan is the routine it is, so the card can say it rather than guess. */
enum class PlanReason { WEEKDAY, ROTATION_NEXT, ROTATION_FIRST }

data class TodayPlan(val routineId: String, val name: String, val reason: PlanReason)

/**
 * The Train root's today plan (`DESIGN.md` §14.2 Train). A weekday routine owns its day;
 * otherwise the next unscheduled routine in the rotation follows the last one trained.
 * Nothing is invented when neither exists.
 */
object RoutinePlanner {
    fun today(
        routines: List<RoutinePlacement>,
        lastRoutineId: String?,
        date: LocalDate,
    ): TodayPlan? {
        val day = date.dayOfWeek.isoDayNumber
        val weekdayRoutine = routines
            .filter { it.weekday == day }
            .sortedWith(compareByDescending<RoutinePlacement> { it.favourite }.thenBy { it.name })
            .firstOrNull()
        val rotation = routines
            .filter { it.rotates }
            .sortedBy { it.rotationIndex ?: Int.MAX_VALUE }
        val lastPosition = rotation.indexOfFirst { it.id == lastRoutineId }
        return when {
            weekdayRoutine != null ->
                TodayPlan(weekdayRoutine.id, weekdayRoutine.name, PlanReason.WEEKDAY)
            rotation.isEmpty() -> null
            lastPosition >= 0 -> {
                val next = rotation[(lastPosition + 1) % rotation.size]
                TodayPlan(next.id, next.name, PlanReason.ROTATION_NEXT)
            }
            else -> TodayPlan(rotation.first().id, rotation.first().name, PlanReason.ROTATION_FIRST)
        }
    }

    /** The seven days of the week that contains [date], Monday first (`DESIGN.md` §14.2). */
    fun week(date: LocalDate): List<LocalDate> {
        val offset = date.dayOfWeek.isoDayNumber - 1
        val monday = date.plusDays(-offset)
        return (0 until DAYS_IN_WEEK).map { monday.plusDays(it) }
    }
}

/** The rotation letter a position earns: 1 -> A, 2 -> B, 27 -> AA. */
fun rotationLetter(index: Int?): String? {
    val value = index?.takeIf { it > 0 } ?: return null
    val letter = 'A' + ((value - 1) % LETTERS)
    val cycle = (value - 1) / LETTERS
    return if (cycle == 0) letter.toString() else "$letter${cycle + 1}"
}

private const val DAYS_IN_WEEK = 7
private const val LETTERS = 26

private fun LocalDate.plusDays(days: Int): LocalDate =
    plus(kotlinx.datetime.DatePeriod(days = days))
