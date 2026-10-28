package pl.szczodrzynski.edziennik.ui.widgets.luckynumber

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.MainActivity
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.enums.NavTarget
import pl.szczodrzynski.edziennik.ext.getJsonObject
import pl.szczodrzynski.edziennik.ext.pendingIntentFlag
import pl.szczodrzynski.edziennik.ext.putExtras
import pl.szczodrzynski.edziennik.ext.toDrawable
import pl.szczodrzynski.edziennik.ui.widgets.WidgetConfig
import pl.szczodrzynski.edziennik.ui.widgets.resolveWidgetTheme
import pl.szczodrzynski.edziennik.utils.Utils
import pl.szczodrzynski.edziennik.utils.models.Date
import timber.log.Timber

class WidgetLuckyNumberProvider : AppWidgetProvider() {
    companion object {
        private const val TAG = "WidgetLuckyNumberProvider"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_CONFIGURATION_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED -> {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, WidgetLuckyNumberProvider::class.java))
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
                val views = buildRemoteViews(app, context, appWidgetManager, appWidgetId, config)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
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
    }

    private fun buildRemoteViews(
        app: App,
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        config: WidgetConfig,
    ): RemoteViews {
        val isDark = config.isDarkTheme(context)
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        val width = Utils.getCellsForSize(minWidthDp)
        val height = Utils.getCellsForSize(minHeightDp)
        val wide = minWidthDp > minHeightDp + 40
        val layout = when {
            config.bigStyle && wide -> R.layout.widget_lucky_number_big_wide
            config.bigStyle -> R.layout.widget_lucky_number_big
            wide -> R.layout.widget_lucky_number_wide
            else -> R.layout.widget_lucky_number
        }
        val views = RemoteViews(app.packageName, layout)

        val colors = applyTheme(context, views, config, isDark)
        bindLuckyNumber(app, views, config, isDark, colors)
        applySize(width, height, views)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            putExtras("fragmentId" to NavTarget.HOME)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentFlag()
        views.setOnClickPendingIntent(
            R.id.widgetLuckyNumberRoot,
            PendingIntent.getActivity(context, appWidgetId, openIntent, flags),
        )
        return views
    }

    private data class WidgetColors(
        val primary: Int,
        val secondary: Int,
        val onSurface: Int,
    )

    private fun applyTheme(context: Context, views: RemoteViews, config: WidgetConfig, isDark: Boolean): WidgetColors {
        val opacity = config.opacity.coerceIn(0f, 1f)
        views.setInt(R.id.widgetLuckyNumberBodyBg, "setAlpha", (opacity * 255f).toInt())

        val colors = resolveWidgetTheme(context, config)
        val defaultBodyColor = if (isDark) 0xFF1E1F25.toInt() else 0xFFF2F4F7.toInt()
        val bodyColor = if (config.appBackground) colors.background else defaultBodyColor
        views.setInt(R.id.widgetLuckyNumberBodyBg, "setColorFilter", bodyColor)

        val numberColor = colors.onSurface
        views.setTextColor(R.id.widgetLuckyNumberNumber, numberColor)
        views.setTextColor(R.id.widgetLuckyNumberCaption, numberColor)
        views.setTextColor(R.id.widgetLuckyNumberProfile, colors.secondary)
        return WidgetColors(colors.primary, colors.secondary, numberColor)
    }

    private fun bindLuckyNumber(
        app: App,
        views: RemoteViews,
        config: WidgetConfig,
        isDark: Boolean,
        colors: WidgetColors,
    ) {
        val today = Date.getToday()
        val tomorrow = today.clone().stepForward(0, 0, 1)
        val profile = app.db.profileDao().getByIdNow(config.profileId)
        val luckyNumber = if (profile != null)
            app.db.luckyNumberDao().getNearestFutureNow(config.profileId, today)
        else
            null
        val isYours = luckyNumber?.number == profile?.studentNumber && profile?.studentNumber != -1

        views.setTextViewText(
            R.id.widgetLuckyNumberProfile,
            profile?.name ?: app.getString(R.string.widget_lucky_number_no_profile),
        )

        val (numberText, caption) = when {
            profile == null -> "" to app.getString(R.string.widget_lucky_number_no_profile)
            luckyNumber == null -> "" to app.getString(R.string.home_lucky_number_no_info)
            luckyNumber.number == -1 -> "" to app.getString(R.string.home_lucky_number_no_number)
            isYours && luckyNumber.date.value == today.value ->
                luckyNumber.number.toString() to app.getString(R.string.home_lucky_number_yours_today)
            isYours && luckyNumber.date.value == tomorrow.value ->
                luckyNumber.number.toString() to app.getString(R.string.home_lucky_number_yours_tomorrow)
            isYours ->
                luckyNumber.number.toString() to app.getString(
                    R.string.home_lucky_number_yours_later,
                    luckyNumber.date.formattedString,
                )
            luckyNumber.date.value == today.value ->
                luckyNumber.number.toString() to app.getString(
                    R.string.home_lucky_number_today,
                    luckyNumber.number,
                )
            luckyNumber.date.value == tomorrow.value ->
                luckyNumber.number.toString() to app.getString(
                    R.string.home_lucky_number_tomorrow,
                    luckyNumber.number,
                )
            else ->
                luckyNumber.number.toString() to app.getString(
                    R.string.home_lucky_number_later,
                    luckyNumber.date.formattedString,
                    luckyNumber.number,
                )
        }

        views.setTextViewText(R.id.widgetLuckyNumberNumber, numberText)
        views.setViewVisibility(
            R.id.widgetLuckyNumberNumber,
            if (numberText.isEmpty()) View.GONE else View.VISIBLE,
        )
        views.setTextViewText(R.id.widgetLuckyNumberCaption, caption)
        views.setBoolean(R.id.widgetLuckyNumberCaption, "setSingleLine", false)
        views.setInt(R.id.widgetLuckyNumberCaption, "setMaxLines", 3)

        if (config.useEmoji) {
            val emojiRes = when {
                luckyNumber == null || luckyNumber.number == -1 -> R.drawable.emoji_sad
                isYours -> R.drawable.emoji_glasses
                else -> R.drawable.emoji_smiling
            }
            views.setImageViewResource(R.id.widgetLuckyNumberIcon, emojiRes)
        } else {
            val icon = when {
                luckyNumber == null || luckyNumber.number == -1 -> CommunityMaterial.Icon.cmd_emoticon_sad_outline
                isYours -> CommunityMaterial.Icon.cmd_emoticon_cool_outline
                else -> CommunityMaterial.Icon.cmd_emoticon_happy_outline
            }
            val iconColor = if (config.useThemeIconColor) colors.primary
            else if (isDark) Color.WHITE else 0xFF1C1B1F.toInt()
            views.setImageViewBitmap(
                R.id.widgetLuckyNumberIcon,
                icon.toDrawable(app, iconColor, sizeDp = if (config.bigStyle) 48 else 36).toBitmap(),
            )
        }
    }

    private fun applySize(width: Int, height: Int, views: RemoteViews) {
        views.setViewVisibility(R.id.widgetLuckyNumberCaption, if (width <= 1) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widgetLuckyNumberProfile, if (height <= 1) View.GONE else View.VISIBLE)
    }
}
