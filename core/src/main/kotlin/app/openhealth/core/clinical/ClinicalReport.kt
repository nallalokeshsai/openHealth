@file:Suppress(
    "TooManyFunctions",
    "MagicNumber",
    "ReturnCount",
    "LongMethod",
    "LongParameterList",
    "CyclomaticComplexMethod",
    "SpreadOperator",
)

package app.openhealth.core.clinical

import app.openhealth.core.citation.Citations
import app.openhealth.core.formula.SleepConsistency
import app.openhealth.core.formula.SleepDebt
import app.openhealth.core.formula.SleepNight
import app.openhealth.core.formula.SleepTiming
import app.openhealth.core.observation.Observation
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.seam.PdfDocumentSpec
import app.openhealth.core.seam.PdfParagraph
import app.openhealth.core.seam.PdfSection
import app.openhealth.core.seam.PdfTable
import app.openhealth.core.units.displayLabel
import app.openhealth.core.vitals.VitalRanges
import kotlin.math.round
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime

/** One activity total as the report prints it; the data layer's row is not imported here. */
data class ReportActivity(
    val localDate: LocalDate,
    val type: String,
    val minutes: Double,
    val distanceKm: Double,
)

/** One night as the report prints it, already assembled from the sleep observations. */
data class ReportSleepNight(
    val date: LocalDate,
    val asleepMinutes: Double,
    val bedtime: Instant?,
    val wake: Instant?,
    val quality: Int?,
)

/**
 * Everything the clinical report may show. It is explicit and flat so the same builder
 * serves the PDF, the preview, the CSV and the tests, and so weight-neutral mode is one
 * flag rather than a second code path (`pr.md` §6.25).
 */
data class ClinicalReportData(
    val patientLabel: String,
    val from: LocalDate,
    val to: LocalDate,
    val generatedAt: Instant,
    val zone: TimeZone,
    val observations: List<Observation> = emptyList(),
    val labs: List<LabResult> = emptyList(),
    val medications: List<Medication> = emptyList(),
    val doses: List<MedicationDose> = emptyList(),
    val symptoms: List<SymptomEpisode> = emptyList(),
    val allergies: List<Allergy> = emptyList(),
    val conditions: List<Condition> = emptyList(),
    val vaccinations: List<Vaccination> = emptyList(),
    val sleep: List<ReportSleepNight> = emptyList(),
    val activities: List<ReportActivity> = emptyList(),
    /** The rollup layer's deduplicated daily values; never a sum of sources. */
    val stepsByDay: Map<LocalDate, Double> = emptyMap(),
    val weightByDay: Map<LocalDate, Double> = emptyMap(),
    val sleepNeedMinutes: Double = 8.0 * 60.0,
    val weightNeutral: Boolean = false,
    val sourceLabels: Map<String, String> = emptyMap(),
)

/** One section of the report; the table is the PDF table and the preview's rows. */
data class ReportSection(
    val title: String,
    val paragraphs: List<String> = emptyList(),
    val table: PdfTable? = null,
)

/**
 * The doctor-ready report (`pr.md` §6.9, §6.16, §6.25; `DESIGN.md` §8.7). It is always
 * light and always carries the unit, source and citation beside each figure, and the
 * disclaimer once at the foot. It never says what a value means.
 */
data class ClinicalReport(
    val title: String,
    val subtitle: String,
    val generatedAt: Instant,
    val sections: List<ReportSection>,
    val disclaimer: String,
    val sourceNote: String,
) {
    fun toPdfSpec(): PdfDocumentSpec = PdfDocumentSpec(
        title = title,
        subtitle = subtitle,
        generatedAt = generatedAt.toString(),
        sections = sections.map { section ->
            PdfSection(
                heading = section.title,
                paragraphs = section.paragraphs.map { PdfParagraph(it) },
                table = section.table,
            )
        },
        footer = "$sourceNote\n$disclaimer",
    )

    /** The plain-text preview and the accessible alternative to the PDF. */
    fun toPlainText(): String = buildString {
        appendLine(title)
        appendLine(subtitle)
        appendLine()
        sections.forEach { section ->
            appendLine(section.title)
            section.paragraphs.forEach { appendLine(it) }
            section.table?.let { table ->
                appendLine(table.columns.joinToString(" | "))
                table.rows.forEach { row -> appendLine(row.joinToString(" | ")) }
            }
            appendLine()
        }
        appendLine(sourceNote)
        appendLine(disclaimer)
    }

    /**
     * A plain CSV that keeps each table's own columns (`pr.md` §6.9 MVP). The section
     * name is a line above its header, so a column is never silently shifted into the
     * wrong field. The source note and the disclaimer travel at the foot.
     */
    fun toCsv(): String = buildString {
        appendLine(csvRow("report", title))
        appendLine(csvRow("window", subtitle))
        appendLine(csvRow("generated", generatedAt.toString()))
        sections.forEach { section ->
            appendLine()
            appendLine(csvRow(section.title))
            section.table?.let { table ->
                appendLine(csvRow(*table.columns.toTypedArray()))
                table.rows.forEach { row -> appendLine(csvRow(*row.toTypedArray())) }
            }
            section.paragraphs.forEach { text -> appendLine(csvRow(text)) }
        }
        appendLine()
        appendLine(csvRow("source_note", sourceNote))
        appendLine(csvRow("disclaimer", disclaimer))
    }
}

object ClinicalReportBuilder {
    private const val DISCLAIMER =
        "This report is a record of information the person entered or imported. It is " +
            "not a diagnosis, not medical advice and not a medical device. Reference " +
            "ranges are informational and cite their source. Discuss any result with a " +
            "clinician."
    private const val SOURCE_NOTE =
        "Every figure names its source. Ranges cite the guideline or the reporting " +
            "laboratory named beside them. Generated offline by openHealth."

    private val VITAL_TYPES = listOf(
        ObservationType.BLOOD_PRESSURE_SYSTOLIC,
        ObservationType.BLOOD_PRESSURE_DIASTOLIC,
        ObservationType.RESTING_HEART_RATE,
        ObservationType.HEART_RATE,
        ObservationType.BLOOD_GLUCOSE,
        ObservationType.BODY_TEMPERATURE,
        ObservationType.OXYGEN_SATURATION,
        ObservationType.RESPIRATORY_RATE,
    )

    fun build(data: ClinicalReportData): ClinicalReport {
        val sections = mutableListOf<ReportSection>()
        vitalSection(data)?.let(sections::add)
        medicationSection(data)?.let(sections::add)
        symptomSection(data)?.let(sections::add)
        labSection(data)?.let(sections::add)
        allergySection(data)?.let(sections::add)
        conditionSection(data)?.let(sections::add)
        vaccinationSection(data)?.let(sections::add)
        sleepSection(data)?.let(sections::add)
        activitySection(data)?.let(sections::add)
        trendSection(data)?.let(sections::add)
        referencesSection(data)?.let(sections::add)
        return ClinicalReport(
            title = "openHealth clinical summary",
            subtitle = "${data.patientLabel} · ${data.from} to ${data.to} · " +
                "generated ${data.generatedAt.toLocalDateTime(data.zone).date}",
            generatedAt = data.generatedAt,
            sections = sections,
            disclaimer = DISCLAIMER,
            sourceNote = SOURCE_NOTE,
        )
    }

    private fun vitalSection(data: ClinicalReportData): ReportSection? {
        val rows = mutableListOf<List<String>>()
        VITAL_TYPES.forEach { type ->
            if (data.weightNeutral &&
                type in setOf(
                    ObservationType.WEIGHT,
                    ObservationType.BODY_FAT_PERCENT,
                    ObservationType.LEAN_MASS,
                )
            ) {
                return@forEach
            }
            val label = vitalLabel(type) ?: return@forEach
            val readings = data.observations.filter {
                it.type == type && it.localDate >= data.from && it.localDate <= data.to
            }
            if (readings.isEmpty()) return@forEach
            val latest = readings.maxBy { it.start }
            val unit = latest.value.unit.displayLabel()
            val range = VitalRanges.forReading(type, latest.context?.name)
            rows += listOf(
                label,
                number(latest.value.value, decimalsFor(type)),
                unit,
                range?.let { "${it.statement} (${Citations.of(it.citation).title})" } ?: "",
                source(data, latest.provenance.sourceId.value),
            )
        }
        if (rows.isEmpty()) return null
        return ReportSection(
            title = "Vitals",
            paragraphs = listOf(
                "The latest reading in the window, its source and the cited reference range.",
            ),
            table = PdfTable(
                columns = listOf("Measure", "Latest", "Unit", "Reference", "Source"),
                rows = rows,
                weights = listOf(1.4f, 0.8f, 0.6f, 2.2f, 1.0f),
            ),
        )
    }

    private fun medicationSection(data: ClinicalReportData): ReportSection? {
        if (data.medications.isEmpty()) return null
        val rows = data.medications
            .sortedWith(compareByDescending<Medication> { it.active }.thenBy { it.name })
            .map { medication ->
                val adherence = Adherence.compute(medication, data.doses, data.from, data.to)
                listOf(
                    medication.name,
                    "${number(medication.doseAmount, 1)} ${medication.doseUnit.ucum}",
                    scheduleText(medication),
                    adherenceText(adherence),
                    if (medication.active) "Active" else "Stopped",
                )
            }
        return ReportSection(
            title = "Medications and supplements",
            paragraphs = listOf(
                "Adherence counts only doses the person logged against the schedule; a " +
                    "dose that was not logged is not assumed to be missed.",
            ),
            table = PdfTable(
                columns = listOf("Medicine", "Dose", "Schedule", "Adherence", "Status"),
                rows = rows,
                weights = listOf(1.6f, 0.9f, 1.6f, 1.2f, 0.8f),
            ),
        )
    }

    private fun symptomSection(data: ClinicalReportData): ReportSection? {
        val episodes = data.symptoms.filter {
            it.localDate >= data.from && it.localDate <= data.to
        }
        if (episodes.isEmpty()) return null
        val rows = episodes.sortedByDescending { it.startedAt }.map { episode ->
            listOf(
                episode.symptomName,
                episode.severity.toString(),
                episode.localDate.toString(),
                episode.durationMinutes?.let { minutesText(it) } ?: "ongoing",
                episode.triggers.orEmpty(),
            )
        }
        return ReportSection(
            title = "Symptoms and episodes",
            paragraphs = listOf("Severity is the person's own 1–10 rating."),
            table = PdfTable(
                columns = listOf("Symptom", "Severity", "Started", "Duration", "Triggers"),
                rows = rows,
                weights = listOf(1.5f, 0.7f, 0.9f, 0.9f, 2.0f),
            ),
        )
    }

    private fun labSection(data: ClinicalReportData): ReportSection? {
        val labs = data.labs.filter {
            it.provenance.localDate >= data.from && it.provenance.localDate <= data.to
        }
        if (labs.isEmpty()) return null
        val rows = labs.sortedByDescending { it.collectedAt }.map { lab ->
            // A printed note is the laboratory's interval; a guideline default must not
            // replace it just because no two numbers were typed.
            val hasNote = lab.referenceNote != null
            val low = lab.referenceLow ?: lab.analyte.defaultLow.takeUnless { hasNote }
            val high = lab.referenceHigh ?: lab.analyte.defaultHigh.takeUnless { hasNote }
            val citation = lab.referenceCitation ?: lab.analyte.defaultCitation
            listOf(
                "${lab.analyte.display} (${lab.analyte.loinc})",
                number(lab.value, lab.analyte.decimals),
                lab.unit.ucum,
                referenceText(low, high, citation?.let { Citations.of(it).title }),
                lab.labName.orEmpty(),
                lab.provenance.localDate.toString(),
            )
        }
        return ReportSection(
            title = "Laboratory results",
            paragraphs = listOf(
                "Each result keeps the LOINC identifier and the interval printed by the " +
                    "reporting laboratory, or the cited default where one exists.",
            ),
            table = PdfTable(
                columns = listOf("Test (LOINC)", "Result", "Unit", "Reference", "Lab", "Date"),
                rows = rows,
                weights = listOf(2.0f, 0.7f, 0.7f, 1.8f, 1.1f, 0.9f),
            ),
        )
    }

    private fun allergySection(data: ClinicalReportData): ReportSection? {
        if (data.allergies.isEmpty()) return null
        val rows = data.allergies.map { allergy ->
            listOf(
                allergy.substance,
                allergy.reaction.orEmpty(),
                allergy.severity.name.lowercase().replaceFirstChar { it.uppercase() },
                if (allergy.active) "Active" else "Inactive",
            )
        }
        return ReportSection(
            title = "Allergies and intolerances",
            table = PdfTable(
                columns = listOf("Substance", "Reaction", "Severity", "Status"),
                rows = rows,
                weights = listOf(1.6f, 1.8f, 0.8f, 0.8f),
            ),
        )
    }

    private fun conditionSection(data: ClinicalReportData): ReportSection? {
        if (data.conditions.isEmpty()) return null
        val rows = data.conditions.map { condition ->
            listOf(
                condition.name,
                condition.onsetOn?.toString().orEmpty(),
                if (condition.resolvedOn == null) "Active" else "Resolved",
            )
        }
        return ReportSection(
            title = "Conditions",
            table = PdfTable(
                columns = listOf("Condition", "Onset", "Status"),
                rows = rows,
                weights = listOf(2.2f, 1.0f, 0.8f),
            ),
        )
    }

    private fun vaccinationSection(data: ClinicalReportData): ReportSection? {
        if (data.vaccinations.isEmpty()) return null
        val rows = data.vaccinations.sortedByDescending { it.administeredOn }.map {
            listOf(it.name, it.doseLabel.orEmpty(), it.administeredOn.toString())
        }
        return ReportSection(
            title = "Vaccinations",
            table = PdfTable(
                columns = listOf("Vaccine", "Dose", "Date"),
                rows = rows,
                weights = listOf(2.2f, 1.0f, 0.9f),
            ),
        )
    }

    private fun sleepSection(data: ClinicalReportData): ReportSection? {
        if (data.sleep.isEmpty()) return null
        val average = data.sleep.map { it.asleepMinutes }.average()
        val debt = SleepDebt.compute(
            data.sleep.map { SleepNight(it.date, it.asleepMinutes) },
            data.sleepNeedMinutes,
            windowDays = data.from.daysUntil(data.to) + 1,
        )
        val consistency = SleepConsistency.compute(
            data.sleep.map { SleepTiming(it.date, it.bedtime, it.wake) },
            data.zone,
        )
        val quality = data.sleep.mapNotNull { it.quality }.takeIf { it.isNotEmpty() }?.average()
        val paragraphs = buildList {
            add("Nights recorded: ${data.sleep.size}. Average sleep: ${minutesText(average)}.")
            val debtValue = (debt as? app.openhealth.core.formula.FormulaOutcome.Computed)?.value
            debtValue?.let {
                add(
                    "Sleep debt over the window: ${minutesText(it.debtMinutes)} against a " +
                        "need of ${minutesText(data.sleepNeedMinutes)} a night.",
                )
            }
            consistency.bedtimeSdMinutes?.let {
                add("Bedtime varied by about ${minutesText(it)} (standard deviation).")
            }
            consistency.wakeSdMinutes?.let {
                add("Wake time varied by about ${minutesText(it)} (standard deviation).")
            }
            quality?.let { add("Average self-rated sleep quality: ${number(it, 1)} of 5.") }
        }
        val rows = data.sleep.sortedByDescending { it.date }.take(14).map { night ->
            listOf(
                night.date.toString(),
                minutesText(night.asleepMinutes),
                night.bedtime?.clock(data.zone).orEmpty(),
                night.wake?.clock(data.zone).orEmpty(),
                night.quality?.toString().orEmpty(),
            )
        }
        return ReportSection(
            title = "Sleep",
            paragraphs = paragraphs,
            table = PdfTable(
                columns = listOf("Night", "Asleep", "Bedtime", "Wake", "Quality"),
                rows = rows,
                weights = listOf(1.0f, 0.9f, 0.8f, 0.8f, 0.7f),
            ),
        )
    }

    private fun activitySection(data: ClinicalReportData): ReportSection? {
        if (data.activities.isEmpty()) return null
        val minutes = data.activities.sumOf { it.minutes }
        val distance = data.activities.sumOf { it.distanceKm }
        val byType = data.activities.groupBy { it.type }.mapValues { (_, rows) ->
            rows.sumOf { it.minutes }
        }
        val paragraphs = buildList {
            add(
                "${data.activities.size} activities, ${minutesText(minutes)} in total, " +
                    "${number(distance, 1)} km recorded.",
            )
            byType.entries.sortedByDescending { it.value }.forEach { (type, value) ->
                add("${type.lowercase().replace('_', ' ')}: ${minutesText(value)}.")
            }
        }
        return ReportSection(title = "Activity", paragraphs = paragraphs)
    }

    private fun trendSection(data: ClinicalReportData): ReportSection? {
        val rows = mutableListOf<List<String>>()
        val days = (data.from.daysUntil(data.to) + 1).coerceAtLeast(1)
        if (data.stepsByDay.isNotEmpty()) {
            rows += listOf(
                "Steps",
                number(data.stepsByDay.values.average(), 0),
                "steps/day",
                "${data.stepsByDay.size} days with data",
                "Device",
            )
        }
        if (data.sleep.isNotEmpty()) {
            rows += listOf(
                "Sleep",
                minutesText(data.sleep.map { it.asleepMinutes }.average()),
                "per night",
                "${data.sleep.size} nights",
                "Sleep source",
            )
        }
        if (data.activities.isNotEmpty()) {
            rows += listOf(
                "Training",
                number(data.activities.sumOf { it.minutes } / days, 0),
                "min/day",
                "${data.activities.size} activities",
                "Activity log",
            )
        }
        if (!data.weightNeutral && data.weightByDay.size >= 2) {
            val weights = data.weightByDay.toSortedMap()
            rows += listOf(
                "Weight",
                "${number(weights.values.first(), 1)} to " +
                    number(weights.values.last(), 1),
                "kg",
                "one source per day, first to last",
                "Device or manual",
            )
        }
        if (rows.isEmpty()) return null
        return ReportSection(
            title = "Trend summary",
            paragraphs = listOf(
                "Averages over the report window. A day without data is left out, not " +
                    "counted as zero.",
            ),
            table = PdfTable(
                columns = listOf("Measure", "Value", "Unit", "Basis", "Source"),
                rows = rows,
                weights = listOf(1.2f, 1.2f, 0.8f, 1.8f, 1.0f),
            ),
        )
    }

    private fun referencesSection(data: ClinicalReportData): ReportSection? {
        val keys = linkedSetOf<app.openhealth.core.citation.CitationKey>()
        VITAL_TYPES.forEach { type ->
            val observation = data.observations.firstOrNull { it.type == type } ?: return@forEach
            VitalRanges.forReading(type, observation.context?.name)?.let { keys += it.citation }
        }
        data.labs.forEach { lab ->
            (lab.referenceCitation ?: lab.analyte.defaultCitation)?.let(keys::add)
        }
        if (data.sleep.isNotEmpty()) {
            keys += app.openhealth.core.citation.CitationKey.SLEEP_CONSENSUS_2015
        }
        if (keys.isEmpty()) return null
        val rows = keys.map { key ->
            val citation = Citations.of(key)
            listOf(
                citation.title,
                citation.authors,
                citation.year.toString(),
                citation.url,
            )
        }
        return ReportSection(
            title = "References",
            paragraphs = listOf("The published sources behind every range in this report."),
            table = PdfTable(
                columns = listOf("Source", "Authors", "Year", "Link"),
                rows = rows,
                weights = listOf(2.4f, 2.0f, 0.6f, 2.0f),
            ),
        )
    }

    // -------------------------------------------------------------------- helpers

    private fun source(data: ClinicalReportData, sourceId: String): String =
        data.sourceLabels[sourceId] ?: sourceId

    private fun vitalLabel(type: ObservationType): String? = when (type) {
        ObservationType.BLOOD_PRESSURE_SYSTOLIC -> "Systolic blood pressure"
        ObservationType.BLOOD_PRESSURE_DIASTOLIC -> "Diastolic blood pressure"
        ObservationType.RESTING_HEART_RATE -> "Resting heart rate"
        ObservationType.HEART_RATE -> "Heart rate"
        ObservationType.BLOOD_GLUCOSE -> "Blood glucose"
        ObservationType.BODY_TEMPERATURE -> "Body temperature"
        ObservationType.OXYGEN_SATURATION -> "Oxygen saturation"
        ObservationType.RESPIRATORY_RATE -> "Respiratory rate"
        ObservationType.WEIGHT -> "Weight"
        ObservationType.BODY_FAT_PERCENT -> "Body fat"
        else -> null
    }

    private fun decimalsFor(type: ObservationType): Int = when (type) {
        ObservationType.BLOOD_GLUCOSE, ObservationType.BODY_TEMPERATURE -> 1
        ObservationType.WEIGHT -> 1
        ObservationType.BODY_FAT_PERCENT -> 1
        else -> 0
    }

    private fun scheduleText(medication: Medication): String {
        if (medication.schedule.asNeeded) return "As needed"
        if (medication.schedule.times.isEmpty()) return "Not scheduled"
        val days = medication.schedule.daysOfWeek
        val dayText = if (days.isEmpty()) "Every day" else days.joinToString(", ") { dayName(it) }
        return "$dayText at ${medication.schedule.times.joinToString(", ")}"
    }

    private fun adherenceText(adherence: MedicationAdherence): String {
        if (adherence.expected == 0) {
            return if (adherence.taken + adherence.skipped == 0) {
                "Not scheduled"
            } else {
                "${adherence.taken} logged"
            }
        }
        return "${adherence.taken} logged of ${adherence.expected} expected, " +
            "${adherence.skipped} skipped"
    }

    private fun referenceText(low: Double?, high: Double?, citation: String?): String {
        val interval = when {
            low != null && high != null -> "${number(low, 2)}-${number(high, 2)}"
            low != null -> "at least ${number(low, 2)}"
            high != null -> "below ${number(high, 2)}"
            else -> "not entered"
        }
        return if (citation != null) "$interval ($citation)" else interval
    }

    private fun minutesText(minutes: Double): String {
        val whole = round(minutes).toLong()
        val hours = whole / 60
        val rest = whole % 60
        return when {
            hours == 0L -> "${rest}m"
            rest == 0L -> "${hours}h"
            else -> "${hours}h ${rest}m"
        }
    }

    private fun dayName(iso: Int): String = when (iso) {
        1 -> "Mon"
        2 -> "Tue"
        3 -> "Wed"
        4 -> "Thu"
        5 -> "Fri"
        6 -> "Sat"
        7 -> "Sun"
        else -> ""
    }

    private fun Instant.clock(zone: TimeZone): String {
        val time = toLocalDateTime(zone).time
        return "${time.hour.toString().padStart(2, '0')}:" +
            time.minute.toString().padStart(2, '0')
    }

    private fun number(value: Double, decimals: Int): String {
        val factor = when (decimals) {
            0 -> 1.0
            1 -> 10.0
            2 -> 100.0
            else -> 1000.0
        }
        val rounded = round(value * factor) / factor
        // Fixed decimals so a printed interval reads "4.00-5.60", not "4.0-5.6".
        return String.format(java.util.Locale.ROOT, "%.${decimals}f", rounded)
    }
}

/** One CSV line, quoted only when it must be (`pr.md` §6.9 plain CSV export). */
private fun csvRow(vararg cells: String): String = cells.joinToString(",") { cell ->
    // A leading =, +, - or @ is a formula in a spreadsheet; quote it as text.
    val safe = if (cell.firstOrNull() in listOf('=', '+', '-', '@')) "'$cell" else cell
    val escaped = safe.replace("\"", "\"\"")
    if (escaped.contains(',') || escaped.contains('"') || escaped.contains('\n')) {
        "\"$escaped\""
    } else {
        escaped
    }
}
