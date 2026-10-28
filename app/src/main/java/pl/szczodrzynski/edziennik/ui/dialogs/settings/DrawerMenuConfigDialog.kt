package pl.szczodrzynski.edziennik.ui.dialogs.settings

import androidx.appcompat.app.AppCompatActivity
import pl.szczodrzynski.edziennik.MainActivity
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.enums.NavTarget
import pl.szczodrzynski.edziennik.ext.resolveString
import pl.szczodrzynski.edziennik.ui.base.dialog.BaseDialog

class DrawerMenuConfigDialog(
    activity: AppCompatActivity,
) : BaseDialog<NavTarget>(activity) {

    override fun getTitleRes() = R.string.settings_theme_drawer_buttons_dialog_title
    override fun getMessageRes() = R.string.settings_theme_drawer_buttons_dialog_text
    override fun getPositiveButtonText() = R.string.ok
    override fun getNegativeButtonText() = R.string.cancel

    @Suppress("USELESS_CAST")
    override fun getMultiChoiceItems() = NavTarget.drawerMoreConfigurableTargets
        .filter {
            if (it == NavTarget.AGENDA && !app.config.labOldAgenda) false
            else if (it == NavTarget.AGENDA_NEW && app.config.labOldAgenda) false
            else true
        }
        .associateBy { it.nameRes.resolveString(activity) as CharSequence }

    override fun getDefaultSelectedItems() = app.config.ui.drawerMoreButtons

    override suspend fun onPositiveClick(): Boolean {
        app.config.ui.drawerMoreButtons = getMultiSelection()
        if (activity is MainActivity) {
            activity.setDrawerItems()
            activity.drawer.updateBadges()
        }
        return DISMISS
    }
}
