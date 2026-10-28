/*
 * Copyright (c) Kuba Szczodrzyński 2020-4-25.
 */
package pl.szczodrzynski.edziennik.data.db.full

import androidx.room.Ignore
import androidx.room.Relation
import pl.szczodrzynski.edziennik.data.db.entity.Announcement
import pl.szczodrzynski.edziennik.data.db.entity.Note
import pl.szczodrzynski.edziennik.data.db.entity.Noteable
import pl.szczodrzynski.edziennik.ui.search.SearchField
import pl.szczodrzynski.edziennik.ui.search.Searchable
import pl.szczodrzynski.edziennik.utils.models.Date

class AnnouncementFull(
        profileId: Int, id: Long,
        subject: String, text: String?,
        startDate: Date?, endDate: Date?,
        teacherId: Long, addedDate: Long = System.currentTimeMillis()
) : Announcement(
        profileId, id,
        subject, text,
        startDate, endDate,
        teacherId, addedDate
), Noteable, Searchable<AnnouncementFull> {
    var teacherName: String? = null

    // metadata
    var seen = false
    var notified = false

    @Relation(parentColumn = "announcementId", entityColumn = "noteOwnerId", entity = Note::class)
    override lateinit var notes: MutableList<Note>
    override fun getNoteType() = Note.OwnerType.ANNOUNCEMENT
    override fun getNoteOwnerProfileId() = profileId
    override fun getNoteOwnerId() = id

    @Ignore
    @Transient
    override var searchPriority = 0

    @Ignore
    @Transient
    override var searchHighlightText: String? = null

    @delegate:Ignore
    @delegate:Transient
    override val searchKeywords by lazy {
        listOf(
            listOf(subject, teacherName),
            listOf(text)
        )
    }

    override fun compareTo(other: Searchable<*>): Int {
        if (other is SearchField)
            return 1
        if (this.searchPriority != other.searchPriority)
            return this.searchPriority.compareTo(other.searchPriority)
        if (other is AnnouncementFull) {
            val d1 = this.startDate ?: Date(0, 0, 0)
            val d2 = other.startDate ?: Date(0, 0, 0)
            return d2.compareTo(d1)
        }
        return 0
    }
}
