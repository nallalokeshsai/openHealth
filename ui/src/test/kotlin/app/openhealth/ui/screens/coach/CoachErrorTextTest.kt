package app.openhealth.ui.screens.coach

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.openhealth.core.ai.CoachCategory
import app.openhealth.core.ai.CoachFailureKind
import app.openhealth.core.ai.CoachProviderKind
import app.openhealth.ui.R
import app.openhealth.ui.theme.OhTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A stored coach failure is written as the failure's own stable code (`kind` or
 * `kind:finding`), while a live send failure arrives as a `coach_error_*` resource name.
 * Both have to reach the same sentence: an outage that reads "the provider returned an
 * error" tells the person the wrong thing about their own data
 * (`pr.md` §6.15 "a provider outage is a graceful, non-blocking state").
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-mdpi")
class CoachErrorTextTest {

    @get:Rule
    val compose = createComposeRule()

    private fun modelWith(errorKey: String) = CoachModel(
        enabled = true,
        configured = true,
        providerKind = CoachProviderKind.ANTHROPIC,
        model = "claude-opus-5-5",
        baseUrl = "",
        keyPresent = true,
        categories = setOf(CoachCategory.TRAINING),
        healthConnect = false,
        disclosureAccepted = true,
        messages = listOf(
            CoachMessageModel(
                id = "m1",
                role = CoachRoleUi.ERROR,
                body = errorKey,
                errorKey = errorKey,
            ),
        ),
        busy = false,
        error = null,
        status = "Ready",
    )

    private fun render(errorKey: String) {
        compose.setContent {
            OhTheme(dark = true) {
                CoachScreen(
                    model = modelWith(errorKey),
                    onBack = {},
                    onToggle = {},
                    onCategory = { _, _ -> },
                    onHealthConnect = {},
                    onProvider = { _, _, _, _ -> },
                    onClearKey = {},
                    onAcceptDisclosure = {},
                    onSend = {},
                    onAccept = {},
                    onDiscard = {},
                    onUndo = {},
                )
            }
        }
    }

    @Test
    fun aStoredOutageReadsAsAnOutageNotAsAServerError() {
        render("offline")
        val offline = RuntimeEnvironment.getApplication().getString(R.string.coach_error_offline)
        val server = RuntimeEnvironment.getApplication().getString(R.string.coach_error_server)
        compose.onNodeWithText(offline).assertExists()
        compose.onNodeWithText(server).assertDoesNotExist()
    }

    @Test
    fun aStoredFailureWithItsFindingsStillNamesTheKind() {
        render("invalid_output:bad_reps,unknown_entry")
        val invalid = RuntimeEnvironment.getApplication()
            .getString(R.string.coach_error_invalid_output)
        compose.onNodeWithText(invalid).assertExists()
    }

    @Test
    fun everyFailureKindHasItsOwnKeyAndALiveKeyIsLeftAlone() {
        CoachFailureKind.entries.forEach { kind ->
            val stored = kind.name.lowercase()
            assertEquals("coach_error_$stored", coachErrorKey(stored))
            assertEquals("coach_error_$stored", coachErrorKey("$stored:wrong_type"))
        }
        assertEquals("coach_error_no_key", coachErrorKey("coach_error_no_key"))
        assertEquals("coach_error_server", coachErrorKey(null))
        assertEquals("coach_error_server", coachErrorKey(""))
    }
}
