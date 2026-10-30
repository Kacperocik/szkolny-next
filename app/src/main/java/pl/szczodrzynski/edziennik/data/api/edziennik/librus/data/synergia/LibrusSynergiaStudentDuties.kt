package pl.szczodrzynski.edziennik.data.api.edziennik.librus.data.synergia

import com.google.gson.JsonObject
import im.wangchao.mhttp.Request
import im.wangchao.mhttp.Response
import im.wangchao.mhttp.callback.JsonCallbackHandler
import im.wangchao.mhttp.callback.TextCallbackHandler
import pl.szczodrzynski.edziennik.data.api.*
import pl.szczodrzynski.edziennik.data.api.edziennik.librus.DataLibrus
import pl.szczodrzynski.edziennik.data.api.edziennik.librus.ENDPOINT_LIBRUS_SYNERGIA_STUDENT_DUTIES
import pl.szczodrzynski.edziennik.data.api.models.ApiError
import pl.szczodrzynski.edziennik.data.db.entity.StudentDuty
import pl.szczodrzynski.edziennik.data.enums.FeatureType
import pl.szczodrzynski.edziennik.ext.*
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean

class LibrusSynergiaStudentDuties(
    private val data: DataLibrus,
    private val onSuccess: (Int) -> Unit,
) {
    companion object { private const val TAG = "LibrusSynergiaStudentDuties" }
    private val finished = AtomicBoolean()

    private fun finish(error: ApiError? = null) {
        if (!finished.compareAndSet(false, true)) return
        error?.let { data.error(it.setCritical(false)) }
        data.setSyncNext(ENDPOINT_LIBRUS_SYNERGIA_STUDENT_DUTIES, 4 * HOUR, FeatureType.AGENDA)
        onSuccess(ENDPOINT_LIBRUS_SYNERGIA_STUDENT_DUTIES)
    }

    init {
        Request.builder()
            .url("$LIBRUS_SYNERGIA_URL/dyzury")
            .userAgent(LIBRUS_USER_AGENT)
            .get()
            .callback(object : TextCallbackHandler() {
                override fun onSuccess(text: String?, response: Response?) {
                    if (data.cancelled) { finish(); return }
                    try {
                        require(response?.code() in 200..299) { "Student duty page request failed" }
                        fetchDuties(extractStudentDutyCsrf(requireNotNull(text)))
                    } catch (e: Exception) {
                        finish(ApiError(TAG, EXCEPTION_LIBRUS_SYNERGIA_REQUEST).withThrowable(e))
                    }
                }
                override fun onFailure(response: Response?, throwable: Throwable?) {
                    finish(ApiError(TAG, ERROR_REQUEST_FAILURE).withResponse(response).withThrowable(throwable))
                }
            })
            .build()
            .enqueue()
    }

    private fun fetchDuties(csrfToken: String) {
        // This endpoint returns JSON even though its content type is text/html.
        Request.builder()
            .url("$LIBRUS_SYNERGIA_URL/duzury_ucznia")
            .userAgent(LIBRUS_USER_AGENT)
            .addHeader("requestkey", csrfToken)
            .addHeader("X-Requested-With", "XMLHttpRequest")
            .addHeader("Accept", "application/json, text/javascript, */*; q=0.01")
            .addHeader("Origin", LIBRUS_SYNERGIA_URL)
            .addHeader("Referer", "$LIBRUS_SYNERGIA_URL/dyzury")
            .post()
            .callback(object : JsonCallbackHandler() {
                override fun onSuccess(json: JsonObject?, response: Response?) {
                    if (data.cancelled) { finish(); return }
                    if (response?.code() !in 200..299 || json == null) {
                        finish(ApiError(TAG, ERROR_RESPONSE_EMPTY).withResponse(response))
                        return
                    }
                    try {
                        val items = parseStudentDuties(data.profileId, json)
                        if (!data.cancelled) data.db.studentDutyDao().replace(data.profileId, items)
                        finish()
                    } catch (e: Exception) {
                        finish(ApiError(TAG, EXCEPTION_LIBRUS_SYNERGIA_REQUEST).withThrowable(e))
                    }
                }
                override fun onFailure(response: Response?, throwable: Throwable?) {
                    if (response?.code() in listOf(401, 403)) data.synergiaSessionIdExpiryTime = 0L
                    finish(ApiError(TAG, ERROR_REQUEST_FAILURE).withResponse(response).withThrowable(throwable))
                }
            })
            .build()
            .enqueue()
    }
}

internal fun parseStudentDuties(profileId: Int, json: JsonObject): List<StudentDuty> =
    requireNotNull(json.getJsonArray("records")) { "Missing student duty records" }.map { element ->
        val record = element.asJsonObject
        val from = LocalDate.parse(requireNotNull(record.getString("dateFrom")))
        val to = LocalDate.parse(requireNotNull(record.getString("dateTo")))
        require(!to.isBefore(from)) { "Invalid student duty date range" }
        StudentDuty(profileId, from.toString(), to.toString())
    }.distinct()

internal fun extractStudentDutyCsrf(page: String): String {
    require(page.contains("dutyMessageBox")) { "Missing student duty page" }
    val token = Regex("""\bvar\s+csrfTokenValue\s*=\s*["']([^"'\r\n]+)["']""")
        .find(page)?.groupValues?.get(1)
    return requireNotNull(token?.takeIf { it.isNotBlank() && it.length <= 8192 }) { "Missing student duty request key" }
}
