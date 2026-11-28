@file:Suppress("MagicNumber")

package app.openhealth.ui.screens.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import app.openhealth.ui.R
import app.openhealth.ui.components.ChartTableModel
import app.openhealth.ui.components.ChartTableSheet
import app.openhealth.ui.components.OhBarRow
import app.openhealth.ui.components.OhButton
import app.openhealth.ui.components.OhButtonSize
import app.openhealth.ui.components.OhButtonVariant
import app.openhealth.ui.components.OhCard
import app.openhealth.ui.components.OhCardHeader
import app.openhealth.ui.components.OhDialog
import app.openhealth.ui.components.OhIconButton
import app.openhealth.ui.components.OhInsetGroup
import app.openhealth.ui.components.OhMediaRow
import app.openhealth.ui.components.OhModalSheet
import app.openhealth.ui.components.OhRing
import app.openhealth.ui.components.OhRow
import app.openhealth.ui.components.OhTextField
import app.openhealth.ui.components.ohSurfacePressable
import app.openhealth.ui.components.rememberInteraction
import app.openhealth.ui.icons.OhGlyphIcons
import app.openhealth.ui.icons.OhIcon
import app.openhealth.ui.icons.OhIcons
import app.openhealth.ui.icons.OhObjectIcons
import app.openhealth.ui.screens.common.OhScreen
import app.openhealth.ui.screens.common.OhSectionTitle
import app.openhealth.ui.screens.common.number
import app.openhealth.ui.screens.common.observedLocale
import app.openhealth.ui.screens.common.shortDate
import app.openhealth.ui.theme.OhTheme
import app.openhealth.ui.theme.OhWeight
import app.openhealth.ui.theme.tabular
import java.time.format.DateTimeFormatter
import kotlinx.datetime.LocalDate

/**
 * The Nutrition root (`DESIGN.md` §14.2): a seven-day strip, the energy card with its
 * ring and macro bars, water as its fifth row, then the four meal sections. The scan
 * action is a header button, never a floating camera over the list.
 */
@Composable
fun NutritionRootScreen(
    model: NutritionModel,
    onSelectDay: (LocalDate) -> Unit,
    onScan: () -> Unit,
    onTargets: () -> Unit,
    onMicros: () -> Unit,
    onAddFood: (MealSectionModel) -> Unit,
    onOpenEntry: (String) -> Unit,
    onDeleteEntry: (String) -> Unit,
    onAddMeal: (String) -> Unit,
    onWater: () -> Unit,
    onLogWater: (String) -> Unit,
    onFasting: () -> Unit,
    onCopyMeal: (MealSectionModel) -> Unit,
    onSaveTemplate: (MealSectionModel, String) -> Unit,
    onCopyDay: () -> Unit,
    onOpenSettings: () -> Unit,
    moduleOff: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (moduleOff) {
        app.openhealth.ui.screens.roots.ModuleOffRoot(
            name = stringResource(R.string.nav_nutrition),
            onOpenSettings = onOpenSettings,
            modifier = modifier,
        )
        return
    }
    var actionsFor by remember { mutableStateOf<MealSectionModel?>(null) }
    var templateFor by remember { mutableStateOf<MealSectionModel?>(null) }
    var templateName by rememberSaveable { mutableStateOf("") }
    var addMealOpen by rememberSaveable { mutableStateOf(false) }
    var newMealName by rememberSaveable { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    var provenance by rememberSaveable { mutableStateOf(false) }
    OhScreen(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = OhTheme.space.s6),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.nav_nutrition),
                style = OhTheme.type.large.copy(fontWeight = OhWeight.bold),
                color = OhTheme.colors.label,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            OhIconButton(
                icon = OhIcons.scan,
                contentDescription = stringResource(R.string.nutrition_scan),
                onClick = onScan,
            )
        }
        Spacer(Modifier.height(OhTheme.space.s5))
        DateStrip(model, onSelectDay)
        Spacer(Modifier.height(OhTheme.space.s5))
        if (model.energy != null) {
            EnergyCard(model, onTargets, onWater, onLogWater, onShowProvenance = {
                provenance = true
            })
        } else {
            model.water?.let { water ->
                OhCard {
                    WaterRow(water, onWater, onLogWater)
                }
            }
        }
        if (model.weightNeutral) {
            Spacer(Modifier.height(OhTheme.space.s3))
            Text(
                text = stringResource(R.string.nutrition_weight_neutral_note),
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
            )
        }
        Spacer(Modifier.height(OhTheme.space.s5))
        model.meals.forEach { section ->
            MealSection(
                section = section,
                showKcal = !model.weightNeutral,
                onAdd = { onAddFood(section) },
                onOpenEntry = onOpenEntry,
                onDelete = { confirmDelete = it },
                onActions = { actionsFor = section },
            )
            Spacer(Modifier.height(OhTheme.space.s5))
        }
        OhButton(
            text = stringResource(R.string.nutrition_add_meal),
            onClick = { addMealOpen = true },
            variant = OhButtonVariant.Quiet,
            size = OhButtonSize.Small,
            leadingIcon = OhIcons.add,
        )
        Spacer(Modifier.height(OhTheme.space.s4))
        OhInsetGroup {
            OhRow(
                title = stringResource(R.string.nutrition_micros),
                subtitle = stringResource(R.string.nutrition_micros_note),
                showChevron = true,
                onClick = onMicros,
            )
        }
        model.fast?.let { fast ->
            Spacer(Modifier.height(OhTheme.space.s3))
            FastingRow(fast, onFasting)
        }
        Spacer(Modifier.height(OhTheme.space.s3))
        OhInsetGroup {
            OhRow(
                title = stringResource(R.string.nutrition_targets),
                subtitle = if (model.targetsSet) {
                    null
                } else {
                    stringResource(R.string.nutrition_energy_no_target)
                },
                showChevron = true,
                onClick = onTargets,
            )
        }
        if (model.canCopyDay) {
            Spacer(Modifier.height(OhTheme.space.s3))
            OhButton(
                text = stringResource(R.string.nutrition_copy_day),
                onClick = onCopyDay,
                variant = OhButtonVariant.Secondary,
                size = OhButtonSize.Small,
            )
        }
        Spacer(Modifier.height(OhTheme.space.s6))
        Text(
            text = stringResource(R.string.nutrition_offline_note, US_OFFLINE_NOTE),
            style = OhTheme.type.caption,
            color = OhTheme.colors.label3,
        )
    }
    actionsFor?.let { section ->
        MealActionsSheet(
            section = section,
            canCopy = model.canCopyDay,
            onDismiss = { actionsFor = null },
            onAdd = {
                actionsFor = null
                onAddFood(section)
            },
            onCopy = {
                actionsFor = null
                onCopyMeal(section)
            },
            onTemplate = {
                actionsFor = null
                templateName = ""
                templateFor = section
            },
        )
    }
    templateFor?.let { section ->
        OhModalSheet(
            visible = true,
            onDismiss = { templateFor = null },
            title = stringResource(R.string.nutrition_template_name),
            primaryLabel = stringResource(R.string.nutrition_food_save),
            primaryEnabled = templateName.isNotBlank(),
            onPrimary = {
                onSaveTemplate(section, templateName)
                templateFor = null
            },
            onCancel = { templateFor = null },
        ) {
            OhTextField(
                value = templateName,
                onValueChange = { templateName = it },
                placeholder = stringResource(R.string.nutrition_template_name),
            )
        }
    }
    if (addMealOpen) {
        OhModalSheet(
            visible = true,
            onDismiss = { addMealOpen = false },
            title = stringResource(R.string.nutrition_add_meal),
            primaryLabel = stringResource(R.string.nutrition_food_save),
            primaryEnabled = newMealName.isNotBlank(),
            onPrimary = {
                onAddMeal(newMealName)
                addMealOpen = false
            },
            onCancel = { addMealOpen = false },
        ) {
            OhTextField(
                value = newMealName,
                onValueChange = { newMealName = it },
                placeholder = stringResource(R.string.nutrition_meal_name),
            )
        }
    }
    if (provenance) {
        DayProvenanceSheet(
            model = model,
            onDismiss = { provenance = false },
            onOpenEntry = {
                provenance = false
                onOpenEntry(it)
            },
        )
    }
    confirmDelete?.let { id ->
        OhDialog(
            visible = true,
            onDismiss = { confirmDelete = null },
            title = stringResource(R.string.nutrition_delete_entry),
            message = stringResource(R.string.nutrition_delete_entry_body),
            primaryLabel = stringResource(R.string.nutrition_delete_entry),
            onPrimary = {
                confirmDelete = null
                onDeleteEntry(id)
            },
            cancelLabel = stringResource(R.string.oh_back),
            onCancel = { confirmDelete = null },
            destructive = true,
        )
    }
}

private const val US_OFFLINE_NOTE = "USDA FoodData Central (CC0)"

@Composable
private fun dateEyebrow(model: NutritionModel): String = if (model.selectedDate == model.today) {
    stringResource(R.string.nutrition_today)
} else {
    shortDate(model.selectedDate)
}

@Composable
private fun DateStrip(model: NutritionModel, onSelectDay: (LocalDate) -> Unit) {
    // Time reads left-to-right even in RTL (DESIGN.md §11.5), as the Today week strip.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OhTheme.space.s0),
        ) {
            model.week.forEach { day ->
                val selected = day.isSelected
                val interaction = rememberInteraction()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = OhTheme.dims.nutritionDayCell)
                        .clip(
                            androidx.compose.foundation.shape.RoundedCornerShape(
                                OhTheme.radius.base,
                            ),
                        )
                        .background(
                            when {
                                selected -> OhTheme.colors.accSoft
                                else -> OhTheme.colors.bg
                            },
                        )
                        .ohSurfacePressable(
                            interactionSource = interaction,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(
                                OhTheme.radius.base,
                            ),
                            enabled = true,
                            onClick = { onSelectDay(day.date) },
                        )
                        .padding(vertical = OhTheme.space.s0),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = weekdayShort(day.date),
                        style = OhTheme.type.caption2,
                        color = if (selected) OhTheme.colors.accInk else OhTheme.colors.label2,
                    )
                    Text(
                        text = day.date.dayOfMonth.toString(),
                        style = OhTheme.type.subhead.tabular(),
                        color = when {
                            day.isToday -> OhTheme.colors.onAcc
                            selected -> OhTheme.colors.accInk
                            else -> OhTheme.colors.label
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .then(
                                if (day.isToday) {
                                    Modifier.background(
                                        OhTheme.colors.acc,
                                    ).padding(horizontal = OhTheme.space.s1)
                                } else {
                                    Modifier
                                },
                            ),
                    )
                    Box(
                        modifier = Modifier
                            .size(OhTheme.space.s0)
                            .clip(CircleShape)
                            .background(
                                if (day.hasEntries) OhTheme.colors.acc else OhTheme.colors.surface,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun weekdayShort(date: LocalDate): String {
    val javaDate = java.time.LocalDate.of(date.year, date.monthNumber, date.dayOfMonth)
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(observedLocale(), "EEE")
    return javaDate.format(
        DateTimeFormatter.ofPattern(pattern, observedLocale()),
    )
}

@Composable
private fun EnergyCard(
    model: NutritionModel,
    onTargets: () -> Unit,
    onWater: () -> Unit,
    onLogWater: (String) -> Unit,
    onShowProvenance: () -> Unit,
) {
    val energy = model.energy ?: return
    var showTable by rememberSaveable { mutableStateOf(false) }
    OhCard {
        OhCardHeader(
            eyebrow = dateEyebrow(model),
            title = stringResource(R.string.today_energy_title),
            glyph = OhGlyphIcons.flame,
            trailing = {
                OhButton(
                    text = stringResource(R.string.nutrition_targets),
                    onClick = onTargets,
                    variant = OhButtonVariant.Quiet,
                    size = OhButtonSize.ExtraSmall,
                )
            },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = OhTheme.space.s4)
                .ohSurfacePressable(
                    interactionSource = rememberInteraction(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(
                        OhTheme.radius.sm,
                    ),
                    enabled = true,
                    onClick = onShowProvenance,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val remaining = energy.remainingKcal
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OhRing(
                    progress = if (energy.kcalTarget != null && energy.kcalTarget > 0.0) {
                        (energy.kcalIn / energy.kcalTarget).toFloat()
                    } else {
                        0f
                    },
                    centre = number(remaining ?: energy.kcalIn),
                    contentDescription = when {
                        remaining != null && remaining >= 0.0 ->
                            stringResource(R.string.nutrition_energy_remaining, number(remaining))

                        remaining != null ->
                            stringResource(R.string.nutrition_energy_over, number(-remaining))

                        else -> energyText(energy.kcalIn)
                    },
                )
                Text(
                    text = when {
                        remaining == null -> stringResource(R.string.nutrition_energy_in_label)
                        remaining < 0.0 -> stringResource(R.string.nutrition_energy_over_label)
                        else -> stringResource(R.string.nutrition_energy_left)
                    },
                    style = OhTheme.type.caption,
                    color = OhTheme.colors.label2,
                )
            }
            Spacer(Modifier.width(OhTheme.space.s6))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(OhTheme.space.s3),
            ) {
                energy.bars.forEach { bar ->
                    OhBarRow(
                        label = macroLabel(bar.key),
                        value = stringResource(
                            R.string.nutrition_macro_value,
                            number(bar.grams),
                        ),
                        target = bar.targetGrams?.let {
                            stringResource(R.string.nutrition_macro_of_target, number(it))
                        },
                        progress = bar.fraction,
                        ink = macroInk(bar.key),
                    )
                }
            }
        }
        Spacer(Modifier.height(OhTheme.space.s3))
        model.water?.let {
            WaterRow(it, onWater, onLogWater)
        }
        Spacer(Modifier.height(OhTheme.space.s3))
        OhRow(
            title = stringResource(R.string.chart_show_table),
            onClick = { showTable = true },
            showChevron = true,
        )
    }
    if (showTable) {
        val measure = stringResource(R.string.nutrition_table_measure)
        val amount = stringResource(R.string.nutrition_table_amount)
        val rows = buildList {
            add(
                listOf(
                    stringResource(R.string.nutrition_table_energy_in),
                    stringResource(R.string.nutrition_table_kcal, number(energy.kcalIn)),
                ),
            )
            add(
                listOf(
                    stringResource(R.string.nutrition_table_target),
                    energy.kcalTarget?.let {
                        stringResource(R.string.nutrition_table_kcal, number(it))
                    } ?: stringResource(R.string.nutrition_energy_no_target),
                ),
            )
            energy.remainingKcal?.let {
                add(
                    listOf(
                        stringResource(R.string.nutrition_table_remaining),
                        stringResource(R.string.nutrition_table_kcal, number(kotlin.math.abs(it))),
                    ),
                )
            }
            energy.bars.forEach { bar ->
                add(
                    listOf(
                        macroLabel(bar.key),
                        stringResource(R.string.nutrition_table_grams, number(bar.grams)),
                    ),
                )
            }
        }
        ChartTableSheet(
            model = ChartTableModel(
                title = stringResource(R.string.nutrition_energy_table_title),
                columns = listOf(measure, amount),
                rows = rows,
            ),
            onDismiss = { showTable = false },
        )
    }
}

@Composable
private fun macroLabel(key: MacroKey): String = stringResource(
    when (key) {
        MacroKey.PROTEIN -> R.string.nutrition_macro_protein
        MacroKey.CARBS -> R.string.nutrition_macro_carbs
        MacroKey.FAT -> R.string.nutrition_macro_fat
        MacroKey.FIBRE -> R.string.nutrition_macro_fibre
    },
)

@Composable
private fun macroInk(key: MacroKey): androidx.compose.ui.graphics.Color = when (key) {
    MacroKey.PROTEIN -> OhTheme.colors.green
    MacroKey.CARBS -> OhTheme.colors.blue
    MacroKey.FAT -> OhTheme.colors.purple
    MacroKey.FIBRE -> OhTheme.colors.teal
}

@Composable
private fun WaterRow(water: WaterCardModel, onWater: () -> Unit, onLogWater: (String) -> Unit) {
    val stack = androidx.compose.ui.platform.LocalDensity.current.fontScale >=
        OhTheme.dims.largeFontScale
    Column {
        OhRow(
            title = stringResource(R.string.nutrition_water),
            subtitle = if (water.targetMl != null) {
                stringResource(
                    R.string.nutrition_water_total,
                    stringResource(R.string.nutrition_ml, number(water.totalMl)),
                    stringResource(R.string.nutrition_ml, number(water.targetMl)),
                )
            } else {
                stringResource(R.string.nutrition_ml, number(water.totalMl))
            },
            leadingIcon = OhObjectIcons.water,
            showChevron = true,
            onClick = onWater,
            trailing = if (stack) {
                null
            } else {
                {
                    water.lastContainer?.let { container ->
                        OhButton(
                            text = stringResource(
                                R.string.nutrition_water_one_tap,
                                stringResource(
                                    R.string.nutrition_ml,
                                    number(container.millilitres),
                                ),
                            ),
                            onClick = { onLogWater(container.id) },
                            variant = OhButtonVariant.Secondary,
                            size = OhButtonSize.ExtraSmall,
                        )
                    }
                }
            },
        )
        if (stack) {
            water.lastContainer?.let { container ->
                Spacer(Modifier.height(OhTheme.space.s2))
                OhButton(
                    text = stringResource(
                        R.string.nutrition_water_one_tap,
                        stringResource(R.string.nutrition_ml, number(container.millilitres)),
                    ),
                    onClick = { onLogWater(container.id) },
                    variant = OhButtonVariant.Secondary,
                    size = OhButtonSize.Small,
                )
            }
        }
    }
}

@Composable
private fun MealSection(
    section: MealSectionModel,
    showKcal: Boolean,
    onAdd: () -> Unit,
    onOpenEntry: (String) -> Unit,
    onDelete: (String) -> Unit,
    onActions: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OhSectionTitle(
                text = mealLabel(section.slot, section.name),
                modifier = Modifier.weight(1f),
            )
            if (showKcal && section.totalKcal != null) {
                Text(
                    text = energyText(section.totalKcal),
                    style = OhTheme.type.footnote.tabular(),
                    color = OhTheme.colors.label2,
                    modifier = Modifier.padding(top = OhTheme.space.s3),
                )
                Spacer(Modifier.width(OhTheme.space.s2))
            }
            OhIconButton(
                icon = OhIcons.add,
                contentDescription = stringResource(R.string.nutrition_add_food),
                onClick = onAdd,
            )
            OhIconButton(
                icon = OhIcons.more,
                contentDescription = stringResource(R.string.nutrition_meal_actions),
                onClick = onActions,
            )
        }
        if (section.entries.isEmpty()) {
            OhRow(
                title = stringResource(R.string.nutrition_add_food),
                subtitle = stringResource(R.string.nutrition_empty_meal),
                leadingIcon = OhIcons.add,
                onClick = onAdd,
            )
        } else {
            OhCard {
                section.entries.forEachIndexed { index, entry ->
                    SwipeToDelete(onDelete = { onDelete(entry.id) }) {
                        OhMediaRow(
                            title = if (entry.name.isNotBlank()) {
                                entry.name
                            } else if (entry.sourceKey == "quick") {
                                stringResource(R.string.nutrition_quick_calories)
                            } else {
                                stringResource(R.string.nutrition_quick_add)
                            },
                            subtitle = buildString {
                                append(
                                    portionText(
                                        entry.grams,
                                        entry.servings,
                                        entry.servingLabel,
                                    ),
                                )
                                append(" · ")
                                append(sourceLabel(entry.sourceKey))
                                if (entry.verified) {
                                    append(" · ")
                                    append(stringResource(R.string.nutrition_verified))
                                }
                            },
                            thumbnail = { FoodThumb() },
                            onClick = { onOpenEntry(entry.id) },
                            trailing = {
                                if (showKcal && entry.kcal != null) {
                                    Text(
                                        text = energyText(entry.kcal),
                                        style = OhTheme.type.subhead.tabular(),
                                        color = OhTheme.colors.label2,
                                    )
                                }
                            },
                        )
                    }
                    if (index < section.entries.lastIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = OhTheme.dims.rowHairInset)
                                .height(OhTheme.dims.hairline)
                                .background(OhTheme.colors.sep),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayProvenanceSheet(
    model: NutritionModel,
    onDismiss: () -> Unit,
    onOpenEntry: (String) -> Unit,
) {
    val entries = model.meals.flatMap { it.entries }
    OhModalSheet(
        visible = true,
        onDismiss = onDismiss,
        title = stringResource(R.string.nutrition_energy_provenance),
    ) {
        Text(
            text = stringResource(R.string.nutrition_energy_provenance_body),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
        Spacer(Modifier.height(OhTheme.space.s3))
        if (entries.isEmpty()) {
            Text(
                text = stringResource(R.string.nutrition_energy_provenance_empty),
                style = OhTheme.type.body,
                color = OhTheme.colors.label2,
            )
        } else {
            entries.forEach { entry ->
                OhRow(
                    title = entry.name,
                    subtitle = sourceLabel(entry.sourceKey),
                    value = entry.kcal?.let { number(it) },
                    unit = entry.kcal?.let { stringResource(R.string.unit_kcal) },
                    showDivider = true,
                    onClick = { onOpenEntry(entry.id) },
                )
            }
        }
        Spacer(Modifier.height(OhTheme.space.s3))
        Text(
            text = stringResource(R.string.nutrition_energy_provenance_targets),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label3,
        )
    }
}

@Composable
private fun MealActionsSheet(
    section: MealSectionModel,
    canCopy: Boolean,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onCopy: () -> Unit,
    onTemplate: () -> Unit,
) {
    OhModalSheet(
        visible = true,
        onDismiss = onDismiss,
        title = mealLabel(section.slot, section.name),
    ) {
        OhRow(
            title = stringResource(R.string.nutrition_add_food),
            leadingIcon = OhIcons.add,
            onClick = onAdd,
        )
        if (canCopy && section.entries.isNotEmpty()) {
            OhRow(
                title = stringResource(R.string.nutrition_copy_meal),
                leadingIcon = OhIcons.undo,
                showDivider = true,
                onClick = onCopy,
            )
        }
        if (section.entries.isNotEmpty()) {
            OhRow(
                title = stringResource(R.string.nutrition_save_template),
                leadingIcon = OhIcons.check,
                showDivider = true,
                onClick = onTemplate,
            )
        }
    }
}

@Composable
private fun FoodThumb() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                color = OhTheme.colors.surface2,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(OhTheme.radius.sm),
            ),
        contentAlignment = Alignment.Center,
    ) {
        OhIcon(
            icon = OhObjectIcons.plate,
            contentDescription = null,
            size = OhTheme.dims.quickTileGlyph,
            tint = OhTheme.colors.label2,
        )
    }
}

/** A horizontal drag reveals a destructive action; the sheet keeps it accessible. */
@Composable
private fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    var offset by remember { mutableFloatStateOf(0f) }
    val maximum = with(density) { OhTheme.dims.touchTarget.toPx() * 1.5f }
    val state = rememberDraggableState { delta ->
        offset = (offset + delta).coerceIn(-maximum, 0f)
    }
    Box {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(OhTheme.radius.base))
                .background(
                    if (offset < 0f) OhTheme.colors.dangerFill else OhTheme.colors.surface,
                ),
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (offset < 0f) {
                Text(
                    text = stringResource(R.string.nutrition_delete_entry),
                    style = OhTheme.type.footnote,
                    color = OhTheme.colors.label,
                    modifier = Modifier.padding(end = OhTheme.space.s6),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = offset }
                .background(OhTheme.colors.surface)
                .draggable(
                    state = state,
                    orientation = Orientation.Horizontal,
                    onDragStopped = {
                        if (-offset > maximum * 0.55f) {
                            onDelete()
                        }
                        offset = 0f
                    },
                ),
        ) {
            content()
        }
    }
}

@Composable
private fun FastingRow(fast: FastCardModel, onFasting: () -> Unit) {
    OhCard(onClick = onFasting) {
        OhCardHeader(
            eyebrow = stringResource(R.string.nutrition_fasting),
            title = if (fast.open) {
                durationText(fast.elapsedMillis)
            } else {
                stringResource(R.string.nutrition_fasting_none)
            },
            glyph = OhGlyphIcons.moon,
        )
        if (fast.open) {
            Spacer(Modifier.height(OhTheme.space.s3))
            app.openhealth.ui.components.OhBar(
                progress = fast.progress,
                ink = OhTheme.colors.acc,
            )
            Spacer(Modifier.height(OhTheme.space.s2))
            Text(
                text = fast.remainingMillis?.let {
                    stringResource(R.string.nutrition_fasting_remaining, durationText(it))
                } ?: stringResource(R.string.nutrition_fasting_past),
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
            )
        } else {
            Spacer(Modifier.height(OhTheme.space.s2))
            Text(
                text = stringResource(R.string.nutrition_fasting_optional),
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
            )
        }
    }
}
