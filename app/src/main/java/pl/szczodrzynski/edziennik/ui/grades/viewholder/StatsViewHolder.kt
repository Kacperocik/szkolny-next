/*
 * Copyright (c) Kuba Szczodrzyński 2020-3-3.
 */

package pl.szczodrzynski.edziennik.ui.grades.viewholder

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.flexbox.FlexboxLayout
import pl.szczodrzynski.edziennik.ext.dp
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.databinding.GradesItemStatsBinding
import pl.szczodrzynski.edziennik.ext.applyExpressiveShapeMorphToButtons
import pl.szczodrzynski.edziennik.ext.onClick
import pl.szczodrzynski.edziennik.ui.base.dialog.SimpleDialog
import pl.szczodrzynski.edziennik.ui.dialogs.settings.GradesConfigDialog
import pl.szczodrzynski.edziennik.ui.grades.GradesAdapter
import pl.szczodrzynski.edziennik.ui.grades.models.GradesStats
import java.text.DecimalFormat

class StatsViewHolder(
    inflater: LayoutInflater,
    parent: ViewGroup,
    val b: GradesItemStatsBinding = GradesItemStatsBinding.inflate(inflater, parent, false)
) : RecyclerView.ViewHolder(b.root), BindableViewHolder<GradesStats, GradesAdapter> {
    companion object {
        private const val TAG = "StatsViewHolder"
    }

    override fun onBind(activity: AppCompatActivity, app: App, item: GradesStats, position: Int, adapter: GradesAdapter) {
        val manager = app.gradesManager
        val isUniversity = manager.isUniversity
        val showAverages = mutableListOf<Int>()
        val showPoint = mutableListOf<Int>()

        b.universityTitle.isVisible = isUniversity
        b.universityLayout.isVisible = isUniversity
        b.universityDivider.isVisible = isUniversity

        if (isUniversity) {
            val format = DecimalFormat("#.00")

            b.normalTitle.isVisible = false
            b.normalLayout.isVisible = false
            b.normalDivider.isVisible = false
            b.helpButton.isVisible = false
            b.pointTitle.isVisible = false
            b.pointLayout.isVisible = false
            b.pointDivider.isVisible = false
            b.noData.isVisible = false
            b.disclaimer.isVisible = true
            b.customValueDivider.isVisible = false
            b.customValueLayout.isVisible = false

            b.universitySemester.text = format.format(item.universitySem)
            b.universityTotal.text = format.format(item.universityTotal)
            b.universityEcts.text = format.format(item.universityEcts)
            return
        }

        val sim = if (adapter.simulatorMode) adapter.simSnapshot else null
        // Keep average cards visible during simulation, including empty semesters.
        val forceCards = adapter.simulatorMode

        getSemesterString(app, item.normalSem1, item.normalSem1Proposed, item.normalSem1Final, item.sem1NotAllFinal).let { (average, notice) ->
            b.normalSemester1Layout.isVisible = average != null || forceCards
            b.normalSemester1Notice.isVisible = notice != null
            b.normalSemester1.text = average
            b.normalSemester1Notice.text = notice
            if (average != null)
                showAverages += 1
            bindSimulatorAverage(
                app = app,
                averageView = b.normalSemester1,
                noticeView = b.normalSemester1Notice,
                beforeRow = b.normalSemester1BeforeRow,
                beforeValue = b.normalSemester1BeforeValue,
                simRow = b.normalSemester1SimRow,
                simValue = b.normalSemester1SimValue,
                live = Triple(item.normalSem1, item.normalSem1Proposed, item.normalSem1Final) to item.sem1NotAllFinal,
                snap = sim?.sem1
            )
        }
        getSemesterString(app, item.normalSem2, item.normalSem2Proposed, item.normalSem2Final, item.sem2NotAllFinal).let { (average, notice) ->
            b.normalSemester2Layout.isVisible = average != null || forceCards
            b.normalSemester2Notice.isVisible = notice != null
            b.normalSemester2.text = average
            b.normalSemester2Notice.text = notice
            if (average != null)
                showAverages += 2
            bindSimulatorAverage(
                app = app,
                averageView = b.normalSemester2,
                noticeView = b.normalSemester2Notice,
                beforeRow = b.normalSemester2BeforeRow,
                beforeValue = b.normalSemester2BeforeValue,
                simRow = b.normalSemester2SimRow,
                simValue = b.normalSemester2SimValue,
                live = Triple(item.normalSem2, item.normalSem2Proposed, item.normalSem2Final) to item.sem2NotAllFinal,
                snap = sim?.sem2
            )
        }
        getSemesterString(app, item.normalYearly, item.normalYearlyProposed, item.normalYearlyFinal, item.yearlyNotAllFinal).let { (average, notice) ->
            b.normalYearlyLayout.isVisible = average != null || forceCards
            b.normalYearlyNotice.isVisible = notice != null
            b.normalYearly.text = average
            b.normalYearlyNotice.text = notice
            if (average != null)
                showAverages += 3
            bindSimulatorAverage(
                app = app,
                averageView = b.normalYearly,
                noticeView = b.normalYearlyNotice,
                beforeRow = b.normalYearlyBeforeRow,
                beforeValue = b.normalYearlyBeforeValue,
                simRow = b.normalYearlySimRow,
                simValue = b.normalYearlySimValue,
                live = Triple(item.normalYearly, item.normalYearlyProposed, item.normalYearlyFinal) to item.yearlyNotAllFinal,
                snap = sim?.yearly
            )
        }

        b.normalTitle.isVisible = showAverages.size > 0 || forceCards
        b.normalLayout.isVisible = showAverages.size > 0 || forceCards
        b.normalDivider.isVisible = showAverages.size > 0 || forceCards
        b.helpButton.isVisible = showAverages.size > 0 || forceCards

        val normalVisibleCount = listOf(
            b.normalSemester1Layout.isVisible,
            b.normalSemester2Layout.isVisible,
            b.normalYearlyLayout.isVisible
        ).count { it }
        (b.normalYearlyLayout.layoutParams as? FlexboxLayout.LayoutParams)?.let {
            val wrapped = normalVisibleCount == 3
            it.flexBasisPercent = if (wrapped) 1f else 0.48f
            it.topMargin = if (wrapped) 8.dp else 0
            b.normalYearlyLayout.requestLayout()
        }
        updateCardGaps(listOf(b.normalSemester1Layout, b.normalSemester2Layout, b.normalYearlyLayout))

        getSemesterString(app, 0f, 0f, item.pointSem1, false).let { (average, _) ->
            b.pointSemester1Layout.isVisible = average != null
            b.pointSemester1.text = average
            if (average != null)
                showPoint += 1
        }
        getSemesterString(app, 0f, 0f, item.pointSem2, false).let { (average, _) ->
            b.pointSemester2Layout.isVisible = average != null
            b.pointSemester2.text = average
            if (average != null)
                showPoint += 2
        }
        getSemesterString(app, 0f, 0f, item.pointYearly, false).let { (average, _) ->
            b.pointYearlyLayout.isVisible = average != null
            b.pointYearly.text = average
            if (average != null)
                showPoint += 3
        }

        b.pointTitle.isVisible = showPoint.size > 0
        b.pointLayout.isVisible = showPoint.size > 0
        b.pointDivider.isVisible = showPoint.size > 0

        val pointVisibleCount = listOf(
            b.pointSemester1Layout.isVisible,
            b.pointSemester2Layout.isVisible,
            b.pointYearlyLayout.isVisible
        ).count { it }
        (b.pointYearlyLayout.layoutParams as? FlexboxLayout.LayoutParams)?.let {
            val wrapped = pointVisibleCount == 3
            it.flexBasisPercent = if (wrapped) 1f else 0.48f
            it.topMargin = if (wrapped) 8.dp else 0
            b.pointYearlyLayout.requestLayout()
        }
        updateCardGaps(listOf(b.pointSemester1Layout, b.pointSemester2Layout, b.pointYearlyLayout))

        b.noData.isVisible = showAverages.isEmpty() && showPoint.isEmpty()
        b.disclaimer.isVisible = !b.noData.isVisible

        b.helpButton.onClick {
            SimpleDialog<Unit>(activity) {
                title(R.string.grades_stats_help_title)
                message(R.string.grades_stats_help_text)
                positive(R.string.ok)
            }.show()
        }

        b.customValueDivider.isVisible = manager.dontCountEnabled || manager.plusValue != null || manager.minusValue != null
        b.customValueLayout.isVisible = b.customValueDivider.isVisible
        b.customValueButton.onClick {
            GradesConfigDialog(activity, reloadOnDismiss = true).show()
        }
        b.customValueButton.applyExpressiveShapeMorphToButtons()
    }

    private fun bindSimulatorAverage(
        app: App,
        averageView: TextView,
        noticeView: TextView,
        beforeRow: View,
        beforeValue: TextView,
        simRow: View,
        simValue: TextView,
        live: Pair<Triple<Float, Float, Float>, Boolean>,
        snap: GradesAdapter.SimulatorStatSnapshot?
    ) {
        if (snap == null) {
            averageView.isVisible = true
            beforeRow.isVisible = false
            simRow.isVisible = false
            return
        }
        val (liveTriple, liveNotAll) = live
        val (liveAverage, liveNotice) = getSimulatorString(app, liveTriple.first, liveTriple.third, liveNotAll)
        val (snapAverage, _) = getSimulatorString(app, snap.expected, snap.final, snap.notAllFinal)
        averageView.isVisible = false
        beforeRow.isVisible = true
        beforeValue.text = snapAverage ?: "–"
        simRow.isVisible = liveAverage != null
        simValue.text = liveAverage
        noticeView.isVisible = liveNotice != null
        noticeView.text = liveNotice
    }

    /**
     * Uses final grades where available and rounded averages for remaining subjects.
     * Averaging final grades alone would reduce the result to a single grade
     * when only one final grade has been set.
     */
    private fun getSimulatorString(context: Context, expected: Float, final: Float, notAllFinal: Boolean): Pair<String?, String?> {
        if (expected == 0f)
            return null to null
        val notice = when {
            final == 0f -> context.getString(R.string.grades_stats_expected)
            notAllFinal -> context.getString(R.string.grades_stats_from_final_and_expected)
            else -> context.getString(R.string.grades_stats_from_final_no_expected)
        }
        return DecimalFormat("#.00").format(expected) to notice
    }

    /**
     * Adds gaps only between cards in the same row.
     * With three visible cards, the yearly card occupies a separate row.
     */
    private fun updateCardGaps(cards: List<View>) {
        val visible = cards.filter { it.isVisible }
        val rowEnds = if (visible.size == 3) setOf(1, 2) else setOf(visible.lastIndex)
        visible.forEachIndexed { index, card ->
            (card.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                it.rightMargin = if (index in rowEnds) 0 else 8.dp
                card.layoutParams = it
            }
        }
    }

    private fun getSemesterString(context: Context, expected: Float, proposed: Float, final: Float, notAllFinal: Boolean) : Pair<String?, String?> {        val format = DecimalFormat("#.00")

        val average = when {
            final != 0f -> final
            proposed != 0f -> proposed
            expected != 0f -> expected
            else -> null
        }?.let {
            format.format(it)
        }

        val notice = when {
            final != 0f -> when {
                notAllFinal -> if (expected != 0f)
                    context.getString(R.string.grades_stats_from_final, format.format(expected))
                else
                    context.getString(R.string.grades_stats_from_final_no_expected)
                proposed != 0f -> context.getString(R.string.grades_stats_proposed_avg, format.format(proposed))
                else -> null
            }
            proposed != 0f -> if (expected != 0f)
                context.getString(R.string.grades_stats_from_proposed, format.format(expected))
            else
                context.getString(R.string.grades_stats_from_proposed_no_expected)
            expected != 0f -> context.getString(R.string.grades_stats_expected)
            else -> null
        }

        return average to notice
    }
}
