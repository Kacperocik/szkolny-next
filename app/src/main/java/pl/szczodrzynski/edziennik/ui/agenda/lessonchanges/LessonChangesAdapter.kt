/*
 * Copyright (c) Kuba Szczodrzyński 2019-12-19.
 */

package pl.szczodrzynski.edziennik.ui.agenda.lessonchanges

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.core.manager.NoteManager
import pl.szczodrzynski.edziennik.data.db.full.LessonFull
import pl.szczodrzynski.edziennik.databinding.TimetableLessonBinding
import pl.szczodrzynski.edziennik.ext.asColoredSpannable
import pl.szczodrzynski.edziennik.ext.asStrikethroughSpannable
import pl.szczodrzynski.edziennik.ext.concat
import pl.szczodrzynski.edziennik.ext.dp
import pl.szczodrzynski.edziennik.ext.enableExpressiveCardShapeMorph
import pl.szczodrzynski.edziennik.ext.listOfNotEmpty
import pl.szczodrzynski.edziennik.ext.onClick
import pl.szczodrzynski.edziennik.ext.resolveAttr
import pl.szczodrzynski.edziennik.ui.timetable.bindLessonAppearance

class LessonChangesAdapter(
        val context: Context,
        private val showNotes: Boolean = true,
        private val onLessonClick: ((lesson: LessonFull) -> Unit)? = null
) : RecyclerView.Adapter<LessonChangesAdapter.ViewHolder>() {

    var items = listOf<LessonFull>()

    private val arrowRight = " → "
    private val bullet = " • "
    private val app = context.applicationContext as App
    private val colorSecondary = android.R.attr.textColorSecondary.resolveAttr(context)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = TimetableLessonBinding.inflate(inflater, parent, false)
        if (onLessonClick != null)
            view.lessonCard.enableExpressiveCardShapeMorph()
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val lesson = items[position]
        val b = holder.b

        if (onLessonClick != null) {
            b.lessonCard.onClick {
                onLessonClick.invoke(lesson)
            }
        }

        val startTime = lesson.displayStartTime
        val endTime = lesson.displayEndTime
        val timeRange = if (startTime != null && endTime != null)
            "${startTime.stringHM} - ${endTime.stringHM}".asColoredSpannable(colorSecondary)
        else
            ""

        b.unread = false
        b.root.background = null

        b.root.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = 16.dp
        }

        // teacher
        val teacherInfo = if (lesson.teacherId != null && lesson.teacherId == lesson.oldTeacherId)
            lesson.teacherName ?: "?"
        else
            mutableListOf<CharSequence>().apply {
                lesson.oldTeacherName?.let { add(it.asStrikethroughSpannable()) }
                lesson.teacherName?.let { add(it) }
            }.concat(arrowRight)

        // team
        val teamInfo = if (lesson.teamId != null && lesson.teamId == lesson.oldTeamId)
            lesson.teamName ?: "?"
        else
            mutableListOf<CharSequence>().apply {
                lesson.oldTeamName?.let { add(it.asStrikethroughSpannable()) }
                lesson.teamName?.let { add(it) }
            }.concat(arrowRight)

        // classroom
        val classroomInfo = if (lesson.classroom != null && lesson.classroom == lesson.oldClassroom)
            lesson.classroom ?: "?"
        else
            mutableListOf<CharSequence>().apply {
                lesson.oldClassroom?.let { add(it.asStrikethroughSpannable()) }
                lesson.classroom?.let { add(it) }
            }.concat(arrowRight)

        b.annotationVisible = app.timetableManager.getAnnotation(context, lesson, b.annotation)
        val lessonText = lesson.getNoteSubstituteText(showNotes) ?: lesson.displaySubjectName
        b.bindLessonAppearance(app, lesson, lessonText)
        b.detailsFirst.text = listOfNotEmpty(timeRange, classroomInfo).concat(bullet)
        b.detailsSecond.text = listOfNotEmpty(teacherInfo, teamInfo).concat(bullet)

        if (showNotes)
            NoteManager.prependIcon(lesson, b.subjectName)

        b.executePendingBindings()
    }

    override fun getItemCount() = items.size

    class ViewHolder(val b: TimetableLessonBinding) : RecyclerView.ViewHolder(b.root)
}
