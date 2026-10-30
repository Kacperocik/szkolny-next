package pl.szczodrzynski.edziennik.ui.lessontopics

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.db.entity.LessonTopic
import pl.szczodrzynski.edziennik.databinding.LessonTopicDayBinding
import pl.szczodrzynski.edziennik.databinding.LessonTopicItemBinding
import pl.szczodrzynski.edziennik.ext.enableExpressiveCardShapeMorph
import pl.szczodrzynski.edziennik.utils.models.Date
import pl.szczodrzynski.edziennik.utils.models.Week

internal data class LessonTopicDay(val date: String, val topics: List<LessonTopic>, val expanded: Boolean)

internal fun groupLessonTopics(items: List<LessonTopic>, expandedDays: Set<String>) =
    items.groupBy { it.date }.map { (date, topics) ->
        LessonTopicDay(date, topics.sortedWith(compareBy({ it.lessonNumber }, { it.id })), date in expandedDays)
    }

internal class LessonTopicAdapter(
    restoredExpandedDays: Set<String>? = null,
    private val onClick: (LessonTopic) -> Unit,
) : ListAdapter<LessonTopicDay, LessonTopicAdapter.Holder>(object : DiffUtil.ItemCallback<LessonTopicDay>() {
    override fun areItemsTheSame(a: LessonTopicDay, b: LessonTopicDay) = a.date == b.date
    override fun areContentsTheSame(a: LessonTopicDay, b: LessonTopicDay) = a == b
}) {
    private var topics = emptyList<LessonTopic>()
    private var initialSelectionPending = restoredExpandedDays == null
    val expandedDays = restoredExpandedDays.orEmpty().toMutableSet()

    fun submitTopics(items: List<LessonTopic>) {
        topics = items
        if (initialSelectionPending && items.isNotEmpty()) {
            expandedDays.add(items.first().date)
            initialSelectionPending = false
        }
        submitList(groupLessonTopics(items, expandedDays))
    }

    class Holder(val binding: LessonTopicDayBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val holder = Holder(LessonTopicDayBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        holder.binding.root.enableExpressiveCardShapeMorph()
        holder.binding.root.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position == RecyclerView.NO_POSITION) return@setOnClickListener
            val day = getItem(position)
            if (!expandedDays.add(day.date)) expandedDays.remove(day.date)
            submitList(groupLessonTopics(topics, expandedDays))
        }
        return holder
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val day = getItem(position)
        val b = holder.binding
        val context = b.root.context
        val date = Date.fromY_m_d(day.date)
        b.date.text = "${Week.getFullDayName(date.weekDay)}, ${date.formattedString}"
        b.count.text = context.resources.getQuantityString(R.plurals.lesson_topics_day_count, day.topics.size, day.topics.size)
        b.arrow.animate().cancel()
        b.arrow.rotation = if (day.expanded) 180f else 0f
        b.topics.isVisible = day.expanded
        ViewCompat.setStateDescription(b.root, context.getString(
            if (day.expanded) R.string.lesson_topics_expanded else R.string.lesson_topics_collapsed,
        ))
        b.topics.removeAllViews()
        if (!day.expanded) return
        day.topics.forEach { item ->
            val row = LessonTopicItemBinding.inflate(LayoutInflater.from(context), b.topics, false)
            row.heading.text = context.getString(R.string.lesson_topics_lesson_format, item.lessonNumber) + " • " + item.subjectName
            row.topic.text = item.topic.ifBlank { context.getString(R.string.lesson_topics_no_topic) }
            if (item.cancelled) row.heading.append(" • " + context.getString(R.string.lesson_topics_cancelled))
            row.teacher.text = item.teacherName
            row.root.enableExpressiveCardShapeMorph()
            row.root.setOnClickListener { onClick(item) }
            b.topics.addView(row.root)
        }
    }
}
