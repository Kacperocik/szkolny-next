package pl.szczodrzynski.edziennik.ui.notifications

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.db.entity.Notification
import pl.szczodrzynski.edziennik.databinding.NotificationsListItemBinding
import com.google.android.material.shape.ShapeAppearanceModel
import pl.szczodrzynski.edziennik.ext.asColoredSpannable
import pl.szczodrzynski.edziennik.ext.concat
import pl.szczodrzynski.edziennik.ext.enableExpressiveCardShapeMorph
import pl.szczodrzynski.edziennik.ext.onClick
import pl.szczodrzynski.edziennik.ext.resolveAttr
import pl.szczodrzynski.edziennik.ext.resolveString
import pl.szczodrzynski.edziennik.ext.toDrawable
import pl.szczodrzynski.edziennik.utils.models.Date
import kotlin.coroutines.CoroutineContext

class NotificationsAdapter(
        private val activity: AppCompatActivity,
        val onItemClick: ((item: Notification) -> Unit)? = null
) : RecyclerView.Adapter<NotificationsAdapter.ViewHolder>(), CoroutineScope {
    companion object {
        private const val TAG = "NotificationsAdapter"
    }

    private val app = activity.applicationContext as App
    // optional: place the manager here

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    var items = listOf<Notification>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(activity)
        val view = NotificationsListItemBinding.inflate(inflater, parent, false)
        return ViewHolder(view)
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
        b.notificationItemCard.shapeAppearanceModel = ShapeAppearanceModel.builder(activity, shapeOverlay, 0).build()
        b.notificationItemCard.enableExpressiveCardShapeMorph()

        val date = Date.fromMillis(item.addedDate).formattedString
        val colorSecondary = R.attr.colorSecondary.resolveAttr(activity)

        b.notificationIcon.background =
            item.type.icon.toDrawable(app, colorAttr = R.attr.colorPrimary)

        b.title.text = item.text
        b.profileDate.text = listOf(
                item.profileName ?: "",
                " • ",
                date
        ).concat().asColoredSpannable(colorSecondary)
        b.profileDate.setTextColor(colorSecondary)
        b.type.text = item.type.titleRes.resolveString(activity)

        onItemClick?.let { listener ->
            b.notificationItemCard.onClick { listener(item) }
        }
    }

    override fun getItemCount() = items.size

    class ViewHolder(val b: NotificationsListItemBinding) : RecyclerView.ViewHolder(b.root)
}
