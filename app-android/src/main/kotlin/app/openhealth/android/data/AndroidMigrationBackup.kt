package app.openhealth.android.data

import android.content.Context
import app.openhealth.data.db.MigrationBackup
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant

/**
 * The automatic local snapshot taken before a schema migration (`pr.md` §6.29). Copies
 * the database and its WAL sidecars into app-private storage. The copy is taken before
 * the migration writes anything, so the three files together are the pre-upgrade state.
 */
class AndroidMigrationBackup(private val context: Context, private val databaseName: String) :
    MigrationBackup {
    override fun snapshot(fromVersion: Long, toVersion: Long): String? {
        val source = context.getDatabasePath(databaseName)
        if (!source.exists()) return null
        val directory = File(context.filesDir, BACKUP_DIRECTORY).apply { mkdirs() }
        val stamp = Instant.now().toEpochMilli()
        val label = "$databaseName-v$fromVersion-v$toVersion-$stamp"
        val target = File(directory, label)
        return try {
            copyIfPresent(source, target)
            copyIfPresent(File(source.path + "-wal"), File(target.path + "-wal"))
            copyIfPresent(File(source.path + "-shm"), File(target.path + "-shm"))
            label
        } catch (failure: IOException) {
            android.util.Log.w(TAG, "Could not snapshot the database before migrating", failure)
            null
        }
    }

    private fun copyIfPresent(source: File, target: File) {
        if (!source.exists()) return
        Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private companion object {
        const val BACKUP_DIRECTORY = "database-backups"
        const val TAG = "openhealth.backup"
    }
}
