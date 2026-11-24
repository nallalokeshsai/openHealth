package app.openhealth.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import app.openhealth.core.preferences.HealthModule
import app.openhealth.core.preferences.ModulePreset
import app.openhealth.core.preferences.ProfileGoal
import app.openhealth.core.preferences.SensitiveMetric
import app.openhealth.core.preferences.UnitSystem
import app.openhealth.ui.R
import app.openhealth.ui.components.OhChip
import app.openhealth.ui.components.OhChipStrip
import app.openhealth.ui.components.OhDialog
import app.openhealth.ui.components.OhInsetGroup
import app.openhealth.ui.components.OhModalSheet
import app.openhealth.ui.components.OhRow
import app.openhealth.ui.components.OhSegmentedControl
import app.openhealth.ui.components.OhSwitch
import app.openhealth.ui.components.OhTextField
import app.openhealth.ui.icons.OhIcons
import app.openhealth.ui.icons.OhModuleIcons
import app.openhealth.ui.screens.common.OhPageHeader
import app.openhealth.ui.theme.OhAccent
import app.openhealth.ui.theme.OhTheme
import app.openhealth.ui.theme.OhWeight

private enum class SettingsSheet {
    NONE,
    PROFILE,
    MODULES,
    DEVICES,
    DATA,
    APPEARANCE,
    PRIVACY,
    ABOUT,
}
private enum class Confirm { NONE, DELETE_NOTES, EMPTY_TRASH }
private enum class Passphrase { NONE, EXPORT, RESTORE }

/**
 * Settings, opened by the top-bar gear (`DESIGN.md` §14.1, §14.2). One scroll surface
 * of grouped rows; each group opens a sheet, and a destructive action is its own group
 * at the bottom, in `red-ink`.
 */
@Composable
fun SettingsScreen(
    state: SettingsState,
    onBack: () -> Unit,
    onSaveProfile: (ProfileEdit) -> Unit,
    onToggleModule: (HealthModule, Boolean) -> Unit,
    onPreset: (ModulePreset) -> Unit,
    onWeightNeutral: (Boolean) -> Unit,
    onTheme: (ThemeChoice) -> Unit,
    onAccent: (OhAccent) -> Unit,
    onAppLock: (Boolean) -> Unit,
    onExport: (Uri, CharArray) -> Unit,
    onRestore: (Uri, CharArray) -> Unit,
    onOpenTimeline: () -> Unit,
    onOpenTrash: () -> Unit,
    onExternalActivity: () -> Unit,
    onDeleteAllNotes: () -> Unit,
    onEmptyTrash: () -> Unit,
    onUndoRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sheet by remember { mutableStateOf(SettingsSheet.NONE) }
    var confirm by remember { mutableStateOf(Confirm.NONE) }
    var passphrase by remember { mutableStateOf(Passphrase.NONE) }
    var confirmRestore by remember { mutableStateOf<CharArray?>(null) }
    var exportUri by remember { mutableStateOf<Uri?>(null) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) {
            exportUri = uri
            passphrase = Passphrase.EXPORT
        }
    }
    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            restoreUri = uri
            passphrase = Passphrase.RESTORE
        }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OhTheme.colors.bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = OhTheme.space.s6),
    ) {
        OhPageHeader(title = stringResource(R.string.settings_title), onBack = onBack)
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            state.message?.let { MessageLine(it) }
            OhCardProfile(state = state, onClick = { sheet = SettingsSheet.PROFILE })
            OhInsetGroup {
                GroupRow(
                    title = stringResource(R.string.settings_modules),
                    detail = stringResource(
                        R.string.settings_modules_detail,
                        state.modules.size,
                    ),
                    icon = OhModuleIcons.workout,
                    onClick = { sheet = SettingsSheet.MODULES },
                )
                GroupRow(
                    title = stringResource(R.string.settings_devices),
                    detail = stringResource(R.string.settings_devices_detail),
                    icon = OhModuleIcons.integrations,
                    showDivider = true,
                    onClick = { sheet = SettingsSheet.DEVICES },
                )
                GroupRow(
                    title = stringResource(R.string.settings_data),
                    detail = stringResource(R.string.settings_data_detail),
                    icon = OhIcons.export,
                    showDivider = true,
                    onClick = { sheet = SettingsSheet.DATA },
                )
            }
            Spacer(Modifier.height(OhTheme.space.s7))
            OhInsetGroup {
                GroupRow(
                    title = stringResource(R.string.settings_appearance),
                    detail = stringResource(
                        R.string.settings_appearance_detail,
                        themeLabel(state.theme),
                        state.accent.label(),
                    ),
                    icon = OhIcons.more,
                    onClick = { sheet = SettingsSheet.APPEARANCE },
                )
                GroupRow(
                    title = stringResource(R.string.settings_privacy),
                    detail = if (state.appLock) {
                        stringResource(R.string.settings_privacy_on)
                    } else {
                        stringResource(R.string.settings_privacy_off)
                    },
                    icon = OhIcons.lock,
                    showDivider = true,
                    onClick = { sheet = SettingsSheet.PRIVACY },
                )
                GroupRow(
                    title = stringResource(R.string.settings_about),
                    detail = state.versionName,
                    icon = OhIcons.info,
                    showDivider = true,
                    onClick = { sheet = SettingsSheet.ABOUT },
                )
            }
            Spacer(Modifier.height(OhTheme.space.s7))
            OhInsetGroup {
                OhRow(
                    title = stringResource(R.string.settings_delete_notes),
                    leadingIcon = OhIcons.delete,
                    destructive = true,
                    showChevron = true,
                    onClick = { confirm = Confirm.DELETE_NOTES },
                )
                OhRow(
                    title = stringResource(R.string.settings_empty_trash),
                    leadingIcon = OhIcons.delete,
                    destructive = true,
                    showDivider = true,
                    showChevron = true,
                    onClick = { confirm = Confirm.EMPTY_TRASH },
                )
            }
            Spacer(Modifier.height(OhTheme.space.s8))
        }
    }

    ProfileSheet(
        visible = sheet == SettingsSheet.PROFILE,
        state = state,
        onDismiss = { sheet = SettingsSheet.NONE },
        onSave = {
            onSaveProfile(it)
            sheet = SettingsSheet.NONE
        },
    )
    ModulesSheet(
        visible = sheet == SettingsSheet.MODULES,
        state = state,
        onDismiss = { sheet = SettingsSheet.NONE },
        onToggle = onToggleModule,
        onPreset = onPreset,
    )
    DevicesSheet(visible = sheet == SettingsSheet.DEVICES, onDismiss = {
        sheet = SettingsSheet.NONE
    })
    DataSheet(
        visible = sheet == SettingsSheet.DATA,
        state = state,
        onDismiss = { sheet = SettingsSheet.NONE },
        onOpenTimeline = onOpenTimeline,
        onOpenTrash = onOpenTrash,
        onExport = {
            sheet = SettingsSheet.NONE
            onExternalActivity()
            createDocument.launch("openHealth-export.ohe")
        },
        onRestore = {
            sheet = SettingsSheet.NONE
            onExternalActivity()
            openDocument.launch(arrayOf("application/octet-stream", "*/*"))
        },
        onUndoRestore = onUndoRestore,
    )
    AppearanceSheet(
        visible = sheet == SettingsSheet.APPEARANCE,
        state = state,
        onDismiss = { sheet = SettingsSheet.NONE },
        onTheme = onTheme,
        onAccent = onAccent,
        onWeightNeutral = onWeightNeutral,
    )
    PrivacySheet(
        visible = sheet == SettingsSheet.PRIVACY,
        state = state,
        onDismiss = { sheet = SettingsSheet.NONE },
        onAppLock = onAppLock,
    )
    AboutSheet(
        visible = sheet == SettingsSheet.ABOUT,
        state = state,
        onDismiss = { sheet = SettingsSheet.NONE },
    )
    PassphraseSheet(
        target = passphrase,
        onDismiss = { passphrase = Passphrase.NONE },
        onSubmit = { secret ->
            when (passphrase) {
                Passphrase.EXPORT -> exportUri?.let { onExport(it, secret) }
                Passphrase.RESTORE -> if (restoreUri != null) confirmRestore = secret
                Passphrase.NONE -> Unit
            }
            if (passphrase == Passphrase.EXPORT) exportUri = null
            passphrase = Passphrase.NONE
        },
    )
    OhDialog(
        visible = confirmRestore != null,
        onDismiss = {
            confirmRestore = confirmRestore.cleared()
        },
        title = stringResource(R.string.settings_restore_confirm_title),
        message = stringResource(R.string.settings_restore_confirm_message),
        primaryLabel = stringResource(R.string.settings_restore_confirm_action),
        destructive = false,
        onPrimary = {
            val secret = confirmRestore
            confirmRestore = null
            restoreUri?.let { uri -> secret?.let { onRestore(uri, it) } }
            restoreUri = null
        },
        cancelLabel = stringResource(R.string.settings_cancel),
        onCancel = {
            confirmRestore = confirmRestore.cleared()
            restoreUri = null
        },
    )
    OhDialog(
        visible = confirm == Confirm.DELETE_NOTES,
        onDismiss = { confirm = Confirm.NONE },
        title = stringResource(R.string.settings_delete_notes_title),
        message = stringResource(R.string.settings_delete_notes_message),
        primaryLabel = stringResource(R.string.settings_delete_notes),
        destructive = true,
        onPrimary = {
            confirm = Confirm.NONE
            onDeleteAllNotes()
        },
        cancelLabel = stringResource(R.string.settings_cancel),
        onCancel = { confirm = Confirm.NONE },
    )
    OhDialog(
        visible = confirm == Confirm.EMPTY_TRASH,
        onDismiss = { confirm = Confirm.NONE },
        title = stringResource(R.string.settings_empty_trash_title),
        message = stringResource(R.string.settings_empty_trash_message),
        primaryLabel = stringResource(R.string.settings_empty_trash),
        destructive = true,
        onPrimary = {
            confirm = Confirm.NONE
            onEmptyTrash()
        },
        cancelLabel = stringResource(R.string.settings_cancel),
        onCancel = { confirm = Confirm.NONE },
    )
}

@Composable
private fun MessageLine(message: SettingsMessage) {
    val error = message in ERROR_MESSAGES
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = OhTheme.space.s3),
    ) {
        if (error) {
            app.openhealth.ui.icons.OhIcon(
                icon = app.openhealth.ui.icons.OhIcons.warning,
                contentDescription = null,
                size = OhTheme.dims.glyphInline,
                tint = OhTheme.colors.redInk,
            )
            Spacer(Modifier.width(OhTheme.space.s1))
        }
        Text(
            text = stringResource(
                when (message) {
                    SettingsMessage.WORKING -> R.string.settings_working
                    SettingsMessage.EXPORT_SAVED -> R.string.settings_export_saved
                    SettingsMessage.EXPORT_FAILED -> R.string.settings_export_failed
                    SettingsMessage.RESTORE_DONE -> R.string.settings_restore_done
                    SettingsMessage.RESTORE_FAILED -> R.string.settings_restore_failed
                    SettingsMessage.WRONG_PASSPHRASE -> R.string.settings_wrong_passphrase
                    SettingsMessage.PASSWORDS_DIFFER -> R.string.settings_passwords_differ
                    SettingsMessage.LOCK_UNAVAILABLE -> R.string.settings_lock_unavailable
                    SettingsMessage.DEMO_ADDED -> R.string.settings_demo_added
                    SettingsMessage.DEMO_FAILED -> R.string.settings_demo_failed
                    SettingsMessage.NOTES_DELETED -> R.string.settings_notes_deleted
                    SettingsMessage.TRASH_EMPTIED -> R.string.settings_trash_emptied
                    SettingsMessage.PROFILE_SAVED -> R.string.settings_profile_saved
                    SettingsMessage.PROFILE_INVALID -> R.string.settings_profile_invalid
                },
            ),
            style = OhTheme.type.footnote,
            color = if (error) OhTheme.colors.redInk else OhTheme.colors.accInk,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

private val ERROR_MESSAGES = setOf(
    SettingsMessage.EXPORT_FAILED,
    SettingsMessage.RESTORE_FAILED,
    SettingsMessage.WRONG_PASSPHRASE,
    SettingsMessage.PASSWORDS_DIFFER,
    SettingsMessage.LOCK_UNAVAILABLE,
    SettingsMessage.DEMO_FAILED,
    SettingsMessage.PROFILE_INVALID,
)

@Composable
private fun OhCardProfile(state: SettingsState, onClick: () -> Unit) {
    OhInsetGroup {
        OhRow(
            title = state.profileName,
            subtitle = state.profileSubtitle,
            leading = { ProfileAvatar(state.profileName) },
            showChevron = true,
            onClick = onClick,
        )
    }
    Spacer(Modifier.height(OhTheme.space.s7))
}

@Composable
private fun ProfileAvatar(name: String) {
    val initials = name.trim().split(' ').filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "?" }
    Box(
        modifier = Modifier
            .size(OhTheme.dims.iconButton)
            .clip(CircleShape)
            .background(OhTheme.colors.surface2),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = OhTheme.type.body.copy(fontWeight = OhWeight.semibold),
            color = OhTheme.colors.label,
        )
    }
}

@Composable
private fun GroupRow(
    title: String,
    detail: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    showDivider: Boolean = false,
    onClick: () -> Unit,
) {
    OhRow(
        title = title,
        subtitle = detail,
        leadingIcon = icon,
        leadingTint = OhTheme.colors.surface2,
        leadingInk = OhTheme.colors.label,
        showDivider = showDivider,
        showChevron = true,
        onClick = onClick,
    )
}

@Composable
private fun ProfileSheet(
    visible: Boolean,
    state: SettingsState,
    onDismiss: () -> Unit,
    onSave: (ProfileEdit) -> Unit,
) {
    if (!visible) return
    var name by remember(state.profileName) { mutableStateOf(state.profileName) }
    var birthDate by remember(state.profileName) { mutableStateOf(state.profileBirthDate) }
    var goal by remember(state.profileName) { mutableStateOf(state.profileGoal) }
    var height by remember(state.profileName) { mutableStateOf(state.profileHeight) }
    var weight by remember(state.profileName) { mutableStateOf(state.profileWeight) }
    var units by remember(state.profileName) { mutableStateOf(state.profileUnits) }
    // Switching units converts what is shown; it is not a new measurement. Only a
    // typed value is sent back, so a units change never writes a weigh-in.
    var heightTyped by remember(state.profileName) { mutableStateOf(false) }
    var weightTyped by remember(state.profileName) { mutableStateOf(false) }
    OhModalSheet(
        visible = true,
        onDismiss = onDismiss,
        title = stringResource(R.string.settings_profile_title),
        primaryLabel = stringResource(R.string.settings_save),
        onPrimary = {
            onSave(
                ProfileEdit(
                    name = name,
                    birthDate = birthDate,
                    goal = goal,
                    height = if (heightTyped) height else "",
                    weight = if (weightTyped) weight else "",
                    units = units,
                ),
            )
        },
        cancelLabel = stringResource(R.string.settings_cancel),
        onCancel = onDismiss,
    ) {
        OhTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = stringResource(R.string.onb_name_placeholder),
            contentDescription = stringResource(R.string.onb_name_title),
        )
        Spacer(Modifier.height(OhTheme.space.s5))
        Text(
            text = stringResource(R.string.onb_birth_title),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
        OhTextField(
            value = birthDate,
            onValueChange = { birthDate = it },
            placeholder = stringResource(R.string.onb_birth_placeholder),
            contentDescription = stringResource(R.string.onb_birth_title),
        )
        Spacer(Modifier.height(OhTheme.space.s5))
        Text(
            text = stringResource(R.string.onb_goal_title),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
        OhChipStrip {
            ProfileGoal.entries
                .filter { it != ProfileGoal.LOSE_FAT || state.profileMayLoseWeight }
                .forEach { option ->
                    OhChip(
                        text = option.label(),
                        selected = goal == option,
                        onClick = { goal = option },
                    )
                }
        }
        Spacer(Modifier.height(OhTheme.space.s5))
        Row(horizontalArrangement = Arrangement.spacedBy(OhTheme.space.s3)) {
            OhTextField(
                value = height,
                onValueChange = {
                    height = it
                    heightTyped = true
                },
                placeholder = stringResource(
                    if (units == UnitSystem.IMPERIAL) R.string.unit_inches else R.string.unit_cm,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            if (state.displayPolicy.shows(SensitiveMetric.WEIGHT)) {
                OhTextField(
                    value = weight,
                    onValueChange = {
                        weight = it
                        weightTyped = true
                    },
                    placeholder = stringResource(
                        if (units == UnitSystem.IMPERIAL) {
                            R.string.unit_pounds
                        } else {
                            R.string.unit_kg
                        },
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(OhTheme.space.s5))
        OhSegmentedControl(
            options = listOf(
                stringResource(R.string.onb_units_metric),
                stringResource(R.string.onb_units_imperial),
            ),
            selectedIndex = if (units == UnitSystem.METRIC) 0 else 1,
            onSelect = { index ->
                val next = if (index == 0) UnitSystem.METRIC else UnitSystem.IMPERIAL
                if (next != units) {
                    height = convertLength(height, units, next)
                    weight = convertMass(weight, units, next)
                    units = next
                }
            },
        )
    }
}

@Composable
private fun ModulesSheet(
    visible: Boolean,
    state: SettingsState,
    onDismiss: () -> Unit,
    onToggle: (HealthModule, Boolean) -> Unit,
    onPreset: (ModulePreset) -> Unit,
) {
    if (!visible) return
    OhModalSheet(
        visible = true,
        onDismiss = onDismiss,
        title = stringResource(R.string.settings_modules),
        cancelLabel = stringResource(R.string.settings_close),
        onCancel = onDismiss,
    ) {
        Text(
            text = stringResource(R.string.settings_modules_body),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
        Spacer(Modifier.height(OhTheme.space.s5))
        OhChipStrip {
            ModulePreset.entries
                .filterNot { it == ModulePreset.CUSTOM }
                .forEach { preset ->
                    OhChip(
                        text = preset.label(),
                        selected = state.preset == preset,
                        onClick = { onPreset(preset) },
                    )
                }
        }
        Spacer(Modifier.height(OhTheme.space.s5))
        OhInsetGroup {
            HealthModule.entries.forEachIndexed { index, module ->
                OhRow(
                    title = module.label(),
                    showDivider = index > 0,
                    mergeSemantics = false,
                    trailing = {
                        OhSwitch(
                            checked = module in state.modules,
                            onCheckedChange = { onToggle(module, it) },
                            contentDescription = module.label(),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun DevicesSheet(visible: Boolean, onDismiss: () -> Unit) {
    if (!visible) return
    OhModalSheet(
        visible = true,
        onDismiss = onDismiss,
        title = stringResource(R.string.settings_devices),
        cancelLabel = stringResource(R.string.settings_close),
        onCancel = onDismiss,
    ) {
        Text(
            text = stringResource(R.string.settings_devices_empty),
            style = OhTheme.type.body,
            color = OhTheme.colors.label,
        )
        Spacer(Modifier.height(OhTheme.space.s0))
        Text(
            text = stringResource(R.string.settings_devices_body),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
    }
}

@Composable
private fun DataSheet(
    visible: Boolean,
    state: SettingsState,
    onDismiss: () -> Unit,
    onOpenTimeline: () -> Unit,
    onOpenTrash: () -> Unit,
    onExport: () -> Unit,
    onRestore: () -> Unit,
    onUndoRestore: () -> Unit,
) {
    if (!visible) return
    OhModalSheet(
        visible = true,
        onDismiss = onDismiss,
        title = stringResource(R.string.settings_data),
        cancelLabel = stringResource(R.string.settings_close),
        onCancel = onDismiss,
    ) {
        OhInsetGroup {
            OhRow(
                title = stringResource(R.string.timeline_title),
                subtitle = stringResource(R.string.settings_data_timeline_body),
                leadingIcon = OhIcons.timer,
                showChevron = true,
                onClick = onOpenTimeline,
            )
            OhRow(
                title = stringResource(R.string.settings_trash),
                subtitle = pluralStringResource(
                    R.plurals.trash_items,
                    state.counts.trash.toInt(),
                    state.counts.trash.toInt(),
                ),
                leadingIcon = OhIcons.delete,
                showDivider = true,
                showChevron = true,
                onClick = onOpenTrash,
            )
        }
        Spacer(Modifier.height(OhTheme.space.s5))
        OhInsetGroup {
            OhRow(
                title = stringResource(R.string.settings_export),
                subtitle = if (state.busy) {
                    stringResource(R.string.settings_working)
                } else {
                    stringResource(R.string.settings_export_body)
                },
                leadingIcon = OhIcons.export,
                showChevron = true,
                enabled = !state.busy,
                onClick = if (state.busy) null else onExport,
            )
            OhRow(
                title = stringResource(R.string.settings_restore),
                subtitle = if (state.busy) {
                    stringResource(R.string.settings_working)
                } else {
                    stringResource(R.string.settings_restore_body)
                },
                leadingIcon = OhIcons.refresh,
                showDivider = true,
                showChevron = true,
                enabled = !state.busy,
                onClick = if (state.busy) null else onRestore,
            )
        }
        if (state.hasRestoreSnapshot) {
            Spacer(Modifier.height(OhTheme.space.s5))
            OhInsetGroup {
                OhRow(
                    title = stringResource(R.string.settings_undo_restore),
                    subtitle = stringResource(R.string.settings_undo_restore_body),
                    leadingIcon = OhIcons.undo,
                    showChevron = true,
                    onClick = onUndoRestore,
                )
            }
        }
        Spacer(Modifier.height(OhTheme.space.s5))
        Text(
            text = stringResource(
                R.string.settings_counts,
                state.counts.notes,
                state.counts.observations,
                state.counts.measurements,
            ),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
    }
}

@Composable
private fun AppearanceSheet(
    visible: Boolean,
    state: SettingsState,
    onDismiss: () -> Unit,
    onTheme: (ThemeChoice) -> Unit,
    onAccent: (OhAccent) -> Unit,
    onWeightNeutral: (Boolean) -> Unit,
) {
    if (!visible) return
    OhModalSheet(
        visible = true,
        onDismiss = onDismiss,
        title = stringResource(R.string.settings_appearance),
        cancelLabel = stringResource(R.string.settings_close),
        onCancel = onDismiss,
    ) {
        Text(
            text = stringResource(R.string.settings_theme),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
        Spacer(Modifier.height(OhTheme.space.s2))
        OhSegmentedControl(
            options = listOf(
                stringResource(R.string.settings_theme_system),
                stringResource(R.string.settings_theme_dark),
                stringResource(R.string.settings_theme_light),
            ),
            selectedIndex = when (state.theme) {
                ThemeChoice.SYSTEM -> 0
                ThemeChoice.DARK -> 1
                ThemeChoice.LIGHT -> 2
            },
            onSelect = {
                onTheme(
                    when (it) {
                        1 -> ThemeChoice.DARK
                        2 -> ThemeChoice.LIGHT
                        else -> ThemeChoice.SYSTEM
                    },
                )
            },
        )
        Spacer(Modifier.height(OhTheme.space.s5))
        Text(
            text = stringResource(R.string.settings_accent),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
        Spacer(Modifier.height(OhTheme.space.s2))
        OhChipStrip {
            OhAccent.entries.forEach { accent ->
                OhChip(
                    text = accent.label(),
                    selected = state.accent == accent,
                    onClick = { onAccent(accent) },
                    swatch = if (OhTheme.colors.dark) accent.darkAcc else accent.lightAcc,
                )
            }
        }
        Spacer(Modifier.height(OhTheme.space.s7))
        OhInsetGroup {
            OhRow(
                title = stringResource(R.string.settings_weight_neutral),
                subtitle = stringResource(R.string.settings_weight_neutral_body),
                trailing = {
                    OhSwitch(
                        checked = state.weightNeutral,
                        onCheckedChange = onWeightNeutral,
                        contentDescription = stringResource(R.string.settings_weight_neutral),
                    )
                },
            )
        }
    }
}

@Composable
private fun PrivacySheet(
    visible: Boolean,
    state: SettingsState,
    onDismiss: () -> Unit,
    onAppLock: (Boolean) -> Unit,
) {
    if (!visible) return
    OhModalSheet(
        visible = true,
        onDismiss = onDismiss,
        title = stringResource(R.string.settings_privacy),
        cancelLabel = stringResource(R.string.settings_close),
        onCancel = onDismiss,
    ) {
        OhInsetGroup {
            OhRow(
                title = stringResource(R.string.settings_app_lock),
                subtitle = if (state.appLockAvailable) {
                    stringResource(R.string.settings_app_lock_body)
                } else {
                    stringResource(R.string.settings_lock_unavailable)
                },
                enabled = state.appLockAvailable,
                mergeSemantics = false,
                trailing = {
                    OhSwitch(
                        checked = state.appLock,
                        enabled = state.appLockAvailable,
                        onCheckedChange = onAppLock,
                        contentDescription = stringResource(R.string.settings_app_lock),
                    )
                },
            )
        }
        Spacer(Modifier.height(OhTheme.space.s5))
        Text(
            text = stringResource(R.string.settings_privacy_body),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
    }
}

@Composable
private fun AboutSheet(visible: Boolean, state: SettingsState, onDismiss: () -> Unit) {
    if (!visible) return
    OhModalSheet(
        visible = true,
        onDismiss = onDismiss,
        title = stringResource(R.string.settings_about),
        cancelLabel = stringResource(R.string.settings_close),
        onCancel = onDismiss,
    ) {
        Text(
            text = stringResource(R.string.settings_about_version, state.versionName),
            style = OhTheme.type.body,
            color = OhTheme.colors.label,
        )
        Spacer(Modifier.height(OhTheme.space.s5))
        Text(
            text = stringResource(R.string.settings_about_medical),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
        Spacer(Modifier.height(OhTheme.space.s5))
        Text(
            text = stringResource(R.string.settings_about_licence),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
        Spacer(Modifier.height(OhTheme.space.s5))
        Text(
            text = stringResource(R.string.settings_about_food_data),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
        Spacer(Modifier.height(OhTheme.space.s5))
        Text(
            text = stringResource(R.string.settings_about_sqlcipher),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
    }
}

@Composable
private fun PassphraseSheet(
    target: Passphrase,
    onDismiss: () -> Unit,
    onSubmit: (CharArray) -> Unit,
) {
    if (target == Passphrase.NONE) return
    var first by remember(target) { mutableStateOf("") }
    var second by remember(target) { mutableStateOf("") }
    var error by remember(target) { mutableStateOf(false) }
    val export = target == Passphrase.EXPORT
    OhModalSheet(
        visible = true,
        onDismiss = onDismiss,
        title = if (export) {
            stringResource(R.string.settings_export)
        } else {
            stringResource(R.string.settings_restore)
        },
        primaryLabel = if (export) {
            stringResource(R.string.settings_export_action)
        } else {
            stringResource(R.string.settings_restore_action)
        },
        // An export sealed with an empty passphrase is not protected at all.
        primaryEnabled = first.isNotEmpty() && (!export || second.isNotEmpty()),
        onPrimary = {
            if (export && first != second) {
                error = true
            } else {
                onSubmit(first.toCharArray())
            }
        },
        cancelLabel = stringResource(R.string.settings_cancel),
        onCancel = onDismiss,
    ) {
        Text(
            text = if (export) {
                stringResource(R.string.settings_export_passphrase_body)
            } else {
                stringResource(R.string.settings_restore_passphrase_body)
            },
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
        Spacer(Modifier.height(OhTheme.space.s5))
        OhTextField(
            value = first,
            onValueChange = {
                first = it
                error = false
            },
            placeholder = stringResource(R.string.settings_passphrase),
            contentDescription = stringResource(R.string.settings_passphrase),
            visualTransformation = PasswordVisualTransformation(),
            error = if (error) stringResource(R.string.settings_passwords_differ) else null,
        )
        if (export) {
            Spacer(Modifier.height(OhTheme.space.s5))
            OhTextField(
                value = second,
                onValueChange = {
                    second = it
                    error = false
                },
                placeholder = stringResource(R.string.settings_passphrase_repeat),
                contentDescription = stringResource(R.string.settings_passphrase_repeat),
                visualTransformation = PasswordVisualTransformation(),
            )
        }
    }
}

@Composable
private fun themeLabel(theme: ThemeChoice): String = stringResource(
    when (theme) {
        ThemeChoice.SYSTEM -> R.string.settings_theme_system
        ThemeChoice.DARK -> R.string.settings_theme_dark
        ThemeChoice.LIGHT -> R.string.settings_theme_light
    },
)

@Composable
private fun OhAccent.label(): String = stringResource(
    when (this) {
        OhAccent.Brand -> R.string.accent_brand
        OhAccent.Lime -> R.string.accent_lime
        OhAccent.Sky -> R.string.accent_sky
        OhAccent.Orange -> R.string.accent_orange
        OhAccent.Violet -> R.string.accent_violet
        OhAccent.Pink -> R.string.accent_pink
        OhAccent.Red -> R.string.accent_red
        OhAccent.Teal -> R.string.accent_teal
        OhAccent.Gold -> R.string.accent_gold
    },
)

@Composable
private fun HealthModule.label(): String = stringResource(
    when (this) {
        HealthModule.TRAINING -> R.string.module_training
        HealthModule.NUTRITION -> R.string.module_nutrition
        HealthModule.STEPS -> R.string.module_steps
        HealthModule.YOGA -> R.string.module_yoga
        HealthModule.BODY -> R.string.module_body
        HealthModule.VITALS -> R.string.module_vitals
        HealthModule.SLEEP -> R.string.module_sleep
        HealthModule.ACTIVITIES -> R.string.module_activities
        HealthModule.MENTAL_HEALTH -> R.string.module_mental_health
        HealthModule.CYCLE -> R.string.module_cycle
        HealthModule.FAMILY -> R.string.module_family
        HealthModule.COACH -> R.string.module_coach
        HealthModule.INTEGRATIONS -> R.string.module_integrations
    },
)

@Composable
private fun ModulePreset.label(): String = stringResource(
    when (this) {
        ModulePreset.JUST_TRAINING -> R.string.preset_training
        ModulePreset.TRAINING_AND_BODY -> R.string.preset_training_body
        ModulePreset.EVERYTHING -> R.string.preset_everything
        ModulePreset.CUSTOM -> R.string.preset_custom
    },
)

@Composable
private fun ProfileGoal.label(): String = stringResource(
    when (this) {
        ProfileGoal.LOSE_FAT -> R.string.goal_lose_fat
        ProfileGoal.MAINTAIN -> R.string.goal_maintain
        ProfileGoal.GAIN_MUSCLE -> R.string.goal_gain_muscle
        ProfileGoal.IMPROVE_HEALTH -> R.string.goal_improve_health
        ProfileGoal.MANAGE_CONDITION -> R.string.goal_manage_condition
        ProfileGoal.JUST_TRACK -> R.string.goal_just_track
    },
)

/** A passphrase that is not going to be used leaves no copy behind in memory. */
private fun CharArray?.cleared(): CharArray? {
    this?.fill('\u0000')
    return null
}

private fun convertLength(text: String, from: UnitSystem, to: UnitSystem): String {
    val value = text.replace(',', '.').toDoubleOrNull() ?: return text
    val converted = when {
        from == UnitSystem.METRIC && to == UnitSystem.IMPERIAL ->
            app.openhealth.core.units.Measure(value, app.openhealth.core.units.UcumUnit.CENTIMETRE)
                .convertTo(app.openhealth.core.units.UcumUnit.INCH).value
        from == UnitSystem.IMPERIAL && to == UnitSystem.METRIC ->
            app.openhealth.core.units.Measure(value, app.openhealth.core.units.UcumUnit.INCH)
                .convertTo(app.openhealth.core.units.UcumUnit.CENTIMETRE).value
        else -> value
    }
    return String.format(java.util.Locale.getDefault(), "%.1f", converted)
}

private fun convertMass(text: String, from: UnitSystem, to: UnitSystem): String {
    val value = text.replace(',', '.').toDoubleOrNull() ?: return text
    val converted = when {
        from == UnitSystem.METRIC && to == UnitSystem.IMPERIAL ->
            app.openhealth.core.units.Measure(value, app.openhealth.core.units.UcumUnit.KILOGRAM)
                .convertTo(app.openhealth.core.units.UcumUnit.POUND).value
        from == UnitSystem.IMPERIAL && to == UnitSystem.METRIC ->
            app.openhealth.core.units.Measure(value, app.openhealth.core.units.UcumUnit.POUND)
                .convertTo(app.openhealth.core.units.UcumUnit.KILOGRAM).value
        else -> value
    }
    return String.format(java.util.Locale.getDefault(), "%.1f", converted)
}
