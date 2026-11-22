package pl.szczodrzynski.navlib

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.core.animation.doOnEnd
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.mikepenz.materialdrawer.view.BezelImageView
import kotlin.math.abs

class ToolbarProfileImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : BezelImageView(context, attrs, defStyleAttr) {

    internal var onProfileSwipe: ((ToolbarProfileImageView, Int) -> Unit)? = null

    private val swipeDistance = maxOf(
        24f * resources.displayMetrics.density,
        ViewConfiguration.get(context).scaledTouchSlop * 2f,
    )
    private var swipeStart: PointF? = null
    private val slidePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var profileSlide: ValueAnimator? = null
    private var outgoingImage: Bitmap? = null
    private var slideDistance = 0f
    private var slideProgress = 0f

    internal fun slideToProfile(image: Drawable?, offset: Int) {
        if (image == null || drawable == null || !isAttachedToWindow || !isShown || width == 0 || height == 0) {
            profileSlide?.cancel()
            setImageDrawable(image)
            return
        }

        val previousImage = Bitmap.createBitmap(resources.displayMetrics, width, height, Bitmap.Config.ARGB_8888)
        onDraw(Canvas(previousImage))
        profileSlide?.cancel()
        setImageDrawable(image)

        outgoingImage = previousImage
        slideDistance = height.toFloat() * offset
        slideProgress = 0f
        profileSlide = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 240L
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener {
                slideProgress = it.animatedValue as Float
                invalidate()
            }
            doOnEnd {
                outgoingImage = null
                profileSlide = null
                invalidate()
            }
        }
        profileSlide?.start()
    }

    override fun onDraw(canvas: Canvas) {
        val previousImage = outgoingImage
        if (previousImage == null) {
            super.onDraw(canvas)
            return
        }

        val saved = canvas.save()
        canvas.clipRect(0, 0, width, height)
        canvas.drawBitmap(previousImage, 0f, -slideDistance * slideProgress, slidePaint)
        canvas.translate(0f, slideDistance * (1f - slideProgress))
        super.onDraw(canvas)
        canvas.restoreToCount(saved)
    }

    override fun onDetachedFromWindow() {
        profileSlide?.cancel()
        swipeStart = null
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> swipeStart = PointF(event.rawX, event.rawY)
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> swipeStart = null
            MotionEvent.ACTION_UP -> {
                val start = swipeStart
                swipeStart = null
                val listener = onProfileSwipe
                if (start != null && listener != null && isEnabled) {
                    val dx = event.rawX - start.x
                    val dy = event.rawY - start.y
                    if (abs(dy) >= swipeDistance && abs(dy) > abs(dx)) {
                        // Cancel the pending click before switching profiles.
                        val cancel = MotionEvent.obtain(event)
                        cancel.action = MotionEvent.ACTION_CANCEL
                        super.onTouchEvent(cancel)
                        cancel.recycle()
                        listener(this, if (dy < 0) 1 else -1)
                        return true
                    }
                }
            }
        }
        return super.onTouchEvent(event)
    }
}
