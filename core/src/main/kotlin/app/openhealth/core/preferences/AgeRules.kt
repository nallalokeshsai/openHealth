package app.openhealth.core.preferences

import kotlinx.datetime.LocalDate

/**
 * The age gate of `pr.md` §6.1: under 16 is not supported, and 16-17 may track but get
 * no calorie deficit target and no weight-loss goal.
 *
 * The rule is one pure function so onboarding, settings and every goal surface ask the
 * same question; a screen never compares a year itself.
 */
object AgeRules {
    const val MINIMUM_AGE = 16
    const val ADULT_AGE = 18

    /** Whole years completed on [today]. A leap-day birthday turns over on 1 March. */
    fun ageOn(dateOfBirth: LocalDate, today: LocalDate): Int {
        var age = today.year - dateOfBirth.year
        val hadBirthday = today.month.ordinal > dateOfBirth.month.ordinal ||
            (today.month == dateOfBirth.month && today.dayOfMonth >= dateOfBirth.dayOfMonth)
        if (!hadBirthday) age -= 1
        return age.coerceAtLeast(0)
    }

    fun isSupported(dateOfBirth: LocalDate, today: LocalDate): Boolean =
        ageOn(dateOfBirth, today) >= MINIMUM_AGE

    /** A weight-loss goal and a deficit target are adults-only (`pr.md` §6.1). */
    fun mayUseWeightLossGoal(dateOfBirth: LocalDate, today: LocalDate): Boolean =
        ageOn(dateOfBirth, today) >= ADULT_AGE
}
