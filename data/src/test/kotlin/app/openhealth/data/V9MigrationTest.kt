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
 * v9 → v10 (`BUILD-ROADMAP.md` §B12). A v9 record has no clinical tables and a timeline
 * view without the four clinical events. The upgrade adds the tables, rebuilds the view
 * and keeps every existing row.
 */
class V9MigrationTest {
    private fun open(path: String) =
        DatabaseOpener(MigrationBackup { from, to -> "backup-$from-$to" })
            .open(jdbcFactory("jdbc:sqlite:$path"))

    private fun setupV9(path: String) {
        val driver = JdbcSqliteDriver("jdbc:sqlite:$path", Properties())
        OpenHealthDb.Schema.create(driver).value
        // The tables 9.sqm introduces: a v9 record has none of them.
        listOf(
            "medication",
            "medication_dose",
            "symptom",
            "symptom_episode",
            "lab_result",
            "allergy",
            "condition",
            "vaccination",
        ).forEach { table -> driver.execute(null, "DROP TABLE IF EXISTS $table", 0) }
        // The v9 timeline view predates the clinical branches.
        driver.execute(null, "DROP VIEW IF EXISTS timeline", 0)
        driver.execute(null, V9_TIMELINE, 0)
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
                "('n1','Before the upgrade','2026-11-19','Europe/Berlin',0,0,'manual','n1',1)",
            0,
        )
        driver.execute(null, "PRAGMA user_version = 9", 0)
        driver.close()
    }

    @Test
    fun aV9RecordKeepsItsRowsAndGainsTheClinicalTables() {
        val directory = Files.createTempDirectory("openhealth-v9-clinical")
        val path = directory.resolve("v9.db").toString()
        setupV9(path)

        val store = open(path)
        assertEquals(11L, store.schemaVersion)
        assertEquals(
            "Before the upgrade",
            store.db.noteQueries.noteById("n1").executeAsOne().body,
        )
        listOf(
            "medication",
            "medication_dose",
            "symptom",
            "symptom_episode",
            "lab_result",
            "allergy",
            "condition",
            "vaccination",
        ).forEach { table ->
            assertTrue(tableExists(store.driver, table), "$table was not created")
        }
        store.close()
        Files.deleteIfExists(java.nio.file.Path.of(path))
        Files.deleteIfExists(directory)
    }

    @Test
    fun theUpgradedTimelineViewIncludesTheClinicalEvents() {
        val directory = Files.createTempDirectory("openhealth-v9-view")
        val path = directory.resolve("v9.db").toString()
        setupV9(path)

        val store = open(path)
        store.db.clinicalQueries.insertVaccination(
            id = "vacc-1",
            name = "Influenza",
            doseLabel = null,
            administeredOn = "2026-11-20",
            notes = null,
            localDate = "2026-11-20",
            zoneId = "Europe/Berlin",
            happenedAt = 0,
            recordedAt = 0,
            sourceId = "manual",
            rawRecordId = null,
            idempotencyKey = "vacc-1",
            device = null,
            appVersion = null,
            revision = 1,
        )
        val kinds = store.db.timelineQueries
            .timelineForDay("2026-11-20")
            .executeAsList()
            .map { it.kind }
        assertTrue("vaccination" in kinds, "$kinds")
        val sql = store.driver.executeQuery(
            identifier = null,
            sql = "SELECT count(*) FROM timeline WHERE kind = 'medication_dose'",
            mapper = { cursor ->
                cursor.next()
                QueryResult.Value(cursor.getLong(0) ?: 0L)
            },
            parameters = 0,
        ).value
        assertEquals(0L, sql)
        store.close()
        Files.deleteIfExists(java.nio.file.Path.of(path))
        Files.deleteIfExists(directory)
    }

    private fun tableExists(driver: app.cash.sqldelight.db.SqlDriver, name: String): Boolean =
        driver.executeQuery(
            identifier = null,
            sql = "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = ?",
            mapper = { cursor ->
                cursor.next()
                QueryResult.Value((cursor.getLong(0) ?: 0L) > 0L)
            },
            parameters = 1,
        ) { bindString(0, name) }.value

    private companion object {
        /** The view as 8.sqm created it: no clinical branches. */
        val V9_TIMELINE = """
            CREATE VIEW timeline AS
            SELECT 'observation' AS kind, id AS entity_id, local_date AS local_date,
                happened_at AS happened_at, recorded_at AS recorded_at,
                source_id AS source_id, type AS detail, user_edited AS edited
            FROM observation
            UNION ALL
            SELECT 'session', id, local_date, happened_at, recorded_at, source_id, routine_id, 0
            FROM session
            UNION ALL
            SELECT 'work_set', work_set.id, session.local_date, work_set.happened_at,
                work_set.recorded_at, work_set.source_id, work_set.exercise_id, 0
            FROM work_set JOIN session ON session.id = work_set.session_id
            UNION ALL
            SELECT 'entry', id, local_date, happened_at, recorded_at, source_kind, meal_id,
                user_edited FROM entry
            UNION ALL
            SELECT 'measurement', id, local_date, happened_at, recorded_at, source_id, type, 0
            FROM measurement
            UNION ALL
            SELECT 'vital', id, local_date, happened_at, recorded_at, source_id, type, 0
            FROM vital
            UNION ALL
            SELECT 'body_photo', id, local_date, happened_at, recorded_at, source_id, NULL, 0
            FROM body_photo
            UNION ALL
            SELECT 'meal', id, local_date, COALESCE(happened_at, created_at),
                COALESCE(recorded_at, created_at), COALESCE(source_id, 'manual'), kind, 0
            FROM meal
            UNION ALL
            SELECT 'fast', id, local_date, start_at, recorded_at, 'manual', NULL, 0
            FROM fast_window
            UNION ALL
            SELECT 'activity', id, local_date, happened_at, recorded_at, source_id, type, 0
            FROM activity
            UNION ALL
            SELECT 'note', id, local_date, happened_at, recorded_at, source_id, NULL, 0
            FROM note
        """.trimIndent()
    }
}
