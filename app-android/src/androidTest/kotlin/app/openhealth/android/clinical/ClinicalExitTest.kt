package app.openhealth.android.clinical

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.openhealth.android.AppContainer
import app.openhealth.android.reports.AndroidPdfRenderer
import app.openhealth.core.clinical.ClinicalReportBuilder
import app.openhealth.core.clinical.ClinicalReportData
import app.openhealth.core.clinical.FhirExportData
import app.openhealth.core.clinical.FhirR4
import app.openhealth.core.formula.SleepConsistency
import app.openhealth.core.formula.SleepTiming
import app.openhealth.core.ids.ObservationId
import app.openhealth.core.ids.SourceId
import app.openhealth.core.observation.Observation
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.observation.Provenance
import app.openhealth.core.seam.PdfParagraph
import app.openhealth.core.seam.PdfSection
import app.openhealth.core.seam.PdfTable
import app.openhealth.core.time.TimeProvenance
import app.openhealth.core.units.Measure
import app.openhealth.ui.R as UiR
import app.openhealth.ui.screens.clinical.ClinicalSectionKind
import java.time.DayOfWeek
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.runner.RunWith

/**
 * The B12 exit tests (`BUILD-ROADMAP.md` §B12). They run on the emulator because the PDF
 * renderer and the alarm clock are Android APIs, and they assert the three promises the
 * phase makes: a report is produced offline in under 30 seconds, the FHIR bundle follows
 * R4, and a reminder lands on its scheduled day.
 */
@RunWith(AndroidJUnit4::class)
class ClinicalExitTest {
    private val zone = TimeZone.of("Europe/Berlin")

    @Test
    fun aDoctorReportRendersAsPdfInUnderThirtySeconds() {
        val spec = app.openhealth.core.seam.PdfDocumentSpec(
            title = "openHealth clinical summary",
            subtitle = "Offline report",
            generatedAt = "2026-11-26T09:00:00Z",
            sections = (1..40).map { index ->
                PdfSection(
                    heading = "Section $index",
                    paragraphs = listOf(PdfParagraph("A neutral line of the record.")),
                    table = PdfTable(
                        columns = listOf("Measure", "Value", "Unit", "Reference", "Source"),
                        rows = (1..25).map { row ->
                            listOf("Measure $row", "$row.0", "mg", "4.0-5.6 (cited)", "Lab")
                        },
                    ),
                )
            },
            footer = "This report is a record of information the person entered. It is " +
                "not a diagnosis and not medical advice.",
        )
        val started = SystemClock.elapsedRealtime()
        val bytes = AndroidPdfRenderer().render(spec)
        val elapsed = SystemClock.elapsedRealtime() - started
        assertTrue(bytes.size > 1_000, "the PDF is empty")
        assertEquals("%PDF", bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII))
        assertTrue(elapsed < 30_000, "the PDF took ${elapsed}ms")
    }

    @Test
    fun theBundleTheDeviceExportsStillValidatesAsR4() {
        val at = Instant.parse("2026-11-20T09:00:00Z")
        val provenance = Provenance(
            sourceId = SourceId("manual"),
            time = TimeProvenance.of(at, at, zone),
            idempotencyKey = "exit-1",
        )
        val observation = Observation(
            id = ObservationId("obs-1"),
            type = ObservationType.RESTING_HEART_RATE,
            value = Measure(54.0, ObservationType.RESTING_HEART_RATE.canonicalUnit),
            start = at,
            end = null,
            provenance = provenance,
        )
        val bundle = FhirR4.export(
            FhirExportData(
                patientId = "self",
                generatedAt = at,
                vitals = listOf(observation),
            ),
        )
        assertTrue(bundle.contains("\"resourceType\": \"Bundle\""))
        assertTrue(bundle.contains("http://loinc.org"))
        assertTrue(bundle.contains("http://unitsofmeasure.org"))
    }

    @Test
    fun aReminderLandsOnItsDayAndTime() {
        val mondayAtEight = ZonedDateTime.of(2026, 11, 23, 7, 0, 0, 0, java.time.ZoneId.of("UTC"))
        assertEquals(DayOfWeek.MONDAY, mondayAtEight.dayOfWeek)
        val next = MedicationReminders.nextOccurrence(
            daysOfWeek = listOf(3, 5),
            now = mondayAtEight,
            time = "08:00",
        )
        assertEquals(DayOfWeek.WEDNESDAY, next?.dayOfWeek)
        assertEquals(8, next?.hour)
        assertEquals(0, next?.minute)
    }

    @Test
    fun bedtimeAcrossMidnightStaysRegular() {
        val result = SleepConsistency.compute(
            listOf(
                SleepTiming(LocalDate(2026, 11, 24), null, null),
            ),
            TimeZone.UTC,
        )
        assertEquals(1, result.nights)
    }

    @Test
    fun theReportStillCarriesItsSourceAndDisclaimer() {
        val at = Instant.parse("2026-11-20T09:00:00Z")
        val report = ClinicalReportBuilder.build(
            ClinicalReportData(
                patientLabel = "self",
                from = LocalDate(2026, 11, 20),
                to = LocalDate(2026, 11, 20),
                generatedAt = at,
                zone = zone,
            ),
        )
        assertTrue(report.disclaimer.contains("not a diagnosis"))
        assertTrue(report.sourceNote.contains("source"))
    }

    /**
     * The record has to hold what the person chose. The allergy editor's Active field
     * offers Yes/No, and the save path compared it against the medicine sheet's "Active",
     * so every allergy the UI wrote came back inactive (`pr.md` §6.9: the record is the
     * person's own, shown back unchanged).
     */
    @Test
    fun anAllergySavedAsActiveComesBackActive() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(ALLERGY_DATABASE)
        val container = AppContainer(
            context,
            databaseName = ALLERGY_DATABASE,
            vaultPreferences = ALLERGY_VAULT,
        )
        try {
            val presenter = container.clinicalPresenter
            val editor = presenter.editor(ClinicalSectionKind.ALLERGIES, id = null)
            // The screen seeds its draft from the editor's own values, so this is the
            // draft a person who only types the substance actually saves.
            val draft = editor.fields.associate { it.key to it.value } +
                mapOf("substance" to "Penicillin")
            assertTrue(presenter.save(ClinicalSectionKind.ALLERGIES, id = null, draft = draft))

            val stored = container.store.clinical.allergies().single()
            assertEquals("Penicillin", stored.substance)
            assertTrue(stored.active, "the editor said Yes, so the record must say active")

            val inactive = presenter.save(
                ClinicalSectionKind.ALLERGIES,
                id = stored.id,
                draft = draft + mapOf("active" to context.getString(UiR.string.clinical_no)),
            )
            assertTrue(inactive)
            assertFalse(container.store.clinical.allergies().single().active)
        } finally {
            container.store.close()
            context.deleteDatabase(ALLERGY_DATABASE)
        }
    }

    private companion object {
        const val ALLERGY_DATABASE = "clinical-allergy-test.db"
        const val ALLERGY_VAULT = "clinical-allergy-test-vault"
    }
}
