package pl.szczodrzynski.edziennik.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration102 : Migration(101, 102) {

    override fun migrate(db: SupportSQLiteDatabase) {
        // store the original type of deleted messages, to allow restoring them
        db.execSQL("ALTER TABLE messages ADD COLUMN messageTypeBeforeDelete INTEGER")
    }
}
