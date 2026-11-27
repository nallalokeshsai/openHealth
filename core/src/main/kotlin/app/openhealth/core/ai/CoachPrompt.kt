package app.openhealth.core.ai

import app.openhealth.core.observation.ObservationType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** The person's own answers the coach may see when PROFILE consent is on. */
data class CoachProfile(
    val sex: String?,
    val birthYear: Int?,
    val heightCm: Double?,
    val units: String?,
    val goalMode: String?,
    val trainingGoal: String?,
)

/** One finished session, reduced to what a plan review needs. */
data class CoachSessionSummary(
    val date: String,
    val routineName: String?,
    val sets: Int,
    val volumeKg: Double,
    val effort: Double?,
)

/** One daily number from an observation layer, with the provenance flag that gates it. */
data class CoachDaily(val date: String, val value: Double, val viaHealthConnect: Boolean)

data class CoachRecovery(val muscle: String, val state: String, val hoursSince: Double)

data class CoachVital(
    val type: ObservationType,
    val value: Double,
    val unit: String,
    val at: String,
    val viaHealthConnect: Boolean,
)

/**
 * Everything the coach could read. A builder fills it from the repositories; the
 * request builder applies consent and weight-neutral mode. Nothing is sent that is not
 * in this structure, and nothing in it is filtered twice.
 */
data class CoachDataset(
    val profile: CoachProfile? = null,
    val plan: CoachPlan = CoachPlan(emptyList()),
    val sessions: List<CoachSessionSummary> = emptyList(),
    val nutrition: List<CoachDaily> = emptyList(),
    val sleep: List<CoachDaily> = emptyList(),
    val recovery: List<CoachRecovery> = emptyList(),
    val vitals: List<CoachVital> = emptyList(),
)

data class CoachProviderRequest(val system: String, val user: String)

/**
 * Builds the provider request. The JSON is the only thing that travels; the system prompt
 * carries the rules and the exact action schema, and the user turn carries the consented
 * data plus the person's note.
 */
object CoachPrompt {
    const val MAX_SESSIONS = 20
    const val MAX_DAYS = 14
    const val MAX_VITALS = 20
    const val MAX_NOTE_LENGTH = 2_000

    private val json = Json { prettyPrint = false }

    fun build(
        dataset: CoachDataset,
        consent: CoachConsent,
        weightNeutral: Boolean,
        note: String,
    ): CoachProviderRequest {
        require(note.isNotBlank()) { "a coach request needs a note" }
        val data = buildJsonObject {
            put("schema", SCHEMA_VERSION)
            put("weight_neutral", weightNeutral)
            putJsonArray("categories") {
                CoachCategory.entries.filter { consent.allows(it) }.forEach { add(it.key) }
            }
            if (consent.allows(CoachCategory.PROFILE)) profile(dataset.profile)
            if (consent.allows(CoachCategory.TRAINING)) training(dataset)
            if (consent.allows(CoachCategory.RECOVERY)) recovery(dataset.recovery)
            if (consent.allows(CoachCategory.NUTRITION) && !weightNeutral) {
                nutrition(dataset.nutrition, consent)
            }
            if (consent.allows(CoachCategory.SLEEP)) sleep(dataset.sleep, consent)
            if (consent.allows(CoachCategory.VITALS)) {
                vitals(dataset.vitals, consent, weightNeutral)
            }
            put("note", note.trim().take(MAX_NOTE_LENGTH))
        }
        return CoachProviderRequest(
            system = SYSTEM_PROMPT,
            user = json.encodeToString(JsonObject.serializer(), data),
        )
    }

    private fun JsonObjectBuilder.profile(profile: CoachProfile?) {
        if (profile == null) return
        putJsonObject("profile") {
            put("sex", profile.sex)
            put("birth_year", profile.birthYear)
            put("height_cm", profile.heightCm)
            put("units", profile.units)
            put("goal_mode", profile.goalMode)
            put("training_goal", profile.trainingGoal)
        }
    }

    private fun JsonObjectBuilder.training(dataset: CoachDataset) {
        putJsonObject("training") {
            putJsonArray("routines") {
                dataset.plan.routines.forEach { routine ->
                    add(
                        buildJsonObject {
                            put("id", routine.id)
                            put("name", routine.name)
                            put("weekday", routine.weekday)
                            putJsonArray("entries") {
                                routine.entries.forEach { entry ->
                                    add(
                                        buildJsonObject {
                                            put("id", entry.id)
                                            put("exercise_id", entry.exerciseId)
                                            put("exercise_name", entry.exerciseName)
                                            put("sets", entry.sets)
                                            put("reps", entry.reps)
                                            put("reps_high", entry.repsHigh)
                                            put("weight_kg", entry.weightKg)
                                            put("rir", entry.rir)
                                        },
                                    )
                                }
                            }
                        },
                    )
                }
            }
            putJsonArray("recent_sessions") {
                dataset.sessions.take(MAX_SESSIONS).forEach { session ->
                    add(
                        buildJsonObject {
                            put("date", session.date)
                            put("routine", session.routineName)
                            put("sets", session.sets)
                            put("volume_kg", session.volumeKg)
                            put("effort", session.effort)
                        },
                    )
                }
            }
        }
    }

    private fun JsonObjectBuilder.recovery(recovery: List<CoachRecovery>) {
        putJsonArray("recovery") {
            recovery.forEach { state ->
                add(
                    buildJsonObject {
                        put("muscle", state.muscle)
                        put("state", state.state)
                        put("hours_since", state.hoursSince)
                    },
                )
            }
        }
    }

    private fun JsonObjectBuilder.nutrition(days: List<CoachDaily>, consent: CoachConsent) {
        putJsonArray("nutrition") {
            days.filter { consent.allows(CoachCategory.NUTRITION, it.viaHealthConnect) }
                .take(MAX_DAYS)
                .forEach { day ->
                    add(
                        buildJsonObject {
                            put("date", day.date)
                            put("energy_kcal", day.value)
                        },
                    )
                }
        }
    }

    private fun JsonObjectBuilder.sleep(days: List<CoachDaily>, consent: CoachConsent) {
        putJsonArray("sleep") {
            days.filter { consent.allows(CoachCategory.SLEEP, it.viaHealthConnect) }
                .take(MAX_DAYS)
                .forEach { day ->
                    add(
                        buildJsonObject {
                            put("date", day.date)
                            put("hours", day.value)
                        },
                    )
                }
        }
    }

    private fun JsonObjectBuilder.vitals(
        readings: List<CoachVital>,
        consent: CoachConsent,
        weightNeutral: Boolean,
    ) {
        putJsonArray("vitals") {
            readings.filter { consent.allows(CoachCategory.VITALS, it.viaHealthConnect) }
                .filterNot { weightNeutral && it.type in WEIGHT_NEUTRAL_HIDDEN }
                .take(MAX_VITALS)
                .forEach { vital ->
                    add(
                        buildJsonObject {
                            put("type", vital.type.name)
                            put("value", vital.value)
                            put("unit", vital.unit)
                            put("at", vital.at)
                        },
                    )
                }
        }
    }

    /** Weight-neutral mode hides these even when the person consents to VITALS. */
    private val WEIGHT_NEUTRAL_HIDDEN: Set<ObservationType> = setOf(
        ObservationType.WEIGHT,
        ObservationType.BODY_FAT_PERCENT,
        ObservationType.LEAN_MASS,
    )

    private const val SCHEMA_VERSION = 1

    val SYSTEM_PROMPT: String = """
        You are the openHealth coach. You review one person's own logged data and propose
        changes to their training plan. You never diagnose, prescribe, dose, treat an
        illness, or give emergency advice; if a note asks for any of that, say plainly that
        it is outside what the coach does and propose no plan change.

        Answer with one JSON object and nothing else. No markdown and no backticks.
        The object is:
        {"note":"one or two sentences to the person","proposals":[{"summary":"one line",
        "rationale":"why, in plain language","actions":[...]}]}

        Each action is exactly one of:
        {"kind":"add_exercise","routine_id":"...","exercise_id":"...","sets":3,"reps":8,"weight_kg":80,"rir":2}
        {"kind":"update_target","routine_id":"...","entry_id":"...","sets":4,"reps":10,"weight_kg":90,"rir":2}
        {"kind":"move_day","routine_id":"...","weekday":3}

        Rules:
        - Use only routine ids, entry ids and exercise ids present in the supplied data.
        - weekday is an ISO weekday, Monday 1 to Sunday 7.
        - sets 1-20, reps 1-100, weight_kg 0-500, rir 0-10. A weight may be omitted.
        - Only change what the data supports; otherwise return an empty proposals list.
        - At most 5 proposals and at most 10 actions in total.
        - Never mention a diagnosis, a medicine, a dose or a supplement protocol.
        - If weight_neutral is true, do not mention weight, body fat or calories.
    """.trimIndent()

    /** The repair turn: the same rules, the failed output and its typed findings. */
    fun repair(
        original: CoachProviderRequest,
        previous: String,
        findings: List<CoachFinding>,
    ): CoachProviderRequest = CoachProviderRequest(
        system = original.system,
        user = buildString {
            append(original.user)
            append("\n\nYour previous answer was rejected. It was:\n")
            append(previous.take(MAX_ECHO))
            append("\nFindings: ")
            append(findings.joinToString("; ") { it.repairText() })
            append("\nReturn the corrected JSON object only.")
        },
    )

    private const val MAX_ECHO = 6_000
}

/**
 * An HTTP POST-based provider. Both adapters live here so the request shape is one file;
 * the key is passed per call and never stored or logged by this code.
 */
/** A provider adapter. Implementations are in `CoachProvider.kt` and in tests. */
interface CoachProvider {
    /** Sends one request. Never throws: a transport failure becomes [CoachFailure]. */
    suspend fun complete(request: CoachProviderRequest): CoachProviderResult
}

sealed interface CoachProviderResult {
    data class Text(
        val text: String,
        val inputTokens: Int? = null,
        val outputTokens: Int? = null,
    ) : CoachProviderResult

    data class Failed(val failure: CoachFailure) : CoachProviderResult
}

enum class CoachFailureKind {
    OFFLINE,
    UNAUTHORIZED,
    RATE_LIMITED,
    SERVER,
    TIMEOUT,
    INVALID_RESPONSE,
    INVALID_OUTPUT,
    SAFETY,
    DISABLED,
}

/** A typed failure. [detail] is a status or a short reason, never a header or a key. */
data class CoachFailure(
    val kind: CoachFailureKind,
    val detail: String? = null,
    val findings: List<CoachFinding> = emptyList(),
)
