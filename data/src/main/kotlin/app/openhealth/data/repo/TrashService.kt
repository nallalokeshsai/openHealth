package app.openhealth.data.repo

import app.openhealth.core.ids.EntryId
import app.openhealth.core.ids.MeasurementId
import app.openhealth.core.ids.SetId
import app.openhealth.core.ids.TombstoneId
import app.openhealth.core.ids.VitalId
import app.openhealth.core.seam.ClockSeam
import app.openhealth.core.seam.SystemClock
import app.openhealth.data.db.Entry
import app.openhealth.data.db.Measurement
import app.openhealth.data.db.OpenHealthDb
import app.openhealth.data.db.Tombstone
import app.openhealth.data.db.Vital
import app.openhealth.data.db.Work_set
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The tables Trash can restore today. A leaf entity is restorable on its own. */
enum class TrashEntity(val table: String) {
    WORK_SET("work_set"),
    ENTRY("entry"),
    MEASUREMENT("measurement"),
    VITAL("vital"),
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
    val revision: Long,
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
    val userVerified: Boolean,
    val userEdited: Boolean,
    val revision: Long,
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
    val revision: Long,
)

/**
 * Undo, Trash and recovery (`pr.md` §6.29). A delete writes a tombstone holding the row
 * it removes, then removes the row, in one transaction. Undo restores it for the rest of
 * the session; Trash keeps it for 30 days; purge is explicit and only touches rows past
 * their window. No code path here deletes anything without a tombstone.
 */
class TrashService(
    private val db: OpenHealthDb,
    private val clock: ClockSeam = SystemClock,
    private val retentionDays: Long = DEFAULT_RETENTION_DAYS,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun deleteWorkSet(id: SetId): TombstoneId? = workSet(id.value)?.let { row ->
        record(
            entity = TrashEntity.WORK_SET,
            entityId = row.id,
            payload = json.encodeToString(WorkSetPayload.serializer(), row.toPayload()),
        ) {
            db.trainingQueries.deleteWorkSet(row.id)
        }
    }

    fun deleteEntry(id: EntryId): TombstoneId? = entry(id.value)?.let { row ->
        record(
            entity = TrashEntity.ENTRY,
            entityId = row.id,
            payload = json.encodeToString(EntryPayload.serializer(), row.toPayload()),
        ) {
            db.nutritionQueries.deleteEntry(row.id)
        }
    }

    fun deleteMeasurement(id: MeasurementId): TombstoneId? = measurement(id.value)?.let { row ->
        record(
            entity = TrashEntity.MEASUREMENT,
            entityId = row.id,
            payload = json.encodeToString(MeasurementPayload.serializer(), row.toPayload()),
        ) {
            db.bodyQueries.deleteMeasurement(row.id)
        }
    }

    fun deleteVital(id: VitalId): TombstoneId? = vital(id.value)?.let { row ->
        record(
            entity = TrashEntity.VITAL,
            entityId = row.id,
            payload = json.encodeToString(VitalPayload.serializer(), row.toPayload()),
        ) {
            db.bodyQueries.deleteVital(row.id)
        }
    }

    /** Restores one tombstoned row. Returns false when it is gone or already restored. */
    fun undo(id: TombstoneId): Boolean {
        val tombstone = tombstoneById(id.value)
        val payload = tombstone?.payload
        val entity = TrashEntity.entries.firstOrNull { it.table == tombstone?.entity_table }
        val missing = tombstone == null || payload == null || entity == null
        if (missing || tombstone.restored_at != null) {
            return false
        }
        db.transaction {
            when (entity) {
                TrashEntity.WORK_SET -> restoreWorkSet(payload)
                TrashEntity.ENTRY -> restoreEntry(payload)
                TrashEntity.MEASUREMENT -> restoreMeasurement(payload)
                TrashEntity.VITAL -> restoreVital(payload)
            }
            db.tombstoneQueries.markRestored(clock.now().toEpochMilliseconds(), tombstone.id)
        }
        return true
    }

    fun purgeExpired(now: Instant = clock.now()): Int {
        val due = db.tombstoneQueries.tombstonesDueForPurge(
            now.toEpochMilliseconds(),
        ).executeAsList()
        db.transaction {
            due.forEach { db.tombstoneQueries.deleteTombstone(it.id) }
        }
        return due.size
    }

    fun active(): List<Tombstone> = db.tombstoneQueries.activeTombstones().executeAsList()

    fun activeFor(entity: TrashEntity, entityId: String): Tombstone? =
        db.tombstoneQueries.tombstoneForEntity(entity.table, entityId).executeAsOneOrNull()

    private fun record(
        entity: TrashEntity,
        entityId: String,
        payload: String,
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
        }
        return id
    }

    private fun tombstoneById(id: String): Tombstone? =
        db.tombstoneQueries.tombstoneById(id).executeAsOneOrNull()

    private fun workSet(id: String): Work_set? =
        db.trainingQueries.workSetById(id).executeAsOneOrNull()

    private fun entry(id: String): Entry? = db.nutritionQueries.entryById(id).executeAsOneOrNull()

    private fun measurement(id: String): Measurement? =
        db.bodyQueries.measurementById(id).executeAsOneOrNull()

    private fun vital(id: String): Vital? = db.bodyQueries.vitalById(id).executeAsOneOrNull()

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
            revision = row.revision,
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
            energyKcal = row.energyKcal,
            proteinG = row.proteinG,
            carbsG = row.carbsG,
            fatG = row.fatG,
            fibreG = row.fibreG,
            sourceKind = row.sourceKind,
            userVerified = row.userVerified,
            userEdited = row.userEdited,
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
            revision = row.revision,
        )
    }

    private fun Work_set.toPayload(): WorkSetPayload = WorkSetPayload(
        id, session_id, session_exercise_id, exercise_id, position, weight_kg, reps, rir, rpe,
        is_warmup, is_backoff, is_failure, is_bodyweight, tempo, rest_seconds, completed_at,
        happened_at, recorded_at, source_id, revision,
    )

    private fun Entry.toPayload(): EntryPayload = EntryPayload(
        id, meal_id, food_id, local_date, zone_id, happened_at, recorded_at, quantity_g,
        energy_kcal, protein_g, carbs_g, fat_g, fibre_g, source_kind, user_verified,
        user_edited, revision,
    )

    private fun Measurement.toPayload(): MeasurementPayload = MeasurementPayload(
        id, type, value_, unit, local_date, zone_id, happened_at, recorded_at, source_id,
        method, notes, revision,
    )

    private fun Vital.toPayload(): VitalPayload = VitalPayload(
        id, type, value_, unit, local_date, zone_id, happened_at, recorded_at, source_id,
        method, context, note, revision,
    )

    companion object {
        const val DEFAULT_RETENTION_DAYS = 30L
    }
}
