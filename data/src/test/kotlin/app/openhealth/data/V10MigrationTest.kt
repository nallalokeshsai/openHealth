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
 * v10 → v11 (`BUILD-ROADMAP.md` §B13). A v10 record has no change log, no per-row merge
 * state and no conflict queue. The upgrade adds them and keeps every existing row.
 */
class V10MigrationTest {
    private fun open(path: String) =
        DatabaseOpener(MigrationBackup { from, to -> "backup-$from-$to" })
            .open(jdbcFactory("jdbc:sqlite:$path"))

    private fun setupV10(path: String) {
        val driver = JdbcSqliteDriver("jdbc:sqlite:$path", Properties())
        OpenHealthDb.Schema.create(driver).value
        driver.execute(null, "ALTER TABLE source DROP COLUMN via_health_connect", 0)
        listOf(
            "change_log", "sync_row_state", "sync_conflict",
            "coach_message", "coach_proposal", "coach_action", "coach_snapshot",
            "mcp_token", "mcp_access",
        ).forEach { table ->
            driver.execute(null, "DROP TABLE IF EXISTS $table", 0)
        }
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
                "('n1','Before sync','2026-11-19','Europe/Berlin',0,0,'manual','n1',1)",
            0,
        )
        driver.execute(null, "PRAGMA user_version = 10", 0)
        driver.close()
    }

    @Test
    fun aV10RecordKeepsItsRowsAndGainsTheSyncTables() {
        val directory = Files.createTempDirectory("openhealth-v10-sync")
        val path = directory.resolve("v10.db").toString()
        setupV10(path)

        val store = open(path)
        assertEquals(12L, store.schemaVersion)
        assertEquals("Before sync", store.db.noteQueries.noteById("n1").executeAsOne().body)
        listOf(
            "change_log", "sync_row_state", "sync_conflict",
            "coach_message", "coach_proposal", "coach_action", "coach_snapshot",
            "mcp_token", "mcp_access",
        ).forEach { table ->
            assertTrue(tableExists(store.driver, table), "$table was not created")
        }
        assertEquals(0L, store.db.syncQueries.pendingChangeCount().executeAsOne())
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
}
