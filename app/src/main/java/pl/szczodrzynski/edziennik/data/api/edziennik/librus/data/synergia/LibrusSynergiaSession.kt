package pl.szczodrzynski.edziennik.data.api.edziennik.librus.data.synergia

import com.google.gson.JsonObject
import im.wangchao.mhttp.Request
import im.wangchao.mhttp.Response
import im.wangchao.mhttp.callback.JsonCallbackHandler
import im.wangchao.mhttp.callback.TextCallbackHandler
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import pl.szczodrzynski.edziennik.data.api.*
import pl.szczodrzynski.edziennik.data.api.edziennik.librus.DataLibrus
import pl.szczodrzynski.edziennik.data.api.models.ApiError
import pl.szczodrzynski.edziennik.ext.getString
import pl.szczodrzynski.edziennik.ext.getUnixDate
import java.net.HttpURLConnection

internal enum class SynergiaPageIssue(val errorCode: Int, val retryable: Boolean) {
    MAINTENANCE(ERROR_LIBRUS_SYNERGIA_MAINTENANCE, retryable = false),
    SESSION_EXPIRED(ERROR_LIBRUS_SYNERGIA_ACCESS_DENIED, retryable = true),
    ACCESS_DENIED(ERROR_LIBRUS_SYNERGIA_ACCESS_DENIED, retryable = true),
    NOT_LOGGED_IN(ERROR_LIBRUS_SYNERGIA_OTHER, retryable = true),
}

internal fun classifySynergiaErrorPage(html: String, doc: Document? = null): SynergiaPageIssue? {
    if (html.contains("Przerwa techniczna", ignoreCase = true))
        return SynergiaPageIssue.MAINTENANCE
    val parsed = doc ?: Jsoup.parse(html)
    if (parsed.select("input[type=password]").isNotEmpty())
        return SynergiaPageIssue.SESSION_EXPIRED
    if (html.contains("stop.png", ignoreCase = true))
        return SynergiaPageIssue.ACCESS_DENIED
    if (!html.contains("jesteś zalogowany", ignoreCase = true))
        return SynergiaPageIssue.NOT_LOGGED_IN
    return null
}

internal fun refreshSynergiaSession(
    data: DataLibrus,
    tag: String,
    onSuccess: () -> Unit,
    onFailure: (ApiError?) -> Unit,
) {
    if (data.cancelled) {
        onFailure(null)
        return
    }
    data.synergiaSessionIdExpiryTime = 0L
    data.app.cookieJar.clear("synergia.librus.pl")

    val accessToken = data.apiAccessToken
    if (accessToken.isNullOrBlank()) {
        onFailure(null)
        return
    }

    Request.builder()
        .url("${if (data.fakeLogin) FAKE_LIBRUS_API else LIBRUS_API_URL}/AutoLoginToken")
        .userAgent(LIBRUS_USER_AGENT)
        .addHeader("Authorization", "Bearer $accessToken")
        .post()
        .allowErrorCode(HttpURLConnection.HTTP_BAD_REQUEST)
        .allowErrorCode(HttpURLConnection.HTTP_UNAUTHORIZED)
        .allowErrorCode(HttpURLConnection.HTTP_FORBIDDEN)
        .allowErrorCode(HttpURLConnection.HTTP_UNAVAILABLE)
        .callback(object : JsonCallbackHandler() {
            override fun onSuccess(json: JsonObject?, response: Response?) {
                if (data.cancelled) {
                    onFailure(null)
                    return
                }
                if (response?.code() == HttpURLConnection.HTTP_UNAVAILABLE) {
                    onFailure(
                        ApiError(tag, ERROR_LIBRUS_API_MAINTENANCE)
                            .withResponse(response)
                            .withApiResponse(json)
                    )
                    return
                }
                val token = json?.getString("Token")
                if (response?.code() !in 200..299 || token.isNullOrBlank()) {
                    onFailure(null)
                    return
                }
                exchangeSynergiaToken(data, tag, token, onSuccess, onFailure)
            }

            override fun onFailure(response: Response?, throwable: Throwable?) {
                if (data.cancelled) {
                    onFailure(null)
                    return
                }
                onFailure(
                    ApiError(tag, ERROR_REQUEST_FAILURE)
                        .withResponse(response)
                        .withThrowable(throwable)
                )
            }
        })
        .build()
        .enqueue()
}

private fun exchangeSynergiaToken(
    data: DataLibrus,
    tag: String,
    token: String,
    onSuccess: () -> Unit,
    onFailure: (ApiError?) -> Unit,
) {
    data.app.cookieJar.clear("synergia.librus.pl")
    Request.builder()
        .url(LIBRUS_SYNERGIA_TOKEN_LOGIN_URL.replace("TOKEN", token) + "/uczen/widok/centrum_powiadomien")
        .userAgent(LIBRUS_USER_AGENT)
        .get()
        .allowErrorCode(HttpURLConnection.HTTP_BAD_REQUEST)
        .allowErrorCode(HttpURLConnection.HTTP_FORBIDDEN)
        .allowErrorCode(HttpURLConnection.HTTP_UNAUTHORIZED)
        .withClient(data.app.httpLazy)
        .callback(object : TextCallbackHandler() {
            override fun onSuccess(text: String?, response: Response?) {
                if (data.cancelled) {
                    onFailure(null)
                    return
                }
                val location = response?.headers()?.get("Location")
                if (location?.endsWith("przerwa_techniczna") == true) {
                    onFailure(
                        ApiError(tag, ERROR_LIBRUS_SYNERGIA_MAINTENANCE)
                            .withResponse(response)
                            .withApiResponse(text)
                    )
                    return
                }
                if (location?.endsWith("centrum_powiadomien") == true) {
                    val sessionId = data.app.cookieJar.get("synergia.librus.pl", "DZIENNIKSID")
                    if (!sessionId.isNullOrBlank()) {
                        data.synergiaSessionId = sessionId
                        data.synergiaSessionIdExpiryTime = response.getUnixDate() + 45 * 60
                        onSuccess()
                        return
                    }
                }
                onFailure(null)
            }

            override fun onFailure(response: Response?, throwable: Throwable?) {
                if (data.cancelled) {
                    onFailure(null)
                    return
                }
                onFailure(
                    ApiError(tag, ERROR_REQUEST_FAILURE)
                        .withResponse(response)
                        .withThrowable(throwable)
                )
            }
        })
        .build()
        .enqueue()
}
