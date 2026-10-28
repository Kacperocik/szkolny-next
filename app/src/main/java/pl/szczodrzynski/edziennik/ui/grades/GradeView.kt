/*
 * Copyright (c) Kuba Szczodrzyński 2020-3-1.
 */

package pl.szczodrzynski.edziennik.ui.grades

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue.COMPLEX_UNIT_SP
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.graphics.ColorUtils
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.db.entity.Grade
import pl.szczodrzynski.edziennik.data.db.entity.Grade.Companion.TYPE_SEMESTER1_FINAL
import pl.szczodrzynski.edziennik.data.db.entity.Grade.Companion.TYPE_SEMESTER1_PROPOSED
import pl.szczodrzynski.edziennik.data.db.entity.Grade.Companion.TYPE_SEMESTER2_FINAL
import pl.szczodrzynski.edziennik.data.db.entity.Grade.Companion.TYPE_SEMESTER2_PROPOSED
import pl.szczodrzynski.edziennik.data.db.entity.Grade.Companion.TYPE_YEAR_FINAL
import pl.szczodrzynski.edziennik.data.db.entity.Grade.Companion.TYPE_YEAR_PROPOSED
import pl.szczodrzynski.edziennik.ext.dp
import pl.szczodrzynski.edziennik.ext.resolveAttr
import pl.szczodrzynski.edziennik.ext.setTintColor
import pl.szczodrzynski.edziennik.core.manager.GradesManager

class GradeView : AppCompatTextView {

    @JvmOverloads
    constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(context, attrs, defStyleAttr)

    constructor(context: Context, grade: Grade, manager: GradesManager, periodGradesTextual: Boolean = false) : this(context, null) {
        setGrade(grade, manager, false, periodGradesTextual)
    }

    @SuppressLint("RestrictedApi")
    fun setGrade(grade: Grade?, manager: GradesManager, bigView: Boolean = false, periodGradesTextual: Boolean = false) {
        if (grade == null) {
            visibility = View.GONE
            return
        }
        visibility = View.VISIBLE

        val gradeName = grade.name

        val gradeColor = manager.getGradeColor(context, grade)

        text = when {
            periodGradesTextual -> when (grade.type) {
                TYPE_SEMESTER1_PROPOSED, TYPE_SEMESTER2_PROPOSED -> context.getString(
                        R.string.grade_semester_proposed_format,
                        gradeName
                )
                TYPE_SEMESTER1_FINAL, TYPE_SEMESTER2_FINAL -> context.getString(
                        R.string.grade_semester_final_format,
                        gradeName
                )
                TYPE_YEAR_PROPOSED -> context.getString(
                        R.string.grade_year_proposed_format,
                        gradeName
                )
                TYPE_YEAR_FINAL -> context.getString(
                        R.string.grade_year_final_format,
                        gradeName
                )
                else -> gradeName
            }
            gradeName.isBlank() -> "  "
            else -> gradeName
        }

        setTextColor(when (grade.type) {
            TYPE_SEMESTER1_PROPOSED,
            TYPE_SEMESTER2_PROPOSED,
            TYPE_YEAR_PROPOSED -> android.R.attr.textColorPrimary.resolveAttr(context)
            else -> if (ColorUtils.calculateLuminance(gradeColor) > 0.3)
                0xaa000000.toInt()
            else
                0xccffffff.toInt()
        })

        val isCircle = manager.shapeMode == GradesManager.SHAPE_MODE_CIRCLE

        setBackgroundResource(when (grade.type) {
            TYPE_SEMESTER1_PROPOSED,
            TYPE_SEMESTER2_PROPOSED,
            TYPE_YEAR_PROPOSED -> when {
                isCircle -> R.drawable.bg_circle_outline
                bigView -> R.drawable.bg_rounded_8dp_outline
                else -> R.drawable.bg_rounded_4dp_outline
            }
            else -> when {
                isCircle -> R.drawable.bg_circle
                bigView -> R.drawable.bg_rounded_8dp
                else -> R.drawable.bg_rounded_4dp
            }
        })
        background.setTintColor(gradeColor)
        gravity = Gravity.CENTER

        if (bigView) {
            setTextSize(COMPLEX_UNIT_SP, 24f)
            setAutoSizeTextTypeUniformWithConfiguration(
                14,
                32,
                1,
                COMPLEX_UNIT_SP
            )
            setPadding(2.dp, 2.dp, 2.dp, 2.dp)
        }
        else {
            setTextSize(COMPLEX_UNIT_SP, 14f)
            val isTextualPeriodGrade = periodGradesTextual && grade.type in setOf(
                TYPE_SEMESTER1_PROPOSED,
                TYPE_SEMESTER2_PROPOSED,
                TYPE_SEMESTER1_FINAL,
                TYPE_SEMESTER2_FINAL,
                TYPE_YEAR_PROPOSED,
                TYPE_YEAR_FINAL,
            )
            val useFixedCircleSize = isCircle && !isTextualPeriodGrade
            setPadding(if (useFixedCircleSize) 1.dp else 5.dp, 0, if (useFixedCircleSize) 1.dp else 5.dp, 0)
            val size = if (useFixedCircleSize) 20.dp else WRAP_CONTENT
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                setMargins(0, 0, 5.dp, 0)
            }
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            if (useFixedCircleSize) {
                val exactSize = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)
                measure(exactSize, exactSize)
            } else {
                measure(WRAP_CONTENT, WRAP_CONTENT)
            }
        }
    }

}
