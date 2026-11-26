package app.openhealth.ui.screens.clinical

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.openhealth.ui.R
import app.openhealth.ui.components.OhCard
import app.openhealth.ui.components.OhInsetGroup
import app.openhealth.ui.components.OhRow
import app.openhealth.ui.icons.OhIcons
import app.openhealth.ui.screens.common.OhEmptyState
import app.openhealth.ui.screens.common.OhPageHeader
import app.openhealth.ui.theme.OhTheme

/**
 * The weekly review (`pr.md` §6.10). A description of the last seven days in the
 * person's own numbers, with a citation beside every range. No score, no streak, no
 * verdict.
 */
@Composable
fun WeeklyReviewScreen(
    model: WeeklyReviewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OhTheme.colors.bg)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = OhTheme.space.s6),
    ) {
        OhPageHeader(title = model.title, onBack = onBack)
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = OhTheme.space.s8),
        ) {
            Text(
                text = model.weekLabel,
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
                modifier = Modifier.padding(bottom = OhTheme.space.s4),
            )
            if (model.items.isEmpty()) {
                OhCard {
                    OhEmptyState(
                        title = model.emptyTitle,
                        body = model.emptyBody,
                        glyph = OhIcons.stats,
                    )
                }
                return@Column
            }
            Text(
                text = model.accessible,
                style = OhTheme.type.body,
                color = OhTheme.colors.label2,
                modifier = Modifier.padding(bottom = OhTheme.space.s4),
            )
            OhInsetGroup {
                model.items.forEachIndexed { index, item ->
                    OhRow(
                        title = item.title,
                        subtitle = item.citation?.let { "${item.note} · $it" } ?: item.note,
                        value = item.value,
                        showDivider = index != model.items.lastIndex,
                    )
                }
            }
            Spacer(Modifier.height(OhTheme.space.s5))
            Text(
                text = stringResource(R.string.clinical_weekly_note),
                style = OhTheme.type.caption,
                color = OhTheme.colors.label2,
            )
        }
    }
}
