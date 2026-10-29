package pl.szczodrzynski.edziennik.ui.excuses

import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.checkbox.MaterialCheckBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.api.edziennik.EdziennikTask
import pl.szczodrzynski.edziennik.data.api.events.ApiTaskAllFinishedEvent
import pl.szczodrzynski.edziennik.data.api.events.ApiTaskErrorEvent
import pl.szczodrzynski.edziennik.data.api.events.ExcuseSentEvent
import pl.szczodrzynski.edziennik.data.db.full.AttendanceFull
import pl.szczodrzynski.edziennik.data.enums.LoginType
import pl.szczodrzynski.edziennik.databinding.ExcuseEditorDialogBinding
import pl.szczodrzynski.edziennik.ext.moveButtonsToTitleWhenSpaceLimited
import pl.szczodrzynski.edziennik.ui.base.dialog.BindingDialog
import pl.szczodrzynski.edziennik.utils.TextInputDropDown
import pl.szczodrzynski.edziennik.utils.models.Date

/**
 * Composes and sends a Librus e-Usprawiedliwienie.
 *
 * Mirrors the two scopes Librus itself offers: whole days (a single day or a range),
 * or selected lessons of one day.
 *
 * The message is intentionally optional - Librus accepts excuses with no text at all.
 */
class ExcuseEditorDialog(
    activity: AppCompatActivity,
    private val attendance: AttendanceFull? = null,
    private val profileId: Int = App.profileId,
) : BindingDialog<ExcuseEditorDialogBinding>(activity) {

    companion object {
        private const val SCOPE_DAYS = 0L
        private const val SCOPE_LESSONS = 1L
    }

    override fun getTitleRes() = R.string.excuses_editor_title
    override fun inflate(layoutInflater: LayoutInflater) =
        ExcuseEditorDialogBinding.inflate(layoutInflater)

    override fun isCancelable() = false
    override fun getPositiveButtonText() = R.string.excuses_editor_send
    override fun getNegativeButtonText() = R.string.cancel

    private var pendingRequest: EdziennikTask.ExcuseSendRequest? = null
    private var lessonsDate: Date? = null

    private fun setSending(sending: Boolean) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = !sending
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = !sending
        b.sendingProgress.isVisible = sending
        b.scopeDropdown.isEnabled = !sending
        b.dateFromDropdown.isEnabled = !sending
        b.dateToDropdown.isEnabled = !sending
        b.messageEdit.isEnabled = !sending
        lessonCheckBoxes.forEach { it.isEnabled = !sending }
        if (sending) b.notifyCheckBox.isEnabled = false else updateNotifyState()
    }

    override suspend fun onNegativeClick() = if (pendingRequest == null) DISMISS else NO_DISMISS

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onExcuseSent(event: ExcuseSentEvent) {
        if (event.profileId != profileId || pendingRequest == null || event.excuse == null) return
        val request = pendingRequest ?: return
        if (event.excuse.dateFrom != request.dateFrom || event.excuse.dateTo != request.dateTo ||
            event.excuse.lessonNumbers != request.lessons || event.excuse.message != request.message) return
        pendingRequest = null
        Toast.makeText(activity, R.string.excuses_sent, Toast.LENGTH_LONG).show()
        dismiss()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onSendError(event: ApiTaskErrorEvent) {
        if (pendingRequest == null || event.request != pendingRequest || event.error.profileId != profileId) return
        if (!event.error.isCritical) return
        pendingRequest = null
        setSending(false)
        b.sendError.text = event.error.getStringReason(activity)
        b.sendError.isVisible = true
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onQueueFinished(@Suppress("UNUSED_PARAMETER") event: ApiTaskAllFinishedEvent) {
        if (pendingRequest == null) return
        pendingRequest = null
        setSending(false)
        b.sendError.setText(R.string.excuses_send_failed)
        b.sendError.isVisible = true
    }

    private val lessonCheckBoxes = mutableListOf<MaterialCheckBox>()

    private val isLessonScope
        get() = b.scopeDropdown.selectedId == SCOPE_LESSONS

    override suspend fun onBeforeShow(): Boolean {
        val profile = withContext(Dispatchers.IO) { app.db.profileDao().getByIdNow(profileId) }
        return profile?.loginStoreType == LoginType.LIBRUS &&
            profile.isParent && !profile.archived && !profile.config.librusExcusesUnavailable
    }

    override suspend fun onShow() {
        dialog.moveButtonsToTitleWhenSpaceLimited()
        b.scopeDropdown.items = mutableListOf(
            TextInputDropDown.Item(SCOPE_DAYS, activity.getString(R.string.excuses_scope_days)),
            TextInputDropDown.Item(SCOPE_LESSONS, activity.getString(R.string.excuses_scope_lessons)),
        )

        for (dropdown in listOf(b.dateFromDropdown, b.dateToDropdown)) {
            dropdown.db = app.db
            dropdown.profileId = this@ExcuseEditorDialog.profileId
            dropdown.showWeekDays = false
            dropdown.showDays = true
            dropdown.showOtherDate = true
            dropdown.loadItems()
        }

        val startDate = attendance?.date ?: Date.getToday()
        b.dateFromDropdown.selectDate(startDate)
        b.dateToDropdown.selectDate(startDate)

        b.dateFromDropdown.onDateSelected = { date, _ ->
            if (!isLessonScope && (b.dateToDropdown.getSelected() as? Date)?.let { it < date } == true)
                b.dateToDropdown.selectDate(date)
            updateNotifyState()
            if (isLessonScope)
                launch { loadLessons(date) }
        }
        b.dateToDropdown.onDateSelected = { _, _ -> updateNotifyState() }

        b.scopeDropdown.select(if (attendance != null) SCOPE_LESSONS else SCOPE_DAYS)
        b.scopeDropdown.setOnChangeListener { item ->
            applyScope(item.id == SCOPE_LESSONS)
            true
        }
        applyScope(attendance != null)
    }

    private fun applyScope(lessonScope: Boolean) {
        b.dateToLayout.isVisible = !lessonScope
        b.lessonsLabel.isVisible = lessonScope
        b.lessonsContainer.isVisible = lessonScope
        updateNotifyState()
        if (lessonScope) {
            val date = b.dateFromDropdown.getSelected() as? Date ?: Date.getToday()
            launch { loadLessons(date) }
        } else {
            b.lessonsEmpty.isVisible = false
        }
    }

    /**
     * Builds the lesson checkboxes from the local timetable, falling back to the
     * attendance entries of that day when no timetable has been downloaded.
     */
    private suspend fun loadLessons(date: Date) {
        lessonsDate = null
        lessonCheckBoxes.clear()
        b.lessonsContainer.removeAllViews()
        val lessons = withContext(Dispatchers.IO) {
            val timetable = app.db.timetableDao()
                .getAllForDateNow(profileId, date)
                .mapNotNull { lesson ->
                    lesson.displayLessonNumber?.let { it to lesson.displaySubjectName }
                }
            timetable.ifEmpty {
                app.db.attendanceDao()
                    .getAllByDateNow(profileId, date)
                    .mapNotNull { it.lessonNumber?.let { no -> no to it.subjectLongName } }
            }
        }.distinctBy { it.first }.sortedBy { it.first }

        if (!isLessonScope || b.dateFromDropdown.getSelected() != date) return
        lessonsDate = date
        b.lessonsContainer.removeAllViews()
        lessonCheckBoxes.clear()
        b.lessonsEmpty.isVisible = lessons.isEmpty()

        for ((lessonNumber, subjectName) in lessons) {
            val checkBox = MaterialCheckBox(activity).apply {
                text = activity.getString(
                    R.string.excuses_editor_lesson_format,
                    lessonNumber,
                    subjectName ?: "",
                ).trim()
                tag = lessonNumber
                isChecked = attendance?.date == date && attendance.lessonNumber == lessonNumber
            }
            lessonCheckBoxes += checkBox
            b.lessonsContainer.addView(checkBox)
        }
    }

    /** Librus only allows notifying teachers about absences that have not happened yet. */
    private fun updateNotifyState() {
        val date = (if (isLessonScope) b.dateFromDropdown.getSelected() else b.dateToDropdown.getSelected())
            as? Date ?: Date.getToday()
        val future = date >= Date.getToday()
        b.notifyCheckBox.isEnabled = future
        if (!future)
            b.notifyCheckBox.isChecked = false
    }

    override suspend fun onPositiveClick(): Boolean {
        val dateFrom = b.dateFromDropdown.getSelected() as? Date
        if (dateFrom == null) {
            Toast.makeText(activity, R.string.excuses_editor_no_date, Toast.LENGTH_SHORT).show()
            return NO_DISMISS
        }

        b.sendError.isVisible = false
        val lessonScope = isLessonScope
        if (lessonScope && lessonsDate != dateFrom) return NO_DISMISS
        val dateTo = if (lessonScope) dateFrom else b.dateToDropdown.getSelected() as? Date ?: dateFrom

        if (dateTo < dateFrom) {
            Toast.makeText(activity, R.string.excuses_editor_bad_range, Toast.LENGTH_SHORT).show()
            return NO_DISMISS
        }

        val lessons = when {
            lessonScope -> lessonCheckBoxes.filter { it.isChecked }.mapNotNull { it.tag as? Int }
            else -> emptyList()
        }
        if (lessonScope && lessons.isEmpty()) {
            Toast.makeText(activity, R.string.excuses_editor_no_lessons, Toast.LENGTH_SHORT).show()
            return NO_DISMISS
        }

        // NOTE: the message is deliberately not validated - Librus allows sending it empty
        val message = b.messageEdit.text?.toString() ?: ""
        val sendNotify = b.notifyCheckBox.isEnabled && b.notifyCheckBox.isChecked

        if (pendingRequest != null) return NO_DISMISS
        val task = EdziennikTask.excuseSend(profileId, dateFrom, dateTo, lessons, message, sendNotify)
        pendingRequest = task.request as EdziennikTask.ExcuseSendRequest
        setSending(true)
        task.enqueue(activity)
        return NO_DISMISS
    }
}
