package app.openhealth.android.ui

import android.app.Activity
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.openhealth.R
import app.openhealth.android.AppContainer
import app.openhealth.android.security.LockError
import app.openhealth.core.body.AdiposeInputs
import app.openhealth.core.body.AdiposeModel
import app.openhealth.core.body.BodySex
import app.openhealth.core.body.MuscleRegion
import app.openhealth.core.body.RecoveryMap
import app.openhealth.core.formula.RecoveryThresholds
import app.openhealth.core.ids.EntryId
import app.openhealth.core.ids.NoteId
import app.openhealth.core.ids.ObservationId
import app.openhealth.core.ids.TombstoneId
import app.openhealth.core.observation.MeasurementMethod
import app.openhealth.core.observation.Observation
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
    val fatal: Boolean = false,
    val sessionUndo: AppToast? = null,
    val recent: List<QuickAddRecent> = emptyList(),
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
        refresh()
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

    private suspend fun rebuild() = withContext(Dispatchers.IO) {
        val preferences = container.preferences.snapshot()
        val profile = store.profile.active()
        val complete = preferences.onboardingComplete && profile != null
        val today = profile?.let { buildToday(preferences, it) }
        val settings = buildSettings(preferences, profile)
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
                timeline = timeline,
                trash = trash,
                recent = recent,
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
            val tombstone = withContext(Dispatchers.IO) {
                when (row.kind) {
                    TimelineKind.NOTE -> store.trash.deleteNote(NoteId(row.entityId))
                    TimelineKind.WATER, TimelineKind.OBSERVATION ->
                        store.trash.deleteObservation(ObservationId(row.entityId))
                    TimelineKind.WEIGHT ->
                        store.trash.deleteObservation(ObservationId(row.entityId))
                    TimelineKind.MEASUREMENT ->
                        store.trash.deleteMeasurement(
                            app.openhealth.core.ids.MeasurementId(row.entityId),
                        )
                    TimelineKind.VITAL ->
                        store.trash.deleteVital(app.openhealth.core.ids.VitalId(row.entityId))
                    TimelineKind.ENTRY -> store.trash.deleteEntry(EntryId(row.entityId))
                    else -> null
                }
            }
            if (tombstone != null) {
                undoAction = UndoAction.Deleted(tombstone)
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
        val weight = value(ObservationType.WEIGHT)
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
            unitSystem = UnitSystem.fromKey(profile.units) ?: UnitSystem.METRIC,
            readinessScore = null,
            modules = preferences.modules,
            demo = preferences.demo,
            bodyMap = buildBodyMap(preferences, profile),
        )
    }

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
        val bodyFat = store.observations.latest(ObservationType.BODY_FAT_PERCENT)
        val tape = tapeMeasurements()
        val adipose = if (fatAllowed) {
            AdiposeModel.compute(
                AdiposeInputs(
                    sex = if (profile.sex_at_birth == "FEMALE") BodySex.FEMALE else BodySex.MALE,
                    bodyFatPercent = bodyFat?.value?.value,
                    heightCm = profile.height_cm,
                    tapeCm = tape,
                ),
            )
        } else {
            null
        }
        val provenance = if (adipose == null) {
            null
        } else {
            app.openhealth.ui.body.FatProvenance(
                bodyFatPercent = bodyFat?.value?.value,
                method = bodyFat?.provenance?.method,
                sourceLabel = bodyFat?.provenance?.sourceId
                    ?.let { store.sources.byId(it)?.label },
                tapeSites = tape.keys.toList(),
            )
        }
        return app.openhealth.ui.body.BodyMapModel.recovery(
            sex = if (profile.sex_at_birth == "FEMALE") BodySex.FEMALE else BodySex.MALE,
            states = states,
            adipose = adipose,
            fatProvenance = provenance,
        )
    }

    /** The newest reading of each tape site the adipose model uses (`DESIGN.md` §9.1.1). */
    private fun tapeMeasurements(): Map<String, Double> = MuscleRegion.entries
        .mapNotNull { region ->
            store.body.latestMeasurementOfType(region.measurementType)?.let { row ->
                region.measurementType to row.value_
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
                row.kind == TimelineKind.MEASUREMENT
        TimelineFilter.NUTRITION ->
            row.kind == TimelineKind.WATER ||
                row.kind == TimelineKind.NUTRITION ||
                row.kind == TimelineKind.MEAL ||
                row.kind == TimelineKind.ENTRY
        TimelineFilter.MANUAL -> !row.imported
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
        "meal" -> mealTimelineRow(item)
        "entry" -> entryTimelineRow(item, sources)
        "session" -> sessionTimelineRow(item, sources)
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

    private fun mealTimelineRow(item: app.openhealth.data.repo.TimelineItem): TimelineRowModel? =
        store.nutrition.mealById(item.entityId)?.let { meal ->
            // B2's meal table has no provenance columns yet; B6 adds them. Do not invent
            // a source in the meantime.
            val source = container.context.getString(UiR.string.timeline_not_recorded)
            TimelineRowModel(
                kind = TimelineKind.MEAL,
                entityId = meal.id,
                sourceId = "",
                title = meal.name.ifBlank { humanize(meal.kind) },
                detail = source,
                valueText = null,
                provenance = RowProvenance(
                    sourceLabel = source,
                    happenedAt = Instant.fromEpochMilliseconds(meal.created_at),
                    recordedAt = Instant.fromEpochMilliseconds(meal.created_at),
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
        val source = sources[entry.source_id] ?: unknownSource()
        TimelineRowModel(
            kind = TimelineKind.ENTRY,
            entityId = entry.id,
            sourceId = entry.source_id.orEmpty(),
            title = entry.food_id ?: container.context.getString(UiR.string.timeline_diary_entry),
            detail = source,
            valueText = entry.energy_kcal?.let { withUnit(it, "kcal") },
            provenance = RowProvenance(
                sourceLabel = source,
                happenedAt = Instant.fromEpochMilliseconds(entry.happened_at),
                recordedAt = Instant.fromEpochMilliseconds(entry.recorded_at),
                method = null,
                device = entry.device,
                edited = entry.user_edited,
            ),
            deletable = true,
            sensitive = true,
        )
    }

    private fun sessionTimelineRow(
        item: app.openhealth.data.repo.TimelineItem,
        sources: Map<String, String>,
    ): TimelineRowModel {
        val source = sources[item.sourceId] ?: unknownSource()
        return TimelineRowModel(
            kind = TimelineKind.SESSION,
            entityId = item.entityId,
            sourceId = item.sourceId,
            title = container.context.getString(UiR.string.timeline_workout_session),
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
                TrashEntity.ENTRY.table -> TrashKind.ENTRY
                TrashEntity.WORK_SET.table -> TrashKind.SET
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

    private fun showToast(messageRes: Int, undo: Boolean) {
        toastJob?.cancel()
        _state.update {
            it.copy(
                toast = AppToast(messageRes, undo),
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

    private fun greeting(hour: Int): Greeting = when (hour) {
        in NIGHT_FROM..NIGHT_TO -> Greeting.NIGHT
        in MORNING_FROM..MORNING_TO -> Greeting.MORNING
        in AFTERNOON_FROM..AFTERNOON_TO -> Greeting.AFTERNOON
        else -> Greeting.EVENING
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
        private const val NIGHT_FROM = 0
        private const val NIGHT_TO = 4
        private const val MORNING_FROM = 5
        private const val MORNING_TO = 11
        private const val AFTERNOON_FROM = 12
        private const val AFTERNOON_TO = 17
        private const val RECENT_LIMIT = 3
        private const val LAST_HOUR = 23
        private const val NBSP = '\u00A0'
        private val RECOVERY_WINDOW = kotlin.time.Duration.parse("28d")
        private const val DEFAULT_RECOVERING_HOURS = 24.0
        private const val DEFAULT_EASING_HOURS = 48.0
        private const val DEFAULT_READY_HOURS = 72.0
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
