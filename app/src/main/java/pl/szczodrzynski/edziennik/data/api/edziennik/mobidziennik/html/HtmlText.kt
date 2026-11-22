package pl.szczodrzynski.edziennik.data.api.edziennik.mobidziennik.html

import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalTime
import java.time.MonthDay

internal object HtmlText {

    data class DateParts(val monthDay: MonthDay, val year: Int?) {
        val month get() = monthDay.monthValue

        fun withFallbackYear(fallbackYear: Int): LocalDate? = try {
            LocalDate.of(year ?: fallbackYear, month, monthDay.dayOfMonth)
        } catch (_: DateTimeException) {
            null
        }
    }

    private val monthForms: Map<String, Int> = buildMap {
        val genitive = listOf(
            "stycznia", "lutego", "marca", "kwietnia", "maja", "czerwca",
            "lipca", "sierpnia", "września", "października", "listopada", "grudnia",
        )
        val nominative = listOf(
            "styczeń", "luty", "marzec", "kwiecień", "maj", "czerwiec",
            "lipiec", "sierpień", "wrzesień", "październik", "listopad", "grudzień",
        )
        val short = listOf("sty", "lut", "mar", "kwi", "maj", "cze", "lip", "sie", "wrz", "paź", "lis", "gru")
        for (i in 0 until 12) {
            put(genitive[i], i + 1)
            put(nominative[i], i + 1)
            put(short[i], i + 1)
        }
    }

    private val iso = Regex("(\\d{4})-(\\d{1,2})-(\\d{1,2})")
    private val dotted = Regex("(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})")
    private val named = Regex("(\\d{1,2})\\s+([\\p{L}]+)\\.?(?:\\s+(\\d{4}))?")
    private val timeRegex = Regex("(\\d{1,2}):(\\d{2})(?::(\\d{2}))?")

    fun monthNumber(name: String): Int? = monthForms[name.lowercase().trimEnd('.')]

    fun parseDate(text: String?): DateParts? {
        if (text.isNullOrBlank()) return null
        iso.find(text)?.let {
            return dateParts(it.groupValues[2].toInt(), it.groupValues[3].toInt(), it.groupValues[1].toInt())
        }
        dotted.find(text)?.let {
            return dateParts(it.groupValues[2].toInt(), it.groupValues[1].toInt(), it.groupValues[3].toInt())
        }
        for (m in named.findAll(text)) {
            val month = monthNumber(m.groupValues[2]) ?: continue
            val year = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()
            return dateParts(month, m.groupValues[1].toInt(), year)
        }
        return null
    }

    private fun dateParts(month: Int, day: Int, year: Int?): DateParts? = try {
        DateParts(MonthDay.of(month, day), year)
    } catch (_: DateTimeException) {
        null
    }

    fun parseTime(text: String?): LocalTime? =
        text?.let { timeRegex.find(it)?.let(::localTime) }

    fun parseTimeRange(text: String?): Pair<LocalTime, LocalTime>? {
        val matches = text?.let { timeRegex.findAll(it).take(2).toList() } ?: return null
        val start = matches.getOrNull(0)?.let(::localTime) ?: return null
        val end = matches.getOrNull(1)?.let(::localTime) ?: return null
        return start to end
    }

    private fun localTime(match: MatchResult): LocalTime? = try {
        LocalTime.of(
            match.groupValues[1].toInt(),
            match.groupValues[2].toInt(),
            match.groupValues[3].toIntOrNull() ?: 0,
        )
    } catch (_: DateTimeException) {
        null
    }
}
