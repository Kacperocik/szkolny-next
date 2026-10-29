package pl.szczodrzynski.edziennik.ui.excuses

import android.os.Bundle
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.MainActivity
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.api.edziennik.EdziennikTask
import pl.szczodrzynski.edziennik.data.api.events.ApiTaskErrorEvent
import pl.szczodrzynski.edziennik.data.api.events.ApiTaskFinishedEvent
import pl.szczodrzynski.edziennik.data.enums.FeatureType
import pl.szczodrzynski.edziennik.databinding.ExcusesFragmentBinding
import pl.szczodrzynski.edziennik.ui.base.fragment.BaseFragment

class ExcusesFragment : BaseFragment<ExcusesFragmentBinding, MainActivity>(
    inflater = ExcusesFragmentBinding::inflate,
) {
    private var profileId = -1
    private var hasItems = false
    override fun getScrollingView() = b.list
    override fun getSyncParams() = FeatureType.EXCUSES to null
    override fun getFab() = if (app.profile.archived || app.profile.config.librusExcusesUnavailable) null else
        R.string.excuses_action_add to CommunityMaterial.Icon3.cmd_text_box_plus_outline
    override suspend fun onFabClick() { ExcuseEditorDialog(activity, profileId = profileId).show() }

    override suspend fun onViewReady(savedInstanceState: Bundle?) {
        profileId = App.profileId
        val adapter = ExcuseListAdapter(activity) { ExcuseDetailsDialog(activity, it).show() }
        b.list.layoutManager = LinearLayoutManager(activity)
        b.list.adapter = adapter
        app.db.librusExcuseDao().getAll(profileId).observe(viewLifecycleOwner) { excuses ->
            hasItems = excuses.isNotEmpty()
            b.progressBar.isVisible = false
            b.list.isVisible = hasItems
            b.noData.isVisible = !hasItems
            b.noData.setText(R.string.excuses_no_data)
            adapter.items = excuses
            adapter.notifyDataSetChanged()
        }
        if (!app.profile.archived) refresh()
    }

    private fun refresh() = EdziennikTask.syncProfile(profileId, setOf(FeatureType.EXCUSES)).enqueue(activity)

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDownloadError(event: ApiTaskErrorEvent) {
        if (event.error.profileId != profileId || event.request !is EdziennikTask.SyncProfileRequest ||
            event.request.featureTypes != setOf(FeatureType.EXCUSES)) return
        b.progressBar.isVisible = false
        if (!hasItems) {
            b.noData.text = event.error.getStringReason(activity)
            b.noData.isVisible = true
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDownloadFinished(event: ApiTaskFinishedEvent) {
        if (event.profileId != profileId || !app.profile.config.librusExcusesUnavailable) return
        hideFab()
        if (!hasItems) b.noData.setText(R.string.error_191_reason)
    }
}
