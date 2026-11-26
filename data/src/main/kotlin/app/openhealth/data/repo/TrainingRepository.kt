package app.openhealth.data.repo

import app.openhealth.core.body.LoadedSet
import app.openhealth.core.body.Muscle
import app.openhealth.core.exercise.Equipment
import app.openhealth.core.exercise.Exercise as CatalogueExercise
import app.openhealth.core.exercise.ExerciseCatalogue
import app.openhealth.core.exercise.ExerciseForce
import app.openhealth.core.exercise.ExerciseLevel
import app.openhealth.core.exercise.ExerciseMechanic
import app.openhealth.data.db.Exercise
import app.openhealth.data.db.OpenHealthDb
import app.openhealth.data.db.Programme
import app.openhealth.data.db.Routine
import app.openhealth.data.db.Routine_exercise
import app.openhealth.data.db.Session
import app.openhealth.data.db.Session_exercise
import app.openhealth.data.db.Work_set
import app.openhealth.data.mapping.requireConsistentDay
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Layer 4 training objects (`pr.md` §6.5, §7.4). A set is written once; a correction keeps
 * the original in the change log rather than overwriting history. Deletes go through
 * [TrashService]. Every session write rebuilds the days it can affect, so a backfilled or
 * moved workout recomputes the rollups without touching `recorded_at` (`pr.md` §7.3).
 */
class TrainingRepository(private val db: OpenHealthDb) {
    private var rollupInvalidator: ((LocalDate) -> Unit)? = null
    private var sync: app.openhealth.data.sync.SyncRepository? = null

    fun recordChangesWith(recorder: app.openhealth.data.sync.SyncRepository?) {
        sync = recorder
    }

    fun invalidateRollupsWith(invalidator: ((LocalDate) -> Unit)?) {
        rollupInvalidator = invalidator
    }

    fun upsertExercise(exercise: Exercise) {
        writeExercise(exercise)
        sync?.capture("exercise", exercise.id)
    }

    private fun writeExercise(exercise: Exercise) {
        db.trainingQueries.insertExercise(
            id = exercise.id,
            name = exercise.name,
            primaryMuscles = exercise.primary_muscles,
            secondaryMuscles = exercise.secondary_muscles,
            equipment = exercise.equipment,
            instructions = exercise.instructions,
            level = exercise.level,
            mechanic = exercise.mechanic,
            force = exercise.force,
            metActivity = exercise.met_activity,
            isCustom = exercise.is_custom,
            sourceId = exercise.source_id,
            createdAt = exercise.created_at,
            updatedAt = exercise.updated_at,
            revision = exercise.revision,
        )
    }

    fun exercises(): List<Exercise> = db.trainingQueries.allExercises().executeAsList()

    fun exerciseById(id: String): Exercise? =
        db.trainingQueries.exerciseById(id).executeAsOneOrNull()

    /**
     * Writes one library or custom exercise. The stored muscle columns and the typed
     * model are the same list, so no reader has to re-map one to the other.
     */
    fun insertCatalogueExercise(exercise: CatalogueExercise, sourceId: String?, nowMillis: Long) {
        writeExercise(
            Exercise(
                id = exercise.id,
                name = exercise.name,
                primary_muscles = exercise.primary.joinToString(",") { it.key },
                secondary_muscles = exercise.secondary.joinToString(",") { it.key },
                equipment = exercise.equipment.key,
                instructions = exercise.instructions.joinToString("\n")
                    .ifBlank { null },
                level = exercise.level.key,
                mechanic = exercise.mechanic?.key,
                force = exercise.force?.key,
                met_activity = null,
                is_custom = exercise.isCustom,
                source_id = sourceId,
                created_at = nowMillis,
                updated_at = nowMillis,
                revision = 1L,
            ),
        )
    }

    /**
     * Writes the bundled library once. The caller records [ExerciseCatalogue.VERSION] in
     * settings and skips this next launch; re-seeding is a real upsert, so a corrected
     * library replaces its own rows and leaves custom exercises alone.
     */
    fun seedCatalogue(
        exercises: List<CatalogueExercise>,
        sourceId: String?,
        nowMillis: Long,
    ): Int = db.transactionWithResult {
        exercises.forEach { insertCatalogueExercise(it, sourceId, nowMillis) }
        exercises.size
    }

    /** The whole library as typed models; a row whose muscle keys are unreadable is skipped. */
    fun catalogue(): List<CatalogueExercise> = exercises().mapNotNull { row ->
        val primary = muscles(row.primary_muscles)
        if (primary.isEmpty()) return@mapNotNull null
        CatalogueExercise(
            id = row.id,
            name = row.name,
            primary = primary,
            secondary = muscles(row.secondary_muscles) - primary.toSet(),
            equipment = Equipment.fromKey(row.equipment),
            level = ExerciseLevel.fromKey(row.level),
            mechanic = ExerciseMechanic.fromKey(row.mechanic),
            force = ExerciseForce.fromKey(row.force),
            bodyPart = app.openhealth.core.body.BodyPart.of(primary),
            instructions = row.instructions.orEmpty().split('\n').filter { it.isNotBlank() },
            isCustom = row.is_custom,
        )
    }

    /**
     * The last four weeks of completed sets with their exercise's muscles, for the
     * recovery map (`pr.md` §6.5). Warm-ups are returned too; the engine ignores them.
     */
    fun loadedSets(fromMillis: Long): List<LoadedSet> =
        db.trainingQueries.recentSetLoads(fromMillis).executeAsList().map { row ->
            LoadedSet(
                sessionId = row.session_id,
                exerciseId = row.exercise_id,
                primary = muscles(row.primary_muscles),
                secondary = muscles(row.secondary_muscles),
                completedAt = Instant.fromEpochMilliseconds(row.completed_at),
                weightKg = row.weight_kg,
                reps = row.reps.toInt(),
                isWarmup = row.is_warmup,
                sourceId = row.source_id,
                recordedAt = Instant.fromEpochMilliseconds(row.recorded_at),
            )
        }

    private fun muscles(cell: String): List<Muscle> = cell
        .split(',')
        .mapNotNull { Muscle.fromKey(it) }
        .distinct()

    // ------------------------------------------------------------------ routines

    fun upsertRoutine(routine: Routine) {
        db.trainingQueries.insertRoutine(
            id = routine.id,
            name = routine.name,
            folder = routine.folder,
            notes = routine.notes,
            archivedAt = routine.archived_at,
            createdAt = routine.created_at,
            updatedAt = routine.updated_at,
            sourceId = routine.source_id,
            revision = routine.revision,
            weekday = routine.weekday,
            rotationIndex = routine.rotation_index,
            favourite = routine.favourite,
            progression = routine.progression,
            targetRestSeconds = routine.target_rest_seconds,
        )
        sync?.capture("routine", routine.id)
    }

    fun routines(): List<Routine> = db.trainingQueries.allRoutines().executeAsList()

    fun routineById(id: String): Routine? = db.trainingQueries.routineById(id).executeAsOneOrNull()

    fun upsertRoutineExercise(entry: Routine_exercise) {
        db.trainingQueries.insertRoutineExercise(
            id = entry.id,
            routineId = entry.routine_id,
            exerciseId = entry.exercise_id,
            position = entry.position,
            targetSets = entry.target_sets,
            targetReps = entry.target_reps,
            targetRepsHigh = entry.target_reps_high,
            targetWeightKg = entry.target_weight_kg,
            notes = entry.notes,
            targetRir = entry.target_rir,
            restSeconds = entry.rest_seconds,
            progression = entry.progression,
            groupIndex = entry.group_index,
        )
        sync?.capture("routine_exercise", entry.id)
    }

    fun routineEntries(routineId: String): List<Routine_exercise> =
        db.trainingQueries.routineEntries(routineId).executeAsList()

    fun routineEntryById(id: String): Routine_exercise? =
        db.trainingQueries.routineEntryById(id).executeAsOneOrNull()

    fun deleteRoutineEntry(id: String) {
        db.trainingQueries.deleteRoutineExercise(id)
        sync?.captureDelete("routine_exercise", id)
    }

    fun exercisesInRoutine(routineId: String): List<Exercise> =
        db.trainingQueries.exercisesInRoutine(routineId).executeAsList()

    fun upsertProgramme(programme: Programme) {
        db.trainingQueries.insertProgramme(
            id = programme.id,
            name = programme.name,
            weeks = programme.weeks,
            notes = programme.notes,
            archivedAt = programme.archived_at,
            createdAt = programme.created_at,
            updatedAt = programme.updated_at,
            sourceId = programme.source_id,
            revision = programme.revision,
        )
        sync?.capture("programme", programme.id)
    }

    fun programmes(): List<Programme> = db.trainingQueries.allProgrammes().executeAsList()

    fun upsertProgrammeRoutine(
        id: String,
        programmeId: String,
        routineId: String,
        week: Long,
        position: Long,
    ) {
        db.trainingQueries.insertProgrammeRoutine(id, programmeId, routineId, week, position)
        sync?.capture("programme_routine", id)
    }

    fun routinesInProgramme(programmeId: String): List<Routine> =
        db.trainingQueries.routinesInProgramme(programmeId).executeAsList()

    // ------------------------------------------------------------------ sessions

    fun upsertSession(session: Session) {
        requireConsistentDay(session.local_date, session.zone_id, session.happened_at)
        db.trainingQueries.insertSession(
            id = session.id,
            startedAt = session.started_at,
            endedAt = session.ended_at,
            localDate = session.local_date,
            zoneId = session.zone_id,
            happenedAt = session.happened_at,
            recordedAt = session.recorded_at,
            routineId = session.routine_id,
            programmeId = session.programme_id,
            notes = session.notes,
            sourceId = session.source_id,
            idempotencyKey = session.idempotency_key,
            observationId = session.observation_id,
            rawRecordId = session.raw_record_id,
            device = session.device,
            appVersion = session.app_version,
            revision = session.revision,
            updatedAt = session.updated_at,
            title = session.title,
            feeling = session.feeling,
            perceivedEffort = session.perceived_effort,
        )
        sync?.capture("session", session.id, session.local_date)
        invalidate(LocalDate.parse(session.local_date))
    }

    fun sessionById(id: String): Session? = db.trainingQueries.sessionById(id).executeAsOneOrNull()

    fun sessionByIdempotencyKey(key: String): Session? =
        db.trainingQueries.sessionByIdempotencyKey(key).executeAsOneOrNull()

    fun openSession(): Session? = db.trainingQueries.openSession().executeAsOneOrNull()

    fun sessionsForDay(date: LocalDate): List<Session> =
        db.trainingQueries.sessionsForDay(date.toString()).executeAsList()

    fun recentSessions(limit: Long): List<Session> =
        db.trainingQueries.recentSessions(limit).executeAsList()

    fun finishedSessions(limit: Long): List<Session> =
        db.trainingQueries.finishedSessions(limit).executeAsList()

    fun sessionsBetween(from: LocalDate, toExclusive: LocalDate): List<Session> =
        db.trainingQueries.sessionsBetween(from.toString(), toExclusive.toString())
            .executeAsList()

    fun lastFinishedSession(): Session? =
        db.trainingQueries.lastFinishedSession().executeAsOneOrNull()

    fun sessionsForRoutine(routineId: String): List<Session> =
        db.trainingQueries.sessionsForRoutine(routineId).executeAsList()

    fun upsertSessionExercise(entry: Session_exercise) {
        db.trainingQueries.insertSessionExercise(
            id = entry.id,
            sessionId = entry.session_id,
            exerciseId = entry.exercise_id,
            position = entry.position,
            notes = entry.notes,
            groupIndex = entry.group_index,
            targetSets = entry.target_sets,
            targetReps = entry.target_reps,
            targetRepsHigh = entry.target_reps_high,
            targetWeightKg = entry.target_weight_kg,
            progression = entry.progression,
            restSeconds = entry.rest_seconds,
        )
        sync?.capture("session_exercise", entry.id)
    }

    fun sessionExercises(sessionId: String): List<Session_exercise> =
        db.trainingQueries.sessionExercises(sessionId).executeAsList()

    fun deleteSessionExercise(id: String) {
        db.trainingQueries.deleteSessionExercise(id)
        sync?.captureDelete("session_exercise", id)
    }

    /** Finishes an open session; the day's training rollups then include it. */
    fun finishSession(id: String, endedAt: Long, nowMillis: Long): Boolean {
        val session = sessionById(id) ?: return false
        // Editing a saved workout keeps the moment it actually ended; only an open
        // session takes the new end time.
        val ended = session.ended_at ?: endedAt
        db.trainingQueries.updateSessionTiming(
            id = id,
            startedAt = session.started_at,
            endedAt = ended,
            localDate = session.local_date,
            happenedAt = session.happened_at,
            updatedAt = nowMillis,
        )
        sync?.capture("session", id, session.local_date)
        invalidate(LocalDate.parse(session.local_date))
        return true
    }

    fun updateSessionNotes(id: String, notes: String?, nowMillis: Long) {
        db.trainingQueries.updateSessionNotes(notes, nowMillis, id)
        sessionDate(id)?.let { date -> sync?.capture("session", id, date.toString()) }
    }

    fun updateSessionDetails(
        id: String,
        details: SessionDetails,
        endedAt: Long?,
        nowMillis: Long,
    ): Boolean {
        val session = sessionById(id) ?: return false
        db.trainingQueries.updateSessionDetails(
            id = id,
            title = details.title,
            notes = details.notes,
            feeling = details.feeling,
            perceivedEffort = details.perceivedEffort,
            endedAt = endedAt,
            updatedAt = nowMillis,
        )
        sync?.capture("session", id, session.local_date)
        invalidate(LocalDate.parse(session.local_date))
        return true
    }

    /**
     * Moves a saved workout to another day (`pr.md` §6.5, §7.3). The session and every set
     * keep their `recorded_at`; only `happened_at`, `completed_at` and the session's start
     * and end move. Both days' rollups are rebuilt.
     */
    fun moveSession(id: String, newStartMillis: Long, zone: TimeZone, nowMillis: Long): Boolean {
        val session = sessionById(id) ?: return false
        val oldDate = LocalDate.parse(session.local_date)
        val newDate = Instant.fromEpochMilliseconds(newStartMillis)
            .toLocalDateTime(zone)
            .date
        val delta = newStartMillis - session.started_at
        db.transaction {
            db.trainingQueries.updateSessionTiming(
                id = id,
                startedAt = newStartMillis,
                endedAt = session.ended_at?.plus(delta),
                localDate = newDate.toString(),
                happenedAt = newStartMillis,
                updatedAt = nowMillis,
            )
            db.trainingQueries.setsForSession(id).executeAsList().forEach { set ->
                db.trainingQueries.updateWorkSetTiming(
                    id = set.id,
                    completedAt = set.completed_at + delta,
                    happenedAt = set.happened_at + delta,
                )
                sync?.capture("work_set", set.id, newDate.toString())
            }
        }
        sync?.capture("session", id, newDate.toString())
        invalidate(oldDate)
        invalidate(newDate)
        return true
    }

    fun setsForSession(sessionId: String): List<Work_set> =
        db.trainingQueries.setsForSession(sessionId).executeAsList()

    fun countSetsForSession(sessionId: String): Long =
        db.trainingQueries.countSetsForSession(sessionId).executeAsOne()

    fun setsForExercise(exerciseId: String): List<Work_set> =
        db.trainingQueries.setsForExercise(exerciseId).executeAsList()

    fun lastWorkSetForExercise(exerciseId: String): Work_set? =
        db.trainingQueries.lastWorkSetForExercise(exerciseId).executeAsOneOrNull()

    /** One row per session that trained an exercise, newest first. */
    fun exerciseSessionSummary(exerciseId: String): List<ExerciseSessionRow> =
        db.trainingQueries.exerciseSessionSummary(exerciseId).executeAsList().map { row ->
            ExerciseSessionRow(
                sessionId = row.session_id,
                lastAt = row.last_at ?: 0L,
                setCount = row.set_count,
                topWeightKg = row.top_weight_kg ?: 0.0,
                topReps = row.top_reps ?: 0L,
            )
        }

    fun setsForExerciseBetween(exerciseId: String, from: Long, to: Long): List<Work_set> =
        db.trainingQueries.setsForExerciseBetween(exerciseId, from, to).executeAsList()

    fun upsertSet(set: Work_set) {
        db.trainingQueries.insertWorkSet(
            id = set.id,
            sessionId = set.session_id,
            sessionExerciseId = set.session_exercise_id,
            exerciseId = set.exercise_id,
            position = set.position,
            weightKg = set.weight_kg,
            reps = set.reps,
            rir = set.rir,
            rpe = set.rpe,
            isWarmup = set.is_warmup,
            isBackoff = set.is_backoff,
            isFailure = set.is_failure,
            isBodyweight = set.is_bodyweight,
            tempo = set.tempo,
            restSeconds = set.rest_seconds,
            notes = set.notes,
            completedAt = set.completed_at,
            happenedAt = set.happened_at,
            recordedAt = set.recorded_at,
            sourceId = set.source_id,
            idempotencyKey = set.idempotency_key,
            observationId = set.observation_id,
            rawRecordId = set.raw_record_id,
            device = set.device,
            appVersion = set.app_version,
            revision = set.revision,
            isDrop = set.is_drop,
            isAssisted = set.is_assisted,
            assistanceKg = set.assistance_kg,
            bandKg = set.band_kg,
        )
        sessionDate(set.session_id)?.let { date ->
            sync?.capture("work_set", set.id, date.toString())
            invalidate(date)
        }
    }

    fun setById(id: String): Work_set? = db.trainingQueries.workSetById(id).executeAsOneOrNull()

    fun setByIdempotencyKey(key: String): Work_set? =
        db.trainingQueries.workSetByIdempotencyKey(key).executeAsOneOrNull()

    /** Corrects a set's values; `recorded_at` and the original stay where they were. */
    fun updateSet(id: String, values: SetValues): Boolean {
        val set = setById(id) ?: return false
        db.trainingQueries.updateWorkSet(
            id = id,
            weightKg = values.weightKg,
            reps = values.reps,
            rir = values.rir,
            rpe = values.rpe,
            isWarmup = values.isWarmup,
            isBackoff = values.isBackoff,
            isFailure = values.isFailure,
            isBodyweight = values.isBodyweight,
            isDrop = values.isDrop,
            isAssisted = values.isAssisted,
            assistanceKg = values.assistanceKg,
            bandKg = values.bandKg,
            tempo = values.tempo,
            restSeconds = values.restSeconds,
            notes = values.notes,
            reason = values.reason,
            editedAt = values.editedAt,
        )
        sessionDate(set.session_id)?.let { date ->
            sync?.capture("work_set", id, date.toString())
            invalidate(date)
        }
        return true
    }

    fun countSets(): Long = db.trainingQueries.countWorkSets().executeAsOne()

    fun countExercises(): Long = db.trainingQueries.countExercises().executeAsOne()

    private fun sessionDate(sessionId: String): LocalDate? =
        sessionById(sessionId)?.local_date?.let(LocalDate::parse)

    private fun invalidate(date: LocalDate) {
        rollupInvalidator?.invoke(date)
    }
}

/** The editable fields of a session, kept together so the update has one parameter. */
data class SessionDetails(
    val title: String?,
    val notes: String?,
    val feeling: String?,
    val perceivedEffort: Double?,
)

/** The editable values of a set; `recorded_at` and the original are never among them. */
data class SetValues(
    val weightKg: Double,
    val reps: Long,
    val rir: Double?,
    val rpe: Double?,
    val isWarmup: Boolean,
    val isBackoff: Boolean,
    val isFailure: Boolean,
    val isBodyweight: Boolean,
    val isDrop: Boolean,
    val isAssisted: Boolean,
    val assistanceKg: Double?,
    val bandKg: Double?,
    val tempo: String?,
    val restSeconds: Long?,
    val notes: String?,
    val reason: String?,
    val editedAt: Long,
)

/** One exercise's result in one session, for the per-exercise history list. */
data class ExerciseSessionRow(
    val sessionId: String,
    val lastAt: Long,
    val setCount: Long,
    val topWeightKg: Double,
    val topReps: Long,
)
