package app.openhealth.data.repo

import app.openhealth.core.ids.EntryId
import app.openhealth.core.ids.MeasurementId
import app.openhealth.core.ids.NoteId
import app.openhealth.core.ids.ObservationId
import app.openhealth.core.ids.PhotoId
import app.openhealth.core.ids.SetId
import app.openhealth.core.ids.TombstoneId
import app.openhealth.core.ids.VitalId
import app.openhealth.core.seam.ClockSeam
import app.openhealth.core.seam.SystemClock
import app.openhealth.data.db.Activity
import app.openhealth.data.db.Activity_point
import app.openhealth.data.db.Body_photo
import app.openhealth.data.db.Entry
import app.openhealth.data.db.Measurement
import app.openhealth.data.db.Note
import app.openhealth.data.db.Observation
import app.openhealth.data.db.OpenHealthDb
import app.openhealth.data.db.Session
import app.openhealth.data.db.Session_exercise
import app.openhealth.data.db.Tombstone
import app.openhealth.data.db.Vital
import app.openhealth.data.db.Work_set
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The tables Trash can restore today. A leaf entity is restorable on its own. */
enum class TrashEntity(val table: String) {
    SESSION("session"),
    ROUTINE_EXERCISE("routine_exercise"),
    WORK_SET("work_set"),
    ENTRY("entry"),
    MEASUREMENT("measurement"),
    VITAL("vital"),
    PHOTO("body_photo"),
    NOTE("note"),
    OBSERVATION("observation"),
    ACTIVITY("activity"),
}

@Serializable
data class WorkSetPayload(
    val id: String,
    val sessionId: String,
    val sessionExerciseId: String?,
    val exerciseId: String,
    val position: Long,
    val weightKg: Double,
    val reps: Long,
    val rir: Double?,
    val rpe: Double?,
    val isWarmup: Boolean,
    val isBackoff: Boolean,
    val isFailure: Boolean,
    val isBodyweight: Boolean,
    val tempo: String?,
    val restSeconds: Long?,
    val completedAt: Long,
    val happenedAt: Long,
    val recordedAt: Long,
    val sourceId: String,
    val idempotencyKey: String,
    val observationId: String?,
    val rawRecordId: String?,
    val device: String?,
    val appVersion: String?,
    val revision: Long,
    val isDrop: Boolean = false,
    val isAssisted: Boolean = false,
    val assistanceKg: Double? = null,
    val bandKg: Double? = null,
    val notes: String? = null,
    /** The session's local day, so restoring or deleting a set rebuilds the right rollup. */
    val localDate: String? = null,
)

/** The session row as a restorable payload; the sets and exercises travel with it. */
@Serializable
data class SessionRowPayload(
    val id: String,
    val startedAt: Long,
    val endedAt: Long?,
    val localDate: String,
    val zoneId: String,
    val happenedAt: Long,
    val recordedAt: Long,
    val routineId: String?,
    val programmeId: String?,
    val notes: String?,
    val sourceId: String,
    val idempotencyKey: String,
    val observationId: String?,
    val rawRecordId: String?,
    val device: String?,
    val appVersion: String?,
    val revision: Long,
    val updatedAt: Long,
    val title: String? = null,
    val feeling: String? = null,
    val perceivedEffort: Double? = null,
)

@Serializable
data class RoutineExercisePayload(
    val id: String,
    val routineId: String,
    val exerciseId: String,
    val position: Long,
    val targetSets: Long?,
    val targetReps: Long?,
    val targetRepsHigh: Long?,
    val targetWeightKg: Double?,
    val notes: String?,
    val targetRir: Double?,
    val restSeconds: Long?,
    val progression: String?,
    val groupIndex: Long?,
)

@Serializable
data class SessionExercisePayload(
    val id: String,
    val sessionId: String,
    val exerciseId: String,
    val position: Long,
    val notes: String?,
    val groupIndex: Long? = null,
    val targetSets: Long? = null,
    val targetReps: Long? = null,
    val targetRepsHigh: Long? = null,
    val targetWeightKg: Double? = null,
    val progression: String? = null,
    val restSeconds: Long? = null,
)

@Serializable
data class SessionPayload(
    val session: SessionRowPayload,
    val exercises: List<SessionExercisePayload> = emptyList(),
    val sets: List<WorkSetPayload> = emptyList(),
)

@Serializable
data class EntryPayload(
    val id: String,
    val mealId: String?,
    val foodId: String?,
    val localDate: String,
    val zoneId: String,
    val happenedAt: Long,
    val recordedAt: Long,
    val quantityG: Double?,
    val energyKcal: Double?,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
    val fibreG: Double?,
    val sourceKind: String,
    val sourceId: String?,
    val userVerified: Boolean,
    val userEdited: Boolean,
    val observationId: String?,
    val rawRecordId: String?,
    val idempotencyKey: String,
    val device: String?,
    val appVersion: String?,
    val revision: Long,
    // v7 (B6). Defaults keep a tombstone written before the upgrade restorable.
    val servingLabel: String? = null,
    val servingG: Double? = null,
    val servings: Double? = null,
    val foodName: String? = null,
    val brand: String? = null,
    val microsJson: String? = null,
)

@Serializable
data class MeasurementPayload(
    val id: String,
    val type: String,
    val value: Double,
    val unit: String,
    val localDate: String,
    val zoneId: String,
    val happenedAt: Long,
    val recordedAt: Long,
    val sourceId: String,
    val method: String?,
    val notes: String?,
    val excludedFromTrends: Boolean,
    val observationId: String?,
    val rawRecordId: String?,
    val idempotencyKey: String,
    val device: String?,
    val appVersion: String?,
    val revision: Long,
)

@Serializable
data class VitalPayload(
    val id: String,
    val type: String,
    val value: Double,
    val unit: String,
    val localDate: String,
    val zoneId: String,
    val happenedAt: Long,
    val recordedAt: Long,
    val sourceId: String,
    val method: String?,
    val context: String?,
    val note: String?,
    val excludedFromTrends: Boolean,
    val observationId: String?,
    val rawRecordId: String?,
    val idempotencyKey: String,
    val device: String?,
    val appVersion: String?,
    val revision: Long,
)

/** A progress photo is a layer-4 row; the bytes travel with it so undo is exact. */
@Serializable
data class PhotoPayload(
    val id: String,
    val localDate: String,
    val zoneId: String,
    val happenedAt: Long,
    val recordedAt: Long,
    val sourceId: String,
    val caption: String?,
    val notes: String?,
    val mime: String,
    val width: Long,
    val height: Long,
    val bytesB64: String,
    val thumbB64: String?,
    val sha256: String,
    val idempotencyKey: String,
    val device: String?,
    val appVersion: String?,
    val revision: Long,
)

/** The whole observation row, provenance included, so undo restores it unchanged. */
@Serializable
data class ObservationPayload(
    val id: String,
    val type: String,
    val value: Double,
    val unit: String,
    val startAt: Long,
    val endAt: Long?,
    val localDate: String,
    val zoneId: String,
    val happenedAt: Long,
    val recordedAt: Long,
    val sourceId: String,
    val device: String?,
    val appVersion: String?,
    val method: String?,
    val context: String?,
    val confidence: Double?,
    val quality: String?,
    val userVerified: Boolean,
    val userEdited: Boolean,
    val originalValue: Double?,
    val originalUnit: String?,
    val editReason: String?,
    val editedAt: Long?,
    val groupKey: String?,
    val excludedFromTrends: Boolean,
    val idempotencyKey: String,
    val rawRecordId: String?,
)

@Serializable
data class NotePayload(
    val id: String,
    val body: String,
    val localDate: String,
    val zoneId: String,
    val happenedAt: Long,
    val recordedAt: Long,
    val editedAt: Long?,
    val sourceId: String?,
    val rawRecordId: String?,
    val idempotencyKey: String,
    val device: String?,
    val appVersion: String?,
    val revision: Long,
)

/** One route sample, with the same fields the activity_point table stores. */
@Serializable
data class ActivityPointPayload(
    val position: Long,
    val latitude: Double,
    val longitude: Double,
    val elevationM: Double? = null,
    val elapsedS: Long,
    val heartRate: Double? = null,
    val cadence: Double? = null,
    val powerW: Double? = null,
    val speedMps: Double? = null,
)

/** An activity and its route travel together, so undo restores the track exactly. */
@Serializable
data class ActivityPayload(
    val id: String,
    val type: String,
    val source: String,
    val name: String?,
    val startedAt: Long,
    val endedAt: Long,
    val durationS: Long,
    val localDate: String,
    val zoneId: String,
    val happenedAt: Long,
    val recordedAt: Long,
    val distanceM: Double,
    val elevationGainM: Double?,
    val avgHeartRate: Double?,
    val maxHeartRate: Double?,
    val avgCadence: Double?,
    val avgPowerW: Double?,
    val energyKcal: Double?,
    val notes: String?,
    val flowId: String?,
    val muscleKeys: String?,
    val device: String?,
    val appVersion: String?,
    val sourceId: String,
    val rawRecordId: String?,
    val idempotencyKey: String,
    val fingerprint: String?,
    val revision: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val points: List<ActivityPointPayload> = emptyList(),
)

/**
 * Undo, Trash and recovery (`pr.md` §6.29). A delete writes a tombstone holding the row
 * it removes, then removes the row, in one transaction. Undo restores it for the rest of
 * the session; Trash keeps it for 30 days; purge is explicit and only touches rows past
 * their window. No code path here deletes anything without a tombstone.
 */
@Suppress("LargeClass")
class TrashService(
    private val db: OpenHealthDb,
    private val clock: ClockSeam = SystemClock,
    private val retentionDays: Long = DEFAULT_RETENTION_DAYS,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var rollupInvalidator: ((LocalDate) -> Unit)? = null

    fun invalidateRollupsWith(invalidator: ((LocalDate) -> Unit)?) {
        rollupInvalidator = invalidator
    }

    fun deleteWorkSet(id: SetId): TombstoneId? = workSet(id.value)?.let { row ->
        record(
            entity = TrashEntity.WORK_SET,
            entityId = row.id,
            payload = json.encodeToString(
                WorkSetPayload.serializer(),
                row.toPayload(sessionDate(row.session_id)),
            ),
            invalidateOn = sessionDate(row.session_id),
        ) {
            db.trainingQueries.deleteWorkSet(row.id)
        }
    }

    /**
     * Deletes a whole workout with undo (`pr.md` §6.5, §6.29). The tombstone carries the
     * session, its exercise rows and its sets, so one restore puts the workout back whole.
     */
    /** Removes one routine exercise with a tombstone; a routine edit is undoable. */
    fun deleteRoutineExercise(id: String): TombstoneId? =
        db.trainingQueries.routineEntryById(id).executeAsOneOrNull()?.let { row ->
            record(
                entity = TrashEntity.ROUTINE_EXERCISE,
                entityId = id,
                payload = json.encodeToString(
                    RoutineExercisePayload.serializer(),
                    RoutineExercisePayload(
                        id = row.id,
                        routineId = row.routine_id,
                        exerciseId = row.exercise_id,
                        position = row.position,
                        targetSets = row.target_sets,
                        targetReps = row.target_reps,
                        targetRepsHigh = row.target_reps_high,
                        targetWeightKg = row.target_weight_kg,
                        notes = row.notes,
                        targetRir = row.target_rir,
                        restSeconds = row.rest_seconds,
                        progression = row.progression,
                        groupIndex = row.group_index,
                    ),
                ),
            ) {
                db.trainingQueries.deleteRoutineExercise(row.id)
            }
        }

    fun deleteSession(id: String): TombstoneId? = session(id)?.let { row ->
        val exercises = db.trainingQueries.sessionExercises(row.id).executeAsList()
        val sets = db.trainingQueries.setsForSession(row.id).executeAsList()
        val payload = SessionPayload(
            session = row.toRowPayload(),
            exercises = exercises.map { it.toPayload() },
            sets = sets.map { it.toPayload(LocalDate.parse(row.local_date)) },
        )
        record(
            entity = TrashEntity.SESSION,
            entityId = row.id,
            payload = json.encodeToString(SessionPayload.serializer(), payload),
            invalidateOn = LocalDate.parse(row.local_date),
        ) {
            sets.forEach { db.trainingQueries.deleteWorkSet(it.id) }
            db.trainingQueries.deleteSessionExercises(row.id)
            db.trainingQueries.deleteSession(row.id)
        }
    }

    fun deleteEntry(id: EntryId): TombstoneId? = entry(id.value)?.let { row ->
        record(
            entity = TrashEntity.ENTRY,
            entityId = row.id,
            payload = json.encodeToString(EntryPayload.serializer(), row.toPayload()),
            invalidateOn = LocalDate.parse(row.local_date),
        ) {
            db.nutritionQueries.deleteEntry(row.id)
        }
    }

    fun deleteMeasurement(id: MeasurementId): TombstoneId? = measurement(id.value)?.let { row ->
        record(
            entity = TrashEntity.MEASUREMENT,
            entityId = row.id,
            payload = json.encodeToString(MeasurementPayload.serializer(), row.toPayload()),
            invalidateOn = LocalDate.parse(row.local_date),
        ) {
            db.bodyQueries.deleteMeasurement(row.id)
        }
    }

    fun deleteVital(id: VitalId): TombstoneId? = vital(id.value)?.let { row ->
        record(
            entity = TrashEntity.VITAL,
            entityId = row.id,
            payload = json.encodeToString(VitalPayload.serializer(), row.toPayload()),
            invalidateOn = LocalDate.parse(row.local_date),
        ) {
            db.bodyQueries.deleteVital(row.id)
        }
    }

    fun deletePhoto(id: PhotoId): TombstoneId? = photo(id.value)?.let { row ->
        record(
            entity = TrashEntity.PHOTO,
            entityId = row.id,
            payload = json.encodeToString(PhotoPayload.serializer(), row.toPayload()),
            invalidateOn = LocalDate.parse(row.local_date),
        ) {
            db.bodyQueries.deletePhoto(row.id)
        }
    }

    fun deleteObservation(id: ObservationId): TombstoneId? = observation(id.value)?.let { row ->
        record(
            entity = TrashEntity.OBSERVATION,
            entityId = row.id,
            payload = json.encodeToString(ObservationPayload.serializer(), row.toPayload()),
            invalidateOn = LocalDate.parse(row.local_date),
        ) {
            db.observationQueries.deleteObservationById(row.id)
        }
    }

    fun deleteNote(id: NoteId): TombstoneId? = note(id.value)?.let { row ->
        record(
            entity = TrashEntity.NOTE,
            entityId = row.id,
            payload = json.encodeToString(NotePayload.serializer(), row.toPayload()),
        ) {
            db.noteQueries.deleteNote(row.id)
        }
    }

    /** Deletes an activity with its route; the tombstone carries both (`pr.md` §6.29). */
    fun deleteActivity(id: String): TombstoneId? = activity(id)?.let { row ->
        val points = db.activityQueries.pointsForActivity(row.id).executeAsList()
        val payload = row.toPayload(points)
        record(
            entity = TrashEntity.ACTIVITY,
            entityId = row.id,
            payload = json.encodeToString(ActivityPayload.serializer(), payload),
            invalidateOn = LocalDate.parse(row.local_date),
        ) {
            db.activityQueries.deletePointsForActivity(row.id)
            db.activityQueries.deleteActivity(row.id)
        }
    }

    /** Restores one tombstoned row. Returns false when it is gone or already restored. */
    @Suppress("CyclomaticComplexMethod")
    fun undo(id: TombstoneId): Boolean {
        val tombstone = tombstoneById(id.value)
        val payload = tombstone?.payload
        val entity = TrashEntity.entries.firstOrNull { it.table == tombstone?.entity_table }
        val missing = tombstone == null || payload == null || entity == null
        if (missing || tombstone.restored_at != null) {
            return false
        }
        var restored = false
        db.transaction {
            when (entity) {
                TrashEntity.SESSION -> restoreSession(payload)
                TrashEntity.ROUTINE_EXERCISE -> restoreRoutineExercise(payload)
                TrashEntity.WORK_SET -> restoreWorkSet(payload)
                TrashEntity.ENTRY -> restoreEntry(payload)
                TrashEntity.MEASUREMENT -> restoreMeasurement(payload)
                TrashEntity.VITAL -> restoreVital(payload)
                TrashEntity.PHOTO -> restorePhoto(payload)
                TrashEntity.NOTE -> restoreNote(payload)
                TrashEntity.OBSERVATION -> restoreObservation(payload)
                TrashEntity.ACTIVITY -> restoreActivity(payload)
            }
            // Append-only inserts ignore an id that a re-created row owns. The undo is
            // only complete when the row is really there afterwards.
            if (rowExists(entity, tombstone.entity_id)) {
                db.tombstoneQueries.markRestored(clock.now().toEpochMilliseconds(), tombstone.id)
                invalidateRestoredDay(entity, payload)
                restored = true
            }
        }
        return restored
    }

    /** Explicit permanent delete of one tombstone: clears the payload, keeps the marker. */
    fun purge(id: TombstoneId): Boolean {
        val tombstone = db.tombstoneQueries.tombstoneById(id.value).executeAsOneOrNull()
        if (tombstone == null || tombstone.restored_at != null) return false
        db.tombstoneQueries.clearTombstonePayload(id.value)
        return true
    }

    fun purgeExpired(now: Instant = clock.now()): Int {
        val due = db.tombstoneQueries.tombstonesDueForPurge(
            now.toEpochMilliseconds(),
        ).executeAsList()
        db.transaction {
            due.forEach { db.tombstoneQueries.clearTombstonePayload(it.id) }
        }
        return due.size
    }

    /** True when the tombstoned row carries weight, body fat or calorie values. */
    fun isSensitive(row: app.openhealth.data.db.ActiveTombstones): Boolean = runCatching {
        val payload = row.payload ?: return false
        when (row.entity_table) {
            TrashEntity.OBSERVATION.table -> json.decodeFromString(
                ObservationPayload.serializer(),
                payload,
            ).type in SENSITIVE_TYPES
            TrashEntity.MEASUREMENT.table -> json.decodeFromString(
                MeasurementPayload.serializer(),
                payload,
            ).type in SENSITIVE_TYPES
            TrashEntity.ENTRY.table -> true
            TrashEntity.PHOTO.table -> true
            else -> false
        }
    }.getOrDefault(false)

    /** A short, human summary of a tombstoned row for the Trash list. */
    @Suppress("CyclomaticComplexMethod")
    fun preview(row: app.openhealth.data.db.ActiveTombstones): String = runCatching {
        val payload = row.payload ?: return ""
        when (row.entity_table) {
            TrashEntity.NOTE.table -> json.decodeFromString(
                NotePayload.serializer(),
                payload,
            ).body.take(TRASH_PREVIEW_CHARS)
            TrashEntity.OBSERVATION.table -> {
                val o = json.decodeFromString(ObservationPayload.serializer(), payload)
                "${o.type.lowercase()} ${o.value} ${unitLabel(o.unit)}"
            }
            TrashEntity.MEASUREMENT.table -> {
                val m = json.decodeFromString(MeasurementPayload.serializer(), payload)
                "${m.type.lowercase()} ${m.value} ${unitLabel(m.unit)}"
            }
            TrashEntity.VITAL.table -> {
                val v = json.decodeFromString(VitalPayload.serializer(), payload)
                "${v.type.lowercase()} ${v.value} ${unitLabel(v.unit)}"
            }
            TrashEntity.PHOTO.table -> {
                val p = json.decodeFromString(PhotoPayload.serializer(), payload)
                p.caption ?: "Progress photo · ${p.localDate}"
            }
            TrashEntity.ENTRY.table -> {
                val e = json.decodeFromString(EntryPayload.serializer(), payload)
                e.foodId ?: "diary entry"
            }
            TrashEntity.WORK_SET.table -> {
                val w = json.decodeFromString(WorkSetPayload.serializer(), payload)
                "${w.weightKg} kg × ${w.reps}"
            }
            TrashEntity.SESSION.table -> {
                val s = json.decodeFromString(SessionPayload.serializer(), payload)
                s.session.title ?: "Workout"
            }
            TrashEntity.ROUTINE_EXERCISE.table -> {
                val e = json.decodeFromString(RoutineExercisePayload.serializer(), payload)
                "routine exercise ${e.position + 1}"
            }
            TrashEntity.ACTIVITY.table -> {
                val a = json.decodeFromString(ActivityPayload.serializer(), payload)
                a.name ?: "${a.type.lowercase()} · ${a.localDate}"
            }
            else -> row.entity_id
        }
    }.getOrElse { row.entity_id.take(TRASH_PREVIEW_FALLBACK) }

    fun byId(id: TombstoneId): Tombstone? =
        db.tombstoneQueries.tombstoneById(id.value).executeAsOneOrNull()

    fun active(): List<app.openhealth.data.db.ActiveTombstones> =
        db.tombstoneQueries.activeTombstones().executeAsList()

    private fun record(
        entity: TrashEntity,
        entityId: String,
        payload: String,
        invalidateOn: LocalDate? = null,
        remove: () -> Unit,
    ): TombstoneId {
        val now = clock.now()
        val id = TombstoneId("tombstone-$entityId-${now.toEpochMilliseconds()}")
        db.transaction {
            db.tombstoneQueries.insertTombstone(
                id = id.value,
                entityTable = entity.table,
                entityId = entityId,
                deletedAt = now.toEpochMilliseconds(),
                purgeAfter = (now + retentionDays.days).toEpochMilliseconds(),
                payload = payload,
                reason = null,
            )
            remove()
            invalidateOn?.let { rollupInvalidator?.invoke(it) }
        }
        return id
    }

    private fun rowExists(entity: TrashEntity, id: String): Boolean = when (entity) {
        TrashEntity.SESSION -> db.trainingQueries.sessionById(id).executeAsOneOrNull() != null
        TrashEntity.ROUTINE_EXERCISE ->
            db.trainingQueries.routineEntryById(id).executeAsOneOrNull() != null
        TrashEntity.WORK_SET -> db.trainingQueries.workSetById(id).executeAsOneOrNull() != null
        TrashEntity.ENTRY -> db.nutritionQueries.entryById(id).executeAsOneOrNull() != null
        TrashEntity.MEASUREMENT -> db.bodyQueries.measurementById(id).executeAsOneOrNull() != null
        TrashEntity.VITAL -> db.bodyQueries.vitalById(id).executeAsOneOrNull() != null
        TrashEntity.PHOTO -> db.bodyQueries.photoById(id).executeAsOneOrNull() != null
        TrashEntity.NOTE -> db.noteQueries.noteById(id).executeAsOneOrNull() != null
        TrashEntity.OBSERVATION ->
            db.observationQueries.observationById(id).executeAsOneOrNull() != null
        TrashEntity.ACTIVITY -> db.activityQueries.activityById(id).executeAsOneOrNull() != null
    }

    private fun invalidateRestoredDay(entity: TrashEntity, payload: String) {
        val date = when (entity) {
            TrashEntity.ENTRY ->
                json.decodeFromString(EntryPayload.serializer(), payload).localDate
            TrashEntity.MEASUREMENT ->
                json.decodeFromString(MeasurementPayload.serializer(), payload).localDate
            TrashEntity.VITAL ->
                json.decodeFromString(VitalPayload.serializer(), payload).localDate
            TrashEntity.OBSERVATION ->
                json.decodeFromString(ObservationPayload.serializer(), payload).localDate
            TrashEntity.SESSION ->
                json.decodeFromString(SessionPayload.serializer(), payload).session.localDate
            TrashEntity.WORK_SET ->
                json.decodeFromString(WorkSetPayload.serializer(), payload).localDate
            TrashEntity.PHOTO ->
                json.decodeFromString(PhotoPayload.serializer(), payload).localDate
            TrashEntity.ACTIVITY ->
                json.decodeFromString(ActivityPayload.serializer(), payload).localDate
            TrashEntity.ROUTINE_EXERCISE, TrashEntity.NOTE -> null
        }
        date?.let { rollupInvalidator?.invoke(LocalDate.parse(it)) }
    }

    private fun tombstoneById(id: String): Tombstone? =
        db.tombstoneQueries.tombstoneById(id).executeAsOneOrNull()

    private fun workSet(id: String): Work_set? =
        db.trainingQueries.workSetById(id).executeAsOneOrNull()

    private fun session(id: String): Session? =
        db.trainingQueries.sessionById(id).executeAsOneOrNull()

    private fun sessionDate(sessionId: String): LocalDate? =
        session(sessionId)?.local_date?.let(LocalDate::parse)

    private fun entry(id: String): Entry? = db.nutritionQueries.entryById(id).executeAsOneOrNull()

    private fun measurement(id: String): Measurement? =
        db.bodyQueries.measurementById(id).executeAsOneOrNull()

    private fun vital(id: String): Vital? = db.bodyQueries.vitalById(id).executeAsOneOrNull()

    private fun photo(id: String): Body_photo? = db.bodyQueries.photoById(id).executeAsOneOrNull()

    private fun activity(id: String): Activity? =
        db.activityQueries.activityById(id).executeAsOneOrNull()

    private fun restoreActivity(payload: String) {
        val row = json.decodeFromString(ActivityPayload.serializer(), payload)
        db.activityQueries.insertActivity(
            id = row.id,
            type = row.type,
            source = row.source,
            name = row.name,
            startedAt = row.startedAt,
            endedAt = row.endedAt,
            durationS = row.durationS,
            localDate = row.localDate,
            zoneId = row.zoneId,
            happenedAt = row.happenedAt,
            recordedAt = row.recordedAt,
            distanceM = row.distanceM,
            elevationGainM = row.elevationGainM,
            avgHeartRate = row.avgHeartRate,
            maxHeartRate = row.maxHeartRate,
            avgCadence = row.avgCadence,
            avgPowerW = row.avgPowerW,
            energyKcal = row.energyKcal,
            notes = row.notes,
            flowId = row.flowId,
            muscleKeys = row.muscleKeys,
            device = row.device,
            appVersion = row.appVersion,
            sourceId = row.sourceId,
            rawRecordId = row.rawRecordId,
            idempotencyKey = row.idempotencyKey,
            fingerprint = row.fingerprint,
            revision = row.revision,
            createdAt = row.createdAt,
            updatedAt = row.updatedAt,
        )
        row.points.forEach { point ->
            db.activityQueries.insertPoint(
                activityId = row.id,
                position = point.position,
                latitude = point.latitude,
                longitude = point.longitude,
                elevationM = point.elevationM,
                elapsedS = point.elapsedS,
                heartRate = point.heartRate,
                cadence = point.cadence,
                powerW = point.powerW,
                speedMps = point.speedMps,
            )
        }
    }

    private fun restorePhoto(payload: String) {
        val row = json.decodeFromString(PhotoPayload.serializer(), payload)
        db.bodyQueries.insertPhoto(
            id = row.id,
            localDate = row.localDate,
            zoneId = row.zoneId,
            happenedAt = row.happenedAt,
            recordedAt = row.recordedAt,
            sourceId = row.sourceId,
            caption = row.caption,
            notes = row.notes,
            mime = row.mime,
            width = row.width,
            height = row.height,
            bytesB64 = row.bytesB64,
            thumbB64 = row.thumbB64,
            sha256 = row.sha256,
            idempotencyKey = row.idempotencyKey,
            device = row.device,
            appVersion = row.appVersion,
            revision = row.revision,
        )
    }

    private fun Body_photo.toPayload(): PhotoPayload = PhotoPayload(
        id = id,
        localDate = local_date,
        zoneId = zone_id,
        happenedAt = happened_at,
        recordedAt = recorded_at,
        sourceId = source_id,
        caption = caption,
        notes = notes,
        mime = mime,
        width = width,
        height = height,
        bytesB64 = bytes_b64,
        thumbB64 = thumb_b64,
        sha256 = sha256,
        idempotencyKey = idempotency_key,
        device = device,
        appVersion = app_version,
        revision = revision,
    )

    private fun Activity.toPayload(points: List<Activity_point>): ActivityPayload = ActivityPayload(
        id = id,
        type = type,
        source = source,
        name = name,
        startedAt = started_at,
        endedAt = ended_at,
        durationS = duration_s,
        localDate = local_date,
        zoneId = zone_id,
        happenedAt = happened_at,
        recordedAt = recorded_at,
        distanceM = distance_m,
        elevationGainM = elevation_gain_m,
        avgHeartRate = avg_heart_rate,
        maxHeartRate = max_heart_rate,
        avgCadence = avg_cadence,
        avgPowerW = avg_power_w,
        energyKcal = energy_kcal,
        notes = notes,
        flowId = flow_id,
        muscleKeys = muscle_keys,
        device = device,
        appVersion = app_version,
        sourceId = source_id,
        rawRecordId = raw_record_id,
        idempotencyKey = idempotency_key,
        fingerprint = fingerprint,
        revision = revision,
        createdAt = created_at,
        updatedAt = updated_at,
        points = points.map { point ->
            ActivityPointPayload(
                position = point.position,
                latitude = point.latitude,
                longitude = point.longitude,
                elevationM = point.elevation_m,
                elapsedS = point.elapsed_s,
                heartRate = point.heart_rate,
                cadence = point.cadence,
                powerW = point.power_w,
                speedMps = point.speed_mps,
            )
        },
    )

    private fun unitLabel(unit: String): String = when (unit) {
        "[lb_av]" -> "lb"
        "[in_i]" -> "in"
        "mm[Hg]" -> "mmHg"
        "{step}" -> "steps"
        "{score}" -> ""
        "/min" -> "bpm"
        else -> unit
    }

    private fun note(id: String): Note? = db.noteQueries.noteById(id).executeAsOneOrNull()

    private fun observation(id: String): Observation? =
        db.observationQueries.observationById(id).executeAsOneOrNull()

    private fun restoreWorkSet(payload: String) {
        val row = json.decodeFromString(WorkSetPayload.serializer(), payload)
        db.trainingQueries.insertWorkSet(
            id = row.id,
            sessionId = row.sessionId,
            sessionExerciseId = row.sessionExerciseId,
            exerciseId = row.exerciseId,
            position = row.position,
            weightKg = row.weightKg,
            reps = row.reps,
            rir = row.rir,
            rpe = row.rpe,
            isWarmup = row.isWarmup,
            isBackoff = row.isBackoff,
            isFailure = row.isFailure,
            isBodyweight = row.isBodyweight,
            tempo = row.tempo,
            restSeconds = row.restSeconds,
            completedAt = row.completedAt,
            happenedAt = row.happenedAt,
            recordedAt = row.recordedAt,
            sourceId = row.sourceId,
            idempotencyKey = row.idempotencyKey,
            observationId = row.observationId,
            rawRecordId = row.rawRecordId,
            device = row.device,
            appVersion = row.appVersion,
            revision = row.revision,
            isDrop = row.isDrop,
            isAssisted = row.isAssisted,
            assistanceKg = row.assistanceKg,
            bandKg = row.bandKg,
            notes = row.notes,
        )
    }

    private fun restoreSession(payload: String) {
        val row = json.decodeFromString(SessionPayload.serializer(), payload)
        val session = row.session
        db.trainingQueries.insertSession(
            id = session.id,
            startedAt = session.startedAt,
            endedAt = session.endedAt,
            localDate = session.localDate,
            zoneId = session.zoneId,
            happenedAt = session.happenedAt,
            recordedAt = session.recordedAt,
            routineId = session.routineId,
            programmeId = session.programmeId,
            notes = session.notes,
            sourceId = session.sourceId,
            idempotencyKey = session.idempotencyKey,
            observationId = session.observationId,
            rawRecordId = session.rawRecordId,
            device = session.device,
            appVersion = session.appVersion,
            revision = session.revision,
            updatedAt = session.updatedAt,
            title = session.title,
            feeling = session.feeling,
            perceivedEffort = session.perceivedEffort,
        )
        row.exercises.forEach { entry ->
            db.trainingQueries.insertSessionExercise(
                id = entry.id,
                sessionId = entry.sessionId,
                exerciseId = entry.exerciseId,
                position = entry.position,
                notes = entry.notes,
                groupIndex = entry.groupIndex,
                targetSets = entry.targetSets,
                targetReps = entry.targetReps,
                targetRepsHigh = entry.targetRepsHigh,
                targetWeightKg = entry.targetWeightKg,
                progression = entry.progression,
                restSeconds = entry.restSeconds,
            )
        }
        row.sets.forEach { set ->
            restoreWorkSet(json.encodeToString(WorkSetPayload.serializer(), set))
        }
    }

    private fun restoreRoutineExercise(payload: String) {
        val row = json.decodeFromString(RoutineExercisePayload.serializer(), payload)
        db.trainingQueries.insertRoutineExercise(
            id = row.id,
            routineId = row.routineId,
            exerciseId = row.exerciseId,
            position = row.position,
            targetSets = row.targetSets,
            targetReps = row.targetReps,
            targetRepsHigh = row.targetRepsHigh,
            targetWeightKg = row.targetWeightKg,
            notes = row.notes,
            targetRir = row.targetRir,
            restSeconds = row.restSeconds,
            progression = row.progression,
            groupIndex = row.groupIndex,
        )
    }

    private fun restoreEntry(payload: String) {
        val row = json.decodeFromString(EntryPayload.serializer(), payload)
        db.nutritionQueries.insertEntry(
            id = row.id,
            mealId = row.mealId,
            foodId = row.foodId,
            localDate = row.localDate,
            zoneId = row.zoneId,
            happenedAt = row.happenedAt,
            recordedAt = row.recordedAt,
            quantityG = row.quantityG,
            servingLabel = row.servingLabel,
            servingG = row.servingG,
            servings = row.servings,
            foodName = row.foodName,
            brand = row.brand,
            energyKcal = row.energyKcal,
            proteinG = row.proteinG,
            carbsG = row.carbsG,
            fatG = row.fatG,
            fibreG = row.fibreG,
            microsJson = row.microsJson,
            sourceKind = row.sourceKind,
            sourceId = row.sourceId,
            userVerified = row.userVerified,
            userEdited = row.userEdited,
            observationId = row.observationId,
            rawRecordId = row.rawRecordId,
            idempotencyKey = row.idempotencyKey,
            device = row.device,
            appVersion = row.appVersion,
            revision = row.revision,
        )
    }

    private fun restoreMeasurement(payload: String) {
        val row = json.decodeFromString(MeasurementPayload.serializer(), payload)
        db.bodyQueries.insertMeasurement(
            id = row.id,
            type = row.type,
            value = row.value,
            unit = row.unit,
            localDate = row.localDate,
            zoneId = row.zoneId,
            happenedAt = row.happenedAt,
            recordedAt = row.recordedAt,
            sourceId = row.sourceId,
            method = row.method,
            notes = row.notes,
            excludedFromTrends = row.excludedFromTrends,
            observationId = row.observationId,
            rawRecordId = row.rawRecordId,
            idempotencyKey = row.idempotencyKey,
            device = row.device,
            appVersion = row.appVersion,
            revision = row.revision,
        )
    }

    private fun restoreVital(payload: String) {
        val row = json.decodeFromString(VitalPayload.serializer(), payload)
        db.bodyQueries.insertVital(
            id = row.id,
            type = row.type,
            value = row.value,
            unit = row.unit,
            localDate = row.localDate,
            zoneId = row.zoneId,
            happenedAt = row.happenedAt,
            recordedAt = row.recordedAt,
            sourceId = row.sourceId,
            method = row.method,
            context = row.context,
            note = row.note,
            excludedFromTrends = row.excludedFromTrends,
            observationId = row.observationId,
            rawRecordId = row.rawRecordId,
            idempotencyKey = row.idempotencyKey,
            device = row.device,
            appVersion = row.appVersion,
            revision = row.revision,
        )
    }

    private fun restoreObservation(payload: String) {
        val row = json.decodeFromString(ObservationPayload.serializer(), payload)
        db.observationQueries.insertObservation(
            id = row.id,
            type = row.type,
            value = row.value,
            unit = row.unit,
            startAt = row.startAt,
            endAt = row.endAt,
            localDate = row.localDate,
            zoneId = row.zoneId,
            happenedAt = row.happenedAt,
            recordedAt = row.recordedAt,
            sourceId = row.sourceId,
            device = row.device,
            appVersion = row.appVersion,
            method = row.method,
            context = row.context,
            confidence = row.confidence,
            quality = row.quality,
            userVerified = row.userVerified,
            userEdited = row.userEdited,
            originalValue = row.originalValue,
            originalUnit = row.originalUnit,
            editReason = row.editReason,
            editedAt = row.editedAt,
            groupKey = row.groupKey,
            excludedFromTrends = row.excludedFromTrends,
            idempotencyKey = row.idempotencyKey,
            rawRecordId = row.rawRecordId,
        )
    }

    private fun restoreNote(payload: String) {
        val row = json.decodeFromString(NotePayload.serializer(), payload)
        db.noteQueries.insertNote(
            id = row.id,
            body = row.body,
            localDate = row.localDate,
            zoneId = row.zoneId,
            happenedAt = row.happenedAt,
            recordedAt = row.recordedAt,
            editedAt = row.editedAt,
            sourceId = row.sourceId,
            rawRecordId = row.rawRecordId,
            idempotencyKey = row.idempotencyKey,
            device = row.device,
            appVersion = row.appVersion,
            revision = row.revision,
        )
    }

    private fun Work_set.toPayload(localDate: LocalDate? = null): WorkSetPayload = WorkSetPayload(
        id, session_id, session_exercise_id, exercise_id, position, weight_kg, reps, rir,
        rpe, is_warmup, is_backoff, is_failure, is_bodyweight, tempo, rest_seconds,
        completed_at, happened_at, recorded_at, source_id, idempotency_key,
        observation_id, raw_record_id, device, app_version, revision, is_drop,
        is_assisted, assistance_kg, band_kg, notes, localDate?.toString(),
    )

    private fun Session.toRowPayload(): SessionRowPayload = SessionRowPayload(
        id, started_at, ended_at, local_date, zone_id, happened_at, recorded_at, routine_id,
        programme_id, notes, source_id, idempotency_key, observation_id, raw_record_id,
        device, app_version, revision, updated_at, title, feeling, perceived_effort,
    )

    private fun Session_exercise.toPayload(): SessionExercisePayload = SessionExercisePayload(
        id = id,
        sessionId = session_id,
        exerciseId = exercise_id,
        position = position,
        notes = notes,
        groupIndex = group_index,
        targetSets = target_sets,
        targetReps = target_reps,
        targetRepsHigh = target_reps_high,
        targetWeightKg = target_weight_kg,
        progression = progression,
        restSeconds = rest_seconds,
    )

    private fun Entry.toPayload(): EntryPayload = EntryPayload(
        id, meal_id, food_id, local_date, zone_id, happened_at, recorded_at, quantity_g,
        energy_kcal, protein_g, carbs_g, fat_g, fibre_g, source_kind, source_id,
        user_verified, user_edited, observation_id, raw_record_id, idempotency_key,
        device, app_version, revision, serving_label, serving_g, servings, food_name,
        brand, micros_json,
    )

    private fun Measurement.toPayload(): MeasurementPayload = MeasurementPayload(
        id, type, value_, unit, local_date, zone_id, happened_at, recorded_at, source_id,
        method, notes, excluded_from_trends, observation_id, raw_record_id, idempotency_key,
        device, app_version, revision,
    )

    private fun Vital.toPayload(): VitalPayload = VitalPayload(
        id, type, value_, unit, local_date, zone_id, happened_at, recorded_at, source_id,
        method, context, note, excluded_from_trends, observation_id, raw_record_id,
        idempotency_key, device, app_version, revision,
    )

    private fun Observation.toPayload(): ObservationPayload = ObservationPayload(
        id, type, value_, unit, start_at, end_at, local_date, zone_id, happened_at,
        recorded_at, source_id, device, app_version, method, context, confidence, quality,
        user_verified, user_edited, original_value, original_unit, edit_reason, edited_at,
        group_key, excluded_from_trends, idempotency_key, raw_record_id,
    )

    private fun Note.toPayload(): NotePayload = NotePayload(
        id, body, local_date, zone_id, happened_at, recorded_at, edited_at, source_id,
        raw_record_id, idempotency_key, device, app_version, revision,
    )

    companion object {
        const val DEFAULT_RETENTION_DAYS = 30L
        private val SENSITIVE_TYPES = setOf(
            "WEIGHT",
            "BODY_FAT_PERCENT",
            "LEAN_MASS",
            "NUTRITION_ENERGY",
            "NUTRITION_PROTEIN",
            "NUTRITION_CARBS",
            "NUTRITION_FAT",
            "NUTRITION_FIBRE",
        )
        private const val TRASH_PREVIEW_FALLBACK = 18
        private const val TRASH_PREVIEW_CHARS = 60
    }
}
