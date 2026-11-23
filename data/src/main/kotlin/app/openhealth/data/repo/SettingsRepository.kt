package app.openhealth.data.repo

import app.openhealth.core.seam.ClockSeam
import app.openhealth.core.seam.SystemClock
import app.openhealth.data.db.OpenHealthDb

/**
 * Layer 5 (`pr.md` §7.5): one row per setting. A key that has never been written is
 * absent, not zero, so the caller decides the default and the app can tell "off" from
 * "never asked".
 */
class SettingsRepository(
    private val db: OpenHealthDb,
    private val clock: ClockSeam = SystemClock,
) {
    fun get(key: String): String? = db.settingsQueries.setting(key).executeAsOneOrNull()?.value_

    fun put(key: String, value: String) {
        db.settingsQueries.putSetting(
            key = key,
            value = value,
            updatedAt = clock.now().toEpochMilliseconds(),
            schemaVersion = SCHEMA_VERSION,
        )
    }

    fun remove(key: String) {
        db.settingsQueries.deleteSetting(key)
    }

    fun all(): Map<String, String> =
        db.settingsQueries.allSettings().executeAsList().associate { it.key to it.value_ }

    fun count(): Long = db.settingsQueries.countSettings().executeAsOne()

    private companion object {
        const val SCHEMA_VERSION = 1L
    }
}

/** The keys B2 and the screens after it read. A key exists once, here. */
object SettingKeys {
    const val UNITS = "units"
    const val ZONE_ID = "zone_id"
    const val LANGUAGE = "language"
    const val WEEK_START = "week_start"
    const val WEIGHT_NEUTRAL = "weight_neutral"
    const val ACTIVE_PROFILE = "active_profile"
    const val SLEEP_NEED_MINUTES = "sleep_need_minutes"
    const val RECOVERY_RECOVERING_HOURS = "recovery.recovering_hours"
    const val RECOVERY_EASING_HOURS = "recovery.easing_hours"
    const val RECOVERY_READY_HOURS = "recovery.ready_hours"
    const val RAW_RETENTION_DAYS = "raw.retention_days"
    const val TRASH_RETENTION_DAYS = "trash.retention_days"
    const val GOAL_MODE = "goal.mode"
    const val GOAL_RATE_KG_PER_WEEK = "goal.rate_kg_per_week"
    const val THEME = "appearance.theme"
    const val ACCENT_PRESET = "appearance.accent"
    const val ONBOARDING_COMPLETE = "onboarding.complete"
}
