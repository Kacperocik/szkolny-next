package pl.szczodrzynski.edziennik.ui.event

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.shape.ShapeAppearanceModel
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.core.manager.EventManager
import pl.szczodrzynski.edziennik.data.db.entity.EventType
import pl.szczodrzynski.edziennik.databinding.DialogSubjectColorsItemBinding
import pl.szczodrzynski.edziennik.ext.dp
import pl.szczodrzynski.edziennik.ext.enableExpressiveCardShapeMorph
import pl.szczodrzynski.edziennik.ext.onClick
import pl.szczodrzynski.edziennik.ext.setTintColor
import pl.szczodrzynski.edziennik.ui.dialogs.ColorPickerDialog
import pl.szczodrzynski.navlib.enableExpressiveImageShapeMorph

class EventColorsAdapter(
    private val activity: AppCompatActivity,
) : RecyclerView.Adapter<EventColorsAdapter.ViewHolder>() {

    sealed interface ColorRow {
        data class Special(
            val key: String,
            val nameRes: Int,
            val fallbackColor: Int,
        ) : ColorRow

        data class Event(val type: EventType) : ColorRow
    }

    private val app = activity.applicationContext as App
    var items = listOf<ColorRow>()

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val b = DialogSubjectColorsItemBinding.inflate(inflater, parent, false)
        b.color.enableExpressiveImageShapeMorph(pressedCornerRadiusDp = 8f)
        return ViewHolder(b)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val b = holder.b
        val isLast = position == itemCount - 1
        // first group (special rows) always occupies positions 0..1,
        // event types start a second group at position 2
        val shapeOverlay = when {
            position == 0 -> R.style.ShapeAppearance_Material3_Corner_GroupTop
            position == 1 -> R.style.ShapeAppearance_Material3_Corner_GroupBottom
            position == 2 -> R.style.ShapeAppearance_Material3_Corner_GroupTop
            isLast -> R.style.ShapeAppearance_Material3_Corner_GroupBottom
            else -> R.style.ShapeAppearance_Material3_Corner_GroupMiddle
        }
        b.root.shapeAppearanceModel = ShapeAppearanceModel.builder(activity, shapeOverlay, 0).build()
        b.root.enableExpressiveCardShapeMorph()
        (b.root.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
            it.bottomMargin = if (position == 1) 12.dp else 3.dp
            b.root.layoutParams = it
        }

        val (key, name, color, profileId) = when (val row = items[position]) {
            is ColorRow.Special -> Quad(
                row.key,
                activity.getString(row.nameRes),
                EventManager.getSpecialColor(app.profile.id, row.key, row.fallbackColor),
                app.profile.id
            )
            is ColorRow.Event -> Quad(
                row.type.id.toString(),
                row.type.name,
                app.eventManager.getEventColor(row.type.id, row.type.color, row.type.profileId),
                row.type.profileId
            )
        }

        b.name.text = name
        b.color.background.setTintColor(color)

        val openPicker = {
            ColorPickerDialog(activity, color) { selectedColor ->
                val colors = app.config[profileId].ui.agendaEventColors.deepCopy()
                colors.addProperty(key, selectedColor)
                app.config[profileId].ui.agendaEventColors = colors
                notifyItemChanged(holder.bindingAdapterPosition)
            }.show()
        }
        b.root.onClick { openPicker() }
        b.color.onClick { openPicker() }
    }

    class ViewHolder(val b: DialogSubjectColorsItemBinding) : RecyclerView.ViewHolder(b.root)

    private data class Quad(val key: String, val name: String, val color: Int, val profileId: Int)
}
