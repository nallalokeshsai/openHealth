package app.openhealth.android.ui

import android.app.Activity
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.openhealth.R
import app.openhealth.android.AppContainer
import app.openhealth.android.body.BodyPresenter
import app.openhealth.android.body.PhotoCodec
import app.openhealth.android.nutrition.NutritionPresenter
import app.openhealth.android.security.LockError
import app.openhealth.android.training.TrainingPresenter
import app.openhealth.core.body.AdiposeInputs
import app.openhealth.core.body.AdiposeModel
import app.openhealth.core.body.BodySex
import app.openhealth.core.body.MuscleRegion
import app.openhealth.core.body.RecoveryMap
import app.openhealth.core.formula.RecoveryThresholds
import app.openhealth.core.ids.EntryId
import app.openhealth.core.ids.NoteId
import app.openhealth.core.ids.ObservationId
import app.openhealth.core.ids.SourceId
import app.openhealth.core.ids.TombstoneId
import app.openhealth.core.observation.MeasurementMethod
import app.openhealth.core.observation.Observation
import app.openhealth.core.observation.ObservationContext
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.observation.Provenance
import app.openhealth.core.observation.SourceKind
import app.openhealth.core.preferences.AgeRules
import app.openhealth.core.preferences.HealthModule
import app.openhealth.core.preferences.ProfileGoal
import app.openhealth.core.preferences.UnitSystem
import app.openhealth.core.seam.UnsupportedExportFormat
import app.openhealth.core.seam.WrongPassphrase
import app.openhealth.core.time.TimeProvenance
import app.openhealth.core.units.Measure
import app.openhealth.core.units.UcumUnit
import app.openhealth.data.db.Measurement
import app.openhealth.data.db.Note
import app.openhealth.data.db.Profile
import app.openhealth.data.repo.SettingKeys
import app.openhealth.data.repo.TrashEntity
import app.openhealth.ui.R as UiR
import app.openhealth.ui.screens.lock.LockMessage
import app.openhealth.ui.screens.nutrition.BarcodeModel
import app.openhealth.ui.screens.nutrition.BarcodeState
import app.openhealth.ui.screens.nutrition.EntryDetailModel
import app.openhealth.ui.screens.nutrition.FastingModel
import app.openhealth.ui.screens.nutrition.FoodChoiceModel
import app.openhealth.ui.screens.nutrition.FoodEditorModel
import app.openhealth.ui.screens.nutrition.FoodSaveModel
import app.openhealth.ui.screens.nutrition.FoodSearchModel
import app.openhealth.ui.screens.nutrition.IngredientModel
import app.openhealth.ui.screens.nutrition.MealChoiceModel
import app.openhealth.ui.screens.nutrition.MealSectionModel
import app.openhealth.ui.screens.nutrition.MealSlot
import app.openhealth.ui.screens.nutrition.MicrosModel
import app.openhealth.ui.screens.nutrition.NutritionModel
import app.openhealth.ui.screens.nutrition.PortionModel
import app.openhealth.ui.screens.nutrition.TargetsModel
import app.openhealth.ui.screens.nutrition.TargetsSaveModel
import app.openhealth.ui.screens.nutrition.WaterSheetModel
import app.openhealth.ui.screens.onboarding.OnboardingAnswers
import app.openhealth.ui.screens.onboarding.OnboardingIssue
import app.openhealth.ui.screens.onboarding.OnboardingStep
import app.openhealth.ui.screens.quickadd.QuickAddMode
import app.openhealth.ui.screens.quickadd.QuickAddRecent
import app.openhealth.ui.screens.settings.ProfileEdit
import app.openhealth.ui.screens.settings.SettingsCounts
import app.openhealth.ui.screens.settings.SettingsMessage
import app.openhealth.ui.screens.settings.SettingsState
import app.openhealth.ui.screens.settings.ThemeChoice
import app.openhealth.ui.screens.settings.TrashItemModel
import app.openhealth.ui.screens.settings.TrashKind
import app.openhealth.ui.screens.timeline.RowProvenance
import app.openhealth.ui.screens.timeline.TimelineDayModel
import app.openhealth.ui.screens.timeline.TimelineFilter
import app.openhealth.ui.screens.timeline.TimelineKind
import app.openhealth.ui.screens.timeline.TimelineModel
import app.openhealth.ui.screens.timeline.TimelineRowModel
import app.openhealth.ui.screens.today.QuickAction
import app.openhealth.ui.screens.today.TodayModel
import app.openhealth.ui.screens.train.ExerciseHistoryModel
import app.openhealth.ui.screens.train.LiveSessionModel
import app.openhealth.ui.screens.train.LiveSetEdit
import app.openhealth.ui.screens.train.RoutineDetailModel
import app.openhealth.ui.screens.train.RoutineDraft
import app.openhealth.ui.screens.train.RoutineEditorModel
import app.openhealth.ui.screens.train.SessionSummaryModel
import app.openhealth.ui.screens.train.TrainModel
import app.openhealth.ui.screens.vitals.PhotoCompareModel
import app.openhealth.ui.screens.vitals.VitalDetailModel
import app.openhealth.ui.screens.vitals.VitalDraft
import app.openhealth.ui.screens.vitals.VitalsModel
import app.openhealth.ui.theme.OhAccent
import java.util.Locale
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

private const val BLUETOOTH_SCAN_MILLIS = 20_000L

/** The onboarding surface state: the current question, what was typed, and why it failed. */
data class OnboardingUi(
    val step: OnboardingStep = OnboardingStep.Welcome,
    val answers: OnboardingAnswers,
    val issue: OnboardingIssue? = null,
    val ageBlocked: Boolean = false,
    val weightLossAllowed: Boolean = true,
    val tier: Int = 1,
    val saving: Boolean = false,
)

/** The one quick-add sheet's state. */
data class QuickAddUi(
    val visible: Boolean = false,
    val mode: QuickAddMode = QuickAddMode.TILES,
    val note: String = "",
    val waterMl: Int = 250,
    val weight: String = "",
    val whenDate: LocalDate,
    val whenHour: Int,
    val saving: Boolean = false,
    val invalid: Boolean = false,
) {
    val canSave: Boolean
        get() = !saving &&
            when (mode) {
                QuickAddMode.NOTE -> note.isNotBlank()
                QuickAddMode.WATER -> waterMl > 0
                QuickAddMode.WEIGHT -> weight.replace(',', '.').toDoubleOrNull()?.let { it > 0 } ==
                    true
                else -> false
            }
}

data class AppToast(
    val messageRes: Int,
    val undo: Boolean,
    val argument: Int? = null,
    val second: Int? = null,
)

/** One undo slot for the session; the tombstone in Trash is the durable promise. */
private sealed interface UndoAction {
    data class Added(val entity: TrashEntity, val id: String) : UndoAction
    data class Deleted(val tombstone: TombstoneId) : UndoAction
    data class DeletedMany(val tombstones: List<TombstoneId>) : UndoAction
}

data class AppState(
    val loaded: Boolean = false,
    val onboardingComplete: Boolean = false,
    val preferences: app.openhealth.android.preferences.Preferences,
    val profile: Profile? = null,
    val today: TodayModel? = null,
    val settings: SettingsState? = null,
    val timeline: TimelineModel? = null,
    val trash: List<TrashItemModel> = emptyList(),
    val onboarding: OnboardingUi,
    val quickAdd: QuickAddUi,
    val locked: Boolean = false,
    val lockMessage: LockMessage? = null,
    val toast: AppToast? = null,
    val fatal: Boolean = false,
    val sessionUndo: AppToast? = null,
    val recent: List<QuickAddRecent> = emptyList(),
    val train: TrainModel? = null,
    val routine: RoutineDetailModel? = null,
    val editor: RoutineEditorModel? = null,
    val live: LiveSessionModel? = null,
    val liveMissing: Boolean = false,
    val summary: SessionSummaryModel? = null,
    val exerciseHistory: ExerciseHistoryModel? = null,
    val nutrition: NutritionModel? = null,
    val nutritionDate: LocalDate? = null,
    val vitals: VitalsModel? = null,
    val vitalDetail: VitalDetailModel? = null,
    val vitalType: ObservationType? = null,
    val vitalFilter: ObservationContext? = null,
    val devices: app.openhealth.ui.screens.devices.DevicesModel? = null,
    val stats: app.openhealth.ui.screens.stats.StatsModel? = null,
    val statsSegment: app.openhealth.ui.screens.stats.StatsSegment =
        app.openhealth.ui.screens.stats.StatsSegment.OVERVIEW,
    val foundDevices: List<app.openhealth.ui.screens.devices.FoundDeviceModel> = emptyList(),
    val pendingPermission: app.openhealth.ui.screens.devices.DevicePermissionRequest? = null,
    val photoCompare: PhotoCompareModel? = null,
    val foodSearch: FoodSearchModel? = null,
    val portion: PortionModel? = null,
    val entryDetail: EntryDetailModel? = null,
    val waterSheet: WaterSheetModel? = null,
    val targetsSheet: TargetsModel? = null,
    val fastingSheet: FastingModel? = null,
    val micros: MicrosModel? = null,
    val barcode: BarcodeModel? = null,
    val foodEditor: FoodEditorModel? = null,
    val ingredientMode: Boolean = false,
    val quickCalories: Boolean = false,
)

/**
 * The one state holder of the shell (`BUILD-ROADMAP.md` §B3). Every write goes through
 * the repositories and its rollup engine, every read rebuilds the screen models, and
 * the weight-neutral policy is read from the settings exactly once per rebuild so no
 * screen can bypass it.
 */
class AppViewModel(
    private val container: AppContainer,
    detectedUnits: UnitSystem = detectUnits(),
) : ViewModel() {
    private val store: app.openhealth.data.LocalStore get() = container.store
    private val zone: TimeZone = container.zone
    private val now: () -> Instant = { container.clock.now() }
    private val training: TrainingPresenter by lazy {
        TrainingPresenter(
            store = store,
            clock = container.clock,
            zone = zone,
            restTimer = container.restTimer,
            ids = container.ids,
            manualSource = AppContainer.MANUAL_SOURCE,
            appVersion = container.versionName,
            freestyleTitle = container.context.getString(R.string.train_freestyle),
            pastTitle = container.context.getString(R.string.train_past_workout),
        )
    }
    private var liveTicker: Job? = null
    private val body: BodyPresenter by lazy {
        BodyPresenter(
            context = container.context,
            store = store,
            clock = container.clock,
            zone = zone,
            ids = container.ids,
            manualSource = AppContainer.MANUAL_SOURCE,
            importSource = AppContainer.IMPORT_SOURCE,
            appVersion = container.versionName,
        )
    }
    private val activity: app.openhealth.android.activity.ActivityPresenter
        get() = container.activity
    private val todayPresenter: app.openhealth.android.today.TodayPresenter by lazy {
        app.openhealth.android.today.TodayPresenter(
            context = container.context,
            store = store,
            clock = container.clock,
            zone = zone,
        )
    }
    private val statsPresenter: app.openhealth.android.stats.StatsPresenter by lazy {
        app.openhealth.android.stats.StatsPresenter(
            context = container.context,
            store = store,
            clock = container.clock,
            zone = zone,
        )
    }
    private val nutritionPresenter: NutritionPresenter by lazy {
        NutritionPresenter(
            store = store,
            clock = container.clock,
            zone = zone,
            ids = container.ids,
            manualSource = AppContainer.MANUAL_SOURCE,
            appVersion = container.versionName,
            foodIndex = container.foodIndex,
            off = container.offClient,
        )
    }

    private val _state = MutableStateFlow(
        AppState(
            preferences = app.openhealth.android.preferences.Preferences(
                onboardingComplete = false,
                weightNeutral = false,
                modules = HealthModule.entries.toSet(),
                preset = app.openhealth.core.preferences.ModulePreset.EVERYTHING,
                theme = app.openhealth.android.preferences.ThemeMode.SYSTEM,
                accent = OhAccent.Brand,
                appLock = false,
                demo = false,
            ),
            onboarding = OnboardingUi(answers = OnboardingAnswers(units = detectedUnits)),
            quickAdd = newQuickAdd(container),
        ),
    )
    val state: StateFlow<AppState> = _state.asStateFlow()

    private var filter: TimelineFilter = TimelineFilter.ALL
    private var timelineLimit: Long = TIMELINE_PAGE
    private var message: SettingsMessage? = null
    private var busy = false
    private var undoAction: UndoAction? = null
    private var suppressNextLock = false
    private var repeating = false
    private var toastJob: Job? = null

    init {
        container.ble.onFound { found ->
            val mapped = found.map { device ->
                app.openhealth.ui.screens.devices.FoundDeviceModel(
                    address = device.address,
                    name = device.name ?: device.address,
                    kindLabel = kindLabel(device.kind),
                    kind = app.openhealth.core.activity.DeviceKindName.of(device.kind),
                )
            }
            _state.update { it.copy(foundDevices = mapped) }
        }
        container.ble.onState {
            _state.update { current ->
                current.copy(
                    devices = activity.model(found = current.foundDevices),
                )
            }
        }
        refresh()
    }

    private fun kindLabel(kind: app.openhealth.core.activity.DeviceKind): String = when (kind) {
        app.openhealth.core.activity.DeviceKind.HEART_RATE ->
            container.context.getString(UiR.string.devices_kind_heart_rate)

        app.openhealth.core.activity.DeviceKind.SCALE ->
            container.context.getString(UiR.string.devices_kind_scale)

        app.openhealth.core.activity.DeviceKind.BLOOD_PRESSURE ->
            container.context.getString(UiR.string.devices_kind_pressure)
    }

    fun refresh() {
        launchGuarded {
            runCatching { rebuild() }.onFailure { failure ->
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                _state.update { state -> state.copy(loaded = true, fatal = true) }
            }
        }
    }

    /** Stops the state holder's coroutines; tests call this before closing the store. */
    fun stop() {
        viewModelScope.cancel()
    }

    /**
     * Every write runs inside this guard: a storage or constraint failure becomes the
     * startup error state, never an uncaught coroutine exception that kills the app.
     */
    private fun launchGuarded(block: suspend () -> Unit): Job = viewModelScope.launch {
        runCatching { block() }.onFailure { failure ->
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            _state.update { it.copy(loaded = true, fatal = true) }
        }
    }

    fun retry() {
        _state.update { it.copy(fatal = false, loaded = false) }
        refresh()
    }

    @Suppress("LongMethod")
    private suspend fun rebuild() = withContext(Dispatchers.IO) {
        reconnectAndReconcile()
        val preferences = container.preferences.snapshot()
        val profile = store.profile.active()
        val complete = preferences.onboardingComplete && profile != null
        val today = profile?.let { loaded ->
            todayPresenter.model(preferences, loaded)
                .copy(bodyMap = buildBodyMap(preferences, loaded))
        }
        val settings = buildSettings(preferences, profile)
        val devices = activity.model(
            stepsModuleOn = app.openhealth.core.preferences.HealthModule.STEPS in
                preferences.modules,
            found = _state.value.foundDevices,
        )
        val timeline = buildTimeline(preferences)
        val trash = buildTrash(preferences)
        val recent = buildRecent(preferences)
        _state.update { previous ->
            val lockOnFirstLoad = !previous.loaded &&
                complete &&
                preferences.appLock &&
                app.openhealth.android.security.BiometricGate().available(container.context)
            previous.copy(
                loaded = true,
                onboardingComplete = complete,
                preferences = preferences,
                profile = profile,
                today = today,
                settings = settings,
                devices = devices,
                timeline = timeline,
                trash = trash,
                recent = recent,
                locked = previous.locked || lockOnFirstLoad,
            )
        }
        // The Train root is the heaviest model; it lands in a second update so a quick
        // add's timeline or a settings change is never held behind the exercise library.
        val train = profile?.let { training.train(preferences, it) }
        _state.update { it.copy(train = train) }
        val vitals = profile?.let { body.model(it, preferences, preferences.demo) }
        _state.update { it.copy(vitals = vitals) }
        refreshStats(preferences, profile)
        val openVital = _state.value.vitalType
        if (openVital != null) {
            val detail = body.detail(
                openVital,
                _state.value.vitalFilter,
                profile?.units == "imperial",
            )
            _state.update { it.copy(vitalDetail = detail) }
        }
        val stepsModuleOn = app.openhealth.core.preferences.HealthModule.STEPS in
            preferences.modules
        if (!stepsModuleOn) {
            // A module is hidden, never deleted: the data stays, the service stops.
            app.openhealth.android.activity.StepTrackingService.ensureStopped(container.context)
        } else if (store.devices.stepTrackingEnabled() && container.activity.phoneSensorPresent) {
            // The counter keeps counting while nothing runs; starting the service makes
            // the next sensor event persist the interval (`pr.md` §6.6).
            app.openhealth.android.activity.StepTrackingService.ensureRunning(container.context)
        }
        // The diary is the third update: its own reads (seven days of entries, the
        // water rollup, the food index) never delay the shell or the Train root.
        val date = _state.value.nutritionDate ?: container.clock.now().toLocalDateTime(zone).date
        val nutritionModel = withContext(Dispatchers.IO) {
            nutritionPresenter.root(date, preferences)
        }
        _state.update { it.copy(nutrition = nutritionModel, nutritionDate = date) }
    }

    private suspend fun refreshStats(
        preferences: app.openhealth.android.preferences.Preferences,
        profile: app.openhealth.data.db.Profile?,
    ) {
        val stats = profile?.let {
            statsPresenter.model(_state.value.statsSegment, preferences)
        }
        _state.update { it.copy(stats = stats) }
    }

    // -------------------------------------------------------------------- training

    fun startSession(routineId: String?) {
        launchGuarded {
            val id = withContext(Dispatchers.IO) { training.startSession(routineId) }
            openLive(id)
        }
    }

    fun startFreestyle() = startSession(null)

    /** Today's Start: the plan's routine, or the Train root when there is no plan. */
    fun startTodayPlan(routineId: String?) {
        if (routineId == null) return
        startSession(routineId)
    }

    fun selectStatsSegment(segment: app.openhealth.ui.screens.stats.StatsSegment) {
        _state.update { it.copy(statsSegment = segment) }
        if (_state.value.profile == null) return
        launchGuarded {
            val model = withContext(Dispatchers.IO) {
                statsPresenter.model(segment, _state.value.preferences)
            }
            _state.update { it.copy(stats = model) }
        }
    }

    /**
     * The goal-ETA what-if's Save: the daily change becomes the goal rate the nutrition
     * targets use, through the same safety clamp (`pr.md` §6.4). Nothing else is rewritten.
     */
    fun saveGoalRate(dailyDeltaKcal: Double) {
        launchGuarded {
            val weekly = dailyDeltaKcal * DAYS_PER_WEEK / KCAL_PER_KG
            withContext(Dispatchers.IO) {
                store.settings.put(
                    SettingKeys.GOAL_RATE_KG_PER_WEEK,
                    kotlin.math.abs(weekly).toString(),
                )
            }
            when {
                weekly < -FLAT_RATE_KG_PER_WEEK -> profileGoal("lose_fat")
                weekly > FLAT_RATE_KG_PER_WEEK -> profileGoal("gain_muscle")
                else -> profileGoal("maintain")
            }
            showToast(R.string.toast_goal_saved, undo = false)
            rebuild()
        }
    }

    fun saveStepGoal(steps: Int) {
        launchGuarded {
            withContext(Dispatchers.IO) {
                store.settings.put(
                    SettingKeys.STEP_GOAL,
                    steps.coerceIn(MIN_STEP_GOAL, MAX_STEP_GOAL).toString(),
                )
            }
            showToast(R.string.toast_step_goal_saved, undo = false)
            rebuild()
        }
    }

    private fun profileGoal(goal: String) {
        val profile = store.profile.active() ?: return
        store.profile.upsert(profile.copy(goal = goal))
    }

    fun startPastSession(date: kotlinx.datetime.LocalDate) {
        launchGuarded {
            val id = withContext(Dispatchers.IO) { training.startPastSession(date) }
            openLive(id)
        }
    }

    fun openLive(sessionId: String) {
        liveTicker?.cancel()
        launchGuarded {
            val model = withContext(Dispatchers.IO) {
                state.value.profile?.let { training.live(sessionId, it) }
            }
            _state.update { it.copy(live = model, liveMissing = model == null) }
            if (model != null) container.restTimer.resume()
            liveTicker = viewModelScope.launch {
                while (true) {
                    delay(LIVE_TICK_MILLIS)
                    val rest = container.restTimer.snapshot(
                        container.clock.now().toEpochMilliseconds(),
                    )
                    _state.update { current ->
                        current.live?.let { live ->
                            current.copy(
                                live = live.copy(
                                    rest = rest?.let {
                                        app.openhealth.ui.screens.train.RestDisplay(
                                            it.remainingSeconds,
                                            it.totalSeconds,
                                            it.paused,
                                        )
                                    },
                                ),
                            )
                        } ?: current
                    }
                }
            }
        }
    }

    fun closeLive() {
        liveTicker?.cancel()
        liveTicker = null
        _state.update { it.copy(live = null) }
    }

    fun logSet(sessionId: String, entry: app.openhealth.ui.screens.train.LoggedSet) {
        launchGuarded {
            withContext(Dispatchers.IO) { training.logSet(sessionId, entry) }
            refreshLive(sessionId)
            rebuild()
        }
    }

    fun updateLiveSet(sessionId: String, setId: String, edit: LiveSetEdit) {
        launchGuarded {
            withContext(Dispatchers.IO) { training.updateSet(setId, edit) }
            refreshLive(sessionId)
        }
    }

    fun deleteLiveSet(sessionId: String, setId: String) {
        launchGuarded {
            val tombstone = withContext(Dispatchers.IO) { training.deleteSet(setId) }
            if (tombstone != null) {
                undoAction = UndoAction.Deleted(tombstone)
                _state.update {
                    it.copy(
                        sessionUndo = AppToast(R.string.toast_deleted, undo = true),
                        toast = null,
                    )
                }
            }
            refreshLive(sessionId)
        }
    }

    fun addLiveExercise(sessionId: String, exerciseId: String) {
        launchGuarded {
            withContext(Dispatchers.IO) { training.addExercise(sessionId, exerciseId) }
            refreshLive(sessionId)
        }
    }

    fun setLiveExerciseNotes(sessionId: String, exerciseId: String, notes: String) {
        launchGuarded {
            withContext(Dispatchers.IO) { training.setExerciseNotes(sessionId, exerciseId, notes) }
            refreshLive(sessionId)
        }
    }

    fun setLiveNote(note: String) {
        val id = state.value.live?.sessionId ?: return
        _state.update { it.copy(live = it.live?.copy(note = note)) }
        launchGuarded {
            withContext(Dispatchers.IO) { training.setSessionNote(id, note) }
        }
    }

    /** Resolves a restored live route whose session id was not carried in the back stack. */
    fun openOpenSession() {
        launchGuarded {
            val id = withContext(Dispatchers.IO) { store.training.openSession()?.id }
            if (id == null) {
                _state.update { it.copy(live = null, liveMissing = true) }
            } else {
                openLive(id)
            }
        }
    }

    fun superset(sessionId: String, exerciseId: String, withNext: Boolean) {
        launchGuarded {
            withContext(Dispatchers.IO) { training.superset(sessionId, exerciseId, withNext) }
            refreshLive(sessionId)
        }
    }

    fun finishLive(sessionId: String) {
        val live = state.value.live
        launchGuarded {
            withContext(Dispatchers.IO) {
                live?.let { training.saveSummary(sessionId, it.feeling, it.note) }
                training.finishSession(sessionId)
            }
            liveTicker?.cancel()
            liveTicker = null
            val summary = withContext(Dispatchers.IO) {
                state.value.profile?.let { training.summary(sessionId, it) }
            }
            _state.update { it.copy(live = null, summary = summary) }
            rebuild()
        }
    }

    fun openSummary(sessionId: String) {
        launchGuarded {
            val summary = withContext(Dispatchers.IO) {
                state.value.profile?.let { training.summary(sessionId, it) }
            }
            _state.update { it.copy(summary = summary) }
        }
    }

    fun closeSummary() {
        _state.update { it.copy(summary = null) }
    }

    fun saveSummary(sessionId: String, feeling: String, note: String) {
        launchGuarded {
            withContext(Dispatchers.IO) { training.saveSummary(sessionId, feeling, note) }
            showToast(R.string.toast_saved, undo = false)
            openSummary(sessionId)
            rebuild()
        }
    }

    fun moveSession(sessionId: String, date: kotlinx.datetime.LocalDate) {
        launchGuarded {
            withContext(Dispatchers.IO) { training.moveSession(sessionId, date) }
            openSummary(sessionId)
            rebuild()
            showToast(R.string.toast_saved, undo = false)
        }
    }

    fun deleteSession(sessionId: String) {
        launchGuarded {
            val tombstone = withContext(Dispatchers.IO) { training.deleteSession(sessionId) }
            if (tombstone != null) {
                undoAction = UndoAction.Deleted(tombstone)
                _state.update {
                    it.copy(
                        summary = null,
                        sessionUndo = AppToast(R.string.toast_deleted, undo = true),
                        toast = null,
                    )
                }
                rebuild()
            }
        }
    }

    fun openRoutine(id: String) {
        launchGuarded {
            val routine = withContext(Dispatchers.IO) {
                state.value.profile?.let { training.routine(id, it) }
            }
            _state.update { it.copy(routine = routine) }
        }
    }

    fun closeRoutine() {
        _state.update { it.copy(routine = null) }
    }

    fun openEditor(id: String?) {
        launchGuarded {
            val editor = withContext(Dispatchers.IO) {
                training.editor(id, state.value.profile)
            }
            _state.update { it.copy(editor = editor) }
        }
    }

    fun closeEditor() {
        _state.update { it.copy(editor = null) }
    }

    fun saveRoutine(draft: RoutineDraft) {
        launchGuarded {
            val id = withContext(Dispatchers.IO) { training.saveRoutine(draft) }
            _state.update { it.copy(editor = null) }
            openRoutine(id)
            rebuild()
        }
    }

    fun archiveRoutine(id: String) {
        launchGuarded {
            withContext(Dispatchers.IO) { training.archiveRoutine(id) }
            _state.update { it.copy(routine = null) }
            rebuild()
        }
    }

    fun toggleFavourite(id: String) {
        launchGuarded {
            withContext(Dispatchers.IO) { training.toggleFavourite(id) }
            openRoutine(id)
            rebuild()
        }
    }

    fun openExerciseHistory(id: String) {
        launchGuarded {
            val history = withContext(Dispatchers.IO) {
                state.value.profile?.let { training.exerciseHistory(id, it) }
            }
            _state.update { it.copy(exerciseHistory = history) }
        }
    }

    fun closeExerciseHistory() {
        _state.update { it.copy(exerciseHistory = null) }
    }

    fun createCustomExercise(draft: app.openhealth.ui.screens.train.CustomExerciseDraft) {
        launchGuarded {
            val choice = withContext(Dispatchers.IO) { training.createCustomExercise(draft) }
            _state.update { current ->
                val editor = current.editor ?: return@update current
                current.copy(editor = editor.copy(library = editor.library + choice))
            }
        }
    }

    fun saveEquipment(profile: app.openhealth.core.training.EquipmentProfile) {
        launchGuarded {
            withContext(Dispatchers.IO) { training.saveEquipment(profile) }
            refreshLive(state.value.live?.sessionId)
        }
    }

    fun restMinus() {
        training.restMinus()
    }

    fun restPlus() {
        training.restPlus()
    }

    fun restPause() {
        training.restPause()
    }

    fun restSkip() {
        training.restSkip()
    }

    private suspend fun refreshLive(sessionId: String?) {
        val id = sessionId ?: return
        val model = withContext(Dispatchers.IO) {
            state.value.profile?.let { training.live(id, it) }
        }
        _state.update { it.copy(live = model) }
    }

    // ----------------------------------------------------------------- nutrition

    fun selectNutritionDay(date: LocalDate) {
        launchGuarded {
            val preferences = state.value.preferences
            val model = withContext(Dispatchers.IO) { nutritionPresenter.root(date, preferences) }
            _state.update { it.copy(nutritionDate = date, nutrition = model) }
        }
    }

    fun openFoodSearch(section: MealSectionModel) {
        val date = state.value.nutritionDate
            ?: container.clock.now().toLocalDateTime(zone).date
        val meal = if (section.mealId != null && section.slot == MealSlot.CUSTOM) {
            MealChoiceModel(section.mealId, MealSlot.CUSTOM, section.name.orEmpty())
        } else {
            MealChoiceModel(null, section.slot, "")
        }
        launchGuarded {
            val model = withContext(Dispatchers.IO) {
                nutritionPresenter.search("", meal, date)
            }
            _state.update { it.copy(foodSearch = model, nutritionDate = date) }
        }
    }

    fun openFoodSearchDefault() {
        val hour = container.clock.now().toLocalDateTime(zone).hour
        val slot = mealForHour(hour)
        val date = container.clock.now().toLocalDateTime(zone).date
        launchGuarded {
            val model = withContext(Dispatchers.IO) {
                nutritionPresenter.search("", MealChoiceModel(null, slot, ""), date)
            }
            _state.update { it.copy(foodSearch = model, nutritionDate = date) }
        }
    }

    fun searchFoods(query: String) {
        val current = state.value.foodSearch ?: return
        // Mark the field's query immediately so a slower earlier result can be told apart
        // from the one the person is now looking at.
        _state.update { it.copy(foodSearch = current.copy(query = query)) }
        launchGuarded {
            val model = withContext(Dispatchers.IO) {
                nutritionPresenter.search(query, current.meal, current.date)
            }
            // A slower earlier query must not overwrite a newer one: only accept a
            // result whose query is still the one in the field.
            _state.update {
                if (it.foodSearch?.query == query) it.copy(foodSearch = model) else it
            }
        }
    }

    fun selectFoodMeal(meal: MealChoiceModel) {
        val search = state.value.foodSearch
        if (search != null) {
            launchGuarded {
                val model = withContext(Dispatchers.IO) {
                    nutritionPresenter.search(search.query, meal, search.date)
                }
                _state.update { it.copy(foodSearch = model) }
            }
            return
        }
        val portion = state.value.portion
        if (portion != null) {
            _state.update { it.copy(portion = portion.copy(meal = meal)) }
        }
    }

    fun selectFood(food: FoodChoiceModel) {
        val search = state.value.foodSearch ?: return
        if (state.value.ingredientMode) {
            val editor = state.value.foodEditor
            if (editor != null) {
                val ingredient = IngredientModel(
                    foodId = food.id,
                    name = food.name,
                    grams = 100.0,
                    per100g = food.per100g,
                )
                _state.update {
                    it.copy(
                        foodSearch = null,
                        ingredientMode = false,
                        foodEditor = editor.copy(ingredients = editor.ingredients + ingredient),
                    )
                }
            }
            return
        }
        launchGuarded {
            val model = withContext(Dispatchers.IO) {
                nutritionPresenter.portion(food, search.meal, search.date)
            }
            _state.update { it.copy(foodSearch = null, portion = model) }
        }
    }

    fun logPortion(grams: Double, servings: Double, servingLabel: String?) {
        val portion = state.value.portion ?: return
        launchGuarded {
            withContext(Dispatchers.IO) {
                nutritionPresenter.logFood(
                    food = portion.food,
                    meal = portion.meal,
                    date = portion.date,
                    grams = grams,
                    servings = servings,
                    servingLabel = servingLabel,
                    at = nutritionPresenter.atFor(portion.date, portion.meal.slot),
                    entryId = portion.entryId,
                )
            }
            _state.update { it.copy(portion = null, entryDetail = null, foodSearch = null) }
            refreshNutritionModel()
            showToast(UiR.string.nutrition_logged, undo = false)
        }
    }

    fun openEntry(entryId: String) {
        launchGuarded {
            val model = withContext(Dispatchers.IO) { nutritionPresenter.entryDetail(entryId) }
            _state.update { it.copy(entryDetail = model) }
        }
    }

    fun editEntry() {
        val entry = state.value.entryDetail ?: return
        launchGuarded {
            val portion = withContext(Dispatchers.IO) {
                nutritionPresenter.portionForEntry(entry.id)
            }
            _state.update { it.copy(entryDetail = null, portion = portion) }
        }
    }

    fun deleteEntry(entryId: String) {
        launchGuarded {
            val tombstone = withContext(Dispatchers.IO) {
                nutritionPresenter.deleteEntry(entryId)
            }
            _state.update { it.copy(entryDetail = null, portion = null) }
            if (tombstone != null) {
                undoAction = UndoAction.Deleted(tombstone)
                showToast(UiR.string.nutrition_entry_deleted, undo = true)
            }
            refreshNutritionModel()
        }
    }

    fun openWater() {
        val date = state.value.nutritionDate
            ?: container.clock.now().toLocalDateTime(zone).date
        launchGuarded {
            val model = withContext(Dispatchers.IO) { nutritionPresenter.waterSheet(date) }
            _state.update { it.copy(waterSheet = model) }
        }
    }

    fun logWaterContainer(containerId: String) {
        val container = nutritionPresenter.containers().firstOrNull { it.id == containerId }
            ?: return
        logWater(containerId, container.millilitres)
    }

    /**
     * One tap from the water row or from the sheet (`DESIGN.md` §14.3). The sheet does not
     * have to be open — the root's one-tap chip is the common path — and the observation
     * is filed on the day being viewed, not on today, so backdating a glass works the way
     * backdating a meal does.
     */
    fun logWater(containerId: String, millilitres: Double) {
        val date = state.value.waterSheet?.date
            ?: state.value.nutritionDate
            ?: container.clock.now().toLocalDateTime(zone).date
        launchGuarded {
            withContext(Dispatchers.IO) {
                nutritionPresenter.selectContainer(containerId)
                nutritionPresenter.logWater(
                    millilitres,
                    nutritionPresenter.atFor(date, MealSlot.SNACKS),
                )
            }
            _state.update { it.copy(waterSheet = null) }
            refreshNutritionModel()
            showToast(UiR.string.nutrition_logged, undo = false)
        }
    }

    fun addWaterContainer(label: String, millilitres: Double) {
        val sheet = state.value.waterSheet ?: return
        launchGuarded {
            withContext(Dispatchers.IO) {
                nutritionPresenter.saveContainer(null, label, millilitres)
            }
            val model = withContext(Dispatchers.IO) {
                nutritionPresenter.waterSheet(sheet.date)
            }
            _state.update { it.copy(waterSheet = model) }
            refreshNutritionModel()
        }
    }

    fun openTargets() {
        val today = container.clock.now().toLocalDateTime(zone).date
        launchGuarded {
            val model = withContext(Dispatchers.IO) {
                nutritionPresenter.targets(today, state.value.preferences)
            }
            _state.update { it.copy(targetsSheet = model) }
        }
    }

    fun saveTargets(save: TargetsSaveModel) {
        val today = container.clock.now().toLocalDateTime(zone).date
        launchGuarded {
            withContext(Dispatchers.IO) { nutritionPresenter.saveTargets(today, save) }
            _state.update { it.copy(targetsSheet = null) }
            refreshNutritionModel()
            showToast(UiR.string.nutrition_targets_saved, undo = false)
        }
    }

    fun openFasting() {
        launchGuarded {
            val model = withContext(Dispatchers.IO) {
                nutritionPresenter.fasting()
            }
            _state.update { it.copy(fastingSheet = model) }
        }
    }

    fun setFastingEnabled(enabled: Boolean) {
        launchGuarded {
            withContext(Dispatchers.IO) { nutritionPresenter.setFastingEnabled(enabled) }
            refreshFasting()
            refreshNutritionModel()
        }
    }

    fun setFastingHours(hours: Double) {
        launchGuarded {
            withContext(Dispatchers.IO) { nutritionPresenter.setFastingHours(hours) }
            refreshFasting()
        }
    }

    fun startFast() {
        val sheet = state.value.fastingSheet ?: return
        launchGuarded {
            withContext(Dispatchers.IO) { nutritionPresenter.startFast(sheet.targetHours) }
            refreshFasting()
            refreshNutritionModel()
        }
    }

    fun endFast() {
        launchGuarded {
            withContext(Dispatchers.IO) { nutritionPresenter.endFast() }
            refreshFasting()
            refreshNutritionModel()
        }
    }

    fun openMicros() {
        val date = state.value.nutritionDate
            ?: container.clock.now().toLocalDateTime(zone).date
        launchGuarded {
            val model = withContext(Dispatchers.IO) { nutritionPresenter.micros(date) }
            _state.update { it.copy(micros = model) }
        }
    }

    fun openMicros(date: LocalDate) {
        launchGuarded {
            val model = withContext(Dispatchers.IO) { nutritionPresenter.micros(date) }
            _state.update { it.copy(micros = model) }
        }
    }

    fun closeMicros() {
        _state.update { it.copy(micros = null) }
    }

    fun startBarcode() {
        _state.update { it.copy(barcode = nutritionPresenter.barcodeStart()) }
    }

    fun acceptBarcodeDisclosure() {
        nutritionPresenter.acceptBarcodeDisclosure()
        _state.update {
            it.copy(
                barcode = it.barcode?.copy(
                    state = BarcodeState.SCANNING,
                    disclosureAccepted = true,
                ),
            )
        }
    }

    fun barcodeLookup(code: String) {
        val current = state.value.barcode ?: nutritionPresenter.barcodeStart()
        _state.update {
            it.copy(barcode = current.copy(state = BarcodeState.LOOKING_UP, barcode = code))
        }
        launchGuarded {
            val result = withContext(Dispatchers.IO) { nutritionPresenter.lookupBarcode(code) }
            _state.update { it.copy(barcode = result) }
        }
    }

    fun barcodeScanAgain() {
        _state.update {
            it.copy(
                barcode = it.barcode?.copy(
                    state = if (it.barcode.disclosureAccepted) {
                        BarcodeState.SCANNING
                    } else {
                        BarcodeState.DISCLOSURE
                    },
                    product = null,
                ),
            )
        }
    }

    fun barcodeManual() {
        _state.update {
            it.copy(
                barcode =
                it.barcode?.copy(state = BarcodeState.MANUAL) ?: nutritionPresenter.manualBarcode(),
            )
        }
    }

    fun barcodeCreateFood(barcode: String?) {
        _state.update { it.copy(barcode = null) }
        openFoodEditor(null, barcode)
    }

    fun logBarcodeProduct(food: FoodChoiceModel) {
        val date = container.clock.now().toLocalDateTime(zone).date
        val hour = container.clock.now().toLocalDateTime(zone).hour
        val slot = mealForHour(hour)
        launchGuarded {
            val model = withContext(Dispatchers.IO) {
                nutritionPresenter.portion(food, MealChoiceModel(null, slot, ""), date)
            }
            _state.update { it.copy(barcode = null, portion = model) }
        }
    }

    fun closeBarcode() {
        _state.update { it.copy(barcode = null) }
    }

    fun openFoodEditor(foodId: String?, barcode: String? = null) {
        launchGuarded {
            val editor = withContext(Dispatchers.IO) { nutritionPresenter.foodEditor(foodId) }
            _state.update {
                it.copy(
                    foodEditor = editor?.let { model ->
                        if (barcode != null) model.copy(barcode = barcode) else model
                    },
                    ingredientMode = false,
                )
            }
        }
    }

    fun openRecipeEditor(recipeId: String?) {
        launchGuarded {
            val editor = withContext(Dispatchers.IO) { nutritionPresenter.recipeEditor(recipeId) }
            _state.update { it.copy(foodEditor = editor, ingredientMode = false) }
        }
    }

    fun saveFoodEditor(save: FoodSaveModel) {
        val date = state.value.nutritionDate
            ?: container.clock.now().toLocalDateTime(zone).date
        launchGuarded {
            withContext(Dispatchers.IO) { nutritionPresenter.saveFood(save) }
            _state.update { it.copy(foodEditor = null, ingredientMode = false) }
            refreshNutritionModel()
            showToast(UiR.string.nutrition_saved, undo = false)
        }
    }

    fun openIngredientSearch() {
        val editor = state.value.foodEditor ?: return
        val date = state.value.nutritionDate
            ?: container.clock.now().toLocalDateTime(zone).date
        launchGuarded {
            val model = withContext(Dispatchers.IO) {
                nutritionPresenter.search(
                    "",
                    MealChoiceModel(null, MealSlot.BREAKFAST, ""),
                    date,
                )
            }
            _state.update {
                it.copy(foodSearch = model, ingredientMode = true, foodEditor = editor)
            }
        }
    }

    fun setIngredientGrams(index: Int, grams: Double) {
        val editor = state.value.foodEditor ?: return
        val ingredients = editor.ingredients.mapIndexed { i, ingredient ->
            if (i == index) ingredient.copy(grams = grams) else ingredient
        }
        _state.update { it.copy(foodEditor = editor.copy(ingredients = ingredients)) }
    }

    fun removeIngredient(index: Int) {
        val editor = state.value.foodEditor ?: return
        val ingredients = editor.ingredients.filterIndexed { i, _ -> i != index }
        _state.update { it.copy(foodEditor = editor.copy(ingredients = ingredients)) }
    }

    fun duplicateRecipe() {
        val editor = state.value.foodEditor ?: return
        val id = editor.id ?: return
        launchGuarded {
            val copyId = withContext(Dispatchers.IO) { nutritionPresenter.duplicateRecipe(id) }
            if (copyId != null) {
                val copy = withContext(Dispatchers.IO) {
                    nutritionPresenter.foodEditor(copyId)
                }
                _state.update { it.copy(foodEditor = copy) }
                refreshNutritionModel()
            }
        }
    }

    fun openRecipePortion(id: String) {
        val date = state.value.nutritionDate
            ?: container.clock.now().toLocalDateTime(zone).date
        launchGuarded {
            val food = withContext(Dispatchers.IO) { store.nutrition.foodById(id) }
            val editor = state.value.foodEditor
            _state.update { it.copy(foodEditor = null) }
            if (food != null) {
                val portion = withContext(Dispatchers.IO) {
                    nutritionPresenter.portion(
                        app.openhealth.ui.screens.nutrition.FoodChoiceModel(
                            id = food.id,
                            name = food.name,
                            brand = food.brand,
                            barcode = food.barcode,
                            sourceKey = food.sourceKind,
                            sourceLabel = food.sourceKind,
                            foodTypeKey = food.foodType.key,
                            per100g = food.per100g,
                            portions = food.portions,
                            defaultServingG = food.defaultServingG,
                            defaultServingLabel = food.defaultServingLabel,
                            allergens = food.allergens,
                            analysisTags = food.analysisTags,
                            ingredientsText = food.ingredientsText,
                            verified = food.verified,
                            isRecipe = food.isRecipe,
                            isBundled = false,
                        ),
                        MealChoiceModel(null, MealSlot.LUNCH, ""),
                        date,
                    )
                }
                _state.update { it.copy(portion = portion) }
            }
            if (editor == null) refreshNutritionModel()
        }
    }

    fun addCustomMeal(name: String) {
        val date = state.value.nutritionDate
            ?: container.clock.now().toLocalDateTime(zone).date
        launchGuarded {
            withContext(Dispatchers.IO) { nutritionPresenter.addCustomMeal(date, name) }
            refreshNutritionModel()
        }
    }

    fun copyMeal(section: MealSectionModel) {
        val from = state.value.nutritionDate
            ?: container.clock.now().toLocalDateTime(zone).date
        val to = container.clock.now().toLocalDateTime(zone).date
        if (from == to) return
        launchGuarded {
            withContext(Dispatchers.IO) {
                nutritionPresenter.copyMeal(
                    from = from,
                    slot = section.slot,
                    to = to,
                    sourceMealId = section.mealId,
                    sourceName = section.name,
                )
            }
            refreshNutritionModel()
            showToast(UiR.string.nutrition_logged, undo = false)
        }
    }

    fun copyDay() {
        val from = state.value.nutritionDate
            ?: container.clock.now().toLocalDateTime(zone).date
        val to = container.clock.now().toLocalDateTime(zone).date
        if (from == to) return
        launchGuarded {
            withContext(Dispatchers.IO) { nutritionPresenter.copyDay(from, to) }
            refreshNutritionModel()
            showToast(UiR.string.nutrition_logged, undo = false)
        }
    }

    fun saveMealTemplate(section: MealSectionModel, name: String) {
        val mealId = section.mealId ?: return
        if (name.isBlank()) return
        launchGuarded {
            withContext(Dispatchers.IO) {
                nutritionPresenter.saveMealAsTemplate(mealId, name)
            }
            showToast(UiR.string.nutrition_template_saved, undo = false)
        }
    }

    fun logTemplate(templateId: String) {
        val search = state.value.foodSearch ?: return
        launchGuarded {
            withContext(Dispatchers.IO) {
                nutritionPresenter.logTemplate(templateId, search.meal, search.date)
            }
            _state.update { it.copy(foodSearch = null) }
            refreshNutritionModel()
            showToast(UiR.string.nutrition_logged, undo = false)
        }
    }

    fun closeNutritionSheets() {
        _state.update {
            it.copy(
                foodSearch = null,
                portion = null,
                entryDetail = null,
                waterSheet = null,
                targetsSheet = null,
                fastingSheet = null,
                ingredientMode = false,
                quickCalories = false,
            )
        }
    }

    fun closeFoodEditor() {
        _state.update { it.copy(foodEditor = null, ingredientMode = false) }
    }

    fun openQuickCalories() {
        _state.update { it.copy(foodSearch = null, quickCalories = true) }
    }

    fun saveQuickCalories(
        kcal: Double,
        protein: Double,
        carbs: Double,
        fat: Double,
        fibre: Double,
    ) {
        val today = container.clock.now().toLocalDateTime(zone).date
        val hour = container.clock.now().toLocalDateTime(zone).hour
        val slot = mealForHour(hour)
        val date = state.value.nutritionDate ?: today
        launchGuarded {
            withContext(Dispatchers.IO) {
                nutritionPresenter.logQuick(
                    kcal = kcal,
                    protein = protein,
                    carbs = carbs,
                    fat = fat,
                    fibre = fibre,
                    meal = MealChoiceModel(null, slot, ""),
                    date = date,
                    at = nutritionPresenter.atFor(date, slot),
                )
            }
            _state.update { it.copy(quickCalories = false) }
            refreshNutritionModel()
            showToast(UiR.string.nutrition_logged, undo = false)
        }
    }

    private fun mealForHour(hour: Int): MealSlot = when {
        hour < BREAKFAST_UNTIL -> MealSlot.BREAKFAST
        hour < LUNCH_UNTIL -> MealSlot.LUNCH
        hour < SNACKS_UNTIL -> MealSlot.SNACKS
        hour < DINNER_UNTIL -> MealSlot.DINNER
        else -> MealSlot.SNACKS
    }

    private suspend fun refreshNutritionModel() {
        val date = state.value.nutritionDate
            ?: container.clock.now().toLocalDateTime(zone).date
        val preferences = state.value.preferences
        val model = withContext(Dispatchers.IO) { nutritionPresenter.root(date, preferences) }
        _state.update { it.copy(nutrition = model, nutritionDate = date) }
    }

    private suspend fun refreshFasting() {
        val model = withContext(Dispatchers.IO) {
            nutritionPresenter.fasting()
        }
        _state.update { it.copy(fastingSheet = model) }
    }

    // ---------------------------------------------------------------- onboarding

    fun onOnboardingChange(answers: OnboardingAnswers) {
        val previous = _state.value.onboarding.answers
        val converted = if (answers.units != previous.units) {
            convertMeasures(answers, previous.units, answers.units)
        } else {
            answers
        }
        _state.update {
            it.copy(onboarding = it.onboarding.copy(answers = converted, issue = null))
        }
    }

    private fun convertMeasures(
        answers: OnboardingAnswers,
        from: UnitSystem,
        to: UnitSystem,
    ): OnboardingAnswers = answers.copy(
        height = convertLength(answers.height, from, to),
        weight = convertMass(answers.weight, from, to),
        goalWeight = convertMass(answers.goalWeight, from, to),
        neck = convertLength(answers.neck, from, to),
        shoulders = convertLength(answers.shoulders, from, to),
        chest = convertLength(answers.chest, from, to),
        waist = convertLength(answers.waist, from, to),
        hips = convertLength(answers.hips, from, to),
        upperArm = convertLength(answers.upperArm, from, to),
        forearm = convertLength(answers.forearm, from, to),
        thigh = convertLength(answers.thigh, from, to),
        calf = convertLength(answers.calf, from, to),
        wrist = convertLength(answers.wrist, from, to),
        ankle = convertLength(answers.ankle, from, to),
    )

    private fun convertLength(text: String, from: UnitSystem, to: UnitSystem): String {
        val value = text.replace(',', '.').toDoubleOrNull() ?: return text
        val converted = when {
            from == UnitSystem.METRIC && to == UnitSystem.IMPERIAL ->
                Measure(value, UcumUnit.CENTIMETRE).convertTo(UcumUnit.INCH).value
            from == UnitSystem.IMPERIAL && to == UnitSystem.METRIC ->
                Measure(value, UcumUnit.INCH).convertTo(UcumUnit.CENTIMETRE).value
            else -> value
        }
        return format(converted)
    }

    private fun convertMass(text: String, from: UnitSystem, to: UnitSystem): String {
        val value = text.replace(',', '.').toDoubleOrNull() ?: return text
        val converted = when {
            from == UnitSystem.METRIC && to == UnitSystem.IMPERIAL ->
                Measure(value, UcumUnit.KILOGRAM).convertTo(UcumUnit.POUND).value
            from == UnitSystem.IMPERIAL && to == UnitSystem.METRIC ->
                Measure(value, UcumUnit.POUND).convertTo(UcumUnit.KILOGRAM).value
            else -> value
        }
        return format(converted)
    }

    fun onOnboardingNext() {
        val current = _state.value.onboarding
        if (current.saving || _state.value.onboardingComplete) return
        val validation = validate(current.step, current.answers)
        val allowed = if (current.step == OnboardingStep.BirthDate) {
            validation.weightLossAllowed
        } else {
            current.weightLossAllowed
        }
        when {
            validation.issue != null || validation.ageBlocked -> {
                _state.update {
                    it.copy(
                        onboarding = it.onboarding.copy(
                            issue = validation.issue,
                            ageBlocked = validation.ageBlocked,
                            weightLossAllowed = allowed,
                            answers = validation.answers,
                        ),
                    )
                }
            }
            current.step == OnboardingStep.Finish -> persistOnboarding(validation.answers)
            else -> {
                val next = nextStep(current.step, validation.answers)
                _state.update {
                    it.copy(
                        onboarding = it.onboarding.copy(
                            step = next,
                            answers = validation.answers,
                            issue = null,
                            ageBlocked = false,
                            weightLossAllowed = allowed,
                            tier = maxOf(it.onboarding.tier, next.tier),
                        ),
                    )
                }
            }
        }
    }

    fun onOnboardingSkip() {
        val current = _state.value.onboarding
        if (current.step.required) {
            _state.update {
                it.copy(onboarding = it.onboarding.copy(issue = OnboardingIssue.REQUIRED))
            }
            return
        }
        val next = when (current.step) {
            OnboardingStep.TierTwoIntro -> OnboardingStep.TierThreeIntro
            OnboardingStep.TierThreeIntro -> OnboardingStep.Finish
            else -> nextStep(current.step, current.answers)
        }
        _state.update {
            it.copy(
                onboarding = it.onboarding.copy(
                    step = next,
                    issue = null,
                    tier = maxOf(it.onboarding.tier, next.tier),
                ),
            )
        }
    }

    fun onOnboardingBack() {
        val current = _state.value.onboarding
        if (current.ageBlocked) {
            _state.update {
                it.copy(onboarding = it.onboarding.copy(ageBlocked = false, issue = null))
            }
            return
        }
        val previous = previousStep(current.step, current.answers)
        _state.update {
            it.copy(onboarding = it.onboarding.copy(step = previous, issue = null))
        }
    }

    fun tryDemo() {
        if (_state.value.onboarding.saving) return
        _state.update { it.copy(onboarding = it.onboarding.copy(saving = true)) }
        launchGuarded {
            runCatching {
                withContext(Dispatchers.IO) {
                    container.addSampleData(
                        days = AppContainer.DEMO_DAYS,
                        profileId = DEMO_PROFILE,
                        writeProfile = true,
                    )
                    container.preferences.setPreset(
                        app.openhealth.core.preferences.ModulePreset.EVERYTHING,
                    )
                    container.preferences.setModules(HealthModule.entries.toSet())
                    container.preferences.setWeightNeutral(false)
                    container.preferences.setDemo(true)
                    if (store.settings.get(SettingKeys.GOAL_WEIGHT_KG).isNullOrBlank()) {
                        val current = store.profile.active()?.weight_kg
                        store.settings.put(
                            SettingKeys.GOAL_WEIGHT_KG,
                            ((current ?: DEMO_WEIGHT_KG) - DEMO_GOAL_DROP_KG).toString(),
                        )
                    }
                    container.preferences.markOnboardingComplete(tier = 3)
                }
            }.onFailure {
                message = SettingsMessage.DEMO_FAILED
            }
            rebuild()
            _state.update { it.copy(onboarding = it.onboarding.copy(saving = false)) }
        }
    }

    private data class Validated(
        val answers: OnboardingAnswers,
        val issue: OnboardingIssue? = null,
        val ageBlocked: Boolean = false,
        val weightLossAllowed: Boolean = true,
    )

    private fun validate(step: OnboardingStep, answers: OnboardingAnswers): Validated =
        when (step) {
            OnboardingStep.Name -> validateName(answers)
            OnboardingStep.BirthDate -> validateBirthDate(answers)
            OnboardingStep.Height -> validateHeight(answers)
            OnboardingStep.Weight -> validateWeight(answers)
            OnboardingStep.Goal -> validateGoal(answers)
            OnboardingStep.Sex -> validateSex(answers)
            OnboardingStep.BodyFat ->
                optionalNumber(answers, answers.bodyFat, MIN_BODY_FAT, MAX_BODY_FAT)
            OnboardingStep.RestingHeartRate ->
                optionalNumber(answers, answers.restingHeartRate, MIN_HEART_RATE, MAX_HEART_RATE)
            OnboardingStep.BloodPressure -> validateBloodPressure(answers)
            OnboardingStep.Measurements -> validateMeasurements(answers)
            OnboardingStep.GoalWeight -> validateGoalWeight(answers)
            else -> Validated(answers)
        }

    /**
     * A tape measurement outside the stored range would be dropped silently at write
     * time; the question says so instead (`pr.md` §6.1 tier 3).
     */
    private fun validateMeasurements(answers: OnboardingAnswers): Validated {
        val typed = tapeSites(answers).map { (_, text) -> text }.filter { it.isNotBlank() }
        val bad = typed.any { tape(it, answers.units) == null }
        return Validated(answers, if (bad) OnboardingIssue.VALUE_OUT_OF_RANGE else null)
    }

    private fun validateSex(answers: OnboardingAnswers): Validated =
        if (answers.sexAtBirth == null && !answers.preferNotToSay) {
            Validated(answers, OnboardingIssue.REQUIRED)
        } else {
            Validated(answers)
        }

    private fun validateGoal(answers: OnboardingAnswers): Validated = if (answers.goal == null) {
        Validated(answers, OnboardingIssue.GOAL_REQUIRED)
    } else {
        Validated(answers)
    }

    private fun validateName(answers: OnboardingAnswers): Validated = if (answers.name.isBlank()) {
        Validated(answers, OnboardingIssue.NAME_REQUIRED)
    } else {
        Validated(answers)
    }

    private fun validateBirthDate(answers: OnboardingAnswers): Validated {
        val todayDate = now().toLocalDateTime(zone).date
        val birth = parseDate(answers.birthDate)
        return when {
            birth == null -> Validated(answers, OnboardingIssue.DATE_INVALID)
            !AgeRules.isSupported(birth, todayDate) ->
                Validated(answers, OnboardingIssue.DATE_UNDER_SIXTEEN, ageBlocked = true)
            else -> {
                val adult = AgeRules.mayUseWeightLossGoal(birth, todayDate)
                val goal = if (!adult &&
                    answers.goal == ProfileGoal.LOSE_FAT
                ) {
                    null
                } else {
                    answers.goal
                }
                Validated(answers.copy(goal = goal), weightLossAllowed = adult)
            }
        }
    }

    private fun validateHeight(answers: OnboardingAnswers): Validated =
        requiredMeasure(answers, answers.height, cm(answers.height, answers.units) != null)

    private fun validateWeight(answers: OnboardingAnswers): Validated =
        requiredMeasure(answers, answers.weight, kg(answers.weight, answers.units) != null)

    private fun requiredMeasure(
        answers: OnboardingAnswers,
        text: String,
        valid: Boolean,
    ): Validated = if (valid) {
        Validated(answers)
    } else {
        Validated(
            answers,
            if (text.isBlank()) {
                OnboardingIssue.VALUE_REQUIRED
            } else {
                OnboardingIssue.VALUE_OUT_OF_RANGE
            },
        )
    }

    private fun optionalNumber(
        answers: OnboardingAnswers,
        text: String,
        min: Double,
        max: Double,
    ): Validated = numberIssue(text, min, max)?.let { Validated(answers, it) } ?: Validated(answers)

    private fun validateBloodPressure(answers: OnboardingAnswers): Validated {
        val systolic = decimal(answers.systolic)
        val diastolic = decimal(answers.diastolic)
        val issue = when {
            systolic == null && diastolic == null -> null
            systolic == null || diastolic == null -> OnboardingIssue.VALUE_OUT_OF_RANGE
            systolic !in MIN_SYSTOLIC..MAX_SYSTOLIC -> OnboardingIssue.VALUE_OUT_OF_RANGE
            diastolic !in MIN_DIASTOLIC..MAX_DIASTOLIC -> OnboardingIssue.VALUE_OUT_OF_RANGE
            else -> null
        }
        return Validated(answers, issue)
    }

    private fun validateGoalWeight(answers: OnboardingAnswers): Validated {
        val issue = when {
            answers.goalWeight.isBlank() -> null
            kg(answers.goalWeight, answers.units) == null -> OnboardingIssue.VALUE_OUT_OF_RANGE
            answers.goalDate.isNotBlank() && parseDate(answers.goalDate) == null ->
                OnboardingIssue.DATE_INVALID
            else -> null
        }
        return Validated(answers, issue)
    }

    private fun numberIssue(text: String, min: Double, max: Double): OnboardingIssue? {
        if (text.isBlank()) return null
        val value = decimal(text)
        return if (value == null || value !in min..max) {
            OnboardingIssue.VALUE_OUT_OF_RANGE
        } else {
            null
        }
    }

    /** A decimal keyboard writes the locale's separator; both are the same number. */
    private fun decimal(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

    private fun nextStep(step: OnboardingStep, answers: OnboardingAnswers): OnboardingStep {
        val order = visibleSteps(answers)
        val index = order.indexOf(step)
        if (index < 0 || index == order.lastIndex) return OnboardingStep.Finish
        return order[index + 1]
    }

    private fun previousStep(step: OnboardingStep, answers: OnboardingAnswers): OnboardingStep {
        val order = visibleSteps(answers)
        val index = order.indexOf(step)
        return if (index <= 1) OnboardingStep.Welcome else order[index - 1]
    }

    private fun visibleSteps(answers: OnboardingAnswers): List<OnboardingStep> {
        val birth = parseDate(answers.birthDate)
        val mayLoseWeight = birth?.let {
            AgeRules.mayUseWeightLossGoal(it, now().toLocalDateTime(zone).date)
        } ?: true
        return OnboardingStep.entries.filter { step ->
            when (step) {
                OnboardingStep.BodyFat -> !answers.weightNeutral
                OnboardingStep.GoalWeight -> !answers.weightNeutral && mayLoseWeight
                else -> true
            }
        }
    }

    private fun answeredTier(answers: OnboardingAnswers): Long {
        val tierThree = listOf(
            answers.bodyFat.isNotBlank(),
            answers.restingHeartRate.isNotBlank(),
            answers.systolic.isNotBlank(),
            answers.neck.isNotBlank(),
            answers.injuries.isNotBlank(),
            answers.medications.isNotBlank(),
            answers.cycleTracking,
            answers.goalWeight.isNotBlank(),
        ).any { it }
        val tierTwo = listOf(
            answers.activity != null,
            answers.experience != null,
            answers.equipment.isNotEmpty(),
            answers.diet != null,
            answers.allergies.isNotBlank(),
        ).any { it }
        return when {
            tierThree -> TIER_THREE
            tierTwo -> TIER_TWO
            else -> TIER_ONE
        }
    }

    private fun persistOnboarding(answers: OnboardingAnswers) {
        _state.update { it.copy(onboarding = it.onboarding.copy(saving = true)) }
        launchGuarded {
            runCatching {
                withContext(Dispatchers.IO) { writeOnboarding(answers) }
            }.onFailure {
                message = SettingsMessage.PROFILE_INVALID
            }
            rebuild()
            _state.update { it.copy(onboarding = it.onboarding.copy(saving = false)) }
        }
    }

    private fun writeOnboarding(answers: OnboardingAnswers) {
        val birth = parseDate(answers.birthDate) ?: error("validated date went missing")
        val heightCm = cm(answers.height, answers.units) ?: error("validated height went missing")
        val weightKg = kg(answers.weight, answers.units) ?: error("validated weight went missing")
        val at = now()
        val existing = store.profile.active()
        val profile = Profile(
            id = existing?.id ?: container.ids.newId(),
            name = answers.name.trim(),
            date_of_birth = birth.toString(),
            sex_at_birth = answers.sexAtBirth?.name,
            gender = null,
            height_cm = heightCm,
            weight_kg = weightKg,
            units = answers.units.key,
            zone_id = zone.id,
            language = Locale.getDefault().language,
            goal = answers.goal?.key,
            activity_level = answers.activity?.name,
            training_experience = answers.experience?.key,
            weight_neutral = answers.weightNeutral,
            intake_tier = answeredTier(answers),
            created_at = existing?.created_at ?: at.toEpochMilliseconds(),
            updated_at = at.toEpochMilliseconds(),
            source_id = AppContainer.MANUAL_SOURCE.value,
            revision = (existing?.revision ?: 0L) + 1L,
        )
        store.profile.upsert(profile)
        val preferences = container.preferences
        store.settings.put(SettingKeys.UNITS, answers.units.key)
        store.settings.put(SettingKeys.ZONE_ID, zone.id)
        store.settings.put(SettingKeys.LANGUAGE, profile.language)
        store.settings.put(SettingKeys.WEIGHT_NEUTRAL, answers.weightNeutral.toString())
        store.settings.put(SettingKeys.GOAL_MODE, answers.goal?.key.orEmpty())
        store.settings.put(
            SettingKeys.EQUIPMENT,
            answers.equipment.joinToString(",") { it.key },
        )
        store.settings.put(SettingKeys.DIETARY_PATTERN, answers.diet?.key.orEmpty())
        store.settings.put(SettingKeys.ALLERGIES, answers.allergies.trim())
        store.settings.put(
            SettingKeys.SLEEP_WINDOW,
            "${answers.sleepStartHour}-${answers.sleepEndHour}",
        )
        store.settings.put(SettingKeys.INJURIES, answers.injuries.trim())
        store.settings.put(SettingKeys.MEDICATIONS, answers.medications.trim())
        store.settings.put(SettingKeys.CYCLE_TRACKING, answers.cycleTracking.toString())
        store.settings.put(
            SettingKeys.COMPOSITION_SOURCE,
            answers.compositionSource?.key.orEmpty(),
        )
        preferences.setPreset(answers.preset)
        preferences.setModules(answers.preset.enabled)
        preferences.markOnboardingComplete(_state.value.onboarding.tier)

        val happened =
            chosenInstant(now().toLocalDateTime(zone).date, now().toLocalDateTime(zone).hour)
        writeOnboardingMeasurements(answers, happened)
    }

    /** The tier-3 answers that become observations or measurements, each with provenance. */
    private fun writeOnboardingMeasurements(answers: OnboardingAnswers, happened: Instant) {
        writeObservation(ObservationType.WEIGHT, weightKgValue(answers), happened)
        if (!answers.weightNeutral) {
            decimal(answers.bodyFat)?.let {
                writeObservation(
                    ObservationType.BODY_FAT_PERCENT,
                    it,
                    happened,
                    method = compositionMethod(answers.compositionSource),
                )
            }
            kg(answers.goalWeight, answers.units)?.let {
                store.settings.put(SettingKeys.GOAL_WEIGHT_KG, it.toString())
            }
            parseDate(answers.goalDate)?.let {
                store.settings.put(SettingKeys.GOAL_TARGET_DATE, it.toString())
            }
        }
        writeOnboardingVitals(answers, happened)
        writeOnboardingTape(answers, happened)
    }

    private fun writeOnboardingVitals(answers: OnboardingAnswers, happened: Instant) {
        decimal(answers.restingHeartRate)?.let {
            writeObservation(ObservationType.RESTING_HEART_RATE, it, happened)
        }
        decimal(answers.systolic)?.let {
            writeObservation(
                ObservationType.BLOOD_PRESSURE_SYSTOLIC,
                it,
                happened,
                method = MeasurementMethod.CUFF,
            )
        }
        decimal(answers.diastolic)?.let {
            writeObservation(
                ObservationType.BLOOD_PRESSURE_DIASTOLIC,
                it,
                happened,
                method = MeasurementMethod.CUFF,
            )
        }
    }

    private fun writeOnboardingTape(answers: OnboardingAnswers, happened: Instant) {
        tapeSites(answers).forEach { (type, text) ->
            tape(text, answers.units)?.let { writeMeasurement(type, it, "cm", happened) }
        }
    }

    /** The eleven tape sites of `pr.md` §6.1 tier 3, named once. */
    private fun tapeSites(answers: OnboardingAnswers): List<Pair<String, String>> = listOf(
        "neck" to answers.neck,
        "shoulders" to answers.shoulders,
        "chest" to answers.chest,
        "waist" to answers.waist,
        "hips" to answers.hips,
        "upper_arm" to answers.upperArm,
        "forearm" to answers.forearm,
        "thigh" to answers.thigh,
        "calf" to answers.calf,
        "wrist" to answers.wrist,
        "ankle" to answers.ankle,
    )

    private fun compositionMethod(
        source: app.openhealth.ui.screens.onboarding.CompositionSource?,
    ): MeasurementMethod = when (source) {
        app.openhealth.ui.screens.onboarding.CompositionSource.SCALE -> MeasurementMethod.SCALE
        app.openhealth.ui.screens.onboarding.CompositionSource.BIA -> MeasurementMethod.BIA
        app.openhealth.ui.screens.onboarding.CompositionSource.DEXA -> MeasurementMethod.DEXA
        app.openhealth.ui.screens.onboarding.CompositionSource.CALIPERS,
        app.openhealth.ui.screens.onboarding.CompositionSource.OTHER,
        -> MeasurementMethod.OTHER
        null -> MeasurementMethod.MANUAL_ENTRY
    }

    private fun weightKgValue(answers: OnboardingAnswers): Double =
        kg(answers.weight, answers.units) ?: error("validated weight went missing")

    // -------------------------------------------------------------------- quick add

    fun openQuickAdd(action: QuickAction?) {
        if (action == QuickAction.FOOD &&
            HealthModule.NUTRITION in _state.value.preferences.modules
        ) {
            _state.update { it.copy(quickAdd = it.quickAdd.copy(visible = false)) }
            openFoodSearchDefault()
            return
        }
        message = null
        val current = _state.value.quickAdd
        val mode = when (action) {
            null -> QuickAddMode.TILES
            QuickAction.NOTE -> QuickAddMode.NOTE
            QuickAction.WATER -> QuickAddMode.WATER
            QuickAction.WEIGHT -> QuickAddMode.WEIGHT
            QuickAction.FOOD -> QuickAddMode.FOOD
            QuickAction.SET -> QuickAddMode.SET
            QuickAction.VITALS -> QuickAddMode.VITALS
        }
        val local = now().toLocalDateTime(zone)
        _state.update {
            it.copy(
                quickAdd = current.copy(
                    visible = true,
                    mode = mode,
                    note = "",
                    weight = "",
                    whenDate = local.date,
                    whenHour = local.hour,
                    invalid = false,
                ),
            )
        }
    }

    fun closeQuickAdd() {
        _state.update {
            it.copy(quickAdd = it.quickAdd.copy(visible = false, mode = QuickAddMode.TILES))
        }
    }

    fun quickAddBack() {
        _state.update { it.copy(quickAdd = it.quickAdd.copy(mode = QuickAddMode.TILES)) }
    }

    fun onQuickAddNote(value: String) {
        _state.update { it.copy(quickAdd = it.quickAdd.copy(note = value)) }
    }

    fun onQuickAddWater(value: Int) {
        _state.update { it.copy(quickAdd = it.quickAdd.copy(waterMl = value)) }
    }

    fun onQuickAddWeight(value: String) {
        _state.update { it.copy(quickAdd = it.quickAdd.copy(weight = value)) }
    }

    /**
     * The backdate strip never offers a moment that has not happened: on today the hour
     * stops at the current one, so the sheet shows exactly the `happened_at` that will
     * be stored (`pr.md` §6.27).
     */
    fun onQuickAddWhen(date: LocalDate, hour: Int) {
        val local = now().toLocalDateTime(zone)
        val safeDate = if (date > local.date) local.date else date
        val safeHour = hour.coerceIn(0, LAST_HOUR)
            .let { if (safeDate == local.date) minOf(it, local.hour) else it }
        _state.update {
            it.copy(quickAdd = it.quickAdd.copy(whenDate = safeDate, whenHour = safeHour))
        }
    }

    fun saveQuickAdd() {
        val quick = _state.value.quickAdd
        if (!quick.canSave) return
        _state.update { it.copy(quickAdd = it.quickAdd.copy(saving = true, invalid = false)) }
        launchGuarded {
            val earned = runCatching<Pair<UndoAction, Int>?> {
                withContext(Dispatchers.IO) { appendQuick(quick) }
            }.getOrNull()
            if (earned != null) {
                undoAction = earned.first
                message = null
                showToast(earned.second, undo = true)
                _state.update {
                    it.copy(quickAdd = newQuickAdd(container).copy(visible = false))
                }
                rebuild()
            } else {
                _state.update {
                    it.copy(quickAdd = it.quickAdd.copy(saving = false, invalid = true))
                }
            }
        }
    }

    private fun appendQuick(quick: QuickAddUi): Pair<UndoAction, Int>? = when (quick.mode) {
        QuickAddMode.NOTE -> {
            val id = container.ids.newId()
            store.notes.insert(
                noteRow(
                    id = id,
                    body = quick.note.trim(),
                    at = chosenInstant(quick.whenDate, quick.whenHour),
                ),
            )
            UndoAction.Added(TrashEntity.NOTE, id) to R.string.toast_note_added
        }
        QuickAddMode.WATER -> {
            val id = container.ids.newId()
            // One glass is one part of the day's total: the rollup sums this source's
            // parts, so a backdated glass adds and a deleted glass subtracts.
            writeIncrement(
                type = ObservationType.WATER,
                value = quick.waterMl.toDouble(),
                happenedAt = chosenInstant(quick.whenDate, quick.whenHour),
                id = id,
            )
            store.settings.put(SettingKeys.LAST_WATER_ML, quick.waterMl.toString())
            UndoAction.Added(TrashEntity.OBSERVATION, id) to R.string.toast_water_added
        }
        QuickAddMode.WEIGHT -> {
            val typed = kg(quick.weight, profileUnits())
            if (typed == null) {
                null
            } else {
                val id = container.ids.newId()
                writeObservation(
                    type = ObservationType.WEIGHT,
                    value = typed,
                    happenedAt = chosenInstant(quick.whenDate, quick.whenHour),
                    id = id,
                )
                UndoAction.Added(TrashEntity.OBSERVATION, id) to R.string.toast_weight_added
            }
        }
        else -> null
    }

    fun repeat(recent: QuickAddRecent) {
        // A recent row logs in one tap, so a double tap must not log twice.
        if (repeating) return
        repeating = true
        launchGuarded {
            val recorded = runCatching {
                withContext(Dispatchers.IO) {
                    val id = container.ids.newId()
                    when (recent.action) {
                        QuickAction.WATER -> {
                            writeIncrement(ObservationType.WATER, recent.value, now(), id = id)
                            store.settings.put(
                                SettingKeys.LAST_WATER_ML,
                                recent.value.toInt().toString(),
                            )
                            UndoAction.Added(TrashEntity.OBSERVATION, id)
                        }
                        QuickAction.WEIGHT -> {
                            writeObservation(ObservationType.WEIGHT, recent.value, now(), id = id)
                            UndoAction.Added(TrashEntity.OBSERVATION, id)
                        }
                        else -> null
                    }
                }
            }.getOrNull()
            if (recorded != null) {
                undoAction = recorded
                showToast(R.string.toast_saved, undo = true)
            } else {
                showToast(R.string.toast_not_saved, undo = false)
            }
            repeating = false
            rebuild()
        }
    }

    // -------------------------------------------------------------------- mutations

    fun deleteTimelineRow(row: TimelineRowModel) {
        if (!row.deletable) return
        launchGuarded {
            val undo = withContext(Dispatchers.IO) {
                when (row.kind) {
                    TimelineKind.NOTE ->
                        store.trash.deleteNote(NoteId(row.entityId))?.let(UndoAction::Deleted)

                    TimelineKind.WATER, TimelineKind.OBSERVATION, TimelineKind.WEIGHT ->
                        store.trash.deleteObservation(ObservationId(row.entityId))
                            ?.let(UndoAction::Deleted)

                    TimelineKind.MEASUREMENT ->
                        store.trash.deleteMeasurement(
                            app.openhealth.core.ids.MeasurementId(row.entityId),
                        )?.let(UndoAction::Deleted)

                    TimelineKind.VITAL -> {
                        val vital = store.body.vitalById(row.entityId)
                        val observationId = vital?.observation_id
                        if (observationId != null) {
                            UndoAction.DeletedMany(body.deleteReading(observationId))
                        } else {
                            store.trash.deleteVital(
                                app.openhealth.core.ids.VitalId(row.entityId),
                            )?.let(UndoAction::Deleted)
                        }
                    }

                    TimelineKind.PHOTO ->
                        store.trash.deletePhoto(app.openhealth.core.ids.PhotoId(row.entityId))
                            ?.let(UndoAction::Deleted)

                    TimelineKind.ENTRY ->
                        store.trash.deleteEntry(EntryId(row.entityId))?.let(UndoAction::Deleted)

                    TimelineKind.SESSION ->
                        store.trash.deleteSession(row.entityId)?.let(UndoAction::Deleted)

                    TimelineKind.SET ->
                        store.trash.deleteWorkSet(app.openhealth.core.ids.SetId(row.entityId))
                            ?.let(UndoAction::Deleted)

                    else -> null
                }
            }
            if (undo != null) {
                undoAction = undo
                toastJob?.cancel()
                _state.update {
                    it.copy(
                        sessionUndo = AppToast(R.string.toast_deleted, undo = true),
                        toast = null,
                    )
                }
                rebuild()
            }
        }
    }

    fun excludeFromTrends(row: TimelineRowModel) {
        if (!row.excludable) return
        launchGuarded {
            runCatching {
                withContext(Dispatchers.IO) {
                    store.observations.excludeFromTrends(
                        ObservationId(row.entityId),
                        !row.excluded,
                    )
                }
            }
            showToast(R.string.toast_saved, undo = false)
            rebuild()
        }
    }

    fun undoLast() {
        val action = undoAction ?: return
        launchGuarded {
            withContext(Dispatchers.IO) {
                when (action) {
                    is UndoAction.Added -> when (action.entity) {
                        TrashEntity.NOTE -> store.trash.deleteNote(NoteId(action.id))
                        TrashEntity.OBSERVATION ->
                            store.trash.deleteObservation(ObservationId(action.id))
                        TrashEntity.MEASUREMENT -> store.trash.deleteMeasurement(
                            app.openhealth.core.ids.MeasurementId(action.id),
                        )
                        else -> Unit
                    }
                    is UndoAction.Deleted -> store.trash.undo(action.tombstone)
                    is UndoAction.DeletedMany -> action.tombstones.forEach { store.trash.undo(it) }
                }
            }
            undoAction = null
            dismissToast()
            _state.update { it.copy(sessionUndo = null, toast = null) }
            rebuild()
        }
    }

    fun dismissToast() {
        toastJob?.cancel()
        _state.update { it.copy(toast = null) }
    }

    fun dismissSessionUndo() {
        _state.update { it.copy(sessionUndo = null) }
    }

    fun toggleModule(module: HealthModule, enabled: Boolean) {
        message = null
        launchGuarded {
            withContext(Dispatchers.IO) {
                container.preferences.setModule(module, enabled)
                container.preferences.setPreset(
                    app.openhealth.core.preferences.ModulePreset.CUSTOM,
                )
            }
            rebuild()
        }
    }

    fun setPreset(preset: app.openhealth.core.preferences.ModulePreset) {
        message = null
        launchGuarded {
            withContext(Dispatchers.IO) {
                container.preferences.setPreset(preset)
                container.preferences.setModules(preset.enabled)
            }
            rebuild()
        }
    }

    fun setWeightNeutral(enabled: Boolean) {
        message = null
        launchGuarded {
            withContext(Dispatchers.IO) { container.preferences.setWeightNeutral(enabled) }
            rebuild()
        }
    }

    fun setTheme(theme: ThemeChoice) {
        launchGuarded {
            withContext(Dispatchers.IO) {
                container.preferences.setTheme(
                    when (theme) {
                        ThemeChoice.SYSTEM -> app.openhealth.android.preferences.ThemeMode.SYSTEM
                        ThemeChoice.DARK -> app.openhealth.android.preferences.ThemeMode.DARK
                        ThemeChoice.LIGHT -> app.openhealth.android.preferences.ThemeMode.LIGHT
                    },
                )
            }
            rebuild()
        }
    }

    fun setAccent(accent: OhAccent) {
        launchGuarded {
            withContext(Dispatchers.IO) { container.preferences.setAccent(accent) }
            rebuild()
        }
    }

    fun setAppLock(enabled: Boolean) {
        val available = app.openhealth.android.security.BiometricGate().available(container.context)
        if (enabled && !available) {
            message = SettingsMessage.LOCK_UNAVAILABLE
            refresh()
            return
        }
        launchGuarded {
            withContext(Dispatchers.IO) {
                container.preferences.setAppLock(enabled)
            }
            rebuild()
        }
    }

    /** Re-arm the lock when the app leaves the foreground (`pr.md` §6.23). */
    fun onAppBackgrounded() {
        if (suppressNextLock) {
            suppressNextLock = false
            return
        }
        val available = app.openhealth.android.security.BiometricGate()
            .available(container.context)
        if (_state.value.preferences.appLock && available && !_state.value.locked) {
            _state.update { it.copy(locked = true, lockMessage = null) }
        }
    }

    /** A file picker stops the activity; that is not the person leaving the app. */
    fun suppressLockForExternalActivity() {
        suppressNextLock = true
    }

    fun disableAppLock() {
        launchGuarded {
            withContext(Dispatchers.IO) { container.preferences.setAppLock(false) }
            _state.update { it.copy(locked = false, lockMessage = null) }
        }
    }

    fun unlock(activity: Activity) {
        app.openhealth.android.security.BiometricGate().authenticate(
            activity = activity,
            prompt = app.openhealth.android.security.LockPrompt(
                title = container.context.getString(R.string.lock_prompt_title),
                subtitle = container.context.getString(R.string.lock_prompt_body),
                cancel = container.context.getString(R.string.lock_prompt_cancel),
            ),
            onSuccess = {
                _state.update { it.copy(locked = false, lockMessage = null) }
            },
            onError = { error ->
                _state.update {
                    it.copy(
                        lockMessage = when (error) {
                            LockError.NO_ENROLMENT -> LockMessage.NO_ENROLMENT
                            LockError.LOCKED_OUT -> LockMessage.LOCKED_OUT
                            LockError.CANCELLED -> LockMessage.CANCELLED
                            LockError.FAILED -> LockMessage.CANCELLED
                        },
                    )
                }
            },
        )
    }

    fun saveProfile(edit: ProfileEdit) {
        message = null
        launchGuarded {
            val ok = withContext(Dispatchers.IO) { writeProfile(edit) }
            message = if (ok) SettingsMessage.PROFILE_SAVED else SettingsMessage.PROFILE_INVALID
            rebuild()
        }
    }

    private fun writeProfile(edit: ProfileEdit): Boolean {
        val current = store.profile.active()
        val birth = if (edit.birthDate.isBlank()) {
            current?.date_of_birth?.let(::parseDate)
        } else {
            parseDate(edit.birthDate)
        }
        val today = now().toLocalDateTime(zone).date
        if (current == null || (birth != null && !AgeRules.isSupported(birth, today))) {
            return false
        }
        // 16-17 never carry a weight-loss goal, whichever screen set it and whatever
        // the birth date was when it was set (`pr.md` §6.1).
        val mayLoseWeight = birth?.let { AgeRules.mayUseWeightLossGoal(it, today) } ?: true
        val goal = (edit.goal ?: ProfileGoal.fromKey(current.goal))
            ?.takeUnless { it == ProfileGoal.LOSE_FAT && !mayLoseWeight }
        val height = cm(edit.height, edit.units) ?: current.height_cm
        val weight = kg(edit.weight, edit.units) ?: current.weight_kg
        store.profile.upsert(
            current.copy(
                name = edit.name.trim().ifBlank { current.name },
                date_of_birth = birth?.toString() ?: current.date_of_birth,
                goal = goal?.key,
                height_cm = height,
                weight_kg = weight,
                units = edit.units.key,
                weight_neutral = current.weight_neutral,
                updated_at = now().toEpochMilliseconds(),
                revision = current.revision + 1L,
            ),
        )
        writeProfileSettings(edit, goal, mayLoseWeight)
        if (weight != null && weight != current.weight_kg) {
            writeObservation(ObservationType.WEIGHT, weight, now())
        }
        return true
    }

    private fun writeProfileSettings(
        edit: ProfileEdit,
        goal: ProfileGoal?,
        mayLoseWeight: Boolean,
    ) {
        store.settings.put(SettingKeys.UNITS, edit.units.key)
        store.settings.put(SettingKeys.GOAL_MODE, goal?.key.orEmpty())
        if (!mayLoseWeight) {
            store.settings.put(SettingKeys.GOAL_WEIGHT_KG, "")
            store.settings.put(SettingKeys.GOAL_TARGET_DATE, "")
        }
    }

    fun setTimelineFilter(value: TimelineFilter) {
        message = null
        filter = value
        launchGuarded { rebuild() }
    }

    fun restoreTombstone(item: TrashItemModel) {
        launchGuarded {
            withContext(Dispatchers.IO) { store.trash.undo(TombstoneId(item.id)) }
            rebuild()
        }
    }

    fun purgeTombstone(item: TrashItemModel) {
        launchGuarded {
            withContext(Dispatchers.IO) { store.trash.purge(TombstoneId(item.id)) }
            rebuild()
        }
    }

    fun deleteAllNotes() {
        message = null
        launchGuarded {
            withContext(Dispatchers.IO) {
                store.notes.all().forEach { store.trash.deleteNote(NoteId(it.id)) }
            }
            message = SettingsMessage.NOTES_DELETED
            rebuild()
        }
    }

    fun emptyTrash() {
        message = null
        launchGuarded {
            withContext(Dispatchers.IO) {
                store.trash.active().forEach { store.trash.purge(TombstoneId(it.id)) }
            }
            message = SettingsMessage.TRASH_EMPTIED
            rebuild()
        }
    }

    fun export(uri: Uri, passphrase: CharArray) {
        if (busy) {
            passphrase.fill('\u0000')
            return
        }
        busy = true
        refresh()
        launchGuarded {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val bytes = container.export.export(passphrase)
                    container.documents.write(uri, bytes)
                }
            }
            message = if (result.isSuccess) {
                SettingsMessage.EXPORT_SAVED
            } else {
                SettingsMessage.EXPORT_FAILED
            }
            busy = false
            rebuild()
        }
    }

    fun restore(uri: Uri, passphrase: CharArray) {
        if (busy) {
            passphrase.fill('\u0000')
            return
        }
        busy = true
        refresh()
        launchGuarded {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val bytes = container.documents.read(uri)
                    // Decrypt and validate before touching the live record or snapshot.
                    val bundle = container.export.read(bytes, passphrase)
                    check(container.writePreRestoreSnapshot()) {
                        "a restore needs a local snapshot first"
                    }
                    container.export.restoreBundle(bundle)
                    store.engine.refreshRegistry()
                    store.engine.rebuildAll()
                }
            }
            message = when (result.exceptionOrNull()) {
                null -> SettingsMessage.RESTORE_DONE
                is WrongPassphrase -> SettingsMessage.WRONG_PASSPHRASE
                is UnsupportedExportFormat -> SettingsMessage.RESTORE_FAILED
                else -> SettingsMessage.RESTORE_FAILED
            }
            busy = false
            rebuild()
        }
    }

    fun undoLastRestore() {
        launchGuarded {
            val ok = withContext(Dispatchers.IO) {
                container.restorePreRestoreSnapshot().also { restored ->
                    // The snapshot brings its own sources back; the live engine must not
                    // keep the registry the restore it is undoing left behind.
                    if (restored) store.engine.refreshRegistry()
                }
            }
            message = if (ok) SettingsMessage.RESTORE_DONE else SettingsMessage.RESTORE_FAILED
            rebuild()
        }
    }

    // -------------------------------------------------------------------- devices

    fun openDevices() {
        _state.update {
            it.copy(
                devices = activity.model(
                    stepsModuleOn = app.openhealth.core.preferences.HealthModule.STEPS in
                        it.preferences.modules,
                    found = it.foundDevices,
                ),
                pendingPermission = null,
            )
        }
    }

    fun closeDevices() {
        container.ble.stopScan()
        _state.update { it.copy(foundDevices = emptyList()) }
    }

    /** Starts or stops the phone's own counter; the permission is asked at first use. */
    fun toggleStepTracking() {
        val tracking = store.devices.stepTrackingEnabled()
        if (tracking) {
            activity.setStepTracking(false)
            app.openhealth.android.activity.StepTrackingService.ensureStopped(container.context)
            refreshDevices()
            return
        }
        if (activityRecognitionGranted()) {
            activity.setStepTracking(true)
            app.openhealth.android.activity.StepTrackingService.ensureRunning(container.context)
            refreshDevices()
        } else {
            _state.update {
                it.copy(
                    pendingPermission =
                    app.openhealth.ui.screens.devices.DevicePermissionRequest
                        .ACTIVITY_RECOGNITION,
                )
            }
        }
    }

    fun onActivityRecognitionResult(granted: Boolean) {
        _state.update { it.copy(pendingPermission = null) }
        if (!granted) {
            showToast(R.string.toast_permission_needed, undo = false)
            return
        }
        activity.setStepTracking(true)
        app.openhealth.android.activity.StepTrackingService.ensureRunning(container.context)
        refreshDevices()
    }

    fun onHealthConnectRequested() {
        _state.update {
            it.copy(
                pendingPermission =
                app.openhealth.ui.screens.devices.DevicePermissionRequest.HEALTH_CONNECT,
            )
        }
    }

    fun onHealthPermissionResult(granted: Set<String>) {
        val requestedHistory = _state.value.pendingPermission ==
            app.openhealth.ui.screens.devices.DevicePermissionRequest.HEALTH_CONNECT_HISTORY
        _state.update { it.copy(pendingPermission = null) }
        if (granted.isEmpty()) {
            showToast(R.string.toast_permission_needed, undo = false)
            refreshDevices()
            return
        }
        if (requestedHistory &&
            androidx.health.connect.client.permission.HealthPermission
                .PERMISSION_READ_HEALTH_DATA_HISTORY !in granted
        ) {
            // Reading a year without the history grant would silently return 30 days;
            // say so instead of marking the import done.
            showToast(R.string.toast_health_no_history, undo = false)
            refreshDevices()
            return
        }
        syncHealthConnectNow(backfill = requestedHistory)
    }

    fun syncHealthConnect(backfill: Boolean) {
        if (backfill) {
            // The year-long read needs its own grant, so the request comes first and the
            // sync is launched by its result, never before it (`pr.md` §6.18).
            _state.update {
                it.copy(
                    pendingPermission = app.openhealth.ui.screens.devices
                        .DevicePermissionRequest.HEALTH_CONNECT_HISTORY,
                )
            }
            return
        }
        syncHealthConnectNow(backfill = false)
    }

    private fun syncHealthConnectNow(backfill: Boolean) {
        launchGuarded {
            val outcome = withContext(Dispatchers.IO) {
                activity.syncHealthConnect(backfill)
            }
            val message = when {
                outcome.error == app.openhealth.android.activity.ActivityPresenter.NO_PERMISSION ->
                    R.string.toast_health_no_permission

                outcome.error != null -> R.string.toast_health_failed
                outcome.imported == 0 -> R.string.toast_health_none
                else -> R.string.toast_health_synced
            }
            refreshDevices()
            showToast(message, undo = false)
        }
    }

    fun importFit(uri: android.net.Uri) {
        launchGuarded {
            val outcome = withContext(Dispatchers.IO) { activity.importFit(uri) }
            val message = when {
                outcome.alreadyImported -> R.string.toast_fit_already_imported
                outcome.ok -> R.string.toast_fit_imported
                else -> R.string.toast_fit_failed
            }
            showToast(message, undo = false)
            refreshDevices()
            refresh()
        }
    }

    fun scanForDevices() {
        if (!container.ble.hasPermissions()) {
            _state.update {
                it.copy(
                    pendingPermission =
                    app.openhealth.ui.screens.devices.DevicePermissionRequest.BLUETOOTH,
                )
            }
            return
        }
        _state.update { it.copy(foundDevices = emptyList()) }
        container.ble.startScan()
        launchGuarded {
            delay(BLUETOOTH_SCAN_MILLIS)
            container.ble.stopScan()
            refreshDevices()
        }
    }

    fun onBluetoothResult(granted: Boolean) {
        _state.update { it.copy(pendingPermission = null) }
        if (!granted) {
            showToast(R.string.toast_permission_needed, undo = false)
            return
        }
        scanForDevices()
    }

    fun stopScanForDevices() {
        container.ble.stopScan()
        refreshDevices()
    }

    fun pairDevice(found: app.openhealth.ui.screens.devices.FoundDeviceModel) {
        launchGuarded {
            val paired = withContext(Dispatchers.IO) { activity.pair(found) }
            if (paired) {
                container.ble.stopScan()
                container.ble.connect(found.address, found.kind.let { kindOf(it) })
                _state.update { it.copy(foundDevices = emptyList()) }
                refreshDevices()
            }
        }
    }

    fun unpairDevice(id: String) {
        launchGuarded {
            withContext(Dispatchers.IO) { activity.unpair(id) }
            refreshDevices()
        }
    }

    fun connectDevice(id: String) {
        launchGuarded { withContext(Dispatchers.IO) { activity.connect(id) } }
    }

    fun correctTodaySteps(steps: Int) {
        launchGuarded {
            withContext(Dispatchers.IO) { activity.correctToday(steps) }
            refreshDevices()
            refresh()
        }
    }

    fun excludeTodayFromSteps() {
        launchGuarded {
            val excluded = withContext(Dispatchers.IO) { activity.toggleExcludeToday() }
            if (excluded) showToast(UiR.string.devices_excluded, undo = false)
            refreshDevices()
            refresh()
        }
    }

    fun moveSourcePriority(sourceId: String, up: Boolean) {
        launchGuarded {
            withContext(Dispatchers.IO) { activity.movePriority(sourceId, up) }
            refreshDevices()
            refresh()
        }
    }

    private var reconnected = false

    private suspend fun reconnectAndReconcile() {
        if (!reconnected) {
            reconnected = true
            runCatching { container.ble.reconnectPaired() }
        }
        // A grant survives a reinstall on the same phone but not a restore onto a new
        // one, so the stored set is refreshed from the platform rather than trusted.
        if (store.devices.healthConnectEnabled() && container.healthBus.isAvailable()) {
            val granted = runCatching { container.healthBus.grantedPermissions() }
                .getOrDefault(emptySet())
            if (granted.isNotEmpty()) store.devices.setHealthConnectGranted(granted)
        }
    }

    private fun refreshDevices() {
        _state.update { current ->
            current.copy(
                devices = activity.model(
                    stepsModuleOn = app.openhealth.core.preferences.HealthModule.STEPS in
                        current.preferences.modules,
                    found = current.foundDevices,
                ),
                // The Settings row names what is connected, so it moves with the page.
                settings = buildSettings(current.preferences, current.profile),
            )
        }
    }

    private fun activityRecognitionGranted(): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(
            container.context,
            android.Manifest.permission.ACTIVITY_RECOGNITION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun kindOf(kind: app.openhealth.core.activity.DeviceKindName) =
        app.openhealth.core.activity.DeviceKindName.toKind(kind)

    // -------------------------------------------------------------------- body and vitals

    fun openVital(type: ObservationType) {
        launchGuarded {
            val detail = withContext(Dispatchers.IO) {
                body.detail(type, null, profileUnits() == UnitSystem.IMPERIAL)
            }
            _state.update {
                it.copy(vitalType = type, vitalFilter = null, vitalDetail = detail)
            }
        }
    }

    fun closeVital() {
        _state.update { it.copy(vitalType = null, vitalFilter = null, vitalDetail = null) }
    }

    fun selectVitalContext(context: ObservationContext?) {
        val type = _state.value.vitalType ?: return
        launchGuarded {
            val detail = withContext(Dispatchers.IO) {
                body.detail(type, context, profileUnits() == UnitSystem.IMPERIAL)
            }
            _state.update { it.copy(vitalFilter = context, vitalDetail = detail) }
        }
    }

    fun saveVitalReading(draft: VitalDraft) {
        val imperial = profileUnits() == UnitSystem.IMPERIAL
        launchGuarded {
            val saved = withContext(Dispatchers.IO) { body.logReading(draft, imperial, now()) }
            showToast(
                if (saved.isNotEmpty()) R.string.toast_reading_saved else R.string.toast_not_saved,
                undo = false,
            )
            rebuild()
        }
    }

    fun saveWeight(value: String) {
        val imperial = profileUnits() == UnitSystem.IMPERIAL
        launchGuarded {
            val saved = withContext(Dispatchers.IO) {
                body.logWeight(value, now(), imperial)
            }
            showToast(
                if (saved != null) R.string.toast_weight_added else R.string.toast_not_saved,
                undo = false,
            )
            rebuild()
        }
    }

    fun saveMeasurement(
        type: String,
        value: String,
        method: MeasurementMethod = MeasurementMethod.MANUAL_ENTRY,
    ) {
        val imperial = profileUnits() == UnitSystem.IMPERIAL
        launchGuarded {
            val saved = withContext(Dispatchers.IO) {
                body.logMeasurement(type, value, now(), imperial, method)
            }
            showToast(
                if (saved != null) R.string.toast_measurement_saved else R.string.toast_not_saved,
                undo = false,
            )
            rebuild()
        }
    }

    fun deleteVitalReading(id: String) {
        launchGuarded {
            val tombstones = withContext(Dispatchers.IO) { body.deleteReading(id) }
            if (tombstones.isNotEmpty()) {
                undoAction = UndoAction.DeletedMany(tombstones)
                showToast(R.string.toast_reading_deleted, undo = true)
            }
            rebuild()
        }
    }

    fun addPhoto(bytes: ByteArray, caption: String?) {
        if (bytes.isEmpty()) return
        launchGuarded {
            val saved = withContext(Dispatchers.IO) {
                PhotoCodec.prepare(bytes)?.let { prepared ->
                    body.savePhoto(prepared, caption, now())
                }
            }
            showToast(
                if (saved != null) R.string.toast_photo_saved else R.string.toast_not_saved,
                undo = false,
            )
            rebuild()
        }
    }

    fun deletePhoto(id: String) {
        launchGuarded {
            val tombstone = withContext(Dispatchers.IO) { body.deletePhoto(id) }
            if (tombstone != null) {
                undoAction = UndoAction.Deleted(tombstone)
                showToast(R.string.toast_photo_deleted, undo = true)
            }
            rebuild()
        }
    }

    fun openPhotoCompare(id: String) {
        launchGuarded {
            val compare = withContext(Dispatchers.IO) { body.photoCompare(id) }
            _state.update { it.copy(photoCompare = compare) }
        }
    }

    fun closePhotoCompare() {
        _state.update { it.copy(photoCompare = null) }
    }

    fun setPhotosHidden(hidden: Boolean) {
        launchGuarded {
            withContext(Dispatchers.IO) { body.setPhotosHidden(hidden) }
            rebuild()
        }
    }

    fun setPhotosInExport(include: Boolean) {
        launchGuarded {
            withContext(Dispatchers.IO) { body.setPhotosInExport(include) }
            rebuild()
        }
    }

    fun startBpProtocol() {
        launchGuarded {
            withContext(Dispatchers.IO) { body.startProtocol() }
            rebuild()
        }
    }

    fun exportVitalsCsv(uri: Uri) {
        launchGuarded {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val text = body.csv()
                    container.documents.write(uri, text.toByteArray(Charsets.UTF_8))
                }
            }
            showToast(
                if (result.isSuccess) R.string.toast_csv_saved else R.string.toast_csv_failed,
                undo = false,
            )
        }
    }

    fun importVitalsCsv(uri: Uri) {
        launchGuarded {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val text = container.documents.read(uri).toString(Charsets.UTF_8)
                    body.importCsv(text)
                }
            }.getOrNull()
            when {
                result == null -> showToast(R.string.toast_csv_none, undo = false)
                result.imported == 0 && result.problems > 0 ->
                    showToast(R.string.toast_csv_none, undo = false)

                result.problems > 0 -> showToast(
                    R.string.toast_csv_import_problems,
                    undo = false,
                    argument = result.imported,
                    second = result.problems,
                )

                else -> showToast(
                    R.string.toast_csv_imported,
                    undo = false,
                    argument = result.imported,
                )
            }
            rebuild()
        }
    }

    // ---------------------------------------------------------------------- builders

    /**
     * The recovery map (`pr.md` §6.5, `DESIGN.md` §9.1): computed from the last four weeks
     * of logged sets, never stored. The adipose layer is hidden entirely in weight-neutral
     * mode (`DESIGN.md` §7.6), not blurred.
     */
    private fun buildBodyMap(
        preferences: app.openhealth.android.preferences.Preferences,
        profile: Profile,
    ): app.openhealth.ui.body.BodyMapModel? {
        if (HealthModule.TRAINING !in preferences.modules) return null
        val instant = now()
        val sets = store.training.loadedSets(
            (instant - RECOVERY_WINDOW).toEpochMilliseconds(),
        )
        val states = RecoveryMap.compute(instant, sets, thresholds = recoveryThresholds())
        val fatAllowed = preferences.displayPolicy.shows(
            app.openhealth.core.preferences.SensitiveMetric.BODY_FAT,
        )
        val figure = bodyFatFigure(instant)
        val tapeRows = tapeRows()
        val tape = tapeRows.mapValues { (_, row) -> row.value_ }
        val inputs = AdiposeInputs(
            sex = if (profile.sex_at_birth == "FEMALE") BodySex.FEMALE else BodySex.MALE,
            bodyFatPercent = figure.percent,
            heightCm = profile.height_cm,
            tapeCm = tape,
        )
        val adipose = if (fatAllowed) AdiposeModel.compute(inputs) else null
        val provenance = if (adipose == null) {
            null
        } else {
            // Which of the person's own values shaped the layer is the model's rule, not
            // the screen's: a single tape site scales nothing and is not claimed, and an
            // estimate with no recorded figure stands entirely on its tape sites.
            val sites = AdiposeModel.provenanceSites(inputs)
            val newestSite = sites.mapNotNull { tapeRows[it] }.maxByOrNull { it.recorded_at }
            app.openhealth.ui.body.FatProvenance(
                bodyFatPercent = figure.percent,
                method = figure.method,
                sourceLabel = figure.sourceLabel
                    ?: newestSite?.let { store.sources.byId(SourceId(it.source_id))?.label },
                observedAt = figure.observedAt
                    ?: newestSite?.let { Instant.fromEpochMilliseconds(it.happened_at) },
                recordedAt = figure.recordedAt
                    ?: newestSite?.let { Instant.fromEpochMilliseconds(it.recorded_at) },
                tapeSites = sites,
            )
        }
        val sourceLabels = store.sources.all().associate { it.id.value to it.label }
        return app.openhealth.ui.body.BodyMapModel.recovery(
            sex = if (profile.sex_at_birth == "FEMALE") BodySex.FEMALE else BodySex.MALE,
            states = states,
            adipose = adipose,
            fatProvenance = provenance,
            extras = app.openhealth.ui.body.BodyMapExtras(
                sexStated = profile.sex_at_birth == "MALE" || profile.sex_at_birth == "FEMALE",
                sourceLabels = sourceLabels,
            ),
        )
    }

    /** The day's winning body-fat figure and where it came from (`pr.md` §7.6). */
    private data class BodyFatFigure(
        val percent: Double?,
        val method: MeasurementMethod?,
        val sourceLabel: String?,
        val observedAt: Instant?,
        val recordedAt: Instant?,
    )

    private fun bodyFatFigure(instant: Instant): BodyFatFigure {
        val rollup = store.rollups.forTypeRange(
            ObservationType.BODY_FAT_PERCENT,
            epochDate,
            instant.toLocalDateTime(zone).date,
        ).lastOrNull() ?: return BodyFatFigure(null, null, null, null, null)
        val observation = store.observations
            .forDayAndType(rollup.localDate, ObservationType.BODY_FAT_PERCENT)
            .filter { it.provenance.sourceId == rollup.sourceId }
            .maxByOrNull { it.provenance.happenedAt }
        return BodyFatFigure(
            percent = rollup.value.value,
            method = observation?.provenance?.method,
            sourceLabel = rollup.sourceId?.let { store.sources.byId(it)?.label },
            observedAt = observation?.provenance?.happenedAt,
            recordedAt = observation?.provenance?.recordedAt,
        )
    }

    /** The newest reading of each tape site the adipose model uses (`DESIGN.md` §9.1.1). */
    private fun tapeRows(): Map<String, app.openhealth.data.db.Measurement> = MuscleRegion.entries
        .mapNotNull { region ->
            store.body.latestMeasurementOfType(region.measurementType)?.let { row ->
                region.measurementType to row
            }
        }
        .toMap()

    /** The person's own thresholds, or the documented defaults when unset or invalid. */
    private fun recoveryThresholds(): RecoveryThresholds {
        fun read(key: String, fallback: Double): Double =
            store.settings.get(key)?.toDoubleOrNull()?.takeIf { it > 0.0 } ?: fallback
        val recovering = read(SettingKeys.RECOVERY_RECOVERING_HOURS, DEFAULT_RECOVERING_HOURS)
        val easing = read(SettingKeys.RECOVERY_EASING_HOURS, DEFAULT_EASING_HOURS)
        val ready = read(SettingKeys.RECOVERY_READY_HOURS, DEFAULT_READY_HOURS)
        return if (recovering < easing && easing < ready) {
            RecoveryThresholds(recovering, easing, ready)
        } else {
            RecoveryThresholds()
        }
    }

    private fun buildRecent(
        preferences: app.openhealth.android.preferences.Preferences,
    ): List<QuickAddRecent> {
        val result = mutableListOf<QuickAddRecent>()
        if (HealthModule.NUTRITION in preferences.modules) {
            val water = store.settings.get(SettingKeys.LAST_WATER_ML)?.toDoubleOrNull()
            if (water != null) {
                result += QuickAddRecent(
                    action = QuickAction.WATER,
                    title = container.context.getString(UiR.string.quick_water),
                    detail = formatDisplay(water, "mL"),
                    value = water,
                    unit = UcumUnit.MILLILITRE,
                )
            }
        }
        if (HealthModule.BODY in preferences.modules &&
            preferences.displayPolicy.shows(app.openhealth.core.preferences.SensitiveMetric.WEIGHT)
        ) {
            store.observations.latest(ObservationType.WEIGHT)?.let { weight ->
                result += QuickAddRecent(
                    action = QuickAction.WEIGHT,
                    title = container.context.getString(UiR.string.quick_weight),
                    detail = formatDisplay(
                        weight.value.value,
                        weight.value.unit.ucum,
                    ),
                    value = weight.value.value,
                    unit = UcumUnit.KILOGRAM,
                )
            }
        }
        return result.take(RECENT_LIMIT)
    }

    private fun buildSettings(
        preferences: app.openhealth.android.preferences.Preferences,
        profile: Profile?,
    ): SettingsState = SettingsState(
        profileName = profile?.name.orEmpty(),
        profileSubtitle = listOfNotNull(
            profile?.goal?.let(::goalLabel),
            UnitSystem.fromKey(profile?.units)?.let(::unitLabel),
        ).joinToString(" · "),
        profileBirthDate = profile?.date_of_birth.orEmpty(),
        profileGoal = ProfileGoal.fromKey(profile?.goal),
        profileHeight = profile?.height_cm?.let {
            displayLength(it, UnitSystem.fromKey(profile.units) ?: UnitSystem.METRIC)
        }.orEmpty(),
        profileWeight = profile?.weight_kg?.let {
            displayMass(it, UnitSystem.fromKey(profile.units) ?: UnitSystem.METRIC)
        }.orEmpty(),
        profileUnits = UnitSystem.fromKey(profile?.units) ?: UnitSystem.METRIC,
        profileMayLoseWeight = profile?.date_of_birth?.let(::parseDate)?.let {
            AgeRules.mayUseWeightLossGoal(it, now().toLocalDateTime(zone).date)
        } ?: true,
        hasRestoreSnapshot = container.hasPreRestoreSnapshot(),
        modules = preferences.modules,
        devicesDetail = activity.summary(),
        preset = preferences.preset,
        weightNeutral = preferences.weightNeutral,
        displayPolicy = preferences.displayPolicy,
        theme = when (preferences.theme) {
            app.openhealth.android.preferences.ThemeMode.SYSTEM -> ThemeChoice.SYSTEM
            app.openhealth.android.preferences.ThemeMode.DARK -> ThemeChoice.DARK
            app.openhealth.android.preferences.ThemeMode.LIGHT -> ThemeChoice.LIGHT
        },
        accent = preferences.accent,
        appLock = preferences.appLock,
        appLockAvailable = app.openhealth.android.security.BiometricGate()
            .available(container.context),
        demo = preferences.demo,
        versionName = container.versionName,
        counts = SettingsCounts(
            notes = store.notes.count(),
            observations = store.observations.count(),
            measurements = store.body.countMeasurements(),
            trash = store.trash.active().size.toLong(),
        ),
        busy = busy,
        // A long encryption is stated, not left silent (`DESIGN.md` §6.4).
        message = if (busy) SettingsMessage.WORKING else message,
    )

    private fun buildTimeline(
        preferences: app.openhealth.android.preferences.Preferences,
    ): TimelineModel {
        val sourceRows = store.sources.all().associateBy { it.id.value }
        // A manual entry says "Entered by you" (`DESIGN.md` §7.2); the stored label is
        // data and is never shown untranslated.
        val sources = sourceRows.mapValues { (_, row) ->
            if (row.kind == SourceKind.MANUAL) manualSourceLabel() else row.label
        }
        val page = store.timeline.page(timelineLimit + 1)
        val hasMore = page.size > timelineLimit
        val all = page.take(timelineLimit.toInt()).mapNotNull { timelineRow(it, sources) }
        val withImport = all.map { row ->
            row.copy(
                imported = sourceRows[row.sourceId]?.kind != SourceKind.MANUAL,
            )
        }
        val visible = if (preferences.weightNeutral) {
            withImport.filterNot { it.sensitive }
        } else {
            withImport
        }
        val filtered = visible.filter(::matchesFilter)
        val days = filtered
            .groupBy { it.provenance.happenedAt.toLocalDateTime(zone).date }
            .toSortedMap(compareByDescending { it })
            .map { (date, rows) -> TimelineDayModel(date, rows) }
        return TimelineModel(
            days = days,
            filter = filter,
            total = filtered.size,
            hasMore = hasMore,
        )
    }

    private fun matchesFilter(row: TimelineRowModel): Boolean = when (filter) {
        TimelineFilter.ALL -> true
        TimelineFilter.NOTES -> row.kind == TimelineKind.NOTE
        TimelineFilter.BODY ->
            row.kind == TimelineKind.WEIGHT ||
                row.kind == TimelineKind.MEASUREMENT ||
                row.kind == TimelineKind.PHOTO
        TimelineFilter.NUTRITION ->
            row.kind == TimelineKind.WATER ||
                row.kind == TimelineKind.NUTRITION ||
                row.kind == TimelineKind.MEAL ||
                row.kind == TimelineKind.FAST ||
                row.kind == TimelineKind.ENTRY
        TimelineFilter.MANUAL -> !row.imported
        // Training rows are their own filter once B9 adds one; today they ride ALL.
        TimelineFilter.IMPORTED -> row.imported
    }

    private fun timelineRow(
        item: app.openhealth.data.repo.TimelineItem,
        sources: Map<String, String>,
    ): TimelineRowModel? = when (item.kind) {
        "note" -> noteTimelineRow(item, sources)
        "observation" -> observationTimelineRow(item, sources)
        "measurement" -> measurementTimelineRow(item, sources)
        "vital" -> vitalTimelineRow(item, sources)
        "body_photo" -> photoTimelineRow(item, sources)
        "meal" -> mealTimelineRow(item, sources)
        "fast" -> fastTimelineRow(item, sources)
        "entry" -> entryTimelineRow(item, sources)
        "session" -> sessionTimelineRow(item, sources)
        "work_set" -> setTimelineRow(item, sources)
        else -> null
    }

    private fun noteTimelineRow(
        item: app.openhealth.data.repo.TimelineItem,
        sources: Map<String, String>,
    ): TimelineRowModel? = store.notes.byId(NoteId(item.entityId))?.let { note ->
        val source = sources[note.source_id] ?: unknownSource()
        TimelineRowModel(
            kind = TimelineKind.NOTE,
            entityId = note.id,
            sourceId = note.source_id.orEmpty(),
            title = note.body,
            detail = source,
            valueText = null,
            provenance = RowProvenance(
                sourceLabel = source,
                happenedAt = Instant.fromEpochMilliseconds(note.happened_at),
                recordedAt = Instant.fromEpochMilliseconds(note.recorded_at),
                method = null,
                device = note.device,
                edited = note.edited_at != null,
                localDay = note.local_date,
                zoneId = note.zone_id,
                rawRecord = note.raw_record_id != null,
            ),
            deletable = true,
        )
    }

    private fun observationTimelineRow(
        item: app.openhealth.data.repo.TimelineItem,
        sources: Map<String, String>,
    ): TimelineRowModel? = store.observations.byId(ObservationId(item.entityId))?.let { obs ->
        val source = sources[obs.provenance.sourceId.value] ?: unknownSource()
        TimelineRowModel(
            sensitive = obs.type in SENSITIVE_TYPES,
            kind = timelineKind(obs.type),
            entityId = obs.id.value,
            title = humanize(obs.type.name),
            detail = source,
            valueText = formatDisplay(obs.value.value, obs.value.unit.ucum),
            provenance = RowProvenance(
                sourceLabel = source,
                happenedAt = obs.provenance.happenedAt,
                recordedAt = obs.provenance.recordedAt,
                method = obs.provenance.method?.name,
                device = obs.provenance.device,
                edited = obs.userEdited,
                localDay = obs.localDate.toString(),
                zoneId = obs.provenance.zone.id,
                quality = obs.quality.label.name,
                rawRecord = obs.provenance.rawRecordId != null,
            ),
            deletable = true,
            excludable = true,
            excluded = obs.excludedFromTrends,
            sourceId = obs.provenance.sourceId.value,
        )
    }

    private fun measurementTimelineRow(
        item: app.openhealth.data.repo.TimelineItem,
        sources: Map<String, String>,
    ): TimelineRowModel? = store.body.measurementById(item.entityId)?.let { row ->
        val source = sources[row.source_id] ?: unknownSource()
        TimelineRowModel(
            sensitive = row.type in SENSITIVE_TYPE_NAMES,
            sourceId = row.source_id,
            kind = TimelineKind.MEASUREMENT,
            entityId = row.id,
            title = humanize(row.type),
            detail = source,
            valueText = formatDisplay(row.value_, row.unit),
            provenance = RowProvenance(
                sourceLabel = source,
                happenedAt = Instant.fromEpochMilliseconds(row.happened_at),
                recordedAt = Instant.fromEpochMilliseconds(row.recorded_at),
                method = row.method,
                device = row.device,
                edited = false,
                localDay = row.local_date,
                zoneId = row.zone_id,
                rawRecord = row.raw_record_id != null,
            ),
            deletable = true,
        )
    }

    private fun photoTimelineRow(
        item: app.openhealth.data.repo.TimelineItem,
        sources: Map<String, String>,
    ): TimelineRowModel? = store.body.photoTimeline(item.entityId)?.let { photo ->
        val source = sources[photo.source_id] ?: unknownSource()
        TimelineRowModel(
            kind = TimelineKind.PHOTO,
            entityId = photo.id,
            sourceId = photo.source_id,
            title = photo.caption ?: container.context.getString(R.string.photo_timeline_title),
            detail = source,
            valueText = null,
            provenance = RowProvenance(
                sourceLabel = source,
                happenedAt = Instant.fromEpochMilliseconds(photo.happened_at),
                recordedAt = Instant.fromEpochMilliseconds(photo.recorded_at),
                method = null,
                device = photo.device,
                edited = photo.revision > 1,
                localDay = photo.local_date,
                zoneId = photo.zone_id,
                rawRecord = false,
            ),
            deletable = true,
            sensitive = true,
        )
    }

    private fun vitalTimelineRow(
        item: app.openhealth.data.repo.TimelineItem,
        sources: Map<String, String>,
    ): TimelineRowModel? = store.body.vitalById(item.entityId)?.let { row ->
        val source = sources[row.source_id] ?: unknownSource()
        TimelineRowModel(
            kind = TimelineKind.VITAL,
            entityId = row.id,
            sourceId = row.source_id,
            title = humanize(row.type),
            detail = source,
            valueText = formatDisplay(row.value_, row.unit),
            provenance = RowProvenance(
                sourceLabel = source,
                happenedAt = Instant.fromEpochMilliseconds(row.happened_at),
                recordedAt = Instant.fromEpochMilliseconds(row.recorded_at),
                method = row.method,
                device = row.device,
                edited = false,
                localDay = row.local_date,
                zoneId = row.zone_id,
                rawRecord = row.raw_record_id != null,
            ),
            deletable = true,
        )
    }

    private fun mealTimelineRow(
        item: app.openhealth.data.repo.TimelineItem,
        sources: Map<String, String>,
    ): TimelineRowModel? = store.nutrition.mealById(item.entityId)?.let { meal ->
        val source = meal.sourceId?.let { sources[it] }
            ?: container.context.getString(UiR.string.timeline_not_recorded)
        TimelineRowModel(
            kind = TimelineKind.MEAL,
            entityId = meal.id,
            sourceId = meal.sourceId.orEmpty(),
            title = meal.name.ifBlank { humanize(meal.kind.key) },
            detail = source,
            valueText = null,
            provenance = RowProvenance(
                sourceLabel = source,
                happenedAt = meal.happenedAt ?: Instant.fromEpochMilliseconds(meal.createdAt),
                recordedAt = meal.recordedAt
                    ?: Instant.fromEpochMilliseconds(meal.createdAt),
                method = null,
                device = null,
                edited = false,
            ),
            deletable = false,
        )
    }

    private fun entryTimelineRow(
        item: app.openhealth.data.repo.TimelineItem,
        sources: Map<String, String>,
    ): TimelineRowModel? = store.nutrition.entryById(item.entityId)?.let { entry ->
        val source = entry.sourceId?.let { sources[it] } ?: unknownSource()
        TimelineRowModel(
            kind = TimelineKind.ENTRY,
            entityId = entry.id,
            sourceId = entry.sourceId.orEmpty(),
            title = entry.foodName ?: entry.foodId
                ?: container.context.getString(UiR.string.timeline_diary_entry),
            detail = source,
            valueText = entry.totals.of(app.openhealth.core.nutrition.Nutrient.ENERGY)
                ?.let { withUnit(it, "kcal") },
            provenance = RowProvenance(
                sourceLabel = source,
                happenedAt = entry.happenedAt,
                recordedAt = entry.recordedAt,
                method = null,
                device = entry.device,
                edited = entry.edited,
            ),
            deletable = true,
            sensitive = true,
        )
    }

    private fun fastTimelineRow(
        item: app.openhealth.data.repo.TimelineItem,
        sources: Map<String, String>,
    ): TimelineRowModel? = store.nutrition.fastById(item.entityId)?.let { fast ->
        TimelineRowModel(
            kind = TimelineKind.FAST,
            entityId = fast.id,
            sourceId = "manual",
            title = container.context.getString(UiR.string.nutrition_fasting),
            detail = container.context.getString(UiR.string.timeline_fast_detail),
            valueText = null,
            provenance = RowProvenance(
                sourceLabel = sources["manual"] ?: unknownSource(),
                happenedAt = fast.window.startEpochMillis.let(Instant::fromEpochMilliseconds),
                recordedAt = fast.recordedAt,
                method = null,
                device = null,
                edited = false,
            ),
            deletable = false,
        )
    }

    private fun setTimelineRow(
        item: app.openhealth.data.repo.TimelineItem,
        sources: Map<String, String>,
    ): TimelineRowModel? = store.training.setById(item.entityId)?.let { set ->
        val source = sources[set.source_id] ?: unknownSource()
        val exercise = store.training.exerciseById(set.exercise_id)?.name ?: set.exercise_id
        TimelineRowModel(
            kind = TimelineKind.SET,
            entityId = set.id,
            sourceId = set.source_id,
            title = exercise,
            detail = source,
            valueText = "${format(set.weight_kg)} kg × ${set.reps}",
            provenance = RowProvenance(
                sourceLabel = source,
                happenedAt = Instant.fromEpochMilliseconds(set.happened_at),
                recordedAt = Instant.fromEpochMilliseconds(set.recorded_at),
                method = null,
                device = set.device,
                edited = set.user_edited,
                localDay = LocalDate.parse(
                    store.training.sessionById(set.session_id)?.local_date
                        ?: item.localDate.toString(),
                ).toString(),
                zoneId = store.training.sessionById(set.session_id)?.zone_id
                    ?: zone.id,
                rawRecord = set.raw_record_id != null,
            ),
            deletable = true,
        )
    }

    private fun sessionTimelineRow(
        item: app.openhealth.data.repo.TimelineItem,
        sources: Map<String, String>,
    ): TimelineRowModel {
        val source = sources[item.sourceId] ?: unknownSource()
        val session = store.training.sessionById(item.entityId)
        return TimelineRowModel(
            kind = TimelineKind.SESSION,
            entityId = item.entityId,
            sourceId = item.sourceId,
            title = session?.title
                ?: container.context.getString(UiR.string.timeline_workout_session),
            detail = source,
            valueText = null,
            provenance = RowProvenance(
                sourceLabel = source,
                happenedAt = Instant.fromEpochMilliseconds(item.happenedAt),
                recordedAt = Instant.fromEpochMilliseconds(item.recordedAt),
                method = null,
                device = null,
                edited = false,
            ),
            deletable = true,
        )
    }

    private fun buildTrash(
        preferences: app.openhealth.android.preferences.Preferences,
    ): List<TrashItemModel> = store.trash.active()
        .filterNot { preferences.weightNeutral && store.trash.isSensitive(it) }
        .map { row ->
            val kind = when (row.entity_table) {
                TrashEntity.NOTE.table -> TrashKind.NOTE
                TrashEntity.OBSERVATION.table -> TrashKind.OBSERVATION
                TrashEntity.MEASUREMENT.table -> TrashKind.MEASUREMENT
                TrashEntity.VITAL.table -> TrashKind.VITAL
                TrashEntity.PHOTO.table -> TrashKind.PHOTO
                TrashEntity.ENTRY.table -> TrashKind.ENTRY
                TrashEntity.WORK_SET.table -> TrashKind.SET
                TrashEntity.SESSION.table -> TrashKind.SESSION
                TrashEntity.ROUTINE_EXERCISE.table -> TrashKind.ROUTINE_EXERCISE
                else -> TrashKind.OBSERVATION
            }
            TrashItemModel(
                id = row.id,
                kind = kind,
                detail = store.trash.preview(row),
                deletedLabel = Instant.fromEpochMilliseconds(row.deleted_at)
                    .toLocalDateTime(zone).date.toString(),
            )
        }

    // ----------------------------------------------------------------------- helpers

    private fun newQuickAdd(container: AppContainer): QuickAddUi {
        val local = container.clock.now().toLocalDateTime(container.zone)
        return QuickAddUi(whenDate = local.date, whenHour = local.hour)
    }

    private fun showToast(
        messageRes: Int,
        undo: Boolean,
        argument: Int? = null,
        second: Int? = null,
    ) {
        toastJob?.cancel()
        _state.update {
            it.copy(
                toast = AppToast(messageRes, undo, argument, second),
                sessionUndo = null,
            )
        }
        toastJob = launchGuarded {
            delay(if (undo) UNDO_TOAST_MS else PLAIN_TOAST_MS)
            _state.update { it.copy(toast = null) }
        }
    }

    private fun chosenInstant(date: LocalDate, hour: Int): Instant {
        val instant = now()
        val local = instant.toLocalDateTime(zone)
        if (date == local.date && hour >= local.hour) return instant
        return LocalDateTime(date, LocalTime(hour.coerceIn(0, LAST_HOUR), 0)).toInstant(zone)
    }

    private fun writeObservation(
        type: ObservationType,
        value: Double,
        happenedAt: Instant,
        id: String = container.ids.newId(),
        method: MeasurementMethod = container.manualMethod(),
    ) {
        val provenance = provenance(happenedAt, now(), method)
        store.observations.record(
            Observation.of(
                id = ObservationId(id),
                type = type,
                value = Measure(value, type.canonicalUnit),
                start = happenedAt,
                provenance = provenance,
            ),
        )
    }

    /**
     * One part of a day's total: a glass of water, not the day's figure. It is stored as
     * a moment with an end so the rollup sums the parts of this source, which keeps the
     * day right whatever order the parts were logged in and whichever one is deleted.
     */
    private fun writeIncrement(
        type: ObservationType,
        value: Double,
        happenedAt: Instant,
        id: String = container.ids.newId(),
    ) {
        store.observations.record(
            Observation(
                id = ObservationId(id),
                type = type,
                value = Measure(value, type.canonicalUnit),
                start = happenedAt,
                end = happenedAt,
                provenance = provenance(happenedAt, now(), container.manualMethod()),
            ),
        )
    }

    private fun writeMeasurement(
        type: String,
        value: Double,
        unit: String,
        happenedAt: Instant,
        id: String = container.ids.newId(),
    ) {
        val recorded = now()
        store.body.upsertMeasurement(
            Measurement(
                id = id,
                type = type,
                value_ = value,
                unit = unit,
                local_date = happenedAt.toLocalDateTime(zone).date.toString(),
                zone_id = zone.id,
                happened_at = happenedAt.toEpochMilliseconds(),
                recorded_at = recorded.toEpochMilliseconds(),
                source_id = AppContainer.MANUAL_SOURCE.value,
                method = container.manualMethod().name,
                notes = null,
                excluded_from_trends = false,
                observation_id = null,
                raw_record_id = null,
                idempotency_key = container.ids.newId(),
                device = "android",
                app_version = container.versionName,
                revision = 1L,
            ),
        )
    }

    private fun noteRow(id: String, body: String, at: Instant): Note {
        val recorded = now()
        return Note(
            id = id,
            body = body,
            local_date = at.toLocalDateTime(zone).date.toString(),
            zone_id = zone.id,
            happened_at = at.toEpochMilliseconds(),
            recorded_at = recorded.toEpochMilliseconds(),
            edited_at = null,
            source_id = AppContainer.MANUAL_SOURCE.value,
            raw_record_id = null,
            idempotency_key = container.ids.newId(),
            device = "android",
            app_version = container.versionName,
            revision = 1L,
        )
    }

    private fun provenance(
        happenedAt: Instant,
        recordedAt: Instant,
        method: MeasurementMethod,
    ): Provenance = Provenance(
        sourceId = AppContainer.MANUAL_SOURCE,
        time = TimeProvenance.of(happenedAt, recordedAt, zone),
        idempotencyKey = container.ids.newId(),
        device = "android",
        appVersion = container.versionName,
        method = method,
    )

    private fun profileUnits(): UnitSystem =
        UnitSystem.fromKey(_state.value.profile?.units) ?: UnitSystem.METRIC

    private fun timelineKind(type: ObservationType): TimelineKind = when (type) {
        ObservationType.WATER -> TimelineKind.WATER
        ObservationType.WEIGHT -> TimelineKind.WEIGHT
        ObservationType.NUTRITION_ENERGY,
        ObservationType.NUTRITION_PROTEIN,
        ObservationType.NUTRITION_CARBS,
        ObservationType.NUTRITION_FAT,
        ObservationType.NUTRITION_FIBRE,
        -> TimelineKind.NUTRITION
        else -> TimelineKind.OBSERVATION
    }

    fun loadMoreTimeline() {
        timelineLimit += TIMELINE_PAGE
        refresh()
    }

    /** Value and unit are joined by a non-breaking space, never concatenated (§11.7). */
    private fun formatDisplay(value: Double, unit: String): String {
        val units = UnitSystem.fromKey(_state.value.profile?.units) ?: UnitSystem.METRIC
        if (units == UnitSystem.IMPERIAL) {
            return when (unit) {
                UcumUnit.KILOGRAM.ucum -> withUnit(
                    Measure(value, UcumUnit.KILOGRAM).convertTo(UcumUnit.POUND).value,
                    "lb",
                )
                UcumUnit.CENTIMETRE.ucum -> withUnit(
                    Measure(value, UcumUnit.CENTIMETRE).convertTo(UcumUnit.INCH).value,
                    "in",
                )
                else -> withUnit(value, displayUnit(unit))
            }
        }
        return withUnit(value, displayUnit(unit))
    }

    private fun withUnit(value: Double, unit: String): String =
        if (unit.isBlank()) format(value) else format(value) + NBSP + unit

    private fun displayUnit(unit: String): String = when (unit) {
        "[lb_av]" -> "lb"
        "[in_i]" -> "in"
        "mm[Hg]" -> "mmHg"
        "{step}" -> "steps"
        "{score}" -> ""
        "/min" -> "bpm"
        else -> unit
    }

    private fun goalLabel(goal: String): String = when (ProfileGoal.fromKey(goal)) {
        ProfileGoal.LOSE_FAT -> container.context.getString(UiR.string.goal_lose_fat)
        ProfileGoal.MAINTAIN -> container.context.getString(UiR.string.goal_maintain)
        ProfileGoal.GAIN_MUSCLE -> container.context.getString(UiR.string.goal_gain_muscle)
        ProfileGoal.IMPROVE_HEALTH -> container.context.getString(UiR.string.goal_improve_health)
        ProfileGoal.MANAGE_CONDITION ->
            container.context.getString(UiR.string.goal_manage_condition)
        ProfileGoal.JUST_TRACK -> container.context.getString(UiR.string.goal_just_track)
        null -> goal
    }

    private fun unitLabel(units: UnitSystem): String = container.context.getString(
        if (units ==
            UnitSystem.IMPERIAL
        ) {
            UiR.string.onb_units_imperial
        } else {
            UiR.string.onb_units_metric
        },
    )

    private fun displayLength(cm: Double, units: UnitSystem): String = format(
        if (units == UnitSystem.IMPERIAL) {
            Measure(cm, UcumUnit.CENTIMETRE).convertTo(UcumUnit.INCH).value
        } else {
            cm
        },
    )

    private fun displayMass(kg: Double, units: UnitSystem): String = format(
        if (units == UnitSystem.IMPERIAL) {
            Measure(kg, UcumUnit.KILOGRAM).convertTo(UcumUnit.POUND).value
        } else {
            kg
        },
    )

    private fun manualSourceLabel(): String =
        container.context.getString(UiR.string.timeline_source_manual)

    private fun unknownSource(): String =
        container.context.getString(UiR.string.timeline_unknown_source)

    private fun parseDate(text: String): LocalDate? =
        runCatching { LocalDate.parse(text.trim()) }.getOrNull()

    private fun kg(text: String, units: UnitSystem): Double? {
        val typed = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
        val value = if (units == UnitSystem.IMPERIAL) {
            Measure(typed, UcumUnit.POUND).convertTo(UcumUnit.KILOGRAM).value
        } else {
            typed
        }
        return value.takeIf { it in MIN_WEIGHT_KG..MAX_WEIGHT_KG }
    }

    private fun cm(text: String, units: UnitSystem): Double? {
        val typed = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
        val value = if (units == UnitSystem.IMPERIAL) {
            Measure(typed, UcumUnit.INCH).convertTo(UcumUnit.CENTIMETRE).value
        } else {
            typed
        }
        return value.takeIf { it in MIN_HEIGHT_CM..MAX_HEIGHT_CM }
    }

    private fun tape(text: String, units: UnitSystem): Double? {
        val typed = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
        val value = if (units == UnitSystem.IMPERIAL) {
            Measure(typed, UcumUnit.INCH).convertTo(UcumUnit.CENTIMETRE).value
        } else {
            typed
        }
        return value.takeIf { it in MIN_TAPE_CM..MAX_TAPE_CM }
    }

    /**
     * The locale's separators, at most one decimal, no trailing zero, and a true minus
     * sign rather than a hyphen (`DESIGN.md` §8.1, §11.7).
     */
    private fun format(value: Double): String = java.text.NumberFormat
        .getNumberInstance(Locale.getDefault())
        .apply { maximumFractionDigits = 1 }
        .format(value)
        .replace('-', MINUS)

    private fun humanize(key: String): String = key.lowercase(Locale.getDefault())
        .split('_')
        .joinToString(" ") { part ->
            part.replaceFirstChar { it.uppercase(Locale.getDefault()) }
        }

    companion object {
        const val DEMO_PROFILE = "profile-demo"
        private const val KCAL_PER_KG = 7_700.0
        private const val DAYS_PER_WEEK = 7.0
        private const val MIN_STEP_GOAL = 1_000
        private const val DEMO_WEIGHT_KG = 82.0
        private const val DEMO_GOAL_DROP_KG = 4.0
        private const val FLAT_RATE_KG_PER_WEEK = 0.01
        private const val MAX_STEP_GOAL = 50_000
        private const val TIMELINE_PAGE = 200L
        private const val UNDO_TOAST_MS = 6_000L
        private const val PLAIN_TOAST_MS = 4_000L
        private const val MIN_WEIGHT_KG = 20.0
        private const val MAX_WEIGHT_KG = 500.0
        private const val MIN_HEIGHT_CM = 50.0
        private const val MAX_HEIGHT_CM = 260.0
        private const val MIN_TAPE_CM = 10.0
        private const val MAX_TAPE_CM = 250.0
        private const val MIN_BODY_FAT = 3.0
        private const val MAX_BODY_FAT = 70.0
        private const val TIER_ONE = 1L
        private const val TIER_TWO = 2L
        private const val TIER_THREE = 3L
        private const val MIN_HEART_RATE = 30.0
        private const val MAX_HEART_RATE = 220.0
        private const val MIN_SYSTOLIC = 60.0
        private const val MAX_SYSTOLIC = 260.0
        private const val MIN_DIASTOLIC = 30.0
        private const val MAX_DIASTOLIC = 180.0
        private const val RECENT_LIMIT = 3
        private const val LAST_HOUR = 23
        private const val NBSP = '\u00A0'
        private val RECOVERY_WINDOW = kotlin.time.Duration.parse("28d")
        private const val DEFAULT_RECOVERING_HOURS = 24.0
        private const val DEFAULT_EASING_HOURS = 48.0
        private const val DEFAULT_READY_HOURS = 72.0
        private val epochDate = kotlinx.datetime.LocalDate(1970, 1, 1)
        private const val MINUS = '\u2212'
        private val SENSITIVE_TYPES = setOf(
            ObservationType.WEIGHT,
            ObservationType.BODY_FAT_PERCENT,
            ObservationType.LEAN_MASS,
            ObservationType.NUTRITION_ENERGY,
            ObservationType.NUTRITION_PROTEIN,
            ObservationType.NUTRITION_CARBS,
            ObservationType.NUTRITION_FAT,
            ObservationType.NUTRITION_FIBRE,
        )
        private val SENSITIVE_TYPE_NAMES = setOf("WEIGHT", "BODY_FAT_PERCENT", "LEAN_MASS")

        fun detectUnits(): UnitSystem {
            val country = Locale.getDefault().country
            return if (country in
                setOf("US", "LR", "MM")
            ) {
                UnitSystem.IMPERIAL
            } else {
                UnitSystem.METRIC
            }
        }
    }
}

/** Creates the one shell state holder with Koin's container. */
class AppViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AppViewModel(container) as T
    }
}

private const val LIVE_TICK_MILLIS = 1_000L

/** The default meal by the time of day (`DESIGN.md` §14.3). */
private const val BREAKFAST_UNTIL = 10
private const val LUNCH_UNTIL = 15
private const val SNACKS_UNTIL = 17
private const val DINNER_UNTIL = 21
