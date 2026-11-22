package pl.szczodrzynski.edziennik.data.api.edziennik.mobidziennik.html

internal data class HtmlGrade(
    val id: String,
    val grade: String?,
    val value: Double?,
    val weight: Double?,
    val category: String?,
    val description: String,
    val countsTowardAverage: Boolean,
    val addedAt: String?,
    val teacher: String?,
)

internal data class HtmlSubjectGrades(val subject: String, val grades: List<HtmlGrade>)

internal data class HtmlExam(
    val date: String,
    val dayAndTime: String,
    val className: String,
    val subject: String,
    val kind: String,
    val topic: String,
    val teacher: String?,
    val addedAt: String?,
)

internal data class HtmlHomework(
    val id: String,
    val studentId: String?,
    val dueDate: String,
    val hours: String,
    val subject: String,
    val teacher: String?,
    val addedAt: String?,
    val body: String,
    val truncated: Boolean,
)

internal data class HtmlMessageRow(
    val id: String,
    val subject: String,
    val attachments: String,
    val sender: String,
    val receivedAt: String,
    val isRead: Boolean,
)

internal data class HtmlMessage(
    val readAt: String?,
    val body: String,
)

internal data class HtmlCalendarEvent(
    val date: String?,
    val type: String,
    val idRaw: String,
    val color: String?,
    val title: String,
    val description: String,
) {
    val studentId: Int?
        get() = idRaw.split(';').getOrNull(1)?.toIntOrNull()?.takeIf { it > 0 }
}

internal data class HtmlSchoolCalendarRow(
    val dueDate: String,
    val name: String,
    val category: String,
    val description: String,
)

internal data class HtmlTimetableDay(val day: String, val date: String?)

internal data class HtmlTimetableLesson(
    val day: String?,
    val date: String?,
    val hours: String,
    val subject: String,
    val details: String,
    val status: String?,
    val note: String,
    val kind: String? = null,
)

internal data class HtmlTimetable(val days: List<HtmlTimetableDay>, val lessons: List<HtmlTimetableLesson>)

internal data class HtmlFinalGrade(val subject: String, val heading: String, val grade: String)
