package app.openhealth.android.clinical

import android.content.Context
import app.openhealth.android.preferences.PreferencesStore
import app.openhealth.core.citation.CitationKey
import app.openhealth.core.citation.Citations
import app.openhealth.core.clinical.Adherence
import app.openhealth.core.clinical.Allergy
import app.openhealth.core.clinical.AllergySeverity
import app.openhealth.core.clinical.ClinicalReportBuilder
import app.openhealth.core.clinical.ClinicalReportData
import app.openhealth.core.clinical.Condition
import app.openhealth.core.clinical.FhirExportData
import app.openhealth.core.clinical.FhirR4
import app.openhealth.core.clinical.LabAnalyte
import app.openhealth.core.clinical.LabResult
import app.openhealth.core.clinical.Medication
import app.openhealth.core.clinical.MedicationDose
import app.openhealth.core.clinical.MedicationForm
import app.openhealth.core.clinical.MedicationSchedule
import app.openhealth.core.clinical.OpenMhealth
import app.openhealth.core.clinical.ReportActivity
import app.openhealth.core.clinical.ReportSleepNight
import app.openhealth.core.clinical.Symptom
import app.openhealth.core.clinical.SymptomEpisode
import app.openhealth.core.clinical.Vaccination
import app.openhealth.core.formula.FormulaOutcome
import app.openhealth.core.formula.SleepConsistency
import app.openhealth.core.formula.SleepDebt
import app.openhealth.core.formula.SleepNight
import app.openhealth.core.formula.SleepSample
import app.openhealth.core.formula.SleepSessions
import app.openhealth.core.formula.SleepStageBreakdown
import app.openhealth.core.formula.SleepTiming
import app.openhealth.core.ids.IdGenerator
import app.openhealth.core.ids.ObservationId
import app.openhealth.core.ids.SourceId
import app.openhealth.core.observation.MeasurementMethod
import app.openhealth.core.observation.Observation
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.observation.Provenance
import app.openhealth.core.preferences.HealthModule
import app.openhealth.core.seam.ClockSeam
import app.openhealth.core.seam.PdfSeam
import app.openhealth.core.time.TimeProvenance
import app.openhealth.core.units.Measure
import app.openhealth.core.units.UcumUnit
import app.openhealth.data.LocalStore
import app.openhealth.data.repo.SettingKeys
import app.openhealth.ui.R
import app.openhealth.ui.screens.clinical.ClinicalEditorModel
import app.openhealth.ui.screens.clinical.ClinicalFieldModel
import app.openhealth.ui.screens.clinical.ClinicalFieldType
import app.openhealth.ui.screens.clinical.ClinicalKind
import app.openhealth.ui.screens.clinical.ClinicalModel
import app.openhealth.ui.screens.clinical.ClinicalRowModel
import app.openhealth.ui.screens.clinical.ClinicalSectionKind
import app.openhealth.ui.screens.clinical.ClinicalSectionModel
import app.openhealth.ui.screens.clinical.ReportFormat
import app.openhealth.ui.screens.clinical.ReportRangeModel
import app.openhealth.ui.screens.clinical.ReportsModel
import app.openhealth.ui.screens.clinical.SleepModel
import app.openhealth.ui.screens.clinical.SleepNightRow
import app.openhealth.ui.screens.clinical.SleepStageRow
import app.openhealth.ui.screens.clinical.WeeklyReviewItemModel
import app.openhealth.ui.screens.clinical.WeeklyReviewModel
import kotlin.math.round
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/** The output of a report export: bytes and what the saver needs to name and type them. */
data class ReportFile(val bytes: ByteArray, val mime: String, val name: String)

/**
 * Sleep, the clinical record, the reports and the weekly review (`BUILD-ROADMAP.md`
 * §B12). It reads the store and returns screen models; every write goes through the
 * repository and keeps provenance. It never diagnoses and never turns a missing value
 * into a zero.
 */
@Suppress(
    "TooManyFunctions",
    "LargeClass",
    "LongParameterList",
    "LongMethod",
    "MagicNumber",
    "ReturnCount",
    "CyclomaticComplexMethod",
)
class ClinicalPresenter(
    private val context: Context,
    private val store: LocalStore,
    private val preferences: PreferencesStore,
    private val zone: TimeZone,
    private val ids: IdGenerator,
    private val clock: ClockSeam,
    private val pdf: PdfSeam,
    private val appVersion: String,
) {
    private val today: LocalDate get() = clock.now().toLocalDateTime(zone).date
    private val manual = SourceId("manual")

    // ------------------------------------------------------------------- the hub

    fun clinicalModel(): ClinicalModel {
        val medications = store.clinical.medications()
        val episodes = store.clinical.allEpisodes()
        val labs = store.clinical.allLabs()
        val allergies = store.clinical.allergies()
        val conditions = store.clinical.conditions()
        val vaccinations = store.clinical.vaccinations()
        val sections = listOf(
            ClinicalSectionModel(
                kind = ClinicalSectionKind.MEDICATIONS,
                title = context.getString(R.string.clinical_medications),
                addLabel = context.getString(R.string.clinical_add),
                emptyBody = context.getString(R.string.clinical_medications_empty),
                rows = medications.map { medication ->
                    val todayDoses = store.clinical.dosesBetween(today, today)
                    val adherence = Adherence.compute(medication, todayDoses, today, today)
                    ClinicalRowModel(
                        id = medication.id,
                        kind = ClinicalKind.MEDICATION,
                        title = medication.name,
                        subtitle = scheduleText(medication),
                        value = when {
                            !medication.active -> context.getString(R.string.clinical_stopped)
                            medication.schedule.asNeeded ->
                                context.getString(R.string.clinical_as_needed_short)
                            adherence.expected == 0 ->
                                context.getString(R.string.clinical_not_scheduled_short)
                            else -> context.getString(
                                R.string.clinical_doses_today,
                                adherence.taken,
                                adherence.expected,
                            )
                        },
                        deletable = false,
                    )
                },
            ),
            ClinicalSectionModel(
                kind = ClinicalSectionKind.SYMPTOMS,
                title = context.getString(R.string.clinical_symptoms),
                addLabel = context.getString(R.string.clinical_add),
                emptyBody = context.getString(R.string.clinical_symptoms_empty),
                rows = episodes.take(30).map { episode ->
                    ClinicalRowModel(
                        id = episode.id,
                        kind = ClinicalKind.EPISODE,
                        title = episode.symptomName,
                        subtitle = episode.localDate.toString(),
                        value = context.getString(
                            R.string.clinical_severity_of_ten,
                            episode.severity,
                        ),
                        deletable = true,
                    )
                },
            ),
            ClinicalSectionModel(
                kind = ClinicalSectionKind.LABS,
                title = context.getString(R.string.clinical_labs),
                addLabel = context.getString(R.string.clinical_add),
                emptyBody = context.getString(R.string.clinical_labs_empty),
                rows = labs.take(30).map { lab ->
                    ClinicalRowModel(
                        id = lab.id,
                        kind = ClinicalKind.LAB,
                        title = lab.analyte.display,
                        subtitle = listOfNotNull(
                            lab.labName,
                            lab.provenance.localDate.toString(),
                        ).joinToString(" · "),
                        value = "${number(lab.value, lab.analyte.decimals)} ${lab.unit.ucum}",
                        deletable = true,
                    )
                },
            ),
            ClinicalSectionModel(
                kind = ClinicalSectionKind.ALLERGIES,
                title = context.getString(R.string.clinical_allergies),
                addLabel = context.getString(R.string.clinical_add),
                emptyBody = context.getString(R.string.clinical_allergies_empty),
                rows = allergies.map { allergy ->
                    ClinicalRowModel(
                        id = allergy.id,
                        kind = ClinicalKind.ALLERGY,
                        title = allergy.substance,
                        subtitle = allergy.reaction.orEmpty(),
                        value = allergy.severity.name.lowercase()
                            .replaceFirstChar { it.uppercase() },
                        deletable = true,
                    )
                },
            ),
            ClinicalSectionModel(
                kind = ClinicalSectionKind.CONDITIONS,
                title = context.getString(R.string.clinical_conditions),
                addLabel = context.getString(R.string.clinical_add),
                emptyBody = context.getString(R.string.clinical_conditions_empty),
                rows = conditions.map { condition ->
                    ClinicalRowModel(
                        id = condition.id,
                        kind = ClinicalKind.CONDITION,
                        title = condition.name,
                        subtitle = condition.onsetOn?.toString().orEmpty(),
                        value = if (condition.resolvedOn == null) {
                            context.getString(R.string.clinical_active)
                        } else {
                            context.getString(R.string.clinical_resolved)
                        },
                        deletable = true,
                    )
                },
            ),
            ClinicalSectionModel(
                kind = ClinicalSectionKind.VACCINATIONS,
                title = context.getString(R.string.clinical_vaccinations),
                addLabel = context.getString(R.string.clinical_add),
                emptyBody = context.getString(R.string.clinical_vaccinations_empty),
                rows = vaccinations.map { vaccination ->
                    ClinicalRowModel(
                        id = vaccination.id,
                        kind = ClinicalKind.VACCINATION,
                        title = vaccination.name,
                        subtitle = vaccination.doseLabel.orEmpty(),
                        value = vaccination.administeredOn.toString(),
                        deletable = true,
                    )
                },
            ),
        )
        return ClinicalModel(
            sections = sections,
            countLabel = context.getString(
                R.string.clinical_count,
                medications.size,
                labs.size,
                episodes.size,
            ),
            note = context.getString(R.string.clinical_note),
        )
    }

    // ----------------------------------------------------------------- editors

    fun editor(section: ClinicalSectionKind, id: String?): ClinicalEditorModel = when (section) {
        ClinicalSectionKind.MEDICATIONS -> medicationEditor(id)
        ClinicalSectionKind.SYMPTOMS -> episodeEditor(id)
        ClinicalSectionKind.LABS -> labEditor(id)
        ClinicalSectionKind.ALLERGIES -> allergyEditor(id)
        ClinicalSectionKind.CONDITIONS -> conditionEditor(id)
        ClinicalSectionKind.VACCINATIONS -> vaccinationEditor(id)
    }

    private fun medicationEditor(id: String?): ClinicalEditorModel {
        val med = id?.let(store.clinical::medication)
        return ClinicalEditorModel(
            kind = ClinicalKind.MEDICATION,
            title = if (med == null) {
                context.getString(R.string.clinical_medication_new)
            } else {
                context.getString(R.string.clinical_medication_edit)
            },
            fields = listOf(
                field("name", R.string.clinical_field_name, med?.name, required = true),
                field(
                    "dose",
                    R.string.clinical_field_dose,
                    med?.doseAmount?.let(::trim),
                    ClinicalFieldType.DECIMAL,
                    required = true,
                ),
                choice(
                    "unit",
                    R.string.clinical_field_unit,
                    UNIT_LABELS.keys.toList(),
                    med?.doseUnit?.ucum ?: "mg",
                ),
                choice(
                    "form",
                    R.string.clinical_field_form,
                    MedicationForm.entries.map { it.label() },
                    med?.form?.label() ?: MedicationForm.TABLET.label(),
                ),
                field(
                    "times",
                    R.string.clinical_field_times,
                    med?.schedule?.times?.joinToString(", "),
                    hint = "08:00, 20:00",
                ),
                ClinicalFieldModel(
                    key = "days",
                    label = context.getString(R.string.clinical_field_days),
                    type = ClinicalFieldType.CHOICE_MULTI,
                    options = DAY_LABELS,
                    selected = med?.schedule?.daysOfWeek
                        ?.mapNotNull { DAY_LABELS.getOrNull(it - 1) }
                        .orEmpty(),
                    hint = context.getString(R.string.clinical_field_days_hint),
                ),
                choice(
                    "as_needed",
                    R.string.clinical_field_as_needed,
                    listOf(
                        context.getString(R.string.clinical_scheduled),
                        context.getString(R.string.clinical_as_needed),
                    ),
                    if (med?.schedule?.asNeeded == true) {
                        context.getString(R.string.clinical_as_needed)
                    } else {
                        context.getString(R.string.clinical_scheduled)
                    },
                ),
                choice(
                    "active",
                    R.string.clinical_field_active,
                    listOf(
                        context.getString(R.string.clinical_active),
                        context.getString(R.string.clinical_stopped),
                    ),
                    if (med?.active != false) {
                        context.getString(R.string.clinical_active)
                    } else {
                        context.getString(R.string.clinical_stopped)
                    },
                ),
                field(
                    "started",
                    R.string.clinical_field_started,
                    med?.startedOn?.toString(),
                    hint = "2026-11-26",
                ),
                field("notes", R.string.clinical_field_notes, med?.notes),
            ),
            saveLabel = context.getString(R.string.clinical_save),
            deleteLabel = null,
            note = context.getString(R.string.clinical_medication_note),
            canLogDose = med != null,
        )
    }

    private fun episodeEditor(id: String?): ClinicalEditorModel {
        val episode = id?.let(store.clinical::episode)
        return ClinicalEditorModel(
            kind = ClinicalKind.EPISODE,
            title = if (episode == null) {
                context.getString(R.string.clinical_symptom_new)
            } else {
                context.getString(R.string.clinical_symptom_edit)
            },
            fields = listOf(
                field(
                    "symptom",
                    R.string.clinical_field_symptom,
                    episode?.symptomName,
                    required = true,
                ),
                field(
                    "severity",
                    R.string.clinical_field_severity,
                    episode?.severity?.toString(),
                    ClinicalFieldType.INTEGER,
                    required = true,
                ),
                field(
                    "date",
                    R.string.clinical_field_date,
                    (episode?.localDate ?: today).toString(),
                    hint = "2026-11-26",
                    required = true,
                ),
                field(
                    "duration",
                    R.string.clinical_field_duration,
                    episode?.durationMinutes?.let { trim(it) },
                    ClinicalFieldType.INTEGER,
                    hint = context.getString(R.string.clinical_field_duration_hint),
                ),
                field("triggers", R.string.clinical_field_triggers, episode?.triggers),
                field("notes", R.string.clinical_field_notes, episode?.notes),
            ),
            saveLabel = context.getString(R.string.clinical_save),
            deleteLabel = if (episode !=
                null
            ) {
                context.getString(R.string.clinical_delete)
            } else {
                null
            },
            note = context.getString(R.string.clinical_symptom_note),
        )
    }

    private fun labEditor(id: String?): ClinicalEditorModel {
        val lab = id?.let(store.clinical::lab)
        return ClinicalEditorModel(
            kind = ClinicalKind.LAB,
            title = if (lab == null) {
                context.getString(R.string.clinical_lab_new)
            } else {
                context.getString(R.string.clinical_lab_edit)
            },
            fields = listOf(
                choice(
                    "analyte",
                    R.string.clinical_field_analyte,
                    LabAnalyte.entries.map { it.display },
                    lab?.analyte?.display ?: LabAnalyte.HBA1C.display,
                ),
                field(
                    "value",
                    R.string.clinical_field_result,
                    lab?.value?.let { number(it, lab.analyte.decimals) },
                    ClinicalFieldType.DECIMAL,
                    required = true,
                ),
                field(
                    "date",
                    R.string.clinical_field_collected,
                    (lab?.provenance?.localDate ?: today).toString(),
                    hint = "2026-11-26",
                ),
                field("lab", R.string.clinical_field_lab, lab?.labName),
                field(
                    "low",
                    R.string.clinical_field_reference_low,
                    lab?.referenceLow?.let(::trim),
                    ClinicalFieldType.DECIMAL,
                    hint = context.getString(R.string.clinical_field_reference_hint),
                ),
                field(
                    "high",
                    R.string.clinical_field_reference_high,
                    lab?.referenceHigh?.let(::trim),
                    ClinicalFieldType.DECIMAL,
                ),
                field("note", R.string.clinical_field_reference_note, lab?.referenceNote),
            ),
            saveLabel = context.getString(R.string.clinical_save),
            deleteLabel = if (lab != null) context.getString(R.string.clinical_delete) else null,
            note = context.getString(R.string.clinical_lab_note),
        )
    }

    private fun allergyEditor(id: String?): ClinicalEditorModel {
        val allergy = id?.let(store.clinical::allergy)
        return ClinicalEditorModel(
            kind = ClinicalKind.ALLERGY,
            title = if (allergy == null) {
                context.getString(R.string.clinical_allergy_new)
            } else {
                context.getString(R.string.clinical_allergy_edit)
            },
            fields = listOf(
                field(
                    "substance",
                    R.string.clinical_field_substance,
                    allergy?.substance,
                    required = true,
                ),
                field("reaction", R.string.clinical_field_reaction, allergy?.reaction),
                choice(
                    "severity",
                    R.string.clinical_field_severity,
                    AllergySeverity.entries.map { it.label() },
                    (allergy?.severity ?: AllergySeverity.MILD).label(),
                ),
                choice(
                    "active",
                    R.string.clinical_field_active,
                    listOf(
                        context.getString(R.string.clinical_yes),
                        context.getString(R.string.clinical_no),
                    ),
                    if (allergy?.active != false) {
                        context.getString(R.string.clinical_yes)
                    } else {
                        context.getString(R.string.clinical_no)
                    },
                ),
                field("noted", R.string.clinical_field_noted, allergy?.notedOn?.toString()),
                field("notes", R.string.clinical_field_notes, allergy?.notes),
            ),
            saveLabel = context.getString(R.string.clinical_save),
            deleteLabel = if (allergy != null) {
                context.getString(R.string.clinical_delete)
            } else {
                null
            },
        )
    }

    private fun conditionEditor(id: String?): ClinicalEditorModel {
        val condition = id?.let(store.clinical::condition)
        return ClinicalEditorModel(
            kind = ClinicalKind.CONDITION,
            title = if (condition == null) {
                context.getString(R.string.clinical_condition_new)
            } else {
                context.getString(R.string.clinical_condition_edit)
            },
            fields = listOf(
                field(
                    "name",
                    R.string.clinical_field_condition,
                    condition?.name,
                    required = true,
                ),
                field("onset", R.string.clinical_field_onset, condition?.onsetOn?.toString()),
                field(
                    "resolved",
                    R.string.clinical_field_resolved,
                    condition?.resolvedOn?.toString(),
                ),
                field("notes", R.string.clinical_field_notes, condition?.notes),
            ),
            saveLabel = context.getString(R.string.clinical_save),
            deleteLabel = if (condition != null) {
                context.getString(R.string.clinical_delete)
            } else {
                null
            },
        )
    }

    private fun vaccinationEditor(id: String?): ClinicalEditorModel {
        val vaccination = id?.let(store.clinical::vaccination)
        return ClinicalEditorModel(
            kind = ClinicalKind.VACCINATION,
            title = if (vaccination == null) {
                context.getString(R.string.clinical_vaccination_new)
            } else {
                context.getString(R.string.clinical_vaccination_edit)
            },
            fields = listOf(
                field(
                    "name",
                    R.string.clinical_field_vaccine,
                    vaccination?.name,
                    required = true,
                ),
                field("dose", R.string.clinical_field_dose_label, vaccination?.doseLabel),
                field(
                    "date",
                    R.string.clinical_field_administered,
                    (vaccination?.administeredOn ?: today).toString(),
                    hint = "2026-11-26",
                ),
                field("notes", R.string.clinical_field_notes, vaccination?.notes),
            ),
            saveLabel = context.getString(R.string.clinical_save),
            deleteLabel = if (vaccination != null) {
                context.getString(R.string.clinical_delete)
            } else {
                null
            },
        )
    }

    /** Saves one editor. Returns false when a required value is missing or malformed. */
    @Suppress("ReturnCount", "CyclomaticComplexMethod")
    fun save(section: ClinicalSectionKind, id: String?, draft: Map<String, String>): Boolean {
        val existing = id
        return when (section) {
            ClinicalSectionKind.MEDICATIONS -> saveMedication(existing, draft)
            ClinicalSectionKind.SYMPTOMS -> saveEpisode(existing, draft)
            ClinicalSectionKind.LABS -> saveLab(existing, draft)
            ClinicalSectionKind.ALLERGIES -> saveAllergy(existing, draft)
            ClinicalSectionKind.CONDITIONS -> saveCondition(existing, draft)
            ClinicalSectionKind.VACCINATIONS -> saveVaccination(existing, draft)
        }
    }

    private fun saveMedication(id: String?, draft: Map<String, String>): Boolean {
        val name = draft["name"].orEmpty().trim()
        val dose = draft["dose"]?.toDoubleOrNull() ?: return false
        if (name.isEmpty() || dose <= 0.0) return false
        val rawTimes = draft["times"].orEmpty()
        val times = rawTimes
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (times.any { !it.matches(Regex("^([01]\\d|2[0-3]):[0-5]\\d$")) }) return false
        val days = draft["days"].orEmpty()
            .split(',')
            .mapNotNull { DAY_LABELS.indexOf(it).takeIf { index -> index >= 0 }?.plus(1) }
        val unit = UNIT_LABELS[draft["unit"]] ?: UcumUnit.MILLIGRAM
        val form = MedicationForm.entries.firstOrNull { it.label() == draft["form"] }
            ?: MedicationForm.TABLET
        val existing = id?.let(store.clinical::medication)
        val medication = Medication(
            id = existing?.id ?: ids.newId(),
            name = name,
            doseAmount = dose,
            doseUnit = unit,
            form = form,
            schedule = MedicationSchedule(
                times = times,
                daysOfWeek = days,
                asNeeded = draft["as_needed"] == context.getString(R.string.clinical_as_needed),
            ),
            active = draft["active"] != context.getString(R.string.clinical_stopped),
            startedOn = draft["started"]?.let(::parseDate),
            endedOn = existing?.endedOn,
            notes = draft["notes"]?.takeIf { it.isNotBlank() },
            provenance = existing?.provenance ?: provenance(atNoon(today)),
            revision = (existing?.revision ?: 0) + 1,
        )
        store.clinical.saveMedication(medication)
        existing?.let { MedicationReminders.cancelAll(context, listOf(it)) }
        if (medication.active) MedicationReminders.schedule(context, medication)
        return true
    }

    private fun saveEpisode(id: String?, draft: Map<String, String>): Boolean {
        val name = draft["symptom"].orEmpty().trim()
        val severity = draft["severity"]?.toIntOrNull() ?: return false
        if (name.isEmpty() || severity !in 1..10) return false
        val date = draft["date"]?.let(::parseDate) ?: today
        val existing = id?.let(store.clinical::episode)
        val symptom = store.clinical.symptoms()
            .firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: Symptom(
                id = ids.newId(),
                name = name,
                notes = null,
                provenance = provenance(atNoon(date)),
            ).also(store.clinical::saveSymptom)
        val started = atNoon(date)
        val duration = draft["duration"]?.toDoubleOrNull()?.takeIf { it > 0 }
        store.clinical.saveEpisode(
            SymptomEpisode(
                id = existing?.id ?: ids.newId(),
                symptomId = symptom.id,
                symptomName = symptom.name,
                severity = severity,
                startedAt = existing?.startedAt ?: started,
                endedAt = duration?.let {
                    started.plus((it.toLong()).minutes)
                } ?: existing?.endedAt,
                triggers = draft["triggers"]?.takeIf { it.isNotBlank() },
                notes = draft["notes"]?.takeIf { it.isNotBlank() },
                localDate = date,
                provenance = existing?.provenance ?: provenance(started),
                revision = (existing?.revision ?: 0) + 1,
            ),
        )
        return true
    }

    private fun saveLab(id: String?, draft: Map<String, String>): Boolean {
        val analyte = LabAnalyte.entries.firstOrNull { it.display == draft["analyte"] }
            ?: return false
        val value = draft["value"]?.toDoubleOrNull() ?: return false
        if (value < 0.0) return false
        val existing = id?.let(store.clinical::lab)
        val date = draft["date"]?.let(::parseDate) ?: existing?.provenance?.localDate ?: today
        val low = draft["low"]?.toDoubleOrNull()
        val high = draft["high"]?.toDoubleOrNull()
        if (low != null && high != null && low > high) return false
        val hasEnteredRange = low != null || high != null
        store.clinical.saveLab(
            LabResult(
                id = existing?.id ?: ids.newId(),
                analyte = analyte,
                value = value,
                unit = analyte.unit,
                referenceLow = low,
                referenceHigh = high,
                referenceNote = draft["note"]?.takeIf { it.isNotBlank() },
                referenceCitation = if (hasEnteredRange) {
                    CitationKey.LAB_REPORTED_RANGE
                } else {
                    analyte.defaultCitation
                },
                collectedAt = existing?.collectedAt ?: atNoon(date),
                labName = draft["lab"]?.takeIf { it.isNotBlank() },
                notes = existing?.notes,
                provenance = existing?.provenance ?: provenance(atNoon(date)),
                revision = (existing?.revision ?: 0) + 1,
            ),
        )
        return true
    }

    private fun saveAllergy(id: String?, draft: Map<String, String>): Boolean {
        val substance = draft["substance"].orEmpty().trim()
        if (substance.isEmpty()) return false
        val existing = id?.let(store.clinical::allergy)
        val severity = AllergySeverity.entries.firstOrNull { it.label() == draft["severity"] }
            ?: AllergySeverity.MILD
        store.clinical.saveAllergy(
            Allergy(
                id = existing?.id ?: ids.newId(),
                substance = substance,
                reaction = draft["reaction"]?.takeIf { it.isNotBlank() },
                severity = severity,
                active = draft["active"] == context.getString(R.string.clinical_active),
                notedOn = draft["noted"]?.let(::parseDate) ?: existing?.notedOn,
                notes = draft["notes"]?.takeIf { it.isNotBlank() },
                provenance = revised(
                    existing?.provenance,
                    atNoon(draft["noted"]?.let(::parseDate) ?: today),
                ),
                revision = (existing?.revision ?: 0) + 1,
            ),
        )
        return true
    }

    private fun saveCondition(id: String?, draft: Map<String, String>): Boolean {
        val name = draft["name"].orEmpty().trim()
        if (name.isEmpty()) return false
        val existing = id?.let(store.clinical::condition)
        store.clinical.saveCondition(
            Condition(
                id = existing?.id ?: ids.newId(),
                name = name,
                code = existing?.code,
                codeSystem = existing?.codeSystem,
                onsetOn = draft["onset"]?.let(::parseDate) ?: existing?.onsetOn,
                resolvedOn = draft["resolved"]?.let(::parseDate) ?: existing?.resolvedOn,
                notes = draft["notes"]?.takeIf { it.isNotBlank() },
                provenance = revised(
                    existing?.provenance,
                    atNoon(draft["onset"]?.let(::parseDate) ?: today),
                ),
                revision = (existing?.revision ?: 0) + 1,
            ),
        )
        return true
    }

    private fun saveVaccination(id: String?, draft: Map<String, String>): Boolean {
        val name = draft["name"].orEmpty().trim()
        if (name.isEmpty()) return false
        val existing = id?.let(store.clinical::vaccination)
        val date = draft["date"]?.let(::parseDate) ?: existing?.administeredOn ?: today
        store.clinical.saveVaccination(
            Vaccination(
                id = existing?.id ?: ids.newId(),
                name = name,
                doseLabel = draft["dose"]?.takeIf { it.isNotBlank() },
                administeredOn = date,
                notes = draft["notes"]?.takeIf { it.isNotBlank() },
                provenance = revised(existing?.provenance, atNoon(date)),
                revision = (existing?.revision ?: 0) + 1,
            ),
        )
        return true
    }

    /** Deletes through Trash and returns the tombstone, so the shell can offer undo. */
    fun delete(section: ClinicalSectionKind, id: String): app.openhealth.core.ids.TombstoneId? =
        when (section) {
            ClinicalSectionKind.MEDICATIONS -> null
            ClinicalSectionKind.SYMPTOMS -> store.trash.deleteSymptomEpisode(id)
            ClinicalSectionKind.LABS -> store.trash.deleteLabResult(id)
            ClinicalSectionKind.ALLERGIES -> store.trash.deleteAllergy(id)
            ClinicalSectionKind.CONDITIONS -> store.trash.deleteCondition(id)
            ClinicalSectionKind.VACCINATIONS -> store.trash.deleteVaccination(id)
        }

    /** Logs a dose now (taken or skipped) from the medication editor. */
    fun logDose(medicationId: String, skipped: Boolean) {
        val medication = store.clinical.medication(medicationId) ?: return
        val now = clock.now()
        val date = now.toLocalDateTime(zone).date
        store.clinical.saveDose(
            MedicationDose(
                id = ids.newId(),
                medicationId = medication.id,
                medicationName = medication.name,
                scheduledFor = now,
                takenAt = if (skipped) null else now,
                skipped = skipped,
                localDate = date,
                notes = null,
                provenance = provenance(now, appVersion),
            ),
        )
    }

    // -------------------------------------------------------------------- sleep

    fun sleepModel(): SleepModel {
        val nights = sleepNights(28)
        val need = sleepNeed()
        val debt = (
            SleepDebt.compute(
                nights.map { SleepNight(it.localDate, it.totals.asleepMinutes) },
                need,
            ) as? FormulaOutcome.Computed
            )?.value
        val timings = nights.map { night ->
            val window = sleepWindow(night)
            SleepTiming(night.localDate, window.first, window.second)
        }
        val consistency = SleepConsistency.compute(timings, zone)
        val latest = nights.lastOrNull()
        val stages = latest?.let { SleepStageBreakdown.of(it.totals) }
        val stageRows = stages?.let(::stageRows).orEmpty()
        val average = nights.map { it.totals.asleepMinutes }.average()
        val rows = nights.sortedByDescending { it.localDate }.map { night ->
            val window = sleepWindow(night)
            SleepNightRow(
                date = night.localDate.toString(),
                asleep = duration(night.totals.asleepMinutes),
                bedtime = window.first?.toLocalDateTime(zone)?.time?.text().orEmpty(),
                wake = window.second?.toLocalDateTime(zone)?.time?.text().orEmpty(),
                quality = qualityForNight(night)?.toString().orEmpty(),
            )
        }
        val accessible = if (nights.isEmpty()) {
            context.getString(R.string.clinical_sleep_empty_body)
        } else {
            context.getString(
                R.string.clinical_sleep_accessible,
                nights.size,
                duration(average),
                consistency.bedtimeSdMinutes?.let(::duration) ?: dash(),
            )
        }
        return SleepModel(
            title = context.getString(R.string.clinical_sleep_title),
            averageLabel = context.getString(R.string.clinical_sleep_average),
            averageValue = duration(average),
            debtLabel = context.getString(R.string.clinical_sleep_debt),
            debtValue = debt?.let { duration(it.debtMinutes) } ?: dash(),
            debtNote = context.getString(
                R.string.clinical_sleep_debt_note,
                duration(need),
                Citations.of(CitationKey.SLEEP_CONSENSUS_2015).title,
            ),
            consistencyBedLabel = context.getString(R.string.clinical_sleep_bedtime_sd),
            consistencyBedValue = consistency.bedtimeSdMinutes?.let(::duration) ?: dash(),
            consistencyWakeLabel = context.getString(R.string.clinical_sleep_wake_sd),
            consistencyWakeValue = consistency.wakeSdMinutes?.let(::duration) ?: dash(),
            consistencyNote = context.getString(R.string.clinical_sleep_consistency_note),
            nights = rows,
            stages = stageRows,
            stagesNote = context.getString(R.string.clinical_sleep_stages_note),
            accessible = accessible,
            emptyTitle = context.getString(R.string.clinical_sleep_empty_title),
            emptyBody = context.getString(R.string.clinical_sleep_empty_body),
            qualityLabel = context.getString(R.string.clinical_sleep_quality_label),
            addQualityLabel = context.getString(R.string.clinical_sleep_add_quality),
            reminder = context.getString(R.string.clinical_sleep_note),
        )
    }

    fun logSleepQuality(rating: Int) {
        if (rating !in 1..5) return
        val now = clock.now()
        store.observations.record(
            Observation.of(
                id = ObservationId(ids.newId()),
                type = ObservationType.SLEEP_QUALITY,
                value = Measure(rating.toDouble(), UcumUnit.SCORE),
                start = now,
                provenance = provenance(now),
            ),
        )
    }

    private fun sleepNights(count: Int): List<app.openhealth.core.formula.SleepSession> {
        val to = today.plus(DatePeriod(days = 1)).atStartOfDayIn(zone)
        val from = today.minus(DatePeriod(days = count + 3)).atStartOfDayIn(zone)
        val samples = SLEEP_TYPES.flatMap { type ->
            store.observations.forTypeBetween(type, from, to).map { observation ->
                SleepSample(
                    groupKey = observation.groupKey,
                    type = observation.type,
                    seconds = observation.value.value,
                    localDate = observation.localDate,
                    sourceId = observation.provenance.sourceId.value,
                )
            }
        }
        val registry = store.sources.registry()
        return SleepSessions.collect(samples) { sourceId ->
            registry.priorityOf(SourceId(sourceId), ObservationType.SLEEP_DURATION)
        }.filter {
            it.localDate >= today.minus(DatePeriod(days = count - 1)) &&
                it.localDate <= today
        }
    }

    private fun sleepWindow(
        night: app.openhealth.core.formula.SleepSession,
    ): Pair<Instant?, Instant?> {
        val from = night.localDate.atStartOfDayIn(zone).plus((-12).hours)
        val to = night.localDate.atStartOfDayIn(zone).plus(36.hours)
        val samples = listOf(ObservationType.SLEEP_IN_BED, ObservationType.SLEEP_DURATION)
            .flatMap { store.observations.forTypeBetween(it, from, to) }
            .filter { observation ->
                observation.localDate == night.localDate &&
                    (
                        night.sourceId == null ||
                            observation.provenance.sourceId.value == night.sourceId
                        )
            }
        if (samples.isEmpty()) return null to null
        val start = samples.minOf { it.start }
        val rawEnd = samples.mapNotNull { it.end }.maxOrNull()
        // An end beyond a day is not a night; fall back to the recorded span rather than
        // print an impossible window. A source that reports only a total still has one.
        val span = night.totals.spanSeconds ?: night.totals.asleepSeconds
        val end = rawEnd?.takeIf { it > start && it - start <= 24.hours }
            ?: start.plus(span.toLong().seconds)
        return start to end
    }

    /**
     * A rating is usually given the next morning, so it belongs to the night whose window
     * it follows, not to the calendar day the session started on.
     */
    private fun qualityForNight(night: app.openhealth.core.formula.SleepSession): Int? {
        val window = sleepWindow(night)
        val start = window.first ?: night.localDate.atStartOfDayIn(zone)
        val end = window.second ?: start.plus(36.hours)
        return store.observations
            .forTypeBetween(ObservationType.SLEEP_QUALITY, start, end.plus(12.hours))
            .maxByOrNull { it.start }
            ?.value?.value?.toInt()
    }

    private fun stageRows(stages: SleepStageBreakdown): List<SleepStageRow> {
        val rows = listOf(
            Triple(context.getString(R.string.clinical_sleep_deep), stages.deepMinutes, stages),
            Triple(context.getString(R.string.clinical_sleep_light), stages.lightMinutes, stages),
            Triple(context.getString(R.string.clinical_sleep_rem), stages.remMinutes, stages),
            Triple(context.getString(R.string.clinical_sleep_awake), stages.awakeMinutes, stages),
        )
        return rows.mapNotNull { (label, minutes, all) ->
            minutes?.let {
                SleepStageRow(
                    label = label,
                    duration = duration(it),
                    share = all.share(it)?.let { share -> "${number(share, 0)} %" }.orEmpty(),
                )
            }
        }
    }

    private fun sleepNeed(): Double =
        store.settings.get(SettingKeys.SLEEP_NEED_MINUTES)?.toDoubleOrNull()
            ?.takeIf { it >= 60.0 } ?: DEFAULT_SLEEP_NEED

    // ------------------------------------------------------------------ reports

    fun reportsModel(days: Int): ReportsModel {
        val data = reportData(days)
        val report = ClinicalReportBuilder.build(data)
        val text = report.toPlainText().lines()
        val limit = PREVIEW_LINES
        return ReportsModel(
            title = context.getString(R.string.clinical_reports_title),
            ranges = RANGES.map { ReportRangeModel(it, rangeLabel(it)) },
            selectedRange = RANGES.indexOf(days).coerceAtLeast(0),
            rangeNote = context.getString(R.string.clinical_report_range_note),
            generatedLabel = context.getString(
                R.string.clinical_report_generated,
                report.generatedAt.toLocalDateTime(zone).date.toString(),
            ),
            previewTitle = report.title,
            previewLines = text.take(limit),
            previewTruncated = text.size > limit,
            disclaimer = report.disclaimer,
            sourceNote = report.sourceNote,
            exports = ReportFormat.entries,
            empty = report.sections.isEmpty(),
            emptyBody = context.getString(R.string.clinical_report_empty),
        )
    }

    /** Builds one export on the device. Nothing here opens a socket. */
    fun export(format: ReportFormat, days: Int): ReportFile {
        val data = reportData(days)
        val report = ClinicalReportBuilder.build(data)
        val stamp = today.toString()
        return when (format) {
            ReportFormat.PDF -> ReportFile(
                bytes = pdf.render(report.toPdfSpec()),
                mime = "application/pdf",
                name = "openHealth-report-$stamp.pdf",
            )
            ReportFormat.CSV -> ReportFile(
                bytes = report.toCsv().toByteArray(Charsets.UTF_8),
                mime = "text/csv",
                name = "openHealth-report-$stamp.csv",
            )
            ReportFormat.FHIR -> ReportFile(
                bytes = FhirR4.export(
                    FhirExportData(
                        patientId = "self",
                        generatedAt = clock.now(),
                        vitals = data.observations,
                        labs = data.labs,
                        medications = data.medications,
                        doses = data.doses,
                        conditions = data.conditions,
                        allergies = data.allergies,
                        vaccinations = data.vaccinations,
                        includeWeight = !data.weightNeutral,
                    ),
                ).toByteArray(Charsets.UTF_8),
                mime = "application/fhir+json",
                name = "openHealth-fhir-$stamp.json",
            )
            ReportFormat.MHEALTH -> ReportFile(
                bytes = OpenMhealth.export(
                    data.observations,
                    clock.now(),
                    includeWeight = !data.weightNeutral,
                ).toByteArray(Charsets.UTF_8),
                mime = "application/json",
                name = "openHealth-omh-$stamp.json",
            )
        }
    }

    private fun reportData(days: Int): ClinicalReportData {
        val to = today
        val from = to.minus(DatePeriod(days = days - 1))
        val fromInstant = from.atStartOfDayIn(zone)
        val toInstant = to.plus(DatePeriod(days = 1)).atStartOfDayIn(zone)
        val observations = ObservationType.entries.flatMap { type ->
            store.observations.forTypeBetween(type, fromInstant, toInstant)
        }
        val nights = sleepNights(days)
        val activities = store.activities.between(from, to).map { activity ->
            ReportActivity(
                localDate = activity.localDate,
                type = activity.type.name,
                minutes = activity.durationSeconds / 60.0,
                distanceKm = activity.distanceM / 1000.0,
            )
        }
        val labels = store.sources.all().associate { it.id.value to it.label }
        val rollups = store.rollups.forRange(from, to)
        val stepsByDay = rollups
            .filter { it.type == ObservationType.STEPS }
            .associate { it.localDate to it.value.value }
        val weightByDay = rollups
            .filter { it.type == ObservationType.WEIGHT }
            .associate { it.localDate to it.value.value }
        return ClinicalReportData(
            patientLabel = store.profile.active()?.name ?: "openHealth",
            from = from,
            to = to,
            generatedAt = clock.now(),
            zone = zone,
            observations = observations,
            labs = store.clinical.labsBetween(from, to),
            medications = store.clinical.medications(),
            doses = store.clinical.dosesBetween(from, to),
            symptoms = store.clinical.episodesBetween(from, to),
            allergies = store.clinical.allergies(),
            conditions = store.clinical.conditions(),
            vaccinations = store.clinical.vaccinations(),
            sleep = nights.map { night ->
                val window = sleepWindow(night)
                ReportSleepNight(
                    date = night.localDate,
                    asleepMinutes = night.totals.asleepMinutes,
                    bedtime = window.first,
                    wake = window.second,
                    quality = qualityForNight(night),
                )
            },
            activities = activities,
            stepsByDay = stepsByDay,
            weightByDay = weightByDay,
            sleepNeedMinutes = sleepNeed(),
            weightNeutral = preferences.snapshot().weightNeutral,
            sourceLabels = labels,
        )
    }

    // ------------------------------------------------------------------- weekly

    fun weeklyReview(): WeeklyReviewModel {
        val to = today
        val from = to.minus(DatePeriod(days = 6))
        val items = mutableListOf<WeeklyReviewItemModel>()
        val nights = sleepNights(7)
        if (nights.isNotEmpty()) {
            val average = nights.map { it.totals.asleepMinutes }.average()
            val debt = (
                SleepDebt.compute(
                    nights.map { SleepNight(it.localDate, it.totals.asleepMinutes) },
                    sleepNeed(),
                ) as? FormulaOutcome.Computed
                )?.value
            items += WeeklyReviewItemModel(
                title = context.getString(R.string.clinical_sleep_title),
                value = duration(average),
                note = context.getString(
                    R.string.clinical_weekly_sleep_note,
                    nights.size,
                    debt?.let { duration(it.debtMinutes) } ?: dash(),
                ),
                citation = Citations.of(CitationKey.SLEEP_CONSENSUS_2015).title,
            )
        }
        val activities = store.activities.between(from, to)
        if (activities.isNotEmpty()) {
            items += WeeklyReviewItemModel(
                title = context.getString(R.string.clinical_weekly_training),
                value = context.getString(R.string.clinical_weekly_sessions, activities.size),
                note = duration(activities.sumOf { it.durationSeconds } / 60.0),
            )
        }
        val steps = (0..6).mapNotNull { offset ->
            val day = to.minus(DatePeriod(days = offset))
            store.rollups.forDayAndType(day, ObservationType.STEPS)?.value?.value
        }
        if (steps.isNotEmpty()) {
            items += WeeklyReviewItemModel(
                title = context.getString(R.string.clinical_weekly_steps),
                value = number(steps.average(), 0),
                note = context.getString(R.string.clinical_weekly_steps_note, steps.size),
            )
        }
        val policy = preferences.snapshot()
        if (policy.weightNeutral.not() &&
            HealthModule.BODY in policy.modules
        ) {
            val weights = store.rollups
                .forRange(from, to)
                .filter { it.type == ObservationType.WEIGHT }
                .sortedBy { it.localDate }
            if (weights.size >= 2) {
                val change = weights.last().value.value - weights.first().value.value
                items += WeeklyReviewItemModel(
                    title = context.getString(R.string.clinical_weekly_weight),
                    value = "${if (change >= 0) "+" else ""}${number(change, 1)} kg",
                    note = context.getString(R.string.clinical_weekly_weight_note),
                )
            }
        }
        val medications = store.clinical.activeMedications()
        if (medications.isNotEmpty()) {
            val doses = store.clinical.dosesBetween(from, to)
            val expected = medications.sumOf { Adherence.expectedDoses(it, from, to) }
            val taken = doses.count { !it.skipped }
            if (expected > 0) {
                items += WeeklyReviewItemModel(
                    title = context.getString(R.string.clinical_weekly_medication),
                    value = context.getString(
                        R.string.clinical_weekly_doses,
                        taken,
                        expected,
                    ),
                    note = context.getString(R.string.clinical_weekly_medication_note),
                )
            }
        }
        return WeeklyReviewModel(
            title = context.getString(R.string.clinical_weekly_title),
            weekLabel = context.getString(
                R.string.clinical_weekly_range,
                from.toString(),
                to.toString(),
            ),
            items = items,
            accessible = if (items.isEmpty()) {
                context.getString(R.string.clinical_weekly_empty_body)
            } else {
                context.getString(
                    R.string.clinical_weekly_accessible,
                    items.size,
                    items.first().title,
                    items.first().value,
                )
            },
            emptyTitle = context.getString(R.string.clinical_weekly_empty_title),
            emptyBody = context.getString(R.string.clinical_weekly_empty_body),
        )
    }

    // ------------------------------------------------------------------ helpers

    /** An edit moves when the event happened; when it was recorded never moves. */
    private fun revised(existing: Provenance?, at: Instant): Provenance = existing?.copy(
        time = TimeProvenance.of(at, existing.time.recordedAt, zone),
    ) ?: provenance(at)

    private fun provenance(at: Instant, version: String = appVersion): Provenance = Provenance(
        sourceId = manual,
        time = TimeProvenance.of(at, clock.now(), zone),
        idempotencyKey = ids.newId(),
        device = "android",
        appVersion = version,
        method = MeasurementMethod.MANUAL_ENTRY,
    )

    private fun atNoon(date: LocalDate): Instant = date.atStartOfDayIn(zone).plus(12.hours)

    private fun parseDate(text: String): LocalDate? =
        runCatching { LocalDate.parse(text.trim()) }.getOrNull()

    private fun field(
        key: String,
        label: Int,
        value: String?,
        type: ClinicalFieldType = ClinicalFieldType.TEXT,
        hint: String = "",
        required: Boolean = false,
    ) = ClinicalFieldModel(
        key = key,
        label = context.getString(label),
        value = value.orEmpty(),
        hint = hint,
        type = type,
        required = required,
    )

    private fun choice(key: String, label: Int, options: List<String>, selected: String) =
        ClinicalFieldModel(
            key = key,
            label = context.getString(label),
            value = selected,
            type = ClinicalFieldType.CHOICE,
            options = options,
        )

    private fun scheduleText(medication: Medication): String = when {
        medication.schedule.asNeeded -> context.getString(R.string.clinical_as_needed)
        medication.schedule.times.isEmpty() -> context.getString(R.string.clinical_not_scheduled)
        else -> medication.schedule.times.joinToString(", ") +
            " · ${number(medication.doseAmount, 1)} ${medication.doseUnit.ucum}"
    }

    private fun duration(minutes: Double): String {
        val whole = round(minutes).toLong()
        val hours = whole / 60
        val rest = whole % 60
        return when {
            hours == 0L -> "${rest}m"
            rest == 0L -> "${hours}h"
            else -> "${hours}h ${rest}m"
        }
    }

    private fun number(value: Double, decimals: Int): String =
        String.format(java.util.Locale.ROOT, "%.${decimals}f", value)

    private fun trim(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    private fun dash(): String = DASH

    private fun kotlinx.datetime.LocalTime.text(): String =
        "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

    private fun rangeLabel(days: Int): String = context.getString(
        when (days) {
            30 -> R.string.clinical_range_30
            90 -> R.string.clinical_range_90
            180 -> R.string.clinical_range_180
            else -> R.string.clinical_range_365
        },
    )

    private fun MedicationForm.label(): String = context.getString(
        when (this) {
            MedicationForm.TABLET -> R.string.clinical_form_tablet
            MedicationForm.CAPSULE -> R.string.clinical_form_capsule
            MedicationForm.LIQUID -> R.string.clinical_form_liquid
            MedicationForm.INJECTION -> R.string.clinical_form_injection
            MedicationForm.INHALER -> R.string.clinical_form_inhaler
            MedicationForm.TOPICAL -> R.string.clinical_form_topical
            MedicationForm.SUPPLEMENT -> R.string.clinical_form_supplement
            MedicationForm.OTHER -> R.string.clinical_form_other
        },
    )

    private fun AllergySeverity.label(): String = context.getString(
        when (this) {
            AllergySeverity.MILD -> R.string.clinical_severity_mild
            AllergySeverity.MODERATE -> R.string.clinical_severity_moderate
            AllergySeverity.SEVERE -> R.string.clinical_severity_severe
        },
    )

    companion object {
        private val SLEEP_TYPES = listOf(
            ObservationType.SLEEP_DURATION,
            ObservationType.SLEEP_DEEP,
            ObservationType.SLEEP_LIGHT,
            ObservationType.SLEEP_REM,
            ObservationType.SLEEP_AWAKE,
            ObservationType.SLEEP_IN_BED,
        )
        private val RANGES = listOf(30, 90, 180, 365)
        private const val PREVIEW_LINES = 40
        private const val DEFAULT_SLEEP_NEED = 8.0 * 60.0
        private const val DASH = "–"
        private val DAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        private val UNIT_LABELS = linkedMapOf(
            "mg" to UcumUnit.MILLIGRAM,
            "g" to UcumUnit.GRAM,
            "ug" to UcumUnit.MICROGRAM,
            "mL" to UcumUnit.MILLILITRE,
            "tablet" to UcumUnit.COUNT,
        )
    }
}
