package pl.szczodrzynski.edziennik.ui.agenda

import pl.szczodrzynski.edziennik.data.db.entity.Lesson
import pl.szczodrzynski.edziennik.data.db.full.LessonFull
import pl.szczodrzynski.edziennik.utils.models.Time

internal data class DayLessonSummary(
    val startTime: Time,
    val endTime: Time,
    val lessonCount: Int,
)

internal fun summarizeDayLessons(lessons: List<LessonFull>): DayLessonSummary? {
    val actualLessons = lessons.filter { it.type != Lesson.TYPE_NO_LESSONS && !it.isCancelled }
    val startTime = actualLessons.mapNotNull { it.displayStartTime }.minOrNull() ?: return null
    val endTime = actualLessons.mapNotNull { it.displayEndTime }.maxOrNull() ?: return null
    if (endTime < startTime) return null

    return DayLessonSummary(startTime, endTime, actualLessons.size)
}
