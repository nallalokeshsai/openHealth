package app.openhealth.ui.screens.train

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.openhealth.ui.R
import app.openhealth.ui.components.OhCard
import app.openhealth.ui.components.OhInsetGroup
import app.openhealth.ui.components.OhRow
import app.openhealth.ui.screens.common.OhEmptyState
import app.openhealth.ui.screens.common.OhPageHeader
import app.openhealth.ui.screens.common.OhScreen
import app.openhealth.ui.screens.common.OhSectionTitle
import app.openhealth.ui.screens.common.number
import app.openhealth.ui.theme.OhTheme
import app.openhealth.ui.theme.OhWeight

/** One exercise's history (`pr.md` §6.5): every session, its top set and its estimate. */
@Composable
fun ExerciseHistoryScreen(
    model: ExerciseHistoryModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OhScreen(modifier = modifier.windowInsetsPadding(WindowInsets.statusBars)) {
        OhPageHeader(title = model.name, onBack = onBack)
        Text(
            text = model.muscles,
            style = OhTheme.type.subhead,
            color = OhTheme.colors.label2,
        )
        Spacer(Modifier.height(OhTheme.space.s5))
        OhCard {
            Text(
                text = model.bestOneRepMax ?: stringResource(R.string.train_not_available),
                style = OhTheme.type.display.copy(fontWeight = OhWeight.semibold),
                color = OhTheme.colors.label,
            )
            Text(
                text = stringResource(R.string.train_history_best_e1rm),
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
            )
            Spacer(Modifier.height(OhTheme.space.s3))
            Text(
                text = stringResource(R.string.train_history_policy) + " · " +
                    progressionPolicyLabel(model.progressionKey, model.incrementKg),
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
            )
            Text(
                text = stringResource(R.string.train_history_next) + " · " +
                    listOfNotNull(
                        model.nextWeight?.let { number(it, 2) },
                        number(model.nextReps.toDouble()),
                    ).joinToString(" × "),
                style = OhTheme.type.footnote,
                color = OhTheme.colors.accInk,
            )
        }
        OhSectionTitle(stringResource(R.string.train_history))
        if (model.rows.isEmpty()) {
            OhCard {
                OhEmptyState(
                    title = stringResource(R.string.train_exercise_history_empty_title),
                    body = stringResource(R.string.train_exercise_history_empty_body),
                )
            }
        } else {
            OhInsetGroup {
                model.rows.forEachIndexed { index, row ->
                    OhRow(
                        title = app.openhealth.ui.screens.common.shortDate(row.date),
                        subtitle = row.topSet,
                        value = row.volume ?: stringResource(R.string.train_not_available),
                        showDivider = index > 0,
                    )
                }
            }
        }
        Spacer(Modifier.height(OhTheme.space.s7))
    }
}
