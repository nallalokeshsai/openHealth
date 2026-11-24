package app.openhealth.data.rollup

import app.openhealth.core.formula.VolumeSet
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
    registry: SourceRegistry,
    private val clock: ClockSeam = SystemClock,
    private val registryProvider: (() -> SourceRegistry)? = null,
) {
    private var registry: SourceRegistry = registry

    /** Re-reads the registry after a source or priority change, before a rebuild. */
    fun refreshRegistry() {
        registry = registryProvider?.invoke() ?: registry
    }

    fun rebuildDay(date: LocalDate): Int = db.transactionWithResult {
        rebuildDayInternal(date)
    }

    /** Full rebuild: delete everything, then rebuild every day that has observations. */
    fun rebuildAll(): Int = db.transactionWithResult {
        rollups.deleteAll()
        val entryDays = db.nutritionQueries.entryDays().executeAsList().map(LocalDate::parse)
        val vitalDays = db.bodyQueries.vitalDays().executeAsList().map(LocalDate::parse)
        val measurementDays = db.bodyQueries.measurementDays().executeAsList().map(LocalDate::parse)
        val sessionDays = db.trainingQueries.sessionDays().executeAsList()
            .map(LocalDate::parse)
        (
            observations.daysWithObservations() + entryDays + vitalDays + measurementDays +
                sessionDays
            )
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
        val writtenTypes = mutableSetOf<ObservationType>()
        byType.forEach { (type, candidates) ->
            val rollup = aggregate(type, candidates, date) ?: return@forEach
            rollups.upsert(rollup)
            writtenTypes += type
            written += 1
        }
        nutritionRollups(date, writtenTypes).forEach {
            rollups.upsert(it)
            written += 1
        }
        measurementRollups(date, writtenTypes).forEach {
            rollups.upsert(it)
            written += 1
        }
        trainingRollups(date, writtenTypes).forEach {
            rollups.upsert(it)
            written += 1
        }
        vitalRollups(date, writtenTypes).forEach {
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
    private fun nutritionRollups(date: LocalDate, skip: Set<ObservationType>): List<DailyRollup> {
        val entries = db.nutritionQueries.entriesForDay(date.toString()).executeAsList()
        if (entries.isEmpty()) return emptyList()
        val watermark = entries.maxOf { it.recorded_at }
        val sources = entries.mapNotNull { it.source_id }.distinct()
        val count = entries.size.toDouble()
        val nutriments = listOf<Pair<ObservationType, (app.openhealth.data.db.Entry) -> Double?>>(
            ObservationType.NUTRITION_ENERGY to { it.energy_kcal },
            ObservationType.NUTRITION_PROTEIN to { it.protein_g },
            ObservationType.NUTRITION_CARBS to { it.carbs_g },
            ObservationType.NUTRITION_FAT to { it.fat_g },
            ObservationType.NUTRITION_FIBRE to { it.fibre_g },
        )
        return nutriments.mapNotNull { (type, valueOf) ->
            if (type in skip) return@mapNotNull null
            val values = entries.mapNotNull(valueOf)
            // A macro no entry reports is missing data, not a zero.
            if (values.isEmpty()) return@mapNotNull null
            DailyRollup(
                localDate = date,
                type = type,
                value = Measure(values.sum(), type.canonicalUnit),
                secondaryValue = count,
                sourceId = sources.singleOrNull()?.let(::SourceId),
                contributingSources = sources.size,
                dedupReason = if (sources.size == 1) {
                    DedupReason.ONE_SOURCE_WINS.name
                } else {
                    DedupReason.NO_DEDUP.name
                },
                watermark = Instant.fromEpochMilliseconds(watermark),
                computedAt = clock.now(),
                detail = null,
            )
        }
    }

    /** Layer 4 vitals follow the type's aggregation, never a blind cross-device mean. */
    private fun vitalRollups(date: LocalDate, skip: Set<ObservationType>): List<DailyRollup> {
        val vitals = db.bodyQueries.vitalsForDay(date.toString()).executeAsList()
            .filterNot { it.excluded_from_trends }
        if (vitals.isEmpty()) return emptyList()
        return vitals.groupBy { it.type }.mapNotNull { (typeName, readings) ->
            val type = enumValueOrNull<ObservationType>(typeName) ?: return@mapNotNull null
            if (type in skip) return@mapNotNull null
            val canonical = readings.mapNotNull { reading ->
                runCatching {
                    Measure(reading.value_, UcumUnit.byUcum(reading.unit))
                        .convertTo(type.canonicalUnit)
                        .value
                }.getOrNull()
            }
            if (canonical.isEmpty()) return@mapNotNull null
            val sources = readings.map { it.source_id }.distinct()
            val value = when (DedupPolicies.aggregation(type)) {
                RollupAggregation.MEAN -> canonical.average()
                RollupAggregation.SUM -> canonical.sum()
                RollupAggregation.LAST -> readings.maxBy { it.happened_at }.let { last ->
                    runCatching {
                        Measure(last.value_, UcumUnit.byUcum(last.unit))
                            .convertTo(type.canonicalUnit)
                            .value
                    }.getOrDefault(canonical.last())
                }

                RollupAggregation.MIN -> canonical.min()
                RollupAggregation.MAX -> canonical.max()
            }
            DailyRollup(
                localDate = date,
                type = type,
                value = Measure(value, type.canonicalUnit),
                secondaryValue = canonical.size.toDouble(),
                sourceId = sources.singleOrNull()?.let(::SourceId),
                contributingSources = sources.size,
                dedupReason = if (sources.size == 1) {
                    DedupReason.ONE_SOURCE_WINS.name
                } else {
                    DedupReason.NO_DEDUP.name
                },
                watermark = Instant.fromEpochMilliseconds(readings.maxOf { it.recorded_at }),
                computedAt = clock.now(),
                detail = null,
            )
        }
    }

    /**
     * Layer 4 sessions are the source of truth for training (`pr.md` §7.3, §7.4): a day's
     * volume, set count and duration are summed here, never stored twice. Only a finished
     * session counts; an open session is still being recorded and is rolled up when it is
     * finished. Moving a session rebuilds both the old and the new day.
     */
    private fun trainingRollups(date: LocalDate, skip: Set<ObservationType>): List<DailyRollup> {
        val sessions = db.trainingQueries.sessionsForDay(date.toString()).executeAsList()
            .filter { it.ended_at != null }
        val sets = sessions.flatMap { session ->
            db.trainingQueries.setsForSession(session.id).executeAsList()
        }
        val work = sets.filterNot { it.is_warmup }
        val sources = (sessions.map { it.source_id } + sets.map { it.source_id })
            .distinct()
        val durationSeconds = sessions.sumOf { session ->
            ((session.ended_at ?: session.started_at) - session.started_at)
                .coerceAtLeast(0L) / MILLIS_PER_SECOND
        }
        // The layer-3 volume is the same number the session summary shows, so it uses the
        // one formula in `:core` rather than a second sum here: a bodyweight set moves the
        // person's own mass less any assistance, plus any band (`pr.md` §6.5, §7.3).
        val bodyweightKg = bodyweightFor(date)
        val volumeKg = work.sumOf { set ->
            VolumeSet(
                weightKg = set.weight_kg,
                reps = set.reps.toInt(),
                isWarmup = false,
                isBodyweight = set.is_bodyweight,
                bandKg = set.band_kg ?: 0.0,
                assistanceKg = set.assistance_kg ?: 0.0,
                bodyweightKg = bodyweightKg,
            ).externalLoadKg * set.reps
        }
        return if (sessions.isEmpty()) {
            emptyList()
        } else {
            listOf(
                ObservationType.TRAINING_VOLUME to volumeKg,
                ObservationType.TRAINING_SETS to work.size.toDouble(),
                ObservationType.TRAINING_DURATION to durationSeconds.toDouble(),
            ).mapNotNull { (type, value) ->
                if (type in skip) return@mapNotNull null
                DailyRollup(
                    localDate = date,
                    type = type,
                    value = Measure(value, type.canonicalUnit),
                    secondaryValue = sessions.size.toDouble(),
                    sourceId = sources.singleOrNull()?.let(::SourceId),
                    contributingSources = sources.size,
                    dedupReason = if (sources.size == 1) {
                        DedupReason.ONE_SOURCE_WINS.name
                    } else {
                        DedupReason.NO_DEDUP.name
                    },
                    watermark = Instant.fromEpochMilliseconds(
                        (sessions.map { it.recorded_at } + sets.map { it.recorded_at })
                            .maxOrNull() ?: 0L,
                    ),
                    computedAt = clock.now(),
                    detail = null,
                )
            }
        }
    }

    /**
     * The bodyweight a bodyweight set moved on [date]: that day's weight rollup when the
     * person weighed in, otherwise the profile's recorded weight. Null means unknown, and
     * an unknown bodyweight contributes no kilograms rather than an invented number.
     */
    private fun bodyweightFor(date: LocalDate): Double? =
        rollups.forDayAndType(date, ObservationType.WEIGHT)?.let { rollup ->
            runCatching { rollup.value.convertTo(UcumUnit.KILOGRAM).value }
                .getOrDefault(rollup.value.value)
        } ?: db.profileQueries.activeProfile().executeAsOneOrNull()?.weight_kg

    /**
     * A tape or scale measurement that names an observation type (weight, body fat, lean
     * mass) is a layer-3 value too; the latest one for the day wins. Measurements of a
     * type with no observation equivalent (waist, neck) stay in layer 4.
     */
    private fun measurementRollups(
        date: LocalDate,
        skip: Set<ObservationType>,
    ): List<DailyRollup> {
        val measurements = db.bodyQueries.measurementsForDay(date.toString()).executeAsList()
            .filterNot { it.excluded_from_trends }
        if (measurements.isEmpty()) return emptyList()
        return measurements.groupBy { it.type }.mapNotNull { (typeName, readings) ->
            val type = enumValueOrNull<ObservationType>(typeName) ?: return@mapNotNull null
            if (type in skip) return@mapNotNull null
            val latest = readings.maxBy { it.happened_at }
            val value = runCatching {
                Measure(latest.value_, UcumUnit.byUcum(latest.unit))
                    .convertTo(type.canonicalUnit)
                    .value
            }.getOrNull() ?: return@mapNotNull null
            val sources = readings.map { it.source_id }.distinct()
            DailyRollup(
                localDate = date,
                type = type,
                value = Measure(value, type.canonicalUnit),
                secondaryValue = readings.size.toDouble(),
                sourceId = sources.singleOrNull()?.let(::SourceId),
                contributingSources = sources.size,
                dedupReason = if (sources.size == 1) {
                    DedupReason.ONE_SOURCE_WINS.name
                } else {
                    DedupReason.NO_DEDUP.name
                },
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
        val aggregation = DedupPolicies.aggregation(type)
        val ordered = resolution.observations.sortedBy { it.provenance.happenedAt }
        // A source that reports a daily total must not also have its intervals summed
        // into it: a total is the day, the intervals are its parts. The rule is per
        // source: one source's total never discards another source's parts, which is
        // what a `SumAllPolicy` type (water, nutrition) keeps on purpose.
        val used = if (aggregation == RollupAggregation.SUM) {
            ordered.groupBy { it.provenance.sourceId }.values.flatMap { perSource ->
                val totals = perSource.filter { it.end == null }
                if (totals.isNotEmpty()) listOf(totals.last()) else perSource
            }
        } else {
            ordered
        }
        val numbers = used.map { it.value.value }
        val value = when (aggregation) {
            RollupAggregation.SUM -> numbers.sum()
            RollupAggregation.MEAN -> numbers.average()
            RollupAggregation.LAST -> used.last().value.value
            RollupAggregation.MIN -> numbers.minOrNull() ?: 0.0
            RollupAggregation.MAX -> numbers.maxOrNull() ?: 0.0
        }
        val isAggregated = aggregation != RollupAggregation.SUM ||
            resolution.reason == DedupReason.NO_DEDUP
        val contributingSources = used.map { it.provenance.sourceId }.distinct()
        return DailyRollup(
            localDate = date,
            type = type,
            value = Measure(value, type.canonicalUnit),
            secondaryValue = if (isAggregated) used.size.toDouble() else null,
            sourceId = resolution.sourceId.takeIf { contributingSources.size == 1 },
            contributingSources = contributingSources.size,
            dedupReason = resolution.reason.name,
            watermark = used.maxOf { it.provenance.recordedAt },
            computedAt = clock.now(),
            detail = null,
        )
    }

    private fun LocalDate.minusDays(days: Int): LocalDate = minus(DatePeriod(days = days))

    private fun LocalDate.plusDays(days: Int): LocalDate = plus(DatePeriod(days = days))

    private companion object {
        const val LAST_DAY_OF_WEEK = 6
        const val MILLIS_PER_SECOND = 1_000L
    }
}
