package pl.szczodrzynski.edziennik.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration105 : Migration(104, 105) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""CREATE TABLE IF NOT EXISTS `studentDuties` (
            `profileId` INTEGER NOT NULL,
            `dateFrom` TEXT NOT NULL,
            `dateTo` TEXT NOT NULL,
            PRIMARY KEY(`profileId`, `dateFrom`, `dateTo`))""".trimIndent())
    }
}
