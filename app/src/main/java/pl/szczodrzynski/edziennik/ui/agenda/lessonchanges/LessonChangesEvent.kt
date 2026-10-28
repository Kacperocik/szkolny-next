/*
 * Copyright (c) Kuba Szczodrzyński 2021-4-8.
 */

package pl.szczodrzynski.edziennik.ui.agenda.lessonchanges

import pl.szczodrzynski.edziennik.core.manager.EventManager
import pl.szczodrzynski.edziennik.ui.agenda.BaseEvent
import pl.szczodrzynski.edziennik.utils.models.Date

class LessonChangesEvent(
    val profileId: Int,
    val date: Date,
    val count: Int,
    showBadge: Boolean
) : BaseEvent(
    id = date.value.toLong(),
    time = date.asCalendar,
    color = EventManager.getSpecialColor(
        profileId,
        EventManager.COLOR_KEY_LESSON_CHANGES,
        EventManager.DEFAULT_LESSON_CHANGES_COLOR
    ),
    showBadge = false,
    showItemBadge = showBadge
) {
    override fun copy() = LessonChangesEvent(profileId, date, count, showItemBadge)

    override fun getShowBadge() = false
}
