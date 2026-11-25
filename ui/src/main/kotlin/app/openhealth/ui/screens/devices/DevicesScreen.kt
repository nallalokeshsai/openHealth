package app.openhealth.ui.screens.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import app.openhealth.core.activity.DeviceKindName
import app.openhealth.ui.R
import app.openhealth.ui.components.OhButton
import app.openhealth.ui.components.OhButtonSize
import app.openhealth.ui.components.OhButtonVariant
import app.openhealth.ui.components.OhCard
import app.openhealth.ui.components.OhDialog
import app.openhealth.ui.components.OhIconButton
import app.openhealth.ui.components.OhInsetGroup
import app.openhealth.ui.components.OhModalSheet
import app.openhealth.ui.components.OhRow
import app.openhealth.ui.components.OhTextField
import app.openhealth.ui.components.ohClickable
import app.openhealth.ui.components.rememberInteraction
import app.openhealth.ui.icons.OhIcons
import app.openhealth.ui.icons.OhModuleIcons
import app.openhealth.ui.icons.OhObjectIcons
import app.openhealth.ui.screens.common.OhPageHeader
import app.openhealth.ui.screens.common.OhSectionTitle
import app.openhealth.ui.theme.OhTheme

/** A half turn puts the down chevron up, so the closed icon set needs no new glyph. */
private const val UPSIDE_DOWN_DEGREES = 180f

/** Six digits is every step count a person will ever type for one day. */
private const val MAX_STEP_DIGITS = 6

/**
 * Devices and integrations (`DESIGN.md` §14.2 Settings and its Devices recipe,
 * `BUILD-ROADMAP.md` §B8). One page, grouped lists, hairlines and status inks: the same
 * surface language as Settings, because this is where a source is chosen and explained,
 * not a new root.
 */
@Composable
fun DevicesScreen(
    model: DevicesModel,
    onBack: () -> Unit,
    onToggleSteps: () -> Unit,
    onCorrectSteps: (Int) -> Unit,
    onExcludeToday: () -> Unit,
    onRequestHealthConnect: () -> Unit,
    onSyncHealthConnect: (Boolean) -> Unit,
    onImportGarmin: () -> Unit,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onPair: (FoundDeviceModel) -> Unit,
    onUnpair: (String) -> Unit,
    onConnect: (String) -> Unit,
    onMovePriority: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var correcting by rememberSaveable { mutableStateOf(false) }
    var confirmingForget by rememberSaveable { mutableStateOf<String?>(null) }
    var proving by rememberSaveable { mutableStateOf(false) }
    val forgetting = model.bluetooth.devices.firstOrNull { it.id == confirmingForget }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OhTheme.colors.bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = OhTheme.space.s6),
    ) {
        OhPageHeader(title = stringResource(R.string.devices_title), onBack = onBack)
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            model.message?.let { message ->
                Text(
                    text = message,
                    style = OhTheme.type.footnote,
                    color = OhTheme.colors.accInk,
                    modifier = Modifier.padding(bottom = OhTheme.space.s3),
                )
            }
            StepsCard(
                model.steps,
                onToggleSteps,
                onCorrect = { correcting = true },
                onProvenance = { proving = true },
            )
            OhSectionTitle(stringResource(R.string.devices_health_connect))
            HealthConnectCard(model.healthConnect, onRequestHealthConnect, onSyncHealthConnect)
            OhSectionTitle(stringResource(R.string.devices_garmin))
            GarminCard(model.garmin, onImportGarmin)
            OhSectionTitle(stringResource(R.string.devices_bluetooth))
            BluetoothCard(
                model.bluetooth,
                onScan,
                onStopScan,
                onPair,
                onForget = { id -> confirmingForget = id },
                onConnect,
            )
            OhSectionTitle(stringResource(R.string.devices_priority))
            PriorityCard(model.priority, onMovePriority)
            Spacer(Modifier.height(OhTheme.space.s3))
            Text(
                text = model.note,
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
                modifier = Modifier.padding(bottom = OhTheme.space.s8),
            )
        }
    }
    CorrectStepsSheet(
        visible = correcting,
        current = model.steps,
        onDismiss = { correcting = false },
        onSave = {
            correcting = false
            onCorrectSteps(it)
        },
        onExclude = {
            correcting = false
            onExcludeToday()
        },
    )
    StepsProvenanceSheet(
        visible = proving,
        current = model.steps,
        onDismiss = { proving = false },
    )
    OhDialog(
        visible = forgetting != null,
        onDismiss = { confirmingForget = null },
        title = stringResource(R.string.devices_forget_title, forgetting?.name.orEmpty()),
        message = stringResource(R.string.devices_forget_body),
        primaryLabel = stringResource(R.string.devices_forget_action),
        destructive = true,
        onPrimary = {
            forgetting?.let { onUnpair(it.id) }
            confirmingForget = null
        },
        cancelLabel = stringResource(R.string.devices_forget_cancel),
        onCancel = { confirmingForget = null },
    )
}

@Composable
private fun StepsCard(
    model: StepsSourceModel,
    onToggle: () -> Unit,
    onCorrect: () -> Unit,
    onProvenance: () -> Unit,
) {
    OhCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.devices_steps_title),
                style = OhTheme.type.title3,
                color = OhTheme.colors.label,
            )
            Text(
                text = if (model.tracking) {
                    stringResource(R.string.devices_on)
                } else {
                    stringResource(R.string.devices_off)
                },
                style = OhTheme.type.footnote,
                color = if (model.tracking) OhTheme.colors.greenInk else OhTheme.colors.label2,
            )
        }
        Spacer(Modifier.height(OhTheme.space.s2))
        Text(
            text = model.todayText,
            style = OhTheme.type.display,
            color = OhTheme.colors.label,
        )
        model.sourceLine?.let { line ->
            Spacer(Modifier.height(OhTheme.space.s1))
            // The provenance affordance is the number's own source line, not a second
            // button: the accent appears once and the tap lands where the fact is
            // (`DESIGN.md` §7.1).
            val interaction = rememberInteraction()
            Text(
                text = line,
                style = OhTheme.type.footnote,
                color = OhTheme.colors.accInk,
                modifier = Modifier
                    .ohClickable(interaction, role = Role.Button, onClick = onProvenance)
                    .semantics {
                        contentDescription = line
                        role = Role.Button
                    },
            )
        }
        model.reasonNote?.let { note ->
            Spacer(Modifier.height(OhTheme.space.s0))
            Text(text = note, style = OhTheme.type.footnote, color = OhTheme.colors.label2)
        }
        model.otherSources.forEach { other ->
            Spacer(Modifier.height(OhTheme.space.s0))
            Text(
                text = other,
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
            )
        }
        model.lastReadingText?.let { last ->
            Spacer(Modifier.height(OhTheme.space.s0))
            Text(text = last, style = OhTheme.type.footnote, color = OhTheme.colors.label2)
        }
        Spacer(Modifier.height(OhTheme.space.s4))
        Column(verticalArrangement = Arrangement.spacedBy(OhTheme.space.s1)) {
            OhButton(
                text = if (model.tracking) {
                    stringResource(R.string.devices_steps_stop)
                } else {
                    stringResource(R.string.devices_steps_start)
                },
                onClick = onToggle,
                variant = OhButtonVariant.Secondary,
                size = OhButtonSize.Small,
                enabled = model.available && !model.moduleOff,
            )
            OhButton(
                text = stringResource(R.string.devices_steps_correct),
                onClick = onCorrect,
                variant = OhButtonVariant.Quiet,
                size = OhButtonSize.Small,
                enabled = model.canCorrect,
            )
        }
        Spacer(Modifier.height(OhTheme.space.s2))
        Text(
            text = model.permissionNote,
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
    }
}

@Composable
private fun HealthConnectCard(
    model: HealthConnectModel,
    onRequest: () -> Unit,
    onSync: (Boolean) -> Unit,
) {
    OhInsetGroup {
        OhRow(
            title = stringResource(R.string.devices_health_connect),
            subtitle = model.statusText,
            leadingIcon = OhModuleIcons.activity,
            leadingTint = OhTheme.colors.surface2,
            leadingInk = OhTheme.colors.label,
            trailing = {
                if (!model.enabled && model.available) {
                    OhButton(
                        text = stringResource(R.string.devices_connect),
                        onClick = onRequest,
                        variant = OhButtonVariant.Secondary,
                        size = OhButtonSize.ExtraSmall,
                    )
                }
            },
        )
        if (model.enabled) {
            OhRow(
                title = stringResource(R.string.devices_health_connect_sync),
                subtitle = model.lastSyncText,
                leadingIcon = OhIcons.refresh,
                leadingTint = OhTheme.colors.surface2,
                leadingInk = OhTheme.colors.label,
                showDivider = true,
                showChevron = true,
                onClick = { onSync(false) },
            )
            OhRow(
                title = stringResource(R.string.devices_health_connect_history),
                subtitle = if (model.historyImported) {
                    stringResource(R.string.devices_health_connect_history_done)
                } else {
                    stringResource(R.string.devices_health_connect_history_body)
                },
                leadingIcon = OhIcons.timer,
                leadingTint = OhTheme.colors.surface2,
                leadingInk = OhTheme.colors.label,
                showDivider = true,
                showChevron = true,
                enabled = !model.historyImported,
                onClick = { onSync(true) },
            )
        }
    }
    Spacer(Modifier.height(OhTheme.space.s2))
    OhInsetGroup {
        model.provides.forEachIndexed { index, provide ->
            OhRow(
                title = provide.label,
                leadingIcon = if (provide.granted) OhIcons.check else OhIcons.close,
                leadingTint = OhTheme.colors.surface2,
                leadingInk = OhTheme.colors.label,
                value = if (provide.granted) {
                    stringResource(R.string.devices_granted)
                } else {
                    stringResource(R.string.devices_not_granted)
                },
                showDivider = index > 0,
            )
        }
    }
    Spacer(Modifier.height(OhTheme.space.s2))
    Text(
        text = model.note,
        style = OhTheme.type.footnote,
        color = OhTheme.colors.label2,
    )
    Spacer(Modifier.height(OhTheme.space.s1))
    Text(
        text = model.vendorNote,
        style = OhTheme.type.footnote,
        color = OhTheme.colors.label2,
    )
}

@Composable
private fun GarminCard(model: GarminModel, onImport: () -> Unit) {
    OhInsetGroup {
        OhRow(
            title = stringResource(R.string.devices_garmin_import),
            subtitle = model.lastImportText ?: stringResource(R.string.devices_garmin_none),
            leadingIcon = OhIcons.export,
            leadingTint = OhTheme.colors.surface2,
            leadingInk = OhTheme.colors.label,
            showChevron = true,
            onClick = onImport,
        )
    }
    model.lastImportDetail?.let { detail ->
        Spacer(Modifier.height(OhTheme.space.s1))
        Text(text = detail, style = OhTheme.type.footnote, color = OhTheme.colors.label2)
    }
    model.stepsFrom?.let { from ->
        Spacer(Modifier.height(OhTheme.space.s1))
        Text(text = from, style = OhTheme.type.footnote, color = OhTheme.colors.label2)
    }
    Spacer(Modifier.height(OhTheme.space.s2))
    Text(text = model.note, style = OhTheme.type.footnote, color = OhTheme.colors.label2)
    Spacer(Modifier.height(OhTheme.space.s1))
    Text(
        text = model.readOnlyNote,
        style = OhTheme.type.footnote,
        color = OhTheme.colors.label2,
    )
}

@Composable
private fun BluetoothCard(
    model: BluetoothModel,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onPair: (FoundDeviceModel) -> Unit,
    onForget: (String) -> Unit,
    onConnect: (String) -> Unit,
) {
    if (model.liveHeartRate != null) {
        OhInsetGroup {
            OhRow(
                title = stringResource(R.string.devices_live_heart_rate),
                value = "${model.liveHeartRate}",
                unit = "bpm",
                leadingIcon = OhModuleIcons.heart,
                leadingTint = OhTheme.colors.surface2,
                leadingInk = OhTheme.colors.label,
            )
        }
        Spacer(Modifier.height(OhTheme.space.s2))
    }
    if (model.devices.isEmpty()) {
        OhInsetGroup {
            OhRow(
                title = stringResource(R.string.devices_bluetooth_none),
                subtitle = stringResource(R.string.devices_bluetooth_none_body),
            )
        }
    } else {
        OhInsetGroup {
            model.devices.forEachIndexed { index, device ->
                OhRow(
                    title = device.name,
                    subtitle = device.statusText,
                    value = device.lastSeenText,
                    leadingIcon = iconFor(device.kind),
                    leadingTint = OhTheme.colors.surface2,
                    leadingInk = OhTheme.colors.label,
                    showDivider = index > 0,
                    mergeSemantics = false,
                    onClick = { onConnect(device.id) },
                    trailing = {
                        OhIconButton(
                            icon = OhIcons.delete,
                            contentDescription = stringResource(
                                R.string.devices_forget,
                                device.name,
                            ),
                            tint = OhTheme.colors.redInk,
                            onClick = { onForget(device.id) },
                        )
                    },
                )
            }
        }
    }
    Spacer(Modifier.height(OhTheme.space.s2))
    if (model.scanning) {
        OhButton(
            text = stringResource(R.string.devices_scan_stop),
            onClick = onStopScan,
            variant = OhButtonVariant.Secondary,
            size = OhButtonSize.Small,
        )
    } else {
        OhButton(
            text = stringResource(R.string.devices_scan),
            onClick = onScan,
            variant = OhButtonVariant.Secondary,
            size = OhButtonSize.Small,
            enabled = model.available,
        )
    }
    if (model.found.isNotEmpty()) {
        Spacer(Modifier.height(OhTheme.space.s2))
        OhInsetGroup {
            model.found.forEachIndexed { index, found ->
                OhRow(
                    title = found.name,
                    subtitle = found.kindLabel,
                    leadingIcon = iconFor(found.kind),
                    leadingTint = OhTheme.colors.surface2,
                    leadingInk = OhTheme.colors.label,
                    showDivider = index > 0,
                    mergeSemantics = false,
                    trailing = {
                        OhButton(
                            text = stringResource(R.string.devices_pair),
                            onClick = { onPair(found) },
                            variant = OhButtonVariant.Secondary,
                            size = OhButtonSize.ExtraSmall,
                        )
                    },
                )
            }
        }
    }
    model.offNote?.let { off ->
        Spacer(Modifier.height(OhTheme.space.s2))
        Text(text = off, style = OhTheme.type.footnote, color = OhTheme.colors.label)
    }
    Spacer(Modifier.height(OhTheme.space.s2))
    Text(text = model.note, style = OhTheme.type.footnote, color = OhTheme.colors.label2)
}

@Composable
private fun PriorityCard(model: PriorityModel, onMove: (String, Boolean) -> Unit) {
    if (model.rows.isEmpty()) {
        OhInsetGroup {
            OhRow(
                title = stringResource(R.string.devices_priority_empty),
            )
        }
    } else {
        OhInsetGroup {
            model.rows.forEachIndexed { index, row ->
                OhRow(
                    title = row.label,
                    subtitle = row.detail,
                    leading = {
                        Box(
                            modifier = Modifier.width(OhTheme.space.s6),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = OhTheme.type.footnote,
                                color = if (row.isFirst) {
                                    OhTheme.colors.accInk
                                } else {
                                    OhTheme.colors.label2
                                },
                            )
                        }
                    },
                    showDivider = index > 0,
                    mergeSemantics = false,
                    trailing = {
                        OhIconButton(
                            icon = OhIcons.chevronDown,
                            contentDescription = stringResource(
                                R.string.devices_move_up,
                                row.label,
                            ),
                            modifier = Modifier.rotate(UPSIDE_DOWN_DEGREES),
                            enabled = !row.isFirst,
                            onClick = { onMove(row.sourceId, true) },
                        )
                        OhIconButton(
                            icon = OhIcons.chevronDown,
                            contentDescription = stringResource(
                                R.string.devices_move_down,
                                row.label,
                            ),
                            enabled = !row.isLast,
                            onClick = { onMove(row.sourceId, false) },
                        )
                    },
                )
            }
        }
    }
    Spacer(Modifier.height(OhTheme.space.s2))
    Text(text = model.note, style = OhTheme.type.footnote, color = OhTheme.colors.label2)
}

private fun iconFor(kind: DeviceKindName) = when (kind) {
    DeviceKindName.HEART_RATE -> OhModuleIcons.heart
    DeviceKindName.SCALE -> OhObjectIcons.scale
    DeviceKindName.BLOOD_PRESSURE -> OhObjectIcons.cuff
}

@Composable
private fun CorrectStepsSheet(
    visible: Boolean,
    current: StepsSourceModel,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
    onExclude: () -> Unit,
) {
    if (!visible) return
    var text by rememberSaveable { mutableStateOf("") }
    val value = text.toIntOrNull()
    OhModalSheet(
        visible = true,
        onDismiss = onDismiss,
        title = stringResource(R.string.devices_correct_title),
        primaryLabel = stringResource(R.string.devices_correct_save),
        primaryEnabled = value != null && value > 0,
        onPrimary = { value?.let(onSave) },
        cancelLabel = stringResource(R.string.devices_correct_cancel),
        onCancel = onDismiss,
    ) {
        Text(
            text = stringResource(R.string.devices_correct_body),
            style = OhTheme.type.body,
            color = OhTheme.colors.label,
        )
        Spacer(Modifier.height(OhTheme.space.s3))
        OhTextField(
            value = text,
            onValueChange = { entered ->
                text = entered.filter(Char::isDigit).take(MAX_STEP_DIGITS)
            },
            placeholder = stringResource(R.string.devices_correct_field),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            contentDescription = stringResource(R.string.devices_correct_field),
        )
        Spacer(Modifier.height(OhTheme.space.s4))
        OhButton(
            text = stringResource(R.string.devices_exclude_today),
            onClick = onExclude,
            variant = OhButtonVariant.Quiet,
            size = OhButtonSize.Small,
            enabled = current.canCorrect,
        )
        Spacer(Modifier.height(OhTheme.space.s1))
        Text(
            text = stringResource(R.string.devices_exclude_body),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
    }
}

@Composable
private fun StepsProvenanceSheet(
    visible: Boolean,
    current: StepsSourceModel,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    OhModalSheet(
        visible = true,
        onDismiss = onDismiss,
        title = stringResource(R.string.devices_provenance_title),
        cancelLabel = stringResource(R.string.devices_close),
        onCancel = onDismiss,
    ) {
        current.provenance.forEach { line ->
            OhRow(
                title = line.label,
                subtitle = line.detail,
                value = line.value,
            )
        }
        Spacer(Modifier.height(OhTheme.space.s2))
        Text(
            text = stringResource(R.string.devices_provenance_body),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
    }
}
