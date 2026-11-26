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
    /** A long local operation is running: encryption, not the network. */
    WORKING,
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
    val profileMayLoseWeight: Boolean,
    val hasRestoreSnapshot: Boolean,

    /** Set when a corrupt database was replaced from the snapshot taken before a migration. */
    val recoveryNote: String?,

    /** How many review questions are waiting; 0 hides nothing, the row stays visible. */
    val reviewCount: Int,
    val modules: Set<HealthModule>,

    /** What the Devices row says: which sources are on, or nothing yet. */
    val devicesDetail: String,
    val preset: ModulePreset,
    /** The switch's own state; it is the control, not a display decision. */
    val weightNeutral: Boolean,

    /** What may be shown. No screen asks the switch instead (`pr.md` §6.25). */
    val displayPolicy: app.openhealth.core.preferences.DisplayPolicy,
    val theme: ThemeChoice,
    val accent: OhAccent,
    val highContrast: Boolean,
    val reduceTransparency: Boolean,
    val reducedMotion: Boolean,
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
enum class TrashKind {
    NOTE,
    OBSERVATION,
    MEASUREMENT,
    VITAL,
    PHOTO,
    ENTRY,
    SET,
    SESSION,
    ROUTINE_EXERCISE,
    DOSE,
    SYMPTOM,
    LAB,
    ALLERGY,
    CONDITION,
    VACCINATION,
}

/** One row of the Trash (`pr.md` §6.29). */
data class TrashItemModel(
    val id: String,
    val kind: TrashKind,
    val detail: String,
    val deletedLabel: String,
)
