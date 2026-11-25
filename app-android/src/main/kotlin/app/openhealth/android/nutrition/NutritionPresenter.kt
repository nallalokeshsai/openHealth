@file:Suppress(
    "LargeClass",
    "TooManyFunctions",
    "LongParameterList",
    "LongMethod",
    "CyclomaticComplexMethod",
    "ReturnCount",
    "MagicNumber",
)

package app.openhealth.android.nutrition

import app.openhealth.android.preferences.Preferences
import app.openhealth.core.citation.CitationKey
import app.openhealth.core.citation.Citations
import app.openhealth.core.formula.ActivityLevel
import app.openhealth.core.formula.Bmr
import app.openhealth.core.formula.EnergySafety
import app.openhealth.core.formula.FormulaOutcome
import app.openhealth.core.formula.GoalMode
import app.openhealth.core.formula.GoalRatePolicy
import app.openhealth.core.formula.SexAtBirth
import app.openhealth.core.formula.Tdee
import app.openhealth.core.ids.EntryId
import app.openhealth.core.ids.IdGenerator
import app.openhealth.core.ids.ObservationId
import app.openhealth.core.ids.SourceId
import app.openhealth.core.ids.TombstoneId
import app.openhealth.core.nutrition.BundledFoodIndex
import app.openhealth.core.nutrition.FastWindow
import app.openhealth.core.nutrition.IndexedFood
import app.openhealth.core.nutrition.MacroTargets
import app.openhealth.core.nutrition.Nutrient
import app.openhealth.core.nutrition.NutrientTotals
import app.openhealth.core.nutrition.PortionMath
import app.openhealth.core.nutrition.RecipeMath
import app.openhealth.core.nutrition.ReferenceIntakes
import app.openhealth.core.nutrition.TargetSuggestions
import app.openhealth.core.observation.Observation
import app.openhealth.core.observation.ObservationContext
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.observation.Provenance
import app.openhealth.core.preferences.Allergen
import app.openhealth.core.preferences.DietaryPattern
import app.openhealth.core.preferences.DisplayPolicy
import app.openhealth.core.seam.ClockSeam
import app.openhealth.core.time.TimeProvenance
import app.openhealth.core.units.Measure
import app.openhealth.core.units.UcumUnit
import app.openhealth.data.LocalStore
import app.openhealth.data.repo.EntryDraft
import app.openhealth.data.repo.EntryRecord
import app.openhealth.data.repo.FastRecord
import app.openhealth.data.repo.FoodDraft
import app.openhealth.data.repo.FoodRecord
import app.openhealth.data.repo.FoodType
import app.openhealth.data.repo.MealDraft
import app.openhealth.data.repo.MealKind
import app.openhealth.data.repo.MealTemplateRecord
import app.openhealth.data.repo.NutritionTargetRecord
import app.openhealth.data.repo.RecipeItemDraft
import app.openhealth.data.repo.SettingKeys
import app.openhealth.data.repo.WaterContainerRecord
import app.openhealth.integrations.food.OffLookup
import app.openhealth.integrations.food.OffProduct
import app.openhealth.integrations.food.OpenFoodFactsClient
import app.openhealth.ui.screens.nutrition.BarcodeModel
import app.openhealth.ui.screens.nutrition.BarcodeState
import app.openhealth.ui.screens.nutrition.DayEntryModel
import app.openhealth.ui.screens.nutrition.DietStatus
import app.openhealth.ui.screens.nutrition.EnergyCardModel
import app.openhealth.ui.screens.nutrition.EntryDetailModel
import app.openhealth.ui.screens.nutrition.FastCardModel
import app.openhealth.ui.screens.nutrition.FastingModel
import app.openhealth.ui.screens.nutrition.FoodChoiceModel
import app.openhealth.ui.screens.nutrition.FoodEditorModel
import app.openhealth.ui.screens.nutrition.FoodRowModel
import app.openhealth.ui.screens.nutrition.FoodSaveModel
import app.openhealth.ui.screens.nutrition.FoodSearchModel
import app.openhealth.ui.screens.nutrition.IngredientModel
import app.openhealth.ui.screens.nutrition.MacroBarModel
import app.openhealth.ui.screens.nutrition.MacroKey
import app.openhealth.ui.screens.nutrition.MealChoiceModel
import app.openhealth.ui.screens.nutrition.MealSectionModel
import app.openhealth.ui.screens.nutrition.MealSlot
import app.openhealth.ui.screens.nutrition.MealTemplateRowModel
import app.openhealth.ui.screens.nutrition.MicroRowModel
import app.openhealth.ui.screens.nutrition.MicrosModel
import app.openhealth.ui.screens.nutrition.NutritionDay
import app.openhealth.ui.screens.nutrition.NutritionModel
import app.openhealth.ui.screens.nutrition.PortionModel
import app.openhealth.ui.screens.nutrition.TargetsModel
import app.openhealth.ui.screens.nutrition.TargetsSaveModel
import app.openhealth.ui.screens.nutrition.WaterCardModel
import app.openhealth.ui.screens.nutrition.WaterContainerModel
import app.openhealth.ui.screens.nutrition.WaterSheetModel
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * The nutrition surfaces' view model (`pr.md` §6.3). It reads the layer-4 diary and the
 * layer-3 water rollup, and it writes through the repositories so the day is rebuilt.
 * The screens never touch storage; every number they show comes from here.
 */
class NutritionPresenter(
    private val store: LocalStore,
    private val clock: ClockSeam,
    private val zone: TimeZone,
    private val ids: IdGenerator,
    private val manualSource: SourceId,
    private val appVersion: String,
    private val foodIndex: BundledFoodIndex,
    private val off: OpenFoodFactsClient,
) {
    // ---- Reading the diary --------------------------------------------------------

    fun root(date: LocalDate, preferences: Preferences): NutritionModel {
        val policy = preferences.displayPolicy
        val today = clock.now().toLocalDateTime(zone).date
        val safeDate = if (date > today) today else date
        store.nutrition.seedDefaultContainers()
        val entries = store.nutrition.entriesForDay(safeDate)
        val meals = store.nutrition.mealsForDay(safeDate)
        val targetPair = targetFor(safeDate)
        val totals = NutrientTotals.sumInOrder(entries.map { it.totals.values })
        val sections = sections(safeDate, entries, meals)
        return NutritionModel(
            selectedDate = safeDate,
            today = today,
            week = week(today, safeDate),
            energy = if (policy.shows(app.openhealth.core.preferences.SensitiveMetric.CALORIES)) {
                energyCard(totals, targetPair)
            } else {
                null
            },
            meals = sections,
            water = waterCard(safeDate, targetPair),
            fast = fastCard(),
            targetsSet = targetPair != null,
            weightNeutral = policy.hidesSomething,
            demo = preferences.demo,
            canCopyDay = safeDate != today,
        )
    }

    private fun week(today: LocalDate, selected: LocalDate): List<NutritionDay> =
        (6 downTo 0).map { offset ->
            val date = today.minus(DatePeriod(days = offset))
            NutritionDay(
                date = date,
                isToday = date == today,
                isSelected = date == selected,
                hasEntries = store.nutrition.entriesForDay(date).isNotEmpty(),
            )
        }

    private fun sections(
        date: LocalDate,
        entries: List<EntryRecord>,
        meals: List<app.openhealth.data.repo.MealRecord>,
    ): List<MealSectionModel> {
        val byMeal = entries.groupBy { it.mealId }
        val byKind = meals.groupBy { it.kind.key }
        val result = mutableListOf<MealSectionModel>()
        val usedMealIds = mutableSetOf<String>()
        MealSlot.daySections.forEach { slot ->
            val matches = byKind[slot.key].orEmpty()
            // Two meals of one kind (a double-tapped log can race the lazy create) are
            // merged into the one section, so no row is hidden from the diary.
            val rows = matches.flatMap { byMeal[it.id].orEmpty() }
                .sortedBy { it.happenedAt }
            usedMealIds += matches.map { it.id }
            result += MealSectionModel(
                mealId = matches.firstOrNull()?.id,
                slot = slot,
                name = null,
                entries = rows.map(::dayEntry),
                totalKcal = rows.sumOf { it.totals.of(Nutrient.ENERGY) ?: 0.0 }
                    .takeIf { rows.isNotEmpty() },
                isToday = date == clock.now().toLocalDateTime(zone).date,
            )
        }
        meals.filter { it.kind == MealKind.CUSTOM && it.id !in usedMealIds }
            .forEach { meal ->
                val rows = byMeal[meal.id].orEmpty()
                if (rows.isEmpty() && meal.name.isBlank()) return@forEach
                result += MealSectionModel(
                    mealId = meal.id,
                    slot = MealSlot.CUSTOM,
                    name = meal.name,
                    entries = rows.map(::dayEntry),
                    totalKcal = rows.sumOf { it.totals.of(Nutrient.ENERGY) ?: 0.0 }
                        .takeIf { rows.isNotEmpty() },
                    isToday = date == clock.now().toLocalDateTime(zone).date,
                )
            }
        byMeal[null]?.takeIf { it.isNotEmpty() }?.let { orphans ->
            result += MealSectionModel(
                mealId = null,
                slot = MealSlot.CUSTOM,
                name = null,
                entries = orphans.map(::dayEntry),
                totalKcal = orphans.sumOf { it.totals.of(Nutrient.ENERGY) ?: 0.0 },
                isToday = date == clock.now().toLocalDateTime(zone).date,
            )
        }
        return result
    }

    private fun dayEntry(entry: EntryRecord): DayEntryModel = DayEntryModel(
        id = entry.id,
        name = entry.foodName ?: entry.brand ?: plainNameOf(entry),
        grams = entry.quantityG,
        servings = entry.servings,
        servingLabel = entry.servingLabel,
        kcal = entry.totals.of(Nutrient.ENERGY),
        sourceKey = entry.sourceKind,
        verified = entry.verified,
        edited = entry.edited,
    )

    private fun plainNameOf(entry: EntryRecord): String =
        entry.foodId?.let { store.nutrition.foodById(it)?.name } ?: ""

    private fun energyCard(totals: NutrientTotals, target: MacroTargets?): EnergyCardModel {
        val targetKcal = target?.kcal
        val bars = listOf(
            MacroKey.PROTEIN to Nutrient.PROTEIN,
            MacroKey.CARBS to Nutrient.CARBS,
            MacroKey.FAT to Nutrient.FAT,
            MacroKey.FIBRE to Nutrient.FIBRE,
        ).map { (key, nutrient) ->
            val grams = totals.of(nutrient) ?: 0.0
            val targetGrams = when (key) {
                MacroKey.PROTEIN -> target?.proteinG
                MacroKey.CARBS -> target?.carbsG
                MacroKey.FAT -> target?.fatG
                MacroKey.FIBRE -> target?.fibreG
            }
            MacroBarModel(
                key = key,
                grams = grams,
                targetGrams = targetGrams,
                fraction = targetGrams?.takeIf { it > 0.0 }
                    ?.let { (grams / it).coerceIn(0.0, 1.0).toFloat() },
            )
        }
        val kcalIn = totals.of(Nutrient.ENERGY) ?: 0.0
        return EnergyCardModel(
            kcalIn = kcalIn,
            kcalTarget = targetKcal,
            remainingKcal = targetKcal?.minus(kcalIn),
            bars = bars,
            goalLine = null,
        )
    }

    private fun waterCard(date: LocalDate, target: MacroTargets?): WaterCardModel {
        val containers = containers()
        val lastId = store.settings.get(SettingKeys.NUTRITION_LAST_CONTAINER)
        val last = containers.firstOrNull { it.id == lastId } ?: containers.firstOrNull()
        val total = waterTotal(date)
        val targetMl = target?.waterMl
        return WaterCardModel(
            totalMl = total,
            targetMl = targetMl,
            fraction = targetMl?.takeIf { it > 0.0 }
                ?.let { (total / it).coerceIn(0.0, 1.0).toFloat() },
            containers = containers,
            lastContainer = last,
        )
    }

    private fun waterTotal(date: LocalDate): Double =
        store.rollups.forDayAndType(date, ObservationType.WATER)?.value?.value ?: 0.0

    private fun fastCard(): FastCardModel {
        // The row is always present so the optional timer can be turned on; the setting
        // only decides whether the card shows a running/starting state (`pr.md` §6.3).
        if (store.settings.get(SettingKeys.NUTRITION_FASTING) != "true") {
            val now = clock.now().toEpochMilliseconds()
            return FastCardModel(
                open = false,
                startAtMillis = null,
                nowMillis = now,
                elapsedMillis = 0,
                remainingMillis = null,
                targetHours = store.settings.get(SettingKeys.NUTRITION_FASTING_HOURS)
                    ?.toDoubleOrNull() ?: 16.0,
                progress = 0f,
            )
        }
        val now = clock.now().toEpochMilliseconds()
        val open = store.nutrition.openFast()
        val start = open?.window?.startEpochMillis
        val targetHours = open?.window?.targetHours
            ?: store.settings.get(SettingKeys.NUTRITION_FASTING_HOURS)?.toDoubleOrNull()
            ?: 16.0
        val window = start?.let { FastWindow(it, targetHours) }
        return FastCardModel(
            open = window?.isOpen == true,
            startAtMillis = start,
            nowMillis = now,
            elapsedMillis = window?.elapsedMillis(now) ?: 0L,
            remainingMillis = window?.remainingHours(now)?.let {
                (it * FastWindow.MILLIS_PER_HOUR).toLong()
            },
            targetHours = targetHours,
            progress = window?.progress(now) ?: 0f,
        )
    }

    fun targets(date: LocalDate, preferences: Preferences): TargetsModel {
        val existing = targetFor(date)
        val suggestion = suggestedTargets()
        return TargetsModel(
            date = date,
            kcal = existing?.kcal?.targetText(0) ?: "",
            proteinG = existing?.proteinG?.targetText(1) ?: "",
            carbsG = existing?.carbsG?.targetText(1) ?: "",
            fatG = existing?.fatG?.targetText(1) ?: "",
            fibreG = existing?.fibreG?.targetText(1) ?: "",
            waterMl = existing?.waterMl?.targetText(0) ?: "",
            suggestionAvailable = suggestion != null,
            suggested = suggestion,
            weightNeutral = preferences.displayPolicy.hidesSomething,
        )
    }

    private fun suggestedTargets(): TargetsSaveModel? {
        val profile = store.profile.active() ?: return null
        val sex = profile.sex_at_birth?.let { runCatching { SexAtBirth.valueOf(it) }.getOrNull() }
            ?: return null
        val height = profile.height_cm ?: return null
        val dob = profile.date_of_birth?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return null
        val weight = profile.weight_kg ?: return null
        val today = clock.now().toLocalDateTime(zone).date
        val age = today.year - dob.year - if (
            today.month.ordinal < dob.month.ordinal ||
            (today.month == dob.month && today.day < dob.day)
        ) {
            1
        } else {
            0
        }
        val level = profile.activity_level
            ?.let { runCatching { ActivityLevel.valueOf(it) }.getOrNull() }
            ?: ActivityLevel.SEDENTARY
        val bmr = Bmr.mifflinStJeor(sex, weight, height, age) as? FormulaOutcome.Computed
            ?: return null
        val tdee = Tdee.fromBmr(bmr.value, level) as? FormulaOutcome.Computed ?: return null
        val goal = when (profile.goal) {
            "lose_fat" -> GoalMode.LOSE
            "gain_muscle" -> GoalMode.GAIN
            else -> GoalMode.MAINTAIN
        }
        val requested = store.settings.get(SettingKeys.GOAL_RATE_KG_PER_WEEK)
            ?.toDoubleOrNull() ?: 0.25
        val signedRate = when (goal) {
            GoalMode.LOSE -> -kotlin.math.abs(requested)
            GoalMode.GAIN -> kotlin.math.abs(requested)
            GoalMode.MAINTAIN -> 0.0
        }
        val rate = GoalRatePolicy.clamp(goal, signedRate, weight)
            as? FormulaOutcome.Computed
        val kcal = if (rate == null) {
            tdee.value
        } else {
            (
                TargetSuggestions.energyForRate(tdee.value, rate.value.appliedKgPerWeek)
                    as? FormulaOutcome.Computed
                )?.value ?: tdee.value
        }
        val floor = (EnergySafety.floors(sex, bmr.value) as? FormulaOutcome.Computed)
            ?.value?.minimumIntakeKcal
        val safeKcal = if (floor != null) maxOf(kcal, floor) else kcal
        val targets = TargetSuggestions.suggest(sex, weight, safeKcal)
            as? FormulaOutcome.Computed
            ?: return null
        return TargetsSaveModel(
            kcal = targets.value.kcal,
            proteinG = targets.value.proteinG?.roundTo(1),
            carbsG = targets.value.carbsG?.roundTo(1),
            fatG = targets.value.fatG?.roundTo(1),
            fibreG = targets.value.fibreG?.roundTo(1),
            waterMl = targets.value.waterMl?.roundTo(0),
        )
    }

    fun micros(date: LocalDate): MicrosModel {
        val entries = store.nutrition.entriesForDay(date)
        val totals = NutrientTotals.sumInOrder(entries.map { it.totals.values })
        val citation = Citations.of(CitationKey.FDA_DAILY_VALUES_2020)
        return MicrosModel(
            date = date,
            rows = Nutrient.microPanel.map { nutrient ->
                val amount = totals.of(nutrient)
                MicroRowModel(
                    nutrient = nutrient,
                    amount = amount,
                    percentOfDailyValue = ReferenceIntakes.of(nutrient)
                        ?.let { reference -> amount?.let(reference::share) },
                )
            },
            entryCount = entries.size,
            citationTitle = citation.title,
            citationUrl = citation.url,
        )
    }

    fun waterSheet(date: LocalDate): WaterSheetModel = WaterSheetModel(
        date = date,
        containers = containers(),
        selectedId = containers().firstOrNull()?.id,
        totalMl = waterTotal(date),
        targetMl = targetFor(date)?.waterMl,
    )

    fun fasting(): FastingModel {
        val enabled = store.settings.get(SettingKeys.NUTRITION_FASTING) == "true"
        val now = clock.now().toEpochMilliseconds()
        val open = store.nutrition.openFast()
        val targetHours = open?.window?.targetHours
            ?: store.settings.get(SettingKeys.NUTRITION_FASTING_HOURS)?.toDoubleOrNull()
            ?: 16.0
        val window = open?.window
        return FastingModel(
            enabled = enabled,
            open = window?.isOpen == true,
            startAtMillis = window?.startEpochMillis,
            targetHours = targetHours,
            nowMillis = now,
            elapsedMillis = window?.elapsedMillis(now) ?: 0L,
            remainingMillis = window?.remainingHours(now)?.let {
                (it * FastWindow.MILLIS_PER_HOUR).toLong()
            },
            progress = window?.progress(now) ?: 0f,
            presets = listOf(12.0, 14.0, 16.0, 18.0),
        )
    }

    // ---- Searching and portions ---------------------------------------------------

    fun search(
        query: String,
        meal: MealChoiceModel,
        date: LocalDate,
        limit: Int = 30,
    ): FoodSearchModel {
        val localRows = if (query.isBlank()) {
            emptyList()
        } else {
            store.nutrition.searchFoodsContaining(query.trim(), limit.toLong()).map(::rowOf)
        }
        val localIds = localRows.map { it.food.id }.toSet()
        val bundled = if (query.isBlank()) {
            emptyList()
        } else {
            foodIndex.search(query, limit)
                .filter { "usda:${it.id}" !in localIds }
                .map(::rowOf)
        }
        val recents = if (query.isBlank()) {
            store.nutrition.recentFoods(RECENT_LIMIT).map(::rowOf)
        } else {
            emptyList()
        }
        val frequents = if (query.isBlank()) {
            store.nutrition.frequentFoods(RECENT_LIMIT).map(::rowOf)
        } else {
            emptyList()
        }
        return FoodSearchModel(
            query = query,
            meal = meal,
            meals = mealChoices(date),
            date = date,
            local = localRows,
            bundled = bundled,
            recents = recents,
            frequents = frequents,
            templates = if (query.isBlank()) templates() else emptyList(),
            indexVersion = foodIndex.version,
        )
    }

    fun portion(food: FoodChoiceModel, meal: MealChoiceModel, date: LocalDate): PortionModel {
        val policy = preferencesPolicy()
        val selectedAllergens = Allergen.fromKeys(
            store.settings.get(SettingKeys.ALLERGIES)
                ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
        )
        val pattern = store.settings.get(SettingKeys.DIETARY_PATTERN)
            ?.let(DietaryPattern::fromKey)
        val defaultGrams = food.defaultServingG
            ?: food.portions.firstOrNull()?.grams
            ?: 100.0
        return PortionModel(
            food = food,
            meal = meal,
            meals = mealChoices(date),
            date = date,
            grams = defaultGrams,
            servings = 1.0,
            servingLabel = food.defaultServingLabel ?: food.portions.firstOrNull()?.label,
            entryId = null,
            weightNeutral = policy.hidesSomething,
            allergens = selectedAllergens.filter { it.declaredIn(food.allergens) }.toSet(),
            hasAllergenData = food.allergens.isNotEmpty(),
            dietPatternKey = pattern?.key,
            dietStatus = dietStatus(pattern, food.analysisTags),
        )
    }

    fun portionForEntry(entryId: String): PortionModel? {
        val entry = store.nutrition.entryById(entryId) ?: return null
        val food = entry.foodId?.let(store.nutrition::foodById)
        val choice = food?.let(::choiceOf) ?: quickChoiceOf(entry)
        val meal = entry.mealId?.let(store.nutrition::mealById)
        val mealChoice = if (meal == null) {
            MealChoiceModel(null, MealSlot.SNACKS, "")
        } else {
            MealChoiceModel(
                id = meal.id,
                slot = MealSlot.fromKey(meal.kind.key),
                label = meal.name,
            )
        }
        val base = portion(choice, mealChoice, entry.localDate)
        return base.copy(
            grams = entry.quantityG ?: 100.0,
            servings = entry.servings ?: 1.0,
            servingLabel = entry.servingLabel,
            entryId = entry.id,
        )
    }

    private fun quickChoiceOf(entry: EntryRecord): FoodChoiceModel {
        val grams = entry.quantityG?.takeIf { it > 0.0 } ?: 100.0
        val per100g = entry.totals.values.mapValues { (_, amount) -> amount / grams * 100.0 }
        return FoodChoiceModel(
            id = "",
            name = entry.foodName ?: "",
            brand = entry.brand,
            barcode = null,
            sourceKey = entry.sourceKind,
            sourceLabel = entry.sourceKind,
            foodTypeKey = "custom",
            per100g = per100g,
            portions = emptyList(),
            defaultServingG = null,
            defaultServingLabel = null,
            allergens = emptySet(),
            analysisTags = emptySet(),
            ingredientsText = null,
            verified = entry.verified,
            isRecipe = false,
            isBundled = false,
        )
    }

    fun meals(date: LocalDate): List<MealChoiceModel> = mealChoices(date)

    private fun mealChoices(date: LocalDate): List<MealChoiceModel> {
        val custom = store.nutrition.mealsForDay(date)
            .filter { it.kind == MealKind.CUSTOM }
            .map { MealChoiceModel(it.id, MealSlot.CUSTOM, it.name) }
        return MealSlot.daySections.map { MealChoiceModel(null, it, "") } + custom
    }

    fun entryDetail(entryId: String): EntryDetailModel? {
        val entry = store.nutrition.entryById(entryId) ?: return null
        val meal = entry.mealId?.let(store.nutrition::mealById)
        return EntryDetailModel(
            id = entry.id,
            name = entry.foodName ?: entry.foodId?.let { store.nutrition.foodById(it)?.name } ?: "",
            brand = entry.brand,
            mealSlot = meal?.kind?.let { MealSlot.fromKey(it.key) } ?: MealSlot.SNACKS,
            mealName = meal?.name?.takeIf { meal.kind == MealKind.CUSTOM },
            date = entry.localDate,
            grams = entry.quantityG,
            servings = entry.servings,
            servingLabel = entry.servingLabel,
            kcal = entry.totals.of(Nutrient.ENERGY),
            proteinG = entry.totals.of(Nutrient.PROTEIN),
            carbsG = entry.totals.of(Nutrient.CARBS),
            fatG = entry.totals.of(Nutrient.FAT),
            fibreG = entry.totals.of(Nutrient.FIBRE),
            happenedAtMillis = entry.happenedAt.toEpochMilliseconds(),
            recordedAtMillis = entry.recordedAt.toEpochMilliseconds(),
            zoneId = entry.zoneId,
            device = entry.device,
            sourceKey = entry.sourceKind,
            verified = entry.verified,
            edited = entry.edited,
            rawRecordId = entry.rawRecordId,
        )
    }

    private fun rowOf(food: FoodRecord): FoodRowModel = FoodRowModel(choiceOf(food))

    private fun rowOf(food: IndexedFood): FoodRowModel = FoodRowModel(choiceOf(food))

    private fun choiceOf(food: FoodRecord): FoodChoiceModel = FoodChoiceModel(
        id = food.id,
        name = food.name,
        brand = food.brand,
        barcode = food.barcode,
        sourceKey = food.sourceKind,
        sourceLabel = food.sourceKind,
        foodTypeKey = food.foodType.key,
        per100g = food.per100g,
        portions = food.portions,
        defaultServingG = food.defaultServingG,
        defaultServingLabel = food.defaultServingLabel,
        allergens = food.allergens,
        analysisTags = food.analysisTags,
        ingredientsText = food.ingredientsText,
        verified = food.verified,
        isRecipe = food.isRecipe,
        isBundled = food.foodType == FoodType.BUNDLED,
    )

    private fun choiceOf(food: IndexedFood): FoodChoiceModel = FoodChoiceModel(
        id = "usda:${food.id}",
        name = food.name,
        brand = null,
        barcode = null,
        sourceKey = "usda",
        sourceLabel = "usda",
        foodTypeKey = FoodType.BUNDLED.key,
        per100g = food.nutrition.per100g,
        portions = food.nutrition.portions,
        defaultServingG = food.nutrition.portions.firstOrNull()?.grams,
        defaultServingLabel = food.nutrition.portions.firstOrNull()?.label,
        allergens = emptySet(),
        analysisTags = emptySet(),
        ingredientsText = null,
        verified = false,
        isRecipe = false,
        isBundled = true,
    )

    private fun dietStatus(pattern: DietaryPattern?, tags: Set<String>): DietStatus =
        when (pattern) {
            DietaryPattern.VEGAN -> when {
                "en:non-vegan" in tags -> DietStatus.DOES_NOT_MATCH
                "en:vegan" in tags -> DietStatus.MATCHES
                else -> DietStatus.UNKNOWN
            }

            DietaryPattern.VEGETARIAN -> when {
                "en:non-vegetarian" in tags -> DietStatus.DOES_NOT_MATCH
                "en:vegetarian" in tags || "en:vegan" in tags -> DietStatus.MATCHES
                else -> DietStatus.UNKNOWN
            }

            else -> DietStatus.UNKNOWN
        }

    // ---- Writing the diary --------------------------------------------------------

    fun logFood(
        food: FoodChoiceModel,
        meal: MealChoiceModel,
        date: LocalDate,
        grams: Double,
        servings: Double,
        servingLabel: String?,
        at: Instant,
        entryId: String? = null,
    ): String {
        // A quick-calorie entry has no food row; editing its amount must not mint an
        // orphan food with a blank name.
        val localId = if (food.id.isBlank()) null else ensureFood(food)
        val mealId = ensureMeal(meal, date, at)
        val totals = PortionMath.forGrams(food.per100g, grams)
        val recordedAt = clock.now()
        val draftId = entryId ?: ids.newId()
        val draft = EntryDraft(
            id = draftId,
            mealId = mealId,
            foodId = localId,
            localDate = date,
            zoneId = zone,
            happenedAt = at,
            recordedAt = recordedAt,
            quantityG = grams,
            servingLabel = servingLabel,
            // The serving's own gram weight, not the total: 2 x 80 g stores 80.
            servingG = if (servings > 0.0) grams / servings else grams,
            servings = servings,
            foodName = food.name,
            brand = food.brand,
            totals = totals,
            sourceKind = food.sourceKey,
            sourceId = manualSource.value,
            verified = food.verified,
            edited = entryId != null,
            idempotencyKey = "entry:$draftId",
            device = "manual",
            appVersion = appVersion,
        )
        if (entryId != null) {
            store.nutrition.updateEntry(entryId, draft)
            return entryId
        }
        store.nutrition.upsertEntry(draft)
        return draft.id
    }

    fun logQuick(
        kcal: Double,
        protein: Double,
        carbs: Double,
        fat: Double,
        fibre: Double,
        meal: MealChoiceModel,
        date: LocalDate,
        at: Instant,
    ): String {
        val mealId = ensureMeal(meal, date, at)
        val totals = linkedMapOf<Nutrient, Double>()
        if (kcal > 0.0) totals[Nutrient.ENERGY] = kcal
        if (protein > 0.0) totals[Nutrient.PROTEIN] = protein
        if (carbs > 0.0) totals[Nutrient.CARBS] = carbs
        if (fat > 0.0) totals[Nutrient.FAT] = fat
        if (fibre > 0.0) totals[Nutrient.FIBRE] = fibre
        val id = ids.newId()
        store.nutrition.upsertEntry(
            EntryDraft(
                id = id,
                mealId = mealId,
                foodId = null,
                localDate = date,
                zoneId = zone,
                happenedAt = at,
                recordedAt = clock.now(),
                quantityG = null,
                foodName = null,
                totals = totals,
                sourceKind = "quick",
                sourceId = manualSource.value,
                verified = true,
                idempotencyKey = "entry:$id",
                device = "manual",
                appVersion = appVersion,
            ),
        )
        return id
    }

    fun deleteEntry(entryId: String): TombstoneId? = store.trash.deleteEntry(EntryId(entryId))

    fun copyMeal(
        from: LocalDate,
        slot: MealSlot,
        to: LocalDate,
        sourceMealId: String? = null,
        sourceName: String? = null,
    ): Int {
        val meal = sourceMealId?.let(store.nutrition::mealById)
            ?: store.nutrition.mealsForDay(from).firstOrNull { it.kind.key == slot.key }
            ?: return 0
        val entries = store.nutrition.entriesForMeal(meal.id)
        val targetMeal = MealChoiceModel(
            id = null,
            slot = slot,
            label = if (slot == MealSlot.CUSTOM) sourceName.orEmpty() else "",
        )
        val at = defaultMoment(to, slot)
        val mealId = ensureMeal(targetMeal, to, at)
        var copied = 0
        entries.forEach { entry ->
            val id = ids.newId()
            store.nutrition.upsertEntry(
                EntryDraft(
                    id = id,
                    mealId = mealId,
                    foodId = entry.foodId,
                    localDate = to,
                    zoneId = zone,
                    happenedAt = retime(entry.happenedAt, to),
                    recordedAt = clock.now(),
                    quantityG = entry.quantityG,
                    servingLabel = entry.servingLabel,
                    servingG = entry.servingG,
                    servings = entry.servings,
                    foodName = entry.foodName,
                    brand = entry.brand,
                    totals = entry.totals.values,
                    sourceKind = entry.sourceKind,
                    sourceId = manualSource.value,
                    verified = entry.verified,
                    idempotencyKey = "entry:$id",
                    device = "manual",
                    appVersion = appVersion,
                ),
            )
            copied += 1
        }
        return copied
    }

    fun copyDay(from: LocalDate, to: LocalDate): Int {
        val entries = store.nutrition.entriesForDay(from)
        if (entries.isEmpty()) return 0
        var copied = 0
        entries.groupBy { it.mealId }.forEach { (mealId, rows) ->
            val sourceMeal = mealId?.let(store.nutrition::mealById)
            val slot = sourceMeal?.kind?.let { MealSlot.fromKey(it.key) } ?: MealSlot.SNACKS
            val at = defaultMoment(to, slot)
            // A custom meal is created fresh on the target day; reusing the source id
            // would file the copies under a meal that belongs to another day.
            val target = MealChoiceModel(
                id = null,
                slot = slot,
                label = if (sourceMeal?.kind == MealKind.CUSTOM) sourceMeal.name else "",
            )
            val targetMealId = ensureMeal(target, to, at)
            rows.forEach { entry ->
                val id = ids.newId()
                store.nutrition.upsertEntry(
                    EntryDraft(
                        id = id,
                        mealId = targetMealId,
                        foodId = entry.foodId,
                        localDate = to,
                        zoneId = zone,
                        happenedAt = retime(entry.happenedAt, to),
                        recordedAt = clock.now(),
                        quantityG = entry.quantityG,
                        servingLabel = entry.servingLabel,
                        servingG = entry.servingG,
                        servings = entry.servings,
                        foodName = entry.foodName,
                        brand = entry.brand,
                        totals = entry.totals.values,
                        sourceKind = entry.sourceKind,
                        sourceId = manualSource.value,
                        verified = entry.verified,
                        idempotencyKey = "entry:$id",
                        device = "manual",
                        appVersion = appVersion,
                    ),
                )
                copied += 1
            }
        }
        return copied
    }

    fun addCustomMeal(date: LocalDate, name: String): String? {
        if (name.isBlank()) return null
        val id = ids.newId()
        val at = defaultMoment(date, MealSlot.CUSTOM)
        store.nutrition.upsertMeal(
            MealDraft(
                id = id,
                name = name.trim(),
                kind = MealKind.CUSTOM,
                localDate = date,
                zoneId = zone,
                happenedAt = at,
                recordedAt = clock.now(),
                sourceId = manualSource.value,
                idempotencyKey = "meal:$id",
            ),
        )
        return id
    }

    private fun ensureMeal(meal: MealChoiceModel, date: LocalDate, at: Instant): String {
        meal.id?.let { existing ->
            store.nutrition.mealById(existing)
                ?.takeIf { it.localDate == date }
                ?.let { return it.id }
        }
        val slot = meal.slot
        if (slot != MealSlot.CUSTOM) {
            store.nutrition.mealForDayKind(date, slot.toData())?.let { return it.id }
        }
        val id = ids.newId()
        store.nutrition.upsertMeal(
            MealDraft(
                id = id,
                name = if (slot == MealSlot.CUSTOM) {
                    meal.label.ifBlank { "Meal" }
                } else {
                    slot.key
                },
                kind = slot.toData(),
                localDate = date,
                zoneId = zone,
                happenedAt = at,
                recordedAt = clock.now(),
                sourceId = manualSource.value,
                idempotencyKey = "meal:$id",
            ),
        )
        return id
    }

    fun atFor(date: LocalDate, slot: MealSlot, now: Instant = clock.now()): Instant =
        if (date == now.toLocalDateTime(zone).date) now else defaultMoment(date, slot)

    private fun defaultMoment(date: LocalDate, slot: MealSlot): Instant {
        val hour = when (slot) {
            MealSlot.BREAKFAST -> 8
            MealSlot.LUNCH -> 12
            MealSlot.SNACKS -> 15
            MealSlot.DINNER -> 19
            MealSlot.CUSTOM -> 12
        }
        return LocalDateTime(date, LocalTime(hour, 0)).toInstant(zone)
    }

    private fun retime(original: Instant, toDate: LocalDate): Instant {
        val time = original.toLocalDateTime(zone).time
        return LocalDateTime(toDate, time).toInstant(zone)
    }

    private fun ensureFood(food: FoodChoiceModel): String? {
        // A food this phone already holds is logged as itself. That includes a bundled
        // food copied in by an earlier log and the demo corpus's own rows: re-prefixing
        // those with `usda:` would both duplicate the row and claim a USDA source ref the
        // food never had.
        if (food.id.isNotBlank() && !food.id.startsWith("off:")) {
            store.nutrition.foodById(food.id)?.let { return it.id }
        }
        val id = when {
            food.id.startsWith("usda:") || food.id.startsWith("off:") -> food.id
            food.isBundled -> "usda:${food.id.removePrefix("usda:")}"
            else -> food.id.ifBlank { ids.newId() }
        }
        val sourceRef = when {
            id.startsWith("usda:") -> id.removePrefix("usda:")
            id.startsWith("off:") -> id.removePrefix("off:")
            else -> null
        }
        val type = when {
            id.startsWith("usda:") -> FoodType.BUNDLED
            id.startsWith("off:") -> FoodType.OPEN_FOOD_FACTS
            food.isRecipe -> FoodType.RECIPE
            else -> FoodType.CUSTOM
        }
        store.nutrition.upsertFood(
            FoodDraft(
                id = id,
                name = food.name,
                brand = food.brand,
                barcode = food.barcode,
                sourceKind = when (type) {
                    FoodType.BUNDLED -> "usda"
                    FoodType.OPEN_FOOD_FACTS -> "openfoodfacts"
                    FoodType.RECIPE -> "recipe"
                    FoodType.CUSTOM -> "manual"
                },
                sourceRef = sourceRef,
                foodType = type,
                per100g = food.per100g,
                portions = food.portions,
                defaultServingG = food.defaultServingG,
                defaultServingLabel = food.defaultServingLabel,
                allergens = food.allergens,
                analysisTags = food.analysisTags,
                ingredientsText = food.ingredientsText,
                verified = food.verified,
            ),
        )
        return id
    }

    // ---- Custom foods and recipes -------------------------------------------------

    fun foodEditor(foodId: String?): FoodEditorModel? {
        if (foodId == null) {
            return FoodEditorModel(
                id = null,
                isRecipe = false,
                name = "",
                brand = "",
                barcode = "",
                servingLabel = "",
                servingGrams = "",
                nutrients = Nutrient.entries.associateWith { "" },
                ingredients = emptyList(),
                servings = "1",
                per100g = emptyMap(),
            )
        }
        val food = store.nutrition.foodById(foodId) ?: return null
        if (food.foodType == FoodType.BUNDLED || food.foodType == FoodType.OPEN_FOOD_FACTS) {
            return null
        }
        val ingredients = if (food.isRecipe) {
            store.nutrition.recipeItems(food.id).mapNotNull { item ->
                val ingredient = item.foodId?.let(store.nutrition::foodById)
                IngredientModel(
                    foodId = ingredient?.id,
                    name = item.foodName ?: ingredient?.name ?: "",
                    grams = item.grams,
                    per100g = ingredient?.per100g.orEmpty(),
                )
            }
        } else {
            emptyList()
        }
        val totalGrams = ingredients.sumOf { it.grams }
        val servings = food.defaultServingG
            ?.takeIf { it > 0.0 }
            ?.let { (totalGrams / it).roundTo(0).toInt().coerceAtLeast(1) }
            ?: 1
        return FoodEditorModel(
            id = food.id,
            isRecipe = food.isRecipe,
            name = food.name,
            brand = food.brand.orEmpty(),
            barcode = food.barcode.orEmpty(),
            servingLabel = food.defaultServingLabel.orEmpty(),
            servingGrams = food.defaultServingG?.plain().orEmpty(),
            nutrients = Nutrient.entries.associateWith { nutrient ->
                food.per100g[nutrient]?.plain().orEmpty()
            },
            ingredients = ingredients,
            servings = servings.toString(),
            per100g = food.per100g,
        )
    }

    fun recipeEditor(recipeId: String?): FoodEditorModel? = if (recipeId == null) {
        FoodEditorModel(
            id = null,
            isRecipe = true,
            name = "",
            brand = "",
            barcode = "",
            servingLabel = "",
            servingGrams = "",
            nutrients = Nutrient.entries.associateWith { "" },
            ingredients = emptyList(),
            servings = "1",
            per100g = emptyMap(),
        )
    } else {
        foodEditor(recipeId)
    }

    fun saveFood(save: FoodSaveModel): String? {
        if (save.name.isBlank()) return null
        val id = save.id ?: ids.newId()
        if (!save.isRecipe) {
            store.nutrition.upsertFood(
                FoodDraft(
                    id = id,
                    name = save.name.trim(),
                    brand = save.brand?.trim()?.takeIf { it.isNotBlank() },
                    barcode = save.barcode?.trim()?.takeIf { it.isNotBlank() },
                    sourceKind = "manual",
                    foodType = FoodType.CUSTOM,
                    per100g = save.nutrients,
                    defaultServingG = save.servingGrams,
                    defaultServingLabel = save.servingLabel?.takeIf { it.isNotBlank() },
                    verified = true,
                ),
            )
            return id
        }
        val ingredients = save.ingredients.mapNotNull { ingredient ->
            val foodId = ingredient.foodId
            val per100g = if (ingredient.per100g.isNotEmpty()) {
                ingredient.per100g
            } else {
                foodId?.let(store.nutrition::foodById)?.per100g.orEmpty()
            }
            if (ingredient.grams <= 0.0 || per100g.isEmpty()) return@mapNotNull null
            RecipeMath.Ingredient(ingredient.grams, per100g)
        }
        if (ingredients.isEmpty()) return null
        val per100g = RecipeMath.per100g(ingredients)
        val totalGrams = ingredients.sumOf { it.grams }
        val servings = save.servings.coerceAtLeast(1)
        store.nutrition.upsertFood(
            FoodDraft(
                id = id,
                name = save.name.trim(),
                sourceKind = "recipe",
                foodType = FoodType.RECIPE,
                per100g = per100g,
                defaultServingG = RecipeMath.servingGrams(totalGrams, servings),
                defaultServingLabel = "1 serving",
                verified = true,
            ),
        )
        store.nutrition.replaceRecipeItems(
            id,
            save.ingredients.mapIndexed { index, ingredient ->
                // An ingredient chosen from the bundled index or Open Food Facts is
                // copied into the encrypted database first: the recipe item's foreign
                // key must point at a food this phone holds, and an offline copy is what
                // makes the recipe work without the index.
                val existing = ingredient.foodId?.let(store.nutrition::foodById)
                val foodId = when {
                    existing != null -> existing.id
                    ingredient.per100g.isEmpty() -> return@mapIndexed null
                    else -> {
                        val copyId = ingredient.foodId
                            ?.takeIf { it.startsWith("usda:") || it.startsWith("off:") }
                            ?: ids.newId()
                        val bundled = copyId.startsWith("usda:")
                        store.nutrition.upsertFood(
                            FoodDraft(
                                id = copyId,
                                name = ingredient.name,
                                sourceKind = if (bundled) "usda" else "manual",
                                sourceRef = when {
                                    copyId.startsWith("usda:") ->
                                        copyId.removePrefix("usda:")
                                    copyId.startsWith("off:") ->
                                        copyId.removePrefix("off:")
                                    else -> null
                                },
                                foodType = when {
                                    copyId.startsWith("usda:") -> FoodType.BUNDLED
                                    copyId.startsWith("off:") -> FoodType.OPEN_FOOD_FACTS
                                    else -> FoodType.CUSTOM
                                },
                                per100g = ingredient.per100g,
                                verified = false,
                            ),
                        )
                        copyId
                    }
                }
                RecipeItemDraft(
                    id = ids.newId(),
                    foodId = foodId,
                    grams = ingredient.grams,
                    foodName = ingredient.name,
                )
            }.filterNotNull(),
        )
        return id
    }

    fun duplicateRecipe(recipeId: String): String? {
        val food = store.nutrition.foodById(recipeId) ?: return null
        if (!food.isRecipe) return null
        val id = ids.newId()
        store.nutrition.upsertFood(
            FoodDraft(
                id = id,
                name = "${food.name} copy",
                foodType = FoodType.RECIPE,
                sourceKind = "recipe",
                per100g = food.per100g,
                portions = food.portions,
                defaultServingG = food.defaultServingG,
                defaultServingLabel = food.defaultServingLabel,
                verified = true,
            ),
        )
        store.nutrition.replaceRecipeItems(
            id,
            store.nutrition.recipeItems(recipeId).map { item ->
                RecipeItemDraft(ids.newId(), item.foodId, item.grams, item.foodName)
            },
        )
        return id
    }

    // ---- Targets, water, fasting, meal templates ----------------------------------

    fun saveTargets(date: LocalDate, save: TargetsSaveModel) {
        val existing = store.nutrition.targetOn(date)
        val id = existing?.id ?: ids.newId()
        val now = clock.now().toEpochMilliseconds()
        store.nutrition.saveTarget(
            NutritionTargetRecord(
                id = id,
                effectiveFrom = date,
                targets = MacroTargets(
                    kcal = save.kcal,
                    proteinG = save.proteinG,
                    carbsG = save.carbsG,
                    fatG = save.fatG,
                    fibreG = save.fibreG,
                    waterMl = save.waterMl,
                ),
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                revision = (existing?.revision ?: 0L) + 1L,
            ),
        )
    }

    fun logWater(ml: Double, at: Instant = clock.now()) {
        if (ml <= 0.0) return
        store.observations.record(
            Observation.of(
                id = ObservationId(ids.newId()),
                type = ObservationType.WATER,
                value = Measure(ml, UcumUnit.MILLILITRE),
                start = at,
                provenance = Provenance(
                    sourceId = manualSource,
                    time = TimeProvenance.of(at, clock.now(), zone),
                    idempotencyKey = "water:${ids.newId()}",
                    device = "manual",
                    appVersion = appVersion,
                ),
            ).copy(context = ObservationContext.OTHER),
        )
        store.settings.put(SettingKeys.LAST_WATER_ML, ml.toInt().toString())
    }

    fun containers(): List<WaterContainerModel> = store.nutrition.waterContainers().map {
        WaterContainerModel(it.id, it.label, it.millilitres, it.isDefault)
    }

    fun saveContainer(id: String?, label: String, millilitres: Double): String? {
        if (label.isBlank() || millilitres <= 0.0) return null
        val containerId = id ?: ids.newId()
        val existing = store.nutrition.waterContainers().firstOrNull { it.id == containerId }
        store.nutrition.upsertWaterContainer(
            WaterContainerRecord(
                id = containerId,
                label = label.trim(),
                millilitres = millilitres,
                position = existing?.position ?: store.nutrition.countWaterContainers(),
                isDefault = existing?.isDefault ?: false,
            ),
        )
        return containerId
    }

    fun deleteContainer(id: String) {
        store.nutrition.deleteWaterContainer(id)
    }

    fun selectContainer(id: String) {
        store.settings.put(SettingKeys.NUTRITION_LAST_CONTAINER, id)
    }

    fun setFastingEnabled(enabled: Boolean) {
        store.settings.put(SettingKeys.NUTRITION_FASTING, enabled.toString())
    }

    fun setFastingHours(hours: Double) {
        store.settings.put(SettingKeys.NUTRITION_FASTING_HOURS, hours.toString())
    }

    fun startFast(hours: Double) {
        if (store.nutrition.openFast() != null) return
        val now = clock.now()
        val id = ids.newId()
        store.nutrition.saveFast(
            FastRecord(
                id = id,
                localDate = now.toLocalDateTime(zone).date,
                zoneId = zone.id,
                window = FastWindow(now.toEpochMilliseconds(), hours),
                recordedAt = now,
                createdAt = now.toEpochMilliseconds(),
                updatedAt = now.toEpochMilliseconds(),
                revision = 1L,
            ),
        )
        store.settings.put(SettingKeys.NUTRITION_FASTING_HOURS, hours.toString())
    }

    fun endFast() {
        val open = store.nutrition.openFast() ?: return
        val now = clock.now()
        store.nutrition.saveFast(
            open.copy(
                window = open.window.copy(endEpochMillis = now.toEpochMilliseconds()),
                updatedAt = now.toEpochMilliseconds(),
                revision = open.revision + 1L,
            ),
        )
    }

    fun templates(): List<MealTemplateRowModel> = store.nutrition.mealTemplates().map { template ->
        val items = store.nutrition.mealTemplateItems(template.id)
        val kcal = items.sumOf { item ->
            val food = item.foodId?.let(store.nutrition::foodById) ?: return@sumOf 0.0
            PortionMath.kilocalories(food.per100g, item.grams) ?: 0.0
        }
        MealTemplateRowModel(
            id = template.id,
            name = template.name,
            items = items.size,
            kcal = kcal.takeIf { items.isNotEmpty() },
        )
    }

    fun saveMealAsTemplate(mealId: String, name: String): String? {
        val meal = store.nutrition.mealById(mealId) ?: return null
        if (name.isBlank()) return null
        val entries = store.nutrition.entriesForMeal(mealId).filter { it.foodId != null }
        if (entries.isEmpty()) return null
        val templateId = ids.newId()
        val now = clock.now().toEpochMilliseconds()
        store.nutrition.upsertMealTemplate(
            MealTemplateRecord(
                id = templateId,
                name = name.trim(),
                kind = meal.kind,
                createdAt = now,
                updatedAt = now,
                revision = 1L,
            ),
        )
        store.nutrition.replaceMealTemplateItems(
            templateId,
            entries.map { entry ->
                RecipeItemDraft(
                    id = ids.newId(),
                    foodId = entry.foodId,
                    grams = entry.quantityG ?: 0.0,
                    foodName = entry.foodName,
                )
            },
        )
        return templateId
    }

    fun logTemplate(templateId: String, meal: MealChoiceModel, date: LocalDate): Int {
        val template = store.nutrition.mealTemplateById(templateId) ?: return 0
        val items = store.nutrition.mealTemplateItems(templateId)
        if (items.isEmpty()) return 0
        val at = atFor(date, MealSlot.fromKey(template.kind.key))
        val mealId = ensureMeal(meal, date, at)
        var logged = 0
        items.forEach { item ->
            val food = item.foodId?.let(store.nutrition::foodById) ?: return@forEach
            val totals = PortionMath.forGrams(food.per100g, item.grams)
            val id = ids.newId()
            store.nutrition.upsertEntry(
                EntryDraft(
                    id = id,
                    mealId = mealId,
                    foodId = food.id,
                    localDate = date,
                    zoneId = zone,
                    happenedAt = at,
                    recordedAt = clock.now(),
                    quantityG = item.grams,
                    foodName = food.name,
                    totals = totals,
                    sourceKind = food.sourceKind,
                    sourceId = manualSource.value,
                    verified = food.verified,
                    idempotencyKey = "entry:$id",
                    device = "manual",
                    appVersion = appVersion,
                ),
            )
            logged += 1
        }
        return logged
    }

    // ---- Barcode ------------------------------------------------------------------

    fun barcodeStart(): BarcodeModel {
        val accepted = store.settings.get(SettingKeys.NUTRITION_OFF_DISCLOSURE) == "true"
        return BarcodeModel(
            state = if (accepted) BarcodeState.SCANNING else BarcodeState.DISCLOSURE,
            barcode = null,
            product = null,
            disclosureAccepted = accepted,
        )
    }

    fun acceptBarcodeDisclosure() {
        store.settings.put(SettingKeys.NUTRITION_OFF_DISCLOSURE, "true")
    }

    fun manualBarcode(): BarcodeModel = BarcodeModel(
        state = BarcodeState.MANUAL,
        barcode = null,
        product = null,
        disclosureAccepted = true,
    )

    /**
     * The local cache answers first (`BUILD-ROADMAP.md` §B6.3): a barcode this phone has
     * already stored is a food row, so the scan resolves offline, in airplane mode, and
     * without a second request to Open Food Facts. Only an unknown barcode leaves the
     * phone, which is what the disclosure promises.
     */
    fun cachedBarcode(barcode: String): FoodChoiceModel? {
        val clean = barcode.filter { it.isDigit() }
        if (!OpenFoodFactsClient.isPlausibleBarcode(clean)) return null
        return store.nutrition.foodByBarcode(clean)?.let(::choiceOf)
    }

    suspend fun lookupBarcode(barcode: String): BarcodeModel {
        val clean = barcode.filter { it.isDigit() }
        if (!OpenFoodFactsClient.isPlausibleBarcode(clean)) {
            return BarcodeModel(BarcodeState.NOT_FOUND, clean, null, true)
        }
        cachedBarcode(clean)?.let { cached ->
            return BarcodeModel(BarcodeState.FOUND, clean, cached, true)
        }
        return when (val result = off.lookup(clean)) {
            is OffLookup.Found -> {
                val choice = cacheProduct(result.product)
                BarcodeModel(BarcodeState.FOUND, clean, choice, true)
            }

            OffLookup.NotFound -> BarcodeModel(BarcodeState.NOT_FOUND, clean, null, true)
            is OffLookup.Unavailable -> BarcodeModel(BarcodeState.UNAVAILABLE, clean, null, true)
        }
    }

    private fun cacheProduct(product: OffProduct): FoodChoiceModel {
        val id = "off:${product.barcode}"
        store.nutrition.upsertFood(
            FoodDraft(
                id = id,
                name = product.name,
                brand = product.brand,
                barcode = product.barcode,
                sourceKind = "openfoodfacts",
                sourceRef = product.barcode,
                foodType = FoodType.OPEN_FOOD_FACTS,
                per100g = product.per100g,
                defaultServingG = product.servingGrams,
                defaultServingLabel = product.servingLabel,
                allergens = product.allergens,
                analysisTags = product.analysisTags,
                ingredientsText = product.ingredientsText,
                verified = false,
            ),
        )
        return choiceOf(store.nutrition.foodById(id)!!)
    }

    // ---- Shared helpers -----------------------------------------------------------

    private fun targetFor(date: LocalDate): MacroTargets? = store.nutrition.targetFor(date)?.targets

    private fun preferencesPolicy(): DisplayPolicy =
        DisplayPolicy.of(store.settings.get(SettingKeys.WEIGHT_NEUTRAL) == "true")

    private fun MealSlot.toData(): MealKind = when (this) {
        MealSlot.BREAKFAST -> MealKind.BREAKFAST
        MealSlot.LUNCH -> MealKind.LUNCH
        MealSlot.DINNER -> MealKind.DINNER
        MealSlot.SNACKS -> MealKind.SNACKS
        MealSlot.CUSTOM -> MealKind.CUSTOM
    }

    private fun Double.plain(): String =
        if (this == toLong().toDouble()) toLong().toString() else toString()

    private fun Double.targetText(decimals: Int): String = roundTo(decimals).plain()

    private fun Double.roundTo(decimals: Int): Double {
        var factor = 1.0
        repeat(decimals) { factor *= 10.0 }
        return kotlin.math.round(this * factor) / factor
    }

    private companion object {
        const val RECENT_LIMIT = 8L
    }
}
