package app.openhealth.ui.screens.clinical

/**
 * The weekly review (`pr.md` §6.10). It is a description of the last seven days against
 * the person's own data and cited references, not a score and not a verdict.
 */
data class WeeklyReviewItemModel(
    val title: String,
    val value: String,
    val note: String,
    val citation: String? = null,
)

data class WeeklyReviewModel(
    val title: String,
    val weekLabel: String,
    val items: List<WeeklyReviewItemModel>,
    val accessible: String,
    val emptyTitle: String,
    val emptyBody: String,
)
