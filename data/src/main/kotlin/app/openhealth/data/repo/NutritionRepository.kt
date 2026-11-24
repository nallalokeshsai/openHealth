package app.openhealth.data.repo

import app.openhealth.core.nutrition.FastWindow
import app.openhealth.core.nutrition.MacroTargets
import app.openhealth.core.nutrition.Nutrient
import app.openhealth.core.nutrition.NutrientTotals
import app.openhealth.core.seam.ClockSeam
import app.openhealth.core.seam.SystemClock
import app.openhealth.data.db.Entry
import app.openhealth.data.db.Fast_window
import app.openhealth.data.db.Food
import app.openhealth.data.db.Meal
import app.openhealth.data.db.Nutrition_target
import app.openhealth.data.db.OpenHealthDb
import app.openhealth.data.db.Recipe_item
import app.openhealth.data.mapping.requireConsistentDay
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/**
 * Layer 4 nutrition (`pr.md` §6.3). A diary entry is the source of truth for its values,
 * so a day's totals are the exact sum of its entries. The repository owns the JSON form
 * of the nutrient maps and the meal/portion metadata; writes invalidate their day through
 * the rollup engine, which rebuilds rather than patches.
 */
class NutritionRepository(
    private val db: OpenHealthDb,
    private val clock: ClockSeam = SystemClock,
) {
    private var rollupInvalidator: ((LocalDate) -> Unit)? = null

    fun invalidateRollupsWith(invalidator: ((LocalDate) -> Unit)?) {
        rollupInvalidator = invalidator
    }

    // ---- Food ---------------------------------------------------------------------

    fun upsertFood(draft: FoodDraft): FoodRecord {
        val existing = db.nutritionQueries.foodById(draft.id).executeAsOneOrNull()
        val now = clock.now().toEpochMilliseconds()
        db.nutritionQueries.insertFood(
            id = draft.id,
            name = draft.name,
            brand = draft.brand,
            barcode = draft.barcode,
            sourceKind = draft.sourceKind,
            sourceRef = draft.sourceRef,
            category = draft.category,
            foodType = draft.foodType.key,
            energyKcalPer100g = draft.per100g[Nutrient.ENERGY],
            proteinGPer100g = draft.per100g[Nutrient.PROTEIN],
            carbsGPer100g = draft.per100g[Nutrient.CARBS],
            fatGPer100g = draft.per100g[Nutrient.FAT],
            fibreGPer100g = draft.per100g[Nutrient.FIBRE],
            nutrientsJson = NutritionJson.encodeNutrients(draft.per100g),
            servingJson = NutritionJson.encodePortions(draft.portions),
            defaultServingG = draft.defaultServingG,
            defaultServingLabel = draft.defaultServingLabel,
            allergensJson = NutritionJson.encodeTags(draft.allergens),
            ingredientsText = draft.ingredientsText,
            analysisJson = NutritionJson.encodeTags(draft.analysisTags),
            verified = draft.verified,
            createdAt = existing?.created_at ?: now,
            updatedAt = now,
            revision = (existing?.revision ?: 0L) + 1L,
        )
        return foodById(draft.id)!!
    }

    fun foodById(id: String): FoodRecord? =
        db.nutritionQueries.foodById(id).executeAsOneOrNull()?.toRecord()

    fun foodByBarcode(barcode: String): FoodRecord? =
        db.nutritionQueries.foodByBarcode(barcode).executeAsOneOrNull()?.toRecord()

    fun searchFoods(prefix: String, limit: Long): List<FoodRecord> =
        db.nutritionQueries.searchFoods(prefix, limit).executeAsList().map { it.toRecord() }

    fun searchFoodsContaining(query: String, limit: Long): List<FoodRecord> =
        db.nutritionQueries.searchFoodsContaining(query, limit).executeAsList().map {
            it.toRecord()
        }

    fun countFoods(): Long = db.nutritionQueries.countFoods().executeAsOne()

    fun allFoods(): List<FoodRecord> =
        db.nutritionQueries.allFoods().executeAsList().map { it.toRecord() }

    fun recentFoods(limit: Long): List<FoodRecord> =
        db.nutritionQueries.recentFoods(limit).executeAsList().map { it.toRecord() }

    fun frequentFoods(limit: Long): List<FoodRecord> =
        db.nutritionQueries.frequentFoods(limit).executeAsList().map { it.toRecord() }

    fun deleteFood(id: String) {
        db.nutritionQueries.deleteFood(id)
    }

    // ---- Meals --------------------------------------------------------------------

    fun upsertMeal(draft: MealDraft): MealRecord {
        val existing = db.nutritionQueries.mealById(draft.id).executeAsOneOrNull()
        val now = clock.now().toEpochMilliseconds()
        db.nutritionQueries.insertMeal(
            id = draft.id,
            name = draft.name,
            kind = draft.kind.key,
            localDate = draft.localDate.toString(),
            zoneId = draft.zoneId?.id,
            happenedAt = draft.happenedAt?.toEpochMilliseconds(),
            recordedAt = draft.recordedAt?.toEpochMilliseconds(),
            sourceId = draft.sourceId,
            idempotencyKey = draft.idempotencyKey,
            notes = draft.notes,
            createdAt = existing?.created_at ?: now,
            updatedAt = now,
            revision = (existing?.revision ?: 0L) + 1L,
        )
        return mealById(draft.id)!!
    }

    fun mealById(id: String): MealRecord? =
        db.nutritionQueries.mealById(id).executeAsOneOrNull()?.toRecord()

    fun mealsForDay(date: LocalDate): List<MealRecord> =
        db.nutritionQueries.mealsForDay(date.toString()).executeAsList().map { it.toRecord() }

    fun mealForDayKind(date: LocalDate, kind: MealKind): MealRecord? =
        db.nutritionQueries.mealForDayKind(date.toString(), kind.key)
            .executeAsOneOrNull()?.toRecord()

    fun allMeals(): List<MealRecord> =
        db.nutritionQueries.allMeals().executeAsList().map { it.toRecord() }

    fun deleteMeal(id: String) {
        val meal = mealById(id) ?: return
        db.nutritionQueries.deleteMeal(id)
        rollupInvalidator?.invoke(meal.localDate)
    }

    // ---- Entries ------------------------------------------------------------------

    fun upsertEntry(draft: EntryDraft) {
        requireConsistentDay(
            draft.localDate.toString(),
            draft.zoneId.id,
            draft.happenedAt.toEpochMilliseconds(),
        )
        val totals = draft.totals
        db.transaction {
            db.nutritionQueries.insertEntry(
                id = draft.id,
                mealId = draft.mealId,
                foodId = draft.foodId,
                localDate = draft.localDate.toString(),
                zoneId = draft.zoneId.id,
                happenedAt = draft.happenedAt.toEpochMilliseconds(),
                recordedAt = draft.recordedAt.toEpochMilliseconds(),
                quantityG = draft.quantityG,
                servingLabel = draft.servingLabel,
                servingG = draft.servingG,
                servings = draft.servings,
                foodName = draft.foodName,
                brand = draft.brand,
                energyKcal = totals[Nutrient.ENERGY],
                proteinG = totals[Nutrient.PROTEIN],
                carbsG = totals[Nutrient.CARBS],
                fatG = totals[Nutrient.FAT],
                fibreG = totals[Nutrient.FIBRE],
                microsJson = NutritionJson.encodeNutrients(totals),
                sourceKind = draft.sourceKind,
                sourceId = draft.sourceId,
                userVerified = draft.verified,
                userEdited = draft.edited,
                observationId = draft.observationId,
                rawRecordId = draft.rawRecordId,
                idempotencyKey = draft.idempotencyKey,
                device = draft.device,
                appVersion = draft.appVersion,
                revision = 1L,
            )
            rollupInvalidator?.invoke(draft.localDate)
        }
    }

    /**
     * Corrects an entry in place. `recorded_at` is not rewritten: the row keeps the moment
     * it was first recorded and gains the edited flag (`pr.md` §6.27).
     */
    fun updateEntry(id: String, draft: EntryDraft): Boolean {
        val existing = db.nutritionQueries.entryById(id).executeAsOneOrNull() ?: return false
        requireConsistentDay(
            draft.localDate.toString(),
            draft.zoneId.id,
            draft.happenedAt.toEpochMilliseconds(),
        )
        val totals = draft.totals
        db.transaction {
            db.nutritionQueries.updateEntry(
                mealId = draft.mealId,
                quantityG = draft.quantityG,
                servingLabel = draft.servingLabel,
                servingG = draft.servingG,
                servings = draft.servings,
                energyKcal = totals[Nutrient.ENERGY],
                proteinG = totals[Nutrient.PROTEIN],
                carbsG = totals[Nutrient.CARBS],
                fatG = totals[Nutrient.FAT],
                fibreG = totals[Nutrient.FIBRE],
                microsJson = NutritionJson.encodeNutrients(totals),
                revision = existing.revision + 1L,
                id = id,
            )
            rollupInvalidator?.invoke(LocalDate.parse(existing.local_date))
            rollupInvalidator?.invoke(draft.localDate)
        }
        return true
    }

    fun entryById(id: String): EntryRecord? =
        db.nutritionQueries.entryById(id).executeAsOneOrNull()?.toRecord()

    fun entriesForDay(date: LocalDate): List<EntryRecord> =
        db.nutritionQueries.entriesForDay(date.toString()).executeAsList().map { it.toRecord() }

    fun entriesForMeal(mealId: String): List<EntryRecord> =
        db.nutritionQueries.entriesForMeal(mealId).executeAsList().map { it.toRecord() }

    fun countEntries(): Long = db.nutritionQueries.countEntries().executeAsOne()

    fun allEntries(): List<EntryRecord> =
        db.nutritionQueries.allEntries().executeAsList().map { it.toRecord() }

    fun entryDays(): List<LocalDate> =
        db.nutritionQueries.entryDays().executeAsList().map(LocalDate::parse)

    // ---- Recipes ------------------------------------------------------------------

    fun recipeItems(recipeFoodId: String): List<RecipeItemRecord> =
        db.nutritionQueries.recipeItems(recipeFoodId).executeAsList().map { it.toRecord() }

    fun replaceRecipeItems(recipeFoodId: String, items: List<RecipeItemDraft>) {
        db.transaction {
            db.nutritionQueries.deleteRecipeItems(recipeFoodId)
            items.forEachIndexed { index, item ->
                db.nutritionQueries.insertRecipeItem(
                    id = item.id,
                    recipeFoodId = recipeFoodId,
                    foodId = item.foodId,
                    position = index.toLong(),
                    grams = item.grams,
                    foodName = item.foodName,
                )
            }
        }
    }

    // ---- Meal templates -----------------------------------------------------------

    fun upsertMealTemplate(template: MealTemplateRecord) {
        db.nutritionQueries.insertMealTemplate(
            id = template.id,
            name = template.name,
            kind = template.kind.key,
            createdAt = template.createdAt,
            updatedAt = template.updatedAt,
            revision = template.revision,
        )
    }

    fun mealTemplateById(id: String): MealTemplateRecord? =
        db.nutritionQueries.mealTemplateById(id).executeAsOneOrNull()?.let { row ->
            MealTemplateRecord(
                id = row.id,
                name = row.name,
                kind = MealKind.fromKey(row.kind) ?: MealKind.CUSTOM,
                createdAt = row.created_at,
                updatedAt = row.updated_at,
                revision = row.revision,
            )
        }

    fun mealTemplates(): List<MealTemplateRecord> =
        db.nutritionQueries.mealTemplates().executeAsList().mapNotNull { row ->
            MealKind.fromKey(row.kind)?.let { kind ->
                MealTemplateRecord(
                    row.id,
                    row.name,
                    kind,
                    row.created_at,
                    row.updated_at,
                    row.revision,
                )
            }
        }

    fun replaceMealTemplateItems(templateId: String, items: List<RecipeItemDraft>) {
        db.transaction {
            db.nutritionQueries.deleteMealTemplateItems(templateId)
            items.forEachIndexed { index, item ->
                db.nutritionQueries.insertMealTemplateItem(
                    id = item.id,
                    templateId = templateId,
                    foodId = item.foodId,
                    position = index.toLong(),
                    grams = item.grams,
                    foodName = item.foodName,
                )
            }
        }
    }

    fun mealTemplateItems(templateId: String): List<MealTemplateItemRecord> =
        db.nutritionQueries.mealTemplateItems(templateId).executeAsList().map { row ->
            MealTemplateItemRecord(
                id = row.id,
                templateId = row.template_id,
                foodId = row.food_id,
                position = row.position,
                grams = row.grams,
                foodName = row.food_name,
            )
        }

    fun deleteMealTemplate(id: String) {
        db.nutritionQueries.deleteMealTemplate(id)
    }

    // ---- Water containers ---------------------------------------------------------

    fun waterContainers(): List<WaterContainerRecord> =
        db.nutritionQueries.waterContainers().executeAsList().map { row ->
            WaterContainerRecord(
                id = row.id,
                label = row.label,
                millilitres = row.millilitres,
                position = row.position,
                isDefault = row.is_default,
            )
        }

    fun upsertWaterContainer(container: WaterContainerRecord) {
        db.nutritionQueries.insertWaterContainer(
            id = container.id,
            label = container.label,
            millilitres = container.millilitres,
            position = container.position,
            isDefault = container.isDefault,
        )
    }

    fun deleteWaterContainer(id: String) {
        db.nutritionQueries.deleteWaterContainer(id)
    }

    fun countWaterContainers(): Long = db.nutritionQueries.countWaterContainers().executeAsOne()

    // ---- Targets ------------------------------------------------------------------

    fun targetFor(date: LocalDate): NutritionTargetRecord? =
        db.nutritionQueries.targetForDate(date.toString()).executeAsOneOrNull()?.toRecord()

    fun targetOn(date: LocalDate): NutritionTargetRecord? =
        db.nutritionQueries.targetOnDate(date.toString()).executeAsOneOrNull()?.toRecord()

    fun saveTarget(record: NutritionTargetRecord) {
        db.nutritionQueries.insertNutritionTarget(
            id = record.id,
            effectiveFrom = record.effectiveFrom.toString(),
            kcal = record.targets.kcal,
            proteinG = record.targets.proteinG,
            carbsG = record.targets.carbsG,
            fatG = record.targets.fatG,
            fibreG = record.targets.fibreG,
            waterMl = record.targets.waterMl,
            createdAt = record.createdAt,
            updatedAt = record.updatedAt,
            revision = record.revision,
        )
    }

    fun allTargets(): List<NutritionTargetRecord> =
        db.nutritionQueries.allTargets().executeAsList().map { it.toRecord() }

    // ---- Fasting ------------------------------------------------------------------

    fun saveFast(record: FastRecord) {
        db.nutritionQueries.insertFast(
            id = record.id,
            startAt = record.window.startEpochMillis,
            targetHours = record.window.targetHours,
            endedAt = record.window.endEpochMillis,
            localDate = record.localDate.toString(),
            zoneId = record.zoneId,
            recordedAt = record.recordedAt.toEpochMilliseconds(),
            createdAt = record.createdAt,
            updatedAt = record.updatedAt,
            revision = record.revision,
        )
    }

    fun fastById(id: String): FastRecord? =
        db.nutritionQueries.fastById(id).executeAsOneOrNull()?.toRecord()

    fun openFast(): FastRecord? = db.nutritionQueries.openFast().executeAsOneOrNull()?.toRecord()

    fun recentFasts(limit: Long): List<FastRecord> =
        db.nutritionQueries.recentFasts(limit).executeAsList().map { it.toRecord() }

    fun allFasts(): List<FastRecord> =
        db.nutritionQueries.allFasts().executeAsList().map { it.toRecord() }

    fun deleteFast(id: String) {
        db.nutritionQueries.deleteFast(id)
    }

    // ---- Mapping ------------------------------------------------------------------

    private fun Food.toRecord(): FoodRecord {
        val stored = NutritionJson.decodeNutrients(nutrients_json)
        val per100g = if (stored.isNotEmpty()) {
            stored
        } else {
            legacyNutrients()
        }
        return FoodRecord(
            id = id,
            name = name,
            brand = brand,
            barcode = barcode,
            sourceKind = source_kind,
            sourceRef = source_ref,
            category = category,
            foodType = FoodType.fromKey(food_type),
            per100g = per100g,
            portions = NutritionJson.decodePortions(serving_json),
            defaultServingG = default_serving_g,
            defaultServingLabel = default_serving_label,
            allergens = NutritionJson.decodeTags(allergens_json),
            ingredientsText = ingredients_text,
            analysisTags = NutritionJson.decodeTags(analysis_json),
            verified = verified,
            createdAt = created_at,
            updatedAt = updated_at,
            revision = revision,
        )
    }

    private fun Food.legacyNutrients(): Map<Nutrient, Double> = buildMap {
        energy_kcal_per_100g?.let { put(Nutrient.ENERGY, it) }
        protein_g_per_100g?.let { put(Nutrient.PROTEIN, it) }
        carbs_g_per_100g?.let { put(Nutrient.CARBS, it) }
        fat_g_per_100g?.let { put(Nutrient.FAT, it) }
        fibre_g_per_100g?.let { put(Nutrient.FIBRE, it) }
    }

    private fun Meal.toRecord(): MealRecord = MealRecord(
        id = id,
        name = name,
        kind = MealKind.fromKey(kind) ?: MealKind.CUSTOM,
        localDate = LocalDate.parse(local_date),
        zoneId = zone_id,
        happenedAt = happened_at?.let(Instant::fromEpochMilliseconds),
        recordedAt = recorded_at?.let(Instant::fromEpochMilliseconds),
        sourceId = source_id,
        notes = notes,
        createdAt = created_at,
        updatedAt = updated_at,
        revision = revision,
    )

    private fun Entry.toRecord(): EntryRecord {
        val stored = NutritionJson.decodeNutrients(micros_json)
        val totals = if (stored.isNotEmpty()) stored else legacyTotals()
        return EntryRecord(
            id = id,
            mealId = meal_id,
            foodId = food_id,
            localDate = LocalDate.parse(local_date),
            zoneId = zone_id,
            happenedAt = Instant.fromEpochMilliseconds(happened_at),
            recordedAt = Instant.fromEpochMilliseconds(recorded_at),
            quantityG = quantity_g,
            servingLabel = serving_label,
            servingG = serving_g,
            servings = servings,
            foodName = food_name,
            brand = brand,
            totals = NutrientTotals(totals),
            sourceKind = source_kind,
            sourceId = source_id,
            verified = user_verified,
            edited = user_edited,
            observationId = observation_id,
            rawRecordId = raw_record_id,
            idempotencyKey = idempotency_key,
            device = device,
            appVersion = app_version,
            revision = revision,
        )
    }

    private fun Entry.legacyTotals(): Map<Nutrient, Double> = buildMap {
        energy_kcal?.let { put(Nutrient.ENERGY, it) }
        protein_g?.let { put(Nutrient.PROTEIN, it) }
        carbs_g?.let { put(Nutrient.CARBS, it) }
        fat_g?.let { put(Nutrient.FAT, it) }
        fibre_g?.let { put(Nutrient.FIBRE, it) }
    }

    private fun Recipe_item.toRecord(): RecipeItemRecord = RecipeItemRecord(
        id = id,
        recipeFoodId = recipe_food_id,
        foodId = food_id,
        position = position,
        grams = grams,
        foodName = food_name,
    )

    private fun Nutrition_target.toRecord(): NutritionTargetRecord = NutritionTargetRecord(
        id = id,
        effectiveFrom = LocalDate.parse(effective_from),
        targets = MacroTargets(
            kcal = kcal,
            proteinG = protein_g,
            carbsG = carbs_g,
            fatG = fat_g,
            fibreG = fibre_g,
            waterMl = water_ml,
        ),
        createdAt = created_at,
        updatedAt = updated_at,
        revision = revision,
    )

    private fun Fast_window.toRecord(): FastRecord = FastRecord(
        id = id,
        localDate = LocalDate.parse(local_date),
        zoneId = zone_id,
        window = FastWindow(
            startEpochMillis = start_at,
            targetHours = target_hours,
            endEpochMillis = ended_at,
        ),
        recordedAt = Instant.fromEpochMilliseconds(recorded_at),
        createdAt = created_at,
        updatedAt = updated_at,
        revision = revision,
    )

    /** The starting containers of a fresh diary (`pr.md` §6.3). */
    fun seedDefaultContainers() {
        if (countWaterContainers() > 0) return
        listOf(
            Triple("water-glass", "Glass", GLASS_ML),
            Triple("water-mug", "Mug", MUG_ML),
            Triple("water-bottle", "Bottle", BOTTLE_ML),
            Triple("water-large", "Large bottle", LARGE_BOTTLE_ML),
        ).forEachIndexed { index, (id, label, ml) ->
            upsertWaterContainer(
                WaterContainerRecord(
                    id = id,
                    label = label,
                    millilitres = ml,
                    position = index.toLong(),
                    isDefault = index == 0,
                ),
            )
        }
    }
}

/** The four starting water containers (`pr.md` §6.3). */
private const val GLASS_ML = 250.0
private const val MUG_ML = 350.0
private const val BOTTLE_ML = 500.0
private const val LARGE_BOTTLE_ML = 750.0
