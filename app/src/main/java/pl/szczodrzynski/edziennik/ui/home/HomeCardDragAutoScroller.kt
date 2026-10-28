package pl.szczodrzynski.edziennik.ui.home

import android.annotation.SuppressLint
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.roundToInt

/** Scrolls the outer viewport while ItemTouchHelper owns the card's touch gesture. */
@SuppressLint("ClickableViewAccessibility") // Observes the gesture without consuming touch events.
internal class HomeCardDragAutoScroller(
    private val list: RecyclerView,
    private val scrollView: NestedScrollView,
) : View.OnTouchListener, View.OnAttachStateChangeListener, Runnable {
    private val visibleBounds = Rect()
    private val location = IntArray(2)
    private val pointerProperties = arrayOf(MotionEvent.PointerProperties())
    private val pointerCoords = arrayOf(MotionEvent.PointerCoords())
    private var lastTouch: MotionEvent? = null
    private var fingerX = 0f
    private var fingerY = 0f
    private var isDragging = false
    private var isDispatchingMove = false
    private var isFrameScheduled = false
    private var lastFrameTime = 0L
    private var scrollRemainder = 0f

    init {
        list.setOnTouchListener(this)
        list.addOnAttachStateChangeListener(this)
    }

    fun setDragging(dragging: Boolean) {
        isDragging = dragging
        if (dragging) {
            lastFrameTime = SystemClock.uptimeMillis()
            scheduleFrame()
        } else {
            list.removeCallbacks(this)
            isFrameScheduled = false
            scrollRemainder = 0f
            lastTouch?.recycle()
            lastTouch = null
        }
    }

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        if (isDispatchingMove) return false
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            setDragging(false)
        } else {
            lastTouch?.recycle()
            lastTouch = MotionEvent.obtain(event)
            fingerX = event.rawX
            fingerY = event.rawY
            if (isDragging) scheduleFrame()
        }
        // ItemTouchHelper continues to handle selection, movement and dropping.
        return false
    }

    private fun scheduleFrame() {
        if (!isFrameScheduled && list.isAttachedToWindow) {
            isFrameScheduled = true
            list.postOnAnimation(this)
        }
    }

    override fun run() {
        isFrameScheduled = false
        val touch = lastTouch ?: return
        if (!isDragging || touch.pointerCount != 1 || !scrollView.getLocalVisibleRect(visibleBounds)) return

        scrollView.getLocationOnScreen(location)
        // The local visible rectangle includes the scroll offset; the edges
        // must stay at the viewport's screen position while the content moves.
        val top = location[1] + visibleBounds.top - scrollView.scrollY
        val bottom = location[1] + visibleBounds.bottom - scrollView.scrollY
        val density = list.resources.displayMetrics.density
        val edgeSize = minOf(56f * density, (bottom - top) / 4f)
        if (edgeSize <= 0f) return
        val edge = when {
            fingerY < top + edgeSize -> -((top + edgeSize - fingerY) / edgeSize).coerceAtMost(1f)
            fingerY > bottom - edgeSize -> ((fingerY - bottom + edgeSize) / edgeSize).coerceAtMost(1f)
            else -> 0f
        }
        val now = SystemClock.uptimeMillis()
        val elapsed = (now - lastFrameTime).coerceIn(0L, 32L)
        lastFrameTime = now
        if (edge == 0f || !scrollView.canScrollVertically(if (edge < 0) -1 else 1)) {
            scrollRemainder = 0f
            return
        }

        // Accelerate towards the edge, with a speed independent of the refresh rate.
        val distance = edge * kotlin.math.abs(edge) * 600f * density * elapsed / 1000f + scrollRemainder
        val scrollBy = distance.roundToInt()
        scrollRemainder = distance - scrollBy
        val oldScrollY = scrollView.scrollY
        scrollView.scrollBy(0, scrollBy)
        if (scrollView.scrollY != oldScrollY) {
            // Scrolling moves the RecyclerView beneath a stationary finger. Update
            // ItemTouchHelper's coordinates so the card and drop targets follow it.
            dispatchDragMove(touch, now)
        }
        if (isDragging) scheduleFrame()
    }

    private fun dispatchDragMove(touch: MotionEvent, now: Long) {
        list.getLocationOnScreen(location)
        touch.getPointerProperties(0, pointerProperties[0])
        touch.getPointerCoords(0, pointerCoords[0])
        pointerCoords[0].x = fingerX - location[0]
        pointerCoords[0].y = fingerY - location[1]
        val move = MotionEvent.obtain(
            touch.downTime, now, MotionEvent.ACTION_MOVE, 1, pointerProperties, pointerCoords,
            touch.metaState, touch.buttonState, touch.xPrecision, touch.yPrecision,
            touch.deviceId, touch.edgeFlags, touch.source, touch.flags,
        )
        isDispatchingMove = true
        try {
            list.dispatchTouchEvent(move)
        } finally {
            isDispatchingMove = false
            move.recycle()
        }
    }

    override fun onViewAttachedToWindow(view: View) = Unit

    override fun onViewDetachedFromWindow(view: View) {
        setDragging(false)
    }
}
