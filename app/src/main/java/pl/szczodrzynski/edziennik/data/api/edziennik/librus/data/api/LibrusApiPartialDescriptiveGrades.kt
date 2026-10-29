package pl.szczodrzynski.edziennik.data.api.edziennik.librus.data.api

import android.graphics.Color
import com.google.gson.JsonObject
import pl.szczodrzynski.edziennik.data.api.*
import pl.szczodrzynski.edziennik.data.api.edziennik.librus.DataLibrus
import pl.szczodrzynski.edziennik.data.api.edziennik.librus.ENDPOINT_LIBRUS_API_PARTIAL_DESCRIPTIVE_GRADES
import pl.szczodrzynski.edziennik.data.api.edziennik.librus.data.LibrusApi
import pl.szczodrzynski.edziennik.data.api.models.ApiError
import pl.szczodrzynski.edziennik.data.api.models.DataRemoveModel
import pl.szczodrzynski.edziennik.data.db.entity.Grade
import pl.szczodrzynski.edziennik.data.db.entity.Teacher
import pl.szczodrzynski.edziennik.data.db.entity.Metadata
import pl.szczodrzynski.edziennik.data.db.entity.SYNC_ALWAYS
import pl.szczodrzynski.edziennik.data.enums.MetadataType
import pl.szczodrzynski.edziennik.ext.*
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class LibrusApiPartialDescriptiveGrades(
    override val data: DataLibrus,
    override val lastSync: Long?,
    private val onSuccess: (Int) -> Unit,
) : LibrusApi(data, lastSync) {
    companion object { const val TAG = "LibrusApiPartialDescriptiveGrades" }

    init {
        read("Auth/TokenInfo") { token ->
            val user = requireNotNull(token.getString("UserIdentifier")) { "Missing user identifier" }
            read("Auth/UserInfo/$user") { info ->
                val student = info.getString("IdentifierOfStudentAssignedWithUser")
                    ?: info.getString("UserIdentifier")?.takeIf { info.getString("UserType") == "STUDENT" }
                require(!student.isNullOrBlank()) { "Missing student identifier" }
                pages("Auth/DescriptiveGradingSystem/PartialGrades/Student/$student", "data", post = true) { grades ->
                    if (grades.isEmpty()) stage(grades, emptyList(), emptyList(), emptyList()) else
                        read("Auth/Lessons") { assignments ->
                            val links = requireNotNull(assignments.getJsonArray("Data")).map { it.asJsonObject }
                            val ids = requiredPartialGradeLessons(grades, links)
                            val lessons = mutableListOf<JsonObject>()
                            fun fetchLessons(chunks: List<List<Long>>) {
                                if (chunks.isEmpty()) {
                                    pages("Users", "Users") { users ->
                                        read("Subjects") { subjects ->
                                            stage(grades, links, lessons, users, requireNotNull(subjects.getJsonArray("Subjects")).map { it.asJsonObject })
                                        }
                                    }
                                } else read("Lessons/" + chunks.first().joinToString(",")) { json ->
                                    val rows = (json.getJsonArray("Lessons")?.map { it.asJsonObject }
                                        ?: listOfNotNull(json.getJsonObject("Lesson"))).toMutableList()
                                    val received = rows.mapNotNull { it.getLong("Id") }.toSet()
                                    // The standard timetable sync already stores the same numeric lesson mappings.
                                    chunks.first().filter { it !in received }.forEach { id ->
                                        data.librusLessons[id]?.let { cached ->
                                            rows.add(JsonObject("Id" to id,
                                                "Subject" to JsonObject("Id" to cached.subjectId),
                                                "Teacher" to JsonObject("Id" to cached.teacherId)))
                                        }
                                    }
                                    require(chunks.first().all { id -> rows.any { it.getLong("Id") == id } }) {
                                        "Missing numeric lesson mappings for requested assessments"
                                    }
                                    lessons.addAll(rows)
                                    fetchLessons(chunks.drop(1))
                                }
                            }
                            fetchLessons(ids.chunked(100))
                        }
                }
            }
        }
    }

    private fun finish(retry: Long = SYNC_ALWAYS) {
        data.setSyncNext(ENDPOINT_LIBRUS_API_PARTIAL_DESCRIPTIVE_GRADES, retry)
        onSuccess(ENDPOINT_LIBRUS_API_PARTIAL_DESCRIPTIVE_GRADES)
    }

    private fun read(endpoint: String, payload: JsonObject? = null, done: (JsonObject) -> Unit) {
        apiGet(TAG, endpoint, method = if (payload == null) GET else POST, payload = payload,
            baseUrl = LIBRUS_API_3_URL, ignoreErrors = listOf(ERROR_LIBRUS_API_DESCRIPTIVE_MODULE_NOT_AVAILABLE)) { json ->
            if ((json.getString("Code") ?: json.getString("Message")) in
                listOf("AccessDeny", "Request is denied", "NotFound", "Resource not found")) {
                finish(WEEK)
            } else try { done(json) } catch (e: Exception) {
                data.error(ApiError(TAG, EXCEPTION_LIBRUS_API_REQUEST).withThrowable(e)
                    .withApiResponse(json).setCritical(false))
                finish(15 * MINUTE)
            }
        }
    }

    private fun pages(endpoint: String, key: String, post: Boolean = false, done: (List<JsonObject>) -> Unit) {
        val all = mutableListOf<JsonObject>()
        fun fetch(page: Int) {
            val payload = if (post) JsonObject("limit" to 800, "page" to page) else null
            val path = if (post) endpoint else "$endpoint?limit=500&page=$page"
            read(path, payload) { json ->
                val rows = requireNotNull(json.getJsonArray(key)) { "Missing $key array" }.map { it.asJsonObject }
                all.addAll(rows)
                val total = json.getJsonObject("pagination")?.getInt("total") ?: all.size
                require(total >= all.size) { "Inconsistent pagination" }
                if (all.size < total) {
                    require(rows.isNotEmpty() && page < 10000) { "Incomplete pagination" }
                    fetch(page + 1)
                } else done(all)
            }
        }
        fetch(1)
    }

    private fun stage(rows: List<JsonObject>, assignments: List<JsonObject>, lessons: List<JsonObject>, users: List<JsonObject>, subjects: List<JsonObject> = emptyList()) {
        val grades = parseLibrusPartialGrades(data.profileId, rows, assignments, lessons, users)
        subjects.forEach { subject ->
            data.getSubject(requireNotNull(subject.getLong("Id")), requireNotNull(subject.getString("Name")))
        }
        val teacherIds = grades.map { it.teacherId }.toSet()
        users.filter { it.getLong("Id") in teacherIds }.forEach { user ->
            val id = user.getLong("Id") ?: return@forEach
            if (data.teacherList[id] == null) {
                data.teacherList.put(id, Teacher(profileId, id, user.getString("FirstName").orEmpty(), user.getString("LastName").orEmpty()))
            }
        }
        data.gradeList.addAll(grades)
        grades.forEach { grade ->
            data.metadataList.add(Metadata(profileId, MetadataType.GRADE, grade.id, profile?.empty == true, profile?.empty == true))
        }
        // Negative IDs belong only to this source; legacy descriptive grades use positive IDs.
        data.toRemove.add(DataRemoveModel.Grades.allWithType(Grade.TYPE_DESCRIPTIVE, Long.MIN_VALUE..-1L))
        finish()
    }
}

internal fun partialGradeId(identifier: String): Long =
    ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(identifier.toByteArray(Charsets.UTF_8))).long or Long.MIN_VALUE

internal fun parseLibrusPartialGrades(
    profileId: Int, rows: List<JsonObject>, assignments: List<JsonObject>, lessons: List<JsonObject>, users: List<JsonObject>,
): List<Grade> {
    val links = assignments.associateBy { it.getString("identifier") }
    val numeric = lessons.associateBy { it.getLong("Id") }
    val teachers = users.associateBy { it.getString("AccountId") }
    return rows.map { row ->
        fun required(key: String) = requireNotNull(row.getString(key)?.takeIf { it.isNotBlank() }) { "Missing $key" }
        val link = requireNotNull(links[required("lessonAssignmentId")]) { "Unknown lesson assignment" }
        require(link.getString("schoolSubjectIdentifier") == required("subjectId")) { "Subject mapping mismatch" }
        val lesson = requireNotNull(numeric[link.getLong("numericIdentifier")]) { "Unknown numeric lesson" }
        val teacher = requireNotNull(teachers[required("teacherId")]?.getLong("Id")) { "Unknown teacher" }
        val area = requireNotNull(row.getJsonObject("area")) { "Missing assessment area" }
        val scale = row.getJsonObject("scaleValue")?.getString("value")?.takeIf { it.isNotBlank() }
        val content = listOfNotNull(row.getString("content")?.takeIf { it.isNotBlank() },
            row.getJsonArray("implementedRequirements")?.mapNotNull { it.asJsonObject.getString("name") }
                ?.takeIf { it.isNotEmpty() }?.joinToString("\n")).joinToString("\n\n").takeIf { it.isNotBlank() }
        require(scale != null || content != null || !row.getString("comments").isNullOrBlank()) { "Empty descriptive grade" }
        val semester = requireNotNull(row.getInt("semester"))
        require(semester in 1..2) { "Invalid semester" }
        val added = LocalDateTime.parse(required("addDate"), DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
            .withResolverStyle(java.time.format.ResolverStyle.STRICT)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val color = area.getString("color")?.let { text ->
            if (text.matches(Regex("[0-9a-fA-F]{6}"))) Color.parseColor("#$text") else null
        } ?: -1
        Grade(profileId, partialGradeId(required("gradeId")), scale ?: "\u2026", Grade.TYPE_DESCRIPTIVE,
            0f, 0f, color, area.getString("name"), content, row.getString("comments")?.takeIf { it.isNotBlank() },
            semester, teacher, requireNotNull(lesson.getJsonObject("Subject")?.getLong("Id")), added)
    }.distinctBy { it.id }
}


internal fun requiredPartialGradeLessons(grades: List<JsonObject>, assignments: List<JsonObject>): List<Long> {
    val links = assignments.associateBy { it.getString("identifier") }
    return grades.map { grade ->
        val link = requireNotNull(links[grade.getString("lessonAssignmentId")]) { "Unknown lesson assignment" }
        requireNotNull(link.getLong("numericIdentifier")?.takeIf { it > 0 }) { "Invalid numeric lesson identifier" }
    }.distinct()
}
