package pl.szczodrzynski.edziennik.ui.dialogs

import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.ext.dp

class BellSyncLayout(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    private var previousOrientation: Int? = null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val currentOrientation = resources.configuration.orientation
        if (previousOrientation != currentOrientation) {
            previousOrientation = currentOrientation
            val landscape = currentOrientation == Configuration.ORIENTATION_LANDSCAPE
            orientation = if (landscape) HORIZONTAL else VERTICAL
            val verticalPadding = (if (landscape) 16 else 24).dp
            updatePadding(top = verticalPadding, bottom = verticalPadding)
            findViewById<View>(R.id.bellSyncInstructions).updateLayoutParams<LayoutParams> {
                width = if (landscape) 0 else ViewGroup.LayoutParams.MATCH_PARENT
            }
            findViewById<View>(R.id.bellSyncButton).updateLayoutParams<LayoutParams> {
                setMargins(0, 0, 0, 0)
                marginStart = (if (landscape) 24 else 32).dp
                marginEnd = (if (landscape) 0 else 32).dp
                topMargin = (if (landscape) 0 else 32).dp
                bottomMargin = topMargin
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
