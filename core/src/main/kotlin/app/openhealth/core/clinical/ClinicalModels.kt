@file:Suppress(
    "MagicNumber",
    "ReturnCount",
    "LongMethod",
    "LongParameterList",
    "CyclomaticComplexMethod",
)

package app.openhealth.core.clinical

import app.openhealth.core.citation.CitationKey
import app.openhealth.core.observation.Provenance
import app.openhealth.core.units.UcumUnit
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/**
 * The clinical record (`pr.md` §6.9). These are layer-4 domain objects: every row keeps
 * the same provenance as an observation, and none of them diagnoses anything. A range is
 * only ever displayed beside the citation it came from (`pr.md` §6.25, §12.1).
 */

/** What a medication or supplement is. */
enum class MedicationForm {
    TABLET,
    CAPSULE,
    LIQUID,
    INJECTION,
    INHALER,
    TOPICAL,
    SUPPLEMENT,
    OTHER,
}

/**
 * A local reminder schedule. Times are wall-clock `HH:mm` strings so a timezone change
 * does not move a dose; [daysOfWeek] uses ISO numbering (1 Monday … 7 Sunday) and an
 * empty list means every day. `asNeeded` medicines are never reminded.
 */
@kotlinx.serialization.Serializable
data class MedicationSchedule(
    val times: List<String> = emptyList(),
    val daysOfWeek: List<Int> = emptyList(),
    val asNeeded: Boolean = false,
) {
    init {
        require(times.all { TIME.matches(it) }) { "a dose time must be HH:mm" }
        require(daysOfWeek.all { it in 1..7 }) { "daysOfWeek uses ISO 1..7" }
    }

    val reminds: Boolean get() = !asNeeded && times.isNotEmpty()

    companion object {
        private val TIME = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")
    }
}

/** One medicine, supplement or prescribed item. */
data class Medication(
    val id: String,
    val name: String,
    val doseAmount: Double,
    val doseUnit: UcumUnit,
    val form: MedicationForm,
    val schedule: MedicationSchedule,
    val active: Boolean,
    val startedOn: LocalDate?,
    val endedOn: LocalDate?,
    val notes: String?,
    val provenance: Provenance,
    val revision: Long = 1,
) {
    init {
        require(name.isNotBlank()) { "a medication needs a name" }
        require(doseAmount > 0.0) { "a dose must be positive" }
    }
}

/** One logged dose: taken, or deliberately skipped. A missed dose is simply not logged. */
data class MedicationDose(
    val id: String,
    val medicationId: String,
    val medicationName: String,
    val scheduledFor: Instant,
    val takenAt: Instant?,
    val skipped: Boolean,
    val localDate: LocalDate,
    val notes: String?,
    val provenance: Provenance,
) {
    init {
        require(skipped != (takenAt != null)) { "a dose is taken or skipped, never both" }
    }
}

/** What was logged against what the schedule expected over a window. */
data class MedicationAdherence(
    val medicationId: String,
    val expected: Int,
    val taken: Int,
    val skipped: Int,
) {
    /** Null when nothing was expected; never a made-up percentage. */
    val percent: Double? get() = if (expected == 0) null else taken * 100.0 / expected
}

/** A named symptom the person tracks, for example "headache". */
data class Symptom(
    val id: String,
    val name: String,
    val notes: String?,
    val provenance: Provenance,
    val revision: Long = 1,
) {
    init {
        require(name.isNotBlank()) { "a symptom needs a name" }
    }
}

/** One episode of a symptom, with a 1–10 severity the person chose. */
data class SymptomEpisode(
    val id: String,
    val symptomId: String,
    val symptomName: String,
    val severity: Int,
    val startedAt: Instant,
    val endedAt: Instant?,
    val triggers: String?,
    val notes: String?,
    val localDate: LocalDate,
    val provenance: Provenance,
    val revision: Long = 1,
) {
    init {
        require(severity in 1..10) { "severity is 1..10" }
        require(endedAt == null || endedAt >= startedAt) {
            "an episode cannot end before it starts"
        }
    }

    val durationMinutes: Double?
        get() = endedAt?.let { (it - startedAt).inWholeSeconds / 60.0 }
}

/** A known allergy or intolerance. */
enum class AllergySeverity { MILD, MODERATE, SEVERE }

data class Allergy(
    val id: String,
    val substance: String,
    val reaction: String?,
    val severity: AllergySeverity,
    val active: Boolean,
    val notedOn: LocalDate?,
    val notes: String?,
    val provenance: Provenance,
    val revision: Long = 1,
) {
    init {
        require(substance.isNotBlank()) { "an allergy needs a substance" }
    }
}

/** A condition the person records about themselves; the app never infers one. */
data class Condition(
    val id: String,
    val name: String,
    val code: String?,
    val codeSystem: String?,
    val onsetOn: LocalDate?,
    val resolvedOn: LocalDate?,
    val notes: String?,
    val provenance: Provenance,
    val revision: Long = 1,
) {
    init {
        require(name.isNotBlank()) { "a condition needs a name" }
    }
}

/** A vaccination the person records. */
data class Vaccination(
    val id: String,
    val name: String,
    val doseLabel: String?,
    val administeredOn: LocalDate,
    val notes: String?,
    val provenance: Provenance,
    val revision: Long = 1,
) {
    init {
        require(name.isNotBlank()) { "a vaccination needs a name" }
    }
}

/** One lab result, entered from the printed report. */
data class LabResult(
    val id: String,
    val analyte: LabAnalyte,
    val value: Double,
    val unit: UcumUnit,
    val referenceLow: Double?,
    val referenceHigh: Double?,
    /** The laboratory's own interval, when the person typed it from the report. */
    val referenceNote: String?,
    val referenceCitation: CitationKey?,
    val collectedAt: Instant,
    val labName: String?,
    val notes: String?,
    val provenance: Provenance,
    val revision: Long = 1,
) {
    init {
        require(value.isFinite() && value >= 0.0) {
            "a lab value must be a real, non-negative number"
        }
        require(referenceLow == null || referenceHigh == null || referenceLow <= referenceHigh) {
            "a reference interval cannot be inverted"
        }
    }

    /** True when the value sits outside the interval the report printed. */
    val outsideReference: Boolean?
        get() {
            val low = referenceLow ?: return referenceHigh?.let { value > it }
            val high = referenceHigh ?: return value < low
            return value < low || value > high
        }
}

/** The editor kinds the clinical surface can open. */
enum class ClinicalKind {
    MEDICATION,
    SYMPTOM,
    LAB,
    ALLERGY,
    CONDITION,
    VACCINATION,
}
