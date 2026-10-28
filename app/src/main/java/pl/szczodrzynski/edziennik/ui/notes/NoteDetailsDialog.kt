/*
 * Copyright (c) Kuba Szczodrzyński 2021-10-24.
 */

package pl.szczodrzynski.edziennik.ui.notes

import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.db.entity.Note
import pl.szczodrzynski.edziennik.data.db.entity.Noteable
import pl.szczodrzynski.edziennik.databinding.NoteDetailsDialogBinding
import pl.szczodrzynski.edziennik.ext.overlayBackgroundColor
import pl.szczodrzynski.edziennik.ui.base.dialog.BindingDialog

class NoteDetailsDialog(
    activity: AppCompatActivity,
    val owner: Noteable?,
    var note: Note,
    private val onDismiss: (() -> Unit)? = null,
) : BindingDialog<NoteDetailsDialogBinding>(activity) {

    private lateinit var binder: NoteDetailsBinder

    override fun getTitleRes(): Int? = null
    override fun inflate(layoutInflater: LayoutInflater) =
        NoteDetailsDialogBinding.inflate(layoutInflater)

    override fun getPositiveButtonText() = R.string.close
    override fun getNeutralButtonText() = if (note.canEdit) R.string.homework_edit else null

    override suspend fun onNeutralClick(): Boolean {
        NoteEditorDialog(
            activity = activity,
            owner = owner,
            editingNote = note,
        ).show()
        return NO_DISMISS
    }

    override suspend fun onShow() {
        binder = NoteDetailsBinder(
            activity = activity,
            b = b,
            onClose = ::dismiss,
            onNoteUpdated = { note = it },
        )
        binder.bind(owner, note)

        if (note.color != null) {
            dialog.overlayBackgroundColor(note.color!!.toInt(), 0x50)
        } else {
            dialog.overlayBackgroundColor(0, 0)
        }
    }

    override suspend fun onDismiss() {
        super.onDismiss()
        if (::binder.isInitialized) {
            binder.unbind()
        }
        onDismiss?.invoke()
    }
}
