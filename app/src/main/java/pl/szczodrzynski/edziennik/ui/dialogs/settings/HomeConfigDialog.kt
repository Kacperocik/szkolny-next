/*
 * Copyright (c) Kuba Szczodrzyński 2025-11-25.
 */

package pl.szczodrzynski.edziennik.ui.dialogs.settings

import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.databinding.HomeConfigDialogBinding
import pl.szczodrzynski.edziennik.ext.onClick
import pl.szczodrzynski.edziennik.ui.base.dialog.ConfigDialog
import pl.szczodrzynski.edziennik.ui.home.HomeCardModel
import pl.szczodrzynski.edziennik.ui.home.HomeCardsDialog

class HomeConfigDialog(
    activity: AppCompatActivity,
    reloadOnDismiss: Boolean = true,
    onShowListener: ((tag: String) -> Unit)? = null,
    onDismissListener: ((tag: String) -> Unit)? = null,
) : ConfigDialog<HomeConfigDialogBinding>(
    activity,
    reloadOnDismiss,
) {

    val TAG = "HomeConfigDialog"

    override fun getTitleRes() = R.string.menu_home_config
    override fun getNegativeButtonText() = R.string.cancel
    override fun inflate(layoutInflater: LayoutInflater) =
        HomeConfigDialogBinding.inflate(layoutInflater)

    private var initialHomeCards = emptyList<HomeCardModel>()

    override suspend fun loadConfig() {
        initialHomeCards = app.profile.config.ui.homeCards.toList()
        b.lockCards.isChecked = app.profile.config.ui.homeCardsLocked
        b.timetableClassroomParentheses.isChecked = app.profile.config.ui.homeTimetableClassroomParentheses
        b.timetableWavyProgress.isChecked = app.profile.config.ui.homeTimetableWavyProgress
        b.timetableCornerButtons.isChecked = app.profile.config.ui.homeTimetableCornerButtons
        b.eventsLimit.value = app.profile.config.ui.homeEventsLimit.toFloat()
        b.eventsWeeks.value = app.profile.config.ui.homeEventsWeeks.toFloat()
        b.gradesWeeks.value = app.profile.config.ui.homeGradesWeeks.toFloat()
        b.announcementsLimit.value = app.profile.config.ui.homeAnnouncementsLimit.toFloat()
        b.notesLimit.value = app.profile.config.ui.homeNotesLimit.toFloat()
        b.notesCornerButton.isChecked = app.profile.config.ui.homeNotesCornerButton
    }

    override suspend fun saveConfig() {
        app.profile.config.ui.homeCardsLocked = b.lockCards.isChecked
        app.profile.config.ui.homeTimetableClassroomParentheses = b.timetableClassroomParentheses.isChecked
        app.profile.config.ui.homeTimetableWavyProgress = b.timetableWavyProgress.isChecked
        app.profile.config.ui.homeTimetableCornerButtons = b.timetableCornerButtons.isChecked
        app.profile.config.ui.homeEventsLimit = b.eventsLimit.value.toInt()
        app.profile.config.ui.homeEventsWeeks = b.eventsWeeks.value.toInt()
        app.profile.config.ui.homeGradesWeeks = b.gradesWeeks.value.toInt()
        app.profile.config.ui.homeAnnouncementsLimit = b.announcementsLimit.value.toInt()
        app.profile.config.ui.homeNotesLimit = b.notesLimit.value.toInt()
        app.profile.config.ui.homeNotesCornerButton = b.notesCornerButton.isChecked
    }

    override fun initView() {
        b.cardLockCards.onClick {
            b.lockCards.toggle()
        }
        b.cardTimetableClassroomParentheses.onClick {
            b.timetableClassroomParentheses.toggle()
        }
        b.cardTimetableWavyProgress.onClick {
            b.timetableWavyProgress.toggle()
        }
        b.cardTimetableCornerButtons.onClick {
            b.timetableCornerButtons.toggle()
        }
        b.cardNotesCornerButton.onClick {
            b.notesCornerButton.toggle()
        }
        b.configureCards.onClick {
            HomeCardsDialog(activity, reloadOnDismiss = false).show()
        }
    }

    override suspend fun onNegativeClick(): Boolean {
        app.profile.config.ui.homeCards = initialHomeCards
        return DISMISS
    }
}
