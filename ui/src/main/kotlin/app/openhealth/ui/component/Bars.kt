@file:Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")

package app.openhealth.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.openhealth.ui.R
import app.openhealth.ui.icon.OhIcon
import app.openhealth.ui.icon.OhIconName
import app.openhealth.ui.theme.OhTheme
import app.openhealth.ui.theme.mediumWeight
import app.openhealth.ui.theme.semiboldWeight

/**
 * DESIGN.md §4.4. The global top bar: the wordmark and the local date at the leading end, quick add
 * and Settings as two 36 dp icon buttons at the trailing end. It is pinned on every root and hidden
 * in a live session. There is no More tab and no second floating disc.
 */
@Composable
fun OhTopBar(
    date: String,
    onQuickAdd: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    appName: String? = null,
    showSyncDot: Boolean = false
) {
    val colors = OhTheme.colors
    val type = OhTheme.type
    val geometry = OhTheme.geometry
    val density = LocalDensity.current
    val hairlinePx = with(density) { OhTheme.space.hairline.toPx() }
    val name = appName ?: stringResource(R.string.oh_app_name)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = geometry.topBarHeight)
            .bottomHairline(colors.sepOp, hairlinePx)
            .background(colors.bgEl.copy(alpha = geometry.tabBarAlpha))
            .padding(horizontal = OhTheme.geometry.screenPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = type.body.semiboldWeight(), color = colors.label)
            Text(date, style = type.caption, color = colors.label2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        OhIconButton(
            icon = OhIconName.Add,
            contentDescription = stringResource(R.string.oh_quick_add),
            onClick = onQuickAdd
        )
        Spacer(Modifier.width(OhTheme.space.space1))
        Box {
            OhIconButton(
                icon = OhIconName.Settings,
                contentDescription = stringResource(R.string.oh_settings),
                onClick = onSettings
            )
            if (showSyncDot) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = OhTheme.space.space1, end = OhTheme.space.space2)
                        .size(geometry.tabDot + geometry.tabDotRing * 2)
                        .clip(RoundedCornerShape(OhTheme.radius.full))
                        .background(colors.bgEl),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(geometry.tabDot)
                            .clip(RoundedCornerShape(OhTheme.radius.full))
                            .background(colors.orange)
                    )
                }
            }
        }
    }
}

/** The five destinations of DESIGN.md §5.15: Today, Vitals, Train (raised centre), Nutrition,
 * Stats. */
enum class OhTab(val labelRes: Int, val icon: OhIconName) {
    Today(R.string.oh_tab_today, OhIconName.Today),
    Vitals(R.string.oh_tab_vitals, OhIconName.Body),
    Train(R.string.oh_tab_train, OhIconName.Train),
    Nutrition(R.string.oh_tab_nutrition, OhIconName.Food),
    Stats(R.string.oh_tab_stats, OhIconName.Insights)
}

/**
 * DESIGN.md §5.15. `bg-el`, a 0.5 dp top hairline, 56 dp of content, icons at 25 dp (stroke 1.65,
 * 2.0 active) and 11/500 labels. Train is the raised 56 dp accent disc, the only glow in the app.
 */
@Composable
fun OhTabBar(selected: OhTab, onSelect: (OhTab) -> Unit, modifier: Modifier = Modifier, dots: Set<OhTab> = emptySet()) {
    val colors = OhTheme.colors
    val geometry = OhTheme.geometry
    val density = LocalDensity.current
    val hairlinePx = with(density) { OhTheme.space.hairline.toPx() }
    val totalHeight = geometry.tabBarHeight + geometry.trainDisc / 2 + geometry.trainDiscLift
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(totalHeight)
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(geometry.tabBarHeight)
                .topHairline(colors.sepOp, hairlinePx)
                .background(colors.bgEl.copy(alpha = geometry.tabBarAlpha))
                .padding(vertical = geometry.tabBarPaddingVertical),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OhTab.entries.forEach { tab ->
                if (tab == OhTab.Train) {
                    Spacer(Modifier.weight(1f))
                } else {
                    TabCell(
                        tab = tab,
                        selected = tab == selected,
                        hasDot = tab in dots,
                        modifier = Modifier.weight(1f),
                        onSelect = { onSelect(tab) }
                    )
                }
            }
        }
        TrainDisc(
            selected = selected == OhTab.Train,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(geometry.trainDisc),
            onSelect = { onSelect(OhTab.Train) }
        )
    }
}

@Composable
private fun TabCell(tab: OhTab, selected: Boolean, hasDot: Boolean, modifier: Modifier, onSelect: () -> Unit) {
    val colors = OhTheme.colors
    val type = OhTheme.type
    val geometry = OhTheme.geometry
    val motion = OhTheme.motion
    val label = stringResource(tab.labelRes)
    val tint = if (selected) colors.accInk else colors.label2
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = if (pressed) motion.pressTab else 1f
    Column(
        modifier = modifier
            .fillMaxHeight()
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onSelect
            )
            .semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
        ) {
            OhIcon(
                name = tab.icon,
                contentDescription = null,
                size = geometry.tabIcon,
                tint = tint,
                strokeWidth = if (selected) geometry.iconStrokeTabActive else geometry.iconStrokeTab
            )
            if (hasDot) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = OhTheme.space.space0, y = -OhTheme.space.space0)
                        .size(geometry.tabDot + geometry.tabDotRing * 2)
                        .clip(RoundedCornerShape(OhTheme.radius.full))
                        .background(colors.bgEl),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(geometry.tabDot)
                            .clip(RoundedCornerShape(OhTheme.radius.full))
                            .background(colors.orange)
                    )
                }
            }
        }
        // At the largest text sizes the labels may drop and the icons carry the tab (§4.3);
        // otherwise 200 % text would clip inside the 56 dp bar.
        if (LocalDensity.current.fontScale <= geometry.largeTextScale) {
            Spacer(Modifier.height(geometry.tabLabelGap))
            Text(
                text = label,
                style = type.caption2.mediumWeight(),
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TrainDisc(selected: Boolean, modifier: Modifier, onSelect: () -> Unit) {
    val colors = OhTheme.colors
    val geometry = OhTheme.geometry
    val motion = OhTheme.motion
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val animatedScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) motion.pressTab else 1f,
        animationSpec = androidx.compose.animation.core.tween(motion.fast, easing = motion.ease),
        label = "train-disc-scale"
    )
    val shape = RoundedCornerShape(OhTheme.radius.full)
    val trainLabel = stringResource(OhTab.Train.labelRes)
    Box(
        modifier = modifier
            .shadow(
                elevation = geometry.trainDiscShadow,
                shape = shape,
                ambientColor = colors.acc,
                spotColor = colors.acc
            )
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            }
            .clip(shape)
            .background(colors.acc)
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onSelect
            )
            .semantics { contentDescription = trainLabel },
        contentAlignment = Alignment.Center
    ) {
        OhIcon(
            name = OhIconName.Train,
            contentDescription = null,
            size = geometry.tabIcon,
            tint = colors.onAcc,
            strokeWidth = geometry.iconStrokeTabActive
        )
    }
}

/**
 * DESIGN.md §5.19: the inline rest/elapsed timer, 22 dp above the tab bar, a 3 dp progress line in
 * the accent, and the clock at 28/600 tabular. A work timer uses the accent ink so "holding" and
 * "resting" are distinguishable at a glance; paused is 50 %.
 */
@Composable
fun OhTimerBar(
    clock: String,
    progress: Float,
    running: Boolean,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onPauseToggle: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    work: Boolean = false
) {
    val colors = OhTheme.colors
    val type = OhTheme.type
    val geometry = OhTheme.geometry
    val density = LocalDensity.current
    val shape = RoundedCornerShape(OhTheme.radius.base)
    val timerDescription = stringResource(R.string.oh_timer, clock)
    val subtractDescription = stringResource(R.string.oh_subtract_15)
    val addDescription = stringResource(R.string.oh_add_15)
    val pauseDescription = stringResource(
        if (running) R.string.oh_pause_timer else R.string.oh_resume_timer
    )
    val skipDescription = stringResource(R.string.oh_skip_rest)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(geometry.timerBarShadow, shape)
            .clip(shape)
            .background(colors.surfaceRaised.copy(alpha = geometry.timerBarAlpha))
            .border(
                width = if (work) geometry.timerWorkRing else OhTheme.space.hairline,
                color = if (work) colors.acc else colors.raisedLine,
                shape = shape
            )
            .alpha(if (running) 1f else geometry.timerPausedAlpha)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = timerDescription
            }
            .padding(geometry.timerBarPadding)
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(geometry.timerProgress)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(colors.acc)
            )
        }
        Spacer(Modifier.height(OhTheme.space.space2))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = clock,
                style = type.timer,
                color = if (work) colors.accInk else colors.label
            )
            Spacer(Modifier.weight(1f))
            val largeText = LocalDensity.current.fontScale > geometry.largeTextScale
            if (largeText) {
                OhIconButton(
                    icon = OhIconName.Minus,
                    contentDescription = subtractDescription,
                    onClick = onMinus
                )
                OhIconButton(
                    icon = OhIconName.Add,
                    contentDescription = addDescription,
                    onClick = onPlus
                )
            } else {
                TimerControl(
                    label = "−15",
                    description = subtractDescription,
                    onClick = onMinus
                )
                TimerControl(
                    label = "+15",
                    description = addDescription,
                    onClick = onPlus
                )
            }
            Spacer(Modifier.width(geometry.timerSkipGap))
            OhIconButton(
                icon = if (running) OhIconName.Pause else OhIconName.Play,
                contentDescription = pauseDescription,
                onClick = onPauseToggle
            )
            Spacer(Modifier.width(geometry.timerSkipGap))
            if (largeText) {
                OhIconButton(
                    icon = OhIconName.Skip,
                    contentDescription = skipDescription,
                    onClick = onSkip
                )
            } else {
                TimerControl(
                    label = "Skip",
                    description = skipDescription,
                    onClick = onSkip
                )
            }
        }
    }
}

@Composable
private fun TimerControl(label: String, description: String, onClick: () -> Unit) {
    val colors = OhTheme.colors
    val type = OhTheme.type
    Box(
        modifier = Modifier
            .heightIn(min = OhTheme.geometry.timerControl)
            .width(OhTheme.geometry.timerControl)
            .clip(RoundedCornerShape(OhTheme.radius.sm))
            .clickable(
                interactionSource = remember {
                    MutableInteractionSource()
                },
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = type.footnote.semiboldWeight(), color = colors.label2, maxLines = 1)
    }
}
