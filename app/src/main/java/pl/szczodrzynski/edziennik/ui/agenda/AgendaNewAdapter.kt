package pl.szczodrzynski.edziennik.ui.agenda

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.mikepenz.iconics.view.IconicsTextView
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.db.full.EventFull
import pl.szczodrzynski.edziennik.databinding.AgendaCounterItemBinding
import pl.szczodrzynski.edziennik.databinding.AgendaGroupItemBinding
import pl.szczodrzynski.edziennik.ext.enableExpressiveCardShapeMorph
import pl.szczodrzynski.edziennik.ext.join
import pl.szczodrzynski.edziennik.ext.resolveAttr
import pl.szczodrzynski.edziennik.ext.setTintColor
import pl.szczodrzynski.edziennik.ui.agenda.event.AgendaEventGroup
import pl.szczodrzynski.edziennik.ui.agenda.lessonchanges.LessonChangesEvent
import pl.szczodrzynski.edziennik.ui.agenda.lessonchanges.LessonChangesEventRenderer
import pl.szczodrzynski.edziennik.ui.agenda.teacherabsence.TeacherAbsenceEvent
import pl.szczodrzynski.edziennik.ui.agenda.teacherabsence.TeacherAbsenceEventRenderer
import pl.szczodrzynski.edziennik.utils.Colors
import pl.szczodrzynski.edziennik.utils.models.Date

sealed class AgendaListItem {
    data class DayHeader(val date: Date, val title: String) : AgendaListItem()
    data class Event(val event: EventFull) : AgendaListItem()
    data class Group(val group: AgendaEventGroup) : AgendaListItem()
    data class Counter(val type: CounterType, val count: Int, val date: Date, val showBadge: Boolean) : AgendaListItem()
    data class Empty(val date: Date) : AgendaListItem()

    enum class CounterType {
        LESSON_CHANGES,
        TEACHER_ABSENCE
    }
}

class AgendaNewAdapter(
    private val context: Context,
    private val app: App,
    private val onEventClick: (EventFull) -> Unit,
    private val onCounterClick: (AgendaListItem.Counter) -> Unit,
    private val onGroupClick: (AgendaEventGroup) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_EVENT = 1
        private const val TYPE_COUNTER = 2
        private const val TYPE_EMPTY = 3
        private const val TYPE_GROUP = 4
    }

    val items = mutableListOf<AgendaListItem>()

    fun setItems(newItems: List<AgendaListItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun getPositionForDate(date: Date): Int {
        return items.indexOfFirst {
            (it is AgendaListItem.DayHeader && it.date.stringY_m_d == date.stringY_m_d) ||
            (it is AgendaListItem.Event && it.event.date.stringY_m_d == date.stringY_m_d) ||
            (it is AgendaListItem.Group && it.group.date.stringY_m_d == date.stringY_m_d) ||
            (it is AgendaListItem.Counter && it.date.stringY_m_d == date.stringY_m_d)
        }
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is AgendaListItem.DayHeader -> TYPE_HEADER
        is AgendaListItem.Event -> TYPE_EVENT
        is AgendaListItem.Group -> TYPE_GROUP
        is AgendaListItem.Counter -> TYPE_COUNTER
        is AgendaListItem.Empty -> TYPE_EMPTY
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(inflater.inflate(R.layout.row_agenda_new_day_header, parent, false))
            TYPE_EVENT -> EventViewHolder(inflater.inflate(R.layout.row_agenda_new_event, parent, false))
            TYPE_GROUP -> GroupViewHolder(inflater.inflate(R.layout.row_agenda_new_group, parent, false))
            TYPE_COUNTER -> CounterViewHolder(inflater.inflate(R.layout.row_agenda_new_counter, parent, false))
            else -> EmptyViewHolder(inflater.inflate(R.layout.row_agenda_new_empty_day, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is AgendaListItem.DayHeader -> (holder as HeaderViewHolder).bind(item)
            is AgendaListItem.Event -> (holder as EventViewHolder).bind(item)
            is AgendaListItem.Group -> (holder as GroupViewHolder).bind(item)
            is AgendaListItem.Counter -> (holder as CounterViewHolder).bind(item)
            is AgendaListItem.Empty -> (holder as EmptyViewHolder).bind(item)
        }
    }

    inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.dayHeaderTitle)
        fun bind(item: AgendaListItem.DayHeader) {
            title.text = item.title
            val isToday = item.date.stringY_m_d == Date.getToday().stringY_m_d
            val textColor = if (isToday) {
                MaterialColors.getColor(title, androidx.appcompat.R.attr.colorPrimary, Color.BLUE)
            } else {
                MaterialColors.getColor(title, R.attr.colorSecondary, Color.GRAY)
            }
            title.setTextColor(textColor)
        }
    }

    inner class EventViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val card: MaterialCardView = view.findViewById(R.id.card)
        private val title: IconicsTextView = view.findViewById(R.id.title)
        private val subtitle: TextView = view.findViewById(R.id.subtitle)
        private val accentIndicator: View? = view.findViewById(R.id.accentIndicator)
        private val badgeBackground: View = view.findViewById(R.id.badgeBackground)
        private val badge: View = view.findViewById(R.id.badge)

        fun bind(item: AgendaListItem.Event) {
            val event = item.event
            val harmonizedColor = MaterialColors.harmonizeWithPrimary(context, app.eventManager.getEventColor(event))
            val isClassic = app.profile.config.ui.agendaClassicCards
            val isCompact = app.profile.config.ui.agendaCompactMode

            val timeText = if (event.time == null)
                context.getString(R.string.agenda_event_all_day)
            else
                event.time!!.stringHM

            val agendaSubjectImportant = app.profile.config.ui.agendaSubjectImportant
            val eventSubtitle = listOfNotNull(
                timeText,
                event.subjectLongName.takeIf { !agendaSubjectImportant },
                event.typeName.takeIf { agendaSubjectImportant },
                event.teacherName,
                event.teamName
            ).join(", ")

            if (isClassic) {
                val textColor = Colors.legibleTextColor(harmonizedColor)
                card.setCardBackgroundColor(harmonizedColor)
                card.radius = (8f * context.resources.displayMetrics.density)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    card.foreground = ContextCompat.getDrawable(context, R.drawable.bg_rounded_8dp_outline)
                    card.foreground?.setTintColor(harmonizedColor)
                }
                accentIndicator?.visibility = View.GONE

                app.eventManager.setEventTopic(
                    title = title,
                    event = event,
                    doneIconColor = textColor,
                    showType = !agendaSubjectImportant,
                    showSubject = agendaSubjectImportant,
                )
                title.setTextColor(textColor)
                subtitle.text = eventSubtitle
                subtitle.setTextColor(textColor)
            } else {
                val surfaceContainer = MaterialColors.getColor(card, R.attr.colorCard, 0x1a808080)
                val primaryText = MaterialColors.getColor(card, android.R.attr.textColorPrimary, Color.BLACK)
                val secondaryText = MaterialColors.getColor(card, R.attr.colorSecondary, Color.GRAY)

                card.setCardBackgroundColor(surfaceContainer)
                card.radius = (16f * context.resources.displayMetrics.density)
                card.strokeWidth = 0
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val typedValue = TypedValue()
                    context.theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)
                    card.foreground = ContextCompat.getDrawable(context, typedValue.resourceId)
                }

                if (accentIndicator != null) {
                    accentIndicator.visibility = View.VISIBLE
                    accentIndicator.background?.setTintColor(harmonizedColor)
                }

                app.eventManager.setEventTopic(
                    title = title,
                    event = event,
                    doneIconColor = primaryText,
                    showType = !agendaSubjectImportant,
                    showSubject = agendaSubjectImportant,
                )
                title.setTextColor(primaryText)
                subtitle.text = eventSubtitle
                subtitle.setTextColor(secondaryText)
            }

            subtitle.isVisible = !isCompact
            title.maxLines = if (isCompact) 2 else 3
            if (accentIndicator != null) {
                val params = accentIndicator.layoutParams
                params.height = ((if (isCompact) 20 else 32) * context.resources.displayMetrics.density).toInt()
                accentIndicator.layoutParams = params
            }

            badgeBackground.isVisible = !event.seen && isClassic
            badgeBackground.background?.setTintColor(
                android.R.attr.colorBackground.resolveAttr(context)
            )
            badge.isVisible = !event.seen

            card.isClickable = true
            card.isFocusable = true
            card.setOnClickListener { onEventClick(event) }
            card.enableExpressiveCardShapeMorph()
        }
    }

    inner class GroupViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val card: MaterialCardView = view.findViewById(R.id.card)
        private val name: TextView = view.findViewById(R.id.name)
        private val count: TextView = view.findViewById(R.id.count)
        private val badge: View = view.findViewById(R.id.badge)

        fun bind(item: AgendaListItem.Group) {
            val group = item.group
            val isClassic = app.profile.config.ui.agendaClassicCards

            if (isClassic) {
                val bgColor = android.R.attr.colorBackground.resolveAttr(context)
                card.setCardBackgroundColor(group.typeColor)
                card.radius = (8f * context.resources.displayMetrics.density)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    card.foreground = ContextCompat.getDrawable(context, R.drawable.bg_rounded_8dp_outline)
                    card.foreground?.setTintColor(group.typeColor)
                }
                name.text = group.typeName
                name.setTextColor(Colors.legibleTextColor(group.typeColor))
                count.text = group.count.toString()
                count.background = ContextCompat.getDrawable(context, R.drawable.bg_rounded_8dp)
                count.background?.setTintColor(bgColor)
                count.setTextColor(Colors.legibleTextColor(bgColor))
            } else {
                val surfaceContainer = MaterialColors.getColor(card, R.attr.colorCard, 0x1a808080)
                val primaryText = MaterialColors.getColor(card, android.R.attr.textColorPrimary, Color.BLACK)

                card.setCardBackgroundColor(surfaceContainer)
                card.radius = (16f * context.resources.displayMetrics.density)
                card.strokeWidth = 0
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val typedValue = TypedValue()
                    context.theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)
                    card.foreground = ContextCompat.getDrawable(context, typedValue.resourceId)
                }
                name.text = group.typeName
                name.setTextColor(primaryText)
                count.text = group.count.toString()
                count.background = ContextCompat.getDrawable(context, R.drawable.bg_rounded_16dp)
                count.background?.setTintColor(group.typeColor)
                count.setTextColor(Colors.legibleTextColor(group.typeColor))
            }

            badge.isVisible = group.showBadge
            card.isClickable = true
            card.isFocusable = true
            card.setOnClickListener { onGroupClick(group) }
            card.enableExpressiveCardShapeMorph()
        }
    }

    inner class CounterViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val card: MaterialCardView = view.findViewById(R.id.card)
        private val name: TextView = view.findViewById(R.id.name)
        private val count: TextView = view.findViewById(R.id.count)
        private val badgeBackground: View = view.findViewById(R.id.badgeBackground)
        private val badge: View = view.findViewById(R.id.badge)

        fun bind(item: AgendaListItem.Counter) {
            val isClassic = app.profile.config.ui.agendaClassicCards
            val color = if (item.type == AgendaListItem.CounterType.LESSON_CHANGES)
                app.eventManager.getLessonChangesColor()
            else
                app.eventManager.getTeacherAbsenceColor()
            val harmonizedColor = MaterialColors.harmonizeWithPrimary(context, color)

            if (isClassic) {
                val textColor = Colors.legibleTextColor(harmonizedColor)
                val bgColor = android.R.attr.colorBackground.resolveAttr(context)
                card.setCardBackgroundColor(harmonizedColor)
                card.radius = (8f * context.resources.displayMetrics.density)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    card.foreground = ContextCompat.getDrawable(context, R.drawable.bg_rounded_8dp_outline)
                    card.foreground?.setTintColor(harmonizedColor)
                }
                name.setText(if (item.type == AgendaListItem.CounterType.LESSON_CHANGES) R.string.agenda_lesson_changes else R.string.agenda_teacher_absence)
                name.setTextColor(textColor)
                count.text = item.count.toString()
                count.background = ContextCompat.getDrawable(context, R.drawable.bg_rounded_8dp)
                count.background?.setTintColor(bgColor)
                count.setTextColor(Colors.legibleTextColor(bgColor))

                badgeBackground.isVisible = item.showBadge
                badgeBackground.background?.setTintColor(
                    android.R.attr.colorBackground.resolveAttr(context)
                )
                badge.isVisible = item.showBadge
            } else {
                val surfaceContainer = MaterialColors.getColor(card, R.attr.colorCard, 0x1a808080)
                val primaryText = MaterialColors.getColor(card, android.R.attr.textColorPrimary, Color.BLACK)

                card.setCardBackgroundColor(surfaceContainer)
                card.radius = (16f * context.resources.displayMetrics.density)
                card.strokeWidth = 0
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val typedValue = TypedValue()
                    context.theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)
                    card.foreground = ContextCompat.getDrawable(context, typedValue.resourceId)
                }
                name.setText(if (item.type == AgendaListItem.CounterType.LESSON_CHANGES) R.string.agenda_lesson_changes else R.string.agenda_teacher_absence)
                name.setTextColor(primaryText)
                count.text = item.count.toString()
                count.background = ContextCompat.getDrawable(context, R.drawable.bg_rounded_16dp)
                count.background?.setTintColor(harmonizedColor)
                count.setTextColor(Colors.legibleTextColor(harmonizedColor))

                badgeBackground.isVisible = false
                badge.isVisible = item.showBadge
            }

            card.isClickable = true
            card.isFocusable = true
            card.setOnClickListener { onCounterClick(item) }
            card.enableExpressiveCardShapeMorph()
        }
    }

    inner class EmptyViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val text: TextView = view.findViewById(R.id.emptyDayText)
        fun bind(item: AgendaListItem.Empty) {
            text.text = context.getString(R.string.dialog_day_no_events)
        }
    }
}
