/*
 * Copyright (c) Kuba Szczodrzyński 2021-4-8.
 */

package pl.szczodrzynski.edziennik.ui.agenda.teacherabsence

import android.os.Build
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.github.tibolte.agendacalendarview.render.EventRenderer
import com.google.android.material.color.MaterialColors
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.databinding.AgendaCounterItemBinding
import pl.szczodrzynski.edziennik.databinding.AgendaWrappedCounterBinding
import pl.szczodrzynski.edziennik.ext.resolveAttr
import pl.szczodrzynski.edziennik.ext.setTintColor
import pl.szczodrzynski.edziennik.utils.Colors

class TeacherAbsenceEventRenderer : EventRenderer<TeacherAbsenceEvent>() {

    override fun render(view: View, event: TeacherAbsenceEvent) {
        val b = AgendaWrappedCounterBinding.bind(view).item
        bindCard(b.card, b.name, b.count, b.badgeBackground, b.badge, event)
    }

    fun render(b: AgendaCounterItemBinding, event: TeacherAbsenceEvent) {
        bindCard(b.card, b.name, b.count, b.badgeBackground, b.badge, event)
    }

    private fun bindCard(
        card: FrameLayout,
        name: TextView,
        count: TextView,
        badgeBackground: View,
        badge: View,
        event: TeacherAbsenceEvent
    ) {
        val harmonizedColor = MaterialColors.harmonizeWithPrimary(card.context, event.color)
        val isClassic = App.profile.config.ui.agendaClassicCards

        if (isClassic) {
            val textColor = Colors.legibleTextColor(harmonizedColor)
            val bgColor = android.R.attr.colorBackground.resolveAttr(card.context)
            card.background = ContextCompat.getDrawable(card.context, R.drawable.bg_rounded_8dp)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                card.foreground = ContextCompat.getDrawable(card.context, R.drawable.bg_rounded_8dp_outline)
                card.foreground?.setTintColor(harmonizedColor)
            }
            card.background?.setTintColor(harmonizedColor)
            name.setText(R.string.agenda_teacher_absence)
            name.setTextColor(textColor)
            count.text = event.count.toString()
            count.background = ContextCompat.getDrawable(card.context, R.drawable.bg_rounded_8dp)
            count.background?.setTintColor(bgColor)
            count.setTextColor(Colors.legibleTextColor(bgColor))
        } else {
            val surfaceContainer = MaterialColors.getColor(card, R.attr.colorCard, 0x1a808080)
            val primaryText = MaterialColors.getColor(card, android.R.attr.textColorPrimary, 0xff000000.toInt())

            card.background = ContextCompat.getDrawable(card.context, R.drawable.bg_rounded_16dp)
            card.background?.setTintColor(surfaceContainer)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                card.foreground = null
            }
            name.setText(R.string.agenda_teacher_absence)
            name.setTextColor(primaryText)
            count.text = event.count.toString()
            count.background = ContextCompat.getDrawable(card.context, R.drawable.bg_rounded_16dp)
            count.background?.setTintColor(harmonizedColor)
            count.setTextColor(Colors.legibleTextColor(harmonizedColor))
        }

        badgeBackground.isVisible = false
        badge.isVisible = false
    }

    override fun getEventLayout(): Int = R.layout.agenda_wrapped_counter
}
