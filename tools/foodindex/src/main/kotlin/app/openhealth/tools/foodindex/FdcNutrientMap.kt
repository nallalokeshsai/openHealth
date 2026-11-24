@file:Suppress("ReturnCount", "MagicNumber")

package app.openhealth.tools.foodindex

import app.openhealth.core.nutrition.Nutrient
import app.openhealth.core.units.Dimension
import app.openhealth.core.units.UcumUnit
import java.util.Locale

/**
 * The FoodData Central nutrient id(s) that feed each of our nutrients. An id list means
 * "use the first one the food reports": energy has three forms in FDC and a food rarely
 * carries more than one. The unit is checked against the dataset, so a milligram value can
 * never be written into a microgram slot.
 */
object FdcNutrientMap {
    val byFdcId: Map<String, Nutrient> = mapOf(
        "1008" to Nutrient.ENERGY,
        "2047" to Nutrient.ENERGY,
        "2048" to Nutrient.ENERGY,
        "1003" to Nutrient.PROTEIN,
        "1004" to Nutrient.FAT,
        "1005" to Nutrient.CARBS,
        "1079" to Nutrient.FIBRE,
        "2000" to Nutrient.SUGARS,
        "1258" to Nutrient.SATURATED_FAT,
        "1292" to Nutrient.MONOUNSATURATED_FAT,
        "1293" to Nutrient.POLYUNSATURATED_FAT,
        "1257" to Nutrient.TRANS_FAT,
        "1253" to Nutrient.CHOLESTEROL,
        "1093" to Nutrient.SODIUM,
        "1092" to Nutrient.POTASSIUM,
        "1087" to Nutrient.CALCIUM,
        "1089" to Nutrient.IRON,
        "1090" to Nutrient.MAGNESIUM,
        "1095" to Nutrient.ZINC,
        "1091" to Nutrient.PHOSPHORUS,
        "1103" to Nutrient.SELENIUM,
        "1098" to Nutrient.COPPER,
        "1101" to Nutrient.MANGANESE,
        "1106" to Nutrient.VITAMIN_A,
        "1162" to Nutrient.VITAMIN_C,
        "1114" to Nutrient.VITAMIN_D,
        "1109" to Nutrient.VITAMIN_E,
        "1185" to Nutrient.VITAMIN_K,
        "1165" to Nutrient.THIAMIN,
        "1166" to Nutrient.RIBOFLAVIN,
        "1167" to Nutrient.NIACIN,
        "1175" to Nutrient.VITAMIN_B6,
        "1177" to Nutrient.FOLATE,
        "1178" to Nutrient.VITAMIN_B12,
        "1057" to Nutrient.CAFFEINE,
        "1051" to Nutrient.WATER,
    )

    /** The order energy ids are tried in; the general factor is the better default. */
    val energyPriority: List<String> = listOf("1008", "2047", "2048")

    /** Converts a dataset amount in [sourceUnit] into [target]'s unit. */
    fun convert(amount: Double, sourceUnit: String, target: UcumUnit): Double? {
        val source = when (sourceUnit.uppercase(Locale.ROOT)) {
            "G" -> UcumUnit.GRAM
            "MG" -> UcumUnit.MILLIGRAM
            "UG", "µG", "MCG" -> UcumUnit.MICROGRAM
            "KCAL" -> UcumUnit.KILOCALORIE
            else -> return null
        }
        if (source.dimension != target.dimension) return null
        if (source.dimension == Dimension.MASS) {
            return amount * source.scale / target.scale
        }
        return amount
    }

    fun nutrientFor(fdcId: String): Nutrient? = byFdcId[fdcId]
}

/** Name normalisation lives in `:core` so the builder and the app share one tokeniser. */
object FoodNames {
    fun normalize(name: String): String = app.openhealth.core.nutrition.FoodQuery.normalize(name)

    fun tokens(name: String): List<String> = app.openhealth.core.nutrition.FoodQuery.tokens(name)
}
