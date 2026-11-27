package app.openhealth.wear.watch

import android.content.Context
import app.openhealth.core.watch.WatchCommand
import app.openhealth.core.watch.WatchProtocol
import app.openhealth.core.watch.WatchSnapshot
import kotlinx.serialization.builtins.ListSerializer

/**
 * The wrist's small local memory: the last snapshot and the commands that have not been
 * acknowledged. It is intentionally not a database: the watch owns no record, and this
 * file is what lets a dropped link, a process kill or a reboot lose nothing
 * (`docs/adr/0019`). It holds no health value beyond the open workout the phone already
 * has.
 */
class WatchStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun snapshot(): WatchSnapshot? = prefs.getString(SNAPSHOT, null)?.let { text ->
        runCatching {
            WatchProtocol.json.decodeFromString(WatchSnapshot.serializer(), text)
        }.getOrNull()
    }

    fun saveSnapshot(snapshot: WatchSnapshot) {
        prefs.edit()
            .putString(
                SNAPSHOT,
                WatchProtocol.json.encodeToString(WatchSnapshot.serializer(), snapshot),
            )
            .apply()
    }

    fun pending(): List<WatchCommand> {
        val text = prefs.getString(PENDING, null) ?: return emptyList()
        return runCatching {
            WatchProtocol.json.decodeFromString(
                ListSerializer(WatchCommand.serializer()),
                text,
            )
        }.getOrDefault(emptyList())
    }

    fun addPending(command: WatchCommand) {
        val current = pending()
        if (current.any { it.id == command.id }) return
        write(current + command)
    }

    /** Removes the commands the phone says it applied; returns how many left the queue. */
    fun acknowledge(appliedIds: List<String>): Int {
        if (appliedIds.isEmpty()) return 0
        val current = pending()
        val next = current.filterNot { it.id in appliedIds }
        if (next.size != current.size) write(next)
        return current.size - next.size
    }

    fun pendingCount(): Int = pending().size

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun write(commands: List<WatchCommand>) {
        prefs.edit()
            .putString(
                PENDING,
                WatchProtocol.json.encodeToString(
                    ListSerializer(WatchCommand.serializer()),
                    commands,
                ),
            )
            .apply()
    }

    companion object {
        private const val FILE = "openhealth.wear_queue"
        private const val SNAPSHOT = "snapshot"
        private const val PENDING = "pending"
    }
}
