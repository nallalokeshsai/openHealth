package app.openhealth.ui.screens.coach

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import app.openhealth.core.ai.CoachCategory
import app.openhealth.core.ai.CoachProviderKind
import app.openhealth.ui.R
import app.openhealth.ui.components.OhButton
import app.openhealth.ui.components.OhButtonVariant
import app.openhealth.ui.components.OhCard
import app.openhealth.ui.components.OhCardHeader
import app.openhealth.ui.components.OhDialog
import app.openhealth.ui.components.OhInsetGroup
import app.openhealth.ui.components.OhModalSheet
import app.openhealth.ui.components.OhRow
import app.openhealth.ui.components.OhSegmentedControl
import app.openhealth.ui.components.OhSwitch
import app.openhealth.ui.components.OhTextField
import app.openhealth.ui.icons.OhModuleIcons
import app.openhealth.ui.screens.common.OhEmptyState
import app.openhealth.ui.screens.common.OhPageHeader
import app.openhealth.ui.screens.common.OhProgressLine
import app.openhealth.ui.screens.common.OhSectionTitle
import app.openhealth.ui.theme.OhTheme

/**
 * The coach surface (`DESIGN.md` §14.2, Coach): the setup, the per-category consent, the
 * diff cards and the notes. No chat bubble, no avatar and no typing dots; Accept is the
 * one accent action, Discard is quiet.
 *
 * Once the provider is configured and the disclosure is acknowledged, the composer and the
 * conversation come first: the screen's reason to exist is a proposal, not a settings list.
 */
@Composable
fun CoachScreen(
    model: CoachModel,
    onBack: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onCategory: (CoachCategory, Boolean) -> Unit,
    onHealthConnect: (Boolean) -> Unit,
    onProvider: (CoachProviderKind, String, String, String) -> Unit,
    onClearKey: () -> Unit,
    onAcceptDisclosure: () -> Unit,
    onSend: (String) -> Unit,
    onAccept: (String) -> Unit,
    onDiscard: (String) -> Unit,
    onUndo: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var disclosure by remember { mutableStateOf(false) }
    var clearKey by remember { mutableStateOf(false) }
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
        BusyLine(model.busy)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(OhTheme.space.s5),
        ) {
            if (!model.enabled) {
                IntroCard(enabled = false, onToggle = onToggle)
                return@Column
            }
            if (!model.configured) {
                IntroCard(enabled = true, onToggle = onToggle)
                ProviderCard(
                    provider = provider,
                    modelName = modelName,
                    baseUrl = baseUrl,
                    keyPresent = model.keyPresent,
                    key = key,
                    onProviderKind = {
                        provider = it
                        key = ""
                    },
                    onModelName = { modelName = it },
                    onBaseUrl = { baseUrl = it },
                    onKey = { key = it },
                    onSave = {
                        onProvider(provider, modelName.trim(), baseUrl.trim(), key)
                        key = ""
                    },
                    onClearKey = { clearKey = true },
                )
                ConsentCard(
                    categories = model.categories,
                    healthConnect = model.healthConnect,
                    onCategory = onCategory,
                    onHealthConnect = onHealthConnect,
                    onDisclosure = { disclosure = true },
                )
            } else if (!model.disclosureAccepted) {
                IntroCard(enabled = true, onToggle = onToggle)
                DisclosureCard(
                    categories = model.categories,
                    healthConnect = model.healthConnect,
                    onOpen = { disclosure = true },
                    onAccept = onAcceptDisclosure,
                )
                ProviderCard(
                    provider = provider,
                    modelName = modelName,
                    baseUrl = baseUrl,
                    keyPresent = model.keyPresent,
                    key = key,
                    onProviderKind = {
                        provider = it
                        key = ""
                    },
                    onModelName = { modelName = it },
                    onBaseUrl = { baseUrl = it },
                    onKey = { key = it },
                    onSave = {
                        onProvider(provider, modelName.trim(), baseUrl.trim(), key)
                        key = ""
                    },
                    onClearKey = { clearKey = true },
                )
                ConsentCard(
                    categories = model.categories,
                    healthConnect = model.healthConnect,
                    onCategory = onCategory,
                    onHealthConnect = onHealthConnect,
                    onDisclosure = { disclosure = true },
                )
            } else {
                Composer(
                    note = note,
                    busy = model.busy,
                    error = model.error,
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
                Spacer(Modifier.height(OhTheme.space.s5))
                ProviderCard(
                    provider = provider,
                    modelName = modelName,
                    baseUrl = baseUrl,
                    keyPresent = model.keyPresent,
                    key = key,
                    onProviderKind = {
                        provider = it
                        key = ""
                    },
                    onModelName = { modelName = it },
                    onBaseUrl = { baseUrl = it },
                    onKey = { key = it },
                    onSave = {
                        onProvider(provider, modelName.trim(), baseUrl.trim(), key)
                        key = ""
                    },
                    onClearKey = { clearKey = true },
                )
                ConsentCard(
                    categories = model.categories,
                    healthConnect = model.healthConnect,
                    onCategory = onCategory,
                    onHealthConnect = onHealthConnect,
                    onDisclosure = { disclosure = true },
                )
                Spacer(Modifier.height(OhTheme.space.s8))
            }
        }
    }
    OhDialog(
        visible = clearKey,
        onDismiss = { clearKey = false },
        title = stringResource(R.string.coach_key_clear_title),
        message = stringResource(R.string.coach_key_clear_body),
        primaryLabel = stringResource(R.string.coach_key_clear),
        destructive = true,
        onPrimary = {
            onClearKey()
            clearKey = false
        },
        cancelLabel = stringResource(R.string.mcp_cancel),
        onCancel = { clearKey = false },
    )
    OhModalSheet(
        visible = disclosure,
        onDismiss = { disclosure = false },
        title = stringResource(R.string.coach_disclosure_title),
        cancelLabel = stringResource(R.string.mcp_close),
        onCancel = { disclosure = false },
    ) {
        Text(
            stringResource(R.string.coach_disclosure_body),
            style = OhTheme.type.body,
            color = OhTheme.colors.label2,
            textAlign = TextAlign.Start,
        )
        Spacer(Modifier.height(OhTheme.space.s4))
        DisclosureList(
            categories = model.categories,
            healthConnect = model.healthConnect,
        )
    }
}

/** A 2 dp indeterminate line under the header while a model answer is in flight (§6.4). */
@Composable
private fun BusyLine(busy: Boolean) {
    if (!busy) return
    val transition = rememberInfiniteTransition(label = "coach-busy")
    val fraction by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "coach-busy-fraction",
    )
    OhProgressLine(fraction = fraction)
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
            style = OhTheme.type.body,
            color = OhTheme.colors.label2,
            textAlign = TextAlign.Start,
        )
        Spacer(Modifier.height(OhTheme.space.s4))
        OhRow(
            title = stringResource(R.string.coach_title),
            mergeSemantics = false,
            trailing = {
                OhSwitch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    contentDescription = stringResource(R.string.coach_switch_master),
                )
            },
        )
        if (!enabled) {
            Spacer(Modifier.height(OhTheme.space.s2))
            Text(
                stringResource(R.string.coach_off_body),
                style = OhTheme.type.footnote,
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
    onProviderKind: (CoachProviderKind) -> Unit,
    onModelName: (String) -> Unit,
    onBaseUrl: (String) -> Unit,
    onKey: (String) -> Unit,
    onSave: () -> Unit,
    onClearKey: () -> Unit,
) {
    val usable = modelName.isNotBlank() &&
        (provider == CoachProviderKind.ANTHROPIC || baseUrl.isNotBlank())
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
                onProviderKind(
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
            style = OhTheme.type.footnote,
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
            style = OhTheme.type.footnote,
            color = OhTheme.colors.labelDim,
            textAlign = TextAlign.Start,
        )
        Spacer(Modifier.height(OhTheme.space.s4))
        Row(horizontalArrangement = Arrangement.spacedBy(OhTheme.space.s3)) {
            OhButton(
                text = stringResource(R.string.coach_save),
                onClick = onSave,
                variant = OhButtonVariant.Secondary,
                enabled = usable,
            )
            if (keyPresent) {
                OhButton(
                    text = stringResource(R.string.coach_key_clear),
                    onClick = onClearKey,
                    variant = OhButtonVariant.Danger,
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
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
            textAlign = TextAlign.Start,
        )
        Spacer(Modifier.height(OhTheme.space.s3))
        OhInsetGroup {
            CoachCategory.entries.forEachIndexed { index, category ->
                val label = categoryLabel(category)
                OhRow(
                    title = label,
                    showDivider = index > 0,
                    mergeSemantics = false,
                    trailing = {
                        OhSwitch(
                            checked = category in categories,
                            onCheckedChange = { onCategory(category, it) },
                            contentDescription = stringResource(
                                R.string.coach_switch_category,
                                label,
                            ),
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
            text = stringResource(R.string.coach_disclosure_open),
            onClick = onDisclosure,
            variant = OhButtonVariant.Quiet,
        )
    }
}

/** The first send's list, with the categories in the order the settings show them. */
@Composable
private fun DisclosureList(categories: Set<CoachCategory>, healthConnect: Boolean) {
    OhInsetGroup {
        CoachCategory.entries.forEachIndexed { index, category ->
            OhRow(
                title = categoryLabel(category),
                value = if (category in categories) {
                    stringResource(R.string.coach_disclosure_included)
                } else {
                    stringResource(R.string.coach_disclosure_excluded)
                },
                showDivider = index > 0,
            )
        }
    }
    Spacer(Modifier.height(OhTheme.space.s3))
    OhRow(
        title = stringResource(R.string.coach_health_connect),
        value = if (healthConnect) {
            stringResource(R.string.coach_disclosure_included)
        } else {
            stringResource(R.string.coach_disclosure_excluded)
        },
    )
}

@Composable
private fun DisclosureCard(
    categories: Set<CoachCategory>,
    healthConnect: Boolean,
    onOpen: () -> Unit,
    onAccept: () -> Unit,
) {
    OhCard {
        Text(
            stringResource(R.string.coach_disclosure_title),
            style = OhTheme.type.title3,
            color = OhTheme.colors.label,
        )
        Spacer(Modifier.height(OhTheme.space.s2))
        Text(
            stringResource(R.string.coach_disclosure_body),
            style = OhTheme.type.body,
            color = OhTheme.colors.label2,
            textAlign = TextAlign.Start,
        )
        Spacer(Modifier.height(OhTheme.space.s3))
        DisclosureList(categories = categories, healthConnect = healthConnect)
        Spacer(Modifier.height(OhTheme.space.s4))
        OhButton(text = stringResource(R.string.coach_disclosure_accept), onClick = onAccept)
        Spacer(Modifier.height(OhTheme.space.s2))
        OhButton(
            text = stringResource(R.string.coach_disclosure_open),
            onClick = onOpen,
            variant = OhButtonVariant.Quiet,
        )
    }
}

@Composable
private fun Composer(
    note: String,
    busy: Boolean,
    error: String?,
    onNote: (String) -> Unit,
    onSend: () -> Unit,
) {
    OhCard {
        OhTextField(
            value = note,
            onValueChange = onNote,
            placeholder = stringResource(R.string.coach_note_hint),
            singleLine = false,
            contentDescription = stringResource(R.string.coach_note_hint),
        )
        error?.let {
            Spacer(Modifier.height(OhTheme.space.s2))
            Text(
                errorText(it),
                style = OhTheme.type.footnote,
                color = OhTheme.colors.redInk,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
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
                style = OhTheme.type.body,
                color = OhTheme.colors.label,
                textAlign = TextAlign.Start,
            )
        }

        CoachRoleUi.ERROR -> OhCard {
            OhSectionTitle(stringResource(R.string.coach_reply))
            Text(
                errorText(message.errorKey),
                style = OhTheme.type.body,
                color = OhTheme.colors.orangeInk,
                textAlign = TextAlign.Start,
            )
        }

        CoachRoleUi.COACH -> {
            OhCard {
                OhSectionTitle(stringResource(R.string.coach_reply))
                Text(
                    message.body,
                    style = OhTheme.type.body,
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
    val acceptLabel = stringResource(R.string.coach_accept_named, proposal.summary)
    val discardLabel = stringResource(R.string.coach_discard_named, proposal.summary)
    val undoLabel = stringResource(R.string.coach_undo_named, proposal.summary)
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
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
            )
            Text(
                diffText(row),
                style = OhTheme.type.body,
                color = OhTheme.colors.label,
                textAlign = TextAlign.Start,
            )
            Spacer(Modifier.height(OhTheme.space.s2))
        }
        Text(
            proposal.rationale,
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
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
                    modifier = Modifier.semantics { contentDescription = acceptLabel },
                )
                OhButton(
                    text = stringResource(R.string.coach_discard),
                    onClick = { onDiscard(proposal.id) },
                    variant = OhButtonVariant.Quiet,
                    modifier = Modifier.semantics { contentDescription = discardLabel },
                )
            }

            CoachProposalStatusUi.ACCEPTED -> Row(
                horizontalArrangement = Arrangement.spacedBy(OhTheme.space.s3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.coach_status_accepted),
                    style = OhTheme.type.footnote,
                    color = OhTheme.colors.greenInk,
                )
                if (proposal.undoable) {
                    OhButton(
                        text = stringResource(R.string.coach_undo),
                        onClick = { onUndo(proposal.id) },
                        variant = OhButtonVariant.Secondary,
                        modifier = Modifier.semantics { contentDescription = undoLabel },
                    )
                }
            }

            CoachProposalStatusUi.DISCARDED -> Text(
                stringResource(R.string.coach_status_discarded),
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
            )

            CoachProposalStatusUi.REVERTED -> Text(
                stringResource(R.string.coach_status_reverted),
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
            )
        }
    }
}

/** "before → after" on one 17/400 row; an added target has only the after side. */
@Composable
private fun diffText(row: CoachDiffRowModel): androidx.compose.ui.text.AnnotatedString {
    val quiet = OhTheme.colors.label2
    return buildAnnotatedString {
        if (row.before != null) {
            withStyle(SpanStyle(color = quiet)) { append(row.before) }
            append("  ")
            withStyle(SpanStyle(color = quiet)) { append("→") }
            append("  ")
        }
        append(row.after)
    }
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
