package app.openhealth.ui.screens.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.openhealth.core.exercise.MusclePlan
import app.openhealth.ui.R
import app.openhealth.ui.body.BodyMapModel
import app.openhealth.ui.body.BodyMapVariant
import app.openhealth.ui.body.OhBodyMap
import app.openhealth.ui.components.OhCard
import app.openhealth.ui.components.OhInsetGroup
import app.openhealth.ui.components.OhMediaRow
import app.openhealth.ui.components.OhRow
import app.openhealth.ui.screens.common.OhEmptyState
import app.openhealth.ui.screens.common.OhPageHeader
import app.openhealth.ui.screens.common.OhScreen
import app.openhealth.ui.screens.common.OhSectionTitle
import app.openhealth.ui.theme.OhTheme

/**
 * The yoga library (`pr.md` §6.7 MVP): the eight built-in flows, the poses with their
 * own text, and the favourite control. No pose media is downloaded; the body map is the
 * diagram and it is drawn from the pose's own muscle list.
 */
@Composable
fun YogaLibraryScreen(
    model: YogaLibraryModel,
    onBack: () -> Unit,
    onStart: (String) -> Unit,
    onFavourite: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var openPose by rememberSaveable { mutableStateOf<String?>(null) }
    OhScreen(modifier = modifier) {
        OhPageHeader(title = stringResource(R.string.yoga_title), onBack = onBack)
        if (model.moduleOff) {
            OhCard {
                OhEmptyState(
                    title = stringResource(R.string.activity_module_off),
                    body = stringResource(R.string.yoga_module_off_body),
                )
            }
            return@OhScreen
        }
        Text(
            text = model.note,
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
        if (model.weekMinutes > 0 || model.streakDays > 0) {
            Text(
                text = listOf(
                    stringResource(R.string.yoga_week_minutes, model.weekMinutes),
                    stringResource(R.string.yoga_streak_days, model.streakDays),
                ).joinToString(" · "),
                style = OhTheme.type.callout,
                color = OhTheme.colors.accInk,
                modifier = Modifier.padding(top = OhTheme.space.s3),
            )
        }
        OhSectionTitle(stringResource(R.string.yoga_flows))
        Column(verticalArrangement = Arrangement.spacedBy(OhTheme.space.s2)) {
            model.flows.forEach { flow ->
                OhMediaRow(
                    title = flow.name,
                    subtitle = listOf(
                        stringResource(R.string.yoga_minutes, flow.minutes),
                        stringResource(R.string.yoga_poses_count, flow.poseCount),
                        flow.levelLabel,
                        flow.focus,
                    ).joinToString(" · "),
                    thumbnail = {
                        OhBodyMap(
                            model = BodyMapModel.plan(
                                sex = app.openhealth.core.body.BodySex.MALE,
                                coverage = emptyMap<app.openhealth.core.body.Muscle, MusclePlan>(),
                            ),
                            variant = BodyMapVariant.MINI,
                        )
                    },
                    trailing = {
                        FavouriteButton(
                            favourite = flow.favourite,
                            onClick = { onFavourite(flow.id, !flow.favourite) },
                        )
                    },
                    onClick = { onStart(flow.id) },
                    showChevron = true,
                )
            }
        }
        OhSectionTitle(stringResource(R.string.yoga_poses))
        OhInsetGroup {
            model.poses.forEachIndexed { index, pose ->
                OhRow(
                    title = pose.name,
                    subtitle = listOf(pose.sanskrit, pose.levelLabel)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    value = pose.muscleLabels.firstOrNull(),
                    showDivider = index > 0,
                    showChevron = true,
                    onClick = { openPose = pose.id },
                )
            }
        }
    }
    val pose = model.poses.firstOrNull { it.id == openPose }
    if (pose != null) {
        app.openhealth.ui.components.OhModalSheet(
            visible = true,
            onDismiss = { openPose = null },
            title = pose.name,
        ) {
            Text(
                text = pose.sanskrit,
                style = OhTheme.type.subhead,
                color = OhTheme.colors.label2,
            )
            Spacer(Modifier.height(OhTheme.space.s2))
            Text(
                text = stringResource(R.string.yoga_why),
                style = OhTheme.type.caption,
                color = OhTheme.colors.label3,
            )
            Text(text = pose.why, style = OhTheme.type.body, color = OhTheme.colors.label)
            Spacer(Modifier.height(OhTheme.space.s3))
            Text(
                text = stringResource(R.string.yoga_modification),
                style = OhTheme.type.caption,
                color = OhTheme.colors.label3,
            )
            Text(text = pose.modification, style = OhTheme.type.body, color = OhTheme.colors.label)
            pose.contraindications?.let { note ->
                Spacer(Modifier.height(OhTheme.space.s3))
                Text(
                    text = stringResource(R.string.yoga_contraindications),
                    style = OhTheme.type.caption,
                    color = OhTheme.colors.label3,
                )
                Text(text = note, style = OhTheme.type.body, color = OhTheme.colors.label)
            }
            Spacer(Modifier.height(OhTheme.space.s3))
            Text(
                text = stringResource(R.string.yoga_breath),
                style = OhTheme.type.caption,
                color = OhTheme.colors.label3,
            )
            Text(text = pose.breathCue, style = OhTheme.type.body, color = OhTheme.colors.label)
            Spacer(Modifier.height(OhTheme.space.s3))
            pose.instructions.forEach { instruction ->
                Text(
                    text = "· $instruction",
                    style = OhTheme.type.footnote,
                    color = OhTheme.colors.label2,
                )
            }
        }
    }
}

@Composable
private fun FavouriteButton(favourite: Boolean, onClick: () -> Unit) {
    app.openhealth.ui.components.OhIconButton(
        icon = app.openhealth.ui.icons.OhIcons.check,
        contentDescription = stringResource(
            if (favourite) R.string.yoga_unfavourite else R.string.yoga_favourite,
        ),
        onClick = onClick,
        on = favourite,
        tint = if (favourite) OhTheme.colors.acc else OhTheme.colors.label2,
    )
}
