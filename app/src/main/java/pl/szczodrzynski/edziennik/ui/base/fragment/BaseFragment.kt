/*
 * Copyright (c) Kuba Szczodrzyński 2024-7-2.
 */

package pl.szczodrzynski.edziennik.ui.base.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import com.google.android.material.appbar.AppBarLayout
import com.google.gson.JsonObject
import com.mikepenz.iconics.typeface.IIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.greenrobot.eventbus.EventBus
import pl.szczodrzynski.edziennik.App
import pl.szczodrzynski.edziennik.MainActivity
import pl.szczodrzynski.edziennik.data.enums.FeatureType
import pl.szczodrzynski.edziennik.data.enums.MetadataType
import pl.szczodrzynski.edziennik.ext.registerSafe
import pl.szczodrzynski.edziennik.ext.startCoroutineTimer
import pl.szczodrzynski.edziennik.ext.unregisterSafe
import pl.szczodrzynski.edziennik.ui.login.LoginActivity
import pl.szczodrzynski.navlib.bottomsheet.items.IBottomSheetItem
import kotlin.coroutines.CoroutineContext

@Suppress("UNCHECKED_CAST")
abstract class BaseFragment<B : ViewBinding, A : AppCompatActivity>(
    private val inflater: ((inflater: LayoutInflater, parent: ViewGroup?, attachToParent: Boolean) -> B)?,
) : Fragment(), CoroutineScope {

    internal lateinit var app: App
    internal lateinit var activity: A
    internal lateinit var b: B

    private var isViewReady: Boolean = false
    private var isActivityReady: Boolean = false
    private var inState: Bundle? = null
    private var appBarAnimator: AppBarColorAnimator? = null

    /**
     * Whether the view is currently being scrolled
     * or is left scrolled away from the top.
     */
    internal var isScrolled = false
        set(value) { // cannot be private - PagerFragment onPageScrollStateChanged
            field = value
            if (isResumed) {
                dispatchCanRefresh()
                appBarAnimator?.dispatchLiftOnScroll()
            }
        }

    /**
     * Forcefully disables the activity's SwipeRefreshLayout.
     *
     * The [PagerFragment] manages its [canRefreshDisabled] state
     * based on the value of the currently selected page.
     */
    internal var canRefreshDisabled = false
        set(value) {
            field = value
            if (isResumed) dispatchCanRefresh()
        }

    /**
     * A list of views (usually app bars) that should have their
     * background color elevated when the fragment is scrolled.
     */
    internal var appBars = mutableSetOf<View>()

    private var job = Job()
    final override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    final override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        job.cancel()
        job = Job()
        activity = getActivity() as? A ?: return null
        context ?: return null
        app = activity.application as App
        // inflate using the constructor parameter or the body method
        b = this.inflater?.invoke(inflater, container, false)
            ?: inflate(inflater, container, false)
            ?: return null
        isViewReady = false
        isActivityReady = false
        inState = savedInstanceState
        appBarAnimator = AppBarColorAnimator(activity, appBars)
        return b.root
    }

    final override fun onStart() {
        super.onStart()
        // ViewPager2 keeps adjacent pages STARTED until a swipe settles.
        // Load their views now, without taking over the active page's controls.
        if (parentFragment is PagerFragment<*, *>) initializeView()
    }

    override fun onResume() {
        super.onResume()
        EventBus.getDefault().registerSafe(this)
        if (!isAdded) return
        if (!isActivityReady) {
            isActivityReady = true
            initializeActivity()
        }
        initializeView()
        dispatchCanRefresh()
        appBarAnimator?.dispatchLiftOnScroll()
    }

    private fun initializeActivity() {
        // setup the activity (bottom sheet, FAB, etc.)
        // run before setupScrollListener {} to populate appBars
        (activity as? MainActivity)?.let(::setupMainActivity)
        (activity as? LoginActivity)?.let(::setupLoginActivity)
        var parent: Fragment? = parentFragment
        while (parent != null) {
            if (parent is PagerFragment<*, *>) {
                val tabLayout = parent.getTabLayout()
                appBars += tabLayout
                (tabLayout.parent as? AppBarLayout)?.let { appBars += it }
                break
            }
            parent = parent.parentFragment
        }
        // listen to scroll state changes
        var first = true
        setupScrollListener {
            if (isScrolled != it || first)
                isScrolled = it
            first = false
        }
    }

    private fun initializeView() {
        val currentView = view ?: return
        if (!isAdded || isViewReady) return
        isViewReady = true
        // let the UI transition for a moment
        startCoroutineTimer(100L) {
            if (!isAdded || view !== currentView)
                return@startCoroutineTimer
            onViewReady(inState)
            if (isResumed) {
                (activity as? MainActivity)?.gainAttention()
                (activity as? MainActivity)?.gainAttentionFAB()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        EventBus.getDefault().unregisterSafe(this)
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && isAdded && view != null && isActivityReady && parentFragment == null) {
            // Navigation clears shared controls even when the destination view is retained.
            (activity as? MainActivity)?.let(::setupMainActivity)
            dispatchCanRefresh()
            appBarAnimator?.dispatchLiftOnScroll()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isViewReady = false
        isActivityReady = false
        job.cancel()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) =
        super.onViewCreated(view, savedInstanceState)

    final override fun onViewStateRestored(savedInstanceState: Bundle?) =
        super.onViewStateRestored(savedInstanceState)

    final override fun onAttach(context: Context) = super.onAttach(context)
    final override fun onCreate(savedInstanceState: Bundle?) = super.onCreate(savedInstanceState)
    final override fun onStop() = super.onStop()
    final override fun onDestroy() = super.onDestroy()
    final override fun onDetach() = super.onDetach()

    /**
     * Called if there is no inflater passed in the constructor.
     * Must return a non-null value.
     */
    open fun inflate(
        inflater: LayoutInflater,
        parent: ViewGroup?,
        attachToParent: Boolean,
    ): B? = null

    /**
     * Called to retrieve the scrolling view contained in the fragment.
     * The scrolling view is configured to work nicely with the app bars
     * and the SwipeRefreshLayout.
     */
    open fun getScrollingView(): View? = null

    /**
     * Called to retrieve the FAB label resource and the icon.
     * If provided, a FAB is added and shown automatically.
     */
    open fun getFab(): Pair<Int, IIcon>? = null

    /**
     * Called to retrieve the [MetadataType] of items displayed by the fragment.
     * If provided, a "mark as read" item is added to the bottom sheet.
     */
    open fun getMarkAsReadType(): MetadataType? = null

    /**
     * Called to retrieve the [FeatureType] this fragment is associated with.
     * May also return arguments for the sync task.
     *
     * If not provided, swipe-to-refresh is disabled and the manual sync dialog
     * selects all features by default.
     *
     * If [FeatureType] is null, all features are synced (and selected by the
     * manual sync dialog).
     *
     * It is important to return the desired [FeatureType] from the first
     * call of this method, which runs before [onViewReady]. Otherwise,
     * swipe-to-refresh will not be enabled unless the view is scrolled.
     */
    open fun getSyncParams(): Pair<FeatureType?, JsonObject?>? = null

    /**
     * Called to retrieve any extra bottom sheet items that should be displayed.
     */
    open fun getBottomSheetItems() = listOf<IBottomSheetItem<*>>()

    /**
     * Called once per view, when resumed (default) or started (pager pages).
     * Adjacent pager pages can load before they become the selected page.
     *
     * Perform view initialization and other tasks, if necessary. Remember to call super
     * if used in a [PagerFragment].
     */
    open suspend fun onViewReady(savedInstanceState: Bundle?) {}

    /**
     * Called when the FAB is clicked (if enabled).
     */
    open suspend fun onFabClick() {}

    fun hideFab() = (activity as? MainActivity)?.hideFab()
    fun showFab() = (activity as? MainActivity)?.showFab()
}
