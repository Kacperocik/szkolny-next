package pl.szczodrzynski.edziennik.ui.excuses

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.db.entity.LibrusExcuse
import pl.szczodrzynski.edziennik.databinding.ExcuseListItemBinding
import pl.szczodrzynski.edziennik.ui.grades.viewholder.BindableViewHolder

class ExcuseViewHolder(
    inflater: LayoutInflater,
    parent: ViewGroup,
    val b: ExcuseListItemBinding = ExcuseListItemBinding.inflate(inflater, parent, false),
) : RecyclerView.ViewHolder(b.root), BindableViewHolder<LibrusExcuse, ExcuseListAdapter> {

    override fun onBind(
        activity: AppCompatActivity,
        app: App,
        item: LibrusExcuse,
        position: Int,
        adapter: ExcuseListAdapter,
    ) {
        b.excusePeriod.text = when {
            item.isRange -> activity.getString(
                R.string.excuses_period_range_format,
                item.dateFrom.formattedString,
                item.dateTo.formattedString,
            )
            item.isWholeDays -> item.dateFrom.formattedString
            else -> activity.getString(
                R.string.excuses_period_lessons_format,
                item.dateFrom.formattedString,
                item.lessonNumbers.joinToString(", "),
            )
        }

        // Librus does not require a message, so an empty one is normal - just hide the row
        b.excuseMessage.isVisible = item.message.isNotBlank()
        b.excuseMessage.text = item.message

        val (statusRes, statusColor) = when (item.status) {
            LibrusExcuse.Status.UNKNOWN -> R.string.excuses_status_unknown to 0xff9e9e9e.toInt()
            LibrusExcuse.Status.SEND -> R.string.excuses_status_send to 0xffffa000.toInt()
            LibrusExcuse.Status.ACCEPT -> R.string.excuses_status_accept to 0xff00c853.toInt()
            LibrusExcuse.Status.REJECT -> R.string.excuses_status_reject to 0xffff3d00.toInt()
        }
        b.excuseStatus.setText(statusRes)
        b.excuseStatus.setTextColor(statusColor)

        b.root.setOnClickListener { adapter.onExcuseClick?.invoke(item) }

        b.attachmentIcon.isVisible = item.hasAttachment
        if (item.hasAttachment) {
            b.attachmentIcon.setImageDrawable(
                IconicsDrawable(activity, CommunityMaterial.Icon.cmd_attachment).apply {
                    colorInt = b.excuseDetails.currentTextColor
                    sizeDp = 18
                }
            )
        }

        b.excuseDetails.isVisible = true
        b.excuseDetails.text = activity.getString(
            R.string.excuses_justified_absences_format,
            item.justifiedAbsences,
        )
    }
}
