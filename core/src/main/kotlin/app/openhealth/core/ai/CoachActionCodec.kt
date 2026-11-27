package app.openhealth.core.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * The stored form of one [CoachAction]. A proposal is created once and applied later, so
 * its actions round-trip through the database unchanged; the applier revalidates them
 * against the live plan before it writes anything.
 */
object CoachActionCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(action: CoachAction): String = buildJsonObject {
        when (action) {
            is CoachAction.AddExercise -> {
                put("kind", "add_exercise")
                put("routine_id", action.routineId)
                put("exercise_id", action.exerciseId)
                put("sets", action.sets)
                put("reps", action.reps)
                action.weightKg?.let { put("weight_kg", it) }
                action.rir?.let { put("rir", it) }
            }

            is CoachAction.UpdateTarget -> {
                put("kind", "update_target")
                put("routine_id", action.routineId)
                put("entry_id", action.entryId)
                action.sets?.let { put("sets", it) }
                action.reps?.let { put("reps", it) }
                action.weightKg?.let { put("weight_kg", it) }
                action.rir?.let { put("rir", it) }
            }

            is CoachAction.MoveDay -> {
                put("kind", "move_day")
                put("routine_id", action.routineId)
                put("weekday", action.weekday)
            }
        }
    }.toString()

    /** Returns null for a corrupt row rather than throwing; an unreadable proposal is
     * shown as invalid and can never be applied. */
    @Suppress("ReturnCount")
    fun decode(text: String): CoachAction? {
        val value = runCatching { json.parseToJsonElement(text) as? JsonObject }
            .getOrNull() ?: return null
        val routineId = value.string("routine_id") ?: return null
        return when (value.string("kind")) {
            "add_exercise" -> CoachAction.AddExercise(
                routineId = routineId,
                exerciseId = value.string("exercise_id") ?: return null,
                sets = value.int("sets") ?: return null,
                reps = value.int("reps") ?: return null,
                weightKg = value.double("weight_kg"),
                rir = value.double("rir"),
            )

            "update_target" -> CoachAction.UpdateTarget(
                routineId = routineId,
                entryId = value.string("entry_id") ?: return null,
                sets = value.int("sets"),
                reps = value.int("reps"),
                weightKg = value.double("weight_kg"),
                rir = value.double("rir"),
            )

            "move_day" -> CoachAction.MoveDay(
                routineId = routineId,
                weekday = value.int("weekday") ?: return null,
            )

            else -> null
        }
    }

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.int(name: String): Int? = (this[name] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.double(name: String): Double? =
        (this[name] as? JsonPrimitive)?.doubleOrNull
}
