package app.openhealth.ui.screens.roots

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.openhealth.ui.R

/** The Vitals root before B7 fills it; only the weight log works today. */
@Composable
fun VitalsRoot(onLogWeight: () -> Unit, modifier: Modifier = Modifier) {
    RootStubScreen(
        title = stringResource(R.string.root_vitals),
        emptyTitle = stringResource(R.string.root_vitals_empty_title),
        emptyBody = stringResource(R.string.root_vitals_empty_body),
        actionLabel = stringResource(R.string.root_vitals_action),
        onAction = onLogWeight,
        modifier = modifier,
    )
}

@Composable
fun TrainRoot(onAddNote: () -> Unit, modifier: Modifier = Modifier) {
    RootStubScreen(
        title = stringResource(R.string.root_train),
        emptyTitle = stringResource(R.string.root_train_empty_title),
        emptyBody = stringResource(R.string.root_train_empty_body),
        actionLabel = stringResource(R.string.root_train_action),
        onAction = onAddNote,
        modifier = modifier,
    )
}

@Composable
fun NutritionRoot(onLogWater: () -> Unit, modifier: Modifier = Modifier) {
    RootStubScreen(
        title = stringResource(R.string.root_nutrition),
        emptyTitle = stringResource(R.string.root_nutrition_empty_title),
        emptyBody = stringResource(R.string.root_nutrition_empty_body),
        actionLabel = stringResource(R.string.root_nutrition_action),
        onAction = onLogWater,
        modifier = modifier,
    )
}

@Composable
fun StatsRoot(onAddNote: () -> Unit, modifier: Modifier = Modifier) {
    RootStubScreen(
        title = stringResource(R.string.root_stats),
        emptyTitle = stringResource(R.string.root_stats_empty_title),
        emptyBody = stringResource(R.string.root_stats_empty_body),
        actionLabel = stringResource(R.string.root_stats_action),
        onAction = onAddNote,
        modifier = modifier,
    )
}
