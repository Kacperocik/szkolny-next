package pl.szczodrzynski.navlib.drawer

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.os.Build
import android.util.Log
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.Insets
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener
import androidx.core.view.WindowInsetsCompat
import androidx.customview.widget.ViewDragHelper
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.RecyclerView
import com.mikepenz.itemanimators.AlphaCrossFadeAnimator
import com.mikepenz.materialdrawer.holder.BadgeStyle
import com.mikepenz.materialdrawer.holder.ColorHolder
import com.mikepenz.materialdrawer.holder.DimenHolder
import com.mikepenz.materialdrawer.holder.StringHolder
import com.mikepenz.materialdrawer.model.BaseDrawerItem
import com.mikepenz.materialdrawer.interfaces.OnPostBindViewListener
import com.mikepenz.materialdrawer.model.MiniDrawerItem
import com.mikepenz.materialdrawer.model.ExpandableDrawerItem
import com.mikepenz.materialdrawer.model.ProfileDrawerItem
import com.mikepenz.materialdrawer.model.ProfileSettingDrawerItem
import com.mikepenz.materialdrawer.model.interfaces.*
import com.mikepenz.materialdrawer.util.*
import com.mikepenz.materialdrawer.view.BezelImageView
import com.mikepenz.materialdrawer.widget.AccountHeaderView
import com.mikepenz.materialdrawer.widget.MaterialDrawerSliderView
import com.mikepenz.materialdrawer.widget.MiniDrawerSliderView
import com.mikepenz.materialize.util.UIUtils
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import pl.szczodrzynski.navlib.*
import pl.szczodrzynski.navlib.drawer.items.DrawerPrimaryItem

class NavDrawer(
    val context: Context,
    val drawerLayout: DrawerLayout,
    val drawerContainerLandscape: FrameLayout,
    val drawerContainerPortrait: FrameLayout,
    val miniDrawerElevation: View
) {
    companion object {
        private const val DRAWER_MODE_NORMAL = 0
        private const val DRAWER_MODE_MINI = 1
        private const val DRAWER_MODE_FIXED = 2

        private fun attachExpressiveShapeMorph(recyclerView: RecyclerView) {
            recyclerView.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
                override fun onChildViewAttachedToWindow(view: View) {
                    view.findViewById<View>(com.mikepenz.materialdrawer.R.id.material_drawer_profileIcon)
                        ?.enableExpressiveImageShapeMorph(pressedCornerRadiusDp = 10f)
                }

                override fun onChildViewDetachedFromWindow(view: View) = Unit
            })
            recyclerView.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
                private var target: View? = null
                private var profileIcon: View? = null

                override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                    when (e.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            target = rv.findChildViewUnder(e.x, e.y)
                            profileIcon = target?.findViewById(com.mikepenz.materialdrawer.R.id.material_drawer_profileIcon)
                            target?.handleExpressiveTouch(e)
                            profileIcon?.handleExpressiveImageTouch(e, pressedCornerRadiusDp = 10f)
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            target?.handleExpressiveTouch(e)
                            profileIcon?.handleExpressiveImageTouch(e, pressedCornerRadiusDp = 10f)
                            target = null
                            profileIcon = null
                        }
                        MotionEvent.ACTION_MOVE -> {
                            if (target != null && rv.scrollState != RecyclerView.SCROLL_STATE_IDLE) {
                                val cancel = MotionEvent.obtain(e)
                                cancel.action = MotionEvent.ACTION_CANCEL
                                target?.handleExpressiveTouch(cancel)
                                profileIcon?.handleExpressiveImageTouch(cancel, pressedCornerRadiusDp = 10f)
                                cancel.recycle()
                                target = null
                                profileIcon = null
                            }
                        }
                    }
                    return false
                }
            })
        }
    }

    private lateinit var activity: Activity
    private val resources: Resources
        get() = context.resources

    internal lateinit var toolbar: NavToolbar
    internal lateinit var bottomBar: NavBottomBar

    private lateinit var drawer: MaterialDrawerSliderView
    private lateinit var accountHeader: AccountHeaderView
    private lateinit var miniDrawer: MiniDrawerSliderView

    var accountHeaderVisible: Boolean = true
        set(value) {
            field = value
            if (::accountHeader.isInitialized) {
                accountHeader.visibility = if (value) View.VISIBLE else View.GONE
            }
        }

    var appFullscreen: Boolean = false
        set(value) {
            field = value
            if (::drawer.isInitialized) {
                applyDrawerInsets()
                ViewCompat.requestApplyInsets(drawer)
            }
        }

    private var drawerMode: Int = DRAWER_MODE_NORMAL
    private var systemBarsInsets: Insets = Insets.NONE
    private var selection: Int = -1

    lateinit var badgeStyle: BadgeStyle

    @SuppressLint("ClickableViewAccessibility")
    fun init(activity: Activity) {
        this.activity = activity

        /*badgeStyle = BadgeStyle(
            R.drawable.material_drawer_badge,
            getColorFromAttr(context, R.attr.colorError),
            getColorFromAttr(context, R.attr.colorError),
            getColorFromAttr(context, R.attr.colorOnError)
        )*/

        badgeStyle = BadgeStyle().apply {
            textColor = ColorHolder.fromColor(Color.WHITE)
            color = ColorHolder.fromColor(0xffd32f2f.toInt())
            corners = DimenHolder.fromDp(50)
            paddingLeftRight = DimenHolder.fromDp(6)
            paddingTopBottom = DimenHolder.fromDp(2)
            minWidth = DimenHolder.fromDp(22)
        }

        drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerStateChanged(newState: Int) {}
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}
            override fun onDrawerClosed(drawerView: View) {
                drawerClosedListener?.invoke()
                profileSelectionClose()
            }
            override fun onDrawerOpened(drawerView: View) {
                drawerOpenedListener?.invoke()
            }
        })

        accountHeader = AccountHeaderView(context).apply {
            dividerBelowHeader = false
            headerBackground = ImageHolder(R.drawable.header)
            displayBadgesOnSmallProfileImages = true
            setOnApplyWindowInsetsListener(this, null)

            post {
                findViewById<View>(com.mikepenz.materialdrawer.R.id.material_drawer_account_header_current)?.enableExpressiveImageShapeMorph(pressedCornerRadiusDp = 16f)
                findViewById<View>(com.mikepenz.materialdrawer.R.id.material_drawer_account_header_small_first)?.enableExpressiveImageShapeMorph(pressedCornerRadiusDp = 10f)
                findViewById<View>(com.mikepenz.materialdrawer.R.id.material_drawer_account_header_small_second)?.enableExpressiveImageShapeMorph(pressedCornerRadiusDp = 10f)
                findViewById<View>(com.mikepenz.materialdrawer.R.id.material_drawer_account_header_small_third)?.enableExpressiveImageShapeMorph(pressedCornerRadiusDp = 10f)
                val fourthId = context.resources.getIdentifier("material_drawer_account_header_small_fourth", "id", context.packageName)
                if (fourthId != 0) {
                    findViewById<View>(fourthId)?.enableExpressiveImageShapeMorph(pressedCornerRadiusDp = 10f)
                }
                styleSmallHeaderBadges()
                bindCurrentProfileImageActions()
                bindSmallHeaderProfileImages()
            }

            onAccountHeaderListener = { view, profile, current ->
                if (profile is ProfileSettingDrawerItem) {
                    drawerProfileSettingClickListener?.invoke(profile.identifier.toInt(), view) ?: false
                }
                else {
                    updateBadges()
                    if (current) {
                        close()
                        profileSelectionClose()
                        true
                    }
                    else {
                        (drawerProfileSelectedListener?.invoke(profile.identifier.toInt(), profile, current, view) ?: false).also {
                            setToolbarProfileImage(profileList.singleOrNull { it.id == profile.identifier.toInt() })
                        }
                    }
                }
            }

            onAccountHeaderItemLongClickListener = { view, profile, current ->
                if (profile is ProfileSettingDrawerItem) {
                    drawerProfileSettingLongClickListener?.invoke(profile.identifier.toInt(), view) ?: true
                }
                else {
                    drawerProfileLongClickListener?.invoke(profile.identifier.toInt(), profile, current, view) ?: false
                }
            }

            onAccountHeaderProfileImageListener = { view, profile, current ->
                drawerProfileImageClickListener?.invoke(profile.identifier.toInt(), profile, current, view) ?: false
            }

            onAccountHeaderSelectionViewClickListener = { _, _ ->
                val listener = drawerProfileSelectionClickListener
                if (listener != null) {
                    listener.invoke()
                    true
                } else {
                    false
                }
            }

            selectionListEnabled = false
            accountSwitcherArrow.visibility = View.GONE
        }
        setupHeaderTextContrast()

        drawer = MaterialDrawerSliderView(context).apply {
            accountHeader = this@NavDrawer.accountHeader
            itemAnimator = AlphaCrossFadeAnimator()
            //hasStableIds = true

            onDrawerItemClickListener = { _, drawerItem, position ->
                if (drawerItem.identifier.toInt() == selection) {
                    // Give the host a chance to handle re-taps of the selected item
                    // (e.g. Settings tapped while on a settings subpage, which
                    // shares the selection id).
                    drawerItemReselectListener?.invoke(drawerItem.identifier.toInt(), position, drawerItem) ?: false
                }
                else {
                    val consumed = drawerItemSelectedListener?.invoke(drawerItem.identifier.toInt(), position, drawerItem)
                    if (consumed == false || !drawerItem.isSelectable) {
                        setSelection(selection, false)
                        consumed == false
                    }
                    else if (consumed == true) {
                        when (drawerItem) {
                            is DrawerPrimaryItem -> toolbar.title = drawerItem.appTitle ?: drawerItem.name?.getText(context) ?: ""
                            is BaseDrawerItem<*, *> -> toolbar.title = drawerItem.name?.getText(context) ?: ""
                        }
                        false
                    }
                    else {
                        false
                    }
                }
            }

            onDrawerItemLongClickListener = { view, drawerItem, position ->
                drawerItemLongClickListener?.invoke(drawerItem.identifier.toInt(), position, drawerItem, view) ?: true
            }
        }

        setOnApplyWindowInsetsListener(drawer) { _, windowInsets ->
            systemBarsInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            applyDrawerInsets()

            // Return CONSUMED if you don't want want the window insets to keep being
            // passed down to descendant views.
            WindowInsetsCompat.CONSUMED
        }

        miniDrawer = object : MiniDrawerSliderView(context) {
            override fun generateMiniDrawerItem(drawerItem: IDrawerItem<*>): IDrawerItem<*>? {
                if (drawerItem is ProfileDrawerItem) return null
                if (drawerItem is ExpandableDrawerItem) return null
                val baseItem = drawerItem as? BaseDrawerItem<*, *>
                val oldLevel = baseItem?.level ?: 1
                baseItem?.level = 1
                val miniItem = super.generateMiniDrawerItem(drawerItem)
                baseItem?.level = oldLevel
                (miniItem as? MiniDrawerItem)?.withPostOnBindViewListener(object : OnPostBindViewListener {
                    override fun onBindView(drawerItem: IDrawerItem<*>, view: View) {
                        styleMiniDrawerBadge(view)
                    }
                })
                return miniItem
            }

            override fun createItems() {
                val items = mutableListOf<IDrawerItem<*>>()
                val mainDrawer = drawer
                if (mainDrawer != null) {
                    fun collectItems(itemList: List<*>) {
                        for (rawItem in itemList) {
                            val item = rawItem as? IDrawerItem<*> ?: continue
                            if (item is ProfileDrawerItem) continue
                            if (item is ExpandableDrawerItem) {
                                item.subItems?.let { collectItems(it) }
                                continue
                            }
                            val miniItem = generateMiniDrawerItem(item)
                            if (miniItem != null) {
                                items.add(miniItem)
                            }
                        }
                    }
                    collectItems(mainDrawer.itemAdapter.adapterItems)
                }

                itemAdapter.set(items)

                val selectedId = listOfNotNull(
                    mainDrawer?.itemAdapter?.adapterItems?.firstOrNull { it.isSelected }?.identifier,
                    mainDrawer?.selectedItemIdentifier?.takeIf { it > 0L },
                    this@NavDrawer.selection.takeIf { it != -1 }?.toLong(),
                    adapter.getItem(0)?.identifier,
                ).firstOrNull { id ->
                    (0 until adapter.itemCount).any { adapter.getItem(it)?.identifier == id }
                } ?: return

                for (i in 0 until adapter.itemCount) {
                    val item = adapter.getItem(i) ?: continue
                    item.isSelected = item.identifier == selectedId
                    if (item.isSelected) {
                        selectExtension.select(i)
                    }
                }
                recyclerView.adapter?.notifyDataSetChanged()
            }
        }.apply {
            drawer = this@NavDrawer.drawer
            includeSecondaryDrawerItems = true
        }

        miniDrawer.recyclerView.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    val child = miniDrawer.recyclerView.findChildViewUnder(e.x, e.y) ?: return false
                    val position = miniDrawer.recyclerView.getChildAdapterPosition(child)
                    if (position != RecyclerView.NO_POSITION) {
                        val item = miniDrawer.itemAdapter.getAdapterItem(position)
                        val id = item.identifier.toInt()
                        if (id != -1) {
                            val consumed = drawerItemSelectedListener?.invoke(id, position, item)
                            if (consumed != false) {
                                setSelection(id, fireOnClick = false)
                            }
                        }
                    }
                    return false
                }

                override fun onLongPress(e: MotionEvent) {
                    val child = miniDrawer.recyclerView.findChildViewUnder(e.x, e.y) ?: return
                    val position = miniDrawer.recyclerView.getChildAdapterPosition(child)
                    if (position != RecyclerView.NO_POSITION) {
                        val item = miniDrawer.itemAdapter.getAdapterItem(position)
                        val id = item.identifier.toInt()
                        if (id != -1) {
                            child.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            drawerItemLongClickListener?.invoke(id, position, item, child)
                        }
                    }
                }
            })

            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                gestureDetector.onTouchEvent(e)
                return false
            }
        })

        updateMiniDrawer()

        attachExpressiveShapeMorph(drawer.recyclerView)
        attachExpressiveShapeMorph(miniDrawer.recyclerView)

        toolbar.profileImageClickListener = {
            profileSelectionOpen()
        }

        val configuration = context.resources.configuration
        decideDrawerMode(
            configuration.orientation,
            configuration.screenWidthDp,
            configuration.screenHeightDp
        )
    }

    fun setItems(vararg items: IDrawerItem<*>) {
        val currentSelection = selection
        drawer.removeAllItems()
        drawer.addItems(*items)
        updateMiniDrawer()
        if (currentSelection != -1) {
            setSelection(currentSelection, fireOnClick = false)
        }
    }

    // Private methods
    private fun drawerSetDragMargin(size: Float) {
        try {
            val mDrawerLayout = drawerLayout
            val mDragger = mDrawerLayout::class.java.getDeclaredField(
                "mLeftDragger"
            )
            mDragger.isAccessible = true
            val draggerObj = mDragger.get(mDrawerLayout) as ViewDragHelper?
            draggerObj?.edgeSize = size.toInt()

            // update for SDK >= 29 (Android 10)
            val useSystemInsets = mDrawerLayout::class.java.getDeclaredField(
                "sEdgeSizeUsingSystemGestureInsets"
            )
            useSystemInsets.isAccessible = true
            useSystemInsets.set(null, false)
        }
        catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Oops, proguard works", Toast.LENGTH_SHORT).show()
        }
    }

    var miniDrawerVisiblePortrait: Boolean? = null
        set(value) {
            field = value
            val configuration = context.resources.configuration
            decideDrawerMode(
                configuration.orientation,
                configuration.screenWidthDp,
                configuration.screenHeightDp
            )
        }
    var miniDrawerVisibleLandscape: Boolean? = null
        set(value) {
            field = value
            val configuration = context.resources.configuration
            decideDrawerMode(
                configuration.orientation,
                configuration.screenWidthDp,
                configuration.screenHeightDp
            )
        }

    /**
     * Whether the fixed drawer (wide landscape screens) is collapsed
     * to the mini drawer by the user.
     */
    var fixedDrawerCollapsed: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (!::drawer.isInitialized) return
            val configuration = context.resources.configuration
            decideDrawerMode(
                configuration.orientation,
                configuration.screenWidthDp,
                configuration.screenHeightDp
            )
            ViewCompat.requestApplyInsets(drawerLayout)
        }
    var fixedDrawerCollapsedChangedListener: ((collapsed: Boolean) -> Unit)? = null

    private fun fixedDrawerAvailable(): Boolean {
        val configuration = context.resources.configuration
        return configuration.orientation != Configuration.ORIENTATION_PORTRAIT &&
            configuration.screenWidthDp >= 900
    }

    /**
     * Handles the toolbar/bottom bar menu button: toggles the fixed drawer
     * on wide screens, opens the slider drawer otherwise.
     */
    fun onMenuButtonClick() {
        if (fixedDrawerAvailable()) {
            fixedDrawerCollapsed = !fixedDrawerCollapsed
            fixedDrawerCollapsedChangedListener?.invoke(fixedDrawerCollapsed)
        } else {
            open()
        }
    }

    internal fun decideDrawerMode(orientation: Int, widthDp: Int, heightDp: Int) {
        val drawerLayoutParams = DrawerLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT).apply {
            gravity = GravityCompat.START
        }
        val fixedLayoutParams = FrameLayout.LayoutParams(UIUtils.convertDpToPixel(300f, context).toInt(), MATCH_PARENT)

        Log.d("NavLib", "Deciding drawer mode:")
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            Log.d("NavLib", "- fixed container disabled")

            if (drawerContainerLandscape.childCount > 0) {
                drawerContainerLandscape.removeAllViews()
            }
            Log.d("NavLib", "- mini drawer land disabled")

            if (drawerLayout.indexOfChild(drawer) == -1) {
                drawerLayout.addView(drawer, drawerLayoutParams)
            }
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
            Log.d("NavLib", "- slider enabled")

            if ((widthDp >= 480 && miniDrawerVisiblePortrait != false) || miniDrawerVisiblePortrait == true) {
                attachMiniDrawer(drawerContainerPortrait)
                Log.d("NavLib", "- mini drawer port enabled")
                drawerSetDragMargin(20 * resources.displayMetrics.density)
                drawerMode = DRAWER_MODE_MINI
                updateMiniDrawer()
            }
            else {
                if (drawerContainerPortrait.childCount > 0) {
                    drawerContainerPortrait.removeAllViews()
                }
                Log.d("NavLib", "- mini drawer port disabled")
                drawerSetDragMargin(20 * resources.displayMetrics.density)
                drawerMode = DRAWER_MODE_NORMAL
            }
        }
        else {
            if (drawerContainerPortrait.childCount > 0) {
                drawerContainerPortrait.removeAllViews()
            }
            Log.d("NavLib", "- mini drawer port disabled")

            val useFixed = widthDp >= 900 && !fixedDrawerCollapsed
            if (!useFixed && ((widthDp >= 480 && miniDrawerVisibleLandscape != false) || miniDrawerVisibleLandscape == true)) {
                if (drawerContainerLandscape.indexOfChild(drawer) != -1)
                    drawerContainerLandscape.removeView(drawer)
                attachMiniDrawer(drawerContainerLandscape)
                Log.d("NavLib", "- mini drawer land enabled")
                drawerSetDragMargin(20 * resources.displayMetrics.density)
                drawerMode = DRAWER_MODE_MINI
                updateMiniDrawer()
            }
            else {
                if (drawerContainerLandscape.indexOfChild(miniDrawer) != -1) {
                    drawerContainerLandscape.removeView(miniDrawer)
                }
                Log.d("NavLib", "- mini drawer land disabled")
                drawerSetDragMargin(20 * resources.displayMetrics.density)
                drawerMode = DRAWER_MODE_NORMAL
            }
            if (useFixed) {
                // screen is big enough to show fixed drawer
                if (drawerLayout.indexOfChild(drawer) != -1) {
                    // remove from slider
                    drawerLayout.removeView(drawer)
                }
                // lock the slider
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                Log.d("NavLib", "- slider disabled")
                // add to fixed container
                if (drawerContainerLandscape.indexOfChild(drawer) == -1)
                    drawerContainerLandscape.addView(drawer, fixedLayoutParams)
                drawer.visibility = View.VISIBLE
                Log.d("NavLib", "- fixed container enabled")
                drawerMode = DRAWER_MODE_FIXED
            }
            else {
                // screen is too small for the fixed drawer
                if (drawerContainerLandscape.indexOfChild(drawer) != -1) {
                    // remove from fixed container
                    drawerContainerLandscape.removeView(drawer)
                }
                Log.d("NavLib", "- fixed container disabled")
                // unlock the slider
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
                if (drawerLayout.indexOfChild(drawer) == -1) {
                    // add to slider
                    drawerLayout.addView(drawer, drawerLayoutParams)
                }
                Log.d("NavLib", "- slider enabled")
            }
        }

        applyDrawerEndCorners(drawerMode != DRAWER_MODE_FIXED)
        applyDrawerInsets()
        miniDrawerElevation.visibility = if (drawerMode == DRAWER_MODE_MINI || drawerMode == DRAWER_MODE_FIXED) View.VISIBLE else View.GONE
    }

    private var miniDrawerItemSize = 0

    /**
     * The mini drawer item size differs between orientations, so both the
     * container width and the already inflated (recycled) item views
     * must follow the current value of the dimension.
     */
    private fun attachMiniDrawer(container: FrameLayout) {
        val itemSize = resources.getDimensionPixelSize(R.dimen.material_mini_drawer_item)
        val layoutParams = FrameLayout.LayoutParams(itemSize, MATCH_PARENT)
        if (container.indexOfChild(miniDrawer) == -1)
            container.addView(miniDrawer, layoutParams)
        else
            miniDrawer.layoutParams = layoutParams

        if (miniDrawerItemSize != 0 && miniDrawerItemSize != itemSize) {
            // re-setting the adapter drops the recycled views inflated with the old size
            miniDrawer.recyclerView.adapter = miniDrawer.recyclerView.adapter
        }
        miniDrawerItemSize = itemSize
    }

    private fun applyDrawerInsets() {
        if (!::drawer.isInitialized) return
        val fixed = drawerMode == DRAWER_MODE_FIXED
        val topMargin = if (fixed || appFullscreen) 0 else systemBarsInsets.top
        (drawer.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
            if (params.topMargin != topMargin) {
                params.topMargin = topMargin
                drawer.layoutParams = params
            }
        }
        val leftPadding = if (fixed) 0 else systemBarsInsets.left
        val topPadding = if (!fixed && appFullscreen) systemBarsInsets.top else 0
        val rightPadding = if (fixed) 0 else systemBarsInsets.right
        val bottomPadding = if (fixed) 0 else systemBarsInsets.bottom
        drawer.setPadding(leftPadding, topPadding, rightPadding, bottomPadding)
    }

    private fun applyDrawerEndCorners(rounded: Boolean) {
        val radius = if (rounded) 28f * resources.displayMetrics.density else 0f
        val shape = ShapeAppearanceModel.builder()
            .setTopRightCorner(CornerFamily.ROUNDED, radius)
            .setBottomRightCorner(CornerFamily.ROUNDED, radius)
            .build()
        drawer.background = MaterialShapeDrawable(shape).apply {
            initializeElevationOverlay(context)
            fillColor = ColorStateList.valueOf(elevateSurface(context, 4))
        }
        drawer.outlineProvider = if (rounded) {
            object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    val r = radius.toInt()
                    outline.setRoundRect(-r, 0, view.width, view.height, radius)
                }
            }
        } else {
            ViewOutlineProvider.BACKGROUND
        }
        drawer.clipToOutline = rounded
        drawer.invalidateOutline()
    }

    var drawerTargetResolver: ((id: Int) -> Int)? = null

    fun resolveDrawerTargetId(id: Int): Int = drawerTargetResolver?.invoke(id) ?: id

    private fun updateMiniDrawer() {
        if (::drawer.isInitialized && drawer.selectedItemIdentifier > 0L) {
            selection = drawer.selectedItemIdentifier.toInt()
        }
        miniDrawer.createItems()
        if (selection != -1) {
            miniDrawer.setSelection(resolveDrawerTargetId(selection).toLong())
        }
    }

    // Public methods
    var isOpen
        get() = drawerLayout.isOpen || drawerMode == DRAWER_MODE_FIXED
        set(value) {
            if (drawerMode == DRAWER_MODE_FIXED)
                return
            if (value && !isOpen) drawerLayout.open() else if (!value && isOpen) drawerLayout.close()
        }
    fun open() { isOpen = true }
    fun close() { isOpen = false }
    fun toggle() { isOpen = !isOpen }

    /**
     * Manually slides the slider drawer for predictive back animations.
     * [fraction] 0 = fully closed, 1 = fully open. Uses layout offsets, so a
     * following [open] continues smoothly from the current position instead
     * of jumping back to closed first. No-op for the fixed drawer.
     */
    fun setSlideOffset(fraction: Float) {
        if (!::drawer.isInitialized || fixedDrawerEnabled()) return
        val width = drawer.width
        if (width <= 0) return
        val targetLeft = -width + (width * fraction.coerceIn(0f, 1f)).toInt()
        val delta = targetLeft - drawer.left
        if (delta != 0) drawer.offsetLeftAndRight(delta)
    }

    var profileSelectionIsOpen
        get() = accountHeader.selectionListShown
        set(value) {
            if (value != profileSelectionIsOpen)
                profileSelectionToggle()
        }
    fun profileSelectionOpen() {
        if (drawerProfileSelectionClickListener != null) {
            drawerProfileSelectionClickListener?.invoke()
            return
        }
        profileSelectionIsOpen = true
    }
    fun profileSelectionClose() { profileSelectionIsOpen = false }
    fun profileSelectionToggle() { accountHeader.selectionListShown = !accountHeader.selectionListShown }

    var drawerOpenedListener: (() -> Unit)? = null
    var drawerClosedListener: (() -> Unit)? = null
    var drawerItemSelectedListener: ((id: Int, position: Int, drawerItem: IDrawerItem<*>) -> Boolean)? = null
    var drawerItemReselectListener: ((id: Int, position: Int, drawerItem: IDrawerItem<*>) -> Boolean)? = null
    var drawerItemLongClickListener: ((id: Int, position: Int, drawerItem: IDrawerItem<*>, view: View?) -> Boolean)? = null
    var drawerProfileSelectedListener: ((id: Int, profile: IProfile, current: Boolean, view: View?) -> Boolean)? = null
    var drawerProfileLongClickListener: ((id: Int, profile: IProfile, current: Boolean, view: View?) -> Boolean)? = null
    var drawerProfileImageClickListener: ((id: Int, profile: IProfile, current: Boolean, view: View) -> Boolean)? = null
    var drawerProfileImageLongClickListener: ((id: Int, profile: IProfile, current: Boolean, view: View) -> Boolean)? = null
    var drawerProfileListEmptyListener: (() -> Unit)? = null
    var drawerProfileSettingClickListener: ((id: Int, view: View?) -> Boolean)? = null
    var drawerProfileSettingLongClickListener: ((id: Int, view: View?) -> Boolean)? = null
    var drawerProfileSelectionClickListener: (() -> Unit)? = null

    fun miniDrawerEnabled(): Boolean = drawerMode == DRAWER_MODE_MINI
    fun fixedDrawerEnabled(): Boolean = drawerMode == DRAWER_MODE_FIXED

    fun setSelection(id: Int, fireOnClick: Boolean = true) {
        Log.d("NavDebug", "setSelection(id = $id, fireOnClick = $fireOnClick)")
        profileSelectionClose()
        selection = id

        if (drawer.selectedItemIdentifier != id.toLong() || !fireOnClick)
            drawer.setSelectionAtPosition(drawer.getPosition(id.toLong()), fireOnClick)

        val miniAdapter = miniDrawer.itemAdapter
        val count = miniAdapter.adapterItemCount
        var targetIdInMini = id.toLong()
        val hasTargetInMini = (0 until count).any { miniAdapter.getAdapterItem(it).identifier == targetIdInMini }

        if (!hasTargetInMini) {
            val drawerSelectedId = drawer.selectedItemIdentifier
            if (drawerSelectedId != -1L && (0 until count).any { miniAdapter.getAdapterItem(it).identifier == drawerSelectedId }) {
                targetIdInMini = drawerSelectedId
            }
        }

        miniDrawer.setSelection(targetIdInMini)
    }
    fun getSelection(): Int = selection

    // TODO 2019-08-27 add methods for Drawable, @DrawableRes
    fun setAccountHeaderBackground(path: String?) {
        if (path == null) {
            accountHeader.headerBackground = ImageHolder(R.drawable.header)
        } else {
            accountHeader.headerBackground = ImageHolder(path)
        }
        lastHeaderBackgroundKey = 0
        accountHeader.post { applyHeaderTextContrast(forceSample = true) }
    }

    private var lastHeaderBackgroundKey = 0
    private var headerNameTextColor = Color.WHITE
    private var headerSubTextColor = Color.WHITE

    private fun setupHeaderTextContrast() {
        val installPreDraw = {
            accountHeader.viewTreeObserver.addOnPreDrawListener {
                applyHeaderTextContrast()
                true
            }
        }
        if (accountHeader.isAttachedToWindow) {
            installPreDraw()
        } else {
            accountHeader.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    v.removeOnAttachStateChangeListener(this)
                    installPreDraw()
                }
                override fun onViewDetachedFromWindow(v: View) {}
            })
        }
        accountHeader.accountHeaderBackground.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            applyHeaderTextContrast()
        }
    }

    private fun applyHeaderTextContrast(forceSample: Boolean = false) {
        val backgroundKey = headerBackgroundKey()
        if (forceSample || backgroundKey != lastHeaderBackgroundKey) {
            lastHeaderBackgroundKey = backgroundKey
            val lightBackground = sampleHeaderTextLuminance() > 0.5
            headerNameTextColor = if (lightBackground) Color.BLACK else Color.WHITE
            headerSubTextColor = ColorUtils.setAlphaComponent(
                headerNameTextColor,
                if (lightBackground) 0x99 else 0xCC
            )
        }
        if (accountHeader.currentProfileName.currentTextColor != headerNameTextColor) {
            accountHeader.currentProfileName.setTextColor(headerNameTextColor)
            accountHeader.currentProfileEmail.setTextColor(headerSubTextColor)
        }
        accountHeader.accountSwitcherArrow.visibility = View.GONE
    }

    private fun headerBackgroundKey(): Int {
        val background = accountHeader.accountHeaderBackground
        return System.identityHashCode(background.drawable) xor
            (background.width * 31) xor
            background.height xor
            (accountHeader.currentProfileName.width * 17)
    }

    private fun sampleHeaderTextLuminance(): Double {
        val background = accountHeader.accountHeaderBackground
        val width = background.width
        val height = background.height
        if (width <= 0 || height <= 0) {
            return 0.0
        }

        return try {
            val sampleWidth = 48
            val sampleHeight = (height.toFloat() / width * sampleWidth).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(sampleWidth, sampleHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.scale(sampleWidth / width.toFloat(), sampleHeight / height.toFloat())
            background.draw(canvas)

            val name = accountHeader.currentProfileName
            val email = accountHeader.currentProfileEmail
            val bgLoc = IntArray(2)
            val nameLoc = IntArray(2)
            background.getLocationOnScreen(bgLoc)
            name.getLocationOnScreen(nameLoc)

            val scaleX = sampleWidth / width.toFloat()
            val scaleY = sampleHeight / height.toFloat()
            val regionLeft: Int
            val regionTop: Int
            val regionRight: Int
            val regionBottom: Int
            if (name.width > 0 && name.height > 0) {
                regionLeft = ((nameLoc[0] - bgLoc[0]) * scaleX).toInt().coerceIn(0, sampleWidth - 1)
                regionTop = ((nameLoc[1] - bgLoc[1]) * scaleY).toInt().coerceIn(0, sampleHeight - 1)
                regionRight = ((nameLoc[0] - bgLoc[0] + name.width) * scaleX).toInt()
                    .coerceIn(regionLeft + 1, sampleWidth)
                regionBottom = ((nameLoc[1] - bgLoc[1] + name.height + email.height) * scaleY).toInt()
                    .coerceIn(regionTop + 1, sampleHeight)
            } else {
                regionLeft = 0
                regionTop = sampleHeight * 2 / 3
                regionRight = sampleWidth
                regionBottom = sampleHeight
            }

            var total = 0.0
            var count = 0
            for (y in regionTop until regionBottom) {
                for (x in regionLeft until regionRight) {
                    val color = bitmap.getPixel(x, y)
                    if (Color.alpha(color) < 16) continue
                    total += ColorUtils.calculateLuminance(color)
                    count++
                }
            }
            bitmap.recycle()
            if (count == 0) 0.0 else total / count
        } catch (_: Exception) {
            0.0
        }
    }

    // Profile
    private var profileList: MutableList<IDrawerProfile> = mutableListOf()

    fun addProfileSettings(vararg items: ProfileSettingDrawerItem) {
        accountHeader.profiles?.addAll(items)
    }

    private fun updateProfileList() {
        // remove all profile items
        val profiles = accountHeader.profiles?.filterNot { it is ProfileDrawerItem } as MutableList<IProfile>?

        if (profileList.isEmpty())
            drawerProfileListEmptyListener?.invoke()

        profileList.forEachIndexed { index, profile ->
            val image = profile.getImageHolder(context)
            ProfileDrawerItem()
                .withIdentifier(profile.id.toLong())
                .withName(profile.name)
                .withEmail(profile.subname)
                .also { it.icon = image }
                .withBadgeStyle(badgeStyle)
                .withNameShown(true)
                .also { profiles?.add(index, it) }
        }

        accountHeader.profiles = profiles

        updateBadges()
        updateMiniDrawer()
        bindCurrentProfileImageActions()
        bindSmallHeaderProfileImages()
    }

    fun setProfileList(profiles: MutableList<out IDrawerProfile>) {
        profileList = profiles as MutableList<IDrawerProfile>
        updateProfileList()
    }
    private var currentProfileObj: IDrawerProfile? = null
    val profileListEmpty: Boolean
        get() = profileList.isEmpty()
    var currentProfile: Int
        get() = accountHeader.activeProfile?.identifier?.toInt() ?: -1
        set(value) {
            Log.d("NavDebug", "currentProfile = $value")
            accountHeader.setActiveProfile(value.toLong(), false)
            currentProfileObj = profileList.singleOrNull { it.id == value }
            setToolbarProfileImage(currentProfileObj)
            updateBadges()
            bindSmallHeaderProfileImages()
        }
    fun appendProfile(profile: IDrawerProfile) {
        profileList.add(profile)
        updateProfileList()
    }
    fun appendProfiles(vararg profiles: IDrawerProfile) {
        profileList.addAll(profiles)
        updateProfileList()
    }
    fun prependProfile(profile: IDrawerProfile) {
        profileList.add(0, profile)
        updateProfileList()
    }
    fun prependProfiles(vararg profiles: IDrawerProfile) {
        profileList.addAll(0, profiles.asList())
        updateProfileList()
    }
    fun addProfileAt(index: Int, profile: IDrawerProfile) {
        profileList.add(index, profile)
        updateProfileList()
    }
    fun addProfilesAt(index: Int, vararg profiles: IDrawerProfile) {
        profileList.addAll(index, profiles.asList())
        updateProfileList()
    }
    fun removeProfileById(id: Int) {
        profileList = profileList.filterNot { it.id == id }.toMutableList()
        updateProfileList()
    }
    fun removeProfileAt(index: Int) {
        profileList.removeAt(index)
        updateProfileList()
    }
    fun removeAllProfile() {
        profileList.clear()
        updateProfileList()
    }
    fun removeAllProfileSettings() {
        accountHeader.profiles = accountHeader.profiles?.filterNot { it is ProfileSettingDrawerItem }?.toMutableList()
    }

    fun getProfileById(id: Int, run: (it: IDrawerProfile?) -> Unit) {
        profileList.singleOrNull { it.id == id }.also {
            run(it)
            updateProfileList()
        }
    }
    fun getProfileByIndex(index: Int, run: (it: IDrawerProfile?) -> Unit) {
        profileList.getOrNull(index).also {
            run(it)
            updateProfileList()
        }
    }

    private fun setToolbarProfileImage(profile: IDrawerProfile?) {
        toolbar.profileImage = profile?.getImageDrawable(context)
    }


    // Badges
    private var unreadCounterList: MutableList<IUnreadCounter> = mutableListOf()
    private val unreadCounterTypeMap = mutableMapOf<Int, Int>()

    fun updateBadges() {

        currentProfileObj = profileList.singleOrNull { it.id == currentProfile }

        drawer.itemAdapter.itemList.items.forEachIndexed { index, item ->
            if (item is Badgeable) {
                item.badge = null
                drawer.updateItem(item)
            }
        }

        var profileCounters = listOf<IUnreadCounter>()

        accountHeader.profiles?.forEach { profile ->
            if (profile !is ProfileDrawerItem) return@forEach
            val counters = unreadCounterList.filter { it.profileId == profile.identifier.toInt() }
            val count = counters.sumBy { it.count }
            val badge = when {
                count == 0 -> null
                count >= 99 -> StringHolder("99+")
                else -> StringHolder(count.toString())
            }
            if (profile.badge != badge) {
                profile.badge = badge
                accountHeader.updateProfile(profile)
            }

            if (currentProfile == profile.identifier.toInt())
                profileCounters = counters
        }

        Log.d("NavDebug", "updateBadges()")
        profileCounters.map {
            it.drawerItemId = unreadCounterTypeMap[it.type]
        }
        var totalCount = 0
        profileCounters.forEach {
            if (it.drawerItemId == null)
                return@forEach
            if (it.profileId != currentProfile) {
                //Log.d("NavDebug", "- Remove badge for ${it.drawerItemId}")
                //drawer?.updateBadge(it.drawerItemId?.toLong() ?: 0, null)
                return@forEach
            }
            Log.d("NavDebug", "- Set badge ${it.count} for ${it.drawerItemId}")
            drawer.updateBadge(
                it.drawerItemId?.toLong() ?: 0,
                when {
                    it.count == 0 -> null
                    it.count >= 99 -> StringHolder("99+")
                    else -> StringHolder(it.count.toString())
                }
            )
            totalCount += it.count
        }
        styleSmallHeaderBadges()
        bindCurrentProfileImageActions()
        bindSmallHeaderProfileImages()
        updateMiniDrawer()

        toolbar.navigationIcon?.setBadgeCount(totalCount)
        bottomBar.navigationIcon?.setBadgeCount(totalCount)
    }

    private fun bindCurrentProfileImageActions() {
        if (!::accountHeader.isInitialized) return
        val image = accountHeader.currentProfileView
        image.setOnClickListener { view ->
            val profile = view.getTag(com.mikepenz.materialdrawer.R.id.material_drawer_profile_header) as? IProfile
            val consumed = if (profile != null) {
                drawerProfileImageClickListener?.invoke(
                    profile.identifier.toInt(),
                    profile,
                    true,
                    view,
                ) ?: false
            } else {
                false
            }
            if (!consumed) {
                close()
                profileSelectionClose()
            }
        }
        image.setOnLongClickListener { view ->
            val profile = view.getTag(com.mikepenz.materialdrawer.R.id.material_drawer_profile_header) as? IProfile
                ?: return@setOnLongClickListener false
            drawerProfileImageLongClickListener?.invoke(
                profile.identifier.toInt(),
                profile,
                true,
                view,
            ) ?: false
        }
    }

    private fun bindSmallHeaderProfileImages() {
        if (!::accountHeader.isInitialized) return

        val activeProfileId = accountHeader.activeProfile?.identifier
        val allProfileItems = accountHeader.profiles?.filterIsInstance<ProfileDrawerItem>() ?: return

        val secondaryProfiles = allProfileItems.filter { it.identifier != activeProfileId }

        val fourthViewId = context.resources.getIdentifier("material_drawer_account_header_small_fourth", "id", context.packageName)
        val fourthBadgeId = context.resources.getIdentifier("material_drawer_account_header_small_fourth_badge", "id", context.packageName)

        val smallViews = listOf(
            com.mikepenz.materialdrawer.R.id.material_drawer_account_header_small_first to com.mikepenz.materialdrawer.R.id.material_drawer_account_header_small_first_badge,
            com.mikepenz.materialdrawer.R.id.material_drawer_account_header_small_second to com.mikepenz.materialdrawer.R.id.material_drawer_account_header_small_second_badge,
            com.mikepenz.materialdrawer.R.id.material_drawer_account_header_small_third to com.mikepenz.materialdrawer.R.id.material_drawer_account_header_small_third_badge,
            fourthViewId to fourthBadgeId,
        )

        smallViews.forEachIndexed { index, (viewId, badgeId) ->
            if (viewId == 0) return@forEachIndexed
            val imgView = accountHeader.findViewById<BezelImageView>(viewId) ?: return@forEachIndexed
            val badgeView = if (badgeId != 0) accountHeader.findViewById<TextView>(badgeId) else null

            val profile = secondaryProfiles.getOrNull(index)
            if (profile != null) {
                imgView.visibility = View.VISIBLE
                profile.icon?.applyTo(imgView)
                imgView.setOnClickListener {
                    accountHeader.onAccountHeaderProfileImageListener?.invoke(imgView, profile, false)
                }
                imgView.setOnLongClickListener {
                    accountHeader.onAccountHeaderItemLongClickListener?.invoke(imgView, profile, false) ?: false
                }
                val badgeText = profile.badge?.getText(context)
                if (!badgeText.isNullOrEmpty()) {
                    badgeView?.visibility = View.VISIBLE
                    badgeView?.text = badgeText
                } else {
                    badgeView?.visibility = View.GONE
                }
            } else {
                imgView.visibility = View.GONE
                badgeView?.visibility = View.GONE
            }
        }
    }

    private fun styleMiniDrawerBadge(itemView: View) {
        val badge = itemView.findViewById<TextView>(com.mikepenz.materialdrawer.R.id.material_drawer_badge)
            ?: return
        if (badge.visibility != View.VISIBLE) return
        val density = context.resources.displayMetrics.density
        val size = (16 * density).toInt()
        val pad = (2 * density).toInt()
        badge.minWidth = size
        badge.minHeight = size
        badge.gravity = Gravity.CENTER
        badge.includeFontPadding = false
        badge.setPadding(pad, pad, pad, pad)
        badge.textSize = 10f
    }

    private fun styleSmallHeaderBadges() {
        if (!::accountHeader.isInitialized) return
        val density = context.resources.displayMetrics.density
        val size = (18 * density).toInt()
        val pad = (3 * density).toInt()
        val fourthBadgeId = context.resources.getIdentifier("material_drawer_account_header_small_fourth_badge", "id", context.packageName)
        val badgeIds = if (fourthBadgeId != 0) {
            intArrayOf(
                com.mikepenz.materialdrawer.R.id.material_drawer_account_header_small_first_badge,
                com.mikepenz.materialdrawer.R.id.material_drawer_account_header_small_second_badge,
                com.mikepenz.materialdrawer.R.id.material_drawer_account_header_small_third_badge,
                fourthBadgeId,
            )
        } else {
            intArrayOf(
                com.mikepenz.materialdrawer.R.id.material_drawer_account_header_small_first_badge,
                com.mikepenz.materialdrawer.R.id.material_drawer_account_header_small_second_badge,
                com.mikepenz.materialdrawer.R.id.material_drawer_account_header_small_third_badge,
            )
        }
        for (id in badgeIds) {
            val badge = accountHeader.findViewById<TextView>(id) ?: continue
            badge.minWidth = size
            badge.minHeight = size
            badge.gravity = Gravity.CENTER
            badge.includeFontPadding = false
            badge.setPadding(pad, pad, pad, pad)
        }
    }

    fun setUnreadCounterList(unreadCounterList: MutableList<out IUnreadCounter>) {
        this.unreadCounterList = unreadCounterList as MutableList<IUnreadCounter>
        updateBadges()
    }

    fun addUnreadCounterType(type: Int, drawerItem: Int) {
        unreadCounterTypeMap[type] = drawerItem
    }

    data class UnreadCounter(
            override var profileId: Int,
            override var type: Int,
            override var drawerItemId: Int?,
            override var count: Int
    ) : IUnreadCounter

    fun setUnreadCount(profileId: Int, type: Int, count: Int) {
        val item = unreadCounterList.singleOrNull {
            it.type == type && it.profileId == profileId
        }
        if (item != null) {
            item.count = count
        }
        else {
            unreadCounterList.add(UnreadCounter(profileId, type, null, count))
        }
        updateBadges()
    }
}
