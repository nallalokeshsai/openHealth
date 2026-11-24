package app.openhealth.ui.screens.roots

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.openhealth.ui.R
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
            modifier = Modifier
                .padding(top = OhTheme.space.s6)
                .semantics { heading() },
        )
        Spacer(Modifier.height(OhTheme.space.s1))
        Text(
            text = stringResource(R.string.root_subtitle),
            style = OhTheme.type.subhead,
            color = OhTheme.colors.label2,
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

/**
 * A module that is switched off (`pr.md` §2.4, §6.21): its surfaces are gone, its data is
 * kept, and the one thing the person can do from its root is turn it back on. It is a calm
 * explanation rather than an empty shell (`DESIGN.md` §7.6), and it offers no logging
 * action, because the module's entries are exactly what is hidden.
 */
@Composable
fun ModuleOffRoot(name: String, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    OhScreen(modifier = modifier) {
        Text(
            text = name,
            style = OhTheme.type.large.copy(fontWeight = OhWeight.bold),
            color = OhTheme.colors.label,
            modifier = Modifier
                .padding(top = OhTheme.space.s6)
                .semantics { heading() },
        )
        Spacer(Modifier.height(OhTheme.space.s1))
        Text(
            text = stringResource(R.string.root_subtitle),
            style = OhTheme.type.subhead,
            color = OhTheme.colors.label2,
        )
        Spacer(Modifier.height(OhTheme.space.s7))
        OhCard {
            OhEmptyState(
                title = stringResource(R.string.root_module_off_title, name),
                body = stringResource(R.string.root_module_off_body),
                glyph = app.openhealth.ui.icons.OhIcons.info,
                actionLabel = stringResource(R.string.root_module_off_action),
                onAction = onOpenSettings,
            )
        }
    }
}
