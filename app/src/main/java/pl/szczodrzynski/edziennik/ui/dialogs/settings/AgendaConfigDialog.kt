/*
 * Copyright (c) Kuba Szczodrzyński 2021-4-10.
 */

package pl.szczodrzynski.edziennik.ui.dialogs.settings

import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.db.entity.Profile
import pl.szczodrzynski.edziennik.databinding.DialogConfigAgendaBinding
import pl.szczodrzynski.edziennik.ext.onChange
import pl.szczodrzynski.edziennik.ext.onClick
import pl.szczodrzynski.edziennik.ui.base.dialog.ConfigDialog
import pl.szczodrzynski.edziennik.ui.event.EventColorsDialog
import pl.szczodrzynski.edziennik.utils.TextInputDropDown

class AgendaConfigDialog(
    activity: AppCompatActivity,
    reloadOnDismiss: Boolean = true,
    private val isNewAgenda: Boolean = false,
) : ConfigDialog<DialogConfigAgendaBinding>(activity, reloadOnDismiss) {

    override fun getTitleRes() = R.string.menu_agenda_config
    override fun getNegativeButtonText() = R.string.cancel
    override fun inflate(layoutInflater: LayoutInflater) =
        DialogConfigAgendaBinding.inflate(layoutInflater)

    override fun initView() {
        b.changeEventColors.onClick {
            EventColorsDialog(activity).show()
        }
    }

    override suspend fun loadConfig() {
        b.config = app.profile.config
        b.isAgendaMode = app.profile.config.ui.agendaViewType == Profile.AGENDA_DEFAULT
        b.isNewAgenda = isNewAgenda

        fun updateDotsEnabled() {
            val changesOn = b.agendaLessonChanges.isChecked
            b.agendaLessonChangesDots.isEnabled = changesOn
            b.cardLessonChangesDots.isClickable = changesOn
            b.cardLessonChangesDots.isFocusable = changesOn
            b.lessonChangesDotsTitle.isEnabled = changesOn
            val absenceOn = b.agendaTeacherAbsence.isChecked
            b.agendaTeacherAbsenceDots.isEnabled = absenceOn
            b.cardTeacherAbsenceDots.isClickable = absenceOn
            b.cardTeacherAbsenceDots.isFocusable = absenceOn
            b.teacherAbsenceDotsTitle.isEnabled = absenceOn
        }
        // OnClickListener (not OnCheckedChangeListener) so the two-way data binding
        // of the parent checkboxes keeps working. Tapping the parent card calls
        // checkbox.performClick(), which also fires this listener after toggling.
        b.agendaLessonChanges.setOnClickListener { updateDotsEnabled() }
        b.agendaTeacherAbsence.setOnClickListener { updateDotsEnabled() }
        b.root.post { b.executePendingBindings(); updateDotsEnabled() }

        b.agendaCalendarWeeksDropdown.append(listOf(
            TextInputDropDown.Item(0, activity.getString(R.string.agenda_config_calendar_weeks_whole)),
            TextInputDropDown.Item(3, activity.getString(R.string.agenda_config_calendar_weeks_3)),
            TextInputDropDown.Item(2, activity.getString(R.string.agenda_config_calendar_weeks_2)),
            TextInputDropDown.Item(1, activity.getString(R.string.agenda_config_calendar_weeks_1)),
        ))
        b.agendaCalendarWeeksDropdown.select(app.profile.config.ui.agendaCalendarWeeks.toLong().takeIf { it in 0..3 } ?: 0)
        b.agendaCalendarWeeksDropdown.setOnChangeListener { item ->
            app.profile.config.ui.agendaCalendarWeeks = item.id.toInt()
            true
        }

        var calledFromListener = false
        b.eventSharingEnabled.isChecked = app.profile.canShare
        b.shareByDefault.isEnabled = app.profile.canShare
        b.eventSharingEnabled.onChange { _, isChecked ->
            if (calledFromListener) {
                calledFromListener = false
                return@onChange
            }
            b.eventSharingEnabled.isChecked = !isChecked
            val dialog = RegistrationConfigDialog(
                activity,
                app.profile,
                onChangeListener = { enabled ->
                    calledFromListener = true
                    b.eventSharingEnabled.isChecked = enabled
                    b.shareByDefault.isEnabled = enabled
                },
            )
            if (isChecked)
                dialog.showEnableDialog()
            else
                dialog.showDisableDialog()
            return@onChange
        }
    }
}
