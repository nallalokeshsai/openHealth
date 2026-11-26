package app.openhealth.ui.screens.clinical

/**
 * The sleep page (`pr.md` §6.10; `BUILD-ROADMAP.md` §B12). It shows the last nights,
 * the average, the debt against the person's own need, how regular the window was, and
 * the stage mix from the most detailed source. A missing value is a dash, never a zero.
 */
data class SleepNightRow(
    val date: String,
    val asleep: String,
    val bedtime: String,
    val wake: String,
    val quality: String,
)

data class SleepStageRow(val label: String, val duration: String, val share: String)

data class SleepModel(
    val title: String,
    val averageLabel: String,
    val averageValue: String,
    val debtLabel: String,
    val debtValue: String,
    val debtNote: String,
    val consistencyBedLabel: String,
    val consistencyBedValue: String,
    val consistencyWakeLabel: String,
    val consistencyWakeValue: String,
    val consistencyNote: String,
    val nights: List<SleepNightRow>,
    val stages: List<SleepStageRow>,
    val stagesNote: String,
    val accessible: String,
    val emptyTitle: String,
    val emptyBody: String,
    val qualityLabel: String,
    val addQualityLabel: String,
    val reminder: String,
) {
    val empty: Boolean get() = nights.isEmpty()
}
