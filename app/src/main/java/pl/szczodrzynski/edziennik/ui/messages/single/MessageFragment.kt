/*
 * Copyright (c) Kuba Szczodrzyński 2019-11-12.
 */

package pl.szczodrzynski.edziennik.ui.messages.single

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Bundle
import com.google.android.material.shape.MaterialShapeDrawable
import pl.szczodrzynski.edziennik.ext.resolveAttr
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.MainActivity
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.api.edziennik.EdziennikTask
import pl.szczodrzynski.edziennik.data.api.events.MessageGetEvent
import pl.szczodrzynski.edziennik.data.db.full.MessageFull
import pl.szczodrzynski.edziennik.data.enums.LoginType
import pl.szczodrzynski.edziennik.data.enums.NavTarget
import pl.szczodrzynski.edziennik.databinding.MessageFragmentBinding
import pl.szczodrzynski.edziennik.ext.Bundle
import pl.szczodrzynski.edziennik.ext.attachToastHint
import pl.szczodrzynski.edziennik.ext.copyToClipboard
import pl.szczodrzynski.edziennik.ext.enableExpressiveCardShapeMorph
import pl.szczodrzynski.edziennik.ext.enableExpressiveShapeMorph
import pl.szczodrzynski.edziennik.ext.setupExpressiveRowButtonSquish
import pl.szczodrzynski.edziennik.ext.get
import pl.szczodrzynski.edziennik.ext.isNotNullNorEmpty
import pl.szczodrzynski.edziennik.ext.onClick
import pl.szczodrzynski.edziennik.ext.plural
import pl.szczodrzynski.edziennik.ext.toDrawable
import pl.szczodrzynski.navlib.enableExpressiveImageShapeMorph
import pl.szczodrzynski.edziennik.ui.base.dialog.SimpleDialog
import pl.szczodrzynski.edziennik.ui.base.fragment.BaseFragment
import pl.szczodrzynski.edziennik.ui.base.fragment.setupFabHideOnScroll
import pl.szczodrzynski.edziennik.ui.dialogs.settings.MessagesConfigDialog
import pl.szczodrzynski.edziennik.ui.messages.MessagesUtils
import pl.szczodrzynski.edziennik.ui.messages.list.MessagesFragment
import pl.szczodrzynski.edziennik.ui.notes.setupNotesButton
import pl.szczodrzynski.edziennik.utils.Anim
import pl.szczodrzynski.edziennik.utils.BetterLink
import pl.szczodrzynski.edziennik.utils.html.BetterHtml
import pl.szczodrzynski.edziennik.utils.models.Date
import pl.szczodrzynski.edziennik.utils.models.Time
import pl.szczodrzynski.navlib.bottomsheet.items.BottomSheetPrimaryItem

class MessageFragment : BaseFragment<MessageFragmentBinding, MainActivity>(
    inflater = MessageFragmentBinding::inflate,
) {

    val isEmbedded: Boolean
        get() = parentFragment is MessagesFragment

    override fun getScrollingView() = b.content

    override fun getBottomSheetItems() = if (isEmbedded) emptyList() else listOf(
        BottomSheetPrimaryItem(true)
            .withTitle(R.string.menu_messages_config)
            .withIcon(CommunityMaterial.Icon.cmd_cog_outline)
            .withOnClickListener {
                activity.bottomSheet.close()
                MessagesConfigDialog(activity, false).show()
            }
    )

    private val manager
        get() = app.messageManager
    private lateinit var message: MessageFull

    override suspend fun onViewReady(savedInstanceState: Bundle?) {
        val barColor = R.attr.colorSurface.resolveAttr(activity)
        b.toolbar.background = MaterialShapeDrawable().apply {
            fillColor = ColorStateList.valueOf(barColor)
        }
        if (isEmbedded) {
            appBars.remove(activity.navView.toolbar)
        }
        appBars += b.toolbar
        b.closeButton.icon = CommunityMaterial.Icon.cmd_close.toDrawable(
            activity,
            colorAttr = R.attr.colorOnSecondaryContainer,
        )
        b.closeButton.enableExpressiveShapeMorph()
        b.closeButton.onClick {
            if (isEmbedded) {
                (parentFragment as? MessagesFragment)?.closeDetailMessage()
            } else {
                activity.navigateUp()
            }
        }

        b.content.setupFabHideOnScroll(
            isEnabled = { (parentFragment as? MessagesFragment)?.isTwoColumnMode == true },
            onHide = ::hideFab,
            onShow = ::showFab,
            onScrollState = { isScrolled = it },
        )

        // click to expand subject and sender
        b.subject.onClick {
            it.maxLines = if (it.maxLines == 30) 2 else 30
        }
        b.subject.setOnLongClickListener {
            val subject = b.subject.text?.toString()
            if (subject.isNullOrBlank())
                return@setOnLongClickListener false
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            subject.copyToClipboard(activity)
            Toast.makeText(activity, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
            true
        }
        b.senderCard.enableExpressiveCardShapeMorph(null, 250L, 250L, 200L)
        b.profileBackground.enableExpressiveImageShapeMorph(10f, 250L, 250L, 200L)
        b.senderCard.onClick {
            val senderName = b.sender.text?.toString()
            if (!senderName.isNullOrBlank()) {
                b.senderCard.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                senderName.copyToClipboard(activity)
                Toast.makeText(activity, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
            }
            b.sender.maxLines = if (b.sender.maxLines == 30) 2 else 30
        }

        b.messageStar.onClick {
            launch {
                manager.starMessage(message, !message.isStarred)
                manager.setStarIcon(b.messageStar, message)
            }
        }
        b.messageStar.attachToastHint(R.string.hint_message_star)

        b.replyButton.onClick {
            activity.navigate(navTarget = NavTarget.MESSAGE_COMPOSE, args = Bundle(
                    "message" to app.gson.toJson(message),
                    "type" to "reply"
            ))
        }
        b.forwardButton.onClick {
            activity.navigate(navTarget = NavTarget.MESSAGE_COMPOSE, args = Bundle(
                    "message" to app.gson.toJson(message),
                    "type" to "forward"
            ))
        }
        b.deleteButton.onClick {
            if (message.isDeleted) {
                launch {
                    manager.restore(message)
                    Toast.makeText(
                        activity,
                        activity.plural(R.plurals.messages_restored, 1),
                        Toast.LENGTH_SHORT
                    ).show()
                    updateActionButtons()
                }
                return@onClick
            }
            SimpleDialog<Unit>(activity) {
                title(R.string.messages_delete_confirmation)
                message(R.string.messages_delete_confirmation_text)
                positive(R.string.ok) {
                    manager.markAsDeleted(message)
                    Toast.makeText(
                        activity,
                        "Wiadomość przeniesiona do usuniętych",
                        Toast.LENGTH_SHORT
                    ).show()
                    if (isEmbedded) {
                        (parentFragment as? MessagesFragment)?.closeDetailMessage()
                    } else {
                        activity.navigateUp()
                    }
                }
                negative(R.string.cancel)
            }.show()
        }
        setupExpressiveRowButtonSquish(b.replyButton, b.forwardButton, b.deleteButton)
        b.downloadButton.isVisible = App.devMode
        b.downloadButton.icon = CommunityMaterial.Icon.cmd_download_outline.toDrawable(activity)
        b.downloadButton.onClick {
            EdziennikTask.messageGet(App.profileId, message).enqueue(activity)
        }

        message = manager.getMessage(App.profileId, arguments) ?: run {
            if (isEmbedded) {
                (parentFragment as? MessagesFragment)?.closeDetailMessage()
            } else {
                activity.navigateUp()
            }
            return
        }
        b.subject.text = message.subject
        checkMessage()
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun onMessageGetEvent(event: MessageGetEvent) {
        EventBus.getDefault().removeStickyEvent(event)
        // TODO remove this: message = event.message
        showMessage()
    }

    private fun checkMessage() {
        if (message.body == null) {
            EdziennikTask.messageGet(App.profileId, message).enqueue(activity)
            return
        }

        if (app.profile.loginStoreType == LoginType.IDZIENNIK) {
            val meta = "\\[META:([A-z0-9]+);([0-9-]+)]".toRegex().find(message.body!!)
            val messageIdBefore = meta?.get(2)?.toLong() ?: -1

            if (messageIdBefore == -1L) {
                EdziennikTask.messageGet(App.profileId, message).enqueue(activity)
                return
            }
        }

        if (app.data.messagesConfig.needsReadStatus) {
            // vulcan: change message status or download attachments
            if ((message.isReceived || message.isDeleted) && !message.seen || message.attachmentIds == null) {
                EdziennikTask.messageGet(App.profileId, message).enqueue(activity)
                return
            }
        }
        else if (!message.readByEveryone) {
            // if a sent msg is not read by everyone, download it again to check the read status
            EdziennikTask.messageGet(App.profileId, message).enqueue(activity)
            return
        }

        showMessage()
    }

    private fun showMessage() {
        b.body.text = MessagesUtils.htmlToSpannable(activity, message.body.toString())
        b.date.text = getString(R.string.messages_date_time_format, Date.fromMillis(message.addedDate).formattedStringShort, Time.fromMillis(message.addedDate).stringHM)

        val messageInfo = MessagesUtils.getMessageInfo(app, message, 40, 20, 14, 10)
        b.profileBackground.setImageBitmap(messageInfo.profileImage)
        b.sender.text = messageInfo.profileName

        b.subject.text = message.subject

        manager.setStarIcon(b.messageStar, message)

        updateActionButtons()

        val hasRecipients = !message.recipients.isNullOrEmpty()
        b.recipientsCard.isVisible = hasRecipients
        if (hasRecipients) {
            val messageRecipients = StringBuilder("<ul>")
            message.recipients?.forEach { recipient ->
                when (recipient.readDate) {
                    -1L -> messageRecipients.append(getString(
                            R.string.messages_recipients_list_unknown_state_format,
                            recipient.fullName
                    ))
                    0L -> messageRecipients.append(getString(
                            R.string.messages_recipients_list_unread_format,
                            recipient.fullName
                    ))
                    1L -> messageRecipients.append(getString(
                            R.string.messages_recipients_list_read_unknown_date_format,
                            recipient.fullName
                    ))
                    else -> messageRecipients.append(getString(
                            R.string.messages_recipients_list_read_format,
                            recipient.fullName,
                            Date.fromMillis(recipient.readDate).formattedString,
                            Time.fromMillis(recipient.readDate).stringHM
                    ))
                }
            }
            messageRecipients.append("</ul>")
            b.recipients.text = BetterHtml.fromHtml(activity, messageRecipients)
        }

        showAttachments()

        BetterLink.attach(b.subject)
        BetterLink.attach(b.body)

        b.progress.visibility = View.GONE
        Anim.fadeIn(b.content, 200, null)

        b.notesButton.setupNotesButton(
            activity = activity,
            owner = message,
        )
    }

    /**
     * The delete button restores the message if it is already deleted.
     */
    private fun updateActionButtons() {
        b.replyButton.isVisible = message.isReceived || message.isDeleted
        b.deleteButton.isVisible = message.isReceived || message.isDeleted
        b.deleteButton.setText(if (message.isDeleted) R.string.messages_restore else R.string.message_delete)
        (b.deleteButton.compoundDrawables[1] as? IconicsDrawable)?.icon =
            if (message.isDeleted)
                CommunityMaterial.Icon.cmd_delete_restore
            else
                CommunityMaterial.Icon3.cmd_trash_can_outline
        if (!isEmbedded) {
            // go back to the message's current tab
            MessagesFragment.pageSelection = message.type.coerceIn(0, 3)
        }
    }

    fun onExternalStarChanged(messageId: Long, isStarred: Boolean) {
        if (!isAdded || view == null || !this::message.isInitialized) return
        if (message.id == messageId) {
            message.isStarred = isStarred
            manager.setStarIcon(b.messageStar, message)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!isEmbedded &&
            !isHidden &&
            newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE &&
            app.profile.config.ui.messagesTwoColumnLandscape
        ) {
            switchToTwoColumnView()
        }
    }

    fun onTwoColumnSettingChanged() {
        if (!isEmbedded &&
            !isHidden &&
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
            app.profile.config.ui.messagesTwoColumnLandscape
        ) {
            switchToTwoColumnView()
        }
    }

    private fun switchToTwoColumnView() {
        val args = Bundle(arguments ?: Bundle())
        if (this::message.isInitialized) {
            args.putLong("messageId", message.id)
            args.putInt("messageType", message.type)
            MessagesFragment.pageSelection = message.type.coerceIn(0, 3)
        }
        MessagesFragment.pendingDetailArgs = args
        activity.navigate(navTarget = NavTarget.MESSAGES, args = args, skipBeforeNavigate = true)
    }

    private fun showAttachments() {
        if (message.attachmentIds.isNullOrEmpty() || message.attachmentNames.isNullOrEmpty()) {
            b.attachmentsTitle.isVisible = false
            b.attachmentsFragment.isVisible = false
        }
        else {
            b.attachmentsTitle.isVisible = true
            b.attachmentsFragment.isVisible = true
            b.attachmentsFragment.init(Bundle().also {
                it.putInt("profileId", message.profileId)
                it.putLongArray("attachmentIds", message.attachmentIds!!.toLongArray())
                it.putStringArray("attachmentNames", message.attachmentNames!!.toTypedArray())
                if (message.attachmentSizes.isNotNullNorEmpty())
                    it.putLongArray("attachmentSizes", message.attachmentSizes!!.toLongArray())
            }, owner = message)
        }
    }
}
