package app.openhealth.ui.screens.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.openhealth.core.activity.ActivityType
import app.openhealth.ui.R
import app.openhealth.ui.components.OhButton
import app.openhealth.ui.components.OhButtonVariant
import app.openhealth.ui.components.OhChip
import app.openhealth.ui.components.OhChipStrip
import app.openhealth.ui.components.OhKpiBand
import app.openhealth.ui.components.OhKpiFigure
import app.openhealth.ui.screens.common.OhPageHeader
import app.openhealth.ui.screens.common.OhScreen
import app.openhealth.ui.screens.common.OhSectionTitle
import app.openhealth.ui.theme.OhTheme

/**
 * The route recorder (`pr.md` §6.11): choose the sport, start the location service, then
 * pause, resume or finish. The values come from the persisted snapshot, so leaving this
 * screen or turning it off keeps the recording alive.
 */
@Composable
fun GpsRecordScreen(
    recording: GpsRecordingModel?,
    onBack: () -> Unit,
    onStart: (ActivityType) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var type by rememberSaveable { mutableStateOf(ActivityType.RUN) }
    OhScreen(modifier = modifier) {
        OhPageHeader(title = stringResource(R.string.gps_title), onBack = onBack)
        Text(
            text = stringResource(R.string.gps_note),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
        if (recording == null) {
            OhSectionTitle(stringResource(R.string.gps_type))
            OhChipStrip {
                ActivityType.entries.filter { it.distanceBased }.forEach { option ->
                    OhChip(
                        text = typeLabel(option),
                        selected = type == option,
                        onClick = { type = option },
                    )
                }
            }
            Spacer(Modifier.height(OhTheme.space.s6))
            OhButton(
                text = stringResource(R.string.gps_start),
                onClick = { onStart(type) },
            )
            Spacer(Modifier.height(OhTheme.space.s2))
            Text(
                text = stringResource(R.string.gps_permission),
                style = OhTheme.type.caption,
                color = OhTheme.colors.label3,
            )
            return@OhScreen
        }
        OhKpiBand(
            figures = listOf(
                OhKpiFigure(
                    formatClock(recording.elapsedSeconds.toInt()),
                    stringResource(R.string.gps_elapsed),
                ),
                OhKpiFigure(
                    "%.2f".format(recording.distanceM / METRES_PER_KILOMETRE),
                    stringResource(R.string.gps_distance),
                ),
                OhKpiFigure(
                    if (recording.paused) {
                        stringResource(
                            R.string.gps_paused,
                        )
                    } else {
                        typeLabel(recording.type)
                    },
                    stringResource(R.string.gps_type),
                ),
            ),
        )
        Spacer(Modifier.height(OhTheme.space.s5))
        if (!recording.hasFix) {
            Text(
                text = stringResource(R.string.gps_no_fix),
                style = OhTheme.type.body,
                color = OhTheme.colors.orangeInk,
            )
            Spacer(Modifier.height(OhTheme.space.s4))
        }
        recording.message?.let {
            Text(
                text = it,
                style = OhTheme.type.footnote,
                color = OhTheme.colors.orangeInk,
            )
            Spacer(Modifier.height(OhTheme.space.s4))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OhTheme.space.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OhButton(
                text = stringResource(
                    if (recording.paused) R.string.gps_resume else R.string.gps_pause,
                ),
                onClick = if (recording.paused) onResume else onPause,
                variant = OhButtonVariant.Secondary,
            )
            OhButton(
                text = stringResource(R.string.gps_finish),
                onClick = onFinish,
            )
        }
        if (recording.recovered) {
            Spacer(Modifier.height(OhTheme.space.s3))
            Text(
                text = stringResource(R.string.gps_recovered),
                style = OhTheme.type.caption,
                color = OhTheme.colors.accInk,
            )
        }
    }
}

private const val METRES_PER_KILOMETRE = 1_000.0
