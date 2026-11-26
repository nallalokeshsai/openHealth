@file:Suppress(
    "MagicNumber",
    "ReturnCount",
    "LongMethod",
    "LongParameterList",
    "CyclomaticComplexMethod",
)

package app.openhealth.core.clinical

import app.openhealth.core.observation.Observation
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.units.UcumUnit
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Open mHealth export, where it fits (`pr.md` §6.17). The five schemas below cover the
 * data the app records in the shape Open mHealth defines: heart rate, blood pressure,
 * body weight, physical activity and sleep duration. A type without a schema is left out
 * rather than forced into a near match; the FHIR bundle and the PDF remain complete.
 *
 * Schema identifiers are the public Open mHealth names (`omh:heart-rate:2.0`); the
 * project does not reproduce the schemas, only emits data points that follow them.
 */
object OpenMhealth {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun export(
        observations: List<Observation>,
        generatedAt: Instant,
        includeWeight: Boolean = true,
    ): String {
        val points = mutableListOf<JsonObject>()
        val systolic = observations.filter { it.type == ObservationType.BLOOD_PRESSURE_SYSTOLIC }
        systolic.forEach { sys ->
            val dia = observations.firstOrNull { candidate ->
                candidate.type == ObservationType.BLOOD_PRESSURE_DIASTOLIC &&
                    sameReading(sys, candidate)
            }
            if (dia != null) points += bloodPressure(sys, dia, generatedAt)
        }
        observations.forEach { observation ->
            if (observation.type == ObservationType.BLOOD_PRESSURE_SYSTOLIC ||
                observation.type == ObservationType.BLOOD_PRESSURE_DIASTOLIC
            ) {
                return@forEach
            }
            point(observation, includeWeight)?.let(points::add)
        }
        val document = buildJsonObject {
            put("data_points", kotlinx.serialization.json.JsonArray(points))
            put("exported_at", generatedAt.toString())
            put("source", "openHealth")
        }
        return json.encodeToString(JsonObject.serializer(), document)
    }

    /** Two readings are one blood-pressure pair when they share a group or are minutes apart. */
    private fun sameReading(a: Observation, b: Observation): Boolean {
        if (a.groupKey != null && a.groupKey == b.groupKey) return true
        return a.localDate == b.localDate &&
            kotlin.math.abs((a.start - b.start).inWholeSeconds) <= 300
    }

    private fun point(observation: Observation, includeWeight: Boolean): JsonObject? {
        // A value is exported only in the unit the schema defines; never guessed.
        expectedUnit(observation.type)?.let { unit ->
            if (observation.value.unit != unit) return null
        }
        if (!includeWeight && observation.type == ObservationType.ACTIVE_CALORIES) return null
        val value = observation.value.value
        val body = when (observation.type) {
            ObservationType.HEART_RATE, ObservationType.RESTING_HEART_RATE ->
                heartRate(value, observation)
            ObservationType.BLOOD_PRESSURE_SYSTOLIC, ObservationType.BLOOD_PRESSURE_DIASTOLIC ->
                return null
            ObservationType.WEIGHT -> {
                if (!includeWeight) return null
                bodyWeight(value, observation)
            }
            ObservationType.STEPS, ObservationType.DISTANCE, ObservationType.ACTIVE_CALORIES ->
                physicalActivity(observation)
            ObservationType.SLEEP_DURATION -> sleepDuration(value, observation)
            else -> null
        } ?: return null
        val schema = schemaFor(observation.type) ?: return null
        return buildJsonObject {
            putJsonObject("header") {
                put("id", observation.id.value)
                put("creation_date_time", observation.provenance.recordedAt.toString())
                put("schema_id", schema)
            }
            put("body", body)
        }
    }

    /** Blood pressure is exported once, as a pair, by [bloodPressure]. */
    fun bloodPressure(
        systolic: Observation,
        diastolic: Observation,
        generatedAt: Instant,
    ): JsonObject = buildJsonObject {
        putJsonObject("header") {
            put("id", "bp-${systolic.id.value}")
            put("creation_date_time", generatedAt.toString())
            put("schema_id", "omh:blood-pressure:2.0")
        }
        putJsonObject("body") {
            putJsonObject("systolic_blood_pressure") {
                put("value", systolic.value.value)
                put("unit", "mmHg")
            }
            putJsonObject("diastolic_blood_pressure") {
                put("value", diastolic.value.value)
                put("unit", "mmHg")
            }
            putJsonObject("effective_time_frame") {
                put("date_time", systolic.start.toString())
            }
        }
    }

    private fun heartRate(value: Double, observation: Observation): JsonObject = buildJsonObject {
        putJsonObject("heart_rate") {
            put("value", value)
            put("unit", "beats/min")
        }
        putJsonObject("effective_time_frame") { put("date_time", observation.start.toString()) }
    }

    private fun bodyWeight(value: Double, observation: Observation): JsonObject = buildJsonObject {
        putJsonObject("body_weight") {
            put("value", value)
            put("unit", "kg")
        }
        putJsonObject("effective_time_frame") { put("date_time", observation.start.toString()) }
    }

    private fun physicalActivity(observation: Observation): JsonObject? {
        val end = observation.end ?: return null
        return buildJsonObject {
            when (observation.type) {
                ObservationType.STEPS -> {
                    put("activity_name", "walking")
                    putJsonObject("step_count") {
                        put("value", observation.value.value)
                        put("unit", "steps")
                    }
                }
                ObservationType.DISTANCE -> {
                    put("activity_name", "walking")
                    putJsonObject("distance") {
                        put("value", observation.value.value)
                        put("unit", "m")
                    }
                }
                ObservationType.ACTIVE_CALORIES -> {
                    put("activity_name", "physical activity")
                    putJsonObject("calories_burned") {
                        put("value", observation.value.value)
                        put("unit", "kcal")
                    }
                }
                else -> return null
            }
            putJsonObject("effective_time_frame") {
                putJsonObject("time_interval") {
                    put("start_date_time", observation.start.toString())
                    put("end_date_time", end.toString())
                }
            }
        }
    }

    private fun sleepDuration(value: Double, observation: Observation): JsonObject {
        val end = observation.end ?: observation.start
        return buildJsonObject {
            putJsonObject("sleep_duration") {
                put("value", value / 3600.0)
                put("unit", "h")
            }
            putJsonObject("effective_time_frame") {
                putJsonObject("time_interval") {
                    put("start_date_time", observation.start.toString())
                    put("end_date_time", end.toString())
                }
            }
        }
    }

    private fun schemaFor(type: ObservationType): String? = when (type) {
        ObservationType.HEART_RATE, ObservationType.RESTING_HEART_RATE -> "omh:heart-rate:2.0"
        ObservationType.WEIGHT -> "omh:body-weight:2.0"
        ObservationType.STEPS, ObservationType.DISTANCE, ObservationType.ACTIVE_CALORIES ->
            "omh:physical-activity:2.0"
        ObservationType.SLEEP_DURATION -> "omh:sleep-duration:1.0"
        else -> null
    }

    /** The unit a value must be in before it is written; exported values are never guessed. */
    fun expectedUnit(type: ObservationType): UcumUnit? = when (type) {
        ObservationType.HEART_RATE, ObservationType.RESTING_HEART_RATE -> UcumUnit.PER_MINUTE
        ObservationType.WEIGHT -> UcumUnit.KILOGRAM
        ObservationType.STEPS -> UcumUnit.STEP
        ObservationType.DISTANCE -> UcumUnit.METRE
        ObservationType.ACTIVE_CALORIES -> UcumUnit.KILOCALORIE
        ObservationType.SLEEP_DURATION -> UcumUnit.SECOND
        else -> null
    }
}
