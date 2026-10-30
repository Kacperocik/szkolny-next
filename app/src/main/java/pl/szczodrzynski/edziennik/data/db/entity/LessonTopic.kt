package pl.szczodrzynski.edziennik.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import pl.szczodrzynski.edziennik.data.db.full.LessonFull

@Entity(tableName = "lessonTopics", primaryKeys = ["profileId", "planned", "id"],
    indices = [Index(value = ["profileId", "date"])])
data class LessonTopic(
    val profileId: Int,
    val planned: Boolean,
    val id: String,
    val date: String,
    val lessonNumber: Int,
    val subjectId: Long,
    val subjectName: String,
    val teacherId: Long,
    val teacherName: String,
    val teamId: Long?,
    val topic: String,
    val cancelled: Boolean,
    val links: String,
    val files: String,
) {
    fun matches(lesson: LessonFull): Boolean =
        profileId == lesson.profileId && !lesson.isExtra && !lesson.isCancelled && !cancelled &&
            date == lesson.displayDate?.stringY_m_d && lessonNumber == lesson.displayLessonNumber &&
            subjectId == lesson.displaySubjectId && teacherId == lesson.displayTeacherId &&
            (teamId == null || teamId == lesson.displayTeamId)
}
