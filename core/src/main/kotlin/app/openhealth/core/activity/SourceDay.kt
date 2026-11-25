package app.openhealth.core.activity

import app.openhealth.core.ids.SourceId
import app.openhealth.core.observation.Observation
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.source.DayResolution
import app.openhealth.core.source.DedupPolicies
import app.openhealth.core.source.DedupReason
import app.openhealth.core.source.RollupAggregation
import app.openhealth.core.source.SourceRegistry
import app.openhealth.core.time.LocalDay
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/** What one source says about one type on one day, for the "which source won" line. */
data class SourceDayContribution(
    val sourceId: SourceId,
    val family: String,
    val total: Double,
    val segments: Int,
    val firstAt: Instant,
    val lastAt: Instant,
    val isDailyTotal: Boolean,
)

/**
 * Who reported what for one day, and which source the rollup took (`pr.md` §7.6). It is
 * a display model: the stored rollup stays the one number of record, and this only
 * explains it. The losing sources are listed with their own figures, because they stay
 * in layer 2 and the provenance sheet must be able to show them.
 */
data class SourceDayBreakdown(
    val type: ObservationType,
    val winningSource: SourceId?,
    val winningFamily: String?,
    val reason: DedupReason?,
    val aggregation: RollupAggregation,
    val contributions: List<SourceDayContribution>,
) {
    val losers: List<SourceDayContribution>
        get() = contributions.filter { it.sourceId != winningSource }
}

object SourceDay {
    fun breakdown(
        type: ObservationType,
        date: LocalDate,
        observations: List<Observation>,
        registry: SourceRegistry,
    ): SourceDayBreakdown {
        val candidates = observations.filter { it.type == type && it.localDate == date }
        val aggregation = DedupPolicies.aggregation(type)
        val contributions = perSource(candidates, aggregation, registry)
        if (candidates.isEmpty()) {
            return SourceDayBreakdown(type, null, null, null, aggregation, emptyList())
        }
        val zone = candidates.first().provenance.zone
        val resolution = DedupPolicies.forType(type)
            .resolve(candidates, registry, LocalDay(date, zone))
        return when (resolution) {
            is DayResolution.Empty ->
                SourceDayBreakdown(type, null, null, null, aggregation, contributions)

            is DayResolution.Selected -> SourceDayBreakdown(
                type = type,
                winningSource = resolution.sourceId,
                winningFamily = resolution.family,
                reason = resolution.reason,
                aggregation = aggregation,
                contributions = contributions,
            )
        }
    }

    /**
     * A source's day figure follows the same rule the rollup uses: a daily total replaces
     * that source's intervals, and the parts are summed or averaged per type. Several
     * sources are never added together here either.
     */
    private fun perSource(
        candidates: List<Observation>,
        aggregation: RollupAggregation,
        registry: SourceRegistry,
    ): List<SourceDayContribution> = candidates
        .groupBy { it.provenance.sourceId }
        .map { (sourceId, all) ->
            val totals = all.filter { it.end == null }
            val used = if (aggregation == RollupAggregation.SUM && totals.isNotEmpty()) {
                listOf(totals.maxBy { it.provenance.happenedAt })
            } else {
                all
            }
            val values = used.map { it.value.value }
            val total = when (aggregation) {
                RollupAggregation.SUM -> values.sum()
                RollupAggregation.MEAN -> values.average()
                RollupAggregation.LAST -> used.maxBy { it.provenance.happenedAt }.value.value
                RollupAggregation.MIN -> values.min()
                RollupAggregation.MAX -> values.max()
            }
            SourceDayContribution(
                sourceId = sourceId,
                family = registry.familyOf(sourceId),
                total = total,
                segments = used.size,
                firstAt = all.minOf { it.start },
                lastAt = all.maxOf { it.end ?: it.start },
                isDailyTotal = totals.isNotEmpty(),
            )
        }
        .sortedWith(compareBy({ registry.priorityOf(it.sourceId, candidates.first().type) }))
}
