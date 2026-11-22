package pl.szczodrzynski.edziennik.ui.timetable

import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import pl.szczodrzynski.edziennik.data.db.entity.Event
import pl.szczodrzynski.edziennik.data.db.full.LessonFull
import pl.szczodrzynski.edziennik.utils.models.Date
import pl.szczodrzynski.edziennik.utils.models.Time

class EventLessonTargetTest {
    private val date = Date(2026, 11, 23)
    private val time = Time(8, 55, 0)

    @Test
    fun selectsTheLessonsGroupWhenTwoLessonsStartTogether() {
        val otherGroup = lesson(1).apply { teamId = 11 }
        val matching = lesson(2).apply { teamId = 12 }
        val target = requireNotNull(EventLessonTarget.from(event().apply { teamId = 12 }))

        assertSame(matching, target.findLesson(listOf(otherGroup, matching)))
    }

    @Test
    fun wholeClassEventCanMatchALessonForASubgroup() {
        val otherTeacher = lesson(1).apply { teacherId = 7 }
        val matching = lesson(2).apply { teacherId = 8 }
        val target = requireNotNull(EventLessonTarget.from(event().apply {
            teamId = 99
            teacherId = 8
        }))

        assertSame(matching, target.findLesson(listOf(otherTeacher, matching)))
    }

    @Test
    fun eventWithoutATimeRequiresAnUnambiguousSubjectMatch() {
        val target = requireNotNull(EventLessonTarget.from(event().apply { time = null }))
        val matching = lesson(1)
        val differentSubject = lesson(2).apply { subjectId = 6 }
        val laterLesson = lesson(3).apply { startTime = Time(10, 45, 0) }

        assertSame(matching, target.findLesson(listOf(differentSubject, matching)))
        assertNull(target.findLesson(listOf(matching, laterLesson)))
        assertNull(EventLessonTarget.from(event().apply {
            time = Time(0, 0, 0)
            subjectId = -1
        }))
    }

    private fun event() = Event(1, 1, date, time, "", null, Event.TYPE_EXAM, -1, 5, -1)

    private fun lesson(id: Long) = LessonFull(1, id).apply {
        date = this@EventLessonTargetTest.date
        startTime = time
        subjectId = 5
        teamId = 11
    }
}
