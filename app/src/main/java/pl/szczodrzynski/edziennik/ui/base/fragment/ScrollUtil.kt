/*
 * Copyright (c) Kuba Szczodrzyński 2024-7-9.
 */

package pl.szczodrzynski.edziennik.ui.base.fragment

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.google.android.material.motion.MotionUtils
import com.google.android.material.shape.MaterialShapeDrawable
import pl.szczodrzynski.edziennik.MainActivity
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.ext.resolveAttr
import pl.szczodrzynski.edziennik.ext.setTintColor
import pl.szczodrzynski.edziennik.ui.messages.list.MessagesFragment
import pl.szczodrzynski.edziennik.ui.messages.list.MessagesListFragment

@SuppressLint("ClickableViewAccessibility")
internal fun View?.setupScrollListener(setIsScrolled: (Boolean) -> Unit) {
    when (val view = this) {
        is RecyclerView -> {
            setIsScrolled(view.canScrollVertically(-1))
            view.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (view.canScrollVertically(-1))
                        setIsScrolled(true)
                    else if (newState == RecyclerView.SCROLL_STATE_IDLE)
                        setIsScrolled(false)
                }

                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (view.canScrollVertically(-1))
                        setIsScrolled(true)
                }
            })
        }

        is NestedScrollView -> {
            setIsScrolled(view.canScrollVertically(-1))
            view.setOnScrollChangeListener(
                NestedScrollView.OnScrollChangeListener { _, _, _, _, _ ->
                    setIsScrolled(view.canScrollVertically(-1))
                }
            )
        }

        is View -> {
            setIsScrolled(view.canScrollVertically(-1))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                view.setOnScrollChangeListener { _, _, _, _, _ ->
                    setIsScrolled(view.canScrollVertically(-1))
                }
            } else {
                view.viewTreeObserver.addOnScrollChangedListener {
                    setIsScrolled(view.canScrollVertically(-1))
                }
            }
        }

        else -> { // null
            // dispatch the default value to the activity
            setIsScrolled(false)
        }
    }
}

fun View.setupFabHideOnScroll(
    isEnabled: () -> Boolean,
    onHide: () -> Unit,
    onShow: () -> Unit,
    onScrollState: ((Boolean) -> Unit)? = null,
) {
    when (this) {
        is RecyclerView -> {
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (!isEnabled()) return
                    if (newState == RecyclerView.SCROLL_STATE_IDLE && !recyclerView.canScrollVertically(-1)) {
                        onShow()
                    }
                }

                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (!isEnabled()) return
                    if (dy > 4) {
                        onHide()
                    } else if (dy < -4 || !recyclerView.canScrollVertically(-1)) {
                        onShow()
                    }
                }
            })
        }

        is NestedScrollView -> {
            setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { v, _, scrollY, _, oldScrollY ->
                onScrollState?.invoke(v.canScrollVertically(-1))
                if (!isEnabled()) return@OnScrollChangeListener
                val dy = scrollY - oldScrollY
                if (dy > 4) {
                    onHide()
                } else if (dy < -4 || scrollY <= 0) {
                    onShow()
                }
            })
        }
    }
}

internal fun BaseFragment<*, *>.setupScrollListener(setIsScrolled: (Boolean) -> Unit) {
    getScrollingView().setupScrollListener(setIsScrolled)
}

internal fun BaseFragment<*, *>.dispatchCanRefresh() {
    if (parentFragment is MessagesFragment && this !is MessagesListFragment)
        return
    val activity = activity as? MainActivity ?: return
    activity.swipeRefreshLayout.isEnabled =
        app.config.sync.pullToRefresh &&
            !canRefreshDisabled &&
            !isScrolled &&
            getSyncParams() != null
}

internal class AppBarColorAnimator(
    private val context: Context,
    private val bars: Set<View>,
) : ValueAnimator.AnimatorUpdateListener {
    companion object {
        // keep track of the current animation value applied globally to the view
        private var currentValue = 0.0f
    }

    private lateinit var animator: ValueAnimator

    private val barColor = R.attr.colorSurface.resolveAttr(context)
    private val liftColor = R.attr.colorSurfaceContainer.resolveAttr(context)

    fun dispatchLiftOnScroll(isScrolled: Boolean) {
        if (::animator.isInitialized)
            animator.cancel()
        animator = ValueAnimator.ofFloat(
            currentValue,
            if (isScrolled) 1.0f else 0.0f,
        )
        animator.duration = MotionUtils.resolveThemeDuration(
            context,
            R.attr.motionDurationMedium2,
            context.resources.getInteger(R.integer.app_bar_elevation_anim_duration),
        ).toLong()
        animator.interpolator = MotionUtils.resolveThemeInterpolator(
            context,
            R.attr.motionEasingStandardInterpolator,
            LinearInterpolator(),
        )
        animator.addUpdateListener(this)
        animator.start()
    }

    context(BaseFragment<*, *>)
    fun dispatchLiftOnScroll() {
        dispatchLiftOnScroll(isScrolled)
    }

    override fun onAnimationUpdate(animation: ValueAnimator) {
        currentValue = animation.animatedValue as Float
        val mixedColor = MaterialColors.layer(
            barColor,
            liftColor,
            currentValue,
        )
        for (bar in bars) {
            when (val drawable = bar.background) {
                is MaterialShapeDrawable -> drawable.fillColor = ColorStateList.valueOf(mixedColor)
                is LayerDrawable -> drawable.getDrawable(0).setTintColor(mixedColor)
                is ColorDrawable -> drawable.color = mixedColor
                else -> {
                    if (bar.background == null) {
                        bar.background = MaterialShapeDrawable().apply {
                            fillColor = ColorStateList.valueOf(mixedColor)
                        }
                    } else {
                        bar.setBackgroundColor(mixedColor)
                    }
                }
            }
        }
    }
}
