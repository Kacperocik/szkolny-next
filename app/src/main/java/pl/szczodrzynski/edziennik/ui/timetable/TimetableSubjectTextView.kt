package pl.szczodrzynski.edziennik.ui.timetable

import android.content.Context
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import com.mikepenz.iconics.view.IconicsTextView
import kotlin.math.abs

class TimetableSubjectTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle,
) : IconicsTextView(context, attrs, defStyleAttr) {

    var maxSubjectLines = 2
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Fit after the header assigns the title its width, so TextView measures the final font size.
        val availableWidth = MeasureSpec.getSize(widthMeasureSpec) - compoundPaddingLeft - compoundPaddingRight
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY && availableWidth > 0) {
            val availableHeight = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED)
                Int.MAX_VALUE
            else
                MeasureSpec.getSize(heightMeasureSpec) - compoundPaddingTop - compoundPaddingBottom
            fitSubjectName(availableWidth, availableHeight)
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun fitSubjectName(availableWidth: Int, availableHeight: Int) {
        val metrics = resources.displayMetrics
        val maxPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 16f, metrics)
        val minPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, metrics)
        val largestLayout = buildSubjectLayout(maxPx, availableWidth)

        var fittedSize = maxPx
        var lineLimit = maxSubjectLines
        if (largestLayout.lineCount > lineLimit || largestLayout.height > availableHeight) {
            val smallestLayout = buildSubjectLayout(minPx, availableWidth)
            while (lineLimit > 1 && smallestLayout.lineCount >= lineLimit &&
                smallestLayout.getLineBottom(lineLimit - 1) > availableHeight
            ) {
                lineLimit--
            }

            fittedSize = minPx
            var low = minPx.toInt() + 1
            var high = maxPx.toInt()
            while (low <= high) {
                val mid = (low + high) / 2
                val layout = buildSubjectLayout(mid.toFloat(), availableWidth)
                if (layout.lineCount <= lineLimit && layout.height <= availableHeight) {
                    fittedSize = mid.toFloat()
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }
        }

        if (maxLines != lineLimit)
            maxLines = lineLimit
        if (abs(textSize - fittedSize) > 0.5f)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, fittedSize)
    }

    private fun buildSubjectLayout(sizePx: Float, availableWidth: Int): StaticLayout {
        val layoutPaint = TextPaint(paint).apply { textSize = sizePx }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, layoutPaint, availableWidth)
                .setIncludePad(includeFontPadding)
                .setLineSpacing(lineSpacingExtra, lineSpacingMultiplier)
                .setBreakStrategy(breakStrategy)
                .setHyphenationFrequency(hyphenationFrequency)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(
                text,
                layoutPaint,
                availableWidth,
                Layout.Alignment.ALIGN_NORMAL,
                lineSpacingMultiplier,
                lineSpacingExtra,
                includeFontPadding,
            )
        }
    }
}
