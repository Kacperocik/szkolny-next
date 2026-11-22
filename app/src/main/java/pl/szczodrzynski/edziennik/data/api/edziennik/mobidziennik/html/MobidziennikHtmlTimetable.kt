package pl.szczodrzynski.edziennik.data.api.edziennik.mobidziennik.html

import android.util.SparseArray
import pl.szczodrzynski.edziennik.data.api.ERROR_MOBIDZIENNIK_WEB_INVALID_RESPONSE
import pl.szczodrzynski.edziennik.data.api.edziennik.mobidziennik.DataMobidziennik
import pl.szczodrzynski.edziennik.data.api.edziennik.mobidziennik.data.MobidziennikWeb
import pl.szczodrzynski.edziennik.data.api.models.DataRemoveModel
import pl.szczodrzynski.edziennik.data.db.entity.Lesson
import pl.szczodrzynski.edziennik.data.db.entity.LessonRange
import pl.szczodrzynski.edziennik.data.db.entity.Metadata
import pl.szczodrzynski.edziennik.data.db.entity.SYNC_ALWAYS
import pl.szczodrzynski.edziennik.data.enums.MetadataType
import pl.szczodrzynski.edziennik.ext.getString
import pl.szczodrzynski.edziennik.ext.keys
import pl.szczodrzynski.edziennik.utils.models.Date
import pl.szczodrzynski.edziennik.utils.models.Time
import pl.szczodrzynski.edziennik.utils.models.Week

class MobidziennikHtmlTimetable(
    override val data: DataMobidziennik,
    override val lastSync: Long?,
    val onSuccess: (endpointId: Int) -> Unit,
) : MobidziennikWeb(data, lastSync) {
    companion object {
        private const val TAG = "MobidziennikHtmlTimetable"
        private val roomRegex = Regex("\\(([^)]*)\\)")

        fun parseDetails(details: String): Triple<String?, String?, String?> {
            val classroom = roomRegex.find(details)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
            val tokens = details.replace(roomRegex, " ").split("|").map { HtmlParsers.clean(it) }.filter { it.isNotEmpty() }
            val withTeacher = tokens.firstOrNull { " - " in it }
            return if (withTeacher != null)
                Triple(withTeacher.substringBefore(" - ").trim(), withTeacher.substringAfter(" - ").trim(), classroom)
            else
                Triple(null, null, classroom)
        }
    }

    private val requestedWeek = data.arguments.getString("weekStart")?.let { Date.fromY_m_d(it).weekStart }
    private val weekStart: Date = requestedWeek ?: Week.getWeekStart()
    private val weekEnd: Date = weekStart.clone().stepForward(0, 0, if (requestedWeek == null) 13 else 6)
    private val lessons = mutableListOf<Lesson>()
    private val daysWithLessons = mutableSetOf<Int>()
    private val ranges = SparseArray<Time>()

    init {
        if (data.profile == null)
            onSuccess(ENDPOINT_MOBIDZIENNIK_HTML_TIMETABLE)
        else {
            val weeks = if (requestedWeek == null) listOf(weekStart, weekStart.clone().stepForward(0, 0, 7))
                else listOf(weekStart)
            sync(weeks.iterator())
        }
    }

    private fun sync(weeks: Iterator<Date>) {
        if (!weeks.hasNext()) {
            finish()
            return
        }
        val monday = weeks.next()
        webGet(TAG, "/dziennik/planlekcji?typ=podstawowy&tydzien=${monday.stringY_m_d}") { text ->
            val timetable = HtmlParsers.parseTimetable(text)
            val dates = timetable.days.mapNotNull { lessonDate(it.date, monday) }
            if (dates.isEmpty()) {
                data.error(TAG, ERROR_MOBIDZIENNIK_WEB_INVALID_RESPONSE)
                return@webGet
            }
            for (lesson in timetable.lessons) {
                val date = lessonDate(lesson.date, monday) ?: continue
                addLesson(lesson, date)
            }
            sync(weeks)
        }
    }

    private fun lessonDate(text: String?, monday: Date): Date? {
        val parts = HtmlText.parseDate(text) ?: return null
        val sunday = monday.clone().stepForward(0, 0, 6)
        val year = if (parts.month == monday.month) monday.year else sunday.year
        val date = parts.withFallbackYear(year) ?: return null
        return Date(date.year, date.monthValue, date.dayOfMonth).takeIf { it >= monday && it <= sunday }
    }

    private fun addLesson(l: HtmlTimetableLesson, date: Date) {
        val profile = data.profile ?: return
        val (startHms, endHms) = HtmlText.parseTimeRange(l.hours) ?: return
        val start = htmlTime(startHms) ?: return
        val end = htmlTime(endHms) ?: return
        val subject = data.htmlSubject(l.subject)
        val (_, teacherName, classroom) = parseDetails(l.details)
        val teacher = data.htmlTeacher(teacherName)
        val team = data.htmlTeamClass()

        val note = "${l.note} ${l.status ?: ""}".lowercase()
        val isRoomChange = "zmiana sali" in l.note.lowercase()
        val type = when {
            "odwo" in note || l.kind == "lo" -> Lesson.TYPE_CANCELLED
            isRoomChange || "zast" in note || note.trim().startsWith("zmiana") || l.kind == "lz" ->
                Lesson.TYPE_CHANGE
            else -> Lesson.TYPE_NORMAL
        }

        val lesson = Lesson(data.profileId, -1).also {
            if (type == Lesson.TYPE_CANCELLED) {
                it.type = type
                it.oldDate = date
                it.oldLessonNumber = start.value
                it.oldStartTime = start
                it.oldEndTime = end
                it.oldSubjectId = subject?.id ?: -1
                it.oldTeamId = team.id
            } else {
                it.type = type
                it.date = date
                it.lessonNumber = start.value
                it.startTime = start
                it.endTime = end
                it.subjectId = subject?.id ?: -1
                it.teacherId = teacher?.id ?: -1
                it.teamId = team.id
                it.classroom = classroom
                if (isRoomChange) {
                    it.oldSubjectId = subject?.id
                    it.oldTeacherId = teacher?.id
                    it.oldTeamId = team.id
                }
            }
            it.id = it.buildId()
            it.ownerId = it.buildOwnerId()
        }
        daysWithLessons += date.value
        ranges[start.value] = end

        if (type != Lesson.TYPE_NORMAL) {
            val seen = profile.empty || date < Date.getToday()
            data.metadataList.add(Metadata(data.profileId, MetadataType.LESSON_CHANGE, lesson.id, seen, seen))
        }
        lessons += lesson
    }

    private fun finish() {
        val profile = data.profile
        if (profile != null) {
            data.toRemove.add(DataRemoveModel.Timetable.between(weekStart.clone(), weekEnd.clone(), isExtra = false))

            val day = weekStart.clone()
            while (day <= weekEnd) {
                if (day.value !in daysWithLessons) {
                    lessons += Lesson(data.profileId, day.value.toLong()).apply {
                        type = Lesson.TYPE_NO_LESSONS
                        date = day.clone()
                    }
                }
                day.stepForward(0, 0, 1)
            }

            ranges.keys().sorted().forEachIndexed { index, startValue ->
                lessons.forEach {
                    if (it.lessonNumber == startValue) it.lessonNumber = index + 1
                    if (it.oldLessonNumber == startValue) it.oldLessonNumber = index + 1
                }
                data.lessonRanges[index + 1] = LessonRange(
                    profileId = profile.id,
                    lessonNumber = index + 1,
                    startTime = Time.fromValue(startValue),
                    endTime = ranges[startValue],
                )
            }
            data.lessonList.addAll(lessons)
        }
        if (requestedWeek == null)
            data.setSyncNext(ENDPOINT_MOBIDZIENNIK_HTML_TIMETABLE, SYNC_ALWAYS)
        onSuccess(ENDPOINT_MOBIDZIENNIK_HTML_TIMETABLE)
    }
}
