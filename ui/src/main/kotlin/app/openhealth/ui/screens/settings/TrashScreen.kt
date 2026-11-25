package app.openhealth.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.openhealth.ui.R
import app.openhealth.ui.components.OhButton
import app.openhealth.ui.components.OhButtonSize
import app.openhealth.ui.components.OhButtonVariant
import app.openhealth.ui.components.OhCard
import app.openhealth.ui.components.OhDialog
import app.openhealth.ui.components.OhIconButton
import app.openhealth.ui.components.OhInsetGroup
import app.openhealth.ui.components.OhRow
import app.openhealth.ui.icons.OhIcons
import app.openhealth.ui.screens.common.OhEmptyState
import app.openhealth.ui.screens.common.OhPageHeader
import app.openhealth.ui.theme.OhTheme

/**
 * Trash (`pr.md` §6.29): every deleted row for 30 days, with Restore and an explicit
 * permanent delete. Permanent delete clears the payload and keeps the tombstone marker
 * so a later sync cannot resurrect the row.
 */
@Composable
fun TrashScreen(
    items: List<TrashItemModel>,
    onBack: () -> Unit,
    onRestore: (TrashItemModel) -> Unit,
    onPurge: (TrashItemModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    var purging by remember { mutableStateOf<TrashItemModel?>(null) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OhTheme.colors.bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = OhTheme.space.s6),
    ) {
        OhPageHeader(title = stringResource(R.string.settings_trash), onBack = onBack)
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            if (items.isEmpty()) {
                OhCard {
                    OhEmptyState(
                        title = stringResource(R.string.trash_empty_title),
                        body = stringResource(R.string.trash_empty_body),
                        glyph = OhIcons.delete,
                        actionLabel = stringResource(R.string.trash_empty_action),
                        onAction = onBack,
                    )
                }
            } else {
                OhInsetGroup {
                    items.forEachIndexed { index, item ->
                        OhRow(
                            title = stringResource(item.kind.labelRes()),
                            subtitle = stringResource(
                                R.string.trash_row_subtitle,
                                item.detail,
                                item.deletedLabel,
                            ),
                            showDivider = index > 0,
                            mergeSemantics = false,
                            trailing = {
                                OhButton(
                                    text = stringResource(R.string.trash_restore),
                                    onClick = { onRestore(item) },
                                    variant = OhButtonVariant.Ghost,
                                    size = OhButtonSize.ExtraSmall,
                                )
                                OhIconButton(
                                    icon = OhIcons.delete,
                                    contentDescription = stringResource(R.string.trash_purge_title),
                                    onClick = { purging = item },
                                    tint = OhTheme.colors.redInk,
                                )
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(OhTheme.space.s8))
        }
    }
    OhDialog(
        visible = purging != null,
        onDismiss = { purging = null },
        title = stringResource(R.string.trash_purge_title),
        message = stringResource(R.string.trash_purge_message),
        primaryLabel = stringResource(R.string.trash_purge_action),
        destructive = true,
        onPrimary = {
            purging?.let(onPurge)
            purging = null
        },
        cancelLabel = stringResource(R.string.settings_cancel),
        onCancel = { purging = null },
    )
}

@Composable
private fun TrashKind.labelRes(): Int = when (this) {
    TrashKind.NOTE -> R.string.trash_kind_note
    TrashKind.OBSERVATION -> R.string.trash_kind_observation
    TrashKind.MEASUREMENT -> R.string.trash_kind_measurement
    TrashKind.VITAL -> R.string.trash_kind_vital
    TrashKind.PHOTO -> R.string.trash_kind_photo
    TrashKind.ENTRY -> R.string.trash_kind_entry
    TrashKind.SET -> R.string.trash_kind_set
    TrashKind.SESSION -> R.string.trash_kind_session
    TrashKind.ROUTINE_EXERCISE -> R.string.trash_kind_routine_exercise
}
