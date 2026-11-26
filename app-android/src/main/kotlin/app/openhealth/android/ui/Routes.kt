package app.openhealth.android.ui

import kotlinx.serialization.Serializable

/**
 * Type-safe routes (`Tech-Stack.md` §4). Settings, the timeline and Trash are pushed
 * pages; the five tabs are roots and are peers.
 */
@Serializable
data object TodayRoute

@Serializable
data object VitalsRoute

@Serializable
data object TrainRoute

@Serializable
data object NutritionRoute

@Serializable
data object StatsRoute

@Serializable
data object SettingsRoute

/** B8: the Devices and integrations page, pushed from Settings. */
@Serializable
data object DevicesRoute

@Serializable
data object TimelineRoute

@Serializable
data object TrashRoute

/** B10: the review inbox for duplicates, out-of-range values and disagreeing sources. */
@Serializable
data object ReviewRoute

@Serializable
data class RoutineRoute(val id: String)

@Serializable
data class RoutineEditorRoute(val id: String? = null)

@Serializable
data class LiveSessionRoute(val sessionId: String? = null)

@Serializable
data class SessionSummaryRoute(val sessionId: String)

@Serializable
data class ExerciseHistoryRoute(val exerciseId: String)

// B6 nutrition pages: the scanner needs the camera, and the two long forms need a
// scrollable page rather than a sheet.
@Serializable
data object BarcodeRoute

@Serializable
data class FoodEditorRoute(
    val foodId: String? = null,
    val recipe: Boolean = false,
    val barcode: String? = null,
)

@Serializable
data class MicrosRoute(val date: String)

// B7: one vital's detail page. The type is the ObservationType name, so a route survives
// process death without a live object.
@Serializable
data class VitalDetailRoute(val type: String)

// B11: movement. Routes carry ids/names only; the live session lives in the services.
@Serializable
data object ActivityListRoute

@Serializable
data class ActivityDetailRoute(val id: String)

@Serializable
data object CardioLogRoute

@Serializable
data object GpsRecordRoute

@Serializable
data object YogaLibraryRoute

@Serializable
data class YogaPlayerRoute(val flowId: String)

@Serializable
data object MapPacksRoute

// B12: sleep, the clinical record, reports and the weekly review.
@Serializable
data object SleepRoute

@Serializable
data object ClinicalRoute

@Serializable
data class ClinicalEditorRoute(val section: String, val id: String? = null)

@Serializable
data object ReportsRoute

@Serializable
data object WeeklyReviewRoute
