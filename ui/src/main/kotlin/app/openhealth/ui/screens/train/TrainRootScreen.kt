package app.openhealth.ui.screens.train

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import app.openhealth.core.body.Muscle
import app.openhealth.ui.R
import app.openhealth.ui.body.BodyMapModel
import app.openhealth.ui.body.OhBodyMap
import app.openhealth.ui.body.OhBodyMapLegend
import app.openhealth.ui.body.OhBodyMapTable
import app.openhealth.ui.body.muscleLabel
import app.openhealth.ui.components.OhButton
import app.openhealth.ui.components.OhCard
import app.openhealth.ui.components.OhCardHeader
import app.openhealth.ui.components.OhMediaRow
import app.openhealth.ui.components.OhModalSheet
import app.openhealth.ui.components.OhRow
import app.openhealth.ui.components.OhSegmentedControl
import app.openhealth.ui.components.OhStepper
import app.openhealth.ui.icons.OhIcons
import app.openhealth.ui.icons.OhModuleIcons
import app.openhealth.ui.screens.common.OhEmptyState
import app.openhealth.ui.screens.common.OhScreen
import app.openhealth.ui.screens.common.OhSectionTitle
import app.openhealth.ui.screens.common.number
import app.openhealth.ui.screens.common.shortDate
import app.openhealth.ui.theme.OhTheme
import app.openhealth.ui.theme.OhWeight
import app.openhealth.ui.theme.ltrFigures
import app.openhealth.ui.theme.tabular
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus

/**
 * The Train root (`DESIGN.md` §14.2): the week strip, today's plan with Start, the
 * Recovery/Plan segmented map pair, the routines with their mini maps and the history.
 * Plan maps and recovery maps never share colours (`pr.md` §6.5).
 */
@Composable
fun TrainRootScreen(
    model: TrainModel,
    onStartPlan: (String) -> Unit,
    onStartFreestyle: () -> Unit,
    onStartPast: (LocalDate) -> Unit,
    onResume: (String) -> Unit,
    onOpenRoutine: (String) -> Unit,
    onNewRoutine: () -> Unit,
    onOpenSession: (String) -> Unit,
    onOpenSettings: () -> Unit,
    moduleOff: Boolean = false,
    modifier: Modifier = Modifier,
    movement: app.openhealth.ui.screens.activity.MovementModel? = null,
    onMovement: (String) -> Unit = {},
    strengthOff: Boolean = false,
) {
    if (moduleOff) {
        app.openhealth.ui.screens.roots.ModuleOffRoot(
            name = stringResource(R.string.nav_train),
            onOpenSettings = onOpenSettings,
            modifier = modifier,
        )
        return
    }
    var segment by rememberSaveable { mutableStateOf(0) }
    var showTable by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var pastOpen by rememberSaveable { mutableStateOf(false) }
    var pastDays by rememberSaveable { mutableStateOf(1) }
    val shown = if (segment == 0) model.recovery else model.planMap
    OhScreen(modifier = modifier) {
        Text(
            text = stringResource(R.string.nav_train),
            style = OhTheme.type.large.copy(fontWeight = OhWeight.bold),
            color = OhTheme.colors.label,
            modifier = Modifier
                .padding(top = OhTheme.space.s6)
                .semantics { heading() },
        )
        Spacer(Modifier.height(OhTheme.space.s5))
        if (!strengthOff) {
            WeekStrip(model)
            Spacer(Modifier.height(OhTheme.space.s5))
            model.openSession?.let { open ->
                OpenSessionCard(open, onResume)
                Spacer(Modifier.height(OhTheme.space.s5))
            }
            PlanCard(model, onStartPlan, onStartFreestyle, onPast = { pastOpen = true })
        }
        if (movement != null) {
            Spacer(Modifier.height(OhTheme.space.s5))
            OhCard {
                OhCardHeader(
                    eyebrow = stringResource(R.string.movement_title),
                    title = stringResource(R.string.movement_title),
                    subtitle = movement.note,
                    glyph = app.openhealth.ui.icons.OhModuleIcons.activity,
                )
                Spacer(Modifier.height(OhTheme.space.s2))
                movement.rows.forEachIndexed { index, row ->
                    OhRow(
                        title = row.title,
                        subtitle = row.subtitle,
                        showDivider = index > 0,
                        showChevron = true,
                        onClick = { onMovement(row.key) },
                    )
                }
            }
        }
        Spacer(Modifier.height(OhTheme.space.s5))
        if (!strengthOff) {
            OhCard {
                OhCardHeader(
                    eyebrow = stringResource(R.string.train_map_title),
                    title = if (segment == 0) {
                        stringResource(R.string.train_map_recovery)
                    } else {
                        stringResource(R.string.train_map_plan)
                    },
                    glyph = OhModuleIcons.workout,
                )
                Spacer(Modifier.height(OhTheme.space.s5))
                OhSegmentedControl(
                    options = listOf(
                        stringResource(R.string.train_map_recovery),
                        stringResource(R.string.train_map_plan),
                    ),
                    selectedIndex = segment,
                    onSelect = { segment = it },
                )
                Spacer(Modifier.height(OhTheme.space.s5))
                if (shown == null) {
                    OhEmptyState(
                        title = stringResource(R.string.train_map_plan),
                        body = stringResource(R.string.train_routines_empty_body),
                    )
                } else {
                    OhBodyMap(
                        model = shown.copy(selected = selected?.let(Muscle::fromKey)),
                        onMuscle = { selected = it.key },
                        onShowTable = { showTable = true },
                    )
                    Spacer(Modifier.height(OhTheme.space.s5))
                    OhBodyMapLegend(shown)
                    Spacer(Modifier.height(OhTheme.space.s3))
                    Text(
                        text = if (segment == 0) {
                            stringResource(R.string.train_map_recovery)
                        } else if (model.planUsesCycle) {
                            stringResource(R.string.train_plan_caption_cycle)
                        } else {
                            stringResource(R.string.train_plan_caption_week)
                        },
                        style = OhTheme.type.footnote,
                        color = OhTheme.colors.label2,
                    )
                }
            }
        }
        if (!strengthOff) OhSectionTitle(stringResource(R.string.train_routines))
        if (!strengthOff && model.routines.isEmpty()) {
            OhCard {
                OhEmptyState(
                    title = stringResource(R.string.train_routines_empty_title),
                    body = stringResource(R.string.train_routines_empty_body),
                    actionLabel = stringResource(R.string.train_new_routine),
                    onAction = onNewRoutine,
                )
            }
        } else if (!strengthOff) {
            Column(verticalArrangement = Arrangement.spacedBy(OhTheme.space.s2)) {
                model.routines.forEach { routine ->
                    OhMediaRow(
                        title = routine.name,
                        subtitle = listOfNotNull(
                            if (routine.favourite) {
                                stringResource(R.string.train_routine_favourite)
                            } else {
                                null
                            },
                            routine.folder,
                            scheduleLabel(
                                routine.schedule,
                                routine.weekday,
                                routine.rotationIndex,
                            ),
                            pluralStringResource(
                                R.plurals.train_sets,
                                routine.setCount,
                                routine.setCount,
                            ),
                        ).joinToString(" · "),
                        thumbnail = {
                            OhBodyMap(
                                model = routine.map,
                                variant = app.openhealth.ui.body.BodyMapVariant.MINI,
                            )
                        },
                        onClick = { onOpenRoutine(routine.id) },
                        showChevron = true,
                    )
                }
            }
            Spacer(Modifier.height(OhTheme.space.s3))
            OhButton(
                text = stringResource(R.string.train_new_routine),
                onClick = onNewRoutine,
                variant = app.openhealth.ui.components.OhButtonVariant.Secondary,
            )
        }
        if (!strengthOff) OhSectionTitle(stringResource(R.string.train_history))
        if (!strengthOff && model.history.isEmpty()) {
            OhCard {
                OhEmptyState(
                    title = stringResource(R.string.train_history_empty_title),
                    body = stringResource(R.string.train_history_empty_body),
                )
            }
        } else if (!strengthOff) {
            Column(verticalArrangement = Arrangement.spacedBy(OhTheme.space.s2)) {
                model.history.forEach { row ->
                    OhMediaRow(
                        title = row.title ?: stringResource(R.string.train_summary_title),
                        subtitle = stringResource(
                            R.string.train_history_row,
                            stringResource(R.string.train_type_strength),
                            shortDate(row.date),
                            pluralStringResource(R.plurals.train_sets, row.sets, row.sets),
                        ),
                        thumbnail = {
                            OhBodyMap(
                                model = row.map,
                                variant = app.openhealth.ui.body.BodyMapVariant.MINI,
                            )
                        },
                        trailing = {
                            HistoryValue(
                                row.volumeLabel
                                    ?: stringResource(R.string.train_not_available),
                            )
                        },
                        onClick = { onOpenSession(row.sessionId) },
                    )
                }
            }
        }
        Spacer(Modifier.height(OhTheme.space.s7))
    }
    OhModalSheet(
        visible = pastOpen,
        onDismiss = { pastOpen = false },
        title = stringResource(R.string.train_past_title),
        primaryLabel = stringResource(R.string.train_past_action),
        onPrimary = {
            pastOpen = false
            onStartPast(model.today.plus(kotlinx.datetime.DatePeriod(days = -pastDays)))
        },
        cancelLabel = stringResource(R.string.train_editor_cancel),
        onCancel = { pastOpen = false },
    ) {
        Text(
            text = stringResource(R.string.train_past_body),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
        Spacer(Modifier.height(OhTheme.space.s3))
        Text(
            text = shortDate(model.today.plus(kotlinx.datetime.DatePeriod(days = -pastDays))),
            style = OhTheme.type.body,
            color = OhTheme.colors.label,
        )
        Spacer(Modifier.height(OhTheme.space.s3))
        OhStepper(
            value = pastDays.toString(),
            onDecrement = { if (pastDays > 1) pastDays -= 1 },
            onIncrement = { if (pastDays < PAST_LIMIT_DAYS) pastDays += 1 },
            unit = stringResource(R.string.train_past_days_ago),
            onValueChange = { value ->
                value.toIntOrNull()?.let { pastDays = it.coerceIn(1, PAST_LIMIT_DAYS) }
            },
        )
    }
    OhModalSheet(
        visible = showTable,
        onDismiss = { showTable = false },
        // The table names the map it belongs to; the segment chooses which one is shown.
        title = if (segment == 0) {
            stringResource(R.string.train_map_recovery)
        } else {
            stringResource(R.string.train_map_plan)
        },
        cancelLabel = stringResource(R.string.train_close),
        onCancel = { showTable = false },
    ) {
        shown?.let { OhBodyMapTable(it) }
    }
    OhModalSheet(
        visible = selected != null,
        onDismiss = { selected = null },
        title = selected?.let { Muscle.fromKey(it) }?.let { muscleLabel(it) },
    ) {
        val map = shown
        val muscle = selected?.let(Muscle::fromKey)
        if (map != null && muscle != null) {
            MuscleDetail(map, muscle)
        }
    }
}

@Composable
private fun WeekStrip(model: TrainModel) {
    OhCard {
        OhCardHeader(
            eyebrow = stringResource(R.string.train_week_title),
            title = shortDate(model.today),
            glyph = OhIcons.today,
        )
        Spacer(Modifier.height(OhTheme.space.s5))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            model.week.forEach { day ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(OhTheme.dims.touchTarget),
                ) {
                    val large = LocalDensity.current.fontScale >= OhTheme.dims.largeFontScale
                    val label = weekdayLabel(day.date.dayOfWeek.isoDayNumber)
                    Text(
                        text = if (large) label.take(1) else label,
                        style = OhTheme.type.caption,
                        color = OhTheme.colors.label2,
                    )
                    Spacer(Modifier.height(OhTheme.space.s1))
                    Box(
                        modifier = Modifier
                            .size(OhTheme.space.s8)
                            .clip(CircleShape)
                            .background(
                                if (day.isToday) {
                                    OhTheme.colors.acc
                                } else {
                                    if (day.hasSession) {
                                        OhTheme.colors.surface3
                                    } else {
                                        OhTheme.colors.surface2
                                    }
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = number(day.date.dayOfMonth.toDouble()),
                            style = OhTheme.type.caption.copy(fontWeight = OhWeight.semibold)
                                .tabular().ltrFigures(),
                            color = if (day.isToday) {
                                OhTheme.colors.onAcc
                            } else {
                                OhTheme.colors.label2
                            },
                        )
                    }
                    Spacer(Modifier.height(OhTheme.space.s1))
                    if (day.hasSession) {
                        Box(
                            modifier = Modifier
                                .size(OhTheme.dims.dot)
                                .clip(CircleShape)
                                .background(OhTheme.colors.acc),
                        )
                    } else {
                        Spacer(Modifier.height(OhTheme.dims.dot))
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanCard(
    model: TrainModel,
    onStartPlan: (String) -> Unit,
    onStartFreestyle: () -> Unit,
    onPast: () -> Unit,
) {
    val plan = model.plan
    OhCard {
        OhCardHeader(
            eyebrow = stringResource(R.string.train_plan_eyebrow),
            title = plan?.name ?: stringResource(R.string.train_live_empty_title),
            glyph = OhModuleIcons.workout,
        )
        if (plan == null) {
            Spacer(Modifier.height(OhTheme.space.s3))
            if (model.openSession != null) {
                Text(
                    text = stringResource(R.string.train_session_open_note),
                    style = OhTheme.type.footnote,
                    color = OhTheme.colors.label2,
                )
            } else {
                // The primary action stays above the explanation, so a fresh install can
                // start a workout without the empty state pushing the button off-screen.
                OhButton(text = stringResource(R.string.train_start), onClick = onStartFreestyle)
                Spacer(Modifier.height(OhTheme.space.s2))
                OhButton(
                    text = stringResource(R.string.train_log_past),
                    onClick = onPast,
                    variant = app.openhealth.ui.components.OhButtonVariant.Ghost,
                )
                Spacer(Modifier.height(OhTheme.space.s3))
            }
            OhEmptyState(
                title = stringResource(R.string.train_routines_empty_title),
                body = stringResource(R.string.train_routines_empty_body),
            )
        } else {
            Spacer(Modifier.height(OhTheme.space.s5))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.width(OhTheme.dims.mediaThumb + OhTheme.space.s6)) {
                    OhBodyMap(
                        model = plan.map,
                        variant = app.openhealth.ui.body.BodyMapVariant.MINI,
                    )
                }
                Spacer(Modifier.width(OhTheme.space.s3))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = planReasonLabel(plan.reason),
                        style = OhTheme.type.footnote,
                        color = OhTheme.colors.label2,
                    )
                    Text(
                        text = pluralStringResource(
                            R.plurals.train_working_sets,
                            plan.setCount,
                            plan.setCount,
                        ),
                        style = OhTheme.type.footnote,
                        color = OhTheme.colors.label2,
                    )
                }
            }
            Spacer(Modifier.height(OhTheme.space.s5))
            if (model.openSession != null) {
                Text(
                    text = stringResource(R.string.train_session_open_note),
                    style = OhTheme.type.footnote,
                    color = OhTheme.colors.label2,
                )
            } else {
                OhButton(
                    text = stringResource(R.string.train_start),
                    onClick = { onStartPlan(plan.routineId) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(OhTheme.space.s2))
                OhButton(
                    text = stringResource(R.string.train_start_freestyle),
                    onClick = onStartFreestyle,
                    variant = app.openhealth.ui.components.OhButtonVariant.Ghost,
                    modifier = Modifier.fillMaxWidth(),
                )
                OhButton(
                    text = stringResource(R.string.train_log_past),
                    onClick = onPast,
                    variant = app.openhealth.ui.components.OhButtonVariant.Ghost,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun OpenSessionCard(open: OpenSessionCard, onResume: (String) -> Unit) {
    OhCard {
        OhCardHeader(
            eyebrow = stringResource(R.string.train_open_session_resume),
            title = open.title,
            glyph = OhIcons.timer,
        )
        Spacer(Modifier.height(OhTheme.space.s5))
        Text(
            text = stringResource(
                R.string.train_open_session,
                open.title,
                pluralStringResource(R.plurals.train_sets, open.setCount, open.setCount),
            ),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
        Spacer(Modifier.height(OhTheme.space.s5))
        OhButton(
            text = stringResource(R.string.train_resume),
            onClick = { onResume(open.sessionId) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun HistoryValue(value: String) {
    Text(
        text = value,
        style = OhTheme.type.subhead.tabular().ltrFigures(),
        color = OhTheme.colors.label2,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.End,
    )
}

@Composable
private fun MuscleDetail(map: BodyMapModel, muscle: Muscle) {
    val shade = map.shade(muscle)
    Text(
        text = when (shade) {
            is app.openhealth.ui.body.MuscleShade.Recovery ->
                app.openhealth.ui.body.recoveryStateLabel(shade.step, shade.hoursSinceLoad)
            is app.openhealth.ui.body.MuscleShade.Plan ->
                stringResource(R.string.train_plan_sets, number(shade.weeklySets))
            app.openhealth.ui.body.MuscleShade.None ->
                stringResource(R.string.recovery_untrained)
        },
        style = OhTheme.type.title3.copy(fontWeight = OhWeight.semibold),
        color = OhTheme.colors.label,
    )
    if (shade is app.openhealth.ui.body.MuscleShade.Recovery) {
        Spacer(Modifier.height(OhTheme.space.s5))
        Text(
            text = stringResource(
                R.string.train_muscle_sets,
                number(shade.sets, 1),
            ),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
        val recorded = shade.lastRecordedAt
        if (recorded != null) {
            Text(
                text = stringResource(
                    R.string.train_muscle_recorded,
                    app.openhealth.ui.screens.common.timeOf(
                        recorded,
                        TimeZone.currentSystemDefault(),
                    ),
                ),
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
            )
        }
        shade.lastSourceLabel?.let { source ->
            Text(
                text = source,
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
            )
        }
        if (shade.hardSession) {
            Spacer(Modifier.height(OhTheme.space.s3))
            Text(
                text = stringResource(R.string.train_muscle_hard),
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
            )
        }
    }
}

private const val PAST_LIMIT_DAYS = 365
