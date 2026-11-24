package app.openhealth.ui.screens.nutrition

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.openhealth.core.nutrition.Nutrient
import app.openhealth.ui.R
import app.openhealth.ui.components.OhChip
import app.openhealth.ui.components.OhChipStrip
import app.openhealth.ui.components.OhIconButton
import app.openhealth.ui.components.OhModalSheet
import app.openhealth.ui.components.OhRow
import app.openhealth.ui.components.OhSearchField
import app.openhealth.ui.icons.OhIcons
import app.openhealth.ui.screens.common.OhEmptyState
import app.openhealth.ui.screens.common.OhSectionTitle
import app.openhealth.ui.theme.OhTheme

/**
 * The food search sheet (`DESIGN.md` §14.2 Search): a sticky field, recent and frequent
 * foods first, results grouped by source, and the create and quick-add escapes.
 */
@Composable
fun FoodSearchSheet(
    model: FoodSearchModel,
    showKcal: Boolean,
    onDismiss: () -> Unit,
    onQuery: (String) -> Unit,
    onSelect: (FoodChoiceModel) -> Unit,
    onTemplate: (String) -> Unit,
    onMeal: (MealChoiceModel) -> Unit,
    onScan: () -> Unit,
    onCreateFood: () -> Unit,
    onCreateRecipe: () -> Unit,
    onQuickCalories: () -> Unit,
) {
    var query by remember(model.query) { mutableStateOf(model.query) }
    OhModalSheet(
        visible = true,
        onDismiss = onDismiss,
        title = stringResource(R.string.nutrition_search_title),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OhSearchField(
                value = query,
                onValueChange = {
                    query = it
                    onQuery(it)
                },
                placeholder = stringResource(R.string.nutrition_search_placeholder),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(OhTheme.space.s2))
            OhIconButton(
                icon = OhIcons.scan,
                contentDescription = stringResource(R.string.nutrition_scan),
                onClick = onScan,
            )
        }
        Spacer(Modifier.height(OhTheme.space.s3))
        OhChipStrip {
            model.meals.forEach { meal ->
                val selected = meal.id == model.meal.id && meal.slot == model.meal.slot
                OhChip(
                    text = mealLabel(meal.slot, meal.label),
                    selected = selected,
                    onClick = { onMeal(meal) },
                )
            }
        }
        Spacer(Modifier.height(OhTheme.space.s4))
        if (query.isBlank()) {
            if (model.templates.isNotEmpty()) {
                OhSectionTitle(text = stringResource(R.string.nutrition_search_templates))
                model.templates.forEach { template ->
                    OhRow(
                        title = template.name,
                        subtitle = if (showKcal && template.kcal != null) {
                            energyText(template.kcal)
                        } else {
                            null
                        },
                        leadingIcon = OhIcons.check,
                        onClick = { onTemplate(template.id) },
                    )
                }
                Spacer(Modifier.height(OhTheme.space.s4))
            }
            if (model.recents.isNotEmpty()) {
                OhSectionTitle(text = stringResource(R.string.nutrition_search_recents))
                model.recents.forEach { row -> FoodRow(row, showKcal, onSelect) }
                Spacer(Modifier.height(OhTheme.space.s4))
            }
            if (model.frequents.isNotEmpty()) {
                OhSectionTitle(text = stringResource(R.string.nutrition_search_frequent))
                model.frequents.forEach { row -> FoodRow(row, showKcal, onSelect) }
            }
            if (model.recents.isEmpty() && model.frequents.isEmpty()) {
                OhEmptyState(
                    title = stringResource(R.string.nutrition_search_none),
                    body = stringResource(R.string.nutrition_create_food),
                    glyph = OhIcons.search,
                )
            }
        } else {
            if (model.local.isNotEmpty()) {
                OhSectionTitle(text = stringResource(R.string.nutrition_search_your_foods))
                model.local.forEach { row -> FoodRow(row, showKcal, onSelect) }
                Spacer(Modifier.height(OhTheme.space.s4))
            }
            if (model.bundled.isNotEmpty()) {
                OhSectionTitle(text = stringResource(R.string.nutrition_search_bundled))
                model.bundled.forEach { row -> FoodRow(row, showKcal, onSelect) }
            }
            if (model.local.isEmpty() && model.bundled.isEmpty()) {
                OhEmptyState(
                    title = stringResource(R.string.nutrition_search_none),
                    body = stringResource(R.string.nutrition_create_food),
                    glyph = OhIcons.search,
                )
            }
        }
        Spacer(Modifier.height(OhTheme.space.s4))
        OhRow(
            title = stringResource(R.string.nutrition_create_food),
            leadingIcon = OhIcons.add,
            onClick = onCreateFood,
        )
        OhRow(
            title = stringResource(R.string.nutrition_create_recipe),
            leadingIcon = OhIcons.add,
            showDivider = true,
            onClick = onCreateRecipe,
        )
        OhRow(
            title = stringResource(R.string.nutrition_quick_calories),
            leadingIcon = OhIcons.edit,
            showDivider = true,
            onClick = onQuickCalories,
        )
        if (query.isNotBlank()) {
            Spacer(Modifier.height(OhTheme.space.s2))
            Text(
                text = stringResource(R.string.nutrition_offline_note, model.indexVersion),
                style = OhTheme.type.caption,
                color = OhTheme.colors.label3,
            )
        }
    }
}

@Composable
private fun FoodRow(row: FoodRowModel, showKcal: Boolean, onSelect: (FoodChoiceModel) -> Unit) {
    val food = row.food
    val kcal = food.per100g[Nutrient.ENERGY]
    OhRow(
        title = food.name,
        subtitle = buildString {
            food.brand?.let {
                append(it)
                append(" · ")
            }
            append(sourceLabel(food.sourceKey))
            if (showKcal && kcal != null) {
                append(" · ")
                append(energyText(kcal))
                append(" ")
                append(stringResource(R.string.nutrition_portion_per_100g))
            }
        },
        leadingIcon = if (food.isRecipe) OhIcons.check else OhIcons.search,
        showChevron = true,
        showDivider = true,
        onClick = { onSelect(food) },
    )
}
