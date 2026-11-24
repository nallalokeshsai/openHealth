package app.openhealth.ui.screens.train

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.openhealth.ui.R
import app.openhealth.ui.body.BodyMapVariant
import app.openhealth.ui.body.OhBodyMap
import app.openhealth.ui.body.OhBodyMapTable
import app.openhealth.ui.body.muscleLabel
import app.openhealth.ui.components.OhButton
import app.openhealth.ui.components.OhButtonVariant
import app.openhealth.ui.components.OhCard
import app.openhealth.ui.components.OhDialog
import app.openhealth.ui.components.OhInsetGroup
import app.openhealth.ui.components.OhKpiBand
import app.openhealth.ui.components.OhKpiFigure
import app.openhealth.ui.components.OhModalSheet
import app.openhealth.ui.components.OhRow
import app.openhealth.ui.components.OhStepper
import app.openhealth.ui.components.OhTextField
import app.openhealth.ui.screens.common.OhPageHeader
import app.openhealth.ui.screens.common.OhScreen
import app.openhealth.ui.screens.common.OhSectionTitle
import app.openhealth.ui.screens.common.number
import app.openhealth.ui.screens.common.shortDate
import app.openhealth.ui.theme.OhTheme
import app.openhealth.ui.theme.OhWeight
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * A finished session's summary (`DESIGN.md` §14.2): the KPI band, the records, the loaded
 * muscles as a mini map, how it felt, and the edit, move and delete actions. Delete goes
 * through Trash with an undo (`pr.md` §6.29).
 */
@Composable
fun SessionSummaryScreen(
    model: SessionSummaryModel,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onMove: (String, LocalDate) -> Unit,
    onDelete: (String) -> Unit,
    onSaveNote: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var note by rememberSaveable(model.sessionId) { mutableStateOf(model.note) }
    var feeling by rememberSaveable(model.sessionId) { mutableStateOf(model.feeling) }
    var moving by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var provenance by rememberSaveable { mutableStateOf(false) }
    var showTable by rememberSaveable { mutableStateOf(false) }
    var daysAgo by rememberSaveable { mutableStateOf(0) }
    OhScreen(modifier = modifier.windowInsetsPadding(WindowInsets.statusBars)) {
        OhPageHeader(title = stringResource(R.string.train_summary_title), onBack = onBack)
        Text(
            text = model.title,
            style = OhTheme.type.large.copy(fontWeight = OhWeight.bold),
            color = OhTheme.colors.label,
        )
        Text(
            text = app.openhealth.ui.screens.common.mediumDate(model.date),
            style = OhTheme.type.subhead,
            color = OhTheme.colors.label2,
        )
        Spacer(Modifier.height(OhTheme.space.s5))
        OhKpiBand(
            figures = listOf(
                OhKpiFigure(model.duration, stringResource(R.string.train_summary_duration)),
                OhKpiFigure(
                    model.volume ?: stringResource(R.string.train_not_available),
                    stringResource(R.string.train_summary_volume),
                ),
                OhKpiFigure(model.sets, stringResource(R.string.train_summary_sets)),
            ),
            footer = stringResource(R.string.train_summary_volume_note),
        )
        Spacer(Modifier.height(OhTheme.space.s3))
        OhInsetGroup {
            OhRow(
                title = stringResource(R.string.train_summary_provenance),
                value = model.sourceLabel,
                showChevron = true,
                onClick = { provenance = true },
            )
        }
        OhSectionTitle(stringResource(R.string.train_summary_prs))
        if (model.prs.isEmpty()) {
            Text(
                text = stringResource(R.string.train_summary_no_prs),
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
            )
        } else {
            OhInsetGroup {
                model.prs.forEachIndexed { index, pr ->
                    OhRow(title = personalRecordLabel(pr), showDivider = index > 0)
                }
            }
        }
        OhSectionTitle(stringResource(R.string.train_summary_muscles))
        OhCard {
            OhBodyMap(
                model = model.map,
                variant = BodyMapVariant.MINI,
                onShowTable = { showTable = true },
            )
            Spacer(Modifier.height(OhTheme.space.s3))
            val parts = mutableListOf<String>()
            for ((muscle, sets) in model.topMuscles) {
                parts += muscleLabel(muscle) + " " + number(sets, 1)
            }
            Text(
                text = parts.joinToString(", "),
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
            )
        }
        OhSectionTitle(stringResource(R.string.train_summary_feeling))
        OhTextField(
            value = feeling,
            onValueChange = { feeling = it },
            placeholder = stringResource(R.string.train_summary_feeling_hint),
            singleLine = false,
        )
        Spacer(Modifier.height(OhTheme.space.s1))
        OhTextField(
            value = note,
            onValueChange = { note = it },
            placeholder = stringResource(R.string.train_summary_note_hint),
            singleLine = false,
        )
        Spacer(Modifier.height(OhTheme.space.s3))
        OhButton(
            text = stringResource(R.string.train_summary_save),
            onClick = { onSaveNote(feeling, note) },
            variant = OhButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(OhTheme.space.s5))
        Column(verticalArrangement = Arrangement.spacedBy(OhTheme.space.s2)) {
            OhButton(
                text = stringResource(R.string.train_summary_edit),
                onClick = { onEdit(model.sessionId) },
                modifier = Modifier.fillMaxWidth(),
            )
            OhButton(
                text = stringResource(R.string.train_summary_move),
                onClick = { moving = true },
                variant = OhButtonVariant.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
            OhButton(
                text = stringResource(R.string.train_summary_delete),
                onClick = { deleting = true },
                variant = OhButtonVariant.Danger,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(OhTheme.space.s7))
    }

    OhModalSheet(
        visible = showTable,
        onDismiss = { showTable = false },
        title = stringResource(R.string.train_summary_muscles),
        cancelLabel = stringResource(R.string.train_close),
        onCancel = { showTable = false },
    ) {
        app.openhealth.ui.body.OhBodyMapTable(model.map)
    }
    OhModalSheet(
        visible = provenance,
        onDismiss = { provenance = false },
        title = stringResource(R.string.train_summary_provenance_title),
        cancelLabel = stringResource(R.string.train_close),
        onCancel = { provenance = false },
    ) {
        OhInsetGroup {
            OhRow(
                title = stringResource(R.string.train_summary_provenance),
                value = model.sourceLabel,
            )
            OhRow(
                title = stringResource(R.string.train_summary_local_day),
                value = model.localDay.toString(),
                showDivider = true,
            )
            OhRow(
                title = stringResource(R.string.train_summary_zone),
                value = model.zoneId,
                showDivider = true,
            )
            OhRow(
                title = stringResource(R.string.train_summary_recorded),
                value = app.openhealth.ui.screens.common.timeOf(
                    model.recordedAt,
                    kotlinx.datetime.TimeZone.currentSystemDefault(),
                ),
                showDivider = true,
            )
            model.device?.let {
                OhRow(
                    title = stringResource(R.string.train_summary_device),
                    value = it,
                    showDivider = true,
                )
            }
        }
    }
    OhModalSheet(
        visible = moving,
        onDismiss = { moving = false },
        title = stringResource(R.string.train_summary_move_title),
        primaryLabel = stringResource(R.string.train_summary_move_action),
        onPrimary = {
            moving = false
            onMove(model.sessionId, model.date.plus(DatePeriod(days = -daysAgo)))
        },
        cancelLabel = stringResource(R.string.train_editor_cancel),
        onCancel = { moving = false },
    ) {
        Text(
            text = shortDate(model.date.plus(DatePeriod(days = -daysAgo))),
            style = OhTheme.type.body,
            color = OhTheme.colors.label,
        )
        Spacer(Modifier.height(OhTheme.space.s3))
        OhStepper(
            value = daysAgo.toString(),
            onDecrement = { if (daysAgo > 0) daysAgo -= 1 },
            onIncrement = { if (daysAgo < MOVE_LIMIT_DAYS) daysAgo += 1 },
            unit = stringResource(R.string.train_past_days_ago),
            onValueChange = { value ->
                value.toIntOrNull()?.let {
                    daysAgo =
                        it.coerceIn(0, MOVE_LIMIT_DAYS)
                }
            },
        )
    }

    if (deleting) {
        OhDialog(
            visible = true,
            onDismiss = { deleting = false },
            title = stringResource(R.string.train_summary_delete),
            message = stringResource(R.string.train_summary_delete_body),
            primaryLabel = stringResource(R.string.train_summary_delete),
            onPrimary = {
                deleting = false
                onDelete(model.sessionId)
            },
            cancelLabel = stringResource(R.string.train_editor_cancel),
            onCancel = { deleting = false },
            destructive = true,
        )
    }
}

private const val MOVE_LIMIT_DAYS = 365
