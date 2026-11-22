package pl.szczodrzynski.edziennik.ui.timetable

import kotlinx.parcelize.Parcelize
import pl.szczodrzynski.edziennik.data.db.entity.Event
import pl.szczodrzynski.edziennik.data.db.full.LessonFull
import pl.szczodrzynski.edziennik.ext.takePositive

@Parcelize
internal data class EventLessonTarget private constructor(
    override val dateValue: Int,
    private val startMinute: Int?,
    private val subjectId: Long?,
    private val teamId: Long?,
    private val teacherId: Long?,
) : TimetableLessonTarget {

    override fun findLesson(lessons: List<LessonFull>): LessonFull? {
        val candidates = lessons.filter { lesson ->
            lesson.displayDate?.value == dateValue &&
                (startMinute == null || lesson.displayStartTime?.inMinutes == startMinute) &&
                (subjectId == null || lesson.displaySubjectId == subjectId)
        }
        if (candidates.size <= 1)
            return candidates.singleOrNull()

        // An event can name the entire class even when the lesson is for a subgroup.
        val matchingTeam = teamId?.let { id -> candidates.filter { it.displayTeamId == id } }
            ?.takeIf { it.isNotEmpty() } ?: candidates
        val matchingTeacher = teacherId?.let { id -> matchingTeam.filter { it.displayTeacherId == id } }
            ?.takeIf { it.isNotEmpty() } ?: matchingTeam
        return matchingTeacher.singleOrNull()
    }

    companion object {
        fun from(event: Event): EventLessonTarget? {
            val startMinute = event.time?.inMinutes?.takeIf { it > 0 }
            val subjectId = event.subjectId.takePositive()
            if (startMinute == null && subjectId == null)
                return null

            return EventLessonTarget(
                dateValue = event.date.value,
                startMinute = startMinute,
                subjectId = subjectId,
                teamId = event.teamId.takePositive(),
                teacherId = event.teacherId.takePositive(),
            )
        }
    }
}
