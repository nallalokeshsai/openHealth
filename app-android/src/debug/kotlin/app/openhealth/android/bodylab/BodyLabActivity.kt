package app.openhealth.android.bodylab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import app.openhealth.core.body.AdiposeInputs
import app.openhealth.core.body.AdiposeModel
import app.openhealth.core.body.BodySex
import app.openhealth.core.body.Muscle
import app.openhealth.core.body.MuscleRecoveryState
import app.openhealth.core.exercise.ExerciseCatalogue
import app.openhealth.core.exercise.PlanCoverage
import app.openhealth.core.exercise.PlannedExercise
import app.openhealth.core.formula.FormulaOutcome
import app.openhealth.core.formula.MuscleRecovery
import app.openhealth.core.formula.RecoveryStep
import app.openhealth.ui.R
import app.openhealth.ui.body.BodyMapModel
import app.openhealth.ui.body.OhBodyMap
import app.openhealth.ui.body.OhBodyMapLegend
import app.openhealth.ui.body.bodyMapTableRows
import app.openhealth.ui.components.OhCard
import app.openhealth.ui.components.OhCardHeader
import app.openhealth.ui.components.OhChip
import app.openhealth.ui.components.OhChipStrip
import app.openhealth.ui.components.OhSlider
import app.openhealth.ui.icons.OhModuleIcons
import app.openhealth.ui.screens.common.number
import app.openhealth.ui.theme.OhTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Debug-only body-model harness (`BUILD-ROADMAP.md` §B4 deliverable 5): scrub body fat %,
 * waist and hours since load and watch the figure update. It is not a product screen and
 * never ships in a release build.
 */
class BodyLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            OhTheme(dark = true) {
                BodyLab()
            }
        }
    }

    @Composable
    private fun BodyLab() {
        var sex by rememberSaveable { mutableStateOf(BodySex.MALE) }
        var bodyFat by rememberSaveable { mutableStateOf(22f) }
        var waist by rememberSaveable { mutableStateOf(88f) }
        var hours by rememberSaveable { mutableStateOf(6f) }
        var planMode by rememberSaveable { mutableStateOf(false) }
        var showFat by rememberSaveable { mutableStateOf(true) }
        var selected by rememberSaveable { mutableStateOf<String?>(null) }
        var catalogued by remember { mutableStateOf(-1) }
        LaunchedEffect(Unit) {
            catalogued = withContext(Dispatchers.IO) { ExerciseCatalogue.bundled.size }
        }
        val adipose = AdiposeModel.compute(
            AdiposeInputs(
                sex = sex,
                bodyFatPercent = bodyFat.toDouble(),
                tapeCm = mapOf("waist" to waist.toDouble()),
            ),
        )
        val model = if (planMode) {
            BodyMapModel.plan(sex, PlanCoverage.compute(samplePlan()))
        } else {
            BodyMapModel.recovery(
                sex = sex,
                states = syntheticRecovery(hours.toDouble()),
                adipose = adipose.takeIf { showFat },
                selected = selected?.let { Muscle.fromKey(it) },
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(OhTheme.colors.bg)
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState())
                .padding(OhTheme.space.s6),
            verticalArrangement = Arrangement.spacedBy(OhTheme.space.s4),
        ) {
            Text(
                text = stringResource(R.string.body_lab_title),
                style = OhTheme.type.large,
                color = OhTheme.colors.label,
            )
            OhCard {
                OhCardHeader(
                    eyebrow = stringResource(R.string.body_lab_map_eyebrow),
                    title = if (planMode) {
                        stringResource(R.string.body_map_legend_plan)
                    } else {
                        stringResource(R.string.body_map_recovery)
                    },
                    glyph = OhModuleIcons.workout,
                )
                OhBodyMap(
                    model = model,
                    onMuscle = { selected = it.key },
                    onShowTable = {},
                )
                OhChipStrip {
                    OhChip(
                        text = stringResource(R.string.body_lab_type),
                        selected = planMode,
                        neutralSelected = true,
                        onClick = { planMode = !planMode },
                    )
                    OhChip(
                        text = stringResource(R.string.body_map_fat_layer),
                        selected = showFat,
                        neutralSelected = true,
                        onClick = { showFat = !showFat },
                    )
                    OhChip(
                        text = stringResource(
                            if (sex == BodySex.MALE) {
                                R.string.body_lab_male
                            } else {
                                R.string.body_lab_female
                            },
                        ),
                        selected = false,
                        onClick = {
                            sex = if (sex == BodySex.MALE) BodySex.FEMALE else BodySex.MALE
                        },
                    )
                }
                OhBodyMapLegend(model = model)
            }
            OhCard {
                Scrub(
                    label = stringResource(R.string.body_lab_body_fat),
                    value = bodyFat,
                    range = BODY_FAT_MIN..BODY_FAT_MAX,
                ) { bodyFat = it }
                Scrub(
                    label = stringResource(R.string.body_lab_waist),
                    value = waist,
                    range = WAIST_MIN..WAIST_MAX,
                ) { waist = it }
                Scrub(
                    label = stringResource(R.string.body_lab_hours),
                    value = hours,
                    range = HOURS_MIN..HOURS_MAX,
                ) { hours = it }
                Spacer(Modifier.height(OhTheme.space.s3))
                Text(
                    text = stringResource(
                        R.string.body_lab_step,
                        hours.toInt(),
                        MuscleRecovery.stepAtHours(hours.toDouble(), false).label(),
                    ),
                    style = OhTheme.type.body,
                    color = OhTheme.colors.label,
                )
                Text(
                    text = stringResource(
                        R.string.body_lab_band,
                        number((adipose?.maxThickness ?: 0.0) * 100),
                    ),
                    style = OhTheme.type.footnote,
                    color = OhTheme.colors.label2,
                )
                Text(
                    text = if (catalogued < 0) {
                        stringResource(R.string.body_lab_reading)
                    } else {
                        stringResource(
                            R.string.body_lab_counts,
                            catalogued,
                            bodyMapTableRows(model).size,
                        )
                    },
                    style = OhTheme.type.footnote,
                    color = OhTheme.colors.label2,
                )
            }
        }
    }

    @Composable
    private fun Scrub(
        label: String,
        value: Float,
        range: ClosedFloatingPointRange<Float>,
        onChange: (Float) -> Unit,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.body_lab_value, label, value.toInt()),
                style = OhTheme.type.subhead,
                color = OhTheme.colors.label,
            )
            OhSlider(
                value = value,
                onValueChange = onChange,
                valueRange = range,
                contentDescription = label,
            )
        }
    }

    private fun FormulaOutcome<RecoveryStep>.label(): String = when (val outcome = this) {
        is FormulaOutcome.Computed -> outcome.value.name.lowercase().replaceFirstChar {
            it.uppercase()
        }
        is FormulaOutcome.NotAvailable -> "no"
    }

    /** A scrub of hours-since-load on four muscles, so each step is visible at once. */
    private fun syntheticRecovery(hours: Double): Map<Muscle, MuscleRecoveryState> =
        listOf(Muscle.QUADS, Muscle.CHEST, Muscle.GLUTES, Muscle.DELTOIDS).associateWith {
            val step = (MuscleRecovery.stepAtHours(hours, false) as? FormulaOutcome.Computed)
                ?.value ?: RecoveryStep.UNTRAINED
            MuscleRecoveryState(it, step, hoursSinceLoad = hours, sets = 4.0)
        }

    private fun samplePlan(): List<PlannedExercise> = ExerciseCatalogue.bundled
        .filter { it.primary.isNotEmpty() }
        .take(5)
        .map { PlannedExercise(it, targetSets = 3) }

    private companion object {
        const val BODY_FAT_MIN = 5f
        const val BODY_FAT_MAX = 50f
        const val WAIST_MIN = 50f
        const val WAIST_MAX = 140f
        const val HOURS_MIN = 0f
        const val HOURS_MAX = 96f
    }
}
