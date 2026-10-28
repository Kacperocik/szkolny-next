package pl.szczodrzynski.edziennik.ui.homework

import android.os.Bundle
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.MainActivity
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.db.entity.Event
import pl.szczodrzynski.edziennik.data.enums.FeatureType
import pl.szczodrzynski.edziennik.databinding.HomeworkListFragmentBinding
import pl.szczodrzynski.edziennik.ext.getInt
import pl.szczodrzynski.edziennik.ext.hideKeyboard
import pl.szczodrzynski.edziennik.ui.base.fragment.BaseFragment
import pl.szczodrzynski.edziennik.ui.event.EventDetailsDialog
import pl.szczodrzynski.edziennik.ui.event.EventListAdapter
import pl.szczodrzynski.edziennik.utils.models.Date

class HomeworkListFragment : BaseFragment<HomeworkListFragmentBinding, MainActivity>(
    inflater = HomeworkListFragmentBinding::inflate,
) {

    override fun getScrollingView() = b.list
    override fun getSyncParams() = FeatureType.HOMEWORK to null

    private var adapter: EventListAdapter? = null

    fun setActiveEventId(eventId: Long?) {
        adapter?.activeEventId = eventId
    }

    override suspend fun onViewReady(savedInstanceState: Bundle?) {
        val homeworkDate = arguments.getInt("homeworkDate", HomeworkDate.CURRENT)

        b.noData.setText(when (homeworkDate) {
            HomeworkDate.PAST -> R.string.homework_no_past
            else -> R.string.homework_no_current
        })

        val today = Date.getToday()
        val filter = when(homeworkDate) {
            HomeworkDate.CURRENT -> "eventDate >= '${today.stringY_m_d}' AND eventIsDone = 0"
            else -> "(eventDate < '${today.stringY_m_d}' OR eventIsDone = 1)"
        }

        val adapter = EventListAdapter(
            activity,
            showWeekDay = true,
            showDate = true,
            showType = false,
            showTime = true,
            showSubject = true,
            markAsSeen = true,
            isReversed = homeworkDate == HomeworkDate.PAST,
            onEventClick = {
                activity.hideKeyboard()
                val parent = parentFragment as? HomeworkFragment
                if (parent?.isTwoColumnMode == true) {
                    parent.openEventInDetail(it)
                } else {
                    parent?.showEventDetailsDialog(it) ?: EventDetailsDialog(activity, it).show()
                }
            }
        )
        this.adapter = adapter
        adapter.activeEventId = (parentFragment as? HomeworkFragment)
            ?.takeIf { it.isTwoColumnMode }
            ?.selectedEventId

        app.db.eventDao().getAllByType(App.profileId, Event.TYPE_HOMEWORK, filter).observe(viewLifecycleOwner, Observer { events ->
            if (!isAdded) return@Observer

            events.forEach {
                it.filterNotes()
            }

            // show/hide relevant views
            b.progressBar.isVisible = false
            b.list.isVisible = events.isNotEmpty()
            b.noData.isVisible = events.isEmpty()
            if (events.isEmpty()) {
                return@Observer
            }

            // apply the new event list
            adapter.setAllItems(events, addSearchField = true)

            // configure the adapter & recycler view
            if (b.list.adapter == null) {
                b.list.apply {
                    setHasFixedSize(true)
                    layoutManager = LinearLayoutManager(context)
                    this.adapter = adapter
                }
            }

            // reapply the filter
            adapter.getSearchField()?.applyTo(adapter)
        })
    }
}
