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

@Serializable
data object TimelineRoute

@Serializable
data object TrashRoute

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
data class FoodEditorRoute(val foodId: String? = null, val recipe: Boolean = false)

@Serializable
data class MicrosRoute(val date: String)
