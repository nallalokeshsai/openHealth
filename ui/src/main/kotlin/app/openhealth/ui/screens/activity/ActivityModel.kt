package app.openhealth.ui.screens.activity

import app.openhealth.core.activity.ActivityPoint
import app.openhealth.core.activity.ActivityType
import app.openhealth.core.activity.YogaFlow
import app.openhealth.core.activity.YogaLevel
import app.openhealth.core.activity.YogaPose
import app.openhealth.ui.components.ChartTableModel
import app.openhealth.ui.screens.devices.ProvenanceLine

/** The three ways to start a movement session, shown on the Train root. */
data class MovementModel(val rows: List<MovementRow>, val note: String)

data class MovementRow(val key: String, val title: String, val subtitle: String)

/** One row of the activity history. */
data class ActivityRowModel(
    val id: String,
    val title: String,
    val subtitle: String,
    val distanceText: String?,
    val durationText: String,
    val sourceLabel: String,
    val hasRoute: Boolean,
    val type: ActivityType,
)

data class ActivityHistoryModel(
    val rows: List<ActivityRowModel>,
    val weekCount: Int,
    val weekMinutes: Int,
    val weekDistanceText: String?,
    val note: String,
    val moduleOff: Boolean = false,
    val canImport: Boolean = true,
    val message: String? = null,
)

data class ActivitySplitRow(
    val index: Int,
    val distanceText: String,
    val paceText: String,
    val elevationText: String,
    val heartRateText: String,
)

data class ActivityZoneRow(
    val label: String,
    val rangeText: String,
    val timeText: String,
    val share: Float,
)

data class ActivityDetailModel(
    val id: String,
    val title: String,
    val typeLabel: String,
    val sourceLabel: String,
    val dateText: String,
    val startedText: String,
    val durationText: String,
    val distanceText: String?,
    val paceText: String?,
    val elevationText: String?,
    val heartRateText: String?,
    val energyText: String?,
    val splits: List<ActivitySplitRow>,
    /** Fastest 1 km and 5 km windows, when the route is long enough (title to time). */
    val bestEfforts: List<Pair<String, String>> = emptyList(),
    val zones: List<ActivityZoneRow>,
    val zoneNote: String,
    val table: ChartTableModel?,
    val route: List<ActivityPoint>,
    val notes: String?,
    val provenance: List<ProvenanceLine>,
    val isYoga: Boolean = false,
    val yogaFlowName: String? = null,
    val muscleLabels: List<String> = emptyList(),
)

data class YogaFlowRow(
    val id: String,
    val name: String,
    val subtitle: String,
    val levelLabel: String,
    val focus: String,
    val minutes: Int,
    val poseCount: Int,
    val favourite: Boolean,
    val primaryMuscles: List<app.openhealth.core.body.Muscle> = emptyList(),
    val secondaryMuscles: List<app.openhealth.core.body.Muscle> = emptyList(),
)

data class YogaPoseRow(
    val id: String,
    val name: String,
    val sanskrit: String,
    val levelLabel: String,
    val muscleLabels: List<String>,
    val why: String,
    val modification: String,
    val contraindications: String?,
    val instructions: List<String>,
    val breathCue: String,
    val primaryMuscles: List<app.openhealth.core.body.Muscle> = emptyList(),
    val secondaryMuscles: List<app.openhealth.core.body.Muscle> = emptyList(),
)

data class YogaLibraryModel(
    val flows: List<YogaFlowRow>,
    val poses: List<YogaPoseRow>,
    val note: String,
    val weekMinutes: Int = 0,
    val streakDays: Int = 0,
    val moduleOff: Boolean = false,
)

/** One installed map pack. */
data class MapPackModel(val name: String, val sizeText: String, val installedText: String)

data class MapPacksModel(
    val packs: List<MapPackModel>,
    val note: String,
    val downloading: Boolean = false,
    val progress: Float = 0f,
    val message: String? = null,
    val canDownload: Boolean = true,
)

/** Presentation-only value used by the player header. */
data class YogaPlayerHeader(val flow: YogaFlow, val favourite: Boolean)

data class YogaPlayerModel(
    val flow: YogaFlow,
    val state: app.openhealth.core.activity.YogaPlayerState,
    val breath: app.openhealth.core.activity.BreathPattern,
    val voiceOn: Boolean,
    val startedAtEpochMillis: Long,
)

data class YogaFinishModel(
    val flowId: String,
    val flowName: String,
    val minutes: Int,
    val posesHeld: Int,
    val muscleLabels: List<String>,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long,
)

data class GpsRecordingModel(
    val type: ActivityType,
    val elapsedSeconds: Long,
    val distanceM: Double,
    val paused: Boolean,
    val hasFix: Boolean,
    val recovered: Boolean = false,
    val message: String? = null,
)

fun YogaLevel.label(): String = name.lowercase()

fun YogaPose.muscleNames(): List<String> = primaryMuscles.map { it.labelKey }
