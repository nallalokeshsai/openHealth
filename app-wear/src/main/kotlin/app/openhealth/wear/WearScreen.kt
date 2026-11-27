package app.openhealth.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.TimeTextDefaults
import androidx.wear.compose.material3.timeTextCurvedText
import app.openhealth.core.training.clockText
import app.openhealth.core.units.Measure
import app.openhealth.core.units.UcumUnit
import app.openhealth.core.watch.WatchExercise
import app.openhealth.core.watch.WatchLiveState
import app.openhealth.ui.theme.OhTheme
import app.openhealth.ui.theme.OhWeight
import app.openhealth.ui.theme.tabular

/**
 * The watch workout surface (`DESIGN.md` §14.6; `docs/adr/0019`). It is stateless so the
 * instrumented test can drive it with a snapshot; every action is a callback.
 */
@Composable
fun WearScreen(
    state: WatchLiveState,
    connected: Boolean,
    heartRateAllowed: Boolean,
    onLogSet: () -> Unit,
    onFinish: () -> Unit,
    onWeight: (Int) -> Unit,
    onReps: (Int) -> Unit,
    onExtendRest: () -> Unit,
    onSkipRest: () -> Unit,
    onAllowHeartRate: () -> Unit,
) {
    // Wear Material 3 paints its own palette unless it is told not to (`DESIGN.md` §12.4,
    // Appendix B). The curved time is a CurvedScope, not a composable, so its style is
    // built here and captured.
    val background = OhTheme.colors.bg
    val timeStyle = TimeTextDefaults.timeTextStyle(
        background = background,
        color = OhTheme.colors.label2,
    )
    Box(Modifier.fillMaxSize().background(background)) {
        AppScaffold(
            timeText = {
                TimeText(backgroundColor = background) { time ->
                    timeTextCurvedText(time, timeStyle)
                }
            },
            containerColor = OhTheme.colors.bg,
            contentColor = OhTheme.colors.label,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = OhTheme.space.s8 + OhTheme.space.s3)
                    .padding(horizontal = OhTheme.space.s6)
                    .padding(bottom = OhTheme.space.s8)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(OhTheme.space.s3),
            ) {
                if (state.hasSession) {
                    SessionContent(
                        state = state,
                        connected = connected,
                        heartRateAllowed = heartRateAllowed,
                        onLogSet = onLogSet,
                        onFinish = onFinish,
                        onWeight = onWeight,
                        onReps = onReps,
                        onExtendRest = onExtendRest,
                        onSkipRest = onSkipRest,
                        onAllowHeartRate = onAllowHeartRate,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.wear_workout),
                        style = OhTheme.type.title3.copy(fontWeight = OhWeight.semibold),
                        color = OhTheme.colors.label,
                    )
                    if (!connected) {
                        Text(
                            text = stringResource(R.string.wear_waiting_phone),
                            style = OhTheme.type.caption2,
                            color = OhTheme.colors.label3,
                            textAlign = TextAlign.Center,
                        )
                    }
                    StartContent()
                }
            }
        }
    }
}

@Composable
private fun StartContent() {
    Text(
        text = stringResource(R.string.wear_start_on_phone),
        style = OhTheme.type.footnote,
        color = OhTheme.colors.label2,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun SessionContent(
    state: WatchLiveState,
    connected: Boolean,
    heartRateAllowed: Boolean,
    onLogSet: () -> Unit,
    onFinish: () -> Unit,
    onWeight: (Int) -> Unit,
    onReps: (Int) -> Unit,
    onExtendRest: () -> Unit,
    onSkipRest: () -> Unit,
    onAllowHeartRate: () -> Unit,
) {
    val session = state.session ?: return
    val exercise = state.currentExercise
    if (exercise == null) {
        Text(
            text = stringResource(R.string.wear_no_exercises),
            style = OhTheme.type.body,
            color = OhTheme.colors.label2,
            textAlign = TextAlign.Center,
        )
    } else {
        ExerciseContent(
            state = state,
            exercise = exercise,
            onLogSet = onLogSet,
            onWeight = onWeight,
            onReps = onReps,
        )
    }

    RestContent(state = state, onExtend = onExtendRest, onSkip = onSkipRest)

    HeartRateContent(
        state = state,
        allowed = heartRateAllowed,
        onAllow = onAllowHeartRate,
    )

    if (state.pendingCount > 0) {
        Text(
            text = if (connected) {
                stringResource(R.string.wear_waiting_sets, state.pendingCount)
            } else {
                stringResource(R.string.wear_offline)
            },
            style = OhTheme.type.caption2,
            color = OhTheme.colors.labelDim,
            textAlign = TextAlign.Center,
        )
    }

    CompactButton(
        onClick = onFinish,
        modifier = Modifier.heightIn(min = OhTheme.dims.touchTarget),
        colors = ButtonDefaults.buttonColors(
            containerColor = OhTheme.colors.surface2,
            contentColor = OhTheme.colors.label,
        ),
    ) {
        Text(text = stringResource(R.string.wear_finish), style = OhTheme.type.caption2)
    }
}

@Composable
private fun ExerciseContent(
    state: WatchLiveState,
    exercise: WatchExercise,
    onLogSet: () -> Unit,
    onWeight: (Int) -> Unit,
    onReps: (Int) -> Unit,
) {
    val session = state.session ?: return
    Text(
        text = exercise.name,
        style = OhTheme.type.body.copy(fontWeight = OhWeight.semibold),
        color = OhTheme.colors.label,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
    )
    val done = exercise.sets.count { !it.isWarmup }
    Text(
        text = stringResource(
            R.string.wear_set_progress,
            (done + 1).coerceAtMost(exercise.targetSets.coerceAtLeast(1)),
            exercise.targetSets.coerceAtLeast(1),
            session.currentExerciseIndex + 1,
            session.exercises.size,
        ),
        style = OhTheme.type.caption2,
        color = OhTheme.colors.label3,
    )

    var selected by rememberSaveable { mutableIntStateOf(0) }
    val lessLabel = stringResource(
        if (selected == 0) R.string.wear_less_weight else R.string.wear_less_reps,
    )
    val moreLabel = stringResource(
        if (selected == 0) R.string.wear_more_weight else R.string.wear_more_reps,
    )
    val selectorLabel = stringResource(
        if (selected == 0) R.string.wear_weight_selected else R.string.wear_reps_selected,
    )
    val selectorState = stringResource(
        if (selected == 0) R.string.wear_weight else R.string.wear_reps,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompactButton(
            onClick = { if (selected == 0) onWeight(-1) else onReps(-1) },
            modifier = Modifier
                .heightIn(min = OhTheme.dims.touchTarget)
                .semantics { contentDescription = lessLabel },
            colors = ButtonDefaults.buttonColors(
                containerColor = OhTheme.colors.surface2,
                contentColor = OhTheme.colors.label,
            ),
        ) {
            Text(text = "−", style = OhTheme.type.title3, color = OhTheme.colors.label)
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .heightIn(min = OhTheme.dims.touchTarget)
                .semantics {
                    role = Role.Button
                    contentDescription = selectorLabel
                    stateDescription = selectorState
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { selected = (selected + 1) % 2 },
        ) {
            Text(
                text = weightText(state),
                style = OhTheme.type.title3.tabular().copy(
                    fontWeight = if (selected == 0) OhWeight.semibold else OhWeight.regular,
                ),
                color = if (selected == 0) OhTheme.colors.label else OhTheme.colors.label3,
            )
            Text(
                text = stringResource(R.string.wear_reps_value, state.reps),
                style = OhTheme.type.caption2.tabular().copy(
                    fontWeight = if (selected == 1) OhWeight.semibold else OhWeight.regular,
                ),
                color = if (selected == 1) OhTheme.colors.label else OhTheme.colors.label3,
            )
        }
        CompactButton(
            onClick = { if (selected == 0) onWeight(1) else onReps(1) },
            modifier = Modifier
                .heightIn(min = OhTheme.dims.touchTarget)
                .semantics { contentDescription = moreLabel },
            colors = ButtonDefaults.buttonColors(
                containerColor = OhTheme.colors.surface2,
                contentColor = OhTheme.colors.label,
            ),
        ) {
            Text(text = "+", style = OhTheme.type.title3, color = OhTheme.colors.label)
        }
    }

    Button(
        onClick = onLogSet,
        modifier = Modifier.fillMaxWidth().heightIn(min = OhTheme.dims.touchTarget),
        colors = ButtonDefaults.buttonColors(
            containerColor = OhTheme.colors.acc,
            contentColor = OhTheme.colors.onAcc,
        ),
    ) {
        Text(
            text = stringResource(R.string.wear_log_set),
            style = OhTheme.type.body.copy(fontWeight = OhWeight.semibold),
        )
    }
}

@Composable
private fun RestContent(state: WatchLiveState, onExtend: () -> Unit, onSkip: () -> Unit) {
    val remaining = state.restRemainingSeconds()
    if (remaining <= 0) return
    Text(
        text = stringResource(R.string.wear_rest),
        style = OhTheme.type.caption2,
        color = OhTheme.colors.label2,
    )
    Text(
        text = clockText(remaining.toLong()),
        style = OhTheme.type.timer.tabular(),
        color = OhTheme.colors.label,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(OhTheme.dims.timerProgress)
            .background(OhTheme.colors.surface3),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(state.restProgress())
                .height(OhTheme.dims.timerProgress)
                .background(OhTheme.colors.acc),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(OhTheme.space.s3)) {
        CompactButton(
            onClick = onExtend,
            modifier = Modifier.heightIn(min = OhTheme.dims.touchTarget),
            colors = ButtonDefaults.buttonColors(
                containerColor = OhTheme.colors.surface2,
                contentColor = OhTheme.colors.label,
            ),
        ) {
            Text(text = stringResource(R.string.wear_extend), style = OhTheme.type.caption2)
        }
        CompactButton(
            onClick = onSkip,
            modifier = Modifier.heightIn(min = OhTheme.dims.touchTarget),
            colors = ButtonDefaults.buttonColors(
                containerColor = OhTheme.colors.surface2,
                contentColor = OhTheme.colors.label,
            ),
        ) {
            Text(text = stringResource(R.string.wear_skip), style = OhTheme.type.caption2)
        }
    }
}

@Composable
private fun HeartRateContent(state: WatchLiveState, allowed: Boolean, onAllow: () -> Unit) {
    if (allowed) {
        state.heartRateBpm?.let { bpm ->
            Text(
                text = stringResource(R.string.wear_heart_rate, bpm),
                style = OhTheme.type.caption.tabular(),
                color = OhTheme.colors.label2,
            )
        }
    } else {
        CompactButton(
            onClick = onAllow,
            modifier = Modifier.heightIn(min = OhTheme.dims.touchTarget),
            colors = ButtonDefaults.buttonColors(
                containerColor = OhTheme.colors.surface2,
                contentColor = OhTheme.colors.label,
            ),
        ) {
            Text(
                text = stringResource(R.string.wear_allow_heart_rate),
                style = OhTheme.type.caption2,
            )
        }
    }
}

@Composable
private fun weightText(state: WatchLiveState): String {
    val kg = state.weightKg
    val shown = if (state.weightUnit == "lb") {
        Measure(kg, UcumUnit.KILOGRAM).convertTo(UcumUnit.POUND).value
    } else {
        kg
    }
    val locale = LocalConfiguration.current.locales[0]
    return String.format(locale, "%.1f %s", shown, state.weightUnit)
}
