package app.openhealth.core.preferences

/**
 * The values weight-neutral mode hides (`pr.md` §6.25, `DESIGN.md` §7.6).
 *
 * A metric is named once here so a screen cannot invent a fourth category or decide for
 * itself what "weight-neutral" means.
 */
enum class SensitiveMetric {
    WEIGHT,
    CALORIES,
    BODY_FAT,
    PROGRESS_PHOTOS,
}

/**
 * The one policy object every screen asks (`pr.md` §6.25, §6.31; `DESIGN.md` §7.6).
 *
 * No screen reads the raw setting: it receives this object, asks [shows], and renders
 * the value or its neutral replacement. When weight-neutral mode is on the value is not
 * deleted and is not blurred; it is simply not shown. Training, steps, sleep and vitals
 * keep working because they are not in [SensitiveMetric].
 */
@JvmInline
value class DisplayPolicy private constructor(private val weightNeutral: Boolean) {
    fun shows(metric: SensitiveMetric): Boolean =
        if (weightNeutral) metric !in HIDDEN_WHEN_ENABLED else true

    /**
     * The value when the policy allows it, otherwise null. The caller renders its own
     * neutral replacement ("Hidden in weight-neutral mode"), never a guess.
     */
    fun <T : Any> value(metric: SensitiveMetric, value: T?): T? = if (shows(metric)) value else null

    /** True when anything at all is hidden, so a screen can add one neutral line. */
    val hidesSomething: Boolean get() = weightNeutral

    companion object {
        val Standard: DisplayPolicy = DisplayPolicy(weightNeutral = false)
        val WeightNeutral: DisplayPolicy = DisplayPolicy(weightNeutral = true)

        fun of(weightNeutral: Boolean): DisplayPolicy =
            if (weightNeutral) WeightNeutral else Standard
    }
}

/** Every category `pr.md` §6.25 lists; none is left to a screen's judgement. */
private val HIDDEN_WHEN_ENABLED: Set<SensitiveMetric> = SensitiveMetric.entries.toSet()
