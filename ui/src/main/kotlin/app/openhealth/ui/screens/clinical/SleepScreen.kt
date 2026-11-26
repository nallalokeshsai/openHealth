package app.openhealth.ui.screens.clinical

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.openhealth.ui.R
import app.openhealth.ui.components.OhCard
import app.openhealth.ui.components.OhInsetGroup
import app.openhealth.ui.components.OhRow
import app.openhealth.ui.screens.common.OhEmptyState
import app.openhealth.ui.screens.common.OhPageHeader
import app.openhealth.ui.theme.OhTheme

/**
 * The sleep page (`pr.md` §6.10). Consistency is measured on the clock, not on the
 * calendar; debt is against the person's own need; stages are shown only when the source
 * reported them. Every chart has its table (`DESIGN.md` §8.6).
 */
@Composable
fun SleepScreen(
    model: SleepModel,
    onBack: () -> Unit,
    onRateQuality: (Int) -> Unit,
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
            if (model.empty) {
                OhCard {
                    OhEmptyState(
                        title = model.emptyTitle,
                        body = model.emptyBody,
                        glyph = app.openhealth.ui.icons.OhModuleIcons.sleep,
                    )
                }
                Spacer(Modifier.height(OhTheme.space.s4))
                RatingStrip(label = model.addQualityLabel, onRate = onRateQuality)
                return@Column
            }
            Text(
                text = model.accessible,
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
                modifier = Modifier
                    .padding(bottom = OhTheme.space.s4)
                    .semantics { contentDescription = model.accessible },
            )
            StatPair(
                firstLabel = model.averageLabel,
                firstValue = model.averageValue,
                secondLabel = model.debtLabel,
                secondValue = model.debtValue,
            )
            Text(
                text = model.debtNote,
                style = OhTheme.type.caption,
                color = OhTheme.colors.label2,
                modifier = Modifier.padding(top = OhTheme.space.s2, bottom = OhTheme.space.s5),
            )
            OhCard {
                Text(
                    text = stringResource(R.string.clinical_sleep_consistency),
                    style = OhTheme.type.title3,
                    color = OhTheme.colors.label,
                    modifier = Modifier.padding(OhTheme.space.s4),
                )
                OhInsetGroup {
                    OhRow(
                        title = model.consistencyBedLabel,
                        value = model.consistencyBedValue,
                        showDivider = true,
                    )
                    OhRow(
                        title = model.consistencyWakeLabel,
                        value = model.consistencyWakeValue,
                    )
                }
                Text(
                    text = model.consistencyNote,
                    style = OhTheme.type.caption,
                    color = OhTheme.colors.label2,
                    modifier = Modifier.padding(OhTheme.space.s4),
                )
            }
            Spacer(Modifier.height(OhTheme.space.s5))
            OhCard {
                Text(
                    text = stringResource(R.string.clinical_sleep_nights),
                    style = OhTheme.type.title3,
                    color = OhTheme.colors.label,
                    modifier = Modifier.padding(OhTheme.space.s4),
                )
                OhInsetGroup {
                    model.nights.forEachIndexed { index, night ->
                        OhRow(
                            title = night.date,
                            subtitle = listOfNotNull(
                                stringResource(
                                    R.string.clinical_sleep_window,
                                    night.bedtime,
                                    night.wake,
                                ),
                                night.quality.takeIf { it.isNotEmpty() }?.let { quality ->
                                    stringResource(
                                        R.string.clinical_sleep_quality_of_five,
                                        quality,
                                    )
                                },
                            ).joinToString(" · "),
                            value = night.asleep,
                            showDivider = index != model.nights.lastIndex,
                        )
                    }
                }
                Text(
                    text = model.qualityLabel,
                    style = OhTheme.type.caption,
                    color = OhTheme.colors.label2,
                    modifier = Modifier.padding(OhTheme.space.s4),
                )
            }
            if (model.stages.isNotEmpty()) {
                Spacer(Modifier.height(OhTheme.space.s5))
                OhCard {
                    Text(
                        text = stringResource(R.string.clinical_sleep_stages),
                        style = OhTheme.type.title3,
                        color = OhTheme.colors.label,
                        modifier = Modifier.padding(OhTheme.space.s4),
                    )
                    OhInsetGroup {
                        model.stages.forEachIndexed { index, stage ->
                            OhRow(
                                title = stage.label,
                                value = stage.duration,
                                subtitle = stage.share,
                                showDivider = index != model.stages.lastIndex,
                            )
                        }
                    }
                    Text(
                        text = model.stagesNote,
                        style = OhTheme.type.caption,
                        color = OhTheme.colors.label2,
                        modifier = Modifier.padding(OhTheme.space.s4),
                    )
                }
            }
            Spacer(Modifier.height(OhTheme.space.s5))
            Text(
                text = model.reminder,
                style = OhTheme.type.caption,
                color = OhTheme.colors.label2,
                modifier = Modifier.padding(bottom = OhTheme.space.s4),
            )
            RatingStrip(label = model.addQualityLabel, onRate = onRateQuality)
        }
    }
}

@Composable
private fun RatingStrip(label: String, onRate: (Int) -> Unit) {
    Text(
        text = label,
        style = OhTheme.type.footnote,
        color = OhTheme.colors.label2,
        modifier = Modifier.padding(bottom = OhTheme.space.s2),
    )
    Row(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(
            OhTheme.space.s2,
        ),
    ) {
        for (rating in 1..RATINGS) {
            app.openhealth.ui.components.OhChip(
                text = rating.toString(),
                selected = false,
                onClick = { onRate(rating) },
            )
        }
    }
}

@Composable
private fun StatPair(
    firstLabel: String,
    firstValue: String,
    secondLabel: String,
    secondValue: String,
) {
    Row(verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = firstLabel,
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
            )
            Text(
                text = firstValue,
                style = OhTheme.type.display,
                color = OhTheme.colors.label,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = secondLabel,
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
            )
            Text(
                text = secondValue,
                style = OhTheme.type.display,
                color = OhTheme.colors.label,
            )
        }
    }
}

private const val RATINGS = 5
