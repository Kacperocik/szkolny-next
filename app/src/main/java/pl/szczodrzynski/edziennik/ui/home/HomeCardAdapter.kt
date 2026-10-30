/*
 * Copyright (c) Kuba Szczodrzyński 2019-11-23.
 */

package pl.szczodrzynski.edziennik.ui.home

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.card.MaterialCardView
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.ext.applyExpressiveShapeMorphToButtons
import pl.szczodrzynski.edziennik.ext.dp
import pl.szczodrzynski.edziennik.ext.enableExpressiveCardShapeMorph

class HomeCardAdapter(
    var items: MutableList<HomeCard>,
    var isCardsLocked: () -> Boolean = { false },
    private val onCardSizeChanged: () -> Unit = {},
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    companion object {
        private const val TAG = "HomeCardAdapter"
    }

    var itemTouchHelper: ItemTouchHelper? = null
    var cardHorizontalMargin = 16.dp

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as ViewHolder).bind(itemTouchHelper)
        (holder.root.layoutParams as? StaggeredGridLayoutManager.LayoutParams)?.apply {
            isFullSpan = false
            leftMargin = cardHorizontalMargin
            rightMargin = cardHorizontalMargin
        }
        items[position].bind(position, holder)
        holder.root.applyExpressiveShapeMorphToButtons()
        holder.root.enableExpressiveCardShapeMorph(isLocked = isCardsLocked)
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        items.getOrNull(holder.adapterPosition)?.unbind(holder.adapterPosition, holder as ViewHolder)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return ViewHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.card_home, parent, false) as MaterialCardView
        ).also { holder ->
            holder.root.addOnLayoutChangeListener { _, left, top, right, bottom,
                                                   oldLeft, oldTop, oldRight, oldBottom ->
                if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop)
                    onCardSizeChanged()
            }
        }
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(val root: MaterialCardView) : RecyclerView.ViewHolder(root) {
        @SuppressLint("ClickableViewAccessibility")
        fun bind(itemTouchHelper: ItemTouchHelper?) {
            /*root.setOnTouchListener { _: View?, event: MotionEvent ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    itemTouchHelper?.startDrag(this)
                    return@setOnTouchListener true
                }
                false
            }*/
        }
    }
}
