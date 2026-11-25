package app.openhealth.ui.screens.vitals

import androidx.compose.ui.graphics.ImageBitmap
import app.openhealth.ui.body.BodyMapModel
import app.openhealth.core.body.MeasurementSite
import app.openhealth.core.citation.CitationKey
import app.openhealth.core.observation.ObservationContext
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.vitals.BpProtocolState
import app.openhealth.core.vitals.RangePosition
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/** Where a number came from, for the provenance sheet every value opens (`DESIGN.md` §7.2). */
data class ValueProvenance(
    val sourceLabel: String,
    val method: String? = null,
    val happenedAt: Instant,
    val recordedAt: Instant,
    val localDate: LocalDate,
    val zoneId: String,
    val device: String? = null,
    val quality: String? = null,
    val edited: Boolean = false,
    val rawRecord: Boolean = false,
    val valueText: String,
    val trendText: String? = null,
)

data class TrendPoint(val date: LocalDate, val value: Double)

/** The Vitals lead card: the latest weight, its trend sentence, the 90-day line and goal. */
data class WeightCardModel(
    val latestKg: Double,
    val latestDate: LocalDate,
    val changeKg: Double? = null,
    val changeDays: Int = 0,
    val weeklyRateKg: Double? = null,
    val goalKg: Double? = null,
    val points: List<TrendPoint>,
    val checkIns: Int,
    val goalSourceLabel: String? = null,
    val provenance: ValueProvenance,
)

/** One measured-site anchor on the measurement silhouette (`DESIGN.md` §9.3). */
data class MeasurementAnchorModel(val site: MeasurementSite, val measured: Boolean)

/**
 * The dynamic body model (`DESIGN.md` §9.1.1): the current figure, an older layer for the
 * scrub, and the anchors that open a tape field. The adipose layer is already null when
 * weight-neutral mode hides it; the muscles stay drawn.
 */
data class BodyFigureModel(
    val current: BodyMapModel,
    val before: BodyMapModel? = null,
    val beforeDate: LocalDate? = null,
    val afterDate: LocalDate? = null,
    val anchors: List<MeasurementAnchorModel> = emptyList(),
    val fatPercent: Double? = null,
    val fatLabel: String? = null,
    val fatSourceLabel: String? = null,
    val fatMethod: String? = null,
    val estimate: Boolean = false,
    val provenance: ValueProvenance? = null,
)

/** The 2-column composition grid and its source lines (`DESIGN.md` §9.2, §14.2). */
enum class CompositionKey {
    BODY_FAT,
    LEAN_MASS,
    WAIST,
    RESTING_HEART_RATE,
    BODY_WATER,
    BMI,
    FFMI,
}

data class CompositionTileModel(
    val key: CompositionKey,
    val valueText: String,
    val unitText: String?,
    val sourceLine: String,
    val estimate: Boolean = false,
    val provenance: ValueProvenance? = null,
)

/** One photo in the 3-across gallery (`DESIGN.md` §9.4). */
data class PhotoTileModel(
    val id: String,
    val date: LocalDate,
    val caption: String?,
    val thumbnail: ImageBitmap?,
    val provenance: ValueProvenance? = null,
)

data class PhotoCompareModel(
    val beforeId: String,
    val afterId: String,
    val beforeDate: LocalDate,
    val afterDate: LocalDate,
    val before: ImageBitmap,
    val after: ImageBitmap,
    val daysApart: Int,
)

/** One circumference row in the list under the silhouette (§9.3). */
data class MeasurementRowModel(
    val site: MeasurementSite,
    val valueText: String,
    val unitText: String,
    val date: LocalDate,
    val sourceLine: String,
    val provenance: ValueProvenance,
)

/** A grouped vital row on the root: the latest reading, its context and its range. */
data class VitalGroupModel(
    val type: ObservationType,
    val latestText: String,
    val unitText: String,
    val date: LocalDate?,
    val count: Int,
    val contextText: String?,
    val position: RangePosition?,
    val citationLine: String?,
    val sourceLabel: String?,
)

/** One stop of the clinical strip; the labels are the design's words (§8.2.1). */
enum class ScaleLabel { LOW, NORMAL, RAISED, HIGH }

data class ScaleBandModel(
    val label: ScaleLabel,
    val from: Double?,
    val to: Double?,
    val citation: CitationKey,
) {
    fun contains(value: Double): Boolean =
        (from == null || value >= from) && (to == null || value < to)
}

data class ScaleModel(
    val bands: List<ScaleBandModel>,
    val domainLow: Double,
    val domainHigh: Double,
) {
    fun fractionOf(value: Double): Float {
        val span = (domainHigh - domainLow).takeIf { it > 0.0 } ?: 1.0
        return ((value - domainLow) / span).toFloat().coerceIn(0f, 1f)
    }

    fun bandAt(value: Double): ScaleBandModel? = bands.firstOrNull { it.contains(value) }
}

/** The published range line and strip a detail screen draws (`DESIGN.md` §7.5, §8.2.1). */
data class ScaleReadingModel(
    val label: String,
    val value: Double,
    val scale: ScaleModel,
)

data class RangeUiModel(
    val statement: String,
    val population: String,
    val boundsText: String,
    val citation: CitationKey,
    val citationLabel: String,
    val position: RangePosition? = null,
    val scale: ScaleModel? = null,
)

data class TrendSeriesModel(
    val label: String?,
    val points: List<TrendPoint>,
    val role: SeriesRole,
)

enum class SeriesRole { SYSTOLIC, DIASTOLIC, SINGLE }

data class TrendChartModel(
    val series: List<TrendSeriesModel>,
    val unitText: String,
    val referenceLow: Double? = null,
    val referenceHigh: Double? = null,
    val goal: Double? = null,
    val description: String,
    val decimals: Int = 1,
)

data class VitalReadingModel(
    val id: String,
    val values: List<Double>,
    val unitText: String,
    val contextText: String?,
    val happenedAt: Instant,
    val provenance: ValueProvenance,
    /** The protocol slot this reading belongs to, for the home-BP grid. */
    val homeSlot: String? = null,
)

data class ContextFilterModel(val context: ObservationContext?, val label: String, val selected: Boolean)

data class BpProtocolModel(
    val state: BpProtocolState,
    val averageSystolic: Double? = null,
    val averageDiastolic: Double? = null,
    val averageReadings: Int = 0,
    val active: Boolean = false,
)

/** The Vitals detail page (`DESIGN.md` §14.2 Vitals detail). */
data class VitalDetailModel(
    val type: ObservationType,
    val title: String,
    val latestText: String,
    val unitText: String,
    val averageText: String,
    val count: Int,
    val range: RangeUiModel?,
    val scaleValues: List<ScaleReadingModel> = emptyList(),
    val chart: TrendChartModel?,
    val contexts: List<ContextFilterModel> = emptyList(),
    val readings: List<VitalReadingModel>,
    val protocol: BpProtocolModel? = null,
    val tableRows: List<List<String>> = emptyList(),
    val tableHeader: List<String> = emptyList(),
)

/** The whole Vitals root; no screen reads storage (`DESIGN.md` §14.2). */
data class VitalsModel(
    val today: LocalDate,
    val weight: WeightCardModel?,
    val body: BodyFigureModel?,
    val composition: List<CompositionTileModel>,
    val photos: List<PhotoTileModel>,
    val photosHidden: Boolean = false,
    val vitals: List<VitalGroupModel>,
    val measurements: List<MeasurementRowModel>,
    val frameSizes: List<MeasurementRowModel>,
    val bodyModuleOn: Boolean,
    val vitalsModuleOn: Boolean,
    val weightNeutral: Boolean,
    val imperial: Boolean = false,
    val demo: Boolean,
) {
    val empty: Boolean
        get() = weight == null && composition.isEmpty() && photos.isEmpty() &&
            vitals.isEmpty() && measurements.isEmpty() && frameSizes.isEmpty() && body == null
}
