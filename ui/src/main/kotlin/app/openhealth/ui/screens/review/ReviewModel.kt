package app.openhealth.ui.screens.review

import app.openhealth.core.review.ReviewKind

/** What a review row offers (`pr.md` §6.28: accept, merge, edit or ignore). */
enum class ReviewAction {
    /** Keep the value the day already shows; the question is answered and closed. */
    KEEP,

    /** Leave the reading out of trends without deleting it (`pr.md` §6.28). */
    EXCLUDE,

    /** Collapse a probable duplicate: the extra reading goes to Trash for 30 days. */
    MERGE,

    /** Open the timeline, where the row can be corrected through the same provenance path. */
    EDIT,

    /** Dismiss the question without changing anything. */
    IGNORE,
}

/** The copy and the target of one action in the item's sheet. */
data class ReviewActionModel(
    val action: ReviewAction,
    val label: String,
    val observationId: String? = null,
    /** Every reading a merge moves to Trash; empty for every other action. */
    val mergeIds: List<String> = emptyList(),
)

/** One line of the "what each source said" block. */
data class ReviewReadingModel(
    val sourceLabel: String,
    val valueText: String,
    val timeText: String,
    /** How and when it was recorded, so every number keeps its provenance (§7.2). */
    val detail: String?,
    val winner: Boolean,
)

/** One row of the review inbox. */
data class ReviewRowModel(
    val key: String,
    val kind: ReviewKind,
    val title: String,
    val subtitle: String,
    val dateLabel: String,
    val readings: List<ReviewReadingModel>,
    val explanation: String?,
    val actions: List<ReviewActionModel>,
)

/** The whole surface: rows, or an empty state that says there is nothing to review. */
data class ReviewModel(
    val rows: List<ReviewRowModel>,
    val emptyTitle: String,
    val emptyBody: String,
    val countLabel: String,
)
