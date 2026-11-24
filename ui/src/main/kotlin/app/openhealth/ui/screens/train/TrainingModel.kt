package app.openhealth.ui.screens.train

import app.openhealth.core.body.Muscle
import app.openhealth.core.training.EquipmentProfile
import app.openhealth.core.training.PersonalRecordKind
import app.openhealth.core.training.PlanReason
import app.openhealth.core.training.ProgressionAction
import app.openhealth.core.training.ProgressionReason
import app.openhealth.core.training.RoutineSchedule
import app.openhealth.ui.body.BodyMapModel
import kotlinx.datetime.LocalDate

/** One cell of the Train week strip (`DESIGN.md` §14.2). */
data class TrainingDay(val date: LocalDate, val isToday: Boolean, val hasSession: Boolean)

/** Today's plan card: the routine it names, why, and the muscles it trains. */
data class TrainingPlanCard(
    val routineId: String,
    val name: String,
    val reason: PlanReason,
    val setCount: Int,
    val map: BodyMapModel,
)

/** A routine row with its mini map, shaded by share of its working sets. */
data class TrainingRoutineRow(
    val id: String,
    val name: String,
    val schedule: RoutineSchedule,
    val weekday: Int?,
    val rotationIndex: Int?,
    val folder: String?,
    val setCount: Int,
    val favourite: Boolean,
    val map: BodyMapModel,
)

/** A finished session in the Train history list. */
data class TrainingHistoryRow(
    val sessionId: String,
    val title: String?,
    val date: LocalDate,
    val sets: Int,
    val volumeLabel: String?,
    val map: BodyMapModel,
)

/** A session that is still open, so the root can offer Resume rather than Start. */
data class OpenSessionCard(
    val sessionId: String,
    val title: String,
    val startedAt: Long,
    val setCount: Int,
)

/** The Train root, everything on it, with no screen reading storage (`DESIGN.md` §14.2). */
data class TrainModel(
    val today: LocalDate,
    val week: List<TrainingDay>,
    val plan: TrainingPlanCard?,
    val recovery: BodyMapModel,
    val planMap: BodyMapModel?,
    /** True when the plan is a rotation cycle rather than a weekday week. */
    val planUsesCycle: Boolean,
    val routines: List<TrainingRoutineRow>,
    val history: List<TrainingHistoryRow>,
    val openSession: OpenSessionCard?,
    val demo: Boolean,
)

/** One exercise in a routine, as its detail page lists it. */
data class RoutineExerciseRow(
    val entryId: String,
    val exerciseId: String,
    val name: String,
    val muscles: String,
    val target: String,
    val rest: String?,
    val groupIndex: Int?,
    val notes: String?,
)

data class RoutineDetailModel(
    val id: String,
    val name: String,
    val folder: String?,
    val schedule: RoutineSchedule,
    val weekday: Int?,
    val rotationIndex: Int?,
    val progressionKey: String,
    val notes: String?,
    val favourite: Boolean,
    val map: BodyMapModel,
    val weeklySets: Double,
    val weightUnit: String,
    val exercises: List<RoutineExerciseRow>,
    val lastTrained: LocalDate?,
)

/** The set kinds the table marks (`DESIGN.md` §14.2): W1…, work, back-off, drop, failure. */
enum class LiveSetKind { WARMUP, WORK, BACKOFF, DROP, FAILURE }

/** The values the live table sends when a set is completed. */
data class LoggedSet(
    val exerciseId: String,
    val weight: String,
    val reps: String,
    val rir: String?,
    val kind: LiveSetKind,
    val notes: String?,
)

/** One set on the live table; [weight] and [reps] stay text while the person types. */
data class LiveSetRow(
    val id: String?,
    val label: String,
    val weight: String,
    val reps: String,
    val rir: String?,
    val kind: LiveSetKind,
    val done: Boolean,
    val prKinds: List<PersonalRecordKind> = emptyList(),
    val tempo: String? = null,
    val notes: String? = null,
    val isBodyweight: Boolean = false,
    val isAssisted: Boolean = false,
    val assistanceKg: String = "",
    val bandKg: String = "",
    val sourceLabel: String? = null,
    val recordedAt: Long? = null,
)

/** The editable values of a saved set, opened from its row. */
data class LiveSetEdit(
    val weight: String,
    val reps: String,
    val rir: String?,
    val kind: LiveSetKind,
    val notes: String,
    val tempo: String,
    val isBodyweight: Boolean,
    val isAssisted: Boolean,
    val assistanceKg: String,
    val bandKg: String,
    val reason: String,
)

/** One exercise block on the live surface, with its prefill and its explanation line. */
data class LiveExerciseModel(
    val id: String,
    val exerciseId: String,
    val name: String,
    val equipmentKey: String,
    val muscles: String,
    val target: String,
    val lastTime: String?,
    val progressionKey: String,
    val incrementKg: Double,
    val action: ProgressionAction,
    val progressionReason: ProgressionReason,
    val nextWeight: String,
    val nextReps: String,
    val notes: String?,
    val groupIndex: Int?,
    val remainingSets: Int,
    val sets: List<LiveSetRow>,
)

data class RestDisplay(val remainingSeconds: Long, val totalSeconds: Long, val paused: Boolean)

/** One library exercise offered by the live session's add sheet. */
data class ExerciseChoice(val id: String, val name: String, val muscles: String)

/** The fields a person types for an exercise that is not in the bundled library. */
data class CustomExerciseDraft(
    val name: String,
    val primary: String,
    val equipment: String,
    val instructions: String,
)

/** The live session (`DESIGN.md` §14.2): focused, no tab bar, a set table and a timer. */
data class LiveSessionModel(
    val sessionId: String,
    val title: String,
    val startedAt: Long,
    val localDate: LocalDate,
    val backdated: Boolean,
    val exercises: List<LiveExerciseModel>,
    val weightUnit: String,
    val map: BodyMapModel,
    val rest: RestDisplay?,
    val editing: Boolean,
    val note: String,
    val feeling: String,
    val library: List<ExerciseChoice> = emptyList(),
    val equipment: EquipmentProfile = EquipmentProfile(),
)

/** One record in the summary list, with the set that earned it. */
data class SummaryRecord(val kind: PersonalRecordKind, val value: String)

/** A session summary (`DESIGN.md` §14.2): KPIs, PRs, the loaded muscles and the note. */
data class SessionSummaryModel(
    val sessionId: String,
    val title: String,
    val date: LocalDate,
    val duration: String,
    val durationSeconds: Long,
    val volume: String?,
    val weightUnit: String,
    val sets: String,
    val prs: List<SummaryRecord>,
    val map: BodyMapModel,
    val topMuscles: List<Pair<Muscle, Double>>,
    val feeling: String,
    val note: String,
    val sourceLabel: String,
    val happenedAt: kotlin.time.Instant,
    val recordedAt: kotlin.time.Instant,
    val localDay: LocalDate,
    val zoneId: String,
    val device: String?,
)

/** One session in the per-exercise history, newest first. */
data class ExerciseHistoryRow(
    val date: LocalDate,
    val sets: String,
    val topSet: String?,
    val volume: String?,
    val estimatedOneRepMax: String?,
)

data class ExerciseHistoryModel(
    val exerciseId: String,
    val name: String,
    val muscles: String,
    val bestOneRepMax: String?,
    val progressionKey: String,
    val incrementKg: Double,
    val nextWeight: String?,
    val nextReps: Int,
    val weightUnit: String,
    val rows: List<ExerciseHistoryRow>,
)

/** One editable exercise row in the routine editor. */
data class EditorExerciseRow(
    val id: String,
    val exerciseId: String,
    val name: String,
    val muscles: String,
    val sets: String,
    val reps: String,
    val weight: String,
    val rest: String,
)

/** A routine being created or edited. */
data class RoutineEditorModel(
    val id: String?,
    val name: String,
    val folder: String,
    val schedule: RoutineSchedule,
    val weekday: Int,
    val rotationIndex: Int,
    val favourite: Boolean,
    val progressionKey: String,
    val notes: String,
    val exercises: List<EditorExerciseRow>,
    val library: List<ExerciseChoice>,
    val weightUnit: String,
)

/** What the editor saves in one action; its fields are local until Save. */
data class RoutineDraft(
    val id: String?,
    val name: String,
    val folder: String?,
    val schedule: RoutineSchedule,
    val weekday: Int,
    val rotationIndex: Int,
    val favourite: Boolean,
    val progressionKey: String,
    val notes: String?,
    val exercises: List<EditorExerciseRow>,
)

/** The muscles a list of names joins for a row's subtitle. */
fun muscleList(muscles: List<Muscle>, label: (Muscle) -> String): String =
    muscles.joinToString(", ") { label(it) }
