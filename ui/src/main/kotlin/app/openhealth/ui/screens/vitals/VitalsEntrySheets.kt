// Plausibility bounds are deliberately wide; a typo is rejected, a real reading is not.
@file:Suppress("MatchingDeclarationName", "MagicNumber")

package app.openhealth.ui.screens.vitals

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import app.openhealth.core.body.CompositionMeasure
import app.openhealth.core.body.MeasurementSite
import app.openhealth.core.observation.MeasurementMethod
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.vitals.ArmSide
import app.openhealth.core.vitals.BodyPosition
import app.openhealth.core.vitals.HomeSlot
import app.openhealth.core.vitals.MealContext
import app.openhealth.ui.R
import app.openhealth.ui.body.measurementSiteLabel
import app.openhealth.ui.components.OhButton
import app.openhealth.ui.components.OhButtonVariant
import app.openhealth.ui.components.OhChip
import app.openhealth.ui.components.OhModalSheet
import app.openhealth.ui.components.OhTextField
import app.openhealth.ui.theme.OhTheme

/** What an Add-reading sheet sends to the presenter. Text stays text until validation. */
data class VitalDraft(
    val type: ObservationType,
    val systolic: String = "",
    val diastolic: String = "",
    val pulse: String = "",
    val value: String = "",
    val arm: ArmSide? = null,
    val position: BodyPosition? = null,
    val meal: MealContext? = null,
    val note: String = "",
    val homeSlot: HomeSlot? = null,
)

/**
 * The Add-reading sheet. Blood pressure is two fields with arm and position; the other
 * four vitals are one value with the context that changes their cited range (fasting or
 * after a meal for glucose). Nothing is saved until the value parses and sits in a
 * plausible range, so a typo cannot become a record.
 */
@Composable
fun AddReadingSheet(
    visible: Boolean,
    initialType: ObservationType?,
    imperial: Boolean,
    homeSlot: HomeSlot?,
    onDismiss: () -> Unit,
    onSave: (VitalDraft) -> Unit,
) {
    if (!visible) return
    var type by rememberSaveable(initialType, homeSlot) { mutableStateOf(initialType) }
    var systolic by rememberSaveable(initialType, homeSlot) { mutableStateOf("") }
    var diastolic by rememberSaveable(initialType, homeSlot) { mutableStateOf("") }
    var pulse by rememberSaveable(initialType, homeSlot) { mutableStateOf("") }
    var value by rememberSaveable(initialType, homeSlot) { mutableStateOf("") }
    var arm by rememberSaveable(initialType, homeSlot) { mutableStateOf<ArmSide?>(null) }
    var position by rememberSaveable(initialType, homeSlot) {
        mutableStateOf<BodyPosition?>(BodyPosition.SITTING)
    }
    var meal by rememberSaveable(initialType, homeSlot) { mutableStateOf<MealContext?>(null) }
    var note by rememberSaveable(initialType, homeSlot) { mutableStateOf("") }
    val chosen = type
    val canSave = chosen != null &&
        when (chosen) {
            ObservationType.BLOOD_PRESSURE_SYSTOLIC ->
                parse(systolic) in 50.0..300.0 && parse(diastolic) in 30.0..200.0

            ObservationType.RESTING_HEART_RATE -> parse(value) in 20.0..250.0
            ObservationType.BODY_TEMPERATURE -> {
                val canonical = if (imperial) (parse(value) - 32.0) * 5.0 / 9.0 else parse(value)
                canonical in 25.0..45.0
            }

            ObservationType.OXYGEN_SATURATION -> parse(value) in 50.0..100.0
            ObservationType.BLOOD_GLUCOSE -> {
                val canonical = if (imperial) parse(value) / 18.0182 else parse(value)
                canonical in 1.0..50.0
            }

            else -> false
        }
    OhModalSheet(
        visible = true,
        onDismiss = onDismiss,
        title = stringResource(R.string.vitals_add_reading_title),
        primaryLabel = if (chosen != null) stringResource(R.string.vitals_add_save) else null,
        primaryEnabled = canSave,
        onPrimary = {
            if (chosen != null) {
                onSave(
                    VitalDraft(
                        type = chosen,
                        systolic = systolic,
                        diastolic = diastolic,
                        pulse = pulse,
                        value = value,
                        arm = arm,
                        position = position,
                        meal = meal,
                        note = note.trim(),
                        homeSlot = homeSlot,
                    ),
                )
            }
        },
        cancelLabel = stringResource(R.string.vitals_add_cancel),
        onCancel = onDismiss,
    ) {
        if (chosen == null) {
            Text(
                text = stringResource(R.string.vitals_add_type),
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
            )
            Spacer(Modifier.height(OhTheme.space.s3))
            B7_VITALS.forEach { vital ->
                OhButton(
                    text = vitalTitle(vital),
                    onClick = {
                        type = vital
                        value = ""
                        systolic = ""
                        diastolic = ""
                    },
                    variant = OhButtonVariant.Secondary,
                )
                Spacer(Modifier.height(OhTheme.space.s2))
            }
            return@OhModalSheet
        }
        val unit = vitalDisplayUnit(chosen, imperial)
        if (chosen == ObservationType.BLOOD_PRESSURE_SYSTOLIC) {
            OhTextField(
                value = systolic,
                onValueChange = { systolic = it },
                placeholder = stringResource(R.string.vitals_add_systolic),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Spacer(Modifier.height(OhTheme.space.s2))
            OhTextField(
                value = diastolic,
                onValueChange = { diastolic = it },
                placeholder = stringResource(R.string.vitals_add_diastolic),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Spacer(Modifier.height(OhTheme.space.s2))
            OhTextField(
                value = pulse,
                onValueChange = { pulse = it },
                placeholder = stringResource(R.string.vitals_add_pulse),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Spacer(Modifier.height(OhTheme.space.s4))
            Text(
                text = stringResource(R.string.vitals_add_arm),
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(OhTheme.space.s2),
            ) {
                ArmSide.entries.forEach { side ->
                    OhChip(
                        text = stringResource(
                            if (side == ArmSide.LEFT) {
                                R.string.vitals_detail_context_left
                            } else {
                                R.string.vitals_detail_context_right
                            },
                        ),
                        selected = arm == side,
                        onClick = { arm = if (arm == side) null else side },
                    )
                }
            }
            Spacer(Modifier.height(OhTheme.space.s3))
            Text(
                text = stringResource(R.string.vitals_add_position),
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(OhTheme.space.s2),
            ) {
                BodyPosition.entries.forEach { slot ->
                    OhChip(
                        text = stringResource(
                            when (slot) {
                                BodyPosition.SITTING -> R.string.vitals_detail_context_sitting
                                BodyPosition.STANDING -> R.string.vitals_detail_context_standing
                                BodyPosition.LYING -> R.string.vitals_detail_context_lying
                            },
                        ),
                        selected = position == slot,
                        onClick = { position = slot },
                    )
                }
            }
        } else {
            OhTextField(
                value = value,
                onValueChange = { value = it },
                placeholder = stringResource(R.string.vitals_add_value),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Spacer(Modifier.height(OhTheme.space.s1))
            Text(
                text = unit,
                style = OhTheme.type.caption,
                color = OhTheme.colors.label2,
            )
            if (chosen == ObservationType.BLOOD_GLUCOSE) {
                Spacer(Modifier.height(OhTheme.space.s3))
                Text(
                    text = stringResource(R.string.vitals_add_context),
                    style = OhTheme.type.footnote,
                    color = OhTheme.colors.label2,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(OhTheme.space.s2),
                ) {
                    MealContext.entries.forEach { context ->
                        OhChip(
                            text = stringResource(
                                when (context) {
                                    MealContext.FASTING ->
                                        R.string.vitals_detail_context_fasting

                                    MealContext.PRE_MEAL ->
                                        R.string.vitals_detail_context_pre_meal

                                    MealContext.POST_MEAL ->
                                        R.string.vitals_detail_context_post_meal
                                },
                            ),
                            selected = meal == context,
                            onClick = { meal = if (meal == context) null else context },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(OhTheme.space.s3))
        OhTextField(
            value = note,
            onValueChange = { note = it },
            placeholder = stringResource(R.string.vitals_add_note),
        )
        Spacer(Modifier.height(OhTheme.space.s3))
        Text(
            text = stringResource(R.string.vitals_add_reading_required),
            style = OhTheme.type.caption,
            color = OhTheme.colors.label2,
        )
    }
}

/**
 * The Add-measurement sheet: pick a tape site, type the circumference in the person's own
 * length unit. The presenter converts to centimetres before storage, so the canonical
 * value and the unit it was entered in agree.
 */
@Composable
fun AddMeasurementSheet(
    visible: Boolean,
    initialSite: MeasurementSite?,
    imperial: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, MeasurementMethod) -> Unit,
) {
    if (!visible) return
    var choice by rememberSaveable(initialSite) {
        mutableStateOf(initialSite?.type ?: MeasurementSite.WAIST.type)
    }
    var value by rememberSaveable(initialSite) { mutableStateOf("") }
    var customName by rememberSaveable(initialSite) { mutableStateOf("") }
    var method by rememberSaveable(initialSite) {
        mutableStateOf(MeasurementMethod.MANUAL_ENTRY)
    }
    val unit = stringResource(if (imperial) R.string.unit_in else R.string.unit_cm)
    val parsed = value.replace(',', '.').toDoubleOrNull()
    val canSave = parsed != null &&
        parsed > 0.0 &&
        (choice != CUSTOM || customName.isNotBlank())
    OhModalSheet(
        visible = true,
        onDismiss = onDismiss,
        title = stringResource(R.string.vitals_add_measurement_title),
        primaryLabel = stringResource(R.string.vitals_add_save),
        primaryEnabled = canSave,
        onPrimary = {
            val type = if (choice == CUSTOM) {
                "custom:" + customName.trim().lowercase().replace(' ', '_')
            } else {
                choice
            }
            onSave(type, value, method)
        },
        cancelLabel = stringResource(R.string.vitals_add_cancel),
        onCancel = onDismiss,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(OhTheme.space.s2),
        ) {
            MeasurementSite.entries.forEach { entry ->
                OhChip(
                    text = measurementSiteLabel(entry),
                    selected = choice == entry.type,
                    onClick = { choice = entry.type },
                )
            }
            OhChip(
                text = stringResource(R.string.vitals_add_custom),
                selected = choice == CUSTOM,
                onClick = { choice = CUSTOM },
            )
            CompositionMeasure.entries.forEach { entry ->
                OhChip(
                    text = stringResource(
                        when (entry) {
                            CompositionMeasure.BODY_FAT -> R.string.vitals_composition_body_fat
                            CompositionMeasure.BODY_WATER -> R.string.vitals_composition_water
                        },
                    ),
                    selected = choice == entry.type,
                    onClick = { choice = entry.type },
                )
            }
        }
        if (choice == CUSTOM) {
            Spacer(Modifier.height(OhTheme.space.s3))
            OhTextField(
                value = customName,
                onValueChange = { customName = it },
                placeholder = stringResource(R.string.vitals_add_custom_name),
            )
        }
        Spacer(Modifier.height(OhTheme.space.s4))
        OhTextField(
            value = value,
            onValueChange = { value = it },
            placeholder = stringResource(R.string.vitals_add_measurement_value),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        if (CompositionMeasure.fromType(choice) != null) {
            Spacer(Modifier.height(OhTheme.space.s3))
            Text(
                text = stringResource(R.string.vitals_add_method),
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(OhTheme.space.s2),
            ) {
                listOf(
                    MeasurementMethod.MANUAL_ENTRY,
                    MeasurementMethod.SCALE,
                    MeasurementMethod.BIA,
                    MeasurementMethod.DEXA,
                ).forEach { candidate ->
                    OhChip(
                        text = methodLabel(candidate) ?: candidate.name,
                        selected = method == candidate,
                        onClick = { method = candidate },
                    )
                }
            }
        }
        Spacer(Modifier.height(OhTheme.space.s1))
        Text(
            text = if (CompositionMeasure.fromType(choice) != null) {
                stringResource(R.string.vitals_composition_body_fat) + " · %"
            } else {
                stringResource(R.string.vitals_add_measurement_hint, unit)
            },
            style = OhTheme.type.caption,
            color = OhTheme.colors.label2,
        )
    }
}

private const val CUSTOM = "custom"

private fun parse(text: String): Double = text.replace(',', '.').toDoubleOrNull() ?: Double.NaN
