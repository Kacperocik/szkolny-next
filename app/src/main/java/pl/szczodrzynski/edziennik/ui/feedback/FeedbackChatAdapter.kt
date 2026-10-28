package pl.szczodrzynski.edziennik.ui.feedback

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import pl.szczodrzynski.edziennik.data.db.entity.FeedbackMessage
import pl.szczodrzynski.edziennik.databinding.ItemChatReceivedBinding
import pl.szczodrzynski.edziennik.databinding.ItemChatSentBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FeedbackChatAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_SENT = 1
        private const val TYPE_RECEIVED = 2
    }

    private val items = mutableListOf<FeedbackMessage>()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun setItems(newItems: List<FeedbackMessage>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun addMessage(message: FeedbackMessage) {
        items.add(message)
        notifyItemInserted(items.size - 1)
    }

    fun clear() {
        items.clear()
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position].received) TYPE_RECEIVED else TYPE_SENT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_SENT) {
            SentViewHolder(ItemChatSentBinding.inflate(inflater, parent, false))
        } else {
            ReceivedViewHolder(ItemChatReceivedBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = items[position]
        val timeStr = timeFormat.format(Date(msg.sentTime))
        if (holder is SentViewHolder) {
            holder.b.messageText.text = msg.text
            holder.b.messageTime.text = timeStr
        } else if (holder is ReceivedViewHolder) {
            holder.b.senderName.text = msg.senderName
            holder.b.messageText.text = msg.text
            holder.b.messageTime.text = timeStr
        }
    }

    override fun getItemCount() = items.size

    class SentViewHolder(val b: ItemChatSentBinding) : RecyclerView.ViewHolder(b.root)
    class ReceivedViewHolder(val b: ItemChatReceivedBinding) : RecyclerView.ViewHolder(b.root)
}
