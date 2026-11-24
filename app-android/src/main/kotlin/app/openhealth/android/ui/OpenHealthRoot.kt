package app.openhealth.android.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.openhealth.R
import app.openhealth.android.AppContainer
import app.openhealth.android.preferences.ThemeMode
import app.openhealth.core.preferences.HealthModule
import app.openhealth.ui.R as UiR
import app.openhealth.ui.components.OhTabItem
import app.openhealth.ui.components.OhToast
import app.openhealth.ui.icons.OhIcons
import app.openhealth.ui.screens.lock.LockScreen
import app.openhealth.ui.screens.onboarding.OnboardingScreen
import app.openhealth.ui.screens.quickadd.QuickAddSheet
import app.openhealth.ui.screens.roots.NutritionRoot
import app.openhealth.ui.screens.roots.StatsRoot
import app.openhealth.ui.screens.roots.VitalsRoot
import app.openhealth.ui.screens.settings.SettingsScreen
import app.openhealth.ui.screens.settings.TrashScreen
import app.openhealth.ui.screens.shell.OhRootShell
import app.openhealth.ui.screens.timeline.TimelineScreen
import app.openhealth.ui.screens.today.QuickAction
import app.openhealth.ui.screens.today.TodayScreen
import app.openhealth.ui.screens.train.ExerciseHistoryScreen
import app.openhealth.ui.screens.train.LiveSessionScreen
import app.openhealth.ui.screens.train.RoutineDetailScreen
import app.openhealth.ui.screens.train.RoutineEditorScreen
import app.openhealth.ui.screens.train.SessionSummaryScreen
import app.openhealth.ui.screens.train.TrainRootScreen
import app.openhealth.ui.theme.OhTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The app tree: theme, lock, onboarding, the shell and the global layers. It is the
 * only place that knows about both the state holder and the navigation graph.
 */
@Composable
fun OpenHealthRoot(container: AppContainer, injected: AppViewModel? = null) {
    val viewModel: AppViewModel = injected ?: viewModel(factory = AppViewModelFactory(container))
    val state by viewModel.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.onAppBackgrounded()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val dark = when (state.preferences.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    OhTheme(dark = dark, accent = state.preferences.accent) {
        SystemBars(dark = dark)
        Box(modifier = Modifier.fillMaxSize().background(OhTheme.colors.bg)) {
            when {
                state.fatal -> StartupErrorScreen(onRetry = viewModel::retry)
                !state.loaded -> Loading()
                state.locked -> {
                    val activity = LocalContext.current.findActivity()
                    LockScreen(
                        message = state.lockMessage,
                        onUnlock = { activity?.let(viewModel::unlock) },
                        onDisableLock = viewModel::disableAppLock,
                    )
                }
                !state.onboardingComplete -> OnboardingScreen(
                    step = state.onboarding.step,
                    answers = state.onboarding.answers,
                    issue = state.onboarding.issue,
                    ageBlocked = state.onboarding.ageBlocked,
                    weightLossAllowed = state.onboarding.weightLossAllowed,
                    saving = state.onboarding.saving,
                    onChange = viewModel::onOnboardingChange,
                    onBack = viewModel::onOnboardingBack,
                    onNext = viewModel::onOnboardingNext,
                    onSkip = viewModel::onOnboardingSkip,
                    onTryDemo = viewModel::tryDemo,
                )
                else -> AppShell(state, viewModel)
            }
            state.sessionUndo?.let { undo ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .padding(bottom = OhTheme.dims.screenBottomReserve),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    OhToast(
                        message = stringResource(undo.messageRes),
                        actionLabel = stringResource(UiR.string.action_undo),
                        onAction = viewModel::undoLast,
                        onDismiss = viewModel::dismissSessionUndo,
                    )
                }
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
                onDateClick = { nav.navigate(TimelineRoute) },
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
                // The Body module hides its surfaces, and weight-neutral hides the
                // value: both gates, exactly as Today applies them.
                val weightOffered = HealthModule.BODY in state.preferences.modules &&
                    state.preferences.displayPolicy.shows(
                        app.openhealth.core.preferences.SensitiveMetric.WEIGHT,
                    )
                VitalsRoot(
                    onLogWeight = if (weightOffered) {
                        { viewModel.openQuickAdd(QuickAction.WEIGHT) }
                    } else {
                        null
                    },
                    moduleOff = HealthModule.VITALS !in state.preferences.modules,
                    onOpenSettings = { nav.navigate(SettingsRoute) },
                )
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
                val train = state.train
                if (train == null) {
                    Loading()
                } else {
                    TrainRootScreen(
                        model = train,
                        onStartPlan = { routineId ->
                            viewModel.startSession(routineId)
                            nav.navigate(LiveSessionRoute())
                        },
                        onStartFreestyle = {
                            viewModel.startFreestyle()
                            nav.navigate(LiveSessionRoute())
                        },
                        onStartPast = { date ->
                            viewModel.startPastSession(date)
                            nav.navigate(LiveSessionRoute())
                        },
                        onResume = { id -> nav.navigate(LiveSessionRoute(id)) },
                        onOpenRoutine = { id -> nav.navigate(RoutineRoute(id)) },
                        onNewRoutine = { nav.navigate(RoutineEditorRoute(null)) },
                        onOpenSession = { id -> nav.navigate(SessionSummaryRoute(id)) },
                        onOpenSettings = { nav.navigate(SettingsRoute) },
                        moduleOff = HealthModule.TRAINING !in state.preferences.modules,
                    )
                }
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
                NutritionRoot(
                    onLogWater = if (HealthModule.NUTRITION in state.preferences.modules) {
                        { viewModel.openQuickAdd(QuickAction.WATER) }
                    } else {
                        null
                    },
                    moduleOff = HealthModule.NUTRITION !in state.preferences.modules,
                    onOpenSettings = { nav.navigate(SettingsRoute) },
                )
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
        composable<RoutineRoute> { entry ->
            val id = entry.toRoute<RoutineRoute>().id
            LaunchedEffect(id) { viewModel.openRoutine(id) }
            val routine = state.routine
            if (routine == null) {
                Loading()
            } else {
                RoutineDetailScreen(
                    model = routine,
                    onBack = {
                        viewModel.closeRoutine()
                        nav.popBackStack()
                    },
                    onStart = { routineId ->
                        viewModel.startSession(routineId)
                        nav.navigate(LiveSessionRoute())
                    },
                    onEdit = { routineId -> nav.navigate(RoutineEditorRoute(routineId)) },
                    onFavourite = { routineId, _ -> viewModel.toggleFavourite(routineId) },
                    onExercise = { exerciseId -> nav.navigate(ExerciseHistoryRoute(exerciseId)) },
                )
            }
        }
        composable<RoutineEditorRoute> { entry ->
            val id = entry.toRoute<RoutineEditorRoute>().id
            LaunchedEffect(id) { viewModel.openEditor(id) }
            val editor = state.editor
            if (editor == null) {
                Loading()
            } else {
                RoutineEditorScreen(
                    model = editor,
                    onBack = {
                        viewModel.closeEditor()
                        nav.popBackStack()
                    },
                    onSave = { draft ->
                        viewModel.saveRoutine(draft)
                        nav.popBackStack()
                    },
                )
            }
        }
        composable<LiveSessionRoute> { entry ->
            val id = entry.toRoute<LiveSessionRoute>().sessionId
            AskNotificationPermission()
            LaunchedEffect(id) { if (id != null) viewModel.openLive(id) }
            val live = state.live
            if (live == null) {
                Loading()
            } else {
                LiveSessionScreen(
                    model = live,
                    now = System.currentTimeMillis(),
                    onBack = {
                        viewModel.closeLive()
                        nav.popBackStack()
                    },
                    onFinish = { sessionId ->
                        viewModel.finishLive(sessionId)
                        nav.navigate(SessionSummaryRoute(sessionId)) {
                            popUpTo<LiveSessionRoute> { inclusive = true }
                        }
                    },
                    onLogSet = { logged -> viewModel.logSet(live.sessionId, logged) },
                    onUpdateSet = { setId, edit ->
                        viewModel.updateLiveSet(live.sessionId, setId, edit)
                    },
                    onDeleteSet = { setId -> viewModel.deleteLiveSet(live.sessionId, setId) },
                    onAddExercise = { exerciseId ->
                        viewModel.addLiveExercise(live.sessionId, exerciseId)
                    },
                    onExerciseNotes = { exerciseId, notes ->
                        viewModel.setLiveExerciseNotes(live.sessionId, exerciseId, notes)
                    },
                    onSessionNote = viewModel::setLiveNote,
                    onSuperset = { exerciseId, withNext ->
                        viewModel.superset(live.sessionId, exerciseId, withNext)
                    },
                    onSaveEquipment = viewModel::saveEquipment,
                    onRestMinus = viewModel::restMinus,
                    onRestPlus = viewModel::restPlus,
                    onRestPause = viewModel::restPause,
                    onRestSkip = viewModel::restSkip,
                )
            }
        }
        composable<SessionSummaryRoute> { entry ->
            val id = entry.toRoute<SessionSummaryRoute>().sessionId
            LaunchedEffect(id) { viewModel.openSummary(id) }
            val summary = state.summary
            if (summary == null) {
                Loading()
            } else {
                SessionSummaryScreen(
                    model = summary,
                    onBack = {
                        viewModel.closeSummary()
                        nav.popBackStack()
                    },
                    onEdit = { sessionId -> nav.navigate(LiveSessionRoute(sessionId)) },
                    onMove = { sessionId, date -> viewModel.moveSession(sessionId, date) },
                    onDelete = { sessionId ->
                        viewModel.deleteSession(sessionId)
                        nav.popBackStack()
                    },
                    onSaveNote = { feeling, note ->
                        viewModel.saveSummary(summary.sessionId, feeling, note)
                    },
                )
            }
        }
        composable<ExerciseHistoryRoute> { entry ->
            val id = entry.toRoute<ExerciseHistoryRoute>().exerciseId
            LaunchedEffect(id) { viewModel.openExerciseHistory(id) }
            val history = state.exerciseHistory
            if (history == null) {
                Loading()
            } else {
                ExerciseHistoryScreen(
                    model = history,
                    onBack = {
                        viewModel.closeExerciseHistory()
                        nav.popBackStack()
                    },
                )
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
                    onExternalActivity = viewModel::suppressLockForExternalActivity,
                    onDeleteAllNotes = viewModel::deleteAllNotes,
                    onEmptyTrash = viewModel::emptyTrash,
                    onUndoRestore = viewModel::undoLastRestore,
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
                    onExclude = viewModel::excludeFromTrends,
                    onLogNote = { viewModel.openQuickAdd(QuickAction.NOTE) },
                    onLoadMore = viewModel::loadMoreTimeline,
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
        allowWeight = state.preferences.displayPolicy.shows(
            app.openhealth.core.preferences.SensitiveMetric.WEIGHT,
        ),
        recent = state.recent,
        note = state.quickAdd.note,
        waterMl = state.quickAdd.waterMl,
        weight = state.quickAdd.weight,
        whenDate = state.quickAdd.whenDate,
        whenHour = state.quickAdd.whenHour,
        saving = state.quickAdd.saving,
        canSave = state.quickAdd.canSave,
        invalid = state.quickAdd.invalid,
        weightUnit = stringResource(
            if (state.profile?.units == "imperial") {
                UiR.string.unit_pounds
            } else {
                UiR.string.unit_kg
            },
        ),
        onDismiss = viewModel::closeQuickAdd,
        onSelect = { action ->
            if (action == QuickAction.SET) {
                viewModel.closeQuickAdd()
                viewModel.startFreestyle()
                nav.navigate(LiveSessionRoute())
            } else {
                viewModel.openQuickAdd(action)
            }
        },
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
private fun RootFrame(
    index: Int,
    date: LocalDate,
    nav: NavHostController,
    onSelectTab: (Int) -> Unit,
    onQuickAdd: () -> Unit,
    onSettings: () -> Unit = { nav.navigate(SettingsRoute) },
    onDateClick: () -> Unit = { nav.navigate(TimelineRoute) },
    content: @Composable () -> Unit,
) {
    OhRootShell(
        appName = stringResource(R.string.app_name),
        dateLabel = app.openhealth.ui.screens.common.mediumDate(date),
        quickAddLabel = stringResource(UiR.string.quick_title),
        settingsLabel = stringResource(UiR.string.settings_title),
        tabs = tabs(),
        selectedIndex = index,
        onSelect = onSelectTab,
        onQuickAdd = onQuickAdd,
        onSettings = onSettings,
        onDateClick = onDateClick,
        dateActionLabel = stringResource(UiR.string.timeline_title),
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

/**
 * The startup error. It stands on its own (the activity shows it before the container
 * exists), so it brings its own theme, and that theme follows the system rather than
 * pinning dark over a light device (`DESIGN.md` §12).
 */
@Composable
fun StartupErrorScreen(onRetry: () -> Unit = {}) {
    OhTheme(dark = isSystemInDarkTheme()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(OhTheme.colors.bg)
                .padding(OhTheme.space.s7),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.startup_error_title),
                    style = OhTheme.type.title3,
                    color = OhTheme.colors.label,
                )
                Spacer(Modifier.height(OhTheme.space.s3))
                Text(
                    text = stringResource(R.string.startup_error_body),
                    style = OhTheme.type.footnote,
                    color = OhTheme.colors.label2,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Spacer(Modifier.height(OhTheme.space.s7))
                app.openhealth.ui.components.OhButton(
                    text = stringResource(R.string.startup_error_retry),
                    onClick = onRetry,
                )
            }
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

private val TAB_ROUTES = listOf(
    TodayRoute,
    VitalsRoute,
    TrainRoute,
    NutritionRoute,
    StatsRoute,
)

/**
 * `POST_NOTIFICATIONS` at the point of use (`Tech-Stack.md` §8): the rest timer's
 * ongoing notification is the only notification the session needs, and the prompt is
 * asked when the person first opens a live session, never at launch.
 */
@Composable
private fun AskNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {},
    )
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
