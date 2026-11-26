@file:Suppress(
    "MagicNumber",
    "ReturnCount",
    "LongMethod",
    "LongParameterList",
    "CyclomaticComplexMethod",
)

package app.openhealth.core.clinical

import app.openhealth.core.observation.ObservationType

/** One LOINC term: the code, its display name and the system URI. */
data class LoincCode(val code: String, val display: String) {
    val system: String get() = LOINC_SYSTEM

    companion object {
        const val LOINC_SYSTEM = "http://loinc.org"
    }
}

/**
 * LOINC codes for the vitals the app records (`pr.md` §6.9, §6.17). Only types with a
 * code the project can state confidently are mapped; a type without one is left out of
 * the FHIR bundle and still appears in the PDF report, so a guess never becomes a code.
 *
 * LOINC is used as a set of identifiers. The LOINC terms are recorded in `NOTICE.md`.
 */
object VitalLoinc {
    private val byType: Map<ObservationType, LoincCode> = mapOf(
        ObservationType.HEART_RATE to LoincCode("8867-4", "Heart rate"),
        ObservationType.RESTING_HEART_RATE to LoincCode("40443-4", "Heart rate --resting"),
        ObservationType.BLOOD_PRESSURE_SYSTOLIC to LoincCode("8480-6", "Systolic blood pressure"),
        ObservationType.BLOOD_PRESSURE_DIASTOLIC to
            LoincCode("8462-4", "Diastolic blood pressure"),
        ObservationType.BLOOD_GLUCOSE to LoincCode("2339-0", "Glucose [Mass/volume] in Blood"),
        ObservationType.BODY_TEMPERATURE to LoincCode("8310-5", "Body temperature"),
        ObservationType.OXYGEN_SATURATION to
            LoincCode("59408-5", "Oxygen saturation in Arterial blood by Pulse oximetry"),
        ObservationType.RESPIRATORY_RATE to LoincCode("9279-1", "Respiratory rate"),
        ObservationType.WEIGHT to LoincCode("29463-7", "Body weight"),
        ObservationType.BODY_FAT_PERCENT to LoincCode("41982-0", "Percentage body fat"),
        ObservationType.SLEEP_DURATION to LoincCode("93832-4", "Sleep duration"),
        ObservationType.STEPS to
            LoincCode("55423-8", "Number of steps in unspecified time Pedometer"),
    )

    fun forType(type: ObservationType): LoincCode? = byType[type]

    fun all(): Map<ObservationType, LoincCode> = byType
}

/** The UCUM system URI a FHIR quantity names. */
const val UCUM_SYSTEM = "http://unitsofmeasure.org"

/** The profile URIs the bundle's resources declare, so a reader knows what it is. */
object FhirProfiles {
    const val PATIENT = "http://hl7.org/fhir/StructureDefinition/Patient"
    const val OBSERVATION = "http://hl7.org/fhir/StructureDefinition/Observation"
    const val MEDICATION_STATEMENT = "http://hl7.org/fhir/StructureDefinition/MedicationStatement"
    const val MEDICATION_ADMINISTRATION =
        "http://hl7.org/fhir/StructureDefinition/MedicationAdministration"
    const val CONDITION = "http://hl7.org/fhir/StructureDefinition/Condition"
    const val ALLERGY_INTOLERANCE =
        "http://hl7.org/fhir/StructureDefinition/AllergyIntolerance"
    const val IMMUNIZATION = "http://hl7.org/fhir/StructureDefinition/Immunization"
}
