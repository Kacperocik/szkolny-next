package pl.szczodrzynski.edziennik.ui.agenda.duty

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.github.tibolte.agendacalendarview.render.EventRenderer
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.databinding.AgendaWrappedStudentDutyBinding

class StudentDutyEventRenderer(private val activity: AppCompatActivity) : EventRenderer<StudentDutyEvent>() {
    override fun render(view: View, event: StudentDutyEvent) {
        AgendaWrappedStudentDutyBinding.bind(view).item.bindDuty(event.duty) {
            showStudentDutyDetails(activity, event.duty)
        }
    }
    override fun getEventLayout() = R.layout.agenda_wrapped_student_duty
}
