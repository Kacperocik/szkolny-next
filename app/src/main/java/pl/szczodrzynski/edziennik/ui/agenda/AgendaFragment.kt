/*
 * Copyright (c) Kacper Ziubryniewicz 2020-1-25
 */

package pl.szczodrzynski.edziennik.ui.agenda

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import java.util.Calendar
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.viewbinding.ViewBinding
import com.applandeo.materialcalendarview.CalendarDay
import com.applandeo.materialcalendarview.listeners.OnCalendarDayClickListener
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import eu.szkolny.font.SzkolnyFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.szczodrzynski.edziennik.MainActivity
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.db.entity.Profile
import pl.szczodrzynski.edziennik.data.enums.FeatureType
import pl.szczodrzynski.edziennik.data.enums.MetadataType
import pl.szczodrzynski.edziennik.databinding.FragmentAgendaCalendarBinding
import pl.szczodrzynski.edziennik.databinding.FragmentAgendaDefaultBinding
import pl.szczodrzynski.edziennik.ext.dp
import pl.szczodrzynski.edziennik.ext.resolveAttr
import pl.szczodrzynski.edziennik.ext.toDrawable
import pl.szczodrzynski.edziennik.ui.base.fragment.BaseFragment
import pl.szczodrzynski.edziennik.ui.dialogs.settings.AgendaConfigDialog
import pl.szczodrzynski.edziennik.ui.event.EventManualDialog
import pl.szczodrzynski.edziennik.utils.models.Date
import pl.szczodrzynski.navlib.bottomsheet.items.BottomSheetPrimaryItem

class AgendaFragment : BaseFragment<ViewBinding, MainActivity>(
    inflater = null,
) {

    override fun inflate(
        inflater: LayoutInflater,
        parent: ViewGroup?,
        attachToParent: Boolean,
    ) = when (app.profile.config.ui.agendaViewType) {
        Profile.AGENDA_DEFAULT -> FragmentAgendaDefaultBinding.inflate(inflater, parent, false)
        Profile.AGENDA_CALENDAR -> FragmentAgendaCalendarBinding.inflate(inflater, parent, false)
        else -> null
    }

    override fun getFab() = R.string.add to CommunityMaterial.Icon3.cmd_plus
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
                    defaultDate = AgendaFragmentDefault.selectedDate
                ).show()
            },
        BottomSheetPrimaryItem(true)
            .withTitle(R.string.menu_agenda_config)
            .withIcon(CommunityMaterial.Icon.cmd_cog_outline)
            .withOnClickListener {
                activity.bottomSheet.close()
                AgendaConfigDialog(activity, true).show()
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
                activity.reloadTarget()
            },
    )

    private var agendaDefault: AgendaFragmentDefault? = null

    override suspend fun onViewReady(savedInstanceState: Bundle?) {
        when (app.profile.config.ui.agendaViewType) {
            Profile.AGENDA_DEFAULT -> createDefaultAgendaView(
                b as? FragmentAgendaDefaultBinding ?: return
            )
            Profile.AGENDA_CALENDAR -> createCalendarAgendaView(
                b as? FragmentAgendaCalendarBinding ?: return
            )
        }
    }

    override suspend fun onFabClick() {
        EventManualDialog(
            activity,
            app.profileId,
            defaultDate = AgendaFragmentDefault.selectedDate
        ).show()
    }

    private suspend fun checkEventTypes() {
        withContext(Dispatchers.Default) {
            app.db.eventTypeDao().getAllWithDefaults(app.profile)
        }
    }

    private suspend fun createDefaultAgendaView(b: FragmentAgendaDefaultBinding) {
        if (!isAdded)
            return
        canRefreshDisabled = true
        checkEventTypes()
        delay(500)

        agendaDefault = AgendaFragmentDefault(activity, app, b)
        agendaDefault?.initView(this@AgendaFragment)
    }

    private fun createMultipleDotsDrawable(
        context: Context,
        colors: List<Int>,
        dotSizeDp: Int = 6,
        gapDp: Int = 2
    ): Drawable {
        val distinctColors = colors.distinct().take(4)
        if (distinctColors.isEmpty()) return ColorDrawable(Color.TRANSPARENT)

        val density = context.resources.displayMetrics.density
        val dotSizePx = (dotSizeDp * density).toInt()
        val gapPx = (gapDp * density).toInt()

        val totalWidth = distinctColors.size * dotSizePx + (distinctColors.size - 1) * gapPx
        val totalHeight = dotSizePx

        val bitmap = Bitmap.createBitmap(
            totalWidth.coerceAtLeast(1),
            totalHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        var left = 0f
        for (color in distinctColors) {
            paint.color = color
            val radius = dotSizePx / 2f
            canvas.drawCircle(left + radius, radius, radius, paint)
            left += dotSizePx + gapPx
        }

        return BitmapDrawable(context.resources, bitmap)
    }

    private suspend fun createCalendarAgendaView(b: FragmentAgendaCalendarBinding) {
        checkEventTypes()
        delay(300)

        val context = context ?: return
        val colorOnSurface = R.attr.colorOnSurface.resolveAttr(context)
        val colorPrimary = R.attr.colorPrimary.resolveAttr(context)

        val previousButton = b.agendaCalendarView.findViewById<ImageView>(com.applandeo.materialcalendarview.R.id.previousButton)
        val forwardButton = b.agendaCalendarView.findViewById<ImageView>(com.applandeo.materialcalendarview.R.id.forwardButton)

        val typedValue = TypedValue()
        val attrRes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            android.R.attr.selectableItemBackgroundBorderless
        } else {
            R.attr.selectableItemBackground
        }
        context.theme.resolveAttribute(attrRes, typedValue, true)
        if (typedValue.resourceId != 0) {
            previousButton?.setBackgroundResource(typedValue.resourceId)
            forwardButton?.setBackgroundResource(typedValue.resourceId)
        }

        b.agendaCalendarView.setPreviousButtonImage(
            CommunityMaterial.Icon.cmd_chevron_left.toDrawable(context, colorInt = colorOnSurface)
        )
        b.agendaCalendarView.setForwardButtonImage(
            CommunityMaterial.Icon.cmd_chevron_right.toDrawable(context, colorInt = colorOnSurface)
        )

        val calendarDays = mutableListOf<CalendarDay>()

        val todayCalendar = Calendar.getInstance()
        val todayCircle = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setStroke(2.dp, colorPrimary)
        }
        val todayDay = CalendarDay(todayCalendar).apply {
            backgroundDrawable = todayCircle
        }
        calendarDays.add(todayDay)

        val (events, changes, absences) = withContext(Dispatchers.IO) {
            Triple(
                app.db.eventDao().getAllNow(app.profileId),
                app.db.timetableDao().getChangesNow(app.profileId),
                app.db.teacherAbsenceDao().getAllNow(app.profileId)
            )
        }

        val unreadEventDates = mutableSetOf<Int>()
        val dateColorsMap = mutableMapOf<String, Pair<Calendar, MutableList<Int>>>()

        events.forEach { event ->
            if (!event.seen) unreadEventDates.add(event.date.value)
            val key = event.date.stringY_m_d
            val entry = dateColorsMap.getOrPut(key) { event.startTimeCalendar to mutableListOf() }
            entry.second.add(app.eventManager.getEventColor(event))
        }

        if (app.profile.config.ui.agendaLessonChanges && app.profile.config.ui.agendaLessonChangesDots) {
            changes.forEach { change ->
                val date = change.displayDate ?: return@forEach
                val key = date.stringY_m_d
                val entry = dateColorsMap.getOrPut(key) { date.asCalendar to mutableListOf() }
                entry.second.add(app.eventManager.getLessonChangesColor())
            }
        }

        if (app.profile.config.ui.agendaTeacherAbsence && app.profile.config.ui.agendaTeacherAbsenceDots) {
            absences.forEach { absence ->
                val curr = absence.dateFrom.clone()
                while (curr <= absence.dateTo) {
                    val key = curr.stringY_m_d
                    val entry = dateColorsMap.getOrPut(key) { curr.asCalendar to mutableListOf() }
                    entry.second.add(app.eventManager.getTeacherAbsenceColor())
                    curr.stepForward(0, 0, 1)
                }
            }
        }

        dateColorsMap.forEach { (_, pair) ->
            val cal = pair.first
            val colors = pair.second

            val existingDay = calendarDays.find { day ->
                day.calendar.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
                day.calendar.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)
            }

            val dotsIcon = createMultipleDotsDrawable(context, colors)

            if (existingDay != null) {
                existingDay.imageDrawable = dotsIcon
            } else {
                val day = CalendarDay(cal).apply {
                    imageDrawable = dotsIcon
                }
                calendarDays.add(day)
            }
        }

        b.agendaCalendarView.setCalendarDays(calendarDays)
        b.agendaCalendarView.setOnCalendarDayClickListener(object : OnCalendarDayClickListener {
            override fun onClick(day: CalendarDay) {
                this@AgendaFragment.launch {
                    val date = Date.fromCalendar(day.calendar)

                    if (date.value in unreadEventDates) {
                        withContext(Dispatchers.Default) {
                            app.db.eventDao().setSeenByDate(app.profileId, date, true)
                        }
                        unreadEventDates.remove(date.value)
                    }

                    DayDialog(activity, app.profileId, date).show()
                }
            }
        })

        b.progressBar.visibility = View.GONE
    }
}
