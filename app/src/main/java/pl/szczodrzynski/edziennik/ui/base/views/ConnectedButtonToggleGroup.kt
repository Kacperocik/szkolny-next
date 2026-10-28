package pl.szczodrzynski.edziennik.ui.base.views

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.google.android.material.R as MaterialR
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.shape.ShapeAppearanceModel
import pl.szczodrzynski.edziennik.R

class ConnectedButtonToggleGroup @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = MaterialR.attr.materialButtonToggleGroupStyle,
) : MaterialButtonToggleGroup(context, attrs, defStyleAttr) {

    var showCheckIcon: Boolean = true

    private var pressedButton: MaterialButton? = null
    private val buttonAnimators = mutableMapOf<MaterialButton, ValueAnimator>()

    init {
        attrs?.let {
            val a = context.obtainStyledAttributes(it, R.styleable.ConnectedButtonToggleGroup, defStyleAttr, 0)
            showCheckIcon = a.getBoolean(R.styleable.ConnectedButtonToggleGroup_showCheckIcon, true)
            a.recycle()
        }

        addOnButtonCheckedListener { _, _, _ ->
            updateAllButtons(animate = isAttachedToWindow)
        }
    }

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)
        if (child is MaterialButton) {
            configureButton(child)
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        for (child in children) {
            if (child is MaterialButton) {
                configureButton(child)
            }
        }
        updateAllButtons(animate = false)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAllButtons(animate = false)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        for (anim in buttonAnimators.values) {
            anim.cancel()
        }
        buttonAnimators.clear()
        pressedButton = null
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        applyConnectedShapes(animate = false)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        applyConnectedShapes(animate = false)
    }

    override fun check(id: Int) {
        super.check(id)
        updateAllButtons(animate = isAttachedToWindow)
    }

    override fun uncheck(id: Int) {
        super.uncheck(id)
        updateAllButtons(animate = isAttachedToWindow)
    }

    override fun clearChecked() {
        super.clearChecked()
        updateAllButtons(animate = isAttachedToWindow)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        var releasedButton: MaterialButton? = null
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val button = findChildButtonUnder(ev.x, ev.y)
                pressedButton = button
                setButtonPressedState(button, true)
            }
            MotionEvent.ACTION_MOVE -> {
                val button = findChildButtonUnder(ev.x, ev.y)
                if (button != pressedButton) {
                    setButtonPressedState(pressedButton, false)
                    pressedButton = button
                    setButtonPressedState(button, true)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                releasedButton = pressedButton
                pressedButton = null
            }
        }

        val result = super.dispatchTouchEvent(ev)

        if (releasedButton != null) {
            setButtonPressedState(releasedButton, false)
        }
        return result
    }

    private fun findChildButtonUnder(x: Float, y: Float): MaterialButton? {
        for (child in children) {
            if (child is MaterialButton && child.isVisible && child.isEnabled) {
                if (x >= child.left && x <= child.right && y >= child.top && y <= child.bottom) {
                    return child
                }
            }
        }
        return null
    }

    private fun configureButton(button: MaterialButton) {
        val density = resources.displayMetrics.density
        button.insetTop = 0
        button.insetBottom = 0
        button.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
        button.iconPadding = (4 * density).toInt()
        button.iconSize = (16 * density).toInt()
        button.strokeWidth = 0
        button.isOpticalCenterEnabled = false

        // Use compact padding so icon + text fit without being ellipsized/clipped
        button.setPadding(
            (6 * density).toInt(),
            (8 * density).toInt(),
            (6 * density).toInt(),
            (8 * density).toInt()
        )
    }

    fun updateAllButtons(animate: Boolean = false) {
        for (child in children) {
            if (child is MaterialButton) {
                updateChildButtonIcon(child, child.isChecked)
            }
        }
        applyConnectedShapes(animate = animate)
    }

    private fun updateChildButtonIcon(button: MaterialButton?, isChecked: Boolean) {
        if (button == null || !showCheckIcon) return
        if (button.tag == "no_check_icon") return
        if (button.text.isNullOrEmpty()) return

        if (isChecked) {
            button.icon = AppCompatResources.getDrawable(context, R.drawable.ic_m3_button_check)
        } else {
            button.icon = null
        }
    }

    private data class CornerSizes(val tl: Float, val tr: Float, val bl: Float, val br: Float)

    private fun getTargetCorners(
        button: MaterialButton,
        isPressed: Boolean,
        isChecked: Boolean,
        isFirst: Boolean,
        isLast: Boolean,
        isOnly: Boolean,
        isRtl: Boolean,
    ): CornerSizes {
        val density = resources.displayMetrics.density
        val innerCornerPx = 8f * density
        val buttonHeight = if (button.height > 0) button.height.toFloat() else 40f * density
        val pillRadiusPx = buttonHeight * 0.5f
        val pressedCornerPx = (innerCornerPx + pillRadiusPx) * 0.5f // Exactly halfway (~14dp)

        if (isPressed) {
            // Interactive expressive shape on touch/press (between inner 8dp and active 20dp pill)
            return CornerSizes(pressedCornerPx, pressedCornerPx, pressedCornerPx, pressedCornerPx)
        }

        if (isOnly || isChecked) {
            // Active / checked button: full rounded pill (50% on all 4 corners)
            return CornerSizes(pillRadiusPx, pillRadiusPx, pillRadiusPx, pillRadiusPx)
        }

        if (isFirst) {
            return if (!isRtl) {
                CornerSizes(tl = pillRadiusPx, tr = innerCornerPx, bl = pillRadiusPx, br = innerCornerPx)
            } else {
                CornerSizes(tl = innerCornerPx, tr = pillRadiusPx, bl = innerCornerPx, br = pillRadiusPx)
            }
        }

        if (isLast) {
            return if (!isRtl) {
                CornerSizes(tl = innerCornerPx, tr = pillRadiusPx, bl = innerCornerPx, br = pillRadiusPx)
            } else {
                CornerSizes(tl = pillRadiusPx, tr = innerCornerPx, bl = pillRadiusPx, br = innerCornerPx)
            }
        }

        // Middle button: 8dp on all corners
        return CornerSizes(innerCornerPx, innerCornerPx, innerCornerPx, innerCornerPx)
    }

    private fun setButtonPressedState(button: MaterialButton?, isPressed: Boolean) {
        if (button == null) return
        val visibleButtons = children.filterIsInstance<MaterialButton>().filter { it.isVisible }.toList()
        val index = visibleButtons.indexOf(button)
        if (index == -1) return

        val isFirst = index == 0
        val isLast = index == visibleButtons.size - 1
        val isOnly = visibleButtons.size == 1
        val isRtl = layoutDirection == View.LAYOUT_DIRECTION_RTL

        val targets = getTargetCorners(
            button = button,
            isPressed = isPressed,
            isChecked = button.isChecked,
            isFirst = isFirst,
            isLast = isLast,
            isOnly = isOnly,
            isRtl = isRtl
        )

        animateButtonToCorners(
            button = button,
            targetTL = targets.tl,
            targetTR = targets.tr,
            targetBL = targets.bl,
            targetBR = targets.br,
            durationMs = if (isPressed) 140L else 200L
        )
    }

    private fun animateButtonToCorners(
        button: MaterialButton,
        targetTL: Float,
        targetTR: Float,
        targetBL: Float,
        targetBR: Float,
        durationMs: Long,
    ) {
        buttonAnimators[button]?.cancel()

        val bounds = RectF(
            0f,
            0f,
            button.width.coerceAtLeast(1).toFloat(),
            button.height.coerceAtLeast(1).toFloat()
        )
        val curTL = button.shapeAppearanceModel.topLeftCornerSize.getCornerSize(bounds)
        val curTR = button.shapeAppearanceModel.topRightCornerSize.getCornerSize(bounds)
        val curBL = button.shapeAppearanceModel.bottomLeftCornerSize.getCornerSize(bounds)
        val curBR = button.shapeAppearanceModel.bottomRightCornerSize.getCornerSize(bounds)

        if (Math.abs(curTL - targetTL) < 0.5f &&
            Math.abs(curTR - targetTR) < 0.5f &&
            Math.abs(curBL - targetBL) < 0.5f &&
            Math.abs(curBR - targetBR) < 0.5f
        ) {
            val model = button.shapeAppearanceModel.toBuilder()
                .setTopLeftCornerSize(targetTL)
                .setTopRightCornerSize(targetTR)
                .setBottomLeftCornerSize(targetBL)
                .setBottomRightCornerSize(targetBR)
                .build()
            button.isOpticalCenterEnabled = false
            button.shapeAppearanceModel = model
            return
        }

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener { anim ->
                val f = anim.animatedValue as Float
                val tl = curTL + (targetTL - curTL) * f
                val tr = curTR + (targetTR - curTR) * f
                val bl = curBL + (targetBL - curBL) * f
                val br = curBR + (targetBR - curBR) * f

                val model = button.shapeAppearanceModel.toBuilder()
                    .setTopLeftCornerSize(tl)
                    .setTopRightCornerSize(tr)
                    .setBottomLeftCornerSize(bl)
                    .setBottomRightCornerSize(br)
                    .build()
                button.isOpticalCenterEnabled = false
                button.shapeAppearanceModel = model
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    buttonAnimators.remove(button)
                }
                override fun onAnimationCancel(animation: Animator) {
                    buttonAnimators.remove(button)
                }
            })
        }

        buttonAnimators[button] = animator
        animator.start()
    }

    /**
     * Enforces the Material Design 3 Expressive Connected Button Group shape rules:
     * - Checked (active) button: full pill (50% on all 4 corners)
     * - Unchecked first button: 50% outer corners, 8dp inner corners
     * - Unchecked middle buttons: 8dp on all corners
     * - Unchecked last button: 8dp inner corners, 50% outer corners
     * - Small 2dp gap between adjacent buttons
     */
    fun applyConnectedShapes(animate: Boolean = false) {
        val visibleButtons = children.filterIsInstance<MaterialButton>().filter { it.isVisible }.toList()
        if (visibleButtons.isEmpty()) return

        val density = resources.displayMetrics.density
        val spacingPx = (2 * density).toInt()
        val isRtl = layoutDirection == View.LAYOUT_DIRECTION_RTL

        for (i in visibleButtons.indices) {
            val button = visibleButtons[i]

            // Apply 2dp spacing between adjacent buttons
            val lp = button.layoutParams as? LinearLayout.LayoutParams
            if (lp != null) {
                val targetMarginStart = if (i == 0) 0 else spacingPx
                if (lp.marginStart != targetMarginStart) {
                    lp.marginStart = targetMarginStart
                    button.layoutParams = lp
                }
            }

            val isFirst = i == 0
            val isLast = i == visibleButtons.size - 1
            val isOnly = visibleButtons.size == 1
            val isPressed = button == pressedButton

            val targets = getTargetCorners(
                button = button,
                isPressed = isPressed,
                isChecked = button.isChecked,
                isFirst = isFirst,
                isLast = isLast,
                isOnly = isOnly,
                isRtl = isRtl
            )

            if (animate && isAttachedToWindow && button.width > 0) {
                animateButtonToCorners(
                    button = button,
                    targetTL = targets.tl,
                    targetTR = targets.tr,
                    targetBL = targets.bl,
                    targetBR = targets.br,
                    durationMs = 250L
                )
            } else {
                if (buttonAnimators[button]?.isRunning == true) {
                    continue
                }
                val model = button.shapeAppearanceModel.toBuilder()
                    .setTopLeftCornerSize(targets.tl)
                    .setTopRightCornerSize(targets.tr)
                    .setBottomLeftCornerSize(targets.bl)
                    .setBottomRightCornerSize(targets.br)
                    .build()
                button.isOpticalCenterEnabled = false
                button.shapeAppearanceModel = model
            }
        }
    }
}
