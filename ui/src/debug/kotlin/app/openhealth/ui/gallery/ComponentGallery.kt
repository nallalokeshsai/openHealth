@file:Suppress("MagicNumber", "LongParameterList", "LongMethod", "CyclomaticComplexMethod")

package app.openhealth.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import app.openhealth.ui.component.OhButton
import app.openhealth.ui.component.OhButtonSize
import app.openhealth.ui.component.OhButtonVariant
import app.openhealth.ui.component.OhCard
import app.openhealth.ui.component.OhCardHeader
import app.openhealth.ui.component.OhChip
import app.openhealth.ui.component.OhDialogCard
import app.openhealth.ui.component.OhIconButton
import app.openhealth.ui.component.OhInsetGroup
import app.openhealth.ui.component.OhKpiBand
import app.openhealth.ui.component.OhKpiFigure
import app.openhealth.ui.component.OhLabelledField
import app.openhealth.ui.component.OhMediaRow
import app.openhealth.ui.component.OhSearchField
import app.openhealth.ui.component.OhSegmentedControl
import app.openhealth.ui.component.OhSheetSurface
import app.openhealth.ui.component.OhSlider
import app.openhealth.ui.component.OhStatTile
import app.openhealth.ui.component.OhStepper
import app.openhealth.ui.component.OhSwitch
import app.openhealth.ui.component.OhTab
import app.openhealth.ui.component.OhTabBar
import app.openhealth.ui.component.OhTextField
import app.openhealth.ui.component.OhTimerBar
import app.openhealth.ui.component.OhToast
import app.openhealth.ui.component.OhTombstoneRow
import app.openhealth.ui.component.OhTopBar
import app.openhealth.ui.icon.OhIcon
import app.openhealth.ui.icon.OhIconName
import app.openhealth.ui.theme.OhAccentPreset
import app.openhealth.ui.theme.OhTheme
import app.openhealth.ui.theme.semiboldWeight

/**
 * The B1 component gallery. It is the visual proof of DESIGN.md §5 and §6, and the surface the
 * screenshot tests render one entry at a time.
 *
 * Debug builds only: `MainActivity` shows it under `BuildConfig.DEBUG` and the blank placeholder
 * screen otherwise, so nothing here is reachable in a release build.
 */
@Composable
fun ComponentGallery() {
    var dark by remember { mutableStateOf(true) }
    var accent by remember { mutableStateOf(OhAccentPreset.Brand) }
    var fontScale by remember { mutableStateOf(1f) }
    var rtl by remember { mutableStateOf(false) }
    var reduceMotion by remember { mutableStateOf(false) }

    OhTheme(
        darkTheme = dark,
        accent = accent,
        reduceMotion = reduceMotion
    ) {
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density.density, fontScale),
            LocalLayoutDirection provides
                if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
        ) {
            GalleryBody(
                dark = dark,
                onDarkChange = { dark = it },
                accent = accent,
                onAccentChange = { accent = it },
                fontScale = fontScale,
                onFontScaleChange = { fontScale = it },
                rtl = rtl,
                onRtlChange = { rtl = it },
                reduceMotion = reduceMotion,
                onReduceMotionChange = { reduceMotion = it }
            )
        }
    }
}

@Composable
private fun GalleryBody(
    dark: Boolean,
    onDarkChange: (Boolean) -> Unit,
    accent: OhAccentPreset,
    onAccentChange: (OhAccentPreset) -> Unit,
    fontScale: Float,
    onFontScaleChange: (Float) -> Unit,
    rtl: Boolean,
    onRtlChange: (Boolean) -> Unit,
    reduceMotion: Boolean,
    onReduceMotionChange: (Boolean) -> Unit
) {
    val colors = OhTheme.colors
    val type = OhTheme.type
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        OhTopBar(
            date = "Mon 23 Nov",
            onQuickAdd = {},
            onSettings = {},
            showSyncDot = true,
            modifier = Modifier.statusBarsPadding()
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OhTheme.geometry.screenPadding)
        ) {
            Spacer(Modifier.height(OhTheme.space.space6))
            Text("Design system", style = type.large, color = colors.label)
            Text(
                "The components and states of DESIGN.md §5 and §6",
                style = type.subhead,
                color = colors.label2
            )
            Spacer(Modifier.height(OhTheme.space.space7))
            GalleryControls(
                dark = dark,
                onDarkChange = onDarkChange,
                accent = accent,
                onAccentChange = onAccentChange,
                fontScale = fontScale,
                onFontScaleChange = onFontScaleChange,
                rtl = rtl,
                onRtlChange = onRtlChange,
                reduceMotion = reduceMotion,
                onReduceMotionChange = onReduceMotionChange
            )
            GalleryEntry.entries.forEach { entry ->
                Spacer(Modifier.height(OhTheme.space.space8))
                Text(entry.title, style = type.footnote.semiboldWeight(), color = colors.label2)
                Spacer(Modifier.height(OhTheme.space.space3))
                GalleryEntryContent(entry)
            }
            Spacer(Modifier.height(OhTheme.space.space8))
        }
        OhTabBar(
            selected = OhTab.Today,
            onSelect = {},
            dots = setOf(OhTab.Today),
            modifier = Modifier.navigationBarsPadding()
        )
    }
}

@Composable
private fun GalleryControls(
    dark: Boolean,
    onDarkChange: (Boolean) -> Unit,
    accent: OhAccentPreset,
    onAccentChange: (OhAccentPreset) -> Unit,
    fontScale: Float,
    onFontScaleChange: (Float) -> Unit,
    rtl: Boolean,
    onRtlChange: (Boolean) -> Unit,
    reduceMotion: Boolean,
    onReduceMotionChange: (Boolean) -> Unit
) {
    val colors = OhTheme.colors
    val type = OhTheme.type
    Column {
        OhSegmentedControl(
            options = listOf("Dark", "Light"),
            selectedIndex = if (dark) 0 else 1,
            onSelect = { onDarkChange(it == 0) }
        )
        Spacer(Modifier.height(OhTheme.space.space5))
        Text("Accent", style = type.footnote, color = colors.label2)
        Spacer(Modifier.height(OhTheme.space.space2))
        Row(horizontalArrangement = Arrangement.spacedBy(OhTheme.space.space2)) {
            OhAccentPreset.entries.take(5).forEach { preset ->
                OhChip(
                    label = preset.key.replaceFirstChar { it.uppercase() },
                    selected = accent == preset,
                    onClick = { onAccentChange(preset) }
                )
            }
        }
        Spacer(Modifier.height(OhTheme.space.space2))
        Row(horizontalArrangement = Arrangement.spacedBy(OhTheme.space.space2)) {
            OhAccentPreset.entries.drop(5).forEach { preset ->
                OhChip(
                    label = preset.key.replaceFirstChar { it.uppercase() },
                    selected = accent == preset,
                    onClick = { onAccentChange(preset) }
                )
            }
        }
        Spacer(Modifier.height(OhTheme.space.space5))
        OhInsetGroup {
            row(
                title = "200% text",
                subtitle = "The layout must survive it without losing an action",
                trailing = {
                    OhSwitch(checked = fontScale > 1f, onCheckedChange = { onFontScaleChange(if (it) 2f else 1f) })
                }
            )
            row(
                title = "Right to left",
                subtitle = "Logical start and end only; the body map is never mirrored",
                trailing = { OhSwitch(checked = rtl, onCheckedChange = onRtlChange) }
            )
            row(
                title = "Reduce motion",
                subtitle = "Presses, sheets and pages become instant",
                trailing = { OhSwitch(checked = reduceMotion, onCheckedChange = onReduceMotionChange) }
            )
        }
    }
}

/** One gallery entry: a component with its states, rendered alone for the screenshot tests. */
enum class GalleryEntry(val title: String) {
    Card("Card and card header"),
    InsetGroup("Inset group and rows"),
    MediaRow("Media row"),
    StatTile("Stat tile"),
    KpiBand("KPI band"),
    Buttons("Buttons: six variants, three sizes, disabled"),
    IconButton("Icon button"),
    Chips("Chips"),
    Segments("Segmented control"),
    Switch("Switch"),
    Stepper("Stepper"),
    Slider("Slider"),
    Fields("Text and search fields"),
    TopBar("Global top bar"),
    TabBar("Five-tab bar with raised Train"),
    Sheets("Sheet"),
    Dialogs("Dialog"),
    Toasts("Toast and undo"),
    Timers("Timer bar"),
    Icons("Icon set"),
    States("States: offline, empty, loading, error, record, conflict")
}

@Composable
fun GalleryEntryContent(entry: GalleryEntry, modifier: Modifier = Modifier) {
    Column(modifier = modifier.testTag("gallery-entry").fillMaxWidth()) {
        when (entry) {
            GalleryEntry.Card -> {
                OhCard {
                    OhCardHeader(
                        glyph = OhIconName.Sleep,
                        eyebrow = "Readiness",
                        title = "Ready for a normal day",
                        subtitle = "Sleep and resting heart rate are inside your range",
                        trailing = {
                            OhIconButton(
                                icon = OhIconName.ChevronRight,
                                contentDescription = "Open readiness",
                                onClick = {}
                            )
                        }
                    )
                }
                OhCard(onClick = {}) {
                    OhCardHeader(
                        glyph = OhIconName.Heart,
                        eyebrow = "Vitals",
                        title = "Resting heart rate",
                        subtitle = "Watch · 07:42"
                    )
                }
            }

            GalleryEntry.InsetGroup -> Column {
                OhInsetGroup {
                    row(
                        title = "Profile",
                        subtitle = "Name, goal, units",
                        icon = OhIconName.Body,
                        trailingChevron = true,
                        onClick = {}
                    )
                    row(
                        title = "Modules",
                        value = "13 on",
                        icon = OhIconName.More,
                        onClick = {}
                    )
                    row(
                        title = "Devices",
                        subtitle = "No scale connected yet",
                        icon = OhIconName.Watch,
                        value = "0",
                        onClick = {}
                    )
                    row(
                        title = "Export all data",
                        subtitle = "A file you keep",
                        icon = OhIconName.Export,
                        onClick = {}
                    )
                }
                // §5.3: a destructive row is separated from the rest by 22 dp and says exactly
                // what it deletes.
                Spacer(Modifier.height(OhTheme.geometry.destructiveGroupGap))
                OhInsetGroup {
                    row(
                        title = "Delete this entry",
                        icon = OhIconName.Delete,
                        destructive = true,
                        onClick = {}
                    )
                }
                Spacer(Modifier.height(OhTheme.geometry.destructiveGroupGap))
                OhInsetGroup {
                    row(
                        title = "Not available yet",
                        subtitle = "Disabled rows still explain themselves",
                        icon = OhIconName.Lock,
                        enabled = false
                    )
                }
            }

            GalleryEntry.MediaRow -> {
                OhMediaRow(
                    title = "Barbell back squat",
                    subtitle = "Quadriceps · Barbell",
                    trailing = {
                        OhIconButton(
                            icon = OhIconName.Add,
                            contentDescription = "Add to routine",
                            onClick = {}
                        )
                    }
                )
                Spacer(Modifier.height(OhTheme.space.space2))
                OhMediaRow(
                    title = "Greek yoghurt",
                    subtitle = "per 100 g · Open Food Facts",
                    trailing = { Text("97 kcal", style = OhTheme.type.footnote, color = OhTheme.colors.label2) }
                )
            }

            GalleryEntry.StatTile -> Row(
                horizontalArrangement = Arrangement.spacedBy(OhTheme.geometry.statTileGap)
            ) {
                OhStatTile(
                    label = "Body fat",
                    value = "18.4%",
                    delta = "−0.4% in a month",
                    modifier = Modifier.weight(1f)
                )
                OhStatTile(
                    label = "Lean mass",
                    value = "63.2 kg",
                    modifier = Modifier.weight(1f)
                )
            }

            GalleryEntry.KpiBand -> OhKpiBand(
                figures = listOf(
                    OhKpiFigure("Duration", "48 min"),
                    OhKpiFigure("Volume", "4 120 kg"),
                    OhKpiFigure("Sets", "16")
                )
            )

            GalleryEntry.Buttons -> Column {
                OhButton(label = "Start session", onClick = {})
                Spacer(Modifier.height(OhTheme.space.space2))
                OhButton(label = "Secondary action", onClick = {}, variant = OhButtonVariant.Secondary)
                Spacer(Modifier.height(OhTheme.space.space2))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(OhTheme.space.space2)
                ) {
                    OhButton(label = "Tinted", onClick = {
                    }, variant = OhButtonVariant.Tinted, size = OhButtonSize.Small)
                    OhButton(label = "Delete", onClick = {
                    }, variant = OhButtonVariant.Danger, size = OhButtonSize.Small)
                    OhButton(label = "See all", onClick = {
                    }, variant = OhButtonVariant.Ghost, size = OhButtonSize.Small)
                    OhButton(label = "Cancel", onClick = {
                    }, variant = OhButtonVariant.Quiet, size = OhButtonSize.ExtraSmall)
                }
                Spacer(Modifier.height(OhTheme.space.space2))
                OhButton(label = "Unavailable", onClick = {}, enabled = false)
            }

            GalleryEntry.IconButton -> Row(horizontalArrangement = Arrangement.spacedBy(OhTheme.space.space2)) {
                OhIconButton(OhIconName.Settings, "Settings", onClick = {})
                OhIconButton(OhIconName.Scan, "Scan a barcode", onClick = {})
                OhIconButton(OhIconName.More, "More", onClick = {}, on = true)
                OhIconButton(OhIconName.Close, "Close", onClick = {})
            }

            GalleryEntry.Chips -> Column {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(OhTheme.space.space2)
                ) {
                    OhChip("Recovery", selected = false, onClick = {})
                    OhChip("Fat layer", selected = true, onClick = {})
                    OhChip("Plan", selected = false, onClick = {})
                    OhChip("Filter on", selected = true, neutralSelected = true, onClick = {})
                }
            }

            GalleryEntry.Segments -> Column {
                OhSegmentedControl(
                    options = listOf("Overview", "Body", "Training", "Sleep", "Nutrition"),
                    selectedIndex = 1,
                    onSelect = {}
                )
            }

            GalleryEntry.Switch -> Row(horizontalArrangement = Arrangement.spacedBy(OhTheme.space.space6)) {
                OhSwitch(checked = true, onCheckedChange = {})
                OhSwitch(checked = false, onCheckedChange = {})
                OhSwitch(checked = true, onCheckedChange = {}, enabled = false)
            }

            GalleryEntry.Stepper -> Row(horizontalArrangement = Arrangement.spacedBy(OhTheme.space.space5)) {
                OhStepper(value = "82.4", unit = "kg", onDecrement = {}, onIncrement = {})
                OhStepper(value = "12", unit = "reps", onDecrement = {}, onIncrement = {}, enabled = false)
            }

            GalleryEntry.Slider -> OhLabelledField(label = "Effort (RIR)") {
                OhSlider(value = 3f, onValueChange = {}, valueRange = 0f..5f, valueLabel = "3")
            }

            GalleryEntry.Fields -> Column {
                OhLabelledField(label = "Weight") {
                    OhTextField(value = "82.4", onValueChange = {}, placeholder = "kg")
                }
                Spacer(Modifier.height(OhTheme.space.space5))
                OhTextField(
                    value = "",
                    onValueChange = {},
                    placeholder = "Search foods",
                    isError = true,
                    errorMessage = "Couldn't save. Your entry is still here."
                )
                Spacer(Modifier.height(OhTheme.space.space5))
                OhSearchField(
                    value = "yoghurt",
                    onValueChange = {},
                    placeholder = "Search foods",
                    resultCount = 84
                )
            }

            GalleryEntry.TopBar -> OhTopBar(
                date = "Mon 23 Nov",
                onQuickAdd = {},
                onSettings = {},
                showSyncDot = true
            )

            GalleryEntry.TabBar -> Column {
                OhTabBar(selected = OhTab.Today, onSelect = {}, dots = setOf(OhTab.Today))
                Spacer(Modifier.height(OhTheme.space.space2))
                OhTabBar(selected = OhTab.Train, onSelect = {})
            }

            GalleryEntry.Sheets -> OhSheetSurface(title = "Log weight") {
                OhLabelledField(label = "Today") {
                    OhStepper(value = "82.4", unit = "kg", onDecrement = {}, onIncrement = {})
                }
                Spacer(Modifier.height(OhTheme.space.space7))
                OhButton(label = "Save weight", onClick = {})
                Spacer(Modifier.height(OhTheme.space.space2))
                Text(
                    "Cancel",
                    style = OhTheme.type.body,
                    color = OhTheme.colors.label2,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            GalleryEntry.Dialogs -> OhDialogCard(
                title = "Delete this entry?",
                body = "The reading leaves your trends and waits 30 days in Trash. You can undo now.",
                primaryLabel = "Delete entry",
                onPrimary = {},
                onCancel = {}
            )

            GalleryEntry.Toasts -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OhToast(text = "Weight saved", onDismiss = {})
                Spacer(Modifier.height(OhTheme.space.space2))
                OhToast(text = "Entry deleted", actionLabel = "Undo", onAction = {}, onDismiss = {})
                Spacer(Modifier.height(OhTheme.space.space5))
                OhTombstoneRow(text = "Weight entry deleted", onUndo = {})
            }

            GalleryEntry.Timers -> Column {
                OhTimerBar(
                    clock = "01:30",
                    progress = 0.55f,
                    running = true,
                    onMinus = {},
                    onPlus = {},
                    onPauseToggle = {},
                    onSkip = {}
                )
                Spacer(Modifier.height(OhTheme.space.space5))
                OhTimerBar(
                    clock = "01:30",
                    progress = 0.55f,
                    running = false,
                    onMinus = {},
                    onPlus = {},
                    onPauseToggle = {},
                    onSkip = {},
                    work = true
                )
            }

            GalleryEntry.Icons -> IconGrid()

            GalleryEntry.States -> StatesPreview()
        }
    }
}

@Composable
private fun IconGrid() {
    val colors = OhTheme.colors
    val type = OhTheme.type
    OhIconName.entries.chunked(6).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(OhTheme.space.space2)) {
            row.forEach { name ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(OhTheme.geometry.cardHeaderGlyph + OhTheme.space.space4)
                            .clip(RoundedCornerShape(OhTheme.radius.sm))
                            .background(colors.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        OhIcon(name = name, contentDescription = null, tint = colors.label)
                    }
                    Text(
                        text = name.name,
                        style = type.caption2,
                        color = colors.label2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(OhTheme.space.space3))
    }
}

@Composable
private fun StatesPreview() {
    val colors = OhTheme.colors
    val type = OhTheme.type
    Column {
        // §6.5 offline banner: orange is attention, never red, and never a modal.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(OhTheme.geometry.bannerHeight)
                .background(colors.surface2)
                .padding(horizontal = OhTheme.space.space6),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OhIcon(
                OhIconName.CloudOff,
                contentDescription = null,
                size = OhTheme.geometry.iconInText,
                tint = colors.orangeInk
            )
            Spacer(Modifier.widthIn(min = OhTheme.space.space2))
            Text(
                "Offline. Your changes are saved on this phone.",
                style = type.footnote,
                color = colors.label2
            )
        }
        Spacer(Modifier.height(OhTheme.space.space5))
        // §6.7 empty: a glyph, a line, a sentence and one action, in a card.
        OhCard {
            OhIcon(
                OhIconName.Weight,
                contentDescription = null,
                size = OhTheme.geometry.emptyGlyph,
                tint = colors.label2
            )
            Spacer(Modifier.height(OhTheme.space.space3))
            Text("No weigh-ins yet", style = type.body.semiboldWeight(), color = colors.label)
            Text(
                "Add one weigh-in and the trend starts on the second day.",
                style = type.footnote,
                color = colors.label2
            )
            Spacer(Modifier.height(OhTheme.space.space5))
            OhButton(label = "Log weight", onClick = {}, size = OhButtonSize.Small)
        }
        // §6.4 loading: a skeleton shaped like the content, never a spinner over the screen.
        OhCard {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(OhTheme.geometry.rowMinHeight)
                    .clip(RoundedCornerShape(OhTheme.radius.sm))
                    .background(colors.surface2)
            )
            Spacer(Modifier.height(OhTheme.space.space3))
            Box(
                Modifier
                    .fillMaxWidth(0.6f)
                    .height(OhTheme.geometry.iconInText)
                    .clip(RoundedCornerShape(OhTheme.radius.sm))
                    .background(colors.surface2)
            )
        }
        // §6.8 success: an in-place tick; §2.8 a PR is a gold tag that stays.
        Row(verticalAlignment = Alignment.CenterVertically) {
            OhIcon(
                OhIconName.Check,
                contentDescription = null,
                size = OhTheme.geometry.iconInText,
                tint = colors.greenInk
            )
            Spacer(Modifier.widthIn(min = OhTheme.space.space2))
            Text("Saved at 07:42", style = type.footnote, color = colors.greenInk)
            Spacer(Modifier.widthIn(min = OhTheme.space.space3))
            Text(
                "Record",
                style = type.caption.semiboldWeight(),
                color = colors.goldInk,
                modifier = Modifier
                    .clip(RoundedCornerShape(OhTheme.radius.sm))
                    .background(colors.yellow.copy(alpha = 0.16f))
                    .padding(horizontal = OhTheme.space.space2, vertical = OhTheme.space.space0)
            )
        }
        Spacer(Modifier.height(OhTheme.space.space5))
        // §7.4 conflict: two sources, the priority marked, no silent resolution.
        OhCard {
            OhCardHeader(
                glyph = OhIconName.Warning,
                eyebrow = "Two sources disagree",
                title = "Thursday's steps",
                subtitle = "Watch 8 412 · Phone 9 103",
                glyphTint = colors.orangeInk
            )
            Spacer(Modifier.height(OhTheme.space.space5))
            Row(horizontalArrangement = Arrangement.spacedBy(OhTheme.space.space2)) {
                OhButton(label = "Keep 8 412", onClick = {}, size = OhButtonSize.Small)
                OhButton(label = "Use 9 103", onClick = {
                }, variant = OhButtonVariant.Secondary, size = OhButtonSize.Small)
            }
        }
    }
}
