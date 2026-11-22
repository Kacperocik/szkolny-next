package pl.szczodrzynski.edziennik.ui.timetable

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.core.graphics.ColorUtils
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.db.entity.Lesson
import pl.szczodrzynski.edziennik.data.db.full.LessonFull
import pl.szczodrzynski.edziennik.databinding.TimetableLessonBinding
import pl.szczodrzynski.edziennik.ext.asColoredSpannable
import pl.szczodrzynski.edziennik.ext.asStrikethroughSpannable
import pl.szczodrzynski.edziennik.ext.dp
import pl.szczodrzynski.edziennik.ext.resolveAttr
import pl.szczodrzynski.edziennik.ext.setTintColor

internal fun TimetableLessonBinding.bindLessonAppearance(app: App, lesson: LessonFull, title: CharSequence?) {
    val context = root.context
    val config = app.config[lesson.profileId].ui
    val subjectColor = app.timetableManager.getSubjectColor(lesson, title)

    if (config.timetableColorBorders) {
        lessonCard.setStrokeColor(subjectColor)
        lessonCard.strokeWidth = 2.dp
    } else {
        lessonCard.strokeWidth = 0
    }

    val titleColor = if (config.timetableColorSubjectName) {
        val headerColor = ColorUtils.setAlphaComponent(subjectColor, 255)
        if (annotationVisible) {
            subjectContainer.background = ColorDrawable(headerColor)
        } else {
            subjectContainer.setBackgroundResource(R.drawable.timetable_subject_color_rounded)
            subjectContainer.background.setTintColor(headerColor)
        }
        lessonHeaderTextColor(headerColor)
    } else {
        subjectContainer.background = null
        null
    }

    lessonNumber = lesson.displayLessonNumber
    lessonNumberText.setTextColor(titleColor ?: R.attr.colorOnSurface.resolveAttr(context))
    subjectName.text = title?.let {
        when {
            lesson.type == Lesson.TYPE_CANCELLED || lesson.type == Lesson.TYPE_SHIFTED_SOURCE ->
                it.asStrikethroughSpannable().asColoredSpannable(
                    titleColor ?: android.R.attr.textColorSecondary.resolveAttr(context)
                )
            titleColor != null -> it.asColoredSpannable(titleColor)
            else -> it
        }
    }
}

internal fun lessonHeaderTextColor(backgroundColor: Int): Int =
    if (ColorUtils.calculateContrast(Color.BLACK, backgroundColor) >=
        ColorUtils.calculateContrast(Color.WHITE, backgroundColor)
    ) Color.BLACK else Color.WHITE
