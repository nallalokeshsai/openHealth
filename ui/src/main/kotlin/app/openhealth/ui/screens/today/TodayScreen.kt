package app.openhealth.ui.screens.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import app.openhealth.core.body.BodySide
import app.openhealth.core.nutrition.Nutrient
import app.openhealth.core.preferences.HealthModule
import app.openhealth.core.preferences.SensitiveMetric
import app.openhealth.ui.R
import app.openhealth.ui.body.BodyMapVariant
import app.openhealth.ui.body.OhBodyMap
import app.openhealth.ui.components.ChartTableModel
import app.openhealth.ui.components.ChartTableSheet
import app.openhealth.ui.components.OhBarRow
import app.openhealth.ui.components.OhButton
import app.openhealth.ui.components.OhCard
import app.openhealth.ui.components.OhCardHeader
import app.openhealth.ui.components.OhInsetGroup
import app.openhealth.ui.components.OhKpiBand
import app.openhealth.ui.components.OhKpiFigure
import app.openhealth.ui.components.OhModalSheet
import app.openhealth.ui.components.OhRing
import app.openhealth.ui.components.OhRow
import app.openhealth.ui.components.OhSlider
import app.openhealth.ui.components.OhSparkline
import app.openhealth.ui.components.ohClickable
import app.openhealth.ui.components.rememberInteraction
import app.openhealth.ui.icons.OhIcons
import app.openhealth.ui.icons.OhModuleIcons
import app.openhealth.ui.icons.OhObjectIcons
import app.openhealth.ui.screens.common.OhEmptyState
import app.openhealth.ui.screens.common.OhQuickTile
import app.openhealth.ui.screens.common.OhScreen
import app.openhealth.ui.screens.common.OhSectionTitle
import app.openhealth.ui.screens.common.number
import app.openhealth.ui.screens.common.observedLocale
import app.openhealth.ui.screens.stats.ChartId
import app.openhealth.ui.screens.stats.ChartRegistry
import app.openhealth.ui.screens.vitals.FormulaSheet
import app.openhealth.ui.screens.vitals.FormulaSheetModel
import app.openhealth.ui.screens.vitals.ValueProvenance
import app.openhealth.ui.screens.vitals.VitalsProvenanceSheet
import app.openhealth.ui.screens.vitals.citationLabel
import app.openhealth.ui.screens.vitals.citationTitle
import app.openhealth.ui.theme.OhTheme
import app.openhealth.ui.theme.OhWeight
import app.openhealth.ui.theme.ltrFigures
import app.openhealth.ui.theme.tabular
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus

/** The values on Today that open a provenance sheet. */
enum class TodayValue { ENERGY_IN, ENERGY_OUT, ENERGY_NET, WATER, STEPS, WEIGHT }

/** One number on Today with where it came from. */
data class TodayProvenance(val value: TodayValue, val provenance: ValueProvenance)

/**
 * Today in the fixed order of `DESIGN.md` §14.2: header, body card, readiness, week strip,
 * energy, steps, weight, due items. The body card is the hero and Start is the accent; the
 * ring is the only other accent use, and the body's colours are the recovery data hues.
 * Every hidden value is removed rather than blurred, and every chart has a table and a
 * formula sheet (`pr.md` §6.25, `DESIGN.md` §8.6).
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
    synchronousLayers: Boolean = false,
    onStartPlan: (String?) -> Unit = {},
    onSaveGoalTarget: (Double) -> Unit = {},
    onSaveStepGoal: (Int) -> Unit = {},
    onOpenVitals: (app.openhealth.core.observation.ObservationType?) -> Unit = {},
) {
    var readinessSheet by rememberSaveable { mutableStateOf(false) }
    var goalSheet by rememberSaveable { mutableStateOf(false) }
    var stepGoalSheet by rememberSaveable { mutableStateOf(false) }
    var formula by remember { mutableStateOf<FormulaSheetModel?>(null) }
    var table by remember { mutableStateOf<ChartTableModel?>(null) }
    var provenance by remember { mutableStateOf<ValueProvenance?>(null) }

    fun openProvenance(value: TodayValue) {
        model.provenances.firstOrNull { it.value == value }?.let { provenance = it.provenance }
    }
    val energyFormula = formulaFor(ChartId.ENERGY_RING)
    val stepsFormula = formulaFor(ChartId.STEPS_RING)
    val weightFormula = formulaFor(ChartId.WEIGHT_SPARKLINE)

    OhScreen(modifier = modifier) {
        TodayHeader(model = model)
        if (model.demo) {
            DemoLabel()
        }
        if (HealthModule.TRAINING in model.modules) {
            BodyCard(
                model = model.bodyMap,
                onOpenTrain = onOpenTrain,
                synchronousLayers = synchronousLayers,
            )
            Spacer(Modifier.height(OhTheme.space.s5))
        }
        model.readiness?.let { readiness ->
            ReadinessCard(readiness = readiness, onOpen = { readinessSheet = true })
            Spacer(Modifier.height(OhTheme.space.s5))
        }
        if (HealthModule.TRAINING in model.modules) {
            WeekStrip(
                model = model,
                onStart = { onStartPlan(model.plan?.routineId) },
                onOpenTrain = onOpenTrain,
            )
            Spacer(Modifier.height(OhTheme.space.s5))
        }
        if (HealthModule.NUTRITION in model.modules &&
            displayPolicy.shows(SensitiveMetric.CALORIES)
        ) {
            model.energy?.let { energy ->
                EnergyCard(
                    energy = energy,
                    onLogFood = onOpenNutrition,
                    onOpenGoal = { goalSheet = true },
                    onOpenIn = { openProvenance(TodayValue.ENERGY_IN) },
                    onOpenOut = { openProvenance(TodayValue.ENERGY_OUT) },
                    onOpenNet = { openProvenance(TodayValue.ENERGY_NET) },
                    onOpenWater = { openProvenance(TodayValue.WATER) },
                    onShowTable = {
                        model.charts.firstOrNull { it.chart == ChartId.ENERGY_RING }
                            ?.let { table = it.table }
                    },
                    onShowFormula = { formula = energyFormula },
                )
                Spacer(Modifier.height(OhTheme.space.s5))
            }
            if (model.energy == null && model.energyNeedsProfile) {
                OhCard {
                    Text(
                        text = stringResource(R.string.today_energy_needs_profile),
                        style = OhTheme.type.subhead,
                        color = OhTheme.colors.label2,
                    )
                }
                Spacer(Modifier.height(OhTheme.space.s5))
            }
        }
        if (HealthModule.STEPS in model.modules) {
            StepsCard(
                steps = model.stepsCard,
                onOpenGoal = { stepGoalSheet = true },
                onOpenSource = { openProvenance(TodayValue.STEPS) },
                onShowTable = {
                    model.charts.firstOrNull { it.chart == ChartId.STEPS_RING }
                        ?.let { table = it.table }
                },
                onShowFormula = { formula = stepsFormula },
            )
            Spacer(Modifier.height(OhTheme.space.s5))
        }
        if (HealthModule.BODY in model.modules &&
            displayPolicy.shows(SensitiveMetric.WEIGHT)
        ) {
            WeightCard(
                weight = model.weightCard,
                unitSystem = model.unitSystem,
                onOpen = { openProvenance(TodayValue.WEIGHT) },
                onLogWeight = { onQuickAdd(QuickAction.WEIGHT) },
                onShowTable = {
                    model.charts.firstOrNull { it.chart == ChartId.WEIGHT_SPARKLINE }
                        ?.let { table = it.table }
                },
                onShowFormula = { formula = weightFormula },
            )
            Spacer(Modifier.height(OhTheme.space.s5))
        }
        DueCard(
            due = model.due,
            onAction = { action ->
                when (action) {
                    TodayDueAction.VITALS -> onOpenVitals(null)
                    TodayDueAction.NOTE -> onQuickAdd(QuickAction.NOTE)
                    TodayDueAction.SLEEP -> onOpenVitals(
                        app.openhealth.core.observation.ObservationType.SLEEP_DURATION,
                    )

                    TodayDueAction.WEIGHT -> onQuickAdd(QuickAction.WEIGHT)
                    TodayDueAction.FOOD -> onQuickAdd(QuickAction.FOOD)
                    TodayDueAction.WATER -> onQuickAdd(QuickAction.WATER)
                }
            },
        )
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

    if (readinessSheet && model.readiness != null) {
        ReadinessSheet(model.readiness, onDismiss = { readinessSheet = false })
    }
    if (goalSheet && model.energy?.whatIf != null) {
        GoalSheet(
            energy = model.energy!!,
            today = model.today,
            unitSystem = model.unitSystem,
            onDismiss = { goalSheet = false },
            onSave = { target ->
                onSaveGoalTarget(target)
                goalSheet = false
            },
        )
    }
    if (stepGoalSheet && model.stepsCard != null) {
        StepGoalSheet(
            current = model.stepsCard.goal,
            onDismiss = { stepGoalSheet = false },
            onSave = { goal ->
                onSaveStepGoal(goal)
                stepGoalSheet = false
            },
        )
    }
    formula?.let { FormulaSheet(sheet = it, onDismiss = { formula = null }) }
    table?.let { ChartTableSheet(model = it, onDismiss = { table = null }) }
    provenance?.let {
        VitalsProvenanceSheet(provenance = it, onDismiss = { provenance = null })
    }
}

@Composable
private fun formulaFor(id: app.openhealth.ui.screens.stats.ChartId): FormulaSheetModel {
    val meta = ChartRegistry.of(id)
    return FormulaSheetModel(
        title = stringResource(meta.formulaTitleRes),
        body = stringResource(meta.formulaBodyRes),
        citation = meta.citation.name,
    )
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
private fun ReadinessCard(readiness: ReadinessCardModel, onOpen: () -> Unit) {
    OhCard {
        OhCardHeader(
            eyebrow = stringResource(R.string.today_readiness_eyebrow),
            title = stringResource(R.string.today_readiness_title),
            glyph = OhModuleIcons.sleep,
            trailing = {
                if (readiness.score != null) {
                    Text(
                        text = number(readiness.score.toDouble()),
                        style = OhTheme.type.display.copy(fontWeight = OhWeight.semibold)
                            .tabular().ltrFigures(),
                        color = OhTheme.colors.label,
                    )
                }
            },
        )
        Spacer(Modifier.height(OhTheme.space.s5))
        if (readiness.chips.isNotEmpty()) {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(OhTheme.space.s2),
                verticalArrangement = Arrangement.spacedBy(OhTheme.space.s2),
            ) {
                readiness.chips.take(READINESS_CHIPS).forEach { chip ->
                    Chip(text = chip.label, value = chip.value, available = chip.available)
                }
            }
            Spacer(Modifier.height(OhTheme.space.s3))
        }
        if (readiness.score == null) {
            OhEmptyState(
                title = stringResource(R.string.today_readiness_empty_title),
                body = readiness.explanation,
            )
        } else {
            CardLink(
                text = stringResource(R.string.today_readiness_explanation),
                onClick = onOpen,
            )
        }
    }
}

@Composable
private fun Chip(text: String, value: String?, available: Boolean) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(OhTheme.radius.full))
            .background(OhTheme.colors.surface2)
            .padding(
                horizontal = OhTheme.space.s3,
                vertical = OhTheme.space.s1,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, style = OhTheme.type.caption, color = OhTheme.colors.label2)
        if (value != null) {
            Spacer(Modifier.width(OhTheme.space.s1))
            Text(
                text = value,
                style = OhTheme.type.caption.tabular(),
                color = if (available) OhTheme.colors.label else OhTheme.colors.label3,
            )
        }
    }
}

@Composable
private fun WeekStrip(model: TodayModel, onStart: () -> Unit, onOpenTrain: () -> Unit) {
    OhCard {
        OhCardHeader(
            eyebrow = stringResource(R.string.today_week_eyebrow),
            title = stringResource(R.string.today_week_title),
            subtitle = stringResource(R.string.today_week_sessions, model.sessionCount),
        )
        Spacer(Modifier.height(OhTheme.space.s5))
        val firstDay = java.time.temporal.WeekFields.of(observedLocale()).firstDayOfWeek.value
        val today = model.today
        val offset = (today.dayOfWeek.isoDayNumber - firstDay + DAYS_IN_WEEK) % DAYS_IN_WEEK
        val weekStart = today + DatePeriod(days = -offset)
        val days = model.week.takeIf { it.size == DAYS_IN_WEEK }
            ?: (0 until DAYS_IN_WEEK).map { index ->
                val day = weekStart + DatePeriod(days = index)
                TodayWeekDay(day, day == today, hasSession = false)
            }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                days.forEach { day -> WeekDay(day = day) }
            }
        }
        Spacer(Modifier.height(OhTheme.space.s5))
        val plan = model.plan
        if (plan == null) {
            OhEmptyState(
                title = stringResource(R.string.today_week_rest_title),
                body = stringResource(R.string.today_week_rest_body),
                actionLabel = stringResource(R.string.today_plan_empty_action),
                onAction = onOpenTrain,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                plan.map?.let { map ->
                    OhBodyMap(
                        model = map,
                        variant = BodyMapVariant.MINI,
                        sides = setOf(BodySide.FRONT),
                        modifier = Modifier.width(OhTheme.dims.bodyMapMiniHeight),
                    )
                    Spacer(Modifier.width(OhTheme.space.s3))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plan.name,
                        style = OhTheme.type.body.copy(fontWeight = OhWeight.semibold),
                        color = OhTheme.colors.label,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = plan.detail,
                        style = OhTheme.type.footnote,
                        color = OhTheme.colors.label2,
                    )
                }
                Spacer(Modifier.width(OhTheme.space.s3))
                OhButton(
                    text = stringResource(R.string.today_week_start),
                    onClick = onStart,
                    size = app.openhealth.ui.components.OhButtonSize.Small,
                )
            }
        }
    }
}

@Composable
private fun WeekDay(day: TodayWeekDay) {
    val colors = OhTheme.colors
    val type = OhTheme.type
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = weekdayLabel(day.date),
            style = type.caption2,
            color = colors.label2,
        )
        Spacer(Modifier.height(OhTheme.space.s1))
        Box(
            modifier = Modifier
                .size(OhTheme.dims.touchTarget)
                .then(
                    if (day.isToday) {
                        Modifier.clip(CircleShape).background(colors.acc)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.date.dayOfMonth.toString(),
                style = type.footnote.copy(
                    fontWeight = if (day.isToday) OhWeight.semibold else OhWeight.regular,
                ),
                color = if (day.isToday) colors.onAcc else colors.label2,
            )
        }
        Box(
            modifier = Modifier
                .size(OhTheme.dims.dot)
                .clip(CircleShape)
                .background(if (day.hasSession) colors.acc else colors.surface2),
        )
    }
}

@Composable
private fun EnergyCard(
    energy: TodayEnergyModel,
    onLogFood: () -> Unit,
    onOpenGoal: () -> Unit,
    onOpenIn: () -> Unit,
    onOpenOut: () -> Unit,
    onOpenNet: () -> Unit,
    onOpenWater: () -> Unit,
    onShowTable: () -> Unit,
    onShowFormula: () -> Unit,
) {
    OhCard {
        OhCardHeader(
            eyebrow = stringResource(R.string.today_energy_eyebrow),
            title = stringResource(R.string.today_energy_title),
            glyph = OhObjectIcons.plate,
        )
        if (!energy.hasIntake) {
            Spacer(Modifier.height(OhTheme.space.s2))
            OhEmptyState(
                title = stringResource(R.string.today_energy_empty_title),
                body = stringResource(R.string.today_energy_no_intake),
                actionLabel = stringResource(R.string.today_energy_empty_action),
                onAction = onLogFood,
            )
            return@OhCard
        }
        Spacer(Modifier.height(OhTheme.space.s5))
        OhKpiBand(
            figures = listOf(
                OhKpiFigure(
                    value = energy.intakeKcal.let { number(it) },
                    label = stringResource(R.string.today_energy_in),
                    unit = stringResource(R.string.unit_kcal),
                    onClick = onOpenIn,
                ),
                OhKpiFigure(
                    value = number((energy.outLowKcal + energy.outHighKcal) / 2.0),
                    label = stringResource(R.string.today_energy_out),
                    unit = stringResource(R.string.unit_kcal),
                    detail = rangeText(energy.outLowKcal, energy.outHighKcal),
                    onClick = onOpenOut,
                ),
                OhKpiFigure(
                    value = number((energy.netLowKcal + energy.netHighKcal) / 2.0),
                    label = stringResource(R.string.today_energy_net),
                    unit = stringResource(R.string.unit_kcal),
                    detail = rangeText(energy.netLowKcal, energy.netHighKcal),
                    onClick = onOpenNet,
                ),
            ),
        )
        Spacer(Modifier.height(OhTheme.space.s5))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OhRing(
                progress = energy.progress(),
                centre = energy.remainingKcal?.let { number(it) }
                    ?: number(energy.intakeKcal),
                contentDescription = energy.targetKcal?.let { target ->
                    stringResource(
                        R.string.today_energy_ring_desc,
                        number(energy.intakeKcal),
                        number(target),
                    )
                } ?: stringResource(
                    R.string.today_energy_ring_desc_no_target,
                    number(energy.intakeKcal),
                ),
            )
            Spacer(Modifier.width(OhTheme.space.s5))
            Column(modifier = Modifier.weight(1f)) {
                if (energy.remainingKcal != null) {
                    Text(
                        text = stringResource(
                            R.string.today_energy_remaining,
                            number(energy.remainingKcal),
                        ),
                        style = OhTheme.type.footnote.tabular(),
                        color = OhTheme.colors.label2,
                    )
                }
                energy.macros.forEach { macro ->
                    OhBarRow(
                        label = macroLabel(macro.nutrient),
                        value = stringResource(R.string.today_macro_value, number(macro.grams)),
                        target = macro.targetGrams?.let {
                            stringResource(R.string.today_macro_target, number(it))
                        },
                        progress = macro.fraction,
                        ink = macroInk(macro.nutrient),
                        onClick = onOpenIn,
                    )
                    Spacer(Modifier.height(OhTheme.space.s2))
                }
                if (energy.waterMl != null) {
                    OhBarRow(
                        label = stringResource(R.string.nutrient_water),
                        value = stringResource(R.string.nutrition_ml, number(energy.waterMl)),
                        target = energy.waterTargetMl?.let {
                            stringResource(R.string.today_water_target, number(it))
                        },
                        progress = energy.waterTargetMl?.takeIf { it > 0.0 }
                            ?.let { (energy.waterMl / it).toFloat().coerceIn(0f, 1f) },
                        ink = OhTheme.colors.tealInk,
                        onClick = onOpenWater,
                    )
                }
            }
        }
        Spacer(Modifier.height(OhTheme.space.s3))
        when (val eta = energy.goalEta) {
            is GoalEtaState.Window -> CardLink(
                text = stringResource(
                    R.string.today_energy_goal_eta,
                    shortDateText(eta.start),
                    shortDateText(eta.end),
                ),
                onClick = onOpenGoal,
            )

            GoalEtaState.NeedsData -> Text(
                text = stringResource(R.string.today_energy_goal_unknown),
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
            )

            GoalEtaState.Steady -> Text(
                text = stringResource(R.string.today_energy_goal_steady),
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
            )

            GoalEtaState.Away -> Text(
                text = stringResource(R.string.today_energy_goal_away),
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
            )

            GoalEtaState.NoGoalWeight -> Text(
                text = stringResource(R.string.today_energy_goal_no_weight),
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
            )
        }
        if (energy.whatIf != null) {
            CardLink(text = stringResource(R.string.today_energy_what_if), onClick = onOpenGoal)
        }
        if (energy.floorApplied) {
            Spacer(Modifier.height(OhTheme.space.s2))
            Text(
                text = stringResource(R.string.today_energy_floor_note),
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
            )
        }
        energy.notes.forEach { note ->
            Spacer(Modifier.height(OhTheme.space.s1))
            Text(
                text = note,
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
            )
        }
        Spacer(Modifier.height(OhTheme.space.s2))
        Text(
            text = stringResource(R.string.today_energy_estimate),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
        Spacer(Modifier.height(OhTheme.space.s2))
        Column {
            CardLink(text = stringResource(R.string.chart_show_table), onClick = onShowTable)
            CardLink(
                text = stringResource(R.string.chart_how_calculated),
                onClick = onShowFormula,
            )
        }
    }
}

private fun TodayEnergyModel.progress(): Float {
    val target = targetKcal?.takeIf { it > 0.0 } ?: return 0f
    return (intakeKcal / target).toFloat().coerceIn(0f, 1f)
}

@Composable
private fun StepsCard(
    steps: TodayStepsModel?,
    onOpenGoal: () -> Unit,
    onOpenSource: () -> Unit,
    onShowTable: () -> Unit,
    onShowFormula: () -> Unit,
) {
    OhCard {
        OhCardHeader(
            eyebrow = stringResource(R.string.today_steps_eyebrow),
            title = stringResource(R.string.today_steps_title),
            glyph = OhModuleIcons.steps,
        )
        if (steps == null) {
            Spacer(Modifier.height(OhTheme.space.s2))
            OhEmptyState(
                title = stringResource(R.string.today_steps_empty_title),
                body = stringResource(R.string.today_steps_none),
            )
            return@OhCard
        }
        Spacer(Modifier.height(OhTheme.space.s5))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OhRing(
                progress = (steps.steps.toFloat() / steps.goal).coerceIn(0f, 1f),
                centre = number(steps.steps.toDouble()),
                contentDescription = stringResource(
                    R.string.today_steps_ring_desc,
                    number(steps.steps.toDouble()),
                    number(steps.goal.toDouble()),
                ),
                diameter = OhTheme.dims.stepsRing,
            )
            Spacer(Modifier.width(OhTheme.space.s5))
            Column(modifier = Modifier.weight(1f)) {
                val details = listOfNotNull(
                    steps.activeMinutes?.let {
                        stringResource(R.string.today_steps_minutes, number(it))
                    },
                    steps.distanceKm?.let {
                        stringResource(R.string.today_steps_distance, number(it, 1))
                    },
                )
                if (details.isNotEmpty()) {
                    Text(
                        text = details.joinToString(" · "),
                        style = OhTheme.type.footnote.tabular(),
                        color = OhTheme.colors.label2,
                    )
                    Spacer(Modifier.height(OhTheme.space.s1))
                }
                CardLink(
                    text = stringResource(
                        R.string.today_steps_goal,
                        number(steps.goal.toDouble()),
                    ),
                    onClick = onOpenGoal,
                )
                if (steps.sourceLabel != null) {
                    CardLink(
                        text = stringResource(R.string.today_steps_source, steps.sourceLabel),
                        onClick = onOpenSource,
                    )
                }
            }
        }
        Spacer(Modifier.height(OhTheme.space.s2))
        Column {
            CardLink(text = stringResource(R.string.chart_show_table), onClick = onShowTable)
            CardLink(
                text = stringResource(R.string.chart_how_calculated),
                onClick = onShowFormula,
            )
        }
    }
}

@Composable
private fun WeightCard(
    weight: TodayWeightModel?,
    unitSystem: app.openhealth.core.preferences.UnitSystem,
    onOpen: () -> Unit,
    onLogWeight: () -> Unit,
    onShowTable: () -> Unit,
    onShowFormula: () -> Unit,
) {
    OhCard {
        OhCardHeader(
            eyebrow = stringResource(R.string.today_weight_eyebrow),
            title = stringResource(R.string.today_weight_title),
            glyph = OhModuleIcons.weight,
        )
        if (weight == null) {
            Spacer(Modifier.height(OhTheme.space.s2))
            OhEmptyState(
                title = stringResource(R.string.today_weight_empty_title),
                body = stringResource(R.string.today_weight_none),
                actionLabel = stringResource(R.string.today_weight_empty_action),
                onAction = onLogWeight,
            )
            return@OhCard
        }
        Spacer(Modifier.height(OhTheme.space.s5))
        val imperial = unitSystem == app.openhealth.core.preferences.UnitSystem.IMPERIAL
        val shown = massIn(weight.weightKg, imperial)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = number(shown, decimals = 1),
                style = OhTheme.type.display.copy(fontWeight = OhWeight.semibold)
                    .tabular()
                    .ltrFigures(),
                color = OhTheme.colors.label,
                modifier = Modifier
                    .heightIn(min = OhTheme.dims.touchTarget)
                    .ohClickable(
                        interactionSource = rememberInteraction(),
                        onClick = onOpen,
                    ),
            )
            Spacer(Modifier.width(OhTheme.space.s0))
            Text(
                text = stringResource(if (imperial) R.string.unit_pounds else R.string.unit_kg),
                style = OhTheme.type.subhead,
                color = OhTheme.colors.label2,
            )
        }
        weight.weeklyChangeKg?.let { change ->
            // A change in either direction is neutral unless the person's own goal
            // makes it good or bad; the card never decides that for them (§2.8).
            val ink = OhTheme.colors.label2
            Spacer(Modifier.height(OhTheme.space.s0))
            Text(
                text = stringResource(
                    R.string.today_weight_week,
                    signedMass(change, imperial),
                ),
                style = OhTheme.type.footnote.tabular().ltrFigures(),
                color = ink,
            )
        }
        if (weight.spark.size >= 2) {
            Spacer(Modifier.height(OhTheme.space.s3))
            OhSparkline(
                points = weight.spark,
                goal = weight.goalKg,
                description = weight.sparkDescription,
            )
        }
        weight.goalKg?.let { goal ->
            Spacer(Modifier.height(OhTheme.space.s2))
            Text(
                text = stringResource(
                    R.string.today_weight_goal,
                    "${number(massIn(goal, imperial), 1)} " +
                        stringResource(if (imperial) R.string.unit_pounds else R.string.unit_kg),
                ),
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
            )
        }
        Spacer(Modifier.height(OhTheme.space.s2))
        Column {
            CardLink(text = stringResource(R.string.chart_show_table), onClick = onShowTable)
            CardLink(
                text = stringResource(R.string.chart_how_calculated),
                onClick = onShowFormula,
            )
        }
    }
}

@Composable
private fun DueCard(due: List<TodayDueItem>, onAction: (TodayDueAction) -> Unit) {
    OhCard {
        OhCardHeader(
            eyebrow = stringResource(R.string.today_due_eyebrow),
            title = stringResource(R.string.today_due_title),
            glyph = OhIcons.check,
        )
        if (due.isEmpty()) {
            Spacer(Modifier.height(OhTheme.space.s2))
            Text(
                text = stringResource(R.string.today_due_none),
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
            )
            return@OhCard
        }
        Spacer(Modifier.height(OhTheme.space.s3))
        OhInsetGroup {
            due.forEachIndexed { index, item ->
                OhRow(
                    title = item.title,
                    subtitle = item.body,
                    showDivider = index > 0,
                    onClick = { onAction(item.action) },
                    trailing = {
                        Text(
                            text = stringResource(R.string.today_due_action),
                            style = OhTheme.type.footnote,
                            color = OhTheme.colors.accInk,
                        )
                    },
                )
            }
        }
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

@Composable
private fun ReadinessSheet(readiness: ReadinessCardModel, onDismiss: () -> Unit) {
    OhModalSheet(
        visible = true,
        onDismiss = onDismiss,
        title = stringResource(R.string.today_readiness_sheet_title),
        cancelLabel = stringResource(R.string.vitals_provenance_close),
        onCancel = onDismiss,
    ) {
        Text(
            text = stringResource(R.string.chart_readiness_formula_body),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
        Spacer(Modifier.height(OhTheme.space.s4))
        OhSectionTitle(stringResource(R.string.today_readiness_sheet_inputs))
        OhInsetGroup {
            readiness.parts.forEachIndexed { index, part ->
                OhRow(
                    title = part.label,
                    value = part.score.toString(),
                    subtitle = part.weightText,
                    showDivider = index > 0,
                )
            }
        }
        Spacer(Modifier.height(OhTheme.space.s4))
        Text(
            text = stringResource(R.string.today_readiness_sheet_version, readiness.version),
            style = OhTheme.type.caption,
            color = OhTheme.colors.label2,
        )
        Spacer(Modifier.height(OhTheme.space.s3))
        Text(
            text = citationLabel(ChartRegistry.of(ChartId.READINESS).citation),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
        Text(
            text = citationTitle(ChartRegistry.of(ChartId.READINESS).citation),
            style = OhTheme.type.caption,
            color = OhTheme.colors.label2,
        )
    }
}

@Composable
private fun GoalSheet(
    energy: TodayEnergyModel,
    today: LocalDate,
    unitSystem: app.openhealth.core.preferences.UnitSystem,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
) {
    val whatIf = energy.whatIf ?: return
    var delta by rememberSaveable { mutableFloatStateOf(whatIf.initialDailyDeltaKcal.toFloat()) }
    val rate = delta * DAYS_PER_WEEK / KCAL_PER_KG
    val window = (
        app.openhealth.core.formula.GoalEta.whatIf(
            currentWeightKg = whatIf.currentWeightKg,
            goalWeightKg = whatIf.goalWeightKg,
            dailyDeltaKcal = delta.toDouble(),
            today = today,
        ) as? app.openhealth.core.formula.FormulaOutcome.Computed
        )?.value
    OhModalSheet(
        visible = true,
        onDismiss = onDismiss,
        title = stringResource(R.string.today_goal_sheet_title),
        cancelLabel = stringResource(R.string.vitals_provenance_close),
        onCancel = onDismiss,
    ) {
        Text(
            text = stringResource(R.string.today_goal_sheet_body),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
        Spacer(Modifier.height(OhTheme.space.s2))
        Text(
            text = stringResource(R.string.today_goal_sheet_citation),
            style = OhTheme.type.caption,
            color = OhTheme.colors.label2,
        )
        Spacer(Modifier.height(OhTheme.space.s5))
        Text(
            text = stringResource(R.string.today_goal_sheet_daily),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label,
        )
        OhSlider(
            value = delta,
            onValueChange = { delta = it },
            valueRange = whatIf.minimumDailyDeltaKcal.toFloat()..whatIf.maximumDailyDeltaKcal
                .toFloat(),
            contentDescription = stringResource(R.string.today_goal_sheet_daily),
        )
        Text(
            text = stringResource(R.string.today_goal_what_if_value, number(delta.toDouble())),
            style = OhTheme.type.body.tabular(),
            color = OhTheme.colors.label,
        )
        Spacer(Modifier.height(OhTheme.space.s4))
        OhInsetGroup {
            OhRow(
                title = stringResource(R.string.today_goal_sheet_rate),
                value = stringResource(
                    R.string.stats_weekly_rate,
                    signedMass(
                        rate,
                        unitSystem == app.openhealth.core.preferences.UnitSystem.IMPERIAL,
                    ),
                ),
            )
            OhRow(
                title = stringResource(R.string.today_goal_sheet_target),
                value = "${number(
                    (
                        energy.targetKcal
                            ?: ((energy.outLowKcal + energy.outHighKcal) / 2.0)
                        ) + delta,
                )} ${stringResource(R.string.unit_kcal)}",
            )
            OhRow(
                title = stringResource(R.string.today_goal_sheet_window),
                value = window?.let {
                    stringResource(
                        R.string.today_goal_window_range,
                        shortDateText(it.start),
                        shortDateText(it.end),
                    )
                } ?: stringResource(R.string.today_energy_goal_unknown),
                showDivider = true,
            )
        }
        Spacer(Modifier.height(OhTheme.space.s3))
        Text(
            text = stringResource(R.string.today_goal_sheet_what_if_body),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
        Spacer(Modifier.height(OhTheme.space.s5))
        OhButton(
            text = stringResource(R.string.today_goal_sheet_save),
            onClick = {
                val base = energy.targetKcal
                    ?: ((energy.outLowKcal + energy.outHighKcal) / 2.0)
                onSave(
                    (base + delta).coerceAtLeast(
                        maxOf(MIN_GOAL_TARGET_KCAL, whatIf.minimumTargetKcal),
                    ),
                )
            },
        )
    }
}

@Composable
private fun StepGoalSheet(current: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var text by rememberSaveable { mutableStateOf(current.toString()) }
    val parsed = text.toIntOrNull()?.takeIf { it in MIN_STEP_GOAL..MAX_STEP_GOAL }
    OhModalSheet(
        visible = true,
        onDismiss = onDismiss,
        title = stringResource(R.string.today_step_goal_title),
        cancelLabel = stringResource(R.string.vitals_provenance_close),
        onCancel = onDismiss,
    ) {
        Text(
            text = stringResource(R.string.today_step_goal_body),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
        Spacer(Modifier.height(OhTheme.space.s4))
        app.openhealth.ui.components.OhTextField(
            value = text,
            onValueChange = { text = it.filter(Char::isDigit).take(MAX_GOAL_DIGITS) },
            placeholder = stringResource(R.string.today_step_goal_field),
        )
        Spacer(Modifier.height(OhTheme.space.s5))
        OhButton(
            text = stringResource(R.string.today_step_goal_save),
            onClick = { parsed?.let(onSave) },
            enabled = parsed != null,
        )
    }
}

@Composable
private fun CardLink(text: String, onClick: () -> Unit) {
    val interaction = rememberInteraction()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(OhTheme.radius.sm))
            .ohClickable(interactionSource = interaction, onClick = onClick)
            .heightIn(min = OhTheme.dims.touchTarget)
            .padding(vertical = OhTheme.space.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, style = OhTheme.type.footnote, color = OhTheme.colors.label2)
    }
}

@Composable
private fun rangeText(low: Double, high: Double): String =
    if (kotlin.math.abs(high - low) < RANGE_MERGE_KCAL) {
        number((low + high) / 2.0)
    } else {
        "${number(low)}–${number(high)}"
    }

@Composable
private fun macroLabel(nutrient: Nutrient): String = stringResource(
    when (nutrient) {
        Nutrient.PROTEIN -> R.string.nutrient_protein
        Nutrient.CARBS -> R.string.nutrient_carbs
        Nutrient.FAT -> R.string.nutrient_fat
        Nutrient.FIBRE -> R.string.nutrient_fibre
        else -> R.string.nutrient_energy
    },
)

@Composable
private fun macroInk(nutrient: Nutrient) = when (nutrient) {
    Nutrient.PROTEIN -> OhTheme.colors.green
    Nutrient.CARBS -> OhTheme.colors.blue
    Nutrient.FAT -> OhTheme.colors.purple
    else -> OhTheme.colors.teal
}

private fun massIn(kg: Double, imperial: Boolean): Double = if (imperial) {
    app.openhealth.core.units.Measure(kg, app.openhealth.core.units.UcumUnit.KILOGRAM)
        .convertTo(app.openhealth.core.units.UcumUnit.POUND).value
} else {
    kg
}

private fun signedMass(kg: Double, imperial: Boolean): String {
    val value = massIn(kg, imperial)
    val rounded = kotlin.math.round(value * TENTHS) / TENTHS
    val unit = if (imperial) "lb" else "kg"
    if (kotlin.math.abs(rounded) < TENTHS_HALF) return "0 $unit"
    val sign = if (rounded > 0) "+" else ""
    val text = java.text.NumberFormat.getNumberInstance(java.util.Locale.getDefault())
        .apply { maximumFractionDigits = 1 }
        .format(rounded)
        .replace('-', '\u2212')
    return "$sign$text $unit"
}

@Composable
private fun shortDateText(date: LocalDate): String {
    val javaDate = java.time.LocalDate.of(date.year, date.month.ordinal + 1, date.dayOfMonth)
    return javaDate.format(
        java.time.format.DateTimeFormatter.ofPattern("d MMM", observedLocale()),
    )
}

private fun LocalDate.plus(days: Int): LocalDate = this.plus(DatePeriod(days = days))

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
private const val READINESS_CHIPS = 3
private const val RANGE_MERGE_KCAL = 20.0
private const val DAYS_PER_WEEK = 7.0
private const val KCAL_PER_KG = 7_700.0
private const val MIN_STEP_GOAL = 1_000
private const val MAX_STEP_GOAL = 50_000
private const val MAX_GOAL_DIGITS = 5
private const val TENTHS = 10.0
private const val TENTHS_HALF = 0.05
private const val MIN_GOAL_TARGET_KCAL = 500.0
