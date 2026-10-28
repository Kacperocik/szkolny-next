package pl.szczodrzynski.edziennik.ui.settings

import android.content.Context
import com.danielstone.materialaboutlibrary.model.MaterialAboutList
import pl.szczodrzynski.edziennik.ui.settings.cards.SettingsAboutCard

class SettingsAboutFragment : BaseSettingsFragment() {

    override val transparentCards = true

    override fun getMaterialAboutList(activityContext: Context?): MaterialAboutList {
        return MaterialAboutList(
            SettingsAboutCard(util).card,
        )
    }
}
