/*
 * Copyright (c) Kuba Szczodrzyński 2020-4-30.
 */

package pl.szczodrzynski.edziennik.ui.attendance.models

import pl.szczodrzynski.edziennik.data.db.entity.AttendanceType
import pl.szczodrzynski.edziennik.data.db.full.AttendanceFull
import pl.szczodrzynski.edziennik.ui.grades.models.ExpandableItemModel

data class AttendanceMonth(
        val year: Int,
        val month: Int,
        override val items: MutableList<AttendanceFull> = mutableListOf()
) : ExpandableItemModel<AttendanceFull>(items) {
    override var level = 1

    var lastAddedDate = 0L

    val hasUnseen: Boolean
        get() = items.any { !it.seen }

    var typeCountMap: Map<AttendanceType, Int> = mapOf()
    var percentage: Float = 0f
}
