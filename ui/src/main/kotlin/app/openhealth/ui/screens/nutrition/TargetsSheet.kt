package app.openhealth.ui.screens.nutrition

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
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
import app.openhealth.ui.components.OhTextField
import app.openhealth.ui.theme.OhTheme

/** Daily targets (`pr.md` §6.3). Every number is the person's; nothing is a prescription. */
@Composable
fun TargetsSheet(model: TargetsModel, onDismiss: () -> Unit, onSave: (TargetsSaveModel) -> Unit) {
    var kcal by remember(model.date) { mutableStateOf(model.kcal) }
    var protein by remember(model.date) { mutableStateOf(model.proteinG) }
    var carbs by remember(model.date) { mutableStateOf(model.carbsG) }
    var fat by remember(model.date) { mutableStateOf(model.fatG) }
    var fibre by remember(model.date) { mutableStateOf(model.fibreG) }
    var water by remember(model.date) { mutableStateOf(model.waterMl) }
    fun apply(suggested: TargetsSaveModel) {
        kcal = suggested.kcal?.let(::plain) ?: ""
        protein = suggested.proteinG?.let(::plain) ?: ""
        carbs = suggested.carbsG?.let(::plain) ?: ""
        fat = suggested.fatG?.let(::plain) ?: ""
        fibre = suggested.fibreG?.let(::plain) ?: ""
        water = suggested.waterMl?.let(::plain) ?: ""
    }
    OhModalSheet(
        visible = true,
        onDismiss = onDismiss,
        title = stringResource(R.string.nutrition_targets_title),
        primaryLabel = stringResource(R.string.nutrition_food_save),
        onPrimary = {
            onSave(
                TargetsSaveModel(
                    kcal = kcal.toDoubleOrNull(),
                    proteinG = protein.toDoubleOrNull(),
                    carbsG = carbs.toDoubleOrNull(),
                    fatG = fat.toDoubleOrNull(),
                    fibreG = fibre.toDoubleOrNull(),
                    waterMl = water.toDoubleOrNull(),
                ),
            )
        },
        onCancel = onDismiss,
    ) {
        if (!model.weightNeutral) {
            Field(
                label = stringResource(R.string.nutrition_targets_kcal),
                value = kcal,
                onChange = { kcal = it },
            )
        }
        Field(
            label = stringResource(R.string.nutrition_targets_protein),
            value = protein,
            onChange = { protein = it },
        )
        Field(
            label = stringResource(R.string.nutrition_targets_carbs),
            value = carbs,
            onChange = { carbs = it },
        )
        Field(
            label = stringResource(R.string.nutrition_targets_fat),
            value = fat,
            onChange = { fat = it },
        )
        Field(
            label = stringResource(R.string.nutrition_targets_fibre),
            value = fibre,
            onChange = { fibre = it },
        )
        Field(
            label = stringResource(R.string.nutrition_targets_water),
            value = water,
            onChange = { water = it },
        )
        model.suggested?.let { suggested ->
            Spacer(Modifier.height(OhTheme.space.s3))
            OhButton(
                text = stringResource(R.string.nutrition_targets_suggest),
                onClick = { apply(suggested) },
                variant = OhButtonVariant.Secondary,
                size = OhButtonSize.Small,
            )
        }
        Spacer(Modifier.height(OhTheme.space.s3))
        OhButton(
            text = stringResource(R.string.nutrition_targets_clear),
            onClick = {
                kcal = ""
                protein = ""
                carbs = ""
                fat = ""
                fibre = ""
                water = ""
            },
            variant = OhButtonVariant.Quiet,
            size = OhButtonSize.Small,
        )
        Spacer(Modifier.height(OhTheme.space.s3))
        androidx.compose.material3.Text(
            text = stringResource(R.string.nutrition_targets_note),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    Spacer(Modifier.height(OhTheme.space.s2))
    OhTextField(
        value = value,
        onValueChange = { onChange(it.filter { c -> c.isDigit() || c == '.' }) },
        placeholder = label,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        contentDescription = label,
    )
}

private fun plain(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
