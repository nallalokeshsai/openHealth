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

    /** The newest snapshot, or null when no migration has ever run on this install. */
    fun latest(): File? = directory()?.listFiles()
        // Only this database's snapshots: the directory is shared by every install of
        // the app, and a test database must never restore the owner's record.
        ?.filter { it.isFile && it.name.startsWith("$databaseName-v") }
        ?.maxByOrNull { it.lastModified() }

    /**
     * Puts the newest snapshot back in place of the database (`B10` corrupted-database
     * recovery). Nothing is deleted: the broken file and its sidecars are renamed aside
     * with a timestamp so the bytes are still there for a person or a later repair, and
     * the snapshot is copied to a temporary file and moved in only once it is complete.
     * A failure restores the broken file's name and returns null.
     */
    fun restoreLatest(): File? {
        val snapshot = latest() ?: return null
        val target = context.getDatabasePath(databaseName)
        target.parentFile?.mkdirs()
        val stamp = java.time.Instant.now().toEpochMilli()
        val temp = File(target.path + ".restore-$stamp")
        return try {
            Files.copy(snapshot.toPath(), temp.toPath(), StandardCopyOption.REPLACE_EXISTING)
            quarantine(target, stamp)
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            // The snapshot's own WAL carries writes that had not been checkpointed.
            copyIfPresent(File(snapshot.path + "-wal"), File(target.path + "-wal"))
            snapshot
        } catch (failure: IOException) {
            android.util.Log.w(TAG, "Could not restore the database snapshot", failure)
            temp.delete()
            null
        }
    }

    /** Renames the broken database and its sidecars aside; nothing is deleted. */
    private fun quarantine(target: File, stamp: Long) {
        listOf(target, File(target.path + "-wal"), File(target.path + "-shm")).forEach { live ->
            if (live.exists()) {
                val aside = File(live.path + ".corrupt-$stamp")
                if (live.renameTo(aside)) {
                    android.util.Log.w(TAG, "Kept the damaged database as ${aside.name}")
                }
            }
        }
    }

    private fun directory(): File? =
        File(context.filesDir, BACKUP_DIRECTORY).takeIf { it.isDirectory }

    private fun copyIfPresent(source: File, target: File) {
        if (!source.exists()) return
        Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private companion object {
        const val BACKUP_DIRECTORY = "database-backups"
        const val TAG = "openhealth.backup"
    }
}
