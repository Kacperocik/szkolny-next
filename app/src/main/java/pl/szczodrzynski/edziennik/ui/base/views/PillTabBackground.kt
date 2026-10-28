package pl.szczodrzynski.edziennik.ui.base.views

import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import pl.szczodrzynski.edziennik.R

internal fun View.applyPillTabBackground() {
    val padStart = paddingStart
    val padEnd = paddingEnd
    val ripple = AppCompatResources.getDrawable(context, R.drawable.tab_pill_ripple) ?: return
    background = ripple
    setPaddingRelative(padStart, 0, padEnd, 0)
}
