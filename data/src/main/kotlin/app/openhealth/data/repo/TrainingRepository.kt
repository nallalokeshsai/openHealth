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
        upsertExercise(
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

    fun countExercises(): Long = db.trainingQueries.countExercises().executeAsOne()
}
