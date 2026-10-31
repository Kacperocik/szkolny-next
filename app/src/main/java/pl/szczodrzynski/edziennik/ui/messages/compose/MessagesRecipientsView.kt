package pl.szczodrzynski.edziennik.ui.messages.compose

import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import com.hootsuite.nachos.NachoTextView

class MessagesRecipientsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : NachoTextView(context, attrs) {

    var popupBoundsView: View? = null
    var onPopupVisibilityChanged: ((Boolean) -> Unit)? = null

    private var lastDismissTime = Long.MIN_VALUE
    private val popupBounds = Rect()
    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        if (isPopupShowing && availablePopupBounds() != popupBounds)
            showDropDown()
    }

    init {
        setOnDismissListener {
            ignoreThreshold = false
            lastDismissTime = SystemClock.uptimeMillis()
            onPopupVisibilityChanged?.invoke(false)
        }
    }

    fun toggleDropDown() {
        if (isPopupShowing) {
            dismissDropDown()
        } else if (lastDismissTime == Long.MIN_VALUE ||
            SystemClock.uptimeMillis() - lastDismissTime > 300L) {
            // An outside touch can dismiss the popup before the arrow receives its click.
            error = null
            ignoreThreshold = true
            (adapter as? MessagesComposeSuggestionAdapter)?.filter?.filter(null)
            showDropDown()
        }
    }

    override fun showDropDown() {
        val bounds = availablePopupBounds()
        if (bounds.isEmpty || !isShown || windowToken == null) {
            dismissDropDown()
            return
        }
        popupBounds.set(bounds)
        val padding = Rect()
        dropDownBackground?.getPadding(padding)
        val rowWidth = (width - padding.left - padding.right).coerceAtLeast(1)
        val widthSpec = MeasureSpec.makeMeasureSpec(rowWidth, MeasureSpec.EXACTLY)
        val heightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        var contentHeight = padding.top + padding.bottom
        val rowParent = parent as? ViewGroup ?: return
        adapter?.let { suggestions ->
            for (position in 0 until suggestions.count) {
                val row = suggestions.getView(position, null, rowParent)
                row.measure(widthSpec, heightSpec)
                contentHeight += row.measuredHeight
                if (contentHeight >= bounds.height()) break
            }
        }
        dropDownVerticalOffset = 0
        dropDownHeight = contentHeight.coerceIn(1, bounds.height())
        super.showDropDown()
        onPopupVisibilityChanged?.invoke(isPopupShowing)
    }

    private fun availablePopupBounds(): Rect {
        val content = popupBoundsView ?: return Rect()
        val visibleWindow = Rect()
        getWindowVisibleDisplayFrame(visibleWindow)
        val contentLocation = IntArray(2)
        content.getLocationOnScreen(contentLocation)
        val fieldLocation = IntArray(2)
        getLocationOnScreen(fieldLocation)
        val gap = (8 * resources.displayMetrics.density).toInt()
        val top = fieldLocation[1] + height
        if (top <= maxOf(contentLocation[1], visibleWindow.top)) return Rect()
        return Rect(
            maxOf(contentLocation[0], visibleWindow.left),
            top,
            minOf(contentLocation[0] + content.width, visibleWindow.right),
            minOf(contentLocation[1] + content.height, visibleWindow.bottom) - gap,
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
        dismissDropDown()
        super.onDetachedFromWindow()
    }
}
