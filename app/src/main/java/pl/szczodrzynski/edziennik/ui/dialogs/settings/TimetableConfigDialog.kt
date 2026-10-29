/*
 * Copyright (c) Kuba Szczodrzyński 2022-10-7.
 */

package pl.szczodrzynski.edziennik.ui.dialogs.settings

import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.databinding.TimetableConfigDialogBinding
import pl.szczodrzynski.edziennik.ext.Intent
import pl.szczodrzynski.edziennik.ext.onClick
import pl.szczodrzynski.edziennik.ui.base.dialog.ConfigDialog
import pl.szczodrzynski.edziennik.ui.timetable.SubjectColorsDialog
import pl.szczodrzynski.edziennik.ui.timetable.TimetableFragment

class TimetableConfigDialog(
    activity: AppCompatActivity,
    reloadOnDismiss: Boolean = true,
) : ConfigDialog<TimetableConfigDialogBinding>(activity, reloadOnDismiss) {

    override fun getTitleRes() = R.string.menu_timetable_config
    override fun inflate(layoutInflater: LayoutInflater) =
        TimetableConfigDialogBinding.inflate(layoutInflater)

    override fun initView() {
        b.features = app.profile.loginStoreType.features
        b.changeSubjectColors.onClick {
            SubjectColorsDialog(activity).show()
        }
    }

    override suspend fun loadConfig() {
        val profile = app.profile
        b.config = profile.config.ui
        b.hideClassLessonsCard.isVisible = profile.config.ui.timetableHideClassLessons ||
            withContext(Dispatchers.IO) { app.db.timetableDao().hasExtraLessons(profile.id) }
    }

    override suspend fun saveConfig() {
        activity.sendBroadcast(
            Intent(TimetableFragment.ACTION_RELOAD_PAGES).setPackage(activity.packageName)
        )
    }
}
