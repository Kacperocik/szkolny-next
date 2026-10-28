package pl.szczodrzynski.edziennik.ui.agenda

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.ext.resolveAttr
import pl.szczodrzynski.edziennik.utils.models.Date

data class MonthDayCell(
    val date: Date?,
    val dayNumber: String,
    val isCurrentMonth: Boolean,
    val isToday: Boolean,
    val eventColors: List<Int>,
    var isSelected: Boolean = false,
)

class MonthCalendarAdapter(
    private val context: Context,
    private val onDayLongClick: (Date) -> Unit,
    private val onDayClick: (MonthDayCell) -> Unit,
) : RecyclerView.Adapter<MonthCalendarAdapter.ViewHolder>() {

    val days = mutableListOf<MonthDayCell>()

    fun setDays(newDays: List<MonthDayCell>, selectedDate: Date) {
        days.clear()
        days.addAll(newDays.map {
            it.copy(isSelected = it.date?.stringY_m_d == selectedDate.stringY_m_d)
        })
        notifyDataSetChanged()
    }

    fun selectDate(selectedDate: Date) {
        val oldPos = days.indexOfFirst { it.isSelected }
        val newPos = days.indexOfFirst { it.date?.stringY_m_d == selectedDate.stringY_m_d }

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
        val view = LayoutInflater.from(parent.context).inflate(R.layout.row_agenda_month_day_cell, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = days.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val cell = days[position]

        if (cell.date == null) {
            holder.card.visibility = View.INVISIBLE
            return
        }

        holder.card.visibility = View.VISIBLE
        holder.dayNumber.text = cell.dayNumber

        holder.dotsContainer.removeAllViews()
        if (cell.eventColors.isNotEmpty()) {
            val displayColors = cell.eventColors.distinct().take(4)
            for (color in displayColors) {
                val dot = View(context).apply {
                    val size = (4 * context.resources.displayMetrics.density).toInt()
                    val margin = (1f * context.resources.displayMetrics.density).toInt()
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

        val primaryContainer = MaterialColors.getColor(holder.card, com.google.android.material.R.attr.colorPrimaryContainer, Color.BLUE)
        val onPrimaryContainer = MaterialColors.getColor(holder.card, com.google.android.material.R.attr.colorOnPrimaryContainer, Color.WHITE)
        val primaryColor = MaterialColors.getColor(holder.card, androidx.appcompat.R.attr.colorPrimary, Color.BLUE)
        val onSurface = MaterialColors.getColor(holder.card, android.R.attr.textColorPrimary, Color.BLACK)

        when {
            cell.isSelected && cell.isToday -> {
                holder.card.setCardBackgroundColor(primaryContainer)
                holder.card.strokeWidth = (2f * context.resources.displayMetrics.density).toInt()
                holder.card.strokeColor = primaryColor
                holder.dayNumber.setTextColor(onPrimaryContainer)
            }
            cell.isSelected -> {
                holder.card.setCardBackgroundColor(primaryContainer)
                holder.card.strokeWidth = 0
                holder.dayNumber.setTextColor(onPrimaryContainer)
            }
            cell.isToday -> {
                holder.card.setCardBackgroundColor(Color.TRANSPARENT)
                holder.card.strokeWidth = (1.5f * context.resources.displayMetrics.density).toInt()
                holder.card.strokeColor = primaryColor
                holder.dayNumber.setTextColor(primaryColor)
            }
            cell.isCurrentMonth -> {
                holder.card.setCardBackgroundColor(Color.TRANSPARENT)
                holder.card.strokeWidth = 0
                holder.dayNumber.setTextColor(onSurface)
            }
            else -> {
                holder.card.setCardBackgroundColor(Color.TRANSPARENT)
                holder.card.strokeWidth = 0
                val dimColor = MaterialColors.getColor(holder.card, R.attr.colorOutline, Color.GRAY)
                holder.dayNumber.setTextColor(dimColor)
            }
        }

        holder.card.setOnClickListener {
            selectDate(cell.date)
            onDayClick(cell)
        }
        holder.card.setOnLongClickListener {
            onDayLongClick(cell.date)
            true
        }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.monthDayCard)
        val dayNumber: TextView = view.findViewById(R.id.monthDayNumber)
        val dotsContainer: LinearLayout = view.findViewById(R.id.monthDayDotsContainer)
    }
}
