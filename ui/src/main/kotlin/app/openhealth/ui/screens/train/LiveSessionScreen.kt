package app.openhealth.ui.screens.train

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import app.openhealth.core.training.BarType
import app.openhealth.core.training.PlateMath
import app.openhealth.core.training.clockText
import app.openhealth.ui.R
import app.openhealth.ui.components.OhButton
import app.openhealth.ui.components.OhButtonVariant
import app.openhealth.ui.components.OhCard
import app.openhealth.ui.components.OhChip
import app.openhealth.ui.components.OhIconButton
import app.openhealth.ui.components.OhModalSheet
import app.openhealth.ui.components.OhRow
import app.openhealth.ui.components.OhSearchField
import app.openhealth.ui.components.OhSegmentedControl
import app.openhealth.ui.components.OhSwitch
import app.openhealth.ui.components.OhTextField
import app.openhealth.ui.components.OhTimerBar
import app.openhealth.ui.components.OhToast
import app.openhealth.ui.components.ohClickable
import app.openhealth.ui.components.rememberInteraction
import app.openhealth.ui.icons.OhIcon
import app.openhealth.ui.icons.OhIcons
import app.openhealth.ui.screens.common.decimalText
import app.openhealth.ui.screens.common.number
import app.openhealth.ui.screens.common.shortDate
import app.openhealth.ui.theme.OhTheme
import app.openhealth.ui.theme.OhWeight
import app.openhealth.ui.theme.ltrFigures
import app.openhealth.ui.theme.tabular
import kotlinx.coroutines.delay

/**
 * The live session (`DESIGN.md` §14.2): a focused surface with no tab bar, the set table,
 * prefill from the last session, one tap to complete, and the rest timer docked at the
 * bottom. Every completed set is already saved; leaving the screen loses nothing.
 */
@Composable
fun LiveSessionScreen(
    model: LiveSessionModel,
    now: Long,
    onBack: () -> Unit,
    onFinish: (String) -> Unit,
    onLogSet: (LoggedSet) -> Unit,
    onUpdateSet: (setId: String, edit: LiveSetEdit) -> Unit,
    onDeleteSet: (setId: String) -> Unit,
    onAddExercise: (String) -> Unit,
    onExerciseNotes: (exerciseId: String, notes: String) -> Unit,
    onSessionNote: (String) -> Unit,
    onSuperset: (exerciseId: String, withNext: Boolean) -> Unit,
    onSaveEquipment: (app.openhealth.core.training.EquipmentProfile) -> Unit,
    onRestMinus: () -> Unit,
    onRestPlus: () -> Unit,
    onRestPause: () -> Unit,
    onRestSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var elapsed by remember {
        mutableStateOf(
            (now - model.startedAt).coerceAtLeast(0L) / MILLIS_PER_SECOND,
        )
    }
    LaunchedEffect(model.startedAt, now) {
        elapsed = ((now - model.startedAt).coerceAtLeast(0L)) / MILLIS_PER_SECOND
        while (true) {
            delay(TICK_MILLIS)
            elapsed += 1
        }
    }
    var showFinish by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var editingSet by rememberSaveable { mutableStateOf<String?>(null) }
    var notesFor by rememberSaveable { mutableStateOf<String?>(null) }
    var platesFor by rememberSaveable { mutableStateOf<String?>(null) }
    var equipmentOpen by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf(false) }
    var sessionNote by rememberSaveable(model.sessionId) { mutableStateOf(model.note) }
    var chosenPlate by remember { mutableStateOf<Pair<String, Double>?>(null) }
    val setBeingEdited = model.exercises.flatMap { exercise ->
        exercise.sets.map { set -> exercise to set }
    }.firstOrNull { it.second.id == editingSet && editingSet != null }
    val notesExercise = model.exercises.firstOrNull { it.id == notesFor }
    val plateExercise = model.exercises.firstOrNull { it.id == platesFor }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OhTheme.colors.bg)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        LiveHeader(
            title = model.title,
            elapsed = if (model.backdated) {
                stringResource(R.string.train_backdated) + " · " + shortDate(model.localDate)
            } else {
                clockText(elapsed)
            },
            onClose = onBack,
            onFinish = { showFinish = true },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OhTheme.space.s6),
        ) {
            if (model.exercises.isEmpty()) {
                Spacer(Modifier.height(OhTheme.space.s8))
                OhCard {
                    Text(
                        text = stringResource(R.string.train_live_empty_title),
                        style = OhTheme.type.body.copy(fontWeight = OhWeight.semibold),
                        color = OhTheme.colors.label,
                    )
                    Spacer(Modifier.height(OhTheme.space.s1))
                    Text(
                        text = stringResource(R.string.train_live_empty_body),
                        style = OhTheme.type.footnote,
                        color = OhTheme.colors.label2,
                    )
                    Spacer(Modifier.height(OhTheme.space.s5))
                    OhButton(
                        text = stringResource(R.string.train_live_add_exercise),
                        onClick = { adding = true },
                        variant = OhButtonVariant.Secondary,
                    )
                }
            }
            model.exercises.forEachIndexed { index, exercise ->
                if (exercise.groupIndex != null &&
                    (index == 0 || model.exercises[index - 1].groupIndex != exercise.groupIndex)
                ) {
                    Text(
                        text = if (model.exercises.count { it.groupIndex == exercise.groupIndex } >
                            2
                        ) {
                            stringResource(R.string.train_live_group_circuit)
                        } else {
                            stringResource(R.string.train_live_superset_label)
                        },
                        style = OhTheme.type.footnote.copy(fontWeight = OhWeight.semibold),
                        color = OhTheme.colors.accInk,
                        modifier = Modifier.padding(
                            top = OhTheme.space.s5,
                            bottom = OhTheme.space.s1,
                        ),
                    )
                }
                ExerciseBlock(
                    exercise = exercise,
                    next = model.exercises.getOrNull(index + 1),
                    chosenWeight = chosenPlate
                        ?.takeIf { it.first == exercise.exerciseId }
                        ?.second,
                    onLog = { logged ->
                        if (logged.weight.replace(',', '.').toDoubleOrNull() == null ||
                            logged.reps.toIntOrNull() == null
                        ) {
                            error = true
                        } else {
                            onLogSet(logged)
                        }
                    },
                    onEditSet = { editingSet = it },
                    onNotes = { notesFor = exercise.id },
                    onPlates = { platesFor = exercise.id },
                    onSuperset = {
                        onSuperset(exercise.id, exercise.groupIndex == null)
                    },
                )
            }
            Spacer(Modifier.height(OhTheme.space.s5))
            Row(horizontalArrangement = Arrangement.spacedBy(OhTheme.space.s2)) {
                OhButton(
                    text = stringResource(R.string.train_live_add_exercise),
                    onClick = { adding = true },
                    variant = OhButtonVariant.Secondary,
                )
            }
            Spacer(Modifier.height(OhTheme.space.s5))
            OhTextField(
                value = sessionNote,
                onValueChange = {
                    sessionNote = it
                    onSessionNote(it)
                },
                placeholder = stringResource(R.string.train_summary_note_hint),
                singleLine = false,
            )
            Spacer(Modifier.height(OhTheme.space.s8))
        }
        model.rest?.let { rest ->
            OhTimerBar(
                time = clockText(rest.remainingSeconds),
                progress = if (rest.totalSeconds > 0) {
                    rest.remainingSeconds.toFloat() / rest.totalSeconds
                } else {
                    0f
                },
                paused = rest.paused,
                onMinus = onRestMinus,
                onPlus = onRestPlus,
                onPauseToggle = onRestPause,
                onSkip = onRestSkip,
                modifier = Modifier
                    .padding(horizontal = OhTheme.space.s6)
                    .padding(bottom = OhTheme.space.s5)
                    .windowInsetsPadding(WindowInsets.navigationBars),
            )
        }
        if (model.rest == null) {
            Spacer(Modifier.height(OhTheme.space.s5))
        }
    }

    OhModalSheet(
        visible = showFinish,
        onDismiss = { showFinish = false },
        title = stringResource(R.string.train_live_finish_title),
        primaryLabel = stringResource(R.string.train_live_finish_action),
        onPrimary = {
            showFinish = false
            onFinish(model.sessionId)
        },
        cancelLabel = stringResource(R.string.train_editor_cancel),
        onCancel = { showFinish = false },
    ) {
        Text(
            text = stringResource(R.string.train_live_finish_body),
            style = OhTheme.type.body,
            color = OhTheme.colors.label2,
        )
    }

    OhModalSheet(
        visible = adding,
        onDismiss = { adding = false },
        title = stringResource(R.string.train_live_add_exercise),
    ) {
        AddExerciseSheet(
            library = model.library,
            onPick = { id ->
                adding = false
                onAddExercise(id)
            },
        )
    }

    OhModalSheet(
        visible = setBeingEdited != null,
        onDismiss = { editingSet = null },
        title = stringResource(R.string.train_live_edit_set),
    ) {
        val pair = setBeingEdited
        if (pair != null) {
            SetEditSheet(
                edit = LiveSetEdit(
                    weight = pair.second.weight,
                    reps = pair.second.reps,
                    rir = pair.second.rir,
                    kind = pair.second.kind,
                    notes = pair.second.notes.orEmpty(),
                    tempo = pair.second.tempo.orEmpty(),
                    isBodyweight = pair.second.isBodyweight,
                    isAssisted = pair.second.isAssisted,
                    assistanceKg = pair.second.assistanceKg,
                    bandKg = pair.second.bandKg,
                ),
                onSave = { edit ->
                    editingSet = null
                    pair.second.id?.let { onUpdateSet(it, edit) }
                },
                onDelete = pair.second.id?.let { id ->
                    {
                        editingSet = null
                        onDeleteSet(id)
                    }
                },
            )
        }
    }

    OhModalSheet(
        visible = notesExercise != null,
        onDismiss = { notesFor = null },
        title = notesExercise?.name ?: stringResource(R.string.train_live_notes),
    ) {
        val exercise = notesExercise
        if (exercise != null) {
            NoteSheet(
                initial = exercise.notes.orEmpty(),
                placeholder = stringResource(R.string.train_live_notes_hint),
                onSave = { note ->
                    notesFor = null
                    onExerciseNotes(exercise.exerciseId, note)
                },
            )
        }
    }

    OhModalSheet(
        visible = plateExercise != null,
        onDismiss = { platesFor = null },
        title = stringResource(R.string.train_plates_title),
    ) {
        val exercise = plateExercise
        if (exercise != null) {
            PlateSheet(
                initial = exercise.nextWeight,
                equipment = model.equipment,
                onEditEquipment = {
                    platesFor = null
                    equipmentOpen = true
                },
                onPlateChosen = { weight ->
                    platesFor = null
                    chosenPlate = exercise.exerciseId to weight
                },
            )
        }
    }

    OhModalSheet(
        visible = equipmentOpen,
        onDismiss = { equipmentOpen = false },
        title = stringResource(R.string.train_plates_equipment_title),
    ) {
        EquipmentSheet(
            equipment = model.equipment,
            onSave = { profile ->
                equipmentOpen = false
                onSaveEquipment(profile)
            },
        )
    }

    if (error) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = OhTheme.space.s8),
            contentAlignment = Alignment.BottomCenter,
        ) {
            OhToast(
                message = stringResource(R.string.train_live_unknown),
                onDismiss = { error = false },
            )
        }
    }
}

@Composable
private fun LiveHeader(title: String, elapsed: String, onClose: () -> Unit, onFinish: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OhTheme.space.s6)
            .height(OhTheme.dims.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OhIconButton(
            icon = OhIcons.close,
            contentDescription = stringResource(R.string.train_live_close),
            onClick = onClose,
        )
        Spacer(Modifier.width(OhTheme.space.s3))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = OhTheme.type.title3.copy(fontWeight = OhWeight.semibold),
                color = OhTheme.colors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = elapsed,
                style = OhTheme.type.footnote.tabular().ltrFigures(),
                color = OhTheme.colors.label2,
            )
        }
        OhButton(
            text = stringResource(R.string.train_live_finish),
            onClick = onFinish,
            variant = OhButtonVariant.Primary,
            size = app.openhealth.ui.components.OhButtonSize.Small,
        )
    }
}

@Composable
private fun ExerciseBlock(
    exercise: LiveExerciseModel,
    next: LiveExerciseModel?,
    chosenWeight: Double?,
    onLog: (LoggedSet) -> Unit,
    onEditSet: (String) -> Unit,
    onNotes: () -> Unit,
    onPlates: () -> Unit,
    onSuperset: () -> Unit,
) {
    var kind by rememberSaveable(exercise.id, exercise.sets.size) {
        mutableStateOf(LiveSetKind.WORK)
    }
    var weight by rememberSaveable(exercise.id, exercise.sets.size) {
        mutableStateOf(exercise.nextWeight)
    }
    var reps by rememberSaveable(exercise.id, exercise.sets.size) {
        mutableStateOf(exercise.nextReps)
    }
    var rir by rememberSaveable(exercise.id, exercise.sets.size) { mutableStateOf<String?>(null) }
    var showEffort by rememberSaveable { mutableStateOf(false) }
    var note by rememberSaveable(exercise.id) { mutableStateOf("") }
    LaunchedEffect(chosenWeight) {
        chosenWeight?.let { weight = decimalText(it, 2) }
    }

    OhCard(modifier = Modifier.padding(top = OhTheme.space.s5)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exercise.name,
                    style = OhTheme.type.title3.copy(fontWeight = OhWeight.semibold),
                    color = OhTheme.colors.label,
                )
                Text(
                    text = equipmentLabel(exercise.equipmentKey) + " · " + exercise.muscles,
                    style = OhTheme.type.footnote,
                    color = OhTheme.colors.label2,
                )
                Text(
                    text = exercise.target,
                    style = OhTheme.type.footnote,
                    color = OhTheme.colors.label2,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                exercise.lastTime?.let {
                    Text(
                        text = it,
                        style = OhTheme.type.caption,
                        color = OhTheme.colors.label2,
                    )
                }
                Text(
                    text = progressionPolicyLabel(exercise.progressionKey, exercise.incrementKg),
                    style = OhTheme.type.caption.copy(fontWeight = OhWeight.semibold),
                    color = OhTheme.colors.accInk,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(OhTheme.space.s1))
        Text(
            text = progressionReasonLabel(exercise.progressionReason),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
        Spacer(Modifier.height(OhTheme.space.s3))
        SetTableHeader()
        exercise.sets.forEach { set ->
            if (set.done) {
                DoneSetRow(set = set, onClick = { set.id?.let(onEditSet) })
            }
        }
        ActiveSetRow(
            label = exercise.sets.firstOrNull { !it.done }?.label ?: "1",
            weight = weight,
            reps = reps,
            rir = rir,
            onWeight = { weight = it },
            onReps = { reps = it },
            onEffort = { showEffort = true },
            onDone = {
                onLog(
                    LoggedSet(
                        exerciseId = exercise.exerciseId,
                        weight = weight,
                        reps = reps,
                        rir = rir,
                        kind = kind,
                        notes = note.ifBlank { null },
                    ),
                )
                kind = LiveSetKind.WORK
                rir = null
                note = ""
            },
        )
        Spacer(Modifier.height(OhTheme.space.s2))
        Row(
            horizontalArrangement = Arrangement.spacedBy(OhTheme.space.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(OhTheme.space.s2),
            ) {
                SetKindChips(selected = kind, onSelect = { kind = it })
            }
        }
        Spacer(Modifier.height(OhTheme.space.s2))
        Row(
            horizontalArrangement = Arrangement.spacedBy(OhTheme.space.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OhButton(
                text = stringResource(R.string.train_live_plate_math),
                onClick = onPlates,
                variant = OhButtonVariant.Ghost,
                size = app.openhealth.ui.components.OhButtonSize.ExtraSmall,
            )
            OhButton(
                text = stringResource(R.string.train_live_notes),
                onClick = onNotes,
                variant = OhButtonVariant.Ghost,
                size = app.openhealth.ui.components.OhButtonSize.ExtraSmall,
            )
            if (next != null &&
                (exercise.groupIndex == null || next.groupIndex != exercise.groupIndex)
            ) {
                OhButton(
                    text = if (exercise.groupIndex == null) {
                        stringResource(R.string.train_live_superset)
                    } else {
                        stringResource(R.string.train_live_superset_off)
                    },
                    onClick = onSuperset,
                    variant = OhButtonVariant.Ghost,
                    size = app.openhealth.ui.components.OhButtonSize.ExtraSmall,
                )
            }
        }
        exercise.notes?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(OhTheme.space.s2))
            Text(text = it, style = OhTheme.type.footnote, color = OhTheme.colors.label2)
        }
    }

    if (showEffort) {
        OhModalSheet(
            visible = true,
            onDismiss = { showEffort = false },
            title = stringResource(R.string.train_effort_title),
        ) {
            EffortSheet(
                onSelect = {
                    rir = it
                    showEffort = false
                },
            )
        }
    }
}

@Composable
private fun SetTableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = OhTheme.space.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(OhTheme.dims.setColumn))
        HeaderCell(stringResource(R.string.train_live_weight), Modifier.weight(1f))
        HeaderCell(stringResource(R.string.train_live_reps), Modifier.weight(1f))
        HeaderCell(stringResource(R.string.train_live_rir), Modifier.width(OhTheme.dims.setRir))
        Spacer(Modifier.width(OhTheme.dims.touchTarget))
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier) {
    Text(
        text = text,
        style = OhTheme.type.caption,
        color = OhTheme.colors.label2,
        textAlign = TextAlign.Center,
        modifier = modifier,
        maxLines = 1,
    )
}

@Composable
private fun DoneSetRow(set: LiveSetRow, onClick: () -> Unit) {
    val large = LocalDensity.current.fontScale >= OhTheme.dims.largeFontScale
    val setWidth = if (large) OhTheme.dims.touchTarget else OhTheme.dims.setColumn
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = OhTheme.dims.touchTarget)
            .ohClickable(rememberInteraction(), onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = set.label,
            style = OhTheme.type.subhead,
            color = OhTheme.colors.label2,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(setWidth),
        )
        Text(
            text = set.weight,
            style = OhTheme.type.body.tabular().ltrFigures(),
            color = OhTheme.colors.label,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = set.reps,
            style = OhTheme.type.body.tabular().ltrFigures(),
            color = OhTheme.colors.label,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = set.rir.orEmpty(),
            style = OhTheme.type.subhead.tabular().ltrFigures(),
            color = OhTheme.colors.label2,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(OhTheme.dims.setRir),
        )
        Box(
            modifier = Modifier
                .width(OhTheme.dims.touchTarget)
                .height(OhTheme.dims.touchTarget),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(OhTheme.dims.setDone)
                    .clip(CircleShape)
                    .background(OhTheme.colors.acc),
                contentAlignment = Alignment.Center,
            ) {
                OhIcon(
                    icon = OhIcons.check,
                    contentDescription = null,
                    size = OhTheme.dims.glyphInline,
                    tint = OhTheme.colors.onAcc,
                )
            }
        }
    }
    if (set.prKinds.isNotEmpty()) {
        Row(
            modifier = Modifier.padding(start = setWidth, bottom = OhTheme.space.s1),
            horizontalArrangement = Arrangement.spacedBy(OhTheme.space.s1),
        ) {
            set.prKinds.forEach { kind ->
                Text(
                    text = personalRecordLabel(kind),
                    style = OhTheme.type.caption.copy(fontWeight = OhWeight.semibold),
                    color = OhTheme.colors.goldInk,
                )
            }
        }
    }
}

@Composable
private fun ActiveSetRow(
    label: String,
    weight: String,
    reps: String,
    rir: String?,
    onWeight: (String) -> Unit,
    onReps: (String) -> Unit,
    onEffort: () -> Unit,
    onDone: () -> Unit,
) {
    val addSetLabel = stringResource(R.string.train_live_current_set)
    val large = LocalDensity.current.fontScale >= OhTheme.dims.largeFontScale
    val setWidth = if (large) OhTheme.dims.touchTarget else OhTheme.dims.setColumn
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = OhTheme.dims.touchTarget)
            .clip(RoundedCornerShape(OhTheme.radius.sm))
            .background(OhTheme.colors.surface2)
            .semantics { contentDescription = addSetLabel },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(OhTheme.dims.setActiveEdge)
                .height(OhTheme.dims.touchTarget)
                .background(OhTheme.colors.acc),
        )
        Text(
            text = label,
            style = OhTheme.type.subhead,
            color = OhTheme.colors.label2,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(setWidth - OhTheme.dims.setActiveEdge),
        )
        SetField(
            value = weight,
            onValueChange = onWeight,
            label = stringResource(R.string.train_live_weight),
            modifier = Modifier.weight(1f),
        )
        SetField(
            value = reps,
            onValueChange = onReps,
            label = stringResource(R.string.train_live_reps),
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .width(OhTheme.dims.setRir)
                .height(OhTheme.dims.touchTarget)
                .ohClickable(
                    rememberInteraction(),
                    role = Role.Button,
                    onClick = onEffort,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = rir ?: stringResource(R.string.train_live_rir),
                style = OhTheme.type.subhead.tabular().ltrFigures(),
                color = if (rir == null) OhTheme.colors.label3 else OhTheme.colors.accInk,
            )
        }
        val doneLabel = stringResource(R.string.train_live_done)
        Box(
            modifier = Modifier
                .width(OhTheme.dims.touchTarget)
                .height(OhTheme.dims.touchTarget)
                .semantics { contentDescription = doneLabel }
                .ohClickable(
                    rememberInteraction(),
                    role = Role.Button,
                    onClick = onDone,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(OhTheme.dims.setDone)
                    .clip(CircleShape)
                    .border(
                        OhTheme.dims.setActiveEdge,
                        OhTheme.colors.acc,
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                OhIcon(
                    icon = OhIcons.check,
                    contentDescription = null,
                    size = OhTheme.dims.glyphInline,
                    tint = OhTheme.colors.accInk,
                )
            }
        }
    }
}

@Composable
private fun SetField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier,
) {
    var field by remember { mutableStateOf(TextFieldValue(value)) }
    LaunchedEffect(value) {
        if (field.text != value) field = TextFieldValue(value)
    }
    Box(
        modifier = modifier
            .padding(horizontal = OhTheme.space.s1)
            .height(OhTheme.dims.touchTarget)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        BasicTextField(
            value = field,
            onValueChange = {
                field = it
                onValueChange(it.text)
            },
            singleLine = true,
            textStyle = OhTheme.type.body.copy(
                fontWeight = OhWeight.semibold,
                color = OhTheme.colors.label,
            ).tabular().copy(textAlign = TextAlign.Center),
            cursorBrush = SolidColor(OhTheme.colors.accInk),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged {
                    if (it.isFocused) {
                        field = field.copy(selection = TextRange(0, field.text.length))
                    }
                },
        )
    }
}

@Composable
private fun SetKindChips(selected: LiveSetKind, onSelect: (LiveSetKind) -> Unit) {
    val options = listOf(
        LiveSetKind.WORK to stringResource(R.string.train_live_add_set),
        LiveSetKind.WARMUP to stringResource(R.string.train_live_add_warmup),
        LiveSetKind.BACKOFF to stringResource(R.string.train_live_add_backoff),
        LiveSetKind.DROP to stringResource(R.string.train_live_add_drop),
        LiveSetKind.FAILURE to stringResource(R.string.train_live_failure),
    )
    options.forEach { (kind, label) ->
        OhChip(
            text = label,
            selected = selected == kind,
            neutralSelected = true,
            onClick = { onSelect(kind) },
        )
    }
}

@Composable
private fun EffortSheet(onSelect: (String?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(OhTheme.space.s2)) {
        listOf(
            "0" to R.string.train_effort_zero,
            "1" to R.string.train_effort_one,
            "2" to R.string.train_effort_two,
            "3" to R.string.train_effort_three,
            "4" to R.string.train_effort_four,
            "5" to R.string.train_effort_five,
        ).forEach { (value, label) ->
            OhRow(
                title = stringResource(label),
                value = stringResource(R.string.train_live_rir),
                showChevron = true,
                onClick = { onSelect(value) },
            )
        }
        OhRow(
            title = stringResource(R.string.train_effort_none),
            showChevron = true,
            onClick = { onSelect(null) },
        )
    }
}

@Composable
private fun AddExerciseSheet(library: List<ExerciseChoice>, onPick: (String) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    val results = remember(search) {
        library.filter { it.name.contains(search, ignoreCase = true) }.take(SEARCH_LIMIT)
    }
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
            onClick = { onPick(choice.id) },
        )
    }
}

@Composable
private fun SetEditSheet(
    edit: LiveSetEdit,
    onSave: (LiveSetEdit) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var weight by rememberSaveable { mutableStateOf(edit.weight) }
    var reps by rememberSaveable { mutableStateOf(edit.reps) }
    var rir by rememberSaveable { mutableStateOf(edit.rir) }
    var kind by rememberSaveable { mutableStateOf(edit.kind) }
    var notes by rememberSaveable { mutableStateOf(edit.notes) }
    var tempo by rememberSaveable { mutableStateOf(edit.tempo) }
    var bodyweight by rememberSaveable { mutableStateOf(edit.isBodyweight) }
    var assisted by rememberSaveable { mutableStateOf(edit.isAssisted) }
    var assistance by rememberSaveable { mutableStateOf(edit.assistanceKg) }
    var band by rememberSaveable { mutableStateOf(edit.bandKg) }
    Column(verticalArrangement = Arrangement.spacedBy(OhTheme.space.s2)) {
        OhTextField(weight, {
            weight = it
        }, placeholder = stringResource(R.string.train_live_weight))
        OhTextField(reps, { reps = it }, placeholder = stringResource(R.string.train_live_reps))
        OhRow(
            title = stringResource(R.string.train_live_rir),
            value = rir ?: stringResource(R.string.train_effort_none),
            showChevron = true,
            onClick = { rir = nextEffort(rir) },
        )
        OhSegmentedControl(
            options = listOf(
                stringResource(R.string.train_live_add_set),
                stringResource(R.string.train_live_add_warmup),
                stringResource(R.string.train_live_add_backoff),
                stringResource(R.string.train_live_add_drop),
                stringResource(R.string.train_live_failure),
            ),
            selectedIndex = SET_KIND_ORDER.indexOf(kind).coerceAtLeast(0),
            onSelect = { kind = SET_KIND_ORDER[it] },
        )
        OhRow(
            title = stringResource(R.string.train_live_bodyweight),
            trailing = { OhSwitch(checked = bodyweight, onCheckedChange = { bodyweight = it }) },
        )
        OhRow(
            title = stringResource(R.string.train_live_assisted),
            trailing = { OhSwitch(checked = assisted, onCheckedChange = { assisted = it }) },
        )
        if (assisted) {
            OhTextField(
                assistance,
                { assistance = it },
                placeholder = stringResource(R.string.train_live_assistance),
            )
        }
        OhTextField(band, { band = it }, placeholder = stringResource(R.string.train_live_band))
        OhTextField(tempo, { tempo = it }, placeholder = stringResource(R.string.train_live_tempo))
        OhTextField(notes, {
            notes = it
        }, placeholder = stringResource(R.string.train_live_set_notes))
        Spacer(Modifier.height(OhTheme.space.s3))
        OhButton(
            text = stringResource(R.string.train_editor_done),
            onClick = {
                onSave(
                    LiveSetEdit(
                        weight = weight,
                        reps = reps,
                        rir = rir,
                        kind = kind,
                        notes = notes,
                        tempo = tempo,
                        isBodyweight = bodyweight,
                        isAssisted = assisted,
                        assistanceKg = assistance,
                        bandKg = band,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (onDelete != null) {
            OhButton(
                text = stringResource(R.string.train_live_delete_set),
                onClick = onDelete,
                variant = OhButtonVariant.Danger,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private val SET_KIND_ORDER = listOf(
    LiveSetKind.WORK,
    LiveSetKind.WARMUP,
    LiveSetKind.BACKOFF,
    LiveSetKind.DROP,
    LiveSetKind.FAILURE,
)

private fun nextEffort(current: String?): String? = when (current) {
    null -> "0"
    "0" -> "1"
    "1" -> "2"
    "2" -> "3"
    "3" -> "4"
    "4" -> "5"
    else -> null
}

@Composable
private fun NoteSheet(initial: String, placeholder: String, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    OhTextField(
        value = text,
        onValueChange = { text = it },
        placeholder = placeholder,
        singleLine = false,
    )
    Spacer(Modifier.height(OhTheme.space.s5))
    OhButton(
        text = stringResource(R.string.train_editor_done),
        onClick = { onSave(text) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PlateSheet(
    initial: String,
    equipment: app.openhealth.core.training.EquipmentProfile,
    onEditEquipment: () -> Unit,
    onPlateChosen: (Double) -> Unit,
) {
    var target by rememberSaveable { mutableStateOf(initial) }
    var barKey by rememberSaveable { mutableStateOf(equipment.defaultBarKey) }
    val bar = equipment.bar(barKey)
    val solution = target.replace(',', '.').toDoubleOrNull()?.let { value ->
        PlateMath.solve(value, bar.weightKg, equipment.platesKg)
    }
    OhTextField(
        value = target,
        onValueChange = { target = it },
        placeholder = stringResource(R.string.train_plates_target),
    )
    Spacer(Modifier.height(OhTheme.space.s3))
    Text(
        text = stringResource(R.string.train_plates_bar),
        style = OhTheme.type.footnote,
        color = OhTheme.colors.label2,
    )
    Spacer(Modifier.height(OhTheme.space.s1))
    Row(horizontalArrangement = Arrangement.spacedBy(OhTheme.space.s1)) {
        equipment.bars.forEach { option ->
            OhChip(
                text = option.label,
                selected = barKey == option.key,
                neutralSelected = true,
                onClick = { barKey = option.key },
            )
        }
    }
    Spacer(Modifier.height(OhTheme.space.s5))
    if (solution == null) {
        Text(
            text = stringResource(R.string.train_plates_none),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
    } else {
        Text(
            text = stringResource(R.string.train_plates_per_side) + " · " +
                solution.perSideKg.joinToString(" + ") { decimalText(it, 2) } + " kg",
            style = OhTheme.type.body,
            color = OhTheme.colors.label,
        )
        Text(
            text = if (solution.exact) {
                stringResource(R.string.train_plates_achieved, number(solution.achievedKg, 2))
            } else {
                stringResource(
                    R.string.train_plates_gap,
                    number(solution.achievedKg, 2),
                    number(solution.perSideGapKg, 2),
                )
            },
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
        Spacer(Modifier.height(OhTheme.space.s5))
        OhButton(
            text = stringResource(R.string.train_plates_use, number(solution.achievedKg, 2)),
            onClick = { onPlateChosen(solution.achievedKg) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(Modifier.height(OhTheme.space.s3))
    OhButton(
        text = stringResource(R.string.train_plates_edit),
        onClick = onEditEquipment,
        variant = OhButtonVariant.Ghost,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun EquipmentSheet(
    equipment: app.openhealth.core.training.EquipmentProfile,
    onSave: (app.openhealth.core.training.EquipmentProfile) -> Unit,
) {
    var plates by rememberSaveable {
        mutableStateOf(equipment.platesKg.joinToString(", ") { decimalText(it, 2) })
    }
    var dumbbell by rememberSaveable {
        mutableStateOf(decimalText(equipment.dumbbellIncrementKg, 2))
    }
    val customLabel = stringResource(R.string.train_plates_custom)
    var customBar by rememberSaveable {
        mutableStateOf(
            equipment.bars.firstOrNull { it.key == "custom" }?.weightKg?.let { decimalText(it, 2) }
                .orEmpty(),
        )
    }
    OhTextField(
        value = plates,
        onValueChange = { plates = it },
        placeholder = stringResource(R.string.train_plates_plates),
    )
    Spacer(Modifier.height(OhTheme.space.s2))
    OhTextField(
        value = dumbbell,
        onValueChange = { dumbbell = it },
        placeholder = stringResource(R.string.equipment_dumbbells),
    )
    Spacer(Modifier.height(OhTheme.space.s2))
    OhTextField(
        value = customBar,
        onValueChange = { customBar = it },
        placeholder = stringResource(R.string.train_plates_custom_weight),
    )
    Spacer(Modifier.height(OhTheme.space.s2))
    OhButton(
        text = stringResource(R.string.train_plates_save),
        onClick = {
            val parsed = plates.split(',', ' ')
                .mapNotNull { it.replace(',', '.').toDoubleOrNull() }
                .filter { it > 0.0 }
            val bars = equipment.bars.filterNot { it.key == "custom" } + listOfNotNull(
                customBar.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }?.let {
                    BarType("custom", customLabel, it)
                },
            )
            if (parsed.isNotEmpty()) {
                onSave(
                    equipment.copy(
                        platesKg = parsed.sortedDescending(),
                        dumbbellIncrementKg = dumbbell.replace(',', '.').toDoubleOrNull()
                            ?.takeIf { it > 0.0 } ?: equipment.dumbbellIncrementKg,
                        bars = bars,
                    ),
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

private const val TICK_MILLIS = 1_000L
private const val MILLIS_PER_SECOND = 1_000L
private const val SEARCH_LIMIT = 40
