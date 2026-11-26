package app.openhealth.android.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.openhealth.data.db.BackupBeforeMigrationSchema
import app.openhealth.data.db.OpenHealthDb
import app.openhealth.data.db.OpenHealthStore
import app.openhealth.data.db.openHealthDatabase
import app.openhealth.data.db.verifyDatabaseIntegrity
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * The encrypted database, opened behind the seam `:core` declares. SQLCipher's passphrase
 * comes from the Keystore-held secret; the schema wrapper takes a snapshot before any
 * migration; an integrity check runs before the first read. Nothing in `:data` or
 * `:core` knows SQLCipher exists (`Tech-Stack.md` §3, §5).
 */
class AndroidDatabaseFactory(
    private val context: Context,
    private val vault: AndroidSecretVault,
    private val databaseName: String = DEFAULT_DATABASE,
) {
    private val backup = AndroidMigrationBackup(context, databaseName)

    /**
     * Opens the record. If the file is corrupt, the newest pre-migration snapshot is
     * put back and the database is opened again (`BUILD-ROADMAP.md` §B10). That is the
     * one automatic destructive step the app takes, and it is taken only because the
     * alternative is a record that cannot open at all; the restore is recorded so the
     * person can see it happened.
     */
    @Suppress("TooGenericExceptionCaught")
    fun open(): OpenHealthStore = try {
        openOnce()
    } catch (failure: RuntimeException) {
        if (!isCorruption(failure) || backup.restoreLatest() == null) throw failure
        android.util.Log.w(TAG, "Database $databaseName was corrupt; restored the snapshot")
        openOnce().also { recovered ->
            recovered.db.settingsQueries.putSetting(
                key = RECOVERED_AT,
                value = java.time.Instant.now().toString(),
                updatedAt = System.currentTimeMillis(),
                schemaVersion = OpenHealthDb.Schema.version,
            )
        }
    }

    @Suppress("TooGenericExceptionCaught") // Any failure here must close the driver.
    private fun openOnce(): OpenHealthStore {
        // SQLCipher ships its native library in the AAR and does not load it for you.
        System.loadLibrary("sqlcipher")
        val passphrase = vault.getOrCreate(DATABASE_PASSPHRASE)
        val schema = BackupBeforeMigrationSchema(OpenHealthDb.Schema, backup)
        val driver = AndroidSqliteDriver(
            schema = schema,
            context = context,
            name = databaseName,
            factory = SupportOpenHelperFactory(passphrase),
            callback = ForeignKeyCallback(schema),
        )
        // A PRAGMA that returns a row cannot go through SqlDriver.execute on Android.
        driver.executeQuery(
            identifier = null,
            sql = "PRAGMA journal_mode = WAL",
            mapper = { cursor ->
                cursor.next()
                app.cash.sqldelight.db.QueryResult.Value(Unit)
            },
            parameters = 0,
        )
        try {
            verifyDatabaseIntegrity(driver, databaseName)
        } catch (failure: RuntimeException) {
            // The connection holds native handles on the file the recovery is about to
            // replace; close it before the corruption handler touches the path.
            runCatching { driver.close() }
            throw failure
        }
        return OpenHealthStore(driver, openHealthDatabase(driver), OpenHealthDb.Schema.version)
    }

    /**
     * Foreign key enforcement is a property of the open database, not a statement you can
     * send down an open connection: `PRAGMA foreign_keys` reaches one pooled connection at
     * best, and is ignored inside a transaction. `setForeignKeyConstraintsEnabled` applies
     * to every connection in the pool, so it is the only reliable switch here.
     *
     * It is off while the schema is created or migrated — a migration rebuilds tables that
     * other tables reference — and on for every read and write afterwards. `onOpen` runs
     * after `onCreate`/`onUpgrade` and outside their transaction, which is where the call
     * is allowed.
     */
    private class ForeignKeyCallback(
        schema: app.cash.sqldelight.db.SqlSchema<
            app.cash.sqldelight.db.QueryResult.Value<Unit>,
            >,
    ) : AndroidSqliteDriver.Callback(schema) {
        override fun onConfigure(db: SupportSQLiteDatabase) {
            db.setForeignKeyConstraintsEnabled(false)
            super.onConfigure(db)
        }

        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            db.setForeignKeyConstraintsEnabled(true)
        }
    }

    /** True for the failures that mean "this file is not a usable database". */
    private fun isCorruption(failure: RuntimeException): Boolean {
        if (failure is android.database.sqlite.SQLiteException) {
            val message = failure.message.orEmpty().lowercase()
            return "corrupt" in message ||
                "not a database" in message ||
                "encrypted" in message ||
                "malformed" in message
        }
        return failure is IllegalStateException &&
            failure.message.orEmpty().contains("failed its integrity check")
    }

    companion object {
        const val DEFAULT_DATABASE = "openhealth.db"
        const val DATABASE_PASSPHRASE = "database.passphrase"

        /** Set when a corrupt database was replaced from a snapshot, so Settings can say so. */
        const val RECOVERED_AT = "recovery.restored_at"
        private const val TAG = "openhealth.database"
    }
}
