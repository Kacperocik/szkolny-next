/*
 * Copyright (c) Kuba Szczodrzyński 2019-12-18.
 */

package pl.szczodrzynski.edziennik.ui.event

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.MainActivity
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.api.edziennik.EdziennikTask
import pl.szczodrzynski.edziennik.data.api.events.EventGetEvent
import pl.szczodrzynski.edziennik.data.db.full.EventFull
import pl.szczodrzynski.edziennik.data.enums.NavTarget
import pl.szczodrzynski.edziennik.databinding.DialogEventDetailsBinding
import pl.szczodrzynski.edziennik.ext.*
import pl.szczodrzynski.edziennik.ui.base.dialog.SimpleDialog
import pl.szczodrzynski.edziennik.ui.notes.setupNotesButton
import pl.szczodrzynski.edziennik.ui.teachers.bindTeacher
import pl.szczodrzynski.edziennik.ui.timetable.TimetableFragment
import pl.szczodrzynski.edziennik.utils.BetterLink
import pl.szczodrzynski.edziennik.utils.models.Date
import pl.szczodrzynski.navlib.enableExpressiveImageShapeMorph
import kotlin.coroutines.CoroutineContext

class EventDetailsBinder(
    val activity: AppCompatActivity,
    val b: DialogEventDetailsBinding,
    val showNotes: Boolean = true,
    val onClose: (() -> Unit)? = null,
    val onEventUpdated: ((EventFull) -> Unit)? = null,
) : CoroutineScope {

    private val app = activity.applicationContext as App
    private val manager
        get() = app.eventManager

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    var event: EventFull? = null
        private set

    private var observer: Observer<EventFull?>? = null
    private var areActionButtonsSetup = false

    fun bind(event: EventFull) {
        this.event = event

        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this)
        }

        observer?.let { obs ->
            this.event?.let { e ->
                app.db.eventDao().getById(e.profileId, e.id).removeObserver(obs)
            }
        }

        val obs = Observer<EventFull?> { updated ->
            if (updated == null) {
                onClose?.invoke()
                return@Observer
            }
            this.event = updated
            onEventUpdated?.invoke(updated)
            update()
        }
        observer = obs
        app.db.eventDao().getById(event.profileId, event.id).observe(activity, obs)
        update()
    }

    fun unbind() {
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this)
        }
        observer?.let { obs ->
            event?.let { e ->
                app.db.eventDao().getById(e.profileId, e.id).removeObserver(obs)
            }
        }
        observer = null
        job.cancelChildren()
    }

    fun update() {
        val event = this.event ?: return
        val eventShared = event.sharedBy != null
        val eventOwn = event.sharedBy == "self"

        b.event = event
        b.eventShared = eventShared
        b.eventOwn = eventOwn

        b.topic.text = event.topicHtml
        b.body.text = event.bodyHtml

        if (!event.seen) {
            manager.markAsSeen(event)
        }

        event.filterNotes()

        val bullet = " • "
        val colorSecondary = android.R.attr.textColorSecondary.resolveAttr(activity)

        try {
            b.monthName = app.resources.getStringArray(R.array.months_day_of_array)[event.date.month - 1]
        } catch (_: Exception) {}

        manager.setLegendText(b.legend, event, showNotes)

        val eventColor = MaterialColors.harmonizeWithPrimary(b.root.context, manager.getEventColor(event))
        val bgDrawable = b.typeColor.background?.mutate()
        if (bgDrawable is GradientDrawable) {
            bgDrawable.setColor(eventColor)
        } else {
            bgDrawable?.setTintColor(eventColor)
        }
        b.typeColor.enableExpressiveImageShapeMorph(pressedCornerRadiusDp = 8f)

        val agendaSubjectImportant = event.subjectLongName != null
                && App.config[event.profileId].ui.agendaSubjectImportant

        b.name = if (agendaSubjectImportant)
            event.subjectLongName
        else
            event.typeName

        b.details = listOfNotNull(
            if (agendaSubjectImportant)
                event.typeName
            else
                event.subjectLongName,
            event.teamName?.asColoredSpannable(colorSecondary)
        ).concat(bullet)

        b.addedBy.setText(
            when (event.sharedBy) {
                null -> when {
                    event.addedManually -> R.string.event_details_added_by_self_format
                    event.teacherName == null -> R.string.event_details_added_by_unknown_format
                    else -> R.string.event_details_added_by_format
                }
                "self" -> R.string.event_details_shared_by_self_format
                else -> R.string.event_details_shared_by_format
            },
            Date.fromMillis(event.addedDate).formattedString,
            event.sharedByName ?: event.teacherName ?: ""
        )

        // MARK AS DONE
        b.checkDoneButton.isChecked = event.isDone
        b.checkDoneButton.onChange { _, isChecked ->
            if (isChecked && !event.isDone) {
                b.checkDoneButton.isChecked = false
                SimpleDialog<Unit>(activity) {
                    title(R.string.event_mark_as_done_title)
                    message(R.string.event_mark_as_done_text)
                    positive(R.string.ok) {
                        event.isDone = true
                        withContext(Dispatchers.IO) {
                            app.db.eventDao().replace(event)
                        }
                        update()
                        b.checkDoneButton.isChecked = true
                    }
                    negative(R.string.cancel)
                }.show()
            } else if (!isChecked && event.isDone) {
                event.isDone = false
                launch(Dispatchers.Default) {
                    app.db.eventDao().replace(event)
                }
                update()
            }
        }
        b.checkDoneButton.attachToastHint(R.string.hint_mark_as_done)

        // SAVE IN CALENDAR
        b.saveInCalendarButton.onClick {
            openInCalendar()
        }
        b.saveInCalendarButton.attachToastHint(R.string.hint_save_in_calendar)

        // GO TO TIMETABLE
        b.goToTimetableButton.onClick {
            onClose?.invoke()
            val dateStr = event.date.stringY_m_d

            val intent = if (activity is MainActivity && activity.navTarget == NavTarget.TIMETABLE)
                Intent(TimetableFragment.ACTION_SCROLL_TO_DATE)
            else if (activity is MainActivity)
                Intent("android.intent.action.MAIN")
            else
                Intent(activity, MainActivity::class.java)

            intent.apply {
                putExtras(
                    "fragmentId" to NavTarget.TIMETABLE,
                    "timetableDate" to dateStr,
                )
            }
            if (activity is MainActivity)
                activity.sendBroadcast(intent)
            else
                activity.startActivity(intent)
        }
        b.goToTimetableButton.attachToastHint(R.string.hint_go_to_timetable)

        // RE-DOWNLOAD
        b.downloadButton.isVisible = App.devMode
        b.downloadButton.onClick {
            EdziennikTask.eventGet(event.profileId, event).enqueue(activity)
        }
        b.downloadButton.attachToastHint(R.string.hint_download_again)

        if (!areActionButtonsSetup) {
            areActionButtonsSetup = true
            setupExpressiveRowButtonSquish(
                b.checkDoneButton,
                b.saveInCalendarButton,
                b.goToTimetableButton,
                b.downloadButton,
            )
        }

        BetterLink.attach(b.topic, onActionSelected = { onClose?.invoke() })

        b.teacherCard.bindTeacher(event.teacherId, event.teacherName) { onClose?.invoke() }

        if (!event.addedManually && (!event.isDownloaded || event.isHomework && event.homeworkBody == null)) {
            b.bodyTitle.isVisible = true
            b.bodyProgressBar.isVisible = true
            b.body.isVisible = false
            EdziennikTask.eventGet(event.profileId, event).enqueue(activity)
        } else if (event.homeworkBody.isNullOrBlank()) {
            b.bodyTitle.isVisible = false
            b.bodyProgressBar.isVisible = false
            b.body.isVisible = false
        } else {
            b.bodyTitle.isVisible = true
            b.bodyProgressBar.isVisible = false
            b.body.isVisible = true
            BetterLink.attach(b.body, onActionSelected = { onClose?.invoke() })
        }

        if (event.attachmentIds.isNullOrEmpty() || event.attachmentNames.isNullOrEmpty()) {
            b.attachmentsTitle.isVisible = false
            b.attachmentsFragment.isVisible = false
        } else {
            b.attachmentsTitle.isVisible = true
            b.attachmentsFragment.isVisible = true
            b.attachmentsFragment.init(Bundle().also {
                it.putInt("profileId", event.profileId)
                it.putLongArray("attachmentIds", event.attachmentIds!!.toLongArray())
                it.putStringArray("attachmentNames", event.attachmentNames!!.toTypedArray())
            }, owner = event)
        }

        b.notesButton.isVisible = showNotes
        b.notesButton.setupNotesButton(
            activity = activity,
            owner = event,
        )
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun onEventGetEvent(event: EventGetEvent) {
        EventBus.getDefault().removeStickyEvent(event)
        if (event.event.homeworkBody == null)
            event.event.homeworkBody = ""
        if (this.event?.id == event.event.id) {
            this.event = event.event
            onEventUpdated?.invoke(event.event)
            update()
        }
    }

    fun openInCalendar() {
        val event = this.event ?: return
        launch {
            val title = event.typeName ?: "" +
                    (if (event.typeName.isNotNullNorBlank() && event.subjectLongName.isNotNullNorBlank()) " - " else " ") +
                    (event.subjectLongName ?: "")

            val intent = Intent(Intent.ACTION_EDIT).apply {
                data = Events.CONTENT_URI
                putExtra(Events.TITLE, title)
                putExtra(Events.DESCRIPTION, event.topicHtml.toString())

                if (event.time == null) {
                    putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
                    putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.date.inMillis)
                    putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.date.inMillis)
                } else {
                    val startTime = event.date.combineWith(event.time)
                    val endTime = startTime + 45 * 60 * 1000 /* 45 min */

                    putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false)
                    putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTime)
                    putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTime)
                }
            }

            try {
                activity.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(activity, R.string.calendar_app_not_found, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
