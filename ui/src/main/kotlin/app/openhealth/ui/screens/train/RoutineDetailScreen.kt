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
import app.openhealth.ui.body.OhBodyMap
import app.openhealth.ui.body.OhBodyMapLegend
import app.openhealth.ui.body.OhBodyMapTable
import app.openhealth.ui.components.OhButton
import app.openhealth.ui.components.OhButtonVariant
import app.openhealth.ui.components.OhCard
import app.openhealth.ui.components.OhChip
import app.openhealth.ui.components.OhInsetGroup
import app.openhealth.ui.components.OhModalSheet
import app.openhealth.ui.components.OhRow
import app.openhealth.ui.screens.common.OhEmptyState
import app.openhealth.ui.screens.common.OhPageHeader
import app.openhealth.ui.screens.common.OhScreen
import app.openhealth.ui.screens.common.OhSectionTitle
import app.openhealth.ui.screens.common.number
import app.openhealth.ui.screens.common.shortDate
import app.openhealth.ui.theme.OhTheme

/** A routine's detail (`DESIGN.md` §14.2): its full map, its targets and Start. */
@Composable
fun RoutineDetailScreen(
    model: RoutineDetailModel?,
    onBack: () -> Unit,
    onStart: (String) -> Unit,
    onEdit: (String) -> Unit,
    onFavourite: (String, Boolean) -> Unit,
    onExercise: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showTable by rememberSaveable { mutableStateOf(false) }
    if (model == null) {
        OhScreen(modifier = modifier.windowInsetsPadding(WindowInsets.statusBars)) {
            OhPageHeader(title = stringResource(R.string.train_routines), onBack = onBack)
            OhEmptyState(
                title = stringResource(R.string.train_routines_empty_title),
                body = stringResource(R.string.train_routines_empty_body),
            )
        }
        return
    }
    OhScreen(modifier = modifier.windowInsetsPadding(WindowInsets.statusBars)) {
        OhPageHeader(title = model.name, onBack = onBack)
        OhCard {
            OhBodyMap(model = model.map, onShowTable = { showTable = true })
            Spacer(Modifier.height(OhTheme.space.s5))
            OhBodyMapLegend(model.map)
            Spacer(Modifier.height(OhTheme.space.s3))
            Text(
                text = if (model.schedule ==
                    app.openhealth.core.training.RoutineSchedule.ROTATION
                ) {
                    stringResource(
                        R.string.train_routine_sets_cycle,
                        number(model.weeklySets),
                    )
                } else {
                    stringResource(R.string.train_routine_sets_week, number(model.weeklySets))
                },
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
            )
            if (model.lastTrained != null) {
                Text(
                    text = stringResource(
                        R.string.train_routine_last_session,
                        shortDate(model.lastTrained),
                    ),
                    style = OhTheme.type.footnote,
                    color = OhTheme.colors.label2,
                )
            }
            Spacer(Modifier.height(OhTheme.space.s5))
            OhButton(
                text = stringResource(R.string.train_routine_start),
                onClick = { onStart(model.id) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OhSectionTitle(stringResource(R.string.train_routine_schedule))
        OhInsetGroup {
            OhRow(
                title = stringResource(R.string.train_routine_schedule),
                value = scheduleLabel(model.schedule, model.weekday, model.rotationIndex),
            )
            OhRow(
                title = stringResource(R.string.train_routine_progression),
                value = progressionPolicyLabel(
                    model.progressionKey,
                    app.openhealth.core.training.ProgressionInput.DEFAULT_INCREMENT_KG,
                ),
                showDivider = true,
            )
            if (model.folder != null) {
                OhRow(
                    title = stringResource(R.string.train_routine_folder),
                    value = model.folder,
                    showDivider = true,
                )
            }
            OhRow(
                title = stringResource(R.string.train_routine_favourite),
                showDivider = true,
                trailing = {
                    OhChip(
                        text = if (model.favourite) {
                            stringResource(R.string.train_routine_favourite)
                        } else {
                            stringResource(R.string.train_editor_favourite)
                        },
                        selected = model.favourite,
                        onClick = { onFavourite(model.id, !model.favourite) },
                    )
                },
            )
        }
        model.notes?.let { notes ->
            OhSectionTitle(stringResource(R.string.train_editor_notes))
            Text(
                text = notes,
                style = OhTheme.type.body,
                color = OhTheme.colors.label2,
            )
        }
        OhSectionTitle(stringResource(R.string.train_routine_exercises))
        if (model.exercises.isEmpty()) {
            OhCard {
                OhEmptyState(
                    title = stringResource(R.string.train_routine_no_exercises_title),
                    body = stringResource(R.string.train_routine_no_exercises_body),
                )
            }
        } else {
            OhInsetGroup {
                model.exercises.forEachIndexed { index, row ->
                    OhRow(
                        title = row.name,
                        subtitle = row.muscles,
                        value = row.target,
                        showDivider = index > 0,
                        showChevron = true,
                        onClick = { onExercise(row.exerciseId) },
                    )
                }
            }
        }
        Spacer(Modifier.height(OhTheme.space.s5))
        Column(verticalArrangement = Arrangement.spacedBy(OhTheme.space.s2)) {
            OhButton(
                text = stringResource(R.string.train_routine_edit),
                onClick = { onEdit(model.id) },
                variant = OhButtonVariant.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
            OhButton(
                text = stringResource(R.string.train_routine_add_exercise),
                onClick = { onEdit(model.id) },
                variant = OhButtonVariant.Ghost,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(OhTheme.space.s7))
    }
    OhModalSheet(
        visible = showTable,
        onDismiss = { showTable = false },
        title = stringResource(R.string.train_map_plan),
        cancelLabel = stringResource(R.string.train_close),
        onCancel = { showTable = false },
    ) {
        OhBodyMapTable(model.map)
    }
}
