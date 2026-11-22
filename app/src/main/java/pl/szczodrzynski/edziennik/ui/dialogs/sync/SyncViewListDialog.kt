/*
 * Copyright (c) Kuba Szczodrzyński 2019-11-13.
 */

package pl.szczodrzynski.edziennik.ui.dialogs.sync

import androidx.appcompat.app.AppCompatActivity
import pl.szczodrzynski.edziennik.MainActivity
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.api.edziennik.EdziennikTask
import pl.szczodrzynski.edziennik.data.db.entity.Message
import pl.szczodrzynski.edziennik.data.db.entity.Profile
import pl.szczodrzynski.edziennik.data.enums.FeatureType
import pl.szczodrzynski.edziennik.data.enums.NavTarget
import pl.szczodrzynski.edziennik.ext.hasFeature
import pl.szczodrzynski.edziennik.ui.base.dialog.BaseDialog
import pl.szczodrzynski.edziennik.ui.messages.list.MessagesFragment

internal abstract class SyncFeatureListDialog(
    activity: AppCompatActivity,
    protected val profile: Profile,
) : BaseDialog<FeatureType>(activity) {

    override fun getNegativeButtonText() = R.string.cancel

    protected val supportedFeatures: Set<FeatureType> by lazy {
        val navigationFeatures = NavTarget.entries.mapNotNull { it.featureType }
            .flatMap { feature ->
                if (feature == FeatureType.MESSAGES_INBOX)
                    listOf(FeatureType.MESSAGES_INBOX, FeatureType.MESSAGES_SENT)
                else listOf(feature)
            }
        (navigationFeatures + FeatureType.entries)
            .filter { !it.isAlwaysNeeded && it.nameRes != null && profile.hasFeature(it) }
            .toSet()
    }

    protected val visibleFeatures: Set<FeatureType> by lazy {
        supportedFeatures.filterNot {
            it == FeatureType.EXCUSES && NavTarget.EXCUSES.isAvailable?.invoke(profile) == false
        }.toSet()
    }

    override fun getMultiChoiceItems(): Map<CharSequence, FeatureType> =
        visibleFeatures.associateBy { activity.getString(requireNotNull(it.nameRes)) }

    override fun getDefaultSelectedItems() =
        app.config[profile.id].sync.automaticSyncFeatures ?: visibleFeatures
}

internal class SyncViewListDialog(
    activity: MainActivity,
    profile: Profile,
    private val currentNavTarget: NavTarget,
) : SyncFeatureListDialog(activity, profile) {

    override fun getTitleRes() = R.string.dialog_sync_view_list_title
    override fun getPositiveButtonText() = R.string.ok
    override fun getNeutralButtonText() = R.string.sync_feature_all

    override fun getDefaultSelectedItems(): Set<FeatureType> = when (currentNavTarget) {
        NavTarget.HOME -> super.getDefaultSelectedItems()
        NavTarget.MESSAGES -> when (MessagesFragment.pageSelection) {
            Message.TYPE_SENT -> setOf(FeatureType.MESSAGES_SENT)
            else -> setOf(FeatureType.MESSAGES_INBOX)
        }
        else -> currentNavTarget.featureType?.let { setOf(it) } ?: emptySet()
    }

    override suspend fun onPositiveClick(): Boolean {
        val selected = getMultiSelection()
        if (selected.isEmpty())
            return DISMISS

        if (activity is MainActivity)
            activity.swipeRefreshLayout.isRefreshing = true
        EdziennikTask.syncProfile(
            profile.id,
            selected
        ).enqueue(activity)
        return DISMISS
    }

    override suspend fun onNeutralClick(): Boolean {
        if (activity is MainActivity)
            activity.swipeRefreshLayout.isRefreshing = true
        EdziennikTask.syncProfile(profile.id).enqueue(activity)
        return DISMISS
    }
}
