package pl.szczodrzynski.edziennik.data.api.edziennik.mobidziennik.html

import pl.szczodrzynski.edziennik.data.api.edziennik.mobidziennik.DataMobidziennik
import pl.szczodrzynski.edziennik.data.api.edziennik.mobidziennik.data.MobidziennikWeb
import pl.szczodrzynski.edziennik.data.api.models.DataRemoveModel
import pl.szczodrzynski.edziennik.data.db.entity.Event
import pl.szczodrzynski.edziennik.data.db.entity.Metadata
import pl.szczodrzynski.edziennik.data.db.entity.SYNC_ALWAYS
import pl.szczodrzynski.edziennik.data.enums.MetadataType
import pl.szczodrzynski.edziennik.utils.models.Date
import pl.szczodrzynski.edziennik.utils.models.Time

class MobidziennikHtmlAgenda(
    override val data: DataMobidziennik,
    override val lastSync: Long?,
    val onSuccess: (endpointId: Int) -> Unit,
) : MobidziennikWeb(data, lastSync) {
    companion object {
        private const val TAG = "MobidziennikHtmlAgenda"
        private const val MAX_CLASS_NAME_LENGTH = 12
    }

    init {
        if (data.profile == null)
            onSuccess(ENDPOINT_MOBIDZIENNIK_HTML_AGENDA)
        else
            tests()
    }

    private fun tests() {
        webGet(TAG, "/dziennik/sprawdziany") { text ->
            HtmlParsers.parseExams(text).forEach { addTest(it) }
            classCalendar()
        }
    }

    private fun classCalendar() {
        webGet(TAG, "/dziennik/kalendarzklasowy") { text ->
            val events = HtmlParsers.parseClassCalendar(text).filter { it.type == "kalendarz" }
            if (data.studentId == HtmlPlaceholder.STUDENT_ID) {
                events.mapNotNull { it.studentId }.distinct().singleOrNull()?.let { data.studentId = it }
            }
            events.filter { it.studentId == null || it.studentId == data.studentId }
                .forEach(::addCalendarEvent)
            schoolCalendar()
        }
    }

    private fun schoolCalendar() {
        webGet(TAG, "/dziennik/kalendarz") { text ->
            HtmlParsers.parseSchoolCalendar(text).forEach { addSchoolEvent(it) }
            finish()
        }
    }

    private fun add(event: Event) {
        data.eventList.add(event)
        data.metadataList.add(
            Metadata(
                data.profileId,
                MetadataType.EVENT,
                event.id,
                data.profile?.empty ?: false,
                data.profile?.empty ?: false,
            )
        )
    }

    private fun addTest(t: HtmlExam) {
        val date = data.htmlDate(t.date, "test date") ?: return
        if (t.className.length <= MAX_CLASS_NAME_LENGTH)
            data.htmlSetClassName(t.className)

        val kind = t.kind.lowercase()
        val type = when {
            "kartk" in kind -> Event.TYPE_SHORT_QUIZ
            "sprawd" in kind -> Event.TYPE_EXAM
            else -> Event.TYPE_DEFAULT
        }
        val id = htmlId("test", date.stringY_m_d, t.subject, t.kind, t.topic)
        add(
            Event(
                profileId = data.profileId,
                id = id,
                date = date,
                time = htmlTime(HtmlText.parseTime(t.dayAndTime)),
                topic = t.topic.ifEmpty { t.kind },
                color = null,
                type = type,
                teacherId = data.htmlTeacher(t.teacher)?.id ?: -1,
                subjectId = data.htmlSubject(t.subject)?.id ?: -1,
                teamId = data.htmlTeamClass().id,
                addedDate = data.htmlDateTimeMillis(t.addedAt) ?: System.currentTimeMillis(),
            )
        )
    }

    private fun addCalendarEvent(e: HtmlCalendarEvent) {
        val date = data.htmlDate(e.date, "class calendar event") ?: return
        val type = when (e.color?.lowercase()) {
            "#c54449" -> Event.TYPE_SHORT_QUIZ
            "#ab0001" -> Event.TYPE_EXAM
            "#008928" -> Event.TYPE_CLASS_EVENT
            "#b66000" -> Event.TYPE_EXCURSION
            else -> Event.TYPE_INFORMATION
        }
        var topic = e.title
        if (e.description.isNotEmpty() && e.description != e.title)
            topic += "\n" + e.description
        val id = e.idRaw.split(";").getOrNull(2)?.toLongOrNull() ?: htmlId("calendar", e.idRaw, e.date, e.title)
        add(
            Event(
                profileId = data.profileId,
                id = id,
                date = date,
                time = null,
                topic = topic,
                color = null,
                type = type,
                teacherId = -1,
                subjectId = -1,
                teamId = data.htmlTeamClass().id,
            )
        )
    }

    private fun addSchoolEvent(r: HtmlSchoolCalendarRow) {
        val date = data.htmlDate(r.dueDate, "school calendar event") ?: return
        var topic = r.name
        if (r.category.isNotEmpty())
            topic += " (${r.category})"
        if (r.description.isNotEmpty())
            topic += "\n" + r.description
        add(
            Event(
                profileId = data.profileId,
                id = htmlId("school", r.dueDate, r.name),
                date = date,
                time = null,
                topic = topic,
                color = null,
                type = Event.TYPE_INFORMATION,
                teacherId = -1,
                subjectId = -1,
                teamId = data.htmlTeamClass().id,
            )
        )
    }

    private fun finish() {
        listOf(
            Event.TYPE_EXAM,
            Event.TYPE_SHORT_QUIZ,
            Event.TYPE_CLASS_EVENT,
            Event.TYPE_EXCURSION,
            Event.TYPE_INFORMATION,
        ).forEach { data.toRemove.add(DataRemoveModel.Events.futureWithType(it)) }
        data.setSyncNext(ENDPOINT_MOBIDZIENNIK_HTML_AGENDA, SYNC_ALWAYS)
        onSuccess(ENDPOINT_MOBIDZIENNIK_HTML_AGENDA)
    }
}
