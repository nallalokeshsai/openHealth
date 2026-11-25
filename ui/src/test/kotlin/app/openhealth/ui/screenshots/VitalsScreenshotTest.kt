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
import app.openhealth.core.body.AdiposeInputs
import app.openhealth.core.body.AdiposeModel
import app.openhealth.core.body.BodySex
import app.openhealth.core.body.MeasurementSite
import app.openhealth.core.citation.CitationKey
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.vitals.BpProtocolState
import app.openhealth.core.vitals.RangePosition
import app.openhealth.ui.body.BodyMapModel
import app.openhealth.ui.screens.vitals.BodyFigureModel
import app.openhealth.ui.screens.vitals.BpProtocolModel
import app.openhealth.ui.screens.vitals.CompositionKey
import app.openhealth.ui.screens.vitals.CompositionTileModel
import app.openhealth.ui.screens.vitals.ContextFilterModel
import app.openhealth.ui.screens.vitals.MeasurementAnchorModel
import app.openhealth.ui.screens.vitals.MeasurementRowModel
import app.openhealth.ui.screens.vitals.PhotoTileModel
import app.openhealth.ui.screens.vitals.RangeUiModel
import app.openhealth.ui.screens.vitals.ScaleBandModel
import app.openhealth.ui.screens.vitals.ScaleLabel
import app.openhealth.ui.screens.vitals.ScaleModel
import app.openhealth.ui.screens.vitals.ScaleReadingModel
import app.openhealth.ui.screens.vitals.SeriesRole
import app.openhealth.ui.screens.vitals.TrendChartModel
import app.openhealth.ui.screens.vitals.TrendPoint
import app.openhealth.ui.screens.vitals.TrendSeriesModel
import app.openhealth.ui.screens.vitals.ValueProvenance
import app.openhealth.ui.screens.vitals.VitalDetailModel
import app.openhealth.ui.screens.vitals.VitalDetailScreen
import app.openhealth.ui.screens.vitals.VitalGroupModel
import app.openhealth.ui.screens.vitals.VitalReadingModel
import app.openhealth.ui.screens.vitals.VitalsModel
import app.openhealth.ui.screens.vitals.VitalsRootScreen
import app.openhealth.ui.screens.vitals.WeightCardModel
import app.openhealth.ui.theme.OhTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The B7 Vitals screens at full size in dark, light, 200 % text and RTL
 * (`DESIGN.md` §14.5). The body model is drawn with `synchronousLayers` so the adipose
 * band is deterministic and the golden never races it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-mdpi")
class VitalsScreenshotTest {
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

    private val geometry by lazy {
        app.openhealth.ui.body.BodyGeometry.load(
            org.robolectric.RuntimeEnvironment.getApplication(),
        )
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

    private val provenance = ValueProvenance(
        sourceLabel = "Manual",
        method = "Entered by you",
        happenedAt = Instant.parse("2026-11-24T07:30:00Z"),
        recordedAt = Instant.parse("2026-11-24T07:31:00Z"),
        localDate = LocalDate(2026, 11, 24),
        zoneId = "Europe/Berlin",
        valueText = "81.6 kg",
    )

    private fun bodyMap(): BodyMapModel {
        val inputs = AdiposeInputs(
            sex = BodySex.MALE,
            bodyFatPercent = 22.0,
            heightCm = 180.0,
            tapeCm = mapOf("waist" to 86.0, "neck" to 38.0, "chest" to 100.0),
        )
        return BodyMapModel.recovery(
            sex = BodySex.MALE,
            states = emptyMap(),
            adipose = AdiposeModel.compute(inputs),
        )
    }

    private fun vitals(weightNeutral: Boolean = false, empty: Boolean = false): VitalsModel {
        if (empty) {
            return VitalsModel(
                today = LocalDate(2026, 11, 25),
                weight = null,
                body = null,
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
        val points = (0 until 12).map {
            TrendPoint(
                LocalDate(2026, 9, 1).plusDays(it * 7),
                83.4 - it * 0.16,
            )
        }
        val weight = if (weightNeutral) {
            null
        } else {
            WeightCardModel(
                latestKg = 82.4,
                latestDate = LocalDate(2026, 11, 24),
                changeKg = -0.6,
                changeDays = 30,
                weeklyRateKg = -0.22,
                goalKg = 78.0,
                points = points,
                checkIns = 12,
                provenance = provenance,
            )
        }
        val composition = if (weightNeutral) {
            emptyList()
        } else {
            listOf(
                CompositionTileModel(
                    CompositionKey.BODY_FAT,
                    "22.0",
                    "%",
                    "Scale",
                    provenance = provenance,
                ),
                CompositionTileModel(
                    CompositionKey.LEAN_MASS,
                    "64.3",
                    "kg",
                    "Calculated from your weight and body fat",
                    provenance = provenance,
                ),
                CompositionTileModel(
                    CompositionKey.WAIST,
                    "86.0",
                    "cm",
                    "Entered by you",
                    provenance = provenance,
                ),
                CompositionTileModel(
                    CompositionKey.RESTING_HEART_RATE,
                    "58",
                    "bpm",
                    "Entered by you",
                    provenance = provenance,
                ),
                CompositionTileModel(
                    CompositionKey.BMI,
                    "25.4",
                    null,
                    "Weight and height",
                    provenance = provenance,
                ),
                CompositionTileModel(
                    CompositionKey.FFMI,
                    "19.9",
                    null,
                    "Weight, height and body fat",
                    provenance = provenance,
                ),
            )
        }
        val body = if (weightNeutral) {
            bodyMap().copy(adipose = null)
        } else {
            bodyMap()
        }
        return VitalsModel(
            today = LocalDate(2026, 11, 25),
            weight = weight,
            body = BodyFigureModel(
                current = body,
                before = if (weightNeutral) null else bodyMap(),
                beforeDate = LocalDate(2026, 9, 1),
                afterDate = LocalDate(2026, 11, 24),
                anchors = MeasurementSite.anchors.map {
                    MeasurementAnchorModel(it, it == MeasurementSite.WAIST)
                },
                fatPercent = if (weightNeutral) null else 22.0,
                fatSourceLabel = if (weightNeutral) null else "Scale",
                estimate = false,
                provenance = if (weightNeutral) null else provenance,
            ),
            composition = composition,
            photos = if (weightNeutral) {
                emptyList()
            } else {
                listOf(
                    PhotoTileModel("p1", LocalDate(2026, 9, 1), null, null, provenance),
                    PhotoTileModel("p2", LocalDate(2026, 10, 1), null, null, provenance),
                    PhotoTileModel("p3", LocalDate(2026, 11, 24), null, null, provenance),
                )
            },
            vitals = listOf(
                VitalGroupModel(
                    ObservationType.BLOOD_PRESSURE_SYSTOLIC,
                    "128/78",
                    "mmHg",
                    LocalDate(2026, 11, 24),
                    9,
                    "Left arm · Sitting",
                    RangePosition.WITHIN,
                    "adults, home measurement · below 135/85 mmHg",
                    "Manual",
                ),
                VitalGroupModel(
                    ObservationType.RESTING_HEART_RATE,
                    "58",
                    "bpm",
                    LocalDate(2026, 11, 24),
                    12,
                    null,
                    RangePosition.WITHIN,
                    "adults at rest · 60-100 bpm",
                    "Manual",
                ),
                VitalGroupModel(
                    ObservationType.BLOOD_GLUCOSE,
                    "5.1",
                    "mmol/L",
                    LocalDate(2026, 11, 23),
                    4,
                    "Fasting",
                    RangePosition.WITHIN,
                    "adults, fasting plasma glucose · 3.9-5.6 mmol/L",
                    "Manual",
                ),
            ),
            measurements = listOf(
                MeasurementRowModel(
                    MeasurementSite.WAIST,
                    "86.0",
                    "cm",
                    LocalDate(2026, 11, 24),
                    "Entered by you",
                    provenance,
                ),
                MeasurementRowModel(
                    MeasurementSite.CHEST,
                    "100.0",
                    "cm",
                    LocalDate(2026, 11, 24),
                    "Entered by you",
                    provenance,
                ),
            ),
            frameSizes = listOf(
                MeasurementRowModel(
                    MeasurementSite.WRIST,
                    "16.5",
                    "cm",
                    LocalDate(2026, 11, 24),
                    "Entered by you",
                    provenance,
                ),
            ),
            bodyModuleOn = true,
            vitalsModuleOn = true,
            weightNeutral = weightNeutral,
            demo = false,
        )
    }

    private fun bpDetail(): VitalDetailModel {
        val readings = (1..6).map { index ->
            VitalReadingModel(
                id = "v-$index",
                values = listOf(126.0 + index, 76.0 + index),
                unitText = "mmHg",
                contextText = "Left arm · Sitting",
                happenedAt = Instant.parse("2026-11-1${index}T07:30:00Z"),
                provenance = provenance.copy(valueText = "12${index - 2}/7${index - 2} mmHg"),
            )
        }
        return VitalDetailModel(
            type = ObservationType.BLOOD_PRESSURE_SYSTOLIC,
            title = "Blood pressure",
            latestText = "128/78",
            unitText = "mmHg",
            averageText = "126/76",
            count = readings.size,
            range = RangeUiModel(
                statement = "below 135/85",
                population = "adults, home measurement",
                boundsText = "90-135 mmHg",
                citation = CitationKey.ESH_HYPERTENSION_2023,
                citationLabel = "ESH 2023 · European Society of Hypertension",
            ),
            scaleValues = listOf(
                ScaleReadingModel(
                    "Systolic",
                    128.0,
                    ScaleModel(
                        bands = listOf(
                            ScaleBandModel(
                                ScaleLabel.LOW,
                                null,
                                90.0,
                                CitationKey.ESH_HYPERTENSION_2023,
                            ),
                            ScaleBandModel(
                                ScaleLabel.NORMAL,
                                90.0,
                                135.0,
                                CitationKey.ESH_HYPERTENSION_2023,
                            ),
                            ScaleBandModel(
                                ScaleLabel.RAISED,
                                135.0,
                                140.0,
                                CitationKey.ESH_HYPERTENSION_2023,
                            ),
                            ScaleBandModel(
                                ScaleLabel.HIGH,
                                140.0,
                                null,
                                CitationKey.ESH_HYPERTENSION_2023,
                            ),
                        ),
                        domainLow = 65.0,
                        domainHigh = 165.0,
                    ),
                ),
                ScaleReadingModel(
                    "Diastolic",
                    78.0,
                    ScaleModel(
                        bands = listOf(
                            ScaleBandModel(
                                ScaleLabel.LOW,
                                null,
                                60.0,
                                CitationKey.ESH_HYPERTENSION_2023,
                            ),
                            ScaleBandModel(
                                ScaleLabel.NORMAL,
                                60.0,
                                85.0,
                                CitationKey.ESH_HYPERTENSION_2023,
                            ),
                            ScaleBandModel(
                                ScaleLabel.RAISED,
                                85.0,
                                90.0,
                                CitationKey.ESH_HYPERTENSION_2023,
                            ),
                            ScaleBandModel(
                                ScaleLabel.HIGH,
                                90.0,
                                null,
                                CitationKey.ESH_HYPERTENSION_2023,
                            ),
                        ),
                        domainLow = 45.0,
                        domainHigh = 105.0,
                    ),
                ),
            ),
            chart = TrendChartModel(
                series = listOf(
                    TrendSeriesModel(
                        "Systolic",
                        readings.reversed().mapIndexed { index, reading ->
                            TrendPoint(LocalDate(2026, 11, 1).plusDays(index), reading.values[0])
                        },
                        SeriesRole.SYSTOLIC,
                    ),
                    TrendSeriesModel(
                        "Diastolic",
                        readings.reversed().mapIndexed { index, reading ->
                            TrendPoint(LocalDate(2026, 11, 1).plusDays(index), reading.values[1])
                        },
                        SeriesRole.DIASTOLIC,
                    ),
                ),
                unitText = "mmHg",
                description = "Blood pressure, six readings over three weeks.",
            ),
            contexts = listOf(
                ContextFilterModel(null, "All", true),
                ContextFilterModel(
                    app.openhealth.core.observation.ObservationContext.LEFT_ARM,
                    "Left arm",
                    false,
                ),
            ),
            readings = readings,
            protocol = BpProtocolModel(
                today = LocalDate(2026, 11, 25),
                state = BpProtocolState(
                    startDate = LocalDate(2026, 11, 18),
                    days = emptyList(),
                    targetPerSlot = 2,
                    recorded = 8,
                    target = 28,
                    complete = false,
                ),
                active = false,
            ),
            tableHeader = listOf("Date", "Value", "Context"),
            tableRows = readings.map {
                listOf(it.provenance.localDate.toString(), "128/78 mmHg", "Left arm")
            },
        )
    }

    @Test
    fun vitalsRoot() {
        shot("vitals-root") {
            VitalsRootScreen(
                model = vitals(),
                onLogWeight = {},
                onOpenDetail = {},
                onSaveReading = {},
                onSaveMeasurement = { _, _, _ -> },
                onAddPhoto = {},
                onOpenPhoto = {},
                onDeletePhoto = {},
                onPhotosHidden = {},
                onPhotosInExport = {},
                onExportCsv = {},
                onImportCsv = {},
                onOpenSettings = {},
                synchronousLayers = true,
                geometry = geometry,
            )
        }
    }

    @Test
    fun vitalsRootEmpty() {
        shot("vitals-empty") {
            VitalsRootScreen(
                model = vitals(empty = true),
                onLogWeight = {},
                onOpenDetail = {},
                onSaveReading = {},
                onSaveMeasurement = { _, _, _ -> },
                onAddPhoto = {},
                onOpenPhoto = {},
                onDeletePhoto = {},
                onPhotosHidden = {},
                onPhotosInExport = {},
                onExportCsv = {},
                onImportCsv = {},
                onOpenSettings = {},
                synchronousLayers = true,
                geometry = geometry,
            )
        }
    }

    @Test
    fun vitalsRootNeutral() {
        shot("vitals-neutral") {
            VitalsRootScreen(
                model = vitals(weightNeutral = true),
                onLogWeight = {},
                onOpenDetail = {},
                onSaveReading = {},
                onSaveMeasurement = { _, _, _ -> },
                onAddPhoto = {},
                onOpenPhoto = {},
                onDeletePhoto = {},
                onPhotosHidden = {},
                onPhotosInExport = {},
                onExportCsv = {},
                onImportCsv = {},
                onOpenSettings = {},
                synchronousLayers = true,
                geometry = geometry,
            )
        }
    }

    @Test
    fun bloodPressureDetail() {
        shot("vitals-detail-bp") {
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

    private fun LocalDate.plusDays(days: Int): LocalDate =
        this + kotlinx.datetime.DatePeriod(days = days)
}

private const val SHOT = "vitals-shot"
