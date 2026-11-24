@file:Suppress("MagicNumber")

package app.openhealth.core.nutrition

import app.openhealth.core.citation.CitationKey
import app.openhealth.core.units.UcumUnit

/** Where a nutrient sits in the panel; macro rows come first (`pr.md` §6.3). */
enum class NutrientGroup { MACRO, MINERAL, VITAMIN, OTHER }

/**
 * The nutrient set the bundled index, Open Food Facts and the custom-food editor share.
 *
 * A value is stored **per 100 g** in [unit]'s own scale: grams for macros, milligrams for
 * most minerals, micrograms for vitamins A/D/K, folate, B12 and selenium. Nothing is
 * rounded on the way in or on the way out (`pr.md` §6.3: a day's totals are the sum of its
 * entries exactly), so a display formatter is the only place a decimal is dropped.
 *
 * [dailyValue] is the US Food and Drug Administration Daily Value for adults and
 * children four years and older on a 2,000 kcal reference diet. It is a published
 * reference, not a personal target: the panel shows it as a share and cites it
 * (`FDA_DAILY_VALUES_2020`, `pr.md` §12.2).
 */
enum class Nutrient(
    val key: String,
    val unit: UcumUnit,
    val dailyValue: Double?,
    val group: NutrientGroup,
) {
    ENERGY("energy", UcumUnit.KILOCALORIE, null, NutrientGroup.MACRO),
    PROTEIN("protein", UcumUnit.GRAM, 50.0, NutrientGroup.MACRO),
    CARBS("carbs", UcumUnit.GRAM, 275.0, NutrientGroup.MACRO),
    FAT("fat", UcumUnit.GRAM, 78.0, NutrientGroup.MACRO),
    FIBRE("fibre", UcumUnit.GRAM, 28.0, NutrientGroup.MACRO),
    SUGARS("sugars", UcumUnit.GRAM, null, NutrientGroup.MACRO),
    SATURATED_FAT("saturated_fat", UcumUnit.GRAM, 20.0, NutrientGroup.MACRO),
    MONOUNSATURATED_FAT("monounsaturated_fat", UcumUnit.GRAM, null, NutrientGroup.MACRO),
    POLYUNSATURATED_FAT("polyunsaturated_fat", UcumUnit.GRAM, null, NutrientGroup.MACRO),
    TRANS_FAT("trans_fat", UcumUnit.GRAM, null, NutrientGroup.MACRO),

    CHOLESTEROL("cholesterol", UcumUnit.MILLIGRAM, 300.0, NutrientGroup.OTHER),
    SODIUM("sodium", UcumUnit.MILLIGRAM, 2_300.0, NutrientGroup.OTHER),
    POTASSIUM("potassium", UcumUnit.MILLIGRAM, 4_700.0, NutrientGroup.MINERAL),
    CALCIUM("calcium", UcumUnit.MILLIGRAM, 1_300.0, NutrientGroup.MINERAL),
    IRON("iron", UcumUnit.MILLIGRAM, 18.0, NutrientGroup.MINERAL),
    MAGNESIUM("magnesium", UcumUnit.MILLIGRAM, 420.0, NutrientGroup.MINERAL),
    ZINC("zinc", UcumUnit.MILLIGRAM, 11.0, NutrientGroup.MINERAL),
    PHOSPHORUS("phosphorus", UcumUnit.MILLIGRAM, 1_250.0, NutrientGroup.MINERAL),
    SELENIUM("selenium", UcumUnit.MICROGRAM, 55.0, NutrientGroup.MINERAL),
    COPPER("copper", UcumUnit.MILLIGRAM, 0.9, NutrientGroup.MINERAL),
    MANGANESE("manganese", UcumUnit.MILLIGRAM, 2.3, NutrientGroup.MINERAL),

    VITAMIN_A("vitamin_a", UcumUnit.MICROGRAM, 900.0, NutrientGroup.VITAMIN),
    VITAMIN_C("vitamin_c", UcumUnit.MILLIGRAM, 90.0, NutrientGroup.VITAMIN),
    VITAMIN_D("vitamin_d", UcumUnit.MICROGRAM, 20.0, NutrientGroup.VITAMIN),
    VITAMIN_E("vitamin_e", UcumUnit.MILLIGRAM, 15.0, NutrientGroup.VITAMIN),
    VITAMIN_K("vitamin_k", UcumUnit.MICROGRAM, 120.0, NutrientGroup.VITAMIN),
    THIAMIN("thiamin", UcumUnit.MILLIGRAM, 1.2, NutrientGroup.VITAMIN),
    RIBOFLAVIN("riboflavin", UcumUnit.MILLIGRAM, 1.3, NutrientGroup.VITAMIN),
    NIACIN("niacin", UcumUnit.MILLIGRAM, 16.0, NutrientGroup.VITAMIN),
    VITAMIN_B6("vitamin_b6", UcumUnit.MILLIGRAM, 1.7, NutrientGroup.VITAMIN),
    FOLATE("folate", UcumUnit.MICROGRAM, 400.0, NutrientGroup.VITAMIN),
    VITAMIN_B12("vitamin_b12", UcumUnit.MICROGRAM, 2.4, NutrientGroup.VITAMIN),

    CAFFEINE("caffeine", UcumUnit.MILLIGRAM, null, NutrientGroup.OTHER),
    WATER("water", UcumUnit.GRAM, null, NutrientGroup.OTHER),
    ;

    /** The five values the energy card's macro bars read, in bar order. */
    val isMacroBar: Boolean
        get() = this == PROTEIN || this == CARBS || this == FAT || this == FIBRE

    companion object {
        private val byKey = entries.associateBy(Nutrient::key)

        fun fromKey(key: String): Nutrient? = byKey[key]

        /** The order the micronutrient panel lists: minerals, then vitamins, then other. */
        val microPanel: List<Nutrient> = entries.filter {
            it.group != NutrientGroup.MACRO && it != CAFFEINE && it != WATER
        }
    }
}

/** The published daily value a panel row is shown against, with its citation. */
data class DailyReference(
    val nutrient: Nutrient,
    val amount: Double = requireNotNull(nutrient.dailyValue),
    val citation: CitationKey = CitationKey.FDA_DAILY_VALUES_2020,
) {
    /** Amount as a share of the daily value, e.g. 0.45 for 45 %. Never clamped. */
    fun share(amount: Double): Double = if (this.amount <= 0.0) 0.0 else amount / this.amount
}

object ReferenceIntakes {
    private val references: Map<Nutrient, DailyReference> = Nutrient.entries
        .filter { it.dailyValue != null }
        .associateWith { DailyReference(it) }

    fun of(nutrient: Nutrient): DailyReference? = references[nutrient]

    fun all(): List<DailyReference> = Nutrient.microPanel.mapNotNull(references::get)
}
