package app.openhealth.data.mapping

import app.openhealth.core.ids.ObservationId
import app.openhealth.core.ids.RawRecordId
import app.openhealth.core.ids.SourceId
import app.openhealth.core.ingestion.RawRecord
import app.openhealth.core.observation.EditTrail
import app.openhealth.core.observation.Observation
import app.openhealth.core.observation.ObservationContext
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.observation.Provenance
import app.openhealth.core.observation.Quality
import app.openhealth.core.observation.QualityLabel
import app.openhealth.core.observation.SourceKind
import app.openhealth.core.time.TimeProvenance
import app.openhealth.core.units.Measure
import app.openhealth.core.units.UcumUnit
import app.openhealth.data.db.Observation as ObservationRow
import app.openhealth.data.db.Raw_record as RawRecordRow
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** Rows in, domain out. Unknown enum or unit values are corruption, not a silent null. */
fun ObservationRow.toDomain(): Observation {
    val observationType = enumValueOrNull<ObservationType>(type)
        ?: error("Observation $id has unknown type $type")
    val measure = Measure(value_, UcumUnit.byUcum(unit))
    val zone = TimeZone.of(zone_id)
    val qualityLabel = enumValueOrNull<QualityLabel>(quality ?: "")
    return Observation(
        id = ObservationId(id),
        type = observationType,
        value = measure,
        start = Instant.fromEpochMilliseconds(start_at),
        end = end_at?.let(Instant::fromEpochMilliseconds),
        provenance = Provenance(
            sourceId = SourceId(source_id),
            time = TimeProvenance(
                happenedAt = Instant.fromEpochMilliseconds(happened_at),
                recordedAt = Instant.fromEpochMilliseconds(recorded_at),
                localDate = LocalDate.parse(local_date),
                zone = zone,
            ),
            idempotencyKey = idempotency_key,
            device = device,
            appVersion = app_version,
            method = method?.let { enumValueOrNull(it) },
            rawRecordId = raw_record_id?.let(::RawRecordId),
        ),
        context = context?.let { enumValueOrNull(it) },
        quality = Quality(qualityLabel ?: QualityLabel.UNKNOWN, confidence),
        userVerified = user_verified,
        userEdited = user_edited,
        editTrail = EditTrail(original_value, original_unit, edit_reason, null),
        groupKey = group_key,
        excludedFromTrends = excluded_from_trends,
    )
}

fun RawRecordRow.toDomain(): RawRecord = RawRecord(
    id = RawRecordId(id),
    sourceId = SourceId(source_id),
    sourceKind = enumValueOrNull<SourceKind>(source_kind) ?: SourceKind.API,
    receivedAt = Instant.fromEpochMilliseconds(received_at),
    recordedAt = Instant.fromEpochMilliseconds(recorded_at),
    happenedAt = happened_at?.let(Instant::fromEpochMilliseconds),
    payloadHash = payload_hash,
    idempotencyKey = idempotency_key,
    schemaVersion = schema_version.toInt(),
    device = device,
    appVersion = app_version,
    payloadRef = payload_ref,
    payload = payload,
)

/** An unknown value from an older schema is not allowed to crash a read. */
inline fun <reified T : Enum<T>> enumValueOrNull(name: String): T? =
    enumValues<T>().firstOrNull { it.name == name }

fun ObservationContext?.nameOrNull(): String? = this?.name
