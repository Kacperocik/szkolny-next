package pl.szczodrzynski.edziennik.data.db.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import pl.szczodrzynski.edziennik.data.db.entity.LibrusSurvey

@Dao
abstract class LibrusSurveyDao {
    @Query("SELECT * FROM librusSurveys WHERE profileId = :profileId ORDER BY publishedDate DESC, id DESC")
    abstract fun getAll(profileId: Int): LiveData<List<LibrusSurvey>>

    @Query("DELETE FROM librusSurveys WHERE profileId = :profileId")
    abstract fun clear(profileId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun addAll(items: List<LibrusSurvey>)

    @Transaction
    open fun replace(profileId: Int, items: List<LibrusSurvey>) {
        require(items.all { it.profileId == profileId })
        clear(profileId)
        addAll(items)
    }
}
