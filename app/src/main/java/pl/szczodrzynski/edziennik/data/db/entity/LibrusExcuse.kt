package pl.szczodrzynski.edziennik.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import pl.szczodrzynski.edziennik.utils.models.Date

/**
 * A Librus e-Usprawiedliwienie (absence excuse), sent by a parent.
 *
 * Excuses are linked to attendances by (date, lesson number) - never by attendance ID,
 * because [pl.szczodrzynski.edziennik.data.api.edziennik.librus.data.api.LibrusApiAttendances]
 * strips non-digits from the Librus ID, so it may not map back.
 */
@Entity(
    tableName = "librusExcuses",
    primaryKeys = ["profileId", "excuseId"],
    indices = [
        Index(value = ["profileId", "excuseDateFrom", "excuseDateTo"]),
    ],
)
data class LibrusExcuse(
    val profileId: Int,
    /** The API-assigned excuse ID. */
    @ColumnInfo(name = "excuseId")
    val id: Long,

    @ColumnInfo(name = "excuseDateFrom")
    val dateFrom: Date,
    @ColumnInfo(name = "excuseDateTo")
    val dateTo: Date,
    /** Comma-separated lesson numbers. Null or empty means whole days. */
    @ColumnInfo(name = "excuseLessons")
    val lessons: String?,
    /** The parent's message. May be empty - Librus does not require it. */
    @ColumnInfo(name = "excuseMessage")
    val message: String,

    @ColumnInfo(name = "excuseStatus")
    val status: Status,
    @ColumnInfo(name = "excuseJustifiedAbsences")
    val justifiedAbsences: Int = 0,
    @ColumnInfo(name = "excuseNotifiedTeachers")
    val notifiedTeachers: String? = null,
    @ColumnInfo(name = "excuseHasAttachment")
    val hasAttachment: Boolean = false,
    @ColumnInfo(name = "excusePostDate")
    val postDate: Long = System.currentTimeMillis(),
) {
    companion object {
        fun statusOf(apiValue: String?) = when (apiValue?.lowercase(java.util.Locale.ROOT)) {
            "accept" -> Status.ACCEPT
            "reject" -> Status.REJECT
            "send" -> Status.SEND
            else -> Status.UNKNOWN
        }
    }

    enum class Status {
        /** The server returned a status this version does not recognize. */
        UNKNOWN,

        /** Sent, awaiting the teacher's decision. */
        SEND,
        ACCEPT,
        REJECT,
    }

    /** Lesson numbers this excuse covers. Empty means whole days. */
    @delegate:Ignore
    @delegate:Transient
    val lessonNumbers by lazy {
        lessons?.split(",")?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()
    }

    val isWholeDays
        get() = lessonNumbers.isEmpty()

    val isRange
        get() = dateFrom != dateTo
}
