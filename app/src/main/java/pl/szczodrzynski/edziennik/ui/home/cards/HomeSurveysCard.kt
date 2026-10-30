package pl.szczodrzynski.edziennik.ui.home.cards

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.setMargins
import pl.szczodrzynski.edziennik.MainActivity
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.db.entity.LibrusSurvey
import pl.szczodrzynski.edziennik.databinding.CardHomeSurveysBinding
import pl.szczodrzynski.edziennik.databinding.RowHomeSurveyBinding
import pl.szczodrzynski.edziennik.ext.dp
import pl.szczodrzynski.edziennik.ext.enableExpressiveCardShapeMorph
import pl.szczodrzynski.edziennik.ui.home.HomeCard
import pl.szczodrzynski.edziennik.ui.surveys.SurveyWebActivity
import pl.szczodrzynski.edziennik.ui.home.HomeCardAdapter
import pl.szczodrzynski.edziennik.utils.models.Date

class HomeSurveysCard(private val activity: MainActivity, var surveys: List<LibrusSurvey>) : HomeCard {
    override val id = HomeCard.CARD_SURVEYS
    override fun bind(position: Int, holder: HomeCardAdapter.ViewHolder) {
        holder.root.removeAllViews()
        holder.root.setOnClickListener(null)
        val b = CardHomeSurveysBinding.inflate(LayoutInflater.from(holder.root.context))
        b.root.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(4.dp) }
        holder.root.addView(b.root)
        surveys.forEach { survey ->
            val row = RowHomeSurveyBinding.inflate(activity.layoutInflater, b.surveys, false)
            row.title.text = survey.title
            row.published.text = activity.getString(R.string.survey_published_format, Date.fromY_m_d(survey.publishedDate).formattedString)
            row.root.enableExpressiveCardShapeMorph()
            row.root.setOnClickListener {
                SurveyWebActivity.open(activity, survey.profileId, survey.id, survey.title)
            }
            b.surveys.addView(row.root)
        }
    }
    override fun unbind(position: Int, holder: HomeCardAdapter.ViewHolder) = Unit
}
