package pl.szczodrzynski.edziennik.ui.settings

import android.content.Context
import com.danielstone.materialaboutlibrary.model.MaterialAboutList
import pl.szczodrzynski.edziennik.ui.settings.cards.SettingsThemeCard

class SettingsThemeFragment : BaseSettingsFragment() {

    override val transparentCards = true

    override fun getMaterialAboutList(activityContext: Context?): MaterialAboutList {
        return MaterialAboutList(
            SettingsThemeCard(util).card,
        )
    }
}
