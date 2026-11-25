package app.openhealth.ui.screens.vitals

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.openhealth.core.body.BodySex
import app.openhealth.core.citation.CitationKey
import app.openhealth.core.observation.ObservationType
import app.openhealth.ui.body.BodyMapModel
import app.openhealth.ui.theme.OhTheme
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The B7 interaction and wording assertions: a BP reading shows its cited range without a
 * verdict, a number opens its provenance, and weight-neutral mode removes the value
 * instead of blurring it (`BUILD-ROADMAP.md` §B7, `DESIGN.md` §7.5-7.6).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-mdpi")
class VitalsSemanticsTest {
    @get:Rule
    val compose = createComposeRule()

    private val provenance = ValueProvenance(
        sourceLabel = "Manual",
        method = "Entered by you",
        happenedAt = Instant.parse("2026-11-24T07:30:00Z"),
        recordedAt = Instant.parse("2026-11-24T07:31:00Z"),
        localDate = LocalDate(2026, 11, 24),
        zoneId = "Europe/Berlin",
        valueText = "82.4 kg",
    )

    @Test
    fun aBloodPressureReadingShowsItsRangeAndCitation() {
        compose.setContent {
            OhTheme(dark = true) {
                Box(Modifier.fillMaxSize()) {
                    VitalDetailScreen(
                        model = bpDetail(),
                        imperial = false,
                        onBack = {},
                        onSaveReading = {},
                        onDeleteReading = {},
                        onSelectContext = {},
                        onStartProtocol = {},
                    )
                }
            }
        }
        compose.onNodeWithText("below 135/85 mmHg", substring = true).assertIsDisplayed()
        compose.onNodeWithText("ESH 2023", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Inside the published range", substring = true).assertIsDisplayed()
        listOf("risk", "warning", "abnormal").forEach { word ->
            compose.onNodeWithText(word, substring = true, ignoreCase = true).assertDoesNotExist()
        }
    }

    @Test
    fun theWeightNumberOpensItsProvenanceSheet() {
        compose.setContent {
            OhTheme(dark = true) {
                Box(Modifier.fillMaxSize()) {
                    VitalsRootScreen(
                        model = rootModel(),
                        onLogWeight = {},
                        onOpenDetail = {},
                        onSaveReading = {},
                        onSaveMeasurement = { _, _ -> },
                        onAddPhoto = {},
                        onOpenPhoto = {},
                        onDeletePhoto = {},
                        onPhotosHidden = {},
                        onExportCsv = {},
                        onImportCsv = {},
                        onOpenSettings = {},
                    )
                }
            }
        }
        compose.onNodeWithText("82.4").performClick()
        compose.onNodeWithText("Where this number comes from").assertExists()
        compose.onNodeWithText("Manual").assertExists()
    }

    @Test
    fun weightNeutralRemovesTheWeightInsteadOfBlurringIt() {
        compose.setContent {
            OhTheme(dark = true) {
                Box(Modifier.fillMaxSize()) {
                    VitalsRootScreen(
                        model = rootModel().copy(
                            weight = null,
                            composition = emptyList(),
                            photos = emptyList(),
                            weightNeutral = true,
                        ),
                        onLogWeight = {},
                        onOpenDetail = {},
                        onSaveReading = {},
                        onSaveMeasurement = { _, _ -> },
                        onAddPhoto = {},
                        onOpenPhoto = {},
                        onDeletePhoto = {},
                        onPhotosHidden = {},
                        onExportCsv = {},
                        onImportCsv = {},
                        onOpenSettings = {},
                    )
                }
            }
        }
        compose.onNodeWithText("82.4").assertDoesNotExist()
        compose.onNodeWithText("Weight-neutral mode is on", substring = true).assertIsDisplayed()
    }

    @Test
    fun theBodyModelChangesWhenBodyFatChanges() {
        // The renderer draws the thickness map; the pure model is the visible difference.
        // A larger percentage can only make the adipose band thicker, never thinner.
        val thin = app.openhealth.core.body.AdiposeModel.compute(
            app.openhealth.core.body.AdiposeInputs(BodySex.MALE, bodyFatPercent = 12.0),
        )!!
        val thick = app.openhealth.core.body.AdiposeModel.compute(
            app.openhealth.core.body.AdiposeInputs(BodySex.MALE, bodyFatPercent = 30.0),
        )!!
        assertTrue(
            thick.maxThickness > thin.maxThickness,
            "a higher body-fat figure must thicken the layer",
        )
        val midpoint = app.openhealth.core.body.AdiposeModel.blend(thin, thick, 0.5)
        assertEquals(
            (thin.maxThickness + thick.maxThickness) / 2.0,
            midpoint.maxThickness,
            1e-9,
        )
        assertEquals(
            thick.maxThickness,
            app.openhealth.core.body.AdiposeModel.blend(thin, thick, 1.0).maxThickness,
            1e-9,
        )
    }

    private fun bpDetail(): VitalDetailModel = VitalDetailModel(
        type = ObservationType.BLOOD_PRESSURE_SYSTOLIC,
        title = "Blood pressure",
        latestText = "128/78",
        unitText = "mmHg",
        averageText = "126/76",
        count = 1,
        range = RangeUiModel(
            statement = "below 135/85",
            population = "adults, home measurement",
            boundsText = "90-135 mmHg",
            citation = CitationKey.ESH_HYPERTENSION_2023,
            citationLabel = "ESH 2023 · European Society of Hypertension",
            position = app.openhealth.core.vitals.RangePosition.WITHIN,
        ),
        chart = null,
        readings = listOf(
            VitalReadingModel(
                id = "v1",
                values = listOf(128.0, 78.0),
                unitText = "mmHg",
                contextText = "Left arm · Sitting",
                happenedAt = Instant.parse("2026-11-24T07:30:00Z"),
                provenance = provenance,
            ),
        ),
    )

    private fun rootModel(): VitalsModel {
        val body = BodyMapModel.recovery(
            sex = app.openhealth.core.body.BodySex.MALE,
            states = emptyMap(),
        )
        return VitalsModel(
            today = LocalDate(2026, 11, 25),
            weight = WeightCardModel(
                latestKg = 82.4,
                latestDate = LocalDate(2026, 11, 24),
                changeKg = -0.6,
                changeDays = 30,
                weeklyRateKg = -0.2,
                goalKg = 78.0,
                points = listOf(
                    TrendPoint(LocalDate(2026, 10, 24), 83.0),
                    TrendPoint(LocalDate(2026, 11, 24), 82.4),
                ),
                checkIns = 2,
                provenance = provenance,
            ),
            body = BodyFigureModel(
                current = body,
                anchors = emptyList(),
                provenance = provenance,
            ),
            composition = emptyList(),
            photos = emptyList(),
            vitals = emptyList(),
            measurements = emptyList(),
            frameSizes = emptyList(),
            bodyModuleOn = true,
            vitalsModuleOn = true,
            weightNeutral = false,
            demo = false,
        )
    }
}
