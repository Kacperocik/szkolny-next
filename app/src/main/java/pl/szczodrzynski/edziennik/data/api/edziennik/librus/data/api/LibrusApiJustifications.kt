package pl.szczodrzynski.edziennik.data.api.edziennik.librus.data.api

import com.google.gson.JsonObject
import pl.szczodrzynski.edziennik.data.api.ERROR_LIBRUS_API_JUSTIFICATIONS_NOT_AVAILABLE
import pl.szczodrzynski.edziennik.data.api.LIBRUS_API_3_URL
import pl.szczodrzynski.edziennik.data.api.edziennik.librus.DataLibrus
import pl.szczodrzynski.edziennik.data.api.edziennik.librus.ENDPOINT_LIBRUS_API_JUSTIFICATIONS
import pl.szczodrzynski.edziennik.data.api.edziennik.librus.data.LibrusApi
import pl.szczodrzynski.edziennik.data.db.entity.LibrusExcuse
import pl.szczodrzynski.edziennik.ext.*
import pl.szczodrzynski.edziennik.utils.models.Date

/** Downloads the parent account's absence excuses through Librus API 3.0. */
class LibrusApiJustifications(
    override val data: DataLibrus,
    override val lastSync: Long?,
    val onSuccess: (endpointId: Int) -> Unit,
) : LibrusApi(data, lastSync) {
    companion object {
        const val TAG = "LibrusApiJustifications"
    }

    init {
        val profile = data.profile
        if (profile == null) {
            onSuccess(ENDPOINT_LIBRUS_API_JUSTIFICATIONS)
        } else {
            val dateFrom = profile.dateSemester1Start.stringY_m_d
            val dateTo = profile.dateYearEnd.stringY_m_d

            apiGet(
                TAG,
                "Justifications?dateFrom=$dateFrom&dateTo=$dateTo",
                baseUrl = LIBRUS_API_3_URL,
                ignoreErrors = listOf(ERROR_LIBRUS_API_JUSTIFICATIONS_NOT_AVAILABLE),
            ) { json ->
                if (json.getJsonArray("data") == null &&
                    (json.getString("Code") ?: json.getString("Message")) in
                    listOf("AccessDeny", "Request is denied", "Resource not found", "NotFound")
                ) {
                    data.app.config[profileId].librusExcusesUnavailable = true
                    data.setSyncNext(ENDPOINT_LIBRUS_API_JUSTIFICATIONS, 1 * WEEK)
                    onSuccess(ENDPOINT_LIBRUS_API_JUSTIFICATIONS)
                    return@apiGet
                }
                val justifications = requireNotNull(json.getJsonArray("data")) {
                    "Missing justifications array"
                }

                val excuseList = justifications.map { requireNotNull(it.asJsonObject.toLibrusExcuse(profileId)) { "Invalid justification" } }

                data.db.runInTransaction {
                    data.db.librusExcuseDao().clear(profileId)
                    data.db.librusExcuseDao().addAll(excuseList)
                }

                data.app.config[profileId].librusExcusesUnavailable = false
                data.setSyncNext(ENDPOINT_LIBRUS_API_JUSTIFICATIONS, 2 * HOUR)
                onSuccess(ENDPOINT_LIBRUS_API_JUSTIFICATIONS)
            }
        }
    }

}

internal fun JsonObject.toLibrusExcuse(profileId: Int): LibrusExcuse? {
    val id = getLong("id")?.takeIf { it > 0 } ?: return null
    val from = getString("dateFrom") ?: return null
    val dateFrom = Date.fromY_m_d(from)
    require(dateFrom.stringY_m_d == from) { "Invalid start date" }
    val to = getString("dateTo") ?: from
    val dateTo = Date.fromY_m_d(to)
    require(dateTo.stringY_m_d == to && dateTo >= dateFrom) { "Invalid end date" }

    // the API returns objects here - [{"number": 7, "date": "2026-09-07"}] - even though
    // POST /Justifications takes plain numbers
    val lessons = getJsonArray("lessons")
        ?.map {
            val number = when {
                it.isJsonObject -> it.asJsonObject.getInt("number")
                it.isJsonPrimitive && it.asJsonPrimitive.isNumber -> it.asInt
                else -> null
            }
            requireNotNull(number?.takeIf { value -> value >= 0 }) { "Invalid lesson number" }
        }
        ?.joinToString(",")

    return LibrusExcuse(
        profileId = profileId,
        id = id,
        dateFrom = dateFrom,
        dateTo = dateTo,
        lessons = lessons,
        message = getString("messageFromParent") ?: "",
        status = LibrusExcuse.statusOf(getString("justificationStatus")),
        justifiedAbsences = getInt("justifiedAbsences") ?: 0,
        // also a list of objects - [{"name": "Kowalska Anna"}]
        notifiedTeachers = getJsonArray("notifiedTeachers")
            ?.mapNotNull {
                when {
                    it.isJsonObject -> it.asJsonObject.getString("name")
                    it.isJsonPrimitive -> it.asString
                    else -> null
                }
            }
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString(", "),
        hasAttachment = getBoolean("attachment") ?: false,
        postDate = getString("postDate")?.let { Date.fromIso(it) } ?: System.currentTimeMillis(),
    )
}
