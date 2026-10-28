/*
 * Copyright (c) Kuba Szczodrzyński 2024-7-3.
 */

package pl.szczodrzynski.edziennik.ui.base.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch
import pl.szczodrzynski.edziennik.ext.set
import pl.szczodrzynski.edziennik.ui.base.views.RecyclerTabLayout
import pl.szczodrzynski.edziennik.ui.base.views.applyPillTabBackground
import pl.szczodrzynski.edziennik.ui.base.views.applyPillTabIndicator

abstract class PagerFragment<B : ViewBinding, A : AppCompatActivity>(
    inflater: ((inflater: LayoutInflater, parent: ViewGroup?, attachToParent: Boolean) -> B)?,
) : BaseFragment<B, A>(inflater) {

    private lateinit var pages: List<Pair<BaseFragment<*, *>, String>>
    private val fragmentCache = mutableMapOf<Int, BaseFragment<*, *>>()
    private lateinit var pagerAdapter: FragmentStateAdapter
    private var pagerGeneration = 0L

    /**
     * Stores the default page index that is activated when
     * entering the fragment. Updated every time a new page
     * is selected.
     *
     * Override with a getter and setter to make it backed by
     * e.g. app.config. Set this value before calling super.[onViewReady]
     * to provide a one-time default.
     */
    protected open var savedPageSelection = -1

    protected val currentFragment: BaseFragment<*, *>?
        get() = fragmentCache[getViewPager().currentItem]
            ?: childFragmentManager.findFragmentByTag("f${getViewPager().currentItem}") as? BaseFragment<*, *>
            ?: childFragmentManager.fragments.getOrNull(getViewPager().currentItem) as? BaseFragment<*, *>

    final override fun getScrollingView() = null
    override fun getSyncParams() = currentFragment?.getSyncParams()

    override fun onResume() {
        // add TabLayout before super's setupScrollListener {}
        val tabLayout = getTabLayout()
        appBars += tabLayout
        (tabLayout.parent as? AppBarLayout)?.let { appBars += it }
        super.onResume()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && isAdded && ::pagerAdapter.isInitialized) {
            if (savedPageSelection != -1 && getViewPager().currentItem != savedPageSelection) {
                getViewPager().setCurrentItem(savedPageSelection, false)
            }
            val fragment = currentFragment
            canRefreshDisabled = fragment?.canRefreshDisabled == true
            isScrolled = fragment?.isScrolled == true
        }
        fragmentCache.values.forEach { it.onHiddenChanged(hidden) }
    }

    override suspend fun onViewReady(savedInstanceState: Bundle?) {
        if (savedPageSelection == -1)
            savedPageSelection = savedInstanceState?.getInt("pageSelection") ?: 0

        pages = onCreatePages()

        pagerAdapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = getPageCount()
            override fun createFragment(position: Int): Fragment {
                val fragment = getPageFragment(position)
                fragmentCache[position] = fragment
                return fragment
            }

            override fun getItemId(position: Int) =
                (pagerGeneration shl 32) or position.toLong()

            override fun containsItem(itemId: Long): Boolean {
                val generation = itemId ushr 32
                val position = (itemId and 0xFFFFFFFFL).toInt()
                return generation == pagerGeneration && position in 0 until getPageCount()
            }
        }

        getViewPager().let {
            it.offscreenPageLimit = 1
            it.adapter = pagerAdapter
            it.setCurrentItem(savedPageSelection, false)
            it.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageScrollStateChanged(state: Int) {
                    this@PagerFragment.onPageScrollStateChanged(state)
                    if (state != ViewPager2.SCROLL_STATE_IDLE) {
                        // disable swipe-to-refresh during scrolling
                        canRefreshDisabled = true
                        return
                    }
                    // take child fragment's values
                    val fragment = currentFragment
                    canRefreshDisabled = fragment?.canRefreshDisabled == true
                    isScrolled = fragment?.isScrolled == true
                }

                override fun onPageSelected(position: Int) {
                    savedPageSelection = position
                    launch {
                        this@PagerFragment.onPageSelected(position)
                    }
                }
            })
        }

        when (val tabLayout = getTabLayout()) {
            is TabLayout -> {
                tabLayout.applyPillRipple()
                TabLayoutMediator(tabLayout, getViewPager()) { tab, position ->
                    tab.text = getPageTitle(position)
                }.attach()
            }

            is RecyclerTabLayout -> tabLayout.setupWithViewPager(getViewPager()) { tab, position ->
                RecyclerTabLayout.setTabText(tab, getPageTitle(position))
            }
        }

        onPageSelected(savedPageSelection)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState["pageSelection"] = getViewPager().currentItem
    }

    /**
     * Recreates pager fragments after a settings change.
     * ViewPager2 only does this when [FragmentStateAdapter] item IDs change.
     */
    protected fun reloadPages() {
        if (!::pagerAdapter.isInitialized)
            return
        fragmentCache.clear()
        pagerGeneration++
        pagerAdapter.notifyDataSetChanged()
    }

    /**
     * Navigates to the specified page with a smooth scroll animation.
     *
     * To navigate without smooth scroll, use [savedPageSelection] instead
     * to provide a default page selection.
     */
    protected fun goToPage(position: Int) {
        getViewPager().setCurrentItem(position, true)
        savedPageSelection = position
        launch {
            onPageSelected(position)
        }
    }

    /**
     * Called to retrieve the [TabLayout] or [RecyclerTabLayout] view of the pager fragment.
     */
    abstract fun getTabLayout(): ViewGroup

    /**
     * Called to retrieve the [ViewPager2] view of the pager fragment.
     */
    abstract fun getViewPager(): ViewPager2

    /**
     * Called to retrieve a list of fragments (and their titles) for the pager.
     * Only used with the default implementation of [getPageCount], [getPageFragment]
     * and [getPageTitle].
     */
    open suspend fun onCreatePages() = listOf<Pair<BaseFragment<*, *>, String>>()

    open fun getPageCount() = pages.size
    open fun getPageFragment(position: Int) = pages[position].first
    open fun getPageTitle(position: Int) = pages[position].second

    /**
     * Called when a new page is selected, by either:
     * - opening the fragment for the first time (default page),
     * - calling [goToPage], or
     * - navigating to another page by the user.
     *
     * The [savedPageSelection] is updated before this method is called.
     */
    open suspend fun onPageSelected(position: Int) {}

    /**
     * Called when the scroll state of the [ViewPager2] changes.
     */
    protected open fun onPageScrollStateChanged(state: Int) {}
}

fun TabLayout.applyPillRipple() {
    applyPillTabIndicator()
    val indicator = getChildAt(0) as? ViewGroup ?: return
    val applyToView: (View) -> Unit = { view ->
        view.applyPillTabBackground()
    }
    indicator.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
        override fun onChildViewAdded(parent: View?, child: View?) {
            child?.let(applyToView)
        }

        override fun onChildViewRemoved(parent: View?, child: View?) {}
    })
    for (i in 0 until indicator.childCount) {
        indicator.getChildAt(i)?.let(applyToView)
    }
}
