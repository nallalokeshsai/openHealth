@file:Suppress("MagicNumber")

package app.openhealth.ui.screens.train

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.openhealth.core.training.PersonalRecordKind
import app.openhealth.core.training.PlanReason
import app.openhealth.core.training.ProgressionPolicy
import app.openhealth.core.training.ProgressionReason
import app.openhealth.core.training.RoutineSchedule
import app.openhealth.core.training.rotationLetter
import app.openhealth.ui.R
import app.openhealth.ui.screens.common.number

/** The words the Train surfaces share, all from resources (`DESIGN.md` §11.4, §11.7). */
@Composable
fun planReasonLabel(reason: PlanReason): String = stringResource(
    when (reason) {
        PlanReason.WEEKDAY -> R.string.train_plan_reason_weekday
        PlanReason.ROTATION_NEXT -> R.string.train_plan_reason_next
        PlanReason.ROTATION_FIRST -> R.string.train_plan_reason_first
    },
)

@Composable
fun progressionPolicyLabel(key: String, incrementKg: Double): String = when (key) {
    "double" -> stringResource(R.string.train_live_policy_double)
    "off" -> stringResource(R.string.train_live_policy_off)
    else -> stringResource(
        R.string.train_live_policy_linear,
        number(incrementKg, 2),
    )
}

@Composable
fun progressionReasonLabel(reason: ProgressionReason): String = stringResource(
    when (reason) {
        ProgressionReason.FIRST_TIME -> R.string.train_live_reason_first
        ProgressionReason.COMPLETED_ALL_REPS -> R.string.train_live_reason_all
        ProgressionReason.COMPLETED_ALL_SETS -> R.string.train_live_reason_sets
        ProgressionReason.MISSED_REP -> R.string.train_live_reason_missed
        ProgressionReason.INCOMPLETE_SESSION -> R.string.train_live_reason_incomplete
        ProgressionReason.STALLED -> R.string.train_live_reason_stalled
        ProgressionReason.POLICY_OFF -> R.string.train_live_reason_off
    },
)

@Composable
fun personalRecordLabel(kind: PersonalRecordKind): String = stringResource(
    when (kind) {
        PersonalRecordKind.HEAVIEST_WEIGHT -> R.string.train_live_pr_weight
        PersonalRecordKind.ESTIMATED_ONE_REP_MAX -> R.string.train_live_pr_e1rm
        PersonalRecordKind.REPS_AT_WEIGHT -> R.string.train_live_pr_reps
    },
)

/** "W1" for a warm-up, otherwise the work-set number (`DESIGN.md` §14.2). */
@Composable
fun setLabel(kind: LiveSetKind, workNumber: Int, warmupNumber: Int): String =
    if (kind == LiveSetKind.WARMUP) {
        stringResource(R.string.train_live_set_warmup, number(warmupNumber.toDouble()))
    } else {
        number(workNumber.toDouble())
    }

/** "Monday" or "Rotation A" for a routine and its schedule. */
@Composable
fun scheduleLabel(schedule: RoutineSchedule, weekday: Int?, rotationIndex: Int?): String =
    when (schedule) {
        RoutineSchedule.WEEKDAY ->
            weekdayLabel(weekday)
        RoutineSchedule.ROTATION ->
            stringResource(R.string.train_routine_rotation, rotationLetter(rotationIndex) ?: "A")
    }

@Composable
fun weekdayLabel(weekday: Int?): String = stringResource(
    when (weekday) {
        1 -> R.string.train_weekday_mon
        2 -> R.string.train_weekday_tue
        3 -> R.string.train_weekday_wed
        4 -> R.string.train_weekday_thu
        5 -> R.string.train_weekday_fri
        6 -> R.string.train_weekday_sat
        else -> R.string.train_weekday_sun
    },
)

/** The equipment name the live session's subtitle states, from its closed key set. */
@Composable
fun equipmentLabel(key: String): String = stringResource(
    when (key) {
        "barbell" -> R.string.equipment_barbell
        "ez-bar" -> R.string.equipment_ez_bar
        "dumbbell" -> R.string.equipment_dumbbells
        "machine" -> R.string.equipment_machines
        "cable" -> R.string.equipment_cables
        "kettlebell" -> R.string.equipment_kettlebell
        "bands" -> R.string.equipment_bands
        "bodyweight" -> R.string.equipment_bodyweight
        "ball" -> R.string.equipment_ball
        else -> R.string.equipment_other
    },
)

/** A bar's label is UI copy, not a stored string (`Tech-Stack.md` §3). */
@Composable
fun barLabel(key: String): String = stringResource(
    when (key) {
        "barbell" -> R.string.equipment_barbell
        "ez" -> R.string.equipment_ez_bar
        "trap" -> R.string.equipment_trap_bar
        "smith" -> R.string.equipment_smith
        else -> R.string.train_plates_custom
    },
)

/** The glyph a piece of equipment carries in a media slot. */
fun equipmentIcon(key: String): androidx.compose.ui.graphics.vector.ImageVector = when (key) {
    "barbell", "ez-bar", "trap" -> app.openhealth.ui.icons.OhObjectIcons.barbell
    "dumbbell" -> app.openhealth.ui.icons.OhObjectIcons.dumbbell
    "kettlebell" -> app.openhealth.ui.icons.OhObjectIcons.kettlebell
    "machine", "cable", "smith" -> app.openhealth.ui.icons.OhObjectIcons.machine
    "bands" -> app.openhealth.ui.icons.OhObjectIcons.band
    else -> app.openhealth.ui.icons.OhModuleIcons.workout
}

/** The policies a routine can choose, in the order the editor offers them. */
val PROGRESSION_KEYS: List<String> = ProgressionPolicy.all.map { it.key }
