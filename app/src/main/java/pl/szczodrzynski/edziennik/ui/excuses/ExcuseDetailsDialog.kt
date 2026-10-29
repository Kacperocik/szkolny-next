package pl.szczodrzynski.edziennik.ui.excuses

import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.db.entity.LibrusExcuse
import pl.szczodrzynski.edziennik.databinding.ExcuseDetailsDialogBinding
import pl.szczodrzynski.edziennik.ext.formatDate
import pl.szczodrzynski.edziennik.ui.base.dialog.BindingDialog
import pl.szczodrzynski.edziennik.utils.models.Date

class ExcuseDetailsDialog(
    activity: AppCompatActivity,
    private val excuse: LibrusExcuse,
) : BindingDialog<ExcuseDetailsDialogBinding>(activity) {

    override fun getTitleRes() = R.string.excuses_details_title
    override fun inflate(layoutInflater: LayoutInflater) =
        ExcuseDetailsDialogBinding.inflate(layoutInflater)

    override fun getPositiveButtonText() = R.string.close
    override fun hasCloseIconWhenSpaceLimited() = true

    override suspend fun onShow() {
        val (statusRes, hintRes, color) = when (excuse.status) {
            LibrusExcuse.Status.UNKNOWN ->
                Triple(R.string.excuses_status_unknown, R.string.excuses_status_unknown_hint, 0xff9e9e9e.toInt())
            LibrusExcuse.Status.SEND ->
                Triple(R.string.excuses_status_send, R.string.excuses_status_send_hint, 0xffffa000.toInt())
            LibrusExcuse.Status.ACCEPT ->
                Triple(R.string.excuses_status_accept, R.string.excuses_status_accept_hint, 0xff00c853.toInt())
            LibrusExcuse.Status.REJECT ->
                Triple(R.string.excuses_status_reject, R.string.excuses_status_reject_hint, 0xffff3d00.toInt())
        }
        b.excuseStatus.setText(statusRes)
        b.excuseStatus.setTextColor(color)
        b.excuseStatusHint.setText(hintRes)

        b.excusePeriod.text = if (excuse.isRange)
            activity.getString(
                R.string.excuses_period_range_format,
                excuse.dateFrom.formattedString,
                excuse.dateTo.formattedString,
            )
        else
            excuse.dateFrom.formattedString

        val lessons = resolveLessonNames()
        b.excuseLessonsLabel.isVisible = lessons != null
        b.excuseLessons.isVisible = lessons != null
        b.excuseLessons.text = lessons

        b.excuseMessage.text = excuse.message.ifBlank {
            activity.getString(R.string.excuses_details_no_message)
        }
        b.excuseJustified.text = excuse.justifiedAbsences.toString()

        b.excuseTeachersLabel.isVisible = !excuse.notifiedTeachers.isNullOrBlank()
        b.excuseTeachers.isVisible = !excuse.notifiedTeachers.isNullOrBlank()
        b.excuseTeachers.text = excuse.notifiedTeachers

        b.excusePostDate.text = excuse.postDate.formatDate("d MMMM yyyy, HH:mm")
        b.excuseAttachment.isVisible = excuse.hasAttachment
    }

    /**
     * Turns the stored lesson numbers into "7. Przyroda" lines, using the local timetable
     * and falling back to the attendance entries of that day. Returns null for whole-day
     * excuses, which have no lessons to list.
     */
    private suspend fun resolveLessonNames(): String? {
        val numbers = excuse.lessonNumbers
        if (numbers.isEmpty())
            return null

        val names = withContext(Dispatchers.IO) {
            subjectNamesFor(excuse.dateFrom)
        }
        return numbers.joinToString("\n") { number ->
            val name = names[number]
            if (name.isNullOrBlank())
                activity.getString(R.string.excuses_details_lesson_number_format, number)
            else
                activity.getString(R.string.excuses_editor_lesson_format, number, name)
        }
    }

    private fun subjectNamesFor(date: Date): Map<Int, String?> {
        val fromTimetable = app.db.timetableDao()
            .getAllForDateNow(excuse.profileId, date)
            .mapNotNull { lesson -> lesson.displayLessonNumber?.let { it to lesson.displaySubjectName } }
        val fromAttendance = app.db.attendanceDao()
            .getAllByDateNow(excuse.profileId, date)
            .mapNotNull { attendance -> attendance.lessonNumber?.let { it to attendance.subjectLongName } }
        // timetable wins, attendance fills the gaps
        return (fromAttendance + fromTimetable).toMap()
    }
}
