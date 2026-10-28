package pl.szczodrzynski.edziennik.ui.home.cards

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.plusAssign
import androidx.core.view.setMargins
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.MainActivity
import pl.szczodrzynski.edziennik.data.api.edziennik.EdziennikTask
import pl.szczodrzynski.edziennik.data.api.events.AnnouncementGetEvent
import pl.szczodrzynski.edziennik.data.db.entity.Profile
import pl.szczodrzynski.edziennik.data.db.full.AnnouncementFull
import pl.szczodrzynski.edziennik.data.enums.LoginType
import pl.szczodrzynski.edziennik.data.enums.NavTarget
import pl.szczodrzynski.edziennik.databinding.CardHomeAnnouncementsBinding
import pl.szczodrzynski.edziennik.ext.dp
import pl.szczodrzynski.edziennik.ext.onClick
import pl.szczodrzynski.edziennik.ext.unregisterSafe
import pl.szczodrzynski.edziennik.ui.announcements.AnnouncementDetailsDialog
import pl.szczodrzynski.edziennik.ui.announcements.AnnouncementsAdapter
import pl.szczodrzynski.edziennik.ui.home.HomeCard
import pl.szczodrzynski.edziennik.ui.home.HomeCardAdapter
import pl.szczodrzynski.edziennik.ui.home.HomeFragment
import pl.szczodrzynski.edziennik.utils.models.Date
import kotlin.coroutines.CoroutineContext

class HomeAnnouncementsCard(
    override val id: Int,
    val app: App,
    val activity: MainActivity,
    val fragment: HomeFragment,
    val profile: Profile,
) : HomeCard, CoroutineScope {
    companion object {
        private const val TAG = "HomeAnnouncementsCard"
    }

    private var job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    private lateinit var adapter: AnnouncementsAdapter

    override fun bind(position: Int, holder: HomeCardAdapter.ViewHolder) { launch {
        holder.root.removeAllViews()
        val b = CardHomeAnnouncementsBinding.inflate(LayoutInflater.from(holder.root.context))
        b.root.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(4.dp)
        }
        holder.root += b.root

        if (!EventBus.getDefault().isRegistered(this@HomeAnnouncementsCard)) {
            EventBus.getDefault().register(this@HomeAnnouncementsCard)
        }

        adapter = AnnouncementsAdapter(activity, embedded = true) { _, announcement ->
            onAnnouncementClick(announcement)
        }

        app.db.announcementDao().getAll(profile.id).observe(fragment) { announcementsAll ->
            val today = Date.getToday()
            val announcements = announcementsAll
                .filter { it.isCurrent(today) }
                .onEach { it.filterNotes() }
                .take(app.profile.config.ui.homeAnnouncementsLimit)

            adapter.setAllItems(announcements)
            if (b.announcementsView.adapter == null) {
                b.announcementsView.adapter = adapter
                b.announcementsView.apply {
                    isNestedScrollingEnabled = false
                    layoutManager = LinearLayoutManager(context)
                }
            }
            adapter.notifyDataSetChanged()

            if (announcements.isNotEmpty()) {
                b.announcementsView.visibility = View.VISIBLE
                b.announcementsNoData.visibility = View.GONE
            } else {
                b.announcementsView.visibility = View.GONE
                b.announcementsNoData.visibility = View.VISIBLE
            }
        }

        holder.root.onClick {
            activity.navigate(navTarget = NavTarget.ANNOUNCEMENTS)
        }
    }}

    private fun AnnouncementFull.isCurrent(today: Date): Boolean {
        val start = startDate
        val end = endDate
        val startOk = start == null || start <= today
        val endOk = end == null || end >= today
        return startOk && endOk
    }

    private fun onAnnouncementClick(announcement: AnnouncementFull) {
        if (announcement.text == null || (profile.loginStoreType == LoginType.LIBRUS && !announcement.seen)) {
            EdziennikTask.announcementGet(profile.id, announcement).enqueue(activity)
        } else {
            showAnnouncementDetailsDialog(announcement)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun onAnnouncementGetEvent(event: AnnouncementGetEvent) {
        EventBus.getDefault().removeStickyEvent(event)
        showAnnouncementDetailsDialog(event.announcement)
    }

    private fun showAnnouncementDetailsDialog(announcement: AnnouncementFull) {
        AnnouncementDetailsDialog(activity, announcement, profile) {
            adapter.notifyDataSetChanged()
        }.show()
    }

    override fun unbind(position: Int, holder: HomeCardAdapter.ViewHolder) {
        EventBus.getDefault().unregisterSafe(this)
    }
}
