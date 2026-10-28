package pl.szczodrzynski.edziennik.ui.agenda.teacherabsence

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.ShapeAppearanceModel
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.db.full.TeacherAbsenceFull
import pl.szczodrzynski.edziennik.ext.enableExpressiveCardShapeMorph
import pl.szczodrzynski.edziennik.ui.messages.MessagesUtils
import pl.szczodrzynski.edziennik.utils.BetterLink
import pl.szczodrzynski.edziennik.utils.models.Date
import pl.szczodrzynski.navlib.enableExpressiveImageShapeMorph

class TeacherAbsenceAdapter(
    private val context: Context,
    private val date: Date,
    private val teacherAbsenceList: List<TeacherAbsenceFull>,
    private val onActionSelected: (() -> Unit)? = null,
) : RecyclerView.Adapter<TeacherAbsenceAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.row_dialog_teacher_absence_item, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int {
        return teacherAbsenceList.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val teacherAbsence = teacherAbsenceList[position]

        val isFirst = position == 0
        val isLast = position == teacherAbsenceList.size - 1

        val shapeOverlay = when {
            isFirst && isLast -> R.style.ShapeAppearance_Material3_Corner_GroupSingle
            isFirst -> R.style.ShapeAppearance_Material3_Corner_GroupTop
            isLast -> R.style.ShapeAppearance_Material3_Corner_GroupBottom
            else -> R.style.ShapeAppearance_Material3_Corner_GroupMiddle
        }
        holder.card.shapeAppearanceModel = ShapeAppearanceModel.builder(context, shapeOverlay, 0).build()
        holder.card.enableExpressiveCardShapeMorph()
        holder.image.enableExpressiveImageShapeMorph(pressedCornerRadiusDp = 10f)

        holder.card.setOnClickListener {
            BetterLink.showTeacherMenu(
                view = it,
                teacherId = teacherAbsence.teacherId,
                teacherName = teacherAbsence.teacherName ?: "",
                onActionSelected = onActionSelected
            )
        }

        holder.teacherAbsenceTeacher.text = teacherAbsence.teacherName

        val profileImage = MessagesUtils.getProfileImage(40, 20, 16, 10, 1, teacherAbsence.teacherName)
        holder.image.setImageBitmap(profileImage)

        val time = when (teacherAbsence.timeFrom != null && teacherAbsence.timeTo != null) {
            true -> when (teacherAbsence.dateFrom.compareTo(teacherAbsence.dateTo)) {
                0 -> teacherAbsence.dateFrom.formattedStringShort + " " +
                        teacherAbsence.timeFrom?.stringHM + " - " + teacherAbsence.timeTo?.stringHM

                else -> teacherAbsence.dateFrom.formattedStringShort + " " + teacherAbsence.timeTo?.stringHM +
                        " - " + teacherAbsence.dateTo.formattedStringShort + " " + teacherAbsence.timeTo?.stringHM
            }

            false -> when (teacherAbsence.dateFrom.compareTo(teacherAbsence.dateTo)) {
                0 -> teacherAbsence.dateFrom.formattedStringShort
                else -> teacherAbsence.dateFrom.formattedStringShort + " - " + teacherAbsence.dateTo.formattedStringShort
            }
        }

        holder.teacherAbsenceTime.text = time

        if (teacherAbsence.name != null) {
            holder.teacherAbsenceName.visibility = View.VISIBLE
            holder.teacherAbsenceName.text = teacherAbsence.name
        } else {
            holder.teacherAbsenceName.visibility = View.GONE
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView = itemView.findViewById(R.id.teacherAbsenceCard)
        val image: ImageView = itemView.findViewById(R.id.teacherAbsenceImage)
        val teacherAbsenceTeacher: TextView = itemView.findViewById(R.id.teacherAbsenceTeacher)
        val teacherAbsenceTime: TextView = itemView.findViewById(R.id.teacherAbsenceTime)
        val teacherAbsenceName: TextView = itemView.findViewById(R.id.teacherAbsenceName)
    }
}
