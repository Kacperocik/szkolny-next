package pl.szczodrzynski.edziennik.ui.timetable

import kotlinx.parcelize.Parcelize
import pl.szczodrzynski.edziennik.data.db.entity.Lesson
import pl.szczodrzynski.edziennik.data.db.full.LessonFull
import pl.szczodrzynski.edziennik.utils.models.Date

@Parcelize
internal data class ShiftedLessonTarget private constructor(
    override val dateValue: Int,
    private val timeValue: Int?,
    private val previousDateValue: Int,
    private val previousTimeValue: Int,
    private val type: Int,
    private val subjectId: Long?,
    private val teamId: Long?,
) : TimetableLessonTarget {
    val date: Date
        get() = Date.fromValue(dateValue)

    override fun findLesson(lessons: List<LessonFull>) = lessons.singleOrNull(::matches)

    fun matches(lesson: LessonFull): Boolean {
        if (lesson.type != type || lesson.displayDate?.value != dateValue)
            return false

        val isSource = type == Lesson.TYPE_SHIFTED_SOURCE
        val previousDate = if (isSource) lesson.date else lesson.oldDate
        val previousTime = if (isSource) lesson.startTime else lesson.oldStartTime
        return (timeValue == null || lesson.displayStartTime?.value == timeValue) &&
            previousDate?.value == previousDateValue &&
            previousTime?.value == previousTimeValue &&
            (subjectId == null || lesson.displaySubjectId == subjectId) &&
            (teamId == null || lesson.displayTeamId == teamId)
    }

    companion object {
        fun from(lesson: LessonFull): ShiftedLessonTarget? {
            val isSource = when (lesson.type) {
                Lesson.TYPE_SHIFTED_SOURCE -> true
                Lesson.TYPE_SHIFTED_TARGET -> false
                else -> return null
            }
            val targetDate = (if (isSource) lesson.date else lesson.oldDate) ?: return null
            val targetTime = if (isSource) lesson.startTime else lesson.oldStartTime
            return ShiftedLessonTarget(
                dateValue = targetDate.value,
                timeValue = targetTime?.value,
                previousDateValue = lesson.displayDate?.value ?: return null,
                previousTimeValue = lesson.displayStartTime?.value ?: return null,
                type = if (isSource) Lesson.TYPE_SHIFTED_TARGET else Lesson.TYPE_SHIFTED_SOURCE,
                subjectId = if (isSource) lesson.subjectId else lesson.oldSubjectId,
                teamId = if (isSource) lesson.teamId else lesson.oldTeamId,
            )
        }
    }
}
