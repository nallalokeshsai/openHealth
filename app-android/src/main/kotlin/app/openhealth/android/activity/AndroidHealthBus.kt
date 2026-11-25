package app.openhealth.android.activity

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import app.openhealth.core.activity.HealthBusTypes
import app.openhealth.core.seam.HealthBus
import app.openhealth.core.seam.HealthReading
import kotlin.reflect.KClass

/**
 * The Android side of the health-bus seam (`Tech-Stack.md` §8). It maps Health Connect's
 * typed records to the app's canonical keys and units and hands the domain plain data, so
 * `:core` never sees `androidx.health` and an import can be tested without the platform.
 *
 * Health Connect is read-only here: the app writes nothing back, which keeps a second
 * copy of a value from appearing in the platform store and being read twice.
 *
 * Verified on 2026-11-25 against the current documentation: `connect-client` 1.1.0 is the
 * stable channel, reads reach back 30 days before the grant unless
 * `READ_HEALTH_DATA_HISTORY` is granted, and the permission screen is the platform's.
 * Garmin Connect writes to Health Connect one way on Android 14 and higher; the types it
 * publishes are steps, distance, floors, heart rate, sleep stages, weight, body fat,
 * active and total calories and exercise sessions, but not Body Battery, HRV status,
 * VO2 max, intensity minutes or training status (Garmin's "Sharing your Garmin Connect
 * data with Health Connect" support page, read 2026-11-25).
 */
class AndroidHealthBus(private val context: Context) : HealthBus {
    private val client: HealthConnectClient?
        get() = runCatching {
            if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
                HealthConnectClient.getOrCreate(context)
            } else {
                null
            }
        }.getOrNull()

    override fun isAvailable(): Boolean = client != null

    override suspend fun grantedPermissions(): Set<String> =
        client?.permissionController?.getGrantedPermissions().orEmpty()

    @Suppress("LongMethod")
    override suspend fun read(
        type: String,
        fromEpochMillis: Long,
        toEpochMillis: Long,
    ): List<HealthReading> {
        val health = client ?: return emptyList()
        val from = java.time.Instant.ofEpochMilli(fromEpochMillis)
        val to = java.time.Instant.ofEpochMilli(toEpochMillis)
        val filter = TimeRangeFilter.between(from, to)
        return when (type) {
            HealthBusTypes.STEPS -> readAll(health, StepsRecord::class, filter) { record ->
                listOf(
                    reading(
                        HealthBusTypes.STEPS,
                        record.count.toDouble(),
                        "{steps}",
                        record,
                        record.startTime.toEpochMilli(),
                        record.endTime.toEpochMilli(),
                    ),
                )
            }

            HealthBusTypes.DISTANCE -> readAll(health, DistanceRecord::class, filter) { record ->
                listOf(
                    reading(
                        HealthBusTypes.DISTANCE,
                        record.distance.inMeters,
                        "m",
                        record,
                        record.startTime.toEpochMilli(),
                        record.endTime.toEpochMilli(),
                    ),
                )
            }

            HealthBusTypes.ACTIVE_CALORIES -> readAll(
                health,
                ActiveCaloriesBurnedRecord::class,
                filter,
            ) { record ->
                listOf(
                    reading(
                        HealthBusTypes.ACTIVE_CALORIES,
                        record.energy.inKilocalories,
                        "kcal",
                        record,
                        record.startTime.toEpochMilli(),
                        record.endTime.toEpochMilli(),
                    ),
                )
            }

            HealthBusTypes.FLOORS -> readAll(health, FloorsClimbedRecord::class, filter) { record ->
                listOf(
                    reading(
                        HealthBusTypes.FLOORS,
                        record.floors,
                        "{count}",
                        record,
                        record.startTime.toEpochMilli(),
                        record.endTime.toEpochMilli(),
                    ),
                )
            }

            HealthBusTypes.HEART_RATE -> readAll(health, HeartRateRecord::class, filter) { record ->
                record.samples.map { sample ->
                    HealthReading(
                        type = HealthBusTypes.HEART_RATE,
                        value = sample.beatsPerMinute.toDouble(),
                        unit = "/min",
                        startEpochMillis = sample.time.toEpochMilli(),
                        endEpochMillis = null,
                        metadata = metadata(record, group = record.metadata.id),
                    )
                }
            }

            HealthBusTypes.RESTING_HEART_RATE -> readAll(
                health,
                RestingHeartRateRecord::class,
                filter,
            ) { record ->
                listOf(
                    HealthReading(
                        type = HealthBusTypes.RESTING_HEART_RATE,
                        value = record.beatsPerMinute.toDouble(),
                        unit = "/min",
                        startEpochMillis = record.time.toEpochMilli(),
                        endEpochMillis = null,
                        metadata = metadata(record),
                    ),
                )
            }

            HealthBusTypes.SLEEP,
            HealthBusTypes.SLEEP_DEEP,
            HealthBusTypes.SLEEP_LIGHT,
            HealthBusTypes.SLEEP_REM,
            HealthBusTypes.SLEEP_AWAKE,
            HealthBusTypes.SLEEP_IN_BED,
            -> readAll(health, SleepSessionRecord::class, filter) { record ->
                sleepReadings(type, record)
            }

            HealthBusTypes.EXERCISE -> readAll(
                health,
                ExerciseSessionRecord::class,
                filter,
            ) { record ->
                val minutes = (record.endTime.toEpochMilli() - record.startTime.toEpochMilli()) /
                    MILLIS_PER_MINUTE.toDouble()
                listOf(
                    HealthReading(
                        type = HealthBusTypes.EXERCISE,
                        value = minutes,
                        unit = "min",
                        startEpochMillis = record.startTime.toEpochMilli(),
                        endEpochMillis = record.endTime.toEpochMilli(),
                        metadata = metadata(record, group = record.metadata.id) +
                            mapOf("title" to record.title.orEmpty()),
                    ),
                )
            }

            HealthBusTypes.WEIGHT -> readAll(health, WeightRecord::class, filter) { record ->
                listOf(
                    HealthReading(
                        type = HealthBusTypes.WEIGHT,
                        value = record.weight.inKilograms,
                        unit = "kg",
                        startEpochMillis = record.time.toEpochMilli(),
                        endEpochMillis = null,
                        metadata = metadata(record),
                    ),
                )
            }

            HealthBusTypes.BODY_FAT -> readAll(health, BodyFatRecord::class, filter) { record ->
                listOf(
                    HealthReading(
                        type = HealthBusTypes.BODY_FAT,
                        value = record.percentage.value,
                        unit = "%",
                        startEpochMillis = record.time.toEpochMilli(),
                        endEpochMillis = null,
                        metadata = metadata(record),
                    ),
                )
            }

            else -> emptyList()
        }
    }

    override suspend fun write(readings: List<HealthReading>) {
        // Read-only by design: writing back would create a second copy of a value in the
        // platform store, which the next sync would read again (pr.md §7.6).
    }

    private suspend fun <T : Record> readAll(
        health: HealthConnectClient,
        type: KClass<T>,
        filter: TimeRangeFilter,
        map: (T) -> List<HealthReading>,
    ): List<HealthReading> = runCatching {
        health.readRecords(
            ReadRecordsRequest(
                recordType = type,
                timeRangeFilter = filter,
                ascendingOrder = true,
            ),
        ).records.flatMap(map)
    }.getOrDefault(emptyList())

    private fun sleepReadings(requested: String, record: SleepSessionRecord): List<HealthReading> {
        val stages = record.stages.mapNotNull { stage ->
            val key = when (stage.stage) {
                SleepSessionRecord.STAGE_TYPE_DEEP -> HealthBusTypes.SLEEP_DEEP
                SleepSessionRecord.STAGE_TYPE_LIGHT -> HealthBusTypes.SLEEP_LIGHT
                SleepSessionRecord.STAGE_TYPE_REM -> HealthBusTypes.SLEEP_REM
                SleepSessionRecord.STAGE_TYPE_AWAKE -> HealthBusTypes.SLEEP_AWAKE
                else -> null
            } ?: return@mapNotNull null
            HealthReading(
                type = key,
                value = (stage.endTime.toEpochMilli() - stage.startTime.toEpochMilli()) /
                    MILLIS_PER_SECOND.toDouble(),
                unit = "s",
                startEpochMillis = stage.startTime.toEpochMilli(),
                endEpochMillis = stage.endTime.toEpochMilli(),
                metadata = metadata(record, group = record.metadata.id) +
                    mapOf("stage" to key),
            )
        }
        // A staged night is reported as its stages; a summary-only night as one in-bed
        // reading. Emitting both would sum a total and its parts (pr.md §7.6).
        val inBed = HealthReading(
            type = HealthBusTypes.SLEEP_IN_BED,
            value = (record.endTime.toEpochMilli() - record.startTime.toEpochMilli()) /
                MILLIS_PER_SECOND.toDouble(),
            unit = "s",
            startEpochMillis = record.startTime.toEpochMilli(),
            endEpochMillis = record.endTime.toEpochMilli(),
            metadata = metadata(record, group = record.metadata.id) + mapOf("stage" to "in_bed"),
        )
        return when (requested) {
            HealthBusTypes.SLEEP -> if (stages.isEmpty()) listOf(inBed) else stages
            HealthBusTypes.SLEEP_IN_BED -> listOf(inBed)
            else -> stages.filter { it.type == requested }
        }
    }

    @Suppress("LongParameterList")
    private fun reading(
        type: String,
        value: Double,
        unit: String,
        record: Record,
        startEpochMillis: Long,
        endEpochMillis: Long?,
    ): HealthReading = HealthReading(
        type = type,
        value = value,
        unit = unit,
        startEpochMillis = startEpochMillis,
        endEpochMillis = endEpochMillis,
        metadata = metadata(record),
    )

    private fun metadata(record: Record, group: String? = null): Map<String, String> {
        val meta = record.metadata
        val device = meta.device?.model ?: meta.device?.manufacturer
        val values = LinkedHashMap<String, String>()
        values["uid"] = meta.id
        if (device != null) values["device"] = device
        if (group != null && group != meta.id) values["group"] = group
        return values
    }

    companion object {
        private const val MILLIS_PER_SECOND = 1000.0
        private const val MILLIS_PER_MINUTE = 60_000.0

        /** Every read permission the app requests, in the capability order. */
        fun readPermissions(): Set<String> = app.openhealth.core.activity.HealthBusCapabilities
            .readKeys
            .mapNotNull(::permissionFor)
            .toSet()

        /** The read permission for one bus key, for the rationale screen. */
        fun permissionFor(key: String): String? = when (key) {
            HealthBusTypes.STEPS -> HealthPermission.getReadPermission(StepsRecord::class)
            HealthBusTypes.DISTANCE -> HealthPermission.getReadPermission(DistanceRecord::class)
            HealthBusTypes.ACTIVE_CALORIES ->
                HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)

            HealthBusTypes.FLOORS -> HealthPermission.getReadPermission(FloorsClimbedRecord::class)
            HealthBusTypes.HEART_RATE -> HealthPermission.getReadPermission(HeartRateRecord::class)
            HealthBusTypes.RESTING_HEART_RATE ->
                HealthPermission.getReadPermission(RestingHeartRateRecord::class)

            HealthBusTypes.SLEEP -> HealthPermission.getReadPermission(SleepSessionRecord::class)
            HealthBusTypes.EXERCISE ->
                HealthPermission.getReadPermission(ExerciseSessionRecord::class)

            HealthBusTypes.WEIGHT -> HealthPermission.getReadPermission(WeightRecord::class)
            HealthBusTypes.BODY_FAT -> HealthPermission.getReadPermission(BodyFatRecord::class)
            else -> null
        }
    }
}
