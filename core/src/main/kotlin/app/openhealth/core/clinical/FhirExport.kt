@file:Suppress(
    "TooManyFunctions",
    "MagicNumber",
    "ReturnCount",
    "LongMethod",
    "LongParameterList",
    "CyclomaticComplexMethod",
)

package app.openhealth.core.clinical

import app.openhealth.core.citation.Citations
import app.openhealth.core.observation.Observation
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.vitals.VitalRanges
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Everything the FHIR R4 export can carry (`pr.md` §6.17, V1). It is a flat, explicit
 * input rather than the database: the same builder is used by the app and by the test
 * that validates the bundle against the R4 schema.
 */
data class FhirExportData(
    val patientId: String,
    val generatedAt: Instant,
    val vitals: List<Observation>,
    val labs: List<LabResult> = emptyList(),
    val medications: List<Medication> = emptyList(),
    val doses: List<MedicationDose> = emptyList(),
    val conditions: List<Condition> = emptyList(),
    val allergies: List<Allergy> = emptyList(),
    val vaccinations: List<Vaccination> = emptyList(),
    /** Weight-neutral mode removes weight and body fat from every export (`pr.md` §6.25). */
    val includeWeight: Boolean = true,
)

/**
 * FHIR R4 export (`pr.md` §6.9, §6.17). The bundle is a `collection` of the person's own
 * records: observations (vitals and labs), medication statements and administrations,
 * conditions, allergies and immunisations. Every resource keeps the source it came from
 * in `meta.tag`, and every code system is named rather than implied.
 *
 * This is a record export, not a clinical decision: nothing here interprets a value.
 */
object FhirR4 {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    private val excludedInWeightNeutral = setOf(
        ObservationType.WEIGHT,
        ObservationType.BODY_FAT_PERCENT,
        ObservationType.LEAN_MASS,
    )

    fun export(data: FhirExportData): String =
        json.encodeToString(JsonObject.serializer(), bundle(data))

    fun bundle(data: FhirExportData): JsonObject = buildJsonObject {
        put("resourceType", "Bundle")
        put("id", fhirId("openhealth-export-${data.generatedAt.toEpochMilliseconds()}"))
        putJsonObject("meta") {
            putJsonArray("tag") {
                addJsonObject {
                    put("system", "https://openhealth.app/source")
                    put("code", "openhealth-export")
                    put("display", "openHealth record export")
                }
            }
        }
        put("type", "collection")
        put("timestamp", data.generatedAt.toString())
        putJsonArray("entry") {
            add(entry(patient(data)))
            data.vitals.forEach { observation ->
                val loinc = VitalLoinc.forType(observation.type) ?: return@forEach
                if (!data.includeWeight &&
                    observation.type in excludedInWeightNeutral
                ) {
                    return@forEach
                }
                add(entry(observation(data, observation, loinc)))
            }
            data.labs.forEach { lab -> add(entry(labObservation(data, lab))) }
            data.medications.forEach { medication ->
                add(entry(medicationStatement(data, medication)))
                data.doses.filter { it.medicationId == medication.id }.forEach { dose ->
                    add(entry(medicationAdministration(data, medication, dose)))
                }
            }
            data.conditions.forEach { add(entry(condition(data, it))) }
            data.allergies.forEach { add(entry(allergy(data, it))) }
            data.vaccinations.forEach { add(entry(immunization(data, it))) }
        }
    }

    // ------------------------------------------------------------------ resources

    private fun patient(data: FhirExportData): JsonObject = buildJsonObject {
        put("resourceType", "Patient")
        put("id", fhirId(data.patientId))
        putJsonObject("meta") { profile(FhirProfiles.PATIENT) }
        put("active", true)
        putJsonArray("identifier") {
            addJsonObject {
                put("system", "urn:openhealth:patient")
                put("value", data.patientId)
            }
        }
    }

    private fun observation(
        data: FhirExportData,
        observation: Observation,
        loinc: LoincCode,
    ): JsonObject = buildJsonObject {
        put("resourceType", "Observation")
        put("id", fhirId(observation.id.value))
        putJsonObject("meta") {
            profile(FhirProfiles.OBSERVATION)
            sourceTag(observation.provenance.sourceId.value)
        }
        put("status", "final")
        putJsonArray("category") {
            addJsonObject {
                putJsonArray("coding") {
                    addJsonObject {
                        put("system", CATEGORY_SYSTEM)
                        put(
                            "code",
                            if (observation.type == ObservationType.STEPS) {
                                "activity"
                            } else {
                                "vital-signs"
                            },
                        )
                    }
                }
            }
        }
        putJsonObject("code") {
            putJsonArray("coding") { add(coding(loinc)) }
            put("text", loinc.display)
        }
        subject(data)
        put("effectiveDateTime", observation.start.toString())
        putJsonObject("valueQuantity") {
            quantity(observation.value.value, observation.value.unit.ucum)
        }
        VitalRanges.forReading(observation.type, observation.context?.name)?.let { range ->
            putJsonArray("referenceRange") {
                addJsonObject {
                    range.low?.let { putJsonObject("low") { quantity(it, range.unit.ucum) } }
                    range.high?.let { putJsonObject("high") { quantity(it, range.unit.ucum) } }
                    put("text", referenceText(range.citation.name, range.statement))
                }
            }
        }
        putJsonArray("identifier") {
            addJsonObject {
                put("system", "urn:openhealth:idempotency")
                put("value", observation.provenance.idempotencyKey)
            }
        }
    }

    private fun labObservation(data: FhirExportData, lab: LabResult): JsonObject = buildJsonObject {
        put("resourceType", "Observation")
        put("id", fhirId(lab.id))
        putJsonObject("meta") {
            profile(FhirProfiles.OBSERVATION)
            sourceTag(lab.provenance.sourceId.value)
        }
        put("status", "final")
        putJsonArray("category") {
            addJsonObject {
                putJsonArray("coding") {
                    addJsonObject {
                        put("system", CATEGORY_SYSTEM)
                        put("code", "laboratory")
                    }
                }
            }
        }
        putJsonObject("code") {
            putJsonArray("coding") {
                addJsonObject {
                    put("system", LoincCode.LOINC_SYSTEM)
                    put("code", lab.analyte.loinc)
                    put("display", lab.analyte.display)
                }
            }
            put("text", lab.analyte.display)
        }
        subject(data)
        put("effectiveDateTime", lab.collectedAt.toString())
        putJsonObject("valueQuantity") { quantity(lab.value, lab.unit.ucum) }
        val hasNote = lab.referenceNote != null
        val low = lab.referenceLow ?: lab.analyte.defaultLow.takeUnless { hasNote }
        val high = lab.referenceHigh ?: lab.analyte.defaultHigh.takeUnless { hasNote }
        if (low != null || high != null) {
            putJsonArray("referenceRange") {
                addJsonObject {
                    low?.let { putJsonObject("low") { quantity(it, lab.unit.ucum) } }
                    high?.let { putJsonObject("high") { quantity(it, lab.unit.ucum) } }
                    val citation = lab.referenceCitation ?: lab.analyte.defaultCitation
                    val text = lab.referenceNote
                        ?: citation?.let { Citations.of(it).title }
                        ?: lab.analyte.rangeText
                    text?.let { put("text", it) }
                }
            }
        }
        lab.labName?.let { name ->
            putJsonArray("performer") { addJsonObject { put("display", name) } }
        }
        putJsonArray("identifier") {
            addJsonObject {
                put("system", "urn:openhealth:idempotency")
                put("value", lab.provenance.idempotencyKey)
            }
        }
    }

    private fun medicationStatement(data: FhirExportData, medication: Medication): JsonObject =
        buildJsonObject {
            put("resourceType", "MedicationStatement")
            put("id", fhirId(medication.id))
            putJsonObject("meta") {
                profile(FhirProfiles.MEDICATION_STATEMENT)
                sourceTag(medication.provenance.sourceId.value)
            }
            put("status", if (medication.active) "active" else "completed")
            putJsonObject("medicationCodeableConcept") {
                put("text", medication.name)
            }
            subject(data)
            put("dateAsserted", data.generatedAt.toString())
            putJsonArray("dosage") {
                addJsonObject {
                    put("text", dosageText(medication))
                    putJsonObject("timing") {
                        putJsonObject("repeat") {
                            medication.schedule.daysOfWeek.takeIf { it.isNotEmpty() }?.let { days ->
                                putJsonArray("dayOfWeek") {
                                    days.forEach { day -> add(dayName(day)) }
                                }
                            }
                            medication.schedule.times.takeIf { it.isNotEmpty() }?.let { times ->
                                putJsonArray("timeOfDay") { times.forEach { add("$it:00") } }
                            }
                        }
                    }
                    putJsonArray("doseAndRate") {
                        addJsonObject {
                            putJsonObject("doseQuantity") {
                                quantity(medication.doseAmount, medication.doseUnit.ucum)
                            }
                        }
                    }
                }
            }
            if (medication.startedOn != null || medication.endedOn != null) {
                putJsonObject("effectivePeriod") {
                    medication.startedOn?.let { put("start", it.toString()) }
                    medication.endedOn?.let { put("end", it.toString()) }
                }
            }
            adherence(medication, data.doses)?.let { text ->
                putJsonArray("note") { addJsonObject { put("text", text) } }
            }
        }

    private fun medicationAdministration(
        data: FhirExportData,
        medication: Medication,
        dose: MedicationDose,
    ): JsonObject = buildJsonObject {
        put("resourceType", "MedicationAdministration")
        put("id", fhirId(dose.id))
        putJsonObject("meta") {
            profile(FhirProfiles.MEDICATION_ADMINISTRATION)
            sourceTag(dose.provenance.sourceId.value)
        }
        put("status", if (dose.skipped) "not-done" else "completed")
        putJsonObject("medicationCodeableConcept") { put("text", medication.name) }
        subject(data)
        put("effectiveDateTime", (dose.takenAt ?: dose.scheduledFor).toString())
        if (dose.skipped) {
            putJsonObject("statusReason") { put("text", "Skipped by the person") }
        } else {
            putJsonArray("dosage") {
                addJsonObject {
                    putJsonObject("dose") {
                        quantity(medication.doseAmount, medication.doseUnit.ucum)
                    }
                }
            }
        }
    }

    private fun condition(data: FhirExportData, condition: Condition): JsonObject =
        buildJsonObject {
            put("resourceType", "Condition")
            put("id", fhirId(condition.id))
            putJsonObject("meta") {
                profile(FhirProfiles.CONDITION)
                sourceTag(condition.provenance.sourceId.value)
            }
            putJsonObject("clinicalStatus") {
                putJsonArray("coding") {
                    addJsonObject {
                        put("system", "http://terminology.hl7.org/CodeSystem/condition-clinical")
                        put("code", if (condition.resolvedOn == null) "active" else "resolved")
                    }
                }
            }
            putJsonObject("code") {
                condition.code?.let { code ->
                    putJsonArray("coding") {
                        addJsonObject {
                            put("system", condition.codeSystem ?: "urn:openhealth:condition")
                            put("code", code)
                        }
                    }
                }
                put("text", condition.name)
            }
            subject(data)
            condition.onsetOn?.let { put("onsetDateTime", it.toString()) }
            condition.resolvedOn?.let { put("abatementDateTime", it.toString()) }
            put("recordedDate", condition.provenance.recordedAt.toString())
        }

    private fun allergy(data: FhirExportData, allergy: Allergy): JsonObject = buildJsonObject {
        put("resourceType", "AllergyIntolerance")
        put("id", fhirId(allergy.id))
        putJsonObject("meta") {
            profile(FhirProfiles.ALLERGY_INTOLERANCE)
            sourceTag(allergy.provenance.sourceId.value)
        }
        putJsonObject("clinicalStatus") {
            putJsonArray("coding") {
                addJsonObject {
                    put(
                        "system",
                        "http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical",
                    )
                    put("code", if (allergy.active) "active" else "inactive")
                }
            }
        }
        put("criticality", if (allergy.severity == AllergySeverity.SEVERE) "high" else "low")
        putJsonObject("code") { put("text", allergy.substance) }
        patientReference(data)
        allergy.notedOn?.let { put("recordedDate", it.toString()) }
        allergy.reaction?.let { reaction ->
            putJsonArray("reaction") {
                addJsonObject {
                    putJsonArray("manifestation") { addJsonObject { put("text", reaction) } }
                    put("severity", allergy.severity.name.lowercase())
                }
            }
        }
    }

    private fun immunization(data: FhirExportData, vaccination: Vaccination): JsonObject =
        buildJsonObject {
            put("resourceType", "Immunization")
            put("id", fhirId(vaccination.id))
            putJsonObject("meta") {
                profile(FhirProfiles.IMMUNIZATION)
                sourceTag(vaccination.provenance.sourceId.value)
            }
            put("status", "completed")
            putJsonObject("vaccineCode") {
                put("text", vaccination.name)
                vaccination.doseLabel?.let { put("text", "${vaccination.name} ($it)") }
            }
            patientReference(data)
            put("occurrenceDateTime", vaccination.administeredOn.toString())
            put("primarySource", true)
            put("recorded", vaccination.provenance.recordedAt.toString())
        }

    // -------------------------------------------------------------------- helpers

    private fun kotlinx.serialization.json.JsonObjectBuilder.subject(data: FhirExportData) {
        putJsonObject("subject") { put("reference", patientUrl(data)) }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.patientReference(
        data: FhirExportData,
    ) {
        putJsonObject("patient") { put("reference", patientUrl(data)) }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.profile(uri: String) {
        putJsonArray("profile") { add(uri) }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.sourceTag(sourceId: String) {
        putJsonArray("tag") {
            addJsonObject {
                put("system", "urn:openhealth:source")
                put("code", sourceId)
            }
        }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.quantity(value: Double, unit: String) {
        put("value", value)
        put("unit", unit)
        put("system", UCUM_SYSTEM)
        put("code", unit)
    }

    private fun coding(loinc: LoincCode): JsonObject = buildJsonObject {
        put("system", loinc.system)
        put("code", loinc.code)
        put("display", loinc.display)
    }

    private fun entry(resource: JsonObject): JsonObject {
        val type = resource["resourceType"]?.toString()?.trim('"') ?: "Resource"
        val id = resource["id"]?.toString()?.trim('"') ?: "resource"
        return buildJsonObject {
            put("fullUrl", resourceUrl(type, id))
            put("resource", resource)
        }
    }

    /** A stable UUID for an app id, so a bundle reference resolves to its entry. */
    private fun resourceUrl(type: String, id: String): String =
        "urn:uuid:" + java.util.UUID.nameUUIDFromBytes("$type/$id".toByteArray()).toString()

    private fun patientUrl(data: FhirExportData): String =
        resourceUrl("Patient", fhirId(data.patientId))

    private fun dosageText(medication: Medication): String {
        val amount = "${trimNumber(medication.doseAmount)} ${medication.doseUnit.ucum}"
        val whenText = when {
            medication.schedule.asNeeded -> "as needed"
            medication.schedule.times.isEmpty() -> "no scheduled time"
            medication.schedule.daysOfWeek.isEmpty() ->
                medication.schedule.times.joinToString(", ")
            else -> medication.schedule.daysOfWeek.joinToString(", ") { dayName(it) } +
                " at " + medication.schedule.times.joinToString(", ")
        }
        return "$amount, $whenText"
    }

    private fun adherence(medication: Medication, doses: List<MedicationDose>): String? {
        val own = doses.filter { it.medicationId == medication.id }
        if (own.isEmpty()) return null
        val taken = own.count { !it.skipped }
        val skipped = own.count { it.skipped }
        return "Doses logged: $taken taken, $skipped skipped"
    }

    private fun dayName(iso: Int): String = when (iso) {
        1 -> "mon"
        2 -> "tue"
        3 -> "wed"
        4 -> "thu"
        5 -> "fri"
        6 -> "sat"
        7 -> "sun"
        else -> "mon"
    }

    private fun referenceText(citation: String, statement: String): String =
        "$statement (${citation.replace('_', ' ').lowercase()})"

    private fun trimNumber(value: Double): String = if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        value.toString()
    }

    /** A FHIR `id` is `[A-Za-z0-9\\-.]{1,64}`; an app id is made to fit, never truncated away. */
    fun fhirId(raw: String): String {
        val cleaned = raw.replace(Regex("[^A-Za-z0-9\\-.]"), "-")
        return cleaned.take(64).ifBlank { "resource" }
    }

    private const val CATEGORY_SYSTEM = "http://terminology.hl7.org/CodeSystem/observation-category"
}
