package app.openhealth.core.watch

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The wire between a wrist and the phone (`pr.md` §6.5, §6.18; `docs/adr/0019`).
 *
 * One versioned envelope carries a command, a snapshot request or a snapshot. The same
 * bytes travel over the Wear OS Data Layer and over the Connect IQ BLE characteristics,
 * so the two watch platforms cannot drift into two protocols. Nothing here touches a
 * platform API and nothing writes a record: the phone applies a command through the same
 * training path as a thumb on its own screen.
 */
object WatchProtocol {
    /** Bumped only for an incompatible envelope; a receiver refuses a higher version. */
    const val VERSION: Int = 1

    const val COMMAND_PATH: String = "/openhealth/watch/command"
    const val SNAPSHOT_PATH: String = "/openhealth/watch/snapshot"
    const val REQUEST_PATH: String = "/openhealth/watch/request"

    const val MAX_APPLIED_COMMAND_IDS: Int = 200

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun encode(envelope: WatchEnvelope): String =
        json.encodeToString(WatchEnvelope.serializer(), envelope)

    fun decode(text: String): WatchEnvelope? = runCatching {
        json.decodeFromString(WatchEnvelope.serializer(), text)
    }.getOrNull()

    /** A malformed or incompatible envelope is ignored rather than crashing a watch. */
    fun decodeCommand(text: String): WatchCommand? = decode(text)?.let { envelope ->
        if (envelope.protocol != VERSION || envelope.type != WatchMessageType.COMMAND) {
            null
        } else {
            envelope.command?.takeIf { validate(it) == null }
        }
    }

    fun decodeSnapshot(text: String): WatchSnapshot? = decode(text)?.let { envelope ->
        if (envelope.protocol != VERSION || envelope.type != WatchMessageType.SNAPSHOT) {
            null
        } else {
            envelope.snapshot?.takeIf { validate(it) == null }
        }
    }

    fun command(command: WatchCommand): WatchEnvelope =
        WatchEnvelope(type = WatchMessageType.COMMAND, command = command)

    fun snapshot(snapshot: WatchSnapshot): WatchEnvelope =
        WatchEnvelope(type = WatchMessageType.SNAPSHOT, snapshot = snapshot)

    fun request(requestId: String): WatchEnvelope =
        WatchEnvelope(type = WatchMessageType.SNAPSHOT_REQUEST, requestId = requestId)

    /** Null when the command is safe to apply; otherwise the reason key shown in a log. */
    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    fun validate(command: WatchCommand): String? {
        if (command.id.isBlank()) return "command-id"
        if (command.happenedAtMillis <= 0L) return "happened-at"
        when (command.type) {
            WatchCommandType.START_SESSION -> {
                if (command.routineId != null && command.routineId.isBlank()) return "routine-id"
            }

            WatchCommandType.LOG_SET -> {
                if (command.sessionId.isNullOrBlank()) return "session-id"
                if (command.exerciseId.isNullOrBlank()) return "exercise-id"
                val weight = command.weightKg ?: return "weight"
                if (!weight.isFinite() || weight < 0.0 || weight > MAX_WEIGHT_KG) return "weight"
                val reps = command.reps ?: return "reps"
                if (reps < 0 || reps > MAX_REPS) return "reps"
                command.rir?.let { rir ->
                    if (!rir.isFinite() || rir < 0.0 || rir > MAX_RIR) return "rir"
                }
            }

            WatchCommandType.FINISH_SESSION -> {
                if (command.sessionId.isNullOrBlank()) return "session-id"
            }

            WatchCommandType.HEART_RATE -> {
                val bpm = command.heartRateBpm ?: return "heart-rate"
                if (bpm < MIN_HEART_RATE || bpm > MAX_HEART_RATE) return "heart-rate"
            }
        }
        return null
    }

    /** Null when the snapshot is safe to render; otherwise a stable reason key. */
    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    fun validate(snapshot: WatchSnapshot): String? {
        if (snapshot.generatedAtMillis <= 0L) return "generated-at"
        snapshot.session?.let { session ->
            if (session.id.isBlank()) return "session-id"
            if (session.currentExerciseIndex < 0) return "current-exercise"
            if (session.exercises.isNotEmpty() &&
                session.currentExerciseIndex > session.exercises.lastIndex
            ) {
                return "current-exercise"
            }
            session.exercises.forEach { exercise ->
                if (exercise.id.isBlank() || exercise.name.isBlank()) return "exercise"
                if (exercise.targetSets < 0 || exercise.targetReps < 0) return "exercise-target"
                if (exercise.sets.any {
                        !it.weightKg.isFinite() || it.weightKg < 0.0 || it.reps < 0
                    }
                ) {
                    return "set"
                }
            }
        }
        snapshot.session?.exercises?.forEach { exercise ->
            val target = exercise.targetWeightKg
            if (target != null && (!target.isFinite() || target < 0.0)) return "exercise-target"
        }
        if (snapshot.routines.any { it.id.isBlank() || it.name.isBlank() }) return "routine"
        snapshot.rest?.let { rest ->
            if (rest.totalSeconds < 0 || rest.remainingSeconds < 0) return "rest"
        }
        return null
    }

    const val MAX_WEIGHT_KG: Double = 1_000.0
    const val MAX_REPS: Int = 1_000
    const val MAX_RIR: Double = 10.0
    const val MIN_HEART_RATE: Int = 20
    const val MAX_HEART_RATE: Int = 260
}

@Serializable
enum class WatchMessageType {
    COMMAND,
    SNAPSHOT_REQUEST,
    SNAPSHOT,
}

@Serializable
data class WatchEnvelope(
    val protocol: Int = WatchProtocol.VERSION,
    val type: WatchMessageType,
    val requestId: String? = null,
    val command: WatchCommand? = null,
    val snapshot: WatchSnapshot? = null,
)

@Serializable
enum class WatchCommandType {
    START_SESSION,
    LOG_SET,
    FINISH_SESSION,
    HEART_RATE,
}

/**
 * One thing a wrist asks the phone to do. [id] is the idempotency key: the phone writes
 * a set once even when the queue is replayed after a dropped link. [happenedAtMillis] is
 * when it happened on the wrist; the phone stamps `recordedAt` when it applies it
 * (`pr.md` §7.3).
 */
@Serializable
data class WatchCommand(
    val id: String,
    val type: WatchCommandType,
    val happenedAtMillis: Long,
    val sessionId: String? = null,
    val routineId: String? = null,
    val exerciseId: String? = null,
    /** The session_exercise row, so two entries of one exercise stay distinct. */
    val sessionExerciseId: String? = null,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val rir: Double? = null,
    val isWarmup: Boolean = false,
    val heartRateBpm: Int? = null,
    val locale: String? = null,
)

/**
 * What the wrist renders. The phone owns the open session and the current exercise; the
 * watch draws the snapshot as it arrives and never invents a plan (`docs/adr/0019`).
 */
@Serializable
data class WatchSnapshot(
    val generatedAtMillis: Long,
    val session: WatchSessionSummary? = null,
    val routines: List<WatchRoutine> = emptyList(),
    val rest: WatchRest? = null,
    val appliedCommandIds: List<String> = emptyList(),
    /** The person's display unit; the command always carries kilograms. */
    val weightUnit: String = "kg",
    /** One tap on the weight stepper, in kilograms, so a lb profile steps 5 lb. */
    val weightIncrementKg: Double = 2.5,
)

@Serializable
data class WatchSessionSummary(
    val id: String,
    val title: String,
    val startedAtMillis: Long,
    val exercises: List<WatchExercise>,
    val currentExerciseIndex: Int,
    val completedSetCount: Int,
    val sourceId: String? = null,
)

@Serializable
data class WatchExercise(
    val id: String,
    /** The session_exercise row; a routine may prescribe the same exercise twice. */
    val entryId: String = "",
    val name: String,
    val targetSets: Int,
    val targetReps: Int,
    val targetRepsHigh: Int? = null,
    val targetWeightKg: Double? = null,
    val restSeconds: Int = 90,
    val sets: List<WatchSet> = emptyList(),
)

@Serializable
data class WatchSet(
    val weightKg: Double,
    val reps: Int,
    val completedAtMillis: Long,
    val isWarmup: Boolean = false,
)

@Serializable
data class WatchRest(val remainingSeconds: Int, val totalSeconds: Int, val paused: Boolean = false)

@Serializable
data class WatchRoutine(val id: String, val name: String, val exerciseCount: Int)
