package pl.szczodrzynski.edziennik.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration104 : Migration(103, 104) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS `lessonTopics` (`profileId` INTEGER NOT NULL, `planned` INTEGER NOT NULL, `id` TEXT NOT NULL, `date` TEXT NOT NULL, `lessonNumber` INTEGER NOT NULL, `subjectId` INTEGER NOT NULL, `subjectName` TEXT NOT NULL, `teacherId` INTEGER NOT NULL, `teacherName` TEXT NOT NULL, `teamId` INTEGER, `topic` TEXT NOT NULL, `cancelled` INTEGER NOT NULL, `links` TEXT NOT NULL, `files` TEXT NOT NULL, PRIMARY KEY(`profileId`, `planned`, `id`))")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_lessonTopics_profileId_date` ON `lessonTopics` (`profileId`, `date`)")
    }
}
