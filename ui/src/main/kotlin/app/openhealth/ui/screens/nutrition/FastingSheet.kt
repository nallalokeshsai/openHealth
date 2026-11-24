package app.openhealth.ui.screens.nutrition

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.openhealth.ui.R
import app.openhealth.ui.components.OhButton
import app.openhealth.ui.components.OhButtonSize
import app.openhealth.ui.components.OhButtonVariant
import app.openhealth.ui.components.OhChip
import app.openhealth.ui.components.OhChipStrip
import app.openhealth.ui.components.OhModalSheet
import app.openhealth.ui.components.OhRow
import app.openhealth.ui.components.OhSwitch
import app.openhealth.ui.theme.OhTheme

/** The optional fasting timer (`pr.md` §6.3): a clock, never a score or a streak. */
@Composable
fun FastingSheet(
    model: FastingModel,
    onDismiss: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onHours: (Double) -> Unit,
    onStart: () -> Unit,
    onEnd: () -> Unit,
) {
    OhModalSheet(
        visible = true,
        onDismiss = onDismiss,
        title = stringResource(R.string.nutrition_fasting),
    ) {
        OhRow(
            title = stringResource(R.string.nutrition_fasting_on),
            trailing = {
                OhSwitch(
                    checked = model.enabled,
                    onCheckedChange = onToggle,
                    contentDescription = stringResource(R.string.nutrition_fasting_on),
                )
            },
        )
        Spacer(Modifier.height(OhTheme.space.s3))
        Text(
            text = stringResource(R.string.nutrition_fasting_hours),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
        Spacer(Modifier.height(OhTheme.space.s2))
        OhChipStrip {
            model.presets.forEach { hours ->
                OhChip(
                    text = stringResource(R.string.nutrition_fasting_target, hours.toInt()),
                    selected = model.targetHours == hours,
                    onClick = { onHours(hours) },
                )
            }
        }
        Spacer(Modifier.height(OhTheme.space.s4))
        if (model.open) {
            Text(
                text = stringResource(
                    R.string.nutrition_fasting_elapsed,
                    durationText(model.elapsedMillis),
                ),
                style = OhTheme.type.title3,
                color = OhTheme.colors.label,
            )
            model.remainingMillis?.let {
                Text(
                    text = stringResource(R.string.nutrition_fasting_remaining, durationText(it)),
                    style = OhTheme.type.footnote,
                    color = OhTheme.colors.label2,
                )
            } ?: Text(
                text = stringResource(R.string.nutrition_fasting_past),
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
            )
            Spacer(Modifier.height(OhTheme.space.s4))
            OhButton(
                text = stringResource(R.string.nutrition_fasting_end),
                onClick = onEnd,
                variant = OhButtonVariant.Secondary,
            )
        } else {
            OhButton(
                text = stringResource(R.string.nutrition_fasting_start),
                onClick = onStart,
                enabled = model.enabled,
            )
        }
        Spacer(Modifier.height(OhTheme.space.s3))
        Text(
            text = stringResource(R.string.nutrition_fasting_optional),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
        if (!model.enabled) {
            Spacer(Modifier.height(OhTheme.space.s2))
            OhButton(
                text = stringResource(R.string.nutrition_fasting_on),
                onClick = { onToggle(true) },
                variant = OhButtonVariant.Quiet,
                size = OhButtonSize.Small,
            )
        }
    }
}
