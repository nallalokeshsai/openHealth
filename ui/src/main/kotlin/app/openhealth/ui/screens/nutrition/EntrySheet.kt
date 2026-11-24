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
import app.openhealth.ui.components.OhModalSheet
import app.openhealth.ui.components.OhRow
import app.openhealth.ui.screens.common.mediumDate
import app.openhealth.ui.screens.common.number
import app.openhealth.ui.screens.common.recordingDelay
import app.openhealth.ui.screens.common.timeOf
import app.openhealth.ui.theme.OhTheme
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** The logged entry's source sheet (`DESIGN.md` §7.1): every value, and where it came from. */
@Composable
fun EntrySheet(
    model: EntryDetailModel,
    showKcal: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val zone = runCatching { TimeZone.of(model.zoneId) }
        .getOrDefault(TimeZone.currentSystemDefault())
    val happened = Instant.fromEpochMilliseconds(model.happenedAtMillis)
    val recorded = Instant.fromEpochMilliseconds(model.recordedAtMillis)
    OhModalSheet(
        visible = true,
        onDismiss = onDismiss,
        title = model.name,
        primaryLabel = stringResource(R.string.nutrition_entry_edit),
        onPrimary = onEdit,
        onCancel = onDismiss,
    ) {
        Text(
            text = buildString {
                model.brand?.let {
                    append(it)
                    append(" · ")
                }
                append(mealLabel(model.mealSlot, model.mealName))
                append(" · ")
                append(mediumDate(model.date))
            },
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
        Spacer(Modifier.height(OhTheme.space.s3))
        OhRow(
            title = stringResource(R.string.nutrition_entry_meal),
            value = mealLabel(model.mealSlot, model.mealName),
        )
        OhRow(
            title = stringResource(R.string.nutrition_entry_amount),
            value = portionText(model.grams, model.servings, model.servingLabel),
            showDivider = true,
        )
        if (showKcal) {
            model.kcal?.let {
                OhRow(
                    title = stringResource(R.string.nutrient_energy),
                    value = number(it),
                    unit = stringResource(R.string.unit_kcal),
                    showDivider = true,
                )
            }
        }
        model.proteinG?.let {
            OhRow(
                title = stringResource(R.string.nutrition_macro_protein),
                value = stringResource(R.string.nutrition_macro_value, number(it, 1)),
                showDivider = true,
            )
        }
        model.carbsG?.let {
            OhRow(
                title = stringResource(R.string.nutrition_macro_carbs),
                value = stringResource(R.string.nutrition_macro_value, number(it, 1)),
                showDivider = true,
            )
        }
        model.fatG?.let {
            OhRow(
                title = stringResource(R.string.nutrition_macro_fat),
                value = stringResource(R.string.nutrition_macro_value, number(it, 1)),
                showDivider = true,
            )
        }
        model.fibreG?.let {
            OhRow(
                title = stringResource(R.string.nutrition_macro_fibre),
                value = stringResource(R.string.nutrition_macro_value, number(it, 1)),
                showDivider = true,
            )
        }
        Spacer(Modifier.height(OhTheme.space.s4))
        Text(
            text = stringResource(R.string.nutrition_entry_provenance),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
        OhRow(
            title = stringResource(R.string.nutrition_entry_happened),
            value = "${mediumDate(model.date)}, ${timeOf(happened, zone)}",
            showDivider = true,
        )
        OhRow(
            title = stringResource(R.string.nutrition_entry_recorded),
            value = "${mediumDate(recorded.toLocalDateTime(zone).date)}, " +
                timeOf(recorded, zone),
            showDivider = true,
        )
        recordingDelay(happened, recorded)?.let { delay ->
            OhRow(
                title = stringResource(R.string.nutrition_entry_delay, delay),
                showDivider = true,
            )
        }
        OhRow(
            title = stringResource(R.string.nutrition_entry_zone),
            value = model.zoneId,
            showDivider = true,
        )
        OhRow(
            title = stringResource(R.string.nutrition_entry_provenance),
            value = sourceLabel(model.sourceKey),
            showDivider = true,
        )
        model.device?.let {
            OhRow(
                title = stringResource(R.string.nutrition_entry_device),
                value = it,
                showDivider = true,
            )
        }
        if (model.verified) {
            OhRow(
                title = stringResource(R.string.nutrition_verified),
                showDivider = true,
            )
        }
        if (model.edited) {
            OhRow(
                title = stringResource(R.string.nutrition_entry_edited),
                showDivider = true,
            )
        }
        model.rawRecordId?.let {
            OhRow(
                title = stringResource(R.string.nutrition_entry_raw, it),
                showDivider = true,
            )
        }
        Spacer(Modifier.height(OhTheme.space.s4))
        OhButton(
            text = stringResource(R.string.nutrition_entry_delete),
            onClick = onDelete,
            variant = OhButtonVariant.Danger,
            size = OhButtonSize.Small,
        )
    }
}
