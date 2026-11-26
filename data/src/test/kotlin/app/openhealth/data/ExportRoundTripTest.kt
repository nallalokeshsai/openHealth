package app.openhealth.data

import app.openhealth.core.seam.FixedClock
import app.openhealth.core.seam.WrongPassphrase
import app.openhealth.core.source.SourceRegistry
import app.openhealth.core.units.UcumUnit
import app.openhealth.data.corpus.CorpusGenerator
import app.openhealth.data.corpus.CorpusRequest
import app.openhealth.data.db.Note
import app.openhealth.data.export.Argon2ExportCipher
import app.openhealth.data.export.CsvExport
import app.openhealth.data.export.ExportBundle
import app.openhealth.data.export.ExportService
import app.openhealth.data.export.ExportTables
import app.openhealth.data.repo.NoteRepository
import app.openhealth.data.repo.ObservationRepository
import app.openhealth.data.repo.RollupRepository
import app.openhealth.data.repo.SourceRepository
import app.openhealth.data.rollup.RollupEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonPrimitive

class ExportRoundTripTest {
    private val now = Instant.parse("2026-03-01T12:00:00Z")
    private val endDate = LocalDate(2026, 3, 1)

    private fun corpusStore(): app.openhealth.data.db.OpenHealthStore {
        val store = inMemoryStore()
        CorpusGenerator(store.db, clock = FixedClock(now)).generate(
            CorpusRequest(days = 60, seed = 7L, endDate = endDate),
        )
        app.openhealth.data.repo.SettingsRepository(store.db).put("units", "metric")
        NoteRepository(store.db).insert(
            Note(
                id = "note-fixture",
                body = "A note that must survive the export",
                local_date = endDate.toString(),
                zone_id = TEST_ZONE.id,
                happened_at = now.toEpochMilliseconds(),
                recorded_at = now.toEpochMilliseconds(),
                edited_at = null,
                source_id = "manual",
                raw_record_id = null,
                idempotency_key = "export-note-fixture",
                device = "manual",
                app_version = "test",
                revision = 1L,
            ),
        )
        store.db.tombstoneQueries.insertTombstone(
            id = "tombstone-fixture",
            entityTable = "measurement",
            entityId = "measurement-gone",
            deletedAt = now.toEpochMilliseconds(),
            purgeAfter = (now + 30.days).toEpochMilliseconds(),
            payload = "{}",
            reason = "fixture",
        )
        val clinical = app.openhealth.data.repo.ClinicalRepository(store.db)
        val provenance = app.openhealth.core.observation.Provenance(
            sourceId = app.openhealth.core.ids.SourceId("manual"),
            time = app.openhealth.core.time.TimeProvenance.of(now, now, TEST_ZONE),
            idempotencyKey = "clinical-fixture",
        )
        clinical.saveMedication(
            app.openhealth.core.clinical.Medication(
                id = "med-fixture",
                name = "Levothyroxine",
                doseAmount = 50.0,
                doseUnit = UcumUnit.MICROGRAM,
                form = app.openhealth.core.clinical.MedicationForm.TABLET,
                schedule = app.openhealth.core.clinical.MedicationSchedule(
                    times = listOf("08:00"),
                ),
                active = true,
                startedOn = endDate,
                endedOn = null,
                notes = null,
                provenance = provenance,
            ),
        )
        clinical.saveLab(
            app.openhealth.core.clinical.LabResult(
                id = "lab-fixture",
                analyte = app.openhealth.core.clinical.LabAnalyte.HBA1C,
                value = 5.4,
                unit = app.openhealth.core.clinical.LabAnalyte.HBA1C.unit,
                referenceLow = 4.0,
                referenceHigh = 5.6,
                referenceNote = null,
                referenceCitation = null,
                collectedAt = now,
                labName = "Labor Berlin",
                notes = null,
                provenance = provenance,
            ),
        )
        RollupEngine(
            ObservationRepository(store.db),
            RollupRepository(store.db),
            store.db,
            SourceRegistry(SourceRepository(store.db).all()),
            FixedClock(now),
        ).rebuildAll()
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
        val restoredClinical = app.openhealth.data.repo.ClinicalRepository(restored.db)
        assertEquals("Levothyroxine", restoredClinical.medication("med-fixture")?.name)
        assertEquals(5.4, restoredClinical.lab("lab-fixture")?.value)
        assertEquals("Labor Berlin", restoredClinical.lab("lab-fixture")?.labName)
        assertEquals(ExportTables.all.size, report.tables)
        assertEquals(
            before.tables.filterNot { it.table == "daily_rollup" }.sumOf { it.rows.size }.toLong(),
            report.rows,
        )

        val after = restorer.snapshot()
        assertEquals(before.schemaVersion, after.schemaVersion)
        assertEquals(semanticTables(before), semanticTables(after))
        restored.close()
        original.close()
    }

    @Test
    fun aBundleWrittenBeforePhotosStillRestores() {
        val store = corpusStore()
        val cipher = Argon2ExportCipher(iterations = 1, memoryKiB = 8_192, parallelism = 1)
        val service = ExportService(store.driver, store.db, cipher, FixedClock(now))
        val bundle = service.snapshot()
        // An export taken before B7 has no body_photo table. It is a complete backup of
        // the older account, not corruption, so it restores with that table empty.
        val older = bundle.copy(tables = bundle.tables.filterNot { it.table == "body_photo" })
        val report = service.restoreBundle(older)
        assertEquals(
            bundle.tables.filterNot { it.table == "body_photo" || it.table == "daily_rollup" }
                .sumOf { it.rows.size },
            report.rows.toInt(),
        )
        assertEquals(0L, store.db.bodyQueries.countPhotos().executeAsOne())
        store.close()
    }

    @Test
    fun photosAreExcludedFromAGeneralExportUnlessIncluded() {
        val store = corpusStore()
        store.db.bodyQueries.insertPhoto(
            id = "photo-export",
            localDate = endDate.toString(),
            zoneId = TEST_ZONE.id,
            happenedAt = now.toEpochMilliseconds(),
            recordedAt = now.toEpochMilliseconds(),
            sourceId = "manual",
            caption = null,
            notes = null,
            mime = "image/jpeg",
            width = 10L,
            height = 10L,
            bytesB64 = "AA==",
            thumbB64 = "AA==",
            sha256 = "abc",
            idempotencyKey = "photo-export",
            device = null,
            appVersion = null,
            revision = 1L,
        )
        val cipher = Argon2ExportCipher(iterations = 1, memoryKiB = 8_192, parallelism = 1)
        val excluded = ExportService(
            store.driver,
            store.db,
            cipher,
            FixedClock(now),
            includePhotos = { false },
        ).snapshot().tables.first { it.table == "body_photo" }
        assertEquals(0, excluded.rows.size)
        val included = ExportService(
            store.driver,
            store.db,
            cipher,
            FixedClock(now),
            includePhotos = { true },
        ).snapshot().tables.first { it.table == "body_photo" }
        assertEquals(1, included.rows.size)
        store.close()
    }

    @Test
    fun aBundleMissingATableIsRefused() {
        val store = corpusStore()
        val cipher = Argon2ExportCipher(iterations = 1, memoryKiB = 8_192, parallelism = 1)
        val service = ExportService(store.driver, store.db, cipher, FixedClock(now))
        val bundle = service.snapshot()
        val truncated = bundle.copy(tables = bundle.tables.filterNot { it.table == "observation" })
        assertFailsWith<app.openhealth.core.seam.UnsupportedExportFormat> {
            service.restoreBundle(truncated)
        }
        store.close()
    }

    @Test
    fun restoreRebuildsRollupsInsteadOfTrustingTheFile() {
        val store = corpusStore()
        val cipher = Argon2ExportCipher(iterations = 1, memoryKiB = 8_192, parallelism = 1)
        val service = ExportService(store.driver, store.db, cipher, FixedClock(now))
        val bundle = service.snapshot()
        val rollups = bundle.tables.first { it.table == "daily_rollup" }
        assertTrue(rollups.rows.isNotEmpty())
        val originalValue = rollups.rows.first().getValue("value")
        val corrupted = bundle.copy(
            tables = bundle.tables.map { table ->
                if (table.table != "daily_rollup") {
                    table
                } else {
                    table.copy(
                        rows = table.rows.mapIndexed { index, row ->
                            if (index == 0) row + ("value" to JsonPrimitive(999_999.0)) else row
                        },
                    )
                }
            },
        )
        service.restoreBundle(corrupted)
        val restored = service.snapshot().tables.first { it.table == "daily_rollup" }
            .rows.first().getValue("value")
        assertEquals(originalValue, restored)
        store.close()
    }

    @Test
    fun aDirectRestoreStillChecksFormatAndSchema() {
        val store = corpusStore()
        val cipher = Argon2ExportCipher(iterations = 1, memoryKiB = 8_192, parallelism = 1)
        val service = ExportService(store.driver, store.db, cipher, FixedClock(now))
        val bundle = service.snapshot()
        assertFailsWith<app.openhealth.core.seam.UnsupportedExportFormat> {
            service.restoreBundle(bundle.copy(format = "something-else"))
        }
        assertFailsWith<app.openhealth.core.seam.UnsupportedExportFormat> {
            service.restoreBundle(bundle.copy(schemaVersion = bundle.schemaVersion + 1))
        }
        store.close()
    }

    @Test
    fun theProductionArgon2DefaultsSealAndOpen() {
        val store = inMemoryStore()
        val exporter = ExportService(
            store.driver,
            store.db,
            Argon2ExportCipher(),
            FixedClock(now),
        )
        val container = exporter.export("a real passphrase".toCharArray())
        val bundle = exporter.read(container, "a real passphrase".toCharArray())
        assertEquals(ExportTables.all.size, bundle.tables.size)
        store.close()
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

    private fun semanticTables(
        bundle: ExportBundle,
    ): List<app.openhealth.data.export.ExportTableData> = bundle.tables.map { table ->
        if (table.table != "daily_rollup") {
            table
        } else {
            table.copy(rows = table.rows.map { row -> row.filterKeys { it != "computed_at" } })
        }
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
