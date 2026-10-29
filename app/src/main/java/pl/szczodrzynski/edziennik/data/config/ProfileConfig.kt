/*
 * Copyright (c) Kuba Szczodrzyński 2019-11-27.
 */

package pl.szczodrzynski.edziennik.data.config

import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.data.config.migration.ProfileConfigMigration2
import pl.szczodrzynski.edziennik.data.config.migration.ProfileConfigMigration3
import pl.szczodrzynski.edziennik.data.config.migration.ProfileConfigMigration4
import pl.szczodrzynski.edziennik.data.config.migration.ProfileConfigMigration5
import pl.szczodrzynski.edziennik.data.config.migration.ProfileConfigMigration6
import pl.szczodrzynski.edziennik.data.db.entity.ConfigEntry
import pl.szczodrzynski.edziennik.data.db.entity.Profile.Companion.AGENDA_DEFAULT
import pl.szczodrzynski.edziennik.data.enums.FeatureType
import pl.szczodrzynski.edziennik.data.enums.NotificationType
import pl.szczodrzynski.edziennik.ui.home.HomeCardModel
import com.google.gson.JsonObject
import pl.szczodrzynski.edziennik.core.manager.AttendanceManager.Companion.SHAPE_MODE_ROUNDED_RECT
import pl.szczodrzynski.edziennik.core.manager.GradesManager.Companion.COLOR_MODE_WEIGHTED
import pl.szczodrzynski.edziennik.core.manager.GradesManager.Companion.SHAPE_MODE_ROUNDED_RECT as GRADES_SHAPE_MODE_ROUNDED_RECT
import pl.szczodrzynski.edziennik.core.manager.GradesManager.Companion.UNIVERSITY_AVERAGE_MODE_ECTS
import pl.szczodrzynski.edziennik.core.manager.GradesManager.Companion.YEAR_ALL_GRADES

class ProfileConfig(
    app: App,
    profileId: Int,
    entries: List<ConfigEntry>?,
) : BaseConfig<ProfileConfig>(app, profileId, entries) {

    override val dataVersion = 6
    override val migrations
        get() = mapOf(
            2 to ProfileConfigMigration2(),
            3 to ProfileConfigMigration3(),
            4 to ProfileConfigMigration4(),
            5 to ProfileConfigMigration5(),
            6 to ProfileConfigMigration6(),
        )

    val grades by lazy { Grades() }
    val ui by lazy { UI() }
    val sync by lazy { Sync() }
    val attendance by lazy { Attendance() }

    var shareByDefault by config<Boolean>(false)
    var librusExcusesUnavailable by config<Boolean>(false)
    var importedUiFeatures by config<Set<FeatureType>> { emptySet() }

    inner class Grades {
        var averageWithoutWeight by config<Boolean>(true)
        var colorMode by config<Int>(COLOR_MODE_WEIGHTED)
        var shapeMode by config<Int>(GRADES_SHAPE_MODE_ROUNDED_RECT)
        var dontCountEnabled by config<Boolean>(false)
        var dontCountGrades by config<List<String>> { listOf() }
        var hideImproved by config<Boolean>(false)
        var hideNoGrade by config<Boolean>(false)
        var hideSticksFromOld by config<Boolean>(false)
        var minusValue by config<Float?>(null)
        var plusValue by config<Float?>(null)
        var yearAverageMode by config<Int>(YEAR_ALL_GRADES)
        var universityAverageMode by config<Int>(UNIVERSITY_AVERAGE_MODE_ECTS)
        var countEctsInProgress by config<Boolean>(false)
    }

    inner class UI {
        var agendaViewType by config<Int>(AGENDA_DEFAULT)
        var agendaCompactMode by config<Boolean>(false)
        var agendaGroupByType by config<Boolean>(false)
        var agendaLessonChanges by config<Boolean>(true)
        var agendaLessonChangesDots by config<Boolean>(true)
        var agendaTeacherAbsence by config<Boolean>(true)
        var agendaTeacherAbsenceDots by config<Boolean>(true)
        var agendaSubjectImportant by config<Boolean>(false)
        var agendaElearningMark by config<Boolean>(false)
        var agendaElearningGroup by config<Boolean>(true)
        var agendaClassicCards by config<Boolean>(false)
        var agendaShowEmptyDays by config<Boolean>(true)
        var agendaCalendarWeeks by config<Int>(0)

        var homeCards by config<List<HomeCardModel>> { listOf() }
        var homeCardsLocked by config<Boolean>(false)
        var homeEventsLimit by config<Int>(5)
        var homeEventsWeeks by config<Int>(2)
        var homeGradesWeeks by config<Int>(2)
        var homeAnnouncementsLimit by config<Int>(4)
        var homeNotesLimit by config<Int>(4)
        var homeNotesCornerButton by config<Boolean>(false)
        var homeTimetableClassroomParentheses by config<Boolean>(true)
        var homeTimetableCornerButtons by config<Boolean>(false)
        var homeTimetableWavyProgress by config<Boolean>(true)

        var messagesTwoColumnLandscape by config<Boolean>(true)
        var announcementsTwoColumnLandscape by config<Boolean>(true)
        var homeworkTwoColumnLandscape by config<Boolean>(true)
        var notesTwoColumnLandscape by config<Boolean>(true)
        var messagesGreetingOnCompose by config<Boolean>(true)
        var messagesGreetingOnReply by config<Boolean>(true)
        var messagesGreetingOnForward by config<Boolean>(false)
        var messagesGreetingText by config<String?>(null)

        var timetableShowAttendance by config<Boolean>(true)
        var timetableShowEvents by config<Boolean>(true)
        var timetableHideClassLessons by config<Boolean>(false)
        var timetableTrimHourRange by config<Boolean>(false)
        var timetableColorSubjectName by config<Boolean>(false)
        var timetableColorBorders by config<Boolean>(true)
        var timetableSubjectColors by config<JsonObject> { JsonObject() }
        var agendaEventColors by config<JsonObject> { JsonObject() }
    }

    inner class Sync {
        var notificationFilter by config(NotificationType.Companion::getDefaultConfig)
        var lastSuccessfulSync by config<Long>(0L)
        var luckyNumberOnlyYours by config<Boolean>(false)
        var ongoingLessonNotification by config<Boolean>(false)
    }

    inner class Attendance {
        var attendancePageSelection by config<Int>(1)
        var groupConsecutiveDays by config<Boolean>(true)
        var shapeMode by config<Int>("attendanceShapeMode", SHAPE_MODE_ROUNDED_RECT)
        var showPresenceInMonth by config<Boolean>(false)
        var useSymbols by config<Boolean>(false)
    }
}
