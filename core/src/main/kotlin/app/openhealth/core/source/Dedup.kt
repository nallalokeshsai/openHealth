package app.openhealth.core.source

import app.openhealth.core.ids.SourceId
import app.openhealth.core.observation.Observation
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.observation.SourceKind
import app.openhealth.core.observation.TemporalShape
import app.openhealth.core.time.LocalDay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/** Why a day resolved the way it did; surfaced as one plain sentence (`pr.md` §7.6). */
enum class DedupReason {
    ONE_SOURCE_WINS,
    GAP_FALLBACK,
    MANUAL_OVERRIDE,
    LATER_WINS,
    DUPLICATE_MERGED,
    MOST_DETAILED,
    NO_DEDUP,
}

/**
 * The observations of one source that represent a day, or none. A resolution never
 * contains two sources for a type that is deduplicated: summing two devices is exactly
 * the bug `pr.md` §7.6 forbids.
 */
sealed interface DayResolution {
    data class Selected(
        val observations: List<Observation>,
        val sourceId: SourceId,
        val family: String,
        val reason: DedupReason,
    ) : DayResolution

    data object Empty : DayResolution
}

/** One rule per observation type. `pr.md` §7.6 lists the defaults it implements. */
fun interface DedupPolicy {
    fun resolve(
        candidates: List<Observation>,
        registry: SourceRegistry,
        day: LocalDay,
    ): DayResolution
}

/** How the rollup engine turns the resolved observations into one number for the day. */
enum class RollupAggregation { SUM, MEAN, LAST, MIN, MAX }

object DedupPolicies {
    private val manualFirst = ManualFirstPerDayPolicy()
    private val oneSource = OneSourcePerDayPolicy()
    private val mostDetailed = MostDetailedPolicy()
    private val keepAll = KeepAllPolicy()
    private val sumAll = SumAllPolicy()

    fun forType(type: ObservationType): DedupPolicy = when (type) {
        ObservationType.STEPS,
        ObservationType.DISTANCE,
        ObservationType.ACTIVE_CALORIES,
        ObservationType.ACTIVE_MINUTES,
        ObservationType.FLIGHTS_CLIMBED,
        -> oneSource

        ObservationType.WEIGHT,
        ObservationType.BODY_FAT_PERCENT,
        ObservationType.RESTING_HEART_RATE,
        -> manualFirst

        ObservationType.SLEEP_DURATION,
        ObservationType.SLEEP_DEEP,
        ObservationType.SLEEP_LIGHT,
        ObservationType.SLEEP_REM,
        ObservationType.SLEEP_AWAKE,
        ObservationType.SLEEP_IN_BED,
        -> mostDetailed

        ObservationType.HEART_RATE,
        ObservationType.HEART_RATE_VARIABILITY,
        ObservationType.BLOOD_PRESSURE_SYSTOLIC,
        ObservationType.BLOOD_PRESSURE_DIASTOLIC,
        ObservationType.BLOOD_GLUCOSE,
        ObservationType.BODY_TEMPERATURE,
        ObservationType.OXYGEN_SATURATION,
        ObservationType.RESPIRATORY_RATE,
        ObservationType.MOOD,
        ObservationType.STRESS,
        ObservationType.LEAN_MASS,
        -> keepAll

        ObservationType.NUTRITION_ENERGY,
        ObservationType.NUTRITION_PROTEIN,
        ObservationType.NUTRITION_CARBS,
        ObservationType.NUTRITION_FAT,
        ObservationType.NUTRITION_FIBRE,
        ObservationType.WATER,
        ObservationType.CAFFEINE,
        -> sumAll
    }

    fun aggregation(type: ObservationType): RollupAggregation = when (type) {
        ObservationType.WEIGHT,
        ObservationType.BODY_FAT_PERCENT,
        ObservationType.LEAN_MASS,
        ObservationType.RESTING_HEART_RATE,
        -> RollupAggregation.LAST

        ObservationType.HEART_RATE,
        ObservationType.HEART_RATE_VARIABILITY,
        ObservationType.BLOOD_PRESSURE_SYSTOLIC,
        ObservationType.BLOOD_PRESSURE_DIASTOLIC,
        ObservationType.BLOOD_GLUCOSE,
        ObservationType.BODY_TEMPERATURE,
        ObservationType.OXYGEN_SATURATION,
        ObservationType.RESPIRATORY_RATE,
        ObservationType.MOOD,
        ObservationType.STRESS,
        -> RollupAggregation.MEAN

        ObservationType.STEPS,
        ObservationType.DISTANCE,
        ObservationType.ACTIVE_CALORIES,
        ObservationType.ACTIVE_MINUTES,
        ObservationType.FLIGHTS_CLIMBED,
        ObservationType.SLEEP_DURATION,
        ObservationType.SLEEP_DEEP,
        ObservationType.SLEEP_LIGHT,
        ObservationType.SLEEP_REM,
        ObservationType.SLEEP_AWAKE,
        ObservationType.SLEEP_IN_BED,
        ObservationType.NUTRITION_ENERGY,
        ObservationType.NUTRITION_PROTEIN,
        ObservationType.NUTRITION_CARBS,
        ObservationType.NUTRITION_FAT,
        ObservationType.NUTRITION_FIBRE,
        ObservationType.WATER,
        ObservationType.CAFFEINE,
        -> RollupAggregation.SUM
    }
}

/**
 * Steps, distance and active minutes: the highest-priority source that covers the day
 * wins. A source with a gap longer than an hour is not stitched together with another;
 * the whole day falls back to the next complete source (`pr.md` §7.6).
 */
class OneSourcePerDayPolicy : DedupPolicy {
    override fun resolve(
        candidates: List<Observation>,
        registry: SourceRegistry,
        day: LocalDay,
    ): DayResolution {
        if (candidates.isEmpty()) return DayResolution.Empty
        val bySource = candidates.groupBy { it.provenance.sourceId }
        val ordered = bySource.entries.sortedWith(
            compareBy({ registry.priorityOf(it.key, candidates.first().type) }, { it.key.value }),
        )
        val preferred = ordered.first()
        val complete = ordered.firstOrNull { (_, observations) -> covers(observations, day) }
        val chosen = complete ?: preferred
        val reason = if (complete == null || chosen.key != preferred.key) {
            DedupReason.GAP_FALLBACK
        } else {
            DedupReason.ONE_SOURCE_WINS
        }
        return DayResolution.Selected(
            observations = chosen.value,
            sourceId = chosen.key,
            family = registry.familyOf(chosen.key),
            reason = reason,
        )
    }

    private fun covers(observations: List<Observation>, day: LocalDay): Boolean {
        val dailyTotal = observations.any {
            it.type.shape == TemporalShape.DAILY_TOTAL && it.end == null
        }
        val dayStart = day.startInclusive()
        val dayEnd = day.endExclusive()
        val intervals = observations
            .filter { it.end != null }
            .map { Pair(it.start, it.end!!) }
            .sortedBy { it.first }
        val coverage = intervals.fold(Coverage(cursor = dayStart)) { state, interval ->
            val from = maxOf(interval.first, dayStart)
            val to = minOf(interval.second, dayEnd)
            when {
                state.gapTooLarge || to <= from -> state
                from - state.cursor > MAX_GAP -> state.copy(gapTooLarge = true)
                else -> Coverage(
                    covered = state.covered + (to - from),
                    cursor = maxOf(state.cursor, to),
                )
            }
        }
        return dailyTotal ||
            (
                intervals.isNotEmpty() &&
                    !coverage.gapTooLarge &&
                    dayEnd - coverage.cursor <= MAX_GAP &&
                    coverage.covered >= day.length() - MAX_GAP
                )
    }

    private data class Coverage(
        val covered: Duration = Duration.ZERO,
        val cursor: kotlin.time.Instant,
        val gapTooLarge: Boolean = false,
    )

    private companion object {
        val MAX_GAP: Duration = 1.hours
    }
}

/**
 * Weight and resting heart rate: one value per local day. A manual entry beats a device
 * entry, and between two of the same kind the later wins. Two values within a minute
 * are duplicates and merge into one.
 */
class ManualFirstPerDayPolicy : DedupPolicy {
    override fun resolve(
        candidates: List<Observation>,
        registry: SourceRegistry,
        day: LocalDay,
    ): DayResolution {
        if (candidates.isEmpty()) return DayResolution.Empty
        val winner = candidates.sortedWith(
            compareBy(
                { if (kindOf(it, registry) == SourceKind.MANUAL) 0 else 1 },
                { -it.provenance.happenedAt.epochSeconds },
                { it.provenance.recordedAt.epochSeconds },
            ),
        ).first()
        val reason = when {
            candidates.size == 1 -> DedupReason.ONE_SOURCE_WINS
            candidates.any { kindOf(it, registry) == SourceKind.MANUAL } &&
                kindOf(winner, registry) == SourceKind.MANUAL &&
                candidates.any {
                    kindOf(it, registry) != SourceKind.MANUAL
                } -> DedupReason.MANUAL_OVERRIDE
            candidates.any {
                it !== winner && isDuplicate(it, winner)
            } -> DedupReason.DUPLICATE_MERGED
            else -> DedupReason.LATER_WINS
        }
        return DayResolution.Selected(
            observations = listOf(winner),
            sourceId = winner.provenance.sourceId,
            family = registry.familyOf(winner.provenance.sourceId),
            reason = reason,
        )
    }

    private fun kindOf(observation: Observation, registry: SourceRegistry): SourceKind? =
        registry.get(observation.provenance.sourceId)?.kind

    private fun isDuplicate(first: Observation, second: Observation): Boolean {
        val apart = kotlin.math.abs(
            (first.provenance.happenedAt - second.provenance.happenedAt).inWholeSeconds,
        )
        return apart <= DUPLICATE_WINDOW_SECONDS
    }

    private companion object {
        const val DUPLICATE_WINDOW_SECONDS = 60L
    }
}

/**
 * Sleep: the most detailed complete record wins. A full night is never replaced by a
 * partial one, so the source with the greatest recorded duration wins.
 */
class MostDetailedPolicy : DedupPolicy {
    override fun resolve(
        candidates: List<Observation>,
        registry: SourceRegistry,
        day: LocalDay,
    ): DayResolution {
        if (candidates.isEmpty()) return DayResolution.Empty
        val bySource = candidates.groupBy { it.provenance.sourceId }
        val winner = bySource.entries
            .sortedWith(
                compareByDescending<Map.Entry<SourceId, List<Observation>>> { (_, observations) ->
                    observations.sumOf { it.value.value }
                }.thenBy { (sourceId, _) ->
                    registry.priorityOf(sourceId, candidates.first().type)
                },
            )
            .first()
        return DayResolution.Selected(
            observations = winner.value,
            sourceId = winner.key,
            family = registry.familyOf(winner.key),
            reason = DedupReason.MOST_DETAILED,
        )
    }
}

/** Blood pressure, heart rate, glucose: every reading is kept and averaged for display. */
class KeepAllPolicy : DedupPolicy {
    override fun resolve(
        candidates: List<Observation>,
        registry: SourceRegistry,
        day: LocalDay,
    ): DayResolution {
        if (candidates.isEmpty()) return DayResolution.Empty
        val first = candidates.first()
        return DayResolution.Selected(
            observations = candidates,
            sourceId = first.provenance.sourceId,
            family = registry.familyOf(first.provenance.sourceId),
            reason = DedupReason.NO_DEDUP,
        )
    }
}

/** Nutrition totals add up: they are different foods, not two reports of one number. */
class SumAllPolicy : DedupPolicy {
    override fun resolve(
        candidates: List<Observation>,
        registry: SourceRegistry,
        day: LocalDay,
    ): DayResolution {
        if (candidates.isEmpty()) return DayResolution.Empty
        val first = candidates.first()
        return DayResolution.Selected(
            observations = candidates,
            sourceId = first.provenance.sourceId,
            family = registry.familyOf(first.provenance.sourceId),
            reason = DedupReason.NO_DEDUP,
        )
    }
}
