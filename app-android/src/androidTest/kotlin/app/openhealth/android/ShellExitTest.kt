package app.openhealth.android

import android.content.Context
import android.net.Uri
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.openhealth.android.data.AndroidDatabaseFactory
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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
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
        val app = context.applicationContext as OpenHealthApp
        runCatching { app.container.close() }
        context.deleteDatabase(AndroidDatabaseFactory.DEFAULT_DATABASE)
        context.getSharedPreferences("openhealth.vault", Context.MODE_PRIVATE)
            .edit().clear().commit()
        container = AppContainer(context, TempDocumentStore)
        viewModel = AppViewModel(container, UnitSystem.METRIC)
    }

    @After
    fun tearDown() {
        viewModel.stop()
        // A rebuild may have been in flight on the IO dispatcher; let it observe the
        // cancellation before the driver closes.
        Thread.sleep(300)
        container.close()
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
        compose.onNodeWithText("Save").performClick()
        waitFor("note stored") { container.store.notes.count() == 1L }

        val elapsed = (System.nanoTime() - started).toDouble()
        val minutes = elapsed / 1_000_000_000.0 / 60.0
        assertTrue(minutes < 3.0, "first note took %.2f minutes".format(minutes))
    }

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
        compose.onNodeWithText("Trend").assertDoesNotExist()
        compose.onNodeWithText("Log weight").assertDoesNotExist()

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

    private fun waitFor(what: String, condition: () -> Boolean) {
        val reached = runCatching {
            compose.waitUntil(timeoutMillis = 15_000, condition = condition)
        }.isSuccess
        assertTrue(
            reached,
            "timed out waiting for $what; step=${viewModel.state.value.onboarding.step}, " +
                "issue=${viewModel.state.value.onboarding.issue}",
        )
    }
}

/** A document store that keeps the bytes in memory, so no system picker is needed. */
private object TempDocumentStore : DocumentStore {
    var bytes: ByteArray? = null

    override fun write(uri: Uri, bytes: ByteArray) {
        this.bytes = bytes
    }

    override fun read(uri: Uri): ByteArray = bytes ?: error("nothing exported")
}
