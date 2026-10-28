package pl.szczodrzynski.edziennik.ui.agenda

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.ext.resolveAttr
import pl.szczodrzynski.edziennik.utils.models.Date

data class WeekDayPill(
    val date: Date,
    val dayName: String,
    val dayNumber: String,
    val eventColors: List<Int>,
    var isSelected: Boolean = false,
)

class WeekDaysAdapter(
    private val context: Context,
    private val onDayLongClick: (Date) -> Unit,
    private val onDayClick: (WeekDayPill) -> Unit,
) : RecyclerView.Adapter<WeekDaysAdapter.ViewHolder>() {

    val days = mutableListOf<WeekDayPill>()

    fun setDays(newDays: List<WeekDayPill>, selectedDate: Date) {
        days.clear()
        days.addAll(newDays.map { it.copy(isSelected = it.date.stringY_m_d == selectedDate.stringY_m_d) })
        notifyDataSetChanged()
    }

    fun selectDate(selectedDate: Date) {
        val oldPos = days.indexOfFirst { it.isSelected }
        val newPos = days.indexOfFirst { it.date.stringY_m_d == selectedDate.stringY_m_d }

        if (oldPos == newPos) return

        if (oldPos >= 0) {
            days[oldPos].isSelected = false
            notifyItemChanged(oldPos)
        }
        if (newPos >= 0) {
            days[newPos].isSelected = true
            notifyItemChanged(newPos)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.row_agenda_day_pill, parent, false)
        val lm = (parent as? RecyclerView)?.layoutManager as? LinearLayoutManager
        if (lm?.orientation == LinearLayoutManager.VERTICAL) {
            // landscape: center the fixed-size pill in a full-width row
            view.layoutParams = LinearLayout.LayoutParams(52.dp, 64.dp).apply {
                setMargins(0, 4.dp, 0, 4.dp)
            }
            val wrapper = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                )
                addView(view)
            }
            return ViewHolder(wrapper)
        }
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = days.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val pill = days[position]

        holder.dayName.text = pill.dayName
        holder.dayNumber.text = pill.dayNumber

        holder.dotsContainer.removeAllViews()
        if (pill.eventColors.isNotEmpty()) {
            val displayColors = pill.eventColors.distinct().take(4)
            for (color in displayColors) {
                val dot = View(context).apply {
                    val size = (5 * context.resources.displayMetrics.density).toInt()
                    val margin = (1.5f * context.resources.displayMetrics.density).toInt()
                    layoutParams = LinearLayout.LayoutParams(size, size).apply {
                        setMargins(margin, 0, margin, 0)
                    }
                    background = ContextCompat.getDrawable(context, R.drawable.bg_circle)
                    val harmonized = MaterialColors.harmonizeWithPrimary(context, color)
                    backgroundTintList = ColorStateList.valueOf(harmonized)
                }
                holder.dotsContainer.addView(dot)
            }
        }

        val surfaceLow = MaterialColors.getColor(holder.card, com.google.android.material.R.attr.colorSurfaceContainerLow, Color.TRANSPARENT)
        val primaryContainer = MaterialColors.getColor(holder.card, com.google.android.material.R.attr.colorPrimaryContainer, Color.BLUE)
        val onPrimaryContainer = MaterialColors.getColor(holder.card, com.google.android.material.R.attr.colorOnPrimaryContainer, Color.WHITE)
        val primaryColor = MaterialColors.getColor(holder.card, androidx.appcompat.R.attr.colorPrimary, Color.BLUE)
        val onSurface = MaterialColors.getColor(holder.card, android.R.attr.textColorPrimary, Color.BLACK)
        val onSurfaceVariant = MaterialColors.getColor(holder.card, R.attr.colorSecondary, Color.GRAY)
        val isToday = pill.date.stringY_m_d == Date.getToday().stringY_m_d

        when {
            pill.isSelected && isToday -> {
                holder.card.setCardBackgroundColor(primaryContainer)
                holder.card.strokeWidth = 2.dp
                holder.card.strokeColor = primaryColor
                holder.dayName.setTextColor(onPrimaryContainer)
                holder.dayNumber.setTextColor(onPrimaryContainer)
            }
            pill.isSelected -> {
                holder.card.setCardBackgroundColor(primaryContainer)
                holder.card.strokeWidth = 0
                holder.dayName.setTextColor(onPrimaryContainer)
                holder.dayNumber.setTextColor(onPrimaryContainer)
            }
            isToday -> {
                holder.card.setCardBackgroundColor(surfaceLow)
                holder.card.strokeWidth = 1.5f.dp
                holder.card.strokeColor = primaryColor
                holder.dayName.setTextColor(primaryColor)
                holder.dayNumber.setTextColor(primaryColor)
            }
            else -> {
                holder.card.setCardBackgroundColor(surfaceLow)
                holder.card.strokeWidth = 1.dp
                holder.card.strokeColor = R.attr.colorOutlineVariant.resolveAttr(context)
                holder.dayName.setTextColor(onSurfaceVariant)
                holder.dayNumber.setTextColor(onSurface)
            }
        }

        holder.card.setOnClickListener {
            selectDate(pill.date)
            onDayClick(pill)
        }
        holder.card.setOnLongClickListener {
            onDayLongClick(pill.date)
            true
        }
    }

    private val Int.dp: Int
        get() = (this * context.resources.displayMetrics.density).toInt()

    private val Float.dp: Int
        get() = (this * context.resources.displayMetrics.density).toInt()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.dayPillCard)
        val dayName: TextView = view.findViewById(R.id.dayPillName)
        val dayNumber: TextView = view.findViewById(R.id.dayPillNumber)
        val dotsContainer: LinearLayout = view.findViewById(R.id.dayPillDotsContainer)
    }
}
