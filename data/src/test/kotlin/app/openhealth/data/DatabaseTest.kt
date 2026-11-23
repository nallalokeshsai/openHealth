package app.openhealth.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.openhealth.data.db.DatabaseOpener
import app.openhealth.data.db.MigrationBackup
import app.openhealth.data.db.OpenHealthDb
import app.openhealth.data.db.journalMode
import app.openhealth.data.db.openHealthDatabase
import java.nio.file.Files
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DatabaseTest {
    @Test
    fun aNewDatabaseIsCreatedAtTheCurrentSchemaVersion() {
        val store = inMemoryStore()
        assertEquals(OpenHealthDb.Schema.version, store.schemaVersion)
        assertEquals(4L, store.schemaVersion)
        assertEquals(0L, store.db.observationQueries.countObservations().executeAsOne())
        assertEquals(0L, store.db.trainingQueries.countWorkSets().executeAsOne())
        store.close()
    }

    @Test
    fun aFileDatabaseRunsInWalMode() {
        val directory = Files.createTempDirectory("openhealth-db")
        val path = directory.resolve("openhealth.db").toString()
        val store = fileStore(path)
        assertEquals("wal", journalMode(store.driver).lowercase())
        store.close()
        Files.deleteIfExists(java.nio.file.Path.of(path))
        Files.deleteIfExists(java.nio.file.Path.of("$path-shm"))
        Files.deleteIfExists(java.nio.file.Path.of("$path-wal"))
        Files.deleteIfExists(directory)
    }

    @Test
    fun anOlderDatabaseIsBackedUpThenMigrated() {
        val directory = Files.createTempDirectory("openhealth-migrate")
        val path = directory.resolve("v1.db").toString()
        // v1 never shipped; it is exactly the v2 schema without the six indexes that
        // 1.sqm adds, so dropping them reproduces the pre-migration state faithfully.
        val first = JdbcSqliteDriver("jdbc:sqlite:$path", Properties())
        OpenHealthDb.Schema.create(first).value
        // The note table arrives in v4; this fixture must really be older than it.
        first.execute(null, "DROP TABLE note", 0)
        V2_INDEXES.forEach { first.execute(null, "DROP INDEX $it", 0) }
        first.execute(null, "PRAGMA user_version = 1", 0)
        first.close()

        var backupLabel: String? = null
        val backup = MigrationBackup { from, to ->
            backupLabel = "v$from-to-v$to"
            "backup-$from-$to"
        }
        val store = DatabaseOpener(backup).open(
            jdbcFactory("jdbc:sqlite:$path"),
        )
        assertEquals("v1-to-v4", backupLabel)
        assertEquals(4L, store.schemaVersion)
        assertEquals(4L, userVersion(store.driver))
        V2_INDEXES.forEach { assertEquals(1L, indexCount(store.driver, it), "index $it") }
        store.close()
        Files.deleteIfExists(java.nio.file.Path.of(path))
        Files.deleteIfExists(directory)
    }

    @Test
    fun everyStoredTableIsInTheExportRegistry() {
        val store = inMemoryStore()
        val tables = store.db.transactionWithResult {
            store.driver.executeQuery(
                null,
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'",
                { cursor ->
                    val names = mutableListOf<String>()
                    while (cursor.next().value) names += cursor.getString(0).orEmpty()
                    QueryResult.Value(names)
                },
                0,
            ).value
        }
        val exported = app.openhealth.data.export.ExportTables.all.map { it.name }.toSet()
        assertEquals(
            emptySet<String>(),
            tables.toSet() - exported,
            "a table is missing from ExportTables and would be silently dropped from an export",
        )
        assertEquals(tables.toSet(), exported)
        store.close()
    }

    @Test
    fun theExportRegistryMatchesEveryTableAndColumn() {
        val store = inMemoryStore()
        app.openhealth.data.export.ExportTables.all.forEach { table ->
            val columns = store.db.transactionWithResult {
                store.driver.executeQuery(
                    null,
                    "PRAGMA table_info(${table.name})",
                    { cursor ->
                        val names = mutableListOf<String>()
                        while (cursor.next().value) names += cursor.getString(1).orEmpty()
                        QueryResult.Value(names)
                    },
                    0,
                ).value
            }
            assertEquals(
                columns.toSet(),
                table.columns.map { it.name }.toSet(),
                "columns of ${table.name}",
            )
        }
        store.close()
    }

    @Test
    fun aMigrationWithoutABackupIsRefused() {
        val directory = Files.createTempDirectory("openhealth-nobackup")
        val path = directory.resolve("v1.db").toString()
        val first = JdbcSqliteDriver("jdbc:sqlite:$path", Properties())
        OpenHealthDb.Schema.create(first).value
        // The note table arrives in v4; this fixture must really be older than it.
        first.execute(null, "DROP TABLE note", 0)
        V2_INDEXES.forEach { first.execute(null, "DROP INDEX $it", 0) }
        first.execute(null, "PRAGMA user_version = 1", 0)
        first.close()
        assertFailsWith<IllegalStateException> {
            DatabaseOpener().open(jdbcFactory("jdbc:sqlite:$path"))
        }
        Files.deleteIfExists(java.nio.file.Path.of(path))
        Files.deleteIfExists(directory)
    }

    @Test
    fun aFailedSnapshotRefusesTheMigration() {
        val directory = Files.createTempDirectory("openhealth-badsnapshot")
        val path = directory.resolve("v1.db").toString()
        val first = JdbcSqliteDriver("jdbc:sqlite:$path", Properties())
        OpenHealthDb.Schema.create(first).value
        // The note table arrives in v4; this fixture must really be older than it.
        first.execute(null, "DROP TABLE note", 0)
        V2_INDEXES.forEach { first.execute(null, "DROP INDEX $it", 0) }
        first.execute(null, "PRAGMA user_version = 1", 0)
        first.close()
        assertFailsWith<IllegalStateException> {
            DatabaseOpener(MigrationBackup { _, _ -> null })
                .open(jdbcFactory("jdbc:sqlite:$path"))
        }
        Files.deleteIfExists(java.nio.file.Path.of(path))
        Files.deleteIfExists(directory)
    }

    @Test
    fun aNewerDatabaseIsRefused() {
        val directory = Files.createTempDirectory("openhealth-newer")
        val path = directory.resolve("newer.db").toString()
        val first = JdbcSqliteDriver("jdbc:sqlite:$path", Properties())
        OpenHealthDb.Schema.create(first).value
        first.execute(null, "PRAGMA user_version = 99", 0)
        first.close()
        assertFailsWith<IllegalArgumentException> {
            DatabaseOpener().open(jdbcFactory("jdbc:sqlite:$path"))
        }
        Files.deleteIfExists(java.nio.file.Path.of(path))
        Files.deleteIfExists(directory)
    }

    @Test
    fun anUnversionedDatabaseWithTablesIsRefused() {
        val directory = Files.createTempDirectory("openhealth-unversioned")
        val path = directory.resolve("unversioned.db").toString()
        val first = JdbcSqliteDriver("jdbc:sqlite:$path", Properties())
        OpenHealthDb.Schema.create(first).value
        first.execute(null, "PRAGMA user_version = 0", 0)
        first.close()
        assertFailsWith<IllegalStateException> {
            DatabaseOpener().open(jdbcFactory("jdbc:sqlite:$path"))
        }
        Files.deleteIfExists(java.nio.file.Path.of(path))
        Files.deleteIfExists(directory)
    }

    @Test
    fun aV2DatabaseGainsItsForeignKeysAndKeepsEveryRow() {
        val directory = Files.createTempDirectory("openhealth-v2")
        val path = directory.resolve("v2.db").toString()

        // v2 is this schema without the REFERENCES clauses, so strip them from the stored
        // DDL and rebuild. The fixture therefore tracks the schema instead of drifting.
        val first = JdbcSqliteDriver("jdbc:sqlite:$path", Properties())
        OpenHealthDb.Schema.create(first).value
        // The note table arrives in v4; this fixture must really be older than it.
        first.execute(null, "DROP TABLE note", 0)
        val ddl = tableDdl(first)
        first.execute(null, "PRAGMA foreign_keys = OFF", 0)
        ddl.forEach { (table, sql) ->
            if (!sql.contains("REFERENCES")) return@forEach
            first.execute(null, "CREATE TABLE copy_$table AS SELECT * FROM $table", 0)
            first.execute(null, "DROP TABLE $table", 0)
            first.execute(null, REFERENCES.replace(sql, ""), 0)
            first.execute(null, "INSERT INTO $table SELECT * FROM copy_$table", 0)
            first.execute(null, "DROP TABLE copy_$table", 0)
        }
        assertEquals(
            0,
            tableDdl(first).filterKeys { !it.startsWith("copy_") }
                .count { it.value.contains("REFERENCES") },
        )

        // One row per layer, including a child that the v3 keys will have to accept.
        val db = openHealthDatabase(first)
        seedSources(app.openhealth.data.db.OpenHealthStore(first, db, 2L))
        db.observationQueries.insertObservation(
            id = "o-1", type = "STEPS", value = 8000.0, unit = "{step}",
            startAt = 0L, endAt = 86_400_000L, localDate = "2026-01-01", zoneId = TEST_ZONE.id,
            happenedAt = 0L, recordedAt = 1L, sourceId = "garmin", device = null,
            appVersion = null, method = null, context = null, confidence = null,
            quality = null, userVerified = false, userEdited = false, originalValue = null,
            originalUnit = null, editReason = null, editedAt = null, groupKey = null,
            excludedFromTrends = false, idempotencyKey = "k-1", rawRecordId = null,
        )
        first.execute(null, "PRAGMA user_version = 2", 0)
        first.close()

        val store = DatabaseOpener(MigrationBackup { from, to -> "backup-$from-$to" }).open(
            jdbcFactory("jdbc:sqlite:$path"),
        )
        assertEquals(4L, store.schemaVersion)
        assertEquals(1L, store.db.observationQueries.countObservations().executeAsOne())
        assertEquals(3, store.db.sourceQueries.allSources().executeAsList().size)

        // The keys are declared, and they bite: an observation may not name a source
        // that does not exist, and a source with history may not be deleted.
        val withKeys = tableDdl(store.driver)
        assertTrue(withKeys.getValue("observation").contains("REFERENCES source"))
        assertTrue(withKeys.getValue("work_set").contains("REFERENCES session"))
        assertFailsWith<Exception> {
            store.driver.execute(
                null,
                "INSERT INTO observation(id, type, value, unit, start_at, local_date, " +
                    "zone_id, happened_at, recorded_at, source_id, idempotency_key) VALUES " +
                    "('o-2','STEPS',1.0,'{step}',0,'2026-01-01','UTC',0,0,'ghost','k-2')",
                0,
            )
        }
        assertFailsWith<Exception> {
            store.driver.execute(null, "DELETE FROM source WHERE id = 'garmin'", 0)
        }
        store.close()
        Files.deleteIfExists(java.nio.file.Path.of(path))
        Files.deleteIfExists(java.nio.file.Path.of("$path-shm"))
        Files.deleteIfExists(java.nio.file.Path.of("$path-wal"))
        Files.deleteIfExists(directory)
    }

    @Test
    fun theSchemaExposesEveryLayer() {
        val store = inMemoryStore()
        val tables = store.db.transactionWithResult {
            store.driver.executeQuery(
                null,
                "SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name",
                { cursor ->
                    val names = mutableListOf<String>()
                    while (cursor.next().value) names += cursor.getString(0).orEmpty()
                    QueryResult.Value(names)
                },
                0,
            ).value
        }
        listOf(
            "raw_record", "observation", "daily_rollup", "profile", "source", "settings",
            "tombstone", "exercise", "routine", "routine_exercise", "programme",
            "programme_routine", "session", "session_exercise", "work_set", "food",
            "meal", "entry", "measurement", "vital",
        ).forEach { assertTrue(it in tables, "missing table $it") }
        val views = store.db.transactionWithResult {
            store.driver.executeQuery(
                null,
                "SELECT name FROM sqlite_master WHERE type = 'view' ORDER BY name",
                { cursor ->
                    val names = mutableListOf<String>()
                    while (cursor.next().value) names += cursor.getString(0).orEmpty()
                    QueryResult.Value(names)
                },
                0,
            ).value
        }
        assertTrue("timeline" in views)
        store.close()
    }

    /** Every stored table's CREATE statement, by table name. */
    private fun tableDdl(driver: app.cash.sqldelight.db.SqlDriver): Map<String, String> =
        driver.executeQuery(
            null,
            "SELECT name, sql FROM sqlite_master WHERE type = 'table' " +
                "AND name NOT LIKE 'sqlite_%' AND sql IS NOT NULL",
            { cursor ->
                val rows = mutableMapOf<String, String>()
                while (cursor.next().value) {
                    rows[cursor.getString(0).orEmpty()] = cursor.getString(1).orEmpty()
                }
                QueryResult.Value(rows)
            },
            0,
        ).value

    private fun userVersion(driver: app.cash.sqldelight.db.SqlDriver): Long = driver.executeQuery(
        null,
        "PRAGMA user_version",
        { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L) },
        0,
    ).value

    private fun indexCount(driver: app.cash.sqldelight.db.SqlDriver, name: String): Long =
        driver.executeQuery(
            null,
            "SELECT count(*) FROM sqlite_master WHERE type = 'index' AND name = '$name'",
            { cursor ->
                QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L)
            },
            0,
        ).value

    private companion object {
        /** The inline foreign key clause this schema adds at v3. */
        val REFERENCES = Regex(""" REFERENCES \w+\(\w+\)( ON DELETE (RESTRICT|SET NULL))?""")

        val V2_INDEXES = listOf(
            "observation_type_local_date",
            "observation_happened",
            "session_happened",
            "entry_happened",
            "measurement_happened",
            "vital_happened",
        )
    }
}
