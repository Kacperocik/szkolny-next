package pl.szczodrzynski.edziennik.data.db.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import pl.szczodrzynski.edziennik.data.db.entity.StudentDuty
import pl.szczodrzynski.edziennik.utils.models.Date

@Dao
abstract class StudentDutyDao {
    @Query("SELECT * FROM studentDuties WHERE profileId = :profileId ORDER BY dateFrom, dateTo")
    abstract fun getAll(profileId: Int): LiveData<List<StudentDuty>>

    @Query("SELECT * FROM studentDuties WHERE profileId = :profileId AND dateFrom = :date ORDER BY dateTo")
    abstract fun getStartingOn(profileId: Int, date: String): LiveData<List<StudentDuty>>

    @Query("DELETE FROM studentDuties WHERE profileId = :profileId AND dateTo >= :today")
    abstract fun clearCurrentAndFuture(profileId: Int, today: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun addAll(items: List<StudentDuty>)

    @Transaction
    open fun replace(profileId: Int, items: List<StudentDuty>, today: String = Date.getToday().stringY_m_d) {
        require(items.all { it.profileId == profileId })
        // Synergia omits completed duties; retain them as local history.
        clearCurrentAndFuture(profileId, today)
        addAll(items)
    }
}
