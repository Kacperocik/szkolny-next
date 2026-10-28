/*
 * Copyright (c) Kuba Szczodrzyński 2020-2-29.
 */

package pl.szczodrzynski.edziennik.ui.grades.viewholder

import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.get
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.databinding.GradesItemSubjectBinding
import pl.szczodrzynski.edziennik.ext.*
import pl.szczodrzynski.edziennik.ui.grades.GradeView
import pl.szczodrzynski.edziennik.ui.grades.GradesAdapter
import pl.szczodrzynski.edziennik.ui.grades.GradesAdapter.Companion.STATE_CLOSED
import pl.szczodrzynski.edziennik.ui.grades.models.GradesSubject

class SubjectViewHolder(
    inflater: LayoutInflater,
    parent: ViewGroup,
    val b: GradesItemSubjectBinding = GradesItemSubjectBinding.inflate(inflater, parent, false)
) : RecyclerView.ViewHolder(b.root), BindableViewHolder<GradesSubject, GradesAdapter> {
    companion object {
        private const val TAG = "SubjectViewHolder"
    }

    override fun onBind(activity: AppCompatActivity, app: App, item: GradesSubject, position: Int, adapter: GradesAdapter) {
        val manager = app.gradesManager

        if (!item.isUnknown) {
            b.subjectName.text = item.subjectName
        }
        else {
            b.subjectName.text = R.string.grades_subject_unknown.resolveString(activity).asItalicSpannable()
        }
        b.dropdownIcon.rotation = when (item.state) {
            STATE_CLOSED -> 0f
            else -> 180f
        }
        b.contentContainer.updatePadding(
            bottom = if (item.state == STATE_CLOSED) 8.dp else 0,
        )

        b.unread.isVisible = item.hasUnseen

        b.previewContainer.visibility = if (item.state == STATE_CLOSED) View.VISIBLE else View.INVISIBLE
        b.yearSummary.visibility = if (item.state == STATE_CLOSED) View.INVISIBLE else View.VISIBLE

        val gradesContainer = b.previewContainer[0]
        b.previewContainer.removeAllViews()
        b.gradesContainer.removeAllViews()
        b.previewContainer.addView(gradesContainer)

        val firstSemester = item.semesters.firstOrNull() ?: return

        if (manager.isUniversity) {
            val ectsPoints = item.semesters.firstOrNull()?.grades?.maxOf { it.weight }
            b.yearSummary.text = if (ectsPoints != null)
                activity.getString(
                    R.string.grades_ects_points_format,
                    ectsPoints
                )
            else
                null
        } else {
            b.yearSummary.text = if (adapter.subjectHasPointGrades(item.subjectId))
                manager.getYearSummaryString(
                    app,
                    item.semesters.map { it.grades.size }.sum(),
                    item.averages
                )
            else adapter.simulatorAvgText(
                "subj:${item.subjectId}",
                manager.getYearSummaryString(
                    app,
                    item.semesters.map { it.grades.size }.sum(),
                    item.averages
                ),
                stripLabel = true
            )
        }

        if (firstSemester.number != item.semester && !manager.isUniversity) {
            b.gradesContainer.addView(TextView(activity).apply {
                setTextColor(android.R.attr.textColorSecondary.resolveAttr(context))
                setText(R.string.grades_preview_other_semester, firstSemester.number)
                setPadding(0, 0, 5.dp, 0)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
        }

        /*if (firstSemester.grades.isEmpty()) {
            b.previewContainer.addView(TextView(app).apply {
                setText(R.string.grades_no_grades_in_semester, firstSemester.number)
            })
        }*/

        val hideImproved = manager.hideImproved
        for (grade in firstSemester.grades) {
            if (hideImproved && grade.isImproved)
                continue
            b.gradesContainer.addView(GradeView(
                activity,
                grade,
                manager,
                periodGradesTextual = false
            ))
        }

        if (!manager.isUniversity) {
            val pointSubject = adapter.subjectHasPointGrades(item.subjectId)
            b.previewContainer.addView(TextView(activity).apply {
                setTextColor(android.R.attr.textColorSecondary.resolveAttr(context))
                val liveSemAvg = manager.getAverageString(app, firstSemester.averages, nameSemester = true, showSemester = firstSemester.number)
                text = if (pointSubject) liveSemAvg else adapter.simulatorAvgText(
                    "semNamed:${item.subjectId}:${firstSemester.number}",
                    liveSemAvg,
                    stripLabel = true
                )
                //gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                    setMargins(0, 0, 8.dp, 0)
                }
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
        }

        // add the topmost semester's grades to preview container (collapsed)
        firstSemester.proposedGrade?.let {
            b.previewContainer.addView(GradeView(
                activity,
                it,
                manager
            ))
        }
        firstSemester.finalGrade?.let {
            b.previewContainer.addView(GradeView(
                activity,
                it,
                manager
            ))
        }

        // remove previously added grades from year preview
        if (b.yearContainer.childCount > 1)
            b.yearContainer.removeViews(1, b.yearContainer.childCount - 1)
        // add the yearly grades to summary container (expanded)
        item.proposedGrade?.let {
            b.yearContainer.addView(GradeView(
                activity,
                    it,
                    manager
            ))
        }
        // Only yearSummary is hidden on collapse; yearContainer remains visible.
        // Hide the button separately to prevent overlap with the collapsed preview.
        val simEditable = adapter.simulatorMode && item.state != STATE_CLOSED && !item.isUnknown &&
                !manager.isUniversity && !adapter.subjectHasPointGrades(item.subjectId)
        item.finalGrade?.let {
            b.yearContainer.addView(GradeView(
                activity,
                    it,
                    manager
            ).apply {
                if (simEditable)
                    onClick { v -> adapter.showSimulatorFinalGradePicker(v, item, null) }
            })
        }
        if (simEditable) {
            b.yearContainer.addView(AppCompatImageView(activity).apply {
                setImageDrawable(CommunityMaterial.Icon2.cmd_flag_checkered.toDrawable(
                    activity,
                    colorAttr = android.R.attr.textColorSecondary,
                    sizeDp = 20
                ))
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                val outValue = TypedValue()
                activity.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
                setBackgroundResource(outValue.resourceId)
                contentDescription = activity.getString(R.string.grades_simulator_set_year_final)
                layoutParams = LinearLayout.LayoutParams(24.dp, 24.dp).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setMargins(3.dp, 0, 8.dp, 0)
                }
                onClick { v -> adapter.showSimulatorFinalGradePicker(v, item, null) }
            })
        }

        // if showing semester 2, add yearly grades to preview container (collapsed)
        if (firstSemester.number == item.semester && !manager.isUniversity) {
            b.previewContainer.addView(TextView(activity).apply {
                val liveYearAvg = manager.getAverageString(app, item.averages, nameSemester = true)
                text = if (adapter.subjectHasPointGrades(item.subjectId)) liveYearAvg else adapter.simulatorAvgText(
                    "subjNamed:${item.subjectId}",
                    liveYearAvg,
                    stripLabel = true
                )
                layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                    setMargins(0, 0, 8.dp, 0)
                }
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })

            item.proposedGrade?.let {
                b.previewContainer.addView(GradeView(
                    activity,
                    it,
                    manager
                ))
            }
            item.finalGrade?.let {
                b.previewContainer.addView(GradeView(
                    activity,
                    it,
                    manager
                ))
            }
        }
    }
}
