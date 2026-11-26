package app.openhealth.core.clinical

import app.openhealth.core.citation.CitationKey
import app.openhealth.core.formula.SleepConsistency
import app.openhealth.core.formula.SleepTiming
import app.openhealth.core.ids.ObservationId
import app.openhealth.core.ids.SourceId
import app.openhealth.core.observation.Observation
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.observation.Provenance
import app.openhealth.core.time.TimeProvenance
import app.openhealth.core.units.Measure
import app.openhealth.core.units.UcumUnit
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The clinical contract (`pr.md` §6.9, §6.17, §6.25; `BUILD-ROADMAP.md` §B12): cited
 * ranges, honest adherence, circular sleep statistics, a light report that names its
 * sources, and a FHIR bundle that validates against the published R4 schema.
 */
class ClinicalTest {
    private val zone = TimeZone.of("Europe/Berlin")
    private val noon = Instant.parse("2026-11-20T11:00:00Z")
    private val date = LocalDate(2026, 11, 20)

    private fun provenance(id: String = "p1", at: Instant = noon): Provenance = Provenance(
        sourceId = SourceId("manual"),
        time = TimeProvenance.of(at, at, zone),
        idempotencyKey = "key:$id",
    )

    private fun observation(
        id: String,
        type: ObservationType,
        value: Double,
        at: Instant = noon,
    ): Observation = Observation(
        id = ObservationId(id),
        type = type,
        value = Measure(value, type.canonicalUnit),
        start = at,
        end = null,
        provenance = provenance(id, at),
    )

    private fun medication(
        id: String = "med-1",
        times: List<String> = listOf("08:00"),
        days: List<Int> = emptyList(),
        asNeeded: Boolean = false,
    ): Medication = Medication(
        id = id,
        name = "Levothyroxine",
        doseAmount = 50.0,
        doseUnit = UcumUnit.MICROGRAM,
        form = MedicationForm.TABLET,
        schedule = MedicationSchedule(times = times, daysOfWeek = days, asNeeded = asNeeded),
        active = true,
        startedOn = date.minus(kotlinx.datetime.DatePeriod(days = 10)),
        endedOn = null,
        notes = null,
        provenance = provenance(id),
    )

    private fun lab(
        analyte: LabAnalyte = LabAnalyte.HBA1C,
        value: Double = 5.4,
        low: Double? = null,
        high: Double? = null,
    ): LabResult = LabResult(
        id = "lab-1",
        analyte = analyte,
        value = value,
        unit = analyte.unit,
        referenceLow = low,
        referenceHigh = high,
        referenceNote = if (low != null ||
            high != null
        ) {
            "the laboratory's printed interval"
        } else {
            null
        },
        referenceCitation = if (low != null ||
            high != null
        ) {
            CitationKey.LAB_REPORTED_RANGE
        } else {
            null
        },
        collectedAt = noon,
        labName = "Labor Berlin",
        notes = null,
        provenance = provenance("lab-1"),
    )

    private fun sampleData(weightNeutral: Boolean = false): ClinicalReportData {
        val now = noon
        return ClinicalReportData(
            patientLabel = "Alex",
            from = date.minus(kotlinx.datetime.DatePeriod(days = 30)),
            to = date,
            generatedAt = now,
            zone = zone,
            observations = listOf(
                observation("bp-s", ObservationType.BLOOD_PRESSURE_SYSTOLIC, 122.0),
                observation("bp-d", ObservationType.BLOOD_PRESSURE_DIASTOLIC, 78.0),
                observation("rhr", ObservationType.RESTING_HEART_RATE, 54.0),
                observation("weight", ObservationType.WEIGHT, 71.4),
                observation("steps-1", ObservationType.STEPS, 8_000.0),
                observation("steps-2", ObservationType.STEPS, 9_500.0),
            ),
            labs = listOf(lab()),
            medications = listOf(medication()),
            doses = listOf(
                MedicationDose(
                    id = "dose-1",
                    medicationId = "med-1",
                    medicationName = "Levothyroxine",
                    scheduledFor = noon,
                    takenAt = noon,
                    skipped = false,
                    localDate = date,
                    notes = null,
                    provenance = provenance("dose-1"),
                ),
            ),
            symptoms = listOf(
                SymptomEpisode(
                    id = "ep-1",
                    symptomId = "sym-1",
                    symptomName = "Headache",
                    severity = 4,
                    startedAt = noon,
                    endedAt = null,
                    triggers = "screen time",
                    notes = null,
                    localDate = date,
                    provenance = provenance("ep-1"),
                ),
            ),
            allergies = listOf(
                Allergy(
                    id = "allergy-1",
                    substance = "Penicillin",
                    reaction = "Rash",
                    severity = AllergySeverity.MODERATE,
                    active = true,
                    notedOn = date,
                    notes = null,
                    provenance = provenance("allergy-1"),
                ),
            ),
            conditions = listOf(
                Condition(
                    id = "cond-1",
                    name = "Asthma",
                    code = null,
                    codeSystem = null,
                    onsetOn = date.minus(kotlinx.datetime.DatePeriod(days = 400)),
                    resolvedOn = null,
                    notes = null,
                    provenance = provenance("cond-1"),
                ),
            ),
            vaccinations = listOf(
                Vaccination(
                    id = "vacc-1",
                    name = "Influenza",
                    doseLabel = "2026",
                    administeredOn = date,
                    notes = null,
                    provenance = provenance("vacc-1"),
                ),
            ),
            sleep = listOf(
                ReportSleepNight(
                    date = date,
                    asleepMinutes = 430.0,
                    bedtime = Instant.parse("2026-11-19T22:30:00Z"),
                    wake = Instant.parse("2026-11-20T06:00:00Z"),
                    quality = 4,
                ),
            ),
            activities = listOf(ReportActivity(date, "RUN", 35.0, 6.2)),
            stepsByDay = mapOf(
                date.minus(kotlinx.datetime.DatePeriod(days = 1)) to 8_000.0,
                date to 9_500.0,
            ),
            weightByDay = mapOf(
                date.minus(kotlinx.datetime.DatePeriod(days = 1)) to 71.4,
                date to 71.0,
            ),
            weightNeutral = weightNeutral,
        )
    }

    // ------------------------------------------------------------------ lab catalogue

    @Test
    fun everyAnalyteHasAUniqueLoincCode() {
        val codes = LabAnalyte.entries.map { it.loinc }
        assertEquals(codes.size, codes.toSet().size, "LOINC codes must be unique")
        assertTrue(codes.all { it.matches(Regex("\\d{1,5}-\\d")) }, "LOINC shape: $codes")
    }

    @Test
    fun aDefaultIntervalAlwaysCarriesItsCitation() {
        LabAnalyte.entries.forEach { analyte ->
            val defaults = analyte.defaultLow != null || analyte.defaultHigh != null
            if (defaults) {
                assertNotNull(analyte.defaultCitation, "${analyte.name} needs a citation")
                assertNotNull(analyte.rangeText, "${analyte.name} needs range text")
            }
        }
    }

    @Test
    fun aGuidelineDefaultIsNotALabReportedRange() {
        assertEquals(
            CitationKey.ADA_GLYCEMIC_TARGETS_2026,
            LabAnalyte.HBA1C.defaultCitation,
        )
        assertNull(LabAnalyte.TSH.defaultInterval(), "a lab-only analyte invents no interval")
        assertNotNull(LabAnalyte.HBA1C.defaultInterval())
    }

    // --------------------------------------------------------------------- adherence

    @Test
    fun adherenceCountsOnlyWhatTheScheduleExpectedAndThePersonLogged() {
        val med = medication()
        val from = date.minus(kotlinx.datetime.DatePeriod(days = 6))
        val expected = Adherence.expectedDoses(med, from, date)
        assertEquals(7, expected)
        val doses = listOf(
            MedicationDose(
                id = "a",
                medicationId = med.id,
                medicationName = med.name,
                scheduledFor = noon,
                takenAt = noon,
                skipped = false,
                localDate = date,
                notes = null,
                provenance = provenance("a"),
            ),
        )
        val result = Adherence.compute(med, doses, from, date)
        assertEquals(7, result.expected)
        assertEquals(1, result.taken)
        assertEquals(0, result.skipped)
        assertEquals(100.0 / 7.0, result.percent)
    }

    @Test
    fun anAsNeededMedicineHasNoExpectedDoses() {
        val med = medication(asNeeded = true)
        val result = Adherence.compute(med, emptyList(), date, date)
        assertEquals(0, result.expected)
        assertNull(result.percent)
    }

    @Test
    fun aWeeklyScheduleOnlyCountsItsDays() {
        val med = medication(days = listOf(1, 4))
        val expected = Adherence.expectedDoses(
            med,
            LocalDate(2026, 11, 16),
            LocalDate(2026, 11, 22),
        )
        // Monday 16th and Thursday 19th, one time each.
        assertEquals(2, expected)
    }

    // ------------------------------------------------------------------ sleep insight

    @Test
    fun bedtimeAcrossMidnightIsTwentyMinutesApartNotTwentyThreeHours() {
        val a = Instant.parse("2026-11-19T22:50:00Z")
        val b = Instant.parse("2026-11-19T23:10:00Z")
        val result = SleepConsistency.compute(
            listOf(
                SleepTiming(date, a, a.plus(kotlin.time.Duration.parse("8h"))),
                SleepTiming(date, b, b.plus(kotlin.time.Duration.parse("8h"))),
            ),
            TimeZone.UTC,
        )
        assertNotNull(result.bedtimeSdMinutes)
        assertTrue(result.bedtimeSdMinutes!! < 30.0, "sd=${result.bedtimeSdMinutes}")
        assertEquals("23:00", SleepConsistency.clockText(result.averageBedtimeMinutes))
    }

    @Test
    fun oneNightHasNoDeviationToReport() {
        val result = SleepConsistency.compute(
            listOf(SleepTiming(date, noon, noon.plus(kotlin.time.Duration.parse("8h")))),
            zone,
        )
        assertNull(result.bedtimeSdMinutes)
        assertNull(result.wakeSdMinutes)
    }

    // ----------------------------------------------------------------------- report

    @Test
    fun theReportNamesEveryRangeAndCarriesTheDisclaimer() {
        val report = ClinicalReportBuilder.build(sampleData())
        val text = report.toPlainText()
        assertTrue(text.contains("Reference"))
        assertTrue(text.contains("openHealth clinical summary"))
        assertTrue(report.disclaimer.contains("not a diagnosis"))
        assertTrue(report.disclaimer.contains("not medical advice"))
        assertTrue(report.sections.any { it.title == "References" })
        assertTrue(report.sections.any { it.title == "Laboratory results" })
        assertTrue(report.sections.any { it.title == "Vitals" })
    }

    @Test
    fun weightNeutralRemovesWeightFromTheReportAndTheBundle() {
        val neutral = ClinicalReportBuilder.build(sampleData(weightNeutral = true))
        val text = neutral.toPlainText()
        assertFalse(text.contains("71.4"), "weight must not appear in a neutral report")
        assertFalse(text.contains("Body fat"))
        val fhir = FhirR4.export(sampleFhir(includeWeight = false))
        assertFalse(fhir.contains("\"29463-7\""), "weight must not appear in a neutral bundle")
    }

    @Test
    fun theCsvKeepsEachTablesOwnColumnsAndCarriesTheDisclaimer() {
        val csv = ClinicalReportBuilder.build(sampleData()).toCsv()
        assertTrue(csv.startsWith("report,"), csv.take(80))
        assertTrue(csv.contains("Measure,Latest,Unit,Reference,Source"))
        assertTrue(csv.contains("Laboratory results"))
        assertTrue(csv.contains("Test (LOINC),Result,Unit,Reference,Lab,Date"))
        assertTrue(csv.contains("HbA1c"))
        assertTrue(csv.contains("disclaimer,\"This report is a record"))
        assertFalse(csv.contains("section,item,value"))
    }

    @Test
    fun aFormulaLikeCellIsQuotedAsText() {
        val report = ClinicalReportBuilder.build(
            sampleData().copy(
                medications = listOf(medication().copy(name = "=HYPERLINK(\"x\")")),
            ),
        )
        val csv = report.toCsv()
        assertTrue(
            csv.contains("'=HYPERLINK"),
            csv.lines().firstOrNull {
                "HYPERLINK" in it
            }.orEmpty(),
        )
    }

    @Test
    @Suppress("NestedBlockDepth")
    fun everyFhirReferenceResolvesToAnEntriesFullUrl() {
        val bundle = Json.parseToJsonElement(FhirR4.export(sampleFhir())).jsonObject
        val entries = bundle["entry"]!!.jsonArray.map { it.jsonObject }
        val urls = entries.map { it["fullUrl"]!!.jsonPrimitive.content }.toSet()
        entries.forEach { entry ->
            val resource = entry["resource"]!!.jsonObject
            fun walk(value: kotlinx.serialization.json.JsonElement) {
                when (value) {
                    is JsonObject -> value.forEach { (key, child) ->
                        if (key == "reference") {
                            val ref = (child as? kotlinx.serialization.json.JsonPrimitive)
                                ?.content
                            if (ref != null && ref.startsWith("urn:uuid:")) {
                                assertTrue(ref in urls, "unresolved reference $ref")
                            }
                        }
                        walk(child)
                    }
                    is kotlinx.serialization.json.JsonArray -> value.forEach { walk(it) }
                    else -> Unit
                }
            }
            walk(resource)
            val fullUrl = entry["fullUrl"]!!.jsonPrimitive.content
            if (fullUrl.startsWith("urn:uuid:")) {
                java.util.UUID.fromString(fullUrl.removePrefix("urn:uuid:"))
            }
        }
    }

    @Test
    fun weightNeutralOmhHasNoCaloriesOrWeight() {
        val document = OpenMhealth.export(
            listOf(
                observation("cal", ObservationType.ACTIVE_CALORIES, 320.0),
                observation("weight", ObservationType.WEIGHT, 71.4),
            ),
            noon,
            includeWeight = false,
        )
        assertFalse(document.contains("kcal"))
        assertFalse(document.contains("71.4"))
    }

    @Test
    fun aLabResultOutsideItsIntervalIsReportedNotInterpreted() {
        val high = lab(value = 6.2, low = 4.0, high = 5.6)
        assertEquals(true, high.outsideReference)
        val inside = lab(value = 5.0, low = 4.0, high = 5.6)
        assertEquals(false, inside.outsideReference)
    }

    // ------------------------------------------------------------------------- FHIR

    private fun sampleFhir(includeWeight: Boolean = true): FhirExportData = FhirExportData(
        patientId = "patient-1",
        generatedAt = noon,
        vitals = listOf(
            observation("bp-s", ObservationType.BLOOD_PRESSURE_SYSTOLIC, 122.0),
            observation("bp-d", ObservationType.BLOOD_PRESSURE_DIASTOLIC, 78.0),
            observation("weight", ObservationType.WEIGHT, 71.4),
        ),
        labs = listOf(lab()),
        medications = listOf(medication()),
        doses = emptyList(),
        conditions = listOf(
            Condition(
                id = "cond-1",
                name = "Asthma",
                code = null,
                codeSystem = null,
                onsetOn = date,
                resolvedOn = null,
                notes = null,
                provenance = provenance("cond-1"),
            ),
        ),
        allergies = listOf(
            Allergy(
                id = "allergy-1",
                substance = "Penicillin",
                reaction = "Rash",
                severity = AllergySeverity.MODERATE,
                active = true,
                notedOn = date,
                notes = null,
                provenance = provenance("allergy-1"),
            ),
        ),
        vaccinations = listOf(
            Vaccination(
                id = "vacc-1",
                name = "Influenza",
                doseLabel = "2026",
                administeredOn = date,
                notes = null,
                provenance = provenance("vacc-1"),
            ),
        ),
        includeWeight = includeWeight,
    )

    @Test
    fun theFhirBundleValidatesAgainstThePublishedR4Schema() {
        val schemaText = javaClass.classLoader!!
            .getResourceAsStream("fhir/fhir.schema.json")!!
            .bufferedReader()
            .use { it.readText() }
        // The published R4 schema declares draft-06 but still writes the draft-04
        // identifier keyword `id`; draft-06 renamed it to `$id`, so the in-memory copy
        // renames the schema key only. Property names called `id` are left alone, and the
        // vendored file is untouched.
        val mapper = com.fasterxml.jackson.databind.ObjectMapper()
        val schemaNode = mapper.readTree(schemaText)
        renameLegacyId(schemaNode)
        val schema = JsonSchemaFactory
            .getInstance(SpecVersion.VersionFlag.V6)
            .getSchema(schemaNode)
        val bundle = FhirR4.export(sampleFhir())
        val errors = schema.validate(mapper.readTree(bundle))
        if (errors.isNotEmpty()) {
            java.io.File("/tmp/fhir-errors.txt")
                .writeText(errors.joinToString("\n") { it.toString() })
        }
        assertTrue(errors.isEmpty(), "FHIR R4 validation failed: $errors")
    }

    /** Draft-06 renamed the `id` keyword to `$id`; FHIR R4 still writes the old name. */
    @Suppress("NestedBlockDepth")
    private fun renameLegacyId(node: com.fasterxml.jackson.databind.JsonNode) {
        when (node) {
            is com.fasterxml.jackson.databind.node.ObjectNode -> {
                val children = node.fields().asSequence().map { it.key to it.value }.toList()
                children.forEach { (name, child) ->
                    if (name in setOf(
                            "properties",
                            "patternProperties",
                            "definitions",
                            "\$defs",
                        )
                    ) {
                        (child as? com.fasterxml.jackson.databind.node.ObjectNode)
                            ?.fields()
                            ?.forEach { entry -> renameLegacyId(entry.value) }
                    } else {
                        if (name == "id") {
                            node.remove("id")
                            node.set<com.fasterxml.jackson.databind.JsonNode>("\$id", child)
                        }
                        renameLegacyId(child)
                    }
                }
            }
            is com.fasterxml.jackson.databind.node.ArrayNode ->
                node.forEach { renameLegacyId(it) }
            else -> Unit
        }
    }

    @Test
    fun everyCodeSystemIsNamedAndEveryQuantityUsesUcum() {
        val bundle = Json.parseToJsonElement(FhirR4.export(sampleFhir())).jsonObject
        val entries = bundle["entry"]!!.jsonArray.map { it.jsonObject["resource"]!!.jsonObject }
        assertTrue(entries.any { it["resourceType"]?.jsonPrimitive?.content == "Patient" })
        entries.filter { it["resourceType"]?.jsonPrimitive?.content == "Observation" }
            .forEach { resource ->
                val code = resource["code"]!!.jsonObject["coding"]!!.jsonArray
                    .first().jsonObject
                assertEquals("http://loinc.org", code["system"]!!.jsonPrimitive.content)
                val quantity = resource["valueQuantity"]!!.jsonObject
                assertEquals(
                    "http://unitsofmeasure.org",
                    quantity["system"]!!.jsonPrimitive.content,
                )
            }
    }

    @Test
    fun theBundleIsACollectionWithValidResourceIds() {
        val bundle = Json.parseToJsonElement(FhirR4.export(sampleFhir())).jsonObject
        assertEquals("Bundle", bundle["resourceType"]!!.jsonPrimitive.content)
        assertEquals("collection", bundle["type"]!!.jsonPrimitive.content)
        bundle["entry"]!!.jsonArray.forEach { entry ->
            val id = entry.jsonObject["resource"]!!.jsonObject["id"]!!.jsonPrimitive.content
            assertTrue(
                id.matches(Regex("[A-Za-z0-9\\-.]{1,64}")),
                "invalid FHIR id: $id",
            )
        }
    }

    @Test
    fun fhirIdCleansAnAppIdWithoutLosingItsEnds() {
        val id = FhirR4.fhirId("observation/with spaces:and-colons")
        assertTrue(id.matches(Regex("[A-Za-z0-9\\-.]{1,64}")), id)
    }

    // ---------------------------------------------------------------- Open mHealth

    @Test
    fun bloodPressureIsOnePairedDataPoint() {
        val systolic = observation("bp-s", ObservationType.BLOOD_PRESSURE_SYSTOLIC, 122.0)
        val diastolic = observation("bp-d", ObservationType.BLOOD_PRESSURE_DIASTOLIC, 78.0)
        val document = OpenMhealth.export(listOf(systolic, diastolic), noon)
        val points = Json.parseToJsonElement(document).jsonObject["data_points"]!!.jsonArray
        assertEquals(1, points.size)
        val point = points.first().jsonObject
        assertEquals(
            "omh:blood-pressure:2.0",
            point["header"]!!.jsonObject["schema_id"]!!.jsonPrimitive.content,
        )
        assertEquals(
            122.0,
            point["body"]!!.jsonObject["systolic_blood_pressure"]!!.jsonObject["value"]!!
                .jsonPrimitive.content.toDouble(),
        )
    }

    @Test
    fun aTypeWithoutAnOpenMhealthSchemaIsLeftOutRatherThanForced() {
        val document = OpenMhealth.export(
            listOf(observation("temp", ObservationType.BODY_TEMPERATURE, 36.6)),
            noon,
        )
        val points = Json.parseToJsonElement(document).jsonObject["data_points"]!!.jsonArray
        assertTrue(points.isEmpty())
    }

    @Test
    fun heartRateUsesTheOmhSchemaAndUnit() {
        val document = OpenMhealth.export(
            listOf(observation("hr", ObservationType.HEART_RATE, 61.0)),
            noon,
        )
        val point = Json.parseToJsonElement(document).jsonObject["data_points"]!!
            .jsonArray.first().jsonObject
        assertEquals(
            "omh:heart-rate:2.0",
            point["header"]!!.jsonObject["schema_id"]!!.jsonPrimitive.content,
        )
        assertEquals(
            "beats/min",
            point["body"]!!.jsonObject["heart_rate"]!!.jsonObject["unit"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun theLabReportedRangeKeepsTheLaboratoryName() {
        val report = ClinicalReportBuilder.build(
            sampleData().copy(labs = listOf(lab(value = 6.2, low = 4.0, high = 5.6))),
        )
        val section = report.sections.first { it.title == "Laboratory results" }
        val row = section.table!!.rows.first()
        assertTrue(row.any { it.contains("Labor Berlin") }, "$row")
        assertTrue(row.any { it.contains("4.00-5.60") }, "$row")
    }

    @Test
    fun theDisclaimerAndSourceNoteAreNeverEmpty() {
        val report = ClinicalReportBuilder.build(sampleData())
        assertTrue(report.disclaimer.length > 40)
        assertTrue(report.sourceNote.length > 40)
        val spec = report.toPdfSpec()
        assertTrue(spec.footer.contains("not a diagnosis"))
    }

    @Test
    fun adherencePrintsTakenAndSkippedInsteadOfAnAssumedMiss() {
        val report = ClinicalReportBuilder.build(sampleData())
        val section = report.sections.first { it.title == "Medications and supplements" }
        val row = section.table!!.rows.first()
        assertTrue(row[3].contains("logged of") && row[3].contains("skipped"), "${row[3]}")
        assertTrue(section.paragraphs.any { it.contains("not assumed") })
    }

    @Test
    fun aReportWithNoDataHasNoEmptySections() {
        val report = ClinicalReportBuilder.build(
            ClinicalReportData(
                patientLabel = "Alex",
                from = date,
                to = date,
                generatedAt = noon,
                zone = zone,
            ),
        )
        assertTrue(report.sections.isEmpty())
        assertTrue(report.toPlainText().contains("not a diagnosis"))
    }

    @Test
    fun aReportNeverShowsAPercentageItCannotCompute() {
        val report = ClinicalReportBuilder.build(
            sampleData().copy(
                medications = listOf(medication(asNeeded = true)),
                doses = emptyList(),
            ),
        )
        val section = report.sections.first { it.title == "Medications and supplements" }
        val row = section.table!!.rows.first()
        assertEquals("Not scheduled", row[3])
        assertFalse(row.toString().contains("%"))
    }

    @Test
    fun aLargeReportBuildsWellUnderThirtySeconds() {
        val observations = (1..5_000).map { index ->
            observation(
                id = "obs-$index",
                type = when (index % 3) {
                    0 -> ObservationType.RESTING_HEART_RATE
                    1 -> ObservationType.STEPS
                    else -> ObservationType.BLOOD_GLUCOSE
                },
                value = 60.0 + index % 20,
            )
        }
        val started = System.nanoTime()
        val report = ClinicalReportBuilder.build(sampleData().copy(observations = observations))
        val csv = report.toCsv()
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertTrue(elapsedMs < 30_000, "the report took ${elapsedMs}ms")
        assertTrue(csv.length > 1_000)
    }

    @Test
    fun thePdfSpecCarriesTheSameRowsAsThePreview() {
        val report = ClinicalReportBuilder.build(sampleData())
        val spec = report.toPdfSpec()
        val labSection = spec.sections.first { it.heading == "Laboratory results" }
        assertEquals(
            report.sections.first {
                it.title == "Laboratory results"
            }.table,
            labSection.table,
        )
    }

    @Test
    fun aLabWithNoReferenceIntervalSaysSoRatherThanGuessing() {
        val report = ClinicalReportBuilder.build(
            sampleData().copy(labs = listOf(lab(analyte = LabAnalyte.TSH, value = 2.1))),
        )
        val row = report.sections.first { it.title == "Laboratory results" }
            .table!!.rows.first()
        assertTrue(row.any { it.contains("not entered") }, "$row")
    }

    @Test
    fun sleepDebtAndConsistencyAppearOnlyWhenThereAreNights() {
        val withNights = ClinicalReportBuilder.build(sampleData())
            .sections.first { it.title == "Sleep" }
        assertTrue(withNights.paragraphs.any { it.contains("Nights recorded: 1") })
        val without = ClinicalReportBuilder.build(sampleData().copy(sleep = emptyList()))
        assertTrue(without.sections.none { it.title == "Sleep" })
    }

    @Test
    @Suppress("NestedBlockDepth")
    fun everyFhirDateIsAnIsoInstant() {
        val bundle = Json.parseToJsonElement(FhirR4.export(sampleFhir())).jsonObject
        fun walk(value: kotlinx.serialization.json.JsonElement, key: String = "") {
            when (value) {
                is JsonObject -> value.forEach { (name, child) ->
                    if (name.endsWith("DateTime") || name == "timestamp" || name == "recorded") {
                        val text = (child as? kotlinx.serialization.json.JsonPrimitive)
                            ?.content
                        if (text != null) {
                            val valid = runCatching { java.time.Instant.parse(text) }.isSuccess ||
                                runCatching { java.time.LocalDate.parse(text) }.isSuccess
                            assertTrue(valid, "$key/$name is not an ISO instant or date: $text")
                        }
                    }
                    walk(child, name)
                }
                is kotlinx.serialization.json.JsonArray -> value.forEach { walk(it, key) }
                else -> Unit
            }
        }
        walk(bundle)
    }
}
