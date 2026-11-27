package app.openhealth.android.wear

import android.content.Context
import app.openhealth.core.watch.WatchProtocol

/**
 * The command ids the phone has already applied, kept across a process restart so a
 * replayed Data Layer or BLE message cannot write a second set (`docs/adr/0019`). It is
 * small, non-sensitive bookkeeping and lives outside SQLCipher like the rest-timer
 * deadline; the records themselves stay in the encrypted store.
 */
class WatchAppliedStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun recent(): List<String> = prefs.getString(IDS, "")
        .orEmpty()
        .split(SEPARATOR)
        .filter { it.isNotBlank() }

    fun record(id: String) {
        if (id.isBlank()) return
        val ids = (recent() + id).distinct().takeLast(WatchProtocol.MAX_APPLIED_COMMAND_IDS)
        prefs.edit().putString(IDS, ids.joinToString(SEPARATOR)).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val FILE = "openhealth.watch_applied"
        private const val IDS = "command_ids"
        private const val SEPARATOR = "|"
    }
}
