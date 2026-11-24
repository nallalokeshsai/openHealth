package app.openhealth.ui.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import app.openhealth.core.citation.CitationKey
import app.openhealth.core.citation.Citations
import app.openhealth.core.nutrition.FoodPortion
import app.openhealth.core.nutrition.Nutrient
import app.openhealth.ui.screens.nutrition.BarcodeModel
import app.openhealth.ui.screens.nutrition.BarcodeScanScreen
import app.openhealth.ui.screens.nutrition.BarcodeState
import app.openhealth.ui.screens.nutrition.DayEntryModel
import app.openhealth.ui.screens.nutrition.EnergyCardModel
import app.openhealth.ui.screens.nutrition.FastCardModel
import app.openhealth.ui.screens.nutrition.FoodChoiceModel
import app.openhealth.ui.screens.nutrition.FoodEditorModel
import app.openhealth.ui.screens.nutrition.FoodEditorScreen
import app.openhealth.ui.screens.nutrition.IngredientModel
import app.openhealth.ui.screens.nutrition.MacroBarModel
import app.openhealth.ui.screens.nutrition.MacroKey
import app.openhealth.ui.screens.nutrition.MealSectionModel
import app.openhealth.ui.screens.nutrition.MealSlot
import app.openhealth.ui.screens.nutrition.MicroRowModel
import app.openhealth.ui.screens.nutrition.MicrosModel
import app.openhealth.ui.screens.nutrition.MicrosScreen
import app.openhealth.ui.screens.nutrition.NutritionDay
import app.openhealth.ui.screens.nutrition.NutritionModel
import app.openhealth.ui.screens.nutrition.NutritionRootScreen
import app.openhealth.ui.screens.nutrition.WaterCardModel
import app.openhealth.ui.screens.nutrition.WaterContainerModel
import app.openhealth.ui.theme.OhTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-mdpi")
class NutritionScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private enum class Variant(
        val dir: String,
        val dark: Boolean,
        val scale: Float,
        val rtl: Boolean,
    ) {
        Dark("dark", true, 1f, false),
        Light("light", false, 1f, false),
        Text200("text200", true, 2f, false),
        Rtl("rtl", true, 1f, true),
    }

    private fun shot(name: String, content: @Composable () -> Unit) {
        val variant = mutableStateOf(Variant.Dark)
        compose.setContent {
            val current = variant.value
            val density = LocalDensity.current
            OhTheme(dark = current.dark) {
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale = current.scale),
                    LocalLayoutDirection provides if (current.rtl) {
                        LayoutDirection.Rtl
                    } else {
                        LayoutDirection.Ltr
                    },
                ) {
                    Box(
                        modifier = Modifier
                            .testTag(SHOT)
                            .fillMaxSize()
                            .background(OhTheme.colors.bg),
                    ) {
                        content()
                    }
                }
            }
        }
        Variant.entries.forEach { entry ->
            compose.runOnIdle { variant.value = entry }
            compose.waitForIdle()
            compose.onNodeWithTag(SHOT)
                .captureRoboImage("src/test/screenshots/${entry.dir}/$name.png")
        }
    }

    @Test
    fun nutritionRoot() = shot("nutrition-root") {
        NutritionRootScreen(
            model = root,
            onSelectDay = {},
            onScan = {},
            onTargets = {},
            onMicros = {},
            onAddFood = {},
            onOpenEntry = {},
            onDeleteEntry = {},
            onAddMeal = {},
            onWater = {},
            onLogWater = {},
            onFasting = {},
            onCopyMeal = {},
            onSaveTemplate = { _, _ -> },
            onCopyDay = {},
            onOpenSettings = {},
        )
    }

    @Test
    fun nutritionRootWeightNeutral() = shot("nutrition-root-neutral") {
        NutritionRootScreen(
            model = root.copy(energy = null, weightNeutral = true),
            onSelectDay = {},
            onScan = {},
            onTargets = {},
            onMicros = {},
            onAddFood = {},
            onOpenEntry = {},
            onDeleteEntry = {},
            onAddMeal = {},
            onWater = {},
            onLogWater = {},
            onFasting = {},
            onCopyMeal = {},
            onSaveTemplate = { _, _ -> },
            onCopyDay = {},
            onOpenSettings = {},
        )
    }

    @Test
    fun nutritionMicros() = shot("nutrition-micros") {
        MicrosScreen(model = micros, onBack = {})
    }

    @Test
    fun nutritionBarcodeFound() = shot("nutrition-barcode-found") {
        BarcodeScanScreen(
            model = BarcodeModel(
                state = BarcodeState.FOUND,
                barcode = "3017620422003",
                product = nutella,
                disclosureAccepted = true,
            ),
            showKcal = true,
            cameraPreview = {},
            onBack = {},
            onAcceptDisclosure = {},
            onScanAgain = {},
            onManual = {},
            onLookup = {},
            onLog = {},
            onCreateFood = {},
        )
    }

    @Test
    fun nutritionBarcodeDisclosure() = shot("nutrition-barcode-disclosure") {
        BarcodeScanScreen(
            model = BarcodeModel(
                state = BarcodeState.DISCLOSURE,
                barcode = null,
                product = null,
                disclosureAccepted = false,
            ),
            showKcal = true,
            cameraPreview = {},
            onBack = {},
            onAcceptDisclosure = {},
            onScanAgain = {},
            onManual = {},
            onLookup = {},
            onLog = {},
            onCreateFood = {},
        )
    }

    @Test
    fun nutritionRecipeEditor() = shot("nutrition-recipe-editor") {
        FoodEditorScreen(
            model = FoodEditorModel(
                id = null,
                isRecipe = true,
                name = "Oat bowl",
                brand = "",
                barcode = "",
                servingLabel = "",
                servingGrams = "",
                nutrients = Nutrient.entries.associateWith { "" },
                ingredients = listOf(
                    IngredientModel("oats", "Oats", 80.0, oats.per100g),
                    IngredientModel("milk", "Milk", 200.0, milk.per100g),
                ),
                servings = "2",
                per100g = mapOf(Nutrient.ENERGY to 120.0),
            ),
            showKcal = true,
            onBack = {},
            onSave = {},
            onAddIngredient = {},
            onIngredientGrams = { _, _ -> },
            onRemoveIngredient = {},
            onDuplicate = {},
            onLog = {},
        )
    }

    private val date = LocalDate(2026, 11, 24)

    private val oats = FoodChoiceModel(
        id = "usda:1",
        name = "Oats",
        brand = null,
        barcode = null,
        sourceKey = "usda",
        sourceLabel = "usda",
        foodTypeKey = "bundled",
        per100g = mapOf(
            Nutrient.ENERGY to 389.0,
            Nutrient.PROTEIN to 16.9,
            Nutrient.CARBS to 66.3,
            Nutrient.FAT to 6.9,
            Nutrient.FIBRE to 10.6,
            Nutrient.SODIUM to 2.0,
        ),
        portions = listOf(FoodPortion("1 cup", 80.0)),
        defaultServingG = 80.0,
        defaultServingLabel = "1 cup",
        allergens = setOf("en:gluten"),
        analysisTags = setOf("en:vegan"),
        ingredientsText = null,
        verified = false,
        isRecipe = false,
        isBundled = true,
    )

    private val milk = FoodChoiceModel(
        id = "usda:2",
        name = "Milk",
        brand = null,
        barcode = null,
        sourceKey = "usda",
        sourceLabel = "usda",
        foodTypeKey = "bundled",
        per100g = mapOf(Nutrient.ENERGY to 42.0, Nutrient.PROTEIN to 3.4),
        portions = listOf(FoodPortion("1 cup", 244.0)),
        defaultServingG = 244.0,
        defaultServingLabel = "1 cup",
        allergens = setOf("en:milk"),
        analysisTags = emptySet(),
        ingredientsText = null,
        verified = false,
        isRecipe = false,
        isBundled = true,
    )

    private val nutella = FoodChoiceModel(
        id = "off:3017620422003",
        name = "Nutella",
        brand = "Ferrero",
        barcode = "3017620422003",
        sourceKey = "openfoodfacts",
        sourceLabel = "openfoodfacts",
        foodTypeKey = "openfoodfacts",
        per100g = mapOf(
            Nutrient.ENERGY to 539.0,
            Nutrient.PROTEIN to 6.3,
            Nutrient.CARBS to 57.5,
            Nutrient.FAT to 30.9,
            Nutrient.SUGARS to 56.3,
        ),
        portions = emptyList(),
        defaultServingG = 15.0,
        defaultServingLabel = "15 g",
        allergens = setOf("en:milk", "en:nuts", "en:soybeans"),
        analysisTags = setOf("en:vegetarian"),
        ingredientsText = "Sugar, palm oil, hazelnuts, milk",
        verified = false,
        isRecipe = false,
        isBundled = false,
    )

    private val week = (6 downTo 0).map { offset ->
        val day = date.minus(DatePeriod(days = offset))
        NutritionDay(day, isToday = offset == 0, isSelected = offset == 0, hasEntries = offset < 4)
    }

    private val entry = DayEntryModel(
        id = "entry-1",
        name = "Oats",
        grams = 80.0,
        servings = 1.0,
        servingLabel = "1 cup",
        kcal = 311.2,
        sourceKey = "usda",
        verified = false,
        edited = false,
    )

    private val root = NutritionModel(
        selectedDate = date,
        today = date,
        week = week,
        energy = EnergyCardModel(
            kcalIn = 1_240.0,
            kcalTarget = 2_400.0,
            remainingKcal = 1_160.0,
            bars = listOf(
                MacroBarModel(MacroKey.PROTEIN, 78.0, 120.0, 0.65f),
                MacroBarModel(MacroKey.CARBS, 142.0, 270.0, 0.52f),
                MacroBarModel(MacroKey.FAT, 41.0, 80.0, 0.51f),
                MacroBarModel(MacroKey.FIBRE, 19.0, 30.0, 0.63f),
            ),
            goalLine = null,
        ),
        meals = listOf(
            MealSectionModel(
                mealId = "meal-1",
                slot = MealSlot.BREAKFAST,
                name = null,
                entries = listOf(
                    entry,
                    entry.copy(id = "entry-2", name = "Milk", kcal = 102.0, grams = 244.0),
                ),
                totalKcal = 413.2,
                isToday = true,
            ),
            MealSectionModel("meal-2", MealSlot.LUNCH, null, emptyList(), null, true),
            MealSectionModel(
                "meal-3",
                MealSlot.DINNER,
                null,
                listOf(entry.copy(id = "entry-3", name = "Chicken breast", kcal = 412.0)),
                412.0,
                true,
            ),
            MealSectionModel("meal-4", MealSlot.SNACKS, null, emptyList(), null, true),
        ),
        water = WaterCardModel(
            totalMl = 1_250.0,
            targetMl = 2_500.0,
            fraction = 0.5f,
            containers = listOf(WaterContainerModel("water-bottle", "Bottle", 500.0, true)),
            lastContainer = WaterContainerModel("water-bottle", "Bottle", 500.0, true),
        ),
        fast = FastCardModel(
            open = true,
            startAtMillis = 0L,
            nowMillis = 54_000_000L,
            elapsedMillis = 54_000_000L,
            remainingMillis = 3_600_000L,
            targetHours = 16.0,
            progress = 0.9f,
        ),
        targetsSet = true,
        weightNeutral = false,
        demo = false,
        canCopyDay = false,
    )

    private val micros = MicrosModel(
        date = date,
        rows = listOf(
            MicroRowModel(Nutrient.SODIUM, 1_240.0, 0.54),
            MicroRowModel(Nutrient.POTASSIUM, 2_100.0, 0.45),
            MicroRowModel(Nutrient.CALCIUM, 480.0, 0.37),
            MicroRowModel(Nutrient.IRON, 8.2, 0.46),
            MicroRowModel(Nutrient.VITAMIN_C, 31.0, 0.34),
            MicroRowModel(Nutrient.VITAMIN_D, null, null),
        ),
        entryCount = 6,
        citationTitle = Citations.of(CitationKey.FDA_DAILY_VALUES_2020).title,
        citationUrl = Citations.of(CitationKey.FDA_DAILY_VALUES_2020).url,
    )

    private companion object {
        const val SHOT = "shot"
    }
}
