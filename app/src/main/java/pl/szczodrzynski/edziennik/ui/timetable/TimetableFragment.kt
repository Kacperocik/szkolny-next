/*
 * Copyright (c) Kacper Ziubryniewicz 2020-1-6
 */

package pl.szczodrzynski.edziennik.ui.timetable

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.asynclayoutinflater.view.AsyncLayoutInflater
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.datepicker.MaterialDatePicker
import com.linkedin.android.tachyon.DayView
import com.linkedin.android.tachyon.DayViewConfig
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import eu.szkolny.font.SzkolnyFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.MainActivity
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.api.edziennik.EdziennikTask
import pl.szczodrzynski.edziennik.data.db.entity.Lesson
import pl.szczodrzynski.edziennik.data.db.full.AttendanceFull
import pl.szczodrzynski.edziennik.data.db.full.EventFull
import pl.szczodrzynski.edziennik.data.db.full.LessonFull
import pl.szczodrzynski.edziennik.data.enums.FeatureType
import pl.szczodrzynski.edziennik.data.enums.MetadataType
import pl.szczodrzynski.edziennik.databinding.FragmentTimetableV2Binding
import pl.szczodrzynski.edziennik.databinding.TimetableLessonBinding
import pl.szczodrzynski.edziennik.ext.Bundle
import pl.szczodrzynski.edziennik.ext.JsonObject
import pl.szczodrzynski.edziennik.ext.applyExpressiveShapeMorphToButtons
import pl.szczodrzynski.edziennik.ext.dp
import pl.szczodrzynski.edziennik.ext.getSchoolYearConstrains
import pl.szczodrzynski.edziennik.ext.getStudentData
import pl.szczodrzynski.edziennik.ext.toDrawable
import pl.szczodrzynski.edziennik.ui.base.fragment.PagerFragment
import pl.szczodrzynski.edziennik.ui.dialogs.settings.TimetableConfigDialog
import pl.szczodrzynski.edziennik.ui.event.EventManualDialog
import pl.szczodrzynski.edziennik.utils.models.Date
import pl.szczodrzynski.edziennik.utils.models.Week
import pl.szczodrzynski.navlib.bottomsheet.items.BottomSheetPrimaryItem
import kotlin.math.max
import kotlin.math.min

class TimetableFragment : PagerFragment<FragmentTimetableV2Binding, MainActivity>(
    inflater = FragmentTimetableV2Binding::inflate,
) {
    companion object {
        private const val TAG = "TimetableFragment"
        const val ACTION_SCROLL_TO_DATE = "pl.szczodrzynski.edziennik.timetable.SCROLL_TO_DATE"
        const val ACTION_RELOAD_PAGES = "pl.szczodrzynski.edziennik.timetable.RELOAD_PAGES"
        const val DEFAULT_START_HOUR = 6
        const val DEFAULT_END_HOUR = 19
        var pageSelection: Date? = null

        /**
         * Minimum timetable width to show the following days next to the selected one.
         */
        private const val MULTI_DAY_MIN_WIDTH_DP = 600
        /**
         * Minimum width of a single day column.
         */
        private const val DAY_MIN_WIDTH_DP = 180
        /**
         * Width of the shared hour labels column (as in fragment_timetable_v2.xml).
         */
        private const val HOUR_LABELS_WIDTH_DP = 50

        private const val PREWARM_LESSON_VIEWS_COUNT = 20
        private const val MAX_LESSON_POOL_SIZE = 48
        private const val MAX_HOUR_LABEL_POOL_SIZE = 96
    }

    data class DayData(
        val lessons: List<LessonFull>,
        val events: List<EventFull>,
        val attendanceList: List<AttendanceFull>,
    )

    override fun getMarkAsReadType() = MetadataType.LESSON_CHANGE
    override fun getBottomSheetItems() = listOf(
        BottomSheetPrimaryItem(true)
            .withTitle(R.string.menu_timetable_sync)
            .withIcon(CommunityMaterial.Icon.cmd_calendar_sync_outline)
            .withOnClickListener {
                activity.bottomSheet.close()
                val date = pageSelection ?: Date.getToday()
                val weekStart = date.weekStart.stringY_m_d
                EdziennikTask.syncProfile(
                    profileId = App.profileId,
                    featureTypes = setOf(FeatureType.TIMETABLE),
                    arguments = JsonObject(
                        "weekStart" to weekStart
                    )
                ).enqueue(activity)
            },
        BottomSheetPrimaryItem(true)
            .withTitle(R.string.timetable_select_day)
            .withIcon(SzkolnyFont.Icon.szf_calendar_today_outline)
            .withOnClickListener { _ ->
                activity.bottomSheet.close()
                val date = pageSelection ?: Date.getToday()
                MaterialDatePicker.Builder.datePicker()
                    .setSelection(date.inMillisUtc)
                    .setCalendarConstraints(app.profile.getSchoolYearConstrains())
                    .build()
                    .apply {
                        addOnPositiveButtonClickListener { millis ->
                            val dateSelected = Date.fromMillisUtc(millis)
                            val index = items.indexOfFirst { it == dateSelected }
                            if (index != -1)
                                b.viewPager.setCurrentItem(index, true)
                        }
                    }
                    .show(activity.supportFragmentManager, TAG)
            },
        BottomSheetPrimaryItem(true)
            .withTitle(R.string.menu_add_event)
            .withDescription(R.string.menu_add_event_desc)
            .withIcon(SzkolnyFont.Icon.szf_calendar_plus_outline)
            .withOnClickListener {
                activity.bottomSheet.close()
                EventManualDialog(
                    activity,
                    App.profileId,
                    defaultDate = items.getOrNull(savedPageSelection) ?: today
                ).show()
            },
        BottomSheetPrimaryItem(true)
            .withTitle(R.string.menu_generate_block_timetable)
            .withDescription(R.string.menu_generate_block_timetable_desc)
            .withIcon(CommunityMaterial.Icon3.cmd_table_large)
            .withOnClickListener {
                activity.bottomSheet.close()
                GenerateBlockTimetableDialog(activity).show()
            },
        BottomSheetPrimaryItem(true)
            .withTitle(R.string.menu_timetable_config)
            .withIcon(CommunityMaterial.Icon.cmd_cog_outline)
            .withOnClickListener {
                activity.bottomSheet.close()
                TimetableConfigDialog(activity, false).show()
            },
        BottomSheetPrimaryItem(true)
            .withTitle(R.string.timetable_fullscreen_mon_fri)
            .withIcon(CommunityMaterial.Icon2.cmd_fullscreen)
            .withOnClickListener {
                activity.bottomSheet.close()
                val date = items.getOrNull(b.viewPager.currentItem) ?: Date.getToday()
                Intent(activity, TimetableFullscreenActivity::class.java).apply {
                    putExtra("date", date.value)
                }.let { startActivity(it) }
            }
    )

    override fun getTabLayout() = b.tabLayout
    override fun getViewPager() = b.viewPager

    override fun getPageCount() = items.size
    override fun getPageFragment(position: Int) = TimetableDayFragment().apply {
        arguments = Bundle(
            "date" to items[position].value,
            "startHour" to if (isMultiDay) multiDayStartHour else startHour,
            "endHour" to if (isMultiDay) multiDayEndHour else endHour,
            "multiDay" to isMultiDay,
        )
    }

    override fun getPageTitle(position: Int): String =
        pageTitles.getOrElse(position) {
            val date = items[position]
            var pageTitle = Week.getFullDayName(date.weekDay)
            if (date > weekEnd || date < weekStart) {
                pageTitle += ", ${date.stringDm}"
            }
            pageTitle
        }

    private var startHour = DEFAULT_START_HOUR
    private var endHour = DEFAULT_END_HOUR
    private val today by lazy { Date.getToday() }
    private val weekStart by lazy { today.weekStart }
    private val weekEnd by lazy { weekStart.clone().stepForward(0, 0, 6) }
    private val items = mutableListOf<Date>()
    private val pageTitles = mutableListOf<String>()

    /**
     * Number of days visible at once - the selected one and the following days.
     */
    private var visibleDays = 1
    private val isMultiDay
        get() = visibleDays > 1
    /**
     * The hour range common for all days, so that the side by side days are aligned.
     */
    private var multiDayStartHour = DEFAULT_START_HOUR
    private var multiDayEndHour = DEFAULT_END_HOUR
    /**
     * The hour range covering all lessons - used if the hours without lessons are shown.
     */
    private var allLessonsStartHour = DEFAULT_START_HOUR
    private var allLessonsEndHour = DEFAULT_END_HOUR
    /**
     * Lesson hours of every day (by [Date.value]), null for days without lessons.
     * Used to trim the hour range to the visible days.
     */
    private val dayHours = mutableMapOf<Int, Pair<Int, Int>?>()
    private var hourLabelsView: DayView? = null
    private var appliedWidth = 0
    private var sharedScrollY = -1
    private var isSyncingScroll = false

    private var pagerScrollState = ViewPager2.SCROLL_STATE_IDLE
    private var pendingHourRangeUpdate = false

    // Shared in-memory cache of day data to avoid per-page DB queries during swipes
    private var isCacheInitialized = false
    private val lessonsByDate = HashMap<Int, List<LessonFull>>()
    private val eventsByDate = HashMap<Int, List<EventFull>>()
    private val attendanceByDate = HashMap<Int, List<AttendanceFull>>()

    // Shared view pools across day pages to avoid inflating XML on the UI thread during swipes
    private val lessonBindingPool = ArrayDeque<TimetableLessonBinding>()
    private val hourLabelPool = ArrayDeque<TextView>()
    private val sharedHourLabels = mutableListOf<TextView>()
    var cachedHourLabelHeight = 0

    fun getCachedDayData(date: Date): DayData? {
        if (!isCacheInitialized) return null
        val d = date.value
        return DayData(
            lessons = lessonsByDate[d] ?: emptyList(),
            events = eventsByDate[d] ?: emptyList(),
            attendanceList = attendanceByDate[d] ?: emptyList(),
        )
    }

    fun acquireLessonBinding(parent: ViewGroup): TimetableLessonBinding {
        while (lessonBindingPool.isNotEmpty()) {
            val binding = lessonBindingPool.removeLast()
            (binding.root.parent as? ViewGroup)?.removeView(binding.root)
            return binding
        }
        return TimetableLessonBinding.inflate(layoutInflater, parent, false)
    }

    fun releaseLessonBindings(bindings: Collection<TimetableLessonBinding>) {
        if (!isAdded || view == null) return
        for (binding in bindings) {
            (binding.root.parent as? ViewGroup)?.removeView(binding.root)
            binding.root.tag = null
            if (lessonBindingPool.size < MAX_LESSON_POOL_SIZE) {
                lessonBindingPool.addLast(binding)
            }
        }
    }

    fun acquireHourLabel(parent: ViewGroup): TextView {
        while (hourLabelPool.isNotEmpty()) {
            val tv = hourLabelPool.removeLast()
            (tv.parent as? ViewGroup)?.removeView(tv)
            return tv
        }
        return layoutInflater.inflate(R.layout.timetable_hour_label, parent, false) as TextView
    }

    fun releaseHourLabels(labels: Collection<TextView>) {
        if (!isAdded || view == null) return
        for (tv in labels) {
            (tv.parent as? ViewGroup)?.removeView(tv)
            if (hourLabelPool.size < MAX_HOUR_LABEL_POOL_SIZE) {
                hourLabelPool.addLast(tv)
            }
        }
    }

    private fun prewarmLessonPool() {
        val ctx = context ?: return
        val asyncInflater = AsyncLayoutInflater(ctx)
        val dummyParent = FrameLayout(ctx)
        val toInflate = (PREWARM_LESSON_VIEWS_COUNT - lessonBindingPool.size).coerceAtLeast(0)
        repeat(toInflate) {
            asyncInflater.inflate(R.layout.timetable_lesson, dummyParent) { inflatedView, _, _ ->
                if (!isAdded || view == null || lessonBindingPool.size >= MAX_LESSON_POOL_SIZE)
                    return@inflate
                lessonBindingPool.addLast(TimetableLessonBinding.bind(inflatedView))
            }
        }
    }

    private fun indexLessonsByDate(allLessons: List<LessonFull>): HashMap<Int, List<LessonFull>> {
        val map = HashMap<Int, MutableList<LessonFull>>()
        for (lesson in allLessons) {
            lesson.filterNotes()
            if (lesson.type != Lesson.TYPE_SHIFTED_SOURCE) {
                lesson.date?.value?.let { d ->
                    map.getOrPut(d) { ArrayList() }.add(lesson)
                }
            }
            if ((lesson.type == Lesson.TYPE_SHIFTED_SOURCE || lesson.type == Lesson.TYPE_CANCELLED) &&
                lesson.oldDate != null &&
                (lesson.type == Lesson.TYPE_SHIFTED_SOURCE || lesson.oldDate != lesson.date)
            ) {
                lesson.oldDate?.value?.let { d ->
                    map.getOrPut(d) { ArrayList() }.add(lesson)
                }
            }
        }
        @Suppress("UNCHECKED_CAST")
        return map as HashMap<Int, List<LessonFull>>
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, i: Intent) {
            if (!isAdded)
                return
            when (i.action) {
                ACTION_SCROLL_TO_DATE -> {
                    val dateStr = i.extras?.getString("timetableDate", null) ?: return
                    val date = Date.fromY_m_d(dateStr)
                    val index = items.indexOf(date)
                    if (index != -1)
                        goToPage(index)
                }

                ACTION_RELOAD_PAGES -> {
                    // e.g. the lesson height or trimming the hours might have changed
                    computeMultiDayHourRange(b.viewPager.currentItem).let { (start, end) ->
                        multiDayStartHour = start
                        multiDayEndHour = end
                    }
                    sharedScrollY = -1
                    cachedHourLabelHeight = 0
                    rebuildHourLabels()
                    reloadPages()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ContextCompat.registerReceiver(
            activity,
            broadcastReceiver,
            IntentFilter(ACTION_SCROLL_TO_DATE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        ContextCompat.registerReceiver(
            activity,
            broadcastReceiver,
            IntentFilter(ACTION_RELOAD_PAGES),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onPause() {
        super.onPause()
        activity.unregisterReceiver(broadcastReceiver)
    }

    override fun onDestroyView() {
        lessonBindingPool.clear()
        hourLabelPool.clear()
        sharedHourLabels.clear()
        cachedHourLabelHeight = 0
        hourLabelsView = null
        super.onDestroyView()
    }

    @SuppressLint("ClickableViewAccessibility")
    override suspend fun onViewReady(savedInstanceState: Bundle?) {
        if (app.profile.getStudentData("timetableNotPublic", false)) {
            b.timetableLayout.visibility = View.GONE
            b.timetableNotPublicLayout.visibility = View.VISIBLE
            return
        }
        b.timetableLayout.visibility = View.VISIBLE
        b.timetableNotPublicLayout.visibility = View.GONE
        b.timetableLayout.applyExpressiveShapeMorphToButtons()
        b.todayButton.icon = SzkolnyFont.Icon.szf_calendar_today_outline.toDrawable(activity)
        b.fullscreenButton.setImageDrawable(
            CommunityMaterial.Icon2.cmd_fullscreen.toDrawable(activity)
        )
        b.todayButton.setOnClickListener {
            b.viewPager.setCurrentItem(items.indexOfFirst { it == today }, true)
        }
        b.fullscreenButton.setOnClickListener {
            val selectedDate = items.getOrNull(b.viewPager.currentItem) ?: today
            startActivity(
                Intent(activity, TimetableFullscreenActivity::class.java).apply {
                    putExtra("weekStart", selectedDate.weekStart.value)
                    putExtra("startHour", startHour)
                    putExtra("endHour", endHour)
                }
            )
        }

        prewarmLessonPool()

        val deferred = async(Dispatchers.Default) {
            items.clear()
            pageTitles.clear()

            val monthDayCount = listOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

            val yearStart = app.profile.dateSemester1Start.clone() ?: return@async
            val yearEnd = app.profile.dateYearEnd
            val wStart = weekStart
            val wEnd = weekEnd
            while (yearStart.value <= yearEnd.value) {
                val date = yearStart.clone()
                items += date
                var title = Week.getFullDayName(date.weekDay)
                if (date > wEnd || date < wStart) {
                    title += ", ${date.stringDm}"
                }
                pageTitles += title

                var maxDays = monthDayCount[yearStart.month - 1]
                if (yearStart.month == 2 && yearStart.isLeap)
                    maxDays++
                yearStart.day++
                if (yearStart.day > maxDays) {
                    yearStart.day = 1
                    yearStart.month++
                }
                if (yearStart.month > 12) {
                    yearStart.month = 1
                    yearStart.year++
                }
            }

            val lessonRanges = app.db.lessonRangeDao().getAllNow(App.profileId)
            startHour = lessonRanges.minOfOrNull { it.startTime.hour } ?: DEFAULT_START_HOUR
            endHour = lessonRanges.maxOfOrNull { it.endTime.hour }?.plus(1) ?: DEFAULT_END_HOUR

            val allLessons = app.db.timetableDao().getAllNow(App.profileId)
            val allEvents = app.db.eventDao().getAllNow(App.profileId)
            val allAttendance = app.db.attendanceDao().getAllNow(App.profileId)

            val indexedLessons = indexLessonsByDate(allLessons)
            val indexedEvents = allEvents.groupByTo(HashMap()) { it.date.value }
            val indexedAttendance = allAttendance.groupByTo(HashMap()) { it.date.value }

            lessonsByDate.clear()
            lessonsByDate.putAll(indexedLessons)
            eventsByDate.clear()
            eventsByDate.putAll(indexedEvents)
            attendanceByDate.clear()
            attendanceByDate.putAll(indexedAttendance)
            isCacheInitialized = true

            // the days shown side by side can't extend their own hour range
            val lessons = allLessons.filter { it.type != Lesson.TYPE_NO_LESSONS }
            allLessonsStartHour = min(startHour, lessons.minOfOrNull { it.displayStartTime?.hour ?: startHour } ?: startHour)
            allLessonsEndHour = max(endHour, lessons.maxOfOrNull { it.displayEndTime?.hour?.plus(1) ?: endHour } ?: endHour)
            dayHours.clear()
            lessons.groupBy { it.displayDate?.value }.forEach { (date, dayLessons) ->
                date ?: return@forEach
                val dayStart = dayLessons.mapNotNull { it.displayStartTime?.hour }.minOrNull() ?: return@forEach
                val dayEnd = dayLessons.mapNotNull { it.displayEndTime?.hour?.plus(1) }.maxOrNull() ?: return@forEach
                dayHours[date] = dayStart to dayEnd
            }
        }
        deferred.await()
        if (!isAdded)
            return

        visibleDays = computeVisibleDays(b.pagerContainer.width)

        val selectedDate = arguments?.getString("timetableDate", "")
            ?.let { if (it.isBlank()) null else Date.fromY_m_d(it) }
        savedPageSelection = items.indexOfFirst { it == (selectedDate ?: today) }
        // before creating the pages, which get the hour range as arguments
        computeMultiDayHourRange(savedPageSelection).let { (start, end) ->
            multiDayStartHour = start
            multiDayEndHour = end
        }

        super.onViewReady(savedInstanceState)

        // the labels only follow the scroll position of the days
        b.hourLabelsScroll.setOnTouchListener { _, _ -> true }
        onPagerWidthChanged(b.pagerContainer.width)
        b.pagerContainer.onWidthChanged = ::onPagerWidthChanged

        observeDatabaseChanges()
    }

    private fun observeDatabaseChanges() {
        var skipInitialLessons = true
        app.db.timetableDao().getAll(App.profileId).observe(viewLifecycleOwner) { allLessons ->
            if (skipInitialLessons) {
                skipInitialLessons = false
                return@observe
            }
            launch {
                val (indexedLessons, indexedAttendance) = withContext(Dispatchers.Default) {
                    val lMap = indexLessonsByDate(allLessons)
                    val aMap = app.db.attendanceDao().getAllNow(App.profileId)
                        .groupByTo(HashMap()) { it.date.value }
                    lMap to aMap
                }
                if (!isAdded) return@launch
                lessonsByDate.clear()
                lessonsByDate.putAll(indexedLessons)
                attendanceByDate.clear()
                attendanceByDate.putAll(indexedAttendance)
                for (fragment in childFragmentManager.fragments) {
                    if (fragment is TimetableDayFragment) {
                        fragment.onCachedDataUpdated()
                    }
                }
            }
        }

        var skipInitialEvents = true
        app.db.eventDao().getAll(App.profileId).observe(viewLifecycleOwner) { allEvents ->
            if (skipInitialEvents) {
                skipInitialEvents = false
                return@observe
            }
            launch {
                val indexedEvents = withContext(Dispatchers.Default) {
                    allEvents.groupByTo(HashMap()) { it.date.value }
                }
                if (!isAdded) return@launch
                eventsByDate.clear()
                eventsByDate.putAll(indexedEvents)
                for (fragment in childFragmentManager.fragments) {
                    if (fragment is TimetableDayFragment) {
                        fragment.onCachedDataUpdated()
                    }
                }
            }
        }
    }

    private fun computeVisibleDays(width: Int): Int {
        val widthDp = width / resources.displayMetrics.density
        if (widthDp < MULTI_DAY_MIN_WIDTH_DP)
            return 1
        return ((widthDp - HOUR_LABELS_WIDTH_DP) / DAY_MIN_WIDTH_DP).toInt().coerceIn(1, 7)
    }

    /**
     * Called before the pager is measured with a new width (e.g. after rotating
     * the screen) - the page size must be updated before the pager lays out
     * its pages, otherwise it would lose the selected day.
     */
    private fun onPagerWidthChanged(width: Int) {
        if (!isAdded || items.isEmpty() || width <= 0 || width == appliedWidth)
            return
        appliedWidth = width
        val position = b.viewPager.currentItem
        val wasMultiDay = isMultiDay
        visibleDays = computeVisibleDays(width)
        applyVisibleDays(width, position)
        if (wasMultiDay != isMultiDay) {
            // the day fragments are configured differently in the multi-day mode
            b.pagerContainer.post {
                if (!isAdded)
                    return@post
                sharedScrollY = -1
                computeMultiDayHourRange(position).let { (start, end) ->
                    multiDayStartHour = start
                    multiDayEndHour = end
                }
                rebuildHourLabels()
                reloadPages()
                if (b.viewPager.currentItem != position)
                    b.viewPager.setCurrentItem(position, false)
                scrollPagerTo(position)
            }
        }
        launch { onPageSelected(position) }
    }

    /**
     * Shows [visibleDays] pages at once, by narrowing the ViewPager2's pages
     * and letting the following pages draw in the remaining space.
     */
    private fun applyVisibleDays(width: Int, position: Int) {
        val recyclerView = b.viewPager.getChildAt(0) as? RecyclerView ?: return
        b.hourLabelsColumn.isVisible = isMultiDay
        if (isMultiDay)
            b.hourLabelsColumn.post { buildHourLabels() }

        val pagerWidth = width - if (isMultiDay) HOUR_LABELS_WIDTH_DP.dp else 0
        val pageWidth = pagerWidth / visibleDays
        recyclerView.clipToPadding = !isMultiDay
        recyclerView.updatePadding(right = pagerWidth - pageWidth)
        b.viewPager.offscreenPageLimit = visibleDays

        // keep the selected day at the start after the page size changed
        recyclerView.post { scrollPagerTo(position) }
    }

    private fun scrollPagerTo(position: Int) {
        val recyclerView = b.viewPager.getChildAt(0) as? RecyclerView ?: return
        (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(position, 0)
    }

    /**
     * Returns the hour range common for the days visible from [position]: covering only
     * their lessons if the hours without lessons are hidden, or all lessons otherwise.
     */
    private fun computeMultiDayHourRange(position: Int): Pair<Int, Int> {
        val allLessons = allLessonsStartHour to allLessonsEndHour
        if (!app.profile.config.ui.timetableTrimHourRange)
            return allLessons
        val ranges = (position until position + visibleDays)
            .mapNotNull { items.getOrNull(it) }
            .mapNotNull { dayHours[it.value] }
        if (ranges.isEmpty())
            return allLessons
        return ranges.minOf { it.first } to ranges.maxOf { it.second }
    }

    private fun requestMultiDayHourRangeUpdate() {
        if (!isAdded || !isMultiDay)
            return
        if (pagerScrollState != ViewPager2.SCROLL_STATE_IDLE) {
            pendingHourRangeUpdate = true
            return
        }
        updateMultiDayHourRange()
    }

    /**
     * Applies the hour range of the currently visible days to the
     * hour labels and all day pages (multi-day mode).
     */
    private fun updateMultiDayHourRange() {
        if (!isAdded || !isMultiDay)
            return
        val (start, end) = computeMultiDayHourRange(b.viewPager.currentItem)
        if (start == multiDayStartHour && end == multiDayEndHour)
            return
        multiDayStartHour = start
        multiDayEndHour = end
        sharedScrollY = -1
        rebuildHourLabels()
        for (fragment in childFragmentManager.fragments) {
            if (fragment is TimetableDayFragment)
                fragment.setHourRange(start, end)
        }
    }

    override fun onPageScrollStateChanged(state: Int) {
        pagerScrollState = state
        if (state == ViewPager2.SCROLL_STATE_IDLE && pendingHourRangeUpdate) {
            pendingHourRangeUpdate = false
            updateMultiDayHourRange()
        }
    }

    /**
     * Called by the day pages after loading their lessons (multi-day mode),
     * to keep the hour range up to date (e.g. after syncing).
     */
    fun onDayHoursLoaded(date: Date, hours: Pair<Int, Int>?) {
        if (dayHours.containsKey(date.value) && dayHours[date.value] == hours)
            return
        dayHours[date.value] = hours
        launch { requestMultiDayHourRangeUpdate() }
    }

    private fun rebuildHourLabels() {
        if (sharedHourLabels.isNotEmpty()) {
            releaseHourLabels(sharedHourLabels)
            sharedHourLabels.clear()
        }
        hourLabelsView?.let { b.hourLabelsFrame.removeView(it) }
        hourLabelsView = null
        buildHourLabels()
    }

    private fun buildHourLabels() {
        if (hourLabelsView != null || !isAdded || !isMultiDay)
            return
        // must match the DayView of TimetableDayFragment in the multi-day mode
        val dayView = DayView(
            activity, DayViewConfig(
                startHour = multiDayStartHour,
                endHour = multiDayEndHour,
                dividerHeight = 1.dp,
                halfHourHeight = app.data.uiConfig.lessonHeight.dp,
                hourDividerColor = Color.TRANSPARENT,
                halfHourDividerColor = Color.TRANSPARENT,
                hourLabelWidth = 40.dp,
                hourLabelMarginEnd = 6.dp,
                eventMargin = 2.dp
            ), true
        )
        dayView.setPadding(4.dp, 10.dp, 0, 10.dp)
        sharedHourLabels.clear()
        dayView.setHourLabelViews((dayView.startHour..dayView.endHour).map { hour ->
            acquireHourLabel(dayView).also {
                it.text = "$hour:00"
                sharedHourLabels.add(it)
            }
        })
        b.hourLabelsFrame.addView(dayView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        hourLabelsView = dayView
        if (sharedScrollY >= 0)
            b.hourLabelsScroll.post { b.hourLabelsScroll.scrollTo(0, sharedScrollY) }
    }

    /**
     * Returns the vertical scroll position shared by the visible days,
     * initializing it with [defaultScrollY] (the first lesson of the day that loaded first).
     */
    fun getSharedScrollY(defaultScrollY: Int): Int {
        if (sharedScrollY < 0) {
            sharedScrollY = defaultScrollY
            b.hourLabelsScroll.post { b.hourLabelsScroll.scrollTo(0, sharedScrollY) }
        }
        return sharedScrollY
    }

    /**
     * Synchronizes the vertical scroll of the visible days and the hour labels.
     */
    fun onDayScrolled(source: TimetableDayFragment, scrollY: Int) {
        if (isSyncingScroll || !isAdded)
            return
        isSyncingScroll = true
        sharedScrollY = scrollY
        b.hourLabelsScroll.scrollTo(0, scrollY)
        for (fragment in childFragmentManager.fragments) {
            if (fragment !== source && fragment is TimetableDayFragment)
                fragment.scrollToSharedPosition(scrollY)
        }
        isSyncingScroll = false
    }

    fun onTimetableScrolled(dy: Int) {
        if (dy > 12) {
            b.todayButton.hide()
            b.fullscreenButton.hide()
        } else if (dy < -12) {
            b.fullscreenButton.show()
            if (!isTodayVisible()) {
                b.todayButton.show()
            }
        }
    }

    private fun isTodayVisible(position: Int = b.viewPager.currentItem): Boolean {
        val todayIndex = items.indexOf(today)
        return todayIndex in position until position + visibleDays
    }

    private var fabShrinkJob: Job? = null

    override suspend fun onPageSelected(position: Int) {
        fabShrinkJob?.cancel()
        pageSelection = items.getOrNull(position)
        // the visible days changed - defer heavy layout changes if currently animating scroll
        requestMultiDayHourRangeUpdate()
        if (isTodayVisible(position)) {
            b.todayButton.hide()
        } else {
            b.todayButton.show()
            b.todayButton.extend()
            fabShrinkJob = launch {
                delay(2500L)
                if (isAdded && !isTodayVisible()) {
                    b.todayButton.shrink()
                }
            }
        }
    }
}
