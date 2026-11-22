package pl.szczodrzynski.edziennik.data.api.edziennik.mobidziennik.html

import pl.szczodrzynski.edziennik.data.api.edziennik.mobidziennik.DataMobidziennik
import pl.szczodrzynski.edziennik.data.api.edziennik.mobidziennik.data.MobidziennikWeb
import pl.szczodrzynski.edziennik.data.api.models.DataRemoveModel
import pl.szczodrzynski.edziennik.data.db.entity.Grade
import pl.szczodrzynski.edziennik.data.db.entity.Grade.Companion.TYPE_NORMAL
import pl.szczodrzynski.edziennik.data.db.entity.Grade.Companion.TYPE_SEMESTER1_FINAL
import pl.szczodrzynski.edziennik.data.db.entity.Grade.Companion.TYPE_SEMESTER1_PROPOSED
import pl.szczodrzynski.edziennik.data.db.entity.Grade.Companion.TYPE_YEAR_FINAL
import pl.szczodrzynski.edziennik.data.db.entity.Grade.Companion.TYPE_YEAR_PROPOSED
import pl.szczodrzynski.edziennik.data.db.entity.Metadata
import pl.szczodrzynski.edziennik.data.db.entity.SYNC_ALWAYS
import pl.szczodrzynski.edziennik.data.enums.MetadataType

class MobidziennikHtmlGrades(
    override val data: DataMobidziennik,
    override val lastSync: Long?,
    val onSuccess: (endpointId: Int) -> Unit,
) : MobidziennikWeb(data, lastSync) {
    companion object {
        private const val TAG = "MobidziennikHtmlGrades"
    }

    init {
        if (data.profile == null)
            onSuccess(ENDPOINT_MOBIDZIENNIK_HTML_GRADES)
        else
            sync(mutableListOf(1, 2))
    }

    private fun sync(semesters: MutableList<Int>) {
        if (semesters.isEmpty()) {
            syncFinal()
            return
        }
        val semester = semesters.removeAt(0)
        webGet(TAG, "/dziennik/oceny/?semestr=$semester") { text ->
            HtmlParsers.parseGrades(text)?.let { grades ->
                for (block in grades)
                    for (grade in block.grades)
                        addGrade(block.subject, grade, semester)
                data.toRemove.add(DataRemoveModel.Grades.semesterWithType(semester, TYPE_NORMAL))
            }
            sync(semesters)
        }
    }

    private fun syncFinal() {
        webGet(TAG, "/dziennik/oceny/?koncowe=true") { text ->
            val grades = HtmlParsers.parseFinalGrades(text)
            for (g in grades)
                addFinalGrade(g)
            for ((semester, type) in grades.mapNotNull { finalType(it.heading) }.distinct())
                data.toRemove.add(DataRemoveModel.Grades.semesterWithType(semester, type))
            data.setSyncNext(ENDPOINT_MOBIDZIENNIK_HTML_GRADES, SYNC_ALWAYS)
            onSuccess(ENDPOINT_MOBIDZIENNIK_HTML_GRADES)
        }
    }

    private fun finalType(header: String): Pair<Int, Int>? {
        val h = header.lowercase()
        val proposed = "przewid" in h
        return when {
            "średni" in h || "sredni" in h -> null
            "śródr" in h || "srodr" in h -> 1 to if (proposed) TYPE_SEMESTER1_PROPOSED else TYPE_SEMESTER1_FINAL
            "roczn" in h -> 2 to if (proposed) TYPE_YEAR_PROPOSED else TYPE_YEAR_FINAL
            else -> null
        }
    }

    private fun addFinalGrade(g: HtmlFinalGrade) {
        val (semester, type) = finalType(g.heading) ?: return
        val name = g.grade.split(" ").first()
        val subject = data.htmlSubject(g.subject)
        val id = htmlId("final", g.subject, type.toString())
        data.gradeList.add(
            Grade(
                profileId = data.profileId,
                id = id,
                name = name,
                type = type,
                value = htmlGradeValue(name),
                weight = 0f,
                color = HtmlPlaceholder.GRADE_COLOR,
                category = null,
                description = null,
                comment = null,
                semester = semester,
                teacherId = -1,
                subjectId = subject?.id ?: -1,
            )
        )
        data.metadataList.add(
            Metadata(
                data.profileId,
                MetadataType.GRADE,
                id,
                data.profile?.empty ?: false,
                data.profile?.empty ?: false,
            )
        )
    }

    private fun addGrade(subjectName: String, g: HtmlGrade, semester: Int) {
        val subject = data.htmlSubject(subjectName)
        val teacher = data.htmlTeacher(g.teacher)
        val id = g.id.toLongOrNull() ?: htmlId("grade", subjectName, g.grade, g.addedAt, g.description)
        val weight = if (g.countsTowardAverage) (g.weight ?: 0.0).toFloat() else 0f
        val description = if (g.countsTowardAverage) g.description else "NLDŚR, ${g.description}"

        val grade = Grade(
            profileId = data.profileId,
            id = id,
            name = g.grade ?: "?",
            type = TYPE_NORMAL,
            value = (g.value ?: 0.0).toFloat(),
            weight = weight,
            color = HtmlPlaceholder.GRADE_COLOR,
            category = g.category,
            description = description,
            comment = null,
            semester = semester,
            teacherId = teacher?.id ?: -1,
            subjectId = subject?.id ?: -1,
            addedDate = data.htmlDateTimeMillis(g.addedAt) ?: System.currentTimeMillis(),
        )
        data.gradeList.add(grade)
        data.metadataList.add(
            Metadata(
                data.profileId,
                MetadataType.GRADE,
                id,
                data.profile?.empty ?: false,
                data.profile?.empty ?: false,
            )
        )
    }
}
