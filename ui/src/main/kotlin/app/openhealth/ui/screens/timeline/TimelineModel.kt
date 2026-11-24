package app.openhealth.ui.screens.timeline

import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/** What a timeline row came from; the screen maps it to a label and a glyph. */
enum class TimelineKind {
    NOTE,
    WATER,
    WEIGHT,
    MEASUREMENT,
    VITAL,
    MEAL,
    NUTRITION,
    ENTRY,
    SESSION,
    OBSERVATION,
    OTHER,
}

/** The provenance sheet a row opens (`pr.md` §6.27, `DESIGN.md` §7.2). */
data class RowProvenance(
    val sourceLabel: String,
    val happenedAt: Instant,
    val recordedAt: Instant,
    val method: String?,
    val device: String?,
    val edited: Boolean,
    val localDay: String = "",
    val zoneId: String = "",
    val quality: String? = null,
    val rawRecord: Boolean = false,
)

data class TimelineRowModel(
    val kind: TimelineKind,
    val entityId: String,
    val title: String,
    val detail: String,
    val valueText: String?,
    val provenance: RowProvenance,
    val deletable: Boolean,
    val sensitive: Boolean = false,
    val excludable: Boolean = false,
    val imported: Boolean = false,
    val sourceId: String = "",
    val excluded: Boolean = false,
)

data class TimelineDayModel(val date: LocalDate, val rows: List<TimelineRowModel>)

enum class TimelineFilter { ALL, NOTES, BODY, NUTRITION, MANUAL, IMPORTED }

data class TimelineModel(
    val days: List<TimelineDayModel>,
    val filter: TimelineFilter,
    val total: Int,
    val hasMore: Boolean = false,
)
