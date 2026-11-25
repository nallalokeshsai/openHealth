package app.openhealth.core.formula

import app.openhealth.core.observation.ObservationType
import app.openhealth.core.source.SourceRegistry
import kotlinx.datetime.LocalDate

/** One sleep reading as the observation layer stores it, before a session is assembled. */
data class SleepSample(
    val groupKey: String?,
    val type: ObservationType,
    val seconds: Double,
    val localDate: LocalDate,
    /** The source that wrote the reading; two sources are never merged into one night. */
    val sourceId: String? = null,
)

/** One night, assembled from every reading the source wrote for the same session. */
data class SleepSession(
    val groupKey: String?,
    val localDate: LocalDate,
    val totals: SleepTotals,
    val sourceId: String? = null,
)

/**
 * A night is a session, not a calendar day (`pr.md` §6.10). Health Connect writes a
 * session's total under the day it started and each stage under the day the stage
 * started, so a night that crosses midnight would otherwise be split into a fragment on
 * each day. Readings that share a source session id are summed into one [SleepTotals],
 * and only when they carry no id are they grouped by their local day.
 *
 * A night is also deduplicated the way the rollup layer deduplicates sleep (`pr.md` §7.6,
 * `MostDetailedPolicy`): readings from two sources are never summed and never shown as
 * two nights. When more than one source covers the same local day the most detailed one
 * wins, ties going to the higher-priority source, and the others are left out.
 */
object SleepSessions {
    private const val UNGROUPED = "ungrouped:"
    private const val NO_SOURCE = "source:none"

    /**
     * [priority] is the configured source priority for sleep, lower being better; the
     * default treats every source as equally trusted, for a caller with no registry.
     */
    fun collect(
        samples: List<SleepSample>,
        priority: (String) -> Int = { SourceRegistry.UNKNOWN_SOURCE_PRIORITY },
    ): List<SleepSession> {
        val grouped = samples
            .filter { it.seconds.isFinite() && it.seconds >= 0.0 }
            .groupBy { sample ->
                // The source is part of the key: two sources that both report a night
                // without a session id must not be added together (`pr.md` §7.6).
                val within = sample.groupKey?.let { "group:$it" }
                    ?: "$UNGROUPED${sample.localDate}"
                "${sample.sourceId ?: NO_SOURCE}|$within"
            }
        val sessions = grouped.values.mapNotNull { readings ->
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
            Assembled(
                session = SleepSession(
                    groupKey = readings.firstNotNullOfOrNull { it.groupKey },
                    localDate = date,
                    totals = totals,
                    sourceId = readings.firstNotNullOfOrNull { it.sourceId },
                ),
                readings = readings.size,
            )
        }
        return sessions
            .groupBy { it.session.localDate }
            .values
            .flatMap { sameDay -> winningSource(sameDay, priority) }
            .sortedBy { it.localDate }
    }

    private data class Assembled(val session: SleepSession, val readings: Int)

    /** One source wins a day, never the sum and never the longest night. */
    private fun winningSource(
        sameDay: List<Assembled>,
        priority: (String) -> Int,
    ): List<SleepSession> {
        val bySource = sameDay.groupBy { it.session.sourceId }
        if (bySource.size <= 1) return sameDay.map { it.session }
        val winner = bySource.entries
            .sortedWith(
                compareByDescending<Map.Entry<String?, List<Assembled>>> { (_, group) ->
                    group.sumOf { it.readings }
                }.thenBy { (sourceId, _) ->
                    sourceId?.let(priority) ?: SourceRegistry.UNKNOWN_SOURCE_PRIORITY
                }.thenBy { (sourceId, _) -> sourceId.orEmpty() },
            )
            .first()
        return winner.value.map { it.session }
    }

    private fun secondsOf(readings: List<SleepSample>, type: ObservationType): Double? {
        val values = readings.filter { it.type == type }.map { it.seconds }
        return values.takeIf { it.isNotEmpty() }?.sum()
    }
}
