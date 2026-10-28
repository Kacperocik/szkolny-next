/*
 * Copyright (c) Kuba Szczodrzyński 2021-4-14.
 */

package pl.szczodrzynski.edziennik.ui.dialogs.settings

import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import pl.szczodrzynski.edziennik.MainActivity
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.databinding.MessagesConfigDialogBinding
import pl.szczodrzynski.edziennik.ui.base.dialog.ConfigDialog
import pl.szczodrzynski.edziennik.ui.messages.list.MessagesFragment
import pl.szczodrzynski.edziennik.ui.messages.single.MessageFragment

class MessagesConfigDialog(
    activity: AppCompatActivity,
    private val reloadOnDismiss: Boolean = true,
) : ConfigDialog<MessagesConfigDialogBinding>(activity, reloadOnDismiss) {

    private var initialTwoColumn = true

    override fun getTitleRes() = R.string.menu_messages_config
    override fun inflate(layoutInflater: LayoutInflater) =
        MessagesConfigDialogBinding.inflate(layoutInflater)

    override suspend fun loadConfig() {
        b.config = app.profile.config.ui
        initialTwoColumn = app.profile.config.ui.messagesTwoColumnLandscape

        b.greetingText.setText(
            app.profile.config.ui.messagesGreetingText
                ?: "\n\nZ poważaniem\n${app.profile.accountOwnerName}"
        )
    }

    override suspend fun saveConfig() {
        val greetingText = b.greetingText.text?.toString()?.trim()
        if (greetingText.isNullOrEmpty())
            app.profile.config.ui.messagesGreetingText = null
        else
            app.profile.config.ui.messagesGreetingText = "\n\n$greetingText"

        if (app.profile.config.ui.messagesTwoColumnLandscape != initialTwoColumn && activity is MainActivity) {
            when (val currentFrag = activity.supportFragmentManager.findFragmentById(R.id.fragment)) {
                is MessagesFragment -> currentFrag.updateTwoColumnLayout()
                is MessageFragment -> currentFrag.onTwoColumnSettingChanged()
                else -> if (!reloadOnDismiss) activity.reloadTarget()
            }
        }
    }
}
