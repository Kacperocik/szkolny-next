package pl.szczodrzynski.edziennik.data.api.edziennik.mobidziennik.html

// Based on the MobiDziennik HTML parsers contributed by itsmgxb24 in szkolny-android PR #229.
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import java.io.StringReader

internal object HtmlParsers {

    private val whitespace = Regex("\\s+")
    private val timetableLeft = Regex("left:\\s*([\\d.]+)%")
    private val hoursRegex = Regex("^\\d\\d:\\d\\d\\s*-\\s*\\d\\d:\\d\\d")
    private val roomSuffix = Regex("\\(\\w+\\)$")
    private val brRegex = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
    private val eventsKey = Regex("\\bevents\\s*:\\s*")

    fun clean(s: String?): String =
        (s ?: "").replace('\u00a0', ' ').replace(whitespace, " ").trim()

    private fun soup(text: String): Document = Jsoup.parse(text)

    private fun content(doc: Document): Element? = doc.selectFirst("#content") ?: doc.body()

    fun attendanceStudentId(text: String): Int? =
        soup(text).select("form input[name=uczen][value], form select[name=uczen] option[value]")
            .mapNotNull { it.attr("value").toIntOrNull()?.takeIf { id -> id > 0 } }
            .distinct()
            .singleOrNull()

    private fun Element.childElements(vararg tags: String): List<Element> =
        children().asSequence().filter { it.tagName() in tags }.toList()

    private fun Element.childElement(tag: String): Element? = children().firstOrNull { it.tagName() == tag }

    private fun collectTexts(node: Node, out: MutableList<String>) {
        if (node is TextNode) out += node.text()
        else node.childNodes().forEach { collectTexts(it, out) }
    }

    private fun getText(el: Element?, sep: String = ""): String {
        if (el == null) return ""
        val out = mutableListOf<String>()
        collectTexts(el, out)
        return out.joinToString(sep)
    }

    private fun infoFields(span: Element?): Map<String, String> {
        val out = linkedMapOf<String, String>()
        if (span == null) return out
        for (strong in span.select("strong")) {
            val labelNode = strong.previousSibling()
            val label = clean((labelNode as? TextNode)?.text() ?: "").trimEnd(':').trim()
            if (label.isNotEmpty())
                out[label] = clean(getText(strong, " "))
        }
        return out
    }

    private fun tableRows(table: Element): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        for (tr in table.select("tr")) {
            val cells = tr.childElements("td", "th").map { clean(getText(it, " ")) }
            if (cells.any { it.isNotEmpty() })
                rows += cells
        }
        return rows
    }

    private fun parseFloat(s: String?): Double? = s?.trim()?.replace(",", ".")?.toDoubleOrNull()

    fun parseGrades(text: String): List<HtmlSubjectGrades>? {
        val doc = content(soup(text)) ?: return null
        val table = doc.selectFirst("table.spis") ?: return null
        val subjects = mutableListOf<HtmlSubjectGrades>()
        for (tr in table.select("tr.naglowek3")) {
            val tds = tr.childElements("td")
            if (tds.size < 2) continue
            val nameDiv = tds[0].selectFirst("div") ?: continue
            nameDiv.select("small").remove()
            val subject = clean(getText(nameDiv))
            val grades = mutableListOf<HtmlGrade>()
            var category: String? = null
            for (el in tds[1].childNodes()) {
                if (el !is Element) continue
                if (el.tagName() == "span") {
                    category = clean(getText(el)).trimEnd(':').trim()
                } else if (el.tagName() == "a" && el.hasClass("info")) {
                    val strong = el.childElement("strong")
                    val sup = el.childElement("sup")
                    val small = el.childElement("small")
                    val f = infoFields(el.childElement("span"))
                    val value = parseFloat(f["Wartość oceny"])
                    val weight = parseFloat(clean(getText(sup)))
                    grades += HtmlGrade(
                        id = el.attr("rel").split(" ").filter { it.isNotEmpty() }.joinToString(" "),
                        grade = strong?.let { clean(getText(it)) },
                        value = value,
                        weight = weight,
                        category = category,
                        description = clean(getText(small)).trim { it == '(' || it == ')' || it == ' ' },
                        countsTowardAverage = (f["Liczona do średniej"] ?: "").lowercase().startsWith("tak"),
                        addedAt = f["Wpisano"],
                        teacher = f["Wpisał(a)"],
                    )
                }
            }
            subjects += HtmlSubjectGrades(subject, grades)
        }
        return subjects
    }

    fun parseExams(text: String): List<HtmlExam> {
        val doc = content(soup(text)) ?: return emptyList()
        val out = mutableListOf<HtmlExam>()
        for (tr in doc.select("table.spis tr")) {
            val tds = tr.childElements("td")
            if (tds.size != 6) continue
            val whenEl = tds[0].selectFirst("small")
            val span = tds[1].selectFirst("span")
            val subjectCell = tds[2].selectFirst("span")
            val kindCell = tds[3].selectFirst("span")
            val f = infoFields(tds[5].selectFirst("span"))
            var dateTxt = clean(getText(tds[0], " "))
            if (whenEl != null)
                dateTxt = clean(dateTxt.replace(clean(getText(whenEl)), ""))
            out += HtmlExam(
                date = dateTxt,
                dayAndTime = if (whenEl != null) clean(getText(whenEl)) else "",
                className = span?.attr("title") ?: clean(getText(tds[1])),
                subject = subjectCell?.attr("title") ?: clean(getText(tds[2])),
                kind = kindCell?.attr("title") ?: clean(getText(tds[3])),
                topic = clean(getText(tds[4], " ")),
                teacher = f["Wpisał(a)"],
                addedAt = f["Czas wpisania"],
            )
        }
        return out
    }

    fun parseHomework(text: String): List<HtmlHomework> {
        val doc = content(soup(text)) ?: return emptyList()
        val out = mutableListOf<HtmlHomework>()
        for (tr in doc.select("tr[id_zadania]")) {
            val tds = tr.childElements("td")
            if (tds.size < 4) continue
            val whenEl = tds[0].selectFirst("small")
            var dateTxt = clean(getText(tds[0], " "))
            if (whenEl != null)
                dateTxt = clean(dateTxt.replace(clean(getText(whenEl)), ""))
            val f = infoFields(tds[2].selectFirst("span"))
            val bodyCell = tds[3]
            val body = bodyCell.attr("title").ifEmpty { clean(getText(bodyCell, " ")) }
            out += HtmlHomework(
                id = tr.attr("id_zadania"),
                studentId = tr.attr("uczen").ifEmpty { null },
                dueDate = dateTxt,
                hours = if (whenEl != null) clean(getText(whenEl)) else "",
                subject = clean(getText(tds[1])),
                teacher = f["Wpisał(a)"],
                addedAt = f["Czas wpisania"],
                body = clean(body),
                truncated = clean(body).endsWith("..."),
            )
        }
        return out
    }

    fun parseHomeworkDetails(text: String, zid: String): Map<String, String> {
        val doc = content(soup(text)) ?: return mapOf("id" to zid)
        doc.select("form, script, style, input, textarea, select, button, a.button, a.button_right").remove()
        doc.select("a.info > span, th span").remove()
        val fields = linkedMapOf<String, String>()
        for (t in doc.select("table")) {
            for (r in tableRows(t)) {
                if (r.size == 2)
                    fields[r[0].split(":")[0].trim()] = r[1]
            }
        }
        if (fields.isNotEmpty())
            return linkedMapOf("id" to zid) + fields
        val plain = getText(doc, "\n").split("\n").map { clean(it) }.filter { it.isNotEmpty() }
        return mapOf("id" to zid, "tekst" to plain.joinToString("\n"))
    }

    fun parseMessageList(text: String): List<HtmlMessageRow> {
        val doc = content(soup(text)) ?: return emptyList()
        val out = mutableListOf<HtmlMessageRow>()
        for (tr in doc.select("table.spis tr[rel]")) {
            val tds = tr.childElements("td")
            if (tds.size < 6) continue
            out += HtmlMessageRow(
                id = tr.attr("rel"),
                subject = clean(getText(tds[0], " ")),
                attachments = clean(getText(tds[1])),
                sender = clean(getText(tds[3], " ")),
                receivedAt = clean(getText(tds[4], " ")),
                isRead = clean(getText(tds[5])).lowercase().startsWith("tak"),
            )
        }
        return out
    }

    fun parseMessage(text: String): HtmlMessage? {
        val body = soup(text).selectFirst("div.wiadomosc_tresc") ?: return null
        val metadata = body.childElements("div").lastOrNull()
        val readAt = metadata?.let { element ->
            getText(element, "\n").lineSequence()
                .firstOrNull { clean(it.substringBefore(':')) == "czas przeczytania" }
                ?.substringAfter(':')?.let(::clean)
        }
        metadata?.remove()
        return HtmlMessage(
            readAt = readAt,
            body = getText(body, "\n").lineSequence().map(::clean).filter { it.isNotEmpty() }.joinToString("\n"),
        )
    }

    private fun JsonObject.str(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString

    fun parseClassCalendar(text: String): List<HtmlCalendarEvent> {
        val m = eventsKey.find(text) ?: return emptyList()
        val reader = JsonReader(StringReader(text.substring(m.range.last + 1))).apply { isLenient = true }
        val events = JsonParser.parseReader(reader)
        if (!events.isJsonArray) return emptyList()
        val out = events.asJsonArray.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject }.map { e ->
            val idRaw = e.str("id") ?: ""
            HtmlCalendarEvent(
                date = e.str("start"),
                type = idRaw.split(";")[0],
                idRaw = idRaw,
                color = e.str("color"),
                title = clean(Parser.unescapeEntities(getText(soup(e.str("title") ?: "").body()), false)),
                description = clean(getText(soup(Parser.unescapeEntities(e.str("comment") ?: "", false)).body(), " ")),
            )
        }
        return out.sortedBy { it.date ?: "" }
    }

    fun parseSchoolCalendar(text: String): List<HtmlSchoolCalendarRow> {
        val doc = content(soup(text)) ?: return emptyList()
        val out = mutableListOf<HtmlSchoolCalendarRow>()
        for (tr in doc.select("table.spis tr.podswietl")) {
            val tds = tr.childElements("td")
            if (tds.size < 3) continue
            val nxt = tr.nextElementSibling()
            val description = if (nxt != null && !nxt.hasClass("podswietl")) clean(getText(nxt, " ")) else ""
            out += HtmlSchoolCalendarRow(
                dueDate = clean(getText(tds[0], " ")),
                name = clean(getText(tds[1], " ")),
                category = clean(getText(tds[2], " ")),
                description = description,
            )
        }
        return out
    }

    fun parseTimetable(text: String): HtmlTimetable {
        val doc = content(soup(text)) ?: return HtmlTimetable(emptyList(), emptyList())
        val days = doc.select(".plansc_top > div").map {
            HtmlTimetableDay(clean(getText(it)), it.attr("title").ifEmpty { null })
        }
        val lessons = mutableListOf<HtmlTimetableLesson>()
        for (bx in doc.select("div.bx")) {
            val style = bx.attr("style")
            val idx = timetableLeft.find(style)?.groupValues?.get(1)?.toDoubleOrNull()?.let { (it / 20).toInt() }
            val raw = Parser.unescapeEntities(bx.attr("title"), false)
            var lines = raw.split(brRegex).map { clean(getText(soup(it).body())) }
            lines = lines.filter { it.isNotEmpty() }
            val hours = lines.firstOrNull { hoursRegex.containsMatchIn(it) } ?: ""
            var rest = lines.filter { it != hours }
            var note = rest.filter {
                ":" in it && !roomSuffix.containsMatchIn(it) && rest.indexOf(it) == 0 &&
                    it.lowercase().let { l ->
                        l.startsWith("lekcja") || l.startsWith("zastępstwo") || l.startsWith("zmiana")
                    }
            }
            val kind = bx.classNames().firstOrNull { it == "pz" || it == "lz" || it == "lo" }
            if (note.isEmpty() && (kind == "lz" || kind == "lo") && rest.size >= 3)
                note = listOf(rest[0])
            rest = rest.filter { it !in note }
            val day = idx?.let { days.getOrNull(it) }
            lessons += HtmlTimetableLesson(
                day = day?.day,
                date = day?.date,
                hours = hours,
                subject = rest.firstOrNull() ?: "",
                details = rest.drop(1).joinToString(" | "),
                status = bx.attr("alt").ifEmpty { null },
                note = note.joinToString(" "),
                kind = kind,
            )
        }
        val order = days.withIndex().associate { it.value.day to it.index }
        return HtmlTimetable(days, lessons.sortedWith(compareBy({ order[it.day] ?: 99 }, { it.hours })))
    }

    fun parseGenericTables(text: String): List<List<List<String>>> {
        val doc = content(soup(text)) ?: return emptyList()
        doc.select("th a.info > span").remove()
        val result = mutableListOf<List<List<String>>>()
        for (t in doc.select("table.spis")) {
            if (t.parents().any { it.tagName() == "table" }) continue
            result += listOf(tableRows(t))
        }
        return result
    }

    fun parseFinalGrades(text: String): List<HtmlFinalGrade> {
        val out = mutableListOf<HtmlFinalGrade>()
        for (table in parseGenericTables(text)) {
            val header = table.firstOrNull() ?: continue
            val h = header.map { it.lowercase() }
            if (h.none { "roczn" in it || "śródrocz" in it || "srodrocz" in it })
                continue
            for (row in table.drop(1)) {
                val subject = row.firstOrNull()?.takeIf { it.isNotEmpty() } ?: continue
                for (j in 1 until minOf(header.size, row.size)) {
                    val cell = row[j].trim()
                    if (cell.isEmpty() || header[j].isEmpty()) continue
                    out += HtmlFinalGrade(subject, header[j], cell)
                }
            }
        }
        return out
    }

    fun parseAnnouncements(text: String): String {
        val doc = content(soup(text)) ?: return ""
        doc.select("h1").remove()
        return clean(getText(doc, " "))
    }
}
