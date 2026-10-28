/*
 * Copyright (c) Kuba Szczodrzyński 2021-10-23.
 */

package pl.szczodrzynski.edziennik.ui.notes

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.shape.ShapeAppearanceModel
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.db.entity.Note
import pl.szczodrzynski.edziennik.databinding.NoteListItemBinding
import pl.szczodrzynski.edziennik.ext.*
import pl.szczodrzynski.edziennik.ui.grades.viewholder.BindableViewHolder
import pl.szczodrzynski.edziennik.ui.search.SearchableAdapter
import pl.szczodrzynski.edziennik.utils.models.Date

class NoteViewHolder(
    inflater: LayoutInflater,
    parent: ViewGroup,
    val b: NoteListItemBinding = NoteListItemBinding.inflate(inflater, parent, false),
) : RecyclerView.ViewHolder(b.root), BindableViewHolder<Note, NoteListAdapter> {
    companion object {
        private const val TAG = "NoteViewHolder"
    }

    override fun onBind(
        activity: AppCompatActivity,
        app: App,
        item: Note,
        position: Int,
        adapter: NoteListAdapter,
    ) {
        val colorHighlight = R.attr.colorControlHighlight.resolveAttr(activity)
        val addedDate = Date.fromMillis(item.addedDate).formattedString

        b.topic.text = adapter.highlightSearchText(
            item = item,
            text = item.topicHtml ?: item.bodyHtml,
            color = colorHighlight,
        )

        if (item.color != null) {
            val cardBgColor = ColorUtils.setAlphaComponent(item.color.toInt(), 0x50)
            b.noteItemCard.setCardBackgroundColor(cardBgColor)
        } else {
            val cardAttr = if (adapter.embedded) R.attr.colorCardNested else R.attr.colorCard
            b.noteItemCard.setCardBackgroundColor(cardAttr.resolveAttr(b.root.context))
        }
        b.colorLayout.background = null

        if (adapter.activeNoteId != null && item.id == adapter.activeNoteId) {
            b.noteItemCard.strokeWidth = 2.dp
            b.noteItemCard.strokeColor = R.attr.colorPrimary.resolveAttr(activity)
            if (item.color == null) {
                b.noteItemCard.setCardBackgroundColor(R.attr.colorSecondaryContainer.resolveAttr(activity))
            }
        } else {
            b.noteItemCard.strokeWidth = 0
            if (item.color == null) {
                val cardAttr = if (adapter.embedded) R.attr.colorCardNested else R.attr.colorCard
                b.noteItemCard.setCardBackgroundColor(cardAttr.resolveAttr(b.root.context))
            }
        }

        if (item.sharedBy != null && item.sharedByName != null) {
            b.addedBy.text = listOf<CharSequence>(
                "{cmd-share-variant}",
                item.sharedByName,
                "•",
                addedDate,
            ).concat(" ")

            // workaround for the span data lost during setText above
            val sharedBySpanned = adapter.highlightSearchText(
                item = item,
                text = item.sharedByName,
                color = colorHighlight,
            )
            b.addedBy.text = b.addedBy.text.replaceSpanned(item.sharedByName, sharedBySpanned)
        } else {
            b.addedBy.setText(R.string.notes_added_by_you_format, addedDate)
        }

        b.addedBy.setTextColor(R.attr.colorSecondary.resolveAttr(b.root.context))

        val prevType = if (position > 0) adapter.getItemViewType(position - 1) else -1
        val nextType = if (position < adapter.itemCount - 1) adapter.getItemViewType(position + 1) else -1

        val isFirst = prevType == SearchableAdapter.ITEM_TYPE_SEARCH || prevType == NoteListAdapter.ITEM_TYPE_CATEGORY || prevType == -1
        val isLast = nextType == NoteListAdapter.ITEM_TYPE_CATEGORY || nextType == -1

        val shapeOverlay = when {
            isFirst && isLast -> R.style.ShapeAppearance_Material3_Corner_GroupSingle
            isFirst -> R.style.ShapeAppearance_Material3_Corner_GroupTop
            isLast -> R.style.ShapeAppearance_Material3_Corner_GroupBottom
            else -> R.style.ShapeAppearance_Material3_Corner_GroupMiddle
        }
        b.noteItemCard.shapeAppearanceModel = ShapeAppearanceModel.builder(b.root.context, shapeOverlay, 0).build()
        b.noteItemCard.clipToOutline = true
        b.noteItemCard.invalidateOutline()
        b.noteItemCard.enableExpressiveCardShapeMorph()

        b.editButton.isVisible = item.canEdit && adapter.onNoteEditClick != null

        if (adapter.onNoteClick != null)
            b.noteItemCard.onClick {
                adapter.onNoteClick.invoke(item)
            }
        if (adapter.onNoteEditClick != null) {
            b.editButton.enableExpressiveShapeMorph()
            b.editButton.onClick {
                adapter.onNoteEditClick.invoke(item)
            }
        }
    }
}
