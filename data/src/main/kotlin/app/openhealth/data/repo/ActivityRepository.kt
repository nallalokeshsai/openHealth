@file:Suppress("LongParameterList")

package app.openhealth.data.repo

import app.openhealth.core.activity.ActivityPoint
import app.openhealth.core.activity.ActivityRecord
import app.openhealth.core.activity.ActivitySource
import app.openhealth.core.activity.ActivityType
import app.openhealth.core.body.Muscle
import app.openhealth.core.seam.ClockSeam
import app.openhealth.core.seam.SystemClock
import app.openhealth.data.db.Activity
import app.openhealth.data.db.Activity_point
import app.openhealth.data.db.OpenHealthDb
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * Layer-4 activities (`pr.md` §6.11, §7.4). A write is one transaction: the summary row,
 * every route sample and the rollup invalidation for its local day. A route is never
 * reconstructed from a summary, and a summary is never derived from a partial route.
 */
class ActivityRepository(
    private val db: OpenHealthDb,
    private val clock: ClockSeam = SystemClock,
) {
    private var rollupInvalidator: ((LocalDate) -> Unit)? = null

    fun invalidateRollupsWith(invalidator: ((LocalDate) -> Unit)?) {
        rollupInvalidator = invalidator
    }

    /**
     * Inserts an activity and its points. Returns the stored id, or null when the
     * idempotency key already exists (the same file imported twice is a no-op).
     */
    fun record(
        activity: ActivityRecord,
        sourceId: String,
        idempotencyKey: String,
        rawRecordId: String? = null,
        device: String? = null,
        appVersion: String? = null,
        now: Instant = clock.now(),
    ): String? {
        val existing = db.activityQueries.activityByIdempotencyKey(idempotencyKey)
            .executeAsOneOrNull()
        if (existing != null) return null
        val revision = 1L
        db.transaction {
            db.activityQueries.insertActivity(
                id = activity.id,
                type = activity.type.name,
                source = activity.source.name,
                name = activity.name,
                startedAt = activity.startedAt.toEpochMilliseconds(),
                endedAt = activity.endedAt.toEpochMilliseconds(),
                durationS = activity.durationSeconds,
                localDate = activity.localDate.toString(),
                zoneId = activity.zoneId,
                happenedAt = activity.startedAt.toEpochMilliseconds(),
                recordedAt = now.toEpochMilliseconds(),
                distanceM = activity.distanceM,
                elevationGainM = activity.elevationGainM,
                avgHeartRate = activity.averageHeartRate,
                maxHeartRate = activity.maxHeartRate,
                avgCadence = activity.averageCadence,
                avgPowerW = activity.averagePowerWatts,
                energyKcal = activity.energyKcal,
                notes = activity.notes,
                flowId = activity.flowId,
                muscleKeys = activity.muscles.joinToString(",") { it.key }
                    .takeIf { it.isNotBlank() },
                device = device,
                appVersion = appVersion,
                sourceId = sourceId,
                rawRecordId = rawRecordId,
                idempotencyKey = idempotencyKey,
                fingerprint = activity.fingerprint,
                revision = revision,
                createdAt = now.toEpochMilliseconds(),
                updatedAt = now.toEpochMilliseconds(),
            )
            activity.points.forEachIndexed { index, point ->
                db.activityQueries.insertPoint(
                    activityId = activity.id,
                    position = index.toLong(),
                    latitude = point.latitude,
                    longitude = point.longitude,
                    elevationM = point.elevationM,
                    elapsedS = point.elapsedSeconds,
                    heartRate = point.heartRate,
                    cadence = point.cadence,
                    powerW = point.powerWatts,
                    speedMps = point.speedMps,
                )
            }
        }
        rollupInvalidator?.invoke(activity.localDate)
        return activity.id
    }

    fun byId(id: String): ActivityRecord? = db.activityQueries.activityById(id)
        .executeAsOneOrNull()?.toDomain()

    fun byIdWithPoints(id: String): ActivityRecord? {
        val row = db.activityQueries.activityById(id).executeAsOneOrNull() ?: return null
        return row.toDomain(points = db.activityQueries.pointsForActivity(id).executeAsList())
    }

    fun byIdempotencyKey(key: String): ActivityRecord? =
        db.activityQueries.activityByIdempotencyKey(key).executeAsOneOrNull()
            ?.toDomain()

    fun between(from: LocalDate, to: LocalDate): List<ActivityRecord> =
        db.activityQueries.activitiesBetween(from.toString(), to.toString())
            .executeAsList().map { it.toDomain() }

    fun forDay(date: LocalDate): List<ActivityRecord> =
        db.activityQueries.activitiesForDay(date.toString())
            .executeAsList().map { it.toDomain() }

    fun recent(limit: Long = 50): List<ActivityRecord> = db.activityQueries.recentActivities(limit)
        .executeAsList().map { it.toDomain() }

    fun all(): List<ActivityRecord> = db.activityQueries.allActivities()
        .executeAsList().map { it.toDomain() }

    fun points(activityId: String): List<ActivityPoint> =
        db.activityQueries.pointsForActivity(activityId).executeAsList().map { it.toPoint() }

    fun count(): Long = db.activityQueries.countActivities().executeAsOne()

    fun pointCount(activityId: String): Long =
        db.activityQueries.countPointsForActivity(activityId).executeAsOne()

    fun updateNotes(id: String, notes: String?): Boolean {
        val row = db.activityQueries.activityById(id).executeAsOneOrNull() ?: return false
        db.activityQueries.updateActivityNotes(
            notes = notes,
            updatedAt = clock.now().toEpochMilliseconds(),
            id = id,
        )
        rollupInvalidator?.invoke(LocalDate.parse(row.local_date))
        return true
    }

    /** Hard delete, used only by [TrashService] after a tombstone carries the row. */
    fun delete(id: String): Boolean {
        val row = db.activityQueries.activityById(id).executeAsOneOrNull() ?: return false
        db.transaction {
            db.activityQueries.deletePointsForActivity(id)
            db.activityQueries.deleteActivity(id)
        }
        rollupInvalidator?.invoke(LocalDate.parse(row.local_date))
        return true
    }

    private fun Activity.toDomain(points: List<Activity_point> = emptyList()): ActivityRecord =
        ActivityRecord(
            id = id,
            type = ActivityType.fromKey(type) ?: ActivityType.OTHER,
            source = ActivitySource.fromKey(source),
            startedAt = Instant.fromEpochMilliseconds(started_at),
            endedAt = Instant.fromEpochMilliseconds(ended_at),
            localDate = LocalDate.parse(local_date),
            zoneId = zone_id,
            distanceM = distance_m,
            elevationGainM = elevation_gain_m,
            averageHeartRate = avg_heart_rate,
            maxHeartRate = max_heart_rate,
            averageCadence = avg_cadence,
            averagePowerWatts = avg_power_w,
            energyKcal = energy_kcal,
            name = name,
            notes = notes,
            flowId = flow_id,
            muscles = muscle_keys.orEmpty().split(',')
                .mapNotNull { key -> Muscle.entries.firstOrNull { it.key == key.trim() } },
            points = points.map { it.toPoint() },
        )

    private fun Activity_point.toPoint(): ActivityPoint = ActivityPoint(
        latitude = latitude,
        longitude = longitude,
        elapsedSeconds = elapsed_s,
        elevationM = elevation_m,
        heartRate = heart_rate,
        cadence = cadence,
        powerWatts = power_w,
        speedMps = speed_mps,
    )

    /** Used by the timeline and the tests without opening a second repository. */
    fun zoneOf(id: String): TimeZone? = db.activityQueries.activityById(id).executeAsOneOrNull()
        ?.zone_id?.let { TimeZone.of(it) }
}
