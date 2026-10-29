package pl.szczodrzynski.edziennik.ui.timetable

import android.graphics.Typeface
import android.text.TextUtils
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commitNow
import pl.szczodrzynski.edziennik.core.manager.filterTimetableLessons
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.db.entity.Lesson
import pl.szczodrzynski.edziennik.ext.dp
import pl.szczodrzynski.edziennik.ext.resolveAttr
import pl.szczodrzynski.edziennik.utils.models.Date
import pl.szczodrzynski.edziennik.utils.models.Week

class TimetableFullscreenActivity : AppCompatActivity() {

    private val app by lazy { application as App }

    private val containerIds = intArrayOf(
        R.id.fullscreen_timetable_monday,
        R.id.fullscreen_timetable_tuesday,
        R.id.fullscreen_timetable_wednesday,
        R.id.fullscreen_timetable_thursday,
        R.id.fullscreen_timetable_friday,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app.uiManager.applyTheme(this)
        app.uiManager.applyLanguage(this)

        val weekStart = intent.getIntExtra("weekStart", Date.getToday().weekStart.value)
            .let(Date::fromValue)
        var startHour = intent.getIntExtra("startHour", TimetableFragment.DEFAULT_START_HOUR)
        var endHour = intent.getIntExtra("endHour", TimetableFragment.DEFAULT_END_HOUR)
        if (app.profile.config.ui.timetableTrimHourRange) {
            val weekEnd = weekStart.clone().stepForward(0, 0, 4)
            val weekLessons = filterTimetableLessons(
                app.db.timetableDao().getBetweenDatesNow(weekStart, weekEnd),
                app.profile.config.ui.timetableHideClassLessons
            )
                .filter {
                    it.profileId == App.profileId &&
                        it.type != Lesson.TYPE_NO_LESSONS &&
                        it.displayStartTime != null
                }
            if (weekLessons.isNotEmpty()) {
                startHour = weekLessons.minOf { it.displayStartTime!!.hour }
                endHour = weekLessons.maxOf { (it.displayEndTime?.hour ?: it.displayStartTime!!.hour) + 1 }
            }
        }

        val dates = (0..4).map { day ->
            weekStart.clone().stepForward(0, 0, day)
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val daysRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        dates.forEachIndexed { index, date ->
            val columnWidth = if (date.weekDay == Week.MONDAY) 50.dp else 0
            headerRow.addView(createDayHeader(date), LinearLayout.LayoutParams(
                columnWidth,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1f,
            ))
            daysRow.addView(FragmentContainerView(this).apply {
                id = containerIds[index]
            }, LinearLayout.LayoutParams(
                columnWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            ))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(R.attr.colorSurface.resolveAttr(this@TimetableFullscreenActivity))
            addView(headerRow, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(R.dimen.timetable_multi_day_header_height),
            ))
            addView(NestedScrollView(this@TimetableFullscreenActivity).apply {
                isFillViewport = false
                addView(daysRow, ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ))
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ))
        }
        setContentView(root)

        if (savedInstanceState == null) {
            supportFragmentManager.commitNow {
                dates.forEachIndexed { index, date ->
                    replace(
                        containerIds[index],
                        TimetableDayFragment().apply {
                            arguments = Bundle().apply {
                                putInt("date", date.value)
                                putInt("startHour", startHour)
                                putInt("endHour", endHour)
                                putBoolean("weeklyView", true)
                                putBoolean("showHourLabels", index == 0)
                            }
                        },
                        fragmentTag(index),
                    )
                }
            }
        }

        hideSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus)
            hideSystemBars()
    }

    private fun createDayHeader(date: Date) =
        TextView(this).apply {
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            val dayName = Week.getFullDayName(date.weekDay).replaceFirstChar { it.uppercase() }
            text = "$dayName, ${date.stringDm}"
            if (date.weekDay == Week.MONDAY) {
                setPadding(50.dp, 0, 0, 0)
            }
            if (date == Date.getToday()) {
                setTextColor(R.attr.colorPrimary.resolveAttr(this@TimetableFullscreenActivity))
            }
        }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun fragmentTag(index: Int) = "fullscreen_timetable_$index"
}
