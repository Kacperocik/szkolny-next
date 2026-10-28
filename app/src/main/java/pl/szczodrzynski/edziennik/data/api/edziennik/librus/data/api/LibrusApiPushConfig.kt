/*
 * Copyright (c) Kuba Szczodrzyński 2020-2-21.
 */

package pl.szczodrzynski.edziennik.data.api.edziennik.librus.data.api

import pl.szczodrzynski.edziennik.data.api.ERROR_LIBRUS_API_OTHER
import pl.szczodrzynski.edziennik.data.api.edziennik.librus.DataLibrus
import pl.szczodrzynski.edziennik.data.api.edziennik.librus.ENDPOINT_LIBRUS_API_PUSH_CONFIG
import pl.szczodrzynski.edziennik.data.api.edziennik.librus.data.LibrusApi
import pl.szczodrzynski.edziennik.data.api.models.ApiError
import pl.szczodrzynski.edziennik.data.db.entity.SYNC_ALWAYS
import pl.szczodrzynski.edziennik.ext.JsonObject
import pl.szczodrzynski.edziennik.ext.getInt
import pl.szczodrzynski.edziennik.ext.getJsonObject

class LibrusApiPushConfig(override val data: DataLibrus,
                          override val lastSync: Long?,
                          val onSuccess: (endpointId: Int) -> Unit
) : LibrusApi(data, lastSync) {
    companion object {
        const val TAG = "LibrusApiPushConfig"
    }

    init {
        val tokenLibrus = data.app.config.sync.tokenLibrus
        if (tokenLibrus.isNullOrBlank()) {
            onSuccess(ENDPOINT_LIBRUS_API_PUSH_CONFIG)
        } else {
            apiGet(TAG, "ChangeRegister", payload = JsonObject(
                    "provider" to "FCM",
                    "device" to tokenLibrus,
                    "sendPush" to "1",
                    "appVersion" to 4
            )) { json ->
                val deviceId = json.getJsonObject("ChangeRegister")?.getInt("Id")
                if (deviceId == null) {
                    data.error(ApiError(TAG, ERROR_LIBRUS_API_OTHER).withApiResponse(json))
                    return@apiGet
                }
                data.pushDeviceId = deviceId

                // sync always: this endpoint has .shouldSync set
                data.setSyncNext(ENDPOINT_LIBRUS_API_PUSH_CONFIG, SYNC_ALWAYS)
                data.app.config.sync.tokenLibrusList =
                        data.app.config.sync.tokenLibrusList + profileId
                onSuccess(ENDPOINT_LIBRUS_API_PUSH_CONFIG)
            }
        }
    }
}
