/*
 * Copyright (c) Kuba Szczodrzyński 2019-11-23.
 */

package pl.szczodrzynski.edziennik.ui.home

import android.animation.ValueAnimator
import android.graphics.RectF
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_DRAG
import androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_IDLE
import androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_SWIPE
import androidx.recyclerview.widget.ItemTouchHelper.DOWN
import androidx.recyclerview.widget.ItemTouchHelper.LEFT
import androidx.recyclerview.widget.ItemTouchHelper.RIGHT
import androidx.recyclerview.widget.ItemTouchHelper.UP
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.card.MaterialCardView
import pl.szczodrzynski.edziennik.ui.home.HomeFragment.Companion.removeCard
import pl.szczodrzynski.edziennik.ui.home.HomeFragment.Companion.swapCards

class CardItemTouchHelperCallback(
    private val cardAdapter: HomeCardAdapter,
    private val onDragStateChanged: (Boolean) -> Unit = {},
    private val onCanRefresh: ((canRefresh: Boolean) -> Unit)?,
) : ItemTouchHelper.Callback() {
    companion object {
        private const val DRAG_FLAGS = UP or DOWN
        private const val SWIPE_FLAGS = LEFT
    }

    private var dragCardView: MaterialCardView? = null
    private var shapeAnimator: ValueAnimator? = null

    private fun animateCardShape(card: MaterialCardView, targetRadiusDp: Float) {
        val density = card.resources.displayMetrics.density
        val targetSizePx = targetRadiusDp * density
        val currentSize = card.shapeAppearanceModel.topLeftCornerSize.getCornerSize(
            RectF(0f, 0f, card.width.toFloat(), card.height.toFloat())
        )
        shapeAnimator?.cancel()
        shapeAnimator = ValueAnimator.ofFloat(currentSize, targetSizePx).apply {
            duration = 250L
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener { anim ->
                val size = anim.animatedValue as Float
                card.shapeAppearanceModel = card.shapeAppearanceModel.toBuilder()
                    .setAllCornerSizes(size)
                    .build()
            }
            start()
        }
    }

    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        val spanCount = (recyclerView.layoutManager as? StaggeredGridLayoutManager)?.spanCount ?: 1
        val dragFlags = if (spanCount > 1) DRAG_FLAGS or LEFT or RIGHT else DRAG_FLAGS
        return makeMovementFlags(dragFlags, SWIPE_FLAGS)
    }

    override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
        val fromPosition = viewHolder.adapterPosition
        val toPosition = target.adapterPosition

        return swapCards(fromPosition, toPosition, cardAdapter)
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val position = viewHolder.adapterPosition
        removeCard(position, cardAdapter)
        cardAdapter.items.removeAt(position)
        cardAdapter.notifyItemRemoved(position)
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        onDragStateChanged(actionState == ACTION_STATE_DRAG)

        if (viewHolder != null && (actionState == ACTION_STATE_DRAG || actionState == ACTION_STATE_SWIPE)) {
            dragCardView = viewHolder.itemView as MaterialCardView
            dragCardView?.isDragged = true
            dragCardView?.let { animateCardShape(it, 14f) }
            onCanRefresh?.invoke(false)
        }
        else if (actionState == ACTION_STATE_IDLE && dragCardView != null) {
            onCanRefresh?.invoke(true)
            dragCardView?.isDragged = false
            dragCardView?.let { animateCardShape(it, 28f) }
            dragCardView = null
        }
    }
}
