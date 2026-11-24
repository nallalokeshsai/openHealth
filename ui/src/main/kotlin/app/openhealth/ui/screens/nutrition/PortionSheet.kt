@file:Suppress("MagicNumber")

package app.openhealth.ui.screens.nutrition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.openhealth.core.nutrition.Nutrient
import app.openhealth.core.nutrition.PortionMath
import app.openhealth.ui.R
import app.openhealth.ui.components.OhButton
import app.openhealth.ui.components.OhButtonSize
import app.openhealth.ui.components.OhButtonVariant
import app.openhealth.ui.components.OhChip
import app.openhealth.ui.components.OhChipStrip
import app.openhealth.ui.components.OhModalSheet
import app.openhealth.ui.components.OhRow
import app.openhealth.ui.components.OhStepper
import app.openhealth.ui.screens.common.number
import app.openhealth.ui.theme.OhTheme

/** The portion sheet (`DESIGN.md` §14.3): a stepper, the food's portions, and Log. */
@Composable
fun PortionSheet(
    model: PortionModel,
    showKcal: Boolean,
    onDismiss: () -> Unit,
    onMeal: (MealChoiceModel) -> Unit,
    onLog: (grams: Double, servings: Double, servingLabel: String?) -> Unit,
    onEditRecipe: () -> Unit = {},
) {
    var grams by remember(model.food.id, model.grams) { mutableStateOf(model.grams) }
    var servings by remember(model.food.id, model.servings) { mutableStateOf(model.servings) }
    var selectedPortion by remember(model.food.id) {
        mutableStateOf(model.food.portions.firstOrNull())
    }
    val totals = remember(model.food.id, grams) {
        PortionMath.forGrams(model.food.per100g, grams)
    }
    OhModalSheet(
        visible = true,
        onDismiss = onDismiss,
        title = model.food.name,
        primaryLabel = stringResource(
            if (model.entryId == null) {
                R.string.nutrition_portion_log
            } else {
                R.string.nutrition_portion_update
            },
        ),
        primaryEnabled = grams > 0.0,
        onPrimary = {
            onLog(
                grams,
                servings,
                selectedPortion?.label ?: model.food.defaultServingLabel,
            )
        },
        onCancel = onDismiss,
    ) {
        Text(
            text = buildString {
                model.food.brand?.let {
                    append(it)
                    append(" · ")
                }
                append(sourceLabel(model.food.sourceKey))
            },
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
        Spacer(Modifier.height(OhTheme.space.s3))
        OhStepper(
            value = number(grams),
            unit = stringResource(R.string.unit_g),
            onDecrement = {
                grams = (grams - 10.0).coerceAtLeast(1.0)
                servings = 1.0
            },
            onIncrement = {
                grams += 10.0
                servings = 1.0
            },
        )
        if (model.food.portions.isNotEmpty()) {
            Spacer(Modifier.height(OhTheme.space.s3))
            OhChipStrip {
                model.food.portions.forEach { portion ->
                    OhChip(
                        text = "${portion.label} (${number(portion.grams)} g)",
                        selected = selectedPortion?.label == portion.label,
                        onClick = {
                            selectedPortion = portion
                            grams = portion.grams
                            servings = 1.0
                        },
                    )
                }
            }
        }
        if (selectedPortion != null) {
            Spacer(Modifier.height(OhTheme.space.s3))
            OhStepper(
                value = number(servings, 2),
                unit = stringResource(R.string.nutrition_portion_servings),
                onDecrement = {
                    servings = (servings - 0.5).coerceAtLeast(0.5)
                    grams = selectedPortion!!.grams * servings
                },
                onIncrement = {
                    servings += 0.5
                    grams = selectedPortion!!.grams * servings
                },
            )
        }
        Spacer(Modifier.height(OhTheme.space.s4))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OhTheme.space.s5),
        ) {
            totals[Nutrient.PROTEIN]?.let {
                Text(
                    text = "${stringResource(R.string.nutrition_macro_protein)} " +
                        stringResource(R.string.nutrition_macro_value, number(it, 1)),
                    style = OhTheme.type.footnote,
                    color = OhTheme.colors.label2,
                )
            }
            totals[Nutrient.CARBS]?.let {
                Text(
                    text = "${stringResource(R.string.nutrition_macro_carbs)} " +
                        stringResource(R.string.nutrition_macro_value, number(it, 1)),
                    style = OhTheme.type.footnote,
                    color = OhTheme.colors.label2,
                )
            }
            totals[Nutrient.FAT]?.let {
                Text(
                    text = "${stringResource(R.string.nutrition_macro_fat)} " +
                        stringResource(R.string.nutrition_macro_value, number(it, 1)),
                    style = OhTheme.type.footnote,
                    color = OhTheme.colors.label2,
                )
            }
        }
        if (showKcal) {
            totals[Nutrient.ENERGY]?.let {
                Text(
                    text = energyText(it),
                    style = OhTheme.type.title3,
                    color = OhTheme.colors.label,
                )
            }
        }
        Spacer(Modifier.height(OhTheme.space.s4))
        Text(
            text = stringResource(R.string.nutrition_portion_meal),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
        Spacer(Modifier.height(OhTheme.space.s2))
        OhChipStrip {
            model.meals.forEach { meal ->
                OhChip(
                    text = mealLabel(meal.slot, meal.label),
                    selected = meal.id == model.meal.id && meal.slot == model.meal.slot,
                    onClick = { onMeal(meal) },
                )
            }
        }
        val allergenText = model.allergens.map { allergenLabel(it) }.joinToString(", ")
        if (allergenText.isNotBlank()) {
            Spacer(Modifier.height(OhTheme.space.s4))
            Text(
                text = stringResource(R.string.nutrition_portion_allergen, allergenText),
                style = OhTheme.type.footnote,
                color = OhTheme.colors.orangeInk,
            )
        } else if (!model.hasAllergenData && model.food.allergens.isEmpty()) {
            Spacer(Modifier.height(OhTheme.space.s4))
            Text(
                text = stringResource(R.string.nutrition_portion_allergen_unknown),
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
            )
        }
        model.dietPatternKey?.let { key ->
            if (key == "vegan" || key == "vegetarian") {
                val name = dietPatternLabel(key)
                Spacer(Modifier.height(OhTheme.space.s2))
                Text(
                    text = when (model.dietStatus) {
                        DietStatus.MATCHES ->
                            stringResource(R.string.nutrition_portion_diet_match, name)

                        DietStatus.DOES_NOT_MATCH ->
                            stringResource(R.string.nutrition_portion_diet_no_match, name)

                        DietStatus.UNKNOWN ->
                            stringResource(R.string.nutrition_portion_diet_unknown, name)
                    },
                    style = OhTheme.type.footnote,
                    color = OhTheme.colors.label2,
                )
            }
        }
        if (showKcal) {
            Spacer(Modifier.height(OhTheme.space.s4))
            OhRow(
                title = stringResource(R.string.nutrition_portion_per_100g),
                value = totals[Nutrient.ENERGY]?.let { energyText(it / grams * 100.0) },
            )
        }
        if (model.food.isRecipe) {
            Spacer(Modifier.height(OhTheme.space.s2))
            OhButton(
                text = stringResource(R.string.nutrition_edit_recipe),
                onClick = onEditRecipe,
                variant = OhButtonVariant.Quiet,
                size = OhButtonSize.Small,
            )
        }
    }
}
