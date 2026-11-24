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
import app.openhealth.ui.components.OhModalSheet
import app.openhealth.ui.components.OhTextField
import app.openhealth.ui.theme.OhTheme

/** Quick calories and macros when the food is not in any database (`pr.md` §6.3). */
@Composable
fun QuickCaloriesSheet(
    showKcal: Boolean,
    onDismiss: () -> Unit,
    onSave: (kcal: Double, protein: Double, carbs: Double, fat: Double, fibre: Double) -> Unit,
) {
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var fibre by remember { mutableStateOf("") }
    val any = listOf(kcal, protein, carbs, fat, fibre).any {
        it.toDoubleOrNull()?.let { v -> v > 0 } ==
            true
    }
    OhModalSheet(
        visible = true,
        onDismiss = onDismiss,
        title = stringResource(R.string.nutrition_quick_calories),
        primaryLabel = stringResource(R.string.nutrition_quick_save),
        primaryEnabled = any,
        onPrimary = {
            onSave(
                kcal.toDoubleOrNull() ?: 0.0,
                protein.toDoubleOrNull() ?: 0.0,
                carbs.toDoubleOrNull() ?: 0.0,
                fat.toDoubleOrNull() ?: 0.0,
                fibre.toDoubleOrNull() ?: 0.0,
            )
        },
        onCancel = onDismiss,
    ) {
        if (showKcal) {
            Field(stringResource(R.string.nutrition_quick_kcal), kcal) { kcal = it }
        }
        Field(stringResource(R.string.nutrition_quick_protein), protein) { protein = it }
        Field(stringResource(R.string.nutrition_quick_carbs), carbs) { carbs = it }
        Field(stringResource(R.string.nutrition_quick_fat), fat) { fat = it }
        Field(stringResource(R.string.nutrition_quick_fibre), fibre) { fibre = it }
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
