package pl.szczodrzynski.navlib.bottomsheet

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isInvisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import pl.szczodrzynski.navlib.bottomsheet.items.BottomSheetPrimaryItem
import pl.szczodrzynski.navlib.bottomsheet.items.BottomSheetSeparatorItem
import pl.szczodrzynski.navlib.bottomsheet.items.CornerPosition
import pl.szczodrzynski.navlib.bottomsheet.items.IBottomSheetItem
import pl.szczodrzynski.navlib.databinding.NavBottomSheetBinding
import pl.szczodrzynski.navlib.elevateSurface

class NavBottomSheet @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : CoordinatorLayout(context, attrs, defStyle) {

    private val b = NavBottomSheetBinding.inflate(LayoutInflater.from(context), this, true)

    val scrimView
        get() = b.bsScrim
    private val bottomSheet
        get() = b.bsView
    private val content
        get() = b.bsContent
    private val dragBar
        get() = b.bsDragBar
    private val list
        get() = b.bsList

    private val bottomSheetBehavior = BottomSheetBehavior.from<View>(bottomSheet)
    private val gestureDetector = GestureDetector(context, OnGestureListener())
    private val flingVelocitySlop = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var sheetDragPointerId = MotionEvent.INVALID_POINTER_ID
    private var sheetDragStartY = 0f
    private var sheetDragBase = 0f
    private var sheetDragDy = 0f
    private var sheetDragGrabbed = false
    private var sheetFingerDelta = 0f
    private var sheetCatchUp = 0f
    private var sheetCatchUpAnimator: ValueAnimator? = null
    private var sheetDragVelocity: VelocityTracker? = null

    private val items = ArrayList<IBottomSheetItem<*>>()
    private val adapter = BottomSheetAdapter(items)

    init {
        bottomSheetBehavior.isHideable = true
        bottomSheetBehavior.skipCollapsed = true
        bottomSheetBehavior.peekHeight = 0
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

        scrimView.setOnClickListener {
            close()
        }

        val contentPaddingLeft = content.paddingLeft
        val contentPaddingRight = content.paddingRight
        val contentPaddingBottom = content.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            content.updatePadding(
                left = contentPaddingLeft + insets.left,
                right = contentPaddingRight + insets.right,
                bottom = contentPaddingBottom + insets.bottom
            )
            bottomSheet.requestLayout()
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(this)

        bottomSheetBehavior.addBottomSheetCallback(object :
            BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(view: View, slideOffset: Float) {
                val alpha = slideOffset.coerceIn(0f, 1f)
                scrimView.alpha = alpha
                scrimView.visibility = if (alpha <= 0.01f) GONE else VISIBLE
            }

            override fun onStateChanged(view: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_HIDDEN -> {
                        bottomSheet.visibility = INVISIBLE
                        scrimView.visibility = GONE
                        scrimView.alpha = 0f
                        hideKeyboard()
                        bottomSheet.scrollTo(0, 0)
                        onCloseListener?.invoke()
                    }
                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        bottomSheet.visibility = INVISIBLE
                        scrimView.visibility = GONE
                        scrimView.alpha = 0f
                        view.post {
                            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
                                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                            }
                        }
                    }
                    BottomSheetBehavior.STATE_EXPANDED,
                    BottomSheetBehavior.STATE_DRAGGING,
                    BottomSheetBehavior.STATE_SETTLING -> {
                        bottomSheet.visibility = VISIBLE
                        if (scrimView.alpha > 0.01f) scrimView.visibility = VISIBLE
                    }
                }
            }
        })

        content.background.colorFilter = PorterDuffColorFilter(
            elevateSurface(context, dp = 8),
            PorterDuff.Mode.SRC_ATOP
        )

        // steal the focus from any EditTexts
        dragBar.requestFocus()

        list.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@NavBottomSheet.adapter
        }
    }

    fun hideKeyboard() = context.getSystemService<InputMethodManager>()
        ?.hideSoftInputFromWindow(rootView.windowToken, 0)

    var onCloseListener: (() -> Unit)? = null

    /*    _____ _
         |_   _| |
           | | | |_ ___ _ __ ___  ___
           | | | __/ _ \ '_ ` _ \/ __|
          _| |_| ||  __/ | | | | \__ \
         |_____|\__\___|_| |_| |_|__*/
    private fun updateGroupedItems() {
        val primaryItems = items.filterIsInstance<BottomSheetPrimaryItem>()
        val otherItems = items.filter { it !is BottomSheetPrimaryItem && it !is BottomSheetSeparatorItem }
        items.clear()

        val grouped = primaryItems.groupBy { it.group }
        for (groupId in grouped.keys.sorted()) {
            val groupItems = grouped.getValue(groupId)
            val size = groupItems.size
            groupItems.forEachIndexed { index, item ->
                item.cornerPosition = when {
                    size == 1 -> CornerPosition.SINGLE
                    index == 0 -> CornerPosition.FIRST
                    index == size - 1 -> CornerPosition.LAST
                    else -> CornerPosition.MIDDLE
                }
                items.add(item)
            }
        }
        items.addAll(otherItems)

        items.forEachIndexed { index, item ->
            if (item is BottomSheetPrimaryItem) {
                item.isFirstInAdapter = index == 0
            }
        }

        adapter.notifyDataSetChanged()
    }

    operator fun plusAssign(item: IBottomSheetItem<*>) {
        appendItem(item)
    }

    fun appendItem(item: IBottomSheetItem<*>) {
        items.add(item)
        updateGroupedItems()
    }

    fun appendItems(vararg items: IBottomSheetItem<*>) {
        this.items.addAll(items)
        updateGroupedItems()
    }

    fun prependItem(item: IBottomSheetItem<*>) {
        items.add(0, item)
        updateGroupedItems()
    }

    fun prependItems(vararg items: IBottomSheetItem<*>) {
        this.items.addAll(0, items.toList())
        updateGroupedItems()
    }

    fun addItemAt(index: Int, item: IBottomSheetItem<*>) {
        items.add(index, item)
        updateGroupedItems()
    }

    fun removeItemById(id: Int) {
        items.removeAll { it.id == id }
        updateGroupedItems()
    }

    fun removeItemAt(index: Int) {
        if (index in 0 until items.size) {
            items.removeAt(index)
            updateGroupedItems()
        }
    }

    fun removeAllItems() {
        items.clear()
        updateGroupedItems()
    }

    fun removeAllStatic() {
        items.removeAll { !it.isContextual }
        updateGroupedItems()
    }

    fun removeAllContextual() {
        items.removeAll { it.isContextual }
        updateGroupedItems()
    }

    fun getItemById(id: Int, run: (it: IBottomSheetItem<*>?) -> Unit) {
        items.singleOrNull { it.id == id }.also {
            run(it)
            if (it != null)
                adapter.notifyItemChanged(items.indexOf(it))
        }
    }

    fun getItemByIndex(index: Int, run: (it: IBottomSheetItem<*>?) -> Unit) {
        items.getOrNull(index).also {
            run(it)
            if (it != null)
                adapter.notifyItemChanged(index)
        }
    }

    fun dispatchBottomSheetEvent(view: View, event: MotionEvent): Boolean {
        if (view == scrimView)
            gestureDetector.onTouchEvent(event)
        // When the sheet is closed its view is invisible and moved off-screen,
        // so there is nothing to forward to - and the original event must keep
        // its coordinates for the source view and its parents (e.g. the
        // DrawerLayout edge swipe detection in onTouchEvent).
        // Instead, watch for an upward swipe to open the sheet.
        if (!isOpen) {
            handleSwipeUpToOpen(event)
            return false
        }
        // Forward a copy: mutating the original event would corrupt the gesture
        // bookkeeping of every ancestor receiving it afterwards.
        val location = IntArray(2)
        bottomSheet.getLocationOnScreen(location)
        val forwarded = MotionEvent.obtain(event)
        forwarded.setLocation(event.rawX - location[0], event.rawY - location[1])
        bottomSheet.dispatchTouchEvent(forwarded)
        forwarded.recycle()
        return false
    }

    /**
     * Drags the closed sheet with the finger on an upward swipe starting on the
     * toolbar or bottom bar, and settles it open or closed on release.
     * Once the swipe passes the touch slop, the sheet quickly glides up to the
     * finger and then follows it 1:1. Horizontal swipes never reach the slop,
     * so the drawer edge gesture keeps working when the bars sit at the
     * bottom of the screen.
     */
    private fun handleSwipeUpToOpen(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Grab mid-animation without a visual jump.
                bottomSheet.animate().cancel()
                sheetCatchUpAnimator?.cancel()
                sheetCatchUpAnimator = null
                sheetDragPointerId = event.getPointerId(0)
                sheetDragStartY = event.getY(0)
                sheetDragBase = (-bottomSheet.translationY).coerceAtLeast(0f)
                sheetDragDy = sheetDragBase
                sheetFingerDelta = 0f
                sheetCatchUp = 0f
                sheetDragGrabbed = false
                if (sheetDragBase > 0f) {
                    bottomSheet.visibility = View.VISIBLE
                }
                sheetDragVelocity?.recycle()
                sheetDragVelocity = VelocityTracker.obtain().also { it.addMovement(event) }
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(sheetDragPointerId)
                if (index == -1) return
                sheetDragVelocity?.addMovement(event)
                sheetFingerDelta = sheetDragStartY - event.getY(index)
                if (!sheetDragGrabbed) {
                    if (sheetFingerDelta <= touchSlop) return
                    // Glide the sheet's top edge up to the finger, then follow it.
                    val sheetPos = IntArray(2)
                    bottomSheet.getLocationOnScreen(sheetPos)
                    val gap = (sheetPos[1] - event.rawY).coerceAtLeast(0f)
                    sheetDragGrabbed = true
                    if (gap > 0f) {
                        sheetCatchUpAnimator?.cancel()
                        sheetCatchUp = 0f
                        sheetCatchUpAnimator = ValueAnimator.ofFloat(0f, gap).apply {
                            duration = 180L
                            interpolator = DecelerateInterpolator()
                            addUpdateListener {
                                sheetCatchUp = it.animatedValue as Float
                                applySheetDragTranslation()
                            }
                            start()
                        }
                    }
                }
                applySheetDragTranslation()
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                val tracker = sheetDragVelocity
                var velocityY = 0f
                if (tracker != null && event.actionMasked == MotionEvent.ACTION_UP) {
                    tracker.addMovement(event)
                    tracker.computeCurrentVelocity(1000)
                    velocityY = tracker.yVelocity
                    tracker.recycle()
                } else {
                    tracker?.recycle()
                }
                sheetDragVelocity = null
                sheetDragPointerId = MotionEvent.INVALID_POINTER_ID
                settleSheetDrag(velocityY)
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == sheetDragPointerId) {
                    sheetDragVelocity?.recycle()
                    sheetDragVelocity = null
                    sheetDragPointerId = MotionEvent.INVALID_POINTER_ID
                    settleSheetDrag(0f)
                }
            }
        }
    }

    private fun applySheetDragTranslation() {
        val maxDy = bottomSheet.height.toFloat()
        if (maxDy <= 0f) return
        val dy = (sheetDragBase + sheetCatchUp + sheetFingerDelta).coerceIn(0f, maxDy)
        sheetDragDy = dy
        if (dy > 0f && bottomSheet.visibility != View.VISIBLE) {
            bottomSheet.visibility = View.VISIBLE
        }
        bottomSheet.translationY = -dy
        val progress = (dy / maxDy).coerceIn(0f, 1f)
        scrimView.visibility = if (progress > 0.01f) View.VISIBLE else View.GONE
        scrimView.alpha = progress
    }

    private fun settleSheetDrag(velocityY: Float) {
        sheetCatchUpAnimator?.cancel()
        sheetCatchUpAnimator = null
        val height = bottomSheet.height.toFloat()
        val flingUp = velocityY < -flingVelocitySlop
        if (height > 0f && (sheetDragDy > height * 0.4f || flingUp)) {
            // Hand over to the behavior without a visual jump: shift the layout
            // top to the current visual position first, so it only settles the
            // remaining distance (or nothing at all when fully dragged out).
            val offset = -sheetDragDy.toInt()
            bottomSheet.translationY = 0f
            if (offset != 0) ViewCompat.offsetTopAndBottom(bottomSheet, offset)
            open()
        } else {
            bottomSheet.animate()
                .translationY(0f)
                .setDuration(200L)
                .withEndAction {
                    if (!isOpen) {
                        bottomSheet.visibility = View.INVISIBLE
                        scrimView.visibility = View.GONE
                        scrimView.alpha = 0f
                    }
                }
                .start()
        }
        sheetDragDy = 0f
        sheetDragBase = 0f
        sheetFingerDelta = 0f
        sheetCatchUp = 0f
    }

    var isOpen
        get() = bottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN
                && bottomSheetBehavior.state != BottomSheetBehavior.STATE_COLLAPSED
        set(value) {
            if (value) {
                bottomSheet.visibility = VISIBLE
                scrimView.visibility = VISIBLE
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            } else {
                if (bottomSheet.isLaidOut &&
                    bottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN
                ) {
                    // Let the behavior animate down; onStateChanged(STATE_HIDDEN)
                    // hides the views and fires the close listener.
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                } else {
                    // Not laid out yet (or already hidden): hide immediately,
                    // as no settle animation - and no state callback - will run.
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                    bottomSheet.visibility = INVISIBLE
                    scrimView.visibility = GONE
                    scrimView.alpha = 0f
                }
            }
        }

    fun open() {
        isOpen = true
    }

    fun close() {
        isOpen = false
    }

    fun toggle() {
        isOpen = !isOpen
    }


    inner class OnGestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            isOpen = false
            return super.onSingleTapUp(e)
        }
    }
}
