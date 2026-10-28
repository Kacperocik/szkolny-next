/*
 * Copyright (c) Kuba Szczodrzyński 2021-3-17.
 */

package pl.szczodrzynski.edziennik.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.TextView
import com.danielstone.materialaboutlibrary.holders.MaterialAboutItemViewHolder
import com.danielstone.materialaboutlibrary.items.MaterialAboutActionItem
import com.danielstone.materialaboutlibrary.items.MaterialAboutActionSwitchItem
import com.danielstone.materialaboutlibrary.items.MaterialAboutItem
import com.danielstone.materialaboutlibrary.items.MaterialAboutSwitchItem
import com.danielstone.materialaboutlibrary.items.MaterialAboutTitleItem.MaterialAboutTitleItemViewHolder
import com.danielstone.materialaboutlibrary.util.DefaultViewTypeManager
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import pl.szczodrzynski.edziennik.MainActivity
import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.enums.NavTarget
import pl.szczodrzynski.edziennik.ext.enableExpressiveCardShapeMorph
import pl.szczodrzynski.edziennik.ext.resolveAttr
import pl.szczodrzynski.edziennik.ui.settings.SettingsViewTypeManager.ItemType.Companion.CATEGORY_ITEM
import pl.szczodrzynski.edziennik.ui.settings.SettingsViewTypeManager.ItemType.Companion.PROFILE_ITEM

class Material3ActionItemViewHolder(view: View) : MaterialAboutItemViewHolder(view) {
    val icon: ImageView? = view.findViewById(R.id.mal_item_image)
    val text: TextView = view.findViewById(R.id.mal_item_text)
    val subText: TextView = view.findViewById(R.id.mal_action_item_subtext)
}

class Material3SwitchItemViewHolder(view: View) : MaterialAboutItemViewHolder(view) {
    val icon: ImageView? = view.findViewById(R.id.mal_switch_image)
    val text: TextView = view.findViewById(R.id.mal_switch_text)
    val subText: TextView = view.findViewById(R.id.mal_switch_subtext)
    val materialSwitch: MaterialSwitch = view.findViewById(R.id.mal_switch)
}

class Material3ActionSwitchItemViewHolder(view: View) : MaterialAboutItemViewHolder(view) {
    val actionView: View? = view.findViewById(R.id.mal_switch_action)
    val icon: ImageView? = view.findViewById(R.id.mal_switch_image)
    val text: TextView = view.findViewById(R.id.mal_switch_text)
    val subText: TextView = view.findViewById(R.id.mal_switch_subtext)
    val materialSwitch: MaterialSwitch = view.findViewById(R.id.mal_switch)
}

class SettingsViewTypeManager : DefaultViewTypeManager() {
    class ItemType {
        companion object {
            const val PROFILE_ITEM = 10
            const val CATEGORY_ITEM = 11
        }
    }

    private fun findCardView(view: View): MaterialCardView? {
        if (view is MaterialCardView) return view
        if (view is ViewGroup) {
            val cardById = view.findViewById<MaterialCardView>(R.id.card)
            if (cardById != null) return cardById
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                if (child is MaterialCardView) return child
            }
        }
        return null
    }

    private fun applyCornerPosition(view: View, item: MaterialAboutItem) {
        val card = findCardView(view) ?: return
        val cornerPosition = ItemCornerPositions.get(item)

        val density = card.resources.displayMetrics.density
        val cornerLarge = 24f * density
        val cornerSmall = 4f * density

        val isFirst = cornerPosition == CornerPosition.FIRST || cornerPosition == CornerPosition.SINGLE
        val isLast = cornerPosition == CornerPosition.LAST || cornerPosition == CornerPosition.SINGLE

        card.shapeAppearanceModel = card.shapeAppearanceModel.toBuilder()
            .setTopLeftCornerSize(if (isFirst) cornerLarge else cornerSmall)
            .setTopRightCornerSize(if (isFirst) cornerLarge else cornerSmall)
            .setBottomLeftCornerSize(if (isLast) cornerLarge else cornerSmall)
            .setBottomRightCornerSize(if (isLast) cornerLarge else cornerSmall)
            .build()
    }

    override fun getLayout(itemType: Int) = when (itemType) {
        PROFILE_ITEM -> R.layout.mal_material_about_profile_item
        CATEGORY_ITEM -> R.layout.mal_material_about_category_item
        DefaultViewTypeManager.ItemType.ACTION_ITEM -> R.layout.mal_material_about_action_item
        DefaultViewTypeManager.ItemType.TITLE_ITEM -> R.layout.mal_material_about_title_item
        DefaultViewTypeManager.ItemType.SECTION_ITEM -> R.layout.mal_material_about_section_item
        DefaultViewTypeManager.ItemType.SWITCH_ITEM -> R.layout.mal_material_about_switch_item
        DefaultViewTypeManager.ItemType.ACTION_SWITCH_ITEM -> R.layout.mal_material_about_action_switch_item
        else -> super.getLayout(itemType)
    }

    override fun getViewHolder(itemType: Int, view: View): MaterialAboutItemViewHolder =
        when (itemType) {
            PROFILE_ITEM -> MaterialAboutProfileItem.getViewHolder(view)
            CATEGORY_ITEM -> MaterialAboutCategoryItem.getViewHolder(view)
            DefaultViewTypeManager.ItemType.ACTION_ITEM -> Material3ActionItemViewHolder(view)
            DefaultViewTypeManager.ItemType.SWITCH_ITEM -> Material3SwitchItemViewHolder(view)
            DefaultViewTypeManager.ItemType.ACTION_SWITCH_ITEM -> Material3ActionSwitchItemViewHolder(view)
            else -> super.getViewHolder(itemType, view)
        }

    override fun setupItem(
        itemType: Int,
        holder: MaterialAboutItemViewHolder,
        item: MaterialAboutItem,
        context: Context
    ) {
        val card = findCardView(holder.itemView)
        card?.isClickable = true
        card?.isFocusable = true
        applyCornerPosition(holder.itemView, item)
        when (itemType) {
            PROFILE_ITEM -> MaterialAboutProfileItem.setupItem(
                holder as MaterialAboutTitleItemViewHolder,
                item as MaterialAboutProfileItem, context
            )
            CATEGORY_ITEM -> MaterialAboutCategoryItem.setupItem(
                holder as MaterialAboutCategoryItem.ViewHolder,
                item as MaterialAboutCategoryItem, context
            )
            DefaultViewTypeManager.ItemType.ACTION_ITEM -> setupActionItem(
                holder as Material3ActionItemViewHolder,
                item as MaterialAboutActionItem, context
            )
            DefaultViewTypeManager.ItemType.SWITCH_ITEM -> setupSwitchItem(
                holder as Material3SwitchItemViewHolder,
                item as MaterialAboutSwitchItem, context
            )
            DefaultViewTypeManager.ItemType.ACTION_SWITCH_ITEM -> setupActionSwitchItem(
                holder as Material3ActionSwitchItemViewHolder,
                item as MaterialAboutActionSwitchItem, context
            )
            else -> super.setupItem(itemType, holder, item, context)
        }
    }

    private fun setupActionItem(
        holder: Material3ActionItemViewHolder,
        item: MaterialAboutActionItem,
        context: Context
    ) {
        val text = item.text
        val textRes = item.textRes
        holder.text.visibility = View.VISIBLE
        if (text != null) {
            holder.text.text = text
        } else if (textRes != 0) {
            holder.text.setText(textRes)
        } else {
            holder.text.visibility = View.GONE
        }

        val subText = item.subText
        val subTextRes = item.subTextRes
        if (subText != null) {
            holder.subText.text = subText
            holder.subText.visibility = View.VISIBLE
        } else if (subTextRes != 0) {
            holder.subText.setText(subTextRes)
            holder.subText.visibility = View.VISIBLE
        } else {
            holder.subText.visibility = View.GONE
        }

        if (item.shouldShowIcon()) {
            holder.icon?.visibility = View.VISIBLE
            val drawable = item.icon
            val drawableRes = item.iconRes
            if (drawable != null) {
                holder.icon?.setImageDrawable(drawable)
            } else if (drawableRes != 0) {
                holder.icon?.setImageResource(drawableRes)
            }
        } else {
            holder.icon?.visibility = View.GONE
        }

        val card = findCardView(holder.itemView) ?: holder.itemView
        card.isClickable = true
        card.isFocusable = true
        card.setOnClickListener {
            item.onClickAction?.onClick()
        }
        (card as? MaterialCardView)?.enableExpressiveCardShapeMorph()
    }

    private fun setupSwitchItem(
        holder: Material3SwitchItemViewHolder,
        item: MaterialAboutSwitchItem,
        context: Context
    ) {
        val text = item.text
        val textRes = item.textRes
        holder.text.visibility = View.VISIBLE
        if (text != null) {
            holder.text.text = text
        } else if (textRes != 0) {
            holder.text.setText(textRes)
        } else {
            holder.text.visibility = View.GONE
        }

        val subText = item.subText
        val subTextRes = item.subTextRes
        val subTextChecked = item.subTextChecked
        val subTextCheckedRes = item.subTextCheckedRes

        val currentSubText = if (item.isChecked && (subTextChecked != null || subTextCheckedRes != 0)) {
            subTextChecked ?: if (subTextCheckedRes != 0) context.getString(subTextCheckedRes) else null
        } else {
            subText ?: if (subTextRes != 0) context.getString(subTextRes) else null
        }

        if (currentSubText != null) {
            holder.subText.text = currentSubText
            holder.subText.visibility = View.VISIBLE
        } else {
            holder.subText.visibility = View.GONE
        }

        if (item.shouldShowIcon()) {
            holder.icon?.visibility = View.VISIBLE
            val drawable = item.icon
            val drawableRes = item.iconRes
            if (drawable != null) {
                holder.icon?.setImageDrawable(drawable)
            } else if (drawableRes != 0) {
                holder.icon?.setImageResource(drawableRes)
            }
        } else {
            holder.icon?.visibility = View.GONE
        }

        holder.materialSwitch.setOnCheckedChangeListener(null)
        holder.materialSwitch.isChecked = item.isChecked

        val listener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
            if (item.isChecked != isChecked) {
                item.isChecked = isChecked
                item.onCheckedChangedAction?.onCheckedChanged(item, isChecked)
            }
        }
        holder.materialSwitch.setOnCheckedChangeListener(listener)

        val card = findCardView(holder.itemView) ?: holder.itemView
        card.isClickable = true
        card.isFocusable = true
        card.setOnClickListener {
            holder.materialSwitch.toggle()
        }
        (card as? MaterialCardView)?.enableExpressiveCardShapeMorph()
    }

    private fun setupActionSwitchItem(
        holder: Material3ActionSwitchItemViewHolder,
        item: MaterialAboutActionSwitchItem,
        context: Context
    ) {
        setupSwitchItem(
            Material3SwitchItemViewHolder(holder.itemView),
            item,
            context
        )

        holder.actionView?.setOnClickListener {
            item.onClickAction?.onClick()
        }
    }
}
