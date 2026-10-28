/*
 * Copyright (c) Kuba Szczodrzyński 2020-4-5.
 */

package pl.szczodrzynski.edziennik.ui.search

import android.animation.ValueAnimator
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.recyclerview.widget.RecyclerView
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.databinding.SearchItemBinding
import pl.szczodrzynski.edziennik.ext.hideKeyboard
import pl.szczodrzynski.edziennik.ext.toDrawable
import pl.szczodrzynski.edziennik.utils.TextInputKeyboardEdit

class SearchViewHolder(
    inflater: LayoutInflater,
    parent: ViewGroup,
    val b: SearchItemBinding = SearchItemBinding.inflate(
        inflater,
        parent,
        false
    ),
) : RecyclerView.ViewHolder(b.root) {
    companion object {
        private const val TAG = "SearchViewHolder"
    }

    private var isSearchActive: Boolean? = null
    private var marginAnimator: ValueAnimator? = null

    init {
        // Setup initial search icon
        b.searchLayout.startIconDrawable = CommunityMaterial.Icon3.cmd_magnify.toDrawable(
            b.searchLayout.context,
            colorAttr = R.attr.colorOnSurfaceVariant
        )
        b.searchLayout.setStartIconContentDescription(R.string.messages_search)

        // Setup clear button (end icon) using CommunityMaterial cmd_close
        b.searchLayout.endIconDrawable = CommunityMaterial.Icon.cmd_close.toDrawable(
            b.searchLayout.context,
            colorAttr = R.attr.colorOnSurfaceVariant
        )
        b.searchLayout.setEndIconOnClickListener {
            b.searchEdit.text?.clear()
        }
        b.searchLayout.setEndIconContentDescription(R.string.search_clear)

        // Setup start icon (search / back) click listener
        b.searchLayout.setStartIconOnClickListener {
            if (isSearchActive == true) {
                b.searchEdit.text?.clear()
                b.root.requestFocus()
                b.searchEdit.clearFocus()
                b.searchLayout.clearFocus()
                b.searchEdit.hideKeyboard()
                updateSearchActiveState(animate = true)
            } else {
                b.searchEdit.requestFocus()
                updateSearchActiveState(animate = true)
            }
            val startIconView = b.searchLayout.findViewById<View>(com.google.android.material.R.id.text_input_start_icon)
            startIconView?.isPressed = false
            startIconView?.clearFocus()
            startIconView?.jumpDrawablesToCurrentState()
            startIconView?.post {
                startIconView.isPressed = false
                startIconView.clearFocus()
                startIconView.jumpDrawablesToCurrentState()
            }
        }

        disableIconFocus()
        b.searchLayout.post {
            disableIconFocus()
        }

        // Focus listener for expanding/narrowing
        b.searchEdit.setOnFocusChangeListener { _, _ ->
            updateSearchActiveState(animate = true)
        }

        // Soft keyboard listener for when user presses hardware back
        b.searchEdit.setOnKeyboardListener(object : TextInputKeyboardEdit.KeyboardListener {
            override fun onStateChanged(keyboardEditText: TextInputKeyboardEdit, showing: Boolean) {
                if (!showing && keyboardEditText.text.isNullOrEmpty()) {
                    keyboardEditText.clearFocus()
                    b.root.requestFocus()
                    updateSearchActiveState(animate = true)
                }
            }
        })
    }

    private fun disableIconFocus() {
        val startIconView = b.searchLayout.findViewById<View>(com.google.android.material.R.id.text_input_start_icon)
        startIconView?.isFocusable = false
        startIconView?.isFocusableInTouchMode = false

        val endIconView = b.searchLayout.findViewById<View>(com.google.android.material.R.id.text_input_end_icon)
        endIconView?.isFocusable = false
        endIconView?.isFocusableInTouchMode = false
    }

    internal fun onTextChanged(s: Editable?) {
        val hasText = !s.isNullOrEmpty()
        b.searchLayout.isEndIconVisible = hasText
        updateSearchActiveState(animate = true)
    }

    fun updateSearchActiveState(animate: Boolean) {
        val shouldBeActive = b.searchEdit.hasFocus() || !b.searchEdit.text.isNullOrEmpty()
        b.searchLayout.isEndIconVisible = !b.searchEdit.text.isNullOrEmpty()

        if (isSearchActive == shouldBeActive && !animate) {
            return
        }
        isSearchActive = shouldBeActive

        val density = b.root.resources.displayMetrics.density
        val inactiveMarginPx = (16 * density).toInt()
        val activeMarginPx = (4 * density).toInt()
        val targetMarginPx = if (shouldBeActive) activeMarginPx else inactiveMarginPx

        // 1. Icon update
        if (shouldBeActive) {
            b.searchLayout.startIconDrawable = CommunityMaterial.Icon.cmd_chevron_left.toDrawable(
                b.searchLayout.context,
                colorAttr = R.attr.colorOnSurfaceVariant
            )
            b.searchLayout.setStartIconContentDescription(R.string.search_back)
        } else {
            b.searchLayout.startIconDrawable = CommunityMaterial.Icon3.cmd_magnify.toDrawable(
                b.searchLayout.context,
                colorAttr = R.attr.colorOnSurfaceVariant
            )
            b.searchLayout.setStartIconContentDescription(R.string.messages_search)
        }
        disableIconFocus()

        // 2. Smooth width expansion animation
        val lp = b.searchLayout.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        val currentMargin = lp.marginStart

        marginAnimator?.cancel()

        if (animate && b.root.isAttachedToWindow && currentMargin != targetMarginPx) {
            marginAnimator = ValueAnimator.ofInt(currentMargin, targetMarginPx).apply {
                duration = 250L
                interpolator = FastOutSlowInInterpolator()
                addUpdateListener { anim ->
                    val m = anim.animatedValue as Int
                    val params = b.searchLayout.layoutParams as? ViewGroup.MarginLayoutParams ?: return@addUpdateListener
                    params.marginStart = m
                    params.marginEnd = m
                    b.searchLayout.layoutParams = params
                }
                start()
            }
        } else {
            lp.marginStart = targetMarginPx
            lp.marginEnd = targetMarginPx
            b.searchLayout.layoutParams = lp
        }
    }

    internal fun bind(item: SearchField, adapter: SearchableAdapter<*>) {
        val watcher = SearchTextWatcher(this, adapter, item)
        b.searchEdit.removeTextChangedListener(watcher)

        val count = adapter.getMatchingContentCount()
        if (adapter.items.isEmpty() || adapter.items.size == adapter.allItems.size || count == 0) {
            b.searchLayout.helperText = " "
        } else {
            val resId = adapter.searchResultsPluralRes
            b.searchLayout.helperText = b.root.context.resources.getQuantityString(resId, count, count)
        }
        b.searchEdit.setText(item.searchText)

        b.searchEdit.addTextChangedListener(watcher)

        // Set initial state without animation on bind
        updateSearchActiveState(animate = false)
    }
}
