package pl.szczodrzynski.edziennik.ui.messages.list

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.db.entity.Teacher
import pl.szczodrzynski.edziennik.data.db.full.MessageFull
import pl.szczodrzynski.edziennik.ui.search.SearchableAdapter

class MessagesAdapter(
    val activity: AppCompatActivity,
    val teachers: List<Teacher>,
    val showNotes: Boolean = true,
    val embedded: Boolean = false,
    val onMessageClick: ((item: MessageFull) -> Unit)? = null,
    val onStarClick: ((item: MessageFull) -> Unit)? = null,
    /**
     * Enables selecting messages (by long-click or clicking the avatar), if not null.
     */
    val onSelectionChanged: ((count: Int) -> Unit)? = null,
) : SearchableAdapter<MessageFull>() {
    override val searchResultsPluralRes = R.plurals.search_results_messages
    companion object {
        private const val TAG = "MessagesAdapter"
        private const val ITEM_TYPE_MESSAGE = 0
    }

    private val app = activity.applicationContext as App

    // optional: place the manager here
    internal val manager
        get() = app.messageManager

    val typefaceNormal: Typeface by lazy { Typeface.create(Typeface.DEFAULT, Typeface.NORMAL) }
    val typefaceBold: Typeface by lazy { Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }

    override fun getItemViewType(item: MessageFull) = ITEM_TYPE_MESSAGE

    private val selectedIds = mutableSetOf<Long>()
    var activeMessageId: Long? = null

    fun setActiveMessage(messageId: Long?) {
        if (activeMessageId == messageId)
            return
        activeMessageId = messageId
        notifyMessagesChanged()
    }

    val isSelectionMode
        get() = selectedIds.isNotEmpty()

    val selectedCount
        get() = selectedIds.size

    val isAllSelected: Boolean
        get() {
            val messageItems = items.filterIsInstance<MessageFull>()
            return messageItems.isNotEmpty() && messageItems.all { it.id in selectedIds }
        }

    fun isSelected(item: MessageFull) = item.id in selectedIds

    // the list also contains the SearchField, so it must not be iterated as MessageFull
    fun getSelectedMessages() =
        allItems.filterIsInstance<MessageFull>().filter { it.id in selectedIds }

    fun toggleSelection(item: MessageFull) {
        if (!selectedIds.remove(item.id))
            selectedIds += item.id
        val index = items.indexOf(item)
        if (index != -1)
            notifyItemChanged(index)
        onSelectionChanged?.invoke(selectedIds.size)
    }

    fun selectAll() {
        items.filterIsInstance<MessageFull>().mapTo(selectedIds) { it.id }
        notifyMessagesChanged()
        onSelectionChanged?.invoke(selectedIds.size)
    }

    fun clearSelection() {
        if (selectedIds.isEmpty())
            return
        selectedIds.clear()
        notifyMessagesChanged()
        onSelectionChanged?.invoke(0)
    }

    /**
     * Rebinds the message items, without the [SearchField] (which would lose its state).
     */
    private fun notifyMessagesChanged() {
        for (position in 0 until itemCount) {
            if (getItemViewType(position) != ITEM_TYPE_SEARCH)
                notifyItemChanged(position)
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        item: MessageFull,
    ) {
        if (holder !is MessageViewHolder)
            return
        holder.onBind(activity, app, item, position, this)
    }

    override fun onCreateViewHolder(
        inflater: LayoutInflater,
        parent: ViewGroup,
        viewType: Int,
    ) = MessageViewHolder(inflater, parent)
}
