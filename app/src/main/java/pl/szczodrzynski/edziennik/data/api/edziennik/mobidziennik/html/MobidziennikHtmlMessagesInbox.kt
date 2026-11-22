package pl.szczodrzynski.edziennik.data.api.edziennik.mobidziennik.html

import pl.szczodrzynski.edziennik.data.api.edziennik.mobidziennik.DataMobidziennik
import pl.szczodrzynski.edziennik.data.api.edziennik.mobidziennik.data.MobidziennikWeb
import pl.szczodrzynski.edziennik.data.db.entity.Message
import pl.szczodrzynski.edziennik.data.db.entity.MessageRecipient
import pl.szczodrzynski.edziennik.data.db.entity.Metadata
import pl.szczodrzynski.edziennik.data.db.entity.SYNC_ALWAYS
import pl.szczodrzynski.edziennik.data.enums.MetadataType

class MobidziennikHtmlMessagesInbox(
    override val data: DataMobidziennik,
    override val lastSync: Long?,
    val onSuccess: (endpointId: Int) -> Unit,
) : MobidziennikWeb(data, lastSync) {
    companion object {
        private const val TAG = "MobidziennikHtmlMessagesInbox"
    }

    init {
        webGet(TAG, "/dziennik/wiadomosci/?strona=0") { text ->
            val dates = HtmlListDates()

            for (row in HtmlParsers.parseMessageList(text)) {
                val id = row.id.toLongOrNull() ?: continue

                val message = Message(
                    profileId = data.profileId,
                    id = id,
                    type = Message.TYPE_RECEIVED,
                    subject = row.subject,
                    body = null,
                    senderId = data.htmlTeacher(row.sender)?.id,
                    addedDate = dates.millis(row.receivedAt) ?: System.currentTimeMillis(),
                )
                if (row.attachments.isNotEmpty() && row.attachments != "0")
                    message.hasAttachments = true
                data.messageRecipientIgnoreList.add(MessageRecipient(data.profileId, -1, id))
                data.messageList.add(message)
                data.setSeenMetadataList.add(
                    Metadata(
                        data.profileId,
                        MetadataType.MESSAGE,
                        id,
                        row.isRead,
                        row.isRead || data.profile?.empty ?: false,
                    )
                )
            }

            data.setSyncNext(ENDPOINT_MOBIDZIENNIK_HTML_MESSAGES_INBOX, SYNC_ALWAYS)
            onSuccess(ENDPOINT_MOBIDZIENNIK_HTML_MESSAGES_INBOX)
        }
    }
}
