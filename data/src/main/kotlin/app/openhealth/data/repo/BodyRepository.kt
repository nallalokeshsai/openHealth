package app.openhealth.data.repo

import app.openhealth.data.db.Measurement
import app.openhealth.data.db.OpenHealthDb
import app.openhealth.data.db.Vital
import app.openhealth.data.mapping.requireConsistentDay
import kotlinx.datetime.LocalDate

/** Layer 4 body and vital rows (`pr.md` §6.8, §6.9). */
class BodyRepository(private val db: OpenHealthDb) {
    private var rollupInvalidator: ((LocalDate) -> Unit)? = null

    fun invalidateRollupsWith(invalidator: ((LocalDate) -> Unit)?) {
        rollupInvalidator = invalidator
    }

    fun upsertMeasurement(measurement: Measurement) {
        requireConsistentDay(measurement.local_date, measurement.zone_id, measurement.happened_at)
        db.transaction {
            db.bodyQueries.insertMeasurement(
                id = measurement.id,
                type = measurement.type,
                value = measurement.value_,
                unit = measurement.unit,
                localDate = measurement.local_date,
                zoneId = measurement.zone_id,
                happenedAt = measurement.happened_at,
                recordedAt = measurement.recorded_at,
                sourceId = measurement.source_id,
                method = measurement.method,
                notes = measurement.notes,
                excludedFromTrends = measurement.excluded_from_trends,
                observationId = measurement.observation_id,
                rawRecordId = measurement.raw_record_id,
                idempotencyKey = measurement.idempotency_key,
                device = measurement.device,
                appVersion = measurement.app_version,
                revision = measurement.revision,
            )
            rollupInvalidator?.invoke(LocalDate.parse(measurement.local_date))
        }
    }

    fun measurementById(id: String): Measurement? =
        db.bodyQueries.measurementById(id).executeAsOneOrNull()

    fun measurementsForDay(date: LocalDate): List<Measurement> =
        db.bodyQueries.measurementsForDay(date.toString()).executeAsList()

    fun measurementsForTypeBetween(type: String, from: Long, to: Long): List<Measurement> =
        db.bodyQueries.measurementsForTypeBetween(type, from, to).executeAsList()

    fun latestMeasurements(limit: Long): List<Measurement> =
        db.bodyQueries.latestMeasurements(limit).executeAsList()

    fun upsertVital(vital: Vital) {
        requireConsistentDay(vital.local_date, vital.zone_id, vital.happened_at)
        db.transaction {
            db.bodyQueries.insertVital(
                id = vital.id,
                type = vital.type,
                value = vital.value_,
                unit = vital.unit,
                localDate = vital.local_date,
                zoneId = vital.zone_id,
                happenedAt = vital.happened_at,
                recordedAt = vital.recorded_at,
                sourceId = vital.source_id,
                method = vital.method,
                context = vital.context,
                note = vital.note,
                excludedFromTrends = vital.excluded_from_trends,
                observationId = vital.observation_id,
                rawRecordId = vital.raw_record_id,
                idempotencyKey = vital.idempotency_key,
                device = vital.device,
                appVersion = vital.app_version,
                revision = vital.revision,
            )
            rollupInvalidator?.invoke(LocalDate.parse(vital.local_date))
        }
    }

    fun vitalById(id: String): Vital? = db.bodyQueries.vitalById(id).executeAsOneOrNull()

    fun vitalsForDay(date: LocalDate): List<Vital> =
        db.bodyQueries.vitalsForDay(date.toString()).executeAsList()

    fun vitalsForTypeBetween(type: String, from: Long, to: Long): List<Vital> =
        db.bodyQueries.vitalsForTypeBetween(type, from, to).executeAsList()

    fun latestVitals(limit: Long): List<Vital> = db.bodyQueries.latestVitals(limit).executeAsList()

    fun countMeasurements(): Long = db.bodyQueries.countMeasurements().executeAsOne()
}
