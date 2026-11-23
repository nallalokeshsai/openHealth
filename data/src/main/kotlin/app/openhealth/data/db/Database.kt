package app.openhealth.data.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver

/** Builds the generated database. Boolean columns are declared `kotlin.Boolean`. */
fun openHealthDatabase(driver: SqlDriver): OpenHealthDb = OpenHealthDb(driver)

/** A snapshot taken immediately before a forward migration (`pr.md` §6.29, §7.9). */
fun interface MigrationBackup {
    /**
     * Copies the database (and its WAL) to a restorable snapshot. Returns the label a
     * settings screen can show, or null when the platform could not copy it.
     */
    fun snapshot(fromVersion: Long, toVersion: Long): String?
}

/** One open database: the generated queries plus the schema version it is running. */
class OpenHealthStore(val driver: SqlDriver, val db: OpenHealthDb, val schemaVersion: Long) :
    AutoCloseable {
    override fun close() {
        driver.close()
    }
}

/**
 * Opens a database in the right state: creates the schema on a new file, backs up and
 * migrates an older one, refuses a newer one, enables WAL and foreign keys, and runs an
 * integrity check before anything reads (`Tech-Stack.md` §5).
 */
class DatabaseOpener(private val backup: MigrationBackup? = null) {
    fun open(driver: SqlDriver, databaseLabel: String = "openhealth.db"): OpenHealthStore {
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        val version = readUserVersion(driver)
        val schemaVersion = OpenHealthDb.Schema.version
        require(version <= schemaVersion) {
            "Database $databaseLabel is at version $version, newer than this app's $schemaVersion"
        }
        when {
            version == 0L && !hasTables(driver) -> create(driver)
            version == 0L -> error(
                "Database $databaseLabel has tables but no schema version; refusing to guess",
            )

            version < schemaVersion -> migrate(driver, version, schemaVersion)
        }
        enableWal(driver)
        val integrity = integrityCheck(driver)
        check(integrity == "ok") {
            "Database $databaseLabel failed its integrity check: $integrity"
        }
        return OpenHealthStore(driver, openHealthDatabase(driver), schemaVersion)
    }

    private fun create(driver: SqlDriver) {
        OpenHealthDb.Schema.create(driver).value
        setUserVersion(driver, OpenHealthDb.Schema.version)
    }

    private fun migrate(driver: SqlDriver, fromVersion: Long, toVersion: Long) {
        val label = backup?.snapshot(fromVersion, toVersion)
        check(backup == null || label != null) {
            "Migration $fromVersion -> $toVersion needs a backup and the platform could not take one"
        }
        OpenHealthDb.Schema.migrate(driver, fromVersion, toVersion).value
        setUserVersion(driver, toVersion)
    }

    private fun readUserVersion(driver: SqlDriver): Long = driver.executeQuery(
        identifier = null,
        sql = "PRAGMA user_version",
        mapper = { cursor ->
            QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L)
        },
        parameters = 0,
    ).value

    private fun hasTables(driver: SqlDriver): Boolean = driver.executeQuery(
        identifier = null,
        sql = "SELECT count(*) FROM sqlite_master " +
            "WHERE type = 'table' AND name NOT LIKE 'sqlite_%'",
        mapper = { cursor ->
            QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L)
        },
        parameters = 0,
    ).value > 0L

    private fun setUserVersion(driver: SqlDriver, version: Long) {
        driver.execute(null, "PRAGMA user_version = $version", 0)
    }

    private fun enableWal(driver: SqlDriver) {
        driver.execute(null, "PRAGMA journal_mode = WAL", 0)
        driver.execute(null, "PRAGMA synchronous = NORMAL", 0)
    }

    private fun integrityCheck(driver: SqlDriver): String = driver.executeQuery(
        identifier = null,
        sql = "PRAGMA integrity_check",
        mapper = { cursor ->
            QueryResult.Value(if (cursor.next().value) cursor.getString(0) else null)
        },
        parameters = 0,
    ).value ?: "no result"
}

/** Runs the integrity check on an already-open driver; throws on anything but "ok". */
fun verifyDatabaseIntegrity(driver: SqlDriver, databaseLabel: String) {
    val integrity = driver.executeQuery(
        identifier = null,
        sql = "PRAGMA integrity_check",
        mapper = { cursor ->
            QueryResult.Value(if (cursor.next().value) cursor.getString(0) else null)
        },
        parameters = 0,
    ).value ?: "no result"
    check(integrity == "ok") { "Database $databaseLabel failed its integrity check: $integrity" }
}

/** Reads back the journal mode; tests use it to prove WAL is really on. */
fun journalMode(driver: SqlDriver): String = driver.executeQuery(
    identifier = null,
    sql = "PRAGMA journal_mode",
    mapper = { cursor ->
        QueryResult.Value(if (cursor.next().value) cursor.getString(0) else null)
    },
    parameters = 0,
).value ?: "unknown"
