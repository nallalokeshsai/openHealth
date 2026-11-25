package app.openhealth.android.body

import android.content.Context
import app.openhealth.core.body.AdiposeInputs
import app.openhealth.core.body.AdiposeModel
import app.openhealth.core.body.BodyCsv
import app.openhealth.core.body.BodyCsvRow
import app.openhealth.core.body.BodySex
import app.openhealth.core.body.CompositionMeasure
import app.openhealth.core.body.MeasurementSite
import app.openhealth.core.body.WeighIn
import app.openhealth.core.body.WeightTrendCalculator
import app.openhealth.core.citation.CitationKey
import app.openhealth.core.formula.Bmi
import app.openhealth.core.formula.Ffmi
import app.openhealth.core.formula.FormulaOutcome
import app.openhealth.core.formula.LeanMass
import app.openhealth.core.ids.IdGenerator
import app.openhealth.core.ids.ObservationId
import app.openhealth.core.ids.PhotoId
import app.openhealth.core.ids.SourceId
import app.openhealth.core.ids.TombstoneId
import app.openhealth.core.ids.VitalId
import app.openhealth.core.observation.MeasurementMethod
import app.openhealth.core.observation.Observation
import app.openhealth.core.observation.ObservationContext
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.observation.Provenance
import app.openhealth.core.observation.SourceKind
import app.openhealth.core.preferences.DisplayPolicy
import app.openhealth.core.preferences.HealthModule
import app.openhealth.core.preferences.SensitiveMetric
import app.openhealth.core.seam.ClockSeam
import app.openhealth.core.time.TimeProvenance
import app.openhealth.core.units.Measure
import app.openhealth.core.units.UcumUnit
import app.openhealth.core.vitals.BloodPressureProtocol
import app.openhealth.core.vitals.BpProtocolReading
import app.openhealth.core.vitals.BpProtocolState
import app.openhealth.core.vitals.HomeSlot
import app.openhealth.core.vitals.MealContext
import app.openhealth.core.vitals.RangePosition
import app.openhealth.core.vitals.VitalContextCode
import app.openhealth.core.vitals.VitalRanges
import app.openhealth.data.LocalStore
import app.openhealth.data.db.Body_photo
import app.openhealth.data.db.Measurement
import app.openhealth.data.db.Profile
import app.openhealth.data.db.Vital
import app.openhealth.data.repo.SettingKeys
import app.openhealth.ui.R as UiR
import app.openhealth.ui.body.BodyMapModel
import app.openhealth.ui.body.FatProvenance
import app.openhealth.ui.screens.vitals.B7_VITALS
import app.openhealth.ui.screens.vitals.BodyFigureModel
import app.openhealth.ui.screens.vitals.BpProtocolModel
import app.openhealth.ui.screens.vitals.CompositionKey
import app.openhealth.ui.screens.vitals.CompositionTileModel
import app.openhealth.ui.screens.vitals.ContextFilterModel
import app.openhealth.ui.screens.vitals.MeasurementAnchorModel
import app.openhealth.ui.screens.vitals.MeasurementRowModel
import app.openhealth.ui.screens.vitals.PhotoCompareModel
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
import app.openhealth.ui.screens.vitals.VitalDraft
import app.openhealth.ui.screens.vitals.VitalGroupModel
import app.openhealth.ui.screens.vitals.VitalReadingModel
import app.openhealth.ui.screens.vitals.VitalsModel
import app.openhealth.ui.screens.vitals.WeightCardModel
import app.openhealth.ui.screens.vitals.vitalDisplayUnit
import app.openhealth.ui.screens.vitals.vitalDisplayValue
import java.util.Locale
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * The whole Vitals surface and every write behind it (`BUILD-ROADMAP.md` §B7): weight,
 * measurements, composition, photos, the five cited vitals and their home-BP protocol.
 * It reads storage only through [LocalStore], so every write rebuilds its day through the
 * same rollup engine as the rest of the app, and it never touches `android.*` beyond the
 * string resources it prints.
 */
// The presenter owns the whole Vitals surface by design (BUILD-ROADMAP.md §B7): one
// read model, one write path, one place where a vital's context is encoded. The
// structural limits are waived here for the same reason TrainingPresenter waives
// LargeClass; the pure rules it calls are small and tested in :core.
@Suppress(
    "LargeClass",
    "TooManyFunctions",
    "LongParameterList",
    "LongMethod",
    "CyclomaticComplexMethod",
    "ReturnCount",
    "MagicNumber",
)
class BodyPresenter(
    private val context: Context,
    private val store: LocalStore,
    private val clock: ClockSeam,
    private val zone: TimeZone,
    private val ids: IdGenerator,
    private val manualSource: SourceId,
    private val importSource: SourceId,
    private val appVersion: String,
) {
    private fun now(): Instant = clock.now()
    private fun today(): LocalDate = now().toLocalDateTime(zone).date

    // ------------------------------------------------------------------ read model

    fun model(
        profile: Profile?,
        preferences: app.openhealth.android.preferences.Preferences,
        demo: Boolean,
    ): VitalsModel {
        val bodyOn = HealthModule.BODY in preferences.modules
        val vitalsOn = HealthModule.VITALS in preferences.modules
        val policy = preferences.displayPolicy
        val weightNeutral = !policy.shows(SensitiveMetric.WEIGHT)
        val imperial = profile?.units == "imperial"
        val body = if (bodyOn) buildBody(profile, policy) else null
        val weight = if (bodyOn) buildWeight(policy, imperial) else null
        val composition = if (bodyOn) buildComposition(profile, policy) else emptyList()
        val measurements = if (bodyOn) {
            measurementRows(MeasurementSite.anchors, imperial) +
                customMeasurementRows(imperial)
        } else {
            emptyList()
        }
        val frames = if (bodyOn) {
            measurementRows(MeasurementSite.frameSizes, imperial)
        } else {
            emptyList()
        }
        val photos = if (bodyOn && policy.shows(SensitiveMetric.PROGRESS_PHOTOS)) {
            photoTiles()
        } else {
            emptyList()
        }
        return VitalsModel(
            today = today(),
            weight = weight,
            body = body,
            composition = composition,
            photos = photos,
            photosHidden = photosHidden(),
            photosInExport = photosInExport(),
            vitals = if (vitalsOn) vitalGroups(imperial) else emptyList(),
            measurements = measurements,
            frameSizes = frames,
            bodyModuleOn = bodyOn,
            vitalsModuleOn = vitalsOn,
            weightNeutral = weightNeutral,
            imperial = imperial,
            demo = demo,
        )
    }

    private fun buildWeight(policy: DisplayPolicy, imperial: Boolean): WeightCardModel? {
        if (!policy.shows(SensitiveMetric.WEIGHT)) return null
        val from = now() - WeightTrendCalculator.SPARK_WINDOW_DAYS.days
        val observations = store.observations.forTypeBetween(
            ObservationType.WEIGHT,
            from,
            now().plus(1.days),
        ).filterNot { it.excludedFromTrends }
        if (observations.isEmpty()) return null
        val checkIns = observations.map {
            WeighIn(
                date = it.localDate,
                kg = it.value.value,
                happenedAtMillis = it.provenance.happenedAt.toEpochMilliseconds(),
                recordedAtMillis = it.provenance.recordedAt.toEpochMilliseconds(),
            )
        }
        val goal = store.settings.get(SettingKeys.GOAL_WEIGHT_KG)?.toDoubleOrNull()
        val trend = WeightTrendCalculator.compute(checkIns, today(), goal)
        val latest = observations.maxWithOrNull(
            compareBy({ it.provenance.happenedAt }, { it.provenance.recordedAt }),
        ) ?: return null
        val displayFactor = if (imperial) POUNDS_PER_KILOGRAM else 1.0
        val displayUnit = if (imperial) "lb" else "kg"
        val provenance = provenance(
            observation = latest,
            valueText = "${format(latest.value.value * displayFactor, 1)} $displayUnit",
            trendText = trend.weeklyRateKg?.let {
                "${format(it * displayFactor, 2)} $displayUnit a week"
            },
        )
        return WeightCardModel(
            latestKg = trend.latestKg ?: latest.value.value,
            latestDate = trend.latestDate ?: latest.localDate,
            changeKg = trend.changeKg,
            changeDays = trend.changeDays,
            weeklyRateKg = trend.weeklyRateKg,
            goalKg = goal,
            points = trend.points.map { TrendPoint(it.date, it.kg) },
            checkIns = trend.checkIns,
            goalSourceLabel = context.getString(UiR.string.vitals_composition_source_calculated),
            provenance = provenance,
        )
    }

    private fun buildBody(profile: Profile?, policy: DisplayPolicy): BodyFigureModel? {
        val sex = sexOf(profile)
        val fatAllowed = policy.shows(SensitiveMetric.BODY_FAT)
        val latest = tapeRows()
        val bodyFat = latestBodyFat()
        val inputs = AdiposeInputs(
            sex = sex,
            bodyFatPercent = if (fatAllowed) bodyFat?.percent else null,
            heightCm = profile?.height_cm,
            tapeCm = latest.mapValues { (_, row) -> row.value_ },
        )
        val adipose = if (fatAllowed) AdiposeModel.compute(inputs) else null
        if (adipose == null && !fatAllowed) {
            // Weight-neutral mode still draws the muscle figure, with no fat layer.
        }
        val provenance = if (adipose != null) {
            val sites = AdiposeModel.provenanceSites(inputs)
            val newestSite = sites.mapNotNull { latest[it] }.maxByOrNull { it.recorded_at }
            ValueProvenance(
                sourceLabel = bodyFat?.sourceLabel
                    ?: newestSite?.let { sourceLabel(it.source_id) }
                    ?: context.getString(UiR.string.vitals_provenance_manual),
                method = bodyFat?.method?.let { methodLabel(it) }
                    ?: context.getString(UiR.string.vitals_method_navy),
                happenedAt = bodyFat?.happenedAt
                    ?: newestSite?.let { Instant.fromEpochMilliseconds(it.happened_at) }
                    ?: now(),
                recordedAt = bodyFat?.recordedAt
                    ?: newestSite?.let { Instant.fromEpochMilliseconds(it.recorded_at) }
                    ?: now(),
                localDate = bodyFat?.localDate ?: newestSite?.local_date?.let(LocalDate::parse)
                    ?: today(),
                zoneId = zone.id,
                device = newestSite?.device,
                valueText = AdiposeModel.fatPercent(inputs)?.let { "${format(it, 1)} %" }
                    ?: context.getString(UiR.string.vitals_body_empty_title),
                trendText = context.getString(UiR.string.vitals_body_estimate),
            )
        } else {
            null
        }
        val current = BodyMapModel.recovery(
            sex = sex,
            states = emptyMap(),
            adipose = adipose,
            fatProvenance = provenance?.let {
                FatProvenance(
                    bodyFatPercent = bodyFat?.percent,
                    method = bodyFat?.method,
                    sourceLabel = it.sourceLabel,
                    observedAt = it.happenedAt,
                    recordedAt = it.recordedAt,
                    tapeSites = if (adipose !=
                        null
                    ) {
                        AdiposeModel.provenanceSites(inputs)
                    } else {
                        emptyList()
                    },
                )
            },
            extras = app.openhealth.ui.body.BodyMapExtras(
                sexStated = profile?.sex_at_birth == "MALE" || profile?.sex_at_birth == "FEMALE",
                sourceLabels = store.sources.all().associate { it.id.value to it.label },
            ),
        )
        val beforeState = historicalAdipose(profile, fatAllowed)
        val measured = MeasurementSite.anchors.map { site ->
            MeasurementAnchorModel(site, latestMeasurement(site.type) != null)
        }
        return BodyFigureModel(
            current = current,
            before = beforeState?.map,
            beforeDate = beforeState?.date,
            afterDate = bodyFat?.localDate ?: today(),
            anchors = measured,
            fatPercent = AdiposeModel.fatPercent(inputs),
            fatLabel = bodyFat?.label,
            fatSourceLabel = if (bodyFat == null && adipose != null) {
                context.getString(UiR.string.vitals_method_navy)
            } else {
                provenance?.sourceLabel
            },
            fatMethod = provenance?.method,
            estimate = bodyFat == null && adipose != null,
            provenance = provenance,
        )
    }

    private data class FatReading(
        val percent: Double,
        val method: MeasurementMethod?,
        val sourceLabel: String,
        val happenedAt: Instant,
        val recordedAt: Instant,
        val localDate: LocalDate,
        val label: String,
    )

    /** The newest recorded percentage, else the Navy estimate from the newest tape sites. */
    /** The newest recorded percentage, from either an observation or a measurement. */
    private fun latestBodyFat(): FatReading? {
        val observation = store.observations.latest(ObservationType.BODY_FAT_PERCENT)
            ?.takeIf { !it.excludedFromTrends }
        val measurement = store.body.latestMeasurementOfType(CompositionMeasure.BODY_FAT.type)
        val observationReading = observation?.let {
            FatReading(
                percent = it.value.value,
                method = it.provenance.method,
                sourceLabel = sourceLabel(it.provenance.sourceId.value),
                happenedAt = it.provenance.happenedAt,
                recordedAt = it.provenance.recordedAt,
                localDate = it.localDate,
                label = context.getString(UiR.string.vitals_composition_body_fat),
            )
        }
        val measurementReading = measurement?.let {
            val method = it.method?.let { name ->
                runCatching { MeasurementMethod.valueOf(name) }.getOrNull()
            }
            FatReading(
                percent = it.value_,
                method = method,
                sourceLabel = sourceLabel(it.source_id),
                happenedAt = Instant.fromEpochMilliseconds(it.happened_at),
                recordedAt = Instant.fromEpochMilliseconds(it.recorded_at),
                localDate = LocalDate.parse(it.local_date),
                label = context.getString(UiR.string.vitals_composition_body_fat),
            )
        }
        return listOfNotNull(observationReading, measurementReading)
            .maxWithOrNull(compareBy({ it.happenedAt }, { it.recordedAt }))
    }

    /**
     * The US Navy tape estimate, when no scale or lab figure exists (`pr.md` §6.8). It is
     * an estimate with no measurement method of its own, so [FatReading.method] stays null
     * and every screen tags it; it never overwrites a recorded value.
     */
    private fun navyFat(profile: Profile?): FatReading? {
        val height = profile?.height_cm ?: return null
        val tape = tapeRows()
        val inputs = AdiposeInputs(
            sex = sexOf(profile),
            heightCm = height,
            tapeCm = tape.mapValues { (_, row) -> row.value_ },
        )
        val percent = AdiposeModel.fatPercent(inputs) ?: return null
        val newest = tape.values.maxByOrNull { it.recorded_at } ?: return null
        return FatReading(
            percent = percent,
            method = null,
            sourceLabel = context.getString(UiR.string.vitals_method_navy),
            happenedAt = Instant.fromEpochMilliseconds(newest.happened_at),
            recordedAt = Instant.fromEpochMilliseconds(newest.recorded_at),
            localDate = LocalDate.parse(newest.local_date),
            label = context.getString(UiR.string.vitals_composition_body_fat),
        )
    }

    private data class HistoricalAdipose(val map: BodyMapModel, val date: LocalDate)

    /**
     * The layer at the edge of the 90-day window, for the before/after scrub. Only a
     * figure that existed at least 30 days ago is offered: a scrub across two days is
     * not a before and after.
     */
    private fun historicalAdipose(profile: Profile?, fatAllowed: Boolean): HistoricalAdipose? {
        if (!fatAllowed) return null
        val cutoff = today().minus(DatePeriod(days = 30))
        val oldFatObservation = store.observations.forTypeBetween(
            ObservationType.BODY_FAT_PERCENT,
            (today() - 120.days).atStart(),
            cutoff.plusDays(1).atStart(),
        ).lastOrNull()
        val oldFatMeasurement = store.body.measurementsForTypeBetween(
            CompositionMeasure.BODY_FAT.type,
            0L,
            cutoff.plusDays(1).atStart().toEpochMilliseconds(),
        ).lastOrNull()
        val oldObservationTime = oldFatObservation?.provenance?.happenedAt
        val oldMeasurementTime = oldFatMeasurement?.let {
            Instant.fromEpochMilliseconds(it.happened_at)
        }
        val useObservation = oldFatObservation != null &&
            (oldFatMeasurement == null || oldObservationTime!! >= oldMeasurementTime!!)
        val oldFatPercent = if (useObservation) {
            oldFatObservation?.value?.value
        } else {
            oldFatMeasurement?.value_
        }
        val oldFatDate = if (useObservation) {
            oldFatObservation?.localDate
        } else {
            oldFatMeasurement?.local_date?.let(LocalDate::parse)
        }
        val oldTape = MeasurementSite.entries.mapNotNull { site ->
            val row = store.body.measurementsForTypeBetween(
                site.type,
                0L,
                cutoff.plusDays(1).atStart().toEpochMilliseconds(),
            ).lastOrNull()
            row?.let { site.type to it }
        }.toMap()
        if (oldFatPercent == null && oldTape.isEmpty()) return null
        val inputs = AdiposeInputs(
            sex = sexOf(profile),
            bodyFatPercent = oldFatPercent,
            heightCm = profile?.height_cm,
            tapeCm = oldTape.mapValues { (_, row) -> row.value_ },
        )
        val layer = AdiposeModel.compute(inputs) ?: return null
        val date = oldFatDate ?: oldTape.values.maxOf { it.local_date }.let(LocalDate::parse)
        val map = BodyMapModel.recovery(
            sex = sexOf(profile),
            states = emptyMap(),
            adipose = layer,
            extras = app.openhealth.ui.body.BodyMapExtras(
                sexStated = profile?.sex_at_birth == "MALE" || profile?.sex_at_birth == "FEMALE",
            ),
        )
        return HistoricalAdipose(map, date)
    }

    private fun buildComposition(
        profile: Profile?,
        policy: DisplayPolicy,
    ): List<CompositionTileModel> {
        val tiles = mutableListOf<CompositionTileModel>()
        val weight = latestWeight()
        val weightAllowed = policy.shows(SensitiveMetric.WEIGHT)
        val fatAllowed = policy.shows(SensitiveMetric.BODY_FAT) && weightAllowed
        val fat = latestBodyFat() ?: if (fatAllowed) navyFat(profile) else null
        if (fatAllowed && fat != null) {
            tiles += CompositionTileModel(
                key = CompositionKey.BODY_FAT,
                valueText = format(fat.percent, 1),
                unitText = "%",
                sourceLine = methodLabel(fat.method) ?: fat.sourceLabel,
                estimate = fat.method == null,
                provenance = fatProvenance(fat),
            )
        }
        if (fatAllowed && weight != null && fat != null) {
            val lean = LeanMass.compute(weight.value.value, fat.percent)
            val imperial = profile?.units == "imperial"
            (lean as? FormulaOutcome.Computed)?.let { outcome ->
                val leanValue = if (imperial) outcome.value / POUNDS_PER_KILOGRAM else outcome.value
                tiles += CompositionTileModel(
                    key = CompositionKey.LEAN_MASS,
                    valueText = format(leanValue, 1),
                    unitText = if (imperial) "lb" else "kg",
                    sourceLine = context.getString(UiR.string.vitals_composition_source_calculated),
                    estimate = fat.method == null,
                    provenance = weightProvenance(weight, "${format(outcome.value, 1)} kg"),
                )
            }
        }
        latestMeasurement(MeasurementSite.WAIST.type)?.let { waist ->
            val imperial = profile?.units == "imperial"
            val display = if (imperial) waist.value_ / 2.54 else waist.value_
            tiles += CompositionTileModel(
                key = CompositionKey.WAIST,
                valueText = format(display, 1),
                unitText = if (imperial) "in" else "cm",
                sourceLine =
                methodLabel(waist.method) ?: context.getString(UiR.string.vitals_method_manual),
                provenance = measurementProvenance(
                    waist,
                    "${format(display, 1)} ${if (imperial) "in" else "cm"}",
                ),
            )
        }
        store.observations.latest(ObservationType.RESTING_HEART_RATE)
            ?.takeIf { !it.excludedFromTrends }
            ?.let { rhr ->
                tiles += CompositionTileModel(
                    key = CompositionKey.RESTING_HEART_RATE,
                    valueText = format(rhr.value.value, 0),
                    unitText = "bpm",
                    sourceLine = methodLabel(rhr.provenance.method)
                        ?: context.getString(UiR.string.vitals_provenance_manual),
                    provenance = provenance(rhr, "${format(rhr.value.value, 0)} bpm"),
                )
            }
        latestMeasurement(CompositionMeasure.BODY_WATER.type)?.let { water ->
            tiles += CompositionTileModel(
                key = CompositionKey.BODY_WATER,
                valueText = format(water.value_, 1),
                unitText = "%",
                sourceLine = context.getString(UiR.string.vitals_composition_source_water),
                provenance = measurementProvenance(water, "${format(water.value_, 1)} %"),
            )
        }
        val height = profile?.height_cm
        if (weightAllowed && weight != null && height != null) {
            (Bmi.compute(weight.value.value, height) as? FormulaOutcome.Computed)
                ?.let { outcome ->
                    tiles += CompositionTileModel(
                        key = CompositionKey.BMI,
                        valueText = format(outcome.value, 1),
                        unitText = null,
                        sourceLine = context.getString(UiR.string.vitals_composition_source_bmi),
                        provenance = weightProvenance(weight, format(outcome.value, 1)),
                    )
                }
            if (fatAllowed && fat != null) {
                (
                    Ffmi.fromBodyFat(weight.value.value, height, fat.percent)
                        as? FormulaOutcome.Computed
                    )?.let { outcome ->
                    tiles += CompositionTileModel(
                        key = CompositionKey.FFMI,
                        valueText = format(outcome.value.ffmi, 1),
                        unitText = null,
                        sourceLine = context.getString(UiR.string.vitals_composition_source_ffmi),
                        estimate = fat.method == null,
                        provenance = fatProvenance(
                            fat,
                        ).copy(valueText = format(outcome.value.ffmi, 1)),
                    )
                }
            }
        }
        return tiles
    }

    /**
     * A measurement the person named themselves (`pr.md` §6.8): `custom:<name>`, stored
     * in centimetres like a tape site and shown under that name. It has no anchor.
     */
    private fun customMeasurementRows(imperial: Boolean): List<MeasurementRowModel> =
        store.body.latestMeasurements(200)
            .filter { it.type.startsWith(CUSTOM_PREFIX) }
            .distinctBy { it.type }
            .sortedByDescending { it.happened_at }
            .map { row ->
                val display = if (imperial) row.value_ / 2.54 else row.value_
                MeasurementRowModel(
                    site = null,
                    customLabel = row.type.removePrefix(CUSTOM_PREFIX),
                    valueText = format(display, 1),
                    unitText = if (imperial) "in" else "cm",
                    date = LocalDate.parse(row.local_date),
                    sourceLine = methodLabel(row.method)
                        ?: context.getString(UiR.string.vitals_method_manual),
                    provenance = measurementProvenance(
                        row,
                        "${format(display, 1)} ${if (imperial) "in" else "cm"}",
                    ),
                )
            }

    private fun measurementRows(
        sites: List<MeasurementSite>,
        imperial: Boolean,
    ): List<MeasurementRowModel> = sites.mapNotNull { site ->
        latestMeasurement(site.type)?.let { row ->
            val display = if (imperial) row.value_ / 2.54 else row.value_
            MeasurementRowModel(
                site = site,
                valueText = format(display, 1),
                unitText = if (imperial) "in" else "cm",
                date = LocalDate.parse(row.local_date),
                sourceLine = methodLabel(row.method)
                    ?: context.getString(UiR.string.vitals_method_manual),
                provenance = measurementProvenance(
                    row,
                    "${format(display, 1)} ${if (imperial) "in" else "cm"}",
                ),
            )
        }
    }

    private fun photoTiles(): List<PhotoTileModel> = store.body.photoGallery(90).mapNotNull { row ->
        val bytes = PhotoCodec.decodeBase64(row.thumb_b64)
        PhotoTileModel(
            id = row.id,
            date = LocalDate.parse(row.local_date),
            caption = row.caption,
            thumbnail = bytes?.let(PhotoCodec::decode),
            provenance = ValueProvenance(
                sourceLabel = sourceLabel(row.source_id),
                method = null,
                happenedAt = Instant.fromEpochMilliseconds(row.happened_at),
                recordedAt = Instant.fromEpochMilliseconds(row.recorded_at),
                localDate = LocalDate.parse(row.local_date),
                zoneId = zone.id,
                valueText = context.getString(UiR.string.vitals_photos_title),
            ),
        )
    }

    private fun vitalGroups(imperial: Boolean): List<VitalGroupModel> {
        val groups = mutableListOf<VitalGroupModel>()
        val bp = latestBloodPressure()
        if (bp != null) {
            val range = VitalRanges.forReading(ObservationType.BLOOD_PRESSURE_SYSTOLIC, null)!!
            val diastolicRange =
                VitalRanges.forReading(ObservationType.BLOOD_PRESSURE_DIASTOLIC, null)!!
            val outside = range.position(bp.first.value.value) != RangePosition.WITHIN ||
                diastolicRange.position(bp.second.value.value) != RangePosition.WITHIN
            groups += VitalGroupModel(
                type = ObservationType.BLOOD_PRESSURE_SYSTOLIC,
                latestText = "${format(
                    bp.first.value.value,
                    0,
                )}/${format(bp.second.value.value, 0)}",
                unitText = "mmHg",
                date = bp.first.localDate,
                count = countReadings(ObservationType.BLOOD_PRESSURE_SYSTOLIC),
                contextText = contextText(contextOf(bp.first)),
                position = if (outside) RangePosition.ABOVE else RangePosition.WITHIN,
                citationLine = rangeLine(range, imperial),
                sourceLabel = sourceLabel(bp.first.provenance.sourceId.value),
            )
        }
        listOf(
            ObservationType.RESTING_HEART_RATE,
            ObservationType.BODY_TEMPERATURE,
            ObservationType.OXYGEN_SATURATION,
            ObservationType.BLOOD_GLUCOSE,
        ).forEach { type ->
            val latest = store.observations.latest(type)?.takeIf { !it.excludedFromTrends }
                ?: return@forEach
            val range = VitalRanges.forReading(type, latest.context?.name)
            groups += VitalGroupModel(
                type = type,
                latestText = format(
                    vitalDisplayValue(type, latest.value.value, imperial),
                    decimalsFor(type),
                ),
                unitText = vitalDisplayUnit(type, imperial),
                date = latest.localDate,
                count = countReadings(type),
                contextText = contextText(contextOf(latest)),
                position = range?.position(latest.value.value),
                citationLine = range?.let { rangeLine(it, imperial) },
                sourceLabel = sourceLabel(latest.provenance.sourceId.value),
            )
        }
        return groups
    }

    private fun countReadings(type: ObservationType): Int = store.observations.forTypeBetween(
        type,
        today().minus(DatePeriod(days = 90)).atStart(),
        now(),
    )
        .count { !it.excludedFromTrends }

    private fun rangeLine(
        range: app.openhealth.core.vitals.ReferenceRange,
        imperial: Boolean,
    ): String {
        val unit = vitalDisplayUnit(range.type, imperial)
        return "${range.population} · ${displayStatement(range, imperial)} $unit"
    }

    // ------------------------------------------------------------------ vital detail

    fun detail(
        type: ObservationType,
        filter: ObservationContext?,
        imperial: Boolean = false,
    ): VitalDetailModel? {
        if (type !in B7_VITALS) return null
        val readings = readingsOf(type, filter, imperial)
        val values = readings.map { it.values }
        val canonicalLatest = values.firstOrNull()
        val latestText = when {
            canonicalLatest == null -> ""
            type == ObservationType.BLOOD_PRESSURE_SYSTOLIC ->
                canonicalLatest.joinToString("/") { format(it, 0) }

            else -> format(
                vitalDisplayValue(type, canonicalLatest.first(), imperial),
                decimalsFor(type),
            )
        }
        val averageText = when {
            values.isEmpty() -> ""
            type == ObservationType.BLOOD_PRESSURE_SYSTOLIC ->
                format(values.map { it[0] }.average(), 0) + "/" +
                    format(values.map { it[1] }.average(), 0)

            type == ObservationType.BODY_TEMPERATURE ->
                format(vitalDisplayValue(type, values.map { it.first() }.average(), imperial), 1)

            else -> format(
                vitalDisplayValue(
                    type,
                    values.map {
                        it.first()
                    }.average(),
                    imperial,
                ),
                0,
            )
        }
        val range = VitalRanges.forReading(type, contextFilterKey(type, filter))
        val position = when {
            type == ObservationType.BLOOD_PRESSURE_SYSTOLIC -> {
                val latest = readings.firstOrNull()?.values
                if (latest != null && latest.size == 2) {
                    val systolic = VitalRanges.forReading(type, null)!!
                    val diastolic = VitalRanges.forReading(
                        ObservationType.BLOOD_PRESSURE_DIASTOLIC,
                        null,
                    )!!
                    if (systolic.position(latest[0]) == RangePosition.WITHIN &&
                        diastolic.position(latest[1]) == RangePosition.WITHIN
                    ) {
                        RangePosition.WITHIN
                    } else {
                        RangePosition.ABOVE
                    }
                } else {
                    null
                }
            }

            else -> latestText.takeIf { it.isNotBlank() }?.let {
                range?.position(readings.firstOrNull()?.values?.firstOrNull() ?: return@let null)
            }
        }
        val scaleValues = range?.let { scaleReadings(type, filter, imperial) } ?: emptyList()
        val chart = chartOf(type, filter, imperial)
        val contexts = contextFilters(type, filter)
        return VitalDetailModel(
            type = type,
            title = vitalTitle(type),
            latestText = latestText,
            unitText = vitalDisplayUnit(type, imperial),
            averageText = averageText,
            count = readings.size,
            range = range?.let { rangeUi(it, position, imperial) },
            scaleValues = scaleValues,
            chart = chart,
            contexts = contexts,
            readings = readings,
            protocol = if (type == ObservationType.BLOOD_PRESSURE_SYSTOLIC) {
                protocolModel()
            } else {
                null
            },
            tableHeader = listOf(
                context.getString(UiR.string.vitals_detail_table_date),
                context.getString(UiR.string.vitals_detail_table_value),
                context.getString(UiR.string.vitals_detail_table_context),
            ),
            tableRows = readings.map {
                listOf(
                    it.provenance.localDate.toString(),
                    it.values.joinToString(" / ") { value ->
                        format(vitalDisplayValue(type, value, imperial), decimalsFor(type))
                    } + " " + it.unitText,
                    it.contextText ?: "",
                )
            },
        )
    }

    private fun latestBloodPressure(): Pair<Observation, Observation>? {
        val systolic = store.observations.forTypeBetween(
            ObservationType.BLOOD_PRESSURE_SYSTOLIC,
            today().minus(DatePeriod(days = 365)).atStart(),
            now(),
        ).lastOrNull { !it.excludedFromTrends } ?: return null
        val diastolic = store.observations.forDayAndType(
            systolic.localDate,
            ObservationType.BLOOD_PRESSURE_DIASTOLIC,
        ).minByOrNull {
            (it.provenance.happenedAt - systolic.provenance.happenedAt).absoluteValue
        } ?: return null
        return systolic to diastolic
    }

    private fun readingsOf(
        type: ObservationType,
        filter: ObservationContext?,
        imperial: Boolean,
    ): List<VitalReadingModel> {
        if (type == ObservationType.BLOOD_PRESSURE_SYSTOLIC) {
            val systolics = bpObservations(type, filter)
            val byTime = bpObservations(ObservationType.BLOOD_PRESSURE_DIASTOLIC, filter)
                .associateBy { it.provenance.happenedAt }
            return systolics.mapNotNull { systolic ->
                val diastolic = byTime[systolic.provenance.happenedAt] ?: return@mapNotNull null
                VitalReadingModel(
                    id = systolic.id.value,
                    values = listOf(systolic.value.value, diastolic.value.value),
                    unitText = "mmHg",
                    contextText = contextText(contextOf(systolic)),
                    happenedAt = systolic.provenance.happenedAt,
                    provenance = provenance(
                        systolic,
                        "${format(systolic.value.value, 0)}/" +
                            "${format(diastolic.value.value, 0)} mmHg",
                    ),
                    homeSlot = VitalContextCode.decode(systolic.context?.name).homeSlot?.key,
                )
            }.sortedByDescending { it.happenedAt }
        }
        return store.observations.forTypeBetween(
            type,
            (today() - 365.days).atStart(),
            now(),
        ).filter { !it.excludedFromTrends && matches(it, filter) }
            .sortedByDescending { it.provenance.happenedAt }
            .map { observation ->
                VitalReadingModel(
                    id = observation.id.value,
                    values = listOf(observation.value.value),
                    unitText = vitalDisplayUnit(type, imperial),
                    contextText = contextText(contextOf(observation)),
                    happenedAt = observation.provenance.happenedAt,
                    provenance = provenance(
                        observation,
                        "${format(observation.value.value, 1)} ${vitalDisplayUnit(type, false)}",
                    ),
                )
            }
    }

    private fun bpObservations(
        type: ObservationType,
        filter: ObservationContext?,
    ): List<Observation> = store.observations.forTypeBetween(
        type,
        (today() - 365.days).atStart(),
        now(),
    ).filter { !it.excludedFromTrends && matches(it, filter) }

    /**
     * The full context a reading carries. The layer-4 vital row keeps arm, position, meal
     * and protocol slot together; the observation keeps the one enum that fits its column.
     * The sheet and the filters read the former, so "Sitting" and "Left arm" both work.
     */
    private fun contextOf(observation: Observation): String? =
        store.body.vitalByObservationId(observation.id.value)?.context
            ?: observation.context?.name

    private fun matches(observation: Observation, filter: ObservationContext?): Boolean {
        if (filter == null) return true
        if (observation.context == filter) return true
        val code = VitalContextCode.decode(contextOf(observation))
        return when (filter) {
            ObservationContext.LEFT_ARM -> code.arm == app.openhealth.core.vitals.ArmSide.LEFT
            ObservationContext.RIGHT_ARM -> code.arm == app.openhealth.core.vitals.ArmSide.RIGHT
            ObservationContext.SITTING ->
                code.position == app.openhealth.core.vitals.BodyPosition.SITTING

            ObservationContext.STANDING ->
                code.position == app.openhealth.core.vitals.BodyPosition.STANDING

            ObservationContext.LYING ->
                code.position == app.openhealth.core.vitals.BodyPosition.LYING

            ObservationContext.FASTING -> code.meal == MealContext.FASTING
            ObservationContext.PRE_MEAL -> code.meal == MealContext.PRE_MEAL
            ObservationContext.POST_MEAL -> code.meal == MealContext.POST_MEAL
            else -> false
        }
    }

    private fun contextFilters(
        type: ObservationType,
        filter: ObservationContext?,
    ): List<ContextFilterModel> {
        val all = listOf(
            ContextFilterModel(
                null,
                context.getString(UiR.string.vitals_detail_context_all),
                filter == null,
            ),
        )
        val options = when (type) {
            ObservationType.BLOOD_PRESSURE_SYSTOLIC -> listOf(
                ObservationContext.LEFT_ARM,
                ObservationContext.RIGHT_ARM,
                ObservationContext.SITTING,
                ObservationContext.STANDING,
                ObservationContext.LYING,
            )

            ObservationType.BLOOD_GLUCOSE -> listOf(
                ObservationContext.FASTING,
                ObservationContext.PRE_MEAL,
                ObservationContext.POST_MEAL,
            )

            else -> emptyList()
        }
        return all + options.map {
            ContextFilterModel(it, contextText(it.name).orEmpty(), filter == it)
        }
    }

    /**
     * The range context: the chosen filter, or the latest reading's own context when the
     * person is looking at "All". A post-meal glucose must not be judged against the
     * fasting range just because no chip is selected.
     */
    private fun contextFilterKey(type: ObservationType, filter: ObservationContext?): String? =
        filter?.name ?: store.observations.latest(type)?.context?.name

    private fun chartOf(
        type: ObservationType,
        filter: ObservationContext?,
        imperial: Boolean,
    ): TrendChartModel? {
        val points = if (type == ObservationType.BLOOD_PRESSURE_SYSTOLIC) {
            val chartStart = today().minus(DatePeriod(days = 30))
            val systolics = bpObservations(type, filter)
                .filter { it.localDate >= chartStart }
                .groupBy { it.localDate }
                .mapValues { (_, same) -> same.maxBy { it.provenance.happenedAt } }
            val diastolics = bpObservations(ObservationType.BLOOD_PRESSURE_DIASTOLIC, filter)
                .filter { it.localDate >= chartStart }
                .groupBy { it.localDate }
                .mapValues { (_, same) -> same.maxBy { it.provenance.happenedAt } }
            if (systolics.size < 2 && diastolics.size < 2) return null
            val count = maxOf(systolics.size, diastolics.size)
            TrendChartModel(
                series = listOf(
                    TrendSeriesModel(
                        label = context.getString(UiR.string.vitals_add_systolic),
                        points = systolics.values.sortedBy { it.localDate }
                            .map {
                                TrendPoint(
                                    it.localDate,
                                    vitalDisplayValue(type, it.value.value, imperial),
                                )
                            },
                        role = SeriesRole.SYSTOLIC,
                    ),
                    TrendSeriesModel(
                        label = context.getString(UiR.string.vitals_add_diastolic),
                        points = diastolics.values.sortedBy { it.localDate }
                            .map {
                                TrendPoint(
                                    it.localDate,
                                    vitalDisplayValue(
                                        ObservationType.BLOOD_PRESSURE_DIASTOLIC,
                                        it.value.value,
                                        imperial,
                                    ),
                                )
                            },
                        role = SeriesRole.DIASTOLIC,
                    ),
                ),
                unitText = "mmHg",
                description = if (count == 1) {
                    context.getString(UiR.string.vitals_chart_desc_one, vitalTitle(type))
                } else {
                    context.getString(UiR.string.vitals_chart_desc, vitalTitle(type), count)
                },
            )
        } else {
            val points = store.observations.forTypeBetween(
                type,
                (today() - 90.days).atStart(),
                now(),
            ).filter { !it.excludedFromTrends && matches(it, filter) }
                .groupBy { it.localDate }
                .mapValues { (_, same) -> same.maxBy { it.provenance.happenedAt } }
            if (points.size < 2) return null
            val count = points.size
            val range = VitalRanges.forReading(type, contextFilterKey(type, filter))
            TrendChartModel(
                series = listOf(
                    TrendSeriesModel(
                        label = null,
                        points = points.values.sortedBy { it.localDate }
                            .map {
                                TrendPoint(
                                    it.localDate,
                                    vitalDisplayValue(type, it.value.value, imperial),
                                )
                            },
                        role = SeriesRole.SINGLE,
                    ),
                ),
                unitText = vitalDisplayUnit(type, imperial),
                referenceLow = range?.low?.let { vitalDisplayValue(type, it, imperial) },
                referenceHigh = range?.high?.let { vitalDisplayValue(type, it, imperial) },
                description = if (count == 1) {
                    context.getString(UiR.string.vitals_chart_desc_one, vitalTitle(type))
                } else {
                    context.getString(UiR.string.vitals_chart_desc, vitalTitle(type), count)
                },
                decimals = decimalsFor(type),
            )
        }
        return points
    }

    private fun scaleReadings(
        type: ObservationType,
        filter: ObservationContext?,
        imperial: Boolean,
    ): List<ScaleReadingModel> {
        val latest = readingsOf(type, filter, imperial).firstOrNull() ?: return emptyList()
        fun convert(value: Double): Double = when (type) {
            ObservationType.BLOOD_GLUCOSE -> if (imperial) value * 18.0182 else value
            else -> value
        }
        fun model(bands: List<app.openhealth.core.vitals.RangeBand>): ScaleModel {
            val mapped = bands.map {
                ScaleBandModel(
                    label = when (it.label) {
                        app.openhealth.core.vitals.RangeBandLabel.LOW -> ScaleLabel.LOW
                        app.openhealth.core.vitals.RangeBandLabel.NORMAL -> ScaleLabel.NORMAL
                        app.openhealth.core.vitals.RangeBandLabel.RAISED -> ScaleLabel.RAISED
                        app.openhealth.core.vitals.RangeBandLabel.HIGH -> ScaleLabel.HIGH
                    },
                    from = it.from?.let(::convert),
                    to = it.to?.let(::convert),
                    citation = it.citation,
                )
            }
            val normalLow = mapped.firstOrNull { it.label == ScaleLabel.NORMAL }?.from ?: 0.0
            val high = mapped.firstOrNull { it.label == ScaleLabel.HIGH }?.from ?: (normalLow + 10)
            val span = high - normalLow
            return ScaleModel(
                bands = mapped,
                domainLow = normalLow - span * 0.5,
                domainHigh = high + span * 0.5,
            )
        }
        return when (type) {
            ObservationType.BLOOD_PRESSURE_SYSTOLIC -> listOf(
                ScaleReadingModel(
                    context.getString(UiR.string.vitals_add_systolic),
                    latest.values[0],
                    model(VitalRanges.scaleFor(type, null)!!),
                ),
                ScaleReadingModel(
                    context.getString(UiR.string.vitals_add_diastolic),
                    latest.values[1],
                    model(
                        VitalRanges.scaleFor(ObservationType.BLOOD_PRESSURE_DIASTOLIC, null)!!,
                    ),
                ),
            )

            ObservationType.BLOOD_GLUCOSE -> listOf(
                ScaleReadingModel(
                    context.getString(UiR.string.vitals_type_glucose),
                    convert(latest.values[0]),
                    model(VitalRanges.scaleFor(type, contextFilterKey(type, filter))!!),
                ),
            )

            else -> emptyList()
        }
    }

    /** One range, its bounds converted to the unit the reading is printed in. */
    private fun displayStatement(
        range: app.openhealth.core.vitals.ReferenceRange,
        imperial: Boolean,
    ): String {
        val convert = imperial &&
            (
                range.type == ObservationType.BODY_TEMPERATURE ||
                    range.type == ObservationType.BLOOD_GLUCOSE
                )
        if (!convert) return range.statement
        val low = range.low
        val high = range.high
        return when {
            low != null && high != null ->
                "${format(vitalDisplayValue(range.type, low, true), 1)}-" +
                    "${format(vitalDisplayValue(range.type, high, true), 1)}"

            low != null -> "from ${format(vitalDisplayValue(range.type, low, true), 1)}"
            high != null -> "below ${format(vitalDisplayValue(range.type, high, true), 1)}"
            else -> range.statement
        }
    }

    private fun displayBounds(
        range: app.openhealth.core.vitals.ReferenceRange,
        imperial: Boolean,
    ): String {
        val unit = vitalDisplayUnit(range.type, imperial)
        val low = range.low
        val high = range.high
        val convert = imperial &&
            (
                range.type == ObservationType.BODY_TEMPERATURE ||
                    range.type == ObservationType.BLOOD_GLUCOSE
                )
        return if (convert) {
            when {
                low != null && high != null ->
                    "${format(vitalDisplayValue(range.type, low, true), 1)}-" +
                        "${format(vitalDisplayValue(range.type, high, true), 1)} $unit"

                low != null -> "from ${format(vitalDisplayValue(range.type, low, true), 1)} $unit"
                high != null -> "below ${format(
                    vitalDisplayValue(range.type, high, true),
                    1,
                )} $unit"
                else -> range.boundsText()
            }
        } else {
            range.boundsText()
        }
    }

    private fun decimalsFor(type: ObservationType): Int = when (type) {
        ObservationType.BODY_TEMPERATURE, ObservationType.BLOOD_GLUCOSE -> 1
        else -> 0
    }

    private fun rangeUi(
        range: app.openhealth.core.vitals.ReferenceRange,
        position: RangePosition?,
        imperial: Boolean,
    ): RangeUiModel = RangeUiModel(
        statement = displayStatement(range, imperial),
        population = range.population,
        boundsText = displayBounds(range, imperial),
        citation = range.citation,
        citationLabel = citationLabel(range.citation),
        position = position,
    )

    // ------------------------------------------------------------------ home protocol

    fun protocolModel(): BpProtocolModel {
        val start = store.settings.get(SettingKeys.VITALS_BP_PROTOCOL_START)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return BpProtocolModel(
                state = BpProtocolState(
                    LocalDate.parse("1970-01-01"),
                    emptyList(),
                    2,
                    0,
                    28,
                    false,
                ),
                today = today(),
            )
        val readings = bpObservations(ObservationType.BLOOD_PRESSURE_SYSTOLIC, null)
            .filter { it.localDate >= start && it.localDate < start.plus(DatePeriod(days = 7)) }
        val protocolReadings = readings.mapNotNull { observation ->
            observation.groupKey?.substringAfter("bp-home-", "")
                ?.takeIf { it.isNotBlank() }
                ?.let(HomeSlot::fromKey)
                ?.let { BpProtocolReading(observation.localDate, it) }
        }
        val state = BloodPressureProtocol.state(start, protocolReadings)
        val afterFirst = readings.filter {
            it.localDate > start && it.groupKey?.startsWith("bp-home-") == true
        }
        val diastolics = bpObservations(ObservationType.BLOOD_PRESSURE_DIASTOLIC, null)
            .associateBy { it.provenance.happenedAt }
        val pairs = afterFirst.mapNotNull { systolic ->
            diastolics[systolic.provenance.happenedAt]?.let {
                systolic.value.value to it.value.value
            }
        }
        val average = BloodPressureProtocol.average(pairs)
        return BpProtocolModel(
            state = state,
            today = today(),
            averageSystolic = average?.systolic,
            averageDiastolic = average?.diastolic,
            averageReadings = average?.readings ?: 0,
            active = BloodPressureProtocol.withinWindow(start, today()) && !state.complete,
        )
    }

    fun startProtocol(): LocalDate {
        val start = today()
        store.settings.put(SettingKeys.VITALS_BP_PROTOCOL_START, start.toString())
        return start
    }

    // ------------------------------------------------------------------ writes

    fun logWeight(weightText: String, at: Instant, imperial: Boolean): ObservationId? {
        val typed = weightText.replace(',', '.').toDoubleOrNull() ?: return null
        if (typed <= 0.0) return null
        val kg = if (imperial) {
            Measure(typed, UcumUnit.POUND).convertTo(UcumUnit.KILOGRAM).value
        } else {
            typed
        }
        if (kg !in 1.0..500.0) return null
        val id = ObservationId(ids.newId())
        store.observations.record(
            Observation.of(
                id = id,
                type = ObservationType.WEIGHT,
                value = Measure(kg, UcumUnit.KILOGRAM),
                start = at,
                provenance = provenance(at, MeasurementMethod.MANUAL_ENTRY),
            ),
        )
        return id
    }

    fun logMeasurement(
        type: String,
        valueText: String,
        at: Instant,
        imperial: Boolean,
        method: MeasurementMethod = MeasurementMethod.MANUAL_ENTRY,
    ): String? {
        val typed = valueText.replace(',', '.').toDoubleOrNull() ?: return null
        if (typed <= 0.0) return null
        val composition = CompositionMeasure.fromType(type)
        val value = if (composition != null) {
            if (typed !in 1.0..80.0) return null
            typed
        } else {
            val cm = if (imperial) typed * 2.54 else typed
            if (cm !in 1.0..400.0) return null
            cm
        }
        val recorded = now()
        val row = Measurement(
            id = ids.newId(),
            type = type,
            value_ = value,
            unit = if (composition != null) "%" else "cm",
            local_date = at.toLocalDateTime(zone).date.toString(),
            zone_id = zone.id,
            happened_at = at.toEpochMilliseconds(),
            recorded_at = recorded.toEpochMilliseconds(),
            source_id = manualSource.value,
            method = method.name,
            notes = null,
            excluded_from_trends = false,
            observation_id = null,
            raw_record_id = null,
            idempotency_key = ids.newId(),
            device = DEVICE,
            app_version = appVersion,
            revision = 1L,
        )
        store.body.upsertMeasurement(row)
        return row.id
    }

    private fun observationContext(draft: VitalDraft): ObservationContext? = when {
        draft.arm == app.openhealth.core.vitals.ArmSide.LEFT -> ObservationContext.LEFT_ARM
        draft.arm == app.openhealth.core.vitals.ArmSide.RIGHT -> ObservationContext.RIGHT_ARM
        draft.position == app.openhealth.core.vitals.BodyPosition.SITTING ->
            ObservationContext.SITTING
        draft.position == app.openhealth.core.vitals.BodyPosition.STANDING ->
            ObservationContext.STANDING
        draft.position == app.openhealth.core.vitals.BodyPosition.LYING ->
            ObservationContext.LYING
        draft.meal == MealContext.FASTING -> ObservationContext.FASTING
        draft.meal == MealContext.PRE_MEAL -> ObservationContext.PRE_MEAL
        draft.meal == MealContext.POST_MEAL -> ObservationContext.POST_MEAL
        else -> null
    }

    fun logReading(draft: VitalDraft, imperial: Boolean, at: Instant): List<String> {
        val context = VitalContextCode(
            arm = draft.arm,
            position = draft.position,
            meal = draft.meal,
            homeSlot = draft.homeSlot,
        ).encode()
        val recorded = now()
        val created = mutableListOf<String>()
        fun write(type: ObservationType, canonical: Double, method: MeasurementMethod) {
            // A reading is both a layer-2 observation (the canonical value the range and
            // rollups read) and a layer-4 vital row (the domain object the timeline
            // lists). They share the hour and the linked id, so one delete removes both.
            val observationId = ObservationId(ids.newId())
            val observation = Observation.of(
                id = observationId,
                type = type,
                value = Measure(canonical, type.canonicalUnit),
                start = at,
                provenance = provenance(at, method),
            ).copy(
                context = observationContext(draft),
                groupKey = draft.homeSlot?.let { "bp-home-${it.key}" },
            )
            store.observations.record(observation)
            val id = ids.newId()
            store.body.upsertVital(
                Vital(
                    id = id,
                    type = type.name,
                    value_ = canonical,
                    unit = type.canonicalUnit.ucum,
                    local_date = at.toLocalDateTime(zone).date.toString(),
                    zone_id = zone.id,
                    happened_at = at.toEpochMilliseconds(),
                    recorded_at = recorded.toEpochMilliseconds(),
                    source_id = manualSource.value,
                    method = method.name,
                    context = context,
                    note = draft.note.takeIf { it.isNotBlank() },
                    excluded_from_trends = false,
                    observation_id = observationId.value,
                    raw_record_id = null,
                    idempotency_key = ids.newId(),
                    device = DEVICE,
                    app_version = appVersion,
                    revision = 1L,
                ),
            )
            created += id
        }
        when (draft.type) {
            ObservationType.BLOOD_PRESSURE_SYSTOLIC -> {
                val systolic = draft.systolic.replace(',', '.').toDoubleOrNull()
                val diastolic = draft.diastolic.replace(',', '.').toDoubleOrNull()
                if (systolic != null && systolic in 50.0..300.0) {
                    write(ObservationType.BLOOD_PRESSURE_SYSTOLIC, systolic, MeasurementMethod.CUFF)
                }
                if (diastolic != null && diastolic in 30.0..200.0) {
                    write(
                        ObservationType.BLOOD_PRESSURE_DIASTOLIC,
                        diastolic,
                        MeasurementMethod.CUFF,
                    )
                }
                val pulse = draft.pulse.replace(',', '.').toDoubleOrNull()
                if (pulse != null && pulse in 20.0..250.0) {
                    write(ObservationType.HEART_RATE, pulse, MeasurementMethod.CUFF)
                }
            }

            ObservationType.RESTING_HEART_RATE -> {
                val value = draft.value.replace(',', '.').toDoubleOrNull()
                if (value != null && value in 20.0..250.0) {
                    write(ObservationType.RESTING_HEART_RATE, value, MeasurementMethod.MANUAL_ENTRY)
                }
            }

            ObservationType.BODY_TEMPERATURE -> {
                val typed = draft.value.replace(',', '.').toDoubleOrNull()
                val celsius = typed?.let {
                    if (imperial) {
                        Measure(it, UcumUnit.FAHRENHEIT).convertTo(UcumUnit.CELSIUS).value
                    } else {
                        it
                    }
                }
                if (celsius != null && celsius in 25.0..45.0) {
                    write(ObservationType.BODY_TEMPERATURE, celsius, MeasurementMethod.MANUAL_ENTRY)
                }
            }

            ObservationType.OXYGEN_SATURATION -> {
                val value = draft.value.replace(',', '.').toDoubleOrNull()
                if (value != null && value in 50.0..100.0) {
                    write(ObservationType.OXYGEN_SATURATION, value, MeasurementMethod.MANUAL_ENTRY)
                }
            }

            ObservationType.BLOOD_GLUCOSE -> {
                val typed = draft.value.replace(',', '.').toDoubleOrNull()
                val mmol = typed?.let {
                    if (imperial) {
                        Measure(it, UcumUnit.MILLIGRAM_PER_DECILITRE)
                            .convertTo(UcumUnit.MILLIMOL_PER_LITRE).value
                    } else {
                        it
                    }
                }
                if (mmol != null && mmol in 1.0..50.0) {
                    write(ObservationType.BLOOD_GLUCOSE, mmol, MeasurementMethod.MANUAL_ENTRY)
                }
            }

            else -> Unit
        }
        return created
    }

    fun deleteVital(id: String): TombstoneId? = store.trash.deleteVital(VitalId(id))

    /**
     * Deletes a whole reading, not one column of it: a blood-pressure pair (and its
     * optional pulse) is one record in the interface even though it is stored as rows.
     * Every row leaves its own tombstone, so the whole group comes back from Trash.
     */
    fun deleteReading(id: String): List<TombstoneId> {
        val primary = store.observations.byId(ObservationId(id)) ?: return emptyList()
        val related = if (primary.type == ObservationType.BLOOD_PRESSURE_SYSTOLIC) {
            listOf(
                ObservationType.BLOOD_PRESSURE_SYSTOLIC,
                ObservationType.BLOOD_PRESSURE_DIASTOLIC,
            )
                .flatMap { type ->
                    store.observations.forDayAndType(primary.localDate, type)
                        .filter {
                            kotlin.math.abs(
                                (it.provenance.happenedAt - primary.provenance.happenedAt)
                                    .inWholeMilliseconds,
                            ) < READ_GROUP_WINDOW_MS
                        }
                } + store.observations.forDayAndType(primary.localDate, ObservationType.HEART_RATE)
                .filter {
                    kotlin.math.abs(
                        (it.provenance.happenedAt - primary.provenance.happenedAt)
                            .inWholeMilliseconds,
                    ) < READ_GROUP_WINDOW_MS
                }
        } else {
            listOf(primary)
        }
        val observationTombstones = related.distinctBy { it.id.value }
            .mapNotNull { store.trash.deleteObservation(it.id) }
        // The layer-4 vital rows written beside each observation carry the timeline row
        // and the free-text context; they leave their own tombstones in the same action.
        val relatedTypes = related.map { it.type.name }.toSet()
        val vitalTombstones = store.body.vitalsForDay(primary.localDate)
            .filter {
                it.type in relatedTypes &&
                    kotlin.math.abs(
                        it.happened_at - primary.provenance.happenedAt.toEpochMilliseconds(),
                    ) <
                    READ_GROUP_WINDOW_MS
            }
            .mapNotNull { store.trash.deleteVital(VitalId(it.id)) }
        return observationTombstones + vitalTombstones
    }

    fun deletePhoto(id: String): TombstoneId? = store.trash.deletePhoto(PhotoId(id))

    fun savePhoto(prepared: PhotoCodec.Prepared, caption: String?, at: Instant): String? {
        val id = ids.newId()
        val recorded = now()
        val row = Body_photo(
            id = id,
            local_date = at.toLocalDateTime(zone).date.toString(),
            zone_id = zone.id,
            happened_at = at.toEpochMilliseconds(),
            recorded_at = recorded.toEpochMilliseconds(),
            source_id = manualSource.value,
            caption = caption?.takeIf { it.isNotBlank() },
            notes = null,
            mime = "image/jpeg",
            width = prepared.width.toLong(),
            height = prepared.height.toLong(),
            bytes_b64 = PhotoCodec.encodeBase64(prepared.bytes),
            thumb_b64 = PhotoCodec.encodeBase64(prepared.thumbnail),
            sha256 = prepared.sha256,
            idempotency_key = ids.newId(),
            device = DEVICE,
            app_version = appVersion,
            revision = 1L,
        )
        store.body.upsertPhoto(row)
        return id
    }

    fun photoCompare(selectedId: String): PhotoCompareModel? {
        // Metadata first: a full-image query would read every photo's bytes just to pick
        // two of them. Only the chosen older and newer rows are loaded.
        val all = store.body.photoGallery(PHOTO_COMPARE_LIMIT)
        if (all.size < 2) return null
        val selected = all.firstOrNull { it.id == selectedId } ?: return null
        val oldest = all.minByOrNull { it.happened_at } ?: return null
        val newest = all.maxByOrNull { it.happened_at } ?: return null
        val beforeId = oldest.id
        val afterId = if (selected.id == beforeId) newest.id else selected.id
        if (beforeId == afterId) return null
        val before = store.body.photoById(beforeId) ?: return null
        val after = store.body.photoById(afterId) ?: return null
        val beforeBytes = PhotoCodec.decodeBase64(before.bytes_b64) ?: return null
        val afterBytes = PhotoCodec.decodeBase64(after.bytes_b64) ?: return null
        val beforeImage = PhotoCodec.decode(beforeBytes) ?: return null
        val afterImage = PhotoCodec.decode(afterBytes) ?: return null
        val days = (
            LocalDate.parse(after.local_date).toEpochDays() -
                LocalDate.parse(before.local_date).toEpochDays()
            ).toInt().coerceAtLeast(1)
        return PhotoCompareModel(
            beforeId = before.id,
            afterId = after.id,
            beforeDate = LocalDate.parse(before.local_date),
            afterDate = LocalDate.parse(after.local_date),
            before = beforeImage,
            after = afterImage,
            daysApart = days,
        )
    }

    fun photosHidden(): Boolean = store.settings.get(SettingKeys.BODY_PHOTOS_HIDDEN) == "true"

    fun setPhotosHidden(hidden: Boolean) {
        store.settings.put(SettingKeys.BODY_PHOTOS_HIDDEN, hidden.toString())
    }

    fun photosInExport(): Boolean = store.settings.get(SettingKeys.BODY_PHOTOS_IN_EXPORT) == "true"

    fun setPhotosInExport(include: Boolean) {
        store.settings.put(SettingKeys.BODY_PHOTOS_IN_EXPORT, include.toString())
    }

    // ------------------------------------------------------------------ CSV

    fun csv(): String {
        val rows = mutableListOf<BodyCsvRow>()
        store.observations.forTypeBetween(
            ObservationType.WEIGHT,
            (today() - 3650.days).atStart(),
            now().plus(1.days),
        ).filterNot { it.excludedFromTrends }.forEach { observation ->
            rows += BodyCsvRow(
                date = observation.localDate,
                type = BodyCsv.WEIGHT,
                value = observation.value.value,
                unit = UcumUnit.KILOGRAM,
                method = observation.provenance.method,
            )
        }
        MeasurementSite.entries.forEach { site ->
            store.body.measurementsForTypeBetween(site.type, 0L, Long.MAX_VALUE).forEach { row ->
                rows += BodyCsvRow(
                    date = LocalDate.parse(row.local_date),
                    type = site.type,
                    value = row.value_,
                    unit = UcumUnit.CENTIMETRE,
                    method = row.method?.let {
                        runCatching { MeasurementMethod.valueOf(it) }.getOrNull()
                    },
                    notes = row.notes,
                )
            }
        }
        CompositionMeasure.entries.forEach { measure ->
            store.body.measurementsForTypeBetween(measure.type, 0L, Long.MAX_VALUE).forEach { row ->
                rows += BodyCsvRow(
                    date = LocalDate.parse(row.local_date),
                    type = measure.type,
                    value = row.value_,
                    unit = UcumUnit.PERCENT,
                    method = row.method?.let {
                        runCatching { MeasurementMethod.valueOf(it) }.getOrNull()
                    },
                    notes = row.notes,
                )
            }
        }
        return BodyCsv.export(rows)
    }

    data class ImportResult(val imported: Int, val problems: Int)

    fun importCsv(text: String): ImportResult {
        val parsed = BodyCsv.parse(text)
        var imported = 0
        parsed.rows.forEach { row ->
            val at = row.date.atTime(LocalTime(12, 0)).toInstant(zone)
            if (row.type == BodyCsv.WEIGHT) {
                // Importing the app's own export must not add a second copy of a sample
                // already on the phone; the date and the value are the identity here.
                val alreadyThere = store.observations.forDayAndType(
                    row.date,
                    ObservationType.WEIGHT,
                ).any { kotlin.math.abs(it.value.value - row.value) < 1e-9 }
                if (alreadyThere) return@forEach
                val id = ObservationId(ids.newId())
                val outcome = store.observations.record(
                    Observation.of(
                        id = id,
                        type = ObservationType.WEIGHT,
                        value = Measure(row.value, UcumUnit.KILOGRAM),
                        start = at,
                        provenance = csvProvenance(
                            at,
                            row.method ?: MeasurementMethod.FILE_IMPORT,
                            "csv-weight-${row.date}-${row.value}-${row.unit.ucum}",
                        ),
                    ),
                )
                if (outcome is app.openhealth.data.repo.RecordOutcome.Inserted) imported++
            } else {
                val idempotencyKey = "csv-${row.type}-${row.date}-${row.value}"
                if (store.body.measurementByIdempotencyKey(idempotencyKey) != null) {
                    return@forEach
                }
                val alreadyThere = store.body.measurementsForDay(row.date).any {
                    it.type == row.type && kotlin.math.abs(it.value_ - row.value) < 1e-9
                }
                if (alreadyThere) return@forEach
                val recorded = now()
                val measurement = Measurement(
                    id = ids.newId(),
                    type = row.type,
                    value_ = row.value,
                    unit = UcumUnit.canonicalFor(row.unit.dimension).ucum,
                    local_date = row.date.toString(),
                    zone_id = zone.id,
                    happened_at = at.toEpochMilliseconds(),
                    recorded_at = recorded.toEpochMilliseconds(),
                    source_id = importSource.value,
                    method = (row.method ?: MeasurementMethod.FILE_IMPORT).name,
                    notes = row.notes,
                    excluded_from_trends = false,
                    observation_id = null,
                    raw_record_id = null,
                    idempotency_key = idempotencyKey,
                    device = DEVICE,
                    app_version = appVersion,
                    revision = 1L,
                )
                store.body.upsertMeasurement(measurement)
                imported++
            }
        }
        return ImportResult(imported, parsed.problems.size)
    }

    // ------------------------------------------------------------------ provenance

    private fun provenance(
        observation: Observation,
        valueText: String,
        trendText: String? = null,
    ): ValueProvenance = ValueProvenance(
        sourceLabel = sourceLabel(observation.provenance.sourceId.value),
        method = methodLabel(observation.provenance.method),
        happenedAt = observation.provenance.happenedAt,
        recordedAt = observation.provenance.recordedAt,
        localDate = observation.localDate,
        zoneId = observation.provenance.zone.id,
        device = observation.provenance.device,
        quality = observation.quality.label.name.lowercase().replace('_', ' '),
        edited = observation.userEdited,
        rawRecord = observation.provenance.rawRecordId != null,
        valueText = valueText,
        trendText = trendText,
    )

    private fun weightProvenance(observation: Observation, valueText: String) =
        provenance(observation, valueText)

    private fun fatProvenance(fat: FatReading): ValueProvenance = ValueProvenance(
        sourceLabel = fat.sourceLabel,
        method = methodLabel(fat.method),
        happenedAt = fat.happenedAt,
        recordedAt = fat.recordedAt,
        localDate = fat.localDate,
        zoneId = zone.id,
        valueText = "${format(fat.percent, 1)} %",
        trendText = if (fat.method ==
            null
        ) {
            context.getString(UiR.string.vitals_method_navy)
        } else {
            null
        },
    )

    private fun measurementProvenance(row: Measurement, valueText: String): ValueProvenance =
        ValueProvenance(
            sourceLabel = sourceLabel(row.source_id),
            method = methodLabel(row.method),
            happenedAt = Instant.fromEpochMilliseconds(row.happened_at),
            recordedAt = Instant.fromEpochMilliseconds(row.recorded_at),
            localDate = LocalDate.parse(row.local_date),
            zoneId = row.zone_id,
            device = row.device,
            edited = row.revision > 1,
            rawRecord = row.raw_record_id != null,
            valueText = valueText,
        )

    private fun latestWeight(): Observation? =
        store.observations.latest(ObservationType.WEIGHT)?.takeIf { !it.excludedFromTrends }

    private fun latestMeasurement(type: String): Measurement? =
        store.body.latestMeasurementOfType(type)

    private fun tapeRows(): Map<String, Measurement> = listOf(
        "neck",
        "chest",
        "waist",
        "hips",
        "upper_arm",
        "thigh",
        "calf",
    ).mapNotNull { type -> latestMeasurement(type)?.let { type to it } }.toMap()

    private fun sourceLabel(sourceId: String): String {
        val source = store.sources.byId(SourceId(sourceId))
        return if (source == null || source.kind == SourceKind.MANUAL) {
            context.getString(UiR.string.vitals_provenance_manual)
        } else {
            source.label
        }
    }

    private fun sexOf(profile: Profile?): BodySex =
        if (profile?.sex_at_birth == "FEMALE") BodySex.FEMALE else BodySex.MALE

    private fun provenance(at: Instant, method: MeasurementMethod): Provenance = Provenance(
        sourceId = manualSource,
        time = TimeProvenance.of(at, now(), zone),
        idempotencyKey = ids.newId(),
        device = DEVICE,
        appVersion = appVersion,
        method = method,
    )

    private fun csvProvenance(at: Instant, method: MeasurementMethod, key: String): Provenance =
        Provenance(
            sourceId = manualSource,
            time = TimeProvenance.of(at, now(), zone),
            idempotencyKey = key,
            device = DEVICE,
            appVersion = appVersion,
            method = method,
        )

    private fun methodLabel(method: MeasurementMethod?): String? = when (method) {
        MeasurementMethod.SCALE -> context.getString(UiR.string.vitals_method_scale)
        MeasurementMethod.BIA -> context.getString(UiR.string.vitals_method_bia)
        MeasurementMethod.DEXA -> context.getString(UiR.string.vitals_method_dexa)
        MeasurementMethod.CUFF -> context.getString(UiR.string.vitals_method_cuff)
        MeasurementMethod.MANUAL_ENTRY -> context.getString(UiR.string.vitals_method_manual)
        MeasurementMethod.FILE_IMPORT -> context.getString(UiR.string.vitals_method_file)
        MeasurementMethod.DEVICE_SENSOR -> context.getString(UiR.string.vitals_method_device)
        null -> null
        else -> method.name.lowercase().replace('_', ' ')
    }

    private fun methodLabel(method: String?): String? = when (method?.uppercase()) {
        null, "" -> null
        "NAVY" -> context.getString(UiR.string.vitals_method_navy)
        else -> method?.uppercase()?.let {
            runCatching { MeasurementMethod.valueOf(it) }.getOrNull()
        }?.let(::methodLabel) ?: method.lowercase().replace('_', ' ')
    }

    private fun contextText(raw: String?): String? {
        val code = VitalContextCode.decode(raw)
        val parts = buildList {
            code.arm?.let {
                add(
                    context.getString(
                        if (it == app.openhealth.core.vitals.ArmSide.LEFT) {
                            UiR.string.vitals_detail_context_left
                        } else {
                            UiR.string.vitals_detail_context_right
                        },
                    ),
                )
            }
            code.position?.let {
                add(
                    context.getString(
                        when (it) {
                            app.openhealth.core.vitals.BodyPosition.SITTING ->
                                UiR.string.vitals_detail_context_sitting
                            app.openhealth.core.vitals.BodyPosition.STANDING ->
                                UiR.string.vitals_detail_context_standing
                            app.openhealth.core.vitals.BodyPosition.LYING ->
                                UiR.string.vitals_detail_context_lying
                        },
                    ),
                )
            }
            code.meal?.let {
                add(
                    context.getString(
                        when (it) {
                            MealContext.FASTING -> UiR.string.vitals_detail_context_fasting
                            MealContext.PRE_MEAL -> UiR.string.vitals_detail_context_pre_meal
                            MealContext.POST_MEAL -> UiR.string.vitals_detail_context_post_meal
                        },
                    ),
                )
            }
            code.homeSlot?.let {
                add(
                    context.getString(
                        if (it == HomeSlot.MORNING) {
                            UiR.string.vitals_detail_home_protocol_morning
                        } else {
                            UiR.string.vitals_detail_home_protocol_evening
                        },
                    ),
                )
            }
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    private fun vitalTitle(type: ObservationType): String = context.getString(
        when (type) {
            ObservationType.BLOOD_PRESSURE_SYSTOLIC -> UiR.string.vitals_type_bp
            ObservationType.RESTING_HEART_RATE -> UiR.string.vitals_type_heart_rate
            ObservationType.BODY_TEMPERATURE -> UiR.string.vitals_type_temperature
            ObservationType.OXYGEN_SATURATION -> UiR.string.vitals_type_oxygen
            ObservationType.BLOOD_GLUCOSE -> UiR.string.vitals_type_glucose
            else -> UiR.string.vitals_type_heart_rate
        },
    )

    private fun citationLabel(key: CitationKey): String = context.getString(
        when (key) {
            CitationKey.ESH_HYPERTENSION_2023 -> UiR.string.vitals_citation_esh
            CitationKey.AHA_RESTING_HEART_RATE -> UiR.string.vitals_citation_aha
            CitationKey.AHA_LOW_BLOOD_PRESSURE -> UiR.string.vitals_citation_aha
            CitationKey.WHO_PULSE_OXIMETRY_2011 -> UiR.string.vitals_citation_who
            CitationKey.GENEVA_BODY_TEMPERATURE_2019 -> UiR.string.vitals_citation_geneva
            CitationKey.ADA_GLYCEMIC_TARGETS_2026 -> UiR.string.vitals_citation_ada
            CitationKey.US_NAVY_BODY_FAT_1984 -> UiR.string.vitals_citation_us_navy
            CitationKey.WHO_BMI_2000 -> UiR.string.vitals_citation_who_bmi
            CitationKey.FFMI_SCHUTZ_2002 -> UiR.string.vitals_citation_ffmi
            else -> UiR.string.vitals_citation_app
        },
    )

    private fun format(value: Double, decimals: Int): String {
        val rounded = if (value % 1.0 == 0.0 && decimals == 0) {
            value.toLong().toString()
        } else {
            String.format(Locale.getDefault(), "%.${decimals}f", value)
        }
        return rounded
    }

    private fun LocalDate.atStart(): Instant = atTime(LocalTime(0, 0)).toInstant(zone)

    private fun LocalDate.plusDays(days: Int): LocalDate = this + DatePeriod(days = days)

    private operator fun LocalDate.minus(duration: kotlin.time.Duration): LocalDate =
        this - DatePeriod(days = duration.inWholeDays.toInt())

    private companion object {
        const val DEVICE = "android"
        const val PHOTO_COMPARE_LIMIT = 200L
        const val MILLIS_PER_DAY = 86_400_000L
        const val READ_GROUP_WINDOW_MS = 2_000L
        const val POUNDS_PER_KILOGRAM = 2.2046226218
        const val CUSTOM_PREFIX = "custom:"
    }
}
