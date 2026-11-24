@file:Suppress("ReturnCount")

package app.openhealth.data.repo

import app.openhealth.core.nutrition.FastWindow
import app.openhealth.core.nutrition.FoodNutrition
import app.openhealth.core.nutrition.FoodPortion
import app.openhealth.core.nutrition.MacroTargets
import app.openhealth.core.nutrition.Nutrient
import app.openhealth.core.nutrition.NutrientTotals
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json

/** The four meal sections and a custom meal (`pr.md` §6.3, `DESIGN.md` §14.2). */
enum class MealKind(val key: String) {
    BREAKFAST("breakfast"),
    LUNCH("lunch"),
    DINNER("dinner"),
    SNACKS("snacks"),
    CUSTOM("custom"),
    ;

    companion object {
        fun fromKey(key: String?): MealKind? = entries.firstOrNull { it.key == key }

        /** The section order of the diary: breakfast, lunch, dinner, snacks, then custom. */
        val daySections: List<MealKind> = listOf(BREAKFAST, LUNCH, DINNER, SNACKS)
    }
}

/** How a food came to be in the diary; it is shown on every entry (`pr.md` §6.3). */
enum class FoodType(val key: String) {
    BUNDLED("bundled"),
    OPEN_FOOD_FACTS("openfoodfacts"),
    CUSTOM("custom"),
    RECIPE("recipe"),
    ;

    companion object {
        fun fromKey(key: String?): FoodType = entries.firstOrNull { it.key == key } ?: CUSTOM
    }
}

/** A food as the diary reads it. Nutrients are per 100 g and unrounded. */
data class FoodRecord(
    val id: String,
    val name: String,
    val brand: String?,
    val barcode: String?,
    val sourceKind: String,
    val sourceRef: String?,
    val category: String?,
    val foodType: FoodType,
    val per100g: Map<Nutrient, Double>,
    val portions: List<FoodPortion>,
    val defaultServingG: Double?,
    val defaultServingLabel: String?,
    val allergens: Set<String>,
    val ingredientsText: String?,
    val analysisTags: Set<String>,
    val verified: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val revision: Long,
) {
    val nutrition: FoodNutrition get() = FoodNutrition(per100g, portions)
    val energyKcalPer100g: Double? get() = per100g[Nutrient.ENERGY]
    val isRecipe: Boolean get() = foodType == FoodType.RECIPE
}

/** What the caller supplies to create or update a food; ids and clocks are the repo's job. */
data class FoodDraft(
    val id: String,
    val name: String,
    val brand: String? = null,
    val barcode: String? = null,
    val sourceKind: String = "manual",
    val sourceRef: String? = null,
    val category: String? = null,
    val foodType: FoodType = FoodType.CUSTOM,
    val per100g: Map<Nutrient, Double> = emptyMap(),
    val portions: List<FoodPortion> = emptyList(),
    val defaultServingG: Double? = null,
    val defaultServingLabel: String? = null,
    val allergens: Set<String> = emptySet(),
    val ingredientsText: String? = null,
    val analysisTags: Set<String> = emptySet(),
    val verified: Boolean = false,
)

data class MealRecord(
    val id: String,
    val name: String,
    val kind: MealKind,
    val localDate: LocalDate,
    val zoneId: String?,
    val happenedAt: Instant?,
    val recordedAt: Instant?,
    val sourceId: String?,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val revision: Long,
)

data class MealDraft(
    val id: String,
    val name: String,
    val kind: MealKind,
    val localDate: LocalDate,
    val zoneId: TimeZone? = null,
    val happenedAt: Instant? = null,
    val recordedAt: Instant? = null,
    val sourceId: String? = null,
    val idempotencyKey: String? = null,
    val notes: String? = null,
)

/** One logged food. [totals] are the absolute amounts in this entry, not per 100 g. */
data class EntryRecord(
    val id: String,
    val mealId: String?,
    val foodId: String?,
    val localDate: LocalDate,
    val zoneId: String,
    val happenedAt: Instant,
    val recordedAt: Instant,
    val quantityG: Double?,
    val servingLabel: String?,
    val servingG: Double?,
    val servings: Double?,
    val foodName: String?,
    val brand: String?,
    val totals: NutrientTotals,
    val sourceKind: String,
    val sourceId: String?,
    val verified: Boolean,
    val edited: Boolean,
    val observationId: String?,
    val rawRecordId: String?,
    val idempotencyKey: String,
    val device: String?,
    val appVersion: String?,
    val revision: Long,
)

data class EntryDraft(
    val id: String,
    val mealId: String?,
    val foodId: String?,
    val localDate: LocalDate,
    val zoneId: TimeZone,
    val happenedAt: Instant,
    val recordedAt: Instant,
    val quantityG: Double?,
    val servingLabel: String? = null,
    val servingG: Double? = null,
    val servings: Double? = null,
    val foodName: String? = null,
    val brand: String? = null,
    val totals: Map<Nutrient, Double> = emptyMap(),
    val sourceKind: String = "manual",
    val sourceId: String? = null,
    val verified: Boolean = false,
    val edited: Boolean = false,
    val observationId: String? = null,
    val rawRecordId: String? = null,
    val idempotencyKey: String,
    val device: String? = null,
    val appVersion: String? = null,
)

data class RecipeItemRecord(
    val id: String,
    val recipeFoodId: String,
    val foodId: String?,
    val position: Long,
    val grams: Double,
    val foodName: String?,
)

data class RecipeItemDraft(
    val id: String,
    val foodId: String?,
    val grams: Double,
    val foodName: String?,
)

data class MealTemplateRecord(
    val id: String,
    val name: String,
    val kind: MealKind,
    val createdAt: Long,
    val updatedAt: Long,
    val revision: Long,
)

data class MealTemplateItemRecord(
    val id: String,
    val templateId: String,
    val foodId: String?,
    val position: Long,
    val grams: Double,
    val foodName: String?,
)

data class WaterContainerRecord(
    val id: String,
    val label: String,
    val millilitres: Double,
    val position: Long,
    val isDefault: Boolean,
)

data class NutritionTargetRecord(
    val id: String,
    val effectiveFrom: LocalDate,
    val targets: MacroTargets,
    val createdAt: Long,
    val updatedAt: Long,
    val revision: Long,
)

data class FastRecord(
    val id: String,
    val localDate: LocalDate,
    val zoneId: String,
    val window: FastWindow,
    val recordedAt: Instant,
    val createdAt: Long,
    val updatedAt: Long,
    val revision: Long,
)

/**
 * The JSON form for nutrient maps, portion lists and tag sets. One object, so the
 * repository, the rollup and the export all read and write the same shape.
 */
object NutritionJson {
    val format: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encodeNutrients(values: Map<Nutrient, Double>): String {
        if (values.isEmpty()) return "{}"
        val ordered = values.entries
            .sortedBy { it.key.ordinal }
            .joinToString(prefix = "{", postfix = "}") { (nutrient, amount) ->
                "\"${nutrient.key}\":$amount"
            }
        return ordered
    }

    fun decodeNutrients(text: String?): Map<Nutrient, Double> {
        if (text.isNullOrBlank() || text == "{}") return emptyMap()
        val parsed = runCatching { format.parseToJsonElement(text) }.getOrNull()
            ?: return emptyMap()
        val obj = parsed as? kotlinx.serialization.json.JsonObject ?: return emptyMap()
        val values = LinkedHashMap<Nutrient, Double>()
        obj.forEach { (key, element) ->
            val nutrient = Nutrient.fromKey(key) ?: return@forEach
            val amount = (element as? kotlinx.serialization.json.JsonPrimitive)
                ?.content?.toDoubleOrNull() ?: return@forEach
            values[nutrient] = amount
        }
        return values
    }

    fun encodePortions(portions: List<FoodPortion>): String =
        kotlinx.serialization.json.buildJsonArray {
            portions.forEach { portion ->
                add(
                    kotlinx.serialization.json.buildJsonObject {
                        put("label", kotlinx.serialization.json.JsonPrimitive(portion.label))
                        put("grams", kotlinx.serialization.json.JsonPrimitive(portion.grams))
                    },
                )
            }
        }.toString()

    fun decodePortions(text: String?): List<FoodPortion> {
        if (text.isNullOrBlank()) return emptyList()
        val parsed = runCatching { format.parseToJsonElement(text) }.getOrNull()
        val array = parsed as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = element as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
            val label = (obj["label"] as? kotlinx.serialization.json.JsonPrimitive)
                ?.content ?: return@mapNotNull null
            val grams = (obj["grams"] as? kotlinx.serialization.json.JsonPrimitive)
                ?.content?.toDoubleOrNull() ?: return@mapNotNull null
            FoodPortion(label, grams)
        }
    }

    fun encodeTags(tags: Set<String>): String = kotlinx.serialization.json.buildJsonArray {
        tags.sorted().forEach {
            add(kotlinx.serialization.json.JsonPrimitive(it))
        }
    }.toString()

    fun decodeTags(text: String?): Set<String> {
        if (text.isNullOrBlank()) return emptySet()
        val parsed = runCatching { format.parseToJsonElement(text) }.getOrNull()
        val array = parsed as? kotlinx.serialization.json.JsonArray ?: return emptySet()
        return array.mapNotNull {
            (it as? kotlinx.serialization.json.JsonPrimitive)?.content
        }.toSet()
    }
}
