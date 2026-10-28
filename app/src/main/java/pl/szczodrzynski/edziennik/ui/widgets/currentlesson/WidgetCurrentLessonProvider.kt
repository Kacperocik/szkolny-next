package pl.szczodrzynski.edziennik.ui.widgets.currentlesson

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.MainActivity
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.db.entity.Lesson
import pl.szczodrzynski.edziennik.data.db.full.LessonFull
import pl.szczodrzynski.edziennik.data.enums.NavTarget
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import pl.szczodrzynski.edziennik.ext.getJsonObject
import pl.szczodrzynski.edziennik.ext.pendingIntentFlag
import pl.szczodrzynski.edziennik.ext.putExtras
import pl.szczodrzynski.edziennik.ext.timeTill
import pl.szczodrzynski.edziennik.ext.toDrawable
import pl.szczodrzynski.edziennik.ui.widgets.WidgetConfig
import pl.szczodrzynski.edziennik.ui.widgets.WidgetUpdater
import pl.szczodrzynski.edziennik.ui.widgets.resolveWidgetTheme
import pl.szczodrzynski.edziennik.utils.models.Date
import pl.szczodrzynski.edziennik.utils.models.Time
import timber.log.Timber

private fun bellSyncDiffMillis(app: App): Long {
    var diff: Long = 0
    app.config.timetable.bellSyncDiff?.let {
        diff = (it.hour * 60 * 60 * 1000 + it.minute * 60 * 1000 + it.second * 1000).toLong()
        diff *= app.config.timetable.bellSyncMultiplier.toLong()
        diff *= -1
    }
    return diff
}

private fun todayLessons(app: App, profileId: Int, today: Date): List<LessonFull> =
    app.db.timetableDao().getAllForDateNow(profileId, today)
        .filter {
            it.displayDate == today &&
                it.type != Lesson.TYPE_NO_LESSONS &&
                !it.isCancelled &&
                it.displayStartTime != null &&
                it.displayEndTime != null
        }
        .sortedBy { it.displayStartTime!!.inMillis }

class WidgetCurrentLessonProvider : AppWidgetProvider() {
    companion object {
        private const val TAG = "WidgetCurrentLessonProvider"
        private const val MAX_EVENTS = 3
        const val ACTION_BOUNDARY_UPDATE = "pl.szczodrzynski.edziennik.action.CURRENT_LESSON_BOUNDARY"

        /**
         * Schedules a one-shot alarm at the nearest lesson boundary (start/end
         * of the current or next lesson) across all instances of this widget,
         * so the lesson switch happens even while the app process is dead.
         */
        fun requestBoundaryUpdate(context: Context) {
            WidgetUpdater.executor.execute {
                try {
                    val app = context.applicationContext as App
                    val manager = AppWidgetManager.getInstance(context)
                    val ids = manager.getAppWidgetIds(
                        ComponentName(context, WidgetCurrentLessonProvider::class.java)
                    )
                    if (ids.isEmpty()) {
                        cancelBoundaryUpdate(context)
                        return@execute
                    }
                    val triggerAt = nextBoundaryTriggerAt(app, ids)
                    if (triggerAt == null) {
                        cancelBoundaryUpdate(context)
                        return@execute
                    }
                    val intent = Intent(context, WidgetCurrentLessonProvider::class.java)
                        .setAction(ACTION_BOUNDARY_UPDATE)
                    val pi = PendingIntent.getBroadcast(
                        context, 0, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentFlag(),
                    )
                    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                } catch (e: Exception) {
                    Timber.e(e)
                }
            }
        }

        private fun nextBoundaryTriggerAt(app: App, ids: IntArray): Long? {
            val diff = bellSyncDiffMillis(app)
            val nowInDay = Time.getNow().inMillis + diff
            val today = Date.getToday()
            var minDelay: Long? = null
            ids.forEach { id ->
                val profileId = app.config.widgetConfigs.getJsonObject(id.toString())
                    ?.let { app.gson.fromJson(it, WidgetConfig::class.java) }?.profileId
                    ?: return@forEach
                todayLessons(app, profileId, today).forEach { lesson ->
                    val start = lesson.displayStartTime!!.inMillis
                    val end = lesson.displayEndTime!!.inMillis
                    val boundary = when {
                        nowInDay < start -> start
                        nowInDay <= end -> end
                        else -> return@forEach
                    }
                    val delay = boundary - nowInDay
                    if (delay > 0 && (minDelay == null || delay < minDelay!!)) {
                        minDelay = delay
                    }
                }
            }
            if (minDelay == null) {
                return null
            }
            return System.currentTimeMillis() + minDelay!!
        }

        private fun cancelBoundaryUpdate(context: Context) {
            try {
                val intent = Intent(context, WidgetCurrentLessonProvider::class.java)
                    .setAction(ACTION_BOUNDARY_UPDATE)
                val pi = PendingIntent.getBroadcast(
                    context, 0, intent,
                    PendingIntent.FLAG_NO_CREATE or pendingIntentFlag(),
                ) ?: return
                pi.cancel()
                val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                am.cancel(pi)
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_BOUNDARY_UPDATE -> {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, WidgetCurrentLessonProvider::class.java))
                if (ids.isNotEmpty()) {
                    onUpdate(context, manager, ids)
                } else {
                    cancelBoundaryUpdate(context)
                }
            }
            Intent.ACTION_CONFIGURATION_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED -> {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, WidgetCurrentLessonProvider::class.java))
                if (ids.isNotEmpty()) {
                    onUpdate(context, manager, ids)
                }
            }
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val app = context.applicationContext as App
        val widgetConfigs = app.config.widgetConfigs
        for (appWidgetId in appWidgetIds) {
            try {
                val config = widgetConfigs.getJsonObject(appWidgetId.toString())
                    ?.let { app.gson.fromJson(it, WidgetConfig::class.java) }
                    ?: continue
                val views = buildRemoteViews(app, context, appWidgetId, config)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
        requestBoundaryUpdate(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle?,
    ) {
        onUpdate(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val app = context.applicationContext as App
        val widgetConfigs = app.config.widgetConfigs
        appWidgetIds.forEach {
            widgetConfigs.remove(it.toString())
        }
        app.config.widgetConfigs = widgetConfigs
        requestBoundaryUpdate(context)
    }

    private fun buildRemoteViews(
        app: App,
        context: Context,
        appWidgetId: Int,
        config: WidgetConfig,
    ): RemoteViews {
        val layout = if (config.bigStyle) R.layout.widget_current_lesson_big else R.layout.widget_current_lesson
        val views = RemoteViews(app.packageName, layout)

        applyTheme(app, context, views, config)
        bindCurrentLesson(app, context, views, config)

        val refreshIntent = Intent(app, WidgetCurrentLessonProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
        }
        views.setOnClickPendingIntent(
            R.id.widgetCurrentLessonRefresh,
            PendingIntent.getBroadcast(
                context,
                appWidgetId,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentFlag(),
            ),
        )

        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            putExtra("profileId", config.profileId)
            putExtra("timetableDate", Date.getToday().stringY_m_d)
            putExtras("fragmentId" to NavTarget.TIMETABLE)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentFlag()
        views.setOnClickPendingIntent(
            R.id.widgetCurrentLessonRoot,
            PendingIntent.getActivity(context, appWidgetId, openIntent, flags),
        )
        return views
    }

    private fun applyTheme(app: App, context: Context, views: RemoteViews, config: WidgetConfig) {
        val isDark = config.isDarkTheme(context)
        val opacity = config.opacity.coerceIn(0f, 1f)
        views.setInt(R.id.widgetCurrentLessonBodyBg, "setAlpha", (opacity * 255f).toInt())

        val colors = resolveWidgetTheme(context, config)
        val defaultBodyColor = if (isDark) 0xFF1E1F25.toInt() else 0xFFF2F4F7.toInt()
        val bodyColor = if (config.appBackground) colors.background else defaultBodyColor
        views.setInt(R.id.widgetCurrentLessonBodyBg, "setColorFilter", bodyColor)

        views.setImageViewBitmap(
            R.id.widgetCurrentLessonRefresh,
            CommunityMaterial.Icon3.cmd_refresh
                .toDrawable(app, colors.onSurface, sizeDp = if (config.bigStyle) 28 else 20)
                .toBitmap(),
        )
        views.setInt(
            R.id.widgetCurrentLessonRefresh,
            "setBackgroundResource",
            if (isDark) R.drawable.widget_button_ripple else R.drawable.widget_button_ripple_dark,
        )

        views.setTextColor(R.id.widgetCurrentLessonProfile, colors.secondary)
        views.setTextColor(R.id.widgetCurrentLessonSubject, colors.onSurface)
        views.setTextColor(R.id.widgetCurrentLessonClassroom, colors.onSurfaceVariant)
        views.setTextColor(R.id.widgetCurrentLessonTimeRange, colors.onSurfaceVariant)
        views.setTextColor(R.id.widgetCurrentLessonTimeLeft, colors.onSurface)
        views.setTextColor(R.id.widgetCurrentLessonEventsLabel, colors.secondary)
        views.setTextColor(R.id.widgetCurrentLessonEvent1, colors.onSurface)
        views.setTextColor(R.id.widgetCurrentLessonEvent2, colors.onSurface)
        views.setTextColor(R.id.widgetCurrentLessonEvent3, colors.onSurface)
        views.setTextColor(R.id.widgetCurrentLessonEmpty, colors.onSurfaceVariant)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setColorStateList(
                R.id.widgetCurrentLessonProgress,
                "setProgressTintList",
                ColorStateList.valueOf(colors.primary),
            )
            views.setColorStateList(
                R.id.widgetCurrentLessonProgress,
                "setProgressBackgroundTintList",
                ColorStateList.valueOf(ColorUtils.setAlphaComponent(colors.primary, 0x40)),
            )
        }
    }

    private fun bindCurrentLesson(
        app: App,
        context: Context,
        views: RemoteViews,
        config: WidgetConfig,
    ) {
        val eventViews = listOf(
            R.id.widgetCurrentLessonEvent1,
            R.id.widgetCurrentLessonEvent2,
            R.id.widgetCurrentLessonEvent3,
        )
        val profile = app.db.profileDao().getByIdNow(config.profileId)
        val profileName = profile?.studentNameLong?.takeIf { it.isNotBlank() }
            ?: profile?.name
            ?: app.getString(R.string.widget_lucky_number_no_profile)
        views.setTextViewText(R.id.widgetCurrentLessonProfile, profileName)

        if (profile == null) {
            showEmpty(views, eventViews, app.getString(R.string.widget_lucky_number_no_profile))
            return
        }

        val now = Time.fromMillis(Time.getNow().inMillis + bellSyncDiffMillis(app))
        val today = Date.getToday()

        val lessonsToday = todayLessons(app, profile.id, today)

        val current = lessonsToday.firstOrNull { now in it.displayStartTime!!..it.displayEndTime!! }
        if (current != null) {
            showLesson(app, context, views, eventViews, profile.id, today, current, now, isNext = false)
            return
        }

        val next = lessonsToday.firstOrNull { it.displayStartTime!! > now }
        if (next != null) {
            showLesson(app, context, views, eventViews, profile.id, today, next, now, isNext = true)
            return
        }

        showEmpty(views, eventViews, app.getString(R.string.widget_current_lesson_no_lesson))
    }

    private fun showLesson(
        app: App,
        context: Context,
        views: RemoteViews,
        eventViews: List<Int>,
        profileId: Int,
        today: Date,
        lesson: LessonFull,
        now: Time,
        isNext: Boolean,
    ) {
        lesson.filterNotes()
        val subject = lesson.getNoteSubstituteText(showNotes = true)
            ?: lesson.displaySubjectName
            ?: app.getString(R.string.timetable_no_subject_name)
        val classroom = lesson.displayClassroom?.takeIf { it.isNotBlank() }
            ?: app.getString(R.string.timetable_no_classroom)
        val start = lesson.displayStartTime!!
        val end = lesson.displayEndTime!!

        val progress: Int
        val timeText: String
        if (isNext) {
            progress = 0
            val diffSeconds = ((start.inMillis - now.inMillis) / 1000).toInt().coerceAtLeast(0)
            timeText = context.timeTill(diffSeconds)
        } else {
            val length = (end.inMillis - start.inMillis).coerceAtLeast(1)
            val passed = (now.inMillis - start.inMillis).coerceIn(0, length)
            progress = (passed * 100 / length).toInt().coerceIn(0, 100)
            val minutesLeft = ((end.inMillis - now.inMillis + 59999) / 60000).toInt().coerceAtLeast(0)
            timeText = context.resources.getQuantityString(R.plurals.time_left_text, minutesLeft) +
                " " + context.resources.getQuantityString(R.plurals.time_left_minutes, minutesLeft, minutesLeft)
        }

        views.setViewVisibility(R.id.widgetCurrentLessonContent, View.VISIBLE)
        views.setViewVisibility(R.id.widgetCurrentLessonEmpty, View.GONE)
        views.setTextViewText(R.id.widgetCurrentLessonSubject, subject)
        views.setTextViewText(R.id.widgetCurrentLessonClassroom, classroom)
        views.setTextViewText(
            R.id.widgetCurrentLessonTimeRange,
            start.stringHM + " - " + end.stringHM,
        )
        views.setTextViewText(R.id.widgetCurrentLessonTimeLeft, timeText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // live per-second countdown, ticking in the launcher process
            // without waking the app - refreshed to the next state by the boundary alarm
            val remainingMs = (if (isNext) start.inMillis - now.inMillis else end.inMillis - now.inMillis)
                .coerceAtLeast(0)
            val format = context.getString(
                if (isNext) R.string.widget_current_lesson_countdown_till
                else R.string.widget_current_lesson_countdown_left
            )
            views.setChronometer(
                R.id.widgetCurrentLessonTimeLeft,
                SystemClock.elapsedRealtime() + remainingMs,
                format,
                true,
            )
            views.setChronometerCountDown(R.id.widgetCurrentLessonTimeLeft, true)
        }
        views.setProgressBar(R.id.widgetCurrentLessonProgress, 100, progress, false)

        val events = app.db.eventDao().getAllByDateNow(profileId, today)
            .filter { it.time != null && it.time == start }
            .take(MAX_EVENTS)
        views.setViewVisibility(
            R.id.widgetCurrentLessonEventsLabel,
            if (events.isEmpty()) View.GONE else View.VISIBLE,
        )
        eventViews.forEachIndexed { index, viewId ->
            if (index < events.size) {
                views.setViewVisibility(viewId, View.VISIBLE)
                views.setTextViewText(viewId, events[index].topic)
            } else {
                views.setViewVisibility(viewId, View.GONE)
            }
        }
    }

    private fun showEmpty(views: RemoteViews, eventViews: List<Int>, text: String) {
        views.setViewVisibility(R.id.widgetCurrentLessonContent, View.GONE)
        views.setViewVisibility(R.id.widgetCurrentLessonEmpty, View.VISIBLE)
        views.setTextViewText(R.id.widgetCurrentLessonEmpty, text)
        views.setViewVisibility(R.id.widgetCurrentLessonEventsLabel, View.GONE)
        eventViews.forEach { views.setViewVisibility(it, View.GONE) }
    }
}
