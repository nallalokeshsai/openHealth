package app.openhealth.ui.screens.nutrition

import app.openhealth.core.nutrition.FoodPortion
import app.openhealth.core.nutrition.Nutrient
import kotlinx.datetime.LocalDate

/** The four default meal sections; a custom meal is its own row with a user name. */
enum class MealSlot(val key: String) {
    BREAKFAST("breakfast"),
    LUNCH("lunch"),
    DINNER("dinner"),
    SNACKS("snacks"),
    CUSTOM("custom"),
    ;

    companion object {
        fun fromKey(key: String): MealSlot = entries.firstOrNull { it.key == key } ?: CUSTOM

        val daySections = listOf(BREAKFAST, LUNCH, DINNER, SNACKS)
    }
}

/** The four macro bars of the energy card, in their fixed order (`DESIGN.md` §8.4). */
enum class MacroKey { PROTEIN, CARBS, FAT, FIBRE }

data class NutritionDay(
    val date: LocalDate,
    val isToday: Boolean,
    val isSelected: Boolean,
    val hasEntries: Boolean,
)

data class MacroBarModel(
    val key: MacroKey,
    val grams: Double,
    val targetGrams: Double?,
    /** Grams over target, clamped to 0..1, or null when no target is set. */
    val fraction: Float?,
)

data class EnergyCardModel(
    val kcalIn: Double,
    val kcalTarget: Double?,
    val remainingKcal: Double?,
    val bars: List<MacroBarModel>,
    val goalLine: String?,
)

data class DayEntryModel(
    val id: String,
    val name: String,
    val grams: Double?,
    val servings: Double?,
    val servingLabel: String?,
    val kcal: Double?,
    val sourceKey: String,
    val verified: Boolean,
    val edited: Boolean,
)

/** One meal section on the diary. A default section has [mealId] null until its first entry. */
data class MealSectionModel(
    val mealId: String?,
    val slot: MealSlot,
    val name: String?,
    val entries: List<DayEntryModel>,
    val totalKcal: Double?,
    val isToday: Boolean,
)

data class WaterContainerModel(
    val id: String,
    val label: String,
    val millilitres: Double,
    val isDefault: Boolean,
)

data class WaterCardModel(
    val totalMl: Double,
    val targetMl: Double?,
    val fraction: Float?,
    val containers: List<WaterContainerModel>,
    val lastContainer: WaterContainerModel?,
)

data class FastCardModel(
    val open: Boolean,
    val startAtMillis: Long?,
    val nowMillis: Long,
    val elapsedMillis: Long,
    val remainingMillis: Long?,
    val targetHours: Double,
    val progress: Float,
)

data class NutritionModel(
    val selectedDate: LocalDate,
    val today: LocalDate,
    val week: List<NutritionDay>,
    val energy: EnergyCardModel?,
    val meals: List<MealSectionModel>,
    val water: WaterCardModel?,
    val fast: FastCardModel?,
    val targetsSet: Boolean,
    val weightNeutral: Boolean,
    val demo: Boolean,
    val canCopyDay: Boolean,
)

/** A food as the search sheet, portion sheet and editor pass it around. */
data class FoodChoiceModel(
    val id: String,
    val name: String,
    val brand: String?,
    val barcode: String?,
    val sourceKey: String,
    val sourceLabel: String,
    val foodTypeKey: String,
    val per100g: Map<Nutrient, Double>,
    val portions: List<FoodPortion>,
    val defaultServingG: Double?,
    val defaultServingLabel: String?,
    val allergens: Set<String>,
    val analysisTags: Set<String>,
    val ingredientsText: String?,
    val verified: Boolean,
    val isRecipe: Boolean,
    val isBundled: Boolean,
)

data class FoodRowModel(val food: FoodChoiceModel)

data class MealTemplateRowModel(
    val id: String,
    val name: String,
    val items: Int,
    val kcal: Double?,
)

/** A meal a new entry can go to: a fixed slot (id null) or a saved custom meal. */
data class MealChoiceModel(val id: String?, val slot: MealSlot, val label: String)

data class FoodSearchModel(
    val query: String,
    val meal: MealChoiceModel,
    val meals: List<MealChoiceModel>,
    val date: LocalDate,
    val local: List<FoodRowModel>,
    val bundled: List<FoodRowModel>,
    val recents: List<FoodRowModel>,
    val frequents: List<FoodRowModel>,
    val templates: List<MealTemplateRowModel>,
    val indexVersion: String,
    /** What an explicit Open Food Facts search returned; empty until the person asks. */
    val online: List<FoodRowModel> = emptyList(),
    val onlineStatus: OnlineSearchStatus = OnlineSearchStatus.IDLE,
)

enum class OnlineSearchStatus { IDLE, SEARCHING, DONE, UNAVAILABLE }

enum class DietStatus { MATCHES, DOES_NOT_MATCH, UNKNOWN }

data class PortionModel(
    val food: FoodChoiceModel,
    val meal: MealChoiceModel,
    val meals: List<MealChoiceModel>,
    val date: LocalDate,
    val grams: Double,
    val servings: Double,
    val servingLabel: String?,
    val entryId: String?,
    val weightNeutral: Boolean,
    val allergens: Set<app.openhealth.core.preferences.Allergen>,
    val hasAllergenData: Boolean,
    val dietPatternKey: String?,
    val dietStatus: DietStatus,
)

data class EntryDetailModel(
    val id: String,
    val name: String,
    val brand: String?,
    val mealSlot: MealSlot,
    val mealName: String?,
    val date: LocalDate,
    val grams: Double?,
    val servings: Double?,
    val servingLabel: String?,
    val kcal: Double?,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
    val fibreG: Double?,
    val happenedAtMillis: Long,
    val recordedAtMillis: Long,
    val zoneId: String,
    val device: String?,
    val sourceKey: String,
    val verified: Boolean,
    val edited: Boolean,
    val rawRecordId: String?,
)

data class WaterSheetModel(
    val date: LocalDate,
    val containers: List<WaterContainerModel>,
    val selectedId: String?,
    val totalMl: Double,
    val targetMl: Double?,
)

data class TargetsModel(
    val date: LocalDate,
    val kcal: String,
    val proteinG: String,
    val carbsG: String,
    val fatG: String,
    val fibreG: String,
    val waterMl: String,
    val suggestionAvailable: Boolean,
    val suggested: TargetsSaveModel?,
    val weightNeutral: Boolean,
)

data class FastingModel(
    val enabled: Boolean,
    val open: Boolean,
    val startAtMillis: Long?,
    val targetHours: Double,
    val nowMillis: Long,
    val elapsedMillis: Long,
    val remainingMillis: Long?,
    val progress: Float,
    val presets: List<Double>,
)

data class MicroRowModel(
    val nutrient: Nutrient,
    val amount: Double?,
    val percentOfDailyValue: Double?,
)

data class MicrosModel(
    val date: LocalDate,
    val rows: List<MicroRowModel>,
    val entryCount: Int,
    val citationTitle: String,
    val citationUrl: String,
)

data class IngredientModel(
    val foodId: String?,
    val name: String,
    val grams: Double,
    val per100g: Map<Nutrient, Double>,
)

data class FoodEditorModel(
    val id: String?,
    val isRecipe: Boolean,
    val name: String,
    val brand: String,
    val barcode: String,
    val servingLabel: String,
    val servingGrams: String,
    val nutrients: Map<Nutrient, String>,
    val ingredients: List<IngredientModel>,
    val servings: String,
    val per100g: Map<Nutrient, Double>,
)

enum class BarcodeState { DISCLOSURE, SCANNING, LOOKING_UP, FOUND, NOT_FOUND, UNAVAILABLE, MANUAL }

data class BarcodeModel(
    val state: BarcodeState,
    val barcode: String?,
    val product: FoodChoiceModel?,
    val disclosureAccepted: Boolean,
)

/** What the editor saves; numbers are parsed and validated by the presenter. */
data class FoodSaveModel(
    val id: String?,
    val isRecipe: Boolean,
    val name: String,
    val brand: String?,
    val barcode: String?,
    val servingLabel: String?,
    val servingGrams: Double?,
    val nutrients: Map<Nutrient, Double>,
    val ingredients: List<IngredientSaveModel>,
    val servings: Int,
)

data class IngredientSaveModel(
    val foodId: String?,
    val name: String,
    val grams: Double,
    val per100g: Map<Nutrient, Double>,
)

data class TargetsSaveModel(
    val kcal: Double?,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
    val fibreG: Double?,
    val waterMl: Double?,
)

/** The meal a sheet is acting on, with the name the section shows. */
data class MealTarget(val id: String?, val slot: MealSlot, val name: String?)
