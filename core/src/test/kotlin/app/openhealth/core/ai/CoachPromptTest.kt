package app.openhealth.core.ai

import app.openhealth.core.observation.ObservationType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
/**
 * The request builder is the last gate before anything leaves the phone: consent,
 * weight-neutral mode and the Health Connect exclusion all apply here
 * (`pr.md` §6.15, §12.3, §6.25).
 */
class CoachPromptTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val now = "2026-11-27T08:00:00Z"

    private val dataset = CoachDataset(
        profile = CoachProfile("female", 1990, 170.0, "metric", "maintain", "strength"),
        plan = CoachPlan(
            listOf(
                CoachPlanRoutine(
                    "push",
                    "Push",
                    1,
                    listOf(
                        CoachPlanEntry(
                            "entry", "bench", "Bench press", 3, 8, null, 80.0, 2.0, 0,
                        ),
                    ),
                ),
            ),
        ),
        sessions = listOf(CoachSessionSummary("2026-11-25", "Push", 12, 4_200.0, 7.0)),
        nutrition = listOf(
            CoachDaily("2026-11-25", 2_200.0, viaHealthConnect = false),
            CoachDaily("2026-11-26", 2_100.0, viaHealthConnect = true),
        ),
        sleep = listOf(CoachDaily("2026-11-26", 7.4, viaHealthConnect = true)),
        recovery = listOf(CoachRecovery("chest", "EASING", 20.0)),
        vitals = listOf(
            CoachVital(ObservationType.WEIGHT, 74.0, "kg", now, viaHealthConnect = false),
            CoachVital(ObservationType.RESTING_HEART_RATE, 52.0, "/min", now, true),
        ),
    )

    private fun build(consent: CoachConsent, weightNeutral: Boolean = false): String =
        CoachPrompt.build(dataset, consent, weightNeutral, "Review my week.").user

    private fun array(body: String, key: String): JsonArray =
        json.parseToJsonElement(body).jsonObject.getValue(key) as JsonArray

    @Test
    fun onlyConsentedCategoriesAreSerialised() {
        val body = build(
            CoachConsent(enabled = true, categories = setOf(CoachCategory.TRAINING)),
        )
        assertTrue(body.contains("\"training\""))
        assertFalse(body.contains("\"profile\""))
        assertFalse(body.contains("\"nutrition\""))
        assertFalse(body.contains("\"sleep\""))
        assertFalse(body.contains("\"vitals\""))
        assertFalse(body.contains("\"recovery\""))
    }

    @Test
    fun healthConnectRowsAreExcludedUnlessSeparatelyOptedIn() {
        val without = build(
            CoachConsent(
                enabled = true,
                categories = setOf(CoachCategory.NUTRITION, CoachCategory.SLEEP),
            ),
        )
        assertEquals(1, array(without, "nutrition").size)
        assertEquals(0, array(without, "sleep").size)

        val with = build(
            CoachConsent(
                enabled = true,
                categories = setOf(CoachCategory.NUTRITION, CoachCategory.SLEEP),
                healthConnect = true,
            ),
        )
        assertEquals(2, array(with, "nutrition").size)
        assertEquals(1, array(with, "sleep").size)
    }

    @Test
    fun aHealthConnectVitalIsExcludedWhileAManualOneIsSent() {
        val body = build(
            CoachConsent(enabled = true, categories = setOf(CoachCategory.VITALS)),
        )
        assertTrue(body.contains("WEIGHT"))
        assertFalse(body.contains("RESTING_HEART_RATE"))
    }

    @Test
    fun weightNeutralModeDropsWeightCaloriesAndBodyFat() {
        val body = build(
            CoachConsent(
                enabled = true,
                categories = setOf(CoachCategory.NUTRITION, CoachCategory.VITALS),
            ),
            weightNeutral = true,
        )
        assertFalse(body.contains("WEIGHT"))
        assertFalse(body.contains("energy_kcal"))
        assertTrue(body.contains("\"weight_neutral\":true"))
    }

    @Test
    fun theNoteIsClippedAndCarried() {
        val long = "x".repeat(CoachPrompt.MAX_NOTE_LENGTH + 500)
        val body = CoachPrompt.build(dataset, CoachConsent(), false, long).user
        val note = json.parseToJsonElement(body).jsonObject.getValue("note").jsonPrimitive.content
        assertEquals(CoachPrompt.MAX_NOTE_LENGTH, note.length)
    }

    @Test
    fun noCategorySendsOnlyTheNoteAndTheConsentList() {
        val body = build(CoachConsent(enabled = true))
        assertTrue(body.contains("\"categories\":[]"))
        assertFalse(body.contains("Bench press"))
        assertTrue(body.contains("Review my week."))
    }
}
