package app.openhealth.android.training

import android.content.Context
import app.openhealth.core.seam.RestTimerSeam
import app.openhealth.core.seam.RestTimerSnapshot

/**
 * The rest timer's persisted state. It is tiny, non-sensitive and has to be readable after
 * a process kill, so it lives in its own preferences file rather than the encrypted store
 * (`pr.md` §6.5: the timer survives process death).
 */
class RestTimerStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun start(seconds: Int, nowMillis: Long) {
        val total = seconds.coerceIn(0, MAX_SECONDS.toInt()).toLong()
        prefs.edit()
            .putBoolean(ACTIVE, total > 0L)
            .putLong(DEADLINE, nowMillis + total * MILLIS_PER_SECOND)
            .putLong(TOTAL, total)
            .putBoolean(PAUSED, false)
            .putLong(PAUSED_REMAINING, 0L)
            .apply()
    }

    fun adjust(deltaSeconds: Int, nowMillis: Long) {
        val state = read(nowMillis) ?: return
        val remaining = (state.remainingSeconds + deltaSeconds).coerceIn(0L, MAX_SECONDS)
        val total = state.totalSeconds.coerceAtLeast(remaining)
        prefs.edit()
            .putLong(TOTAL, total)
            .putLong(DEADLINE, nowMillis + remaining * MILLIS_PER_SECOND)
            .putLong(PAUSED_REMAINING, remaining)
            .apply()
        if (remaining <= 0) clear()
    }

    fun togglePause(nowMillis: Long) {
        val state = read(nowMillis) ?: return
        val paused = !state.paused
        val remaining = state.remainingSeconds
        prefs.edit()
            .putBoolean(PAUSED, paused)
            .putLong(PAUSED_REMAINING, remaining)
            .putLong(DEADLINE, nowMillis + remaining * MILLIS_PER_SECOND)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun active(): Boolean = prefs.getBoolean(ACTIVE, false)

    fun read(nowMillis: Long): RestTimerSnapshot? {
        if (!prefs.getBoolean(ACTIVE, false)) return null
        val total = prefs.getLong(TOTAL, 0L)
        val paused = prefs.getBoolean(PAUSED, false)
        val remaining = if (paused) {
            prefs.getLong(PAUSED_REMAINING, 0L)
        } else {
            ((prefs.getLong(DEADLINE, 0L) - nowMillis) / MILLIS_PER_SECOND).coerceAtLeast(0L)
        }
        return RestTimerSnapshot(
            remainingSeconds = remaining,
            totalSeconds = total,
            paused = paused,
            // No exercise name is stored: the prefs file is outside SQLCipher, and the
            // notification only needs to say that a rest is running.
            label = "",
        )
    }

    companion object {
        private const val FILE = "openhealth.rest_timer"
        private const val ACTIVE = "active"
        private const val DEADLINE = "deadline"
        private const val TOTAL = "total"
        private const val PAUSED = "paused"
        private const val PAUSED_REMAINING = "paused_remaining"
        private const val MAX_SECONDS = 600L
        private const val MILLIS_PER_SECOND = 1_000L
    }
}

/** The seam implementation used by the app; every call reaches [RestTimerService]. */
class AndroidRestTimer(private val context: Context) : RestTimerSeam {
    private val store = RestTimerStore(context)

    @Suppress("UnusedParameter")
    override fun start(seconds: Int, label: String) {
        // The label is deliberately not stored outside SQLCipher; the notification says
        // "Rest" and the live screen already names the exercise.
        store.start(seconds, System.currentTimeMillis())
        RestTimerService.start(context)
    }

    override fun resume() {
        if (store.active()) RestTimerService.start(context)
    }

    override fun adjust(seconds: Int) {
        if (!store.active()) return
        store.adjust(seconds, System.currentTimeMillis())
        RestTimerService.start(context)
    }

    override fun togglePause() {
        if (!store.active()) return
        store.togglePause(System.currentTimeMillis())
        RestTimerService.start(context)
    }

    override fun skip() {
        // Clearing is enough: a running service sees the empty state on its next tick and
        // stops itself, already in the foreground. Sending a second startForegroundService
        // can tear it down before the first start has settled, which Android reports as a
        // missed foreground promise.
        store.clear()
    }

    override fun snapshot(nowMillis: Long): RestTimerSnapshot? = store.read(nowMillis)
}
