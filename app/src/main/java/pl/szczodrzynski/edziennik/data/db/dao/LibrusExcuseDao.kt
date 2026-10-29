package pl.szczodrzynski.edziennik.data.db.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import pl.szczodrzynski.edziennik.data.db.entity.LibrusExcuse
import pl.szczodrzynski.edziennik.utils.models.Date

@Dao
interface LibrusExcuseDao {
    companion object {
        private const val ORDER_BY = "ORDER BY excuseDateFrom DESC, excusePostDate DESC"
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun add(excuse: LibrusExcuse)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun addAll(excuseList: List<LibrusExcuse>)

    @Query("DELETE FROM librusExcuses WHERE profileId = :profileId")
    fun clear(profileId: Int)

    @Query("SELECT * FROM librusExcuses WHERE profileId = :profileId $ORDER_BY")
    fun getAll(profileId: Int): LiveData<List<LibrusExcuse>>

    @Query("SELECT * FROM librusExcuses WHERE profileId = :profileId $ORDER_BY")
    fun getAllNow(profileId: Int): List<LibrusExcuse>

    /**
     * Finds an excuse covering the given lesson - either one listing that exact lesson number,
     * or a whole-day/range excuse containing the date.
     */
    @Query(
        """SELECT * FROM librusExcuses
        WHERE profileId = :profileId
          AND :date BETWEEN excuseDateFrom AND excuseDateTo
          AND (excuseLessons IS NULL OR excuseLessons = ''
               OR ',' || excuseLessons || ',' LIKE '%,' || :lessonNumber || ',%')
        ORDER BY excuseId DESC LIMIT 1"""
    )
    fun getForLessonNow(profileId: Int, date: Date, lessonNumber: Int): LibrusExcuse?
}
