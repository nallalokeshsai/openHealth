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
    private var sync: app.openhealth.data.sync.SyncRepository? = null

    fun recordChangesWith(recorder: app.openhealth.data.sync.SyncRepository?) {
        sync = recorder
    }

    fun get(key: String): String? = db.settingsQueries.setting(key).executeAsOneOrNull()?.value_

    fun updatedAt(key: String): Long? =
        db.settingsQueries.setting(key).executeAsOneOrNull()?.updated_at

    fun put(key: String, value: String) {
        db.settingsQueries.putSetting(
            key = key,
            value = value,
            updatedAt = clock.now().toEpochMilliseconds(),
            schemaVersion = SCHEMA_VERSION,
        )
        sync?.capture("settings", key)
    }

    fun remove(key: String) {
        db.settingsQueries.deleteSetting(key)
        sync?.captureDelete("settings", key)
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

    /** B9: the daily step goal the Today ring fills towards (`pr.md` §6.6). */
    const val STEP_GOAL = "goal.steps"
    const val THEME = "appearance.theme"
    const val ACCENT_PRESET = "appearance.accent"

    /** B10 accessibility options (`DESIGN.md` §11.1, §12.4). They change how the same
     * tokens are drawn, never which value a screen shows. */
    const val HIGH_CONTRAST = "appearance.high_contrast"
    const val REDUCE_TRANSPARENCY = "appearance.reduce_transparency"
    const val REDUCED_MOTION = "appearance.reduced_motion"
    const val ONBOARDING_COMPLETE = "onboarding.complete"
    const val ONBOARDING_TIER = "onboarding.tier"
    const val MODULE_PRESET = "module.preset"
    const val APP_LOCK = "privacy.app_lock"
    const val DEMO_DATA = "demo.sample_data"
    const val FAVOURITE_FLOWS = "activity.favourite_flows"

    /** Tier-3 answers with no observation or measurement type of their own. */
    const val INJURIES = "profile.injuries"
    const val MEDICATIONS = "profile.medications"
    const val CYCLE_TRACKING = "profile.cycle_tracking"
    const val EQUIPMENT = "profile.equipment"
    const val COMPOSITION_SOURCE = "profile.composition_source"
    const val DIETARY_PATTERN = "profile.dietary_pattern"
    const val ALLERGIES = "profile.allergies"
    const val SLEEP_WINDOW = "profile.sleep_window"
    const val GOAL_WEIGHT_KG = "goal.target_weight_kg"
    const val GOAL_TARGET_DATE = "goal.target_date"
    const val LAST_WATER_ML = "quickadd.last_water_ml"

    /** Nutrition (B6): the fasting card is opt-in, and the Open Food Facts lookup is
     * explicit for the first time (pr.md §6.3). */
    const val NUTRITION_FASTING = "nutrition.fasting_enabled"
    const val NUTRITION_FASTING_HOURS = "nutrition.fasting_hours"
    const val NUTRITION_OFF_DISCLOSURE = "nutrition.off_disclosure_accepted"
    const val NUTRITION_LAST_CONTAINER = "nutrition.last_water_container"

    /** The bundled exercise library's version, so seeding runs once (pr.md §6.5). */
    const val EXERCISE_CATALOGUE = "exercise.catalogue_version"

    /** The one equipment profile: bars, plates and the dumbbell increment (pr.md §6.5). */
    const val TRAINING_EQUIPMENT = "training.equipment"

    /** B14: the Connect IQ direct link is off until the person turns it on. */
    const val WATCH_LINK = "watch.link_enabled"

    /** B8 activity sources: pairing, the step counter's runtime state and each source's
     * last sync (`pr.md` §6.6, §6.18, §7.6). These are configuration and runtime state,
     * never health values. */
    const val DEVICE_REGISTRY = "device.registry"
    const val STEPS_TRACKING = "steps.tracking_enabled"
    const val STEPS_BOOT_MS = "steps.boot_ms"
    const val STEPS_LAST_COUNT = "steps.last_count"
    const val STEPS_LAST_AT = "steps.last_at"
    const val SYNC_BOOK = "activity.sync_book"
    const val HEALTH_CONNECT_ENABLED = "health_connect.enabled"
    const val HEALTH_CONNECT_GRANTED = "health_connect.granted"
    const val HEALTH_CONNECT_BACKFILL = "health_connect.backfilled"

    /** B7: the gallery can hide a photo until it is tapped, and the home-BP protocol
     * remembers the day it started so its seven-day grid survives a restart. */
    const val BODY_PHOTOS_HIDDEN = "body.photos_hidden"
    const val BODY_PHOTOS_IN_EXPORT = "body.photos_in_export"
    const val VITALS_BP_PROTOCOL_START = "vitals.bp_protocol_start"

    /** B10: the review inbox remembers only which questions the person has answered. */
    const val REVIEW_DISMISSED = "review.dismissed"

    /** B13 sync: configuration and cursor are per-device and never captured or synced. */
    const val SYNC_DEVICE_ID = "sync.device_id"
    const val SYNC_SERVER_URL = "sync.server_url"
    const val SYNC_PULL_CURSOR = "sync.pull_cursor"
    const val SYNC_STATUS = "sync.status"
    const val SYNC_LAST_ERROR = "sync.last_error"
    const val SYNC_LAST_SYNCED = "sync.last_synced"
    const val SYNC_ENABLED = "sync.enabled"
    const val SYNC_WIFI_ONLY = "sync.wifi_only"
}

/** A setting is user data unless it only describes this device or its sync state. */
object SyncSettingsPolicy {
    private val localPrefixes = listOf("sync.", "demo.", "steps.", "device.", "health_connect.")

    /**
     * Device-local bookkeeping whose key does not carry a local prefix. The sync book
     * records when each source last reached **this** phone, so another device's copy
     * would make the Devices screen state something untrue; the catalogue version marks
     * what this install has seeded and would stop a later upgrade re-seeding.
     */
    private val localKeys = setOf(SettingKeys.SYNC_BOOK, SettingKeys.EXERCISE_CATALOGUE)

    fun shouldSync(key: String): Boolean =
        key !in localKeys && localPrefixes.none { key.startsWith(it) }
}
