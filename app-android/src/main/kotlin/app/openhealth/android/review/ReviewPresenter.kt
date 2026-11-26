@file:Suppress("TooManyFunctions")

package app.openhealth.android.review

import android.content.Context
import app.openhealth.android.preferences.Preferences
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.preferences.HealthModule
import app.openhealth.core.preferences.SensitiveMetric
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Builds the review inbox from the last 30 days of readings (`pr.md` §6.28,
 * `BUILD-ROADMAP.md` §B10). It is read-only: the screen's actions go through the same
 * repositories as the timeline, so an exclusion or a merge writes one tombstone and
 * recomputes that day's rollups.
 *
 * The inbox obeys the same gates as every other surface: a module that is switched off
 * hides its questions, and weight-neutral mode hides weight, body fat and energy rows
 * entirely rather than showing a number (`pr.md` §6.21, §6.25).
 */
class ReviewPresenter(
    private val context: Context,
    private val store: LocalStore,
    private val clock: ClockSeam,
    private val zone: TimeZone,
) {
    private val number: NumberFormat = NumberFormat.getNumberInstance()
    private val json = Json { ignoreUnknownKeys = true }

    fun model(preferences: Preferences): ReviewModel {
        val today = clock.now().toLocalDateTime(zone).date
        val from = today.minus(DatePeriod(days = WINDOW_DAYS))
        val labelModule = preferences.modules
        val policy = preferences.displayPolicy
        val observations = store.observations.daysWithObservations()
            .filter { it >= from && it <= today }
            .flatMap { store.observations.forDay(it) }
            // A module that is off has no surfaces, and that includes its questions.
            .filter { type(it.type) in labelModule }
            // A hidden metric is not shown anywhere, not even as a review question.
            .filter { type ->
                sensitive(type.type)?.let(policy::shows) ?: true
            }
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
            countLabel = countLabel(items.size),
        )
    }

    /** The Settings badge: how many questions are waiting, without building the screen. */
    fun count(preferences: Preferences): Int = model(preferences).rows.size

    /** Answers one item: the caller dismisses it once the action has been applied. */
    fun dismiss(key: String) {
        val keys = dismissedKeys().toMutableSet()
        keys += key
        store.settings.put(
            SettingKeys.REVIEW_DISMISSED,
            json.encodeToString(
                ListSerializer(String.serializer()),
                keys.toList().takeLast(MAX_DISMISSED),
            ),
        )
    }

    private fun dismissedKeys(): Set<String> {
        val raw = store.settings.get(SettingKeys.REVIEW_DISMISSED) ?: return emptySet()
        return runCatching {
            json.decodeFromString(ListSerializer(String.serializer()), raw).toSet()
        }.getOrDefault(emptySet())
    }

    private fun countLabel(count: Int): String = if (count == 0) {
        context.getString(UiR.string.review_empty_title)
    } else {
        context.resources.getQuantityString(UiR.plurals.review_count, count, count)
    }

    private fun row(item: ReviewItem): ReviewRowModel = ReviewRowModel(
        key = item.key,
        kind = item.kind,
        title = title(item),
        subtitle = subtitle(item),
        dateLabel = java.time.LocalDate.parse(item.localDate.toString()).format(
            java.time.format.DateTimeFormatter.ofLocalizedDate(
                java.time.format.FormatStyle.MEDIUM,
            ),
        ),
        readings = item.readings.map { reading ->
            ReviewReadingModel(
                sourceLabel = reading.sourceLabel,
                valueText = value(item.type, reading.value),
                timeText = time(reading),
                detail = detail(reading),
                // The reading the day uses wins; when the rollup's winner is not among
                // the readings, nothing is marked rather than the largest one guessed at.
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
            item.readings.firstOrNull()?.sourceLabel.orEmpty(),
            label(item.type),
        )
    }

    private fun subtitle(item: ReviewItem): String = when (item.kind) {
        ReviewKind.DISAGREEMENT -> disagreementBody(item)
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

    private fun disagreementBody(item: ReviewItem): String {
        val winner = item.readings.firstOrNull { it.sourceId == item.winnerSourceId }
        val other = item.readings.firstOrNull { it != winner } ?: item.readings.first()
        return if (winner == null) {
            context.getString(
                UiR.string.review_disagreement_body_no_winner,
                other.sourceLabel,
                value(item.type, other.value),
                item.readings.first().sourceLabel,
                value(item.type, item.readings.first().value),
            )
        } else {
            context.getString(
                UiR.string.review_disagreement_body,
                winner.sourceLabel,
                value(item.type, winner.value),
                other.sourceLabel,
                value(item.type, other.value),
                winner.sourceLabel,
            )
        }
    }

    private fun actions(item: ReviewItem): List<ReviewActionModel> = buildList {
        when (item.kind) {
            ReviewKind.DISAGREEMENT -> {
                val winner = item.readings.firstOrNull { it.sourceId == item.winnerSourceId }
                if (winner != null) {
                    add(
                        ReviewActionModel(
                            action = ReviewAction.KEEP,
                            label = context.getString(
                                UiR.string.review_keep,
                                value(item.type, winner.value),
                            ),
                        ),
                    )
                }
                item.readings.filterNot { it.sourceId == item.winnerSourceId }.forEach {
                    add(
                        ReviewActionModel(
                            action = ReviewAction.EXCLUDE,
                            // The value is in the label so two identical buttons can
                            // never mean two different readings.
                            label = context.getString(
                                UiR.string.review_exclude,
                                value(item.type, it.value),
                            ),
                            observationId = it.observationId,
                        ),
                    )
                }
            }

            ReviewKind.OUTSIDE_RANGE -> item.readings.forEach { reading ->
                add(
                    ReviewActionModel(
                        action = ReviewAction.EXCLUDE,
                        label = context.getString(
                            UiR.string.review_exclude,
                            value(item.type, reading.value),
                        ),
                        observationId = reading.observationId,
                    ),
                )
            }

            ReviewKind.DUPLICATE -> add(
                ReviewActionModel(
                    action = ReviewAction.MERGE,
                    label = context.resources.getQuantityString(
                        UiR.plurals.review_merge,
                        item.mergeIds.size,
                        item.mergeIds.size,
                    ),
                    observationId = item.mergeIds.firstOrNull(),
                    mergeIds = item.mergeIds,
                ),
            )
        }
        add(ReviewActionModel(ReviewAction.EDIT, context.getString(UiR.string.review_edit)))
        add(ReviewActionModel(ReviewAction.IGNORE, context.getString(UiR.string.review_ignore)))
    }

    private fun minutesApart(readings: List<ReviewReading>): String {
        val millis = readings.maxOf { it.happenedAt.toEpochMilliseconds() } -
            readings.minOf { it.happenedAt.toEpochMilliseconds() }
        val minutes = (millis / MILLIS_PER_MINUTE).coerceAtLeast(0).toInt()
        return if (minutes <= 1) {
            context.getString(UiR.string.review_under_a_minute)
        } else {
            context.resources.getQuantityString(UiR.plurals.review_minutes_apart, minutes, minutes)
        }
    }

    private fun time(reading: ReviewReading): String {
        val local = reading.happenedAt.toLocalDateTime(zone)
        return "%02d:%02d".format(local.hour, local.minute)
    }

    /** The provenance line under a reading: how it was recorded and by what. */
    private fun detail(reading: ReviewReading): String? {
        val parts = listOfNotNull(
            reading.method?.lowercase()?.replace('_', ' '),
            reading.device,
            time(reading.recordedAt),
        )
        return if (parts.isEmpty()) null else parts.joinToString(" · ")
    }

    private fun time(instant: kotlin.time.Instant): String {
        val local = instant.toLocalDateTime(zone)
        return "%02d:%02d".format(local.hour, local.minute)
    }

    private fun value(type: ObservationType, raw: Double): String {
        val shown = if (type in MASS_TYPES) mass(raw) else raw
        val scaled = if (shown == shown.toLong().toDouble()) {
            number.format(shown.toLong())
        } else {
            number.format(Math.round(shown * SINGLE_DECIMAL) / SINGLE_DECIMAL)
        }
        val unit = if (type in MASS_TYPES && imperial()) {
            context.getString(UiR.string.unit_pounds)
        } else {
            type.canonicalUnit.ucum
        }
        return "$scaled $unit"
    }

    private fun mass(kilograms: Double): Double =
        if (imperial()) kilograms * POUNDS_PER_KILOGRAM else kilograms

    private fun imperial(): Boolean = store.profile.active()?.units == "imperial"

    private fun label(type: ObservationType): String = context.getString(
        when (type) {
            ObservationType.WEIGHT -> UiR.string.review_type_weight
            ObservationType.BODY_FAT_PERCENT -> UiR.string.stats_card_body_fat_title
            ObservationType.STEPS -> UiR.string.stats_kpi_steps
            ObservationType.HEART_RATE -> UiR.string.review_type_heart_rate
            ObservationType.SLEEP_DURATION -> UiR.string.stats_kpi_sleep
            ObservationType.NUTRITION_ENERGY -> UiR.string.stats_kpi_energy
            else -> UiR.string.review_type_other
        },
    )

    /** The module a type belongs to (`pr.md` §6.21). */
    private fun type(type: ObservationType): HealthModule = when (type.category) {
        app.openhealth.core.observation.ObservationCategory.ACTIVITY -> HealthModule.STEPS
        app.openhealth.core.observation.ObservationCategory.VITALS -> HealthModule.VITALS
        app.openhealth.core.observation.ObservationCategory.BODY -> HealthModule.BODY
        app.openhealth.core.observation.ObservationCategory.NUTRITION -> HealthModule.NUTRITION
        app.openhealth.core.observation.ObservationCategory.SLEEP -> HealthModule.SLEEP
        app.openhealth.core.observation.ObservationCategory.TRAINING -> HealthModule.TRAINING
        app.openhealth.core.observation.ObservationCategory.WELLBEING -> HealthModule.MENTAL_HEALTH
    }

    /** Which sensitive switch hides a type, or null when the type is not sensitive. */
    private fun sensitive(type: ObservationType): SensitiveMetric? = when (type) {
        ObservationType.WEIGHT, ObservationType.LEAN_MASS -> SensitiveMetric.WEIGHT
        ObservationType.BODY_FAT_PERCENT -> SensitiveMetric.BODY_FAT
        ObservationType.NUTRITION_ENERGY -> SensitiveMetric.CALORIES
        else -> null
    }

    private companion object {
        const val WINDOW_DAYS = 30
        const val MAX_DISMISSED = 500
        const val MILLIS_PER_MINUTE = 60_000L
        const val SINGLE_DECIMAL = 10.0
        const val POUNDS_PER_KILOGRAM = 2.2046226218
        val MASS_TYPES = setOf(ObservationType.WEIGHT, ObservationType.LEAN_MASS)
    }
}

/** Parses the local date a row belongs to, for sorting or tests. */
fun ReviewItem.day(): LocalDate = localDate
