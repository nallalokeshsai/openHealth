@file:Suppress("TooManyFunctions")

package app.openhealth.android.review

import android.content.Context
import app.openhealth.core.review.ReviewInbox
import app.openhealth.core.review.ReviewItem
import app.openhealth.core.review.ReviewKind
import app.openhealth.core.review.ReviewReading
import app.openhealth.core.seam.ClockSeam
import app.openhealth.data.LocalStore
import app.openhealth.data.repo.SettingKeys
import app.openhealth.ui.R as UiR
import app.openhealth.ui.screens.review.ReviewAction
import app.openhealth.ui.screens.review.ReviewActionModel
import app.openhealth.ui.screens.review.ReviewModel
import app.openhealth.ui.screens.review.ReviewReadingModel
import app.openhealth.ui.screens.review.ReviewRowModel
import java.text.NumberFormat
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Builds the review inbox from the last 30 days of readings (`pr.md` §6.28,
 * `BUILD-ROADMAP.md` §B10). It is read-only: the screen's actions go through the same
 * repositories as the timeline, so an exclusion or a merge writes one tombstone and
 * recomputes that day's rollups.
 */
class ReviewPresenter(
    private val context: Context,
    private val store: LocalStore,
    private val clock: ClockSeam,
    private val zone: TimeZone,
) {
    private val number: NumberFormat = NumberFormat.getNumberInstance()
    private val json = Json { ignoreUnknownKeys = true }

    fun model(): ReviewModel {
        val today = clock.now().toLocalDateTime(zone).date
        val from = today.minus(DatePeriod(days = WINDOW_DAYS))
        val observations = store.observations.daysWithObservations()
            .filter { it >= from && it <= today }
            .flatMap { store.observations.forDay(it) }
        val labels = store.sources.all().associate { it.id.value to it.label }
        val dismissed = dismissedKeys()
        val items = ReviewInbox.build(
            observations = observations,
            sourceLabels = labels,
            winnerBy = { type, day -> store.rollups.forDayAndType(day, type)?.sourceId?.value },
        ).filterNot { it.key in dismissed }
        return ReviewModel(
            rows = items.map(::row),
            emptyTitle = context.getString(UiR.string.review_empty_title),
            emptyBody = context.getString(UiR.string.review_empty_body),
            countLabel = if (items.size == 1) {
                context.getString(UiR.string.review_one)
            } else {
                context.getString(UiR.string.review_subtitle, items.size)
            },
        )
    }

    /** Answers one item: the caller dismisses it once the action has been applied. */
    fun dismiss(key: String) {
        val keys = dismissedKeys().toMutableSet()
        keys += key
        store.settings.put(
            SettingKeys.REVIEW_DISMISSED,
            json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(String.serializer()),
                keys.toList().takeLast(MAX_DISMISSED),
            ),
        )
    }

    private fun dismissedKeys(): Set<String> {
        val raw = store.settings.get(SettingKeys.REVIEW_DISMISSED) ?: return emptySet()
        return runCatching {
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(String.serializer()),
                raw,
            ).toSet()
        }.getOrDefault(emptySet())
    }

    private fun row(item: ReviewItem): ReviewRowModel = ReviewRowModel(
        key = item.key,
        kind = item.kind,
        title = title(item),
        subtitle = subtitle(item),
        dateLabel = item.localDate.toString(),
        readings = item.readings.map { reading ->
            ReviewReadingModel(
                sourceLabel = reading.sourceLabel,
                valueText = value(item.type, reading.value),
                timeText = time(reading),
                winner = reading.sourceId == item.winnerSourceId,
            )
        },
        explanation = when (item.kind) {
            ReviewKind.OUTSIDE_RANGE -> context.getString(UiR.string.review_explain_range)
            ReviewKind.DUPLICATE -> context.getString(UiR.string.review_explain_duplicate)
            else -> null
        },
        actions = actions(item),
    )

    private fun title(item: ReviewItem): String = when (item.kind) {
        ReviewKind.DISAGREEMENT -> context.getString(
            UiR.string.review_disagreement_title,
            label(item.type),
        )

        ReviewKind.OUTSIDE_RANGE -> context.getString(
            UiR.string.review_range_title,
            label(item.type),
        )

        ReviewKind.DUPLICATE -> context.getString(
            UiR.string.review_duplicate_title,
            sourceOf(item),
            label(item.type),
        )
    }

    private fun subtitle(item: ReviewItem): String = when (item.kind) {
        ReviewKind.DISAGREEMENT -> {
            val winner = item.readings.firstOrNull { it.sourceId == item.winnerSourceId }
                ?: item.readings.first()
            context.getString(
                UiR.string.review_disagreement_body,
                winner.sourceLabel,
                value(item.type, winner.value),
                item.readings.firstOrNull { it != winner }?.sourceLabel.orEmpty(),
                item.readings.firstOrNull { it != winner }?.let {
                    value(item.type, it.value)
                }.orEmpty(),
                winner.sourceLabel,
            )
        }

        ReviewKind.OUTSIDE_RANGE -> {
            val reading = item.readings.first()
            context.getString(
                UiR.string.review_range_body,
                reading.sourceLabel,
                value(item.type, reading.value),
                label(item.type),
                value(item.type, item.range?.min ?: 0.0),
                value(item.type, item.range?.max ?: 0.0),
            )
        }

        ReviewKind.DUPLICATE -> context.getString(
            UiR.string.review_duplicate_body,
            minutesApart(item.readings),
            label(item.type),
        )
    }

    private fun actions(item: ReviewItem): List<ReviewActionModel> = buildList {
        when (item.kind) {
            ReviewKind.DISAGREEMENT -> {
                val winner = item.readings.firstOrNull { it.sourceId == item.winnerSourceId }
                    ?: return@buildList
                add(
                    ReviewActionModel(
                        action = ReviewAction.KEEP,
                        label = context.getString(UiR.string.review_keep, winner.sourceLabel),
                    ),
                )
                item.readings.filterNot { it == winner }.forEach { reading ->
                    add(
                        ReviewActionModel(
                            action = ReviewAction.EXCLUDE,
                            label = context.getString(
                                UiR.string.review_exclude,
                                reading.sourceLabel,
                            ),
                            observationId = reading.observationId,
                        ),
                    )
                }
            }

            ReviewKind.OUTSIDE_RANGE -> item.readings.forEach { reading ->
                add(
                    ReviewActionModel(
                        action = ReviewAction.EXCLUDE,
                        label = context.getString(UiR.string.review_leave_out),
                        observationId = reading.observationId,
                    ),
                )
            }

            ReviewKind.DUPLICATE -> add(
                ReviewActionModel(
                    action = ReviewAction.MERGE,
                    label = context.getString(UiR.string.review_merge),
                    observationId = item.readings.first().observationId,
                ),
            )
        }
        add(ReviewActionModel(ReviewAction.EDIT, context.getString(UiR.string.review_edit)))
        add(ReviewActionModel(ReviewAction.IGNORE, context.getString(UiR.string.review_ignore)))
    }

    private fun sourceOf(item: ReviewItem): String =
        item.readings.firstOrNull()?.sourceLabel.orEmpty()

    private fun minutesApart(readings: List<ReviewReading>): String {
        val millis = readings.maxOf { it.happenedAt.toEpochMilliseconds() } -
            readings.minOf { it.happenedAt.toEpochMilliseconds() }
        val minutes = (millis / MILLIS_PER_MINUTE).coerceAtLeast(0)
        return if (minutes <= 1) "under a minute" else "$minutes minutes"
    }

    private fun time(reading: ReviewReading): String {
        val local = reading.happenedAt.toLocalDateTime(zone)
        return "%02d:%02d".format(local.hour, local.minute)
    }

    private fun value(type: app.openhealth.core.observation.ObservationType, raw: Double): String {
        val scaled = if (raw == raw.toLong().toDouble()) {
            number.format(raw.toLong())
        } else {
            number.format(Math.round(raw * SINGLE_DECIMAL) / SINGLE_DECIMAL)
        }
        return "$scaled ${type.canonicalUnit.ucum}"
    }

    private fun label(type: app.openhealth.core.observation.ObservationType): String =
        type.name.lowercase().replace('_', ' ')

    private companion object {
        const val WINDOW_DAYS = 30
        const val MAX_DISMISSED = 500
        const val MILLIS_PER_MINUTE = 60_000L
        const val SINGLE_DECIMAL = 10.0
    }
}
