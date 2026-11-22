package pl.szczodrzynski.edziennik.data.api.edziennik.mobidziennik.html

import pl.szczodrzynski.edziennik.data.api.edziennik.mobidziennik.DataMobidziennik
import pl.szczodrzynski.edziennik.data.api.edziennik.mobidziennik.data.MobidziennikWeb
import pl.szczodrzynski.edziennik.data.api.models.DataRemoveModel
import pl.szczodrzynski.edziennik.data.db.entity.Event
import pl.szczodrzynski.edziennik.data.db.entity.Metadata
import pl.szczodrzynski.edziennik.data.db.entity.SYNC_ALWAYS
import pl.szczodrzynski.edziennik.data.enums.MetadataType

class MobidziennikHtmlHomework(
    override val data: DataMobidziennik,
    override val lastSync: Long?,
    val onSuccess: (endpointId: Int) -> Unit,
) : MobidziennikWeb(data, lastSync) {
    companion object {
        private const val TAG = "MobidziennikHtmlHomework"
    }

    init {
        if (data.profile == null)
            onSuccess(ENDPOINT_MOBIDZIENNIK_HTML_HOMEWORK)
        else
            webGet(TAG, "/dziennik/zadaniadomowe") { text ->
                val homework = HtmlParsers.parseHomework(text)
                if (data.studentId == HtmlPlaceholder.STUDENT_ID) {
                    homework.mapNotNull { it.studentId?.toIntOrNull()?.takeIf { id -> id > 0 } }
                        .distinct().singleOrNull()?.let { data.studentId = it }
                }
                homework.filter { it.studentId == null || it.studentId.toIntOrNull() == data.studentId }
                    .forEach { add(it) }

                data.toRemove.add(DataRemoveModel.Events.futureWithType(Event.TYPE_HOMEWORK))
                data.setSyncNext(ENDPOINT_MOBIDZIENNIK_HTML_HOMEWORK, SYNC_ALWAYS)
                onSuccess(ENDPOINT_MOBIDZIENNIK_HTML_HOMEWORK)
            }
    }

    private fun add(h: HtmlHomework) {
        val date = data.htmlDate(h.dueDate, "homework date") ?: return
        val id = h.id.toLongOrNull() ?: htmlId("homework", h.dueDate, h.subject, h.body)
        val event = Event(
            profileId = data.profileId,
            id = id,
            date = date,
            time = htmlTime(HtmlText.parseTimeRange(h.hours)?.first ?: HtmlText.parseTime(h.hours)),
            topic = h.body,
            color = null,
            type = Event.TYPE_HOMEWORK,
            teacherId = data.htmlTeacher(h.teacher)?.id ?: -1,
            subjectId = data.htmlSubject(h.subject)?.id ?: -1,
            teamId = data.htmlTeamClass().id,
            addedDate = data.htmlDateTimeMillis(h.addedAt) ?: System.currentTimeMillis(),
        )
        event.homeworkBody = if (h.truncated) null else h.body
        event.isDownloaded = !h.truncated

        data.eventList.add(event)
        data.metadataList.add(
            Metadata(
                data.profileId,
                MetadataType.HOMEWORK,
                id,
                data.profile?.empty ?: false,
                data.profile?.empty ?: false,
            )
        )
    }
}
