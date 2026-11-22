package pl.szczodrzynski.edziennik.data.api.edziennik.mobidziennik

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.szczodrzynski.edziennik.data.api.edziennik.mobidziennik.html.HtmlParsers
import pl.szczodrzynski.edziennik.data.api.edziennik.mobidziennik.html.HtmlText
import pl.szczodrzynski.edziennik.data.api.edziennik.mobidziennik.login.isMobidziennikLoginPage
import java.time.LocalDate
import java.time.LocalTime

class MobidziennikHtmlParsingTest {
    @Test
    fun recognizesThePasswordFieldRegardlessOfHtmlQuotes() {
        assertTrue(isMobidziennikLoginPage("<form><input type='password' name='haslo'></form>"))
        assertFalse(isMobidziennikLoginPage("<p>Zmień hasło</p>"))
    }

    @Test
    fun acceptsPolishDatesAndRejectsInvalidCalendarValues() {
        assertEquals(LocalDate.of(2026, 11, 23), HtmlText.parseDate("pon., 23 lis.")?.withFallbackYear(2026))
        assertNull(HtmlText.parseDate("2026-02-29")?.withFallbackYear(2025))
        assertEquals(LocalTime.of(8, 55) to LocalTime.of(9, 40), HtmlText.parseTimeRange("08:55 - 09:40"))
        assertNull(HtmlText.parseTime("25:00"))
    }

    @Test
    fun readsOneStudentWithoutGuessingWhenSeveralAreAvailable() {
        val first = "<option value='17'>Uczeń 1</option>"
        val second = "<option value='23'>Uczeń 2</option>"
        assertEquals(17, HtmlParsers.attendanceStudentId("<form><input name='uczen' value='17'></form>"))
        assertNull(HtmlParsers.attendanceStudentId("<form><select name='uczen'>$first$second</select></form>"))
    }

    @Test
    fun distinguishesAnUnrecognizedGradesPageFromAnEmptyTable() {
        assertNull(HtmlParsers.parseGrades("<div id='content'>Przerwa techniczna</div>"))
        assertTrue(HtmlParsers.parseGrades("<div id='content'><table class='spis'></table></div>")!!.isEmpty())
    }
}
