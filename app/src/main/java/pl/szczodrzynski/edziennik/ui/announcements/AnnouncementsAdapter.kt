package pl.szczodrzynski.edziennik.ui.announcements

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.shape.ShapeAppearanceModel
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.db.full.AnnouncementFull
import pl.szczodrzynski.edziennik.databinding.RowAnnouncementsItemBinding
import pl.szczodrzynski.edziennik.ext.enableExpressiveCardShapeMorph
import pl.szczodrzynski.edziennik.ext.resolveAttr
import pl.szczodrzynski.edziennik.ui.messages.MessagesUtils
import pl.szczodrzynski.edziennik.ui.search.SearchableAdapter
import pl.szczodrzynski.navlib.enableExpressiveImageShapeMorph

class AnnouncementsAdapter(
    val activity: AppCompatActivity,
    val embedded: Boolean = false,
    val onClick: ((v: View, announcement: AnnouncementFull) -> Unit)? = null,
) : SearchableAdapter<AnnouncementFull>() {
    override val searchResultsPluralRes = R.plurals.search_results_announcements

    companion object {
        private const val ITEM_TYPE_ANNOUNCEMENT = 0
    }

    var activeAnnouncementId: Long? = null

    fun setActiveAnnouncement(id: Long?) {
        if (activeAnnouncementId == id) return
        activeAnnouncementId = id
        notifyDataSetChanged()
    }

    override fun getItemViewType(item: AnnouncementFull) = ITEM_TYPE_ANNOUNCEMENT

    override fun onCreateViewHolder(
        inflater: LayoutInflater,
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {
        val binding = RowAnnouncementsItemBinding.inflate(inflater, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        item: AnnouncementFull
    ) {
        if (holder !is ViewHolder) return
        val b = holder.b
        val context = holder.itemView.context

        val firstContentIndex = if (items.isNotEmpty() && getItemViewType(0) == ITEM_TYPE_SEARCH) 1 else 0
        val lastContentIndex = items.size - 1

        val isFirst = position == firstContentIndex
        val isLast = position == lastContentIndex

        val shapeOverlay = when {
            isFirst && isLast -> R.style.ShapeAppearance_Material3_Corner_GroupSingle
            isFirst -> R.style.ShapeAppearance_Material3_Corner_GroupTop
            isLast -> R.style.ShapeAppearance_Material3_Corner_GroupBottom
            else -> R.style.ShapeAppearance_Material3_Corner_GroupMiddle
        }
        b.announcementsItemCard.shapeAppearanceModel = ShapeAppearanceModel.builder(context, shapeOverlay, 0).build()
        b.announcementsItemCard.enableExpressiveCardShapeMorph()
        b.announcementsItemImage.enableExpressiveImageShapeMorph(pressedCornerRadiusDp = 10f)

        if (item.id == activeAnnouncementId) {
            b.announcementsItemCard.setCardBackgroundColor(R.attr.colorSecondaryContainer.resolveAttr(context))
        } else {
            val cardAttr = if (embedded) R.attr.colorCardNested else R.attr.colorCard
            b.announcementsItemCard.setCardBackgroundColor(cardAttr.resolveAttr(context))
        }

        if (onClick != null) {
            b.announcementsItemCard.setOnClickListener { v -> onClick.invoke(v, item) }
        } else {
            b.announcementsItemCard.setOnClickListener(null)
        }

        b.announcementsItemSender.text = item.teacherName
        b.announcementsItemTitle.text = item.subject
        b.announcementsItemText.text = item.text

        if (item.endDate == null && item.startDate != null) {
            b.announcementsItemDate.text = item.startDate?.getFormattedString()
        } else if (item.startDate != null && item.endDate != null) {
            b.announcementsItemDate.text = context.getString(
                R.string.date_relative_format,
                item.startDate?.getFormattedStringShort(),
                item.endDate?.getFormattedStringShort()
            )
        } else {
            b.announcementsItemDate.text = ""
        }

        b.announcementsItemUnread.isVisible = !item.seen
        b.announcementsItemTitle.background = null
        if (!item.seen) {
            b.announcementsItemSender.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            b.announcementsItemTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        } else {
            b.announcementsItemSender.setTypeface(Typeface.DEFAULT, Typeface.NORMAL)
            b.announcementsItemTitle.setTypeface(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val profileImage = MessagesUtils.getProfileImage(48, 24, 18, 12, 1, item.teacherName)
        b.announcementsItemImage.setImageBitmap(profileImage)
    }

    class ViewHolder(val b: RowAnnouncementsItemBinding) : RecyclerView.ViewHolder(b.root)
}
