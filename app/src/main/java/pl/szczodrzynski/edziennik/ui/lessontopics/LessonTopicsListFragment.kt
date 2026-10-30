package pl.szczodrzynski.edziennik.ui.lessontopics

import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.MainActivity
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.api.edziennik.EdziennikTask
import pl.szczodrzynski.edziennik.data.api.events.ApiTaskErrorEvent
import pl.szczodrzynski.edziennik.data.db.entity.LessonTopic
import pl.szczodrzynski.edziennik.data.enums.FeatureType
import pl.szczodrzynski.edziennik.databinding.LessonTopicsListFragmentBinding
import pl.szczodrzynski.edziennik.ui.base.fragment.BaseFragment

class LessonTopicsListFragment : BaseFragment<LessonTopicsListFragmentBinding, MainActivity>(LessonTopicsListFragmentBinding::inflate) {
    private var profileId = -1
    private var items = emptyList<LessonTopic>()
    private var selectedSubject: Long? = null
    private lateinit var adapter: LessonTopicAdapter
    private val planned get() = arguments?.getBoolean("planned") == true
    override fun getScrollingView() = b.list
    override fun getSyncParams() = FeatureType.LESSON_TOPICS to null

    override suspend fun onViewReady(savedInstanceState: Bundle?) {
        profileId = App.profileId
        selectedSubject = savedInstanceState?.takeIf { it.containsKey("subject") }?.getLong("subject")
        adapter = LessonTopicAdapter(savedInstanceState?.getStringArrayList("expandedDays")?.toSet()) { LessonTopicDetailsDialog(activity, it).show() }
        b.list.layoutManager = LinearLayoutManager(activity)
        b.list.adapter = adapter
        app.db.lessonTopicDao().getAll(profileId).observe(viewLifecycleOwner) { all ->
            items = all.filter { it.planned == planned }.let { if (planned) it.sortedWith(compareBy({ t -> t.date }, { t -> t.lessonNumber }, { t -> t.id })) else it }
            val subjects = items.distinctBy { it.subjectId }.sortedBy { it.subjectName }
            if (selectedSubject != null && subjects.none { it.subjectId == selectedSubject }) selectedSubject = null
            val labels = listOf(getString(R.string.lesson_topics_all_subjects)) + subjects.map { it.subjectName }
            b.subjectFilter.setAdapter(ArrayAdapter(activity, R.layout.lesson_topic_subject_dropdown_item, labels))
            b.subjectFilter.setText(subjects.firstOrNull { it.subjectId == selectedSubject }?.subjectName ?: labels.first(), false)
            b.subjectFilter.setOnItemClickListener { _, _, position, _ ->
                selectedSubject = subjects.getOrNull(position - 1)?.subjectId
                render()
            }
            render()
        }
    }

    private fun render() {
        val visible = items.filter { selectedSubject == null || it.subjectId == selectedSubject }
        adapter.submitTopics(visible)
        b.list.isVisible = visible.isNotEmpty()
        b.emptyState.isVisible = visible.isEmpty()
        b.noData.setText(if (selectedSubject != null) R.string.lesson_topics_no_filtered else
            if (planned) R.string.lesson_topics_no_planned else R.string.lesson_topics_no_realized)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        selectedSubject?.let { outState.putLong("subject", it) }
        if (::adapter.isInitialized) outState.putStringArrayList("expandedDays", ArrayList(adapter.expandedDays))
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDownloadError(event: ApiTaskErrorEvent) {
        val request = event.request as? EdziennikTask.SyncProfileRequest ?: return
        if (event.error.profileId != profileId || request.featureTypes != setOf(FeatureType.LESSON_TOPICS)) return
        if (items.isEmpty()) {
            b.noData.text = event.error.getStringReason(activity)
            b.emptyState.isVisible = true
        }
    }
}
