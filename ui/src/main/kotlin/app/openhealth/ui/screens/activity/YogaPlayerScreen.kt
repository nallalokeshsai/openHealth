package app.openhealth.ui.screens.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.openhealth.core.activity.BreathPattern
import app.openhealth.core.activity.BreathPhase
import app.openhealth.core.activity.YogaFlow
import app.openhealth.core.activity.YogaPhase
import app.openhealth.core.activity.YogaPlayer
import app.openhealth.core.activity.YogaPlayerState
import app.openhealth.core.activity.YogaSide
import app.openhealth.core.body.BodySex
import app.openhealth.core.body.Muscle
import app.openhealth.core.exercise.MusclePlan
import app.openhealth.ui.R
import app.openhealth.ui.body.BodyMapModel
import app.openhealth.ui.body.BodyMapVariant
import app.openhealth.ui.body.OhBodyMap
import app.openhealth.ui.body.OhBodyMapTable
import app.openhealth.ui.body.PlanWindow
import app.openhealth.ui.components.OhButton
import app.openhealth.ui.components.OhButtonVariant
import app.openhealth.ui.components.OhIconButton
import app.openhealth.ui.components.OhModalSheet
import app.openhealth.ui.icons.OhIcons
import app.openhealth.ui.screens.common.OhFocusedScreen
import app.openhealth.ui.theme.OhTheme
import app.openhealth.ui.theme.OhWeight
import app.openhealth.ui.theme.tabular

/**
 * The yoga player (`pr.md` §6.7): a large timer, the pose and its body map, the breath
 * cue, pause, skip, back, repeat and finish. The clock lives in the foreground service,
 * so the hold keeps counting with the screen off and survives a process death.
 */
@Composable
fun YogaPlayerScreen(
    flow: YogaFlow,
    state: YogaPlayerState,
    breath: BreathPattern,
    voiceOn: Boolean,
    onPause: () -> Unit,
    onSkip: () -> Unit,
    onPrevious: () -> Unit,
    onRepeat: () -> Unit,
    onFinish: () -> Unit,
    onVoice: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    synchronousLayers: Boolean = false,
) {
    val step = YogaPlayer.currentStep(flow, state)
    val pose = step?.let { app.openhealth.core.activity.YogaLibrary.pose(it.poseId) }
    val remaining = YogaPlayer.phaseRemaining(flow, state)
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var showTable by rememberSaveable { mutableStateOf(false) }
    OhFocusedScreen(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OhIconButton(
                icon = OhIcons.close,
                contentDescription = stringResource(R.string.yoga_player_finish),
                onClick = onFinish,
            )
            Spacer(Modifier.width(OhTheme.space.s3))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = flow.name, style = OhTheme.type.title3, color = OhTheme.colors.label)
                Text(
                    text = stringResource(
                        R.string.yoga_player_pose,
                        (state.stepIndex + 1).coerceAtMost(state.stepCount),
                        state.stepCount,
                    ),
                    style = OhTheme.type.caption,
                    color = OhTheme.colors.label3,
                )
            }
            OhIconButton(
                icon = OhIcons.check,
                contentDescription = stringResource(
                    if (voiceOn) R.string.yoga_player_voice else R.string.yoga_player_voice,
                ),
                onClick = { onVoice(!voiceOn) },
                on = voiceOn,
                tint = if (voiceOn) OhTheme.colors.acc else OhTheme.colors.label3,
            )
        }
        Spacer(Modifier.height(OhTheme.space.s5))
        if (pose == null) {
            Text(
                text = stringResource(R.string.yoga_finish_title),
                style = OhTheme.type.large,
                color = OhTheme.colors.label,
            )
            return@OhFocusedScreen
        }
        Text(
            text = pose.name,
            style = OhTheme.type.large.copy(fontWeight = OhWeight.bold),
            color = OhTheme.colors.label,
        )
        Text(
            text = pose.sanskrit,
            style = OhTheme.type.subhead,
            color = OhTheme.colors.label2,
        )
        if (step != null && step.side != YogaSide.NONE) {
            Text(
                text = stringResource(
                    when (step.side) {
                        YogaSide.LEFT -> R.string.yoga_side_left
                        YogaSide.RIGHT -> R.string.yoga_side_right
                        YogaSide.BOTH -> R.string.yoga_side_both
                        YogaSide.NONE -> R.string.yoga_player_hold
                    },
                ),
                style = OhTheme.type.callout.copy(fontWeight = OhWeight.semibold),
                color = OhTheme.colors.accInk,
            )
        }
        Spacer(Modifier.height(OhTheme.space.s4))
        Text(
            text = formatClock(remaining),
            style = OhTheme.type.timerFullScreen.copy(
                fontWeight = OhWeight.semibold,
            ).tabular(),
            color = if (state.paused) OhTheme.colors.label3 else OhTheme.colors.label,
            modifier = Modifier.semantics {
                contentDescription = "Pose timer, $remaining seconds remaining"
            },
        )
        Text(
            text = when (state.phase) {
                YogaPhase.TRANSITION -> stringResource(R.string.yoga_player_transition)
                YogaPhase.HOLD -> stringResource(R.string.yoga_player_hold)
                YogaPhase.DONE -> stringResource(R.string.yoga_finish_title)
            },
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
        Spacer(Modifier.height(OhTheme.space.s4))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(OhTheme.space.s0)
                .clip(CircleShape)
                .background(OhTheme.colors.surface3),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(YogaPlayer.progress(state))
                    .height(OhTheme.space.s0)
                    .clip(CircleShape)
                    .background(OhTheme.colors.acc),
            )
        }
        Spacer(Modifier.height(OhTheme.space.s6))
        val coverage = pose.primaryMuscles.associateWith {
            MusclePlan(it, weeklySets = 1.0, share = 1.0)
        } + pose.secondaryMuscles.associateWith {
            MusclePlan(it, weeklySets = 0.5, share = 0.5)
        }
        val map = BodyMapModel.plan(
            sex = BodySex.MALE,
            coverage = coverage,
            selected = selected?.let(Muscle::fromKey),
            window = PlanWindow.SESSION,
        )
        OhBodyMap(
            model = map,
            onMuscle = { selected = it.key },
            onShowTable = { showTable = true },
            variant = BodyMapVariant.FULL,
            synchronousLayers = synchronousLayers,
        )
        Spacer(Modifier.height(OhTheme.space.s2))
        OhButton(
            text = stringResource(R.string.body_map_show_table),
            onClick = { showTable = true },
            variant = OhButtonVariant.Quiet,
        )
        Spacer(Modifier.height(OhTheme.space.s4))
        val phase = breath.phaseAt(state.elapsedSeconds)
        Text(
            text = stringResource(phase.label()),
            style = OhTheme.type.title3,
            color = OhTheme.colors.accInk,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Text(
            text = pose.breathCue,
            style = OhTheme.type.body,
            color = OhTheme.colors.label2,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(OhTheme.space.s6))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OhButton(
                text = stringResource(R.string.yoga_player_previous),
                onClick = onPrevious,
                variant = OhButtonVariant.Ghost,
            )
            OhButton(
                text = stringResource(
                    if (state.paused) R.string.yoga_player_resume else R.string.yoga_player_pause,
                ),
                onClick = onPause,
                variant = OhButtonVariant.Primary,
            )
            OhButton(
                text = stringResource(R.string.yoga_player_skip),
                onClick = onSkip,
                variant = OhButtonVariant.Ghost,
            )
        }
        Spacer(Modifier.height(OhTheme.space.s2))
        OhButton(
            text = stringResource(R.string.yoga_player_repeat),
            onClick = onRepeat,
            variant = OhButtonVariant.Secondary,
        )
        Spacer(Modifier.height(OhTheme.space.s2))
        OhButton(
            text = stringResource(R.string.yoga_player_finish),
            onClick = onFinish,
            variant = OhButtonVariant.Quiet,
        )
        Spacer(Modifier.height(OhTheme.space.s3))
        Text(
            text = stringResource(R.string.yoga_player_screen_off),
            style = OhTheme.type.caption,
            color = OhTheme.colors.label3,
        )
    }
    if (showTable && pose != null) {
        OhModalSheet(
            visible = true,
            onDismiss = { showTable = false },
            title = stringResource(R.string.body_map_table_title),
        ) {
            OhBodyMapTable(
                model = BodyMapModel.plan(
                    sex = BodySex.MALE,
                    coverage = pose.primaryMuscles.associateWith {
                        MusclePlan(it, weeklySets = 1.0, share = 1.0)
                    } + pose.secondaryMuscles.associateWith {
                        MusclePlan(it, weeklySets = 0.5, share = 0.5)
                    },
                    window = PlanWindow.SESSION,
                ),
            )
        }
    }
}

private fun BreathPhase.label(): Int = when (this) {
    BreathPhase.INHALE -> R.string.yoga_breath_inhale
    BreathPhase.HOLD_IN -> R.string.yoga_breath_hold_in
    BreathPhase.EXHALE -> R.string.yoga_breath_exhale
    BreathPhase.HOLD_OUT -> R.string.yoga_breath_hold_out
}

private const val SECONDS_PER_MINUTE = 60

internal fun formatClock(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val minutes = safe / SECONDS_PER_MINUTE
    val rest = safe % SECONDS_PER_MINUTE
    return "%d:%02d".format(minutes, rest)
}

/** The summary shown after a flow or after leaving one early. */
@Composable
fun YogaFinishScreen(
    flowName: String,
    minutes: Int,
    posesHeld: Int,
    muscleLabels: List<String>,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OhFocusedScreen(modifier = modifier) {
        Spacer(Modifier.height(OhTheme.space.s7))
        Text(
            text = stringResource(R.string.yoga_finish_title),
            style = OhTheme.type.large.copy(fontWeight = OhWeight.bold),
            color = OhTheme.colors.label,
        )
        Text(text = flowName, style = OhTheme.type.title3, color = OhTheme.colors.label2)
        Spacer(Modifier.height(OhTheme.space.s3))
        Text(
            text = stringResource(R.string.yoga_finish_body, minutes, posesHeld),
            style = OhTheme.type.body,
            color = OhTheme.colors.label,
        )
        if (muscleLabels.isNotEmpty()) {
            Spacer(Modifier.height(OhTheme.space.s4))
            Text(
                text = stringResource(R.string.yoga_finish_muscles),
                style = OhTheme.type.caption,
                color = OhTheme.colors.label3,
            )
            Text(
                text = muscleLabels.joinToString(" · "),
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
            )
        }
        Spacer(Modifier.height(OhTheme.space.s7))
        OhButton(text = stringResource(R.string.yoga_finish_done), onClick = onDone)
    }
}
