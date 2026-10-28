/*
 * Copyright (c) Kuba Szczodrzyński 2020-1-6.
 */

package pl.szczodrzynski.edziennik.ui.widgets.notifications

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.MainActivity
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.core.receiver.SzkolnyReceiver
import pl.szczodrzynski.edziennik.data.enums.NavTarget
import pl.szczodrzynski.edziennik.ext.Bundle
import pl.szczodrzynski.edziennik.ext.getJsonObject
import pl.szczodrzynski.edziennik.ext.pendingIntentMutable
import pl.szczodrzynski.edziennik.ext.putExtras
import pl.szczodrzynski.edziennik.ext.toDrawable
import pl.szczodrzynski.edziennik.ui.widgets.WidgetConfig
import pl.szczodrzynski.edziennik.ui.widgets.resolveWidgetTheme

class WidgetNotificationsProvider : AppWidgetProvider() {
    companion object {
        private const val TAG = "WidgetNotificationsProvider"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_CONFIGURATION_CHANGED == intent.action) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, WidgetNotificationsProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            if (appWidgetIds != null && appWidgetIds.isNotEmpty()) {
                onUpdate(context, appWidgetManager, appWidgetIds)
            }
        }
        super.onReceive(context, intent)
    }

    @SuppressLint("ResourceType")
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val app = context.applicationContext as App
        val widgetConfigs = app.config.widgetConfigs
        for (appWidgetId in appWidgetIds) {
            val config = widgetConfigs.getJsonObject(appWidgetId.toString())?.let { app.gson.fromJson(it, WidgetConfig::class.java) } ?: continue

            val isDark = config.isDarkTheme(context)
            val iconSize = if (config.bigStyle) 28 else 20

            val views: RemoteViews = if (config.bigStyle) {
                RemoteViews(app.packageName, if (isDark) R.layout.widget_notifications_dark_big else R.layout.widget_notifications_big)
            } else {
                RemoteViews(app.packageName, if (isDark) R.layout.widget_notifications_dark else R.layout.widget_notifications)
            }

            // transparency
            val opacity = config.opacity.coerceIn(0f, 1f)
            val alphaInt = (opacity * 255f).toInt()
            views.setInt(R.id.widgetNotificationsHeaderBg, "setAlpha", alphaInt)
            views.setInt(R.id.widgetNotificationsBackgroundBg, "setAlpha", alphaInt)

            val colors = resolveWidgetTheme(context, config)
            val primaryColor = colors.primary
            val defaultBodyColor = if (isDark) 0xFF1E1F25.toInt() else 0xFFF2F4F7.toInt()
            val bodyColor = if (config.appBackground) colors.background else defaultBodyColor
            val headerColor = if (config.headerSameAsBody) bodyColor else primaryColor

            views.setInt(R.id.widgetNotificationsHeaderBg, "setColorFilter", headerColor)
            views.setInt(R.id.widgetNotificationsBackgroundBg, "setColorFilter", bodyColor)

            val rippleRes = if (config.headerSameAsBody && !isDark) R.drawable.widget_button_ripple_dark else R.drawable.widget_button_ripple
            views.setInt(R.id.widgetNotificationsSync, "setBackgroundResource", rippleRes)

            if (config.headerSameAsBody) {
                val titleColor = if (isDark) 0xFFE6E1E5.toInt() else 0xFF1C1B1F.toInt()
                views.setTextColor(R.id.widgetNotificationsTitle, titleColor)
                val syncIconColor = if (isDark) Color.WHITE else 0xFF1C1B1F.toInt()
                val syncIntent = SzkolnyReceiver.getIntent(context, Bundle("task" to "SyncRequest"))
                val syncPendingIntent = PendingIntent.getBroadcast(context, appWidgetId, syncIntent, pendingIntentMutable())
                views.setOnClickPendingIntent(R.id.widgetNotificationsSync, syncPendingIntent)
                views.setImageViewBitmap(
                    R.id.widgetNotificationsSync,
                    CommunityMaterial.Icon.cmd_download_outline
                        .toDrawable(app, syncIconColor, sizeDp = iconSize)
                        .toBitmap()
                )
            } else {
                views.setTextColor(R.id.widgetNotificationsTitle, Color.WHITE)
                val syncIntent = SzkolnyReceiver.getIntent(context, Bundle("task" to "SyncRequest"))
                val syncPendingIntent = PendingIntent.getBroadcast(context, appWidgetId, syncIntent, pendingIntentMutable())
                views.setOnClickPendingIntent(R.id.widgetNotificationsSync, syncPendingIntent)
                views.setImageViewBitmap(
                    R.id.widgetNotificationsSync,
                    CommunityMaterial.Icon.cmd_download_outline
                        .toDrawable(app, Color.WHITE, sizeDp = iconSize)
                        .toBitmap()
                )
            }

            views.setViewVisibility(R.id.widgetNotificationsLoading, View.GONE)

            val listIntent = Intent(context, WidgetNotificationsService::class.java)
            listIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            listIntent.putExtra("config", app.gson.toJson(config))
            listIntent.data = Uri.parse(listIntent.toUri(Intent.URI_INTENT_SCHEME))
            views.setRemoteAdapter(R.id.widgetNotificationsListView, listIntent)

            val itemIntent = Intent(context, MainActivity::class.java)
            itemIntent.action = Intent.ACTION_MAIN
            val itemPendingIntent = PendingIntent.getActivity(context, appWidgetId, itemIntent, pendingIntentMutable())
            views.setPendingIntentTemplate(R.id.widgetNotificationsListView, itemPendingIntent)

            val headerIntent = Intent(context, MainActivity::class.java)
            headerIntent.action = Intent.ACTION_MAIN
            headerIntent.putExtras("fragmentId" to NavTarget.NOTIFICATIONS)
            val headerPendingIntent = PendingIntent.getActivity(context, appWidgetId, headerIntent, pendingIntentMutable())
            views.setOnClickPendingIntent(R.id.widgetNotificationsHeader, headerPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widgetNotificationsListView)
        }
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
