package app.openhealth.ui.screens.sync

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import app.openhealth.core.sync.SyncStatus
import app.openhealth.ui.R
import app.openhealth.ui.components.OhButton
import app.openhealth.ui.components.OhButtonSize
import app.openhealth.ui.components.OhButtonVariant
import app.openhealth.ui.components.OhCard
import app.openhealth.ui.components.OhDialog
import app.openhealth.ui.components.OhInsetGroup
import app.openhealth.ui.components.OhRow
import app.openhealth.ui.components.OhTextField
import app.openhealth.ui.icons.OhIcons
import app.openhealth.ui.screens.common.OhEmptyState
import app.openhealth.ui.screens.common.OhPageHeader
import app.openhealth.ui.screens.common.OhSectionTitle
import app.openhealth.ui.theme.OhTheme
import app.openhealth.ui.theme.ohBlockDirection

/**
 * Settings → Sync (`pr.md` §6.24, `DESIGN.md` §14.1 settings surface). Local-first: the
 * page states what the device has, offers pairing, and shows a conflict with both sides
 * and their provenance. It never resolves one silently.
 */
@Composable
fun SyncScreen(
    state: SyncModel,
    onBack: () -> Unit,
    onPair: (String, String) -> Unit,
    onSyncNow: () -> Unit,
    onDisconnect: () -> Unit,
    onKeep: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var url by rememberSaveable { mutableStateOf(state.serverUrl) }
    var code by rememberSaveable { mutableStateOf("") }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OhTheme.colors.bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = OhTheme.space.s6),
    ) {
        OhPageHeader(title = stringResource(R.string.sync_title), onBack = onBack)
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Text(
                text = stringResource(R.string.sync_intro),
                style = OhTheme.type.body.copy(textDirection = ohBlockDirection()),
                color = OhTheme.colors.label2,
                textAlign = TextAlign.Start,
            )
            Spacer(Modifier.height(OhTheme.space.s5))
            if (!state.paired) {
                UnpairedSection(
                    url = url,
                    code = code,
                    busy = state.busy,
                    error = state.pairError,
                    onUrl = { url = it },
                    onCode = { code = it.uppercase() },
                    onPair = { onPair(url.trim(), code.trim()) },
                )
            } else {
                PairedSection(state = state, onSyncNow = onSyncNow, onDisconnect = onDisconnect)
            }

            OhSectionTitle(stringResource(R.string.sync_conflicts_title))
            if (state.conflicts.isEmpty()) {
                OhEmptyState(
                    title = stringResource(R.string.sync_conflicts_none_title),
                    body = stringResource(R.string.sync_conflicts_none),
                    glyph = OhIcons.check,
                )
            } else {
                state.conflicts.forEach { conflict ->
                    ConflictCard(conflict = conflict, busy = state.busy, onKeep = onKeep)
                    Spacer(Modifier.height(OhTheme.space.s3))
                }
            }
            Spacer(Modifier.height(OhTheme.space.s6))
            Text(
                text = stringResource(R.string.sync_optional_note),
                style = OhTheme.type.footnote.copy(textDirection = ohBlockDirection()),
                color = OhTheme.colors.labelDim,
                textAlign = TextAlign.Start,
            )
            Spacer(Modifier.height(OhTheme.space.s6))
        }
    }
}

@Composable
private fun UnpairedSection(
    url: String,
    code: String,
    busy: Boolean,
    error: String?,
    onUrl: (String) -> Unit,
    onCode: (String) -> Unit,
    onPair: () -> Unit,
) {
    OhInsetGroup {
        OhRow(
            title = stringResource(R.string.sync_server_label),
            subtitle = stringResource(R.string.sync_server_body),
            leadingIcon = OhIcons.refresh,
        )
    }
    Spacer(Modifier.height(OhTheme.space.s3))
    OhTextField(
        value = url,
        onValueChange = onUrl,
        placeholder = stringResource(R.string.sync_server_placeholder),
        enabled = !busy,
        contentDescription = stringResource(R.string.sync_server_label),
    )
    Spacer(Modifier.height(OhTheme.space.s3))
    OhTextField(
        value = code,
        onValueChange = onCode,
        placeholder = stringResource(R.string.sync_code_placeholder),
        enabled = !busy,
        error = error,
        contentDescription = stringResource(R.string.sync_code_label),
    )
    Spacer(Modifier.height(OhTheme.space.s4))
    OhButton(
        text = if (busy) {
            stringResource(R.string.sync_pairing)
        } else {
            stringResource(R.string.sync_pair)
        },
        onClick = onPair,
        enabled = !busy && url.isNotBlank() && code.isNotBlank(),
    )
}

@Composable
private fun PairedSection(state: SyncModel, onSyncNow: () -> Unit, onDisconnect: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    OhInsetGroup {
        OhRow(
            title = stringResource(R.string.sync_server_label),
            value = state.serverUrl,
            leadingIcon = OhIcons.refresh,
            leadingTint = OhTheme.colors.surface2,
            leadingInk = OhTheme.colors.label2,
        )
        OhRow(
            title = stringResource(R.string.sync_status_label),
            value = statusLabel(state.status),
            showDivider = true,
            leadingIcon = statusIcon(state.status),
            leadingTint = OhTheme.colors.surface2,
            leadingInk = statusInk(state.status),
        )
        OhRow(
            title = stringResource(R.string.sync_pending_label),
            value = state.pending.toString(),
            showDivider = true,
        )
        state.lastSyncedLabel?.let { label ->
            OhRow(
                title = stringResource(R.string.sync_last_synced),
                value = label,
                showDivider = true,
            )
        }
    }
    state.error?.takeIf { it.isNotBlank() }?.let { error ->
        Spacer(Modifier.height(OhTheme.space.s3))
        Text(
            text = stringResource(R.string.sync_error_label),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.orangeInk,
        )
        Text(
            text = error,
            style = OhTheme.type.body.copy(textDirection = ohBlockDirection()),
            color = OhTheme.colors.orangeInk,
            textAlign = TextAlign.Start,
        )
    }
    Spacer(Modifier.height(OhTheme.space.s4))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(OhTheme.space.s3),
    ) {
        OhButton(
            text = if (state.busy) {
                stringResource(R.string.sync_syncing)
            } else {
                stringResource(R.string.sync_now)
            },
            onClick = onSyncNow,
            enabled = !state.busy,
            modifier = Modifier.weight(1f),
        )
        OhButton(
            text = stringResource(R.string.sync_disconnect),
            onClick = { confirming = true },
            variant = OhButtonVariant.Secondary,
            enabled = !state.busy,
            modifier = Modifier.weight(1f),
        )
    }
    OhDialog(
        visible = confirming,
        onDismiss = { confirming = false },
        title = stringResource(R.string.sync_disconnect_title),
        message = stringResource(R.string.sync_disconnect_body),
        primaryLabel = stringResource(R.string.sync_disconnect),
        cancelLabel = stringResource(R.string.sync_disconnect_keep),
        destructive = true,
        onPrimary = {
            confirming = false
            onDisconnect()
        },
        onCancel = { confirming = false },
    )
}

@Composable
private fun ConflictCard(
    conflict: SyncConflictModel,
    busy: Boolean,
    onKeep: (String, Boolean) -> Unit,
) {
    OhCard {
        Text(
            text = entityLabel(conflict.entity),
            style = OhTheme.type.title3,
            color = OhTheme.colors.label,
        )
        Text(
            text = stringResource(R.string.sync_conflict_reason),
            style = OhTheme.type.caption,
            color = OhTheme.colors.label2,
        )
        Spacer(Modifier.height(OhTheme.space.s3))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OhTheme.space.s3),
        ) {
            ConflictSide(
                label = stringResource(R.string.sync_conflict_local),
                side = conflict.local,
                modifier = Modifier.weight(1f),
            )
            ConflictSide(
                label = stringResource(R.string.sync_conflict_remote),
                side = conflict.remote,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(OhTheme.space.s3))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OhTheme.space.s3),
        ) {
            OhButton(
                text = keepLabel(conflict, conflict.local),
                onClick = { onKeep(conflict.id, true) },
                variant = OhButtonVariant.Secondary,
                size = OhButtonSize.Small,
                enabled = !busy,
                modifier = Modifier.weight(1f),
            )
            OhButton(
                text = keepLabel(conflict, conflict.remote),
                onClick = { onKeep(conflict.id, false) },
                variant = OhButtonVariant.Secondary,
                size = OhButtonSize.Small,
                enabled = !busy,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ConflictSide(label: String, side: SyncSideModel, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
        Text(
            text = side.device,
            style = OhTheme.type.caption2,
            color = OhTheme.colors.label2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = side.whenLabel,
            style = OhTheme.type.caption2,
            color = OhTheme.colors.label2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (side.fields.isEmpty()) {
            Text(
                text = stringResource(R.string.sync_deleted_version),
                style = OhTheme.type.body,
                color = OhTheme.colors.label,
            )
        }
        side.fields.forEach { field ->
            Text(
                text = "${fieldLabel(field.key)}: ${field.value.ifBlank {
                    stringResource(R.string.sync_value_not_set)
                }}",
                style = OhTheme.type.body,
                color = OhTheme.colors.label,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun keepLabel(conflict: SyncConflictModel, side: SyncSideModel): String {
    val value = conflict.headline(side).take(HEADLINE_CHARS)
    return if (value.isBlank()) {
        stringResource(R.string.sync_keep_this_version)
    } else {
        stringResource(R.string.sync_keep_value, value)
    }
}

private const val HEADLINE_CHARS = 18

@Composable
private fun entityLabel(entity: String): String = when (entity) {
    "profile" -> stringResource(R.string.sync_entity_profile)
    "settings" -> stringResource(R.string.sync_entity_settings)
    "note" -> stringResource(R.string.sync_entity_note)
    "routine" -> stringResource(R.string.sync_entity_routine)
    "session" -> stringResource(R.string.sync_entity_session)
    "food" -> stringResource(R.string.sync_entity_food)
    "entry" -> stringResource(R.string.sync_entity_entry)
    "measurement" -> stringResource(R.string.sync_entity_measurement)
    "vital" -> stringResource(R.string.sync_entity_vital)
    "activity" -> stringResource(R.string.sync_entity_activity)
    else -> stringResource(R.string.sync_entity_generic)
}

@Composable
private fun fieldLabel(key: String): String = when (key) {
    "name", "display_name" -> stringResource(R.string.sync_field_name)
    "body" -> stringResource(R.string.sync_field_body)
    "value" -> stringResource(R.string.sync_field_value)
    "title" -> stringResource(R.string.sync_field_title)
    "notes" -> stringResource(R.string.sync_field_notes)
    "weight_kg" -> stringResource(R.string.sync_field_weight)
    "height_cm" -> stringResource(R.string.sync_field_height)
    "active" -> stringResource(R.string.sync_field_active)
    else -> key.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

@Composable
private fun statusLabel(status: SyncStatus): String = when (status) {
    SyncStatus.LOCAL_ONLY -> stringResource(R.string.sync_status_local_only)
    SyncStatus.SYNCING -> stringResource(R.string.sync_status_syncing)
    SyncStatus.SYNCED -> stringResource(R.string.sync_status_synced)
    SyncStatus.OFFLINE -> stringResource(R.string.sync_status_offline)
    SyncStatus.ERROR -> stringResource(R.string.sync_status_error)
}

private fun statusIcon(status: SyncStatus) = when (status) {
    SyncStatus.SYNCED -> OhIcons.check
    SyncStatus.OFFLINE -> OhIcons.cloudOff
    SyncStatus.ERROR -> OhIcons.warning
    else -> OhIcons.refresh
}

@Composable
private fun statusInk(status: SyncStatus) = when (status) {
    SyncStatus.SYNCED -> OhTheme.colors.acc
    SyncStatus.OFFLINE, SyncStatus.ERROR -> OhTheme.colors.orangeInk
    else -> OhTheme.colors.label2
}
