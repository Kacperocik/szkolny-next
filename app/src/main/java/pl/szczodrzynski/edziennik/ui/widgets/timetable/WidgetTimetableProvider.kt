/*
 * Copyright (c) Kuba Szczodrzyński 2020-1-6.
 */

package pl.szczodrzynski.edziennik.ui.widgets.timetable

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.util.SparseArray
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.withStyledAttributes
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.MainActivity
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.api.edziennik.EdziennikTask
import pl.szczodrzynski.edziennik.data.db.entity.Lesson
import pl.szczodrzynski.edziennik.data.db.entity.Lesson.Companion.TYPE_NO_LESSONS
import pl.szczodrzynski.edziennik.data.enums.NavTarget
import pl.szczodrzynski.edziennik.ext.filterOutArchived
import pl.szczodrzynski.edziennik.ext.getJsonObject
import pl.szczodrzynski.edziennik.ext.getJsonObject
import pl.szczodrzynski.edziennik.ext.pendingIntentFlag
import pl.szczodrzynski.edziennik.ext.pendingIntentMutable
import pl.szczodrzynski.edziennik.ext.putExtras
import pl.szczodrzynski.edziennik.ext.toDrawable
import pl.szczodrzynski.edziennik.ui.widgets.LessonDialogActivity
import pl.szczodrzynski.edziennik.ui.widgets.WidgetConfig
import pl.szczodrzynski.edziennik.ui.widgets.resolveWidgetTheme
import pl.szczodrzynski.edziennik.utils.models.Date
import pl.szczodrzynski.edziennik.utils.models.ItemWidgetTimetableModel
import pl.szczodrzynski.edziennik.utils.models.Time
import pl.szczodrzynski.edziennik.utils.models.Week
import timber.log.Timber
import java.lang.reflect.InvocationTargetException


class WidgetTimetableProvider : AppWidgetProvider() {
    companion object {
        const val ACTION_SYNC_DATA = "ACTION_SYNC_DATA"
        private const val TAG = "WidgetTimetable"

        var timetables: SparseArray<List<ItemWidgetTimetableModel>>? = null

        @JvmStatic
        fun loadWidgetData(context: Context, appWidgetId: Int) {
            try {
                val app = context.applicationContext as App
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val provider = WidgetTimetableProvider()
                val widgetConfigs = app.config.widgetConfigs
                val config = widgetConfigs.getJsonObject(appWidgetId.toString())?.let { app.gson.fromJson(it, WidgetConfig::class.java) } ?: WidgetConfig(-1)

                var bellSyncDiffMillis: Long = 0
                app.config.timetable.bellSyncDiff?.let {
                    bellSyncDiffMillis = (it.hour * 60 * 60 * 1000 + it.minute * 60 * 1000 + it.second * 1000).toLong()
                    bellSyncDiffMillis *= app.config.timetable.bellSyncMultiplier.toLong()
                    bellSyncDiffMillis *= -1
                }

                val colors = resolveWidgetTheme(context, config)
                val isDark = colors.isDark
                val views = if (config.bigStyle) {
                    RemoteViews(context.packageName, if (isDark) R.layout.widget_timetable_dark_big else R.layout.widget_timetable_big)
                } else {
                    RemoteViews(context.packageName, if (isDark) R.layout.widget_timetable_dark else R.layout.widget_timetable)
                }

                if (timetables == null) timetables = SparseArray()
                provider.prepareAppWidget(app, context, appWidgetId, views, config, colors, bellSyncDiffMillis, timetables!!)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (e: Exception) {
                Timber.e(e)
            }
        }

        fun getPendingSelfIntent(context: Context, action: String): PendingIntent {
            val intent = Intent(context, WidgetTimetableProvider::class.java)
            intent.action = action
            return getPendingSelfIntent(context, intent)
        }

        private fun getPendingSelfIntent(context: Context, intent: Intent): PendingIntent {
            return PendingIntent.getBroadcast(context, 0, intent, pendingIntentFlag())
        }

        fun drawableToBitmap(drawable: Drawable): Bitmap {

            if (drawable is BitmapDrawable) {
                return drawable.bitmap
            }

            val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)

            return bitmap
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (ACTION_SYNC_DATA == intent.action) {
            EdziennikTask.sync().enqueue(context)
        } else if (Intent.ACTION_CONFIGURATION_CHANGED == intent.action) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, WidgetTimetableProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            if (appWidgetIds != null && appWidgetIds.isNotEmpty()) {
                onUpdate(context, appWidgetManager, appWidgetIds)
            }
        }
        super.onReceive(context, intent)
    }

    private val ignoreCancelled = false

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        pl.szczodrzynski.edziennik.ui.widgets.WidgetUpdater.executor.execute {
            try {
                updateWidgets(context, appWidgetManager)
            } catch (e: Exception) {
                Timber.e(e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun updateWidgets(context: Context, appWidgetManager: AppWidgetManager) {
        val thisWidget = ComponentName(context, WidgetTimetableProvider::class.java)
        val app = context.applicationContext as App
        val widgetConfigs = app.config.widgetConfigs

        var bellSyncDiffMillis: Long = 0
        app.config.timetable.bellSyncDiff?.let {
            bellSyncDiffMillis = (it.hour * 60 * 60 * 1000 + it.minute * 60 * 1000 + it.second * 1000).toLong()
            bellSyncDiffMillis *= app.config.timetable.bellSyncMultiplier.toLong()
            bellSyncDiffMillis *= -1
        }

        val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget) ?: return
        val nextTimetables = SparseArray<List<ItemWidgetTimetableModel>>()
        val nextViews = SparseArray<RemoteViews>()

        allWidgetIds.forEach { appWidgetId ->
            try {
                val config = widgetConfigs.getJsonObject(appWidgetId.toString())
                    ?.let { app.gson.fromJson(it, WidgetConfig::class.java) }
                    ?: return@forEach

                val colors = resolveWidgetTheme(context, config)
                val isDark = colors.isDark
                val views = if (config.bigStyle) {
                    RemoteViews(context.packageName, if (isDark) R.layout.widget_timetable_dark_big else R.layout.widget_timetable_big)
                } else {
                    RemoteViews(context.packageName, if (isDark) R.layout.widget_timetable_dark else R.layout.widget_timetable)
                }

                val refreshIntent = Intent(app, WidgetTimetableProvider::class.java)
                refreshIntent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                refreshIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, allWidgetIds)
                views.setOnClickPendingIntent(
                    R.id.widgetTimetableRefresh,
                    PendingIntent.getBroadcast(
                        context,
                        appWidgetId,
                        refreshIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentFlag(),
                    ),
                )
                views.setViewVisibility(R.id.widgetTimetableSync, View.GONE)
                views.setViewVisibility(R.id.widgetTimetableLoading, View.GONE)

                prepareAppWidget(app, context, appWidgetId, views, config, colors, bellSyncDiffMillis, nextTimetables)
                nextViews.put(appWidgetId, views)
            } catch (e: Exception) {
                Timber.e(e)
            }
        }

        timetables = nextTimetables
        allWidgetIds.forEach { appWidgetId ->
            val views = nextViews.get(appWidgetId) ?: return@forEach
            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widgetTimetableListView)
        }
    }

    @SuppressLint("ResourceType")
    private fun prepareAppWidget(
            app: App,
            context: Context,
            appWidgetId: Int,
            views: RemoteViews,
            widgetConfig: WidgetConfig,
            colors: pl.szczodrzynski.edziennik.ui.widgets.WidgetThemeColors,
            bellSyncDiffMillis: Long,
            targetTimetables: SparseArray<List<ItemWidgetTimetableModel>>,
    ) {
        // get the current bell-synced time
        val now = Time.fromMillis(Time.getNow().inMillis + bellSyncDiffMillis)

        // set the widget transparency
        val opacity = widgetConfig.opacity.coerceIn(0f, 1f)
        val alphaInt = (opacity * 255f).toInt()
        views.setInt(R.id.widgetTimetableHeaderBg, "setAlpha", alphaInt)
        views.setInt(R.id.widgetTimetableBackgroundBg, "setAlpha", alphaInt)

        val isDark = colors.isDark
        val primaryColor = colors.primary
        val defaultBodyColor = if (isDark) 0xFF1E1F25.toInt() else 0xFFF2F4F7.toInt()
        val bodyColor = if (widgetConfig.appBackground) colors.background else defaultBodyColor
        val headerColor = if (widgetConfig.headerSameAsBody) bodyColor else primaryColor

        views.setInt(R.id.widgetTimetableHeaderBg, "setColorFilter", headerColor)
        views.setInt(R.id.widgetTimetableBackgroundBg, "setColorFilter", bodyColor)

        val rippleRes = if (widgetConfig.headerSameAsBody && !isDark) R.drawable.widget_button_ripple_dark else R.drawable.widget_button_ripple
        views.setInt(R.id.widgetTimetableRefresh, "setBackgroundResource", rippleRes)
        views.setInt(R.id.widgetTimetableSync, "setBackgroundResource", rippleRes)

        if (widgetConfig.headerSameAsBody) {
            val titleColor = if (isDark) 0xFFE6E1E5.toInt() else 0xFF1C1B1F.toInt()
            val subtitleColor = if (isDark) 0xFFCAC4D0.toInt() else 0xFF49454F.toInt()
            views.setTextColor(R.id.widgetTimetableTitle, titleColor)
            views.setTextColor(R.id.widgetTimetableSubtitle, subtitleColor)
            val iconColor = if (isDark) Color.WHITE else 0xFF1C1B1F.toInt()
            views.setImageViewBitmap(
                R.id.widgetTimetableRefresh,
                CommunityMaterial.Icon3.cmd_refresh
                    .toDrawable(app, iconColor, sizeDp = if (widgetConfig.bigStyle) 28 else 20)
                    .toBitmap()
            )
        } else {
            views.setTextColor(R.id.widgetTimetableTitle, Color.WHITE)
            views.setTextColor(R.id.widgetTimetableSubtitle, 0xFFD0E4FF.toInt())
            views.setImageViewBitmap(
                R.id.widgetTimetableRefresh,
                CommunityMaterial.Icon3.cmd_refresh
                    .toDrawable(app, Color.WHITE, sizeDp = if (widgetConfig.bigStyle) 28 else 20)
                    .toBitmap()
            )
        }

        val unified = widgetConfig.profileId == -1

        // get all profiles or one profile with the specified id
        val profileList = if (unified)
            app.db.profileDao().allNow.filterOutArchived()
        else
            listOfNotNull(app.db.profileDao().getByIdNow(widgetConfig.profileId))

        // no profile was found
        if (profileList.isEmpty()) {
            views.setViewVisibility(R.id.widgetTimetableLoading, View.VISIBLE)
            views.setTextViewText(R.id.widgetTimetableLoading, app.getString(R.string.widget_timetable_profile_doesnt_exist))
            return
        }

        views.setViewVisibility(R.id.widgetTimetableLoading, View.GONE)

        // set lesson search bounds
        val today = Date.getToday()
        val searchEnd = today.clone().stepForward(0, 0, 7)

        var scrollPos = 0

        var profileId: Int? = null
        var displayingDate: Date? = null

        val models = mutableListOf<ItemWidgetTimetableModel>()

        // get all lessons within the search bounds
        val lessonList = app.db.timetableDao().getBetweenDatesNow(today, searchEnd)

        for (profile in profileList) {

            // search for lessons to display
            val timetableDate = Date.getToday()
            var checkedDays = 0
            var lessons = lessonList.filter {
                it.profileId == profile.id
                        && it.displayDate == timetableDate
                        /*&& it.displayEndTime > now*/
                        && !(it.isCancelled && ignoreCancelled)
            }
            while ((lessons.isEmpty() || lessons.none {
                        it.type != Lesson.TYPE_NO_LESSONS
                                && (it.displayDate != today
                                || (it.displayDate == today
                                && it.displayEndTime != null
                                && it.displayEndTime!! >= now))
                                && !it.isCancelled
                    }) && checkedDays < 7) {

                timetableDate.stepForward(0, 0, 1)
                lessons = lessonList.filter {
                    it.profileId == profile.id
                            && it.displayDate == timetableDate
                }

                if (lessons.isEmpty())
                    break

                /*lessons = lessons.filterNot {
                    it.isCancelled && ignoreCancelled
                }*/

                checkedDays++
            }

            // add a profile separator with its name
            if (unified) {
                val separator = ItemWidgetTimetableModel()
                separator.profileId = profile.id
                separator.bigStyle = widgetConfig.bigStyle
                separator.darkTheme = isDark
                separator.separatorProfileName = profile.name
                if (lessons.isNotEmpty() && lessons[0].type != TYPE_NO_LESSONS) {
                    separator.lessonDate = timetableDate
                }
                models.add(separator)
            }

            views.setViewVisibility(R.id.widgetTimetableListView, View.VISIBLE)
            views.setViewVisibility(R.id.widgetTimetableNoTimetable, View.GONE)
            views.setViewVisibility(R.id.widgetTimetableNoLessons, View.GONE)

            // set the displayingDate to show in the header
            if (!unified) {
                if (lessons.isNotEmpty())
                    displayingDate = timetableDate
                profileId = profile.id
                if (lessons.isEmpty() && checkedDays < 7) {
                    views.setViewVisibility(R.id.widgetTimetableListView, View.GONE)
                    views.setViewVisibility(R.id.widgetTimetableNoTimetable, View.VISIBLE)
                }
                else if (lessons.none { !it.isCancelled } || lessons.size == 1 && lessons[0].type == Lesson.TYPE_NO_LESSONS) {
                    views.setViewVisibility(R.id.widgetTimetableListView, View.GONE)
                    views.setViewVisibility(R.id.widgetTimetableNoLessons, View.VISIBLE)
                }
            }
            else {
                if (lessons.isEmpty() && checkedDays < 7) {
                    val separator = ItemWidgetTimetableModel()
                    separator.profileId = profile.id
                    separator.bigStyle = widgetConfig.bigStyle
                    separator.darkTheme = isDark
                    separator.isNoTimetableItem = true
                    models.add(separator)
                }
                if (lessons.none { !it.isCancelled } || lessons.size == 1 && lessons[0].type == Lesson.TYPE_NO_LESSONS) {
                    val separator = ItemWidgetTimetableModel()
                    separator.profileId = profile.id
                    separator.bigStyle = widgetConfig.bigStyle
                    separator.darkTheme = isDark
                    separator.isNoLessonsItem = true
                    models.add(separator)
                }
            }

            // get all events for the current date
            val events = app.db.eventDao().getAllByDateNow(profile.id, timetableDate)

            lessons.forEachIndexed { pos, lesson ->
                if (lesson.type == TYPE_NO_LESSONS)
                    return@forEachIndexed
                val model = ItemWidgetTimetableModel()

                model.bigStyle = widgetConfig.bigStyle
                model.darkTheme = isDark
                model.colorSubjectBackground = widgetConfig.colorSubjectBackground
                model.appBackground = widgetConfig.appBackground

                model.themeColorSecondary = colors.secondary
                model.themeColorOnSurfaceVariant = colors.onSurfaceVariant
                model.themeColorTextPrimary = colors.onSurface

                model.profileId = profile.id

                model.lessonId = lesson.id
                model.lessonDate = timetableDate
                model.startTime = lesson.displayStartTime ?: return@forEachIndexed
                model.endTime = lesson.displayEndTime ?: return@forEachIndexed

                // check if the lesson has already passed or it's currently in progress
                if (lesson.displayDate == today) {
                    lesson.displayEndTime?.let { endTime ->
                        model.lessonPassed = now > endTime
                        lesson.displayStartTime?.let { startTime ->
                            model.lessonCurrent = now in startTime..endTime
                        }
                    }
                }

                // set where should the list view scroll to
                if (model.lessonCurrent) {
                    scrollPos = pos
                } else if (model.lessonPassed) {
                    scrollPos = pos + 1
                }

                // remove notes from other profiles
                lesson.filterNotes()
                // set the subject and classroom name
                model.subjectName = lesson.getNoteSubstituteText(showNotes = true)
                    ?: lesson.displaySubjectName
                model.classroomName = lesson.displayClassroom

                // set the bell sync to calculate progress in ListProvider
                model.bellSyncDiffMillis = bellSyncDiffMillis

                // make the model aware of the lesson type
                when (lesson.type) {
                    Lesson.TYPE_CANCELLED,
                    Lesson.TYPE_SHIFTED_SOURCE -> {
                        model.lessonCancelled = true
                    }
                    Lesson.TYPE_CHANGE,
                    Lesson.TYPE_SHIFTED_TARGET -> {
                        model.lessonChange = true
                        model.lessonChangeNoClassroom = lesson.classroom.isNullOrBlank()
                    }
                }

                // add every event on this lesson
                for (event in events) {
                    if (event.time == null || event.time != lesson.displayStartTime)
                        continue
                    val eventColor = if (event.isHomework) ItemWidgetTimetableModel.EVENT_COLOR_HOMEWORK else app.eventManager.getEventColor(event)
                    model.eventColors.add(eventColor)
                }

                models += model
            }
        }

        if (unified) {
            // set the title for an unified widget
            views.setTextViewText(R.id.widgetTimetableTitle, app.getString(R.string.widget_timetable_title_unified))
            views.setViewVisibility(R.id.widgetTimetableSubtitle, View.GONE)
        } else {
            // set the title to present the widget's profile
            views.setTextViewText(R.id.widgetTimetableTitle, profileList[0].name)
            views.setViewVisibility(R.id.widgetTimetableTitle, View.VISIBLE)
            // make the subtitle show current date for these lessons
            displayingDate?.let {
                when (Date.diffDays(it, Date.getToday())) {
                    0 -> views.setTextViewText(R.id.widgetTimetableSubtitle, app.getString(R.string.day_today_format, Week.getFullDayName(it.weekDay)))
                    1 -> views.setTextViewText(R.id.widgetTimetableSubtitle, app.getString(R.string.day_tomorrow_format, Week.getFullDayName(it.weekDay)))
                    else -> views.setTextViewText(R.id.widgetTimetableSubtitle, Week.getFullDayName(it.weekDay) + " " + it.formattedString)
                }
            }
        }

        // intent running when the header is clicked
        val headerIntent = Intent(app, MainActivity::class.java)
        headerIntent.action = "android.intent.action.MAIN"
        if (!unified) {
            // per-profile widget should redirect to it + correct day
            profileId?.let {
                headerIntent.putExtra("profileId", it)
            }
            displayingDate?.let {
                headerIntent.putExtra("timetableDate", it.stringY_m_d)
            }
        }
        headerIntent.putExtras("fragmentId" to NavTarget.TIMETABLE)
        val headerPendingIntent = PendingIntent.getActivity(app, appWidgetId, headerIntent, FLAG_UPDATE_CURRENT or pendingIntentMutable())
        views.setOnClickPendingIntent(R.id.widgetTimetableHeader, headerPendingIntent)

        targetTimetables.put(appWidgetId, models)

        // apply the list service to the list view
        val listIntent = Intent(app, WidgetTimetableService::class.java)
        listIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        listIntent.data = Uri.parse(listIntent.toUri(Intent.URI_INTENT_SCHEME))
        views.setRemoteAdapter(R.id.widgetTimetableListView, listIntent)

        // create an intent used to display the lesson details dialog
        val itemIntent = Intent(app, LessonDialogActivity::class.java)
        itemIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK/* or Intent.FLAG_ACTIVITY_CLEAR_TASK*/)
        val itemPendingIntent = PendingIntent.getActivity(app, appWidgetId, itemIntent, FLAG_UPDATE_CURRENT or pendingIntentMutable())
        views.setPendingIntentTemplate(R.id.widgetTimetableListView, itemPendingIntent)

        if (!unified)
            views.setScrollPosition(R.id.widgetTimetableListView, scrollPos)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val app = context.applicationContext as App
        val widgetConfigs = app.config.widgetConfigs
        appWidgetIds.forEach {
            widgetConfigs.remove(it.toString())
        }
        app.config.widgetConfigs = widgetConfigs
    }
}
