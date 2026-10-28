package pl.szczodrzynski.edziennik.ui.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import java.util.concurrent.Executors
import pl.szczodrzynski.edziennik.ui.widgets.currentlesson.WidgetCurrentLessonProvider
import pl.szczodrzynski.edziennik.ui.widgets.luckynumber.WidgetLuckyNumberProvider
import pl.szczodrzynski.edziennik.ui.widgets.notifications.WidgetNotificationsProvider
import pl.szczodrzynski.edziennik.ui.widgets.timetable.WidgetTimetableProvider
import timber.log.Timber

object WidgetUpdater {
    @JvmField
    val executor = Executors.newSingleThreadExecutor()

    fun updateAll(context: Context) {
        update(context, WidgetTimetableProvider::class.java)
        update(context, WidgetNotificationsProvider::class.java)
        update(context, WidgetLuckyNumberProvider::class.java)
        update(context, WidgetCurrentLessonProvider::class.java)
    }

    fun update(context: Context, provider: Class<out AppWidgetProvider>) {
        try {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, provider))
            if (ids == null || ids.isEmpty()) return
            context.sendBroadcast(Intent(context, provider).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            })
        } catch (e: Exception) {
            Timber.e(e)
        }
    }
}
