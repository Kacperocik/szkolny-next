/*
 * Copyright (c) Kuba Szczodrzyński 2021-10-17.
 */

package pl.szczodrzynski.edziennik.ext

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.core.graphics.ColorUtils
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonGroup
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.navlib.dispatchExpressiveImageMorphToDescendants

fun TextView.setText(@StringRes resid: Int, vararg formatArgs: Any) {
    text = context.getString(resid, *formatArgs)
}

@Suppress("UNCHECKED_CAST")
inline fun <T : View> T.onClick(crossinline onClickListener: (v: T) -> Unit) {
    setOnClickListener { v: View ->
        onClickListener(v as T)
    }
}

@Suppress("UNCHECKED_CAST")
inline fun <T : View> T.onLongClick(crossinline onLongClickListener: (v: T) -> Boolean) {
    setOnLongClickListener { v: View ->
        onLongClickListener(v as T)
    }
}

@Suppress("UNCHECKED_CAST")
inline fun <T : CompoundButton> T.onChange(crossinline onChangeListener: (v: T, isChecked: Boolean) -> Unit) {
    setOnCheckedChangeListener { buttonView, isChecked ->
        onChangeListener(buttonView as T, isChecked)
    }
}

@Suppress("UNCHECKED_CAST")
inline fun <T : MaterialButton> T.onChange(crossinline onChangeListener: (v: T, isChecked: Boolean) -> Unit) {
    clearOnCheckedChangeListeners()
    addOnCheckedChangeListener { buttonView, isChecked ->
        onChangeListener(buttonView as T, isChecked)
    }
}

fun View.attachToastHint(stringRes: Int) = onLongClick {
    Toast.makeText(it.context, stringRes, Toast.LENGTH_SHORT).show()
    true
}

fun View.detachToastHint() = setOnLongClickListener(null)

/**
 * Convert a value in dp to pixels.
 */
val Int.dp: Int
    get() = (this * Resources.getSystem().displayMetrics.density).toInt()
/**
 * Convert a value in pixels to dp.
 */
val Int.px: Int
    get() = (this / Resources.getSystem().displayMetrics.density).toInt()

fun View.findParentById(targetId: Int): View? {
    if (id == targetId) {
        return this
    }
    val viewParent = this.parent ?: return null
    if (viewParent is View) {
        return viewParent.findParentById(targetId)
    }
    return null
}

fun CheckBox.trigger() { isChecked = !isChecked }

inline fun RadioButton.setOnSelectedListener(crossinline listener: (buttonView: CompoundButton) -> Unit)
        = setOnCheckedChangeListener { buttonView, isChecked -> if (isChecked) listener(buttonView) }

fun TextView.getTextPosition(range: IntRange): Rect {
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    // Initialize global value
    var parentTextViewRect = Rect()

    // Initialize values for the computing of clickedText position
    //val completeText = parentTextView.text as SpannableString
    val textViewLayout = this.layout

    val startOffsetOfClickedText = range.first//completeText.getSpanStart(clickedText)
    val endOffsetOfClickedText = range.last//completeText.getSpanEnd(clickedText)
    var startXCoordinatesOfClickedText = textViewLayout.getPrimaryHorizontal(startOffsetOfClickedText)
    var endXCoordinatesOfClickedText = textViewLayout.getPrimaryHorizontal(endOffsetOfClickedText)

    // Get the rectangle of the clicked text
    val currentLineStartOffset = textViewLayout.getLineForOffset(startOffsetOfClickedText)
    val currentLineEndOffset = textViewLayout.getLineForOffset(endOffsetOfClickedText)
    val keywordIsInMultiLine = currentLineStartOffset != currentLineEndOffset
    textViewLayout.getLineBounds(currentLineStartOffset, parentTextViewRect)

    // Update the rectangle position to his real position on screen
    val parentTextViewLocation = intArrayOf(0, 0)
    this.getLocationOnScreen(parentTextViewLocation)

    val parentTextViewTopAndBottomOffset = (parentTextViewLocation[1] - this.scrollY + this.compoundPaddingTop)
    parentTextViewRect.top += parentTextViewTopAndBottomOffset
    parentTextViewRect.bottom += parentTextViewTopAndBottomOffset

    // In the case of multi line text, we have to choose what rectangle take
    if (keywordIsInMultiLine) {
        val screenHeight = windowManager.defaultDisplay.height
        val dyTop = parentTextViewRect.top
        val dyBottom = screenHeight - parentTextViewRect.bottom
        val onTop = dyTop > dyBottom

        if (onTop) {
            endXCoordinatesOfClickedText = textViewLayout.getLineRight(currentLineStartOffset);
        } else {
            parentTextViewRect = Rect()
            textViewLayout.getLineBounds(currentLineEndOffset, parentTextViewRect);
            parentTextViewRect.top += parentTextViewTopAndBottomOffset;
            parentTextViewRect.bottom += parentTextViewTopAndBottomOffset;
            startXCoordinatesOfClickedText = textViewLayout.getLineLeft(currentLineEndOffset);
        }
    }

    parentTextViewRect.left += (
            parentTextViewLocation[0] +
                    startXCoordinatesOfClickedText +
                    this.compoundPaddingLeft -
                    this.scrollX
            ).toInt()
    parentTextViewRect.right = (
            parentTextViewRect.left +
                    endXCoordinatesOfClickedText -
                    startXCoordinatesOfClickedText
            ).toInt()

    return parentTextViewRect
}

inline fun ViewPager.addOnPageSelectedListener(crossinline block: (position: Int) -> Unit) = addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
    override fun onPageScrollStateChanged(state: Int) {}
    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
    override fun onPageSelected(position: Int) { block(position) }
})

fun View.removeFromParent() {
    (parent as? ViewGroup)?.removeView(this)
}

fun View.appendView(child: View) {
    val parent = parent as? ViewGroup ?: return
    val index = parent.indexOfChild(this)
    parent.addView(child, index + 1)
}

/**
 * Applies Material Design 3 Expressive shape morphing to a MaterialButton on press/touch interactions.
 * Smoothly morphs corner sizes when pressed and restores them when released. For text buttons (like OK or Zamknij),
 * a subtle container highlight is animated quickly during press so the shape morph is clearly visible.
 */
@SuppressLint("ClickableViewAccessibility")
fun MaterialButton.enableExpressiveShapeMorph(
    restCornerRadiusDp: Float = 20f,
    pressedCornerRadiusDp: Float = 8f,
    pressDurationMs: Long = 250L,
    releaseDurationMs: Long = 250L,
    minHoldTimeMs: Long = 200L,
) {
    if (parent is MaterialButtonGroup) return

    val density = Resources.getSystem().displayMetrics.density
    val restRadiusPx = restCornerRadiusDp * density
    val pressedRadiusPx = pressedCornerRadiusDp * density

    shapeAppearanceModel = shapeAppearanceModel.toBuilder()
        .setAllCornerSizes(restRadiusPx)
        .build()

    val isTextButton = strokeWidth == 0 && (backgroundTintList == null || backgroundTintList?.defaultColor == Color.TRANSPARENT)
    val baseColor = MaterialColors.getColor(this, R.attr.colorPrimary, Color.GRAY)
    val colorRest = ColorUtils.setAlphaComponent(baseColor, 0)
    val colorPressed = ColorUtils.setAlphaComponent(baseColor, 35) // ~14% opacity

    val actualPressDuration = if (isTextButton) 400L else pressDurationMs
    val actualReleaseDuration = if (isTextButton) 400L else releaseDurationMs
    val actualMinHoldTime = if (isTextButton) 350L else minHoldTimeMs

    var shapeAnimator: ValueAnimator? = null
    var tintAnimator: ValueAnimator? = null
    var downTimeMs = 0L

    var isCornersCaptured = false
    var activeTL = true
    var activeTR = true
    var activeBL = true
    var activeBR = true

    fun captureActiveCornersIfNeeded() {
        if (isCornersCaptured) return
        activeTL = true
        activeTR = true
        activeBL = true
        activeBR = true
        isCornersCaptured = true
    }

    fun animateShape(targetRadiusPx: Float, durationMs: Long, delayMs: Long = 0L) {
        if (!activeTL && !activeTR && !activeBL && !activeBR) return

        val b = RectF(0f, 0f, width.coerceAtLeast(10).toFloat(), height.coerceAtLeast(10).toFloat())
        val curTL = shapeAppearanceModel.topLeftCornerSize.getCornerSize(b)
        val curTR = shapeAppearanceModel.topRightCornerSize.getCornerSize(b)
        val curBL = shapeAppearanceModel.bottomLeftCornerSize.getCornerSize(b)
        val curBR = shapeAppearanceModel.bottomRightCornerSize.getCornerSize(b)
        val curMax = maxOf(curTL, curTR, curBL, curBR)

        shapeAnimator?.cancel()
        shapeAnimator = ValueAnimator.ofFloat(curMax, targetRadiusPx).apply {
            this.duration = durationMs
            this.startDelay = delayMs
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener { anim ->
                val size = anim.animatedValue as Float
                shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                    .setTopLeftCornerSize(if (activeTL) size else 0f)
                    .setTopRightCornerSize(if (activeTR) size else 0f)
                    .setBottomLeftCornerSize(if (activeBL) size else 0f)
                    .setBottomRightCornerSize(if (activeBR) size else 0f)
                    .build()
            }
            start()
        }
    }

    fun animateTint(targetColor: Int, durationMs: Long, delayMs: Long = 0L) {
        if (!isTextButton) return
        val currentColor = backgroundTintList?.defaultColor ?: colorRest
        tintAnimator?.cancel()
        tintAnimator = ValueAnimator.ofObject(ArgbEvaluator(), currentColor, targetColor).apply {
            this.duration = durationMs
            this.startDelay = delayMs
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener { anim ->
                val color = anim.animatedValue as Int
                backgroundTintList = ColorStateList.valueOf(color)
            }
            start()
        }
    }

    setOnTouchListener { v, event ->
        if (!v.isEnabled || !v.isClickable) {
            return@setOnTouchListener v.onTouchEvent(event)
        }
        @Suppress("UNCHECKED_CAST")
        val extraListener = v.getTag(R.id.expressive_button_touch_listener) as? ((View, MotionEvent) -> Unit)
        extraListener?.invoke(v, event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downTimeMs = System.currentTimeMillis()
                captureActiveCornersIfNeeded()
                animateShape(pressedRadiusPx, actualPressDuration)
                // Fade in quickly to reveal the shape before it finishes morphing
                animateTint(colorPressed, 100L)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val elapsed = System.currentTimeMillis() - downTimeMs
                val delay = if (elapsed < actualMinHoldTime) actualMinHoldTime - elapsed else 0L
                animateShape(restRadiusPx, actualReleaseDuration, delayMs = delay)
                animateTint(colorRest, actualReleaseDuration, delayMs = delay)
            }
        }
        v.onTouchEvent(event)
    }
}

fun MaterialButton.addExpressiveTouchCallback(callback: (View, MotionEvent) -> Unit) {
    setTag(R.id.expressive_button_touch_listener, callback)
    enableExpressiveShapeMorph()
}

/**
 * Connects side-by-side buttons in a horizontal row so that touching one expands it,
 * squeezing neighboring buttons with Expressive motion physics, and returning with a spring bounce.
 */
fun setupExpressiveRowButtonSquish(vararg buttons: MaterialButton, targetDeltaWeight: Float = 0.35f) {
    if (buttons.size < 2) return

    val allButtons = buttons.toList()
    var currentProgress = 0f
    var activeButton: MaterialButton? = null
    var activeAnimator: ValueAnimator? = null
    var springAnimation: SpringAnimation? = null

    // Ensure parent doesn't align by text baseline vertically
    (allButtons.firstOrNull()?.parent as? LinearLayout)?.isBaselineAligned = false

    fun getVisibleButtons(): List<MaterialButton> = allButtons.filter { it.isVisible }

    fun updateWeights(activeBtn: MaterialButton?, progress: Float) {
        val visibleButtons = getVisibleButtons()
        val n = visibleButtons.size
        if (n < 2) return

        val delta = (progress / 100f) * targetDeltaWeight
        val weights = FloatArray(n) { 1f }
        val activeIdx = if (activeBtn != null) visibleButtons.indexOf(activeBtn) else -1

        if (activeIdx in 0 until n && delta != 0f) {
            weights[activeIdx] = 1f + delta

            if (activeIdx == 0) {
                // First in line: expands only to the right into the next button
                weights[1] = 1f - delta
            } else if (activeIdx == n - 1) {
                // Last in line: expands only to the left into the previous button
                weights[n - 2] = 1f - delta
            } else {
                // Middle button: expands in both directions, squeezing both neighbors equally
                weights[activeIdx - 1] = 1f - delta / 2f
                weights[activeIdx + 1] = 1f - delta / 2f
            }
        }

        for (i in 0 until n) {
            val btn = visibleButtons[i]
            val lp = btn.layoutParams as? LinearLayout.LayoutParams ?: continue
            val newWeight = weights[i].coerceAtLeast(0.1f)
            if (lp.weight != newWeight) {
                lp.weight = newWeight
                btn.layoutParams = lp
            }
        }
    }

    fun animateToProgress(activeBtn: MaterialButton?, targetProgress: Float, useSpring: Boolean) {
        activeAnimator?.cancel()
        springAnimation?.cancel()

        if (useSpring) {
            val floatHolder = FloatValueHolder(currentProgress)
            val spring = SpringForce(targetProgress).apply {
                // Expressive bounce with medium damping and low stiffness for clear, tactile elasticity
                dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
                stiffness = 350f
            }
            springAnimation = SpringAnimation(floatHolder).apply {
                this.spring = spring
                setMinimumVisibleChange(0.05f)
                addUpdateListener { _, value, _ ->
                    currentProgress = value
                    updateWeights(activeBtn, value)
                }
                addEndListener { _, _, _, _ ->
                    if (targetProgress == 0f) {
                        currentProgress = 0f
                        activeButton = null
                        updateWeights(null, 0f)
                    }
                }
                start()
            }
        } else {
            activeAnimator = ValueAnimator.ofFloat(currentProgress, targetProgress).apply {
                duration = 180L
                interpolator = FastOutSlowInInterpolator()
                addUpdateListener { anim ->
                    val value = anim.animatedValue as Float
                    currentProgress = value
                    updateWeights(activeBtn, value)
                }
                start()
            }
        }
    }

    allButtons.forEach { button ->
        button.addExpressiveTouchCallback { _, event ->
            val visibleButtons = getVisibleButtons()
            if (visibleButtons.size < 2 || !visibleButtons.contains(button)) return@addExpressiveTouchCallback

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    activeButton = button
                    animateToProgress(button, targetProgress = 100f, useSpring = false)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (activeButton == button) {
                        animateToProgress(button, targetProgress = 0f, useSpring = true)
                    }
                }
            }
        }
    }
}

/**
 * Checks if a View and its child interactive elements (like CheckBox, Switch, Button, TextView, etc.) are enabled.
 */
fun View.isViewAndChildrenEnabled(): Boolean {
    if (!isEnabled) return false
    if (this is ViewGroup) {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (!child.isEnabled) return false
            if (child is ViewGroup && !child.isViewAndChildrenEnabled()) return false
        }
    }
    return true
}

/**
 * Applies Material Design 3 Expressive shape morphing to a FloatingActionButton on press/touch interactions.
 */
@SuppressLint("ClickableViewAccessibility")
fun FloatingActionButton.enableExpressiveFabShapeMorph(
    restCornerRadiusDp: Float = 20f,
    pressedCornerRadiusDp: Float = 8f,
    pressDurationMs: Long = 250L,
    releaseDurationMs: Long = 250L,
    minHoldTimeMs: Long = 200L,
) {
    val density = Resources.getSystem().displayMetrics.density
    val restRadiusPx = restCornerRadiusDp * density
    val pressedRadiusPx = pressedCornerRadiusDp * density

    var shapeAnimator: ValueAnimator? = null
    var downTimeMs = 0L

    fun animateShape(targetRadiusPx: Float, durationMs: Long, delayMs: Long = 0L) {
        val b = RectF(0f, 0f, width.coerceAtLeast(10).toFloat(), height.coerceAtLeast(10).toFloat())
        val curRadius = shapeAppearanceModel.topLeftCornerSize.getCornerSize(b)

        shapeAnimator?.cancel()
        shapeAnimator = ValueAnimator.ofFloat(curRadius, targetRadiusPx).apply {
            this.duration = durationMs
            this.startDelay = delayMs
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener { anim ->
                val size = anim.animatedValue as Float
                shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                    .setAllCornerSizes(size)
                    .build()
            }
            start()
        }
    }

    setOnTouchListener { v, event ->
        if (!v.isEnabled || !v.isClickable) {
            return@setOnTouchListener v.onTouchEvent(event)
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downTimeMs = System.currentTimeMillis()
                animateShape(pressedRadiusPx, pressDurationMs)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val elapsed = System.currentTimeMillis() - downTimeMs
                val delay = if (elapsed < minHoldTimeMs) minHoldTimeMs - elapsed else 0L
                animateShape(restRadiusPx, releaseDurationMs, delayMs = delay)
            }
        }
        v.onTouchEvent(event)
    }
}

/**
 * Applies Material Design 3 Expressive shape morphing to a Chip on press/touch interactions.
 */
@SuppressLint("ClickableViewAccessibility")
fun Chip.enableExpressiveChipShapeMorph(
    restCornerRadiusDp: Float = 20f,
    pressedCornerRadiusDp: Float = 8f,
    pressDurationMs: Long = 250L,
    releaseDurationMs: Long = 250L,
    minHoldTimeMs: Long = 200L,
) {
    val density = Resources.getSystem().displayMetrics.density
    val restRadiusPx = restCornerRadiusDp * density
    val pressedRadiusPx = pressedCornerRadiusDp * density

    shapeAppearanceModel = shapeAppearanceModel.toBuilder()
        .setAllCornerSizes(restRadiusPx)
        .build()

    var shapeAnimator: ValueAnimator? = null
    var downTimeMs = 0L

    fun animateShape(targetRadiusPx: Float, durationMs: Long, delayMs: Long = 0L) {
        val b = RectF(0f, 0f, width.coerceAtLeast(10).toFloat(), height.coerceAtLeast(10).toFloat())
        val curRadius = shapeAppearanceModel.topLeftCornerSize.getCornerSize(b)

        shapeAnimator?.cancel()
        shapeAnimator = ValueAnimator.ofFloat(curRadius, targetRadiusPx).apply {
            this.duration = durationMs
            this.startDelay = delayMs
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener { anim ->
                val size = anim.animatedValue as Float
                shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                    .setAllCornerSizes(size)
                    .build()
            }
            start()
        }
    }

    setOnTouchListener { v, event ->
        if (!v.isEnabled || !v.isClickable) {
            return@setOnTouchListener v.onTouchEvent(event)
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downTimeMs = System.currentTimeMillis()
                animateShape(pressedRadiusPx, pressDurationMs)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val elapsed = System.currentTimeMillis() - downTimeMs
                val delay = if (elapsed < minHoldTimeMs) minHoldTimeMs - elapsed else 0L
                animateShape(restRadiusPx, releaseDurationMs, delayMs = delay)
            }
        }
        v.onTouchEvent(event)
    }
}

private class RotatingDrawable(val base: Drawable) : Drawable() {
    var rotationDegrees: Float = 0f

    override fun draw(canvas: Canvas) {
        val b = bounds
        val px = b.exactCenterX()
        val py = b.exactCenterY()
        val count = canvas.save()
        canvas.rotate(rotationDegrees, px, py)
        base.bounds = b
        base.draw(canvas)
        canvas.restoreToCount(count)
    }

    override fun setAlpha(alpha: Int) {
        base.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        base.colorFilter = colorFilter
    }

    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = base.opacity

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        base.bounds = bounds
    }
}

/**
 * Easter Egg: Smoothly rotates ONLY the Drawable inside an ImageView 360 degrees.
 */
fun ImageView.spinImageDrawable360(durationMs: Long = 750L) {
    val current = drawable ?: return
    val rotating = if (current is RotatingDrawable) current else RotatingDrawable(current).also { setImageDrawable(it) }
    ValueAnimator.ofFloat(0f, 360f).apply {
        duration = durationMs
        interpolator = FastOutSlowInInterpolator()
        addUpdateListener { anim ->
            rotating.rotationDegrees = anim.animatedValue as Float
            invalidate()
        }
        start()
    }
}

/**
 * Easter Egg: Smoothly rotates ONLY the icon inside a MaterialButton 360 degrees.
 */
fun MaterialButton.spinIcon360(durationMs: Long = 750L) {
    val current = icon ?: return
    val rotating = if (current is RotatingDrawable) current else RotatingDrawable(current).also { icon = it }
    ValueAnimator.ofFloat(0f, 360f).apply {
        duration = durationMs
        interpolator = FastOutSlowInInterpolator()
        addUpdateListener { anim ->
            rotating.rotationDegrees = anim.animatedValue as Float
            invalidate()
        }
        start()
    }
}

/**
 * Easter Egg: Smoothly rotates/spins a View or icon 360 degrees in place on long-press.
 */
fun View.spinGear360(durationMs: Long = 750L) {
    when (this) {
        is MaterialButton -> spinIcon360(durationMs)
        is ImageView -> spinImageDrawable360(durationMs)
        is ViewGroup -> {
            val iconChild = findViewById<ImageView>(com.mikepenz.materialdrawer.R.id.material_drawer_icon)
                ?: findViewById<ImageView>(pl.szczodrzynski.navlib.R.id.material_drawer_icon)
            if (iconChild != null) {
                iconChild.spinImageDrawable360(durationMs)
            } else {
                pivotX = width / 2f
                pivotY = height / 2f
                animate().cancel()
                animate()
                    .rotationBy(360f)
                    .setDuration(durationMs)
                    .setInterpolator(FastOutSlowInInterpolator())
                    .start()
            }
        }
        else -> {
            pivotX = width / 2f
            pivotY = height / 2f
            animate().cancel()
            animate()
                .rotationBy(360f)
                .setDuration(durationMs)
                .setInterpolator(FastOutSlowInInterpolator())
                .start()
        }
    }
}

/**
 * Recursively enables Material 3 Expressive shape morphing on all MaterialButtons, FloatingActionButtons and MaterialCardViews
 * within a view or layout hierarchy.
 */
fun View.applyExpressiveShapeMorphToButtons() {
    when (this) {
        is MaterialButtonGroup -> {
            // Connected button groups manage their own MD3 connected shapes.
        }
        is MaterialButton -> if (isClickable && parent !is MaterialButtonGroup) enableExpressiveShapeMorph()
        is FloatingActionButton -> if (isClickable) enableExpressiveFabShapeMorph()
        is MaterialCardView -> {
            enableExpressiveCardShapeMorph()
            for (i in 0 until childCount) {
                getChildAt(i).applyExpressiveShapeMorphToButtons()
            }
        }
        is ViewGroup -> {
            for (i in 0 until childCount) {
                getChildAt(i).applyExpressiveShapeMorphToButtons()
            }
        }
    }
}

/**
 * Applies Material Design 3 Expressive shape morphing to a MaterialCardView on press/touch interactions.
 * Smoothly morphs corner sizes on press according to each corner's initial radius:
 * - Large corners (>= 16dp, e.g. 24dp/28dp) morph down to 12dp/14dp on press and return to initial size on release.
 * - Small corners (< 16dp, e.g. 4dp) morph down to 2dp on press and return to initial size on release.
 */
@SuppressLint("ClickableViewAccessibility")
fun MaterialCardView.enableExpressiveCardShapeMorph(
    isLocked: (() -> Boolean)? = null,
    pressDurationMs: Long = 250L,
    releaseDurationMs: Long = 250L,
    minHoldTimeMs: Long = 200L,
) {
    (getTag(R.id.expressive_card_shape_morph_animator) as? ValueAnimator)?.cancel()
    if (isLocked != null) {
        setTag(R.id.expressive_card_shape_morph_locked, isLocked)
    }

    val density = Resources.getSystem().displayMetrics.density

    var downTimeMs = 0L

    var isCornersCaptured = false
    var initTL = 0f
    var initTR = 0f
    var initBL = 0f
    var initBR = 0f

    var pressedTL = 0f
    var pressedTR = 0f
    var pressedBL = 0f
    var pressedBR = 0f

    fun captureInitialAndTargetCorners() {
        if (isCornersCaptured) return
        val bounds = RectF(0f, 0f, width.coerceAtLeast(10).toFloat(), height.coerceAtLeast(10).toFloat())
        initTL = shapeAppearanceModel.topLeftCornerSize.getCornerSize(bounds)
        initTR = shapeAppearanceModel.topRightCornerSize.getCornerSize(bounds)
        initBL = shapeAppearanceModel.bottomLeftCornerSize.getCornerSize(bounds)
        initBR = shapeAppearanceModel.bottomRightCornerSize.getCornerSize(bounds)

        val threshold = 12f * density

        fun calculatePressedPx(initPx: Float): Float {
            return when {
                initPx >= 28f * density -> 14f * density
                initPx >= 16f * density -> 12f * density
                initPx >= threshold -> 8f * density
                initPx > 0f -> 2f * density
                else -> 0f
            }
        }

        pressedTL = calculatePressedPx(initTL)
        pressedTR = calculatePressedPx(initTR)
        pressedBL = calculatePressedPx(initBL)
        pressedBR = calculatePressedPx(initBR)

        if (initTL > 0f || initTR > 0f || initBL > 0f || initBR > 0f || width > 0) {
            isCornersCaptured = true
        }
    }

    fun animateShape(progress: Float, durationMs: Long, delayMs: Long = 0L) {
        val currentBounds = RectF(0f, 0f, width.coerceAtLeast(10).toFloat(), height.coerceAtLeast(10).toFloat())
        val startTL = shapeAppearanceModel.topLeftCornerSize.getCornerSize(currentBounds)
        val startTR = shapeAppearanceModel.topRightCornerSize.getCornerSize(currentBounds)
        val startBL = shapeAppearanceModel.bottomLeftCornerSize.getCornerSize(currentBounds)
        val startBR = shapeAppearanceModel.bottomRightCornerSize.getCornerSize(currentBounds)

        val targetTL = initTL + (pressedTL - initTL) * progress
        val targetTR = initTR + (pressedTR - initTR) * progress
        val targetBL = initBL + (pressedBL - initBL) * progress
        val targetBR = initBR + (pressedBR - initBR) * progress

        (getTag(R.id.expressive_card_shape_morph_animator) as? ValueAnimator)?.cancel()

        ValueAnimator.ofFloat(0f, 1f).apply {
            setTag(R.id.expressive_card_shape_morph_animator, this)
            this.duration = durationMs
            this.startDelay = delayMs
            interpolator = FastOutSlowInInterpolator()

            addUpdateListener { anim ->
                if (this@enableExpressiveCardShapeMorph.isDragged) {
                    anim.cancel()
                    return@addUpdateListener
                }
                val fraction = anim.animatedValue as Float
                val tl = startTL + (targetTL - startTL) * fraction
                val tr = startTR + (targetTR - startTR) * fraction
                val bl = startBL + (targetBL - startBL) * fraction
                val br = startBR + (targetBR - startBR) * fraction

                shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                    .setTopLeftCornerSize(tl)
                    .setTopRightCornerSize(tr)
                    .setBottomLeftCornerSize(bl)
                    .setBottomRightCornerSize(br)
                    .build()
                invalidateOutline()
                invalidate()
            }
            start()
        }
    }

    setOnTouchListener { v, event ->
        val locked = (getTag(R.id.expressive_card_shape_morph_locked) as? () -> Boolean)?.invoke() == true
        if (locked || !v.isViewAndChildrenEnabled() || (!v.isClickable && !v.isFocusable)) {
            return@setOnTouchListener v.onTouchEvent(event)
        }
        val handled = v.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downTimeMs = System.currentTimeMillis()
                captureInitialAndTargetCorners()
                animateShape(progress = 1f, durationMs = pressDurationMs)
                v.dispatchExpressiveImageMorphToDescendants(event)
                return@setOnTouchListener true
            }
            MotionEvent.ACTION_UP -> {
                val elapsed = System.currentTimeMillis() - downTimeMs
                val delay = if (elapsed < minHoldTimeMs) minHoldTimeMs - elapsed else 0L
                animateShape(progress = 0f, durationMs = releaseDurationMs, delayMs = delay)
                v.dispatchExpressiveImageMorphToDescendants(event)
                if (!handled && v.isClickable) {
                    v.performClick()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                val elapsed = System.currentTimeMillis() - downTimeMs
                val delay = if (elapsed < minHoldTimeMs) minHoldTimeMs - elapsed else 0L
                animateShape(progress = 0f, durationMs = releaseDurationMs, delayMs = delay)
                v.dispatchExpressiveImageMorphToDescendants(event)
            }
        }
        handled
    }
}
