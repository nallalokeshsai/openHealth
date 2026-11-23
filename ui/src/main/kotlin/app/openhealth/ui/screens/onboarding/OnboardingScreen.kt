package app.openhealth.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import app.openhealth.core.formula.ActivityLevel
import app.openhealth.core.formula.SexAtBirth
import app.openhealth.core.preferences.DietaryPattern
import app.openhealth.core.preferences.Equipment
import app.openhealth.core.preferences.ModulePreset
import app.openhealth.core.preferences.ProfileGoal
import app.openhealth.core.preferences.TrainingExperience
import app.openhealth.core.preferences.UnitSystem
import app.openhealth.ui.R
import app.openhealth.ui.components.OhButton
import app.openhealth.ui.components.OhButtonSize
import app.openhealth.ui.components.OhButtonVariant
import app.openhealth.ui.components.OhChip
import app.openhealth.ui.components.OhChipStrip
import app.openhealth.ui.components.OhIconButton
import app.openhealth.ui.components.OhInsetGroup
import app.openhealth.ui.components.OhRow
import app.openhealth.ui.components.OhStepper
import app.openhealth.ui.components.OhSwitch
import app.openhealth.ui.components.OhTextField
import app.openhealth.ui.icons.OhIcon
import app.openhealth.ui.icons.OhIcons
import app.openhealth.ui.screens.common.OhProgressLine
import app.openhealth.ui.screens.common.observedLocale
import app.openhealth.ui.theme.OhTheme
import app.openhealth.ui.theme.OhWeight

/**
 * The onboarding flow of `pr.md` §6.1. One question per screen, a quiet Skip that is
 * always visible, a 3 dp accent progress line (no dots, no percentages), and units and
 * locale pre-detected. Required tier-1 answers cannot be skipped; tapping Skip there
 * states why instead of advancing.
 */
@Composable
fun OnboardingScreen(
    step: OnboardingStep,
    answers: OnboardingAnswers,
    issue: OnboardingIssue?,
    ageBlocked: Boolean,
    weightLossAllowed: Boolean,
    onChange: (OnboardingAnswers) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onTryDemo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (ageBlocked) {
        AgeBlockedScreen(onBack = onBack, modifier = modifier)
        return
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OhTheme.colors.bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(OhTheme.dims.touchTarget)
                .padding(horizontal = OhTheme.space.s6),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (step != OnboardingStep.Welcome) {
                OhIconButton(
                    icon = OhIcons.chevronLeft,
                    contentDescription = stringResource(R.string.oh_back),
                    onClick = onBack,
                )
            } else {
                Spacer(Modifier.width(OhTheme.dims.iconButton))
            }
            Spacer(Modifier.weight(1f))
            if (step != OnboardingStep.Welcome && step != OnboardingStep.Finish) {
                OhButton(
                    text = stringResource(R.string.onb_skip),
                    onClick = onSkip,
                    variant = OhButtonVariant.Quiet,
                    size = OhButtonSize.ExtraSmall,
                )
            }
        }
        OhProgressLine(fraction = step.progress())
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OhTheme.space.s6),
        ) {
            Spacer(Modifier.height(OhTheme.space.s7))
            when (step) {
                OnboardingStep.Welcome -> Welcome(onNext = onNext, onTryDemo = onTryDemo)
                OnboardingStep.Name -> NameStep(answers, onChange, issue)
                OnboardingStep.BirthDate -> BirthDateStep(answers, onChange, issue)
                OnboardingStep.Sex -> SexStep(answers, onChange)
                OnboardingStep.Height -> MeasureStep(
                    title = stringResource(R.string.onb_height_title),
                    subtitle = stringResource(R.string.onb_height_body),
                    value = answers.height,
                    unit = if (answers.units == UnitSystem.IMPERIAL) {
                        stringResource(R.string.unit_inches)
                    } else {
                        stringResource(R.string.unit_cm)
                    },
                    onChange = { onChange(answers.copy(height = it)) },
                    issue = issue,
                )
                OnboardingStep.Weight -> MeasureStep(
                    title = stringResource(R.string.onb_weight_title),
                    subtitle = stringResource(R.string.onb_weight_body),
                    value = answers.weight,
                    unit = if (answers.units == UnitSystem.IMPERIAL) {
                        stringResource(R.string.unit_pounds)
                    } else {
                        stringResource(R.string.unit_kg)
                    },
                    onChange = { onChange(answers.copy(weight = it)) },
                    issue = issue,
                )
                OnboardingStep.Units -> UnitsStep(answers, onChange)
                OnboardingStep.Goal -> GoalStep(answers, onChange, weightLossAllowed)
                OnboardingStep.WeightNeutral -> WeightNeutralStep(answers, onChange)
                OnboardingStep.TierTwoIntro -> TierIntro(
                    title = stringResource(R.string.onb_tier_two_title),
                    body = stringResource(R.string.onb_tier_two_body),
                )
                OnboardingStep.Activity -> ActivityStep(answers, onChange)
                OnboardingStep.Experience -> ExperienceStep(answers, onChange)
                OnboardingStep.Equipment -> EquipmentStep(answers, onChange)
                OnboardingStep.Diet -> DietStep(answers, onChange)
                OnboardingStep.Allergies -> TextStep(
                    title = stringResource(R.string.onb_allergies_title),
                    subtitle = stringResource(R.string.onb_allergies_body),
                    value = answers.allergies,
                    onChange = { onChange(answers.copy(allergies = it)) },
                )
                OnboardingStep.Sleep -> SleepStep(answers, onChange)
                OnboardingStep.Modules -> ModulesStep(answers, onChange)
                OnboardingStep.TierThreeIntro -> TierIntro(
                    title = stringResource(R.string.onb_tier_three_title),
                    body = stringResource(R.string.onb_tier_three_body),
                )
                OnboardingStep.BodyFat -> MeasureStep(
                    title = stringResource(R.string.onb_body_fat_title),
                    subtitle = stringResource(R.string.onb_body_fat_body),
                    value = answers.bodyFat,
                    unit = stringResource(R.string.unit_percent),
                    onChange = { onChange(answers.copy(bodyFat = it)) },
                    issue = issue,
                )
                OnboardingStep.RestingHeartRate -> MeasureStep(
                    title = stringResource(R.string.onb_rhr_title),
                    subtitle = stringResource(R.string.onb_rhr_body),
                    value = answers.restingHeartRate,
                    unit = stringResource(R.string.unit_bpm),
                    onChange = { onChange(answers.copy(restingHeartRate = it)) },
                    issue = issue,
                )
                OnboardingStep.BloodPressure -> BloodPressureStep(answers, onChange, issue)
                OnboardingStep.Measurements -> MeasurementsStep(answers, onChange, issue)
                OnboardingStep.Injuries -> TextStep(
                    title = stringResource(R.string.onb_injuries_title),
                    subtitle = stringResource(R.string.onb_injuries_body),
                    value = answers.injuries,
                    onChange = { onChange(answers.copy(injuries = it)) },
                    multiline = true,
                )
                OnboardingStep.Medications -> TextStep(
                    title = stringResource(R.string.onb_medications_title),
                    subtitle = stringResource(R.string.onb_medications_body),
                    value = answers.medications,
                    onChange = { onChange(answers.copy(medications = it)) },
                    multiline = true,
                )
                OnboardingStep.Cycle -> CycleStep(answers, onChange)
                OnboardingStep.GoalWeight -> GoalWeightStep(answers, onChange, issue)
                OnboardingStep.Finish -> FinishStep()
            }
            if (issue == OnboardingIssue.REQUIRED) {
                Spacer(Modifier.height(OhTheme.space.s5))
                Text(
                    text = stringResource(R.string.onb_required),
                    style = OhTheme.type.footnote,
                    color = OhTheme.colors.orangeInk,
                )
            }
            Spacer(Modifier.height(OhTheme.space.s8))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OhTheme.space.s6, vertical = OhTheme.space.s5),
        ) {
            if (step != OnboardingStep.Welcome) {
                OhButton(
                    text = if (step == OnboardingStep.Finish) {
                        stringResource(R.string.onb_finish_action)
                    } else {
                        stringResource(R.string.onb_continue)
                    },
                    onClick = onNext,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (step == OnboardingStep.TierTwoIntro || step == OnboardingStep.TierThreeIntro) {
                Spacer(Modifier.height(OhTheme.space.s2))
                OhButton(
                    text = stringResource(R.string.onb_fill_later),
                    onClick = onSkip,
                    variant = OhButtonVariant.Quiet,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun Welcome(onNext: () -> Unit, onTryDemo: () -> Unit) {
    QuestionHeader(
        title = stringResource(R.string.onb_welcome_title),
        body = stringResource(R.string.onb_welcome_body),
    )
    Spacer(Modifier.height(OhTheme.space.s7))
    OhButton(
        text = stringResource(R.string.onb_start),
        onClick = onNext,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(OhTheme.space.s2))
    OhButton(
        text = stringResource(R.string.onb_try_sample),
        onClick = onTryDemo,
        variant = OhButtonVariant.Secondary,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(OhTheme.space.s7))
    Text(
        text = stringResource(R.string.onb_welcome_disclaimer),
        style = OhTheme.type.footnote,
        color = OhTheme.colors.label2,
    )
    Spacer(Modifier.height(OhTheme.space.s5))
    Text(
        text = stringResource(R.string.onb_welcome_local),
        style = OhTheme.type.footnote,
        color = OhTheme.colors.label2,
    )
}

@Composable
private fun NameStep(
    answers: OnboardingAnswers,
    onChange: (OnboardingAnswers) -> Unit,
    issue: OnboardingIssue?,
) {
    QuestionHeader(
        title = stringResource(R.string.onb_name_title),
        body = stringResource(R.string.onb_name_body),
    )
    Spacer(Modifier.height(OhTheme.space.s7))
    OhTextField(
        value = answers.name,
        onValueChange = { onChange(answers.copy(name = it)) },
        placeholder = stringResource(R.string.onb_name_placeholder),
        error = if (issue == OnboardingIssue.NAME_REQUIRED) {
            stringResource(R.string.onb_name_required)
        } else {
            null
        },
    )
}

@Composable
private fun BirthDateStep(
    answers: OnboardingAnswers,
    onChange: (OnboardingAnswers) -> Unit,
    issue: OnboardingIssue?,
) {
    QuestionHeader(
        title = stringResource(R.string.onb_birth_title),
        body = stringResource(R.string.onb_birth_body),
    )
    Spacer(Modifier.height(OhTheme.space.s7))
    OhTextField(
        value = answers.birthDate,
        onValueChange = { onChange(answers.copy(birthDate = it)) },
        placeholder = stringResource(R.string.onb_birth_placeholder),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        error = when (issue) {
            OnboardingIssue.DATE_INVALID -> stringResource(R.string.onb_birth_invalid)
            OnboardingIssue.DATE_UNDER_SIXTEEN -> stringResource(R.string.onb_birth_under_sixteen)
            else -> null
        },
    )
    Spacer(Modifier.height(OhTheme.space.s5))
    Text(
        text = stringResource(R.string.onb_birth_note),
        style = OhTheme.type.footnote,
        color = OhTheme.colors.label2,
    )
}

@Composable
private fun SexStep(answers: OnboardingAnswers, onChange: (OnboardingAnswers) -> Unit) {
    QuestionHeader(
        title = stringResource(R.string.onb_sex_title),
        body = stringResource(R.string.onb_sex_body),
    )
    Spacer(Modifier.height(OhTheme.space.s7))
    ChoiceGroup(
        choices = listOf(
            Choice("female", stringResource(R.string.onb_sex_female), null),
            Choice("male", stringResource(R.string.onb_sex_male), null),
            Choice("unspecified", stringResource(R.string.onb_sex_not_saying), null),
        ),
        selectedKey = when {
            answers.preferNotToSay -> "unspecified"
            answers.sexAtBirth == SexAtBirth.FEMALE -> "female"
            answers.sexAtBirth == SexAtBirth.MALE -> "male"
            else -> null
        },
        onSelect = { key ->
            onChange(
                when (key) {
                    "female" -> answers.copy(
                        sexAtBirth = SexAtBirth.FEMALE,
                        preferNotToSay = false,
                    )
                    "male" -> answers.copy(sexAtBirth = SexAtBirth.MALE, preferNotToSay = false)
                    else -> answers.copy(sexAtBirth = null, preferNotToSay = true)
                },
            )
        },
    )
}

@Composable
private fun UnitsStep(answers: OnboardingAnswers, onChange: (OnboardingAnswers) -> Unit) {
    QuestionHeader(
        title = stringResource(R.string.onb_units_title),
        body = stringResource(R.string.onb_units_body),
    )
    Spacer(Modifier.height(OhTheme.space.s7))
    ChoiceGroup(
        choices = listOf(
            Choice(
                UnitSystem.METRIC.key,
                stringResource(R.string.onb_units_metric),
                stringResource(R.string.onb_units_metric_body),
            ),
            Choice(
                UnitSystem.IMPERIAL.key,
                stringResource(R.string.onb_units_imperial),
                stringResource(R.string.onb_units_imperial_body),
            ),
        ),
        selectedKey = answers.units.key,
        onSelect = { key ->
            UnitSystem.fromKey(key)?.let { onChange(answers.copy(units = it)) }
        },
    )
}

@Composable
private fun GoalStep(
    answers: OnboardingAnswers,
    onChange: (OnboardingAnswers) -> Unit,
    weightLossAllowed: Boolean,
) {
    QuestionHeader(
        title = stringResource(R.string.onb_goal_title),
        body = stringResource(R.string.onb_goal_body),
    )
    Spacer(Modifier.height(OhTheme.space.s7))
    val goals = ProfileGoal.entries.filterNot { it == ProfileGoal.LOSE_FAT && !weightLossAllowed }
    ChoiceGroup(
        choices = goals.map { Choice(it.key, it.label(), null) },
        selectedKey = answers.goal?.key,
        onSelect = { key -> ProfileGoal.fromKey(key)?.let { onChange(answers.copy(goal = it)) } },
    )
    if (!weightLossAllowed) {
        Spacer(Modifier.height(OhTheme.space.s5))
        Text(
            text = stringResource(R.string.onb_goal_under_eighteen),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
    }
}

@Composable
private fun WeightNeutralStep(answers: OnboardingAnswers, onChange: (OnboardingAnswers) -> Unit) {
    QuestionHeader(
        title = stringResource(R.string.onb_weight_neutral_title),
        body = stringResource(R.string.onb_weight_neutral_body),
    )
    Spacer(Modifier.height(OhTheme.space.s7))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.onb_weight_neutral_switch),
            style = OhTheme.type.body,
            color = OhTheme.colors.label,
            modifier = Modifier.weight(1f),
        )
        OhSwitch(
            checked = answers.weightNeutral,
            onCheckedChange = { onChange(answers.copy(weightNeutral = it)) },
            contentDescription = stringResource(R.string.onb_weight_neutral_switch),
        )
    }
    Spacer(Modifier.height(OhTheme.space.s5))
    Text(
        text = stringResource(R.string.onb_weight_neutral_note),
        style = OhTheme.type.footnote,
        color = OhTheme.colors.label2,
    )
}

@Composable
private fun TierIntro(title: String, body: String) {
    QuestionHeader(title = title, body = body)
}

@Composable
private fun ActivityStep(answers: OnboardingAnswers, onChange: (OnboardingAnswers) -> Unit) {
    QuestionHeader(
        title = stringResource(R.string.onb_activity_title),
        body = stringResource(R.string.onb_activity_body),
    )
    Spacer(Modifier.height(OhTheme.space.s7))
    ChoiceGroup(
        choices = ActivityLevel.entries.map { Choice(it.name, it.label(), null) },
        selectedKey = answers.activity?.name,
        onSelect = { key ->
            ActivityLevel.entries.firstOrNull { it.name == key }?.let {
                onChange(answers.copy(activity = it))
            }
        },
    )
}

@Composable
private fun ExperienceStep(answers: OnboardingAnswers, onChange: (OnboardingAnswers) -> Unit) {
    QuestionHeader(
        title = stringResource(R.string.onb_experience_title),
        body = stringResource(R.string.onb_experience_body),
    )
    Spacer(Modifier.height(OhTheme.space.s7))
    ChoiceGroup(
        choices = TrainingExperience.entries.map { Choice(it.key, it.label(), null) },
        selectedKey = answers.experience?.key,
        onSelect = { key ->
            TrainingExperience.fromKey(key)?.let { onChange(answers.copy(experience = it)) }
        },
    )
}

@Composable
private fun EquipmentStep(answers: OnboardingAnswers, onChange: (OnboardingAnswers) -> Unit) {
    QuestionHeader(
        title = stringResource(R.string.onb_equipment_title),
        body = stringResource(R.string.onb_equipment_body),
    )
    Spacer(Modifier.height(OhTheme.space.s7))
    OhChipStrip {
        Equipment.entries.forEach { equipment ->
            OhChip(
                text = equipment.label(),
                selected = equipment in answers.equipment,
                onClick = {
                    onChange(
                        answers.copy(
                            equipment = if (equipment in answers.equipment) {
                                answers.equipment - equipment
                            } else {
                                answers.equipment + equipment
                            },
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun DietStep(answers: OnboardingAnswers, onChange: (OnboardingAnswers) -> Unit) {
    QuestionHeader(
        title = stringResource(R.string.onb_diet_title),
        body = stringResource(R.string.onb_diet_body),
    )
    Spacer(Modifier.height(OhTheme.space.s7))
    ChoiceGroup(
        choices = DietaryPattern.entries.map { Choice(it.key, it.label(), null) },
        selectedKey = answers.diet?.key,
        onSelect = { key ->
            DietaryPattern.fromKey(key)?.let { onChange(answers.copy(diet = it)) }
        },
    )
}

@Composable
private fun SleepStep(answers: OnboardingAnswers, onChange: (OnboardingAnswers) -> Unit) {
    QuestionHeader(
        title = stringResource(R.string.onb_sleep_title),
        body = stringResource(R.string.onb_sleep_body),
    )
    Spacer(Modifier.height(OhTheme.space.s7))
    Text(
        text = stringResource(R.string.onb_sleep_bedtime),
        style = OhTheme.type.subhead,
        color = OhTheme.colors.label2,
    )
    HourStepper(
        hour = answers.sleepStartHour,
        onChange = { onChange(answers.copy(sleepStartHour = it)) },
    )
    Spacer(Modifier.height(OhTheme.space.s5))
    Text(
        text = stringResource(R.string.onb_sleep_wake),
        style = OhTheme.type.subhead,
        color = OhTheme.colors.label2,
    )
    HourStepper(
        hour = answers.sleepEndHour,
        onChange = { onChange(answers.copy(sleepEndHour = it)) },
    )
}

@Composable
private fun ModulesStep(answers: OnboardingAnswers, onChange: (OnboardingAnswers) -> Unit) {
    QuestionHeader(
        title = stringResource(R.string.onb_modules_title),
        body = stringResource(R.string.onb_modules_body),
    )
    Spacer(Modifier.height(OhTheme.space.s7))
    ChoiceGroup(
        choices = ModulePreset.entries.map { Choice(it.key, it.label(), it.body()) },
        selectedKey = answers.preset.key,
        onSelect = { key ->
            ModulePreset.fromKey(key)?.let { onChange(answers.copy(preset = it)) }
        },
    )
    Spacer(Modifier.height(OhTheme.space.s5))
    Text(
        text = stringResource(R.string.onb_modules_note),
        style = OhTheme.type.footnote,
        color = OhTheme.colors.label2,
    )
}

@Composable
private fun BloodPressureStep(
    answers: OnboardingAnswers,
    onChange: (OnboardingAnswers) -> Unit,
    issue: OnboardingIssue?,
) {
    QuestionHeader(
        title = stringResource(R.string.onb_bp_title),
        body = stringResource(R.string.onb_bp_body),
    )
    Spacer(Modifier.height(OhTheme.space.s7))
    MeasureStep(
        title = stringResource(R.string.onb_bp_systolic),
        subtitle = null,
        value = answers.systolic,
        unit = stringResource(R.string.unit_mmhg),
        onChange = { onChange(answers.copy(systolic = it)) },
        issue = issue,
    )
    Spacer(Modifier.height(OhTheme.space.s5))
    MeasureStep(
        title = stringResource(R.string.onb_bp_diastolic),
        subtitle = null,
        value = answers.diastolic,
        unit = stringResource(R.string.unit_mmhg),
        onChange = { onChange(answers.copy(diastolic = it)) },
        issue = issue,
    )
}

@Composable
private fun MeasurementsStep(
    answers: OnboardingAnswers,
    onChange: (OnboardingAnswers) -> Unit,
    issue: OnboardingIssue?,
) {
    QuestionHeader(
        title = stringResource(R.string.onb_tape_title),
        body = stringResource(R.string.onb_tape_body),
    )
    Spacer(Modifier.height(OhTheme.space.s7))
    val unit = if (answers.units == UnitSystem.IMPERIAL) {
        stringResource(R.string.unit_inches)
    } else {
        stringResource(R.string.unit_cm)
    }
    listOf(
        Triple(stringResource(R.string.onb_tape_neck), answers.neck) { v: String ->
            onChange(answers.copy(neck = v))
        },
        Triple(stringResource(R.string.onb_tape_chest), answers.chest) { v: String ->
            onChange(answers.copy(chest = v))
        },
        Triple(stringResource(R.string.onb_tape_waist), answers.waist) { v: String ->
            onChange(answers.copy(waist = v))
        },
        Triple(stringResource(R.string.onb_tape_hips), answers.hips) { v: String ->
            onChange(answers.copy(hips = v))
        },
    ).forEachIndexed { index, (label, value, setter) ->
        if (index > 0) Spacer(Modifier.height(OhTheme.space.s5))
        MeasureStep(
            title = label,
            subtitle = null,
            value = value,
            unit = unit,
            onChange = setter,
            issue = issue,
        )
    }
}

@Composable
private fun CycleStep(answers: OnboardingAnswers, onChange: (OnboardingAnswers) -> Unit) {
    QuestionHeader(
        title = stringResource(R.string.onb_cycle_title),
        body = stringResource(R.string.onb_cycle_body),
    )
    Spacer(Modifier.height(OhTheme.space.s7))
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.onb_cycle_switch),
            style = OhTheme.type.body,
            color = OhTheme.colors.label,
            modifier = Modifier.weight(1f),
        )
        OhSwitch(
            checked = answers.cycleTracking,
            onCheckedChange = { onChange(answers.copy(cycleTracking = it)) },
            contentDescription = stringResource(R.string.onb_cycle_switch),
        )
    }
}

@Composable
private fun GoalWeightStep(
    answers: OnboardingAnswers,
    onChange: (OnboardingAnswers) -> Unit,
    issue: OnboardingIssue?,
) {
    QuestionHeader(
        title = stringResource(R.string.onb_goal_weight_title),
        body = stringResource(R.string.onb_goal_weight_body),
    )
    Spacer(Modifier.height(OhTheme.space.s7))
    MeasureStep(
        title = stringResource(R.string.onb_goal_weight_value),
        subtitle = null,
        value = answers.goalWeight,
        unit = if (answers.units == UnitSystem.IMPERIAL) {
            stringResource(R.string.unit_pounds)
        } else {
            stringResource(R.string.unit_kg)
        },
        onChange = { onChange(answers.copy(goalWeight = it)) },
        issue = issue,
    )
    Spacer(Modifier.height(OhTheme.space.s5))
    Text(
        text = stringResource(R.string.onb_goal_date_title),
        style = OhTheme.type.subhead,
        color = OhTheme.colors.label2,
    )
    OhTextField(
        value = answers.goalDate,
        onValueChange = { onChange(answers.copy(goalDate = it)) },
        placeholder = stringResource(R.string.onb_goal_date_placeholder),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        error = if (issue == OnboardingIssue.DATE_INVALID) {
            stringResource(R.string.onb_birth_invalid)
        } else {
            null
        },
    )
}

@Composable
private fun FinishStep() {
    QuestionHeader(
        title = stringResource(R.string.onb_finish_title),
        body = stringResource(R.string.onb_finish_body),
    )
    Spacer(Modifier.height(OhTheme.space.s7))
    Text(
        text = stringResource(R.string.onb_finish_note),
        style = OhTheme.type.footnote,
        color = OhTheme.colors.label2,
    )
}

@Composable
private fun AgeBlockedScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OhTheme.colors.bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = OhTheme.space.s6),
    ) {
        Spacer(Modifier.height(OhTheme.space.s8))
        Text(
            text = stringResource(R.string.onb_age_blocked_title),
            style = OhTheme.type.large.copy(fontWeight = OhWeight.bold),
            color = OhTheme.colors.label,
        )
        Spacer(Modifier.height(OhTheme.space.s5))
        Text(
            text = stringResource(R.string.onb_age_blocked_body),
            style = OhTheme.type.body,
            color = OhTheme.colors.label2,
        )
        Spacer(Modifier.height(OhTheme.space.s8))
        OhButton(
            text = stringResource(R.string.onb_age_blocked_action),
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun QuestionHeader(title: String, body: String?) {
    Text(
        text = title,
        style = OhTheme.type.large.copy(fontWeight = OhWeight.bold),
        color = OhTheme.colors.label,
    )
    if (body != null) {
        Spacer(Modifier.height(OhTheme.space.s5))
        Text(text = body, style = OhTheme.type.subhead, color = OhTheme.colors.label2)
    }
}

private data class Choice(val key: String, val label: String, val body: String?)

@Composable
private fun ChoiceGroup(choices: List<Choice>, selectedKey: String?, onSelect: (String) -> Unit) {
    OhInsetGroup {
        choices.forEachIndexed { index, choice ->
            OhRow(
                title = choice.label,
                subtitle = choice.body,
                showDivider = index > 0,
                onClick = { onSelect(choice.key) },
                trailing = if (choice.key == selectedKey) {
                    {
                        OhIcon(
                            icon = OhIcons.check,
                            contentDescription = null,
                            size = OhTheme.dims.glyphInline,
                            tint = OhTheme.colors.accInk,
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun MeasureStep(
    title: String,
    subtitle: String?,
    value: String,
    unit: String,
    onChange: (String) -> Unit,
    issue: OnboardingIssue?,
) {
    Text(text = title, style = OhTheme.type.subhead, color = OhTheme.colors.label2)
    Spacer(Modifier.height(OhTheme.space.s1))
    OhTextField(
        value = value,
        onValueChange = onChange,
        placeholder = unit,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        trailing = {
            Text(text = unit, style = OhTheme.type.footnote, color = OhTheme.colors.label2)
        },
        error = when (issue) {
            OnboardingIssue.VALUE_REQUIRED -> stringResource(R.string.onb_value_required)
            OnboardingIssue.VALUE_OUT_OF_RANGE -> stringResource(R.string.onb_value_range)
            else -> null
        },
    )
    if (subtitle != null) {
        Spacer(Modifier.height(OhTheme.space.s1))
        Text(text = subtitle, style = OhTheme.type.footnote, color = OhTheme.colors.label2)
    }
}

@Composable
private fun TextStep(
    title: String,
    subtitle: String,
    value: String,
    onChange: (String) -> Unit,
    multiline: Boolean = false,
) {
    QuestionHeader(title = title, body = subtitle)
    Spacer(Modifier.height(OhTheme.space.s7))
    OhTextField(
        value = value,
        onValueChange = onChange,
        singleLine = !multiline,
    )
}

@Composable
private fun HourStepper(hour: Int, onChange: (Int) -> Unit) {
    OhStepper(
        value = HOUR_FORMAT.format(observedLocale(), hour),
        onDecrement = { onChange(previousHour(hour)) },
        onIncrement = { onChange(nextHour(hour)) },
    )
}

private fun OnboardingStep.progress(): Float {
    val index = ordinal.toFloat()
    val last = (OnboardingStep.entries.size - 1).toFloat()
    return if (last == 0f) 1f else index / last
}

@Composable
private fun ProfileGoal.label(): String = stringResource(
    when (this) {
        ProfileGoal.LOSE_FAT -> R.string.goal_lose_fat
        ProfileGoal.MAINTAIN -> R.string.goal_maintain
        ProfileGoal.GAIN_MUSCLE -> R.string.goal_gain_muscle
        ProfileGoal.IMPROVE_HEALTH -> R.string.goal_improve_health
        ProfileGoal.MANAGE_CONDITION -> R.string.goal_manage_condition
        ProfileGoal.JUST_TRACK -> R.string.goal_just_track
    },
)

@Composable
private fun ActivityLevel.label(): String = stringResource(
    when (this) {
        ActivityLevel.SEDENTARY -> R.string.activity_sedentary
        ActivityLevel.LIGHTLY_ACTIVE -> R.string.activity_light
        ActivityLevel.MODERATELY_ACTIVE -> R.string.activity_moderate
        ActivityLevel.VERY_ACTIVE -> R.string.activity_very
        ActivityLevel.EXTREMELY_ACTIVE -> R.string.activity_extremely
    },
)

@Composable
private fun TrainingExperience.label(): String = stringResource(
    when (this) {
        TrainingExperience.NONE -> R.string.experience_none
        TrainingExperience.BEGINNER -> R.string.experience_beginner
        TrainingExperience.INTERMEDIATE -> R.string.experience_intermediate
        TrainingExperience.ADVANCED -> R.string.experience_advanced
    },
)

@Composable
private fun Equipment.label(): String = stringResource(
    when (this) {
        Equipment.BODYWEIGHT -> R.string.equipment_bodyweight
        Equipment.DUMBBELLS -> R.string.equipment_dumbbells
        Equipment.BARBELL -> R.string.equipment_barbell
        Equipment.MACHINES -> R.string.equipment_machines
        Equipment.BANDS -> R.string.equipment_bands
        Equipment.CARDIO -> R.string.equipment_cardio
    },
)

@Composable
private fun DietaryPattern.label(): String = stringResource(
    when (this) {
        DietaryPattern.OMNIVORE -> R.string.diet_omnivore
        DietaryPattern.VEGETARIAN -> R.string.diet_vegetarian
        DietaryPattern.VEGAN -> R.string.diet_vegan
        DietaryPattern.PESCATARIAN -> R.string.diet_pescatarian
        DietaryPattern.HALAL -> R.string.diet_halal
        DietaryPattern.KOSHER -> R.string.diet_kosher
        DietaryPattern.OTHER -> R.string.diet_other
    },
)

@Composable
private fun ModulePreset.label(): String = stringResource(
    when (this) {
        ModulePreset.JUST_TRAINING -> R.string.preset_training
        ModulePreset.TRAINING_AND_BODY -> R.string.preset_training_body
        ModulePreset.EVERYTHING -> R.string.preset_everything
    },
)

@Composable
private fun ModulePreset.body(): String = stringResource(
    when (this) {
        ModulePreset.JUST_TRAINING -> R.string.preset_training_body_copy
        ModulePreset.TRAINING_AND_BODY -> R.string.preset_training_body_body_copy
        ModulePreset.EVERYTHING -> R.string.preset_everything_body_copy
    },
)

private fun previousHour(hour: Int): Int = (hour + HOURS_PER_DAY - 1) % HOURS_PER_DAY

private fun nextHour(hour: Int): Int = (hour + 1) % HOURS_PER_DAY

private const val HOURS_PER_DAY = 24
private const val HOUR_FORMAT = "%02d:00"
