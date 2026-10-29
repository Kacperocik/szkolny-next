package pl.szczodrzynski.edziennik.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration103 : Migration(102, 103) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `librusExcuses` (" +
                "`profileId` INTEGER NOT NULL, " +
                "`excuseId` INTEGER NOT NULL, " +
                "`excuseDateFrom` TEXT NOT NULL, " +
                "`excuseDateTo` TEXT NOT NULL, " +
                "`excuseLessons` TEXT, " +
                "`excuseMessage` TEXT NOT NULL, " +
                "`excuseStatus` TEXT NOT NULL, " +
                "`excuseJustifiedAbsences` INTEGER NOT NULL, " +
                "`excuseNotifiedTeachers` TEXT, " +
                "`excuseHasAttachment` INTEGER NOT NULL, " +
                "`excusePostDate` INTEGER NOT NULL, " +
                "PRIMARY KEY(`profileId`, `excuseId`))"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_librusExcuses_profileId_excuseDateFrom_excuseDateTo` " +
                "ON `librusExcuses` (`profileId`, `excuseDateFrom`, `excuseDateTo`)"
        )
    }
}
