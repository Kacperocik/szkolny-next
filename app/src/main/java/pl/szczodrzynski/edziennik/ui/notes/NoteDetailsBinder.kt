/*
 * Copyright (c) Kuba Szczodrzyński 2021-10-24.
 */

package pl.szczodrzynski.edziennik.ui.notes

import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.db.entity.Note
import pl.szczodrzynski.edziennik.data.db.entity.Noteable
import pl.szczodrzynski.edziennik.databinding.NoteDetailsDialogBinding
import pl.szczodrzynski.edziennik.ext.setText
import pl.szczodrzynski.edziennik.utils.models.Date

class NoteDetailsBinder(
    val activity: AppCompatActivity,
    val b: NoteDetailsDialogBinding,
    val onClose: (() -> Unit)? = null,
    val onNoteUpdated: ((Note) -> Unit)? = null,
) {
    private val app = activity.applicationContext as App
    private val manager
        get() = app.noteManager

    var owner: Noteable? = null
        private set
    var note: Note? = null
        private set

    private var observer: Observer<Note?>? = null

    fun bind(owner: Noteable?, note: Note) {
        this.owner = owner
        this.note = note

        manager.configureHeader(activity, owner, b.header)
        b.idsLayout.isVisible = App.devMode

        observer?.let { obs ->
            this.note?.let { n ->
                app.db.noteDao().get(n.profileId, n.id).removeObserver(obs)
            }
        }

        val obs = Observer<Note?> { updated ->
            if (updated == null) {
                onClose?.invoke()
                return@Observer
            }
            this.note = updated
            onNoteUpdated?.invoke(updated)
            update()
        }
        observer = obs
        app.db.noteDao().get(note.profileId, note.id).observe(activity, obs)
        update()
    }

    fun unbind() {
        observer?.let { obs ->
            note?.let { n ->
                app.db.noteDao().get(n.profileId, n.id).removeObserver(obs)
            }
        }
        observer = null
    }

    fun update() {
        val note = this.note ?: return
        b.note = note

        b.addedBy.setText(
            when (note.sharedBy) {
                null -> R.string.event_details_added_by_unknown_format
                "self" -> R.string.event_details_shared_by_self_format
                else -> R.string.event_details_shared_by_format
            },
            Date.fromMillis(note.addedDate).formattedString,
            note.sharedByName ?: "",
        )
    }
}
