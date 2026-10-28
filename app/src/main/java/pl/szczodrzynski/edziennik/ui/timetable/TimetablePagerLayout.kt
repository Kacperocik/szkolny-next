package pl.szczodrzynski.edziennik.ui.timetable

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout

/**
 * Reports width changes before the children are measured and laid out,
 * so that the ViewPager2's page size can be adjusted before it lays out
 * its pages (a stale page size makes it lose the current item).
 */
class TimetablePagerLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    var onWidthChanged: ((width: Int) -> Unit)? = null
    private var lastWidth = -1

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED && width != lastWidth) {
            lastWidth = width
            onWidthChanged?.invoke(width)
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
