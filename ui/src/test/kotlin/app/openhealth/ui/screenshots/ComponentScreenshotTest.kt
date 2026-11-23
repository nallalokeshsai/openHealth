package app.openhealth.ui.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
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
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi screenshot tests for every component and state in the four variants the
 * build requires: dark, light, 200 % text and RTL (`BUILD-ROADMAP.md` §B1; `Tech-Stack.md`
 * §10). `./gradlew check` verifies the committed goldens; `-Proborazzi.record` rewrites
 * them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-mdpi")
class ComponentScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private enum class Variant(
        val dir: String,
        val dark: Boolean,
        val scale: Float,
        val rtl: Boolean,
    ) {
        Dark("dark", true, 1f, false),
        Light("light", false, 1f, false),
        Text200("text200", true, 2f, false),
        Rtl("rtl", true, 1f, true),
    }

    private fun shot(name: String, content: @Composable () -> Unit) {
        val variant = mutableStateOf(Variant.Dark)
        compose.setContent {
            val current = variant.value
            val density = LocalDensity.current
            OhTheme(dark = current.dark) {
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale = current.scale),
                    LocalLayoutDirection provides if (current.rtl) {
                        LayoutDirection.Rtl
                    } else {
                        LayoutDirection.Ltr
                    },
                ) {
                    Box(
                        modifier = Modifier
                            .testTag(SHOT)
                            .fillMaxWidth()
                            .background(OhTheme.colors.bg),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(OhTheme.space.s6)) {
                            content()
                        }
                    }
                }
            }
        }
        Variant.entries.forEach { entry ->
            compose.runOnIdle { variant.value = entry }
            compose.waitForIdle()
            compose.onNodeWithTag(SHOT)
                .captureRoboImage("src/test/screenshots/${entry.dir}/$name.png")
        }
    }

    @Test
    fun card() {
        shot("card") {
            OhCard {
                OhCardHeader(eyebrow = "Today", title = "Readiness", subtitle = "Sleep and HRV")
            }
        }
    }

    @Test
    fun cardHeaderWithTrailing() {
        shot("card_header_trailing") {
            OhCard {
                OhCardHeader(
                    eyebrow = "Vitals",
                    title = "Weight",
                    subtitle = "Watch · 07:42",
                    glyph = OhModuleIcons.weight,
                    trailing = {
                        OhIconButton(
                            icon = OhIcons.chevronRight,
                            contentDescription = "Open weight",
                            onClick = {},
                        )
                    },
                )
            }
        }
    }

    @Test
    fun rows() {
        shot("rows") {
            OhInsetGroup {
                OhRow(title = "Weight", value = "82.4 kg")
                OhRow(
                    title = "Steps",
                    subtitle = "Garmin · 07:42",
                    value = "8 412",
                    showDivider = true,
                )
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
                OhRow(
                    title = "Delete all data",
                    leadingIcon = OhIcons.delete,
                    destructive = true,
                    showDivider = true,
                    onClick = {},
                )
            }
        }
    }

    @Test
    fun mediaRow() {
        shot("media_row") {
            OhMediaRow(
                title = "Barbell bench press",
                subtitle = "Pectorals · Barbell",
                thumbnail = {
                    Box(
                        modifier = Modifier.fillMaxWidth().background(OhTheme.colors.surface2),
                        contentAlignment = androidx.compose.ui.Alignment.Center,
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
    }

    @Test
    fun statTile() {
        shot("stat_tile") {
            OhStatTile(label = "Body fat", value = "18.2", unit = "%", delta = "−0.4 in a month")
        }
    }

    @Test
    fun kpiBand() {
        shot("kpi_band") {
            OhKpiBand(
                figures = listOf(
                    OhKpiFigure(value = "1 240", label = "In", unit = "kcal"),
                    OhKpiFigure(value = "2 310", label = "Out", unit = "kcal"),
                    OhKpiFigure(value = "−1 070", label = "Net", unit = "kcal"),
                ),
                footer = "At this pace: 14 Feb to 3 Mar",
            )
        }
    }

    @Test
    fun buttons() {
        shot("buttons") {
            Column {
                OhButton(text = "Log weight", onClick = {}, modifier = Modifier.fillMaxWidth())
                OhButton(
                    text = "Keep 8 412",
                    onClick = {},
                    variant = OhButtonVariant.Secondary,
                    modifier = Modifier.fillMaxWidth().padding(top = OhTheme.space.s3),
                )
                OhButton(
                    text = "Choose another",
                    onClick = {},
                    variant = OhButtonVariant.Tinted,
                    modifier = Modifier.fillMaxWidth().padding(top = OhTheme.space.s3),
                )
                OhButton(
                    text = "Delete entry",
                    onClick = {},
                    variant = OhButtonVariant.Danger,
                    modifier = Modifier.fillMaxWidth().padding(top = OhTheme.space.s3),
                )
                OhButton(
                    text = "Disabled action",
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth().padding(top = OhTheme.space.s3),
                )
                OhButton(
                    text = "See all",
                    onClick = {},
                    variant = OhButtonVariant.Ghost,
                    modifier = Modifier.padding(top = OhTheme.space.s3),
                )
                OhButton(
                    text = "Cancel",
                    onClick = {},
                    variant = OhButtonVariant.Quiet,
                    modifier = Modifier.padding(top = OhTheme.space.s3),
                )
                OhButton(
                    text = "Small",
                    onClick = {},
                    size = OhButtonSize.Small,
                    variant = OhButtonVariant.Secondary,
                    modifier = Modifier.padding(top = OhTheme.space.s3),
                )
                OhButton(
                    text = "Tiny",
                    onClick = {},
                    size = OhButtonSize.ExtraSmall,
                    variant = OhButtonVariant.Tinted,
                    modifier = Modifier.padding(top = OhTheme.space.s3),
                )
            }
        }
    }

    @Test
    fun iconButtons() {
        shot("icon_buttons") {
            Column {
                OhIconButton(icon = OhIcons.add, contentDescription = "Add", onClick = {})
                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = OhTheme.space.s3))
                OhIconButton(icon = OhIcons.filter, contentDescription = "Filter", onClick = {
                }, on = true)
                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = OhTheme.space.s3))
                OhIconButton(icon = OhIcons.settings, contentDescription = "Settings", onClick = {
                }, showDot = true)
                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = OhTheme.space.s3))
                OhIconButton(icon = OhIcons.delete, contentDescription = "Delete", onClick = {
                }, enabled = false)
            }
        }
    }

    @Test
    fun chips() {
        shot("chips") {
            Column {
                OhChipStrip {
                    OhChip(text = "All", selected = true, onClick = {})
                    OhChip(text = "Manual", selected = false, onClick = {})
                    OhChip(text = "Imported", selected = false, onClick = {})
                }
                OhChipStrip(modifier = Modifier.padding(top = OhTheme.space.s2)) {
                    OhChip(text = "Filter on", selected = true, neutralSelected = true, onClick = {
                    })
                    OhChip(text = "Disabled", selected = false, enabled = false, onClick = {})
                }
            }
        }
    }

    @Test
    fun segmented() {
        shot("segmented") {
            OhSegmentedControl(
                options = listOf("Overview", "Body", "Training", "Sleep"),
                selectedIndex = 1,
                onSelect = {},
            )
        }
    }

    @Test
    fun switches() {
        shot("switch") {
            Column {
                OhSwitch(checked = true, onCheckedChange = {}, contentDescription = "On")
                OhSwitch(checked = false, onCheckedChange = {}, contentDescription = "Off")
                OhSwitch(checked = true, onCheckedChange = {
                }, enabled = false, contentDescription = "Disabled")
            }
        }
    }

    @Test
    fun stepper() {
        shot("stepper") {
            OhStepper(value = "82.4", unit = "kg", onDecrement = {}, onIncrement = {})
        }
    }

    @Test
    fun slider() {
        shot("slider") {
            OhSlider(value = 0.35f, onValueChange = {}, contentDescription = "Effort")
        }
    }

    @Test
    fun textFields() {
        shot("fields") {
            Column {
                OhTextField(value = "", onValueChange = {}, placeholder = "Exercise name")
                OhTextField(
                    value = "82.4",
                    onValueChange = {},
                    modifier = Modifier.padding(top = OhTheme.space.s3),
                )
                OhTextField(
                    value = "982",
                    onValueChange = {},
                    error = "That is outside the plausible range.",
                    modifier = Modifier.padding(top = OhTheme.space.s3),
                )
                OhTextField(
                    value = "",
                    onValueChange = {},
                    placeholder = "Disabled",
                    enabled = false,
                    modifier = Modifier.padding(top = OhTheme.space.s3),
                )
            }
        }
    }

    @Test
    fun searchFields() {
        shot("search_fields") {
            Column {
                OhSearchField(value = "", onValueChange = {
                }, placeholder = "Search foods", count = 84)
                OhSearchField(
                    value = "bench",
                    onValueChange = {},
                    modifier = Modifier.padding(top = OhTheme.space.s3),
                )
            }
        }
    }

    @Test
    fun topBar() {
        shot("top_bar") {
            OhTopBar(
                appName = "openHealth",
                dateLabel = "Mon 23 Nov",
                quickAddLabel = "Quick add",
                settingsLabel = "Settings",
                onQuickAdd = {},
                onSettings = {},
                settingsDot = true,
            )
        }
    }

    @Test
    fun tabBarToday() {
        shot("tab_bar_today") {
            OhTabBar(tabs = tabs(), selectedIndex = 0, onSelect = {}, dotIndices = setOf(0))
        }
    }

    @Test
    fun tabBarTrain() {
        shot("tab_bar_train") {
            OhTabBar(tabs = tabs(), selectedIndex = 2, onSelect = {})
        }
    }

    @Test
    fun sheet() {
        shot("sheet") {
            OhSheet(
                title = "Log weight",
                primaryLabel = "Save weight",
                cancelLabel = "Cancel",
                content = {
                    OhTextField(value = "82.4", onValueChange = {}, placeholder = "Weight")
                },
            )
        }
    }

    @Test
    fun dialog() {
        shot("dialog") {
            OhDialogCard(
                title = "Two sources disagree",
                message = "Thursday's steps differ by 691.",
                primaryLabel = "Keep 8 412",
                cancelLabel = "Use 9 103",
            )
        }
    }

    @Test
    fun toast() {
        shot("toast") {
            Column {
                OhToast(message = "Logged 60 s at 80 kg.")
                OhToast(
                    message = "Deleted one entry.",
                    actionLabel = "Undo",
                    onAction = {},
                    onDismiss = {},
                    modifier = Modifier.padding(top = OhTheme.space.s3),
                )
            }
        }
    }

    @Test
    fun timerBar() {
        shot("timer_bar") {
            Column {
                OhTimerBar(time = "1:30", progress = 0.4f)
                OhTimerBar(
                    time = "0:12",
                    progress = 0.8f,
                    work = true,
                    modifier = Modifier.padding(top = OhTheme.space.s3),
                )
                OhTimerBar(
                    time = "2:00",
                    progress = 0.1f,
                    paused = true,
                    modifier = Modifier.padding(top = OhTheme.space.s3),
                )
            }
        }
    }

    @Test
    fun icons() {
        // Every icon in the set, so a broken glyph cannot ship green.
        val groups: List<List<ImageVector>> = listOf(
            listOf(
                OhIcons.today, OhIcons.vitals, OhIcons.train, OhIcons.nutrition,
                OhIcons.stats, OhIcons.more, OhIcons.add, OhIcons.remove, OhIcons.check,
                OhIcons.close, OhIcons.chevronLeft, OhIcons.chevronRight, OhIcons.chevronDown,
                OhIcons.search, OhIcons.filter, OhIcons.scan, OhIcons.camera, OhIcons.play,
                OhIcons.pause, OhIcons.skip, OhIcons.timer, OhIcons.edit, OhIcons.delete,
                OhIcons.undo, OhIcons.share, OhIcons.export, OhIcons.lock, OhIcons.cloudOff,
                OhIcons.refresh, OhIcons.info, OhIcons.warning, OhIcons.externalLink,
                OhIcons.settings,
            ),
            listOf(
                OhModuleIcons.workout, OhModuleIcons.yoga, OhModuleIcons.steps,
                OhModuleIcons.weight, OhModuleIcons.heart, OhModuleIcons.sleep,
                OhModuleIcons.activity, OhModuleIcons.mood, OhModuleIcons.cycle,
                OhModuleIcons.family, OhModuleIcons.coach, OhModuleIcons.integrations,
            ),
            listOf(
                OhObjectIcons.barbell, OhObjectIcons.dumbbell, OhObjectIcons.kettlebell,
                OhObjectIcons.plate, OhObjectIcons.machine, OhObjectIcons.band,
                OhObjectIcons.mat, OhObjectIcons.shoe, OhObjectIcons.bike,
                OhObjectIcons.route, OhObjectIcons.scale, OhObjectIcons.cuff,
                OhObjectIcons.glucose, OhObjectIcons.pill, OhObjectIcons.flask,
                OhObjectIcons.file, OhObjectIcons.watch, OhObjectIcons.phone,
            ),
            listOf(
                OhGlyphIcons.strength, OhGlyphIcons.arms, OhGlyphIcons.abs,
                OhGlyphIcons.legs, OhGlyphIcons.pullUp, OhGlyphIcons.run,
                OhGlyphIcons.swim, OhGlyphIcons.boxing, OhGlyphIcons.stretch,
                OhGlyphIcons.moon, OhGlyphIcons.flame, OhGlyphIcons.bolt,
            ),
        )
        shot("icons") {
            Column {
                groups.forEach { group ->
                    group.chunked(ICONS_PER_ROW).forEach { row ->
                        androidx.compose.foundation.layout.Row {
                            row.forEach { icon ->
                                OhIcon(
                                    icon = icon,
                                    contentDescription = null,
                                    size = OhTheme.dims.tabIcon,
                                    tint = OhTheme.colors.label,
                                    modifier = Modifier.padding(OhTheme.space.s1),
                                )
                            }
                        }
                    }
                    androidx.compose.foundation.layout.Spacer(
                        Modifier.padding(top = OhTheme.space.s2),
                    )
                }
            }
        }
    }

    private fun shotPressed(name: String, nodeText: String, content: @Composable () -> Unit) {
        val variant = mutableStateOf(Variant.Dark)
        compose.setContent {
            val current = variant.value
            val density = LocalDensity.current
            OhTheme(dark = current.dark) {
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale = current.scale),
                    LocalLayoutDirection provides if (current.rtl) {
                        LayoutDirection.Rtl
                    } else {
                        LayoutDirection.Ltr
                    },
                ) {
                    Box(
                        modifier = Modifier
                            .testTag(SHOT)
                            .fillMaxWidth()
                            .background(OhTheme.colors.bg),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(OhTheme.space.s6)) {
                            content()
                        }
                    }
                }
            }
        }
        Variant.entries.forEach { entry ->
            compose.runOnIdle { variant.value = entry }
            compose.waitForIdle()
            compose.onNodeWithText(nodeText).performTouchInput { down(center) }
            compose.mainClock.advanceTimeBy(PRESS_SETTLE_MS)
            compose.waitForIdle()
            compose.onNodeWithTag(SHOT)
                .captureRoboImage("src/test/screenshots/${entry.dir}/$name.png")
            compose.onNodeWithText(nodeText).performTouchInput { up() }
        }
    }

    @Test
    fun buttonPressed() {
        shotPressed("button_pressed", "Log weight") {
            OhButton(text = "Log weight", onClick = {}, modifier = Modifier.fillMaxWidth())
        }
    }

    @Test
    fun cardPressed() {
        shotPressed("card_pressed", "Readiness") {
            OhCard(onClick = {}) {
                OhCardHeader(eyebrow = "Today", title = "Readiness", subtitle = "Sleep and HRV")
            }
        }
    }

    @Test
    fun rowPressed() {
        shotPressed("row_pressed", "Weight") {
            OhInsetGroup {
                OhRow(title = "Weight", value = "82.4", unit = "kg", onClick = {})
                OhRow(title = "Steps", value = "8 412", showDivider = true)
            }
        }
    }

    private fun tabs(): List<OhTabItem> = listOf(
        OhTabItem(label = "Today", icon = OhIcons.today),
        OhTabItem(label = "Vitals", icon = OhIcons.vitals),
        OhTabItem(label = "Train", icon = OhIcons.train, raised = true),
        OhTabItem(label = "Nutrition", icon = OhIcons.nutrition),
        OhTabItem(label = "Stats", icon = OhIcons.stats),
    )

    private companion object {
        const val SHOT = "shot"
        const val PRESS_SETTLE_MS = 500L
        const val ICONS_PER_ROW = 7
    }
}
