package app.openhealth.data.repo

import app.openhealth.core.ids.ObservationId
import app.openhealth.core.ingestion.RawRecord
import app.openhealth.core.observation.Observation
import app.openhealth.core.observation.ObservationType
import app.openhealth.data.db.OpenHealthDb
import app.openhealth.data.mapping.toDomain
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

sealed interface RecordOutcome {
    data class Inserted(val id: ObservationId) : RecordOutcome

    data class Duplicate(val existingId: ObservationId) : RecordOutcome
}

/**
 * Writes observations with their raw ingestion record in one transaction. The
 * idempotency key is unique, so a retried import or a replayed sync batch inserts
 * nothing and is not an error (`pr.md` §7.1, §7.7).
 */
class ObservationRepository(
    private val db: OpenHealthDb,
    private val clock: app.openhealth.core.seam.ClockSeam = app.openhealth.core.seam.SystemClock,
) {
    private var rollupInvalidator: ((LocalDate) -> Unit)? = null
    private var sync: app.openhealth.data.sync.SyncRepository? = null

    /** Wired by the composition root once the sync engine exists (B13). */
    fun recordChangesWith(recorder: app.openhealth.data.sync.SyncRepository?) {
        sync = recorder
    }

    /**
     * Wired by the composition root once the rollup engine exists. Every write that
     * changes a day calls it, so a backfilled Tuesday rewrites Tuesday (`pr.md` §7.3).
     */
    fun invalidateRollupsWith(invalidator: ((LocalDate) -> Unit)?) {
        rollupInvalidator = invalidator
    }

    fun record(observation: Observation, raw: RawRecord? = null): RecordOutcome {
        val outcome = db.transactionWithResult {
            raw?.let { insertRaw(it) }
            val existing = db.observationQueries.observationByIdempotencyKey(
                observation.provenance.idempotencyKey,
            ).executeAsOneOrNull()
            if (existing != null) {
                RecordOutcome.Duplicate(ObservationId(existing.id))
            } else {
                insert(observation)
                rollupInvalidator?.invoke(observation.localDate)
                RecordOutcome.Inserted(observation.id)
            }
        }
        return outcome
    }

    /**
     * Bulk ingestion for imports and the fixture corpus: one transaction, one existence
     * check per observation, raw records first.
     */
    fun recordAll(observations: List<Observation>, rawRecords: List<RawRecord> = emptyList()): Int {
        val inserted = db.transactionWithResult {
            rawRecords.forEach(::insertRaw)
            var inserted = 0
            observations.forEach { observation ->
                val existing = db.observationQueries.observationByIdempotencyKey(
                    observation.provenance.idempotencyKey,
                ).executeAsOneOrNull()
                if (existing == null) {
                    insert(observation)
                    inserted += 1
                }
            }
            if (inserted > 0) {
                observations.map { it.localDate }.distinct().forEach {
                    rollupInvalidator?.invoke(it)
                }
            }
            inserted
        }
        return inserted
    }

    private fun insertRaw(raw: RawRecord) {
        db.rawRecordQueries.insertRawRecord(
            id = raw.id.value,
            sourceId = raw.sourceId.value,
            sourceKind = raw.sourceKind.name,
            device = raw.device,
            appVersion = raw.appVersion,
            receivedAt = raw.receivedAt.toEpochMilliseconds(),
            recordedAt = raw.recordedAt.toEpochMilliseconds(),
            happenedAt = raw.happenedAt?.toEpochMilliseconds(),
            payloadHash = raw.payloadHash,
            payloadRef = raw.payloadRef,
            payload = raw.payload,
            idempotencyKey = raw.idempotencyKey,
            schemaVersion = raw.schemaVersion.toLong(),
        )
    }

    private fun insert(observation: Observation) {
        val provenance = observation.provenance
        db.observationQueries.insertObservation(
            id = observation.id.value,
            type = observation.type.name,
            value = observation.value.value,
            unit = observation.value.unit.ucum,
            startAt = observation.start.toEpochMilliseconds(),
            endAt = observation.end?.toEpochMilliseconds(),
            localDate = observation.localDate.toString(),
            zoneId = provenance.zone.id,
            happenedAt = provenance.happenedAt.toEpochMilliseconds(),
            recordedAt = provenance.recordedAt.toEpochMilliseconds(),
            sourceId = provenance.sourceId.value,
            device = provenance.device,
            appVersion = provenance.appVersion,
            method = provenance.method?.name,
            context = observation.context?.name,
            confidence = observation.quality.confidence,
            quality = observation.quality.label.name,
            userVerified = observation.userVerified,
            userEdited = observation.userEdited,
            originalValue = observation.editTrail.originalValue,
            originalUnit = observation.editTrail.originalUnit,
            editReason = observation.editTrail.reason,
            editedAt = observation.editTrail.editedAt?.toEpochMilliseconds(),
            groupKey = observation.groupKey,
            excludedFromTrends = observation.excludedFromTrends,
            idempotencyKey = provenance.idempotencyKey,
            rawRecordId = provenance.rawRecordId?.value,
        )
        sync?.capture("observation", observation.id.value, observation.localDate.toString())
    }

    fun byId(id: ObservationId): Observation? =
        db.observationQueries.observationById(id.value).executeAsOneOrNull()?.toDomain()

    fun forDay(date: LocalDate): List<Observation> =
        db.observationQueries.observationsForDay(date.toString()).executeAsList().map {
            it.toDomain()
        }

    fun forDayAndType(date: LocalDate, type: ObservationType): List<Observation> =
        db.observationQueries.observationsForDayAndType(date.toString(), type.name)
            .executeAsList()
            .map { it.toDomain() }

    fun forTypeBetween(type: ObservationType, from: Instant, to: Instant): List<Observation> =
        db.observationQueries.observationsForTypeBetween(
            type = type.name,
            from = from.toEpochMilliseconds(),
            to = to.toEpochMilliseconds(),
        ).executeAsList().map { it.toDomain() }

    fun latest(type: ObservationType): Observation? =
        db.observationQueries.latestObservationOfType(type.name).executeAsOneOrNull()?.toDomain()

    fun forGroup(groupKey: String): List<Observation> =
        db.observationQueries.observationsForGroup(groupKey).executeAsList().map { it.toDomain() }

    fun daysWithObservations(): List<LocalDate> =
        db.observationQueries.daysWithObservations().executeAsList().map(LocalDate::parse)

    /**
     * A user correction keeps the value it replaced and the reason (`pr.md` §6.28). The
     * recording timestamp does not move; the next rollup rebuild sees the new value.
     */
    fun correct(id: ObservationId, correctedValue: Double, reason: String): Boolean {
        val existing = db.observationQueries.observationById(id.value).executeAsOneOrNull()
            ?: return false
        db.transaction {
            db.observationQueries.markUserEdited(
                value = correctedValue,
                reason = reason,
                editedAt = clock.now().toEpochMilliseconds(),
                previousValue = existing.value_,
                previousUnit = existing.unit,
                id = id.value,
            )
            rollupInvalidator?.invoke(LocalDate.parse(existing.local_date))
            sync?.capture("observation", id.value, existing.local_date)
        }
        return true
    }

    fun excludeFromTrends(id: ObservationId, excluded: Boolean) {
        val existing =
            db.observationQueries.observationById(id.value).executeAsOneOrNull() ?: return
        db.observationQueries.setExcludedFromTrends(excluded, id.value)
        sync?.capture("observation", id.value, existing.local_date)
        rollupInvalidator?.invoke(LocalDate.parse(existing.local_date))
    }

    fun count(): Long = db.observationQueries.countObservations().executeAsOne()
}
