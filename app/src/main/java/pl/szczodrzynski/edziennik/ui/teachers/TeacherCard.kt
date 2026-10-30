package pl.szczodrzynski.edziennik.ui.teachers

import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.core.view.isVisible
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.databinding.TeacherCardBinding
import pl.szczodrzynski.edziennik.ext.copyToClipboard
import pl.szczodrzynski.edziennik.ext.enableExpressiveCardShapeMorph
import pl.szczodrzynski.edziennik.ui.messages.MessagesUtils
import pl.szczodrzynski.edziennik.utils.BetterLink
import pl.szczodrzynski.navlib.enableExpressiveImageShapeMorph

fun TeacherCardBinding.bindTeacher(
    teacherId: Long,
    teacherName: String?,
    onActionSelected: () -> Unit,
) {
    val teacher = teacherName.orEmpty()
    root.isVisible = teacher.isNotBlank()
    name.text = teacher
    image.setImageBitmap(MessagesUtils.getProfileImage(48, 24, 18, 12, 1, teacher))
    card.enableExpressiveCardShapeMorph(null, 250L, 250L, 200L)
    image.enableExpressiveImageShapeMorph(10f, 250L, 250L, 200L)
    root.setOnClickListener { view ->
        BetterLink.showTeacherMenu(view, teacherId, teacher, onActionSelected)
    }
    root.setOnLongClickListener {
        if (teacher.isBlank()) return@setOnLongClickListener false
        it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        teacher.copyToClipboard(it.context)
        Toast.makeText(it.context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
        true
    }
}
