package app.openhealth.core.formula

import app.openhealth.core.observation.ObservationType
import kotlinx.datetime.LocalDate

/** One sleep reading as the observation layer stores it, before a session is assembled. */
data class SleepSample(
    val groupKey: String?,
    val type: ObservationType,
    val seconds: Double,
    val localDate: LocalDate,
)

/** One night, assembled from every reading the source wrote for the same session. */
data class SleepSession(val groupKey: String?, val localDate: LocalDate, val totals: SleepTotals)

/**
 * A night is a session, not a calendar day (`pr.md` §6.10). Health Connect writes a
 * session's total under the day it started and each stage under the day the stage
 * started, so a night that crosses midnight would otherwise be split into a fragment on
 * each day. Readings that share a source session id are summed into one [SleepTotals],
 * and only when they carry no id are they grouped by their local day.
 */
object SleepSessions {
    private const val UNGROUPED = "ungrouped:"

    fun collect(samples: List<SleepSample>): List<SleepSession> {
        val grouped = samples
            .filter { it.seconds.isFinite() && it.seconds >= 0.0 }
            .groupBy { sample ->
                sample.groupKey?.let { "group:$it" } ?: "$UNGROUPED${sample.localDate}"
            }
        return grouped.values.mapNotNull { readings ->
            val total = readings.firstOrNull { it.type == ObservationType.SLEEP_DURATION }
            val stages = readings.filter { it.type != ObservationType.SLEEP_DURATION }
            val totals = SleepTotalsCalculator.of(
                spanSeconds = total?.seconds,
                inBedSeconds = readings.firstOrNull {
                    it.type == ObservationType.SLEEP_IN_BED
                }?.seconds,
                deepSeconds = secondsOf(readings, ObservationType.SLEEP_DEEP),
                lightSeconds = secondsOf(readings, ObservationType.SLEEP_LIGHT),
                remSeconds = secondsOf(readings, ObservationType.SLEEP_REM),
                awakeSeconds = secondsOf(readings, ObservationType.SLEEP_AWAKE),
            ) ?: return@mapNotNull null
            val date = total?.localDate
                ?: stages.minOfOrNull { it.localDate }
                ?: return@mapNotNull null
            SleepSession(
                groupKey = readings.firstNotNullOfOrNull { it.groupKey },
                localDate = date,
                totals = totals,
            )
        }.sortedBy { it.localDate }
    }

    private fun secondsOf(readings: List<SleepSample>, type: ObservationType): Double? {
        val values = readings.filter { it.type == type }.map { it.seconds }
        return values.takeIf { it.isNotEmpty() }?.sum()
    }
}
