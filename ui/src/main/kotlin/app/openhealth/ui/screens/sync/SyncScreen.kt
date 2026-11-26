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
import androidx.compose.ui.text.style.TextOverflow
import app.openhealth.core.sync.SyncStatus
import app.openhealth.ui.R
import app.openhealth.ui.components.OhButton
import app.openhealth.ui.components.OhButtonSize
import app.openhealth.ui.components.OhButtonVariant
import app.openhealth.ui.components.OhCard
import app.openhealth.ui.components.OhInsetGroup
import app.openhealth.ui.components.OhRow
import app.openhealth.ui.components.OhTextField
import app.openhealth.ui.screens.common.OhPageHeader
import app.openhealth.ui.theme.OhTheme

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
                style = OhTheme.type.body,
                color = OhTheme.colors.label2,
            )
            Spacer(Modifier.height(OhTheme.space.s5))
            if (!state.paired) {
                OhInsetGroup {
                    OhRow(
                        title = stringResource(R.string.sync_server_label),
                        subtitle = stringResource(R.string.sync_server_body),
                        leadingIcon = app.openhealth.ui.icons.OhIcons.refresh,
                    )
                }
                Spacer(Modifier.height(OhTheme.space.s3))
                OhTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = stringResource(R.string.sync_server_placeholder),
                    enabled = !state.busy,
                    contentDescription = stringResource(R.string.sync_server_label),
                )
                Spacer(Modifier.height(OhTheme.space.s3))
                OhTextField(
                    value = code,
                    onValueChange = { code = it.uppercase() },
                    placeholder = stringResource(R.string.sync_code_placeholder),
                    enabled = !state.busy,
                    contentDescription = stringResource(R.string.sync_code_label),
                )
                Spacer(Modifier.height(OhTheme.space.s4))
                OhButton(
                    text = if (state.busy) {
                        stringResource(R.string.sync_pairing)
                    } else {
                        stringResource(R.string.sync_pair)
                    },
                    onClick = { onPair(url.trim(), code.trim()) },
                    enabled = !state.busy && url.isNotBlank() && code.isNotBlank(),
                )
            } else {
                OhInsetGroup {
                    OhRow(
                        title = stringResource(R.string.sync_server_label),
                        value = state.serverUrl,
                        leadingIcon = app.openhealth.ui.icons.OhIcons.refresh,
                    )
                    OhRow(
                        title = stringResource(R.string.sync_status_label),
                        value = statusLabel(state.status),
                        showDivider = true,
                        leadingIcon = statusIcon(state.status),
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
                    state.error?.takeIf { it.isNotBlank() }?.let { error ->
                        OhRow(
                            title = stringResource(R.string.sync_error_label),
                            value = error,
                            showDivider = true,
                            destructive = true,
                        )
                    }
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
                        onClick = onDisconnect,
                        variant = OhButtonVariant.Secondary,
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(OhTheme.space.s6))
            Text(
                text = stringResource(R.string.sync_conflicts_title),
                style = OhTheme.type.title3,
                color = OhTheme.colors.label,
            )
            Spacer(Modifier.height(OhTheme.space.s2))
            if (state.conflicts.isEmpty()) {
                Text(
                    text = stringResource(R.string.sync_conflicts_none),
                    style = OhTheme.type.body,
                    color = OhTheme.colors.label2,
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
                style = OhTheme.type.caption,
                color = OhTheme.colors.label3,
            )
            Spacer(Modifier.height(OhTheme.space.s6))
        }
    }
}

@Composable
private fun ConflictCard(
    conflict: SyncConflictModel,
    busy: Boolean,
    onKeep: (String, Boolean) -> Unit,
) {
    OhCard {
        Text(
            text = conflict.entityLabel,
            style = OhTheme.type.title3,
            color = OhTheme.colors.label,
        )
        Text(
            text = conflict.reason,
            style = OhTheme.type.caption,
            color = OhTheme.colors.label2,
        )
        Spacer(Modifier.height(OhTheme.space.s3))
        ConflictSide(
            label = stringResource(R.string.sync_conflict_local),
            side = conflict.local,
        )
        Spacer(Modifier.height(OhTheme.space.s2))
        ConflictSide(
            label = stringResource(R.string.sync_conflict_remote),
            side = conflict.remote,
        )
        Spacer(Modifier.height(OhTheme.space.s3))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OhTheme.space.s3),
        ) {
            OhButton(
                text = stringResource(R.string.sync_keep_local),
                onClick = { onKeep(conflict.id, true) },
                variant = OhButtonVariant.Secondary,
                size = OhButtonSize.Small,
                enabled = !busy,
                modifier = Modifier.weight(1f),
            )
            OhButton(
                text = stringResource(R.string.sync_keep_remote),
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
private fun ConflictSide(label: String, side: SyncSideModel) {
    Column {
        Text(
            text = "$label · ${side.device} · ${side.whenLabel}",
            style = OhTheme.type.caption,
            color = OhTheme.colors.label2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        side.fields.forEach { field ->
            Text(
                text = "${field.label}: ${field.value}",
                style = OhTheme.type.body,
                color = OhTheme.colors.label,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun statusLabel(status: SyncStatus): String = when (status) {
    SyncStatus.LOCAL_ONLY -> stringResource(R.string.sync_status_local_only)
    SyncStatus.SYNCING -> stringResource(R.string.sync_status_syncing)
    SyncStatus.SYNCED -> stringResource(R.string.sync_status_synced)
    SyncStatus.OFFLINE -> stringResource(R.string.sync_status_offline)
    SyncStatus.ERROR -> stringResource(R.string.sync_status_error)
}

@Composable
private fun statusIcon(status: SyncStatus) = when (status) {
    SyncStatus.SYNCED -> app.openhealth.ui.icons.OhIcons.check
    SyncStatus.OFFLINE, SyncStatus.ERROR -> app.openhealth.ui.icons.OhIcons.warning
    else -> app.openhealth.ui.icons.OhIcons.refresh
}
