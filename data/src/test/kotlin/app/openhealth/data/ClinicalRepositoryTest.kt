package app.openhealth.data

import app.openhealth.core.citation.CitationKey
import app.openhealth.core.clinical.Allergy
import app.openhealth.core.clinical.AllergySeverity
import app.openhealth.core.clinical.Condition
import app.openhealth.core.clinical.LabAnalyte
import app.openhealth.core.clinical.LabResult
import app.openhealth.core.clinical.Medication
import app.openhealth.core.clinical.MedicationDose
import app.openhealth.core.clinical.MedicationForm
import app.openhealth.core.clinical.MedicationSchedule
import app.openhealth.core.clinical.Symptom
import app.openhealth.core.clinical.SymptomEpisode
import app.openhealth.core.clinical.Vaccination
import app.openhealth.core.ids.SourceId
import app.openhealth.core.observation.Provenance
import app.openhealth.core.time.TimeProvenance
import app.openhealth.core.units.UcumUnit
import app.openhealth.data.db.OpenHealthDb
import app.openhealth.data.export.ExportTables
import app.openhealth.data.repo.ClinicalRepository
import app.openhealth.data.repo.TrashService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus

/**
 * Schema v10 and the clinical record (`BUILD-ROADMAP.md` §B12). Every row keeps its
 * provenance through a round trip, every delete leaves a restorable tombstone, and the
 * export registry knows every new table so a snapshot rebuilds the record completely.
 */
class ClinicalRepositoryTest {
    private val zone = TEST_ZONE
    private val date = LocalDate(2026, 11, 20)
    private val at = Instant.parse("2026-11-20T09:00:00Z")

    private fun prov(key: String): Provenance = Provenance(
        sourceId = SourceId("manual"),
        time = TimeProvenance.of(at, at, zone),
        idempotencyKey = "key:$key",
    )

    private fun medication(id: String = "med-1") = Medication(
        id = id,
        name = "Levothyroxine",
        doseAmount = 50.0,
        doseUnit = UcumUnit.MICROGRAM,
        form = MedicationForm.TABLET,
        schedule = MedicationSchedule(times = listOf("08:00"), daysOfWeek = listOf(1, 4)),
        active = true,
        startedOn = date,
        endedOn = null,
        notes = "before breakfast",
        provenance = prov(id),
    )

    private fun dose(id: String = "dose-1", medId: String = "med-1") = MedicationDose(
        id = id,
        medicationId = medId,
        medicationName = "Levothyroxine",
        scheduledFor = at,
        takenAt = at,
        skipped = false,
        localDate = date,
        notes = null,
        provenance = prov(id),
    )

    private fun symptom(id: String = "sym-1") = Symptom(
        id = id,
        name = "Headache",
        notes = null,
        provenance = prov(id),
    )

    private fun episode(id: String = "ep-1", symptomId: String = "sym-1") = SymptomEpisode(
        id = id,
        symptomId = symptomId,
        symptomName = "Headache",
        severity = 4,
        startedAt = at,
        endedAt = at.plus(kotlin.time.Duration.parse("2h")),
        triggers = "screen time",
        notes = null,
        localDate = date,
        provenance = prov(id),
    )

    private fun lab(id: String = "lab-1") = LabResult(
        id = id,
        analyte = LabAnalyte.HBA1C,
        value = 5.4,
        unit = LabAnalyte.HBA1C.unit,
        referenceLow = 4.0,
        referenceHigh = 5.6,
        referenceNote = "printed interval",
        referenceCitation = CitationKey.LAB_REPORTED_RANGE,
        collectedAt = at,
        labName = "Labor Berlin",
        notes = null,
        provenance = prov(id),
    )

    private fun allergy(id: String = "allergy-1") = Allergy(
        id = id,
        substance = "Penicillin",
        reaction = "Rash",
        severity = AllergySeverity.MODERATE,
        active = true,
        notedOn = date,
        notes = null,
        provenance = prov(id),
    )

    private fun condition(id: String = "cond-1") = Condition(
        id = id,
        name = "Asthma",
        code = null,
        codeSystem = null,
        onsetOn = date,
        resolvedOn = null,
        notes = null,
        provenance = prov(id),
    )

    private fun vaccination(id: String = "vacc-1") = Vaccination(
        id = id,
        name = "Influenza",
        doseLabel = "2026",
        administeredOn = date,
        notes = null,
        provenance = prov(id),
    )

    @Test
    fun theSchemaIsVersionTenAndCarriesEveryClinicalTable() {
        val store = inMemoryStore()
        assertEquals(11L, store.schemaVersion)
        assertEquals(OpenHealthDb.Schema.version, store.schemaVersion)
        ClinicalRepository(store.db).counts().also {
            assertEquals(0L, it.medications)
            assertEquals(0L, it.doses)
            assertEquals(0L, it.episodes)
            assertEquals(0L, it.labs)
            assertEquals(0L, it.vaccinations)
        }
        store.close()
    }

    @Test
    fun aMedicationAndItsDoseRoundTripWithTheirProvenance() {
        val store = inMemoryStore()
        val repo = ClinicalRepository(store.db)
        repo.saveMedication(medication())
        repo.saveDose(dose())

        val saved = assertNotNull(repo.medication("med-1"))
        assertEquals("Levothyroxine", saved.name)
        assertEquals(50.0, saved.doseAmount)
        assertEquals(UcumUnit.MICROGRAM, saved.doseUnit)
        assertEquals(listOf(1, 4), saved.schedule.daysOfWeek)
        assertEquals("key:med-1", saved.provenance.idempotencyKey)
        assertEquals(date, saved.provenance.localDate)
        assertEquals(zone, saved.provenance.zone)

        val savedDose = assertNotNull(repo.dose("dose-1"))
        assertEquals("med-1", savedDose.medicationId)
        assertEquals("Levothyroxine", savedDose.medicationName)
        assertEquals(date, savedDose.localDate)
        store.close()
    }

    @Test
    fun editingAMedicationKeepsItsIdempotencyKey() {
        val store = inMemoryStore()
        val repo = ClinicalRepository(store.db)
        repo.saveMedication(medication())
        repo.saveMedication(
            medication().copy(name = "Levothyroxine 75", doseAmount = 75.0, revision = 2),
        )
        val saved = assertNotNull(repo.medication("med-1"))
        assertEquals("Levothyroxine 75", saved.name)
        assertEquals(75.0, saved.doseAmount)
        assertEquals(2L, saved.revision)
        store.close()
    }

    @Test
    fun everyClinicalRowRoundTrips() {
        val store = inMemoryStore()
        val repo = ClinicalRepository(store.db)
        repo.saveSymptom(symptom())
        repo.saveEpisode(episode())
        repo.saveLab(lab())
        repo.saveAllergy(allergy())
        repo.saveCondition(condition())
        repo.saveVaccination(vaccination())

        assertEquals("Headache", repo.symptom("sym-1")?.name)
        assertEquals(4, repo.episode("ep-1")?.severity)
        assertEquals(date, repo.episode("ep-1")?.localDate)
        val savedLab = assertNotNull(repo.lab("lab-1"))
        assertEquals(LabAnalyte.HBA1C, savedLab.analyte)
        assertEquals(CitationKey.LAB_REPORTED_RANGE, savedLab.referenceCitation)
        assertEquals("Labor Berlin", savedLab.labName)
        assertEquals(5.4, savedLab.value)
        assertEquals(AllergySeverity.MODERATE, repo.allergy("allergy-1")?.severity)
        assertEquals(date, repo.condition("cond-1")?.onsetOn)
        assertEquals("2026", repo.vaccination("vacc-1")?.doseLabel)
        store.close()
    }

    @Test
    fun aDeletedDoseRestoresExactly() {
        val store = inMemoryStore()
        val repo = ClinicalRepository(store.db)
        val trash = TrashService(store.db)
        repo.saveMedication(medication())
        repo.saveDose(dose())
        val tombstone = assertNotNull(trash.deleteMedicationDose("dose-1"))
        assertNull(repo.dose("dose-1"))
        assertTrue(trash.undo(tombstone))
        val restored = assertNotNull(repo.dose("dose-1"))
        assertEquals("key:dose-1", restored.provenance.idempotencyKey)
        assertEquals(date, restored.localDate)
        store.close()
    }

    @Test
    fun everyClinicalDefinitionRestoresFromTrash() {
        val store = inMemoryStore()
        val repo = ClinicalRepository(store.db)
        val trash = TrashService(store.db)
        repo.saveSymptom(symptom())
        repo.saveEpisode(episode())
        repo.saveLab(lab())
        repo.saveAllergy(allergy())
        repo.saveCondition(condition())
        repo.saveVaccination(vaccination())

        val episodeTombstone = assertNotNull(trash.deleteSymptomEpisode("ep-1"))
        val labTombstone = assertNotNull(trash.deleteLabResult("lab-1"))
        val allergyTombstone = assertNotNull(trash.deleteAllergy("allergy-1"))
        val conditionTombstone = assertNotNull(trash.deleteCondition("cond-1"))
        val vaccinationTombstone = assertNotNull(trash.deleteVaccination("vacc-1"))

        assertNull(repo.episode("ep-1"))
        assertNull(repo.lab("lab-1"))
        assertNull(repo.allergy("allergy-1"))
        assertNull(repo.condition("cond-1"))
        assertNull(repo.vaccination("vacc-1"))

        assertTrue(trash.undo(episodeTombstone))
        assertTrue(trash.undo(labTombstone))
        assertTrue(trash.undo(allergyTombstone))
        assertTrue(trash.undo(conditionTombstone))
        assertTrue(trash.undo(vaccinationTombstone))

        assertEquals("key:ep-1", repo.episode("ep-1")?.provenance?.idempotencyKey)
        assertEquals("key:lab-1", repo.lab("lab-1")?.provenance?.idempotencyKey)
        assertEquals("key:allergy-1", repo.allergy("allergy-1")?.provenance?.idempotencyKey)
        assertEquals("key:cond-1", repo.condition("cond-1")?.provenance?.idempotencyKey)
        assertEquals("key:vacc-1", repo.vaccination("vacc-1")?.provenance?.idempotencyKey)
        store.close()
    }

    @Test
    fun changingALabsAnalyteChangesItsLoincCode() {
        val store = inMemoryStore()
        val repo = ClinicalRepository(store.db)
        repo.saveLab(lab())
        repo.saveLab(
            lab().copy(
                analyte = app.openhealth.core.clinical.LabAnalyte.TSH,
                unit = app.openhealth.core.clinical.LabAnalyte.TSH.unit,
                value = 2.1,
                referenceLow = null,
                referenceHigh = null,
                referenceNote = null,
                referenceCitation = null,
                revision = 2,
            ),
        )
        val saved = assertNotNull(repo.lab("lab-1"))
        assertEquals(app.openhealth.core.clinical.LabAnalyte.TSH, saved.analyte)
        assertEquals("3016-3", store.db.clinicalQueries.labById("lab-1").executeAsOne().loinc_code)
        assertEquals("TSH", store.db.clinicalQueries.labById("lab-1").executeAsOne().display)
        store.close()
    }

    @Test
    fun correctingALabDateMovesItsLocalDayAndKeepsRecordedAt() {
        val store = inMemoryStore()
        val repo = ClinicalRepository(store.db)
        repo.saveLab(lab())
        val original = assertNotNull(repo.lab("lab-1"))
        val correctedDate = date.minus(kotlinx.datetime.DatePeriod(days = 3))
        val correctedAt = correctedDate.atStartOfDayIn(TEST_ZONE).plus(9.hours)
        repo.saveLab(
            original.copy(
                collectedAt = correctedAt,
                provenance = original.provenance.copy(
                    time = app.openhealth.core.time.TimeProvenance.of(
                        correctedAt,
                        original.provenance.time.recordedAt,
                        TEST_ZONE,
                    ),
                ),
                revision = 2,
            ),
        )
        val saved = assertNotNull(repo.lab("lab-1"))
        assertEquals(correctedDate, saved.provenance.localDate)
        assertEquals(original.provenance.time.recordedAt, saved.provenance.time.recordedAt)
        store.close()
    }

    @Test
    fun movingASymptomEpisodeMovesItsLocalDayAndSymptom() {
        val store = inMemoryStore()
        val repo = ClinicalRepository(store.db)
        repo.saveSymptom(symptom())
        repo.saveSymptom(symptom("sym-2").copy(name = "Migraine"))
        repo.saveEpisode(episode())
        val original = assertNotNull(repo.episode("ep-1"))
        val moved = date.minus(kotlinx.datetime.DatePeriod(days = 2))
        val movedAt = moved.atStartOfDayIn(TEST_ZONE).plus(12.hours)
        repo.saveEpisode(
            original.copy(
                symptomId = "sym-2",
                symptomName = "Migraine",
                startedAt = movedAt,
                endedAt = movedAt.plus(kotlin.time.Duration.parse("1h")),
                localDate = moved,
                provenance = original.provenance.copy(
                    time = app.openhealth.core.time.TimeProvenance.of(
                        movedAt,
                        original.provenance.time.recordedAt,
                        TEST_ZONE,
                    ),
                ),
                revision = 2,
            ),
        )
        val saved = assertNotNull(repo.episode("ep-1"))
        assertEquals("sym-2", saved.symptomId)
        assertEquals(moved, saved.localDate)
        assertEquals(movedAt, saved.startedAt)
        store.close()
    }

    @Test
    fun theExportRegistryKnowsEveryClinicalTable() {
        listOf(
            "medication",
            "medication_dose",
            "symptom",
            "symptom_episode",
            "lab_result",
            "allergy",
            "condition",
            "vaccination",
        ).forEach { name ->
            assertNotNull(ExportTables.byName(name), "$name is missing from the export registry")
        }
        val order = ExportTables.all.map { it.name }
        assertTrue(order.indexOf("medication") < order.indexOf("medication_dose"))
        assertTrue(order.indexOf("symptom") < order.indexOf("symptom_episode"))
    }

    @Test
    fun theTimelineCarriesTheFourClinicalEvents() {
        val store = inMemoryStore()
        val repo = ClinicalRepository(store.db)
        repo.saveMedication(medication())
        repo.saveSymptom(symptom())
        repo.saveDose(dose())
        repo.saveEpisode(episode())
        repo.saveLab(lab())
        repo.saveVaccination(vaccination())
        val kinds = app.openhealth.data.repo.TimelineRepository(store.db, store.driver)
            .forDay(date).map { it.kind }.toSet()
        assertTrue("medication_dose" in kinds, "$kinds")
        assertTrue("symptom_episode" in kinds, "$kinds")
        assertTrue("lab_result" in kinds, "$kinds")
        assertTrue("vaccination" in kinds, "$kinds")
        store.close()
    }

    @Test
    fun aClinicalRowAppearsOnThePagedTimeline() {
        val store = inMemoryStore()
        val repo = ClinicalRepository(store.db)
        repo.saveLab(lab())
        val page = app.openhealth.data.repo.TimelineRepository(store.db, store.driver)
            .page(20)
        assertTrue(page.any { it.kind == "lab_result" && it.detail == "4548-4" }, "$page")
        store.close()
    }
}
