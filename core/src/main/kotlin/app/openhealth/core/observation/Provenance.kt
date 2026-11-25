package app.openhealth.core.observation

import app.openhealth.core.ids.RawRecordId
import app.openhealth.core.ids.SourceId
import app.openhealth.core.time.TimeProvenance
import kotlin.time.Instant

/**
 * Where a value came from (`pr.md` §7.1). A vendor source keeps its own kind even when
 * it arrives inside Health Connect; [Source.family] then says the two records are the
 * same upstream device, so a day is never counted twice (`pr.md` §7.6, §6.18).
 */
enum class SourceKind(val defaultFamily: String) {
    MANUAL("manual"),

    /** A sensor on this phone, such as the hardware step counter (`pr.md` §6.6). */
    DEVICE("device"),
    HEALTH_CONNECT("health-connect"),
    HEALTHKIT("healthkit"),
    GARMIN("garmin"),
    FITBIT("fitbit"),
    STRAVA("strava"),
    CGM("cgm"),
    LAB("lab"),
    CSV("csv"),
    API("api"),
    MCP("mcp"),
}

/** How the value was obtained, where it is known (`pr.md` §7.2). */
enum class MeasurementMethod {
    MANUAL_ENTRY,
    CUFF,
    CGM_SENSOR,
    BIA,
    DEXA,
    LAB_ASSAY,
    WATCH_OPTICAL,
    SCALE,
    DEVICE_SENSOR,
    FILE_IMPORT,
    OTHER,
}

/** Context that changes how a value should be read (`pr.md` §2.2, §7.2). */
enum class ObservationContext {
    RESTING,
    POST_MEAL,
    PRE_MEAL,
    FASTING,
    PRE_WORKOUT,
    POST_WORKOUT,
    SLEEPING,
    AWAKE,
    LEFT_ARM,
    RIGHT_ARM,
    SITTING,
    STANDING,
    LYING,
    OTHER,
}

/** A quality hint, never a diagnosis. */
enum class QualityLabel { HIGH, MEDIUM, LOW, UNKNOWN }

data class Quality(val label: QualityLabel = QualityLabel.UNKNOWN, val confidence: Double? = null) {
    init {
        require(confidence == null || confidence in 0.0..1.0) {
            "confidence must be within 0.0..1.0, was $confidence"
        }
    }
}

/**
 * The provenance block of `pr.md` §2.2, §7.1 and §7.2. It is stored on the observation
 * itself, not inferred later: `happenedAt` says when the event occurred and `recordedAt`
 * when the app learned about it. Backfill never rewrites the second one (`pr.md` §6.27).
 */
data class Provenance(
    val sourceId: SourceId,
    val time: TimeProvenance,
    val idempotencyKey: String,
    val device: String? = null,
    val appVersion: String? = null,
    val method: MeasurementMethod? = null,
    val rawRecordId: RawRecordId? = null,
) {
    init {
        require(idempotencyKey.isNotBlank()) { "idempotencyKey must not be blank" }
    }

    val happenedAt: Instant get() = time.happenedAt
    val recordedAt: Instant get() = time.recordedAt
    val localDate get() = time.localDate
    val zone get() = time.zone

    /** True when the value was entered after the day it belongs to had passed. */
    val isBackfilled: Boolean get() = time.recordingLagHours() >= BACKFILL_HOURS

    companion object {
        const val BACKFILL_HOURS = 24.0
    }
}

/** A correction keeps the value it replaced (`pr.md` §7.2, §6.28). */
data class EditTrail(
    val originalValue: Double? = null,
    val originalUnit: String? = null,
    val reason: String? = null,
    val editedAt: Instant? = null,
) {
    val isEmpty: Boolean get() = originalValue == null && originalUnit == null
}
