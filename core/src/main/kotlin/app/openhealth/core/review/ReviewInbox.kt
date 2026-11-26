package app.openhealth.core.review

import app.openhealth.core.observation.Observation
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.observation.TemporalShape
import app.openhealth.core.validate.PlausibilityRanges
import app.openhealth.core.validate.PlausibleRange
import kotlin.math.abs
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/** Why a day needs the person's attention (`pr.md` §6.28). */
enum class ReviewKind {
    /** Two sources supplied a value for the same day and they do not agree. */
    DISAGREEMENT,

    /** A reading sits outside the plausibility screen, which is not a diagnosis. */
    OUTSIDE_RANGE,

    /** One source wrote the same reading twice within [ReviewInbox.DUPLICATE_SECONDS]. */
    DUPLICATE,
}

/** One observation as the inbox sees it, with the label a screen shows. */
data class ReviewReading(
    val observationId: String,
    val sourceId: String,
    val sourceLabel: String,
    val value: Double,
    val happenedAt: Instant,
    val recordedAt: Instant,
    val method: String? = null,
    val device: String? = null,
)

/**
 * One row of the review inbox. It carries the readings it is about so the sheet can
 * show what each source said, and a [key] that is stable across rebuilds so "ignore"
 * survives a restart.
 */
data class ReviewItem(
    val key: String,
    val kind: ReviewKind,
    val type: ObservationType,
    val localDate: LocalDate,
    val readings: List<ReviewReading>,
    /** The source the day's rollup used, when there is one (disagreements). */
    val winnerSourceId: String? = null,
    val range: PlausibleRange? = null,
    /** For a duplicate: the readings that would move to Trash; the first is kept. */
    val mergeIds: List<String> = emptyList(),
) {
    init {
        require(readings.isNotEmpty()) { "a review item is about at least one reading" }
    }
}

/**
 * Builds the inbox from a window of observations (`pr.md` §6.28, `BUILD-ROADMAP.md` §B10).
 *
 * It never edits anything: the dedup policy still decides the day's value, and every
 * item is a question with an answer the person chooses. A reading the person has already
 * excluded from trends is not asked about again, and a day with one source is never a
 * disagreement.
 */
object ReviewInbox {
    /** Two sources disagree when their values differ by more than this fraction. */
    const val DISAGREEMENT_FRACTION = 0.05

    /** Two readings from one source this close in time and value are a probable duplicate. */
    const val DUPLICATE_SECONDS = 90L

    /** A duplicate's values must also match this closely. */
    const val DUPLICATE_FRACTION = 0.01

    private const val MILLIS_PER_SECOND = 1_000L

    /** How many readings one duplicate item draws; a longer run is still merged whole. */
    const val MAX_DUPLICATE_ROWS = 5

    fun build(
        observations: List<Observation>,
        sourceLabels: Map<String, String>,
        winnerBy: (ObservationType, LocalDate) -> String? = { _, _ -> null },
    ): List<ReviewItem> {
        val live = observations.filterNot { it.excludedFromTrends }
        val items = mutableListOf<ReviewItem>()
        live.groupBy { it.type to it.localDate }.forEach { (key, group) ->
            val (type, date) = key
            val readings = group.map { it.toReading(sourceLabels) }
            outOfRange(type, date, group, readings)?.let(items::add)
            duplicates(type, date, readings)?.let(items::add)
            disagreement(type, date, readings, winnerBy(type, date))?.let(items::add)
        }
        return items.sortedWith(compareBy({ it.localDate }, { it.type.name }, { it.kind.name }))
            .reversed()
    }

    private fun outOfRange(
        type: ObservationType,
        date: LocalDate,
        group: List<Observation>,
        readings: List<ReviewReading>,
    ): ReviewItem? {
        val range = runCatching { PlausibilityRanges.of(type) }.getOrNull() ?: return null
        val offending = group.indices.filterNot { range.contains(group[it].value.value) }
        return if (offending.isEmpty()) {
            null
        } else {
            ReviewItem(
                key = "range:${type.name}:$date:" +
                    offending.joinToString(",") { group[it].id.value },
                kind = ReviewKind.OUTSIDE_RANGE,
                type = type,
                localDate = date,
                readings = offending.map(readings::get),
                range = range,
            )
        }
    }

    private fun duplicates(
        type: ObservationType,
        date: LocalDate,
        readings: List<ReviewReading>,
    ): ReviewItem? {
        // A stream of samples is not a duplicate: a heart-rate strap records the same
        // value every second, and offering each of those for deletion would be wrong
        // (and could produce a hundred-row item from one FIT import). A type that is a
        // value, a total or an interval for a day can be written twice by mistake.
        if (type.shape == TemporalShape.INSTANT) return null
        val runs = duplicateRuns(readings)
        val extra = runs.flatMap { it.extras }
        val kept = runs.firstOrNull()?.kept
        return if (kept == null) {
            null
        } else {
            ReviewItem(
                key = "duplicate:${type.name}:$date:" +
                    extra.joinToString(",") { it.observationId },
                kind = ReviewKind.DUPLICATE,
                type = type,
                localDate = date,
                // The kept reading comes first so the sheet can show the pair and its
                // gap; every extra id is merged even when the sheet draws fewer rows. It
                // is the reading the extras repeat, never another source's reading that
                // merely sorts earlier in the day.
                readings = listOf(kept) + extra.take(MAX_DUPLICATE_ROWS - 1),
                mergeIds = extra.map { it.observationId },
            )
        }
    }

    private fun disagreement(
        type: ObservationType,
        date: LocalDate,
        readings: List<ReviewReading>,
        winnerSourceId: String?,
    ): ReviewItem? {
        // One reading per source is the comparison: a source's second reading is a
        // duplicate question, not a second opinion.
        val bySource = readings
            .groupBy { it.sourceId }
            .mapValues { (_, rows) -> rows.maxBy { it.happenedAt } }
        if (bySource.size < 2) return null
        val values = bySource.values.map { it.value }
        val spread = values.max() - values.min()
        val largest = values.maxOf { abs(it) }
        return if (largest == 0.0 || spread / largest <= DISAGREEMENT_FRACTION) {
            null
        } else {
            val ordered = bySource.values.sortedByDescending { it.value }
            ReviewItem(
                key = "disagree:${type.name}:$date:" +
                    ordered.joinToString(",") { it.observationId },
                kind = ReviewKind.DISAGREEMENT,
                type = type,
                localDate = date,
                readings = ordered,
                winnerSourceId = winnerSourceId,
            )
        }
    }

    /** One source's reading and the later readings that repeat it. */
    private data class DuplicateRun(val kept: ReviewReading, val extras: List<ReviewReading>)

    /**
     * The runs of repeated readings. Readings are ordered by source and time, so the
     * first of a run is the one kept and every later match is an extra. A run belongs to
     * one source: the reading a question keeps is the reading its extras repeat, so a
     * second source on the same day can never be named as the duplicate's survivor.
     */
    private fun duplicateRuns(readings: List<ReviewReading>): List<DuplicateRun> {
        val ordered = readings.sortedWith(compareBy({ it.sourceId }, { it.happenedAt }))
        val runs = mutableListOf<DuplicateRun>()
        val extras = mutableListOf<ReviewReading>()
        var keep: ReviewReading? = null
        ordered.forEach { reading ->
            val previous = keep
            if (previous != null && isDuplicate(previous, reading)) {
                extras += reading
            } else {
                if (previous != null && extras.isNotEmpty()) {
                    runs += DuplicateRun(previous, extras.toList())
                }
                extras.clear()
                keep = reading
            }
        }
        val last = keep
        if (last != null && extras.isNotEmpty()) {
            runs += DuplicateRun(last, extras.toList())
        }
        return runs
    }

    private fun isDuplicate(previous: ReviewReading, reading: ReviewReading): Boolean {
        if (previous.sourceId != reading.sourceId) return false
        val seconds = abs(
            reading.happenedAt.toEpochMilliseconds() -
                previous.happenedAt.toEpochMilliseconds(),
        ) / MILLIS_PER_SECOND
        return seconds <= DUPLICATE_SECONDS &&
            close(previous.value, reading.value, DUPLICATE_FRACTION)
    }

    private fun close(a: Double, b: Double, fraction: Double): Boolean {
        val largest = maxOf(abs(a), abs(b))
        return largest == 0.0 || abs(a - b) / largest <= fraction
    }

    private fun Observation.toReading(labels: Map<String, String>): ReviewReading = ReviewReading(
        observationId = id.value,
        sourceId = provenance.sourceId.value,
        sourceLabel = labels[provenance.sourceId.value] ?: provenance.sourceId.value,
        value = value.value,
        happenedAt = provenance.happenedAt,
        recordedAt = provenance.recordedAt,
        method = provenance.method?.name,
        device = provenance.device,
    )
}
