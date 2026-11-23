package app.openhealth.ui.screens.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.openhealth.ui.R
import app.openhealth.ui.components.OhButton
import app.openhealth.ui.components.OhButtonVariant
import app.openhealth.ui.components.OhIconButton
import app.openhealth.ui.icons.OhIcons
import app.openhealth.ui.theme.OhTheme
import app.openhealth.ui.theme.OhWeight

/**
 * The scrolling column every page shares: screen padding from `DESIGN.md` §4.3, a
 * centred 560 dp content column, and the tab bar's 88 dp reserve at the bottom.
 */
@Composable
fun OhScreen(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = OhTheme.space.s6)
            .padding(top = OhTheme.space.s3, bottom = OhTheme.dims.screenBottomReserve),
        content = content,
    )
}

/** A pushed page: back control, large title, then the body. The tab bar is hidden. */
@Composable
fun OhPageHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(OhTheme.dims.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OhIconButton(
            icon = OhIcons.chevronLeft,
            contentDescription = stringResource(R.string.oh_back),
            onClick = onBack,
        )
        Spacer(Modifier.width(OhTheme.space.s3))
        Text(
            text = title,
            style = OhTheme.type.title3.copy(fontWeight = OhWeight.semibold),
            color = OhTheme.colors.label,
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(OhTheme.space.s3))
}

/** A section heading: 22 dp above, 12 dp below, 13 sp `label-2` (§4.3, §14.2). */
@Composable
fun OhSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = OhTheme.type.footnote.copy(fontWeight = OhWeight.semibold),
        color = OhTheme.colors.label2,
        modifier = modifier
            .padding(top = OhTheme.space.s7, bottom = OhTheme.space.s1)
            .semantics { heading() },
    )
}

/**
 * The empty pattern of `DESIGN.md` §6.7: what this is, why it is empty, and one thing
 * to do. A screen never shows a placeholder number where a value would be.
 */
@Composable
fun OhEmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = OhTheme.space.s5),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = title,
            style = OhTheme.type.body.copy(fontWeight = OhWeight.medium),
            color = OhTheme.colors.label,
        )
        Spacer(Modifier.height(OhTheme.space.s0))
        Text(text = body, style = OhTheme.type.footnote, color = OhTheme.colors.label2)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(OhTheme.space.s5))
            OhButton(
                text = actionLabel,
                onClick = onAction,
                variant = OhButtonVariant.Secondary,
                size = app.openhealth.ui.components.OhButtonSize.Small,
            )
        }
    }
}

/** A thin accent progress line under a header: no dots, no percentages (§14.2). */
@Composable
fun OhProgressLine(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(OhTheme.dims.timerProgress)
            .background(OhTheme.colors.surface2),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(OhTheme.dims.timerProgress)
                .background(OhTheme.colors.acc),
        )
    }
}

/** Full-height surface for a focused page that has no tab bar. */
@Composable
fun OhFocusedScreen(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OhTheme.colors.bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = OhTheme.space.s6),
        verticalArrangement = Arrangement.Top,
        content = content,
    )
}
