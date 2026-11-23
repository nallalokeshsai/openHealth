package app.openhealth.ui.screens.settings

import app.openhealth.core.preferences.HealthModule
import app.openhealth.core.preferences.ModulePreset
import app.openhealth.core.preferences.ProfileGoal
import app.openhealth.core.preferences.UnitSystem
import app.openhealth.ui.theme.OhAccent

/** The appearance setting (`DESIGN.md` §12.4): system, or a pinned theme. */
enum class ThemeChoice { SYSTEM, DARK, LIGHT }

/** A transient result the settings screen states in one line, never a toast alone. */
enum class SettingsMessage {
    EXPORT_SAVED,
    EXPORT_FAILED,
    RESTORE_DONE,
    RESTORE_FAILED,
    WRONG_PASSPHRASE,
    PASSWORDS_DIFFER,
    LOCK_UNAVAILABLE,
    DEMO_ADDED,
    DEMO_FAILED,
    NOTES_DELETED,
    TRASH_EMPTIED,
    PROFILE_SAVED,
    PROFILE_INVALID,
}

data class SettingsCounts(
    val notes: Long,
    val observations: Long,
    val measurements: Long,
    val trash: Long,
)

data class SettingsState(
    val profileName: String,
    val profileSubtitle: String,
    val profileBirthDate: String,
    val profileGoal: ProfileGoal?,
    val profileHeight: String,
    val profileWeight: String,
    val profileUnits: UnitSystem,
    val modules: Set<HealthModule>,
    val preset: ModulePreset,
    val weightNeutral: Boolean,
    val theme: ThemeChoice,
    val accent: OhAccent,
    val appLock: Boolean,
    val appLockAvailable: Boolean,
    val demo: Boolean,
    val versionName: String,
    val counts: SettingsCounts,
    val busy: Boolean,
    val message: SettingsMessage?,
)

/** The profile sheet's editable values; numbers are strings until the holder parses them. */
data class ProfileEdit(
    val name: String,
    val birthDate: String,
    val goal: ProfileGoal?,
    val height: String,
    val weight: String,
    val units: UnitSystem,
)

/** What kind of row a tombstone holds; the screen owns its label. */
enum class TrashKind { NOTE, OBSERVATION, MEASUREMENT, VITAL, ENTRY, SET }

/** One row of the Trash (`pr.md` §6.29). */
data class TrashItemModel(
    val id: String,
    val kind: TrashKind,
    val detail: String,
    val deletedLabel: String,
)
