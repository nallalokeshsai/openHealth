package app.openhealth.android.coach

import android.content.Context
import app.openhealth.android.preferences.PreferencesStore
import app.openhealth.core.ai.AnthropicCoachProvider
import app.openhealth.core.ai.CoachCategory
import app.openhealth.core.ai.CoachConsent
import app.openhealth.core.ai.CoachDiff
import app.openhealth.core.ai.CoachDiffRow
import app.openhealth.core.ai.CoachExchange
import app.openhealth.core.ai.CoachExchangeResult
import app.openhealth.core.ai.CoachPrompt
import app.openhealth.core.ai.CoachProvider
import app.openhealth.core.ai.CoachProviderConfig
import app.openhealth.core.ai.CoachProviderKind
import app.openhealth.core.ai.CoachTarget
import app.openhealth.core.ai.SelfHostedCoachProvider
import app.openhealth.core.preferences.DisplayPolicy
import app.openhealth.core.seam.HttpTransport
import app.openhealth.core.seam.SecretVault
import app.openhealth.core.units.Measure
import app.openhealth.core.units.UcumUnit
import app.openhealth.data.LocalStore
import app.openhealth.data.repo.CoachApplyResult
import app.openhealth.data.repo.CoachPlanApplier
import app.openhealth.data.repo.CoachProposal
import app.openhealth.data.repo.CoachProposalStatus
import app.openhealth.data.repo.CoachRepository
import app.openhealth.ui.R as UiR
import app.openhealth.ui.screens.coach.CoachDiffRowModel
import app.openhealth.ui.screens.coach.CoachMessageModel
import app.openhealth.ui.screens.coach.CoachModel
import app.openhealth.ui.screens.coach.CoachProposalModel
import app.openhealth.ui.screens.coach.CoachProposalStatusUi
import app.openhealth.ui.screens.coach.CoachRoleUi
import kotlin.math.roundToInt

/**
 * The coach screen's holder (B15). It builds the request from the consented dataset,
 * runs the provider through the normal HTTP seam, stores the exchange, and applies or
 * reverts a proposal only when the person says so.
 *
 * The key is written to [SecretVault] and read at send time; it is never put in the
 * model, the database or an error message.
 */
@Suppress("TooManyFunctions", "LongParameterList")
class CoachPresenter(
    private val context: Context,
    private val store: LocalStore,
    private val vault: SecretVault,
    private val preferences: PreferencesStore,
    private val data: app.openhealth.data.repo.CoachDataBuilder,
    private val applier: CoachPlanApplier,
    private val coach: CoachRepository,
    private val transport: HttpTransport,
    private val policy: () -> DisplayPolicy = { DisplayPolicy.Standard },
) {
    /** Accepted in this app session, so Undo is a session-length tombstone, not a timer. */
    private val acceptedThisSession = mutableSetOf<String>()

    fun model(busy: Boolean = false, error: String? = null): CoachModel {
        val consent = preferences.coachConsent()
        val provider = preferences.coachProvider()
        val keyPresent = vault.has(keyName(provider.kind))
        val proposals = coach.proposals().groupBy { it.messageId }
        val messages = coach.messages().map { message ->
            CoachMessageModel(
                id = message.id,
                role = when (message.role) {
                    app.openhealth.data.repo.CoachRole.PERSON -> CoachRoleUi.PERSON
                    app.openhealth.data.repo.CoachRole.ERROR -> CoachRoleUi.ERROR
                    else -> CoachRoleUi.COACH
                },
                body = message.body,
                proposals = proposals[message.id].orEmpty().map(::proposalModel),
                errorKey = message.errorKey,
            )
        }
        return CoachModel(
            enabled = consent.enabled,
            configured = provider.isUsable() &&
                (
                    !requiresKey(provider.kind) ||
                        vault.has(keyName(provider.kind))
                    ),
            providerKind = provider.kind,
            model = provider.model,
            baseUrl = provider.baseUrl,
            keyPresent = keyPresent,
            categories = consent.categories,
            healthConnect = consent.healthConnect,
            healthConnectAvailable = true,
            disclosureAccepted = preferences.snapshot().coachDisclosed,
            messages = messages,
            busy = busy,
            error = error,
            status = status(consent, provider, keyPresent),
        )
    }

    fun summary(): String {
        val consent = preferences.coachConsent()
        return when {
            !consent.enabled -> context.getString(UiR.string.settings_coach_off)
            !consent.sendsAnything -> context.getString(UiR.string.settings_coach_none)
            else -> context.resources.getQuantityString(
                UiR.plurals.settings_coach_categories,
                consent.categories.size,
                consent.categories.size,
            )
        }
    }

    fun setEnabled(enabled: Boolean) {
        val current = preferences.coachConsent()
        preferences.setCoachConsent(current.copy(enabled = enabled))
    }

    fun setCategory(category: CoachCategory, enabled: Boolean) {
        val current = preferences.coachConsent()
        val categories = if (enabled) {
            current.categories + category
        } else {
            current.categories - category
        }
        preferences.setCoachConsent(current.copy(categories = categories))
    }

    fun setHealthConnect(enabled: Boolean) {
        preferences.setCoachConsent(preferences.coachConsent().copy(healthConnect = enabled))
    }

    fun saveProvider(kind: CoachProviderKind, model: String, baseUrl: String, key: String?) {
        preferences.setCoachProvider(
            CoachProviderConfig(kind = kind, model = model, baseUrl = baseUrl),
        )
        if (!key.isNullOrBlank()) {
            vault.put(keyName(kind), key.trim().toByteArray())
        }
    }

    fun setDisclosed() {
        preferences.setCoachDisclosed(true)
    }

    fun saveKey(plain: String): Boolean {
        if (plain.isBlank()) return false
        vault.put(keyName(preferences.coachProvider().kind), plain.trim().toByteArray())
        return true
    }

    fun clearKey() {
        vault.remove(keyName(preferences.coachProvider().kind))
    }

    /**
     * Sends one note. The person's message is stored first, so a provider outage leaves
     * the note visible rather than losing it (`pr.md` §6.15 acceptance).
     */
    @Suppress("ReturnCount")
    suspend fun send(note: String): CoachModel {
        val text = note.trim()
        if (text.isBlank()) return model(error = ERROR_NOTE)
        val consent = preferences.coachConsent()
        if (!consent.enabled) return model(error = ERROR_DISABLED)
        if (!consent.sendsAnything) return model(error = ERROR_CATEGORY)
        if (!preferences.snapshot().coachDisclosed) return model(error = ERROR_DISCLOSURE)
        val config = preferences.coachProvider()
        if (!config.isUsable()) return model(error = ERROR_DISABLED)
        val key = vault.get(keyName(config.kind))?.toString(Charsets.UTF_8)
        if (requiresKey(config.kind) && key.isNullOrBlank()) return model(error = ERROR_KEY)
        val provider = provider(config, key.orEmpty()) ?: return model(error = ERROR_DISABLED)
        coach.appendPerson(text)
        val request = CoachPrompt.build(
            dataset = data.dataset(),
            consent = consent,
            weightNeutral = !policy().shows(app.openhealth.core.preferences.SensitiveMetric.WEIGHT),
            note = text,
        )
        val plan = applier.plan()
        return when (val result = CoachExchange(provider).run(request, plan)) {
            is CoachExchangeResult.Answer -> {
                coach.appendCoach(
                    answer = result.answer,
                    meta = app.openhealth.data.repo.CoachTurnMeta(
                        provider = config.kind.key,
                        model = config.model,
                        inputTokens = result.inputTokens,
                        outputTokens = result.outputTokens,
                        repaired = result.repaired,
                    ),
                )
                model()
            }

            is CoachExchangeResult.Failed -> {
                coach.appendError(
                    result.failure,
                    app.openhealth.data.repo.CoachTurnMeta(
                        provider = config.kind.key,
                        model = config.model,
                    ),
                )
                model(error = errorKey(result.failure))
            }
        }
    }

    /** Returns a stable error key, or null when the change was applied. */
    fun accept(id: String): String? {
        val result = applier.apply(id)
        return when (result) {
            is CoachApplyResult.Applied -> {
                acceptedThisSession += id
                null
            }

            is CoachApplyResult.Rejected -> ERROR_ACCEPT
        }
    }

    fun discard(id: String) {
        coach.decide(id, CoachProposalStatus.DISCARDED)
    }

    /** Returns a stable error key, or null when the plan was restored. */
    @Suppress("ReturnCount")
    fun undo(id: String): String? {
        if (!isUndoable(id)) return ERROR_UNDO
        val undone = applier.undo(id)
        if (!undone) return ERROR_UNDO
        acceptedThisSession -= id
        return null
    }

    fun isUndoable(id: String): Boolean = id in acceptedThisSession

    private fun proposalModel(proposal: CoachProposal): CoachProposalModel {
        val actions = proposal.decodedActions
        val plan = applier.planFor(proposal.id)
        val rows = CoachDiff.rows(actions, plan).map(::rowModel)
        return CoachProposalModel(
            id = proposal.id,
            summary = proposal.summary,
            rationale = proposal.rationale,
            rows = rows,
            status = when (proposal.status) {
                CoachProposalStatus.PROPOSED -> CoachProposalStatusUi.PROPOSED
                CoachProposalStatus.ACCEPTED -> CoachProposalStatusUi.ACCEPTED
                CoachProposalStatus.DISCARDED -> CoachProposalStatusUi.DISCARDED
                CoachProposalStatus.REVERTED -> CoachProposalStatusUi.REVERTED
            },
            undoable = proposal.status == CoachProposalStatus.ACCEPTED &&
                isUndoable(proposal.id),
        )
    }

    private fun rowModel(row: CoachDiffRow): CoachDiffRowModel = when (row) {
        is CoachDiffRow.Added -> CoachDiffRowModel(
            label = row.exerciseName,
            before = null,
            after = targetText(
                CoachTarget(row.sets, row.reps, row.weightKg, row.rir),
            ),
        )

        is CoachDiffRow.Target -> CoachDiffRowModel(
            label = row.exerciseName,
            before = targetText(row.before),
            after = targetText(row.after),
        )

        is CoachDiffRow.Day -> CoachDiffRowModel(
            label = row.routineName,
            before = row.before?.let(::weekday),
            after = weekday(row.after),
        )
    }

    private fun targetText(target: CoachTarget): String {
        val sets = target.sets
        val reps = target.reps
        val head = if (sets != null && reps != null) {
            "$sets × $reps"
        } else {
            (sets ?: reps)?.toString()
                ?: "—"
        }
        val weight = target.weightKg?.let { weightText(it) }
        val rir = target.rir?.let { "RIR ${trimNumber(it)}" }
        return listOfNotNull(head, weight, rir).joinToString(" · ")
    }

    private fun weightText(kg: Double): String {
        val imperial = store.profile.active()?.units == "imperial"
        return if (imperial) {
            val pounds = Measure(kg, UcumUnit.KILOGRAM).convertTo(UcumUnit.POUND).value
            "${trimNumber(pounds)} lb"
        } else {
            "${trimNumber(kg)} kg"
        }
    }

    @Suppress("MagicNumber") // one decimal is the display precision of a load
    private fun trimNumber(value: Double): String {
        val rounded = (value * 10).roundToInt() / 10.0
        return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
    }

    @Suppress("MagicNumber") // ISO weekday numbers
    private fun weekday(day: Int): String {
        val resource = when (day) {
            1 -> UiR.string.train_weekday_mon
            2 -> UiR.string.train_weekday_tue
            3 -> UiR.string.train_weekday_wed
            4 -> UiR.string.train_weekday_thu
            5 -> UiR.string.train_weekday_fri
            6 -> UiR.string.train_weekday_sat
            else -> UiR.string.train_weekday_sun
        }
        return context.getString(resource)
    }

    private fun status(
        consent: CoachConsent,
        provider: CoachProviderConfig,
        keyPresent: Boolean,
    ): String = when {
        !consent.enabled -> context.getString(UiR.string.coach_status_off)
        !provider.isUsable() || (requiresKey(provider.kind) && !keyPresent) ->
            context.getString(UiR.string.coach_error_disabled)

        else -> context.getString(UiR.string.coach_status_ready)
    }

    private fun provider(config: CoachProviderConfig, key: String): CoachProvider? =
        when (config.kind) {
            CoachProviderKind.ANTHROPIC -> if (key.isBlank()) {
                null
            } else {
                AnthropicCoachProvider(transport, key, config.model)
            }

            CoachProviderKind.SELF_HOSTED -> SelfHostedCoachProvider(
                transport = transport,
                baseUrl = config.baseUrl,
                model = config.model,
                apiKey = key,
            )
        }

    private fun requiresKey(kind: CoachProviderKind): Boolean = kind == CoachProviderKind.ANTHROPIC

    private fun keyName(kind: CoachProviderKind): String = "coach.api_key.${kind.key}"

    private fun errorKey(failure: app.openhealth.core.ai.CoachFailure): String =
        "coach_error_${failure.kind.name.lowercase()}"

    companion object {
        const val ERROR_NOTE = "coach_error_note"
        const val ERROR_DISABLED = "coach_error_disabled"
        const val ERROR_CATEGORY = "coach_error_need_category"
        const val ERROR_KEY = "coach_error_no_key"
        const val ERROR_DISCLOSURE = "coach_error_disclosure"
        const val ERROR_ACCEPT = "coach_error_accept"
        const val ERROR_UNDO = "coach_error_undo"
    }
}
