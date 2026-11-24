package app.openhealth.data

import app.openhealth.core.nutrition.FastWindow
import app.openhealth.core.nutrition.FoodPortion
import app.openhealth.core.nutrition.MacroTargets
import app.openhealth.core.nutrition.Nutrient
import app.openhealth.core.nutrition.PortionMath
import app.openhealth.data.LocalStore
import app.openhealth.data.repo.EntryDraft
import app.openhealth.data.repo.FastRecord
import app.openhealth.data.repo.FoodDraft
import app.openhealth.data.repo.FoodType
import app.openhealth.data.repo.MealDraft
import app.openhealth.data.repo.MealKind
import app.openhealth.data.repo.NutritionJson
import app.openhealth.data.repo.NutritionRepository
import app.openhealth.data.repo.NutritionTargetRecord
import app.openhealth.data.repo.RecipeItemDraft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

class NutritionRepositoryTest {
    private val day = LocalDate(2026, 11, 24)
    private val yesterday = day.plus(DatePeriod(days = -1))

    private fun seeded(): LocalStore {
        val opened = inMemoryStore()
        val store = LocalStore(opened.driver, opened.db)
        store.nutrition.upsertFood(
            FoodDraft(
                id = "food-oats",
                name = "Oats",
                sourceKind = "usda",
                foodType = FoodType.BUNDLED,
                per100g = mapOf(
                    Nutrient.ENERGY to 389.0,
                    Nutrient.PROTEIN to 16.9,
                    Nutrient.FIBRE to 10.6,
                    Nutrient.SODIUM to 2.0,
                    Nutrient.VITAMIN_C to 0.5,
                ),
                portions = listOf(FoodPortion("1 cup", 80.0)),
            ),
        )
        store.nutrition.upsertMeal(
            MealDraft(
                id = "meal-1",
                name = "breakfast",
                kind = MealKind.BREAKFAST,
                localDate = day,
                zoneId = TEST_ZONE,
                happenedAt = dayStart(day).plus(8.hours),
                recordedAt = dayStart(day).plus(8.hours),
                sourceId = "manual",
                idempotencyKey = "meal-1",
            ),
        )
        return store
    }

    @Test
    fun aDayTotalIsTheExactSumOfItsEntries() {
        val store = seeded()
        val repository = NutritionRepository(store.db)
        val first = mapOf(
            Nutrient.ENERGY to 123.456789,
            Nutrient.PROTEIN to 1.0 / 3.0,
            Nutrient.SODIUM to 2.5,
            Nutrient.VITAMIN_C to 0.123456789,
        )
        val second = mapOf(
            Nutrient.ENERGY to 0.1,
            Nutrient.PROTEIN to 0.2,
            Nutrient.SODIUM to 0.3,
        )
        listOf(first, second).forEachIndexed { index, totals ->
            store.nutrition.upsertEntry(
                EntryDraft(
                    id = "e-$index",
                    mealId = "meal-1",
                    foodId = "food-oats",
                    localDate = day,
                    zoneId = TEST_ZONE,
                    happenedAt = dayStart(day).plus(8.hours).plus(index.seconds),
                    recordedAt = dayStart(day).plus(8.hours).plus(index.seconds),
                    quantityG = 100.0,
                    foodName = "Oats",
                    totals = totals,
                    sourceKind = "usda",
                    sourceId = "manual",
                    idempotencyKey = "entry-$index",
                ),
            )
        }
        val entries = repository.entriesForDay(day)
        assertEquals(2, entries.size)
        val expected = entries.map { it.totals.values }
            .reduce { acc, values ->
                (acc.keys + values.keys).associateWith { key ->
                    (acc[key] ?: 0.0) + (values[key] ?: 0.0)
                }
            }
        val energy = store.rollups.forDayAndType(
            day,
            app.openhealth.core.observation.ObservationType.NUTRITION_ENERGY,
        )!!
        assertEquals(expected.getValue(Nutrient.ENERGY), energy.value.value, 0.0)
        val protein = store.rollups.forDayAndType(
            day,
            app.openhealth.core.observation.ObservationType.NUTRITION_PROTEIN,
        )!!
        assertEquals(expected.getValue(Nutrient.PROTEIN), protein.value.value, 0.0)
        // The micronutrient totals ride on the day's rollup detail, rebuilt with it.
        val detail = energy.detail.orEmpty()
        assertTrue(detail.contains("sodium"), detail)
        assertTrue(detail.contains("vitamin_c"), detail)
        store.close()
    }

    @Test
    fun aBackfilledEntryKeepsRecordedAtAndRebuildsItsOwnDay() {
        val store = seeded()
        val repository = NutritionRepository(store.db)
        val recorded = dayStart(day).plus(20.hours)
        store.nutrition.upsertEntry(
            EntryDraft(
                id = "backfilled",
                mealId = null,
                foodId = "food-oats",
                localDate = yesterday,
                zoneId = TEST_ZONE,
                happenedAt = dayStart(yesterday).plus(8.hours),
                recordedAt = recorded,
                quantityG = 50.0,
                foodName = "Oats",
                totals = PortionMath.forGrams(
                    mapOf(Nutrient.ENERGY to 389.0),
                    50.0,
                ),
                sourceKind = "usda",
                sourceId = "manual",
                idempotencyKey = "backfilled",
            ),
        )
        store.engine.rebuildAll()
        assertNotNull(
            store.rollups.forDayAndType(
                yesterday,
                app.openhealth.core.observation.ObservationType.NUTRITION_ENERGY,
            ),
        )
        assertNull(
            store.rollups.forDayAndType(
                day,
                app.openhealth.core.observation.ObservationType.NUTRITION_ENERGY,
            ),
        )
        assertEquals(recorded, repository.entryById("backfilled")!!.recordedAt)
        store.close()
    }

    @Test
    fun aMealCarriesItsOwnProvenance() {
        val store = seeded()
        val meal = store.nutrition.mealById("meal-1")!!
        assertEquals(TEST_ZONE.id, meal.zoneId)
        assertEquals(dayStart(day).plus(8.hours), meal.happenedAt)
        assertEquals("manual", meal.sourceId)
        assertEquals(day, meal.localDate)
        store.close()
    }

    @Test
    fun targetsKeepTheirHistory() {
        val store = seeded()
        val repository = NutritionRepository(store.db)
        repository.saveTarget(
            NutritionTargetRecord(
                id = "t-1",
                effectiveFrom = yesterday,
                targets = MacroTargets(kcal = 2_400.0, proteinG = 120.0),
                createdAt = 1L,
                updatedAt = 1L,
                revision = 1L,
            ),
        )
        repository.saveTarget(
            NutritionTargetRecord(
                id = "t-2",
                effectiveFrom = day,
                targets = MacroTargets(kcal = 2_200.0),
                createdAt = 2L,
                updatedAt = 2L,
                revision = 1L,
            ),
        )
        assertEquals(2_400.0, repository.targetFor(yesterday)!!.targets.kcal)
        assertEquals(2_200.0, repository.targetFor(day)!!.targets.kcal)
        assertEquals(2_200.0, repository.targetFor(day.plus(DatePeriod(days = 1)))!!.targets.kcal)
        assertNull(repository.targetFor(yesterday.plus(DatePeriod(days = -1))))
        store.close()
    }

    @Test
    fun aFastIsOpenUntilItEndsAndOnlyOneIsOpen() {
        val store = seeded()
        val repository = NutritionRepository(store.db)
        val now = dayStart(day).plus(20.hours)
        repository.saveFast(
            FastRecord(
                id = "fast-1",
                localDate = day,
                zoneId = TEST_ZONE.id,
                window = FastWindow(now.toEpochMilliseconds(), 16.0),
                recordedAt = now,
                createdAt = now.toEpochMilliseconds(),
                updatedAt = now.toEpochMilliseconds(),
                revision = 1L,
            ),
        )
        assertEquals("fast-1", repository.openFast()!!.id)
        repository.saveFast(
            repository.openFast()!!.copy(
                window = repository.openFast()!!.window.copy(
                    endEpochMillis = now.toEpochMilliseconds() + 36_000_000,
                ),
            ),
        )
        assertNull(repository.openFast())
        assertEquals(1, repository.recentFasts(5).size)
        assertTrue(store.timeline.page(10).any { it.kind == "fast" })
        store.close()
    }

    @Test
    fun theStartingWaterContainersAreSeededOnce() {
        val store = seeded()
        store.nutrition.seedDefaultContainers()
        assertEquals(4, store.nutrition.waterContainers().size)
        store.nutrition.seedDefaultContainers()
        assertEquals(4, store.nutrition.waterContainers().size)
        store.close()
    }

    @Test
    fun aRecipeRemembersItsIngredients() {
        val store = seeded()
        store.nutrition.upsertFood(
            FoodDraft(
                id = "recipe-1",
                name = "Oat bowl",
                sourceKind = "recipe",
                foodType = FoodType.RECIPE,
                per100g = mapOf(Nutrient.ENERGY to 250.0),
                defaultServingG = 200.0,
                defaultServingLabel = "1 serving",
            ),
        )
        store.nutrition.replaceRecipeItems(
            "recipe-1",
            listOf(RecipeItemDraft("i-1", "food-oats", 80.0, "Oats")),
        )
        val items = store.nutrition.recipeItems("recipe-1")
        assertEquals(1, items.size)
        assertEquals("food-oats", items.single().foodId)
        assertEquals(80.0, items.single().grams)
        // Replacing the list does not accumulate old rows.
        store.nutrition.replaceRecipeItems(
            "recipe-1",
            listOf(
                RecipeItemDraft("i-2", "food-oats", 40.0, "Oats"),
                RecipeItemDraft("i-3", "food-oats", 60.0, "Oats"),
            ),
        )
        assertEquals(2, store.nutrition.recipeItems("recipe-1").size)
        store.close()
    }

    @Test
    fun nutrientsSurviveTheJsonRoundTrip() {
        val values = Nutrient.entries.associateWith { it.ordinal.toDouble() + 0.5 }
        val text = NutritionJson.encodeNutrients(values)
        assertEquals(values, NutritionJson.decodeNutrients(text))
        assertEquals(emptyMap(), NutritionJson.decodeNutrients(null))
        assertEquals(emptyMap(), NutritionJson.decodeNutrients("not json"))
    }

    @Test
    fun aLegacyFoodWithoutTheNewJsonStillReadsItsMacros() {
        val store = seeded()
        // A B2-era row: the five macro columns only, no nutrients_json.
        store.driver.execute(
            null,
            "INSERT INTO food(id, name, source_kind, energy_kcal_per_100g, " +
                "protein_g_per_100g, carbs_g_per_100g, fat_g_per_100g, " +
                "fibre_g_per_100g, verified, created_at, updated_at, revision) VALUES " +
                "('legacy','Legacy','manual',100,1,2,3,4,0,0,0,1)",
            0,
        )
        val food = store.nutrition.foodById("legacy")!!
        assertEquals(100.0, food.per100g[Nutrient.ENERGY])
        assertEquals(4.0, food.per100g[Nutrient.FIBRE])
        store.close()
    }
}
