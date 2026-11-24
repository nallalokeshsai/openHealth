package app.openhealth.core.nutrition

/**
 * One food in the bundled USDA FoodData Central index (`tools/foodindex`). The index is
 * read-only, because it is data and not a diary; logging a food copies it into the
 * encrypted database, which is then "everything this person has ever logged".
 */
data class IndexedFood(
    val id: String,
    val name: String,
    val category: String?,
    val nutrition: FoodNutrition,
) {
    val energyKcalPer100g: Double? get() = nutrition.per100g[Nutrient.ENERGY]
}

/** The dataset record shown on the food-data attribution screen (`pr.md` §12.2). */
data class FoodIndexMeta(
    val version: String,
    val licence: String,
    val licenceUrl: String,
    val foodCount: Int,
    val foundationCount: Int,
    val srLegacyCount: Int,
    val sourceUrls: List<String>,
    val generatedAt: String,
)

/**
 * The bundled offline index (`pr.md` §6.3). It is a platform seam because the compact
 * SQLite file ships as an app asset; the search contract stays here so `:core` owns the
 * behaviour and the Android implementation owns only file access.
 */
interface BundledFoodIndex {
    /** The dataset version string, e.g. "USDA FDC foundation 2026-04-30 + SR Legacy 2018-04". */
    val version: String

    val foodCount: Int

    val meta: FoodIndexMeta

    /** Name search over the bundled foods, most useful first, at most [limit]. */
    fun search(query: String, limit: Int): List<IndexedFood>

    fun byId(id: String): IndexedFood?
}
