@file:Suppress("MatchingDeclarationName")

package app.openhealth.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import app.openhealth.ui.icons.OhIcon
import app.openhealth.ui.icons.OhIcons
import app.openhealth.ui.icons.withStrokeWidth
import app.openhealth.ui.theme.OhTheme
import app.openhealth.ui.theme.OhWeight

/** One destination in [OhTabBar]. `raised` marks the centre disc (`DESIGN.md` §5.15). */
data class OhTabItem(val label: String, val icon: ImageVector, val raised: Boolean = false)

/**
 * §4.4 The global top bar: the app name and the current date at the leading end,
 * quick add and settings as two 36 dp icon buttons at the trailing end. `bg-el`,
 * the same hairline as the tab bar, and hidden in a live session.
 */
@Composable
fun OhTopBar(
    appName: String,
    dateLabel: String,
    quickAddLabel: String,
    settingsLabel: String,
    onQuickAdd: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    settingsDot: Boolean = false,
    onDateClick: (() -> Unit)? = null,
) {
    val colors = OhTheme.colors
    val type = OhTheme.type
    val dims = OhTheme.dims
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.barFill)
            .ohHairline(
                color = colors.sepOp,
                insetStart = OhTheme.space.none,
                insetEnd = OhTheme.space.none,
                atBottom = true,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = dims.touchTarget)
                .padding(horizontal = OhTheme.space.s6),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val leadingInteraction = rememberInteraction()
            var leading = Modifier.weight(1f)
            if (onDateClick != null) {
                leading = leading.ohClickable(
                    leadingInteraction,
                    role = Role.Button,
                    onClick = onDateClick,
                )
            }
            Column(modifier = leading) {
                // The wordmark: "openHealth" in the UI font at 17/600 (§10.5).
                Text(
                    text = appName,
                    style = type.body.copy(fontWeight = OhWeight.semibold),
                    color = colors.label,
                    maxLines = 1,
                )
                Text(text = dateLabel, style = type.caption, color = colors.label2, maxLines = 1)
            }
            OhIconButton(
                icon = OhIcons.add,
                contentDescription = quickAddLabel,
                onClick = onQuickAdd,
            )
            Spacer(Modifier.width(OhTheme.space.s1))
            OhIconButton(
                icon = OhIcons.settings,
                contentDescription = settingsLabel,
                onClick = onSettings,
                showDot = settingsDot,
            )
        }
    }
}

/**
 * §5.15 Tab bar: five destinations with a raised centre Train disc. `bg-el` at
 * 94 %, a 0.5 dp top `sep-op` hairline, icons 25 dp, labels 11/500, active `acc-ink`.
 * The disc is 56 dp, `acc`, lifted 12 dp, and is the only glow in the app.
 */
@Composable
fun OhTabBar(
    tabs: List<OhTabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    dotIndices: Set<Int> = emptySet(),
) {
    val colors = OhTheme.colors
    val dims = OhTheme.dims
    require(tabs.size == TAB_COUNT) { "The tab bar has five destinations (DESIGN.md §5.15)." }
    require(tabs.count { it.raised } == 1) {
        "Exactly one destination is the raised centre disc (DESIGN.md §5.15)."
    }
    val raisedIndex = tabs.indexOfFirst { it.raised }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(dims.tabBarHeight + dims.trainDiscLift),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(dims.tabBarHeight)
                .background(colors.barFill)
                .ohHairline(
                    color = colors.sepOp,
                    insetStart = OhTheme.space.none,
                    insetEnd = OhTheme.space.none,
                    atBottom = false,
                ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dims.tabBarHeight)
                    .selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEachIndexed { index, tab ->
                    TabCell(
                        tab = tab,
                        selected = index == selectedIndex,
                        showDot = index in dotIndices,
                        onClick = { onSelect(index) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        RaisedDisc(
            tab = tabs[raisedIndex],
            selected = raisedIndex == selectedIndex,
            onClick = { onSelect(raisedIndex) },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = OhTheme.space.s6),
        )
    }
}

@Composable
private fun TabCell(
    tab: OhTabItem,
    selected: Boolean,
    showDot: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OhTheme.colors
    val type = OhTheme.type
    val dims = OhTheme.dims
    if (tab.raised) {
        // The centre cell is a spacer: the disc is drawn above the bar.
        Box(modifier = modifier.height(dims.tabBarHeight))
        return
    }
    val tint = if (selected) colors.accInk else colors.label2
    val interaction = rememberInteraction()
    // At the very largest font scales the tab bar keeps its icons and drops its labels,
    // but the name stays on the accessibility node and a target never shrinks (§4.3, §11.1).
    val showLabel = LocalDensity.current.fontScale < LABEL_DROP_FONT_SCALE
    Column(
        modifier = modifier
            .height(dims.tabBarHeight)
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .semantics { contentDescription = tab.label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(modifier = Modifier.ohPressScale(interaction, OhTheme.motion.pressScaleTab)) {
            val glyph = remember(tab.icon, selected) {
                tab.icon.withStrokeWidth(
                    if (selected) dims.tabStrokeActive else dims.tabStrokeRest,
                )
            }
            OhIcon(
                icon = glyph,
                contentDescription = null,
                size = dims.tabIcon,
                tint = tint,
            )
            if (showDot) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(dims.dot + dims.dotRing * 2f)
                        .clip(CircleShape)
                        .background(colors.bgEl),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(dims.dot)
                            .clip(CircleShape)
                            .background(colors.orange),
                    )
                }
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(OhTheme.space.s0))
            Text(
                text = tab.label,
                style = type.caption2.copy(fontWeight = OhWeight.medium),
                color = tint,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun RaisedDisc(
    tab: OhTabItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OhTheme.colors
    val dims = OhTheme.dims
    val interaction = rememberInteraction()
    Box(
        modifier = modifier
            .size(dims.trainDisc)
            // The only glow in the app: a soft shadow in the accent (§5.15).
            .shadow(
                elevation = dims.raisedElevation,
                shape = CircleShape,
                ambientColor = colors.acc,
                spotColor = colors.acc,
            )
            .ohPressScale(interaction, OhTheme.motion.pressScaleTab)
            .clip(CircleShape)
            .background(colors.acc)
            .ohClickable(interaction, role = Role.Tab, onClick = onClick)
            .semantics {
                contentDescription = tab.label
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        OhIcon(
            icon = tab.icon,
            contentDescription = tab.label,
            size = dims.tabIcon,
            tint = colors.onAcc,
        )
    }
}

/** At this font scale the tab bar keeps icons and drops labels (§4.3). */
private const val LABEL_DROP_FONT_SCALE = 1.75f

/** The five destinations of DESIGN.md §5.15. */
private const val TAB_COUNT = 5
