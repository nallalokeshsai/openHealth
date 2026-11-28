package app.openhealth.android.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import app.openhealth.R
import app.openhealth.android.MainActivity
import app.openhealth.android.OpenHealthApp
import app.openhealth.core.observation.ObservationType
import app.openhealth.data.repo.SettingKeys
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * A home-screen widget that mirrors the Today steps ring (`Tech-Stack.md` §2, §4): the
 * day's steps against the goal. It shows steps only, never weight, calories or a vital,
 * so a glance at a home screen reveals nothing the weight-neutral switch hides. It reads
 * the local database and opens the app on tap; nothing is fetched and nothing leaves
 * the device.
 */
class TodayWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_today)
        val steps = runCatching { stepsToday(context) }.getOrNull()
        val goal = runCatching { stepGoal(context) }.getOrDefault(DEFAULT_GOAL)
        if (steps == null) {
            views.setTextViewText(R.id.widget_steps, context.getString(R.string.widget_today_empty))
            views.setTextViewText(R.id.widget_goal, "")
        } else {
            views.setTextViewText(
                R.id.widget_steps,
                context.getString(R.string.widget_today_steps, "%,d".format(steps)),
            )
            views.setTextViewText(
                R.id.widget_goal,
                context.getString(R.string.widget_today_goal, "%,d".format(goal)),
            )
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(R.id.widget_root, open)
        appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, views) }
    }

    private fun stepsToday(context: Context): Int? {
        val store = (context.applicationContext as OpenHealthApp).container.store
        val date = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        return store.rollups.forDayAndType(date, ObservationType.STEPS)?.value?.value?.toInt()
    }

    private fun stepGoal(context: Context): Int {
        val store = (context.applicationContext as OpenHealthApp).container.store
        return store.settings.get(SettingKeys.STEP_GOAL)?.toIntOrNull() ?: DEFAULT_GOAL
    }

    private companion object {
        const val DEFAULT_GOAL = 8_000
    }
}
