/*
 * Copyright (c) Kuba Szczodrzyński 2020-4-5.
 */

package pl.szczodrzynski.edziennik.ui.messages.list

import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.shape.ShapeAppearanceModel
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.db.full.MessageFull
import pl.szczodrzynski.edziennik.databinding.MessagesListItemBinding
import pl.szczodrzynski.edziennik.ext.attachToastHint
import pl.szczodrzynski.edziennik.ext.dp
import pl.szczodrzynski.edziennik.ext.detachToastHint
import pl.szczodrzynski.edziennik.ext.enableExpressiveCardShapeMorph
import pl.szczodrzynski.edziennik.ext.onClick
import pl.szczodrzynski.edziennik.ext.resolveAttr
import pl.szczodrzynski.edziennik.ext.toDrawable
import pl.szczodrzynski.edziennik.ui.grades.viewholder.BindableViewHolder
import pl.szczodrzynski.edziennik.ui.messages.MessagesUtils
import pl.szczodrzynski.edziennik.core.manager.NoteManager
import pl.szczodrzynski.edziennik.ui.search.SearchableAdapter
import pl.szczodrzynski.edziennik.utils.models.Date
import pl.szczodrzynski.navlib.enableExpressiveImageShapeMorph

class MessageViewHolder(
    inflater: LayoutInflater,
    parent: ViewGroup,
    val b: MessagesListItemBinding = MessagesListItemBinding.inflate(inflater, parent, false),
) : RecyclerView.ViewHolder(b.root), BindableViewHolder<MessageFull, MessagesAdapter> {
    companion object {
        private const val TAG = "MessageViewHolder"
    }

    override fun onBind(
        activity: AppCompatActivity,
        app: App,
        item: MessageFull,
        position: Int,
        adapter: MessagesAdapter,
    ) {
        b.messageDate.text = Date.fromMillis(item.addedDate).formattedStringShort
        b.messageAttachmentImage.isVisible = item.hasAttachments

        b.messageBody.text = item.bodyHtml?.take(200)

        val isRead = item.isSent || item.isDraft || item.seen
        val typeface = if (isRead) adapter.typefaceNormal else adapter.typefaceBold
        val textColor = if (isRead)
            R.attr.colorOnSurfaceVariant.resolveAttr(b.root.context)
        else
            R.attr.colorOnSurface.resolveAttr(b.root.context)
        // set text styles
        b.messageSender.setTextColor(textColor)
        b.messageSender.typeface = typeface
        b.messageSubject.setTextColor(textColor)
        b.messageSubject.typeface = typeface
        b.messageDate.setTextColor(R.attr.colorSecondary.resolveAttr(b.root.context))
        b.messageDate.typeface = typeface

        if (adapter.onStarClick == null) {
            b.messageStar.isVisible = false
        }
        b.messageStar.detachToastHint()

        val messageInfo = MessagesUtils.getMessageInfo(app, item, 48, 24, 18, 12)
        if (adapter.isSelected(item)) {
            b.messageProfileBackground.setImageDrawable(
                CommunityMaterial.Icon.cmd_check_circle.toDrawable(
                    activity,
                    colorAttr = R.attr.colorPrimary,
                    sizeDp = 40,
                )
            )
            b.messageItemCard.setCardBackgroundColor(R.attr.colorPrimaryContainer.resolveAttr(activity))
        } else if (adapter.activeMessageId == item.id) {
            b.messageProfileBackground.setImageBitmap(messageInfo.profileImage)
            b.messageItemCard.setCardBackgroundColor(R.attr.colorSecondaryContainer.resolveAttr(activity))
        } else {
            b.messageProfileBackground.setImageBitmap(messageInfo.profileImage)
            val cardBg = if (adapter.embedded) R.attr.colorCardNested else if (isRead) R.attr.colorCard else R.attr.colorCardUnread
            b.messageItemCard.setCardBackgroundColor(cardBg.resolveAttr(b.root.context))
        }
        b.messageItemCard.strokeWidth = 0

        val colorHighlight = R.attr.colorControlHighlight.resolveAttr(activity)
        b.messageSubject.text = adapter.highlightSearchText(
            item = item,
            text = item.subject,
            color = colorHighlight
        )
        b.messageSender.text = adapter.highlightSearchText(
            item = item,
            text = messageInfo.profileName ?: "",
            color = colorHighlight
        )

        val firstContentIndex = if (adapter.items.isNotEmpty() && adapter.getItemViewType(0) == SearchableAdapter.ITEM_TYPE_SEARCH) 1 else 0
        val lastContentIndex = adapter.items.size - 1

        val isFirst = position == firstContentIndex
        val isLast = position == lastContentIndex

        val shapeOverlay = when {
            isFirst && isLast -> R.style.ShapeAppearance_Material3_Corner_GroupSingle
            isFirst -> R.style.ShapeAppearance_Material3_Corner_GroupTop
            isLast -> R.style.ShapeAppearance_Material3_Corner_GroupBottom
            else -> R.style.ShapeAppearance_Material3_Corner_GroupMiddle
        }
        b.messageItemCard.shapeAppearanceModel = ShapeAppearanceModel.builder(b.root.context, shapeOverlay, 0).build()
        b.messageItemCard.enableExpressiveCardShapeMorph()
        b.messageProfileBackground.enableExpressiveImageShapeMorph(pressedCornerRadiusDp = 10f)

        if (adapter.showNotes)
            NoteManager.prependIcon(item, b.messageSubject)

        if (adapter.onMessageClick != null || adapter.onSelectionChanged != null) {
            b.messageItemCard.onClick {
                if (adapter.isSelectionMode)
                    adapter.toggleSelection(item)
                else
                    adapter.onMessageClick?.invoke(item)
            }
        }
        if (adapter.onSelectionChanged != null) {
            b.messageItemCard.setOnLongClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                adapter.toggleSelection(item)
                true
            }
            b.messageProfileBackground.onClick {
                adapter.toggleSelection(item)
            }
        } else {
            b.messageItemCard.setOnLongClickListener(null)
            b.messageProfileBackground.setOnClickListener(null)
            b.messageProfileBackground.isClickable = false
        }
        adapter.onStarClick?.let { listener ->
            b.messageStar.isVisible = true
            adapter.manager.setStarIcon(b.messageStar, item)
            b.messageStar.onClick { listener(item) }
            b.messageStar.attachToastHint(R.string.hint_message_star)
        }
    }
}
