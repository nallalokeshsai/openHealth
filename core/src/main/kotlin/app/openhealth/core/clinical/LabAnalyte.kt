@file:Suppress(
    "MagicNumber",
    "ReturnCount",
    "LongMethod",
    "LongParameterList",
    "CyclomaticComplexMethod",
)

package app.openhealth.core.clinical

import app.openhealth.core.citation.CitationKey
import app.openhealth.core.units.UcumUnit

/**
 * The small, closed lab catalogue (`pr.md` §6.9). Each analyte carries the LOINC code
 * that identifies it, the UCUM unit the app stores, and either a cited default interval
 * or no interval at all. A default is only present where a published guideline supports
 * one; every other result keeps the interval printed on the person's own report, with
 * [CitationKey.LAB_REPORTED_RANGE] and the laboratory's name.
 *
 * LOINC codes are identifiers, not copied text: the terms are recorded in `NOTICE.md`.
 */
enum class LabAnalyte(
    val loinc: String,
    val display: String,
    val unit: UcumUnit,
    val defaultLow: Double? = null,
    val defaultHigh: Double? = null,
    val defaultCitation: CitationKey? = null,
    val rangeText: String? = null,
    val decimals: Int = 1,
) {
    HBA1C(
        loinc = "4548-4",
        display = "HbA1c",
        unit = UcumUnit.PERCENT,
        defaultLow = 4.0,
        defaultHigh = 5.6,
        defaultCitation = CitationKey.ADA_GLYCEMIC_TARGETS_2026,
        rangeText = "4.0-5.6 %",
    ),
    FASTING_GLUCOSE(
        loinc = "1558-6",
        display = "Fasting glucose",
        unit = UcumUnit.MILLIMOL_PER_LITRE,
        defaultLow = 3.9,
        defaultHigh = 5.6,
        defaultCitation = CitationKey.ADA_GLYCEMIC_TARGETS_2026,
        rangeText = "3.9-5.6 mmol/L",
    ),
    TOTAL_CHOLESTEROL(
        loinc = "2093-3",
        display = "Total cholesterol",
        unit = UcumUnit.MILLIMOL_PER_LITRE,
        defaultHigh = 5.0,
        defaultCitation = CitationKey.ESC_EAS_DYSLIPIDAEMIA_2019,
        rangeText = "below 5.0 mmol/L",
    ),
    LDL_CHOLESTEROL(
        loinc = "2089-1",
        display = "LDL cholesterol",
        unit = UcumUnit.MILLIMOL_PER_LITRE,
        defaultHigh = 3.0,
        defaultCitation = CitationKey.ESC_EAS_DYSLIPIDAEMIA_2019,
        rangeText = "below 3.0 mmol/L",
    ),
    HDL_CHOLESTEROL(
        loinc = "2085-9",
        display = "HDL cholesterol",
        unit = UcumUnit.MILLIMOL_PER_LITRE,
        defaultLow = 1.0,
        defaultCitation = CitationKey.ESC_EAS_DYSLIPIDAEMIA_2019,
        rangeText = "1.0 mmol/L or above",
    ),
    TRIGLYCERIDES(
        loinc = "2571-8",
        display = "Triglycerides",
        unit = UcumUnit.MILLIMOL_PER_LITRE,
        defaultHigh = 1.7,
        defaultCitation = CitationKey.ESC_EAS_DYSLIPIDAEMIA_2019,
        rangeText = "below 1.7 mmol/L",
    ),
    TSH(
        loinc = "3016-3",
        display = "TSH",
        unit = UcumUnit.MILLI_INTERNATIONAL_UNIT_PER_LITRE,
        defaultCitation = CitationKey.LAB_REPORTED_RANGE,
        decimals = 2,
    ),
    FREE_T4(
        loinc = "3024-7",
        display = "Free T4",
        unit = UcumUnit.PICOMOLE_PER_LITRE,
        defaultCitation = CitationKey.LAB_REPORTED_RANGE,
    ),
    VITAMIN_D(
        loinc = "1989-3",
        display = "Vitamin D (25-OH)",
        unit = UcumUnit.NANOMOLE_PER_LITRE,
        defaultCitation = CitationKey.LAB_REPORTED_RANGE,
    ),
    VITAMIN_B12(
        loinc = "2132-9",
        display = "Vitamin B12",
        unit = UcumUnit.PICOMOLE_PER_LITRE,
        defaultCitation = CitationKey.LAB_REPORTED_RANGE,
    ),
    FERRITIN(
        loinc = "2276-4",
        display = "Ferritin",
        unit = UcumUnit.MICROGRAM_PER_LITRE,
        defaultCitation = CitationKey.LAB_REPORTED_RANGE,
    ),
    CREATININE(
        loinc = "2160-0",
        display = "Creatinine",
        unit = UcumUnit.MICROMOLE_PER_LITRE,
        defaultCitation = CitationKey.LAB_REPORTED_RANGE,
    ),
    EGFR(
        loinc = "33914-3",
        display = "eGFR",
        unit = UcumUnit.MILLILITRE_PER_MINUTE_PER_SQUARE_METRE,
        defaultCitation = CitationKey.LAB_REPORTED_RANGE,
    ),
    ALT(
        loinc = "1742-6",
        display = "ALT",
        unit = UcumUnit.UNIT_PER_LITRE,
        defaultCitation = CitationKey.LAB_REPORTED_RANGE,
    ),
    AST(
        loinc = "1920-8",
        display = "AST",
        unit = UcumUnit.UNIT_PER_LITRE,
        defaultCitation = CitationKey.LAB_REPORTED_RANGE,
    ),
    HAEMOGLOBIN(
        loinc = "718-7",
        display = "Haemoglobin",
        unit = UcumUnit.GRAM_PER_LITRE,
        defaultCitation = CitationKey.LAB_REPORTED_RANGE,
    ),
    WHITE_CELLS(
        loinc = "6690-2",
        display = "White cell count",
        unit = UcumUnit.BILLION_PER_LITRE,
        defaultCitation = CitationKey.LAB_REPORTED_RANGE,
    ),
    PLATELETS(
        loinc = "777-3",
        display = "Platelets",
        unit = UcumUnit.BILLION_PER_LITRE,
        defaultCitation = CitationKey.LAB_REPORTED_RANGE,
    ),
    CRP(
        loinc = "1988-5",
        display = "C-reactive protein",
        unit = UcumUnit.MILLIGRAM_PER_LITRE,
        defaultCitation = CitationKey.LAB_REPORTED_RANGE,
    ),
    SODIUM(
        loinc = "2951-2",
        display = "Sodium",
        unit = UcumUnit.MILLIMOL_PER_LITRE,
        defaultCitation = CitationKey.LAB_REPORTED_RANGE,
    ),
    POTASSIUM(
        loinc = "2823-3",
        display = "Potassium",
        unit = UcumUnit.MILLIMOL_PER_LITRE,
        defaultCitation = CitationKey.LAB_REPORTED_RANGE,
    ),
    CALCIUM(
        loinc = "2000-8",
        display = "Calcium",
        unit = UcumUnit.MILLIMOL_PER_LITRE,
        defaultCitation = CitationKey.LAB_REPORTED_RANGE,
        decimals = 2,
    ),
    URATE(
        loinc = "3084-1",
        display = "Urate",
        unit = UcumUnit.MICROMOLE_PER_LITRE,
        defaultCitation = CitationKey.LAB_REPORTED_RANGE,
    ),
    PSA(
        loinc = "2857-1",
        display = "PSA",
        unit = UcumUnit.MICROGRAM_PER_LITRE,
        defaultCitation = CitationKey.LAB_REPORTED_RANGE,
        decimals = 2,
    ),
    INR(
        loinc = "6301-6",
        display = "INR",
        unit = UcumUnit.RATIO,
        defaultCitation = CitationKey.LAB_REPORTED_RANGE,
        decimals = 2,
    ),
    ;

    /** The default interval, or none when the report must supply it. */
    fun defaultInterval(): Pair<Double?, Double?>? =
        if (defaultCitation == CitationKey.LAB_REPORTED_RANGE) {
            null
        } else {
            defaultLow to defaultHigh
        }

    companion object {
        fun fromLoinc(code: String): LabAnalyte? = entries.firstOrNull { it.loinc == code }
    }
}
