/*
 * Copyright (c) Kuba Szczodrzyński 2019-12-18.
 */

package pl.szczodrzynski.edziennik.ui.event

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.db.full.EventFull
import pl.szczodrzynski.edziennik.databinding.DialogEventDetailsBinding
import pl.szczodrzynski.edziennik.ext.dp
import pl.szczodrzynski.edziennik.ui.base.dialog.BindingDialog

// TODO: 2021-10-19 rewrite to the new dialog style
class EventDetailsDialog(
    activity: AppCompatActivity,
    // this event is observed for changes
    var event: EventFull,
    private val showNotes: Boolean = true,
    private val onDismiss: (() -> Unit)? = null,
) : BindingDialog<DialogEventDetailsBinding>(activity) {

    private lateinit var binder: EventDetailsBinder

    override fun getTitleRes(): Int? = null
    override fun inflate(layoutInflater: LayoutInflater) =
        DialogEventDetailsBinding.inflate(layoutInflater)

    override fun getPositiveButtonText() = R.string.close
    override fun getNeutralButtonText() = if (event.addedManually) R.string.homework_edit else null
    override fun hasCloseIconWhenSpaceLimited() = true
    override fun getNeutralButtonIcon() = CommunityMaterial.Icon3.cmd_pencil_outline

    override fun onCloseIconVisibilityChanged(isVisible: Boolean) {
        // move the date and time from under the X
        b.dateContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            marginEnd = if (isVisible) 32.dp else 0
        }
    }

    override suspend fun onNeutralClick(): Boolean {
        EventManualDialog(
            activity,
            event.profileId,
            editingEvent = event,
            onSaveListener = {
                if (it == null) {
                    dismiss()
                }
            },
        ).show()
        return NO_DISMISS
    }

    override suspend fun onShow() {
        binder = EventDetailsBinder(
            activity = activity,
            b = b,
            showNotes = showNotes,
            onClose = ::dismiss,
            onEventUpdated = { event = it },
        )
        binder.bind(event)
    }

    override suspend fun onDismiss() {
        super.onDismiss()
        if (::binder.isInitialized) {
            binder.unbind()
        }
        onDismiss?.invoke()
    }
}
