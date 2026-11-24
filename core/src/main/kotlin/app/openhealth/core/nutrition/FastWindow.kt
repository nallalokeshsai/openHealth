package app.openhealth.core.nutrition

/**
 * One fasting window (`pr.md` §6.3). It is a timer the person starts and ends; nothing
 * here scores a fast, compares it with a target or calls a long one good. The target is
 * the person's own choice and the UI only says how the elapsed time relates to it.
 */
data class FastWindow(
    val startEpochMillis: Long,
    val targetHours: Double,
    val endEpochMillis: Long? = null,
) {
    init {
        require(targetHours > 0.0) { "A fast target is longer than zero." }
        require(endEpochMillis == null || endEpochMillis >= startEpochMillis) {
            "A fast cannot end before it starts."
        }
    }

    val isOpen: Boolean get() = endEpochMillis == null

    fun elapsedMillis(nowMillis: Long): Long {
        val end = endEpochMillis ?: nowMillis
        return (end - startEpochMillis).coerceAtLeast(0L)
    }

    fun elapsedHours(nowMillis: Long): Double = elapsedMillis(nowMillis) / MILLIS_PER_HOUR

    fun targetMillis(): Long = (targetHours * MILLIS_PER_HOUR).toLong()

    /** Hours left before the target, or null once it has passed. */
    fun remainingHours(nowMillis: Long): Double? {
        val remaining = targetMillis() - elapsedMillis(nowMillis)
        return if (remaining <= 0L) null else remaining / MILLIS_PER_HOUR
    }

    /** 0 to 1; stops at the target rather than overshooting into a "score". */
    fun progress(nowMillis: Long): Float {
        val target = targetMillis()
        if (target <= 0L) return 1f
        return (elapsedMillis(nowMillis).toDouble() / target).coerceIn(0.0, 1.0).toFloat()
    }

    fun isPastTarget(nowMillis: Long): Boolean = elapsedMillis(nowMillis) >= targetMillis()

    companion object {
        const val MILLIS_PER_HOUR = 3_600_000.0
        const val MIN_TARGET_HOURS = 1.0
        const val MAX_TARGET_HOURS = 72.0
    }
}
