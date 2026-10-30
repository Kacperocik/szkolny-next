package pl.szczodrzynski.edziennik.ui.announcements

import android.animation.LayoutTransition
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.db.entity.Profile
import pl.szczodrzynski.edziennik.data.db.full.AnnouncementFull
import pl.szczodrzynski.edziennik.data.enums.LoginType
import pl.szczodrzynski.edziennik.databinding.DialogAnnouncementBinding
import pl.szczodrzynski.edziennik.ext.copyToClipboard
import pl.szczodrzynski.edziennik.ui.base.dialog.BindingDialog
import pl.szczodrzynski.edziennik.ui.notes.setupNotesButton
import pl.szczodrzynski.edziennik.ui.teachers.bindTeacher

class AnnouncementDetailsDialog(
    activity: AppCompatActivity,
    val announcement: AnnouncementFull,
    private val profile: Profile? = null,
    private val onSeen: (() -> Unit)? = null,
    private val onDismiss: (() -> Unit)? = null,
) : BindingDialog<DialogAnnouncementBinding>(activity) {

    constructor(
        activity: AppCompatActivity,
        announcement: AnnouncementFull,
        onSeen: (() -> Unit)? = null,
        onDismiss: (() -> Unit)? = null,
    ) : this(activity, announcement, null, onSeen, onDismiss)

    override suspend fun onDismiss() {
        super.onDismiss()
        onDismiss?.invoke()
    }

    override fun getTitle(): CharSequence = announcement.subject

    override fun inflate(layoutInflater: LayoutInflater): DialogAnnouncementBinding =
        DialogAnnouncementBinding.inflate(layoutInflater)

    override fun getPositiveButtonText() = R.string.close
    override fun hasCloseIconWhenSpaceLimited() = true

    override suspend fun onShow() {
        dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.let { titleView ->
            (titleView.parent as? ViewGroup)?.layoutTransition = LayoutTransition()
            val outValue = TypedValue()
            if (activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)) {
                titleView.setBackgroundResource(outValue.resourceId)
            }
            titleView.setOnClickListener {
                titleView.maxLines = if (titleView.maxLines >= 30) 2 else 30
            }
            titleView.setOnLongClickListener {
                val subject = announcement.subject
                if (subject.isNotBlank()) {
                    it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    subject.copyToClipboard(activity)
                    Toast.makeText(activity, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
                    true
                } else {
                    false
                }
            }
        }

        b.teacherCard.bindTeacher(announcement.teacherId, announcement.teacherName, ::dismiss)

        val start = announcement.startDate?.formattedString ?: "-"
        val end = announcement.endDate?.let { " do ${it.formattedString}" } ?: ""
        b.dateRange.text = "$start$end"
        b.text.text = announcement.text
        b.notesButton.setupNotesButton(activity, announcement)

        val targetProfile = profile ?: app.profile
        if (!announcement.seen && targetProfile.loginStoreType != LoginType.LIBRUS) {
            announcement.seen = true
            launch(Dispatchers.IO) {
                app.db.metadataDao().setSeen(targetProfile.id, announcement, true)
            }
            onSeen?.invoke()
        }
    }
}
