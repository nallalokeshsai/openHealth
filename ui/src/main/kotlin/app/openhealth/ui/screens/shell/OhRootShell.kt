package app.openhealth.ui.screens.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
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
    content: @Composable (PaddingValues) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OhTheme.colors.bg)
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
        )
        Box(modifier = Modifier.weight(1f)) {
            content(PaddingValues())
        }
        Box(
            modifier = Modifier
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
