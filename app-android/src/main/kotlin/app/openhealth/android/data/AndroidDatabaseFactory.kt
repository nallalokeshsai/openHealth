package app.openhealth.android.data

import android.content.Context
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
        )
        verifyDatabaseIntegrity(driver, databaseName)
        return OpenHealthStore(driver, openHealthDatabase(driver), OpenHealthDb.Schema.version)
    }

    companion object {
        const val DEFAULT_DATABASE = "openhealth.db"
        const val DATABASE_PASSPHRASE = "database.passphrase"
    }
}
