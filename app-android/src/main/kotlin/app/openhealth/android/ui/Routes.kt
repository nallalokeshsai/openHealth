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
