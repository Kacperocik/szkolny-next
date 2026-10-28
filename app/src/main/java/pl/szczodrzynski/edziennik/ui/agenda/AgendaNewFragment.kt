package pl.szczodrzynski.edziennik.ui.agenda

import android.graphics.Color
import android.content.res.Configuration
import android.os.Bundle
import android.util.SparseIntArray
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import androidx.core.view.updateLayoutParams
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import eu.szkolny.font.SzkolnyFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.szczodrzynski.edziennik.MainActivity
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.db.entity.Profile
import pl.szczodrzynski.edziennik.data.db.full.EventFull
import pl.szczodrzynski.edziennik.data.enums.FeatureType
import pl.szczodrzynski.edziennik.data.enums.MetadataType
import pl.szczodrzynski.edziennik.databinding.FragmentAgendaNewBinding
import pl.szczodrzynski.edziennik.ext.dp
import pl.szczodrzynski.edziennik.ext.toDrawable
import pl.szczodrzynski.edziennik.ui.agenda.event.AgendaEventGroup
import pl.szczodrzynski.edziennik.ui.agenda.lessonchanges.LessonChangesDialog
import pl.szczodrzynski.edziennik.ui.agenda.lessonchanges.LessonChangesEvent
import pl.szczodrzynski.edziennik.ui.agenda.teacherabsence.TeacherAbsenceDialog
import pl.szczodrzynski.edziennik.ui.agenda.teacherabsence.TeacherAbsenceEvent
import pl.szczodrzynski.edziennik.ui.base.fragment.BaseFragment
import pl.szczodrzynski.edziennik.ui.dialogs.settings.AgendaConfigDialog
import pl.szczodrzynski.edziennik.ui.event.EventDetailsDialog
import pl.szczodrzynski.edziennik.ui.event.EventManualDialog
import pl.szczodrzynski.edziennik.utils.models.Date
import pl.szczodrzynski.navlib.bottomsheet.items.BottomSheetPrimaryItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

class AgendaNewFragment : BaseFragment<FragmentAgendaNewBinding, MainActivity>(FragmentAgendaNewBinding::inflate) {

    private lateinit var weekDaysAdapter: WeekDaysAdapter
    private lateinit var monthCalendarAdapter: MonthCalendarAdapter
    private lateinit var agendaAdapter: AgendaNewAdapter

    private var selectedDate: Date = Date.getToday()
    private var visibleMonth: Date = Date.getToday()

    /**
     * True while a temporary full-month preview is shown (pull down gesture
     * in week modes). Collapses back to the configured week range on focus loss.
     */
    private var monthPreviewActive = false

    private var monthGridHeightAnimator: ValueAnimator? = null

    private var pullPointerId = MotionEvent.INVALID_POINTER_ID
    private var pullStartX = 0f
    private var pullStartY = 0f
    private var pullTriggered = false

    private val rawEvents = mutableListOf<EventFull>()
    private val rawLessonChanges = mutableListOf<LessonChangesEvent>()
    private val rawTeacherAbsences = mutableListOf<TeacherAbsenceEvent>()
    private var viewsReady = false
    private var inflatedOrientation = Configuration.ORIENTATION_UNDEFINED

    override fun getFab() = R.string.add to CommunityMaterial.Icon3.cmd_plus
    override suspend fun onFabClick() {
        collapseMonthPreview()
        EventManualDialog(
            activity,
            app.profileId,
            defaultDate = selectedDate
        ).show()
    }
    override fun getMarkAsReadType() = MetadataType.EVENT
    override fun getSyncParams() = FeatureType.AGENDA to null
    override fun getBottomSheetItems() = listOf(
        BottomSheetPrimaryItem(true)
            .withTitle(R.string.menu_add_event)
            .withDescription(R.string.menu_add_event_desc)
            .withIcon(SzkolnyFont.Icon.szf_calendar_plus_outline)
            .withOnClickListener {
                activity.bottomSheet.close()
                EventManualDialog(
                    activity,
                    app.profileId,
                    defaultDate = selectedDate
                ).show()
            },
        BottomSheetPrimaryItem(true)
            .withTitle(R.string.menu_agenda_config)
            .withIcon(CommunityMaterial.Icon.cmd_cog_outline)
            .withOnClickListener {
                activity.bottomSheet.close()
                AgendaConfigDialog(activity, true, isNewAgenda = true).show()
            },
        BottomSheetPrimaryItem(true)
            .withTitle(R.string.menu_agenda_change_view)
            .withIcon(
                if (app.profile.config.ui.agendaViewType == Profile.AGENDA_DEFAULT)
                    CommunityMaterial.Icon.cmd_calendar_outline
                else
                    CommunityMaterial.Icon2.cmd_format_list_bulleted_square
            )
            .withOnClickListener {
                activity.bottomSheet.close()
                app.profile.config.ui.agendaViewType =
                    if (app.profile.config.ui.agendaViewType == Profile.AGENDA_DEFAULT)
                        Profile.AGENDA_CALENDAR
                    else
                        Profile.AGENDA_DEFAULT
                applyViewType(animate = true)
                activity.reloadTarget()
            },
    )

    private fun getSchoolYearStart(): Date {
        val isUniversity = app.gradesManager.isUniversity
        return if (isUniversity) {
            Date(app.profile.studentSchoolYearStart, 10, 1)
        } else {
            app.profile.dateSemester1Start
        }
    }

    private fun getSchoolYearEnd(): Date {
        val isUniversity = app.gradesManager.isUniversity
        return if (isUniversity) {
            Date(app.profile.studentSchoolYearStart + 1, 7, 31)
        } else {
            app.profile.dateYearEnd
        }
    }

    override suspend fun onViewReady(savedInstanceState: Bundle?) {
        setupTopCalendar()
        setupMonthCalendar()
        setupMainEventsList()
        applyViewType()
        updateMonthNavigation()

        loadData()
        viewsReady = true
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        inflatedOrientation = resources.configuration.orientation
    }

    /**
     * MainActivity handles orientation|screenSize itself, so the view is not
     * recreated on rotation. Recreate it through the FragmentManager
     * (detach/attach) to pick the matching portrait/landscape layout.
     * State (selected date, visible month, cached data) is kept in fields,
     * LiveData observers are re-registered on the new view lifecycle.
     *
     * Detach and attach must be committed as two separate transactions -
     * within a single one the FragmentManager only applies the final state
     * (still attached), so the view would never be recreated.
     *
     * Note: the view must NOT be swapped manually with removeView/addView -
     * FragmentManager tracks the original root and would fail to remove
     * the manually added one on navigation, leaving a frozen agenda
     * behind in the container.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!isAdded || view == null) return
        if (newConfig.orientation == inflatedOrientation) return
        view?.post { recreateView() }
    }

    private fun recreateView() {
        if (!isAdded || isDetached || view == null) return
        if (resources.configuration.orientation == inflatedOrientation) return
        val fm = parentFragmentManager
        if (fm.isDestroyed) return
        monthGridHeightAnimator?.cancel()
        monthGridHeightAnimator = null
        monthPreviewActive = false
        viewsReady = false
        fm.beginTransaction().detach(this).commitNowAllowingStateLoss()
        fm.beginTransaction().attach(this).commitNowAllowingStateLoss()
    }

    private fun applyViewType(animate: Boolean = false) {
        val isCalendar = app.profile.config.ui.agendaViewType == Profile.AGENDA_CALENDAR
        val showView = if (isCalendar) b.monthCalendarCard else b.calendarCard
        val hideView = if (isCalendar) b.calendarCard else b.monthCalendarCard

        if (isCalendar) {
            buildMonthCalendarGrid()
        }

        if (animate) {
            hideView.animate()
                .alpha(0f)
                .setDuration(100)
                .withEndAction {
                    hideView.visibility = View.GONE
                    hideView.alpha = 1f

                    showView.alpha = 0f
                    showView.visibility = View.VISIBLE
                    showView.animate()
                        .alpha(1f)
                        .setDuration(150)
                        .start()
                    syncMonthScrollWrapper()
                    updateCalendarColumnWidth(isCalendar)
                }
                .start()
        } else {
            showView.visibility = View.VISIBLE
            showView.alpha = 1f
            hideView.visibility = View.GONE
            syncMonthScrollWrapper()
            updateCalendarColumnWidth(isCalendar)
        }
    }

    /**
     * In landscape the week strip does not need half of the screen:
     * it becomes a narrow fixed-width column, while the month grid
     * keeps half of the width. In portrait there is no side column.
     */
    private fun updateCalendarColumnWidth(isCalendar: Boolean) {
        val column = b.root.findViewById<View>(R.id.calendarColumn) ?: return
        val lp = column.layoutParams as? LinearLayout.LayoutParams ?: return
        if (isCalendar) {
            lp.width = 0
            lp.weight = 1f
        } else {
            lp.width = 148.dp
            lp.weight = 0f
        }
        column.layoutParams = lp
    }

    /**
     * In landscape the month card sits inside its own scroll container
     * (R.id.monthCalendarScroll), which must follow the card visibility -
     * otherwise the empty container would cover the week strip and
     * intercept touches. In portrait the card has no such wrapper.
     */
    private fun syncMonthScrollWrapper() {
        val parent = b.monthCalendarCard.parent as? View ?: return
        if (parent.id == R.id.monthCalendarScroll) {
            parent.visibility = b.monthCalendarCard.visibility
        }
    }

    private fun showDayEvents(date: Date) {
        DayDialog(activity, app.profileId, date).show()
    }

    private fun setupTopCalendar() {
        weekDaysAdapter = WeekDaysAdapter(
            requireContext(),
            onDayLongClick = ::showDayEvents,
        ) { pill ->
            selectedDate = pill.date
            updateCurrentMonthHeader()
            scrollToDate(pill.date)
        }

        val lm = LinearLayoutManager(
            requireContext(),
            if (requireContext().resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
                LinearLayoutManager.VERTICAL
            else
                LinearLayoutManager.HORIZONTAL,
            false
        )
        b.weekDaysRecyclerView.layoutManager = lm
        b.weekDaysRecyclerView.adapter = weekDaysAdapter

        b.weekDaysRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val firstPos = lm.findFirstVisibleItemPosition()
                val lastPos = lm.findLastVisibleItemPosition()
                if (firstPos >= 0 && lastPos >= firstPos && lastPos < weekDaysAdapter.days.size) {
                    val visiblePills = weekDaysAdapter.days.subList(firstPos, lastPos + 1)
                    val dominantMonthDate = visiblePills.groupBy { it.date.month to it.date.year }
                        .maxByOrNull { it.value.size }?.value?.firstOrNull()?.date
                    if (dominantMonthDate != null) {
                        val cal = dominantMonthDate.asCalendar
                        val sdf = SimpleDateFormat("LLLL yyyy", Locale("pl"))
                        b.currentMonthText.text = sdf.format(cal.time).replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase(Locale("pl")) else it.toString()
                        }
                    }
                }
            }
        })

        b.btnToday.setOnClickListener {
            selectedDate = Date.getToday()
            weekDaysAdapter.selectDate(selectedDate)
            updateCurrentMonthHeader()
            centerDayPill(selectedDate, smooth = false)
            scrollToDate(selectedDate)
        }

        updateCurrentMonthHeader()
        buildTopCalendarPills()
    }

    private fun setupMonthCalendar() {
        monthCalendarAdapter = MonthCalendarAdapter(
            requireContext(),
            onDayLongClick = ::showDayEvents,
        ) { cell ->
            if (cell.date != null) {
                selectedDate = cell.date
                if (!cell.isCurrentMonth) {
                    val forward = cell.date > visibleMonth
                    animateToMonth(cell.date, forward = forward)
                }
                scrollToDate(cell.date)
            }
        }

        b.monthDaysRecyclerView.adapter = monthCalendarAdapter

        b.btnPrevMonth.setImageDrawable(
            CommunityMaterial.Icon.cmd_chevron_left.toDrawable(requireContext(), colorAttr = android.R.attr.textColorPrimary)
        )
        b.btnNextMonth.setImageDrawable(
            CommunityMaterial.Icon.cmd_chevron_right.toDrawable(requireContext(), colorAttr = android.R.attr.textColorPrimary)
        )

        b.btnPrevMonth.setOnClickListener {
            stepMonth(forward = false)
        }

        b.btnNextMonth.setOnClickListener {
            stepMonth(forward = true)
        }

        b.btnMonthToday.setOnClickListener {
            val today = Date.getToday()
            val forward = today > visibleMonth
            selectedDate = today
            monthCalendarAdapter.selectDate(selectedDate)
            animateToMonth(today, forward = forward)
            scrollToDate(selectedDate)
        }

        setupMonthCalendarSwipeGesture()
        updateMonthHeader()
    }

    private fun stepMonth(forward: Boolean) {
        val weeksMode = app.profile.config.ui.agendaCalendarWeeks
        if (!monthPreviewActive && (weeksMode == 1 || weeksMode == 2)) {
            stepWeeks(forward, weeksMode)
            return
        }
        val targetMonth = visibleMonth.clone().apply { stepForward(0, if (forward) 1 else -1, 0) }
        animateToMonth(targetMonth, forward = forward)
    }

    private fun stepWeeks(forward: Boolean, weeks: Int) {
        val target = visibleMonth.clone().apply {
            stepForward(0, 0, if (forward) 7 * weeks else -7 * weeks)
        }
        animateToMonth(target, forward = forward, forceSlide = true)
    }

    /**
     * In 3-week mode there is nowhere to navigate, so the arrows are hidden
     * (swipe is disabled separately in the gesture handler) - unless the
     * full-month preview is active, which is always navigable by months.
     * INVISIBLE (not GONE) keeps the header layout stable when toggling.
     */
    private fun updateMonthNavigation() {
        val showArrows = app.profile.config.ui.agendaCalendarWeeks != 3 || monthPreviewActive
        b.btnPrevMonth.visibility = if (showArrows) View.VISIBLE else View.INVISIBLE
        b.btnNextMonth.visibility = if (showArrows) View.VISIBLE else View.INVISIBLE
    }

    private fun expandMonthPreview() {
        if (monthPreviewActive) return
        if (app.profile.config.ui.agendaCalendarWeeks !in 1..3) return
        if (!isAdded) return
        monthPreviewActive = true
        updateMonthNavigation()
        buildMonthCalendarGrid()
        animateMonthGridHeight()
    }

    private fun collapseMonthPreview() {
        if (!monthPreviewActive) return
        monthPreviewActive = false
        if (!isAdded) return
        updateMonthNavigation()
        buildMonthCalendarGrid()
        animateMonthGridHeight()
    }

    /**
     * Smoothly animates the month grid height between the week range
     * and the full month (and back), instead of jumping instantly.
     */
    private fun animateMonthGridHeight() {
        val rv = b.monthDaysRecyclerView
        monthGridHeightAnimator?.cancel()
        monthGridHeightAnimator = null
        val startHeight = rv.height
        if (rv.width <= 0 || startHeight <= 0) {
            rv.updateLayoutParams<ViewGroup.LayoutParams> {
                height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
            return
        }
        rv.measure(
            View.MeasureSpec.makeMeasureSpec(rv.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val endHeight = rv.measuredHeight
        if (endHeight <= 0 || endHeight == startHeight) return
        monthGridHeightAnimator = ValueAnimator.ofInt(startHeight, endHeight).apply {
            duration = 450
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener {
                rv.updateLayoutParams<ViewGroup.LayoutParams> {
                    height = it.animatedValue as Int
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    rv.updateLayoutParams<ViewGroup.LayoutParams> {
                        height = ViewGroup.LayoutParams.WRAP_CONTENT
                    }
                }
            })
            start()
        }
    }

    private fun animateToMonth(targetMonth: Date, forward: Boolean, forceSlide: Boolean = false) {
        if (!forceSlide && targetMonth.month == visibleMonth.month && targetMonth.year == visibleMonth.year) {
            visibleMonth = targetMonth
            updateMonthHeader()
            buildMonthCalendarGrid()
            return
        }

        val width = b.monthDaysRecyclerView.width.toFloat().coerceAtLeast(100f)
        val slideOutX = if (forward) -width else width
        val slideInX = if (forward) width else -width

        b.monthDaysRecyclerView.animate()
            .translationX(slideOutX)
            .alpha(0f)
            .setDuration(100)
            .withEndAction {
                visibleMonth = targetMonth
                updateMonthHeader()
                buildMonthCalendarGrid()

                b.monthDaysRecyclerView.translationX = slideInX
                b.monthDaysRecyclerView.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(150)
                    .start()
            }
            .start()
    }

    private fun setupMonthCalendarSwipeGesture() {
        val swipeThreshold = (40 * requireContext().resources.displayMetrics.density).toInt()
        val swipeVelocityThreshold = 100
        val pullThreshold = (56 * requireContext().resources.displayMetrics.density)

        val gestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y

                if (abs(diffY) > abs(diffX) &&
                    diffY > swipeThreshold &&
                    velocityY > 1000
                ) {
                    expandMonthPreview()
                    return true
                }

                if (Math.abs(diffX) > Math.abs(diffY) &&
                    Math.abs(diffX) > swipeThreshold &&
                    Math.abs(velocityX) > swipeVelocityThreshold
                ) {
                    // In 3-week mode there is nowhere to navigate - except
                    // in the full-month preview, which steps by months.
                    if (!monthPreviewActive && app.profile.config.ui.agendaCalendarWeeks == 3) return false
                    if (diffX > 0) {
                        stepMonth(forward = false)
                    } else {
                        stepMonth(forward = true)
                    }
                    return true
                }
                return false
            }
        })

        b.monthDaysRecyclerView.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        pullPointerId = e.getPointerId(0)
                        pullStartX = e.x
                        pullStartY = e.y
                        pullTriggered = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!pullTriggered && !monthPreviewActive) {
                            val index = e.findPointerIndex(pullPointerId)
                            if (index != -1) {
                                val dx = e.getX(index) - pullStartX
                                val dy = e.getY(index) - pullStartY
                                if (dy > pullThreshold && dy > abs(dx)) {
                                    pullTriggered = true
                                    expandMonthPreview()
                                }
                            }
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        pullPointerId = MotionEvent.INVALID_POINTER_ID
                        pullTriggered = false
                    }
                }
                gestureDetector.onTouchEvent(e)
                return false
            }
        })
    }

    private fun updateCurrentMonthHeader() {
        val cal = selectedDate.asCalendar
        val sdf = SimpleDateFormat("LLLL yyyy", Locale("pl"))
        b.currentMonthText.text = sdf.format(cal.time).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("pl")) else it.toString() }
    }

    private fun updateMonthHeader() {
        val cal = visibleMonth.asCalendar
        val sdf = SimpleDateFormat("LLLL yyyy", Locale("pl"))
        b.monthCurrentText.text = sdf.format(cal.time).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("pl")) else it.toString() }
    }

    private fun loadData() {
        app.db.eventDao().getAll(app.profileId).observe(viewLifecycleOwner) { eventList ->
            synchronized(rawEvents) {
                rawEvents.clear()
                rawEvents.addAll(eventList)
            }
            updateData()
        }

        app.db.teacherAbsenceDao().getAll(app.profileId).observe(viewLifecycleOwner) { absenceList ->
            synchronized(rawTeacherAbsences) {
                rawTeacherAbsences.clear()
                val countMap = SparseIntArray()
                for (absence in absenceList) {
                    val curr = absence.dateFrom.clone()
                    while (curr <= absence.dateTo) {
                        countMap.put(curr.value, countMap.get(curr.value, 0) + 1)
                        curr.stepForward(0, 0, 1)
                    }
                }
                for (i in 0 until countMap.size()) {
                    val dateInt = countMap.keyAt(i)
                    val count = countMap.valueAt(i)
                    rawTeacherAbsences.add(TeacherAbsenceEvent(app.profileId, Date.fromValue(dateInt), count))
                }
            }
            updateData()
        }

        app.db.timetableDao().getChanges(app.profileId).observe(viewLifecycleOwner) { changesList ->
            synchronized(rawLessonChanges) {
                rawLessonChanges.clear()
                val grouped = changesList.groupBy { it.displayDate }
                grouped.forEach { (date, list) ->
                    if (date != null) {
                        rawLessonChanges.add(LessonChangesEvent(app.profileId, date, list.size, list.any { !it.seen }))
                    }
                }
            }
            updateData()
        }
    }

    private fun updateData() {
        val eventsSnapshot = synchronized(rawEvents) { rawEvents.toList() }
        val changesSnapshot = synchronized(rawLessonChanges) { rawLessonChanges.toList() }
        val absencesSnapshot = synchronized(rawTeacherAbsences) { rawTeacherAbsences.toList() }

        buildTopCalendarPills(eventsSnapshot, changesSnapshot, absencesSnapshot)
        buildMonthCalendarGrid(eventsSnapshot, changesSnapshot, absencesSnapshot)
        buildAgendaList(eventsSnapshot, changesSnapshot, absencesSnapshot)
    }

    private fun setupMainEventsList() {
        agendaAdapter = AgendaNewAdapter(
            context = requireContext(),
            app = app,
            onEventClick = { event ->
                EventDetailsDialog(activity, event).show()
            },
            onCounterClick = { counter ->
                when (counter.type) {
                    AgendaListItem.CounterType.LESSON_CHANGES -> {
                        LessonChangesDialog(
                            activity = activity,
                            profileId = app.profileId,
                            defaultDate = counter.date
                        ).show()
                    }
                    AgendaListItem.CounterType.TEACHER_ABSENCE -> {
                        TeacherAbsenceDialog(
                            activity = activity,
                            profileId = app.profileId,
                            date = counter.date
                        ).show()
                    }
                }
            },
            onGroupClick = { group ->
                DayDialog(
                    activity = activity,
                    profileId = app.profileId,
                    date = group.date,
                    eventTypeId = group.typeId,
                ).show()
            }
        )

        b.eventsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        b.eventsRecyclerView.adapter = agendaAdapter

        // Losing focus collapses the temporary full-month preview.
        b.eventsRecyclerView.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                if (e.actionMasked == MotionEvent.ACTION_DOWN) collapseMonthPreview()
                return false
            }
        })

        b.eventsRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            private var isDragging = false

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    isDragging = true
                    collapseMonthPreview()
                } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    isDragging = false
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (!isDragging) return
                val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val firstPos = lm.findFirstVisibleItemPosition()
                if (firstPos in 0 until agendaAdapter.items.size) {
                    val item = agendaAdapter.items[firstPos]
                    val date = when (item) {
                        is AgendaListItem.DayHeader -> item.date
                        is AgendaListItem.Event -> item.event.date
                        is AgendaListItem.Group -> item.group.date
                        is AgendaListItem.Counter -> item.date
                        is AgendaListItem.Empty -> item.date
                    }
                    if (date.stringY_m_d != selectedDate.stringY_m_d) {
                        selectedDate = date
                        val isCalendar = app.profile.config.ui.agendaViewType == Profile.AGENDA_CALENDAR
                        if (isCalendar) {
                            val weeksMode = app.profile.config.ui.agendaCalendarWeeks
                            if (!monthPreviewActive && weeksMode in 1..3) {
                                // Follow the scrolled date: shift the week range
                                // once it leaves the displayed weeks.
                                val (rangeStart, rangeEnd) = visibleWeeksRange(weeksMode)
                                if (selectedDate < rangeStart || selectedDate > rangeEnd) {
                                    visibleMonth = selectedDate
                                    updateMonthHeader()
                                    buildMonthCalendarGrid()
                                } else {
                                    monthCalendarAdapter.selectDate(selectedDate)
                                }
                            } else {
                                monthCalendarAdapter.selectDate(selectedDate)
                                if (selectedDate.month != visibleMonth.month || selectedDate.year != visibleMonth.year) {
                                    visibleMonth = selectedDate
                                    updateMonthHeader()
                                    buildMonthCalendarGrid()
                                }
                            }
                        } else {
                            weekDaysAdapter.selectDate(selectedDate)
                            updateCurrentMonthHeader()
                            centerDayPill(selectedDate, smooth = true)
                        }
                    }
                }
            }
        })
    }

    private fun buildTopCalendarPills(
        events: List<EventFull> = synchronized(rawEvents) { rawEvents.toList() },
        changes: List<LessonChangesEvent> = synchronized(rawLessonChanges) { rawLessonChanges.toList() },
        absences: List<TeacherAbsenceEvent> = synchronized(rawTeacherAbsences) { rawTeacherAbsences.toList() }
    ) {
        val daysList = mutableListOf<WeekDayPill>()
        val startCal = getSchoolYearStart().asCalendar
        val endCal = getSchoolYearEnd().asCalendar
        val dayNames = arrayOf("ND", "PN", "WT", "ŚR", "CZ", "PT", "SB")

        while (startCal <= endCal) {
            val d = Date.fromCalendar(startCal)
            val dayOfWeek = startCal.get(Calendar.DAY_OF_WEEK) - 1
            val dayNameStr = dayNames[dayOfWeek % 7]
            val dayNumberStr = startCal.get(Calendar.DAY_OF_MONTH).toString()

            val dayEvents = events.filter { it.date.stringY_m_d == d.stringY_m_d }
            val dayChanges = changes.filter { it.date.stringY_m_d == d.stringY_m_d }
            val dayAbsences = absences.filter { it.date.stringY_m_d == d.stringY_m_d }

            val colors = mutableListOf<Int>()
            if (app.profile.config.ui.agendaLessonChanges && app.profile.config.ui.agendaLessonChangesDots && dayChanges.isNotEmpty()) {
                colors.add(app.eventManager.getLessonChangesColor())
            }
            if (app.profile.config.ui.agendaTeacherAbsence && app.profile.config.ui.agendaTeacherAbsenceDots && dayAbsences.isNotEmpty()) {
                colors.add(app.eventManager.getTeacherAbsenceColor())
            }
            dayEvents.forEach { colors.add(app.eventManager.getEventColor(it)) }

            if (app.profile.config.ui.agendaShowEmptyDays || colors.isNotEmpty() || d.stringY_m_d == selectedDate.stringY_m_d || d.stringY_m_d == Date.getToday().stringY_m_d) {
                daysList.add(WeekDayPill(d, dayNameStr, dayNumberStr, colors))
            }
            startCal.add(Calendar.DAY_OF_YEAR, 1)
        }

        weekDaysAdapter.setDays(daysList, selectedDate)
        centerDayPill(selectedDate, smooth = false)
    }

    private fun buildMonthCalendarGrid(
        events: List<EventFull> = synchronized(rawEvents) { rawEvents.toList() },
        changes: List<LessonChangesEvent> = synchronized(rawLessonChanges) { rawLessonChanges.toList() },
        absences: List<TeacherAbsenceEvent> = synchronized(rawTeacherAbsences) { rawTeacherAbsences.toList() }
    ) {
        val calendarWeeks = app.profile.config.ui.agendaCalendarWeeks
        if (!monthPreviewActive && calendarWeeks in 1..3) {
            buildWeeksCalendarGrid(calendarWeeks, events, changes, absences)
            return
        }
        val daysList = mutableListOf<MonthDayCell>()
        val firstOfMonth = Date(visibleMonth.year, visibleMonth.month, 1)
        val firstCal = firstOfMonth.asCalendar
        val dayOfWeek = (firstCal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // 0 = PN, 6 = ND
        val todayStr = Date.getToday().stringY_m_d

        // 1. Previous month trailing days
        if (dayOfWeek > 0) {
            val prevCal = firstOfMonth.asCalendar.apply { add(Calendar.DAY_OF_MONTH, -dayOfWeek) }
            for (i in 0 until dayOfWeek) {
                val d = Date.fromCalendar(prevCal)
                val dayEvents = events.filter { it.date.stringY_m_d == d.stringY_m_d }
                val dayChanges = changes.filter { it.date.stringY_m_d == d.stringY_m_d }
                val dayAbsences = absences.filter { it.date.stringY_m_d == d.stringY_m_d }

                val colors = mutableListOf<Int>()
                if (app.profile.config.ui.agendaLessonChanges && app.profile.config.ui.agendaLessonChangesDots && dayChanges.isNotEmpty()) colors.add(app.eventManager.getLessonChangesColor())
                if (app.profile.config.ui.agendaTeacherAbsence && app.profile.config.ui.agendaTeacherAbsenceDots && dayAbsences.isNotEmpty()) colors.add(app.eventManager.getTeacherAbsenceColor())
                dayEvents.forEach { colors.add(app.eventManager.getEventColor(it)) }

                daysList.add(MonthDayCell(d, prevCal.get(Calendar.DAY_OF_MONTH).toString(), isCurrentMonth = false, isToday = (d.stringY_m_d == todayStr), eventColors = colors))
                prevCal.add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        // 2. Current month days
        val daysInMonth = firstCal.getActualMaximum(Calendar.DAY_OF_MONTH)

        for (day in 1..daysInMonth) {
            val d = Date(visibleMonth.year, visibleMonth.month, day)
            val dayEvents = events.filter { it.date.stringY_m_d == d.stringY_m_d }
            val dayChanges = changes.filter { it.date.stringY_m_d == d.stringY_m_d }
            val dayAbsences = absences.filter { it.date.stringY_m_d == d.stringY_m_d }

            val colors = mutableListOf<Int>()
            if (app.profile.config.ui.agendaLessonChanges && app.profile.config.ui.agendaLessonChangesDots && dayChanges.isNotEmpty()) colors.add(app.eventManager.getLessonChangesColor())
            if (app.profile.config.ui.agendaTeacherAbsence && app.profile.config.ui.agendaTeacherAbsenceDots && dayAbsences.isNotEmpty()) colors.add(app.eventManager.getTeacherAbsenceColor())
            dayEvents.forEach { colors.add(app.eventManager.getEventColor(it)) }

            val isToday = d.stringY_m_d == todayStr
            daysList.add(MonthDayCell(d, day.toString(), isCurrentMonth = true, isToday = isToday, eventColors = colors))
        }

        // 3. Next month leading days
        val targetTotal = if (daysList.size > 35) 42 else 35
        val remaining = targetTotal - daysList.size
        if (remaining > 0) {
            val nextCal = Date(visibleMonth.year, visibleMonth.month, daysInMonth).asCalendar.apply { add(Calendar.DAY_OF_MONTH, 1) }
            for (i in 0 until remaining) {
                val d = Date.fromCalendar(nextCal)
                val dayEvents = events.filter { it.date.stringY_m_d == d.stringY_m_d }
                val dayChanges = changes.filter { it.date.stringY_m_d == d.stringY_m_d }
                val dayAbsences = absences.filter { it.date.stringY_m_d == d.stringY_m_d }

                val colors = mutableListOf<Int>()
                if (app.profile.config.ui.agendaLessonChanges && app.profile.config.ui.agendaLessonChangesDots && dayChanges.isNotEmpty()) colors.add(app.eventManager.getLessonChangesColor())
                if (app.profile.config.ui.agendaTeacherAbsence && app.profile.config.ui.agendaTeacherAbsenceDots && dayAbsences.isNotEmpty()) colors.add(app.eventManager.getTeacherAbsenceColor())
                dayEvents.forEach { colors.add(app.eventManager.getEventColor(it)) }

                daysList.add(MonthDayCell(d, nextCal.get(Calendar.DAY_OF_MONTH).toString(), isCurrentMonth = false, isToday = (d.stringY_m_d == todayStr), eventColors = colors))
                nextCal.add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        monthCalendarAdapter.setDays(daysList, selectedDate)
    }

    private fun collectMonthCellColors(
        d: Date,
        events: List<EventFull>,
        changes: List<LessonChangesEvent>,
        absences: List<TeacherAbsenceEvent>,
    ): MutableList<Int> {
        val dateStr = d.stringY_m_d
        val dayEvents = events.filter { it.date.stringY_m_d == dateStr }
        val dayChanges = changes.filter { it.date.stringY_m_d == dateStr }
        val dayAbsences = absences.filter { it.date.stringY_m_d == dateStr }

        val colors = mutableListOf<Int>()
        if (app.profile.config.ui.agendaLessonChanges && app.profile.config.ui.agendaLessonChangesDots && dayChanges.isNotEmpty()) colors.add(app.eventManager.getLessonChangesColor())
        if (app.profile.config.ui.agendaTeacherAbsence && app.profile.config.ui.agendaTeacherAbsenceDots && dayAbsences.isNotEmpty()) colors.add(app.eventManager.getTeacherAbsenceColor())
        dayEvents.forEach { colors.add(app.eventManager.getEventColor(it)) }
        return colors
    }

    /**
     * First and last date displayed by [buildWeeksCalendarGrid] for the current
     * [visibleMonth]: Monday-anchored range covering the configured weeks.
     */
    private fun visibleWeeksRange(weeks: Int): Pair<Date, Date> {
        val weekCount = weeks.coerceIn(1, 3)
        val firstWeekOffset = if (weeks == 3) -1 else 0
        val startCal = visibleMonth.asCalendar
        val mondayOffset = (startCal.get(Calendar.DAY_OF_WEEK) + 5) % 7
        startCal.add(Calendar.DAY_OF_MONTH, -mondayOffset + firstWeekOffset * 7)
        val rangeStart = Date.fromCalendar(startCal)
        startCal.add(Calendar.DAY_OF_MONTH, weekCount * 7 - 1)
        val rangeEnd = Date.fromCalendar(startCal)
        return rangeStart to rangeEnd
    }

    /**
     * Builds a limited week-based grid instead of the whole month.
     * Weeks are anchored on the week containing [visibleMonth]:
     * - 3 weeks: previous, current and next week,
     * - 2 weeks: current and next week,
     * - 1 week: current week only.
     */
    private fun buildWeeksCalendarGrid(
        weeks: Int,
        events: List<EventFull>,
        changes: List<LessonChangesEvent>,
        absences: List<TeacherAbsenceEvent>,
    ) {
        val daysList = mutableListOf<MonthDayCell>()
        val todayStr = Date.getToday().stringY_m_d

        // Monday (grid starts on Monday) of the week containing visibleMonth.
        val startCal = visibleMonth.asCalendar
        val mondayOffset = (startCal.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val firstWeekOffset = if (weeks == 3) -1 else 0
        startCal.add(Calendar.DAY_OF_MONTH, -mondayOffset + firstWeekOffset * 7)

        repeat(weeks) {
            repeat(7) {
                val d = Date.fromCalendar(startCal)
                val colors = collectMonthCellColors(d, events, changes, absences)
                val isCurrentMonth = d.month == visibleMonth.month && d.year == visibleMonth.year
                daysList.add(MonthDayCell(
                    d,
                    startCal.get(Calendar.DAY_OF_MONTH).toString(),
                    isCurrentMonth = isCurrentMonth,
                    isToday = (d.stringY_m_d == todayStr),
                    eventColors = colors
                ))
                startCal.add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        monthCalendarAdapter.setDays(daysList, selectedDate)
    }

    private var buildAgendaJob: Job? = null

    private fun buildAgendaList(
        events: List<EventFull> = synchronized(rawEvents) { rawEvents.toList() },
        changes: List<LessonChangesEvent> = synchronized(rawLessonChanges) { rawLessonChanges.toList() },
        absences: List<TeacherAbsenceEvent> = synchronized(rawTeacherAbsences) { rawTeacherAbsences.toList() }
    ) {
        buildAgendaJob?.cancel()
        buildAgendaJob = launch(Dispatchers.Default) {
            val itemsList = mutableListOf<AgendaListItem>()
            val startCal = getSchoolYearStart().asCalendar
            val endCal = getSchoolYearEnd().asCalendar

            val sdfDayHeader = SimpleDateFormat("EEEE, d MMMM", Locale("pl"))

            while (startCal <= endCal) {
                val currDate = Date.fromCalendar(startCal)
                val dateStr = currDate.stringY_m_d

                val dayEvents = events.filter { it.date.stringY_m_d == dateStr }
                val dayChangesList = if (app.profile.config.ui.agendaLessonChanges) changes.filter { it.date.stringY_m_d == dateStr } else emptyList()
                val dayChangesCount = dayChangesList.sumOf { it.count }
                val dayChangesUnread = dayChangesList.any { it.showItemBadge }

                val dayAbsencesList = if (app.profile.config.ui.agendaTeacherAbsence) absences.filter { it.date.stringY_m_d == dateStr } else emptyList()
                val dayAbsenceCount = dayAbsencesList.sumOf { it.count }
                val hasContent = dayEvents.isNotEmpty() || dayChangesCount > 0 || dayAbsenceCount > 0

                if (hasContent || app.profile.config.ui.agendaShowEmptyDays) {
                    val cal = currDate.asCalendar
                    val rawTitle = sdfDayHeader.format(cal.time)
                    val formattedTitle = rawTitle.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("pl")) else it.toString() }

                    itemsList.add(AgendaListItem.DayHeader(currDate, formattedTitle))

                    if (dayChangesCount > 0) {
                        itemsList.add(AgendaListItem.Counter(AgendaListItem.CounterType.LESSON_CHANGES, dayChangesCount, currDate, dayChangesUnread))
                    }
                    if (dayAbsenceCount > 0) {
                        itemsList.add(AgendaListItem.Counter(AgendaListItem.CounterType.TEACHER_ABSENCE, dayAbsenceCount, currDate, false))
                    }

                    if (dayEvents.isNotEmpty()) {
                        if (!app.profile.config.ui.agendaGroupByType) {
                            dayEvents.forEach {
                                itemsList.add(AgendaListItem.Event(it))
                            }
                        } else {
                            dayEvents.groupBy { it.type }.forEach { (typeId, typeEvents) ->
                                if (typeEvents.size == 1) {
                                    itemsList.add(AgendaListItem.Event(typeEvents.first()))
                                } else {
                                    val firstEv = typeEvents.first()
                                    itemsList.add(
                                        AgendaListItem.Group(
                                            AgendaEventGroup(
                                                profileId = firstEv.profileId,
                                                date = firstEv.date,
                                                typeId = typeId,
                                                typeName = firstEv.typeName ?: "wydarzenia",
                                                typeColor = app.eventManager.getEventColor(firstEv),
                                                count = typeEvents.size,
                                                showBadge = typeEvents.any { !it.seen }
                                            )
                                        )
                                    )
                                }
                            }
                        }
                    } else if (!hasContent) {
                        itemsList.add(AgendaListItem.Empty(currDate))
                    }
                }

                startCal.add(Calendar.DAY_OF_YEAR, 1)
            }

            withContext(Dispatchers.Main) {
                agendaAdapter.setItems(itemsList)
                b.progressBar.visibility = View.GONE
                b.eventsRecyclerView.visibility = View.VISIBLE
                scrollToDate(selectedDate)
            }
        }
    }

    private fun centerDayPill(date: Date, smooth: Boolean = true) {
        val selectedPos = weekDaysAdapter.days.indexOfFirst { it.date.stringY_m_d == date.stringY_m_d }
        if (selectedPos >= 0) {
            b.weekDaysRecyclerView.post {
                val lm = b.weekDaysRecyclerView.layoutManager as? LinearLayoutManager ?: return@post
                if (smooth) {
                    val scroller = object : LinearSmoothScroller(requireContext()) {
                        override fun calculateDtToFit(viewStart: Int, viewEnd: Int, boxStart: Int, boxEnd: Int, snapPreference: Int): Int {
                            return (boxStart + (boxEnd - boxStart) / 2) - (viewStart + (viewEnd - viewStart) / 2)
                        }
                    }
                    scroller.targetPosition = selectedPos
                    lm.startSmoothScroll(scroller)
                } else {
                    val rv = b.weekDaysRecyclerView
                    val density = requireContext().resources.displayMetrics.density
                    if (lm.orientation == LinearLayoutManager.VERTICAL) {
                        // landscape: pills scroll top/bottom, center vertically
                        val contentHeight = rv.height - rv.paddingTop - rv.paddingBottom
                        val child = lm.findViewByPosition(selectedPos)
                        val itemHeight = child?.let { lm.getDecoratedMeasuredHeight(it) }
                            ?: (72 * density).toInt()
                        lm.scrollToPositionWithOffset(selectedPos, ((contentHeight - itemHeight) / 2).coerceAtLeast(0))
                        // Correct any remainder (e.g. estimated height for far items).
                        rv.post {
                            val laidOut = lm.findViewByPosition(selectedPos) ?: return@post
                            val contentCenter = rv.paddingTop + (rv.height - rv.paddingTop - rv.paddingBottom) / 2
                            val delta = (laidOut.top + laidOut.bottom) / 2 - contentCenter
                            if (delta != 0) rv.scrollBy(0, delta)
                        }
                    } else {
                        // scrollToPositionWithOffset measures from the padding edge,
                        // so center within the content area, not the whole view.
                        val contentWidth = rv.width - rv.paddingLeft - rv.paddingRight
                        val child = lm.findViewByPosition(selectedPos)
                        val itemWidth = child?.let { lm.getDecoratedMeasuredWidth(it) }
                            ?: (60 * density).toInt()
                        lm.scrollToPositionWithOffset(selectedPos, ((contentWidth - itemWidth) / 2).coerceAtLeast(0))
                        // Correct any remainder (e.g. estimated width for far items).
                        rv.post {
                            val laidOut = lm.findViewByPosition(selectedPos) ?: return@post
                            val contentCenter = rv.paddingLeft + (rv.width - rv.paddingLeft - rv.paddingRight) / 2
                            val delta = (laidOut.left + laidOut.right) / 2 - contentCenter
                            if (delta != 0) rv.scrollBy(delta, 0)
                        }
                    }
                }
            }
        }
    }

    private fun scrollToDate(date: Date) {
        centerDayPill(date, smooth = false)
        var pos = agendaAdapter.getPositionForDate(date)
        if (pos < 0) {
            pos = agendaAdapter.items.indexOfFirst {
                val itemDate = when (it) {
                    is AgendaListItem.DayHeader -> it.date
                    is AgendaListItem.Event -> it.event.date
                    is AgendaListItem.Group -> it.group.date
                    is AgendaListItem.Counter -> it.date
                    is AgendaListItem.Empty -> it.date
                }
                itemDate >= date
            }
        }
        if (pos < 0 && agendaAdapter.items.isNotEmpty()) {
            pos = agendaAdapter.items.size - 1
        }
        if (pos >= 0) {
            (b.eventsRecyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(pos, 0)
        }
    }
}
