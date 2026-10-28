package pl.szczodrzynski.edziennik.ui.feedback

import android.content.BroadcastReceiver
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.api.events.FeedbackMessageEvent
import pl.szczodrzynski.edziennik.data.api.szkolny.SzkolnyApi
import pl.szczodrzynski.edziennik.data.db.entity.FeedbackMessage
import pl.szczodrzynski.edziennik.databinding.FragmentFeedbackBinding
import pl.szczodrzynski.edziennik.ext.onClick
import pl.szczodrzynski.edziennik.ui.base.fragment.BaseFragment
import pl.szczodrzynski.edziennik.utils.Utils.openUrl

class FeedbackFragment : BaseFragment<FragmentFeedbackBinding, AppCompatActivity>(
    inflater = FragmentFeedbackBinding::inflate,
) {

    private val chatAdapter = FeedbackChatAdapter()
    private val api by lazy { SzkolnyApi(app) }
    private var isDev = false
    private var currentDeviceId: String? = null

    private var receiver: BroadcastReceiver? = null

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun onFeedbackMessageEvent(event: FeedbackMessageEvent) {
        EventBus.getDefault().removeStickyEvent(event)
        val message = event.message
        if (currentDeviceId == null || message.deviceId == currentDeviceId) {
            chatAdapter.addMessage(message)
            if (chatAdapter.itemCount > 0) {
                b.chatRecyclerView.smoothScrollToPosition(chatAdapter.itemCount - 1)
            }
        } else {
            Toast.makeText(context, "${message.senderName}: Nowa wiadomość w innym wątku.", Toast.LENGTH_LONG).show()
        }
    }

    private fun launchDeviceSelection() {
        if (!isDev)
            return
        launch {
            val messages = withContext(Dispatchers.Default) { app.db.feedbackMessageDao().allWithCountNow }
            val popupMenu = PopupMenu(activity, b.targetDeviceDropDown)
            messages.forEachIndexed { index, m ->
                popupMenu.menu.add(0, index, index, "${m.senderName} (${m.deviceId}) - ${m.deviceName}")
            }
            popupMenu.setOnMenuItemClickListener { item ->
                b.targetDeviceDropDown.setText(item.title)
                chatAdapter.clear()
                val message = messages[item.itemId]
                currentDeviceId = message.deviceId
                this@FeedbackFragment.launch { loadMessages() }
                false
            }
            popupMenu.show()
        }
    }

    private suspend fun loadMessages(messageList: List<FeedbackMessage>? = null) {
        val messages = withContext(Dispatchers.Default) {
            if (currentDeviceId == null)
                messageList ?: app.db.feedbackMessageDao().allNow
            else
                messageList?.filter { it.deviceId == currentDeviceId } ?: app.db.feedbackMessageDao().getByDeviceIdNow(currentDeviceId!!)
        }

        if (messages.isNotEmpty()) {
            b.chatLayout.visibility = View.VISIBLE
            b.inputLayout.visibility = View.GONE
        }

        chatAdapter.setItems(messages)
        if (messages.isNotEmpty()) {
            b.chatRecyclerView.scrollToPosition(messages.size - 1)
        }
    }

    override suspend fun onViewReady(savedInstanceState: Bundle?) {
        EventBus.getDefault().removeStickyEvent(FeedbackMessageEvent::class.java)

        b.faqText.setOnClickListener { openFaq() }
        b.faqButton.setOnClickListener { openFaq() }

        val layoutManager = LinearLayoutManager(context).apply {
            stackFromEnd = true
        }
        b.chatRecyclerView.apply {
            this.layoutManager = layoutManager
            adapter = chatAdapter
        }

        val messages = withContext(Dispatchers.IO) {
            val messages = app.db.feedbackMessageDao().allNow
            isDev = App.devMode && messages.any { it.deviceId != null }
            messages
        }

        b.targetDeviceLayout.visibility = if (isDev) View.VISIBLE else View.GONE
        b.targetDeviceDropDown.onClick {
            launchDeviceSelection()
        }

        if (isDev) {
            messages.firstOrNull { it.received && it.devId == null }?.let {
                currentDeviceId = it.deviceId
                b.targetDeviceDropDown.setText("${it.senderName} (${it.deviceId}) - ${it.deviceName}")
            }
            // handle notification intent
            arguments?.getString("feedbackMessageDeviceId")?.let { deviceId ->
                messages.firstOrNull { it.received && it.deviceId == deviceId && it.devId == null }?.let {
                    currentDeviceId = deviceId
                    b.targetDeviceDropDown.setText("${it.senderName} (${it.deviceId}) - ${it.deviceName}")
                }
            }
            b.chatLayout.visibility = View.VISIBLE
            b.inputLayout.visibility = View.GONE
        } else if (messages.isEmpty()) {
            b.chatLayout.visibility = View.GONE
            b.inputLayout.visibility = View.VISIBLE
        }

        loadMessages(messages)

        b.sendButton.onClick {
            send(b.textInput.text?.toString())
        }
        b.chatSendButton.onClick {
            send(b.chatInputEditText.text?.toString())
        }
    }

    private fun send(text: String?) {
        if (text.isNullOrBlank()) {
            Toast.makeText(activity, "Podaj treść wiadomości.", Toast.LENGTH_SHORT).show()
            return
        }

        if (isDev && (currentDeviceId == null || currentDeviceId == "szkolny.eu")) {
            Toast.makeText(activity, "Wybierz urządzenie docelowe.", Toast.LENGTH_SHORT).show()
            return
        }

        launch {
            val message = api.runCatching(activity) {
                val message = api.sendFeedbackMessage(
                    senderName = App.profile.accountName ?: App.profile.studentNameLong,
                    targetDeviceId = if (isDev) currentDeviceId else null,
                    text = text
                )
                app.db.feedbackMessageDao().add(message)
                message
            } ?: return@launch

            if (message.text.startsWith("devmode")) {
                app.config.devModePassword = message.text.substringAfter("devmode")
                app.checkDevModePassword()
            }

            b.chatLayout.visibility = View.VISIBLE
            b.inputLayout.visibility = View.GONE

            b.textInput.text = null
            b.chatInputEditText.text = null

            chatAdapter.addMessage(message)
            if (chatAdapter.itemCount > 0) {
                b.chatRecyclerView.smoothScrollToPosition(chatAdapter.itemCount - 1)
            }
        }
    }

    private fun openFaq() {
        openUrl(activity, "http://szkolny.eu/pomoc/")
    }
}
