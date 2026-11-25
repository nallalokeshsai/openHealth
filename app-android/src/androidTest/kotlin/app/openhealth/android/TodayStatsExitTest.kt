package app.openhealth.android

import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.openhealth.android.documents.DocumentStore
import app.openhealth.android.ui.AppViewModel
import app.openhealth.android.ui.OpenHealthRoot
import app.openhealth.core.preferences.HealthModule
import app.openhealth.core.preferences.ModulePreset
import app.openhealth.core.preferences.UnitSystem
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * B9's device exit scenarios: Today's cards and their table, the readiness explanation,
 * and Stats' segments, charts and formula sheet, through the real screens and the
 * encrypted store. The demo corpus seeds the day so no value is invented by the test.
 */
@RunWith(AndroidJUnit4::class)
class TodayStatsExitTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: Context
    private lateinit var container: AppContainer
    private lateinit var viewModel: AppViewModel

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(DB)
        context.getSharedPreferences(VAULT, Context.MODE_PRIVATE).edit().clear().commit()
        container = AppContainer(
            context,
            NoDocuments,
            databaseName = DB,
            vaultPreferences = VAULT,
        )
        runBlocking {
            container.addSampleData(days = 40, profileId = PROFILE, writeProfile = true)
            container.preferences.setPreset(ModulePreset.EVERYTHING)
            container.preferences.setModules(HealthModule.entries.toSet())
            container.preferences.setWeightNeutral(false)
            container.preferences.setDemo(true)
            container.preferences.markOnboardingComplete(tier = 3)
            container.store.settings.put("goal.target_weight_kg", "78.0")
        }
        viewModel = AppViewModel(container, UnitSystem.METRIC)
    }

    @After
    fun tearDown() {
        viewModel.stop()
        Thread.sleep(300)
        container.close()
        context.deleteDatabase(DB)
        context.getSharedPreferences(VAULT, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun todayShowsTheRecipeAndOpensItsTables() {
        compose.setContent { OpenHealthRoot(container, viewModel) }
        waitFor("loaded") { viewModel.state.value.loaded }
        waitFor("Today") {
            compose.onAllNodesWithText("Readiness").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Readiness").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Steps").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Weight").onFirst().performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Energy").performScrollTo().assertIsDisplayed()

        compose.onAllNodesWithText("Show as table").onLast().performScrollTo().performClick()
        waitFor("table") {
            compose.onAllNodesWithText("Day").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("Value").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Close").performClick()
        compose.onAllNodesWithText("Readiness compares today with your own 28-day baseline.")
            .onFirst()
            .performScrollTo()
            .performClick()
        assertTrue(compose.onAllNodesWithText("Inputs").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun statsSegmentsAndFormulaSheet() {
        compose.setContent { OpenHealthRoot(container, viewModel) }
        waitFor("loaded") { viewModel.state.value.loaded }
        compose.onNodeWithContentDescription("Stats").performClick()
        waitFor("stats") {
            compose.onAllNodesWithText("Overview").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Sleep").performScrollTo().performClick()
        waitFor("sleep") {
            compose.onAllNodesWithText("Sleep, 14 nights").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithText("How this is calculated").onFirst().performClick()
        waitFor("formula") {
            compose.onAllNodesWithText("How sleep is calculated").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Training").performScrollTo().performClick()
        waitFor("training") {
            compose.onAllNodesWithText("Training consistency, 12 weeks")
                .fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("Not enough data yet").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitFor(name: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + TIMEOUT
        while (System.currentTimeMillis() < deadline) {
            compose.waitForIdle()
            if (condition()) return
            Thread.sleep(50)
        }
        throw AssertionError("Timed out waiting for $name")
    }

    private object NoDocuments : DocumentStore {
        override fun write(uri: Uri, bytes: ByteArray) = Unit

        override fun read(uri: Uri): ByteArray = error("nothing exported")
    }

    private companion object {
        const val DB = "b9-test.db"
        const val VAULT = "b9-test.vault"
        const val PROFILE = "profile-b9"
        const val TIMEOUT = 20_000L
    }
}
