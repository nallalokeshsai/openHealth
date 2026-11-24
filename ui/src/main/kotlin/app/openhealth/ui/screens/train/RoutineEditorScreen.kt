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
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.openhealth.core.training.RoutineSchedule
import app.openhealth.ui.R
import app.openhealth.ui.components.OhButton
import app.openhealth.ui.components.OhButtonVariant
import app.openhealth.ui.components.OhCard
import app.openhealth.ui.components.OhChip
import app.openhealth.ui.components.OhInsetGroup
import app.openhealth.ui.components.OhModalSheet
import app.openhealth.ui.components.OhRow
import app.openhealth.ui.components.OhSearchField
import app.openhealth.ui.components.OhSegmentedControl
import app.openhealth.ui.components.OhStepper
import app.openhealth.ui.components.OhSwitch
import app.openhealth.ui.components.OhTextField
import app.openhealth.ui.screens.common.OhPageHeader
import app.openhealth.ui.screens.common.OhScreen
import app.openhealth.ui.screens.common.OhSectionTitle
import app.openhealth.ui.theme.OhTheme

/**
 * The routine editor: the MVP's routine surface (`pr.md` §6.5). It is a pushed page in the
 * sheet grammar; the draft is local until Save, and a routine is never saved empty.
 */
@Composable
fun RoutineEditorScreen(
    model: RoutineEditorModel,
    onBack: () -> Unit,
    onSave: (RoutineDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by rememberSaveable(model.id) { mutableStateOf(model.name) }
    var folder by rememberSaveable(model.id) { mutableStateOf(model.folder) }
    var schedule by rememberSaveable(model.id) { mutableStateOf(model.schedule) }
    var weekday by rememberSaveable(model.id) { mutableStateOf(model.weekday) }
    var rotation by rememberSaveable(model.id) { mutableStateOf(model.rotationIndex) }
    var favourite by rememberSaveable(model.id) { mutableStateOf(model.favourite) }
    var progression by rememberSaveable(model.id) { mutableStateOf(model.progressionKey) }
    var notes by rememberSaveable(model.id) { mutableStateOf(model.notes) }
    var exercises by rememberSaveable(stateSaver = exerciseListSaver) {
        mutableStateOf(model.exercises)
    }
    var adding by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var issue by rememberSaveable { mutableStateOf<Int?>(null) }
    val editingRow = exercises.firstOrNull { it.id == editing }
    val canSave = name.isNotBlank() && exercises.isNotEmpty()

    OhScreen(modifier = modifier.windowInsetsPadding(WindowInsets.statusBars)) {
        OhPageHeader(
            title = stringResource(
                if (model.id == null) R.string.train_editor_new else R.string.train_editor_edit,
            ),
            onBack = onBack,
        )
        OhTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = stringResource(R.string.train_editor_name_hint),
            contentDescription = stringResource(R.string.train_editor_name),
        )
        Spacer(Modifier.height(OhTheme.space.s2))
        OhTextField(
            value = folder,
            onValueChange = { folder = it },
            placeholder = stringResource(R.string.train_editor_folder_hint),
            contentDescription = stringResource(R.string.train_editor_folder),
        )
        OhSectionTitle(stringResource(R.string.train_editor_schedule))
        OhSegmentedControl(
            options = listOf(
                stringResource(R.string.train_editor_weekday),
                stringResource(R.string.train_editor_rotation),
            ),
            selectedIndex = if (schedule == RoutineSchedule.WEEKDAY) 0 else 1,
            onSelect = {
                schedule = if (it == 0) RoutineSchedule.WEEKDAY else RoutineSchedule.ROTATION
            },
        )
        Spacer(Modifier.height(OhTheme.space.s3))
        if (schedule == RoutineSchedule.WEEKDAY) {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(OhTheme.space.s1),
            ) {
                WEEKDAYS.forEach { day ->
                    OhChip(
                        text = weekdayLabel(day),
                        selected = weekday == day,
                        neutralSelected = true,
                        onClick = { weekday = day },
                    )
                }
            }
        } else {
            OhStepper(
                value = rotation.toString(),
                onDecrement = { if (rotation > 1) rotation -= 1 },
                onIncrement = { if (rotation < MAX_ROTATION) rotation += 1 },
                unit = stringResource(R.string.train_editor_rotation_position),
                onValueChange = { value ->
                    value.toIntOrNull()?.let { rotation = it.coerceIn(1, MAX_ROTATION) }
                },
            )
        }
        OhRow(
            title = stringResource(R.string.train_editor_favourite),
            trailing = { OhSwitch(checked = favourite, onCheckedChange = { favourite = it }) },
        )
        OhSectionTitle(stringResource(R.string.train_editor_progression))
        OhSegmentedControl(
            options = listOf(
                stringResource(R.string.train_editor_progression_linear),
                stringResource(R.string.train_editor_progression_double),
                stringResource(R.string.train_editor_progression_off),
            ),
            selectedIndex = PROGRESSION_KEYS.indexOf(progression).coerceAtLeast(0),
            onSelect = { progression = PROGRESSION_KEYS[it] },
        )
        OhSectionTitle(stringResource(R.string.train_routine_exercises))
        if (exercises.isEmpty()) {
            OhCard {
                Text(
                    text = stringResource(R.string.train_routine_no_exercises_body),
                    style = OhTheme.type.footnote,
                    color = OhTheme.colors.label2,
                )
            }
        } else {
            OhInsetGroup {
                exercises.forEachIndexed { index, row ->
                    OhRow(
                        title = row.name,
                        subtitle = row.muscles,
                        value = "${row.sets} × ${row.reps}",
                        showDivider = index > 0,
                        showChevron = true,
                        onClick = { editing = row.id },
                    )
                }
            }
        }
        Spacer(Modifier.height(OhTheme.space.s3))
        OhButton(
            text = stringResource(R.string.train_editor_add),
            onClick = { adding = true },
            variant = OhButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
        OhSectionTitle(stringResource(R.string.train_editor_notes))
        OhTextField(
            value = notes,
            onValueChange = { notes = it },
            placeholder = stringResource(R.string.train_summary_note_hint),
            singleLine = false,
        )
        Spacer(Modifier.height(OhTheme.space.s5))
        OhButton(
            text = stringResource(R.string.train_editor_save),
            onClick = {
                issue = when {
                    name.isBlank() -> R.string.train_editor_invalid_name
                    exercises.isEmpty() -> R.string.train_editor_invalid_exercises
                    else -> null
                }
                if (issue == null) {
                    onSave(
                        RoutineDraft(
                            id = model.id,
                            name = name.trim(),
                            folder = folder.trim().ifBlank { null },
                            schedule = schedule,
                            weekday = weekday,
                            rotationIndex = rotation,
                            favourite = favourite,
                            progressionKey = progression,
                            notes = notes.trim().ifBlank { null },
                            exercises = exercises,
                        ),
                    )
                }
            },
            enabled = canSave,
            modifier = Modifier.fillMaxWidth(),
        )
        issue?.let {
            Spacer(Modifier.height(OhTheme.space.s2))
            Text(
                text = stringResource(it),
                style = OhTheme.type.footnote,
                color = OhTheme.colors.redInk,
            )
        }
        Spacer(Modifier.height(OhTheme.space.s7))
    }

    OhModalSheet(
        visible = adding,
        onDismiss = { adding = false },
        title = stringResource(R.string.train_editor_add),
    ) {
        AddExercisePicker(
            library = model.library,
            onPick = { choice ->
                adding = false
                exercises = exercises + EditorExerciseRow(
                    id = "new-${choice.id}-${exercises.size}",
                    exerciseId = choice.id,
                    name = choice.name,
                    muscles = choice.muscles,
                    sets = "3",
                    reps = "8-12",
                    weight = "",
                    rest = "",
                )
            },
        )
    }

    OhModalSheet(
        visible = editingRow != null,
        onDismiss = { editing = null },
        title = editingRow?.name ?: stringResource(R.string.train_editor_exercise_target),
    ) {
        val row = editingRow
        if (row != null) {
            ExerciseTargetSheet(
                row = row,
                onSave = { updated ->
                    exercises = exercises.map { if (it.id == updated.id) updated else it }
                    editing = null
                },
                onRemove = {
                    exercises = exercises.filterNot { it.id == row.id }
                    editing = null
                },
            )
        }
    }
}

@Composable
private fun AddExercisePicker(library: List<ExerciseChoice>, onPick: (ExerciseChoice) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    val results = library.filter { it.name.contains(search, ignoreCase = true) }.take(SEARCH_LIMIT)
    OhSearchField(
        value = search,
        onValueChange = { search = it },
        placeholder = stringResource(R.string.train_editor_search),
    )
    Spacer(Modifier.height(OhTheme.space.s3))
    if (results.isEmpty()) {
        Text(
            text = stringResource(R.string.train_editor_no_results),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
    }
    results.forEach { choice ->
        OhRow(
            title = choice.name,
            subtitle = choice.muscles,
            showChevron = true,
            onClick = { onPick(choice) },
        )
    }
}

@Composable
private fun ExerciseTargetSheet(
    row: EditorExerciseRow,
    onSave: (EditorExerciseRow) -> Unit,
    onRemove: () -> Unit,
) {
    var sets by rememberSaveable(row.id) { mutableStateOf(row.sets) }
    var reps by rememberSaveable(row.id) { mutableStateOf(row.reps) }
    var weight by rememberSaveable(row.id) { mutableStateOf(row.weight) }
    var rest by rememberSaveable(row.id) { mutableStateOf(row.rest) }
    Column(verticalArrangement = Arrangement.spacedBy(OhTheme.space.s2)) {
        OhTextField(
            value = sets,
            onValueChange = { sets = it },
            placeholder = stringResource(R.string.train_editor_sets),
        )
        OhTextField(
            value = reps,
            onValueChange = { reps = it },
            placeholder = stringResource(R.string.train_editor_reps_hint),
        )
        OhTextField(
            value = weight,
            onValueChange = { weight = it },
            placeholder = stringResource(R.string.train_editor_weight),
        )
        OhTextField(
            value = rest,
            onValueChange = { rest = it },
            placeholder = stringResource(R.string.train_editor_rest),
        )
        Spacer(Modifier.height(OhTheme.space.s3))
        OhButton(
            text = stringResource(R.string.train_editor_done),
            onClick = {
                onSave(row.copy(sets = sets, reps = reps, weight = weight, rest = rest))
            },
            modifier = Modifier.fillMaxWidth(),
        )
        OhButton(
            text = stringResource(R.string.train_editor_remove),
            onClick = onRemove,
            variant = OhButtonVariant.Danger,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private val exerciseListSaver = listSaver<List<EditorExerciseRow>, String>(
    save = { rows ->
        rows.flatMap { row ->
            listOf(
                row.id,
                row.exerciseId,
                row.name,
                row.muscles,
                row.sets,
                row.reps,
                row.weight,
                row.rest,
            )
        }
    },
    restore = { flat ->
        flat.chunked(EXERCISE_FIELDS).map { fields ->
            EditorExerciseRow(
                id = fields[0],
                exerciseId = fields[1],
                name = fields[2],
                muscles = fields[3],
                sets = fields[4],
                reps = fields[5],
                weight = fields[6],
                rest = fields[7],
            )
        }
    },
)

@Suppress("MagicNumber")
private val WEEKDAYS = 1..7
private const val MAX_ROTATION = 26
private const val SEARCH_LIMIT = 40
private const val EXERCISE_FIELDS = 8
