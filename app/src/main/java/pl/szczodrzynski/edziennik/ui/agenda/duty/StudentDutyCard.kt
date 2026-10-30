package pl.szczodrzynski.edziennik.ui.agenda.duty

import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.db.entity.StudentDuty
import pl.szczodrzynski.edziennik.databinding.StudentDutyCardBinding
import pl.szczodrzynski.edziennik.ext.enableExpressiveCardShapeMorph
import pl.szczodrzynski.edziennik.ext.setTintColor
import pl.szczodrzynski.edziennik.ui.base.dialog.SimpleDialog
import pl.szczodrzynski.edziennik.utils.Colors
import pl.szczodrzynski.edziennik.utils.models.Date

fun StudentDutyCardBinding.bindDuty(duty: StudentDuty, embedded: Boolean = false, onClick: () -> Unit) {
    val context = root.context
    period.text = context.getString(R.string.student_duty_period,
        Date.fromY_m_d(duty.dateFrom).formattedString, Date.fromY_m_d(duty.dateTo).formattedString)
    val color = MaterialColors.harmonizeWithPrimary(context, (context.applicationContext as App).eventManager.getStudentDutyColor(duty.profileId))
    val classic = App.config[duty.profileId].ui.agendaClassicCards
    val backgroundAttr = if (embedded) R.attr.colorCardNested else R.attr.colorCard
    root.setCardBackgroundColor(if (classic) color else MaterialColors.getColor(root, backgroundAttr))
    root.radius = (if (classic) 8f else 16f) * context.resources.displayMetrics.density
    title.setTextColor(if (classic) Colors.legibleTextColor(color) else MaterialColors.getColor(root, R.attr.colorSecondary))
    period.setTextColor(if (classic) Colors.legibleTextColor(color) else MaterialColors.getColor(root, android.R.attr.textColorPrimary))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        if (classic) {
            root.foreground = ContextCompat.getDrawable(context, R.drawable.bg_rounded_8dp_outline)?.apply { setTintColor(color) }
        } else {
            val ripple = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
            root.foreground = ContextCompat.getDrawable(context, ripple.resourceId)
        }
    }
    root.enableExpressiveCardShapeMorph()
    root.setOnClickListener { onClick() }
}

fun showStudentDutyDetails(activity: AppCompatActivity, duty: StudentDuty) {
    SimpleDialog<Unit>(activity) {
        title(R.string.student_duty_title)
        message(SpannableString(activity.getString(R.string.student_duty_period,
            Date.fromY_m_d(duty.dateFrom).formattedString, Date.fromY_m_d(duty.dateTo).formattedString)).apply {
            setSpan(RelativeSizeSpan(4f / 3f), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        })
        positive(R.string.close)
    }.show()
}
