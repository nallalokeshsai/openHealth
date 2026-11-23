package app.openhealth.data.rollup

import app.openhealth.core.ids.SourceId
import app.openhealth.core.observation.Observation
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.rollup.DailyRollup
import app.openhealth.core.seam.ClockSeam
import app.openhealth.core.seam.SystemClock
import app.openhealth.core.source.DayResolution
import app.openhealth.core.source.DedupPolicies
import app.openhealth.core.source.DedupReason
import app.openhealth.core.source.RollupAggregation
import app.openhealth.core.source.SourceRegistry
import app.openhealth.core.time.LocalDay
import app.openhealth.core.units.Measure
import app.openhealth.core.units.UcumUnit
import app.openhealth.data.db.OpenHealthDb
import app.openhealth.data.mapping.enumValueOrNull
import app.openhealth.data.repo.ObservationRepository
import app.openhealth.data.repo.RollupRepository
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Layer 3 (`pr.md` §7.3). A day is always rebuilt from its observations: the incremental
 * path deletes and rewrites one day, the full path deletes every row and rewrites every
 * day. Both run the same code, so the two results cannot drift.
 *
 * Downstream values - weekly averages, streaks, sleep debt, adaptive TDEE - are computed
 * on read from these rows and are not stored, so recomputing the affected day is
 * sufficient. Storing one later would need an invalidation rule; see ADR 0005.
 */
class RollupEngine(
    private val observations: ObservationRepository,
    private val rollups: RollupRepository,
    private val db: OpenHealthDb,
    private val registry: SourceRegistry,
    private val clock: ClockSeam = SystemClock,
) {
    fun rebuildDay(date: LocalDate): Int = db.transactionWithResult {
        rebuildDayInternal(date)
    }

    /** Full rebuild: delete everything, then rebuild every day that has observations. */
    fun rebuildAll(): Int = db.transactionWithResult {
        rollups.deleteAll()
        val entryDays = db.nutritionQueries.entryDays().executeAsList().map(LocalDate::parse)
        val vitalDays = db.bodyQueries.vitalDays().executeAsList().map(LocalDate::parse)
        (observations.daysWithObservations() + entryDays + vitalDays)
            .distinct()
            .sumOf { rebuildDayInternal(it) }
    }

    /**
     * A write is followed by a rebuild of its day. `recorded_at` is never touched here:
     * a backfilled observation keeps the moment it was recorded (`pr.md` §6.27, §7.3).
     */
    fun onObservationWritten(observation: Observation): Int = rebuildDay(observation.localDate)

    /**
     * The days whose read-time aggregates are affected by a write to [date]: the ISO
     * week that contains it. Longer windows (28-day baselines, streaks) are computed on
     * read from the stored days, so they need no invalidation list.
     */
    fun affectedDays(date: LocalDate): List<LocalDate> {
        val weekStart = date.minusDays(date.dayOfWeek.isoDayNumber - 1)
        return app.openhealth.core.time.Days.range(weekStart, weekStart.plusDays(LAST_DAY_OF_WEEK))
    }

    private fun rebuildDayInternal(date: LocalDate): Int {
        val byType = observations.forDay(date)
            .filterNot { it.excludedFromTrends }
            .groupBy(Observation::type)
        rollups.deleteDay(date)
        var written = 0
        byType.forEach { (type, candidates) ->
            val rollup = aggregate(type, candidates, date) ?: return@forEach
            rollups.upsert(rollup)
            written += 1
        }
        nutritionRollups(date).forEach {
            rollups.upsert(it)
            written += 1
        }
        vitalRollups(date).forEach {
            rollups.upsert(it)
            written += 1
        }
        return written
    }

    /**
     * Layer 4 nutrition is the source of truth for a diary entry, so its totals are
     * summed here rather than duplicated as observations (`pr.md` §7.3, §6.3). A day's
     * energy and macros are the exact sum of its entries, with no intermediate rounding.
     */
    private fun nutritionRollups(date: LocalDate): List<DailyRollup> {
        val entries = db.nutritionQueries.entriesForDay(date.toString()).executeAsList()
        if (entries.isEmpty()) return emptyList()
        val watermark = entries.maxOf { it.recorded_at }
        val sources = entries.map { it.source_kind }.distinct()
        val count = entries.size.toDouble()
        fun rollup(type: ObservationType, value: Double): DailyRollup = DailyRollup(
            localDate = date,
            type = type,
            value = Measure(value, type.canonicalUnit),
            secondaryValue = count,
            sourceId = null,
            contributingSources = sources.size,
            dedupReason = DedupReason.NO_DEDUP.name,
            watermark = Instant.fromEpochMilliseconds(watermark),
            computedAt = clock.now(),
            detail = null,
        )
        return listOf(
            rollup(ObservationType.NUTRITION_ENERGY, entries.sumOf { it.energy_kcal ?: 0.0 }),
            rollup(ObservationType.NUTRITION_PROTEIN, entries.sumOf { it.protein_g ?: 0.0 }),
            rollup(ObservationType.NUTRITION_CARBS, entries.sumOf { it.carbs_g ?: 0.0 }),
            rollup(ObservationType.NUTRITION_FAT, entries.sumOf { it.fat_g ?: 0.0 }),
            rollup(ObservationType.NUTRITION_FIBRE, entries.sumOf { it.fibre_g ?: 0.0 }),
        )
    }

    /** Layer 4 vitals are averaged per type for the day; every reading stays in its row. */
    private fun vitalRollups(date: LocalDate): List<DailyRollup> {
        val vitals = db.bodyQueries.vitalsForDay(date.toString()).executeAsList()
        if (vitals.isEmpty()) return emptyList()
        return vitals.groupBy { it.type }.mapNotNull { (typeName, readings) ->
            val type = enumValueOrNull<ObservationType>(typeName) ?: return@mapNotNull null
            val canonical = readings.mapNotNull { reading ->
                runCatching {
                    Measure(reading.value_, UcumUnit.byUcum(reading.unit))
                        .convertTo(type.canonicalUnit)
                        .value
                }.getOrNull()
            }
            if (canonical.isEmpty()) return@mapNotNull null
            val sources = readings.map { it.source_id }.distinct()
            DailyRollup(
                localDate = date,
                type = type,
                value = Measure(canonical.average(), type.canonicalUnit),
                secondaryValue = canonical.size.toDouble(),
                sourceId = sources.singleOrNull()?.let(::SourceId),
                contributingSources = sources.size,
                dedupReason = DedupReason.NO_DEDUP.name,
                watermark = Instant.fromEpochMilliseconds(readings.maxOf { it.recorded_at }),
                computedAt = clock.now(),
                detail = null,
            )
        }
    }

    private fun aggregate(
        type: ObservationType,
        candidates: List<Observation>,
        date: LocalDate,
    ): DailyRollup? {
        val zone = candidates.first().provenance.zone
        val resolution = DedupPolicies.forType(type)
            .resolve(candidates, registry, LocalDay(date, zone))
        return when (resolution) {
            is DayResolution.Empty -> null
            is DayResolution.Selected -> rollupOf(type, resolution, date)
        }
    }

    private fun rollupOf(
        type: ObservationType,
        resolution: DayResolution.Selected,
        date: LocalDate,
    ): DailyRollup {
        val selected = resolution.observations
        val ordered = selected.sortedBy { it.provenance.happenedAt }
        val numbers = ordered.map { it.value.value }
        val value = when (DedupPolicies.aggregation(type)) {
            RollupAggregation.SUM -> numbers.sum()
            RollupAggregation.MEAN -> numbers.average()
            RollupAggregation.LAST -> ordered.last().value.value
            RollupAggregation.MIN -> numbers.minOrNull() ?: 0.0
            RollupAggregation.MAX -> numbers.maxOrNull() ?: 0.0
        }
        val isAggregated = DedupPolicies.aggregation(type) != RollupAggregation.SUM ||
            resolution.reason == DedupReason.NO_DEDUP
        val contributingSources = selected.map { it.provenance.sourceId }.distinct()
        return DailyRollup(
            localDate = date,
            type = type,
            value = Measure(value, type.canonicalUnit),
            secondaryValue = if (isAggregated) selected.size.toDouble() else null,
            sourceId = resolution.sourceId.takeIf { contributingSources.size == 1 },
            contributingSources = contributingSources.size,
            dedupReason = resolution.reason.name,
            watermark = selected.maxOf { it.provenance.recordedAt },
            computedAt = clock.now(),
            detail = null,
        )
    }

    private fun LocalDate.minusDays(days: Int): LocalDate = minus(DatePeriod(days = days))

    private fun LocalDate.plusDays(days: Int): LocalDate = plus(DatePeriod(days = days))

    private companion object {
        const val LAST_DAY_OF_WEEK = 6
    }
}
