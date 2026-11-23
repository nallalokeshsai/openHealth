package app.openhealth.ui.screens.roots

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.openhealth.ui.components.OhCard
import app.openhealth.ui.screens.common.OhEmptyState
import app.openhealth.ui.screens.common.OhScreen
import app.openhealth.ui.theme.OhTheme
import app.openhealth.ui.theme.OhWeight

/**
 * The four roots B3 does not fill yet. Each is an honest empty state with one working
 * action where one exists; none shows a number it does not have.
 */
@Composable
fun RootStubScreen(
    title: String,
    emptyTitle: String,
    emptyBody: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    OhScreen(modifier = modifier) {
        Text(
            text = title,
            style = OhTheme.type.large.copy(fontWeight = OhWeight.bold),
            color = OhTheme.colors.label,
            modifier = Modifier.padding(top = OhTheme.space.s6),
        )
        Spacer(Modifier.height(OhTheme.space.s7))
        OhCard {
            OhEmptyState(
                title = emptyTitle,
                body = emptyBody,
                glyph = app.openhealth.ui.icons.OhIcons.info,
                actionLabel = actionLabel,
                onAction = onAction,
            )
        }
    }
}
