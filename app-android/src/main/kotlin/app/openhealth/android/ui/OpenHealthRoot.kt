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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
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
import app.openhealth.android.nutrition.BarcodeCameraPreview
import app.openhealth.android.preferences.ThemeMode
import app.openhealth.core.preferences.HealthModule
import app.openhealth.ui.R as UiR
import app.openhealth.ui.components.OhTabItem
import app.openhealth.ui.components.OhToast
import app.openhealth.ui.icons.OhIcons
import app.openhealth.ui.screens.lock.LockScreen
import app.openhealth.ui.screens.nutrition.BarcodeScanScreen
import app.openhealth.ui.screens.nutrition.BarcodeState
import app.openhealth.ui.screens.nutrition.EntrySheet
import app.openhealth.ui.screens.nutrition.FastingSheet
import app.openhealth.ui.screens.nutrition.FoodEditorScreen
import app.openhealth.ui.screens.nutrition.FoodSearchSheet
import app.openhealth.ui.screens.nutrition.MicrosScreen
import app.openhealth.ui.screens.nutrition.NutritionRootScreen
import app.openhealth.ui.screens.nutrition.PortionSheet
import app.openhealth.ui.screens.nutrition.QuickCaloriesSheet
import app.openhealth.ui.screens.nutrition.TargetsSheet
import app.openhealth.ui.screens.nutrition.WaterSheet
import app.openhealth.ui.screens.onboarding.OnboardingScreen
import app.openhealth.ui.screens.quickadd.QuickAddSheet
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
                val model = state.nutrition
                if (model != null) {
                    NutritionRootScreen(
                        model = model,
                        onSelectDay = viewModel::selectNutritionDay,
                        onScan = {
                            viewModel.startBarcode()
                            nav.navigate(BarcodeRoute)
                        },
                        onTargets = viewModel::openTargets,
                        onMicros = {
                            viewModel.openMicros()
                            nav.navigate(MicrosRoute(model.selectedDate.toString()))
                        },
                        onAddFood = viewModel::openFoodSearch,
                        onOpenEntry = viewModel::openEntry,
                        onDeleteEntry = viewModel::deleteEntry,
                        onAddMeal = viewModel::addCustomMeal,
                        onWater = viewModel::openWater,
                        onLogWater = viewModel::logWaterContainer,
                        onFasting = viewModel::openFasting,
                        onCopyMeal = viewModel::copyMeal,
                        onSaveTemplate = viewModel::saveMealTemplate,
                        onCopyDay = viewModel::copyDay,
                        onOpenSettings = { nav.navigate(SettingsRoute) },
                        moduleOff = HealthModule.NUTRITION !in state.preferences.modules,
                    )
                }
            }
        }
        composable<BarcodeRoute> {
            LaunchedEffect(Unit) {
                if (state.barcode == null) viewModel.startBarcode()
            }
            BarcodeScanScreen(
                model = state.barcode ?: app.openhealth.ui.screens.nutrition.BarcodeModel(
                    state = BarcodeState.DISCLOSURE,
                    barcode = null,
                    product = null,
                    disclosureAccepted = false,
                ),
                showKcal = state.preferences.displayPolicy.shows(
                    app.openhealth.core.preferences.SensitiveMetric.CALORIES,
                ),
                cameraPreview = { onBarcode -> BarcodeCameraPreview(onBarcode = onBarcode) },
                onBack = {
                    viewModel.closeBarcode()
                    nav.popBackStack()
                },
                onAcceptDisclosure = viewModel::acceptBarcodeDisclosure,
                onScanAgain = viewModel::barcodeScanAgain,
                onManual = viewModel::barcodeManual,
                onLookup = viewModel::barcodeLookup,
                onLog = {
                    viewModel.logBarcodeProduct(it)
                    nav.popBackStack()
                },
                onCreateFood = { barcode ->
                    viewModel.closeBarcode()
                    nav.popBackStack()
                    nav.navigate(FoodEditorRoute(barcode = barcode))
                },
            )
        }
        composable<FoodEditorRoute> { entry ->
            val route = entry.toRoute<FoodEditorRoute>()
            LaunchedEffect(route.foodId, route.recipe, route.barcode) {
                if (state.foodEditor == null) {
                    if (route.recipe) {
                        viewModel.openRecipeEditor(route.foodId)
                    } else {
                        viewModel.openFoodEditor(route.foodId, route.barcode)
                    }
                }
            }
            val editor = state.foodEditor
            if (editor != null) {
                FoodEditorScreen(
                    model = editor,
                    showKcal = state.preferences.displayPolicy.shows(
                        app.openhealth.core.preferences.SensitiveMetric.CALORIES,
                    ),
                    onBack = {
                        viewModel.closeFoodEditor()
                        nav.popBackStack()
                    },
                    onSave = {
                        viewModel.saveFoodEditor(it)
                        nav.popBackStack()
                    },
                    onAddIngredient = viewModel::openIngredientSearch,
                    onIngredientGrams = viewModel::setIngredientGrams,
                    onRemoveIngredient = viewModel::removeIngredient,
                    onDuplicate = viewModel::duplicateRecipe,
                    onLog = { id ->
                        viewModel.openRecipePortion(id)
                        nav.popBackStack()
                    },
                )
            } else {
                Loading()
            }
        }
        composable<MicrosRoute> { entry ->
            val route = entry.toRoute<MicrosRoute>()
            LaunchedEffect(route.date) { viewModel.openMicros(LocalDate.parse(route.date)) }
            val micros = state.micros
            if (micros != null) {
                MicrosScreen(
                    model = micros,
                    onBack = {
                        viewModel.closeMicros()
                        nav.popBackStack()
                    },
                )
            } else {
                Loading()
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
                    onArchive = { routineId ->
                        viewModel.archiveRoutine(routineId)
                        nav.popBackStack()
                    },
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
                    onCreateCustom = viewModel::createCustomExercise,
                )
            }
        }
        composable<LiveSessionRoute> { entry ->
            val id = entry.toRoute<LiveSessionRoute>().sessionId
            AskNotificationPermission()
            LaunchedEffect(id) {
                if (id != null) viewModel.openLive(id) else viewModel.openOpenSession()
            }
            val live = state.live
            if (live == null) {
                if (state.liveMissing) {
                    SessionEnded(onBack = {
                        viewModel.closeLive()
                        nav.popBackStack()
                    })
                } else {
                    Loading()
                }
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
    val showKcal = state.preferences.displayPolicy.shows(
        app.openhealth.core.preferences.SensitiveMetric.CALORIES,
    )
    state.foodSearch?.let { search ->
        FoodSearchSheet(
            model = search,
            showKcal = showKcal,
            onDismiss = viewModel::closeNutritionSheets,
            onQuery = viewModel::searchFoods,
            onSelect = viewModel::selectFood,
            onTemplate = viewModel::logTemplate,
            onMeal = viewModel::selectFoodMeal,
            onScan = {
                viewModel.closeNutritionSheets()
                viewModel.startBarcode()
                nav.navigate(BarcodeRoute)
            },
            onCreateFood = {
                viewModel.closeNutritionSheets()
                viewModel.openFoodEditor(null)
                nav.navigate(FoodEditorRoute())
            },
            onCreateRecipe = {
                viewModel.closeNutritionSheets()
                viewModel.openRecipeEditor(null)
                nav.navigate(FoodEditorRoute(recipe = true))
            },
            onQuickCalories = viewModel::openQuickCalories,
        )
    }
    state.portion?.let { portion ->
        PortionSheet(
            model = portion,
            showKcal = showKcal,
            onDismiss = viewModel::closeNutritionSheets,
            onMeal = viewModel::selectFoodMeal,
            onLog = viewModel::logPortion,
            onEditRecipe = {
                val id = portion.food.id
                viewModel.closeNutritionSheets()
                viewModel.openRecipeEditor(id)
                nav.navigate(FoodEditorRoute(id, recipe = true))
            },
        )
    }
    state.entryDetail?.let { entry ->
        EntrySheet(
            model = entry,
            showKcal = showKcal,
            onDismiss = viewModel::closeNutritionSheets,
            onEdit = viewModel::editEntry,
            onDelete = { viewModel.deleteEntry(entry.id) },
        )
    }
    state.waterSheet?.let { water ->
        WaterSheet(
            model = water,
            onDismiss = viewModel::closeNutritionSheets,
            onLog = viewModel::logWater,
            onAdd = viewModel::addWaterContainer,
        )
    }
    state.targetsSheet?.let { targets ->
        TargetsSheet(
            model = targets,
            onDismiss = viewModel::closeNutritionSheets,
            onSave = viewModel::saveTargets,
        )
    }
    state.fastingSheet?.let { fasting ->
        FastingSheet(
            model = fasting,
            onDismiss = viewModel::closeNutritionSheets,
            onToggle = viewModel::setFastingEnabled,
            onHours = viewModel::setFastingHours,
            onStart = viewModel::startFast,
            onEnd = viewModel::endFast,
        )
    }
    if (state.quickCalories) {
        QuickCaloriesSheet(
            showKcal = showKcal,
            onDismiss = viewModel::closeNutritionSheets,
            onSave = viewModel::saveQuickCalories,
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

/** A restored live route with no open session: say so and leave, rather than spin. */
@Composable
private fun SessionEnded(onBack: () -> Unit) {
    app.openhealth.ui.screens.common.OhScreen(
        modifier = Modifier.windowInsetsPadding(
            androidx.compose.foundation.layout.WindowInsets.statusBars,
        ),
    ) {
        app.openhealth.ui.screens.common.OhSectionTitle(
            stringResource(UiR.string.train_no_open_title),
        )
        Text(
            text = stringResource(UiR.string.train_no_open_body),
            style = OhTheme.type.footnote,
            color = OhTheme.colors.label2,
        )
        app.openhealth.ui.components.OhButton(
            text = stringResource(UiR.string.train_no_open_action),
            onClick = onBack,
        )
    }
}

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
