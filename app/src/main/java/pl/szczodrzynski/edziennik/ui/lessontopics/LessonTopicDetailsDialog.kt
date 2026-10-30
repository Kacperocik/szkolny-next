package pl.szczodrzynski.edziennik.ui.lessontopics

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.gson.JsonParser
import com.google.android.material.button.MaterialButton
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.db.entity.LessonTopic
import pl.szczodrzynski.edziennik.databinding.LessonTopicDetailsBinding
import pl.szczodrzynski.edziennik.ext.getString
import pl.szczodrzynski.edziennik.ui.base.dialog.BindingDialog
import pl.szczodrzynski.edziennik.ui.teachers.bindTeacher
import pl.szczodrzynski.edziennik.ui.timetable.LessonDetailsDialog
import pl.szczodrzynski.edziennik.utils.models.Date

class LessonTopicDetailsDialog(activity: AppCompatActivity, private val item: LessonTopic) : BindingDialog<LessonTopicDetailsBinding>(activity) {
    override fun getTitleRes() = R.string.lesson_topics_details_title
    override fun inflate(layoutInflater: LayoutInflater) = LessonTopicDetailsBinding.inflate(layoutInflater)
    override fun getPositiveButtonText() = R.string.close
    override fun hasCloseIconWhenSpaceLimited() = true
    override suspend fun onShow() {
        b.heading.text = item.subjectName
        b.lessonDate.text = listOf(Date.fromY_m_d(item.date).formattedString,
            activity.getString(R.string.lesson_topics_lesson_format, item.lessonNumber)).joinToString(" • ")
        b.topicLabel.setText(if (item.planned) R.string.lesson_topics_planned_label else R.string.lesson_topics_realized_label)
        b.topic.text = item.topic.ifBlank { activity.getString(R.string.lesson_topics_no_topic) }
        b.teacherCard.bindTeacher(item.teacherId, item.teacherName, ::dismiss)
        b.cancelled.isVisible = item.cancelled
        val lessonAndAttendance = withContext(Dispatchers.IO) {
            val date = Date.fromY_m_d(item.date)
            val lesson = app.db.timetableDao().getAllForDateNow(item.profileId, date)
                .singleOrNull { item.matches(it) }
            lesson?.let {
                it.filterNotes()
                val attendance = app.db.attendanceDao().getAllByDateNow(item.profileId, date)
                    .firstOrNull { attendance -> it.startTime != null && attendance.startTime == it.startTime }
                it to attendance
            }
        }
        b.lessonDetails.isVisible = lessonAndAttendance != null
        b.lessonDetails.setOnClickListener {
            lessonAndAttendance?.let { (lesson, attendance) ->
                LessonDetailsDialog(activity, lesson, attendance, showTopics = false).show()
            }
        }
        JsonParser.parseString(item.links).asJsonArray.forEach { element ->
            val link = element.asJsonObject
            val url = link.getString("Url") ?: return@forEach
            if (Uri.parse(url).scheme !in listOf("https", "http")) return@forEach
            b.links.addView(MaterialButton(activity, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = link.getString("Name") ?: url
                setOnClickListener { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            })
        }
        if (JsonParser.parseString(item.files).asJsonArray.size() > 0) b.links.addView(TextView(activity).apply {
            setText(R.string.lesson_topics_files)
        })
    }
}
