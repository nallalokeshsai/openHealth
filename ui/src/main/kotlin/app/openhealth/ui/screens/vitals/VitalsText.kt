package app.openhealth.ui.screens.vitals

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.openhealth.core.citation.CitationKey
import app.openhealth.core.citation.Citations
import app.openhealth.core.observation.MeasurementMethod
import app.openhealth.core.observation.ObservationContext
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.vitals.ArmSide
import app.openhealth.core.vitals.BodyPosition
import app.openhealth.core.vitals.HomeSlot
import app.openhealth.core.vitals.MealContext
import app.openhealth.core.vitals.RangePosition
import app.openhealth.ui.R

/** The five vitals of B7 in their fixed order (`pr.md` §6.9, `BUILD-ROADMAP.md` §B7). */
val B7_VITALS: List<ObservationType> = listOf(
    ObservationType.BLOOD_PRESSURE_SYSTOLIC,
    ObservationType.RESTING_HEART_RATE,
    ObservationType.BODY_TEMPERATURE,
    ObservationType.OXYGEN_SATURATION,
    ObservationType.BLOOD_GLUCOSE,
)

@Composable
fun vitalTitle(type: ObservationType): String = stringResource(
    when (type) {
        ObservationType.BLOOD_PRESSURE_SYSTOLIC -> R.string.vitals_type_bp
        ObservationType.RESTING_HEART_RATE -> R.string.vitals_type_heart_rate
        ObservationType.BODY_TEMPERATURE -> R.string.vitals_type_temperature
        ObservationType.OXYGEN_SATURATION -> R.string.vitals_type_oxygen
        ObservationType.BLOOD_GLUCOSE -> R.string.vitals_type_glucose
        else -> R.string.vitals_type_heart_rate
    },
)

@Composable
fun citationLabel(key: CitationKey): String = stringResource(
    when (key) {
        CitationKey.ESH_HYPERTENSION_2023 -> R.string.vitals_citation_esh
        CitationKey.AHA_RESTING_HEART_RATE -> R.string.vitals_citation_aha
        CitationKey.WHO_PULSE_OXIMETRY_2011 -> R.string.vitals_citation_who
        CitationKey.GENEVA_BODY_TEMPERATURE_2019 -> R.string.vitals_citation_geneva
        CitationKey.ADA_GLYCEMIC_TARGETS_2026 -> R.string.vitals_citation_ada
        CitationKey.US_NAVY_BODY_FAT_1984 -> R.string.vitals_citation_us_navy
        CitationKey.WHO_BMI_2000 -> R.string.vitals_citation_who_bmi
        CitationKey.FFMI_SCHUTZ_2002 -> R.string.vitals_citation_ffmi
        else -> R.string.vitals_citation_app
    },
)

/** The citation's full first line, shown in a formula or provenance sheet. */
fun citationTitle(key: CitationKey): String = Citations.of(key).title

fun citationAuthors(key: CitationKey): String {
    val citation = Citations.of(key)
    return "${citation.authors} · ${citation.publication} (${citation.year})"
}

@Composable
fun methodLabel(method: MeasurementMethod?): String? = when (method) {
    MeasurementMethod.SCALE -> stringResource(R.string.vitals_method_scale)
    MeasurementMethod.BIA -> stringResource(R.string.vitals_method_bia)
    MeasurementMethod.DEXA -> stringResource(R.string.vitals_method_dexa)
    MeasurementMethod.MANUAL_ENTRY -> stringResource(R.string.vitals_method_manual)
    MeasurementMethod.CUFF -> stringResource(R.string.vitals_method_cuff)
    MeasurementMethod.DEVICE_SENSOR -> stringResource(R.string.vitals_method_device)
    MeasurementMethod.FILE_IMPORT -> stringResource(R.string.vitals_method_file)
    MeasurementMethod.OTHER -> stringResource(R.string.vitals_method_other)
    MeasurementMethod.CGM_SENSOR -> stringResource(R.string.vitals_method_device)
    MeasurementMethod.LAB_ASSAY -> stringResource(R.string.vitals_method_other)
    MeasurementMethod.WATCH_OPTICAL -> stringResource(R.string.vitals_method_device)
    null -> null
}

@Composable
fun methodLabel(method: String?): String? = when (method?.uppercase()) {
    "SCALE" -> stringResource(R.string.vitals_method_scale)
    "BIA" -> stringResource(R.string.vitals_method_bia)
    "DEXA" -> stringResource(R.string.vitals_method_dexa)
    "NAVY" -> stringResource(R.string.vitals_method_navy)
    "MANUAL_ENTRY" -> stringResource(R.string.vitals_method_manual)
    "CUFF" -> stringResource(R.string.vitals_method_cuff)
    "DEVICE_SENSOR" -> stringResource(R.string.vitals_method_device)
    "FILE_IMPORT" -> stringResource(R.string.vitals_method_file)
    "OTHER" -> stringResource(R.string.vitals_method_other)
    null, "" -> null
    else -> method.lowercase().replace('_', ' ')
}

@Composable
fun contextLabel(context: ObservationContext?): String? = when (context) {
    ObservationContext.LEFT_ARM -> stringResource(R.string.vitals_detail_context_left)
    ObservationContext.RIGHT_ARM -> stringResource(R.string.vitals_detail_context_right)
    ObservationContext.SITTING -> stringResource(R.string.vitals_detail_context_sitting)
    ObservationContext.STANDING -> stringResource(R.string.vitals_detail_context_standing)
    ObservationContext.LYING -> stringResource(R.string.vitals_detail_context_lying)
    ObservationContext.FASTING -> stringResource(R.string.vitals_detail_context_fasting)
    ObservationContext.PRE_MEAL -> stringResource(R.string.vitals_detail_context_pre_meal)
    ObservationContext.POST_MEAL -> stringResource(R.string.vitals_detail_context_post_meal)
    null -> null
    else -> context.name.lowercase().replace('_', ' ')
}

@Composable
fun contextSummary(
    arm: ArmSide? = null,
    position: BodyPosition? = null,
    meal: MealContext? = null,
    slot: HomeSlot? = null,
): String? {
    val parts = buildList {
        arm?.let {
            add(
                stringResource(
                    if (it == ArmSide.LEFT) {
                        R.string.vitals_detail_context_left
                    } else {
                        R.string.vitals_detail_context_right
                    },
                ),
            )
        }
        position?.let {
            add(
                stringResource(
                    when (it) {
                        BodyPosition.SITTING -> R.string.vitals_detail_context_sitting
                        BodyPosition.STANDING -> R.string.vitals_detail_context_standing
                        BodyPosition.LYING -> R.string.vitals_detail_context_lying
                    },
                ),
            )
        }
        meal?.let {
            add(
                stringResource(
                    when (it) {
                        MealContext.FASTING -> R.string.vitals_detail_context_fasting
                        MealContext.PRE_MEAL -> R.string.vitals_detail_context_pre_meal
                        MealContext.POST_MEAL -> R.string.vitals_detail_context_post_meal
                    },
                ),
            )
        }
        slot?.let {
            add(
                stringResource(
                    if (it == HomeSlot.MORNING) {
                        R.string.vitals_detail_home_protocol_morning
                    } else {
                        R.string.vitals_detail_home_protocol_evening
                    },
                ),
            )
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

@Composable
fun positionLabel(position: RangePosition?): String? = when (position) {
    RangePosition.BELOW, RangePosition.ABOVE -> stringResource(R.string.vitals_reading_outside)
    RangePosition.WITHIN -> stringResource(R.string.vitals_reading_inside)
    null -> null
}

/** The display unit of a vital, respecting the person's measurement system. */
fun vitalDisplayUnit(type: ObservationType, imperial: Boolean): String = when (type) {
    ObservationType.BODY_TEMPERATURE -> if (imperial) "°F" else "°C"
    ObservationType.BLOOD_GLUCOSE -> if (imperial) "mg/dL" else "mmol/L"
    ObservationType.OXYGEN_SATURATION -> "%"
    ObservationType.RESTING_HEART_RATE -> "bpm"
    else -> "mmHg"
}

/** Convert a canonical value into the unit the screen prints. */
fun vitalDisplayValue(type: ObservationType, canonical: Double, imperial: Boolean): Double =
    when (type) {
        ObservationType.BODY_TEMPERATURE -> if (imperial) {
            canonical * 9.0 / 5.0 + 32.0
        } else {
            canonical
        }

        ObservationType.BLOOD_GLUCOSE -> if (imperial) canonical * 18.0182 else canonical
        else -> canonical
    }
