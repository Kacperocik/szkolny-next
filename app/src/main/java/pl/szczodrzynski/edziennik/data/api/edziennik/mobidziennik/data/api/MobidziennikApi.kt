/*
 * Copyright (c) Kuba Szczodrzyński 2019-10-11.
 */

package pl.szczodrzynski.edziennik.data.api.edziennik.mobidziennik.data.api

import pl.szczodrzynski.edziennik.data.api.ERROR_MOBIDZIENNIK_WEB_INVALID_RESPONSE
import pl.szczodrzynski.edziennik.data.api.edziennik.mobidziennik.DataMobidziennik
import pl.szczodrzynski.edziennik.data.api.edziennik.mobidziennik.ENDPOINT_MOBIDZIENNIK_API_MAIN
import pl.szczodrzynski.edziennik.data.api.edziennik.mobidziennik.data.MobidziennikWeb
import pl.szczodrzynski.edziennik.data.api.models.ApiError
import pl.szczodrzynski.edziennik.data.db.entity.SYNC_ALWAYS
import pl.szczodrzynski.edziennik.data.enums.FeatureType

class MobidziennikApi(override val data: DataMobidziennik,
                      override val lastSync: Long?,
                      val onSuccess: (endpointId: Int) -> Unit
) : MobidziennikWeb(data, lastSync)  {
    companion object {
        private const val TAG = "MobidziennikApi"
    }

    init {
        webGet(TAG, "/api/zrzutbazy") { text ->
            if (!text.contains("T@B#LA")) {
                data.error(ApiError(TAG, ERROR_MOBIDZIENNIK_WEB_INVALID_RESPONSE)
                        .withApiResponse(text))
                return@webGet
            }

            val tables = text.split("T@B#LA")
            tables.forEachIndexed { index, table ->
                val rows = table.split("\n")
                when (index) {
                    0 -> MobidziennikApiUsers(data, rows)
                    3 -> MobidziennikApiDates(data, rows)
                    4 -> MobidziennikApiSubjects(data, rows)
                    7 -> MobidziennikApiTeams(data, rows, null)
                    8 -> MobidziennikApiStudent(data, rows)
                    9 -> MobidziennikApiTeams(data, null, rows)
                    14 -> if (data.shouldSyncFeature(FeatureType.GRADES)) MobidziennikApiGradeCategories(data, rows)
                    15 -> MobidziennikApiLessons(data, rows)
                    //16 -> MobidziennikApiAttendance(data, rows) // disabled since the new web scrapper is used
                    17 -> if (data.shouldSyncFeature(FeatureType.BEHAVIOUR)) MobidziennikApiNotices(data, rows)
                    18 -> if (data.shouldSyncFeature(FeatureType.GRADES)) MobidziennikApiGrades(data, rows)
                    21 -> if (data.shouldSyncFeature(FeatureType.AGENDA)) MobidziennikApiEvents(data, rows)
                    23 -> if (data.shouldSyncFeature(FeatureType.HOMEWORK)) MobidziennikApiHomework(data, rows)
                    24 -> if (data.shouldSyncFeature(FeatureType.TIMETABLE)) MobidziennikApiTimetable(data, rows)
                }
            }

            data.setSyncNext(ENDPOINT_MOBIDZIENNIK_API_MAIN, SYNC_ALWAYS)
            onSuccess(ENDPOINT_MOBIDZIENNIK_API_MAIN)
        }
    }
}
