/*
 * Copyright (c) Kuba Szczodrzyński 2024-7-7.
 */

package pl.szczodrzynski.edziennik.ui.base.views

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.drawable.updateBounds
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator.TabConfigurationStrategy
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.ext.getDeclaredField
import pl.szczodrzynski.edziennik.ext.onClick
import java.lang.reflect.Method

class RecyclerTabLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : RecyclerView(context, attrs, defStyleAttr) {

    companion object {
        private val tabTextField by lazy {
            TabLayout.Tab::class.java.getDeclaredField("text").apply { isAccessible = true }
        }
        private val tabViewTabField by lazy {
            TabLayout.TabView::class.java.getDeclaredField("tab").apply { isAccessible = true }
        }
        private val tabViewUpdateMethod by lazy {
            TabLayout.TabView::class.java.getDeclaredMethod("updateTab").apply { isAccessible = true }
        }

        fun setTabText(tab: TabLayout.Tab, text: CharSequence?) {
            tabTextField.set(tab, text)
        }
    }

    private val tabLayout: TabLayout = TabLayout(context, attrs)
    private lateinit var linearLayoutManager: LinearLayoutManager
    private lateinit var viewPager: ViewPager2
    private lateinit var viewPagerAdapter: RecyclerView.Adapter<ViewHolder>
    private lateinit var tabAdapter: Adapter
    private lateinit var tabConfigurationStrategy: TabConfigurationStrategy
    private lateinit var tab: TabLayout.Tab
    private var tabSelectedIndicator: Drawable = tabLayout.tabSelectedIndicator
    private var tabIndicatorGravity: Int = tabLayout.tabIndicatorGravity
    private var tabIndicatorInterpolator: Any? = tabLayout.getDeclaredField("tabIndicatorInterpolator")
    private var updateIndicatorMethod: Method? = resolveUpdateIndicatorMethod(tabIndicatorInterpolator)

    init {
        val defaultHeight = resources.getDimensionPixelSize(R.dimen.timetable_day_header_height)
        minimumHeight = defaultHeight
    }

    private var selectedTab = 0

    /**
     * Page position and offset that the indicator should currently point at.
     * The indicator is recalculated from the actual tab views after every layout,
     * as scrollToPositionWithOffset() only moves the tabs on the next layout pass.
     */
    private var indicatorPosition = 0
    private var indicatorOffset = 0f

    private fun resolveUpdateIndicatorMethod(interpolator: Any?): Method? =
        interpolator?.javaClass?.let { cls ->
            runCatching {
                cls.getDeclaredMethod(
                    "updateIndicatorForOffset",
                    TabLayout::class.java,
                    View::class.java,
                    View::class.java,
                    Float::class.javaPrimitiveType,
                    Drawable::class.java,
                ).apply { isAccessible = true }
            }.getOrNull()
        }

    fun setupWithViewPager(pager: ViewPager2, strategy: TabConfigurationStrategy) {
        linearLayoutManager = LinearLayoutManager(context).also {
            it.orientation = HORIZONTAL
        }
        viewPager = pager
        viewPagerAdapter = pager.adapter!!
        tabAdapter = Adapter()
        tabConfigurationStrategy = strategy
        tab = TabLayout.Tab()
        tabSelectedIndicator = PillTabIndicatorDrawable(this, tabLayout.tabSelectedIndicator)
        tabIndicatorGravity = tabLayout.tabIndicatorGravity
        tabIndicatorInterpolator = tabLayout.getDeclaredField("tabIndicatorInterpolator")
        updateIndicatorMethod = resolveUpdateIndicatorMethod(tabIndicatorInterpolator)
        selectedTab = pager.currentItem
        indicatorPosition = pager.currentItem
        indicatorOffset = 0f

        this.addOnScrollListener(OnScrollListener())
        viewPager.registerOnPageChangeCallback(OnPageChangeCallback())
        viewPagerAdapter.registerAdapterDataObserver(AdapterDataObserver())

        setWillNotDraw(false)
        setAdapter(tabAdapter)
        setHasFixedSize(true)
        layoutManager = linearLayoutManager
        itemAnimator = null
        background = tabLayout.background

        // The initial page callback may precede tab measurement.
        // Resync after layout so the indicator has valid bounds.
        post { syncSelectedTabWithPager() }
    }

    private fun syncSelectedTabWithPager() {
        if (!::viewPager.isInitialized || !::tabAdapter.isInitialized)
            return
        val position = viewPager.currentItem
        if (position < 0 || position >= tabAdapter.itemCount)
            return
        selectedTab = position
        indicatorPosition = position
        indicatorOffset = 0f
        tabAdapter.notifyDataSetChanged()
        // Bring the selected tab into view before measuring it for centering.
        linearLayoutManager.scrollToPosition(position)
        post {
            val tabView = findViewHolderForLayoutPosition(position)?.itemView ?: return@post
            val offset = (width / 2f) - (tabView.width / 2f)
            linearLayoutManager.scrollToPositionWithOffset(position, offset.toInt())
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        updateIndicator()
    }

    /**
     * Sets the indicator bounds from the current positions of the tab views.
     */
    private fun updateIndicator() {
        if (!::viewPager.isInitialized)
            return
        val thisTab = findViewHolderForLayoutPosition(indicatorPosition)?.itemView
        if (thisTab == null || thisTab.width == 0 || thisTab.visibility != View.VISIBLE) {
            // The selected tab may be recycled when the strip is scrolled manually.
            // Never keep drawing the indicator at its last visible position.
            tabSelectedIndicator.updateBounds(left = 0, right = 0)
            invalidate()
            return
        }
        val nextTab = findViewHolderForLayoutPosition(indicatorPosition + 1)?.itemView
        tabSelectedIndicator.updateBounds(top = 0, bottom = height)
        val interpolator = tabIndicatorInterpolator
        val method = updateIndicatorMethod
        if (interpolator != null && method != null) {
            method.invoke(
                interpolator,
                tabLayout,
                thisTab,
                nextTab,
                indicatorOffset,
                tabSelectedIndicator,
            )
        } else {
            tabSelectedIndicator.updateBounds(left = thisTab.left, right = thisTab.right)
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!::viewPager.isInitialized || tabSelectedIndicator.bounds.width() <= 0)
            return
        // draw the selected tab indicator
        var indicatorHeight = tabSelectedIndicator.bounds.height()
        if (indicatorHeight < 0)
            indicatorHeight = tabSelectedIndicator.intrinsicHeight

        var indicatorTop = 0
        var indicatorBottom = 0
        when (tabIndicatorGravity) {
            TabLayout.INDICATOR_GRAVITY_BOTTOM -> {
                indicatorTop = height - indicatorHeight
                indicatorBottom = height
            }

            TabLayout.INDICATOR_GRAVITY_CENTER -> {
                indicatorTop = (height - indicatorHeight) / 2
                indicatorBottom = (height + indicatorHeight) / 2
            }

            TabLayout.INDICATOR_GRAVITY_TOP -> {
                indicatorTop = 0
                indicatorBottom = indicatorHeight
            }

            TabLayout.INDICATOR_GRAVITY_STRETCH -> {
                indicatorTop = 0
                indicatorBottom = height
            }
        }

        tabSelectedIndicator.updateBounds(top = indicatorTop, bottom = indicatorBottom)
        tabSelectedIndicator.draw(canvas)
    }

    inner class Adapter : RecyclerView.Adapter<Adapter.TabViewHolder>() {
        override fun getItemCount() =
            viewPagerAdapter.itemCount

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            TabViewHolder(tabLayout.TabView(context)).also { holder ->
                val defaultHeight = resources.getDimensionPixelSize(R.dimen.timetable_day_header_height)
                holder.tabView.layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    defaultHeight,
                )
                holder.tabView.minimumHeight = defaultHeight
                holder.tabView.applyPillTabBackground()
                holder.tabView.onClick {
                    viewPager.setCurrentItem(holder.lastPosition, true)
                }
            }

        override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
            val tabView = holder.tabView
            tabConfigurationStrategy.onConfigureTab(tab, position)
            tabViewTabField.set(tabView, tab)
            tabViewUpdateMethod.invoke(tabView)
            tabViewTabField.set(tabView, null)
            tabView.isSelected = position == selectedTab
            tabView.isActivated = position == selectedTab
            holder.lastPosition = position
        }

        inner class TabViewHolder(
            val tabView: TabLayout.TabView,
            var lastPosition: Int = 0,
        ) : ViewHolder(tabView)
    }

    inner class OnScrollListener : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            updateIndicator()
        }
    }

    inner class OnPageChangeCallback : ViewPager2.OnPageChangeCallback() {
        override fun onPageScrolled(
            position: Int,
            positionOffset: Float,
            positionOffsetPixels: Int,
        ) {
            stopScroll()
            indicatorPosition = position
            indicatorOffset = positionOffset

            val thisTab = findViewHolderForLayoutPosition(position)?.itemView
            val nextTab = findViewHolderForLayoutPosition(position + 1)?.itemView

            // scroll to the currently selected tab
            val thisTabWidth = thisTab?.width ?: 0
            val nextTabWidth = nextTab?.width ?: 0
            val tabDistance = (thisTabWidth / 2) + (nextTabWidth / 2)
            val offset = (width / 2.0f) - (thisTabWidth / 2.0f) - (tabDistance * positionOffset)
            // 'offset' is the screen position of the tab's left edge
            linearLayoutManager.scrollToPositionWithOffset(position, offset.toInt())

            // update selection state of the current tab directly on bound views
            // to avoid re-measuring / re-binding tabs in the middle of the scroll animation
            val roundedPosition = Math.round(position + positionOffset)
            if (selectedTab != roundedPosition) {
                val previousTab = selectedTab
                selectedTab = roundedPosition
                (findViewHolderForLayoutPosition(previousTab) as? Adapter.TabViewHolder)?.tabView?.let {
                    it.isSelected = false
                    it.isActivated = false
                }
                (findViewHolderForLayoutPosition(selectedTab) as? Adapter.TabViewHolder)?.tabView?.let {
                    it.isSelected = true
                    it.isActivated = true
                }
            }

            // the tabs move on the next layout pass - onLayout() corrects the indicator then
            updateIndicator()
        }
    }

    inner class AdapterDataObserver : RecyclerView.AdapterDataObserver() {
        @SuppressLint("NotifyDataSetChanged")
        override fun onChanged() =
            tabAdapter.notifyDataSetChanged()

        override fun onItemRangeChanged(positionStart: Int, itemCount: Int) =
            tabAdapter.notifyItemRangeChanged(positionStart, itemCount)

        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) =
            tabAdapter.notifyItemRangeInserted(positionStart, itemCount)

        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) =
            tabAdapter.notifyItemRangeRemoved(positionStart, itemCount)

        @SuppressLint("NotifyDataSetChanged")
        override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) =
            tabAdapter.notifyDataSetChanged()
    }
}
