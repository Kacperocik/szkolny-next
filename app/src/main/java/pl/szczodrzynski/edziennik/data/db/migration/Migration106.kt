package pl.szczodrzynski.edziennik.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration106 : Migration(105, 106) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""CREATE TABLE IF NOT EXISTS `librusSurveys` (
            `profileId` INTEGER NOT NULL,
            `id` INTEGER NOT NULL,
            `title` TEXT NOT NULL,
            `publishedDate` TEXT NOT NULL,
            PRIMARY KEY(`profileId`, `id`))""".trimIndent())
    }
}
