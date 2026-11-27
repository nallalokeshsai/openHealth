package app.openhealth.android

import android.content.Context
import android.net.Uri
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.openhealth.android.documents.DocumentStore
import app.openhealth.android.ui.AppViewModel
import app.openhealth.android.ui.OpenHealthRoot
import app.openhealth.core.observation.Observation
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.observation.Provenance
import app.openhealth.core.preferences.HealthModule
import app.openhealth.core.preferences.UnitSystem
import app.openhealth.core.time.TimeProvenance
import app.openhealth.core.units.Measure
import app.openhealth.core.units.UcumUnit
import app.openhealth.data.db.Profile
import app.openhealth.ui.screens.onboarding.OnboardingStep
import app.openhealth.ui.screens.today.QuickAction
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The B3 exit scenarios, driven through the real screens, the real state holder and the
 * encrypted database on the phone. Maestro is not installed on the build machine; this
 * is the substitute and it is recorded as such in `docs/build-log.md`.
 */
@RunWith(AndroidJUnit4::class)
class ShellExitTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: Context
    private lateinit var container: AppContainer
    private lateinit var viewModel: AppViewModel

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DATABASE)
        context.getSharedPreferences(TEST_VAULT, Context.MODE_PRIVATE).edit().clear().commit()
        container = AppContainer(
            context,
            TempDocumentStore,
            databaseName = TEST_DATABASE,
            vaultPreferences = TEST_VAULT,
        )
        viewModel = AppViewModel(container, UnitSystem.METRIC)
    }

    @After
    fun tearDown() {
        viewModel.stop()
        // A rebuild may have been in flight on the IO dispatcher; let it observe the
        // cancellation before the driver closes.
        Thread.sleep(300)
        container.close()
        context.deleteDatabase(TEST_DATABASE)
        context.getSharedPreferences(TEST_VAULT, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun freshInstallReachesTheFirstNoteInUnderThreeMinutes() {
        val started = System.nanoTime()
        compose.setContent { OpenHealthRoot(container, viewModel) }
        waitFor("loaded") { viewModel.state.value.loaded }

        compose.onNodeWithText("Start").performClick()
        typeAndContinue("Sam")
        typeAndContinue("1990-04-12")
        compose.onNodeWithText("Female").performClick()
        advance()
        typeAndContinue("180")
        typeAndContinue("80")
        advance()
        waitFor("goal") { viewModel.state.value.onboarding.step == OnboardingStep.Goal }
        compose.onNodeWithText("Just track").performClick()
        advance()
        waitFor("weight neutral") {
            viewModel.state.value.onboarding.step == OnboardingStep.WeightNeutral
        }
        advance()
        waitFor("tier two") {
            viewModel.state.value.onboarding.step == OnboardingStep.TierTwoIntro
        }
        compose.onNodeWithText("Fill in later").performClick()
        waitFor("tier three") {
            viewModel.state.value.onboarding.step == OnboardingStep.TierThreeIntro
        }
        compose.onNodeWithText("Fill in later").performClick()
        waitFor("finish") {
            viewModel.state.value.onboarding.step == OnboardingStep.Finish
        }
        compose.onNodeWithText("Go to Today").performClick()
        waitFor("onboarding complete") { viewModel.state.value.onboardingComplete }

        compose.onNodeWithContentDescription("Quick add").performClick()
        compose.onAllNodesWithText("Note").onLast().performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Knee felt fine today")
        waitFor("note typed") { viewModel.state.value.quickAdd.note.isNotBlank() }
        // The sheet lifts above the IME; a tap while the keyboard is still animating can
        // land on the keyboard. Dismiss it first, as a person does, then tap Save.
        hideKeyboard()
        compose.onNodeWithText("Save").performClick()
        // The sheet closes before its write is observable; asserting the sheet first
        // separates "the tap did nothing" from "the write is slow" in a failure.
        waitFor("note sheet closed") { !viewModel.state.value.quickAdd.visible }
        waitFor("note stored") { container.store.notes.count() == 1L }

        val elapsed = (System.nanoTime() - started).toDouble()
        val minutes = elapsed / 1_000_000_000.0 / 60.0
        assertTrue(minutes < 3.0, "first note took %.2f minutes".format(minutes))
    }

    @Test
    fun todayRendersTheBodyMapFromTheShippedGeometry() {
        compose.setContent { OpenHealthRoot(container, viewModel) }
        waitFor("loaded") { viewModel.state.value.loaded }
        compose.onNodeWithText("Try with sample data").performClick()
        waitFor("demo complete") { viewModel.state.value.onboardingComplete }
        // The muscle nodes exist only once OhBodyMap has parsed the packaged geometry;
        // before that the card shows its reserved placeholder. This is the regression
        // test for a blank map on Today.
        waitFor("body map") {
            compose.onAllNodesWithContentDescription("Chest").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            compose.onAllNodesWithContentDescription("Chest").fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(
            compose.onAllNodesWithContentDescription("Quadriceps")
                .fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun seventeenYearOldIsNotOfferedAWeightLossGoal() {
        compose.setContent { OpenHealthRoot(container, viewModel) }
        waitFor("loaded") { viewModel.state.value.loaded }
        compose.onNodeWithText("Start").performClick()
        typeAndContinue("Ana")
        typeAndContinue("2009-06-15")
        compose.onNodeWithText("Female").performClick()
        advance()
        typeAndContinue("170")
        typeAndContinue("70")
        advance()
        waitFor("goal") { viewModel.state.value.onboarding.step == OnboardingStep.Goal }
        assertFalse(viewModel.state.value.onboarding.weightLossAllowed)
        compose.onNodeWithText("Lose fat").assertDoesNotExist()
        compose.onNodeWithText("Just track").assertExists()
    }

    @Test
    fun underSixteenIsBlockedBeforeAnythingIsSaved() {
        compose.setContent { OpenHealthRoot(container, viewModel) }
        waitFor("loaded") { viewModel.state.value.loaded }
        compose.onNodeWithText("Start").performClick()
        typeAndContinue("Sam")
        typeAndContinue("2015-01-01")
        waitFor("blocked") { viewModel.state.value.onboarding.ageBlocked }
        compose.onNodeWithText("openHealth is for people aged 16 and over").assertExists()
        assertEquals(0L, container.store.profile.all().size.toLong())
    }

    @Test
    fun quickAddWeightReplacesTheDaysWeight() {
        seedProfile()
        viewModel.refresh()
        compose.setContent { OpenHealthRoot(container, viewModel) }
        waitFor("loaded") { viewModel.state.value.loaded }
        waitFor("profile") { viewModel.state.value.onboardingComplete }
        viewModel.openQuickAdd(QuickAction.WEIGHT)
        waitFor("weight form") {
            viewModel.state.value.quickAdd.mode ==
                app.openhealth.ui.screens.quickadd.QuickAddMode.WEIGHT
        }
        compose.onNode(hasSetTextAction()).performTextInput("81")
        closeKeyboard()
        compose.onNodeWithText("Save").performClick()
        waitFor("weight 81") {
            container.store.rollups.forDayAndType(container.today, ObservationType.WEIGHT)
                ?.value?.value == 81.0
        }
        waitFor("sheet closed") { !viewModel.state.value.quickAdd.visible }
        viewModel.openQuickAdd(QuickAction.WEIGHT)
        waitFor("weight form again") {
            viewModel.state.value.quickAdd.mode ==
                app.openhealth.ui.screens.quickadd.QuickAddMode.WEIGHT &&
                viewModel.state.value.quickAdd.weight.isEmpty()
        }
        compose.onNode(hasSetTextAction()).performTextInput("82")
        closeKeyboard()
        compose.onNodeWithText("Save").performClick()
        waitFor("weight 82") {
            container.store.rollups.forDayAndType(container.today, ObservationType.WEIGHT)
                ?.value?.value == 82.0
        }
    }

    @Test
    fun waterGlassesAddUpAndABackdatedGlassIsNotLost() {
        seedProfile()
        compose.setContent { OpenHealthRoot(container, viewModel) }
        waitFor("loaded") { viewModel.state.value.loaded }
        waitFor("shell") { viewModel.state.value.onboardingComplete }

        viewModel.openQuickAdd(QuickAction.WATER)
        waitFor("water form") {
            viewModel.state.value.quickAdd.mode ==
                app.openhealth.ui.screens.quickadd.QuickAddMode.WATER
        }
        compose.onNodeWithText("Save").performClick()
        waitFor("first glass") { waterToday() == 250.0 }
        waitFor("sheet closed") { !viewModel.state.value.quickAdd.visible }

        // The backdate strip never offers a moment that has not happened yet.
        viewModel.openQuickAdd(QuickAction.WATER)
        waitFor("water form again") {
            viewModel.state.value.quickAdd.mode ==
                app.openhealth.ui.screens.quickadd.QuickAddMode.WATER
        }
        viewModel.onQuickAddWhen(container.today, LAST_HOUR)
        val nowHour = container.clock.now().toLocalDateTime(container.zone).hour
        waitFor("hour clamped to now") {
            viewModel.state.value.quickAdd.whenHour == nowHour
        }

        // A glass logged for an earlier hour of the same day adds to the day.
        viewModel.onQuickAddWhen(container.today, 0)
        waitFor("backdated") { viewModel.state.value.quickAdd.whenHour == 0 }
        compose.onNodeWithText("Save").performClick()
        waitFor("two glasses") { waterToday() == 500.0 }

        // Deleting one glass takes exactly that glass off the day.
        val glass = viewModel.state.value.timeline?.days.orEmpty()
            .flatMap { it.rows }
            .first { it.kind == app.openhealth.ui.screens.timeline.TimelineKind.WATER }
        viewModel.deleteTimelineRow(glass)
        waitFor("one glass left") { waterToday() == 250.0 }
    }

    private fun waterToday(): Double? = container.store.rollups
        .forDayAndType(container.today, ObservationType.WATER)?.value?.value

    @Test
    fun turningAModuleOffHidesItAndReenablingRestoresItsData() {
        seedProfile()
        compose.setContent { OpenHealthRoot(container, viewModel) }
        waitFor("loaded") { viewModel.state.value.loaded }

        compose.onNodeWithContentDescription("Quick add").performClick()
        compose.onAllNodesWithText("Water").onLast().performClick()
        waitFor("water form") {
            viewModel.state.value.quickAdd.mode ==
                app.openhealth.ui.screens.quickadd.QuickAddMode.WATER
        }
        compose.onNodeWithText("Save").performClick()
        waitFor("water stored") { container.store.observations.count() == 1L }
        waitFor("quick add closed") { !viewModel.state.value.quickAdd.visible }

        // Nutrition off: the water tile and the energy card disappear.
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Modules").performClick()
        compose.onNodeWithContentDescription("Nutrition").performClick()
        compose.onNodeWithText("Close").performClick()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitFor("nutrition hidden") {
            HealthModule.NUTRITION !in viewModel.state.value.preferences.modules
        }
        compose.onAllNodesWithText("Water").assertCountEquals(0)

        // Re-enabling restores the surfaces; the observation was never deleted.
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Modules").performClick()
        compose.onNodeWithContentDescription("Nutrition").performClick()
        compose.onNodeWithText("Close").performClick()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitFor("nutrition back") {
            HealthModule.NUTRITION in viewModel.state.value.preferences.modules
        }
        compose.onAllNodesWithText("Water").assertCountEquals(1)
        assertEquals(1L, container.store.observations.count())
    }

    @Test
    fun weightNeutralHidesWeightAndCaloriesAcrossToday() {
        seedProfile()
        runBlocking {
            container.store.observations.record(
                Observation.of(
                    id = app.openhealth.core.ids.ObservationId("weight-test"),
                    type = ObservationType.WEIGHT,
                    value = Measure(80.0, UcumUnit.KILOGRAM),
                    start = container.clock.now(),
                    provenance = Provenance(
                        sourceId = AppContainer.MANUAL_SOURCE,
                        time = TimeProvenance.of(
                            container.clock.now(),
                            container.clock.now(),
                            container.zone,
                        ),
                        idempotencyKey = "weight-test",
                        method = app.openhealth.core.observation.MeasurementMethod.MANUAL_ENTRY,
                    ),
                ),
            )
            container.preferences.setWeightNeutral(true)
        }
        viewModel.refresh()
        compose.setContent { OpenHealthRoot(container, viewModel) }
        waitFor("loaded") { viewModel.state.value.loaded }
        // `loaded` is already true from the view model's own first rebuild, so waiting on
        // it alone can assert against the state from before `setWeightNeutral`. Wait for
        // the switch the assertions are about, as the nutrition case already does.
        waitFor("weight neutral") { viewModel.state.value.preferences.weightNeutral }
        compose.onNodeWithText("Trend").assertDoesNotExist()
        compose.onNodeWithText("Log weight").assertDoesNotExist()
        assertTrue(
            viewModel.state.value.timeline?.days.orEmpty()
                .flatMap { it.rows }
                .none { it.kind == app.openhealth.ui.screens.timeline.TimelineKind.WEIGHT },
        )

        viewModel.setWeightNeutral(false)
        waitFor("weight back") { !viewModel.state.value.preferences.weightNeutral }
        compose.onNodeWithText("Trend").assertExists()
    }

    @Test
    fun exportThenRestoreFromSettingsRoundTripsEveryTable() {
        seedProfile()
        runBlocking {
            container.store.notes.insert(
                app.openhealth.data.db.Note(
                    id = "note-before-export",
                    body = "Survives the round trip",
                    local_date = container.today.toString(),
                    zone_id = container.zone.id,
                    happened_at = container.clock.now().toEpochMilliseconds(),
                    recorded_at = container.clock.now().toEpochMilliseconds(),
                    edited_at = null,
                    source_id = AppContainer.MANUAL_SOURCE.value,
                    raw_record_id = null,
                    idempotency_key = "note-before-export",
                    device = "android",
                    app_version = container.versionName,
                    revision = 1L,
                ),
            )
        }
        // The Settings screen calls exactly these two seams; the SAF picker itself is
        // exercised by the manual walk-through.
        val exported = container.export.export("correct horse battery staple".toCharArray())
        container.documents.write(Uri.EMPTY, exported)
        assertNotNull(TempDocumentStore.bytes)

        runBlocking {
            container.store.notes.insert(
                app.openhealth.data.db.Note(
                    id = "note-after-export",
                    body = "Should be replaced",
                    local_date = container.today.toString(),
                    zone_id = container.zone.id,
                    happened_at = container.clock.now().toEpochMilliseconds(),
                    recorded_at = container.clock.now().toEpochMilliseconds(),
                    edited_at = null,
                    source_id = AppContainer.MANUAL_SOURCE.value,
                    raw_record_id = null,
                    idempotency_key = "note-after-export",
                    device = "android",
                    app_version = container.versionName,
                    revision = 1L,
                ),
            )
        }
        assertTrue(container.writePreRestoreSnapshot())
        container.export.restore(
            container.documents.read(Uri.EMPTY),
            "correct horse battery staple".toCharArray(),
        )
        assertEquals(1L, container.store.notes.count())
        assertEquals(
            "Survives the round trip",
            container.store.notes.byId(app.openhealth.core.ids.NoteId("note-before-export"))?.body,
        )
        assertTrue(container.store.profile.active() != null)

        // The restore took a local snapshot first; the same seam restores it.
        assertTrue(container.hasPreRestoreSnapshot())
        assertTrue(container.restorePreRestoreSnapshot())
        assertEquals(2L, container.store.notes.count())
    }

    @Test
    fun theSettingsRestorePathRebuildsTheRecordAndCanBeUndone() {
        seedProfile()
        TempDocumentStore.bytes = null
        compose.setContent { OpenHealthRoot(container, viewModel) }
        waitFor("loaded") { viewModel.state.value.loaded }
        waitFor("shell") { viewModel.state.value.onboardingComplete }

        // One note and one weight, so the restore has both a row and a rollup to rebuild.
        viewModel.openQuickAdd(QuickAction.WEIGHT)
        waitFor("weight form") {
            viewModel.state.value.quickAdd.mode ==
                app.openhealth.ui.screens.quickadd.QuickAddMode.WEIGHT
        }
        compose.onNode(hasSetTextAction()).performTextInput("77")
        closeKeyboard()
        hideKeyboard()
        compose.onNodeWithText("Save").performClick()
        waitFor("weight stored") {
            container.store.rollups
                .forDayAndType(container.today, ObservationType.WEIGHT)?.value?.value == 77.0
        }
        waitFor("sheet closed") { !viewModel.state.value.quickAdd.visible }

        // The Settings seams, in the order the screen calls them.
        viewModel.export(Uri.EMPTY, "correct horse battery staple".toCharArray())
        waitFor("exported", timeoutMillis = CIPHER_TIMEOUT_MS) {
            TempDocumentStore.bytes != null
        }

        viewModel.openQuickAdd(QuickAction.WEIGHT)
        waitFor("weight form again") {
            viewModel.state.value.quickAdd.mode ==
                app.openhealth.ui.screens.quickadd.QuickAddMode.WEIGHT
        }
        compose.onNode(hasSetTextAction()).performTextInput("90")
        closeKeyboard()
        compose.onNodeWithText("Save").performClick()
        waitFor("weight replaced") {
            container.store.rollups
                .forDayAndType(container.today, ObservationType.WEIGHT)?.value?.value == 90.0
        }
        waitFor("sheet closed again") { !viewModel.state.value.quickAdd.visible }

        viewModel.restore(Uri.EMPTY, "correct horse battery staple".toCharArray())
        waitFor("restored, rollups rebuilt", timeoutMillis = CIPHER_TIMEOUT_MS) {
            container.store.rollups
                .forDayAndType(container.today, ObservationType.WEIGHT)?.value?.value == 77.0
        }
        assertTrue(container.hasPreRestoreSnapshot())

        viewModel.undoLastRestore()
        waitFor("restore undone", timeoutMillis = CIPHER_TIMEOUT_MS) {
            container.store.rollups
                .forDayAndType(container.today, ObservationType.WEIGHT)?.value?.value == 90.0
        }
        assertFalse(container.hasPreRestoreSnapshot())
    }

    @Test
    fun aSeventeenYearOldProfileCannotBeSavedWithAWeightLossGoal() {
        seedProfile()
        compose.setContent { OpenHealthRoot(container, viewModel) }
        waitFor("loaded") { viewModel.state.value.loaded }
        waitFor("shell") { viewModel.state.value.onboardingComplete }
        val seventeen = container.today.minus(kotlinx.datetime.DatePeriod(years = 17))
        viewModel.saveProfile(
            app.openhealth.ui.screens.settings.ProfileEdit(
                name = "Ana",
                birthDate = seventeen.toString(),
                goal = app.openhealth.core.preferences.ProfileGoal.LOSE_FAT,
                height = "170",
                weight = "70",
                units = UnitSystem.METRIC,
            ),
        )
        waitFor("saved") { viewModel.state.value.profile?.name == "Ana" }
        assertEquals(null, viewModel.state.value.profile?.goal)
        assertFalse(viewModel.state.value.settings?.profileMayLoseWeight ?: true)
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

    private fun typeAndContinue(text: String) {
        compose.onNode(hasSetTextAction()).performTextInput(text)
        closeKeyboard()
        advance()
    }

    private fun advance() {
        compose.onNodeWithText("Continue")
            .performSemanticsAction(SemanticsActions.OnClick)
    }

    private fun closeKeyboard() {
        compose.activityRule.scenario.onActivity { activity ->
            val input = activity.getSystemService(InputMethodManager::class.java)
            input.hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
        }
        compose.waitForIdle()
        Thread.sleep(500)
    }

    /** Put the IME away so a modal sheet's buttons are tappable at their settled bounds. */
    private fun hideKeyboard() {
        val imm = context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
            ?: return
        compose.runOnUiThread {
            imm.hideSoftInputFromWindow(compose.activity.window.decorView.windowToken, 0)
        }
        compose.waitForIdle()
        // The IME animates away after the hide request; tapping while it still covers
        // the sheet's Save button is what made this test flaky (B4 traced it).
        Thread.sleep(300)
    }

    private fun waitFor(what: String, timeoutMillis: Long = 30_000, condition: () -> Boolean) {
        val reached = runCatching {
            compose.waitUntil(timeoutMillis = timeoutMillis, condition = condition)
        }.isSuccess
        assertTrue(
            reached,
            "timed out waiting for $what; step=${viewModel.state.value.onboarding.step}, " +
                "issue=${viewModel.state.value.onboarding.issue}, " +
                "fatal=${viewModel.state.value.fatal}",
        )
    }

    private companion object {
        const val TEST_DATABASE = "b3-shell-test.db"
        const val TEST_VAULT = "openhealth.vault.b3-test"
    }
}

private const val LAST_HOUR = 23

/** Argon2id is deliberately slow; an encrypted round trip is several derivations. */
private const val CIPHER_TIMEOUT_MS = 180_000L

/** A document store that keeps the bytes in memory, so no system picker is needed. */
private object TempDocumentStore : DocumentStore {
    var bytes: ByteArray? = null

    override fun write(uri: Uri, bytes: ByteArray) {
        this.bytes = bytes
    }

    override fun read(uri: Uri): ByteArray = bytes ?: error("nothing exported")
}
