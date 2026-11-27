package app.openhealth.ui.screens.nutrition

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import app.openhealth.ui.R
import app.openhealth.ui.components.OhButton
import app.openhealth.ui.components.OhButtonSize
import app.openhealth.ui.components.OhButtonVariant
import app.openhealth.ui.components.OhModalSheet
import app.openhealth.ui.components.OhRow
import app.openhealth.ui.components.OhTextField
import app.openhealth.ui.icons.OhIcons
import app.openhealth.ui.screens.common.number
import app.openhealth.ui.theme.OhTheme

/** Water (`pr.md` §6.3): the last container is one tap; the sheet exposes the rest. */
@Composable
fun WaterSheet(
    model: WaterSheetModel,
    onDismiss: () -> Unit,
    onLog: (containerId: String, millilitres: Double) -> Unit,
    onAdd: (label: String, millilitres: Double) -> Unit,
    onDelete: (containerId: String) -> Unit = {},
) {
    var label by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    val parsed = amount.toDoubleOrNull()
    OhModalSheet(
        visible = true,
        onDismiss = onDismiss,
        title = stringResource(R.string.nutrition_water),
    ) {
        Text(
            text = if (model.targetMl != null) {
                stringResource(
                    R.string.nutrition_water_total,
                    stringResource(R.string.nutrition_ml, number(model.totalMl)),
                    stringResource(R.string.nutrition_ml, number(model.targetMl)),
                )
            } else {
                stringResource(R.string.nutrition_ml, number(model.totalMl))
            },
            style = OhTheme.type.title3,
            color = OhTheme.colors.label,
        )
        Spacer(Modifier.height(OhTheme.space.s3))
        model.containers.forEach { container ->
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    OhRow(
                        title = container.label,
                        value = stringResource(
                            R.string.nutrition_ml,
                            number(container.millilitres),
                        ),
                        leadingIcon = OhIcons.add,
                        showDivider = true,
                        onClick = { onLog(container.id, container.millilitres) },
                    )
                }
                // The last container stays: a sheet with nothing to tap is a dead end.
                if (model.containers.size > 1) {
                    OhButton(
                        text = stringResource(R.string.nutrition_water_remove),
                        onClick = { onDelete(container.id) },
                        variant = OhButtonVariant.Ghost,
                        size = OhButtonSize.Small,
                    )
                }
            }
        }
        Spacer(Modifier.height(OhTheme.space.s4))
        OhTextField(
            value = label,
            onValueChange = { label = it },
            placeholder = stringResource(R.string.nutrition_water_label),
        )
        Spacer(Modifier.height(OhTheme.space.s2))
        OhTextField(
            value = amount,
            onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
            placeholder = stringResource(R.string.nutrition_water_amount),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Spacer(Modifier.height(OhTheme.space.s3))
        OhButton(
            text = stringResource(R.string.nutrition_water_add),
            onClick = {
                if (label.isNotBlank() && parsed != null && parsed > 0.0) {
                    onAdd(label, parsed)
                    label = ""
                    amount = ""
                }
            },
            variant = OhButtonVariant.Secondary,
            size = OhButtonSize.Small,
            enabled = label.isNotBlank() && parsed != null && parsed > 0.0,
        )
    }
}
