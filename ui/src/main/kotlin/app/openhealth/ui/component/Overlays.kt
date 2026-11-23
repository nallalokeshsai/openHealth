@file:Suppress("MagicNumber", "LongParameterList", "LongMethod", "CyclomaticComplexMethod")

package app.openhealth.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.openhealth.ui.R
import app.openhealth.ui.icon.OhIcon
import app.openhealth.ui.icon.OhIconName
import app.openhealth.ui.theme.OhTheme
import app.openhealth.ui.theme.mediumWeight
import app.openhealth.ui.theme.semiboldWeight
import kotlinx.coroutines.delay

/**
 * DESIGN.md §5.16. A bottom sheet on `bg-el`, 22 dp top corners, a 36 × 5 grabber, a 40 % black
 * scrim, and one primary action under the content with a quiet Cancel below it. A sheet never
 * contains another sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OhSheet(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    primaryAction: (@Composable () -> Unit)? = null,
    cancelLabel: String? = null,
    onCancel: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = OhTheme.colors
    val geometry = OhTheme.geometry
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val resolvedCancelLabel = cancelLabel ?: stringResource(R.string.oh_cancel)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        containerColor = colors.bgEl,
        contentColor = colors.label,
        scrimColor = Color.Black.copy(alpha = geometry.sheetScrimAlpha),
        tonalElevation = geometry.sheetTonalElevation,
        shape = RoundedCornerShape(
            topStart = OhTheme.radius.xl,
            topEnd = OhTheme.radius.xl
        ),
        dragHandle = { SheetGrabber() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(
                    start = geometry.sheetPaddingHorizontal,
                    end = geometry.sheetPaddingHorizontal,
                    bottom = geometry.sheetBottomPadding
                )
        ) {
            Text(title, style = OhTheme.type.title3.semiboldWeight(), color = colors.label)
            Spacer(Modifier.height(OhTheme.space.space7))
            content()
            if (primaryAction != null) {
                Spacer(Modifier.height(OhTheme.space.space7))
                primaryAction()
            }
            if (onCancel != null) {
                Spacer(Modifier.height(OhTheme.space.space3))
                Text(
                    text = resolvedCancelLabel,
                    style = OhTheme.type.body,
                    color = colors.label2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember {
                                MutableInteractionSource()
                            },
                            indication = null,
                            role = Role.Button,
                            onClick = onCancel
                        )
                        .padding(vertical = OhTheme.space.space3)
                        .semantics { contentDescription = resolvedCancelLabel }
                )
            }
        }
    }
}

/**
 * The sheet surface without the modal window, for the gallery and for screenshot tests. A sheet in
 * a screenshot is still a sheet: same fill, corners, grabber and padding.
 */
@Composable
fun OhSheetSurface(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = OhTheme.colors
    val geometry = OhTheme.geometry
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(
                    topStart = OhTheme.radius.xl,
                    topEnd = OhTheme.radius.xl
                )
            )
            .background(colors.bgEl)
            .padding(
                horizontal = geometry.sheetPaddingHorizontal,
                vertical = geometry.sheetPaddingVertical
            )
    ) {
        SheetGrabber()
        Spacer(Modifier.height(geometry.grabberGap))
        Text(title, style = OhTheme.type.title3.semiboldWeight(), color = colors.label)
        Spacer(Modifier.height(OhTheme.space.space7))
        content()
    }
}

@Composable
private fun SheetGrabber() {
    val colors = OhTheme.colors
    val geometry = OhTheme.geometry
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = geometry.grabberGap),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(geometry.grabberWidth)
                .height(geometry.grabberHeight)
                .clip(RoundedCornerShape(OhTheme.radius.full))
                .background(colors.label4)
        )
    }
}

/**
 * DESIGN.md §5.17. One decision or one small result: 300 dp wide (440 for a real summary),
 * `surface-raised`, 16 dp corners, 22 dp padding, at most two actions. A dialog with a form is a
 * sheet instead.
 */
@Composable
fun OhDialog(
    onDismissRequest: () -> Unit,
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    cancelLabel: String? = null,
    onCancel: (() -> Unit)? = null,
    wide: Boolean = false
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        OhDialogCard(
            title = title,
            body = body,
            primaryLabel = primaryLabel,
            onPrimary = onPrimary,
            modifier = modifier,
            cancelLabel = cancelLabel,
            onCancel = onCancel,
            wide = wide
        )
    }
}

/** The dialog surface on its own, so the gallery and screenshot tests can render it. */
@Composable
fun OhDialogCard(
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    cancelLabel: String? = null,
    onCancel: (() -> Unit)? = null,
    wide: Boolean = false
) {
    val colors = OhTheme.colors
    val type = OhTheme.type
    val geometry = OhTheme.geometry
    val shape = RoundedCornerShape(OhTheme.radius.lg)
    val resolvedCancelLabel = cancelLabel ?: stringResource(R.string.oh_cancel)
    Column(
        modifier = modifier
            .width(if (wide) geometry.dialogWideWidth else geometry.dialogWidth)
            .shadow(geometry.dialogShadow, shape)
            .clip(shape)
            .background(colors.surfaceRaised)
            .border(OhTheme.space.hairline, colors.raisedLine, shape)
            .padding(geometry.dialogPadding)
    ) {
        Text(title, style = type.title3.semiboldWeight(), color = colors.label)
        Spacer(Modifier.height(OhTheme.space.space2))
        Text(body, style = type.subhead, color = colors.label2)
        Spacer(Modifier.height(OhTheme.space.space7))
        OhButton(label = primaryLabel, onClick = onPrimary)
        if (onCancel != null) {
            Spacer(Modifier.height(OhTheme.space.space1))
            Text(
                text = resolvedCancelLabel,
                style = type.body,
                color = colors.label2,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember {
                            MutableInteractionSource()
                        },
                        indication = null,
                        role = Role.Button,
                        onClick = onCancel
                    )
                    .padding(vertical = OhTheme.space.space3)
            )
        }
    }
}

/** One toast at a time. A new message replaces the old (§5.18). */
data class OhToastMessage(
    val id: Long,
    val text: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null
)

class OhToastState internal constructor() {
    var current: OhToastMessage? by mutableStateOf(null)
        private set
    private var nextId = 1L

    fun show(text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null): OhToastMessage {
        val message = OhToastMessage(nextId++, text, actionLabel, onAction)
        current = message
        return message
    }

    fun dismiss() {
        current = null
    }
}

@Composable
fun rememberOhToastState(): OhToastState = remember { OhToastState() }

/**
 * DESIGN.md §5.18. The toast is the immediate shortcut, never the undo itself: a destructive action
 * also leaves a tombstone row for the session and a Trash entry for 30 days.
 */
@Composable
fun OhToastHost(state: OhToastState, modifier: Modifier = Modifier) {
    val geometry = OhTheme.geometry
    val motion = OhTheme.motion
    val message = state.current
    LaunchedEffect(message?.id) {
        val shown = message ?: return@LaunchedEffect
        val millis = if (shown.actionLabel != null) geometry.toastUndoMillis else geometry.toastPlainMillis
        delay(millis.toLong())
        if (state.current?.id == shown.id) state.dismiss()
    }
    Box(modifier = modifier.padding(bottom = geometry.toastLift)) {
        AnimatedVisibility(
            visible = message != null,
            enter = fadeIn(tween(motion.med)) + slideInVertically(tween(motion.med)) { it / 2 },
            exit = fadeOut(tween(motion.med)) + slideOutVertically(tween(motion.med)) { it / 2 }
        ) {
            if (message != null) {
                OhToast(
                    text = message.text,
                    actionLabel = message.actionLabel,
                    onAction = {
                        message.onAction?.invoke()
                        state.dismiss()
                    },
                    onDismiss = state::dismiss
                )
            }
        }
    }
}

/** The pill itself, for the gallery and for a screen that positions its own host. */
@Composable
fun OhToast(
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    val colors = OhTheme.colors
    val type = OhTheme.type
    val geometry = OhTheme.geometry
    val dismissLabel = stringResource(R.string.oh_dismiss)
    Row(
        modifier = modifier
            .fillMaxWidth(geometry.toastMaxWidthFraction)
            .clip(RoundedCornerShape(OhTheme.radius.xl))
            .background(colors.surface2.copy(alpha = 0.88f))
            .padding(
                horizontal = geometry.toastPaddingHorizontal,
                vertical = geometry.toastPaddingVertical
            )
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = type.subhead.mediumWeight(),
            color = colors.label,
            modifier = Modifier.weight(1f)
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.width(OhTheme.space.space3))
            Box(
                modifier = Modifier
                    .height(geometry.toastActionTarget)
                    .clickable(
                        interactionSource = remember {
                            MutableInteractionSource()
                        },
                        indication = null,
                        role = Role.Button,
                        onClick = onAction
                    )
                    .semantics { contentDescription = actionLabel }
                    .padding(horizontal = OhTheme.space.space2),
                contentAlignment = Alignment.Center
            ) {
                Text(actionLabel, style = type.subhead.semiboldWeight(), color = colors.accInk)
            }
        }
        if (onDismiss != null) {
            Spacer(Modifier.width(OhTheme.space.space2))
            Box(
                modifier = Modifier
                    .size(geometry.toastActionTarget)
                    .clickable(
                        interactionSource = remember {
                            MutableInteractionSource()
                        },
                        indication = null,
                        role = Role.Button,
                        onClick = onDismiss
                    )
                    .semantics { contentDescription = dismissLabel },
                contentAlignment = Alignment.Center
            ) {
                OhIcon(
                    name = OhIconName.Close,
                    contentDescription = null,
                    size = OhTheme.geometry.iconButtonGlyph,
                    tint = colors.label2
                )
            }
        }
    }
}
