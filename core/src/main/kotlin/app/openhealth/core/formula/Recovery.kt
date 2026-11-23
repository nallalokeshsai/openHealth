package app.openhealth.core.formula

import app.openhealth.core.citation.CitationKey
import kotlin.time.Instant

/**
 * The three recovery steps plus the untrained base (`pr.md` §6.5). Under 24 h after a
 * load the muscle is Recovering, 24-48 h Easing, 48-72 h Ready, later untrained base.
 * A hard session - volume or effort above that muscle's own four-week median - holds a
 * muscle one step longer by adding one step length to the elapsed time before the
 * classification. It is a neutral display of the training that was logged, not a claim
 * about soreness.
 */
enum class RecoveryStep { UNTRAINED, RECOVERING, EASING, READY }

data class RecoveryThresholds(
    val recoveringHours: Double = 24.0,
    val easingHours: Double = 48.0,
    val readyHours: Double = 72.0,
) {
    init {
        require(recoveringHours < easingHours && easingHours < readyHours) {
            "recovery thresholds must increase"
        }
    }

    val stepHours: Double get() = easingHours - recoveringHours
}

object MuscleRecovery {
    private const val SECONDS_PER_HOUR = 3_600.0

    fun stepAtHours(
        hoursSinceLoad: Double,
        hardSession: Boolean,
        thresholds: RecoveryThresholds = RecoveryThresholds(),
    ): FormulaOutcome<RecoveryStep> {
        val inputs = listOf(
            input("hours", hoursSinceLoad, "h"),
            input("stepHours", thresholds.stepHours, "h"),
        )
        if (hoursSinceLoad < 0.0 || !hoursSinceLoad.isFinite()) {
            return notAvailable(NotAvailableReason.OUT_OF_RANGE, CitationKey.APP_RULE, inputs)
        }
        val adjusted = if (hardSession) hoursSinceLoad - thresholds.stepHours else hoursSinceLoad
        val step = when {
            adjusted > thresholds.readyHours -> RecoveryStep.UNTRAINED
            adjusted < thresholds.recoveringHours -> RecoveryStep.RECOVERING
            adjusted < thresholds.easingHours -> RecoveryStep.EASING
            else -> RecoveryStep.READY
        }
        return computed(step, CitationKey.APP_RULE, inputs)
    }

    fun step(
        now: Instant,
        lastLoadedAt: Instant,
        hardSession: Boolean,
        thresholds: RecoveryThresholds = RecoveryThresholds(),
    ): FormulaOutcome<RecoveryStep> {
        if (now < lastLoadedAt) {
            return notAvailable(
                NotAvailableReason.OUT_OF_RANGE,
                CitationKey.APP_RULE,
                listOf(input("hours", (lastLoadedAt - now).inWholeSeconds / SECONDS_PER_HOUR, "h")),
            )
        }
        return stepAtHours(
            (now - lastLoadedAt).inWholeSeconds / SECONDS_PER_HOUR,
            hardSession,
            thresholds,
        )
    }
}

/** A muscle's four-week load, used to decide whether a session was hard for it. */
data class MuscleLoadHistory(val weeklyMedianSets: Double, val weeklyMedianTonnage: Double)

object HardSession {
    private const val HARD_MULTIPLIER = 1.0

    /**
     * A session is hard for a muscle when its sets or tonnage exceed that muscle's own
     * four-week median. Median, not mean: one unusual week must not raise the bar for
     * everything that follows.
     */
    fun isHard(sets: Int, tonnageKg: Double, history: MuscleLoadHistory): Boolean =
        sets > history.weeklyMedianSets * HARD_MULTIPLIER ||
            tonnageKg > history.weeklyMedianTonnage * HARD_MULTIPLIER
}
