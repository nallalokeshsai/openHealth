package app.openhealth.data

import app.openhealth.core.seam.FixedClock
import app.openhealth.core.seam.WrongPassphrase
import app.openhealth.core.units.UcumUnit
import app.openhealth.data.corpus.CorpusGenerator
import app.openhealth.data.corpus.CorpusRequest
import app.openhealth.data.export.Argon2ExportCipher
import app.openhealth.data.export.CsvExport
import app.openhealth.data.export.ExportService
import app.openhealth.data.export.ExportTables
import app.openhealth.data.repo.ObservationRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

class ExportRoundTripTest {
    private val now = Instant.parse("2026-03-01T12:00:00Z")
    private val endDate = LocalDate(2026, 3, 1)

    private fun corpusStore(): app.openhealth.data.db.OpenHealthStore {
        val store = inMemoryStore()
        CorpusGenerator(store.db, clock = FixedClock(now)).generate(
            CorpusRequest(days = 60, seed = 7L, endDate = endDate),
        )
        return store
    }

    @Test
    fun exportThenRestoreReproducesEveryTable() {
        val original = corpusStore()
        val cipher = Argon2ExportCipher(iterations = 1, memoryKiB = 8_192, parallelism = 1)
        val exporter = ExportService(original.driver, original.db, cipher, FixedClock(now))
        val before = exporter.snapshot()
        assertTrue(before.tables.sumOf { it.rows.size } > 500)
        assertTrue(before.tables.first { it.table == "raw_record" }.rows.isNotEmpty())

        val container = exporter.export("correct horse battery staple".toCharArray())
        assertEquals(before, exporter.read(container, "correct horse battery staple".toCharArray()))

        val restored = inMemoryStore()
        val restorer = ExportService(restored.driver, restored.db, cipher, FixedClock(now))
        val report = restorer.restore(container, "correct horse battery staple".toCharArray())
        assertEquals(ExportTables.all.size, report.tables)
        assertEquals(before.tables.sumOf { it.rows.size }.toLong(), report.rows)

        val after = restorer.snapshot()
        assertEquals(before.schemaVersion, after.schemaVersion)
        assertEquals(before.tables, after.tables)
        restored.close()
        original.close()
    }

    @Test
    fun theWrongPassphraseDoesNotOpenTheExport() {
        val store = corpusStore()
        val cipher = Argon2ExportCipher(iterations = 1, memoryKiB = 8_192, parallelism = 1)
        val exporter = ExportService(store.driver, store.db, cipher, FixedClock(now))
        val container = exporter.export("right".toCharArray())
        assertFailsWith<WrongPassphrase> {
            exporter.read(container, "wrong".toCharArray())
        }
        store.close()
    }

    @Test
    fun theContainerCarriesItsParametersAndRejectsGarbage() {
        val store = inMemoryStore()
        val cipher = Argon2ExportCipher(iterations = 1, memoryKiB = 8_192, parallelism = 1)
        val exporter = ExportService(store.driver, store.db, cipher, FixedClock(now))
        val container = exporter.export("pass".toCharArray())
        assertEquals("OHX1", container.copyOfRange(0, 4).decodeToString())
        assertFailsWith<app.openhealth.core.seam.UnsupportedExportFormat> {
            exporter.read(byteArrayOf(1, 2, 3, 4, 5), "pass".toCharArray())
        }
        store.close()
    }

    @Test
    fun anExportFromANewerSchemaIsRefused() {
        val store = inMemoryStore()
        val cipher = Argon2ExportCipher(iterations = 1, memoryKiB = 8_192, parallelism = 1)
        val exporter = ExportService(store.driver, store.db, cipher, FixedClock(now))
        val bundle = exporter.snapshot()
        val newer = bundle.copy(schemaVersion = bundle.schemaVersion + 1)
        assertFailsWith<app.openhealth.core.seam.UnsupportedExportFormat> {
            exporter.decode(exporter.encode(newer))
        }
        store.close()
    }

    @Test
    fun restoreReplacesRatherThanAppends() {
        val store = corpusStore()
        val cipher = Argon2ExportCipher(iterations = 1, memoryKiB = 8_192, parallelism = 1)
        val service = ExportService(store.driver, store.db, cipher, FixedClock(now))
        val bundle = service.snapshot()
        val once = service.snapshot().tables.sumOf { it.rows.size }
        service.restoreBundle(bundle)
        val twice = service.snapshot().tables.sumOf { it.rows.size }
        assertEquals(once, twice)
        store.close()
    }

    @Test
    fun aBackfilledObservationKeepsItsRecordedAtThroughExport() {
        val store = inMemoryStore()
        val repository = ObservationRepository(store.db)
        val happened = dayStart(endDate).plus(7.hours)
        val recorded = now.plus(48.hours)
        val observation = testObservation(
            id = "late-entry",
            type = app.openhealth.core.observation.ObservationType.WEIGHT,
            value = 79.4,
            unit = UcumUnit.KILOGRAM,
            start = happened,
            recordedAt = recorded,
        )
        repository.record(observation)
        val cipher = Argon2ExportCipher(iterations = 1, memoryKiB = 8_192, parallelism = 1)
        val service = ExportService(store.driver, store.db, cipher, FixedClock(now))
        val container = service.export("pass".toCharArray())

        val restored = inMemoryStore()
        ExportService(restored.driver, restored.db, cipher, FixedClock(now))
            .restore(container, "pass".toCharArray())
        val row = restored.db.observationQueries.observationById("late-entry").executeAsOne()
        assertEquals(recorded.toEpochMilliseconds(), row.recorded_at)
        assertEquals(happened.toEpochMilliseconds(), row.happened_at)
        restored.close()
        store.close()
    }
}

class CsvExportTest {
    @Test
    fun observationsExportWithTheirProvenance() {
        val observation = testObservation(
            id = "csv-1",
            type = app.openhealth.core.observation.ObservationType.WEIGHT,
            value = 80.0,
            unit = UcumUnit.KILOGRAM,
            start = Instant.parse("2026-03-01T07:00:00Z"),
        )
        val csv = CsvExport.observations(listOf(observation))
        val lines = csv.trim().split("\r\n")
        assertEquals(CsvExport.HEADER.size, lines[0].split(",").size)
        assertTrue(lines[0].startsWith("id,type,value,unit,start"))
        assertTrue(lines[1].startsWith("csv-1,WEIGHT,80.0,kg,"))
        assertTrue(lines[1].contains(",garmin,"))
        assertEquals(2, lines.size)
    }

    @Test
    fun fieldsWithSeparatorsOrQuotesAreQuoted() {
        val observation = testObservation(
            id = "quote-\"and,comma\"",
            type = app.openhealth.core.observation.ObservationType.WEIGHT,
            value = 80.0,
            unit = UcumUnit.KILOGRAM,
            start = Instant.parse("2026-03-01T07:00:00Z"),
        )
        val csv = CsvExport.observations(listOf(observation))
        assertTrue(csv.contains("\"quote-\"\"and,comma\"\"\""))
    }
}
