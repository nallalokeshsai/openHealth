package app.openhealth.ui.screens.mcp

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
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.openhealth.core.ai.McpScope
import app.openhealth.ui.R
import app.openhealth.ui.components.OhButton
import app.openhealth.ui.components.OhButtonVariant
import app.openhealth.ui.components.OhCard
import app.openhealth.ui.components.OhCardHeader
import app.openhealth.ui.components.OhChip
import app.openhealth.ui.components.OhChipStrip
import app.openhealth.ui.components.OhDialog
import app.openhealth.ui.components.OhInsetGroup
import app.openhealth.ui.components.OhRow
import app.openhealth.ui.components.OhSwitch
import app.openhealth.ui.components.OhTextField
import app.openhealth.ui.icons.OhModuleIcons
import app.openhealth.ui.screens.common.OhEmptyState
import app.openhealth.ui.screens.common.OhPageHeader
import app.openhealth.ui.screens.common.OhSectionTitle
import app.openhealth.ui.theme.OhTheme
import app.openhealth.ui.theme.ltrFigures
import app.openhealth.ui.theme.tabular

/**
 * The external-access screen (B15). The endpoint is loopback; a token is shown once and
 * only its digest is stored. The audit shows the tool and the decision, never a value.
 */
@Composable
fun McpScreen(
    model: McpModel,
    onBack: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onCreate: (String, Set<McpScope>) -> Unit,
    onRevoke: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var label by rememberSaveable { mutableStateOf("") }
    var scopes by remember {
        mutableStateOf(
            setOf(
                McpScope.READ_TRAINING,
                McpScope.READ_DAY,
            ),
        )
    }
    var revoke by remember { mutableStateOf<McpTokenModel?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OhTheme.colors.bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = OhTheme.space.s6),
    ) {
        OhPageHeader(title = stringResource(R.string.mcp_title), onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(OhTheme.space.s5),
        ) {
            Text(
                stringResource(R.string.mcp_intro),
                style = OhTheme.type.body,
                color = OhTheme.colors.label2,
                textAlign = TextAlign.Start,
            )
            model.error?.let { key ->
                Text(
                    errorText(key),
                    style = OhTheme.type.footnote,
                    color = OhTheme.colors.orangeInk,
                )
            }
            OhCard {
                OhRow(
                    title = stringResource(R.string.mcp_toggle),
                    mergeSemantics = false,
                    trailing = {
                        OhSwitch(
                            checked = model.enabled,
                            onCheckedChange = onToggle,
                            contentDescription = stringResource(R.string.mcp_toggle),
                        )
                    },
                )
                if (model.enabled) {
                    Spacer(Modifier.height(OhTheme.space.s3))
                    Text(
                        stringResource(R.string.mcp_endpoint),
                        style = OhTheme.type.footnote,
                        color = OhTheme.colors.label2,
                    )
                    SelectionContainer {
                        Text(
                            model.endpoint,
                            style = OhTheme.type.body.tabular().ltrFigures(),
                            color = OhTheme.colors.label,
                        )
                    }
                    Text(
                        if (model.running) {
                            stringResource(R.string.mcp_running)
                        } else {
                            stringResource(R.string.mcp_stopped)
                        },
                        style = OhTheme.type.footnote,
                        color = if (model.running) {
                            OhTheme.colors.greenInk
                        } else {
                            OhTheme.colors.label2
                        },
                    )
                }
            }
            if (model.enabled) {
                OhCard {
                    OhCardHeader(
                        eyebrow = stringResource(R.string.mcp_scopes),
                        title = stringResource(R.string.mcp_create),
                        glyph = OhModuleIcons.coach,
                    )
                    Spacer(Modifier.height(OhTheme.space.s3))
                    OhTextField(
                        value = label,
                        onValueChange = { label = it },
                        placeholder = stringResource(R.string.mcp_label_hint),
                        contentDescription = stringResource(R.string.mcp_label_hint),
                    )
                    Spacer(Modifier.height(OhTheme.space.s3))
                    OhChipStrip {
                        McpScope.entries.forEach { scope ->
                            OhChip(
                                text = scopeLabel(scope),
                                selected = scope in scopes,
                                onClick = {
                                    scopes = if (scope in scopes) {
                                        scopes - scope
                                    } else {
                                        scopes + scope
                                    }
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(OhTheme.space.s4))
                    OhButton(
                        text = stringResource(R.string.mcp_create),
                        onClick = {
                            onCreate(label, scopes)
                            label = ""
                        },
                        enabled = label.isNotBlank() && scopes.isNotEmpty(),
                    )
                }
                model.newToken?.let { token ->
                    OhCard {
                        Text(
                            stringResource(R.string.mcp_new_token_title),
                            style = OhTheme.type.title3,
                            color = OhTheme.colors.label,
                        )
                        Spacer(Modifier.height(OhTheme.space.s2))
                        SelectionContainer {
                            Text(
                                token,
                                style = OhTheme.type.body.tabular().ltrFigures(),
                                color = OhTheme.colors.accInk,
                            )
                        }
                        Spacer(Modifier.height(OhTheme.space.s2))
                        Text(
                            stringResource(R.string.mcp_new_token_body),
                            style = OhTheme.type.footnote,
                            color = OhTheme.colors.label2,
                            textAlign = TextAlign.Start,
                        )
                    }
                }
                Spacer(Modifier.height(OhTheme.space.s4))
            }
            TokenList(
                model = model,
                onRevoke = { id -> revoke = model.tokens.firstOrNull { it.id == id } },
            )
            AuditList(model = model)
            Spacer(Modifier.height(OhTheme.space.s8))
        }
    }
    OhDialog(
        visible = revoke != null,
        onDismiss = { revoke = null },
        title = stringResource(R.string.mcp_revoke_title),
        message = stringResource(R.string.mcp_revoke_body, revoke?.label.orEmpty()),
        primaryLabel = stringResource(R.string.mcp_revoke),
        destructive = true,
        onPrimary = {
            revoke?.let { onRevoke(it.id) }
            revoke = null
        },
        cancelLabel = stringResource(R.string.mcp_cancel),
        onCancel = { revoke = null },
    )
}

@Composable
private fun TokenList(model: McpModel, onRevoke: (String) -> Unit) {
    OhSectionTitle(stringResource(R.string.mcp_tokens))
    if (model.tokens.isEmpty()) {
        OhEmptyState(
            title = stringResource(R.string.mcp_no_tokens),
            body = stringResource(R.string.mcp_no_tokens_body),
            glyph = OhModuleIcons.coach,
        )
        return
    }
    model.tokens.forEach { token ->
        OhCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(OhTheme.space.s3),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        token.label,
                        style = OhTheme.type.subhead,
                        color = OhTheme.colors.label,
                    )
                    val scopeLine = token.scopes.map { scopeLabel(it) }.joinToString(" · ")
                    Text(
                        scopeLine,
                        style = OhTheme.type.footnote,
                        color = OhTheme.colors.label2,
                    )
                    Text(
                        stringResource(R.string.mcp_created, token.createdLabel),
                        style = OhTheme.type.footnote,
                        color = OhTheme.colors.labelDim,
                    )
                    Text(
                        token.lastUsedLabel?.let {
                            stringResource(R.string.mcp_last_used, it)
                        } ?: stringResource(R.string.mcp_never_used),
                        style = OhTheme.type.footnote,
                        color = OhTheme.colors.labelDim,
                    )
                }
                if (token.revoked) {
                    Text(
                        stringResource(R.string.mcp_revoked),
                        style = OhTheme.type.footnote,
                        color = OhTheme.colors.label2,
                    )
                } else {
                    val revokeLabel = stringResource(R.string.mcp_revoke_named, token.label)
                    OhButton(
                        text = stringResource(R.string.mcp_revoke),
                        onClick = { onRevoke(token.id) },
                        variant = OhButtonVariant.Danger,
                        modifier = Modifier.semantics { contentDescription = revokeLabel },
                    )
                }
            }
        }
    }
}

@Composable
private fun AuditList(model: McpModel) {
    OhSectionTitle(stringResource(R.string.mcp_audit))
    if (model.audit.isEmpty()) {
        OhEmptyState(
            title = stringResource(R.string.mcp_no_audit_title),
            body = stringResource(R.string.mcp_no_audit),
            glyph = OhModuleIcons.coach,
        )
        return
    }
    OhInsetGroup {
        model.audit.forEachIndexed { index, entry ->
            OhRow(
                title = entry.tool,
                subtitle = stringResource(R.string.mcp_call_at, entry.atLabel),
                value = if (entry.ok) {
                    stringResource(R.string.mcp_call_ok)
                } else {
                    stringResource(R.string.mcp_call_failed)
                },
                showDivider = index > 0,
            )
        }
    }
}

@Composable
private fun scopeLabel(scope: McpScope): String = when (scope) {
    McpScope.READ_PROFILE -> stringResource(R.string.mcp_scope_read_profile)
    McpScope.READ_TRAINING -> stringResource(R.string.mcp_scope_read_training)
    McpScope.READ_DAY -> stringResource(R.string.mcp_scope_read_day)
    McpScope.WRITE_BODY -> stringResource(R.string.mcp_scope_write_body)
    McpScope.WRITE_NUTRITION -> stringResource(R.string.mcp_scope_write_nutrition)
    McpScope.WRITE_NOTES -> stringResource(R.string.mcp_scope_write_notes)
}

@Composable
private fun errorText(key: String): String = when (key) {
    "mcp_error_start" -> stringResource(R.string.mcp_error_start)
    "mcp_error_label" -> stringResource(R.string.mcp_error_label)
    "mcp_error_scope" -> stringResource(R.string.mcp_error_scope)
    else -> stringResource(R.string.mcp_error_start)
}
