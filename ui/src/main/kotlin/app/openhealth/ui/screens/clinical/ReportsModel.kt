package app.openhealth.ui.screens.clinical

/** The four exports a report offers (`pr.md` §6.9, §6.17). */
enum class ReportFormat { PDF, CSV, FHIR, MHEALTH }

data class ReportRangeModel(val days: Int, val label: String)

/**
 * The reports page. The preview is the report's own plain text, rendered on light paper
 * even in a dark app, and every export is produced on the device (`DESIGN.md` §8.7).
 */
data class ReportsModel(
    val title: String,
    val ranges: List<ReportRangeModel>,
    val selectedRange: Int,
    val rangeNote: String,
    val generatedLabel: String,
    val previewTitle: String,
    val previewLines: List<String>,
    val previewTruncated: Boolean,
    val disclaimer: String,
    val sourceNote: String,
    val exports: List<ReportFormat>,
    val empty: Boolean,
    val emptyBody: String,
)
