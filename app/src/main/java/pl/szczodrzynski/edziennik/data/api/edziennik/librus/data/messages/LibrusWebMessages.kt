package pl.szczodrzynski.edziennik.data.api.edziennik.librus.data.messages

import androidx.core.util.set
import androidx.room.OnConflictStrategy
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import im.wangchao.mhttp.Request
import im.wangchao.mhttp.Response
import im.wangchao.mhttp.callback.TextCallbackHandler
import org.greenrobot.eventbus.EventBus
import org.jsoup.Jsoup
import pl.szczodrzynski.edziennik.data.api.edziennik.librus.login.LibrusLoginSynergia
import pl.szczodrzynski.edziennik.data.enums.LoginMethod
import pl.szczodrzynski.edziennik.data.api.*
import pl.szczodrzynski.edziennik.data.api.edziennik.librus.DataLibrus
import pl.szczodrzynski.edziennik.data.api.events.MessageSentEvent
import pl.szczodrzynski.edziennik.data.api.events.RecipientListGetEvent
import pl.szczodrzynski.edziennik.data.api.models.ApiError
import pl.szczodrzynski.edziennik.data.db.entity.Message
import pl.szczodrzynski.edziennik.data.db.entity.MessageRecipient
import pl.szczodrzynski.edziennik.data.db.entity.Teacher
import pl.szczodrzynski.edziennik.ext.*
import pl.szczodrzynski.edziennik.utils.models.Date
import java.net.URI

/** The browser message API uses account IDs, not Synergia teacher IDs. */
class LibrusWebMessages(private val data: DataLibrus) {
    private val tag = "LibrusWebMessages"
    private val host = "https://wiadomosci.librus.pl"
    private var terminal = false
    private var posted = false
    private var sessionRenewed = false
    private var stage = "Preparing web messages"

    private fun fail(response: Response? = null, throwable: Throwable? = null) {
        if (terminal || data.cancelled) return
        terminal = true
        val diagnostic = webMessageDiagnostic(stage, response?.code(),
            response?.headers()?.get("Content-Type"), throwable?.let { error ->
                val origin = error.stackTrace.firstOrNull { it.fileName == "LibrusWebMessages.kt" }
                error.javaClass.simpleName + (origin?.let { " at ${it.fileName}:${it.lineNumber}" } ?: "")
            })
        // Login URLs and response headers can contain session credentials. Report only safe metadata.
        data.error(ApiError(tag, if (posted) ERROR_MESSAGE_SEND_UNCONFIRMED else ERROR_LIBRUS_MESSAGES_ERROR)
            .withThrowable(IllegalStateException(diagnostic)))
    }

    private fun finish(action: () -> Unit) {
        if (terminal || data.cancelled) return
        terminal = true
        action()
    }

    private fun session(ready: () -> Unit) {
        // Start with the selected profile's Synergia session; never reuse another profile's web login.
        data.app.cookieJar.clear("wiadomosci.librus.pl")
        data.app.cookieJar.set("synergia.librus.pl", "DZIENNIKSID", data.synergiaSessionId)
        bootstrap("https://synergia.librus.pl/wiadomosci3", 0, {
            renewSession(ready)
        }) {
            json("/me") { ready() }
        }
    }

    private fun renewSession(ready: () -> Unit) {
        if (terminal || data.cancelled) return
        if (sessionRenewed || posted) { fail(); return }
        sessionRenewed = true
        data.synergiaSessionIdExpiryTime = 0
        data.loginMethods.remove(LoginMethod.LIBRUS_SYNERGIA)
        LibrusLoginSynergia(data) {
            if (!data.cancelled) {
                data.loginMethods.add(LoginMethod.LIBRUS_SYNERGIA)
                session(ready)
            }
        }
    }

    private fun bootstrap(url: String, redirects: Int, expired: () -> Unit, ready: () -> Unit) {
        if (terminal || data.cancelled) return
        stage = "Web login: redirect $redirects"
        val secureUrl = secureWebMessageLoginUrl(url)
        if (redirects > 8 || secureUrl == null) {
            fail(); return
        }
        val uri = URI(secureUrl)
        Request.builder().url(secureUrl)
            .userAgent(if (uri.host == "synergia.librus.pl") LIBRUS_USER_AGENT else SYNERGIA_USER_AGENT)
            .get()
            .withClient(data.app.httpLazy)
            .callback(object : TextCallbackHandler() {
                override fun onSuccess(text: String?, response: Response?) {
                    if (terminal || data.cancelled) return
                    val location = response?.headers()?.get("Location")
                    if (response?.code() in 300..399 && location != null) {
                        bootstrap(uri.resolve(location).toString(), redirects + 1, expired, ready)
                    } else if (response?.code() in 200..299 &&
                        response?.request()?.url()?.host == "wiadomosci.librus.pl") ready()
                    else {
                        val flags = webMessageLoginPageFlags(text.orEmpty())
                        stage += "; Synergia HTML: $flags"
                        if (!sessionRenewed && !posted && uri.host == "synergia.librus.pl" &&
                            response?.code() in 200..299 && flags != "maintenance") expired()
                        else fail(response)
                    }
                }
                override fun onFailure(response: Response?, throwable: Throwable?) = fail(response, throwable)
            }).build().enqueue()
    }

    private fun json(path: String, payload: JsonObject? = null, next: (JsonObject) -> Unit) {
        if (terminal || data.cancelled) return
        stage = "${if (payload == null) "GET" else "POST"} /api$path"
        Request.builder().url("$host/api$path").userAgent(SYNERGIA_USER_AGENT)
            .addHeader("Accept", "application/json")
            .addHeader("Referer", "$host/nowy/")
            .apply {
                if (payload == null) get() else {
                    post()
                    addHeader("Origin", host)
                    data.app.cookieJar.get("wiadomosci.librus.pl", "XSRF-TOKEN")?.let {
                        addHeader("X-XSRF-TOKEN", java.net.URLDecoder.decode(it, "UTF-8"))
                    }
                    setJsonBody(payload)
                }
            }
            // A lost POST response must not cause an automatic duplicate message.
            .withClient(data.app.http.newBuilder().retryOnConnectionFailure(false)
                .followRedirects(false).build())
            .callback(object : TextCallbackHandler() {
                override fun onSuccess(text: String?, response: Response?) {
                    if (terminal || data.cancelled) return
                    if (response?.code() !in 200..299) { fail(response); return }
                    try {
                        val result = if (text.isNullOrBlank() && payload != null) JsonObject()
                            else JsonParser.parseString(text).asJsonObject
                        require(result.get("errors")?.let { it.isJsonArray && it.asJsonArray.size() > 0 } != true)
                        require(result.getString("status") != "error")
                        next(result)
                    } catch (e: Exception) { fail(response, e) }
                }
                override fun onFailure(response: Response?, throwable: Throwable?) = fail(response, throwable)
            }).build().enqueue()
    }

    fun recipients(done: () -> Unit) = session {
        json("/receivers/types?includeClass=true") { types ->
            val entries = types.getAsJsonObject("data").getAsJsonArray("list")
            val queue = entries.map { it.asJsonObject.get("id").asString }.toMutableList()
            val fetched = mutableListOf<WebRecipient>()
            fun fetchNext() {
                if (queue.isEmpty()) {
                    stage = "Saving web recipients"
                    val teachers = mergeWebRecipients(data, fetched)
                    data.teacherOnConflictStrategy = OnConflictStrategy.REPLACE
                    data.profile?.lastReceiversSync = System.currentTimeMillis()
                    finish {
                        EventBus.getDefault().postSticky(RecipientListGetEvent(data.profileId, teachers))
                        done()
                    }
                    return
                }
                val type = queue.removeAt(0)
                if (type == "customGroups") { fetchNext(); return }
                require(type.matches(Regex("[a-zA-Z]+")))
                val group = if (type in listOf("parentsCouncil", "students", "classParents", "guardians"))
                    "students-and-attendants" else "school-employees"
                json("/receivers/groups/$group?receiverType=$type") { response ->
                    fetched += parseWebRecipients(response, type)
                    fetchNext()
                }
            }
            fetchNext()
        }
    }

    fun send(recipients: Set<Teacher>, subject: String, text: String, done: () -> Unit) {
        if (recipients.isEmpty() || recipients.any { it.profileId != data.profileId ||
                it.loginId?.matches(Regex("web:[0-9]+")) != true }) {
            fail(); return
        }
        session {
            // Take a snapshot before sending so an older identical message cannot be mistaken for this one.
            json("/outbox/messages?page=0&limit=100") { before ->
                val previous = before.getAsJsonArray("data").map { it.asJsonObject.getString("messageId") }.toSet()
                val payload = webMessagePayload(recipients.map { it.loginId!!.removePrefix("web:") }, subject, text)
                posted = true
                json("/messages", payload) {
                    json("/outbox/messages?page=0&limit=100") { after ->
                        val sent = after.getAsJsonArray("data").map { it.asJsonObject }.filter {
                            it.getString("messageId") !in previous &&
                                it.getString("topic") == subject
                        }
                        val row = sent.firstOrNull()
                        val id = row?.getString("messageId")?.toLongOrNull()
                        val message = if (id != null) Message(data.profileId, id, Message.TYPE_SENT,
                            subject, text, null, Date.fromIso(row.getString("sendDate")!!)) else null
                        if (message != null) {
                            data.messageList.add(message)
                            recipients.forEach { teacher ->
                                data.messageRecipientList.add(MessageRecipient(data.profileId, teacher.id,
                                    -1, 0, message.id))
                            }
                        }
                        finish {
                            EventBus.getDefault().postSticky(MessageSentEvent(data.profileId, message, message?.addedDate))
                            done()
                        }
                    }
                }
            }
        }
    }
}

internal data class WebRecipient(val accountId: String, val name: String, val type: String, val group: String?)

internal fun parseWebRecipients(json: JsonObject, type: String): List<WebRecipient> {
    require(json.has("receivers") || json.has("classes")) { "Missing recipient collection" }
    val result = mutableListOf<WebRecipient>()
    fun walk(value: com.google.gson.JsonElement, group: String? = null) {
        if (value.isJsonArray) value.asJsonArray.forEach { walk(it, group) }
        else if (value.isJsonObject) {
            val obj = value.asJsonObject
            if (obj.has("accountId")) {
                val id = obj.getString("accountId")!!
                require(id.matches(Regex("[0-9]+")))
                val name = obj.getString("label") ?: obj.getString("name")
                require(!name.isNullOrBlank())
                result += WebRecipient(id, name, type, group)
            } else if (obj.has("receivers")) walk(obj.get("receivers"), obj.getString("label") ?: group)
            else if (obj.has("classes")) walk(obj.get("classes"), group)
            else error("Unknown recipient group")
        } else error("Invalid recipient")
    }
    walk(json)
    return result
}

internal fun mergeWebRecipients(data: DataLibrus, rows: List<WebRecipient>): List<Teacher> =
    mergeWebRecipients(data.profileId, data.teacherList, rows)

internal fun mergeWebRecipients(profileId: Int, teachers: android.util.LongSparseArray<Teacher>,
    rows: List<WebRecipient>): List<Teacher> {
    val result = linkedMapOf<String, Teacher>()
    val resetAccounts = mutableSetOf<String>()
    rows.forEach { row ->
        val key = "web:${row.accountId}"
        val teacher = result.getOrPut(key) {
            val name = row.name.fixName()
            teachers.filter { it.loginId == key }.singleOrNull()
                ?: teachers.filter { it.fullNameLastFirst == name && it.loginId?.startsWith("web:") != true }.singleOrNull()
                ?: Teacher(profileId, freeWebRecipientId(row.accountId, teachers)).apply {
                    val parts = name.splitName()
                    this.name = parts?.second ?: name
                    surname = parts?.first ?: ""
                    teachers[id] = this
                }
        }.also {
            if (resetAccounts.add(key)) it.type = 0
        }
        teacher.loginId = key
        teacher.setTeacherType(webRecipientType(row.type))
        if (row.group != null) teacher.typeDescription = row.group
    }
    teachers.filter { it.loginId?.startsWith("web:") == true && it.loginId !in result }.forEach {
        it.loginId = null
    }
    return result.values.toList()
}

internal fun webRecipientType(type: String): Int = when (type) {
    "teachers" -> Teacher.TYPE_TEACHER
    "tutors" -> Teacher.TYPE_EDUCATOR
    "headteachers" -> Teacher.TYPE_PRINCIPAL
    "pedagogue" -> Teacher.TYPE_PEDAGOGUE
    "counsellor" -> Teacher.TYPE_COUNSELLOR
    "specialCounsellor" -> Teacher.TYPE_SPECIAL_COUNSELLOR
    "psychologist" -> Teacher.TYPE_PSYCHOLOGIST
    "careerAdvisor" -> Teacher.TYPE_CAREER_ADVISOR
    "librarian" -> Teacher.TYPE_LIBRARIAN
    "secretary" -> Teacher.TYPE_SECRETARIAT
    "admin" -> Teacher.TYPE_SCHOOL_ADMIN
    "sadmin" -> Teacher.TYPE_SUPER_ADMIN
    "otherSpecialist" -> Teacher.TYPE_SPECIALIST
    "parentsCouncil" -> Teacher.TYPE_PARENTS_COUNCIL
    "schoolParentsCouncil" -> Teacher.TYPE_SCHOOL_PARENTS_COUNCIL
    "students" -> Teacher.TYPE_STUDENT
    "classParents", "guardians" -> Teacher.TYPE_PARENT
    else -> Teacher.TYPE_OTHER
}

internal fun webMessagePayload(ids: List<String>, subject: String, text: String) = JsonObject().apply {
    addProperty("topic", subject.base64Encode())
    addProperty("content", text.base64Encode())
    add("receivers", JsonObject().apply {
        add("schoolReceivers", JsonArray().apply {
            ids.distinct().forEach { id -> add(JsonObject().apply { addProperty("accountId", id) }) }
        })
    })
    add("storageId", com.google.gson.JsonNull.INSTANCE)
    addProperty("category", "normal")
}


internal fun webMessageDiagnostic(stage: String, status: Int?, contentType: String?, failure: String?): String =
    listOfNotNull(stage, status?.let { "HTTP $it" },
        contentType?.substringBefore(';')?.let { "Content-Type: $it" }, failure).joinToString("; ")


/** Mirror the browser's HSTS upgrade for Librus's legacy /nowy redirect without sending HTTP. */
internal fun secureWebMessageLoginUrl(url: String): String? = try {
    val uri = URI(url)
    when {
        uri.userInfo != null || uri.host !in listOf("synergia.librus.pl", "wiadomosci.librus.pl") -> null
        uri.scheme == "https" && uri.port in listOf(-1, 443) -> url
        uri.scheme == "http" && uri.host == "wiadomosci.librus.pl" &&
            uri.port in listOf(-1, 80) && uri.path in listOf("/nowy", "/nowy/") ->
            "https://wiadomosci.librus.pl" + uri.rawPath +
                (uri.rawQuery?.let { "?$it" } ?: "")
        else -> null
    }
} catch (_: java.net.URISyntaxException) {
    null
}


internal fun freeWebRecipientId(accountId: String, teachers: android.util.LongSparseArray<Teacher>): Long {
    val remoteId = accountId.toLong()
    if (remoteId > 0 && teachers[remoteId] == null) return remoteId
    var localId = -maxOf(25L, remoteId)
    while (teachers[localId] != null) {
        check(localId > Long.MIN_VALUE) { "No free local recipient ID" }
        localId--
    }
    return localId
}


/** Classify unexpected HTML without including account names, forms or credentials in error reports. */
internal fun webMessageLoginPageFlags(html: String): String {
    val doc = Jsoup.parse(html)
    return when {
        html.contains("Przerwa techniczna", ignoreCase = true) -> "maintenance"
        doc.select("input[type=password]").isNotEmpty() -> "login form"
        html.contains("stop.png") -> "access denied"
        html.contains("jesteś zalogowany", ignoreCase = true) -> "logged-in page without redirect"
        else -> "unrecognized page"
    }
}
