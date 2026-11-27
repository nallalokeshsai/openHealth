@file:Suppress("LongParameterList")

package app.openhealth.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.openhealth.ui.R
import app.openhealth.ui.icons.OhIcon
import app.openhealth.ui.icons.OhIcons
import app.openhealth.ui.theme.OhAlpha
import app.openhealth.ui.theme.OhTheme
import app.openhealth.ui.theme.OhWeight
import app.openhealth.ui.theme.ltrFigures
import app.openhealth.ui.theme.sheetMaxHeight
import app.openhealth.ui.theme.tabular
import app.openhealth.ui.theme.toastMaxWidth

/**
 * §5.16 Sheet: `bg-el`, top radius 22, grabber 36 × 5 `label-4`, title 20/600, the
 * primary action as a full-width button under the content and Cancel as a quiet
 * text button below it. This composes the sheet body only; [OhModalSheet] adds the
 * modal behaviour so a screenshot test can render the body on its own.
 */
@Composable
fun OhSheet(
    modifier: Modifier = Modifier,
    title: String? = null,
    grabber: Boolean = true,
    contentDescription: String? = null,
    primaryLabel: String? = null,
    primaryEnabled: Boolean = true,
    onPrimary: (() -> Unit)? = null,
    cancelLabel: String? = null,
    onCancel: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = OhTheme.colors
    val type = OhTheme.type
    val dims = OhTheme.dims
    val screenHeight = app.openhealth.ui.theme.windowHeightDp()
    val statusTop = with(LocalDensity.current) {
        WindowInsets.statusBars.getTop(this).toDp().value
    }
    val maxHeight = sheetMaxHeight(screenHeight, statusTop, dims.sheetMaxHeightFraction)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .clip(RoundedCornerShape(topStart = OhTheme.radius.xl, topEnd = OhTheme.radius.xl))
            .background(colors.bgEl)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .imePadding()
            .padding(
                start = OhTheme.space.s6,
                end = OhTheme.space.s6,
                top = dims.grabberTop,
                bottom = OhTheme.space.s7,
            )
            .semantics {
                if (contentDescription !=
                    null
                ) {
                    this.contentDescription = contentDescription
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (grabber) {
            Box(
                modifier = Modifier
                    .size(width = dims.grabberWidth, height = dims.grabberHeight)
                    .clip(RoundedCornerShape(OhTheme.radius.full))
                    .background(colors.label4),
            )
            Spacer(Modifier.height(OhTheme.space.s2))
        }
        if (title != null) {
            Text(
                text = title,
                style = type.title3.copy(fontWeight = OhWeight.semibold),
                color = colors.label,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = OhTheme.space.s3),
            )
            Spacer(Modifier.height(OhTheme.space.s7))
        }
        // The body scrolls only downwards; the title and the actions stay put (§5.16).
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            content = content,
        )
        if (primaryLabel != null) {
            Spacer(Modifier.height(OhTheme.space.s7))
            OhButton(
                text = primaryLabel,
                onClick = { onPrimary?.invoke() },
                enabled = primaryEnabled,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (cancelLabel != null) {
            Spacer(Modifier.height(OhTheme.space.s2))
            OhButton(
                text = cancelLabel,
                onClick = { onCancel?.invoke() },
                variant = OhButtonVariant.Quiet,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The modal sheet: scrim 40 % black, 340 ms rise, back / scrim tap / swipe-down all
 * close (`DESIGN.md` §5.16). It never contains another sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OhModalSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    primaryLabel: String? = null,
    primaryEnabled: Boolean = true,
    onPrimary: (() -> Unit)? = null,
    cancelLabel: String? = null,
    onCancel: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!visible) return
    val colors = OhTheme.colors
    val dims = OhTheme.dims
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
        ),
        containerColor = colors.bgEl,
        contentColor = colors.label,
        scrimColor = colors.scrim,
        shape = RoundedCornerShape(topStart = OhTheme.radius.xl, topEnd = OhTheme.radius.xl),
        contentWindowInsets = { WindowInsets(0) },
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = dims.grabberTop, bottom = OhTheme.space.s2)
                    .size(width = dims.grabberWidth, height = dims.grabberHeight)
                    .clip(RoundedCornerShape(OhTheme.radius.full))
                    .background(colors.label4),
            )
        },
    ) {
        OhSheet(
            title = title,
            grabber = false,
            primaryLabel = primaryLabel,
            primaryEnabled = primaryEnabled,
            onPrimary = onPrimary,
            cancelLabel = cancelLabel,
            onCancel = onCancel,
            content = content,
        )
    }
}

/**
 * §5.17 Dialog: 300 dp wide (440 dp for a real summary), `surface-raised`, radius
 * 16, padding 22, two actions maximum: the primary and a quiet cancel.
 */
@Composable
fun OhDialogCard(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    wide: Boolean = false,
    primaryLabel: String? = null,
    primaryEnabled: Boolean = true,
    onPrimary: (() -> Unit)? = null,
    cancelLabel: String? = null,
    onCancel: (() -> Unit)? = null,
    destructive: Boolean = false,
) {
    val colors = OhTheme.colors
    val type = OhTheme.type
    val dims = OhTheme.dims
    val shape = RoundedCornerShape(OhTheme.radius.lg)
    val elevation = if (colors.dark) dims.dialogElevation else dims.raisedElevation
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val motion = OhTheme.motion
    val pop by animateFloatAsState(
        targetValue = if (appeared) OhAlpha.full else DIALOG_POP_FROM,
        animationSpec = tween(motion.duration(motion.dialog), easing = motion.ease),
        label = "dialogPop",
    )
    Column(
        modifier = modifier
            .width(if (wide) dims.dialogWidthWide else dims.dialogWidth)
            .graphicsLayer {
                scaleX = pop
                scaleY = pop
            }
            .shadow(elevation, shape)
            .clip(shape)
            .background(colors.surfaceRaised)
            .then(
                if (colors.dark) {
                    Modifier.border(
                        dims.hairline,
                        colors.raisedLine,
                        shape,
                    )
                } else {
                    Modifier
                },
            )
            .padding(dims.dialogPad),
    ) {
        Text(
            text = title,
            style = type.title3.copy(fontWeight = OhWeight.semibold),
            color = colors.label,
        )
        if (message != null) {
            Spacer(Modifier.height(OhTheme.space.s3))
            Text(text = message, style = type.body, color = colors.label2)
        }
        Spacer(Modifier.height(OhTheme.space.s7))
        // At the largest text sizes the two actions stack instead of clipping a label.
        val stacked = LocalDensity.current.fontScale >= dims.largeFontScale
        val actions: @Composable () -> Unit = {
            if (cancelLabel != null) {
                OhButton(
                    text = cancelLabel,
                    onClick = { onCancel?.invoke() },
                    variant = OhButtonVariant.Quiet,
                    modifier = if (stacked) Modifier.fillMaxWidth() else Modifier.weight(1f),
                )
            }
            if (primaryLabel != null) {
                OhButton(
                    text = primaryLabel,
                    onClick = { onPrimary?.invoke() },
                    enabled = primaryEnabled,
                    variant = if (destructive) {
                        OhButtonVariant.Danger
                    } else {
                        OhButtonVariant.Primary
                    },
                    modifier = if (stacked) Modifier.fillMaxWidth() else Modifier.weight(1f),
                )
            }
        }
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(OhTheme.space.s3)) { actions() }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(OhTheme.space.s3)) { actions() }
        }
    }
}

/** The modal dialog wrapper: 280 ms pop from 94 % scale, scrim 40 % black. */
@Composable
fun OhDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    wide: Boolean = false,
    primaryLabel: String? = null,
    primaryEnabled: Boolean = true,
    onPrimary: (() -> Unit)? = null,
    cancelLabel: String? = null,
    onCancel: (() -> Unit)? = null,
    destructive: Boolean = false,
) {
    if (!visible) return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OhTheme.space.s6),
            contentAlignment = Alignment.Center,
        ) {
            OhDialogCard(
                title = title,
                modifier = modifier,
                message = message,
                wide = wide,
                primaryLabel = primaryLabel,
                primaryEnabled = primaryEnabled,
                onPrimary = onPrimary,
                cancelLabel = cancelLabel,
                onCancel = onCancel,
                destructive = destructive,
            )
        }
    }
}

/**
 * §5.18 Toast: a 22 dp-radius pill, `surface-2` at 88 %, 15/500, at most 88 % wide.
 * The toast is **not** the undo: the tombstone row and the 30-day Trash are
 * (`pr.md` §6.29). One toast at a time.
 */
@Composable
fun OhToast(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    dismissLabel: String = stringResource(R.string.oh_toast_dismiss),
) {
    val colors = OhTheme.colors
    val type = OhTheme.type
    val dims = OhTheme.dims
    val maxWidth =
        toastMaxWidth(app.openhealth.ui.theme.windowWidthDp(), dims.toastMaxWidthFraction)
    Row(
        modifier = modifier
            .widthIn(max = maxWidth)
            .clip(RoundedCornerShape(OhTheme.radius.xl))
            .background(colors.toastFill)
            .padding(horizontal = dims.toastPadH, vertical = dims.toastPadV)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = type.subhead.copy(fontWeight = OhWeight.medium),
            color = colors.label,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.width(OhTheme.space.s5))
            ToastAction(label = actionLabel, onClick = onAction, accent = true)
        }
        if (onDismiss != null) {
            Spacer(Modifier.width(OhTheme.space.s2))
            Box(
                modifier = Modifier
                    .size(dims.touchTarget)
                    .ohClickable(
                        rememberInteraction(),
                        role = androidx.compose.ui.semantics.Role.Button,
                    ) {
                        onDismiss()
                    }
                    .semantics { contentDescription = dismissLabel },
                contentAlignment = Alignment.Center,
            ) {
                OhIcon(
                    icon = OhIcons.close,
                    contentDescription = null,
                    size = dims.iconButtonGlyph,
                    tint = colors.label2,
                )
            }
        }
    }
}

@Composable
private fun ToastAction(label: String, onClick: () -> Unit, accent: Boolean) {
    val colors = OhTheme.colors
    val type = OhTheme.type
    Box(
        modifier = Modifier
            .height(OhTheme.dims.touchTarget)
            .ohClickable(
                rememberInteraction(),
                role = androidx.compose.ui.semantics.Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = type.subhead.copy(fontWeight = OhWeight.medium),
            color = if (accent) colors.accInk else colors.label2,
        )
    }
}

/**
 * §5.19 Timer bar: 22 dp above the tab bar, radius 12, `surface-raised` at 97 %, a
 * 3 dp accent progress line on the top edge, a 28/600 tabular clock, and four 44 dp
 * controls. Paused is at 50 %; a work timer reads `acc-ink`.
 */
@Composable
fun OhTimerBar(
    time: String,
    progress: Float,
    modifier: Modifier = Modifier,
    remainingSeconds: Long? = null,
    paused: Boolean = false,
    work: Boolean = false,
    onMinus: () -> Unit = {},
    onPlus: () -> Unit = {},
    onPauseToggle: () -> Unit = {},
    onSkip: () -> Unit = {},
    minusLabel: String = stringResource(R.string.oh_timer_minus),
    plusLabel: String = stringResource(R.string.oh_timer_plus),
    pauseLabel: String = stringResource(R.string.oh_timer_pause),
    playLabel: String = stringResource(R.string.oh_timer_resume),
    skipLabel: String = stringResource(R.string.oh_timer_skip),
) {
    val colors = OhTheme.colors
    val type = OhTheme.type
    val dims = OhTheme.dims
    val shape = RoundedCornerShape(OhTheme.radius.base)
    // The clock changes every second; announcing all of it would be noise. A hidden
    // milestone node carries only 10, 3, 2, 1 and Ready (`DESIGN.md` §11.2).
    val announcement = when (remainingSeconds) {
        ANNOUNCE_TEN -> stringResource(R.string.oh_timer_ten)
        ANNOUNCE_THREE -> stringResource(R.string.oh_timer_three)
        2L -> stringResource(R.string.oh_timer_two)
        1L -> stringResource(R.string.oh_timer_one)
        0L -> stringResource(R.string.oh_timer_ready)
        else -> null
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (paused) OhAlpha.paused else OhAlpha.full)
            .shadow(dims.raisedElevation, shape)
            .clip(shape)
            .background(colors.timerBarFill)
            .border(dims.hairline, if (work) colors.acc else colors.raisedLine, shape),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(dims.timerProgress)
                .background(colors.surface3),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(dims.timerProgress)
                    .background(colors.acc),
            )
        }
        if (announcement != null) {
            Box(
                modifier = Modifier
                    .size(OhTheme.space.none)
                    .semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = announcement
                    },
            )
        }
        Row(
            modifier = Modifier.padding(horizontal = OhTheme.space.s5, vertical = OhTheme.space.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = time,
                style = type.timer.copy(fontWeight = OhWeight.semibold).tabular().ltrFigures(),
                color = if (work) colors.accInk else colors.label,
            )
            Spacer(Modifier.weight(1f))
            TimerControl(icon = OhIcons.remove, description = minusLabel, onClick = onMinus)
            TimerControl(icon = OhIcons.add, description = plusLabel, onClick = onPlus)
            TimerControl(
                icon = if (paused) OhIcons.play else OhIcons.pause,
                description = if (paused) playLabel else pauseLabel,
                onClick = onPauseToggle,
            )
            TimerControl(icon = OhIcons.skip, description = skipLabel, onClick = onSkip)
        }
    }
}

@Composable
private fun TimerControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    val colors = OhTheme.colors
    val dims = OhTheme.dims
    Box(
        modifier = Modifier
            .size(dims.touchTarget)
            .ohClickable(
                rememberInteraction(),
                role = androidx.compose.ui.semantics.Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        OhIcon(
            icon = icon,
            contentDescription = null,
            size = dims.iconButtonGlyph,
            tint = colors.label,
        )
    }
}

private const val ANNOUNCE_TEN = 10L
private const val ANNOUNCE_THREE = 3L

/** The dialog pops from 94 % scale (DESIGN.md §5.17). */
private const val DIALOG_POP_FROM = 0.94f
