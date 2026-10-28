/*
 * Copyright (c) Kuba Szczodrzyński 2021-3-16.
 */

package pl.szczodrzynski.edziennik.ui.settings

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.danielstone.materialaboutlibrary.MaterialAboutFragment
import com.google.android.material.card.MaterialCardView
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.MainActivity
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.ui.base.fragment.AppBarColorAnimator
import pl.szczodrzynski.edziennik.ui.base.fragment.setupScrollListener
import kotlin.math.roundToInt

abstract class BaseSettingsFragment : MaterialAboutFragment() {

    protected lateinit var activity: MainActivity
    protected lateinit var app: App

    protected open val transparentCards: Boolean = false

    protected val util by lazy {
        SettingsUtil(activity) {
            refreshMaterialAboutList()
        }
    }

    private var appBarAnimator: AppBarColorAnimator? = null
    private val appBars = mutableSetOf<View>()
    private var isScrolled = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        activity = (getActivity() as? MainActivity) ?: return null
        app = activity.application as App
        appBars.clear()
        appBars += activity.navView.toolbar
        appBarAnimator = AppBarColorAnimator(activity, appBars)
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (transparentCards) {
            makeCardsTransparent(view)
        }
        val recyclerView = view.findViewById<RecyclerView>(R.id.mal_recyclerview)
        var first = true
        recyclerView.setupScrollListener {
            if (isScrolled != it || first) {
                isScrolled = it
                appBarAnimator?.dispatchLiftOnScroll(it)
            }
            first = false
        }
    }

    override fun onResume() {
        super.onResume()
        val recyclerView = view?.findViewById<RecyclerView>(R.id.mal_recyclerview)
        val scrolled = recyclerView?.canScrollVertically(-1) == true
        isScrolled = scrolled
        appBarAnimator?.dispatchLiftOnScroll(scrolled)
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            val recyclerView = view?.findViewById<RecyclerView>(R.id.mal_recyclerview)
            val scrolled = recyclerView?.canScrollVertically(-1) == true
            isScrolled = scrolled
            appBarAnimator?.dispatchLiftOnScroll(scrolled)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        appBarAnimator = null
        isScrolled = false
    }

    private fun makeCardsTransparent(view: View) {
        val recyclerView = view.findViewById<RecyclerView>(R.id.mal_recyclerview) ?: return
        val outerMargin = (4 * resources.displayMetrics.density).roundToInt()
        fun applyTransparent(child: View) {
            val cardView = child as? MaterialCardView ?: child.findViewById(R.id.mal_list_card)
            cardView?.setCardBackgroundColor(Color.TRANSPARENT)
            cardView?.strokeWidth = 0
            cardView?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = outerMargin
                rightMargin = outerMargin
            }
        }
        for (i in 0 until recyclerView.childCount) {
            applyTransparent(recyclerView.getChildAt(i))
        }
        recyclerView.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(child: View) {
                applyTransparent(child)
            }
            override fun onChildViewDetachedFromWindow(child: View) {}
        })
    }

    override fun getViewTypeManager() =
        SettingsViewTypeManager()
}
