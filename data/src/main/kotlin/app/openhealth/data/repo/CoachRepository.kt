package app.openhealth.data.repo

import app.openhealth.core.ai.CoachAction
import app.openhealth.core.ai.CoachActionCodec
import app.openhealth.core.ai.CoachAnswer
import app.openhealth.core.ai.CoachFinding
import app.openhealth.core.ai.CoachFailure
import app.openhealth.core.ai.CoachProposalDraft
import app.openhealth.core.ids.IdGenerator
import app.openhealth.core.ids.RandomIdGenerator
import app.openhealth.core.seam.ClockSeam
import app.openhealth.core.seam.SystemClock
import app.openhealth.data.db.Coach_message
import app.openhealth.data.db.Coach_proposal
import app.openhealth.data.db.OpenHealthDb
import app.openhealth.data.mapping.enumValueOrNull

enum class CoachRole { PERSON, COACH, ERROR }

enum class CoachProposalStatus(val key: String) {
    PROPOSED("proposed"),
    ACCEPTED("accepted"),
    DISCARDED("discarded"),
    REVERTED("reverted"),
    ;

    companion object {
        fun fromKey(key: String?): CoachProposalStatus =
            entries.firstOrNull { it.key == key } ?: PROPOSED
    }
}

data class CoachMessage(
    val id: String,
    val threadId: String,
    val role: CoachRole,
    val body: String,
    val createdAt: Long,
    val provider: String?,
    val model: String?,
    val inputTokens: Int?,
    val outputTokens: Int?,
    val proposalId: String?,
    val errorKey: String?,
    val repaired: Boolean,
)

data class CoachStoredAction(
    val id: String,
    val position: Long,
    val raw: String,
) {
    val action: CoachAction? get() = CoachActionCodec.decode(raw)
}

data class CoachProposal(
    val id: String,
    val messageId: String,
    val summary: String,
    val rationale: String,
    val status: CoachProposalStatus,
    val createdAt: Long,
    val decidedAt: Long?,
    val actions: List<CoachStoredAction>,
) {
    val decodedActions: List<CoachAction> get() = actions.mapNotNull(CoachStoredAction::action)
}

/**
 * The coach's conversation and proposal store (B15, `pr.md` §6.15). Append-only messages,
 * one status change per proposal, and a before-snapshot per approval. It never touches a
 * rollup: a proposal is not a measurement and only becomes one through the normal
 * training path when it is approved.
 */
class CoachRepository(
    private val db: OpenHealthDb,
    private val clock: ClockSeam = SystemClock,
    private val ids: IdGenerator = RandomIdGenerator(),
) {
    private var sync: app.openhealth.data.sync.SyncRepository? = null

    fun recordChangesWith(recorder: app.openhealth.data.sync.SyncRepository?) {
        sync = recorder
    }

    fun appendPerson(text: String, threadId: String = THREAD): String =
        appendMessage(
            threadId = threadId,
            role = CoachRole.PERSON,
            body = text,
            provider = null,
            model = null,
            inputTokens = null,
            outputTokens = null,
            proposalId = null,
            errorKey = null,
            repaired = false,
        )

    /**
     * Stores one coach answer and every proposal it carried. Returns the message id and
     * the proposal ids in card order. A note-only answer stores no proposal.
     */
    fun appendCoach(
        answer: CoachAnswer,
        provider: String,
        model: String,
        inputTokens: Int? = null,
        outputTokens: Int? = null,
        repaired: Boolean = false,
        threadId: String = THREAD,
    ): Pair<String, List<String>> {
        val messageId = appendMessage(
            threadId = threadId,
            role = CoachRole.COACH,
            body = answer.note,
            provider = provider,
            model = model,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            proposalId = null,
            errorKey = null,
            repaired = repaired,
        )
        val proposalIds = answer.proposals.map { draft ->
            val proposalId = ids.newId()
            db.coachQueries.insertProposal(
                id = proposalId,
                messageId = messageId,
                summary = draft.summary,
                rationale = draft.rationale,
                status = CoachProposalStatus.PROPOSED.key,
                createdAt = clock.now().toEpochMilliseconds(),
            )
            appendActions(proposalId, draft)
            proposalId
        }
        return messageId to proposalIds
    }

    /** A visible failure is a row too, so the conversation shows what went wrong. */
    fun appendError(
        failure: CoachFailure,
        provider: String,
        model: String,
        threadId: String = THREAD,
    ): String = appendMessage(
        threadId = threadId,
        role = CoachRole.ERROR,
        body = failure.errorKey(),
        provider = provider,
        model = model,
        inputTokens = null,
        outputTokens = null,
        proposalId = null,
        errorKey = failure.errorKey(),
        repaired = false,
    )

    fun messages(threadId: String = THREAD): List<CoachMessage> =
        db.coachQueries.messagesForThread(threadId).executeAsList().map { it.toDomain() }

    fun proposals(limit: Long = 50): List<CoachProposal> =
        db.coachQueries.recentProposals(limit).executeAsList().map { row ->
            row.toDomain(db.coachQueries.actionsForProposal(row.id).executeAsList())
        }

    fun proposal(id: String): CoachProposal? =
        db.coachQueries.proposalById(id).executeAsOneOrNull()?.let { row ->
            row.toDomain(db.coachQueries.actionsForProposal(id).executeAsList())
        }

    fun decide(id: String, status: CoachProposalStatus): Boolean {
        val proposal = proposal(id) ?: return false
        if (proposal.status != CoachProposalStatus.PROPOSED &&
            status == CoachProposalStatus.ACCEPTED
        ) {
            return false
        }
        db.coachQueries.decideProposal(
            status = status.key,
            decidedAt = clock.now().toEpochMilliseconds(),
            id = id,
        )
        return true
    }

    /** Marks an approved proposal as reverted after its snapshot was restored. */
    fun markReverted(id: String) {
        db.coachQueries.decideProposal(
            status = CoachProposalStatus.REVERTED.key,
            decidedAt = clock.now().toEpochMilliseconds(),
            id = id,
        )
    }

    fun saveSnapshot(proposalId: String, payload: String): String {
        val id = ids.newId()
        db.coachQueries.insertSnapshot(
            id = id,
            proposalId = proposalId,
            createdAt = clock.now().toEpochMilliseconds(),
            payload = payload,
        )
        return id
    }

    fun snapshot(proposalId: String): String? =
        db.coachQueries.snapshotForProposal(proposalId).executeAsOneOrNull()?.payload

    fun countMessages(): Long = db.coachQueries.countMessages().executeAsOne()

    private fun appendActions(proposalId: String, draft: CoachProposalDraft) {
        draft.actions.forEachIndexed { index, action ->
            db.coachQueries.insertAction(
                id = ids.newId(),
                proposalId = proposalId,
                position = index.toLong(),
                kind = action.kindKey(),
                payload = CoachActionCodec.encode(action),
            )
        }
    }

    private fun appendMessage(
        threadId: String,
        role: CoachRole,
        body: String,
        provider: String?,
        model: String?,
        inputTokens: Int?,
        outputTokens: Int?,
        proposalId: String?,
        errorKey: String?,
        repaired: Boolean,
    ): String {
        val id = ids.newId()
        db.coachQueries.insertMessage(
            id = id,
            threadId = threadId,
            role = role.name,
            body = body,
            createdAt = clock.now().toEpochMilliseconds(),
            provider = provider,
            model = model,
            inputTokens = inputTokens?.toLong(),
            outputTokens = outputTokens?.toLong(),
            proposalId = proposalId,
            errorKey = errorKey,
            repaired = repaired,
        )
        return id
    }

    private fun Coach_message.toDomain(): CoachMessage = CoachMessage(
        id = id,
        threadId = thread_id,
        role = enumValueOrNull<CoachRole>(role) ?: CoachRole.COACH,
        body = body,
        createdAt = created_at,
        provider = provider,
        model = model,
        inputTokens = input_tokens?.toInt(),
        outputTokens = output_tokens?.toInt(),
        proposalId = proposal_id,
        errorKey = error_key,
        repaired = repaired,
    )

    private fun Coach_proposal.toDomain(actions: List<app.openhealth.data.db.Coach_action>) =
        CoachProposal(
            id = id,
            messageId = message_id,
            summary = summary,
            rationale = rationale,
            status = CoachProposalStatus.fromKey(status),
            createdAt = created_at,
            decidedAt = decided_at,
            actions = actions.map {
                CoachStoredAction(id = it.id, position = it.position, raw = it.payload)
            },
        )

    companion object {
        const val THREAD = "coach"
    }
}

/** A type key for storage; the wire format is the parser's business. */
private fun CoachAction.kindKey(): String = when (this) {
    is CoachAction.AddExercise -> "add_exercise"
    is CoachAction.UpdateTarget -> "update_target"
    is CoachAction.MoveDay -> "move_day"
}

/** Stable code for the UI to translate; never a provider message or a raw body. */
private fun CoachFailure.errorKey(): String = buildString {
    append(kind.name.lowercase())
    if (findings.isNotEmpty()) {
        append(':')
        append(findings.map(CoachFinding::code).distinct().joinToString(",") { it.name.lowercase() })
    }
}
