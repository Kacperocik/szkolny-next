package pl.szczodrzynski.edziennik.ui.timetable

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.shape.ShapeAppearanceModel
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.db.entity.Subject
import pl.szczodrzynski.edziennik.databinding.DialogSubjectColorsItemBinding
import pl.szczodrzynski.edziennik.ext.onClick
import pl.szczodrzynski.edziennik.ext.setTintColor
import pl.szczodrzynski.edziennik.ext.enableExpressiveCardShapeMorph
import pl.szczodrzynski.edziennik.ui.dialogs.ColorPickerDialog
import pl.szczodrzynski.navlib.enableExpressiveImageShapeMorph

class SubjectColorsAdapter(
    private val activity: AppCompatActivity,
) : RecyclerView.Adapter<SubjectColorsAdapter.ViewHolder>() {

    private val app = activity.applicationContext as App
    var items = listOf<Subject>()

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val b = DialogSubjectColorsItemBinding.inflate(inflater, parent, false)
        b.color.enableExpressiveImageShapeMorph(pressedCornerRadiusDp = 8f)
        return ViewHolder(b)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val b = holder.b
        val isFirst = position == 0
        val isLast = position == itemCount - 1
        val shapeOverlay = when {
            isFirst && isLast -> R.style.ShapeAppearance_Material3_Corner_GroupSingle
            isFirst -> R.style.ShapeAppearance_Material3_Corner_GroupTop
            isLast -> R.style.ShapeAppearance_Material3_Corner_GroupBottom
            else -> R.style.ShapeAppearance_Material3_Corner_GroupMiddle
        }
        b.root.shapeAppearanceModel = ShapeAppearanceModel.builder(activity, shapeOverlay, 0).build()
        b.root.enableExpressiveCardShapeMorph()

        b.name.text = item.longName
        val color = app.timetableManager.getSubjectColor(item.id, item.longName)
        b.color.background.setTintColor(color)

        val openPicker = {
            val subjectId = item.id
            ColorPickerDialog(activity, color) { selectedColor ->
                val colors = app.profile.config.ui.timetableSubjectColors.deepCopy()
                colors.addProperty(subjectId.toString(), selectedColor)
                app.profile.config.ui.timetableSubjectColors = colors
                val index = items.indexOfFirst { it.id == subjectId }
                if (index >= 0) notifyItemChanged(index)
            }.show()
        }
        b.root.onClick { openPicker() }
        b.color.onClick { openPicker() }
    }

    class ViewHolder(val b: DialogSubjectColorsItemBinding) : RecyclerView.ViewHolder(b.root)
}
