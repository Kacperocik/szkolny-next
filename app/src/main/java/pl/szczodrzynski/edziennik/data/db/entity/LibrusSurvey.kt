package pl.szczodrzynski.edziennik.data.db.entity

import androidx.room.Entity

@Entity(tableName = "librusSurveys", primaryKeys = ["profileId", "id"])
data class LibrusSurvey(val profileId: Int, val id: Long, val title: String, val publishedDate: String)
