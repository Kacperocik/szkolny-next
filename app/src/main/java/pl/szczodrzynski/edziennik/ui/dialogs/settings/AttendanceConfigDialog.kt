/*
 * Copyright (c) Kuba Szczodrzyński 2021-10-17.
 */

package pl.szczodrzynski.edziennik.ui.dialogs.settings

import android.view.LayoutInflater
import android.widget.RadioButton
import androidx.appcompat.app.AppCompatActivity
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.core.manager.AttendanceManager.Companion.SHAPE_MODE_CIRCLE
import pl.szczodrzynski.edziennik.core.manager.AttendanceManager.Companion.SHAPE_MODE_ROUNDED_RECT
import pl.szczodrzynski.edziennik.databinding.AttendanceConfigDialogBinding
import pl.szczodrzynski.edziennik.ext.onChange
import pl.szczodrzynski.edziennik.ext.onClick
import pl.szczodrzynski.edziennik.ext.setOnSelectedListener
import pl.szczodrzynski.edziennik.ui.base.dialog.ConfigDialog

class AttendanceConfigDialog(
    activity: AppCompatActivity,
    reloadOnDismiss: Boolean = true,
) : ConfigDialog<AttendanceConfigDialogBinding>(activity, reloadOnDismiss) {

    override fun getTitleRes() = R.string.menu_attendance_config
    override fun inflate(layoutInflater: LayoutInflater) =
        AttendanceConfigDialogBinding.inflate(layoutInflater)

    override suspend fun loadConfig() {
        b.useSymbols.isChecked = app.profile.config.attendance.useSymbols
        b.groupConsecutiveDays.isChecked = app.profile.config.attendance.groupConsecutiveDays
        b.showPresenceInMonth.isChecked = app.profile.config.attendance.showPresenceInMonth
        when (app.profile.config.attendance.shapeMode) {
            SHAPE_MODE_CIRCLE -> b.attendanceShapeCircle
            else -> b.attendanceShapeRoundedRect
        }.isChecked = true
    }

    override fun initView() {
        fun selectRadio(selected: RadioButton, vararg group: RadioButton) {
            group.forEach { it.isChecked = it == selected }
        }

        b.cardUseSymbols.onClick {
            b.useSymbols.toggle()
        }
        b.cardGroupConsecutiveDays.onClick {
            b.groupConsecutiveDays.toggle()
        }
        b.cardShowPresenceInMonth.onClick {
            b.showPresenceInMonth.toggle()
        }
        val shapeGroup = arrayOf(b.attendanceShapeRoundedRect, b.attendanceShapeCircle)
        b.cardShapeRoundedRect.onClick {
            selectRadio(b.attendanceShapeRoundedRect, *shapeGroup)
            app.profile.config.attendance.shapeMode = SHAPE_MODE_ROUNDED_RECT
        }
        b.cardShapeCircle.onClick {
            selectRadio(b.attendanceShapeCircle, *shapeGroup)
            app.profile.config.attendance.shapeMode = SHAPE_MODE_CIRCLE
        }
        b.attendanceShapeRoundedRect.setOnSelectedListener {
            selectRadio(b.attendanceShapeRoundedRect, *shapeGroup)
            app.profile.config.attendance.shapeMode = SHAPE_MODE_ROUNDED_RECT
        }
        b.attendanceShapeCircle.setOnSelectedListener {
            selectRadio(b.attendanceShapeCircle, *shapeGroup)
            app.profile.config.attendance.shapeMode = SHAPE_MODE_CIRCLE
        }
        b.useSymbols.onChange { _, isChecked ->
            app.profile.config.attendance.useSymbols = isChecked
        }
        b.groupConsecutiveDays.onChange { _, isChecked ->
            app.profile.config.attendance.groupConsecutiveDays = isChecked
        }
        b.showPresenceInMonth.onChange { _, isChecked ->
            app.profile.config.attendance.showPresenceInMonth = isChecked
        }
    }
}
