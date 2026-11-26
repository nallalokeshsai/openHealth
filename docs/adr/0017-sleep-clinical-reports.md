# 0017 — Sleep, clinical record and reports

**Date:** 2026-11-26 · **Phase:** B12 · **Status:** accepted

## Decisions

1. **Schema v10 is the clinical record.** Migration `9.sqm` adds `medication`,
   `medication_dose`, `symptom`, `symptom_episode`, `lab_result`, `allergy`, `condition`
   and `vaccination`. Every row carries the same provenance as an observation (source,
   device, method where known, `happened_at`, `recorded_at`, local date, zone,
   idempotency key, raw record). Clinical rows are layer 4, not layer 2: a lab result is
   what the laboratory reported, not an observation the app derived. The timeline gains
   the four **events** (`medication_dose`, `symptom_episode`, `lab_result`, `vaccination`);
   definitions (a medicine, an allergy) are not events, just as a routine is not.
2. **LOINC first, no invented code.** The lab catalogue is a closed list
   (`core/…/clinical/LabAnalyte.kt`) and a vital map (`Loinc.kt`) of LOINC codes. A type
   without a code the project can state confidently is left out of the FHIR bundle and
   still appears in the PDF report; a guess never becomes a code. LOINC terms are used as
   identifiers with attribution in `NOTICE.md`.
3. **A reference interval is either the laboratory's or a cited guideline's.** The lab
   editor asks for the interval printed on the person's own report and records the
   laboratory's name; that interval carries `LAB_REPORTED_RANGE`. Bundled defaults exist
   only where a published guideline supports one: HbA1c and fasting glucose (ADA 2026)
   and lipids (ESC/EAS 2019). Every other analyte is `LAB_REPORTED_RANGE` with no default,
   and the report says "not entered" rather than borrowing another lab's interval
   (CLSI EP28).
4. **Adherence is logged doses, not assumed doses.** `expected` is what the schedule asked
   for in the window; `taken` and `skipped` are what the person logged. An unlogged dose
   is never counted as missed and no percentage is invented when nothing was expected.
5. **Reminders are local alarms.** `AlarmManager` plus a notification with Taken and
   Skipped actions; no push server, no network. Exact alarms are used when the platform
   allows them, otherwise `setAndAllowWhileIdle`. A boot receiver rebuilds the schedule.
   The notification is a convenience, not a medical alert (`pr.md` §12.1).
6. **Reports are light, cited and offline.** One data model produces the preview, the PDF,
   the CSV, the FHIR bundle and the Open mHealth export. The PDF is laid out on the device
   with `android.graphics.pdf` behind `PdfSeam`; a large report renders in well under 30 s
   with no network. The footer carries the source note and the disclaimer on every page.
   Reports never interpret a value.
7. **FHIR R4 is validated, not asserted.** The official R4 JSON schema is vendored under
   `core/src/test/resources/fhir/` (CC0) and a JVM test validates the exported bundle with
   `com.networknt:json-schema-validator` (Apache-2.0, test scope only). The test renames
   the schema's draft-04 `id` keyword to draft-06 `$id` in memory because HL7's file
   declares draft-06 while still using the older keyword; the vendored file is untouched.
8. **Open mHealth only where it fits.** Heart rate, blood pressure (one paired data
   point), body weight, physical activity and sleep duration use the public OMH schema
   ids. A type without a schema is omitted rather than forced into a near match; FHIR and
   the PDF remain complete.
9. **Sleep quality is the person's, not inferred.** A new observation type
   `SLEEP_QUALITY` stores a 1–5 self-rating (keepAll, mean rollup, plausibility 1–5). It is
   never derived from stages. Bedtime and wake consistency use a circular standard
   deviation, so 23:50 and 00:10 are twenty minutes apart.
10. **Weight-neutral mode is one flag on the data, not a second report.** Weight, body fat
    and photo-derived values are removed from the report, the PDF, the FHIR bundle and the
    Open mHealth export when the setting is on; nothing is blurred or blanked in place.
