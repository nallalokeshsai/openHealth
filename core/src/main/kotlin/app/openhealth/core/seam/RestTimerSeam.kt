package app.openhealth.core.seam

/**
 * A rest timer's observable state (`DESIGN.md` §5.19). The arithmetic lives in
 * [app.openhealth.core.training.RestTimer]; this seam is what survives process death:
 * the deadline, the total and whether it is paused, read back from disk.
 */
data class RestTimerSnapshot(
    val remainingSeconds: Long,
    val totalSeconds: Long,
    val paused: Boolean,
    val label: String,
) {
    val running: Boolean get() = remainingSeconds > 0 && !paused

    val progress: Float
        get() {
            return if (totalSeconds <= 0) {
                0f
            } else {
                (remainingSeconds.toFloat() / totalSeconds).coerceIn(0f, 1f)
            }
        }
}

/**
 * The platform seam for the rest timer. Android implements it with a foreground service
 * and an ongoing notification with actions; the core only sees seconds.
 */
interface RestTimerSeam {
    fun start(seconds: Int, label: String)

    /** Re-attaches the foreground notification after the process was killed. */
    fun resume()
    fun adjust(seconds: Int)
    fun togglePause()
    fun skip()
    fun snapshot(nowMillis: Long): RestTimerSnapshot?
}

/** A seam with no platform behind it, for pure tests and previews. */
object NoRestTimer : RestTimerSeam {
    override fun start(seconds: Int, label: String) = Unit
    override fun resume() = Unit
    override fun adjust(seconds: Int) = Unit
    override fun togglePause() = Unit
    override fun skip() = Unit
    override fun snapshot(nowMillis: Long): RestTimerSnapshot? = null
}
