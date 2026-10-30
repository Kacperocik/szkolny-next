/*
 * Copyright (c) Kuba Szczodrzyński 2020-4-3.
 */

package pl.szczodrzynski.edziennik.core.manager

import android.widget.TextView
import androidx.core.view.isVisible
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import com.mikepenz.iconics.view.IconicsTextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.db.entity.StudentDuty
import pl.szczodrzynski.edziennik.data.db.full.EventFull
import pl.szczodrzynski.edziennik.ext.concat
import pl.szczodrzynski.edziennik.ext.join
import pl.szczodrzynski.edziennik.ext.resolveColor
import pl.szczodrzynski.edziennik.ext.startCoroutineTimer
import pl.szczodrzynski.edziennik.ext.toDrawable
import pl.szczodrzynski.edziennik.utils.html.BetterHtml
import kotlin.coroutines.CoroutineContext

class EventManager(val app: App) : CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Default

    fun getEventColor(typeId: Long?, fallbackColor: Int, profileId: Int = app.profile.id): Int {
        typeId?.let { id ->
            app.config[profileId].ui.agendaEventColors
                .get(id.toString())
                ?.takeIf { it.isJsonPrimitive }
                ?.asInt
        }?.let { return it }
        return fallbackColor
    }

    fun getEventColor(event: EventFull): Int =
        getEventColor(event.type, event.eventColor, event.profileId)

    fun getLessonChangesColor(profileId: Int = app.profile.id): Int =
        getSpecialColor(profileId, COLOR_KEY_LESSON_CHANGES, DEFAULT_LESSON_CHANGES_COLOR)

    fun getTeacherAbsenceColor(profileId: Int = app.profile.id): Int =
        getSpecialColor(profileId, COLOR_KEY_TEACHER_ABSENCE, DEFAULT_TEACHER_ABSENCE_COLOR)

    fun getStudentDutyColor(profileId: Int = app.profile.id): Int =
        getSpecialColor(profileId, COLOR_KEY_STUDENT_DUTY, StudentDuty.COLOR)

    companion object {
        const val COLOR_KEY_STUDENT_DUTY = "studentDuty"
        const val COLOR_KEY_LESSON_CHANGES = "lessonChanges"
        const val COLOR_KEY_TEACHER_ABSENCE = "teacherAbsence"
        val DEFAULT_LESSON_CHANGES_COLOR = 0xff78909c.toInt()
        val DEFAULT_TEACHER_ABSENCE_COLOR = 0xffff1744.toInt()

        fun getSpecialColor(profileId: Int, key: String, fallbackColor: Int): Int {
            return App.config[profileId].ui.agendaEventColors
                .get(key)
                ?.takeIf { it.isJsonPrimitive }
                ?.asInt ?: fallbackColor
        }
    }

    /*    _    _ _____    _____                 _  __ _
         | |  | |_   _|  / ____|               (_)/ _(_)
         | |  | | | |   | (___  _ __   ___  ___ _| |_ _  ___
         | |  | | | |    \___ \| '_ \ / _ \/ __| |  _| |/ __|
         | |__| |_| |_   ____) | |_) |  __/ (__| | | | | (__
          \____/|_____| |_____/| .__/ \___|\___|_|_| |_|\___|
                               | |
                               |*/
    fun markAsSeen(event: EventFull) {
        event.seen = true
        startCoroutineTimer(500L, 0L) {
            app.db.metadataDao().setSeen(event.profileId, event, true)
        }
    }

    fun setEventTopic(
        title: TextView,
        event: EventFull,
        showType: Boolean = true,
        showSubject: Boolean = false,
        showNotes: Boolean = true,
        doneIconColor: Int? = null
    ) {
        val topicSpan = event.getNoteSubstituteText(showNotes) ?: event.topicHtml
        val hasReplacingNotes = event.hasReplacingNotes()

        val typeText = if (showType) "<b>${event.typeName ?: "wydarzenie"}</b> " else null
        val subjectText = if (showSubject && event.subjectLongName != null) "<b>${event.subjectLongName}</b> " else null

        val prefixText = listOfNotNull(
            if (event.addedManually && !event.isSharedReceived) "{cmd-calendar-edit} " else null,
            if (event.isSharedReceived) "{cmd-share-variant} " else null,
            if (event.hasNotes() && hasReplacingNotes && showNotes) "{cmd-swap-horizontal} " else null,
            if (event.hasNotes() && !hasReplacingNotes && showNotes) "{cmd-playlist-edit} " else null,
            typeText,
            subjectText,
        ).joinToString("")

        val formattedPrefix = BetterHtml.fromHtml(title.context, prefixText)

        title.text = listOfNotNull(
            formattedPrefix.takeIf { it.isNotBlank() },
            topicSpan,
        ).concat(delimiter = " ")

        title.setCompoundDrawables(
            null,
            null,
            if (event.isDone)
                CommunityMaterial.Icon.cmd_check.toDrawable(
                    title.context,
                    doneIconColor ?: R.color.md_green_500.resolveColor(title.context)
                )
            else null,
            null
        )
    }

    fun setLegendText(legend: IconicsTextView, event: EventFull, showNotes: Boolean = true) {
        legend.text = listOfNotNull(
            if (event.addedManually) R.string.legend_event_added_manually else null,
            if (event.isSharedSent) R.string.legend_event_shared_sent else null,
            if (event.isSharedReceived) R.string.legend_event_shared_received else null,
            if (event.isDone) R.string.legend_event_is_done else null,
            if (showNotes) NoteManager.getLegendText(event) else null,
        ).map { legend.context.getString(it) }.join("\n")
        legend.isVisible = legend.text.isNotBlank()
    }
}
