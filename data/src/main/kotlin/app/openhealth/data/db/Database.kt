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
 * Creates a connection to the one database, with foreign keys on or off.
 *
 * Foreign key enforcement is a **connection** setting on every SQLite binding this app
 * uses: `PRAGMA foreign_keys` is silently ignored once a connection is established (the
 * JDBC driver and Android both require it at open time). The platform owns connection
 * creation, so the platform is the only place it can be set, and a migration — which
 * drops and rebuilds referenced tables — needs a connection that has them off.
 */
fun interface DatabaseDriverFactory {
    fun create(foreignKeys: Boolean): SqlDriver
}

/**
 * Opens a database in the right state: creates the schema on a new file, backs up and
 * migrates an older one, refuses a newer one, enables WAL and foreign keys, and runs an
 * integrity check before anything reads (`Tech-Stack.md` §5).
 *
 * An upgrade runs first, on its own connection with foreign keys off, and is proved
 * clean with `PRAGMA foreign_key_check` before the database is opened for use. The
 * connection handed back always enforces foreign keys, and `open` fails rather than
 * return one that does not.
 */
class DatabaseOpener(private val backup: MigrationBackup? = null) {
    fun open(
        factory: DatabaseDriverFactory,
        databaseLabel: String = "openhealth.db",
    ): OpenHealthStore {
        upgradeIfNeeded(factory, databaseLabel)

        val driver = factory.create(foreignKeys = true)
        check(foreignKeysEnforced(driver)) {
            "Database $databaseLabel was opened without foreign key enforcement; the " +
                "driver factory must enable it when it creates the connection"
        }
        val version = readUserVersion(driver)
        val schemaVersion = OpenHealthDb.Schema.version
        if (version == 0L && !hasTables(driver)) create(driver)
        check(readUserVersion(driver) == schemaVersion) {
            "Database $databaseLabel is at version ${readUserVersion(driver)} after the " +
                "upgrade pass; expected $schemaVersion"
        }
        enableWal(driver)
        val integrity = integrityCheck(driver)
        check(integrity == "ok") {
            "Database $databaseLabel failed its integrity check: $integrity"
        }
        return OpenHealthStore(driver, openHealthDatabase(driver), schemaVersion)
    }

    /**
     * Brings an existing database up to the current schema, on a connection whose
     * foreign keys are off, and closes it again. A new database is left to [open].
     */
    private fun upgradeIfNeeded(factory: DatabaseDriverFactory, databaseLabel: String) {
        val driver = factory.create(foreignKeys = false)
        try {
            val version = readUserVersion(driver)
            val schemaVersion = OpenHealthDb.Schema.version
            require(version <= schemaVersion) {
                "Database $databaseLabel is at version $version, newer than this app's " +
                    schemaVersion
            }
            if (version == 0L) {
                check(!hasTables(driver)) {
                    "Database $databaseLabel has tables but no schema version; " +
                        "refusing to guess"
                }
                return
            }
            if (version == schemaVersion) return
            migrate(driver, version, schemaVersion, databaseLabel)
        } finally {
            driver.close()
        }
    }

    private fun create(driver: SqlDriver) {
        OpenHealthDb.Schema.create(driver).value
        setUserVersion(driver, OpenHealthDb.Schema.version)
    }

    private fun migrate(
        driver: SqlDriver,
        fromVersion: Long,
        toVersion: Long,
        databaseLabel: String,
    ) {
        val backup = backup ?: error(
            "Migration $fromVersion -> $toVersion needs a MigrationBackup; refusing to upgrade " +
                "without a restorable snapshot",
        )
        check(backup.snapshot(fromVersion, toVersion) != null) {
            "Migration $fromVersion -> $toVersion needs a backup and the platform could not take one"
        }
        OpenHealthDb.Schema.migrate(driver, fromVersion, toVersion).value
        setUserVersion(driver, toVersion)
        val violations = foreignKeyViolations(driver)
        check(violations == 0L) {
            "Migration $fromVersion -> $toVersion left $violations foreign key violations " +
                "in $databaseLabel; restore the pre-migration backup"
        }
    }

    private fun setUserVersion(driver: SqlDriver, version: Long) {
        driver.execute(null, "PRAGMA user_version = $version", 0)
    }

    private fun enableWal(driver: SqlDriver) {
        driver.execute(null, "PRAGMA journal_mode = WAL", 0)
        driver.execute(null, "PRAGMA synchronous = NORMAL", 0)
    }
}

// Read-only pragmas. They describe the driver rather than the opener, so they sit beside
// it instead of inside it.

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
    sql = "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'",
    mapper = { cursor ->
        QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L)
    },
    parameters = 0,
).value > 0L

/** Reads enforcement back rather than trusting that setting it worked. */
private fun foreignKeysEnforced(driver: SqlDriver): Boolean = driver.executeQuery(
    identifier = null,
    sql = "PRAGMA foreign_keys",
    mapper = { cursor ->
        QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L)
    },
    parameters = 0,
).value == 1L

/** `PRAGMA foreign_key_check` returns one row per violation; zero rows is clean. */
private fun foreignKeyViolations(driver: SqlDriver): Long = driver.executeQuery(
    identifier = null,
    sql = "PRAGMA foreign_key_check",
    mapper = { cursor ->
        var rows = 0L
        while (cursor.next().value) rows++
        QueryResult.Value(rows)
    },
    parameters = 0,
).value

private fun integrityCheck(driver: SqlDriver): String = driver.executeQuery(
    identifier = null,
    sql = "PRAGMA integrity_check",
    mapper = { cursor ->
        QueryResult.Value(if (cursor.next().value) cursor.getString(0) else null)
    },
    parameters = 0,
).value ?: "no result"

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
