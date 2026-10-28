package pl.szczodrzynski.edziennik.ui.timetable

import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.databinding.DialogSubjectColorsBinding
import pl.szczodrzynski.edziennik.ui.base.dialog.BindingDialog
import java.text.Collator
import java.util.Locale

class SubjectColorsDialog(
    activity: AppCompatActivity,
) : BindingDialog<DialogSubjectColorsBinding>(activity) {

    override fun getTitleRes() = R.string.timetable_config_change_subject_colors
    override fun inflate(layoutInflater: LayoutInflater) =
        DialogSubjectColorsBinding.inflate(layoutInflater)

    override fun getPositiveButtonText() = R.string.ok

    override suspend fun onShow() {
        val subjects = withContext(Dispatchers.Default) {
            val collator = Collator.getInstance(Locale("pl", "PL"))
            app.db.subjectDao().getAllNow(App.profileId)
                .sortedWith(compareBy(collator) { it.longName })
        }
        b.list.layoutManager = LinearLayoutManager(activity)
        b.list.adapter = SubjectColorsAdapter(activity).also {
            it.items = subjects
        }
    }
}
