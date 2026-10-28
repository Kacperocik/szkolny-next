/*
 * Copyright (c) Kuba Szczodrzyński 2020-2-29.
 */

package pl.szczodrzynski.edziennik.ui.grades

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuPopupHelper
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.ShapeAppearanceModel
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.db.entity.Grade
import pl.szczodrzynski.edziennik.data.db.entity.Grade.Companion.TYPE_NO_GRADE
import pl.szczodrzynski.edziennik.data.db.full.GradeFull
import pl.szczodrzynski.edziennik.ext.dp
import pl.szczodrzynski.edziennik.ext.applyExpressiveShapeMorphToButtons
import pl.szczodrzynski.edziennik.ext.enableExpressiveCardShapeMorph
import pl.szczodrzynski.edziennik.ext.onClick
import pl.szczodrzynski.edziennik.ext.startCoroutineTimer
import pl.szczodrzynski.edziennik.ext.toDrawable
import pl.szczodrzynski.edziennik.ui.base.dialog.SimpleDialog
import pl.szczodrzynski.edziennik.ui.grades.models.*
import pl.szczodrzynski.edziennik.ui.grades.viewholder.*
import timber.log.Timber
import java.text.DecimalFormat
import kotlin.coroutines.CoroutineContext

class GradesAdapter(
        val activity: AppCompatActivity,
        val showNotes: Boolean = true,
        var onGradeClick: ((item: GradeFull) -> Unit)? = null,
        var onSimulatorToggle: ((enabled: Boolean) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), CoroutineScope {
    companion object {
        private const val TAG = "GradesAdapter"
        private const val ITEM_TYPE_SUBJECT = 0
        private const val ITEM_TYPE_SEMESTER = 1
        private const val ITEM_TYPE_EMPTY = 2
        private const val ITEM_TYPE_GRADE = 3
        private const val ITEM_TYPE_STATS = 4
        private const val ITEM_TYPE_UNKNOWN_SUBJECT = 5
        private const val ITEM_TYPE_SIMULATOR = 6
        const val STATE_CLOSED = 0
        const val STATE_OPENED = 1

        private val SIM_GRADE_NAMES = listOf(
            "1-", "1", "1+", "2-", "2", "2+", "3-", "3", "3+",
            "4-", "4", "4+", "5-", "5", "5+", "6-", "6", "6+", "0"
        )

        private val SIM_FINAL_GRADE_NAMES = listOf("1", "2", "3", "4", "5", "6")
        private const val SIM_FINAL_REMOVE_ID = 100
    }

    /** Enables grade editing through simulator controls instead of grade details. */
    var simulatorMode = false

    /** Statistics captured before simulation, used for before-and-after comparisons. */
    var simSnapshot: SimulatorSnapshot? = null

    private var simGradeId = -1L

    class SimulatorStatSnapshot(
        val expected: Float,
        val proposed: Float,
        val final: Float,
        val notAllFinal: Boolean
    )

    class SimulatorSnapshot(
        val sem1: SimulatorStatSnapshot,
        val sem2: SimulatorStatSnapshot,
        val yearly: SimulatorStatSnapshot
    )

    private val app = activity.applicationContext as App
    private val manager
        get() = app.gradesManager

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    var items = mutableListOf<Any>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            ITEM_TYPE_SUBJECT -> SubjectViewHolder(inflater, parent)
            ITEM_TYPE_SEMESTER -> SemesterViewHolder(inflater, parent)
            ITEM_TYPE_EMPTY -> EmptyViewHolder(inflater, parent)
            ITEM_TYPE_GRADE -> GradeViewHolder(inflater, parent)
            ITEM_TYPE_STATS -> StatsViewHolder(inflater, parent)
            ITEM_TYPE_UNKNOWN_SUBJECT -> UnknownSubjectViewHolder(inflater, parent)
            ITEM_TYPE_SIMULATOR -> SimulatorToggleViewHolder(inflater.inflate(R.layout.grades_item_simulator_toggle, parent, false))
            else -> throw IllegalArgumentException("Incorrect viewType")
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is GradesSubject -> ITEM_TYPE_SUBJECT
            is GradesSemester -> ITEM_TYPE_SEMESTER
            is GradesEmpty -> ITEM_TYPE_EMPTY
            is Grade -> ITEM_TYPE_GRADE
            is GradesStats -> ITEM_TYPE_STATS
            is GradesUnknownSubject -> ITEM_TYPE_UNKNOWN_SUBJECT
            is GradesSimulatorToggle -> ITEM_TYPE_SIMULATOR
            else -> throw IllegalArgumentException("Incorrect viewType")
        }
    }

    private val onClickListener = View.OnClickListener { view ->
        val model = view.getTag(R.string.tag_key_model)
        if (model is GradeFull) {
            if (simulatorMode && !subjectHasPointGrades(model.subjectId)) {
                showSimulatorGradeMenu(view, model)
                return@OnClickListener
            }
            onGradeClick?.invoke(model)
            return@OnClickListener
        }
        if (model !is ExpandableItemModel<*>)
            return@OnClickListener
        expandModel(model, view)
    }

    fun expandModel(model: ExpandableItemModel<*>?, view: View?, notifyAdapter: Boolean = true) {
        model ?: return
        var position = items.indexOf(model)
        if (position == -1)
            return

        if (model is GradesSubject || model is GradesSemester) {
            view?.findViewById<View>(R.id.dropdownIcon)?.let { dropdownIcon ->
                ObjectAnimator.ofFloat(
                        dropdownIcon,
                        View.ROTATION,
                        if (model.state == STATE_CLOSED) 0f else 180f,
                        if (model.state == STATE_CLOSED) 180f else 0f
                ).setDuration(200).start();
            }
        }
        if (model is GradesSubject) {
            // hide the preview, show summary
            val preview = view?.findViewById<View>(R.id.previewContainer)
            val summary = view?.findViewById<View>(R.id.yearSummary)
            preview?.visibility = if (model.state == STATE_CLOSED) View.INVISIBLE else View.VISIBLE
            summary?.visibility = if (model.state == STATE_CLOSED) View.VISIBLE else View.INVISIBLE

            // expanding a subject - mark proposed & final grade as seen
            var unseenChanged = false
            if (model.proposedGrade?.seen == false) {
                manager.markAsSeen(model.proposedGrade!!)
                unseenChanged = true
            }
            if (model.finalGrade?.seen == false) {
                manager.markAsSeen(model.finalGrade!!)
                unseenChanged = true
            }
            // remove the override flag
            model.hasUnseen = false

            if (unseenChanged) {
                // check if the unseen status has changed
                if (!model.hasUnseen) {
                    notifyItemChanged(model)
                }
            }
        }

        if (model.state == STATE_CLOSED) {

            val subItems = when {
                model is GradesSubject && manager.isUniversity -> listOf()
                model is GradesSemester && model.grades.isEmpty() ->
                    listOf(GradesEmpty())
                model is GradesSemester && manager.hideImproved ->
                    model.items.filter { !it.seen || !it.isImproved }
                else -> model.items
            }

            if (model is GradesSubject && model.isUnknown) {
                position++
                items.add(position, GradesUnknownSubject())
                if (notifyAdapter) notifyItemInserted(position)
            }

            model.state = STATE_OPENED
            if (subItems.isNotEmpty()) {
                position++
                items.addAll(position, subItems.filterNotNull())
                if (notifyAdapter) notifyItemRangeInserted(position, subItems.size)
            }

            if (model is GradesSubject) {
                // auto expand first semester
                if (model.semesters.isNotEmpty()) {
                    val semester = model.semesters.firstOrNull { it.grades.isNotEmpty() } ?: model.semesters.first()
                    val semesterIndex = model.semesters.indexOf(semester)

                    val grades = when {
                        semester.grades.isEmpty() ->
                            listOf(GradesEmpty())
                        manager.hideImproved ->
                            semester.grades.filter { !it.seen || !it.isImproved }
                        else -> semester.grades
                    }

                    position++
                    semester.state = STATE_OPENED
                    items.addAll(position + semesterIndex, grades)
                    if (notifyAdapter) notifyItemRangeInserted(position + semesterIndex, grades.size)
                }
            }
            if (notifyAdapter) notifyItemChanged(items.indexOf(model))
        }
        else {
            val start = position + 1
            var end: Int = items.size
            for (i in start until items.size) {
                val model1 = items[i]
                val level = if (model1 is GradesStats || model1 is GradesSimulatorToggle) 0 else (model1 as? ExpandableItemModel<*>)?.level ?: 3
                if (level <= model.level) {
                    end = i
                    break
                } else {
                    if (model1 is ExpandableItemModel<*> && model1.state == STATE_OPENED) {
                        model1.state = STATE_CLOSED
                    }
                }
            }

            if (end != -1) {
                items.subList(start, end).clear()
                if (notifyAdapter) notifyItemRangeRemoved(start, end - start)
            }

            model.state = STATE_CLOSED
            if (notifyAdapter) notifyItemChanged(position)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        if (holder is SimulatorToggleViewHolder && item is GradesSimulatorToggle) {
            holder.button.setText(if (simulatorMode) R.string.grades_simulator_exit else R.string.menu_grades_editor)
            holder.button.setOnClickListener { onSimulatorToggle?.invoke(!simulatorMode) }
            holder.button.applyExpressiveShapeMorphToButtons()
            return
        }
        if (holder !is BindableViewHolder<*, *>)
            return

        val viewType = when (holder) {
            is SubjectViewHolder -> ITEM_TYPE_SUBJECT
            is SemesterViewHolder -> ITEM_TYPE_SEMESTER
            is EmptyViewHolder -> ITEM_TYPE_EMPTY
            is GradeViewHolder -> ITEM_TYPE_GRADE
            is StatsViewHolder -> ITEM_TYPE_STATS
            is UnknownSubjectViewHolder -> ITEM_TYPE_UNKNOWN_SUBJECT
            is SimulatorToggleViewHolder -> ITEM_TYPE_SIMULATOR
            else -> throw IllegalArgumentException("Incorrect viewType")
        }
        holder.itemView.setTag(R.string.tag_key_view_type, viewType)
        holder.itemView.setTag(R.string.tag_key_position, position)
        holder.itemView.setTag(R.string.tag_key_model, item)

        bindGroupShape(holder.itemView, item, position)

        when {
            holder is SubjectViewHolder && item is GradesSubject -> holder.onBind(activity, app, item, position, this)
            holder is SemesterViewHolder && item is GradesSemester -> holder.onBind(activity, app, item, position, this)
            holder is EmptyViewHolder && item is GradesEmpty -> holder.onBind(activity, app, item, position, this)
            holder is GradeViewHolder && item is GradeFull -> holder.onBind(activity, app, item, position, this)
            holder is StatsViewHolder && item is GradesStats -> holder.onBind(activity, app, item, position, this)
            holder is UnknownSubjectViewHolder && item is GradesUnknownSubject -> holder.onBind(activity, app, item, position, this)
        }

        if (holder is SemesterViewHolder && item is GradesSemester) {
            val subject = items.firstOrNull { it is GradesSubject && it.subjectId == item.subjectId } as? GradesSubject
            val canEdit = simulatorMode && !item.hideEditor && subject != null && !subjectHasPointGrades(subject.subjectId)
            holder.b.simAddButton.isVisible = canEdit
            holder.b.simAddButton.setOnClickListener { v ->
                val subj = subject ?: return@setOnClickListener
                addSimulatorGradeFlow(v, subj, item)
            }
            holder.b.simFinalButton.isVisible = canEdit
            val onFinalClick = View.OnClickListener { v ->
                val subj = subject ?: return@OnClickListener
                showSimulatorFinalGradePicker(v, subj, item)
            }
            holder.b.simFinalButton.setOnClickListener(onFinalClick)
            // Outside simulation, a clickable grade would intercept clicks that expand the row.
            holder.b.finalGrade.setOnClickListener(if (canEdit) onFinalClick else null)
            holder.b.finalGrade.isClickable = canEdit
        }

        val isItemClickable = when (item) {
            is ExpandableItemModel<*> -> true
            is GradeFull -> onGradeClick != null && item.type != TYPE_NO_GRADE
            else -> false
        }

        if (isItemClickable) {
            holder.itemView.setOnClickListener(onClickListener)
            holder.itemView.isEnabled = true
        } else {
            holder.itemView.setOnClickListener(null)
            holder.itemView.isClickable = false
        }
    }

    fun notifyItemChanged(model: Any) {
        startCoroutineTimer(1000L, 0L) {
            val index = items.indexOf(model)
            if (index != -1)
                notifyItemChanged(index)
        }
    }

    fun removeItem(model: Any) {
        startCoroutineTimer(2000L, 0L) {
            val index = items.indexOf(model)
            if (index != -1) {
                items.removeAt(index)
                notifyItemRemoved(index)
                if (index > 0) notifyItemChanged(index - 1)
            }
        }
    }

    private fun bindGroupShape(view: View, item: Any, position: Int) {
        val card = view as? MaterialCardView ?: return
        val prev = items.getOrNull(position - 1)
        val next = items.getOrNull(position + 1)
        val isFirst = item is GradesSubject || prev == null || prev is GradesStats || prev is GradesSimulatorToggle
        val isLast = next == null || next is GradesSubject || next is GradesStats || next is GradesSimulatorToggle

        val shapeOverlay = when {
            isFirst && isLast -> R.style.ShapeAppearance_Material3_Corner_GroupSingle
            isFirst -> R.style.ShapeAppearance_Material3_Corner_GroupTop
            isLast -> R.style.ShapeAppearance_Material3_Corner_GroupBottom
            else -> R.style.ShapeAppearance_Material3_Corner_GroupMiddle
        }
        card.shapeAppearanceModel = ShapeAppearanceModel.builder(activity, shapeOverlay, 0).build()
        card.enableExpressiveCardShapeMorph()

        (card.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
            params.bottomMargin = (if (isLast) 8 else 3).dp
            card.layoutParams = params
        }
    }

    override fun getItemCount() = items.size

    class SimulatorToggleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val button: MaterialButton = view.findViewById(R.id.simulatorToggleButton)
    }

    /**
     * Original grade names, values and weights captured before simulation.
     * Simulator edits mutate shared objects, so these values must be restored on exit.
     */
    private var simGradeOriginals = mutableMapOf<Long, Triple<String, Float, Float>>()

    fun snapshotSimulatorGrades() {
        // Include collapsed semesters and proposed/final grades so every edit can be restored.
        simGradeOriginals = items.filterIsInstance<GradesSubject>()
            .flatMap { subject ->
                listOfNotNull(subject.proposedGrade, subject.finalGrade) +
                    subject.semesters.flatMap { semester ->
                        semester.grades + listOfNotNull(semester.proposedGrade, semester.finalGrade)
                    }
            }
            .associate { it.id to Triple(it.name, it.value, it.weight) }
            .toMutableMap()
    }

    fun restoreSimulatorGrades(grades: List<GradeFull>) {
        if (simGradeOriginals.isEmpty())
            return
        for (grade in grades) {
            simGradeOriginals[grade.id]?.let { (name, value, weight) ->
                grade.name = name
                grade.value = value
                grade.weight = weight
            }
        }
        simGradeOriginals.clear()
    }

    /**
     * Formatted averages captured before simulation.
     * Keys: "sem:{subjectId}:{number}", "semNamed:{subjectId}:{number}",
     * "subj:{subjectId}", "subjNamed:{subjectId}".
     */
    private var simAvgSnapshot = mutableMapOf<String, String>()

    fun snapshotSimulatorAverages() {
        simAvgSnapshot.clear()
        for (subject in items.filterIsInstance<GradesSubject>()) {
            if (subject.isUnknown)
                continue
            simAvgSnapshot["subj:${subject.subjectId}"] = manager.getYearSummaryString(
                app,
                subject.semesters.sumOf { it.grades.size },
                subject.averages
            ).toString()
            simAvgSnapshot["subjNamed:${subject.subjectId}"] = manager.getAverageString(
                app,
                subject.averages,
                nameSemester = true
            )?.toString() ?: ""
            for (semester in subject.semesters) {
                simAvgSnapshot["sem:${subject.subjectId}:${semester.number}"] = manager.getAverageString(
                    app,
                    semester.averages
                )?.toString() ?: ""
                simAvgSnapshot["semNamed:${subject.subjectId}:${semester.number}"] = manager.getAverageString(
                    app,
                    semester.averages,
                    nameSemester = true,
                    showSemester = semester.number
                )?.toString() ?: ""
            }
        }
    }

    fun clearSimulatorSnapshots() {
        simAvgSnapshot.clear()
    }

    /**
     * Formats original and simulated averages together while simulation is active.
     * [stripLabel] removes repeated labels from the simulated part.
     */
    fun simulatorAvgText(key: String, live: CharSequence?, stripLabel: Boolean = false): CharSequence? {
        if (!simulatorMode)
            return live
        val snap = simAvgSnapshot[key]?.takeIf { it.isNotEmpty() } ?: return live
        if (live == null)
            return snap
        var liveText = live.toString()
        if (stripLabel) {
            liveText = liveText.substringAfter(": ")
            val averageLabel = activity.getString(R.string.grades_average_normal_format, "")
            if (averageLabel.isNotEmpty())
                liveText = liveText.replace(averageLabel, "")
        }
        return "$snap / $liveText"
    }

    /** Subjects with point-based grades are excluded from simulation. */
    fun subjectHasPointGrades(subjectId: Long): Boolean {
        val subject = items.firstOrNull { it is GradesSubject && it.subjectId == subjectId } as? GradesSubject
            ?: return false
        return subject.semesters.any { semester ->
            semester.grades.any { it.type == Grade.TYPE_POINT_AVG || it.type == Grade.TYPE_POINT_SUM }
        }
    }

    @SuppressLint("RestrictedApi")
    private fun showSimulatorGradeMenu(anchor: View, grade: GradeFull) {
        val context = anchor.context
        val menu = MenuBuilder(context)
        menu.add(0, 0, 0, R.string.grades_editor_change_grade).apply {
            icon = CommunityMaterial.Icon3.cmd_pencil_outline.toDrawable(context)
            setOnMenuItemClickListener {
                showSimulatorGradePicker(anchor) { name ->
                    grade.name = name
                    grade.value = manager.getGradeValue(name)
                    refreshSimulatorAverages()
                }
                true
            }
        }
        menu.add(0, 1, 1, R.string.grades_editor_change_weight).apply {
            icon = CommunityMaterial.Icon3.cmd_weight.toDrawable(context)
            setOnMenuItemClickListener {
                showSimulatorWeightPicker(anchor) { weight ->
                    grade.weight = weight
                    refreshSimulatorAverages()
                }
                true
            }
        }
        menu.add(0, 2, 2, R.string.remove).apply {
            icon = CommunityMaterial.Icon.cmd_delete_sweep_outline.toDrawable(context)
            setOnMenuItemClickListener {
                removeSimulatorGrade(grade)
                true
            }
        }
        MenuPopupHelper(context, menu, anchor).apply {
            setForceShowIcon(true)
            show()
        }
    }

    fun showSimulatorGradePicker(anchor: View, callback: (String) -> Unit) {
        val popup = PopupMenu(activity, anchor)
        SIM_GRADE_NAMES.forEachIndexed { index, name ->
            popup.menu.add(0, index, index, name)
        }
        popup.setOnMenuItemClickListener { item ->
            callback(SIM_GRADE_NAMES[item.itemId])
            true
        }
        popup.show()
    }

    fun showSimulatorWeightPicker(anchor: View, callback: (Float) -> Unit) {
        val popup = PopupMenu(activity, anchor)
        for (i in 0..6) {
            popup.menu.add(0, i, i, activity.getString(R.string.grades_editor_weight_format, DecimalFormat("#.##").format(i.toLong())))
        }
        popup.menu.add(1, 100, 100, activity.getString(R.string.grades_editor_weight_other))
        popup.setOnMenuItemClickListener { item ->
            if (item.itemId == 100) {
                SimpleDialog<Unit>(activity) {
                    title(R.string.grades_editor_add_grade_title)
                    message(R.string.grades_editor_add_grade_weight)
                    input(InputType.TYPE_NUMBER_FLAG_SIGNED)
                    positive(R.string.ok) {
                        try {
                            callback(getInput()?.text?.toString()?.toFloat() ?: 0f)
                        } catch (e: Exception) {
                            Timber.e(e)
                        }
                    }
                    negative(R.string.cancel)
                }.show()
            } else {
                callback(item.itemId.toFloat())
            }
            true
        }
        popup.show()
    }

    fun addSimulatorGradeFlow(anchor: View, subject: GradesSubject, semester: GradesSemester) {
        showSimulatorGradePicker(anchor) { name ->
            showSimulatorWeightPicker(anchor) { weight ->
                addSimulatorGrade(subject, semester, name, weight)
            }
        }
    }

    private fun createSimulatorGrade(subjectId: Long, semester: Int, type: Int, name: String, weight: Float) =
        GradeFull(
            profileId = App.profileId,
            id = simGradeId--,
            name = name,
            type = type,
            value = manager.getGradeValue(name),
            weight = weight,
            color = 0,
            category = null,
            description = null,
            comment = null,
            semester = semester,
            teacherId = -1L,
            subjectId = subjectId,
            addedDate = 0L
        ).also {
            it.notes = mutableListOf()
            // Mark simulated final grades as seen to prevent views from updating read status in the database.
            it.seen = true
            it.showAsUnseen = false
        }

    fun addSimulatorGrade(subject: GradesSubject, semester: GradesSemester, name: String, weight: Float) {
        val grade = createSimulatorGrade(subject.subjectId, semester.number, Grade.TYPE_NORMAL, name, weight)
        semester.grades.add(grade)

        if (semester.state == STATE_OPENED) {
            val semPos = items.indexOf(semester)
            if (semPos != -1) {
                var insertPos = semPos + 1
                while (insertPos < items.size) {
                    val next = items[insertPos]
                    val level = if (next is GradesStats || next is GradesSimulatorToggle) 0 else (next as? ExpandableItemModel<*>)?.level ?: 3
                    if (level <= semester.level)
                        break
                    insertPos++
                }
                // grades might be filtered (hideImproved) – keep the same rules as expansion
                val visible = !manager.hideImproved || grade.seen || !grade.isImproved
                if (visible) {
                    items.add(insertPos, grade)
                }
            }
        } else {
            expandModel(semester, null)
        }
        refreshSimulatorAverages()
    }

    /** Selects a semester grade, or the yearly grade when [semester] is null. */
    fun showSimulatorFinalGradePicker(anchor: View, subject: GradesSubject, semester: GradesSemester?) {
        val current = if (semester != null) semester.finalGrade else subject.finalGrade
        val popup = PopupMenu(activity, anchor)
        SIM_FINAL_GRADE_NAMES.forEachIndexed { index, name ->
            popup.menu.add(0, index, index, name)
        }
        if (current != null) {
            popup.menu.add(
                1,
                SIM_FINAL_REMOVE_ID,
                SIM_FINAL_REMOVE_ID,
                if (semester != null)
                    R.string.grades_simulator_remove_semester_final
                else
                    R.string.grades_simulator_remove_year_final
            )
        }
        popup.setOnMenuItemClickListener { item ->
            setSimulatorFinalGrade(subject, semester, SIM_FINAL_GRADE_NAMES.getOrNull(item.itemId))
            true
        }
        popup.show()
    }

    private fun setSimulatorFinalGrade(subject: GradesSubject, semester: GradesSemester?, name: String?) {
        val current = if (semester != null) semester.finalGrade else subject.finalGrade
        val grade = when {
            name == null -> null
            current != null -> current.also {
                it.name = name
                it.value = manager.getGradeValue(name)
            }
            semester != null -> createSimulatorGrade(
                subjectId = subject.subjectId,
                semester = semester.number,
                type = if (semester.number == 1) Grade.TYPE_SEMESTER1_FINAL else Grade.TYPE_SEMESTER2_FINAL,
                name = name,
                weight = 0f
            )
            else -> createSimulatorGrade(
                subjectId = subject.subjectId,
                semester = subject.semester,
                type = Grade.TYPE_YEAR_FINAL,
                name = name,
                weight = 0f
            )
        }
        if (semester != null)
            semester.finalGrade = grade
        else
            subject.finalGrade = grade
        refreshSimulatorAverages()
    }

    private fun removeSimulatorGrade(grade: GradeFull) {
        val subject = items.firstOrNull { it is GradesSubject && it.subjectId == grade.subjectId } as? GradesSubject ?: return
        val semester = subject.semesters.firstOrNull { it.number == grade.semester } ?: return
        semester.grades.remove(grade)
        val pos = items.indexOf(grade)
        if (pos != -1) {
            items.removeAt(pos)
        }
        refreshSimulatorAverages()
    }

    private fun countSimGrade(grade: Grade, averages: GradesAverages) {
        val value = manager.getGradeValue(grade)
        val weight = manager.getGradeWeight(manager.dontCountEnabled, manager.dontCountGrades, grade)
        when (grade.type) {
            Grade.TYPE_NORMAL -> {
                if (grade.value > 0f) {
                    averages.normalSum += value
                    averages.normalCount++
                }
                averages.normalWeightedSum += value * weight
                averages.normalWeightedCount += weight
            }
            Grade.TYPE_POINT_AVG -> {
                averages.pointAvgSum += grade.value
                averages.pointAvgMax += grade.valueMax ?: value
            }
            Grade.TYPE_POINT_SUM -> {
                averages.pointSum += grade.value
            }
        }
    }

    fun refreshSimulatorAverages() {
        val subjects = items.filterIsInstance<GradesSubject>()
        for (subject in subjects) {
            if (subject.isUnknown)
                continue
            subject.averages.reset()
            for (semester in subject.semesters) {
                val averages = GradesAverages()
                for (grade in semester.grades) {
                    countSimGrade(grade, averages)
                    countSimGrade(grade, subject.averages)
                }
                manager.calculateAverages(averages)
                semester.averages.copyFrom(averages)
            }
            manager.calculateAverages(subject.averages, subject.semesters)
        }
        val stats = items.firstOrNull { it is GradesStats } as? GradesStats
        if (stats != null) {
            manager.computeGradesStats(subjects, stats)
        }
        notifyDataSetChanged()
    }
}
