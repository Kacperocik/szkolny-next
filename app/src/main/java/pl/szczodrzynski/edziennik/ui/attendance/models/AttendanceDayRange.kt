/*
 * Copyright (c) Kuba Szczodrzyński 2020-4-30.
 */

package pl.szczodrzynski.edziennik.ui.attendance.models

import pl.szczodrzynski.edziennik.data.db.full.AttendanceFull
import pl.szczodrzynski.edziennik.ui.grades.models.ExpandableItemModel
import pl.szczodrzynski.edziennik.utils.models.Date

data class AttendanceDayRange(
        var rangeStart: Date,
        var rangeEnd: Date?,
        override val items: MutableList<AttendanceFull> = mutableListOf()
) : ExpandableItemModel<AttendanceFull>(items) {
    override var level = 1

    var lastAddedDate = 0L

    val hasUnseen: Boolean
        get() = items.any { !it.seen }
}
