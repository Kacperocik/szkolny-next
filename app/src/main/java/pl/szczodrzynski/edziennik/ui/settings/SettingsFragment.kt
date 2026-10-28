/*
 * Copyright (c) Kuba Szczodrzyński 2021-3-16.
 */

package pl.szczodrzynski.edziennik.ui.settings

import android.content.Context
import com.danielstone.materialaboutlibrary.model.MaterialAboutCard
import com.danielstone.materialaboutlibrary.model.MaterialAboutList
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.enums.NavTarget
import pl.szczodrzynski.edziennik.ui.settings.cards.SettingsProfileCard

class SettingsFragment : BaseSettingsFragment() {

    override fun getMaterialAboutList(activityContext: Context?): MaterialAboutList {
        val categoriesCard = MaterialAboutCard.Builder()
            .addItem(
                MaterialAboutCategoryItem(
                    titleRes = R.string.settings_card_theme_title,
                    subtitleRes = R.string.settings_card_theme_subtext,
                    icon = CommunityMaterial.Icon3.cmd_palette_outline,
                ) {
                    activity.navigate(navTarget = NavTarget.SETTINGS_THEME)
                }
            )
            .addItem(
                MaterialAboutCategoryItem(
                    titleRes = R.string.settings_card_register_title,
                    subtitleRes = R.string.settings_card_register_subtext,
                    icon = CommunityMaterial.Icon3.cmd_school_outline,
                ) {
                    activity.navigate(navTarget = NavTarget.SETTINGS_REGISTER)
                }
            )
            .addItem(
                MaterialAboutCategoryItem(
                    titleRes = R.string.settings_card_sync_title,
                    subtitleRes = R.string.settings_card_sync_subtext,
                    icon = CommunityMaterial.Icon.cmd_download_outline,
                ) {
                    activity.navigate(navTarget = NavTarget.SETTINGS_SYNC)
                }
            )
            .addItem(
                MaterialAboutCategoryItem(
                    titleRes = R.string.settings_card_about_title,
                    subtitleRes = R.string.settings_card_about_subtext,
                    icon = CommunityMaterial.Icon2.cmd_information_outline,
                ) {
                    activity.navigate(navTarget = NavTarget.SETTINGS_ABOUT)
                }
            )
            .build()

        return MaterialAboutList(
            SettingsProfileCard(util).card,
            categoriesCard,
        )
    }
}
