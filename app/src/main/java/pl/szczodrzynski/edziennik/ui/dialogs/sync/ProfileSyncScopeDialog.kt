package pl.szczodrzynski.edziennik.ui.dialogs.sync

import androidx.appcompat.app.AppCompatActivity
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.db.entity.Profile

internal class ProfileSyncScopeDialog(
    activity: AppCompatActivity,
    profile: Profile,
) : SyncFeatureListDialog(activity, profile) {
    override fun getTitleRes() = R.string.profile_config_sync_scope
    override fun getPositiveButtonText() = R.string.save

    override suspend fun onPositiveClick(): Boolean {
        val syncConfig = app.config[profile.id].sync
        val previousSelection = syncConfig.automaticSyncFeatures ?: supportedFeatures
        val hiddenSelection = previousSelection.intersect(supportedFeatures - visibleFeatures)
        val selected = getMultiSelection() + hiddenSelection
        syncConfig.automaticSyncFeatures = selected.takeUnless { it == supportedFeatures }
        return DISMISS
    }
}
