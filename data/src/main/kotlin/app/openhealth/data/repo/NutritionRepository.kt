package app.openhealth.data.repo

import app.openhealth.data.db.Entry
import app.openhealth.data.db.Food
import app.openhealth.data.db.Meal
import app.openhealth.data.db.OpenHealthDb
import kotlinx.datetime.LocalDate

/** Layer 4 nutrition objects (`pr.md` §6.3). Totals are computed from entries, never stored. */
class NutritionRepository(private val db: OpenHealthDb) {
    private var rollupInvalidator: ((LocalDate) -> Unit)? = null

    fun invalidateRollupsWith(invalidator: ((LocalDate) -> Unit)?) {
        rollupInvalidator = invalidator
    }

    fun upsertFood(food: Food) {
        db.nutritionQueries.insertFood(
            id = food.id,
            name = food.name,
            brand = food.brand,
            barcode = food.barcode,
            sourceKind = food.source_kind,
            sourceRef = food.source_ref,
            energyKcalPer100g = food.energy_kcal_per_100g,
            proteinGPer100g = food.protein_g_per_100g,
            carbsGPer100g = food.carbs_g_per_100g,
            fatGPer100g = food.fat_g_per_100g,
            fibreGPer100g = food.fibre_g_per_100g,
            servingJson = food.serving_json,
            verified = food.verified,
            createdAt = food.created_at,
            updatedAt = food.updated_at,
            revision = food.revision,
        )
    }

    fun foodById(id: String): Food? = db.nutritionQueries.foodById(id).executeAsOneOrNull()

    fun foodByBarcode(barcode: String): Food? =
        db.nutritionQueries.foodByBarcode(barcode).executeAsOneOrNull()

    fun searchFoods(prefix: String, limit: Long): List<Food> =
        db.nutritionQueries.searchFoods(prefix, limit).executeAsList()

    fun upsertMeal(meal: Meal) {
        db.nutritionQueries.insertMeal(
            id = meal.id,
            name = meal.name,
            kind = meal.kind,
            localDate = meal.local_date,
            notes = meal.notes,
            createdAt = meal.created_at,
            updatedAt = meal.updated_at,
            revision = meal.revision,
        )
    }

    fun mealById(id: String): Meal? = db.nutritionQueries.mealById(id).executeAsOneOrNull()

    fun mealsForDay(date: LocalDate): List<Meal> =
        db.nutritionQueries.mealsForDay(date.toString()).executeAsList()

    fun upsertEntry(entry: Entry) {
        db.nutritionQueries.insertEntry(
            id = entry.id,
            mealId = entry.meal_id,
            foodId = entry.food_id,
            localDate = entry.local_date,
            zoneId = entry.zone_id,
            happenedAt = entry.happened_at,
            recordedAt = entry.recorded_at,
            quantityG = entry.quantity_g,
            energyKcal = entry.energy_kcal,
            proteinG = entry.protein_g,
            carbsG = entry.carbs_g,
            fatG = entry.fat_g,
            fibreG = entry.fibre_g,
            sourceKind = entry.source_kind,
            userVerified = entry.user_verified,
            userEdited = entry.user_edited,
            observationId = entry.observation_id,
            rawRecordId = entry.raw_record_id,
            idempotencyKey = entry.idempotency_key,
            device = entry.device,
            appVersion = entry.app_version,
            revision = entry.revision,
        )
        rollupInvalidator?.invoke(LocalDate.parse(entry.local_date))
    }

    fun entryById(id: String): Entry? = db.nutritionQueries.entryById(id).executeAsOneOrNull()

    fun entriesForDay(date: LocalDate): List<Entry> =
        db.nutritionQueries.entriesForDay(date.toString()).executeAsList()

    fun entriesForMeal(mealId: String): List<Entry> =
        db.nutritionQueries.entriesForMeal(mealId).executeAsList()

    fun countEntries(): Long = db.nutritionQueries.countEntries().executeAsOne()
}
