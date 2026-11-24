package app.openhealth.core.nutrition

import app.openhealth.core.citation.CitationKey
import app.openhealth.core.formula.FormulaOutcome
import app.openhealth.core.formula.SexAtBirth
import app.openhealth.core.preferences.Allergen
import app.openhealth.core.units.Dimension
import app.openhealth.core.units.Measure
import app.openhealth.core.units.UcumUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NutrientTest {
    @Test
    fun everyNutrientHasAUniqueKeyAndAUnitOfItsDimension() {
        assertEquals(Nutrient.entries.size, Nutrient.entries.map { it.key }.toSet().size)
        Nutrient.entries.forEach { nutrient ->
            val expected = if (nutrient == Nutrient.ENERGY) Dimension.ENERGY else Dimension.MASS
            assertEquals(expected, nutrient.unit.dimension, nutrient.key)
            assertTrue(nutrient.unit !in setOf(UcumUnit.KILOGRAM, UcumUnit.POUND), nutrient.key)
        }
    }

    @Test
    fun everyDailyValueIsPositiveAndCitesTheFda() {
        ReferenceIntakes.all().forEach { reference ->
            assertTrue(reference.amount > 0.0, reference.nutrient.key)
            assertEquals(CitationKey.FDA_DAILY_VALUES_2020, reference.citation)
            assertEquals(reference.nutrient.dailyValue, reference.amount)
        }
        Nutrient.entries.filter { it.dailyValue == null }.forEach {
            assertNull(ReferenceIntakes.of(it), it.key)
        }
        assertTrue(ReferenceIntakes.all().isNotEmpty())
    }

    @Test
    fun theMicroPanelOmitsMacrosAndHasNoDuplicates() {
        assertTrue(Nutrient.microPanel.none { it.group == NutrientGroup.MACRO })
        assertTrue(Nutrient.PROTEIN !in Nutrient.microPanel)
        assertEquals(
            Nutrient.microPanel.size,
            Nutrient.microPanel.map { it.key }.toSet().size,
        )
    }

    @Test
    fun aShareIsTheAmountOverTheDailyValue() {
        val vitaminC = ReferenceIntakes.of(Nutrient.VITAMIN_C)!!
        assertEquals(0.5, vitaminC.share(45.0))
        assertEquals(0.0, vitaminC.share(0.0))
    }

    @Test
    fun aMicrogramIsAThousandthOfAMilligram() {
        val micro = Measure(1_000.0, UcumUnit.MICROGRAM).convertTo(UcumUnit.MILLIGRAM)
        assertEquals(1.0, micro.value, 1e-12)
        assertEquals(1e-9, Measure(1.0, UcumUnit.MICROGRAM).toCanonical().value, 1e-18)
    }
}

class PortionMathTest {
    private val per100g = mapOf(
        Nutrient.ENERGY to 389.0,
        Nutrient.PROTEIN to 16.9,
        Nutrient.FAT to 6.9,
        Nutrient.FIBRE to 10.6,
    )

    @Test
    fun oneHundredGramsIsTheStoredValueExactly() {
        val amounts = PortionMath.forGrams(per100g, 100.0)
        per100g.forEach { (nutrient, value) ->
            assertEquals(value, amounts.getValue(nutrient), 0.0)
        }
    }

    @Test
    fun fiftyGramsIsHalf() {
        assertEquals(194.5, PortionMath.forGrams(per100g, 50.0).getValue(Nutrient.ENERGY))
        assertEquals(8.45, PortionMath.forGrams(per100g, 50.0).getValue(Nutrient.PROTEIN))
    }

    @Test
    fun aHouseholdPortionScalesByItsServingCount() {
        val cup = FoodPortion("1 cup", 80.0)
        assertEquals(240.0, PortionMath.gramsFor(cup, 3.0))
        assertEquals(80.0, PortionMath.gramsFor(cup, 1.0))
        assertFailsWith<IllegalArgumentException> { PortionMath.gramsFor(cup, -1.0) }
    }

    @Test
    fun aNegativeMassIsRejected() {
        assertFailsWith<IllegalArgumentException> { PortionMath.forGrams(per100g, -1.0) }
    }

    @Test
    fun totalsAddInOrderWithoutRounding() {
        val first = PortionMath.forGrams(per100g, 33.3)
        val second = PortionMath.forGrams(per100g, 66.7)
        val totals = NutrientTotals.sumInOrder(listOf(first, second))
        // The same left-to-right addition the rollup performs, not a rounded display value.
        assertEquals(
            first.getValue(Nutrient.ENERGY) + second.getValue(Nutrient.ENERGY),
            totals.values.getValue(Nutrient.ENERGY),
        )
        assertEquals(389.0, totals.values.getValue(Nutrient.ENERGY), 1e-12)
        assertEquals(
            first.getValue(Nutrient.FIBRE) + second.getValue(Nutrient.FIBRE),
            totals.values.getValue(Nutrient.FIBRE),
        )
    }

    @Test
    fun aNutrientNoEntryReportsIsMissingNotZero() {
        val totals = NutrientTotals.sumInOrder(listOf(mapOf(Nutrient.ENERGY to 100.0)))
        assertNull(totals.of(Nutrient.PROTEIN))
        assertEquals(100.0, totals.of(Nutrient.ENERGY))
    }

    @Test
    fun recipeValuesComeFromItsIngredients() {
        val values = RecipeMath.per100g(
            listOf(
                RecipeMath.Ingredient(200.0, mapOf(Nutrient.ENERGY to 50.0)),
                RecipeMath.Ingredient(100.0, mapOf(Nutrient.ENERGY to 200.0)),
            ),
        )
        assertEquals(100.0, values.getValue(Nutrient.ENERGY), 1e-12)
    }

    @Test
    fun anEmptyRecipeHasNoValuesRatherThanDividingByZero() {
        assertTrue(RecipeMath.per100g(emptyList()).isEmpty())
        assertTrue(RecipeMath.per100g(listOf(RecipeMath.Ingredient(0.0, emptyMap()))).isEmpty())
    }

    @Test
    fun aServingIsTheFinishedWeightDividedByTheServings() {
        assertEquals(150.0, RecipeMath.servingGrams(600.0, 4))
        assertFailsWith<IllegalArgumentException> { RecipeMath.servingGrams(600.0, 0) }
    }
}

class TargetSuggestionsTest {
    @Test
    fun theSuggestionUsesTheCitedMacroRules() {
        val outcome = TargetSuggestions.suggest(SexAtBirth.MALE, 80.0, 2_400.0)
        val targets = (outcome as FormulaOutcome.Computed).value
        assertEquals(CitationKey.IOM_MACRONUTRIENTS_2005, outcome.citation)
        assertEquals(64.0, targets.proteinG!!, 1e-12)
        assertEquals(80.0, targets.fatG!!, 1e-12)
        assertEquals(356.0, targets.carbsG!!, 1e-12)
        assertEquals(33.6, targets.fibreG!!, 1e-12)
        assertEquals(2_500.0, targets.waterMl)
        assertEquals(2_400.0, targets.kcal)
    }

    @Test
    fun aFemaleSuggestionUsesTheFemaleWaterStartingPoint() {
        val targets = (
            TargetSuggestions.suggest(SexAtBirth.FEMALE, 60.0, 1_800.0)
                as FormulaOutcome.Computed
            ).value
        assertEquals(2_000.0, targets.waterMl)
        assertEquals(48.0, targets.proteinG!!, 1e-12)
    }

    @Test
    fun anImpossibleEnergyIsNotAvailable() {
        val outcome = TargetSuggestions.suggest(SexAtBirth.MALE, 80.0, 100.0)
        assertTrue(outcome is FormulaOutcome.NotAvailable)
    }

    @Test
    fun aGoalRateBecomesADailyEnergyWithACitation() {
        val outcome = TargetSuggestions.energyForRate(2_500.0, -0.45) as FormulaOutcome.Computed
        assertEquals(CitationKey.WISHNOFSKY_1958, outcome.citation)
        assertEquals(2_500.0 - 0.45 * 7_700.0 / 7.0, outcome.value, 1e-12)
    }

    @Test
    fun targetsRefuseNegativeValues() {
        assertFailsWith<IllegalArgumentException> { MacroTargets(kcal = -1.0) }
        assertTrue(MacroTargets.EMPTY.isEmpty)
    }
}

class FastWindowTest {
    private val start = 1_000_000L
    private val hour = FastWindow.MILLIS_PER_HOUR.toLong()

    @Test
    fun anOpenFastCountsUpToNow() {
        val fast = FastWindow(start, targetHours = 16.0)
        assertTrue(fast.isOpen)
        assertEquals(8.0, fast.elapsedHours(start + 8 * hour), 1e-9)
        assertEquals(8.0, fast.remainingHours(start + 8 * hour)!!, 1e-9)
        assertEquals(0.5f, fast.progress(start + 8 * hour), 1e-6f)
        assertTrue(!fast.isPastTarget(start + 8 * hour))
    }

    @Test
    fun pastTheTargetItSaysSoWithoutAScore() {
        val fast = FastWindow(start, targetHours = 16.0)
        assertNull(fast.remainingHours(start + 17 * hour))
        assertEquals(1f, fast.progress(start + 17 * hour))
        assertTrue(fast.isPastTarget(start + 16 * hour))
    }

    @Test
    fun aClosedFastFreezesItsElapsedTime() {
        val fast = FastWindow(start, 16.0, endEpochMillis = start + 14 * hour)
        assertEquals(14.0, fast.elapsedHours(start + 40 * hour), 1e-9)
        assertEquals(14.0, fast.elapsedHours(start), 1e-9)
        assertTrue(!fast.isOpen)
    }

    @Test
    fun aFastCannotEndBeforeItStarts() {
        assertFailsWith<IllegalArgumentException> { FastWindow(start, 16.0, start - 1) }
        assertFailsWith<IllegalArgumentException> { FastWindow(start, 0.0) }
    }
}

class AllergenTest {
    @Test
    fun theCommonAllergensAreUniqueAndMatchTheirOffTags() {
        assertEquals(Allergen.entries.size, Allergen.entries.map { it.key }.toSet().size)
        assertTrue(Allergen.GLUTEN.declaredIn(setOf("en:gluten", "en:milk")))
        assertTrue(!Allergen.PEANUTS.declaredIn(setOf("en:nuts")))
        assertTrue(Allergen.SHELLFISH.declaredIn(setOf("en:molluscs")))
        assertTrue(!Allergen.OTHER.declaredIn(setOf("en:gluten")))
        assertEquals(setOf(Allergen.GLUTEN), Allergen.fromKeys(listOf("gluten", "unknown")))
        assertEquals(Allergen.PEANUTS, Allergen.fromKey(" Peanuts "))
        assertEquals(Allergen.NUTS, Allergen.fromKey("tree nuts"))
        assertEquals(Allergen.MILK, Allergen.fromKey("en:milk"))
        assertEquals(Allergen.MILK, Allergen.fromKey("Lactose"))
        assertEquals(Allergen.SHELLFISH, Allergen.fromKey("seafood"))
        assertNull(Allergen.fromKey(""))
    }

    @Test
    fun aFoodWithNoTagsMatchesNothing() {
        Allergen.entries.forEach { assertTrue(!it.declaredIn(emptySet())) }
    }

    @Test
    fun anIndexedFoodExposesItsEnergy() {
        val food = IndexedFood(
            id = "1",
            name = "Oats",
            category = "Cereal Grains",
            nutrition = FoodNutrition(mapOf(Nutrient.ENERGY to 389.0)),
        )
        assertNotNull(food.energyKcalPer100g)
        assertEquals(389.0, food.energyKcalPer100g)
    }
}
