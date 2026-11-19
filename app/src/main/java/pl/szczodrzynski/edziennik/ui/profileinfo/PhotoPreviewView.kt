package pl.szczodrzynski.edziennik.ui.profileinfo

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.widget.AppCompatImageView

internal class PhotoPreviewView(context: Context, bitmap: Bitmap) : AppCompatImageView(context) {
    private val photoMatrix = Matrix()
    private var fitScale = 1f
    private var zoom = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    private val zoomAnimator = ValueAnimator().apply {
        duration = 220L
        interpolator = DecelerateInterpolator()
    }

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoomTo(zoom * detector.scaleFactor, detector.focusX, detector.focusY)
            return true
        }
    }).apply { isQuickScaleEnabled = false }

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true

        override fun onDoubleTapEvent(e: MotionEvent): Boolean {
            if (e.actionMasked == MotionEvent.ACTION_DOWN) {
                animateZoom(if (zoom > 1f) 1f else 2.5f, e.x, e.y)
            }
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (e2.pointerCount != 1 || scaleDetector.isInProgress || zoom <= 1f) return false
            offsetX -= distanceX
            offsetY -= distanceY
            updateMatrix()
            return true
        }
    }).apply { setIsLongpressEnabled(false) }

    init {
        scaleType = ScaleType.MATRIX
        setImageBitmap(bitmap)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        zoomAnimator.cancel()
        if (w == 0 || h == 0) return
        fitScale = minOf(w.toFloat() / drawable.intrinsicWidth, h.toFloat() / drawable.intrinsicHeight)
        zoom = 1f
        updateMatrix()
    }

    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            zoomAnimator.cancel()
        }
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    override fun onDetachedFromWindow() {
        zoomAnimator.cancel()
        super.onDetachedFromWindow()
    }

    private fun animateZoom(target: Float, focusX: Float, focusY: Float) {
        val startZoom = zoom
        val startX = offsetX
        val startY = offsetY
        zoomAnimator.apply {
            removeAllUpdateListeners()
            setFloatValues(startZoom, target)
            addUpdateListener {
                zoom = it.animatedValue as Float
                val ratio = zoom / startZoom
                offsetX = focusX - (focusX - startX) * ratio
                offsetY = focusY - (focusY - startY) * ratio
                updateMatrix()
            }
            start()
        }
    }

    private fun zoomTo(target: Float, focusX: Float, focusY: Float) {
        val newZoom = target.coerceIn(1f, 5f)
        val ratio = newZoom / zoom
        offsetX = focusX - (focusX - offsetX) * ratio
        offsetY = focusY - (focusY - offsetY) * ratio
        zoom = newZoom
        updateMatrix()
    }

    private fun updateMatrix() {
        val scale = fitScale * zoom
        offsetX = constrainOffset(offsetX, width - drawable.intrinsicWidth * scale)
        offsetY = constrainOffset(offsetY, height - drawable.intrinsicHeight * scale)
        photoMatrix.setScale(scale, scale)
        photoMatrix.postTranslate(offsetX, offsetY)
        imageMatrix = photoMatrix
    }

    private fun constrainOffset(offset: Float, space: Float): Float =
        if (space >= 0f) space / 2 else offset.coerceIn(space, 0f)
}
