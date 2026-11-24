@file:Suppress("MagicNumber")

package app.openhealth.ui.screens.nutrition

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.openhealth.core.nutrition.Nutrient
import app.openhealth.core.preferences.Allergen
import app.openhealth.core.preferences.DietaryPattern
import app.openhealth.core.units.UcumUnit
import app.openhealth.ui.R
import app.openhealth.ui.screens.common.number

/** The labels the Nutrition surfaces share (`DESIGN.md` §11.4, §11.7). */
@Composable
fun nutrientLabel(nutrient: Nutrient): String = stringResource(
    when (nutrient) {
        Nutrient.ENERGY -> R.string.nutrient_energy
        Nutrient.PROTEIN -> R.string.nutrient_protein
        Nutrient.CARBS -> R.string.nutrient_carbs
        Nutrient.FAT -> R.string.nutrient_fat
        Nutrient.FIBRE -> R.string.nutrient_fibre
        Nutrient.SUGARS -> R.string.nutrient_sugars
        Nutrient.SATURATED_FAT -> R.string.nutrient_saturated_fat
        Nutrient.MONOUNSATURATED_FAT -> R.string.nutrient_monounsaturated_fat
        Nutrient.POLYUNSATURATED_FAT -> R.string.nutrient_polyunsaturated_fat
        Nutrient.TRANS_FAT -> R.string.nutrient_trans_fat
        Nutrient.CHOLESTEROL -> R.string.nutrient_cholesterol
        Nutrient.SODIUM -> R.string.nutrient_sodium
        Nutrient.POTASSIUM -> R.string.nutrient_potassium
        Nutrient.CALCIUM -> R.string.nutrient_calcium
        Nutrient.IRON -> R.string.nutrient_iron
        Nutrient.MAGNESIUM -> R.string.nutrient_magnesium
        Nutrient.ZINC -> R.string.nutrient_zinc
        Nutrient.PHOSPHORUS -> R.string.nutrient_phosphorus
        Nutrient.SELENIUM -> R.string.nutrient_selenium
        Nutrient.COPPER -> R.string.nutrient_copper
        Nutrient.MANGANESE -> R.string.nutrient_manganese
        Nutrient.VITAMIN_A -> R.string.nutrient_vitamin_a
        Nutrient.VITAMIN_C -> R.string.nutrient_vitamin_c
        Nutrient.VITAMIN_D -> R.string.nutrient_vitamin_d
        Nutrient.VITAMIN_E -> R.string.nutrient_vitamin_e
        Nutrient.VITAMIN_K -> R.string.nutrient_vitamin_k
        Nutrient.THIAMIN -> R.string.nutrient_thiamin
        Nutrient.RIBOFLAVIN -> R.string.nutrient_riboflavin
        Nutrient.NIACIN -> R.string.nutrient_niacin
        Nutrient.VITAMIN_B6 -> R.string.nutrient_vitamin_b6
        Nutrient.FOLATE -> R.string.nutrient_folate
        Nutrient.VITAMIN_B12 -> R.string.nutrient_vitamin_b12
        Nutrient.CAFFEINE -> R.string.nutrient_caffeine
        Nutrient.WATER -> R.string.nutrient_water
    },
)

@Composable
fun unitLabel(unit: UcumUnit): String = when (unit) {
    UcumUnit.KILOCALORIE -> stringResource(R.string.unit_kcal)
    UcumUnit.MILLIGRAM -> stringResource(R.string.unit_mg)
    UcumUnit.MICROGRAM -> stringResource(R.string.unit_ug)
    UcumUnit.MILLILITRE -> stringResource(R.string.unit_ml)
    else -> stringResource(R.string.unit_g)
}

@Composable
fun mealLabel(slot: MealSlot, customName: String? = null): String = when (slot) {
    MealSlot.BREAKFAST -> stringResource(R.string.nutrition_meal_breakfast)
    MealSlot.LUNCH -> stringResource(R.string.nutrition_meal_lunch)
    MealSlot.DINNER -> stringResource(R.string.nutrition_meal_dinner)
    MealSlot.SNACKS -> stringResource(R.string.nutrition_meal_snacks)
    MealSlot.CUSTOM -> customName.orEmpty()
}

@Composable
fun sourceLabel(key: String): String = when (key) {
    "usda", "bundled" -> stringResource(R.string.nutrition_source_usda)
    "openfoodfacts" -> stringResource(R.string.nutrition_source_off)
    "recipe" -> stringResource(R.string.nutrition_source_recipe)
    "custom" -> stringResource(R.string.nutrition_source_custom)
    "quick" -> stringResource(R.string.nutrition_source_quick)
    else -> stringResource(R.string.nutrition_source_manual)
}

@Composable
fun allergenLabel(allergen: Allergen): String = stringResource(
    when (allergen) {
        Allergen.GLUTEN -> R.string.allergen_gluten
        Allergen.MILK -> R.string.allergen_milk
        Allergen.EGGS -> R.string.allergen_eggs
        Allergen.PEANUTS -> R.string.allergen_peanuts
        Allergen.NUTS -> R.string.allergen_nuts
        Allergen.SOY -> R.string.allergen_soy
        Allergen.FISH -> R.string.allergen_fish
        Allergen.SHELLFISH -> R.string.allergen_shellfish
        Allergen.SESAME -> R.string.allergen_sesame
        Allergen.OTHER -> R.string.allergen_other
    },
)

@Composable
fun dietPatternLabel(key: String?): String = when (DietaryPattern.fromKey(key)) {
    DietaryPattern.VEGAN -> stringResource(R.string.diet_vegan)
    DietaryPattern.VEGETARIAN -> stringResource(R.string.diet_vegetarian)
    DietaryPattern.PESCATARIAN -> stringResource(R.string.diet_pescatarian)
    DietaryPattern.HALAL -> stringResource(R.string.diet_halal)
    DietaryPattern.KOSHER -> stringResource(R.string.diet_kosher)
    DietaryPattern.OTHER -> stringResource(R.string.diet_other)
    else -> stringResource(R.string.diet_omnivore)
}

/** "1 cup", "1.5 × 1 cup" or "120 g", the row's subtitle. */
@Composable
fun portionText(grams: Double?, servings: Double?, servingLabel: String?): String = when {
    servings != null && servings != 1.0 && servingLabel != null ->
        stringResource(
            R.string.nutrition_entry_servings,
            number(servings, 2),
            servingLabel,
        )

    servingLabel != null && grams != null ->
        stringResource(R.string.nutrition_portion_serving_label, number(grams), servingLabel)

    grams != null -> stringResource(R.string.nutrition_entry_amount, number(grams))
    else -> stringResource(R.string.nutrition_quick_add)
}

/** "14 h 20 min" or "45 min"; never a decimal hour. */
@Composable
fun durationText(millis: Long): String {
    val totalMinutes = (millis / 60_000).coerceAtLeast(0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours <= 0 -> stringResource(R.string.unit_minutes_short, minutes.toInt())
        minutes == 0L -> stringResource(R.string.unit_hours_short, hours.toInt())
        else -> "${stringResource(R.string.unit_hours_short, hours.toInt())} " +
            stringResource(R.string.unit_minutes_short, minutes.toInt())
    }
}

@Composable
fun energyText(kcal: Double): String = stringResource(
    R.string.nutrition_portion_kcal,
    number(kcal),
)
