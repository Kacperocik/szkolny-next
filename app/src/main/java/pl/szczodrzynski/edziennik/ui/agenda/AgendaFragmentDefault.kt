/*
 * Copyright (c) Kuba Szczodrzyński 2021-4-8.
 */

package pl.szczodrzynski.edziennik.ui.agenda

import android.os.Build
import android.util.SparseIntArray
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.AbsListView.OnScrollListener
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.util.forEach
import androidx.core.util.set
import androidx.core.view.isVisible
import com.github.tibolte.agendacalendarview.CalendarManager
import com.github.tibolte.agendacalendarview.CalendarPickerController
import com.github.tibolte.agendacalendarview.R
import com.github.tibolte.agendacalendarview.agenda.AgendaAdapter
import com.github.tibolte.agendacalendarview.models.BaseCalendarEvent
import com.github.tibolte.agendacalendarview.models.CalendarEvent
import com.github.tibolte.agendacalendarview.models.IDayItem
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.ext.dp
import pl.szczodrzynski.edziennik.ext.resolveAttr
import pl.szczodrzynski.edziennik.MainActivity
import pl.szczodrzynski.edziennik.data.db.full.EventFull
import pl.szczodrzynski.edziennik.databinding.FragmentAgendaDefaultBinding
import pl.szczodrzynski.edziennik.ui.agenda.event.AgendaEvent
import pl.szczodrzynski.edziennik.ui.agenda.event.AgendaEventGroup
import pl.szczodrzynski.edziennik.ui.agenda.event.AgendaEventGroupRenderer
import pl.szczodrzynski.edziennik.ui.agenda.event.AgendaEventRenderer
import pl.szczodrzynski.edziennik.ui.agenda.lessonchanges.LessonChangesDialog
import pl.szczodrzynski.edziennik.ui.agenda.lessonchanges.LessonChangesEvent
import pl.szczodrzynski.edziennik.ui.agenda.lessonchanges.LessonChangesEventRenderer
import pl.szczodrzynski.edziennik.ui.agenda.teacherabsence.TeacherAbsenceDialog
import pl.szczodrzynski.edziennik.ui.agenda.teacherabsence.TeacherAbsenceEvent
import pl.szczodrzynski.edziennik.ui.agenda.teacherabsence.TeacherAbsenceEventRenderer
import pl.szczodrzynski.edziennik.ui.event.EventDetailsDialog
import pl.szczodrzynski.edziennik.utils.models.Date
import java.util.*

class AgendaFragmentDefault(
    private val activity: MainActivity,
    private val app: App,
    private val b: FragmentAgendaDefaultBinding
) : OnScrollListener, CoroutineScope {
    companion object {
        var selectedDate: Date = Date.getToday()
    }

    override val coroutineContext = Job() + Dispatchers.Main

    private val unreadDates = mutableSetOf<Int>()
    private val events = mutableListOf<CalendarEvent>()
    private var isInitialized = false
    private val cardMargin = 8.dp
    private val cardRadius = 24.dp
    private val collapsedCalendarHeight
        get() = (
            activity.resources.getDimension(R.dimen.calendar_header_height) +
            2 * activity.resources.getDimension(R.dimen.day_cell_height)
        ).toInt()
    private var listTopInset = 0
    private var scrollTargetDay: Calendar? = null

    private val listView
        get() = b.agendaDefaultView.agendaView.agendaListView
    private val adapter
        get() = listView.adapter as? AgendaAdapter
    private val manager
        get() = CalendarManager.getInstance()

    private var scrollState = OnScrollListener.SCROLL_STATE_IDLE
    private var updatePending = false
    private var notifyPending = false
    override fun onScrollStateChanged(view: AbsListView?, newScrollState: Int) {
        b.agendaDefaultView.agendaScrollListener.onScrollStateChanged(view, scrollState)
        scrollState = newScrollState
        if (newScrollState != OnScrollListener.SCROLL_STATE_IDLE) {
            // the user is scrolling the list, so stop keeping a day at the top
            scrollTargetDay = null
        }
        if (updatePending) updateData()
        if (notifyPending) notifyDataSetChanged()
    }

    override fun onScroll(
        view: AbsListView?,
        firstVisibleItem: Int,
        visibleItemCount: Int,
        totalItemCount: Int
    ) = b.agendaDefaultView.agendaScrollListener.onScroll(
        view,
        firstVisibleItem,
        visibleItemCount,
        totalItemCount
    )

    /**
     * Mark the data as needing update, either after 1 second (when
     * not scrolling) or 1 second after scrolling stops.
     */
    private fun updateData() = launch {
        if (scrollState == OnScrollListener.SCROLL_STATE_IDLE) {
            updatePending = false
            delay(1000)
            notifyDataSetChanged()
        } else updatePending = true
    }

    /**
     * Notify the adapter about changes, either instantly or after
     * scrolling stops.
     */
    private fun notifyDataSetChanged() {
        if (scrollState == OnScrollListener.SCROLL_STATE_IDLE) {
            notifyPending = false
            adapter?.notifyDataSetChanged()
        } else notifyPending = true
    }

    suspend fun initView(fragment: AgendaFragment) {
        isInitialized = false

        withContext(Dispatchers.Default) {
            if (app.profile.config.ui.agendaLessonChanges)
                addLessonChanges(events)

            if (app.profile.config.ui.agendaTeacherAbsence)
                addTeacherAbsence(events)
        }

        app.db.eventDao().getAll(app.profileId).observe(fragment) {
            addEvents(events, it)
            if (isInitialized)
                updateView()
            else
                initViewPriv()
        }
    }

    private fun initViewPriv() {
        val dateStart = app.profile.dateSemester1Start.asCalendar
        val dateEnd = app.profile.dateYearEnd.asCalendar

        val isCompactMode = app.profile.config.ui.agendaCompactMode

        b.agendaDefaultView.init(
            events,
            dateStart,
            dateEnd,
            Locale.getDefault(),
            object : CalendarPickerController {
                override fun onDaySelected(dayItem: IDayItem) {
                    val c = Calendar.getInstance()
                    c.time = dayItem.date
                    if (c.timeInMillis == selectedDate.inMillis) {
                        DayDialog(activity, app.profileId, selectedDate).show()
                    } else {
                        scrollToSelectedDate(c)
                    }
                }

                override fun onEventSelected(event: CalendarEvent) {
                    val date = Date.fromCalendar(event.instanceDay)

                    when (event) {
                        is AgendaEvent -> EventDetailsDialog(activity, event.event).show()
                        is LessonChangesEvent -> LessonChangesDialog(
                            activity = activity,
                            profileId = app.profileId,
                            defaultDate = date
                        ).show()
                        is TeacherAbsenceEvent -> TeacherAbsenceDialog(
                            activity = activity,
                            profileId = app.profileId,
                            date = date
                        ).show()
                        is AgendaEventGroup -> DayDialog(
                            activity = activity,
                            profileId = app.profileId,
                            date = date,
                            eventTypeId = event.typeId,
                        ).show()
                        is BaseCalendarEvent -> if (event.isPlaceHolder)
                            DayDialog(activity, app.profileId, date).show()
                    }

                    if (event is BaseEvent && event.showItemBadge) {
                        val unreadCount = manager.events.count {
                            it.instanceDay.equals(event.instanceDay) && it.showBadge
                        }
                        // only clicked event is unread, remove the day badge
                        if (unreadCount == 1 && event.showBadge) {
                            event.dayReference.showBadge = false
                            unreadDates.remove(date.value)
                        }
                        setAsRead(event)
                    }
                }

                override fun onScrollToDate(calendar: Calendar) {
                    selectedDate = Date.fromCalendar(calendar)

                    // Mark as read scrolled date
                    if (selectedDate.value in unreadDates) {
                        setAsRead(calendar)
                        activity.launch(Dispatchers.Default) {
                            app.db.eventDao().setSeenByDate(app.profileId, selectedDate, true)
                        }
                        unreadDates.remove(selectedDate.value)
                    }
                }
            },
            AgendaEventRenderer(app.eventManager, isCompactMode),
            AgendaEventGroupRenderer(),
            LessonChangesEventRenderer(),
            TeacherAbsenceEventRenderer()
        )

        val calendarView = b.agendaDefaultView.findViewById<View>(
            R.id.calendar_view
        ) ?: b.agendaDefaultView.getChildAt(0)

        val dayNamesHeader = (calendarView as? ViewGroup)?.getChildAt(0) as? ViewGroup
        if (dayNamesHeader != null) {
            val abbreviations = activity.resources.getStringArray(
                com.applandeo.materialcalendarview.R.array.material_calendar_day_abbreviations_array
            )
            for (i in 0 until dayNamesHeader.childCount) {
                val textView = dayNamesHeader.getChildAt(i) as? TextView
                if (textView != null && abbreviations.isNotEmpty()) {
                    textView.text = abbreviations[(i + 1) % abbreviations.size]
                }
            }
        }

        if (calendarView != null && calendarView.parent is ViewGroup && calendarView.parent !is MaterialCardView) {
            val parent = calendarView.parent as ViewGroup
            val params = calendarView.layoutParams

            parent.removeView(calendarView)

            val cardView = MaterialCardView(activity).apply {
                layoutParams = params
                radius = cardRadius.toFloat()
                cardElevation = 4.dp.toFloat()
                setCardBackgroundColor(pl.szczodrzynski.edziennik.R.attr.colorCard.resolveAttr(activity))
                strokeColor = com.google.android.material.R.attr.colorOutlineVariant.resolveAttr(activity)
                strokeWidth = 1.dp

                if (this.layoutParams is ViewGroup.MarginLayoutParams) {
                    (this.layoutParams as ViewGroup.MarginLayoutParams).setMargins(8.dp, cardMargin, 8.dp, cardMargin)
                }

                // the height the library collapses the calendar to, set before
                // the first layout - otherwise the weeks list measures itself
                // to the whole page and flashes expanded while loading
                addView(calendarView, ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    collapsedCalendarHeight
                ))
            }

            parent.addView(cardView)
            cardView.bringToFront()

            val agendaView = b.agendaDefaultView.agendaView
            val agendaParams = agendaView.layoutParams as? FrameLayout.LayoutParams
                ?: FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            agendaParams.width = FrameLayout.LayoutParams.MATCH_PARENT
            agendaParams.height = FrameLayout.LayoutParams.MATCH_PARENT
            agendaParams.setMargins(0, 0, 0, 0)
            agendaView.layoutParams = agendaParams
            agendaView.enablePlaceholderForCalendar(false)

            agendaView.viewTreeObserver.addOnPreDrawListener {
                if (agendaView.translationY != 0f) {
                    agendaView.translationY = 0f
                }
                true
            }

            calendarView.viewTreeObserver.addOnPreDrawListener {
                if (calendarView.translationY != 0f) {
                    calendarView.translationY = 0f
                }
                true
            }

            cardView.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, _ ->
                setListTopInset(bottom)
            }

            agendaView.findViewById<View>(
                R.id.view_shadow
            )?.visibility = View.GONE

            for (i in 0 until b.agendaDefaultView.childCount) {
                val child = b.agendaDefaultView.getChildAt(i)
                if (child != cardView && child != calendarView && child != agendaView) {
                    if (child.javaClass.simpleName != "FloatingActionButton") {
                        child.visibility = View.GONE
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                calendarView.elevation = 0f
                calendarView.translationZ = 0f
                agendaView.elevation = 0f
                agendaView.translationZ = 0f
            }
        }

        listView.setOnScrollListener(this)

        isInitialized = true
        b.progressBar.isVisible = false

        scrollToSelectedDate(selectedDate.asCalendar)
    }

    /**
     * Make the list start right below the calendar card, which floats above it.
     *
     * The items are laid out (and clipped) a corner radius above the bottom
     * edge of the card, so that they show in its rounded corners. They must
     * not be laid out any higher than that: the sticky header is picked
     * assuming that at most one item reaches above it, so a list laid out
     * behind the whole calendar makes both the pinned day header and the day
     * circled on the calendar lag one day behind the list.
     *
     * The wrapper's negative padding is added back to every scroll position
     * requested from it, which puts the day scrolled to - also by the library
     * itself, on day clicks and on the initial scroll to today - just above the
     * sticky header line. Its own day header is then hidden and the library
     * pins a copy of it right below the calendar.
     */
    private fun setListTopInset(inset: Int) {
        if (inset <= 0 || inset == listTopInset)
            return
        listTopInset = inset

        listView.clipToPadding = false
        listView.setPadding(
            listView.paddingLeft,
            1.dp - cardRadius,
            listView.paddingRight,
            listView.paddingBottom
        )
        listView.setStickyHeaderTopOffset(inset)

        // both calls above are forwarded to the wrapped list, so its own
        // values have to be set afterwards
        with(listView.wrappedList) {
            clipToPadding = true
            setPadding(paddingLeft, inset - cardRadius, paddingRight, paddingBottom)
        }

        // changing the padding doesn't move the items, so scroll them again
        scrollTargetDay?.let { day ->
            listView.post { scrollToDay(day) }
        }
    }

    private fun scrollToSelectedDate(calendar: Calendar) {
        scrollTargetDay = calendar
        listView.post {
            // the library also scrolls to today after laying out the list
            listView.post {
                scrollToDay(calendar)
            }
        }
    }

    private fun scrollToDay(calendar: Calendar) {
        val index = manager.events.indexOfFirst {
            it.instanceDay.get(Calendar.DAY_OF_YEAR) == calendar.get(Calendar.DAY_OF_YEAR) &&
            it.instanceDay.get(Calendar.YEAR) == calendar.get(Calendar.YEAR)
        }
        if (index >= 0) {
            listView.setSelection(index)
        }
    }

    private fun updateView() {
        manager.events.clear()
        manager.loadEvents(events, BaseCalendarEvent())

        adapter?.updateEvents(manager.events)
        scrollToSelectedDate(selectedDate.asCalendar)
    }

    private fun setAsRead(date: Calendar) {
        // get all events matching the date
        val events = manager.events.filter {
            if (it.instanceDay.equals(date) && it.showBadge && it is AgendaEvent) {
                // hide the day badge for the date
                it.dayReference.showBadge = false
                return@filter true
            }
            false
        }
        // set this date's events as read
        setAsRead(*events.toTypedArray())
    }

    private fun setAsRead(vararg event: CalendarEvent) {
        // hide per-event badges
        for (e in event) {
            events.firstOrNull {
                it == e
            }?.showBadge = false
            e.showBadge = false
        }

        listView.setOnScrollListener(this)
        updateData()
    }

    private fun addEvents(
        events: MutableList<CalendarEvent>,
        eventList: List<EventFull>
    ) {
        events.removeAll { it is AgendaEvent || it is AgendaEventGroup }

        if (!app.profile.config.ui.agendaGroupByType) {
            events += eventList.map {
                if (!it.seen)
                    unreadDates.add(it.date.value)
                AgendaEvent(it, color = app.eventManager.getEventColor(it))
            }
            return
        }

        eventList.groupBy {
            it.date.value to it.type
        }.forEach { (_, list) ->
            val event = list.first()
            if (list.size == 1) {
                if (!event.seen)
                    unreadDates.add(event.date.value)
                events += AgendaEvent(event, color = app.eventManager.getEventColor(event))
            } else {
                events.add(0, AgendaEventGroup(
                    profileId = event.profileId,
                    date = event.date,
                    typeId = event.type,
                    typeName = event.typeName ?: "-",
                    typeColor = app.eventManager.getEventColor(event),
                    count = list.size,
                    showBadge = list.any { !it.seen }
                ))
            }
        }
    }

    private fun addLessonChanges(events: MutableList<CalendarEvent>) {
        val lessons = app.db.timetableDao().getChangesNow(app.profileId)

        val grouped = lessons.groupBy {
            it.displayDate
        }

        events += grouped.mapNotNull { (date, changes) ->
            LessonChangesEvent(
                app.profileId,
                date = date ?: return@mapNotNull null,
                count = changes.size,
                showBadge = changes.any { !it.seen }
            )
        }
    }

    private fun addTeacherAbsence(events: MutableList<CalendarEvent>) {
        val teacherAbsence = app.db.teacherAbsenceDao().getAllNow(app.profileId)

        val countMap = SparseIntArray()

        for (absence in teacherAbsence) {
            while (absence.dateFrom <= absence.dateTo) {
                countMap[absence.dateFrom.value] += 1
                absence.dateFrom.stepForward(0, 0, 1)
            }
        }

        countMap.forEach { dateInt, count ->
            events += TeacherAbsenceEvent(
                app.profileId,
                date = Date.fromValue(dateInt),
                count = count
            )
        }
    }
}
