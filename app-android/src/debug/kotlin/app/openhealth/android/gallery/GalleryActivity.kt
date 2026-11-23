package app.openhealth.android.gallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import app.openhealth.ui.components.OhButton
import app.openhealth.ui.components.OhButtonSize
import app.openhealth.ui.components.OhButtonVariant
import app.openhealth.ui.components.OhCard
import app.openhealth.ui.components.OhCardHeader
import app.openhealth.ui.components.OhChip
import app.openhealth.ui.components.OhChipStrip
import app.openhealth.ui.components.OhDialogCard
import app.openhealth.ui.components.OhIconButton
import app.openhealth.ui.components.OhInsetGroup
import app.openhealth.ui.components.OhKpiBand
import app.openhealth.ui.components.OhKpiFigure
import app.openhealth.ui.components.OhMediaRow
import app.openhealth.ui.components.OhRow
import app.openhealth.ui.components.OhSearchField
import app.openhealth.ui.components.OhSegmentedControl
import app.openhealth.ui.components.OhSheet
import app.openhealth.ui.components.OhSlider
import app.openhealth.ui.components.OhStatTile
import app.openhealth.ui.components.OhStepper
import app.openhealth.ui.components.OhSwitch
import app.openhealth.ui.components.OhTabBar
import app.openhealth.ui.components.OhTabItem
import app.openhealth.ui.components.OhTextField
import app.openhealth.ui.components.OhTimerBar
import app.openhealth.ui.components.OhToast
import app.openhealth.ui.components.OhTopBar
import app.openhealth.ui.icons.OhGlyphIcons
import app.openhealth.ui.icons.OhIcon
import app.openhealth.ui.icons.OhIcons
import app.openhealth.ui.icons.OhModuleIcons
import app.openhealth.ui.icons.OhObjectIcons
import app.openhealth.ui.theme.OhTheme
import app.openhealth.ui.theme.OhWeight

/**
 * Debug-only component gallery (`BUILD-ROADMAP.md` §B1 deliverable 5): every
 * component and every state, in dark and light, at normal and 200 % text.
 *
 * It is not a screen of the product and never ships in a release build: it lives in
 * the `debug` source set and is launched from its own debug-only activity.
 */
class GalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var dark by rememberSaveable { mutableStateOf(true) }
            var hugeText by rememberSaveable { mutableStateOf(false) }
            // The window frame's bar icons follow the gallery's theme; the real app sets
            // this from the system theme with enableEdgeToEdge in B3.
            LaunchedEffect(dark) {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            OhTheme(dark = dark) {
                val density = LocalDensity.current
                val scaled = remember(density, hugeText) {
                    if (hugeText) Density(density.density, fontScale = 2f) else density
                }
                CompositionLocalProvider(LocalDensity provides scaled) {
                    GalleryScreen(
                        dark = dark,
                        hugeText = hugeText,
                        onDark = { dark = it },
                        onHugeText = { hugeText = it },
                    )
                }
            }
        }
    }
}

@Composable
private fun GalleryScreen(
    dark: Boolean,
    hugeText: Boolean,
    onDark: (Boolean) -> Unit,
    onHugeText: (Boolean) -> Unit,
) {
    val colors = OhTheme.colors
    val type = OhTheme.type
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            // Edge-to-edge is the platform default at targetSdk 37; the shell consumes
            // insets in B3, so the debug gallery reserves the status bar itself (§4.3).
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        OhTopBar(
            appName = "openHealth",
            dateLabel = "Component gallery",
            quickAddLabel = "Quick add",
            settingsLabel = "Settings",
            onQuickAdd = {},
            onSettings = {},
            settingsDot = true,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = OhTheme.space.s6,
                end = OhTheme.space.s6,
                top = OhTheme.space.s5,
                bottom = OhTheme.space.s8,
            ),
            verticalArrangement = Arrangement.spacedBy(OhTheme.space.s7),
        ) {
            item {
                Column {
                    Text(
                        text = "Design system",
                        style = type.large.copy(fontWeight = OhWeight.bold),
                        color = colors.label,
                    )
                    Text(
                        text = "Every component and state, in both themes and at 200 % text.",
                        style = type.subhead,
                        color = colors.label2,
                    )
                    Spacer(Modifier.height(OhTheme.space.s3))
                    OhChipStrip {
                        OhChip(text = "Dark", selected = dark, onClick = { onDark(true) })
                        OhChip(text = "Light", selected = !dark, onClick = { onDark(false) })
                        OhChip(
                            text = "Text 100%",
                            selected = !hugeText,
                            onClick = { onHugeText(false) },
                        )
                        OhChip(
                            text = "Text 200%",
                            selected = hugeText,
                            onClick = { onHugeText(true) },
                        )
                    }
                }
            }

            item { Section("Colour") { ColourRamp() } }
            item { Section("Type scale") { TypeScale() } }
            item { Section("Card and card header") { CardAndHeader() } }
            item { Section("Inset group and row") { InsetGroupAndRows() } }
            item { Section("Media row") { MediaRows() } }
            item { Section("Stat tile and KPI band") { StatTiles() } }
            item { Section("Buttons") { Buttons() } }
            item { Section("Icon buttons") { IconButtons() } }
            item { Section("Chips") { Chips() } }
            item { Section("Segmented control") { Segmented() } }
            item { Section("Switch, stepper, slider") { Controls() } }
            item { Section("Fields") { Fields() } }
            item { Section("Tab bar") { TabBars() } }
            item { Section("Sheet") { Sheets() } }
            item { Section("Dialog") { Dialogs() } }
            item { Section("Toast") { Toasts() } }
            item { Section("Timer bar") { TimerBars() } }
            item { Section("Icons") { Icons() } }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val colors = OhTheme.colors
    val type = OhTheme.type
    Column {
        Text(
            text = title,
            style = type.footnote.copy(fontWeight = OhWeight.semibold),
            color = colors.label2,
        )
        Spacer(Modifier.height(OhTheme.space.s3))
        content()
    }
}

@Composable
private fun ColourRamp() {
    val colors = OhTheme.colors
    val type = OhTheme.type
    val swatches = listOf(
        "bg" to colors.bg,
        "bg-el" to colors.bgEl,
        "surface" to colors.surface,
        "surface-2" to colors.surface2,
        "surface-3" to colors.surface3,
        "acc" to colors.acc,
        "acc-soft" to colors.accSoft,
        "acc-line" to colors.accLine,
        "fat" to colors.fat,
        "ready" to colors.recReady,
        "easing" to colors.recEasing,
        "recovering" to colors.recActive,
        "green-ink" to colors.greenInk,
        "orange-ink" to colors.orangeInk,
        "gold-ink" to colors.goldInk,
        "red-ink" to colors.redInk,
        "blue-ink" to colors.blueInk,
        "teal-ink" to colors.tealInk,
    )
    Column(verticalArrangement = Arrangement.spacedBy(OhTheme.space.s1)) {
        swatches.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(OhTheme.space.s4)) {
                row.forEach { (name, color) ->
                    Column(modifier = Modifier.width(OhTheme.space.s8 * 2f)) {
                        Box(
                            modifier = Modifier
                                .size(OhTheme.space.s8)
                                .clip(RoundedCornerShape(OhTheme.radius.sm))
                                .background(color),
                        )
                        Text(text = name, style = type.caption2, color = colors.label2)
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeScale() {
    val colors = OhTheme.colors
    val type = OhTheme.type
    Column(verticalArrangement = Arrangement.spacedBy(OhTheme.space.s0)) {
        listOf(
            "caption2" to type.caption2,
            "caption" to type.caption,
            "footnote" to type.footnote,
            "subhead" to type.subhead,
            "callout" to type.callout,
            "body" to type.body,
            "title3" to type.title3,
            "title2" to type.title2,
            "large" to type.large,
            "display" to type.display,
        ).forEach { (name, style) ->
            Text(text = "$name · 82.4 kg", style = style, color = colors.label)
        }
    }
}

@Composable
private fun CardAndHeader() {
    val colors = OhTheme.colors
    OhCard {
        OhCardHeader(
            eyebrow = "Today",
            title = "Readiness",
            subtitle = "Sleep, HRV and resting heart rate",
            glyph = OhModuleIcons.heart,
            glyphTint = colors.accInk,
            trailing = {
                OhIconButton(
                    icon = OhIcons.chevronRight,
                    contentDescription = "Open readiness",
                    onClick = {},
                )
            },
        )
    }
}

@Composable
private fun InsetGroupAndRows() {
    val colors = OhTheme.colors
    OhInsetGroup {
        OhRow(title = "Weight", value = "82.4 kg", showDivider = false)
        OhRow(title = "Steps", value = "8 412", showDivider = true)
        OhRow(
            title = "Resting heart rate",
            subtitle = "Watch · 07:42",
            value = "54 bpm",
            showDivider = true,
            showChevron = true,
            onClick = {},
        )
        OhRow(
            title = "Modules",
            subtitle = "Thirteen surfaces, none deleted",
            leadingIcon = OhIcons.more,
            showDivider = true,
            showChevron = true,
            onClick = {},
        )
        OhRow(
            title = "Disabled row",
            subtitle = "Explains itself",
            enabled = false,
            showDivider = true,
        )
        Row(modifier = Modifier.fillMaxWidth().padding(OhTheme.space.s6)) {
            OhRow(
                title = "Delete all data",
                leadingIcon = OhIcons.delete,
                destructive = true,
                onClick = {},
            )
        }
        Box(modifier = Modifier.height(OhTheme.space.s0).fillMaxWidth().background(colors.surface))
    }
}

@Composable
private fun MediaRows() {
    OhMediaRow(
        title = "Barbell bench press",
        subtitle = "Pectorals · Barbell",
        thumbnail = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(OhTheme.colors.surface2),
                contentAlignment = Alignment.Center,
            ) {
                OhIcon(
                    icon = OhObjectIcons.barbell,
                    contentDescription = null,
                    size = OhTheme.dims.iconButtonGlyph,
                    tint = OhTheme.colors.label2,
                )
            }
        },
        showChevron = true,
        onClick = {},
    )
}

@Composable
private fun StatTiles() {
    Column(verticalArrangement = Arrangement.spacedBy(OhTheme.space.s4)) {
        Row(horizontalArrangement = Arrangement.spacedBy(OhTheme.space.s4)) {
            OhStatTile(
                label = "Body fat",
                value = "18.2 %",
                delta = "−0.4 in a month",
                modifier = Modifier.weight(1f),
            )
            OhStatTile(
                label = "Lean mass",
                value = "64.1 kg",
                delta = "+0.2 in a month",
                modifier = Modifier.weight(1f),
            )
        }
        OhKpiBand(
            figures = listOf(
                OhKpiFigure(value = "1 240", label = "In"),
                OhKpiFigure(value = "2 310", label = "Out"),
                OhKpiFigure(value = "−1 070", label = "Net"),
            ),
            footer = "At this pace: 14 Feb to 3 Mar",
        )
    }
}

@Composable
private fun Buttons() {
    Column(verticalArrangement = Arrangement.spacedBy(OhTheme.space.s3)) {
        OhButton(text = "Log weight", onClick = {}, modifier = Modifier.fillMaxWidth())
        OhButton(
            text = "Keep 8 412",
            onClick = {},
            variant = OhButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
        OhButton(
            text = "Choose another",
            onClick = {},
            variant = OhButtonVariant.Tinted,
            modifier = Modifier.fillMaxWidth(),
        )
        OhButton(
            text = "Delete entry",
            onClick = {},
            variant = OhButtonVariant.Danger,
            modifier = Modifier.fillMaxWidth(),
        )
        OhButton(text = "See all", onClick = {}, variant = OhButtonVariant.Ghost)
        OhButton(text = "Cancel", onClick = {}, variant = OhButtonVariant.Quiet)
        OhButton(
            text = "Disabled action",
            onClick = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(OhTheme.space.s3)) {
            OhButton(text = "Small", onClick = {
            }, size = OhButtonSize.Small, variant = OhButtonVariant.Secondary)
            OhButton(text = "Tiny", onClick = {
            }, size = OhButtonSize.ExtraSmall, variant = OhButtonVariant.Tinted)
        }
    }
}

@Composable
private fun IconButtons() {
    Row(horizontalArrangement = Arrangement.spacedBy(OhTheme.space.s2)) {
        OhIconButton(icon = OhIcons.add, contentDescription = "Add", onClick = {})
        OhIconButton(icon = OhIcons.close, contentDescription = "Close", onClick = {})
        OhIconButton(icon = OhIcons.filter, contentDescription = "Filter", onClick = {}, on = true)
        OhIconButton(icon = OhIcons.settings, contentDescription = "Settings", onClick = {
        }, showDot = true)
        OhIconButton(icon = OhIcons.delete, contentDescription = "Delete", onClick = {
        }, enabled = false)
    }
}

@Composable
private fun Chips() {
    Column(verticalArrangement = Arrangement.spacedBy(OhTheme.space.s2)) {
        OhChipStrip {
            OhChip(text = "All", selected = true, onClick = {})
            OhChip(text = "Manual", selected = false, onClick = {})
            OhChip(text = "Imported", selected = false, onClick = {})
        }
        OhChipStrip {
            OhChip(text = "Filter on", selected = true, neutralSelected = true, onClick = {})
            OhChip(text = "Disabled", selected = false, enabled = false, onClick = {})
        }
    }
}

@Composable
private fun Segmented() {
    var selected by remember { mutableStateOf(0) }
    OhSegmentedControl(
        options = listOf("Overview", "Body", "Training", "Sleep"),
        selectedIndex = selected,
        onSelect = { selected = it },
    )
}

@Composable
private fun Controls() {
    var on by remember { mutableStateOf(true) }
    var steps by remember { mutableStateOf(8) }
    var slider by remember { mutableStateOf(0.35f) }
    Column(verticalArrangement = Arrangement.spacedBy(OhTheme.space.s3)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OhSwitch(checked = on, onCheckedChange = {
                on = it
            }, contentDescription = "Demo switch")
            Spacer(Modifier.width(OhTheme.space.s3))
            OhSwitch(checked = false, onCheckedChange = {}, contentDescription = "Off switch")
            Spacer(Modifier.width(OhTheme.space.s3))
            OhSwitch(checked = true, onCheckedChange = {
            }, enabled = false, contentDescription = "Disabled switch")
        }
        OhStepper(
            value = steps.toString(),
            unit = "rep",
            onDecrement = { steps = (steps - 1).coerceAtLeast(0) },
            onIncrement = { steps += 1 },
        )
        OhSlider(
            value = slider,
            onValueChange = { slider = it },
            contentDescription = "Demo slider",
        )
    }
}

@Composable
private fun Fields() {
    var text by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("bench") }
    Column(verticalArrangement = Arrangement.spacedBy(OhTheme.space.s3)) {
        OhTextField(value = text, onValueChange = { text = it }, placeholder = "Exercise name")
        OhTextField(
            value = "82.4",
            onValueChange = {},
            error = "That is outside the plausible range.",
        )
        OhTextField(value = "", onValueChange = {}, placeholder = "Disabled", enabled = false)
        OhSearchField(value = search, onValueChange = { search = it }, count = 84)
        OhSearchField(value = "", onValueChange = {}, placeholder = "Search foods")
    }
}

@Composable
private fun TabBars() {
    val tabs = listOf(
        OhTabItem(label = "Today", icon = OhIcons.today),
        OhTabItem(label = "Vitals", icon = OhIcons.vitals),
        OhTabItem(label = "Train", icon = OhIcons.train, raised = true),
        OhTabItem(label = "Nutrition", icon = OhIcons.nutrition),
        OhTabItem(label = "Stats", icon = OhIcons.stats),
    )
    Column(verticalArrangement = Arrangement.spacedBy(OhTheme.space.s3)) {
        Box(modifier = Modifier.fillMaxWidth().background(OhTheme.colors.bg)) {
            OhTabBar(tabs = tabs, selectedIndex = 0, onSelect = {}, dotIndices = setOf(0))
        }
        Box(modifier = Modifier.fillMaxWidth().background(OhTheme.colors.bg)) {
            OhTabBar(tabs = tabs, selectedIndex = 2, onSelect = {})
        }
    }
}

@Composable
private fun Sheets() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(OhTheme.radius.xl))
            .background(OhTheme.colors.surface),
    ) {
        OhSheet(
            title = "Log weight",
            primaryLabel = "Save weight",
            cancelLabel = "Cancel",
            content = {
                OhTextField(value = "82.4", onValueChange = {}, placeholder = "Weight")
                Spacer(Modifier.height(OhTheme.space.s3))
                Text(
                    text = "Yesterday you recorded 82.6 kg.",
                    style = OhTheme.type.footnote,
                    color = OhTheme.colors.label2,
                )
            },
        )
    }
}

@Composable
private fun Dialogs() {
    Row(horizontalArrangement = Arrangement.spacedBy(OhTheme.space.s3)) {
        OhDialogCard(
            title = "Two sources disagree",
            message = "Thursday's steps differ by 691.",
            primaryLabel = "Keep 8 412",
            cancelLabel = "Use 9 103",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Toasts() {
    Column(verticalArrangement = Arrangement.spacedBy(OhTheme.space.s3)) {
        OhToast(message = "Logged 60 s at 80 kg.")
        OhToast(message = "Deleted one entry.", actionLabel = "Undo", onAction = {}, onDismiss = {})
    }
}

@Composable
private fun TimerBars() {
    Column(verticalArrangement = Arrangement.spacedBy(OhTheme.space.s3)) {
        OhTimerBar(time = "1:30", progress = 0.4f)
        OhTimerBar(time = "0:12", progress = 0.8f, work = true)
        OhTimerBar(time = "2:00", progress = 0.1f, paused = true)
    }
}

@Composable
private fun Icons() {
    val groups = listOf(
        "Navigation" to listOf(
            OhIcons.today,
            OhIcons.vitals,
            OhIcons.train,
            OhIcons.nutrition,
            OhIcons.stats,
            OhIcons.more,
        ),
        "Actions" to listOf(
            OhIcons.add, OhIcons.check, OhIcons.close, OhIcons.chevronLeft,
            OhIcons.chevronRight, OhIcons.chevronDown, OhIcons.search, OhIcons.filter,
            OhIcons.scan, OhIcons.camera, OhIcons.play, OhIcons.pause, OhIcons.skip,
            OhIcons.timer, OhIcons.edit, OhIcons.delete, OhIcons.undo, OhIcons.share,
            OhIcons.export, OhIcons.lock, OhIcons.cloudOff, OhIcons.refresh, OhIcons.info,
            OhIcons.warning, OhIcons.externalLink, OhIcons.settings, OhIcons.remove,
        ),
        "Modules" to listOf(
            OhModuleIcons.workout, OhModuleIcons.yoga, OhModuleIcons.steps,
            OhModuleIcons.weight, OhModuleIcons.heart, OhModuleIcons.sleep,
            OhModuleIcons.activity, OhModuleIcons.mood, OhModuleIcons.cycle,
            OhModuleIcons.family, OhModuleIcons.coach, OhModuleIcons.integrations,
        ),
        "Objects" to listOf(
            OhObjectIcons.barbell, OhObjectIcons.dumbbell, OhObjectIcons.kettlebell,
            OhObjectIcons.plate, OhObjectIcons.machine, OhObjectIcons.band,
            OhObjectIcons.mat, OhObjectIcons.shoe, OhObjectIcons.bike,
            OhObjectIcons.route, OhObjectIcons.scale, OhObjectIcons.cuff,
            OhObjectIcons.glucose, OhObjectIcons.pill, OhObjectIcons.flask,
            OhObjectIcons.file, OhObjectIcons.watch, OhObjectIcons.phone,
        ),
        "Routine keys" to listOf(
            OhGlyphIcons.strength, OhGlyphIcons.arms, OhGlyphIcons.abs,
            OhGlyphIcons.legs, OhGlyphIcons.pullUp, OhGlyphIcons.run,
            OhGlyphIcons.swim, OhGlyphIcons.boxing, OhGlyphIcons.stretch,
            OhGlyphIcons.moon, OhGlyphIcons.flame, OhGlyphIcons.bolt,
        ),
    )
    Column(verticalArrangement = Arrangement.spacedBy(OhTheme.space.s4)) {
        groups.forEach { (name, icons) ->
            Text(text = name, style = OhTheme.type.caption, color = OhTheme.colors.label2)
            icons.chunked(7).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(OhTheme.space.s4)) {
                    row.forEach { icon ->
                        OhIcon(
                            icon = icon,
                            contentDescription = null,
                            size = OhTheme.dims.tabIcon,
                            tint = OhTheme.colors.label,
                        )
                    }
                }
            }
        }
    }
}
