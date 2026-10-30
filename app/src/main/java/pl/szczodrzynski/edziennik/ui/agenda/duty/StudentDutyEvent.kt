package pl.szczodrzynski.edziennik.ui.agenda.duty

import pl.szczodrzynski.edziennik.core.manager.EventManager
import pl.szczodrzynski.edziennik.data.db.entity.StudentDuty
import pl.szczodrzynski.edziennik.ui.agenda.BaseEvent
import pl.szczodrzynski.edziennik.utils.models.Date

class StudentDutyEvent(val duty: StudentDuty) : BaseEvent(
    id = -(Date.fromY_m_d(duty.dateFrom).value.toLong() * 100000000L + Date.fromY_m_d(duty.dateTo).value),
    time = Date.fromY_m_d(duty.dateFrom).asCalendar,
    color = EventManager.getSpecialColor(duty.profileId, EventManager.COLOR_KEY_STUDENT_DUTY, StudentDuty.COLOR),
    showBadge = false,
) {
    override fun copy() = StudentDutyEvent(duty)
}
