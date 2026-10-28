package pl.szczodrzynski.navlib

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.content.res.Resources
import android.graphics.Color
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.View.*
import android.view.Window
import android.view.WindowManager
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.mikepenz.materialize.util.KeyboardUtil


class SystemBarsUtil(private val activity: Activity) {
    companion object {
        private const val COLOR_TRANSPARENT = Color.TRANSPARENT
        /**
         * A fallback color.
         * Tells to apply a #22000000 overlay over the status/nav bar color.
         * This has the same effect as [statusBarDarker].
         */
        const val COLOR_HALF_TRANSPARENT = -1
        /**
         * Use ?colorPrimaryDark as a fallback or status bar color.
         */
        const val COLOR_PRIMARY_DARK = -2
        /**
         * A fallback color.
         * Not recommended to use as [statusBarFallbackLight] because it will make status bar
         * icons almost invisible.
         */
        const val COLOR_DO_NOT_CHANGE = -3

        private const val TARGET_MODE_NORMAL = 0
        private const val TARGET_MODE_LIGHT = 1
        private const val TARGET_MODE_GRADIENT = 2
    }

    val window: Window by lazy {
        activity.window
    }
    val resources: Resources by lazy {
        activity.resources
    }

    /**
     * A view which will have the padding added when the soft input keyboard appears.
     */
    var paddingByKeyboard: View? = null
    /**
     * Whether the app should be fullscreen.
     *
     * This means it will display under the system bars
     * and you should probably provide [statusBarBgView],
     * [navigationBarBgView] and [marginBySystemBars].
     */
    var appFullscreen = false

    /**
     * Define the color used to tint the status bar background.
     *
     * Valid values are [COLOR_PRIMARY_DARK] or a color integer.
     *
     * You cannot use neither [COLOR_HALF_TRANSPARENT] nor [COLOR_DO_NOT_CHANGE] here.
     * See [statusBarDarker].
     */
    var statusBarColor = COLOR_PRIMARY_DARK
    /**
     * Whether the status bar should have a dark overlay (#22000000).
     *
     * Useful if the [statusBarColor] is set to a bright color and is the same as an action bar.
     * Not useful if [statusBarColor] is [COLOR_PRIMARY_DARK].
     */
    var statusBarDarker = false
    /**
     * A fallback status bar color used on Android Lollipop
     * when the [statusBarColor] combined with [statusBarDarker] is
     * too bright not to blend with status bar icons (they cannot be
     * set to dark).
     *
     * This will (most likely) not be used when [statusBarDarker] is true.
     *
     * Valid values are [COLOR_HALF_TRANSPARENT], [COLOR_PRIMARY_DARK], [COLOR_DO_NOT_CHANGE].
     */
    var statusBarFallbackLight = COLOR_HALF_TRANSPARENT
    /**
     * A fallback status bar color used on Android KitKat and older.
     * On these systems there is a black-to-transparent gradient as
     * the status bar background.
     *
     * Valid values are [COLOR_HALF_TRANSPARENT], [COLOR_PRIMARY_DARK], [COLOR_DO_NOT_CHANGE].
     */
    var statusBarFallbackGradient = COLOR_DO_NOT_CHANGE

    // TODO remove - test for huawei
    var statusBarTranslucent = false

    /**
     * If false, the nav bar is mostly translucent but not completely transparent.
     */
    var navigationBarTransparent = true

    /**
     * A background view to be resized in order to fit under the status bar.
     */
    var statusBarBgView: View? = null
    /**
     * A background view to be resized in order to fit under the nav bar.
     */
    var navigationBarBgView: View? = null

    /**
     * A dark, half-transparent view to be resized in order to fit under the status bar.
     */
    var statusBarDarkView: View? = null
    /**
     * A dark, half-transparent view to be resized in order to fit under the nav bar.
     */
    var navigationBarDarkView: View? = null

    /**
     * A view which will have the margin added not to overlap with the status/nav bar.
     */
    var marginBySystemBars: View? = null
    /**
     * A view which will listen to the inset applying.
     */
    var insetsListener: View? = null
    /**
     * A view which will have the padding added not to overlap with the nav bar.
     * Useful for persistent bottom sheets.
     * Requires [marginBySystemBars].
     */
    var paddingByNavigationBar: View? = null

    private var keyboardUtil: KeyboardUtil? = null
    private var insetsApplied = false

    fun commit() {
        Log.d("NavLib", "SystemBarsUtil applying: appFullscreen=$appFullscreen")
        insetsApplied = false
        if (paddingByKeyboard != null) {
            // thanks mikepenz for this life-saving class
            keyboardUtil = KeyboardUtil(activity, paddingByKeyboard)
            keyboardUtil?.enable()
        }

        // get the correct target SB color
        var targetStatusBarColor = statusBarColor
        if (targetStatusBarColor == COLOR_PRIMARY_DARK)
            targetStatusBarColor = getColorFromAttr(activity, R.attr.colorPrimaryDark)

        val targetStatusBarDarker = statusBarDarker
        val targetStatusBarLight = ColorUtils.calculateLuminance(targetStatusBarColor) > 0.75 && !targetStatusBarDarker

        val insetsController = WindowCompat.getInsetsController(window, window.decorView)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val targetView = insetsListener ?: marginBySystemBars ?: window.decorView

        ViewCompat.setOnApplyWindowInsetsListener(targetView) { _, windowInsets ->
            val statusBarsInsets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBarsInsets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())

            if (appFullscreen) {
                // Fullscreen mode: Hide top status bar (clock, battery, wifi, etc.) so nothing overlaps
                insetsController.hide(WindowInsetsCompat.Type.statusBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

                if (SDK_INT < VERSION_CODES.R) {
                    @Suppress("DEPRECATION")
                    window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                }

                if (SDK_INT >= VERSION_CODES.LOLLIPOP) {
                    window.statusBarColor = Color.TRANSPARENT
                }

                applyPadding(0, 0, 0, navBarsInsets.bottom)
                statusBarBgView?.visibility = GONE
                statusBarDarkView?.visibility = GONE
            } else {
                // Normal mode: Show status bar and add top padding equal to statusBarsInsets.top so app toolbar starts below status bar
                insetsController.show(WindowInsetsCompat.Type.statusBars())
                insetsController.show(WindowInsetsCompat.Type.navigationBars())

                if (SDK_INT < VERSION_CODES.R) {
                    @Suppress("DEPRECATION")
                    window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                }

                insetsController.isAppearanceLightStatusBars = targetStatusBarLight

                if (SDK_INT >= VERSION_CODES.LOLLIPOP) {
                    window.statusBarColor = Color.TRANSPARENT
                }

                applyPadding(0, statusBarsInsets.top, 0, navBarsInsets.bottom)

                statusBarBgView?.visibility = VISIBLE
                statusBarBgView?.setBackgroundColor(targetStatusBarColor)
                statusBarDarkView?.visibility = if (targetStatusBarDarker) VISIBLE else GONE
            }

            windowInsets
        }

        ViewCompat.requestApplyInsets(targetView)
    }

    private fun isTablet(c: Context): Boolean {
        return (c.resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
    }

    private fun hasNavigationBar(context: Context): Boolean {
        val id = context.resources.getIdentifier("config_showNavigationBar", "bool", "android")
        var hasNavigationBar = id > 0 && context.resources.getBoolean(id)

        if (!hasNavigationBar && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            val d = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay

            val realDisplayMetrics = DisplayMetrics()
            d.getRealMetrics(realDisplayMetrics)

            val realHeight = realDisplayMetrics.heightPixels
            val realWidth = realDisplayMetrics.widthPixels

            val displayMetrics = DisplayMetrics()
            d.getMetrics(displayMetrics)

            val displayHeight = displayMetrics.heightPixels
            val displayWidth = displayMetrics.widthPixels

            hasNavigationBar = realWidth - displayWidth > 0 || realHeight - displayHeight > 0
        }

        // Allow a system property to override this. Used by the emulator.
        // See also hasNavigationBar().
        val navBarOverride = System.getProperty("qemu.hw.mainkeys")
        if (navBarOverride == "1")
            hasNavigationBar = true
        else if (navBarOverride == "0") hasNavigationBar = false

        return hasNavigationBar
    }

    private fun applyPadding(left: Int, top: Int, right: Int, bottom: Int) {
        marginBySystemBars?.setPadding(left, top, right, bottom)

        statusBarBgView?.layoutParams?.height = top
        navigationBarBgView?.layoutParams?.height = bottom

        statusBarDarkView?.layoutParams?.height = top
        navigationBarDarkView?.layoutParams?.height = bottom

        paddingByNavigationBar?.setPadding(
            (8 * resources.displayMetrics.density).toInt(),
            0,
            (8 * resources.displayMetrics.density).toInt(),
            bottom
        )
    }

    fun destroy() {
        if (paddingByKeyboard != null) {
            keyboardUtil?.disable()
        }
    }
}
