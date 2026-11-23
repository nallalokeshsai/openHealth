package app.openhealth.android.ui

import android.app.Activity
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.openhealth.R
import app.openhealth.android.AppContainer
import app.openhealth.android.security.LockError
import app.openhealth.core.ids.NoteId
import app.openhealth.core.ids.ObservationId
import app.openhealth.core.ids.TombstoneId
import app.openhealth.core.observation.Observation
import app.openhealth.core.observation.ObservationType
import app.openhealth.core.observation.Provenance
import app.openhealth.core.observation.Quality
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
import app.openhealth.ui.screens.lock.LockMessage
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
import app.openhealth.ui.screens.today.Greeting
import app.openhealth.ui.screens.today.QuickAction
import app.openhealth.ui.screens.today.TodayModel
import app.openhealth.ui.screens.today.TodayStatus
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

/** The onboarding surface state: the current question, what was typed, and why it failed. */
data class OnboardingUi(
    val step: OnboardingStep = OnboardingStep.Welcome,
    val answers: OnboardingAnswers,
    val issue: OnboardingIssue? = null,
    val ageBlocked: Boolean = false,
    val weightLossAllowed: Boolean = true,
    val tier: Int = 1,
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
) {
    val canSave: Boolean
        get() = when (mode) {
            QuickAddMode.NOTE -> note.isNotBlank()
            QuickAddMode.WATER -> waterMl > 0
            QuickAddMode.WEIGHT -> weight.toDoubleOrNull()?.let { it > 0 } == true
            else -> false
        }
}

data class AppToast(val messageRes: Int, val undo: Boolean)

/** One undo slot for the session; the tombstone in Trash is the durable promise. */
private sealed interface UndoAction {
    data class Added(val entity: TrashEntity, val id: String) : UndoAction
    data class Deleted(val tombstone: TombstoneId) : UndoAction
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
    private val store = container.store
    private val zone: TimeZone = container.zone
    private val now: () -> Instant = { container.clock.now() }

    private val _state = MutableStateFlow(
        AppState(
            preferences = container.preferences.snapshot(),
            onboarding = OnboardingUi(answers = OnboardingAnswers(units = detectedUnits)),
            quickAdd = newQuickAdd(container),
        ),
    )
    val state: StateFlow<AppState> = _state.asStateFlow()

    private var filter: TimelineFilter = TimelineFilter.ALL
    private var message: SettingsMessage? = null
    private var busy = false
    private var undoAction: UndoAction? = null
    private var toastJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { rebuild() }
    }

    /** Stops the state holder's coroutines; tests call this before closing the store. */
    fun stop() {
        viewModelScope.cancel()
    }

    private suspend fun rebuild() = withContext(Dispatchers.IO) {
        val preferences = container.preferences.snapshot()
        val profile = store.profile.active()
        val complete = preferences.onboardingComplete && profile != null
        val today = profile?.let { buildToday(preferences, it) }
        val settings = buildSettings(preferences, profile)
        val timeline = buildTimeline()
        val trash = buildTrash()
        _state.update { previous ->
            val lockOnFirstLoad = !previous.loaded && complete && preferences.appLock
            previous.copy(
                loaded = true,
                onboardingComplete = complete,
                preferences = preferences,
                profile = profile,
                today = today,
                settings = settings,
                timeline = timeline,
                trash = trash,
                locked = previous.locked || lockOnFirstLoad,
            )
        }
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
        chest = convertLength(answers.chest, from, to),
        waist = convertLength(answers.waist, from, to),
        hips = convertLength(answers.hips, from, to),
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
        val validation = validate(current.step, current.answers)
        if (validation.issue != null || validation.ageBlocked) {
            _state.update {
                it.copy(
                    onboarding = it.onboarding.copy(
                        issue = validation.issue,
                        ageBlocked = validation.ageBlocked,
                        weightLossAllowed = validation.weightLossAllowed,
                        answers = validation.answers,
                    ),
                )
            }
            return
        }
        if (current.step == OnboardingStep.Finish) {
            persistOnboarding(validation.answers)
            return
        }
        val next = nextStep(current.step, validation.answers)
        _state.update {
            it.copy(
                onboarding = it.onboarding.copy(
                    step = next,
                    answers = validation.answers,
                    issue = null,
                    ageBlocked = false,
                    weightLossAllowed = validation.weightLossAllowed,
                    tier = maxOf(it.onboarding.tier, next.tier),
                ),
            )
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
        viewModelScope.launch {
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
                    container.preferences.markOnboardingComplete(tier = 3)
                }
            }.onFailure {
                message = SettingsMessage.DEMO_FAILED
            }
            rebuild()
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
            OnboardingStep.GoalWeight -> validateGoalWeight(answers)
            else -> Validated(answers)
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
        val systolic = answers.systolic.toDoubleOrNull()
        val diastolic = answers.diastolic.toDoubleOrNull()
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
        val value = text.toDoubleOrNull()
        return if (value == null || value !in min..max) {
            OnboardingIssue.VALUE_OUT_OF_RANGE
        } else {
            null
        }
    }

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

    private fun visibleSteps(answers: OnboardingAnswers): List<OnboardingStep> =
        OnboardingStep.entries.filter { step ->
            when (step) {
                OnboardingStep.BodyFat, OnboardingStep.GoalWeight -> !answers.weightNeutral
                else -> true
            }
        }

    private fun persistOnboarding(answers: OnboardingAnswers) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { writeOnboarding(answers) }
            }.onFailure {
                message = SettingsMessage.PROFILE_INVALID
            }
            rebuild()
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
            intake_tier = _state.value.onboarding.tier.toLong(),
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
        preferences.setPreset(answers.preset)
        preferences.setModules(answers.preset.enabled)
        preferences.markOnboardingComplete(_state.value.onboarding.tier)

        val happened =
            chosenInstant(now().toLocalDateTime(zone).date, now().toLocalDateTime(zone).hour)
        writeOnboardingMeasurements(answers, happened)
    }

    /** The tier-3 answers that become observations or measurements, each with provenance. */
    private fun writeOnboardingMeasurements(answers: OnboardingAnswers, happened: Instant) {
        writeObservation(
            ObservationType.WEIGHT,
            weightKgValue(answers),
            happened,
        )
        if (!answers.weightNeutral) {
            answers.bodyFat.toDoubleOrNull()?.let {
                writeMeasurement(BODY_FAT, it, UcumUnit.PERCENT.ucum, happened)
            }
            kg(answers.goalWeight, answers.units)?.let {
                store.settings.put(SettingKeys.GOAL_WEIGHT_KG, it.toString())
            }
            parseDate(answers.goalDate)?.let {
                store.settings.put(SettingKeys.GOAL_TARGET_DATE, it.toString())
            }
        }
        answers.restingHeartRate.toDoubleOrNull()?.let {
            writeObservation(ObservationType.RESTING_HEART_RATE, it, happened)
        }
        answers.systolic.toDoubleOrNull()?.let {
            writeObservation(ObservationType.BLOOD_PRESSURE_SYSTOLIC, it, happened)
        }
        answers.diastolic.toDoubleOrNull()?.let {
            writeObservation(ObservationType.BLOOD_PRESSURE_DIASTOLIC, it, happened)
        }
        tape(answers.neck, answers.units)?.let { writeMeasurement("neck", it, "cm", happened) }
        tape(answers.chest, answers.units)?.let { writeMeasurement("chest", it, "cm", happened) }
        tape(answers.waist, answers.units)?.let { writeMeasurement("waist", it, "cm", happened) }
        tape(answers.hips, answers.units)?.let { writeMeasurement("hips", it, "cm", happened) }
    }

    private fun weightKgValue(answers: OnboardingAnswers): Double =
        kg(answers.weight, answers.units) ?: error("validated weight went missing")

    // -------------------------------------------------------------------- quick add

    fun openQuickAdd(action: QuickAction?) {
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
        _state.update {
            it.copy(quickAdd = current.copy(visible = true, mode = mode, note = "", weight = ""))
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

    fun onQuickAddWhen(date: LocalDate, hour: Int) {
        _state.update { it.copy(quickAdd = it.quickAdd.copy(whenDate = date, whenHour = hour)) }
    }

    fun saveQuickAdd() {
        val quick = _state.value.quickAdd
        if (!quick.canSave) return
        viewModelScope.launch {
            val earned: Pair<UndoAction, Int>? = runCatching<Pair<UndoAction, Int>?> {
                withContext(Dispatchers.IO) { appendQuick(quick) }
            }.getOrNull()
            if (earned != null) {
                undoAction = earned.first
                message = null
                showToast(earned.second, undo = true)
                _state.update {
                    it.copy(
                        quickAdd = newQuickAdd(container).copy(visible = false),
                    )
                }
                rebuild()
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
            writeObservation(
                type = ObservationType.WATER,
                value = quick.waterMl.toDouble(),
                happenedAt = chosenInstant(quick.whenDate, quick.whenHour),
                asInterval = true,
                id = id,
            )
            UndoAction.Added(TrashEntity.OBSERVATION, id) to R.string.toast_water_added
        }
        QuickAddMode.WEIGHT -> {
            val typed = kg(quick.weight, profileUnits())
            if (typed == null) {
                null
            } else {
                val id = container.ids.newId()
                writeMeasurement(
                    type = "WEIGHT",
                    value = typed,
                    unit = UcumUnit.KILOGRAM.ucum,
                    happenedAt = chosenInstant(quick.whenDate, quick.whenHour),
                    id = id,
                )
                UndoAction.Added(TrashEntity.MEASUREMENT, id) to R.string.toast_weight_added
            }
        }
        else -> null
    }

    fun repeat(recent: QuickAddRecent) {
        // A recent row logs in one tap: the same amount, now.
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                when (recent.action) {
                    QuickAction.WATER -> {
                        val amount =
                            recent.detail.substringBefore(" ").toDoubleOrNull()
                                ?: return@withContext
                        val id = container.ids.newId()
                        writeObservation(
                            ObservationType.WATER,
                            amount,
                            now(),
                            asInterval = true,
                            id = id,
                        )
                        undoAction = UndoAction.Added(TrashEntity.OBSERVATION, id)
                    }
                    QuickAction.WEIGHT -> {
                        val amount = kg(recent.detail.substringBefore(" "), profileUnits())
                            ?: return@withContext
                        val id = container.ids.newId()
                        writeMeasurement(
                            "WEIGHT",
                            amount,
                            UcumUnit.KILOGRAM.ucum,
                            now(),
                            id,
                        )
                        undoAction = UndoAction.Added(TrashEntity.MEASUREMENT, id)
                    }
                    else -> return@withContext
                }
            }
            showToast(R.string.toast_saved, undo = true)
            rebuild()
        }
    }

    // -------------------------------------------------------------------- mutations

    fun deleteTimelineRow(row: TimelineRowModel) {
        if (!row.deletable) return
        viewModelScope.launch {
            val tombstone = withContext(Dispatchers.IO) {
                when (row.kind) {
                    TimelineKind.NOTE -> store.trash.deleteNote(NoteId(row.entityId))
                    TimelineKind.WATER, TimelineKind.OBSERVATION ->
                        store.trash.deleteObservation(ObservationId(row.entityId))
                    TimelineKind.WEIGHT, TimelineKind.MEASUREMENT ->
                        store.trash.deleteMeasurement(
                            app.openhealth.core.ids.MeasurementId(row.entityId),
                        )
                    TimelineKind.VITAL ->
                        store.trash.deleteVital(app.openhealth.core.ids.VitalId(row.entityId))
                    else -> null
                }
            }
            if (tombstone != null) {
                undoAction = UndoAction.Deleted(tombstone)
                showToast(R.string.toast_deleted, undo = true)
                rebuild()
            }
        }
    }

    fun undoLast() {
        val action = undoAction ?: return
        viewModelScope.launch {
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
                }
            }
            undoAction = null
            dismissToast()
            rebuild()
        }
    }

    fun dismissToast() {
        toastJob?.cancel()
        _state.update { it.copy(toast = null) }
    }

    fun toggleModule(module: HealthModule, enabled: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val current = _state.value.preferences.modules.toMutableSet()
                if (enabled) current += module else current -= module
                container.preferences.setModule(module, enabled)
            }
            rebuild()
        }
    }

    fun setPreset(preset: app.openhealth.core.preferences.ModulePreset) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                container.preferences.setPreset(preset)
                container.preferences.setModules(preset.enabled)
            }
            rebuild()
        }
    }

    fun setWeightNeutral(enabled: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.preferences.setWeightNeutral(enabled) }
            rebuild()
        }
    }

    fun setTheme(theme: ThemeChoice) {
        viewModelScope.launch {
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
        viewModelScope.launch {
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
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                container.preferences.setAppLock(enabled)
            }
            rebuild()
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
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val current = store.profile.active() ?: return@withContext false
                val birth = if (edit.birthDate.isBlank()) {
                    current.date_of_birth?.let(::parseDate)
                } else {
                    parseDate(edit.birthDate)
                }
                if (birth != null &&
                    !AgeRules.isSupported(birth, now().toLocalDateTime(zone).date)
                ) {
                    return@withContext false
                }
                val height = cm(edit.height, edit.units) ?: current.height_cm
                val weight = kg(edit.weight, edit.units) ?: current.weight_kg
                store.profile.upsert(
                    current.copy(
                        name = edit.name.trim().ifBlank { current.name },
                        date_of_birth = birth?.toString() ?: current.date_of_birth,
                        goal = edit.goal?.key ?: current.goal,
                        height_cm = height,
                        weight_kg = weight,
                        units = edit.units.key,
                        weight_neutral = current.weight_neutral,
                        updated_at = now().toEpochMilliseconds(),
                        revision = current.revision + 1L,
                    ),
                )
                store.settings.put(SettingKeys.UNITS, edit.units.key)
                true
            }
            message = if (ok) SettingsMessage.PROFILE_SAVED else SettingsMessage.PROFILE_INVALID
            rebuild()
        }
    }

    fun setTimelineFilter(value: TimelineFilter) {
        filter = value
        viewModelScope.launch { rebuild() }
    }

    fun restoreTombstone(item: TrashItemModel) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.trash.undo(TombstoneId(item.id)) }
            rebuild()
        }
    }

    fun purgeTombstone(item: TrashItemModel) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.trash.purge(TombstoneId(item.id)) }
            rebuild()
        }
    }

    fun deleteAllNotes() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                store.notes.latest(MAX_NOTES).forEach { store.trash.deleteNote(NoteId(it.id)) }
            }
            message = SettingsMessage.NOTES_DELETED
            rebuild()
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                store.trash.active().forEach { store.trash.purge(TombstoneId(it.id)) }
            }
            message = SettingsMessage.TRASH_EMPTIED
            rebuild()
        }
    }

    fun addSampleData() {
        val profile = _state.value.profile ?: return
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    check(store.observations.count() == 0L) { "not empty" }
                    container.addSampleData(AppContainer.DEMO_DAYS, profile.id)
                    container.preferences.setDemo(true)
                }
            }
            message = if (result.isSuccess) {
                SettingsMessage.DEMO_ADDED
            } else {
                SettingsMessage.DEMO_FAILED
            }
            rebuild()
        }
    }

    fun export(uri: Uri, passphrase: CharArray) {
        busy = true
        refresh()
        viewModelScope.launch {
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
        busy = true
        refresh()
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val bytes = container.documents.read(uri)
                    container.export.restore(bytes, passphrase)
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

    // ---------------------------------------------------------------------- builders

    private fun buildToday(
        preferences: app.openhealth.android.preferences.Preferences,
        profile: Profile,
    ): TodayModel {
        val date = now().toLocalDateTime(zone).date
        val rollups = store.rollups.forDay(date)
        fun value(type: ObservationType): Double? =
            rollups.firstOrNull { it.type == type }?.value?.value
        val notes = store.notes.forDay(date).size
        val water = value(ObservationType.WATER)
        val steps = value(ObservationType.STEPS)?.toInt()
        val energy = value(ObservationType.NUTRITION_ENERGY)
        val weight = value(ObservationType.WEIGHT) ?: profile.weight_kg
        val anything =
            notes > 0 || water != null || weight != null || steps != null || energy != null
        return TodayModel(
            today = date,
            greeting = greeting(now().toLocalDateTime(zone).hour),
            status = when {
                preferences.weightNeutral -> TodayStatus.WEIGHT_NEUTRAL
                anything -> TodayStatus.LOGGED_TODAY
                else -> TodayStatus.NOTHING_LOGGED
            },
            noteCount = notes,
            waterMl = water,
            steps = steps,
            energyKcal = energy,
            weightKg = weight,
            readinessScore = null,
            modules = preferences.modules,
            demo = preferences.demo,
        )
    }

    private fun buildSettings(
        preferences: app.openhealth.android.preferences.Preferences,
        profile: Profile?,
    ): SettingsState = SettingsState(
        profileName = profile?.name.orEmpty(),
        profileSubtitle = listOfNotNull(
            profile?.goal?.let(::humanize),
            profile?.units,
        ).joinToString(" · "),
        profileBirthDate = profile?.date_of_birth.orEmpty(),
        profileGoal = ProfileGoal.fromKey(profile?.goal),
        profileHeight = profile?.height_cm?.let { format(it) }.orEmpty(),
        profileWeight = profile?.weight_kg?.let { format(it) }.orEmpty(),
        profileUnits = UnitSystem.fromKey(profile?.units) ?: UnitSystem.METRIC,
        modules = preferences.modules,
        preset = preferences.preset,
        weightNeutral = preferences.weightNeutral,
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
        message = message,
    )

    private fun buildTimeline(): TimelineModel {
        val sources = store.sources.all().associate { it.id.value to it.label }
        val all = store.timeline.page(TIMELINE_PAGE).mapNotNull { timelineRow(it, sources) }
        val filtered = all.filter(::matchesFilter)
        val days = filtered
            .groupBy { it.provenance.happenedAt.toLocalDateTime(zone).date }
            .toSortedMap(compareByDescending { it })
            .map { (date, rows) -> TimelineDayModel(date, rows) }
        return TimelineModel(days = days, filter = filter, total = filtered.size)
    }

    private fun matchesFilter(row: TimelineRowModel): Boolean = when (filter) {
        TimelineFilter.ALL -> true
        TimelineFilter.NOTES -> row.kind == TimelineKind.NOTE
        TimelineFilter.BODY ->
            row.kind == TimelineKind.WEIGHT ||
                row.kind == TimelineKind.MEASUREMENT
        TimelineFilter.NUTRITION ->
            row.kind == TimelineKind.WATER ||
                row.kind == TimelineKind.NUTRITION ||
                row.kind == TimelineKind.MEAL
    }

    private fun timelineRow(
        item: app.openhealth.data.repo.TimelineItem,
        sources: Map<String, String>,
    ): TimelineRowModel? = when (item.kind) {
        "note" -> noteTimelineRow(item, sources)
        "observation" -> observationTimelineRow(item, sources)
        "measurement" -> measurementTimelineRow(item, sources)
        "vital" -> vitalTimelineRow(item, sources)
        "meal" -> mealTimelineRow(item)
        "session" -> sessionTimelineRow(item, sources)
        else -> null
    }

    private fun noteTimelineRow(
        item: app.openhealth.data.repo.TimelineItem,
        sources: Map<String, String>,
    ): TimelineRowModel? = store.notes.byId(NoteId(item.entityId))?.let { note ->
        val source = sources[note.source_id] ?: "Manual"
        TimelineRowModel(
            kind = TimelineKind.NOTE,
            entityId = note.id,
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
            ),
            deletable = true,
        )
    }

    private fun observationTimelineRow(
        item: app.openhealth.data.repo.TimelineItem,
        sources: Map<String, String>,
    ): TimelineRowModel? = store.observations.byId(ObservationId(item.entityId))?.let { obs ->
        val source = sources[obs.provenance.sourceId.value] ?: "Manual"
        TimelineRowModel(
            kind = timelineKind(obs.type),
            entityId = obs.id.value,
            title = humanize(obs.type.name),
            detail = source,
            valueText = format(obs.value.value) + " " + obs.value.unit.ucum,
            provenance = RowProvenance(
                sourceLabel = source,
                happenedAt = obs.provenance.happenedAt,
                recordedAt = obs.provenance.recordedAt,
                method = obs.provenance.method?.name,
                device = obs.provenance.device,
                edited = obs.userEdited,
            ),
            deletable = true,
        )
    }

    private fun measurementTimelineRow(
        item: app.openhealth.data.repo.TimelineItem,
        sources: Map<String, String>,
    ): TimelineRowModel? = store.body.measurementById(item.entityId)?.let { row ->
        val source = sources[row.source_id] ?: "Manual"
        TimelineRowModel(
            kind = TimelineKind.MEASUREMENT,
            entityId = row.id,
            title = humanize(row.type),
            detail = source,
            valueText = format(row.value_) + " " + row.unit,
            provenance = RowProvenance(
                sourceLabel = source,
                happenedAt = Instant.fromEpochMilliseconds(row.happened_at),
                recordedAt = Instant.fromEpochMilliseconds(row.recorded_at),
                method = row.method,
                device = row.device,
                edited = false,
            ),
            deletable = true,
        )
    }

    private fun vitalTimelineRow(
        item: app.openhealth.data.repo.TimelineItem,
        sources: Map<String, String>,
    ): TimelineRowModel? = store.body.vitalById(item.entityId)?.let { row ->
        val source = sources[row.source_id] ?: "Manual"
        TimelineRowModel(
            kind = TimelineKind.VITAL,
            entityId = row.id,
            title = humanize(row.type),
            detail = source,
            valueText = format(row.value_) + " " + row.unit,
            provenance = RowProvenance(
                sourceLabel = source,
                happenedAt = Instant.fromEpochMilliseconds(row.happened_at),
                recordedAt = Instant.fromEpochMilliseconds(row.recorded_at),
                method = row.method,
                device = row.device,
                edited = false,
            ),
            deletable = true,
        )
    }

    private fun mealTimelineRow(item: app.openhealth.data.repo.TimelineItem): TimelineRowModel? =
        store.nutrition.mealById(item.entityId)?.let { meal ->
            TimelineRowModel(
                kind = TimelineKind.MEAL,
                entityId = meal.id,
                title = meal.name ?: humanize(meal.kind),
                detail = "Manual",
                valueText = null,
                provenance = RowProvenance(
                    sourceLabel = "Manual",
                    happenedAt = Instant.fromEpochMilliseconds(meal.created_at),
                    recordedAt = Instant.fromEpochMilliseconds(meal.created_at),
                    method = null,
                    device = null,
                    edited = false,
                ),
                deletable = false,
            )
        }

    private fun sessionTimelineRow(
        item: app.openhealth.data.repo.TimelineItem,
        sources: Map<String, String>,
    ): TimelineRowModel {
        val source = sources[item.sourceId] ?: "Manual"
        return TimelineRowModel(
            kind = TimelineKind.SESSION,
            entityId = item.entityId,
            title = "Workout session",
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
            deletable = false,
        )
    }

    private fun buildTrash(): List<TrashItemModel> = store.trash.active().map { row ->
        val kind = when (row.entity_table) {
            TrashEntity.NOTE.table -> TrashKind.NOTE
            TrashEntity.OBSERVATION.table -> TrashKind.OBSERVATION
            TrashEntity.MEASUREMENT.table -> TrashKind.MEASUREMENT
            TrashEntity.VITAL.table -> TrashKind.VITAL
            TrashEntity.ENTRY.table -> TrashKind.ENTRY
            TrashEntity.WORK_SET.table -> TrashKind.SET
            else -> TrashKind.OBSERVATION
        }
        TrashItemModel(
            id = row.id,
            kind = kind,
            detail = row.entity_id.take(TRASH_DETAIL_LENGTH),
            deletedLabel = Instant.fromEpochMilliseconds(row.deleted_at)
                .toLocalDateTime(zone).date.toString(),
        )
    }

    // ----------------------------------------------------------------------- helpers

    private fun newQuickAdd(container: AppContainer): QuickAddUi {
        val local = container.clock.now().toLocalDateTime(container.zone)
        return QuickAddUi(whenDate = local.date, whenHour = local.hour)
    }

    private fun showToast(messageRes: Int, undo: Boolean) {
        toastJob?.cancel()
        _state.update { it.copy(toast = AppToast(messageRes, undo)) }
        toastJob = viewModelScope.launch {
            delay(if (undo) UNDO_TOAST_MS else PLAIN_TOAST_MS)
            _state.update { it.copy(toast = null) }
        }
    }

    private fun chosenInstant(date: LocalDate, hour: Int): Instant {
        val local = now().toLocalDateTime(zone)
        val safeHour = if (date == local.date) minOf(hour, local.hour) else hour
        return LocalDateTime(date, LocalTime(safeHour, 0)).toInstant(zone)
    }

    private fun writeObservation(
        type: ObservationType,
        value: Double,
        happenedAt: Instant,
        asInterval: Boolean = false,
        id: String = container.ids.newId(),
    ) {
        val recorded = now()
        val provenance = provenance(happenedAt, recorded)
        val observation = if (asInterval) {
            Observation(
                id = ObservationId(id),
                type = type,
                value = Measure(value, type.canonicalUnit),
                start = happenedAt,
                end = happenedAt,
                provenance = provenance,
                quality = Quality(),
            )
        } else {
            Observation.of(
                id = ObservationId(id),
                type = type,
                value = Measure(value, type.canonicalUnit),
                start = happenedAt,
                provenance = provenance,
            )
        }
        store.observations.record(observation)
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

    private fun provenance(happenedAt: Instant, recordedAt: Instant): Provenance = Provenance(
        sourceId = AppContainer.MANUAL_SOURCE,
        time = TimeProvenance.of(happenedAt, recordedAt, zone),
        idempotencyKey = container.ids.newId(),
        device = "android",
        appVersion = container.versionName,
        method = container.manualMethod(),
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

    private fun greeting(hour: Int): Greeting = when (hour) {
        in MORNING_FROM..MORNING_TO -> Greeting.MORNING
        in AFTERNOON_FROM..AFTERNOON_TO -> Greeting.AFTERNOON
        else -> Greeting.EVENING
    }

    private fun format(value: Double): String {
        val rounded = String.format(Locale.getDefault(), "%.1f", value)
        return if (rounded.endsWith(".0")) rounded.dropLast(2) else rounded
    }

    private fun humanize(key: String): String = key.lowercase(Locale.getDefault())
        .split('_')
        .joinToString(" ") { part ->
            part.replaceFirstChar { it.uppercase(Locale.getDefault()) }
        }

    companion object {
        const val DEMO_PROFILE = "profile-demo"
        private const val TIMELINE_PAGE = 200L
        private const val MAX_NOTES = 10_000L
        private const val UNDO_TOAST_MS = 6_000L
        private const val PLAIN_TOAST_MS = 4_000L
        private const val BODY_FAT = "BODY_FAT_PERCENT"
        private const val MIN_WEIGHT_KG = 20.0
        private const val MAX_WEIGHT_KG = 500.0
        private const val MIN_HEIGHT_CM = 50.0
        private const val MAX_HEIGHT_CM = 260.0
        private const val MIN_TAPE_CM = 10.0
        private const val MAX_TAPE_CM = 250.0
        private const val MIN_BODY_FAT = 3.0
        private const val MAX_BODY_FAT = 70.0
        private const val MIN_HEART_RATE = 30.0
        private const val MAX_HEART_RATE = 220.0
        private const val MIN_SYSTOLIC = 60.0
        private const val MAX_SYSTOLIC = 260.0
        private const val MIN_DIASTOLIC = 30.0
        private const val MAX_DIASTOLIC = 180.0
        private const val MORNING_FROM = 5
        private const val MORNING_TO = 11
        private const val AFTERNOON_FROM = 12
        private const val AFTERNOON_TO = 17
        private const val TRASH_DETAIL_LENGTH = 18

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
