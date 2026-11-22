package pl.szczodrzynski.edziennik.ui.timetable

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.szczodrzynski.edziennik.data.db.entity.Lesson
import pl.szczodrzynski.edziennik.data.db.full.LessonFull
import pl.szczodrzynski.edziennik.utils.models.Date
import pl.szczodrzynski.edziennik.utils.models.Time

class ShiftedLessonTargetTest {
    @Test
    fun sameDayMoveMatchesTheOtherSlotInBothDirections() {
        val original = lesson(Lesson.TYPE_SHIFTED_SOURCE)
        val moved = lesson(Lesson.TYPE_SHIFTED_TARGET)
        val forward = requireNotNull(ShiftedLessonTarget.from(original))
        val backward = requireNotNull(ShiftedLessonTarget.from(moved))

        assertTrue(forward.matches(moved))
        assertTrue(backward.matches(original))
        assertFalse(forward.matches(original))
        assertFalse(backward.matches(moved))
    }

    @Test
    fun anotherGroupOrAnotherOriginalSlotDoesNotMatchTheTarget() {
        val target = requireNotNull(ShiftedLessonTarget.from(lesson(Lesson.TYPE_SHIFTED_SOURCE)))

        assertFalse(target.matches(lesson(Lesson.TYPE_SHIFTED_TARGET).apply { teamId = 9 }))
        assertFalse(target.matches(lesson(Lesson.TYPE_SHIFTED_TARGET).apply { oldStartTime = Time(9, 50, 0) }))
    }

    private fun lesson(type: Int) = LessonFull(1, type.toLong()).apply {
        this.type = type
        date = Date(2026, 11, 23)
        startTime = Time(10, 45, 0)
        subjectId = 4
        teamId = 5
        oldDate = Date(2026, 11, 23)
        oldStartTime = Time(8, 55, 0)
        oldSubjectId = 4
        oldTeamId = 5
    }
}
