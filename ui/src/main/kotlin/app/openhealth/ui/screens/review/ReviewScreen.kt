package app.openhealth.ui.screens.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.openhealth.core.review.ReviewKind
import app.openhealth.ui.R
import app.openhealth.ui.components.OhButton
import app.openhealth.ui.components.OhButtonSize
import app.openhealth.ui.components.OhButtonVariant
import app.openhealth.ui.components.OhCard
import app.openhealth.ui.components.OhInsetGroup
import app.openhealth.ui.components.OhModalSheet
import app.openhealth.ui.components.OhRow
import app.openhealth.ui.icons.OhIcons
import app.openhealth.ui.screens.common.OhEmptyState
import app.openhealth.ui.screens.common.OhPageHeader
import app.openhealth.ui.theme.OhTheme

/**
 * The review inbox (`pr.md` §6.28): probable duplicates, values outside the plausibility
 * screen, and days where two sources disagree. Every row is a question, never an
 * automatic edit; the item's sheet shows what each source said before it offers an
 * action.
 */
@Composable
fun ReviewScreen(
    model: ReviewModel,
    onBack: () -> Unit,
    onAction: (ReviewRowModel, ReviewActionModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf<ReviewRowModel?>(null) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OhTheme.colors.bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = OhTheme.space.s6),
    ) {
        OhPageHeader(title = stringResource(R.string.review_title), onBack = onBack)
        androidx.compose.material3.Text(
            text = model.countLabel,
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
            modifier = Modifier.padding(bottom = OhTheme.space.s5),
        )
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            if (model.rows.isEmpty()) {
                OhCard {
                    OhEmptyState(
                        title = model.emptyTitle,
                        body = model.emptyBody,
                        glyph = OhIcons.check,
                        actionLabel = stringResource(R.string.review_empty_action),
                        onAction = onBack,
                    )
                }
            } else {
                OhInsetGroup {
                    model.rows.forEachIndexed { index, row ->
                        OhRow(
                            title = row.title,
                            subtitle = row.subtitle,
                            leadingIcon = row.kind.icon(),
                            // Neutral glyph tiles: the accent on this screen belongs to
                            // nothing yet, so it is not spent on three decorations.
                            leadingTint = OhTheme.colors.surface2,
                            leadingInk = OhTheme.colors.label2,
                            showDivider = index > 0,
                            showChevron = true,
                            onClick = { open = row },
                        )
                    }
                }
            }
            Spacer(Modifier.height(OhTheme.space.s8))
        }
    }
    val item = open
    if (item != null) {
        OhModalSheet(
            visible = true,
            onDismiss = { open = null },
            title = item.title,
            cancelLabel = stringResource(R.string.settings_close),
            onCancel = { open = null },
        ) {
            item.readings.forEach { reading ->
                OhRow(
                    title = reading.sourceLabel,
                    subtitle = "${reading.timeText} · ${reading.valueText}",
                    trailing = if (reading.winner) {
                        {
                            Column {
                                OhButton(
                                    text = stringResource(R.string.review_shown),
                                    onClick = {},
                                    enabled = false,
                                    variant = OhButtonVariant.Ghost,
                                    size = OhButtonSize.ExtraSmall,
                                )
                            }
                        }
                    } else {
                        null
                    },
                )
            }
            item.explanation?.let {
                Spacer(Modifier.height(OhTheme.space.s5))
                androidx.compose.material3.Text(
                    text = it,
                    style = OhTheme.type.footnote,
                    color = OhTheme.colors.label2,
                )
            }
            Spacer(Modifier.height(OhTheme.space.s7))
            item.actions.forEach { action ->
                OhButton(
                    text = action.label,
                    onClick = {
                        open = null
                        onAction(item, action)
                    },
                    variant = if (action.action == ReviewAction.IGNORE) {
                        OhButtonVariant.Ghost
                    } else {
                        OhButtonVariant.Secondary
                    },
                    size = OhButtonSize.Default,
                )
                Spacer(Modifier.height(OhTheme.space.s3))
            }
        }
    }
}

private fun ReviewKind.icon() = when (this) {
    ReviewKind.DISAGREEMENT -> OhIcons.info
    ReviewKind.OUTSIDE_RANGE -> OhIcons.info
    ReviewKind.DUPLICATE -> OhIcons.edit
}
