package app.openhealth.data.repo

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
import app.openhealth.core.ids.RawRecordId
import app.openhealth.core.ids.SourceId
import app.openhealth.core.observation.Provenance
import app.openhealth.core.time.TimeProvenance
import app.openhealth.core.units.UcumUnit
import app.openhealth.data.db.OpenHealthDb
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json

/**
 * The clinical record (`pr.md` §6.9). It is a layer-4 repository like the body and
 * training ones: it writes the domain row and never invents provenance. Deletion is not
 * here; it goes through [TrashService] so every destructive action leaves a tombstone
 * (`pr.md` §6.29).
 */
@Suppress("TooManyFunctions", "LargeClass")
class ClinicalRepository(private val db: OpenHealthDb) {
    private val json = Json { ignoreUnknownKeys = true }
    private var sync: app.openhealth.data.sync.SyncRepository? = null

    fun recordChangesWith(recorder: app.openhealth.data.sync.SyncRepository?) {
        sync = recorder
    }

    // ------------------------------------------------------------------ medications

    fun saveMedication(medication: Medication) {
        val existing = db.clinicalQueries.medicationById(medication.id).executeAsOneOrNull()
        if (existing == null) {
            db.clinicalQueries.insertMedication(
                id = medication.id,
                name = medication.name,
                doseAmount = medication.doseAmount,
                doseUnit = medication.doseUnit.ucum,
                form = medication.form.name,
                scheduleJson = json.encodeToString(
                    MedicationSchedule.serializer(),
                    medication.schedule,
                ),
                active = medication.active,
                startedOn = medication.startedOn?.toString(),
                endedOn = medication.endedOn?.toString(),
                notes = medication.notes,
                localDate = medication.provenance.localDate.toString(),
                zoneId = medication.provenance.zone.id,
                happenedAt = medication.provenance.happenedAt.toEpochMilliseconds(),
                recordedAt = medication.provenance.recordedAt.toEpochMilliseconds(),
                sourceId = medication.provenance.sourceId.value,
                rawRecordId = medication.provenance.rawRecordId?.value,
                idempotencyKey = medication.provenance.idempotencyKey,
                device = medication.provenance.device,
                appVersion = medication.provenance.appVersion,
                revision = medication.revision,
            )
        } else {
            db.clinicalQueries.updateMedication(
                name = medication.name,
                doseAmount = medication.doseAmount,
                doseUnit = medication.doseUnit.ucum,
                form = medication.form.name,
                scheduleJson = json.encodeToString(
                    MedicationSchedule.serializer(),
                    medication.schedule,
                ),
                active = medication.active,
                startedOn = medication.startedOn?.toString(),
                endedOn = medication.endedOn?.toString(),
                notes = medication.notes,
                revision = medication.revision,
                id = medication.id,
            )
        }
        sync?.capture(
            "medication",
            medication.id,
            medication.provenance.localDate.toString(),
        )
    }

    fun medications(): List<Medication> =
        db.clinicalQueries.allMedications().executeAsList().map { it.toDomain() }

    fun activeMedications(): List<Medication> =
        db.clinicalQueries.activeMedications().executeAsList().map { it.toDomain() }

    fun medication(id: String): Medication? =
        db.clinicalQueries.medicationById(id).executeAsOneOrNull()?.toDomain()

    fun saveDose(dose: MedicationDose) {
        val existingDose = db.clinicalQueries.doseById(dose.id).executeAsOneOrNull()
        if (existingDose != null) {
            db.clinicalQueries.updateDose(
                takenAt = dose.takenAt?.toEpochMilliseconds(),
                skipped = dose.skipped,
                notes = dose.notes,
                revision = existingDose.revision + 1,
                id = dose.id,
            )
            return
        }
        db.clinicalQueries.insertDose(
            id = dose.id,
            medicationId = dose.medicationId,
            scheduledFor = dose.scheduledFor.toEpochMilliseconds(),
            takenAt = dose.takenAt?.toEpochMilliseconds(),
            skipped = dose.skipped,
            localDate = dose.localDate.toString(),
            zoneId = dose.provenance.zone.id,
            happenedAt = dose.provenance.happenedAt.toEpochMilliseconds(),
            recordedAt = dose.provenance.recordedAt.toEpochMilliseconds(),
            sourceId = dose.provenance.sourceId.value,
            rawRecordId = dose.provenance.rawRecordId?.value,
            idempotencyKey = dose.provenance.idempotencyKey,
            device = dose.provenance.device,
            appVersion = dose.provenance.appVersion,
            notes = dose.notes,
            revision = 1,
        )
        sync?.capture("medication_dose", dose.id, dose.provenance.localDate.toString())
    }

    fun dosesBetween(from: LocalDate, to: LocalDate): List<MedicationDose> =
        db.clinicalQueries.dosesBetween(from.toString(), to.toString())
            .executeAsList()
            .map { it.toDomain() }

    fun dosesForMedication(medicationId: String): List<MedicationDose> =
        db.clinicalQueries.dosesForMedication(medicationId).executeAsList().map { it.toDomain() }

    fun dose(id: String): MedicationDose? =
        db.clinicalQueries.doseById(id).executeAsOneOrNull()?.toDomain()

    fun allDoses(): List<MedicationDose> =
        db.clinicalQueries.allDoses().executeAsList().map { it.toDomain() }

    // --------------------------------------------------------------------- symptoms

    fun saveSymptom(symptom: Symptom) {
        val existing = db.clinicalQueries.symptomById(symptom.id).executeAsOneOrNull()
        if (existing == null) {
            db.clinicalQueries.insertSymptom(
                id = symptom.id,
                name = symptom.name,
                notes = symptom.notes,
                localDate = symptom.provenance.localDate.toString(),
                zoneId = symptom.provenance.zone.id,
                happenedAt = symptom.provenance.happenedAt.toEpochMilliseconds(),
                recordedAt = symptom.provenance.recordedAt.toEpochMilliseconds(),
                sourceId = symptom.provenance.sourceId.value,
                rawRecordId = symptom.provenance.rawRecordId?.value,
                idempotencyKey = symptom.provenance.idempotencyKey,
                device = symptom.provenance.device,
                appVersion = symptom.provenance.appVersion,
                revision = symptom.revision,
            )
        } else {
            db.clinicalQueries.updateSymptom(
                name = symptom.name,
                notes = symptom.notes,
                revision = symptom.revision,
                id = symptom.id,
            )
        }
        sync?.capture("symptom", symptom.id, symptom.provenance.localDate.toString())
    }

    fun symptoms(): List<Symptom> =
        db.clinicalQueries.allSymptoms().executeAsList().map { it.toDomain() }

    fun symptom(id: String): Symptom? =
        db.clinicalQueries.symptomById(id).executeAsOneOrNull()?.toDomain()

    fun saveEpisode(episode: SymptomEpisode) {
        if (db.clinicalQueries.episodeById(episode.id).executeAsOneOrNull() != null) {
            db.clinicalQueries.updateEpisode(
                symptomId = episode.symptomId,
                localDate = episode.localDate.toString(),
                happenedAt = episode.provenance.happenedAt.toEpochMilliseconds(),
                severity = episode.severity.toLong(),
                startedAt = episode.startedAt.toEpochMilliseconds(),
                endedAt = episode.endedAt?.toEpochMilliseconds(),
                triggers = episode.triggers,
                notes = episode.notes,
                revision = episode.revision,
                id = episode.id,
            )
            return
        }
        db.clinicalQueries.insertEpisode(
            id = episode.id,
            symptomId = episode.symptomId,
            severity = episode.severity.toLong(),
            startedAt = episode.startedAt.toEpochMilliseconds(),
            endedAt = episode.endedAt?.toEpochMilliseconds(),
            triggers = episode.triggers,
            notes = episode.notes,
            localDate = episode.localDate.toString(),
            zoneId = episode.provenance.zone.id,
            happenedAt = episode.provenance.happenedAt.toEpochMilliseconds(),
            recordedAt = episode.provenance.recordedAt.toEpochMilliseconds(),
            sourceId = episode.provenance.sourceId.value,
            rawRecordId = episode.provenance.rawRecordId?.value,
            idempotencyKey = episode.provenance.idempotencyKey,
            device = episode.provenance.device,
            appVersion = episode.provenance.appVersion,
            revision = 1,
        )
        sync?.capture(
            "symptom_episode",
            episode.id,
            episode.provenance.localDate.toString(),
        )
    }

    fun episodesBetween(from: LocalDate, to: LocalDate): List<SymptomEpisode> =
        db.clinicalQueries.episodesBetween(from.toString(), to.toString())
            .executeAsList()
            .mapNotNull { it.toDomain() }

    fun allEpisodes(): List<SymptomEpisode> =
        db.clinicalQueries.allEpisodes().executeAsList().mapNotNull { it.toDomain() }

    fun episode(id: String): SymptomEpisode? =
        db.clinicalQueries.episodeById(id).executeAsOneOrNull()?.toDomain()

    // ------------------------------------------------------------------------- labs

    fun saveLab(lab: LabResult) {
        if (db.clinicalQueries.labById(lab.id).executeAsOneOrNull() != null) {
            db.clinicalQueries.updateLab(
                loincCode = lab.analyte.loinc,
                display = lab.analyte.display,
                localDate = lab.provenance.localDate.toString(),
                happenedAt = lab.provenance.happenedAt.toEpochMilliseconds(),
                value = lab.value,
                unit = lab.unit.ucum,
                referenceLow = lab.referenceLow,
                referenceHigh = lab.referenceHigh,
                referenceNote = lab.referenceNote,
                referenceCitation = lab.referenceCitation?.name,
                collectedAt = lab.collectedAt.toEpochMilliseconds(),
                labName = lab.labName,
                notes = lab.notes,
                revision = lab.revision,
                id = lab.id,
            )
            return
        }
        db.clinicalQueries.insertLab(
            id = lab.id,
            loincCode = lab.analyte.loinc,
            display = lab.analyte.display,
            value = lab.value,
            unit = lab.unit.ucum,
            referenceLow = lab.referenceLow,
            referenceHigh = lab.referenceHigh,
            referenceNote = lab.referenceNote,
            referenceCitation = lab.referenceCitation?.name,
            collectedAt = lab.collectedAt.toEpochMilliseconds(),
            localDate = lab.provenance.localDate.toString(),
            zoneId = lab.provenance.zone.id,
            happenedAt = lab.provenance.happenedAt.toEpochMilliseconds(),
            recordedAt = lab.provenance.recordedAt.toEpochMilliseconds(),
            sourceId = lab.provenance.sourceId.value,
            rawRecordId = lab.provenance.rawRecordId?.value,
            idempotencyKey = lab.provenance.idempotencyKey,
            labName = lab.labName,
            notes = lab.notes,
            device = lab.provenance.device,
            appVersion = lab.provenance.appVersion,
            revision = lab.revision,
        )
        sync?.capture("lab_result", lab.id, lab.provenance.localDate.toString())
    }

    fun labsBetween(from: LocalDate, to: LocalDate): List<LabResult> =
        db.clinicalQueries.labsBetween(from.toString(), to.toString())
            .executeAsList()
            .mapNotNull { it.toDomain() }

    fun labsForAnalyte(analyte: LabAnalyte): List<LabResult> =
        db.clinicalQueries.labsForAnalyte(analyte.loinc).executeAsList()
            .mapNotNull { it.toDomain() }

    fun allLabs(): List<LabResult> =
        db.clinicalQueries.allLabs().executeAsList().mapNotNull { it.toDomain() }

    fun lab(id: String): LabResult? =
        db.clinicalQueries.labById(id).executeAsOneOrNull()?.toDomain()

    // ------------------------------------------------------ allergies and conditions

    fun saveAllergy(allergy: Allergy) {
        if (db.clinicalQueries.allergyById(allergy.id).executeAsOneOrNull() != null) {
            db.clinicalQueries.updateAllergy(
                localDate = allergy.provenance.localDate.toString(),
                happenedAt = allergy.provenance.happenedAt.toEpochMilliseconds(),
                substance = allergy.substance,
                reaction = allergy.reaction,
                severity = allergy.severity.name,
                active = allergy.active,
                notedOn = allergy.notedOn?.toString(),
                notes = allergy.notes,
                revision = allergy.revision,
                id = allergy.id,
            )
            return
        }
        db.clinicalQueries.insertAllergy(
            id = allergy.id,
            substance = allergy.substance,
            reaction = allergy.reaction,
            severity = allergy.severity.name,
            active = allergy.active,
            notedOn = allergy.notedOn?.toString(),
            notes = allergy.notes,
            localDate = allergy.provenance.localDate.toString(),
            zoneId = allergy.provenance.zone.id,
            happenedAt = allergy.provenance.happenedAt.toEpochMilliseconds(),
            recordedAt = allergy.provenance.recordedAt.toEpochMilliseconds(),
            sourceId = allergy.provenance.sourceId.value,
            rawRecordId = allergy.provenance.rawRecordId?.value,
            idempotencyKey = allergy.provenance.idempotencyKey,
            device = allergy.provenance.device,
            appVersion = allergy.provenance.appVersion,
            revision = allergy.revision,
        )
        sync?.capture("allergy", allergy.id, allergy.provenance.localDate.toString())
    }

    fun allergies(): List<Allergy> =
        db.clinicalQueries.allAllergies().executeAsList().map { it.toDomain() }

    fun allergy(id: String): Allergy? =
        db.clinicalQueries.allergyById(id).executeAsOneOrNull()?.toDomain()

    fun saveCondition(condition: Condition) {
        if (db.clinicalQueries.conditionById(condition.id).executeAsOneOrNull() != null) {
            db.clinicalQueries.updateCondition(
                localDate = condition.provenance.localDate.toString(),
                happenedAt = condition.provenance.happenedAt.toEpochMilliseconds(),
                name = condition.name,
                code = condition.code,
                codeSystem = condition.codeSystem,
                onsetOn = condition.onsetOn?.toString(),
                resolvedOn = condition.resolvedOn?.toString(),
                notes = condition.notes,
                revision = condition.revision,
                id = condition.id,
            )
            return
        }
        db.clinicalQueries.insertCondition(
            id = condition.id,
            name = condition.name,
            code = condition.code,
            codeSystem = condition.codeSystem,
            onsetOn = condition.onsetOn?.toString(),
            resolvedOn = condition.resolvedOn?.toString(),
            notes = condition.notes,
            localDate = condition.provenance.localDate.toString(),
            zoneId = condition.provenance.zone.id,
            happenedAt = condition.provenance.happenedAt.toEpochMilliseconds(),
            recordedAt = condition.provenance.recordedAt.toEpochMilliseconds(),
            sourceId = condition.provenance.sourceId.value,
            rawRecordId = condition.provenance.rawRecordId?.value,
            idempotencyKey = condition.provenance.idempotencyKey,
            device = condition.provenance.device,
            appVersion = condition.provenance.appVersion,
            revision = condition.revision,
        )
        sync?.capture("condition", condition.id, condition.provenance.localDate.toString())
    }

    fun conditions(): List<Condition> =
        db.clinicalQueries.allConditions().executeAsList().map { it.toDomain() }

    fun condition(id: String): Condition? =
        db.clinicalQueries.conditionById(id).executeAsOneOrNull()?.toDomain()

    fun saveVaccination(vaccination: Vaccination) {
        if (db.clinicalQueries.vaccinationById(vaccination.id).executeAsOneOrNull() != null) {
            db.clinicalQueries.updateVaccination(
                localDate = vaccination.provenance.localDate.toString(),
                happenedAt = vaccination.provenance.happenedAt.toEpochMilliseconds(),
                name = vaccination.name,
                doseLabel = vaccination.doseLabel,
                administeredOn = vaccination.administeredOn.toString(),
                notes = vaccination.notes,
                revision = vaccination.revision,
                id = vaccination.id,
            )
            return
        }
        db.clinicalQueries.insertVaccination(
            id = vaccination.id,
            name = vaccination.name,
            doseLabel = vaccination.doseLabel,
            administeredOn = vaccination.administeredOn.toString(),
            notes = vaccination.notes,
            localDate = vaccination.provenance.localDate.toString(),
            zoneId = vaccination.provenance.zone.id,
            happenedAt = vaccination.provenance.happenedAt.toEpochMilliseconds(),
            recordedAt = vaccination.provenance.recordedAt.toEpochMilliseconds(),
            sourceId = vaccination.provenance.sourceId.value,
            rawRecordId = vaccination.provenance.rawRecordId?.value,
            idempotencyKey = vaccination.provenance.idempotencyKey,
            device = vaccination.provenance.device,
            appVersion = vaccination.provenance.appVersion,
            revision = vaccination.revision,
        )
        sync?.capture("vaccination", vaccination.id, vaccination.provenance.localDate.toString())
    }

    fun vaccinations(): List<Vaccination> =
        db.clinicalQueries.allVaccinations().executeAsList().map { it.toDomain() }

    fun vaccination(id: String): Vaccination? =
        db.clinicalQueries.vaccinationById(id).executeAsOneOrNull()?.toDomain()

    fun counts(): ClinicalCounts = ClinicalCounts(
        medications = db.clinicalQueries.countMedications().executeAsOne(),
        doses = db.clinicalQueries.countDoses().executeAsOne(),
        episodes = db.clinicalQueries.countEpisodes().executeAsOne(),
        labs = db.clinicalQueries.countLabs().executeAsOne(),
        vaccinations = db.clinicalQueries.countVaccinations().executeAsOne(),
    )

    // ---------------------------------------------------------------------- mapping

    private fun app.openhealth.data.db.Medication.toDomain(): Medication = Medication(
        id = id,
        name = name,
        doseAmount = dose_amount,
        doseUnit = UcumUnit.byUcum(dose_unit),
        form = MedicationForm.valueOf(form),
        schedule = json.decodeFromString(MedicationSchedule.serializer(), schedule_json),
        active = active,
        startedOn = started_on?.let(LocalDate::parse),
        endedOn = ended_on?.let(LocalDate::parse),
        notes = notes,
        provenance = provenance(
            source_id,
            happened_at,
            recorded_at,
            local_date,
            zone_id,
            idempotency_key,
            device,
            app_version,
            raw_record_id,
        ),
        revision = revision,
    )

    private fun app.openhealth.data.db.Medication_dose.toDomain(): MedicationDose {
        val medication = medication(medication_id)
        return MedicationDose(
            id = id,
            medicationId = medication_id,
            medicationName = medication?.name ?: medication_id,
            scheduledFor = Instant.fromEpochMilliseconds(scheduled_for),
            takenAt = taken_at?.let(Instant::fromEpochMilliseconds),
            skipped = skipped,
            localDate = LocalDate.parse(local_date),
            notes = notes,
            provenance = provenance(
                source_id,
                happened_at,
                recorded_at,
                local_date,
                zone_id,
                idempotency_key,
                device,
                app_version,
                raw_record_id,
            ),
        )
    }

    private fun app.openhealth.data.db.Symptom.toDomain(): Symptom = Symptom(
        id = id,
        name = name,
        notes = notes,
        provenance = provenance(
            source_id,
            happened_at,
            recorded_at,
            local_date,
            zone_id,
            idempotency_key,
            device,
            app_version,
            raw_record_id,
        ),
        revision = revision,
    )

    private fun app.openhealth.data.db.Symptom_episode.toDomain(): SymptomEpisode? {
        val symptomName = symptom(symptom_id)?.name ?: return null
        return SymptomEpisode(
            id = id,
            symptomId = symptom_id,
            symptomName = symptomName,
            severity = severity.toInt(),
            startedAt = Instant.fromEpochMilliseconds(started_at),
            endedAt = ended_at?.let(Instant::fromEpochMilliseconds),
            triggers = triggers,
            notes = notes,
            localDate = LocalDate.parse(local_date),
            provenance = provenance(
                source_id,
                happened_at,
                recorded_at,
                local_date,
                zone_id,
                idempotency_key,
                device,
                app_version,
                raw_record_id,
            ),
        )
    }

    private fun app.openhealth.data.db.Lab_result.toDomain(): LabResult? {
        // A row written by a later catalogue is skipped, never allowed to fail the hub.
        val analyte = LabAnalyte.fromLoinc(loinc_code) ?: return null
        return LabResult(
            id = id,
            analyte = analyte,
            value = value_,
            unit = UcumUnit.byUcum(unit),
            referenceLow = reference_low,
            referenceHigh = reference_high,
            referenceNote = reference_note,
            referenceCitation = reference_citation?.let(CitationKey::valueOf),
            collectedAt = Instant.fromEpochMilliseconds(collected_at),
            labName = lab_name,
            notes = notes,
            provenance = provenance(
                source_id,
                happened_at,
                recorded_at,
                local_date,
                zone_id,
                idempotency_key,
                device,
                app_version,
                raw_record_id,
            ),
        )
    }

    private fun app.openhealth.data.db.Allergy.toDomain(): Allergy = Allergy(
        id = id,
        substance = substance,
        reaction = reaction,
        severity = AllergySeverity.valueOf(severity),
        active = active,
        notedOn = noted_on?.let(LocalDate::parse),
        notes = notes,
        provenance = provenance(
            source_id,
            happened_at,
            recorded_at,
            local_date,
            zone_id,
            idempotency_key,
            device,
            app_version,
            raw_record_id,
        ),
    )

    private fun app.openhealth.data.db.Condition.toDomain(): Condition = Condition(
        id = id,
        name = name,
        code = code,
        codeSystem = code_system,
        onsetOn = onset_on?.let(LocalDate::parse),
        resolvedOn = resolved_on?.let(LocalDate::parse),
        notes = notes,
        provenance = provenance(
            source_id,
            happened_at,
            recorded_at,
            local_date,
            zone_id,
            idempotency_key,
            device,
            app_version,
            raw_record_id,
        ),
    )

    private fun app.openhealth.data.db.Vaccination.toDomain(): Vaccination = Vaccination(
        id = id,
        name = name,
        doseLabel = dose_label,
        administeredOn = LocalDate.parse(administered_on),
        notes = notes,
        provenance = provenance(
            source_id,
            happened_at,
            recorded_at,
            local_date,
            zone_id,
            idempotency_key,
            device,
            app_version,
            raw_record_id,
        ),
    )

    @Suppress("LongParameterList")
    private fun provenance(
        sourceId: String,
        happenedAt: Long,
        recordedAt: Long,
        localDate: String,
        zoneId: String,
        idempotencyKey: String,
        device: String?,
        appVersion: String?,
        rawRecordId: String?,
    ): Provenance = Provenance(
        sourceId = SourceId(sourceId),
        time = TimeProvenance(
            happenedAt = Instant.fromEpochMilliseconds(happenedAt),
            recordedAt = Instant.fromEpochMilliseconds(recordedAt),
            localDate = LocalDate.parse(localDate),
            zone = TimeZone.of(zoneId),
        ),
        idempotencyKey = idempotencyKey,
        device = device,
        appVersion = appVersion,
        rawRecordId = rawRecordId?.let(::RawRecordId),
    )
}

/** What the clinical hub says it holds; counts are read from the rows, never guessed. */
data class ClinicalCounts(
    val medications: Long,
    val doses: Long,
    val episodes: Long,
    val labs: Long,
    val vaccinations: Long,
)
