package app.openhealth.android.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.openhealth.R
import app.openhealth.android.AppContainer
import app.openhealth.android.preferences.ThemeMode
import app.openhealth.ui.R as UiR
import app.openhealth.ui.components.OhTabItem
import app.openhealth.ui.components.OhToast
import app.openhealth.ui.icons.OhIcons
import app.openhealth.ui.screens.lock.LockScreen
import app.openhealth.ui.screens.onboarding.OnboardingScreen
import app.openhealth.ui.screens.quickadd.QuickAddRecent
import app.openhealth.ui.screens.quickadd.QuickAddSheet
import app.openhealth.ui.screens.roots.NutritionRoot
import app.openhealth.ui.screens.roots.StatsRoot
import app.openhealth.ui.screens.roots.TrainRoot
import app.openhealth.ui.screens.roots.VitalsRoot
import app.openhealth.ui.screens.settings.SettingsScreen
import app.openhealth.ui.screens.settings.TrashScreen
import app.openhealth.ui.screens.shell.OhRootShell
import app.openhealth.ui.screens.timeline.TimelineScreen
import app.openhealth.ui.screens.today.QuickAction
import app.openhealth.ui.screens.today.TodayScreen
import app.openhealth.ui.theme.OhTheme
import java.util.Locale
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The app tree: theme, lock, onboarding, the shell and the global layers. It is the
 * only place that knows about both the state holder and the navigation graph.
 */
@Composable
fun OpenHealthRoot(container: AppContainer) {
    val viewModel: AppViewModel = viewModel(factory = AppViewModelFactory(container))
    val state by viewModel.state.collectAsState()
    val dark = when (state.preferences.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    OhTheme(dark = dark, accent = state.preferences.accent) {
        SystemBars(dark = dark)
        Box(modifier = Modifier.fillMaxSize().background(OhTheme.colors.bg)) {
            when {
                !state.loaded -> Loading()
                state.locked -> {
                    val activity = LocalContext.current.findActivity()
                    LockScreen(
                        message = state.lockMessage,
                        onUnlock = { activity?.let(viewModel::unlock) },
                    )
                }
                !state.onboardingComplete -> OnboardingScreen(
                    step = state.onboarding.step,
                    answers = state.onboarding.answers,
                    issue = state.onboarding.issue,
                    ageBlocked = state.onboarding.ageBlocked,
                    weightLossAllowed = state.onboarding.weightLossAllowed,
                    onChange = viewModel::onOnboardingChange,
                    onBack = viewModel::onOnboardingBack,
                    onNext = viewModel::onOnboardingNext,
                    onSkip = viewModel::onOnboardingSkip,
                    onTryDemo = viewModel::tryDemo,
                )
                else -> AppShell(state, viewModel)
            }
            state.toast?.let { toast ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .padding(bottom = OhTheme.dims.toastLift),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    OhToast(
                        message = stringResource(toast.messageRes),
                        actionLabel = if (toast.undo) {
                            stringResource(UiR.string.action_undo)
                        } else {
                            null
                        },
                        onAction = if (toast.undo) viewModel::undoLast else null,
                        onDismiss = viewModel::dismissToast,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppShell(state: AppState, viewModel: AppViewModel) {
    val nav = rememberNavController()
    val date = state.today?.today
        ?: kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val goTab: (Int) -> Unit = { index ->
        nav.navigate(TAB_ROUTES[index]) {
            popUpTo(nav.graph.findStartDestination().id) { inclusive = true }
            launchSingleTop = true
        }
    }
    NavHost(navController = nav, startDestination = TodayRoute) {
        composable<TodayRoute> {
            RootFrame(
                index = 0,
                date = date,
                nav = nav,
                onSelectTab = goTab,
                onQuickAdd = { viewModel.openQuickAdd(null) },
                onSettings = { nav.navigate(SettingsRoute) },
            ) {
                val today = state.today
                if (today == null) {
                    Loading()
                } else {
                    TodayScreen(
                        model = today,
                        displayPolicy = state.preferences.displayPolicy,
                        onQuickAdd = viewModel::openQuickAdd,
                        onOpenTimeline = { nav.navigate(TimelineRoute) },
                        onOpenTrain = { goTab(2) },
                        onOpenNutrition = { viewModel.openQuickAdd(QuickAction.FOOD) },
                    )
                }
            }
        }
        composable<VitalsRoute> {
            RootFrame(
                index = 1,
                date = date,
                nav = nav,
                onSelectTab = goTab,
                onQuickAdd = { viewModel.openQuickAdd(null) },
            ) {
                VitalsRoot(onLogWeight = { viewModel.openQuickAdd(QuickAction.WEIGHT) })
            }
        }
        composable<TrainRoute> {
            RootFrame(
                index = 2,
                date = date,
                nav = nav,
                onSelectTab = goTab,
                onQuickAdd = { viewModel.openQuickAdd(null) },
            ) {
                TrainRoot(onAddNote = { viewModel.openQuickAdd(QuickAction.NOTE) })
            }
        }
        composable<NutritionRoute> {
            RootFrame(
                index = 3,
                date = date,
                nav = nav,
                onSelectTab = goTab,
                onQuickAdd = { viewModel.openQuickAdd(null) },
            ) {
                NutritionRoot(onLogWater = { viewModel.openQuickAdd(QuickAction.WATER) })
            }
        }
        composable<StatsRoute> {
            RootFrame(
                index = 4,
                date = date,
                nav = nav,
                onSelectTab = goTab,
                onQuickAdd = { viewModel.openQuickAdd(null) },
            ) {
                StatsRoot(onAddNote = { viewModel.openQuickAdd(QuickAction.NOTE) })
            }
        }
        composable<SettingsRoute> {
            val settings = state.settings
            if (settings != null) {
                SettingsScreen(
                    state = settings,
                    onBack = { nav.popBackStack() },
                    onSaveProfile = viewModel::saveProfile,
                    onToggleModule = viewModel::toggleModule,
                    onPreset = viewModel::setPreset,
                    onWeightNeutral = viewModel::setWeightNeutral,
                    onTheme = viewModel::setTheme,
                    onAccent = viewModel::setAccent,
                    onAppLock = viewModel::setAppLock,
                    onExport = viewModel::export,
                    onRestore = viewModel::restore,
                    onOpenTimeline = { nav.navigate(TimelineRoute) },
                    onOpenTrash = { nav.navigate(TrashRoute) },
                    onDeleteAllNotes = viewModel::deleteAllNotes,
                    onEmptyTrash = viewModel::emptyTrash,
                    onAddDemo = viewModel::addSampleData,
                )
            }
        }
        composable<TimelineRoute> {
            val timeline = state.timeline
            if (timeline != null) {
                TimelineScreen(
                    model = timeline,
                    onBack = { nav.popBackStack() },
                    onFilter = viewModel::setTimelineFilter,
                    onDelete = viewModel::deleteTimelineRow,
                )
            }
        }
        composable<TrashRoute> {
            TrashScreen(
                items = state.trash,
                onBack = { nav.popBackStack() },
                onRestore = viewModel::restoreTombstone,
                onPurge = viewModel::purgeTombstone,
            )
        }
    }
    QuickAddSheet(
        visible = state.quickAdd.visible,
        mode = state.quickAdd.mode,
        modules = state.preferences.modules,
        recent = recentItems(state),
        note = state.quickAdd.note,
        waterMl = state.quickAdd.waterMl,
        weight = state.quickAdd.weight,
        whenDate = state.quickAdd.whenDate,
        whenHour = state.quickAdd.whenHour,
        saving = false,
        onDismiss = viewModel::closeQuickAdd,
        onSelect = viewModel::openQuickAdd,
        onBack = viewModel::quickAddBack,
        onNoteChange = viewModel::onQuickAddNote,
        onWaterChange = viewModel::onQuickAddWater,
        onWeightChange = viewModel::onQuickAddWeight,
        onWhenDate = { viewModel.onQuickAddWhen(it, state.quickAdd.whenHour) },
        onWhenHour = { viewModel.onQuickAddWhen(state.quickAdd.whenDate, it) },
        onSave = viewModel::saveQuickAdd,
        onRepeat = viewModel::repeat,
    )
}

@Composable
private fun recentItems(state: AppState): List<QuickAddRecent> {
    val rows = state.timeline?.days?.flatMap { it.rows }.orEmpty()
    return rows
        .filter {
            it.kind == app.openhealth.ui.screens.timeline.TimelineKind.WATER ||
                it.kind == app.openhealth.ui.screens.timeline.TimelineKind.WEIGHT
        }
        .take(RECENT_LIMIT)
        .map { row ->
            QuickAddRecent(
                action = if (row.kind == app.openhealth.ui.screens.timeline.TimelineKind.WATER) {
                    QuickAction.WATER
                } else {
                    QuickAction.WEIGHT
                },
                title = row.title,
                detail = row.valueText.orEmpty(),
            )
        }
}

@Composable
private fun RootFrame(
    index: Int,
    date: LocalDate,
    nav: NavHostController,
    onSelectTab: (Int) -> Unit,
    onQuickAdd: () -> Unit,
    onSettings: () -> Unit = { nav.navigate(SettingsRoute) },
    content: @Composable () -> Unit,
) {
    OhRootShell(
        appName = "openHealth",
        dateLabel = dateLabel(date),
        quickAddLabel = stringResource(UiR.string.quick_title),
        settingsLabel = stringResource(UiR.string.settings_title),
        tabs = tabs(),
        selectedIndex = index,
        onSelect = onSelectTab,
        onQuickAdd = onQuickAdd,
        onSettings = onSettings,
    ) {
        content()
    }
}

@Composable
private fun tabs(): List<OhTabItem> = listOf(
    OhTabItem(stringResource(UiR.string.nav_today), OhIcons.today),
    OhTabItem(stringResource(UiR.string.nav_vitals), OhIcons.vitals),
    OhTabItem(stringResource(UiR.string.nav_train), OhIcons.train, raised = true),
    OhTabItem(stringResource(UiR.string.nav_nutrition), OhIcons.nutrition),
    OhTabItem(stringResource(UiR.string.nav_stats), OhIcons.stats),
)

private fun dateLabel(date: LocalDate): String =
    java.time.LocalDate.of(date.year, date.month.ordinal + MONTH_OFFSET, date.dayOfMonth)
        .format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))

@Composable
private fun Loading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(UiR.string.action_loading),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
    }
}

@Composable
fun StartupErrorScreen() {
    OhTheme(dark = true) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(OhTheme.colors.bg)
                .padding(OhTheme.space.s7),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.startup_error_body),
                style = OhTheme.type.footnote,
                color = OhTheme.colors.label2,
            )
        }
    }
}

@Composable
private fun SystemBars(dark: Boolean) {
    val view = LocalView.current
    val context = LocalContext.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = context.findActivity() ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(activity.window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

private const val MONTH_OFFSET = 1
private const val RECENT_LIMIT = 3

private val TAB_ROUTES = listOf(
    TodayRoute,
    VitalsRoute,
    TrainRoute,
    NutritionRoute,
    StatsRoute,
)
