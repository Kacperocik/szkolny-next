package pl.szczodrzynski.edziennik.ui.excuses

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.data.db.entity.LibrusExcuse

class ExcuseListAdapter(
    private val activity: AppCompatActivity,
    val onExcuseClick: ((excuse: LibrusExcuse) -> Unit)? = null,
) : RecyclerView.Adapter<ExcuseViewHolder>() {

    private val app = activity.applicationContext as App

    var items = listOf<LibrusExcuse>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ExcuseViewHolder(LayoutInflater.from(parent.context), parent)

    override fun onBindViewHolder(holder: ExcuseViewHolder, position: Int) {
        holder.onBind(activity, app, items[position], position, this)
    }

    override fun getItemCount() = items.size
}
