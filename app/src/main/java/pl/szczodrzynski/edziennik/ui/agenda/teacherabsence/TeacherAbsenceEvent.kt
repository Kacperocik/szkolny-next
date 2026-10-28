/*
 * Copyright (c) Kuba Szczodrzyński 2021-4-8.
 */

package pl.szczodrzynski.edziennik.ui.agenda.teacherabsence

import pl.szczodrzynski.edziennik.core.manager.EventManager
import pl.szczodrzynski.edziennik.ui.agenda.BaseEvent
import pl.szczodrzynski.edziennik.utils.models.Date

class TeacherAbsenceEvent(
    val profileId: Int,
    val date: Date,
    val count: Int
) : BaseEvent(
    id = date.value.toLong(),
    time = date.asCalendar,
    color = EventManager.getSpecialColor(
        profileId,
        EventManager.COLOR_KEY_TEACHER_ABSENCE,
        EventManager.DEFAULT_TEACHER_ABSENCE_COLOR
    ),
    showBadge = false
) {
    override fun copy() = TeacherAbsenceEvent(profileId, date, count)
}
