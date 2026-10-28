package pl.szczodrzynski.navlib

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.Outline
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.widget.ImageView
import androidx.annotation.AttrRes
import androidx.core.content.ContextCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.elevation.ElevationOverlayProvider
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.MaterialShapeDrawable
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.materialdrawer.holder.StringHolder
import com.mikepenz.materialdrawer.model.interfaces.Badgeable
import com.mikepenz.materialdrawer.model.interfaces.withBadge
import com.mikepenz.materialdrawer.util.getDrawerItem
import com.mikepenz.materialdrawer.util.updateItem
import com.mikepenz.materialdrawer.view.BezelImageView
import com.mikepenz.materialdrawer.widget.MaterialDrawerSliderView
import java.util.WeakHashMap

fun blendColors(background: Int, foreground: Int): Int {
    val r1 = (background shr 16 and 0xff)
    val g1 = (background shr 8 and 0xff)
    val b1 = (background and 0xff)

    val r2 = (foreground shr 16 and 0xff)
    val g2 = (foreground shr 8 and 0xff)
    val b2 = (foreground and 0xff)
    val a2 = (foreground shr 24 and 0xff)

    val factor = a2.toFloat() / 255f
    val red = (r1 * (1 - factor) + r2 * factor)
    val green = (g1 * (1 - factor) + g2 * factor)
    val blue = (b1 * (1 - factor) + b2 * factor)

    return (0xff000000 or (red.toLong() shl 16) or (green.toLong() shl 8) or (blue.toLong())).toInt()
}

fun elevateSurface(context: Context, dp: Int): Int {
    ElevationOverlayProvider(context).apply {
        return compositeOverlay(themeSurfaceColor, dp * context.resources.displayMetrics.density)
    }
}

fun IconicsDrawable.colorAttr(context: Context, @AttrRes attrRes: Int) {
    colorInt = getColorFromAttr(context, attrRes)
}

fun getColorFromAttr(context: Context, @AttrRes color: Int): Int {
    val typedValue = TypedValue()
    context.theme.resolveAttribute(color, typedValue, true)
    if (typedValue.resourceId != 0) {
        return ContextCompat.getColor(context, typedValue.resourceId)
    }
    return typedValue.data
}

fun Drawable.setBadgeCount(count: Int) {
    if (this is LayerDrawable) {
        (this as LayerDrawable?)?.apply {
            findDrawableByLayerId(R.id.ic_badge)
                .takeIf { it is BadgeDrawable }
                ?.also { badge ->
                    (badge as BadgeDrawable).setCount(count.toString())
                    mutate()
                    setDrawableByLayerId(R.id.ic_badge, badge)
                }
        }
    }
}

fun MaterialDrawerSliderView.updateBadge(identifier: Long, badge: StringHolder?) {
    val drawerItem = getDrawerItem(identifier)
    if (drawerItem is Badgeable) {
        drawerItem.withBadge(badge)
        updateItem(drawerItem)
    }
}

private val shapeMorphStates = WeakHashMap<View, ShapeMorphState>()

private class ShapeMorphState(var currentRadiusPx: Float) {
    var shapeAnimator: ValueAnimator? = null
    var downTimeMs = 0L
    var shapes: List<Drawable> = emptyList()
    var onRadiusChanged: ((Float) -> Unit)? = null
    var isPressedMorph = false

    fun animateShape(
        view: View,
        targetSizePx: Float,
        durationMs: Long,
        delayMs: Long = 0L,
    ) {
        shapeAnimator?.cancel()
        shapeAnimator = ValueAnimator.ofFloat(currentRadiusPx, targetSizePx).apply {
            this.duration = durationMs
            this.startDelay = delayMs
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener { anim ->
                currentRadiusPx = anim.animatedValue as Float
                val listener = onRadiusChanged
                if (listener != null) {
                    listener(currentRadiusPx)
                } else {
                    applyCornerRadius(shapes, currentRadiusPx)
                    view.invalidate()
                }
            }
            start()
        }
    }
}

/**
 * Morphs the MaterialDrawer item indicator (selected fill + ripple mask), not the whole row.
 * clipToOutline cannot change that inset MaterialShapeDrawable, so it is not used here.
 */
fun View.handleExpressiveTouch(
    event: MotionEvent,
    restCornerRadiusDp: Float = 28f,
    pressedCornerRadiusDp: Float = 12f,
    pressDurationMs: Long = 250L,
    releaseDurationMs: Long = 250L,
    minHoldTimeMs: Long = 200L
) {
    val density = Resources.getSystem().displayMetrics.density
    val restSizePx = restCornerRadiusDp * density
    val pressedSizePx = pressedCornerRadiusDp * density

    var state = shapeMorphStates[this]
    if (state == null) {
        state = ShapeMorphState(restSizePx)
        shapeMorphStates[this] = state
    }

    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            state.shapes = collectCornerDrawables()
            if (state.shapes.isEmpty()) return
            state.currentRadiusPx = firstCornerRadius(state.shapes)?.takeIf { it > 0f } ?: restSizePx
            state.downTimeMs = System.currentTimeMillis()
            state.animateShape(this, pressedSizePx, pressDurationMs)
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            if (state.shapes.isEmpty()) return
            val elapsed = System.currentTimeMillis() - state.downTimeMs
            val delay = if (elapsed < minHoldTimeMs) minHoldTimeMs - elapsed else 0L
            state.animateShape(this, restSizePx, releaseDurationMs, delayMs = delay)
        }
    }
}

private fun View.collectCornerDrawables(): List<Drawable> {
    val result = LinkedHashSet<Drawable>()
    collectCornerDrawables(background, result)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        collectCornerDrawables(foreground, result)
    }
    result.forEach { it.mutate() }
    return result.toList()
}

private fun collectCornerDrawables(drawable: Drawable?, into: MutableSet<Drawable>) {
    when (drawable) {
        null -> return
        is MaterialShapeDrawable, is GradientDrawable -> into.add(drawable)
        is InsetDrawable -> collectCornerDrawables(drawable.drawable, into)
        is RippleDrawable -> {
            for (i in 0 until drawable.numberOfLayers) {
                collectCornerDrawables(drawable.getDrawable(i), into)
            }
        }
        is StateListDrawable -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                for (i in 0 until drawable.stateCount) {
                    collectCornerDrawables(drawable.getStateDrawable(i), into)
                }
            } else {
                collectCornerDrawables(drawable.current, into)
            }
        }
        is LayerDrawable -> {
            for (i in 0 until drawable.numberOfLayers) {
                collectCornerDrawables(drawable.getDrawable(i), into)
            }
        }
    }
}

private fun firstCornerRadius(shapes: List<Drawable>): Float? {
    val shape = shapes.firstOrNull() ?: return null
    return when (shape) {
        is MaterialShapeDrawable -> shape.topLeftCornerResolvedSize
        else -> null
    }
}

private fun applyCornerRadius(shapes: List<Drawable>, radiusPx: Float) {
    shapes.forEach { drawable ->
        when (drawable) {
            is MaterialShapeDrawable -> drawable.setCornerSize(radiusPx)
            is GradientDrawable -> drawable.cornerRadius = radiusPx
        }
        drawable.invalidateSelf()
    }
}

private val imageMorphStates = WeakHashMap<View, ShapeMorphState>()

private fun BezelImageView.maskDrawable(): Drawable? {
    return runCatching {
        BezelImageView::class.java.getDeclaredField("mMaskDrawable").apply {
            isAccessible = true
        }.get(this) as? Drawable
    }.getOrNull()
}

private fun View.circleRadiusPx(): Float {
    val size = minOf(width, height)
    return if (size > 0) size / 2f else {
        val w = layoutParams?.width ?: 0
        val h = layoutParams?.height ?: 0
        val s = minOf(w, h)
        if (s > 0) s / 2f else 20f * resources.displayMetrics.density
    }
}

private fun View.applyImageCornerRadius(radiusPx: Float) {
    val r = radiusPx.coerceAtLeast(0f)
    when (this) {
        is ShapeableImageView -> {
            shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                .setAllCornerSizes(r)
                .build()
        }
        is BezelImageView -> {
            clipToOutline = false
            val mask = maskDrawable()?.mutate()
            if (mask is GradientDrawable) {
                mask.cornerRadius = r
            }
            invalidate()
        }
        else -> {
            val shapes = LinkedHashSet<Drawable>()
            collectCornerDrawables(background, shapes)
            shapes.forEach { it.mutate() }
            shapes.forEach { drawable ->
                if (drawable is GradientDrawable) {
                    // Ovals ignore cornerRadius; a filled round-rect can morph into a squircle.
                    drawable.shape = GradientDrawable.RECTANGLE
                }
            }
            if (shapes.isNotEmpty()) {
                applyCornerRadius(shapes.toList(), r)
            }

            if (outlineProvider == null || outlineProvider == ViewOutlineProvider.BACKGROUND) {
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        val currentR = imageMorphStates[view]?.currentRadiusPx
                        val corner = if (currentR != null && currentR >= 0f) currentR else if (view.width > 0 && view.height > 0) minOf(view.width, view.height) / 2f else view.circleRadiusPx()
                        outline.setRoundRect(0, 0, view.width, view.height, corner)
                    }
                }
            }
            clipToOutline = true
            invalidateOutline()
        }
    }
}

/**
 * Morphs a profile image from a circle toward [pressedCornerRadiusDp] while pressed.
 * BezelImageView clips with a circular bitmap mask and overwrites outlineProvider, so the
 * mask itself must be a round-rect GradientDrawable and animated here.
 */
private fun View.imageMorphState(): ShapeMorphState {
    return imageMorphStates.getOrPut(this) {
        ShapeMorphState(circleRadiusPx()).also { state ->
            state.onRadiusChanged = { radius -> applyImageCornerRadius(radius) }
        }
    }
}

fun View.releaseExpressiveImageMorph(
    releaseDurationMs: Long = 250L,
    minHoldTimeMs: Long = 200L,
    immediate: Boolean = false,
) {
    val state = imageMorphStates[this] ?: return
    val restRadiusPx = circleRadiusPx()
    if (immediate || !isAttachedToWindow) {
        state.shapeAnimator?.cancel()
        state.isPressedMorph = false
        state.currentRadiusPx = restRadiusPx
        applyImageCornerRadius(restRadiusPx)
        return
    }
    if (!state.isPressedMorph) return
    state.isPressedMorph = false
    val elapsed = System.currentTimeMillis() - state.downTimeMs
    val delay = if (elapsed < minHoldTimeMs) minHoldTimeMs - elapsed else 0L
    state.animateShape(this, restRadiusPx, releaseDurationMs, delayMs = delay)
}

fun View.cancelExpressiveImageShapeMorph() {
    releaseExpressiveImageMorph(immediate = true)
}

fun View.handleExpressiveImageTouch(
    event: MotionEvent,
    pressedCornerRadiusDp: Float = 10f,
    pressDurationMs: Long = 250L,
    releaseDurationMs: Long = 250L,
    minHoldTimeMs: Long = 200L,
) {
    val pressedRadiusPx = pressedCornerRadiusDp * resources.displayMetrics.density
    val state = imageMorphState()

    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            if (!state.isPressedMorph) {
                state.downTimeMs = System.currentTimeMillis()
            }
            state.isPressedMorph = true
            state.animateShape(this, pressedRadiusPx, pressDurationMs)
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_OUTSIDE -> {
            releaseExpressiveImageMorph(releaseDurationMs, minHoldTimeMs)
        }
    }
}

fun View.dispatchExpressiveImageMorphToDescendants(event: MotionEvent) {
    val radius = getTag(R.id.nav_expressive_image_morph) as? Float
    if (radius != null) {
        handleExpressiveImageTouch(event, pressedCornerRadiusDp = radius)
    }
    if (this is ViewGroup) {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            // Skip inner cards or inner RecyclerViews so outer container touch events
            // do not propagate down to profile pictures inside specific announcement cards
            if (child is MaterialCardView || child is RecyclerView) {
                continue
            }
            child.dispatchExpressiveImageMorphToDescendants(event)
        }
    }
}

private class CardMorphSession(
    var initialModel: com.google.android.material.shape.ShapeAppearanceModel? = null,
    var animator: ValueAnimator? = null,
    var downTimeMs: Long = 0L,
    var pressed: Boolean = false,
)

private val cardMorphSessions = WeakHashMap<View, CardMorphSession>()

/**
 * Cancels any in-flight press morph on this card and forgets its state.
 * Call it before rebinding a recycled card, so a stale animator cannot
 * overwrite the freshly bound shape afterwards.
 */
fun MaterialCardView.cancelExpressiveCardShapeMorph() {
    cardMorphSessions.remove(this)?.animator?.cancel()
}

private fun animateCardMorph(
    card: MaterialCardView,
    session: CardMorphSession,
    pressed: Boolean,
    durationMs: Long,
    delayMs: Long = 0L,
) {
    val density = card.resources.displayMetrics.density
    val bounds = RectF(
        0f,
        0f,
        card.width.coerceAtLeast(10).toFloat(),
        card.height.coerceAtLeast(10).toFloat()
    )
    val model = card.shapeAppearanceModel
    val starts = listOf(
        model.topLeftCornerSize.getCornerSize(bounds),
        model.topRightCornerSize.getCornerSize(bounds),
        model.bottomLeftCornerSize.getCornerSize(bounds),
        model.bottomRightCornerSize.getCornerSize(bounds),
    )
    val rest = session.initialModel
    val targets = if (pressed) {
        starts.map { corner ->
            if (corner >= 16f * density) 12f * density
            else if (corner > 0f) 2f * density
            else 0f
        }
    } else {
        listOf(
            rest?.topLeftCornerSize?.getCornerSize(bounds) ?: starts[0],
            rest?.topRightCornerSize?.getCornerSize(bounds) ?: starts[1],
            rest?.bottomLeftCornerSize?.getCornerSize(bounds) ?: starts[2],
            rest?.bottomRightCornerSize?.getCornerSize(bounds) ?: starts[3],
        )
    }

    session.animator?.cancel()
    session.animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = durationMs
        startDelay = delayMs
        interpolator = FastOutSlowInInterpolator()
        addUpdateListener { valueAnimator ->
            val fraction = valueAnimator.animatedFraction
            card.shapeAppearanceModel = model.toBuilder()
                .setTopLeftCornerSize(starts[0] + (targets[0] - starts[0]) * fraction)
                .setTopRightCornerSize(starts[1] + (targets[1] - starts[1]) * fraction)
                .setBottomLeftCornerSize(starts[2] + (targets[2] - starts[2]) * fraction)
                .setBottomRightCornerSize(starts[3] + (targets[3] - starts[3]) * fraction)
                .build()
        }
        start()
    }
}

@SuppressLint("ClickableViewAccessibility")
fun MaterialCardView.enableExpressiveCardShapeMorph(
    pressDurationMs: Long = 250L,
    releaseDurationMs: Long = 250L,
    minHoldTimeMs: Long = 200L,
) {
    setOnTouchListener { view, event ->
        val card = view as? MaterialCardView ?: return@setOnTouchListener view.onTouchEvent(event)
        val handled = view.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val session = cardMorphSessions.getOrPut(card) { CardMorphSession() }
                session.animator?.cancel()
                session.initialModel = card.shapeAppearanceModel
                session.downTimeMs = System.currentTimeMillis()
                session.pressed = true
                animateCardMorph(card, session, pressed = true, durationMs = pressDurationMs)
                card.dispatchExpressiveImageMorphToDescendants(event)
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val session = cardMorphSessions[card]
                if (session?.pressed != true) return@setOnTouchListener handled
                session.pressed = false
                val elapsed = System.currentTimeMillis() - session.downTimeMs
                val delay = (minHoldTimeMs - elapsed).coerceAtLeast(0L)
                animateCardMorph(
                    card,
                    session,
                    pressed = false,
                    durationMs = releaseDurationMs,
                    delayMs = delay
                )
                card.dispatchExpressiveImageMorphToDescendants(event)
                handled
            }
            else -> handled
        }
    }
}

/**
 * Enables Material Design 3 Expressive shape morphing on touch/press for profile pictures.
 */
@SuppressLint("ClickableViewAccessibility")
fun View.enableExpressiveImageShapeMorph(
    pressedCornerRadiusDp: Float = 10f,
    pressDurationMs: Long = 250L,
    releaseDurationMs: Long = 250L,
    minHoldTimeMs: Long = 200L,
) {
    if (getTag(R.id.nav_expressive_image_morph) is Float) {
        releaseExpressiveImageMorph(immediate = true)
        return
    }
    setTag(R.id.nav_expressive_image_morph, pressedCornerRadiusDp)

    if (this is BezelImageView) {
        maskDrawable()?.mutate()
        clipToOutline = false
    } else {
        background?.let { background = (it.constantState?.newDrawable() ?: it).mutate() }
        if (this is ImageView) {
            drawable?.let { setImageDrawable((it.constantState?.newDrawable() ?: it).mutate()) }
        }
    }

    applyImageCornerRadius(circleRadiusPx())
    addOnLayoutChangeListener { v, _, _, _, _, oldLeft, oldTop, oldRight, oldBottom ->
        if (v.width == oldRight - oldLeft && v.height == oldBottom - oldTop) return@addOnLayoutChangeListener
        val state = imageMorphStates[v]
        if (state?.isPressedMorph == true || state?.shapeAnimator?.isStarted == true) {
            return@addOnLayoutChangeListener
        }
        val rest = v.circleRadiusPx()
        if (state != null) state.currentRadiusPx = rest
        v.applyImageCornerRadius(rest)
    }

    val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
        if (!hasFocus) {
            releaseExpressiveImageMorph(releaseDurationMs, minHoldTimeMs, immediate = true)
        }
    }
    val attachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            v.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)
        }

        override fun onViewDetachedFromWindow(v: View) {
            v.viewTreeObserver.removeOnWindowFocusChangeListener(focusListener)
            v.releaseExpressiveImageMorph(immediate = true)
        }
    }
    addOnAttachStateChangeListener(attachListener)
    if (isAttachedToWindow) {
        viewTreeObserver.addOnWindowFocusChangeListener(focusListener)
    }

    setOnTouchListener { v, event ->
        if (!v.isEnabled) {
            return@setOnTouchListener v.onTouchEvent(event)
        }
        v.handleExpressiveImageTouch(
            event,
            pressedCornerRadiusDp,
            pressDurationMs,
            releaseDurationMs,
            minHoldTimeMs,
        )
        v.onTouchEvent(event)
    }
}
