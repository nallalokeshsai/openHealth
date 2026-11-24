package app.openhealth.ui.screens.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.openhealth.ui.components.OhTabBar
import app.openhealth.ui.components.OhTabItem
import app.openhealth.ui.components.OhTopBar
import app.openhealth.ui.theme.OhTheme

/**
 * The five-tab shell of `DESIGN.md` §4.4 and §5.15: a pinned global top bar, the
 * selected root, and the tab bar. Content scrolls under nothing; the bars are opaque
 * at their documented 94 % `bg-el` and the status/navigation areas stay `bg`.
 */
@Composable
fun OhRootShell(
    appName: String,
    dateLabel: String,
    quickAddLabel: String,
    settingsLabel: String,
    tabs: List<OhTabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onQuickAdd: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    settingsDot: Boolean = false,
    onDateClick: (() -> Unit)? = null,
    dateActionLabel: String? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OhTheme.colors.bg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            OhTopBar(
                appName = appName,
                dateLabel = dateLabel,
                quickAddLabel = quickAddLabel,
                settingsLabel = settingsLabel,
                onQuickAdd = onQuickAdd,
                onSettings = onSettings,
                settingsDot = settingsDot,
                onDateClick = onDateClick,
                dateActionLabel = dateActionLabel,
            )
            // The content scrolls under the translucent tab bar; every screen reserves
            // the documented 88 dp at its bottom (§4.3), so nothing hides behind it.
            Box(modifier = Modifier.weight(1f)) {
                content(PaddingValues())
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(OhTheme.colors.barFill)
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            OhTabBar(
                tabs = tabs,
                selectedIndex = selectedIndex,
                onSelect = onSelect,
            )
        }
    }
}
