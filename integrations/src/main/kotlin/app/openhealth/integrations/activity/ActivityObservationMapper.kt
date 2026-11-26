package app.openhealth.integrations.activity

import app.openhealth.core.activity.ActivityCalculator
import app.openhealth.core.activity.ActivityRecord
import app.openhealth.core.ids.IdGenerator
import app.openhealth.core.ids.ObservationId
import app.openhealth.core.ids.RawRecordId
import app.openhealth.core.ids.SourceId
import app.openhealth.core.observation.MeasurementMethod
import app.openhealth.core.observation.Observation
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.observation.Provenance
import app.openhealth.core.time.TimeProvenance
import app.openhealth.core.units.Measure
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

/** Where an activity's derived observations came from. */
data class ActivityObservationContext(
    val sourceId: SourceId,
    val zone: TimeZone,
    val recordedAt: Instant,
    val method: MeasurementMethod,
    val device: String? = null,
    val appVersion: String? = null,
    val rawRecordId: RawRecordId? = null,
    val ids: IdGenerator,
)

/**
 * Turns an activity into the layer-2 observations the daily rollups use (`pr.md` §7.2,
 * §7.3): distance, active minutes and active calories, all over the session's own window
 * and under one group key. The summary row stays the place for the route and the detail.
 * Heart-rate samples are not re-emitted from the summary; a file's own heart-rate series
 * is imported by its reader.
 */
object ActivityObservationMapper {
    private const val SECONDS_PER_MINUTE = 60.0
    fun toObservations(
        activity: ActivityRecord,
        context: ActivityObservationContext,
    ): List<Observation> {
        val observations = mutableListOf<Observation>()
        val group = "activity:${activity.id}"
        val metrics = ActivityCalculator.metrics(activity)
        if (metrics.distanceM > 0.0) {
            observations += observation(
                context,
                ObservationType.DISTANCE,
                metrics.distanceM,
                activity,
                "distance",
                group,
            )
        }
        observations += observation(
            context,
            ObservationType.ACTIVE_MINUTES,
            activity.durationSeconds / SECONDS_PER_MINUTE,
            activity,
            "active",
            group,
        )
        val energy = activity.energyKcal
        if (energy != null && energy > 0.0) {
            observations += observation(
                context,
                ObservationType.ACTIVE_CALORIES,
                energy,
                activity,
                "calories",
                group,
            )
        }
        return observations
    }

    @Suppress("LongParameterList")
    private fun observation(
        context: ActivityObservationContext,
        type: ObservationType,
        value: Double,
        activity: ActivityRecord,
        key: String,
        group: String,
    ): Observation = Observation(
        id = ObservationId(context.ids.newId()),
        type = type,
        value = Measure(value, type.canonicalUnit).convertTo(type.canonicalUnit),
        start = activity.startedAt,
        end = activity.endedAt,
        provenance = Provenance(
            sourceId = context.sourceId,
            time = TimeProvenance.of(activity.startedAt, context.recordedAt, context.zone),
            idempotencyKey = "activity:${context.sourceId.value}:${activity.id}:$key",
            device = context.device,
            appVersion = context.appVersion,
            method = context.method,
            rawRecordId = context.rawRecordId,
        ),
        groupKey = group,
    )
}
