package app.openhealth.ui.screens.stats

import androidx.annotation.StringRes
import app.openhealth.core.citation.CitationKey
import app.openhealth.ui.R

/**
 * Every chart the app draws, named once. A chart that is not here cannot be shown: the
 * registry test fails when an id has no title, no "how this is calculated" sheet or no
 * citation, and the model tests fail when a chart arrives without a table alternative
 * and a one-sentence description (`pr.md` §6.16, `DESIGN.md` §8.6).
 */
enum class ChartId {
    READINESS,
    WEIGHT_SPARKLINE,
    WEIGHT_TREND,
    ENERGY_RING,
    MACRO_BARS,
    ENERGY_TREND,
    STEPS_RING,
    STEPS_TREND,
    TRAINING_VOLUME,
    MUSCLE_SETS,
    TRAINING_CONSISTENCY,
    BODY_FAT_TREND,
    SLEEP_DURATION,
    SLEEP_STAGES,
    PROTEIN_TREND,
}

/** A chart's fixed text: what it is called, and the sheet that explains its arithmetic. */
data class ChartMeta(
    val id: ChartId,
    @StringRes val titleRes: Int,
    @StringRes val formulaTitleRes: Int,
    @StringRes val formulaBodyRes: Int,
    val citation: CitationKey,
)

object ChartRegistry {
    private val entries: List<ChartMeta> = listOf(
        ChartMeta(
            id = ChartId.READINESS,
            titleRes = R.string.today_readiness_title,
            formulaTitleRes = R.string.chart_readiness_formula_title,
            formulaBodyRes = R.string.chart_readiness_formula_body,
            citation = CitationKey.APP_RULE,
        ),
        ChartMeta(
            id = ChartId.WEIGHT_SPARKLINE,
            titleRes = R.string.today_weight_title,
            formulaTitleRes = R.string.chart_weight_formula_title,
            formulaBodyRes = R.string.chart_weight_formula_body,
            citation = CitationKey.APP_RULE,
        ),
        ChartMeta(
            id = ChartId.WEIGHT_TREND,
            titleRes = R.string.stats_card_weight_title,
            formulaTitleRes = R.string.chart_weight_formula_title,
            formulaBodyRes = R.string.chart_weight_formula_body,
            citation = CitationKey.APP_RULE,
        ),
        ChartMeta(
            id = ChartId.ENERGY_RING,
            titleRes = R.string.today_energy_title,
            formulaTitleRes = R.string.chart_energy_formula_title,
            formulaBodyRes = R.string.chart_energy_formula_body,
            citation = CitationKey.COMPENDIUM_OF_PHYSICAL_ACTIVITIES_2024,
        ),
        ChartMeta(
            id = ChartId.MACRO_BARS,
            titleRes = R.string.stats_card_macros_title,
            formulaTitleRes = R.string.chart_macros_formula_title,
            formulaBodyRes = R.string.chart_macros_formula_body,
            citation = CitationKey.IOM_MACRONUTRIENTS_2005,
        ),
        ChartMeta(
            id = ChartId.ENERGY_TREND,
            titleRes = R.string.stats_card_energy_title,
            formulaTitleRes = R.string.chart_energy_formula_title,
            formulaBodyRes = R.string.chart_energy_formula_body,
            citation = CitationKey.COMPENDIUM_OF_PHYSICAL_ACTIVITIES_2024,
        ),
        ChartMeta(
            id = ChartId.STEPS_RING,
            titleRes = R.string.today_steps_title,
            formulaTitleRes = R.string.chart_steps_formula_title,
            formulaBodyRes = R.string.chart_steps_formula_body,
            citation = CitationKey.APP_RULE,
        ),
        ChartMeta(
            id = ChartId.STEPS_TREND,
            titleRes = R.string.stats_card_steps_title,
            formulaTitleRes = R.string.chart_steps_formula_title,
            formulaBodyRes = R.string.chart_steps_formula_body,
            citation = CitationKey.APP_RULE,
        ),
        ChartMeta(
            id = ChartId.TRAINING_VOLUME,
            titleRes = R.string.stats_card_volume_title,
            formulaTitleRes = R.string.chart_volume_formula_title,
            formulaBodyRes = R.string.chart_volume_formula_body,
            citation = CitationKey.APP_RULE,
        ),
        ChartMeta(
            id = ChartId.MUSCLE_SETS,
            titleRes = R.string.stats_card_muscles_title,
            formulaTitleRes = R.string.chart_muscles_formula_title,
            formulaBodyRes = R.string.chart_muscles_formula_body,
            citation = CitationKey.APP_RULE,
        ),
        ChartMeta(
            id = ChartId.TRAINING_CONSISTENCY,
            titleRes = R.string.stats_card_consistency_title,
            formulaTitleRes = R.string.chart_consistency_formula_title,
            formulaBodyRes = R.string.chart_consistency_formula_body,
            citation = CitationKey.APP_RULE,
        ),
        ChartMeta(
            id = ChartId.BODY_FAT_TREND,
            titleRes = R.string.stats_card_body_fat_title,
            formulaTitleRes = R.string.chart_body_fat_formula_title,
            formulaBodyRes = R.string.chart_body_fat_formula_body,
            citation = CitationKey.APP_RULE,
        ),
        ChartMeta(
            id = ChartId.SLEEP_DURATION,
            titleRes = R.string.stats_card_sleep_title,
            formulaTitleRes = R.string.chart_sleep_formula_title,
            formulaBodyRes = R.string.chart_sleep_formula_body,
            citation = CitationKey.SLEEP_CONSENSUS_2015,
        ),
        ChartMeta(
            id = ChartId.SLEEP_STAGES,
            titleRes = R.string.stats_card_sleep_stages_title,
            formulaTitleRes = R.string.chart_sleep_stages_formula_title,
            formulaBodyRes = R.string.chart_sleep_stages_formula_body,
            citation = CitationKey.SLEEP_CONSENSUS_2015,
        ),
        ChartMeta(
            id = ChartId.PROTEIN_TREND,
            titleRes = R.string.stats_card_protein_title,
            formulaTitleRes = R.string.chart_protein_formula_title,
            formulaBodyRes = R.string.chart_protein_formula_body,
            citation = CitationKey.IOM_MACRONUTRIENTS_2005,
        ),
    )

    val all: List<ChartMeta> = entries

    fun of(id: ChartId): ChartMeta = entries.firstOrNull { it.id == id }
        ?: error("Chart $id has no registry entry (DESIGN.md §8.6).")
}
