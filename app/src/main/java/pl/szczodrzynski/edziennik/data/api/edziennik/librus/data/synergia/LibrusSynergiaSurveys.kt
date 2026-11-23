package pl.szczodrzynski.edziennik.data.api.edziennik.librus.data.synergia

import im.wangchao.mhttp.Request
import im.wangchao.mhttp.Response
import im.wangchao.mhttp.callback.TextCallbackHandler
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import pl.szczodrzynski.edziennik.data.api.*
import pl.szczodrzynski.edziennik.data.api.edziennik.librus.DataLibrus
import pl.szczodrzynski.edziennik.data.api.edziennik.librus.ENDPOINT_LIBRUS_SYNERGIA_SURVEYS
import pl.szczodrzynski.edziennik.data.api.models.ApiError
import pl.szczodrzynski.edziennik.data.db.entity.LibrusSurvey
import pl.szczodrzynski.edziennik.data.enums.FeatureType
import pl.szczodrzynski.edziennik.ext.MINUTE
import java.net.HttpURLConnection
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean

class LibrusSynergiaSurveys(private val data: DataLibrus, private val onSuccess: (Int) -> Unit) {
    companion object { private const val TAG = "LibrusSynergiaSurveys" }
    private val finished = AtomicBoolean()

    private fun finish(error: ApiError? = null) {
        if (!finished.compareAndSet(false, true)) return
        if (error != null) {
            data.error(error.setCritical(false))
        } else if (!data.cancelled) {
            data.setSyncNext(ENDPOINT_LIBRUS_SYNERGIA_SURVEYS, 30 * MINUTE, FeatureType.SURVEYS)
        }
        onSuccess(ENDPOINT_LIBRUS_SYNERGIA_SURVEYS)
    }

    init {
        fetchSurveys(retried = false)
    }

    private fun fetchSurveys(retried: Boolean) {
        if (data.cancelled) { finish(); return }
        Request.builder().url("$LIBRUS_SYNERGIA_URL/ankiety_admin_ankiety")
            .userAgent(LIBRUS_USER_AGENT).get()
            .callback(object : TextCallbackHandler() {
                override fun onSuccess(text: String?, response: Response?) {
                    if (data.cancelled) { finish(); return }
                    val body = text?.takeIf { it.isNotBlank() }
                    if (body == null) {
                        finish(ApiError(TAG, ERROR_RESPONSE_EMPTY).withResponse(response))
                        return
                    }
                    if (response?.code() == HttpURLConnection.HTTP_UNAVAILABLE) {
                        finish(ApiError(TAG, ERROR_LIBRUS_SYNERGIA_MAINTENANCE).withResponse(response).withApiResponse(body))
                        return
                    }
                    if (response?.code() in listOf(HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN)) {
                        retryOrFinish(
                            retried,
                            ApiError(TAG, ERROR_LIBRUS_SYNERGIA_ACCESS_DENIED).withResponse(response).withApiResponse(body),
                            invalidateSession = true,
                        )
                        return
                    }
                    try {
                        require(response?.code() in 200..299) { "Survey list request failed" }
                        val doc = Jsoup.parse(body)
                        val surveys = parseLibrusSurveysOrNull(data.profileId, doc)
                        if (surveys == null) {
                            val issue = classifySynergiaErrorPage(body, doc)
                            if (issue != null) {
                                val error = ApiError(TAG, issue.errorCode).withResponse(response).withApiResponse(body)
                                if (issue.retryable) {
                                    retryOrFinish(retried, error, invalidateSession = issue == SynergiaPageIssue.SESSION_EXPIRED)
                                } else {
                                    finish(error)
                                }
                                return
                            }
                            throw IllegalArgumentException("Missing survey list table")
                        }
                        if (!data.cancelled) data.db.librusSurveyDao().replace(data.profileId, surveys)
                        finish()
                    } catch (e: Exception) {
                        finish(
                            ApiError(TAG, EXCEPTION_LIBRUS_SYNERGIA_REQUEST)
                                .withResponse(response)
                                .withThrowable(e)
                                .withApiResponse(body)
                        )
                    }
                }
                override fun onFailure(response: Response?, throwable: Throwable?) {
                    if (data.cancelled) { finish(); return }
                    val code = response?.code()
                    if (code == HttpURLConnection.HTTP_UNAVAILABLE) {
                        finish(ApiError(TAG, ERROR_LIBRUS_SYNERGIA_MAINTENANCE).withResponse(response).withThrowable(throwable))
                        return
                    }
                    if (code in listOf(HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN)) {
                        retryOrFinish(
                            retried,
                            ApiError(TAG, ERROR_LIBRUS_SYNERGIA_ACCESS_DENIED).withResponse(response).withThrowable(throwable),
                            invalidateSession = true,
                        )
                        return
                    }
                    finish(ApiError(TAG, ERROR_REQUEST_FAILURE).withResponse(response).withThrowable(throwable))
                }
            }).build().enqueue()
    }

    private fun retryOrFinish(retried: Boolean, fallbackError: ApiError, invalidateSession: Boolean) {
        if (!retried) {
            refreshSynergiaSession(
                data = data,
                tag = TAG,
                onSuccess = { fetchSurveys(retried = true) },
                onFailure = { refreshError ->
                    if (data.cancelled) finish()
                    else finish(refreshError ?: fallbackError)
                },
            )
        } else {
            if (invalidateSession) data.synergiaSessionIdExpiryTime = 0L
            finish(fallbackError)
        }
    }
}

internal fun parseLibrusSurveys(profileId: Int, html: String): List<LibrusSurvey> =
    parseLibrusSurveysOrNull(profileId, Jsoup.parse(html))
        ?: throw IllegalArgumentException("Missing survey list table")

internal fun parseLibrusSurveysOrNull(profileId: Int, doc: Document): List<LibrusSurvey>? {
    val table = doc.select("table.decorated").firstOrNull {
        it.select("tr").firstOrNull()?.text()?.contains("Nazwa ankiety") == true
    }
    if (table == null) {
        val body = doc.getElementById("body")
        val isSurveyPage = body?.select("h2.inside")?.any { it.text() == "Ankiety" } == true
        val hasEmptyNotice = body?.select(".warning-box.information")?.any {
            it.select(".warning-title").text() == "Brak dostępnych ankiet." &&
                it.select(".warning-content").text() == "Nie posiadasz ankiet do wypełnienia."
        } == true
        if (isSurveyPage && hasEmptyNotice && doc.select("input[type=password]").isEmpty())
            return emptyList()
        return null
    }
    return table.select("tr").mapNotNull { row ->
        val link = row.select("a[href]").firstOrNull { it.text().contains("Wypełnij ankietę", ignoreCase = true) }
            ?: return@mapNotNull null
        val id = Regex("^/ankieta-([0-9]+)$").matchEntire(link.attr("href"))?.groupValues?.get(1)?.toLong()
        requireNotNull(id) { "Invalid survey link" }
        val cells = row.children().toList().filter { it.tagName() == "td" }
        require(cells.size >= 3) { "Invalid survey row" }
        val title = cells[0].text().trim()
        require(title.isNotBlank()) { "Missing survey title" }
        LibrusSurvey(profileId, id, title, LocalDate.parse(cells[1].text().trim()).toString())
    }.distinctBy { it.id }
}
