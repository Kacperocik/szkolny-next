/*
 * Copyright (c) Kacper Ziubryniewicz 2020-1-16
 */

package pl.szczodrzynski.edziennik.ui.dialogs.settings

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.widget.RadioButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.shape.ShapeAppearanceModel
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.core.manager.GradesManager.Companion.COLOR_MODE_DEFAULT
import pl.szczodrzynski.edziennik.core.manager.GradesManager.Companion.COLOR_MODE_WEIGHTED
import pl.szczodrzynski.edziennik.core.manager.GradesManager.Companion.SHAPE_MODE_CIRCLE
import pl.szczodrzynski.edziennik.core.manager.GradesManager.Companion.SHAPE_MODE_ROUNDED_RECT
import pl.szczodrzynski.edziennik.core.manager.GradesManager.Companion.ORDER_BY_DATE_DESC
import pl.szczodrzynski.edziennik.core.manager.GradesManager.Companion.ORDER_BY_SUBJECT_ASC
import pl.szczodrzynski.edziennik.core.manager.GradesManager.Companion.YEAR_1_AVG_2_AVG
import pl.szczodrzynski.edziennik.core.manager.GradesManager.Companion.YEAR_1_AVG_2_SEM
import pl.szczodrzynski.edziennik.core.manager.GradesManager.Companion.YEAR_1_SEM_2_AVG
import pl.szczodrzynski.edziennik.core.manager.GradesManager.Companion.YEAR_1_SEM_2_SEM
import pl.szczodrzynski.edziennik.core.manager.GradesManager.Companion.YEAR_ALL_GRADES
import pl.szczodrzynski.edziennik.databinding.DialogConfigGradesBinding
import pl.szczodrzynski.edziennik.ext.join
import pl.szczodrzynski.edziennik.ext.onChange
import pl.szczodrzynski.edziennik.ext.onClick
import pl.szczodrzynski.edziennik.ext.setOnSelectedListener
import pl.szczodrzynski.edziennik.ui.base.dialog.ConfigDialog
import pl.szczodrzynski.edziennik.ui.base.dialog.SimpleDialog
import pl.szczodrzynski.edziennik.core.manager.GradesManager.Companion.UNIVERSITY_AVERAGE_MODE_ECTS
import pl.szczodrzynski.edziennik.core.manager.GradesManager.Companion.UNIVERSITY_AVERAGE_MODE_SIMPLE

class GradesConfigDialog(
    activity: AppCompatActivity,
    reloadOnDismiss: Boolean = true,
) : ConfigDialog<DialogConfigGradesBinding>(activity, reloadOnDismiss) {

    override fun getTitleRes() = R.string.menu_grades_config
    override fun getNegativeButtonText() = R.string.cancel
    override fun inflate(layoutInflater: LayoutInflater) =
        DialogConfigGradesBinding.inflate(layoutInflater)

    private class Snapshot(
        val orderBy: Int,
        val colorMode: Int,
        val shapeMode: Int,
        val yearAverageMode: Int,
        val universityAverageMode: Int,
        val hideImproved: Boolean,
        val hideNoGrade: Boolean,
        val averageWithoutWeight: Boolean,
        val countEctsInProgress: Boolean,
    )

    private lateinit var snapshot: Snapshot

    @SuppressLint("SetTextI18n")
    override suspend fun loadConfig() {
        snapshot = Snapshot(
            orderBy = config.orderBy,
            colorMode = app.profile.config.grades.colorMode,
            shapeMode = app.profile.config.grades.shapeMode,
            yearAverageMode = app.profile.config.grades.yearAverageMode,
            universityAverageMode = app.profile.config.grades.universityAverageMode,
            hideImproved = app.profile.config.grades.hideImproved,
            hideNoGrade = app.profile.config.grades.hideNoGrade,
            averageWithoutWeight = app.profile.config.grades.averageWithoutWeight,
            countEctsInProgress = app.profile.config.grades.countEctsInProgress,
        )
        b.isUniversity = app.gradesManager.isUniversity
        b.cardHideImproved.shapeAppearanceModel = ShapeAppearanceModel.builder(
            activity,
            if (b.isUniversity) R.style.ShapeAppearance_Material3_Corner_GroupTop else R.style.ShapeAppearance_Material3_Corner_GroupMiddle,
            0
        ).build()

        b.customPlusCheckBox.isChecked = app.profile.config.grades.plusValue != null
        b.customPlusValue.isVisible = b.customPlusCheckBox.isChecked
        b.customMinusCheckBox.isChecked = app.profile.config.grades.minusValue != null
        b.customMinusValue.isVisible = b.customMinusCheckBox.isChecked

        b.customPlusValue.value = app.profile.config.grades.plusValue ?: 0.5f
        b.customMinusValue.value = app.profile.config.grades.minusValue ?: 0.25f

        when (config.orderBy) {
            ORDER_BY_DATE_DESC -> b.sortGradesByDateRadio
            ORDER_BY_SUBJECT_ASC -> b.sortGradesBySubjectRadio
            else -> null
        }?.isChecked = true

        when (app.profile.config.grades.colorMode) {
            COLOR_MODE_DEFAULT -> b.gradeColorFromERegister
            COLOR_MODE_WEIGHTED -> b.gradeColorByValue
            else -> null
        }?.isChecked = true

        when (app.profile.config.grades.shapeMode) {
            SHAPE_MODE_ROUNDED_RECT -> b.gradeShapeRoundedRect
            SHAPE_MODE_CIRCLE -> b.gradeShapeCircle
            else -> b.gradeShapeRoundedRect
        }.isChecked = true

        when (app.profile.config.grades.yearAverageMode) {
            YEAR_ALL_GRADES -> b.gradeAverageMode4
            YEAR_1_AVG_2_AVG -> b.gradeAverageMode0
            YEAR_1_SEM_2_AVG -> b.gradeAverageMode1
            YEAR_1_AVG_2_SEM -> b.gradeAverageMode2
            YEAR_1_SEM_2_SEM -> b.gradeAverageMode3
            else -> null
        }?.isChecked = true

        when (app.profile.config.grades.universityAverageMode) {
            UNIVERSITY_AVERAGE_MODE_ECTS -> b.gradeUniversityAverageMode1
            UNIVERSITY_AVERAGE_MODE_SIMPLE -> b.gradeUniversityAverageMode0
            else -> null
        }?.isChecked = true

        b.dontCountGrades.isChecked =
            app.profile.config.grades.dontCountEnabled && app.profile.config.grades.dontCountGrades.isNotEmpty()
        b.hideImproved.isChecked = app.profile.config.grades.hideImproved
        b.hideNoGrade.isChecked = app.profile.config.grades.hideNoGrade
        b.averageWithoutWeight.isChecked = app.profile.config.grades.averageWithoutWeight
        b.countEctsInProgress.isChecked = app.profile.config.grades.countEctsInProgress

        if (app.profile.config.grades.dontCountGrades.isEmpty()) {
            b.dontCountGradesText.setText("nb, 0, bz, bd")
        } else {
            b.dontCountGradesText.setText(app.profile.config.grades.dontCountGrades.join(", "))
        }
    }

    override suspend fun saveConfig() {
        app.profile.config.grades.plusValue =
            if (b.customPlusCheckBox.isChecked) b.customPlusValue.value else null
        app.profile.config.grades.minusValue =
            if (b.customMinusCheckBox.isChecked) b.customMinusValue.value else null

        b.dontCountGradesText.setText(
            b.dontCountGradesText
                .text
                ?.toString()
                ?.lowercase()
                ?.replace(", ", ",")
        )
        app.profile.config.grades.dontCountEnabled = b.dontCountGrades.isChecked
        app.profile.config.grades.dontCountGrades = b.dontCountGradesText.text
            ?.split(",")
            ?.map { it.trim() }
            ?: listOf()
    }

    override fun initView() {
        b.cardCustomPlus.onClick { b.customPlusCheckBox.toggle() }
        b.cardCustomMinus.onClick { b.customMinusCheckBox.toggle() }
        b.customPlusCheckBox.setOnCheckedChangeListener { _, isChecked ->
            b.customPlusValue.isVisible = isChecked
        }
        b.customMinusCheckBox.setOnCheckedChangeListener { _, isChecked ->
            b.customMinusValue.isVisible = isChecked
        }
        b.cardDontCount.onClick { b.dontCountGrades.toggle() }
        b.cardHideImproved.onClick { b.hideImproved.toggle() }
        b.cardHideNoGrade.onClick { b.hideNoGrade.toggle() }
        b.cardAverageWithoutWeight.onClick { b.averageWithoutWeight.toggle() }
        b.cardCountEctsInProgress.onClick { b.countEctsInProgress.toggle() }

        fun selectRadio(selected: RadioButton, vararg group: RadioButton) {
            group.forEach { it.isChecked = (it == selected) }
        }

        // Sort Group
        val sortGroup = arrayOf(b.sortGradesByDateRadio, b.sortGradesBySubjectRadio)
        b.cardSortByDate.onClick { selectRadio(b.sortGradesByDateRadio, *sortGroup); config.orderBy = ORDER_BY_DATE_DESC }
        b.cardSortBySubject.onClick { selectRadio(b.sortGradesBySubjectRadio, *sortGroup); config.orderBy = ORDER_BY_SUBJECT_ASC }
        b.sortGradesByDateRadio.setOnSelectedListener { selectRadio(b.sortGradesByDateRadio, *sortGroup); config.orderBy = ORDER_BY_DATE_DESC }
        b.sortGradesBySubjectRadio.setOnSelectedListener { selectRadio(b.sortGradesBySubjectRadio, *sortGroup); config.orderBy = ORDER_BY_SUBJECT_ASC }

        // Color Group
        val colorGroup = arrayOf(b.gradeColorFromERegister, b.gradeColorByValue)
        b.cardColorFromERegister.onClick { selectRadio(b.gradeColorFromERegister, *colorGroup); app.profile.config.grades.colorMode = COLOR_MODE_DEFAULT }
        b.cardColorByValue.onClick { selectRadio(b.gradeColorByValue, *colorGroup); app.profile.config.grades.colorMode = COLOR_MODE_WEIGHTED }
        b.gradeColorFromERegister.setOnSelectedListener { selectRadio(b.gradeColorFromERegister, *colorGroup); app.profile.config.grades.colorMode = COLOR_MODE_DEFAULT }
        b.gradeColorByValue.setOnSelectedListener { selectRadio(b.gradeColorByValue, *colorGroup); app.profile.config.grades.colorMode = COLOR_MODE_WEIGHTED }

        // Shape Group
        val shapeGroup = arrayOf(b.gradeShapeRoundedRect, b.gradeShapeCircle)
        b.cardShapeRoundedRect.onClick { selectRadio(b.gradeShapeRoundedRect, *shapeGroup); app.profile.config.grades.shapeMode = SHAPE_MODE_ROUNDED_RECT }
        b.cardShapeCircle.onClick { selectRadio(b.gradeShapeCircle, *shapeGroup); app.profile.config.grades.shapeMode = SHAPE_MODE_CIRCLE }
        b.gradeShapeRoundedRect.setOnSelectedListener { selectRadio(b.gradeShapeRoundedRect, *shapeGroup); app.profile.config.grades.shapeMode = SHAPE_MODE_ROUNDED_RECT }
        b.gradeShapeCircle.setOnSelectedListener { selectRadio(b.gradeShapeCircle, *shapeGroup); app.profile.config.grades.shapeMode = SHAPE_MODE_CIRCLE }

        // Year Average Group
        val yearAvgGroup = arrayOf(b.gradeAverageMode4, b.gradeAverageMode0, b.gradeAverageMode1, b.gradeAverageMode2, b.gradeAverageMode3)
        b.cardAvg4.onClick { selectRadio(b.gradeAverageMode4, *yearAvgGroup); app.profile.config.grades.yearAverageMode = YEAR_ALL_GRADES }
        b.cardAvg0.onClick { selectRadio(b.gradeAverageMode0, *yearAvgGroup); app.profile.config.grades.yearAverageMode = YEAR_1_AVG_2_AVG }
        b.cardAvg1.onClick { selectRadio(b.gradeAverageMode1, *yearAvgGroup); app.profile.config.grades.yearAverageMode = YEAR_1_SEM_2_AVG }
        b.cardAvg2.onClick { selectRadio(b.gradeAverageMode2, *yearAvgGroup); app.profile.config.grades.yearAverageMode = YEAR_1_AVG_2_SEM }
        b.cardAvg3.onClick { selectRadio(b.gradeAverageMode3, *yearAvgGroup); app.profile.config.grades.yearAverageMode = YEAR_1_SEM_2_SEM }
        b.gradeAverageMode4.setOnSelectedListener { selectRadio(b.gradeAverageMode4, *yearAvgGroup); app.profile.config.grades.yearAverageMode = YEAR_ALL_GRADES }
        b.gradeAverageMode0.setOnSelectedListener { selectRadio(b.gradeAverageMode0, *yearAvgGroup); app.profile.config.grades.yearAverageMode = YEAR_1_AVG_2_AVG }
        b.gradeAverageMode1.setOnSelectedListener { selectRadio(b.gradeAverageMode1, *yearAvgGroup); app.profile.config.grades.yearAverageMode = YEAR_1_SEM_2_AVG }
        b.gradeAverageMode2.setOnSelectedListener { selectRadio(b.gradeAverageMode2, *yearAvgGroup); app.profile.config.grades.yearAverageMode = YEAR_1_AVG_2_SEM }
        b.gradeAverageMode3.setOnSelectedListener { selectRadio(b.gradeAverageMode3, *yearAvgGroup); app.profile.config.grades.yearAverageMode = YEAR_1_SEM_2_SEM }

        // University Average Group
        val univAvgGroup = arrayOf(b.gradeUniversityAverageMode1, b.gradeUniversityAverageMode0)
        b.cardUnivAvg1.onClick { selectRadio(b.gradeUniversityAverageMode1, *univAvgGroup); app.profile.config.grades.universityAverageMode = UNIVERSITY_AVERAGE_MODE_ECTS }
        b.cardUnivAvg0.onClick { selectRadio(b.gradeUniversityAverageMode0, *univAvgGroup); app.profile.config.grades.universityAverageMode = UNIVERSITY_AVERAGE_MODE_SIMPLE }
        b.gradeUniversityAverageMode1.setOnSelectedListener { selectRadio(b.gradeUniversityAverageMode1, *univAvgGroup); app.profile.config.grades.universityAverageMode = UNIVERSITY_AVERAGE_MODE_ECTS }
        b.gradeUniversityAverageMode0.setOnSelectedListener { selectRadio(b.gradeUniversityAverageMode0, *univAvgGroup); app.profile.config.grades.universityAverageMode = UNIVERSITY_AVERAGE_MODE_SIMPLE }

        b.hideImproved.onChange { _, isChecked -> app.profile.config.grades.hideImproved = isChecked }
        b.hideNoGrade.onChange { _, isChecked -> app.profile.config.grades.hideNoGrade = isChecked }
        b.averageWithoutWeight.onChange { _, isChecked ->
            app.profile.config.grades.averageWithoutWeight = isChecked
        }
        b.countEctsInProgress.onChange { _, isChecked ->
            app.profile.config.grades.countEctsInProgress = isChecked
        }

        b.averageWithoutWeightHelp.onClick {
            SimpleDialog<Unit>(activity) {
                title(R.string.grades_config_average_without_weight)
                message(R.string.grades_config_average_without_weight_message)
                positive(R.string.ok)
            }.show()
        }
    }

    override suspend fun onNegativeClick(): Boolean {
        config.orderBy = snapshot.orderBy
        app.profile.config.grades.colorMode = snapshot.colorMode
        app.profile.config.grades.shapeMode = snapshot.shapeMode
        app.profile.config.grades.yearAverageMode = snapshot.yearAverageMode
        app.profile.config.grades.universityAverageMode = snapshot.universityAverageMode
        app.profile.config.grades.hideImproved = snapshot.hideImproved
        app.profile.config.grades.hideNoGrade = snapshot.hideNoGrade
        app.profile.config.grades.averageWithoutWeight = snapshot.averageWithoutWeight
        app.profile.config.grades.countEctsInProgress = snapshot.countEctsInProgress
        return DISMISS
    }
}
