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
    fun open(): OpenHealthStore {
        // SQLCipher ships its native library in the AAR and does not load it for you.
        System.loadLibrary("sqlcipher")
        val passphrase = vault.getOrCreate(DATABASE_PASSPHRASE)
        val schema = BackupBeforeMigrationSchema(
            OpenHealthDb.Schema,
            AndroidMigrationBackup(context, databaseName),
        )
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
        verifyDatabaseIntegrity(driver, databaseName)
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

    companion object {
        const val DEFAULT_DATABASE = "openhealth.db"
        const val DATABASE_PASSPHRASE = "database.passphrase"
    }
}
