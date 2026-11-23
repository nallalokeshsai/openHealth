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
        assertEquals(2L, store.schemaVersion)
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
        V2_INDEXES.forEach { first.execute(null, "DROP INDEX $it", 0) }
        first.execute(null, "PRAGMA user_version = 1", 0)
        first.close()

        var backupLabel: String? = null
        val backup = MigrationBackup { from, to ->
            backupLabel = "v$from-to-v$to"
            "backup-$from-$to"
        }
        val store = DatabaseOpener(backup).open(
            JdbcSqliteDriver("jdbc:sqlite:$path", Properties()),
        )
        assertEquals("v1-to-v2", backupLabel)
        assertEquals(2L, store.schemaVersion)
        assertEquals(2L, userVersion(store.driver))
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
    fun aNewerDatabaseIsRefused() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        openHealthDatabase(driver)
        driver.execute(null, "PRAGMA user_version = 99", 0)
        assertFailsWith<IllegalArgumentException> { DatabaseOpener().open(driver) }
    }

    @Test
    fun anUnversionedDatabaseWithTablesIsRefused() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        OpenHealthDb.Schema.create(driver).value
        driver.execute(null, "PRAGMA user_version = 0", 0)
        assertFailsWith<IllegalStateException> { DatabaseOpener().open(driver) }
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
