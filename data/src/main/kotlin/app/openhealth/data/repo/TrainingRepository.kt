package app.openhealth.data.repo

import app.openhealth.data.db.Exercise
import app.openhealth.data.db.OpenHealthDb
import app.openhealth.data.db.Programme
import app.openhealth.data.db.Routine
import app.openhealth.data.db.Routine_exercise
import app.openhealth.data.db.Session
import app.openhealth.data.db.Session_exercise
import app.openhealth.data.db.Work_set
import app.openhealth.data.mapping.requireConsistentDay
import kotlinx.datetime.LocalDate

/**
 * Layer 4 training objects. A set is written once; a correction keeps the original in
 * the change log rather than overwriting history. Deletes go through [TrashService].
 */
class TrainingRepository(private val db: OpenHealthDb) {
    fun upsertExercise(exercise: Exercise) {
        db.trainingQueries.insertExercise(
            id = exercise.id,
            name = exercise.name,
            primaryMuscles = exercise.primary_muscles,
            secondaryMuscles = exercise.secondary_muscles,
            equipment = exercise.equipment,
            instructions = exercise.instructions,
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
        )
    }

    fun routines(): List<Routine> = db.trainingQueries.allRoutines().executeAsList()

    fun upsertRoutineExercise(entry: Routine_exercise) {
        db.trainingQueries.insertRoutineExercise(
            id = entry.id,
            routineId = entry.routine_id,
            exerciseId = entry.exercise_id,
            position = entry.position,
            targetSets = entry.target_sets,
            targetReps = entry.target_reps,
            targetWeightKg = entry.target_weight_kg,
            notes = entry.notes,
        )
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
    }

    fun routinesInProgramme(programmeId: String): List<Routine> =
        db.trainingQueries.routinesInProgramme(programmeId).executeAsList()

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
        )
    }

    fun sessionById(id: String): Session? = db.trainingQueries.sessionById(id).executeAsOneOrNull()

    fun sessionsForDay(date: LocalDate): List<Session> =
        db.trainingQueries.sessionsForDay(date.toString()).executeAsList()

    fun recentSessions(limit: Long): List<Session> =
        db.trainingQueries.recentSessions(limit).executeAsList()

    fun upsertSessionExercise(entry: Session_exercise) {
        db.trainingQueries.insertSessionExercise(
            id = entry.id,
            sessionId = entry.session_id,
            exerciseId = entry.exercise_id,
            position = entry.position,
            notes = entry.notes,
        )
    }

    fun sessionExercises(sessionId: String): List<Session_exercise> =
        db.trainingQueries.sessionExercises(sessionId).executeAsList()

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
        )
    }

    fun setById(id: String): Work_set? = db.trainingQueries.workSetById(id).executeAsOneOrNull()

    fun setsForSession(sessionId: String): List<Work_set> =
        db.trainingQueries.setsForSession(sessionId).executeAsList()

    fun setsForExerciseBetween(exerciseId: String, from: Long, to: Long): List<Work_set> =
        db.trainingQueries.setsForExerciseBetween(exerciseId, from, to).executeAsList()

    fun countSets(): Long = db.trainingQueries.countWorkSets().executeAsOne()
}
