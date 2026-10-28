package pl.szczodrzynski.edziennik.ui.event

import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.core.manager.EventManager
import pl.szczodrzynski.edziennik.databinding.DialogSubjectColorsBinding
import pl.szczodrzynski.edziennik.ui.base.dialog.BindingDialog
import java.text.Collator
import java.util.Locale

class EventColorsDialog(
    activity: AppCompatActivity,
) : BindingDialog<DialogSubjectColorsBinding>(activity) {

    override fun getTitleRes() = R.string.agenda_config_change_event_colors
    override fun inflate(layoutInflater: LayoutInflater) =
        DialogSubjectColorsBinding.inflate(layoutInflater)

    override fun getPositiveButtonText() = R.string.ok

    override suspend fun onShow() {
        val eventTypes = withContext(Dispatchers.Default) {
            val collator = Collator.getInstance(Locale("pl", "PL"))
            app.db.eventTypeDao().getAllWithDefaults(app.profile)
                .sortedWith(compareBy(collator) { it.name })
        }
        b.list.layoutManager = LinearLayoutManager(activity)
        b.list.adapter = EventColorsAdapter(activity).also {
            it.items = listOf(
                EventColorsAdapter.ColorRow.Special(
                    EventManager.COLOR_KEY_LESSON_CHANGES,
                    R.string.agenda_lesson_changes,
                    EventManager.DEFAULT_LESSON_CHANGES_COLOR
                ),
                EventColorsAdapter.ColorRow.Special(
                    EventManager.COLOR_KEY_TEACHER_ABSENCE,
                    R.string.agenda_teacher_absence,
                    EventManager.DEFAULT_TEACHER_ABSENCE_COLOR
                )
            ) + eventTypes.map { EventColorsAdapter.ColorRow.Event(it) }
        }
    }
}
