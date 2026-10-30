package pl.szczodrzynski.edziennik.data.db.entity

import androidx.room.Entity

@Entity(tableName = "studentDuties", primaryKeys = ["profileId", "dateFrom", "dateTo"])
data class StudentDuty(val profileId: Int, val dateFrom: String, val dateTo: String) {
    companion object { const val COLOR = 0xff009688.toInt() }
    fun startsOn(date: String) = dateFrom == date
}
