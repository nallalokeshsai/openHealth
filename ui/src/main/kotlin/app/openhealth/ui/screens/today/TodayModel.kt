package app.openhealth.ui.screens.today

import app.openhealth.core.preferences.HealthModule
import kotlinx.datetime.LocalDate

/** The part of the day the greeting names (`DESIGN.md` §14.2, Today item 1). */
enum class Greeting { MORNING, AFTERNOON, EVENING }

/** The one-line status under the greeting. Never a score and never a guess. */
enum class TodayStatus { NOTHING_LOGGED, LOGGED_TODAY, WEIGHT_NEUTRAL }

/** The six quick-add tiles of `pr.md` §6.2 and `DESIGN.md` §14.2. */
enum class QuickAction { NOTE, WATER, WEIGHT, FOOD, SET, VITALS }

/**
 * Everything Today shows. A field that is null is honestly absent and the screen renders
 * its empty state; nothing here is a placeholder number.
 */
data class TodayModel(
    val today: LocalDate,
    val greeting: Greeting,
    val status: TodayStatus,
    val noteCount: Int,
    val waterMl: Double?,
    val steps: Int?,
    val energyKcal: Double?,
    val weightKg: Double?,
    val unitSystem: app.openhealth.core.preferences.UnitSystem =
        app.openhealth.core.preferences.UnitSystem.METRIC,
    val readinessScore: Int?,
    val modules: Set<HealthModule>,
    val demo: Boolean,
)
