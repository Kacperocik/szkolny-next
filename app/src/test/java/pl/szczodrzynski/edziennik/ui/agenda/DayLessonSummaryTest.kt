package pl.szczodrzynski.edziennik.ui.agenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.szczodrzynski.edziennik.data.db.entity.Lesson
import pl.szczodrzynski.edziennik.data.db.full.LessonFull
import pl.szczodrzynski.edziennik.utils.models.Time

class DayLessonSummaryTest {
    private fun lesson(id: Long, start: String, end: String, type: Int = Lesson.TYPE_NORMAL) =
        LessonFull(1, id).apply {
            this.type = type
            startTime = Time.fromH_m(start)
            endTime = Time.fromH_m(end)
        }

    @Test
    fun movedLessonDoesNotShortenTheDayOrCountItsOriginalSlotTwice() {
        val lessons = listOf(
            lesson(1, "08:00", "08:45"),
            lesson(2, "08:55", "09:40", Lesson.TYPE_CANCELLED),
            lesson(3, "09:50", "10:35"),
            lesson(4, "10:45", "11:30"),
            lesson(5, "11:45", "12:30"),
            lesson(6, "08:55", "09:40", Lesson.TYPE_SHIFTED_SOURCE).apply {
                oldStartTime = Time.fromH_m("12:40")
                oldEndTime = Time.fromH_m("13:25")
            },
            lesson(7, "08:55", "09:40", Lesson.TYPE_SHIFTED_TARGET).apply {
                oldStartTime = Time.fromH_m("12:40")
                oldEndTime = Time.fromH_m("13:25")
            },
        )

        val summary = requireNotNull(summarizeDayLessons(lessons))
        assertEquals("8:00", summary.startTime.stringHM)
        assertEquals("12:30", summary.endTime.stringHM)
        assertEquals(5, summary.lessonCount)
        assertEquals("4:30", Time.diff(summary.startTime, summary.endTime).stringHM)
        assertEquals(summary, summarizeDayLessons(lessons.reversed()))
    }

    @Test
    fun rangeDoesNotDependOnDatabaseIdOrderOrTheLastLessonsEndTime() {
        val summary = requireNotNull(summarizeDayLessons(listOf(
            lesson(1, "10:00", "13:00"),
            lesson(2, "08:00", "08:45"),
            lesson(3, "11:00", "11:45"),
        )))
        assertEquals("8:00", summary.startTime.stringHM)
        assertEquals("13:00", summary.endTime.stringHM)
        assertEquals(3, summary.lessonCount)
    }

    @Test
    fun cancelledLessonsAtEitherEndDoNotExtendTheDay() {
        val summary = requireNotNull(summarizeDayLessons(listOf(
            lesson(1, "08:00", "08:45", Lesson.TYPE_CANCELLED),
            lesson(2, "09:50", "10:35"),
            lesson(3, "15:00", "15:45", Lesson.TYPE_CANCELLED),
        )))
        assertEquals("9:50", summary.startTime.stringHM)
        assertEquals("10:35", summary.endTime.stringHM)
        assertEquals(1, summary.lessonCount)
    }

    @Test
    fun missingCurrentTimesUseTheSameFallbackAsTheTimetable() {
        val changedLesson = LessonFull(1, 1).apply {
            type = Lesson.TYPE_CHANGE
            oldStartTime = Time.fromH_m("09:50")
            oldEndTime = Time.fromH_m("10:35")
        }
        val summary = requireNotNull(summarizeDayLessons(listOf(changedLesson)))
        assertEquals("9:50", summary.startTime.stringHM)
        assertEquals("10:35", summary.endTime.stringHM)
        assertEquals(1, summary.lessonCount)
    }

    @Test
    fun emptyOrFullyCancelledDayHasNoLessonSummary() {
        assertNull(summarizeDayLessons(emptyList()))
        assertNull(summarizeDayLessons(listOf(
            lesson(1, "08:00", "08:45", Lesson.TYPE_CANCELLED),
            lesson(2, "08:55", "09:40", Lesson.TYPE_SHIFTED_SOURCE),
            lesson(3, "00:00", "23:59", Lesson.TYPE_NO_LESSONS),
        )))
    }

    @Test
    fun missingOrInvalidTimesDoNotProduceAnInventedTimeRange() {
        assertNull(summarizeDayLessons(listOf(LessonFull(1, 1))))
        assertNull(summarizeDayLessons(listOf(lesson(1, "10:00", "09:00"))))
    }
}
