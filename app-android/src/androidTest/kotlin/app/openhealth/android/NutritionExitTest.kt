package app.openhealth.android

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.openhealth.android.ui.AppViewModel
import app.openhealth.android.ui.OpenHealthRoot
import app.openhealth.core.nutrition.Nutrient
import app.openhealth.core.preferences.HealthModule
import app.openhealth.core.preferences.UnitSystem
import app.openhealth.core.seam.HttpRequest
import app.openhealth.core.seam.HttpResponse
import app.openhealth.core.seam.HttpTransport
import app.openhealth.data.db.Profile
import app.openhealth.data.repo.EntryDraft
import app.openhealth.data.repo.MealDraft
import app.openhealth.data.repo.MealKind
import app.openhealth.integrations.food.OpenFoodFactsClient
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * B6's exit scenarios on the phone (`BUILD-ROADMAP.md` §B6). The camera capture itself is
 * a manual step (there is no way to point a test at a real barcode); the timed path here
 * starts at the decoded barcode and runs lookup, portion and log through the real state
 * holder, the real encrypted store and the real Open Food Facts parser. The build log
 * records that substitution.
 */
@RunWith(AndroidJUnit4::class)
class NutritionExitTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: android.content.Context
    private lateinit var container: AppContainer
    private lateinit var viewModel: AppViewModel
    private val transport = FakeTransport()

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        runCatching {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName,
                android.Manifest.permission.CAMERA,
            )
        }
        context.deleteDatabase(TEST_DATABASE)
        context.getSharedPreferences(TEST_VAULT, android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        container = AppContainer(
            context,
            databaseName = TEST_DATABASE,
            vaultPreferences = TEST_VAULT,
            offClientOverride = OpenFoodFactsClient(transport),
        )
        viewModel = AppViewModel(container, UnitSystem.METRIC)
    }

    @After
    fun tearDown() {
        viewModel.stop()
        Thread.sleep(300)
        container.close()
        runCatching {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName,
                android.Manifest.permission.CAMERA,
            )
        }
        context.deleteDatabase(TEST_DATABASE)
        context.getSharedPreferences(TEST_VAULT, android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun barcodeToLoggedTakesUnderFiveSeconds() {
        seedProfile()
        compose.setContent { OpenHealthRoot(container, viewModel) }
        waitFor("loaded") { viewModel.state.value.loaded }
        waitFor("profile") { viewModel.state.value.onboardingComplete }
        openNutrition()
        waitFor("nutrition") { viewModel.state.value.nutrition != null }

        compose.onNodeWithContentDescription("Scan a barcode").performClick()
        compose.onNodeWithText("Look up barcodes").performClick()
        compose.onNodeWithText("Enter the barcode instead").performClick()
        compose.onNodeWithContentDescription("Barcode digits").performTextInput("12345678")

        val started = System.nanoTime()
        compose.onNodeWithText("Look up").performClick()
        waitFor("found") {
            viewModel.state.value.barcode?.state ==
                app.openhealth.ui.screens.nutrition.BarcodeState.FOUND
        }
        compose.onNodeWithText("Log this product").performClick()
        waitFor("portion") { viewModel.state.value.portion != null }
        compose.onNodeWithText("Log").performClick()
        waitFor("entry stored") { container.store.nutrition.countEntries() == 1L }
        val millis = (System.nanoTime() - started) / 1_000_000.0
        println("barcode to logged: %.0f ms".format(millis))
        assertTrue(millis < 5_000.0, "barcode to logged took %.0f ms".format(millis))

        val today = container.clock.now().toLocalDateTime(container.zone).date
        val entry = container.store.nutrition.entriesForDay(today).single()
        assertEquals("openfoodfacts", entry.sourceKind)
        // 40 g of a 200 kcal/100 g product is exactly 80 kcal.
        assertEquals(80.0, entry.totals.of(Nutrient.ENERGY)!!, 1e-9)
        waitFor("energy card") { viewModel.state.value.nutrition?.energy?.kcalIn == 80.0 }
    }

    @Test
    fun aDaysTotalsEqualTheSumOfItsEntriesExactly() {
        seedProfile()
        val today = container.clock.now().toLocalDateTime(container.zone).date
        val meal = container.store.nutrition.upsertMeal(
            MealDraft(
                id = "meal-exact",
                name = "breakfast",
                kind = MealKind.BREAKFAST,
                localDate = today,
                zoneId = container.zone,
                happenedAt = container.clock.now(),
                recordedAt = container.clock.now(),
                sourceId = AppContainer.MANUAL_SOURCE.value,
                idempotencyKey = "meal-exact",
            ),
        )
        val first = mapOf(
            Nutrient.ENERGY to 1.0,
            Nutrient.PROTEIN to 1.0 / 3.0,
            Nutrient.SODIUM to 2.5,
        )
        val second = mapOf(
            Nutrient.ENERGY to 2.2,
            Nutrient.PROTEIN to 0.7,
            Nutrient.SODIUM to 0.1,
        )
        listOf(first, second).forEachIndexed { index, totals ->
            container.store.nutrition.upsertEntry(
                EntryDraft(
                    id = "exact-$index",
                    mealId = meal.id,
                    foodId = null,
                    localDate = today,
                    zoneId = container.zone,
                    happenedAt = container.clock.now(),
                    recordedAt = container.clock.now(),
                    quantityG = 100.0,
                    foodName = "Exact $index",
                    totals = totals,
                    sourceKind = "manual",
                    sourceId = AppContainer.MANUAL_SOURCE.value,
                    idempotencyKey = "exact-$index",
                ),
            )
        }
        viewModel.refresh()
        // Wait for the rebuilt model, not merely for a model: the previous one is still
        // in state while the refresh runs, and asserting against it is the B9/B10
        // class-sequence flake.
        waitFor("energy rebuilt") {
            viewModel.state.value.nutrition?.energy?.kcalIn == 3.2
        }
        val model = viewModel.state.value.nutrition!!
        val expected = container.store.nutrition.entriesForDay(today)
            .fold(0.0) { acc, entry -> acc + (entry.totals.of(Nutrient.ENERGY) ?: 0.0) }
        assertEquals(expected, model.energy!!.kcalIn, 0.0)
        assertEquals(3.2, model.energy!!.kcalIn, 0.0)
    }

    @Test
    fun weightNeutralHidesCaloriesAndEnergy() {
        seedProfile()
        container.preferences.setWeightNeutral(true)
        viewModel.refresh()
        compose.setContent { OpenHealthRoot(container, viewModel) }
        waitFor("loaded") { viewModel.state.value.loaded }
        openNutrition()
        waitFor("weight neutral") {
            viewModel.state.value.preferences.weightNeutral &&
                viewModel.state.value.nutrition?.energy == null
        }
        compose.onAllNodesWithText("kcal", substring = true).assertCountEquals(0)
        compose.onNodeWithText("Calories and energy are hidden in weight-neutral mode.")
            .assertExists()
    }

    @Test
    fun copyingADayCarriesItsEntriesToTheNewDay() {
        seedProfile()
        val source = container.clock.now().toLocalDateTime(container.zone).date
            .minus(DatePeriod(days = 1))
        val target = source.plus(DatePeriod(days = 1))
        val at = kotlinx.datetime.LocalDateTime(
            source,
            kotlinx.datetime.LocalTime(12, 0),
        ).toInstant(container.zone)
        val meal = container.store.nutrition.upsertMeal(
            MealDraft(
                id = "meal-copy",
                name = "Pre-workout",
                kind = MealKind.CUSTOM,
                localDate = source,
                zoneId = container.zone,
                happenedAt = container.clock.now(),
                recordedAt = container.clock.now(),
                sourceId = AppContainer.MANUAL_SOURCE.value,
                idempotencyKey = "meal-copy",
            ),
        )
        container.store.nutrition.upsertEntry(
            EntryDraft(
                id = "copy-entry",
                mealId = meal.id,
                foodId = null,
                localDate = source,
                zoneId = container.zone,
                happenedAt = at,
                recordedAt = container.clock.now(),
                quantityG = 100.0,
                foodName = "Rice",
                totals = mapOf(Nutrient.ENERGY to 130.0),
                sourceKind = "manual",
                sourceId = AppContainer.MANUAL_SOURCE.value,
                idempotencyKey = "copy-entry",
            ),
        )
        val presenter = app.openhealth.android.nutrition.NutritionPresenter(
            store = container.store,
            clock = container.clock,
            zone = container.zone,
            ids = container.ids,
            manualSource = AppContainer.MANUAL_SOURCE,
            appVersion = container.versionName,
            foodIndex = container.foodIndex,
            off = container.offClient,
        )
        assertEquals(1, presenter.copyDay(source, target))
        val copied = container.store.nutrition.entriesForDay(target)
        assertEquals(1, copied.size)
        assertEquals(130.0, copied.single().totals.of(Nutrient.ENERGY)!!, 0.0)
        assertEquals("Rice", copied.single().foodName)
        // A custom meal is recreated on the target day, not shared with the source day.
        val targetMeal = container.store.nutrition.mealsForDay(target)
            .single { it.kind == MealKind.CUSTOM }
        assertEquals("Pre-workout", targetMeal.name)
        assertTrue(targetMeal.id != "meal-copy")
        assertEquals(targetMeal.id, copied.single().mealId)
    }

    @Test
    fun aDeletedEntryComesBackAfterUndo() {
        seedProfile()
        val today = container.clock.now().toLocalDateTime(container.zone).date
        val meal = container.store.nutrition.upsertMeal(
            MealDraft(
                id = "meal-undo",
                name = "breakfast",
                kind = MealKind.BREAKFAST,
                localDate = today,
                zoneId = container.zone,
                happenedAt = container.clock.now(),
                recordedAt = container.clock.now(),
                sourceId = AppContainer.MANUAL_SOURCE.value,
                idempotencyKey = "meal-undo",
            ),
        )
        container.store.nutrition.upsertEntry(
            EntryDraft(
                id = "undo-entry",
                mealId = meal.id,
                foodId = null,
                localDate = today,
                zoneId = container.zone,
                happenedAt = container.clock.now(),
                recordedAt = container.clock.now(),
                quantityG = 100.0,
                foodName = "Oats",
                totals = mapOf(Nutrient.ENERGY to 379.0),
                sourceKind = "manual",
                sourceId = AppContainer.MANUAL_SOURCE.value,
                idempotencyKey = "undo-entry",
            ),
        )
        viewModel.refresh()
        waitFor("model") { viewModel.state.value.nutrition != null }
        viewModel.deleteEntry("undo-entry")
        waitFor("deleted") { container.store.nutrition.entryById("undo-entry") == null }
        viewModel.undoLast()
        waitFor("restored") { container.store.nutrition.entryById("undo-entry") != null }
        waitFor("model includes it") {
            viewModel.state.value.nutrition?.meals
                ?.any { section -> section.entries.any { it.id == "undo-entry" } } == true
        }
    }

    /**
     * `BUILD-ROADMAP.md` §B6.3 asks for a **local cache** behind the Open Food Facts
     * lookup, and the disclosure tells the person the product "works offline afterwards".
     * A barcode the phone already holds must therefore resolve without a second request:
     * one network call for the first scan, none for the second, and the same product.
     */
    @Test
    fun aCachedBarcodeResolvesWithoutCallingOpenFoodFacts() {
        seedProfile()
        compose.setContent { OpenHealthRoot(container, viewModel) }
        waitFor("loaded") { viewModel.state.value.loaded }
        openNutrition()
        waitFor("nutrition") { viewModel.state.value.nutrition != null }

        compose.onNodeWithContentDescription("Scan a barcode").performClick()
        compose.onNodeWithText("Look up barcodes").performClick()
        compose.onNodeWithText("Enter the barcode instead").performClick()
        compose.onNodeWithContentDescription("Barcode digits").performTextInput("12345678")
        compose.onNodeWithText("Look up").performClick()
        waitFor("found") {
            viewModel.state.value.barcode?.state ==
                app.openhealth.ui.screens.nutrition.BarcodeState.FOUND
        }
        assertEquals(1, transport.calls, "the first lookup is the one call that goes out")

        // The second scan of the same barcode: the cached food answers it.
        viewModel.startBarcode()
        waitFor("scanner") { viewModel.state.value.barcode != null }
        viewModel.barcodeManual()
        viewModel.barcodeLookup("12345678")
        waitFor("found from cache") {
            viewModel.state.value.barcode?.state ==
                app.openhealth.ui.screens.nutrition.BarcodeState.FOUND
        }
        assertEquals(1, transport.calls, "a cached barcode must not call the network again")
        assertEquals("Testo Bar", viewModel.state.value.barcode?.product?.name)
    }

    @Test
    fun theBundledIndexSearchesAndLogsWithNoNetwork() {
        seedProfile()
        compose.setContent { OpenHealthRoot(container, viewModel) }
        waitFor("loaded") { viewModel.state.value.loaded }
        openNutrition()
        waitFor("nutrition") { viewModel.state.value.nutrition != null }
        compose.onAllNodesWithText("Add food").onFirst().performClick()
        waitFor("search") { viewModel.state.value.foodSearch != null }
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("oats")
        waitFor("bundled results") {
            viewModel.state.value.foodSearch?.bundled?.isNotEmpty() == true
        }
        val food = viewModel.state.value.foodSearch!!.bundled.first().food
        compose.onNodeWithText(food.name).performClick()
        waitFor("portion") { viewModel.state.value.portion != null }
        compose.onNodeWithText("Log").performClick()
        waitFor("entry stored") { container.store.nutrition.countEntries() == 1L }
        val today = container.clock.now().toLocalDateTime(container.zone).date
        assertEquals("usda", container.store.nutrition.entriesForDay(today).single().sourceKind)
        assertEquals(0, transport.calls, "the local search must not call the network")
    }

    /**
     * `DESIGN.md` §14.3 budgets water at one tap. The chip sits on the Nutrition root, so
     * it has to log without the water sheet ever being opened, and the glass has to land
     * on the day being viewed.
     */
    @Test
    fun theOneTapWaterChipLogsWithoutOpeningTheSheet() {
        seedProfile()
        compose.setContent { OpenHealthRoot(container, viewModel) }
        waitFor("loaded") { viewModel.state.value.loaded }
        openNutrition()
        waitFor("nutrition") { viewModel.state.value.nutrition != null }

        val before = viewModel.state.value.nutrition?.water?.totalMl ?: 0.0
        assertEquals(null, viewModel.state.value.waterSheet)
        compose.onNodeWithText("Log 250 ml").performClick()
        waitFor("water logged") {
            (viewModel.state.value.nutrition?.water?.totalMl ?: 0.0) >= before + 250.0
        }

        val today = container.clock.now().toLocalDateTime(container.zone).date
        val rollup = container.store.rollups.forDayAndType(
            today,
            app.openhealth.core.observation.ObservationType.WATER,
        )
        assertEquals(250.0, rollup!!.value.value, 1e-9)
    }

    /**
     * The Open Food Facts lookup is the app's only outbound call, and the app must declare
     * `INTERNET` itself. The timed exit test above uses a fake transport, so a missing
     * declaration would not show there: on a real build the lookup would throw and the
     * screen would show "Unavailable" for every barcode. The `play` flavour used to
     * inherit the permission from ML Kit's manifest, which left the Play-Services-free
     * `fdroid` flavour without it; this runs on whichever flavour is installed.
     */
    @Test
    fun theAppDeclaresTheInternetPermissionItselfOnEveryFlavour() {
        val declared = context.packageManager
            .getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.GET_PERMISSIONS,
            )
            .requestedPermissions
            .orEmpty()
            .toSet()
        assertTrue(
            android.Manifest.permission.INTERNET in declared,
            "the Open Food Facts lookup cannot run without INTERNET; declared: $declared",
        )
        assertEquals(
            android.content.pm.PackageManager.PERMISSION_GRANTED,
            context.checkSelfPermission(android.Manifest.permission.INTERNET),
        )
    }

    private fun seedProfile() {
        val now = container.clock.now()
        runBlocking {
            container.store.profile.upsert(
                Profile(
                    id = "profile-test",
                    name = "Sam",
                    date_of_birth = "1990-04-12",
                    sex_at_birth = "FEMALE",
                    gender = null,
                    height_cm = 170.0,
                    weight_kg = 80.0,
                    units = "metric",
                    zone_id = container.zone.id,
                    language = "en",
                    goal = "just_track",
                    activity_level = "MODERATELY_ACTIVE",
                    training_experience = "BEGINNER",
                    weight_neutral = false,
                    intake_tier = 1L,
                    created_at = now.toEpochMilliseconds(),
                    updated_at = now.toEpochMilliseconds(),
                    source_id = AppContainer.MANUAL_SOURCE.value,
                    revision = 1L,
                ),
            )
            container.preferences.setModules(HealthModule.entries.toSet())
            container.preferences.markOnboardingComplete(1)
        }
        viewModel.refresh()
        val deadline = System.currentTimeMillis() + 30_000
        while (!viewModel.state.value.onboardingComplete &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(50)
        }
    }

    /** The tab bar is composed after `loaded`; under load the click can race it. */
    private fun openNutrition() {
        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodesWithContentDescription("Nutrition").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Nutrition").performClick()
    }

    private fun waitFor(what: String, timeoutMillis: Long = 30_000, condition: () -> Boolean) {
        val reached = runCatching {
            compose.waitUntil(timeoutMillis = timeoutMillis, condition = condition)
        }.isSuccess
        assertTrue(reached, "timed out waiting for $what")
    }

    private class FakeTransport : HttpTransport {
        var calls = 0

        override suspend fun get(request: HttpRequest): HttpResponse {
            calls += 1
            return HttpResponse(200, PRODUCT)
        }
    }

    private companion object {
        const val TEST_DATABASE = "nutrition-exit.db"
        const val TEST_VAULT = "openhealth.vault.b6-test"
        val PRODUCT = """
            {
              "status": 1,
              "product": {
                "code": "12345678",
                "product_name": "Testo Bar",
                "brands": "Test",
                "serving_quantity": "40",
                "serving_size": "40 g",
                "nutriments": {
                  "energy-kcal_100g": 200,
                  "proteins_100g": 10,
                  "carbohydrates_100g": 20,
                  "fat_100g": 8,
                  "sodium_100g": 0.1
                },
                "allergens_tags": ["en:milk"],
                "ingredients_analysis_tags": ["en:vegetarian"]
              }
            }
        """.trimIndent()
    }
}
