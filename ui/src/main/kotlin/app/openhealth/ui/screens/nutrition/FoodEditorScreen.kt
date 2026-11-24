@file:Suppress("MagicNumber")

package app.openhealth.ui.screens.nutrition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import app.openhealth.core.nutrition.Nutrient
import app.openhealth.core.nutrition.NutrientGroup
import app.openhealth.core.nutrition.PortionMath
import app.openhealth.core.nutrition.RecipeMath
import app.openhealth.ui.R
import app.openhealth.ui.components.OhButton
import app.openhealth.ui.components.OhButtonSize
import app.openhealth.ui.components.OhButtonVariant
import app.openhealth.ui.components.OhIconButton
import app.openhealth.ui.components.OhStepper
import app.openhealth.ui.components.OhTextField
import app.openhealth.ui.icons.OhIcons
import app.openhealth.ui.screens.common.OhFocusedScreen
import app.openhealth.ui.screens.common.OhPageHeader
import app.openhealth.ui.screens.common.OhSectionTitle
import app.openhealth.ui.screens.common.number
import app.openhealth.ui.theme.OhTheme

/** The custom food and recipe editor (`pr.md` §6.3). All values are per 100 g. */
@Composable
fun FoodEditorScreen(
    model: FoodEditorModel,
    showKcal: Boolean,
    onBack: () -> Unit,
    onSave: (FoodSaveModel) -> Unit,
    onAddIngredient: () -> Unit,
    onIngredientGrams: (index: Int, grams: Double) -> Unit,
    onRemoveIngredient: (index: Int) -> Unit,
    onDuplicate: () -> Unit,
    onLog: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by rememberSaveable(model.id) { mutableStateOf(model.name) }
    var brand by rememberSaveable(model.id) { mutableStateOf(model.brand) }
    var barcode by rememberSaveable(model.id) { mutableStateOf(model.barcode) }
    var servingLabel by rememberSaveable(model.id) { mutableStateOf(model.servingLabel) }
    var servingGrams by rememberSaveable(model.id) { mutableStateOf(model.servingGrams) }
    var servings by rememberSaveable(model.id) { mutableStateOf(model.servings) }
    val nutrientValues = remember(model.id) {
        mutableStateMapOf<Nutrient, String>().apply {
            Nutrient.entries.forEach { put(it, model.nutrients[it].orEmpty()) }
        }
    }
    var errorRes by remember { mutableStateOf<Int?>(null) }
    val parsedNutrients = Nutrient.entries.mapNotNull { nutrient ->
        nutrientValues[nutrient]?.toDoubleOrNull()?.let { nutrient to it }
    }.toMap()
    val recipePer100g = remember(model.ingredients) {
        RecipeMath.per100g(
            model.ingredients.map {
                RecipeMath.Ingredient(it.grams, it.per100g)
            },
        )
    }
    val shownPer100g = if (model.isRecipe) recipePer100g else parsedNutrients
    val totalGrams = model.ingredients.sumOf { it.grams }
    val servingCount = servings.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val perServingGrams = if (model.isRecipe && totalGrams > 0.0) {
        RecipeMath.servingGrams(totalGrams, servingCount)
    } else {
        null
    }
    OhFocusedScreen(modifier = modifier) {
        OhPageHeader(
            title = stringResource(
                when {
                    model.isRecipe && model.id == null -> R.string.nutrition_recipe_new
                    model.isRecipe -> R.string.nutrition_recipe_edit
                    model.id == null -> R.string.nutrition_food_new
                    else -> R.string.nutrition_food_edit
                },
            ),
            onBack = onBack,
        )
        FieldLabel(stringResource(R.string.nutrition_food_name))
        OhTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = stringResource(R.string.nutrition_food_name),
        )
        Spacer(Modifier.height(OhTheme.space.s2))
        FieldLabel(stringResource(R.string.nutrition_food_brand))
        OhTextField(
            value = brand,
            onValueChange = { brand = it },
            placeholder = stringResource(R.string.nutrition_food_brand),
        )
        Spacer(Modifier.height(OhTheme.space.s2))
        FieldLabel(stringResource(R.string.nutrition_food_barcode))
        OhTextField(
            value = barcode,
            onValueChange = { barcode = it.filter(Char::isDigit) },
            placeholder = stringResource(R.string.nutrition_food_barcode),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Spacer(Modifier.height(OhTheme.space.s3))
        Row(horizontalArrangement = Arrangement.spacedBy(OhTheme.space.s2)) {
            FieldLabel(
                text = stringResource(R.string.nutrition_food_serving_label),
                modifier = Modifier.weight(1f),
            )
            FieldLabel(
                text = stringResource(R.string.nutrition_food_serving_grams),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(OhTheme.space.s2)) {
            OhTextField(
                value = servingLabel,
                onValueChange = { servingLabel = it },
                placeholder = stringResource(R.string.nutrition_food_serving_label),
                modifier = Modifier.weight(1f),
            )
            OhTextField(
                value = servingGrams,
                onValueChange = { servingGrams = it.filter { c -> c.isDigit() || c == '.' } },
                placeholder = stringResource(R.string.nutrition_food_serving_grams),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }
        if (model.isRecipe) {
            Spacer(Modifier.height(OhTheme.space.s4))
            FieldLabel(stringResource(R.string.nutrition_food_recipe_servings))
            OhTextField(
                value = servings,
                onValueChange = { servings = it.filter(Char::isDigit) },
                placeholder = stringResource(R.string.nutrition_food_recipe_servings),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Spacer(Modifier.height(OhTheme.space.s3))
            OhSectionTitle(text = stringResource(R.string.nutrition_food_ingredients))
            if (model.ingredients.isEmpty()) {
                Text(
                    text = stringResource(R.string.nutrition_food_no_ingredients),
                    style = OhTheme.type.body,
                    color = OhTheme.colors.label2,
                )
            }
            model.ingredients.forEachIndexed { index, ingredient ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = ingredient.name,
                            style = OhTheme.type.body,
                            color = OhTheme.colors.label,
                        )
                    }
                    OhStepper(
                        value = number(ingredient.grams),
                        unit = stringResource(R.string.unit_g),
                        onDecrement = {
                            onIngredientGrams(index, (ingredient.grams - 10.0).coerceAtLeast(1.0))
                        },
                        onIncrement = { onIngredientGrams(index, ingredient.grams + 10.0) },
                    )
                    OhIconButton(
                        icon = OhIcons.delete,
                        contentDescription = stringResource(R.string.nutrition_ingredient_delete),
                        onClick = { onRemoveIngredient(index) },
                    )
                }
            }
            Spacer(Modifier.height(OhTheme.space.s3))
            OhButton(
                text = stringResource(R.string.nutrition_food_add_ingredient),
                onClick = onAddIngredient,
                variant = OhButtonVariant.Secondary,
                size = OhButtonSize.Small,
                leadingIcon = OhIcons.add,
            )
            Spacer(Modifier.height(OhTheme.space.s4))
            if (showKcal) {
                shownPer100g[Nutrient.ENERGY]?.let {
                    Text(
                        text = energyText(it),
                        style = OhTheme.type.title3,
                        color = OhTheme.colors.label,
                    )
                    Text(
                        text = stringResource(R.string.nutrition_portion_per_100g),
                        style = OhTheme.type.caption,
                        color = OhTheme.colors.label3,
                    )
                }
            }
            perServingGrams?.let {
                Text(
                    text = stringResource(R.string.nutrition_recipe_portions, servingCount),
                    style = OhTheme.type.footnote,
                    color = OhTheme.colors.label2,
                )
                if (showKcal) {
                    val perServing = PortionMath.forGrams(shownPer100g, it)
                    perServing[Nutrient.ENERGY]?.let { kcal ->
                        Text(
                            text = stringResource(
                                R.string.nutrition_recipe_serving_kcal,
                                number(kcal),
                            ),
                            style = OhTheme.type.footnote,
                            color = OhTheme.colors.label2,
                        )
                    }
                }
            }
        } else {
            Spacer(Modifier.height(OhTheme.space.s4))
            OhSectionTitle(text = stringResource(R.string.nutrition_food_nutrients))
            Text(
                text = stringResource(R.string.nutrition_food_per_100g),
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
            )
            listOf(
                NutrientGroup.MACRO,
                NutrientGroup.MINERAL,
                NutrientGroup.VITAMIN,
                NutrientGroup.OTHER,
            ).forEach { group ->
                Spacer(Modifier.height(OhTheme.space.s2))
                Text(
                    text = when (group) {
                        NutrientGroup.MACRO -> stringResource(R.string.nutrition_food_nutrients)
                        NutrientGroup.MINERAL ->
                            stringResource(R.string.nutrition_micros_minerals)

                        NutrientGroup.VITAMIN ->
                            stringResource(R.string.nutrition_micros_vitamins)

                        else -> stringResource(R.string.nutrition_micros_other)
                    },
                    style = OhTheme.type.footnote,
                    color = OhTheme.colors.label2,
                )
                Nutrient.entries
                    .filter { it.group == group }
                    .filter { showKcal || it != Nutrient.ENERGY }
                    .forEach { nutrient ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${nutrientLabel(nutrient)} (${unitLabel(nutrient.unit)})",
                                style = OhTheme.type.footnote,
                                color = OhTheme.colors.label2,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(OhTheme.space.s2))
                            OhTextField(
                                value = nutrientValues[nutrient].orEmpty(),
                                onValueChange = { nutrientValues[nutrient] = it },
                                placeholder = "",
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Decimal,
                                ),
                                contentDescription = nutrientLabel(nutrient),
                                modifier = Modifier.width(OhTheme.dims.nutritionField),
                            )
                        }
                    }
            }
            Spacer(Modifier.height(OhTheme.space.s4))
            val preview = parsedNutrients[Nutrient.ENERGY]
            if (showKcal && preview != null) {
                Text(
                    text = energyText(preview),
                    style = OhTheme.type.title3,
                    color = OhTheme.colors.label,
                )
                Text(
                    text = stringResource(R.string.nutrition_portion_per_100g),
                    style = OhTheme.type.caption,
                    color = OhTheme.colors.label3,
                )
            }
        }
        errorRes?.let {
            Spacer(Modifier.height(OhTheme.space.s3))
            Text(
                text = stringResource(it),
                style = OhTheme.type.footnote,
                color = OhTheme.colors.orangeInk,
            )
        }
        Spacer(Modifier.height(OhTheme.space.s5))
        OhButton(
            text = stringResource(R.string.nutrition_food_save),
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val nutrients = parseNutrients(nutrientValues)
                when {
                    name.isBlank() -> errorRes = R.string.nutrition_food_error_name
                    model.isRecipe && model.ingredients.isEmpty() ->
                        errorRes = R.string.nutrition_food_error_ingredients

                    !model.isRecipe && nutrients.values.all { it == 0.0 } ->
                        errorRes = R.string.nutrition_food_error_nutrients

                    else -> {
                        errorRes = null
                        onSave(
                            FoodSaveModel(
                                id = model.id,
                                isRecipe = model.isRecipe,
                                name = name.trim(),
                                brand = brand.trim().takeIf { it.isNotBlank() },
                                barcode = barcode.trim().takeIf { it.isNotBlank() },
                                servingLabel = servingLabel.trim().takeIf { it.isNotBlank() },
                                servingGrams = servingGrams.toDoubleOrNull(),
                                nutrients = nutrients,
                                ingredients = model.ingredients.map {
                                    IngredientSaveModel(
                                        foodId = it.foodId,
                                        name = it.name,
                                        grams = it.grams,
                                        per100g = it.per100g,
                                    )
                                },
                                servings = servingCount,
                            ),
                        )
                    }
                }
            },
        )
        if (model.isRecipe && model.id != null) {
            Spacer(Modifier.height(OhTheme.space.s2))
            OhButton(
                text = stringResource(R.string.nutrition_recipe_duplicate),
                onClick = onDuplicate,
                variant = OhButtonVariant.Secondary,
                size = OhButtonSize.Small,
            )
            Spacer(Modifier.height(OhTheme.space.s2))
            OhButton(
                text = stringResource(R.string.nutrition_recipe_log),
                onClick = { onLog(model.id) },
                variant = OhButtonVariant.Secondary,
                size = OhButtonSize.Small,
            )
        }
    }
}

private fun parseNutrients(values: Map<Nutrient, String>): Map<Nutrient, Double> =
    values.mapNotNull { (nutrient, text) ->
        text.toDoubleOrNull()?.takeIf { it >= 0.0 }?.let { nutrient to it }
    }.toMap()

@Composable
private fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = OhTheme.type.footnote,
        color = OhTheme.colors.label2,
        modifier = modifier,
    )
}
