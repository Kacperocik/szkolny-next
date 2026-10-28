package pl.szczodrzynski.edziennik.ui.base.views

import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.color.MaterialColors
import com.google.android.material.tabs.TabLayout
import pl.szczodrzynski.edziennik.R

internal fun TabLayout.applyPillTabIndicator() {
    if (tabSelectedIndicator is PillTabIndicatorDrawable) return
    val strip = getChildAt(0) as? ViewGroup ?: return
    setSelectedTabIndicator(PillTabIndicatorDrawable(strip, tabSelectedIndicator))
}

/** Draws both decorations from Material's animated indicator bounds. */
internal class PillTabIndicatorDrawable(
    private val tabStrip: ViewGroup,
    private val pill: Drawable,
) : Drawable() {
    private val density = tabStrip.resources.displayMetrics.density
    private val labelBounds = Rect()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(tabStrip, R.attr.colorPrimary)
    }

    override fun onBoundsChange(bounds: Rect) {
        pill.bounds = bounds
    }

    override fun draw(canvas: Canvas) {
        if (bounds.width() <= 0) return
        pill.draw(canvas)
        val left = textEdge(bounds.left.toFloat(), leftEdge = true) ?: return
        val right = textEdge(bounds.right.toFloat(), leftEdge = false) ?: return
        if (right <= left) return

        // The tab background's bottom 1dp is the divider. Keep the underline
        // immediately above it, outside the pill's bottom inset.
        val bottom = bounds.bottom - density
        canvas.drawRoundRect(left, bottom - 2f * density, right, bottom, density, density, paint)
    }

    private fun textEdge(position: Float, leftEdge: Boolean): Float? {
        var before: View? = null
        var after: View? = null
        var beforePosition = Float.NEGATIVE_INFINITY
        var afterPosition = Float.POSITIVE_INFINITY
        for (index in 0 until tabStrip.childCount) {
            val tab = tabStrip.getChildAt(index)
            if (tab.visibility != View.VISIBLE || tab.width == 0) continue
            val edge = (if (leftEdge) tab.left else tab.right).toFloat()
            if (edge <= position && edge > beforePosition) {
                before = tab
                beforePosition = edge
            }
            if (edge >= position && edge < afterPosition) {
                after = tab
                afterPosition = edge
            }
        }
        // Only interpolate between attached tab edges. Clamping to the nearest
        // label would draw a stray underline when the pill is outside the strip.
        val start = before ?: return null
        val end = after ?: return null
        val startText = labelEdge(start, leftEdge) ?: return null
        val endText = labelEdge(end, leftEdge) ?: return null
        if (start === end) return startText

        // Recover each edge's progress separately, preserving the pill's elastic
        // animation in either direction, including RTL and horizontal scrolling.
        val fraction = ((position - beforePosition) / (afterPosition - beforePosition)).coerceIn(0f, 1f)
        return startText + (endText - startText) * fraction
    }

    private fun labelEdge(tab: View, leftEdge: Boolean): Float? {
        val label = (tab as? ViewGroup)?.findTabLabel() ?: return null
        val layout = label.layout ?: return null
        if (layout.lineCount == 0) return null
        var edge = if (leftEdge) Float.POSITIVE_INFINITY else Float.NEGATIVE_INFINITY
        for (line in 0 until layout.lineCount) {
            edge = if (leftEdge) minOf(edge, layout.getLineLeft(line))
            else maxOf(edge, layout.getLineRight(line))
        }
        labelBounds.set(0, 0, label.width, label.height)
        tabStrip.offsetDescendantRectToMyCoords(label, labelBounds)
        return labelBounds.left + label.compoundPaddingLeft - label.scrollX + edge
    }

    private fun ViewGroup.findTabLabel(): TextView? {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility != View.VISIBLE) continue
            if (child is TextView && child.text.isNotEmpty()) return child
            if (child is ViewGroup) child.findTabLabel()?.let { return it }
        }
        return null
    }

    override fun setTint(tintColor: Int) = pill.setTint(tintColor)
    override fun setTintList(tint: ColorStateList?) = pill.setTintList(tint)
    override fun setTintMode(tintMode: PorterDuff.Mode?) = pill.setTintMode(tintMode)
    override fun setColorFilter(colorFilter: ColorFilter?) = pill.setColorFilter(colorFilter)
    override fun getIntrinsicHeight() = pill.intrinsicHeight
    override fun getIntrinsicWidth() = pill.intrinsicWidth
    override fun isStateful() = pill.isStateful
    override fun onStateChange(state: IntArray) = pill.setState(state)

    override fun setAlpha(alpha: Int) {
        pill.alpha = alpha
        paint.alpha = alpha
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
