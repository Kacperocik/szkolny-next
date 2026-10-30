package pl.szczodrzynski.edziennik.ui.lessontopics

import android.os.Bundle
import pl.szczodrzynski.edziennik.MainActivity
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.enums.FeatureType
import pl.szczodrzynski.edziennik.databinding.BasePagerFragmentBinding
import pl.szczodrzynski.edziennik.ui.base.fragment.PagerFragment

class LessonTopicsFragment : PagerFragment<BasePagerFragmentBinding, MainActivity>(BasePagerFragmentBinding::inflate) {
    override fun getTabLayout() = b.tabLayout
    override fun getViewPager() = b.viewPager
    override fun getSyncParams() = FeatureType.LESSON_TOPICS to null
    override suspend fun onCreatePages() = listOf(
        LessonTopicsListFragment().apply { arguments = Bundle().apply { putBoolean("planned", false) } } to getString(R.string.lesson_topics_realized),
        LessonTopicsListFragment().apply { arguments = Bundle().apply { putBoolean("planned", true) } } to getString(R.string.lesson_topics_planned),
    )
}
