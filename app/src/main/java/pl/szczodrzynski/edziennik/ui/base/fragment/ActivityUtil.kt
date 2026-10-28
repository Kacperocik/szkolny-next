/*
 * Copyright (c) Kuba Szczodrzyński 2024-7-3.
 */

package pl.szczodrzynski.edziennik.ui.base.fragment

import android.widget.Toast
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import pl.szczodrzynski.edziennik.MainActivity
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.ui.login.LoginActivity
import pl.szczodrzynski.navlib.bottomsheet.items.BottomSheetGroup
import pl.szczodrzynski.navlib.bottomsheet.items.BottomSheetPrimaryItem

internal fun BaseFragment<*, *>.setupMainActivity(activity: MainActivity) {
    val items = getBottomSheetItems().toMutableList()

    val markAsReadType = getMarkAsReadType()
    if (markAsReadType != null) {
        items += BottomSheetPrimaryItem(true)
            .withTitle(R.string.menu_mark_as_read)
            .withIcon(CommunityMaterial.Icon.cmd_eye_check_outline)
            .withGroup(BottomSheetGroup.ACTIONS)
            .withOnClickListener {
                activity.bottomSheet.close()
                launch(Dispatchers.IO) {
                    app.db.metadataDao().setAllSeen(app.profileId, markAsReadType, true)
                }
                Toast.makeText(
                    activity,
                    R.string.main_menu_mark_as_read_success,
                    Toast.LENGTH_SHORT
                ).show()
            }
    }
    if (items.isNotEmpty()) {
        activity.navView.bottomSheet.prependItems(*items.toTypedArray())
    }

    getFab()?.let { (text, icon) ->
        activity.navView.bottomBar.apply {
            fabEnable = true
            fabExtendedText = app.getString(text)
            fabIcon = icon
            setFabOnClickListener {
                launch {
                    onFabClick()
                }
            }
        }
    }

    appBars += activity.navView.toolbar
}

internal fun BaseFragment<*, *>.setupLoginActivity(activity: LoginActivity) {}
