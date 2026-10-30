package pl.szczodrzynski.edziennik.data.api.edziennik.librus.data.api

import com.google.gson.JsonObject
import pl.szczodrzynski.edziennik.data.api.*
import pl.szczodrzynski.edziennik.data.api.edziennik.librus.DataLibrus
import pl.szczodrzynski.edziennik.data.api.edziennik.librus.ENDPOINT_LIBRUS_API_PLANNED_LESSONS
import pl.szczodrzynski.edziennik.data.api.edziennik.librus.data.LibrusApi
import pl.szczodrzynski.edziennik.data.api.models.ApiError
import pl.szczodrzynski.edziennik.data.db.entity.LessonTopic
import pl.szczodrzynski.edziennik.ext.*
import java.time.LocalDate

class LibrusApiLessonTopics(
    override val data: DataLibrus,
    override val lastSync: Long?,
    private val endpointId: Int,
    private val onSuccess: (Int) -> Unit,
) : LibrusApi(data, lastSync) {
    private val planned = endpointId == ENDPOINT_LIBRUS_API_PLANNED_LESSONS
    private val resource = if (planned) "PlannedLessons" else "Realizations"

    init {
        pages(resource, resource) { rows ->
            val lookups = mutableMapOf<String, Map<Long, JsonObject>>()
            fun load(resources: List<Pair<String, List<Long>>>) {
                if (resources.isEmpty()) {
                    val subjects = lookups.getValue("Subjects").values.toList()
                    val users = lookups.getValue("Users").values.toList()
                    val items = parseLibrusLessonTopics(profileId, planned, rows,
                        lookups.getValue("Lessons").values.toList(),
                        lookups.getValue("VirtualClasses").values.toList(), subjects, users,
                        lookups.getValue("Substitutions").values.toList())
                    if (!data.cancelled) data.db.lessonTopicDao().replace(profileId, planned, items)
                    finish()
                    return
                }
                val (key, ids) = resources.first()
                val received = mutableListOf<JsonObject>()
                fun chunk(pending: List<List<Long>>) {
                    if (pending.isEmpty()) {
                        lookups[key] = received.associateBy { requireNotNull(it.getLong("Id")) }
                        if (resources.size == 1 && key == "Substitutions") {
                            val assignments = lookups.getValue("Lessons").values + lookups.getValue("VirtualClasses").values
                            val changes = lookups.getValue("Substitutions").values
                            val subjectIds = (assignments.mapNotNull { it.ref("Subject") } +
                                changes.flatMap { c -> listOfNotNull(c.ref("Subject"), c.ref("NewSubject")) }).distinct()
                            val teacherIds = (assignments.mapNotNull { it.ref("Teacher") } +
                                changes.flatMap { c -> listOfNotNull(c.ref("Teacher"), c.ref("NewTeacher")) }).distinct()
                            load(listOf("Subjects" to subjectIds, "Users" to teacherIds))
                        } else load(resources.drop(1))
                        return
                    }
                    val path = (if (key == "Substitutions") "Calendars/Substitutions/" else key) + "/" + pending.first().joinToString(",")
                    read(path) { json ->
                        val singular = when (key) { "VirtualClasses" -> "VirtualClass"; "Lessons" -> "Lesson"; "Users" -> "User"; "Subjects" -> "Subject"; else -> "Substitution" }
                        val list = json.getJsonArray(key)?.map { it.asJsonObject }
                            ?: listOfNotNull(json.getJsonObject(singular))
                        require(pending.first().all { id -> list.any { it.getLong("Id") == id } }) { "Incomplete lesson topic dictionary: $key" }
                        received.addAll(list)
                        chunk(pending.drop(1))
                    }
                }
                chunk(ids.distinct().filter { it > 0 }.chunked(100))
            }
            load(listOf("Lessons" to rows.mapNotNull { it.ref("Lesson") },
                "VirtualClasses" to rows.mapNotNull { it.ref("VirtualClass") },
                "Substitutions" to rows.mapNotNull { it.ref("Substitution") }))
        }
    }

    private fun finish(delay: Long = HOUR) {
        data.setSyncNext(endpointId, delay)
        onSuccess(endpointId)
    }

    private fun read(path: String, done: (JsonObject) -> Unit) {
        apiGet("LibrusApiLessonTopics", path, baseUrl = LIBRUS_API_3_URL,
            ignoreErrors = listOf(ERROR_LIBRUS_API_ACCESS_DENIED, ERROR_LIBRUS_API_RESOURCE_ACCESS_DENIED,
                ERROR_LIBRUS_API_RESOURCE_NOT_FOUND, ERROR_LIBRUS_API_DATA_NOT_FOUND)) { json ->
            if ((json.getString("Code") ?: json.getString("Message")) in
                listOf("AccessDeny", "Request is denied", "NotFound", "Resource not found")) {
                data.error(ApiError("LibrusApiLessonTopics", ERROR_LIBRUS_API_ACCESS_DENIED).setCritical(false))
                finish(HOUR)
            } else try { if (!data.cancelled) done(json) else finish() } catch (e: Exception) {
                data.error(ApiError("LibrusApiLessonTopics", EXCEPTION_LIBRUS_API_REQUEST)
                    .withThrowable(e).setCritical(false))
                finish()
            }
        }
    }

    private fun pages(path: String, key: String, done: (List<JsonObject>) -> Unit) {
        val all = mutableListOf<JsonObject>()
        fun fetch(page: Int) {
            read(if (page == 1) path else "$path?page=$page") { json ->
                val rows = requireNotNull(json.getJsonArray(key)) { "Missing lesson topics array" }.map { it.asJsonObject }
                all.addAll(rows)
                val total = json.getJsonObject("pagination")?.getInt("total") ?: all.size
                require(total >= all.size) { "Invalid lesson topic pagination" }
                if (all.size < total) {
                    require(rows.isNotEmpty() && page < 10000) { "Incomplete lesson topics" }
                    fetch(page + 1)
                } else done(all)
            }
        }
        fetch(1)
    }
}

private fun JsonObject.ref(key: String) = getJsonObject(key)?.getLong("Id")?.takeIf { it > 0 }

internal fun parseLibrusLessonTopics(
    profileId: Int, planned: Boolean, rows: List<JsonObject>,
    lessons: List<JsonObject>, virtualClasses: List<JsonObject>,
    subjects: List<JsonObject>, users: List<JsonObject>, substitutions: List<JsonObject>,
): List<LessonTopic> {
    fun indexed(list: List<JsonObject>) = list.associateBy { requireNotNull(it.getLong("Id")) }
    val lessonMap = indexed(lessons)
    val virtualMap = indexed(virtualClasses)
    val subjectMap = indexed(subjects)
    val userMap = indexed(users)
    val changeMap = indexed(substitutions)
    val items = rows.map { row ->
        val lessonId = row.ref("Lesson")
        val virtualId = row.ref("VirtualClass")
        val assignment = requireNotNull(if (lessonId != null) lessonMap[lessonId] else virtualMap[virtualId]) { "Missing topic lesson mapping" }
        val change = row.ref("Substitution")?.let { requireNotNull(changeMap[it]) { "Missing topic substitution" } }
        val subject = requireNotNull(change?.ref("NewSubject") ?: change?.ref("Subject") ?: assignment.ref("Subject"))
        val teacher = requireNotNull(change?.ref("NewTeacher") ?: change?.ref("Teacher") ?: assignment.ref("Teacher"))
        val date = requireNotNull(change?.getString("NewDate") ?: row.getString("Date"))
        require(LocalDate.parse(date).toString() == date) { "Invalid topic date" }
        val number = requireNotNull(change?.getInt("NewLessonNo") ?: row.getInt("LessonNo") ?: row.getInt("LessonNumber"))
        require(number >= 0) { "Invalid topic lesson number" }
        val teacherJson = requireNotNull(userMap[teacher]) { "Missing topic teacher" }
        val topic = if (row.has("ImplementedContent") || row.has("TopicOfTheDay") || row.has("ImplementedSubjectAreas")) {
            (listOfNotNull(row.getString("ImplementedContent") ?: row.getString("TopicOfTheDay")) +
                (row.getJsonArray("ImplementedSubjectAreas")?.mapNotNull { it.asJsonObject.getString("Content") } ?: emptyList()))
                .filter { it.isNotBlank() }.joinToString("\n")
        } else row.getString("Topic") ?: ""
        LessonTopic(profileId, planned, requireNotNull(row.getString("Id")).also { require(it.isNotBlank()) }, date, number,
            subject, requireNotNull(subjectMap[subject]?.getString("Name")), teacher,
            listOfNotNull(teacherJson.getString("FirstName"), teacherJson.getString("LastName")).joinToString(" "),
            virtualId, topic,
            change?.getBoolean("IsCancelled") ?: change?.getBoolean("IsCanceled") ?: false,
            (row.getJsonArray(if (planned) "PlannedLessonLinks" else "RealizationLinks")?.toString() ?: "[]"),
            (row.getJsonArray(if (planned) "PlannedLessonFiles" else "RealizationFiles")?.toString() ?: "[]"))
    }
    require(items.map { it.id }.distinct().size == items.size) { "Duplicate topic identifiers" }
    return items
}
