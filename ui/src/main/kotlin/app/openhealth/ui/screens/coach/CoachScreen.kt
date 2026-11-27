package app.openhealth.ui.screens.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import app.openhealth.core.ai.CoachCategory
import app.openhealth.core.ai.CoachProviderKind
import app.openhealth.ui.R
import app.openhealth.ui.components.OhButton
import app.openhealth.ui.components.OhButtonVariant
import app.openhealth.ui.components.OhCard
import app.openhealth.ui.components.OhCardHeader
import app.openhealth.ui.components.OhInsetGroup
import app.openhealth.ui.components.OhModalSheet
import app.openhealth.ui.components.OhRow
import app.openhealth.ui.components.OhSegmentedControl
import app.openhealth.ui.components.OhSwitch
import app.openhealth.ui.components.OhTextField
import app.openhealth.ui.icons.OhModuleIcons
import app.openhealth.ui.screens.common.OhEmptyState
import app.openhealth.ui.screens.common.OhPageHeader
import app.openhealth.ui.screens.common.OhSectionTitle
import app.openhealth.ui.theme.OhTheme
import app.openhealth.ui.theme.ohBlockDirection

/**
 * The coach surface (`DESIGN.md` §14.2, Coach): the setup card, the per-category
 * consent, and a conversation of notes and diff cards. No chat bubble, no avatar and no
 * typing dots; Accept is the one accent action, Discard is quiet.
 */
@Composable
fun CoachScreen(
    model: CoachModel,
    onBack: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onCategory: (CoachCategory, Boolean) -> Unit,
    onHealthConnect: (Boolean) -> Unit,
    onProvider: (CoachProviderKind, String, String) -> Unit,
    onSaveKey: (String) -> Unit,
    onClearKey: () -> Unit,
    onAcceptDisclosure: () -> Unit,
    onSend: (String) -> Unit,
    onAccept: (String) -> Unit,
    onDiscard: (String) -> Unit,
    onUndo: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var disclosure by remember { mutableStateOf(false) }
    var note by rememberSaveable { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var provider by remember(model.providerKind) { mutableStateOf(model.providerKind) }
    var modelName by remember(model.model) { mutableStateOf(model.model) }
    var baseUrl by remember(model.baseUrl) { mutableStateOf(model.baseUrl) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OhTheme.colors.bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = OhTheme.space.s6),
    ) {
        OhPageHeader(title = stringResource(R.string.coach_title), onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(OhTheme.space.s5),
        ) {
            model.error?.let { ErrorLine(it) }
            IntroCard(
                enabled = model.enabled,
                onToggle = onToggle,
            )
            if (model.enabled) {
                ProviderCard(
                    provider = provider,
                    modelName = modelName,
                    baseUrl = baseUrl,
                    keyPresent = model.keyPresent,
                    key = key,
                    onProvider = { provider = it },
                    onModelName = { modelName = it },
                    onBaseUrl = { baseUrl = it },
                    onKey = { key = it },
                    onSave = {
                        onProvider(provider, modelName.trim(), baseUrl.trim())
                        if (key.isNotBlank()) {
                            onSaveKey(key)
                            key = ""
                        }
                    },
                    onClearKey = onClearKey,
                )
                ConsentCard(
                    categories = model.categories,
                    healthConnect = model.healthConnect,
                    onCategory = onCategory,
                    onHealthConnect = onHealthConnect,
                    onDisclosure = { disclosure = true },
                )
                if (!model.disclosureAccepted) {
                    DisclosureNotice(onAccept = onAcceptDisclosure)
                }
                Composer(
                    note = note,
                    busy = model.busy,
                    onNote = { note = it },
                    onSend = {
                        if (note.isNotBlank()) {
                            onSend(note)
                            note = ""
                        }
                    },
                )
                if (model.messages.isEmpty()) {
                    OhEmptyState(
                        title = stringResource(R.string.coach_empty_title),
                        body = stringResource(R.string.coach_empty_body),
                        glyph = OhModuleIcons.coach,
                    )
                } else {
                    model.messages.forEach { message ->
                        MessageBlock(
                            message = message,
                            onAccept = onAccept,
                            onDiscard = onDiscard,
                            onUndo = onUndo,
                        )
                    }
                }
                Spacer(Modifier.height(OhTheme.space.s8))
            }
        }
    }
    OhModalSheet(
        visible = disclosure,
        onDismiss = { disclosure = false },
        title = stringResource(R.string.coach_disclosure_title),
        cancelLabel = stringResource(R.string.mcp_close),
        onCancel = { disclosure = false },
    ) {
        Text(
            stringResource(R.string.coach_disclosure_body),
            style = OhTheme.type.body.copy(textDirection = ohBlockDirection()),
            color = OhTheme.colors.label2,
            textAlign = TextAlign.Start,
        )
        Spacer(Modifier.height(OhTheme.space.s4))
        OhInsetGroup {
            CoachCategory.entries.forEachIndexed { index, category ->
                OhRow(
                    title = categoryLabel(category),
                    value = if (category in model.categories) {
                        stringResource(R.string.coach_disclosure_included)
                    } else {
                        stringResource(R.string.coach_disclosure_excluded)
                    },
                    showDivider = index > 0,
                )
            }
        }
        Spacer(Modifier.height(OhTheme.space.s4))
        OhRow(
            title = stringResource(R.string.coach_health_connect),
            value = if (model.healthConnect) {
                stringResource(R.string.coach_disclosure_included)
            } else {
                stringResource(R.string.coach_disclosure_excluded)
            },
        )
    }
}

@Composable
private fun IntroCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    OhCard {
        OhCardHeader(
            eyebrow = stringResource(R.string.coach_card_eyebrow),
            title = stringResource(R.string.coach_intro_title),
            glyph = OhModuleIcons.coach,
        )
        Spacer(Modifier.height(OhTheme.space.s3))
        Text(
            stringResource(R.string.coach_intro_body),
            style = OhTheme.type.body.copy(textDirection = ohBlockDirection()),
            color = OhTheme.colors.label2,
            textAlign = TextAlign.Start,
        )
        Spacer(Modifier.height(OhTheme.space.s4))
        OhRow(
            title = if (enabled) {
                stringResource(R.string.coach_turn_off)
            } else {
                stringResource(R.string.coach_turn_on)
            },
            mergeSemantics = false,
            trailing = {
                OhSwitch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    contentDescription = stringResource(R.string.coach_title),
                )
            },
        )
        if (!enabled) {
            Spacer(Modifier.height(OhTheme.space.s2))
            Text(
                stringResource(R.string.coach_off_body),
                style = OhTheme.type.footnote.copy(textDirection = ohBlockDirection()),
                color = OhTheme.colors.label2,
                textAlign = TextAlign.Start,
            )
        }
    }
}

@Composable
private fun ProviderCard(
    provider: CoachProviderKind,
    modelName: String,
    baseUrl: String,
    keyPresent: Boolean,
    key: String,
    onProvider: (CoachProviderKind) -> Unit,
    onModelName: (String) -> Unit,
    onBaseUrl: (String) -> Unit,
    onKey: (String) -> Unit,
    onSave: () -> Unit,
    onClearKey: () -> Unit,
) {
    OhCard {
        OhCardHeader(
            eyebrow = stringResource(R.string.coach_provider),
            title = stringResource(R.string.coach_provider_title),
        )
        Spacer(Modifier.height(OhTheme.space.s3))
        OhSegmentedControl(
            options = listOf(
                stringResource(R.string.coach_provider_anthropic),
                stringResource(R.string.coach_provider_self_hosted),
            ),
            selectedIndex = if (provider == CoachProviderKind.ANTHROPIC) 0 else 1,
            onSelect = {
                onProvider(
                    if (it == 0) CoachProviderKind.ANTHROPIC else CoachProviderKind.SELF_HOSTED,
                )
            },
        )
        Spacer(Modifier.height(OhTheme.space.s3))
        if (provider == CoachProviderKind.SELF_HOSTED) {
            OhTextField(
                value = baseUrl,
                onValueChange = onBaseUrl,
                placeholder = stringResource(R.string.coach_url_hint),
                contentDescription = stringResource(R.string.coach_url_label),
            )
            Spacer(Modifier.height(OhTheme.space.s3))
        }
        OhTextField(
            value = modelName,
            onValueChange = onModelName,
            placeholder = stringResource(R.string.coach_model_hint),
            contentDescription = stringResource(R.string.coach_model),
        )
        Spacer(Modifier.height(OhTheme.space.s3))
        Text(
            if (keyPresent) {
                stringResource(R.string.coach_key_present)
            } else {
                stringResource(R.string.coach_key_missing)
            },
            style = OhTheme.type.footnote.copy(textDirection = ohBlockDirection()),
            color = OhTheme.colors.label2,
        )
        Spacer(Modifier.height(OhTheme.space.s2))
        OhTextField(
            value = key,
            onValueChange = onKey,
            placeholder = stringResource(R.string.coach_key_hint),
            visualTransformation = PasswordVisualTransformation(),
            contentDescription = stringResource(R.string.coach_key_label),
        )
        Spacer(Modifier.height(OhTheme.space.s2))
        Text(
            stringResource(R.string.coach_key_note),
            style = OhTheme.type.caption.copy(textDirection = ohBlockDirection()),
            color = OhTheme.colors.label3,
            textAlign = TextAlign.Start,
        )
        Spacer(Modifier.height(OhTheme.space.s4))
        Row(horizontalArrangement = Arrangement.spacedBy(OhTheme.space.s3)) {
            OhButton(text = stringResource(R.string.coach_save), onClick = onSave)
            if (keyPresent) {
                OhButton(
                    text = stringResource(R.string.coach_key_clear),
                    onClick = onClearKey,
                    variant = OhButtonVariant.Quiet,
                )
            }
        }
    }
}

@Composable
private fun ConsentCard(
    categories: Set<CoachCategory>,
    healthConnect: Boolean,
    onCategory: (CoachCategory, Boolean) -> Unit,
    onHealthConnect: (Boolean) -> Unit,
    onDisclosure: () -> Unit,
) {
    OhCard {
        OhCardHeader(
            eyebrow = stringResource(R.string.coach_consent_eyebrow),
            title = stringResource(R.string.coach_consent_title),
        )
        Spacer(Modifier.height(OhTheme.space.s2))
        Text(
            stringResource(R.string.coach_consent_body),
            style = OhTheme.type.footnote.copy(textDirection = ohBlockDirection()),
            color = OhTheme.colors.label2,
            textAlign = TextAlign.Start,
        )
        Spacer(Modifier.height(OhTheme.space.s3))
        OhInsetGroup {
            CoachCategory.entries.forEachIndexed { index, category ->
                OhRow(
                    title = categoryLabel(category),
                    showDivider = index > 0,
                    mergeSemantics = false,
                    trailing = {
                        OhSwitch(
                            checked = category in categories,
                            onCheckedChange = { onCategory(category, it) },
                            contentDescription = categoryLabel(category),
                        )
                    },
                )
            }
        }
        Spacer(Modifier.height(OhTheme.space.s3))
        OhRow(
            title = stringResource(R.string.coach_health_connect),
            subtitle = stringResource(R.string.coach_health_connect_body),
            mergeSemantics = false,
            trailing = {
                OhSwitch(
                    checked = healthConnect,
                    onCheckedChange = onHealthConnect,
                    contentDescription = stringResource(R.string.coach_health_connect),
                )
            },
        )
        Spacer(Modifier.height(OhTheme.space.s3))
        OhButton(
            text = stringResource(R.string.coach_disclosure_title),
            onClick = onDisclosure,
            variant = OhButtonVariant.Quiet,
        )
    }
}

@Composable
private fun DisclosureNotice(onAccept: () -> Unit) {
    OhCard {
        Text(
            stringResource(R.string.coach_disclosure_title),
            style = OhTheme.type.title3,
            color = OhTheme.colors.label,
        )
        Spacer(Modifier.height(OhTheme.space.s2))
        Text(
            stringResource(R.string.coach_disclosure_body),
            style = OhTheme.type.body.copy(textDirection = ohBlockDirection()),
            color = OhTheme.colors.label2,
            textAlign = TextAlign.Start,
        )
        Spacer(Modifier.height(OhTheme.space.s4))
        OhButton(text = stringResource(R.string.coach_disclosure_accept), onClick = onAccept)
    }
}

@Composable
private fun Composer(note: String, busy: Boolean, onNote: (String) -> Unit, onSend: () -> Unit) {
    OhCard {
        OhTextField(
            value = note,
            onValueChange = onNote,
            placeholder = stringResource(R.string.coach_note_hint),
            singleLine = false,
            contentDescription = stringResource(R.string.coach_note_hint),
        )
        Spacer(Modifier.height(OhTheme.space.s3))
        OhButton(
            text = if (busy) {
                stringResource(R.string.coach_working)
            } else {
                stringResource(R.string.coach_send)
            },
            onClick = onSend,
            enabled = !busy && note.isNotBlank(),
        )
    }
}

@Composable
private fun MessageBlock(
    message: CoachMessageModel,
    onAccept: (String) -> Unit,
    onDiscard: (String) -> Unit,
    onUndo: (String) -> Unit,
) {
    when (message.role) {
        CoachRoleUi.PERSON -> OhCard {
            OhSectionTitle(stringResource(R.string.coach_you))
            Text(
                message.body,
                style = OhTheme.type.body.copy(textDirection = ohBlockDirection()),
                color = OhTheme.colors.label,
                textAlign = TextAlign.Start,
            )
        }

        CoachRoleUi.ERROR -> OhCard {
            OhSectionTitle(stringResource(R.string.coach_reply))
            Text(
                errorText(message.errorKey),
                style = OhTheme.type.body.copy(textDirection = ohBlockDirection()),
                color = OhTheme.colors.orangeInk,
                textAlign = TextAlign.Start,
            )
        }

        CoachRoleUi.COACH -> {
            OhCard {
                OhSectionTitle(stringResource(R.string.coach_reply))
                Text(
                    message.body,
                    style = OhTheme.type.body.copy(textDirection = ohBlockDirection()),
                    color = OhTheme.colors.label,
                    textAlign = TextAlign.Start,
                )
            }
            message.proposals.forEach { proposal ->
                ProposalCard(
                    proposal = proposal,
                    onAccept = onAccept,
                    onDiscard = onDiscard,
                    onUndo = onUndo,
                )
            }
        }
    }
}

@Composable
private fun ProposalCard(
    proposal: CoachProposalModel,
    onAccept: (String) -> Unit,
    onDiscard: (String) -> Unit,
    onUndo: (String) -> Unit,
) {
    OhCard {
        OhCardHeader(
            eyebrow = stringResource(R.string.coach_proposed_change),
            title = proposal.summary,
            glyph = OhModuleIcons.coach,
        )
        Spacer(Modifier.height(OhTheme.space.s3))
        proposal.rows.forEach { row ->
            Text(
                row.label,
                style = OhTheme.type.subhead,
                color = OhTheme.colors.label,
            )
            row.before?.let { before ->
                Text(
                    stringResource(R.string.coach_before) + " · " + before,
                    style = OhTheme.type.footnote.copy(textDirection = ohBlockDirection()),
                    color = OhTheme.colors.label2,
                )
            }
            Text(
                stringResource(R.string.coach_after) + " · " + row.after,
                style = OhTheme.type.body.copy(textDirection = ohBlockDirection()),
                color = OhTheme.colors.label,
            )
            Spacer(Modifier.height(OhTheme.space.s2))
        }
        Text(
            proposal.rationale,
            style = OhTheme.type.footnote.copy(textDirection = ohBlockDirection()),
            color = OhTheme.colors.accInk,
            textAlign = TextAlign.Start,
        )
        Spacer(Modifier.height(OhTheme.space.s3))
        when (proposal.status) {
            CoachProposalStatusUi.PROPOSED -> Row(
                horizontalArrangement = Arrangement.spacedBy(OhTheme.space.s3),
            ) {
                OhButton(
                    text = stringResource(R.string.coach_accept),
                    onClick = { onAccept(proposal.id) },
                    variant = OhButtonVariant.Primary,
                )
                OhButton(
                    text = stringResource(R.string.coach_discard),
                    onClick = { onDiscard(proposal.id) },
                    variant = OhButtonVariant.Quiet,
                )
            }

            CoachProposalStatusUi.ACCEPTED -> Row(
                horizontalArrangement = Arrangement.spacedBy(OhTheme.space.s3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.coach_status_accepted),
                    style = OhTheme.type.footnote.copy(textDirection = ohBlockDirection()),
                    color = OhTheme.colors.greenInk,
                )
                if (proposal.undoable) {
                    OhButton(
                        text = stringResource(R.string.coach_undo),
                        onClick = { onUndo(proposal.id) },
                        variant = OhButtonVariant.Secondary,
                    )
                }
            }

            CoachProposalStatusUi.DISCARDED -> Text(
                stringResource(R.string.coach_status_discarded),
                style = OhTheme.type.footnote.copy(textDirection = ohBlockDirection()),
                color = OhTheme.colors.label2,
            )

            CoachProposalStatusUi.REVERTED -> Text(
                stringResource(R.string.coach_status_reverted),
                style = OhTheme.type.footnote.copy(textDirection = ohBlockDirection()),
                color = OhTheme.colors.label2,
            )
        }
    }
}

@Composable
private fun ErrorLine(key: String) {
    Text(
        errorText(key),
        style = OhTheme.type.footnote.copy(textDirection = ohBlockDirection()),
        color = OhTheme.colors.orangeInk,
        textAlign = TextAlign.Start,
    )
}

@Composable
private fun categoryLabel(category: CoachCategory): String = when (category) {
    CoachCategory.PROFILE -> stringResource(R.string.coach_category_profile)
    CoachCategory.TRAINING -> stringResource(R.string.coach_category_training)
    CoachCategory.RECOVERY -> stringResource(R.string.coach_category_recovery)
    CoachCategory.NUTRITION -> stringResource(R.string.coach_category_nutrition)
    CoachCategory.SLEEP -> stringResource(R.string.coach_category_sleep)
    CoachCategory.VITALS -> stringResource(R.string.coach_category_vitals)
}

@Composable
private fun errorText(key: String?): String = when (key) {
    "coach_error_offline" -> stringResource(R.string.coach_error_offline)
    "coach_error_unauthorized" -> stringResource(R.string.coach_error_unauthorized)
    "coach_error_rate_limited" -> stringResource(R.string.coach_error_rate_limited)
    "coach_error_server" -> stringResource(R.string.coach_error_server)
    "coach_error_timeout" -> stringResource(R.string.coach_error_timeout)
    "coach_error_invalid_response" -> stringResource(R.string.coach_error_invalid_response)
    "coach_error_invalid_output" -> stringResource(R.string.coach_error_invalid_output)
    "coach_error_safety" -> stringResource(R.string.coach_error_safety)
    "coach_error_disabled" -> stringResource(R.string.coach_error_disabled)
    "coach_error_no_key" -> stringResource(R.string.coach_error_no_key)
    "coach_error_need_category" -> stringResource(R.string.coach_error_need_category)
    "coach_error_note" -> stringResource(R.string.coach_error_note)
    "coach_error_disclosure" -> stringResource(R.string.coach_error_disclosure)
    "coach_error_accept" -> stringResource(R.string.coach_error_accept)
    "coach_error_undo" -> stringResource(R.string.coach_error_undo)
    else -> stringResource(R.string.coach_error_server)
}
