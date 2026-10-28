/*
 * Copyright (c) Kuba Szczodrzyński 2021-3-30.
 */

package pl.szczodrzynski.edziennik.ext

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.ColorStateList
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.os.Build
import android.util.TypedValue
import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.window.BackEvent
import android.window.OnBackAnimationCallback
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.app.Dialog
import androidx.appcompat.app.AlertDialog
import androidx.core.animation.doOnEnd
import androidx.core.graphics.ColorUtils
import androidx.core.view.ScrollingView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePaddingRelative
import androidx.core.widget.NestedScrollView
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import com.google.android.material.dialog.InsetDialogOnTouchListener
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.dialog.MaterialDialogs
import com.google.android.material.shape.MaterialShapeDrawable
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import pl.szczodrzynski.edziennik.MainActivity
import pl.szczodrzynski.edziennik.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val DIALOG_FREE_AREA_MARGIN_DP = 16
private const val DIALOG_LIMITED_AREA_MARGIN_DP = 8
/**
 * Below this height (e.g. a phone in landscape), dialogs may cover the app bars.
 */
private const val DIALOG_MIN_FREE_AREA_HEIGHT_DP = 480
/**
 * Changes of the dialog height smaller than this are ignored, so that it doesn't oscillate.
 */
private const val DIALOG_RESIZE_THRESHOLD_DP = 4

/**
 * @param isLimited whether there's so little space, that the area covers the app bars
 */
private class DialogArea(val rect: Rect, val isLimited: Boolean) {
    val margin
        get() = if (isLimited) DIALOG_LIMITED_AREA_MARGIN_DP.dp else DIALOG_FREE_AREA_MARGIN_DP.dp
}

/**
 * Expands dialogs with overflowing scrollable content only as much as needed,
 * within the screen area available below system and app bars.
 * Rechecks after layout changes to handle delayed content and rotation.
 * [contentOverflow] lets custom views report content they would otherwise shrink to fit.
 */
@SuppressLint("RestrictedApi")
@Suppress("DEPRECATION")
@JvmOverloads
fun AlertDialog.allowTallerHeight(contentOverflow: (() -> Int)? = null) {
    val window = window ?: return
    val decorView = window.decorView
    val activity = context.findActivity()
    // not the current pixel width - it would stay the same after rotating the screen
    val originalWidth = window.attributes.width
    val originalHeight = window.attributes.height
    val parentPanel = findViewById<View>(androidx.appcompat.R.id.parentPanel)
    val originalPanelHeight = parentPanel?.layoutParams?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT
    // the Material dialog background has large transparent vertical insets,
    // which are replaced with exact values when the dialog is grown
    var appliedBackground: InsetDrawable? = null
    var originalInsets = Rect()
    var insets: Rect? = null

    // Disable system window animations to let our Motion Physics system take full control
    window.setWindowAnimations(0)
    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

    // The dialog may need a few layout passes to settle its size - keep it
    // in expressive initial state until then, so that it doesn't jump right after opening.
    var isRevealed = false
    decorView.alpha = 0f
    decorView.scaleX = 0.88f
    decorView.scaleY = 0.88f
    decorView.translationY = 24.dp.toFloat()

    fun startExpressiveEnterAnimation() {
        if (isRevealed) return
        isRevealed = true

        val currentInsets = insets ?: originalInsets
        val visibleH = (decorView.height - currentInsets.top - currentInsets.bottom).coerceAtLeast(0)
        decorView.pivotX = decorView.width / 2f
        decorView.pivotY = currentInsets.top + (visibleH / 2f)

        val springForce = SpringForce(1f).apply {
            dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
            stiffness = 650f
        }
        val transForce = SpringForce(0f).apply {
            dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
            stiffness = 650f
        }
        val alphaForce = SpringForce(1f).apply {
            dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
            stiffness = 800f
        }

        SpringAnimation(decorView, DynamicAnimation.SCALE_X).setSpring(springForce).start()
        SpringAnimation(decorView, DynamicAnimation.SCALE_Y).setSpring(springForce).start()
        SpringAnimation(decorView, DynamicAnimation.TRANSLATION_Y).setSpring(transForce).start()
        SpringAnimation(decorView, DynamicAnimation.ALPHA).setSpring(alphaForce).start()
    }

    var lastImeVisible = false
    var normalTargetHeight: Int? = null
    var lastOrientation = activity?.resources?.configuration?.orientation
    // Safety net against resize loops - e.g. the system moving or resizing
    // the window again after every change. Stop adjusting for a moment then.
    var adjustCount = 0
    var adjustWindowStart = 0L
    var frozenUntil = 0L

    fun applySize(drawable: Drawable?, insets: Rect, visibleHeight: Int) {
        appliedBackground = InsetDrawable(drawable, insets.left, insets.top, insets.right, insets.bottom)
        window.setBackgroundDrawable(appliedBackground)
        decorView.setOnTouchListener(InsetDialogOnTouchListener(this, insets))
        findViewById<View>(androidx.appcompat.R.id.parentPanel)
            ?.layoutParams
            ?.height = ViewGroup.LayoutParams.MATCH_PARENT
        window.setLayout(originalWidth, visibleHeight + insets.top + insets.bottom)
    }

    /**
     * Returns true if the dialog size is changed (or not known yet).
     */
    fun adjustHeight(insetsOverride: WindowInsetsCompat? = null): Boolean {
        if (!decorView.isAttachedToWindow || decorView.height == 0)
            return true
        val now = android.os.SystemClock.uptimeMillis()
        val background = decorView.background as? InsetDrawable ?: return false
        if (background !== appliedBackground) {
            // not grown yet, or the background was replaced (e.g. overlayBackgroundColor())
            originalInsets = Rect().also { background.getPadding(it) }
            insets = null
        }

        val currentOrientation = activity?.resources?.configuration?.orientation
        if (currentOrientation != null && currentOrientation != lastOrientation) {
            lastOrientation = currentOrientation
            normalTargetHeight = null
            frozenUntil = 0L
            adjustCount = 0
            adjustWindowStart = now
            // Explicit window height and MATCH_PARENT panels belong to the old
            // orientation. Let Android measure the natural content height again
            // before considering overflow or growing the dialog in the new one.
            if (parentPanel is ViewGroup)
                TransitionManager.endTransitions(parentPanel)
            appliedBackground = InsetDrawable(
                background.drawable,
                originalInsets.left, originalInsets.top, originalInsets.right, originalInsets.bottom,
            )
            window.setBackgroundDrawable(appliedBackground)
            decorView.setOnTouchListener(InsetDialogOnTouchListener(this, originalInsets))
            insets = null
            parentPanel?.updateLayoutParams { height = originalPanelHeight }
            window.setLayout(originalWidth, originalHeight)
            decorView.requestLayout()
            return true
        }
        if (decorView.isLayoutRequested)
            return true
        if (now < frozenUntil) {
            decorView.postDelayed({
                if (isRevealed && decorView.isAttachedToWindow) adjustHeight()
            }, (frozenUntil - now).coerceAtLeast(10L))
            return false
        }
        // The keyboard shrinks the area the dialog window is centered in.
        val rootInsets = insetsOverride ?: ViewCompat.getRootWindowInsets(decorView)
        val imeInsets = rootInsets?.getInsets(WindowInsetsCompat.Type.ime())
        val imeHeight = imeInsets?.bottom ?: 0
        val imeVisible = (rootInsets?.isVisible(WindowInsetsCompat.Type.ime()) == true) && imeHeight > 0
        val imeChanged = imeVisible != lastImeVisible
        lastImeVisible = imeVisible

        if (imeChanged) {
            frozenUntil = 0L
            adjustCount = 0
            if (!imeVisible) {
                // Post retries to align with keyboard exit animation
                decorView.postDelayed({
                    if (isRevealed && decorView.isAttachedToWindow) adjustHeight()
                }, 150L)
                decorView.postDelayed({
                    if (isRevealed && decorView.isAttachedToWindow) adjustHeight()
                }, 300L)
            }
        }

        var overflow = max(decorView.getScrollableOverflow() ?: 0, contentOverflow?.invoke() ?: 0)
        val currentInsets = insets ?: originalInsets
        val visibleHeight = decorView.height - currentInsets.top - currentInsets.bottom
        if (insets != null && overflow <= 0) {
            // Already grown - check if the content needs less space now (e.g. the
            // buttons were hidden). The extra space is given to the weighted panels,
            // so measure the content height without it.
            findViewById<View>(androidx.appcompat.R.id.parentPanel)?.let { panel ->
                panel.measure(
                    View.MeasureSpec.makeMeasureSpec(panel.width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(panel.height, View.MeasureSpec.AT_MOST),
                )
                val slack = panel.height - panel.measuredHeight
                // a small threshold, so that growing and shrinking can't alternate
                if (slack > DIALOG_RESIZE_THRESHOLD_DP.dp)
                    overflow = -slack
            }
        }
        if (insets != null && overflow in 1..DIALOG_RESIZE_THRESHOLD_DP.dp && !imeChanged)
            overflow = 0

        // The area the window is actually centered in by the system (without the
        // system bars and the keyboard). It doesn't depend on the window height,
        // unlike the window position - so the result doesn't change after resizing.
        val windowFrame = Rect().also { decorView.getWindowVisibleDisplayFrame(it) }
        if (windowFrame.height() <= 0)
            return false

        val dialogArea = activity?.getDialogArea() ?: return false
        val freeArea = Rect(dialogArea.rect)

        val screenHeight = activity.window?.decorView?.height?.takeIf { it > 0 }
            ?: decorView.resources.displayMetrics.heightPixels
        val imeTopOnScreen = screenHeight - imeHeight

        if (imeVisible) {
            freeArea.bottom = min(freeArea.bottom, imeTopOnScreen)
            freeArea.top = max(freeArea.top, windowFrame.top)
        }

        val margin = if (imeVisible) DIALOG_LIMITED_AREA_MARGIN_DP.dp else dialogArea.margin
        val maxHeight = freeArea.height() - 2 * margin
        if (maxHeight <= 0)
            return false

        // not grown yet - only when the content needs it, or doesn't fit the free area, or keyboard is visible
        if (insets == null && overflow <= 0 && visibleHeight <= maxHeight && !imeVisible)
            return false

        val desiredHeight = if (insets != null && !imeVisible && (overflow >= 0 || imeChanged)) {
            max(normalTargetHeight ?: 0, visibleHeight + overflow)
        } else {
            visibleHeight + overflow
        }
        val targetHeight = desiredHeight.coerceAtMost(maxHeight)
        if (!imeVisible && (insets != null || overflow > 0)) {
            normalTargetHeight = targetHeight
        }

        val effectiveWindowBottom = if (imeVisible) min(windowFrame.bottom, imeTopOnScreen) else windowFrame.bottom
        val effectiveWindowCenterY = (windowFrame.top + effectiveWindowBottom) / 2
        val offset = freeArea.centerY() - effectiveWindowCenterY

        val newInsets = Rect(
            originalInsets.left,
            (2 * offset).coerceAtLeast(0),
            originalInsets.right,
            (-2 * offset).coerceAtLeast(0),
        )

        // the window must fit its frame, otherwise the system moves it
        val availableWindowHeight = effectiveWindowBottom - windowFrame.top
        val excess = targetHeight + newInsets.top + newInsets.bottom - availableWindowHeight
        if (excess > 0) {
            newInsets.top = (newInsets.top - excess).coerceAtLeast(0)
            newInsets.bottom = (newInsets.bottom - excess).coerceAtLeast(0)
        }
        val windowHeight = targetHeight + newInsets.top + newInsets.bottom
        if (newInsets == insets && abs(windowHeight - decorView.height) <= 1)
            return false

        if (now - adjustWindowStart > 1000L) {
            adjustWindowStart = now
            adjustCount = 0
        }
        if (++adjustCount > 15) {
            frozenUntil = now + 500L
            adjustCount = 0
            return false
        }

        insets = newInsets
        val drawable = background.drawable
        if (!isRevealed) {
            applySize(drawable, newInsets, targetHeight)
            return true
        }

        findViewById<View>(androidx.appcompat.R.id.parentPanel)?.let { panel ->
            if (panel is ViewGroup) {
                TransitionManager.beginDelayedTransition(panel, ChangeBounds().apply {
                    duration = 200L
                    interpolator = FastOutSlowInInterpolator()
                })
            }
        }
        applySize(drawable, newInsets, targetHeight)
        return false
    }

    var preDrawPasses = 0
    val preDrawListener = object : ViewTreeObserver.OnPreDrawListener {
        override fun onPreDraw(): Boolean {
            if (isRevealed) {
                if (decorView.viewTreeObserver.isAlive) {
                    decorView.viewTreeObserver.removeOnPreDrawListener(this)
                }
                return true
            }
            if (preDrawPasses++ < 3) {
                val sizeChanged = adjustHeight()
                if (sizeChanged) {
                    return false
                }
            }
            if (decorView.viewTreeObserver.isAlive) {
                decorView.viewTreeObserver.removeOnPreDrawListener(this)
            }
            startExpressiveEnterAnimation()
            return true
        }
    }
    decorView.viewTreeObserver.addOnPreDrawListener(preDrawListener)

    decorView.postDelayed({
        startExpressiveEnterAnimation()
    }, 350L)

    val globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        if (isRevealed) {
            adjustHeight()
        }
    }
    decorView.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)

    ViewCompat.setOnApplyWindowInsetsListener(decorView) { _, insetsCompat ->
        if (isRevealed) {
            decorView.post {
                if (isRevealed && decorView.isAttachedToWindow) {
                    adjustHeight(insetsCompat)
                }
            }
        }
        insetsCompat
    }

    ViewCompat.setWindowInsetsAnimationCallback(decorView, object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {
        override fun onEnd(animation: WindowInsetsAnimationCompat) {
            super.onEnd(animation)
            if (isRevealed && decorView.isAttachedToWindow) {
                decorView.post {
                    if (isRevealed && decorView.isAttachedToWindow) {
                        adjustHeight()
                    }
                }
            }
        }

        override fun onProgress(
            insets: WindowInsetsCompat,
            runningAnimations: MutableList<WindowInsetsAnimationCompat>
        ): WindowInsetsCompat {
            return insets
        }
    })

    decorView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {}
        override fun onViewDetachedFromWindow(v: View) {
            if (decorView.viewTreeObserver.isAlive) {
                decorView.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
                decorView.viewTreeObserver.removeOnGlobalLayoutListener(globalLayoutListener)
            }
        }
    })
}

/**
 * Returns how many pixels the scrollable views in this view need to show
 * their whole content, or null if there are no scrollable views.
 */
private fun View.getScrollableOverflow(): Int? {
    if (!isShown)
        return null
    val overflow = when (this) {
        is NestedScrollView -> getChildAt(0)?.let { it.height + paddingTop + paddingBottom - height }
        is ScrollingView -> computeVerticalScrollRange() - computeVerticalScrollExtent()
        is ScrollView -> getChildAt(0)?.let { it.height + paddingTop + paddingBottom - height }
        // the content height is unknown - grow as much as possible if needed
        is ListView -> if (canScrollVertically(1) || canScrollVertically(-1)) Int.MAX_VALUE / 4 else 0
        else -> null
    }
    if (overflow != null)
        return overflow.coerceAtLeast(0)
    if (this !is ViewGroup)
        return null
    return (0 until childCount).mapNotNull { getChildAt(it).getScrollableOverflow() }.maxOrNull()
}

private fun Context.findActivity(): Activity? {
    var context: Context? = this
    while (context is ContextWrapper) {
        if (context is Activity)
            return context
        context = context.baseContext
    }
    return null
}

/**
 * Screen area not covered by the visible system bars, nor by the app bars
 * of [MainActivity] (at the top or the bottom) - unless the remaining
 * space is too small, then the app bars may be covered.
 */
private fun Activity.getDialogArea(): DialogArea? {
    val root = window?.decorView ?: return null
    if (root.height == 0)
        return null
    val location = IntArray(2)
    root.getLocationOnScreen(location)
    val systemArea = Rect(location[0], location[1], location[0] + root.width, location[1] + root.height)
    ViewCompat.getRootWindowInsets(root)
        ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        ?.let {
            systemArea.top += it.top
            systemArea.bottom -= it.bottom
        }
    val appArea = Rect(systemArea)
    if (this is MainActivity) {
        for (bar in listOf(navView.toolbar, navView.bottomBar)) {
            if (!bar.isShown || bar.height == 0)
                continue
            bar.getLocationOnScreen(location)
            val barTop = location[1]
            val barBottom = barTop + bar.height
            if ((barTop + barBottom) / 2 < systemArea.centerY())
                appArea.top = max(appArea.top, barBottom)
            else
                appArea.bottom = min(appArea.bottom, barTop)
        }
    }
    if (appArea.height() >= DIALOG_MIN_FREE_AREA_HEIGHT_DP.dp)
        return DialogArea(appArea, isLimited = false)
    return DialogArea(systemArea, isLimited = true)
}

/**
 * Whether the screen space for dialogs is very limited (e.g. a phone in landscape).
 */
fun Context.isDialogSpaceLimited() = findActivity()?.getDialogArea()?.isLimited == true

/** AppCompat's DialogTitle shrinks overflowing text, but never restores its original size. */
fun AlertDialog.restoreTitleSizeOnConfigurationChanges() {
    val title = findViewById<TextView>(androidx.appcompat.R.id.alertTitle) ?: return
    val originalSize = title.textSize
    var orientation = context.resources.configuration.orientation
    window?.decorView?.viewTreeObserver?.addOnGlobalLayoutListener {
        val newOrientation = context.resources.configuration.orientation
        if (newOrientation != orientation) {
            orientation = newOrientation
            title.setTextSize(TypedValue.COMPLEX_UNIT_PX, originalSize)
        }
    }
}

/**
 * The title row of a dialog, if the dialog has a title. Dialogs without a title
 * usually have their own header in the content.
 */
private fun AlertDialog.getVisibleTitleTemplate(): LinearLayout? {
    if (findViewById<View>(androidx.appcompat.R.id.topPanel)?.isVisible != true)
        return null
    val titleTemplate = findViewById<LinearLayout>(androidx.appcompat.R.id.title_template) ?: return null
    // leave the space for the buttons added next to the title
    findViewById<View>(androidx.appcompat.R.id.alertTitle)?.updateLayoutParams<LinearLayout.LayoutParams> {
        width = 0
        weight = 1f
    }
    return titleTemplate
}

/** Restore the standard message inset after moving or hiding the bottom actions. */
private fun AlertDialog.updateMessageBottomSpacing() {
    val hasBottomButtons = findViewById<View>(androidx.appcompat.R.id.buttonPanel)?.isVisible == true
    findViewById<View>(androidx.appcompat.R.id.textSpacerNoButtons)?.isVisible = !hasBottomButtons
}

private fun AlertDialog.createIconButton(
    icon: IIcon,
    description: CharSequence,
    isFilled: Boolean,
    onClick: () -> Unit,
) = MaterialButton(
    context,
    null,
    if (isFilled) R.attr.materialIconButtonFilledTonalStyle else R.attr.materialIconButtonStyle,
).apply {
    this.icon = icon.toDrawable(
        context,
        colorAttr = if (isFilled) R.attr.colorOnSecondaryContainer else R.attr.colorOnSurfaceVariant,
    )
    iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
    iconPadding = 0
    if (isFilled) {
        insetTop = 0
        insetBottom = 0
    }
    contentDescription = description
    isVisible = false
    onClick { onClick() }
}

/**
 * When the screen space is very limited, hides the bottom buttons and shows
 * a close (X) button in the title instead. Updated on configuration changes.
 * For dialogs, whose positive button closes them.
 *
 * @param neutralIcon if not null, the neutral button (e.g. "Edit") is shown as
 * an icon next to the X too; otherwise it's kept at the bottom
 * @param shouldShowIcon optional visibility policy, reevaluated after layout changes
 * @param inlineIcons place overlay icons in one row, with the close icon at the end
 */
@JvmOverloads
fun AlertDialog.useCloseIconWhenSpaceLimited(
    onVisibilityChanged: ((isVisible: Boolean) -> Unit)? = null,
    neutralIcon: IIcon? = null,
    shouldShowIcon: (() -> Boolean)? = null,
    inlineIcons: Boolean = false,
) {
    val window = window ?: return
    val buttonPanel = findViewById<View>(androidx.appcompat.R.id.buttonPanel)
    val positiveButton = getButton(AlertDialog.BUTTON_POSITIVE)
    val neutralButton = getButton(AlertDialog.BUTTON_NEUTRAL)?.takeIf { it.isVisible }
    val negativeButton = getButton(AlertDialog.BUTTON_NEGATIVE)?.takeIf { it.isVisible }
    val titleTemplate = getVisibleTitleTemplate()
    // a filled button is readable over the content scrolled under it
    val isFilled = titleTemplate == null

    val closeButton = createIconButton(CommunityMaterial.Icon.cmd_close, context.getString(R.string.close), isFilled) {
        dismiss()
    }
    val neutralIconButton = if (neutralIcon != null && neutralButton != null)
        createIconButton(neutralIcon, neutralButton.text, isFilled) { neutralButton.performClick() }
    else
        null
    val iconButtons = listOfNotNull(neutralIconButton, closeButton)
    // buttons, which are still shown at the bottom
    val hasBottomButtons = negativeButton != null || (neutralButton != null && neutralIconButton == null)

    var paddingEnd = 0
    var iconsPaddingEnd = 0
    if (titleTemplate != null) {
        for (button in iconButtons) {
            titleTemplate.addView(button, LinearLayout.LayoutParams(48.dp, 48.dp).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginStart = 8.dp
            })
        }
        // Align the icon with the dialog content, instead of the button bounds.
        // Not using a negative margin, as the title panel clips its padding area.
        paddingEnd = titleTemplate.paddingEnd
        iconsPaddingEnd = (paddingEnd - 12.dp).coerceAtLeast(0)
    } else {
        // Keep the close icon nearest the top-end corner of the dialog card.
        val content = window.decorView.findViewById<ViewGroup>(android.R.id.content) as? FrameLayout ?: return
        iconButtons.reversed().forEachIndexed { index, button ->
            content.addView(button, FrameLayout.LayoutParams(40.dp, 40.dp).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = 12.dp + if (inlineIcons) 0 else index * (40 + 8).dp
                marginEnd = 12.dp + if (inlineIcons) index * (40 + 8).dp else 0
            })
        }
    }

    val update = {
        val isLimited = shouldShowIcon?.invoke() ?: context.isDialogSpaceLimited()
        if (closeButton.isVisible != isLimited) {
            iconButtons.forEach { it.isVisible = isLimited }
            if (hasBottomButtons) {
                positiveButton?.isVisible = !isLimited
                if (neutralIconButton != null)
                    neutralButton?.isVisible = !isLimited
            } else {
                buttonPanel?.isVisible = !isLimited
            }
            titleTemplate?.updatePaddingRelative(end = if (isLimited) iconsPaddingEnd else paddingEnd)
            updateMessageBottomSpacing()
            onVisibilityChanged?.invoke(isLimited)
        }
    }
    update()
    window.decorView.viewTreeObserver.addOnGlobalLayoutListener { update() }
}

/**
 * When the screen space is very limited, moves the buttons (e.g. "Cancel" and "OK")
 * to the end of the title row, instead of a separate row at the bottom.
 * Updated on configuration changes. Does nothing for dialogs without a title.
 */
@JvmOverloads
fun AlertDialog.moveButtonsToTitleWhenSpaceLimited(
    neutralIcon: IIcon? = null,
    destructiveNeutralIcon: Boolean = false,
) {
    val window = window ?: return
    val buttonPanel = findViewById<View>(androidx.appcompat.R.id.buttonPanel) ?: return
    val buttons = listOf(AlertDialog.BUTTON_NEUTRAL, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_POSITIVE)
        .mapNotNull { getButton(it) }
        .filter { it.isVisible && it.parent is ViewGroup }
    if (buttons.isEmpty())
        return
    val titleTemplate = getVisibleTitleTemplate() ?: return

    val neutralButton = getButton(AlertDialog.BUTTON_NEUTRAL)?.takeIf { it in buttons }
    val neutralIconButton = if (neutralIcon != null && neutralButton != null)
        createIconButton(neutralIcon, neutralButton.text, isFilled = true) { neutralButton.performClick() }.apply {
            if (destructiveNeutralIcon) {
                backgroundTintList = ColorStateList.valueOf(MaterialColors.getColor(this, R.attr.colorError))
                iconTint = ColorStateList.valueOf(MaterialColors.getColor(this, R.attr.colorOnError))
            }
            enableExpressiveShapeMorph()
            titleTemplate.addView(this, LinearLayout.LayoutParams(40.dp, 40.dp).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginStart = 8.dp
            })
        }
    else
        null
    val movedButtons = buttons.filterNot { it == neutralButton && neutralIconButton != null }

    class Origin(val parent: ViewGroup, val index: Int, val params: ViewGroup.LayoutParams)
    val origins = buttons.associateWith {
        val parent = it.parent as ViewGroup
        Origin(parent, parent.indexOfChild(it), it.layoutParams)
    }
    // align the text button with the dialog content, like at the bottom
    val paddingEnd = titleTemplate.paddingEnd
    val buttonsPaddingEnd = (paddingEnd - 12.dp).coerceAtLeast(0)
    var isMoved = false

    val update = {
        val isLimited = context.isDialogSpaceLimited()
        if (isMoved != isLimited) {
            isMoved = isLimited
            if (isLimited) {
                for (button in movedButtons) {
                    origins.getValue(button).parent.removeView(button)
                    titleTemplate.addView(button, LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        gravity = Gravity.CENTER_VERTICAL
                        marginStart = 8.dp
                    })
                }
            } else {
                for (button in movedButtons) {
                    val origin = origins.getValue(button)
                    titleTemplate.removeView(button)
                    origin.parent.addView(button, origin.index.coerceAtMost(origin.parent.childCount), origin.params)
                }
            }
            neutralIconButton?.isVisible = isLimited
            buttonPanel.isVisible = !isLimited
            titleTemplate.updatePaddingRelative(end = if (isLimited) buttonsPaddingEnd else paddingEnd)
            updateMessageBottomSpacing()
        }
    }
    update()
    window.decorView.viewTreeObserver.addOnGlobalLayoutListener { update() }
}

/** Shows the dialog and enables [AlertDialog.allowTallerHeight]. */
fun MaterialAlertDialogBuilder.showTaller(): AlertDialog {
    return show().also { it.allowTallerHeight() }
}


@SuppressLint("RestrictedApi")
fun AlertDialog.overlayBackgroundColor(color: Int, alpha: Int) {
    // this is absolutely horrible
    val colorSurface16dp = ColorUtils.compositeColors(
        R.color.colorSurface_16dp.resolveColor(context),
        MaterialColors.getColor(
            context,
            R.attr.colorSurface,
            javaClass.canonicalName,
        )
    )
    val colorDialogBackground = MaterialColors.layer(colorSurface16dp, color, alpha / 255f)
    val backgroundInsets = MaterialDialogs.getDialogBackgroundInsets(
        context,
        R.attr.alertDialogStyle,
        R.style.MaterialAlertDialog_Material3,
    )
    val background = MaterialShapeDrawable(
        context,
        null,
        R.attr.alertDialogStyle,
        R.style.MaterialAlertDialog_Material3
    )
    with(background) {
        initializeElevationOverlay(context)
        fillColor = ColorStateList.valueOf(colorDialogBackground)
        /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            setCornerSize(android.R.attr.dialogCornerRadius.resolveDimenAttr(context))
        }*/
        elevation = ViewCompat.getElevation(window?.decorView ?: return@with)
    }
    val insetDrawable = MaterialDialogs.insetDrawable(background, backgroundInsets)
    window?.setBackgroundDrawable(insetDrawable)
}

/**
 * Enables predictive back gesture animation on Android 14+ (API 34+) and
 * registers an [OnBackInvokedCallback] on Android 13+ (API 33+).
 */
fun Dialog.setupPredictiveBackAnimation(onDismiss: (() -> Unit)? = null) {
    val window = window ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val decorView = window.decorView
        val callback = object : OnBackAnimationCallback {
            override fun onBackStarted(backEvent: BackEvent) {
                decorView.pivotX = if (backEvent.swipeEdge == BackEvent.EDGE_LEFT) 0f else decorView.width.toFloat()
                decorView.pivotY = decorView.height / 2f
            }

            override fun onBackProgressed(backEvent: BackEvent) {
                val progress = backEvent.progress
                val scale = 1f - (progress * 0.12f)
                decorView.scaleX = scale
                decorView.scaleY = scale
                decorView.alpha = 1f - (progress * 0.3f)
            }

            override fun onBackInvoked() {
                if (onDismiss != null) onDismiss() else dismiss()
            }

            override fun onBackCancelled() {
                decorView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(200L)
                    .start()
            }
        }
        window.onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT,
            callback
        )
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val callback = OnBackInvokedCallback {
            if (onDismiss != null) onDismiss() else dismiss()
        }
        window.onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT,
            callback
        )
    }
}

/**
 * Dismisses a dialog with a smooth scale and fade-out animation.
 */
fun Dialog.dismissWithAnimation(onDismissed: (() -> Unit)? = null) {
    val window = window
    val decor = window?.decorView
    if (decor == null || !isShowing) {
        dismiss()
        onDismissed?.invoke()
        return
    }

    decor.animate()
        .scaleX(0.90f)
        .scaleY(0.90f)
        .alpha(0f)
        .setDuration(150L)
        .setInterpolator(FastOutSlowInInterpolator())
        .withEndAction {
            try {
                dismiss()
            } catch (ignored: Throwable) {}
            onDismissed?.invoke()
        }
        .start()
}
