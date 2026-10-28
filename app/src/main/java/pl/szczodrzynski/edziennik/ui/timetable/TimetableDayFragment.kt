/*
 * Copyright (c) Kacper Ziubryniewicz 2020-1-6
 */

package pl.szczodrzynski.edziennik.ui.timetable

import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.asynclayoutinflater.view.AsyncLayoutInflater
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.core.view.marginTop
import androidx.core.view.setPadding
import androidx.core.view.updateLayoutParams
import androidx.core.widget.NestedScrollView
import com.linkedin.android.tachyon.DayView
import com.linkedin.android.tachyon.DayViewConfig
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.MainActivity
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.core.manager.NoteManager
import pl.szczodrzynski.edziennik.data.api.edziennik.EdziennikTask
import pl.szczodrzynski.edziennik.data.db.entity.Lesson
import pl.szczodrzynski.edziennik.data.db.full.AttendanceFull
import pl.szczodrzynski.edziennik.data.db.full.EventFull
import pl.szczodrzynski.edziennik.data.db.full.LessonFull
import pl.szczodrzynski.edziennik.data.enums.FeatureType
import pl.szczodrzynski.edziennik.databinding.TimetableDayFragmentBinding
import pl.szczodrzynski.edziennik.databinding.TimetableLessonBinding
import pl.szczodrzynski.edziennik.databinding.TimetableNoLessonsBinding
import pl.szczodrzynski.edziennik.databinding.TimetableNoTimetableBinding
import pl.szczodrzynski.edziennik.ext.JsonObject
import pl.szczodrzynski.edziennik.ext.applyExpressiveShapeMorphToButtons
import pl.szczodrzynski.edziennik.ext.asColoredSpannable
import pl.szczodrzynski.edziennik.ext.asStrikethroughSpannable
import pl.szczodrzynski.edziennik.ext.concat
import pl.szczodrzynski.edziennik.ext.dp
import pl.szczodrzynski.edziennik.ext.enableExpressiveCardShapeMorph
import pl.szczodrzynski.edziennik.ext.getStudentData
import pl.szczodrzynski.edziennik.ext.listOfNotEmpty
import pl.szczodrzynski.edziennik.ext.onClick
import pl.szczodrzynski.edziennik.ext.resolveAttr
import pl.szczodrzynski.edziennik.ext.resolveDrawable
import pl.szczodrzynski.edziennik.ext.setText
import pl.szczodrzynski.edziennik.ext.setTintColor
import pl.szczodrzynski.edziennik.ext.startCoroutineTimer
import pl.szczodrzynski.edziennik.ext.toDrawable
import pl.szczodrzynski.edziennik.ui.base.fragment.BaseFragment
import pl.szczodrzynski.edziennik.ui.timetable.TimetableFragment.Companion.DEFAULT_END_HOUR
import pl.szczodrzynski.edziennik.ui.timetable.TimetableFragment.Companion.DEFAULT_START_HOUR
import pl.szczodrzynski.edziennik.utils.models.Date
import pl.szczodrzynski.edziennik.utils.models.Time
import pl.szczodrzynski.edziennik.utils.models.Week
import pl.szczodrzynski.edziennik.utils.mutableLazy
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class TimetableDayFragment : BaseFragment<TimetableDayFragmentBinding, AppCompatActivity>(
    inflater = TimetableDayFragmentBinding::inflate,
) {

    private lateinit var inflater: AsyncLayoutInflater

    private var timeIndicatorJob: Job? = null

    private lateinit var date: Date
    private var startHour = DEFAULT_START_HOUR
    private var endHour = DEFAULT_END_HOUR
    private var firstEventMinute = 24 * 60
    private var paddingTop = 0
    private var syncArgs = JsonObject()
    private var weeklyView = false
    private var showHourLabels = true
    /**
     * One of several days shown side by side in [TimetableFragment]: the hour range
     * and vertical scroll are shared, the hour labels are shown by the parent.
     */
    private var multiDay = false
    private var readyView: View? = null

    private var placeholderView: View? = null
    private var placeholderGeneration = 0

    private val activeLessonBindings = mutableListOf<TimetableLessonBinding>()
    private val activeHourLabels = mutableListOf<TextView>()
    private var lastEventTimeRanges = listOf<DayView.EventTimeRange>()

    private val lessonClickListener = View.OnClickListener { v ->
        if (isAdded && v.tag is Pair<*, *>) {
            val (lessonObj, attendanceObj) = v.tag as Pair<*, *>
            LessonDetailsDialog(
                activity = activity,
                lesson = lessonObj as LessonFull,
                attendance = attendanceObj as AttendanceFull?,
            ).show()
        }
    }

    private val manager
        get() = app.timetableManager
    private val attendanceManager
        get() = app.attendanceManager

    private val dayViewDelegate = mutableLazy {
        val dayView = DayView(
            activity, DayViewConfig(
                startHour = startHour,
                endHour = endHour,
                dividerHeight = 1.dp,
                halfHourHeight = app.data.uiConfig.lessonHeight.dp,
                hourDividerColor = R.attr.hourDividerColor.resolveAttr(context),
                halfHourDividerColor = R.attr.halfHourDividerColor.resolveAttr(context),
                hourLabelWidth = if (showHourLabels) 40.dp else 0,
                hourLabelMarginEnd = if (showHourLabels) 10.dp else 0,
                eventMargin = 2.dp
            ), true
        )
        when {
            weeklyView -> dayView.setPadding(4.dp)
            // the vertical padding must match the parent's hour labels column
            multiDay -> dayView.setPadding(4.dp, 10.dp, 4.dp, 10.dp)
            else -> dayView.setPadding(10.dp)
        }
        return@mutableLazy dayView
    }
    private val dayView by dayViewDelegate

    override fun getScrollingView() = b.scrollView
    override fun getSyncParams() = FeatureType.TIMETABLE to syncArgs

    override suspend fun onViewReady(savedInstanceState: Bundle?) {
        // in the multi-day mode this is already called from onViewCreated()
        if (readyView === view)
            return
        readyView = view
        this.inflater = AsyncLayoutInflater(requireContext())

        date = arguments?.getInt("date")?.let { Date.fromValue(it) } ?: Date.getToday()
        startHour = arguments?.getInt("startHour") ?: DEFAULT_START_HOUR
        endHour = arguments?.getInt("endHour") ?: DEFAULT_END_HOUR
        weeklyView = arguments?.getBoolean("weeklyView") == true
        multiDay = arguments?.getBoolean("multiDay") == true
        showHourLabels = !multiDay && arguments?.getBoolean("showHourLabels", true) ?: true
        applyWeeklyLayout()

        syncArgs = JsonObject(
            "weekStart" to date.weekStart.stringY_m_d
        )

        attachScrollListener()

        val parent = timetableFragment
        val cached = parent?.getCachedDayData(date)
        if (cached != null) {
            processLessonList(cached.lessons, cached.events, cached.attendanceList)
            return
        }

        // Fallback when not hosted in TimetableFragment (e.g. TimetableFullscreenActivity)
        app.db.timetableDao().getAllForDate(App.profileId, date).observe(viewLifecycleOwner) { lessons ->
            launch {
                val (events, attendanceList) = withContext(Dispatchers.Default) {
                    lessons.forEach { it.filterNotes() }
                    val ev = app.db.eventDao().getAllByDateNow(App.profileId, date)
                    val att = app.db.attendanceDao().getAllByDateNow(App.profileId, date)
                    ev to att
                }
                processLessonList(lessons, events, attendanceList)
            }
        }
    }

    /**
     * Called by [TimetableFragment] when the underlying database tables change.
     */
    fun onCachedDataUpdated() {
        if (!isAdded || view == null || !::date.isInitialized || !::inflater.isInitialized)
            return
        val cached = timetableFragment?.getCachedDayData(date) ?: return
        if (cached.lessons == lastLessons &&
            cached.events == lastEvents &&
            cached.attendanceList == lastAttendanceList
        ) {
            return
        }
        processLessonList(cached.lessons, cached.events, cached.attendanceList)
    }

    private var lastLessons: List<LessonFull>? = null
    private var lastEvents: List<EventFull> = listOf()
    private var lastAttendanceList: List<AttendanceFull> = listOf()

    /**
     * Sets the hour range shared by the visible days (multi-day mode),
     * rebuilding the day view if it's already shown without re-inflating lesson views.
     */
    fun setHourRange(startHour: Int, endHour: Int) {
        if (startHour == this.startHour && endHour == this.endHour)
            return
        this.startHour = startHour
        this.endHour = endHour
        val lessons = lastLessons ?: return
        if (view == null || !::inflater.isInitialized)
            return
        if (dayViewDelegate.isInitialized() && activeLessonBindings.isNotEmpty() && lastEventTimeRanges.size == activeLessonBindings.size) {
            dayView.removeEventViews()
            val existingEvents = activeLessonBindings.map { it.root }
            releaseHourLabelsToPool()
            b.dayFrame.removeView(dayView)
            dayViewDelegate.deinitialize()
            b.dayFrame.addView(dayView, 0)
            setupHourLabels()
            updateTimeIndicator()
            dayView.setEventViews(existingEvents, lastEventTimeRanges)
            if (multiDay) {
                val firstEventTop = (firstEventMinute - dayView.startHour * 60) * dayView.minuteHeight
                val scrollY = timetableFragment?.getSharedScrollY(firstEventTop.toInt()) ?: 0
                b.scrollView.post { scrollToSharedPosition(scrollY) }
            }
            return
        }
        if (dayViewDelegate.isInitialized()) {
            dayView.removeEventViews()
            releaseHourLabelsToPool()
            b.dayFrame.removeView(dayView)
            dayViewDelegate.deinitialize()
        }
        processLessonList(lessons, lastEvents, lastAttendanceList)
    }

    private fun setupHourLabels() {
        val parent = timetableFragment
        val hourLabelViews = mutableListOf<View>()
        for (i in dayView.startHour..dayView.endHour) {
            val hourLabelView = parent?.acquireHourLabel(dayView)
                ?: (layoutInflater.inflate(R.layout.timetable_hour_label, dayView, false) as TextView)
            hourLabelView.text = if (showHourLabels) "$i:00" else ""
            activeHourLabels.add(hourLabelView)
            hourLabelViews.add(hourLabelView)
        }
        dayView.setHourLabelViews(hourLabelViews)
        // measure dayView top padding needed for the timeIndicator (cached across days)
        val cachedHeight = parent?.cachedHourLabelHeight ?: 0
        if (cachedHeight > 0) {
            paddingTop = cachedHeight / 2 + dayView.paddingTop
        } else {
            hourLabelViews.getOrNull(0)?.let {
                it.measure(0, 0)
                val measured = it.measuredHeight
                if (measured > 0 && parent != null) {
                    parent.cachedHourLabelHeight = measured
                }
                paddingTop = measured / 2 + dayView.paddingTop
            }
        }
    }

    private fun releaseHourLabelsToPool() {
        if (activeHourLabels.isNotEmpty()) {
            timetableFragment?.releaseHourLabels(activeHourLabels) ?: run {
                for (tv in activeHourLabels) {
                    (tv.parent as? ViewGroup)?.removeView(tv)
                }
            }
            activeHourLabels.clear()
        }
    }

    private fun releaseLessonBindingsToPool() {
        if (activeLessonBindings.isNotEmpty()) {
            if (dayViewDelegate.isInitialized()) {
                dayView.removeEventViews()
            }
            timetableFragment?.releaseLessonBindings(activeLessonBindings) ?: run {
                for (lb in activeLessonBindings) {
                    (lb.root.parent as? ViewGroup)?.removeView(lb.root)
                }
            }
            activeLessonBindings.clear()
        }
        lastEventTimeRanges = emptyList()
    }

    private fun processLessonList(
        lessons: List<LessonFull>,
        events: List<EventFull>,
        attendanceList: List<AttendanceFull>,
    ) {
        val generation = ++placeholderGeneration
        lastLessons = lessons
        lastEvents = events
        lastAttendanceList = attendanceList

        val hasLessons = lessons.any { it.type != Lesson.TYPE_NO_LESSONS }
        if (multiDay && !hasLessons)
            timetableFragment?.onDayHoursLoaded(date, null)

        // no lessons - timetable not downloaded yet
        if (lessons.isEmpty()) {
            releaseLessonBindingsToPool()
            releaseHourLabelsToPool()
            inflater.inflate(R.layout.timetable_no_timetable, b.contentFrame) { view, _, _ ->
                if (!isAdded || generation != placeholderGeneration)
                    return@inflate
                showPlaceholder(view)
                val placeholder = TimetableNoTimetableBinding.bind(view)
                val weekStart = date.weekStart.stringY_m_d
                placeholder.noTimetableSync.icon = CommunityMaterial.Icon.cmd_download_outline.toDrawable(requireContext())
                placeholder.noTimetableSync.onClick {
                    it.isEnabled = false
                    EdziennikTask.syncProfile(
                        profileId = App.profileId,
                        featureTypes = setOf(FeatureType.TIMETABLE),
                        arguments = JsonObject(
                            "weekStart" to weekStart
                        )
                    ).enqueue(activity)
                }
                placeholder.noTimetableWeek.setText(R.string.timetable_no_timetable_week, weekStart)
            }
            return
        }
        // one lesson indicating a day without lessons
        if (lessons.size == 1 && lessons[0].type == Lesson.TYPE_NO_LESSONS) {
            releaseLessonBindingsToPool()
            releaseHourLabelsToPool()
            inflater.inflate(R.layout.timetable_no_lessons, b.contentFrame) { view, _, _ ->
                if (!isAdded || generation != placeholderGeneration)
                    return@inflate
                showPlaceholder(view)
                val placeholder = TimetableNoLessonsBinding.bind(view)
                val weekStart = date.weekStart.stringY_m_d
                placeholder.noLessonsSync.onClick {
                    it.isEnabled = false
                    EdziennikTask.syncProfile(
                        profileId = App.profileId,
                        featureTypes = setOf(FeatureType.TIMETABLE),
                        arguments = JsonObject(
                            "weekStart" to weekStart
                        )
                    ).enqueue(activity)
                }
                placeholder.noLessonsSync.isVisible = date.weekDay !in Week.SATURDAY..Week.SUNDAY
            }
            return
        }

        // reload the fragment when: no lessons, user wants to sync the week, the timetable is not public, pager gets removed
        if (app.profile.getStudentData("timetableNotPublic", false)) {
            if (activity is MainActivity)
                (activity as MainActivity).reloadTarget()
            else
                activity.finish()
            // TODO fix for (not really)possible infinite loops
            return
        }

        clearPlaceholder()

        if (dayViewDelegate.isInitialized()) {
            dayView.removeEventViews()
            releaseHourLabelsToPool()
            b.dayFrame.removeView(dayView)
        }

        val lessonsActual = lessons.filter { it.type != Lesson.TYPE_NO_LESSONS }

        val minStartHour = lessonsActual.minOf { it.displayStartTime?.hour ?: DEFAULT_END_HOUR }
        val maxEndHour =
            lessonsActual.maxOf { it.displayEndTime?.hour?.plus(1) ?: DEFAULT_START_HOUR }
        if (multiDay)
            timetableFragment?.onDayHoursLoaded(date, minStartHour to maxEndHour)

        if (!weeklyView && !multiDay && app.profile.config.ui.timetableTrimHourRange) {
            dayViewDelegate.deinitialize()
            // end/start defaults are swapped on purpose
            startHour = minStartHour
            endHour = maxEndHour
        } else if (startHour > minStartHour || endHour < maxEndHour) {
            dayViewDelegate.deinitialize()
            startHour = min(startHour, minStartHour)
            endHour = max(endHour, maxEndHour)
        }

        b.scrollView.isVisible = true
        applyWeeklyLayout()
        b.dayFrame.addView(dayView, 0)

        setupHourLabels()

        lessons.forEach { it.showAsUnseen = !it.seen }

        buildLessonViews(lessonsActual, events, attendanceList)
    }

    private fun showPlaceholder(view: View) {
        placeholderView?.let { b.contentFrame.removeView(it) }
        placeholderView = view
        b.scrollView.isVisible = false
        b.progressBar.isVisible = false
        if (view.parent == null)
            b.contentFrame.addView(view)
        view.applyExpressiveShapeMorphToButtons()
    }

    private fun clearPlaceholder() {
        placeholderView?.let { b.contentFrame.removeView(it) }
        placeholderView = null
    }

    private fun buildLessonViews(
        lessons: List<LessonFull>,
        events: List<EventFull>,
        attendanceList: List<AttendanceFull>,
    ) {
        if (!isAdded)
            return

        val parent = timetableFragment
        val eventViews = ArrayList<View>(lessons.size)
        val eventTimeRanges = ArrayList<DayView.EventTimeRange>(lessons.size)

        val recycledBindings = ArrayList(activeLessonBindings)
        activeLessonBindings.clear()
        var remaining = recycledBindings.size

        val bullet = " • "
        val colorOnSurface = R.attr.colorOnSurface.resolveAttr(activity)
        val colorSecondary = android.R.attr.textColorSecondary.resolveAttr(activity)
        val showAttendance = app.profile.config.ui.timetableShowAttendance
        val showEvents = app.profile.config.ui.timetableShowEvents
        val colorSubjectName = app.profile.config.ui.timetableColorSubjectName
        val colorBorders = app.profile.config.ui.timetableColorBorders

        firstEventMinute = 24 * 60

        for (lesson in lessons) {
            val startTime = lesson.displayStartTime ?: continue
            val endTime = lesson.displayEndTime ?: continue

            val attendance = if (showAttendance)
                attendanceList.find { it.startTime == lesson.startTime }
            else
                null

            firstEventMinute = min(firstEventMinute, startTime.hour * 60 + startTime.minute)

            val lb = if (remaining > 0) {
                recycledBindings[--remaining].also {
                    (it.root.parent as? ViewGroup)?.removeView(it.root)
                }
            } else {
                parent?.acquireLessonBinding(dayView)
                    ?: TimetableLessonBinding.inflate(layoutInflater, dayView, false)
            }
            val eventView = lb.root
            activeLessonBindings += lb
            eventViews += eventView

            eventView.tag = lesson to attendance
            eventView.setOnClickListener(null)
            eventView.isClickable = false
            lb.lessonCard.setOnClickListener { lessonClickListener.onClick(eventView) }
            lb.lessonCard.enableExpressiveCardShapeMorph()

            val eventIcons = arrayOf(lb.event1, lb.event2, lb.event3)
            if (showEvents) {
                var matchedCount = 0
                for (ev in events) {
                    if (ev.time != null && ev.time == startTime) {
                        val iconView = eventIcons[matchedCount]
                        iconView.isVisible = true
                        iconView.background = R.drawable.bg_circle.resolveDrawable(activity)
                            .setTintColor(app.eventManager.getEventColor(ev))
                        matchedCount++
                        if (matchedCount == 3) break
                    }
                }
                for (i in matchedCount until 3) {
                    eventIcons[i].isVisible = false
                }
            } else {
                lb.event1.visibility = View.GONE
                lb.event2.visibility = View.GONE
                lb.event3.visibility = View.GONE
            }

            val timeRange =
                "${startTime.stringHM} - ${endTime.stringHM}".asColoredSpannable(colorSecondary)

            // teacher
            val teacherInfo =
                if (lesson.teacherId != null && lesson.teacherId == lesson.oldTeacherId)
                    lesson.teacherName ?: "?"
                else
                    lesson.teacherName
                    ?: lesson.oldTeacherName?.asStrikethroughSpannable()
                    ?: ""

            // team
            val teamInfo = if (lesson.teamId != null && lesson.teamId == lesson.oldTeamId)
                lesson.teamName ?: "?"
            else
                lesson.teamName
                ?: lesson.oldTeamName?.asStrikethroughSpannable()
                ?: ""

            // classroom
            val classroomInfo =
                if (lesson.classroom != null && lesson.classroom == lesson.oldClassroom)
                    lesson.classroom ?: "?"
                else
                    lesson.classroom
                    ?: lesson.oldClassroom?.asStrikethroughSpannable()
                    ?: ""

            lb.annotationVisible = manager.getAnnotation(activity, lesson, lb.annotation)
            if (weeklyView && lesson.type == Lesson.TYPE_CHANGE) {
                lb.annotation.text = lb.annotation.text.take(1)
            }

            val lessonText =
                lesson.getNoteSubstituteText(showNotes = true) ?: lesson.displaySubjectName

            val subjectColor = lesson.color
                ?: manager.getSubjectColor(lesson.displaySubjectId, lessonText?.toString())
            val (subjectTextPrimary, subjectTextSecondary) = if (colorSubjectName) {
                if (lb.annotationVisible) {
                    lb.subjectContainer.background = ColorDrawable(subjectColor)
                } else {
                    lb.subjectContainer.setBackgroundResource(R.drawable.timetable_subject_color_rounded)
                    lb.subjectContainer.background.setTintColor(subjectColor)
                }
                when (ColorUtils.calculateLuminance(subjectColor) > 0.5) {
                    true -> /* light */ 0xFF000000 to 0xFF666666
                    false -> /* dark */ 0xFFFFFFFF to 0xFFAAAAAA
                }
            } else {
                lb.subjectContainer.background = null
                null to colorSecondary
            }

            if (colorBorders) {
                lb.lessonCard.setStrokeColor(subjectColor)
                lb.lessonCard.strokeWidth = 2.dp
            } else {
                lb.lessonCard.strokeWidth = 0
            }

            lb.lessonNumber = lesson.displayLessonNumber
            if (subjectTextPrimary != null)
                lb.lessonNumberText.setTextColor(subjectTextPrimary.toInt())
            else
                lb.lessonNumberText.setTextColor(colorOnSurface)
            lb.subjectName.text = lessonText?.let {
                if (lesson.type == Lesson.TYPE_CANCELLED || lesson.type == Lesson.TYPE_SHIFTED_SOURCE)
                    it.asStrikethroughSpannable().asColoredSpannable(subjectTextSecondary.toInt())
                else if (subjectTextPrimary != null)
                    it.asColoredSpannable(subjectTextPrimary.toInt())
                else
                    it
            }
            lb.detailsFirst.text = listOfNotEmpty(classroomInfo, timeRange).concat(bullet)
            lb.detailsSecond.text = listOfNotEmpty(teacherInfo, teamInfo).concat(bullet)

            NoteManager.prependIcon(lesson, lb.subjectName)

            lb.attendanceIcon.isVisible = attendance?.let {
                val icon = attendanceManager.getAttendanceIcon(it) ?: return@let false
                val color = attendanceManager.getAttendanceColor(it)
                lb.attendanceIcon.setImageDrawable(icon.toDrawable(color))
                true
            } ?: false

            lb.unread = lesson.type != Lesson.TYPE_NORMAL && lesson.showAsUnseen
            if (!lesson.seen) {
                manager.markAsSeen(lesson)
            }

            lb.executePendingBindings()
            lb.subjectName.ensureSubjectNameFitsWidth()

            // The day view needs the event time ranges in the start minute/end minute format,
            // so calculate those here
            val startMinute = 60 * startTime.hour + startTime.minute
            val endMinute = 60 * endTime.hour + endTime.minute
            eventTimeRanges.add(DayView.EventTimeRange(startMinute, endMinute))
        }

        if (remaining > 0) {
            val unused = recycledBindings.subList(0, remaining)
            parent?.releaseLessonBindings(unused) ?: run {
                for (b in unused) {
                    (b.root.parent as? ViewGroup)?.removeView(b.root)
                }
            }
        }

        lastEventTimeRanges = eventTimeRanges
        updateTimeIndicator()

        dayView.setEventViews(eventViews, eventTimeRanges)
        if (multiDay) {
            val firstEventTop = (firstEventMinute - dayView.startHour * 60) * dayView.minuteHeight
            val scrollY = parent?.getSharedScrollY(firstEventTop.toInt()) ?: 0
            b.scrollView.post { scrollToSharedPosition(scrollY) }
        } else if (!weeklyView) {
            val firstEventTop = (firstEventMinute - dayView.startHour * 60) * dayView.minuteHeight
            b.scrollView.scrollTo(0, firstEventTop.toInt())
        }

        b.progressBar.isVisible = false
    }

    private fun updateTimeIndicator() {
        val time = Time.getNow()
        val isTimeInView =
            date == Date.getToday() && time.hour in dayView.startHour..dayView.endHour

        b.timeIndicator.isVisible = isTimeInView
        b.timeIndicatorMarker.isVisible = isTimeInView
        if (isTimeInView) {
            val startTime = Time(dayView.startHour, 0, 0)
            val seconds = time.inSeconds - startTime.inSeconds * 1f
            b.timeIndicator.updateLayoutParams<FrameLayout.LayoutParams> {
                topMargin = (seconds * dayView.minuteHeight / 60f).toInt() + paddingTop
            }
            b.timeIndicatorMarker.updateLayoutParams<FrameLayout.LayoutParams> {
                topMargin = b.timeIndicator.marginTop - (16.dp / 2) + (1.dp / 2)
            }
        }

        if (timeIndicatorJob == null) {
            timeIndicatorJob = startCoroutineTimer(repeatMillis = 30000) {
                updateTimeIndicator()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        weeklyView = arguments?.getBoolean("weeklyView") == true
        multiDay = arguments?.getBoolean("multiDay") == true
        applyWeeklyLayout()
        if (multiDay) {
            showDayHeader()
        }
        // ViewPager2 does not resume offscreen pages until settled, and BaseFragment.onResume
        // adds a 100ms delay - initialize the day view right away
        launch { onViewReady(savedInstanceState) }
    }

    override fun onResume() {
        super.onResume()
        // BaseFragment replaces the scroll listener when the page gets resumed,
        // which in the multi-day mode happens after onViewReady()
        if (readyView != null && readyView === view)
            attachScrollListener()
        if (!::date.isInitialized || weeklyView || !dayViewDelegate.isInitialized())
            return
        if (!multiDay) {
            val firstEventTop = (firstEventMinute - dayView.startHour * 60) * dayView.minuteHeight
            b.scrollView.scrollTo(0, firstEventTop.toInt())
        }
        updateTimeIndicator()
    }

    override fun onDestroyView() {
        timeIndicatorJob?.cancel()
        timeIndicatorJob = null
        releaseLessonBindingsToPool()
        releaseHourLabelsToPool()
        if (dayViewDelegate.isInitialized()) {
            dayViewDelegate.deinitialize()
        }
        placeholderView = null
        readyView = null
        super.onDestroyView()
    }

    private val timetableFragment
        get() = parentFragment as? TimetableFragment

    private fun showDayHeader() {
        val date = arguments?.getInt("date")?.let { Date.fromValue(it) } ?: return
        val dayName = Week.getFullDayName(date.weekDay).replaceFirstChar { it.uppercase() }
        b.dayHeader.text = "$dayName, ${date.stringDm}"
        if (date == Date.getToday())
            b.dayHeader.setTextColor(R.attr.colorPrimary.resolveAttr(activity))
        else
            b.dayHeader.setTextColor(R.attr.colorOnSurface.resolveAttr(activity))
        b.dayHeader.isVisible = true
    }

    /**
     * Scrolls to the vertical position shared by all visible days (multi-day mode).
     */
    fun scrollToSharedPosition(scrollY: Int) {
        if (view == null || b.scrollView.scrollY == scrollY)
            return
        b.scrollView.scrollTo(0, scrollY)
    }

    override fun onPause() {
        super.onPause()
        timeIndicatorJob?.cancel()
        timeIndicatorJob = null
    }

    private fun applyWeeklyLayout() {
        if (!weeklyView || view == null) return
        b.root.layoutParams = (b.root.layoutParams ?: ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )).apply {
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        }
        b.contentFrame.updateLayoutParams {
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        }
        b.scrollView.updateLayoutParams {
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        }
        b.scrollView.isFillViewport = false
        b.scrollView.isNestedScrollingEnabled = false
    }

    /**
     * The subject name used to rely on TextView autosize. That measures against the view's
     * own height, and the height is wrap_content, so once a narrow landscape column shrinks
     * the text it never grows back when the same card becomes wider in portrait.
     * Size is chosen from the available width instead, between 12sp and 16sp.
     */
    private fun TextView.ensureSubjectNameFitsWidth() {
        if (getTag(R.id.subjectName) != true) {
            setTag(R.id.subjectName, true)
            addOnLayoutChangeListener { v, left, _, right, _, oldLeft, _, oldRight, _ ->
                if (right - left == oldRight - oldLeft) return@addOnLayoutChangeListener
                (v as TextView).fitSubjectNameToWidth()
            }
        }
        fitSubjectNameToWidth()
    }

    private fun TextView.fitSubjectNameToWidth() {
        val availableWidth = width - totalPaddingLeft - totalPaddingRight
        if (availableWidth <= 0) return
        val maxPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 16f, resources.displayMetrics)
        val minPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)
        val best = if (subjectNameFits(maxPx, availableWidth)) {
            maxPx
        } else {
            var low = minPx.toInt()
            var high = maxPx.toInt()
            var fitted = low
            while (low <= high) {
                val mid = (low + high) / 2
                if (subjectNameFits(mid.toFloat(), availableWidth)) {
                    fitted = mid
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }
            fitted.toFloat()
        }
        if (abs(textSize - best) > 0.5f)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, best)
    }

    private fun TextView.subjectNameFits(sizePx: Float, availableWidth: Int): Boolean {
        if (text.isEmpty()) return true
        val layout = buildSubjectLayout(sizePx, availableWidth)
        val lines = if (maxLines > 0) maxLines else Int.MAX_VALUE
        return layout.lineCount <= lines
    }

    private fun TextView.buildSubjectLayout(sizePx: Float, availableWidth: Int): StaticLayout {
        val layoutPaint = TextPaint(paint)
        layoutPaint.textSize = sizePx
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, layoutPaint, availableWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(includeFontPadding)
                .setLineSpacing(lineSpacingExtra, lineSpacingMultiplier)
                .setBreakStrategy(breakStrategy)
                .setHyphenationFrequency(hyphenationFrequency)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(
                text,
                layoutPaint,
                availableWidth,
                Layout.Alignment.ALIGN_NORMAL,
                lineSpacingMultiplier,
                lineSpacingExtra,
                includeFontPadding,
            )
        }
    }

    private fun attachScrollListener() {
        if (weeklyView || view == null) return
        b.scrollView.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            timetableFragment?.onTimetableScrolled(scrollY - oldScrollY)
            if (multiDay)
                timetableFragment?.onDayScrolled(this, scrollY)
        })
    }
}
