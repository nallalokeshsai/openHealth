package app.openhealth.ui.screens.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import app.openhealth.core.preferences.HealthModule
import app.openhealth.core.preferences.SensitiveMetric
import app.openhealth.ui.R
import app.openhealth.ui.components.OhCard
import app.openhealth.ui.components.OhCardHeader
import app.openhealth.ui.components.OhInsetGroup
import app.openhealth.ui.components.OhRow
import app.openhealth.ui.components.OhStatTile
import app.openhealth.ui.icons.OhIcons
import app.openhealth.ui.icons.OhModuleIcons
import app.openhealth.ui.icons.OhObjectIcons
import app.openhealth.ui.screens.common.OhEmptyState
import app.openhealth.ui.screens.common.OhQuickTile
import app.openhealth.ui.screens.common.OhScreen
import app.openhealth.ui.screens.common.OhSectionTitle
import app.openhealth.ui.screens.common.number
import app.openhealth.ui.screens.common.observedLocale
import app.openhealth.ui.theme.OhTheme
import app.openhealth.ui.theme.OhWeight
import app.openhealth.ui.theme.ltrFigures
import app.openhealth.ui.theme.tabular
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Today in the fixed order of `DESIGN.md` §14.2: header, body card, readiness, week
 * strip, energy, steps, weight, due items. Every card is a real empty state in B3:
 * no number is invented, and a value that the weight-neutral policy hides is not
 * rendered at all (`pr.md` §6.25).
 */
@Composable
fun TodayScreen(
    model: TodayModel,
    displayPolicy: app.openhealth.core.preferences.DisplayPolicy,
    onQuickAdd: (QuickAction) -> Unit,
    onOpenTimeline: () -> Unit,
    onOpenTrain: () -> Unit,
    onOpenNutrition: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OhScreen(modifier = modifier) {
        TodayHeader(model = model)
        if (model.demo) {
            DemoLabel()
        }
        if (HealthModule.TRAINING in model.modules) {
            BodyCard(model = model.bodyMap, onOpenTrain = onOpenTrain)
            Spacer(Modifier.height(OhTheme.space.s5))
        }
        if (HealthModule.SLEEP in model.modules || HealthModule.VITALS in model.modules) {
            ReadinessCard(model.readinessScore)
            Spacer(Modifier.height(OhTheme.space.s5))
        }
        if (HealthModule.TRAINING in model.modules) {
            WeekStrip(today = model.today, onStart = onOpenTrain)
            Spacer(Modifier.height(OhTheme.space.s5))
        }
        if (HealthModule.NUTRITION in model.modules &&
            displayPolicy.shows(SensitiveMetric.CALORIES)
        ) {
            EnergyCard(model.energyKcal, onOpenNutrition)
            Spacer(Modifier.height(OhTheme.space.s5))
        }
        if (HealthModule.STEPS in model.modules) {
            StepsCard(model.steps)
            Spacer(Modifier.height(OhTheme.space.s5))
        }
        if (HealthModule.BODY in model.modules &&
            displayPolicy.shows(SensitiveMetric.WEIGHT)
        ) {
            WeightCard(
                weightKg = model.weightKg,
                unitSystem = model.unitSystem,
                onLogWeight = { onQuickAdd(QuickAction.WEIGHT) },
            )
            Spacer(Modifier.height(OhTheme.space.s5))
        }
        DueCard()
        QuickActions(
            modules = model.modules,
            allowWeight = displayPolicy.shows(SensitiveMetric.WEIGHT),
            onQuickAdd = onQuickAdd,
        )
        Spacer(Modifier.height(OhTheme.space.s7))
        OhInsetGroup {
            OhRow(
                title = stringResource(R.string.timeline_title),
                subtitle = stringResource(R.string.today_timeline_body),
                leadingIcon = OhIcons.timer,
                showChevron = true,
                onClick = onOpenTimeline,
            )
        }
        Spacer(Modifier.height(OhTheme.space.s7))
    }
}

@Composable
private fun TodayHeader(model: TodayModel) {
    val type = OhTheme.type
    val colors = OhTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = OhTheme.space.s6),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    when (model.greeting) {
                        Greeting.NIGHT -> R.string.today_greeting_night
                        Greeting.MORNING -> R.string.today_greeting_morning
                        Greeting.AFTERNOON -> R.string.today_greeting_afternoon
                        Greeting.EVENING -> R.string.today_greeting_evening
                    },
                ),
                style = type.large.copy(fontWeight = OhWeight.bold),
                color = colors.label,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(OhTheme.space.s1))
            Text(
                text = when (model.status) {
                    TodayStatus.NOTHING_LOGGED -> stringResource(R.string.today_status_empty)
                    TodayStatus.LOGGED_TODAY -> stringResource(R.string.today_status_logged)
                    TodayStatus.WEIGHT_NEUTRAL -> stringResource(
                        R.string.today_status_weight_neutral,
                    )
                },
                style = type.subhead,
                color = colors.label2,
            )
        }
    }
    Spacer(Modifier.height(OhTheme.space.s6))
}

@Composable
private fun DemoLabel() {
    Text(
        text = stringResource(R.string.today_sample_data),
        style = OhTheme.type.footnote,
        color = OhTheme.colors.label2,
        modifier = Modifier.padding(bottom = OhTheme.space.s3),
    )
}

@Composable
private fun ReadinessCard(score: Int?) {
    OhCard {
        OhCardHeader(
            eyebrow = stringResource(R.string.today_readiness_eyebrow),
            title = stringResource(R.string.today_readiness_title),
            glyph = OhModuleIcons.sleep,
        )
        if (score == null) {
            OhEmptyState(
                title = stringResource(R.string.today_readiness_empty_title),
                body = stringResource(R.string.today_readiness_empty_body),
            )
        } else {
            Spacer(Modifier.height(OhTheme.space.s5))
            Text(
                text = number(score.toDouble()),
                style = OhTheme.type.display.copy(fontWeight = OhWeight.semibold)
                    .tabular().ltrFigures(),
                color = OhTheme.colors.label,
            )
        }
    }
}

@Composable
private fun WeekStrip(today: LocalDate, onStart: () -> Unit) {
    OhCard {
        OhCardHeader(
            eyebrow = stringResource(R.string.today_week_eyebrow),
            title = stringResource(R.string.today_week_title),
        )
        Spacer(Modifier.height(OhTheme.space.s5))
        val firstDay = java.time.temporal.WeekFields.of(observedLocale()).firstDayOfWeek.value
        val offset = (today.dayOfWeek.isoDayNumber - firstDay + DAYS_IN_WEEK) % DAYS_IN_WEEK
        val weekStart = today - kotlinx.datetime.DatePeriod(days = offset)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                repeat(DAYS_IN_WEEK) { index ->
                    val day = weekStart.plusDays(index)
                    WeekDay(day = day, isToday = day == today)
                }
            }
        }
        Spacer(Modifier.height(OhTheme.space.s5))
        OhEmptyState(
            title = stringResource(R.string.today_plan_empty_title),
            body = stringResource(R.string.today_plan_empty_body),
            actionLabel = stringResource(R.string.today_plan_empty_action),
            onAction = onStart,
        )
    }
}

@Composable
private fun WeekDay(day: LocalDate, isToday: Boolean) {
    val colors = OhTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = weekdayLabel(day),
            style = OhTheme.type.caption2,
            color = colors.label2,
        )
        Spacer(Modifier.height(OhTheme.space.s1))
        Box(
            modifier = Modifier
                .size(OhTheme.dims.touchTarget)
                .then(
                    if (isToday) {
                        Modifier.clip(CircleShape).background(colors.acc)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.dayOfMonth.toString(),
                style = OhTheme.type.footnote.copy(
                    fontWeight = if (isToday) OhWeight.semibold else OhWeight.regular,
                ),
                color = if (isToday) colors.onAcc else colors.label2,
            )
        }
    }
}

@Composable
private fun EnergyCard(energyKcal: Double?, onLogFood: () -> Unit) {
    OhCard {
        OhCardHeader(
            eyebrow = stringResource(R.string.today_energy_eyebrow),
            title = stringResource(R.string.today_energy_title),
            glyph = OhObjectIcons.plate,
        )
        if (energyKcal == null) {
            OhEmptyState(
                title = stringResource(R.string.today_energy_empty_title),
                body = stringResource(R.string.today_energy_empty_body),
                actionLabel = stringResource(R.string.today_energy_empty_action),
                onAction = onLogFood,
            )
        } else {
            Spacer(Modifier.height(OhTheme.space.s5))
            OhStatTile(
                label = stringResource(R.string.today_energy_in),
                value = number(energyKcal),
                unit = stringResource(R.string.unit_kcal),
            )
        }
    }
}

@Composable
private fun StepsCard(steps: Int?) {
    OhCard {
        OhCardHeader(
            eyebrow = stringResource(R.string.today_steps_eyebrow),
            title = stringResource(R.string.today_steps_title),
            glyph = OhModuleIcons.steps,
        )
        if (steps == null) {
            OhEmptyState(
                title = stringResource(R.string.today_steps_empty_title),
                body = stringResource(R.string.today_steps_empty_body),
            )
        } else {
            Spacer(Modifier.height(OhTheme.space.s5))
            Text(
                text = number(steps.toDouble()),
                style = OhTheme.type.display.copy(fontWeight = OhWeight.semibold)
                    .tabular().ltrFigures(),
                color = OhTheme.colors.label,
            )
        }
    }
}

@Composable
private fun WeightCard(
    weightKg: Double?,
    unitSystem: app.openhealth.core.preferences.UnitSystem,
    onLogWeight: () -> Unit,
) {
    OhCard {
        OhCardHeader(
            eyebrow = stringResource(R.string.today_weight_eyebrow),
            title = stringResource(R.string.today_weight_title),
            glyph = OhModuleIcons.weight,
        )
        if (weightKg == null) {
            OhEmptyState(
                title = stringResource(R.string.today_weight_empty_title),
                body = stringResource(R.string.today_weight_empty_body),
                actionLabel = stringResource(R.string.today_weight_empty_action),
                onAction = onLogWeight,
            )
        } else {
            Spacer(Modifier.height(OhTheme.space.s5))
            val imperial = unitSystem == app.openhealth.core.preferences.UnitSystem.IMPERIAL
            val shown = if (imperial) {
                app.openhealth.core.units.Measure(
                    weightKg,
                    app.openhealth.core.units.UcumUnit.KILOGRAM,
                ).convertTo(app.openhealth.core.units.UcumUnit.POUND).value
            } else {
                weightKg
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = number(shown, decimals = 1),
                    style = OhTheme.type.display.copy(fontWeight = OhWeight.semibold)
                        .tabular()
                        .ltrFigures(),
                    color = OhTheme.colors.label,
                )
                Spacer(Modifier.width(OhTheme.space.s0))
                Text(
                    text = stringResource(
                        if (imperial) R.string.unit_pounds else R.string.unit_kg,
                    ),
                    style = OhTheme.type.subhead,
                    color = OhTheme.colors.label2,
                )
            }
        }
    }
}

@Composable
private fun DueCard() {
    OhCard {
        OhCardHeader(
            eyebrow = stringResource(R.string.today_due_eyebrow),
            title = stringResource(R.string.today_due_title),
            glyph = OhIcons.check,
        )
        OhEmptyState(
            title = stringResource(R.string.today_due_empty_title),
            body = stringResource(R.string.today_due_empty_body),
        )
    }
}

@Composable
private fun QuickActions(
    modules: Set<HealthModule>,
    allowWeight: Boolean,
    onQuickAdd: (QuickAction) -> Unit,
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
    if (actions.isEmpty()) return
    OhSectionTitle(stringResource(R.string.today_quick_title))
    Column(verticalArrangement = Arrangement.spacedBy(OhTheme.space.s4)) {
        actions.chunked(QUICK_COLUMNS).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(OhTheme.space.s4),
            ) {
                row.forEach { action ->
                    OhQuickTile(
                        action = action,
                        onClick = { onQuickAdd(action) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(QUICK_COLUMNS - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

private fun LocalDate.plusDays(days: Int): LocalDate =
    this + kotlinx.datetime.DatePeriod(days = days)

@Composable
private fun weekdayLabel(day: LocalDate): String {
    val javaDate = java.time.LocalDate.of(
        day.year,
        day.month.ordinal + MONTH_OFFSET,
        day.dayOfMonth,
    )
    return javaDate.format(
        java.time.format.DateTimeFormatter.ofPattern("EE", observedLocale()),
    )
}

private const val DAYS_IN_WEEK = 7
private const val MONTH_OFFSET = 1
private const val QUICK_COLUMNS = 3
