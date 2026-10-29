package pl.szczodrzynski.edziennik.data.api.edziennik.librus.data.api

import com.google.gson.JsonObject
import pl.szczodrzynski.edziennik.data.api.ERROR_LIBRUS_API_EXTRA_TIMETABLE_NOT_AVAILABLE
import pl.szczodrzynski.edziennik.data.api.EXCEPTION_LIBRUS_API_REQUEST
import pl.szczodrzynski.edziennik.data.api.LIBRUS_API_3_URL
import pl.szczodrzynski.edziennik.data.api.edziennik.librus.DataLibrus
import pl.szczodrzynski.edziennik.data.api.edziennik.librus.ENDPOINT_LIBRUS_API_EXTRA_TIMETABLES
import pl.szczodrzynski.edziennik.data.api.edziennik.librus.data.LibrusApi
import pl.szczodrzynski.edziennik.data.api.models.ApiError
import pl.szczodrzynski.edziennik.data.api.models.DataRemoveModel
import pl.szczodrzynski.edziennik.data.db.entity.Lesson
import pl.szczodrzynski.edziennik.data.db.entity.LessonRange
import pl.szczodrzynski.edziennik.data.db.entity.SYNC_ALWAYS
import pl.szczodrzynski.edziennik.ext.MINUTE
import pl.szczodrzynski.edziennik.ext.WEEK
import pl.szczodrzynski.edziennik.ext.getJsonArray
import pl.szczodrzynski.edziennik.ext.getJsonObject
import pl.szczodrzynski.edziennik.ext.getString
import pl.szczodrzynski.edziennik.utils.models.Date
import pl.szczodrzynski.edziennik.utils.models.Time
import pl.szczodrzynski.edziennik.utils.models.Week
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.GregorianCalendar
import java.util.Locale

class LibrusApiExtraTimetables(
    override val data: DataLibrus,
    override val lastSync: Long?,
    val onSuccess: (endpointId: Int) -> Unit,
) : LibrusApi(data, lastSync) {
    companion object {
        const val TAG = "LibrusApiExtraTimetables"
    }

    private var retryIn = SYNC_ALWAYS
    private var receivedTimetable = false

    init {
        val currentWeekStart = Week.getWeekStart().apply {
            if (Date.getToday().weekDay > Week.FRIDAY) stepForward(0, 0, 7)
        }
        val weekStart = Date.fromY_m_d(data.arguments?.getString("weekStart") ?: currentWeekStart.stringY_m_d)
        val weekEnd = weekStart.clone().stepForward(0, 0, 6)
        fetchSource(LibrusExtraTimetableSource.entries.iterator(), weekStart, weekEnd)
    }

    private fun fetchSource(sources: Iterator<LibrusExtraTimetableSource>, weekStart: Date, weekEnd: Date) {
        if (!sources.hasNext()) {
            data.setSyncNext(ENDPOINT_LIBRUS_API_EXTRA_TIMETABLES,
                if (!receivedTimetable && retryIn == SYNC_ALWAYS) WEEK else retryIn)
            onSuccess(ENDPOINT_LIBRUS_API_EXTRA_TIMETABLES)
            return
        }
        val source = sources.next()
        apiGet(
            TAG,
            "Timetables/${source.endpoint}?dateFrom=${weekStart.stringY_m_d}&dateTo=${weekEnd.stringY_m_d}&hideOutdatedEntries=false",
            ignoreErrors = listOf(ERROR_LIBRUS_API_EXTRA_TIMETABLE_NOT_AVAILABLE),
            baseUrl = LIBRUS_API_3_URL,
        ) { json ->
            val unavailable = (json.getString("Code") ?: json.getString("Message")) in
                listOf("AccessDeny", "Request is denied", "Resource not found", "NotFound")
            if (!unavailable) {
                try {
                    stageLibrusExtraTimetable(data, json, weekStart, weekEnd, source)
                    receivedTimetable = true
                } catch (e: Exception) {
                    // Validate a whole source before replacing its cached lessons; other sources remain independent.
                    data.error(ApiError(TAG, EXCEPTION_LIBRUS_API_REQUEST)
                        .withThrowable(e).withApiResponse(json).setCritical(false))
                    retryIn = 15 * MINUTE
                }
            }
            fetchSource(sources, weekStart, weekEnd)
        }
    }
}

internal enum class LibrusExtraTimetableSource(val endpoint: String, namespace: Long) {
    OTHER_ACTIVITIES("OtherActivitiesRegister", 0),
    ONE_TO_ONE("OneToOneLearningPlan", 1);

    // Disjoint negative ID ranges keep API identifiers stable and allow independent cache replacement.
    private val prefix = Long.MIN_VALUE or (namespace shl 60)
    val idRange = prefix..(prefix or ((1L shl 60) - 1))

    fun lessonId(identifier: String, shiftedSource: Boolean = false): Long {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$identifier:$shiftedSource".toByteArray(Charsets.UTF_8))
        return prefix or (ByteBuffer.wrap(digest).long and ((1L shl 60) - 1))
    }
}

internal fun stageLibrusExtraTimetable(
    data: DataLibrus, json: JsonObject, weekStart: Date, weekEnd: Date,
    source: LibrusExtraTimetableSource = LibrusExtraTimetableSource.OTHER_ACTIVITIES,
) {
    val entries = parseLibrusExtraTimetable(json, weekStart, weekEnd)
    val ranges = (0 until data.lessonRanges.size()).map { data.lessonRanges.valueAt(it) }

    fun Lesson.setActual(entry: LibrusExtraLesson) {
        date = entry.date
        lessonNumber = findExtraLessonNumber(entry.startTime, entry.endTime, ranges)
        startTime = entry.startTime
        endTime = entry.endTime
        subjectId = data.getSubject(null, entry.title).id
        teacherId = entry.teacherName?.let { data.getTeacherByLastFirst(it).id }
        classroom = entry.classroom
    }
    fun Lesson.setOriginal(entry: LibrusExtraLesson) {
        oldDate = entry.date
        oldLessonNumber = findExtraLessonNumber(entry.startTime, entry.endTime, ranges)
        oldStartTime = entry.startTime
        oldEndTime = entry.endTime
        oldSubjectId = data.getSubject(null, entry.title).id
        oldTeacherId = entry.teacherName?.let { data.getTeacherByLastFirst(it).id }
        oldClassroom = entry.classroom
    }
    val lessons = mutableListOf<Lesson>()
    entries.forEach { entry ->
        val original = entry.original
        val shifted = original != null && (entry.date != original.date || entry.startTime != original.startTime)
        val lesson = Lesson(data.profileId, source.lessonId(entry.identifier)).apply {
            isExtra = true
            if (entry.cancelled) {
                type = Lesson.TYPE_CANCELLED
                setOriginal(entry)
            } else {
                setActual(entry)
                if (entry.substitution) type = Lesson.TYPE_CHANGE
                if (original != null) {
                    setOriginal(original)
                    type = if (shifted) Lesson.TYPE_SHIFTED_TARGET else Lesson.TYPE_CHANGE
                }
            }
            ownerId = buildOwnerId()
        }
        if (lesson.displayDate?.let { it in weekStart..weekEnd } == true) lessons.add(lesson)
        if (shifted && original != null && original.date in weekStart..weekEnd) {
            lessons.add(Lesson(data.profileId, source.lessonId(original.identifier, shiftedSource = true)).apply {
                isExtra = true
                type = Lesson.TYPE_SHIFTED_SOURCE
                setActual(entry)
                setOriginal(original)
                ownerId = buildOwnerId()
            })
        }
    }
    data.lessonList.addAll(lessons.distinctBy { it.id })
    data.toRemove.add(DataRemoveModel.Timetable.between(weekStart, weekEnd, isExtra = true, idRange = source.idRange))
    if (source == LibrusExtraTimetableSource.OTHER_ACTIVITIES) {
        // The initial DZD adapter used positive, content-based IDs; replace those rows on the first successful refresh.
        data.toRemove.add(DataRemoveModel.Timetable.between(weekStart, weekEnd, isExtra = true, idRange = 0L..Long.MAX_VALUE))
    }
}

internal data class LibrusExtraLesson(
    val date: Date,
    val startTime: Time,
    val endTime: Time,
    val title: String,
    val teacherName: String?,
    val classroom: String?,
    val cancelled: Boolean,
    val identifier: String,
    val original: LibrusExtraLesson?,
    val substitution: Boolean,
)

internal fun parseLibrusExtraTimetable(json: JsonObject, from: Date, to: Date): List<LibrusExtraLesson> {
    val entries = requireNotNull(json.getJsonArray("data")) { "Missing extra timetable array" }
    fun parse(entry: JsonObject, depth: Int = 0): LibrusExtraLesson {
        require(depth < 8) { "Too many nested substitutions" }
        val status = entry.getString("status")?.uppercase(Locale.ROOT)
        val cancelled = when (status) {
            null, "ACTUAL", "NORMAL", "ACTIVE", "ADDED", "PLANNED", "SUBSTITUTED", "SUBSTITUTION" -> false
            "CANCELLED", "CANCELED" -> true
            else -> throw IllegalArgumentException("Unknown extra lesson status: $status")
        }
        val identifier = requireNotNull(entry.getString("identifier")?.takeIf { it.isNotBlank() }) { "Missing extra lesson identifier" }
        val dateText = requireNotNull(entry.getString("date")) { "Missing extra lesson date" }
        require(dateText.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) { "Invalid extra lesson date" }
        val date = Date.fromY_m_d(dateText)
        GregorianCalendar(date.year, date.month - 1, date.day).apply { isLenient = false }.timeInMillis
        val start = parseExtraLessonTime(entry.getString("startTime"))
        val end = parseExtraLessonTime(entry.getString("endTime"))
        require(end > start) { "Invalid extra lesson time range" }
        val title = requireNotNull(entry.getString("title")?.trim()?.takeIf { it.isNotEmpty() }) { "Missing extra lesson title" }
        val original = entry.getJsonObject("substitutedLesson") ?: entry.getJsonObject("substituted_lesson")
        return LibrusExtraLesson(date, start, end, title,
            entry.getString("teacherName")?.trim()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotEmpty() },
            entry.getJsonObject("classroom")?.getString("symbol") ?: entry.getString("classroom"), cancelled,
            identifier, original?.let { parse(it, depth + 1) }, substitution = status == "SUBSTITUTION")
    }
    return entries.mapNotNull { element ->
        require(element.isJsonObject) { "Invalid extra lesson" }
        val entry = element.asJsonObject
        if (entry.getString("status")?.uppercase(Locale.ROOT) == "SUBSTITUTED") return@mapNotNull null
        val lesson = parse(entry)
        require(lesson.date in from..to || lesson.original?.date?.let { it in from..to } == true) { "Extra lesson outside requested week" }
        lesson
    }
}

private fun parseExtraLessonTime(value: String?): Time {
    require(value != null && value.matches(Regex("\\d{1,2}:\\d{2}(:\\d{2})?"))) { "Invalid extra lesson time" }
    val parts = value.split(':').map { it.toInt() }
    val seconds = parts.getOrElse(2) { 0 }
    require(parts[0] in 0..23 && parts[1] in 0..59 && seconds in 0..59) { "Invalid extra lesson time" }
    return Time(parts[0], parts[1], seconds)
}

internal fun findExtraLessonNumber(start: Time, end: Time, ranges: List<LessonRange>): Int? {
    fun Time.seconds() = hour * 3600 + minute * 60 + second
    var bestNumber: Int? = null
    var bestWeight = 0.0
    for (range in ranges.sortedBy { it.lessonNumber }) {
        if (range.endTime <= range.startTime) continue
        val overlap = minOf(end.seconds(), range.endTime.seconds()) - maxOf(start.seconds(), range.startTime.seconds())
        if (overlap <= 0) continue
        val span = maxOf(end.seconds(), range.endTime.seconds()) - minOf(start.seconds(), range.startTime.seconds())
        val weight = overlap.toDouble() / span
        if (weight > bestWeight) {
            bestWeight = weight
            bestNumber = range.lessonNumber
        }
    }
    return bestNumber
}
