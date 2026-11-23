package app.openhealth.ui.screens.quickadd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import app.openhealth.core.preferences.HealthModule
import app.openhealth.ui.R
import app.openhealth.ui.components.OhButton
import app.openhealth.ui.components.OhButtonSize
import app.openhealth.ui.components.OhButtonVariant
import app.openhealth.ui.components.OhChip
import app.openhealth.ui.components.OhChipStrip
import app.openhealth.ui.components.OhInsetGroup
import app.openhealth.ui.components.OhModalSheet
import app.openhealth.ui.components.OhRow
import app.openhealth.ui.components.OhStepper
import app.openhealth.ui.components.OhTextField
import app.openhealth.ui.icons.OhIcons
import app.openhealth.ui.screens.common.OhQuickTile
import app.openhealth.ui.screens.common.hourOf
import app.openhealth.ui.screens.common.shortDate
import app.openhealth.ui.screens.today.QuickAction
import app.openhealth.ui.theme.OhTheme
import app.openhealth.ui.theme.OhWeight
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/** What the one quick-add sheet is showing: the six tiles or one of their forms. */
enum class QuickAddMode { TILES, NOTE, WATER, WEIGHT, FOOD, SET, VITALS }

/** A recently logged item, offered first (`pr.md` §6.27). */
data class QuickAddRecent(val action: QuickAction, val title: String, val detail: String)

/**
 * The global quick-add sheet of `DESIGN.md` §14.2: a 3 × 2 grid of 84 dp tiles, then
 * recent rows and a Backdate row. A tile opens its own small form inside the same
 * sheet; a sheet never nests another sheet. Food, set and vitals are shells here and
 * say what they are waiting for instead of pretending to log.
 */
@Composable
fun QuickAddSheet(
    visible: Boolean,
    mode: QuickAddMode,
    modules: Set<HealthModule>,
    allowWeight: Boolean,
    recent: List<QuickAddRecent>,
    note: String,
    waterMl: Int,
    weight: String,
    whenDate: LocalDate,
    whenHour: Int,
    saving: Boolean,
    canSave: Boolean,
    invalid: Boolean,
    weightUnit: String,
    onDismiss: () -> Unit,
    onSelect: (QuickAction) -> Unit,
    onBack: () -> Unit,
    onNoteChange: (String) -> Unit,
    onWaterChange: (Int) -> Unit,
    onWeightChange: (String) -> Unit,
    onWhenDate: (LocalDate) -> Unit,
    onWhenHour: (Int) -> Unit,
    onSave: () -> Unit,
    onRepeat: (QuickAddRecent) -> Unit,
) {
    OhModalSheet(
        visible = visible,
        onDismiss = onDismiss,
        title = when (mode) {
            QuickAddMode.TILES -> stringResource(R.string.quick_title)
            QuickAddMode.NOTE -> stringResource(R.string.quick_note)
            QuickAddMode.WATER -> stringResource(R.string.quick_water)
            QuickAddMode.WEIGHT -> stringResource(R.string.quick_weight)
            QuickAddMode.FOOD -> stringResource(R.string.quick_food)
            QuickAddMode.SET -> stringResource(R.string.quick_set)
            QuickAddMode.VITALS -> stringResource(R.string.quick_vitals)
        },
        primaryLabel = when (mode) {
            QuickAddMode.TILES -> null
            QuickAddMode.FOOD, QuickAddMode.SET, QuickAddMode.VITALS -> null
            else -> stringResource(R.string.quick_save)
        },
        primaryEnabled = canSave && !saving,
        onPrimary = onSave,
        cancelLabel = if (mode == QuickAddMode.TILES) {
            stringResource(R.string.quick_close)
        } else {
            stringResource(R.string.quick_back)
        },
        onCancel = if (mode == QuickAddMode.TILES) onDismiss else onBack,
    ) {
        when (mode) {
            QuickAddMode.TILES -> Tiles(
                modules = modules,
                allowWeight = allowWeight,
                recent = recent,
                onSelect = onSelect,
                onRepeat = onRepeat,
            )
            QuickAddMode.NOTE -> NoteForm(
                note,
                onNoteChange,
                whenDate,
                whenHour,
                onWhenDate,
                onWhenHour,
            )
            QuickAddMode.WATER -> WaterForm(
                waterMl,
                onWaterChange,
                whenDate,
                whenHour,
                onWhenDate,
                onWhenHour,
            )
            QuickAddMode.WEIGHT -> WeightForm(
                weight,
                onWeightChange,
                whenDate,
                whenHour,
                onWhenDate,
                onWhenHour,
                invalid,
                weightUnit,
            )
            QuickAddMode.FOOD -> LaterForm(
                title = stringResource(R.string.quick_food_later_title),
                body = stringResource(R.string.quick_food_later_body),
                action = stringResource(R.string.quick_note),
                onAction = { onSelect(QuickAction.NOTE) },
            )
            QuickAddMode.SET -> LaterForm(
                title = stringResource(R.string.quick_set_later_title),
                body = stringResource(R.string.quick_set_later_body),
                action = stringResource(R.string.quick_note),
                onAction = { onSelect(QuickAction.NOTE) },
            )
            QuickAddMode.VITALS -> LaterForm(
                title = stringResource(R.string.quick_vitals_later_title),
                body = stringResource(R.string.quick_vitals_later_body),
                action = stringResource(R.string.quick_note),
                onAction = { onSelect(QuickAction.NOTE) },
            )
        }
    }
}

@Composable
private fun Tiles(
    modules: Set<HealthModule>,
    allowWeight: Boolean,
    recent: List<QuickAddRecent>,
    onSelect: (QuickAction) -> Unit,
    onRepeat: (QuickAddRecent) -> Unit,
) {
    val actions = buildList {
        add(QuickAction.NOTE)
        if (HealthModule.NUTRITION in modules) {
            add(QuickAction.WATER)
            add(QuickAction.FOOD)
        }
        if (HealthModule.TRAINING in modules) add(QuickAction.SET)
        if (HealthModule.BODY in modules && allowWeight) add(QuickAction.WEIGHT)
        if (HealthModule.VITALS in modules) add(QuickAction.VITALS)
    }
    Column(verticalArrangement = Arrangement.spacedBy(OhTheme.space.s4)) {
        actions.chunked(QUICK_COLUMNS).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(OhTheme.space.s4),
            ) {
                row.forEach { action ->
                    OhQuickTile(
                        action = action,
                        onClick = { onSelect(action) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(QUICK_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    if (recent.isNotEmpty()) {
        Spacer(Modifier.height(OhTheme.space.s7))
        Text(
            text = stringResource(R.string.quick_recent),
            style = OhTheme.type.footnote.copy(fontWeight = OhWeight.semibold),
            color = OhTheme.colors.label2,
        )
        Spacer(Modifier.height(OhTheme.space.s1))
        OhInsetGroup {
            recent.forEachIndexed { index, item ->
                OhRow(
                    title = item.title,
                    subtitle = item.detail,
                    showDivider = index > 0,
                    showChevron = true,
                    onClick = { onRepeat(item) },
                )
            }
        }
    }
    Spacer(Modifier.height(OhTheme.space.s7))
    OhInsetGroup {
        OhRow(
            title = stringResource(R.string.quick_backdate),
            subtitle = stringResource(R.string.quick_backdate_body),
            leadingIcon = OhIcons.timer,
            showChevron = true,
            onClick = { onSelect(QuickAction.NOTE) },
        )
    }
}

@Composable
private fun NoteForm(
    note: String,
    onNoteChange: (String) -> Unit,
    whenDate: LocalDate,
    whenHour: Int,
    onWhenDate: (LocalDate) -> Unit,
    onWhenHour: (Int) -> Unit,
) {
    OhTextField(
        value = note,
        onValueChange = onNoteChange,
        placeholder = stringResource(R.string.quick_note_placeholder),
        singleLine = false,
    )
    Spacer(Modifier.height(OhTheme.space.s5))
    WhenSection(whenDate, whenHour, onWhenDate, onWhenHour)
}

@Composable
private fun WaterForm(
    waterMl: Int,
    onWaterChange: (Int) -> Unit,
    whenDate: LocalDate,
    whenHour: Int,
    onWhenDate: (LocalDate) -> Unit,
    onWhenHour: (Int) -> Unit,
) {
    Text(
        text = stringResource(R.string.quick_water_amount),
        style = OhTheme.type.subhead,
        color = OhTheme.colors.label2,
    )
    Spacer(Modifier.height(OhTheme.space.s2))
    OhChipStrip {
        WATER_AMOUNTS.forEach { amount ->
            OhChip(
                text = stringResource(R.string.quick_water_ml, amount),
                selected = amount == waterMl,
                onClick = { onWaterChange(amount) },
            )
        }
    }
    Spacer(Modifier.height(OhTheme.space.s5))
    WhenSection(whenDate, whenHour, onWhenDate, onWhenHour)
}

@Composable
private fun WeightForm(
    weight: String,
    onWeightChange: (String) -> Unit,
    whenDate: LocalDate,
    whenHour: Int,
    onWhenDate: (LocalDate) -> Unit,
    onWhenHour: (Int) -> Unit,
    invalid: Boolean,
    weightUnit: String,
) {
    OhTextField(
        value = weight,
        onValueChange = onWeightChange,
        placeholder = stringResource(R.string.quick_weight_value),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        error = if (invalid) stringResource(R.string.quick_weight_invalid) else null,
        trailing = {
            Text(text = weightUnit, style = OhTheme.type.footnote, color = OhTheme.colors.label2)
        },
    )
    Spacer(Modifier.height(OhTheme.space.s5))
    WhenSection(whenDate, whenHour, onWhenDate, onWhenHour)
}

@Composable
private fun LaterForm(title: String, body: String, action: String, onAction: () -> Unit) {
    Text(
        text = title,
        style = OhTheme.type.body.copy(fontWeight = OhWeight.medium),
        color = OhTheme.colors.label,
    )
    Spacer(Modifier.height(OhTheme.space.s0))
    Text(text = body, style = OhTheme.type.footnote, color = OhTheme.colors.label2)
    Spacer(Modifier.height(OhTheme.space.s5))
    OhButton(
        text = action,
        onClick = onAction,
        variant = OhButtonVariant.Secondary,
        size = OhButtonSize.Small,
    )
}

@Composable
private fun WhenSection(
    date: LocalDate,
    hour: Int,
    onDate: (LocalDate) -> Unit,
    onHour: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Text(
        text = stringResource(R.string.quick_when),
        style = OhTheme.type.footnote,
        color = OhTheme.colors.label2,
    )
    Spacer(Modifier.height(OhTheme.space.s1))
    OhRow(
        title = whenLabel(date, hour),
        leadingIcon = OhIcons.timer,
        showChevron = true,
        onClick = { expanded = !expanded },
    )
    if (expanded) {
        Spacer(Modifier.height(OhTheme.space.s3))
        OhChipStrip {
            for (offset in 0 until WHEN_DAYS) {
                val day = date.minusDays(offset)
                OhChip(
                    text = shortDate(day),
                    selected = day == date,
                    onClick = { onDate(day) },
                )
            }
        }
        Spacer(Modifier.height(OhTheme.space.s3))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.quick_hour),
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
                modifier = Modifier.weight(1f),
            )
            OhStepper(
                value = hourOf(hour),
                onDecrement = { onHour(previousHour(hour)) },
                onIncrement = { onHour(nextHour(hour)) },
            )
        }
    }
}

@Composable
private fun whenLabel(date: LocalDate, hour: Int): String {
    val today = kotlin.time.Clock.System.now().toLocalDateTime(
        kotlinx.datetime.TimeZone.currentSystemDefault(),
    ).date
    val day = when (date) {
        today -> stringResource(R.string.quick_today)
        today.minusDays(1) -> stringResource(R.string.quick_yesterday)
        else -> shortDate(date)
    }
    return stringResource(
        R.string.quick_when_value,
        day,
        hourOf(hour),
    )
}

private fun LocalDate.minusDays(days: Int): LocalDate =
    this + kotlinx.datetime.DatePeriod(days = -days)

private fun previousHour(hour: Int): Int = (hour + HOURS_PER_DAY - 1) % HOURS_PER_DAY

private fun nextHour(hour: Int): Int = (hour + 1) % HOURS_PER_DAY

private val WATER_AMOUNTS = listOf(GLASS_SMALL, GLASS_CUP, GLASS_BOTTLE, GLASS_LARGE)
private const val WHEN_DAYS = 7
private const val QUICK_COLUMNS = 3
private const val HOURS_PER_DAY = 24
private const val GLASS_SMALL = 250
private const val GLASS_CUP = 330
private const val GLASS_BOTTLE = 500
private const val GLASS_LARGE = 750
