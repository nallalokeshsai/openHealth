package app.openhealth.android.training

import app.openhealth.core.body.BodySex
import app.openhealth.core.body.LoadedSet
import app.openhealth.core.body.Muscle
import app.openhealth.core.body.RecoveryMap
import app.openhealth.core.exercise.Exercise as CatalogueExercise
import app.openhealth.core.exercise.PlanCoverage
import app.openhealth.core.exercise.PlannedExercise
import app.openhealth.core.formula.FormulaOutcome
import app.openhealth.core.formula.OneRepMax
import app.openhealth.core.formula.RecoveryThresholds
import app.openhealth.core.formula.SessionVolumeCalculator
import app.openhealth.core.formula.VolumeSet
import app.openhealth.core.ids.IdGenerator
import app.openhealth.core.ids.SourceId
import app.openhealth.core.ids.TombstoneId
import app.openhealth.core.preferences.HealthModule
import app.openhealth.core.preferences.UnitSystem
import app.openhealth.core.seam.ClockSeam
import app.openhealth.core.seam.RestTimerSeam
import app.openhealth.core.training.EquipmentProfile
import app.openhealth.core.training.LinearProgression
import app.openhealth.core.training.PerformedSet
import app.openhealth.core.training.PersonalRecordKind
import app.openhealth.core.training.PersonalRecords
import app.openhealth.core.training.ProgressionDecision
import app.openhealth.core.training.ProgressionInput
import app.openhealth.core.training.ProgressionPolicy
import app.openhealth.core.training.RoutinePlacement
import app.openhealth.core.training.RoutinePlanner
import app.openhealth.core.training.RoutineSchedule
import app.openhealth.core.training.SessionLoads
import app.openhealth.core.training.TrainingSetRecord
import app.openhealth.core.training.TrainingUnits
import app.openhealth.core.training.clockText
import app.openhealth.data.LocalStore
import app.openhealth.data.db.Profile
import app.openhealth.data.db.Routine
import app.openhealth.data.db.Session
import app.openhealth.data.db.Work_set
import app.openhealth.data.repo.SessionDetails
import app.openhealth.data.repo.SetValues
import app.openhealth.data.repo.SettingKeys
import app.openhealth.ui.body.BodyMapExtras
import app.openhealth.ui.body.BodyMapModel
import app.openhealth.ui.screens.train.EditorExerciseRow
import app.openhealth.ui.screens.train.ExerciseChoice
import app.openhealth.ui.screens.train.ExerciseHistoryModel
import app.openhealth.ui.screens.train.ExerciseHistoryRow
import app.openhealth.ui.screens.train.LiveExerciseModel
import app.openhealth.ui.screens.train.LiveSessionModel
import app.openhealth.ui.screens.train.LiveSetEdit
import app.openhealth.ui.screens.train.LiveSetKind
import app.openhealth.ui.screens.train.LiveSetRow
import app.openhealth.ui.screens.train.OpenSessionCard
import app.openhealth.ui.screens.train.RestDisplay
import app.openhealth.ui.screens.train.RoutineDetailModel
import app.openhealth.ui.screens.train.RoutineDraft
import app.openhealth.ui.screens.train.RoutineEditorModel
import app.openhealth.ui.screens.train.RoutineExerciseRow
import app.openhealth.ui.screens.train.SessionSummaryModel
import app.openhealth.ui.screens.train.TrainModel
import app.openhealth.ui.screens.train.TrainingDay
import app.openhealth.ui.screens.train.TrainingHistoryRow
import app.openhealth.ui.screens.train.TrainingPlanCard
import app.openhealth.ui.screens.train.TrainingRoutineRow
import java.util.Locale
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Builds every Train surface and performs every training write (`BUILD-ROADMAP.md` §B5).
 * The view model stays the one state holder; this class owns the training domain so the
 * shell does not grow another thousand lines. It reads storage only through [LocalStore],
 * so the rollups are rebuilt by the same engine as every other write.
 */
@Suppress("LargeClass", "TooManyFunctions", "LongParameterList")
class TrainingPresenter(
    private val store: LocalStore,
    private val clock: ClockSeam,
    private val zone: TimeZone,
    private val restTimer: RestTimerSeam,
    private val ids: IdGenerator,
    private val manualSource: SourceId,
    private val appVersion: String,
    private val freestyleTitle: String,
    private val pastTitle: String,
) {
    private var catalogueCache: List<CatalogueExercise>? = null

    private fun now(): Instant = clock.now()
    private fun today(): LocalDate = now().toLocalDateTime(zone).date

    fun equipment(): EquipmentProfile =
        EquipmentProfile.decode(store.settings.get(SettingKeys.TRAINING_EQUIPMENT))

    fun createCustomExercise(
        draft: app.openhealth.ui.screens.train.CustomExerciseDraft,
    ): ExerciseChoice {
        val primary = app.openhealth.core.body.Muscle.fromKey(draft.primary)
            ?: app.openhealth.core.body.Muscle.CHEST
        val id = "custom-${ids.newId()}"
        val at = now().toEpochMilliseconds()
        store.training.upsertExercise(
            app.openhealth.data.db.Exercise(
                id = id,
                name = draft.name.trim(),
                primary_muscles = primary.key,
                secondary_muscles = "",
                equipment = draft.equipment,
                instructions = draft.instructions.trim().ifBlank { null },
                level = "beginner",
                mechanic = "compound",
                force = null,
                met_activity = null,
                is_custom = true,
                source_id = manualSource.value,
                created_at = at,
                updated_at = at,
                revision = 1L,
            ),
        )
        catalogueCache = null
        return ExerciseChoice(id, draft.name.trim(), muscleLabel(primary))
    }

    fun saveEquipment(profile: EquipmentProfile) {
        store.settings.put(SettingKeys.TRAINING_EQUIPMENT, EquipmentProfile.encode(profile))
    }

    // ------------------------------------------------------------------ the root

    @Suppress("LongMethod")
    fun train(
        preferences: app.openhealth.android.preferences.Preferences,
        profile: Profile,
    ): TrainModel {
        val date = today()
        val instant = now()
        val week = RoutinePlanner.week(date)
        val weekEnd = week.last().plus(DatePeriod(days = 1))
        val sessions = store.training.sessionsBetween(week.first(), weekEnd)
        val sessionDays = sessions.map { LocalDate.parse(it.local_date) }.toSet()
        val routines = store.training.routines().filter { it.archived_at == null }
        val catalogue = catalogue()
        val last = store.training.lastFinishedSession()
        val plan = RoutinePlanner.today(
            routines.map { it.toPlacement() },
            last?.routine_id,
            date,
        )
        val routineRows = routines.map { routine ->
            val coverage = coverage(entriesOf(routine.id), catalogue)
            TrainingRoutineRow(
                id = routine.id,
                name = routine.name,
                schedule = routine.schedule(),
                weekday = routine.weekday?.toInt(),
                rotationIndex = routine.rotation_index?.toInt(),
                folder = routine.folder,
                setCount = entriesOf(routine.id).sumOf { it.target_sets ?: DEFAULT_SETS.toLong() }
                    .toInt(),
                favourite = routine.favourite,
                map = BodyMapModel.plan(sex(profile), coverage),
            )
        }
        val weekdayPlan = routines.any { it.weekday != null }
        val showcase = if (weekdayPlan) {
            routines.filter { it.weekday != null }
        } else {
            routines.filter { it.weekday == null }
        }
        val showcaseCoverage = if (routines.isEmpty()) {
            emptyMap()
        } else {
            PlanCoverage.compute(
                showcase.flatMap { routine ->
                    entriesOf(routine.id).mapNotNull { entry ->
                        catalogue[entry.exercise_id]?.let {
                            PlannedExercise(it, entry.target_sets?.toInt() ?: DEFAULT_SETS)
                        }
                    }
                },
            )
        }
        val open = store.training.openSession()
        return TrainModel(
            today = date,
            week = week.map { day ->
                TrainingDay(
                    date = day,
                    isToday = day == date,
                    hasSession = day in sessionDays,
                )
            },
            plan = plan?.let { chosen ->
                TrainingPlanCard(
                    routineId = chosen.routineId,
                    name = chosen.name,
                    reason = chosen.reason,
                    setCount = entriesOf(chosen.routineId)
                        .sumOf { it.target_sets ?: DEFAULT_SETS.toLong() }
                        .toInt(),
                    map = BodyMapModel.plan(
                        sex(profile),
                        coverage(entriesOf(chosen.routineId), catalogue),
                    ),
                )
            },
            recovery = recoveryMap(preferences, profile),
            planMap = BodyMapModel.plan(sex(profile), showcaseCoverage),
            planUsesCycle = !weekdayPlan,
            routines = routineRows,
            history = history(profile),
            openSession = open?.let {
                OpenSessionCard(
                    sessionId = it.id,
                    title = it.title ?: freestyleTitle,
                    startedAt = it.started_at,
                    setCount = store.training.countSetsForSession(it.id).toInt(),
                )
            },
            demo = preferences.demo,
        )
    }

    private fun recoveryMap(
        preferences: app.openhealth.android.preferences.Preferences,
        profile: Profile,
    ): BodyMapModel {
        val instant = now()
        val enabled = HealthModule.TRAINING in preferences.modules
        val sets = if (enabled) {
            store.training.loadedSets(
                (instant - RecoveryMap.HISTORY_DAYS.days).toEpochMilliseconds(),
            )
        } else {
            emptyList<LoadedSet>()
        }
        val states = RecoveryMap.compute(instant, sets, thresholds = thresholds())
        val labels = store.sources.all().associate { it.id.value to it.label }
        return BodyMapModel.recovery(
            sex = sex(profile),
            states = states,
            extras = BodyMapExtras(
                sexStated = profile.sex_at_birth == "MALE" || profile.sex_at_birth == "FEMALE",
                sourceLabels = labels,
            ),
        )
    }

    private fun history(profile: Profile): List<TrainingHistoryRow> =
        store.training.finishedSessions(HISTORY_ROWS).mapNotNull { session ->
            val sets = store.training.setsForSession(session.id)
            if (sets.isEmpty()) return@mapNotNull null
            val catalogue = catalogue()
            val performed = sets.mapNotNull { set ->
                catalogue[set.exercise_id]?.let { exercise ->
                    PerformedSet(
                        primary = exercise.primary,
                        secondary = exercise.secondary,
                        weightKg = set.weight_kg,
                        reps = set.reps.toInt(),
                        isWarmup = set.is_warmup,
                    )
                }
            }
            val loads = SessionLoads.byMuscle(performed)
            TrainingHistoryRow(
                sessionId = session.id,
                title = session.title,
                date = LocalDate.parse(session.local_date),
                sets = sets.count { !it.is_warmup },
                volumeLabel = volumeOrNull(sets, profile)?.let { volume ->
                    "${shown(volume, profile)} ${unit(profile)}"
                },
                map = BodyMapModel.plan(sex(profile), loads),
            )
        }

    // ------------------------------------------------------------------ a routine

    fun routine(id: String, profile: Profile): RoutineDetailModel? {
        val routine = store.training.routineById(id) ?: return null
        val catalogue = catalogue()
        val rows = entriesOf(id).map { entry ->
            val exercise = catalogue[entry.exercise_id]
            RoutineExerciseRow(
                entryId = entry.id,
                exerciseId = entry.exercise_id,
                name = exercise?.name ?: exerciseName(entry.exercise_id),
                muscles = muscleNames(exercise),
                target = targetLabel(
                    entry.target_sets ?: DEFAULT_SETS.toLong(),
                    entry.target_reps,
                    entry.target_reps_high,
                    entry.target_weight_kg,
                    profile,
                ),
                rest = entry.rest_seconds?.let { "$it s" },
                groupIndex = entry.group_index?.toInt(),
                notes = entry.notes,
            )
        }
        val lastTrained = store.training.sessionsForRoutine(id).firstOrNull()
            ?.local_date?.let(LocalDate::parse)
        return RoutineDetailModel(
            id = routine.id,
            name = routine.name,
            folder = routine.folder,
            schedule = routine.schedule(),
            weekday = routine.weekday?.toInt(),
            rotationIndex = routine.rotation_index?.toInt(),
            progressionKey = routine.progression ?: LinearProgression.key,
            notes = routine.notes,
            favourite = routine.favourite,
            map = BodyMapModel.plan(sex(profile), coverage(entriesOf(id), catalogue)),
            weeklySets = entriesOf(id).sumOf { it.target_sets ?: DEFAULT_SETS.toLong() }.toDouble(),
            weightUnit = unit(profile),
            exercises = rows,
            lastTrained = lastTrained,
        )
    }

    fun editor(id: String?, profile: Profile?): RoutineEditorModel {
        val routine = id?.let { store.training.routineById(it) }
        val catalogue = catalogue()
        val rows = id?.let { routineId ->
            entriesOf(routineId).map { entry ->
                val exercise = catalogue[entry.exercise_id]
                EditorExerciseRow(
                    id = entry.id,
                    exerciseId = entry.exercise_id,
                    name = exercise?.name ?: exerciseName(entry.exercise_id),
                    muscles = muscleNames(exercise),
                    sets = (entry.target_sets ?: DEFAULT_SETS.toLong()).toString(),
                    reps = repsLabel(entry.target_reps, entry.target_reps_high),
                    weight = entry.target_weight_kg?.let { shown(it, profile) }.orEmpty(),
                    rest = entry.rest_seconds?.toString().orEmpty(),
                )
            }
        }.orEmpty()
        return RoutineEditorModel(
            id = routine?.id,
            name = routine?.name.orEmpty(),
            folder = routine?.folder.orEmpty(),
            schedule = routine?.schedule() ?: RoutineSchedule.WEEKDAY,
            weekday = routine?.weekday?.toInt() ?: today().dayOfWeek.isoDayNumber,
            rotationIndex = routine?.rotation_index?.toInt() ?: 1,
            favourite = routine?.favourite ?: false,
            progressionKey = routine?.progression ?: LinearProgression.key,
            notes = routine?.notes.orEmpty(),
            exercises = rows,
            library = catalogue.values.map { ExerciseChoice(it.id, it.name, muscleNames(it)) },
            weightUnit = unit(profile),
        )
    }

    /** Creates or updates one routine and replaces its exercise rows in one transaction. */
    @Suppress("LongMethod")
    fun saveRoutine(draft: RoutineDraft): String {
        val now = now()
        val id = draft.id ?: ids.newId()
        val existing = draft.id?.let { store.training.routineById(it) }
        store.db.transaction {
            store.training.upsertRoutine(
                Routine(
                    id = id,
                    name = draft.name,
                    folder = draft.folder,
                    notes = draft.notes,
                    archived_at = null,
                    created_at = existing?.created_at ?: now.toEpochMilliseconds(),
                    updated_at = now.toEpochMilliseconds(),
                    source_id = manualSource.value,
                    revision = (existing?.revision ?: 0L) + 1L,
                    weekday = if (draft.schedule == RoutineSchedule.WEEKDAY) {
                        draft.weekday.toLong()
                    } else {
                        null
                    },
                    rotation_index = if (draft.schedule == RoutineSchedule.ROTATION) {
                        draft.rotationIndex.toLong()
                    } else {
                        null
                    },
                    favourite = draft.favourite,
                    progression = draft.progressionKey,
                    target_rest_seconds = null,
                ),
            )
            val keep = mutableSetOf<String>()
            draft.exercises.forEachIndexed { index, row ->
                val entryId = if (row.id.startsWith("new-")) ids.newId() else row.id
                keep += entryId
                val (low, high) = parseReps(row.reps)
                store.training.upsertRoutineExercise(
                    app.openhealth.data.db.Routine_exercise(
                        id = entryId,
                        routine_id = id,
                        exercise_id = row.exerciseId,
                        position = index.toLong(),
                        target_sets = row.sets.toLongOrNull()?.coerceIn(1L, MAX_SETS.toLong())
                            ?: DEFAULT_SETS.toLong(),
                        target_reps = low.toLong(),
                        target_reps_high = high.toLong(),
                        target_weight_kg = row.weight.replace(',', '.').toDoubleOrNull()
                            ?.let { entered(it, store.profile.active()) },
                        notes = null,
                        target_rir = null,
                        rest_seconds = row.rest.toLongOrNull(),
                        progression = null,
                        group_index = null,
                    ),
                )
            }
            entriesOf(id).filterNot { it.id in keep }.forEach {
                store.trash.deleteRoutineExercise(it.id)
            }
        }
        catalogueCache = null
        return id
    }

    fun archiveRoutine(id: String) {
        val routine = store.training.routineById(id) ?: return
        store.training.upsertRoutine(
            routine.copy(
                archived_at = now().toEpochMilliseconds(),
                updated_at = now().toEpochMilliseconds(),
                revision = routine.revision + 1,
            ),
        )
    }

    fun toggleFavourite(id: String) {
        val routine = store.training.routineById(id) ?: return
        store.training.upsertRoutine(
            routine.copy(
                favourite = !routine.favourite,
                updated_at = now().toEpochMilliseconds(),
                revision = routine.revision + 1,
            ),
        )
    }

    // ------------------------------------------------------------------ a session

    fun startSession(routineId: String?): String {
        store.training.openSession()?.let { return it.id }
        val instant = now()
        val date = instant.toLocalDateTime(zone).date
        val routine = routineId?.let { store.training.routineById(it) }
        val sessionId = ids.newId()
        store.training.upsertSession(
            Session(
                id = sessionId,
                started_at = instant.toEpochMilliseconds(),
                ended_at = null,
                local_date = date.toString(),
                zone_id = zone.id,
                happened_at = instant.toEpochMilliseconds(),
                recorded_at = instant.toEpochMilliseconds(),
                routine_id = routine?.id,
                programme_id = null,
                notes = null,
                source_id = manualSource.value,
                idempotency_key = "session-${ids.newId()}",
                observation_id = null,
                raw_record_id = null,
                device = "manual",
                app_version = appVersion,
                revision = 1,
                updated_at = instant.toEpochMilliseconds(),
                title = routine?.name ?: freestyleTitle,
                feeling = null,
                perceived_effort = null,
            ),
        )
        routine?.let { row ->
            entriesOf(row.id).forEach { entry ->
                store.training.upsertSessionExercise(
                    app.openhealth.data.db.Session_exercise(
                        id = ids.newId(),
                        session_id = sessionId,
                        exercise_id = entry.exercise_id,
                        position = entry.position,
                        notes = entry.notes,
                        group_index = entry.group_index,
                        target_sets = entry.target_sets,
                        target_reps = entry.target_reps,
                        target_reps_high = entry.target_reps_high,
                        target_weight_kg = entry.target_weight_kg,
                        progression = entry.progression ?: row.progression,
                        rest_seconds = entry.rest_seconds,
                    ),
                )
            }
        }
        return sessionId
    }

    /** Starts a workout on a past day; sets are filed on that day when they are logged. */
    fun startPastSession(date: LocalDate): String {
        store.training.openSession()?.let { return it.id }
        val instant = now()
        val start = LocalDateTime(date, instant.toLocalDateTime(zone).time).toInstant(zone)
        val sessionId = ids.newId()
        store.training.upsertSession(
            Session(
                id = sessionId,
                started_at = start.toEpochMilliseconds(),
                ended_at = null,
                local_date = date.toString(),
                zone_id = zone.id,
                happened_at = start.toEpochMilliseconds(),
                recorded_at = instant.toEpochMilliseconds(),
                routine_id = null,
                programme_id = null,
                notes = null,
                source_id = manualSource.value,
                idempotency_key = "session-${ids.newId()}",
                observation_id = null,
                raw_record_id = null,
                device = "manual",
                app_version = appVersion,
                revision = 1,
                updated_at = instant.toEpochMilliseconds(),
                title = pastTitle,
                feeling = null,
                perceived_effort = null,
            ),
        )
        return sessionId
    }

    fun addExercise(sessionId: String, exerciseId: String) {
        val session = store.training.sessionById(sessionId) ?: return
        val already = store.training.sessionExercises(sessionId).any {
            it.exercise_id == exerciseId
        }
        if (already) return
        val position =
            (store.training.sessionExercises(sessionId).maxOfOrNull { it.position } ?: -1) + 1
        store.training.upsertSessionExercise(
            app.openhealth.data.db.Session_exercise(
                id = ids.newId(),
                session_id = sessionId,
                exercise_id = exerciseId,
                position = position,
                notes = null,
                group_index = null,
                target_sets = DEFAULT_SETS.toLong(),
                target_reps = DEFAULT_REPS.toLong(),
                target_reps_high = DEFAULT_REPS_HIGH.toLong(),
                target_weight_kg = null,
                progression = session.routine_id
                    ?.let { store.training.routineById(it)?.progression },
                rest_seconds = session.routine_id
                    ?.let { store.training.routineById(it)?.target_rest_seconds },
            ),
        )
    }

    /** Logs a completed set and starts the rest timer unless a superset partner comes next. */
    @Suppress("LongMethod", "ReturnCount")
    fun logSet(
        sessionId: String,
        logged: app.openhealth.ui.screens.train.LoggedSet,
    ): List<PersonalRecordKind> {
        val exerciseId = logged.exerciseId
        val session = store.training.sessionById(sessionId) ?: return emptyList()
        val entry = ensureSessionExercise(session, exerciseId) ?: return emptyList()
        val profile = store.profile.active()
        val kg = logged.weight.replace(',', '.').toDoubleOrNull()
            ?.let { entered(it, profile) } ?: 0.0
        val count = logged.reps.toIntOrNull() ?: return emptyList()
        val bodyweight = store.training.exerciseById(exerciseId)?.equipment == "bodyweight"
        val recorded = now()
        val position =
            (store.training.setsForSession(sessionId).maxOfOrNull { it.position } ?: -1) + 1
        val sessionDate = LocalDate.parse(session.local_date)
        val happened = if (sessionDate != today()) {
            Instant.fromEpochMilliseconds(
                session.started_at + position * PAST_SET_SPACING_MILLIS,
            )
        } else {
            recorded
        }
        val history = store.training.setsForExercise(exerciseId)
            .filterNot { it.is_warmup }
            .map { set ->
                TrainingSetRecord(
                    weightKg = set.weight_kg,
                    reps = set.reps.toInt(),
                    isWarmup = false,
                    isBodyweight = set.is_bodyweight,
                )
            }
        val candidate = TrainingSetRecord(
            weightKg = kg,
            reps = count,
            isWarmup = logged.kind == LiveSetKind.WARMUP,
            isBodyweight = false,
        )
        val records = PersonalRecords.detect(history, candidate)
        store.training.upsertSet(
            Work_set(
                id = ids.newId(),
                session_id = sessionId,
                session_exercise_id = entry.id,
                exercise_id = exerciseId,
                position = position,
                weight_kg = kg,
                reps = count.toLong(),
                rir = logged.rir?.toDoubleOrNull(),
                rpe = null,
                is_warmup = logged.kind == LiveSetKind.WARMUP,
                is_backoff = logged.kind == LiveSetKind.BACKOFF,
                is_failure = logged.kind == LiveSetKind.FAILURE,
                is_bodyweight = bodyweight,
                tempo = null,
                rest_seconds = entry.rest_seconds,
                notes = logged.notes,
                completed_at = happened.toEpochMilliseconds(),
                happened_at = happened.toEpochMilliseconds(),
                recorded_at = recorded.toEpochMilliseconds(),
                source_id = manualSource.value,
                idempotency_key = "set-${ids.newId()}",
                observation_id = null,
                raw_record_id = null,
                device = "manual",
                app_version = appVersion,
                revision = 1,
                is_drop = logged.kind == LiveSetKind.DROP,
                is_assisted = false,
                assistance_kg = null,
                band_kg = null,
                original_weight_kg = null,
                original_reps = null,
                edit_reason = null,
                edited_at = null,
                user_edited = false,
            ),
        )
        startRestIfDue(sessionId, entry)
        return records.map { it.kind }
    }

    fun updateSet(setId: String, edit: LiveSetEdit) {
        val profile = store.profile.active()
        val kg = edit.weight.replace(',', '.').toDoubleOrNull()
            ?.let { entered(it, profile) } ?: return
        val reps = edit.reps.toLongOrNull() ?: return
        store.training.updateSet(
            setId,
            SetValues(
                weightKg = kg,
                reps = reps,
                rir = edit.rir?.toDoubleOrNull(),
                rpe = null,
                isWarmup = edit.kind == LiveSetKind.WARMUP,
                isBackoff = edit.kind == LiveSetKind.BACKOFF,
                isFailure = edit.kind == LiveSetKind.FAILURE,
                isBodyweight = edit.isBodyweight,
                isDrop = edit.kind == LiveSetKind.DROP,
                isAssisted = edit.isAssisted,
                assistanceKg = edit.assistanceKg.replace(',', '.').toDoubleOrNull(),
                bandKg = edit.bandKg.replace(',', '.').toDoubleOrNull(),
                tempo = edit.tempo.ifBlank { null },
                restSeconds = null,
                notes = edit.notes.ifBlank { null },
                reason = edit.reason.ifBlank { null },
                editedAt = now().toEpochMilliseconds(),
            ),
        )
    }

    fun deleteSet(id: String): TombstoneId? =
        store.trash.deleteWorkSet(app.openhealth.core.ids.SetId(id))

    fun setExerciseNotes(sessionId: String, exerciseId: String, notes: String) {
        val entry = store.training.sessionExercises(sessionId)
            .firstOrNull { it.exercise_id == exerciseId } ?: return
        store.db.trainingQueries.updateSessionExerciseNotes(notes.ifBlank { null }, entry.id)
    }

    @Suppress("ReturnCount")
    fun superset(sessionId: String, exerciseId: String, withNext: Boolean) {
        val entries = store.training.sessionExercises(sessionId).sortedBy { it.position }
        val index = entries.indexOfFirst { it.exercise_id == exerciseId }
        if (index < 0) return
        val current = entries[index]
        val next = entries.getOrNull(index + 1)
        if (!withNext) {
            store.db.trainingQueries.updateSessionExerciseGroup(null, current.id)
            if (next != null && next.group_index == current.group_index) {
                store.db.trainingQueries.updateSessionExerciseGroup(null, next.id)
            }
            return
        }
        if (next == null) return
        val group = current.group_index
            ?: ((entries.mapNotNull { it.group_index }.maxOrNull() ?: 0L) + 1)
        store.db.trainingQueries.updateSessionExerciseGroup(group, current.id)
        store.db.trainingQueries.updateSessionExerciseGroup(group, next.id)
    }

    fun finishSession(sessionId: String) {
        val session = store.training.sessionById(sessionId)
        val at = now()
        val end = if (session != null && LocalDate.parse(session.local_date) != today()) {
            // A backfilled workout happened then: its end is after its last set, not
            // whenever the person finished typing.
            val lastSet = store.training.setsForSession(sessionId)
                .maxOfOrNull { it.completed_at } ?: session.started_at
            lastSet + BACKFILL_END_SPACING_MILLIS
        } else {
            at.toEpochMilliseconds()
        }
        store.training.finishSession(sessionId, end, at.toEpochMilliseconds())
        restTimer.skip()
    }

    fun moveSession(sessionId: String, newDate: LocalDate) {
        val session = store.training.sessionById(sessionId) ?: return
        val old = Instant.fromEpochMilliseconds(session.started_at).toLocalDateTime(zone)
        val start = LocalDateTime(newDate, old.time).toInstant(zone)
        store.training.moveSession(
            sessionId,
            start.toEpochMilliseconds(),
            zone,
            now().toEpochMilliseconds(),
        )
    }

    fun deleteSession(sessionId: String): TombstoneId? {
        restTimer.skip()
        return store.trash.deleteSession(sessionId)
    }

    fun setSessionNote(sessionId: String, note: String) {
        store.training.updateSessionNotes(
            sessionId,
            note.ifBlank { null },
            now().toEpochMilliseconds(),
        )
    }

    fun saveSummary(sessionId: String, feeling: String, note: String) {
        store.training.updateSessionDetails(
            sessionId,
            SessionDetails(
                title = null,
                notes = note.ifBlank { null },
                feeling = feeling.ifBlank { null },
                perceivedEffort = null,
            ),
            endedAt = store.training.sessionById(sessionId)?.ended_at,
            nowMillis = now().toEpochMilliseconds(),
        )
    }

    // ------------------------------------------------------------------ the live model

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    fun live(sessionId: String, profile: Profile): LiveSessionModel? {
        val session = store.training.sessionById(sessionId) ?: return null
        val catalogue = catalogue()
        val equipment = equipment()
        val entries = store.training.sessionExercises(sessionId).sortedBy { it.position }
        val exercises = entries.map { entry ->
            val exercise = catalogue[entry.exercise_id]
            val sets = store.training.setsForSession(sessionId)
                .filter { it.exercise_id == entry.exercise_id }
                .sortedBy { it.position }
            val allSets = store.training.setsForExercise(entry.exercise_id)
                .sortedBy { it.completed_at }
            val targetLow = entry.target_reps?.toInt() ?: DEFAULT_REPS
            val targetHigh = entry.target_reps_high?.toInt() ?: targetLow
            val decision = decision(
                DecisionRequest(
                    exerciseId = entry.exercise_id,
                    sessionId = sessionId,
                    targetSets = entry.target_sets?.toInt() ?: DEFAULT_SETS,
                    targetLow = targetLow,
                    targetHigh = targetHigh,
                    policyKey = entry.progression
                        ?: session.routine_id?.let { store.training.routineById(it)?.progression },
                    incrementKg = incrementFor(exercise, equipment),
                ),
            )
            val last = store.training.lastWorkSetForExercise(entry.exercise_id)
            val prefillWeight = decision.weightKg
                ?: last?.weight_kg
                ?: entry.target_weight_kg
                ?: 0.0
            val prefillReps = decision.repsLow
            var warmup = 0
            var work = 0
            val rows = sets.map { set ->
                val label = if (set.is_warmup) {
                    warmup += 1
                    "W$warmup"
                } else {
                    work += 1
                    work.toString()
                }
                val before = allSets
                    .filter { it.completed_at < set.completed_at }
                    .map {
                        TrainingSetRecord(
                            it.weight_kg,
                            it.reps.toInt(),
                            it.is_warmup,
                            it.is_bodyweight,
                        )
                    }
                val prs = PersonalRecords.detect(
                    before,
                    TrainingSetRecord(
                        set.weight_kg,
                        set.reps.toInt(),
                        set.is_warmup,
                        set.is_bodyweight,
                    ),
                ).map { it.kind }
                LiveSetRow(
                    id = set.id,
                    label = label,
                    weight = shown(set.weight_kg, profile),
                    reps = set.reps.toString(),
                    rir = set.rir?.let { trimNumber(it) },
                    kind = kindOf(set),
                    done = true,
                    prKinds = prs,
                    tempo = set.tempo,
                    notes = set.notes,
                    isBodyweight = set.is_bodyweight,
                    isAssisted = set.is_assisted,
                    assistanceKg = set.assistance_kg?.let { shown(it, profile) }.orEmpty(),
                    bandKg = set.band_kg?.let { shown(it, profile) }.orEmpty(),
                    sourceLabel = store.sources
                        .byId(app.openhealth.core.ids.SourceId(set.source_id))?.label,
                    recordedAt = set.recorded_at,
                )
            } + LiveSetRow(
                id = null,
                label = if (work == 0 && warmup == 0) "1" else (work + 1).toString(),
                weight = shown(prefillWeight, profile),
                reps = prefillReps.toString(),
                rir = null,
                kind = LiveSetKind.WORK,
                done = false,
            )
            LiveExerciseModel(
                id = entry.id,
                exerciseId = entry.exercise_id,
                name = exercise?.name ?: exerciseName(entry.exercise_id),
                equipmentKey = exercise?.equipment?.key ?: "other",
                muscles = muscleNames(exercise),
                target = (entry.target_sets ?: DEFAULT_SETS.toLong()).toString() + " × " +
                    repsLabel(entry.target_reps, entry.target_reps_high),
                lastTime = last?.let {
                    "${shown(it.weight_kg, profile)} ${unit(profile)} × ${it.reps}"
                },
                progressionKey = ProgressionPolicy.fromKey(entry.progression).key,
                incrementKg = incrementFor(exercise, equipment),
                action = decision.action,
                progressionReason = decision.reason,
                nextWeight = shown(decision.weightKg ?: prefillWeight, profile),
                nextReps = decision.repsLow.toString(),
                notes = entry.notes,
                groupIndex = entry.group_index?.toInt(),
                remainingSets = (
                    (entry.target_sets ?: DEFAULT_SETS.toLong()).toInt() -
                        rows.count { it.done && it.kind != LiveSetKind.WARMUP } - 1
                    )
                    .coerceAtLeast(0),
                sets = rows,
            )
        }
        val rest = restTimer.snapshot(now().toEpochMilliseconds())
        val performed = exercises.flatMap { exercise ->
            val catalogueExercise = catalogue[exercise.exerciseId]
            if (catalogueExercise == null) {
                emptyList()
            } else {
                exercise.sets.filter { it.done }.map { set ->
                    PerformedSet(
                        primary = catalogueExercise.primary,
                        secondary = catalogueExercise.secondary,
                        weightKg = set.weight.replace(',', '.').toDoubleOrNull() ?: 0.0,
                        reps = set.reps.toIntOrNull() ?: 0,
                        isWarmup = set.kind == LiveSetKind.WARMUP,
                    )
                }
            }
        }
        return LiveSessionModel(
            sessionId = session.id,
            title = session.title ?: freestyleTitle,
            startedAt = session.started_at,
            localDate = LocalDate.parse(session.local_date),
            backdated = LocalDate.parse(session.local_date) != today(),
            exercises = exercises,
            weightUnit = unit(profile),
            map = BodyMapModel.plan(sex(profile), SessionLoads.byMuscle(performed)),
            rest = rest?.let {
                RestDisplay(
                    remainingSeconds = it.remainingSeconds,
                    totalSeconds = it.totalSeconds,
                    paused = it.paused,
                )
            },
            editing = session.ended_at != null,
            note = session.notes.orEmpty(),
            feeling = session.feeling.orEmpty(),
            library = catalogue.values.map { ExerciseChoice(it.id, it.name, muscleNames(it)) },
            equipment = equipment,
        )
    }

    @Suppress("LongMethod")
    fun summary(sessionId: String, profile: Profile): SessionSummaryModel? {
        val session = store.training.sessionById(sessionId) ?: return null
        val sets = store.training.setsForSession(sessionId)
        val catalogue = catalogue()
        val performed = sets.mapNotNull { set ->
            catalogue[set.exercise_id]?.let {
                PerformedSet(
                    it.primary,
                    it.secondary,
                    set.weight_kg,
                    set.reps.toInt(),
                    set.is_warmup,
                )
            }
        }
        val loads = SessionLoads.byMuscle(performed)
        val top = SessionLoads.topMuscles(performed)
        val prs = mutableListOf<app.openhealth.ui.screens.train.SummaryRecord>()
        sets.forEach { set ->
            val before = store.training.setsForExercise(set.exercise_id)
                .filter { it.completed_at < set.completed_at }
                .map {
                    TrainingSetRecord(it.weight_kg, it.reps.toInt(), it.is_warmup, it.is_bodyweight)
                }
            PersonalRecords.detect(
                before,
                TrainingSetRecord(
                    set.weight_kg,
                    set.reps.toInt(),
                    set.is_warmup,
                    set.is_bodyweight,
                ),
            ).forEach { record ->
                if (prs.none { it.kind == record.kind }) {
                    prs += app.openhealth.ui.screens.train.SummaryRecord(
                        kind = record.kind,
                        value = "${shown(
                            record.weightKg,
                            profile,
                        )} ${unit(profile)} × ${record.reps}",
                    )
                }
            }
        }
        val volume = volumeOrNull(sets, profile)
        return SessionSummaryModel(
            sessionId = session.id,
            title = session.title ?: freestyleTitle,
            date = LocalDate.parse(session.local_date),
            duration = clockText(
                ((session.ended_at ?: session.started_at) - session.started_at) /
                    MILLIS_PER_SECOND,
            ),
            durationSeconds = ((session.ended_at ?: session.started_at) - session.started_at) /
                MILLIS_PER_SECOND,
            volume = volume?.let { "${shown(it, profile)} ${unit(profile)}" },
            weightUnit = unit(profile),
            sets = sets.count { !it.is_warmup }.toString(),
            prs = prs,
            map = BodyMapModel.plan(sex(profile), loads),
            topMuscles = top,
            feeling = session.feeling.orEmpty(),
            note = session.notes.orEmpty(),
            sourceLabel = store.sources.byId(app.openhealth.core.ids.SourceId(session.source_id))
                ?.label ?: session.source_id,
            happenedAt = Instant.fromEpochMilliseconds(session.happened_at),
            recordedAt = Instant.fromEpochMilliseconds(session.recorded_at),
            localDay = LocalDate.parse(session.local_date),
            zoneId = session.zone_id,
            device = session.device,
        )
    }

    fun exerciseHistory(exerciseId: String, profile: Profile): ExerciseHistoryModel {
        val exercise = catalogue()[exerciseId]
        val summary = store.training.exerciseSessionSummary(exerciseId)
        val last = summary.firstOrNull()
        val target = store.training.sessionExercises(last?.sessionId.orEmpty())
            .firstOrNull { it.exercise_id == exerciseId }
        val decision = decision(
            DecisionRequest(
                exerciseId = exerciseId,
                sessionId = last?.sessionId.orEmpty(),
                targetSets = target?.target_sets?.toInt() ?: DEFAULT_SETS,
                targetLow = target?.target_reps?.toInt() ?: DEFAULT_REPS,
                targetHigh = target?.target_reps_high?.toInt() ?: DEFAULT_REPS_HIGH,
                policyKey = target?.progression,
                incrementKg = incrementFor(exercise, equipment()),
            ),
        )
        val best = PersonalRecords.bestOneRepMax(
            store.training.setsForExercise(exerciseId)
                .map {
                    TrainingSetRecord(it.weight_kg, it.reps.toInt(), it.is_warmup, it.is_bodyweight)
                },
        )
        val rows = summary.mapNotNull { item ->
            val session = store.training.sessionById(item.sessionId) ?: return@mapNotNull null
            val sets = store.training.setsForSession(item.sessionId).filter {
                it.exercise_id == exerciseId && !it.is_warmup
            }
            val top = sets.maxByOrNull { it.weight_kg }
            ExerciseHistoryRow(
                date = LocalDate.parse(session.local_date),
                sets = sets.size.toString(),
                topSet = top?.let {
                    "${shown(it.weight_kg, profile)} ${unit(profile)} × ${it.reps}"
                },
                volume = volumeOrNull(sets, profile)?.let {
                    "${shown(it, profile)} ${unit(profile)}"
                },
                estimatedOneRepMax = sets.mapNotNull { oneRepMax(it) }.maxOrNull()
                    ?.let { "${shown(it, profile)} ${unit(profile)}" },
            )
        }
        return ExerciseHistoryModel(
            exerciseId = exerciseId,
            name = exercise?.name ?: exerciseName(exerciseId),
            muscles = muscleNames(exercise),
            bestOneRepMax = best?.let { "${shown(it, profile)} ${unit(profile)}" },
            progressionKey = target?.progression ?: LinearProgression.key,
            incrementKg = incrementFor(exercise, equipment()),
            nextWeight = decision.weightKg?.let { shown(it, profile) },
            weightUnit = unit(profile),
            nextReps = decision.repsLow,
            rows = rows,
        )
    }

    fun restMinus() = restTimer.adjust(-app.openhealth.core.training.RestTimer.STEP_SECONDS)
    fun restPlus() = restTimer.adjust(app.openhealth.core.training.RestTimer.STEP_SECONDS)
    fun restPause() = restTimer.togglePause()
    fun restSkip() = restTimer.skip()

    // ------------------------------------------------------------------ helpers

    private fun ensureSessionExercise(
        session: Session,
        exerciseId: String,
    ): app.openhealth.data.db.Session_exercise? {
        val existing = store.training.sessionExercises(session.id)
            .firstOrNull { it.exercise_id == exerciseId }
        if (existing != null) return existing
        addExercise(session.id, exerciseId)
        return store.training.sessionExercises(session.id)
            .firstOrNull { it.exercise_id == exerciseId }
    }

    private fun startRestIfDue(sessionId: String, entry: app.openhealth.data.db.Session_exercise) {
        val group = entry.group_index
        if (group != null) {
            val members = store.training.sessionExercises(sessionId)
                .filter { it.group_index == group }
                .sortedBy { it.position }
            if (members.isNotEmpty() && members.last().id != entry.id) return
        }
        val seconds = entry.rest_seconds?.toInt() ?: DEFAULT_REST_SECONDS
        restTimer.start(seconds, exerciseName(entry.exercise_id))
    }

    private fun decision(request: DecisionRequest): ProgressionDecision {
        val policy = ProgressionPolicy.fromKey(request.policyKey)
        val sets = store.training.setsForExercise(request.exerciseId).map { set ->
            app.openhealth.core.training.ProgressionSet(
                sessionId = set.session_id,
                weightKg = set.weight_kg,
                reps = set.reps.toInt(),
                isWarmup = set.is_warmup,
                isBackoff = set.is_backoff,
                isDrop = set.is_drop,
                completedAt = set.completed_at,
            )
        }
        return policy.next(
            app.openhealth.core.training.ProgressionPlanner.input(
                app.openhealth.core.training.ProgressionRequest(
                    sets = sets,
                    currentSessionId = request.sessionId,
                    targetSets = request.targetSets,
                    targetRepsLow = request.targetLow,
                    targetRepsHigh = request.targetHigh,
                    incrementKg = request.incrementKg,
                ),
            ),
        )
    }

    private fun incrementFor(exercise: CatalogueExercise?, equipment: EquipmentProfile): Double =
        if (exercise?.equipment == app.openhealth.core.exercise.Equipment.DUMBBELL) {
            equipment.dumbbellIncrementKg
        } else {
            ProgressionInput.DEFAULT_INCREMENT_KG
        }

    private fun volumeOrNull(sets: List<Work_set>, profile: Profile): Double? {
        val result = SessionVolumeCalculator.compute(
            sets.map {
                VolumeSet(
                    weightKg = it.weight_kg,
                    reps = it.reps.toInt(),
                    isWarmup = it.is_warmup,
                    isBodyweight = it.is_bodyweight,
                    bandKg = it.band_kg ?: 0.0,
                    assistanceKg = it.assistance_kg ?: 0.0,
                    bodyweightKg = profile.weight_kg,
                )
            },
        )
        return (result as? FormulaOutcome.Computed)?.value?.externalLoadKg
    }

    private fun oneRepMax(set: Work_set): Double? =
        (OneRepMax.estimate(set.weight_kg, set.reps.toInt()) as? FormulaOutcome.Computed)?.value

    private fun catalogue(): Map<String, CatalogueExercise> =
        (catalogueCache ?: store.training.catalogue().also { catalogueCache = it })
            .associateBy { it.id }

    private fun entriesOf(routineId: String) = store.training.routineEntries(routineId)

    private fun coverage(
        entries: List<app.openhealth.data.db.Routine_exercise>,
        catalogue: Map<String, CatalogueExercise>,
    ): Map<Muscle, app.openhealth.core.exercise.MusclePlan> = PlanCoverage.compute(
        entries.mapNotNull { entry ->
            catalogue[entry.exercise_id]?.let {
                PlannedExercise(it, entry.target_sets?.toInt() ?: DEFAULT_SETS)
            }
        },
    )

    private fun exerciseName(id: String): String = store.training.exerciseById(id)?.name ?: id

    private fun muscleNames(exercise: CatalogueExercise?): String =
        exercise?.primary?.joinToString(", ") { muscleLabel(it) }.orEmpty()

    private fun muscleLabel(muscle: Muscle): String =
        muscle.key.replace('-', ' ').replaceFirstChar { it.titlecase(Locale.getDefault()) }

    private fun sex(profile: Profile): BodySex =
        if (profile.sex_at_birth == "FEMALE") BodySex.FEMALE else BodySex.MALE

    private fun thresholds(): RecoveryThresholds {
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

    private fun Routine.toPlacement(): RoutinePlacement = RoutinePlacement(
        id = id,
        name = name,
        weekday = weekday?.toInt(),
        rotationIndex = rotation_index?.toInt(),
        favourite = favourite,
        folder = folder,
    )

    private fun Routine.schedule(): RoutineSchedule =
        if (weekday == null) RoutineSchedule.ROTATION else RoutineSchedule.WEEKDAY

    private fun kindOf(set: Work_set): LiveSetKind = when {
        set.is_warmup -> LiveSetKind.WARMUP
        set.is_drop -> LiveSetKind.DROP
        set.is_backoff -> LiveSetKind.BACKOFF
        set.is_failure -> LiveSetKind.FAILURE
        else -> LiveSetKind.WORK
    }

    private fun targetLabel(
        sets: Long,
        reps: Long?,
        high: Long?,
        weight: Double?,
        profile: Profile,
    ): String {
        val repsText = repsLabel(reps, high)
        val base = "$sets × $repsText"
        return if (weight != null) {
            "$base @ ${shown(weight, profile)} ${unit(profile)}"
        } else {
            base
        }
    }

    private fun repsLabel(reps: Long?, high: Long?): String {
        val low = reps?.toInt() ?: DEFAULT_REPS
        val top = high?.toInt() ?: low
        return if (top > low) "$low-$top" else low.toString()
    }

    private fun parseReps(value: String): Pair<Int, Int> {
        val numbers = value.split('-', '–', ' ')
            .mapNotNull { it.trim().toIntOrNull() }
        val low = numbers.firstOrNull()?.coerceIn(1, REP_LIMIT) ?: DEFAULT_REPS
        val high = numbers.getOrNull(1)?.coerceIn(low, REP_LIMIT) ?: low
        return low to high
    }

    private fun shown(kilograms: Double, profile: Profile?): String =
        trimNumber(TrainingUnits.toDisplay(kilograms, imperial(profile)))

    private fun entered(display: Double, profile: Profile?): Double =
        TrainingUnits.toKilograms(display, imperial(profile))

    private fun imperial(profile: Profile?): Boolean =
        UnitSystem.fromKey(profile?.units) == UnitSystem.IMPERIAL

    private fun unit(profile: Profile?): String = TrainingUnits.label(imperial(profile))

    private fun trimNumber(value: Double): String {
        val whole = value.toLong()
        return if (value == whole.toDouble()) {
            whole.toString()
        } else {
            String.format(Locale.getDefault(), "%.2f", value).trimEnd('0').trimEnd('.')
        }
    }

    private data class DecisionRequest(
        val exerciseId: String,
        val sessionId: String,
        val targetSets: Int,
        val targetLow: Int,
        val targetHigh: Int,
        val policyKey: String?,
        val incrementKg: Double,
    )

    private companion object {
        const val DEFAULT_SETS = 3
        const val DEFAULT_REPS = 8
        const val DEFAULT_REPS_HIGH = 12
        const val DEFAULT_REST_SECONDS = 120
        const val HISTORY_ROWS = 20L
        const val MAX_SETS = 20
        const val REP_LIMIT = 100
        const val DEFAULT_RECOVERING_HOURS = 24.0
        const val DEFAULT_EASING_HOURS = 48.0
        const val DEFAULT_READY_HOURS = 72.0
        const val MILLIS_PER_SECOND = 1_000L
        const val PAST_SET_SPACING_MILLIS = 120_000L
        const val BACKFILL_END_SPACING_MILLIS = 60_000L
    }
}
