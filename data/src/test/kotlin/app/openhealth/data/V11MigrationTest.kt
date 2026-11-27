package app.openhealth.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.openhealth.data.db.DatabaseOpener
import app.openhealth.data.db.MigrationBackup
import app.openhealth.data.db.OpenHealthDb
import java.nio.file.Files
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * v11 → v12 (`BUILD-ROADMAP.md` §B15). A v11 record has no coach tables, no MCP token
 * table and no transport flag on a source; the upgrade adds them and keeps every row.
 */
class V11MigrationTest {
    private fun open(path: String) =
        DatabaseOpener(MigrationBackup { from, to -> "backup-$from-$to" })
            .open(jdbcFactory("jdbc:sqlite:$path"))

    private fun setupV11(path: String) {
        val driver = JdbcSqliteDriver("jdbc:sqlite:$path", Properties())
        OpenHealthDb.Schema.create(driver).value
        driver.execute(null, "ALTER TABLE source DROP COLUMN via_health_connect", 0)
        listOf(
            "coach_snapshot", "coach_action", "coach_proposal", "coach_message",
            "mcp_access", "mcp_token",
        ).forEach { driver.execute(null, "DROP TABLE IF EXISTS $it", 0) }
        driver.execute(
            null,
            "INSERT INTO source(id, kind, label, family, created_at, updated_at) VALUES " +
                "('manual','MANUAL','Manual entry','manual',0,0)",
            0,
        )
        driver.execute(
            null,
            "INSERT INTO note(id, body, local_date, zone_id, happened_at, recorded_at, " +
                "source_id, idempotency_key, revision) VALUES " +
                "('n1','Before the coach','2026-11-19','Europe/Berlin',0,0,'manual','n1',1)",
            0,
        )
        driver.execute(null, "PRAGMA user_version = 11", 0)
        driver.close()
    }

    @Test
    fun aV11RecordKeepsItsRowsAndGainsTheCoachAndMcpTables() {
        val directory = Files.createTempDirectory("openhealth-v11-coach")
        val path = directory.resolve("v11.db").toString()
        setupV11(path)

        val store = open(path)
        assertEquals(12L, store.schemaVersion)
        assertEquals("Before the coach", store.db.noteQueries.noteById("n1").executeAsOne().body)
        listOf(
            "coach_message", "coach_proposal", "coach_action", "coach_snapshot",
            "mcp_token", "mcp_access",
        ).forEach { table ->
            assertTrue(tableExists(store.driver, table), "$table was not created")
        }
        assertTrue(
            columnExists(store.driver, "source", "via_health_connect"),
            "the source transport flag was not added",
        )
        assertEquals(
            false,
            app.openhealth.data.repo.SourceRepository(store.db)
                .byId(app.openhealth.core.ids.SourceId("manual"))!!.viaHealthConnect,
        )
        store.close()
    }

    private fun tableExists(driver: app.cash.sqldelight.db.SqlDriver, table: String): Boolean =
        driver.executeQuery(
            identifier = null,
            sql = "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = ?",
            mapper = { cursor ->
                cursor.next()
                QueryResult.Value((cursor.getLong(0) ?: 0L) > 0L)
            },
            parameters = 1,
        ) { bindString(0, table) }.value

    private fun columnExists(
        driver: app.cash.sqldelight.db.SqlDriver,
        table: String,
        column: String,
    ): Boolean = driver.executeQuery(
        identifier = null,
        sql = "SELECT count(*) FROM pragma_table_info(?) WHERE name = ?",
        mapper = { cursor ->
            cursor.next()
            QueryResult.Value((cursor.getLong(0) ?: 0L) > 0L)
        },
        parameters = 2,
    ) { bindString(0, table); bindString(1, column) }.value
}
