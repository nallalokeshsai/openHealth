@file:Suppress("MagicNumber") // parser limits, named in the constants below.

package app.openhealth.core.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** The result of reading one provider answer. */
sealed interface CoachOutputResult {
    data class Valid(val answer: CoachAnswer) : CoachOutputResult

    data class Invalid(val findings: List<CoachFinding>, val raw: String) : CoachOutputResult
}

/**
 * Reads the coach's structured answer. Every field is checked here, not in the UI: the
 * same validator that guards manual entry guards a proposal (`Tech-Stack.md` §9).
 */
object CoachParser {
    private val json = Json { ignoreUnknownKeys = false }

    private val ROOT_FIELDS = setOf("note", "proposals")
    private val ADD_FIELDS = setOf(
        "kind",
        "routine_id",
        "exercise_id",
        "sets",
        "reps",
        "weight_kg",
        "rir",
    )
    private val UPDATE_FIELDS = setOf(
        "kind",
        "routine_id",
        "entry_id",
        "sets",
        "reps",
        "weight_kg",
        "rir",
    )
    private val MOVE_FIELDS = setOf("kind", "routine_id", "weekday")
    private val PROPOSAL_FIELDS = setOf("summary", "rationale", "actions")
    private const val MAX_NOTE = 600
    private const val MAX_SUMMARY = 200
    private const val MAX_RATIONALE = 800

    fun parse(text: String, plan: CoachPlan): CoachOutputResult {
        val body = stripFence(text.trim())
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject
            ?: return CoachOutputResult.Invalid(
                listOf(CoachFinding(CoachFindingCode.NOT_JSON)),
                text,
            )
        return runCatching { read(root, plan) }
            .getOrElse {
                CoachOutputResult.Invalid(listOf(CoachFinding(CoachFindingCode.NOT_JSON)), text)
            }
    }

    @Suppress("ReturnCount")
    private fun read(root: JsonObject, plan: CoachPlan): CoachOutputResult {
        val findings = mutableListOf<CoachFinding>()
        val unknown = root.keys - ROOT_FIELDS
        if (unknown.isNotEmpty()) findings += CoachFinding(CoachFindingCode.UNKNOWN_FIELD)
        val note = root["note"]?.jsonPrimitive?.contentOrNull
            ?: return invalid(findings + CoachFinding(CoachFindingCode.MISSING_FIELD))
        if (note.isBlank() || note.length > MAX_NOTE) {
            findings += CoachFinding(CoachFindingCode.EMPTY_TEXT)
        }
        val proposalsJson = root["proposals"] as? JsonArray
            ?: return invalid(findings + CoachFinding(CoachFindingCode.MISSING_FIELD))
        if (proposalsJson.size > CoachActionValidator.MAX_PROPOSALS) {
            findings += CoachFinding(CoachFindingCode.TOO_MANY_PROPOSALS)
        }
        val proposals = mutableListOf<CoachProposalDraft>()
        proposalsJson.forEach { element ->
            val objectValue = element as? JsonObject
                ?: return invalid(findings + CoachFinding(CoachFindingCode.WRONG_TYPE))
            val proposal = readProposal(objectValue, plan, findings)
            proposal?.let(proposals::add)
        }
        // Safety is checked after structure so a well-formed but clinical answer still
        // fails, and the one repair round gets a precise finding.
        if (proposals.sumOf { it.actions.size } > CoachActionValidator.MAX_ACTIONS) {
            findings += CoachFinding(CoachFindingCode.TOO_MANY_ACTIONS)
        }
        val texts = buildList {
            add(note)
            proposals.forEach {
                add(it.summary)
                add(it.rationale)
            }
        }
        if (texts.any(CoachSafety::isUnsafe)) {
            findings += CoachFinding(CoachFindingCode.UNSAFE_CLAIM)
        }
        if (findings.isNotEmpty()) {
            return CoachOutputResult.Invalid(
                findings.distinct(),
                root.toString(),
            )
        }
        return CoachOutputResult.Valid(CoachAnswer(note.trim(), proposals))
    }

    private fun readProposal(
        value: JsonObject,
        plan: CoachPlan,
        findings: MutableList<CoachFinding>,
    ): CoachProposalDraft? {
        if ((value.keys - PROPOSAL_FIELDS).isNotEmpty()) {
            findings += CoachFinding(CoachFindingCode.UNKNOWN_FIELD)
        }
        val summary = value["summary"]?.jsonPrimitive?.contentOrNull ?: ""
        val rationale = value["rationale"]?.jsonPrimitive?.contentOrNull ?: ""
        if (summary.isBlank() || summary.length > MAX_SUMMARY) {
            findings += CoachFinding(CoachFindingCode.EMPTY_TEXT)
        }
        if (rationale.isBlank() || rationale.length > MAX_RATIONALE) {
            findings += CoachFinding(CoachFindingCode.EMPTY_TEXT)
        }
        val actionsJson = value["actions"] as? JsonArray
            ?: run {
                findings += CoachFinding(CoachFindingCode.NO_ACTIONS)
                return null
            }
        val actions = mutableListOf<CoachAction>()
        actionsJson.forEachIndexed { index, element ->
            val actionObject = element as? JsonObject
                ?: run {
                    findings += CoachFinding(CoachFindingCode.WRONG_TYPE, index)
                    null
                }
            if (actionObject != null) {
                readAction(actionObject, index, findings)?.let(actions::add)
            }
        }
        findings += CoachActionValidator.validate(actions, plan)
        return CoachProposalDraft(summary.trim(), rationale.trim(), actions)
    }

    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    private fun readAction(
        value: JsonObject,
        index: Int,
        findings: MutableList<CoachFinding>,
    ): CoachAction? {
        val kind = value["kind"]?.jsonPrimitive?.contentOrNull
            ?: return invalidAction(findings, CoachFindingCode.MISSING_FIELD, index)
        val routineId = value.stringField("routine_id")
            ?: return invalidAction(findings, CoachFindingCode.MISSING_FIELD, index)
        val allowed = when (kind) {
            "add_exercise" -> ADD_FIELDS
            "update_target" -> UPDATE_FIELDS
            "move_day" -> MOVE_FIELDS
            else -> emptySet()
        }
        if ((value.keys - allowed).isNotEmpty()) {
            findings += CoachFinding(CoachFindingCode.UNKNOWN_FIELD, index)
        }
        return when (kind) {
            "add_exercise" -> {
                val exerciseId = value.stringField("exercise_id")
                    ?: return invalidAction(findings, CoachFindingCode.MISSING_FIELD, index)
                val sets = value.intField("sets")
                    ?: return invalidAction(findings, CoachFindingCode.MISSING_FIELD, index)
                val reps = value.intField("reps")
                    ?: return invalidAction(findings, CoachFindingCode.MISSING_FIELD, index)
                CoachAction.AddExercise(
                    routineId = routineId,
                    exerciseId = exerciseId,
                    sets = sets,
                    reps = reps,
                    weightKg = value.doubleField("weight_kg"),
                    rir = value.doubleField("rir"),
                )
            }

            "update_target" -> {
                val entryId = value.stringField("entry_id")
                    ?: return invalidAction(findings, CoachFindingCode.MISSING_FIELD, index)
                CoachAction.UpdateTarget(
                    routineId = routineId,
                    entryId = entryId,
                    sets = value.intField("sets"),
                    reps = value.intField("reps"),
                    weightKg = value.doubleField("weight_kg"),
                    rir = value.doubleField("rir"),
                )
            }

            "move_day" -> {
                val weekday = value.intField("weekday")
                    ?: return invalidAction(findings, CoachFindingCode.MISSING_FIELD, index)
                CoachAction.MoveDay(routineId = routineId, weekday = weekday)
            }

            else -> invalidAction(findings, CoachFindingCode.WRONG_TYPE, index)
        }
    }

    private fun invalidAction(
        findings: MutableList<CoachFinding>,
        code: CoachFindingCode,
        index: Int,
    ): CoachAction? {
        findings += CoachFinding(code, index)
        return null
    }

    private fun invalid(findings: List<CoachFinding>): CoachOutputResult =
        CoachOutputResult.Invalid(findings.distinct(), "")

    private fun JsonObject.stringField(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.intField(name: String): Int? {
        val primitive = this[name] as? JsonPrimitive ?: return null
        return primitive.intOrNull ?: primitive.doubleOrNull?.takeIf { it % 1.0 == 0.0 }?.toInt()
    }

    private fun JsonObject.doubleField(name: String): Double? =
        (this[name] as? JsonPrimitive)?.doubleOrNull

    private fun stripFence(text: String): String {
        if (!text.startsWith("```")) return text
        val withoutOpen = text.removePrefix("```json").removePrefix("```").trimStart()
        return withoutOpen.removeSuffix("```").trimEnd()
    }
}

/**
 * The acceptance criterion "the coach never diagnoses, prescribes, or doses"
 * (`pr.md` §6.15). A match does not edit the text; it makes the answer invalid, so the
 * one repair round can fix it and a second failure is visible.
 */
object CoachSafety {
    private val banned = listOf(
        Regex("\\byou (likely |probably |may |might )?have\\b", RegexOption.IGNORE_CASE),
        Regex("\\bdiagnos", RegexOption.IGNORE_CASE),
        Regex("\\bprescri", RegexOption.IGNORE_CASE),
        Regex("\\bdos(e|age|ing)\\b", RegexOption.IGNORE_CASE),
        // Any dose-like quantity, and the frequency wording that makes it one.
        Regex("\\b\\d+\\s?(mg|mcg|g|iu|ml|units?)\\b", RegexOption.IGNORE_CASE),
        Regex(
            "\\b(take|use|apply)\\s+\\d+\\s+(tablet|pill|capsule|dose|puff|unit)s?\\b",
            RegexOption.IGNORE_CASE,
        ),
        Regex(
            "\\b(once|twice|two times|three times)\\s+(daily|a day|per day)\\b",
            RegexOption.IGNORE_CASE,
        ),
        Regex("\\bstop taking\\b", RegexOption.IGNORE_CASE),
        Regex("\\bstart taking\\b", RegexOption.IGNORE_CASE),
        Regex("\\bcure\\b", RegexOption.IGNORE_CASE),
        Regex("\\btreat (your|the) (condition|disease|illness)\\b", RegexOption.IGNORE_CASE),
    )

    fun isUnsafe(text: String): Boolean = banned.any { it.containsMatchIn(text) }
}

sealed interface CoachExchangeResult {
    data class Answer(
        val answer: CoachAnswer,
        val repaired: Boolean,
        val inputTokens: Int?,
        val outputTokens: Int?,
    ) : CoachExchangeResult

    data class Failed(val failure: CoachFailure) : CoachExchangeResult
}

/**
 * One request, at most one repair, then a visible failure (`Tech-Stack.md` §9). A
 * provider failure is never repaired; only invalid output is.
 */
class CoachExchange(private val provider: CoachProvider) {
    @Suppress("ReturnCount")
    suspend fun run(request: CoachProviderRequest, plan: CoachPlan): CoachExchangeResult {
        val first = provider.complete(request)
        val firstText = when (first) {
            is CoachProviderResult.Failed -> return CoachExchangeResult.Failed(first.failure)
            is CoachProviderResult.Text -> first
        }
        when (val parsed = CoachParser.parse(firstText.text, plan)) {
            is CoachOutputResult.Valid -> return CoachExchangeResult.Answer(
                answer = parsed.answer,
                repaired = false,
                inputTokens = firstText.inputTokens,
                outputTokens = firstText.outputTokens,
            )

            is CoachOutputResult.Invalid -> {
                val repair = CoachPrompt.repair(request, parsed.raw, parsed.findings)
                val second = provider.complete(repair)
                val secondText = when (second) {
                    is CoachProviderResult.Failed -> return CoachExchangeResult.Failed(
                        second.failure,
                    )
                    is CoachProviderResult.Text -> second
                }
                return when (val retry = CoachParser.parse(secondText.text, plan)) {
                    is CoachOutputResult.Valid -> CoachExchangeResult.Answer(
                        answer = retry.answer,
                        repaired = true,
                        inputTokens = secondText.inputTokens,
                        outputTokens = secondText.outputTokens,
                    )

                    is CoachOutputResult.Invalid -> CoachExchangeResult.Failed(
                        CoachFailure(
                            kind = if (retry.findings.any {
                                    it.code == CoachFindingCode.UNSAFE_CLAIM
                                }
                            ) {
                                CoachFailureKind.SAFETY
                            } else {
                                CoachFailureKind.INVALID_OUTPUT
                            },
                            findings = retry.findings,
                        ),
                    )
                }
            }
        }
    }
}
