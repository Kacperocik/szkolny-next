package pl.szczodrzynski.edziennik.data.api.edziennik.librus.data.synergia

import im.wangchao.mhttp.Request
import im.wangchao.mhttp.Response
import im.wangchao.mhttp.callback.TextCallbackHandler
import org.jsoup.Jsoup
import pl.szczodrzynski.edziennik.data.api.*
import pl.szczodrzynski.edziennik.data.api.edziennik.librus.DataLibrus
import pl.szczodrzynski.edziennik.data.api.edziennik.librus.ENDPOINT_LIBRUS_SYNERGIA_SURVEYS
import pl.szczodrzynski.edziennik.data.api.models.ApiError
import pl.szczodrzynski.edziennik.data.db.entity.LibrusSurvey
import pl.szczodrzynski.edziennik.data.enums.FeatureType
import pl.szczodrzynski.edziennik.ext.MINUTE
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean

class LibrusSynergiaSurveys(private val data: DataLibrus, private val onSuccess: (Int) -> Unit) {
    companion object { private const val TAG = "LibrusSynergiaSurveys" }
    private val finished = AtomicBoolean()

    private fun finish(error: ApiError? = null) {
        if (!finished.compareAndSet(false, true)) return
        error?.let { data.error(it.setCritical(false)) }
        data.setSyncNext(ENDPOINT_LIBRUS_SYNERGIA_SURVEYS, 30 * MINUTE, FeatureType.SURVEYS)
        onSuccess(ENDPOINT_LIBRUS_SYNERGIA_SURVEYS)
    }

    init {
        Request.builder().url("$LIBRUS_SYNERGIA_URL/ankiety_admin_ankiety")
            .userAgent(LIBRUS_USER_AGENT).get()
            .callback(object : TextCallbackHandler() {
                override fun onSuccess(text: String?, response: Response?) {
                    if (data.cancelled) { finish(); return }
                    try {
                        require(response?.code() in 200..299) { "Survey list request failed" }
                        val surveys = parseLibrusSurveys(data.profileId, requireNotNull(text))
                        if (!data.cancelled) data.db.librusSurveyDao().replace(data.profileId, surveys)
                        finish()
                    } catch (e: Exception) {
                        finish(ApiError(TAG, EXCEPTION_LIBRUS_SYNERGIA_REQUEST).withThrowable(e))
                    }
                }
                override fun onFailure(response: Response?, throwable: Throwable?) {
                    finish(ApiError(TAG, ERROR_REQUEST_FAILURE).withResponse(response).withThrowable(throwable))
                }
            }).build().enqueue()
    }
}

internal fun parseLibrusSurveys(profileId: Int, html: String): List<LibrusSurvey> {
    val doc = Jsoup.parse(html)
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
    }
    requireNotNull(table) { "Missing survey list table" }
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
