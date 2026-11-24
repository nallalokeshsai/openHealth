package app.openhealth.core.nutrition

/**
 * A household measure the bundled index carries for a food, copied from USDA FoodData
 * Central's `food_portion` table (`tools/foodindex`). A portion is named as the dataset
 * names it, with its gram weight.
 */
data class FoodPortion(val label: String, val grams: Double)

/**
 * The nutrition of one food, as stored or as read from the bundled index. [per100g] is
 * keyed by [Nutrient] and never rounded (`pr.md` §6.3).
 */
data class FoodNutrition(
    val per100g: Map<Nutrient, Double>,
    val portions: List<FoodPortion> = emptyList(),
) {
    fun portion(label: String): FoodPortion? =
        portions.firstOrNull { it.label.equals(label, ignoreCase = true) }
}

/**
 * Portion and recipe arithmetic. Every function is pure and rounds nothing: a day's
 * totals are the exact sum of the entries that the same multiplication produced.
 */
object PortionMath {
    /** The amount of every nutrient in [grams] of a food. */
    fun forGrams(per100g: Map<Nutrient, Double>, grams: Double): Map<Nutrient, Double> {
        require(grams >= 0.0) { "A portion cannot have a negative mass." }
        return per100g.mapValues { (_, value) -> value * grams / PERCENT }
    }

    /** The mass in grams of [servings] servings of [portion]. */
    fun gramsFor(portion: FoodPortion, servings: Double): Double {
        require(servings >= 0.0) { "A portion cannot have a negative count." }
        return portion.grams * servings
    }

    /** The kilocalories in [grams] of a food, or null when the food does not report it. */
    fun kilocalories(per100g: Map<Nutrient, Double>, grams: Double): Double? =
        per100g[Nutrient.ENERGY]?.let { it * grams / PERCENT }
}

/**
 * A day's nutrient totals. [sumInOrder] is the one addition the diary, the rollup and the
 * tests all use, so "the totals equal the sum of the entries" is a statement about the
 * same code path, not a coincidence of two loops.
 */
data class NutrientTotals(val values: Map<Nutrient, Double>) {
    operator fun plus(other: NutrientTotals): NutrientTotals = combine(listOf(this, other))

    fun of(nutrient: Nutrient): Double? = values[nutrient]

    fun rounded(nutrient: Nutrient, decimals: Int): Double? =
        values[nutrient]?.let { round(it, decimals) }

    companion object {
        val EMPTY = NutrientTotals(emptyMap())

        /** Adds entries left to right, exactly as the rollup does. */
        fun sumInOrder(entries: List<Map<Nutrient, Double>>): NutrientTotals {
            val totals = LinkedHashMap<Nutrient, Double>()
            entries.forEach { entry ->
                entry.forEach { (nutrient, amount) ->
                    totals[nutrient] = (totals[nutrient] ?: 0.0) + amount
                }
            }
            return NutrientTotals(totals)
        }

        fun combine(totals: List<NutrientTotals>): NutrientTotals =
            sumInOrder(totals.map { it.values })
    }
}

private fun round(value: Double, decimals: Int): Double {
    var factor = 1.0
    repeat(decimals) { factor *= DECIMAL_BASE }
    return kotlin.math.round(value * factor) / factor
}

/**
 * Recipe arithmetic (`pr.md` §6.3): a recipe is its ingredients, and its per-100 g values
 * are derived from them. [servings] is metadata used to pre-fill a portion; it never
 * changes the per-100 g values.
 */
object RecipeMath {
    data class Ingredient(val grams: Double, val per100g: Map<Nutrient, Double>)

    /**
     * The finished dish's per-100 g values. When the ingredients weigh nothing the recipe
     * has no nutrition rather than a division by zero.
     */
    fun per100g(ingredients: List<Ingredient>): Map<Nutrient, Double> {
        val totalGrams = ingredients.sumOf { it.grams }
        if (totalGrams <= 0.0) return emptyMap()
        val totals = NutrientTotals.sumInOrder(ingredients.map { forGrams(it) })
        return totals.values.mapValues { (_, amount) -> amount / totalGrams * PERCENT }
    }

    /** The grams of one serving when a recipe of [totalGrams] makes [servings] servings. */
    fun servingGrams(totalGrams: Double, servings: Int): Double {
        require(servings > 0) { "A recipe has at least one serving." }
        return totalGrams / servings
    }

    private fun forGrams(ingredient: Ingredient): Map<Nutrient, Double> =
        PortionMath.forGrams(ingredient.per100g, ingredient.grams)
}

/** A per-100 g value and a percentage are the same factor. */
private const val PERCENT = 100.0

private const val DECIMAL_BASE = 10.0
