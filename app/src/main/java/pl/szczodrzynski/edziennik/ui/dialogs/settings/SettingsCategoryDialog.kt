package pl.szczodrzynski.edziennik.ui.dialogs.settings

import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.danielstone.materialaboutlibrary.adapters.MaterialAboutListAdapter
import com.danielstone.materialaboutlibrary.model.MaterialAboutCard
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.ui.base.dialog.BaseDialog
import pl.szczodrzynski.edziennik.ui.settings.SettingsViewTypeManager

class SettingsCategoryDialog(
    activity: AppCompatActivity,
    private val titleRes: Int,
    private val card: MaterialAboutCard,
) : BaseDialog<Unit>(activity) {

    override fun getTitleRes() = titleRes
    override fun getPositiveButtonText() = R.string.close
    override fun hasCloseIconWhenSpaceLimited() = true

    override fun getView(): View {
        val recyclerView = RecyclerView(activity)
        recyclerView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        recyclerView.layoutManager = LinearLayoutManager(activity)
        val adapter = MaterialAboutListAdapter(SettingsViewTypeManager())
        adapter.setData(arrayListOf(card))
        recyclerView.adapter = adapter
        return recyclerView
    }
}
