package pl.szczodrzynski.edziennik.ui.base.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import pl.szczodrzynski.edziennik.ext.dp

class SquareFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private var currentRadiusPx = -1f
    private var shapeAnimator: ValueAnimator? = null
    private var isPressedDown = false
    var isSwatchSelected: Boolean = false
        private set

    private val selectedRadiusPx get() = 10.dp.toFloat()
    private val pressedSelectedRadiusPx get() = 5.dp.toFloat()
    private val circleRadiusPx get() = if (width > 0) width / 2f else 18.dp.toFloat()

    init {
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val radius = if (currentRadiusPx >= 0f) currentRadiusPx else view.width / 2f
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
        clipToOutline = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val size = minOf(measuredWidth, measuredHeight)
        val squareSpec = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)
        super.onMeasure(squareSpec, squareSpec)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val target = if (isSwatchSelected) selectedRadiusPx else (w / 2f)
        currentRadiusPx = target
        applyRadius(target)
    }

    fun setSwatchSelected(selected: Boolean, animated: Boolean = true) {
        if (isSwatchSelected == selected && currentRadiusPx >= 0f) return
        isSwatchSelected = selected

        val targetRadius = if (selected) selectedRadiusPx else circleRadiusPx
        if (animated && isAttachedToWindow && width > 0) {
            animateToRadius(targetRadius, durationMs = 250L)
        } else {
            shapeAnimator?.cancel()
            currentRadiusPx = targetRadius
            applyRadius(targetRadius)
        }
    }

    private fun animateToRadius(targetRadius: Float, durationMs: Long) {
        shapeAnimator?.cancel()
        val startRadius = if (currentRadiusPx >= 0f) currentRadiusPx else circleRadiusPx
        if (startRadius == targetRadius) return
        shapeAnimator = ValueAnimator.ofFloat(startRadius, targetRadius).apply {
            duration = durationMs
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener { anim ->
                currentRadiusPx = anim.animatedValue as Float
                applyRadius(currentRadiusPx)
            }
            start()
        }
    }

    private fun applyRadius(radius: Float) {
        (background?.mutate() as? GradientDrawable)?.apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
        }
        invalidateOutline()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isPressedDown = true
                val target = if (isSwatchSelected) pressedSelectedRadiusPx else selectedRadiusPx
                animateToRadius(target, durationMs = 200L)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isPressedDown) {
                    isPressedDown = false
                    val target = if (isSwatchSelected) selectedRadiusPx else circleRadiusPx
                    animateToRadius(target, durationMs = 250L)
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        shapeAnimator?.cancel()
    }
}

class ColorSatValView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var hue = 0f
        set(value) {
            if (field == value) return
            field = value
            svBitmap?.recycle()
            svBitmap = null
            invalidate()
        }
    var saturation = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }
    var value = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var onChanged: ((saturation: Float, value: Float) -> Unit)? = null

    private val pointerRadius = 10.dp.toFloat()
    private val cornerRadius = 16.dp.toFloat()
    private val pointerStrokeWidth = 2.dp.toFloat()
    private val pointerShadowWidth = 4.dp.toFloat()
    private val pointerMargin = 14.dp.toFloat()

    private val bounds = RectF()
    private val pointerFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pointerStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = pointerStrokeWidth
        color = Color.WHITE
    }
    private val pointerShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = pointerShadowWidth
        color = Color.argb(60, 0, 0, 0)
    }
    private val clipPath = Path()
    private var svBitmap: Bitmap? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bounds.set(pointerMargin, pointerMargin, w - pointerMargin, h - pointerMargin)
        clipPath.reset()
        clipPath.addRoundRect(bounds, cornerRadius, cornerRadius, Path.Direction.CW)
        svBitmap?.recycle()
        svBitmap = null
    }

    override fun onDraw(canvas: Canvas) {
        if (bounds.isEmpty) return
        val bmp = svBitmap ?: createSvBitmap().also { svBitmap = it }
        val saved = canvas.save()
        canvas.clipPath(clipPath)
        canvas.drawBitmap(bmp, bounds.left, bounds.top, null)
        canvas.restoreToCount(saved)

        val x = bounds.left + saturation * bounds.width()
        val y = bounds.top + (1f - value) * bounds.height()
        pointerFill.color = Color.HSVToColor(floatArrayOf(hue, saturation, value))
        canvas.drawCircle(x, y, pointerRadius, pointerShadow)
        canvas.drawCircle(x, y, pointerRadius, pointerFill)
        canvas.drawCircle(x, y, pointerRadius, pointerStroke)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent.requestDisallowInterceptTouchEvent(true)
        saturation = ((event.x - bounds.left) / bounds.width()).coerceIn(0f, 1f)
        value = (1f - (event.y - bounds.top) / bounds.height()).coerceIn(0f, 1f)
        onChanged?.invoke(saturation, value)
        invalidate()
        return true
    }

    private fun createSvBitmap(): Bitmap {
        val width = bounds.width().toInt().coerceAtLeast(1)
        val height = bounds.height().toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val hueColor = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
        val sat = LinearGradient(0f, 0f, width.toFloat(), 0f, Color.WHITE, hueColor, Shader.TileMode.CLAMP)
        val valShader = LinearGradient(0f, 0f, 0f, height.toFloat(), Color.WHITE, Color.BLACK, Shader.TileMode.CLAMP)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = ComposeShader(valShader, sat, PorterDuff.Mode.MULTIPLY)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        return bitmap
    }
}

class ColorHueBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var hue = 0f
        set(value) {
            field = value.coerceIn(0f, 360f)
            invalidate()
        }

    var onChanged: ((Float) -> Unit)? = null

    private val hues = intArrayOf(
        0xFFFF0000.toInt(),
        0xFFFFFF00.toInt(),
        0xFF00FF00.toInt(),
        0xFF00FFFF.toInt(),
        0xFF0000FF.toInt(),
        0xFFFF00FF.toInt(),
        0xFFFF0000.toInt(),
    )
    private val bounds = RectF()
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pointerFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pointerStrokeWidth = 2.dp.toFloat()
    private val pointerShadowWidth = 4.dp.toFloat()
    private val pointerStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = pointerStrokeWidth
        color = Color.WHITE
    }
    private val pointerShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = pointerShadowWidth
        color = Color.argb(60, 0, 0, 0)
    }
    private val pointerRadius = 10.dp.toFloat()
    private val cornerRadius = 12.dp.toFloat()
    private val pointerMargin = 14.dp.toFloat()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bounds.set(pointerMargin, h / 2f - 8.dp, w - pointerMargin, h / 2f + 8.dp)
        trackPaint.shader = LinearGradient(
            bounds.left, 0f, bounds.right, 0f, hues, null, Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        if (bounds.isEmpty) return
        canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, trackPaint)
        val x = bounds.left + (hue / 360f) * bounds.width()
        val y = height / 2f
        pointerFill.color = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
        canvas.drawCircle(x, y, pointerRadius, pointerShadow)
        canvas.drawCircle(x, y, pointerRadius, pointerFill)
        canvas.drawCircle(x, y, pointerRadius, pointerStroke)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent.requestDisallowInterceptTouchEvent(true)
        hue = ((event.x - bounds.left) / bounds.width()).coerceIn(0f, 1f) * 360f
        onChanged?.invoke(hue)
        invalidate()
        return true
    }
}
