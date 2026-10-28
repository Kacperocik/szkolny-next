package pl.szczodrzynski.edziennik.ui.settings

import android.content.Context
import com.danielstone.materialaboutlibrary.model.MaterialAboutList
import pl.szczodrzynski.edziennik.ui.settings.cards.SettingsSyncCard

class SettingsSyncFragment : BaseSettingsFragment() {

    override val transparentCards = true

    override fun getMaterialAboutList(activityContext: Context?): MaterialAboutList {
        return MaterialAboutList(
            SettingsSyncCard(util).card,
        )
    }
}
