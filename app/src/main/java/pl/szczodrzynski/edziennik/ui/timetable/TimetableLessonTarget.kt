package pl.szczodrzynski.edziennik.ui.timetable

import android.os.Parcelable
import pl.szczodrzynski.edziennik.data.db.full.LessonFull

internal sealed interface TimetableLessonTarget : Parcelable {
    val dateValue: Int

    fun findLesson(lessons: List<LessonFull>): LessonFull?
}
