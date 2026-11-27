@file:Suppress(
    "TooManyFunctions",
    "LargeClass",
    "LongParameterList",
    "LongMethod",
    "MagicNumber",
    "ReturnCount",
    "CyclomaticComplexMethod",
)

package app.openhealth.android.activity

import android.content.Context
import app.openhealth.core.activity.ActivityCalculator
import app.openhealth.core.activity.ActivityEnergy as Energy
import app.openhealth.core.activity.ActivityRecord
import app.openhealth.core.activity.ActivitySource
import app.openhealth.core.activity.ActivityType
import app.openhealth.core.activity.HeartRateZone
import app.openhealth.core.activity.HeartRateZoneModel
import app.openhealth.core.activity.TrainingLoad
import app.openhealth.core.activity.TrainingLoadModel
import app.openhealth.core.activity.YogaFlow
import app.openhealth.core.activity.YogaLibrary
import app.openhealth.core.activity.YogaMuscleLoad
import app.openhealth.core.body.ActivityMuscleLoad
import app.openhealth.core.body.Muscle
import app.openhealth.core.ids.IdGenerator
import app.openhealth.core.ids.RawRecordId
import app.openhealth.core.ids.SourceId
import app.openhealth.core.ingestion.RawRecord
import app.openhealth.core.observation.MeasurementMethod
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.observation.SourceKind
import app.openhealth.core.preferences.DisplayPolicy
import app.openhealth.core.preferences.SensitiveMetric
import app.openhealth.core.seam.ClockSeam
import app.openhealth.core.source.Source
import app.openhealth.data.LocalStore
import app.openhealth.data.ingestion.PayloadHash
import app.openhealth.data.repo.SettingKeys
import app.openhealth.integrations.activity.ActivityFileException
import app.openhealth.integrations.activity.ActivityFiles
import app.openhealth.integrations.activity.ActivityObservationContext
import app.openhealth.integrations.activity.ActivityObservationMapper
import app.openhealth.integrations.activity.GpxWriter
import app.openhealth.integrations.activity.ImportedActivity
import app.openhealth.integrations.activity.StravaArchive
import app.openhealth.ui.R as UiR
import app.openhealth.ui.components.ChartTableModel
import app.openhealth.ui.screens.activity.ActivityDetailModel
import app.openhealth.ui.screens.activity.ActivityHistoryModel
import app.openhealth.ui.screens.activity.ActivityRowModel
import app.openhealth.ui.screens.activity.ActivitySplitRow
import app.openhealth.ui.screens.activity.ActivityZoneRow
import app.openhealth.ui.screens.activity.CardioEntry
import app.openhealth.ui.screens.activity.MovementModel
import app.openhealth.ui.screens.activity.MovementRow
import app.openhealth.ui.screens.activity.YogaFlowRow
import app.openhealth.ui.screens.activity.YogaLibraryModel
import app.openhealth.ui.screens.activity.YogaPoseRow
import app.openhealth.ui.screens.devices.ProvenanceLine
import java.util.Date
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Cardio, GPS and yoga (`pr.md` §6.7, §6.11): the read models and the writes. An activity
 * is a layer-4 row with a route; its distance, active minutes and calories become
 * layer-2 observations so the day's rollups deduplicate it against any other source.
 */
@Suppress("TooManyFunctions", "LargeClass", "LongParameterList", "LongMethod", "MagicNumber")
class ActivityLogPresenter(
    private val context: Context,
    private val store: LocalStore,
    private val clock: ClockSeam,
    private val zone: TimeZone,
    private val ids: IdGenerator,
    private val appVersion: String,
    private val manualSource: SourceId,
    private val recordingSource: SourceId,
    private val importSource: SourceId,
    private val stravaSource: SourceId,
    private val garminSource: SourceId,
) {
    private fun now(): Instant = clock.now()
    private fun today(): LocalDate = now().toLocalDateTime(zone).date

    // ------------------------------------------------------------------ movement entry

    fun movementModel(yogaOn: Boolean, activitiesOn: Boolean): MovementModel? {
        if (!yogaOn && !activitiesOn) return null
        val rows = buildList {
            if (activitiesOn) {
                add(
                    MovementRow(
                        key = KEY_GPS,
                        title = context.getString(UiR.string.movement_record_gps),
                        subtitle = context.getString(UiR.string.movement_record_gps_body),
                    ),
                )
                add(
                    MovementRow(
                        key = KEY_CARDIO,
                        title = context.getString(UiR.string.movement_log_cardio),
                        subtitle = context.getString(UiR.string.movement_log_cardio_body),
                    ),
                )
            }
            if (yogaOn) {
                add(
                    MovementRow(
                        key = KEY_YOGA,
                        title = context.getString(UiR.string.movement_yoga),
                        subtitle = context.getString(UiR.string.movement_yoga_body),
                    ),
                )
            }
            if (activitiesOn) {
                add(
                    MovementRow(
                        key = KEY_HISTORY,
                        title = context.getString(UiR.string.movement_history),
                        subtitle = context.getString(UiR.string.movement_history_body),
                    ),
                )
            }
        }
        return MovementModel(rows = rows, note = context.getString(UiR.string.movement_note))
    }

    // ------------------------------------------------------------------ history

    fun historyModel(activitiesOn: Boolean): ActivityHistoryModel {
        val date = today()
        val weekStart = date.minus(DatePeriod(days = 6))
        val week = store.activities.between(weekStart, date)
        val rows = store.activities.recent(HISTORY_LIMIT).map { it.toRow() }
        return ActivityHistoryModel(
            rows = rows,
            weekCount = week.size,
            weekMinutes = (week.sumOf { it.durationSeconds } / 60).toInt(),
            weekDistanceText = week.sumOf { metricsDistance(it) }.takeIf { it > 0.0 }
                ?.let { formatDistance(it) },
            note = context.getString(UiR.string.activity_history_note),
            moduleOff = !activitiesOn,
        )
    }

    private fun ActivityRecord.toRow(): ActivityRowModel {
        val metrics = ActivityCalculator.metrics(this)
        return ActivityRowModel(
            id = id,
            title = name ?: typeLabel(type),
            subtitle = listOf(
                shortDate(localDate),
                sourceLabel(source),
            ).joinToString(" · "),
            distanceText = metrics.distanceM.takeIf { it > 0.0 }?.let(::formatDistance),
            durationText = formatDuration(durationSeconds),
            sourceLabel = sourceLabel(source),
            hasRoute = points.isNotEmpty() || hasRoute,
            type = type,
        )
    }

    // ------------------------------------------------------------------ detail

    fun detail(id: String, policy: DisplayPolicy = DisplayPolicy.Standard): ActivityDetailModel? {
        val record = store.activities.byIdWithPoints(id) ?: return null
        val metrics = ActivityCalculator.metrics(record)
        val maxHr = record.maxHeartRate ?: estimateMaxHr()
        val estimate = record.maxHeartRate == null
        val zones = maxHr?.let { max ->
            HeartRateZoneModel.from(record.points, max, estimate)
        }
        val zoneRows = zones?.slices?.map { slice ->
            val lower = maxHr * slice.zone.lowerFraction
            val upper = if (slice.zone == HeartRateZone.Z5) {
                maxHr
            } else {
                maxHr * HeartRateZone.entries[slice.zone.ordinal + 1].lowerFraction
            }
            ActivityZoneRow(
                label = context.getString(UiR.string.activity_zone_label, slice.zone.ordinal + 1),
                rangeText = context.getString(
                    UiR.string.activity_zone_range,
                    lower.roundToInt().toString(),
                    upper.roundToInt().toString(),
                ),
                timeText = formatDuration(slice.seconds),
                share = slice.share.toFloat(),
            )
        }.orEmpty().filter { it.timeText != "0:00" }
        val table = if (zoneRows.isNotEmpty()) {
            ChartTableModel(
                title = context.getString(UiR.string.activity_detail_zones),
                columns = listOf(
                    context.getString(UiR.string.activity_table_zone),
                    context.getString(UiR.string.activity_table_range),
                    context.getString(UiR.string.activity_table_time),
                    context.getString(UiR.string.activity_table_share),
                ),
                rows = zones!!.slices.map { slice ->
                    listOf(
                        context.getString(UiR.string.activity_zone_label, slice.zone.ordinal + 1),
                        "${(maxHr * slice.zone.lowerFraction).roundToInt()}–" +
                            "${(
                                maxHr * if (slice.zone == HeartRateZone.Z5) {
                                    1.0
                                } else {
                                    HeartRateZone.entries[slice.zone.ordinal + 1].lowerFraction
                                }
                                ).roundToInt()} bpm",
                        formatDuration(slice.seconds),
                        "${(slice.share * 100).roundToInt()} %",
                    )
                },
                citation = "Edwards 1993",
            )
        } else {
            null
        }
        val flowName = record.flowId?.let { YogaLibrary.flow(it)?.name }
        return ActivityDetailModel(
            id = record.id,
            title = record.name ?: flowName ?: typeLabel(record.type),
            typeLabel = typeLabel(record.type),
            sourceLabel = sourceLabel(record.source),
            dateText = mediumDate(record.localDate),
            startedText = timeOf(record.startedAt),
            durationText = formatDuration(record.durationSeconds),
            distanceText = metrics.distanceM.takeIf { it > 0.0 }?.let(::formatDistance),
            paceText = metrics.averagePaceSecondsPerKm
                ?.takeIf { record.type.distanceBased }
                ?.let(::formatPace),
            elevationText = metrics.elevationGainM.takeIf { it > 0.0 }
                ?.let { context.getString(UiR.string.activity_split_elevation, it.roundToInt()) },
            heartRateText = metrics.averageHeartRate?.roundToInt()
                ?.let { "$it bpm" },
            // Calories are one of the four values weight-neutral mode hides
            // (`pr.md` §6.25); the activity keeps every other number.
            energyText = policy.value(SensitiveMetric.CALORIES, metrics.energyKcal)
                ?.roundToInt()?.let { "$it kcal" },
            bestEfforts = ActivityCalculator.bestEfforts(record.points)
                .filter { it.achieved }
                .map { effort ->
                    val label = if (effort.distanceM >= 1_000.0) {
                        "${(effort.distanceM / 1_000.0).roundToInt()} km"
                    } else {
                        "${effort.distanceM.roundToInt()} m"
                    }
                    label to formatDuration(effort.durationSeconds.roundToInt().toLong())
                },
            splits = metrics.splits.map { split ->
                ActivitySplitRow(
                    index = split.index,
                    distanceText = if (split.distanceM >= 1_000.0) {
                        String.format(java.util.Locale.ROOT, "%.2f km", split.distanceM / 1_000.0)
                    } else {
                        context.getString(UiR.string.activity_m, split.distanceM.roundToInt())
                    },
                    paceText = formatPace(split.paceSecondsPerKm),
                    elevationText = if (split.elevationGainM > 0.0) {
                        context.getString(
                            UiR.string.activity_split_elevation,
                            split.elevationGainM.roundToInt(),
                        )
                    } else {
                        ""
                    },
                    heartRateText = split.averageHeartRate?.roundToInt()?.let {
                        "$it bpm"
                    }.orEmpty(),
                )
            },
            zones = zoneRows,
            zoneNote = if (estimate) {
                context.getString(
                    UiR.string.activity_zone_note_estimate,
                    (maxHr ?: 0.0).roundToInt(),
                )
            } else {
                context.getString(
                    UiR.string.activity_zone_note_measured,
                    (maxHr ?: 0.0).roundToInt(),
                )
            },
            table = table,
            route = record.points,
            notes = record.notes,
            provenance = provenance(record, metrics.distanceM),
            isYoga = record.type == ActivityType.YOGA,
            yogaFlowName = flowName,
            muscleLabels = record.muscles.map { muscleLabel(it) },
        )
    }

    private fun provenance(record: ActivityRecord, distanceM: Double): List<ProvenanceLine> =
        listOf(
            ProvenanceLine(
                label = context.getString(UiR.string.activity_provenance_source),
                detail = record.sourceDetail(),
                value = sourceLabel(record.source),
            ),
            ProvenanceLine(
                label = context.getString(UiR.string.activity_provenance_started),
                detail = "${mediumDate(record.localDate)} · ${timeOf(record.startedAt)}",
                value = null,
            ),
            ProvenanceLine(
                label = context.getString(UiR.string.activity_provenance_recorded),
                detail = timeOf(now()),
                value = null,
            ),
            ProvenanceLine(
                label = context.getString(UiR.string.activity_provenance_route),
                detail = if (record.points.isEmpty()) {
                    context.getString(UiR.string.activity_detail_no_route)
                } else {
                    context.getString(UiR.string.activity_m, record.points.size)
                },
                value = distanceM.takeIf { it > 0.0 }?.let(::formatDistance),
            ),
        )

    // ------------------------------------------------------------------ writes

    /** Saves a manual cardio session; the date can be yesterday. */
    fun logCardio(entry: CardioEntry): String? {
        if (entry.minutes <= 0) return null
        val endsAt = if (entry.date == today()) {
            now()
        } else {
            entry.date.plus(DatePeriod(days = 1)).atStartOfDayIn(zone).minus(1.seconds)
        }
        val startsAt = endsAt.minus(entry.minutes.minutes)
        val record = ActivityRecord(
            id = ids.newId(),
            type = entry.type,
            source = ActivitySource.MANUAL,
            startedAt = startsAt,
            endedAt = endsAt,
            localDate = entry.date,
            zoneId = zone.id,
            distanceM = (entry.distanceKm ?: 0.0) * 1_000.0,
            elevationGainM = null,
            averageHeartRate = entry.averageHeartRate,
            maxHeartRate = entry.maxHeartRate,
            energyKcal = entry.energyKcal,
            notes = entry.notes,
        )
        return save(
            record = record,
            sourceId = manualSource,
            method = MeasurementMethod.MANUAL_ENTRY,
            device = null,
        )
    }

    /** Saves a finished GPS recording. */
    fun finishRecording(record: ActivityRecord, device: String? = null): String? = save(
        record = record,
        sourceId = recordingSource,
        method = MeasurementMethod.DEVICE_SENSOR,
        device = device,
    )

    /** Saves a yoga flow as a session with its muscles and MET energy. */
    fun saveYoga(
        flowId: String,
        startedAt: Instant,
        endedAt: Instant,
        muscles: YogaMuscleLoad,
        notes: String? = null,
    ): String? {
        val flow = YogaLibrary.flow(flowId) ?: return null
        if (endedAt <= startedAt) return null
        val record = ActivityRecord(
            id = ids.newId(),
            type = ActivityType.YOGA,
            source = ActivitySource.MANUAL,
            startedAt = startedAt,
            endedAt = endedAt,
            localDate = startedAt.toLocalDateTime(zone).date,
            zoneId = zone.id,
            name = flow.name,
            notes = notes,
            flowId = flow.id,
            muscles = muscles.weights.keys.toList(),
        )
        return save(
            record = record,
            sourceId = manualSource,
            method = MeasurementMethod.MANUAL_ENTRY,
            device = null,
        )
    }

    private fun save(
        record: ActivityRecord,
        sourceId: SourceId,
        method: MeasurementMethod,
        device: String?,
        rawRecordId: RawRecordId? = null,
    ): String? {
        val at = now()
        val weight = store.profile.active()?.weight_kg
        val weighed = Energy.withEstimate(record, weight)
        val rawId = rawRecordId ?: RawRecordId(ids.newId())
        if (rawRecordId == null) {
            val payload = summary(weighed)
            val raw = RawRecord(
                id = rawId,
                sourceId = sourceId,
                sourceKind = sourceKindOf(sourceId),
                receivedAt = at,
                recordedAt = at,
                happenedAt = weighed.startedAt,
                payloadHash = PayloadHash.sha256(payload),
                idempotencyKey = "activity-raw:${weighed.id}",
                schemaVersion = RAW_SCHEMA,
                device = device,
                appVersion = appVersion,
                payload = payload,
            )
            store.raw.insert(raw)
        }
        val key = "activity:${sourceId.value}:${weighed.id}"
        store.activities.record(
            activity = weighed,
            sourceId = sourceId.value,
            idempotencyKey = key,
            rawRecordId = rawId.value,
            device = device,
            appVersion = appVersion,
            now = at,
        )
        val observations = ActivityObservationMapper.toObservations(
            weighed,
            ActivityObservationContext(
                sourceId = sourceId,
                zone = zone,
                recordedAt = at,
                method = method,
                device = device,
                appVersion = appVersion,
                rawRecordId = rawId,
                ids = ids,
            ),
        )
        store.observations.recordAll(observations)
        return weighed.id
    }

    // ------------------------------------------------------------------ import

    fun import(fileName: String, bytes: ByteArray): ActivityImportOutcome {
        val hash = PayloadHash.sha256(bytes)
        if (store.raw.byHash(hash) != null) {
            return ActivityImportOutcome(duplicate = true)
        }
        return if (fileName.lowercase().endsWith(".zip")) {
            importStrava(bytes, hash)
        } else {
            importOneFile(fileName, bytes, hash)
        }
    }

    private fun importOneFile(
        fileName: String,
        bytes: ByteArray,
        hash: String,
    ): ActivityImportOutcome {
        val parsed = runCatching { ActivityFiles.import(fileName, bytes) }
            .getOrElse { error ->
                return ActivityImportOutcome(
                    error = if (error is ActivityFileException) {
                        context.getString(UiR.string.activity_import_invalid)
                    } else {
                        context.getString(UiR.string.activity_import_invalid)
                    },
                )
            }
        if (parsed.isEmpty()) return ActivityImportOutcome(empty = true)
        val isFit = fileName.lowercase().endsWith(".fit")
        val sourceId = if (isFit) garminSource else importSource
        ensureSource(sourceId, if (isFit) SourceKind.GARMIN else SourceKind.CSV)
        val at = now()
        val rawId = RawRecordId(ids.newId())
        val raw = RawRecord(
            id = rawId,
            sourceId = sourceId,
            sourceKind = if (isFit) SourceKind.GARMIN else SourceKind.CSV,
            receivedAt = at,
            recordedAt = at,
            happenedAt = parsed.firstOrNull()?.startedAt,
            payloadHash = hash,
            idempotencyKey = "activity-file:$hash",
            schemaVersion = RAW_SCHEMA,
            device = parsed.firstOrNull()?.device,
            appVersion = appVersion,
            payload = parsed.joinToString("\n") { summary(it.toRecord(ids.newId())) },
        )
        store.raw.insert(raw)
        val idsSaved = parsed.map { imported ->
            save(
                imported.toRecord(ids.newId()),
                sourceId,
                MeasurementMethod.FILE_IMPORT,
                imported.device,
                rawId,
            )
        }
        return ActivityImportOutcome(imported = idsSaved.count { it != null })
    }

    private fun importStrava(bytes: ByteArray, hash: String): ActivityImportOutcome {
        val archive = runCatching { StravaArchive.read(bytes) }
            .getOrElse {
                return ActivityImportOutcome(
                    error = context.getString(UiR.string.activity_import_invalid),
                )
            }
        if (archive.isEmpty) return ActivityImportOutcome(empty = true)
        ensureSource(stravaSource, SourceKind.STRAVA)
        val at = now()
        val rawId = RawRecordId(ids.newId())
        val raw = RawRecord(
            id = rawId,
            sourceId = stravaSource,
            sourceKind = SourceKind.STRAVA,
            receivedAt = at,
            recordedAt = at,
            happenedAt = archive.activities.firstOrNull()?.startedAt,
            payloadHash = hash,
            idempotencyKey = "strava-archive:$hash",
            schemaVersion = RAW_SCHEMA,
            device = null,
            appVersion = appVersion,
            payload = "activities=${archive.activities.size};csv=${archive.csvRows}",
        )
        store.raw.insert(raw)
        val saved = archive.activities.count { imported ->
            save(
                imported.toRecord(ids.newId()),
                stravaSource,
                MeasurementMethod.FILE_IMPORT,
                null,
                rawId,
            ) != null
        }
        return ActivityImportOutcome(imported = saved, skipped = archive.skipped.size)
    }

    private fun ImportedActivity.toRecord(id: String): ActivityRecord {
        val weight = store.profile.active()?.weight_kg
        val record = ActivityRecord(
            id = id,
            type = type,
            source = ActivitySource.IMPORTED,
            startedAt = startedAt,
            endedAt = endedAt,
            localDate = startedAt.toLocalDateTime(zone).date,
            zoneId = zone.id,
            distanceM = distanceM,
            averageHeartRate = averageHeartRate,
            maxHeartRate = maximumHeartRate,
            averageCadence = averageCadence,
            averagePowerWatts = averagePowerWatts,
            energyKcal = calories,
            name = name,
            points = points,
        )
        return Energy.withEstimate(record, weight)
    }

    fun exportGpx(id: String): ByteArray? {
        val record = store.activities.byIdWithPoints(id) ?: return null
        if (record.points.isEmpty()) return null
        return GpxWriter.write(record).toByteArray(Charsets.UTF_8)
    }

    fun exportTcx(id: String): ByteArray? {
        val record = store.activities.byIdWithPoints(id) ?: return null
        if (record.points.isEmpty()) return null
        return app.openhealth.integrations.activity.TcxWriter.write(record)
            .toByteArray(Charsets.UTF_8)
    }

    fun delete(id: String): Boolean = store.trash.deleteActivity(id) != null

    // ------------------------------------------------------------------ yoga library

    fun yogaLibrary(): YogaLibraryModel {
        val favourites = favourites()
        return YogaLibraryModel(
            flows = YogaLibrary.flows.map { flow ->
                YogaFlowRow(
                    id = flow.id,
                    name = flow.name,
                    subtitle = flow.subtitle,
                    levelLabel = context.getString(levelRes(flow.level)),
                    focus = flow.focus,
                    minutes = flow.minutes,
                    poseCount = flow.poseCount,
                    favourite = flow.id in favourites,
                    primaryMuscles = app.openhealth.core.activity.YogaLoad.of(flow).primary,
                    secondaryMuscles = app.openhealth.core.activity.YogaLoad.of(flow).secondary,
                )
            },
            poses = YogaLibrary.poses.map { pose ->
                YogaPoseRow(
                    id = pose.id,
                    name = pose.name,
                    sanskrit = pose.sanskrit,
                    levelLabel = context.getString(levelRes(pose.level)),
                    muscleLabels = pose.primaryMuscles.map(::muscleLabel),
                    why = pose.why,
                    modification = pose.modification,
                    contraindications = pose.contraindications,
                    instructions = pose.setup,
                    breathCue = pose.breathCue,
                    primaryMuscles = pose.primaryMuscles,
                    secondaryMuscles = pose.secondaryMuscles,
                )
            },
            note = context.getString(UiR.string.yoga_library_note),
            weekMinutes = weeklyYogaMinutes(today()),
            streakDays = yogaStreak(today()),
        )
    }

    fun toggleFavourite(flowId: String, favourite: Boolean) {
        val current = favourites().toMutableSet()
        if (favourite) current += flowId else current -= flowId
        store.settings.put(SettingKeys.FAVOURITE_FLOWS, current.joinToString(","))
    }

    private fun yogaStreak(date: LocalDate): Int {
        val days = store.activities
            .between(date.minus(DatePeriod(days = 60)), date)
            .filter { it.type == ActivityType.YOGA }
            .map { it.localDate }
            .toSet()
        var cursor = if (date in days) date else date.minus(DatePeriod(days = 1))
        var streak = 0
        while (cursor in days) {
            streak += 1
            cursor = cursor.minus(DatePeriod(days = 1))
        }
        return streak
    }

    private fun favourites(): Set<String> = store.settings.get(SettingKeys.FAVOURITE_FLOWS)
        .orEmpty()
        .split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()

    fun flow(id: String): YogaFlow? = YogaLibrary.flow(id)

    // ------------------------------------------------------------------ training load and recovery

    fun trainingLoad(date: LocalDate = today()): TrainingLoadModel {
        val from = date.minus(DatePeriod(days = TRAINING_LOAD_DAYS))
        val activities = store.activities.between(from, date)
        val sessions = store.training.sessionsBetween(from, date.plus(DatePeriod(days = 1)))
            .filter { it.ended_at != null }
            .map { session ->
                TrainingLoad.StrengthLoad(
                    date = LocalDate.parse(session.local_date),
                    durationSeconds = (session.ended_at!! - session.started_at) / 1_000,
                    perceivedEffort = session.perceived_effort,
                )
            }
        val resting = store.observations.latest(ObservationType.RESTING_HEART_RATE)
            ?.value?.value
        val sex = store.profile.active()?.sex_at_birth
        val male = sex?.equals("male", ignoreCase = true) == true
        val days = TrainingLoad.loadDays(
            activities = activities,
            strengthSessions = sessions,
            restingHeartRate = resting,
            maxHeartRate = estimateMaxHr(),
            female = sex?.equals("female", ignoreCase = true) == true,
            neutralSex = !male && sex?.equals("female", ignoreCase = true) != true,
        )
        val outcome = TrainingLoad.compute(date, days)
        return (outcome as? app.openhealth.core.formula.FormulaOutcome.Computed)?.value
            ?: TrainingLoadModel(emptyList(), 0.0, 0.0, 0.0, false)
    }

    fun recoveryLoads(since: LocalDate): List<ActivityMuscleLoad> =
        store.activities.between(since, today()).map { record ->
            val primary = if (record.muscles.isNotEmpty()) {
                record.muscles
            } else {
                record.type.primaryMuscles
            }
            val secondary = if (record.muscles.isNotEmpty()) {
                emptyList()
            } else {
                record.type.secondaryMuscles
            }
            ActivityMuscleLoad(
                activityId = record.id,
                at = record.endedAt,
                primary = primary,
                secondary = secondary,
                sourceId = null,
                recordedAt = null,
            )
        }

    /** Energy from activities for the Today card, in the order they happened. */
    fun energyForDay(date: LocalDate, weightKg: Double?): List<Double> =
        store.activities.forDay(date).mapNotNull { Energy.estimate(it, weightKg) }

    fun weeklyYogaMinutes(date: LocalDate): Int {
        val from = date.minus(DatePeriod(days = 6))
        return (
            store.activities.between(from, date)
                .filter { it.type == ActivityType.YOGA }
                .sumOf { it.durationSeconds } / 60
            ).toInt()
    }

    // ------------------------------------------------------------------ helpers

    private fun ensureSource(id: SourceId, kind: SourceKind) {
        if (store.sources.byId(id) != null) return
        store.sources.upsert(
            Source(
                id = id,
                kind = kind,
                label = when (kind) {
                    SourceKind.STRAVA -> context.getString(UiR.string.activity_import_archive)
                    SourceKind.GARMIN -> context.getString(UiR.string.activity_import_garmin)
                    else -> context.getString(UiR.string.activity_import_file)
                },
                family = kind.defaultFamily,
            ),
        )
    }

    private fun sourceKindOf(id: SourceId): SourceKind =
        store.sources.byId(id)?.kind ?: SourceKind.DEVICE

    private fun sourceLabel(source: ActivitySource): String = context.getString(
        when (source) {
            ActivitySource.RECORDED -> UiR.string.activity_source_recorded
            ActivitySource.IMPORTED -> UiR.string.activity_source_imported
            ActivitySource.MANUAL -> UiR.string.activity_source_manual
        },
    )

    private fun ActivityRecord.sourceDetail(): String = sourceLabel(source)

    private fun typeLabel(type: ActivityType): String = context.getString(
        when (type) {
            ActivityType.RUN -> UiR.string.activity_type_run
            ActivityType.WALK -> UiR.string.activity_type_walk
            ActivityType.HIKE -> UiR.string.activity_type_hike
            ActivityType.RIDE -> UiR.string.activity_type_ride
            ActivityType.SWIM -> UiR.string.activity_type_swim
            ActivityType.ROW -> UiR.string.activity_type_row
            ActivityType.SKI -> UiR.string.activity_type_ski
            ActivityType.YOGA -> UiR.string.activity_type_yoga
            ActivityType.MOBILITY -> UiR.string.activity_type_mobility
            ActivityType.OTHER -> UiR.string.activity_type_other
        },
    )

    private fun levelRes(level: app.openhealth.core.activity.YogaLevel): Int = when (level) {
        app.openhealth.core.activity.YogaLevel.BEGINNER -> UiR.string.yoga_level_beginner
        app.openhealth.core.activity.YogaLevel.INTERMEDIATE -> UiR.string.yoga_level_intermediate
        app.openhealth.core.activity.YogaLevel.ADVANCED -> UiR.string.yoga_level_advanced
    }

    private fun muscleLabel(muscle: Muscle): String = muscle.key.replace('-', ' ')
        .replaceFirstChar { it.uppercase() }

    private fun estimateMaxHr(): Double? {
        val dob = store.profile.active()?.date_of_birth ?: return null
        val birth = runCatching { LocalDate.parse(dob) }.getOrNull() ?: return null
        val today = today()
        var age = today.year - birth.year
        if (today.monthNumber < birth.monthNumber ||
            (today.monthNumber == birth.monthNumber && today.dayOfMonth < birth.dayOfMonth)
        ) {
            age -= 1
        }
        return HeartRateZoneModel.estimateMaxHeartRate(age)
    }

    private fun metricsDistance(record: ActivityRecord): Double =
        ActivityCalculator.metrics(record).distanceM

    private fun formatDistance(metres: Double): String = if (metres >= 1_000.0) {
        context.getString(
            UiR.string.activity_km,
            String.format(
                java.util.Locale.ROOT,
                "%.2f",
                metres / 1_000.0,
            ),
        )
    } else {
        context.getString(UiR.string.activity_m, metres.roundToInt())
    }

    private fun formatDuration(seconds: Long): String {
        val safe = seconds.coerceAtLeast(0)
        val hours = safe / 3_600
        val minutes = (safe % 3_600) / 60
        val secs = safe % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, secs)
        } else {
            "%d:%02d".format(minutes, secs)
        }
    }

    private fun formatPace(secondsPerKm: Double): String {
        if (secondsPerKm <= 0.0 || !secondsPerKm.isFinite()) return "-"
        val total = secondsPerKm.roundToInt()
        return context.getString(
            UiR.string.activity_pace_km,
            "%d:%02d".format(total / 60, total % 60),
        )
    }

    private fun shortDate(date: LocalDate): String =
        java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
            .format(Date(date.atStartOfDayIn(zone).toEpochMilliseconds()))

    private fun mediumDate(date: LocalDate): String = shortDate(date)

    private fun timeOf(at: Instant): String {
        val formatter = android.text.format.DateFormat.getTimeFormat(context)
        formatter.timeZone = java.util.TimeZone.getTimeZone(zone.id)
        return formatter.format(Date(at.toEpochMilliseconds()))
    }

    private fun summary(record: ActivityRecord): String = buildString {
        append("type=").append(record.type.name)
        append(";source=").append(record.source.name)
        append(";start=").append(record.startedAt)
        append(";end=").append(record.endedAt)
        append(";distance=").append(record.distanceM)
        append(";points=").append(record.points.size)
        append(";kcal=").append(record.energyKcal ?: 0.0)
    }

    companion object {
        const val KEY_GPS = "gps"
        const val KEY_CARDIO = "cardio"
        const val KEY_YOGA = "yoga"
        const val KEY_HISTORY = "history"
        private const val HISTORY_LIMIT = 100L
        private const val TRAINING_LOAD_DAYS = 120
        private const val RAW_SCHEMA = 1
    }
}

data class ActivityImportOutcome(
    val imported: Int = 0,
    val skipped: Int = 0,
    val duplicate: Boolean = false,
    val empty: Boolean = false,
    val error: String? = null,
) {
    val ok: Boolean get() = error == null
}
