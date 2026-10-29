package pl.szczodrzynski.edziennik.data.api.events

import pl.szczodrzynski.edziennik.data.db.entity.LibrusExcuse

/** Posted after an excuse send attempt. A null [excuse] means the send failed. */
data class ExcuseSentEvent(val profileId: Int, val excuse: LibrusExcuse?)
