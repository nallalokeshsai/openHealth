@file:Suppress("MagicNumber")

package app.openhealth.core.formula

/**
 * A night's sleep, settled once (`pr.md` §6.10).
 *
 * A source may report the whole session span (Health Connect's `SLEEP_DURATION`), the
 * stages inside it, and the time in bed. Those are not three durations to add: the span
 * contains the stages, and awake time inside the span is not sleep. When stages exist the
 * asleep figure is deep plus light plus REM, so a staged night's awake minutes never
 * count as sleep; when they do not, the span is the only honest duration. Time in bed is
 * kept beside them and never added to either.
 */
data class SleepTotals(
    val spanSeconds: Double?,
    val inBedSeconds: Double?,
    val deepSeconds: Double?,
    val lightSeconds: Double?,
    val remSeconds: Double?,
    val awakeSeconds: Double?,
    val asleepSeconds: Double,
) {
    val hasStages: Boolean
        get() = deepSeconds != null || lightSeconds != null || remSeconds != null

    val asleepMinutes: Double get() = asleepSeconds / 60.0
}

object SleepTotalsCalculator {
    private const val MAX_SECONDS = 24.0 * 60.0 * 60.0

    /**
     * [spanSeconds] is the session span (or a summary-only duration), the four stage
     * values are optional. A negative or absurd value is treated as absent rather than
     * carried into a score.
     */
    @Suppress("LongParameterList", "ReturnCount")
    fun of(
        spanSeconds: Double? = null,
        inBedSeconds: Double? = null,
        deepSeconds: Double? = null,
        lightSeconds: Double? = null,
        remSeconds: Double? = null,
        awakeSeconds: Double? = null,
    ): SleepTotals? {
        val span = clean(spanSeconds)
        val deep = clean(deepSeconds)
        val light = clean(lightSeconds)
        val rem = clean(remSeconds)
        val awake = clean(awakeSeconds)
        val staged = listOfNotNull(deep, light, rem)
        val asleep = when {
            staged.isNotEmpty() -> staged.sum()
            span != null -> span
            else -> return null
        }
        if (asleep > MAX_SECONDS) return null
        return SleepTotals(
            spanSeconds = span,
            inBedSeconds = clean(inBedSeconds) ?: span,
            deepSeconds = deep,
            lightSeconds = light,
            remSeconds = rem,
            awakeSeconds = awake,
            asleepSeconds = asleep,
        )
    }

    private fun clean(value: Double?): Double? =
        value?.takeIf { it.isFinite() && it >= 0.0 && it <= MAX_SECONDS }
}
