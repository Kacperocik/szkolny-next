/*
 * Copyright (c) Kuba Szczodrzyński 2020-5-9.
 */

package pl.szczodrzynski.edziennik.ui.attendance

import android.view.LayoutInflater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.core.manager.AttendanceManager.Companion.SHAPE_MODE_CIRCLE
import pl.szczodrzynski.edziennik.data.db.full.AttendanceFull
import pl.szczodrzynski.edziennik.databinding.AttendanceDetailsDialogBinding
import pl.szczodrzynski.edziennik.ext.setTintColor
import pl.szczodrzynski.edziennik.ui.base.dialog.BindingDialog
import pl.szczodrzynski.edziennik.ui.notes.setupNotesButton
import pl.szczodrzynski.edziennik.utils.BetterLink
import pl.szczodrzynski.edziennik.core.manager.NoteManager
import pl.szczodrzynski.edziennik.ext.enableExpressiveShapeMorph
import pl.szczodrzynski.edziennik.ext.onClick
import pl.szczodrzynski.edziennik.ui.excuses.ExcuseDetailsDialog
import pl.szczodrzynski.edziennik.ui.excuses.ExcuseEditorDialog
import pl.szczodrzynski.edziennik.data.db.entity.Attendance
import pl.szczodrzynski.edziennik.data.db.entity.LibrusExcuse
import pl.szczodrzynski.edziennik.data.enums.LoginType

class AttendanceDetailsDialog(
    activity: AppCompatActivity,
    private val attendance: AttendanceFull,
    private val showNotes: Boolean = true,
) : BindingDialog<AttendanceDetailsDialogBinding>(activity) {

    override fun getTitleRes(): Int? = null
    override fun inflate(layoutInflater: LayoutInflater) =
        AttendanceDetailsDialogBinding.inflate(layoutInflater)

    override fun getPositiveButtonText() = R.string.close
    override fun hasCloseIconWhenSpaceLimited() = true

    override suspend fun onBeforeShow(): Boolean {
        val manager = app.attendanceManager

        val attendanceColor = manager.getAttendanceColor(attendance)
        b.attendance = attendance
        b.devMode = App.devMode
        b.attendanceName.setBackgroundResource(
            if (manager.shapeMode == SHAPE_MODE_CIRCLE)
                R.drawable.bg_circle
            else
                R.drawable.bg_rounded_16dp
        )
        b.attendanceName.setTextColor(if (ColorUtils.calculateLuminance(attendanceColor) > 0.3) 0xaa000000.toInt() else 0xccffffff.toInt())
        b.attendanceName.background.setTintColor(attendanceColor)

        b.attendanceIsCounted.setText(if (attendance.isCounted) R.string.yes else R.string.no)

        attendance.teacherName?.let { name ->
            BetterLink.attach(
                b.teacherName,
                teachers = mapOf(attendance.teacherId to name),
                onActionSelected = ::dismiss
            )
        }

        setupExcuseButton()
        b.notesButton.isVisible = showNotes
        b.notesButton.setupNotesButton(
            activity = activity,
            owner = attendance,
        )
        b.legend.isVisible = showNotes
        if (showNotes)
            NoteManager.setLegendText(attendance, b.legend)
        return true
    }

    private suspend fun setupExcuseButton() {
        val profile = withContext(Dispatchers.IO) { app.db.profileDao().getByIdNow(attendance.profileId) } ?: return
        if (profile.loginStoreType != LoginType.LIBRUS || !profile.isParent || profile.config.librusExcusesUnavailable) return
        val number = attendance.lessonNumber ?: return
        val excuse = withContext(Dispatchers.IO) {
            app.db.librusExcuseDao().getForLessonNow(profile.id, attendance.date, number)
        }
        if (excuse != null) {
            b.excuseStatus.isVisible = true
            b.excuseStatus.setText(when (excuse.status) {
                LibrusExcuse.Status.ACCEPT -> R.string.excuses_status_accept
                LibrusExcuse.Status.REJECT -> R.string.excuses_status_reject
                LibrusExcuse.Status.SEND -> R.string.excuses_status_send
                LibrusExcuse.Status.UNKNOWN -> R.string.excuses_status_unknown
            })
            b.excuseStatus.onClick { ExcuseDetailsDialog(activity, excuse).show() }
        }
        b.excuseButton.isVisible = !profile.archived && attendance.baseType in setOf(Attendance.TYPE_ABSENT, Attendance.TYPE_BELATED) &&
            (excuse == null || excuse.status == LibrusExcuse.Status.REJECT)
        b.excuseButton.enableExpressiveShapeMorph()
        b.excuseButton.onClick {
            dismiss()
            ExcuseEditorDialog(activity, attendance, profile.id).show()
        }
    }
}
