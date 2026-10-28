package pl.szczodrzynski.edziennik.ui.widgets

import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.view.ContextThemeWrapper
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.enums.Theme
import pl.szczodrzynski.edziennik.data.enums.Theme.Mode
import pl.szczodrzynski.edziennik.data.enums.Theme.Type

data class WidgetThemeColors(
    val primary: Int,
    val secondary: Int,
    val background: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int,
    val isDark: Boolean,
)

fun resolveWidgetTheme(context: android.content.Context, config: WidgetConfig): WidgetThemeColors {
    val app = context.applicationContext as App
    val isDark = config.isDarkTheme(context)
    var color = if (config.themeColor != -1) {
        Theme.entries.getOrNull(config.themeColor) ?: Theme.DEFAULT
    } else {
        app.config.ui.themeColor
    }
    val type = app.config.ui.themeType
    val mode = app.config.ui.themeMode
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && color == Theme.DEFAULT && type == Type.M3) {
        color = Theme.BLUE
    }
    val styleRes = color.styleRes[type to mode]
        ?: color.styleRes[Type.M3 to Mode.DAYNIGHT]
        ?: R.style.AppTheme_M3

    val nightConfig = Configuration(context.resources.configuration)
    val nightBits = if (isDark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
    nightConfig.uiMode = (nightConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightBits
    val themed = ContextThemeWrapper(context.createConfigurationContext(nightConfig), styleRes)
    val overlay = color.intensityOverlay(app.config.ui.themeIntensity, type, mode)
    if (overlay != 0) themed.theme.applyStyle(overlay, true)
    if (isDark && app.config.ui.themeBlackMode) {
        themed.theme.applyStyle(R.style.ThemeOverlay_AppTheme_Black, true)
    }

    val attrs = themed.obtainStyledAttributes(
        intArrayOf(
            R.attr.colorPrimary,
            R.attr.colorSecondary,
            android.R.attr.colorBackground,
            R.attr.colorSurface,
            R.attr.colorOnSurface,
            R.attr.colorOnSurfaceVariant,
        ),
    )
    val primary = attrs.getColor(0, if (isDark) Color.WHITE else 0xFF1C1B1F.toInt())
    val secondary = attrs.getColor(1, if (isDark) 0xFFCAC4D0.toInt() else 0xFF625B71.toInt())
    val windowBackground = attrs.getColor(2, 0)
    val surface = attrs.getColor(3, 0)
    val onSurface = attrs.getColor(4, if (isDark) 0xFFE6E1E5.toInt() else 0xFF1C1B1F.toInt())
    val onSurfaceVariant = attrs.getColor(5, if (isDark) 0xFFCAC4D0.toInt() else 0xFF49454F.toInt())
    attrs.recycle()

    val background = when {
        windowBackground != 0 -> windowBackground
        surface != 0 -> surface
        else -> if (isDark) 0xFF1E1F25.toInt() else 0xFFF2F4F7.toInt()
    }
    return WidgetThemeColors(primary, secondary, background, onSurface, onSurfaceVariant, isDark)
}
