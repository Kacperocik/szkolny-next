package pl.szczodrzynski.edziennik.ui.base.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.color.MaterialColors
import com.google.android.material.loadingindicator.LoadingIndicator
import com.google.android.material.loadingindicator.LoadingIndicatorDrawable
import pl.szczodrzynski.edziennik.ext.dp

class ExpressiveSwipeRefreshLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SwipeRefreshLayout(context, attrs) {

    private var indicatorView: LoadingIndicator? = null
    private var expressiveDrawable: LoadingIndicatorDrawable? = null
    private var circleView: ImageView? = null
    private var currentIndicatorColor: Int = 0
    private var currentContainerColor: Int = 0

    private var lastRecordedTop: Int = Int.MIN_VALUE
    private var isDraggingContent: Boolean = false
    private val contentSpringAnimations = mutableListOf<SpringAnimation>()
    private var lastTouchX: Float = -1f
    private var lastTouchY: Float = -1f

    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        if (!isRefreshing) {
            checkCircleViewMoved()
        }
        true
    }

    init {
        setupExpressiveIndicator()
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        setupExpressiveIndicator()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setupExpressiveIndicator()
        viewTreeObserver.addOnPreDrawListener(preDrawListener)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (viewTreeObserver.isAlive) {
            viewTreeObserver.removeOnPreDrawListener(preDrawListener)
        }
        cancelSpringAnimations()
        forEachContentChild { it.translationY = 0f }
        lastTouchX = -1f
        lastTouchY = -1f
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = ev.actionIndex.coerceIn(0, ev.pointerCount - 1)
                val loc = IntArray(2)
                getLocationOnScreen(loc)
                lastTouchX = loc[0] + ev.getX(index)
                lastTouchY = loc[1] + ev.getY(index)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                lastTouchX = -1f
                lastTouchY = -1f
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun canChildScrollUp(): Boolean {
        if (super.canChildScrollUp()) return true
        val circle = circleView ?: findCircleView()
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child !== circle && canViewOrChildScrollUp(child)) {
                return true
            }
        }
        return false
    }

    private fun canViewOrChildScrollUp(view: View): Boolean {
        if (!view.isVisible || !view.isShown || view.width <= 0 || view.height <= 0) return false

        val hasTouch = lastTouchX >= 0f && lastTouchY >= 0f
        if (hasTouch) {
            val loc = IntArray(2)
            view.getLocationOnScreen(loc)
            val left = loc[0]
            val top = loc[1]
            val right = left + view.width
            val bottom = top + view.height
            if (lastTouchX < left || lastTouchX > right || lastTouchY < top || lastTouchY > bottom) {
                return false
            }
        } else {
            val loc = IntArray(2)
            view.getLocationInWindow(loc)
            val w = width
            if (w > 0 && (loc[0] + view.width <= 0 || loc[0] >= w)) {
                return false
            }
        }

        if (view !== circleView && view.canScrollVertically(-1)) {
            return true
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                if (canViewOrChildScrollUp(view.getChildAt(i))) {
                    return true
                }
            }
        }
        return false
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            cancelSpringAnimations()
            isDraggingContent = false
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelSpringAnimations()
                isDraggingContent = false
            }
            MotionEvent.ACTION_MOVE -> {
                val circle = circleView ?: findCircleView()
                if (circle != null && circle.top > progressViewStartOffset) {
                    isDraggingContent = true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val result = super.onTouchEvent(ev)
                handleDragRelease()
                return result
            }
        }
        val result = super.onTouchEvent(ev)
        val circle = circleView ?: findCircleView()
        if (circle != null && circle.top > progressViewStartOffset) {
            isDraggingContent = true
            updateContentTranslation()
        }
        if (!isRefreshing) {
            checkCircleViewMoved()
        }
        return result
    }

    override fun onStartNestedScroll(child: View, target: View, axes: Int, type: Int): Boolean {
        val handled = super.onStartNestedScroll(child, target, axes, type)
        if (handled) {
            cancelSpringAnimations()
            isDraggingContent = false
        }
        return handled
    }

    override fun onStartNestedScroll(child: View, target: View, nestedScrollAxes: Int): Boolean {
        val handled = super.onStartNestedScroll(child, target, nestedScrollAxes)
        if (handled) {
            cancelSpringAnimations()
            isDraggingContent = false
        }
        return handled
    }

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int,
        consumed: IntArray
    ) {
        super.onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, type, consumed)
        val circle = circleView ?: findCircleView()
        if (circle != null && circle.top > progressViewStartOffset) {
            isDraggingContent = true
            updateContentTranslation()
        }
        if (!isRefreshing) {
            checkCircleViewMoved()
        }
    }

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int
    ) {
        super.onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed)
        val circle = circleView ?: findCircleView()
        if (circle != null && circle.top > progressViewStartOffset) {
            isDraggingContent = true
            updateContentTranslation()
        }
        if (!isRefreshing) {
            checkCircleViewMoved()
        }
    }

    override fun onStopNestedScroll(target: View, type: Int) {
        super.onStopNestedScroll(target, type)
        handleDragRelease()
    }

    override fun onStopNestedScroll(target: View) {
        super.onStopNestedScroll(target)
        handleDragRelease()
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (!isRefreshing) {
            checkCircleViewMoved()
        }
        super.dispatchDraw(canvas)
    }

    private fun checkCircleViewMoved() {
        val target = circleView ?: findCircleView() ?: return
        val currentTop = target.top
        if (currentTop != lastRecordedTop) {
            lastRecordedTop = currentTop
            target.invalidate()
            if (isDraggingContent) {
                updateContentTranslation()
            }
        }
    }

    private inline fun forEachContentChild(action: (View) -> Unit) {
        val circle = circleView ?: findCircleView()
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child !== circle) {
                action(child)
            }
        }
    }

    private fun updateContentTranslation() {
        val circle = circleView ?: findCircleView() ?: return
        val start = progressViewStartOffset
        val pullDistance = (circle.top - start).coerceAtLeast(0)
        val translation = pullDistance * CONTENT_DRAG_FACTOR
        forEachContentChild { child ->
            child.translationY = translation
        }
    }

    private fun cancelSpringAnimations() {
        if (contentSpringAnimations.isNotEmpty()) {
            contentSpringAnimations.forEach { it.cancel() }
            contentSpringAnimations.clear()
        }
    }

    private fun startContentSpringAnimation() {
        cancelSpringAnimations()
        forEachContentChild { child ->
            if (child.translationY != 0f) {
                val spring = SpringForce(0f).apply {
                    dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
                    stiffness = 600f
                }
                val anim = SpringAnimation(child, DynamicAnimation.TRANSLATION_Y, 0f).apply {
                    this.spring = spring
                }
                contentSpringAnimations.add(anim)
                anim.start()
            }
        }
    }

    private fun handleDragRelease() {
        if (isDraggingContent || hasOffsetContent()) {
            isDraggingContent = false
            startContentSpringAnimation()
        }
    }

    private fun hasOffsetContent(): Boolean {
        val circle = circleView ?: findCircleView()
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child !== circle && child.translationY != 0f) {
                return true
            }
        }
        return false
    }

    private fun findCircleView(): ImageView? {
        if (circleView != null) return circleView
        val found = (0 until childCount).firstNotNullOfOrNull { i ->
            getChildAt(i) as? ImageView
        } ?: run {
            try {
                val field = SwipeRefreshLayout::class.java.getDeclaredField("mCircleView")
                field.isAccessible = true
                field.get(this) as? ImageView
            } catch (e: Throwable) {
                null
            }
        }
        circleView = found
        return found
    }

    fun setupExpressiveIndicator() {
        val targetView = findCircleView() ?: return

        currentContainerColor = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorPrimaryContainer,
            MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerHigh, Color.WHITE)
        )
        currentIndicatorColor = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorOnPrimaryContainer,
            MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, Color.BLUE)
        )

        super.setProgressBackgroundColorSchemeColor(currentContainerColor)

        applyLoadingIndicatorDrawable(targetView)
    }

    private fun applyLoadingIndicatorDrawable(targetView: ImageView) {
        val indicator = LoadingIndicator(context, null, com.google.android.material.R.attr.loadingIndicatorStyle).apply {
            setContainerColor(Color.TRANSPARENT)
            setIndicatorColor(currentIndicatorColor)
            setIndicatorSize(24.dp)
            setContainerWidth(40.dp)
            setContainerHeight(40.dp)
        }
        indicatorView = indicator

        val baseDrawable = indicator.drawable
        expressiveDrawable = baseDrawable

        // Wrap the drawable so it rotates counter-clockwise when pulled,
        // unwinds clockwise on cancel, and animates with indeterminate rotation on refresh
        val controlledDrawable = object : Drawable(), Drawable.Callback {
            init {
                baseDrawable.callback = this
            }

            override fun draw(canvas: Canvas) {
                if (!isRefreshing) {
                    val target = circleView ?: findCircleView()
                    val start = progressViewStartOffset
                    val end = progressViewEndOffset
                    val totalDist = (end - start).toFloat()
                    val currentTop = target?.top ?: start
                    val fraction = if (totalDist > 0f) {
                        ((currentTop - start).toFloat() / totalDist).coerceAtLeast(0f)
                    } else 0f

                    // Indeterminate loading rotates clockwise (+).
                    // Swipe pull rotates in the opposite direction (counter-clockwise, -).
                    // On cancel, currentTop moves back to start, rewinding rotation back to 0.
                    val rotation = -360f * fraction
                    if (rotation != 0f) {
                        val count = canvas.save()
                        val cx = bounds.exactCenterX()
                        val cy = bounds.exactCenterY()
                        canvas.rotate(rotation, cx, cy)
                        baseDrawable.draw(canvas)
                        canvas.restoreToCount(count)
                        return
                    }
                }
                baseDrawable.draw(canvas)
            }

            override fun setAlpha(alpha: Int) {
                baseDrawable.alpha = alpha
            }

            override fun setColorFilter(colorFilter: ColorFilter?) {
                baseDrawable.colorFilter = colorFilter
            }

            @Deprecated("Deprecated in Java")
            override fun getOpacity(): Int = baseDrawable.opacity

            override fun setBounds(bounds: Rect) {
                super.setBounds(bounds)
                baseDrawable.bounds = bounds
            }

            override fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
                super.setBounds(left, top, right, bottom)
                baseDrawable.setBounds(left, top, right, bottom)
            }

            override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
                super.setVisible(visible, restart)
                val shouldAnimate = isRefreshing
                return baseDrawable.setVisible(visible, restart, shouldAnimate)
            }

            override fun invalidateDrawable(who: Drawable) {
                invalidateSelf()
            }

            override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
                scheduleSelf(what, `when`)
            }

            override fun unscheduleDrawable(who: Drawable, what: Runnable) {
                unscheduleSelf(what)
            }
        }

        targetView.setImageDrawable(controlledDrawable)

        if (targetView.width > 0 && targetView.height > 0) {
            controlledDrawable.setBounds(0, 0, targetView.width, targetView.height)
        }
        targetView.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            controlledDrawable.setBounds(0, 0, right - left, bottom - top)
        }

        // Never animate on initial setup when nothing is loading
        baseDrawable.setVisible(targetView.isVisible, false, isRefreshing)
    }

    override fun setProgressBackgroundColorSchemeColor(color: Int) {
        super.setProgressBackgroundColorSchemeColor(color)
        currentContainerColor = color
        findCircleView()?.setBackgroundColor(color)
    }

    override fun setColorSchemeColors(vararg colors: Int) {
        super.setColorSchemeColors(*colors)
        if (colors.isNotEmpty()) {
            currentIndicatorColor = colors[0]
            indicatorView?.setIndicatorColor(currentIndicatorColor)
            expressiveDrawable?.invalidateSelf()
        }
    }

    override fun setOnRefreshListener(listener: OnRefreshListener?) {
        if (listener == null) {
            super.setOnRefreshListener(null)
        } else {
            super.setOnRefreshListener {
                val target = findCircleView()
                val visible = target?.isVisible ?: false
                expressiveDrawable?.setVisible(visible, false, true)
                listener.onRefresh()
            }
        }
    }

    override fun setRefreshing(refreshing: Boolean) {
        super.setRefreshing(refreshing)
        val target = findCircleView()
        val visible = target?.isVisible ?: false
        expressiveDrawable?.setVisible(visible, false, refreshing)
        if (refreshing) {
            lastRecordedTop = Int.MIN_VALUE
            handleDragRelease()
        }
        target?.invalidate()
    }

    companion object {
        private const val CONTENT_DRAG_FACTOR = 0.5f
    }
}
