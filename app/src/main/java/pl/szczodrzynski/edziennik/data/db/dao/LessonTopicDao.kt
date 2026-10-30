package pl.szczodrzynski.edziennik.data.db.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import pl.szczodrzynski.edziennik.data.db.entity.LessonTopic

@Dao
abstract class LessonTopicDao {
    @Query("SELECT * FROM lessonTopics WHERE profileId = :profileId ORDER BY date DESC, lessonNumber DESC, id DESC")
    abstract fun getAll(profileId: Int): LiveData<List<LessonTopic>>

    @Query("SELECT * FROM lessonTopics WHERE profileId = :profileId AND date = :date ORDER BY planned, id")
    abstract fun getForDate(profileId: Int, date: String): LiveData<List<LessonTopic>>

    @Query("DELETE FROM lessonTopics WHERE profileId = :profileId AND planned = :planned")
    abstract fun clear(profileId: Int, planned: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun addAll(items: List<LessonTopic>)

    @Transaction
    open fun replace(profileId: Int, planned: Boolean, items: List<LessonTopic>) {
        require(items.all { it.profileId == profileId && it.planned == planned })
        clear(profileId, planned)
        addAll(items)
    }
}
