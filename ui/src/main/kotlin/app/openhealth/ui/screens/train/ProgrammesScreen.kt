package app.openhealth.ui.screens.train

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.openhealth.ui.R
import app.openhealth.ui.components.OhButton
import app.openhealth.ui.components.OhButtonSize
import app.openhealth.ui.components.OhButtonVariant
import app.openhealth.ui.components.OhCard
import app.openhealth.ui.components.OhChip
import app.openhealth.ui.components.OhInsetGroup
import app.openhealth.ui.components.OhModalSheet
import app.openhealth.ui.components.OhRow
import app.openhealth.ui.components.OhTextField
import app.openhealth.ui.screens.common.OhEmptyState
import app.openhealth.ui.screens.common.OhPageHeader
import app.openhealth.ui.theme.OhTheme

/**
 * Multi-week programmes (`pr.md` §6.5): a name, a length of 1 to 12 weeks and the routines
 * of each week. One programme can be followed; the Train tab then says which week it is.
 */
@Composable
fun ProgrammesScreen(
    model: ProgrammesModel,
    onBack: () -> Unit,
    onEdit: (String?) -> Unit,
    onCloseEditor: () -> Unit,
    onSave: (ProgrammeDraft) -> Unit,
    onActivate: (String?) -> Unit,
    onArchive: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OhTheme.colors.bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = OhTheme.space.s6),
    ) {
        OhPageHeader(title = stringResource(R.string.programmes_title), onBack = onBack)
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            if (model.programmes.isEmpty()) {
                OhCard {
                    OhEmptyState(
                        title = stringResource(R.string.programmes_empty_title),
                        body = stringResource(R.string.programmes_empty_body),
                        actionLabel = stringResource(R.string.programmes_new),
                        onAction = { onEdit(null) },
                    )
                }
            } else {
                OhInsetGroup {
                    model.programmes.forEachIndexed { index, row ->
                        OhRow(
                            title = row.name,
                            subtitle = if (row.routinesText.isBlank()) {
                                stringResource(R.string.programmes_weeks, row.weeks)
                            } else {
                                stringResource(R.string.programmes_weeks, row.weeks) +
                                    " · " + row.routinesText
                            },
                            value = if (row.active) {
                                stringResource(
                                    R.string.programmes_week_of,
                                    row.currentWeek ?: 1,
                                    row.weeks,
                                )
                            } else {
                                null
                            },
                            showDivider = index > 0,
                            showChevron = true,
                            onClick = { onEdit(row.id) },
                        )
                    }
                }
                Spacer(Modifier.height(OhTheme.space.s4))
                OhButton(
                    text = stringResource(R.string.programmes_new),
                    onClick = { onEdit(null) },
                    variant = OhButtonVariant.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(OhTheme.space.s8))
        }
    }
    val draft = model.editing
    if (draft != null) {
        OhModalSheet(
            visible = true,
            onDismiss = onCloseEditor,
            title = stringResource(R.string.programmes_edit),
        ) {
            ProgrammeEditor(
                draft = draft,
                routines = model.routines,
                active = model.programmes.firstOrNull { it.id == draft.id }?.active == true,
                onSave = onSave,
                onActivate = onActivate,
                onArchive = onArchive,
            )
        }
    }
}

@Composable
private fun ProgrammeEditor(
    draft: ProgrammeDraft,
    routines: List<Pair<String, String>>,
    active: Boolean,
    onSave: (ProgrammeDraft) -> Unit,
    onActivate: (String?) -> Unit,
    onArchive: (String) -> Unit,
) {
    var name by remember(draft.id) { mutableStateOf(draft.name) }
    var weeks by remember(draft.id) { mutableStateOf(draft.weeks) }
    var invalid by remember(draft.id) { mutableStateOf(false) }
    OhTextField(
        value = name,
        onValueChange = {
            name = it
            invalid = false
        },
        placeholder = stringResource(R.string.programmes_name),
    )
    Spacer(Modifier.height(OhTheme.space.s3))
    weeks.forEachIndexed { index, chosen ->
        Text(
            text = stringResource(R.string.programmes_week, index + 1),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
        if (routines.isEmpty()) {
            Text(
                text = stringResource(R.string.programmes_no_routines),
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label3,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(OhTheme.space.s2),
            verticalArrangement = Arrangement.spacedBy(OhTheme.space.s2),
        ) {
            routines.forEach { (id, label) ->
                OhChip(
                    text = label,
                    selected = id in chosen,
                    onClick = {
                        weeks = weeks.mapIndexed { i, list ->
                            if (i != index) {
                                list
                            } else if (id in list) {
                                list - id
                            } else {
                                list + id
                            }
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(OhTheme.space.s3))
    }
    OhButton(
        text = stringResource(R.string.programmes_add_week),
        onClick = { if (weeks.size < MAX_WEEKS) weeks = weeks + listOf(emptyList()) },
        variant = OhButtonVariant.Ghost,
        size = OhButtonSize.Small,
        enabled = weeks.size < MAX_WEEKS,
    )
    if (weeks.size > 1) {
        OhButton(
            text = stringResource(R.string.programmes_remove_week),
            onClick = { weeks = weeks.dropLast(1) },
            variant = OhButtonVariant.Ghost,
            size = OhButtonSize.Small,
        )
    }
    if (invalid) {
        Text(
            text = stringResource(R.string.programmes_invalid),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.redInk,
        )
    }
    Spacer(Modifier.height(OhTheme.space.s3))
    OhButton(
        text = stringResource(R.string.programmes_save),
        onClick = {
            if (name.isBlank()) {
                invalid = true
            } else {
                onSave(ProgrammeDraft(draft.id, name.trim(), weeks))
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    val id = draft.id
    if (id != null) {
        Spacer(Modifier.height(OhTheme.space.s2))
        OhButton(
            text = if (active) {
                stringResource(R.string.programmes_stop)
            } else {
                stringResource(R.string.programmes_follow)
            },
            onClick = { onActivate(if (active) null else id) },
            variant = OhButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(OhTheme.space.s2))
        OhButton(
            text = stringResource(R.string.programmes_archive),
            onClick = { onArchive(id) },
            variant = OhButtonVariant.Danger,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private const val MAX_WEEKS = 12
