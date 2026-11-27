package app.openhealth.ui.screens.coach

import app.openhealth.core.ai.CoachCategory
import app.openhealth.core.ai.CoachProviderKind

/** The four states of a proposal card. */
enum class CoachProposalStatusUi { PROPOSED, ACCEPTED, DISCARDED, REVERTED }

enum class CoachRoleUi { PERSON, COACH, ERROR }

/** One before → after row of the diff card (`DESIGN.md` §14.2, Coach). */
data class CoachDiffRowModel(val label: String, val before: String?, val after: String)

data class CoachProposalModel(
    val id: String,
    val summary: String,
    val rationale: String,
    val rows: List<CoachDiffRowModel>,
    val status: CoachProposalStatusUi,
    /** True only for the newest accepted proposal in this app session (`DESIGN.md` §5.18). */
    val undoable: Boolean,
)

data class CoachMessageModel(
    val id: String,
    val role: CoachRoleUi,
    val body: String,
    val proposals: List<CoachProposalModel> = emptyList(),
    /** A stable key the screen translates; never a provider body. */
    val errorKey: String? = null,
)

/**
 * The coach surface and its setup, in one model. The key is presence-only: the plain key
 * never enters the state, the database or a log.
 */
data class CoachModel(
    val enabled: Boolean,
    val configured: Boolean,
    val providerKind: CoachProviderKind,
    val model: String,
    val baseUrl: String,
    val keyPresent: Boolean,
    val categories: Set<CoachCategory>,
    val healthConnect: Boolean,
    val healthConnectAvailable: Boolean,
    val disclosureAccepted: Boolean,
    val messages: List<CoachMessageModel>,
    val busy: Boolean,
    val error: String?,
    val status: String,
)
