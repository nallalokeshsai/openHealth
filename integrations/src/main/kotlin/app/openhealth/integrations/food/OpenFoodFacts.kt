@file:Suppress("ReturnCount", "MagicNumber")

package app.openhealth.integrations.food

import app.openhealth.core.nutrition.Nutrient
import app.openhealth.core.seam.HttpRequest
import app.openhealth.core.seam.HttpTransport
import app.openhealth.core.units.UcumUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** One product as Open Food Facts published it, normalised to our nutrient set. */
data class OffProduct(
    val barcode: String,
    val name: String,
    val brand: String?,
    val servingGrams: Double?,
    val servingLabel: String?,
    val per100g: Map<Nutrient, Double>,
    val allergens: Set<String>,
    val analysisTags: Set<String>,
    val ingredientsText: String?,
)

sealed interface OffLookup {
    data class Found(val product: OffProduct) : OffLookup

    data object NotFound : OffLookup

    /** No answer: the device is offline, the call timed out, or the server refused. */
    data class Unavailable(val reason: String) : OffLookup
}

/**
 * Open Food Facts is ODbL and is used only when the person asks for one barcode
 * (`pr.md` §6.3, §12.2). The request carries the barcode and nothing else; there is no
 * account, no device id and no diary data in the URL or the headers.
 */
class OpenFoodFactsClient(
    private val transport: HttpTransport,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val userAgent: String = DEFAULT_USER_AGENT,
) {
    suspend fun lookup(barcode: String): OffLookup {
        if (!isPlausibleBarcode(barcode)) return OffLookup.NotFound
        val url = "$baseUrl/api/v2/product/$barcode.json" +
            "?fields=product_name,brands,serving_quantity,serving_size,nutriments," +
            "allergens_tags,ingredients_analysis_tags,ingredients_text,code"
        val response = runCatching {
            transport.get(
                HttpRequest(
                    url = url,
                    headers = mapOf(
                        "User-Agent" to userAgent,
                        "Accept" to "application/json",
                    ),
                ),
            )
        }.getOrElse { return OffLookup.Unavailable(it.javaClass.simpleName) }
        if (response.status == 404) return OffLookup.NotFound
        if (response.status !in 200..299) {
            return OffLookup.Unavailable("HTTP ${response.status}")
        }
        return when (val parsed = OffParser.parse(response.body, barcode)) {
            is OffParser.Result.Found -> OffLookup.Found(parsed.product)
            OffParser.Result.NotFound -> OffLookup.NotFound
            is OffParser.Result.Invalid -> OffLookup.Unavailable(parsed.reason)
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://world.openfoodfacts.org"

        /**
         * Open Food Facts asks clients to identify themselves and to say where the app is
         * from. This is the only header the app adds, and it names no device or person.
         */
        const val DEFAULT_USER_AGENT =
            "openHealth/1.0 (Android; +https://github.com/nallalokeshsai/openHealth)"

        /** EAN-8, UPC-A, EAN-13 and ITF-14 digits only; no spaces or letters. */
        fun isPlausibleBarcode(barcode: String): Boolean =
            barcode.length in 8..14 && barcode.all { it.isDigit() }
    }
}

/** Parses the public API v2 product payload without trusting any of its optional fields. */
object OffParser {
    private val json = Json { ignoreUnknownKeys = true }

    sealed interface Result {
        data class Found(val product: OffProduct) : Result

        data object NotFound : Result

        data class Invalid(val reason: String) : Result
    }

    fun parse(body: String, barcode: String): Result {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject
            ?: return Result.Invalid("invalid-json")
        val status = (root["status"] as? JsonPrimitive)?.content?.toIntOrNull()
        val product = root["product"] as? JsonObject
        if (status == 0 || product == null) return Result.NotFound
        val name = product.string("product_name")
            ?: product.string("generic_name")
            ?: return Result.Invalid("no-name")
        if (name.isBlank()) return Result.Invalid("no-name")
        val nutriments = product["nutriments"] as? JsonObject ?: JsonObject(emptyMap())
        val per100g = nutrientMap(nutriments)
        if (per100g.isEmpty()) return Result.Invalid("no-nutrition")
        val servingGrams = product.number("serving_quantity")
            ?.takeIf { it > 0.0 }
        val servingLabel = product.string("serving_size")
            ?.takeIf { it.isNotBlank() }
        return Result.Found(
            OffProduct(
                barcode = barcode,
                name = name.trim(),
                brand = product.string("brands")?.substringBefore(',')?.trim()
                    ?.takeIf { it.isNotBlank() },
                servingGrams = servingGrams,
                servingLabel = servingLabel,
                per100g = per100g,
                allergens = product.stringSet("allergens_tags"),
                analysisTags = product.stringSet("ingredients_analysis_tags"),
                ingredientsText = product.string("ingredients_text")
                    ?.takeIf { it.isNotBlank() },
            ),
        )
    }

    /** OFF stores every nutrient except energy in grams per 100 g. */
    private fun nutrientMap(nutriments: JsonObject): Map<Nutrient, Double> {
        val values = LinkedHashMap<Nutrient, Double>()
        val energyKcal = nutriments.number("energy-kcal_100g")
            ?: nutriments.number("energy_100g")?.let { it / KILOJOULES_PER_KILOCALORIE }
        energyKcal?.let { values[Nutrient.ENERGY] = it }
        OFF_KEYS.forEach { (key, nutrient) ->
            val grams = nutriments.number(key) ?: return@forEach
            values[nutrient] = fromGrams(grams, nutrient.unit)
        }
        return values
    }

    private fun fromGrams(grams: Double, target: UcumUnit): Double =
        grams * UcumUnit.GRAM.scale / target.scale

    private val OFF_KEYS = mapOf(
        "proteins_100g" to Nutrient.PROTEIN,
        "carbohydrates_100g" to Nutrient.CARBS,
        "fat_100g" to Nutrient.FAT,
        "fiber_100g" to Nutrient.FIBRE,
        "fibre_100g" to Nutrient.FIBRE,
        "sugars_100g" to Nutrient.SUGARS,
        "saturated-fat_100g" to Nutrient.SATURATED_FAT,
        "monounsaturated-fat_100g" to Nutrient.MONOUNSATURATED_FAT,
        "polyunsaturated-fat_100g" to Nutrient.POLYUNSATURATED_FAT,
        "trans-fat_100g" to Nutrient.TRANS_FAT,
        "cholesterol_100g" to Nutrient.CHOLESTEROL,
        "sodium_100g" to Nutrient.SODIUM,
        "potassium_100g" to Nutrient.POTASSIUM,
        "calcium_100g" to Nutrient.CALCIUM,
        "iron_100g" to Nutrient.IRON,
        "magnesium_100g" to Nutrient.MAGNESIUM,
        "zinc_100g" to Nutrient.ZINC,
        "phosphorus_100g" to Nutrient.PHOSPHORUS,
        "selenium_100g" to Nutrient.SELENIUM,
        "copper_100g" to Nutrient.COPPER,
        "manganese_100g" to Nutrient.MANGANESE,
        "vitamin-a_100g" to Nutrient.VITAMIN_A,
        "vitamin-c_100g" to Nutrient.VITAMIN_C,
        "vitamin-d_100g" to Nutrient.VITAMIN_D,
        "vitamin-e_100g" to Nutrient.VITAMIN_E,
        "vitamin-k_100g" to Nutrient.VITAMIN_K,
        "vitamin-b1_100g" to Nutrient.THIAMIN,
        "vitamin-b2_100g" to Nutrient.RIBOFLAVIN,
        "vitamin-pp_100g" to Nutrient.NIACIN,
        "vitamin-b6_100g" to Nutrient.VITAMIN_B6,
        "folates_100g" to Nutrient.FOLATE,
        "vitamin-b12_100g" to Nutrient.VITAMIN_B12,
        "caffeine_100g" to Nutrient.CAFFEINE,
        "water_100g" to Nutrient.WATER,
    )

    private const val KILOJOULES_PER_KILOCALORIE = 4.184

    private fun JsonObject.number(key: String): Double? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.stringSet(key: String): Set<String> = (this[key] as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        ?.toSet()
        .orEmpty()
}
