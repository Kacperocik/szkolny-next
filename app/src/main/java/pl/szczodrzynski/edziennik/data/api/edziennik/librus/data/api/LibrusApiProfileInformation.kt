package pl.szczodrzynski.edziennik.data.api.edziennik.librus.data.api

import com.google.gson.JsonObject
import pl.szczodrzynski.edziennik.data.api.*
import pl.szczodrzynski.edziennik.data.api.edziennik.librus.DataLibrus
import pl.szczodrzynski.edziennik.data.api.edziennik.librus.data.LibrusApi
import pl.szczodrzynski.edziennik.ext.*

class LibrusApiProfileInformation(data: DataLibrus, done: (JsonObject) -> Unit) : LibrusApi(data, null) {
    init {
        apiGet("LibrusApiProfileInformation", "Schools") { school ->
            requireNotNull(school.getJsonObject("School"))
            apiGet("LibrusApiProfileInformation", "Classes") { schoolClass ->
                val row = requireNotNull(schoolClass.getJsonObject("Class"))
                val ids = (row.getJsonArray("ClassTutors")?.mapNotNull { it.asJsonObject.getLong("Id") }
                    ?: listOfNotNull(row.getJsonObject("ClassTutor")?.getLong("Id"))).distinct()
                fun photos(tutors: JsonObject) {
                    apiGet("LibrusApiProfileInformation", "Auth/Photos", baseUrl = LIBRUS_API_3_URL,
                        ignoreErrors = listOf(ERROR_LIBRUS_API_ACCESS_DENIED, ERROR_LIBRUS_API_RESOURCE_ACCESS_DENIED,
                            ERROR_LIBRUS_API_RESOURCE_NOT_FOUND, ERROR_LIBRUS_API_DATA_NOT_FOUND)) { photo ->
                        done(JsonObject("school" to school.getJsonObject("School"), "schoolClass" to row,
                            "tutors" to tutors, "photos" to photo.getJsonObject("data"),
                            "downloadedAt" to System.currentTimeMillis()))
                    }
                }
                if (ids.isEmpty()) photos(JsonObject()) else
                    apiGet("LibrusApiProfileInformation", "Users/" + ids.joinToString(",")) { photos(it) }
            }
        }
    }
}
