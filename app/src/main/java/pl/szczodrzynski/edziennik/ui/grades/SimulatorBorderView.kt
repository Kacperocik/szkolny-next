package pl.szczodrzynski.edziennik.ui.grades

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.android.material.color.MaterialColors
import pl.szczodrzynski.edziennik.ext.dp
import pl.szczodrzynski.edziennik.ext.isNightMode
import kotlin.math.min

/** Striped border overlay indicating that grade simulation is active. */
class SimulatorBorderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val thicknessPx = 8.dp.toFloat()
    private val stripePx = 16.dp.toFloat()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private fun stripeColor(x: Float, y: Float, mono: Int, accent: Int): Int {
        val index = (((x + y) / stripePx).toInt() % 2 + 2) % 2
        return if (index == 0) mono else accent
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val t = thicknessPx
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f)
            return
        val accent = MaterialColors.getColor(
            this,
            androidx.appcompat.R.attr.colorPrimary,
            Color.RED
        )
        val mono = if (context.isNightMode) Color.WHITE else Color.BLACK

        // Use the same x + y stripe pattern on every edge to keep corners continuous.
        var x = 0f
        while (x < w) {
            val end = min(x + stripePx + 1f, w)
            paint.color = stripeColor(x, 0f, mono, accent)
            canvas.drawRect(x, 0f, end, t, paint)
            paint.color = stripeColor(x, h - t, mono, accent)
            canvas.drawRect(x, h - t, end, h, paint)
            x += stripePx
        }

        // lewo i prawo
        var y = 0f
        while (y < h) {
            val end = min(y + stripePx + 1f, h)
            paint.color = stripeColor(0f, y, mono, accent)
            canvas.drawRect(0f, y, t, end, paint)
            paint.color = stripeColor(w - t, y, mono, accent)
            canvas.drawRect(w - t, y, w, end, paint)
            y += stripePx
        }
    }
}
