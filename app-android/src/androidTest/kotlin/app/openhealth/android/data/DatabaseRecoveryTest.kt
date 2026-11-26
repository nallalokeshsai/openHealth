package app.openhealth.android.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `BUILD-ROADMAP.md` §B10: corrupted-database recovery, proved on the device against a
 * database of its own. It never touches the owner's record: the file has a test-only
 * name, and the snapshot lookup is scoped to that name.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseRecoveryTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "b10-recovery-test.db"

    @After
    fun cleanUp() {
        context.deleteDatabase(name)
        val backups = java.io.File(context.filesDir, "database-backups")
        backups.listFiles()?.filter { it.name.startsWith("$name-v") }?.forEach { it.delete() }
        context.getDatabasePath(name).parentFile?.listFiles()
            ?.filter { it.name.startsWith("$name.corrupt-") || it.name.contains(".restore-") }
            ?.forEach { it.delete() }
    }

    @Test
    fun aCorruptDatabaseIsRestoredFromItsPreMigrationSnapshot() {
        val vault = AndroidSecretVault(context)
        val factory = AndroidDatabaseFactory(context, vault, name)
        factory.open().use { store ->
            store.db.settingsQueries.putSetting(
                key = "probe",
                value = "kept across recovery",
                updatedAt = 1L,
                schemaVersion = 1L,
            )
        }
        assertNotNull(
            "the snapshot must be written",
            AndroidMigrationBackup(context, name).snapshot(1, 2),
        )

        val file = context.getDatabasePath(name)
        assertTrue(file.exists())
        file.writeBytes("this file is not a database".toByteArray())
        listOf("-wal", "-shm").forEach { suffix ->
            java.io.File(file.path + suffix).takeIf { it.exists() }?.delete()
        }

        val recovered = factory.open()
        try {
            assertEquals(
                "kept across recovery",
                recovered.db.settingsQueries.setting("probe").executeAsOne().value_,
            )
            assertNotNull(
                "the recovery is recorded for the Settings screen",
                recovered.db.settingsQueries.setting(
                    AndroidDatabaseFactory.RECOVERED_AT,
                ).executeAsOneOrNull()?.value_,
            )
        } finally {
            recovered.close()
        }
        // Nothing is deleted: the damaged bytes stay beside the database, renamed.
        assertTrue(
            "the damaged database must be kept aside",
            context.getDatabasePath(name).parentFile?.listFiles()
                ?.any { it.name.startsWith("$name.corrupt-") } == true,
        )
    }

    @Test
    fun aSnapshotBelongsToItsOwnDatabase() {
        val other = "b10-other-test.db"
        val directory = java.io.File(context.filesDir, "database-backups").apply { mkdirs() }
        val mine = java.io.File(directory, "$name-v1-v2-100")
        val theirs = java.io.File(directory, "$other-v1-v2-200")
        mine.writeBytes("mine".toByteArray())
        theirs.writeBytes("theirs".toByteArray())
        try {
            val latest = AndroidMigrationBackup(context, name).latest()
            assertEquals(
                "only this database's snapshots may be restored",
                mine.name,
                latest?.name,
            )
        } finally {
            mine.delete()
            theirs.delete()
        }
    }
}
