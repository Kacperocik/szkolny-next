package pl.szczodrzynski.navlib

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import kotlin.math.roundToInt
import pl.szczodrzynski.navlib.bottomsheet.NavBottomSheet
import pl.szczodrzynski.navlib.databinding.NavViewBinding
import pl.szczodrzynski.navlib.drawer.NavDrawer

class NavView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    private val b = NavViewBinding.inflate(LayoutInflater.from(context), this)
    private var contentView: LinearLayout? = null
    val drawer: NavDrawer

    val coordinator
        get() = b.nvCoordinator
    val toolbar
        get() = b.nvToolbar
    val toolbarProgress
        get() = b.nvToolbarProgress
    val bottomBar
        get() = b.nvBottomBar
    val nightlyText
        get() = b.nvNightlyText
    val bottomSheet
        get() = b.nvBottomSheet
    fun hideFab() = bottomBar.hideFab()
    fun showFab() = bottomBar.showFab()

    init {
        contentView = b.nvContent

        drawer = NavDrawer(
            context,
            b.nvDrawerLayout,
            b.nvDrawerContainerLandscape,
            b.nvMiniDrawerContainerPortrait,
            b.nvMiniDrawerElevation,
        )

        drawer.toolbar = b.nvToolbar
        drawer.bottomBar = b.nvBottomBar

        b.nvToolbar.navView = this
        b.nvToolbar.toolbarImage = b.nvToolbarImage
        b.nvToolbarImage.enableExpressiveImageShapeMorph(pressedCornerRadiusDp = 8f)

        b.nvBottomBar.navView = this
        b.nvBottomBar.fabExtendedView = b.nvExtendedFloatingActionButton

        toolbar.drawerClickListener = drawer::onMenuButtonClick
        toolbar.menuClickListener = bottomSheet::open
        bottomBar.drawerClickListener = drawer::onMenuButtonClick
        bottomBar.menuClickListener = bottomSheet::open

        b.nvBottomBar.setOnTouchListener(bottomSheet::dispatchBottomSheetEvent)
        b.nvBottomSheet.scrimView.setOnTouchListener(bottomSheet::dispatchBottomSheetEvent)

        // the drawer consumes the window insets, which - with the default, sequential
        // dispatching - would leave the views placed after it (the bottom sheet) unaware
        // of the navigation bar
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            for (child in children)
                ViewCompat.dispatchApplyWindowInsets(child, insets)
            WindowInsetsCompat.CONSUMED
        }
    }

    fun configSystemBarsUtil(systemBarsUtil: SystemBarsUtil) {
        this.systemBarsUtil = systemBarsUtil.apply {
            this.statusBarBgView = b.nvStatusBarBackground
            this.navigationBarBgView = b.nvNavigationBarBackground
            this.statusBarDarkView = b.nvStatusBarDarker
            this.navigationBarDarkView = b.nvNavigationBarBackground
            this.insetsListener = b.nvDrawerLayout
            this.marginBySystemBars = b.nvMain
        }
    }

    /**
     * Set the FAB's on click listener
     */
    fun setFabOnClickListener(onClickListener: OnClickListener?) {
        bottomBar.setFabOnClickListener(onClickListener)
    }

    internal var systemBarsUtil: SystemBarsUtil? = null

    @SuppressLint("ClickableViewAccessibility")
    var toolbarAtBottom = false
        set(value) {
            field = value
            updateToolbarPosition()
            b.nvToolbar.setOnTouchListener(
                if (value) bottomSheet::dispatchBottomSheetEvent else null
            )
        }

    private fun updateToolbarPosition() {
        val toolbarContainer = b.nvToolbarContainer
        val elevationView = b.nvToolbarElevation

        toolbarContainer.updateLayoutParams<CoordinatorLayout.LayoutParams> {
            gravity = if (toolbarAtBottom) Gravity.BOTTOM else Gravity.TOP
        }

        b.nvToolbarProgress.updateLayoutParams<FrameLayout.LayoutParams> {
            gravity = if (toolbarAtBottom) Gravity.TOP else Gravity.BOTTOM
        }

        val toolbarHeight = if (toolbar.enable) {
            toolbarContainer.measure(MATCH_PARENT, WRAP_CONTENT)
            toolbarContainer.measuredHeight
        } else 0

        elevationView.updateLayoutParams<CoordinatorLayout.LayoutParams> {
            gravity = if (toolbarAtBottom) Gravity.BOTTOM else Gravity.TOP
            if (toolbarAtBottom) {
                topMargin = 0
                bottomMargin = toolbarHeight
            } else {
                bottomMargin = 0
                topMargin = toolbarHeight
            }
        }
        elevationView.setBackgroundResource(if (toolbarAtBottom) R.drawable.shadow_top else R.drawable.shadow_bottom)
        val density = resources.displayMetrics.density
        toolbarContainer.translationZ = 8f * density
        elevationView.translationZ = 8f * density

        bottomBar.setFabParams()
        setContentMargins()
    }

    fun showToolbarProgress(progress: Float = -1f) {
        val view = b.nvToolbarProgress
        val indeterminate = progress < 0f
        if (view.isIndeterminate != indeterminate) {
            view.isVisible = false
            view.isIndeterminate = indeterminate
        }
        if (!indeterminate) {
            view.setProgressCompat(progress.roundToInt().coerceIn(0, 100), true)
        }
        view.isVisible = true
    }

    fun hideToolbarProgress() {
        b.nvToolbarProgress.isVisible = false
    }

    internal fun setContentMargins() {
        contentView?.updateLayoutParams<CoordinatorLayout.LayoutParams> {
            val toolbarHeight = if (toolbar.enable) {
                toolbar.measure(MATCH_PARENT, WRAP_CONTENT)
                toolbar.measuredHeight
            } else 0

            val bottomBarHeight = if (bottomBar.enable) {
                bottomBar.measure(MATCH_PARENT, WRAP_CONTENT)
                bottomBar.measuredHeight
            } else 0

            if (toolbarAtBottom) {
                topMargin = 0
                bottomMargin = toolbarHeight + bottomBarHeight
            } else {
                topMargin = toolbarHeight
                bottomMargin = bottomBarHeight
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)

        Log.d(
            "NavLib",
            "CONFIGURATION CHANGED: ${newConfig?.screenWidthDp}x${newConfig?.screenHeightDp} " + if (newConfig?.orientation == ORIENTATION_PORTRAIT) "portrait" else "landscape"
        )

        systemBarsUtil?.commit()

        drawer.decideDrawerMode(
            newConfig?.orientation ?: ORIENTATION_PORTRAIT,
            newConfig?.screenWidthDp ?: 0,
            newConfig?.screenHeightDp ?: 0
        )
    }

    fun onBackPressed(): Boolean {
        if (drawer.isOpen && !drawer.fixedDrawerEnabled()) {
            if (drawer.profileSelectionIsOpen) {
                drawer.profileSelectionClose()
                return true
            }
            drawer.close()
            return true
        }
        if (bottomSheet.isOpen) {
            bottomSheet.close()
            return true
        }
        return false
    }

    override fun addView(child: View?, index: Int, params: ViewGroup.LayoutParams?) =
        contentView?.addView(child, index, params) ?: super.addView(child, index, params)
}
